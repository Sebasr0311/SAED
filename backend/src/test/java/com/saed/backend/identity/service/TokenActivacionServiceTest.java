package com.saed.backend.identity.service;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.identity.dto.TokenActivacionInfoDTO;
import com.saed.backend.identity.repository.TokenActivacionRepository;
import com.saed.backend.identity.service.impl.TokenActivacionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenActivacionServiceTest {

    @Mock
    private TokenActivacionRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private TokenActivacionService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenActivacionServiceImpl(
                tokenRepository,
                emailService,
                passwordEncoder,
                "https://app.saed.com"
        );
    }

    @Test
    @DisplayName("generarYEnviarTokenActivacion invalida previos, guarda token y envía correo")
    void generarYEnviarTokenActivacion_exitoso() throws Exception {
        Long idUsuario = 10L;
        TokenActivacionRepository.UsuarioInfo user = new TokenActivacionRepository.UsuarioInfo(
                idUsuario, "carlos.admin", "carlos@saed.com", "Carlos", "ACTIVO"
        );
        when(tokenRepository.obtenerUsuarioInfo(idUsuario)).thenReturn(Optional.of(user));

        String rawToken = tokenService.generarYEnviarTokenActivacion(idUsuario, "127.0.0.1");

        assertNotNull(rawToken);
        assertEquals(64, rawToken.length()); // 32 bytes hex
        verify(tokenRepository).invalidarTokensPrevios(idUsuario, "ACTIVACION_INICIAL");
        verify(tokenRepository).guardarToken(eq(idUsuario), anyString(), eq("ACTIVACION_INICIAL"), any(Instant.class), eq("127.0.0.1"));
        verify(emailService).enviarHtmlPublico(eq("carlos@saed.com"), contains("Bienvenido"), contains(rawToken));
    }

    @Test
    @DisplayName("validarToken retorna info completa si el token es válido y no ha expirado")
    void validarToken_valido() {
        String rawToken = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        Instant expira = Instant.now().plus(24, ChronoUnit.HOURS);
        TokenActivacionRepository.TokenRegistro reg = new TokenActivacionRepository.TokenRegistro(
                1L, 10L, "hash_dummy", "ACTIVACION_INICIAL", expira, false
        );
        TokenActivacionRepository.UsuarioInfo user = new TokenActivacionRepository.UsuarioInfo(
                10L, "carlos.admin", "carlos@saed.com", "Carlos", "ACTIVO"
        );

        when(tokenRepository.buscarPorHash(anyString())).thenReturn(Optional.of(reg));
        when(tokenRepository.obtenerUsuarioInfo(10L)).thenReturn(Optional.of(user));

        TokenActivacionInfoDTO info = tokenService.validarToken(rawToken);

        assertTrue(info.valido());
        assertEquals(10L, info.idUsuario());
        assertEquals("carlos.admin", info.nombreUsuario());
        assertEquals("carlos@saed.com", info.email());
        assertEquals("Carlos", info.primerNombre());
    }

    @Test
    @DisplayName("validarToken retorna inválido si el token ya fue utilizado")
    void validarToken_yaUsado() {
        String rawToken = "token_ya_usado";
        Instant expira = Instant.now().plus(24, ChronoUnit.HOURS);
        TokenActivacionRepository.TokenRegistro reg = new TokenActivacionRepository.TokenRegistro(
                1L, 10L, "hash_dummy", "ACTIVACION_INICIAL", expira, true
        );

        when(tokenRepository.buscarPorHash(anyString())).thenReturn(Optional.of(reg));

        TokenActivacionInfoDTO info = tokenService.validarToken(rawToken);

        assertFalse(info.valido());
        assertTrue(info.mensaje().contains("ya ha sido utilizado"));
    }

    @Test
    @DisplayName("validarToken retorna inválido si el token ha expirado")
    void validarToken_expirado() {
        String rawToken = "token_expirado";
        Instant expira = Instant.now().minus(2, ChronoUnit.HOURS);
        TokenActivacionRepository.TokenRegistro reg = new TokenActivacionRepository.TokenRegistro(
                1L, 10L, "hash_dummy", "ACTIVACION_INICIAL", expira, false
        );

        when(tokenRepository.buscarPorHash(anyString())).thenReturn(Optional.of(reg));

        TokenActivacionInfoDTO info = tokenService.validarToken(rawToken);

        assertFalse(info.valido());
        assertTrue(info.mensaje().contains("ha expirado"));
    }

    @Test
    @DisplayName("activarCuenta rechaza contraseñas débiles que no cumplen políticas de seguridad")
    void activarCuenta_passwordDebil() {
        String rawToken = "token_dummy";
        assertThrows(IllegalArgumentException.class, () ->
                tokenService.activarCuenta(rawToken, "debil123", "debil123")
        );
    }

    @Test
    @DisplayName("activarCuenta rechaza contraseñas que no coinciden")
    void activarCuenta_passwordsNoCoinciden() {
        String rawToken = "token_dummy";
        assertThrows(IllegalArgumentException.class, () ->
                tokenService.activarCuenta(rawToken, "StrongP@ss1", "StrongP@ss2")
        );
    }

    @Test
    @DisplayName("activarCuenta con contraseña robusta actualiza hash, activa cuenta y consume token")
    void activarCuenta_exitoso() {
        String rawToken = "token_valido_para_activar";
        String newPassword = "SecureP@ssword2026!";
        Instant expira = Instant.now().plus(24, ChronoUnit.HOURS);
        TokenActivacionRepository.TokenRegistro reg = new TokenActivacionRepository.TokenRegistro(
                5L, 20L, "hash_dummy", "ACTIVACION_INICIAL", expira, false
        );

        when(tokenRepository.buscarPorHash(anyString())).thenReturn(Optional.of(reg));
        when(passwordEncoder.encode(newPassword)).thenReturn("bcrypt_hash_2026");

        tokenService.activarCuenta(rawToken, newPassword, newPassword);

        verify(tokenRepository).actualizarPasswordYActivar(20L, "bcrypt_hash_2026");
        verify(tokenRepository).marcarComoUsado(5L);
    }
}
