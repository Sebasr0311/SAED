package com.edificio.admin.dao;

import com.edificio.admin.model.VehiculoVisita;
import com.edificio.admin.model.enums.TipoVehiculo;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla VEHICULOS_VISITA.
 * La hora de salida y liberacion de parqueadero se gestionan via
 * TRG_ACCESO_SALIDA al actualizar REGISTROS_ACCESO.hora_salida.
 */
public class VehiculoVisitaDAO implements CrudDAO<VehiculoVisita> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<VehiculoVisita> findAll() throws SQLException {
        List<VehiculoVisita> lista = new ArrayList<>();
        String sql = "SELECT id_vehiculo_visita, id_visita, placa, tipo, descripcion_tipo, "
                   + "       id_parqueadero, hora_salida, actualizado_en "
                   + "FROM   VEHICULOS_VISITA "
                   + "ORDER  BY id_vehiculo_visita DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public VehiculoVisita findById(Integer id) throws SQLException {
        String sql = "SELECT id_vehiculo_visita, id_visita, placa, tipo, descripcion_tipo, "
                   + "       id_parqueadero, hora_salida, actualizado_en "
                   + "FROM   VEHICULOS_VISITA WHERE id_vehiculo_visita = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Vehiculos de una visita. */
    public List<VehiculoVisita> findByVisita(Integer idVisita) throws SQLException {
        List<VehiculoVisita> lista = new ArrayList<>();
        String sql = "SELECT id_vehiculo_visita, id_visita, placa, tipo, descripcion_tipo, "
                   + "       id_parqueadero, hora_salida, actualizado_en "
                   + "FROM   VEHICULOS_VISITA WHERE id_visita = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idVisita);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    @Override
    public Integer insert(VehiculoVisita v) throws SQLException {
        String sql = "BEGIN INSERT INTO VEHICULOS_VISITA "
                   + "  (id_visita, placa, tipo, descripcion_tipo, id_parqueadero, hora_entrada) "
                   + "VALUES (?, UPPER(TRIM(?)), ?, ?, ?, CURRENT_TIMESTAMP) "
                   + "RETURNING id_vehiculo_visita INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, v.getIdVisita());
            cs.setString(2, v.getPlaca());
            cs.setString(3, v.getTipo().name());
            cs.setString(4, v.getDescripcionTipo());
            if (v.getIdParqueadero() != null) cs.setInt(5, v.getIdParqueadero());
            else                               cs.setNull(5, Types.NUMERIC);
            cs.registerOutParameter(6, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(6);
        }
    }

    /** Actualiza parqueadero asignado (puede hacerse antes de validar QR). */
    @Override
    public void update(VehiculoVisita v) throws SQLException {
        String sql = "UPDATE VEHICULOS_VISITA "
                   + "SET    placa = UPPER(TRIM(?)), tipo = ?, descripcion_tipo = ?, "
                   + "       id_parqueadero = ? "
                   + "WHERE  id_vehiculo_visita = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, v.getPlaca());
            ps.setString(2, v.getTipo().name());
            ps.setString(3, v.getDescripcionTipo());
            if (v.getIdParqueadero() != null) ps.setInt(4, v.getIdParqueadero());
            else                               ps.setNull(4, Types.NUMERIC);
            ps.setInt(5, v.getIdVehiculoVisita());
            ps.executeUpdate();
        }
    }

    /** Los vehiculos de visita no se eliminan. */
    @Override
    public void delete(Integer id) throws SQLException {
        throw new UnsupportedOperationException("Los vehiculos de visita no se eliminan.");
    }

    // ---- mapeo ----

    private VehiculoVisita mapear(ResultSet rs) throws SQLException {
        VehiculoVisita v = new VehiculoVisita();
        v.setIdVehiculoVisita(rs.getInt("id_vehiculo_visita"));
        v.setIdVisita(rs.getInt("id_visita"));
        v.setPlaca(rs.getString("placa"));
        v.setTipo(TipoVehiculo.valueOf(rs.getString("tipo")));
        v.setDescripcionTipo(rs.getString("descripcion_tipo"));

        int idP = rs.getInt("id_parqueadero");
        v.setIdParqueadero(rs.wasNull() ? null : idP);

        Timestamp hs = rs.getTimestamp("hora_salida");
        if (hs != null) v.setHoraSalida(hs.toLocalDateTime());

        Timestamp ac = rs.getTimestamp("actualizado_en");
        if (ac != null) v.setActualizadoEn(ac.toLocalDateTime());
        return v;
    }
}
