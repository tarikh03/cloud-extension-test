/////////////////////////////////////////////////////////////////////////
// Project Shared Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common.JMX;

import java.util.*;
import java.sql.*;

import com.k2view.cdbms.cluster.CassandraClusterSingleton;
import com.k2view.cdbms.cluster.Consistency;
import com.k2view.cdbms.cluster.FabricClusterAPI;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.fabric.common.stats.CustomStats;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.json.JSONObject;
import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class SharedLogic {


	@type(UserJob)
	public static void fnExposeStatsToJMX(String result) {
		result = result.replaceAll("\\\"", "\"");
		JSONObject JMSRS = new JSONObject(result);
		Iterator<String> LUs = JMSRS.keys();
		while (LUs.hasNext()) {
		    String lu = LUs.next();
		    if (JMSRS.has(lu)) {
		        JSONObject luStats = JMSRS.getJSONObject(lu);
		        Iterator<String> luData = luStats.keys();
		        while (luData.hasNext()) {
		            String jsonKey = luData.next();
		            CustomStats.reset("iidf_stats_" + lu, jsonKey);
		            long val;
		            if (luStats.getString(jsonKey).contains(".")) {
		                val = Math.round(Double.parseDouble(luStats.getString(jsonKey)));
		            } else {
		                val = Long.parseLong(luStats.getString(jsonKey));
		            }
		            CustomStats.count("iidf_stats_" + lu, jsonKey, val);
		        }
		    }
		}
	}


	@out(name = "rs", type = String.class, desc = "")
	private static String fnGetLookupsLag(){
		JSONObject lookupsLag = new JSONObject();
		final String clusterName = CassandraClusterSingleton.getInstance().getClusterName();
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IifProperties.getInstance().getKafkaBootstrapServers());
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		String lookupGroupID = "";
		
		try (AdminClient client = AdminClient.create(props);
			 Consumer<Long, String> consumer = new KafkaConsumer<>(props)) {
		    for (String lookupTopic : getTranslationsData("trnLookupTopics").keySet()) {
		        lookupGroupID = "IDfinderGroupId_" + clusterName;
		        long lag = 0;
		        Map<TopicPartition, OffsetAndMetadata> groupOffSet = client.listConsumerGroupOffsets(lookupGroupID).partitionsToOffsetAndMetadata().get();
		        Map<TopicPartition, Long> topicEndOffsets = consumer.endOffsets(groupOffSet.keySet());
		
		        Map<String, Long> topicsLag = new HashMap<>();
		        for(Map.Entry<TopicPartition, Long> topicEndOffset : topicEndOffsets.entrySet()){
					long groupTopicPartitionOffSet = groupOffSet.get(topicEndOffset.getKey()).offset();
				    lag += topicEndOffset.getValue() - groupTopicPartitionOffSet;
		        }
		        lookupsLag.put(lookupTopic, lag);
		    }
		} catch (Exception e) {
		    log.error(String.format("Failed fetching group lag for Lookup Consumer, HOST:%s, Group_ID:%s", props.get("bootstrap.servers"), lookupGroupID), e);
		}
		
		return lookupsLag.toString();
	}


	@type(UserJob)
	public static void fnExposeIIDFStats() throws Exception {
		JSONObject JMSRS = new JSONObject();
		String[] operations = new String[]{"avg_iid_per_sec", "delta_topic_lag", "iidfinder_consumption_rate_sec", "iidfinder_lag", "parser_get_consumption_rate_sec"};
		List<String> opeLst = Arrays.asList(operations);
		String[] luTypes = JMX_STATS_LUS.split(",");
		final String getLuStatsMaxSeq = "SELECT max(job_run_seq) FROM k2view_%s.lu_stats";
		final String getLuStatsInfo = "SELECT operation_name, operation_value, status from k2view_%s.lu_stats where job_run_seq = ?";
		for (String luType : luTypes) {
		    Object maxSeq = db("dbCassandra").fetch(String.format(getLuStatsMaxSeq, luType.trim().toLowerCase())).firstValue();
		    if (maxSeq == null) continue;
		    try (Db.Rows rs = db("dbCassandra").fetch(String.format(getLuStatsInfo, luType.trim().toLowerCase()), maxSeq)) {
		        JSONObject JMSLURS = new JSONObject();
		        for (Db.Row row : rs) {
		            if (row.get("status") != null && row.get("status").toString().equals("completed") && opeLst.contains(row.get("operation_name").toString())) {
		                JMSLURS.put(row.get("operation_name").toString(), row.get("operation_value"));
		            }
		        }
		        JMSRS.put(luType.trim(), JMSLURS);
		    }
		}
		//JMSRS.put("LOOKUPS", fnGetLookupsLag());
		
		FabricClusterAPI.getNodes(null, Consistency.LOCAL_QUORUM, FabricClusterAPI.NodeStatus.ALIVE).forEach(node -> {
		    try {
		        String val = "\"" + JMSRS.toString().replace("\"", "\\\"") + "\"";
		        fabric().execute("startjob USER_JOB NAME='" + getLuType().luName + ".fnExposeStatsToJMX' " + " UID='" + UUID.randomUUID() + "' ARGS='{\"result\":" + val + "}' AFFINITY='" + node.getEffectiveIp() + "'");
		    } catch (SQLException e) {
		        throw new RuntimeException(e);
		    }
		});
	}


}
