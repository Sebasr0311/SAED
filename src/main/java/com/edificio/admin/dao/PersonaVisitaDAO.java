package com.edificio.admin.dao;

import com.edificio.admin.model.PersonaVisita;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla REGISTRO_VISITA (union N:M entre VISITAS y VISITANTES).
 * Al insertar un registro, el trigger TRG_AUTO_FRECUENTE actualiza
 * FRECUENTES_RESIDENTE automaticamente.
 */
public class PersonaVisitaDAO implements CrudDAO<PersonaVisita> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<PersonaVisita> findAll() throws SQLException {
        List<PersonaVisita> lista = new ArrayList<>();
        String sql = "SELECT id_registro_visita, id_visita, id_visitante, es_titular, "
                   + "       id_vehiculo_visita "
                   + "FROM   REGISTRO_VISITA "
                   + "ORDER  BY id_registro_visita DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public PersonaVisita findById(Integer id) throws SQLException {
        String sql = "SELECT id_registro_visita, id_visita, id_visitante, es_titular, "
                   + "       id_vehiculo_visita "
                   + "FROM   REGISTRO_VISITA WHERE id_registro_visita = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Todos los registros (visitantes) de una visita. */
    public List<PersonaVisita> findByVisita(Integer idVisita) throws SQLException {
        List<PersonaVisita> lista = new ArrayList<>();
        String sql = "SELECT id_registro_visita, id_visita, id_visitante, es_titular, "
                   + "       id_vehiculo_visita "
                   + "FROM   REGISTRO_VISITA WHERE id_visita = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idVisita);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    @Override
    public Integer insert(PersonaVisita pv) throws SQLException {
        String sql = "BEGIN INSERT INTO REGISTRO_VISITA "
                   + "  (id_visita, id_visitante, es_titular, id_vehiculo_visita) "
                   + "VALUES (?, ?, ?, ?) "
                   + "RETURNING id_registro_visita INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, pv.getIdVisita());
            cs.setInt(2, pv.getIdVisitante());
            cs.setInt(3, pv.isEsTitular() ? 1 : 0);
            if (pv.getIdVehiculoVisita() != null) cs.setInt(4, pv.getIdVehiculoVisita());
            else                                   cs.setNull(4, Types.NUMERIC);
            cs.registerOutParameter(5, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(5);
        }
    }

    /** Los registros de visita no se modifican. */
    @Override
    public void update(PersonaVisita pv) throws SQLException {
        throw new UnsupportedOperationException("Los registros de visita son inmutables.");
    }

    /** Los registros de visita no se eliminan. */
    @Override
    public void delete(Integer id) throws SQLException {
        throw new UnsupportedOperationException("Los registros de visita no se eliminan.");
    }

    // ---- mapeo ----

    private PersonaVisita mapear(ResultSet rs) throws SQLException {
        PersonaVisita pv = new PersonaVisita();
        pv.setIdRegistroVisita(rs.getInt("id_registro_visita"));
        pv.setIdVisita(rs.getInt("id_visita"));
        pv.setIdVisitante(rs.getInt("id_visitante"));
        pv.setEsTitular(rs.getInt("es_titular") == 1);

        int idVeh = rs.getInt("id_vehiculo_visita");
        pv.setIdVehiculoVisita(rs.wasNull() ? null : idVeh);

        return pv;
    }
}
