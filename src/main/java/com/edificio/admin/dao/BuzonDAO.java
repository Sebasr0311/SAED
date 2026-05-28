package com.edificio.admin.dao;

import com.edificio.admin.model.Buzon;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BuzonDAO {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    public List<Buzon> findByApartamento(int idApartamento) throws SQLException {
        List<Buzon> lista = new ArrayList<>();
        String sql = "SELECT id_mensaje, id_apartamento, id_visita, tipo, titulo, cuerpo, "
                   + "       foto_captura, "
                   + "       leido, leido_en, entregado, entregado_en, "
                   + "       confirmado, confirmado_en, creado_por, fecha_creacion "
                   + "FROM   BUZON "
                   + "WHERE  id_apartamento = ? OR id_apartamento IS NULL "
                   + "ORDER  BY fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idApartamento);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public List<Buzon> findPendientesByApartamento(int idApartamento) throws SQLException {
        List<Buzon> lista = new ArrayList<>();
        String sql = "SELECT id_mensaje, id_apartamento, id_visita, tipo, titulo, cuerpo, "
                   + "       foto_captura, "
                   + "       leido, leido_en, entregado, entregado_en, "
                   + "       confirmado, confirmado_en, creado_por, fecha_creacion "
                   + "FROM   BUZON "
                   + "WHERE  (id_apartamento = ? OR id_apartamento IS NULL) "
                   + "AND    (leido = 0 OR (tipo = 'CONFIRMAR_VISITA' AND confirmado IS NULL)) "
                   + "ORDER  BY fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idApartamento);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public Buzon findByVisitaAndPendiente(int idVisita) throws SQLException {
        String sql = "SELECT id_mensaje, id_apartamento, id_visita, tipo, titulo, cuerpo, "
                   + "       foto_captura, "
                   + "       leido, leido_en, entregado, entregado_en, "
                   + "       confirmado, confirmado_en, creado_por, fecha_creacion "
                   + "FROM   BUZON "
                   + "WHERE  id_visita = ? AND tipo = 'CONFIRMAR_VISITA' "
                   + "AND    confirmado IS NULL "
                   + "ORDER  BY fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idVisita);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public Buzon findByVisita(int idVisita) throws SQLException {
        String sql = "SELECT id_mensaje, id_apartamento, id_visita, tipo, titulo, cuerpo, "
                   + "       foto_captura, "
                   + "       leido, leido_en, entregado, entregado_en, "
                   + "       confirmado, confirmado_en, creado_por, fecha_creacion "
                   + "FROM   BUZON "
                   + "WHERE  id_visita = ? AND tipo = 'CONFIRMAR_VISITA' "
                   + "ORDER  BY fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idVisita);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /**
     * Busca TODOS los mensajes asociados a una visita (incluye CONFIRMAR_VISITA y VISITA_CAPTURA).
     * Útil para obtener la foto capturada del visitante.
     */
    public List<Buzon> findAllByVisita(int idVisita) throws SQLException {
        List<Buzon> lista = new ArrayList<>();
        String sql = "SELECT id_mensaje, id_apartamento, id_visita, tipo, titulo, cuerpo, "
                   + "       foto_captura, "
                   + "       leido, leido_en, entregado, entregado_en, "
                   + "       confirmado, confirmado_en, creado_por, fecha_creacion "
                   + "FROM   BUZON "
                   + "WHERE  id_visita = ? "
                   + "ORDER  BY fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idVisita);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public List<Buzon> findAllAvisos() throws SQLException {
        List<Buzon> lista = new ArrayList<>();
        String sql = "SELECT b.id_mensaje, b.id_apartamento, b.id_visita, b.tipo, b.titulo, b.cuerpo, "
                   + "       b.foto_captura, "
                   + "       b.leido, b.leido_en, b.entregado, b.entregado_en, "
                   + "       b.confirmado, b.confirmado_en, b.creado_por, b.fecha_creacion, "
                   + "       a.numero AS numero_apartamento "
                   + "FROM   BUZON b "
                   + "LEFT JOIN APARTAMENTOS a ON b.id_apartamento = a.id_apartamento "
                   + "WHERE  b.tipo = 'AVISO' "
                   + "ORDER  BY b.fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Buzon b = mapear(rs);
                b.setNumeroApartamento(rs.getString("numero_apartamento"));
                lista.add(b);
            }
        }
        return lista;
    }

    public Buzon findById(int idMensaje) throws SQLException {
        String sql = "SELECT id_mensaje, id_apartamento, id_visita, tipo, titulo, cuerpo, "
                   + "       foto_captura, "
                   + "       leido, leido_en, entregado, entregado_en, "
                   + "       confirmado, confirmado_en, creado_por, fecha_creacion "
                   + "FROM   BUZON WHERE id_mensaje = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idMensaje);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public List<Buzon> findQuejasRuidoPendientesHoy() throws SQLException {
        List<Buzon> lista = new ArrayList<>();
        String sql = "SELECT b.id_mensaje, b.id_apartamento, b.id_visita, b.tipo, b.titulo, b.cuerpo, "
                   + "       b.foto_captura, b.leido, b.leido_en, b.entregado, b.entregado_en, "
                   + "       b.confirmado, b.confirmado_en, b.creado_por, b.fecha_creacion, "
                   + "       a.numero AS numero_apartamento "
                   + "FROM   BUZON b "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = b.id_apartamento "
                   + "WHERE  b.tipo = 'QUEJA_RUIDO' "
                   + "AND    TRUNC(b.fecha_creacion) = TRUNC(SYSDATE) "
                   + "AND    NOT EXISTS (SELECT 1 FROM MULTAS m WHERE m.id_mensaje = b.id_mensaje) "
                   + "ORDER  BY b.fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Buzon b = mapear(rs);
                b.setNumeroApartamento(rs.getString("numero_apartamento"));
                lista.add(b);
            }
        }
        return lista;
    }

    public Integer insert(Buzon b) throws SQLException {
        String sql = "INSERT INTO BUZON "
                   + "(id_apartamento, id_visita, tipo, titulo, cuerpo, foto_captura, "
                   + " creado_por) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, new String[]{"id_mensaje"})) {
            if (b.getIdApartamento() != null) ps.setInt(1, b.getIdApartamento());
            else ps.setNull(1, Types.NUMERIC);
            if (b.getIdVisita() != null) ps.setInt(2, b.getIdVisita());
            else ps.setNull(2, Types.NUMERIC);
            ps.setString(3, b.getTipo());
            ps.setString(4, b.getTitulo());
            if (b.getCuerpo() != null) ps.setString(5, b.getCuerpo());
            else ps.setNull(5, Types.VARCHAR);
            if (b.getFotoCaptura() != null) ps.setString(6, b.getFotoCaptura());
            else ps.setNull(6, Types.CLOB);
            ps.setInt(7, b.getCreadoPor());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    public void marcarTodoLeidoYEntregado(int idApartamento) throws SQLException {
        String sql = "UPDATE BUZON SET leido = 1, leido_en = SYSTIMESTAMP, entregado = 1, entregado_en = SYSTIMESTAMP "
                   + "WHERE id_apartamento = ? AND entregado = 0";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idApartamento);
            ps.executeUpdate();
        }
    }

    public void marcarMultiLeidoYEntregado(List<Integer> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) return;
        String sql = "UPDATE BUZON SET leido = 1, leido_en = SYSTIMESTAMP, entregado = 1, entregado_en = SYSTIMESTAMP "
                   + "WHERE id_mensaje IN (";
        for (int i = 0; i < ids.size(); i++) sql += (i > 0 ? ",?" : "?");
        sql += ")";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            ps.executeUpdate();
        }
    }

    public void marcarLeido(int idMensaje) throws SQLException {
        String sql = "UPDATE BUZON SET leido = 1, leido_en = SYSTIMESTAMP WHERE id_mensaje = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idMensaje);
            ps.executeUpdate();
        }
    }

    public void marcarEntregado(int idMensaje) throws SQLException {
        String sql = "UPDATE BUZON SET entregado = 1, entregado_en = SYSTIMESTAMP WHERE id_mensaje = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idMensaje);
            ps.executeUpdate();
        }
    }

    public int countPaquetesPendientes() throws SQLException {
        String sql = "SELECT COUNT(*) FROM BUZON WHERE tipo = 'PAQUETE' AND entregado = 0";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Obtiene todos los paquetes pendientes con información del apartamento (para portero). */
    public List<Buzon> findAllPaquetesPendientes() throws SQLException {
        List<Buzon> lista = new ArrayList<>();
        String sql = "SELECT b.id_mensaje, b.id_apartamento, b.id_visita, b.tipo, b.titulo, b.cuerpo, "
                   + "       b.foto_captura, b.leido, b.leido_en, b.entregado, b.entregado_en, "
                   + "       b.confirmado, b.confirmado_en, b.creado_por, b.fecha_creacion, "
                   + "       a.numero AS numero_apartamento "
                   + "FROM   BUZON b "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = b.id_apartamento "
                   + "WHERE  b.tipo = 'PAQUETE' AND b.entregado = 0 "
                   + "ORDER  BY b.fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Buzon b = mapear(rs);
                b.setNumeroApartamento(rs.getString("numero_apartamento"));
                lista.add(b);
            }
        }
        return lista;
    }

    /** Obtiene todos los paquetes registrados (para admin) con info del residente. */
    public List<Buzon> findAllPaquetes() throws SQLException {
        List<Buzon> lista = new ArrayList<>();
        String sql = "SELECT b.id_mensaje, b.id_apartamento, b.id_visita, b.tipo, b.titulo, b.cuerpo, "
                   + "       b.foto_captura, b.leido, b.leido_en, b.entregado, b.entregado_en, "
                   + "       b.confirmado, b.confirmado_en, b.creado_por, b.fecha_creacion, "
                   + "       a.numero AS numero_apartamento, "
                   + "       (SELECT r.nombres || ' ' || r.apellidos "
                   + "          FROM RESIDENTES r "
                   + "          JOIN CONTRATO_RESIDENTE cr ON cr.id_residente = r.id_residente "
                   + "          JOIN CONTRATOS c ON c.id_contrato = cr.id_contrato AND c.estado = 'ACTIVO' "
                   + "          WHERE c.id_apartamento = b.id_apartamento AND cr.rol_en_contrato = 'ARRENDATARIO' "
                   + "          AND ROWNUM = 1) AS nombre_residente "
                   + "FROM   BUZON b "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = b.id_apartamento "
                   + "WHERE  b.tipo = 'PAQUETE' "
                   + "ORDER  BY b.fecha_creacion DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Buzon b = mapear(rs);
                b.setNumeroApartamento(rs.getString("numero_apartamento"));
                b.setNombreResidente(rs.getString("nombre_residente"));
                lista.add(b);
            }
        }
        return lista;
    }

    public void confirmarVisita(int idMensaje, int confirmado) throws SQLException {
        String sql = "UPDATE BUZON SET confirmado = ?, confirmado_en = SYSTIMESTAMP, leido = 1, leido_en = SYSTIMESTAMP WHERE id_mensaje = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, confirmado);
            ps.setInt(2, idMensaje);
            ps.executeUpdate();
        }
    }

    private Buzon mapear(ResultSet rs) throws SQLException {
        Buzon b = new Buzon();
        b.setIdMensaje(rs.getInt("id_mensaje"));
        int apt = rs.getInt("id_apartamento");
        b.setIdApartamento(rs.wasNull() ? null : apt);
        int vis = rs.getInt("id_visita");
        b.setIdVisita(rs.wasNull() ? null : vis);
        b.setTipo(rs.getString("tipo"));
        b.setTitulo(rs.getString("titulo"));
        b.setCuerpo(rs.getString("cuerpo"));
        b.setFotoCaptura(rs.getString("foto_captura"));
        b.setLeido(rs.getInt("leido") == 1);
        Timestamp le = rs.getTimestamp("leido_en");
        if (le != null) b.setLeidoEn(le.toLocalDateTime());
        b.setEntregado(rs.getInt("entregado") == 1);
        Timestamp ee = rs.getTimestamp("entregado_en");
        if (ee != null) b.setEntregadoEn(ee.toLocalDateTime());
        int c = rs.getInt("confirmado");
        b.setConfirmado(rs.wasNull() ? null : c);
        Timestamp ce = rs.getTimestamp("confirmado_en");
        if (ce != null) b.setConfirmadoEn(ce.toLocalDateTime());
        b.setCreadoPor(rs.getInt("creado_por"));
        Timestamp fc = rs.getTimestamp("fecha_creacion");
        if (fc != null) b.setFechaCreacion(fc.toLocalDateTime());
        try { b.setEmpresaMensajeria(rs.getString("empresa_mensajeria")); } catch (SQLException e) { /* columna no disponible */ }
        try { b.setNumeroGuia(rs.getString("numero_guia")); } catch (SQLException e) { /* columna no disponible */ }
        return b;
    }

}
