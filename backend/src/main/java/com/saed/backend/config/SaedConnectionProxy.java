package com.saed.backend.config;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class SaedConnectionProxy implements InvocationHandler {
    
    private final Connection target;

    public SaedConnectionProxy(Connection target) {
        this.target = target;
    }

    public static Connection createProxy(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                new SaedConnectionProxy(target)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("close".equals(method.getName())) {
            clearSaedContext(target);
            target.close();
            return null;
        }
        return method.invoke(target, args);
    }

    private void clearSaedContext(Connection connection) {
        try (CallableStatement cs = connection.prepareCall("{call PKG_SAED_SESSION.CLEAR_CONTEXT()}")) {
            cs.execute();
        } catch (SQLException e) {
            // Log this strictly. If this fails, the connection might leak context to the next user.
            // Hikari usually rolls back and tests the connection, but this ensures Oracle drops the context.
            System.err.println("CRITICAL: Failed to clear SAED context on connection close: " + e.getMessage());
        }
    }
}
