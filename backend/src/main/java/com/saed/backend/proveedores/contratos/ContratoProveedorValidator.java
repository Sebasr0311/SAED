package com.saed.backend.proveedores.contratos;

import com.saed.backend.finanzas.dto.ContratoProveedorCreateDTO;

import java.math.BigDecimal;

/** Business validation of a supplier contract request (F09-02), applied before any database work. */
public final class ContratoProveedorValidator {

    private static final int MAX_DIAS_ALERTA = 365;

    private ContratoProveedorValidator() {
    }

    public static void validar(ContratoProveedorCreateDTO req) {
        if (req == null) {
            throw new IllegalArgumentException("El contrato es obligatorio.");
        }
        if (req.idProveedor() == null) {
            throw new IllegalArgumentException("El ID del proveedor es obligatorio.");
        }
        if (req.numeroContrato() == null || req.numeroContrato().isBlank() || req.numeroContrato().trim().length() > 50) {
            throw new IllegalArgumentException("El número de contrato es obligatorio (máximo 50 caracteres).");
        }
        if (req.objetoContrato() == null || req.objetoContrato().isBlank() || req.objetoContrato().trim().length() > 400) {
            throw new IllegalArgumentException("El objeto del contrato es obligatorio (máximo 400 caracteres).");
        }
        if (req.valorTotal() == null || req.valorTotal().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El valor total es obligatorio y no puede ser negativo.");
        }
        if (req.fechaInicio() == null || req.fechaFin() == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin del contrato son obligatorias.");
        }
        if (req.fechaFin().isBefore(req.fechaInicio())) {
            throw new IllegalArgumentException("La fecha de finalización no puede ser anterior a la fecha de inicio.");
        }
        if (req.diasAlertaVenc() != null && (req.diasAlertaVenc() < 0 || req.diasAlertaVenc() > MAX_DIAS_ALERTA)) {
            throw new IllegalArgumentException("Los días de alerta de vencimiento deben estar entre 0 y " + MAX_DIAS_ALERTA + ".");
        }
    }
}
