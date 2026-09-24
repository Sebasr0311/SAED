package com.saed.backend.incidentes.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.incidentes.dto.*;
import com.saed.backend.incidentes.exception.IncidenteInvalidStateException;
import com.saed.backend.incidentes.exception.IncidenteInvalidTransitionException;
import com.saed.backend.incidentes.repository.IncidenteRepository;
import com.saed.backend.incidentes.service.IncidenteService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;

@Service
public class IncidenteServiceImpl implements IncidenteService {

    private static final Set<String> ESTADOS_VALIDOS = Set.of(
            "REPORTADO",
            "EN_INVESTIGACION",
            "ACCION_TOMADA",
            "ESCALADO_A_SANCION",
            "CERRADO"
    );

    private static final Set<String> ROLES_INVOLUCRADO = Set.of(
            "AFECTADO",
            "PRESUNTO_INFRACTOR",
            "TESTIGO",
            "INFORMADOR",
            "OTRO"
    );

    private final IncidenteRepository incidenteRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public IncidenteServiceImpl(IncidenteRepository incidenteRepository, NamedParameterJdbcTemplate jdbcTemplate) {
        this.incidenteRepository = incidenteRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    private void validarEstadoValido(String estado) {
        if (estado == null || !ESTADOS_VALIDOS.contains(estado.trim().toUpperCase())) {
            throw new IncidenteInvalidStateException(estado);
        }
    }

    private void validarTransicion(String origen, String destino) {
        validarEstadoValido(origen);
        validarEstadoValido(destino);

        String o = origen.trim().toUpperCase();
        String d = destino.trim().toUpperCase();

        if (o.equals(d)) {
            return; // Idempotente
        }

        boolean permitida = false;
        switch (o) {
            case "REPORTADO":
                permitida = Set.of("EN_INVESTIGACION", "ACCION_TOMADA", "ESCALADO_A_SANCION", "CERRADO").contains(d);
                break;
            case "EN_INVESTIGACION":
                permitida = Set.of("ACCION_TOMADA", "ESCALADO_A_SANCION", "CERRADO").contains(d);
                break;
            case "ACCION_TOMADA":
                permitida = Set.of("EN_INVESTIGACION", "ESCALADO_A_SANCION", "CERRADO").contains(d);
                break;
            case "ESCALADO_A_SANCION":
                permitida = Set.of("EN_INVESTIGACION", "ACCION_TOMADA", "CERRADO").contains(d);
                break;
            case "CERRADO":
                // Solo reapertura a EN_INVESTIGACION
                permitida = "EN_INVESTIGACION".equals(d);
                break;
            default:
                permitida = false;
        }

        if (!permitida) {
            throw new IncidenteInvalidTransitionException(o, d);
        }
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
        boolean isStaff = "ADMIN_PROPIEDAD".equals(roleCode) || "PORTERO".equals(roleCode) || "SUPERADMIN".equals(roleCode);
        
        if (!isStaff) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null || !userUnitId.equals(incidente.getIdUnidad())) {
                throw new AccessDeniedException("Acceso denegado: el incidente no pertenece a su unidad");
            }
        }
        
        // Cargar personas involucradas
        incidente.setInvolucrados(incidenteRepository.findInvolucradosByIncidente(idIncidente));
        return incidente;
    }

    @Override
    @Auditable(action = "CREATE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.WARN)
    public Long reportarIncidente(IncidenteDTO request) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long registradoPor = SaedContextHolder.getContext().getUserId();
        String roleCode = SaedContextHolder.getContext().getRoleCode();
        
        boolean isStaff = "ADMIN_PROPIEDAD".equals(roleCode) || "PORTERO".equals(roleCode) || "SUPERADMIN".equals(roleCode);
        
        if (!isStaff) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null) {
                throw new AccessDeniedException("Usuario no tiene unidad asignada");
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
                    Map.of("enlace", "UNIDAD:" + request.getIdUnidad(), "idApto", request.getIdUnidad()),
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

        Long idIncidente = incidenteRepository.createIncidente(request, idPropiedad, registradoPor);

        // Si se enviaron involucrados en el registro inicial
        if (request.getInvolucrados() != null && !request.getInvolucrados().isEmpty()) {
            for (IncidenteInvolucradoDTO inv : request.getInvolucrados()) {
                try {
                    addInvolucrado(idIncidente, inv);
                } catch (Exception e) {
                    // Log o continuar
                }
            }
        }

        return idIncidente;
    }

    @Override
    @Auditable(action = "UPDATE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.WARN)
    public void cambiarEstado(Long idIncidente, IncidenteEstadoRequestDTO dto) {
        if (dto == null || dto.getEstado() == null || dto.getEstado().isBlank()) {
            throw new IllegalArgumentException("El estado destino es obligatorio");
        }

        String targetState = dto.getEstado().trim().toUpperCase();
        validarEstadoValido(targetState);

        IncidenteDTO incidente = getIncidenteById(idIncidente);
        validarTransicion(incidente.getEstado(), targetState);

        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long userId = SaedContextHolder.getContext().getUserId();

        switch (targetState) {
            case "EN_INVESTIGACION":
                incidenteRepository.iniciarInvestigacion(idIncidente, idPropiedad, userId);
                break;
            case "ESCALADO_A_SANCION":
                incidenteRepository.escalarIncidente(idIncidente, idPropiedad, userId,
                        dto.getMotivo() != null ? dto.getMotivo() : "Escalado a sanción", null);
                break;
            case "CERRADO":
                incidenteRepository.updateEstado(idIncidente, idPropiedad, "CERRADO", dto.getConclusiones());
                break;
            default:
                incidenteRepository.updateEstado(idIncidente, idPropiedad, targetState, dto.getConclusiones());
                break;
        }
    }

    @Override
    @Auditable(action = "UPDATE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.WARN)
    public void iniciarInvestigacion(Long idIncidente) {
        IncidenteDTO incidente = getIncidenteById(idIncidente);
        validarTransicion(incidente.getEstado(), "EN_INVESTIGACION");

        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long investigadoPor = SaedContextHolder.getContext().getUserId();
        incidenteRepository.iniciarInvestigacion(idIncidente, idPropiedad, investigadoPor);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public void actualizarInvestigacion(Long idIncidente, IncidenteInvestigacionRequestDTO dto) {
        IncidenteDTO incidente = getIncidenteById(idIncidente);
        if ("CERRADO".equals(incidente.getEstado())) {
            throw new IncidenteInvalidTransitionException("CERRADO", "EN_INVESTIGACION", "No se pueden modificar hallazgos en un incidente cerrado");
        }

        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        incidenteRepository.actualizarInvestigacion(idIncidente, idPropiedad, dto != null ? dto.getHallazgos() : null);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public void concluirInvestigacion(Long idIncidente, IncidenteInvestigacionRequestDTO dto) {
        IncidenteDTO incidente = getIncidenteById(idIncidente);
        String dest = (dto != null && dto.getEstadoDestino() != null && !dto.getEstadoDestino().isBlank())
                ? dto.getEstadoDestino().trim().toUpperCase() : "ACCION_TOMADA";

        validarTransicion(incidente.getEstado(), dest);

        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        String hallazgos = dto != null ? dto.getHallazgos() : null;
        incidenteRepository.concluirInvestigacion(idIncidente, idPropiedad, hallazgos, dest);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public void escalarIncidente(Long idIncidente, IncidenteEscalamientoRequestDTO dto) {
        if (dto == null || dto.getMotivo() == null || dto.getMotivo().isBlank()) {
            throw new IllegalArgumentException("El motivo de escalamiento es obligatorio");
        }

        IncidenteDTO incidente = getIncidenteById(idIncidente);
        validarTransicion(incidente.getEstado(), "ESCALADO_A_SANCION");

        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long escaladoPor = SaedContextHolder.getContext().getUserId();
        incidenteRepository.escalarIncidente(idIncidente, idPropiedad, escaladoPor, dto.getMotivo(), dto.getSancionSugerida());
    }

    @Override
    @Auditable(action = "CLOSE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public void cerrarIncidente(Long idIncidente, String conclusiones) {
        IncidenteDTO incidente = getIncidenteById(idIncidente);
        validarTransicion(incidente.getEstado(), "CERRADO");

        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        incidenteRepository.updateEstado(idIncidente, idPropiedad, "CERRADO", conclusiones);
    }

    @Override
    public List<IncidenteInvolucradoDTO> getInvolucrados(Long idIncidente) {
        // Valida acceso
        getIncidenteById(idIncidente);
        return incidenteRepository.findInvolucradosByIncidente(idIncidente);
    }

    @Override
    @Auditable(action = "CREATE", resource = "INCIDENTE_INVOLUCRADO", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public Long addInvolucrado(Long idIncidente, IncidenteInvolucradoDTO dto) {
        // Valida que el incidente exista y el usuario tenga acceso
        IncidenteDTO incidente = getIncidenteById(idIncidente);
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();

        if (dto == null || dto.getRolEnIncidente() == null || dto.getRolEnIncidente().isBlank()) {
            throw new IllegalArgumentException("El rol en el incidente es obligatorio");
        }

        String rol = dto.getRolEnIncidente().trim().toUpperCase();
        if (!ROLES_INVOLUCRADO.contains(rol)) {
            throw new IllegalArgumentException("Rol en incidente no válido: '" + rol + "'. Permitidos: " + ROLES_INVOLUCRADO);
        }
        dto.setRolEnIncidente(rol);

        // Anti-IDOR validation: si se envía persona, debe pertenecer a la copropiedad u organización
        if (dto.getIdPersona() != null) {
            boolean pertenece = incidenteRepository.isPersonaInPropiedad(dto.getIdPersona(), idPropiedad);
            if (!pertenece) {
                throw new AccessDeniedException("La persona seleccionada no pertenece a la copropiedad actual.");
            }
        } else if (dto.getIdVehiculo() == null) {
            // Si no hay persona ni vehículo registrados, debe haber identificación externa
            if (dto.getNombreIdentificacionExterna() == null || dto.getNombreIdentificacionExterna().isBlank()) {
                throw new IllegalArgumentException("Debe especificar una persona registrada, un vehículo o la identificación externa del involucrado.");
            }
        }

        return incidenteRepository.addInvolucrado(idIncidente, dto);
    }

    @Override
    @Auditable(action = "DELETE", resource = "INCIDENTE_INVOLUCRADO", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public void removeInvolucrado(Long idIncidente, Long idInvolucrado) {
        // Valida acceso
        getIncidenteById(idIncidente);
        incidenteRepository.removeInvolucrado(idIncidente, idInvolucrado);
    }
}
