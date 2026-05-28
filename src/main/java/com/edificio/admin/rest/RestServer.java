package com.edificio.admin.rest;

import com.edificio.admin.rest.handler.*;
import com.edificio.admin.service.AlertaService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;

public class RestServer {

    private static HttpServer server;
    private static final CorsFilter CORS = new CorsFilter();

    public static synchronized void start(int port) {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));

            addContext("/api/auth", new AuthHandler());
            addContext("/api/usuarios", new UsuarioHandler());
            addContext("/api/residentes", new ResidenteHandler());
            addContext("/api/apartamentos", new ApartamentoHandler());
            addContext("/api/contratos", new ContratoHandler());
            addContext("/api/cuotas", new CuotaHandler());
            addContext("/api/pagos", new PagoHandler());
            addContext("/api/visitas", new VisitaHandler());
            addContext("/api/parqueaderos", new ParqueaderoHandler());
            addContext("/api/alertas", new AlertaHandler());
            addContext("/api/qr", new QrHandler());
            addContext("/api/registros-acceso", new RegistroAccesoHandler());
            addContext("/api/tipos-documento", new TipoDocumentoHandler());
            addContext("/api/visitantes", new VisitanteHandler());
            addContext("/api/buzon", new BuzonHandler());
            addContext("/api/tutores", new TutorHandler());
            addContext("/api/multas", new MultaHandler());
            addContext("/api/quejas", new QuejaSugerenciaHandler());

            // Health check para Railway
            server.createContext("/health", exchange -> {
                String resp = "{\"status\":\"ok\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length());
                exchange.getResponseBody().write(resp.getBytes());
                exchange.getResponseBody().close();
            });

            // Servir archivos estáticos del frontend
            String frontendDir = System.getProperty("frontend.dir", "frontend");
            server.createContext("/", new StaticFilesHandler(frontendDir));

            iniciarScheduler();

            server.start();
            System.out.println("[REST] Servidor HTTP iniciado en puerto " + port);
        } catch (Exception e) {
            System.err.println("[REST] Error al iniciar servidor: " + e.getMessage());
        }
    }

    private static void addContext(String path, com.sun.net.httpserver.HttpHandler handler) {
        com.sun.net.httpserver.HttpContext ctx = server.createContext(path, handler);
        ctx.getFilters().add(CORS);
    }

    private static ScheduledExecutorService scheduler;

    private static void iniciarScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "alerta-scheduler");
            t.setDaemon(true);
            return t;
        });
        AlertaService alertaService = new AlertaService();
        // Ejecutar una vez al día (cada 24h, empezando dentro de 1 minuto)
        scheduler.scheduleWithFixedDelay(() -> {
            alertaService.generarAlertasProximoVencimiento();
        }, 1, 24, TimeUnit.HOURS);
    }

    public static synchronized void stop() {
        if (scheduler != null) scheduler.shutdown();
        if (server != null) {
            server.stop(1);
            server = null;
        }
    }
}
