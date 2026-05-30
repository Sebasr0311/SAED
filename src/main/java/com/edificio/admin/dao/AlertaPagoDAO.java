package com.edificio.admin.dao;

import com.edificio.admin.model.AlertaPago;
import com.edificio.admin.model.AlertaPago.Canal;
import com.edificio.admin.model.AlertaPago.TipoAlerta;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para ALERTAS_PAGO.
 *
 * No implementa CrudDAO: las alertas las genera Oracle (triggers).
 * Solo se necesita:
 *   - findAll()             — todas las alertas (con datos de residente/apto/cuota)
 *   - findNoLeidas()        — filtradas a leida = 0
 *   - marcarLeida(idAlerta) — UPDATE leida=1, leida_en=CURRENT_TIMESTAMP
 */
public class AlertaPagoDAO {

    private static final String SELECT_BASE =
        "SELECT a.id_alerta, a.id_cuota, a.tipo_alerta, a.canal, "
        + "       a.leida, a.enviada_en, a.leida_en, "
        + "       c.anio, c.mes, c.estado AS estado_cuota, "
        + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
        + "       ap.numero                        AS numero_apartamento "
        + "FROM   ALERTAS_PAGO    a "
        + "JOIN   CUOTAS_ARRIENDO c  ON c.id_cuota    = a.id_cuota "
        + "JOIN   CONTRATOS       ct ON ct.id_contrato = c.id_contrato "
        + "JOIN   CONTRATO_RESIDENTE cr ON cr.id_contrato = ct.id_contrato "
        + "                            AND cr.rol_en_contrato = 'ARRENDATARIO' "
        + "JOIN   RESIDENTES      r  ON r.id_residente = cr.id_residente "
        + "JOIN   APARTAMENTOS    ap ON ap.id_apartamento = ct.id_apartamento ";

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    /** Devuelve todas las alertas ordenadas: no leídas primero, luego por fecha desc. */
    public List<AlertaPago> findAll() throws SQLException {
        String sql = SELECT_BASE + "ORDER BY a.leida ASC, a.enviada_en DESC";
        return ejecutarConsulta(sql);
    }

    /** Busca una alerta por su ID. */
    public AlertaPago findById(Integer idAlerta) throws SQLException {
        String sql = SELECT_BASE + "WHERE a.id_alerta = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idAlerta);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Solo las alertas sin leer. */
    public List<AlertaPago> findNoLeidas() throws SQLException {
        String sql = SELECT_BASE + "WHERE a.leida = 0 ORDER BY a.enviada_en DESC";
        return ejecutarConsulta(sql);
    }

    /**
     * Inserta una nueva alerta.
     * Se usa SEC_ALERTAS_PAGO.NEXTVAL para el ID.
     */
    public void insert(Integer idCuota, TipoAlerta tipoAlerta, Canal canal) throws SQLException {
        String sql = "INSERT INTO ALERTAS_PAGO (id_alerta, id_cuota, tipo_alerta, canal, leida, enviada_en) "
                   + "VALUES (SEC_ALERTAS_PAGO.NEXTVAL, ?, ?, ?, 0, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idCuota);
            ps.setString(2, tipoAlerta.name());
            ps.setString(3, canal.name());
            ps.executeUpdate();
        }
    }

    /**
     * Marca una alerta como leída.
     * CHK_ALERTA_LEIDA_EN en Oracle exige que leida_en NOT NULL cuando leida=1.
     */
    public void marcarLeida(Integer idAlerta) throws SQLException {
        String sql = "UPDATE ALERTAS_PAGO "
                   + "SET leida = 1, leida_en = CURRENT_TIMESTAMP "
                   + "WHERE id_alerta = ? AND leida = 0";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idAlerta);
            ps.executeUpdate();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<AlertaPago> ejecutarConsulta(String sql) throws SQLException {
        List<AlertaPago> lista = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    private AlertaPago mapear(ResultSet rs) throws SQLException {
        AlertaPago a = new AlertaPago();
        a.setIdAlerta(rs.getInt("id_alerta"));
        a.setIdCuota(rs.getInt("id_cuota"));

        try { a.setTipoAlerta(TipoAlerta.valueOf(rs.getString("tipo_alerta"))); }
        catch (IllegalArgumentException ignored) {}

        try { a.setCanal(Canal.valueOf(rs.getString("canal"))); }
        catch (IllegalArgumentException ignored) {}

        a.setLeida(rs.getInt("leida") == 1);

        Timestamp env = rs.getTimestamp("enviada_en");
        if (env != null) a.setEnviadaEn(env.toLocalDateTime());

        Timestamp lei = rs.getTimestamp("leida_en");
        if (lei != null) a.setLeidaEn(lei.toLocalDateTime());

        a.setAnio(rs.getInt("anio"));
        a.setMes(rs.getInt("mes"));
        a.setEstadoCuota(rs.getString("estado_cuota"));
        a.setNombreResidente(rs.getString("nombre_residente"));
        a.setNumeroApartamento(rs.getString("numero_apartamento"));

        return a;
    }
}
