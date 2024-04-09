package com.k2view.cdbms.usercode.lu.GG_Simulator;

import com.k2view.broadway.model.Actor;
import com.k2view.broadway.model.Context;
import com.k2view.broadway.model.Data;
import com.k2view.fabric.parser.JSQLParserException;
import com.k2view.fabric.parser.expression.Expression;
import com.k2view.fabric.parser.expression.operators.relational.ExpressionList;
import com.k2view.fabric.parser.parser.CCJSqlParserManager;
import com.k2view.fabric.parser.statement.Delete;
import com.k2view.fabric.parser.statement.Insert;
import com.k2view.fabric.parser.statement.select.PlainSelect;
import com.k2view.fabric.parser.statement.select.Select;
import com.k2view.fabric.parser.statement.select.SelectItem;
import com.k2view.fabric.parser.statement.update.UpdateTable;

import java.io.StringReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AnalyzeSql implements Actor {

    //Analyze select statement
    public void action(Data input, Data output, Context context) throws Exception {
        com.k2view.fabric.parser.statement.Statement sqlStmt = null;
        try {
            sqlStmt = new CCJSqlParserManager().parse(new StringReader(input.string("sql")));
        } catch (Exception e) {
            throw new Exception("Cannot parse sql statement: " + input.string("sql") + ", This might be caused by values that contain single quotes");
        }
        if ("SELECT".equals(input.string("sql_type"))) {
            if (sqlStmt instanceof Select) {
                Select selectStmt = (Select) sqlStmt;
                PlainSelect plainSelect = (PlainSelect) selectStmt.getSelectBody();
                output.put("tableName", plainSelect.getFromItem().toString());
            } else throw new SQLException("Please insert a valid select statement");
        } else {
            if (sqlStmt instanceof UpdateTable) {
                UpdateTable upStmt = ((UpdateTable) sqlStmt);
                String sourceTableName = upStmt.getTable().toString();
                List expList = upStmt.getExpressions();
                List column = upStmt.getColumns();
                output.put("tableName", sourceTableName.toUpperCase());
                output.put("statementValues", expList);
                output.put("statementColumns", column);
                output.put("whereClause", upStmt.getWhere().toString().split("(?i)( and )"));
            } else if (sqlStmt instanceof Insert) {
                Insert insStmt = (Insert) sqlStmt;
                String sourceTableName = insStmt.getTable().toString();
                List<Expression> expList = ((ExpressionList) insStmt.getItemsList()).getExpressions();
                List column = insStmt.getColumns();
                output.put("tableName", sourceTableName.toUpperCase());
                output.put("statementValues", expList);
                output.put("statementColumns", column);
            } else if (sqlStmt instanceof Delete) {
                Delete dltStmt = (Delete) sqlStmt;
                String sourceTableName = dltStmt.getTable().toString();
                output.put("tableName", sourceTableName.toUpperCase());
                output.put("whereClause", dltStmt.getWhere().toString().split("(?i)( and )"));
            } else throw new SQLException("Please insert a valid DML command");
        }
    }
}
