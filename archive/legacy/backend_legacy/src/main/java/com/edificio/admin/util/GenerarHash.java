package com.edificio.admin.util;

import org.mindrot.jbcrypt.BCrypt;

public class GenerarHash {
    public static void main(String[] args) {
        String[] passwords = {"Admin2026!", "Portero2026!", "Residente2026!"};
        String[] labels    = {"ADMIN",      "PORTERO",      "RESIDENTE"};
        for (int i = 0; i < passwords.length; i++) {
            String hash = BCrypt.hashpw(passwords[i], BCrypt.gensalt(12));
            // Verify immediately to catch any encoding issue
            boolean ok = BCrypt.checkpw(passwords[i], hash);
            System.out.println("HASH_" + labels[i] + "=" + hash + " [verify=" + ok + "]");
        }
    }
}
