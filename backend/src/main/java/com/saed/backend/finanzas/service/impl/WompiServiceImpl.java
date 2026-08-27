package com.saed.backend.finanzas.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.finanzas.service.WompiService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@Service
public class WompiServiceImpl implements WompiService {

    private static final String WOMPI_EVENTS_SECRET = System.getenv("WOMPI_EVENTS_SECRET");
    
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FinanzasService finanzasService;
    private final ObjectMapper mapper;

    public WompiServiceImpl(NamedParameterJdbcTemplate jdbcTemplate, FinanzasService finanzasService, ObjectMapper mapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.finanzasService = finanzasService;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void procesarWebhook(String payloadRaw) throws Exception {
        if (payloadRaw == null || payloadRaw.isBlank()) return;

        Map<String, Object> evento = mapper.readValue(payloadRaw, Map.class);
        String event = (String) evento.get("event");
        if (!"transaction.updated".equals(event)) return;

        if (!verificarChecksum(evento)) {
            System.err.println("[Wompi] Checksum invalido");
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

        // Buscar transaccion
        List<Map<String, Object>> txs = jdbcTemplate.queryForList(
                "SELECT ID_TRANSACCION, ID_UNIDAD, ID_CUOTA, TIPO_ENTIDAD, ESTADO FROM TRANSACCIONES_PAGO WHERE REFERENCIA = :ref",
                new MapSqlParameterSource("ref", referencia)
        );

        if (txs.isEmpty()) {
            System.err.println("[Wompi] Referencia no encontrada: " + referencia);
            return;
        }

        Map<String, Object> intencion = txs.get(0);
        String estadoActual = (String) intencion.get("ESTADO");

        if (!"PENDIENTE".equals(estadoActual)) return; // Idempotencia

        String nuevoEstado = estadoInternoDe(status);
        boolean aprobada = "APROBADO".equals(nuevoEstado);

        jdbcTemplate.update(
                "UPDATE TRANSACCIONES_PAGO SET ESTADO = :est, PAYLOAD_WEBHOOK = :pay, ID_WIDGET = :wompi WHERE ID_TRANSACCION = :id",
                new MapSqlParameterSource("est", nuevoEstado)
                        .addValue("pay", payloadRaw)
                        .addValue("wompi", idWompi)
                        .addValue("id", ((Number) intencion.get("ID_TRANSACCION")).longValue())
        );

        if (aprobada) {
            Number montoObj = (Number) tx.get("amount_in_cents");
            BigDecimal montoPesos = new BigDecimal(montoObj.longValue()).divide(new BigDecimal(100));

            // Si es cuota
            if (intencion.get("ID_CUOTA") != null) {
                Long idCuota = ((Number) intencion.get("ID_CUOTA")).longValue();
                finanzasService.registrarPago(new PagoRequestDTO(idCuota, java.time.LocalDate.now(), montoPesos, "WOMPI", referencia));
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
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

