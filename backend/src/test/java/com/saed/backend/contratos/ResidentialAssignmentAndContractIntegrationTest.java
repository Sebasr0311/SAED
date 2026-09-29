package com.saed.backend.contratos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.AssignmentResponseDTO;
import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite de Certificación Integral para el GAP de Asignación Residencial y Formalización de Contratos.
 * 
 * Valida de forma rigurosa y adversarial los 3 pilares arquitectónicos:
 * 1. Relación residencial (vinculación de habitante / titular con unidad en RESIDENTES_UNIDAD y PROPIETARIOS_UNIDAD).
 * 2. Relación contractual (formalización de contrato en CONTRATOS con canon, fechas, cuotas y límites).
 * 3. Documento contractual (generación de snapshot HTML_CONGELADO, compilación PDF, hash SHA-256 y almacenamiento).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ResidentialAssignmentAndContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssignmentService assignmentService;

    // Constantes de prueba aisladas para evitar conflictos
    private static final Long ORG_A_ID = 998801L;
    private static final Long PROP_A_ID = 998801L;
    private static final Long UNIT_A_ID = 998801L;

    private static final Long ORG_B_ID = 998802L;
    private static final Long PROP_B_ID = 998802L;
    private static final Long UNIT_B_ID = 998802L;

    private static final Long USER_ADMIN_PROP_A = 998811L;
    private static final Long ASSIGN_ADMIN_PROP_A = 998811L;

    private static final Long USER_ADMIN_PROP_B = 998812L;
    private static final Long ASSIGN_ADMIN_PROP_B = 998812L;

    private static final Long PERSONA_RESIDENTE = 998821L;
    private static final Long PERSONA_PROPIETARIO = 998822L;

    private static final Long PLANTILLA_ACTIVA_ID = 998831L;
    private static final Long PLANTILLA_BORRADOR_ID = 998832L;

    @BeforeEach
    public void setUp() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}

        // Limpiar datos previos de pruebas
        cleanTestData();

        // 1. Organismos y Propiedades
        try {
            jdbcTemplate.update("""
                MERGE INTO ORGANIZACIONES o 
                USING (SELECT 998801 AS id, 'Org Residencial A' AS n, '900008801-1' AS if, 'adminA@test.com' AS ec FROM DUAL) s 
                ON (o.ID_ORGANIZACION = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.if, s.ec) 
                WHEN MATCHED THEN UPDATE SET o.EMAIL_CONTACTO = s.ec
            """);
            jdbcTemplate.update("""
                MERGE INTO ORGANIZACIONES o 
                USING (SELECT 998802 AS id, 'Org Foranea B' AS n, '900008802-2' AS if, 'adminB@test.com' AS ec FROM DUAL) s 
                ON (o.ID_ORGANIZACION = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.if, s.ec) 
                WHEN MATCHED THEN UPDATE SET o.EMAIL_CONTACTO = s.ec
            """);

            jdbcTemplate.update("""
                MERGE INTO PROPIEDADES pr 
                USING (SELECT 998801 AS id, 998801 AS o, 1 AS t, 'Edificio Residencial Test A' AS n, 'Calle 100' AS dir, 'Bogota' AS ciu, 'Colombia' AS pais, 'MIXTA' AS oc, 'ACTIVA' AS st FROM DUAL) s 
                ON (pr.ID_PROPIEDAD = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.dir, s.ciu, s.pais, s.oc, s.st) 
                WHEN MATCHED THEN UPDATE SET pr.ESTADO = 'ACTIVA'
            """);
            jdbcTemplate.update("""
                MERGE INTO PROPIEDADES pr 
                USING (SELECT 998802 AS id, 998802 AS o, 1 AS t, 'Edificio Foraneo Test B' AS n, 'Calle 200' AS dir, 'Medellin' AS ciu, 'Colombia' AS pais, 'MIXTA' AS oc, 'ACTIVA' AS st FROM DUAL) s 
                ON (pr.ID_PROPIEDAD = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.dir, s.ciu, s.pais, s.oc, s.st) 
                WHEN MATCHED THEN UPDATE SET pr.ESTADO = 'ACTIVA'
            """);

            // 2. Unidades
            jdbcTemplate.update("""
                MERGE INTO UNIDADES u 
                USING (SELECT 998801 AS id, 998801 AS prop, 1 AS t, 'Apto 101' AS num, 'ACTIVA' AS st FROM DUAL) s 
                ON (u.ID_UNIDAD = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) VALUES (s.id, s.prop, s.t, s.num, s.st) 
                WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVA'
            """);
            jdbcTemplate.update("""
                MERGE INTO UNIDADES u 
                USING (SELECT 998802 AS id, 998802 AS prop, 1 AS t, 'Apto 201' AS num, 'ACTIVA' AS st FROM DUAL) s 
                ON (u.ID_UNIDAD = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, ESTADO) VALUES (s.id, s.prop, s.t, s.num, s.st) 
                WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVA'
            """);

            // 3. Personas y Usuarios de Administrador
            jdbcTemplate.update("""
                MERGE INTO PERSONAS p 
                USING (SELECT 998811 AS id, 1 AS td, 'CC998811' AS doc, 'NATURAL' AS tp, 'adminA@test.com' AS em, 'AdminProp' AS n, 'A' AS a FROM DUAL) s 
                ON (p.ID_PERSONA = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.doc, s.tp, s.em, s.n, s.a)
            """);
            jdbcTemplate.update("""
                MERGE INTO USUARIOS u 
                USING (SELECT 998811 AS id, 998811 AS p, 'admin_prop_a_test' AS u, 'adminA@test.com' AS em, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3' AS h, 'ACTIVO' AS st FROM DUAL) s 
                ON (u.ID_USUARIO = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.p, s.u, s.em, s.h, s.st)
            """);

            jdbcTemplate.update("""
                MERGE INTO PERSONAS p 
                USING (SELECT 998812 AS id, 1 AS td, 'CC998812' AS doc, 'NATURAL' AS tp, 'adminB@test.com' AS em, 'AdminProp' AS n, 'B' AS a FROM DUAL) s 
                ON (p.ID_PERSONA = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.doc, s.tp, s.em, s.n, s.a)
            """);
            jdbcTemplate.update("""
                MERGE INTO USUARIOS u 
                USING (SELECT 998812 AS id, 998812 AS p, 'admin_prop_b_test' AS u, 'adminB@test.com' AS em, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3' AS h, 'ACTIVO' AS st FROM DUAL) s 
                ON (u.ID_USUARIO = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.p, s.u, s.em, s.h, s.st)
            """);

            // 4. Personas para pruebas de asignación residencial
            jdbcTemplate.update("""
                MERGE INTO PERSONAS p 
                USING (SELECT 998821 AS id, 1 AS td, 'CC998821' AS doc, 'NATURAL' AS tp, 'residente@test.com' AS em, 'Carlos' AS n, 'Arrendatario' AS a FROM DUAL) s 
                ON (p.ID_PERSONA = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.doc, s.tp, s.em, s.n, s.a)
            """);
            jdbcTemplate.update("""
                MERGE INTO PERSONAS p 
                USING (SELECT 998822 AS id, 1 AS td, 'CC998822' AS doc, 'NATURAL' AS tp, 'propietario@test.com' AS em, 'Gloria' AS n, 'Propietaria' AS a FROM DUAL) s 
                ON (p.ID_PERSONA = s.id) 
                WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.doc, s.tp, s.em, s.n, s.a)
            """);

            // 5. Asignaciones reales en Oracle para que SaedDataSourceProxy / PKG_SAED_SESSION no lance ORA-20080
            Long idRolProp = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD'", Long.class);
            jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION IN (998811, 998812)");
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO) VALUES (998811, 998811, ?, 998801, 998801, 'ACTIVA')", idRolProp);
            jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO) VALUES (998812, 998812, ?, 998802, 998802, 'ACTIVA')", idRolProp);

            // 6. Membresía activa para StorageQuotaService
            Long defaultPlanId = jdbcTemplate.queryForObject("SELECT ID_PLAN FROM PLANES WHERE ROWNUM = 1", Long.class);
            if (defaultPlanId != null) {
                jdbcTemplate.update("""
                    MERGE INTO MEMBRESIAS m 
                    USING (SELECT 998801 AS o, ? AS p, 'ACTIVA' AS st FROM DUAL) s 
                    ON (m.ID_ORGANIZACION = s.o) 
                    WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA) VALUES (s.o, s.p, s.st, TRUNC(SYSDATE), TRUNC(SYSDATE)+365, 'N') 
                    WHEN MATCHED THEN UPDATE SET m.ESTADO = 'ACTIVA'
                """, defaultPlanId);
            }

            // 7. Plantillas contractuales
            jdbcTemplate.update("DELETE FROM PLANTILLAS_CONTRATOS WHERE ID_PLANTILLA IN (998831, 998832)");
            jdbcTemplate.update("""
                INSERT INTO PLANTILLAS_CONTRATOS (ID_PLANTILLA, ID_ORGANIZACION, CODIGO, NOMBRE, TIPO_CONTRATO, CONTENIDO_HTML, ESTADO, VERSION, CREADO_POR) 
                VALUES (998831, 998801, 'TPL_TEST_ACTIVA', 'Plantilla Residencial Activa', 'INICIAL', 
                        '<html><head><style>body{font-family:sans-serif;}</style></head><body><h1>CONTRATO DE ARRENDAMIENTO</h1><p>Arrendatario: ${residente.nombreCompleto}</p><p>Documento: ${residente.numeroDocumento}</p><p>Inmueble: ${unidad.identificador}</p><p>Canon: ${contrato.canon_mensual}</p></body></html>', 
                        'ACTIVA', 1, 998811)
            """);
            jdbcTemplate.update("""
                INSERT INTO PLANTILLAS_CONTRATOS (ID_PLANTILLA, ID_ORGANIZACION, CODIGO, NOMBRE, TIPO_CONTRATO, CONTENIDO_HTML, ESTADO, VERSION, CREADO_POR) 
                VALUES (998832, 998801, 'TPL_TEST_BORRADOR', 'Plantilla Residencial Borrador', 'INICIAL', 
                        '<html><body><h1>BORRADOR NO VALIDO</h1></body></html>', 
                        'BORRADOR', 1, 998811)
            """);
        } catch (Exception e) {
            System.err.println("ERROR SETUP SEEDING: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        // Mockito AssignmentService
        OrganizationDTO orgA = new OrganizationDTO(ORG_A_ID, "Org Residencial A");
        PropertyDTO propA = new PropertyDTO(PROP_A_ID, "Edificio Residencial Test A");

        OrganizationDTO orgB = new OrganizationDTO(ORG_B_ID, "Org Foranea B");
        PropertyDTO propB = new PropertyDTO(PROP_B_ID, "Edificio Foraneo Test B");

        AssignmentResponseDTO assignA = new AssignmentResponseDTO();
        assignA.setIdAsignacion(ASSIGN_ADMIN_PROP_A);
        assignA.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        assignA.setOrganizacion(orgA);
        assignA.setPropiedad(propA);
        Mockito.when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_A, USER_ADMIN_PROP_A)).thenReturn(Optional.of(assignA));

        AssignmentResponseDTO assignB = new AssignmentResponseDTO();
        assignB.setIdAsignacion(ASSIGN_ADMIN_PROP_B);
        assignB.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        assignB.setOrganizacion(orgB);
        assignB.setPropiedad(propB);
        Mockito.when(assignmentService.validateAssignment(ASSIGN_ADMIN_PROP_B, USER_ADMIN_PROP_B)).thenReturn(Optional.of(assignB));

        try {
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    @AfterEach
    public void tearDown() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            cleanTestData();
            jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.clearContext();
    }

    private void cleanTestData() {
        try { jdbcTemplate.update("DELETE FROM PAGO_DETALLE WHERE ID_CUOTA IN (SELECT ID_CUOTA FROM CUOTAS WHERE ID_UNIDAD IN (998801, 998802))"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM CUOTAS WHERE ID_UNIDAD IN (998801, 998802)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM CONTRATO_RESIDENTE WHERE ID_CONTRATO IN (SELECT ID_CONTRATO FROM CONTRATOS WHERE ID_UNIDAD IN (998801, 998802))"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM CONTRATOS WHERE ID_UNIDAD IN (998801, 998802)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD IN (998801, 998802)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD IN (998801, 998802)"); } catch (Exception ignored) {}
    }

    // =========================================================================
    // ESCENARIO 1: ASIGNACIÓN DE ARRENDATARIO SIN CONTRATO (crearContrato = false)
    // =========================================================================
    @Test
    @DisplayName("F3.1: Asignar ARRENDATARIO sin contrato persiste residencia activa y CERO contratos")
    public void test01_AsignarArrendatarioSinContrato_Exitoso() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_A);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_A_ID);
        payload.put("tipoRelacion", "ARRENDATARIO");
        payload.put("crearContrato", false);

        mockMvc.perform(post("/api/v1/residentes/" + PERSONA_RESIDENTE + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        // Verificación en Base de Datos
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        Integer resCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO' AND TIPO_RESIDENTE = 'ARRENDATARIO'",
                Integer.class, UNIT_A_ID, PERSONA_RESIDENTE);
        assertEquals(1, resCount, "El habitante debe estar activo en RESIDENTES_UNIDAD con tipo ARRENDATARIO");

        Integer conCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CONTRATOS WHERE ID_UNIDAD = ?",
                Integer.class, UNIT_A_ID);
        assertEquals(0, conCount, "No debe haberse creado ningún contrato en CONTRATOS cuando crearContrato es false");

        Integer propCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO'",
                Integer.class, UNIT_A_ID, PERSONA_RESIDENTE);
        assertEquals(0, propCount, "El arrendatario no debe ser titular de dominio en PROPIETARIOS_UNIDAD");
    }

    // =========================================================================
    // ESCENARIO 2: ASIGNACIÓN DE PROPIETARIO RESIDENTE SIN CONTRATO
    // =========================================================================
    @Test
    @DisplayName("F3.2: Asignar PROPIETARIO_RESIDENTE sin contrato crea dominio y residencia sin contratos")
    public void test02_AsignarPropietarioSinContrato_Exitoso() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_A);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_A_ID);
        payload.put("tipoRelacion", "PROPIETARIO_RESIDENTE");
        payload.put("crearContrato", false);

        mockMvc.perform(post("/api/v1/residentes/" + PERSONA_PROPIETARIO + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        Integer propCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO' AND ES_PRINCIPAL = 'S'",
                Integer.class, UNIT_A_ID, PERSONA_PROPIETARIO);
        assertEquals(1, propCount, "El propietario debe estar activo en PROPIETARIOS_UNIDAD como titular principal");

        Integer resCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO' AND TIPO_RESIDENTE = 'PROPIETARIO'",
                Integer.class, UNIT_A_ID, PERSONA_PROPIETARIO);
        assertEquals(1, resCount, "El propietario residente debe figurar activo en RESIDENTES_UNIDAD con tipo PROPIETARIO");

        Integer conCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CONTRATOS WHERE ID_UNIDAD = ?",
                Integer.class, UNIT_A_ID);
        assertEquals(0, conCount, "No debe existir contrato para el propietario");
    }

    // =========================================================================
    // ESCENARIO 3: ASIGNACIÓN CON FORMALIZACIÓN Y GENERACIÓN DOCUMENTAL COMPLETA
    // =========================================================================
    @Test
    @DisplayName("F3.3: Asignar con crearContrato=true formaliza contrato, congela HTML y genera PDF SHA-256")
    public void test03_AsignarConFormalizacionContrato_GeneraDocumento() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_A);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_A_ID);
        payload.put("tipoRelacion", "ARRENDATARIO");
        payload.put("crearContrato", true);
        payload.put("canonMensual", new BigDecimal("1750000.00"));
        payload.put("fechaInicio", "2026-10-01");
        payload.put("fechaFin", "2027-09-30");
        payload.put("idPlantilla", PLANTILLA_ACTIVA_ID);

        mockMvc.perform(post("/api/v1/residentes/" + PERSONA_RESIDENTE + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // 1. Residencia física activa
        Integer resCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM RESIDENTES_UNIDAD WHERE ID_UNIDAD = ? AND ID_PERSONA = ? AND ESTADO = 'ACTIVO'",
                Integer.class, UNIT_A_ID, PERSONA_RESIDENTE);
        assertEquals(1, resCount);

        // 2. Contrato creado
        List<Map<String, Object>> contratos = jdbcTemplate.queryForList(
                "SELECT ID_CONTRATO, ESTADO, CANON_MENSUAL, HTML_CONGELADO, DOCUMENTO_HASH, DOCUMENTO_URL FROM CONTRATOS WHERE ID_UNIDAD = ? AND ID_ARRENDATARIO_PRINCIPAL = ?",
                UNIT_A_ID, PERSONA_RESIDENTE);
        assertEquals(1, contratos.size(), "Debe existir exactamente 1 contrato en CONTRATOS");

        Map<String, Object> c = contratos.get(0);
        assertNotNull(c.get("HTML_CONGELADO"), "El snapshot HTML_CONGELADO no debe ser nulo");
        String html = c.get("HTML_CONGELADO").toString();
        assertTrue(html.contains("Carlos Arrendatario"), "HTML debe contener el nombre sustituido del residente");
        assertTrue(html.contains("Apto 101"), "HTML debe contener el identificador sustituido de la unidad");

        assertNotNull(c.get("DOCUMENTO_HASH"), "El hash SHA-256 no debe ser nulo");
        assertEquals(64, c.get("DOCUMENTO_HASH").toString().length(), "El hash SHA-256 debe ser de 64 caracteres hexadecimales");
        assertNotNull(c.get("DOCUMENTO_URL"), "La URL/Ruta física del documento PDF no debe ser nula");
    }

    // =========================================================================
    // ESCENARIO 4: VALIDACIÓN DE CANON MENSUAL INVÁLIDO
    // =========================================================================
    @Test
    @DisplayName("F3.4: crearContrato=true con canon negativo o cero es rechazado con 400 Bad Request")
    public void test04_AsignarConContrato_CanonInvalido_Rechaza() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_A);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_A_ID);
        payload.put("tipoRelacion", "ARRENDATARIO");
        payload.put("crearContrato", true);
        payload.put("canonMensual", new BigDecimal("-500.00"));
        payload.put("fechaInicio", "2026-10-01");
        payload.put("idPlantilla", PLANTILLA_ACTIVA_ID);

        mockMvc.perform(post("/api/v1/residentes/" + PERSONA_RESIDENTE + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());

        // Asegurar que no hubo persistencia parcial (Rollback garantizado)
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        Integer conCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CONTRATOS WHERE ID_UNIDAD = ?", Integer.class, UNIT_A_ID);
        assertEquals(0, conCount);
    }

    // =========================================================================
    // ESCENARIO 5: VALIDACIÓN DE FECHAS INVERTIDAS
    // =========================================================================
    @Test
    @DisplayName("F3.5: crearContrato=true con fechaFin anterior a fechaInicio es rechazado con 400 Bad Request")
    public void test05_AsignarConContrato_FechasInvertidas_Rechaza() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_A);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_A_ID);
        payload.put("tipoRelacion", "ARRENDATARIO");
        payload.put("crearContrato", true);
        payload.put("canonMensual", new BigDecimal("1500000.00"));
        payload.put("fechaInicio", "2026-10-15");
        payload.put("fechaFin", "2026-10-01"); // Fecha fin anterior
        payload.put("idPlantilla", PLANTILLA_ACTIVA_ID);

        mockMvc.perform(post("/api/v1/residentes/" + PERSONA_RESIDENTE + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // ESCENARIO 6: VALIDACIÓN DE PLANTILLA INACTIVA / BORRADOR
    // =========================================================================
    @Test
    @DisplayName("F3.6: crearContrato=true con plantilla en estado BORRADOR es rechazado con 400 Bad Request")
    public void test06_AsignarConContrato_PlantillaBorrador_Rechaza() throws Exception {
        String token = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_A);

        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_A_ID);
        payload.put("tipoRelacion", "ARRENDATARIO");
        payload.put("crearContrato", true);
        payload.put("canonMensual", new BigDecimal("1500000.00"));
        payload.put("fechaInicio", "2026-10-01");
        payload.put("idPlantilla", PLANTILLA_BORRADOR_ID); // Estado BORRADOR

        mockMvc.perform(post("/api/v1/residentes/" + PERSONA_RESIDENTE + "/asignar-apartamento")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // ESCENARIO 7: CONTROL DE PERÍMETRO Y SEGURIDAD MULTI-TENANT ANTI-IDOR
    // =========================================================================
    @Test
    @DisplayName("F3.7: Intento de asignación cruzada a una unidad de otra propiedad es bloqueado con 403 Forbidden")
    public void test07_SeguridadPerimetroPropiedad_CrossProperty_Forbidden() throws Exception {
        // Token del Administrador de la Propiedad A
        String tokenAdminA = jwtProvider.generateIdentityToken(USER_ADMIN_PROP_A);

        // Intenta asignar habitante a UNIT_B_ID (Propiedad B)
        Map<String, Object> payload = new HashMap<>();
        payload.put("idApartamento", UNIT_B_ID);
        payload.put("tipoRelacion", "ARRENDATARIO");
        payload.put("crearContrato", false);

        mockMvc.perform(post("/api/v1/residentes/" + PERSONA_RESIDENTE + "/asignar-apartamento")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Assignment-Id", String.valueOf(ASSIGN_ADMIN_PROP_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }
}
