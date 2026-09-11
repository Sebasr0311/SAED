package com.saed.backend.porteria.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.service.PorteriaService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.context.SaedContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.logging.Logger;

@Tag(name = "Porteria", description = "API para la gestion de Porteria")
@RestController
@RequestMapping("/api/v1/porteria")
public class PorteriaController {

    private static final Logger log = Logger.getLogger(PorteriaController.class.getName());
    private final PorteriaService porteriaService;
    private final EmailService emailService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PorteriaController(PorteriaService porteriaService, EmailService emailService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.porteriaService = porteriaService;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- ADMIN CRUD PORTERIAS ---
    @Operation(summary = "Listar porterias de la propiedad")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<ApiResponse<List<PorteriaDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.success(porteriaService.listarPorterias()));
    }

    @Operation(summary = "Obtener porteria por ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<ApiResponse<PorteriaDTO>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(porteriaService.getPorteriaById(id)));
    }

    @Operation(summary = "Crear porteria")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<PorteriaDTO>> crear(@RequestBody @Valid PorteriaCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(porteriaService.crearPorteria(request)));
    }

    @Operation(summary = "Actualizar porteria")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<PorteriaDTO>> actualizar(@PathVariable Long id, @RequestBody @Valid PorteriaCreateDTO request) {
        return ResponseEntity.ok(ApiResponse.success(porteriaService.actualizarPorteria(id, request)));
    }

    @Operation(summary = "Eliminar porteria")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        porteriaService.eliminarPorteria(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // --- VISITAS ---
    @PostMapping("/visitas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_PORTERO')")
    public Map<String, Object> programarVisita(@RequestBody Map<String, Object> body) {
        Long currentUserId = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getUserId() : null;

        Long unidadId = null;
        if (body.get("unidadId") != null && !body.get("unidadId").toString().isBlank()) {
            unidadId = Long.valueOf(body.get("unidadId").toString());
        }

        Long visitanteId = null;
        if (body.get("visitanteId") != null && !body.get("visitanteId").toString().isBlank()) {
            visitanteId = Long.valueOf(body.get("visitanteId").toString());
        } else if (body.get("idVisitante") != null && !body.get("idVisitante").toString().isBlank()) {
            visitanteId = Long.valueOf(body.get("idVisitante").toString());
        } else if (body.get("idFrecuente") != null && !body.get("idFrecuente").toString().isBlank()) {
            visitanteId = Long.valueOf(body.get("idFrecuente").toString());
        }

        if (visitanteId != null) {
            try {
                List<Long> vCheck = jdbcTemplate.query(
                    "SELECT ID_VISITANTE FROM VISITANTES WHERE ID_VISITANTE = :v",
                    Map.of("v", visitanteId), (rs, r) -> rs.getLong("ID_VISITANTE")
                );
                if (vCheck.isEmpty()) {
                    List<Long> vByPersona = jdbcTemplate.query(
                        "SELECT ID_VISITANTE FROM VISITANTES WHERE ID_PERSONA = :p",
                        Map.of("p", visitanteId), (rs, r) -> rs.getLong("ID_VISITANTE")
                    );
                    if (!vByPersona.isEmpty()) {
                        visitanteId = vByPersona.get(0);
                    }
                }
            } catch (Exception ignored) {}
        }

        String rawMetodo = body.get("metodoIngreso") != null ? body.get("metodoIngreso").toString().trim().toUpperCase() : "CODIGO_QR";
        String metodoIngreso;
        if ("LLAMADA_PORTERIA".equals(rawMetodo) || "PREAUTORIZADA".equals(rawMetodo) || "EMERGENCIA".equals(rawMetodo)) {
            metodoIngreso = rawMetodo;
        } else {
            metodoIngreso = "CODIGO_QR";
        }

        String rawMotivo = body.get("motivo") != null ? body.get("motivo").toString() : (body.get("notas") != null ? body.get("notas").toString() : "Visita programada");
        String motivo = (rawMotivo != null && rawMotivo.length() > 200) ? rawMotivo.substring(0, 200) : rawMotivo;

        Long autorizadoPor = body.get("autorizadoPor") != null ? Long.valueOf(body.get("autorizadoPor").toString()) : null;
        ZonedDateTime fechaProgramada = ZonedDateTime.now();
        if (body.get("fechaProgramada") != null) {
            try {
                fechaProgramada = ZonedDateTime.parse(body.get("fechaProgramada").toString());
            } catch (Exception ignored) {}
        }

        String rawEstado = body.get("estado") != null ? body.get("estado").toString().trim().toUpperCase() : "PROGRAMADA";
        String estado;
        if ("EN_CURSO".equals(rawEstado) || "FINALIZADA".equals(rawEstado) || "CANCELADA".equals(rawEstado) || "RECHAZADA".equals(rawEstado)) {
            estado = rawEstado;
        } else {
            estado = "PROGRAMADA";
        }

        // Si el contexto actual es de RESIDENTE y tiene unidad asignada, vincularla directamente
        if (SaedContextHolder.getContext() != null && "RESIDENTE".equals(SaedContextHolder.getContext().getRoleCode())) {
            if (SaedContextHolder.getContext().getUnitId() != null) {
                unidadId = SaedContextHolder.getContext().getUnitId();
            }
        }

        // Si viene idResidente y aun no tenemos unidadId, resolver unidad del residente
        if (unidadId == null && body.get("idResidente") != null) {
            Long idRes = Long.valueOf(body.get("idResidente").toString());
            try {
                List<Long> uids = jdbcTemplate.query(
                    "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = :id AND ESTADO = 'ACTIVO'",
                    Map.of("id", idRes), (rs, r) -> rs.getLong("ID_UNIDAD")
                );
                if (uids.isEmpty()) {
                    uids = jdbcTemplate.query(
                        "SELECT ID_UNIDAD FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :id AND ESTADO IN ('ACTIVA', 'ACTIVO') AND ID_UNIDAD IS NOT NULL",
                        Map.of("id", idRes), (rs, r) -> rs.getLong("ID_UNIDAD")
                    );
                }
                if (uids.isEmpty()) {
                    uids = jdbcTemplate.query(
                        "SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru JOIN USUARIOS u ON ru.ID_PERSONA = u.ID_PERSONA WHERE u.ID_USUARIO = :id AND ru.ESTADO = 'ACTIVO'",
                        Map.of("id", idRes), (rs, r) -> rs.getLong("ID_UNIDAD")
                    );
                }
                if (!uids.isEmpty()) {
                    unidadId = uids.get(0);
                }
            } catch (Exception e) {
                log.warning("No se pudo resolver unidad para idResidente: " + e.getMessage());
            }
        }

        // Si aún no tenemos unidadId pero tenemos currentUserId (residente logueado)
        if (unidadId == null && currentUserId != null) {
            try {
                List<Long> uids = jdbcTemplate.query(
                    "SELECT ID_UNIDAD FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :u AND ESTADO IN ('ACTIVA', 'ACTIVO') AND ID_UNIDAD IS NOT NULL",
                    Map.of("u", currentUserId), (rs, r) -> rs.getLong("ID_UNIDAD")
                );
                if (uids.isEmpty()) {
                    uids = jdbcTemplate.query(
                        "SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru JOIN USUARIOS u ON ru.ID_PERSONA = u.ID_PERSONA WHERE u.ID_USUARIO = :u AND ru.ESTADO = 'ACTIVO'",
                        Map.of("u", currentUserId), (rs, r) -> rs.getLong("ID_UNIDAD")
                    );
                }
                if (!uids.isEmpty()) {
                    unidadId = uids.get(0);
                }
            } catch (Exception e) {
                log.warning("No se pudo resolver unidad para currentUserId: " + e.getMessage());
            }
        }

        if (unidadId == null) {
            Long propId = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getPropertyId() : null;
            if (propId != null) {
                List<Long> firstUnit = jdbcTemplate.query(
                    "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_PROPIEDAD = :p AND ROWNUM = 1",
                    Map.of("p", propId), (rs, r) -> rs.getLong("ID_UNIDAD")
                );
                if (!firstUnit.isEmpty()) {
                    unidadId = firstUnit.get(0);
                }
            }
        }
        if (unidadId == null) {
            unidadId = 1L;
        }

        // Resolver autorizadoPor garantizando que sea un ID_USUARIO valido en USUARIOS (FK_VISITAS_AUTORIZADOR)
        if (autorizadoPor != null) {
            try {
                List<Long> uCheck = jdbcTemplate.query(
                    "SELECT ID_USUARIO FROM USUARIOS WHERE ID_USUARIO = :u",
                    Map.of("u", autorizadoPor), (rs, r) -> rs.getLong("ID_USUARIO")
                );
                if (uCheck.isEmpty()) {
                    autorizadoPor = null;
                }
            } catch (Exception ignored) {
                autorizadoPor = null;
            }
        }
        if (autorizadoPor == null && currentUserId != null) {
            try {
                List<Long> uCheck = jdbcTemplate.query(
                    "SELECT ID_USUARIO FROM USUARIOS WHERE ID_USUARIO = :u",
                    Map.of("u", currentUserId), (rs, r) -> rs.getLong("ID_USUARIO")
                );
                if (!uCheck.isEmpty()) {
                    autorizadoPor = currentUserId;
                }
            } catch (Exception ignored) {}
        }

        // Marcar como frecuente si se solicita en el registro de visita
        boolean esFrecuente = Boolean.TRUE.equals(body.get("guardarFrecuente")) || "true".equalsIgnoreCase(String.valueOf(body.get("guardarFrecuente")));

        // Si viene mapa de visitante y no visitanteId, resolver/crear persona y visitante
        if (visitanteId == null && body.get("visitante") instanceof Map<?, ?> visMap) {
            String doc = visMap.get("numeroDocumento") != null ? visMap.get("numeroDocumento").toString().trim() : "";
            String nom = visMap.get("nombres") != null ? visMap.get("nombres").toString().trim() : "Visitante";
            String ape = visMap.get("apellidos") != null ? visMap.get("apellidos").toString().trim() : "";
            String tel = visMap.get("telefono") != null ? visMap.get("telefono").toString().trim() : "";
            String email = visMap.get("email") != null ? visMap.get("email").toString().trim() : "";
            Long idTipoDoc = 1L;
            if (visMap.get("idTipoDoc") != null) {
                try { idTipoDoc = Long.valueOf(visMap.get("idTipoDoc").toString()); } catch (Exception ignored) {}
            }
            if (visMap.get("idVisitante") != null) {
                try { visitanteId = Long.valueOf(visMap.get("idVisitante").toString()); } catch (Exception ignored) {}
            }
            Long personaId = null;
            if (visMap.get("idPersona") != null) {
                try { personaId = Long.valueOf(visMap.get("idPersona").toString()); } catch (Exception ignored) {}
            }

            SaedContext prevCtx = SaedContextHolder.getContext();
            try {
                Long orgId = prevCtx != null && prevCtx.getOrganizationId() != null ? prevCtx.getOrganizationId() : 1L;
                Long propId = prevCtx != null && prevCtx.getPropertyId() != null ? prevCtx.getPropertyId() : 1L;
                SaedContext systemCtx = SaedContext.builder()
                    .userId(1L)
                    .organizationId(orgId)
                    .propertyId(propId)
                    .roleCode("SUPERADMIN")
                    .roleScope("GLOBAL")
                    .build();
                SaedContextHolder.setContext(systemCtx);
                try {
                    jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                    jdbcTemplate.getJdbcOperations().execute(
                        String.format("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, 'SUPERADMIN'); END;", orgId, propId)
                    );
                } catch (Exception ignored) {}

                if (personaId == null && !doc.isEmpty()) {
                    try {
                        List<Long> pers = jdbcTemplate.query(
                            "SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc ORDER BY CASE WHEN ID_TIPO_DOCUMENTO = :idTipoDoc THEN 0 ELSE 1 END, ID_PERSONA ASC",
                            Map.of("doc", doc, "idTipoDoc", idTipoDoc), (rs, r) -> rs.getLong("ID_PERSONA")
                        );
                        if (!pers.isEmpty()) {
                            personaId = pers.get(0);
                        }
                    } catch (Exception ignored) {}
                }

                if (personaId == null) {
                    try {
                        KeyHolder kh = new GeneratedKeyHolder();
                        MapSqlParameterSource pParams = new MapSqlParameterSource()
                            .addValue("idTipo", idTipoDoc)
                            .addValue("doc", !doc.isEmpty() ? doc : "V-" + System.currentTimeMillis())
                            .addValue("nom", nom)
                            .addValue("ape", ape.isBlank() ? "N/A" : ape)
                            .addValue("tel", tel.isBlank() ? null : tel)
                            .addValue("email", email.isBlank() ? null : email);
                        jdbcTemplate.update(
                            "INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, TELEFONO, EMAIL, ESTADO) " +
                            "VALUES (:idTipo, :doc, 'NATURAL', :nom, :ape, :tel, :email, 'ACTIVO')",
                            pParams, kh, new String[]{"ID_PERSONA"}
                        );
                        personaId = extractGeneratedKey(kh, "ID_PERSONA");
                    } catch (Exception e) {
                        log.warning("Error creando persona para visitante: " + e.getMessage());
                    }
                    if (personaId == null && !doc.isEmpty()) {
                        try {
                            List<Long> pers = jdbcTemplate.query(
                                "SELECT ID_PERSONA FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc ORDER BY CASE WHEN ID_TIPO_DOCUMENTO = :idTipoDoc THEN 0 ELSE 1 END, ID_PERSONA ASC",
                                Map.of("doc", doc, "idTipoDoc", idTipoDoc), (rs, r) -> rs.getLong("ID_PERSONA")
                            );
                            if (!pers.isEmpty()) {
                                personaId = pers.get(0);
                            }
                        } catch (Exception ignored) {}
                    }
                }

                if (personaId != null && visitanteId == null) {
                    try {
                        List<Long> visList = jdbcTemplate.query(
                            "SELECT ID_VISITANTE FROM VISITANTES WHERE ID_PERSONA = :p",
                            Map.of("p", personaId), (rs, r) -> rs.getLong("ID_VISITANTE")
                        );
                        if (!visList.isEmpty()) {
                            visitanteId = visList.get(0);
                            if (esFrecuente) {
                                jdbcTemplate.update("UPDATE VISITANTES SET ES_FRECUENTE = 'S' WHERE ID_VISITANTE = :v", Map.of("v", visitanteId));
                            }
                        } else {
                            KeyHolder khVis = new GeneratedKeyHolder();
                            jdbcTemplate.update(
                                "INSERT INTO VISITANTES (ID_PERSONA, ES_FRECUENTE, ESTADO) VALUES (:p, :frec, 'ACTIVO')",
                                new MapSqlParameterSource()
                                    .addValue("p", personaId)
                                    .addValue("frec", esFrecuente ? "S" : "N"),
                                khVis, new String[]{"ID_VISITANTE"}
                            );
                            visitanteId = extractGeneratedKey(khVis, "ID_VISITANTE");
                        }
                    } catch (Exception e) {
                        log.warning("Error resolviendo ID_VISITANTE: " + e.getMessage());
                    }
                    if (visitanteId == null) {
                        try {
                            List<Long> visList = jdbcTemplate.query(
                                "SELECT ID_VISITANTE FROM VISITANTES WHERE ID_PERSONA = :p",
                                Map.of("p", personaId), (rs, r) -> rs.getLong("ID_VISITANTE")
                            );
                            if (!visList.isEmpty()) {
                                visitanteId = visList.get(0);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } finally {
                restoreSaedContext(prevCtx);
            }
        }

        if (visitanteId == null) {
            throw new IllegalStateException("No fue posible registrar la información del visitante. Verifique que los datos del documento y nombre sean válidos.");
        }

        VisitaRequestDTO request = new VisitaRequestDTO(
            unidadId,
            visitanteId,
            metodoIngreso,
            motivo,
            autorizadoPor,
            fechaProgramada,
            estado
        );

        VisitaDTO visita = porteriaService.programarVisita(request);

        // Marcar como frecuente si se solicita en el registro de visita
        if (esFrecuente) {
            try {
                jdbcTemplate.update("UPDATE VISITANTES SET ES_FRECUENTE = 'S' WHERE ID_VISITANTE = :v", Map.of("v", visitanteId));
            } catch (Exception ignored) {}
        }

        // Duración QR
        int validezMin = 1440;
        if (body.get("tiempoValidezMin") != null) {
            try { validezMin = Integer.parseInt(body.get("tiempoValidezMin").toString()); } catch (Exception ignored) {}
        }

        // Generar QR automatico y notificar al VISITANTE
        String token = UUID.randomUUID().toString();
        QrAccesoRequestDTO qrReq = new QrAccesoRequestDTO(
            visita.idVisita(),
            token,
            ZonedDateTime.now(ZoneId.of("America/Bogota")).plusMinutes(validezMin),
            1,
            "ACTIVO",
            autorizadoPor
        );

        QrAccesoDTO qr = porteriaService.generarQrAcceso(qrReq);

        // Vehiculo si vino en payload con placa (objeto vehiculo o campos planos en raíz)
        String placa = null;
        String tipoV = "VEHICULO";
        if (body.get("vehiculo") instanceof Map<?, ?> vehMap) {
            placa = vehMap.get("placa") != null ? vehMap.get("placa").toString().trim().toUpperCase() : null;
            if (vehMap.get("tipo") != null) {
                tipoV = vehMap.get("tipo").toString();
            }
        } else if (body.get("placa") != null && !body.get("placa").toString().isBlank()) {
            placa = body.get("placa").toString().trim().toUpperCase();
            if (body.get("tipoVehiculo") != null && !body.get("tipoVehiculo").toString().isBlank()) {
                tipoV = body.get("tipoVehiculo").toString();
            }
        }
        if (placa != null && !placa.isBlank()) {
            try {
                porteriaService.registrarIngresoVehiculo(new VehiculoVisitaRequestDTO(visita.idVisita(), null, placa, tipoV, "DENTRO"));
            } catch (Exception e) {
                log.warning("No se pudo registrar vehiculo de visita: " + e.getMessage());
            }
        }

        // Notificar email si disponible
        try {
            List<Map<String, Object>> visitantes = jdbcTemplate.queryForList(
                "SELECT p.EMAIL FROM PERSONAS p JOIN VISITANTES v ON p.ID_PERSONA = v.ID_PERSONA WHERE v.ID_VISITANTE = :v AND p.EMAIL IS NOT NULL", 
                Map.of("v", visitanteId)
            );
            if (!visitantes.isEmpty()) {
                String email = (String) visitantes.get(0).get("EMAIL");
                if (email != null && !email.isBlank()) {
                    emailService.enviarCorreoQR(email, token, qr.fechaExpiracion().toString(), "Visitante SAED");
                    log.info("QR enviado exitosamente al visitante: " + email);
                }
            }
        } catch (Exception e) {
            log.warning("Fallo al enviar correo QR a visitante: " + e.getMessage());
        }

        // Devolver respuesta unificada para que VisitasPage.jsx de React no falle
        Map<String, Object> response = new HashMap<>();
        response.put("idVisita", visita.idVisita());
        response.put("codigoQr", token);
        response.put("token", token);
        return response;
    }

    @PostMapping("/visitas/rapida")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_PORTERO')")
    public Map<String, Object> programarVisitaRapida(@RequestBody Map<String, Object> body) {
        if (body.get("motivo") == null && body.get("notas") == null) {
            body.put("motivo", "Visita frecuente rápida");
        }
        body.put("metodoIngreso", "CODIGO_QR");
        return programarVisita(body);
    }

    @GetMapping("/visitas/buscar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PORTERO')")
    public ResponseEntity<Map<String, Object>> buscarVisitantePorDocumento(@RequestParam(required = false) String documento) {
        if (documento == null || documento.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of());
        }
        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            Long orgId = prevCtx != null && prevCtx.getOrganizationId() != null ? prevCtx.getOrganizationId() : 1L;
            Long propId = prevCtx != null && prevCtx.getPropertyId() != null ? prevCtx.getPropertyId() : 1L;
            SaedContext systemCtx = SaedContext.builder()
                .userId(1L)
                .organizationId(orgId)
                .propertyId(propId)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
            SaedContextHolder.setContext(systemCtx);
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                jdbcTemplate.getJdbcOperations().execute(
                    String.format("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, 'SUPERADMIN'); END;", orgId, propId)
                );
            } catch (Exception ignored) {}

            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT p.ID_PERSONA, p.ID_TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO, p.PRIMER_NOMBRE, p.PRIMER_APELLIDO, p.TELEFONO, p.EMAIL, v.ID_VISITANTE " +
                "FROM PERSONAS p LEFT JOIN VISITANTES v ON p.ID_PERSONA = v.ID_PERSONA " +
                "WHERE p.NUMERO_DOCUMENTO = :doc AND ROWNUM = 1",
                Map.of("doc", documento.trim())
            );
            if (!list.isEmpty()) {
                Map<String, Object> row = list.get(0);
                Map<String, Object> res = new HashMap<>();
                res.put("idPersona", row.get("ID_PERSONA"));
                res.put("idVisitante", row.get("ID_VISITANTE"));
                res.put("idTipoDoc", row.get("ID_TIPO_DOCUMENTO"));
                res.put("numeroDocumento", row.get("NUMERO_DOCUMENTO"));
                res.put("nombres", row.get("PRIMER_NOMBRE"));
                res.put("apellidos", row.get("PRIMER_APELLIDO"));
                res.put("telefono", row.get("TELEFONO"));
                res.put("email", row.get("EMAIL"));
                return ResponseEntity.ok(res);
            }
        } catch (Exception e) {
            log.warning("Error buscando visitante por documento: " + e.getMessage());
        } finally {
            restoreSaedContext(prevCtx);
        }
        return ResponseEntity.ok(Map.of());
    }

    @GetMapping("/visitas/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PORTERO')")
    public VisitaDTO getVisitaById(@PathVariable Long id) {
        return porteriaService.getVisitaById(id);
    }

    @GetMapping("/unidades/{unidadId}/visitas")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE')")
    public List<VisitaDTO> getVisitasByUnidad(@PathVariable Long unidadId) {
        return porteriaService.getVisitasByUnidad(unidadId);
    }

    @PutMapping("/visitas/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE')")
    public VisitaDTO actualizarVisita(@PathVariable Long id, @RequestBody @Valid VisitaRequestDTO request) {
        return porteriaService.actualizarVisita(id, request);
    }

    @PutMapping("/visitas/{id}/salida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public void registrarSalidaVisita(@PathVariable Long id) {
        porteriaService.registrarSalidaVisita(id);
    }

    @GetMapping("/visitas-resumen")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public List<VisitaListDTO> getVisitasResumen() {
        return porteriaService.getVisitasResumen();
    }

    @GetMapping("/visitas/historial")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public List<VisitaHistorialDTO> getVisitasHistorial(
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin) {
        return porteriaService.getVisitasHistorial(fechaInicio, fechaFin);
    }

    @GetMapping("/visitas/{id}/detalle")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PORTERO')")
    public VisitaDetalleDTO getVisitaDetalle(@PathVariable Long id) {
        return porteriaService.getVisitaDetalle(id);
    }

    // --- REGISTROS DE ACCESO ---
    @PostMapping("/registros/entrada")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO')")
    public RegistroAccesoDTO registrarEntrada(@RequestBody @Valid RegistroAccesoRequestDTO request) {
        return porteriaService.registrarEntrada(request);
    }

    @PostMapping("/registros/salida")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO')")
    public RegistroAccesoDTO registrarSalida(@RequestBody @Valid RegistroAccesoRequestDTO request) {
        return porteriaService.registrarSalida(request);
    }

    @GetMapping("/propiedades/{propiedadId}/registros")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public List<RegistroAccesoDTO> getRegistrosByPropiedad(@PathVariable Long propiedadId) {
        return porteriaService.getRegistrosByPropiedad(propiedadId);
    }

    // --- QR ACCESOS ---
    @PostMapping("/qr")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public QrAccesoDTO generarQrAcceso(@RequestBody @Valid QrAccesoRequestDTO request) {
        return porteriaService.generarQrAcceso(request);
    }

    @GetMapping("/qr/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PORTERO')")
    public QrAccesoDTO getQrAccesoById(@PathVariable Long id) {
        return porteriaService.getQrAccesoById(id);
    }

    @PostMapping("/qr/validar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public Map<String, Object> validarQr(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            token = body.get("codigoQr");
        }
        if (token == null || token.isBlank()) {
            return Map.of("valido", false, "mensaje", "Token o código QR no proporcionado");
        }
        return porteriaService.validarQrDetalle(token);
    }

    @PostMapping("/qr/notificar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public Map<String, Object> notificarVisita(@RequestBody Map<String, String> body) {
        String token = body.get("codigoQr");
        if (token == null || token.isBlank()) {
            token = body.get("token");
        }
        String fotoCaptura = body.get("fotoCaptura");
        return porteriaService.notificarVisitaQr(token, fotoCaptura);
    }

    @PostMapping("/qr/entrada")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public Map<String, Object> registrarEntradaQr(@RequestBody Map<String, String> body) {
        String token = body.get("codigoQr");
        if (token == null || token.isBlank()) {
            token = body.get("token");
        }
        String medioTransporte = body.get("medioTransporte");
        String placa = body.get("placa");
        String descripcion = body.get("descripcion");
        return porteriaService.registrarEntradaQr(token, medioTransporte, placa, descripcion);
    }

    // --- VEHICULOS VISITA ---
    @PostMapping("/vehiculos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO')")
    public VehiculoVisitaDTO registrarIngresoVehiculo(@RequestBody @Valid VehiculoVisitaRequestDTO request) {
        return porteriaService.registrarIngresoVehiculo(request);
    }

    @PostMapping("/vehiculos/{id}/salida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO')")
    public void registrarSalidaVehiculo(@PathVariable Long id, @RequestBody Map<String, BigDecimal> body) {
        porteriaService.registrarSalidaVehiculo(id, body.getOrDefault("costoTotal", BigDecimal.ZERO));
    }

    private Long extractGeneratedKey(KeyHolder kh, String columnName) {
        if (kh == null) return null;
        try {
            if (kh.getKey() != null) {
                return kh.getKey().longValue();
            }
        } catch (Exception ignored) {}
        if (kh.getKeys() != null) {
            for (Map.Entry<String, Object> entry : kh.getKeys().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(columnName) && entry.getValue() instanceof Number num) {
                    return num.longValue();
                }
            }
            for (Map.Entry<String, Object> entry : kh.getKeys().entrySet()) {
                if (!entry.getKey().equalsIgnoreCase("ROWID") && entry.getValue() instanceof Number num) {
                    return num.longValue();
                }
            }
        }
        if (kh.getKeyList() != null && !kh.getKeyList().isEmpty()) {
            for (Map<String, Object> map : kh.getKeyList()) {
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(columnName) && entry.getValue() instanceof Number num) {
                        return num.longValue();
                    }
                }
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (!entry.getKey().equalsIgnoreCase("ROWID") && entry.getValue() instanceof Number num) {
                        return num.longValue();
                    }
                }
            }
        }
        return null;
    }

    private void restoreSaedContext(SaedContext prevCtx) {
        if (prevCtx != null) {
            SaedContextHolder.setContext(prevCtx);
            if (prevCtx.getUserId() != null && prevCtx.getRoleCode() != null) {
                try {
                    Long orgId = prevCtx.getOrganizationId() != null ? prevCtx.getOrganizationId() : 0L;
                    Long propId = prevCtx.getPropertyId() != null ? prevCtx.getPropertyId() : 0L;
                    String role = prevCtx.getRoleCode();
                    jdbcTemplate.getJdbcOperations().execute(
                        String.format("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(%d); PKG_SAED_SESSION.SET_CONTEXT(%d, %d, %d, '%s'); END;",
                            prevCtx.getUserId(), prevCtx.getUserId(), orgId, propId, role)
                    );
                } catch (Exception ignored) {}
            }
        } else {
            SaedContextHolder.clearContext();
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
            } catch (Exception ignored) {}
        }
    }
}
