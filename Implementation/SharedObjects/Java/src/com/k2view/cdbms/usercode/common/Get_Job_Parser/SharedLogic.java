/////////////////////////////////////////////////////////////////////////
// Project Shared Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common.Get_Job_Parser;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.sql.*;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.k2view.cdbms.finder.refactor.IidfProcessorUtils;
import com.k2view.cdbms.finder.refactor.IidfTablesUtils;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.user.UserCode;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import org.apache.commons.lang3.exception.ExceptionUtils;

import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class SharedLogic {

    private static final LoadingCache<String, IidTimeStamp> iidsCache = CacheBuilder.newBuilder()
            .maximumSize(Integer.parseInt(getLuType().ludbGlobals.get("DELTA_JOB_MAX_CACHE_SIZE")))
            .build(new CacheLoader<String, IidTimeStamp>() {
                @Override
                public IidTimeStamp load(String iid) {
                    return null;
                }
            });

    private static final String luName = getLuType().luName;
    private static final String deltaTableName = IidfTablesUtils.getDeltaTableName(IidfProcessorUtils.getAlias(luName));
    private static final String query = String.format("select iid from %s.%s where iid=? limit 1", IidfTablesUtils.getKeyspace(), deltaTableName);
    private static final int TEN_MINUTES = 10 * 60 * 1000;
    private static DecimalFormat dcFor = new DecimalFormat("##.##");
    private static final String logFailed = "Insert into %s.get_jobs_failed_iids (iid, failed_time, failure_reason, full_error_msg) values (?,?,?,?)";

    @type(RootFunction)
    @out(name = "out", type = String.class, desc = "")
    public static void k2_MsgParser2(String in) throws Exception {
        Map<String, String> parserArgs = parserParams();
        String parserUID = "deltaIid_" + parserArgs.get("partition");
        final String createTableGetJobsFailedIIDS = "CREATE TABLE if not exists %s.get_jobs_failed_iids (    iid text,    failed_time timestamp,    failure_reason text,    full_error_msg text,    PRIMARY KEY (iid, failed_time))";
        db(DB_CASS_NAME).execute(String.format(createTableGetJobsFailedIIDS, getLuType().getKeyspaceName()));
        Stats stats = new Stats(parserUID);
        stats.insertStats("Started");

        processMessages(msg -> {
            if (msg != null) {
                handleMsg(msg.getKey(), (Long) msg.getValue(), stats, false);
            }
            if (isAborted()) throw new InterruptedException();
            return true;
        });

        stats.insertStats("Stopped");
        UserCode.yield(null);
    }


    @type(RootFunction)
    @out(name = "out", type = void.class, desc = "")
    public static void k2_MsgParserPriority(String in) throws Exception {
        Map<String, String> parserArgs = parserParams();
        String parserUID = "deltaPriorityId_" + parserArgs.get("partition");
        Stats stats = new Stats(parserUID);
        stats.insertStats("Started");

        processMessages(msg -> {
            if (msg != null) {
                handleMsg(msg.getKey(), (Long) msg.getValue(), stats, true);
            }
            if (isAborted()) throw new InterruptedException();
            return true;
        });

        stats.insertStats("Stopped");
        UserCode.yield(null);
    }

    private static void handleMsg(String iid, long timestamp, Stats stats, boolean isPriority) throws InterruptedException, SQLException {
        IidTimeStamp prevIidTs = iidsCache.getIfPresent(iid);
        IidTimeStamp currIidTs = new IidTimeStamp(iid, timestamp);
        try {
            long h = System.currentTimeMillis() - timestamp;

            int delay = Integer.parseInt(getLuType().ludbGlobals.get(isPriority ? "DELTA_PRIORITY_JOB_DELAY_TIME" : "DELTA_JOB_DELAY_TIME"));
            if (h < delay) {
                Thread.sleep(delay - h);
            }

            if (prevIidTs != null) {
                if (prevIidTs.ts - Integer.parseInt(getLuType().ludbGlobals.get(isPriority ? "DELTA_PRIORITY_JOB_PREV_MESSAGE_DIFF_EPSILON_MS" : "DELTA_JOB_PREV_MESSAGE_DIFF_EPSILON_MS")) < currIidTs.ts) {
                    handleNewIID(currIidTs, stats, isPriority);
                }
            } else {
                handleNewIID(currIidTs, stats, isPriority);
            }
        } catch (SQLException ex) {
            log.error(String.format("Failed to handle %s.%s", luName, iid), ex);
            logFailedIID(iid, ex);
        }
    }

    private static void handleNewIID(IidTimeStamp currIidTs, Stats stats, boolean isPriority) throws SQLException, InterruptedException {
        long currentTimeMillis = System.currentTimeMillis();
        Instant start = null;
        try (Db.Rows rs = db(DB_CASS_NAME).fetch(query, currIidTs.iid)) {
            if (rs.firstValue() != null) {
                Thread.sleep(Long.parseLong(getLuType().ludbGlobals.get(isPriority ? "IID_PRIORITY_GET_DELAY" : "IID_GET_DELAY")));
                final Db fabric = fabric();
                stats.setTotal_iid_processed_in_ten_min();
                try {
                    start = Instant.now();
                    fabric.execute("GET ?.?", luName, currIidTs.iid);
                    stats.setTotal_iid_passed();
                } catch (Exception e) {
                    stats.setTotal_iid_failed();
                    throw e;
                }

            }
        } finally {
            if (start != null) {
                stats.setTotal_iid_processed_time_in_ten_min(Duration.between(start, Instant.now()).toMillis());
            }
        }
        // Add to the cache
        iidsCache.put(currIidTs.iid, new IidTimeStamp(currIidTs.iid, currentTimeMillis));
        if (System.currentTimeMillis() > stats.getTenAgo()) {
            stats.insertStats("Running");
        }
    }

    private static class IidTimeStamp {
        String iid;
        Long ts;

        IidTimeStamp(String iid, Long ts) {
            this.iid = iid;
            this.ts = ts;
        }
    }

    private static class Stats {
        long total_iid_failed;
        long total_iid_passed;
        long total_iid_processed;
        long total_iid_processed_in_ten_min;
        long total_iid_processed_time_in_ten_min;
        long tenAgo;
        String host;
        String parserUID;
        final String insOnStart = "Insert into %s.iid_get_rate (node,uid,status,update_time) values (?,?,?,?)";
        final String insOnRunning = "Insert into %s.iid_get_rate (node,uid,iid_avg_run_time_in_sec,status,total_iid_failed,total_iid_passed,total_iid_processed,total_iid_processed_in_ten_min,update_time,avg_iid_sync_time_in_ms) values (?,?,?,?,?,?,?,?,?,?)";
        final String creTableIIDGetRate = "CREATE TABLE if not exists %s.iid_get_rate (    uid text PRIMARY KEY,    iid_avg_run_time_in_sec double,    node text,    status text,    total_iid_failed bigint,    total_iid_passed bigint,    total_iid_processed bigint,    total_iid_processed_in_ten_min bigint,    update_time timestamp, avg_iid_sync_time_in_ms bigint)";

        private Stats(String parserUID) throws UnknownHostException, SQLException {
            this.host = InetAddress.getLocalHost().getHostAddress();
            this.tenAgo = System.currentTimeMillis() + TEN_MINUTES;
            this.parserUID = parserUID;
            createIIDGetRateTable();
        }

        private void createIIDGetRateTable() throws SQLException {
            db(DB_CASS_NAME).execute(String.format(creTableIIDGetRate, getLuType().getKeyspaceName()));
        }

        private long getTotal_iid_processed_time_in_ten_min() {
            return total_iid_processed_time_in_ten_min;
        }

        private long getTotal_iid_failed() {
            return total_iid_failed;
        }

        private long getTotal_iid_passed() {
            return total_iid_passed;
        }

        private long getTotal_iid_processed() {
            return total_iid_processed;
        }

        private long getTtotal_iid_processed_in_ten_min() {
            return total_iid_processed_in_ten_min;
        }

        private long getTenAgo() {
            return tenAgo;
        }

        private void setTotal_iid_processed_time_in_ten_min(long IIDSyncTime) {
            total_iid_processed_time_in_ten_min += IIDSyncTime;
        }

        private void setTotal_iid_failed() {
            total_iid_failed++;
        }

        private void setTotal_iid_passed() {
            total_iid_passed++;
        }

        private void setTotal_iid_processed(long total_iid_processed_in_ten_min) {
            total_iid_processed += total_iid_processed_in_ten_min;
        }

        private void setTotal_iid_processed_in_ten_min() {
            total_iid_processed_in_ten_min++;
        }

        private void insertStats(String status) throws SQLException {
            if ("Started".equals(status)) {
                db(DB_CASS_NAME).execute(String.format(insOnStart, getLuType().getKeyspaceName()), host, this.parserUID, status, new Timestamp(System.currentTimeMillis()));
            } else {
                double avg = (total_iid_processed_in_ten_min / (TEN_MINUTES / 1000.0));
                avg = Double.parseDouble(dcFor.format(avg));
                long avgIIDSyncTime = total_iid_processed_time_in_ten_min / total_iid_processed_in_ten_min;
                setTotal_iid_processed(total_iid_processed_in_ten_min);

                db(DB_CASS_NAME).execute(String.format(insOnRunning, getLuType().getKeyspaceName()), host, this.parserUID, avg, status, total_iid_failed, total_iid_passed, total_iid_processed, total_iid_processed_in_ten_min, new Timestamp(System.currentTimeMillis()), avgIIDSyncTime);

                tenAgo = System.currentTimeMillis() + TEN_MINUTES;
                total_iid_processed_in_ten_min = 0;
                total_iid_processed_time_in_ten_min = 0;
            }
        }
    }

    private static void logFailedIID(String iid, SQLException ex) throws SQLException {
        db(DB_CASS_NAME).execute(String.format(logFailed, getLuType().getKeyspaceName()), iid, new Timestamp(System.currentTimeMillis()), ex.getMessage(), ExceptionUtils.getStackTrace(ex));
    }

}