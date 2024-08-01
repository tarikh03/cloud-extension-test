package com.k2view.cdbms.usercode.common;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.print.attribute.HashAttributeSet;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupAuthorizationException;
import org.apache.kafka.common.errors.GroupIdNotFoundException;

import java.sql.*;
import java.math.*;
import java.io.*;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.sync.*;
import com.k2view.fabric.common.stats.CustomStats;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.logging.LogEntry.*;
import com.k2view.cdbms.shared.user.UserCode;
import com.k2view.broadway.model.Actor;
import com.k2view.broadway.model.Data;
import com.k2view.fabric.common.stats.Stats;
import com.k2view.fabric.common.stats.StatsType;
import com.k2view.fabric.common.Util;

public class ReadKafkaStatsActor implements Actor {
    private AdminClient adminClient;

    public ReadKafkaStatsActor() {
    }

    public void action(Data input, Data output) throws ExecutionException, InterruptedException {
        String bootstrap = input.get("bootstrap")+"";
        String topics = input.get("topics") == null ? null:input.get("topics")+"";
        String groupId = input.get("groupId")+"";
        //Map of Group->Topic->Partition->Operation->Results
        Map<String,Map<String,Map<String,Map<String,Map<String,Object>>>>> statsMap = new HashMap<>();
        
        final List<String> topicsList;
        List<Map<String,String>> lagsArrayOut = new ArrayList<>() ;

        statsMap.put(groupId,new HashMap<>());

        if(topics != null){
            topicsList = Arrays.asList(topics.trim().split(","));
        } else {
            topicsList = null;
        }

        boolean isIssl = false;//TODO test

        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        
        //TODO
        if(isIssl){
            // SSL configuration
            config.put("security.protocol", "SSL");
            config.put("ssl.truststore.location", "/path/to/truststore.jks");
            config.put("ssl.truststore.password", "your-truststore-password");
            config.put("ssl.keystore.location", "/path/to/keystore.jks");
            config.put("ssl.keystore.password", "your-keystore-password");
            config.put("ssl.key.password", "your-key-password");
        }

        adminClient = AdminClient.create(config);
        final Map<String, Long> totalLagHolder = new HashMap<>();

        try {
            //totalLagHolder = new HashMap<>();
            totalLagHolder.put("totalLag",0L);
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, Long> consumerGroupOffsets = new HashMap<>();

            offsetsResult.partitionsToOffsetAndMetadata().get().forEach((tp, om) -> {
                if(topicsList == null
                    || (topicsList != null && topicsList.contains(tp.topic()))){
                    consumerGroupOffsets.put(tp, om.offset());
                } 
            });

            if (consumerGroupOffsets.isEmpty()) {
                UserCode.log.info(String.format("No offsets found for topic %s in consumer group %s", topics, groupId));
                return;
            }

            Map<TopicPartition, Long> latestOffsets = new HashMap<>();
            for (TopicPartition tp : consumerGroupOffsets.keySet()) {
                latestOffsets.put(tp, adminClient.listOffsets(Collections.singletonMap(tp, OffsetSpec.latest())).all().get().get(tp).offset());
            }

            consumerGroupOffsets.forEach((tp, consumerOffset) -> {
                long latestOffset = latestOffsets.get(tp);
                long lag = latestOffset - consumerOffset;

                updateStats(statsMap, groupId, tp.topic(), String.valueOf(tp.partition()), OPERATION.LAG, lag);
                updateStats(statsMap, groupId, tp.topic(), String.valueOf(tp.partition()), OPERATION.CURRENT_OFFSET, consumerOffset);
                updateStats(statsMap, groupId, tp.topic(), String.valueOf(tp.partition()), OPERATION.OFFSET, latestOffset);

                UserCode.log.info(String.format("Topic: %s, Partition: %d, Current Offset: %d, Latest Offset: %d, Lag: %d", tp.topic(), tp.partition(), consumerOffset, latestOffset, lag));
            });

        } catch (GroupIdNotFoundException | GroupAuthorizationException e) {
            UserCode.log.info(String.format("Consumer group %s not found or not authorized", groupId));
        } finally {
            adminClient.close();
        }

        Long lagPerGroup = 0L;

        Map<String,Long> lagPerTopic = new HashMap<>();
    
        long currentTS = System.currentTimeMillis();
        Map<String, Long> tmpStatsMap;
        long secondsElapsed = 0;

        for(Map.Entry<String, Map<String,Map<String,Map<String,Map<String,Object>>>>> groupEntry:statsMap.entrySet()){
            for(Map.Entry<String, Map<String,Map<String,Map<String,Object>>>> topicEntry:groupEntry.getValue().entrySet()){
                if(!lagPerTopic.containsKey(topicEntry.getKey())){
                    lagPerTopic.put(topicEntry.getKey(), 0L);
                }

                for(Map.Entry<String, Map<String,Map<String,Object>>> partitionEntry:topicEntry.getValue().entrySet()){
                    for(Map.Entry<String, Map<String,Object>> operEntry:partitionEntry.getValue().entrySet()){
                        if(operEntry.getKey().equals(OPERATION.LAG.toString())) {
                            lagPerTopic.put(topicEntry.getKey(),lagPerTopic.get(topicEntry.getKey()) + Long.valueOf(operEntry.getValue().get("value").toString()));
                            lagPerGroup = lagPerGroup + Long.valueOf(operEntry.getValue().get("value").toString());
                            
                            CustomStats.reset("CONSUMER_GROUP_TOPIC_PARTITION", groupId+"_"+topicEntry.getKey()+"_"+partitionEntry.getKey()+"_"+OPERATION.LAG.toString());
                            CustomStats.count("CONSUMER_GROUP_TOPIC_PARTITION", groupId+"_"+topicEntry.getKey()+"_"+partitionEntry.getKey()+"_"+OPERATION.LAG.toString(), Long.valueOf(operEntry.getValue().get("value").toString()));
                            
                        } else if(operEntry.getKey().equals(OPERATION.CURRENT_OFFSET.toString())){
                            tmpStatsMap = readCustomStats("CONSUMER_GROUP_TOPIC_PARTITION",groupId+"_"+topicEntry.getKey()+"_"+partitionEntry.getKey()+"_"+operEntry.getKey());

                            CustomStats.reset("CONSUMER_GROUP_TOPIC_PARTITION", groupId+"_"+topicEntry.getKey()+"_"+partitionEntry.getKey()+"_"+operEntry.getKey());
                            CustomStats.count("CONSUMER_GROUP_TOPIC_PARTITION", groupId+"_"+topicEntry.getKey()+"_"+partitionEntry.getKey()+"_"+operEntry.getKey(), Long.valueOf(operEntry.getValue().get("value").toString()));
                        
                            if(!tmpStatsMap.isEmpty()){
                                secondsElapsed = TimeUnit.MILLISECONDS.toSeconds(currentTS-tmpStatsMap.get("timestamp"));
                                CustomStats.reset("CONSUMER_GROUP_TOPIC_PARTITION", groupId+"_"+topicEntry.getKey()+"_"+partitionEntry.getKey()+"_"+OPERATION.CONSUMPTION_RATE_PER_SEC.toString());
                                CustomStats.count("CONSUMER_GROUP_TOPIC_PARTITION", groupId+"_"+topicEntry.getKey()+"_"+partitionEntry.getKey()+"_"+OPERATION.CONSUMPTION_RATE_PER_SEC.toString(), (Long.valueOf(operEntry.getValue().get("value").toString())-tmpStatsMap.get("last"))/secondsElapsed);
                            }
                        } else if(operEntry.getKey().equals(OPERATION.OFFSET.toString())){
                            CustomStats.reset("CONSUMER_GROUP_TOPIC_PARTITION", groupId+"_"+topicEntry.getKey()+"_"+partitionEntry.getKey()+"_"+operEntry.getKey());
                            CustomStats.count("CONSUMER_GROUP_TOPIC_PARTITION", groupId+"_"+topicEntry.getKey()+"_"+partitionEntry.getKey()+"_"+operEntry.getKey(), Long.valueOf(operEntry.getValue().get("value").toString()));
                        }
                    }
                }
            }
        }

        tmpStatsMap = readCustomStats("CONSUMER_GROUP",groupId+"_"+OPERATION.LAG.toString());
        CustomStats.reset("CONSUMER_GROUP", groupId+"_"+OPERATION.LAG.toString());
        CustomStats.count("CONSUMER_GROUP", groupId+"_"+OPERATION.LAG.toString(), lagPerGroup);

        if(!tmpStatsMap.isEmpty()){
            secondsElapsed = TimeUnit.MILLISECONDS.toSeconds(currentTS-tmpStatsMap.get("timestamp"));
            CustomStats.reset("CONSUMER_GROUP", groupId+"_"+OPERATION.CONSUMPTION_RATE_PER_SEC.toString());
            CustomStats.count("CONSUMER_GROUP", groupId+"_"+OPERATION.CONSUMPTION_RATE_PER_SEC.toString(), (lagPerGroup-tmpStatsMap.get("last"))/secondsElapsed);
    
        }

        for(Map.Entry<String, Long> entry : lagPerTopic.entrySet()){
            tmpStatsMap = readCustomStats("CONSUMER_GROUP_TOPIC",groupId+"_"+entry.getKey()+"_"+OPERATION.LAG.toString());
            CustomStats.reset("CONSUMER_GROUP_TOPIC",groupId+"_"+entry.getKey()+"_"+OPERATION.LAG.toString());
            CustomStats.count("CONSUMER_GROUP_TOPIC",groupId+"_"+entry.getKey()+"_"+OPERATION.LAG.toString(), entry.getValue());
            if(!tmpStatsMap.isEmpty()){
                secondsElapsed = TimeUnit.MILLISECONDS.toSeconds(currentTS-tmpStatsMap.get("timestamp"));
                CustomStats.reset("CONSUMER_GROUP_TOPIC", groupId+"_"+entry.getKey()+"_"+OPERATION.CONSUMPTION_RATE_PER_SEC.toString());
                CustomStats.count("CONSUMER_GROUP_TOPIC", groupId+"_"+entry.getKey()+"_"+OPERATION.CONSUMPTION_RATE_PER_SEC.toString(), (entry.getValue()-tmpStatsMap.get("last"))/secondsElapsed);
            }
        }

        UserCode.log.info(String.format("lagPerGroup = %s",lagPerGroup));
        UserCode.log.info(String.format("lagPerTopic = %s",lagPerTopic));

        output.put(new Object[]{"lagsArr", lagsArrayOut});
    }

    private void updateStats(Map<String,Map<String,Map<String,Map<String,Map<String,Object>>>>> map, String groupId, String topic, String partition, OPERATION operation, long val){
        String oper = operation.toString();
        if(!map.get(groupId).containsKey(topic)){
            map.get(groupId).put(topic, new HashMap<>());
        }

        if(!map.get(groupId).get(topic).containsKey(partition)){
            map.get(groupId).get(topic).put(partition, new HashMap<>());
        }

        if(!map.get(groupId).get(topic).get(partition).containsKey(oper)){
            map.get(groupId).get(topic).get(partition).put(oper, new HashMap<>());
        }

        map.get(groupId).get(topic).get(partition).get(oper).put("operation",oper);
        map.get(groupId).get(topic).get(partition).get(oper).put("value",val);
    }

    private Map<String,Long> readCustomStats(String statsTitle, String key){
        StatsType[] var5 = StatsType.values();
        int var6 = var5.length;
        Map<String,Long> statsMap = new HashMap<>();
        Stats stats =null;
        /*stats = (Stats)Util.parseEnum(Stats.class, "CONSUMER_GROUP");
        for(int var7 = 0; var7 < var6; ++var7) {
            StatsType st = var5[var7];
            UserCode.log.info(String.format("st.name() = %s, value = %s",st.name(),stats.value(st, key)));
            statsMap.put(st.name(), stats.value(st, key));
        }*/
        
        try{
            stats = (Stats)Util.parseEnum(Stats.class, statsTitle);
        } catch(RuntimeException e){
            if(!e.getMessage().contains("is not a valid Stats")){
                throw e;
            } else {
                //UserCode.log.info("readCustomStats: Exception = " + e.getMessage());
                //ignore when stats are not created, it will fail to read them.
                //next run it will be able to read them.
            }
        }

        if(stats==null){
            return statsMap;
        }

        for(int var7 = 0; var7 < var6; ++var7) {
            StatsType st = var5[var7];
            statsMap.put(st.name(), stats.value(st, key));
        }

        return statsMap;
 
    }

    public enum OPERATION {
        LAG, CURRENT_OFFSET, OFFSET, CONSUMPTION_RATE_PER_SEC
    }

    public void close() {
        if(adminClient != null){
            adminClient.close();
        }
    }

}

