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
public class CheckTableTest {
    @Autowired
    private DataSource dataSource;

    @Test
    public void checkColumns() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT column_name FROM user_tab_columns WHERE table_name = 'USUARIO_ASIGNACIONES'");
            System.out.println("--- COLUMNAS ---");
            while (rs.next()) {
                System.out.println(rs.getString(1));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
