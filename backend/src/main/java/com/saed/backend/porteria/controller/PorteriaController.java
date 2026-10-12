package com.saed.backend.porteria.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.service.PorteriaService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.context.SaedContext;
import com.saed.backend.person.service.VisitorIdentityRegistry;
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

    // --- ASIGNACIÓN DE PORTEROS Y TURNOS (LEY 1920 DE 2018) ---
    private static final Map<String, Map<String, String>> CATALOGO_TURNOS = Map.of(
        "TURNO_8H_MANANA", Map.of("nombre", "Turno Mañana (06:00 - 14:00)", "inicio", "06:00", "fin", "14:00", "tipo", "ORDINARIO_8H"),
        "TURNO_8H_TARDE", Map.of("nombre", "Turno Tarde (14:00 - 22:00)", "inicio", "14:00", "fin", "22:00", "tipo", "ORDINARIO_8H"),
        "TURNO_8H_NOCHE", Map.of("nombre", "Turno Noche (22:00 - 06:00)", "inicio", "22:00", "fin", "06:00", "tipo", "ORDINARIO_8H"),
        "TURNO_12H_DIURNO", Map.of("nombre", "Turno Especial Diurno 12h (06:00 - 18:00)", "inicio", "06:00", "fin", "18:00", "tipo", "LEY_1920_12H"),
        "TURNO_12H_NOCTURNO", Map.of("nombre", "Turno Especial Nocturno 12h (18:00 - 06:00)", "inicio", "18:00", "fin", "06:00", "tipo", "LEY_1920_12H")
    );

    @Operation(summary = "Catálogo de turnos legales de vigilancia (Ley 1920 de 2018)")
    @GetMapping("/turnos/catalogo")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> catalogoTurnos() {
        List<Map<String, Object>> result = CATALOGO_TURNOS.entrySet().stream()
            .map(e -> {
                Map<String, Object> m = new HashMap<>(e.getValue());
                m.put("codigo", e.getKey());
                return m;
            })
            .toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Listar porteros disponibles en la propiedad para asignación de turnos")
    @GetMapping("/porteros-disponibles")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listarPorterosDisponibles() {
        Long propId = requireCallerPropertyId();
        String sql = """
            SELECT u.ID_USUARIO, u.NOMBRE_USUARIO, u.EMAIL,
                   p.PRIMER_NOMBRE, p.SEGUNDO_NOMBRE, p.PRIMER_APELLIDO, p.SEGUNDO_APELLIDO, p.NUMERO_DOCUMENTO
            FROM USUARIOS u
            JOIN USUARIO_ASIGNACIONES ua ON ua.ID_USUARIO = u.ID_USUARIO
            JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
            LEFT JOIN PERSONAS p ON p.ID_PERSONA = u.ID_PERSONA
            WHERE ua.ID_PROPIEDAD = :propId
              AND r.CODIGO = 'PORTERO'
              AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
              AND u.ACTIVO = 1
            ORDER BY u.NOMBRE_USUARIO
            """;
        List<Map<String, Object>> porteros = jdbcTemplate.query(sql, Map.of("propId", propId), (rs, rowNum) -> {
            String pNom = rs.getString("PRIMER_NOMBRE");
            String sNom = rs.getString("SEGUNDO_NOMBRE");
            String pApe = rs.getString("PRIMER_APELLIDO");
            String sApe = rs.getString("SEGUNDO_APELLIDO");
            StringBuilder sb = new StringBuilder();
            if (pNom != null) sb.append(pNom).append(" ");
            if (sNom != null) sb.append(sNom).append(" ");
            if (pApe != null) sb.append(pApe).append(" ");
            if (sApe != null) sb.append(sApe);
            String nombreCompleto = sb.toString().trim();
            if (nombreCompleto.isEmpty()) nombreCompleto = rs.getString("NOMBRE_USUARIO");

            Map<String, Object> m = new HashMap<>();
            m.put("idUsuario", rs.getLong("ID_USUARIO"));
            m.put("username", rs.getString("NOMBRE_USUARIO"));
            m.put("email", rs.getString("EMAIL"));
            m.put("nombreCompleto", nombreCompleto);
            m.put("numeroDocumento", rs.getString("NUMERO_DOCUMENTO"));
            return m;
        });
        return ResponseEntity.ok(ApiResponse.success(porteros));
    }

    @Operation(summary = "Listar turnos y porteros asignados a una portería")
    @GetMapping("/{id}/turnos")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<ApiResponse<List<PorteroTurnoDTO>>> listarTurnos(@PathVariable Long id) {
        Long propId = requireCallerPropertyId();
        String sql = """
            SELECT pt.ID_ASIGNACION_TURNO, pt.ID_PORTERIA, pt.ID_USUARIO,
                   u.NOMBRE_USUARIO,
                   p.PRIMER_NOMBRE, p.SEGUNDO_NOMBRE, p.PRIMER_APELLIDO, p.SEGUNDO_APELLIDO,
                   pt.CODIGO_TURNO, pt.NOMBRE_TURNO, pt.HORA_INICIO, pt.HORA_FIN,
                   pt.DIAS_SEMANA, pt.ESTADO, pt.FECHA_ASIGNACION
            FROM PORTERIA_TURNOS pt
            JOIN USUARIOS u ON u.ID_USUARIO = pt.ID_USUARIO
            LEFT JOIN PERSONAS p ON p.ID_PERSONA = u.ID_PERSONA
            WHERE pt.ID_PORTERIA = :idPorteria AND pt.ID_PROPIEDAD = :propId
            ORDER BY pt.HORA_INICIO ASC
            """;
        List<PorteroTurnoDTO> turnos = jdbcTemplate.query(sql, Map.of("idPorteria", id, "propId", propId), (rs, rowNum) -> {
            String pNom = rs.getString("PRIMER_NOMBRE");
            String sNom = rs.getString("SEGUNDO_NOMBRE");
            String pApe = rs.getString("PRIMER_APELLIDO");
            String sApe = rs.getString("SEGUNDO_APELLIDO");
            StringBuilder sb = new StringBuilder();
            if (pNom != null) sb.append(pNom).append(" ");
            if (sNom != null) sb.append(sNom).append(" ");
            if (pApe != null) sb.append(pApe).append(" ");
            if (sApe != null) sb.append(sApe);
            String nombreCompleto = sb.toString().trim();
            if (nombreCompleto.isEmpty()) nombreCompleto = rs.getString("NOMBRE_USUARIO");

            java.sql.Timestamp ts = rs.getTimestamp("FECHA_ASIGNACION");
            return new PorteroTurnoDTO(
                rs.getLong("ID_ASIGNACION_TURNO"),
                rs.getLong("ID_PORTERIA"),
                rs.getLong("ID_USUARIO"),
                rs.getString("NOMBRE_USUARIO"),
                nombreCompleto,
                rs.getString("CODIGO_TURNO"),
                rs.getString("NOMBRE_TURNO"),
                rs.getString("HORA_INICIO"),
                rs.getString("HORA_FIN"),
                rs.getString("DIAS_SEMANA"),
                rs.getString("ESTADO"),
                ts != null ? ts.toInstant() : null
            );
        });
        return ResponseEntity.ok(ApiResponse.success(turnos));
    }

    @Operation(summary = "Asignar un portero a una portería con un turno (Ley 1920 de 2018)")
    @PostMapping("/{id}/turnos")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<Void>> asignarTurno(
            @PathVariable Long id,
            @RequestBody @Valid PorteroTurnoRequestDTO req) {
        Long propId = requireCallerPropertyId();

        Map<String, String> infoTurno = CATALOGO_TURNOS.get(req.codigoTurno());
        if (infoTurno == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Código de turno inválido según la Ley 1920 de 2018"));
        }

        // The porteria and the assigned user must both belong to the caller's property.
        List<Long> porteria = jdbcTemplate.query(
            "SELECT ID_PORTERIA FROM PORTERIAS WHERE ID_PORTERIA = :idPorteria AND ID_PROPIEDAD = :propId",
            Map.of("idPorteria", id, "propId", propId), (rs, r) -> rs.getLong("ID_PORTERIA")
        );
        if (porteria.isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException("La portería no pertenece a la propiedad asignada");
        }
        List<Long> portero = jdbcTemplate.query(
            "SELECT ua.ID_USUARIO FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON r.ID_ROL = ua.ID_ROL "
                + "JOIN USUARIOS u ON u.ID_USUARIO = ua.ID_USUARIO "
                + "WHERE ua.ID_USUARIO = :idUsuario AND ua.ID_PROPIEDAD = :propId AND r.CODIGO = 'PORTERO' "
                + "AND ua.ESTADO IN ('ACTIVA', 'ACTIVO') AND u.ACTIVO = 1",
            Map.of("idUsuario", req.idUsuario(), "propId", propId), (rs, r) -> rs.getLong("ID_USUARIO")
        );
        if (portero.isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException("El usuario no es un portero activo de la propiedad");
        }

        // PORTERIA_TURNOS is VPD-protected (V5.32): MERGE raises ORA-28132, so update first, then insert.
        Map<String, Object> params = new HashMap<>();
        params.put("propId", propId);
        params.put("idPorteria", id);
        params.put("idUsuario", req.idUsuario());
        params.put("codigoTurno", req.codigoTurno());
        params.put("nombreTurno", infoTurno.get("nombre"));
        params.put("horaInicio", infoTurno.get("inicio"));
        params.put("horaFin", infoTurno.get("fin"));
        params.put("diasSemana", req.diasSemana() != null && !req.diasSemana().isBlank() ? req.diasSemana().trim() : "L-D");

        int updated = jdbcTemplate.update(
            "UPDATE PORTERIA_TURNOS SET CODIGO_TURNO = :codigoTurno, NOMBRE_TURNO = :nombreTurno, "
                + "HORA_INICIO = :horaInicio, HORA_FIN = :horaFin, DIAS_SEMANA = :diasSemana, ESTADO = 'ACTIVO', "
                + "FECHA_ASIGNACION = CURRENT_TIMESTAMP "
                + "WHERE ID_PROPIEDAD = :propId AND ID_PORTERIA = :idPorteria AND ID_USUARIO = :idUsuario",
            params
        );
        if (updated == 0) {
            jdbcTemplate.update(
                "INSERT INTO PORTERIA_TURNOS (ID_PROPIEDAD, ID_PORTERIA, ID_USUARIO, CODIGO_TURNO, NOMBRE_TURNO, "
                    + "HORA_INICIO, HORA_FIN, DIAS_SEMANA, ESTADO) VALUES (:propId, :idPorteria, :idUsuario, "
                    + ":codigoTurno, :nombreTurno, :horaInicio, :horaFin, :diasSemana, 'ACTIVO')",
                params
            );
        }

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Desvincular turno de un portero en una portería")
    @DeleteMapping("/{id}/turnos/{idAsignacion}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<Void>> desasignarTurno(@PathVariable Long id, @PathVariable Long idAsignacion) {
        Long propId = requireCallerPropertyId();
        jdbcTemplate.update(
            "DELETE FROM PORTERIA_TURNOS WHERE ID_ASIGNACION_TURNO = :idAsignacion AND ID_PORTERIA = :idPorteria AND ID_PROPIEDAD = :propId",
            Map.of("idAsignacion", idAsignacion, "idPorteria", id, "propId", propId)
        );
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // --- VISITAS ---
    @PostMapping("/visitas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_RESIDENTE_CONVIVENCIA') or hasAuthority('SCOPE_PORTERO')")
    public Map<String, Object> programarVisita(@RequestBody Map<String, Object> body) {
        Long currentUserId = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getUserId() : null;
        if (currentUserId == null) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                try { currentUserId = Long.parseLong(auth.getName()); } catch (Exception ignored) {}
            }
        }

        Long unidadId = null;
        if (body.get("unidadId") != null && !body.get("unidadId").toString().isBlank()) {
            unidadId = Long.valueOf(body.get("unidadId").toString());
        } else if (body.get("idUnidad") != null && !body.get("idUnidad").toString().isBlank()) {
            unidadId = Long.valueOf(body.get("idUnidad").toString());
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
            // F7-02: a client-supplied visitor id is only honored when it is visible under the
            // caller's own context (RLS); otherwise it is discarded and never reaches the insert.
            Long visibleVisitanteId = null;
            try {
                List<Long> vCheck = jdbcTemplate.query(
                    "SELECT ID_VISITANTE FROM VISITANTES WHERE ID_VISITANTE = :v",
                    Map.of("v", visitanteId), (rs, r) -> rs.getLong("ID_VISITANTE")
                );
                if (!vCheck.isEmpty()) {
                    visibleVisitanteId = vCheck.get(0);
                } else {
                    List<Long> vByPersona = jdbcTemplate.query(
                        "SELECT ID_VISITANTE FROM VISITANTES WHERE ID_PERSONA = :p",
                        Map.of("p", visitanteId), (rs, r) -> rs.getLong("ID_VISITANTE")
                    );
                    if (!vByPersona.isEmpty()) {
                        visibleVisitanteId = vByPersona.get(0);
                    }
                }
            } catch (Exception ignored) {}
            if (visibleVisitanteId == null) {
                // A legacy visitor nobody has claimed is hidden by the row-level policy: the resolver adopts it for
                // this property; a visitor of any property (or of another resident) is never returned.
                visibleVisitanteId = new VisitorIdentityRegistry(jdbcTemplate).claimUnclaimed(visitanteId);
            }
            visitanteId = visibleVisitanteId;
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

        // SEC-02: Server-controlled autorizador (prevencion de suplantacion).
        // Ignorar cualquier valor enviado por el cliente en 'autorizadoPor' y derivarlo exclusivamente de la identidad autenticada.
        Long autorizadoPor = currentUserId;
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

        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) {
            throw new org.springframework.security.access.AccessDeniedException("Missing tenant context");
        }
        String roleCode = ctx.getRoleCode();
        String roleScope = ctx.getRoleScope();
        boolean residentCaller = "RESIDENTE".equals(roleCode) || "RESIDENTE_CONVIVENCIA".equals(roleCode) || "UNIDAD".equals(roleScope);

        // F7-02: a client-supplied property id must match the authenticated property scope.
        Object rawPropId = body.get("idPropiedad") != null ? body.get("idPropiedad") : body.get("propiedadId");
        if (rawPropId != null && !rawPropId.toString().isBlank()) {
            Long requestedPropId;
            try {
                requestedPropId = Long.valueOf(rawPropId.toString());
            } catch (NumberFormatException e) {
                throw new org.springframework.security.access.AccessDeniedException("Invalid property scope");
            }
            if (!requestedPropId.equals(ctx.getPropertyId())) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para programar visitas en otra propiedad");
            }
        }

        // Validar autorizacion de ambito residencial para RESIDENTE y RESIDENTE_CONVIVENCIA
        if (residentCaller) {
            Long userUnitId = ctx.getUnitId();
            if (userUnitId == null && currentUserId != null) {
                try {
                    List<Long> uids = jdbcTemplate.query(
                        "SELECT ID_UNIDAD FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :u AND ESTADO IN ('ACTIVA', 'ACTIVO') AND ID_UNIDAD IS NOT NULL",
                        Map.of("u", currentUserId), (rs, r) -> rs.getLong("ID_UNIDAD")
                    );
                    if (!uids.isEmpty()) {
                        userUnitId = uids.get(0);
                    }
                } catch (Exception ignored) {}
            }
            if (userUnitId == null) {
                throw new org.springframework.security.access.AccessDeniedException("El usuario no tiene una unidad residencial asignada");
            }
            if (unidadId != null && !userUnitId.equals(unidadId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para programar visitas en otra unidad");
            }
            unidadId = userUnitId;
        } else {
            // F7-02: admin and portero callers are restricted to their own property.
            if (ctx.getPropertyId() == null) {
                throw new org.springframework.security.access.AccessDeniedException("El usuario no tiene una propiedad asignada");
            }
            if (unidadId != null) {
                requireUnitInCallerProperty(unidadId, ctx.getPropertyId());
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

        // F7-02: never attribute a visit to an arbitrary unit of the property; the destination must be explicit or resolvable.
        if (unidadId == null) {
            throw new IllegalArgumentException("No fue posible determinar la unidad de la visita");
        }
        if (!residentCaller) {
            // Units resolved from idResidente or assignments must still belong to the caller's property.
            requireUnitInCallerProperty(unidadId, ctx.getPropertyId());
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

        // Si viene mapa de visitante o datos planos en raíz, y no visitanteId, resolver/crear persona y visitante
        Map<?, ?> visMap = (body.get("visitante") instanceof Map<?, ?> m) ? m : null;
        if (visMap == null && (body.get("documento") != null || body.get("nombreVisitante") != null || body.get("numeroDocumento") != null)) {
            Map<String, Object> synthetic = new HashMap<>();
            synthetic.put("numeroDocumento", body.get("documento") != null ? body.get("documento") : body.get("numeroDocumento"));
            synthetic.put("nombres", body.get("nombreVisitante") != null ? body.get("nombreVisitante") : body.get("nombres"));
            synthetic.put("apellidos", body.get("apellidos") != null ? body.get("apellidos") : "");
            synthetic.put("telefono", body.get("telefono") != null ? body.get("telefono") : "");
            synthetic.put("email", body.get("email") != null ? body.get("email") : "");
            synthetic.put("idTipoDoc", body.get("idTipoDoc") != null ? body.get("idTipoDoc") : 1L);
            visMap = synthetic;
        }

        if (visitanteId == null && visMap != null) {
            String doc = visMap.get("numeroDocumento") != null ? visMap.get("numeroDocumento").toString().trim() : "";
            String nom = visMap.get("nombres") != null ? visMap.get("nombres").toString().trim() : "Visitante";
            String ape = visMap.get("apellidos") != null ? visMap.get("apellidos").toString().trim() : "";
            String tel = visMap.get("telefono") != null ? visMap.get("telefono").toString().trim() : "";
            String email = visMap.get("email") != null ? visMap.get("email").toString().trim() : "";
            Long idTipoDoc = 1L;
            if (visMap.get("idTipoDoc") != null) {
                try { idTipoDoc = Long.valueOf(visMap.get("idTipoDoc").toString()); } catch (Exception ignored) {}
            }
            // F7-02: client-supplied idPersona / idVisitante are ignored; the visitor identity is
            // resolved only from the document below. The elevated block that follows is limited to the
            // global visitor-identity upsert (PERSONAS is globally unique by document and VISITANTES
            // INSERT is RLS-checked against existing visits) and runs only after all scope checks passed.
            Long personaId = null;
            // DEC-01: the visitor person belongs to the organization of the caller context, resolved before any elevation.
            Long personaOrgId = com.saed.backend.person.service.PersonaOrganizationBinding.contextOrganization(jdbcTemplate);

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
                        KeyHolder kh = com.saed.backend.person.service.PersonaOrganizationBinding.insertNaturalPerson(
                            jdbcTemplate, personaOrgId, idTipoDoc,
                            !doc.isEmpty() ? doc : "V-" + System.currentTimeMillis(), nom, ape.isBlank() ? "N/A" : ape,
                            email.isBlank() ? null : email, tel.isBlank() ? null : tel);
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

            } finally {
                restoreSaedContext(prevCtx);
            }

            // The visitor row is resolved under the caller's own context by the security-schema resolver (V5.67):
            // the person may already be a visitor the caller cannot see, and the unique key is global.
            if (personaId != null) {
                try {
                    visitanteId = new VisitorIdentityRegistry(jdbcTemplate).resolveByPerson(personaId, esFrecuente, null);
                } catch (org.springframework.security.access.AccessDeniedException denied) {
                    throw denied;
                } catch (RuntimeException e) {
                    log.warning("Error resolviendo ID_VISITANTE (" + e.getClass().getSimpleName() + ")");
                }
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
            try { validezMin = Integer.parseInt(body.get("tiempoValidezMin").toString()); } catch (NumberFormatException ignored) {
                validezMin = 1440;
            }
        }
        // F07-01: bounded validity; the service also clamps the expiry and issues the token itself.
        validezMin = Math.max(5, Math.min(7 * 24 * 60, validezMin));

        // Generar QR automatico y notificar al VISITANTE (token emitido por el servicio)
        QrAccesoRequestDTO qrReq = new QrAccesoRequestDTO(
            visita.idVisita(),
            UUID.randomUUID().toString(),
            ZonedDateTime.now(ZoneId.of("America/Bogota")).plusMinutes(validezMin),
            1,
            "ACTIVO",
            autorizadoPor
        );

        QrAccesoDTO qr = porteriaService.generarQrAcceso(qrReq);
        String token = qr.tokenQr();

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
                    emailService.enviarCorreoQRAsync(email, token, qr.fechaExpiracion().toString(), "Visitante SAED");
                    log.info("Despachado envio asincrono de QR a visitante: " + email);
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
        response.put("autorizadoPor", visita.autorizadoPor() != null ? visita.autorizadoPor() : autorizadoPor);
        return response;
    }

    @PostMapping("/visitas/rapida")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_RESIDENTE_CONVIVENCIA') or hasAuthority('SCOPE_PORTERO')")
    public Map<String, Object> programarVisitaRapida(@RequestBody Map<String, Object> body) {
        if (body.get("motivo") == null && body.get("notas") == null) {
            body.put("motivo", "Visita frecuente rápida");
        }
        body.put("metodoIngreso", "CODIGO_QR");
        return programarVisita(body);
    }

    @GetMapping("/visitas/buscar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<Map<String, Object>> buscarVisitantePorDocumento(@RequestParam(required = false) String documento) {
        // C-06: gate-only lookup. Runs under the caller's own context (no SUPERADMIN/bootstrap elevation)
        // and is explicitly scoped to visitors with a visit to a unit in the caller's property.
        // Out-of-scope and nonexistent documents return the same empty response (no existence oracle).
        SaedContext ctx = SaedContextHolder.getContext();
        Long propId = ctx != null ? ctx.getPropertyId() : null;
        if (documento == null || documento.trim().isEmpty() || propId == null) {
            return ResponseEntity.ok(Map.of());
        }
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT p.ID_PERSONA, p.ID_TIPO_DOCUMENTO, p.NUMERO_DOCUMENTO, p.PRIMER_NOMBRE, p.PRIMER_APELLIDO, p.TELEFONO, v.ID_VISITANTE "
                    + "FROM PERSONAS p JOIN VISITANTES v ON p.ID_PERSONA = v.ID_PERSONA "
                    + "WHERE p.NUMERO_DOCUMENTO = :doc "
                    + "AND EXISTS (SELECT 1 FROM VISITAS vi JOIN UNIDADES un ON un.ID_UNIDAD = vi.ID_UNIDAD "
                    + "WHERE vi.ID_VISITANTE = v.ID_VISITANTE AND un.ID_PROPIEDAD = :propId) "
                    + "AND ROWNUM = 1",
                Map.of("doc", documento.trim(), "propId", propId)
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
                res.put("telefono", maskPhone(row.get("TELEFONO")));
                return ResponseEntity.ok(res);
            }
        } catch (Exception e) {
            log.warning("Error buscando visitante por documento: " + e.getMessage());
        }
        return ResponseEntity.ok(Map.of());
    }

    private static String maskPhone(Object raw) {
        if (raw == null) {
            return null;
        }
        String phone = raw.toString().trim();
        if (phone.length() <= 4) {
            return "****";
        }
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    @GetMapping("/visitas/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA', 'SCOPE_PORTERO')")
    public VisitaDTO getVisitaById(@PathVariable Long id) {
        return porteriaService.getVisitaById(id);
    }

    @GetMapping("/unidades/{unidadId}/visitas")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public List<VisitaDTO> getVisitasByUnidad(@PathVariable Long unidadId) {
        return porteriaService.getVisitasByUnidad(unidadId);
    }

    @PutMapping("/visitas/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public VisitaDTO actualizarVisita(@PathVariable Long id, @RequestBody @Valid VisitaRequestDTO request) {
        return porteriaService.actualizarVisita(id, request);
    }

    @PutMapping("/visitas/{id}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public void cancelarVisita(@PathVariable Long id) {
        porteriaService.cancelarVisita(id);
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
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA', 'SCOPE_PORTERO')")
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
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_RESIDENTE_CONVIVENCIA')")
    public QrAccesoDTO generarQrAcceso(@RequestBody @Valid QrAccesoRequestDTO request) {
        return porteriaService.generarQrAcceso(request);
    }

    @PostMapping("/qr/{id}/revocar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public void revocarQr(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        porteriaService.revocarQr(id, body != null ? body.get("motivo") : null);
    }

    @GetMapping("/qr/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_RESIDENTE_CONVIVENCIA', 'SCOPE_PORTERO')")
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
    public void registrarSalidaVehiculo(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, BigDecimal> body) {
        // F07-03: the parking fee is computed server side; a client-supplied costoTotal is ignored.
        porteriaService.registrarSalidaVehiculo(id);
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

    /** Tenant scope for property-level endpoints; never falls back to a default property. */
    private static Long requireCallerPropertyId() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getPropertyId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Missing property context");
        }
        return ctx.getPropertyId();
    }

    private void requireUnitInCallerProperty(Long unitId, Long propId) {
        List<Long> match = jdbcTemplate.query(
            "SELECT ID_UNIDAD FROM UNIDADES WHERE ID_UNIDAD = :u AND ID_PROPIEDAD = :p",
            Map.of("u", unitId, "p", propId), (rs, r) -> rs.getLong("ID_UNIDAD")
        );
        if (match.isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException("La unidad no pertenece a la propiedad asignada");
        }
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
