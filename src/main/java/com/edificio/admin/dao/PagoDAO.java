package com.edificio.admin.dao;

import com.edificio.admin.model.Pago;
import com.edificio.admin.model.enums.MetodoPago;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla PAGOS.
 * Los pagos son inmutables una vez registrados (no hay DELETE fisico).
 */
public class PagoDAO implements CrudDAO<Pago> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<Pago> findAll() throws SQLException {
        List<Pago> lista = new ArrayList<>();
        String sql = "SELECT id_pago, id_cuota, fecha_pago, valor_pagado, metodo_pago, "
                   + "       referencia, comprobante_url, id_registrado_por, notas, fecha_registro "
                   + "FROM   PAGOS "
                   + "ORDER  BY fecha_registro DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public Pago findById(Integer id) throws SQLException {
        String sql = "SELECT id_pago, id_cuota, fecha_pago, valor_pagado, metodo_pago, "
                   + "       referencia, comprobante_url, id_registrado_por, notas, fecha_registro "
                   + "FROM   PAGOS WHERE id_pago = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Pagos registrados para una cuota especifica. */
    public List<Pago> findByCuota(Integer idCuota) throws SQLException {
        List<Pago> lista = new ArrayList<>();
        String sql = "SELECT id_pago, id_cuota, fecha_pago, valor_pagado, metodo_pago, "
                   + "       referencia, comprobante_url, id_registrado_por, notas, fecha_registro "
                   + "FROM   PAGOS WHERE id_cuota = ? ORDER BY fecha_registro";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idCuota);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    @Override
    public Integer insert(Pago p) throws SQLException {
        String sql = "BEGIN INSERT INTO PAGOS "
                   + "  (id_cuota, fecha_pago, valor_pagado, metodo_pago, "
                   + "   referencia, comprobante_url, id_registrado_por, notas) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                   + "RETURNING id_pago INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, p.getIdCuota());
            cs.setDate(2, Date.valueOf(p.getFechaPago()));
            cs.setBigDecimal(3, p.getValorPagado());
            cs.setString(4, p.getMetodoPago().name());
            cs.setString(5, p.getReferencia());
            if (p.getComprobanteUrl() != null) cs.setString(6, p.getComprobanteUrl());
            else                               cs.setNull(6, Types.VARCHAR);
            if (p.getRegistradoPor() != null) cs.setInt(7, p.getRegistradoPor());
            else                               cs.setNull(7, Types.NUMERIC);
            cs.setString(8, p.getNotas());
            cs.registerOutParameter(9, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(9);
        }
    }

    /** Los pagos no se modifican; este metodo es solo para cumplir la interfaz. */
    @Override
    public void update(Pago p) throws SQLException {
        throw new UnsupportedOperationException("Los pagos son inmutables.");
    }

    /** Los pagos no se eliminan; este metodo es solo para cumplir la interfaz. */
    @Override
    public void delete(Integer id) throws SQLException {
        throw new UnsupportedOperationException("Los pagos no se eliminan.");
    }

    // ---- mapeo ----

    private Pago mapear(ResultSet rs) throws SQLException {
        Pago p = new Pago();
        p.setIdPago(rs.getInt("id_pago"));
        p.setIdCuota(rs.getInt("id_cuota"));
        java.sql.Date fp = rs.getDate("fecha_pago");
        p.setFechaPago(fp != null ? fp.toLocalDate() : null);
        p.setValorPagado(rs.getBigDecimal("valor_pagado"));
        p.setMetodoPago(MetodoPago.valueOf(rs.getString("metodo_pago")));
        p.setReferencia(rs.getString("referencia"));
        p.setComprobanteUrl(rs.getString("comprobante_url"));

        int rp = rs.getInt("id_registrado_por");
        p.setRegistradoPor(rs.wasNull() ? null : rp);

        p.setNotas(rs.getString("notas"));
        Timestamp cr = rs.getTimestamp("fecha_registro");
        if (cr != null) p.setCreadoEn(cr.toLocalDateTime());
        return p;
    }
}
