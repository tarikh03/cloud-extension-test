/////////////////////////////////////////////////////////////////////////
// Project Shared Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common.Orphan_Job;

import java.sql.*;

import com.k2view.cdbms.finder.refactor.IidfKafkaUtils;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.fabric.common.ClusterUtil;

import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.usercode.common.Get_Job.SharedLogic.fnGetTopParCnt;
import static com.k2view.cdbms.usercode.common.SharedGlobals.DB_CASS_NAME;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class SharedLogic {


	@type(UserJob)
	public static void orphanJobsExecutor() throws Exception {
		final String topicName = IidfKafkaUtils.getKafkaOrphanTopic(getLuType().luName);
		final String startJob = "startjob PARSER NAME='%s.orphanIid' UID='orphanIid%s' AFFINITY='FINDER_ORPHAN' ARGS='{\"topic\":\"%s\",\"partition\":\"%s\"}'";
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


    @type(UserJob)
    public static void fnOrphanJobManager() throws Exception {
        final String topicName = IidfKafkaUtils.getKafkaOrphanTopic(getLuType().luName);
        final String startJob = "startjob USER_JOB name='%s.orphanJobsExecutor'";
        final String k2System = ClusterUtil.getClusterId() == null || "".equals(ClusterUtil.getClusterId()) ? "k2system" : "k2system_" + ClusterUtil.getClusterId();
        final String getRunningJobs = "SELECT count(*) from %s.k2_jobs WHERE type = 'PARSER' and name = '%s.orphanIid' and status = 'IN_PROCESS' ALLOW FILTERING ";
        final Object parserCount = db(DB_CASS_NAME).fetch(String.format(getRunningJobs, k2System, getLuType().luName)).firstValue();

        int partitions = fnGetTopParCnt(topicName);
        if (parserCount == null || Integer.parseInt((parserCount + "")) < partitions) {
            log.info(String.format("fnGetJobManager: Starting Get Job Parser For %s Total Number Of Partitions:%s", topicName, partitions));
            fabric().execute(String.format(startJob, getLuType().luName));
        }
    }


}

