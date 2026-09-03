package com.saed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootTest
@ActiveProfiles("dev")
public class ParcheTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void aplicarParche() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            
            String sql = Files.readString(Paths.get("../database/migrations/V5.2__fix_propiedades_rls_recursion.sql"));
            // Strip trailing slash and comments
            String cleanSql = sql.replaceAll("/\\s*$", "").trim();
            jdbcTemplate.execute(cleanSql);
            System.out.println("PKG_SAED_SECURITY_RLS ACTUALIZADO CON ÉXITO.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
