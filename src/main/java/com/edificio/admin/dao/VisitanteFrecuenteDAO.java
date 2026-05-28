package com.edificio.admin.dao;

import com.edificio.admin.model.VisitanteFrecuente;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para el módulo de Visitantes Frecuentes.
 *
 * Lecturas → VW_VISITANTES_FRECUENTES
 * Liberación → SP_LIBERAR_VISITA_FRECUENTE (9 IN / 4 OUT)
 * Ocultar → UPDATE FRECUENTES_RESIDENTE SET activo = 0
 */
public class VisitanteFrecuenteDAO {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    // ------------------------------------------------------------------
    // Consulta principal
    // ------------------------------------------------------------------

    /**
     * Retorna los visitantes frecuentes activos del residente, ordenados por
     * fecha de última visita descendente (el más reciente primero).
     */
    public List<VisitanteFrecuente> findByResidente(int idResidente) throws SQLException {
        List<VisitanteFrecuente> lista = new ArrayList<>();
        String sql =
            "SELECT id_frecuente, id_residente, id_visitante, "
          + "       nombre_visitante, documento, total_visitas, ultima_visita, "
          + "       ultima_placa, ultimo_tipo_vehiculo, ultima_descripcion_tipo, activo "
          + "FROM   VW_VISITANTES_FRECUENTES "
          + "WHERE  id_residente = ? AND activo = 1 "
          + "ORDER  BY ultima_visita DESC NULLS LAST";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idResidente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    // ------------------------------------------------------------------
    // Liberación rápida de QR
    // ------------------------------------------------------------------

    /**
     * Resultado que devuelve el SP tras generar la visita y el QR.
     */
    public static class LiberarVisitaResult {
        /** ID de la visita recién creada. */
        public int           idVisita;
        /** Código QR listo para mostrar/compartir. */
        public String        codigoQR;
        /** Timestamp de expiración del QR. */
        public LocalDateTime fechaExpiracion;
        /** Mensaje descriptivo del SP (p.ej. "QR generado para Juan Pérez"). */
        public String        mensaje;
    }

    /**
     * Llama a SP_LIBERAR_VISITA_FRECUENTE y retorna los 4 valores OUT.
     *
     * @param idVisitante     ID en VISITANTES
     * @param idContratoRes   ID en CONTRATO_RESIDENTE (contrato activo del residente)
     * @param idResidente     ID del residente autorizante
     * @param cantPersonas    1 – 99
     * @param tiempoValidez   5 – 60 minutos
     * @param tipoVehiculo    null = a pie | "VEHICULO" | "MOTO" | "BICICLETA" | "OTRO"
     * @param placa           null si sin vehículo
     * @param descripcionTipo obligatorio si tipoVehiculo = "OTRO"; null en otros casos
     * @param notas           observaciones libres (puede ser null)
     */
    public LiberarVisitaResult liberarVisita(
            int    idVisitante,
            int    idContratoRes,
            int    idResidente,
            int    cantPersonas,
            int    tiempoValidez,
            String tipoVehiculo,
            String placa,
            String descripcionTipo,
            String notas) throws SQLException {

        String sql = "{CALL RESIDENCIAL.SP_LIBERAR_VISITA_FRECUENTE(?,?,?,?,?,?,?,?,?,?,?,?,?)}";
        try (CallableStatement cs = conn().prepareCall(sql)) {

            // --- IN params (1-9) ---
            cs.setInt(1, idVisitante);
            cs.setInt(2, idContratoRes);
            cs.setInt(3, idResidente);
            cs.setInt(4, cantPersonas);
            cs.setInt(5, tiempoValidez);

            if (tipoVehiculo != null) cs.setString(6, tipoVehiculo);
            else                      cs.setNull  (6, Types.VARCHAR);

            if (placa != null && !placa.isBlank()) cs.setString(7, placa);
            else                                   cs.setNull  (7, Types.VARCHAR);

            if (descripcionTipo != null && !descripcionTipo.isBlank()) cs.setString(8, descripcionTipo);
            else                                                        cs.setNull  (8, Types.VARCHAR);

            if (notas != null && !notas.isBlank()) cs.setString(9, notas);
            else                                   cs.setNull  (9, Types.VARCHAR);

            // --- OUT params (10-13) ---
            cs.registerOutParameter(10, Types.NUMERIC);    // p_id_visita
            cs.registerOutParameter(11, Types.VARCHAR);    // p_codigo_qr
            cs.registerOutParameter(12, Types.TIMESTAMP);  // p_fecha_expiracion
            cs.registerOutParameter(13, Types.VARCHAR);    // p_mensaje

            cs.execute();

            LiberarVisitaResult r = new LiberarVisitaResult();
            r.idVisita        = cs.getInt(10);
            r.codigoQR        = cs.getString(11);
            Timestamp ts      = cs.getTimestamp(12);
            r.fechaExpiracion = ts != null ? ts.toLocalDateTime() : null;
            r.mensaje         = cs.getString(13);
            return r;
        }
    }

    // ------------------------------------------------------------------
    // Ocultar frecuente
    // ------------------------------------------------------------------

    /**
     * Marca el registro como inactivo (activo = 0).
     * Se reactiva automáticamente si el visitante vuelve a ser registrado
     * para el mismo residente (vía TRG_AUTO_FRECUENTE), o manualmente con
     * {@link #reactivarFrecuente(int)}.
     */
    public void ocultarFrecuente(int idFrecuente) throws SQLException {
        String sql = "UPDATE FRECUENTES_RESIDENTE SET activo = 0 WHERE id_frecuente = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idFrecuente);
            ps.executeUpdate();
        }
    }

    /**
     * Reactiva un frecuente previamente oculto (activo = 1).
     * Llamado desde el panel del residente al pulsar "Desbloquear".
     */
    public void reactivarFrecuente(int idFrecuente) throws SQLException {
        String sql = "UPDATE FRECUENTES_RESIDENTE SET activo = 1 WHERE id_frecuente = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idFrecuente);
            ps.executeUpdate();
        }
    }

    /**
     * Retorna los visitantes frecuentes OCULTOS (activo=0) del residente,
     * ordenados por nombre para facilitar la búsqueda.
     */
    public List<VisitanteFrecuente> findOcultosByResidente(int idResidente) throws SQLException {
        List<VisitanteFrecuente> lista = new ArrayList<>();
        String sql =
            "SELECT id_frecuente, id_residente, id_visitante, "
          + "       nombre_visitante, documento, total_visitas, ultima_visita, "
          + "       ultima_placa, ultimo_tipo_vehiculo, ultima_descripcion_tipo, activo "
          + "FROM   VW_VISITANTES_FRECUENTES "
          + "WHERE  id_residente = ? AND activo = 0 "
          + "ORDER  BY nombre_visitante";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idResidente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    // ------------------------------------------------------------------
    // Mapeo
    // ------------------------------------------------------------------

    private VisitanteFrecuente mapear(ResultSet rs) throws SQLException {
        VisitanteFrecuente f = new VisitanteFrecuente();
        f.setIdFrecuente           (rs.getInt   ("id_frecuente"));
        f.setIdResidente           (rs.getInt   ("id_residente"));
        f.setIdVisitante           (rs.getInt   ("id_visitante"));
        f.setNombreVisitante       (rs.getString("nombre_visitante"));
        f.setDocumento             (rs.getString("documento"));
        f.setTotalVisitas          (rs.getInt   ("total_visitas"));

        Timestamp uv = rs.getTimestamp("ultima_visita");
        f.setUltimaVisita(uv != null ? uv.toLocalDateTime() : null);

        f.setUltimaPlaca           (rs.getString("ultima_placa"));
        f.setUltimoTipoVehiculo    (rs.getString("ultimo_tipo_vehiculo"));
        f.setUltimaDescripcionTipo (rs.getString("ultima_descripcion_tipo"));
        f.setActivo                (rs.getInt   ("activo") == 1);
        return f;
    }
}
