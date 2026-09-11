package com.saed.backend.asambleas.controller;

import com.saed.backend.asambleas.dto.*;
import com.saed.backend.asambleas.service.AsambleaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/asambleas")
public class AsambleasController {

    private final AsambleaService asambleaService;

    public AsambleasController(AsambleaService asambleaService) {
        this.asambleaService = asambleaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<List<AsambleaDTO>> listarAsambleas() {
        return ResponseEntity.ok(asambleaService.listarAsambleas());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<AsambleaDTO> obtenerDetalleAsamblea(@PathVariable Long id) {
        return ResponseEntity.ok(asambleaService.obtenerDetalleAsamblea(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<AsambleaDTO> convocarAsamblea(@Valid @RequestBody AsambleaCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asambleaService.convocarAsamblea(request));
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<AsambleaDTO> actualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody AsambleaEstadoUpdateRequestDTO request) {
        return ResponseEntity.ok(asambleaService.actualizarEstado(id, request.getNuevoEstado()));
    }

    @GetMapping("/{id}/quorum")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<QuorumLiveDTO> obtenerQuorumEnVivo(@PathVariable Long id) {
        return ResponseEntity.ok(asambleaService.obtenerQuorumEnVivo(id));
    }

    @GetMapping("/{id}/asistencias")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<List<AsistenciaDTO>> listarAsistencias(@PathVariable Long id) {
        return ResponseEntity.ok(asambleaService.listarAsistencias(id));
    }

    @PostMapping("/{id}/asistencias")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<AsistenciaDTO> registrarAsistencia(
            @PathVariable Long id,
            @Valid @RequestBody AsistenciaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asambleaService.registrarAsistencia(id, request));
    }

    @PutMapping("/{id}/asistencias/{idUnidad}/retiro")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> retirarAsistencia(
            @PathVariable Long id,
            @PathVariable Long idUnidad) {
        asambleaService.retirarAsistencia(id, idUnidad);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/poderes")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<List<PoderDTO>> listarPoderes(@PathVariable Long id) {
        return ResponseEntity.ok(asambleaService.listarPoderes(id));
    }

    @PostMapping("/{id}/poderes")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<PoderDTO> radicarPoder(
            @PathVariable Long id,
            @Valid @RequestBody PoderRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asambleaService.radicarPoder(id, request));
    }

    @PutMapping("/{id}/poderes/{idPoder}/decision")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<PoderDTO> decidirPoder(
            @PathVariable Long id,
            @PathVariable Long idPoder,
            @Valid @RequestBody PoderDecisionDTO request) {
        return ResponseEntity.ok(asambleaService.decidirPoder(idPoder, request.getEstado()));
    }

    @GetMapping("/{id}/votaciones")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<List<VotacionDTO>> listarVotaciones(@PathVariable Long id) {
        return ResponseEntity.ok(asambleaService.listarVotaciones(id));
    }

    @PostMapping("/{id}/votaciones")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<VotacionDTO> crearPuntoVotacion(
            @PathVariable Long id,
            @Valid @RequestBody VotacionCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(asambleaService.crearPuntoVotacion(id, request));
    }

    @PutMapping("/{id}/votaciones/{idVotacion}/cerrar")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<VotacionDTO> cerrarVotacion(
            @PathVariable Long id,
            @PathVariable Long idVotacion) {
        return ResponseEntity.ok(asambleaService.cerrarVotacion(idVotacion));
    }

    @PostMapping("/{id}/votaciones/{idVotacion}/votar")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO')")
    public ResponseEntity<Void> emitirVoto(
            @PathVariable Long id,
            @PathVariable Long idVotacion,
            @Valid @RequestBody VotoRequestDTO request) {
        asambleaService.emitirVoto(idVotacion, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
