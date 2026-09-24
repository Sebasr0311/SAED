package com.saed.backend.documentos.service.impl;

import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.impl.FileStorageServiceImpl;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.documentos.repository.DocumentoRepository;
import com.saed.backend.documentos.service.DocumentoService;
import com.saed.backend.platform.service.StorageQuotaService;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class DocumentoServiceImpl implements DocumentoService {

    public static final Set<String> CATEGORIAS_VALIDAS = Set.of(
            "REGLAMENTO_INTERNO",
            "RUT_MATRICULA",
            "ACTA_ASAMBLEA",
            "CONTRATO_PROVEEDOR",
            "POLIZA_SEGURO",
            "ESTADO_FINANCIERO",
            "PLANOS",
            "MANUAL_CONVIVENCIA",
            "OTRO"
    );

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

    private void validateCategoria(String categoria) {
        if (categoria == null || !CATEGORIAS_VALIDAS.contains(categoria.trim())) {
            throw new IllegalArgumentException("Categoría inválida: '" + categoria + "'. Debe ser una de: " + CATEGORIAS_VALIDAS);
        }
    }

    private void validateDocumentAccess(DocumentoDTO doc) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) {
            throw new AccessDeniedException("Contexto de seguridad no autenticado");
        }
        String role = ctx.getRoleCode();
        if ("SUPERADMIN".equalsIgnoreCase(role)) {
            return;
        }

        // Porteros do not have access to document repository
        if ("PORTERO".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Rol sin privilegios para acceder al repositorio documental");
        }

        // Organization verification
        if (ctx.getOrganizationId() != null && doc.getIdOrganizacion() != null
                && !ctx.getOrganizationId().equals(doc.getIdOrganizacion())) {
            throw new AccessDeniedException("No autorizado para acceder a documentos de otra organización");
        }

        // Admin Propiedad verification
        if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            if (ctx.getPropertyId() != null && doc.getIdPropiedad() != null
                    && !ctx.getPropertyId().equals(doc.getIdPropiedad())) {
                throw new AccessDeniedException("No autorizado para acceder a documentos de otra propiedad");
            }
            return;
        }

        // Admin Organizacion verification
        if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role)) {
            if (ctx.getOrganizationId() != null && doc.getIdOrganizacion() != null
                    && !ctx.getOrganizationId().equals(doc.getIdOrganizacion())) {
                throw new AccessDeniedException("No autorizado para acceder a documentos de otra organización");
            }
            return;
        }

        // Resident / Copropietario verification
        if ("RESIDENTE".equalsIgnoreCase(role) || "PROPIETARIO_UNIDAD".equalsIgnoreCase(role)
                || "PROPIETARIO".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
            if (!"S".equalsIgnoreCase(doc.getEsPublicoResidentes())) {
                throw new AccessDeniedException("Este documento es confidencial y no está disponible para residentes");
            }
            if (ctx.getPropertyId() != null && doc.getIdPropiedad() != null
                    && !ctx.getPropertyId().equals(doc.getIdPropiedad())) {
                throw new AccessDeniedException("No autorizado para acceder a documentos de otra propiedad");
            }
            return;
        }

        throw new AccessDeniedException("Acceso no autorizado al documento");
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
    public DocumentoDTO getDocumentoById(Long idDocumento) {
        DocumentoDTO doc = documentoRepository.findById(idDocumento)
                .orElseThrow(() -> new NoSuchElementException("Documento no encontrado con ID " + idDocumento));
        validateDocumentAccess(doc);
        return doc;
    }

    @Override
    public DocumentoDescarga downloadDocumento(Long idDocumento) {
        DocumentoDTO doc = getDocumentoById(idDocumento);
        if (doc.getArchivoUrl() == null || doc.getArchivoUrl().isBlank()) {
            throw new IllegalStateException("El documento no tiene un archivo físico asociado");
        }
        Resource resource = fileStorageService.loadAsResource(doc.getArchivoUrl());
        if (resource == null || !resource.exists()) {
            throw new NoSuchElementException("El archivo físico asociado al documento no existe en el almacenamiento");
        }
        String filename = doc.getArchivoNombreOrig() != null && !doc.getArchivoNombreOrig().isBlank()
                ? doc.getArchivoNombreOrig()
                : "documento_" + idDocumento + ".pdf";
        String mimeType = doc.getArchivoMimeType() != null && !doc.getArchivoMimeType().isBlank()
                ? doc.getArchivoMimeType()
                : "application/octet-stream";
        long size = doc.getArchivoTamanoBytes() != null ? doc.getArchivoTamanoBytes() : 0L;
        return new DocumentoDescarga(resource, filename, mimeType, size);
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
            throw new IllegalStateException("Contexto sin organización asignada");
        }

        if (request.getTitulo() == null || request.getTitulo().isBlank() || request.getCategoria() == null || request.getCategoria().isBlank()) {
            throw new IllegalArgumentException("El título y categoría son obligatorios");
        }

        validateCategoria(request.getCategoria());

        // Validate storage quota if an attachment size is reported
        if (request.getArchivoUrl() != null && request.getArchivoTamanoBytes() != null && request.getArchivoTamanoBytes() > 0) {
            long sizeBytes = request.getArchivoTamanoBytes();
            if (sizeBytes > FileStorageServiceImpl.MAX_FILE_SIZE_BYTES) {
                throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido de 10 MB");
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
            throw new IllegalStateException("Contexto sin organización asignada");
        }

        if (titulo == null || titulo.isBlank() || categoria == null || categoria.isBlank()) {
            throw new IllegalArgumentException("El título y categoría son obligatorios");
        }

        validateCategoria(categoria);

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
        doc.setArchivoSha256(stored.sha256());

        Long idDoc = documentoRepository.createDocumento(doc, idOrganizacion, idPropiedad, creadoPor);
        documentoRepository.addVersion(idDoc, doc, creadoPor);

        return idDoc;
    }

    @Override
    @Transactional
    public void updateDocumento(Long idDocumento, DocumentoDTO request) {
        DocumentoDTO existing = getDocumentoById(idDocumento);
        SaedContext ctx = SaedContextHolder.getContext();
        String role = ctx != null ? ctx.getRoleCode() : null;
        if (!"SUPERADMIN".equalsIgnoreCase(role) && !"ADMIN_ORGANIZACION".equalsIgnoreCase(role) && !"ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Solo administradores pueden modificar metadatos de documentos");
        }

        if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            if (existing.getIdPropiedad() == null || !existing.getIdPropiedad().equals(ctx.getPropertyId())) {
                throw new AccessDeniedException("No autorizado para modificar documentos de otra propiedad");
            }
        }

        if (request.getCategoria() != null) {
            validateCategoria(request.getCategoria());
            existing.setCategoria(request.getCategoria().trim());
        }
        if (request.getTitulo() != null && !request.getTitulo().isBlank()) {
            existing.setTitulo(request.getTitulo().trim());
        }
        if (request.getDescripcion() != null) {
            existing.setDescripcion(request.getDescripcion().trim());
        }
        if (request.getEsPublicoResidentes() != null) {
            existing.setEsPublicoResidentes(request.getEsPublicoResidentes());
        }
        if (request.getRolMinimoAcceso() != null) {
            existing.setRolMinimoAcceso(request.getRolMinimoAcceso());
        }

        Long propId = ctx != null ? ctx.getPropertyId() : null;
        documentoRepository.updateDocumento(idDocumento, existing, propId);
    }

    @Override
    @Transactional
    public void deleteDocumento(Long idDocumento) {
        DocumentoDTO existing = getDocumentoById(idDocumento);
        SaedContext ctx = SaedContextHolder.getContext();
        String role = ctx != null ? ctx.getRoleCode() : null;
        if (!"SUPERADMIN".equalsIgnoreCase(role) && !"ADMIN_ORGANIZACION".equalsIgnoreCase(role) && !"ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Solo administradores pueden eliminar documentos");
        }

        if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            if (existing.getIdPropiedad() == null || !existing.getIdPropiedad().equals(ctx.getPropertyId())) {
                throw new AccessDeniedException("No autorizado para eliminar documentos de otra propiedad");
            }
        }

        Long idPropiedad = ctx != null ? ctx.getPropertyId() : null;
        documentoRepository.deleteDocumento(idDocumento, idPropiedad);
    }

    @Override
    @Transactional
    public void addVersionMultipart(Long idDocumento, MultipartFile file, String notasCambio) {
        // 1. Pessimistic lock on parent document row in Oracle to serialize concurrent version uploads
        documentoRepository.lockDocumentoForUpdate(idDocumento);

        DocumentoDTO existing = getDocumentoById(idDocumento);
        SaedContext ctx = SaedContextHolder.getContext();
        String role = ctx != null ? ctx.getRoleCode() : null;
        if (!"SUPERADMIN".equalsIgnoreCase(role) && !"ADMIN_ORGANIZACION".equalsIgnoreCase(role) && !"ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Solo administradores pueden agregar nuevas versiones a documentos");
        }

        if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            if (existing.getIdPropiedad() == null || !existing.getIdPropiedad().equals(ctx.getPropertyId())) {
                throw new AccessDeniedException("No autorizado para versionar documentos de otra propiedad");
            }
        }

        Long idOrganizacion = existing.getIdOrganizacion();
        FileStorageService.StoredFile stored = fileStorageService.store(file, "documentos", idOrganizacion);

        DocumentoDTO newVersion = new DocumentoDTO();
        newVersion.setArchivoUrl(stored.relativePath());
        newVersion.setArchivoNombreOrig(stored.originalFilename());
        newVersion.setArchivoMimeType(stored.mimeType());
        newVersion.setArchivoTamanoBytes(stored.sizeBytes());
        newVersion.setArchivoSha256(stored.sha256());
        newVersion.setNotasCambio(notasCambio != null ? notasCambio.trim() : null);

        Long subidoPor = ctx != null ? ctx.getUserId() : null;
        documentoRepository.addVersion(idDocumento, newVersion, subidoPor);
    }
}
