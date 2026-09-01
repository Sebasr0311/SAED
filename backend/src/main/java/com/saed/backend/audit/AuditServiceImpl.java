package com.saed.backend.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);
    
    /**
     * Allowed values strictly mandated by Oracle constraint CK_AUDITORIA_ACCION:
     * accion IN ('LOGIN', 'LOGOUT', 'INSERT', 'UPDATE', 'DELETE', 'QR_SCAN',
     *            'ACCESO_CONCEDIDO', 'ACCESO_DENEGADO', 'PAGO', 'CAMBIO_CONFIGURACION',
     *            'CAMBIO_ROL', 'EXPORTACION_REPORTE', 'EJECUCION_REGLA')
     */
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "LOGIN", "LOGOUT", "INSERT", "UPDATE", "DELETE", "QR_SCAN",
            "ACCESO_CONCEDIDO", "ACCESO_DENEGADO", "PAGO", "CAMBIO_CONFIGURACION",
            "CAMBIO_ROL", "EXPORTACION_REPORTE", "EJECUCION_REGLA"
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditServiceImpl(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(AuditEntry entry) {
        if (entry == null) {
            return;
        }

        String rawAction = entry.getAccion();
        String normalizedAction = normalizeAction(rawAction);
        String safeEstadoAnterior = ensureValidJson(entry.getEstadoAnterior());
        String safeEstadoNuevo = ensureValidJson(entry.getEstadoNuevo());

        String sql = "INSERT INTO AUDITORIA_LOG (" +
                "ID_USUARIO, ID_ORGANIZACION, ID_PROPIEDAD, ACCION, ENTIDAD, " +
                "ID_ENTIDAD_AFECTADA, IP_ORIGEN, USER_AGENT, RESULTADO, " +
                "ESTADO_ANTERIOR, ESTADO_NUEVO, FECHA_HORA" +
                ") VALUES (" +
                ":idUsuario, :idOrganizacion, :idPropiedad, :accion, :entidad, " +
                ":idEntidadAfectada, :ipOrigen, :userAgent, :resultado, " +
                ":estadoAnterior, :estadoNuevo, SYSTIMESTAMP)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("idUsuario", entry.getIdUsuario())
                .addValue("idOrganizacion", entry.getIdOrganizacion())
                .addValue("idPropiedad", entry.getIdPropiedad())
                .addValue("accion", normalizedAction)
                .addValue("entidad", entry.getEntidad() != null ? entry.getEntidad() : "GENERAL")
                .addValue("idEntidadAfectada", entry.getIdEntidadAfectada())
                .addValue("ipOrigen", entry.getIpOrigen())
                .addValue("userAgent", entry.getUserAgent() != null && entry.getUserAgent().length() > 500
                        ? entry.getUserAgent().substring(0, 500) : entry.getUserAgent())
                .addValue("resultado", entry.getResultado() != null ? entry.getResultado() : "EXITOSO")
                .addValue("estadoAnterior", safeEstadoAnterior)
                .addValue("estadoNuevo", safeEstadoNuevo);

        try {
            jdbcTemplate.update(sql, params);
            log.debug("Audit record persisted: originalAction={}, normalizedAction={}, entity={}, id={}, result={}",
                    rawAction, normalizedAction, entry.getEntidad(), entry.getIdEntidadAfectada(), entry.getResultado());
        } catch (Exception ex) {
            log.error("Failed to persist audit log entry: originalAction={}, normalizedAction={}, entity={}, id={}, result={}, error={}",
                    rawAction, normalizedAction, entry.getEntidad(), entry.getIdEntidadAfectada(), entry.getResultado(), ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long idUsuario, Long idOrganizacion, Long idPropiedad,
                              String accion, String entidad, Long idEntidadAfectada,
                              String ipOrigen, String userAgent,
                              String estadoAnterior, String estadoNuevo) {
        AuditEntry entry = new AuditEntry();
        entry.setIdUsuario(idUsuario);
        entry.setIdOrganizacion(idOrganizacion);
        entry.setIdPropiedad(idPropiedad);
        entry.setAccion(accion);
        entry.setEntidad(entidad);
        entry.setIdEntidadAfectada(idEntidadAfectada);
        entry.setIpOrigen(ipOrigen);
        entry.setUserAgent(userAgent);
        entry.setResultado("EXITOSO");
        entry.setEstadoAnterior(estadoAnterior);
        entry.setEstadoNuevo(estadoNuevo);
        recordEvent(entry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long idUsuario, Long idOrganizacion, Long idPropiedad,
                              String accion, String entidad, Long idEntidadAfectada,
                              String ipOrigen, String userAgent,
                              String estadoAnterior, String errorReason) {
        AuditEntry entry = new AuditEntry();
        entry.setIdUsuario(idUsuario);
        entry.setIdOrganizacion(idOrganizacion);
        entry.setIdPropiedad(idPropiedad);
        entry.setAccion(accion);
        entry.setEntidad(entidad);
        entry.setIdEntidadAfectada(idEntidadAfectada);
        entry.setIpOrigen(ipOrigen);
        entry.setUserAgent(userAgent);
        entry.setResultado("FALLIDO");
        entry.setEstadoAnterior(estadoAnterior);
        entry.setEstadoNuevo(errorReason);
        recordEvent(entry);
    }

    /**
     * Normalizes arbitrary action strings to the exact allowed Oracle CK_AUDITORIA_ACCION catalog.
     */
    public String normalizeAction(String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            return "UPDATE";
        }
        String upper = rawAction.toUpperCase().trim();
        if (ALLOWED_ACTIONS.contains(upper)) {
            return upper;
        }

        // Semantic prefix / keyword mapping
        if (upper.contains("CREATE") || upper.contains("INSERT") || upper.contains("REGISTER") 
                || upper.contains("SOLICITAR") || upper.contains("GUARDAR") || upper.contains("EMITIR") || upper.contains("GENERAR")) {
            return "INSERT";
        }
        if (upper.contains("DELETE") || upper.contains("REMOVE") || upper.contains("CANCEL") 
                || upper.contains("ELIMINAR") || upper.contains("ANULAR") || upper.contains("REVOCAR")) {
            return "DELETE";
        }
        if (upper.contains("PAGO") || upper.contains("PAY") || upper.contains("WEBHOOK") 
                || upper.contains("INTENCION") || upper.contains("CONCILIAR")) {
            return "PAGO";
        }
        if (upper.contains("ROL") || upper.contains("ASIGNACION") || upper.contains("RESIDENTE") 
                || upper.contains("OWNER") || upper.contains("PROPIETARIO") || upper.contains("HABITANTE")) {
            return "CAMBIO_ROL";
        }
        if (upper.contains("CHECKIN") || upper.contains("CHECKOUT") || upper.contains("ACCESO") 
                || upper.contains("VISITA") || upper.contains("PORTERIA")) {
            return "ACCESO_CONCEDIDO";
        }
        if (upper.contains("QR")) {
            return "QR_SCAN";
        }
        if (upper.contains("CONFIG") || upper.contains("STATUS") || upper.contains("ESTADO") 
                || upper.contains("PRIORIDAD") || upper.contains("BLOQUEAR") || upper.contains("DESBLOQUEAR") 
                || upper.contains("PASSWORD") || upper.contains("CONTRASENA") || upper.contains("APROBAR") 
                || upper.contains("RECHAZAR") || upper.contains("CERRAR") || upper.contains("RESOLVER") 
                || upper.contains("RESPONDER") || upper.contains("UPDATE") || upper.contains("EDIT") || upper.contains("MODIFICAR")) {
            return "UPDATE";
        }
        if (upper.contains("REPORTE") || upper.contains("EXPORT") || upper.contains("DESCARGAR")) {
            return "EXPORTACION_REPORTE";
        }
        if (upper.contains("REGLA") || upper.contains("CARTERA") || upper.contains("RECALCULAR") 
                || upper.contains("JOB") || upper.contains("AUTOMATIZACION")) {
            return "EJECUCION_REGLA";
        }
        
        return "UPDATE";
    }

    /**
     * Ensures any string payload fulfills Oracle's IS JSON constraint (Object or Array).
     * Prevents double-serialization when the string is already a valid JSON Object or Array.
     */
    public String ensureValidJson(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                objectMapper.readTree(trimmed);
                return trimmed; // Already valid JSON object/array, return without re-wrapping
            } catch (Exception ignored) {
            }
        }
        try {
            return objectMapper.writeValueAsString(Map.of("message", trimmed));
        } catch (Exception e) {
            return "{\"message\":\"" + trimmed.replace("\"", "\\\"") + "\"}";
        }
    }
}
