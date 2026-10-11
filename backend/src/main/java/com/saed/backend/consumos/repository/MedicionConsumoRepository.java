package com.saed.backend.consumos.repository;

import com.saed.backend.consumos.dto.ConsumoTendenciaDTO;
import com.saed.backend.consumos.dto.ConsumosSummaryDTO;
import com.saed.backend.consumos.dto.MedicionConsumoDTO;
import com.saed.backend.consumos.dto.MedicionConsumoRequestDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MedicionConsumoRepository {

    /**
     * Readings of a property. {@code idUnidad} restricts to one unit; {@code soloAreasComunes} restricts to
     * common-area meters (readings without unit). Both absent means every reading of the property.
     */
    List<MedicionConsumoDTO> findAllByPropiedad(Long idPropiedad, String periodo, String tipoServicio, Long idUnidad,
                                                boolean soloAreasComunes);

    Optional<MedicionConsumoDTO> findByIdAndPropiedad(Long idMedicion, Long idPropiedad);

    /**
     * Latest reading of a meter (a null {@code idUnidad} means a common-area meter), optionally only from
     * periods strictly before {@code antesDePeriodo} (YYYY-MM).
     */
    Optional<MedicionConsumoDTO> findUltimaLectura(Long idPropiedad, Long idUnidad, String numeroMedidor,
                                                   String tipoServicio, String antesDePeriodo);

    /** Average consumption of a meter, excluding the reading {@code excluirIdMedicion} when editing it. */
    BigDecimal calcularPromedioHistorico(Long idPropiedad, Long idUnidad, String numeroMedidor, String tipoServicio,
                                         Long excluirIdMedicion);

    Long insert(Long idPropiedad, Long userId, MedicionConsumoRequestDTO dto, BigDecimal costoTotal, String anomalia, String observacion);

    void update(Long idMedicion, Long idPropiedad, MedicionConsumoRequestDTO dto, BigDecimal costoTotal, String anomalia, String observacion);

    void delete(Long idMedicion, Long idPropiedad);

    /** Summary for the property, or for one unit when {@code idUnidad} is not null. */
    ConsumosSummaryDTO getSummary(Long idPropiedad, String periodo, Long idUnidad);

    /** Trend of the last {@code ultimosMeses} periods, for the property or for one unit. */
    List<ConsumoTendenciaDTO> getTendencias(Long idPropiedad, String tipoServicio, int ultimosMeses, Long idUnidad);
}
