package com.saed.backend.reglamentos.repository;

import com.saed.backend.reglamentos.dto.ReglamentoDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReglamentoRepository {
    Long create(ReglamentoDTO dto);
    void update(ReglamentoDTO dto);
    Optional<ReglamentoDTO> findById(Long idReglamento);
    Optional<ReglamentoDTO> findVigenteByPropiedadAndTipo(Long idPropiedad, String tipoNormativa);
    List<ReglamentoDTO> findAllAdmin(Long idPropiedad, Long idOrganizacion, String tipoNormativa, String estado);
    List<ReglamentoDTO> findAllResidente(Long idPropiedad, String tipoNormativa);
    void lockPropiedadForUpdate(Long idPropiedad);
    Optional<Long> lockVigenteForUpdate(Long idPropiedad, String tipoNormativa);
    void reemplazar(Long idReglamento, Long idUsuario);
    void publicar(Long idReglamento, LocalDate fechaVigor, Long idUsuario);
    void inactivar(Long idReglamento, Long idUsuario);
    boolean existsDocumentoInPropiedad(Long idDocumento, Long idPropiedad, Long idOrganizacion);
    void markDocumentoPublicoResidentes(Long idDocumento);
}
