package com.edificio.admin.dao;

import com.edificio.admin.model.Pago;
import com.edificio.admin.model.enums.MetodoPago;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO para la tabla PAGOS.
 * Los pagos son inmutables una vez registrados (no hay DELETE fisico).
 */
public class PagoDAO extends BaseDAO implements CrudDAO<Pago> {

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

    public BigDecimal sumAll() throws SQLException {
        String sql = "SELECT NVL(SUM(valor_pagado), 0) FROM PAGOS";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public List<Map<String, Object>> sumByTipoCuota() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        String sql = "SELECT cq.tipo_cuota, NVL(SUM(p.valor_pagado), 0) AS total "
                   + "FROM   PAGOS p "
                   + "JOIN   CUOTAS_ARRIENDO cq ON cq.id_cuota = p.id_cuota "
                   + "GROUP  BY cq.tipo_cuota";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("tipo", rs.getString("tipo_cuota"));
                m.put("total", rs.getBigDecimal("total"));
                lista.add(m);
            }
        }
        return lista;
    }

    public List<Map<String, Object>> monthlyBreakdown() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        String sql = "SELECT EXTRACT(YEAR FROM p.fecha_pago) AS anio, "
                   + "       EXTRACT(MONTH FROM p.fecha_pago) AS mes, "
                   + "       NVL(SUM(p.valor_pagado), 0) AS total "
                   + "FROM   PAGOS p "
                   + "GROUP  BY EXTRACT(YEAR FROM p.fecha_pago), EXTRACT(MONTH FROM p.fecha_pago) "
                   + "ORDER  BY anio DESC, mes DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("anio", rs.getInt("anio"));
                m.put("mes", rs.getInt("mes"));
                m.put("total", rs.getBigDecimal("total"));
                lista.add(m);
            }
        }
        return lista;
    }

    public List<Map<String, Object>> findAllRegistrados() throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        String sql = "SELECT tipo_pago, id, fecha, valor, metodo, apartamento, residente, descripcion "
                   + "FROM ( "
                   + "  SELECT 'CUOTA' AS tipo_pago, p.id_pago AS id, p.fecha_pago AS fecha, "
                   + "         p.valor_pagado AS valor, NVL(p.metodo_pago, 'EFECTIVO') AS metodo, "
                   + "         a.numero AS apartamento, "
                   + "         NVL(r.nombres || ' ' || r.apellidos, '-') AS residente, "
                   + "         cq.tipo_cuota || ' ' || cq.anio || '/' || LPAD(cq.mes, 2, '0') AS descripcion "
                   + "  FROM PAGOS p "
                   + "  JOIN CUOTAS_ARRIENDO cq ON cq.id_cuota = p.id_cuota "
                   + "  JOIN CONTRATOS c ON c.id_contrato = cq.id_contrato "
                   + "  JOIN APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "  LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "  LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente "
                   + "  UNION ALL "
                   + "  SELECT 'MULTA' AS tipo_pago, m.id_multa AS id, m.fecha_pago AS fecha, "
                   + "         m.monto AS valor, NVL(m.metodo_pago, 'EFECTIVO') AS metodo, "
                   + "         a.numero AS apartamento, "
                   + "         NVL(r.nombres || ' ' || r.apellidos, '-') AS residente, "
                   + "         m.tipo AS descripcion "
                   + "  FROM MULTAS m "
                   + "  JOIN APARTAMENTOS a ON a.id_apartamento = m.id_apartamento "
                   + "  LEFT JOIN CONTRATOS c ON c.id_apartamento = m.id_apartamento AND c.estado = 'ACTIVO' "
                   + "  LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "  LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente "
                   + "  WHERE m.estado = 'PAGADA' AND m.fecha_pago IS NOT NULL "
                   + ") ORDER BY fecha DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("tipoPago", rs.getString("tipo_pago"));
                m.put("id", rs.getInt("id"));
                java.sql.Date f = rs.getDate("fecha");
                m.put("fecha", f != null ? f.toLocalDate().toString() : null);
                m.put("valor", rs.getBigDecimal("valor"));
                m.put("metodo", rs.getString("metodo"));
                m.put("apartamento", rs.getString("apartamento"));
                m.put("residente", rs.getString("residente"));
                m.put("descripcion", rs.getString("descripcion"));
                lista.add(m);
            }
        }
        return lista;
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
