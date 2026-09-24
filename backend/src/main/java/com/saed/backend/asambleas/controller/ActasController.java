package com.saed.backend.asambleas.controller;

import com.saed.backend.asambleas.dto.ActaCreateRequestDTO;
import com.saed.backend.asambleas.dto.ActaDTO;
import com.saed.backend.asambleas.dto.ActaEstadoUpdateRequestDTO;
import com.saed.backend.asambleas.dto.ActaUpdateRequestDTO;
import com.saed.backend.asambleas.service.ActaService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.documentos.service.DocumentoService.DocumentoDescarga;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/actas")
public class ActasController {

    private final ActaService actaService;

    public ActasController(ActaService actaService) {
        this.actaService = actaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<List<ActaDTO>> listarActas(
            @RequestParam(required = false) Long idPropiedad,
            @RequestParam(required = false) String estado) {
        SaedContext ctx = SaedContextHolder.getContext();
        String role = (ctx != null) ? ctx.getRoleCode() : "";

        if ("RESIDENTE".equalsIgnoreCase(role) || "PROPIETARIO_UNIDAD".equalsIgnoreCase(role)
                || "PROPIETARIO".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
            return ResponseEntity.ok(actaService.listarResidente());
        }

        return ResponseEntity.ok(actaService.listarAdmin(idPropiedad, estado));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<ActaDTO> obtenerDetalleActa(@PathVariable Long id) {
        return ResponseEntity.ok(actaService.obtenerPorId(id));
    }

    @GetMapping("/asamblea/{idAsamblea}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<ActaDTO> obtenerPorAsamblea(@PathVariable Long idAsamblea) {
        return ResponseEntity.ok(actaService.obtenerPorAsamblea(idAsamblea));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ActaDTO> crearBorrador(@Valid @RequestBody ActaCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(actaService.crearBorrador(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ActaDTO> actualizarActa(
            @PathVariable Long id,
            @Valid @RequestBody ActaUpdateRequestDTO request) {
        return ResponseEntity.ok(actaService.actualizarActa(id, request));
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ActaDTO> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody ActaEstadoUpdateRequestDTO request) {
        return ResponseEntity.ok(actaService.cambiarEstado(id, request));
    }

    @PostMapping("/{id}/documento")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ActaDTO> asociarDocumento(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "idDocumento", required = false) Long idDocumento,
            @RequestParam(value = "documentoUrl", required = false) String documentoUrl) {
        if (file != null && !file.isEmpty()) {
            return ResponseEntity.ok(actaService.subirYAsociarDocumento(id, file));
        }
        return ResponseEntity.ok(actaService.asociarDocumento(id, idDocumento, documentoUrl));
    }

    @GetMapping("/{id}/documento")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<Resource> descargarDocumento(@PathVariable Long id) {
        DocumentoDescarga descarga = actaService.descargarDocumento(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.mimeType()))
                .contentLength(descarga.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + descarga.filename() + "\"")
                .body(descarga.resource());
    }
}
