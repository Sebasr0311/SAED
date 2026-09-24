package com.saed.backend.finanzas.service;

import com.saed.backend.finanzas.dto.PazYSalvoDetalleDTO;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

public interface PazYSalvoService {

    /**
     * Evalúa en tiempo real si una unidad está a paz y salvo financieramente
     * consolidando cartera (cuotas) y multas sin doble conteo.
     */
    PazYSalvoEstadoFinancieroDTO verificarEstadoFinanciero(Long idUnidad);

    /**
     * Emite un Paz y Salvo oficial para una unidad tras validar que no existan
     * obligaciones exigibles pendientes. Genera el PDF oficial, calcula el SHA-256
     * e interactúa con el sistema de almacenamiento y cuota.
     */
    Map<String, Object> generarPazYSalvo(Long idUnidad, String motivo);

    /**
     * Lista los paz y salvos emitidos con aislamiento de tenant y anti-IDOR.
     */
    List<Map<String, Object>> listar();

    /**
     * Obtiene el detalle completo y verificado de un paz y salvo específico.
     */
    PazYSalvoDetalleDTO obtenerDetalle(Long idPazSalvo);

    /**
     * Descarga el documento oficial PDF aplicando controles anti-IDOR por rol/tenant.
     */
    Resource descargarPdf(Long idPazSalvo);

    /**
     * Verifica la validez y autenticidad de un certificado mediante su código único.
     */
    Map<String, Object> verificarPorCodigo(String codigo);
}
