package com.edificio.admin.dao;

import com.edificio.admin.model.Tutor;
import com.edificio.admin.model.Tutor.Parentesco;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla TUTORES.
 *
 * Un menor de edad puede tener como máximo un tutor registrado en la aplicación
 * (UQ_TUTOR_DOC evita duplicados por documento, y la pantalla de Residentes
 * gestiona un único tutor por menor).
 */
public class TutorDAO implements CrudDAO<Tutor> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<Tutor> findAll() throws SQLException {
        List<Tutor> lista = new ArrayList<>();
        String sql = "SELECT id_tutor, id_residente_menor, id_tipo_doc, numero_documento, "
                   + "       nombres, apellidos, telefono, email, parentesco, doc_pdf_url, "
                   + "       fecha_registro, actualizado_en "
                   + "FROM   TUTORES ORDER BY apellidos, nombres";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(mapear(rs));
        }
        return lista;
    }

    @Override
    public Tutor findById(Integer id) throws SQLException {
        String sql = "SELECT id_tutor, id_residente_menor, id_tipo_doc, numero_documento, "
                   + "       nombres, apellidos, telefono, email, parentesco, doc_pdf_url, "
                   + "       fecha_registro, actualizado_en "
                   + "FROM   TUTORES WHERE id_tutor = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Busca el tutor de un residente menor (relación 1:1 desde la app). */
    public Tutor findByResidenteMenor(Integer idResidenteMenor) throws SQLException {
        String sql = "SELECT id_tutor, id_residente_menor, id_tipo_doc, numero_documento, "
                   + "       nombres, apellidos, telefono, email, parentesco, doc_pdf_url, "
                   + "       fecha_registro, actualizado_en "
                   + "FROM   TUTORES WHERE id_residente_menor = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idResidenteMenor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    @Override
    public Integer insert(Tutor t) throws SQLException {
        String sql = "BEGIN INSERT INTO TUTORES "
                   + "  (id_residente_menor, id_tipo_doc, numero_documento, nombres, apellidos, "
                   + "   telefono, email, parentesco, doc_pdf_url) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                   + "RETURNING id_tutor INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, t.getIdResidenteMenor());
            cs.setInt(2, t.getIdTipoDoc());
            cs.setString(3, t.getNumeroDocumento());
            cs.setString(4, t.getNombres());
            cs.setString(5, t.getApellidos());
            setNullableString(cs, 6, t.getTelefono());
            setNullableString(cs, 7, t.getEmail());
            cs.setString(8, t.getParentesco().name());
            setNullableString(cs, 9, t.getDocPdfUrl());
            cs.registerOutParameter(10, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(10);
        }
    }

    @Override
    public void update(Tutor t) throws SQLException {
        String sql = "UPDATE TUTORES "
                   + "SET id_residente_menor = ?, id_tipo_doc = ?, numero_documento = ?, "
                   + "    nombres = ?, apellidos = ?, telefono = ?, email = ?, "
                   + "    parentesco = ?, doc_pdf_url = ? "
                   + "WHERE id_tutor = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, t.getIdResidenteMenor());
            ps.setInt(2, t.getIdTipoDoc());
            ps.setString(3, t.getNumeroDocumento());
            ps.setString(4, t.getNombres());
            ps.setString(5, t.getApellidos());
            setNullableString(ps, 6, t.getTelefono());
            setNullableString(ps, 7, t.getEmail());
            ps.setString(8, t.getParentesco().name());
            setNullableString(ps, 9, t.getDocPdfUrl());
            ps.setInt(10, t.getIdTutor());
            ps.executeUpdate();
        }
    }

    /** Elimina el tutor (CASCADE en BD elimina la fila si se elimina el menor). */
    @Override
    public void delete(Integer id) throws SQLException {
        String sql = "DELETE FROM TUTORES WHERE id_tutor = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Tutor mapear(ResultSet rs) throws SQLException {
        Tutor t = new Tutor();
        t.setIdTutor(rs.getInt("id_tutor"));
        t.setIdResidenteMenor(rs.getInt("id_residente_menor"));
        t.setIdTipoDoc(rs.getInt("id_tipo_doc"));
        t.setNumeroDocumento(rs.getString("numero_documento"));
        t.setNombres(rs.getString("nombres"));
        t.setApellidos(rs.getString("apellidos"));
        t.setTelefono(rs.getString("telefono"));
        t.setEmail(rs.getString("email"));
        try { t.setParentesco(Parentesco.valueOf(rs.getString("parentesco"))); }
        catch (IllegalArgumentException ignored) {}
        t.setDocPdfUrl(rs.getString("doc_pdf_url"));

        Timestamp fr = rs.getTimestamp("fecha_registro");
        if (fr != null) t.setFechaRegistro(fr.toLocalDateTime());
        Timestamp au = rs.getTimestamp("actualizado_en");
        if (au != null) t.setActualizadoEn(au.toLocalDateTime());

        return t;
    }

    private void setNullableString(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) ps.setNull(idx, Types.VARCHAR);
        else                              ps.setString(idx, val);
    }

    private void setNullableString(CallableStatement cs, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) cs.setNull(idx, Types.VARCHAR);
        else                              cs.setString(idx, val);
    }
}
