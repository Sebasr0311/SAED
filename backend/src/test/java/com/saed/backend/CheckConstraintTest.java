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
public class CheckConstraintTest {
    @Autowired
    private DataSource dataSource;

    @Test
    public void check() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT search_condition FROM user_constraints WHERE constraint_name = 'CK_ASIGNACION_ESTADO'");
            if (rs.next()) {
                System.out.println("--- CHECK CONSTRAINT ---");
                System.out.println(rs.getString(1));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
