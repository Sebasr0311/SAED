package com.saed.backend.finanzas.controller;
import com.saed.backend.finanzas.dto.CuotaDTO;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PagosController {
    private final FinanzasService finanzasService;
    public PagosController(FinanzasService finanzasService) { this.finanzasService = finanzasService; }

    @GetMapping("/cuotas")
    public ResponseEntity<List<CuotaDTO>> getCuotasPendientes(@RequestParam(required = false) Boolean pendientes) {
        return ResponseEntity.ok(finanzasService.getCuotasPendientes());
    }

    @PostMapping("/pagos")
    public ResponseEntity<Map<String, Object>> registrarPago(@Valid @RequestBody PagoRequestDTO request) {
        finanzasService.registrarPago(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true));
    }
    
    @GetMapping("/multas/todas")
    public ResponseEntity<List<Object>> getMultas() {
        return ResponseEntity.ok(List.of()); 
    }
}
