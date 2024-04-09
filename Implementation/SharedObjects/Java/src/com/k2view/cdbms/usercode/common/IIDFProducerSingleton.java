package com.k2view.cdbms.usercode.common;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.k2view.fabric.common.Log;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import static com.k2view.cdbms.usercode.common.IIDF.SharedLogic.UserKafkaProperties;

public class IIDFProducerSingleton {
    protected static Log log = Log.a(IIDFProducerSingleton.class);
    private static final IIDFProducerSingleton INSTANCE = new IIDFProducerSingleton();
    private Producer<String, String> producer = null;
    private Properties props = new Properties();
    final private long FIVE_MIN = 300000;
    private long lastCallForCls = System.currentTimeMillis() + this.FIVE_MIN;
	private Map<String, List<Future>> IIDAsyncMessages = new HashMap<>();

    {
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    public static IIDFProducerSingleton getInstance() {
        return INSTANCE;
    }

    private IIDFProducerSingleton() {
        UserKafkaProperties(this.props);
        connect();

    }

    private void connect() {
        if (this.producer == null) {
            final String producerCreated = "Start creating kafka producer to %s";

            log.info(String.format(producerCreated, this.props.get("bootstrap.servers")));
            this.producer = new KafkaProducer<>(this.props);
            log.info("Kafka producer created");
            createWatcher();
        }
    }

    private void close() {
        final String producerClosed = "Kafka producer:%s is closed";
        final String producerClosingStart = "Start shutting down kafka producer:%s";

        if (this.producer != null) {
            log.info(String.format(producerClosingStart, INSTANCE.hashCode()));
            this.producer.close();
            this.producer = null;
            log.info(String.format(producerClosed, INSTANCE.hashCode()));
        }
    }

    @SuppressWarnings("unchecked")
    public void send(String topic, String messageKey, String message) throws ExecutionException, InterruptedException {
        if (this.producer == null) connect();
        this.lastCallForCls = System.currentTimeMillis() + this.FIVE_MIN;

        try {
            this.producer.send(new ProducerRecord(topic, messageKey, message)).get();
        } catch (Exception e) {
            log.error("IIDF: Failed To Send Records To Kafka");
            close();
            throw e;
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    public void sendAsync(String IID, String topic, String messageKey, String message){
        if (this.producer == null) connect();
        this.lastCallForCls = System.currentTimeMillis() + this.FIVE_MIN;

        Future future = this.producer.send(new ProducerRecord(topic, messageKey, message));
        List<Future> futuresList = IIDAsyncMessages.get(IID);
        if (futuresList == null) {
            futuresList = new ArrayList<>();
        }
        futuresList.add(future);
        IIDAsyncMessages.put(IID, futuresList);
    }
	
    private void createWatcher() {
        Runnable watcher = () -> new Thread(new producerWatcher()).start();
        watcher.run();
    }

    @SuppressWarnings({"unused"})
    public boolean analyzeFutures(String IID) {
        AtomicInteger foundFailedMessage = new AtomicInteger(0);
        List<Future> futureList = IIDAsyncMessages.get(IID);
        if (futureList != null) {
            futureList.parallelStream().forEach(future -> {
                if (foundFailedMessage.get() > 0) return;
                try {
                    future.get(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    foundFailedMessage.getAndIncrement();
                    log.error("Kafka Async error!", e);
                }
            });
        }

        IIDAsyncMessages.remove(IID);

        return foundFailedMessage.get() > 0;
    }
	
    class producerWatcher implements Runnable {
        final String watcherCreated = "Producer watcher created for kafka producer:%s";

        @Override
        public void run() {
            log.info(String.format(this.watcherCreated, INSTANCE.hashCode()));
            while (true) {
                if (lastCallForCls < System.currentTimeMillis()) {
                    close();
                    break;
                }

                try {
                    Thread.sleep(FIVE_MIN);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}