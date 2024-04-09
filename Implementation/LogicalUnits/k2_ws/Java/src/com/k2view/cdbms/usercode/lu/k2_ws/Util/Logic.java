/////////////////////////////////////////////////////////////////////////
// Project Web Services
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.lu.k2_ws.Util;

import com.k2view.cdbms.shared.user.WebServiceUserCode;
import com.k2view.fabric.api.endpoint.Endpoint.*;
import java.util.*;
import java.sql.*;
import java.math.*;
import java.io.*;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.sync.*;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.cdbms.shared.logging.LogEntry.*;
import com.k2view.cdbms.func.oracle.OracleToDate;
import com.k2view.cdbms.func.oracle.OracleRownum;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.shared.user.ProductFunctions.*;
import static com.k2view.cdbms.usercode.common.SharedLogic.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class Logic extends WebServiceUserCode {


	@webService(path = "", verb = {MethodType.GET, MethodType.POST, MethodType.PUT, MethodType.DELETE}, version = "1", isRaw = false, isCustomPayload = false, produce = {Produce.XML, Produce.JSON}, elevatedPermission = false)
	public static Object test() throws Exception {
		return graphit("test.graphit");
	}


	@webService(path = "", verb = {MethodType.GET, MethodType.POST, MethodType.PUT, MethodType.DELETE}, version = "1", isRaw = false, isCustomPayload = false, produce = {Produce.XML, Produce.JSON}, elevatedPermission = false)
	public static void test2() throws Exception {
		log.info("starting");
		Thread.sleep(1000);
		log.info("after sleep");
		log.info("sleeping again for 1 seconds");
		Thread.sleep(1000);
		log.info("done ");
	}

	
	

	
}
