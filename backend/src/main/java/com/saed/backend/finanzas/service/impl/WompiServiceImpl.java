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

    @org.springframework.beans.factory.annotation.Value("${wompi.public-key:${WOMPI_PUBLIC_KEY:}}")
    private String wompiPublicKey;

    @org.springframework.beans.factory.annotation.Value("${wompi.integrity-secret:${WOMPI_INTEGRITY_SECRET:}}")
    private String wompiIntegritySecret;

    @org.springframework.beans.factory.annotation.Value("${wompi.events-secret:${WOMPI_EVENTS_SECRET:}}")
    private String wompiEventsSecret;

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

    public String getPublicKey() {
        if (wompiPublicKey != null && !wompiPublicKey.isBlank()) return wompiPublicKey;
        return System.getenv("WOMPI_PUBLIC_KEY");
    }

    public void setPublicKey(String key) {
        this.wompiPublicKey = key;
    }

    public String getIntegritySecret() {
        if (wompiIntegritySecret != null && !wompiIntegritySecret.isBlank()) return wompiIntegritySecret;
        return System.getenv("WOMPI_INTEGRITY_SECRET");
    }

    public void setIntegritySecret(String secret) {
        this.wompiIntegritySecret = secret;
    }

    public String getEventsSecret() {
        if (wompiEventsSecret != null && !wompiEventsSecret.isBlank()) return wompiEventsSecret;
        return System.getenv("WOMPI_EVENTS_SECRET");
    }

    public void setEventsSecret(String secret) {
        this.wompiEventsSecret = secret;
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE_INTENTION", resource = "WOMPI_PAYMENT", category = AuditCategory.FINANCIAL, severity = AuditSeverity.CRITICAL)
    public Map<String, Object> crearIntencion(String concepto, Long idItem) throws Exception {
        String pubKey = getPublicKey();
        String integritySec = getIntegritySecret();
        if (pubKey == null || pubKey.isBlank() || integritySec == null || integritySec.isBlank()) {
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
        resp.put("publicKey", pubKey);
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
        String s = referencia + montoCentavos + "COP" + getIntegritySecret();
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

        // 1. Verificación matemática de firma con WOMPI_EVENTS_SECRET antes de interactuar con BD
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
        String currency = (String) tx.get("currency");
        Number amountInCents = (Number) tx.get("amount_in_cents");

        if (referencia == null || status == null || idWompi == null || amountInCents == null) return;

        // 2. Validación de moneda
        if (currency != null && !"COP".equalsIgnoreCase(currency)) {
            log.warn("[Wompi] Moneda no coincide: {}", currency);
            return;
        }

        // 3. Establecer contexto de ejecución seguro para consultar TRANSACCIONES_PAGO
        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            SaedContext systemCtx = SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build();
            SaedContextHolder.setContext(systemCtx);
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            } catch (Exception ignored) {}

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
            Long idTransaccion = ((Number) intencion.get("ID_TRANSACCION")).longValue();
            Long idUnidad = ((Number) intencion.get("ID_UNIDAD")).longValue();
            long expectedCentavos = ((Number) intencion.get("MONTO_CENTAVOS")).longValue();

            // 4. Validación exacta de monto en centavos
            if (amountInCents.longValue() != expectedCentavos) {
                log.warn("[Wompi] Monto en centavos no coincide: recibido={}, esperado={}", amountInCents, expectedCentavos);
                return;
            }

            // 5. Determinar nuevo estado y resolver inquilino (propiedad y organización)
            String nuevoEstado = estadoInternoDe(status);
            boolean aprobada = "APROBADO".equals(nuevoEstado);
            String metodoPagoReal = (String) tx.get("payment_method_type");
            if (metodoPagoReal == null) metodoPagoReal = "WOMPI";

            Long idPropiedad = null;
            Long idOrganizacion = null;
            try {
                List<Map<String, Object>> uProps = jdbcTemplate.queryForList(
                    "SELECT u.ID_PROPIEDAD, p.ID_ORGANIZACION FROM UNIDADES u " +
                    "JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD WHERE u.ID_UNIDAD = :u",
                    new MapSqlParameterSource("u", idUnidad)
                );
                if (!uProps.isEmpty()) {
                    idPropiedad = ((Number) uProps.get(0).get("ID_PROPIEDAD")).longValue();
                    idOrganizacion = ((Number) uProps.get(0).get("ID_ORGANIZACION")).longValue();
                }
            } catch (Exception ignored) {}

            if (idPropiedad != null && idOrganizacion != null) {
                SaedContext tenantCtx = SaedContext.builder()
                    .userId(1L)
                    .organizationId(idOrganizacion)
                    .propertyId(idPropiedad)
                    .unitId(idUnidad)
                    .roleCode("SUPERADMIN")
                    .roleScope("GLOBAL")
                    .build();
                SaedContextHolder.setContext(tenantCtx);
                try {
                    jdbcTemplate.getJdbcOperations().execute(String.format(
                        "BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, 'SUPERADMIN'); END;",
                        idOrganizacion, idPropiedad
                    ));
                } catch (Exception ignored) {}
            }

            // 6. Transición Atómica de Estado (Idempotencia y Replay Protection)
            int updated = jdbcTemplate.update(
                "UPDATE TRANSACCIONES_PAGO SET ESTADO_PASARELA = :est, PAYLOAD_WEBHOOK = :pay, ID_TRANSACCION_PASARELA = :wompi, METODO_ORIGEN = :metodo " +
                "WHERE ID_TRANSACCION = :id AND ESTADO_PASARELA = 'PENDIENTE'",
                new MapSqlParameterSource("est", nuevoEstado)
                    .addValue("pay", payloadRaw)
                    .addValue("wompi", idWompi)
                    .addValue("metodo", metodoPagoReal)
                    .addValue("id", idTransaccion)
            );

            if (updated == 0) {
                log.info("[Wompi] Transaccion {} ya no esta PENDIENTE (evento duplicado/idempotente ignorado).", referencia);
                return;
            }

            // 7. Si fue aprobada, asentar el pago en el libro contable
            if (aprobada) {
                BigDecimal montoPesos = new BigDecimal(expectedCentavos).divide(new BigDecimal(100));

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
                            "PASARELA_WOMPI",
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
        } finally {
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
            } catch (Exception e) {
                // [C5][H-07] DO NOT silence — a failed CLEAR_CONTEXT leaves the Oracle session with
                // SUPERADMIN context in the connection pool. Log for production alerting.
                log.error("[SECURITY][C5] CRITICAL: failed to CLEAR Oracle session context after Wompi webhook. "
                        + "Possible SUPERADMIN context bleed in connection pool. Error: {}", e.getMessage());
            }
            if (prevCtx != null) {
                SaedContextHolder.setContext(prevCtx);
            } else {
                SaedContextHolder.clearContext();
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
        String eventsSecret = getEventsSecret();
        if (eventsSecret == null || eventsSecret.isBlank()) return false;

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
        sb.append(eventsSecret);

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