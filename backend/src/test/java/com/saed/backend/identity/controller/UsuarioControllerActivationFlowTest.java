package com.saed.backend.identity.controller;

import com.saed.backend.identity.service.PendingAccountCredentials;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DEC-13: accounts created by an administrator, or by a resident for a cohabitant, never receive a password by
 * e-mail or in the API response; they start with an unusable credential and activate through a single-use link.
 */
class UsuarioControllerActivationFlowTest {

    private static String read(String... parts) throws IOException {
        return Files.readString(Paths.get("src/main", parts), StandardCharsets.UTF_8);
    }

    private static String controller() throws IOException {
        return read("java", "com", "saed", "backend", "identity", "controller", "UsuarioController.java");
    }

    private static String createMethod() throws IOException {
        String src = controller();
        int start = src.indexOf("public ResponseEntity<ApiResponse<Map<String, Object>>> crearUsuario");
        int end = src.indexOf("public ResponseEntity<ApiResponse<Void>> actualizarUsuario");
        assertTrue(start > 0 && end > start);
        return src.substring(start, end);
    }

    @Test
    void unusableCredentialsAreRandomAndNeverTheRequestedPassword() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("hash");

        assertEquals("hash", PendingAccountCredentials.unusableHash(encoder));
        PendingAccountCredentials.unusableHash(encoder);

        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(encoder, atLeast(2)).encode(raw.capture());
        assertEquals(64, raw.getAllValues().get(0).length());
        assertNotEquals(raw.getAllValues().get(0), raw.getAllValues().get(1));
    }

    @Test
    void creationNeverHandlesAPlainPasswordOrMailsCredentials() throws IOException {
        String create = createMethod();
        assertFalse(create.contains("rawPassword"), "the requested password is ignored, never read");
        assertFalse(create.contains("PasswordGenerator"), "no generated password");
        assertFalse(create.contains("passwordGenerada"), "the response carries no password");
        assertFalse(create.contains("enviarCredencialesCreadoPorUsuario"), "no credential e-mail");
        assertFalse(create.contains("saedfront.vercel.app"), "no hard-coded login URL");
        assertFalse(controller().contains("emailService"), "the controller no longer sends mail itself");
    }

    @Test
    void newAccountsStartUnusableAndReceiveAnActivationLink() throws IOException {
        String create = createMethod();
        assertTrue(create.contains("PendingAccountCredentials.unusableHash(passwordEncoder)"));
        assertTrue(create.contains("'PENDIENTE_VERIFICACION'"), "direct inserts stay pending until activated");
        assertTrue(create.contains("generarYEnviarTokenActivacion(idUsuario, null)"));
        int inserts = create.indexOf("INSERT INTO USUARIO_ASIGNACIONES");
        int token = create.indexOf("generarYEnviarTokenActivacion");
        assertTrue(inserts > 0 && token > inserts, "the link is issued after the assignment exists");
    }

    @Test
    void theCredentialEmailTemplateAndSenderAreGone() throws IOException {
        assertFalse(Files.exists(Paths.get("src", "main", "resources", "templates", "correos",
                "correo_credenciales_creado_por_usuario.html")));
        assertFalse(read("java", "com", "saed", "backend", "common", "service", "EmailService.java")
                .contains("enviarCredencialesCreadoPorUsuario"));
    }
}
