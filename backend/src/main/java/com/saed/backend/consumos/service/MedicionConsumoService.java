package com.saed.backend.consumos.service;

import com.saed.backend.consumos.dto.ConsumoTendenciaDTO;
import com.saed.backend.consumos.dto.ConsumosSummaryDTO;
import com.saed.backend.consumos.dto.MedicionConsumoDTO;
import com.saed.backend.consumos.dto.MedicionConsumoRequestDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MedicionConsumoService {
    List<MedicionConsumoDTO> getAll(String periodo, String tipoServicio, Long idUnidad);
    MedicionConsumoDTO getById(Long id);
    Optional<BigDecimal> getUltimaLectura(Long idUnidad, String numeroMedidor, String tipoServicio);
    Long create(MedicionConsumoRequestDTO dto);
    void update(Long id, MedicionConsumoRequestDTO dto);
    void delete(Long id);
    ConsumosSummaryDTO getSummary(String periodo);
    List<ConsumoTendenciaDTO> getTendencias(String tipoServicio, int ultimosMeses);
}
