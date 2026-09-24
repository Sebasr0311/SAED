package com.saed.backend.trabajadores.repository;

import com.saed.backend.trabajadores.dto.ObraTrabajadorDTO;

import java.util.List;
import java.util.Optional;

public interface ObraTrabajadorRepository {

    void asignarOActualizar(Long idObra, Long idTrabajador, String autorizado, Long autorizadoPor);

    void autorizar(Long idObra, Long idTrabajador, Long autorizadoPor);

    void revocar(Long idObra, Long idTrabajador);

    void desasignar(Long idObra, Long idTrabajador);

    List<ObraTrabajadorDTO> listarPorObra(Long idObra);

    List<ObraTrabajadorDTO> listarAutorizadosPorObra(Long idObra);

    Optional<ObraTrabajadorDTO> buscarPorObraYTrabajador(Long idObra, Long idTrabajador);

    long contarTotalAsignados(Long idObra);

    long contarAutorizados(Long idObra);
}
