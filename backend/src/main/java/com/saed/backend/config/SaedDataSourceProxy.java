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
            return SaedConnectionProxy.createProxy(connection);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        try {
            applySaedContext(connection);
            return SaedConnectionProxy.createProxy(connection);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    private void applySaedContext(Connection connection) throws SQLException {
        SaedContext context = SaedContextHolder.getContext();
        
        if (context == null || context.getUserId() == null) {
            // State 0: Anonymous (login flow)
            return;
        }

        if (context.getOrganizationId() == null) {
            // State 1: Bootstrap (identity only, fetching contexts)
            try (CallableStatement cs = connection.prepareCall("{call PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?)}")) {
                cs.setLong(1, context.getUserId());
                cs.execute();
            }
        } else {
            // State 2: Business (full tenant context)
            try (CallableStatement cs = connection.prepareCall("{call PKG_SAED_SESSION.SET_CONTEXT(?, ?, ?, ?)}")) {
                cs.setLong(1, context.getUserId());
                cs.setLong(2, context.getOrganizationId());
                
                if (context.getPropertyId() != null) {
                    cs.setLong(3, context.getPropertyId());
                } else {
                    cs.setNull(3, Types.NUMERIC);
                }
                
                if (context.getRoleCode() != null) {
                    cs.setString(4, context.getRoleCode());
                } else {
                    cs.setNull(4, Types.VARCHAR);
                }
                
                cs.execute();
            }
        }
    }
}
