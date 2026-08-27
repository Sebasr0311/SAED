package com.saed.backend.finanzas.controller;
import com.saed.backend.finanzas.dto.CuotaDTO;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;

@Tag(name = "Pagos", description = "API para la gestion de Pagos")
@RestController
@RequestMapping("/api/v1")
public class PagosController {
    private final FinanzasService finanzasService;
    private final com.saed.backend.finanzas.service.WompiService wompiService;
    public PagosController(FinanzasService finanzasService, com.saed.backend.finanzas.service.WompiService wompiService) { this.finanzasService = finanzasService; this.wompiService = wompiService; }

    @GetMapping("/cuotas")
    public ResponseEntity<List<CuotaDTO>> getCuotasPendientes(@RequestParam(required = false) Boolean pendientes) {
        return ResponseEntity.ok(finanzasService.getCuotasPendientes());
    }

    @PostMapping("/pagos")
    public ResponseEntity<Map<String, Object>> registrarPago(@Valid @RequestBody PagoRequestDTO request) {
        finanzasService.registrarPago(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true));
    }
    
    
    
    @PostMapping("/pagos/wompi/webhook")
    public ResponseEntity<Void> wompiWebhook(@RequestBody String payload) {
        try {
            // Requiere WompiService injection (anadir despues)
            wompiService.procesarWebhook(payload);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            System.err.println("Error procesando webhook Wompi: " + e.getMessage());
            return ResponseEntity.ok().build(); // Wompi espera 200 siempre si no es error de red
        }
    }
}


