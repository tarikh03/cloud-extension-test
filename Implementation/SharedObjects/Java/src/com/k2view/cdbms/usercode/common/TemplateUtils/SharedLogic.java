/////////////////////////////////////////////////////////////////////////
// Project Shared Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common.TemplateUtils;

import java.util.*;
import java.sql.*;
import java.math.*;
import java.io.*;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.user.UserCode;
import com.k2view.cdbms.sync.*;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.logging.LogEntry.*;
import com.k2view.cdbms.func.oracle.OracleToDate;
import com.k2view.cdbms.func.oracle.OracleRownum;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.fabric.common.Util;
import com.k2view.fabric.events.*;
import com.k2view.fabric.fabricdb.datachange.TableDataChange;

import static com.k2view.cdbms.shared.user.ProductFunctions.*;
import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.usercode.common.SharedGlobals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class SharedLogic {


	@out(name = "result", type = String.class, desc = "")
	public static String transform(String templateFile, Object data) throws Exception {
		Handlebars handlebars = new Handlebars();
		
		Template template = handlebars.compileInline(templateFile);
		
		return template.apply(data);
	}


	@out(name = "res", type = Object.class, desc = "")
	public static Object buildTemplateData(String luName, String consumerJobId) throws Exception {
		
		if (luName == null || Util.isEmpty(luName)) {
			log.error("##### buildTemplateData - LU_NAME is null or empty");
		}
		
		if (consumerJobId == null || Util.isEmpty(consumerJobId)) {
			log.error("##### buildTemplateData - CONSUMER_JOB_ID is null or empty");
		}
		
		Map<String, Object> map = new TreeMap<>();
		map.put("LU_NAME", luName);
		map.put("CONSUMER_JOB_ID", consumerJobId);
		
		return map;
	}

	
	
	

}
