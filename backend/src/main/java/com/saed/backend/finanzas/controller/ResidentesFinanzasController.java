package com.saed.backend.finanzas.controller;
import com.saed.backend.finanzas.dto.ResidenteDashboardDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "ResidentesFinanzas", description = "API para la gestion de ResidentesFinanzas")
@RestController
@RequestMapping("/api/v1/residentes")
@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE')")
public class ResidentesFinanzasController {
    private final FinanzasService finanzasService;
    public ResidentesFinanzasController(FinanzasService finanzasService) { this.finanzasService = finanzasService; }

    @GetMapping("/{id}/dashboard")
    public ResponseEntity<ResidenteDashboardDTO> getDashboard(@PathVariable Long id) {
        return ResponseEntity.ok(finanzasService.getDashboardResidente(id));
    }
}

