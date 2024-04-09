/////////////////////////////////////////////////////////////////////////
// Project Shared Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common.IIDFTesting;

import java.util.*;
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
import com.k2view.cdbms.usercode.common.IIDFBasicLUTesting;
import com.k2view.fabric.events.*;
import com.k2view.fabric.fabricdb.datachange.TableDataChange;

import static com.k2view.cdbms.shared.user.ProductFunctions.*;
import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class SharedLogic {


	@type(UserJob)
	public static void fnExecuteIIDFBasicTesting(String LUName, String LUI, String testUID, String tableName) throws Exception {
		try {
		    new IIDFBasicLUTesting(LUName, LUI, testUID, ("null".equals(tableName)) ? null : tableName);
		} catch (Exception e) {
		    log.error(e);
		}
	}


}
