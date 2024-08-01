/////////////////////////////////////////////////////////////////////////
// Project Shared Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common.Jobs;

import java.util.*;

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
import com.k2view.cdbms.shared.user.UserCode;
import com.k2view.cdbms.sync.*;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.logging.LogEntry.*;
import com.k2view.cdbms.func.oracle.OracleToDate;
import com.k2view.cdbms.func.oracle.OracleRownum;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.fabric.events.*;
import com.k2view.fabric.fabricdb.datachange.TableDataChange;

import static com.k2view.cdbms.shared.user.ProductFunctions.*;
import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class SharedLogic {

	

@desc("")
@out(name = "", type = String.class, desc = "")
@type(UserJob)
public static void printLags(@desc("") String param1) throws Exception {
    AdminClient adminClient;
    String consumerGroupId = "myFirstGroup";
    Properties config = new Properties();
    config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.01:9093");
    
    adminClient = AdminClient.create(config);

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
            log.info(String.format("Topic: %s, Partition: %d, Current Offset: %d, Latest Offset: %d, Lag: %d%n", tp.topic(), tp.partition(), consumerOffset, latestOffset, lag));
        });

    } catch (GroupIdNotFoundException | GroupAuthorizationException e) {
        log.info(String.format("Consumer group %s not found or not authorized%n", consumerGroupId));
    } finally {
        adminClient.close();
    }
}


}
