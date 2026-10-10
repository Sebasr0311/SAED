package com.saed.backend.org;

import com.saed.backend.org.dto.CreateOrgAdminRequestDTO;
import com.saed.backend.org.service.OrgAdminAccountService;
import com.saed.backend.org.service.OrgAdminAccountService.AccountResolution;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F3-03 / C-05: an organization administrator can only create or reuse identities that belong
 * to its own organization. Existing identities are never overwritten, passwords are never set
 * by another user and no bootstrap/global database context is used.
 */
class OrgAdminAccountIsolationTest {

    private static final long ORG = 10L;

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private OrgAdminAccountService service;

    @BeforeEach
    void setUp() {
        when(encoder.encode(anyString())).thenReturn("$2a$hash");
        service = new OrgAdminAccountService(jdbc, encoder);
    }

    private static CreateOrgAdminRequestDTO request() {
        CreateOrgAdminRequestDTO r = new CreateOrgAdminRequestDTO();
        r.setNombreUsuario("nuevo.admin");
        r.setEmail("Victim@Example.com");
        r.setPassword("Attacker-Chosen-1!");
        r.setPrimerNombre("Ana");
        r.setPrimerApellido("Gomez");
        r.setNumeroDocumento("123456");
        return r;
    }

    private void personaByDocument(Long idPersona, Long personaOrg) {
        if (idPersona == null) {
            when(jdbc.queryForList(contains("FROM PERSONAS"), anyMap())).thenReturn(List.of());
            return;
        }
        Map<String, Object> row = new HashMap<>();
        row.put("ID_PERSONA", idPersona);
        row.put("ID_ORGANIZACION", personaOrg);
        when(jdbc.queryForList(contains("FROM PERSONAS"), anyMap())).thenReturn(List.of(row));
    }

    private void userByPersona(Long idUsuario) {
        when(jdbc.queryForList(contains("WHERE ID_PERSONA = :p"), anyMap()))
                .thenReturn(idUsuario == null ? List.of() : List.of(Map.of("ID_USUARIO", idUsuario)));
    }

    private void userByEmail(Long idUsuario) {
        when(jdbc.queryForList(contains("LOWER(EMAIL) = :email"), anyMap()))
                .thenReturn(idUsuario == null ? List.of() : List.of(Map.of("ID_USUARIO", idUsuario)));
    }

    private void assignments(long inOrg, long outsideOrg, long privileged) {
        when(jdbc.queryForObject(contains("ua.ID_ORGANIZACION = :org"), anyMap(), eq(Long.class))).thenReturn(inOrg);
        when(jdbc.queryForObject(contains("ua.ID_ORGANIZACION <> :org"), anyMap(), eq(Long.class))).thenReturn(outsideOrg);
        when(jdbc.queryForObject(contains("r.CODIGO IN ('SUPERADMIN', 'ADMIN_ORGANIZACION')"), anyMap(), eq(Long.class)))
                .thenReturn(privileged);
    }

    private void verifyNoIdentityWrites() {
        verify(jdbc, never()).update(contains("UPDATE USUARIOS"), anyMap());
        verify(jdbc, never()).update(contains("UPDATE USUARIOS"), any(SqlParameterSource.class));
        verify(jdbc, never()).update(contains("UPDATE PERSONAS"), anyMap());
        verify(jdbc, never()).update(contains("UPDATE PERSONAS"), any(SqlParameterSource.class));
        verify(jdbc, never()).getJdbcOperations();
    }

    @Test
    void existingUserOfAnotherOrganizationIsRejectedAndUntouched() {
        personaByDocument(7L, 99L);
        userByPersona(70L);
        assignments(0, 1, 0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resolveOrCreate(ORG, request()));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verifyNoIdentityWrites();
    }

    @Test
    void existingUserFoundByEmailOutsideTheOrganizationIsRejected() {
        personaByDocument(null, null);
        userByEmail(71L);
        assignments(0, 1, 0);

        assertThrows(ResponseStatusException.class, () -> service.resolveOrCreate(ORG, request()));
        verifyNoIdentityWrites();
    }

    @Test
    void privilegedAccountInsideTheOrganizationCannotBeReused() {
        personaByDocument(7L, ORG);
        userByPersona(72L);
        assignments(1, 0, 1);

        assertThrows(ResponseStatusException.class, () -> service.resolveOrCreate(ORG, request()));
        verifyNoIdentityWrites();
    }

    @Test
    void ordinaryAccountOfTheSameOrganizationIsReusedWithoutCredentialOrPersonChanges() {
        personaByDocument(7L, ORG);
        userByPersona(73L);
        assignments(1, 0, 0);

        AccountResolution resolution = service.resolveOrCreate(ORG, request());

        assertEquals(73L, resolution.idUsuario());
        assertFalse(resolution.created());
        verifyNoIdentityWrites();
        verify(encoder, never()).encode(anyString());
    }

    @Test
    void personOfAnotherOrganizationWithoutAccountIsNotOverwrittenOrLinked() {
        personaByDocument(7L, 99L);
        userByPersona(null);
        userByEmail(null);

        assertThrows(ResponseStatusException.class, () -> service.resolveOrCreate(ORG, request()));
        verifyNoIdentityWrites();
        verify(jdbc, never()).update(contains("INSERT INTO USUARIOS"), any(SqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
    }

    @Test
    void newIdentityIsCreatedPendingActivationIgnoringTheRequestedPassword() {
        personaByDocument(null, null);
        userByPersona(null);
        userByEmail(null);
        doAnswer(inv -> {
            KeyHolder kh = inv.getArgument(2);
            String sql = inv.getArgument(0);
            kh.getKeyList().add(Map.of("ID", sql.contains("PERSONAS") ? 501L : 601L));
            return 1;
        }).when(jdbc).update(anyString(), any(SqlParameterSource.class), any(KeyHolder.class), any(String[].class));

        AccountResolution resolution = service.resolveOrCreate(ORG, request());

        assertEquals(601L, resolution.idUsuario());
        assertTrue(resolution.created());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, atLeastOnce()).update(sql.capture(), params.capture(), any(KeyHolder.class), any(String[].class));
        List<String> statements = sql.getAllValues();
        int userInsert = indexOf(statements, "INSERT INTO USUARIOS");
        int personaInsert = indexOf(statements, "INSERT INTO PERSONAS");
        assertTrue(statements.get(userInsert).contains("'PENDIENTE_VERIFICACION'"));
        assertEquals(ORG, params.getAllValues().get(personaInsert).getValue("org"));
        verify(encoder, never()).encode("Attacker-Chosen-1!");
        verifyNoIdentityWrites();
    }

    @Test
    void identityChangesAreRefusedForAccountsThatAlsoBelongToAnotherOrganization() {
        assignments(1, 1, 0);

        assertThrows(AccessDeniedException.class, () -> service.assertIdentityManagedByOrganization(ORG, 80L));
    }

    @Test
    void identityChangesAreRefusedForAccountsOutsideTheOrganization() {
        assignments(0, 0, 0);

        assertThrows(AccessDeniedException.class, () -> service.assertIdentityManagedByOrganization(ORG, 82L));
    }

    @Test
    void identityChangesAreAllowedForAccountsOnlyInTheOrganization() {
        assignments(1, 0, 0);

        assertDoesNotThrow(() -> service.assertIdentityManagedByOrganization(ORG, 81L));
    }

    private static int indexOf(List<String> statements, String fragment) {
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).contains(fragment)) {
                return i;
            }
        }
        throw new AssertionError("statement not executed: " + fragment);
    }
}
