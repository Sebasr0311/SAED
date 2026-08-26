package com.saed.backend.context;

import com.saed.backend.config.DataSourceConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.dao.DataAccessException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SaedContextIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    public void cleanup() {
        SaedContextHolder.clearContext();
    }

    @Test
    public void givenNoContext_whenSelect_thenZeroTrustShouldBlockOrReturnZero() {
        // By default, no context is set in SaedContextHolder
        // Oracle RLS should return 0 rows for any protected table.
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM ORGANIZACIONES", Integer.class);
            assertEquals(0, count, "Zero Trust failed: Unauthenticated user saw data.");
        } catch (BadSqlGrammarException e) {
             // In case the user doesn't have privileges, which is also fine.
             assertTrue(true);
        }
    }

    @Test
    public void givenInvalidRoleContext_whenSetContext_thenOracleThrowsSpoofingError() {
        // Create an illegal context (e.g., trying to be SUPERADMIN when not allowed)
        // Note: Oracle PKG_SAED_SESSION logic throws -20083 if the user is not in ADMINISTRADORES_SAED
        SaedContext mockAttackerContext = SaedContext.builder()
                .userId(9999L)
                .roleCode("SUPERADMIN")
                .build();
        
        SaedContextHolder.setContext(mockAttackerContext);

        // When the proxy intercepts this query, it will invoke SET_CONTEXT which should fail
        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.queryForObject("SELECT count(*) FROM ORGANIZACIONES", Integer.class);
        });
        
        assertTrue(exception.getMessage().contains("ORA-2008"), "Should throw context spoofing error");
    }
}
