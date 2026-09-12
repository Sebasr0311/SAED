package com.saed.backend.consumos.repository;

import com.saed.backend.consumos.dto.ConsumoTendenciaDTO;
import com.saed.backend.consumos.dto.ConsumosSummaryDTO;
import com.saed.backend.consumos.dto.MedicionConsumoDTO;
import com.saed.backend.consumos.dto.MedicionConsumoRequestDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MedicionConsumoRepository {
    List<MedicionConsumoDTO> findAllByPropiedad(Long idPropiedad, String periodo, String tipoServicio, Long idUnidad);
    Optional<MedicionConsumoDTO> findByIdAndPropiedad(Long idMedicion, Long idPropiedad);
    Optional<BigDecimal> findUltimaLectura(Long idPropiedad, Long idUnidad, String numeroMedidor, String tipoServicio);
    BigDecimal calcularPromedioHistorico(Long idPropiedad, Long idUnidad, String numeroMedidor, String tipoServicio);
    Long insert(Long idPropiedad, Long userId, MedicionConsumoRequestDTO dto, BigDecimal costoTotal, String anomalia, String observacion);
    void update(Long idMedicion, Long idPropiedad, MedicionConsumoRequestDTO dto, BigDecimal costoTotal, String anomalia, String observacion);
    void delete(Long idMedicion, Long idPropiedad);
    ConsumosSummaryDTO getSummary(Long idPropiedad, String periodo);
    List<ConsumoTendenciaDTO> getTendencias(Long idPropiedad, String tipoServicio, int ultimosMeses);
}
