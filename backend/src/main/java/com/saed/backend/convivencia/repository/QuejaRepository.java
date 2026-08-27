package com.saed.backend.convivencia.repository;
import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import java.util.List;

public interface QuejaRepository {
    List<QuejaDTO> findAll();
    List<QuejaDTO> findByUserId(Long idUsuario);
    void create(QuejaRequestDTO dto, Long idUsuario, Long idPropiedad);
    void updateRespuesta(Long id, String respuesta);
    void updateEstado(Long id, String estado);
    void updatePrioridad(Long id, String prioridad);
}
