package com.saed.backend.platform.entitlement;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.authorization.service.PropertyService;
import com.saed.backend.authorization.service.PropertyStatusService;
import com.saed.backend.common.exception.PlanLimitExceededException;
import com.saed.backend.platform.exception.InactiveMembershipException;
import com.saed.backend.platform.service.PlanLimitService;
import com.saed.backend.platform.service.impl.PlanLimitServiceImpl;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F03-02: plan limits are enforced under a pessimistic lock on the organization's active
 * membership row, taken in the caller's transaction before counting, so concurrent creators of
 * the same organization are serialized. Oracle-level proof with two sessions remains a database
 * tier task; these tests pin the ordering and the boundary.
 */
class PlanLimitLockingTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PlanLimitServiceImpl service = new PlanLimitServiceImpl(jdbc);

    @SuppressWarnings("unchecked")
    private void activeMembership(Long propertyLimit, Long unitLimit, Long userLimit) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("ID_MEMBRESIA")).thenReturn(1L);
        when(rs.getLong("ID_PLAN")).thenReturn(2L);
        when(rs.getString("ESTADO")).thenReturn("ACTIVA");
        when(rs.getObject("LIMITE_PROPIEDADES")).thenReturn(propertyLimit);
        when(rs.getLong("LIMITE_PROPIEDADES")).thenReturn(propertyLimit == null ? 0L : propertyLimit);
        when(rs.getObject("LIMITE_UNIDADES")).thenReturn(unitLimit);
        when(rs.getLong("LIMITE_UNIDADES")).thenReturn(unitLimit == null ? 0L : unitLimit);
        when(rs.getObject("LIMITE_USUARIOS")).thenReturn(userLimit);
        when(rs.getLong("LIMITE_USUARIOS")).thenReturn(userLimit == null ? 0L : userLimit);
        when(jdbc.query(contains("FOR UPDATE OF m.ID_MEMBRESIA"), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(inv -> List.of(((RowMapper<Object>) inv.getArgument(2)).mapRow(rs, 0)));
    }

    private void currentCount(String fromFragment, long count) {
        when(jdbc.queryForObject(contains(fromFragment), any(SqlParameterSource.class), eq(Number.class))).thenReturn(count);
    }

    @Test
    @SuppressWarnings("unchecked")
    void membershipRowIsLockedBeforeCounting() throws Exception {
        activeMembership(5L, 100L, 50L);
        currentCount("FROM PROPIEDADES", 4L);

        service.validateAndLockPropertyLimit(10L);

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).query(contains("FOR UPDATE OF m.ID_MEMBRESIA"), any(SqlParameterSource.class), any(RowMapper.class));
        order.verify(jdbc).queryForObject(contains("FROM PROPIEDADES"), any(SqlParameterSource.class), eq(Number.class));
    }

    @Test
    void lockOnlyConsidersCurrentActiveOrTrialMemberships() throws Exception {
        activeMembership(5L, 100L, 50L);
        currentCount("FROM PROPIEDADES", 0L);

        service.validateAndLockPropertyLimit(10L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertTrue(sql.getValue().contains("m.ESTADO IN ('ACTIVA', 'PRUEBA')"));
        assertTrue(sql.getValue().contains("FECHA_FIN >= TRUNC(SYSDATE)"));
    }

    @Test
    void lastFreeSlotIsGrantedAndTheNextIsRefused() throws Exception {
        activeMembership(5L, 10L, 3L);
        currentCount("FROM PROPIEDADES", 4L);
        assertDoesNotThrow(() -> service.validateAndLockPropertyLimit(10L));

        currentCount("FROM PROPIEDADES", 5L);
        assertThrows(PlanLimitExceededException.class, () -> service.validateAndLockPropertyLimit(10L));

        currentCount("FROM UNIDADES", 10L);
        assertThrows(PlanLimitExceededException.class, () -> service.validateAndLockUnitLimit(10L));

        currentCount("COUNT(DISTINCT ua.ID_USUARIO)", 3L);
        assertThrows(PlanLimitExceededException.class, () -> service.validateAndLockUserLimit(10L, null));
    }

    @Test
    void suspendedOrExpiredMembershipCannotCreateResources() {
        when(jdbc.query(contains("FOR UPDATE OF m.ID_MEMBRESIA"), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM MEMBRESIAS m"), any(SqlParameterSource.class)))
                .thenReturn(List.of(Map.of("ESTADO", "SUSPENDIDA")));

        assertThrows(InactiveMembershipException.class, () -> service.validateAndLockPropertyLimit(10L));
        assertThrows(InactiveMembershipException.class, () -> service.validateAndLockUnitLimit(10L));
        assertThrows(InactiveMembershipException.class, () -> service.validateAndLockUserLimit(10L, 7L));
    }

    @Test
    void limitChecksJoinTheCallersTransaction() throws NoSuchMethodException {
        for (String name : List.of("validateAndLockPropertyLimit", "validateAndLockUnitLimit")) {
            Method m = PlanLimitServiceImpl.class.getMethod(name, Long.class);
            assertTransactionalRequired(m);
        }
        assertTransactionalRequired(PlanLimitServiceImpl.class.getMethod("validateAndLockUserLimit", Long.class, Long.class));
    }

    private static void assertTransactionalRequired(Method m) {
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx, m.getName());
        assertEquals(Propagation.REQUIRED, tx.propagation(), m.getName());
    }

    // ------------------------------------------------------------ property reactivation gap

    @Test
    void reactivatingAPropertyConsumesAPlanSlotFirst() {
        PropertyRepository repo = mock(PropertyRepository.class);
        PlanLimitService limits = mock(PlanLimitService.class);
        PropertyDTO inactive = new PropertyDTO();
        inactive.setIdOrganizacion(10L);
        inactive.setEstado("INACTIVA");
        when(repo.findById(5L)).thenReturn(Optional.of(inactive));
        PropertyService properties = new PropertyService(repo, mock(PropertyStatusService.class), limits);

        properties.updateStatus(5L, "ACTIVA");

        InOrder order = inOrder(limits, repo);
        order.verify(limits).validateAndLockPropertyLimit(10L);
        order.verify(repo).updateStatus(5L, "ACTIVA");
    }

    @Test
    void deactivatingOrKeepingActiveDoesNotConsumeASlot() {
        PropertyRepository repo = mock(PropertyRepository.class);
        PlanLimitService limits = mock(PlanLimitService.class);
        PropertyDTO active = new PropertyDTO();
        active.setIdOrganizacion(10L);
        active.setEstado("ACTIVA");
        when(repo.findById(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.of(active));
        PropertyService properties = new PropertyService(repo, mock(PropertyStatusService.class), limits);

        properties.updateStatus(5L, "INACTIVA");
        properties.updateStatus(5L, "ACTIVA");

        verify(limits, never()).validateAndLockPropertyLimit(any());
    }
}
