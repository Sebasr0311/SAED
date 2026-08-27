package com.saed.backend.convivencia.service;
import com.saed.backend.convivencia.dto.QuejaDTO;
import com.saed.backend.convivencia.dto.QuejaRequestDTO;
import java.util.List;

public interface QuejaService {
    List<QuejaDTO> findAll();
    List<QuejaDTO> findMyQuejas();
    void createQueja(QuejaRequestDTO dto);
    void responder(Long id, String respuesta);
    void actualizarEstado(Long id, String estado);
    void actualizarPrioridad(Long id, String prioridad);
}
