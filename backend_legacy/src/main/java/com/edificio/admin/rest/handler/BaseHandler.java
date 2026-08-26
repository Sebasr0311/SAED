package com.edificio.admin.rest.handler;

import com.edificio.admin.rest.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;

/**
 * Base class for all REST handlers.
 * Provides shared sendJson utility and consistent error handling.
 */
public abstract class BaseHandler {

    protected void sendJson(HttpExchange exchange, int code, Object obj) throws IOException {
        byte[] bytes = JsonUtil.toJson(obj).getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }
}
