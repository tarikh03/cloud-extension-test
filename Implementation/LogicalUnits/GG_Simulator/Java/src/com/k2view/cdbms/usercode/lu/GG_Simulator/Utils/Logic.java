/////////////////////////////////////////////////////////////////////////
// LU Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.lu.GG_Simulator.Utils;

import java.util.*;

import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.user.UserCode;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import org.json.JSONObject;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class Logic extends UserCode {

	private final static String LU_TABLES = "IidFinder";
	private final static String REF = "REF";
	private final static String LOOKUP = "LKUP";

	@out(name = "result", type = Boolean.class, desc = "")
	public static Boolean isNullOrEmptyString(String str) throws Exception {
		return str == null || "".equalsIgnoreCase(str) || "null".equalsIgnoreCase(str) || "undefined".equalsIgnoreCase(str);
	}


	public static Map<String, Object> extractLookupPrimaryKeys(String tableName) throws Exception {
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


	public static String setTopicName(String i_topic_name, String i_tblType, String i_partiKey, String i_sourceTableName) {
		String topicName;
		//If topic name was sent as input, return it.
		if (i_topic_name != null && !"".equals(i_topic_name)) {
			return i_topic_name;
		} else {
			//Structure topic name based on the table type.
			if (i_tblType != null && i_tblType.equalsIgnoreCase("Reference")) {
				topicName = REF + ".<>";
			} else if (i_tblType != null && i_tblType.equalsIgnoreCase("lookup")) {
				topicName = LOOKUP + ".<>_LKUP_" + i_partiKey;
			} else {
				topicName = LU_TABLES + ".<>";
			}
			return topicName.replace("<>", i_sourceTableName.toUpperCase());
		}
	}
}
