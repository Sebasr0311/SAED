package com.edificio.admin.dao;

import com.edificio.admin.exception.ConexionFallidaException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton sincronizado para la conexion a Oracle 18c.
 * Esquema: RESIDENCIAL / Tablespace: RESIDENCIAL_TBS / Service: xepdb1
 *
 * Las credenciales se leen en este orden (primero gana):
 *   1. Variables de entorno: DB_URL, DB_USER, DB_PASS
 *   2. Archivo /bd.properties del classpath (db.url, db.usuario, db.clave)
 *   3. Valores por defecto (localhost/xepdb1)
 *
 * NO usa Class.forName() — ojdbc11 registra el driver automaticamente
 * via java.sql.DriverManager (Service Provider Interface, Java 9+).
 */
public class ConexionBD {

    // Valores por defecto (fallback)
    private static final String URL_DEFAULT     = "jdbc:oracle:thin:@localhost:1521/xepdb1";
    private static final String USUARIO_DEFAULT = "RESIDENCIAL";
    private static final String CLAVE_DEFAULT   = "Residencial2024#";

    private final String url;
    private final String usuario;
    private final String clave;

    private static ConexionBD instancia;
    private        Connection  conexion;

    // Constructor privado — patron Singleton
    private ConexionBD() {
        // 1. Variables de entorno (Railway)
        String envUrl = getenv("DB_URL");
        String envUsr = getenv("DB_USER");
        String envPwd = getenv("DB_PASS");

        // 2. Archivo bd.properties
        Properties props = cargarProperties();

        this.url     = first(envUrl, props.getProperty("db.url"),     URL_DEFAULT);
        this.usuario = first(envUsr, props.getProperty("db.usuario"), USUARIO_DEFAULT);
        this.clave   = first(envPwd, props.getProperty("db.clave"),   CLAVE_DEFAULT);
        abrirConexion();
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

    public static synchronized ConexionBD getInstancia() {
        if (instancia == null) {
            instancia = new ConexionBD();
        }
        return instancia;
    }

    public synchronized Connection getConexion() {
        try {
            if (conexion == null || conexion.isClosed()) {
                abrirConexion();
            }
        } catch (SQLException e) {
            throw new ConexionFallidaException(
                "No se pudo verificar el estado de la conexion: " + e.getMessage(), e);
        }
        return conexion;
    }

    public static synchronized void cerrar() {
        if (instancia != null) {
            try {
                if (instancia.conexion != null && !instancia.conexion.isClosed()) {
                    instancia.conexion.close();
                }
            } catch (SQLException e) {
                System.err.println("[ConexionBD] Error al cerrar: " + e.getMessage());
            } finally {
                instancia = null;
            }
        }
    }

    private void abrirConexion() {
        try {
            conexion = DriverManager.getConnection(url, usuario, clave);
            conexion.setAutoCommit(true);
        } catch (SQLException e) {
            throw new ConexionFallidaException(
                "No se pudo conectar a Oracle (" + url + "): " + e.getMessage(), e);
        }
    }
}
