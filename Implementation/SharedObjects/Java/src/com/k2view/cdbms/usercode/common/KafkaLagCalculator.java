package com.k2view.cdbms.usercode.common;

import java.util.*;
import java.sql.*;
import java.math.*;
import java.io.*;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.sync.*;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.logging.LogEntry.*;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupAuthorizationException;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;

import java.util.*;
import java.util.concurrent.ExecutionException;

public class KafkaLagCalculator {

    private final AdminClient adminClient;

    public KafkaLagCalculator(Properties config) {
        this.adminClient = AdminClient.create(config);
    }

    public void calculateLagAllTopics(String consumerGroupId) throws ExecutionException, InterruptedException {
        try {
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(consumerGroupId);
            Map<TopicPartition, Long> consumerGroupOffsets = new HashMap<>();

            offsetsResult.partitionsToOffsetAndMetadata().get().forEach((tp, om) -> {
                consumerGroupOffsets.put(tp, om.offset());
            });

            Map<TopicPartition, Long> latestOffsets = new HashMap<>();
            for (TopicPartition tp : consumerGroupOffsets.keySet()) {
                latestOffsets.put(tp, adminClient.listOffsets(Collections.singletonMap(tp, OffsetSpec.latest())).all().get().get(tp).offset());
            }

            consumerGroupOffsets.forEach((tp, consumerOffset) -> {
                long latestOffset = latestOffsets.get(tp);
                long lag = latestOffset - consumerOffset;
                System.out.printf("Topic: %s, Partition: %d, Current Offset: %d, Latest Offset: %d, Lag: %d%n", tp.topic(), tp.partition(), consumerOffset, latestOffset, lag);
            });

        } catch (GroupIdNotFoundException | GroupAuthorizationException e) {
            System.out.printf("Consumer group %s not found or not authorized%n", consumerGroupId);
        } finally {
            adminClient.close();
        }
    }

    public void calculateLag(String consumerGroupId, String topic) throws ExecutionException, InterruptedException {
        try {
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(consumerGroupId);
            Map<TopicPartition, Long> consumerGroupOffsets = new HashMap<>();

            offsetsResult.partitionsToOffsetAndMetadata().get().forEach((tp, om) -> {
                if (tp.topic().equals(topic)) {
                    consumerGroupOffsets.put(tp, om.offset());
                }
            });

            if (consumerGroupOffsets.isEmpty()) {
                System.out.printf("No offsets found for topic %s in consumer group %s%n", topic, consumerGroupId);
                return;
            }

            Map<TopicPartition, Long> latestOffsets = new HashMap<>();
            for (TopicPartition tp : consumerGroupOffsets.keySet()) {
                latestOffsets.put(tp, adminClient.listOffsets(Collections.singletonMap(tp, OffsetSpec.latest())).all().get().get(tp).offset());
            }

            consumerGroupOffsets.forEach((tp, consumerOffset) -> {
                long latestOffset = latestOffsets.get(tp);
                long lag = latestOffset - consumerOffset;
                System.out.printf("Topic: %s, Partition: %d, Current Offset: %d, Latest Offset: %d, Lag: %d%n", tp.topic(), tp.partition(), consumerOffset, latestOffset, lag);
            });

        } catch (GroupIdNotFoundException | GroupAuthorizationException e) {
            System.out.printf("Consumer group %s not found or not authorized%n", consumerGroupId);
        } finally {
            adminClient.close();
        }
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Properties config = new Properties();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.01:9093");

        KafkaLagCalculator lagCalculator = new KafkaLagCalculator(config);
        lagCalculator.calculateLagAllTopics("your-consumer-group-id");
    }
}