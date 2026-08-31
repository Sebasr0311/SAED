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
public class CheckSchemaTest {
    @Autowired
    private DataSource dataSource;

    @Test
    public void check() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            System.out.println("--- PROPIEDADES COLUMNAS ---");
            ResultSet rs = stmt.executeQuery("SELECT column_name, data_type FROM user_tab_columns WHERE table_name = 'PROPIEDADES' ORDER BY column_id");
            while (rs.next()) {
                System.out.println(rs.getString(1) + " " + rs.getString(2));
            }
            
            System.out.println("--- UNIDADES COLUMNAS ---");
            rs = stmt.executeQuery("SELECT column_name, data_type FROM user_tab_columns WHERE table_name = 'UNIDADES' ORDER BY column_id");
            while (rs.next()) {
                System.out.println(rs.getString(1) + " " + rs.getString(2));
            }
            
            System.out.println("--- TIPOS_PROPIEDAD ---");
            rs = stmt.executeQuery("SELECT * FROM TIPOS_PROPIEDAD");
            while(rs.next()) {
                System.out.println(rs.getString(1) + " " + rs.getString(2));
            }
            
            System.out.println("--- TIPOS_UNIDAD ---");
            rs = stmt.executeQuery("SELECT * FROM TIPOS_UNIDAD");
            while(rs.next()) {
                System.out.println(rs.getString(1) + " " + rs.getString(2));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
