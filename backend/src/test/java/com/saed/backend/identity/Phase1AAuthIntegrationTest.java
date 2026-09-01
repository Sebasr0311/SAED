package com.saed.backend.identity;

import com.saed.backend.identity.dto.AuthResponse;
import com.saed.backend.identity.dto.LoginRequest;
import com.saed.backend.identity.service.AuthService;
import com.saed.backend.security.jwt.JwtProvider;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class Phase1AAuthIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${jwt.secret:dGhpcy1pcy1hLXZlcnktc2VjdXJlLWtleS1mb3Itc2FlZC0yLjAtc2VjcmV0}")
    private String jwtSecret;

    private static final String TEST_USERNAME = "admin_global@saed.com";
    private static final String TEST_PASSWORD = "Password123!";

    @BeforeEach
    public void setup() {
        SaedContextHolder.setContext(SaedContext.builder().userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        String hash = encoder.encode(TEST_PASSWORD);
        
        try { jdbcTemplate.update("UPDATE PERSONAS SET EMAIL = ? WHERE ID_PERSONA = 1", TEST_USERNAME); } catch (Exception e) {}
        try { jdbcTemplate.update("UPDATE USUARIOS SET HASH_PASSWORD = ?, NOMBRE_USUARIO = 'admin_global', EMAIL = ?, ESTADO = 'ACTIVO' WHERE ID_USUARIO = 1", hash, TEST_USERNAME); } catch (Exception e) {}
        try { jdbcTemplate.update("UPDATE ADMINISTRADORES_SAED SET NIVEL = 'SUPERADMIN', ESTADO = 'ACTIVO' WHERE ID_USUARIO = 1"); } catch (Exception e) {}
        try { jdbcTemplate.update("INSERT INTO ADMINISTRADORES_SAED (ID_ADMINISTRADOR_SAED, ID_USUARIO, NIVEL, ESTADO) VALUES (1, 1, 'SUPERADMIN', 'ACTIVO')"); } catch (Exception e) {}

        Long userId = getTestUserId();
        
        if (userId != null) {
            jdbcTemplate.update("CALL SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP.REGISTER_LOGIN_SUCCESS(?, ?)", userId, "TEST_SETUP");
        }
        
        jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        SaedContextHolder.clearContext();
    }

    @AfterEach
    public void tearDown() {
        SaedContextHolder.clearContext();
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
            Map<String, Object> out = call.execute(Map.of("p_email", TEST_USERNAME));
            Number id = (Number) out.get("p_id_usuario");
            return id != null ? id.longValue() : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Test
    public void testA_LoginCorrecto() {
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
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
        req.setUsername(TEST_USERNAME);
        req.setPassword("wrongpassword");
        
        Exception ex = assertThrows(com.saed.backend.identity.exception.InvalidCredentialsException.class, () -> authService.login(req));
        assertEquals("Credenciales invalidas", ex.getMessage());
    }

    @Test
    public void testC_UsuarioInexistente() {
        LoginRequest req = new LoginRequest();
        req.setUsername("notexists.saed");
        req.setPassword("password");
        
        Exception ex = assertThrows(com.saed.backend.identity.exception.InvalidCredentialsException.class, () -> authService.login(req));
        assertEquals("Credenciales invalidas", ex.getMessage());
    }

    @Test
    public void testH_JwtValidoAndStructure() {
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
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
        int count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM USUARIOS", Integer.class);
        assertEquals(0, count, "El backend no deberia poder hacer SELECT directo a USUARIOS debido a RLS");
    }

    @Test
    public void testHttp401OnInvalidCredentials() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("notexists.saed");
        req.setPassword("wrong");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Credenciales invalidas"));
    }

    @Test
    public void testExpiredJwtIsRejected() throws Exception {
        java.util.Date now = new java.util.Date();
        java.util.Date past = new java.util.Date(now.getTime() - 3600000); 
        
        String expiredJwt = Jwts.builder()
                .subject("100")
                .issuedAt(past)
                .expiration(past)
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();

        mockMvc.perform(get("/api/v1/me/contexts")
                .header("Authorization", "Bearer " + expiredJwt))
                .andExpect(status().isUnauthorized());
                
        assertNull(SaedContextHolder.getContext(), "SaedContext should be null since authentication failed");
    }
}
