package com.saed.backend.comunicacion.controller;

import com.saed.backend.platform.annotation.RequireModule;
import com.saed.backend.finanzas.service.WompiService;
import com.saed.backend.finanzas.service.impl.WompiServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Map;

@Tag(name = "Wompi", description = "API para la gestion de pagos con Wompi")
@RestController
@RequestMapping("/api/v1/pagos")
public class WompiController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final WompiService wompiService;
    private final WompiServiceImpl wompiServiceImpl;

    public WompiController(NamedParameterJdbcTemplate jdbcTemplate, WompiService wompiService, WompiServiceImpl wompiServiceImpl) {
        this.jdbcTemplate = jdbcTemplate;
        this.wompiService = wompiService;
        this.wompiServiceImpl = wompiServiceImpl;
    }

    @RequireModule("FINANZAS")
    @GetMapping("/registrados")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getPagosRegistrados() {
        // Explicit columns only: approvers, rejecters and the idempotency key are internal (F6-22).
        return jdbcTemplate.queryForList(
            "SELECT ID_PAGO, ID_UNIDAD, MONTO_TOTAL, METODO_PAGO, REFERENCIA_COMPROBANTE, FECHA_PAGO, ESTADO " +
            "FROM PAGOS ORDER BY FECHA_PAGO DESC", new MapSqlParameterSource());
    }

    @RequireModule("FINANZAS")
    @GetMapping("/wompi/historial")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getHistorialWompi() {
        return jdbcTemplate.queryForList(
            "SELECT ID_TRANSACCION, REFERENCIA_INTERNA, MONTO_CENTAVOS, ESTADO_PASARELA, METODO_ORIGEN, FECHA_REGISTRO " +
            "FROM TRANSACCIONES_PAGO WHERE PASARELA = 'WOMPI' AND ID_UNIDAD IS NOT NULL ORDER BY FECHA_REGISTRO DESC",
            new MapSqlParameterSource()
        );
    }

    @GetMapping("/wompi/estado")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_RESIDENTE')")
    public Map<String, Object> getEstadoWompi(@RequestParam String referencia) {
        return wompiServiceImpl.estadoIntencion(referencia);
    }

    @RequireModule("FINANZAS")
    @PostMapping("/wompi/solicitud")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public Map<String, Object> postSolicitud(@RequestBody Map<String, Object> payload) throws Exception {
        // Exceptions keep their type so the global handler maps them (404, 400, 409) instead of a blanket 500.
        String concepto = (String) payload.getOrDefault("concepto", "CUOTA");
        Object idObj = payload.get("idItem") != null ? payload.get("idItem") : payload.get("id");
        if (idObj == null) {
            throw new IllegalArgumentException("idItem o id es requerido");
        }
        Long idItem;
        try {
            idItem = Long.valueOf(String.valueOf(idObj));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("idItem invalido");
        }
        return wompiService.crearIntencion(concepto, idItem);
    }

    @RequireModule("FINANZAS")
    @PostMapping("/wompi/transaccion")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Void> postTransaccion(@RequestBody Map<String, Object> payload) {
        // Confirmacion opcional del frontend: la fuente de verdad es el webhook.
        return ResponseEntity.ok().build();
    }
}