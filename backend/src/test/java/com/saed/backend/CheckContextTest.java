package com.saed.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;

@SpringBootTest
@ActiveProfiles("dev")
public class CheckContextTest {
    @Autowired
    private DataSource dataSource;

    @Test
    public void test() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            System.out.println("--- TESTING SET_BOOTSTRAP_CONTEXT ---");
            try { 
                stmt.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;"); 
                System.out.println("SET_BOOTSTRAP_CONTEXT OK");
            } catch(Exception e){
                e.printStackTrace();
            }
            
            System.out.println("--- TESTING SET_CONTEXT ---");
            try { 
                stmt.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;"); 
                System.out.println("SET_CONTEXT OK");
            } catch(Exception e){
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
