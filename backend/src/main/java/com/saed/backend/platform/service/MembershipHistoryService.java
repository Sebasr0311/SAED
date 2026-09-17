package com.saed.backend.platform.service;

import java.util.List;
import java.util.Map;

/**
 * Servicio centralizado para la trazabilidad inmutable del ciclo de vida de membresías (GAP-ENT-05).
 * Registra eventos atómicos en MEMBRESIAS_HISTORIAL garantizando cumplimiento de restricciones Oracle
 * (CK_MEMBHIST_TIPO, FK_MEMBHIST_*, TRG_MEMBHIST_IMMUTABLE).
 */
public interface MembershipHistoryService {

    /**
     * Registra una transición de estado o plan en MEMBRESIAS_HISTORIAL de forma atómica.
     *
     * @param idMembresia     Identificador de la membresía modificada (requerido)
     * @param idPlanAnterior  Identificador del plan previo (puede ser null en INICIO)
     * @param idPlanNuevo     Identificador del plan nuevo (requerido)
     * @param tipoCambio      Tipo de cambio según CK_MEMBHIST_TIPO (INICIO, UPGRADE, DOWNGRADE, RENOVACION, CANCELACION, SUSPENSION, REACTIVACION)
     * @param observaciones   Descripción o referencia de la operación (opcional)
     * @param realizadoPor    ID del usuario que ejecutó la acción (FK a USUARIOS.ID_USUARIO, null para eventos de sistema/webhook)
     */
    void recordChange(Long idMembresia, Long idPlanAnterior, Long idPlanNuevo, String tipoCambio, String observaciones, Long realizadoPor);

    /**
     * Consulta el historial cronológico de una membresía específica.
     *
     * @param idMembresia Identificador de la membresía
     * @return Lista de registros del historial ordenados descendentemente por fecha
     */
    List<Map<String, Object>> getHistoryForMembership(Long idMembresia);

    /**
     * Consulta el historial cronológico de todas las membresías pertenecientes a una organización.
     *
     * @param idOrganizacion Identificador de la organización
     * @return Lista de registros del historial ordenados descendentemente por fecha
     */
    List<Map<String, Object>> getHistoryForOrganization(Long idOrganizacion);
}
