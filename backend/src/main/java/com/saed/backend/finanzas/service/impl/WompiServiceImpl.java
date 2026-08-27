package com.saed.backend.finanzas.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.finanzas.service.WompiService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContextHolder;
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

@Service
public class WompiServiceImpl implements WompiService {

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
    public Map<String, Object> crearIntencion(String concepto, Long idItem) throws Exception {
        if (WOMPI_PUBLIC_KEY == null || WOMPI_INTEGRITY_SECRET == null) {
            throw new RuntimeException("Wompi no configurado.");
        }
        if (!"CUOTA".equals(concepto) && !"MULTA".equals(concepto)) {
            throw new RuntimeException("Concepto invalido");
        }
        
        Long idUnidad = SaedContextHolder.getContext().getUnitId();
        if (idUnidad == null) throw new RuntimeException("No tenant/unit context");

        BigDecimal monto = null;
        if ("CUOTA".equals(concepto)) {
            List<Map<String, Object>> cuotas = jdbcTemplate.queryForList(
                "SELECT SALDO_PENDIENTE FROM CUOTAS_ARRIENDO WHERE ID_CUOTA = :id AND ID_UNIDAD = :u",
                new MapSqlParameterSource("id", idItem).addValue("u", idUnidad)
            );
            if (cuotas.isEmpty()) throw new RuntimeException("Cuota no encontrada o sin acceso");
            monto = (BigDecimal) cuotas.get(0).get("SALDO_PENDIENTE");
        } else {
            List<Map<String, Object>> multas = jdbcTemplate.queryForList(
                "SELECT MONTO_MULTA, ESTADO FROM MULTAS WHERE ID_MULTA = :id AND ID_UNIDAD = :u",
                new MapSqlParameterSource("id", idItem).addValue("u", idUnidad)
            );
            if (multas.isEmpty()) throw new RuntimeException("Multa no encontrada o sin acceso");
            String estado = (String) multas.get(0).get("ESTADO");
            if ("PAGADA".equals(estado)) throw new RuntimeException("Multa ya esta pagada");
            monto = (BigDecimal) multas.get(0).get("MONTO_MULTA");
        }
        
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Monto invalido");
        }
        
        long montoCentavos = monto.multiply(new BigDecimal(100)).longValue();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String referencia = "SAED-" + concepto + "-" + idItem + "-" + ts;
        
        String firma = firmaIntegridad(referencia, montoCentavos);
        
        // El insert usa la tabla de SAED 2.0 (TRANSACCIONES_PAGO) tal como esta en V3.9:
        // ID_TRANSACCION, ID_UNIDAD, ID_CUOTA, TIPO_ENTIDAD, ESTADO, REFERENCIA, MONTO_CENTAVOS, MONEDA, PAYLOAD_WEBHOOK, ID_WIDGET
        String sql = "INSERT INTO TRANSACCIONES_PAGO (ID_UNIDAD, ID_CUOTA, TIPO_ENTIDAD, REFERENCIA, MONTO_CENTAVOS, MONEDA, ESTADO) " +
                     "VALUES (:u, :idItem, :concepto, :ref, :mc, 'COP', 'PENDIENTE')";
        jdbcTemplate.update(sql, new MapSqlParameterSource()
            .addValue("u", idUnidad)
            .addValue("idItem", idItem)
            .addValue("concepto", concepto)
            .addValue("ref", referencia)
            .addValue("mc", montoCentavos)
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("referencia", referencia);
        resp.put("montoCentavos", montoCentavos);
        resp.put("publicKey", WOMPI_PUBLIC_KEY);
        resp.put("firmaIntegridad", firma);
        return resp;
    }
    
    private String firmaIntegridad(String referencia, long montoCentavos) throws Exception {
        String s = referencia + montoCentavos + "COP" + WOMPI_INTEGRITY_SECRET;
        return sha256Hex(s);
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
            Long idUnidad = ((Number) intencion.get("ID_UNIDAD")).longValue();
            
            String concepto = (String) intencion.get("TIPO_ENTIDAD");
            if ("CUOTA".equals(concepto)) {
                Long idCuota = ((Number) intencion.get("ID_CUOTA")).longValue();
                finanzasService.registrarPago(new PagoRequestDTO(idCuota, java.time.LocalDate.now(), montoPesos, "WOMPI", referencia));
            } else if ("MULTA".equals(concepto)) {
                Long idMulta = ((Number) intencion.get("ID_CUOTA")).longValue(); // Using ID_CUOTA column to store ID for multas
                jdbcTemplate.update("UPDATE MULTAS SET ESTADO = 'PAGADA' WHERE ID_MULTA = :id AND ID_UNIDAD = :u",
                    new MapSqlParameterSource("id", idMulta).addValue("u", idUnidad));
            }
            
            try {
                List<Map<String, Object>> residentes = jdbcTemplate.queryForList(
                    "SELECT P.EMAIL FROM PERSONAS P " +
                    "JOIN UNIDAD_HABITANTES UH ON UH.ID_PERSONA = P.ID_PERSONA " +
                    "WHERE UH.ID_UNIDAD = :u AND P.EMAIL IS NOT NULL", 
                    Map.of("u", idUnidad)
                );
                if (!residentes.isEmpty()) {
                    String destinatario = (String) residentes.get(0).get("EMAIL");
                    emailService.enviarReciboPago(destinatario, concepto, montoPesos, referencia, java.time.LocalDate.now().toString());
                }
            } catch(Exception e) {
                e.printStackTrace();
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