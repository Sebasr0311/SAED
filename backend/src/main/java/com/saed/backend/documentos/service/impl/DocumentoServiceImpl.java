package com.saed.backend.documentos.service.impl;

import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.impl.FileStorageServiceImpl;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.documentos.repository.DocumentoRepository;
import com.saed.backend.documentos.service.DocumentoService;
import com.saed.backend.platform.service.StorageQuotaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class DocumentoServiceImpl implements DocumentoService {

    private final DocumentoRepository documentoRepository;
    private final StorageQuotaService storageQuotaService;
    private final FileStorageService fileStorageService;

    public DocumentoServiceImpl(DocumentoRepository documentoRepository,
                                StorageQuotaService storageQuotaService,
                                FileStorageService fileStorageService) {
        this.documentoRepository = documentoRepository;
        this.storageQuotaService = storageQuotaService;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public List<DocumentoDTO> getDocumentosAdmin() {
        Long idPropiedad = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getPropertyId() : null;
        return documentoRepository.findAllByPropiedad(idPropiedad);
    }

    @Override
    public List<DocumentoDTO> getDocumentosResidente() {
        Long idPropiedad = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getPropertyId() : null;
        return documentoRepository.findPublicosByPropiedad(idPropiedad);
    }

    @Override
    @Transactional
    public Long uploadDocumento(DocumentoDTO request) {
        Long idOrganizacion = request.getIdOrganizacion() != null
                ? request.getIdOrganizacion()
                : (SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getOrganizationId() : null);
        Long idPropiedad = request.getIdPropiedad() != null
                ? request.getIdPropiedad()
                : (SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getPropertyId() : null);
        Long creadoPor = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getUserId() : null;

        if (idOrganizacion == null) {
            throw new IllegalStateException("Contexto sin organizacion asignada");
        }

        if (request.getTitulo() == null || request.getCategoria() == null) {
            throw new IllegalArgumentException("El titulo y categoria son obligatorios");
        }

        // Validate storage quota if an attachment size is reported
        if (request.getArchivoUrl() != null && request.getArchivoTamanoBytes() != null && request.getArchivoTamanoBytes() > 0) {
            long sizeBytes = request.getArchivoTamanoBytes();
            if (sizeBytes > FileStorageServiceImpl.MAX_FILE_SIZE_BYTES) {
                throw new IllegalArgumentException("El archivo excede el tamano maximo permitido de 10 MB");
            }
            storageQuotaService.validateUpload(idOrganizacion, sizeBytes);
        }

        // 1. Create document metadata
        Long idDoc = documentoRepository.createDocumento(request, idOrganizacion, idPropiedad, creadoPor);

        // 2. Add first version if file data provided
        if (request.getArchivoUrl() != null) {
            if (request.getArchivoNombreOrig() == null) request.setArchivoNombreOrig("documento.pdf");
            if (request.getArchivoMimeType() == null) request.setArchivoMimeType("application/pdf");
            if (request.getArchivoTamanoBytes() == null) request.setArchivoTamanoBytes(0L);
            
            documentoRepository.addVersion(idDoc, request, creadoPor);
        }

        return idDoc;
    }

    @Override
    @Transactional
    public Long uploadDocumentoMultipart(MultipartFile file, String titulo, String categoria,
                                         String descripcion, String esPublicoResidentes, String rolMinimoAcceso) {
        Long idOrganizacion = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getOrganizationId() : null;
        Long idPropiedad = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getPropertyId() : null;
        Long creadoPor = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getUserId() : null;

        if (idOrganizacion == null) {
            throw new IllegalStateException("Contexto sin organizacion asignada");
        }

        if (titulo == null || titulo.isBlank() || categoria == null || categoria.isBlank()) {
            throw new IllegalArgumentException("El titulo y categoria son obligatorios");
        }

        // Stores file validating both single-file limit and cumulative organizational quota
        FileStorageService.StoredFile stored = fileStorageService.store(file, "documentos", idOrganizacion);

        DocumentoDTO doc = new DocumentoDTO();
        doc.setTitulo(titulo.trim());
        doc.setCategoria(categoria.trim());
        doc.setDescripcion(descripcion != null ? descripcion.trim() : null);
        doc.setEsPublicoResidentes(esPublicoResidentes != null ? esPublicoResidentes : "N");
        doc.setRolMinimoAcceso(rolMinimoAcceso != null ? rolMinimoAcceso : "ADMIN_PROPIEDAD");
        doc.setArchivoUrl(stored.relativePath());
        doc.setArchivoNombreOrig(stored.originalFilename());
        doc.setArchivoMimeType(stored.mimeType());
        doc.setArchivoTamanoBytes(stored.sizeBytes());

        Long idDoc = documentoRepository.createDocumento(doc, idOrganizacion, idPropiedad, creadoPor);
        documentoRepository.addVersion(idDoc, doc, creadoPor);

        return idDoc;
    }

    @Override
    public void deleteDocumento(Long idDocumento) {
        Long idPropiedad = SaedContextHolder.getContext() != null ? SaedContextHolder.getContext().getPropertyId() : null;
        documentoRepository.deleteDocumento(idDocumento, idPropiedad);
    }
}
