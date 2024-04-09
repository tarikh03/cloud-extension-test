/////////////////////////////////////////////////////////////////////////
// Project Shared Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common.IID_STATS;

import java.util.*;
import java.sql.*;
import com.k2view.cdbms.cluster.CassandraClusterSingleton;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.cdbms.usercode.common.SendEmail;
import com.k2view.fabric.common.ClusterUtil;
import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class SharedLogic {


	private static void fnInsRecToLUStats(String operation_name, String operation_value, String status, Long jobRunSeq) throws Exception {
		db(DB_CASS_NAME).execute(String.format("Insert into %s.lu_stats (operation_name, operation_value, status, last_update_time, job_run_seq) values (?,?,?,?,?)", getLuType().getKeyspaceName()), operation_name, operation_value, status, new Timestamp(System.currentTimeMillis()), jobRunSeq);
	}

	@type(UserJob)
	public static void fnGetLUStats() throws Exception {
		long jobRunSeq = 1;
		Object rowVal = db(DB_CASS_NAME).fetch(String.format("SELECT max(job_run_seq) from %s.lu_stats", getLuType().getKeyspaceName())).firstValue();
		if (rowVal != null) {
		    jobRunSeq = Long.parseLong(rowVal.toString()) + 1;
		}	
		
		fnGetIIDStats(jobRunSeq);
		fnGetIIDFinderStats(jobRunSeq);
		fnGeLUDeltaTopicInfo(jobRunSeq);
	}


	private static void fnGetIIDStats(Long jobRunSeq) throws Exception {
		double totalAVG = 0;
		long totalAVGSIIDyncTime = 0;
		long totalIIDPassed = 0;
		long totalIIDFailed = 0;
		long cnt = 0;
		try (Db.Rows rs = db(DB_CASS_NAME).fetch("SELECT node, uid, status, iid_avg_run_time_in_sec, total_iid_passed, total_iid_failed, avg_iid_sync_time_in_ms from " + getLuType().getKeyspaceName() + ".iid_get_rate")) {
		    for (Db.Row row : rs) {
		        if ("Running".equalsIgnoreCase(("" + row.cell(2)))) {
		            totalAVG += Double.parseDouble(row.cell(3) + "");
		            totalIIDPassed += Long.parseLong(row.cell(4) + "");
		            totalIIDFailed += Long.parseLong(row.cell(5) + "");
					totalAVGSIIDyncTime += Long.parseLong(row.cell(6) + "");
					cnt++;
		        }
		    }
			
		    fnInsRecToLUStats("delta_job_total_iids_received", (totalIIDPassed + totalIIDFailed) + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("delta_job_total_iids_passed", totalIIDPassed + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("delta_job_total_iids_failed", totalIIDFailed + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("delta_job_avg_iid_processed_per_sec", totalAVG + "", "completed", jobRunSeq);
			long avgIIDSyncTime = (cnt != 0) ? (totalAVGSIIDyncTime/cnt) : 0;
			fnInsRecToLUStats("delta_job_avg_iid_sync_time_in_ms", avgIIDSyncTime + "", "completed", jobRunSeq);
		} catch (Exception e) {
			log.error("Failed updating lu_stats table!", e);
		    fnInsRecToLUStats("delta_job_total_iids_received", "0", "failed", jobRunSeq);
		    fnInsRecToLUStats("delta_job_total_iids_passed", "0", "failed", jobRunSeq);
		    fnInsRecToLUStats("delta_job_total_iids_failed", "0", "failed", jobRunSeq);
		    fnInsRecToLUStats("delta_job_avg_iid_processed_per_sec", "0", "failed", jobRunSeq);
			fnInsRecToLUStats("delta_job_avg_iid_sync_time_in_ms", "0", "failed", jobRunSeq);
		}
	}


	private static void fnGetIIDFinderStats(Long jobRunSeq) throws Exception {
		Set<String> LUTopicsList = null;
		try {
		    LUTopicsList = getTranslationsData("trnLUKafkaTopics").keySet();
		} catch (Exception e) {
		}
		
		final String clusterName = CassandraClusterSingleton.getInstance().getClusterName();
		final String IDFinderGroupID = "IDfinderGroupId_" + clusterName;
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IifProperties.getInstance().getKafkaBootstrapServers());
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		long groupTotalOffSet = 0L;
		long topicsTotalOffSet = 0L;
		long groupTotalOffSet2 = 0L;
		long topicsTotalOffSet2 = 0L;
		long groupConsumptionRate = 0L;
		long topicsInsertRate = 0L;
		long lag = 0;
		
		try (AdminClient client = AdminClient.create(props);
		     Consumer<Long, String> consumer = new KafkaConsumer<>(props)) {
		    for (int i = 0; i < 2; i++) {
		        lag = 0;
		        groupTotalOffSet = 0L;
		        topicsTotalOffSet = 0L;
		        Map<TopicPartition, OffsetAndMetadata> groupOffSet = client.listConsumerGroupOffsets(IDFinderGroupID).partitionsToOffsetAndMetadata().get();
		        Map<TopicPartition, Long> topicEndOffsets = consumer.endOffsets(groupOffSet.keySet());
		
		        for (Map.Entry<TopicPartition, Long> topicEndOffset : topicEndOffsets.entrySet()) {
					if (LUTopicsList != null && !LUTopicsList.contains(topicEndOffset.getKey().topic())) continue;
		            long groupTopicPartitionOffSet = groupOffSet.get(topicEndOffset.getKey()).offset();
		            lag += topicEndOffset.getValue() - groupTopicPartitionOffSet;
		            groupTotalOffSet += groupTopicPartitionOffSet;
		            topicsTotalOffSet += topicEndOffset.getValue();
		        }
		
		        if (i == 0) {
		            groupTotalOffSet2 = groupTotalOffSet;
		            topicsTotalOffSet2 = topicsTotalOffSet;
		            Thread.sleep(5000);
		        } else {
		            groupConsumptionRate = ((groupTotalOffSet - groupTotalOffSet2) / 5);
		            topicsInsertRate = ((topicsTotalOffSet - topicsTotalOffSet2) / 5);
		        }
		    }
		
		    fnInsRecToLUStats("iidfinder_consumption_rate_sec", groupConsumptionRate + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("iidfinder_kafka_inset_rate_sec", topicsInsertRate + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("iidfinder_lag", lag + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("iidfinder_group_offset", groupTotalOffSet + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("iidfinder_topics_offset", topicsTotalOffSet + "", "completed", jobRunSeq);
		} catch (Exception e) {
		    log.error(String.format("Failed fetching group info for IDFinder!, HOST:%s, Group_ID:%s", props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG), IDFinderGroupID), e);
		    fnInsRecToLUStats("iidfinder_consumption_rate_sec", "", "failed to get stats - " + e.getMessage(), jobRunSeq);
		    fnInsRecToLUStats("iidfinder_kafka_inset_rate_sec", "", "failed to get stats - " + e.getMessage(), jobRunSeq);
		    fnInsRecToLUStats("iidfinder_lag", "-1", "failed to get stats - " + e.getMessage(), jobRunSeq);
		    fnInsRecToLUStats("iidfinder_group_offset", "-1", "failed to get stats - " + e.getMessage(), jobRunSeq);
		    fnInsRecToLUStats("iidfinder_topics_offset", "-1", "failed to get stats - " + e.getMessage(), jobRunSeq);
		}
	}


	private static void fnGeLUDeltaTopicInfo(Long jobRunSeq) throws Exception {
		final String topicName = String.format("Delta_%s" + getLuType().luName, ClusterUtil.getClusterId() != null && !"".equals(ClusterUtil.getClusterId()) ? ClusterUtil.getClusterId() + "_" : "cluster_");
		final String clusterName = CassandraClusterSingleton.getInstance().getClusterName();
		final String GroupID = getLuType().ludbGlobals.get("GET_PARSER_GROUP_ID");
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IifProperties.getInstance().getProp().getSection("delta_kafka_producer").getString("BOOTSTRAP_SERVERS"));
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

		long groupTotalOffSet = 0L;
		long topicsTotalOffSet = 0L;
		long groupTotalOffSet2 = 0L;
		long topicsTotalOffSet2 = 0L;
		long groupConsumptionRate = 0L;
		long topicsInsertRate = 0L;
		long lag = 0;
		
		try (AdminClient client = AdminClient.create(props);
		     Consumer<Long, String> consumer = new KafkaConsumer<>(props)) {
		    for (int i = 0; i < 2; i++) {
		        lag = 0;
		        groupTotalOffSet = 0L;
		        topicsTotalOffSet = 0L;
		        Map<TopicPartition, OffsetAndMetadata> groupOffSet = client.listConsumerGroupOffsets(GroupID).partitionsToOffsetAndMetadata().get();
		        Map<TopicPartition, Long> topicEndOffsets = consumer.endOffsets(groupOffSet.keySet());
		
		        for (Map.Entry<TopicPartition, Long> topicEndOffset : topicEndOffsets.entrySet()) {
					if (!topicName.equals(topicEndOffset.getKey().topic())) continue;
		            long groupTopicPartitionOffSet = groupOffSet.get(topicEndOffset.getKey()).offset();
		            lag += topicEndOffset.getValue() - groupTopicPartitionOffSet;
		            groupTotalOffSet += groupTopicPartitionOffSet;
		            topicsTotalOffSet += topicEndOffset.getValue();
		        }
		
		        if (i == 0) {
		            groupTotalOffSet2 = groupTotalOffSet;
		            topicsTotalOffSet2 = topicsTotalOffSet;
		            Thread.sleep(5000);
		        } else {
		            groupConsumptionRate = ((groupTotalOffSet - groupTotalOffSet2) / 5);
		            topicsInsertRate = ((topicsTotalOffSet - topicsTotalOffSet2) / 5);
		        }
		    }
		
		    fnInsRecToLUStats("delta_job__consumption_rate_sec", groupConsumptionRate + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("iidfinder_insert_rate_to_delta_topic_per_sec", topicsInsertRate + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("delta_job_topic_lag", lag + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("delta_job_group_offset", groupTotalOffSet + "", "completed", jobRunSeq);
		    fnInsRecToLUStats("delta_job_topic_offset", topicsTotalOffSet + "", "completed", jobRunSeq);
		
		} catch (Exception e) {
		    log.error(String.format("Failed fetching group info for Delta topic group!, HOST:%s, Group_ID:%s", props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG), GroupID), e);
		    fnInsRecToLUStats("delta_job__consumption_rate_sec", "", "failed to get stats - " + e.getMessage(), jobRunSeq);
		    fnInsRecToLUStats("iidfinder_insert_rate_to_delta_topic_per_sec", "", "failed to get stats - " + e.getMessage(), jobRunSeq);
		    fnInsRecToLUStats("delta_job_topic_lag", "-1", "failed to get stats - " + e.getMessage(), jobRunSeq);
		    fnInsRecToLUStats("delta_job_group_offset", "-1", "failed to get stats - " + e.getMessage(), jobRunSeq);
		    fnInsRecToLUStats("delta_job_topic_offset", "-1", "failed to get stats - " + e.getMessage(), jobRunSeq);
		}
	}
	


	@type(UserJob)
	public static void fnSendLUsStats() throws Exception {
		final String getLuStatsMaxSeq = "SELECT max(job_run_seq) FROM %s.lu_stats";
		final String htmlBody = "<!DOCTYPE html><html><head><style>table {font-family: arial, sans-serif;border-collapse: collapse;width: 100%;}td, th {border: 1px solid #dddddd;text-align: left;padding: 8px;}tr:nth-child(even) {background-color: #dddddd;}</style></head><body><h2 align=\"center\">Location LUs Status</h2>";
		final com.k2view.cdbms.lut.DbInterface interfaceDetails = (com.k2view.cdbms.lut.DbInterface) InterfacesManager.getInstance().getInterface(DB_CASS_NAME);
		String[] luTypes = STATS_EMAIL_REPORT_LUS.split(",");
		Set<String> operationsList = new HashSet<>();
		StringBuilder tableRS = new StringBuilder().append("<table><tr><th>LU</th><th>Operation</th><th>Result</th><th>Update Time</th><th>Status</th></tr>");
		StringBuilder headerAggRs = new StringBuilder().append("<table><tr><th>LU</th>");
		Map<String, Map<String, Object>> statsResult = new HashMap<>();
		operationsList.add("Deleted_Delta_Plus_Orphans");
		operationsList.add("Delta_Table_Count");
		operationsList.add("Orphan_Table_Count");
		
		for (String luType : luTypes) {
			luType = luType.trim();
		    headerAggRs.append("<th>").append(luType).append("</th>");
		    Map<String, Object> statsResultLU = new HashMap<>();
		    String luKeyspace = "k2view_" + luType.toLowerCase();
		    Object rs = db(DB_CASS_NAME).fetch(String.format(getLuStatsMaxSeq, luKeyspace)).firstValue();
		    long jobRunSeq;
		    if (rs == null) {
		        continue;
		    } else {
		        jobRunSeq = Long.parseLong("" + rs);
		    }
		
		    getLUStatistics(jobRunSeq, tableRS, operationsList, statsResultLU, luKeyspace, luType);
		    getIDFinderStats(tableRS, operationsList, statsResultLU, luKeyspace, luType);
		
		    statsResult.put(luType, statsResultLU);
		}
		tableRS.append("</table>");
		headerAggRs.append("</tr>");
		String aggRs = aggResults(statsResult, headerAggRs.toString(), luTypes, operationsList);
		String emailBody = htmlBody + aggRs + "<br><br>" + tableRS.toString() + "</body></html>";
		
		String emailSender = "";
		String emailSenderHost = "";
		int emailSenderPort = 0;
		String emailList = "";
		Map<String, Map<String, String>> emailTrnRs = getTranslationsData("trnEmailDetails");
		for (Map.Entry<String, Map<String, String>> emailTrnRsEnt : emailTrnRs.entrySet()) {
		    Map<String, String> emailDet = emailTrnRsEnt.getValue();
		    emailSender = emailDet.get("EMAIL_SENDER_ADDRESS");
		    emailSenderHost = emailDet.get("EMAIL_SENDER_HOST");
		    emailSenderPort = Integer.parseInt(emailDet.get("EMAIL_SENDER_PORT"));
			emailList = emailDet.get("EMAIL_LISTS");
		}
		SendEmail.sendEmail(emailList, emailSender, emailSenderHost, emailSenderPort, null, null, 0, "Location LUs Status - " + CassandraClusterSingleton.getInstance().getClusterName(), emailBody, null);
	}

    private static void getLUStatistics(long jobRunSeq, StringBuilder tableRS, Set<String> operationsList, Map<String, Object> statsResultLU, String luKeyspace, String luType) throws SQLException {
		final String getLuStatsInfo = "SELECT operation_name, last_update_time, operation_value, status from %s.lu_stats where job_run_seq = ?";
		try (Db.Rows rs1 = db(DB_CASS_NAME).fetch(String.format(getLuStatsInfo, luKeyspace), jobRunSeq)) {
            for (Db.Row row : rs1) {
                if (row.get("operation_name").toString().startsWith("total_")) continue;
                statsResultLU.put(row.get("operation_name").toString(), row.get("operation_value"));
                operationsList.add(row.get("operation_name").toString());
                tableRS.append("<tr><td>").append(luType).append("</td>");
                tableRS.append("<td>").append(row.get("operation_name")).append("</td>");
                tableRS.append("<td>").append(row.get("operation_value")).append("</td>");
                tableRS.append("<td>").append(row.get("last_update_time")).append("</td>");
                tableRS.append("<td>").append(row.get("status")).append("</td></tr>");
            }
        }
    }

    private static void getIDFinderStats(StringBuilder tableRS, Set<String> operationsList, Map<String, Object> statsResultLU, String luKeyspace, String luType) throws SQLException {
		final String getIDFinderStats = "SELECT sum(operation_value) from %s.idfinder_stats WHERE operation_name = ? ALLOW FILTERING";
		String[] operations = new String[]{"IDFInder_Orphan_Count", "IDFInder_Delta_Count", "IDFInder_Deleted_Deltas_Count"};
        long IDFInder_Deleted_Deltas_Count = 0;
        long IDFInder_Orphan_Count = 0;
        for (String operation : operations) {
            Object rs1 = db(DB_CASS_NAME).fetch(String.format(getIDFinderStats, luKeyspace), operation).firstValue();
            if ("IDFInder_Deleted_Deltas_Count".equals(operation)) {
                IDFInder_Deleted_Deltas_Count = Long.parseLong("" + rs1);
            }
            if ("IDFInder_Orphan_Count".equals(operation)) {
                IDFInder_Orphan_Count = Long.parseLong("" + rs1);
            }
            statsResultLU.put(operation, rs1);
            operationsList.add(operation);
            tableRS.append("<tr><td>").append(luType).append("</td>");
            tableRS.append("<td>").append(operation).append("</td>");
            tableRS.append("<td>").append(Long.parseLong(rs1.toString())).append("</td>");
            tableRS.append("<td></td>");
            tableRS.append("<td></td></tr>");
        }
        statsResultLU.put("Deleted_Delta_Plus_Orphans", (IDFInder_Orphan_Count + IDFInder_Deleted_Deltas_Count));
    }

    private static String aggResults(Map<String, Map<String, Object>> statsResult, String headerAggRs, String[] luTypes, Set<String> operationsList) {
        StringBuilder tableRS = new StringBuilder().append(headerAggRs);
        for (String operation : operationsList) {
            tableRS.append("<tr><td>").append(operation).append("</td>");
            for (String luType : luTypes) {
				luType = luType.trim();
                Map<String, Object> luRS = statsResult.get(luType);
                tableRS.append("<td>").append(luRS.get(operation)).append("</td>");
            }
            tableRS.append("</tr>");
        }
        tableRS.append("</table>");

        return tableRS.toString();
    }

}
