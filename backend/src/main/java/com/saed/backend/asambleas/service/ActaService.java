package com.saed.backend.asambleas.service;

import com.saed.backend.asambleas.dto.ActaCreateRequestDTO;
import com.saed.backend.asambleas.dto.ActaDTO;
import com.saed.backend.asambleas.dto.ActaEstadoUpdateRequestDTO;
import com.saed.backend.asambleas.dto.ActaUpdateRequestDTO;
import com.saed.backend.documentos.service.DocumentoService.DocumentoDescarga;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ActaService {

    ActaDTO crearBorrador(ActaCreateRequestDTO request);

    ActaDTO actualizarActa(Long idActa, ActaUpdateRequestDTO request);

    ActaDTO cambiarEstado(Long idActa, ActaEstadoUpdateRequestDTO request);

    ActaDTO asociarDocumento(Long idActa, Long idDocumento, String documentoUrl);

    ActaDTO subirYAsociarDocumento(Long idActa, MultipartFile file);

    ActaDTO obtenerPorId(Long idActa);

    ActaDTO obtenerPorAsamblea(Long idAsamblea);

    List<ActaDTO> listarAdmin(Long idPropiedad, String estado);

    List<ActaDTO> listarResidente();

    DocumentoDescarga descargarDocumento(Long idActa);
}
