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
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM ORGANIZACIONES", Integer.class);
            assertEquals(0, count, "Zero Trust failed: Unauthenticated user saw data.");
        } catch (DataAccessException e) {
             assertTrue(e.getCause() != null && e.getCause().getMessage().contains("ORA-2008"), 
                        "Oracle should reject null/invalid user with ORA-2008X");
        }
    }

    @Test
    public void givenInvalidRoleContext_whenSetContext_thenOracleThrowsSpoofingError() {
        SaedContext mockAttackerContext = SaedContext.builder()
                .userId(9999L)
                .roleCode("SUPERADMIN")
                .build();
        
        SaedContextHolder.setContext(mockAttackerContext);

        DataAccessException exception = assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.queryForObject("SELECT count(*) FROM ORGANIZACIONES", Integer.class);
        });
        
        assertTrue(exception.getCause() != null && exception.getCause().getMessage().contains("ORA-2008"), 
                   "Should throw context spoofing error (ORA-2008X) from root cause");
    }
}
