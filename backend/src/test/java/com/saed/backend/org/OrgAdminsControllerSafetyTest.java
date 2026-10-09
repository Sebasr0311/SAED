package com.saed.backend.org;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F3-03: OrgAdminsController must not elevate the database context, must not set or e-mail
 * passwords, and must consume plan seats before re-activating an assignment (F03-02).
 */
class OrgAdminsControllerSafetyTest {

    private static String source() throws IOException {
        return Files.readString(Paths.get("src", "main", "java", "com", "saed", "backend", "org", "controller",
                "OrgAdminsController.java"), StandardCharsets.UTF_8);
    }

    @Test
    void noBootstrapOrForgedContext() throws IOException {
        String src = source();
        assertFalse(src.contains("SET_BOOTSTRAP_CONTEXT"), "no bootstrap context elevation");
        assertFalse(src.contains("PKG_SAED_SESSION.SET_CONTEXT"), "no forged context restore");
    }

    @Test
    void noPasswordIsSetOrMailedByAnotherUser() throws IOException {
        String src = source();
        assertFalse(src.contains("SET HASH_PASSWORD"), "administrators never set another user's password");
        assertFalse(src.contains("enviarCredencialesCreadoPorUsuario"), "no clear-text credential e-mail");
        assertTrue(src.contains("generarYEnviarTokenActivacion"), "new accounts receive an activation link");
        assertTrue(src.contains("orgAdminAccountService.resolveOrCreate"), "identity resolution is scoped");
        assertTrue(src.contains("assertIdentityManagedByOrganization"), "identity edits are scoped");
    }

    @Test
    void reactivationConsumesAPlanSeat() throws IOException {
        String src = source();
        int lock = src.indexOf("validateAndLockUserLimit");
        int reactivate = src.indexOf("SET ESTADO = 'ACTIVA'");
        assertTrue(lock > 0 && reactivate > 0 && lock < reactivate,
                "plan user limit is locked before an assignment is re-activated");
    }
}
