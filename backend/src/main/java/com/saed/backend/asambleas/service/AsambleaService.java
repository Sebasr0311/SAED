package com.saed.backend.asambleas.service;

import com.saed.backend.asambleas.dto.*;

import java.util.List;

public interface AsambleaService {

    List<AsambleaDTO> listarAsambleas();

    AsambleaDTO obtenerDetalleAsamblea(Long idAsamblea);

    AsambleaDTO convocarAsamblea(AsambleaCreateRequestDTO request);

    AsambleaDTO actualizarEstado(Long idAsamblea, String nuevoEstado);

    QuorumLiveDTO obtenerQuorumEnVivo(Long idAsamblea);

    List<AsistenciaDTO> listarAsistencias(Long idAsamblea);

    AsistenciaDTO registrarAsistencia(Long idAsamblea, AsistenciaRequestDTO request);

    void retirarAsistencia(Long idAsamblea, Long idUnidad);

    List<PoderDTO> listarPoderes(Long idAsamblea);

    PoderDTO radicarPoder(Long idAsamblea, PoderRequestDTO request);

    PoderDTO decidirPoder(Long idPoder, String nuevoEstado);

    List<VotacionDTO> listarVotaciones(Long idAsamblea);

    VotacionDTO crearPuntoVotacion(Long idAsamblea, VotacionCreateRequestDTO request);

    VotacionDTO cerrarVotacion(Long idVotacion);

    void emitirVoto(Long idVotacion, VotoRequestDTO request);
}
