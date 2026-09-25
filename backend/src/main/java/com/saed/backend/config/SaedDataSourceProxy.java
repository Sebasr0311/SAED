package com.saed.backend.config;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

public class SaedDataSourceProxy extends DelegatingDataSource {

    public SaedDataSourceProxy(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        try {
            applySaedContext(connection);
            return connection;
        } catch (Exception e) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            if (e instanceof SQLException) {
                throw (SQLException) e;
            }
            throw new SQLException("Failed to apply SAED context", e);
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        try {
            applySaedContext(connection);
            return connection;
        } catch (Exception e) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            if (e instanceof SQLException) {
                throw (SQLException) e;
            }
            throw new SQLException("Failed to apply SAED context", e);
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SaedDataSourceProxy.class);

    private void applySaedContext(Connection connection) throws SQLException {
        SaedContext context = SaedContextHolder.getContext();
        
        if (context == null || context.getUserId() == null) {
            try (CallableStatement cs = connection.prepareCall("{call PKG_SAED_SESSION.CLEAR_CONTEXT()}")) {
                cs.execute();
            }
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("SAED CONTEXT TO ORACLE: userId={} orgId={} propId={} role={}",
                    context.getUserId(), context.getOrganizationId(), context.getPropertyId(), context.getRoleCode());
        }

        if (context.getRoleScope() != null || context.getRoleCode() != null) {
            String plsql = "BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?); PKG_SAED_SESSION.SET_CONTEXT(?, ?, ?, ?); END;";
            try (CallableStatement cs = connection.prepareCall(plsql)) {
                cs.setLong(1, context.getUserId());
                cs.setLong(2, context.getUserId());

                if (context.getOrganizationId() != null) {
                    cs.setLong(3, context.getOrganizationId());
                } else {
                    cs.setNull(3, Types.NUMERIC);
                }

                if (context.getPropertyId() != null) {
                    cs.setLong(4, context.getPropertyId());
                } else {
                    cs.setNull(4, Types.NUMERIC);
                }

                if (context.getRoleCode() != null) {
                    cs.setString(5, context.getRoleCode());
                } else {
                    cs.setNull(5, Types.VARCHAR);
                }

                cs.execute();
            }
        } else {
            try (CallableStatement cs = connection.prepareCall("{call PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?)}")) {
                cs.setLong(1, context.getUserId());
                cs.execute();
            }
        }
    }
}
