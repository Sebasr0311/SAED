package com.saed.backend.documentos.controller;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.documentos.service.DocumentoService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDocumentosAdmin() {
        List<DocumentoDTO> docs = documentoService.getDocumentosAdmin();
        return ResponseEntity.ok(ApiResponse.success(Map.of("items", docs)));
    }

    @GetMapping("/residente")
    @PreAuthorize("hasAnyAuthority('SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDocumentosResidente() {
        List<DocumentoDTO> docs = documentoService.getDocumentosResidente();
        return ResponseEntity.ok(ApiResponse.success(Map.of("items", docs)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<ApiResponse<DocumentoDTO>> getDocumentoById(@PathVariable Long id) {
        DocumentoDTO doc = documentoService.getDocumentoById(id);
        return ResponseEntity.ok(ApiResponse.success(doc));
    }

    @GetMapping(value = {"/{id}/descargar", "/{id}/archivo"})
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PROPIETARIO', 'SCOPE_PROPIETARIO_UNIDAD', 'SCOPE_RESIDENTE_CONVIVENCIA')")
    public ResponseEntity<Resource> descargarDocumento(@PathVariable Long id) {
        DocumentoService.DocumentoDescarga descarga = documentoService.downloadDocumento(id);

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
    @Auditable(action = "CREATE_DOCUMENT_METADATA", resource = "DOCUMENTO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<ApiResponse<Map<String, Long>>> uploadDocumento(@RequestBody DocumentoDTO request) {
        Long id = documentoService.uploadDocumento(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of("idDocumento", id)));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPLOAD_DOCUMENT", resource = "DOCUMENTO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadDocumentoFile(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam("titulo") String titulo,
            @RequestParam("categoria") String categoria,
            @RequestParam(value = "descripcion", required = false) String descripcion,
            @RequestParam(value = "esPublicoResidentes", defaultValue = "N") String esPublicoResidentes,
            @RequestParam(value = "rolMinimoAcceso", defaultValue = "ADMIN_PROPIEDAD") String rolMinimoAcceso) {

        Long idDoc = documentoService.uploadDocumentoMultipart(
                archivo, titulo, categoria, descripcion, esPublicoResidentes, rolMinimoAcceso
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "idDocumento", idDoc,
                "nombreArchivo", archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "documento",
                "tamanoBytes", archivo.getSize()
        )));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPDATE_DOCUMENT", resource = "DOCUMENTO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<ApiResponse<DocumentoDTO>> updateDocumento(
            @PathVariable Long id,
            @RequestBody DocumentoDTO request) {
        documentoService.updateDocumento(id, request);
        DocumentoDTO updated = documentoService.getDocumentoById(id);
        return ResponseEntity.ok(ApiResponse.success(updated, "Metadatos del documento actualizados correctamente"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "DELETE_DOCUMENT", resource = "DOCUMENTO", category = AuditCategory.SECURITY, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Void>> deleteDocumento(@PathVariable Long id) {
        documentoService.deleteDocumento(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Documento eliminado correctamente"));
    }

    @PostMapping(value = "/{id}/versiones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "ADD_DOCUMENT_VERSION", resource = "DOCUMENTO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<ApiResponse<Map<String, Object>>> addVersion(
            @PathVariable Long id,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(value = "notasCambio", required = false) String notasCambio) {
        documentoService.addVersionMultipart(id, archivo, notasCambio);
        DocumentoDTO updated = documentoService.getDocumentoById(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(Map.of(
                "idDocumento", id,
                "numeroVersion", updated.getNumeroVersion() != null ? updated.getNumeroVersion() : 1,
                "nombreArchivo", archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "documento",
                "tamanoBytes", archivo.getSize()
        ), "Nueva versión del documento subida correctamente"));
    }
}
