/////////////////////////////////////////////////////////////////////////
// Shared Globals
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common;

import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;

public class SharedGlobals {

	@desc("Indicate for how long to keep data in IIDF_RECENT_UPDATES Table in hours")
	@category("IIDF")
	public static String IIDF_KEEP_HISTORY_TIME = "0";
	@desc("Indicate Project Cassandra interface name")
	@category("IIDF")
	public static final String DB_CASS_NAME = "dbCassandra";
	@desc("Indicate the delay between the source to kafka - in minutes")
	@category("IIDF")
	public static String IIDF_SOURCE_DELAY = "0";
	@desc("Indicate if to run LUDB's source population map, Need to be set per LU")
	@category("IIDF")
	public static String IIDF_EXTRACT_FROM_SOURCE = "true";
	@desc("Indicate the list of  ludb's root table, Need to be set per LU")
	@category("IIDF")
	public static final String IIDF_ROOT_TABLE_NAME = "";
	@desc("If to insertr statistics to IIDF_STATISTICS common table")
	@category("IIDF")
	public static String STATS_ACTIVE = "false";
	@desc("If to insert IID statistics")
	@category("IIDF")
	public static String IID_STATS = "false";
	@desc("If to skip cross IID logic")
	@category("IIDF")
	public static String CROSS_IID_SKIP_LOGIC = "false";
	@desc("If data change process time in ms is bigger then this a warning with details will  be printed to log")
	@category("IIDF")
	public static String DEBUG_LOG_THRESHOLD = "200";
	@desc("Lus list (comma seperated) to send statistics for")
	@category("IIDF")
	public static String STATS_EMAIL_REPORT_LUS = "";
	@desc("The prefix used for LU Table Kafka topics")
	@category("IIDF")
	public static String IDFINDER_TOPIC_PREFIX = "IidFinder.";
	@desc("The group id used for parser get")
	@category("IIDF_PARSER_GET")
	public static String GET_PARSER_GROUP_ID = "fabric_default";
	@desc("Used to slow down the parser get")
	@category("IIDF_PARSER_GET")
	public static String IID_GET_DELAY = "0";
	@desc("Time to start parser get")
	@category("IIDF_PARSER_GET")
	public static String GET_JOB_START_TIME = "0";
	@desc("Time to stop parser get")
	@category("IIDF_PARSER_GET")
	public static String GET_JOB_STOP_TIME = "0";
	@desc("If to run parser get manager")
	@category("IIDF_PARSER_GET")
	public static String GET_JOBS_IND = "false";
	@desc("Delay iid process time")
	@category("IIDF_PARSER_GET")
	public static String DELTA_JOB_DELAY_TIME = "300";
	@desc("iid sync will be skipped if the previous sync of this Iid occurred in the last X seconds")
	@category("IIDF_PARSER_GET")
	public static String DELTA_JOB_PREV_MESSAGE_DIFF_EPSILON_MS = "4000";
	@desc("Maximum IIDs to keep in cache")
	@category("IIDF_PARSER_GET")
	public static String DELTA_JOB_MAX_CACHE_SIZE = "1000000";
	@desc("If IDFinder lag is bigger then this, Parser get will be stopoped")
	@category("IIDF_PARSER_GET")
	public static String LAG_THRESHOLD = "0";
	@desc("Delay orphan msg process time")
	@category("IIDF_PARSER_ORPHAN")
	public static String ORPHAN_JOB_DELAY_TIME = "";
	@desc("The max number of datachanges for iid")
	@category("IIDF")
	public static String IID_DATACHANGE_LIMIT = "100000";
	@desc("The LOOKUP consumer Kafka group ID")
	@category("IIDF_JMX")
	public static String LOOKUP_GROUP_ID = "";
	@desc("List of LUs  (comma seperated) to expose stats for")
	@category("IIDF_JMX")
	public static String JMX_STATS_LUS = "";
	public static String SQLITE_RESERVED_KEYWORDS = "RANK";
	@category("IIDF_PRIORITY_PARSER_GET")
	public static String IID_PRIORITY_GET_DELAY = "0";
	@category("IIDF_PRIORITY_PARSER_GET")
	public static String DELTA_PRIORITY_JOB_DELAY_TIME = "300";
	@category("IIDF_PRIORITY_PARSER_GET")
	public static String DELTA_PRIORITY_JOB_PREV_MESSAGE_DIFF_EPSILON_MS = "4000";
	@desc("Maximum values of combo box input object")
	@category("TDM")
	public static String COMBO_MAX_COUNT = "49";
	@desc("Indicator to delete the instance to target DB")
	@category("TDM")
	public static String TDM_DELETE_BEFORE_LOAD = "false";
	@desc("Indicator to insert the instance to target DB")
	@category("TDM")
	public static String TDM_INSERT_TO_TARGET = "false";
	@category("TDM")
	public static String TDM_SYNC_SOURCE_DATA = "true";
	@desc("Target product version to override by task execution process")
	@category("TDM")
	public static String TDM_TARGET_PRODUCT_VERSION = "false";
	@desc("Source product version to override by task execution process")
	@category("TDM")
	public static String TDM_SOURCE_PRODUCT_VERSION = "false";
	@category("TDM")
	public static String TDM_REPLACE_SEQUENCES = "false";
	@category("TDM")
	public static String TDM_TASK_EXE_ID = "0";
	@category("TDM")
	public static String MASK_FLAG = "0";
	@category("TDM")
	public static String TDM_SOURCE_ENVIRONMENT_NAME = "SRC";
	@category("TDM")
	public static String TDM_SYNTHETIC_DATA = "false";
	@category("TDM")
	public static String TDM_TASK_ID = "0";
	@category("TDM")
	public static String TDM_VERSION_DATETIME = "19700101000000";
	@category("TDM")
	public static String TDM_VERSION_NAME = "";
	@desc("Indicator to mark the task as dataflux or not")
	@category("TDM")
	public static String TDM_DATAFLUX_TASK = "false";
	@desc("The TTL for external entity list for non jdbc source DB")
	@category("TDM")
	public static String TDM_EXTERNAL_ENTITY_LIST_TTL = "2592000";
	@desc("The maximum number of entities to be returned to be displayed in list of entities")
	@category("TDM")
	public static String MAX_NUMBER_OF_ENTITIES_IN_LIST = "100";
	@desc("Each Instance can have a TTL, this global holds the type of the TTL and it can have one of the following values:Minutes, Hours, Days, Weeks, or Years")
	@category("TDM")
	public static String TDM_LU_RETENTION_PERIOD_TYPE = "Days";
	@desc("The value of the TTL based on the type defined in TDM_LU_RETENTION_PERIOD_TYPE. Populate this global with zero or empty value to avoid setting a TTL on the TDM LUIs.")
	@category("TDM")
	public static String TDM_LU_RETENTION_PERIOD_VALUE = "10";
	@desc("The max number of retrieved entities from TDM_RESERVED_ENTITIES, if set to zero then no limit")
	@category("TDM")
	public static String GET_RESERVED_ENTITIES_LIMIT = "0";
	@category("TDM_DEBUG")
	public static String USER_NAME = "admin";
	@category("TDM_DEBUG")
	public static String USER_FABRIC_ROLES = "admin";
	@category("TDM_DEBUG")
	public static String USER_PERMISSION_GROUP = "admin";
	@category("TDM_DEBUG")
	public static String TDM_RESERVE_IND = "false";
	@category("TDM_DEBUG")
	public static String RESERVE_RETENTION_PERIOD_TYPE = "Days";
	@category("TDM_DEBUG")
	public static String RESERVE_RETENTION_PERIOD_VALUE = "10";
	@category("TDM_DEBUG")
	public static String BE_ID = "0";
	@category("TDM_DEBUG")
	public static String TASK_TYPE = "EXTRACT";
	@category("TDM_DEBUG")
	public static String enable_masking = "true";
	@category("TDM_DEBUG")
	public static String enable_sequences = "false";
	@category("TDM")
	public static String TDM_REF_UPD_SIZE = "10000";
	@category("TDM")
	public static String TDM_TARGET_ENVIRONMENT_NAME = "TAR";

	




	


}
