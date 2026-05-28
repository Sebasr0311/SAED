package com.edificio.admin.dao;

import com.edificio.admin.model.Parqueadero;
import com.edificio.admin.model.enums.EstadoParqueadero;
import com.edificio.admin.model.enums.TipoParqueadero;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
     * DAO para la tabla PARQUEADEROS.
     * findAll() incluye JOIN con APARTAMENTOS / CONTRATOS / RESIDENTES
     * para mostrar el apartamento asignado y el residente propietario.
     */
    public class ParqueaderoDAO implements CrudDAO<Parqueadero> {

    /** SQL base con JOIN para obtener apartamento y propietario. */
    private static final String SQL_BASE =
        "SELECT p.id_parqueadero, p.codigo, p.tipo, p.es_visitante, p.estado, "
      + "       p.actualizado_en, p.id_apartamento, "
      + "       a.numero  AS numero_apartamento, "
      + "       r.nombres || ' ' || r.apellidos AS nombre_propietario "
      + "FROM   PARQUEADEROS p "
      + "LEFT JOIN APARTAMENTOS a "
      + "       ON a.id_apartamento = p.id_apartamento "
      + "LEFT JOIN CONTRATOS c "
      + "       ON c.id_apartamento = a.id_apartamento AND c.estado = 'ACTIVO' "
      + "LEFT JOIN CONTRATO_RESIDENTE cr "
      + "       ON cr.id_contrato = c.id_contrato AND cr.rol_en_contrato = 'ARRENDATARIO' "
      + "LEFT JOIN RESIDENTES r "
      + "       ON r.id_residente = cr.id_residente "; 

    private String construirWhere(String estado, String tipo, Boolean esVisitante, List<Object> params) {
        StringBuilder sb = new StringBuilder();
        boolean primera = true;
        if (estado != null && !estado.isEmpty()) {
            sb.append("WHERE p.estado = ?");
            params.add(estado);
            primera = false;
        }
        if (tipo != null && !tipo.isEmpty()) {
            if (!primera) sb.append(" AND ");
            else { sb.append("WHERE "); primera = false; }
            sb.append("p.tipo = ?");
            params.add(tipo);
        }
        if (esVisitante != null) {
            if (!primera) sb.append(" AND ");
            else sb.append("WHERE ");
            sb.append("p.es_visitante = ?");
            params.add(esVisitante ? 1 : 0);
        }
        return sb.toString();
    }

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<Parqueadero> findAll() throws SQLException {
        List<Parqueadero> lista = new ArrayList<>();
        String sql = SQL_BASE
                   + "ORDER  BY p.es_visitante, p.codigo";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public Parqueadero findById(Integer id) throws SQLException {
        String sql = SQL_BASE + "WHERE p.id_parqueadero = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Todos los parqueaderos rotativos para visitantes (es_visitante=1), con su estado actual. */
    public List<Parqueadero> findTodosVisitantes() throws SQLException {
        List<Parqueadero> lista = new ArrayList<>();
        String sql = SQL_BASE
                   + "WHERE p.es_visitante = 1 "
                   + "ORDER BY p.codigo";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    /** Solo parqueaderos disponibles para visitantes (usados al asignar en visita). */
    public List<Parqueadero> findDisponiblesParaVisitantes() throws SQLException {
        List<Parqueadero> lista = new ArrayList<>();
        String sql = SQL_BASE
                   + "WHERE p.es_visitante = 1 AND p.estado = 'DISPONIBLE' "
                   + "ORDER BY p.codigo";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    /** Busca parqueaderos con filtros opcionales. */
    public List<Parqueadero> findByApartamento(Integer idApartamento) throws SQLException {
        List<Parqueadero> lista = new ArrayList<>();
        String sql = SQL_BASE + "WHERE p.id_apartamento = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idApartamento);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    public List<Parqueadero> findConFiltros(String estado, String tipo, Boolean esVisitante) throws SQLException {
        List<Parqueadero> lista = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String where = construirWhere(estado, tipo, esVisitante, params);
        String sql = SQL_BASE + where + " ORDER BY p.es_visitante, p.codigo";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                else                     ps.setString(i + 1, (String) p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    @Override
    public Integer insert(Parqueadero p) throws SQLException {
        String sql = "BEGIN INSERT INTO PARQUEADEROS "
                   + "  (codigo, tipo, es_visitante, estado, id_apartamento) "
                   + "VALUES (?, ?, ?, ?, ?) "
                   + "RETURNING id_parqueadero INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setString(1, p.getCodigo());
            cs.setString(2, p.getTipo().name());
            cs.setInt(3, p.isEsVisitante() ? 1 : 0);
            cs.setString(4, p.getEstado() != null ? p.getEstado().name()
                                                   : EstadoParqueadero.DISPONIBLE.name());
            if (p.getIdApartamento() != null) {
                cs.setInt(5, p.getIdApartamento());
            } else {
                cs.setNull(5, Types.NUMERIC);
            }
            cs.registerOutParameter(6, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(6);
        }
    }

    @Override
    public void update(Parqueadero p) throws SQLException {
        String sql = "UPDATE PARQUEADEROS "
                   + "SET    codigo = ?, tipo = ?, es_visitante = ?, estado = ?, id_apartamento = ? "
                   + "WHERE  id_parqueadero = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, p.getCodigo());
            ps.setString(2, p.getTipo().name());
            ps.setInt(3, p.isEsVisitante() ? 1 : 0);
            ps.setString(4, p.getEstado().name());
            if (p.getIdApartamento() != null) {
                ps.setInt(5, p.getIdApartamento());
            } else {
                ps.setNull(5, Types.NUMERIC);
            }
            ps.setInt(6, p.getIdParqueadero());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(Integer id) throws SQLException {
        String sql = "DELETE FROM PARQUEADEROS WHERE id_parqueadero = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---- mapeo ----

    private Parqueadero mapear(ResultSet rs) throws SQLException {
        Parqueadero p = new Parqueadero();
        p.setIdParqueadero(rs.getInt("id_parqueadero"));
        p.setCodigo(rs.getString("codigo"));
        p.setTipo(TipoParqueadero.valueOf(rs.getString("tipo")));
        p.setEsVisitante(rs.getInt("es_visitante") == 1);
        p.setEstado(EstadoParqueadero.valueOf(rs.getString("estado")));
        Timestamp ac = rs.getTimestamp("actualizado_en");
        if (ac != null) p.setActualizadoEn(ac.toLocalDateTime());
        // Campos de JOIN (pueden ser null para rotativos)
        int idApt = rs.getInt("id_apartamento");
        if (!rs.wasNull()) p.setIdApartamento(idApt);
        p.setNumeroApartamento(rs.getString("numero_apartamento"));
        p.setNombrePropietario(rs.getString("nombre_propietario"));
        return p;
    }
}
