package com.edificio.admin.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class FechaUtil {
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    public static String formatFecha(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return fecha.format(FORMATO_FECHA);
    }

    public static String formatFechaHora(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            return "";
        }
        return fechaHora.format(FORMATO_FECHA_HORA);
    }

    public static String formatHora(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            return "";
        }
        return fechaHora.format(FORMATO_HORA);
    }

    public static LocalDate parseFecha(String fecha) {
        if (fecha == null || fecha.isEmpty()) {
            return null;
        }
        return LocalDate.parse(fecha, FORMATO_FECHA);
    }

    public static long diasEntre(LocalDate fecha1, LocalDate fecha2) {
        if (fecha1 == null || fecha2 == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(fecha1, fecha2);
    }

    public static long horasEntre(LocalDateTime fecha1, LocalDateTime fecha2) {
        if (fecha1 == null || fecha2 == null) {
            return 0;
        }
        return ChronoUnit.HOURS.between(fecha1, fecha2);
    }

    public static boolean estaVencido(LocalDate fechaLimite) {
        if (fechaLimite == null) {
            return false;
        }
        return LocalDate.now().isAfter(fechaLimite);
    }

    public static boolean estaProximoVencer(LocalDate fechaLimite, int dias) {
        if (fechaLimite == null) {
            return false;
        }
        return LocalDate.now().plusDays(dias).isAfter(fechaLimite) && !estaVencido(fechaLimite);
    }

    public static String getTiempoTranscurrido(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            return "";
        }
        long horas = horasEntre(fechaHora, LocalDateTime.now());
        if (horas < 1) {
            return "Hace menos de una hora";
        } else if (horas < 24) {
            return "Hace " + horas + " hora(s)";
        } else {
            long dias = ChronoUnit.DAYS.between(fechaHora, LocalDateTime.now());
            return "Hace " + dias + " día(s)";
        }
    }
}