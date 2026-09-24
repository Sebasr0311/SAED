package com.saed.backend.reglamentos.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.documentos.service.DocumentoService;
import com.saed.backend.reglamentos.dto.ReglamentoCreateRequestDTO;
import com.saed.backend.reglamentos.dto.ReglamentoDTO;
import com.saed.backend.reglamentos.dto.ReglamentoPublicarRequestDTO;
import com.saed.backend.reglamentos.dto.ReglamentoUpdateRequestDTO;
import com.saed.backend.reglamentos.service.ReglamentoService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reglamentos")
public class ReglamentoController {

    private final ReglamentoService reglamentoService;

    public ReglamentoController(ReglamentoService reglamentoService) {
        this.reglamentoService = reglamentoService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReglamentosAdmin(
            @RequestParam(required = false) String tipoNormativa,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long idPropiedad) {
        List<ReglamentoDTO> items = reglamentoService.listAdmin(tipoNormativa, estado, idPropiedad);
        return ResponseEntity.ok(ApiResponse.success(Map.of("items", items)));
    }

    @GetMapping("/residente")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReglamentosResidente(
            @RequestParam(required = false) String tipoNormativa) {
        List<ReglamentoDTO> items = reglamentoService.listResidente(tipoNormativa);
        return ResponseEntity.ok(ApiResponse.success(Map.of("items", items)));
    }

    @GetMapping("/vigente")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<ApiResponse<ReglamentoDTO>> getVigente(
            @RequestParam String tipoNormativa,
            @RequestParam(required = false) Long idPropiedad) {
        ReglamentoDTO dto = reglamentoService.getVigente(tipoNormativa, idPropiedad);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<ApiResponse<ReglamentoDTO>> getById(@PathVariable Long id) {
        ReglamentoDTO dto = reglamentoService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @GetMapping("/{id}/descargar")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<Resource> descargar(@PathVariable Long id) {
        DocumentoService.DocumentoDescarga descarga = reglamentoService.downloadDocumento(id);

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(descarga.mimeType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(descarga.filename(), StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(disposition);
        headers.setContentType(mediaType);
        if (descarga.sizeBytes() > 0) {
            headers.setContentLength(descarga.sizeBytes());
        }
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.EXPIRES, "0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(descarga.resource());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<ReglamentoDTO>> createDraft(
            @Valid @RequestBody ReglamentoCreateRequestDTO request,
            @RequestParam(required = false) Long idPropiedad) {
        ReglamentoDTO created = reglamentoService.createDraft(request, idPropiedad);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<ReglamentoDTO>> updateDraft(
            @PathVariable Long id,
            @Valid @RequestBody ReglamentoUpdateRequestDTO request) {
        ReglamentoDTO updated = reglamentoService.updateDraft(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/{id}/publicar")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<ReglamentoDTO>> publicar(
            @PathVariable Long id,
            @RequestBody(required = false) ReglamentoPublicarRequestDTO request) {
        ReglamentoDTO published = reglamentoService.publicar(id, request);
        return ResponseEntity.ok(ApiResponse.success(published));
    }

    @PostMapping("/{id}/inactivar")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<ReglamentoDTO>> inactivar(@PathVariable Long id) {
        ReglamentoDTO inactivated = reglamentoService.inactivar(id);
        return ResponseEntity.ok(ApiResponse.success(inactivated));
    }
}
