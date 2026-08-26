package com.saed.backend.identity.service;

import com.saed.backend.identity.dto.AuthData;
import com.saed.backend.identity.dto.AuthResponse;
import com.saed.backend.identity.dto.LoginRequest;
import com.saed.backend.identity.repository.AuthRepository;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuthServiceTest {

    private AuthRepository authRepository;
    private PasswordEncoder passwordEncoder;
    private JwtProvider jwtProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authRepository = mock(AuthRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtProvider = mock(JwtProvider.class);
        authService = new AuthService(authRepository, passwordEncoder, jwtProvider);
    }

    @Test
    void whenValidCredentials_thenReturnsToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@saed.com");
        request.setPassword("password123");

        AuthData authData = new AuthData();
        authData.setIdUsuario(1L);
        authData.setHashPassword("hashed");
        authData.setEstado("ACTIVO");
        authData.setIntentosFallidos(0);

        when(authRepository.getAuthData("test@saed.com")).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtProvider.generateIdentityToken(1L)).thenReturn("dummy-jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("dummy-jwt-token", response.getToken());
        verify(authRepository).registerLoginSuccess(1L, "0.0.0.0");
    }

    @Test
    void whenInvalidPassword_thenIncrementsFailedAttempts() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@saed.com");
        request.setPassword("wrong");

        AuthData authData = new AuthData();
        authData.setIdUsuario(1L);
        authData.setHashPassword("hashed");
        authData.setEstado("ACTIVO");
        authData.setIntentosFallidos(0);

        when(authRepository.getAuthData("test@saed.com")).thenReturn(Optional.of(authData));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));
        assertEquals("Credenciales invǭlidas", ex.getMessage());
        verify(authRepository).registerLoginFailure(1L, "0.0.0.0");
    }

    @Test
    void whenUserInactive_thenBlocksLogin() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@saed.com");
        request.setPassword("password123");

        AuthData authData = new AuthData();
        authData.setEstado("INACTIVO");

        when(authRepository.getAuthData("test@saed.com")).thenReturn(Optional.of(authData));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));
        assertEquals("Usuario inactivo o bloqueado", ex.getMessage());
    }
}
