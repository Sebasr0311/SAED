package com.saed.backend.identity.service;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Credential of an account that has not been activated yet (DEC-13). It is the hash of a random 256-bit value that
 * nobody knows, so the account cannot be used to sign in until its owner sets a password through the single-use
 * activation link.
 */
public final class PendingAccountCredentials {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PendingAccountCredentials() {
    }

    public static String unusableHash(PasswordEncoder encoder) {
        byte[] unknown = new byte[32];
        RANDOM.nextBytes(unknown);
        return encoder.encode(HexFormat.of().formatHex(unknown));
    }
}
