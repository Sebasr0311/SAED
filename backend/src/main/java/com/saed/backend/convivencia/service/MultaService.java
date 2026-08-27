package com.saed.backend.convivencia.service;
import com.saed.backend.convivencia.dto.MultaDTO;
import java.util.List;

public interface MultaService {
    List<MultaDTO> findAll();
    MultaDTO findById(Long id);
    void pagar(Long id, String metodo);
    void anular(Long id);
}
