package com.k2view.cdbms.usercode.common;

import java.util.*;
import java.sql.*;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.lut.*;
import com.k2view.fabric.common.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.DB_CASS_NAME;
import static com.k2view.cdbms.usercode.common.SharedGlobals.IDFINDER_TOPIC_PREFIX;

public class IIDFBasicLUTesting {
    private static Log log = Log.a(IIDFBasicLUTesting.class);
    private final Pattern dateTimeColumnRegex = Pattern.compile("^[0-9]{4}-[0-9]{1,2}-[0-9]{1,2} [0-9]{1,2}:[0-9]{1,2}:[0-9]{1,2}");
    private final Pattern timeColumnRegex = Pattern.compile("^[0-9]{1,2}:[0-9]{1,2}:[0-9]{1,2}");
    private final LUType luType;
    private final java.text.DateFormat clsDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private final String LUI;
    private final String LUName;
    private final Map<String, Set<String>> LUTableToParent;
    private final String testUID;
    private List<String> LURealRootTable;
    private int tableProcessedCount = 0;
    private int tableSkipCount = 0;
    private int tableNoRowFoundCount = 0;
    private int tableUserSkipCount = 0;
    private int total_tables_update_operation_skipped = 0;
    private int total_table_Primary_key_not_found = 0;
    private String currentTableInProcess;
    private int sleep = 100;

    public IIDFBasicLUTesting(String LUName, String LUI, String testUID, String tableName) throws SQLException, ExecutionException, InterruptedException {
        this.testUID = testUID;
        this.LUI = LUI;
        this.LUName = LUName;
        this.luType = LUTypeFactoryImpl.getInstance().getTypeByName(LUName);
        this.LUTableToParent = fnIIDFGetParentTable();
        this.LURealRootTable = !"".equals(this.luType.ludbGlobals.get("IIDF_ROOT_TABLE_NAME")) ? Arrays.asList(this.luType.ludbGlobals.get("IIDF_ROOT_TABLE_NAME").split(",")) : new ArrayList<>();
        LudbObject tableToStartFrom = (tableName != null) ? this.luType.ludbObjects.get(tableName.toUpperCase()) : luType.getRootObject();
        buildTestingTable();

        resetIID();
        validateLUTable(LUName, tableToStartFrom, getTranslationsData("trnBasicLUTestingIgnoredTables").keySet());
        updateStatusTable("completed");
        resetIID();
    }

    private void validateLUTable(String LUName, LudbObject LUTable, Set<String> LUIgnoredTables) throws SQLException, ExecutionException, InterruptedException {
        List<LudbObject> LUTablesChildObjects = LUTable.childObjects;
        for (LudbObject LUChildTable : LUTablesChildObjects) {
            validateLUTable(LUName, LUChildTable, LUIgnoredTables);
        }

        boolean userSkip = LUIgnoredTables.contains(LUTable.ludbObjectName);
        if (LUTable.ludbObjectName.toLowerCase().startsWith("iidf") || userSkip) {
            if (userSkip) {
                insToStatusTable(LUTable, "user_skip", "U/D/I", null, null, "table_found_in_trnBasicLUTestingIgnoredTables");
                this.tableUserSkipCount++;
            } else {
                insToStatusTable(LUTable, "skip", "U/D/I", null, null, "iidf_lib_table");
                this.tableSkipCount++;
            }
            return;
        }

        List<String> LUTableKeys = getLUTableKeys(LUName, LUTable.ludbObjectName);
        if (LUTableKeys == null) {
            log.warn("Primary key not found for table {}", LUTable.ludbObjectName);
            insToStatusTable(LUTable, "skip", "U/D/I", null, null, "primary_key_missed");
            this.total_table_Primary_key_not_found++;
            return;
        }

        Db.Row row = getTableFirstRecord(LUTable);
        if (row.isEmpty()) {
            log.warn("No rows found for table {}", LUTable.ludbObjectName);
            insToStatusTable(LUTable, "skip", "U/D/I", null, null, "empty_table");
            this.tableNoRowFoundCount++;
            return;
        }

        updateStatusTable("running");
        this.currentTableInProcess = LUTable.ludbObjectName;
        JSONObject GGMessage = new JSONObject();
        GGMessage.put("table", fnIIDFGetTableSourceFullName(LUTable));
        GGMessage.put("pos", "10101010101010101010");
        JSONArray keysArray = new JSONArray();
        LUTableKeys.forEach(keysArray::put);
        GGMessage.put("primary_keys", keysArray);

        boolean updateTestResult = buildUpdate(LUTable, row, GGMessage, LUTableKeys);

        if (!this.LURealRootTable.contains(LUTable.k2StudioObjectName)) {
            JSONObject afterForInsert = GGMessage.getJSONObject("after");
            buildDelete(LUTable, row, GGMessage, LUTableKeys);
            buildInsert(LUTable, row, GGMessage, LUTableKeys, afterForInsert);
        }

        if (updateTestResult) this.tableProcessedCount++;
    }

    private void buildInsert(LudbObject LUTable, Db.Row row, JSONObject GGMessage, List<String> LUTableKeys, JSONObject afterForInsert) throws ExecutionException, InterruptedException, SQLException {
        Date date = new Date();
        GGMessage.put("op_type", "I");
        GGMessage.put("op_ts", clsDateFormat.format(date));
        GGMessage.put("current_ts", clsDateFormat.format(date).replaceFirst(" ", "T"));

        List<Object> keysValues = new ArrayList<>();
        StringBuilder rowKey = new StringBuilder();
        StringBuilder whereColumn = new StringBuilder();
        String prefix = "";

        for (String tableKey : LUTableKeys) {
            Object columnValue = row.get(tableKey);

            rowKey.append(columnValue);
            keysValues.add(columnValue);
            whereColumn.append(prefix).append(tableKey).append(" = ?");
            if ("".equals(prefix)) prefix = " and ";
        }

        GGMessage.put("after", afterForInsert);
        GGMessage.put("before", new JSONObject());

        IIDFProducerSingleton.getInstance().send(IDFINDER_TOPIC_PREFIX + GGMessage.getString("table"), rowKey.toString(), GGMessage.toString());
        sleep(this.sleep);

        String testResult = "passed";
        Object rs;
        int tries = 0;
        do {
            tries++;
            fabricGet("on");
            rs = fabric().fetch(String.format("select 1 from %s where %s", LUTable.ludbObjectName, whereColumn.toString()), keysValues.toArray()).firstValue();
        } while (rs == null && tries < 3);

        if (rs == null) {
            testResult = "failed";
            log.info("Insert validation for table {} failed, expected 1 row but found 0", LUTable.ludbObjectName);
        }

        insToStatusTable(LUTable, testResult, "I", date, GGMessage, null);

    }

    private void buildDelete(LudbObject LUTable, Db.Row row, JSONObject GGMessage, List<String> LUTableKeys) throws ExecutionException, InterruptedException, SQLException {
        Date date = new Date();
        GGMessage.put("op_type", "D");
        GGMessage.put("op_ts", clsDateFormat.format(date));
        GGMessage.put("current_ts", clsDateFormat.format(date).replaceFirst(" ", "T"));

        List<Object> keysValues = new ArrayList<>();
        StringBuilder rowKey = new StringBuilder();
        StringBuilder whereColumn = new StringBuilder();
        String prefix = "";

        JSONObject before = new JSONObject();
        for (String tableKey : LUTableKeys) {
            Object columnValue = row.get(tableKey);

            rowKey.append(columnValue);
            keysValues.add(columnValue);
            whereColumn.append(prefix).append(tableKey).append(" = ?");
            if ("".equals(prefix)) prefix = " and ";

            before.put(tableKey, columnValue);
        }

        GGMessage.put("before", before);
        GGMessage.put("after", new JSONObject());

        IIDFProducerSingleton.getInstance().send(IDFINDER_TOPIC_PREFIX + GGMessage.getString("table"), rowKey.toString(), GGMessage.toString());
        sleep(this.sleep);


        String testResult = "passed";
        Object rs;
        int tries = 0;
        do {
            tries++;
            fabricGet("on");
            rs = fabric().fetch(String.format("select 1 from %s where %s", LUTable.ludbObjectName, whereColumn.toString()), keysValues.toArray()).firstValue();
        } while (rs != null && tries < 3);

        if (rs != null) {
            testResult = "failed";
            log.info("Delete validation for table {} failed, expected 1 row but found 0", LUTable.ludbObjectName);
        }

        insToStatusTable(LUTable, testResult, "D", date, GGMessage, null);

    }

    private boolean buildUpdate(LudbObject LUTable, Db.Row row, JSONObject GGMessage, List<String> LUTableKeys) throws ExecutionException, InterruptedException, SQLException {
        Date date = new Date();
        GGMessage.put("op_type", "U");
        GGMessage.put("op_ts", clsDateFormat.format(date));
        GGMessage.put("current_ts", clsDateFormat.format(date).replaceFirst(" ", "T"));

        List<String> linkedColumnsToChildrenTables = getTableLinkedColumns(LUTable);

        String updatedColumnName = null;
        List<Object> keysValues = new ArrayList<>();
        StringBuilder rowKey = new StringBuilder();
        StringBuilder whereColumn = new StringBuilder();
        String prefix = "";
        JSONObject before = new JSONObject();
        JSONObject after = new JSONObject();
        String newColumnValue = null;
        boolean columnUpdated = false;
        for (Map.Entry<String, Object> rowEntry : row.entrySet()) {
            String columnName = rowEntry.getKey();
            Object columnValue = rowEntry.getValue();
            before.put(columnName.toUpperCase(), columnValue);

            if (LUTableKeys.contains(columnName)) {
                rowKey.append(columnValue);
                keysValues.add(columnValue);
                whereColumn.append(prefix).append(columnName).append(" = ?");
                if ("".equals(prefix)) prefix = " and ";
            }

            if (!columnUpdated && !linkedColumnsToChildrenTables.contains(columnName.toUpperCase()) && !LUTableKeys.contains(columnName)) {
                if (LUTable.ludbColumnMap.get(columnName).columnType.toLowerCase().equals("TEXT")) {
                    if (dateTimeColumnRegex.matcher(columnValue.toString()).matches()) {
                        columnValue = "2999-11-11 11:11:11";
                    } else if (timeColumnRegex.matcher(columnValue.toString()).matches()) {
                        columnValue = "11:11:11";
                    } else {
                        columnValue = "IIDF_Basic_Testing";
                    }
                } else if (LUTable.ludbColumnMap.get(columnName).columnType.toLowerCase().equals("integer")) {
                    columnValue = -999;
                }
                newColumnValue = columnValue.toString();
                columnUpdated = true;
                updatedColumnName = columnName;
            }

            after.put(columnName.toUpperCase(), columnValue);
        }

        GGMessage.put("before", before);
        GGMessage.put("after", after);

        if (!columnUpdated) {
            log.warn("Could not find column to update for table {}", LUTable.ludbObjectName);
            insToStatusTable(LUTable, "skip", "U", null, null, "column_to_update_not_found");
            this.total_tables_update_operation_skipped++;
            return false;
        }

        IIDFProducerSingleton.getInstance().send(IDFINDER_TOPIC_PREFIX + GGMessage.getString("table"), rowKey.toString(), GGMessage.toString());
        sleep(this.sleep);


        String testResult = "passed";
        Object columnValueFromRow;
        int tries = 0;
        do {
            fabricGet("on");
            columnValueFromRow = fabric().fetch(String.format("select %s from %s where %s", updatedColumnName, LUTable.ludbObjectName, whereColumn.toString()), keysValues.toArray()).firstValue();
            tries++;
        } while ((columnValueFromRow == null || !columnValueFromRow.toString().equals(newColumnValue)) && tries < 3);

        if (columnValueFromRow == null || !columnValueFromRow.toString().equals(newColumnValue)) {
            testResult = "failed";
            log.info("Update validation for table {} column {} failed, expected value {} actual value {}", LUTable.ludbObjectName, updatedColumnName, newColumnValue, columnValueFromRow);
        }

        insToStatusTable(LUTable, testResult, "U", date, GGMessage, null);

        return true;
    }

    private void insToStatusTable(LudbObject LUTable, String status, String operation, Date testDate, JSONObject GGMessage, String skip_reason) throws SQLException {
        final String testingTableInsert = "insert into %s.iidf_basic_lu_testing_details (test_sequence, table_name, operation_tested, test_time, test_result, GGMessage, skip_reason, lu_name, iid) values (?,?,?,?,?,?,?,?,?)";
        db(DB_CASS_NAME).execute(String.format(testingTableInsert, this.luType.getKeyspaceName()), this.testUID, LUTable.ludbObjectName, operation, testDate, status, GGMessage, skip_reason, this.LUName, this.LUI);
    }

    private List<String> getTableLinkedColumns(LudbObject LUTable) {
        List<String> linkedColumnsToChildrenTables = new ArrayList<>();
        Map<String, Map<String, List<LudbRelationInfo>>> LudbRelationInfo = this.luType.getLudbPhysicalRelations();
        String tableName = LUTable.k2StudioObjectName;
        Map<String, List<LudbRelationInfo>> val = LudbRelationInfo.get(tableName);
        if (val == null) return linkedColumnsToChildrenTables;

        for (Map.Entry<String, List<com.k2view.cdbms.lut.LudbRelationInfo>> relDetails : val.entrySet()) {
            relDetails.getValue().forEach(x -> linkedColumnsToChildrenTables.add(x.from.get("column").toUpperCase()));
        }

        Set<String> parentTables = this.LUTableToParent.get(LUTable.k2StudioObjectName);
        for (String parentTable : parentTables) {
            List<com.k2view.cdbms.lut.LudbRelationInfo> parentToChildRel = LudbRelationInfo.get(parentTable).get(LUTable.k2StudioObjectName);
            parentToChildRel.forEach(tablesRel -> linkedColumnsToChildrenTables.add(tablesRel.to.get("column").toUpperCase()));
        }

        return linkedColumnsToChildrenTables;
    }

    private void resetIID() throws SQLException {
        fabric().execute("delete instance ?.?", this.LUName, this.LUI);
        fabricGet("force");
    }

    private void fabricGet(String syncMode) throws SQLException {
        fabric().execute(String.format("set sync %s", syncMode));
        fabric().execute("get ?.?", this.LUName, this.LUI);
    }

    private Db.Row getTableFirstRecord(LudbObject LUTable) throws SQLException {
        return fabric().fetch(String.format("select * from %s limit 1", LUTable.ludbObjectName)).firstRow();
    }

    private List<String> getLUTableKeys(String LUName, String LUTableName) {
        LUType lut = LUTypeFactoryImpl.getInstance().getTypeByName(LUName);
        if (lut != null) {
            LudbObject rtTable = lut.ludbObjects.get(LUTableName);
            if (rtTable != null) {
                LudbIndexes luIndx = rtTable.indexes;
                if (luIndx == null) return null;
                for (LudbIndex luLocIndex : luIndx.localIndexes) {
                    if (luLocIndex.pk) {
                        return luLocIndex.columns;
                    }
                }
            }
        }
        return null;
    }

    private void buildTestingTable() throws SQLException {
        String buildTestingTable = "create table if not exists %s.iidf_basic_lu_testing_details (test_sequence text, table_name text, operation_tested text, test_time timestamp, test_result text, GGMessage text, skip_reason text, lu_name text, iid text, primary key ((test_sequence, table_name), operation_tested))";
        final String testingTableInsert = "create table if not exists %s.iidf_basic_lu_testing_summary (test_sequence text primary key, status text, total_tables bigint, total_tables_processed bigint, total_tables_skipped bigint, total_tables_user_skipped bigint, current_Table_in_process text, lu_name text, iid text, total_tables_update_operation_skipped bigint, total_tables_No_Row_Found_skipped bigint, total_tables_Primary_key_not_found bigint)";
        db(DB_CASS_NAME).execute(String.format(buildTestingTable, this.luType.getKeyspaceName()));
        db(DB_CASS_NAME).execute(String.format(testingTableInsert, this.luType.getKeyspaceName()));
    }

    private String fnIIDFGetTableSourceFullName(LudbObject LUTable) {
        TablePopulationObject popMap = ((TableObject) this.luType.ludbObjects.get(LUTable.ludbObjectName)).getAllTablePopulationObjects().get(0);
        String sourceSchemaName = popMap.iidFinderProp.sourceSchema;
        String sourceTableName = popMap.iidFinderProp.sourceTable;
        if (sourceSchemaName != null && !"".equals(sourceSchemaName) && sourceTableName != null && !"".equals(sourceTableName)) {
            return sourceSchemaName.toUpperCase() + "." + sourceTableName.toUpperCase();
        }
        return null;
    }

    private Map<String, Set<String>> fnIIDFGetParentTable() {
        Map<String, Set<String>> prntTable = new HashMap<>();
        LudbObject table = this.luType.getRootObject();
        setTblPrnt(table, prntTable);
        return prntTable;
    }

    private void setTblPrnt(LudbObject table, Map<String, Set<String>> prntTable) {
        if (table.childObjects == null) {
            return;
        }
        for (LudbObject chiTbl : table.childObjects) {
            Set<String> prntArr = prntTable.get(chiTbl.k2StudioObjectName);
            if (prntArr == null) {
                prntArr = new HashSet<>();
            }
            prntArr.add(table.k2StudioObjectName);
            prntTable.put(chiTbl.k2StudioObjectName, prntArr);

            setTblPrnt(chiTbl, prntTable);
        }
    }

    private void updateStatusTable(String status) throws SQLException {
        if ("completed".equals(status)) {
            this.currentTableInProcess = null;
        }
        final String testingTableInsert = "insert into %s.iidf_basic_lu_testing_summary (test_sequence, status, total_tables, total_tables_processed, total_tables_skipped, total_tables_user_skipped, current_Table_in_process, lu_name, iid, total_tables_update_operation_skipped, total_tables_No_Row_Found_skipped, total_tables_Primary_key_not_found) values (?,?,?,?,?,?,?,?,?,?,?,?)";
        db(DB_CASS_NAME).execute(String.format(testingTableInsert, this.luType.getKeyspaceName()), this.testUID, status, this.luType.ludbObjects.size(), this.tableProcessedCount, this.tableSkipCount, this.tableUserSkipCount, this.currentTableInProcess, this.LUName, this.LUI, this.total_tables_update_operation_skipped, this.tableNoRowFoundCount, this.total_table_Primary_key_not_found);
    }

}
