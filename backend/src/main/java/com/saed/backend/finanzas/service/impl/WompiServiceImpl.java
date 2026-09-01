package com.saed.backend.finanzas.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.finanzas.service.WompiService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WompiServiceImpl — pasarela de pagos Wompi (produccion).
 *
 * Flujo:
 *  1. crearIntencion(concepto, idItem) -> inserta en TRANSACCIONES_PAGO (2.0)
 *     y devuelve { referencia, montoCentavos, publicKey, firmaIntegridad }.
 *  2. El widget del frontend abre con publicKey + firma.
 *  3. Wompi envia webhook transaction.updated -> procesarWebhook valida
 *     checksum (SHA-256 con WOMPI_EVENTS_SECRET), actualiza estado y, si
 *     APPROVED, registra el pago via FinanzasService + correo de recibo.
 *
 * Requiere: WOMPI_PUBLIC_KEY, WOMPI_INTEGRITY_SECRET, WOMPI_EVENTS_SECRET.
 */
@Service
public class WompiServiceImpl implements WompiService {

    private static final Logger log = LoggerFactory.getLogger(WompiServiceImpl.class);

    private static final String WOMPI_PUBLIC_KEY = System.getenv("WOMPI_PUBLIC_KEY");
    private static final String WOMPI_INTEGRITY_SECRET = System.getenv("WOMPI_INTEGRITY_SECRET");
    private static final String WOMPI_EVENTS_SECRET = System.getenv("WOMPI_EVENTS_SECRET");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FinanzasService finanzasService;
    private final ObjectMapper mapper;
    private final EmailService emailService;

    public WompiServiceImpl(NamedParameterJdbcTemplate jdbcTemplate, FinanzasService finanzasService, ObjectMapper mapper, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.finanzasService = finanzasService;
        this.mapper = mapper;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE_INTENTION", resource = "WOMPI_PAYMENT", category = AuditCategory.FINANCIAL, severity = AuditSeverity.CRITICAL)
    public Map<String, Object> crearIntencion(String concepto, Long idItem) throws Exception {
        if (WOMPI_PUBLIC_KEY == null || WOMPI_INTEGRITY_SECRET == null) {
            throw new RuntimeException("Wompi no configurado.");
        }
        if (!"CUOTA".equals(concepto) && !"MULTA".equals(concepto)) {
            throw new RuntimeException("Concepto invalido");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        Long idUnidad = ctx.getUnitId();

        // Monto y validacion segun el esquema 2.0 real (CUOTAS / MULTAS)
        BigDecimal monto;
        Long idCuota = null;
        if ("CUOTA".equals(concepto)) {
            String sqlCuota = (idUnidad != null)
                ? "SELECT ID_CUOTA, ID_UNIDAD, SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = :id AND ID_UNIDAD = :u AND ESTADO = 'PENDIENTE'"
                : "SELECT ID_CUOTA, ID_UNIDAD, SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = :id AND ESTADO = 'PENDIENTE'";
            MapSqlParameterSource params = new MapSqlParameterSource("id", idItem);
            if (idUnidad != null) params.addValue("u", idUnidad);

            List<Map<String, Object>> cuotas = jdbcTemplate.queryForList(sqlCuota, params);
            if (cuotas.isEmpty()) throw new RuntimeException("Cuota no encontrada, ya pagada o sin acceso");
            idCuota = ((Number) cuotas.get(0).get("ID_CUOTA")).longValue();
            idUnidad = ((Number) cuotas.get(0).get("ID_UNIDAD")).longValue();
            monto = (BigDecimal) cuotas.get(0).get("SALDO_PENDIENTE");
        } else {
            String sqlMulta = (idUnidad != null)
                ? "SELECT ID_MULTA, ID_UNIDAD, MONTO FROM MULTAS WHERE ID_MULTA = :id AND ID_UNIDAD = :u AND ESTADO IN ('IMPUESTA','EN_DESCARGOS','RATIFICADA')"
                : "SELECT ID_MULTA, ID_UNIDAD, MONTO FROM MULTAS WHERE ID_MULTA = :id AND ESTADO IN ('IMPUESTA','EN_DESCARGOS','RATIFICADA')";
            MapSqlParameterSource params = new MapSqlParameterSource("id", idItem);
            if (idUnidad != null) params.addValue("u", idUnidad);

            List<Map<String, Object>> multas = jdbcTemplate.queryForList(sqlMulta, params);
            if (multas.isEmpty()) throw new RuntimeException("Multa no encontrada, ya pagada o sin acceso");
            idCuota = ((Number) multas.get(0).get("ID_MULTA")).longValue();
            idUnidad = ((Number) multas.get(0).get("ID_UNIDAD")).longValue();
            monto = (BigDecimal) multas.get(0).get("MONTO");
        }

        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Monto invalido");
        }

        long montoCentavos = monto.multiply(new BigDecimal(100)).longValue();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String referencia = "SAED-" + concepto + "-" + idItem + "-" + ts;

        String firma = firmaIntegridad(referencia, montoCentavos);

        // Esquema 2.0 real de TRANSACCIONES_PAGO
        String sql = "INSERT INTO TRANSACCIONES_PAGO " +
                     "(ID_UNIDAD, ID_PAGO, PASARELA, ID_TRANSACCION_PASARELA, REFERENCIA_INTERNA, MONTO_CENTAVOS, MONEDA, ESTADO_PASARELA, METODO_ORIGEN, FIRMA_CHECKSUM) " +
                     "VALUES (:u, NULL, 'WOMPI', :ref, :ref, :mc, 'COP', 'PENDIENTE', :concepto, :firma)";
        jdbcTemplate.update(sql, new MapSqlParameterSource()
            .addValue("u", idUnidad)
            .addValue("ref", referencia)
            .addValue("mc", montoCentavos)
            .addValue("concepto", concepto)
            .addValue("firma", firma)
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("referencia", referencia);
        resp.put("montoCentavos", montoCentavos);
        resp.put("publicKey", WOMPI_PUBLIC_KEY);
        resp.put("firmaIntegridad", firma);
        return resp;
    }

    /** Estado de una intencion por referencia (para GET /wompi/estado). */
    public Map<String, Object> estadoIntencion(String referencia) {
        List<Map<String, Object>> txs = jdbcTemplate.queryForList(
            "SELECT ESTADO_PASARELA, MONTO_CENTAVOS, REFERENCIA_INTERNA FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = :ref",
            new MapSqlParameterSource("ref", referencia)
        );
        if (txs.isEmpty()) {
            return Map.of("estado", "NO_ENCONTRADA");
        }
        Map<String, Object> tx = txs.get(0);
        return Map.of(
            "estado", tx.get("ESTADO_PASARELA"),
            "montoCentavos", tx.get("MONTO_CENTAVOS"),
            "referencia", tx.get("REFERENCIA_INTERNA")
        );
    }

    private String firmaIntegridad(String referencia, long montoCentavos) throws Exception {
        String s = referencia + montoCentavos + "COP" + WOMPI_INTEGRITY_SECRET;
        return sha256Hex(s);
    }

    @Override
    @Transactional
    @Auditable(action = "PROCESS_WEBHOOK", resource = "WOMPI_WEBHOOK", category = AuditCategory.FINANCIAL, severity = AuditSeverity.CRITICAL)
    public void procesarWebhook(String payloadRaw) throws Exception {
        if (payloadRaw == null || payloadRaw.isBlank()) return;

        Map<String, Object> evento = mapper.readValue(payloadRaw, Map.class);
        String event = (String) evento.get("event");
        if (!"transaction.updated".equals(event)) return;

        if (!verificarChecksum(evento)) {
            log.warn("[Wompi] Checksum invalido");
            return;
        }

        Map<String, Object> data = (Map<String, Object>) evento.get("data");
        if (data == null) return;
        Map<String, Object> tx = (Map<String, Object>) data.get("transaction");
        if (tx == null) return;

        String referencia = (String) tx.get("reference");
        String idWompi = (String) tx.get("id");
        String status = (String) tx.get("status");

        if (referencia == null || status == null) return;

        List<Map<String, Object>> txs = jdbcTemplate.queryForList(
            "SELECT ID_TRANSACCION, ID_UNIDAD, ID_PAGO, PASARELA, ESTADO_PASARELA, METODO_ORIGEN, MONTO_CENTAVOS " +
            "FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA = :ref",
            new MapSqlParameterSource("ref", referencia)
        );

        if (txs.isEmpty()) {
            log.warn("[Wompi] Referencia no encontrada: {}", referencia);
            return;
        }

        Map<String, Object> intencion = txs.get(0);
        String estadoActual = (String) intencion.get("ESTADO_PASARELA");
        if (!"PENDIENTE".equals(estadoActual)) return; // Idempotencia

        // Validacion exacta de monto en centavos
        Number amountInCents = (Number) tx.get("amount_in_cents");
        long expectedCentavos = ((Number) intencion.get("MONTO_CENTAVOS")).longValue();
        if (amountInCents == null || amountInCents.longValue() != expectedCentavos) {
            log.warn("[Wompi] Monto en centavos no coincide: recibido={}, esperado={}", amountInCents, expectedCentavos);
            return;
        }

        String nuevoEstado = estadoInternoDe(status);
        boolean aprobada = "APROBADO".equals(nuevoEstado);
        String metodoPagoReal = (String) tx.get("payment_method_type");
        if (metodoPagoReal == null) metodoPagoReal = "WOMPI";

        jdbcTemplate.update(
            "UPDATE TRANSACCIONES_PAGO SET ESTADO_PASARELA = :est, PAYLOAD_WEBHOOK = :pay, ID_TRANSACCION_PASARELA = :wompi, METODO_ORIGEN = :metodo WHERE ID_TRANSACCION = :id",
            new MapSqlParameterSource("est", nuevoEstado)
                .addValue("pay", payloadRaw)
                .addValue("wompi", idWompi)
                .addValue("metodo", metodoPagoReal)
                .addValue("id", ((Number) intencion.get("ID_TRANSACCION")).longValue())
        );

        if (aprobada) {
            BigDecimal montoPesos = new BigDecimal(expectedCentavos).divide(new BigDecimal(100));
            Long idUnidad = ((Number) intencion.get("ID_UNIDAD")).longValue();

            // Extraer concepto e idItem desde la referencia "SAED-<CONCEPTO>-<ID>-<TIMESTAMP>"
            String[] refParts = referencia.split("-");
            String concepto = refParts.length >= 2 ? refParts[1] : "CUOTA";
            Long idItem = refParts.length >= 3 ? Long.parseLong(refParts[2]) : null;

            try {
                if ("CUOTA".equals(concepto) && idItem != null) {
                    finanzasService.registrarPago(new PagoRequestDTO(
                        idItem,
                        java.time.LocalDate.now(),
                        montoPesos,
                        metodoPagoReal,
                        referencia
                    ));
                } else if ("MULTA".equals(concepto) && idItem != null) {
                    jdbcTemplate.update(
                        "UPDATE MULTAS SET ESTADO = 'PAGADA' WHERE ID_MULTA = :id AND ID_UNIDAD = :u",
                        new MapSqlParameterSource("id", idItem).addValue("u", idUnidad)
                    );
                }
            } catch (Exception e) {
                log.error("[Wompi] Error registrando pago aprobado", e);
            }

            // Recibo por correo
            try {
                List<Map<String, Object>> residentes = jdbcTemplate.queryForList(
                    "SELECT P.EMAIL FROM PERSONAS P " +
                    "JOIN RESIDENTES_UNIDAD RU ON RU.ID_PERSONA = P.ID_PERSONA " +
                    "WHERE RU.ID_UNIDAD = :u AND P.EMAIL IS NOT NULL",
                    new MapSqlParameterSource("u", idUnidad)
                );
                if (!residentes.isEmpty()) {
                    String destinatario = (String) residentes.get(0).get("EMAIL");
                    emailService.enviarReciboPago(destinatario, concepto, montoPesos, referencia, java.time.LocalDate.now().toString());
                }
            } catch (Exception e) {
                log.error("[Wompi] Error enviando recibo de pago", e);
            }
        }
    }

    private String estadoInternoDe(String status) {
        if ("APPROVED".equals(status)) return "APROBADO";
        if ("DECLINED".equals(status)) return "RECHAZADO";
        if ("ERROR".equals(status)) return "ERROR";
        if ("VOIDED".equals(status)) return "ANULADO";
        return "PENDIENTE";
    }

    private boolean verificarChecksum(Map<String, Object> evento) throws Exception {
        if (WOMPI_EVENTS_SECRET == null || WOMPI_EVENTS_SECRET.isBlank()) return false;

        Map<String, Object> signature = (Map<String, Object>) evento.get("signature");
        if (signature == null) return false;

        List<String> properties = (List<String>) signature.get("properties");
        Object checksum = signature.get("checksum");
        Object timestamp = evento.get("timestamp");

        if (properties == null || checksum == null || timestamp == null) return false;

        Map<String, Object> data = (Map<String, Object>) evento.get("data");
        StringBuilder sb = new StringBuilder();
        for (String prop : properties) {
            Object valor = navegar(data, prop);
            sb.append(valor == null ? "" : normalizarNumero(valor));
        }
        sb.append(normalizarNumero(timestamp));
        sb.append(WOMPI_EVENTS_SECRET);

        String calc = sha256Hex(sb.toString());
        return calc.equalsIgnoreCase(String.valueOf(checksum));
    }

    private Object navegar(Map<String, Object> data, String ruta) {
        Object cur = data;
        for (String parte : ruta.split("\\.")) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<?, ?>) cur).get(parte);
        }
        return cur;
    }

    private String normalizarNumero(Object num) {
        if (num instanceof Number) {
            double d = ((Number) num).doubleValue();
            if (d == (long) d) return String.valueOf((long) d);
        }
        return num.toString();
    }

    private String sha256Hex(String base) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(base.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}