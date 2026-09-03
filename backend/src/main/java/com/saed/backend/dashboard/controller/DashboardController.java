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
    public ResponseEntity<Void> asignarApartamento(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok().build();
    }
}

