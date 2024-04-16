/////////////////////////////////////////////////////////////////////////
// LU Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.lu.PATIENT_LU.Root;

import java.util.*;
import java.sql.*;
import java.math.*;
import java.io.*;
import java.util.stream.Stream;

import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.Globals;
import com.k2view.cdbms.shared.user.UserCode;
import com.k2view.cdbms.sync.*;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.cdbms.shared.logging.LogEntry.*;
import com.k2view.cdbms.func.oracle.OracleToDate;
import com.k2view.cdbms.func.oracle.OracleRownum;
import com.k2view.cdbms.usercode.lu.PATIENT_LU.*;

import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.shared.user.ProductFunctions.*;
import static com.k2view.cdbms.usercode.common.SharedLogic.*;
import static com.k2view.cdbms.usercode.lu.PATIENT_LU.Globals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class Logic extends UserCode {


	@type(RootFunction)
	@out(name = "INVOICE_ID", type = String.class, desc = "")
	@out(name = "PAYMENT_ID", type = String.class, desc = "")
	@out(name = "ISSUED_DATE", type = String.class, desc = "")
	@out(name = "STATUS", type = String.class, desc = "")
	@out(name = "AMOUNT", type = String.class, desc = "")
	public static void fnPopPayment(String INVOICE_ID) throws Exception {
		String sql = "SELECT INVOICE_ID, PAYMENT_ID, ISSUED_DATE, STATUS, AMOUNT FROM PAYMENT where INVOICE_ID = ?";
		Db.Rows rows = db("dbOracle").fetch(sql, INVOICE_ID);
		for (Db.Row row:rows){
			 UserCode.yield(row.cells());
		}
	}

	
	
	
	
}
