package com.edificio.admin.dao;

import com.edificio.admin.model.WompiPago;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla TRANSACCIONES_PAGO (intenciones de pago con Wompi).
 * El PK se genera con DEFAULT de secuencia (SEC_TRANSACCIONES_PAGO.NEXTVAL).
 */
public class WompiPagoDAO extends BaseDAO {

    private static final String COLS =
        "id, referencia, id_apartamento, id_usuario, concepto, id_cuota, id_multa, " +
        "monto_centavos, moneda, estado, id_transaccion_wompi, metodo_pago_wompi, " +
        "payload_webhook, fecha_creacion, fecha_confirmacion";

    public Integer insert(WompiPago w) throws SQLException {
        String sql = "BEGIN INSERT INTO TRANSACCIONES_PAGO " +
            "  (referencia, id_apartamento, id_usuario, concepto, id_cuota, id_multa, " +
            "   monto_centavos, moneda, estado) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "RETURNING id INTO ?; END;";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setString(1, w.getReferencia());
            cs.setInt(2, w.getIdApartamento());
            cs.setInt(3, w.getIdUsuario());
            cs.setString(4, w.getConcepto());
            if (w.getIdCuota() != null) cs.setInt(5, w.getIdCuota());
            else                       cs.setNull(5, Types.NUMERIC);
            if (w.getIdMulta() != null) cs.setInt(6, w.getIdMulta());
            else                        cs.setNull(6, Types.NUMERIC);
            cs.setLong(7, w.getMontoCentavos());
            cs.setString(8, w.getMoneda() != null ? w.getMoneda() : "COP");
            cs.setString(9, w.getEstado() != null ? w.getEstado() : "PENDIENTE");
            cs.registerOutParameter(10, Types.NUMERIC);
            cs.executeUpdate();
            return cs.getInt(10);
        }
    }

    public WompiPago findById(Integer id) throws SQLException {
        String sql = "SELECT " + COLS + " FROM TRANSACCIONES_PAGO WHERE id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public WompiPago findByReferencia(String referencia) throws SQLException {
        String sql = "SELECT " + COLS + " FROM TRANSACCIONES_PAGO WHERE referencia = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, referencia);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Pendientes con transaccion creada en Wompi y mas antiguas de N minutos. */
    public List<WompiPago> findPendientesAntiguos(int minutos) throws SQLException {
        return findBySql(
            "SELECT " + COLS + " FROM TRANSACCIONES_PAGO " +
            "WHERE estado = 'PENDIENTE' AND id_transaccion_wompi IS NOT NULL " +
            "  AND fecha_creacion < SYSTIMESTAMP - NUMTODSINTERVAL(?, 'MINUTE') " +
            "ORDER BY fecha_creacion", minutos);
    }

    /** Pendientes que nunca crearon transaccion en Wompi (widget ni se abrio). */
    public List<WompiPago> findPendientesSinTransaccion(int minutos) throws SQLException {
        return findBySql(
            "SELECT " + COLS + " FROM TRANSACCIONES_PAGO " +
            "WHERE estado = 'PENDIENTE' AND id_transaccion_wompi IS NULL " +
            "  AND fecha_creacion < SYSTIMESTAMP - NUMTODSINTERVAL(?, 'MINUTE') " +
            "ORDER BY fecha_creacion", minutos);
    }

    public List<WompiPago> findByApartamento(Integer idApartamento) throws SQLException {
        return findBySql(
            "SELECT " + COLS + " FROM TRANSACCIONES_PAGO WHERE id_apartamento = ? " +
            "ORDER BY fecha_creacion DESC", idApartamento);
    }

    public List<WompiPago> findAll() throws SQLException {
        return findBySql(
            "SELECT " + COLS + " FROM TRANSACCIONES_PAGO ORDER BY fecha_creacion DESC");
    }

    /**
     * Actualiza estado y datos del webhook/reconciliacion.
     * conConfirmacion=true setea fecha_confirmacion=SYSTIMESTAMP.
     */
    public void marcarEstado(Integer id, String nuevoEstado,
                             String idTransaccionWompi, String metodoPagoWompi,
                             String payload, boolean conConfirmacion) throws SQLException {
        String sql = "UPDATE TRANSACCIONES_PAGO SET " +
            "estado = ?, " +
            "id_transaccion_wompi = COALESCE(?, id_transaccion_wompi), " +
            "metodo_pago_wompi = COALESCE(?, metodo_pago_wompi), " +
            "payload_webhook = ?, " +
            "fecha_confirmacion = CASE WHEN ? = 1 AND estado = 'PENDIENTE' " +
            "    THEN SYSTIMESTAMP ELSE fecha_confirmacion END " +
            "WHERE id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, nuevoEstado);
            if (idTransaccionWompi != null) ps.setString(2, idTransaccionWompi);
            else ps.setNull(2, Types.VARCHAR);
            if (metodoPagoWompi != null) ps.setString(3, metodoPagoWompi);
            else ps.setNull(3, Types.VARCHAR);
            if (payload != null) ps.setString(4, payload);
            else ps.setNull(4, Types.CLOB);
            ps.setInt(5, conConfirmacion ? 1 : 0);
            ps.setInt(6, id);
            ps.executeUpdate();
        }
    }

    private List<WompiPago> findBySql(String sql, Object... params) throws SQLException {
        List<WompiPago> lista = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof Integer) ps.setInt(i + 1, (Integer) params[i]);
                else if (params[i] instanceof Long) ps.setLong(i + 1, (Long) params[i]);
                else ps.setString(i + 1, String.valueOf(params[i]));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(mapear(rs));
            }
        }
        return lista;
    }

    private WompiPago mapear(ResultSet rs) throws SQLException {
        WompiPago w = new WompiPago();
        w.setId(rs.getInt("id"));
        w.setReferencia(rs.getString("referencia"));
        w.setIdApartamento(rs.getInt("id_apartamento"));
        w.setIdUsuario(rs.getInt("id_usuario"));
        w.setConcepto(rs.getString("concepto"));
        int c = rs.getInt("id_cuota");
        w.setIdCuota(rs.wasNull() ? null : c);
        int m = rs.getInt("id_multa");
        w.setIdMulta(rs.wasNull() ? null : m);
        w.setMontoCentavos(rs.getLong("monto_centavos"));
        w.setMoneda(rs.getString("moneda"));
        w.setEstado(rs.getString("estado"));
        w.setIdTransaccionWompi(rs.getString("id_transaccion_wompi"));
        w.setMetodoPagoWompi(rs.getString("metodo_pago_wompi"));
        w.setPayloadWebhook(rs.getString("payload_webhook"));
        Timestamp fc = rs.getTimestamp("fecha_creacion");
        if (fc != null) w.setFechaCreacion(fc.toLocalDateTime());
        Timestamp fco = rs.getTimestamp("fecha_confirmacion");
        if (fco != null) w.setFechaConfirmacion(fco.toLocalDateTime());
        return w;
    }
}
