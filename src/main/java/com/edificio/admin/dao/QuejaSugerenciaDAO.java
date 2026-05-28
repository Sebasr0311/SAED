package com.edificio.admin.dao;

import com.edificio.admin.model.QuejaSugerencia;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuejaSugerenciaDAO {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    public List<QuejaSugerencia> findAll() throws SQLException {
        List<QuejaSugerencia> lista = new ArrayList<>();
        String sql = "SELECT q.id_queja, q.id_apartamento, q.id_multa, q.tipo, q.categoria, "
                   + "       q.titulo, q.descripcion, q.foto_evidencia, q.estado, q.respuesta_admin, "
                   + "       q.prioridad, q.fecha_creacion, q.fecha_respuesta, q.creado_por, q.respondido_por, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       u.username AS nombre_admin "
                   + "FROM   QUEJAS_SUGERENCIAS q "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = q.id_apartamento "
                   + "LEFT JOIN USUARIOS uc ON uc.id_usuario = q.creado_por "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = uc.id_residente "
                   + "LEFT JOIN USUARIOS u ON u.id_usuario = q.respondido_por "
                   + "ORDER  BY q.fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public List<QuejaSugerencia> findByApartamento(int idApartamento) throws SQLException {
        List<QuejaSugerencia> lista = new ArrayList<>();
        String sql = "SELECT q.id_queja, q.id_apartamento, q.id_multa, q.tipo, q.categoria, "
                   + "       q.titulo, q.descripcion, q.foto_evidencia, q.estado, q.respuesta_admin, "
                   + "       q.prioridad, q.fecha_creacion, q.fecha_respuesta, q.creado_por, q.respondido_por, "
                   + "       a.numero AS numero_apartamento, "
                   + "       u.username AS nombre_admin "
                   + "FROM   QUEJAS_SUGERENCIAS q "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = q.id_apartamento "
                   + "LEFT JOIN USUARIOS u ON u.id_usuario = q.respondido_por "
                   + "WHERE  q.id_apartamento = ? "
                   + "ORDER  BY q.fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idApartamento);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    public QuejaSugerencia findById(int idQueja) throws SQLException {
        String sql = "SELECT q.id_queja, q.id_apartamento, q.id_multa, q.tipo, q.categoria, "
                   + "       q.titulo, q.descripcion, q.foto_evidencia, q.estado, q.respuesta_admin, "
                   + "       q.prioridad, q.fecha_creacion, q.fecha_respuesta, q.creado_por, q.respondido_por, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       u.username AS nombre_admin "
                   + "FROM   QUEJAS_SUGERENCIAS q "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = q.id_apartamento "
                   + "LEFT JOIN USUARIOS uc ON uc.id_usuario = q.creado_por "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = uc.id_residente "
                   + "LEFT JOIN USUARIOS u ON u.id_usuario = q.respondido_por "
                   + "WHERE  q.id_queja = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idQueja);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapear(rs);
                return null;
            }
        }
    }

    public List<QuejaSugerencia> findPendientes() throws SQLException {
        List<QuejaSugerencia> lista = new ArrayList<>();
        String sql = "SELECT q.id_queja, q.id_apartamento, q.id_multa, q.tipo, q.categoria, "
                   + "       q.titulo, q.descripcion, q.foto_evidencia, q.estado, q.respuesta_admin, "
                   + "       q.prioridad, q.fecha_creacion, q.fecha_respuesta, q.creado_por, q.respondido_por, "
                   + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente "
                   + "FROM   QUEJAS_SUGERENCIAS q "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = q.id_apartamento "
                   + "LEFT JOIN USUARIOS uc ON uc.id_usuario = q.creado_por "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = uc.id_residente "
                   + "WHERE  q.estado = 'PENDIENTE' "
                   + "ORDER  BY q.prioridad DESC, q.fecha_creacion ASC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public List<QuejaSugerencia> findByMulta(int idMulta) throws SQLException {
        List<QuejaSugerencia> lista = new ArrayList<>();
        String sql = "SELECT q.id_queja, q.id_apartamento, q.id_multa, q.tipo, q.categoria, "
                   + "       q.titulo, q.descripcion, q.foto_evidencia, q.estado, q.respuesta_admin, "
                   + "       q.prioridad, q.fecha_creacion, q.fecha_respuesta, q.creado_por, q.respondido_por, "
                    + "       a.numero AS numero_apartamento, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       u.username AS nombre_admin "
                   + "FROM   QUEJAS_SUGERENCIAS q "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = q.id_apartamento "
                   + "LEFT JOIN USUARIOS uc ON uc.id_usuario = q.creado_por "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = uc.id_residente "
                   + "LEFT JOIN USUARIOS u ON u.id_usuario = q.respondido_por "
                   + "WHERE  q.id_multa = ? "
                   + "ORDER  BY q.fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idMulta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    public Integer insert(QuejaSugerencia q) throws SQLException {
        String sql = "INSERT INTO QUEJAS_SUGERENCIAS "
                   + "(id_queja, id_apartamento, id_multa, tipo, categoria, titulo, descripcion, "
                   + " foto_evidencia, estado, prioridad, creado_por) "
                   + "VALUES (SEQ_QUEJAS_SUGERENCIAS.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, new String[]{"id_queja"})) {
            ps.setInt(1, q.getIdApartamento());
            if (q.getIdMulta() != null) ps.setInt(2, q.getIdMulta());
            else ps.setNull(2, Types.NUMERIC);
            ps.setString(3, q.getTipo());
            if (q.getCategoria() != null) ps.setString(4, q.getCategoria());
            else ps.setNull(4, Types.VARCHAR);
            ps.setString(5, q.getTitulo());
            ps.setString(6, q.getDescripcion());
            if (q.getFotoEvidencia() != null) ps.setString(7, q.getFotoEvidencia());
            else ps.setNull(7, Types.CLOB);
            ps.setString(8, q.getEstado() != null ? q.getEstado() : "PENDIENTE");
            ps.setString(9, q.getPrioridad() != null ? q.getPrioridad() : "MEDIA");
            ps.setInt(10, q.getCreadoPor());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) throw new SQLException("No se pudo obtener el ID de la queja generada");
                return rs.getInt(1);
            }
        }
    }

    public void responder(int idQueja, String respuesta, int respondidoPor) throws SQLException {
        String sql = "UPDATE QUEJAS_SUGERENCIAS SET respuesta_admin = ?, estado = 'RESUELTA', "
                   + "       fecha_respuesta = SYSTIMESTAMP, respondido_por = ? "
                   + "WHERE id_queja = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, respuesta);
            ps.setInt(2, respondidoPor);
            ps.setInt(3, idQueja);
            ps.executeUpdate();
        }
    }

    public void cambiarEstado(int idQueja, String nuevoEstado) throws SQLException {
        String sql = "UPDATE QUEJAS_SUGERENCIAS SET estado = ? WHERE id_queja = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            ps.setInt(2, idQueja);
            ps.executeUpdate();
        }
    }

    public void cambiarPrioridad(int idQueja, String nuevaPrioridad) throws SQLException {
        String sql = "UPDATE QUEJAS_SUGERENCIAS SET prioridad = ? WHERE id_queja = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, nuevaPrioridad);
            ps.setInt(2, idQueja);
            ps.executeUpdate();
        }
    }

    private QuejaSugerencia mapear(ResultSet rs) throws SQLException {
        QuejaSugerencia q = new QuejaSugerencia();
        q.setIdQueja(rs.getInt("id_queja"));
        q.setIdApartamento(rs.getInt("id_apartamento"));
        int idMulta = rs.getInt("id_multa");
        q.setIdMulta(rs.wasNull() ? null : idMulta);
        q.setTipo(rs.getString("tipo"));
        q.setCategoria(rs.getString("categoria"));
        q.setTitulo(rs.getString("titulo"));
        q.setDescripcion(rs.getString("descripcion"));
        try { q.setFotoEvidencia(rs.getString("foto_evidencia")); } catch (SQLException e) { /* opcional */ }
        q.setEstado(rs.getString("estado"));
        q.setRespuestaAdmin(rs.getString("respuesta_admin"));
        q.setPrioridad(rs.getString("prioridad"));
        Timestamp fc = rs.getTimestamp("fecha_creacion");
        if (fc != null) q.setFechaCreacion(fc.toLocalDateTime());
        Timestamp fr = rs.getTimestamp("fecha_respuesta");
        if (fr != null) q.setFechaRespuesta(fr.toLocalDateTime());
        q.setCreadoPor(rs.getInt("creado_por"));
        int respondidoPor = rs.getInt("respondido_por");
        q.setRespondidoPor(rs.wasNull() ? null : respondidoPor);
        try { q.setNumeroApartamento(rs.getString("numero_apartamento")); } catch (SQLException e) { /* opcional */ }
        try { q.setNombreResidente(rs.getString("nombre_residente")); } catch (SQLException e) { /* opcional */ }
        try { q.setNombreAdmin(rs.getString("nombre_admin")); } catch (SQLException e) { /* opcional */ }
        return q;
    }
}
