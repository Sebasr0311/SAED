package com.saed.backend.documentos.service.impl;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.documentos.dto.DocumentoDTO;
import com.saed.backend.documentos.repository.DocumentoRepository;
import com.saed.backend.documentos.service.DocumentoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentoServiceImpl implements DocumentoService {

    private final DocumentoRepository documentoRepository;

    public DocumentoServiceImpl(DocumentoRepository documentoRepository) {
        this.documentoRepository = documentoRepository;
    }

    @Override
    public List<DocumentoDTO> getDocumentosAdmin() {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        return documentoRepository.findAllByPropiedad(idPropiedad);
    }

    @Override
    public List<DocumentoDTO> getDocumentosResidente() {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        return documentoRepository.findPublicosByPropiedad(idPropiedad);
    }

    @Override
    @Transactional
    public Long uploadDocumento(DocumentoDTO request) {
        Long idOrganizacion = SaedContextHolder.getContext().getOrganizationId();
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long creadoPor = SaedContextHolder.getContext().getUserId();

        if (idOrganizacion == null) {
            throw new IllegalStateException("Contexto sin organizacion asignada");
        }

        if (request.getTitulo() == null || request.getCategoria() == null) {
            throw new IllegalArgumentException("El titulo y categoria son obligatorios");
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
    public void deleteDocumento(Long idDocumento) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        documentoRepository.deleteDocumento(idDocumento, idPropiedad);
    }
}
