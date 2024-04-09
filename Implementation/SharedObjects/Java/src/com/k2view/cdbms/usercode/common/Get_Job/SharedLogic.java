/////////////////////////////////////////////////////////////////////////
// Project Shared Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common.Get_Job;

import java.util.*;
import java.sql.*;
import java.util.Date;

import com.k2view.cdbms.cluster.CassandraClusterSingleton;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.fabric.common.ClusterUtil;
import com.k2view.fabric.common.ini.Configurator;
import com.k2view.fabric.commonArea.producer.pubsub.KafkaAdminProperties;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.json.JSONObject;

import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.DB_CASS_NAME;


@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class SharedLogic {


    @type(UserJob)
    public static void deltaJobsExecutor() throws Exception {
		final String topicName = String.format("Delta_%s%s", ClusterUtil.getClusterId() != null && !"".equals(ClusterUtil.getClusterId()) ? ClusterUtil.getClusterId() + "_" : "cluster_", getLuType().luName);
        final String startJob = "startjob PARSER NAME='%s.deltaIid' UID='deltaIid_%s' AFFINITY='FINDER_DELTA' ARGS='{\"topic\":\"%s\",\"partition\":\"%s\"}'";
        int partitions = fnGetTopParCnt(topicName);
        for (int i = 0; i < partitions; i++) {
            try {
                fabric().execute(String.format(startJob, getLuType().luName, i, topicName, i));
            } catch (SQLException e) {
                if (!e.getMessage().contains("Job is running")) {
                    throw e;
                }
            }
        }
    }


    @out(name = "result", type = Integer.class, desc = "")
    public static Integer fnGetTopParCnt(String topicName) {
        final KafkaAdminProperties kafkaCommon = Configurator.load(KafkaAdminProperties.class);
        Producer<String, JSONObject> producer = null;
        try {
            Properties props = kafkaCommon.getProperties();
            if (!props.contains("key.serializer")) {
                props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            }
            if (!props.contains("value.serializer")) {
                props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            }
            producer = new KafkaProducer<>(props);
            return producer.partitionsFor(topicName).size();
        } finally {
            if (producer != null) producer.close();
        }
    }

	@type(UserJob)
	public static void fnGetJobManager() throws Exception {
		if (Boolean.parseBoolean(getLuType().ludbGlobals.get("GET_JOBS_IND"))) {
		    final String topicName = String.format("Delta_%s%s", ClusterUtil.getClusterId() != null && !"".equals(ClusterUtil.getClusterId()) ? ClusterUtil.getClusterId() + "_" : "cluster_", getLuType().luName);
		    final int to = Integer.parseInt(getLuType().ludbGlobals.get("GET_JOB_START_TIME") + "");
		    final int from = Integer.parseInt(getLuType().ludbGlobals.get("GET_JOB_STOP_TIME") + "");
		    final String startJob = "startjob USER_JOB name='%s.deltaJobsExecutor'";
		    final String stopParser = "stopparser %s deltaIid";
		    final String k2System = ClusterUtil.getClusterId() == null || "".equals(ClusterUtil.getClusterId()) ? "k2system" : "k2system_" + ClusterUtil.getClusterId();
		    final String getRunningJobs = "SELECT count(*) from %s.k2_jobs WHERE type = 'PARSER' and name = '%s.deltaIid' and status = 'IN_PROCESS' ALLOW FILTERING ";
		    final Object parserCount = db(DB_CASS_NAME).fetch(String.format(getRunningJobs, k2System, getLuType().luName)).firstValue();
		
		    Date date = new Date();
		    Calendar c = Calendar.getInstance();
		    c.setTime(date);
		    int t = c.get(Calendar.HOUR_OF_DAY) * 100 + c.get(Calendar.MINUTE);
		    boolean isBetween = to > from && t >= from && t <= to || to < from && (t >= from || t <= to);
		
		    long lag = getGroupLag();
		    log.info(String.format("fnGetJobManager: LU: %s Current Lag: %s Time Validation Result: %s", getLuType().luName, lag, isBetween));
		    if (isBetween || lag > Long.parseLong(getLuType().ludbGlobals.get("LAG_THRESHOLD"))) {
		        log.info(String.format("fnGetJobManager: Stopping Get Job Parser For %s", topicName));
		        if (parserCount != null && Integer.parseInt((parserCount + "")) > 1) {
		            fabric().execute(String.format(stopParser, getLuType().luName));
		        }
		    } else {
		        int partitions = fnGetTopParCnt(topicName);
		        log.info(String.format("fnGetJobManager: Starting Get Job Parser For %s Total Number Of Partitions:%s", topicName, partitions));
		        if (parserCount == null || Integer.parseInt((parserCount + "")) < partitions) {
		            fabric().execute(String.format(startJob, getLuType().luName));
		        }
		    }
		}
	}

	private static long getGroupLag() {
		Set<String> LUTopicsList = null;
		try {
		    LUTopicsList = getTranslationsData("trnLUKafkaTopics").keySet();
		} catch (Exception e) {
		}
		
		final String clusterName = CassandraClusterSingleton.getInstance().getClusterName();
		final String IDFinderGroupID = "IDfinderGroupId_" + clusterName;
		long lag = 0;
		Map<String, Object> props = new HashMap<>();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, IifProperties.getInstance().getKafkaBootstrapServers());
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
		
		try (AdminClient client = AdminClient.create(props);
             Consumer<Long, String> consumer = new KafkaConsumer<>(props)) {
		    Map<TopicPartition, OffsetAndMetadata> groupOffSet = client.listConsumerGroupOffsets(IDFinderGroupID).partitionsToOffsetAndMetadata().get();
		    Map<TopicPartition, Long> topicEndOffsets = consumer.endOffsets(groupOffSet.keySet());
		
		    for (Map.Entry<TopicPartition, Long> topicEndOffset : topicEndOffsets.entrySet()) {
		        if (LUTopicsList != null && !LUTopicsList.contains(topicEndOffset.getKey().topic())) continue;
		        long groupTopicPartitionOffSet = groupOffSet.get(topicEndOffset.getKey()).offset();
		        lag += topicEndOffset.getValue() - groupTopicPartitionOffSet;
		    }
		} catch (Exception e) {
		    log.error(String.format("Failed fetching group lag for IDFinder!, HOST:%s, Group_ID:%s", props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG), IDFinderGroupID), e);
		}
		
		return lag;
	}

	@type(UserJob)
	public static void fnGetJobManagerDeltaPriority() throws Exception {
		if(!(IifProperties.DeltaType.KAFKA.name()).equals(com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("DELTA_STORAGE"))){
			log.warn("Job running on incorrect mode {} , check IifConfig",com.k2view.cdbms.shared.IifProperties.getInstance().getProp().getString("DELTA_STORAGE"));
			return;
		}

		if (Boolean.parseBoolean(getLuType().ludbGlobals.get("GET_PRIORITY_JOBS_IND") + "")) {
			final String topicName = "DeltaPriority_cluster_" + getLuType().luName.toUpperCase();
			final int to = Integer.parseInt(getLuType().ludbGlobals.get("GET_PRIOIRTY_JOB_START_TIME") + "");
			final int from = Integer.parseInt(getLuType().ludbGlobals.get("GET_PRIOIRTY_JOB_STOP_TIME") + "");
			Date date = new Date();
			Calendar c = Calendar.getInstance();
			c.setTime(date);
			int t = c.get(Calendar.HOUR_OF_DAY) * 100 + c.get(Calendar.MINUTE);
			boolean isBetween = to > from && t >= from && t <= to || to < from && (t >= from || t <= to);

			Object parserCount = null;
			try (Db.Rows rs = db("dbCassandra").fetch("SELECT count(*) from k2system.k2_jobs WHERE type = 'PARSER' and name = '" + getLuType().luName + ".deltaPriorityId' and status = 'IN_PROCESS' ALLOW FILTERING ")) {
				parserCount = rs.firstValue();
			}
			//long lag = 0;
			//    long lag = (Long)getGroupLag();
			log.info(String.format("fnGetJobManagerDeltaPriority: Running LU: %s Time Validation Result: %s", getLuType().luName, isBetween));
			//    if (isBetween || lag > Long.parseLong(getLuType().ludbGlobals.get("LAG_THRESHOLD") + "")) {
			if (isBetween) {
				log.info(String.format("fnGetJobManagerDeltaPriority: Stopping Get Job Parser For %s", topicName));
				if (parserCount != null && Integer.parseInt((parserCount + "")) > 1) {
					fabric().execute("stopparser " + getLuType().luName + " deltaPriorityId");
				}
			} else {
				int partitions = fnGetTopParCnt(topicName);
				log.info(String.format("fnGetJobManagerDeltaPriority: Starting Get Job Parser For %s Total Number Of Partitions:%s", topicName, partitions));
				if (parserCount == null || Integer.parseInt((parserCount + "")) < partitions) {
					fabric().execute("startjob USER_JOB name='" + getLuType().luName + ".deltaPriorityJobsExecutor'");
				}
			}
		}
	}

	@type(UserJob)
	public static void deltaPriorityJobsExecutor() throws Exception {
		final String topicName = String.format("DeltaPriority_%s%s", ClusterUtil.getClusterId() != null && !"".equals(ClusterUtil.getClusterId()) ? ClusterUtil.getClusterId() + "_" : "cluster_", getLuType().luName);
		final String startJob = "startjob PARSER NAME='%s.deltaPriorityId' UID='deltaPriorityId_%s' AFFINITY='FINDER_PRIORITY_DELTA_%s' ARGS='{\"topic\":\"%s\",\"partition\":\"%s\"}'";
		int partitions = fnGetTopParCnt(topicName);
		for (int i = 0; i < partitions; i++) {
			try {
				fabric().execute(String.format(startJob, getLuType().luName, i,getLuType().luName,topicName, i));
			} catch (SQLException e) {
				if (!e.getMessage().contains("Job is running")) {
					throw e;
				}
			}
		}
	}


}
