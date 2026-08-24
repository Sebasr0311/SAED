package com.edificio.admin.config;

import com.edificio.admin.model.Usuario;

/**
 * Mantiene el usuario autenticado durante la sesión activa.
 * Se establece en LoginController tras autenticarse y se limpia al cerrar sesión.
 */
public class SesionUsuario {

    private static Usuario usuarioActual;

    private SesionUsuario() {}

    public static Usuario getUsuarioActual() {
        return usuarioActual;
    }

    public static void setUsuarioActual(Usuario usuario) {
        usuarioActual = usuario;
    }

    public static void cerrarSesion() {
        usuarioActual = null;
    }
}
