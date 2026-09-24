package com.saed.backend.finanzas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class ConciliacionFinancialIntegrityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Long testPropId;

    @BeforeEach
    void setUp() {
        setElevatedContext(1L, 1L, "SUPERADMIN");

        List<Map<String, Object>> props = jdbcTemplate.queryForList(
            "SELECT ID_PROPIEDAD FROM PROPIEDADES WHERE ID_ORGANIZACION = 1 ORDER BY ID_PROPIEDAD ASC"
        );
        if (!props.isEmpty()) {
            testPropId = ((Number) props.get(0).get("ID_PROPIEDAD")).longValue();
        } else {
            testPropId = 1L;
        }
    }

    private void setElevatedContext(Long orgId, Long propId, String roleCode) {
        SaedContext ctx = SaedContext.builder()
            .userId(1L)
            .organizationId(orgId)
            .propertyId(propId)
            .unitId(1L)
            .roleCode("SUPERADMIN")
            .roleScope("GLOBAL")
            .build();
        SaedContextHolder.setContext(ctx);

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
            jdbcTemplate.execute(String.format(
                "BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, 'SUPERADMIN'); END;",
                orgId, propId
            ));
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Test 1: Crear conciliación en estado EN_PROCESO exitosamente")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    void test01_crearConciliacion_enProceso_exitoso() throws Exception {
        setElevatedContext(1L, testPropId, "SUPERADMIN");
        jdbcTemplate.update("DELETE FROM CONCILIACIONES WHERE BANCO_CUENTA = 'Bancolombia-9876' AND PERIODO = '2026-05'");

        Map<String, Object> body = Map.of(
            "bancoCuenta", "Bancolombia-9876",
            "periodo", "2026-05",
            "saldoBanco", 15000000,
            "saldoLibros", 15000000
        );

        mockMvc.perform(post("/api/v1/conciliaciones")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.id", notNullValue()));

        setElevatedContext(1L, 1L, "SUPERADMIN");
        String estadoDB = jdbcTemplate.queryForObject(
            "SELECT ESTADO FROM CONCILIACIONES WHERE BANCO_CUENTA = 'Bancolombia-9876' AND PERIODO = '2026-05'",
            String.class
        );
        assertEquals("EN_PROCESO", estadoDB, "El estado inicial persistido debe ser EN_PROCESO");
    }

    @Test
    @DisplayName("Test 2: Transicionar a CONCILIADO sin violación ORA-02290")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    void test02_cambiarEstado_conciliado_sinViolacionConstraint() throws Exception {
        setElevatedContext(1L, testPropId, "SUPERADMIN");
        jdbcTemplate.update("DELETE FROM CONCILIACIONES WHERE BANCO_CUENTA = 'Davivienda-1234' AND PERIODO = '2026-06'");

        // Crear registro en base de datos
        jdbcTemplate.update(
            "INSERT INTO CONCILIACIONES (ID_PROPIEDAD, BANCO_CUENTA, PERIODO, SALDO_BANCO, SALDO_LIBROS, ESTADO, CONCILIADO_POR) " +
            "VALUES (?, 'Davivienda-1234', '2026-06', 20000000, 20000000, 'EN_PROCESO', 1)",
            testPropId
        );
        Long id = jdbcTemplate.queryForObject(
            "SELECT ID_CONCILIACION FROM CONCILIACIONES WHERE BANCO_CUENTA = 'Davivienda-1234' AND PERIODO = '2026-06'",
            Long.class
        );

        Map<String, String> patchBody = Map.of("estado", "CONCILIADO");

        mockMvc.perform(patch("/api/v1/conciliaciones/" + id + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        setElevatedContext(1L, 1L, "SUPERADMIN");
        String estadoFinal = jdbcTemplate.queryForObject(
            "SELECT ESTADO FROM CONCILIACIONES WHERE ID_CONCILIACION = ?",
            String.class, id
        );
        assertEquals("CONCILIADO", estadoFinal, "El estado en Oracle debe persistirse exactamente como CONCILIADO");
    }

    @Test
    @DisplayName("Test 3: Transicionar a CON_DIFERENCIAS sin violación ORA-02290")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    void test03_cambiarEstado_conDiferencias_sinViolacionConstraint() throws Exception {
        setElevatedContext(1L, testPropId, "SUPERADMIN");
        jdbcTemplate.update("DELETE FROM CONCILIACIONES WHERE BANCO_CUENTA = 'BBVA-5566' AND PERIODO = '2026-07'");

        jdbcTemplate.update(
            "INSERT INTO CONCILIACIONES (ID_PROPIEDAD, BANCO_CUENTA, PERIODO, SALDO_BANCO, SALDO_LIBROS, ESTADO, CONCILIADO_POR) " +
            "VALUES (?, 'BBVA-5566', '2026-07', 18000000, 19500000, 'EN_PROCESO', 1)",
            testPropId
        );
        Long id = jdbcTemplate.queryForObject(
            "SELECT ID_CONCILIACION FROM CONCILIACIONES WHERE BANCO_CUENTA = 'BBVA-5566' AND PERIODO = '2026-07'",
            Long.class
        );

        Map<String, String> patchBody = Map.of("estado", "CON_DIFERENCIAS");

        mockMvc.perform(patch("/api/v1/conciliaciones/" + id + "/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        setElevatedContext(1L, 1L, "SUPERADMIN");
        String estadoFinal = jdbcTemplate.queryForObject(
            "SELECT ESTADO FROM CONCILIACIONES WHERE ID_CONCILIACION = ?",
            String.class, id
        );
        assertEquals("CON_DIFERENCIAS", estadoFinal, "El estado en Oracle debe persistirse exactamente como CON_DIFERENCIAS");
    }

    @Test
    @DisplayName("Test 4: Enviar 'CONCILIADA' es rechazado con 400 Bad Request")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    void test04_enviarConciliada_esRechazadoCon400() throws Exception {
        setElevatedContext(1L, testPropId, "SUPERADMIN");

        Map<String, String> patchBody = Map.of("estado", "CONCILIADA");

        mockMvc.perform(patch("/api/v1/conciliaciones/1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.message", containsString("EN_PROCESO, CONCILIADO o CON_DIFERENCIAS")));
    }

    @Test
    @DisplayName("Test 5: Enviar 'DISCREPANCIA' es rechazado con 400 Bad Request")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    void test05_enviarDiscrepancia_esRechazadoCon400() throws Exception {
        setElevatedContext(1L, testPropId, "SUPERADMIN");

        Map<String, String> patchBody = Map.of("estado", "DISCREPANCIA");

        mockMvc.perform(patch("/api/v1/conciliaciones/1/estado")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.message", containsString("EN_PROCESO, CONCILIADO o CON_DIFERENCIAS")));
    }

    @Test
    @DisplayName("Test 6: Resumen estadístico mapea CONCILIADO y CON_DIFERENCIAS correctamente")
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    void test06_resumenEstadistico_mapeaEstadosCanonicos() throws Exception {
        setElevatedContext(1L, testPropId, "SUPERADMIN");

        mockMvc.perform(get("/api/v1/conciliaciones/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.CONCILIADOS", notNullValue()))
                .andExpect(jsonPath("$.data.EN_PROCESO", notNullValue()))
                .andExpect(jsonPath("$.data.CON_DIFERENCIAS", notNullValue()));
    }
}
