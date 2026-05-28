package com.edificio.admin; 

import com.edificio.admin.rest.RestServer;

/**
 * Punto de entrada solo para el backend REST (sin ventana JavaFX).
 * Lee el puerto de la variable de entorno PORT (Railway) o usa 8080 por defecto.
 */
public class RestServerMain {

    public static void main(String[] args) {
        String portEnv = System.getenv("PORT");
        int port;
        try {
            port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
        } catch (NumberFormatException e) {
            port = 8080;
        }
        RestServer.start(port);
        System.out.println("[RestServerMain] Backend REST iniciado en puerto " + port);
        System.out.println("[RestServerMain] Presione Ctrl+C para detener");
        Object lock = new Object();
        synchronized (lock) {
            try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}