package com.saed.backend.sanciones.service.impl;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.sanciones.dto.*;
import com.saed.backend.sanciones.repository.SancionRepository;
import com.saed.backend.sanciones.service.SancionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class SancionServiceImpl implements SancionService {

    private static final Logger log = LoggerFactory.getLogger(SancionServiceImpl.class);

    private final SancionRepository sancionRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final EmailService emailService;

    public SancionServiceImpl(SancionRepository sancionRepository,
                              NamedParameterJdbcTemplate jdbc,
                              EmailService emailService) {
        this.sancionRepository = sancionRepository;
        this.jdbc = jdbc;
        this.emailService = emailService;
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

    @Override
    public SancionDTO getSancionById(Long id) {
        SancionDTO sancion = sancionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expediente sancionatorio no encontrado con ID: " + id));
        sancion.setDescargos(sancionRepository.findDescargosBySancion(id));
        return sancion;
    }

    @Override
    @Transactional
    public SancionDTO crearPliego(SancionCreateRequestDTO request) {
        Long propId = resolvePropertyId();
        if (propId == null) {
            // Resolver desde la unidad
            String propSql = "SELECT ID_PROPIEDAD FROM UNIDADES WHERE ID_UNIDAD = :uId";
            List<Long> pList = jdbc.query(propSql, new MapSqlParameterSource("uId", request.getIdUnidad()), (rs, i) -> rs.getLong("ID_PROPIEDAD"));
            if (!pList.isEmpty()) {
                propId = pList.get(0);
            } else {
                propId = 1L; // Fallback predeterminado
            }
        }

        SaedContext ctx = SaedContextHolder.getContext();
        Long idUsuarioCreador = ctx != null ? ctx.getUserId() : null;

        String expediente = sancionRepository.generarSiguienteExpediente(propId);

        int dias = (request.getDiasParaDescargos() != null && request.getDiasParaDescargos() > 0)
                ? request.getDiasParaDescargos() : 10;
        LocalDate fechaLimite = LocalDate.now().plusDays(dias);

        Long idSancion = sancionRepository.crearSancion(request, propId, idUsuarioCreador, expediente, fechaLimite);

        // Notificación por email al imputado (best-effort)
        notificarAperturaPliego(request.getIdPersonaImputada(), expediente, request.getTipoFalta(), fechaLimite);

        return getSancionById(idSancion);
    }

    @Override
    @Transactional
    public void radicarDescargos(Long idSancion, DescargoRequestDTO request) {
        SancionDTO sancion = sancionRepository.findById(idSancion)
                .orElseThrow(() -> new IllegalArgumentException("Expediente sancionatorio no encontrado"));

        if ("APLICADA".equalsIgnoreCase(sancion.getEstado()) || "ABSUELTA".equalsIgnoreCase(sancion.getEstado()) || "ANULADA".equalsIgnoreCase(sancion.getEstado())) {
            throw new IllegalStateException("No es posible radicar descargos: el expediente ya se encuentra cerrado con estado " + sancion.getEstado());
        }

        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null ? ctx.getUserId() : null;

        Long idPersonaPresenta = null;
        if (userId != null) {
            String pSql = "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :uId";
            List<Long> pList = jdbc.query(pSql, new MapSqlParameterSource("uId", userId), (rs, i) -> rs.getLong("ID_PERSONA"));
            if (!pList.isEmpty()) {
                idPersonaPresenta = pList.get(0);
            }
        }
        if (idPersonaPresenta == null) {
            idPersonaPresenta = sancion.getIdPersonaImputada();
        }

        sancionRepository.registrarDescargo(idSancion, idPersonaPresenta, userId, request.getDescargos(), request.getPruebasAdjuntasUrl());
        sancionRepository.actualizarEstado(idSancion, "EN_DESCARGOS");
    }

    @Override
    @Transactional
    public void emitirResolucion(Long idSancion, ResolucionRequestDTO request) {
        SancionDTO sancion = sancionRepository.findById(idSancion)
                .orElseThrow(() -> new IllegalArgumentException("Expediente sancionatorio no encontrado"));

        String dec = request.getDecision().toUpperCase().trim();
        if (!Arrays.asList("APLICADA", "ABSUELTA", "ANULADA").contains(dec)) {
            throw new IllegalArgumentException("Decisión inválida. Debe ser APLICADA, ABSUELTA o ANULADA");
        }

        sancionRepository.emitirResolucion(idSancion, dec, request.getResolucionFinal());

        // Si la sanción es aplicada y contempla multa económica, generar el registro financiero
        if ("APLICADA".equals(dec) && "MULTA_ECONOMICA".equalsIgnoreCase(sancion.getTipoSancionPropuesta())) {
            try {
                Optional<Long> idConceptoOpt = sancionRepository.findConceptoMulta(sancion.getIdPropiedad());
                if (idConceptoOpt.isPresent()) {
                    SaedContext ctx = SaedContextHolder.getContext();
                    Long idUsuario = ctx != null ? ctx.getUserId() : null;
                    sancionRepository.crearMultaDesdeSancion(
                            idSancion,
                            sancion.getIdUnidad(),
                            sancion.getIdPersonaImputada(),
                            idConceptoOpt.get(),
                            request.getMontoMulta(),
                            "Resolución sancionatoria en firme (" + sancion.getNumeroExpediente() + "): " + request.getResolucionFinal(),
                            idUsuario
                    );
                }
            } catch (Exception e) {
                log.warn("No se pudo generar registro automático en MULTAS para sanción {}: {}", idSancion, e.getMessage());
            }
        }

        // Notificar resolución al sancionado
        notificarResolucion(sancion.getIdPersonaImputada(), sancion.getNumeroExpediente(), dec, request.getResolucionFinal());
    }

    @Override
    public List<SancionDTO> getMisSanciones() {
        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = ctx != null ? ctx.getUserId() : null;
        if (userId == null) return Collections.emptyList();

        Long idPersona = null;
        String pSql = "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :uId";
        List<Long> pList = jdbc.query(pSql, new MapSqlParameterSource("uId", userId), (rs, i) -> rs.getLong("ID_PERSONA"));
        if (!pList.isEmpty()) {
            idPersona = pList.get(0);
        }

        if (idPersona == null) return Collections.emptyList();

        // Buscar unidades del residente/propietario
        List<Long> unidades = new ArrayList<>();
        String uSql1 = "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = :pId AND NVL(ESTADO, 'ACTIVO') = 'ACTIVO'";
        unidades.addAll(jdbc.query(uSql1, new MapSqlParameterSource("pId", idPersona), (rs, i) -> rs.getLong("ID_UNIDAD")));

        String uSql2 = "SELECT ID_UNIDAD FROM PROPIETARIOS_UNIDAD WHERE ID_PERSONA = :pId AND NVL(ESTADO, 'ACTIVO') = 'ACTIVO'";
        unidades.addAll(jdbc.query(uSql2, new MapSqlParameterSource("pId", idPersona), (rs, i) -> rs.getLong("ID_UNIDAD")));

        Long propId = resolvePropertyId();
        List<SancionDTO> lista = sancionRepository.findByPersonaOUnidades(idPersona, unidades, propId);
        for (SancionDTO s : lista) {
            s.setDescargos(sancionRepository.findDescargosBySancion(s.getIdSancion()));
        }
        return lista;
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
                String nombre = (String) res.get(0).get("PRIMER_NOMBRE");
                String asunto = "Notificación de Pliego de Cargos - Expediente " + expediente;
                String html = "<p>Estimado/a <strong>" + nombre + "</strong>,</p>" +
                        "<p>Se le notifica que se ha abierto un expediente disciplinario (<strong>" + expediente + "</strong>) " +
                        "por la presunta falta: <em>" + falta + "</em>.</p>" +
                        "<p>De acuerdo con la Ley 675 y el reglamento de propiedad horizontal, cuenta hasta el <strong>" +
                        fechaLimite + "</strong> para presentar sus descargos en el portal SAED.</p>" +
                        "<p>Atentamente,<br/>Comité de Convivencia y Administración</p>";
                emailService.enviarHtmlPublico(email, asunto, html);
            }
        } catch (Exception e) {
            log.warn("No se pudo enviar email de pliego a persona {}: {}", idPersona, e.getMessage());
        }
    }

    private void notificarResolucion(Long idPersona, String expediente, String decision, String resolucionFinal) {
        try {
            String sql = "SELECT EMAIL, PRIMER_NOMBRE FROM PERSONAS WHERE ID_PERSONA = :pId";
            List<Map<String, Object>> res = jdbc.queryForList(sql, new MapSqlParameterSource("pId", idPersona));
            if (!res.isEmpty() && res.get(0).get("EMAIL") != null) {
                String email = (String) res.get(0).get("EMAIL");
                String nombre = (String) res.get(0).get("PRIMER_NOMBRE");
                String asunto = "Resolución Final - Expediente " + expediente;
                String html = "<p>Estimado/a <strong>" + nombre + "</strong>,</p>" +
                        "<p>Se ha emitido decisión de fondo respecto al expediente <strong>" + expediente + "</strong>:</p>" +
                        "<p><strong>Decisión:</strong> " + decision + "</p>" +
                        "<p><strong>Resolución motivada:</strong><br/>" + resolucionFinal + "</p>" +
                        "<p>Atentamente,<br/>Consejo de Administración</p>";
                emailService.enviarHtmlPublico(email, asunto, html);
            }
        } catch (Exception e) {
            log.warn("No se pudo enviar email de resolución a persona {}: {}", idPersona, e.getMessage());
        }
    }
}
