package com.saed.backend.comunicacion.controller;

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

    @GetMapping("/registrados")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getPagosRegistrados() {
        return jdbcTemplate.queryForList("SELECT * FROM PAGOS ORDER BY FECHA_PAGO DESC", new MapSqlParameterSource());
    }

    @GetMapping("/wompi/historial")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getHistorialWompi() {
        return jdbcTemplate.queryForList(
            "SELECT ID_TRANSACCION, REFERENCIA_INTERNA, MONTO_CENTAVOS, ESTADO_PASARELA, METODO_ORIGEN, FECHA_REGISTRO " +
            "FROM TRANSACCIONES_PAGO WHERE PASARELA = 'WOMPI' ORDER BY FECHA_REGISTRO DESC",
            new MapSqlParameterSource()
        );
    }

    @GetMapping("/wompi/estado")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public Map<String, Object> getEstadoWompi(@RequestParam String referencia) {
        return wompiServiceImpl.estadoIntencion(referencia);
    }

    @PostMapping("/wompi/solicitud")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public Map<String, Object> postSolicitud(@RequestBody Map<String, Object> payload) {
        try {
            String concepto = (String) payload.getOrDefault("concepto", "CUOTA");
            Long idItem = Long.valueOf(String.valueOf(payload.get("idItem")));
            return wompiService.crearIntencion(concepto, idItem);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @PostMapping("/wompi/transaccion")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Void> postTransaccion(@RequestBody Map<String, Object> payload) {
        // Confirmacion opcional del frontend: la fuente de verdad es el webhook.
        return ResponseEntity.ok().build();
    }
}