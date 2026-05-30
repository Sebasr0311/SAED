package com.edificio.admin.dao;

import com.edificio.admin.model.CuotaArriendo;
import com.edificio.admin.model.enums.EstadoCuota;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla CUOTAS_ARRIENDO.
 * Unicidad por (id_contrato, anio, mes, tipo_cuota) garantizada en BD.
 */
public class CuotaArriendoDAO implements CrudDAO<CuotaArriendo> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<CuotaArriendo> findAll() throws SQLException {
        List<CuotaArriendo> lista = new ArrayList<>();
        String sql = "SELECT cq.id_cuota, cq.id_contrato, cq.anio, cq.mes, cq.tipo_cuota, cq.fecha_limite, "
                   + "       cq.valor_base, cq.valor_mora, cq.valor_total, cq.estado, cq.actualizado_en, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente "
                   + "FROM   CUOTAS_ARRIENDO cq "
                   + "JOIN   CONTRATOS c ON c.id_contrato = cq.id_contrato "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente "
                   + "ORDER  BY cq.fecha_limite DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public CuotaArriendo findById(Integer id) throws SQLException {
        String sql = "SELECT cq.id_cuota, cq.id_contrato, cq.anio, cq.mes, cq.tipo_cuota, cq.fecha_limite, "
                   + "       cq.valor_base, cq.valor_mora, cq.valor_total, cq.estado, cq.actualizado_en, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente "
                   + "FROM   CUOTAS_ARRIENDO cq "
                   + "JOIN   CONTRATOS c ON c.id_contrato = cq.id_contrato "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente "
                   + "WHERE  cq.id_cuota = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Cuotas de un contrato ordenadas por fecha (mas reciente primero). */
    public List<CuotaArriendo> findByContrato(Integer idContrato) throws SQLException {
        List<CuotaArriendo> lista = new ArrayList<>();
        String sql = "SELECT cq.id_cuota, cq.id_contrato, cq.anio, cq.mes, cq.tipo_cuota, cq.fecha_limite, "
                   + "       cq.valor_base, cq.valor_mora, cq.valor_total, cq.estado, cq.actualizado_en, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       NVL(SUM(p.valor_pagado), 0) AS total_pagado, "
                   + "       cq.valor_total - NVL(SUM(p.valor_pagado), 0) AS saldo_pendiente "
                   + "FROM   CUOTAS_ARRIENDO cq "
                   + "JOIN   CONTRATOS c ON c.id_contrato = cq.id_contrato "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente "
                   + "LEFT JOIN PAGOS p ON p.id_cuota = cq.id_cuota "
                   + "WHERE  cq.id_contrato = ? "
                   + "GROUP  BY cq.id_cuota, cq.id_contrato, cq.anio, cq.mes, cq.tipo_cuota, cq.fecha_limite, "
                   + "          cq.valor_base, cq.valor_mora, cq.valor_total, cq.estado, cq.actualizado_en, "
                   + "          a.numero, r.nombres, r.apellidos "
                   + "ORDER  BY cq.anio DESC, cq.mes DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idContrato);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    /** Cuotas pendientes / vencidas / en mora para el modulo de cartera. */
    public List<CuotaArriendo> findPendientes() throws SQLException {
        List<CuotaArriendo> lista = new ArrayList<>();
        String sql = "SELECT cq.id_cuota, cq.id_contrato, cq.anio, cq.mes, cq.tipo_cuota, cq.fecha_limite, "
                   + "       cq.valor_base, cq.valor_mora, cq.valor_total, cq.estado, cq.actualizado_en, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       NVL(SUM(p.valor_pagado), 0) AS total_pagado, "
                   + "       cq.valor_total - NVL(SUM(p.valor_pagado), 0) AS saldo_pendiente "
                   + "FROM   CUOTAS_ARRIENDO cq "
                   + "JOIN   CONTRATOS c ON c.id_contrato = cq.id_contrato "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente "
                   + "LEFT JOIN PAGOS p ON p.id_cuota = cq.id_cuota "
                   + "WHERE  cq.estado IN ('PENDIENTE','VENCIDA','EN_MORA') "
                   + "GROUP  BY cq.id_cuota, cq.id_contrato, cq.anio, cq.mes, cq.tipo_cuota, cq.fecha_limite, "
                   + "          cq.valor_base, cq.valor_mora, cq.valor_total, cq.estado, cq.actualizado_en, "
                   + "          a.numero, r.nombres, r.apellidos "
                   + "ORDER  BY cq.fecha_limite ASC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public Integer insert(CuotaArriendo q) throws SQLException {
        String sql = "BEGIN INSERT INTO CUOTAS_ARRIENDO "
                   + "  (id_contrato, anio, mes, tipo_cuota, fecha_limite, valor_base, "
                   + "   valor_mora, valor_total, estado) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                   + "RETURNING id_cuota INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, q.getIdContrato());
            cs.setInt(2, q.getAnio());
            cs.setInt(3, q.getMes());
            cs.setString(4, q.getTipoCuota() != null ? q.getTipoCuota() : "ARRIENDO");
            cs.setDate(5, Date.valueOf(q.getFechaLimite()));
            cs.setBigDecimal(6, q.getValorBase());
            cs.setBigDecimal(7, q.getValorMora() != null ? q.getValorMora() : BigDecimal.ZERO);
            cs.setBigDecimal(8, q.getValorTotal());
            cs.setString(9, q.getEstado() != null ? q.getEstado().name()
                                                   : EstadoCuota.PENDIENTE.name());
            cs.registerOutParameter(10, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(10);
        }
    }

    @Override
    public void update(CuotaArriendo q) throws SQLException {
        String sql = "UPDATE CUOTAS_ARRIENDO "
                   + "SET    fecha_limite = ?, valor_base = ?, valor_mora = ?, "
                   + "       valor_total = ?, estado = ? "
                   + "WHERE  id_cuota = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(q.getFechaLimite()));
            ps.setBigDecimal(2, q.getValorBase());
            ps.setBigDecimal(3, q.getValorMora() != null ? q.getValorMora() : BigDecimal.ZERO);
            ps.setBigDecimal(4, q.getValorTotal());
            ps.setString(5, q.getEstado().name());
            ps.setInt(6, q.getIdCuota());
            ps.executeUpdate();
        }
    }

    /** Soft-delete: estado = ANULADA. */
    @Override
    public void delete(Integer id) throws SQLException {
        String sql = "UPDATE CUOTAS_ARRIENDO SET estado = 'ANULADA' WHERE id_cuota = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---- mapeo ----

    private CuotaArriendo mapear(ResultSet rs) throws SQLException {
        CuotaArriendo q = new CuotaArriendo();
        q.setIdCuota(rs.getInt("id_cuota"));
        q.setIdContrato(rs.getInt("id_contrato"));
        q.setAnio(rs.getInt("anio"));
        q.setMes(rs.getInt("mes"));
        try { q.setTipoCuota(rs.getString("tipo_cuota")); } catch (SQLException e) { q.setTipoCuota("ARRIENDO"); }
        q.setFechaLimite(rs.getDate("fecha_limite").toLocalDate());
        q.setValorBase(rs.getBigDecimal("valor_base"));
        q.setValorMora(rs.getBigDecimal("valor_mora"));
        q.setValorTotal(rs.getBigDecimal("valor_total"));
        q.setEstado(EstadoCuota.valueOf(rs.getString("estado")));
        Timestamp ac = rs.getTimestamp("actualizado_en");
        if (ac != null) q.setActualizadoEn(ac.toLocalDateTime());
        try { q.setNumeroApartamento(rs.getString("numero_apartamento")); } catch (SQLException e) { /* columna no disponible */ }
        try { q.setNombreResidente(rs.getString("nombre_residente")); } catch (SQLException e) { /* columna no disponible */ }
        try { q.setTotalPagado(rs.getBigDecimal("total_pagado")); } catch (SQLException e) { /* columna no disponible */ }
        try { q.setSaldoPendiente(rs.getBigDecimal("saldo_pendiente")); } catch (SQLException e) { /* columna no disponible */ }
        return q;
    }
}
