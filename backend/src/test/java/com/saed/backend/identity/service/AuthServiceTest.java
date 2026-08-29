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
}
