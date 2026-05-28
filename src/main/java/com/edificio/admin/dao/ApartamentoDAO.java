package com.edificio.admin.dao;

import com.edificio.admin.model.Apartamento;
import com.edificio.admin.model.enums.EstadoApartamento;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla APARTAMENTOS.
 * El estado se sincroniza automaticamente por TRG_CONT_SYNC_APARTAMENTO;
 * desde Java solo se actualiza cuando el admin cambia datos fisicos.
 */
public class ApartamentoDAO implements CrudDAO<Apartamento> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<Apartamento> findAll() throws SQLException {
        List<Apartamento> lista = new ArrayList<>();
        String sql = "SELECT id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, administracion, estado, "
                   + "       activo, fecha_registro, actualizado_en "
                   + "FROM   APARTAMENTOS "
                   + "WHERE  activo = 1 "
                   + "ORDER  BY piso, numero";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public Apartamento findById(Integer id) throws SQLException {
        String sql = "SELECT id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, administracion, estado, "
                   + "       activo, fecha_registro, actualizado_en "
                   + "FROM   APARTAMENTOS WHERE id_apartamento = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Filtra apartamentos por estado (DISPONIBLE / OCUPADO / MANTENIMIENTO). */
    public List<Apartamento> findByEstado(EstadoApartamento estado) throws SQLException {
        List<Apartamento> lista = new ArrayList<>();
        String sql = "SELECT id_apartamento, numero, piso, tipo, area_m2, capacidad_maxima, administracion, estado, "
                   + "       activo, fecha_registro, actualizado_en "
                   + "FROM   APARTAMENTOS "
                   + "WHERE  estado = ? AND activo = 1 "
                   + "ORDER  BY piso, numero";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, estado.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    @Override
    public Integer insert(Apartamento a) throws SQLException {
        String sql = "BEGIN INSERT INTO APARTAMENTOS "
                   + "  (numero, piso, tipo, area_m2, capacidad_maxima, estado) "
                   + "VALUES (?, ?, ?, ?, ?, ?) "
                   + "RETURNING id_apartamento INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setString(1, a.getNumero());
            cs.setInt(2, a.getPiso());
            cs.setString(3, a.getTipo());
            cs.setBigDecimal(4, a.getAreaM2());
            cs.setInt(5, a.getCapacidadMaxima() != null ? a.getCapacidadMaxima() : 2);
            cs.setString(6, a.getEstado() != null ? a.getEstado().name() : EstadoApartamento.DISPONIBLE.name());
            cs.registerOutParameter(7, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(7);
        }
    }

    @Override
    public void update(Apartamento a) throws SQLException {
        String sql = "UPDATE APARTAMENTOS "
                   + "SET    numero = ?, piso = ?, tipo = ?, area_m2 = ?, capacidad_maxima = ?, estado = ? "
                   + "WHERE  id_apartamento = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, a.getNumero());
            ps.setInt(2, a.getPiso());
            ps.setString(3, a.getTipo());
            ps.setBigDecimal(4, a.getAreaM2());
            ps.setInt(5, a.getCapacidadMaxima() != null ? a.getCapacidadMaxima() : 2);
            ps.setString(6, a.getEstado().name());
            ps.setInt(7, a.getIdApartamento());
            ps.executeUpdate();
        }
    }

    /** Soft-delete: activo = 0. */
    @Override
    public void delete(Integer id) throws SQLException {
        String sql = "UPDATE APARTAMENTOS SET activo = 0 WHERE id_apartamento = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---- mapeo ----

    private Apartamento mapear(ResultSet rs) throws SQLException {
        Apartamento a = new Apartamento();
        a.setIdApartamento(rs.getInt("id_apartamento"));
        a.setNumero(rs.getString("numero"));
        a.setPiso(rs.getInt("piso"));
        a.setTipo(rs.getString("tipo"));

        BigDecimal area = rs.getBigDecimal("area_m2");
        a.setAreaM2(area);
        
        a.setCapacidadMaxima(rs.getInt("capacidad_maxima"));

        try { a.setAdministracion(rs.getBigDecimal("administracion")); } catch (SQLException e) { }

        a.setEstado(EstadoApartamento.valueOf(rs.getString("estado")));
        a.setActivo(rs.getInt("activo") == 1);

        Timestamp cr = rs.getTimestamp("fecha_registro");
        if (cr != null) a.setCreadoEn(cr.toLocalDateTime());
        Timestamp ac = rs.getTimestamp("actualizado_en");
        if (ac != null) a.setActualizadoEn(ac.toLocalDateTime());

        return a;
    }
}
