/////////////////////////////////////////////////////////////////////////
// Project Web Services
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.lu.k2_ws.Date;

import java.util.*;
import java.sql.*;
import java.math.*;
import java.io.*;
import java.util.Date;

import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.user.WebServiceUserCode;
import com.k2view.cdbms.sync.*;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.cdbms.shared.logging.LogEntry.*;
import com.k2view.cdbms.func.oracle.OracleToDate;
import com.k2view.cdbms.func.oracle.OracleRownum;
import com.k2view.cdbms.usercode.lu.k2_ws.*;
import com.k2view.fabric.api.endpoint.Endpoint.*;
import com.k2view.graphIt.script.Scripter;

import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.shared.user.ProductFunctions.*;
import static com.k2view.cdbms.usercode.common.SharedLogic.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class Logic extends WebServiceUserCode {


	@webService(path = "", verb = {MethodType.GET, MethodType.POST, MethodType.PUT, MethodType.DELETE}, version = "1", isRaw = false, isCustomPayload = false, produce = {Produce.XML, Produce.JSON}, elevatedPermission = false)
	public static void testWS() throws Exception {
		Map<String, Object> graphitParams = new HashMap<>();
		Map<String,Object> response = new LinkedHashMap<>();
		
		String sql = "Select issued_date From invoice Where subscriber_id = 110 Order By invoice_id";
		List<Map<String, Object>> alarmes = new ArrayList<>();
		Map<String, Object> temp = new LinkedHashMap<>();
		Date a = (Date) db("BILLING_DB").fetch(sql).firstValue();
		log.info("a = "+a.getTime());
		
		
		
		
		//graphitParams.put("convertTimeZone",(Scripter.F) p-> {
		//	try {
		//		return convertTimeZone("2016-08-26 13:01:08.000");
		//	} catch (Exception e) {
		//		throw new RuntimeException(e);
		//	}
		//});
	}

	
	

	
}
