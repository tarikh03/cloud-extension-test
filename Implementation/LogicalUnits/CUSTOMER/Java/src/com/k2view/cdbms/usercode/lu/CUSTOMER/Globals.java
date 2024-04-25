/////////////////////////////////////////////////////////////////////////
// LU Globals
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.lu.CUSTOMER;

import com.k2view.cdbms.usercode.common.SharedGlobals;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;

public class Globals extends SharedGlobals {



	@desc("Indicate which is the real ludb's root table")
	@category("IIDF")
	public static final String IIDF_ROOT_TABLE_NAME = "CUSTOMER";
	@desc("Indicate if to run LUDB's source population map")
	@category("IIDF")
	public static String IIDF_EXTRACT_FROM_SOURCE = "true";

	


}
