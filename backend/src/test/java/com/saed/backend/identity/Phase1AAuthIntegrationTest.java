package com.saed.backend.identity;

import com.saed.backend.identity.dto.AuthResponse;
import com.saed.backend.identity.dto.LoginRequest;
import com.saed.backend.identity.service.AuthService;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("dev")
public class Phase1AAuthIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${jwt.secret:dGhpcy1pcy1hLXZlcnktc2VjdXJlLWtleS1mb3Itc2FlZC0yLjAtc2VjcmV0}")
    private String jwtSecret;

    private static final String TEST_EMAIL = "integration@saed.com";
    private static final String TEST_PASSWORD = "password123";

    @BeforeEach
    public void setup() {
        Long userId = getTestUserId();
        if (userId != null) {
            jdbcTemplate.update("CALL SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP.REGISTER_LOGIN_SUCCESS(?, ?)", userId, "TEST_SETUP");
        }
    }
    
    private Long getTestUserId() {
        try {
            var call = new org.springframework.jdbc.core.simple.SimpleJdbcCall(jdbcTemplate)
                .withCatalogName("SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP")
                .withProcedureName("GET_AUTH_DATA")
                .withoutProcedureColumnMetaDataAccess()
                .declareParameters(
                    new org.springframework.jdbc.core.SqlParameter("p_email", java.sql.Types.VARCHAR), 
                    new org.springframework.jdbc.core.SqlOutParameter("p_id_usuario", java.sql.Types.NUMERIC), 
                    new org.springframework.jdbc.core.SqlOutParameter("p_hash", java.sql.Types.VARCHAR), 
                    new org.springframework.jdbc.core.SqlOutParameter("p_estado", java.sql.Types.VARCHAR), 
                    new org.springframework.jdbc.core.SqlOutParameter("p_intentos", java.sql.Types.NUMERIC)
                );
            Map<String, Object> out = call.execute(Map.of("p_email", TEST_EMAIL));
            Number id = (Number) out.get("p_id_usuario");
            return id != null ? id.longValue() : null;
        } catch(Exception e) {
            return null;
        }
    }

    @Test
    public void testA_LoginCorrecto() {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);
        
        AuthResponse res = authService.login(req);
        
        assertNotNull(res);
        assertNotNull(res.getToken());
        assertFalse(res.isRequiresPasswordChange());
        assertNotNull(res.getIdUsuario());
    }

    @Test
    public void testB_PasswordIncorrecto() {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword("wrongpassword");
        
        Exception ex = assertThrows(com.saed.backend.identity.exception.InvalidCredentialsException.class, () -> authService.login(req));
        assertEquals("Credenciales invalidas", ex.getMessage());
    }

    @Test
    public void testC_UsuarioInexistente() {
        LoginRequest req = new LoginRequest();
        req.setEmail("notexists@saed.com");
        req.setPassword("password");
        
        Exception ex = assertThrows(com.saed.backend.identity.exception.InvalidCredentialsException.class, () -> authService.login(req));
        assertEquals("Credenciales invalidas", ex.getMessage());
    }

    @Test
    public void testH_JwtValidoAndStructure() {
        LoginRequest req = new LoginRequest();
        req.setEmail(TEST_EMAIL);
        req.setPassword(TEST_PASSWORD);
        
        AuthResponse res = authService.login(req);
        
        String token = res.getToken();
        assertTrue(jwtProvider.validateToken(token));
        
        Long userId = jwtProvider.getUserIdFromToken(token);
        assertEquals(res.getIdUsuario(), userId);
        
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
                
        assertNull(claims.get("rol"), "JWT should NOT contain rol");
        assertNull(claims.get("organizacion"), "JWT should NOT contain organizacion");
        assertNull(claims.get("propiedad"), "JWT should NOT contain propiedad");
    }

    @Test
    public void testQ_UsoCorrectoDePkgAuthBootstrap() {
        Long userId = getTestUserId();
        assertNotNull(userId, "PKG_AUTH_BOOTSTRAP debe devolver el usuario independientemente de RLS");
    }

    @Test
    public void testR_VerificacionRlsSelectDirecto() {
        int count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SAED_V39_FINAL_TEST.USUARIOS", Integer.class);
        assertEquals(0, count, "El backend no deberia poder hacer SELECT directo a USUARIOS debido a RLS");
    }
    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    public void testHttp401OnInvalidCredentials() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("notexists@saed.com");
        req.setPassword("wrong");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.success").value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("Credenciales invalidas"));
    }

    @Test
    public void testExpiredJwtIsRejected() throws Exception {
        java.util.Date now = new java.util.Date();
        java.util.Date past = new java.util.Date(now.getTime() - 3600000); 
        
        String expiredJwt = io.jsonwebtoken.Jwts.builder()
                .subject("100")
                .issuedAt(past)
                .expiration(past)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me/contexts")
                .header("Authorization", "Bearer " + expiredJwt))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
                
        assertNull(com.saed.backend.context.SaedContextHolder.getContext(), "SaedContext should be null since authentication failed");
    }
}

