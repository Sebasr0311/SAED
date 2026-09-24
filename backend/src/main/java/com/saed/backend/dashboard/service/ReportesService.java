package com.saed.backend.dashboard.service;

import com.saed.backend.dashboard.dto.CarteraMorosaDTO;
import com.saed.backend.dashboard.dto.EjecucionCuotasDTO;
import com.saed.backend.dashboard.dto.EjecucionPresupuestalGlobalDTO;
import com.saed.backend.dashboard.dto.PagoRecienteDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * Servicio de reportes operativos y financieros base para SAED 2.0.
 * Aplica aislamiento estricto por tenant (propiedad u organización), validación temporal y paginación acotada.
 */
public interface ReportesService {

    /**
     * Reporte detallado de cartera morosa agrupada por unidad.
     *
     * @param propertyId  ID de propiedad opcional (solo permitido para ADMIN_ORGANIZACION perteneciente a su org).
     * @param fechaInicio Filtro opcional de fecha de vencimiento mínima.
     * @param fechaFin    Filtro opcional de fecha de vencimiento máxima.
     * @param page        Número de página (0-indexed).
     * @param size        Tamaño de página (máximo 200).
     * @return Lista de unidades morosas con saldo y cálculo determinista de días de mora.
     */
    List<CarteraMorosaDTO> getCarteraMorosa(Long propertyId, LocalDate fechaInicio, LocalDate fechaFin, int page, int size);

    /**
     * Reporte consolidado de facturación y recaudo de cuotas por periodo.
     *
     * @param propertyId    ID de propiedad opcional.
     * @param fechaInicio   Filtro opcional de fecha mínima para derivar periodo.
     * @param fechaFin      Filtro opcional de fecha máxima para derivar periodo.
     * @param periodoInicio Filtro opcional de periodo inicial (YYYY-MM).
     * @param periodoFin    Filtro opcional de periodo final (YYYY-MM).
     * @return Lista de periodos con totales facturados, pendientes, recaudados y % de cumplimiento.
     */
    List<EjecucionCuotasDTO> getEjecucionCuotas(Long propertyId, LocalDate fechaInicio, LocalDate fechaFin, String periodoInicio, String periodoFin);

    /**
     * Reporte detallado de pagos recientes registrados.
     *
     * @param propertyId  ID de propiedad opcional.
     * @param fechaInicio Filtro opcional de fecha de pago mínima.
     * @param fechaFin    Filtro opcional de fecha de pago máxima.
     * @param page        Número de página (0-indexed).
     * @param size        Tamaño de página (máximo 200).
     * @return Lista de pagos ordenados descendentemente por fecha.
     */
    List<PagoRecienteDTO> getPagosRecientes(Long propertyId, LocalDate fechaInicio, LocalDate fechaFin, int page, int size);

    /**
     * Reporte de ejecución presupuestal global (ingresos y egresos consolidados).
     *
     * @param propertyId   ID de propiedad opcional.
     * @param vigenciaAnio Año fiscal / vigencia a evaluar (por defecto el año en curso).
     * @return Resumen consolidado con ingresos/egresos presupuestados vs ejecutados (recaudo efectivo aprobado).
     */
    EjecucionPresupuestalGlobalDTO getEjecucionPresupuestal(Long propertyId, Integer vigenciaAnio);
}
