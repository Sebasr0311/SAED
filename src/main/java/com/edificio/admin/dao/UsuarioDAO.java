package com.edificio.admin.dao;

import com.edificio.admin.model.Usuario;
import com.edificio.admin.model.enums.TipoRol;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla USUARIOS.
 * La autenticacion compara solo username y activo=1; la verificacion
 * del password_hash (BCrypt) se delega a UsuarioService.
 */
public class UsuarioDAO implements CrudDAO<Usuario> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<Usuario> findAll() throws SQLException {
        List<Usuario> lista = new ArrayList<>();
        String sql = "SELECT u.id_usuario, u.id_residente, u.username, u.password_hash, u.rol, "
                   + "       u.activo, u.ultimo_login, u.actualizado_en, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       (SELECT a2.id_apartamento FROM CONTRATO_RESIDENTE cr2 "
                   + "         JOIN CONTRATOS c2 ON c2.id_contrato = cr2.id_contrato AND c2.estado = 'ACTIVO' "
                   + "         JOIN APARTAMENTOS a2 ON a2.id_apartamento = c2.id_apartamento "
                   + "        WHERE cr2.id_residente = u.id_residente AND ROWNUM = 1) AS id_apartamento, "
                   + "       (SELECT a2.numero FROM CONTRATO_RESIDENTE cr2 "
                   + "         JOIN CONTRATOS c2 ON c2.id_contrato = cr2.id_contrato AND c2.estado = 'ACTIVO' "
                   + "         JOIN APARTAMENTOS a2 ON a2.id_apartamento = c2.id_apartamento "
                   + "        WHERE cr2.id_residente = u.id_residente AND ROWNUM = 1) AS numero_apartamento "
                   + "FROM   USUARIOS u "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = u.id_residente "
                   + "WHERE  u.activo = 1 "
                   + "ORDER  BY u.username";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public Usuario findById(Integer id) throws SQLException {
        String sql = "SELECT u.id_usuario, u.id_residente, u.username, u.password_hash, u.rol, "
                   + "       u.activo, u.ultimo_login, u.actualizado_en, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       (SELECT a2.id_apartamento FROM CONTRATO_RESIDENTE cr2 "
                   + "         JOIN CONTRATOS c2 ON c2.id_contrato = cr2.id_contrato AND c2.estado = 'ACTIVO' "
                   + "         JOIN APARTAMENTOS a2 ON a2.id_apartamento = c2.id_apartamento "
                   + "        WHERE cr2.id_residente = u.id_residente AND ROWNUM = 1) AS id_apartamento, "
                   + "       (SELECT a2.numero FROM CONTRATO_RESIDENTE cr2 "
                   + "         JOIN CONTRATOS c2 ON c2.id_contrato = cr2.id_contrato AND c2.estado = 'ACTIVO' "
                   + "         JOIN APARTAMENTOS a2 ON a2.id_apartamento = c2.id_apartamento "
                   + "        WHERE cr2.id_residente = u.id_residente AND ROWNUM = 1) AS numero_apartamento "
                   + "FROM   USUARIOS u "
                   + "LEFT JOIN RESIDENTES r ON r.id_residente = u.id_residente "
                   + "WHERE  u.id_usuario = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /**
     * Busca el usuario por username para el flujo de login.
     * Devuelve el registro aunque este inactivo (el servicio verifica activo).
     */
    public Usuario findByUsername(String username) throws SQLException {
        String sql = "SELECT id_usuario, id_residente, username, password_hash, rol, "
                   + "       activo, ultimo_login, actualizado_en "
                   + "FROM   USUARIOS WHERE username = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Actualiza el timestamp de ultimo_login al momento actual. */
    public void registrarLogin(Integer idUsuario) throws SQLException {
        String sql = "UPDATE USUARIOS SET ultimo_login = CURRENT_TIMESTAMP WHERE id_usuario = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idUsuario);
            ps.executeUpdate();
        }
    }

    @Override
    public Integer insert(Usuario u) throws SQLException {
        String sql = "BEGIN INSERT INTO USUARIOS "
                   + "  (id_residente, username, password_hash, rol, activo) "
                   + "VALUES (?, ?, ?, ?, ?) "
                   + "RETURNING id_usuario INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            if (u.getIdResidente() != null) cs.setInt(1, u.getIdResidente());
            else                             cs.setNull(1, Types.NUMERIC);
            cs.setString(2, u.getUsername());
            cs.setString(3, u.getPasswordHash());
            cs.setString(4, u.getRol().name());
            cs.setInt(5, u.isActivo() ? 1 : 0);
            cs.registerOutParameter(6, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(6);
        }
    }

    @Override
    public void update(Usuario u) throws SQLException {
        String sql = "UPDATE USUARIOS "
                   + "SET    id_residente = ?, username = ?, password_hash = ?, "
                   + "       rol = ?, activo = ? "
                   + "WHERE  id_usuario = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            if (u.getIdResidente() != null) ps.setInt(1, u.getIdResidente());
            else                             ps.setNull(1, Types.NUMERIC);
            ps.setString(2, u.getUsername());
            ps.setString(3, u.getPasswordHash());
            ps.setString(4, u.getRol().name());
            ps.setInt(5, u.isActivo() ? 1 : 0);
            ps.setInt(6, u.getIdUsuario());
            ps.executeUpdate();
        }
    }

    /** Soft-delete: activo = 0. */
    @Override
    public void delete(Integer id) throws SQLException {
        String sql = "UPDATE USUARIOS SET activo = 0 WHERE id_usuario = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---- mapeo ----

    private Usuario mapear(ResultSet rs) throws SQLException {
        Usuario u = new Usuario();
        u.setIdUsuario(rs.getInt("id_usuario"));

        int idRes = rs.getInt("id_residente");
        u.setIdResidente(rs.wasNull() ? null : idRes);

        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRol(TipoRol.valueOf(rs.getString("rol")));
        u.setActivo(rs.getInt("activo") == 1);

        Timestamp ul = rs.getTimestamp("ultimo_login");
        if (ul != null) u.setUltimoLogin(ul.toLocalDateTime());

        Timestamp act = rs.getTimestamp("actualizado_en");
        if (act != null) u.setActualizadoEn(act.toLocalDateTime());

        try { u.setNombreResidente(rs.getString("nombre_residente")); } catch (SQLException e) { /* columna no disponible */ }
        try { u.setIdApartamento(rs.getInt("id_apartamento")); if (rs.wasNull()) u.setIdApartamento(null); } catch (SQLException e) { /* columna no disponible */ }
        try { u.setNumeroApartamento(rs.getString("numero_apartamento")); } catch (SQLException e) { /* columna no disponible */ }

        return u;
    }
}
