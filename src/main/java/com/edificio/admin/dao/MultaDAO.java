package com.edificio.admin.dao;

import com.edificio.admin.model.Multa;
import com.edificio.admin.model.enums.EstadoMulta;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MultaDAO {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    public List<Multa> findAllConResidente() throws SQLException {
        List<Multa> lista = new ArrayList<>();
        String sql = "SELECT m.id_multa, m.id_apartamento, m.id_mensaje, m.tipo, m.monto, "
                   + "       m.estado, m.descripcion, m.fecha_creacion, m.fecha_pago, m.creado_por, "
                   + "       m.registrado_pago_por, m.metodo_pago, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente "
                   + "FROM   MULTAS m "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = m.id_apartamento "
                   + "LEFT JOIN CONTRATOS c ON c.id_apartamento = m.id_apartamento AND c.estado = 'ACTIVO' "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente AND r.activo = 1 "
                   + "ORDER  BY m.fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Multa m = mapear(rs);
                m.setNombreResidente(rs.getString("nombre_residente"));
                lista.add(m);
            }
        }
        return lista;
    }

    public List<Multa> findByApartamento(int idApartamento) throws SQLException {
        List<Multa> lista = new ArrayList<>();
        String sql = "SELECT m.id_multa, m.id_apartamento, m.id_mensaje, m.tipo, m.monto, "
                   + "       m.estado, m.descripcion, m.fecha_creacion, m.fecha_pago, m.creado_por, "
                   + "       m.registrado_pago_por, m.metodo_pago, "
                   + "       a.numero AS numero_apartamento "
                   + "FROM   MULTAS m "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = m.id_apartamento "
                   + "WHERE  m.id_apartamento = ? "
                   + "ORDER  BY m.fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idApartamento);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public Multa findById(int idMulta) throws SQLException {
        String sql = "SELECT m.id_multa, m.id_apartamento, m.id_mensaje, m.tipo, m.monto, "
                   + "       m.estado, m.descripcion, m.foto_evidencia, m.fecha_creacion, m.fecha_pago, m.creado_por, "
                   + "       m.registrado_pago_por, m.metodo_pago, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       u.username AS nombre_portero, "
                   + "       msg.fecha_creacion AS fecha_aviso_ruido "
                   + "FROM   MULTAS m "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = m.id_apartamento "
                   + "LEFT JOIN CONTRATOS c ON c.id_apartamento = m.id_apartamento AND c.estado = 'ACTIVO' "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = cr.id_residente AND r.activo = 1 "
                   + "LEFT JOIN USUARIOS u ON u.id_usuario = m.creado_por "
                   + "LEFT JOIN BUZON msg ON msg.id_mensaje = m.id_mensaje "
                   + "WHERE  m.id_multa = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idMulta);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Multa m = mapear(rs);
                    m.setNombreResidente(rs.getString("nombre_residente"));
                    m.setNombrePortero(rs.getString("nombre_portero"));
                    Timestamp fechaAviso = rs.getTimestamp("fecha_aviso_ruido");
                    if (fechaAviso != null) {
                        m.setFechaAvisoRuido(fechaAviso.toLocalDateTime());
                    }
                    return m;
                }
            }
        }
        return null;
    }

    public Integer insert(Multa m) throws SQLException {
        String sql = "INSERT INTO MULTAS (id_apartamento, id_mensaje, tipo, monto, estado, descripcion, foto_evidencia, creado_por) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, new String[]{"id_multa"})) {
            ps.setInt(1, m.getIdApartamento());
            if (m.getIdMensaje() != null) ps.setInt(2, m.getIdMensaje());
            else ps.setNull(2, Types.NUMERIC);
            ps.setString(3, m.getTipo());
            ps.setBigDecimal(4, m.getMonto());
            ps.setString(5, m.getEstado().name());
            if (m.getDescripcion() != null) ps.setString(6, m.getDescripcion());
            else ps.setNull(6, Types.VARCHAR);
            if (m.getFotoEvidencia() != null) ps.setString(7, m.getFotoEvidencia());
            else ps.setNull(7, Types.CLOB);
            ps.setInt(8, m.getCreadoPor());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No se pudo obtener el ID de la multa generada");
                return rs.getInt(1);
            }
        }
    }

    public void pagar(int idMulta, Integer registradoPagoPor,
                      String metodoPago) throws SQLException {
        String sql = "UPDATE MULTAS SET estado = 'PAGADA', fecha_pago = CURRENT_TIMESTAMP, "
                   + "registrado_pago_por = ?, metodo_pago = ? "
                   + "WHERE id_multa = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (registradoPagoPor != null) ps.setInt(1, registradoPagoPor);
            else ps.setNull(1, Types.NUMERIC);
            if (metodoPago != null) ps.setString(2, metodoPago);
            else ps.setNull(2, Types.VARCHAR);
            ps.setInt(3, idMulta);
            ps.executeUpdate();
        }
    }

    public void anular(int idMulta) throws SQLException {
        String sql = "UPDATE MULTAS SET estado = 'ANULADA' WHERE id_multa = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idMulta);
            ps.executeUpdate();
        }
    }

    private Multa mapear(ResultSet rs) throws SQLException {
        Multa m = new Multa();
        m.setIdMulta(rs.getInt("id_multa"));
        m.setIdApartamento(rs.getInt("id_apartamento"));
        int msg = rs.getInt("id_mensaje");
        m.setIdMensaje(rs.wasNull() ? null : msg);
        m.setTipo(rs.getString("tipo"));
        m.setMonto(rs.getBigDecimal("monto"));
        m.setEstado(EstadoMulta.valueOf(rs.getString("estado")));
        m.setDescripcion(rs.getString("descripcion"));
        try { m.setFotoEvidencia(rs.getString("foto_evidencia")); } catch (SQLException e) { /* no en consultas de lista */ }
        try {
            int rpb = rs.getInt("registrado_pago_por");
            m.setRegistradoPagoPor(rs.wasNull() ? null : rpb);
        } catch (SQLException e) { /* columna opcional */ }
        try { m.setMetodoPago(rs.getString("metodo_pago")); } catch (SQLException e) { /* columna opcional */ }
        Timestamp fc = rs.getTimestamp("fecha_creacion");
        if (fc != null) m.setFechaCreacion(fc.toLocalDateTime());
        Timestamp fp = rs.getTimestamp("fecha_pago");
        if (fp != null) m.setFechaPago(fp.toLocalDateTime());
        m.setCreadoPor(rs.getInt("creado_por"));
        m.setNumeroApartamento(rs.getString("numero_apartamento"));
        return m;
    }
}
