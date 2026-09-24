package com.saed.backend.trabajadores.dto;

import java.time.LocalDate;

public record TrabajadorDTO(
        Long idTrabajador,
        Long idPersona,
        Long idProveedor,
        String razonSocialProveedor,
        String tipoDocumento,
        String numeroDocumento,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        String nombreCompleto,
        String email,
        String telefono,
        String oficioEspecialidad,
        String arlAseguradora,
        LocalDate arlFechaAfiliacion,
        LocalDate arlFechaVencimiento,
        LocalDate certAlturasVencimiento,
        String empresaIndependiente,
        String estado,
        boolean arlVigente
) {
    public static boolean calcularArlVigente(LocalDate vencimiento, LocalDate afiliacion) {
        return calcularArlVigente(vencimiento, afiliacion, LocalDate.now());
    }

    public static boolean calcularArlVigente(LocalDate vencimiento, LocalDate afiliacion, LocalDate fechaReferencia) {
        if (vencimiento == null) {
            return false;
        }
        LocalDate hoy = (fechaReferencia != null) ? fechaReferencia : LocalDate.now();

        // Rango inválido / incoherente: afiliación posterior a vencimiento
        if (afiliacion != null && afiliacion.isAfter(vencimiento)) {
            return false;
        }

        // Vencimiento pasado: fecha_vencimiento < hoy -> No vigente (vencimiento = hoy sigue vigente)
        if (vencimiento.isBefore(hoy)) {
            return false;
        }

        // Afiliación futura: fecha_afiliacion > hoy -> No vigente (afiliacion = hoy ya está vigente)
        if (afiliacion != null && afiliacion.isAfter(hoy)) {
            return false;
        }

        return true;
    }
}
