package com.saed.backend.authorization;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("dev")
@AutoConfigureMockMvc
public class Phase1BAdversarialTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @AfterEach
    public void cleanup() {
        SaedContextHolder.clearContext();
    }

    @Test
    public void testMissingAssignmentIdHeader_Returns401() throws Exception {
        // Identity-only token without assignment header -> 401 on protected endpoints
        String token = jwtProvider.generateIdentityToken(1L);

        mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
                
        mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid X-Assignment-Id format"));
    }

    @Test
    public void testForeignAssignmentSpoofing_Returns403() throws Exception {
        // User 1 tries to access Assignment 9999
        String token = jwtProvider.generateIdentityToken(1L);

        mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + token)
                .header("X-Assignment-Id", "9999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or inactive assignment"));
    }
}
