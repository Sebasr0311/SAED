package com.saed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("dev")
public class DatabaseAuditCheckTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testAuditTableExists() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM user_tables WHERE table_name = 'AUDITORIA_LOG'", Integer.class);
        System.out.println("===========================================");
        System.out.println("AUDITORIA_LOG table count: " + count);
        System.out.println("===========================================");
        assertTrue(count > 0, "La tabla de auditoria no existe en Oracle");
    }
}
