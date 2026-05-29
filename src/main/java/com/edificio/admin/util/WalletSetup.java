package com.edificio.admin.util;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Inicializa el wallet de Oracle ATP para conexion cloud.
 *
 * Orden de preferencia:
 *   1. Variable de entorno TNS_ADMIN (ya existe y apunta a directorio valido)
 *   2. wallet.zip en el classpath (se extrae a ${java.io.tmpdir}/saed-oracle-wallet/)
 *
 * Debe llamarse ANTES de cualquier intento de conexion a BD.
 */
public class WalletSetup {

    private static final String WALLET_ZIP  = "/wallet.zip";
    private static final String WALLET_DIR  = "saed-oracle-wallet";
    private static final String PROP_EXTRACTED = "saed.wallet.extracted";
    private static final String ENV_WALLET_B64 = "WALLET_BASE64";

    public static void init() {
        // 1. TNS_ADMIN ya configurado externamente
        String tnsAdmin = getenv("TNS_ADMIN");
        if (tnsAdmin != null && !tnsAdmin.isEmpty()) {
            Path p = Paths.get(tnsAdmin);
            if (Files.exists(p) && Files.isDirectory(p)) {
                System.out.println("[WalletSetup] Usando TNS_ADMIN del entorno: " + p.toAbsolutePath());
                return;
            }
            System.out.println("[WalletSetup] TNS_ADMIN=" + tnsAdmin + " no existe o no es directorio — se ignorara");
        }

        // 2. Ya extraido en una ejecucion anterior
        String prev = System.getProperty(PROP_EXTRACTED);
        if (prev != null) {
            Path p = Paths.get(prev);
            if (Files.exists(p)) {
                System.out.println("[WalletSetup] Wallet ya extraido en: " + p);
                return;
            }
        }

        Path targetDir = Paths.get(System.getProperty("java.io.tmpdir"), WALLET_DIR);

        // 3. WALLET_BASE64 env var (Railway — wallet como base64 en variable de entorno)
        String walletB64 = getenv(ENV_WALLET_B64);
        if (walletB64 != null && !walletB64.isEmpty()) {
            try {
                byte[] zipBytes = java.util.Base64.getDecoder().decode(walletB64);
                extractWallet(zipBytes, targetDir);
            String absPath = targetDir.toAbsolutePath().toString();
            fixSqlnetOra(targetDir, absPath);
            System.setProperty("oracle.net.tns_admin", absPath);
            System.out.println("[WalletSetup] Wallet extraido desde WALLET_BASE64 en: " + absPath);
            System.setProperty(PROP_EXTRACTED, absPath);
            return;
            } catch (Exception e) {
                System.err.println("[WalletSetup] Error al decodificar WALLET_BASE64: " + e.getMessage());
            }
        }

        // 4. Extraer wallet.zip del classpath
        try (InputStream is = WalletSetup.class.getResourceAsStream(WALLET_ZIP)) {
            if (is == null) {
                System.out.println("[WalletSetup] wallet.zip no encontrado en el classpath — conexion local por defecto");
                return;
            }

            Files.createDirectories(targetDir);
            try (ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path entryPath = targetDir.resolve(entry.getName()).normalize();

                    if (!entryPath.startsWith(targetDir)) {
                        throw new IOException("ZIP entry fuera del directorio destino: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(entryPath);
                    } else {
                        Files.createDirectories(entryPath.getParent());
                        try (OutputStream os = Files.newOutputStream(entryPath)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = zis.read(buf)) != -1) {
                                os.write(buf, 0, n);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }

            String absPath = targetDir.toAbsolutePath().toString();
            fixSqlnetOra(targetDir, absPath);
            System.setProperty("oracle.net.tns_admin", absPath);
            System.out.println("[WalletSetup] Wallet extraido exitosamente en: " + absPath);
            System.setProperty(PROP_EXTRACTED, absPath);

        } catch (Exception e) {
            System.err.println("[WalletSetup] Error al extraer wallet: " + e.getMessage());
        }
    }

    private static void extractWallet(byte[] zipBytes, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir)) continue;
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (OutputStream os = Files.newOutputStream(entryPath)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = zis.read(buf)) != -1) os.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Corrige sqlnet.ora reemplazando "?/network/admin" por la ruta real.
     * El wallet de ATP usa "?/network/admin" como placeholder; el driver
     * JDBC thin resuelve ? como TNS_ADMIN, pero sqlplus usa ORACLE_HOME.
     * Con la ruta absoluta funciona en ambos entornos.
     */
    private static void fixSqlnetOra(Path targetDir, String absPath) {
        try {
            Path sqlnet = targetDir.resolve("sqlnet.ora");
            if (Files.exists(sqlnet)) {
                String content = Files.readString(sqlnet);
                String walletDir = absPath.replace("\\", "/");
                content = content.replace("?/network/admin", walletDir);
                Files.writeString(sqlnet, content);
                System.out.println("[WalletSetup] sqlnet.ora corregido: ?/network/admin -> " + walletDir);
            }
        } catch (Exception e) {
            System.err.println("[WalletSetup] Error al corregir sqlnet.ora: " + e.getMessage());
        }
    }

    private static String getenv(String key) {
        try { return System.getenv(key); } catch (Exception e) { return null; }
    }
}
