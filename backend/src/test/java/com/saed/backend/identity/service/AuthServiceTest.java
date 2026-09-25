package com.saed.backend.identity.service;

import com.saed.backend.identity.dto.AuthData;
import com.saed.backend.identity.dto.AuthResponse;
import com.saed.backend.identity.dto.LoginRequest;
import com.saed.backend.identity.repository.AuthRepository;
import com.saed.backend.security.jwt.JwtProvider;
import com.saed.backend.security.jwt.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuthServiceTest {

    private AuthRepository authRepository;
    private PasswordEncoder passwordEncoder;
    private JwtProvider jwtProvider;
    private RefreshTokenService refreshTokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authRepository = mock(AuthRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        refreshTokenService = mock(RefreshTokenService.class);
        
        jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "jwtSecret", "dGhpcy1pcy1hLXZlcnktc2VjdXJlLWtleS1mb3Itc2FlZC0yLjAtc2VjcmV0");
        ReflectionTestUtils.setField(jwtProvider, "jwtExpirationMs", 86400000);
        
        authService = new AuthService(authRepository, passwordEncoder, jwtProvider, refreshTokenService);
    }

    @Test
    void whenValidCredentials_thenReturnsToken() {
        LoginRequest request = new LoginRequest();
        request.setUsername("test@saed.com");
        request.setPassword("password123");

        AuthData authData = new AuthData();
        authData.setIdUsuario(1L);
        authData.setHashPassword("hashed");
        authData.setEstado("ACTIVO");
        authData.setIntentosFallidos(0);

        when(authRepository.getAuthData("test@saed.com")).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertNotNull(response.getToken());
        verify(authRepository).registerLoginSuccess(1L, "API");
    }

    @Test
    void whenInvalidPassword_thenIncrementsFailedAttempts() {
        LoginRequest request = new LoginRequest();
        request.setUsername("test@saed.com");
        request.setPassword("wrong");

        AuthData authData = new AuthData();
        authData.setIdUsuario(1L);
        authData.setHashPassword("hashed");
        authData.setEstado("ACTIVO");
        authData.setIntentosFallidos(0);

        when(authRepository.getAuthData("test@saed.com")).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        Exception ex = assertThrows(com.saed.backend.identity.exception.InvalidCredentialsException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("Credenciales"));
        verify(authRepository).registerLoginFailure(1L, "API");
    }

    @Test
    void whenUserInactive_thenBlocksLogin() {
        LoginRequest request = new LoginRequest();
        request.setUsername("test@saed.com");
        request.setPassword("password123");
        
        AuthData authData = new AuthData();
        authData.setHashPassword("hashed");
        authData.setEstado("INACTIVO");
        
        when(authRepository.getAuthData("test@saed.com")).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        
        Exception ex = assertThrows(com.saed.backend.identity.exception.InvalidCredentialsException.class, () -> authService.login(request));
        assertEquals("Credenciales invalidas", ex.getMessage());
    }

    @Test
    void whenUserBlocked_thenBlocksLogin() {
        LoginRequest request = new LoginRequest();
        request.setUsername("test@saed.com");
        request.setPassword("password123");
        
        AuthData authData = new AuthData();
        authData.setHashPassword("hashed");
        authData.setEstado("BLOQUEADO");
        authData.setIdUsuario(1L);
        
        when(authRepository.getAuthData("test@saed.com")).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        
        Exception ex = assertThrows(com.saed.backend.identity.exception.InvalidCredentialsException.class, () -> authService.login(request));
        assertEquals("Credenciales invalidas", ex.getMessage());
    }

    @Test
    void whenVerifyPasswordCorrect_thenReturnsTrue() {
        when(authRepository.getPasswordHash(1L)).thenReturn(Optional.of("hashed_pw"));
        when(passwordEncoder.matches("secret123", "hashed_pw")).thenReturn(true);

        boolean result = authService.verifyPassword(1L, "secret123");
        assertTrue(result);
    }

    @Test
    void whenVerifyPasswordIncorrect_thenReturnsFalse() {
        when(authRepository.getPasswordHash(1L)).thenReturn(Optional.of("hashed_pw"));
        when(passwordEncoder.matches("wrong_pw", "hashed_pw")).thenReturn(false);

        boolean result = authService.verifyPassword(1L, "wrong_pw");
        assertFalse(result);
    }

    @Test
    void whenVerifyPasswordNullOrEmpty_thenReturnsFalse() {
        assertFalse(authService.verifyPassword(null, "secret"));
        assertFalse(authService.verifyPassword(1L, null));
        assertFalse(authService.verifyPassword(1L, "   "));
    }

    @Test
    void whenVerifyPasswordUserId1WithOldHardcodedFallback_thenReturnsFalse() {
        when(authRepository.getPasswordHash(1L)).thenReturn(Optional.of("hashed_pw"));
        when(passwordEncoder.matches("admin_global123", "hashed_pw")).thenReturn(false);

        boolean result = authService.verifyPassword(1L, "admin_global123");
        assertFalse(result, "SD-01: Hardcoded fallback admin_global123 must not bypass password verification");
    }

    @Test
    void whenLoginUserId1WithMismatchedHash_thenFailsEvenWithOldHardcodedCredential() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin_global");
        request.setPassword("admin_global123");

        AuthData authData = new AuthData();
        authData.setIdUsuario(1L);
        authData.setHashPassword("different_hashed_password");
        authData.setEstado("ACTIVO");

        when(authRepository.getAuthData("admin_global")).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches("admin_global123", "different_hashed_password")).thenReturn(false);

        assertThrows(com.saed.backend.identity.exception.InvalidCredentialsException.class,
                () -> authService.login(request),
                "SD-01: Hardcoded admin_global123 must not authenticate if hash does not match in DB");
    }

    @Test
    void whenAdminPropiedadWithoutProperties_thenThrowsNoAssignedPropertiesException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin_prop_sin_propiedad");
        request.setPassword("password123");

        AuthData authData = new AuthData();
        authData.setIdUsuario(202L);
        authData.setHashPassword("hashed_pw");
        authData.setEstado("ACTIVO");

        when(authRepository.getAuthData("admin_prop_sin_propiedad")).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches("password123", "hashed_pw")).thenReturn(true);
        when(authRepository.isInactiveAdminPropiedadWithoutProperties(202L)).thenReturn(true);

        com.saed.backend.identity.exception.NoAssignedPropertiesException ex =
                assertThrows(com.saed.backend.identity.exception.NoAssignedPropertiesException.class,
                        () -> authService.login(request));

        assertEquals("No administra ninguna propiedad hasta el momento", ex.getMessage());
        verify(authRepository, never()).registerLoginSuccess(anyLong(), anyString());
    }
}
