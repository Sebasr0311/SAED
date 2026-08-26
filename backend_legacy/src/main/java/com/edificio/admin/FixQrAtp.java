package com.edificio.admin;

import com.edificio.admin.dao.ConexionBD;
import java.sql.*;

/**
 * Aplica el fix de zona horaria a los SPs de QR en ATP.
 * Reemplaza CURRENT_TIMESTAMP por CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP)
 * para que funcione independientemente de la zona horaria de sesion.
 */
public class FixQrAtp {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("DB_URL");
        if (url == null || !url.contains("residencial_high")) {
            System.out.println("ERROR: DB_URL debe apuntar a residencial_high (ATP).");
            System.exit(1);
        }

        try (Connection c = ConexionBD.getInstancia().getConexion();
             Statement st = c.createStatement()) {

            System.out.println("Conectado a ATP. Session TZ: " + getSingle(st, "SELECT SESSIONTIMEZONE FROM DUAL"));

            String[] sqls = {
                // 1. DEFAULT en fecha_generacion
                "ALTER TABLE QR_ACCESOS MODIFY (fecha_generacion DEFAULT CURRENT_TIMESTAMP)",

                // 2. DEFAULT en VISITAS.fecha_registro
                "ALTER TABLE VISITAS MODIFY (fecha_registro DEFAULT CURRENT_TIMESTAMP)",

                // 3. SP_LIBERAR_VISITA_FRECUENTE
                "CREATE OR REPLACE PROCEDURE SP_LIBERAR_VISITA_FRECUENTE (\n" +
                "    p_id_visitante      IN  NUMBER,\n" +
                "    p_id_contrato_res   IN  NUMBER,\n" +
                "    p_id_residente      IN  NUMBER,\n" +
                "    p_cantidad_personas IN  NUMBER,\n" +
                "    p_tiempo_validez    IN  NUMBER,\n" +
                "    p_tipo_vehiculo     IN  VARCHAR2,\n" +
                "    p_placa             IN  VARCHAR2,\n" +
                "    p_descripcion_tipo  IN  VARCHAR2,\n" +
                "    p_notas             IN  VARCHAR2,\n" +
                "    p_id_visita         OUT NUMBER,\n" +
                "    p_codigo_qr         OUT VARCHAR2,\n" +
                "    p_fecha_expiracion  OUT TIMESTAMP,\n" +
                "    p_mensaje           OUT VARCHAR2\n" +
                ")\n" +
                "AS\n" +
                "    v_id_visita     VISITAS.id_visita%TYPE;\n" +
                "    v_count         NUMBER;\n" +
                "    v_codigo_qr     VARCHAR2(255);\n" +
                "    v_fecha_exp     TIMESTAMP;\n" +
                "    v_bogota        TIMESTAMP;\n" +
                "BEGIN\n" +
                "    SELECT CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP) INTO v_bogota FROM DUAL;\n" +
                "\n" +
                "    SELECT COUNT(*) INTO v_count\n" +
                "      FROM FRECUENTES_RESIDENTE\n" +
                "     WHERE id_residente = p_id_residente\n" +
                "       AND id_visitante = p_id_visitante\n" +
                "       AND activo       = 1;\n" +
                "\n" +
                "    IF v_count = 0 THEN\n" +
                "        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;\n" +
                "        p_mensaje := 'El visitante no figura como frecuente activo de este residente.';\n" +
                "        RETURN;\n" +
                "    END IF;\n" +
                "\n" +
                "    SELECT COUNT(*) INTO v_count\n" +
                "      FROM CONTRATO_RESIDENTE cr\n" +
                "      JOIN CONTRATOS          c  ON c.id_contrato = cr.id_contrato\n" +
                "     WHERE cr.id_contrato_res = p_id_contrato_res\n" +
                "       AND cr.id_residente    = p_id_residente\n" +
                "       AND c.estado           = 'ACTIVO';\n" +
                "\n" +
                "    IF v_count = 0 THEN\n" +
                "        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;\n" +
                "        p_mensaje := 'No se encontro un contrato ACTIVO para este residente.';\n" +
                "        RETURN;\n" +
                "    END IF;\n" +
                "\n" +
                "    IF p_tiempo_validez NOT BETWEEN 5 AND 60 THEN\n" +
                "        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;\n" +
                "        p_mensaje := 'El tiempo de validez del QR debe estar entre 5 y 60 minutos.';\n" +
                "        RETURN;\n" +
                "    END IF;\n" +
                "\n" +
                "    IF p_tipo_vehiculo = 'OTRO'\n" +
                "       AND (p_descripcion_tipo IS NULL OR TRIM(p_descripcion_tipo) IS NULL) THEN\n" +
                "        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;\n" +
                "        p_mensaje := 'Debe especificar la descripcion del vehiculo cuando el tipo es OTRO.';\n" +
                "        RETURN;\n" +
                "    END IF;\n" +
                "\n" +
                "    INSERT INTO VISITAS (\n" +
                "        id_contrato_res, id_residente, tiempo_validez_min,\n" +
                "        cantidad_personas, notas, estado, fecha_registro, actualizado_en\n" +
                "    ) VALUES (\n" +
                "        p_id_contrato_res, p_id_residente, p_tiempo_validez,\n" +
                "        p_cantidad_personas, p_notas, 'PENDIENTE', v_bogota, v_bogota\n" +
                "    ) RETURNING id_visita INTO v_id_visita;\n" +
                "\n" +
                "    INSERT INTO REGISTRO_VISITA (id_visita, id_visitante, es_titular)\n" +
                "    VALUES (v_id_visita, p_id_visitante, 1);\n" +
                "\n" +
                "    IF p_tipo_vehiculo IS NOT NULL AND p_placa IS NOT NULL THEN\n" +
                "        INSERT INTO VEHICULOS_VISITA (id_visita, placa, tipo, descripcion_tipo)\n" +
                "        VALUES (v_id_visita, UPPER(TRIM(p_placa)), p_tipo_vehiculo,\n" +
                "                CASE WHEN p_tipo_vehiculo = 'OTRO' THEN p_descripcion_tipo ELSE NULL END);\n" +
                "    END IF;\n" +
                "\n" +
                "    v_codigo_qr := LOWER(RAWTOHEX(SYS_GUID()));\n" +
                "    v_fecha_exp := v_bogota + NUMTODSINTERVAL(p_tiempo_validez, 'MINUTE');\n" +
                "\n" +
                "    INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado)\n" +
                "    VALUES (v_id_visita, v_codigo_qr, v_bogota, v_fecha_exp, 0);\n" +
                "\n" +
                "    COMMIT;\n" +
                "\n" +
                "    p_id_visita        := v_id_visita;\n" +
                "    p_codigo_qr        := v_codigo_qr;\n" +
                "    p_fecha_expiracion := v_fecha_exp;\n" +
                "    p_mensaje := 'Visita frecuente registrada. QR generado correctamente.';\n" +
                "\n" +
                "EXCEPTION\n" +
                "    WHEN OTHERS THEN\n" +
                "        ROLLBACK;\n" +
                "        p_id_visita := NULL; p_codigo_qr := NULL; p_fecha_expiracion := NULL;\n" +
                "        p_mensaje := 'Error interno: ' || SQLERRM;\n" +
                "END SP_LIBERAR_VISITA_FRECUENTE;",

                // 4. SP_GENERAR_QR_VISITA
                "CREATE OR REPLACE PROCEDURE SP_GENERAR_QR_VISITA (\n" +
                "    p_id_visita  IN  NUMBER,\n" +
                "    p_tiempo_min IN  NUMBER,\n" +
                "    p_codigo_qr  OUT VARCHAR2,\n" +
                "    p_expiracion OUT TIMESTAMP,\n" +
                "    p_mensaje    OUT VARCHAR2\n" +
                ") IS\n" +
                "    v_estado   VISITAS.estado%TYPE;\n" +
                "    v_count    NUMBER;\n" +
                "    v_bogota   TIMESTAMP;\n" +
                "BEGIN\n" +
                "    SELECT CAST(SYSTIMESTAMP AT TIME ZONE 'America/Bogota' AS TIMESTAMP) INTO v_bogota FROM DUAL;\n" +
                "    SELECT estado INTO v_estado FROM VISITAS WHERE id_visita = p_id_visita;\n" +
                "\n" +
                "    IF v_estado != 'PENDIENTE' THEN\n" +
                "        p_codigo_qr := NULL; p_expiracion := NULL;\n" +
                "        p_mensaje := 'Solo se puede generar QR para visitas PENDIENTE.';\n" +
                "        RETURN;\n" +
                "    END IF;\n" +
                "\n" +
                "    SELECT COUNT(*) INTO v_count\n" +
                "      FROM QR_ACCESOS\n" +
                "     WHERE id_visita = p_id_visita AND usado = 0\n" +
                "       AND fecha_expiracion > v_bogota;\n" +
                "\n" +
                "    IF v_count > 0 THEN\n" +
                "        p_codigo_qr := NULL; p_expiracion := NULL;\n" +
                "        p_mensaje := 'Ya existe un QR activo para esta visita.';\n" +
                "        RETURN;\n" +
                "    END IF;\n" +
                "\n" +
                "    IF p_tiempo_min NOT BETWEEN 5 AND 60 THEN\n" +
                "        p_codigo_qr := NULL; p_expiracion := NULL;\n" +
                "        p_mensaje := 'El tiempo de validez debe estar entre 5 y 60 minutos.';\n" +
                "        RETURN;\n" +
                "    END IF;\n" +
                "\n" +
                "    p_codigo_qr  := LOWER(RAWTOHEX(SYS_GUID()));\n" +
                "    p_expiracion := v_bogota + NUMTODSINTERVAL(p_tiempo_min, 'MINUTE');\n" +
                "\n" +
                "    INSERT INTO QR_ACCESOS (id_visita, codigo_qr, fecha_generacion, fecha_expiracion, usado)\n" +
                "    VALUES (p_id_visita, p_codigo_qr, v_bogota, p_expiracion, 0);\n" +
                "\n" +
                "    COMMIT;\n" +
                "    p_mensaje := 'QR generado. Vigencia: ' || p_tiempo_min || ' minuto(s).';\n" +
                "EXCEPTION\n" +
                "    WHEN NO_DATA_FOUND THEN\n" +
                "        p_codigo_qr := NULL; p_expiracion := NULL;\n" +
                "        p_mensaje := 'Visita no encontrada (id_visita=' || p_id_visita || ').';\n" +
                "    WHEN OTHERS THEN\n" +
                "        ROLLBACK;\n" +
                "        p_codigo_qr := NULL; p_expiracion := NULL;\n" +
                "        p_mensaje := 'Error interno: ' || SQLERRM;\n" +
                "END SP_GENERAR_QR_VISITA;",

                // 5. Clean up old buggy records
                "UPDATE QR_ACCESOS\n" +
                "   SET fecha_generacion = fecha_generacion - INTERVAL '5' HOUR\n" +
                " WHERE usado = 0\n" +
                "   AND fecha_generacion > fecha_expiracion",
            };

            for (int i = 0; i < sqls.length; i++) {
                try {
                    st.execute(sqls[i]);
                    System.out.println("OK: statement " + (i + 1));
                } catch (SQLException e) {
                    System.out.println("ERROR en stmt " + (i + 1) + ": " + e.getMessage());
                }
            }

            // Verification
            System.out.println("\n--- VERIFICACION ---");
            System.out.println("DEFAULT fecha_generacion: " + getSingle(st,
                "SELECT DATA_DEFAULT FROM USER_TAB_COLUMNS WHERE TABLE_NAME='QR_ACCESOS' AND COLUMN_NAME='FECHA_GENERACION'"));
            System.out.println("QRs con UTC: " + getSingle(st,
                "SELECT COUNT(*) FROM QR_ACCESOS WHERE usado=0 AND fecha_generacion > fecha_expiracion"));

            // Check SP source
            System.out.println("\n--- SP_LIBERAR_VISITA_FRECUENTE (v_bogota/CURRENT) ---");
            try (ResultSet rs = st.executeQuery(
                "SELECT LINE, TEXT FROM USER_SOURCE WHERE NAME='SP_LIBERAR_VISITA_FRECUENTE' AND TYPE='PROCEDURE' AND (TEXT LIKE '%bogota%' OR TEXT LIKE '%CURRENT_TIMESTAMP%') ORDER BY LINE")) {
                while (rs.next()) System.out.println("L" + rs.getInt("LINE") + ": " + rs.getString("TEXT"));
            }
        }
    }

    static String getSingle(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) { return rs.next() ? rs.getString(1) : null; }
    }
}
