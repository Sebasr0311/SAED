package com.edificio.admin.dao;

import com.edificio.admin.model.Visitante;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VisitanteDAO implements CrudDAO<Visitante> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    private static final String SELECT_COLS =
        "SELECT id_visitante, id_tipo_doc, numero_documento, "
      + "       nombres, apellidos, telefono, email, activo ";

    @Override
    public List<Visitante> findAll() throws SQLException {
        List<Visitante> lista = new ArrayList<>();
        String sql = SELECT_COLS
                   + "FROM   VISITANTES "
                   + "WHERE  activo = 1 "
                   + "ORDER  BY apellidos, nombres";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public Visitante findById(Integer id) throws SQLException {
        String sql = SELECT_COLS
                   + "FROM   VISITANTES WHERE id_visitante = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public Visitante findByDocumento(Integer idTipoDoc, String numeroDocumento) throws SQLException {
        if (idTipoDoc == null) return null;
        String sql = SELECT_COLS
                   + "FROM   VISITANTES "
                   + "WHERE  id_tipo_doc = ? AND numero_documento = ? AND activo = 1";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idTipoDoc);
            ps.setString(2, numeroDocumento);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public Visitante findByNumeroDocumento(String numeroDocumento) throws SQLException {
        String sql = SELECT_COLS
                   + "FROM   VISITANTES "
                   + "WHERE  numero_documento = ? AND activo = 1";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, numeroDocumento);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    @Override
    public Integer insert(Visitante v) throws SQLException {
        String sql = "BEGIN INSERT INTO VISITANTES "
                   + "  (id_tipo_doc, numero_documento, nombres, apellidos, telefono, email, activo) "
                   + "VALUES (?, ?, ?, ?, ?, ?, 1) "
                   + "RETURNING id_visitante INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            if (v.getIdTipoDoc() != null) cs.setInt   (1, v.getIdTipoDoc());
            else                           cs.setNull  (1, Types.NUMERIC);
            cs.setString(2, v.getNumeroDocumento());
            cs.setString(3, v.getNombres());
            cs.setString(4, v.getApellidos());
            if (v.getTelefono() != null) cs.setString(5, v.getTelefono());
            else                         cs.setNull  (5, Types.VARCHAR);
            if (v.getEmail() != null)    cs.setString(6, v.getEmail());
            else                         cs.setNull  (6, Types.VARCHAR);
            cs.registerOutParameter(7, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(7);
        }
    }

    @Override
    public void update(Visitante v) throws SQLException {
        String sql = "UPDATE VISITANTES "
                   + "SET    nombres = ?, apellidos = ?, telefono = ?, email = ? "
                   + "WHERE  id_visitante = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, v.getNombres());
            ps.setString(2, v.getApellidos());
            if (v.getTelefono() != null) ps.setString(3, v.getTelefono());
            else                         ps.setNull  (3, Types.VARCHAR);
            if (v.getEmail() != null)    ps.setString(4, v.getEmail());
            else                         ps.setNull  (4, Types.VARCHAR);
            ps.setInt(5, v.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(Integer id) throws SQLException {
        String sql = "UPDATE VISITANTES SET activo = 0 WHERE id_visitante = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private Visitante mapear(ResultSet rs) throws SQLException {
        Visitante v = new Visitante();
        v.setId(rs.getInt("id_visitante"));
        v.setIdTipoDoc(rs.getInt("id_tipo_doc"));
        v.setNumeroDocumento(rs.getString("numero_documento"));
        v.setNombres(rs.getString("nombres"));
        v.setApellidos(rs.getString("apellidos"));
        v.setTelefono(rs.getString("telefono"));
        v.setEmail(rs.getString("email"));
        v.setActivo(rs.getInt("activo") == 1);
        return v;
    }

}
