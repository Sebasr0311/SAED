import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.*;

/**
 * Migra todos los datos desde Oracle XE local hacia ATP cloud.
 * Uso: java MigrarDatosLocalToATP [--execute]
 *
 * Conexion local:  localhost:1521/xepdb1 / RESIDENCIAL
 * Conexion ATP:    residencial_high / RESIDENCIAL / Administrador2026 (via wallet)
 *
 * Sin --execute solo muestra el plan.
 * Con --execute borra datos en ATP y los reemplaza con los de local.
 */
public class MigrarDatosLocalToATP {

    static final String LOCAL_URL   = "jdbc:oracle:thin:@localhost:1521/xepdb1";
    static final String LOCAL_USER  = "RESIDENCIAL";
    static final String LOCAL_PASS  = "Residencial2024#";

    static final String ATP_URL     = "jdbc:oracle:thin:@residencial_high";
    static final String ATP_USER    = "RESIDENCIAL";
    static final String ATP_PASS    = "Administrador2026";
    static final String WALLET_PATH = "src/main/resources/wallet.zip";

    static final String[] TABLAS_INSERT = {
        "TIPOS_DOCUMENTO",
        "APARTAMENTOS",
        "PARQUEADEROS",
        "RESIDENTES",
        "TUTORES",
        "USUARIOS",
        "CONTRATOS",
        "CONTRATO_RESIDENTE",
        "CUOTAS_ARRIENDO",
        "PAGOS",
        "MULTAS",
        "VISITANTES",
        "VISITAS",
        "VEHICULOS_VISITA",
        "REGISTRO_VISITA",
        "QR_ACCESOS",
        "FRECUENTES_RESIDENTE",
        "REGISTROS_ACCESO",
        "BUZON",
        "QUEJAS_SUGERENCIAS",
        "ALERTAS_PAGO"
    };

    static final String[] TABLAS_DELETE = {
        "QUEJAS_SUGERENCIAS",
        "REGISTROS_ACCESO",
        "FRECUENTES_RESIDENTE",
        "REGISTRO_VISITA",
        "VEHICULOS_VISITA",
        "QR_ACCESOS",
        "BUZON",
        "ALERTAS_PAGO",
        "PAGOS",
        "CUOTAS_ARRIENDO",
        "MULTAS",
        "VISITAS",
        "VISITANTES",
        "CONTRATO_RESIDENTE",
        "CONTRATOS",
        "USUARIOS",
        "TUTORES",
        "RESIDENTES",
        "PARQUEADEROS",
        "APARTAMENTOS",
        "TIPOS_DOCUMENTO"
    };

    static final String[] SECUENCIAS = {
        "SEC_TIPOS_DOCUMENTO",
        "SEC_APARTAMENTOS",
        "SEC_PARQUEADEROS",
        "SEC_RESIDENTES",
        "SEC_TUTORES",
        "SEC_USUARIOS",
        "SEC_CONTRATOS",
        "SEC_CONTRATO_RESIDENTE",
        "SEC_CUOTAS_ARRIENDO",
        "SEC_PAGOS",
        "SEC_ALERTAS_PAGO",
        "SEC_MULTAS",
        "SEC_VISITAS",
        "SEC_QR_ACCESOS",
        "SEC_VISITANTES",
        "SEC_VEHICULOS_VISITA",
        "SEC_REGISTRO_VISITA",
        "SEC_FRECUENTES_RESIDENTE",
        "SEC_REGISTROS_ACCESO",
        "SEC_BUZON",
        "SEQ_QUEJAS_SUGERENCIAS"
    };

    static int totalInsertados = 0;
    static int totalErrores = 0;

    public static void main(String[] args) throws Exception {
        boolean execute = false;
        for (String a : args) {
            if (a.equals("--execute")) execute = true;
        }

        System.out.println("=== MIGRAR DATOS: LOCAL XE -> ATP ===");
        System.out.println("Local:  " + LOCAL_URL);
        System.out.println("ATP:    " + ATP_URL);
        if (!execute) {
            System.out.println("\n(Modo simulacion. Pas --execute para migrar realmente)");
        }

        System.out.println("\nConectando a BD local...");
        Class.forName("oracle.jdbc.OracleDriver");
        try (Connection local = DriverManager.getConnection(LOCAL_URL, LOCAL_USER, LOCAL_PASS)) {
            System.out.println("OK: Conectado a BD local");

            System.out.println("\nConectando a ATP...");
            setupWallet();
            try (Connection atp = DriverManager.getConnection(ATP_URL, ATP_USER, ATP_PASS)) {
                System.out.println("OK: Conectado a ATP");
                atp.setAutoCommit(false);

                if (execute) {
                    System.out.println("\n--- LIMPIANDO DATOS EN ATP ---");
                    limpiarATP(atp);

                    System.out.println("\n--- MIGRANDO DATOS ---");
                    for (String tabla : TABLAS_INSERT) {
                        migrarTabla(local, atp, tabla);
                    }

                    System.out.println("\n--- REINICIANDO SECUENCIAS ---");
                    reiniciarSecuencias(atp);

                    atp.commit();
                    System.out.println("\n=== MIGRACION COMPLETADA ===");
                    System.out.println("Insertados: " + totalInsertados + "  Errores: " + totalErrores);
                } else {
                    System.out.println("\nPlan de migracion:");
                    System.out.println("1. Deshabilitar FK en ATP");
                    System.out.println("2. Eliminar datos de " + TABLAS_DELETE.length + " tablas");
                    System.out.println("3. Insertar datos en " + TABLAS_INSERT.length + " tablas (desde local)");
                    System.out.println("4. Reiniciar " + SECUENCIAS.length + " secuencias");
                    System.out.println("5. Habilitar FK en ATP");
                    System.out.println("\nEjecuta: java MigrarDatosLocalToATP --execute");
                }
            }
        }
    }

    static void setupWallet() throws Exception {
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir"), "saed-migrate-wallet");
        Path walletFile = Paths.get(WALLET_PATH).toAbsolutePath();
        if (!Files.exists(walletFile)) {
            System.err.println("WARN: wallet.zip no encontrado en " + walletFile);
            System.err.println("WARN: Intentando con TNS_ADMIN del entorno...");
            String tns = System.getenv("TNS_ADMIN");
            if (tns != null && !tns.isEmpty()) {
                System.setProperty("oracle.net.tns_admin", tns);
                System.out.println("Usando TNS_ADMIN: " + tns);
            }
            return;
        }
        deleteDir(tmp);
        Files.createDirectories(tmp);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(walletFile))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path t = tmp.resolve(e.getName()).normalize();
                if (!t.startsWith(tmp)) continue;
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

        // Copy files up from network/admin
        Path netAdmin = tmp.resolve("network").resolve("admin");
        if (Files.exists(netAdmin)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(netAdmin)) {
                for (Path f : ds) {
                    Path dest = tmp.resolve(f.getFileName());
                    if (!Files.exists(dest)) Files.copy(f, dest);
                }
            }
        }

        // Fix sqlnet.ora
        Path sqlnet = tmp.resolve("sqlnet.ora");
        if (Files.exists(sqlnet)) {
            String content = Files.readString(sqlnet);
            content = content.replace("?/network/admin",
                tmp.toAbsolutePath().toString().replace("\\", "/") + "/network/admin");
            Files.writeString(sqlnet, content);
        }

        System.setProperty("oracle.net.tns_admin", tmp.toAbsolutePath().toString().replace("\\", "/"));
        System.out.println("Wallet extraido en: " + tmp);
    }

    static void limpiarATP(Connection atp) throws SQLException {
        try (Statement s = atp.createStatement()) {
            try { s.execute("UPDATE MULTAS SET id_mensaje = NULL WHERE id_mensaje IS NOT NULL"); } catch (Exception e) {}
            for (String t : TABLAS_DELETE) {
                try {
                    s.execute("DELETE FROM " + t);
                    System.out.println("  Limpiado: " + t);
                } catch (Exception e) {
                    System.out.println("  WARN al limpiar " + t + ": " + e.getMessage());
                }
            }
        }
    }

    static void migrarTabla(Connection local, Connection atp, String tabla) throws Exception {
        // Get column info from local
        DatabaseMetaData meta = local.getMetaData();
        List<String> cols = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, LOCAL_USER, tabla, null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                cols.add(colName);
            }
        }
        if (cols.isEmpty()) {
            System.out.println("  SKIP " + tabla + " (sin columnas)");
            return;
        }

        // Read all data from local (use getString to avoid Oracle-specific types)
        String selectSql = "SELECT * FROM " + tabla;
        List<List<Object>> filas = new ArrayList<>();
        try (Statement s = local.createStatement();
             ResultSet rs = s.executeQuery(selectSql)) {
            ResultSetMetaData rsmd = rs.getMetaData();
            while (rs.next()) {
                List<Object> fila = new ArrayList<>();
                for (int i = 1; i <= cols.size(); i++) {
                    int colType = rsmd.getColumnType(i);
                    if (colType == Types.TIMESTAMP || colType == Types.TIMESTAMP_WITH_TIMEZONE) {
                        fila.add(rs.getTimestamp(i));
                    } else if (colType == Types.DATE) {
                        fila.add(rs.getDate(i));
                    } else if (colType == Types.BLOB || colType == Types.CLOB) {
                        fila.add(rs.getString(i));
                    } else {
                        fila.add(rs.getObject(i));
                    }
                }
                filas.add(fila);
            }
        }

        if (filas.isEmpty()) {
            System.out.println("  " + tabla + ": 0 filas (sin datos)");
            return;
        }

        // Build INSERT SQL
        StringBuilder sql = new StringBuilder("INSERT INTO " + tabla + " (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(cols.get(i));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");

        // Insert into ATP
        int ok = 0;
        try (PreparedStatement ps = atp.prepareStatement(sql.toString())) {
            for (List<Object> fila : filas) {
                try {
                    for (int i = 0; i < fila.size(); i++) {
                        Object val = fila.get(i);
                        if (val == null) {
                            ps.setNull(i + 1, Types.NULL);
                        } else if (val instanceof Timestamp) {
                            ps.setTimestamp(i + 1, (Timestamp) val);
                        } else if (val instanceof java.sql.Date) {
                            ps.setDate(i + 1, (java.sql.Date) val);
                        } else {
                            ps.setObject(i + 1, val);
                        }
                    }
                    ps.executeUpdate();
                    ok++;
                } catch (Exception e) {
                    System.err.println("  ERROR " + tabla + " fila " + (ok + 1) + ": " + e.getMessage());
                    totalErrores++;
                }
            }
        }

        totalInsertados += ok;
        System.out.println("  " + tabla + ": " + ok + " filas insertadas" +
            (filas.size() != ok ? " (" + (filas.size() - ok) + " errores)" : ""));
    }

    static void reiniciarSecuencias(Connection atp) throws SQLException {
        for (String seq : SECUENCIAS) {
            try (Statement s = atp.createStatement()) {
                // Check if sequence exists
                try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM user_sequences WHERE sequence_name = '" + seq + "'")) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        System.out.println("  SKIP " + seq + " (no existe)");
                        continue;
                    }
                }
                // Get the corresponding table to find max ID
                String tabla = inferirTabla(seq);
                if (tabla != null) {
                    String pk = inferirPK(tabla);
                    if (pk != null) {
                        try (Statement s2 = atp.createStatement();
                             ResultSet rs = s2.executeQuery("SELECT NVL(MAX(" + pk + "), 0) + 1 FROM " + tabla)) {
                            if (rs.next()) {
                                long nextVal = rs.getLong(1);
                                s.execute("DROP SEQUENCE " + seq);
                                s.execute("CREATE SEQUENCE " + seq + " START WITH " + nextVal +
                                    " INCREMENT BY 1 NOCACHE NOCYCLE");
                                System.out.println("  " + seq + " -> " + nextVal);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("  WARN " + seq + ": " + e.getMessage());
            }
        }
    }

    static String inferirTabla(String seq) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("SEC_TIPOS_DOCUMENTO", "TIPOS_DOCUMENTO");
        map.put("SEC_APARTAMENTOS", "APARTAMENTOS");
        map.put("SEC_PARQUEADEROS", "PARQUEADEROS");
        map.put("SEC_RESIDENTES", "RESIDENTES");
        map.put("SEC_TUTORES", "TUTORES");
        map.put("SEC_USUARIOS", "USUARIOS");
        map.put("SEC_CONTRATOS", "CONTRATOS");
        map.put("SEC_CONTRATO_RESIDENTE", "CONTRATO_RESIDENTE");
        map.put("SEC_CUOTAS_ARRIENDO", "CUOTAS_ARRIENDO");
        map.put("SEC_PAGOS", "PAGOS");
        map.put("SEC_ALERTAS_PAGO", "ALERTAS_PAGO");
        map.put("SEC_MULTAS", "MULTAS");
        map.put("SEC_VISITAS", "VISITAS");
        map.put("SEC_QR_ACCESOS", "QR_ACCESOS");
        map.put("SEC_VISITANTES", "VISITANTES");
        map.put("SEC_VEHICULOS_VISITA", "VEHICULOS_VISITA");
        map.put("SEC_REGISTRO_VISITA", "REGISTRO_VISITA");
        map.put("SEC_FRECUENTES_RESIDENTE", "FRECUENTES_RESIDENTE");
        map.put("SEC_REGISTROS_ACCESO", "REGISTROS_ACCESO");
        map.put("SEC_BUZON", "BUZON");
        map.put("SEQ_QUEJAS_SUGERENCIAS", "QUEJAS_SUGERENCIAS");
        return map.get(seq);
    }

    static String inferirPK(String tabla) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("TIPOS_DOCUMENTO", "ID_TIPO_DOC");
        map.put("APARTAMENTOS", "ID_APARTAMENTO");
        map.put("PARQUEADEROS", "ID_PARQUEADERO");
        map.put("RESIDENTES", "ID_RESIDENTE");
        map.put("TUTORES", "ID_TUTOR");
        map.put("USUARIOS", "ID_USUARIO");
        map.put("CONTRATOS", "ID_CONTRATO");
        map.put("CONTRATO_RESIDENTE", "ID_CONTRATO_RES");
        map.put("CUOTAS_ARRIENDO", "ID_CUOTA");
        map.put("PAGOS", "ID_PAGO");
        map.put("ALERTAS_PAGO", "ID_ALERTA");
        map.put("MULTAS", "ID_MULTA");
        map.put("VISITAS", "ID_VISITA");
        map.put("QR_ACCESOS", "ID_QR");
        map.put("VISITANTES", "ID_VISITANTE");
        map.put("VEHICULOS_VISITA", "ID_VEHICULO_VISITA");
        map.put("REGISTRO_VISITA", "ID_REGISTRO_VISITA");
        map.put("FRECUENTES_RESIDENTE", "ID_FRECUENTE");
        map.put("REGISTROS_ACCESO", "ID_ACCESO");
        map.put("BUZON", "ID_MENSAJE");
        map.put("QUEJAS_SUGERENCIAS", "ID_QUEJA");
        return map.get(tabla);
    }

    static void deleteDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception e) {} });
        }
    }
}
