package com.edificio.admin.dao;

import com.edificio.admin.model.Residente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla RESIDENTES.
 * La vinculacion con apartamentos se hace via CONTRATO_RESIDENTE -> CONTRATOS,
 * no hay FK directa en esta tabla.
 */
public class ResidenteDAO implements CrudDAO<Residente> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<Residente> findAll() throws SQLException {
        List<Residente> lista = new ArrayList<>();
        String sql = "SELECT r.id_residente, r.id_tipo_doc, r.numero_documento, r.nombres, r.apellidos, "
                   + "       r.email, r.telefono, r.fecha_nacimiento, r.es_menor_edad, r.activo, "
                   + "       r.fecha_registro, r.actualizado_en, "
                    + "       (SELECT a2.id_apartamento FROM CONTRATO_RESIDENTE cr2 "
                    + "         JOIN CONTRATOS c2 ON c2.id_contrato = cr2.id_contrato "
                    + "         JOIN APARTAMENTOS a2 ON a2.id_apartamento = c2.id_apartamento "
                    + "        WHERE cr2.id_residente = r.id_residente AND ROWNUM = 1) AS id_apartamento, "
                    + "       (SELECT a2.numero FROM CONTRATO_RESIDENTE cr2 "
                    + "         JOIN CONTRATOS c2 ON c2.id_contrato = cr2.id_contrato "
                    + "         JOIN APARTAMENTOS a2 ON a2.id_apartamento = c2.id_apartamento "
                    + "        WHERE cr2.id_residente = r.id_residente AND ROWNUM = 1) AS numero_apartamento "
                    + "FROM   RESIDENTES r "
                    + "WHERE  r.activo = 1 "
                    + "ORDER  BY r.apellidos, r.nombres";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public Residente findById(Integer id) throws SQLException {
        String sql = "SELECT r.id_residente, r.id_tipo_doc, r.numero_documento, r.nombres, r.apellidos, "
                   + "       r.email, r.telefono, r.fecha_nacimiento, r.es_menor_edad, r.activo, "
                   + "       r.fecha_registro, r.actualizado_en, "
                    + "       (SELECT a2.id_apartamento FROM CONTRATO_RESIDENTE cr2 "
                    + "         JOIN CONTRATOS c2 ON c2.id_contrato = cr2.id_contrato "
                    + "         JOIN APARTAMENTOS a2 ON a2.id_apartamento = c2.id_apartamento "
                    + "        WHERE cr2.id_residente = r.id_residente AND ROWNUM = 1) AS id_apartamento, "
                    + "       (SELECT a2.numero FROM CONTRATO_RESIDENTE cr2 "
                    + "         JOIN CONTRATOS c2 ON c2.id_contrato = cr2.id_contrato "
                    + "         JOIN APARTAMENTOS a2 ON a2.id_apartamento = c2.id_apartamento "
                    + "        WHERE cr2.id_residente = r.id_residente AND ROWNUM = 1) AS numero_apartamento "
                    + "FROM   RESIDENTES r "
                    + "WHERE  r.id_residente = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Busca todos los residentes asociados a un contrato (via CONTRATO_RESIDENTE). */
    public List<Residente> findByContrato(Integer idContrato) throws SQLException {
        List<Residente> lista = new ArrayList<>();
        String sql = "SELECT r.id_residente, r.id_tipo_doc, r.numero_documento, r.nombres, "
                   + "       r.apellidos, r.email, r.telefono, r.fecha_nacimiento, "
                   + "       r.es_menor_edad, r.activo, r.fecha_registro, r.actualizado_en, "
                   + "       a.id_apartamento, a.numero AS numero_apartamento "
                   + "FROM   RESIDENTES r "
                   + "JOIN   CONTRATO_RESIDENTE cr ON cr.id_residente = r.id_residente "
                   + "JOIN   CONTRATOS c ON c.id_contrato = cr.id_contrato "
                   + "JOIN   APARTAMENTOS a ON a.id_apartamento = c.id_apartamento "
                   + "WHERE  cr.id_contrato = ? AND r.activo = 1";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idContrato);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    @Override
    public Integer insert(Residente r) throws SQLException {
        String sql = "BEGIN INSERT INTO RESIDENTES "
                   + "  (id_tipo_doc, numero_documento, nombres, apellidos, email, "
                   + "   telefono, fecha_nacimiento, es_menor_edad, activo) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1) "
                   + "RETURNING id_residente INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, r.getIdTipoDoc());
            cs.setString(2, r.getNumeroDocumento());
            cs.setString(3, r.getNombres());
            cs.setString(4, r.getApellidos());
            cs.setString(5, r.getEmail());
            cs.setString(6, r.getTelefono());
            if (r.getFechaNacimiento() != null)
                cs.setDate(7, Date.valueOf(r.getFechaNacimiento()));
            else
                cs.setNull(7, Types.DATE);
            cs.setInt(8, r.isEsMenorEdad() ? 1 : 0);
            cs.registerOutParameter(9, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(9);
        }
    }

    @Override
    public void update(Residente r) throws SQLException {
        String sql = "UPDATE RESIDENTES "
                   + "SET    id_tipo_doc = ?, numero_documento = ?, nombres = ?, "
                   + "       apellidos = ?, email = ?, telefono = ?, "
                   + "       fecha_nacimiento = ?, es_menor_edad = ? "
                   + "WHERE  id_residente = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, r.getIdTipoDoc());
            ps.setString(2, r.getNumeroDocumento());
            ps.setString(3, r.getNombres());
            ps.setString(4, r.getApellidos());
            ps.setString(5, r.getEmail());
            ps.setString(6, r.getTelefono());
            if (r.getFechaNacimiento() != null)
                ps.setDate(7, Date.valueOf(r.getFechaNacimiento()));
            else
                ps.setNull(7, Types.DATE);
            ps.setInt(8, r.isEsMenorEdad() ? 1 : 0);
            ps.setInt(9, r.getId());
            ps.executeUpdate();
        }
    }

    /** Soft-delete: activo = 0. */
    @Override
    public void delete(Integer id) throws SQLException {
        String sql = "UPDATE RESIDENTES SET activo = 0 WHERE id_residente = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---- mapeo ----

    private Residente mapear(ResultSet rs) throws SQLException {
        Residente r = new Residente();
        r.setId(rs.getInt("id_residente"));
        r.setIdTipoDoc(rs.getInt("id_tipo_doc"));
        r.setNumeroDocumento(rs.getString("numero_documento"));
        r.setNombres(rs.getString("nombres"));
        r.setApellidos(rs.getString("apellidos"));
        r.setEmail(rs.getString("email"));
        r.setTelefono(rs.getString("telefono"));

        Date fn = rs.getDate("fecha_nacimiento");
        if (fn != null) r.setFechaNacimiento(fn.toLocalDate());

        r.setEsMenorEdad(rs.getInt("es_menor_edad") == 1);
        r.setActivo(rs.getInt("activo") == 1);

        Timestamp cr = rs.getTimestamp("fecha_registro");
        if (cr != null) r.setCreadoEn(cr.toLocalDateTime());

        Timestamp ac = rs.getTimestamp("actualizado_en");
        if (ac != null) r.setActualizadoEn(ac.toLocalDateTime());

        try { r.setIdApartamento(rs.getInt("id_apartamento")); if (rs.wasNull()) r.setIdApartamento(null); } catch (SQLException e) { /* columna no disponible */ }
        try { r.setNumeroApartamento(rs.getString("numero_apartamento")); } catch (SQLException e) { /* columna no disponible */ }

        return r;
    }
}
