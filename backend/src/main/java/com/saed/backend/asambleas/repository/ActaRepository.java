package com.saed.backend.asambleas.repository;

import com.saed.backend.asambleas.dto.ActaCreateRequestDTO;
import com.saed.backend.asambleas.dto.ActaDTO;
import com.saed.backend.asambleas.dto.ActaUpdateRequestDTO;

import java.util.List;
import java.util.Optional;

public interface ActaRepository {

    Optional<ActaDTO> findById(Long idActa);

    Optional<ActaDTO> findByAsambleaId(Long idAsamblea);

    List<ActaDTO> findAllAdmin(Long idPropiedad, Long idOrganizacion, String estado);

    List<ActaDTO> findAllResidente(Long idPropiedad);

    Long createActa(ActaCreateRequestDTO request, Long idUsuarioRedactor);

    void updateActa(Long idActa, ActaUpdateRequestDTO request);

    void updateEstado(Long idActa, String nuevoEstado);

    void asociarDocumento(Long idActa, Long idDocumento, String documentoUrl);

    void lockActaForUpdate(Long idActa);

    boolean existsByAsambleaId(Long idAsamblea);
}
