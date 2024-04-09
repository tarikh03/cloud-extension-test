/////////////////////////////////////////////////////////////////////////
// Project Web Services
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.lu.k2_ws.IIDFUtils;

import java.util.*;
import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.k2view.cdbms.usercode.common.IIDFProducerSingleton;
import com.k2view.fabric.commonArea.producer.pubsub.DeltaKafkaProducerProperties;
import com.k2view.fabric.parser.JSQLParserException;
import com.k2view.cdbms.interfaces.jobs.kafka.KafkaInterface;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.user.WebServiceUserCode;
import com.k2view.cdbms.lut.*;
import com.k2view.fabric.api.endpoint.Endpoint.*;
import com.k2view.fabric.common.ClusterUtil;
import com.k2view.fabric.common.ini.Configurator;

import com.k2view.fabric.parser.expression.Expression;
import com.k2view.fabric.parser.expression.operators.relational.ExpressionList;
import com.k2view.fabric.parser.parser.CCJSqlParserManager;
import com.k2view.fabric.parser.schema.Column;
import com.k2view.fabric.parser.statement.Delete;
import com.k2view.fabric.parser.statement.Insert;
import com.k2view.fabric.parser.statement.update.UpdateTable;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.KafkaFuture;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.k2view.cdbms.usercode.common.IIDF.SharedLogic.fnIIDFGetTablePK;
import static com.k2view.cdbms.usercode.common.IIDF.SharedLogic.fnIIDFGetTablesCoInfo;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;

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

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class Logic extends WebServiceUserCode {
    private final static String LU_TABLES = "IidFinder";
    private final static String REF = "REF";
    private final static String LOOKUP = "LKUP";
    private final static java.util.regex.Pattern patternInsert = java.util.regex.Pattern.compile("(?i)^insert(.*)");
    private final static java.util.regex.Pattern patternUpdate = java.util.regex.Pattern.compile("(?i)^update(.*)");
    private final static java.util.regex.Pattern patternDelete = java.util.regex.Pattern.compile("(?i)^delete(.*)");
    private final static java.text.DateFormat clsDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private final static String WSdebug = "Debug Completed With No Errors!";
    private final static String LUTableNotFound = "Failed getting lu table name based on source schema and table names!, LU Name:%s, Source Schema Name:%s, Source Table Name:%s";

	@desc("This WS receives a list of LU names and build the relevant IIDF lib tables")
	@webService(path = "", verb = {MethodType.GET}, version = "1", isRaw = true, isCustomPayload = false, produce = {Produce.JSON}, elevatedPermission = true)
	public static Object wsBuildIIDFTables(@param(description="The LU names list separated by pipe") String LUsList) throws Exception {
		JSONArray jsonArray = new JSONArray();
		final String IIDStats = "CREATE TABLE if not exists %s.iid_stats (    iid text,    iid_sync_time timestamp,    account_change_count bigint,    cross_instance_check_count int,    delete_orphan_check_count int,    iid_change_ind_count bigint,    iid_deltas_count int,    iid_skipped boolean,    replicates_requests_count int,    replicates_send_to_kafka_due_to_delete_orphan_count bigint,    replicates_send_to_kafka_due_to_replicate_request_count bigint,    sync_duration bigint,    time_to_execute_cross_instance_check bigint,    time_to_execute_delete_orphan_check bigint,    time_to_execute_replicates_request bigint,    time_to_fetch_deltas_from_api bigint,    time_to_fetch_deltas_from_iidf_queue_and_execute bigint,    time_to_insert_deltas_to_iidfqueue_table bigint,    PRIMARY KEY (iid, iid_sync_time))";
		final String IDFinderStats = "CREATE TABLE if not exists %s.idfinder_stats (    host text,    operation_name text,    iteration int,    last_update_time timestamp,    operation_value bigint,    status text,    PRIMARY KEY ((host, operation_name), iteration))";
		final String luStats = "CREATE TABLE if not exists %s.lu_stats (    job_run_seq bigint,    operation_name text,    last_update_time timestamp,    operation_value text,    status text,    PRIMARY KEY (job_run_seq, operation_name, last_update_time))";
		for (String LU : LUsList.split("\\|")) {
		    JSONObject jsonObject = new JSONObject();
		    LUType luType = LUTypeFactoryImpl.getInstance().getTypeByName(LU);
		    db(DB_CASS_NAME).execute(String.format(IIDStats, luType.getKeyspaceName()));
		    db(DB_CASS_NAME).execute(String.format(IDFinderStats, luType.getKeyspaceName()));
		    db(DB_CASS_NAME).execute(String.format(luStats, luType.getKeyspaceName()));
		    jsonObject.put(LU, "Tables created successfully");
		    jsonArray.put(jsonObject);
		}
		
		return jsonArray;
	}


	@desc("This WS fetch the topics exists in kafka cluster")
	@webService(path = "", verb = {MethodType.GET}, version = "1", isRaw = true, isCustomPayload = false, produce = {Produce.JSON}, elevatedPermission = true)
	public static Object wsGetKafkaTopics(@param(description="idfinder/Kafka Interface Name If empty then Delta Kafka Producer") String KafkaSource) throws Exception {
		Properties properties = new Properties();
		if (KafkaSource != null && "idfinder".equals(KafkaSource.toLowerCase())) {
		    SubnodeConfiguration properties2 = IifProperties.getInstance().getProp().getSection("finder_kafka_ssl_properties");
		    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IifProperties.getInstance().getKafkaBootstrapServers());
		    if (properties2.getBoolean("SSL_ENABLED", false)) {
		        properties.put("security.protocol", properties2.getString("SECURITY_PROTOCOL"));
		        properties.put("ssl.truststore.location", properties2.getString("TRUSTSTORE_LOCATION"));
		        properties.put("ssl.truststore.password", properties2.getString("TRUSTSTORE_PASSWORD"));
		        properties.put("ssl.keystore.location", properties2.getString("KEYSTORE_LOCATION"));
		        properties.put("ssl.keystore.password", properties2.getString("KEYSTORE_PASSWORD"));
		        properties.put("ssl.key.password", properties2.getString("KEY_PASSWORD"));
		        properties.put("ssl.endpoint.identification.algorithm", properties2.getString("ENDPOINT_IDENTIFICATION_ALGORITHM"));
		        properties.put("ssl.cipher.suites", properties2.getString("SSL_CIPHER_SUITES"));
		        properties.put("ssl.enabled.protocols", properties2.getString("SSL_ENABLED_PROTOCOLS"));
		        properties.put("ssl.truststore.type", properties2.getString("SSL_TRUSTSTORE_TYPE"));
		    }
		} else if (KafkaSource != null && !"".equals(KafkaSource)) {
		    KafkaInterface sourceTopicInterface = (KafkaInterface) InterfacesManager.getInstance().getInterface(KafkaSource);
		    properties.putAll(sourceTopicInterface.getProperties());
		
		} else {
		    DeltaKafkaProducerProperties kafkaCommonConsumerProperties = Configurator.load(DeltaKafkaProducerProperties.class);
		    properties = kafkaCommonConsumerProperties.getProperties();
		}
		
		JSONArray topicsCreationResult = new JSONArray();
		try (AdminClient client = AdminClient.create(properties)) {
		    client.listTopics().names().get().forEach(topicsCreationResult::put);
		}
		return topicsCreationResult;
	}


	@desc("This WS receives a list of topic names with partition and replicate description and build the Kafka topic.")
	@webService(path = "", verb = {MethodType.GET}, version = "1", isRaw = true, isCustomPayload = false, produce = {Produce.JSON}, elevatedPermission = true)
	public static Object wsCrKafkaTopics(@param(description="<TopicName>,<Partition>,<Replicate>|<TopicName2>,<Partition>,<Replicate>") String topicsList, @param(description="idfinder/Kafka Interface Name If empty then Delta Kafka Producer") String KafkaSource) throws Exception {
		Properties properties = new Properties();
		if (KafkaSource != null && "idfinder".equals(KafkaSource.toLowerCase())) {
		    SubnodeConfiguration properties2 = IifProperties.getInstance().getProp().getSection("finder_kafka_ssl_properties");
		    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IifProperties.getInstance().getKafkaBootstrapServers());
		    if (properties2.getBoolean("SSL_ENABLED", false)) {
		        properties.put("security.protocol", properties2.getString("SECURITY_PROTOCOL"));
		        properties.put("ssl.truststore.location", properties2.getString("TRUSTSTORE_LOCATION"));
		        properties.put("ssl.truststore.password", properties2.getString("TRUSTSTORE_PASSWORD"));
		        properties.put("ssl.keystore.location", properties2.getString("KEYSTORE_LOCATION"));
		        properties.put("ssl.keystore.password", properties2.getString("KEYSTORE_PASSWORD"));
		        properties.put("ssl.key.password", properties2.getString("KEY_PASSWORD"));
		        properties.put("ssl.endpoint.identification.algorithm", properties2.getString("ENDPOINT_IDENTIFICATION_ALGORITHM"));
		        properties.put("ssl.cipher.suites", properties2.getString("SSL_CIPHER_SUITES"));
		        properties.put("ssl.enabled.protocols", properties2.getString("SSL_ENABLED_PROTOCOLS"));
		        properties.put("ssl.truststore.type", properties2.getString("SSL_TRUSTSTORE_TYPE"));
		    }
		} else if (KafkaSource != null && !"".equals(KafkaSource)) {
		    KafkaInterface sourceTopicInterface = (KafkaInterface) InterfacesManager.getInstance().getInterface(KafkaSource);
		    properties.putAll(sourceTopicInterface.getProperties());
		
		} else {
		    DeltaKafkaProducerProperties kafkaCommonConsumerProperties = Configurator.load(DeltaKafkaProducerProperties.class);
		    properties = kafkaCommonConsumerProperties.getProperties();
		}
		
		JSONArray topicsCreationResult = new JSONArray();
		try (AdminClient client = AdminClient.create(properties)) {
		    List<NewTopic> newTopics = new ArrayList<>();
		
		    for (String LUTopicDetails : topicsList.split("\\|")) {
		        String[] details = LUTopicDetails.split(",");
		        String topicName = details[0];
		        NewTopic newTopic = new NewTopic(topicName, Integer.parseInt(details[1]), Short.parseShort(details[2]));
		        newTopics.add(newTopic);
		    }
		
		    CreateTopicsResult create = client.createTopics(newTopics);
		    Map<String, KafkaFuture<Void>> createValues = create.values();
		    for (Map.Entry<String, KafkaFuture<Void>> topicCreateResult : createValues.entrySet()) {
		        JSONObject topicCreationResult = new JSONObject();
		        String errorMSG = null;
		        try {
		            topicCreateResult.getValue().get(1000, TimeUnit.MILLISECONDS);
		        } catch (InterruptedException | ExecutionException | TimeoutException e) {
		            errorMSG = e.getMessage();
		            log.error(e);
		        }
		
		        topicCreationResult.put("TopicName", topicCreateResult.getKey());
		        topicCreationResult.put("CreationResult", errorMSG == null);
		        topicCreationResult.put("Error_MSG", errorMSG);
		
		        topicsCreationResult.put(topicCreationResult);
		    }
		}
		return topicsCreationResult;
	}

	@desc("This WS build and produce a GG message based on SQL query.\r\n" +
			"If using update or delete query, the query must contain the table's primary key")
	@webService(path = "", verb = {MethodType.GET}, version = "1", isRaw = true, isCustomPayload = false, produce = {Produce.JSON}, elevatedPermission = true)
	public static Object wsBuildAndPublishGGMessage(@param(description="Mandatory") String sql_stmt, @param(description="Mandatory") String lu_name, @param(description="Mandatory If the source schema and table name exists for more then one LU Table") String custom_lu_table_name, @param(description="If Set To True WS Will Not Send Records To Kafka") Boolean debug, @param(description="If You Wish To Override The Topic Logi") String topic_name, @param(description="If You Wish To Customise The Message Time") String op_ts, @param(description="If You Wish To Customise The pos Value") String pos, @param(description="If You Wish To Customise targetIid To Be sent To IIDFinder") String targetIid, @param(description="If You Wish To Have No Before") Boolean no_before, @param(description="If To Send op_tp as R") Boolean replicate, @param(description="For Use For Lookup Tables Topic Only") String partiKey, @param(description="Can Be ref/lookup Or Left Empty For LU Tables") String tblType) throws Exception {
		clsDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		com.k2view.fabric.parser.statement.Statement sqlStmt;
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
		    sourceTableName = update(IIDJSon, custom_lu_table_name, lu_name, sqlStmt, messageKey, no_before == null ? false : no_before);
		} else if (patternDelete.matcher(sql_stmt).find()) {
		    sourceTableName = delete(IIDJSon, custom_lu_table_name, lu_name, sqlStmt, messageKey);
		}
		
		IIDJSon.put("table", sourceTableName.toUpperCase());
		String topicName = setTopicName(topic_name, tblType, partiKey, sourceTableName);
		
		JSONObject rs = new JSONObject();
		String status = "WS Completed Publishing Message To Kafka With No Errors!";
		if (!debug) {
		    IIDFProducerSingleton.getInstance().send(topicName, messageKey.toString(), IIDJSon.toString());
		} else {
		    status = WSdebug;
		}
		rs.put("Status", status);
		rs.put("TopicName", topicName);
		rs.put("GGMessage", IIDJSon);
		
		return rs;
	}


	@desc("This WS receives a list of LU names with partition and replicate description and build the relevant Kafka delta topic.")
	@webService(path = "", verb = {MethodType.GET}, version = "1", isRaw = true, isCustomPayload = false, produce = {Produce.JSON}, elevatedPermission = true)
	public static Object wsCrKafkaDeltaTopics(@param(description="<LUName>,<Partition>,<Replicate>|<LUName>,<Partition>,<Replicate>") String LUs, @param(description="idfinder/Kafka Interface Name If empty then Delta Kafka Producer") String KafkaSource) throws Exception {
		Properties properties = new Properties();
		if (KafkaSource != null && "idfinder".equals(KafkaSource.toLowerCase())) {
		    SubnodeConfiguration properties2 = IifProperties.getInstance().getProp().getSection("finder_kafka_ssl_properties");
		    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IifProperties.getInstance().getKafkaBootstrapServers());
		    if (properties2.getBoolean("SSL_ENABLED", false)) {
		        properties.put("security.protocol", properties2.getString("SECURITY_PROTOCOL"));
		        properties.put("ssl.truststore.location", properties2.getString("TRUSTSTORE_LOCATION"));
		        properties.put("ssl.truststore.password", properties2.getString("TRUSTSTORE_PASSWORD"));
		        properties.put("ssl.keystore.location", properties2.getString("KEYSTORE_LOCATION"));
		        properties.put("ssl.keystore.password", properties2.getString("KEYSTORE_PASSWORD"));
		        properties.put("ssl.key.password", properties2.getString("KEY_PASSWORD"));
		        properties.put("ssl.endpoint.identification.algorithm", properties2.getString("ENDPOINT_IDENTIFICATION_ALGORITHM"));
		        properties.put("ssl.cipher.suites", properties2.getString("SSL_CIPHER_SUITES"));
		        properties.put("ssl.enabled.protocols", properties2.getString("SSL_ENABLED_PROTOCOLS"));
		        properties.put("ssl.truststore.type", properties2.getString("SSL_TRUSTSTORE_TYPE"));
		    }
		} else if (KafkaSource != null && !"".equals(KafkaSource)) {
		    KafkaInterface sourceTopicInterface = (KafkaInterface) InterfacesManager.getInstance().getInterface(KafkaSource);
		    properties.putAll(sourceTopicInterface.getProperties());
		
		} else {
		    DeltaKafkaProducerProperties kafkaCommonConsumerProperties = Configurator.load(DeltaKafkaProducerProperties.class);
		    properties = kafkaCommonConsumerProperties.getProperties();
		}
		
		JSONArray topicsCreationResult = new JSONArray();
		try (AdminClient client = AdminClient.create(properties)) {
		    List<NewTopic> newTopics = new ArrayList<>();
		
		    for (String LU : LUs.split("\\|")) {
		        String[] details = LU.split(",");
		        String topicName = String.format("Delta_%s%s", ClusterUtil.getClusterId() != null && !"".equals(ClusterUtil.getClusterId()) ? ClusterUtil.getClusterId() + "_" : "cluster_", details[0]);
		        NewTopic newTopic = new NewTopic(topicName, Integer.parseInt(details[1]), Short.parseShort(details[2]));
		        newTopics.add(newTopic);
		    }
		
		    CreateTopicsResult create = client.createTopics(newTopics);
		    Map<String, KafkaFuture<Void>> createValues = create.values();
		    for (Map.Entry<String, KafkaFuture<Void>> topicCreateResult : createValues.entrySet()) {
		        JSONObject topicCreationResult = new JSONObject();
		        String errorMSG = null;
		        try {
		            topicCreateResult.getValue().get(1000, TimeUnit.MILLISECONDS);
		        } catch (InterruptedException | ExecutionException | TimeoutException e) {
		            errorMSG = e.getMessage();
		            log.error(e);
		        }
		
		        topicCreationResult.put("TopicName", topicCreateResult.getKey());
		        topicCreationResult.put("CreationResult", errorMSG == null);
		        topicCreationResult.put("Error_MSG", errorMSG);
		
		        topicsCreationResult.put(topicCreationResult);
		    }
		}
		return topicsCreationResult;
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
    private static String insert(JSONObject IIDJSon, String custom_lu_table_name, String lu_name, com.k2view.fabric.parser.statement.Statement sqlStmt, StringBuilder messageKey) throws Exception {
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

    @SuppressWarnings("unchecked")
    private static String update(JSONObject IIDJSon, String custom_lu_table_name, String lu_name, com.k2view.fabric.parser.statement.Statement sqlStmt, StringBuilder messageKey, boolean no_before) throws Exception {
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

    private static String delete(JSONObject IIDJSon, String custom_lu_table_name, String lu_name, com.k2view.fabric.parser.statement.Statement sqlStmt, StringBuilder messageKey) throws Exception {
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
