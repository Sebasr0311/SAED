package com.saed.backend.consumos.service.impl;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.consumos.dto.ConsumoTendenciaDTO;
import com.saed.backend.consumos.dto.ConsumosSummaryDTO;
import com.saed.backend.consumos.dto.MedicionConsumoDTO;
import com.saed.backend.consumos.dto.MedicionConsumoRequestDTO;
import com.saed.backend.consumos.repository.MedicionConsumoRepository;
import com.saed.backend.consumos.service.MedicionConsumoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class MedicionConsumoServiceImpl implements MedicionConsumoService {

    private final MedicionConsumoRepository repository;

    public MedicionConsumoServiceImpl(MedicionConsumoRepository repository) {
        this.repository = repository;
    }

    private Long getCurrentPropertyId() {
        Long propId = SaedContextHolder.getContext().getPropertyId();
        if (propId == null) {
            throw new IllegalStateException("No hay una copropiedad activa en el contexto de seguridad.");
        }
        return propId;
    }

    private Long getCurrentUserId() {
        return SaedContextHolder.getContext().getUserId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MedicionConsumoDTO> getAll(String periodo, String tipoServicio, Long idUnidad) {
        return repository.findAllByPropiedad(getCurrentPropertyId(), periodo, tipoServicio, idUnidad);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicionConsumoDTO getById(Long id) {
        return repository.findByIdAndPropiedad(id, getCurrentPropertyId())
                .orElseThrow(() -> new IllegalArgumentException("Medición de consumo no encontrada con ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getUltimaLectura(Long idUnidad, String numeroMedidor, String tipoServicio) {
        return repository.findUltimaLectura(getCurrentPropertyId(), idUnidad, numeroMedidor, tipoServicio);
    }

    @Override
    public Long create(MedicionConsumoRequestDTO dto) {
        validarLecturas(dto);

        Long propId = getCurrentPropertyId();
        Long userId = getCurrentUserId();

        BigDecimal consumo = dto.getLecturaActual().subtract(dto.getLecturaAnterior());
        BigDecimal tarifa = dto.getTarifaUnitaria() != null ? dto.getTarifaUnitaria() : BigDecimal.ZERO;
        BigDecimal costoTotal = consumo.multiply(tarifa).setScale(2, RoundingMode.HALF_UP);

        String anomalia = dto.getAnomaliaDetectada();
        String observacion = dto.getObservacionAnomalia();

        if (anomalia == null || anomalia.isBlank() || "N".equalsIgnoreCase(anomalia)) {
            BigDecimal promedio = repository.calcularPromedioHistorico(propId, dto.getIdUnidad(), dto.getNumeroMedidor(), dto.getTipoServicio());
            if (promedio != null && promedio.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal umbral = promedio.multiply(new BigDecimal("1.50"));
                if (consumo.compareTo(umbral) > 0) {
                    anomalia = "S";
                    if (observacion == null || observacion.isBlank()) {
                        observacion = "Alerta automática: Consumo (" + consumo + ") excede el promedio histórico (" +
                                      promedio.setScale(1, RoundingMode.HALF_UP) + ") en más del 50%";
                    }
                } else {
                    anomalia = "N";
                }
            } else {
                anomalia = "N";
            }
        }

        return repository.insert(propId, userId, dto, costoTotal, anomalia, observacion);
    }

    @Override
    public void update(Long id, MedicionConsumoRequestDTO dto) {
        validarLecturas(dto);

        Long propId = getCurrentPropertyId();
        repository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new IllegalArgumentException("Medición de consumo no encontrada con ID: " + id));

        BigDecimal consumo = dto.getLecturaActual().subtract(dto.getLecturaAnterior());
        BigDecimal tarifa = dto.getTarifaUnitaria() != null ? dto.getTarifaUnitaria() : BigDecimal.ZERO;
        BigDecimal costoTotal = consumo.multiply(tarifa).setScale(2, RoundingMode.HALF_UP);

        String anomalia = (dto.getAnomaliaDetectada() != null && "S".equalsIgnoreCase(dto.getAnomaliaDetectada())) ? "S" : "N";
        String observacion = dto.getObservacionAnomalia();

        repository.update(id, propId, dto, costoTotal, anomalia, observacion);
    }

    @Override
    public void delete(Long id) {
        Long propId = getCurrentPropertyId();
        repository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new IllegalArgumentException("Medición de consumo no encontrada con ID: " + id));
        repository.delete(id, propId);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumosSummaryDTO getSummary(String periodo) {
        return repository.getSummary(getCurrentPropertyId(), periodo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsumoTendenciaDTO> getTendencias(String tipoServicio, int ultimosMeses) {
        return repository.getTendencias(getCurrentPropertyId(), tipoServicio, ultimosMeses);
    }

    private void validarLecturas(MedicionConsumoRequestDTO dto) {
        if (dto.getLecturaActual().compareTo(dto.getLecturaAnterior()) < 0) {
            throw new IllegalArgumentException("La lectura actual (" + dto.getLecturaActual() +
                    ") no puede ser menor a la lectura anterior (" + dto.getLecturaAnterior() + ")");
        }
    }
}
