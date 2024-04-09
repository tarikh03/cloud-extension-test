package k2Studio.usershared;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.internals.NoOpConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import com.k2view.fabric.common.Log;
import com.k2view.cdbms.shared.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.sql.*;

import static com.k2view.cdbms.usercode.common.IIDF.SharedLogic.UserKafkaConsumProperties;

public abstract class AbstractKafkaConsumer<T> {

    protected static Log log = Log.a(AbstractKafkaConsumer.class);
    private KafkaConsumer<String, T> consumer;
    private String topicName;
    private String groupId;
    private boolean isPolling = true;
    private volatile boolean errorOccured = false;
    private int delayOnFailureMs;
    private int maxRetry;
    private int repeatedErrors;
    private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Properties props = new Properties();
	
    public AbstractKafkaConsumer(String groupId, String topicName) throws Exception {
        this.groupId = groupId;
        this.topicName = topicName;
        this.delayOnFailureMs = 100;//IifProperties.getInstance().getDelayOnPollFailureMs();
        this.maxRetry = 3;//IifProperties.getInstance().getMaxRetryOnPollFailure();
        initProps();
        this.consumer = new KafkaConsumer<>(this.props);
    }

    private void initProps() throws Exception {
        this.props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        this.props.put("value.deserializer", getDeserializer());
        UserKafkaConsumProperties(this.props, this.groupId);
    }

    public void poll() throws InterruptedException {
        this.consumer.subscribe(Pattern.compile(this.topicName), new NoOpConsumerRebalanceListener());

        try {
            while (isPolling()) {
                ConsumerRecords<String, T> records = consumer.poll(Duration.ofSeconds(100));
                errorOccured = false;
                T currRec = null;
                try {
                    for (ConsumerRecord<String, T> record : records) {
                        currRec = record.value();
                        processValue(record.key(), currRec);                      
                    }
                } catch (Exception e) {
                    log.error("Lookup Consumer Failed To Process Message - " + currRec.toString());
                    log.error("AbstractKafkaConsumer", e);
                    errorOccured = true;
                }

                if (!errorOccured) {
                    consumer.commitSync();
                    if (!records.isEmpty()) this.repeatedErrors = 0;
                    
                } else {
                    this.repeatedErrors++;
                    if (this.repeatedErrors >= this.maxRetry) {
                        log.warn(String.format("AbstractKafkaConsumer: Kafka poll failed and exceeded the max retry of '%d' retries, AbstractKafkaConsumer: will be terminated", maxRetry));
                        throw new InterruptedException("AbstractKafkaConsumer: execution will be terminated");
                    }
                    log.warn(String.format("Kafka poll failed '%d/%d' retries", repeatedErrors, maxRetry));
                    seekToFirstOffset(records);
                    try {
                        Thread.sleep(delayOnFailureMs);
                    } catch (InterruptedException e) {
                        throw e;
                    }
                }
            }
        } finally {
            consumer.unsubscribe();
            log.warn("AbstractKafkaConsumer: Stopped polling");
			if(consumer != null)consumer.close();
        }
    }

    protected void seekToFirstOffset(ConsumerRecords<String, T> records) {
        for (TopicPartition partition : records.partitions()) {
            List<ConsumerRecord<String, T>> partitionRecords = records.records(partition);
            long firstOffset = partitionRecords.get(0).offset();
            consumer.seek(partition, firstOffset);
        }
    }

    public void stopPolling() {
        setPolling(false);
    }

    public boolean isPolling() {
        return isPolling;
    }

    public void setPolling(boolean isPolling) {
        this.isPolling = isPolling;
    }


    protected abstract void processValue(String key, T value) throws Exception;

    protected abstract String getDeserializer();

    @FunctionalInterface
    public interface DbExecute {
        boolean exec(String interfaceName, String sql, Object[] valuesForPreparedStatement) throws SQLException;
    }

    @FunctionalInterface
    public interface DBQuery {
        ResultSetWrapper exec(String interfaceName, String sql, Object[] valuesForPreparedStatement) throws SQLException;
    }
	
	@FunctionalInterface 
    public interface DBSelectValue { 
        Object exec(String interfaceName, String sql, Object[] valuesForPreparedStatement) throws SQLException; 
    }

}

