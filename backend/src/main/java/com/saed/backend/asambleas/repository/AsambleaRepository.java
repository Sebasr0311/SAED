package com.saed.backend.asambleas.repository;

import com.saed.backend.asambleas.dto.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AsambleaRepository {

    List<AsambleaDTO> findAllByPropiedad(Long idPropiedad);

    Optional<AsambleaDTO> findById(Long idAsamblea);

    Long createAsamblea(AsambleaCreateRequestDTO request, Long idPropiedad, Long idUsuario);

    void updateEstado(Long idAsamblea, String nuevoEstado);

    void updateQuorumAlcanzado(Long idAsamblea, BigDecimal quorumAlcanzadoPct);

    List<AsistenciaDTO> findAsistencias(Long idAsamblea);

    Long registrarAsistencia(Long idAsamblea, AsistenciaRequestDTO request, BigDecimal coeficiente);

    void retirarAsistencia(Long idAsamblea, Long idUnidad);

    BigDecimal calcularQuorumAlcanzadoPct(Long idAsamblea, Long idPropiedad);

    List<PoderDTO> findPoderes(Long idAsamblea);

    Long registrarPoder(Long idAsamblea, PoderRequestDTO request);

    void decidirPoder(Long idPoder, String nuevoEstado, Long idUsuarioValidador);

    List<VotacionDTO> findVotaciones(Long idAsamblea);

    Optional<VotacionDTO> findVotacionById(Long idVotacion);

    Long createVotacion(Long idAsamblea, VotacionCreateRequestDTO request);

    void cerrarVotacion(Long idVotacion);

    Long registrarVoto(Long idVotacion, VotoRequestDTO request, BigDecimal coeficiente);

    BigDecimal findCoeficienteUnidad(Long idUnidad);

    boolean existeAsistenciaUnidad(Long idAsamblea, Long idUnidad);

    boolean existeVotoUnidad(Long idVotacion, Long idUnidad);

    boolean existePoderUnidad(Long idAsamblea, Long idUnidad);

    int countUnidadesActivas(Long idPropiedad);
}
