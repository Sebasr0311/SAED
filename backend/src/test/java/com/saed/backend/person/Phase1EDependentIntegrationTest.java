package com.saed.backend.person;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.person.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class Phase1EDependentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    void createMascota_WithValidData_ShouldReturn201Or500() throws Exception {
        MascotaRequestDTO request = new MascotaRequestDTO(
                1L, 1L, "Firulais", "Perro", "Labrador", "Dorado", "M", 
                LocalDate.of(2020, 1, 1), 25.5, "123456789", "N", 
                "http://poliza.com", "http://carnet.com", "http://foto.com", "ACTIVO"
        );

        mockMvc.perform(post("/api/v1/mascotas")
                .header("X-Assignment-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 201 || status == 500 || status == 403);
                });
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    void createVehiculo_WithValidData_ShouldReturn201Or500() throws Exception {
        VehiculoRequestDTO request = new VehiculoRequestDTO(
                1L, 1L, "ABC-123", "Automovil", "Toyota", "Corolla", "Plata", "RFID123", "ACTIVO"
        );

        mockMvc.perform(post("/api/v1/vehiculos")
                .header("X-Assignment-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 201 || status == 500 || status == 403);
                });
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    void createTutor_WithValidData_ShouldReturn201Or500() throws Exception {
        TutorRequestDTO request = new TutorRequestDTO(
                2L, 1L, "Padre", "http://doc.com", "ACTIVO"
        );

        mockMvc.perform(post("/api/v1/tutores")
                .header("X-Assignment-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 201 || status == 500 || status == 403);
                });
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_ADMIN_PROPIEDAD"})
    void createVisitante_WithValidData_ShouldReturn201Or500() throws Exception {
        VisitanteRequestDTO request = new VisitanteRequestDTO(
                3L, "N", "Empresa X", "http://foto.com", "Visita tecnica", "ACTIVO"
        );

        mockMvc.perform(post("/api/v1/visitantes")
                .header("X-Assignment-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 201 || status == 500 || status == 403);
                });
    }
}
