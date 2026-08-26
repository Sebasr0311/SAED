package com.edificio.admin;

import com.edificio.admin.dao.ConexionBD;
import java.sql.*;

public class CheckAtp {
    public static void main(String[] args) throws Exception {
        try (Connection c = ConexionBD.getInstancia().getConexion();
             Statement st = c.createStatement()) {

            // 1. Timezone info
            try (ResultSet rs = st.executeQuery(
                "SELECT SESSIONTIMEZONE, DBTIMEZONE, SYSTIMESTAMP, CURRENT_TIMESTAMP FROM DUAL")) {
                rs.next();
                System.out.println("SESSIONTIMEZONE=" + rs.getString(1));
                System.out.println("DBTIMEZONE=" + rs.getString(2));
                System.out.println("SYSTIMESTAMP=" + rs.getTimestamp(3));
                System.out.println("CURRENT_TIMESTAMP=" + rs.getTimestamp(4));
            }

            // 2. SP_LIBERAR_VISITA_FRECUENTE source (lines with TIMESTAMP/INSERT)
            System.out.println("\n--- SP_LIBERAR_VISITA_FRECUENTE (TIMESTAMP/INSERT) ---");
            try (ResultSet rs = st.executeQuery(
                "SELECT LINE, TEXT FROM USER_SOURCE WHERE NAME='SP_LIBERAR_VISITA_FRECUENTE' AND TYPE='PROCEDURE' ORDER BY LINE")) {
                while (rs.next()) {
                    String t = rs.getString("TEXT");
                    if (t.toUpperCase().contains("TIMESTAMP") || t.toUpperCase().contains("INSERT"))
                        System.out.println("L" + rs.getInt("LINE") + ": " + t);
                }
            }

            // 3. SP_GENERAR_QR_VISITA source
            System.out.println("\n--- SP_GENERAR_QR_VISITA (TIMESTAMP/INSERT) ---");
            try (ResultSet rs = st.executeQuery(
                "SELECT LINE, TEXT FROM USER_SOURCE WHERE NAME='SP_GENERAR_QR_VISITA' AND TYPE='PROCEDURE' ORDER BY LINE")) {
                while (rs.next()) {
                    String t = rs.getString("TEXT");
                    if (t.toUpperCase().contains("TIMESTAMP") || t.toUpperCase().contains("INSERT"))
                        System.out.println("L" + rs.getInt("LINE") + ": " + t);
                }
            }

            // 4. Last 10 QRs
            System.out.println("\n--- Ultimos 10 QRs ---");
            try (ResultSet rs = st.executeQuery(
                "SELECT id_qr, fecha_generacion, fecha_expiracion, usado FROM QR_ACCESOS ORDER BY id_qr DESC FETCH FIRST 10 ROWS ONLY")) {
                while (rs.next()) {
                    System.out.println("id=" + rs.getInt("id_qr")
                        + " gen=" + rs.getTimestamp("fecha_generacion")
                        + " exp=" + rs.getTimestamp("fecha_expiracion")
                        + " usado=" + rs.getInt("usado"));
                }
            }

            // 5. DEFAULT on fecha_generacion
            try (ResultSet rs = st.executeQuery(
                "SELECT DATA_DEFAULT FROM USER_TAB_COLUMNS WHERE TABLE_NAME='QR_ACCESOS' AND COLUMN_NAME='FECHA_GENERACION'")) {
                if (rs.next()) System.out.println("\nDEFAULT fecha_generacion: " + rs.getString("DATA_DEFAULT"));
            }
        }
    }
}
