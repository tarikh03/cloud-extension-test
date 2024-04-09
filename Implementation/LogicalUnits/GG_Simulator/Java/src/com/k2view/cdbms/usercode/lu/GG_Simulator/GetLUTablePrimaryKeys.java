package com.k2view.cdbms.usercode.lu.GG_Simulator;

import java.sql.SQLException;
import java.util.*;

import com.k2view.broadway.model.Data;
import com.k2view.broadway.model.Context;
import com.k2view.broadway.model.Actor;
import com.k2view.cdbms.config.LuTablesMap;
import com.k2view.cdbms.config.TableProperties;
import com.k2view.cdbms.finder.Table;
import com.k2view.cdbms.finder.TableProperty;
import com.k2view.cdbms.lut.LUType;
import com.k2view.cdbms.lut.LudbObject;
import com.k2view.cdbms.shared.Db;
import com.k2view.fabric.session.Fabric;

import static com.k2view.cdbms.shared.user.UserCode.fabric;

public class GetLUTablePrimaryKeys implements Actor {

    public void action(Data input, Data output, Context context) throws SQLException {

        LuTablesMap tablesMap = TableProperties.getInstance().getLuMap(input.fields().get("lu_name").toString());
        for (Map.Entry<String, List<TableProperty>> entry : tablesMap.entrySet()) {
            String tblName = entry.getValue().get(0).getTableName();
            String kp = entry.getValue().get(0).getTableKeyspace();
            if (input.fields().get("table_name").toString().equalsIgnoreCase(kp + "." + tblName)) {
                List<String> pks = entry.getValue().get(0).getPrimaryKeys();
                output.put("primary_keys", pks);
                break;
            }
        }
    }
}
