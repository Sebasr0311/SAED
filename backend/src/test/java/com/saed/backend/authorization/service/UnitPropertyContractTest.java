package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyRequestDTO;
import com.saed.backend.authorization.dto.UnitCoefficientSummaryDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import com.saed.backend.authorization.repository.PropertyRepositoryImpl;
import com.saed.backend.authorization.repository.UnitRepository;
import com.saed.backend.authorization.repository.UnitRepositoryImpl;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.service.PlanLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F04-01: territory and unit fields round-trip, unit identifier uniqueness (null block included)
 * and the DEC-04 coefficient contract (0 &lt; c &le; 1, property sum &le; 1.0000, incomplete sum is a warning).
 */
@ExtendWith(MockitoExtension.class)
class UnitPropertyContractTest {

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private PlanLimitService planLimitService;

    @AfterEach
    void tearDown() {
        SaedContextHolder.clearContext();
    }

    private UnitService service() {
        return new UnitService(unitRepository, planLimitService, null, org.mockito.Mockito.mock(PropertyStatusService.class));
    }

    private static void propertyAdmin() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(3L).roleCode("ADMIN_PROPIEDAD").roleScope("PROPIEDAD")
                .organizationId(1L).propertyId(5L).build());
    }

    private static UnitRequestDTO unit(String identifier, Long block, String coefficient) {
        UnitRequestDTO r = new UnitRequestDTO();
        r.setIdPropiedad(5L);
        r.setIdTipoUnidad(1L);
        r.setIdentificador(identifier);
        r.setIdBloque(block);
        r.setCoeficienteCopropiedad(coefficient == null ? null : new BigDecimal(coefficient));
        return r;
    }

    // ---- coefficients (DEC-04) ----

    @Test
    void create_rejectsZeroOrNegativeCoefficient() {
        propertyAdmin();
        assertThrows(IllegalArgumentException.class, () -> service().create(unit("101", null, "0")));
        assertThrows(IllegalArgumentException.class, () -> service().create(unit("101", null, "-0.01")));
        verify(unitRepository, never()).create(any());
    }

    @Test
    void create_rejectsCoefficientAboveOne() {
        propertyAdmin();
        assertThrows(IllegalArgumentException.class, () -> service().create(unit("101", null, "1.5")));
        verify(unitRepository, never()).create(any());
    }

    @Test
    void create_rejectsWhenPropertySumWouldExceedOne() {
        propertyAdmin();
        when(planLimitService.getOrganizationIdForProperty(5L)).thenReturn(1L);
        when(unitRepository.lockPropertyAndSumCoefficients(5L, null)).thenReturn(new BigDecimal("0.9500"));

        assertThrows(IllegalStateException.class, () -> service().create(unit("101", null, "0.06")));
        verify(unitRepository, never()).create(any());
    }

    @Test
    void create_acceptsSumThatRoundsToOneAtFourDecimals() {
        propertyAdmin();
        when(planLimitService.getOrganizationIdForProperty(5L)).thenReturn(1L);
        when(unitRepository.lockPropertyAndSumCoefficients(5L, null)).thenReturn(new BigDecimal("0.950000"));
        when(unitRepository.create(any())).thenReturn(7L);

        assertEquals(7L, service().create(unit("101", null, "0.050040")));
    }

    @Test
    void update_excludesOwnCoefficientFromSum() {
        propertyAdmin();
        UnitDTO existing = new UnitDTO();
        existing.setId(9L);
        existing.setIdPropiedad(5L);
        when(unitRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(unitRepository.lockPropertyAndSumCoefficients(5L, 9L)).thenReturn(new BigDecimal("0.6"));

        service().update(9L, unit("101", null, "0.4"));
        verify(unitRepository).update(eq(9L), any());

        when(unitRepository.lockPropertyAndSumCoefficients(5L, 9L)).thenReturn(new BigDecimal("0.7"));
        assertThrows(IllegalStateException.class, () -> service().update(9L, unit("101", null, "0.4")));
    }

    @Test
    void coefficientSummary_reportsIncompleteSumAsWarning() {
        propertyAdmin();
        UnitCoefficientSummaryDTO raw = new UnitCoefficientSummaryDTO();
        raw.setIdPropiedad(5L);
        raw.setTotalUnidades(3);
        raw.setUnidadesSinCoeficiente(1);
        raw.setSumaCoeficientes(new BigDecimal("0.700000"));
        when(unitRepository.coefficientSummary(5L)).thenReturn(raw);

        UnitCoefficientSummaryDTO summary = service().coefficientSummary(5L);

        assertFalse(summary.isCompleto());
        assertNotNull(summary.getAdvertencia());
        assertEquals(new BigDecimal("0.3000"), summary.getFaltante());
    }

    @Test
    void coefficientSummary_completeSumHasNoWarning() {
        propertyAdmin();
        UnitCoefficientSummaryDTO raw = new UnitCoefficientSummaryDTO();
        raw.setIdPropiedad(5L);
        raw.setTotalUnidades(2);
        raw.setSumaCoeficientes(new BigDecimal("1.000000"));
        when(unitRepository.coefficientSummary(5L)).thenReturn(raw);

        UnitCoefficientSummaryDTO summary = service().coefficientSummary(5L);

        assertTrue(summary.isCompleto());
        assertEquals(null, summary.getAdvertencia());
    }

    @Test
    void coefficientSummary_propertyAdminCannotReadAnotherProperty() {
        propertyAdmin();
        assertThrows(AccessDeniedException.class, () -> service().coefficientSummary(6L));
        verify(unitRepository, never()).coefficientSummary(any());
    }

    @Test
    void coefficientSummary_orgAdminCannotReadPropertyOfAnotherOrganization() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(2L).roleCode("ADMIN_ORGANIZACION").roleScope("ORGANIZACION").organizationId(1L).build());
        when(planLimitService.getOrganizationIdForProperty(8L)).thenReturn(2L);
        assertThrows(AccessDeniedException.class, () -> service().coefficientSummary(8L));
    }

    // ---- uniqueness (null block included) ----

    @Test
    void create_rejectsDuplicateIdentifierWithNullBlock() {
        propertyAdmin();
        when(planLimitService.getOrganizationIdForProperty(5L)).thenReturn(1L);
        when(unitRepository.existsIdentifier(5L, null, "101", null)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service().create(unit("101", null, null)));
        verify(unitRepository, never()).create(any());
    }

    @Test
    void update_rejectsDuplicateIdentifierInSameBlock() {
        propertyAdmin();
        UnitDTO existing = new UnitDTO();
        existing.setId(9L);
        existing.setIdPropiedad(5L);
        when(unitRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(unitRepository.existsIdentifier(5L, 3L, "202", 9L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service().update(9L, unit("202", 3L, null)));
        verify(unitRepository, never()).update(any(), any());
    }

    @Test
    void repository_identifierCheckTreatsNullBlockAsItsOwnGroupCaseInsensitively() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(jdbc.queryForObject(sql.capture(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(1);

        assertTrue(new UnitRepositoryImpl(jdbc).existsIdentifier(5L, null, "a-1", null));
        String q = sql.getValue().toUpperCase();
        assertTrue(q.contains("NVL(ID_BLOQUE, -1) = NVL(:IDBLOQUE, -1)"), q);
        assertTrue(q.contains("UPPER(IDENTIFICADOR) = UPPER(:IDENTIFICADOR)"), q);
    }

    // ---- territory and floor round-trip ----

    @Test
    void unitRepository_persistsAndReadsFloor() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        doAnswer(inv -> {
            KeyHolder kh = inv.getArgument(2);
            kh.getKeyList().add(java.util.Map.of("ID_UNIDAD", 11L));
            return 1;
        }).when(jdbc).update(sql.capture(), params.capture(), any(KeyHolder.class), any(String[].class));

        UnitRequestDTO req = unit("101", null, "0.1");
        req.setPiso("12");
        new UnitRepositoryImpl(jdbc).create(req);
        assertTrue(sql.getValue().toLowerCase().contains("piso"));
        assertEquals("12", params.getValue().getValue("piso"));

        ArgumentCaptor<RowMapper<UnitDTO>> mapper = rowMapperCaptor();
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper.capture())).thenReturn(List.of());
        new UnitRepositoryImpl(jdbc).findById(11L);
        ResultSet rs = mock(ResultSet.class, org.mockito.Mockito.withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
        when(rs.getString("piso")).thenReturn("12");
        assertEquals("12", mapper.getValue().mapRow(rs, 0).getPiso());
    }

    @Test
    void unitRepository_updatePersistsFloor() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(jdbc.update(sql.capture(), params.capture())).thenReturn(1);

        UnitRequestDTO req = unit("101", null, null);
        req.setPiso("PH");
        new UnitRepositoryImpl(jdbc).update(11L, req);
        assertTrue(sql.getValue().toLowerCase().contains("piso = :piso"));
        assertEquals("PH", params.getValue().getValue("piso"));
    }

    @Test
    void propertyRepository_persistsCountryStateAndCity() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        doAnswer(inv -> {
            KeyHolder kh = inv.getArgument(2);
            kh.getKeyList().add(java.util.Map.of("ID_PROPIEDAD", 4L));
            return 1;
        }).when(jdbc).update(sql.capture(), params.capture(), any(KeyHolder.class), any(String[].class));

        PropertyRequestDTO req = new PropertyRequestDTO();
        req.setIdOrganizacion(1L);
        req.setIdTipoPropiedad(1L);
        req.setNombre("Torres");
        req.setDireccion("Calle 1");
        req.setPais("Colombia");
        req.setDepartamento("Cesar");
        req.setCiudad("Valledupar");
        new PropertyRepositoryImpl(jdbc).create(req);

        String q = sql.getValue().toLowerCase();
        assertTrue(q.contains("pais") && q.contains("departamento"), q);
        assertEquals("Colombia", params.getValue().getValue("pais"));
        assertEquals("Cesar", params.getValue().getValue("departamento"));
        assertEquals("Valledupar", params.getValue().getValue("ciudad"));
    }

    @Test
    void propertyRepository_updateAndReadRoundTripTerritory() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(jdbc.update(sql.capture(), params.capture())).thenReturn(1);

        PropertyRequestDTO req = new PropertyRequestDTO();
        req.setPais("Colombia");
        req.setDepartamento("Antioquia");
        req.setCiudad("Medellín");
        new PropertyRepositoryImpl(jdbc).update(4L, req);
        assertTrue(sql.getValue().toLowerCase().contains("departamento = :departamento"));
        assertEquals("Antioquia", params.getValue().getValue("departamento"));
        assertEquals("Colombia", params.getValue().getValue("pais"));

        ArgumentCaptor<RowMapper<PropertyDTO>> mapper = rowMapperCaptor();
        when(jdbc.query(anyString(), any(SqlParameterSource.class), mapper.capture())).thenReturn(List.of());
        new PropertyRepositoryImpl(jdbc).findById(4L);
        ResultSet rs = mock(ResultSet.class, org.mockito.Mockito.withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
        when(rs.getString("pais")).thenReturn("Colombia");
        when(rs.getString("departamento")).thenReturn("Antioquia");
        PropertyDTO dto = mapper.getValue().mapRow(rs, 0);
        assertEquals("Colombia", dto.getPais());
        assertEquals("Antioquia", dto.getDepartamento());
    }

    @Test
    void propertyRepository_updateWithNullCountryKeepsColombiaDefault() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(jdbc.update(anyString(), params.capture())).thenReturn(1);
        new PropertyRepositoryImpl(jdbc).update(4L, new PropertyRequestDTO());
        assertEquals("Colombia", params.getValue().getValue("pais"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<RowMapper<T>> rowMapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(RowMapper.class);
    }
}
