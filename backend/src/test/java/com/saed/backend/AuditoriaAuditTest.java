package com.saed.backend;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class AuditoriaAuditTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testAuditoriaTable() {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("SELECT table_name FROM user_tables WHERE table_name LIKE '%AUDITORIA%'");
        System.out.println("AUDITORIA TABLES: " + result);

        List<Map<String, Object>> triggers = jdbcTemplate.queryForList("SELECT trigger_name, status FROM user_triggers WHERE trigger_name LIKE 'TRG_AUDIT_%'");
        System.out.println("AUDITORIA TRIGGERS: " + triggers);
    }
}