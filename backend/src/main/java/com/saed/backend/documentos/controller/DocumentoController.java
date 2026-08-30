package com.saed.backend.documentos.controller;

import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.documentos.service.DocumentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Map<String, Object>> getDocumentosAdmin() {
        List<DocumentoDTO> docs = documentoService.getDocumentosAdmin();
        Map<String, Object> response = new HashMap<>();
        response.put("items", docs);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/residente")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<Map<String, Object>> getDocumentosResidente() {
        List<DocumentoDTO> docs = documentoService.getDocumentosResidente();
        Map<String, Object> response = new HashMap<>();
        response.put("items", docs);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Map<String, Long>> uploadDocumento(@RequestBody DocumentoDTO request) {
        Long id = documentoService.uploadDocumento(request);
        return ResponseEntity.ok(Map.of("idDocumento", id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN_PROPIEDAD')")
    public ResponseEntity<Void> deleteDocumento(@PathVariable Long id) {
        documentoService.deleteDocumento(id);
        return ResponseEntity.ok().build();
    }
}
