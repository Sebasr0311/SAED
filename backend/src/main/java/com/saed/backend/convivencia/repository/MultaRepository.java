package com.saed.backend.convivencia.repository;
import com.saed.backend.convivencia.dto.MultaDTO;
import java.util.List;

public interface MultaRepository {
    List<MultaDTO> findAll();
    MultaDTO findById(Long id);
    void updateEstado(Long id, String estado);
}
