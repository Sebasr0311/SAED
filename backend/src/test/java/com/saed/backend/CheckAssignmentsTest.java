package com.saed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;

@SpringBootTest
@ActiveProfiles("dev")
public class CheckAssignmentsTest {
    @Autowired
    private DataSource dataSource;

    @Test
    public void runScript() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT ID_ASIGNACION, ID_USUARIO, ID_ROL FROM USUARIO_ASIGNACIONES");
            System.out.println("--- ASIGNACIONES ACTUALES ---");
            while (rs.next()) {
                System.out.println("Asignacion: " + rs.getLong("ID_ASIGNACION") + " | Usuario: " + rs.getLong("ID_USUARIO") + " | Rol: " + rs.getLong("ID_ROL"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
