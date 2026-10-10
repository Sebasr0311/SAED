package com.saed.backend.sanciones.service.impl;

import com.saed.backend.audit.AuditService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.sanciones.dto.*;
import com.saed.backend.sanciones.exception.ConceptoMultaNoConfiguradoException;
import com.saed.backend.sanciones.integration.FineReceivablesPort;
import com.saed.backend.sanciones.repository.SancionRepository;
import com.saed.backend.sanciones.service.SancionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class SancionServiceImpl implements SancionService {

    private static final Logger log = LoggerFactory.getLogger(SancionServiceImpl.class);

    private static final Set<String> CLOSED_STATES = Set.of("APLICADA", "ABSUELTA", "ANULADA");
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN_ORGANIZACION", "ADMIN_PROPIEDAD");
    private static final Set<String> PARTICIPANT_ROLES =
            Set.of("RESIDENTE", "PROPIETARIO", "PROPIETARIO_UNIDAD", "RESIDENTE_CONVIVENCIA");
    private static final String SUSPENSION_ZONAS_COMUNES = "SUSPENSION_ZONAS_COMUNES";

    private final SancionRepository sancionRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final EmailService emailService;
    private final AuditService auditService;
    private final Optional<FineReceivablesPort> fineReceivables;

    public SancionServiceImpl(SancionRepository sancionRepository,
                              NamedParameterJdbcTemplate jdbc,
                              EmailService emailService,
                              AuditService auditService,
                              Optional<FineReceivablesPort> fineReceivables) {
        this.sancionRepository = sancionRepository;
        this.jdbc = jdbc;
        this.emailService = emailService;
        this.auditService = auditService;
        this.fineReceivables = fineReceivables;
    }

    @Override
    public List<SancionDTO> getAllSanciones() {
        Long propId = resolvePropertyId();
        List<SancionDTO> lista = sancionRepository.findAllByPropiedad(propId);
        for (SancionDTO s : lista) {
            s.setDescargos(sancionRepository.findDescargosBySancion(s.getIdSancion()));
        }
        return lista;
    }

    /**
     * F10-09: administrators of the sanction's property, the accused person and the active owners or
     * residents of the sanctioned unit may read the file. Everyone else (other residents, porters,
     * SUPERADMIN per DEC-02) gets the same not-found answer as for a missing file.
     */
    @Override
    public SancionDTO getSancionById(Long id) {
        SancionDTO sancion = findSancionVisible(id);
        sancion.setDescargos(sancionRepository.findDescargosBySancion(id));
        return sancion;
    }

    @Override
    @Transactional
    public SancionDTO crearPliego(SancionCreateRequestDTO request) {
        String grav = request.getGravedad() != null ? request.getGravedad().toUpperCase().trim() : "LEVE";
        if (!Arrays.asList("LEVE", "GRAVE", "GRAVISIMA").contains(grav)) {
            throw new IllegalArgumentException("Nivel de gravedad inválido. Debe ser LEVE, GRAVE o GRAVISIMA.");
        }
        request.setGravedad(grav);

        // F10-15: the property always comes from the session; there is no default property fallback.
        Long propId = resolvePropertyId();
        if (propId == null) {
            throw new IllegalStateException("No hay una propiedad activa en el contexto de sesión");
        }
        Long propUnidad = sancionRepository.findPropiedadUnidad(request.getIdUnidad());
        if (!propId.equals(propUnidad)) {
            throw new IllegalArgumentException("La unidad indicada no pertenece a la propiedad activa");
        }
        if (!sancionRepository.personaVinculadaAUnidad(request.getIdPersonaImputada(), request.getIdUnidad())) {
            throw new IllegalArgumentException("La persona imputada no es propietaria ni residente activa de la unidad");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        Long idUsuarioCreador = ctx != null ? ctx.getUserId() : null;
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;

        String expediente = sancionRepository.generarSiguienteExpediente(propId);

        int dias = (request.getDiasParaDescargos() != null && request.getDiasParaDescargos() > 0)
                ? request.getDiasParaDescargos() : 10;
        LocalDate fechaLimite = LocalDate.now().plusDays(dias);

        Long idSancion = sancionRepository.crearSancion(request, propId, idUsuarioCreador, expediente, fechaLimite);

        auditService.recordSuccess(
                idUsuarioCreador, orgId, propId,
                "CREAR_SANCION", "SANCIONES", idSancion,
                "127.0.0.1", "SAED-Core", null, "NOTIFICADA"
        );

        notificarAperturaPliego(request.getIdPersonaImputada(), expediente, request.getTipoFalta(), fechaLimite);

        return getSancionById(idSancion);
    }

    /**
     * Only the accused person or an active owner/resident of the sanctioned unit may file a defense,
     * only while the file is open and before the discharge term expires.
     */
    @Override
    @Transactional
    public void radicarDescargos(Long idSancion, DescargoRequestDTO request) {
        SancionDTO sancion = sancionRepository.findById(idSancion)
                .orElseThrow(() -> notFound(idSancion));

        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null ? ctx.getUserId() : null;
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;

        Long idPersonaPresenta = userId != null ? sancionRepository.findPersonaUsuario(userId) : null;
        if (!esParticipante(sancion, idPersonaPresenta)) {
            throw notFound(idSancion);
        }

        if (CLOSED_STATES.contains(normalize(sancion.getEstado()))) {
            throw new IllegalStateException("No es posible radicar descargos: el expediente ya se encuentra cerrado con estado " + sancion.getEstado());
        }
        if (sancion.getFechaLimiteDescargos() != null && LocalDate.now().isAfter(sancion.getFechaLimiteDescargos())) {
            throw new IllegalStateException("El término para radicar descargos venció el " + sancion.getFechaLimiteDescargos());
        }

        sancionRepository.registrarDescargo(idSancion, idPersonaPresenta, userId, request.getDescargos(), request.getPruebasAdjuntasUrl());
        sancionRepository.actualizarEstado(idSancion, "EN_DESCARGOS");

        auditService.recordSuccess(
                userId, orgId, sancion.getIdPropiedad(),
                "RADICAR_DESCARGOS", "SANCION_DESCARGOS", idSancion,
                "127.0.0.1", "SAED-Core", sancion.getEstado(), "EN_DESCARGOS"
        );
    }

    /**
     * F10-04: a file is resolved only once, only while open, and not before the discharge term
     * expires unless a defense was filed. The state change is claimed with a conditional update
     * before any effect, so a repeated or concurrent resolution cannot apply a fine or restriction
     * twice (F10-06/F10-07 effects are applied exactly once).
     */
    @Override
    @Transactional
    public void emitirResolucion(Long idSancion, ResolucionRequestDTO request) {
        SancionDTO sancion = findSancionVisible(idSancion);

        String dec = request.getDecision().toUpperCase().trim();
        if (!Arrays.asList("APLICADA", "ABSUELTA", "ANULADA").contains(dec)) {
            throw new IllegalArgumentException("Decisión inválida. Debe ser APLICADA, ABSUELTA o ANULADA");
        }

        String estadoActual = normalize(sancion.getEstado());
        if (CLOSED_STATES.contains(estadoActual)) {
            throw new IllegalStateException("El expediente ya se encuentra cerrado con estado " + sancion.getEstado());
        }
        if ("NOTIFICADA".equals(estadoActual) && sancion.getFechaLimiteDescargos() != null
                && !LocalDate.now().isAfter(sancion.getFechaLimiteDescargos())) {
            throw new IllegalStateException("No se puede resolver antes del vencimiento del término de descargos ("
                    + sancion.getFechaLimiteDescargos() + ") si el imputado no ha radicado descargos");
        }

        boolean aplicaMulta = "APLICADA".equals(dec) && "MULTA_ECONOMICA".equalsIgnoreCase(sancion.getTipoSancionPropuesta());
        boolean aplicaRestriccion = "APLICADA".equals(dec) && SUSPENSION_ZONAS_COMUNES.equalsIgnoreCase(sancion.getTipoSancionPropuesta());

        Long idConcepto = null;
        if (aplicaMulta) {
            if (request.getMontoMulta() == null || request.getMontoMulta().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El monto de la multa es obligatorio y debe ser mayor a 0 cuando la resolución aplica una MULTA_ECONOMICA.");
            }
            idConcepto = sancionRepository.findConceptoMulta(sancion.getIdPropiedad())
                    .orElseThrow(() -> new ConceptoMultaNoConfiguradoException(
                            "CONCEPTO_MULTA_NO_CONFIGURADO: No existe un concepto de cobro activo de tipo MULTA configurado para la propiedad."));
        }

        LocalDate restriccionDesde = null;
        LocalDate restriccionHasta = null;
        if (aplicaRestriccion) {
            restriccionDesde = request.getRestriccionDesde() != null ? request.getRestriccionDesde() : LocalDate.now();
            restriccionHasta = request.getRestriccionHasta();
            if (restriccionHasta == null) {
                throw new IllegalArgumentException("La suspensión de zonas comunes requiere la fecha final de la restricción (restriccionHasta)");
            }
            if (restriccionHasta.isBefore(restriccionDesde)) {
                throw new IllegalArgumentException("La fecha final de la restricción no puede ser anterior a la fecha inicial");
            }
        }

        int claimed = sancionRepository.emitirResolucionSiAbierta(
                idSancion, dec, request.getResolucionFinal(), restriccionDesde, restriccionHasta);
        if (claimed == 0) {
            throw new IllegalStateException("El expediente ya fue resuelto por otra operación");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null ? ctx.getUserId() : null;
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;

        if (aplicaMulta) {
            aplicarMulta(sancion, idConcepto, request, userId);
        }

        auditService.recordSuccess(
                userId, orgId, sancion.getIdPropiedad(),
                "EMITIR_RESOLUCION", "SANCIONES", idSancion,
                "127.0.0.1", "SAED-Core", sancion.getEstado(), dec
        );

        notificarResolucion(sancion.getIdPersonaImputada(), sancion.getNumeroExpediente(), dec, request.getResolucionFinal());
    }

    private void aplicarMulta(SancionDTO sancion, Long idConcepto, ResolucionRequestDTO request, Long userId) {
        Long idSancion = sancion.getIdSancion();
        if (sancionRepository.findMultaPorSancion(idSancion).isPresent()) {
            log.warn("La sanción {} ya tiene una multa registrada; no se crea otra", idSancion);
            return;
        }
        String motivo = "Resolución sancionatoria en firme (" + sancion.getNumeroExpediente() + "): " + request.getResolucionFinal();
        Long idMulta = sancionRepository.crearMultaDesdeSancion(
                idSancion, sancion.getIdUnidad(), sancion.getIdPersonaImputada(), idConcepto,
                request.getMontoMulta(), motivo, userId);

        if (fineReceivables.isEmpty()) {
            log.info("Multa {} de la sanción {} registrada en MULTAS; no hay integración de cartera configurada", idMulta, idSancion);
            return;
        }
        Optional<Long> idCuota = fineReceivables.get().postFineReceivable(new FineReceivablesPort.FineReceivable(
                idMulta, idSancion, sancion.getIdPropiedad(), sancion.getIdUnidad(), idConcepto,
                request.getMontoMulta(), "Multa expediente " + sancion.getNumeroExpediente()));
        idCuota.ifPresent(cuota -> sancionRepository.vincularCuotaMulta(idMulta, cuota));
    }

    @Override
    @Transactional
    public void anularSancion(Long idSancion, String motivo) {
        SancionDTO sancion = findSancionVisible(idSancion);

        String estadoActual = normalize(sancion.getEstado());
        if ("ANULADA".equals(estadoActual)) {
            return;
        }
        if ("ABSUELTA".equals(estadoActual)) {
            throw new IllegalStateException("No es posible anular un expediente disciplinario que ya fue absuelto.");
        }
        if ("APLICADA".equals(estadoActual)) {
            throw new IllegalStateException("No es posible anular directamente un expediente disciplinario con sanción en firme.");
        }

        String resolucion = (motivo != null && !motivo.isBlank()) ? motivo : "Expediente disciplinario anulado por la administración.";
        if (sancionRepository.emitirResolucionSiAbierta(idSancion, "ANULADA", resolucion, null, null) == 0) {
            throw new IllegalStateException("El expediente ya fue cerrado por otra operación");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null ? ctx.getUserId() : null;
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        auditService.recordSuccess(
                userId, orgId, sancion.getIdPropiedad(),
                "ANULAR_SANCION", "SANCIONES", idSancion,
                "127.0.0.1", "SAED-Core", sancion.getEstado(), "ANULADA"
        );
    }

    @Override
    public List<SancionDTO> getMisSanciones() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null ? ctx.getUserId() : null;
        if (userId == null) return Collections.emptyList();

        Long idPersona = sancionRepository.findPersonaUsuario(userId);
        if (idPersona == null) return Collections.emptyList();

        List<Long> unidades = sancionRepository.findUnidadesPersona(idPersona);
        Long propId = resolvePropertyId();
        List<SancionDTO> lista = sancionRepository.findByPersonaOUnidades(idPersona, unidades, propId);
        for (SancionDTO s : lista) {
            s.setDescargos(sancionRepository.findDescargosBySancion(s.getIdSancion()));
        }
        return lista;
    }

    @Override
    public boolean hasActiveCommonAreaRestriction(Long unitId, LocalDate date) {
        if (unitId == null || date == null) {
            return false;
        }
        return sancionRepository.existeRestriccionZonasComunes(unitId, date);
    }

    private SancionDTO findSancionVisible(Long idSancion) {
        SancionDTO sancion = sancionRepository.findById(idSancion).orElseThrow(() -> notFound(idSancion));
        SaedContext ctx = SaedContextHolder.getContext();
        String role = ctx != null && ctx.getRoleCode() != null ? ctx.getRoleCode().toUpperCase(Locale.ROOT) : "";

        if (ADMIN_ROLES.contains(role)) {
            if ("ADMIN_PROPIEDAD".equals(role) && ctx.getPropertyId() != null
                    && !ctx.getPropertyId().equals(sancion.getIdPropiedad())) {
                throw notFound(idSancion);
            }
            return sancion;
        }
        if (PARTICIPANT_ROLES.contains(role) && ctx.getUserId() != null
                && esParticipante(sancion, sancionRepository.findPersonaUsuario(ctx.getUserId()))) {
            return sancion;
        }
        throw notFound(idSancion);
    }

    private boolean esParticipante(SancionDTO sancion, Long idPersona) {
        if (idPersona == null) {
            return false;
        }
        return idPersona.equals(sancion.getIdPersonaImputada())
                || sancionRepository.personaVinculadaAUnidad(idPersona, sancion.getIdUnidad());
    }

    private static NoSuchElementException notFound(Long idSancion) {
        return new NoSuchElementException("Expediente sancionatorio no encontrado con ID: " + idSancion);
    }

    private static String normalize(String estado) {
        return estado == null ? "" : estado.toUpperCase(Locale.ROOT);
    }

    private Long resolvePropertyId() {
        SaedContext ctx = SaedContextHolder.getContext();
        return ctx != null ? ctx.getPropertyId() : null;
    }

    private void notificarAperturaPliego(Long idPersona, String expediente, String falta, LocalDate fechaLimite) {
        try {
            String sql = "SELECT EMAIL, PRIMER_NOMBRE FROM PERSONAS WHERE ID_PERSONA = :pId";
            List<Map<String, Object>> res = jdbc.queryForList(sql, new MapSqlParameterSource("pId", idPersona));
            if (!res.isEmpty() && res.get(0).get("EMAIL") != null) {
                String email = (String) res.get(0).get("EMAIL");
                String nombre = escape(res.get(0).get("PRIMER_NOMBRE"));
                String asunto = "Notificación de Pliego de Cargos - Expediente " + expediente;
                String html = "<p>Estimado/a <strong>" + nombre + "</strong>,</p>" +
                        "<p>Se le notifica que se ha abierto un expediente disciplinario (<strong>" + escape(expediente) + "</strong>) " +
                        "por la presunta falta: <em>" + escape(falta) + "</em>.</p>" +
                        "<p>De acuerdo con la Ley 675 y el reglamento de propiedad horizontal, cuenta hasta el <strong>" +
                        fechaLimite + "</strong> para presentar sus descargos en el portal SAED.</p>" +
                        "<p>Atentamente,<br/>Comité de Convivencia y Administración</p>";
                emailService.enviarHtmlPublico(email, asunto, html);
            }
        } catch (Exception e) {
            // Notification is best-effort and must not roll back the charge sheet.
            log.warn("No se pudo enviar email de pliego a persona {} ({})", idPersona, e.getClass().getSimpleName());
        }
    }

    private void notificarResolucion(Long idPersona, String expediente, String decision, String resolucionFinal) {
        try {
            String sql = "SELECT EMAIL, PRIMER_NOMBRE FROM PERSONAS WHERE ID_PERSONA = :pId";
            List<Map<String, Object>> res = jdbc.queryForList(sql, new MapSqlParameterSource("pId", idPersona));
            if (!res.isEmpty() && res.get(0).get("EMAIL") != null) {
                String email = (String) res.get(0).get("EMAIL");
                String nombre = escape(res.get(0).get("PRIMER_NOMBRE"));
                String asunto = "Resolución Final - Expediente " + expediente;
                String html = "<p>Estimado/a <strong>" + nombre + "</strong>,</p>" +
                        "<p>Se ha emitido decisión de fondo respecto al expediente <strong>" + escape(expediente) + "</strong>:</p>" +
                        "<p><strong>Decisión:</strong> " + escape(decision) + "</p>" +
                        "<p><strong>Resolución motivada:</strong><br/>" + escape(resolucionFinal) + "</p>" +
                        "<p>Atentamente,<br/>Consejo de Administración</p>";
                emailService.enviarHtmlPublico(email, asunto, html);
            }
        } catch (Exception e) {
            // Notification is best-effort and must not roll back the resolution.
            log.warn("No se pudo enviar email de resolución a persona {} ({})", idPersona, e.getClass().getSimpleName());
        }
    }

    private static String escape(Object value) {
        return value == null ? "" : HtmlUtils.htmlEscape(String.valueOf(value));
    }
}
