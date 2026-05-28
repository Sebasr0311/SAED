package com.edificio.admin; 

import com.edificio.admin.rest.RestServer;

/**
 * Punto de entrada solo para el backend REST (sin ventana JavaFX).
 * Corre el servidor HTTP en el puerto 8080 y mantiene el proceso vivo.
 */
public class RestServerMain {

    public static void main(String[] args) {
        RestServer.start(8080);
        System.out.println("[RestServerMain] Backend REST iniciado en puerto 8080");
        System.out.println("[RestServerMain] Presione Ctrl+C para detener");
        // Mantener el hilo principal vivo
        Object lock = new Object();
        synchronized (lock) {
            try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}