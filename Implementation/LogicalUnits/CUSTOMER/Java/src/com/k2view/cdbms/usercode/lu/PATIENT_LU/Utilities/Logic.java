/////////////////////////////////////////////////////////////////////////
// LU Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.lu.PATIENT_LU.Utilities;

import com.k2view.cdbms.finder.api.IidFinderApi;
import com.k2view.cdbms.shared.user.UserCode;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.type;

import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.UserJob;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class Logic extends UserCode {



	@type(UserJob)
	public static void deltaJobsExecutor() throws Exception {
		int partitions = 2;
		
		// parser must be started from userJob, otherwise, exact topic name must be given
		for (int i = 0; i < partitions; i++) {
			String cmd = "startjob PARSER NAME='" + getLuType().luName + ".deltaIid' UID='deltaIid_" + i + "' AFFINITY='finder_delta' ARGS='{\"topic\":\"" + IidFinderApi.getKafkaDeltaTopic(getLuType().luName) + "\"," + "\"partition\":\"" + i + "\"}'";
			log.info(cmd);
		    db("fabric").execute(cmd);
		}
		
		
		//db("fabric").execute("startjob PARSER NAME='" + getLuType().luName + ".deltaIid' UID='deltaIid_0_1' AFFINITY='finder_delta' ARGS='{\"topic\":\"" + IidFinderUtils.getKafkaDeltaTopic(getLuType().luName) + "\"," + "\"partition\":\"0\"}'");
	}

}
