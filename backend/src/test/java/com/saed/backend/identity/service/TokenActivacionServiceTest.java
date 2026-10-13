package com.saed.backend.identity.service;

import com.saed.backend.common.service.EmailService;
import com.saed.backend.identity.dto.TokenActivacionInfoDTO;
import com.saed.backend.identity.repository.TokenActivacionRepository;
import com.saed.backend.identity.repository.TokenActivacionRepository.Consulta;
import com.saed.backend.identity.repository.TokenActivacionRepository.UsuarioInfo;
import com.saed.backend.identity.service.impl.TokenActivacionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenActivacionServiceTest {

    private static final String BASE_URL = "https://app.saed.example";
    private static final Pattern LINK = Pattern.compile("(https://app\\.saed\\.example/activar-cuenta\\?token=([0-9a-f]{64}))");

    @Mock
    private TokenActivacionRepository tokenRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private TokenActivacionService tokenService;

    @BeforeEach
    void setUp() throws Exception {
        tokenService = new TokenActivacionServiceImpl(tokenRepository, emailService, passwordEncoder, BASE_URL + "/");
    }

    private static UsuarioInfo user() {
        return new UsuarioInfo(10L, "carlos.admin", "carlos@saed.example", "Carlos", null);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("the token is stored only as a SHA-256 hash with a 48 h expiry and mailed as a link on the configured base URL")
    void issuedTokenIsHashedAtRestExpiringAndLinkedOnTheConfiguredBaseUrl() throws Exception {
        when(tokenRepository.emitirToken(eq(10L), eq("ACTIVACION_INICIAL"), anyString(), any(Instant.class), eq("127.0.0.1")))
                .thenReturn(Optional.of(user()));

        tokenService.generarYEnviarTokenActivacion(10L, "127.0.0.1");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> expiry = ArgumentCaptor.forClass(Instant.class);
        verify(tokenRepository).emitirToken(eq(10L), eq("ACTIVACION_INICIAL"), hash.capture(), expiry.capture(), eq("127.0.0.1"));
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarHtmlPublico(eq("carlos@saed.example"), contains("Bienvenido"), html.capture());

        Matcher link = LINK.matcher(html.getValue());
        assertTrue(link.find(), "the e-mail must carry the activation link built from the configured base URL");
        String rawToken = link.group(2);
        assertEquals(sha256(rawToken), hash.getValue(), "only the SHA-256 of the token is stored");
        assertNotEquals(rawToken, hash.getValue());
        Duration lifetime = Duration.between(Instant.now(), expiry.getValue());
        assertTrue(lifetime.compareTo(Duration.ofHours(47)) > 0 && lifetime.compareTo(Duration.ofHours(48).plusMinutes(1)) <= 0,
                "expires in 48 hours, was " + lifetime);
    }

    @Test
    @DisplayName("no password is ever part of the activation e-mail")
    void theActivationEmailCarriesNoPassword() throws Exception {
        when(tokenRepository.emitirToken(any(), anyString(), anyString(), any(), any())).thenReturn(Optional.of(user()));

        tokenService.generarYEnviarTokenActivacion(10L, null);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarHtmlPublico(anyString(), anyString(), html.capture());
        String lower = html.getValue().toLowerCase();
        assertFalse(lower.contains("passwordgenerada") || lower.contains("contraseña temporal")
                || lower.contains("contraseña generada"), "no password text in the e-mail");
    }

    @Test
    @DisplayName("no e-mail is sent when the database refuses to issue the token")
    void nothingIsMailedWhenIssuanceIsRefused() throws Exception {
        when(tokenRepository.emitirToken(any(), anyString(), anyString(), any(), any())).thenReturn(Optional.empty());

        tokenService.generarYEnviarTokenActivacion(10L, null);

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("a mail failure does not undo the issued token")
    void mailFailureIsContained() throws Exception {
        when(tokenRepository.emitirToken(any(), anyString(), anyString(), any(), any())).thenReturn(Optional.of(user()));
        doThrow(new IllegalStateException("smtp down")).when(emailService).enviarHtmlPublico(anyString(), anyString(), anyString());

        tokenService.generarYEnviarTokenActivacion(10L, null);

        verify(tokenRepository).emitirToken(any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("validarToken returns the account data only for a valid token")
    void validarToken_valido() throws Exception {
        String rawToken = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        Instant expira = Instant.now().plus(24, ChronoUnit.HOURS);
        when(tokenRepository.consultar(eq(sha256(rawToken)), any(Instant.class)))
                .thenReturn(new Consulta("VALIDO", user(), expira));

        TokenActivacionInfoDTO info = tokenService.validarToken(rawToken);

        assertTrue(info.valido());
        assertEquals(10L, info.idUsuario());
        assertEquals("carlos.admin", info.nombreUsuario());
        assertEquals(expira, info.fechaExpiracion());
    }

    @Test
    @DisplayName("validarToken reports used, expired and unknown tokens as invalid")
    void validarToken_invalidos() throws Exception {
        when(tokenRepository.consultar(anyString(), any(Instant.class)))
                .thenReturn(new Consulta("USADO", null, null))
                .thenReturn(new Consulta("EXPIRADO", null, null))
                .thenReturn(new Consulta("INEXISTENTE", null, null))
                .thenReturn(new Consulta("USUARIO_NO_DISPONIBLE", null, null));

        TokenActivacionInfoDTO used = tokenService.validarToken("a");
        TokenActivacionInfoDTO expired = tokenService.validarToken("b");
        TokenActivacionInfoDTO unknown = tokenService.validarToken("c");
        TokenActivacionInfoDTO unavailable = tokenService.validarToken("d");

        assertFalse(used.valido());
        assertTrue(used.mensaje().contains("ya ha sido utilizado"));
        assertFalse(expired.valido());
        assertTrue(expired.mensaje().contains("ha expirado"));
        assertFalse(unknown.valido());
        assertFalse(unavailable.valido());
        assertTrue(used.email() == null && expired.email() == null);
    }

    @Test
    @DisplayName("activarCuenta rejects weak passwords and mismatches before touching the database")
    void activarCuenta_passwordsInvalidas() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> tokenService.activarCuenta("t", "debil123", "debil123"));
        assertThrows(IllegalArgumentException.class, () -> tokenService.activarCuenta("t", "StrongP@ss1", "StrongP@ss2"));
        verifyNoInteractions(tokenRepository);
    }

    @Test
    @DisplayName("activarCuenta hashes the password and consumes the token through the atomic database operation")
    void activarCuenta_exitoso() throws Exception {
        String rawToken = "token_valido_para_activar";
        String newPassword = "SecureP@ssword2026!";
        when(passwordEncoder.encode(newPassword)).thenReturn("bcrypt_hash_2026");
        when(tokenRepository.activar(eq(sha256(rawToken)), eq("bcrypt_hash_2026"), any(Instant.class))).thenReturn("ACTIVADA");

        tokenService.activarCuenta(rawToken, newPassword, newPassword);

        verify(tokenRepository).activar(eq(sha256(rawToken)), eq("bcrypt_hash_2026"), any(Instant.class));
    }

    @Test
    @DisplayName("a used or expired token cannot activate; an unknown one is invalid")
    void activarCuenta_tokenNoUtilizable() throws Exception {
        when(passwordEncoder.encode(anyString())).thenReturn("h");
        when(tokenRepository.activar(anyString(), anyString(), any(Instant.class)))
                .thenReturn("USADO").thenReturn("EXPIRADO").thenReturn("INEXISTENTE").thenReturn("USUARIO_NO_DISPONIBLE");

        assertThrows(IllegalStateException.class, () -> tokenService.activarCuenta("t", "SecureP@ssword2026!", "SecureP@ssword2026!"));
        assertThrows(IllegalStateException.class, () -> tokenService.activarCuenta("t", "SecureP@ssword2026!", "SecureP@ssword2026!"));
        assertThrows(IllegalArgumentException.class, () -> tokenService.activarCuenta("t", "SecureP@ssword2026!", "SecureP@ssword2026!"));
        assertThrows(IllegalArgumentException.class, () -> tokenService.activarCuenta("t", "SecureP@ssword2026!", "SecureP@ssword2026!"));
    }

    @Test
    @DisplayName("solicitarReenvio mails a new link only when an account matches and contains failures")
    void solicitarReenvio() throws Exception {
        when(tokenRepository.emitirTokenReenvio(eq("carlos.admin"), anyString(), any(Instant.class), eq("10.0.0.1")))
                .thenReturn(Optional.of(user()));
        when(tokenRepository.emitirTokenReenvio(eq("nobody"), anyString(), any(Instant.class), anyString()))
                .thenReturn(Optional.empty());
        when(tokenRepository.emitirTokenReenvio(eq("boom"), anyString(), any(Instant.class), anyString()))
                .thenThrow(new IllegalStateException("database"));

        tokenService.solicitarReenvio("carlos.admin", "10.0.0.1");
        tokenService.solicitarReenvio("nobody", "10.0.0.1");
        tokenService.solicitarReenvio("boom", "10.0.0.1");
        tokenService.solicitarReenvio("  ", "10.0.0.1");

        verify(emailService).enviarHtmlPublico(eq("carlos@saed.example"), anyString(), contains(BASE_URL + "/activar-cuenta?token="));
        verify(emailService, never()).enviarHtmlPublico(eq("nobody"), anyString(), anyString());
    }

    private static String contains(String part) {
        return org.mockito.ArgumentMatchers.contains(part);
    }
}
