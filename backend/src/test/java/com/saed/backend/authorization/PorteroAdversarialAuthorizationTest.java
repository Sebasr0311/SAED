package com.saed.backend.authorization;

import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentManagementService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PorteroAdversarialAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AssignmentManagementService assignmentManagementService;

    @MockBean
    private AssignmentService assignmentService;

    private final String porteroAssignment1 = "103";
    private final String porteroAssignment2 = "106";
    private final String foreignAssignment = "888888";
    private final Long porteroUserId = 3L;

    private Long idRolSuperAdmin;
    private Long idRolOrgAdmin;
    private Long idRolPropAdmin;
    private Long idRolPortero;

    @BeforeEach
    public void setupMocks() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // 1. Roles
        idRolSuperAdmin = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'SUPERADMIN'", Long.class);
        idRolOrgAdmin = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'", Long.class);
        idRolPropAdmin = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD'", Long.class);
        idRolPortero = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'PORTERO'", Long.class);

        // 2. Extra Property 999994 for Org 1
        try { jdbcTemplate.update("MERGE INTO PROPIEDADES pr USING (SELECT 999994 AS id, 1 AS o, 1 AS t, 'Prop Test 999994' AS n, 'Calle 101 # 10-20' AS dir, 'Bogota' AS ciu, 'Colombia' AS pai, 'MIXTA' AS ocu, 'ACTIVA' AS st FROM DUAL) s ON (pr.ID_PROPIEDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS, TIPO_OCUPACION_PREDOMINANTE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.dir, s.ciu, s.pai, s.ocu, s.st)"); } catch (Exception ignored) {}

        // 3. Tenant B (888888)
        try { jdbcTemplate.update("MERGE INTO ORGANIZACIONES o USING (SELECT 888888 AS id, 'Org Test 888888' AS n, 'NIT888888' AS if, 'org888888@test.com' AS ec FROM DUAL) s ON (o.ID_ORGANIZACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (s.id, s.n, s.if, s.ec) WHEN MATCHED THEN UPDATE SET o.EMAIL_CONTACTO = s.ec"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO PROPIEDADES pr USING (SELECT 888888 AS id, 888888 AS o, 1 AS t, 'Prop Test 888888' AS n, 'ACTIVA' AS st FROM DUAL) s ON (pr.ID_PROPIEDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, ESTADO) VALUES (s.id, s.o, s.t, s.n, s.st)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 888888 AS id, 1 AS td, 'DOC888888' AS nd, 'NATURAL' AS tp, 'admin888888@test.com' AS em, 'AdminB' AS pn, 'PropB' AS pa FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.nd, s.tp, s.em, s.pn, s.pa)"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 888888 AS id, 888888 AS ip, 'admin888' AS nu, 'admin888888@test.com' AS em, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st)"); } catch (Exception ignored) {}

        // 4. Assignments
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 103 AS id, 3 AS u, ? AS r, 1 AS o, 1 AS p, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolPortero); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 106 AS id, 3 AS u, ? AS r, 1 AS o, 999994 AS p, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolPortero); } catch (Exception ignored) {}
        try { jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 888888 AS id, 888888 AS u, ? AS r, 888888 AS o, 888888 AS p, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolPortero); } catch (Exception ignored) {}

        // 5. Seed QR de prueba expirado y activo
        try {
            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 2 AS id, 1 AS td, 'DOC222' AS nd, 'NATURAL' AS tp, 'Visitante' AS pn, 'Test' AS pa FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa)");
            jdbcTemplate.update("MERGE INTO VISITANTES vis USING (SELECT 2 AS p FROM DUAL) s ON (vis.ID_PERSONA = s.p) WHEN NOT MATCHED THEN INSERT (ID_PERSONA) VALUES (s.p)");
            Long visitanteId = jdbcTemplate.queryForObject("SELECT MIN(ID_VISITANTE) FROM VISITANTES WHERE ID_PERSONA = 2", Long.class);
            jdbcTemplate.update("MERGE INTO VISITAS v USING (SELECT 1 AS u, ? AS vis, 'CODIGO_QR' AS mi, 'Reunion' AS mo, CURRENT_TIMESTAMP AS fp, 'PROGRAMADA' AS st FROM DUAL) s ON (v.ID_UNIDAD = s.u AND v.ID_VISITANTE = s.vis) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_VISITANTE, METODO_INGRESO, MOTIVO, FECHA_PROGRAMADA, ESTADO) VALUES (s.u, s.vis, s.mi, s.mo, s.fp, s.st) WHEN MATCHED THEN UPDATE SET v.ESTADO = s.st", visitanteId);
            Long visitaId = jdbcTemplate.queryForObject("SELECT MIN(ID_VISITA) FROM VISITAS WHERE ID_UNIDAD = 1", Long.class);

            jdbcTemplate.update("MERGE INTO QR_ACCESOS qr USING (SELECT ? AS v, 'TOKEN_VALIDO_TEST' AS t, CURRENT_TIMESTAMP + INTERVAL '1' DAY AS exp, 5 AS up, 0 AS uc, 'ACTIVO' AS st FROM DUAL) s ON (qr.TOKEN_QR = s.t) WHEN NOT MATCHED THEN INSERT (ID_VISITA, TOKEN_QR, FECHA_EXPIRACION, USOS_PERMITIDOS, USOS_CONSUMIDOS, ESTADO) VALUES (s.v, s.t, s.exp, s.up, s.uc, s.st) WHEN MATCHED THEN UPDATE SET qr.ID_VISITA = s.v, qr.FECHA_EXPIRACION = s.exp, qr.ESTADO = s.st, qr.USOS_CONSUMIDOS = 0", visitaId);
            jdbcTemplate.update("MERGE INTO QR_ACCESOS qr USING (SELECT ? AS v, 'TOKEN_EXPIRADO_TEST' AS t, CURRENT_TIMESTAMP - INTERVAL '1' DAY AS exp, 1 AS up, 0 AS uc, 'EXPIRADO' AS st FROM DUAL) s ON (qr.TOKEN_QR = s.t) WHEN NOT MATCHED THEN INSERT (ID_VISITA, TOKEN_QR, FECHA_EXPIRACION, USOS_PERMITIDOS, USOS_CONSUMIDOS, ESTADO) VALUES (s.v, s.t, s.exp, s.up, s.uc, s.st) WHEN MATCHED THEN UPDATE SET qr.ID_VISITA = s.v, qr.FECHA_EXPIRACION = s.exp, qr.ESTADO = s.st", visitaId);
        } catch (Exception e) {
            e.printStackTrace();
        }

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();

        // 6. Mockito expectations for assignments
        AssignmentResponseDTO portero1 = new AssignmentResponseDTO();
        portero1.setIdAsignacion(103L);
        portero1.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        portero1.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propDTO1 = new PropertyDTO();
        propDTO1.setId(1L);
        propDTO1.setIdOrganizacion(1L);
        propDTO1.setNombre("Edificio Residencial SAED");
        portero1.setPropiedad(propDTO1);
        Mockito.when(assignmentService.validateAssignment(103L, 3L)).thenReturn(Optional.of(portero1));

        AssignmentResponseDTO portero2 = new AssignmentResponseDTO();
        portero2.setIdAsignacion(106L);
        portero2.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        portero2.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propDTO2 = new PropertyDTO();
        propDTO2.setId(999994L);
        propDTO2.setIdOrganizacion(1L);
        propDTO2.setNombre("Prop Test 999994");
        portero2.setPropiedad(propDTO2);
        Mockito.when(assignmentService.validateAssignment(106L, 3L)).thenReturn(Optional.of(portero2));

        AssignmentResponseDTO foreignPortero = new AssignmentResponseDTO();
        foreignPortero.setIdAsignacion(888888L);
        foreignPortero.setRol(new RoleDTO("PORTERO", "PROPIEDAD"));
        foreignPortero.setOrganizacion(new OrganizationDTO(888888L, "Org Test 888888"));
        PropertyDTO propDTOB = new PropertyDTO();
        propDTOB.setId(888888L);
        propDTOB.setIdOrganizacion(888888L);
        propDTOB.setNombre("Prop Test 888888");
        foreignPortero.setPropiedad(propDTOB);
        Mockito.when(assignmentService.validateAssignment(888888L, 888888L)).thenReturn(Optional.of(foreignPortero));
    }

    @AfterEach
    public void cleanup() {
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    // ==========================================
    // 1. PORTERO PERMITIDO EN OPERACIÓN DIARIA DE PORTERÍA (200 OK / 201 CREATED)
    // ==========================================

    @Test
    @DisplayName("PORTERO puede consultar las unidades de la propiedad para identificar apartamentos")
    public void portero_canReadUnits() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/units")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO puede consultar una unidad por ID")
    public void portero_canReadUnitById() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/units/1")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO puede consultar los residentes de una unidad para anunciar visitantes")
    public void portero_canReadResidentsOfUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/units/1/residents")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO puede consultar los propietarios de una unidad")
    public void portero_canReadOwnersOfUnit() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/units/1/owners")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO puede consultar el directorio de personas")
    public void portero_canReadPersonas() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/personas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO puede consultar resumen de visitas")
    public void portero_canReadVisitasResumen() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/porteria/visitas-resumen")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO puede consultar historial de visitas por rango de fechas")
    public void portero_canReadVisitasHistorial() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/porteria/visitas/historial")
                .param("fechaInicio", "2026-01-01")
                .param("fechaFin", "2026-12-31")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO puede consultar registros de acceso de la propiedad")
    public void portero_canReadRegistrosByPropiedad() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/porteria/propiedades/1/registros")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO puede consultar correspondencia (paquetes)")
    public void portero_canReadPaquetes() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/paquetes")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO puede consultar parqueaderos y bahías")
    public void portero_canReadParqueaderos() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/parqueaderos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO puede consultar asignaciones de parqueaderos")
    public void portero_canReadParqueaderosAsignaciones() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/parqueaderos/asignaciones")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());
    }

    // ==========================================
    // 2. QR SECURITY & VALIDATION ADVERSARIAL
    // ==========================================

    @Test
    @DisplayName("PORTERO valida QR válido: retorna valido = true")
    public void portero_validarQr_valido() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = "{\"token\":\"TOKEN_VALIDO_TEST\"}";
        mockMvc.perform(post("/api/v1/porteria/qr/validar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true));
    }

    @Test
    @DisplayName("PORTERO valida QR expirado: retorna valido = false")
    public void portero_validarQr_expirado() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = "{\"token\":\"TOKEN_EXPIRADO_TEST\"}";
        mockMvc.perform(post("/api/v1/porteria/qr/validar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false));
    }

    @Test
    @DisplayName("PORTERO valida QR inexistente / manipulado: retorna valido = false")
    public void portero_validarQr_inexistente() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = "{\"token\":\"TOKEN_HACKER_123\"}";
        mockMvc.perform(post("/api/v1/porteria/qr/validar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false));
    }

    // ==========================================
    // 3. PORTERO DENEGADO EN GESTIÓN DE UNIDADES Y RESIDENTES (403 FORBIDDEN)
    // ==========================================

    @Test
    @DisplayName("PORTERO no puede crear unidades (403 Forbidden)")
    public void portero_cannotCreateUnits() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = """
            {
                "idPropiedad": 1,
                "idTipoUnidad": 1,
                "identificador": "Apto 999",
                "areaM2": 80.0
            }
        """;
        mockMvc.perform(post("/api/v1/units")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede actualizar unidades (403 Forbidden)")
    public void portero_cannotUpdateUnits() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = """
            {
                "idPropiedad": 1,
                "idTipoUnidad": 1,
                "identificador": "Apto Modificado",
                "areaM2": 80.0
            }
        """;
        mockMvc.perform(put("/api/v1/units/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede agregar propietarios a una unidad (403 Forbidden)")
    public void portero_cannotAddOwners() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = """
            {
                "personaId": 1,
                "porcentajePropiedad": 100,
                "esPrincipal": "S"
            }
        """;
        mockMvc.perform(post("/api/v1/units/1/owners")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede agregar residentes a una unidad (403 Forbidden)")
    public void portero_cannotAddResidents() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = """
            {
                "personaId": 1,
                "tipoResidente": "ARRENDATARIO"
            }
        """;
        mockMvc.perform(post("/api/v1/units/1/residents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede crear personas en el directorio general (403 Forbidden)")
    public void portero_cannotCreatePersonas() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = """
            {
                "tipoDocumentoId": 1,
                "numeroDocumento": "123456789",
                "tipoPersona": "NATURAL",
                "primerNombre": "Hacker",
                "primerApellido": "Test",
                "email": "hacker@test.com"
            }
        """;
        mockMvc.perform(post("/api/v1/personas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede mutar ni eliminar personas (403 Forbidden)")
    public void portero_cannotDeletePersonas() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(delete("/api/v1/personas/1")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 4. PORTERO DENEGADO EN MÓDULOS FINANCIEROS Y ADMINISTRATIVOS (403 FORBIDDEN)
    // ==========================================

    @Test
    @DisplayName("PORTERO no puede consultar cuotas financieras (403 Forbidden)")
    public void portero_cannotAccessCuotas() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/cuotas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede consultar cartera (403 Forbidden)")
    public void portero_cannotAccessCartera() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/cartera")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede consultar resumen de cartera (403 Forbidden)")
    public void portero_cannotAccessCarteraResumen() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/cartera/resumen")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede registrar pagos (403 Forbidden)")
    public void portero_cannotRegisterPagos() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = """
            {
                "idCuota": 1,
                "valorPagado": 100000,
                "metodoPago": "EFECTIVO"
            }
        """;
        mockMvc.perform(post("/api/v1/pagos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede consultar ni administrar multas (403 Forbidden)")
    public void portero_cannotAccessMultas() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/multas/todas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede consultar administración de quejas (403 Forbidden)")
    public void portero_cannotAccessQuejasAdmin() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/quejas/todas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede consultar administración de PQRS (403 Forbidden)")
    public void portero_cannotAccessPqrsAdmin() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/pqrs/todos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede consultar pólizas de seguros (403 Forbidden)")
    public void portero_cannotAccessSeguros() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/seguros/polizas")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede consultar contratos (403 Forbidden)")
    public void portero_cannotAccessContratos() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/contratos")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 5. PORTERO DENEGADO EN PLATAFORMA Y ORGANIZACIÓN (403 FORBIDDEN)
    // ==========================================

    @Test
    @DisplayName("PORTERO no puede acceder al dashboard de plataforma (403 Forbidden)")
    public void portero_cannotAccessPlatformDashboard() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/platform/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede acceder a planes de plataforma (403 Forbidden)")
    public void portero_cannotAccessPlatformPlans() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/platform/plans")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede acceder a administradores de plataforma (403 Forbidden)")
    public void portero_cannotAccessPlatformAdmins() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/platform/admins")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede acceder a la consola organizacional (403 Forbidden)")
    public void portero_cannotAccessOrgProfile() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/org/profile")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede acceder al dashboard organizacional (403 Forbidden)")
    public void portero_cannotAccessOrgDashboard() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        mockMvc.perform(get("/api/v1/org/dashboard")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede crear ni administrar propiedades (403 Forbidden)")
    public void portero_cannotCreateProperties() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = """
            {
                "nombre": "Prop Ilegal Portero",
                "idTipoPropiedad": 1,
                "direccion": "Calle 1",
                "ciudad": "Bogota",
                "pais": "Colombia"
            }
        """;
        mockMvc.perform(post("/api/v1/properties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede crear asignaciones de roles (403 Forbidden)")
    public void portero_cannotCreateAssignments() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = """
            {
                "idUsuario": 3,
                "idRol": 1,
                "idOrganizacion": 1
            }
        """;
        mockMvc.perform(post("/api/v1/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PORTERO no puede mutar estado de asignaciones (403 Forbidden)")
    public void portero_cannotUpdateAssignmentStatus() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);
        String body = "{\"estado\":\"INACTIVA\"}";
        mockMvc.perform(patch("/api/v1/assignments/103/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 6. ANTI-PRIVILEGE ESCALATION & IDENTITY SPOOFING
    // ==========================================

    @Test
    @DisplayName("PORTERO no puede crear asignaciones de rol SUPERADMIN (Anti-escalamiento service-level)")
    public void portero_cannotAssignSuperAdminRole() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(porteroUserId)
                    .organizationId(1L)
                    .propertyId(1L)
                    .roleCode("PORTERO")
                    .roleScope("PROPIEDAD")
                    .build());

            AssignmentRequestDTO req = new AssignmentRequestDTO();
            req.setIdUsuario(porteroUserId);
            req.setIdRol(idRolSuperAdmin);
            req.setIdOrganizacion(1L);

            assertThrows(AccessDeniedException.class, () -> {
                assignmentManagementService.create(req);
            });
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("PORTERO no puede crear asignaciones de rol ADMIN_ORGANIZACION (Anti-escalamiento service-level)")
    public void portero_cannotAssignAdminOrgRole() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(porteroUserId)
                    .organizationId(1L)
                    .propertyId(1L)
                    .roleCode("PORTERO")
                    .roleScope("PROPIEDAD")
                    .build());

            AssignmentRequestDTO req = new AssignmentRequestDTO();
            req.setIdUsuario(porteroUserId);
            req.setIdRol(idRolOrgAdmin);
            req.setIdOrganizacion(1L);

            assertThrows(AccessDeniedException.class, () -> {
                assignmentManagementService.create(req);
            });
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("PORTERO no puede crear asignaciones de rol ADMIN_PROPIEDAD (Anti-escalamiento service-level)")
    public void portero_cannotAssignAdminPropRole() {
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(porteroUserId)
                    .organizationId(1L)
                    .propertyId(1L)
                    .roleCode("PORTERO")
                    .roleScope("PROPIEDAD")
                    .build());

            AssignmentRequestDTO req = new AssignmentRequestDTO();
            req.setIdUsuario(porteroUserId);
            req.setIdRol(idRolPropAdmin);
            req.setIdOrganizacion(1L);

            assertThrows(AccessDeniedException.class, () -> {
                assignmentManagementService.create(req);
            });
        } finally {
            SaedContextHolder.clearContext();
        }
    }

    // ==========================================
    // 7. MULTI-PROPERTY CONTEXT SWITCHING & CROSS-TENANT ISOLATION
    // ==========================================

    @Test
    @DisplayName("PORTERO con múltiples propiedades: cambio de X-Assignment-Id conmuta su contexto")
    public void portero_multiPropertySwitching() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId);

        // Access with Assignment 1 (Property 1)
        mockMvc.perform(get("/api/v1/units")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment1))
                .andExpect(status().isOk());

        // Access with Assignment 2 (Property 999994)
        mockMvc.perform(get("/api/v1/units")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", porteroAssignment2))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PORTERO no puede usar un assignment que pertenezca a otro usuario")
    public void portero_cannotUseForeignAssignment() throws Exception {
        String token = jwtProvider.generateIdentityToken(porteroUserId); // User 3
        mockMvc.perform(get("/api/v1/units")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", foreignAssignment))
                .andExpect(status().isForbidden());
    }
}
