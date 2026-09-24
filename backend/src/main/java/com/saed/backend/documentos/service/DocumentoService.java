package com.saed.backend.documentos.service;

import com.saed.backend.documentos.dto.DocumentoDTO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface DocumentoService {

    record DocumentoDescarga(
        Resource resource,
        String filename,
        String mimeType,
        long sizeBytes
    ) {}

    List<DocumentoDTO> getDocumentosAdmin();
    List<DocumentoDTO> getDocumentosResidente();
    DocumentoDTO getDocumentoById(Long idDocumento);
    DocumentoDescarga downloadDocumento(Long idDocumento);
    Long uploadDocumento(DocumentoDTO request);
    Long uploadDocumentoMultipart(MultipartFile file, String titulo, String categoria,
                                 String descripcion, String esPublicoResidentes, String rolMinimoAcceso);
    void updateDocumento(Long idDocumento, DocumentoDTO request);
    void deleteDocumento(Long idDocumento);
    void addVersionMultipart(Long idDocumento, MultipartFile file, String notasCambio);
}
