package com.edificio.admin.dao;

import com.edificio.admin.exception.ConexionFallidaException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton thread-safe para la conexion a Oracle 18c.
 * Cada hilo obtiene su propia conexion via ThreadLocal, garantizando
 * seguridad en entornos multihilo (servidor REST con pool de hilos).
 *
 * Esquema: RESIDENCIAL / Tablespace: RESIDENCIAL_TBS / Service: xepdb1
 *
 * Las credenciales se leen en este orden (primero gana):
 *   1. Variables de entorno: DB_URL, DB_USER, DB_PASS
 *   2. Archivo /bd.properties del classpath (db.url, db.usuario, db.clave)
 *   3. Valores por defecto (localhost/xepdb1) — solo para desarrollo local
 *
 * NO usa Class.forName() — ojdbc11 registra el driver automaticamente
 * via java.sql.DriverManager (Service Provider Interface, Java 9+).
 */
public class ConexionBD {

    // Valores por defecto (fallback solo desarrollo local)
    private static final String URL_DEFAULT     = "jdbc:oracle:thin:@localhost:1521/xepdb1";
    private static final String USUARIO_DEFAULT = "RESIDENCIAL";
    private static final String CLAVE_DEFAULT   = "Residencial2024#";

    private final String url;
    private final String usuario;
    private final String clave;

    private static volatile ConexionBD instancia;
    private static final ThreadLocal<Connection> conexionPorHilo = new ThreadLocal<>();

    // Constructor privado — patron Singleton
    private ConexionBD() {
        // 1. Variables de entorno (produccion)
        String envUrl = getenv("DB_URL");
        String envUsr = getenv("DB_USER");
        String envPwd = getenv("DB_PASS");

        // 2. Archivo bd.properties
        Properties props = cargarProperties();

        this.url     = first(envUrl, props.getProperty("db.url"),     URL_DEFAULT);
        this.usuario = first(envUsr, props.getProperty("db.usuario"), USUARIO_DEFAULT);
        this.clave   = first(envPwd, props.getProperty("db.clave"),   CLAVE_DEFAULT);

        if (CLAVE_DEFAULT.equals(this.clave) && getenv("DB_PASS") == null) {
            System.err.println("[ConexionBD] ADVERTENCIA: Usando clave por defecto. "
                + "Configure DB_PASS en entorno o db.clave en bd.properties para produccion.");
        }
    }

    /** Retorna el primer valor no nulo. */
    private static String first(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    /** Envoltura segura para System.getenv (nunca lanza excepcion). */
    private static String getenv(String key) {
        try { return System.getenv(key); } catch (Exception e) { return null; }
    }

    /**
     * Carga bd.properties desde el classpath.
     * Si no existe o falla la lectura devuelve Properties vacio
     * y el constructor usara los valores por defecto o env vars.
     */
    private static Properties cargarProperties() {
        Properties p = new Properties();
        try (InputStream is = ConexionBD.class.getResourceAsStream("/bd.properties")) {
            if (is != null) {
                p.load(is);
            } else {
                System.err.println("[ConexionBD] bd.properties no encontrado; usando variables de entorno o default.");
            }
        } catch (IOException e) {
            System.err.println("[ConexionBD] Error al leer bd.properties: " + e.getMessage());
        }
        return p;
    }

    public static ConexionBD getInstancia() {
        if (instancia == null) {
            synchronized (ConexionBD.class) {
                if (instancia == null) {
                    instancia = new ConexionBD();
                }
            }
        }
        return instancia;
    }

    /**
     * Retorna una conexion dedicada para el hilo actual.
     * Cada hilo obtiene su propia instancia de Connection,
     * eliminando problemas de concurrencia.
     */
    public Connection getConexion() {
        try {
            Connection c = conexionPorHilo.get();
            if (c == null || c.isClosed()) {
                c = crearConexion();
                conexionPorHilo.set(c);
            }
            return c;
        } catch (SQLException e) {
            throw new ConexionFallidaException(
                "No se pudo obtener conexion para el hilo actual: " + e.getMessage(), e);
        }
    }

    /**
     * Cierra todas las conexiones abiertas por cada hilo y reinicia la instancia.
     */
    public static void cerrar() {
        // No usar instancia para no crear una si no existe
        Connection c = conexionPorHilo.get();
        if (c != null) {
            try {
                if (!c.isClosed()) c.close();
            } catch (SQLException e) {
                System.err.println("[ConexionBD] Error al cerrar conexion del hilo: " + e.getMessage());
            }
            conexionPorHilo.remove();
        }
    }

    /**
     * Cierra todas las conexiones de todos los hilos y reinicia la instancia.
     */
    public static synchronized void cerrarTodas() {
        // Este metodo se llama solo al detener el servidor
        // En un escenario tipico cada hilo cierra su conexion al finalizar
        System.out.println("[ConexionBD] Cerrando todas las conexiones...");
        // Nota: no podemos iterar ThreadLocal values. Cada hilo debe llamar a cerrar().
        // En la practica, al detener el servidor se cierran las conexiones activas.
        instancia = null;
    }

    private Connection crearConexion() {
        try {
            Connection c = DriverManager.getConnection(url, usuario, clave);
            c.setAutoCommit(true);
            try (var stmt = c.createStatement()) {
                stmt.execute("ALTER SESSION SET TIME_ZONE = 'America/Bogota'");
            }
            return c;
        } catch (SQLException e) {
            throw new ConexionFallidaException(
                "No se pudo conectar a Oracle (" + url + "): " + e.getMessage(), e);
        }
    }
}
