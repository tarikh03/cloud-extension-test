/////////////////////////////////////////////////////////////////////////
// Project Web Services
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.lu.k2_ws.IIDFValidations;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.io.*;

import com.google.gson.JsonNull;
import com.k2view.broadway.actors.builtin.CsvBuilder;
import com.k2view.cdbms.shared.*;
import com.k2view.cdbms.shared.user.WebServiceUserCode;
import com.k2view.cdbms.lut.*;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import com.k2view.fabric.api.endpoint.Endpoint.*;
import com.k2view.fabric.common.ClusterUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.k2view.cdbms.usercode.common.SharedGlobals.*;
import java.sql.*;
import java.math.*;
import java.util.Date;

import com.k2view.cdbms.sync.*;
import com.k2view.cdbms.shared.logging.LogEntry.*;
import com.k2view.cdbms.func.oracle.OracleToDate;
import com.k2view.cdbms.func.oracle.OracleRownum;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;
import static com.k2view.cdbms.shared.user.ProductFunctions.*;
import static com.k2view.cdbms.usercode.common.SharedLogic.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class Logic extends WebServiceUserCode {


	@desc("This WS test basic insert, update and delete scenarios for all LUs tables.\r\n" +
			"update will not be done on primary key column or linked columns")
	@webService(path = "", verb = {MethodType.GET}, version = "1", isRaw = true, isCustomPayload = false, produce = {Produce.JSON}, elevatedPermission = true)
	public static Object wsBasicLUTesting(@param(description="The LU Name to be tested") String LUName, @param(description="The LUI to be tested") String LUI, @param(description="If only specific table should be tested") String tableName, @param(description="If to run in async mode") boolean async) throws Exception {
		String testUID = UUID.randomUUID().toString();
		LUType luType = LUTypeFactoryImpl.getInstance().getTypeByName(LUName);
		String jobName = luType.luName + "." + "fnExecuteIIDFBasicTesting";
		final String k2System = ClusterUtil.getClusterId() == null || "".equals(ClusterUtil.getClusterId()) ? "k2system" : "k2system_" + ClusterUtil.getClusterId();
		
		JSONObject testResult = new JSONObject();
		fabric().execute(String.format("startjob user_job name='%s' UID='%s' args='{\"LUName\":\"%s\", \"LUI\":\"%s\", \"testUID\":\"%s\", \"tableName\":\"%s\"}'", jobName, testUID, LUName, LUI, testUID, tableName));
		if (!async) {
		    Object status;
		    do {
		        status = db(DB_CASS_NAME).fetch(String.format("select status from %s.k2_jobs where type = 'USER_JOB' and name = ? and uid = ? allow filtering", k2System), jobName, testUID).firstValue();
		    } while ("IN_PROCESS".equals(status.toString()) || "WAITING".equals(status.toString()));
		
		    db(DB_CASS_NAME).fetch(String.format("SELECT * from %s.iidf_basic_lu_testing_summary WHERE test_sequence = ? ", luType.getKeyspaceName()), testUID).forEach(row -> row.forEach((columnName, columnValue) -> testResult.put(columnName.toUpperCase(), columnValue)));
		    return testResult;
		}else{
			testResult.put("TEST_SEQUENCE", testUID);
		}
		
		return testResult;
	}

    @desc("This WS retrieve all LU Tables IDFinder configuration")
    @webService(path = "", verb = {MethodType.GET}, version = "1", isRaw = true, isCustomPayload = false, produce = {Produce.JSON}, elevatedPermission = true)
    public static Object wsValidateIDFinderConfigOnLUTables(@param(description = "The LU Name to fetch info from") String LUName) throws Exception {
        JSONObject jsonObject = new JSONObject();
        LUTypeFactoryImpl.getInstance().getTypeByName(LUName).ludbObjects.values().parallelStream().forEach(ludbObject -> {
            if (ludbObject.ludbObjectName.toLowerCase().startsWith("iidf")) return;
            TablePopulationObject popMap = ((TableObject) ludbObject).getEnabledTablePopulationObjects().get(0);
            if (popMap.iidFinderProp.proactiveIndicator) {
                jsonObject.put(ludbObject.ludbObjectName, popMap.iidFinderProp);
            } else {
                jsonObject.put(ludbObject.ludbObjectName, "IDFinder not configured!");
            }
        });

        return jsonObject;
    }


    @desc("This ws fetch the result for the basic IDFinder test")
    @webService(path = "", verb = {MethodType.GET}, version = "1", isRaw = true, isCustomPayload = false, produce = {Produce.JSON}, elevatedPermission = true)
    public static Object wsBasicLUTestingFetchResult(@param(description = "The test UID") String testUID, @param(description = "The LU Name related to this test") String LUName, @param(description = "If to fetch only summary or detailed results") Boolean summary, @param(description = "If to filter based on LU table name") String LUTableName, @param(description = "If to filter passed results") Boolean filterPassed, Boolean downloadReport) throws Exception {
        LUType luType = LUTypeFactoryImpl.getInstance().getTypeByName(LUName);
        String summaryTable = String.format("%s.%s", luType.getKeyspaceName(), "iidf_basic_lu_testing_summary");
        String detailsTable = String.format("%s.%s", luType.getKeyspaceName(), "iidf_basic_lu_testing_details");

        if (summary) {
            JSONObject testResult = new JSONObject();
            db(DB_CASS_NAME).fetch(String.format("SELECT * from %s WHERE test_sequence = ? ", summaryTable), testUID).forEach(row -> row.forEach((columnName, columnValue) -> testResult.put(columnName.toUpperCase(), columnValue)));
            return testResult;
        } else {
            JSONArray rs = new JSONArray();
            List<Object> params = new ArrayList<>();
            params.add(testUID);
            StringBuilder filterTable = new StringBuilder();
            if (LUTableName != null && !"".equals(LUTableName)) {
                filterTable.append(" and table_name = ?");
                params.add(LUTableName);
            } else {
                filterTable.append(" ALLOW FILTERING");
            }

            db(DB_CASS_NAME).fetch(String.format("SELECT * from %s WHERE test_sequence = ? ", detailsTable) + filterTable.toString(), params.toArray()).forEach(row -> {
                JSONObject testResultDetails = new JSONObject();
                if ((filterPassed != null && filterPassed) && "passed".equals(row.get("test_result"))) return;

                row.forEach((columnName, columnValue) -> testResultDetails.put(columnName.toUpperCase(), columnValue));

                rs.put(testResultDetails);
            });

            if (downloadReport) {
                java.text.DateFormat clsDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS");
                String fileName = "IIDF_Basic_Test_Result_%s.csv";
                StringWriter sw = new StringWriter();
                CSVFormat formatter = CSVFormat.DEFAULT.withRecordSeparator("\n").withQuote('"');
                formatter = formatter.withDelimiter(',');
                formatter = formatter.withHeader("TEST_SEQUENCE", "LU_NAME", "IID", "TABLE_NAME", "OPERATION_TESTED", "TEST_RESULT", "TEST_TIME", "GGMESSAGE");
                CSVPrinter csvPrinter = new CSVPrinter(sw, formatter);

                for (int i = 0; i < rs.length(); i++) {
                    JSONObject jsonLine = rs.getJSONObject(i);
                    csvPrinter.print(jsonLine.getString("TEST_SEQUENCE"));
                    csvPrinter.print(jsonLine.getString("LU_NAME"));
                    csvPrinter.print(jsonLine.getString("IID"));
                    csvPrinter.print(jsonLine.getString("TABLE_NAME"));
                    csvPrinter.print(jsonLine.getString("OPERATION_TESTED"));
                    csvPrinter.print(jsonLine.getString("TEST_RESULT"));
                    csvPrinter.print(jsonLine.has("TEST_TIME") ? jsonLine.get("TEST_TIME") : JSONObject.NULL);
                    csvPrinter.print(jsonLine.has("GGMESSAGE") ? jsonLine.get("GGMESSAGE") : JSONObject.NULL);
                    csvPrinter.println();
                }

                byte[] bytes = sw.toString().getBytes(StandardCharsets.UTF_8);
                ByteArrayOutputStream bOutput = new ByteArrayOutputStream();
                bOutput.write(bytes);
                bOutput.flush();
                response().setHeader("Content-Disposition", "attachment; filename=\"" + String.format(fileName, clsDateFormat.format(new Date())) + "\"");
                return bOutput;
            }
            return rs;
        }
    }
}
