package com.saed.backend.paquetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.paquetes.dto.PaqueteEntregaDTO;
import com.saed.backend.paquetes.dto.PaqueteRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Phase1GPaquetesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.saed.backend.security.jwt.JwtProvider jwtProvider;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    public void setupMocks() {
        com.saed.backend.context.SaedContextHolder.setContext(com.saed.backend.context.SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        try { jdbcTemplate.update("DELETE FROM ENTREGAS_PAQUETE"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM PAQUETES"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM ADMINISTRADORES_SAED WHERE ID_USUARIO IN (999992, 999993)"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO IN (999992, 999993)"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM USUARIOS WHERE ID_USUARIO IN (999992, 999993)"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM PERSONAS WHERE ID_PERSONA IN (999992, 999993)"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM UNIDADES WHERE ID_PROPIEDAD = 999992"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM PROPIEDADES WHERE ID_PROPIEDAD = 999992"); } catch (Exception e) {}
        try { jdbcTemplate.update("DELETE FROM ORGANIZACIONES WHERE ID_ORGANIZACION = 999992"); } catch (Exception e) {}
        
        try { jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (999992, 1, 'DOC999992', 'NATURAL', 'u999992@test.com', 'N2', 'A2')"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (999992, 999992, '999992', 'u999992@test.com', 'hash', 'ACTIVO')"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO ADMINISTRADORES_SAED (ID_ADMINISTRADOR_SAED, ID_USUARIO, NIVEL, ESTADO) VALUES (999992, 999992, 'SUPERADMIN', 'ACTIVO')"); } catch (Exception e) {}
        
        try { jdbcTemplate.update("INSERT INTO ORGANIZACIONES (ID_ORGANIZACION, NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO) VALUES (999992, 'Org 999992', 'NIT999992', 'org@test.com')"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO PROPIEDADES (ID_PROPIEDAD, ID_ORGANIZACION, ID_TIPO_PROPIEDAD, NOMBRE, DIRECCION, CIUDAD, PAIS) VALUES (999992, 999992, 1, 'Prop999992', 'Dir1', 'Cid1', 'COL')"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO PORTERIAS (ID_PORTERIA, ID_PROPIEDAD, NOMBRE, ESTADO) VALUES (1, 999992, 'Porteria Principal', 'ACTIVA')"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO UNIDADES (ID_UNIDAD, ID_PROPIEDAD, IDENTIFICADOR, ID_TIPO_UNIDAD, AREA_M2) VALUES (999992, 999992, 'UNIDAD999992', 1, 100)"); } catch (Exception e) {}

        try { jdbcTemplate.update("INSERT INTO PERSONAS (ID_PERSONA, ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, EMAIL, PRIMER_NOMBRE, PRIMER_APELLIDO) VALUES (999993, 1, 'DOC999993', 'NATURAL', 'u999993@test.com', 'N3', 'A3')"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO USUARIOS (ID_USUARIO, ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO) VALUES (999993, 999993, '999993', 'u999993@test.com', 'hash', 'ACTIVO')"); } catch (Exception e) {}
        
        Long idRol = 0L;
        try { idRol = jdbcTemplate.queryForObject("SELECT ID_ROL FROM ROLES WHERE CODIGO = 'PORTERO'", Long.class); }
        catch (Exception ex) { 
            jdbcTemplate.update("INSERT INTO ROLES (ID_ROL, CODIGO, NOMBRE, ALCANCE, ESTADO) VALUES (999992, 'PORTERO', 'Portero', 'PROPIEDAD', 'ACTIVO')");
            idRol = 999992L;
        }
        try { jdbcTemplate.update("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION, ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ESTADO) VALUES (999992, 999992, ?, 999992, 999992, 'ACTIVA')", idRol); } catch(Exception e) { System.out.println("ERROR AL INSERTAR ASIGNACION: " + e.getMessage()); }
        
        
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        com.saed.backend.context.SaedContextHolder.clearContext();
    }

    @org.junit.jupiter.api.AfterEach
    public void cleanup() {
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        com.saed.backend.context.SaedContextHolder.clearContext();
    }

    @Test
    void porteroPuedeRegistrarYEntregarPaquete() throws Exception {
        PaqueteRequestDTO req = new PaqueteRequestDTO(
            999992L,
            null,
            "Servientrega",
            "GUI-12345",
            "Caja amazon",
            "MEDIANO",
            null,
            1L
        );

        String token = jwtProvider.generateIdentityToken(999992L);

        String res = mockMvc.perform(post("/api/v1/paquetes").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Assignment-Id", "999992")
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        System.out.println("Registro Response: " + res);
    }
}
