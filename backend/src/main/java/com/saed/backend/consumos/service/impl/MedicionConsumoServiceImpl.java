package com.saed.backend.consumos.service.impl;

import com.saed.backend.consumos.dto.ConsumoTendenciaDTO;
import com.saed.backend.consumos.dto.ConsumosSummaryDTO;
import com.saed.backend.consumos.dto.MedicionConsumoDTO;
import com.saed.backend.consumos.dto.MedicionConsumoRequestDTO;
import com.saed.backend.consumos.repository.MedicionConsumoRepository;
import com.saed.backend.consumos.service.MedicionConsumoService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Consumption readings (F11-03).
 *
 * <p>Reading policy: readings are never negative. A current reading lower than the previous one is rejected
 * unless the request declares a meter reset ({@code reinicioMedidor}); a reset is stored with a 0 baseline
 * (consumption = new reading) and the previous reading is kept in {@code LECTURA_PREVIA_REINICIO}. A reset
 * flag without a rollback is rejected as inconsistent.
 *
 * <p>Anomaly policy: a reading is anomalous when its consumption exceeds 150 % of the meter's historical
 * average (the edited reading itself excluded). It is recomputed on every create and edit; a manual
 * anomaly is kept only when it carries its own observation.
 *
 * <p>Scope: the property comes from the server context; unit-level roles only see their own unit.
 */
@Service
@Transactional
public class MedicionConsumoServiceImpl implements MedicionConsumoService {

    static final String AUTO_PREFIX = "Alerta automática:";
    private static final BigDecimal THRESHOLD = new BigDecimal("1.50");
    private static final Set<String> UNIT_ROLES = Set.of("RESIDENTE", "PROPIETARIO", "RESIDENTE_CONVIVENCIA");

    private final MedicionConsumoRepository repository;

    public MedicionConsumoServiceImpl(MedicionConsumoRepository repository) {
        this.repository = repository;
    }

    private SaedContext context() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getPropertyId() == null) {
            throw new AccessDeniedException("No hay una copropiedad activa en el contexto de seguridad.");
        }
        return ctx;
    }

    private Long getCurrentPropertyId() {
        return context().getPropertyId();
    }

    /** Unit the caller is bound to, or null for property administrators. */
    private Long boundUnit() {
        SaedContext ctx = context();
        if (ctx.getRoleCode() != null && UNIT_ROLES.contains(ctx.getRoleCode())) {
            if (ctx.getUnitId() == null) {
                throw new AccessDeniedException("No hay una unidad activa en el contexto de seguridad.");
            }
            return ctx.getUnitId();
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MedicionConsumoDTO> getAll(String periodo, String tipoServicio, Long idUnidad, boolean soloAreasComunes) {
        Long propId = getCurrentPropertyId();
        Long unit = boundUnit();
        if (unit != null) {
            return repository.findAllByPropiedad(propId, periodo, tipoServicio, unit, false);
        }
        return repository.findAllByPropiedad(propId, periodo, tipoServicio, soloAreasComunes ? null : idUnidad,
                soloAreasComunes);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicionConsumoDTO getById(Long id) {
        Long unit = boundUnit();
        return repository.findByIdAndPropiedad(id, getCurrentPropertyId())
                .filter(m -> unit == null || unit.equals(m.getIdUnidad()))
                .orElseThrow(() -> new NoSuchElementException("Medición de consumo no encontrada."));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MedicionConsumoDTO> getUltimaLectura(Long idUnidad, String numeroMedidor, String tipoServicio,
                                                         String antesDePeriodo) {
        return repository.findUltimaLectura(getCurrentPropertyId(), idUnidad, numeroMedidor, tipoServicio, antesDePeriodo);
    }

    @Override
    public Long create(MedicionConsumoRequestDTO dto) {
        Long propId = getCurrentPropertyId();
        Long userId = context().getUserId();
        BigDecimal consumo = normalizeReadings(dto);
        BigDecimal costoTotal = cost(consumo, dto);
        BigDecimal promedio = repository.calcularPromedioHistorico(propId, dto.getIdUnidad(), dto.getNumeroMedidor(),
                dto.getTipoServicio(), null);
        Anomaly anomaly = evaluate(consumo, promedio, dto);
        return repository.insert(propId, userId, dto, costoTotal, anomaly.flag(), anomaly.observation());
    }

    @Override
    public void update(Long id, MedicionConsumoRequestDTO dto) {
        Long propId = getCurrentPropertyId();
        repository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new NoSuchElementException("Medición de consumo no encontrada."));
        BigDecimal consumo = normalizeReadings(dto);
        BigDecimal costoTotal = cost(consumo, dto);
        BigDecimal promedio = repository.calcularPromedioHistorico(propId, dto.getIdUnidad(), dto.getNumeroMedidor(),
                dto.getTipoServicio(), id);
        Anomaly anomaly = evaluate(consumo, promedio, dto);
        repository.update(id, propId, dto, costoTotal, anomaly.flag(), anomaly.observation());
    }

    @Override
    public void delete(Long id) {
        Long propId = getCurrentPropertyId();
        repository.findByIdAndPropiedad(id, propId)
                .orElseThrow(() -> new NoSuchElementException("Medición de consumo no encontrada."));
        repository.delete(id, propId);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumosSummaryDTO getSummary(String periodo) {
        return repository.getSummary(getCurrentPropertyId(), periodo, boundUnit());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsumoTendenciaDTO> getTendencias(String tipoServicio, int ultimosMeses) {
        int meses = Math.max(1, Math.min(60, ultimosMeses));
        return repository.getTendencias(getCurrentPropertyId(), tipoServicio, meses, boundUnit());
    }

    /** Applies the negative/reset policy and returns the consumption of the reading. */
    static BigDecimal normalizeReadings(MedicionConsumoRequestDTO dto) {
        BigDecimal anterior = dto.getLecturaAnterior();
        BigDecimal actual = dto.getLecturaActual();
        if (anterior == null || actual == null) {
            throw new IllegalArgumentException("Las lecturas anterior y actual son obligatorias.");
        }
        if (anterior.signum() < 0 || actual.signum() < 0) {
            throw new IllegalArgumentException("Las lecturas no pueden ser negativas.");
        }
        boolean reset = Boolean.TRUE.equals(dto.getReinicioMedidor());
        if (actual.compareTo(anterior) < 0) {
            if (!reset) {
                throw new IllegalArgumentException("La lectura actual (" + actual + ") es menor a la anterior ("
                        + anterior + "). Si el medidor se reinició o reemplazó, marque el reinicio de medidor.");
            }
            dto.setLecturaPreviaReinicio(anterior);
            dto.setLecturaAnterior(BigDecimal.ZERO);
            return actual;
        }
        if (reset) {
            throw new IllegalArgumentException("Un reinicio de medidor requiere una lectura actual menor a la anterior.");
        }
        dto.setLecturaPreviaReinicio(null);
        return actual.subtract(anterior);
    }

    private static BigDecimal cost(BigDecimal consumo, MedicionConsumoRequestDTO dto) {
        BigDecimal tarifa = dto.getTarifaUnitaria() != null ? dto.getTarifaUnitaria() : BigDecimal.ZERO;
        if (tarifa.signum() < 0) {
            throw new IllegalArgumentException("La tarifa unitaria no puede ser negativa.");
        }
        return consumo.multiply(tarifa).setScale(2, RoundingMode.HALF_UP);
    }

    record Anomaly(String flag, String observation) {
    }

    static Anomaly evaluate(BigDecimal consumo, BigDecimal promedio, MedicionConsumoRequestDTO dto) {
        String observacion = dto.getObservacionAnomalia();
        boolean autoObservation = observacion != null && observacion.startsWith(AUTO_PREFIX);
        boolean manual = "S".equalsIgnoreCase(dto.getAnomaliaDetectada())
                && observacion != null && !observacion.isBlank() && !autoObservation;
        if (promedio != null && promedio.signum() > 0 && consumo.compareTo(promedio.multiply(THRESHOLD)) > 0) {
            String obs = manual ? observacion : AUTO_PREFIX + " Consumo (" + consumo.stripTrailingZeros().toPlainString()
                    + ") excede el promedio histórico (" + promedio.setScale(1, RoundingMode.HALF_UP)
                    + ") en más del 50%";
            return new Anomaly("S", obs);
        }
        if (manual) {
            return new Anomaly("S", observacion);
        }
        return new Anomaly("N", autoObservation ? null : blankToNull(observacion));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
