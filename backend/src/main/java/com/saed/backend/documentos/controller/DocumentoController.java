package com.saed.backend.documentos.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.documentos.service.DocumentoService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documentos")
public class DocumentoController {

    private final DocumentoService documentoService;

    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Map<String, Object>> getDocumentosAdmin() {
        List<DocumentoDTO> docs = documentoService.getDocumentosAdmin();
        Map<String, Object> response = new HashMap<>();
        response.put("items", docs);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/residente")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<Map<String, Object>> getDocumentosResidente() {
        List<DocumentoDTO> docs = documentoService.getDocumentosResidente();
        Map<String, Object> response = new HashMap<>();
        response.put("items", docs);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<Map<String, Long>> uploadDocumento(@RequestBody DocumentoDTO request) {
        Long id = documentoService.uploadDocumento(request);
        return ResponseEntity.ok(Map.of("idDocumento", id));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPLOAD_DOCUMENT", resource = "DOCUMENTO", category = AuditCategory.SECURITY, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> uploadDocumentoFile(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam("titulo") String titulo,
            @RequestParam("categoria") String categoria,
            @RequestParam(value = "descripcion", required = false) String descripcion,
            @RequestParam(value = "esPublicoResidentes", defaultValue = "N") String esPublicoResidentes,
            @RequestParam(value = "rolMinimoAcceso", defaultValue = "ADMIN_PROPIEDAD") String rolMinimoAcceso) {

        Long idDoc = documentoService.uploadDocumentoMultipart(
                archivo, titulo, categoria, descripcion, esPublicoResidentes, rolMinimoAcceso
        );

        return ResponseEntity.ok(Map.of(
                "idDocumento", idDoc,
                "nombreArchivo", archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "documento",
                "tamanoBytes", archivo.getSize()
        ));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "DELETE_DOCUMENT", resource = "DOCUMENTO", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public ResponseEntity<Void> deleteDocumento(@PathVariable Long id) {
        documentoService.deleteDocumento(id);
        return ResponseEntity.ok().build();
    }
}
