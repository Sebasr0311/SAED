package com.saed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
public class ParcheTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void aplicarParche() {
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute("INSERT INTO ADMINISTRADORES_SAED (ID_ADMINISTRADOR_SAED, ID_USUARIO, NIVEL, ESTADO) VALUES (1, 1, 'SUPERADMIN', 'ACTIVO')");
            System.out.println("PARCHE APLICADO EXITOSAMENTE.");
        } catch (Exception e) {
            System.out.println("Ya existe o error: " + e.getMessage());
        }
    }
}
