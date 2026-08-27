package com.saed.backend.comunicacion.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Tag(name = "Wompi", description = "API para la gestion de Wompi")
@RestController
@RequestMapping("/api/v1/pagos")
public class WompiController {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    public WompiController(NamedParameterJdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @GetMapping("/registrados")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getPagosRegistrados() {
        return jdbcTemplate.queryForList("SELECT * FROM PAGOS ORDER BY FECHA_PAGO DESC", Map.of());
    }

    @GetMapping("/wompi/historial")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public List<Map<String, Object>> getHistorialWompi() {
        return jdbcTemplate.queryForList("SELECT * FROM TRANSACCIONES_PAGO WHERE PASARELA = 'WOMPI' ORDER BY FECHA_REGISTRO DESC", Map.of());
    }

    @GetMapping("/wompi/estado")
    public Map<String, Object> getEstadoWompi(@RequestParam String referencia) {
        return Map.of("estado", "APPROVED");
    }

    @PostMapping("/wompi/solicitud")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public Map<String, Object> postSolicitud(@RequestBody Map<String, Object> payload) {
        return Map.of("referencia", "WOMPI-" + System.currentTimeMillis());
    }

    @PostMapping("/wompi/transaccion")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Void> postTransaccion(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok().build();
    }
}

