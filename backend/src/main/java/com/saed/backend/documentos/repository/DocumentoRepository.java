package com.saed.backend.documentos.repository;

import com.saed.backend.documentos.dto.DocumentoDTO;
import java.util.List;

public interface DocumentoRepository {
    List<DocumentoDTO> findAllByPropiedad(Long idPropiedad);
    List<DocumentoDTO> findPublicosByPropiedad(Long idPropiedad);
    Long createDocumento(DocumentoDTO documento, Long idOrganizacion, Long idPropiedad, Long creadoPor);
    void addVersion(Long idDocumento, DocumentoDTO documento, Long subidoPor);
    void deleteDocumento(Long idDocumento, Long idPropiedad);
}
