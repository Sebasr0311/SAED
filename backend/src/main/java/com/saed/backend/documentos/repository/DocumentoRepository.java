package com.saed.backend.documentos.repository;

import com.saed.backend.documentos.dto.DocumentoDTO;
import java.util.List;
import java.util.Optional;

public interface DocumentoRepository {
    List<DocumentoDTO> findAllByPropiedad(Long idPropiedad);
    List<DocumentoDTO> findPublicosByPropiedad(Long idPropiedad);
    Optional<DocumentoDTO> findById(Long idDocumento);
    void lockDocumentoForUpdate(Long idDocumento);
    Long createDocumento(DocumentoDTO documento, Long idOrganizacion, Long idPropiedad, Long creadoPor);
    void addVersion(Long idDocumento, DocumentoDTO documento, Long subidoPor);
    int updateDocumento(Long idDocumento, DocumentoDTO documento, Long idPropiedad);
    void deleteDocumento(Long idDocumento, Long idPropiedad);
}
