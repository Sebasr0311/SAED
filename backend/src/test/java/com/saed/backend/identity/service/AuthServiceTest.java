package com.saed.backend.identity.service;

import com.saed.backend.identity.dto.AuthResponse;
import com.saed.backend.identity.dto.LoginRequest;
import com.saed.backend.identity.model.User;
import com.saed.backend.identity.repository.UserRepository;
import com.saed.backend.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtProvider jwtProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtProvider = mock(JwtProvider.class);
        authService = new AuthService(userRepository, passwordEncoder, jwtProvider);
    }

    @Test
    void whenValidCredentials_thenReturnsToken() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@saed.com");
        request.setPassword("password123");

        User user = new User();
        user.setIdUsuario(1L);
        user.setEmail("test@saed.com");
        user.setHashPassword("hashed");
        user.setEstado("ACTIVO");
        user.setIntentosFallidos(0);

        when(userRepository.findByEmail("test@saed.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtProvider.generateIdentityToken(1L)).thenReturn("dummy-jwt-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("dummy-jwt-token", response.getToken());
        verify(userRepository).updateLastLogin(1L);
        verify(userRepository).updateFailedAttempts(1L, 0);
    }

    @Test
    void whenInvalidPassword_thenIncrementsFailedAttempts() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@saed.com");
        request.setPassword("wrong");

        User user = new User();
        user.setIdUsuario(1L);
        user.setHashPassword("hashed");
        user.setEstado("ACTIVO");
        user.setIntentosFallidos(0);

        when(userRepository.findByEmail("test@saed.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));
        assertEquals("Credenciales inválidas", ex.getMessage());
        verify(userRepository).updateFailedAttempts(1L, 1);
    }

    @Test
    void whenUserInactive_thenBlocksLogin() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@saed.com");
        request.setPassword("password123");

        User user = new User();
        user.setEstado("INACTIVO");

        when(userRepository.findByEmail("test@saed.com")).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request));
        assertEquals("Usuario inactivo o bloqueado", ex.getMessage());
    }
}
