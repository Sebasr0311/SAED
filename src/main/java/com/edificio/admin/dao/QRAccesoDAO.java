package com.edificio.admin.dao;

import com.edificio.admin.model.QRAcceso;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO para la tabla QR_ACCESOS.
 * La validacion atomica del QR se delega al procedimiento SP_VALIDAR_QR en Oracle.
 * Este DAO solo gestiona la creacion inicial y lectura del registro.
 */
public class QRAccesoDAO implements CrudDAO<QRAcceso> {

    private Connection conn() {
        return ConexionBD.getInstancia().getConexion();
    }

    @Override
    public List<QRAcceso> findAll() throws SQLException {
        List<QRAcceso> lista = new ArrayList<>();
        String sql = "SELECT id_qr, id_visita, codigo_qr, fecha_expiracion, usado, "
                   + "       fecha_uso, id_vigilante_uso "
                   + "FROM   QR_ACCESOS "
                   + "ORDER  BY id_qr DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { lista.add(mapear(rs)); }
        }
        return lista;
    }

    @Override
    public QRAcceso findById(Integer id) throws SQLException {
        String sql = "SELECT id_qr, id_visita, codigo_qr, fecha_expiracion, usado, "
                   + "       fecha_uso, id_vigilante_uso "
                   + "FROM   QR_ACCESOS WHERE id_qr = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Busca el QR de una visita (relacion 1:1). */
    public QRAcceso findByVisita(Integer idVisita) throws SQLException {
        String sql = "SELECT id_qr, id_visita, codigo_qr, fecha_expiracion, usado, "
                   + "       fecha_uso, id_vigilante_uso "
                   + "FROM   QR_ACCESOS WHERE id_visita = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idVisita);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Busca un QR por su codigo (UUID hex). */
    public QRAcceso findByCodigo(String codigoQr) throws SQLException {
        String sql = "SELECT id_qr, id_visita, codigo_qr, fecha_expiracion, usado, "
                   + "       fecha_uso, id_vigilante_uso "
                   + "FROM   QR_ACCESOS WHERE codigo_qr = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, codigoQr);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Marca un QR como usado (actualiza usado, fecha_uso, id_vigilante_uso). */
    public void marcarUsado(Integer idQr) throws SQLException {
        String sql = "UPDATE QR_ACCESOS SET usado = 1, fecha_uso = CURRENT_TIMESTAMP WHERE id_qr = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idQr);
            ps.executeUpdate();
        }
    }

    /** Busca QR generados o usados en una fecha especifica. */
    public List<QRAcceso> findByFecha(java.time.LocalDate fecha) throws SQLException {
        List<QRAcceso> lista = new ArrayList<>();
        String sql = "SELECT id_qr, id_visita, codigo_qr, fecha_expiracion, usado, "
                   + "       fecha_uso, id_vigilante_uso "
                   + "FROM   QR_ACCESOS "
                   + "WHERE  TRUNC(fecha_expiracion) = ? OR TRUNC(fecha_uso) = ? "
                   + "ORDER  BY id_qr DESC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(fecha));
            ps.setDate(2, java.sql.Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { lista.add(mapear(rs)); }
            }
        }
        return lista;
    }

    /**
     * Retorna QR activos (no usados, no expirados) de un residente,
     * con datos del visitante titular para mostrar en el panel del residente.
     */
    public List<Map<String, Object>> findActivosByResidente(int idResidente) throws SQLException {
        List<Map<String, Object>> lista = new ArrayList<>();
        String sql = "SELECT q.id_qr, q.codigo_qr, q.fecha_expiracion, "
                   + "       v.id_visita, v.fecha_registro, v.cantidad_personas, "
                   + "       vt.nombres, vt.apellidos "
                   + "FROM   QR_ACCESOS q "
                   + "JOIN   VISITAS v ON q.id_visita = v.id_visita "
                   + "JOIN   REGISTRO_VISITA rv ON v.id_visita = rv.id_visita AND rv.es_titular = 1 "
                   + "JOIN   VISITANTES vt ON rv.id_visitante = vt.id_visitante "
                   + "WHERE  v.id_residente = ? AND q.usado = 0 AND q.fecha_expiracion > CURRENT_TIMESTAMP "
                   + "ORDER  BY q.fecha_expiracion ASC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idResidente);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("idQr", rs.getInt("id_qr"));
                    m.put("codigoQr", rs.getString("codigo_qr"));
                    m.put("fechaExpiracion", rs.getTimestamp("fecha_expiracion").toLocalDateTime().toString());
                    m.put("idVisita", rs.getInt("id_visita"));
                    Timestamp fr = rs.getTimestamp("fecha_registro");
                    m.put("fechaRegistro", fr != null ? fr.toLocalDateTime().toString() : null);
                    m.put("cantidadPersonas", rs.getInt("cantidad_personas"));
                    String nom = rs.getString("nombres") != null ? rs.getString("nombres") : "";
                    String ape = rs.getString("apellidos") != null ? rs.getString("apellidos") : "";
                    m.put("nombreVisitante", (nom + " " + ape).trim());
                    lista.add(m);
                }
            }
        }
        return lista;
    }

    /**
     * Retorna todos los datos necesarios para la tarjeta de validacion del vigilante,
     * incluyendo residente, apartamento, visitante titular y vehiculo.
     */
    public Map<String, Object> findValidationData(String codigoQr) throws SQLException {
        String sql = "SELECT q.id_qr, q.codigo_qr, q.fecha_expiracion, "
                   + "       v.id_visita, v.fecha_registro, v.cantidad_personas, v.notas, "
                   + "       r.id_residente, r.nombres AS res_nombres, r.apellidos AS res_apellidos, "
                   + "       a.id_apartamento, a.numero AS apt_numero, "
                   + "       vt.id_visitante, vt.nombres AS vis_nombres, vt.apellidos AS vis_apellidos, "
                   + "       vt.numero_documento AS vis_documento, "
                   + "       vt.foto_url AS vis_foto_url, vt.doc_pdf_url AS vis_doc_pdf_url, vt.foto_doc AS vis_foto_doc, "
                   + "       vh.placa, vh.tipo AS vehiculo_tipo "
                   + "FROM   QR_ACCESOS q "
                   + "JOIN   VISITAS v ON q.id_visita = v.id_visita "
                   + "JOIN   RESIDENTES r ON v.id_residente = r.id_residente "
                   + "JOIN   CONTRATO_RESIDENTE cr ON v.id_contrato_res = cr.id_contrato_res "
                   + "JOIN   CONTRATOS c ON cr.id_contrato = c.id_contrato "
                   + "JOIN   APARTAMENTOS a ON c.id_apartamento = a.id_apartamento "
                   + "JOIN   REGISTRO_VISITA rv ON v.id_visita = rv.id_visita AND rv.es_titular = 1 "
                   + "JOIN   VISITANTES vt ON rv.id_visitante = vt.id_visitante "
                   + "LEFT JOIN VEHICULOS_VISITA vh ON v.id_visita = vh.id_visita "
                   + "WHERE  q.codigo_qr = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, codigoQr);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> m = new HashMap<>();
                m.put("idQr", rs.getInt("id_qr"));
                m.put("codigoQr", rs.getString("codigo_qr"));
                Timestamp fe = rs.getTimestamp("fecha_expiracion");
                m.put("fechaExpiracion", fe != null ? fe.toLocalDateTime().toString() : null);
                m.put("idVisita", rs.getInt("id_visita"));
                Timestamp fr = rs.getTimestamp("fecha_registro");
                m.put("fechaRegistro", fr != null ? fr.toLocalDateTime().toString() : null);
                m.put("cantidadPersonas", rs.getInt("cantidad_personas"));
                m.put("notas", rs.getString("notas"));
                m.put("idResidente", rs.getInt("id_residente"));
                m.put("idApartamento", rs.getInt("id_apartamento"));
                String rn = nullToEmpty(rs.getString("res_nombres"));
                String ra = nullToEmpty(rs.getString("res_apellidos"));
                m.put("nombreResidente", (rn + " " + ra).trim());
                m.put("numeroApartamento", rs.getString("apt_numero"));
                String vn = nullToEmpty(rs.getString("vis_nombres"));
                String va = nullToEmpty(rs.getString("vis_apellidos"));
                m.put("nombreVisitante", (vn + " " + va).trim());
                m.put("idVisitante", rs.getInt("id_visitante"));
                m.put("documentoVisitante", rs.getString("vis_documento"));
                m.put("placaVehiculo", rs.getString("placa"));
                m.put("vehiculoTipo", rs.getString("vehiculo_tipo"));
                m.put("fotoUrl", rs.getString("vis_foto_url"));
                m.put("docPdfUrl", rs.getString("vis_doc_pdf_url"));
                m.put("fotoDoc", rs.getString("vis_foto_doc"));
                return m;
            }
        }
    }

    /**
     * Verifica si un visitante ya tiene un QR activo generado por el mismo residente
     * (no usado, no expirado). Diferentes residentes pueden generar QR para el mismo visitante.
     */
    public boolean tieneQRActivo(int idVisitante, int idResidente) throws SQLException {
        String sql = "SELECT COUNT(*) FROM QR_ACCESOS q "
                   + "JOIN   VISITAS v ON q.id_visita = v.id_visita "
                   + "JOIN   REGISTRO_VISITA rv ON v.id_visita = rv.id_visita "
                   + "WHERE  rv.id_visitante = ? AND v.id_residente = ? "
                   + "AND    q.usado = 0 AND q.fecha_expiracion > CURRENT_TIMESTAMP";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idVisitante);
            ps.setInt(2, idResidente);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    /**
     * Inserta el codigo QR generado por Oracle (LOWER(RAWTOHEX(SYS_GUID()))).
     * fecha_expiracion = CURRENT_TIMESTAMP + intervalo(tiempoValidezMin).
     * El codigo_qr debe generarse en Oracle; desde Java solo se almacena.
     */
    @Override
    public Integer insert(QRAcceso q) throws SQLException {
        String sql = "BEGIN INSERT INTO QR_ACCESOS "
                   + "  (id_visita, codigo_qr, fecha_expiracion, usado) "
                   + "VALUES (?, ?, ?, 0) "
                   + "RETURNING id_qr INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, q.getIdVisita());
            cs.setString(2, q.getCodigoQr());
            cs.setTimestamp(3, Timestamp.valueOf(q.getFechaExpiracion()));
            cs.registerOutParameter(4, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(4);
        }
    }

    /** Actualiza estado de uso (normalmente via SP_VALIDAR_QR en Oracle). */
    @Override
    public void update(QRAcceso q) throws SQLException {
        String sql = "UPDATE QR_ACCESOS "
                   + "SET    usado = ?, fecha_uso = ?, id_vigilante_uso = ? "
                   + "WHERE  id_qr = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, q.isUsado() ? 1 : 0);
            if (q.getFechaUso() != null)
                ps.setTimestamp(2, Timestamp.valueOf(q.getFechaUso()));
            else
                ps.setNull(2, Types.TIMESTAMP);
            if (q.getIdVigilanteUso() != null) ps.setInt(3, q.getIdVigilanteUso());
            else                                ps.setNull(3, Types.NUMERIC);
            ps.setInt(4, q.getIdQr());
            ps.executeUpdate();
        }
    }

    /**
     * Marca un QR como usado de forma atómica (UPDATE ... WHERE usado = 0).
     * Retorna true si se actualizó alguna fila, false si ya estaba usado.
     * Elimina la condición TOCTOU entre check y update.
     */
    public boolean marcarUsado(int idQr, int idVigilante) throws SQLException {
        String sql = "UPDATE QR_ACCESOS "
                   + "SET    usado = 1, fecha_uso = CURRENT_TIMESTAMP, id_vigilante_uso = ? "
                   + "WHERE  id_qr = ? AND usado = 0";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, idVigilante);
            ps.setInt(2, idQr);
            return ps.executeUpdate() > 0;
        }
    }

    /** Los QR no se eliminan. */
    @Override
    public void delete(Integer id) throws SQLException {
        throw new UnsupportedOperationException("Los codigos QR no se eliminan.");
    }

    // ---- mapeo ----

    private QRAcceso mapear(ResultSet rs) throws SQLException {
        QRAcceso q = new QRAcceso();
        q.setIdQr(rs.getInt("id_qr"));
        q.setIdVisita(rs.getInt("id_visita"));
        q.setCodigoQr(rs.getString("codigo_qr"));
        q.setFechaExpiracion(rs.getTimestamp("fecha_expiracion").toLocalDateTime());
        q.setUsado(rs.getInt("usado") == 1);

        Timestamp fu = rs.getTimestamp("fecha_uso");
        if (fu != null) q.setFechaUso(fu.toLocalDateTime());

        int vig = rs.getInt("id_vigilante_uso");
        q.setIdVigilanteUso(rs.wasNull() ? null : vig);

        return q;
    }
}
