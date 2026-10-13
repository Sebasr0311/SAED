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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    private static final int MIN_MOTIVO_REAPERTURA = 10;
    private static final Set<String> STAFF_ROLES = Set.of("ADMIN_PROPIEDAD", "PORTERO");
    private static final Set<String> RESIDENT_ROLES = Set.of("RESIDENTE", "RESIDENTE_CONVIVENCIA");

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
                // Terminal for generic transitions: reopening needs a reason (reabrirIncidente, F9-03).
                permitida = false;
                break;
            default:
                permitida = false;
        }

        if (!permitida) {
            throw new IncidenteInvalidTransitionException(o, d);
        }
    }

    private static String currentRole() {
        return SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getRoleCode() : null;
    }

    private static boolean isStaff() {
        return STAFF_ROLES.contains(currentRole());
    }

    /** Staff and residents of the property only; SUPERADMIN has no operational access (DEC-02). */
    private static void requireIncidentReader() {
        if (!isStaff() && !RESIDENT_ROLES.contains(currentRole())) {
            throw new AccessDeniedException("Operación no permitida para el rol actual.");
        }
    }

    private static void requireStaff() {
        if (!isStaff()) {
            throw new AccessDeniedException("Operación reservada al personal de la copropiedad.");
        }
    }

    private static void requireAdmin() {
        if (!"ADMIN_PROPIEDAD".equals(currentRole())) {
            throw new AccessDeniedException("Operación reservada a la administración de la copropiedad.");
        }
    }

    /** Residents do not see investigation findings, suggested sanctions, police reports or third parties (F9-08). */
    private static IncidenteDTO vistaResidente(IncidenteDTO dto) {
        dto.setHallazgosInvestigacion(null);
        dto.setSancionSugerida(null);
        dto.setMotivoEscalamiento(null);
        dto.setNumeroDenunciaPolicia(null);
        dto.setInvestigadoPor(null);
        dto.setEscaladoPor(null);
        dto.setMotivoReapertura(null);
        dto.setReabiertoPor(null);
        dto.setInvolucrados(new ArrayList<>());
        return dto;
    }

    @Override
    public List<IncidenteDTO> getAllIncidentes() {
        requireStaff();
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        return incidenteRepository.findAllByPropiedad(idPropiedad);
    }

    @Override
    public List<IncidenteDTO> getMisIncidentes() {
        requireIncidentReader();
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long idUnidad = SaedContextHolder.getContext().getUnitId();
        if (idUnidad == null) {
            throw new AccessDeniedException("Usuario no tiene unidad asignada");
        }
        List<IncidenteDTO> list = incidenteRepository.findAllByUnidad(idUnidad, idPropiedad);
        return isStaff() ? list : list.stream().map(IncidenteServiceImpl::vistaResidente).toList();
    }

    @Override
    public IncidenteDTO getIncidenteById(Long idIncidente) {
        IncidenteDTO incidente = cargarIncidente(idIncidente);
        if (!isStaff()) {
            return vistaResidente(incidente);
        }
        incidente.setInvolucrados(incidenteRepository.findInvolucradosByIncidente(idIncidente));
        return incidente;
    }

    /** Loads an incident visible to the caller; missing, foreign-property and other-unit ids are all 404. */
    private IncidenteDTO cargarIncidente(Long idIncidente) {
        requireIncidentReader();
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        IncidenteDTO incidente = incidenteRepository.findById(idIncidente, idPropiedad)
                .orElseThrow(() -> new NoSuchElementException("Incidente no encontrado."));
        if (!isStaff()) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null || !userUnitId.equals(incidente.getIdUnidad())) {
                throw new NoSuchElementException("Incidente no encontrado.");
            }
        }
        return incidente;
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.WARN)
    public Long reportarIncidente(IncidenteDTO request) {
        requireIncidentReader();
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long registradoPor = SaedContextHolder.getContext().getUserId();
        String roleCode = SaedContextHolder.getContext().getRoleCode();

        if (!isStaff()) {
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
            // The noise-warning rule is enforced; a failing lookup aborts the report instead of skipping the rule.
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
        }

        Long idIncidente = incidenteRepository.createIncidente(request, idPropiedad, registradoPor);

        // Involved parties sent with the report are part of the same transaction: any invalid one aborts it.
        if (isStaff() && request.getInvolucrados() != null && !request.getInvolucrados().isEmpty()) {
            for (IncidenteInvolucradoDTO inv : request.getInvolucrados()) {
                addInvolucrado(idIncidente, inv);
            }
        }

        return idIncidente;
    }

    @Override
    @Auditable(action = "UPDATE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.WARN)
    public void cambiarEstado(Long idIncidente, IncidenteEstadoRequestDTO dto) {
        requireAdmin();
        if (dto == null || dto.getEstado() == null || dto.getEstado().isBlank()) {
            throw new IllegalArgumentException("El estado destino es obligatorio");
        }

        String targetState = dto.getEstado().trim().toUpperCase();
        validarEstadoValido(targetState);

        IncidenteDTO incidente = cargarIncidente(idIncidente);
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
        requireAdmin();
        IncidenteDTO incidente = cargarIncidente(idIncidente);
        validarTransicion(incidente.getEstado(), "EN_INVESTIGACION");

        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long investigadoPor = SaedContextHolder.getContext().getUserId();
        incidenteRepository.iniciarInvestigacion(idIncidente, idPropiedad, investigadoPor);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public void actualizarInvestigacion(Long idIncidente, IncidenteInvestigacionRequestDTO dto) {
        requireAdmin();
        IncidenteDTO incidente = cargarIncidente(idIncidente);
        if ("CERRADO".equals(incidente.getEstado())) {
            throw new IncidenteInvalidTransitionException("CERRADO", "EN_INVESTIGACION", "No se pueden modificar hallazgos en un incidente cerrado");
        }

        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        incidenteRepository.actualizarInvestigacion(idIncidente, idPropiedad, dto != null ? dto.getHallazgos() : null);
    }

    @Override
    @Auditable(action = "UPDATE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public void concluirInvestigacion(Long idIncidente, IncidenteInvestigacionRequestDTO dto) {
        requireAdmin();
        IncidenteDTO incidente = cargarIncidente(idIncidente);
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
        requireAdmin();
        if (dto == null || dto.getMotivo() == null || dto.getMotivo().isBlank()) {
            throw new IllegalArgumentException("El motivo de escalamiento es obligatorio");
        }

        IncidenteDTO incidente = cargarIncidente(idIncidente);
        validarTransicion(incidente.getEstado(), "ESCALADO_A_SANCION");

        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long escaladoPor = SaedContextHolder.getContext().getUserId();
        incidenteRepository.escalarIncidente(idIncidente, idPropiedad, escaladoPor, dto.getMotivo(), dto.getSancionSugerida());
    }

    @Override
    @Auditable(action = "CLOSE", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public void cerrarIncidente(Long idIncidente, String conclusiones) {
        requireAdmin();
        IncidenteDTO incidente = cargarIncidente(idIncidente);
        validarTransicion(incidente.getEstado(), "CERRADO");

        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        incidenteRepository.updateEstado(idIncidente, idPropiedad, "CERRADO", conclusiones);
    }

    @Override
    @Transactional
    @Auditable(action = "REOPEN", resource = "INCIDENTE", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public void reabrirIncidente(Long idIncidente, String motivo) {
        requireAdmin();
        String reason = motivo != null ? motivo.trim() : "";
        if (reason.length() < MIN_MOTIVO_REAPERTURA) {
            throw new IllegalArgumentException("El motivo de reapertura es obligatorio (mínimo "
                    + MIN_MOTIVO_REAPERTURA + " caracteres).");
        }
        if (reason.length() > 1000) {
            throw new IllegalArgumentException("El motivo de reapertura no puede superar 1000 caracteres.");
        }
        IncidenteDTO incidente = cargarIncidente(idIncidente);
        if (!"CERRADO".equals(incidente.getEstado())) {
            throw new IncidenteInvalidTransitionException(incidente.getEstado(), "EN_INVESTIGACION",
                    "Solo se puede reabrir un incidente cerrado.");
        }
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long userId = SaedContextHolder.getContext().getUserId();
        if (incidenteRepository.reabrir(idIncidente, idPropiedad, userId, reason) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El incidente cambió de estado mientras se reabría; recargue e intente de nuevo.");
        }
    }

    @Override
    public List<IncidenteInvolucradoDTO> getInvolucrados(Long idIncidente) {
        // Third parties are staff-only information (F9-08)
        requireStaff();
        cargarIncidente(idIncidente);
        return incidenteRepository.findInvolucradosByIncidente(idIncidente);
    }

    @Override
    @Auditable(action = "CREATE", resource = "INCIDENTE_INVOLUCRADO", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public Long addInvolucrado(Long idIncidente, IncidenteInvolucradoDTO dto) {
        requireStaff();
        cargarIncidente(idIncidente);
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
        requireAdmin();
        cargarIncidente(idIncidente);
        incidenteRepository.removeInvolucrado(idIncidente, idInvolucrado);
    }
}
