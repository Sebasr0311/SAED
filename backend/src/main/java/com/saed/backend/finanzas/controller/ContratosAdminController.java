package com.saed.backend.finanzas.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.repository.ContratoProveedorRepository;
import com.saed.backend.finanzas.service.ContratoParticipanteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "ContratosAdmin", description = "API para coarrendatarios y contratos de proveedor")
@RestController
@RequestMapping("/api/v1/contratos-admin")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class ContratosAdminController {

    private final ContratoParticipanteService participanteService;
    private final ContratoProveedorRepository contratoProveedorRepo;

    public ContratosAdminController(ContratoParticipanteService participanteService, ContratoProveedorRepository contratoProveedorRepo) {
        this.participanteService = participanteService;
        this.contratoProveedorRepo = contratoProveedorRepo;
    }

    // --- COARRENDATARIOS ---
    @Operation(summary = "Listar coarrendatarios de un contrato")
    @GetMapping("/coarrendatarios/{idContrato}")
    public ResponseEntity<ApiResponse<List<CoarrendatarioDTO>>> listarCoarrendatarios(@PathVariable Long idContrato) {
        return ResponseEntity.ok(ApiResponse.success(participanteService.listarCoarrendatarios(idContrato)));
    }

    @Operation(summary = "Agregar coarrendatario a un contrato")
    @PostMapping("/coarrendatarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<CoarrendatarioDTO>> crearCoarrendatario(@RequestBody CoarrendatarioCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(participanteService.agregarCoarrendatario(request)));
    }

    @Operation(summary = "Cambiar estado de coarrendatario")
    @PatchMapping("/coarrendatarios/{id}/estado")
    public ResponseEntity<ApiResponse<Void>> actualizarEstadoCoarrendatario(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        participanteService.actualizarEstadoCoarrendatario(id, body.getOrDefault("estado", "ACTIVO"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Eliminar coarrendatario")
    @DeleteMapping("/coarrendatarios/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarCoarrendatario(@PathVariable Long id) {
        participanteService.eliminarCoarrendatario(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // --- CONTRATOS PROVEEDOR ---
    @Operation(summary = "Listar contratos de proveedor")
    @GetMapping("/proveedores")
    public ResponseEntity<ApiResponse<List<ContratoProveedorDTO>>> listarContratosProveedor() {
        return ResponseEntity.ok(ApiResponse.success(contratoProveedorRepo.listar()));
    }

    @Operation(summary = "Crear contrato de proveedor")
    @PostMapping("/proveedores")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<ContratoProveedorDTO>> crearContratoProveedor(@RequestBody ContratoProveedorCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(contratoProveedorRepo.crear(request)));
    }

    @Operation(summary = "Cambiar estado de contrato de proveedor")
    @PatchMapping("/proveedores/{id}/estado")
    public ResponseEntity<ApiResponse<Void>> actualizarEstadoContratoProveedor(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        contratoProveedorRepo.actualizarEstado(id, body.getOrDefault("estado", "VIGENTE"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Eliminar contrato de proveedor")
    @DeleteMapping("/proveedores/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarContratoProveedor(@PathVariable Long id) {
        contratoProveedorRepo.eliminar(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
