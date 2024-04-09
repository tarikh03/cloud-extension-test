/////////////////////////////////////////////////////////////////////////
// LU Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.lu.reg_test.Utilities;

import java.util.*;
import java.sql.*;
import java.math.*;
import java.io.*;

import com.google.gson.JsonArray;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.Globals;
import com.k2view.cdbms.shared.user.UserCode;
import com.k2view.cdbms.sync.*;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.cdbms.shared.logging.LogEntry.*;
import com.k2view.cdbms.func.oracle.OracleToDate;
import com.k2view.cdbms.func.oracle.OracleRownum;
import com.k2view.cdbms.usercode.lu.reg_test.*;
import com.k2view.fabric.events.*;
import com.k2view.fabric.fabricdb.datachange.TableDataChange;
import com.k2view.graphIt.Json;
import org.json.JSONArray;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.shared.user.ProductFunctions.*;
import static com.k2view.cdbms.usercode.common.SharedLogic.*;
import static com.k2view.cdbms.usercode.lu.reg_test.Globals.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class Logic extends UserCode {


	@type(EventFunction)
	public static void cassAuthentication(EventDataContext eventDataContext) throws Exception {
		log.info("succeded!!!########################");
	}




	@type(UserJob)
	public static void jobTest() throws Exception {
		String sql = "migrate reg_test from dbCassandra using ('select iid from k2view_reg_test_6_5_tdm.test_table1') with async=true";
		fabric().execute("set sync force");
		
		log.info("job ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		//vfnl_issue1();
		try{
			db("fabric").execute(sql);
			//Thread.sleep(2000);
			//fabric().execute("ref_sync tables='verizon'");
			
			
			log.info("batch is done~~~~~~~~~~~~~~~~~~~~");
			Db.Rows rows = db("dbCassandra").fetch("select iid from k2view_reg_test_6_5_tdm.test_table1");
			for(Db.Row row : rows){
				log.info("row = "+row);
				fabric().execute("delete instance TEST_CUST.?", row);
				}
		}catch(SQLException e){log.info(e.toString());}
	}


	@type(RootFunction)
	@out(name = "output", type = void.class, desc = "")
	public static void fnRootDummy(String input) throws Exception {
		Object[] row = {null};
		yield(row);
	}


	@type(DecisionFunction)
	@out(name = "decision", type = Boolean.class, desc = "")
	public static Boolean fnExt() throws Exception {
		return false;
	}


	@out(name = "out", type = String.class, desc = "")
	public static String fnSleep(String in) throws Exception {
		Thread.sleep(2000);
		
		return "a";
	}


	@out(name = "out", type = String.class, desc = "")
	public static String fnPrintInput(String in, Iterable<Map<String,Object>> RS_new, Object RS_as_object) throws Exception {
		Map<String,Object> firstRow = null;
		Set<String> columnsToCompare = null;
		
		//log.info("hhh" + in );
		
		/*Iterable<?> aaa = (Iterable<?>) RS_as_object;
		Iterator<?> iteratorLU = aaa.iterator();
		while (iteratorLU.hasNext()){
			log.info("rrrrrr" + iteratorLU.next() );
		}
		*/
		
		Iterator<Map<String,Object>> iteratorLU1 = RS_new.iterator();
		if(iteratorLU1.hasNext()){
			log.info("### gilad2222");
		}
		
		
		
		//log.info("###fff " + RS_new.iterator().toString());
		//Iterator<Map<String,Object>> iteratorLU = RS_new.iterator();
		
		
		
		
		for (Map<String, Object> map : RS_new) {
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				String key = entry.getKey();
				Object value = entry.getValue();
				System.out.println("Key: " + key + ", Value: " + value);
			}
		}
		
		
		/*
		Db.Rows rows = (Db.Rows) RS_as_object;
		
		Object[] aaa = (Object[]) RS_as_object;
		for (Db.Row row : rows) {
			log.info("Element: " + row.toString());
		}
		*/
		
		
		/*
		Iterable<Map<String,Object>> aaa = Object[ RS_as_object;
		Iterator<Map<String,Object>> iteratorLU = aaa.iterator();
		if(iteratorLU.hasNext()){
			log.info("### gilad1111");
		}
		
		
		
		
		*/
		//firstRow = RS_new.iterator().next();
		
		
		
		
		
		
		
		
		
		/*
		
		Map<String,Object> map = (HashMap<String,Object>) RS_as_object;
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			System.out.println("Key: " + key + ", Value: " + value);
		}
		
		while (RS_new.iterator().hasNext()) {
			log.info("### WHILE there is 1 more line ahead in RS_new");
		}
		
		if(RS_new.iterator().hasNext()){
			log.info("### there is 1 more line ahead in RS_new");
			firstRow = RS_new.iterator().next();
			log.info("### firstRow = " + firstRow.toString());
		} else {
			log.info("### there is NO more line ahead in RS_new");
		}
		
		
		
		
		
		
		
		
		*/
		
		
		
		//log.info("hhh" + RS_new.iterator().toString());
		//log.info("hhh" + RS_new.getClass().getName());
		//log.info("hhh" + RS_new.iterator().next());
		
		
		//JSONArray qqq =  new JSONArray(RS_new);
		//Iterator <Object> it1 = qqq.iterator();
		//while (it1.hasNext()) {
		//	log.info("### JSONARRAY qqq --- there is 1 more line ahead in RS_new");
		//}
		
		
		/*String aaa = "[\n" +
				"\n" +
				"  {\n" +
				"\n" +
				"    \"customer_id\": 215\n" +
				"\n" +
				"  },\n" +
				"\n" +
				"  {\n" +
				"\n" +
				"    \"customer_id\": 216\n" +
				"\n" +
				"  },\n" +
				"\n" +
				"  {\n" +
				"\n" +
				"    \"customer_id\": 217\n" +
				"\n" +
				"  },\n" +
				"\n" +
				"  {\n" +
				"\n" +
				"    \"customer_id\": 218\n" +
				"\n" +
				"  },\n" +
				"\n" +
				"  {\n" +
				"\n" +
				"    \"customer_id\": 219\n" +
				"\n" +
				"  },\n" +
				"\n" +
				"  {\n" +
				"\n" +
				"    \"customer_id\": 220\n" +
				"\n" +
				"  }\n" +
				"\n" +
				"]";
		
		JSONArray bbb = new JSONArray(aaa);
		Iterator <Object> it = bbb.iterator();
		while (it.hasNext()) {
			log.info("### JSONARRAY  there is 1 more line ahead in RS_new");
		}
		*/
		
		
		
		return in;
	}

	
	
	
	
}
