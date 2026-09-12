package com.saed.backend.common.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // omitted I, O
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz"; // omitted l
    private static final String DIGITS = "23456789";                  // omitted 0, 1
    private static final String SPECIAL = "!@#$%*";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    /**
     * Generates a random, secure, 10-character password guaranteed to contain
     * at least one uppercase letter, one lowercase letter, one digit, and one special character.
     */
    public static String generate() {
        return generate(10);
    }

    public static String generate(int length) {
        if (length < 8) {
            length = 8;
        }

        List<Character> chars = new ArrayList<>(length);
        chars.add(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        chars.add(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        chars.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        chars.add(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));

        for (int i = 4; i < length; i++) {
            chars.add(ALL.charAt(RANDOM.nextInt(ALL.length())));
        }

        Collections.shuffle(chars, RANDOM);

        StringBuilder sb = new StringBuilder(chars.size());
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }
}
