package com.saed.backend.documentos.service;

import com.saed.backend.documentos.dto.DocumentoDTO;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface DocumentoService {
    List<DocumentoDTO> getDocumentosAdmin();
    List<DocumentoDTO> getDocumentosResidente();
    Long uploadDocumento(DocumentoDTO request);
    Long uploadDocumentoMultipart(MultipartFile file, String titulo, String categoria,
                                 String descripcion, String esPublicoResidentes, String rolMinimoAcceso);
    void deleteDocumento(Long idDocumento);
}
