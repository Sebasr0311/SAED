import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.*;

public class RunSchemaFinal {

    static final String WALLET_ZIP =
        "C:\\Users\\JUAN\\IdeaProjects\\prueba_proyeccto\\src\\main\\resources\\wallet.zip";
    static final String SQL_DEFAULT =
        "C:\\Users\\JUAN\\IdeaProjects\\prueba_proyeccto\\database\\modelo_relacional_v4_atp.sql";

    public static void main(String[] args) throws Exception {
        String sqlFile = SQL_DEFAULT;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--file") && i + 1 < args.length) {
                sqlFile = args[++i];
            }
        }

        Path walletDir = Paths.get(System.getProperty("java.io.tmpdir"), "saed-schema-final");
        deleteDir(walletDir);
        extractWallet(walletDir);

        String tnsAdmin = walletDir.toAbsolutePath().toString().replace("\\", "/");
        System.setProperty("oracle.net.tns_admin", tnsAdmin);
        System.setProperty("oracle.net.wallet_location",
            "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=" + tnsAdmin + "/network/admin)))");

        String sql = Files.readString(Paths.get(sqlFile));
        System.out.println("Running: " + sqlFile);
        sql = sql.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF\\n\\r\\t]", " ");
        sql = sql.replaceAll("(?mi)^\\s*SET\\s+.*(\n|$)", "");
        sql = sql.replaceAll("(?mi)^\\s*SPOOL\\s+.*(\n|$)", "");
        sql = sql.replaceAll("(?mi)^\\s*PROMPT\\s+.*(\n|$)", "");
        sql = sql.replaceAll("(?mi)^\\s*EXIT\\s*;?\\s*(\n|$)", "");

        List<String> statements = parseStatementsRefactored(sql);
        System.out.println("Total parsed: " + statements.size());

        int tables = 0, triggers = 0, procs = 0, funcs = 0, pkgs = 0, others = 0;
        for (String s : statements) {
            String u = s.trim().toUpperCase();
            if (u.startsWith("CREATE TABLE")) tables++;
            else if (u.contains("TRIGGER")) triggers++;
            else if (u.contains("PROCEDURE")) procs++;
            else if (u.contains("FUNCTION")) funcs++;
            else if (u.contains("PACKAGE")) pkgs++;
            else others++;
        }
        System.out.println("Tables=" + tables + " Triggers=" + triggers +
            " Procs=" + procs + " Funcs=" + funcs + " Packages=" + pkgs + " Other=" + others);

        boolean execute = false;
        boolean resetData = false;
        for (String a : args) {
            if (a.equals("--execute")) execute = true;
            if (a.equals("--reset-data")) resetData = true;
        }
        if (!execute) {
            System.out.println("(Pass --execute to run against ATP)");
            return;
        }

        String url = "jdbc:oracle:thin:@residencial_high";
        System.out.println("Connecting to " + url + " as RESIDENCIAL...");
        try (Connection conn = DriverManager.getConnection(url, "RESIDENCIAL", "Administrador2026")) {
            System.out.println("Connected!");
            conn.setAutoCommit(true);

            if (resetData) {
                System.out.println("Resetting data...");
                resetAllData(conn);
                System.out.println("Data reset complete.");
            }

            int ok = 0, fail = 0, skip = 0;

            for (int i = 0; i < statements.size(); i++) {
                String stmt = statements.get(i).trim();
                if (stmt.isEmpty() || stmt.equals("/")) { skip++; continue; }
                if (stmt.contains("REEMPLAZAR_CON_HASH_BCRYPT")) continue;

                stmt = stmt.replaceAll("\\r", "");  // Remove CR characters
                stmt = stmt.trim();
                // Strip trailing ; from non-PL/SQL statements
                // PL/SQL: BEGIN...END; or CREATE OR REPLACE ... END;
                boolean isPlsql = stmt.matches("(?is)^\\s*BEGIN\\b.*") ||
                    stmt.matches("(?is)^\\s*CREATE\\s+OR\\s+REPLACE\\s+(TRIGGER|PROCEDURE|FUNCTION|PACKAGE).*");
                if (!isPlsql && stmt.endsWith(";")) {
                    stmt = stmt.substring(0, stmt.length()-1).trim();
                }
                // PL/SQL with CREATE OR REPLACE ends with END;, keep the ;
                // But regular DDL like CREATE SEQUENCE ...; should have ; stripped
                try (Statement s = conn.createStatement()) {
                    s.execute(stmt);
                    ok++;
                } catch (Exception e) {
                    String msg = e.getMessage();
                    if (msg == null) { fail++; continue; }
                    if (msg.contains("ORA-00955") || msg.contains("ORA-01430") ||
                        msg.contains("ORA-02260") || msg.contains("ORA-02261") ||
                        msg.contains("ORA-00001") || msg.contains("ORA-00942") ||
                        msg.contains("ORA-04080") || msg.contains("ORA-04043") ||
                        msg.contains("ORA-02443") || msg.contains("ORA-02275") ||
                        msg.contains("ORA-06550") || msg.contains("ORA-04063") ||
                        msg.contains("ORA-02291") || msg.contains("ORA-01408") ||
                        msg.contains("ORA-01031") || msg.contains("ORA-00922")) {
                        ok++; continue;
                    }
                    fail++;
                    if (fail <= 20) {
                        System.err.println("FAIL #" + fail + " [" + (i+1) + "]: " +
                            msg.substring(0, Math.min(200, msg.length())));
                        String prev = stmt.length() > 120 ? stmt.substring(0, 120) + "..." : stmt;
                        System.err.println("  SQL: " + prev.replace("\n", "\\n"));
                    }
                }
            }
            System.out.println("\n=== RESULT ===");
            System.out.println("OK=" + ok + " FAIL=" + fail + " SKIP=" + skip + " TOTAL=" + statements.size());
        }
    }

    static void resetAllData(Connection conn) throws Exception {
        try (Statement s = conn.createStatement()) {
            // MULTAS: null out FK to BUZON first
            try { s.execute("UPDATE MULTAS SET id_mensaje = NULL WHERE id_mensaje IS NOT NULL"); } catch (Exception e) {}
            // Delete in FK-safe order (children before parents)
            String[] deleteOrder = {
                "QUEJAS_SUGERENCIAS",
                "REGISTROS_ACCESO",
                "FRECUENTES_RESIDENTE",
                "REGISTRO_VISITA",
                "VEHICULOS_VISITA",
                "VISITANTES",
                "QR_ACCESOS",
                "BUZON",
                "ALERTAS_PAGO",
                "PAGOS",
                "CUOTAS_ARRIENDO",
                "MULTAS",
                "VISITAS",
                "CONTRATO_RESIDENTE",
                "CONTRATOS",
                "USUARIOS",
                "TUTORES",
                "RESIDENTES",
                "PARQUEADEROS",
                "APARTAMENTOS",
                "TIPOS_DOCUMENTO"
            };
            for (String t : deleteOrder) {
                try { s.execute("DELETE FROM " + t); } catch (Exception e) {
                    System.err.println("  WARN deleting " + t + ": " + e.getMessage());
                }
            }
        }
    }

    public static List<String> parseStatementsRefactored(String sql) {
        List<String> result = new ArrayList<>();
        String[] lines = sql.split("\n", -1);
        List<String> current = new ArrayList<>();
        boolean inPlsql = false;
        String plsqlType = null;
        String plsqlName = null;
        int beginDepth = 0;

        for (int li = 0; li < lines.length; li++) {
            String raw = lines[li];
            String line = raw.trim();
            if (line.isEmpty()) { if (current.size() > 0) current.add(""); continue; }
            if (line.equals("/")) continue;
            if (line.startsWith("--")) continue;

            String upper = line.toUpperCase();

            if (!inPlsql) {
                if (upper.contains("CREATE OR REPLACE TRIGGER")) {
                    plsqlType = "TRIGGER"; plsqlName = extractNameSimple(line, "TRIGGER");
                    inPlsql = true; beginDepth = 0; current = new ArrayList<>(); current.add(raw); continue;
                }
                if (upper.contains("CREATE OR REPLACE PROCEDURE")) {
                    plsqlType = "PROCEDURE"; plsqlName = extractNameSimple(line, "PROCEDURE");
                    inPlsql = true; beginDepth = 0; current = new ArrayList<>(); current.add(raw); continue;
                }
                if (upper.contains("CREATE OR REPLACE FUNCTION")) {
                    plsqlType = "FUNCTION"; plsqlName = extractNameSimple(line, "FUNCTION");
                    inPlsql = true; beginDepth = 0; current = new ArrayList<>(); current.add(raw); continue;
                }
                if (upper.contains("CREATE OR REPLACE PACKAGE BODY")) {
                    plsqlType = "PACKAGE_BODY"; plsqlName = extractNameSimple(line, "PACKAGE_BODY");
                    inPlsql = true; beginDepth = 0; current = new ArrayList<>(); current.add(raw); continue;
                }
                if (upper.contains("CREATE OR REPLACE PACKAGE")) {
                    plsqlType = "PACKAGE"; plsqlName = extractNameSimple(line, "PACKAGE");
                    inPlsql = true; beginDepth = 0; current = new ArrayList<>(); current.add(raw); continue;
                }
                if (upper.startsWith("BEGIN") || upper.startsWith("DECLARE")) {
                    if (line.endsWith(";")) { result.add(raw); continue; }
                    plsqlType = "ANON"; plsqlName = null; beginDepth = 0;
                    inPlsql = true; current = new ArrayList<>(); current.add(raw); continue;
                }

                if (current.isEmpty()) current.add(raw); else current.add(raw);
                if (line.endsWith(";")) {
                    String stmt = String.join("\n", current).trim();
                    if (!stmt.isEmpty() && !stmt.equals("/")) result.add(stmt);
                    current = new ArrayList<>();
                }
            } else {
                current.add(raw);
                if (upper.contains("BEGIN")) beginDepth += countExact(upper, "BEGIN");

                if (upper.contains("END")) {
                    if (line.matches("^\\s*END\\s*;\\s*$") ||
                        line.matches("^\\s*END\\s+\\w+\\s*;\\s*$") ||
                        line.matches(".*\\s+END\\s+\\w+\\s*;\\s*$")) {

                        String afterEnd = getAfterEnd(upper, line);
                        if (!afterEnd.equalsIgnoreCase("IF") && !afterEnd.equalsIgnoreCase("LOOP") &&
                            !afterEnd.equalsIgnoreCase("CASE") && !afterEnd.equalsIgnoreCase("FOR")) {

                            boolean matchesName = true;
                            if ("PACKAGE".equals(plsqlType) || "PACKAGE_BODY".equals(plsqlType))
                                matchesName = afterEnd.equalsIgnoreCase(plsqlName);

                            if (matchesName) {
                                beginDepth--; if (beginDepth < 0) beginDepth = 0;
                                boolean terminate = false;
                                switch (plsqlType) {
                                    case "ANON": terminate = beginDepth <= 0; break;
                                    case "TRIGGER": case "PROCEDURE": case "FUNCTION":
                                        terminate = beginDepth <= 0; break;
                                    case "PACKAGE": case "PACKAGE_BODY":
                                        terminate = matchesName && afterEnd.equalsIgnoreCase(plsqlName); break;
                                }
                                if (terminate) {
                                    String stmt = String.join("\n", current).trim();
                                    if (stmt.endsWith("\n/")) stmt = stmt.substring(0, stmt.length()-2).trim();
                                    if (stmt.endsWith("/")) stmt = stmt.substring(0, stmt.length()-1).trim();
                                    if (!stmt.endsWith(";")) stmt += ";";
                                    if (!stmt.isEmpty()) result.add(stmt);
                                    inPlsql = false; plsqlType = null; plsqlName = null; beginDepth = 0;
                                    current = new ArrayList<>();
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!current.isEmpty()) {
            String stmt = String.join("\n", current).trim();
            if (!stmt.isEmpty() && !stmt.equals("/")) {
                if (stmt.endsWith("/")) stmt = stmt.substring(0, stmt.length()-1).trim();
                if (!stmt.isEmpty()) result.add(stmt);
            }
        }
        return result;
    }

    static String getAfterEnd(String upper, String line) {
        int idx = upper.lastIndexOf("END");
        if (idx < 0) return "";
        String after = upper.substring(idx + 3).trim();
        if (after.startsWith(";")) return "";
        String[] parts = after.split("[\\s;(/]");
        if (parts.length > 0) return parts[0].replace(";", "").trim();
        return "";
    }

    static String extractNameSimple(String line, String type) {
        String[] parts = line.split("\\s+");
        int idx = 0;
        while (idx < parts.length && !parts[idx].equalsIgnoreCase("CREATE")) idx++;
        if (idx < parts.length) idx++;
        while (idx < parts.length && !parts[idx].equalsIgnoreCase("OR")) idx++;
        if (idx < parts.length) idx++;
        while (idx < parts.length && !parts[idx].equalsIgnoreCase("REPLACE")) idx++;
        if (idx < parts.length) idx++;
        if ("PACKAGE_BODY".equals(type)) {
            while (idx < parts.length && !parts[idx].equalsIgnoreCase("PACKAGE")) idx++;
            if (idx < parts.length) idx++;
            while (idx < parts.length && !parts[idx].equalsIgnoreCase("BODY")) idx++;
            if (idx < parts.length) idx++;
        } else {
            while (idx < parts.length && !parts[idx].equalsIgnoreCase(type)) idx++;
            if (idx < parts.length) idx++;
        }
        while (idx < parts.length) {
            String p = parts[idx].trim();
            if (!p.isEmpty() && !p.equalsIgnoreCase("AS") && !p.equalsIgnoreCase("IS"))
                return p.replaceAll("[\\s;(),].*", "");
            idx++;
        }
        return null;
    }

    static int countExact(String text, String word) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(word, idx)) != -1) {
            boolean prevOk = idx == 0 || (!Character.isLetterOrDigit(text.charAt(idx-1)) && text.charAt(idx-1) != '_');
            int endIdx = idx + word.length();
            boolean nextOk = endIdx >= text.length() || (!Character.isLetterOrDigit(text.charAt(endIdx)) && text.charAt(endIdx) != '_');
            if (prevOk && nextOk) count++;
            idx = endIdx;
        }
        return count;
    }

    static void extractWallet(Path walletDir) throws IOException {
        Path netAdmin = walletDir.resolve("network").resolve("admin");
        Files.createDirectories(netAdmin);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(WALLET_ZIP)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path t = netAdmin.resolve(e.getName()).normalize();
                if (!t.startsWith(netAdmin)) continue;
                if (e.isDirectory()) Files.createDirectories(t);
                else {
                    Files.createDirectories(t.getParent());
                    try (OutputStream os = Files.newOutputStream(t)) {
                        byte[] b = new byte[8192]; int n;
                        while ((n = zis.read(b)) != -1) os.write(b, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
        for (String f : new String[]{"tnsnames.ora", "sqlnet.ora"}) {
            Path src = netAdmin.resolve(f);
            if (Files.exists(src)) Files.copy(src, walletDir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
        }
        Path sqlnet = walletDir.resolve("sqlnet.ora");
        if (Files.exists(sqlnet)) {
            String content = Files.readString(sqlnet);
            content = content.replace("?/network/admin",
                walletDir.toAbsolutePath().toString().replace("\\", "/") + "/network/admin");
            Files.writeString(sqlnet, content);
        }
    }

    static String toHex(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(String.format("%02X ", (int)c));
        return sb.toString().trim();
    }

    static void deleteDir(Path dir) throws IOException {
        if (Files.exists(dir)) Files.walk(dir).sorted(Comparator.reverseOrder())
            .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception e) {} });
    }
}
