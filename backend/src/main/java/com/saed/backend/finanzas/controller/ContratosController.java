package com.saed.backend.finanzas.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.finanzas.dto.ContratoDTO;
import com.saed.backend.finanzas.dto.ContratoDetalleDTO;
import com.saed.backend.finanzas.dto.ContratoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Contratos", description = "API para la gestión y descarga documental de Contratos de Arrendamiento")
@RestController
@RequestMapping("/api/v1/contratos")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_RESIDENTE_CONVIVENCIA')")
public class ContratosController {

    private final FinanzasService finanzasService;

    public ContratosController(FinanzasService finanzasService) {
        this.finanzasService = finanzasService;
    }

    @Operation(summary = "Listar contratos visibles según el rol del usuario")
    @GetMapping
    public ResponseEntity<List<ContratoDTO>> getContratos() {
        return ResponseEntity.ok(finanzasService.getContratos());
    }

    @Operation(summary = "Consultar el detalle de un contrato específico con validación anti-IDOR")
    @GetMapping("/{id}")
    public ResponseEntity<ContratoDetalleDTO> getContratoPorId(@PathVariable Long id) {
        return ResponseEntity.ok(finanzasService.getContratoDetalle(id));
    }

    @Operation(summary = "Descargar el documento PDF oficial del contrato con verificación de integridad SHA-256")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> descargarPdf(@PathVariable Long id) {
        ContratoDetalleDTO detalle = finanzasService.getContratoDetalle(id);
        Resource resource = finanzasService.descargarPdfContrato(id);

        String num = detalle.getNumeroContrato() != null ? detalle.getNumeroContrato() : String.valueOf(id);
        String filename = "contrato_" + num + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        if (detalle.getDocumentoHash() != null) {
            headers.add("X-Content-Sha256", detalle.getDocumentoHash());
        }

        return ResponseEntity.ok().headers(headers).body(resource);
    }

    @Operation(summary = "Crear un nuevo contrato de arrendamiento (Administración)")
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "CREATE", resource = "CONTRATO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<Map<String, Object>> createContrato(@Valid @RequestBody ContratoRequestDTO request) {
        Long id = finanzasService.createContrato(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "id", id));
    }

    @Operation(summary = "Activar formalmente un contrato de arrendamiento")
    @PostMapping("/{id}/activar")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "ACTIVATE", resource = "CONTRATO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<Map<String, Object>> activarContrato(@PathVariable Long id) {
        finanzasService.actualizarEstadoContrato(id, "ACTIVO");
        return ResponseEntity.ok(Map.of("success", true));
    }

    @Operation(summary = "Cancelar formalmente un contrato de arrendamiento")
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "CANCEL", resource = "CONTRATO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<Map<String, Object>> cancelarContrato(@PathVariable Long id) {
        finanzasService.actualizarEstadoContrato(id, "CANCELADO");
        return ResponseEntity.ok(Map.of("success", true));
    }
}
