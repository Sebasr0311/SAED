package com.saed.backend.porteria.repository;

import com.saed.backend.porteria.dto.ParkingSpotClaim;
import com.saed.backend.porteria.repository.impl.PorteriaRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F07-03 / F7-04: a visitor spot is claimed with a conditional update, so a spot taken concurrently by
 * another gate (0 rows updated) is skipped instead of being double-assigned.
 */
class PorteriaParkingClaimTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PorteriaRepositoryImpl repo;

    PorteriaParkingClaimTest() {
        when(jdbc.getJdbcTemplate()).thenReturn(mock(JdbcTemplate.class));
        repo = new PorteriaRepositoryImpl(jdbc);
    }

    @Test
    void spotTakenConcurrentlyIsSkippedAndTheNextOneIsClaimed() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(
            Map.of("ID_PARQUEADERO", 1L, "NUMERO_PARQUEADERO", "V-1"),
            Map.of("ID_PARQUEADERO", 2L, "NUMERO_PARQUEADERO", "V-2")));
        List<String> updates = new ArrayList<>();
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenAnswer(inv -> {
            updates.add(inv.getArgument(0));
            return updates.size() == 1 ? 0 : 1; // V-1 lost the race, V-2 is claimed
        });

        Optional<ParkingSpotClaim> claim = repo.claimAvailableSpot(70L, "VISITANTES");

        assertEquals(Optional.of(new ParkingSpotClaim(2L, "V-2")), claim);
        assertTrue(updates.stream().allMatch(s -> s.contains("ESTADO = 'DISPONIBLE'") && s.contains("TIPO = :tipo")));
    }

    @Test
    void noSpotIsReturnedWhenEveryCandidateWasTaken() {
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of(
            Map.of("ID_PARQUEADERO", 1L, "NUMERO_PARQUEADERO", "V-1")));
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

        assertEquals(Optional.empty(), repo.claimAvailableSpot(70L, "VISITANTES"));
    }

    @Test
    void vehicleCloseIsConditionalSoASpotIsReleasedOnce() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
        assertEquals(false, repo.closeVehiculoVisita(3L, java.math.BigDecimal.ZERO));
    }
}
