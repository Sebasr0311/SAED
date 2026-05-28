package com.edificio.admin.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class StaticFilesHandler implements HttpHandler {

    private final Path baseDir;

    private static final Map<String, String> MIME = new java.util.HashMap<>();
    static {
        MIME.put("html", "text/html");
        MIME.put("css", "text/css");
        MIME.put("js", "application/javascript");
        MIME.put("json", "application/json");
        MIME.put("png", "image/png");
        MIME.put("jpg", "image/jpeg");
        MIME.put("jpeg", "image/jpeg");
        MIME.put("gif", "image/gif");
        MIME.put("svg", "image/svg+xml");
        MIME.put("ico", "image/x-icon");
        MIME.put("pdf", "application/pdf");
        MIME.put("woff", "font/woff");
        MIME.put("woff2", "font/woff2");
    }

    public StaticFilesHandler(String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();

        if (requestPath.equals("/")) requestPath = "/index.html";

        Path file = baseDir.resolve(requestPath.substring(1)).normalize();

        if (!file.startsWith(baseDir) || !Files.exists(file) || Files.isDirectory(file)) {
            // fallback a index.html para SPA routing
            file = baseDir.resolve("index.html");
            if (!Files.exists(file)) {
                String resp = "404 Not Found";
                exchange.sendResponseHeaders(404, resp.length());
                exchange.getResponseBody().write(resp.getBytes());
                exchange.getResponseBody().close();
                return;
            }
        }

        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String ext = dot > 0 ? fileName.substring(dot + 1) : "";
        String contentType = MIME.getOrDefault(ext.toLowerCase(), "application/octet-stream");

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, Files.size(file));
        try (OutputStream os = exchange.getResponseBody()) {
            Files.copy(file, os);
        }
    }
}
