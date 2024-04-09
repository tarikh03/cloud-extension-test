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


public class ExtractLUTableMetadata implements Actor {
    private static Log log = Log.a(ExtractLUTableMetadata.class);
    @Override
    public void action(Data input, Data output, Context context) throws Exception {
        //Function inputs
        String lu_name = input.string("lu_name");
        String table_type = input.string("table_type");
        String[] primaryKeys = new String[0];

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
            custom_table_name = input.string("source_table");
        }
        output.put("primaryKeys", primaryKeys);
        output.put("luTableName",custom_table_name);
    }
    private static Map<String, Object> extractLookupPrimaryKeys(String tableName) throws Exception {
        String tblName = tableName.replaceFirst("\\.", "_");
        String BWFlowLookupName = String.format("Lookup_%s.flow", tblName);
        Map<String, Object> tableInfo = new HashMap<>();
        //search the creation broadway flow of the input table
        byte[] bwFlow = LUTypeFactoryImpl.getInstance().getTypeByName("Lookup").broadwayFiles().get(BWFlowLookupName);
        JSONObject testV = new JSONObject(new String(bwFlow));
        testV.getJSONArray("levels").forEach(stage -> {
            ((JSONObject) stage).getJSONArray("stages").forEach(innerStage -> {
                ((JSONObject) innerStage).getJSONArray("actors").forEach(actor -> {
                    //search the table create actor
                    if (((JSONObject) actor).getString("name").equalsIgnoreCase("lookup_table_create_"+tblName)) {
                        JSONObject actorType = ((JSONObject) actor).getJSONObject("actorType");
                        StringBuilder table = new StringBuilder();
                        Map<String, String> tblColumns = new HashMap<>();
                        Set<String> tblKey = new HashSet<>();
                        actorType.getJSONArray("inputs").forEach(input -> {
                            if ("table".equals(((JSONObject) input).getString("name"))) {
                                table.append(((JSONObject) input).getString("const").toUpperCase());
                            }
                            if ("fields".equals(((JSONObject) input).getString("name"))) {
                                ((JSONObject) input).getJSONArray("const").forEach(column -> {
                                    String columnName = ((JSONObject) column).getString("name").toUpperCase();
                                    boolean partitionKey = ((JSONObject) column).getBoolean("partition_key");
                                    boolean clusteringKey = ((JSONObject) column).getBoolean("clustering_key");
                                    //save the partition keys and clustering keys primary keys
                                    String type = ((JSONObject) column).getString("type");
                                    if (partitionKey || clusteringKey) {
                                        tblKey.add(columnName);
                                    }
                                    //convert cassandra data types to sqlite (Fabric) data types
                                    if("BIGINT".equalsIgnoreCase(type) || "INT".equalsIgnoreCase(type) || "SMALLINT".equalsIgnoreCase(type) || "COUNTER".equalsIgnoreCase(type)){
                                        type = "INTEGER";
                                    } else if("DECIMAL".equalsIgnoreCase(type) || "DOUBLE".equalsIgnoreCase(type) || "FLOAT".equalsIgnoreCase(type)){
                                        type = "REAL";
                                    }else type = type.toUpperCase();
                                    tblColumns.put(columnName,type);
                                });
                            }
                        });
                        tableInfo.put("keys",tblKey);
                        tableInfo.put("columns",tblColumns);
                        tableInfo.put("name",table.toString());
                    }
                });
            });
        });
        return tableInfo;
    }

}


