package com.saed.backend.documentos.service;

import com.saed.backend.documentos.dto.DocumentoDTO;
import java.util.List;

public interface DocumentoService {
    List<DocumentoDTO> getDocumentosAdmin();
    List<DocumentoDTO> getDocumentosResidente();
    Long uploadDocumento(DocumentoDTO request);
    void deleteDocumento(Long idDocumento);
}
