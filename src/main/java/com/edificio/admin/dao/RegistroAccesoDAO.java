package com.edificio.admin.dao;

import com.edificio.admin.model.RegistroAcceso;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla REGISTROS_ACCESO.
 *
 * No implementa CrudDAO porque:
 *   · Los INSERT los realiza SP_VALIDAR_QR en Oracle.
 *   · El UPDATE relevante es sólo registrar la hora_salida.
 *   · Los registros nunca se eliminan (historial inmutable de accesos).
 */
public class RegistroAccesoDAO {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    // ── Consultas ──────────────────────────────────────────────────────────────

    /** Todos los registros, del más reciente al más antiguo. */
    public List<RegistroAcceso> findAll() throws SQLException {
        List<RegistroAcceso> lista = new ArrayList<>();
        String sql = "SELECT id_acceso, id_visita, id_vigilante, hora_entrada, hora_salida, "
                   + "       observaciones, fecha_registro, actualizado_en "
                   + "FROM   REGISTROS_ACCESO "
                   + "ORDER  BY hora_entrada DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    /** Busca por clave primaria. Devuelve null si no existe. */
    public RegistroAcceso findById(Integer idAcceso) throws SQLException {
        String sql = "SELECT id_acceso, id_visita, id_vigilante, hora_entrada, hora_salida, "
                   + "       observaciones, fecha_registro, actualizado_en "
                   + "FROM   REGISTROS_ACCESO WHERE id_acceso = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idAcceso);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Busca el registro de acceso de una visita (relación 1:1). Devuelve null si no existe. */
    public RegistroAcceso findByVisita(Integer idVisita) throws SQLException {
        String sql = "SELECT id_acceso, id_visita, id_vigilante, hora_entrada, hora_salida, "
                   + "       observaciones, fecha_registro, actualizado_en "
                   + "FROM   REGISTROS_ACCESO WHERE id_visita = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idVisita);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /**
     * Visitantes actualmente dentro del edificio (hora_salida IS NULL).
     * Incluye el nombre del residente y el apartamento que autorizó la visita.
     */
    public List<RegistroAcceso> findActivos() throws SQLException {
        List<RegistroAcceso> lista = new ArrayList<>();
        String sql = "SELECT ra.id_acceso, ra.id_visita, ra.id_vigilante, "
                   + "       ra.hora_entrada, ra.hora_salida, "
                   + "       ra.observaciones, ra.fecha_registro, ra.actualizado_en, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       a.numero AS numero_apartamento, a.piso, "
                   + "       pq.codigo AS codigo_parqueadero "
                   + "FROM   REGISTROS_ACCESO ra "
                   + "JOIN   VISITAS              v  ON v.id_visita      = ra.id_visita "
                   + "JOIN   RESIDENTES           r  ON r.id_residente   = v.id_residente "
                   + "JOIN   CONTRATO_RESIDENTE   cr ON cr.id_contrato_res = v.id_contrato_res "
                   + "JOIN   CONTRATOS            c  ON c.id_contrato    = cr.id_contrato "
                   + "JOIN   APARTAMENTOS         a  ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN VEHICULOS_VISITA  vv ON vv.id_visita     = v.id_visita AND vv.hora_salida IS NULL "
                   + "LEFT JOIN PARQUEADEROS     pq ON pq.id_parqueadero = vv.id_parqueadero "
                   + "WHERE  ra.hora_salida IS NULL AND v.estado = 'ACTIVA' "
                   + "ORDER  BY ra.hora_entrada DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RegistroAcceso ra = mapear(rs);
                ra.setNombreResidente(rs.getString("nombre_residente"));
                ra.setNumeroApartamento(rs.getString("numero_apartamento"));
                ra.setPiso(rs.getInt("piso"));
                ra.setCodigoParqueadero(rs.getString("codigo_parqueadero"));
                lista.add(ra);
            }
        }
        return lista;
    }

    // ── Operaciones ────────────────────────────────────────────────────────────

    /**
     * Inserta un nuevo registro de acceso.
     */
    public Integer insert(RegistroAcceso ra) throws SQLException {
        String sql = "BEGIN INSERT INTO REGISTROS_ACCESO "
                   + "  (id_visita, id_vigilante, hora_entrada, observaciones) "
                   + "VALUES (?, ?, ?, ?) "
                   + "RETURNING id_acceso INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, ra.getIdVisita());
            cs.setInt(2, ra.getIdVigilante());
            if (ra.getHoraEntrada() != null)
                cs.setTimestamp(3, Timestamp.valueOf(ra.getHoraEntrada()));
            else
                cs.setNull(3, Types.TIMESTAMP);
            cs.setString(4, ra.getObservaciones());
            cs.registerOutParameter(5, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(5);
        }
    }

    /**
     * Registra la salida del visitante para la visita indicada.
     *
     * TRG_ACCESO_SALIDA se dispara automáticamente y:
     *   · Cambia VISITAS.estado → FINALIZADA
     *   · Libera parqueaderos asociados a la visita
     *   · Copia hora_salida a todos los VEHICULOS_VISITA de esa visita
     *
     * @param idVisita ID de la visita cuyo visitante está saliendo
     */
    public void registrarSalida(Integer idVisita) throws SQLException {
        String sql = "UPDATE REGISTROS_ACCESO "
                   + "SET    hora_salida = SYSTIMESTAMP "
                   + "WHERE  id_visita = ? "
                   + "  AND  hora_salida IS NULL"; // evita sobrescribir si ya tiene salida
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idVisita);
            ps.executeUpdate();
        }
    }

    /**
     * Registra la salida y actualiza observaciones en el mismo UPDATE.
     *
     * @param idVisita       ID de la visita
     * @param observaciones  Notas del portero al momento de la salida (puede ser null)
     */
    public void registrarSalida(Integer idVisita, String observaciones) throws SQLException {
        String sql = "UPDATE REGISTROS_ACCESO "
                   + "SET    hora_salida   = SYSTIMESTAMP, "
                   + "       observaciones = COALESCE(?, observaciones) "
                   + "WHERE  id_visita     = ? "
                   + "  AND  hora_salida IS NULL";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (observaciones != null && !observaciones.isBlank())
                ps.setString(1, observaciones.trim());
            else
                ps.setNull(1, Types.VARCHAR);
            ps.setInt(2, idVisita);
            ps.executeUpdate();
        }
    }

    // ── Mapeo interno ──────────────────────────────────────────────────────────

    private RegistroAcceso mapear(ResultSet rs) throws SQLException {
        RegistroAcceso ra = new RegistroAcceso();
        ra.setIdAcceso(rs.getInt("id_acceso"));
        ra.setIdVisita(rs.getInt("id_visita"));
        ra.setIdVigilante(rs.getInt("id_vigilante"));

        Timestamp he = rs.getTimestamp("hora_entrada");
        if (he != null) ra.setHoraEntrada(he.toLocalDateTime());

        Timestamp hs = rs.getTimestamp("hora_salida");
        if (hs != null) ra.setHoraSalida(hs.toLocalDateTime());

        ra.setObservaciones(rs.getString("observaciones"));

        Timestamp fr = rs.getTimestamp("fecha_registro");
        if (fr != null) ra.setFechaRegistro(fr.toLocalDateTime());

        Timestamp ac = rs.getTimestamp("actualizado_en");
        if (ac != null) ra.setActualizadoEn(ac.toLocalDateTime());

        return ra;
    }
}
