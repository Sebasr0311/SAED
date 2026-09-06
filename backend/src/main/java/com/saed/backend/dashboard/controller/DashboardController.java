package com.saed.backend.dashboard.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.List;
import java.util.Map;

@Tag(name = "Dashboard", description = "API para la gestion de Dashboard")
@RestController
@RequestMapping("/api/v1/residentes")
public class DashboardController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    public DashboardController(NamedParameterJdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> getResidentes(@RequestParam(required = false) Long idApartamento) {
        if (idApartamento == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
            "SELECT p.ID_PERSONA, p.NUMERO_DOCUMENTO, p.NOMBRES, p.APELLIDOS, p.TELEFONO, p.EMAIL " +
            "FROM UNIDADES_HABITANTES uh " +
            "JOIN PERSONAS p ON uh.ID_PERSONA = p.ID_PERSONA " +
            "WHERE uh.ID_UNIDAD = :idApto AND uh.ACTIVO = 'S'",
            Map.of("idApto", idApartamento)
        );
    }

    @GetMapping("/{id}/frecuentes")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getFrecuentes(@PathVariable Long id) {
        Long userId = com.saed.backend.context.SaedContextHolder.getContext().getUserId();
        if (userId != null) {
            try {
                Long myPersonaId = jdbcTemplate.queryForObject(
                    "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                    Map.of("u", userId), Long.class);
                if (myPersonaId != null && !myPersonaId.equals(id)) {
                    throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar visitantes de otro residente");
                }
            } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {}
        }
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT v.ID_VISITANTE, v.ID_PERSONA, v.EMPRESA, v.ES_FRECUENTE " +
                "FROM VISITANTES v " +
                "JOIN VISITAS vi ON v.ID_VISITANTE = vi.ID_VISITANTE " +
                "WHERE v.ES_FRECUENTE = 'S'", Map.of());
    }

    @DeleteMapping("/{id}/frecuentes/{idFrecuente}")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Void> deleteFrecuente(@PathVariable Long id, @PathVariable Long idFrecuente) {
        Long userId = com.saed.backend.context.SaedContextHolder.getContext().getUserId();
        if (userId != null) {
            try {
                Long myPersonaId = jdbcTemplate.queryForObject(
                    "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                    Map.of("u", userId), Long.class);
                if (myPersonaId != null && !myPersonaId.equals(id)) {
                    throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para modificar visitantes de otro residente");
                }
            } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {}
        }
        jdbcTemplate.update("UPDATE VISITANTES SET ES_FRECUENTE = 'N' WHERE ID_VISITANTE = :id", Map.of("id", idFrecuente));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/qr-activos")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getQrActivos(@PathVariable Long id) {
        Long userId = com.saed.backend.context.SaedContextHolder.getContext().getUserId();
        if (userId != null) {
            try {
                Long myPersonaId = jdbcTemplate.queryForObject(
                    "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                    Map.of("u", userId), Long.class);
                if (myPersonaId != null && !myPersonaId.equals(id)) {
                    throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para consultar QRs de otro residente");
                }
            } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {}
        }
        return jdbcTemplate.queryForList(
                "SELECT q.ID_QR, q.ID_VISITA, q.TOKEN_QR AS TOKEN, q.FECHA_EXPIRACION, q.ESTADO " +
                "FROM QR_ACCESOS q JOIN VISITAS v ON q.ID_VISITA = v.ID_VISITA WHERE q.ESTADO = 'ACTIVO'", Map.of());
    }
    
    @PostMapping("/{id}/asignar-apartamento")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> asignarApartamento(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("idApartamento")) {
            return ResponseEntity.badRequest().build();
        }
        Object aptVal = payload.get("idApartamento");
        Long unitId = null;
        if (aptVal instanceof Number num) {
            unitId = num.longValue();
        } else if (aptVal != null) {
            try {
                unitId = Long.parseLong(aptVal.toString().trim());
            } catch (NumberFormatException ignored) {}
        }
        if (unitId == null) {
            return ResponseEntity.badRequest().build();
        }

        String rol = payload.containsKey("rolEnContrato") && payload.get("rolEnContrato") != null
                ? payload.get("rolEnContrato").toString()
                : (payload.containsKey("tipoResidente") && payload.get("tipoResidente") != null
                ? payload.get("tipoResidente").toString()
                : "RESIDENTE");

        // Verify if relationship already exists
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                Map.of("unitId", unitId, "personaId", id),
                Integer.class
        );

        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO RESIDENTES_UNIDAD (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE) VALUES (:unitId, :personaId, :tipoResidente)",
                    Map.of("unitId", unitId, "personaId", id, "tipoResidente", rol)
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE RESIDENTES_UNIDAD SET TIPO_RESIDENTE = :tipoResidente WHERE ID_UNIDAD = :unitId AND ID_PERSONA = :personaId",
                    Map.of("unitId", unitId, "personaId", id, "tipoResidente", rol)
            );
        }
        return ResponseEntity.ok().build();
    }
}

