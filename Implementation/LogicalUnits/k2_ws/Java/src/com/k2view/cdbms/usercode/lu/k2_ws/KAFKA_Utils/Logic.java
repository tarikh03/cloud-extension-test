/////////////////////////////////////////////////////////////////////////
// Project Web Services
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.lu.k2_ws.KAFKA_Utils;

import java.util.*;
import java.io.*;

import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.user.WebServiceUserCode;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.usercode.common.IIDFProducerSingleton;
import com.k2view.fabric.parser.JSQLParserException;
import com.k2view.fabric.parser.expression.Expression;
import com.k2view.fabric.parser.expression.operators.relational.ExpressionList;
import com.k2view.fabric.parser.parser.CCJSqlParserManager;
import com.k2view.fabric.parser.schema.Column;
import com.k2view.fabric.parser.statement.Delete;
import com.k2view.fabric.parser.statement.Insert;
import com.k2view.fabric.parser.statement.Statement;
import com.k2view.fabric.parser.statement.update.UpdateTable;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.k2view.cdbms.usercode.common.IIDF.SharedLogic.fnIIDFGetTablePK;
import static com.k2view.cdbms.usercode.common.IIDF.SharedLogic.fnIIDFGetTablesCoInfo;

import com.k2view.fabric.api.endpoint.Endpoint.*;

import java.sql.*;
import java.math.*;

import com.k2view.cdbms.sync.*;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.cdbms.shared.logging.LogEntry.*;
import com.k2view.cdbms.func.oracle.OracleToDate;
import com.k2view.cdbms.func.oracle.OracleRownum;

import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.shared.user.ProductFunctions.*;
import static com.k2view.cdbms.usercode.common.SharedLogic.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam", "unchecked"})
public class Logic extends WebServiceUserCode {
    private final static String LU_TABLES = "IidFinder";
    private final static String REF = "REF";
    private final static String LOOKUP = "LKUP";
    private final static java.util.regex.Pattern patternInsert = java.util.regex.Pattern.compile("(?i)^insert(.*)");
    private final static java.util.regex.Pattern patternUpdate = java.util.regex.Pattern.compile("(?i)^update(.*)");
    private final static java.util.regex.Pattern patternDelete = java.util.regex.Pattern.compile("(?i)^delete(.*)");
    private final static java.text.DateFormat clsDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private final static String debugResult = "Debug Completed With No Errors! " + System.lineSeparator() + "Topic Name:%s" + System.lineSeparator() + "Msg:%s";
    private final static String WSResult = "WS Completed Publishing Message To Kafka With No Errors! " + System.lineSeparator() + "Topic Name:%s" + System.lineSeparator() + "Msg:%s";
    private final static String LUTableNotFound = "Failed getting lu table name based on source schema and table names!, LU Name:%s, Source Schema Name:%s, Source Table Name:%s";

	@webService(path = "", verb = {MethodType.GET}, version = "1", isRaw = false, isCustomPayload = false, produce = {Produce.XML, Produce.JSON})
	public static String wsUpdateKafka(@param(description="Mandatory") String sql_stmt, @param(description="Mandatory") String lu_name, @param(description="Mandatory If the source schema and table name exists for more then one LU Table") String custom_lu_table_name, @param(description="If Set To True WS Will Not Send Records To Kafka") Boolean debug, @param(description="If You Wish To Override The Topic Logi") String topic_name, @param(description="If You Wish To Customise The Message Time") String op_ts, @param(description="If You Wish To Customise The pos Value") String pos, @param(description="If You Wish To Customise targetIid To Be sent To IIDFinder") String targetIid, @param(description="If You Wish To Have No Before") Boolean no_before, @param(description="If To Send op_tp as R") Boolean replicate, @param(description="For Use For Lookup Tables Topic Only") String partiKey, @param(description="Can Be ref/lookup Or Left Empty For LU Tables") String tblType) throws Exception {
		clsDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		Statement sqlStmt;
		String sourceTableName = "";
		StringBuilder messageKey = new StringBuilder();
		
		try {
		    sqlStmt = new CCJSqlParserManager().parse(new StringReader(sql_stmt));
		} catch (JSQLParserException e) {
		    log.error(String.format("Failed to parse SQL statement, Please check logs! - %s", sql_stmt));
		    throw e;
		}
		
		JSONObject IIDJSon = new JSONObject();
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
		
		if (targetIid != null && !targetIid.equals("")) {
		    IIDJSon.put("targetIid", targetIid);
		}
		
		if (replicate != null && replicate) {
		    IIDJSon.put("op_type", "R");
		}
		
		if (patternInsert.matcher(sql_stmt).find()) {
		    sourceTableName = insert(IIDJSon, custom_lu_table_name, lu_name, sqlStmt, messageKey);
		} else if (patternUpdate.matcher(sql_stmt).find()) {
		    sourceTableName = update(IIDJSon, custom_lu_table_name, lu_name, sqlStmt, messageKey, no_before);
		} else if (patternDelete.matcher(sql_stmt).find()) {
		    sourceTableName = delete(IIDJSon, custom_lu_table_name, lu_name, sqlStmt, messageKey);
		}
		
		IIDJSon.put("table", sourceTableName.toUpperCase());
		String topicName = setTopicName(topic_name, tblType, partiKey, sourceTableName);
		
		if (!debug) {
		    IIDFProducerSingleton.getInstance().send(topicName, messageKey.toString(), IIDJSon.toString());
		    return String.format(WSResult, topicName, IIDJSon.toString());
		}
		
		return String.format(debugResult, topicName, IIDJSon.toString());
	}

    private static String setTopicName(String i_topic_name, String i_tblType, String i_partiKey, String i_sourceTableName) {
        String topicName;
        if (i_topic_name != null && !"".equals(i_topic_name)) {
            return i_topic_name;
        } else {
            if (i_tblType != null && i_tblType.equalsIgnoreCase("ref")) {
                topicName = REF + ".<>";
            } else if (i_tblType != null && i_tblType.equalsIgnoreCase("lookup")) {
                topicName = LOOKUP + ".<>_LKUP_" + i_partiKey;
            } else {
                topicName = LU_TABLES + ".<>";
            }
            return topicName.replace("<>", i_sourceTableName.toUpperCase());
        }
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

    private static JSONObject setMessageAfter(List<Expression> statementColumnsValues, List<Column> statementColumnsName, Map<String, String> luTableColumnsMap, JSONObject IIDJSon, List<String> tableKeys, StringBuilder messageKey) {
        JSONObject after = new JSONObject();
        int i = 0;
        for (Expression x : statementColumnsValues) {
            String columnName = statementColumnsName.get(i).getColumnName();
            String columnValue = (x + "");

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
            }
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

    private static JSONObject setMessageBefore(Map<String, String> luTableColumnsMap, String[] statementColumnsNdValues, JSONObject IIDJSon, List<String> tableKeys, StringBuilder messageKey) {
        String parseFailed = "Failed To Parse Long Value For Column %s , Value Found:%s";
        JSONObject before = new JSONObject();
        for (String culNdVal : statementColumnsNdValues) {
            String columnName = culNdVal.split("=")[0].trim();
            String columnValue = culNdVal.split("=")[1].trim();

            if (tableKeys != null) setMessageKey(tableKeys, messageKey, columnName, columnValue);

            if (luTableColumnsMap.get(columnName.toUpperCase()).equals("TEXT")) {
                String textVal = columnValue.replaceAll("^'|'$", "");
                before.put(columnName.toUpperCase(), textVal);
            } else if (luTableColumnsMap.get(columnName.toUpperCase()).equals("INTEGER")) {
                long longVal;
                try {
                    longVal = Long.parseLong(columnValue);
                } catch (Exception e) {
                    log.error(String.format("Failed To Parse Long Value For Column %s, Value Found:%s", columnName, columnValue));
                    throw e;
                }
                before.put(columnName.toUpperCase(), longVal);
            } else if (luTableColumnsMap.get(columnName.toUpperCase()).equals("REAL")) {
                double doubeValue;
                try {
                    doubeValue = Double.parseDouble(columnValue);
                } catch (Exception e) {
                    log.error(String.format("Failed To Parse Double Value For Column %s, Value Found:%s", columnName, columnValue));
                    throw e;
                }
                before.put(columnName.toUpperCase(), doubeValue);
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

    private static String insert(JSONObject IIDJSon, String custom_lu_table_name, String lu_name, Statement sqlStmt, StringBuilder messageKey) throws Exception {
        Insert insStmt = (Insert) sqlStmt;

        if (insStmt.getTable().getSchemaName() == null) {
            throw new RuntimeException("Schema name in statement is mandatory!");
        }

        IIDJSon.put("op_type", "I");

        String sourceTableName = insStmt.getTable().getSchemaName() + "." + insStmt.getTable().getName();

        String luTableName;
        if (custom_lu_table_name == null || "".equals(custom_lu_table_name)) {
            luTableName = getLUTableName(lu_name, insStmt.getTable().getSchemaName(), insStmt.getTable().getName());
        } else {
            luTableName = custom_lu_table_name;
        }


        if (luTableName == null) {
            throw new InvalidObjectException(String.format(LUTableNotFound, lu_name, insStmt.getTable().getSchemaName(), insStmt.getTable().getName()));
        }

        Map<String, String> luTableColumnsMap = fnIIDFGetTablesCoInfo(luTableName, lu_name);
        String[] luTablePKColumns = fnIIDFGetTablePK(luTableName, lu_name);

        setPrimaryKeys(luTablePKColumns, IIDJSon);

        JSONObject after = setMessageAfter(((ExpressionList) insStmt.getItemsList()).getExpressions(), insStmt.getColumns(), luTableColumnsMap, IIDJSon, Arrays.asList(luTablePKColumns), messageKey);
        validatePKExistsInWhere(luTablePKColumns, after);

        return sourceTableName;
    }

    private static String update(JSONObject IIDJSon, String custom_lu_table_name, String lu_name, Statement sqlStmt, StringBuilder messageKey, boolean no_before) throws Exception {
        UpdateTable upStmt = (UpdateTable) sqlStmt;

        if (upStmt.getTable().getSchemaName() == null) {
            throw new RuntimeException("Schema name in statement is mandatory!");
        }

        IIDJSon.put("op_type", "U");

        String sourceTableName = upStmt.getTable().getSchemaName() + "." + upStmt.getTable().getName();

        String luTableName;
        if (custom_lu_table_name == null || "".equals(custom_lu_table_name)) {
            luTableName = getLUTableName(lu_name, upStmt.getTable().getSchemaName(), upStmt.getTable().getName());
        } else {
            luTableName = custom_lu_table_name;
        }

        if (luTableName == null) {
            throw new InvalidObjectException(String.format(LUTableNotFound, lu_name, upStmt.getTable().getSchemaName(), upStmt.getTable().getName()));
        }

        Map<String, String> luTableColumnsMap = fnIIDFGetTablesCoInfo(luTableName, lu_name);
        String[] luTablePKColumns = fnIIDFGetTablePK(luTableName, lu_name);

        setPrimaryKeys(luTablePKColumns, IIDJSon);

        JSONObject before = setMessageBefore(luTableColumnsMap, upStmt.getWhere().toString().split("(?i)( and )"), IIDJSon, Arrays.asList(luTablePKColumns), messageKey);
        if (no_before) {
            IIDJSon.remove("before");
        }

        JSONObject after = setMessageAfter(upStmt.getExpressions(), upStmt.getColumns(), luTableColumnsMap, IIDJSon, null, null);
        setPrimaryKeysForUpdate(after, before, luTablePKColumns);
        validatePKExistsInWhere(luTablePKColumns, before);

        return sourceTableName;
    }

    private static String delete(JSONObject IIDJSon, String custom_lu_table_name, String lu_name, Statement sqlStmt, StringBuilder messageKey) throws Exception {
        Delete delStmt = (Delete) sqlStmt;

        if (delStmt.getTable().getSchemaName() == null) {
            throw new RuntimeException("Schema name in statement is mandatory!");
        }

        IIDJSon.put("op_type", "D");

        String sourceTableName = delStmt.getTable().getSchemaName() + "." + delStmt.getTable().getName();

        String luTableName;
        if (custom_lu_table_name == null || "".equals(custom_lu_table_name)) {
            luTableName = getLUTableName(lu_name, delStmt.getTable().getSchemaName(), delStmt.getTable().getName());
        } else {
            luTableName = custom_lu_table_name;
        }

        if (luTableName == null) {
            throw new InvalidObjectException(String.format(LUTableNotFound, lu_name, delStmt.getTable().getSchemaName(), delStmt.getTable().getName()));
        }

        Map<String, String> luTableColumnsMap = fnIIDFGetTablesCoInfo(luTableName, lu_name);
        String[] luTablePKColumns = fnIIDFGetTablePK(luTableName, lu_name);

        setPrimaryKeys(luTablePKColumns, IIDJSon);

        JSONObject before = setMessageBefore(luTableColumnsMap, delStmt.getWhere().toString().split("(?i)( and )"), IIDJSon, Arrays.asList(luTablePKColumns), messageKey);
        validatePKExistsInWhere(luTablePKColumns, before);

        return sourceTableName;
    }
}
