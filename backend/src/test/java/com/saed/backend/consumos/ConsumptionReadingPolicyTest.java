package com.saed.backend.consumos;

import com.saed.backend.consumos.controller.ConsumosController;
import com.saed.backend.consumos.dto.MedicionConsumoDTO;
import com.saed.backend.consumos.dto.MedicionConsumoRequestDTO;
import com.saed.backend.consumos.repository.MedicionConsumoRepository;
import com.saed.backend.consumos.service.MedicionConsumoService;
import com.saed.backend.consumos.service.impl.MedicionConsumoServiceImpl;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F11-03: reading policy (negative delta, meter reset), anomaly recompute on edit, scoping and contracts. */
class ConsumptionReadingPolicyTest {

    private static final long PROP = 10L;
    private static final long UNIT = 701L;
    private static final long READING = 55L;

    private MedicionConsumoRepository repo;
    private MedicionConsumoServiceImpl service;

    @BeforeEach
    void setUp() {
        repo = mock(MedicionConsumoRepository.class);
        service = new MedicionConsumoServiceImpl(repo);
        admin();
    }

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    private static void admin() {
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(1L).propertyId(PROP)
            .roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD").build());
    }

    private static void resident() {
        SaedContextHolder.setContext(SaedContext.builder().userId(40L).organizationId(1L).propertyId(PROP)
            .unitId(UNIT).roleCode("RESIDENTE").roleScope("UNIDAD").build());
    }

    private static MedicionConsumoRequestDTO reading(String anterior, String actual) {
        MedicionConsumoRequestDTO dto = new MedicionConsumoRequestDTO();
        dto.setIdUnidad(UNIT);
        dto.setTipoServicio("AGUA");
        dto.setNumeroMedidor("M-1");
        dto.setPeriodo("2026-05");
        dto.setLecturaAnterior(new BigDecimal(anterior));
        dto.setLecturaActual(new BigDecimal(actual));
        dto.setTarifaUnitaria(new BigDecimal("2.00"));
        return dto;
    }

    private static MedicionConsumoDTO stored(String anomalia, String observacion) {
        MedicionConsumoDTO dto = new MedicionConsumoDTO();
        dto.setIdMedicion(READING);
        dto.setIdPropiedad(PROP);
        dto.setIdUnidad(UNIT);
        dto.setAnomaliaDetectada(anomalia);
        dto.setObservacionAnomalia(observacion);
        return dto;
    }

    // ---- negative delta and meter reset ---------------------------------------------------

    @Test
    void negativeDeltaWithoutMeterResetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create(reading("120", "100")));
        verify(repo, never()).insert(anyLong(), any(), any(), any(), anyString(), any());
    }

    @Test
    void negativeReadingsAreRejectedEvenWithMeterReset() {
        MedicionConsumoRequestDTO dto = reading("10", "-1");
        dto.setReinicioMedidor(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(dto));
        assertThrows(IllegalArgumentException.class, () -> service.create(reading("-5", "10")));
        verify(repo, never()).insert(anyLong(), any(), any(), any(), anyString(), any());
    }

    @Test
    void meterResetStoresAZeroBaselineAndKeepsThePreviousReading() {
        MedicionConsumoRequestDTO dto = reading("9990", "15");
        dto.setReinicioMedidor(true);
        when(repo.insert(anyLong(), any(), any(), any(), anyString(), any())).thenReturn(1L);

        service.create(dto);

        ArgumentCaptor<MedicionConsumoRequestDTO> saved = ArgumentCaptor.forClass(MedicionConsumoRequestDTO.class);
        ArgumentCaptor<BigDecimal> cost = ArgumentCaptor.forClass(BigDecimal.class);
        verify(repo).insert(eq(PROP), eq(20L), saved.capture(), cost.capture(), anyString(), any());
        assertEquals(0, saved.getValue().getLecturaAnterior().compareTo(BigDecimal.ZERO));
        assertEquals(0, saved.getValue().getLecturaPreviaReinicio().compareTo(new BigDecimal("9990")));
        assertTrue(saved.getValue().getReinicioMedidor());
        assertEquals(new BigDecimal("30.00"), cost.getValue(), "consumption since reset is the new reading");
    }

    @Test
    void meterResetFlagWithoutRollbackIsRejected() {
        MedicionConsumoRequestDTO dto = reading("100", "120");
        dto.setReinicioMedidor(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(dto));
    }

    // ---- anomaly --------------------------------------------------------------------------

    @Test
    void creationFlagsConsumptionAboveOneAndAHalfTimesTheHistoricalAverage() {
        when(repo.calcularPromedioHistorico(PROP, UNIT, "M-1", "AGUA", null)).thenReturn(new BigDecimal("10"));
        service.create(reading("100", "116"));
        ArgumentCaptor<String> obs = ArgumentCaptor.forClass(String.class);
        verify(repo).insert(eq(PROP), eq(20L), any(), any(), eq("S"), obs.capture());
        assertTrue(obs.getValue().startsWith("Alerta automática"));
    }

    @Test
    void editingANormalValueClearsTheAutomaticAnomalyComputedWithoutTheEditedReading() {
        when(repo.findByIdAndPropiedad(READING, PROP))
            .thenReturn(Optional.of(stored("S", "Alerta automática: Consumo (40) excede el promedio")));
        when(repo.calcularPromedioHistorico(PROP, UNIT, "M-1", "AGUA", READING)).thenReturn(new BigDecimal("10"));

        MedicionConsumoRequestDTO dto = reading("100", "110");
        dto.setAnomaliaDetectada("S");
        dto.setObservacionAnomalia("Alerta automática: Consumo (40) excede el promedio");
        service.update(READING, dto);

        ArgumentCaptor<String> obs = ArgumentCaptor.forClass(String.class);
        verify(repo).update(eq(READING), eq(PROP), any(), any(), eq("N"), obs.capture());
        assertNull(obs.getValue());
    }

    @Test
    void editingToAnAbnormalValueRaisesTheAnomaly() {
        when(repo.findByIdAndPropiedad(READING, PROP)).thenReturn(Optional.of(stored("N", null)));
        when(repo.calcularPromedioHistorico(PROP, UNIT, "M-1", "AGUA", READING)).thenReturn(new BigDecimal("10"));
        service.update(READING, reading("100", "130"));
        verify(repo).update(eq(READING), eq(PROP), any(), any(), eq("S"), any());
    }

    @Test
    void aManualAnomalyWithItsOwnObservationIsKept() {
        when(repo.calcularPromedioHistorico(any(), any(), any(), any(), any())).thenReturn(new BigDecimal("10"));
        MedicionConsumoRequestDTO dto = reading("100", "105");
        dto.setAnomaliaDetectada("S");
        dto.setObservacionAnomalia("Medidor con sello roto");
        service.create(dto);
        verify(repo).insert(eq(PROP), eq(20L), any(), any(), eq("S"), eq("Medidor con sello roto"));
    }

    // ---- scope and not-found --------------------------------------------------------------

    @Test
    void residentIsBoundToTheirOwnUnit() {
        resident();
        service.getAll(null, null, 999L, false);
        verify(repo).findAllByPropiedad(PROP, null, null, UNIT, false);
        service.getSummary("2026-05");
        verify(repo).getSummary(PROP, "2026-05", UNIT);
        when(repo.findByIdAndPropiedad(READING, PROP)).thenReturn(Optional.of(stored("N", null)));
        MedicionConsumoDTO otherUnit = stored("N", null);
        otherUnit.setIdUnidad(802L);
        when(repo.findByIdAndPropiedad(READING, PROP)).thenReturn(Optional.of(otherUnit));
        assertThrows(NoSuchElementException.class, () -> service.getById(READING));
    }

    @Test
    void residentCannotListCommonAreaReadingsOrUseAnotherUnit() {
        resident();
        service.getAll(null, "AGUA", null, true);
        verify(repo).findAllByPropiedad(PROP, null, "AGUA", UNIT, false);
    }

    @Test
    void organizationAdminWithoutPropertyIsDeniedNotConflicted() {
        SaedContextHolder.setContext(SaedContext.builder().userId(20L).organizationId(1L)
            .roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").build());
        assertThrows(AccessDeniedException.class, () -> service.getAll(null, null, null, false));
    }

    @Test
    void missingReadingIsNotFound() {
        when(repo.findByIdAndPropiedad(READING, PROP)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.getById(READING));
        assertThrows(NoSuchElementException.class, () -> service.update(READING, reading("1", "2")));
        assertThrows(NoSuchElementException.class, () -> service.delete(READING));
    }

    // ---- last-reading contract ------------------------------------------------------------

    @Test
    void lastReadingContractReportsAbsenceExplicitlyAndReturnsTheReadingFields() {
        MedicionConsumoService svc = mock(MedicionConsumoService.class);
        ConsumosController controller = new ConsumosController(svc);
        when(svc.getUltimaLectura(isNull(), eq("M-9"), eq("AGUA"), eq("2026-05"))).thenReturn(Optional.empty());
        Map<String, Object> none = controller.getUltimaLectura(null, "M-9", "AGUA", "2026-05").getBody();
        assertEquals(Boolean.FALSE, none.get("encontrada"));
        assertFalse(none.containsKey("lecturaActual"), "no fabricated zero reading");

        MedicionConsumoDTO last = stored("N", null);
        last.setLecturaActual(new BigDecimal("321.50"));
        last.setPeriodo("2026-04");
        last.setNumeroMedidor("M-9");
        last.setUnidadMedida("M3");
        when(svc.getUltimaLectura(eq(UNIT), eq("M-9"), eq("AGUA"), eq("2026-05"))).thenReturn(Optional.of(last));
        Map<String, Object> found = controller.getUltimaLectura(UNIT, "M-9", "AGUA", "2026-05").getBody();
        assertEquals(Boolean.TRUE, found.get("encontrada"));
        assertEquals(new BigDecimal("321.50"), found.get("lecturaActual"));
        assertEquals("2026-04", found.get("periodo"));
    }

    @Test
    void trendWindowIsClamped() {
        service.getTendencias("AGUA", 500);
        verify(repo).getTendencias(PROP, "AGUA", 60, null);
        service.getTendencias(null, 0);
        verify(repo).getTendencias(PROP, null, 1, null);
    }
}
