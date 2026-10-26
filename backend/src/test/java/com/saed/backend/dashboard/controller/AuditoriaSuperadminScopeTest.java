package com.saed.backend.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * DEC-02: SUPERADMIN may see who did what on the platform, but not the content of a tenant's records. Audit rows
 * that belong to an organization keep their metadata and lose ESTADO_ANTERIOR/ESTADO_NUEVO for SUPERADMIN.
 */
class AuditoriaSuperadminScopeTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AuditoriaController controller = new AuditoriaController(jdbc);

    @AfterEach
    void clear() {
        SaedContextHolder.clearContext();
    }

    @Test
    void superadminListingMasksTenantStatePayloads() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").build());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());

        controller.listar(null, null, null, null, null, 100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(MapSqlParameterSource.class));
        assertThat(sql.getValue())
                .contains("CASE WHEN ID_ORGANIZACION IS NULL THEN ESTADO_ANTERIOR END")
                .contains("CASE WHEN ID_ORGANIZACION IS NULL THEN ESTADO_NUEVO END");
    }

    @Test
    void superadminDetailMasksTenantStatePayloads() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").build());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(Map.of("ID_LOG", 5)));

        controller.detalle(5L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(MapSqlParameterSource.class));
        assertThat(sql.getValue())
                .doesNotContain("SELECT *")
                .contains("CASE WHEN ID_ORGANIZACION IS NULL THEN ESTADO_NUEVO END");
    }

    @Test
    void organizationAdminKeepsItsOwnStatePayloads() {
        SaedContextHolder.setContext(SaedContext.builder().userId(2L).organizationId(7L)
                .roleCode("ADMIN_ORGANIZACION").build());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());

        controller.listar(null, null, null, null, null, 100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(MapSqlParameterSource.class));
        assertThat(sql.getValue()).contains("ESTADO_NUEVO").doesNotContain("CASE WHEN ID_ORGANIZACION IS NULL")
                .contains("ID_ORGANIZACION = :ctxOrgId");
    }

    @Test
    void listingLimitIsBounded() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).roleCode("SUPERADMIN").build());
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());

        controller.listar(null, null, null, null, null, 10_000_000);

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(anyString(), params.capture());
        assertThat(params.getValue().getValue("limite")).isEqualTo(500);
    }
}
