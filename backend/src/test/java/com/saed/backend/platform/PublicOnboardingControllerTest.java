package com.saed.backend.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.platform.dto.OnboardingRegistroRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
public class PublicOnboardingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("GET /api/v1/auth/onboarding/planes debe listar los planes públicos activos")
    void testListarPlanesPublicos() throws Exception {
        mockMvc.perform(get("/api/v1/auth/onboarding/planes")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/auth/onboarding/registro con campos inválidos debe retornar 400 Bad Request")
    void testRegistroInvalido() throws Exception {
        OnboardingRegistroRequestDTO dto = new OnboardingRegistroRequestDTO();
        // Vacío sin campos requeridos

        mockMvc.perform(post("/api/v1/auth/onboarding/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/auth/onboarding/registro para plan comercial genera referencia e intención Wompi")
    void testRegistroComercial() throws Exception {
        // Consultar primer plan comercial existente
        List<Map<String, Object>> planes = jdbcTemplate.queryForList(
                "SELECT ID_PLAN FROM PLANES WHERE ESTADO = 'ACTIVO' AND PRECIO_MENSUAL > 0 ORDER BY PRECIO_MENSUAL ASC",
                Map.of()
        );

        if (planes.isEmpty()) return; // Skip si no hay planes en BD

        Long idPlan = ((Number) planes.get(0).get("ID_PLAN")).longValue();

        String unique = String.valueOf(System.currentTimeMillis() % 1000000);
        OnboardingRegistroRequestDTO dto = new OnboardingRegistroRequestDTO();
        dto.setNombreOrganizacion("Inmobiliaria Prueba " + unique);
        dto.setNit("900" + unique + "-1");
        dto.setCiudad("Medellín");
        dto.setDireccion("Calle 50 # 40-10");
        dto.setEmailContacto("contacto" + unique + "@inmoprueba.com");
        dto.setTelefonoContacto("6044445566");

        dto.setAdminPrimerNombre("Alejandro");
        dto.setAdminPrimerApellido("Gómez");
        dto.setAdminTipoDocumento("CC");
        dto.setAdminNumeroDocumento("10" + unique);
        dto.setAdminEmail("admin" + unique + "@inmoprueba.com");
        dto.setAdminTelefono("3112223344");

        dto.setIdPlan(idPlan);
        dto.setCicloFacturacion("ANUAL");

        mockMvc.perform(post("/api/v1/auth/onboarding/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.requierePago").value(true))
                .andExpect(jsonPath("$.data.referencia").value(startsWith("SAED-MEMBRESIA-")))
                .andExpect(jsonPath("$.data.wompiPublicKey").isNotEmpty())
                .andExpect(jsonPath("$.data.firmaIntegridad").isNotEmpty());
    }
}
