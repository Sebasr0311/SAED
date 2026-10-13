package com.saed.backend.identity.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.service.ConvivienteQuotaService;
import com.saed.backend.platform.service.PlanLimitService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Isolated-Oracle matrix finding (recovery-13): SUPERADMIN received every user of every organization, including
 * residents with document number and phone. DEC-02: the platform administrator manages platform accounts only
 * (other SUPERADMIN accounts and organization administrators), never operational residents or staff.
 */
class SuperAdminUserListingScopeTest {

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void superAdminListsOnlyPlatformAccounts() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(List.of());
        UsuarioController controller = new UsuarioController(jdbc, mock(PasswordEncoder.class), mock(ObjectProvider.class),
                mock(EmailService.class), mock(ConvivienteQuotaService.class), mock(PlanLimitService.class));
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());

        controller.listarUsuarios();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(SqlParameterSource.class));
        String query = sql.getValue().replaceAll("\\s+", " ");
        assertTrue(query.contains("r.CODIGO IN ('SUPERADMIN', 'ADMIN_ORGANIZACION')"),
                "SUPERADMIN listing must be limited to platform accounts: " + query);
    }
}
