package com.saed.backend.reservas;

import com.saed.backend.reservas.controller.ReservasController;
import com.saed.backend.reservas.dto.UpdateZonaComunDTO;
import com.saed.backend.reservas.repository.impl.ReservasRepositoryImpl;
import com.saed.backend.reservas.service.ReservasService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** F08-01: zone booking policy is editable by the administrator and the deposit can be registered. */
class ReservationPolicyPersistenceTest {

    @Test
    void zoneUpdatePersistsThePolicyAndKeepsUnsetValues() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ReservasRepositoryImpl repo = new ReservasRepositoryImpl(jdbc);
        UpdateZonaComunDTO dto = new UpdateZonaComunDTO();
        dto.setNombre("BBQ");
        dto.setTipo("BBQ");
        dto.setHoraApertura("08:00");
        dto.setHoraCierre("21:00");
        dto.setValorDeposito(new BigDecimal("100000"));

        repo.updateZona(5L, 70L, dto);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("HORA_APERTURA = NVL(?, HORA_APERTURA)"), sql.getValue());
        assertTrue(sql.getValue().contains("VALOR_DEPOSITO = NVL(?, VALOR_DEPOSITO)"), sql.getValue());
        List<Object> values = Arrays.asList(args.getValue());
        assertTrue(values.contains("08:00") && values.contains("21:00") && values.contains(new BigDecimal("100000")));
    }

    @Test
    void depositEndpointDelegatesToTheScopedService() {
        ReservasService service = mock(ReservasService.class);
        new ReservasController(service).registrarDeposito(900L, Map.of("monto", new BigDecimal("50000")));
        verify(service).registrarDeposito(900L, new BigDecimal("50000"));
    }

    @Test
    void policyHoursAreValidated() {
        assertTrue(hasPattern("horaApertura") && hasPattern("horaCierre"));
    }

    private static boolean hasPattern(String field) {
        try {
            return UpdateZonaComunDTO.class.getDeclaredField(field)
                .isAnnotationPresent(jakarta.validation.constraints.Pattern.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }
}
