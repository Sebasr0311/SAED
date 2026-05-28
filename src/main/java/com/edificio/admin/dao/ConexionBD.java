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
 * Las credenciales se leen desde el classpath: /bd.properties
 * Si el archivo no se encuentra se usan los valores por defecto (fallback).
 *
 * NO usa Class.forName() — ojdbc11 registra el driver automaticamente
 * via java.sql.DriverManager (Service Provider Interface, Java 9+).
 */
public class ConexionBD {

    // Valores por defecto (fallback si bd.properties no existe en el classpath)
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
        Properties props = cargarProperties();
        this.url     = props.getProperty("db.url",     URL_DEFAULT);
        this.usuario = props.getProperty("db.usuario", USUARIO_DEFAULT);
        this.clave   = props.getProperty("db.clave",   CLAVE_DEFAULT);
        abrirConexion();
    }

    /**
     * Carga bd.properties desde el classpath.
     * Si no existe o falla la lectura devuelve Properties vacio
     * y el constructor usara los valores por defecto.
     */
    private static Properties cargarProperties() {
        Properties p = new Properties();
        try (InputStream is = ConexionBD.class.getResourceAsStream("/bd.properties")) {
            if (is != null) {
                p.load(is);
            } else {
                System.err.println("[ConexionBD] bd.properties no encontrado; usando valores por defecto.");
            }
        } catch (IOException e) {
            System.err.println("[ConexionBD] Error al leer bd.properties: " + e.getMessage());
        }
        return p;
    }

    /**
     * Devuelve la unica instancia de ConexionBD.
     * Thread-safe con bloqueo doble.
     *
     * @throws ConexionFallidaException si no se puede conectar a Oracle
     */
    public static synchronized ConexionBD getInstancia() {
        if (instancia == null) {
            instancia = new ConexionBD();
        }
        return instancia;
    }

    /**
     * Devuelve la conexion activa, reabriendo si fue cerrada.
     *
     * @throws ConexionFallidaException si la conexion no esta disponible
     */
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

    /** Cierra la conexion y destruye el singleton para permitir reconexion. */
    public static synchronized void cerrar() {
        if (instancia != null) {
            try {
                if (instancia.conexion != null && !instancia.conexion.isClosed()) {
                    instancia.conexion.close();
                }
            } catch (SQLException e) {
                // Solo loguear; de todas formas destruimos la instancia
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
