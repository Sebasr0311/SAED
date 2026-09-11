package com.saed.backend.incidentes.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.incidentes.dto.IncidenteDTO;
import com.saed.backend.incidentes.repository.IncidenteRepository;
import com.saed.backend.incidentes.service.IncidenteService;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class IncidenteServiceImpl implements IncidenteService {

    private final IncidenteRepository incidenteRepository;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate;

    public IncidenteServiceImpl(IncidenteRepository incidenteRepository, org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate) {
        this.incidenteRepository = incidenteRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<IncidenteDTO> getAllIncidentes() {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        return incidenteRepository.findAllByPropiedad(idPropiedad);
    }

    @Override
    public List<IncidenteDTO> getMisIncidentes() {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long idUnidad = SaedContextHolder.getContext().getUnitId();
        if (idUnidad == null) {
            throw new SecurityException("Usuario no tiene unidad asignada");
        }
        return incidenteRepository.findAllByUnidad(idUnidad, idPropiedad);
    }

    @Override
    public IncidenteDTO getIncidenteById(Long idIncidente) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        IncidenteDTO incidente = incidenteRepository.findById(idIncidente, idPropiedad)
                .orElseThrow(() -> new IllegalArgumentException("Incidente no encontrado o acceso denegado"));
                
        String roleCode = SaedContextHolder.getContext().getRoleCode();
        boolean isStaff = "SUPERADMIN".equals(roleCode) || "ADMIN_PROPIEDAD".equals(roleCode) || "PORTERO".equals(roleCode);
        
        if (!isStaff) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null || !userUnitId.equals(incidente.getIdUnidad())) {
                throw new SecurityException("Acceso denegado: el incidente no pertenece a su unidad");
            }
        }
        
        return incidente;
    }

    @Override
    public Long reportarIncidente(IncidenteDTO request) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long registradoPor = SaedContextHolder.getContext().getUserId();
        String roleCode = SaedContextHolder.getContext().getRoleCode();
        
        boolean isStaff = "SUPERADMIN".equals(roleCode) || "ADMIN_PROPIEDAD".equals(roleCode) || "PORTERO".equals(roleCode);
        
        if (!isStaff) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null) {
                throw new SecurityException("Usuario no tiene unidad asignada");
            }
            // Strict IDOR prevention: force the unit ID from context for residents
            request.setIdUnidad(userUnitId);
            // Residents cannot report common area or security gate issues, only unit issues
            request.setIdZonaComun(null);
            request.setIdPorteria(null);
        }

        if (request.getTitulo() == null || request.getTitulo().isBlank()) {
            throw new IllegalArgumentException("El titulo es obligatorio");
        }
        
        if (request.getTipoIncidente() == null || request.getTipoIncidente().isBlank()) {
            throw new IllegalArgumentException("El tipo de incidente es obligatorio");
        }

        if (request.getDescripcionHechos() == null || request.getDescripcionHechos().isBlank()) {
            throw new IllegalArgumentException("La descripcion de los hechos es obligatoria");
        }

        if (request.getFechaHoraIncidente() == null) {
            request.setFechaHoraIncidente(ZonedDateTime.now());
        }

        if ("PORTERO".equals(roleCode) && request.getIdUnidad() != null &&
            (request.getTitulo().toLowerCase().contains("ruido") || 
             (request.getDescripcionHechos() != null && request.getDescripcionHechos().toLowerCase().contains("ruido")))) {
            String sql = "SELECT MAX(FECHA_ENVIO) AS ULTIMA_FECHA FROM NOTIFICACIONES " +
                         "WHERE TITULO = 'Aviso por Ruido' AND (" +
                         "  ENLACE_DESTINO = :enlace " +
                         "  OR ID_USUARIO_DESTINATARIO IN (" +
                         "    SELECT ua.ID_USUARIO FROM USUARIO_ASIGNACIONES ua WHERE ua.ID_UNIDAD = :idApto " +
                         "    UNION " +
                         "    SELECT u.ID_USUARIO FROM RESIDENTES_UNIDAD ru JOIN USUARIOS u ON ru.ID_PERSONA = u.ID_PERSONA WHERE ru.ID_UNIDAD = :idApto" +
                         "  )" +
                         ")";
            try {
                List<java.sql.Timestamp> list = jdbcTemplate.query(
                    sql,
                    java.util.Map.of("enlace", "UNIDAD:" + request.getIdUnidad(), "idApto", request.getIdUnidad()),
                    (rs, r) -> rs.getTimestamp("ULTIMA_FECHA")
                );
                java.sql.Timestamp ultimaFecha = (list != null && !list.isEmpty()) ? list.get(0) : null;
                if (ultimaFecha == null) {
                    throw new IllegalArgumentException("Debe realizar el aviso de ruido primero antes de poder reportar la infracción.");
                }
                long diffMillis = System.currentTimeMillis() - ultimaFecha.getTime();
                long minutos = Math.max(0, diffMillis / (60 * 1000));
                if (minutos < 30) {
                    throw new IllegalArgumentException("No puede aplicar la multa aún. Deben transcurrir al menos 30 minutos desde el aviso de ruido (faltan " + (30 - minutos) + " minutos).");
                }
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ignored) {}
        }

        return incidenteRepository.createIncidente(request, idPropiedad, registradoPor);
    }

    @Override
    @Auditable(action = "CLOSE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public void cerrarIncidente(Long idIncidente, String conclusiones) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        
        IncidenteDTO incidente = getIncidenteById(idIncidente);
        if ("CERRADO".equals(incidente.getEstado())) {
            throw new IllegalStateException("El incidente ya esta cerrado");
        }
        
        incidenteRepository.updateEstado(idIncidente, idPropiedad, "CERRADO", conclusiones);
    }
}
