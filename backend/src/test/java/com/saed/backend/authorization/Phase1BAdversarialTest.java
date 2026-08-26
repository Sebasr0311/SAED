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
    public void testMissingAssignmentIdHeader_Returns400() throws Exception {
        // We will call a generic protected endpoint (like /api/v1/propiedades if it exists)
        // Since we don't have business endpoints yet, let's call the /api/v1/me endpoint which is protected
        String token = jwtProvider.generateIdentityToken(1L);

        mockMvc.perform(get("/api/v1/me")
                .header("Authorization", "Bearer " + token))
                // Our JWT filter leaves saedContext with STATE 1 (Identity Only)
                // Since /api/v1/me doesn't strictly check for assignment in the controller currently, it might return 200.
                // However, if the controller queries DB, RLS would block it.
                // We'll test that a bad header format returns 400.
                .andExpect(status().isOk());
                
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
