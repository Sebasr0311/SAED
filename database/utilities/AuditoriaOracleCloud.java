import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AuditoriaOracleCloud {

    private static final String ATP_URL = "jdbc:oracle:thin:@residencial_high";
    private static final String ATP_USER = "RESIDENCIAL";
    private static final String ATP_PASS = "Administrador2026";
    private static final String WALLET_PATH = "src/main/resources/wallet.zip";
    private static final String OUTPUT_FILE = "C:/Users/JUAN/Downloads/auditoria_oracle_cloud.txt";

    private static final StringBuilder report = new StringBuilder();
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   INICIANDO AUDITORIA COMPLETA DE ORACLE CLOUD  ");
        System.out.println("=================================================");

        try {
            setupWallet();
            Class.forName("oracle.jdbc.OracleDriver");

            System.out.println("Conectando a Oracle ATP Cloud (" + ATP_URL + ")...");
            try (Connection conn = DriverManager.getConnection(ATP_URL, ATP_USER, ATP_PASS)) {
                System.out.println("¡Conexion exitosa! Ejecutando auditoria exhaustiva...");
                generateAuditReport(conn);
            }

            // Guardar reporte en Downloads
            File outFile = new File(OUTPUT_FILE);
            Files.writeString(outFile.toPath(), report.toString(), StandardCharsets.UTF_8);
            System.out.println("\n=================================================");
            System.out.println(" AUDITORIA COMPLETADA CON EXITO");
            System.out.println(" Archivo generado: " + outFile.getAbsolutePath());
            System.out.println(" Tamano: " + outFile.length() + " bytes");
            System.out.println("=================================================");

        } catch (Exception e) {
            System.err.println("ERROR DURANTE LA AUDITORIA: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void setupWallet() throws Exception {
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir"), "saed-audit-wallet");
        Path walletFile = Paths.get(WALLET_PATH).toAbsolutePath();
        if (!Files.exists(walletFile)) {
            walletFile = Paths.get("C:/Users/JUAN/IdeaProjects/prueba_proyeccto/src/main/resources/wallet.zip");
        }

        if (!Files.exists(walletFile)) {
            System.err.println("WARN: wallet.zip no encontrado en " + walletFile);
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
                if (e.isDirectory()) {
                    Files.createDirectories(t);
                } else {
                    Files.createDirectories(t.getParent());
                    try (OutputStream os = Files.newOutputStream(t)) {
                        byte[] b = new byte[8192];
                        int n;
                        while ((n = zis.read(b)) != -1) os.write(b, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }

        // Copy files up from network/admin if present
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
        System.out.println("Oracle Wallet configurado en: " + tmp);
    }

    private static void deleteDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    private static void printHeader(String title) {
        String bar = "=".repeat(90);
        report.append("\n").append(bar).append("\n");
        report.append("  ").append(title).append("\n");
        report.append(bar).append("\n\n");
    }

    private static void printSubHeader(String title) {
        String bar = "-".repeat(90);
        report.append("\n").append(bar).append("\n");
        report.append("  ").append(title).append("\n");
        report.append(bar).append("\n");
    }

    private static void generateAuditReport(Connection conn) {
        report.append("==========================================================================================\n");
        report.append("           REPORTE DE AUDITORIA COMPLETA - ORACLE CLOUD INFRASTRUCTURE (ATP)             \n");
        report.append("           PROYECTO: SAED (SISTEMA DE ADMINISTRACION DE EDIFICIOS)                        \n");
        report.append("           FECHA DE GENERACION: ").append(SDF.format(new Date())).append("\n");
        report.append("==========================================================================================\n\n");

        auditInstanceAndDatabase(conn);
        auditUserAndPrivileges(conn);
        auditStorageAndTablespaces(conn);
        auditObjectSummary(conn);
        auditTablesAndColumns(conn);
        auditConstraintsAndRelations(conn);
        auditIndexes(conn);
        auditViews(conn);
        auditSequences(conn);
        auditTriggers(conn);
        auditCodeObjects(conn);
        auditDataInventoryAndRowCounts(conn);
        auditJobsAndScheduler(conn);
        auditHealthAndIntegrityChecks(conn);
    }

    private static void auditInstanceAndDatabase(Connection conn) {
        printHeader("1. INFORMACION DE LA INSTANCIA, BASE DE DATOS Y ENTORNO CLOUD");

        queryAndFormat("Version de Oracle Database y Banner:", conn,
                "SELECT banner_full FROM v$version WHERE ROWNUM = 1",
                "SELECT banner FROM v$version");

        queryAndFormat("Parametros Globales de la Base de Datos (SYS_CONTEXT):", conn,
                "SELECT SYS_CONTEXT('USERENV', 'DB_NAME') AS DB_NAME, " +
                        "SYS_CONTEXT('USERENV', 'DB_UNIQUE_NAME') AS DB_UNIQUE_NAME, " +
                        "SYS_CONTEXT('USERENV', 'CON_NAME') AS CONTAINER_PDB, " +
                        "SYS_CONTEXT('USERENV', 'SESSION_USER') AS CURRENT_USER, " +
                        "SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS CURRENT_SCHEMA, " +
                        "SYS_CONTEXT('USERENV', 'SERVER_HOST') AS SERVER_HOST, " +
                        "SYS_CONTEXT('USERENV', 'SERVICE_NAME') AS SERVICE_NAME, " +
                        "SYS_CONTEXT('USERENV', 'IP_ADDRESS') AS CLIENT_IP, " +
                        "SYS_CONTEXT('USERENV', 'HOST') AS CLIENT_MACHINE, " +
                        "SYS_CONTEXT('USERENV', 'OS_USER') AS CLIENT_OS_USER " +
                        "FROM DUAL");

        queryAndFormat("Configuracion NLS (Charset, Timezone, Idioma):", conn,
                "SELECT parameter, value FROM nls_database_parameters WHERE parameter IN " +
                        "('NLS_CHARACTERSET', 'NLS_NCHAR_CHARACTERSET', 'NLS_TERRITORY', 'NLS_LANGUAGE', " +
                        "'NLS_DATE_FORMAT', 'NLS_TIMESTAMP_FORMAT', 'NLS_TIMESTAMP_TZ_FORMAT', 'NLS_RDBMS_VERSION') ORDER BY parameter");

        queryAndFormat("Metricas de Base de Datos y Sesion Actual:", conn,
                "SELECT SYSDATE AS DB_DATE_LOCAL, SYSTIMESTAMP AS DB_TIMESTAMP, " +
                        "DBTIMEZONE AS DB_TIMEZONE, SESSIONTIMEZONE AS SESSION_TIMEZONE FROM DUAL");
    }

    private static void auditUserAndPrivileges(Connection conn) {
        printHeader("2. USUARIOS, ROLES Y PRIVILEGIOS DE SEGURIDAD");

        queryAndFormat("Detalles del Usuario Actual (USER_USERS):", conn,
                "SELECT username, user_id, account_status, default_tablespace, temporary_tablespace, " +
                        "created, authentication_type FROM user_users");

        queryAndFormat("Roles Asignados al Usuario (USER_ROLE_PRIVS):", conn,
                "SELECT granted_role, admin_option, default_role, os_grant FROM user_role_privs ORDER BY granted_role");

        queryAndFormat("Privilegios de Sistema (USER_SYS_PRIVS):", conn,
                "SELECT privilege, admin_option FROM user_sys_privs ORDER BY privilege");

        queryAndFormat("Privilegios sobre Objetos Otorgados/Recibidos (USER_TAB_PRIVS):", conn,
                "SELECT table_name, privilege, grantor, grantable FROM user_tab_privs ORDER BY table_name, privilege");

        queryAndFormat("Usuarios y Esquemas Registrados en la PDB (ALL_USERS):", conn,
                "SELECT username, user_id, created, oracle_maintained, common FROM all_users ORDER BY username");
    }

    private static void auditStorageAndTablespaces(Connection conn) {
        printHeader("3. ALMACENAMIENTO, TABLESPACES Y SEGMENTOS");

        queryAndFormat("Tablespaces Accesibles (USER_TABLESPACES):", conn,
                "SELECT tablespace_name, block_size, initial_extent, next_extent, status, contents, logging FROM user_tablespaces");

        queryAndFormat("Uso de Almacenamiento por Tipo de Objeto (USER_SEGMENTS):", conn,
                "SELECT segment_type, COUNT(*) AS TOTAL_OBJETOS, " +
                        "ROUND(SUM(bytes)/1024/1024, 2) AS TOTAL_MB, " +
                        "SUM(blocks) AS TOTAL_BLOQUES, " +
                        "SUM(extents) AS TOTAL_EXTENTS " +
                        "FROM user_segments GROUP BY segment_type ORDER BY TOTAL_MB DESC");

        queryAndFormat("Top 25 Segmentos con Mayor Consumo de Espacio:", conn,
                "SELECT segment_name, segment_type, tablespace_name, " +
                        "ROUND(bytes/1024/1024, 3) AS TAMANO_MB, blocks, extents " +
                        "FROM user_segments ORDER BY bytes DESC FETCH FIRST 25 ROWS ONLY");
    }

    private static void auditObjectSummary(Connection conn) {
        printHeader("4. RESUMEN DE OBJETOS EN EL ESQUEMA (USER_OBJECTS)");

        queryAndFormat("Conteo de Objetos por Tipo y Estado:", conn,
                "SELECT object_type, status, COUNT(*) AS CANTIDAD " +
                        "FROM user_objects GROUP BY object_type, status ORDER BY object_type, status");

        queryAndFormat("Listado General de Todos los Objetos del Esquema:", conn,
                "SELECT object_name, object_type, status, created, last_ddl_time, temporary " +
                        "FROM user_objects ORDER BY object_type, object_name");
    }

    private static void auditTablesAndColumns(Connection conn) {
        printHeader("5. DETALLE ESTRUCTURAL DE TABLAS Y COLUMNAS");

        List<String> tables = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT table_name, num_rows, tablespace_name, status, last_analyzed FROM user_tables ORDER BY table_name")) {
            while (rs.next()) {
                String tName = rs.getString("TABLE_NAME");
                tables.add(tName);
                report.append("------------------------------------------------------------------------------------------\n");
                report.append(String.format(" TABLA: %-30s | Estado: %-8s | Tablespace: %-15s | Filas (Stats): %s\n",
                        tName, rs.getString("STATUS"), rs.getString("TABLESPACE_NAME"), rs.getString("NUM_ROWS")));
                report.append("------------------------------------------------------------------------------------------\n");

                // Columnas
                try (PreparedStatement colSt = conn.prepareStatement(
                        "SELECT column_name, data_type, data_length, data_precision, data_scale, nullable, data_default " +
                                "FROM user_tab_columns WHERE table_name = ? ORDER BY column_id")) {
                    colSt.setString(1, tName);
                    try (ResultSet colRs = colSt.executeQuery()) {
                        report.append(String.format("   %-30s %-20s %-10s %-25s\n", "COLUMNA", "TIPO DE DATO", "NULLABLE", "VALOR DEFAULT"));
                        report.append(String.format("   %-30s %-20s %-10s %-25s\n", "-------", "------------", "--------", "-------------"));
                        while (colRs.next()) {
                            String colName = colRs.getString("COLUMN_NAME");
                            String dataType = colRs.getString("DATA_TYPE");
                            int len = colRs.getInt("DATA_LENGTH");
                            int prec = colRs.getInt("DATA_PRECISION");
                            int scale = colRs.getInt("DATA_SCALE");
                            String nullStr = colRs.getString("NULLABLE");
                            String defVal = colRs.getString("DATA_DEFAULT");

                            String fullType = dataType;
                            if ("VARCHAR2".equals(dataType) || "CHAR".equals(dataType)) {
                                fullType += "(" + len + ")";
                            } else if ("NUMBER".equals(dataType)) {
                                if (prec > 0) fullType += "(" + prec + (scale > 0 ? "," + scale : "") + ")";
                            }

                            report.append(String.format("   %-30s %-20s %-10s %-25s\n",
                                    colName, fullType, ("Y".equals(nullStr) ? "NULL" : "NOT NULL"),
                                    (defVal != null ? defVal.trim() : "-")));
                        }
                    }
                }
                report.append("\n");
            }
        } catch (Exception e) {
            report.append("Error al auditar tablas: ").append(e.getMessage()).append("\n");
        }
    }

    private static void auditConstraintsAndRelations(Connection conn) {
        printHeader("6. RESTRICCIONES (CONSTRAINTS), CLAVES PRIMARIAS, FORANEAS Y UNICAS");

        queryAndFormat("Claves Primarias (PK) y Claves Unicas (UK):", conn,
                "SELECT c.table_name, c.constraint_name, c.constraint_type, " +
                        "LISTAGG(cc.column_name, ', ') WITHIN GROUP (ORDER BY cc.position) AS COLUMNAS, " +
                        "c.status, c.validated " +
                        "FROM user_constraints c " +
                        "JOIN user_cons_columns cc ON c.constraint_name = cc.constraint_name " +
                        "WHERE c.constraint_type IN ('P', 'U') " +
                        "GROUP BY c.table_name, c.constraint_name, c.constraint_type, c.status, c.validated " +
                        "ORDER BY c.table_name, c.constraint_type");

        queryAndFormat("Claves Foraneas (FK - Integridad Referencial):", conn,
                "SELECT c.table_name AS TABLA_ORIGEN, c.constraint_name AS FK_NAME, " +
                        "LISTAGG(cc.column_name, ', ') WITHIN GROUP (ORDER BY cc.position) AS COLUMNAS_ORIGEN, " +
                        "r.table_name AS TABLA_DESTINO_PK, c.delete_rule, c.status " +
                        "FROM user_constraints c " +
                        "JOIN user_cons_columns cc ON c.constraint_name = cc.constraint_name " +
                        "LEFT JOIN user_constraints r ON c.r_constraint_name = r.constraint_name " +
                        "WHERE c.constraint_type = 'R' " +
                        "GROUP BY c.table_name, c.constraint_name, r.table_name, c.delete_rule, c.status " +
                        "ORDER BY c.table_name, c.constraint_name");

        queryAndFormat("Restricciones Check (CK):", conn,
                "SELECT table_name, constraint_name, search_condition_vc, status " +
                        "FROM user_constraints WHERE constraint_type = 'C' AND search_condition_vc NOT LIKE '%IS NOT NULL%' " +
                        "ORDER BY table_name, constraint_name");
    }

    private static void auditIndexes(Connection conn) {
        printHeader("7. INDICES Y ESTRUCTURAS DE ACCESO");

        queryAndFormat("Indices Definidos y Columnas Indexadas:", conn,
                "SELECT i.table_name, i.index_name, i.index_type, i.uniqueness, i.status, " +
                        "LISTAGG(ic.column_name || ' ' || ic.descend, ', ') WITHIN GROUP (ORDER BY ic.column_position) AS COLUMNAS " +
                        "FROM user_indexes i " +
                        "JOIN user_ind_columns ic ON i.index_name = ic.index_name " +
                        "GROUP BY i.table_name, i.index_name, i.index_type, i.uniqueness, i.status " +
                        "ORDER BY i.table_name, i.index_name");

        queryAndFormat("Diagnostico: Claves Foraneas SIN INDICE (Riesgo de Bloqueos de Tabla):", conn,
                "SELECT c.table_name, c.constraint_name AS FK_NAME, " +
                        "LISTAGG(cc.column_name, ', ') WITHIN GROUP (ORDER BY cc.position) AS FK_COLUMNS " +
                        "FROM user_constraints c " +
                        "JOIN user_cons_columns cc ON c.constraint_name = cc.constraint_name " +
                        "WHERE c.constraint_type = 'R' " +
                        "AND NOT EXISTS ( " +
                        "  SELECT 1 FROM user_ind_columns ic " +
                        "  WHERE ic.table_name = c.table_name " +
                        "  AND ic.column_name = cc.column_name " +
                        "  AND ic.column_position = 1 " +
                        ") " +
                        "GROUP BY c.table_name, c.constraint_name " +
                        "ORDER BY c.table_name");
    }

    private static void auditViews(Connection conn) {
        printHeader("8. VISTAS DEL ESQUEMA");

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT view_name, read_only, text_length, text FROM user_views ORDER BY view_name")) {
            while (rs.next()) {
                String vName = rs.getString("VIEW_NAME");
                String readOnly = rs.getString("READ_ONLY");
                String text = rs.getString("TEXT");

                report.append("VISTA: ").append(vName).append(" (Solo lectura: ").append(readOnly).append(")\n");
                report.append("DEFINICION SQL:\n");
                report.append(text != null ? text.trim() : "(Sin definicion)").append("\n");
                report.append("-".repeat(80)).append("\n\n");
            }
        } catch (Exception e) {
            report.append("Error al auditar vistas: ").append(e.getMessage()).append("\n");
        }
    }

    private static void auditSequences(Connection conn) {
        printHeader("9. SECUENCIAS (ESTADO Y VALORES ACTUALES)");

        queryAndFormat("Inventario de Secuencias (USER_SEQUENCES):", conn,
                "SELECT sequence_name, min_value, max_value, increment_by, cycle_flag, order_flag, cache_size, last_number " +
                        "FROM user_sequences ORDER BY sequence_name");
    }

    private static void auditTriggers(Connection conn) {
        printHeader("10. TRIGGERS (DISPARADORES PL/SQL)");

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT trigger_name, trigger_type, triggering_event, table_name, status, trigger_body " +
                     "FROM user_triggers ORDER BY table_name, trigger_name")) {
            while (rs.next()) {
                String tName = rs.getString("TRIGGER_NAME");
                String tType = rs.getString("TRIGGER_TYPE");
                String tEvent = rs.getString("TRIGGERING_EVENT");
                String tabName = rs.getString("TABLE_NAME");
                String status = rs.getString("STATUS");
                String body = rs.getString("TRIGGER_BODY");

                report.append(String.format("TRIGGER: %-30s | Tabla: %-25s | Tipo: %-20s | Evento: %-15s | Estado: %s\n",
                        tName, tabName, tType, tEvent, status));
                report.append("CODIGO DEL TRIGGER:\n");
                report.append(body != null ? body.trim() : "(Vacio)").append("\n");
                report.append("-".repeat(80)).append("\n\n");
            }
        } catch (Exception e) {
            report.append("Error al auditar triggers: ").append(e.getMessage()).append("\n");
        }
    }

    private static void auditCodeObjects(Connection conn) {
        printHeader("11. CODIGO PL/SQL: PROCEDIMIENTOS, FUNCIONES Y PAQUETES");

        queryAndFormat("Listado de Procedimientos, Funciones y Paquetes:", conn,
                "SELECT name, type, " +
                        "(SELECT status FROM user_objects WHERE object_name = s.name AND object_type = s.type) AS STATUS, " +
                        "MAX(line) AS LINEAS_CODIGO " +
                        "FROM user_source s GROUP BY name, type ORDER BY type, name");

        // Detalle de argumentos / parametros
        queryAndFormat("Parametros y Firmas de Subprogramas (USER_ARGUMENTS):", conn,
                "SELECT object_name, package_name, argument_name, position, sequence, data_type, in_out " +
                        "FROM user_arguments WHERE object_name IS NOT NULL ORDER BY package_name, object_name, position");

        // Codigo fuente completo
        printSubHeader("CODIGO FUENTE COMPLETO DE OBJETOS PL/SQL:");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, type, line, text FROM user_source ORDER BY type, name, line")) {
            String currentObj = "";
            while (rs.next()) {
                String name = rs.getString("NAME");
                String type = rs.getString("TYPE");
                int line = rs.getInt("LINE");
                String text = rs.getString("TEXT");

                String objKey = type + " " + name;
                if (!objKey.equals(currentObj)) {
                    currentObj = objKey;
                    report.append("\n================================================================================\n");
                    report.append(" OBJETO PL/SQL: ").append(objKey).append("\n");
                    report.append("================================================================================\n");
                }
                report.append(String.format("%4d: %s", line, text));
            }
            report.append("\n");
        } catch (Exception e) {
            report.append("Error al exportar codigo PL/SQL: ").append(e.getMessage()).append("\n");
        }
    }

    private static void auditDataInventoryAndRowCounts(Connection conn) {
        printHeader("12. CONTEO REAL DE REGISTROS Y MUESTRA DE DATOS POR TABLA");

        List<String> tables = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT table_name FROM user_tables ORDER BY table_name")) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        } catch (Exception e) {
            report.append("Error al obtener listado de tablas: ").append(e.getMessage()).append("\n");
            return;
        }

        report.append(String.format("%-35s %-15s %-30s\n", "TABLA", "TOTAL REGISTROS", "ESTADO DE LLENADO"));
        report.append(String.format("%-35s %-15s %-30s\n", "-----", "---------------", "-----------------"));

        long grandTotalRows = 0;
        for (String tbl : tables) {
            long count = 0;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tbl)) {
                if (rs.next()) count = rs.getLong(1);
            } catch (Exception e) {
                count = -1;
            }
            grandTotalRows += Math.max(0, count);
            report.append(String.format("%-35s %-15d %-30s\n", tbl, count, (count > 0 ? "CON DATOS (" + count + " filas)" : "VACIA (0 filas)")));
        }

        report.append("\nTOTAL ACUMULADO DE FILAS EN TODO EL ESQUEMA: ").append(grandTotalRows).append(" registros.\n\n");

        // Resumen detallado de tablas con datos (Muestra representativa)
        printSubHeader("MUESTRA REPRESENTATIVA DE REGISTROS (TOP 5 FILAS POR TABLA):");
        for (String tbl : tables) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM " + tbl + " FETCH FIRST 5 ROWS ONLY")) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();

                report.append("\n--- TABLA: ").append(tbl).append(" ---\n");
                StringBuilder headerLine = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    headerLine.append(String.format("%-20s ", md.getColumnName(i)));
                }
                report.append(headerLine).append("\n");
                report.append("-".repeat(Math.max(60, cols * 21))).append("\n");

                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    StringBuilder rowLine = new StringBuilder();
                    for (int i = 1; i <= cols; i++) {
                        String val = rs.getString(i);
                        if (val != null && val.length() > 18) {
                            val = val.substring(0, 15) + "...";
                        }
                        rowLine.append(String.format("%-20s ", (val != null ? val : "NULL")));
                    }
                    report.append(rowLine).append("\n");
                }
                if (rowCount == 0) {
                    report.append("(Tabla sin registros)\n");
                }
            } catch (Exception e) {
                report.append("  [Error consultando tabla ").append(tbl).append(": ").append(e.getMessage()).append("]\n");
            }
        }
    }

    private static void auditJobsAndScheduler(Connection conn) {
        printHeader("13. TRABAJOS PROGRAMADOS (SCHEDULER JOBS)");

        queryAndFormat("Trabajos del Programador (USER_SCHEDULER_JOBS):", conn,
                "SELECT job_name, job_type, job_action, start_date, repeat_interval, enabled, state, run_count, failure_count " +
                        "FROM user_scheduler_jobs ORDER BY job_name");
    }

    private static void auditHealthAndIntegrityChecks(Connection conn) {
        printHeader("14. DIAGNOSTICO DE SALUD, INTEGRIDAD Y AUDITORIA DE SEGURIDAD");

        queryAndFormat("1. Objetos Invalidos en el Esquema:", conn,
                "SELECT object_name, object_type, status, last_ddl_time FROM user_objects WHERE status != 'VALID' ORDER BY object_type, object_name");

        queryAndFormat("2. Restricciones Deshabilitadas o No Validadas:", conn,
                "SELECT table_name, constraint_name, constraint_type, status, validated FROM user_constraints " +
                        "WHERE status != 'ENABLED' OR validated != 'VALIDATED' ORDER BY table_name, constraint_name");

        queryAndFormat("3. Triggers Deshabilitados:", conn,
                "SELECT trigger_name, table_name, status FROM user_triggers WHERE status != 'ENABLED' ORDER BY table_name, trigger_name");

        queryAndFormat("4. Indices No Utilizables (UNUSABLE):", conn,
                "SELECT table_name, index_name, status FROM user_indexes WHERE status != 'VALID' AND status != 'N/A' ORDER BY table_name, index_name");

        // Diagnostico de sincronizacion de secuencias vs MAX(PK)
        printSubHeader("5. Sincronizacion de Secuencias vs Valor Maximo de Claves Primarias:");
        Map<String, String[]> seqPkMap = new LinkedHashMap<>();
        seqPkMap.put("SEC_TIPOS_DOCUMENTO", new String[]{"TIPOS_DOCUMENTO", "ID_TIPO_DOC"});
        seqPkMap.put("SEC_APARTAMENTOS", new String[]{"APARTAMENTOS", "ID_APARTAMENTO"});
        seqPkMap.put("SEC_PARQUEADEROS", new String[]{"PARQUEADEROS", "ID_PARQUEADERO"});
        seqPkMap.put("SEC_RESIDENTES", new String[]{"RESIDENTES", "ID_RESIDENTE"});
        seqPkMap.put("SEC_TUTORES", new String[]{"TUTORES", "ID_TUTOR"});
        seqPkMap.put("SEC_USUARIOS", new String[]{"USUARIOS", "ID_USUARIO"});
        seqPkMap.put("SEC_CONTRATOS", new String[]{"CONTRATOS", "ID_CONTRATO"});
        seqPkMap.put("SEC_CONTRATO_RESIDENTE", new String[]{"CONTRATO_RESIDENTE", "ID_CONTRATO_RES"});
        seqPkMap.put("SEC_CUOTAS_ARRIENDO", new String[]{"CUOTAS_ARRIENDO", "ID_CUOTA"});
        seqPkMap.put("SEC_PAGOS", new String[]{"PAGOS", "ID_PAGO"});
        seqPkMap.put("SEC_ALERTAS_PAGO", new String[]{"ALERTAS_PAGO", "ID_ALERTA"});
        seqPkMap.put("SEC_MULTAS", new String[]{"MULTAS", "ID_MULTA"});
        seqPkMap.put("SEC_VISITAS", new String[]{"VISITAS", "ID_VISITA"});
        seqPkMap.put("SEC_QR_ACCESOS", new String[]{"QR_ACCESOS", "ID_QR"});
        seqPkMap.put("SEC_VISITANTES", new String[]{"VISITANTES", "ID_VISITANTE"});
        seqPkMap.put("SEC_VEHICULOS_VISITA", new String[]{"VEHICULOS_VISITA", "ID_VEHICULO_VISITA"});
        seqPkMap.put("SEC_REGISTRO_VISITA", new String[]{"REGISTRO_VISITA", "ID_REGISTRO_VISITA"});
        seqPkMap.put("SEC_FRECUENTES_RESIDENTE", new String[]{"FRECUENTES_RESIDENTE", "ID_FRECUENTE"});
        seqPkMap.put("SEC_REGISTROS_ACCESO", new String[]{"REGISTROS_ACCESO", "ID_ACCESO"});
        seqPkMap.put("SEC_BUZON", new String[]{"BUZON", "ID_MENSAJE"});
        seqPkMap.put("SEQ_QUEJAS_SUGERENCIAS", new String[]{"QUEJAS_SUGERENCIAS", "ID_QUEJA"});

        report.append(String.format("%-28s %-22s %-12s %-12s %-20s\n", "SECUENCIA", "TABLA (PK)", "LAST_NUMBER", "MAX_ID_TABLA", "ESTADO"));
        report.append(String.format("%-28s %-22s %-12s %-12s %-20s\n", "---------", "----------", "-----------", "------------", "------"));

        for (Map.Entry<String, String[]> entry : seqPkMap.entrySet()) {
            String seq = entry.getKey();
            String tbl = entry.getValue()[0];
            String pk = entry.getValue()[1];

            long lastNum = -1;
            long maxId = -1;

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT last_number FROM user_sequences WHERE sequence_name = '" + seq + "'")) {
                if (rs.next()) lastNum = rs.getLong(1);
            } catch (Exception ignored) {}

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT NVL(MAX(" + pk + "), 0) FROM " + tbl)) {
                if (rs.next()) maxId = rs.getLong(1);
            } catch (Exception ignored) {}

            String statusStr;
            if (lastNum == -1) {
                statusStr = "NO EXISTE SECUENCIA";
            } else if (lastNum > maxId) {
                statusStr = "OK (SIN RIESGO PK)";
            } else {
                statusStr = "ALERTA: DESFASADA (<= MAX ID)";
            }

            report.append(String.format("%-28s %-22s %-12d %-12d %-20s\n",
                    seq, tbl + "." + pk, lastNum, maxId, statusStr));
        }

        report.append("\n==========================================================================================\n");
        report.append("                         FIN DEL INFORME DE AUDITORIA                                     \n");
        report.append("==========================================================================================\n");
    }

    private static void queryAndFormat(String sectionTitle, Connection conn, String... sqlQueries) {
        printSubHeader(sectionTitle);
        for (String sql : sqlQueries) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {

                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();

                int[] colWidths = new int[colCount + 1];
                for (int i = 1; i <= colCount; i++) {
                    colWidths[i] = Math.max(md.getColumnLabel(i).length(), 10);
                }

                List<List<String>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<String> row = new ArrayList<>();
                    for (int i = 1; i <= colCount; i++) {
                        String val = rs.getString(i);
                        if (val == null) val = "NULL";
                        else val = val.trim();
                        row.add(val);
                        if (val.length() > colWidths[i] && val.length() < 60) {
                            colWidths[i] = val.length();
                        }
                    }
                    rows.add(row);
                }

                // Imprimir cabeceras
                StringBuilder hdr = new StringBuilder();
                StringBuilder sep = new StringBuilder();
                for (int i = 1; i <= colCount; i++) {
                    int w = Math.min(colWidths[i], 60);
                    hdr.append(String.format("%-" + w + "s  ", md.getColumnLabel(i)));
                    sep.append("-".repeat(w)).append("  ");
                }
                report.append(hdr).append("\n").append(sep).append("\n");

                if (rows.isEmpty()) {
                    report.append("  (0 registros encontrados)\n\n");
                } else {
                    for (List<String> r : rows) {
                        StringBuilder line = new StringBuilder();
                        for (int i = 0; i < colCount; i++) {
                            int w = Math.min(colWidths[i + 1], 60);
                            String v = r.get(i);
                            if (v.length() > 60) v = v.substring(0, 57) + "...";
                            line.append(String.format("%-" + w + "s  ", v));
                        }
                        report.append(line).append("\n");
                    }
                    report.append("\n");
                }
                return; // Consulta ejecutada con exito
            } catch (Exception e) {
                // Si falla una consulta alternativa, prueba la siguiente
            }
        }
        report.append("  (Consulta no disponible con los privilegios actuales o vista no existente)\n\n");
    }
}
