package com.edificio.admin.dao;

import com.edificio.admin.model.Visita;
import com.edificio.admin.model.enums.EstadoVisita;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla VISITAS.
 * La generacion del QR se hace despues de insertar la visita (QRAccesoDAO).
 */
public class VisitaDAO implements CrudDAO<Visita> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<Visita> findAll() throws SQLException {
        List<Visita> lista = new ArrayList<>();
        String sql = "SELECT id_visita, id_contrato_res, id_residente, fecha_registro, "
                   + "       tiempo_validez_min, cantidad_personas, estado, notas, actualizado_en "
                   + "FROM   VISITAS "
                   + "ORDER  BY fecha_registro DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public Visita findById(Integer id) throws SQLException {
        String sql = "SELECT id_visita, id_contrato_res, id_residente, fecha_registro, "
                   + "       tiempo_validez_min, cantidad_personas, estado, notas, actualizado_en "
                   + "FROM   VISITAS WHERE id_visita = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Visitas de un residente especifico. */
    public List<Visita> findByResidente(Integer idResidente) throws SQLException {
        List<Visita> lista = new ArrayList<>();
        String sql = "SELECT id_visita, id_contrato_res, id_residente, fecha_registro, "
                   + "       tiempo_validez_min, cantidad_personas, estado, notas, actualizado_en "
                   + "FROM   VISITAS "
                   + "WHERE  id_residente = ? "
                   + "ORDER  BY fecha_registro DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idResidente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    /** Visitas con estado PENDIENTE (QR valido) o ACTIVA (dentro del edificio). */
    public List<Visita> findPendientesYActivas() throws SQLException {
        expirarVencidas();
        List<Visita> lista = new ArrayList<>();
        String sql = "SELECT v.id_visita, v.id_contrato_res, v.id_residente, v.fecha_registro, "
                   + "       v.tiempo_validez_min, v.cantidad_personas, v.estado, v.notas, v.actualizado_en, "
                   + "       vt.nombres || ' ' || vt.apellidos AS nombre_visitante, "
                   + "       vt.numero_documento AS documento_visitante, "
                   + "       a.numero AS numero_apartamento, a.piso, "
                   + "       ra.hora_entrada AS fecha_visita, "
                   + "       b.foto_captura "
                   + "FROM   VISITAS v "
                   + "LEFT JOIN QR_ACCESOS q        ON q.id_visita      = v.id_visita "
                   + "LEFT JOIN REGISTRO_VISITA rv  ON rv.id_visita     = v.id_visita AND rv.es_titular = 1 "
                   + "LEFT JOIN VISITANTES     vt   ON vt.id_visitante  = rv.id_visitante "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato_res = v.id_contrato_res "
                   + "LEFT JOIN CONTRATOS      c    ON c.id_contrato    = cr.id_contrato "
                   + "LEFT JOIN APARTAMENTOS   a    ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN REGISTROS_ACCESO ra ON ra.id_visita     = v.id_visita "
                   + "LEFT JOIN BUZON          b    ON b.id_visita      = v.id_visita AND b.tipo = 'CONFIRMAR_VISITA' "
                   + "WHERE  v.estado = 'ACTIVA' "
                   + "   OR  (v.estado = 'PENDIENTE' AND q.usado = 0 AND q.fecha_expiracion > SYSTIMESTAMP) "
                   + "ORDER  BY v.fecha_registro DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { 
                Visita v = mapearConPresentacion(rs);
                String foto = rs.getString("foto_captura");
                if (foto != null) v.setFotoCaptura(foto);
                String doc = rs.getString("documento_visitante");
                if (doc != null) v.setDocumentoVisitante(doc);
                Timestamp fv = rs.getTimestamp("fecha_visita");
                if (fv != null) v.setFechaVisita(fv.toLocalDateTime());
                lista.add(v);
            }
        }
        return lista;
    }

    /** Marca como EXPIRADA las visitas PENDIENTE cuyo QR ya vencio. */
    private void expirarVencidas() throws SQLException {
        String sql = "UPDATE VISITAS SET estado = 'EXPIRADA', actualizado_en = SYSTIMESTAMP "
                   + "WHERE  estado = 'PENDIENTE' "
                   + "AND    EXISTS (SELECT 1 FROM QR_ACCESOS q "
                   + "               WHERE q.id_visita = VISITAS.id_visita "
                   + "                 AND q.usado = 0 "
                   + "                 AND q.fecha_expiracion <= SYSTIMESTAMP)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    /** Visitas con un estado especifico (p.ej. PENDIENTE, ACTIVA). */
    public List<Visita> findByEstado(EstadoVisita estado) throws SQLException {
        List<Visita> lista = new ArrayList<>();
        String sql = "SELECT id_visita, id_contrato_res, id_residente, fecha_registro, "
                   + "       tiempo_validez_min, cantidad_personas, estado, notas, actualizado_en "
                   + "FROM   VISITAS "
                   + "WHERE  estado = ? "
                   + "ORDER  BY fecha_registro DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, estado.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    /** 
     * Obtiene todas las visitas FINALIZADAS registradas hoy.
     * Útil para mostrar el historial diario completo al portero.
     */
    public List<Visita> findVisitasHoy() throws SQLException {
        List<Visita> lista = new ArrayList<>();
        String sql = "SELECT v.id_visita, v.id_contrato_res, v.id_residente, v.fecha_registro, "
                   + "       v.tiempo_validez_min, v.cantidad_personas, v.estado, v.notas, v.actualizado_en, "
                   + "       vt.nombres || ' ' || vt.apellidos AS nombre_visitante, "
                   + "       vt.numero_documento AS documento_visitante, "
                   + "       a.numero AS numero_apartamento, a.piso, "
                   + "       ra.hora_entrada AS fecha_visita, "
                   + "       ra.hora_salida AS fecha_salida, "
                   + "       b.foto_captura "
                   + "FROM   VISITAS v "
                   + "LEFT JOIN REGISTRO_VISITA rv  ON rv.id_visita     = v.id_visita AND rv.es_titular = 1 "
                   + "LEFT JOIN VISITANTES     vt   ON vt.id_visitante  = rv.id_visitante "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato_res = v.id_contrato_res "
                   + "LEFT JOIN CONTRATOS      c    ON c.id_contrato    = cr.id_contrato "
                   + "LEFT JOIN APARTAMENTOS   a    ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN REGISTROS_ACCESO ra ON ra.id_visita     = v.id_visita "
                   + "LEFT JOIN BUZON          b    ON b.id_visita      = v.id_visita AND b.tipo = 'CONFIRMAR_VISITA' "
                   + "WHERE  TRUNC(v.fecha_registro) = TRUNC(SYSDATE) "
                   + "AND    v.estado = 'FINALIZADA' "
                   + "ORDER  BY v.fecha_registro DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { 
                Visita v = mapearConPresentacion(rs);
                Timestamp fv = rs.getTimestamp("fecha_visita");
                if (fv != null) v.setFechaVisita(fv.toLocalDateTime());
                Timestamp fs = rs.getTimestamp("fecha_salida");
                if (fs != null) v.setFechaSalida(fs.toLocalDateTime());
                String foto = rs.getString("foto_captura");
                if (foto != null) v.setFotoCaptura(foto);
                String doc = rs.getString("documento_visitante");
                if (doc != null) v.setDocumentoVisitante(doc);
                lista.add(v);
            }
        }
        return lista;
    }

    /**
     * Obtiene visitas en un rango de fechas específico.
     * Incluye información de visitante, apartamento y horas de entrada/salida.
     * 
     * @param fechaInicio formato "YYYY-MM-DD"
     * @param fechaFin formato "YYYY-MM-DD"
     * @return lista de visitas en el rango especificado
     */
    public List<Visita> findByRangoFechas(String fechaInicio, String fechaFin) throws SQLException {
        List<Visita> lista = new ArrayList<>();
        String sql = "SELECT v.id_visita, v.id_contrato_res, v.id_residente, v.fecha_registro, "
                   + "       v.tiempo_validez_min, v.cantidad_personas, v.estado, v.notas, v.actualizado_en, "
                   + "       vt.nombres || ' ' || vt.apellidos AS nombre_visitante, "
                   + "       vt.numero_documento AS documento_visitante, "
                   + "       r.nombres || ' ' || r.apellidos AS nombre_residente, "
                   + "       a.numero AS numero_apartamento, a.piso, "
                   + "       ra.hora_entrada AS fecha_visita, "
                   + "       ra.hora_salida AS fecha_salida, "
                   + "       pq.codigo AS codigo_parqueadero, "
                   + "       vv.tipo AS tipo_vehiculo, "
                   + "       vv.placa AS placa_vehiculo "
                   + "FROM   VISITAS v "
                   + "LEFT JOIN REGISTRO_VISITA rv  ON rv.id_visita     = v.id_visita AND rv.es_titular = 1 "
                   + "LEFT JOIN VISITANTES     vt   ON vt.id_visitante  = rv.id_visitante "
                   + "LEFT JOIN RESIDENTES     r    ON r.id_residente   = v.id_residente "
                   + "LEFT JOIN CONTRATO_RESIDENTE cr ON cr.id_contrato_res = v.id_contrato_res "
                   + "LEFT JOIN CONTRATOS      c    ON c.id_contrato    = cr.id_contrato "
                   + "LEFT JOIN APARTAMENTOS   a    ON a.id_apartamento = c.id_apartamento "
                   + "LEFT JOIN REGISTROS_ACCESO ra ON ra.id_visita     = v.id_visita "
                   + "LEFT JOIN VEHICULOS_VISITA vv ON vv.id_visita     = v.id_visita "
                   + "LEFT JOIN PARQUEADEROS   pq   ON pq.id_parqueadero = vv.id_parqueadero "
                   + "WHERE  TRUNC(v.fecha_registro) BETWEEN TO_DATE(?, 'YYYY-MM-DD') AND TO_DATE(?, 'YYYY-MM-DD') "
                   + "ORDER  BY v.fecha_registro DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, fechaInicio);
            ps.setString(2, fechaFin);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Visita v = mapearConPresentacion(rs);
                    Timestamp fv = rs.getTimestamp("fecha_visita");
                    if (fv != null) v.setFechaVisita(fv.toLocalDateTime());
                    Timestamp fs = rs.getTimestamp("fecha_salida");
                    if (fs != null) v.setFechaSalida(fs.toLocalDateTime());
                    v.setDocumentoVisitante(rs.getString("documento_visitante"));
                    v.setNombreResidente(rs.getString("nombre_residente"));
                    v.setCodigoParqueadero(rs.getString("codigo_parqueadero"));
                    v.setTipoVehiculo(rs.getString("tipo_vehiculo"));
                    v.setPlacaVehiculo(rs.getString("placa_vehiculo"));
                    lista.add(v);
                }
            }
        }
        return lista;
    }

    @Override
    public Integer insert(Visita v) throws SQLException {
        String sql = "BEGIN INSERT INTO VISITAS "
                   + "  (id_contrato_res, id_residente, tiempo_validez_min, "
                   + "   cantidad_personas, estado, notas) "
                   + "VALUES (?, ?, ?, ?, ?, ?) "
                   + "RETURNING id_visita INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, v.getIdContratoRes());
            cs.setInt(2, v.getIdResidente());
            cs.setInt(3, v.getTiempoValidezMin());
            cs.setInt(4, v.getCantidadPersonas());
            cs.setString(5, v.getEstado() != null ? v.getEstado().name()
                                                   : EstadoVisita.PENDIENTE.name());
            cs.setString(6, v.getNotas());
            cs.registerOutParameter(7, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(7);
        }
    }

    @Override
    public void update(Visita v) throws SQLException {
        String sql = "UPDATE VISITAS "
                   + "SET    tiempo_validez_min = ?, cantidad_personas = ?, "
                   + "       estado = ?, notas = ? "
                   + "WHERE  id_visita = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, v.getTiempoValidezMin());
            ps.setInt(2, v.getCantidadPersonas());
            ps.setString(3, v.getEstado().name());
            ps.setString(4, v.getNotas());
            ps.setInt(5, v.getIdVisita());
            ps.executeUpdate();
        }
    }

    /** Soft-delete: estado = CANCELADA. */
    @Override
    public void delete(Integer id) throws SQLException {
        String sql = "UPDATE VISITAS SET estado = 'CANCELADA' WHERE id_visita = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---- mapeo ----

    private Visita mapearConPresentacion(ResultSet rs) throws SQLException {
        Visita v = mapear(rs);
        v.setNombreVisitante(rs.getString("nombre_visitante"));
        v.setNumeroApartamento(rs.getString("numero_apartamento"));
        v.setPiso(rs.getObject("piso") != null ? rs.getInt("piso") : null);
        return v;
    }

    private Visita mapear(ResultSet rs) throws SQLException {
        Visita v = new Visita();
        v.setIdVisita(rs.getInt("id_visita"));
        v.setIdContratoRes(rs.getInt("id_contrato_res"));
        v.setIdResidente(rs.getInt("id_residente"));
        Timestamp fr = rs.getTimestamp("fecha_registro");
        if (fr != null) v.setFechaRegistro(fr.toLocalDateTime());
        v.setTiempoValidezMin(rs.getInt("tiempo_validez_min"));
        v.setCantidadPersonas(rs.getInt("cantidad_personas"));
        v.setEstado(EstadoVisita.valueOf(rs.getString("estado")));
        v.setNotas(rs.getString("notas"));
        Timestamp ac = rs.getTimestamp("actualizado_en");
        if (ac != null) v.setActualizadoEn(ac.toLocalDateTime());
        return v;
    }
}
