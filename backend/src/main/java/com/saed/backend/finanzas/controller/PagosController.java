package com.saed.backend.finanzas.controller;
import com.saed.backend.finanzas.dto.CuotaDTO;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;

@Tag(name = "Pagos", description = "API para la gestion de Pagos")
@RestController
@RequestMapping("/api/v1")
public class PagosController {
    private static final Logger log = LoggerFactory.getLogger(PagosController.class);

    private final FinanzasService finanzasService;
    private final com.saed.backend.finanzas.service.WompiService wompiService;
    public PagosController(FinanzasService finanzasService, com.saed.backend.finanzas.service.WompiService wompiService) { this.finanzasService = finanzasService; this.wompiService = wompiService; }

    @GetMapping("/cuotas")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<List<CuotaDTO>> getCuotasPendientes(@RequestParam(required = false) Boolean pendientes) {
        return ResponseEntity.ok(finanzasService.getCuotasPendientes());
    }

    @PostMapping("/pagos")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public ResponseEntity<Map<String, Object>> registrarPago(@Valid @RequestBody PagoRequestDTO request) {
        finanzasService.registrarPago(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true));
    }

    @PostMapping({"/pagos/wompi/webhook", "/pagos/notificacion"})
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> wompiWebhook(@RequestBody String payload) {
        try {
            wompiService.procesarWebhook(payload);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error procesando webhook Wompi", e);
            return ResponseEntity.ok().build(); // Wompi espera 200 siempre si no es error de red
        }
    }
}


