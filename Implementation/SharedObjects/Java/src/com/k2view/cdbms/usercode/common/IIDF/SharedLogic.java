/////////////////////////////////////////////////////////////////////////
// Project Shared Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common.IIDF;

//import com.datastax.oss.driver.api.core.CqlSession;
//import com.k2view.cdbms.cluster.CassandraClusterSingleton;
import com.k2view.cdbms.exceptions.InstanceNotFoundException;
import com.k2view.cdbms.finder.DataChange;
import com.k2view.cdbms.finder.api.IidFinderApi;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.Db;
import com.k2view.cdbms.shared.Globals;
import com.k2view.cdbms.shared.IifProperties;
import com.k2view.cdbms.shared.LUTypeFactoryImpl;
import com.k2view.cdbms.shared.logging.LogEntry;
import com.k2view.cdbms.shared.logging.MsgId;
import com.k2view.cdbms.shared.user.UserCode;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.out;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.type;
import com.k2view.cdbms.sync.SyncMode;
import com.k2view.cdbms.usercode.common.IIDFProducerSingleton;
import com.k2view.fabric.common.Util;
import com.k2view.fabric.common.mtable.MTable;
import com.k2view.fabric.common.mtable.MTables;
import com.k2view.fabric.common.stats.CustomStats;


import org.json.JSONArray;
import org.json.JSONObject;
import org.sqlite.SQLiteException;

import java.io.InvalidObjectException;
import java.util.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.k2view.cdbms.lut.FunctionDef.functionContext;
import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class SharedLogic {


    private static void fnIIDFDeleteFromChilds(Map<String, DataChange> childTables, List<Object> paramsStats) {
        Instant start = Instant.now();
        //Loop throw table list and clean there orphan records
        if (childTables != null && !childTables.isEmpty()) {
            Map<String, Set<String>> prntTable = fnIIDFGetParentTable(getLuType().luName);
            TreeMap<String, Map<String, List<LudbRelationInfo>>> phyRel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            phyRel.putAll(getLuType().getLudbPhysicalRelations());
            setThreadGlobals("LUDB_PHYISICAL_RELATION", phyRel);
            childTables.forEach((key, value) -> {
                try {
                    fnIIDFDeleteOrphans(key, prntTable, null, value.getPos(), value.getOpTimestamp(), true, value.getOperation(), value.getCurrentTimestamp(), value.isLogicalChildChanged());
                } catch (Exception e) {
                    throw new RuntimeException("fnIIDFDeleteFromChilds", e);
                }
            });

            String DELETE_ORPHAN_IGNORE_REPLICATES = getThreadGlobals("DELETE_ORPHAN_IGNORE_REPLICATES") + "";
            if (!"Y".equals(DELETE_ORPHAN_IGNORE_REPLICATES)) {
                paramsStats.add((Duration.between(start, Instant.now()).toMillis()));//execute_delete_orphan
                paramsStats.add(childTables.size());//delete_orphan_count
                setThreadGlobals("DELETE_ORPHAN", childTables);
                setThreadGlobals("DELETED_TABLES", new HashSet<>());
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    private static void fnIIDFDeleteOrphans(String tableName, Map<String, Set<String>> prntTbl, String targetIid, String pos, Long op_ts, Boolean run_parent_delete, Object opetaion, Long cr_ts, Boolean logicalChildChanged) throws Exception {
        DataChange.Operation ope = (DataChange.Operation) opetaion;
        LudbObject ludbObject = getLuType().ludbObjects.get(tableName);
        final String delete = "delete from %s.%s where %s";

        boolean rec_deleted = true;
        if (run_parent_delete) {
            rec_deleted = fnIIDFDeleteTableOrphanRecords(tableName, prntTbl, targetIid, pos, op_ts, cr_ts);
        }

        if ((!rec_deleted && ope.equals(DataChange.Operation.update) && !logicalChildChanged) || (!rec_deleted && ope.equals(DataChange.Operation.delete) && !run_parent_delete)) {
            return;
        }

        for (LudbObject child : ludbObject.childObjects) {//Loop throw all table's childs and delete orphan records
            String childTableName = child.ludbObjectName;

            Map<String, String> trnMap = getTranslationValues("trnIIDFOrphanRecV", new Object[]{childTableName.toUpperCase()});//Get table record from trnIIDFOrphanRecV
            if (Boolean.parseBoolean(trnMap.get("SKIP_VALIDATE_IND"))) {//Check if to run orphan record check on table
                continue;
            }

            Set<String> prntTables = prntTbl.get(childTableName.toUpperCase());
            StringBuilder sqlQuery = new StringBuilder();
            String prefix = "";
            for (String tbl : prntTables) {

                TreeMap<String, List<LudbRelationInfo>> phyRelTbl = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);//Get table -> child links
                phyRelTbl.putAll(((TreeMap<String, Map<String, List<LudbRelationInfo>>>) getThreadGlobals("LUDB_PHYISICAL_RELATION")).get(tbl + ""));

                List<LudbRelationInfo> childRelations = phyRelTbl.get(childTableName);
                HashMap<String, String> popRelMap = new HashMap<>();
                childRelations.forEach(chRel -> {
                    String popMapName = chRel.to.get("populationObjectName");
                    if (popRelMap.containsKey(popMapName)) {
                        popRelMap.put(popMapName, String.format("%s and %s = %s.%s", popRelMap.get(popMapName), chRel.from.get("column"), childTableName, chRel.to.get("column")));
                    } else {
                        popRelMap.put(popMapName, String.format("%s = %s.%s", chRel.from.get("column"), childTableName, chRel.to.get("column")));
                    }
                });

                for (String con : popRelMap.values()) {
                    sqlQuery.append(prefix).append(" not exists (select 1 from ").append(getLuType().luName).append(".").append(tbl).append(" where ").append(con).append(" ) ");
                    prefix = " and ";
                }
            }

            //Check if to add another condition to orphan records check query
            if (trnMap.get("VALIDATION_SQL") != null && !trnMap.get("VALIDATION_SQL").equalsIgnoreCase("")) {
                sqlQuery.append(" and not exists (").append(trnMap.get("VALIDATION_SQL")).append(")");
            }

            rec_deleted = fnIIDFGetRecData(childTableName, sqlQuery.toString(), targetIid, pos, op_ts, (!Boolean.parseBoolean(IIDF_EXTRACT_FROM_SOURCE) || getTranslationsData("trnIIDFForceReplicateOnDelete").keySet().contains(childTableName.toUpperCase())), cr_ts);//Before deleting records send them to kafka
            ludb().execute(String.format(delete, getLuType().luName, childTableName, sqlQuery.toString()));//Delete orphan records
            if (rec_deleted) {
                fnIIDFDeleteOrphans(childTableName, prntTbl, targetIid, pos, op_ts, false, ope, cr_ts, false);//Call function again with child table
            }
        }
    }


    private static Map<String, Set<String>> fnIIDFGetParentTable(String lut_name) {
        //Function to get table parent
        Map<String, Set<String>> prntTable = new HashMap<>();
        LUType lut = LUTypeFactoryImpl.getInstance().getTypeByName(lut_name);
        LudbObject table = lut.getRootObject();
        setTblPrnt(table, prntTable);
        return prntTable;
    }

    @out(name = "result", type = Object.class, desc = "")
    public static String[] fnIIDFGetTablePK(String table_name, String lu_name) throws Exception {
        LUType lut = LUTypeFactoryImpl.getInstance().getTypeByName(lu_name);
        if (lut != null) {
            LudbObject rtTable = lut.ludbObjects.get(table_name);
            if (rtTable != null) {
                LudbIndexes luIndx = rtTable.indexes;
                for (LudbIndex luLocIndx : luIndx.localIndexes) {
                    if (luLocIndx.pk) {
                        return luLocIndx.columns.toArray(new String[0]);
                    }
                }
            }
        }

        String pkColumnsFromTrn = getTranslationValues("trnTable2PK", new Object[]{table_name.toUpperCase()}).get("pk_list");
        if (pkColumnsFromTrn == null) {
            throw new InvalidObjectException(String.format("Couldn't find Primary key columns for table: %s neither in trnTable2PK nor on table indexes, Please check!", table_name));
        } else {
            return pkColumnsFromTrn.split(",");
        }
    }

    private static void fnIIDFSendRecBack2Kafka(String table_name, Set<String> column_list, List<Object> valList, String targetIid, String pos, Long op_ts, String reason, Long cr_ts) throws Exception {
        String delOrpIgnRep = getThreadGlobals("DELETE_ORPHAN_IGNORE_REPLICATES") == null ? "N" : getThreadGlobals("DELETE_ORPHAN_IGNORE_REPLICATES").toString();
        if (inDebugMode() || "Y".equals(delOrpIgnRep)) return;//Dont run

        String source_table_name = fnIIDFGetTableSourceFullName(table_name);//Get table source full name
        if (source_table_name == null) {
            throw new RuntimeException("Couldn't find LU Table source schema and table names!");
        }


        String globalName = "";

        if ("R".equals(reason)) {
            globalName = "SEND_BACK_CNT_R";
        } else if ("D".equals(reason)) {
            globalName = "SEND_BACK_CNT_D";
        }

        Object rs = getThreadGlobals(globalName);
        if (rs == null) {
            setThreadGlobals(globalName, 1);
        } else {
            long cnt = Long.parseLong((rs + ""));
            cnt++;
            setThreadGlobals(globalName, cnt);
        }
        final java.text.DateFormat clsDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
        clsDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        java.util.Date currentTime = new java.util.Date();//Create date
        String currTime = clsDateFormat.format(currentTime);
        String current_ts = clsDateFormat.format(currentTime).replace(" ", "T");

        JSONObject IIDJSon = new JSONObject();
        IIDJSon.put("table", source_table_name);
        IIDJSon.put("op_type", "R");

        IIDJSon.put("op_ts", currTime);

        if (cr_ts != null && cr_ts != 0) {
            Timestamp ts = new Timestamp((cr_ts + 1));
            Date tsToDate = new Date(ts.getTime());
            IIDJSon.put("current_ts", clsDateFormat.format(tsToDate).replace(" ", "T"));
        } else if ("R".equals(reason)) {
            Timestamp ts = new Timestamp((op_ts / 1000));
            Date tsToDate = new Date(ts.getTime());
            IIDJSon.put("current_ts", clsDateFormat.format(tsToDate).replace(" ", "T"));
        } else {
            IIDJSon.put("current_ts", current_ts);
        }

        if (pos != null && !pos.equals("")) {
            IIDJSon.put("pos", pos);
        } else {
            IIDJSon.put("pos", "00000000020030806864");
        }

        if (targetIid != null && !targetIid.equals("")) {//If target iid was sent on data change send it back to kafka
            IIDJSon.put("targetIid", targetIid);
        }

        //Get table primary keys
        JSONArray PK = new JSONArray();
        String[] pkCuls = fnIIDFGetTablePK(table_name, getLuType().luName);
        String prefix = "";
        for (String pkCul : pkCuls) {
            PK.put(pkCul);
        }
        IIDJSon.put("primary_keys", PK);

        //Start build the json message
        Set<String> PKSet = new HashSet<>(Arrays.asList(pkCuls));
        StringBuilder msgKey = new StringBuilder();
        JSONObject after = new JSONObject();
        int i = 0;
        for (String col : column_list) {
            if (PKSet.contains(col.toUpperCase())) msgKey.append(valList.get(i));
            after.put(col.toUpperCase(), valList.get(i));
            i++;
        }
        IIDJSon.put("after", after);

        //send message to kafka
        IIDFProducerSingleton.getInstance().send(IDFINDER_TOPIC_PREFIX + source_table_name, msgKey.toString(), IIDJSon.toString());
    }

    private static void fnIIDFPopRecentUpdates(Object[] valueArrays, Long opTimeStamp, String iid, String sql, Long maxUpdated, Long optCurrentTimeStamp, Long idfinderHandleTime) throws Exception {
        //Function to send records to IIDF RECENT UPDATES
        StringBuilder strArray = new StringBuilder();
        String prefix = "";
        if (valueArrays != null) {
            for (Object val : valueArrays) {
                strArray.append(prefix).append(val);
                prefix = ",";
            }
        }
        ludb().execute(String.format("insert or replace into %s.IIDF_RECENT_UPDATES (IID,SQL,VAL_ARRAY,OP_TIMESTAMP,CURRENT_TIMESTAMP,OP_CURRENT_TIMESTAMP, IDFINDER_HANDLE_TIME) values (?,?,?,?,?,?,?)", getLuType().luName), iid, sql, strArray.toString(), opTimeStamp, maxUpdated, optCurrentTimeStamp, idfinderHandleTime);
    }

    @SuppressWarnings({"unchecked"})
    @type(RootFunction)
    @out(name = "IID", type = void.class, desc = "")
    public static void fnIIDFPopTable(String IID) throws Exception {
        Map<String, List<Map<String, Object>>> idfTablesDeleteList = new HashMap<>();
        DataChange dataFromQ = null;
        try (Db.Rows qData = ludb().fetch(String.format("select DATA_CHANGE from %s.IIDF_QUEUE order by \"current_timeStamp\" , POS", getLuType().luName))) {
            for (Db.Row row : qData) {
                dataFromQ = DataChange.fromJson(row.cell(0) + "");
                String tableName = dataFromQ.getTargetTableName();//Get table name from data change
                String fullSourceTableName = String.format("%s_%s", dataFromQ.getTablespace(), dataFromQ.getTable());
                String sql;
                Object[] valueArrays = null;
                if (dataFromQ.getOperation().equals(DataChange.Operation.insert)) {
                    List<Object> params = new ArrayList<>();
                    StringBuilder where = new StringBuilder();

                    String and = " and ";
                    String questionMark = " ? ";
                    dataFromQ.getKeys().forEach((x, y) -> {
                        if (where.length() == 0) {
                            where.append(String.format("%s = %s", x, questionMark));
                        } else {
                            where.append(String.format("%s %s = %s", and, x, questionMark));
                        }
                        params.add(y);
                    });

                    if (dataFromQ.getOrgOperation().equals(DataChange.Operation.update) && ludb().fetch(String.format("Select 1 from %s where %s", String.format("%s.%s", getLuType().luName, tableName), where.toString()), params.toArray()).firstValue() != null) {
                        dataFromQ.setOperation(DataChange.Operation.update);
                        sql = dataFromQ.toSql(DataChange.Operation.update, String.format("%s.%s", getLuType().luName, tableName));
                        valueArrays = dataFromQ.sqlValues();
                        dataFromQ.setOperation(DataChange.Operation.insert);
                    } else {
                        sql = dataFromQ.toSql(DataChange.Operation.upsert, String.format("%s.%s", getLuType().luName, tableName));
                    }
                } else {
                    sql = dataFromQ.toSql(dataFromQ.getOperation(), String.format("%s.%s", getLuType().luName, tableName));
                }
                if (valueArrays == null) valueArrays = dataFromQ.sqlValues();

                if (((("1").equals(getThreadGlobals("childCrossInsts" + tableName.toUpperCase())) || isFirstSync())) && ("true").equals(IIDF_EXTRACT_FROM_SOURCE)) {
                    //Get DC Keys
                    String prefix = "";
                    StringBuilder recKeys = new StringBuilder();
                    List<Object> keyVals = new ArrayList<>();
                    Map<String, Object> dcKeys = dataFromQ.getKeys();
                    for (Map.Entry<String, Object> dcKeysEnt : dcKeys.entrySet()) {
                        keyVals.add(dcKeysEnt.getValue());
                        recKeys.append(prefix).append(dcKeysEnt.getKey()).append(" = ? ");
                        prefix = " and ";
                    }

                    if (dataFromQ.getOperation().equals(DataChange.Operation.delete)) {
                        //Check if DC keys exists in LU table
                        if (ludb().fetch("select 1 from " + getLuType().luName + "." + tableName + " where " + recKeys.toString(), keyVals.toArray()).firstValue() != null) {
                            if (idfTablesDeleteList.containsKey(fullSourceTableName)) {
                                List<Map<String, Object>> tblKeys = idfTablesDeleteList.get(fullSourceTableName);
                                tblKeys.add(dcKeys);
                            } else {
                                List<Map<String, Object>> tblKeys = new ArrayList<>();
                                tblKeys.add(dcKeys);
                                idfTablesDeleteList.put(fullSourceTableName, tblKeys);
                            }
                        }
                        //Check if DC keys exists in idfinder delete list
                    } else if (dataFromQ.getOperation().equals(DataChange.Operation.insert)) {
                        List<Map<String, Object>> tblKeys = idfTablesDeleteList.get(fullSourceTableName);
                        if (tblKeys != null) {
                            Iterator<Map<String, Object>> it = tblKeys.iterator();
                            outsideloop:
                            while (it.hasNext()) {
                                Map<String, Object> dcKey = it.next();
                                for (Map.Entry<String, Object> msgKeys : dcKey.entrySet()) {
                                    if (!msgKeys.getValue().equals(dcKeys.get(msgKeys.getKey()))) {
                                        continue outsideloop;
                                    }
                                }
                                it.remove();
                            }
                        }
                    }
                    try {
                        ludb().execute(fnIIDFReplaceSqliteKeywords(sql), valueArrays);
                    } catch (SQLiteException e) {
                        if (e.getMessage().contains("SQLITE_CONSTRAINT") && getTranslationsData("trnIIDFIgnoreSqliteConstraint").keySet().contains(tableName)) {
                            fnIIDFDelRecDueToIgnoreSqliteConstraint(tableName, dataFromQ.getKeys());
                            return;
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }
        //Loop throw the map and delete tables keys from idfinder cache
        Map<String, String> keys = null;
        for (Map.Entry<String, List<Map<String, Object>>> keysToRemove : idfTablesDeleteList.entrySet()) {
            for (Map<String, Object> DCKeys : keysToRemove.getValue()) {
                keys = new HashMap<>();
                for (Map.Entry<String, Object> keyEnt : DCKeys.entrySet()) {
                    keys.put(keyEnt.getKey(), keyEnt.getValue() == null ? null : keyEnt.getValue().toString());
                }
                if (!"false".equals(((TableObject) getLuType().ludbObjects.get(keysToRemove.getKey())).getEnabledTablePopulationObjects().get(0).iidFinderProp.isStoreData))
                    IidFinderApi.addDeleteFinder(getLuType().luName, getInstanceID(), keysToRemove.getKey(), keys);
            }
        }
        if (keys != null) IidFinderApi.runDeleteFinder(getLuType().luName, getInstanceID());
        if (dataFromQ != null) {
            Map<String, DataChange> deleteOrphan = getThreadGlobals("DELETE_ORPHAN") == null ? null : (Map<String, DataChange>) getThreadGlobals("DELETE_ORPHAN");
            if (deleteOrphan != null) {
                setThreadGlobals("DELETE_ORPHAN_IGNORE_REPLICATES", "Y");
                fnIIDFDeleteFromChilds(deleteOrphan, null);//Execute delete orphan
                setThreadGlobals("DELETE_ORPHAN_IGNORE_REPLICATES", "N");
            }
        }
        if (false) UserCode.yield(new Object[]{null});        
    }

    @SuppressWarnings({"unchecked"})
    @out(name = "rec_deleted", type = Boolean.class, desc = "")
    private static Boolean fnIIDFGetRecData(String table_name, String table_where, String targetIid, String pos, Long op_ts, Boolean send_rec_to_kafka, Long cr_ts) throws Exception {
        //Fetch table orphan records and call fnIIDFSendRecBack2Kafka to send it to kafka
        boolean rec_deleted = false;
        Set<String> tblColArr = getLuType().ludbObjects.get(table_name).ludbColumnMap.keySet();
        try (Db.Rows rs = ludb().fetch(String.format("Select %s From %s.%s where %s", String.join(",", tblColArr), getLuType().luName, table_name, table_where))) {
            List<Object> vals = new LinkedList();
            for (Db.Row row : rs) {
                if (!rec_deleted) rec_deleted = true;
                for (Object colVal : row.cells()) {
                    if (colVal == null) {
                        vals.add(JSONObject.NULL);
                    } else {
                        vals.add(colVal);
                    }
                }
                if (send_rec_to_kafka)
                    fnIIDFSendRecBack2Kafka(table_name, tblColArr, vals, targetIid, pos, op_ts, "D", cr_ts);
                vals.clear();
            }
        }
        return rec_deleted;
    }

    private static void fnIIDFFetchIIDDatachanges(String IID, AtomicLong maxHandleTime, AtomicLong minDeltaOPTimestamp, List<Object> paramsStats) throws Exception {
        String selectCount = "select count(DATA_CHANGE) from %s.IIDF_QUEUE";
        String insertToFailedTable = "insert into %s.resync_instance (iid) values (?)";
        String insertStmt = "insert into %s.IIDF_QUEUE (DATA_CHANGE , iid , opt_timeStamp , current_timeStamp, POS ) values (?,?,?,?,?)";
        Map<String, Map<String, Integer>> statsMap = new HashMap<>();

        AtomicInteger dcCounter = new AtomicInteger();
        dcCounter.set(Integer.parseInt(ludb().fetch(String.format(selectCount, getLuType().luName)).firstValue().toString()));
        Instant start = Instant.now();
        Set<Long> hashedDC = new HashSet<>();
        AtomicLong dbExecuteStartTimeTTL = new AtomicLong(0);
        try (Stream<DataChange> dataChangesFromDelta = IidFinderApi.getDeltas(getLuType().luName, IID, maxHandleTime.get(),true)) {//Fetch all DCs newer then maxHandleTime
            dataChangesFromDelta.forEach(dc -> {//Loop throw all DCs and insert to IIDF_QUEUE LU Table
                long jsonHashed = dc.toJson().hashCode();
                if (hashedDC.contains(jsonHashed)) return;
                hashedDC.add(jsonHashed);
                try {
                    if (IID_DATACHANGE_LIMIT != null && !"".equals(IID_DATACHANGE_LIMIT) && dcCounter.getAndIncrement() == Integer.parseInt(IID_DATACHANGE_LIMIT)) {
                        db("dbCassandra").execute(String.format(insertToFailedTable, getLuType().getKeyspaceName()), IID);
                        skipSync();
                    }

                    if (minDeltaOPTimestamp.get() == -1 || minDeltaOPTimestamp.get() > dc.getOpTimestamp()) {
                        minDeltaOPTimestamp.set(dc.getOpTimestamp());
                    }

                    if (maxHandleTime.get() == 0 || maxHandleTime.get() < dc.getHandleTimestamp()) {
                        maxHandleTime.set(dc.getHandleTimestamp());
                    }

                    if (Boolean.parseBoolean(STATS_ACTIVE)) {
                        Map<String, Integer> tableStatsMap = statsMap.get(dc.getTargetTableName());
                        if (tableStatsMap == null) tableStatsMap = new HashMap<>();
                        fnIIDFSetStats("" + dc.getOperation(), tableStatsMap, dc.isIidChanged());
                        statsMap.put(dc.getTargetTableName(), tableStatsMap);
                    }
                    Instant dbExecuteStartTime = Instant.now();
                    ludb().execute(String.format(insertStmt, getLuType().luName), dc.toJson(), IID, dc.getOpTimestamp(), dc.getCurrentTimestamp(), dc.getPos());
                    dbExecuteStartTimeTTL.set(dbExecuteStartTimeTTL.get() + Duration.between(dbExecuteStartTime, Instant.now()).toMillis());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        paramsStats.add((Duration.between(start, Instant.now()).toMillis()) - dbExecuteStartTimeTTL.get());//Time To Fetch Deltas From IDFInder API
        paramsStats.add(dbExecuteStartTimeTTL);//Time To Insert Deltas To IIDF_QUEUE Table
        paramsStats.add(hashedDC.size());//Total Deltas Count
        setThreadGlobals("IID_DELTA_COUNT", hashedDC.size());
    }

    private static String fnIIDFFilterResult(AtomicLong minDeltaOPTimestamp, List<Object> params) throws SQLException {
        long maxQueueOPTimestamp = 0;
        final String IIDFQueueMaxOPTS = "select max(opt_timeStamp) from %s.IIDF_QUEUE";
        Object maxOPTS = ludb().fetch(String.format(IIDFQueueMaxOPTS, getLuType().luName)).firstValue();
        if (maxOPTS != null) maxQueueOPTimestamp = Long.parseLong("" + maxOPTS.toString());
        if (minDeltaOPTimestamp.get() > maxQueueOPTimestamp) {
            params.add(minDeltaOPTimestamp);
            return " Where opt_timeStamp >= ? ";
        } else {
            return "";
        }
    }

    private static String fnIIDFReplaceSqliteKeywords(String sql) {
        List<String> savedWords = Arrays.stream(getGlobal("SQLITE_RESERVED_KEYWORDS", getLuType().luName).split(",")).map(String::trim).collect(Collectors.toList());
        String updatedSql = sql;
        for (String word : savedWords) {
            if (updatedSql.startsWith("UPDATE")) {
                String regex = String.format("[,\\s*]\\b%s\\b\\s*=", word);
                Pattern keywordInUpdate = Pattern.compile(regex);
                Matcher matcher = keywordInUpdate.matcher(updatedSql);
                if (matcher.find(0)) {
                    String found = matcher.group();
                    updatedSql = updatedSql.replaceFirst(found, found.replace(word, "'" + word + "'"));
                }
            }
            if (updatedSql.startsWith("INSERT")) {
                String regex = String.format("[\\(,]\\s*\\b%s\\b\\s*[,\\)]", word);
                Pattern keywordInInsert = Pattern.compile(regex);
                Matcher matcher = keywordInInsert.matcher(updatedSql);
                if (matcher.find()) {
                    String found = matcher.group(0);
                    updatedSql = updatedSql.replace(found, found.replace(word, "'" + word + "'"));
                }
            }
        }
        return updatedSql;
    }

    private static boolean fnIIDFProcessDatachange(Map<String, Map<String, String>> trnIIDFCrossIIDTablesList, DataChange.Operation Operation, List<DataChange> replicateRequests, String tableName, DataChange dataChange, Map<String, Long> debugTiming, List<Object[]> savedDC, String IID, boolean isIidChanged, Map<String, DataChange> deleteOrphan, long maxUpdated, Set<String> trnIIDFForceReplicateOnDelete) throws Exception {
        Instant startTime;
        long optTimeStamp = dataChange.getOpTimestamp();
        long currTimeStamp = dataChange.getCurrentTimestamp();
        //If data change operation is of type REPLICAT, Add datachange to replicate list to execute after all data changes are processed
        if (Operation.equals(DataChange.Operation.replicate)) {
            Map<String, String> trnIIDFCrossIIDTablesListRS = trnIIDFCrossIIDTablesList.get(tableName.toUpperCase());
            if (!("true").equals(IIDF_EXTRACT_FROM_SOURCE) || (("true").equals(IIDF_EXTRACT_FROM_SOURCE)) && trnIIDFCrossIIDTablesList.keySet().contains(tableName.toUpperCase()) && Boolean.parseBoolean(trnIIDFCrossIIDTablesListRS.get("ACTIVE"))) {
                replicateRequests.add(dataChange);
            }
        } else {
            setThreadGlobals("fnIIDFExecTableCustomCode", "Before");
            fnIIDFExecTableCustomCode(dataChange, false); //Check if user added any specific activity for data change table before the LUDB sql execution
            if (getThreadGlobals("IIDF_SKIP_DC") != null) {
                setThreadGlobals("IIDF_SKIP_DC", null);
                return false;
            }
            if (dataChange.getOperation().equals(DataChange.Operation.update) && dataChange.isIidChanged() && dataChange.getBeforeValues().size() == 0) {
                startTime = Instant.now();
                fnIIDFConvertDCToUpsert(dataChange);
                debugTiming.put("fnIIDFConvertDCToUpsert", Duration.between(startTime, Instant.now()).toMillis());
            }


            startTime = Instant.now();
            String sql;
            if (Operation.equals(DataChange.Operation.insert)) {
                sql = dataChange.toSql(DataChange.Operation.upsert, String.format("%s.%s", getLuType().luName, tableName));
                Operation = DataChange.Operation.upsert;
            } else {
                sql = dataChange.toSql(dataChange.getOperation(), String.format("%s.%s", getLuType().luName, tableName));
            }            
            Object[] valueArrays = dataChange.sqlValues();//Get messages values to use for execute
            if ((System.currentTimeMillis() - dataChange.getCurrentTimestamp() / 1000) <= Long.valueOf(IIDF_SOURCE_DELAY) * 60 * 1000 && !trnIIDFForceReplicateOnDelete.contains(tableName))//Check if it is needed to insert data change to queue table
                savedDC.add(new Object[]{dataChange.toJson(), IID, optTimeStamp, currTimeStamp, dataChange.getPos()});
            debugTiming.put("Setting operation", Duration.between(startTime, Instant.now()).toMillis());
            //If data change operation is of type UPDATE and data change table is croos instance table or data change cause instance change
            //Or If data change operation is of type DELETE and data change table is croos instance
            //Add data change to delete orphan table list to execute after all data changes are processed and execute data change
            if (Operation.equals(DataChange.Operation.update) && isIidChanged && (dataChange.getBeforeValues() == null || dataChange.getBeforeValues().size() == 0)) {
                startTime = Instant.now();
                fnIIDFCleanIIDFinderCTables(dataChange);
                debugTiming.put("fnIIDFCleanIIDFinderCTables", Duration.between(startTime, Instant.now()).toMillis());
            }

            if ((Operation.equals(DataChange.Operation.update) && isIidChanged) || Operation.equals(DataChange.Operation.delete)) {
                if (!deleteOrphan.containsKey(tableName) || (deleteOrphan.containsKey(tableName) && deleteOrphan.get(tableName).getOperation().equals(DataChange.Operation.update))) {
                    deleteOrphan.put(tableName, dataChange);
                }
                startTime = Instant.now();
                try {                    
                    ludb().execute(fnIIDFReplaceSqliteKeywords(sql), valueArrays);
                }catch (SQLiteException e) {
                    if (e.getMessage().contains("SQLITE_CONSTRAINT") && getTranslationsData("trnIIDFIgnoreSqliteConstraint").keySet().contains(tableName)) {
                        fnIIDFDelRecDueToIgnoreSqliteConstraint(tableName, dataChange.getKeys());
                        return true;
                    } else {
                        throw e;
                    }
                }
                debugTiming.put("ludb().execute 1", Duration.between(startTime, Instant.now()).toMillis());
            } else if (Operation.equals(DataChange.Operation.upsert) && dataChange.getKeys().values().contains(null)) {//If PK holds null value for insert statment we run update instead
                startTime = Instant.now();
                fnIIDFExecInsNullPK(dataChange);
                debugTiming.put("fnIIDFExecInsNullPK", Duration.between(startTime, Instant.now()).toMillis());
            } else {//If data change is a normal data change execute it
                startTime = Instant.now();
                try {                    
                    ludb().execute(fnIIDFReplaceSqliteKeywords(sql), valueArrays);
                }catch (SQLiteException e) {
                    if (e.getMessage().contains("SQLITE_CONSTRAINT") && getTranslationsData("trnIIDFIgnoreSqliteConstraint").keySet().contains(tableName)) {
                        fnIIDFDelRecDueToIgnoreSqliteConstraint(tableName, dataChange.getKeys());
                        return true;
                    } else {
                        throw e;
                    }
                }
                debugTiming.put("ludb().execute 2", Duration.between(startTime, Instant.now()).toMillis());
            }
            if (dataChange.getCurrentTimestamp() > (maxUpdated - Long.valueOf(IIDF_KEEP_HISTORY_TIME) * 60 * 60 * 1000 * 1000)) {
                startTime = Instant.now();
                fnIIDFPopRecentUpdates(valueArrays, optTimeStamp, IID, sql, maxUpdated, currTimeStamp, dataChange.getHandleTimestamp());//Update IIDF_RECENT_UPDATES with data change details
                debugTiming.put("fnIIDFPopRecentUpdates", Duration.between(startTime, Instant.now()).toMillis());
            }
        }
        return true;
    }

    private static void fnIIDFDelRecDueToIgnoreSqliteConstraint(String tableName, Object tableKeys) throws Exception {
        Map<String, Object> tblKeys = (Map<String, Object>) tableKeys;
        String delStmt = "Delete from %s where ";
        String prefix = "";
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> ent : tblKeys.entrySet()) {
            where.append(prefix + ent.getKey() + " = ? ");
            prefix = " and ";
            params.add(ent.getValue());
        }
        ludb().execute(String.format(delStmt, tableName) + where.toString(), params.toArray());
    }

    private static void fnIIDFCheckProcessTime(Map<String, Long> debugTiming, long ttlProccDC, String IID, DataChange dataChange) {
        debugTiming.put("Total Datachange proccess time", ttlProccDC);
        LinkedHashMap<String, Long> sortedMap = new LinkedHashMap<>();
        debugTiming.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(x -> sortedMap.put(x.getKey(), x.getValue()));
        StringBuilder sb = new StringBuilder().append("\n");
        int i = 0;
        for (Map.Entry<String, Long> ent : sortedMap.entrySet()) {
            if (ent.getValue() == 0) continue;
            sb.append("IID Logging ").append(IID).append(" ").append(i).append(":").append(ent.getKey()).append(" = ").append(ent.getValue()).append("\n");
            i++;
        }
        log.warn(String.format("%s IID Logging %s DC:%s", sb.toString(), IID, dataChange.toJson()));
    }

    private static DataChange fnIIDFConvertDatachange(Map<String, Long> debugTiming, Db.Row row) {
        Instant startTime = Instant.now();
        DataChange dataChange = DataChange.fromJson(row.cell(0) + "");
        debugTiming.put("Converting string to Datachange", Duration.between(startTime, Instant.now()).toMillis());
        return dataChange;
    }

    private static List<String> fnIIDFFetchTableColumns(Map<String, Long> debugTiming, DataChange dataChange) {
        Instant startTime = Instant.now();
        List<String> culListStr = new ArrayList<>();//Fetch table column list and add them to list
        List<LudbColumn> culList = getLuType().ludbObjects.get(dataChange.getTargetTableName()).getOrderdObjectColumns();
        for (LudbColumn culName : culList) {
            culListStr.add(culName.getName());
        }
        debugTiming.put("Fetching LU table's columns", Duration.between(startTime, Instant.now()).toMillis());
        return culListStr;
    }

    private static boolean fnIIDFRetainUnusedColumns(DataChange.Operation Operation, List<String> culListStr, DataChange dataChange, Map<String, Long> debugTiming) {
        Instant startTime = Instant.now();
        if (!Operation.equals(DataChange.Operation.delete) && !Operation.equals(DataChange.Operation.replicate) && dataChange.getValues() != null) {//Remove columns from data change that doesn't exists in LU table
            dataChange.getValues().keySet().retainAll(culListStr);
            if (dataChange.getValues().isEmpty()) return false;
        }
        debugTiming.put("Removing not existed columns", Duration.between(startTime, Instant.now()).toMillis());
        return true;
    }

    private static void fnIIDFCountIIDChange(boolean isIidChanged) {
        if (isIidChanged && getThreadGlobals("is_iid_change") != null) {
            long cnt = Long.parseLong(getThreadGlobals("is_iid_change") + "");
            cnt++;
            setThreadGlobals("is_iid_change", cnt);
        } else if (isIidChanged) {
            setThreadGlobals("is_iid_change", 1);
        }
    }

    private static void fnIIDFCleanIIDFQueue(List<Object[]> savedDC) throws SQLException {
        String IIDFQueueDelete = "DELETE FROM %s.IIDF_QUEUE";
        String insertStmt = "insert into %s.IIDF_QUEUE (DATA_CHANGE , iid , opt_timeStamp , current_timeStamp, POS ) values (?,?,?,?,?)";
        ludb().execute(String.format(IIDFQueueDelete, getLuType().luName));//Clean IIDF_QUEUE table
        for (Object[] dcToKeepValues : savedDC) {
            ludb().execute(String.format(insertStmt, getLuType().luName), dcToKeepValues);
        }
    }

    private static void fnIIDFCleanIIDFRecentUpdates(long maxUpdated) throws SQLException {
        String IIDFRecentUpdatesSDelete = "delete from %s.IIDF_RECENT_UPDATES where \"current_timeStamp\" < ?";
        long time_to_keep = Long.valueOf(IIDF_KEEP_HISTORY_TIME);//Check which records to delete from IIDF_RECENT_UPDATES
        ludb().execute(String.format(IIDFRecentUpdatesSDelete, getLuType().luName), maxUpdated - time_to_keep * 60 * 60 * 1000 * 1000);
    }

    @type(RootFunction)
    @out(name = "IID", type = String.class, desc = "")
    @out(name = "MAX_DC_UPDATE", type = Long.class, desc = "")
    public static void fnIIDFConsMsgs(String IID) throws Exception {
        final String IIDFQueue = "select DATA_CHANGE, rowid from %s.IIDF_QUEUE %s order by \"current_timeStamp\" , POS";
        final String IIDF = "select MAX_DC_UPDATE from %s.IIDF ";
        Map<String, Map<String, Integer>> statsMap = new HashMap<>();
        long maxUpdated = System.currentTimeMillis() * 1000;
        List<Object[]> savedDC = new ArrayList<>();
        List<DataChange> replicateRequests = new ArrayList<>();
        Map<String, DataChange> deleteOrphan = new LinkedHashMap<>();
        List<Long> crossInstanceList = new ArrayList<>();
        AtomicLong maxHandleTime = new AtomicLong(0);
        DataChange dataChange;
        Map<String, Map<String, String>> trnIIDFCrossIIDTablesList = getTranslationsData("trnIIDFCrossIIDTablesList");
        Set<String> trnIIDFForceReplicateOnDelete = getTranslationsData("trnIIDFForceReplicateOnDelete").keySet();
        setThreadGlobals("SYNC_TIME", Instant.now());
        List<Object> paramsStats = new LinkedList<>();
        Map<String, Long> debugTiming = new HashMap<>();
        setThreadGlobals("DEBUG_TIMING_MAP", debugTiming);

        fnIIDFExecPreSyncUserActivity();

        Object maxDCUpdate = ludb().fetch(String.format(IIDF, getLuType().luName)).firstValue();//Fetch the latest data change time
        if (maxDCUpdate != null) maxHandleTime.set(Long.parseLong(maxDCUpdate.toString()));

        AtomicLong minDeltaOPTimestamp = new AtomicLong(-1);
        Instant start;

        fnIIDFFetchIIDDatachanges(IID, maxHandleTime, minDeltaOPTimestamp, paramsStats);

        List<Object> paramsList = new ArrayList<>();
        String filterQueue = fnIIDFFilterResult(minDeltaOPTimestamp, paramsList);

        start = Instant.now();
        try (Db.Rows iidfQRS = ludb().fetch(String.format(IIDFQueue, getLuType().luName, filterQueue), paramsList.toArray())) {
            for (Db.Row row : iidfQRS) {
                Instant dcProcess = Instant.now();

                if (row.cell(0) == null) {
                    throw new RuntimeException(String.format("fnIIDFConsMsgs: Found NULL value for DC in %s.IIDF_QUEUE!", getLuType().luName));
                }

                dataChange = fnIIDFConvertDatachange(debugTiming, row);
                if (dataChange.isCustomSql()) {
                    String sql = dataChange.toSql().replaceFirst("(?i)insert(?!\\s+or\\b)", "INSERT OR REPLACE");
                    ludb().execute(sql, dataChange.sqlValues());
                    continue;
                }
                DataChange.Operation Operation = dataChange.getOperation();//Get datachange operation type from data change
                String tableName = dataChange.getTargetTableName();//Get LU table name from data change

                if (dataChange.getOrgOperation().equals(DataChange.Operation.replicate) && !Operation.equals(DataChange.Operation.replicate)) {
                    if (fnIIDFCheckRExists(dataChange, debugTiming)) continue;
                }

                if (dataChange.getOperation().equals(DataChange.Operation.insert) && dataChange.getOrgOperation().equals(DataChange.Operation.update)) {
                    if(!fnIIDFForceTableSyncFromSource(dataChange)){
                        continue;
                    }
                }

                if (fnIIDFExecNonLUTableDelta(dataChange)) continue;

                fnIIDFUpdateSelectiveTable(dataChange, deleteOrphan, debugTiming);

                List<String> culListStr = fnIIDFFetchTableColumns(debugTiming, dataChange);//Fetch table column list and add them to list

                if (!fnIIDFRetainUnusedColumns(Operation, culListStr, dataChange, debugTiming)) continue;

                Instant startTime = Instant.now();
                String targetIid = dataChange.getTargetIid();//Get targetIid from data change
                boolean isCrossIID = dataChange.isCrossIID();//Indicate if table is cross instance table
                boolean isIidChanged = dataChange.isIidChanged();//if data change cause instance change
                fnIIDFCountIIDChange(isIidChanged);
                debugTiming.put("Getting datachange info 2", Duration.between(startTime, Instant.now()).toMillis());

                if (!fnIIDFProcessDatachange(trnIIDFCrossIIDTablesList, Operation, replicateRequests, tableName, dataChange, debugTiming, savedDC, IID, isIidChanged, deleteOrphan, maxUpdated, trnIIDFForceReplicateOnDelete)) {
                    continue;
                }

                if (((Operation.equals(DataChange.Operation.upsert) || Operation.equals(DataChange.Operation.insert)) && (isCrossIID || isIidChanged)) || (Operation.equals(DataChange.Operation.update) && isIidChanged) || (dataChange.getOrgOperation().equals(DataChange.Operation.replicate) && !Operation.equals(DataChange.Operation.replicate))) {
                    crossInstanceList.add(Long.parseLong(row.cell(1) + ""));//Add datachange for cross instance list
                }

                setThreadGlobals("fnIIDFExecTableCustomCode", "After");
                fnIIDFExecTableCustomCode(dataChange, true);//Check if user added any specific activity for data change table after the LUDB sql execution
                
                long ttldcProcess = Duration.between(dcProcess, Instant.now()).toMillis();
                if (ttldcProcess > Long.parseLong(DEBUG_LOG_THRESHOLD)) {
                    fnIIDFCheckProcessTime(debugTiming, ttldcProcess, IID, dataChange);
                }
            }
            paramsStats.add((Duration.between(start, Instant.now()).toMillis()));//execute_deltas Time
        }
        
        if ("true".equalsIgnoreCase(STATS_ACTIVE)) {
            fnIIDFUpdateStats(statsMap);//Updating IIDF_STATISTICS table with all data changes received for instance
        }
        
        start = Instant.now();
        for (DataChange repDC : replicateRequests) {//Execute all REPLICATE data change
            fnIIDFFetchInstanceData(repDC.getTargetTableName(), repDC.getKeys(), null, repDC.getTargetIid(), repDC.getBeforeValues().keySet(), repDC.getPos(), repDC.getOpTimestamp(), repDC.getCurrentTimestamp());
        }
        paramsStats.add((Duration.between(start, Instant.now()).toMillis()));//execute_replicates
        paramsStats.add(replicateRequests.size());//replicates_count

        fnIIDFDeleteFromChilds(deleteOrphan, paramsStats);//Execute delete orphan

        if (("true").equals(IIDF_EXTRACT_FROM_SOURCE)) {
            fnIIDFCheckCrossIns(crossInstanceList, paramsStats);//Executing cross instance check
        }

        setThreadGlobals("STATS", paramsStats);

        fnIIDFCleanIIDFQueue(savedDC);//Clean IIDF_QUEUE table

        fnIIDFCleanIIDFRecentUpdates(maxUpdated);//Check which records to delete from IIDF_RECENT_UPDATES

        UserCode.yield(new Object[]{IID, maxHandleTime});
    }

    public static void fnIIDFCheckIfInstanceFound() throws Exception {
        //Check if LUDB real root table as at least 1 record if not throwing instance doesn't exists exception, even if one table has entry break
        final String select = "select 1 from %s.%s limit 1";
        boolean rootFnd = false;
        if (!"".equals(getLuType().ludbGlobals.get("IIDF_ROOT_TABLE_NAME").trim())) {
            for (String tbl : getLuType().ludbGlobals.get("IIDF_ROOT_TABLE_NAME").split(",")) {
                if (ludb().fetch(String.format(select, getLuType().luName, tbl.trim())).firstValue() != null) {
                    rootFnd = true;
                    break;
                }
            }
        } else {
            rootFnd = true;
        }

        if (!rootFnd) {
            LogEntry lg = new LogEntry("INSTANCE NOT FOUND!", MsgId.INSTANCE_MISSING);
            lg.luInstance = getInstanceID();
            lg.luType = getLuType().luName;
            throw new InstanceNotFoundException(lg, null);
        }
    }

    private static void fnIIDFFetchInstanceData(String table, Map<String, Object> root_table_pk, Map<Integer, Map<String, Object>> tableDataMap, String targetIid, Set<String> linked_fields, String pos, Long op_ts, Long cr_ts) throws Exception {
        //Fetch Instances data based and send it to kafka
        //We first start with one table record and fetch all its childs records recrusive
        if (tableDataMap == null) tableDataMap = new HashMap<>();
        Set<String> linked_fieldsSet = null;


        if (root_table_pk != null) {//Get table's primary keys
            linked_fieldsSet = linked_fields;
            StringBuilder whereSB = new StringBuilder().append(" where ");
            String prefix = "";
            Object[] params = new Object[root_table_pk.size()];
            int i = 0;
            for (String table_column_name : root_table_pk.keySet()) {
                whereSB.append(prefix).append(table_column_name).append(" = ? ");
                params[i] = root_table_pk.get(table_column_name);
                i++;
                prefix = " and ";
            }

            Set<String> tblColSet = getLuType().ludbObjects.get(table).ludbColumnMap.keySet();//Get table's columms
            Db.Row row = ludb().fetch(String.format("select %s from %s.%s %s", String.join(",", tblColSet), getLuType().luName, table, whereSB.toString()), params).firstRow();
            if (row.isEmpty()) {
                log.warn(String.format("Can't retrieve table's data, Key not found!, Table Name:%s, Key %s", table, Arrays.toString(params)));
                return;
            }

            Map<String, Object> rootTableMap = new HashMap<>(row);
            tableDataMap.put(1, rootTableMap);
        }

        Map<String, Map<String, String>> trnIIDFCrossIIDTablesList = getTranslationsData("trnIIDFCrossIIDTablesList");
        LudbObject ludbObject = getLuType().ludbObjects.get(table);//Get table's childs
        outerloop:
        for (LudbObject child_table : ludbObject.childObjects) {
            String childTableName = child_table.ludbObjectName;
            Map<String, String> trnIIDFCrossIIDTablesListRS = trnIIDFCrossIIDTablesList.get(childTableName.toUpperCase());
            if (trnIIDFCrossIIDTablesList.keySet().size() > 0 && (!trnIIDFCrossIIDTablesList.keySet().contains(childTableName.toUpperCase()) || !Boolean.parseBoolean(trnIIDFCrossIIDTablesListRS.get("ACTIVE")))) {
                continue;
            }
            Map<Integer, Map<String, Object>> sonTableDataMap = new HashMap<>();
            List<LudbRelationInfo> relToChild = getLuType().getLudbPhysicalRelations().get(ludbObject.ludbObjectName).get(child_table.k2StudioObjectName);
            StringBuilder sqlWhere = new StringBuilder();
            Set<String> ParentColumnsSet = new LinkedHashSet<>();
            int i = 0;
            String prefix = "";
            for (LudbRelationInfo chRel : relToChild) {//Set table's to child links
                if (ParentColumnsSet.contains(chRel.from.get("column").toUpperCase())) continue;
                ParentColumnsSet.add(chRel.from.get("column").toUpperCase());
                sqlWhere.append(prefix).append("(").append(childTableName).append(".").append(chRel.to.get("column")).append(" = ?) ");
                prefix = " and ";
            }

            if (root_table_pk != null && linked_fieldsSet != null) {//Validating replicate links match the son table links, Only for son of replicate table
                if (linked_fieldsSet.size() != ParentColumnsSet.size()) {
                    continue;
                } else {
                    for (String linkField : ParentColumnsSet) {
                        if (!linked_fieldsSet.contains(linkField.toUpperCase())) continue outerloop;
                    }
                }
            }

            String where = sqlWhere.toString();
            sqlWhere = new StringBuilder();
            Set<String> tblColSet = getLuType().ludbObjects.get(childTableName).ludbColumnMap.keySet();//Get child table columns
            int cnt = 1;
            int ttl = tableDataMap.size();
            prefix = "";
            Map<Integer, Map<String, Object>> recMap = new HashMap<>();
            for (Map<String, Object> tableDataRec : tableDataMap.values()) {//Loop throw all parent table records
                sqlWhere.append(prefix).append(where);
                prefix = " or ";
                recMap.put(cnt, tableDataRec);
                if (ttl + cnt > 1000) {
                    if (cnt % 1000 == 0) {
                        //Fetch table records
                        fnIIDFFetchTableData(recMap, ParentColumnsSet, tblColSet, sqlWhere.toString(), childTableName, sonTableDataMap, targetIid, pos, op_ts, cr_ts);
                        sqlWhere = new StringBuilder();
                        prefix = "";
                        cnt = 0;
                        recMap = new HashMap<>();
                    }
                } else if (ttl + cnt > 100) {
                    if (cnt % 100 == 0) {
                        //Fetch table records
                        fnIIDFFetchTableData(recMap, ParentColumnsSet, tblColSet, sqlWhere.toString(), childTableName, sonTableDataMap, targetIid, pos, op_ts, cr_ts);
                        sqlWhere = new StringBuilder();
                        prefix = "";
                        cnt = 0;
                        recMap = new HashMap<>();
                    }
                } else if (ttl + cnt > 10) {
                    if (cnt % 10 == 0) {
                        //Fetch table records
                        fnIIDFFetchTableData(recMap, ParentColumnsSet, tblColSet, sqlWhere.toString(), childTableName, sonTableDataMap, targetIid, pos, op_ts, cr_ts);
                        sqlWhere = new StringBuilder();
                        prefix = "";
                        cnt = 0;
                        recMap = new HashMap<>();
                    }
                }
                cnt++;
                ttl--;
            }
            //Fetch table records
            if (sqlWhere.length() == 0) sqlWhere.append(where);
            fnIIDFFetchTableData(recMap, ParentColumnsSet, tblColSet, sqlWhere.toString(), childTableName, sonTableDataMap, targetIid, pos, op_ts, cr_ts);
            //cnt = 0;
            //recMap = new HashMap<>();
            //Fetch child table childs records
            fnIIDFFetchInstanceData(childTableName, null, sonTableDataMap, targetIid, null, pos, op_ts, cr_ts);
        }
    }

    @SuppressWarnings({"unchecked"})
    private static void fnIIDFFetchTableData(Map<Integer, Map<String, Object>> recMap, Set<String> ParentColumnsSet, Set<String> tblColSet, String sqlWhere, String child_table_name, Map<Integer, Map<String, Object>> sonTableDataMap, String targetIid, String pos, Long op_ts, Long cr_ts) throws Exception {
        Object[] params = new Object[(ParentColumnsSet).size() * recMap.size()];
        int i = 0;
        if (recMap.size() == 0) return;
        for (Map<String, Object> mapRec : recMap.values()) {
            for (String parentTableColumnVal : ParentColumnsSet) {
                params[i] = mapRec.get(parentTableColumnVal);//Prepare params for execution
                i++;
            }
        }

        //Fetch records
        try (Db.Rows rs = ludb().fetch(String.format("select %s from %s.%s where %s", String.join(",", (tblColSet)), getLuType().luName, child_table_name, sqlWhere), params)) {
            i = 0;
            for (Db.Row row : rs) {
                Map<String, Object> sonMap = new LinkedHashMap<>();
                int colCnt = 0;
                List<Object> vals = new LinkedList();
                for (String column : tblColSet) {
                    sonMap.put(column.toUpperCase(), row.cell(colCnt));
                    if (row.cell(colCnt) == null) {
                        vals.add(JSONObject.NULL);
                    } else {
                        vals.add(row.cell(colCnt));
                    }
                    colCnt++;
                }
                sonTableDataMap.put(i, sonMap);//Add table column values to map
                i++;
                //Send record to kafka
                fnIIDFSendRecBack2Kafka(child_table_name, tblColSet, vals, targetIid, pos, op_ts, "R", cr_ts);
                vals.clear();
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    private static void fnIIDFExecTableCustomCode(Object dc, Boolean post_exec) throws Exception {
        DataChange dataChange = (DataChange) dc;
        String tableName = dataChange.getTargetTableName();
        //Check if table name exists in translaion
        Map<String, Long> debugTiming = (Map<String, Long>) getThreadGlobals("DEBUG_TIMING_MAP");
        Map<String, Object> keys = new HashMap<>();
        keys.put("lu_name", getLuType().luName);
        keys.put("table_name", tableName);
        keys.put("post_exec", post_exec + "");
        MTable mtable = MTables.get("mtExecUserActivity");
        Map<String, Object> translationOutput = mtable.mapByKey(keys, MTable.Feature.caseInsensitive);
        if (translationOutput != null && !translationOutput.isEmpty()) {
            //If global name is not null set table global
            if (translationOutput.get("global_name") != null) {
                String[] gloVals = ("" + translationOutput.get("global_value")).split("\\|");
                int i = 0;
                for (String gloName : ("" + translationOutput.get("global_name")).split("\\|")) {
                    if (gloName == null || "null".equalsIgnoreCase(gloName) || "".equals(gloName)) continue;
                    setThreadGlobals(gloName, gloVals[i]);
                    i++;
                }
            }
            //If function name is not null execute function
            if (translationOutput.get("function_name") != null) {
                String[] userMethodsLst = ("" + translationOutput.get("function_name")).split("\\|");
                for (String userMethod : userMethodsLst) {
                    if (userMethod == null || "null".equalsIgnoreCase(userMethod) || "".equals(userMethod)) continue;
                    Instant startTime = Instant.now();
                    getLuType().invokeFunction(userMethod, functionContext().getExecution(), dataChange,functionContext());
                    //getLuType().invokeFunction(userMethod, null, dataChange, functionContext());
                    debugTiming.put("fnIIDFExecTableCustomCode: " + getThreadGlobals("fnIIDFExecTableCustomCode") + " -> " + userMethod, Duration.between(startTime, Instant.now()).toMillis());
                }
            }
        }
    }

    @out(name = "tableSourceFullName", type = String.class, desc = "")
    private static String fnIIDFGetTableSourceFullName(String table_name) {
        //Getting LU table full source name (schema and table name) from the first population
        TablePopulationObject popMap = ((TableObject) getLuType().ludbObjects.get(table_name)).getAllTablePopulationObjects().get(0);
        String sourceSchemaName = popMap.iidFinderProp.sourceSchema;
        String sourceTableName = popMap.iidFinderProp.sourceTable;
        if (sourceSchemaName != null && !"".equals(sourceSchemaName) && sourceTableName != null && !"".equals(sourceTableName)) {
            return sourceSchemaName.toUpperCase() + "." + sourceTableName.toUpperCase();
        }
        return null;
    }

    @SuppressWarnings({"unchecked"})
    @type(DecisionFunction)
    @out(name = "decision", type = Boolean.class, desc = "")
    public static Boolean fnIIDFCheckExtractFromSourceInd() throws Exception {
        String tableName = getTableName();
        boolean structureChanged = isStructureChanged();

        if (structureChanged) {
            setThreadGlobals(tableName.toUpperCase() + "_STRUCTURE_CHANGE", "Y");
        }

        if ((isFirstSync() || structureChanged || SyncMode.FORCE.toString().equalsIgnoreCase(getSyncMode())) && Boolean.parseBoolean(IIDF_EXTRACT_FROM_SOURCE)) {
            return true;
        } else if (Boolean.parseBoolean(IIDF_EXTRACT_FROM_SOURCE)) {
            Set<String> parentsList = new HashSet<>();
            if (getThreadGlobals("childCrossInsts" + tableName.toUpperCase()) != null) {
                return true;
            } else {
                if (getThreadGlobals("IIDF_PARENT_TABLES") == null) {
                    setThreadGlobals("IIDF_PARENT_TABLES", fnIIDFGetParentTable(getLuType().luName));
                } else {
                    Map<String, Set<String>> prntTable = (Map<String, Set<String>>) getThreadGlobals("IIDF_PARENT_TABLES");
                    if (prntTable != null && !prntTable.isEmpty()) {
                        parentsList = prntTable.get(tableName);
                    }
                }
                if (parentsList != null) {
                    for (String table : parentsList) {
                        if (getThreadGlobals("childCrossInsts" + table.toUpperCase()) != null) {
                            setThreadGlobals("childCrossInsts" + tableName.toUpperCase(), "1");
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void fnIIDFUpdateStats(Map<String, Map<String, Integer>> statsMap) throws Exception {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date();
        Db ci = ludb();
        ci.beginTransaction();
        for (Map.Entry<String, Map<String, Integer>> mapEnt : statsMap.entrySet()) {
            Map<String, Integer> tableStats = mapEnt.getValue();
            Db.Row rowData;
            try (Db.Rows rows = fabric().fetch("Select TOTAL_UPDATE, TOTAL_INSERT, TOTAL_DELETE, TOTAL_REPLICATE, TOTAL_IIDCHANGE from IIDF_STATISTICS where LU_NAME = ? and IID = ? and TABLE_NAME = ?", getLuType().luName, getInstanceID(), mapEnt.getKey())) {
                rowData = rows.firstRow();
            }
            int total_update = rowData.cell(0) != null ? Integer.parseInt(rowData.cell(0) + "") : 0;
            int total_insert = rowData.cell(1) != null ? Integer.parseInt(rowData.cell(1) + "") : 0;
            int total_delete = rowData.cell(2) != null ? Integer.parseInt(rowData.cell(2) + "") : 0;
            int total_repicate = rowData.cell(3) != null ? Integer.parseInt(rowData.cell(3) + "") : 0;
            int total_iidchange = rowData.cell(4) != null ? Integer.parseInt(rowData.cell(4) + "") : 0;
            for (Map.Entry<String, Integer> tblEnt : tableStats.entrySet()) {
                if (tblEnt.getKey().equalsIgnoreCase("UPDATE")) {
                    total_update += tblEnt.getValue();
                } else if (tblEnt.getKey().equalsIgnoreCase("DELETE")) {
                    total_delete += tblEnt.getValue();
                } else if (tblEnt.getKey().equalsIgnoreCase("INSERT")) {
                    total_insert += tblEnt.getValue();
                } else if (tblEnt.getKey().equalsIgnoreCase("REPLICATE")) {
                    total_insert += tblEnt.getValue();
                } else if (tblEnt.getKey().equalsIgnoreCase("IID_CHANGE")) {
                    total_iidchange += tblEnt.getValue();
                }
            }
            ci.execute("INSERT OR REPLACE INTO IIDF_STATISTICS (LU_NAME, IID, TABLE_NAME, TOTAL_UPDATE, TOTAL_INSERT, TOTAL_DELETE, TOTAL_REPLICATE, LAST_EXECUTION_TIME, TOTAL_IIDCHANGE) values (?,?,?,?,?,?,?,?,?)", getLuType().luName, getInstanceID(), mapEnt.getKey(), total_update, total_insert, total_delete, total_repicate, dateFormat.format(date), total_iidchange);
        }
        ci.commit();
    }

    private static void fnIIDFSetStats(String Operation, Map<String, Integer> tableStatsMap, Boolean isIIDChange) {
        if (Operation.equalsIgnoreCase("UPDATE")) {
            if (tableStatsMap.keySet().contains("UPDATE")) {
                int update = tableStatsMap.get("UPDATE");
                tableStatsMap.put("UPDATE", ++update);
            } else {
                tableStatsMap.put("UPDATE", 1);
            }
        } else if (Operation.equalsIgnoreCase("INSERT")) {
            if (tableStatsMap.keySet().contains("INSERT")) {
                int insert = tableStatsMap.get("INSERT");
                tableStatsMap.put("INSERT", ++insert);
            } else {
                tableStatsMap.put("INSERT", 1);
            }
        } else if (Operation.equalsIgnoreCase("DELETE")) {
            if (tableStatsMap.keySet().contains("DELETE")) {
                int delete = tableStatsMap.get("DELETE");
                tableStatsMap.put("DELETE", ++delete);
            } else {
                tableStatsMap.put("DELETE", 1);
            }
        } else if (Operation.equalsIgnoreCase("REPLICATE")) {
            if (tableStatsMap.keySet().contains("REPLICATE")) {
                int replicate = tableStatsMap.get("REPLICATE");
                tableStatsMap.put("REPLICATE", ++replicate);
            } else {
                tableStatsMap.put("REPLICATE", 1);
            }
        }
        if (isIIDChange) {
            if (tableStatsMap.keySet().contains("IID_CHANGE")) {
                int iidChange = tableStatsMap.get("IID_CHANGE");
                tableStatsMap.put("IID_CHANGE", ++iidChange);
            } else {
                tableStatsMap.put("IID_CHANGE", 1);
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    private static void fnIIDFCheckCrossIns(List<Long> dataChanges, List<Object> paramsStats) throws Exception {
        boolean isLogicalChildChanged;
        List<Object> valuesForCrossInsts;
        Instant start = Instant.now();
        DataChange dataChange;
        Map<String, Map<String, String>> trnIIDFForceOrSkipCrossInsCheck = getTranslationsData("trnIIDFForceOrSkipCrossInsCheck");

        for (Long dataChange_rowID : dataChanges) {
            Object dc = ludb().fetch(String.format("select DATA_CHANGE from %s.IIDF_QUEUE where rowid = ?", getLuType().luName), dataChange_rowID).firstValue();
            if (dc == null) {
                log.warn("fnIIDFCheckCrossIns - Cant find datachange");
                continue;
            }
            dataChange = DataChange.fromJson(dc + "");
            String tableName = dataChange.getTargetTableName();
            String Operation = "" + dataChange.getOperation();
            isLogicalChildChanged = dataChange.isLogicalChildChanged();
            LudbObject ludbObject = getLuType().ludbObjects.get(tableName);
            List<LudbObject> childObjects = ludbObject.childObjects;
            outerloop:
            for (LudbObject child : childObjects) {
                String childTblName = child.k2StudioObjectName;
                if (trnIIDFForceOrSkipCrossInsCheck.containsKey(childTblName.toUpperCase()) && Boolean.parseBoolean(trnIIDFForceOrSkipCrossInsCheck.get(childTblName.toUpperCase()).get("SKIP")))
                    continue;

                if (Operation.equals("insert") && getThreadGlobals("checkCrossInstns" + tableName.toUpperCase()) != null) {
                    setThreadGlobals("checkCrossInstns" + childTblName.toUpperCase(), "1");
                } else if (Operation.equals("insert") || ((Operation.equals("update") && isLogicalChildChanged))) {
                    Map<String, Object> keysMap = dataChange.getKeys();
                    Map<String, Object> valuesMap = dataChange.getValues();
                    Map<String, Object> allKeysMap = new HashMap<>();
                    allKeysMap.putAll(keysMap);
                    allKeysMap.putAll(valuesMap);

                    if (getThreadGlobals("childCrossInsts" + childTblName.toUpperCase()) == null) {
                        if (Boolean.parseBoolean(getLuType().ludbGlobals.get("CROSS_IID_SKIP_LOGIC")) || (trnIIDFForceOrSkipCrossInsCheck.containsKey(childTblName.toUpperCase()) && Boolean.parseBoolean(trnIIDFForceOrSkipCrossInsCheck.get(childTblName.toUpperCase()).get("FORCE"))) || (Operation.equals("insert") && dataChange.getOrgOperation().equals(DataChange.Operation.replicate))) {
                            setThreadGlobals("childCrossInsts" + childTblName.toUpperCase(), "1");
                            continue;
                        }

                        List<LudbRelationInfo> childRelations = (List) ((Map) getLuType().getLudbPhysicalRelations().get(tableName)).get(childTblName);
                        Map<String, Map<String, String>> childRelationsMap = new HashMap<>();
                        outerLinksLoop:
                        for (LudbRelationInfo ludbRelationInfo : childRelations) {
                            String popName = ludbRelationInfo.to.get("populationObjectName");
                            for(TablePopulationObject pop:((TableObject) child).getAllTablePopulationObjects()){
                                if(pop.disabled && pop.objectName.equals(popName)){
                                    continue outerLinksLoop;
                                }
                            }
                            if (childRelationsMap.containsKey(popName)) {
                                childRelationsMap.get(popName).put(ludbRelationInfo.from.get("column").toUpperCase(), ludbRelationInfo.to.get("column").toUpperCase());
                            } else {
                                Map<String, String> fromToMap = new HashMap<>();
                                fromToMap.put(ludbRelationInfo.from.get("column").toUpperCase(), ludbRelationInfo.to.get("column").toUpperCase());
                                childRelationsMap.put(popName, fromToMap);
                            }
                        }
                        ArrayList<String> selectStms = new ArrayList<>();
                        ArrayList<List<Object>> biningParams = new ArrayList<>();
                        String prefixSlct = "";
                        for (Map.Entry<String, Map<String, String>> population : childRelationsMap.entrySet()) {
                            List<Object> valsList = new LinkedList<>();
                            StringBuilder selectStm = new StringBuilder().append("select 1 from ").append(getLuType().luName).append(".").append(childTblName).append(" where ");
                            for (Map.Entry<String, String> fields : population.getValue().entrySet()) {
                                String childField = fields.getValue().toUpperCase();
                                if (allKeysMap.containsKey(fields.getKey().toUpperCase())) {
                                    valsList.add(allKeysMap.get(fields.getKey().toUpperCase()));
                                } else {
                                    List<Object> vals = new ArrayList<>();
                                    StringBuilder sb = new StringBuilder();
                                    String prefix = "";
                                    String[] pkCuls = fnIIDFGetTablePK(tableName, getLuType().luName);
                                    for (String pkCul : pkCuls) {
                                        sb.append(prefix).append(pkCul).append(" = ? ");
                                        prefix = " and ";
                                        vals.add(allKeysMap.get(pkCul.toUpperCase()));
                                    }

                                    try (Db.Rows rs = ludb().fetch(String.format("select %s from %s.%s where %s", fields.getKey().toUpperCase(), getLuType().luName, tableName, sb.toString()), vals.toArray())) {
                                        Object rowVal = rs.firstValue();
                                        if (rowVal != null) {
                                            valsList.add(rowVal);
                                        } else {
                                            continue outerloop;
                                        }
                                    }
                                }
                                selectStm.append(prefixSlct).append(childField).append(" = ? ");
                                prefixSlct = " and ";
                            }
                            prefixSlct = "";
                            biningParams.add(valsList);
                            selectStms.add(selectStm.toString());
                        }

                        int count = 0;
                        for (String stmt : selectStms) {
                            try (Db.Rows rs = ludb().fetch(stmt, biningParams.get(count).toArray())) {
                                Object countChildObj = rs.firstValue();
                                if (countChildObj == null) {
                                    setThreadGlobals("childCrossInsts" + childTblName.toUpperCase(), "1");
                                    break;
                                }
                            }
                            count++;
                        }
                    }
                }
            }
        }
        paramsStats.add((Duration.between(start, Instant.now()).toMillis()));//Time to execute cross instance check
        paramsStats.add(dataChanges.size()); //Total cross DCs count
    }

    private static void fnIIDFExecInsNullPK(Object dc) throws Exception {
        DataChange dataChange = (DataChange) dc;
        Set<String> tblPKSet = dataChange.getKeys().keySet();
        Map<String, Object> TblAllValsMap = dataChange.getValues();
        String prefixWhere = "";
        String prefixSet = "";
        Set<Object> whereVals = new LinkedHashSet<>();
        Set<Object> setVals = new LinkedHashSet<>();
        StringBuilder updateStmt = new StringBuilder().append(" update ").append(getLuType().luName).append(".").append(dataChange.getTargetTableName()).append(" set ");
        StringBuilder whereStmt = new StringBuilder().append(" where ");
        for (Map.Entry<String, Object> tblVals : TblAllValsMap.entrySet()) {
            if (tblPKSet.contains(tblVals.getKey())) {
                if (tblVals.getValue() == null) {
                    whereStmt.append(prefixWhere).append(tblVals.getKey()).append(" is null ");
                } else {
                    whereStmt.append(prefixWhere).append(tblVals.getKey()).append(" = ? ");
                    whereVals.add(tblVals.getValue());
                    //  prefixWhere = " and ";
                }
                prefixWhere = " and ";
            } else {
                updateStmt.append(prefixSet).append(tblVals.getKey()).append(" = ? ");
                setVals.add(tblVals.getValue());
                prefixSet = ",";
            }
        }

        Set<Object> parmas = new LinkedHashSet<>();
        parmas.addAll(setVals);
        parmas.addAll(whereVals);
        Object rs;
        try (Db.Rows rs2 = ludb().fetch(String.format("select 1 from %s.%s %s", getLuType().luName, dataChange.getTargetTableName(), whereStmt.toString()), whereVals.toArray())) {
            rs = rs2.firstValue();
        }
        if (rs == null) {//If its the first time we execute it we run insert
            String tableName = dataChange.getTargetTableName();//Get table name from data change
            ludb().execute(fnIIDFReplaceSqliteKeywords(dataChange.toSql(DataChange.Operation.upsert, tableName)), dataChange.sqlValues());
        } else if (setVals.size() != 0) {//If its not the first time we execute it we run update
            ludb().execute(fnIIDFReplaceSqliteKeywords(updateStmt.toString() + whereStmt.toString()), parmas.toArray());
        }
    }

    @SuppressWarnings({"unchecked"})
    @out(name = "rec_deleted", type = Boolean.class, desc = "")
    private static Boolean fnIIDFDeleteTableOrphanRecords(String tableName, Map<String, Set<String>> parent_tables, String targetIid, String pos, Long op_ts, Long cr_ts) throws Exception {
        final String delete = "delete from %s.%s where %s";
        Set<String> deletedTables = getThreadGlobals("DELETED_TABLES") != null ? (HashSet<String>) getThreadGlobals("DELETED_TABLES") : new HashSet<>();
        if (deletedTables.contains(tableName)) {
            return false;
        } else {
            deletedTables.add(tableName);
            setThreadGlobals("DELETED_TABLES", deletedTables);
        }

        //Get table record from trnIIDFOrphanRecV
        Map<String, String> trnMap = getTranslationValues("trnIIDFOrphanRecV", new Object[]{tableName.toUpperCase()});
        //Check if to run orphan record check on table
        if (Boolean.parseBoolean(trnMap.get("SKIP_VALIDATE_IND"))) {
            return false;
        }

        Set<String> prntTables = parent_tables.get(tableName.toUpperCase());
        StringBuilder sqlQuery = new StringBuilder();
        String prefix = "";
        for (String parent_table : prntTables) {//Loop throw all table's parents and delete orphan records

            TreeMap<String, List<LudbRelationInfo>> phyRelTbl = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);//Get table -> child links
            phyRelTbl.putAll(((TreeMap<String, Map<String, List<LudbRelationInfo>>>) getThreadGlobals("LUDB_PHYISICAL_RELATION")).get(parent_table + ""));

            HashMap<String, String> popRelMap = new HashMap<>();
            List<LudbRelationInfo> parentRelations = phyRelTbl.get(tableName);
            for (LudbRelationInfo chRel : parentRelations) {
                String popMapName = chRel.to.get("populationObjectName");
                if (popRelMap.containsKey(popMapName)) {
                    popRelMap.put(popMapName, popRelMap.get(popMapName) + " and " + chRel.from.get("column") + " = " + tableName + "." + chRel.to.get("column"));
                } else {
                    popRelMap.put(popMapName, chRel.from.get("column") + " = " + tableName + "." + chRel.to.get("column"));
                }
            }

            for (String con : popRelMap.values()) {
                sqlQuery.append(prefix).append(" not exists (select 1 from ").append(getLuType().luName).append(".").append(parent_table).append(" where ").append(con).append(" ) ");
                prefix = " and ";
            }
        }

        //Check if to add another condition to orphan records check query
        if (trnMap.get("VALIDATION_SQL") != null && !trnMap.get("VALIDATION_SQL").equalsIgnoreCase("")) {
            sqlQuery.append(" and not exists (").append(trnMap.get("VALIDATION_SQL")).append(")");
        }
        boolean rec_deleted = fnIIDFGetRecData(tableName, sqlQuery.toString(), targetIid, pos, op_ts, (!Boolean.parseBoolean(IIDF_EXTRACT_FROM_SOURCE) || getTranslationsData("trnIIDFForceReplicateOnDelete").keySet().contains(tableName.toUpperCase())), cr_ts);//Before deleting records send them to kafka
        ludb().execute(String.format(delete, getLuType().luName, tableName, sqlQuery.toString()));//Delete orphan records
        return rec_deleted;
    }

    @type(RootFunction)
    @out(name = "IID", type = String.class, desc = "")
    public static void fnIIDFPopRecentDummy(String IID) throws Exception {
        if (false)
            UserCode.yield(new Object[]{null});
    }

    private static void setTblPrnt(LudbObject table, Map<String, Set<String>> prntTable) {
        if (table.childObjects == null) {
            return;
        }
        for (LudbObject chiTbl : table.childObjects) {

            Set<String> prntArr = prntTable.get(chiTbl.k2StudioObjectName.toUpperCase());
            if (prntArr == null) {
                prntArr = new HashSet<>();
            }
            prntArr.add(table.k2StudioObjectName);
            prntTable.put(chiTbl.k2StudioObjectName.toUpperCase(), prntArr);

            setTblPrnt(chiTbl, prntTable);
        }
    }

    private static Properties getSSLProperties() {
        Properties props = new Properties();
        if (com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getBoolean("SSL_ENABLED", false)) {
            appendProperty(props, "security.protocol", com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("SECURITY_PROTOCOL", null));
            appendProperty(props, "ssl.truststore.location", com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("TRUSTSTORE_LOCATION", null));
            appendProperty(props, "ssl.truststore.password", com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("TRUSTSTORE_PASSWORD", null));
            appendProperty(props, "ssl.keystore.location", com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("KEYSTORE_LOCATION", null));
            appendProperty(props, "ssl.keystore.password", com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("KEYSTORE_PASSWORD", null));
            appendProperty(props, "ssl.key.password", com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("KEY_PASSWORD", null));
            props.setProperty("ssl.endpoint.identification.algorithm", "");
            appendProperty(props, "ssl.endpoint.identification.algorithm", com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("ENDPOINT_IDENTIFICATION_ALGORITHM", null));
            appendProperty(props, "ssl.cipher.suites", com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("SSL_CIPHER_SUITES", null));
            appendProperty(props, "ssl.enabled.protocols", com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("SSL_ENABLED_PROTOCOLS", null));
            appendProperty(props, "ssl.truststore.type", com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("SSL_TRUSTSTORE_TYPE", null));
            return props;
        } else {
            return props;
        }
    }

    public static void UserKafkaProperties(Properties props) {
        props.put("bootstrap.servers", IifProperties.getInstance().getKafkaBootstrapServers());
        props.put("connections.max.idle.ms", 300000);
        props.put("acks", "all");
        props.put("retries", "5");
        props.put("batch.size", "" + IifProperties.getInstance().getKafkaBatchSize());
        props.put("linger.ms", 1);
        props.put("max.block.ms", "" + IifProperties.getInstance().getKafkaMaxBlockMs());
        props.put("buffer.memory", "" + IifProperties.getInstance().getKafkaBufferMemory());
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        Properties sslProps = getSSLProperties();
        props.putAll(sslProps);
    }

    public static void UserKafkaConsumProperties(Properties props, String groupId) {
        props.put("group.id", groupId == null ? "IIDFinderGroupId" : groupId);
        props.put("bootstrap.servers", IifProperties.getInstance().getKafkaBootstrapServers());
        props.put("enable.auto.commit", "false");
        props.put("auto.offset.reset", "earliest");
        props.put("max.poll.records", 50);
        props.put("session.timeout.ms", 120000);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        Properties sslProps = getSSLProperties();
        props.putAll(sslProps);
    }

    public static void fnIIDFCleanThreadGlobals() {
        Object iidDeltaCount = getThreadGlobals("IID_DELTA_COUNT");
        if (iidDeltaCount != null && !inDebugMode()) {
            CustomStats.count("idfinder_stats_" + getLuType().luName.toLowerCase(), "deleted_deltas", Long.parseLong(iidDeltaCount + ""));
        }

        clearThreadGlobals();
    }

    private static void appendProperty(Properties p, String key, String value) {
        Objects.requireNonNull(key);
        if (!Util.isEmpty(value)) {
            p.put(key, value);
        }
    }
/*
    @SuppressWarnings({"unchecked"})
    public static void fnIIDFInsertIIDStats() {
        if (inDebugMode()) return;
        Instant startTime = (Instant) getThreadGlobals("SYNC_TIME");
        long ttlSyncTime = Duration.between(startTime, Instant.now()).toMillis();
        List<Object> paramsStats = (LinkedList<Object>) getThreadGlobals("STATS");

        long replicates_send_back_r = 0;
        if (getThreadGlobals("SEND_BACK_CNT_R") != null) {
            replicates_send_back_r = Long.parseLong(("" + getThreadGlobals("SEND_BACK_CNT_R")));
        }

        long replicates_send_back_d = 0;
        if (getThreadGlobals("SEND_BACK_CNT_D") != null) {
            replicates_send_back_d = Long.parseLong(("" + getThreadGlobals("SEND_BACK_CNT_D")));
        }

        long accountChange = 0;
        if (getThreadGlobals("ACCOUNT_CHANGE") != null) {
            accountChange = Long.parseLong(getThreadGlobals("ACCOUNT_CHANGE") + "");
        }

        long is_iid_change = 0;
        if (getThreadGlobals("is_iid_change") != null) {
            is_iid_change = Long.parseLong(getThreadGlobals("is_iid_change") + "");
        }

        boolean iid_skipped = false;
        if (getThreadGlobals("IID_SKIPPED") != null) {
            iid_skipped = Boolean.parseBoolean(getThreadGlobals("IID_SKIPPED") + "");
        }

        class myRunnable implements Runnable {
            private String iid;
            private List<Object> paramsStats;
            private long replicates_send_back_r;
            private long replicates_send_back_d;
            private long ttlSyncTime;
            private long accountChange;
            private UserCodeDelegate i_ucd;
            private long is_iid_change;
            private boolean iid_skipped;
            private CqlSession cassandraSession = CassandraClusterSingleton.getInstance().getSession();

            private myRunnable(String iid, List<Object> paramsStats, long replicates_send_back_r, long replicates_send_back_d, long ttlSyncTime, UserCodeDelegate i_ucd, long accountChange, long is_iid_change, boolean iid_skipped) {
                this.iid = iid;
                this.paramsStats = paramsStats;
                this.replicates_send_back_r = replicates_send_back_r;
                this.replicates_send_back_d = replicates_send_back_d;
                this.ttlSyncTime = ttlSyncTime;
                this.i_ucd = i_ucd;
                this.accountChange = accountChange;
                this.is_iid_change = is_iid_change;
                this.iid_skipped = iid_skipped;
            }

            public void run() {
                try {
                    com.datastax.oss.driver.api.core.cql.PreparedStatement preStmt;
                    Object rs = Globals.get("STATS_PREPARED_" + this.i_ucd.getLuType().luName);
                    if (rs == null) {
                        preStmt = cassandraSession.prepare("insert into " + this.i_ucd.getLuType().getKeyspaceName() + ".iid_stats (iid,iid_sync_time,sync_duration,time_to_fetch_deltas_from_api,time_to_insert_deltas_to_iidfqueue_table,iid_deltas_count,time_to_fetch_deltas_from_iidf_queue_and_execute,time_to_execute_replicates_request,replicates_requests_count,time_to_execute_delete_orphan_check,delete_orphan_check_count,time_to_execute_cross_instance_check,cross_instance_check_count,replicates_send_to_kafka_due_to_delete_orphan_count,replicates_send_to_kafka_due_to_replicate_request_count,iid_change_ind_count,account_change_count,iid_skipped) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                        Globals.set("STATS_PREPARED_" + this.i_ucd.getLuType().luName, preStmt);
                    } else {
                        preStmt = (com.datastax.oss.driver.api.core.cql.PreparedStatement) rs;
                    }
                    cassandraSession.execute(preStmt.bind(iid, new Timestamp(System.currentTimeMillis()), ttlSyncTime, paramsStats.get(0), Long.parseLong(paramsStats.get(1).toString()), paramsStats.get(2), paramsStats.get(3), paramsStats.get(4), paramsStats.get(5), paramsStats.get(6), paramsStats.get(7), paramsStats.get(8), paramsStats.get(9), replicates_send_back_d, replicates_send_back_r, this.is_iid_change, this.accountChange, this.iid_skipped));
                } catch (Exception e) {
                    log.error("fnIIDFInsertIIDStats,", e);
                }
            }
        }
        if (Boolean.parseBoolean(getLuType().ludbGlobals.get("IID_STATS") + "")) {
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(1);
            executor.submit(new myRunnable(getInstanceID(), paramsStats, replicates_send_back_r, replicates_send_back_d, ttlSyncTime, functionContext(), accountChange, is_iid_change, iid_skipped));
            executor.shutdown();
        }
    }
*/
    @out(name = "rs", type = Boolean.class, desc = "")
    private static Boolean fnIIDFExecNonLUTableDelta(Object i_dataChange) throws Exception {
        DataChange dataChange = (DataChange) i_dataChange;
        String tableName = dataChange.getTargetTableName();
        Map<String, String> trnMap = getTranslationValues("trnIIDFExecNonLUTableDelta", new Object[]{tableName.toUpperCase()});
        if (Boolean.parseBoolean(trnMap.get("ACTIVE"))) {
            setThreadGlobals("fnIIDFExecTableCustomCode", "fnIIDFExecNonLUTableDelta");
            fnIIDFExecTableCustomCode(dataChange, false);
            setThreadGlobals("fnIIDFExecTableCustomCode", "");
            return Boolean.parseBoolean(trnMap.get("SKIP"));
        } else {
            return false;
        }
    }

    private static void fnIIDFUpdateSelectiveTable(Object dc, Map<String, DataChange> deleteOrphan, Map<String, Long> debugTiming) {
        Instant startTime = Instant.now();
        DataChange dataChange = (DataChange) dc;
        boolean execInsert = false;
        String tableName = dataChange.getTargetTableName();
        if (!getTranslationsData("trnSelectiveTables").keySet().contains(tableName.toUpperCase())) return;
        Map<String, String> trnMap = getTranslationValues("trnSelectiveTables", new Object[]{tableName.toUpperCase()});
        if (dataChange.getOperation().equals(DataChange.Operation.update)) {
            Map<String, Object> beforeValues = dataChange.getBeforeValues();
            Map<String, Object> afterValues = dataChange.getValues();
            Map<String, Object> dcKeys = dataChange.getKeys();
            for (Map.Entry<String, Object> keysEnt : dcKeys.entrySet()) {
                if (!afterValues.get(keysEnt.getKey()).equals(keysEnt.getValue())) return;
            }
            String[] columnsList = trnMap.get("column_list") != null && trnMap.get("column_list").trim().length() > 0 ? trnMap.get("column_list").split(",") : null;
            if (beforeValues.size() > 0 && columnsList != null) {
                for (String columnName : columnsList) {
                    Object beforeValue = beforeValues.get(columnName.toUpperCase());
                    Object afterValue = afterValues.get(columnName.toUpperCase());
                    if (beforeValues.containsKey(columnName.toUpperCase()) && ( (beforeValue != null && afterValue == null) || (beforeValue == null && afterValue != null) || (beforeValue != null && !beforeValue.toString().equalsIgnoreCase(afterValue.toString())))) {
                        dataChange.setOperation(DataChange.Operation.upsert);
                        deleteOrphan.put(tableName, dataChange);
                        break;
                    }
                }
            } else {
                dataChange.setOperation(DataChange.Operation.upsert);
                deleteOrphan.put(tableName, dataChange);
            }
        }
        debugTiming.put("fnIIDFUpdateSelectiveTable", Duration.between(startTime, Instant.now()).toMillis());
    }

    private static void fnIIDFCleanIIDFinderCTables(Object i_dc) throws Exception {
        final String select = "Select * from %s.%s where %s";
        DataChange dc = (DataChange) i_dc;
        String LUTableName = dc.getTargetTableName();
        Map<String, String> keys = new HashMap<>();
        StringBuilder sqlGetRowValuesWhere = new StringBuilder();
        List<Object> params = new ArrayList<>();
        String prefix = "";
        Map<String, Object> dataChangeKeys = dc.getKeys();
        for (Map.Entry<String, Object> mapEnt : dataChangeKeys.entrySet()) {
            sqlGetRowValuesWhere.append(prefix).append(mapEnt.getKey()).append(" = ? ");
            prefix = " and ";
            params.add(mapEnt.getValue());
            keys.put(mapEnt.getKey(), mapEnt.getValue() + "");
        }
        Db.Row rs = ludb().fetch(String.format(select, getLuType().luName, LUTableName, sqlGetRowValuesWhere.toString()), params.toArray()).firstRow();
        TreeMap<String, List<LudbRelationInfo>> phyRel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Map<String, List<LudbRelationInfo>>> LUPR = getLuType().getLudbPhysicalRelations();
        Map<String, List<LudbRelationInfo>> LUTR = LUPR.get(LUTableName);
        if (LUTR == null) {
            LUTR = LUPR.get(LUTableName.toLowerCase());
            if (LUTR == null) return;
        }
        phyRel.putAll(LUTR);
        Set<String> tableLinksToChildes = new HashSet<>();
        Map<String, Object> dcAfterValues = dc.getValues();
        boolean rubDF = false;
        for (LudbObject childTable : getLuType().ludbObjects.get(LUTableName).childObjects) {
            Map<String, String> tableLinkToChield = new HashMap<>();
            List<LudbRelationInfo> childParentRelation = phyRel.get(childTable.ludbObjectName);
            for (LudbRelationInfo chRel : childParentRelation) {
                tableLinksToChildes.add(chRel.from.get("column").toUpperCase());
            }
            Map<String, Object> linkedColumnValuesFromAfter = new HashMap<>();
            for (String parentLinkedColumn : tableLinksToChildes) {
                if (dcAfterValues.containsKey(parentLinkedColumn)) {
                    String rowColumnValue = rs.get(parentLinkedColumn) + "";
                    String afterValue = dcAfterValues.get(parentLinkedColumn) + "";
                    if (!rowColumnValue.equals(afterValue)) {
                        tableLinksToChildes.forEach(column -> tableLinkToChield.put(column, rs.get(column) + ""));
                        rubDF = true;
                        break;
                    }
                }
            }
            if (tableLinkToChield.size() > 0 && !"false".equals(((TableObject) getLuType().ludbObjects.get(LUTableName)).getEnabledTablePopulationObjects().get(0).iidFinderProp.isStoreData)) {
                IidFinderApi.addDeleteFinder(getLuType().luName, getInstanceID(), String.format("%s_%s", dc.getTablespace(), dc.getTable()), keys, tableLinkToChield);
            }
            tableLinksToChildes.clear();
        }
        Map<String, String> trnRS = getTranslationValues("trnIIDFCleanIIDFinderCTables", new Object[]{LUTableName});
        String linkedColumns = trnRS.get("LINKED_COLUMNS");
        if (linkedColumns != null && !"".equals(linkedColumns)) {
            Map<String, String> tableLinkToChield = new HashMap<>();
            for (String linkedColumn : linkedColumns.split(",")) {
                if (dcAfterValues.containsKey(linkedColumn)) {
                    String rowColumnValue = rs.get(linkedColumn) + "";
                    String afterValue = dcAfterValues.get(linkedColumn) + "";
                    if (!rowColumnValue.equals(afterValue)) {
                        for (String linkedColumn2 : linkedColumns.split(",")) {
                            tableLinkToChield.put(linkedColumn2, rs.get(linkedColumn2) + "");
                        }
                        rubDF = true;
                        if (!"false".equals(((TableObject) getLuType().ludbObjects.get(LUTableName)).getEnabledTablePopulationObjects().get(0).iidFinderProp.isStoreData))
                            IidFinderApi.addDeleteFinder(getLuType().luName, getInstanceID(), String.format("%s_%s", dc.getTablespace(), dc.getTable()), keys, tableLinkToChield);
                        break;
                    }
                }
            }
        }
        if (rubDF) {
            IidFinderApi.runDeleteFinder(getLuType().luName, getInstanceID());
        }
    }

    @out(name = "rs", type = Boolean.class, desc = "")
    private static Boolean fnIIDFCheckRExists(Object i_dc, Map<String, Long> debugTiming) throws Exception {
        Instant startTime = Instant.now();
        final String select = "select 1 from %s.%s where %s";
        DataChange dc = (DataChange) i_dc;
        String tableName = dc.getTargetTableName();
        if (getTranslationsData("trnIIDFForceReplicateOnDelete").keySet().contains(tableName)) return false;
        String prefix = "";
        StringBuilder recKeys = new StringBuilder();
        List<Object> keyVals = new ArrayList<>();
        Map<String, Object> dcKeys = dc.getKeys();
        for (Map.Entry<String, Object> dcKeysEnt : dcKeys.entrySet()) {
            keyVals.add(dcKeysEnt.getValue());
            recKeys.append(prefix).append(dcKeysEnt.getKey()).append(" = ? ");
            prefix = " and ";
        }

        boolean rs = ludb().fetch(String.format(select, getLuType().luName, tableName, recKeys.toString()), keyVals.toArray()).firstValue() != null;
        debugTiming.put("fnIIDFCheckRExists", Duration.between(startTime, Instant.now()).toMillis());
        return rs;
    }

    private static void fnIIDFConvertDCToUpsert(Object i_dc) throws Exception {
        final String select = "Select 1 from %s.%s where %s";
        DataChange dc = (DataChange) i_dc;
        String tableName = dc.getTargetTableName();
        Map<String, Object> dcKeys = dc.getKeys();
        StringBuilder stmtWhere = new StringBuilder();
        String prefix = "";
        Set<Object> params = new LinkedHashSet<>();
        for (Map.Entry<String, Object> dcKeysEnt : dcKeys.entrySet()) {
            stmtWhere.append(prefix).append(dcKeysEnt.getKey()).append("  =  ? ");
            prefix = " and ";
            params.add(dcKeysEnt.getValue());
        }
        if (ludb().fetch(String.format(select, getLuType().luName, tableName, stmtWhere.toString()), params.toArray()).firstValue() == null)
            dc.setOperation(DataChange.Operation.upsert);
    }

    @out(name = "rs", type = Object.class, desc = "")
    public static Map<String, String> fnIIDFGetTablesCoInfo(String table_name, String lu_name) throws Exception {
        Map<String, String> tblcolMap = new HashMap<>();
        LUType lut = LUTypeFactoryImpl.getInstance().getTypeByName(lu_name);
        if (lut != null) {
            LudbObject rtTable = lut.ludbObjects.get(table_name);
            if (rtTable != null) {
                for (Map.Entry<String, LudbColumn> mapEnt : rtTable.getLudbColumnMap().entrySet()) {
                    tblcolMap.put(mapEnt.getKey().toUpperCase(), mapEnt.getValue().columnType.toUpperCase());
                }
                return tblcolMap;
            }
        }

        throw new InvalidObjectException(String.format("Failed To Get Table Columns Map!, Table Name:%s, LUName:%s", table_name, lu_name));
    }

    @SuppressWarnings({"unchecked"})
    private static void fnIIDFExecPreSyncUserActivity() throws Exception {
        Map<String, Long> debugTiming = (Map<String, Long>) getThreadGlobals("DEBUG_TIMING_MAP");
        for (Map.Entry<String, Map<String, String>> trnData : getTranslationsData("trnIIDFExecPreSyncUserActivity").entrySet()) {
            String x = trnData.getKey();
            Map<String, String> y = trnData.getValue();
            if (x == null || "null".equalsIgnoreCase(x) || "".equals(x) || !(Boolean.parseBoolean(y.get("active"))))
                return;
            Instant startTime = Instant.now();
            getLuType().invokeFunction(x, functionContext().getExecution());
            //getLuType().invokeFunction(x, null, functionContext());
            debugTiming.put("fnIIDFExecPreSyncUserActivity: " + x, Duration.between(startTime, Instant.now()).toMillis());
        }
    }

    private static boolean fnIIDFForceTableSyncFromSource(Object i_dc) throws Exception {
        DataChange dataChange = (DataChange) i_dc;
        Map<String, Object> dcTableColumns = dataChange.getValues();
        String tableName = dataChange.getTargetTableName();
        Map<String, String> tableColumns = fnIIDFGetTablesCoInfo(tableName, getLuType().luName);
        if (tableColumns.keySet().stream().anyMatch(x -> (!x.startsWith("k2_") && !dcTableColumns.containsKey(x)))) {
            if("TRUE".equalsIgnoreCase(getGlobal("IIDF_EXTRACT_FROM_SOURCE",getLuType().luName)+"")) {
                setThreadGlobals("childCrossInsts" + tableName, "1");
                return true;
            }else{
                return false;
            }
        }
        return true;
    }

    @type(UserJob)
    public static void fnIIDFReSyncBigDCIID() throws Exception {
        final String luName = getLuType().luName;
        final String luKeySpaceName = getLuType().getKeyspaceName();
        String delFailedIID = "delete from %s.resync_instance where iid = ?";
        String fetchFailedIID = "select iid from %s.resync_instance limit 1000";

        fabric().execute("set sync force");
        db("dbCassandra").fetch(String.format(fetchFailedIID, luKeySpaceName)).forEach(row -> {
            try {
                Db.Row rs = fabric().fetch("delete instance ?.?", luName, row.get("iid").toString()).firstRow();
                if (rs != null) rs.get("Status");
                fabric().execute("get ?.?", luName, row.get("iid").toString());
                db("dbCassandra").execute(String.format(delFailedIID, luKeySpaceName), row.get("iid").toString());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
