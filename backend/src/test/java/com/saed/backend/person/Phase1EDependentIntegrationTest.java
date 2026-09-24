package com.saed.backend.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.authorization.dto.*;
import com.saed.backend.authorization.service.AssignmentService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.person.dto.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Phase1EDependentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AssignmentService assignmentService;

    @BeforeEach
    public void setupMocks() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");

        // Clean lingering test records
        try { jdbcTemplate.update("DELETE FROM MASCOTAS WHERE NOMBRE IN ('Firulais99', 'MascotaAjena', 'MascotaPropia') OR NUMERO_MICROCHIP LIKE 'CHIP99%'"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM VEHICULOS WHERE PLACA LIKE 'ZZZ99%' OR PLACA LIKE 'TEST%'"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM TUTORES WHERE ID_PERSONA_MENOR = 999995"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM VISITANTES WHERE ID_PERSONA = 10"); } catch (Exception ignored) {}

        // Ensure user 2 (Admin Propiedad) and user 4 (Residente) exist and are ACTIVO in DB
        try {
            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 2 AS id, 1 AS td, '1000000002' AS nd, 'NATURAL' AS tp, 'Admin' AS pn, 'Propiedad' AS pa, 'admin@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)");
            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 2 AS id, 2 AS ip, 'admin' AS nu, 'admin@saed.com' AS em, '$2a$10$Y8yWwG2uR38jM8eIq0f6oOV3/1vM7Z13GkIefw7U9M/P0FqX3q4P3' AS pw, 'ACTIVO' AS st, 0 AS ifal FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st, s.ifal) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO', u.INTENTOS_FALLIDOS = 0");
            Long idRolProp = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_PROPIEDAD'", Long.class);
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 102 AS id, 2 AS u, ? AS r, 1 AS o, 1 AS p, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolProp);
        } catch (Exception e) {
            System.err.println("SETUP USER 2 ERROR: " + e.getMessage());
        }

        try {
            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 4 AS id, 1 AS td, 'CC4000' AS nd, 'NATURAL' AS tp, 'Carlos' AS pn, 'Martinez' AS pa, 'camartinez@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)");
            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 4 AS id, 4 AS ip, 'camartinez' AS nu, 'camartinez@saed.com' AS em, '$2a$10$abcdef' AS pw, 'ACTIVO' AS st FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (s.id, s.ip, s.nu, s.em, s.pw, s.st) WHEN MATCHED THEN UPDATE SET u.ESTADO = 'ACTIVO'");
            Long idRolRes = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE'", Long.class);
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 201 AS id, 4 AS u, ? AS r, 1 AS o, 1 AS p, 1 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO, FECHA_INICIO) VALUES (s.id, s.u, s.r, s.o, s.p, s.un, s.st, TRUNC(SYSDATE)) WHEN MATCHED THEN UPDATE SET ua.ID_USUARIO = s.u, ua.ID_ROL = s.r, ua.ID_ORGANIZACION = s.o, ua.ID_PROPIEDAD = s.p, ua.ID_UNIDAD = s.un, ua.ESTADO = s.st, ua.FECHA_INICIO = TRUNC(SYSDATE), ua.FECHA_FIN = NULL", idRolRes);
            jdbcTemplate.update("MERGE INTO RESIDENTES_UNIDAD ru USING (SELECT 1 AS u, 4 AS p, 'TITULAR' AS tr, 'ACTIVO' AS st FROM DUAL) s ON (ru.ID_UNIDAD = s.u AND ru.ID_PERSONA = s.p) WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO) VALUES (s.u, s.p, s.tr, s.st) WHEN MATCHED THEN UPDATE SET ru.ESTADO = 'ACTIVO', ru.TIPO_RESIDENTE = 'TITULAR'");
        } catch (Exception e) {
            System.err.println("SETUP USER 4 ERROR: " + e.getMessage());
        }

        // Ensure minor persona 999995 exists and is linked to unit 1 in RESIDENTES_UNIDAD
        try {
            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 999995 AS id, 1 AS td, 'DOC999995' AS nd, 'NATURAL' AS tp, 'Menor' AS pn, 'Test' AS pa, 'menor@saed.com' AS em, ADD_MONTHS(TRUNC(SYSDATE), -120) AS fn FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, FECHA_NACIMIENTO) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em, s.fn)");
            jdbcTemplate.update("MERGE INTO USUARIOS u USING (SELECT 999995 AS id, 999995 AS p, 'menor995' AS un, '$2a$10$abcdefghijklmnopqrstuvwxyz123456' AS cl, 'menor@saed.com' AS em, 'ACTIVO' AS st, 0 AS fl FROM DUAL) s ON (u.ID_USUARIO = s.id) WHEN NOT MATCHED THEN INSERT (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, HASH_PASSWORD, EMAIL, ESTADO, INTENTOS_FALLIDOS) VALUES (s.id, s.p, s.un, s.cl, s.em, s.st, s.fl)");
            Long idRolResidente = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'RESIDENTE'", Long.class);
            jdbcTemplate.update("MERGE INTO USUARIO_ASIGNACIONES ua USING (SELECT 999995 AS id, 999995 AS u, 1 AS o, " + idRolResidente + " AS r, 1 AS p, 1 AS un, 'ACTIVA' AS st FROM DUAL) s ON (ua.ID_ASIGNACION = s.id) WHEN NOT MATCHED THEN INSERT (ID_ASIGNACION, ID_USUARIO, ID_ORGANIZACION, ID_ROL, ID_PROPIEDAD, ID_UNIDAD, ESTADO) VALUES (s.id, s.u, s.o, s.r, s.p, s.un, s.st)");
            jdbcTemplate.update("MERGE INTO RESIDENTES_UNIDAD ru USING (SELECT 999995 AS id, 1 AS u, 999995 AS p, 'CONVIVIENTE' AS tr, 'ACTIVO' AS st FROM DUAL) s ON (ru.ID_RESIDENTE_UNIDAD = s.id) WHEN NOT MATCHED THEN INSERT (ID_RESIDENTE_UNIDAD, ID_UNIDAD, ID_PERSONA, TIPO_RESIDENTE, ESTADO) VALUES (s.id, s.u, s.p, s.tr, s.st) WHEN MATCHED THEN UPDATE SET ru.ESTADO = 'ACTIVO', ru.TIPO_RESIDENTE = 'CONVIVIENTE'");
        } catch (Exception e) {
            System.err.println("SETUP 999995 ERROR: " + e.getMessage());
        }

        // Ensure persona 999996 exists for visitante test and clean any existing record
        try {
            jdbcTemplate.update("MERGE INTO PERSONAS p USING (SELECT 999996 AS id, 1 AS td, 'DOC999996' AS nd, 'NATURAL' AS tp, 'Visit' AS pn, 'One' AS pa, 'vis996@saed.com' AS em FROM DUAL) s ON (p.ID_PERSONA = s.id) WHEN NOT MATCHED THEN INSERT (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL) VALUES (s.id, s.td, s.nd, s.tp, s.pn, s.pa, s.em)");
            jdbcTemplate.update("DELETE FROM VISITANTES WHERE ID_PERSONA = 999996");
        } catch (Exception ignored) {}

        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();

        // 1. Mock Admin Propiedad: user 2, assignment 102
        AssignmentResponseDTO propAdmin = new AssignmentResponseDTO();
        propAdmin.setIdAsignacion(102L);
        propAdmin.setRol(new RoleDTO("ADMIN_PROPIEDAD", "PROPIEDAD"));
        propAdmin.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        PropertyDTO propDTO = new PropertyDTO();
        propDTO.setId(1L);
        propDTO.setIdOrganizacion(1L);
        propDTO.setNombre("Edificio Residencial SAED");
        propAdmin.setPropiedad(propDTO);
        Mockito.when(assignmentService.validateAssignment(102L, 2L)).thenReturn(Optional.of(propAdmin));

        // 2. Mock Residente Unit 1: user 4, assignment 201
        AssignmentResponseDTO res1 = new AssignmentResponseDTO();
        res1.setIdAsignacion(201L);
        res1.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        res1.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        res1.setPropiedad(propDTO);
        UnitDTO unitDTO1 = new UnitDTO();
        unitDTO1.setId(1L);
        unitDTO1.setIdentificador("Apt 101");
        res1.setUnidad(unitDTO1);
        Mockito.when(assignmentService.validateAssignment(201L, 4L)).thenReturn(Optional.of(res1));

        // 3. Mock Residente Unit 2: user 5, assignment 202
        AssignmentResponseDTO res2 = new AssignmentResponseDTO();
        res2.setIdAsignacion(202L);
        res2.setRol(new RoleDTO("RESIDENTE", "UNIDAD"));
        res2.setOrganizacion(new OrganizationDTO(1L, "SAED Global S.A.S."));
        res2.setPropiedad(propDTO);
        UnitDTO unitDTO2 = new UnitDTO();
        unitDTO2.setId(2L);
        unitDTO2.setIdentificador("Apt 102");
        res2.setUnidad(unitDTO2);
        Mockito.when(assignmentService.validateAssignment(202L, 5L)).thenReturn(Optional.of(res2));
    }

    @AfterEach
    public void cleanup() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        try { jdbcTemplate.update("DELETE FROM MASCOTAS WHERE NOMBRE IN ('Firulais99', 'MascotaAjena', 'MascotaPropia') OR NUMERO_MICROCHIP LIKE 'CHIP99%'"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM VEHICULOS WHERE PLACA LIKE 'ZZZ99%' OR PLACA LIKE 'TEST%'"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM TUTORES WHERE ID_PERSONA_MENOR = 999995"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM VISITANTES WHERE ID_PERSONA = 999996"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = 999995 OR ID_RESIDENTE_UNIDAD = 999995"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = 999995 OR ID_USUARIO = 999995"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM USUARIOS WHERE ID_USUARIO = 999995"); } catch (Exception ignored) {}
        try { jdbcTemplate.update("DELETE FROM PERSONAS WHERE ID_PERSONA IN (999995, 999996)"); } catch (Exception ignored) {}
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"}, username = "2")
    void createMascota_AsAdminPropiedad_ShouldReturn201() throws Exception {
        MascotaRequestDTO request = new MascotaRequestDTO(
                1L, 4L, "Firulais99", "Perro", "Labrador", "Dorado", "M", 
                LocalDate.of(2020, 1, 1), 25.5, "CHIP991", "N", 
                "http://poliza.com", "http://carnet.com", "http://foto.com", "ACTIVA"
        );

        mockMvc.perform(post("/api/v1/mascotas")
                .header("X-Assignment-Id", "102")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Firulais99"))
                .andExpect(jsonPath("$.especie").value("PERRO"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"}, username = "2")
    void createVehiculo_AsAdminPropiedad_ShouldReturn201() throws Exception {
        VehiculoRequestDTO request = new VehiculoRequestDTO(
                4L, 1L, "ZZZ991", "Automovil", "Toyota", "Corolla", "Plata", "RFID991", "ACTIVO"
        );

        mockMvc.perform(post("/api/v1/vehiculos")
                .header("X-Assignment-Id", "102")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("ZZZ991"))
                .andExpect(jsonPath("$.tipoVehiculo").value("AUTOMOVIL"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"}, username = "2")
    void getMascotasAndVehiculosByUnidad_AsAdminPropiedad_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/v1/unidades/1/mascotas")
                .header("X-Assignment-Id", "102"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/unidades/1/vehiculos")
                .header("X-Assignment-Id", "102"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_RESIDENTE"}, username = "4")
    void residente_SecurityIDOR_CannotManageOtherUnitMascotasOrVehiculos() throws Exception {
        // Resident in unit 1 attempts to register a pet in unit 2 -> 403 Forbidden
        MascotaRequestDTO petOtherUnit = new MascotaRequestDTO(
                2L, 4L, "MascotaAjena", "Gato", "Siames", "Blanco", "H",
                LocalDate.of(2021, 5, 10), 4.2, "CHIP992", "N",
                null, null, null, "ACTIVA"
        );

        mockMvc.perform(post("/api/v1/mascotas")
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(petOtherUnit)))
                .andExpect(status().isForbidden());

        // Resident in unit 1 attempts to register a vehicle in unit 2 -> 403 Forbidden
        VehiculoRequestDTO vehicleOtherUnit = new VehiculoRequestDTO(
                4L, 2L, "ZZZ992", "Automovil", "Mazda", "3", "Rojo", null, "ACTIVO"
        );

        mockMvc.perform(post("/api/v1/vehiculos")
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(vehicleOtherUnit)))
                .andExpect(status().isForbidden());

        // Resident in unit 1 attempts to consult pets/vehicles of unit 2 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/unidades/2/mascotas")
                .header("X-Assignment-Id", "201"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/unidades/2/vehiculos")
                .header("X-Assignment-Id", "201"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_RESIDENTE"}, username = "4")
    void residente_CanManageOwnUnitMascotasAndVehiculos() throws Exception {
        // 1. Create own pet in unit 1
        MascotaRequestDTO ownPet = new MascotaRequestDTO(
                1L, 4L, "MascotaPropia", "Perro", "Beagle", "Tricolor", "M",
                LocalDate.of(2022, 3, 15), 12.0, "CHIP993", "N",
                null, null, null, "ACTIVA"
        );

        mockMvc.perform(post("/api/v1/mascotas")
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ownPet)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("MascotaPropia"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        // 2. Query own unit pets
        mockMvc.perform(get("/api/v1/unidades/1/mascotas")
                .header("X-Assignment-Id", "201"))
                .andExpect(status().isOk());

        // 3. Create own vehicle in unit 1
        VehiculoRequestDTO ownVehicle = new VehiculoRequestDTO(
                4L, 1L, "ZZZ993", "Motocicleta", "Yamaha", "MT-03", "Negro", "RFID993", "ACTIVO"
        );

        mockMvc.perform(post("/api/v1/vehiculos")
                .header("X-Assignment-Id", "201")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ownVehicle)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("ZZZ993"))
                .andExpect(jsonPath("$.tipoVehiculo").value("MOTOCICLETA"));

        // 4. Query own unit vehicles
        mockMvc.perform(get("/api/v1/unidades/1/vehiculos")
                .header("X-Assignment-Id", "201"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"}, username = "2")
    void createTutor_WithValidData_ShouldReturn201() throws Exception {
        TutorRequestDTO request = new TutorRequestDTO(
                999995L, 4L, "Padre", "http://doc.com", "ACTIVO"
        );

        mockMvc.perform(post("/api/v1/tutores")
                .header("X-Assignment-Id", "102")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentesco").value("Padre"));
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"}, username = "2")
    void createVisitante_WithValidData_ShouldReturn201() throws Exception {
        VisitanteRequestDTO request = new VisitanteRequestDTO(
                999996L, "N", "Empresa X", "http://foto.com", "Visita tecnica", "ACTIVO"
        );

        mockMvc.perform(post("/api/v1/visitantes")
                .header("X-Assignment-Id", "102")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresa").value("Empresa X"));
    }
}
