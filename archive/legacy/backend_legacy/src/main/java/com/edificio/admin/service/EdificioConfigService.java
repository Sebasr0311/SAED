package com.edificio.admin.service;

public class EdificioConfigService {

    private EdificioConfigService() {}

    public static String getNombreEdificio() { return "Torres del Horizonte"; }
    public static String getDireccionEdificio() { return "Cra 12 # 45-67"; }
    public static String getCiudadEdificio() { return "Monter\u00eda, C\u00f3rdoba"; }
    public static String getNitAdministrador() { return "900.456.789-1"; }
    public static String getNombreAdministrador() { return "Carlos Andr\u00e9s Mendoza"; }
    public static String getCcAdministrador() { return "79.123.456"; }
    public static String getTelefonoAdministracion() { return "+57 301 234 5678"; }
    public static String getCorreoAdministracion() { return "administracion@torreshorizonte.com"; }

    public static String formatearTipoApartamento(String tipo) {
        if (tipo == null) return "";
        return switch (tipo) {
            case "ESTUDIO" -> "Estudio";
            case "1_HAB" -> "1 Habitaci\u00f3n";
            case "2_HAB" -> "2 Habitaciones";
            case "3_HAB" -> "3 Habitaciones";
            case "PENTHOUSE" -> "Penthouse";
            default -> tipo;
        };
    }
}
