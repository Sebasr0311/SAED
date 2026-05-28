package com.edificio.admin.util;

import java.util.regex.Pattern;

public class ValidadorDocumento {
    private static final Pattern CEDULA_COL = Pattern.compile("^[0-9]{6,12}$");
    private static final Pattern PASAPORTE = Pattern.compile("^[A-Z]{1,2}[0-9]{4,9}$");
    private static final Pattern CORREO = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9]{10,12}$");

    public static boolean validarCedula(String numeroDocumento) {
        if (numeroDocumento == null || numeroDocumento.isEmpty()) {
            return false;
        }
        return CEDULA_COL.matcher(numeroDocumento).matches();
    }

    public static boolean validarPasaporte(String numeroDocumento) {
        if (numeroDocumento == null || numeroDocumento.isEmpty()) {
            return false;
        }
        return PASAPORTE.matcher(numeroDocumento).matches();
    }

    public static boolean validarDocumento(String numeroDocumento, String tipoDocumento) {
        if (numeroDocumento == null || numeroDocumento.isEmpty()) {
            return false;
        }
        switch (tipoDocumento != null ? tipoDocumento.toUpperCase() : "") {
            case "CC":
            case "CÉDULA":
            case "CEDULA":
                return validarCedula(numeroDocumento);
            case "PA":
            case "PASAPORTE":
                return validarPasaporte(numeroDocumento);
            case "CE":
            case "CEDULA_EXTRANJERIA":
                return CEDULA_COL.matcher(numeroDocumento).matches();
            default:
                return numeroDocumento.length() >= 5;
        }
    }

    public static boolean validarCorreo(String correo) {
        if (correo == null || correo.isEmpty()) {
            return false;
        }
        return CORREO.matcher(correo).matches();
    }

    public static boolean validarTelefono(String telefono) {
        if (telefono == null || telefono.isEmpty()) {
            return false;
        }
        return TELEFONO.matcher(telefono).matches();
    }

    public static String[] getTiposDocumento() {
        return new String[]{"Cédula", "Cédula de Extranjería", "Pasaporte", "NIT", "Otro"};
    }
}