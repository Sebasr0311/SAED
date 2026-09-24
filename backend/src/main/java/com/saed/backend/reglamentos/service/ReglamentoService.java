package com.saed.backend.reglamentos.service;

import com.saed.backend.documentos.service.DocumentoService.DocumentoDescarga;
import com.saed.backend.reglamentos.dto.ReglamentoCreateRequestDTO;
import com.saed.backend.reglamentos.dto.ReglamentoDTO;
import com.saed.backend.reglamentos.dto.ReglamentoPublicarRequestDTO;
import com.saed.backend.reglamentos.dto.ReglamentoUpdateRequestDTO;

import java.util.List;

public interface ReglamentoService {
    ReglamentoDTO createDraft(ReglamentoCreateRequestDTO request, Long customPropiedadId);
    ReglamentoDTO updateDraft(Long idReglamento, ReglamentoUpdateRequestDTO request);
    ReglamentoDTO publicar(Long idReglamento, ReglamentoPublicarRequestDTO request);
    ReglamentoDTO inactivar(Long idReglamento);
    ReglamentoDTO getById(Long idReglamento);
    ReglamentoDTO getVigente(String tipoNormativa, Long idPropiedad);
    List<ReglamentoDTO> listAdmin(String tipoNormativa, String estado, Long idPropiedad);
    List<ReglamentoDTO> listResidente(String tipoNormativa);
    DocumentoDescarga downloadDocumento(Long idReglamento);
}
