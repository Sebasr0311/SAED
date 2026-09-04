package com.saed.backend.demo;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DemoDatasetRunnerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    @DisplayName("1. Execute V5.99__demo_seeds.sql and populate demo dataset")
    public void test01_executeDemoSeedScript() throws IOException {
        File seedFile = new File("../database/demo/V5.99__demo_seeds.sql");
        if (!seedFile.exists()) {
            seedFile = new File("database/demo/V5.99__demo_seeds.sql");
        }
        assertTrue(seedFile.exists(), "Seed script file must exist at " + seedFile.getAbsolutePath());

        String sqlContent = Files.readString(seedFile.toPath(), StandardCharsets.UTF_8);

        // Establish SuperAdmin ThreadLocal context so SaedDataSourceProxy injects PKG_SAED_SESSION on every pooled connection
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L)
                .roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        try {
            System.out.println("=== ROLES IN DB ===");
            for (Map<String, Object> r : jdbcTemplate.queryForList("SELECT ID_ROL, CODIGO, ALCANCE, ESTADO FROM ROLES ORDER BY ID_ROL")) {
                System.out.println("  ROLE: " + r);
            }
            System.out.println("=== ASIGNACIONES IN DB ===");
            for (Map<String, Object> a : jdbcTemplate.queryForList("SELECT ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO FROM USUARIO_ASIGNACIONES")) {
                System.out.println("  ASIG: " + a);
            }
        } catch (Exception diagEx) {
            System.out.println("Diag error: " + diagEx.getMessage());
        }

        // Clean comments and SQL*Plus commands
        StringBuilder cleanSql = new StringBuilder();
        for (String line : sqlContent.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("--") || t.toUpperCase().startsWith("WHENEVER") || t.toUpperCase().startsWith("ALTER SESSION")) {
                continue;
            }
            cleanSql.append(line).append("\n");
        }

        // Split by statement (separated by semicolons and slash for PL/SQL blocks)
        String[] blocks = cleanSql.toString().split("(?m)^/\\s*$");
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // If it is a PL/SQL block (starts with BEGIN or DECLARE)
            if (trimmed.toUpperCase().startsWith("BEGIN") || trimmed.toUpperCase().startsWith("DECLARE")) {
                try {
                    jdbcTemplate.execute(trimmed);
                } catch (Exception e) {
                    System.err.println("PL/SQL block execution note: " + e.getMessage());
                }
            } else {
                // Split individual SQL statements by semicolon
                String[] stmts = trimmed.split(";");
                for (String stmt : stmts) {
                    String cleanStmt = stmt.trim();
                    if (!cleanStmt.isEmpty() && !cleanStmt.equalsIgnoreCase("COMMIT")) {
                        try {
                            jdbcTemplate.execute(cleanStmt);
                        } catch (Exception e) {
                            System.err.println("Statement execution error [" + cleanStmt + "]: " + e.getMessage());
                            throw e;
                        }
                    }
                }
            }
        }

        jdbcTemplate.execute("COMMIT");
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @Test
    @Order(2)
    @DisplayName("2. Validate Core Organization and Property structure")
    public void test02_validateOrgAndProperty() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        Integer orgCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ORGANIZACIONES WHERE ID_ORGANIZACION = 1", Integer.class);
        assertEquals(1, orgCount, "Organization 1 must exist");

        String orgName = jdbcTemplate.queryForObject("SELECT NOMBRE FROM ORGANIZACIONES WHERE ID_ORGANIZACION = 1", String.class);
        assertEquals("SAED Global S.A.S.", orgName);

        Integer propCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PROPIEDADES WHERE ID_PROPIEDAD = 1 AND ID_ORGANIZACION = 1", Integer.class);
        assertEquals(1, propCount, "Property 1 must exist in Organization 1");

        String propName = jdbcTemplate.queryForObject("SELECT NOMBRE FROM PROPIEDADES WHERE ID_PROPIEDAD = 1", String.class);
        assertEquals("Edificio Residencial SAED", propName);

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @Test
    @Order(3)
    @DisplayName("3. Validate the 4 Units (Apto 101, 102, 201, 202)")
    public void test03_validateFourUnits() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        List<String> units = jdbcTemplate.queryForList(
                "SELECT IDENTIFICADOR FROM UNIDADES WHERE ID_PROPIEDAD = 1 ORDER BY ID_UNIDAD ASC", String.class);
        assertTrue(units.size() >= 4, "Must have at least 4 units");
        assertTrue(units.contains("Apto 101"), "Must contain Apto 101");
        assertTrue(units.contains("Apto 102"), "Must contain Apto 102");
        assertTrue(units.contains("Apto 201"), "Must contain Apto 201");
        assertTrue(units.contains("Apto 202"), "Must contain Apto 202");

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @Test
    @Order(4)
    @DisplayName("4. Validate Demo Users, Roles and Assignments")
    public void test04_validateUsersAndAssignments() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // Validate user usernames
        List<String> usernames = jdbcTemplate.queryForList(
                "SELECT NOMBRE_USUARIO FROM USUARIOS WHERE ID_USUARIO IN (1, 2, 3, 4, 5) ORDER BY ID_USUARIO ASC", String.class);
        assertEquals(List.of("admin_global", "admin", "portero01", "camartinez", "anagomez"), usernames);

        // Validate Admin Propiedad assignment
        Map<String, Object> adminAsig = jdbcTemplate.queryForMap(
                "SELECT ua.ID_USUARIO, r.CODIGO as ROL, ua.ID_ORGANIZACION, ua.ID_PROPIEDAD, ua.ID_UNIDAD " +
                "FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON ua.ID_ROL = r.ID_ROL WHERE ua.ID_ASIGNACION = 102");
        assertEquals("ADMIN_PROPIEDAD", adminAsig.get("ROL"));
        assertEquals(1L, ((Number) adminAsig.get("ID_ORGANIZACION")).longValue());
        assertEquals(1L, ((Number) adminAsig.get("ID_PROPIEDAD")).longValue());
        assertNull(adminAsig.get("ID_UNIDAD"));

        // Validate Portero assignment
        Map<String, Object> porteroAsig = jdbcTemplate.queryForMap(
                "SELECT ua.ID_USUARIO, r.CODIGO as ROL, ua.ID_ORGANIZACION, ua.ID_PROPIEDAD, ua.ID_UNIDAD " +
                "FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON ua.ID_ROL = r.ID_ROL WHERE ua.ID_ASIGNACION = 103");
        assertEquals("PORTERO", porteroAsig.get("ROL"));
        assertEquals(1L, ((Number) porteroAsig.get("ID_PROPIEDAD")).longValue());

        // Validate Resident 1 assignment
        Map<String, Object> res1Asig = jdbcTemplate.queryForMap(
                "SELECT ua.ID_USUARIO, r.CODIGO as ROL, ua.ID_ORGANIZACION, ua.ID_PROPIEDAD, ua.ID_UNIDAD " +
                "FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON ua.ID_ROL = r.ID_ROL WHERE ua.ID_ASIGNACION = 104");
        assertEquals("RESIDENTE", res1Asig.get("ROL"));
        assertEquals(1L, ((Number) res1Asig.get("ID_UNIDAD")).longValue());

        // Validate Resident 2 assignment
        Map<String, Object> res2Asig = jdbcTemplate.queryForMap(
                "SELECT ua.ID_USUARIO, r.CODIGO as ROL, ua.ID_ORGANIZACION, ua.ID_PROPIEDAD, ua.ID_UNIDAD " +
                "FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON ua.ID_ROL = r.ID_ROL WHERE ua.ID_ASIGNACION = 105");
        assertEquals("RESIDENTE", res2Asig.get("ROL"));
        assertEquals(2L, ((Number) res2Asig.get("ID_UNIDAD")).longValue());

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @Test
    @Order(5)
    @DisplayName("5. Validate Visitor, Scheduled Visit, and QR Token")
    public void test05_validateVisitsAndQR() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // Visitor persona
        Integer visitorPersona = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM PERSONAS WHERE ID_PERSONA = 10", Integer.class);
        assertEquals(1, visitorPersona, "Persona 10 (Visitante Demo) must exist");

        // Scheduled visit
        Map<String, Object> visita = jdbcTemplate.queryForMap(
                "SELECT ID_VISITA, ID_UNIDAD, METODO_INGRESO, MOTIVO, ESTADO FROM VISITAS WHERE ID_VISITA = 100");
        assertEquals(1L, ((Number) visita.get("ID_UNIDAD")).longValue());
        assertEquals("CODIGO_QR", visita.get("METODO_INGRESO"));
        assertEquals("PROGRAMADA", visita.get("ESTADO"));

        // QR Token
        Map<String, Object> qr = jdbcTemplate.queryForMap(
                "SELECT TOKEN_QR, USOS_PERMITIDOS, USOS_CONSUMIDOS, ESTADO FROM QR_ACCESOS WHERE TOKEN_QR = 'SAED-DEMO-QR-2026-TOKEN'");
        assertEquals("ACTIVO", qr.get("ESTADO"));
        assertEquals(5, ((Number) qr.get("USOS_PERMITIDOS")).intValue());
        assertEquals(0, ((Number) qr.get("USOS_CONSUMIDOS")).intValue());

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @Test
    @Order(6)
    @DisplayName("6. Validate Parking Spots & Visitor Vehicle")
    public void test06_validateParkingAndVehicle() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        Integer parkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PARQUEADEROS WHERE ID_PROPIEDAD = 1 AND NUMERO_PARQUEADERO IN ('V-01', 'V-02')", Integer.class);
        assertEquals(2, parkCount, "Must have 2 visitor parkings (V-01, V-02)");

        Map<String, Object> veh = jdbcTemplate.queryForMap(
                "SELECT PLACA, ID_PARQUEADERO, ID_VISITA, ESTADO FROM VEHICULOS_VISITA WHERE ID_VEHICULO_VISITA = 1");
        assertEquals("DEM-123", veh.get("PLACA"));
        assertEquals(100L, ((Number) veh.get("ID_VISITA")).longValue());
        assertEquals(1L, ((Number) veh.get("ID_PARQUEADERO")).longValue());
        assertEquals("DENTRO", veh.get("ESTADO"));

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @Test
    @Order(7)
    @DisplayName("7. Validate Financial Ledger: Paid & Pending Cuotas, Payments and Balances")
    public void test07_validateFinancialLedger() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // Cuota 1: Unit 1, Agosto 2026 -> PAGADA
        Map<String, Object> c1 = jdbcTemplate.queryForMap("SELECT VALOR_BASE, SALDO_PENDIENTE, ESTADO FROM CUOTAS WHERE ID_CUOTA = 1");
        assertEquals("PAGADA", c1.get("ESTADO"));
        assertEquals(0, ((BigDecimal) c1.get("SALDO_PENDIENTE")).compareTo(BigDecimal.ZERO));

        // Cuota 2: Unit 1, Septiembre 2026 -> PENDIENTE
        Map<String, Object> c2 = jdbcTemplate.queryForMap("SELECT VALOR_BASE, SALDO_PENDIENTE, ESTADO FROM CUOTAS WHERE ID_CUOTA = 2");
        assertEquals("PENDIENTE", c2.get("ESTADO"));
        assertEquals(0, ((BigDecimal) c2.get("SALDO_PENDIENTE")).compareTo(new BigDecimal("250000.00")));

        // Cuota 3: Unit 2, Septiembre 2026 -> PAGADA
        Map<String, Object> c3 = jdbcTemplate.queryForMap("SELECT VALOR_BASE, SALDO_PENDIENTE, ESTADO FROM CUOTAS WHERE ID_CUOTA = 3");
        assertEquals("PAGADA", c3.get("ESTADO"));
        assertEquals(0, ((BigDecimal) c3.get("SALDO_PENDIENTE")).compareTo(BigDecimal.ZERO));

        // Pagos: 2 approved payments total $500,000
        BigDecimal totalPagos = jdbcTemplate.queryForObject("SELECT SUM(MONTO_TOTAL) FROM PAGOS WHERE ID_PAGO IN (1, 2)", BigDecimal.class);
        assertNotNull(totalPagos);
        assertEquals(0, totalPagos.compareTo(new BigDecimal("500000.00")));

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @Test
    @Order(8)
    @DisplayName("8. Validate RLS Multi-Tenant Isolation across Roles")
    public void test08_validateRlsMultiTenantIsolation() {
        // As Residente 1 (Unit 1): only Cuotas for Unit 1 must be accessible
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(4L).organizationId(1L).propertyId(1L).unitId(1L)
                .roleCode("RESIDENTE").roleScope("UNIDAD").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(4, 1, 1, 'RESIDENTE'); END;");

        List<Long> res1Cuotas = jdbcTemplate.queryForList("SELECT ID_CUOTA FROM CUOTAS ORDER BY ID_CUOTA", Long.class);
        assertTrue(res1Cuotas.contains(1L), "Resident 1 can see their Cuota 1");
        assertTrue(res1Cuotas.contains(2L), "Resident 1 can see their Cuota 2");
        assertFalse(res1Cuotas.contains(3L), "Resident 1 CANNOT see Cuota 3 belonging to Unit 2");

        // As Residente 2 (Unit 2): only Cuotas for Unit 2 must be accessible
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(5L).organizationId(1L).propertyId(1L).unitId(2L)
                .roleCode("RESIDENTE").roleScope("UNIDAD").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(5, 1, 1, 'RESIDENTE'); END;");

        List<Long> res2Cuotas = jdbcTemplate.queryForList("SELECT ID_CUOTA FROM CUOTAS ORDER BY ID_CUOTA", Long.class);
        assertTrue(res2Cuotas.contains(3L), "Resident 2 can see their Cuota 3");
        assertFalse(res2Cuotas.contains(1L), "Resident 2 CANNOT see Cuota 1 belonging to Unit 1");
        assertFalse(res2Cuotas.contains(2L), "Resident 2 CANNOT see Cuota 2 belonging to Unit 1");

        // Clean up
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }
}
