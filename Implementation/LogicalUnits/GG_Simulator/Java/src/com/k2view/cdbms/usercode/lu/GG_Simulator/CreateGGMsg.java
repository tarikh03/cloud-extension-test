package com.k2view.cdbms.usercode.lu.GG_Simulator;


import com.k2view.broadway.model.Actor;
import com.k2view.broadway.model.Context;
import com.k2view.broadway.model.Data;
import com.k2view.cdbms.config.LuTablesMap;
import com.k2view.cdbms.config.TableProperties;
import com.k2view.cdbms.finder.TableProperty;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.LUTypeFactoryImpl;
import com.k2view.cdbms.shared.utils.UserCodeDescribe;
import com.k2view.fabric.common.Log;
import com.k2view.fabric.parser.JSQLParserException;
import com.k2view.fabric.parser.expression.Expression;
import com.k2view.fabric.parser.expression.operators.relational.ExpressionList;
import com.k2view.fabric.parser.parser.CCJSqlParserManager;
import com.k2view.fabric.parser.schema.Column;
import com.k2view.fabric.parser.statement.Delete;
import com.k2view.fabric.parser.statement.Insert;
import com.k2view.fabric.parser.statement.update.UpdateTable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InvalidObjectException;
import java.io.StringReader;
import java.util.*;

import static com.k2view.cdbms.shared.user.UserCode.getTranslationValues;
import static com.k2view.cdbms.usercode.lu.GG_Simulator.Utils.Logic.*;


public class CreateGGMsg implements Actor {
    private static Log log = Log.a(CreateGGMsg.class);
    private final static java.util.regex.Pattern patternInsert = java.util.regex.Pattern.compile("(?i)^insert(.*)");
    private final static java.util.regex.Pattern patternUpdate = java.util.regex.Pattern.compile("(?i)^update(.*)");
    private final static java.util.regex.Pattern patternDelete = java.util.regex.Pattern.compile("(?i)^delete(.*)");
    private final static java.text.DateFormat clsDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private final static String WSdebug = "Debug Completed With No Errors!";
    private final static String LUTableNotFound = "Failed getting lu table name based on source schema and table names!, LU Name:%s, Source Schema Name:%s, Source Table Name:%s";
    @Override
    public void action(Data input, Data output, Context context) throws Exception {
        //Function inputs
        String sql_stmt = input.string("sql_stmt");
        String lu_name = input.string("lu_name");
        String topic_name = input.string("topic_name");
        String op_ts = input.string("op_ts");
        String pos = input.string("pos");
        Boolean before_values = Boolean.parseBoolean(input.string("before_values"));
        String partition_key = input.string("partition_key");
        String table_type = input.string("table_type");
        String[] primaryKeys = new String[0];
        clsDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        com.k2view.fabric.parser.statement.Statement sqlStmt = null;
        String sourceTableName = "";
        StringBuilder messageKey = new StringBuilder();

        if (isNullOrEmptyString(sql_stmt)) {
            sql_stmt = null;
        }
        String custom_table_name = "";
        //if the table type is reference
        if ("Reference".equalsIgnoreCase(table_type)) {
            lu_name = "k2_ref";
        }
        //extract target table name from iidfinder staging xml based on source table name
        if("Reference".equalsIgnoreCase(table_type) || "LU Table".equalsIgnoreCase(table_type)) {
            LuTablesMap tablesMap = TableProperties.getInstance().getLuMap(lu_name);
            for (Map.Entry<String, List<TableProperty>> entry : tablesMap.entrySet()) {
                String tblName = entry.getValue().get(0).getTableName();
                String kp = entry.getValue().get(0).getTableKeyspace();
                if (input.fields().get("source_table").toString().equalsIgnoreCase(kp + "." + tblName)) {
                    custom_table_name = entry.getValue().get(0).getTargetTableName();
                    primaryKeys = entry.getValue().get(0).getPrimaryKeys().toArray(new String[0]);
                    break;
                }
            }
        }else {
            //extract the lookup table partition/clustering keys to be used as the primary keys
            primaryKeys =( (HashSet<String>) extractLookupPrimaryKeys(input.string("source_table")).get("keys")).toArray(new String[0]);
        }
        if (isNullOrEmptyString(topic_name)) {
            topic_name = null;
        }
        if (isNullOrEmptyString(op_ts)) {
            op_ts = null;
        }
        if (isNullOrEmptyString(pos)) {
            pos = null;
        }
        if (isNullOrEmptyString(partition_key)) {
            partition_key = null;
        }
        try {
            sqlStmt = new CCJSqlParserManager().parse(new StringReader(sql_stmt));
        } catch (JSQLParserException e) {
            log.error(String.format("Failed to parse SQL statement, Please check logs! - %s", sql_stmt));
            throw e;
        }
        //Create Json to be used as the GG message
        JSONObject IIDJSon = new JSONObject();

        //Add message params
        if (op_ts != null && !op_ts.equals("")) {
            IIDJSon.put("op_ts", op_ts);
        } else {
            IIDJSon.put("op_ts", clsDateFormat.format(new java.util.Date()));
        }
        IIDJSon.put("current_ts", clsDateFormat.format(new java.util.Date()).replace(" ", "T"));
        if (pos != null && !pos.equals("")) {
            IIDJSon.put("pos", pos);
        } else {
            IIDJSon.put("pos", "00000000020030806864");
        }
        //Call function based on DML statement
        if (patternInsert.matcher(sql_stmt).find()) {
            sourceTableName = insert(IIDJSon, custom_table_name, lu_name, sqlStmt, messageKey,primaryKeys,table_type);
        } else if (patternUpdate.matcher(sql_stmt).find()) {
            sourceTableName = update(IIDJSon, custom_table_name, lu_name, sqlStmt, messageKey, before_values,primaryKeys,table_type);
        } else if (patternDelete.matcher(sql_stmt).find()) {
            sourceTableName = delete(IIDJSon, custom_table_name, lu_name, sqlStmt, messageKey,primaryKeys, table_type);
        }

        IIDJSon.put("table", sourceTableName.toUpperCase());
        //Output the GG message and the topic name
        output.put("TopicName", topic_name);
        output.put("GGMessage", IIDJSon);
    }

    private static void validatePKExistsInWhere(String[] pkCuls, JSONObject before) {
        for (String keyColumn : pkCuls) {
            if (!before.has(keyColumn)) {
                throw new RuntimeException("All primary key columns must be part of where!");
            }
        }
    }

    private static void setPrimaryKeys(String[] luTablePKColumns, JSONObject IIDJSon) {
        JSONArray PK = new JSONArray();
        for (String pkCul : luTablePKColumns) {
            PK.put(pkCul);
        }
        IIDJSon.put("primary_keys", PK);
    }

    //Create 'After' section in the GG message
    private static JSONObject setMessageAfter(List<Expression> statementColumnsValues, List<Column> statementColumnsName, Map<String, String> luTableColumnsMap, JSONObject IIDJSon, List<String> tableKeys, StringBuilder messageKey) {
        JSONObject after = new JSONObject();
        int i = 0;
        for (Expression x : statementColumnsValues) {
            String columnName = statementColumnsName.get(i).getColumnName();
            String columnValue = (x + "");
            //this is used to skip null values
            if("nullFromBW".equalsIgnoreCase(columnValue)){
                i++;
                continue;
            }
            setMessageKey(tableKeys, messageKey, columnName, columnValue);

            if (luTableColumnsMap.get(columnName.toUpperCase()).equals("TEXT")) {
                String textVal = columnValue.replaceAll("^'|'$", "");
                after.put(columnName.toUpperCase(), textVal);
            } else if (luTableColumnsMap.get(columnName.toUpperCase()).equals("INTEGER") && columnValue.length() <= 11) {
                int intVal;
                try {
                    intVal = Integer.parseInt(columnValue);
                } catch (Exception e) {
                    log.error(String.format("Failed To Parse Integer Value For Column %s, Value Found:%s", columnName, columnValue));
                    throw e;
                }
                after.put(columnName.toUpperCase(), intVal);
            } else if (luTableColumnsMap.get(columnName.toUpperCase()).equals("INTEGER") && columnValue.length() > 11) {
                long intVal;
                try {
                    intVal = Long.parseLong(columnValue);
                } catch (Exception e) {
                    log.error(String.format("Failed To Parse Long Value For Column %s, Value Found:%s", columnName, columnValue));
                    throw e;
                }
                after.put(columnName.toUpperCase(), intVal);
            } else if (luTableColumnsMap.get(columnName.toUpperCase()).equals("REAL")) {
                double doubeValue;
                try {
                    doubeValue = Double.parseDouble(columnValue);
                } catch (Exception e) {
                    log.error(String.format("Failed To Parse Double Value For Column %s, Value Found:%s", columnName, columnValue));
                    throw e;
                }
                after.put(columnName.toUpperCase(), doubeValue);
            }else after.put(columnName.toUpperCase(), columnValue);
            i++;
        }
        IIDJSon.put("after", after);
        return after;
    }

    private static void setPrimaryKeysForUpdate(JSONObject after, JSONObject before, String[] luTablePKColumns) {
        List<String> pkList = Arrays.asList(luTablePKColumns);
        Iterator<String> beforeKeys = before.keys();
        while (beforeKeys.hasNext()) {
            String key = beforeKeys.next();
            if (pkList.contains(key.toUpperCase()) && !after.has(key)) {
                after.put(key, before.get(key));
            }
        }
    }

    //Create 'After' section in the GG message
    private static JSONObject setMessageBefore(Map<String, String> luTableColumnsMap, String[] statementColumnsNdValues, JSONObject IIDJSon, List<String> tableKeys, StringBuilder messageKey) {
        String parseFailed = "Failed To Parse Long Value For Column %s , Value Found:%s";
        JSONObject before = new JSONObject();
        for (String culNdVal : statementColumnsNdValues) {
            String columnName = culNdVal.split("=")[0].trim();
            String columnValue = culNdVal.split("=")[1].trim();

            if (tableKeys != null) setMessageKey(tableKeys, messageKey, columnName, columnValue);

            switch (luTableColumnsMap.get(columnName.toUpperCase())) {
                case "TEXT":
                    String textVal = columnValue.replaceAll("^'|'$", "");
                    before.put(columnName.toUpperCase(), textVal);
                    break;
                case "INTEGER":
                    long longVal;
                    try {
                        longVal = Long.parseLong(columnValue);
                    } catch (Exception e) {
                        log.error(String.format("Failed To Parse Long Value For Column %s, Value Found:%s", columnName, columnValue));
                        throw e;
                    }
                    before.put(columnName.toUpperCase(), longVal);
                    break;
                case "REAL":
                    double doubeValue;
                    try {
                        doubeValue = Double.parseDouble(columnValue);
                    } catch (Exception e) {
                        log.error(String.format("Failed To Parse Double Value For Column %s, Value Found:%s", columnName, columnValue));
                        throw e;
                    }
                    before.put(columnName.toUpperCase(), doubeValue);
                    break;
            }
        }
        IIDJSon.put("before", before);
        return before;
    }

    private static String getLUTableName(String luName, String sourceSchemaName, String sourceTableName) throws InvalidObjectException {
        LUType luT = LUTypeFactoryImpl.getInstance().getTypeByName(luName);
        if (luT == null) {
            throw new InvalidObjectException(String.format("LU Name %s was not found!", luName));
        }

        LudbObject rootLUTable = luT.getRootObject();
        return findLUTable(rootLUTable, sourceSchemaName, sourceTableName);
    }

    private static String findLUTable(LudbObject rootLUTable, String sourceSchemaName, String sourceTableName) {
        String LUTableName = "";
        for (LudbObject luTable : rootLUTable.childObjects) {
            List<TablePopulationObject> idfinderProp = ((TableObject) luTable).getEnabledTablePopulationObjects();
            if (idfinderProp != null && idfinderProp.size() > 0 && idfinderProp.get(0).iidFinderProp.sourceSchema.equalsIgnoreCase(sourceSchemaName) && idfinderProp.get(0).iidFinderProp.sourceTable.equalsIgnoreCase(sourceTableName)) {
                return luTable.k2StudioObjectName;
            } else {
                LUTableName = findLUTable(luTable, sourceSchemaName, sourceTableName);
                if (LUTableName != null && !"".equals(LUTableName)) {
                    return LUTableName;
                }
            }
        }
        return LUTableName;
    }


    private static void setMessageKey(List<String> tableKeys, StringBuilder messageKey, String columnName, String columnValue) {
        if (tableKeys != null && tableKeys.contains(columnName.toUpperCase())) messageKey.append(columnValue);
    }

    @SuppressWarnings("unchecked")
    private static String insert(JSONObject IIDJSon, String custom_table_name, String lu_name, com.k2view.fabric.parser.statement.Statement sqlStmt, StringBuilder messageKey, String[] primaryKeys, String tableType) throws Exception {
        Insert insStmt = (Insert) sqlStmt;
        if (insStmt.getTable().getSchemaName() == null) {
            throw new RuntimeException("Schema name in statement is mandatory!");
        }
        IIDJSon.put("op_type", "I");
        String sourceTableName = insStmt.getTable().getSchemaName() + "." + insStmt.getTable().getName();
        String luTableName;
        if ((!"Lookup".equalsIgnoreCase(tableType)) && (custom_table_name == null || "".equals(custom_table_name))) {
            luTableName = getLUTableName(lu_name, insStmt.getTable().getSchemaName(), insStmt.getTable().getName());
        } else {
            luTableName = custom_table_name;
        }
        if (luTableName == null) {
            throw new InvalidObjectException(String.format(LUTableNotFound, lu_name, insStmt.getTable().getSchemaName(), insStmt.getTable().getName()));
        }
        Map<String, String> luTableColumnsMap;
        //extract table columns based on table type.
        //columns of lookup table are extracted from lookup broadway flows
        //columns of LU tables are extracted from LU
        if("Lookup".equalsIgnoreCase(tableType)){
            luTableColumnsMap = (Map<String, String>) extractLookupPrimaryKeys(sourceTableName).get("columns");
        } else luTableColumnsMap = fnIIDFGetTablesCoInfo(luTableName, lu_name);
        setPrimaryKeys(primaryKeys, IIDJSon);
        JSONObject after = setMessageAfter(((ExpressionList) insStmt.getItemsList()).getExpressions(), insStmt.getColumns(), luTableColumnsMap, IIDJSon, Arrays.asList(primaryKeys), messageKey);
        validatePKExistsInWhere(primaryKeys, after);
        return sourceTableName;
    }

    @SuppressWarnings("unchecked")
    private static String update(JSONObject IIDJSon, String custom_table_name, String lu_name, com.k2view.fabric.parser.statement.Statement sqlStmt, StringBuilder messageKey, boolean before_values,String[] primaryKeys, String tableType) throws Exception {
        UpdateTable upStmt = (UpdateTable) sqlStmt;

        if (upStmt.getTable().getSchemaName() == null) {
            throw new RuntimeException("Schema name in statement is mandatory!");
        }
        IIDJSon.put("op_type", "U");
        String sourceTableName = upStmt.getTable().getSchemaName() + "." + upStmt.getTable().getName();
        String luTableName;
        //extract table columns based on table type.
        //columns of lookup table are extracted from lookup broadway flows
        //columns of LU tables are extracted from LU
        if ((!"Lookup".equalsIgnoreCase(tableType)) && (custom_table_name == null || "".equals(custom_table_name))) {
            luTableName = getLUTableName(lu_name, upStmt.getTable().getSchemaName(), upStmt.getTable().getName());
        } else {
            luTableName = custom_table_name;
        }
        if (luTableName == null) {
            throw new InvalidObjectException(String.format(LUTableNotFound, lu_name, upStmt.getTable().getSchemaName(), upStmt.getTable().getName()));
        }
        Map<String, String> luTableColumnsMap;
        if ("Lookup".equalsIgnoreCase(tableType)) {
            luTableColumnsMap = (Map<String, String>) extractLookupPrimaryKeys(sourceTableName).get("columns");
        } else luTableColumnsMap = fnIIDFGetTablesCoInfo(luTableName, lu_name);
        setPrimaryKeys(primaryKeys, IIDJSon);
        JSONObject before = setMessageBefore(luTableColumnsMap, upStmt.getWhere().toString().split("(?i)( and )"), IIDJSon, Arrays.asList(primaryKeys), messageKey);
        if (!before_values) {
            IIDJSon.remove("before");
        }
        JSONObject after = setMessageAfter(upStmt.getExpressions(), upStmt.getColumns(), luTableColumnsMap, IIDJSon, null, null);
        setPrimaryKeysForUpdate(after, before, primaryKeys);
        validatePKExistsInWhere(primaryKeys, before);
        return sourceTableName;
    }

    private static String delete(JSONObject IIDJSon, String custom_table_name, String lu_name, com.k2view.fabric.parser.statement.Statement sqlStmt, StringBuilder messageKey,String[] primaryKeys, String tableType) throws Exception {
        Delete delStmt = (Delete) sqlStmt;
        if (delStmt.getTable().getSchemaName() == null) {
            throw new RuntimeException("Schema name in statement is mandatory!");
        }
        IIDJSon.put("op_type", "D");
        String sourceTableName = delStmt.getTable().getSchemaName() + "." + delStmt.getTable().getName();
        String luTableName;
        //extract table columns based on table type.
        //columns of lookup table are extracted from lookup broadway flows
        //columns of LU tables are extracted from LU
        if ((!"Lookup".equalsIgnoreCase(tableType)) && (custom_table_name == null || "".equals(custom_table_name))) {
            luTableName = getLUTableName(lu_name, delStmt.getTable().getSchemaName(), delStmt.getTable().getName());
        } else {
            luTableName = custom_table_name;
        }
        if (luTableName == null) {
            throw new InvalidObjectException(String.format(LUTableNotFound, lu_name, delStmt.getTable().getSchemaName(), delStmt.getTable().getName()));
        }
        Map<String, String> luTableColumnsMap;
        if ("Lookup".equalsIgnoreCase(tableType)) {
            luTableColumnsMap = (Map<String, String>) extractLookupPrimaryKeys(sourceTableName).get("columns");
        } else luTableColumnsMap = fnIIDFGetTablesCoInfo(luTableName, lu_name);
        setPrimaryKeys(primaryKeys, IIDJSon);
        JSONObject before = setMessageBefore(luTableColumnsMap, delStmt.getWhere().toString().split("(?i)( and )"), IIDJSon, Arrays.asList(primaryKeys), messageKey);
        validatePKExistsInWhere(primaryKeys, before);
        return sourceTableName;
    }

    @UserCodeDescribe.out(name = "result", type = Object.class, desc = "")
    private static String[] fnIIDFGetTablePK(String table_name, String lu_name) throws Exception {
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

    @UserCodeDescribe.out(name = "rs", type = Object.class, desc = "")
    private static Map<String, String> fnIIDFGetTablesCoInfo(String table_name, String lu_name) throws Exception {
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

}


