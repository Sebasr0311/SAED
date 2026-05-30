package com.edificio.admin.dao;

import com.edificio.admin.model.Contrato;
import com.edificio.admin.model.enums.EstadoContrato;
import com.edificio.admin.model.enums.TipoContrato;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla CONTRATOS.
 * El estado del apartamento se sincroniza via TRG_CONT_SYNC_APARTAMENTO;
 * basta con UPDATE de estado aqui.
 */
public class ContratoDAO implements CrudDAO<Contrato> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<Contrato> findAll() throws SQLException {
        List<Contrato> lista = new ArrayList<>();
        String sql = "SELECT c.id_contrato, c.id_apartamento, c.id_tutor, c.id_registrado_por, c.fecha_inicio, c.fecha_fin, "
                   + "       c.valor_mensual, c.dia_pago, c.dias_gracia, c.porcentaje_mora, c.contrato_pdf_url, "
                   + "       c.tipo_contrato, c.estado, c.observaciones, c.fecha_registro, c.actualizado_en, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       r.id_residente AS id_residente "
                   + "FROM   CONTRATOS c "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente "
                   + "ORDER  BY c.fecha_registro DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public Contrato findById(Integer id) throws SQLException {
        String sql = "SELECT c.id_contrato, c.id_apartamento, c.id_tutor, c.id_registrado_por, c.fecha_inicio, c.fecha_fin, "
                   + "       c.valor_mensual, c.dia_pago, c.dias_gracia, c.porcentaje_mora, c.contrato_pdf_url, "
                   + "       c.tipo_contrato, c.estado, c.observaciones, c.fecha_registro, c.actualizado_en, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       r.id_residente AS id_residente "
                   + "FROM   CONTRATOS c "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente "
                   + "WHERE  c.id_contrato = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Devuelve el contrato ACTIVO de un apartamento (0 o 1 resultado). */
    public Contrato findActivoByApartamento(Integer idApartamento) throws SQLException {
        String sql = "SELECT c.id_contrato, c.id_apartamento, c.id_tutor, c.id_registrado_por, c.fecha_inicio, c.fecha_fin, "
                   + "       c.valor_mensual, c.dia_pago, c.dias_gracia, c.porcentaje_mora, c.contrato_pdf_url, "
                   + "       c.tipo_contrato, c.estado, c.observaciones, c.fecha_registro, c.actualizado_en, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       r.id_residente AS id_residente "
                   + "FROM   CONTRATOS c "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente "
                   + "WHERE  c.id_apartamento = ? AND c.estado = 'ACTIVO'";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idApartamento);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Historial de contratos de un apartamento. */
    public List<Contrato> findByApartamento(Integer idApartamento) throws SQLException {
        List<Contrato> lista = new ArrayList<>();
        String sql = "SELECT c.id_contrato, c.id_apartamento, c.id_tutor, c.id_registrado_por, c.fecha_inicio, c.fecha_fin, "
                   + "       c.valor_mensual, c.dia_pago, c.dias_gracia, c.porcentaje_mora, c.contrato_pdf_url, "
                   + "       c.tipo_contrato, c.estado, c.observaciones, c.fecha_registro, c.actualizado_en, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       r.id_residente AS id_residente "
                   + "FROM   CONTRATOS c "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente "
                   + "WHERE  c.id_apartamento = ? "
                   + "ORDER  BY c.fecha_inicio DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idApartamento);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    @Override
    public Integer insert(Contrato c) throws SQLException {
        String sql = "BEGIN INSERT INTO CONTRATOS "
                   + "  (id_apartamento, id_tutor, id_registrado_por, fecha_inicio, fecha_fin, "
                   + "   valor_mensual, dia_pago, dias_gracia, porcentaje_mora, contrato_pdf_url, "
                   + "   tipo_contrato, estado, observaciones) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                   + "RETURNING id_contrato INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, c.getIdApartamento());
            if (c.getIdTutor() != null) cs.setInt(2, c.getIdTutor());
            else                         cs.setNull(2, Types.NUMERIC);
            if (c.getIdRegistradoPor() != null) cs.setInt(3, c.getIdRegistradoPor());
            else                                 cs.setNull(3, Types.NUMERIC);
            cs.setDate(4, Date.valueOf(c.getFechaInicio()));
            if (c.getFechaFin() != null) cs.setDate(5, Date.valueOf(c.getFechaFin()));
            else                          cs.setNull(5, Types.DATE);
            cs.setBigDecimal(6, c.getValorMensual());
            cs.setInt(7, c.getDiaPago());
            cs.setInt(8, c.getDiasGracia());
            cs.setBigDecimal(9, c.getPorcentajeMora());
            if (c.getContratoPdfUrl() != null) cs.setString(10, c.getContratoPdfUrl());
            else                                cs.setNull(10, Types.VARCHAR);
            if (c.getTipoContrato() != null) cs.setString(11, c.getTipoContrato().name());
            else                             cs.setNull(11, Types.VARCHAR);
            cs.setString(12, c.getEstado() != null ? c.getEstado().name()
                                                    : EstadoContrato.PENDIENTE_FIRMA.name());
            cs.setString(13, c.getNotas());
            cs.registerOutParameter(14, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(14);
        }
    }

    @Override
    public void update(Contrato c) throws SQLException {
        String sql = "UPDATE CONTRATOS "
                   + "SET    id_apartamento = ?, id_tutor = ?, fecha_inicio = ?, fecha_fin = ?, "
                   + "       valor_mensual = ?, dia_pago = ?, dias_gracia = ?, porcentaje_mora = ?, "
                   + "       contrato_pdf_url = ?, tipo_contrato = ?, estado = ?, observaciones = ? "
                   + "WHERE  id_contrato = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, c.getIdApartamento());
            if (c.getIdTutor() != null) ps.setInt(2, c.getIdTutor());
            else                         ps.setNull(2, Types.NUMERIC);
            ps.setDate(3, Date.valueOf(c.getFechaInicio()));
            if (c.getFechaFin() != null) ps.setDate(4, Date.valueOf(c.getFechaFin()));
            else                          ps.setNull(4, Types.DATE);
            ps.setBigDecimal(5, c.getValorMensual());
            ps.setInt(6, c.getDiaPago());
            ps.setInt(7, c.getDiasGracia());
            ps.setBigDecimal(8, c.getPorcentajeMora());
            if (c.getContratoPdfUrl() != null) ps.setString(9, c.getContratoPdfUrl());
            else                                ps.setNull(9, Types.VARCHAR);
            if (c.getTipoContrato() != null) ps.setString(10, c.getTipoContrato().name());
            else                             ps.setNull(10, Types.VARCHAR);
            ps.setString(11, c.getEstado().name());
            ps.setString(12, c.getNotas());
            ps.setInt(13, c.getIdContrato());
            ps.executeUpdate();
        }
    }

    /** Soft-delete: cambia estado a CANCELADO. */
    @Override
    public void delete(Integer id) throws SQLException {
        String sql = "UPDATE CONTRATOS SET estado = 'CANCELADO' WHERE id_contrato = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---- mapeo ----

    private Contrato mapear(ResultSet rs) throws SQLException {
        Contrato c = new Contrato();
        c.setIdContrato(rs.getInt("id_contrato"));
        c.setIdApartamento(rs.getInt("id_apartamento"));

        int t = rs.getInt("id_tutor");
        c.setIdTutor(rs.wasNull() ? null : t);

        int rp = rs.getInt("id_registrado_por");
        c.setIdRegistradoPor(rs.wasNull() ? null : rp);
        c.setFechaInicio(rs.getDate("fecha_inicio").toLocalDate());

        Date ff = rs.getDate("fecha_fin");
        if (ff != null) c.setFechaFin(ff.toLocalDate());

        BigDecimal vm = rs.getBigDecimal("valor_mensual");
        c.setValorMensual(vm);

        try { c.setDiaPago(rs.getInt("dia_pago")); } catch (SQLException e) { /* columna no disponible */ }
        try { c.setDiasGracia(rs.getInt("dias_gracia")); } catch (SQLException e) { /* columna no disponible */ }
        try { c.setPorcentajeMora(rs.getBigDecimal("porcentaje_mora")); } catch (SQLException e) { /* columna no disponible */ }
        try { c.setContratoPdfUrl(rs.getString("contrato_pdf_url")); } catch (SQLException e) { /* columna no disponible */ }
        try {
            String tc = rs.getString("tipo_contrato");
            if (tc != null) c.setTipoContrato(TipoContrato.valueOf(tc));
        } catch (SQLException e) { /* columna no disponible */ }

        c.setEstado(EstadoContrato.valueOf(rs.getString("estado")));
        c.setNotas(rs.getString("observaciones"));

        Timestamp cr = rs.getTimestamp("fecha_registro");
        if (cr != null) c.setCreadoEn(cr.toLocalDateTime());
        Timestamp ac = rs.getTimestamp("actualizado_en");
        if (ac != null) c.setActualizadoEn(ac.toLocalDateTime());

        try { c.setNumeroApartamento(rs.getString("numero_apartamento")); } catch (SQLException e) { /* columna no disponible */ }
        try { c.setNombreResidente(rs.getString("nombre_residente")); } catch (SQLException e) { /* columna no disponible */ }
        try {
            int ir = rs.getInt("id_residente");
            c.setIdResidente(rs.wasNull() ? null : ir);
        } catch (SQLException e) { /* columna no disponible */ }

        return c;
    }

    /**
     * Marca como VENCIDO todo contrato ACTIVO cuya fecha_fin ya pasó,
     * y pone el apartamento en DISPONIBLE.
     * Se llama antes de listarTodos() para mantener estados consistentes.
     */
    public void expiracionAutomatica() throws SQLException {
        // 1. Obtener IDs de apartamentos afectados antes de actualizar
        String sqlSelect = "SELECT id_apartamento FROM CONTRATOS "
                         + "WHERE estado = 'ACTIVO' AND fecha_fin IS NOT NULL AND fecha_fin < TRUNC(CURRENT_DATE)";
        List<Integer> aptosAfectados = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sqlSelect);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { aptosAfectados.add(rs.getInt("id_apartamento")); }
        }

        if (aptosAfectados.isEmpty()) return;

        // 2. Marcar contratos como VENCIDO
        String sqlVencer = "UPDATE CONTRATOS SET estado = 'VENCIDO', actualizado_en = CURRENT_DATE "
                         + "WHERE estado = 'ACTIVO' AND fecha_fin IS NOT NULL AND fecha_fin < TRUNC(CURRENT_DATE)";
        try (PreparedStatement ps = conn().prepareStatement(sqlVencer)) {
            ps.executeUpdate();
        }

        // 3. Liberar los apartamentos (DISPONIBLE)
        String sqlApt = "UPDATE APARTAMENTOS SET estado = 'DISPONIBLE' WHERE id_apartamento = ?";
        try (PreparedStatement ps = conn().prepareStatement(sqlApt)) {
            for (Integer idApt : aptosAfectados) {
                ps.setInt(1, idApt);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
