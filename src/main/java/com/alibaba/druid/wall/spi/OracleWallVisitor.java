/*
 * Copyright 1999-2011 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.druid.wall.spi;

import java.util.ArrayList;
import java.util.List;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLName;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLInListExpr;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLCallStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectGroupByClause;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLTruncateStatement;
import com.alibaba.druid.sql.ast.statement.SQLUnionQuery;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleDeleteStatement;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleInsertStatement;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleMergeStatement;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleMultiInsertStatement;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleMultiInsertStatement.InsertIntoClause;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleSelectQueryBlock;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleSelectTableReference;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.OracleUpdateStatement;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleASTVisitorAdapter;
import com.alibaba.druid.wall.Violation;
import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.WallProvider;
import com.alibaba.druid.wall.WallVisitor;
import com.alibaba.druid.wall.violation.IllegalSQLObjectViolation;

public class OracleWallVisitor extends OracleASTVisitorAdapter implements WallVisitor {

    private final WallConfig      config;
    private final WallProvider    provider;
    private final List<Violation> violations = new ArrayList<Violation>();

    public OracleWallVisitor(WallProvider provider){
        this.config = provider.getConfig();
        this.provider = provider;
    }

    public WallProvider getProvider() {
        return provider;
    }

    public WallConfig getConfig() {
        return config;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public boolean visit(SQLIdentifierExpr x) {
        String name = x.getName();
        name = WallVisitorUtils.form(name);
        if (config.isVariantCheck() && config.getPermitVariants().contains(name)) {
            getViolations().add(new IllegalSQLObjectViolation(toSQL(x)));
        }
        return true;
    }

    public boolean visit(SQLPropertyExpr x) {
        WallVisitorUtils.check(this, x);
        return true;
    }

    public boolean visit(SQLInListExpr x) {
        WallVisitorUtils.check(this, x);
        return true;
    }

    public boolean visit(SQLBinaryOpExpr x) {
        WallVisitorUtils.check(this, x);
        return true;
    }

    @Override
    public boolean visit(SQLMethodInvokeExpr x) {
        WallVisitorUtils.checkFunction(this, x);

        return true;
    }

    public boolean visit(OracleSelectTableReference x) {
        WallVisitorUtils.check(this, x);
        return true;
    }

    public boolean visit(SQLExprTableSource x) {
        WallVisitorUtils.check(this, x);

        if (x.getExpr() instanceof SQLName) {
            return false;
        }

        return true;
    }

    public boolean visit(SQLSelectGroupByClause x) {
        WallVisitorUtils.checkHaving(this, x.getHaving());
        return true;
    }

    @Override
    public boolean visit(SQLSelectQueryBlock x) {
        WallVisitorUtils.checkSelelct(this, x);

        return true;
    }

    @Override
    public boolean visit(OracleSelectQueryBlock x) {
        WallVisitorUtils.checkSelelct(this, x);

        return true;
    }

    @Override
    public boolean visit(SQLUnionQuery x) {
        WallVisitorUtils.checkUnion(this, x);

        return true;
    }

    @Override
    public String toSQL(SQLObject obj) {
        return SQLUtils.toOracleString(obj);
    }

    @Override
    public boolean isPermitTable(String name) {
        if (!config.isTableCheck()) {
            return false;
        }

        name = WallVisitorUtils.form(name);
        if (name.startsWith("v$") || name.startsWith("v_$")) {
            return true;
        }
        return config.getPermitTables().contains(name);
    }

    public void preVisit(SQLObject x) {
        if (!(x instanceof SQLStatement)) {
            return;
        }

        if (config.isNoneBaseStatementAllow()) {
            return;
        }

        boolean allow = false;
        if (x instanceof SQLInsertStatement) {
            allow = true;
        } else if (x instanceof SQLSelectStatement) {
            allow = true;
        } else if (x instanceof SQLDeleteStatement) {
            allow = true;
        } else if (x instanceof SQLUpdateStatement) {
            allow = true;
        } else if (x instanceof OracleMultiInsertStatement) {
            allow = true;
        } else if (x instanceof OracleMergeStatement) {
            allow = true;
        } else if (x instanceof SQLCallStatement) {
            allow = true;
        } else if (x instanceof SQLTruncateStatement) {
            allow = config.isTruncateAllow();
        } 
        
        if (!allow) {
            violations.add(new IllegalSQLObjectViolation(toSQL(x)));
        }
    }

    @Override
    public boolean visit(SQLSelectStatement x) {
        if (!config.isSelelctAllow()) {
            this.getViolations().add(new IllegalSQLObjectViolation(this.toSQL(x)));
            return false;
        }

        return true;
    }

    @Override
    public boolean visit(OracleInsertStatement x) {
        return visit((SQLInsertStatement) x);
    }

    @Override
    public boolean visit(SQLInsertStatement x) {
        WallVisitorUtils.checkInsert(this, x);

        return true;
    }

    @Override
    public boolean visit(InsertIntoClause x) {
        WallVisitorUtils.checkInsert(this, x);

        return true;
    }

    @Override
    public boolean visit(OracleMultiInsertStatement x) {
        if (!config.isInsertAllow()) {
            this.getViolations().add(new IllegalSQLObjectViolation(this.toSQL(x)));
            return false;
        }

        return true;
    }

    @Override
    public boolean visit(OracleDeleteStatement x) {
        return visit((SQLDeleteStatement) x);
    }

    @Override
    public boolean visit(SQLDeleteStatement x) {
        WallVisitorUtils.checkDelete(this, x);
        return true;
    }

    @Override
    public boolean visit(OracleUpdateStatement x) {
        return visit((SQLUpdateStatement) x);
    }

    @Override
    public boolean visit(SQLUpdateStatement x) {
        WallVisitorUtils.checkUpdate(this, x);

        return true;
    }
}
