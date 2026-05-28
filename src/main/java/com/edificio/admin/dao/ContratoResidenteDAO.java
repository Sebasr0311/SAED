package com.edificio.admin.dao;

import com.edificio.admin.model.ContratoResidente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla CONTRATO_RESIDENTE (union N:M Contratos x Residentes).
 */
public class ContratoResidenteDAO implements CrudDAO<ContratoResidente> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<ContratoResidente> findAll() throws SQLException {
        List<ContratoResidente> lista = new ArrayList<>();
        String sql = "SELECT id_contrato_res, id_contrato, id_residente, rol_en_contrato "
                   + "FROM   CONTRATO_RESIDENTE";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public ContratoResidente findById(Integer id) throws SQLException {
        String sql = "SELECT id_contrato_res, id_contrato, id_residente, rol_en_contrato "
                   + "FROM   CONTRATO_RESIDENTE WHERE id_contrato_res = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Registros de un contrato especifico. */
    public List<ContratoResidente> findByContrato(Integer idContrato) throws SQLException {
        List<ContratoResidente> lista = new ArrayList<>();
        String sql = "SELECT id_contrato_res, id_contrato, id_residente, rol_en_contrato "
                   + "FROM   CONTRATO_RESIDENTE WHERE id_contrato = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idContrato);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    /** Contratos activos de un residente. */
    public List<ContratoResidente> findByResidente(Integer idResidente) throws SQLException {
        List<ContratoResidente> lista = new ArrayList<>();
        String sql = "SELECT cr.id_contrato_res, cr.id_contrato, cr.id_residente, cr.rol_en_contrato "
                   + "FROM   CONTRATO_RESIDENTE cr "
                   + "JOIN   CONTRATOS c ON c.id_contrato = cr.id_contrato "
                   + "WHERE  cr.id_residente = ? AND c.estado = 'ACTIVO'";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idResidente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    @Override
    public Integer insert(ContratoResidente cr) throws SQLException {
        String sql = "BEGIN INSERT INTO CONTRATO_RESIDENTE "
                   + "  (id_contrato, id_residente, rol_en_contrato) "
                   + "VALUES (?, ?, ?) "
                   + "RETURNING id_contrato_res INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, cr.getIdContrato());
            cs.setInt(2, cr.getIdResidente());
            cs.setString(3, cr.getRolEnContrato());
            cs.registerOutParameter(4, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(4);
        }
    }

    /** El rol en contrato puede actualizarse si fue registrado incorrectamente. */
    @Override
    public void update(ContratoResidente cr) throws SQLException {
        String sql = "UPDATE CONTRATO_RESIDENTE SET rol_en_contrato = ? "
                   + "WHERE id_contrato_res = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, cr.getRolEnContrato());
            ps.setInt(2, cr.getIdContratoRes());
            ps.executeUpdate();
        }
    }

    /** Elimina la asociacion (no es soft-delete ya que no tiene campo activo). */
    @Override
    public void delete(Integer id) throws SQLException {
        String sql = "DELETE FROM CONTRATO_RESIDENTE WHERE id_contrato_res = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---- mapeo ----

    private ContratoResidente mapear(ResultSet rs) throws SQLException {
        return new ContratoResidente(
            rs.getInt("id_contrato_res"),
            rs.getInt("id_contrato"),
            rs.getInt("id_residente"),
            rs.getString("rol_en_contrato")
        );
    }
}
