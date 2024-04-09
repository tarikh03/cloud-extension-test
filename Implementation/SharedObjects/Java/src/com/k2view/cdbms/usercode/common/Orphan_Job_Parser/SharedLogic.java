/////////////////////////////////////////////////////////////////////////
// Project Shared Functions
/////////////////////////////////////////////////////////////////////////

package com.k2view.cdbms.usercode.common.Orphan_Job_Parser;

import com.k2view.cdbms.finder.refactor.IidfProcessorUtils;
import com.k2view.cdbms.finder.LuDaoFactory;
import com.k2view.cdbms.finder.LuDaoInterface;
import com.k2view.cdbms.finder.TriggeredMessage;
import com.k2view.cdbms.finder.api.IidFinderApi;
import com.k2view.cdbms.shared.utils.UserCodeDescribe.*;
import static com.k2view.cdbms.shared.user.UserCode.*;
import static com.k2view.cdbms.shared.utils.UserCodeDescribe.FunctionType.*;

@SuppressWarnings({"unused", "DefaultAnnotationParam"})
public class SharedLogic {

	@type(RootFunction)
	@out(name = "out", type = void.class, desc = "")
	public static void k2_MsgParserOrphan(String in) throws Exception {
		LuDaoInterface luDao = LuDaoFactory.getLuDao(getLuType().luName);
		luDao.setWorkerId("Orphan_Job_"+Thread.currentThread().getName());
		
		processMessages(msg -> {
			if (msg != null) {
				handleMsgOrphan(msg.getKey(), (String) msg.getValue());
			}
			if(isAborted())throw new InterruptedException();
			return true;
		});
		
		yield(null);
	}
	
	private static void handleMsgOrphan(String iid, String msg) {
		try {
			TriggeredMessage triggeredMsg = IidfProcessorUtils.fromJson(msg, TriggeredMessage.class);
			long h = System.currentTimeMillis() - triggeredMsg.getHandleTime();

			int delay = Integer.parseInt(getLuType().ludbGlobals.get("ORPHAN_JOB_DELAY_TIME"));
			if (h < delay) {
				Thread.sleep(delay - h);
			}
			IidFinderApi.processOrphans(getLuType().luName, iid, triggeredMsg);
		} catch (Exception ex) {
			log.error(String.format("Failed to handle %s.%s", getLuType().luName, iid), ex);
		}
	}

}
