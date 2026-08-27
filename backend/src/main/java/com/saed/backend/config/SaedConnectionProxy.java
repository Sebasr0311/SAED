package com.saed.backend.config;

import java.sql.Connection;
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
            // Delegar el close directamente a Hikari. El contexto RLS se limpia
            // por request en JwtAuthenticationFilter (SaedContextHolder.clearContext
            // en finally) y cada conexion del pool re-aplica SET_CONTEXT al
            // obtenerse (SaedDataSourceProxy.applySaedContext). Interceptar
            // close() aqui rompe el reciclaje del pool y agota las conexiones
            // ("Failed to obtain JDBC Connection") bajo carga del navegador.
            target.close();
            return null;
        }
        return method.invoke(target, args);
    }
}