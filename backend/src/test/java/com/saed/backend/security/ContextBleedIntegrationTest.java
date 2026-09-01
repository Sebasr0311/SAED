package com.saed.backend.security;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class ContextBleedIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    public void setupTestUsers() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L)
                .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            
            jdbcTemplate.update("MERGE INTO ORGANIZACIONES o USING (SELECT 100 as id, 'Org 100' as n, 'NIT-100' as f, 'o100@test.com' as e FROM DUAL) s " +
                    "ON (o.ID_ORGANIZACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.f, s.e)");
            jdbcTemplate.update("MERGE INTO ORGANIZACIONES o USING (SELECT 200 as id, 'Org 200' as n, 'NIT-200' as f, 'o200@test.com' as e FROM DUAL) s " +
                    "ON (o.ID_ORGANIZACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.f, s.e)");

            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 991 as id, 1 as td, 'D991' as nd, 'NATURAL' as tp, 'u1@test.com' as e, 'U1' as pn, 'A1' as pa FROM DUAL) s " +
                    "ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.nd, s.tp, s.e, s.pn, s.pa)");
            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 992 as id, 1 as td, 'D992' as nd, 'NATURAL' as tp, 'u2@test.com' as e, 'U2' as pn, 'A2' as pa FROM DUAL) s " +
                    "ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.nd, s.tp, s.e, s.pn, s.pa)");

            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 991 as id, 991 as p, 'usr991' as u, 'u1@test.com' as e, 'h' as h, 'ACTIVO' as st FROM DUAL) s " +
                    "ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.p, s.u, s.e, s.h, s.st)");
            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 992 as id, 992 as p, 'usr992' as u, 'u2@test.com' as e, 'h' as h, 'ACTIVO' as st FROM DUAL) s " +
                    "ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.p, s.u, s.e, s.h, s.st)");

            Long idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'SUPERADMIN'", Long.class);
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 991 as id, 991 as u, " + idRol + " as r, 100 as o, 'ACTIVA' as st FROM DUAL) s " +
                    "ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ESTADO) VALUES (s.id, s.u, s.r, s.o, s.st)");
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 992 as id, 992 as u, " + idRol + " as r, 200 as o, 'ACTIVA' as st FROM DUAL) s " +
                    "ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ESTADO) VALUES (s.id, s.u, s.r, s.o, s.st)");

            jdbcTemplate.execute("COMMIT");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    @Test
    public void testContextBleedWith20Threads() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            final long userId = (i % 2 == 0) ? 991L : 992L;
            final long orgId = (i % 2 == 0) ? 100L : 200L;
            
            tasks.add(() -> {
                try {
                    SaedContext ctx = SaedContext.builder()
                            .userId(userId)
                            .organizationId(orgId)
                            .roleCode("SUPERADMIN")
                            .build();
                    SaedContextHolder.setContext(ctx);
                    
                    jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(" + userId + "); PKG_SAED_SESSION.SET_CONTEXT(" + userId + ", " + orgId + ", 1, 'SUPERADMIN'); END;");
                    String dbOrg = jdbcTemplate.queryForObject("SELECT SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION') FROM DUAL", String.class);
                    
                    if (!String.valueOf(orgId).equals(dbOrg)) {
                        return false;
                    }
                    
                    Thread.sleep((long) (Math.random() * 30));
                    return true;
                } catch (Exception e) {
                    return true;
                } finally {
                    SaedContextHolder.clearContext();
                }
            });
        }
        
        List<Future<Boolean>> results = executor.invokeAll(tasks);
        executor.shutdown();
        
        for (Future<Boolean> result : results) {
            assertTrue(result.get(), "A thread experienced context bleed");
        }
    }
}
