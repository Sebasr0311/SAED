package com.saed.backend.finanzas.controller;
import com.saed.backend.finanzas.dto.ResidenteDashboardDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/residentes")
public class ResidentesFinanzasController {
    private final FinanzasService finanzasService;
    public ResidentesFinanzasController(FinanzasService finanzasService) { this.finanzasService = finanzasService; }

    @GetMapping("/{id}/dashboard")
    public ResponseEntity<ResidenteDashboardDTO> getDashboard(@PathVariable Long id) {
        return ResponseEntity.ok(finanzasService.getDashboardResidente(id));
    }
}
