package com.saed.backend.finanzas.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.finanzas.dto.PagoRequestDTO;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.finanzas.service.WompiService;
import com.saed.backend.identity.service.TokenActivacionService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
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

    @org.springframework.beans.factory.annotation.Value("${wompi.public-key:${WOMPI_PUBLIC_KEY:pub_test_IZg6dmwtip4WYXjPP8G7zYvWzCU5wRaH}}")
    private String wompiPublicKey;

    @org.springframework.beans.factory.annotation.Value("${wompi.integrity-secret:${WOMPI_INTEGRITY_SECRET:test_integrity_CT3taBwOqVtSQITYMGUTcrJftHdoLoPQ}}")
    private String wompiIntegritySecret;

    @org.springframework.beans.factory.annotation.Value("${wompi.events-secret:${WOMPI_EVENTS_SECRET:test_events_VgrGtdTPd9q6XKtBz3iRTgTHJSUuwBBy}}")
    private String wompiEventsSecret;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FinanzasService finanzasService;
    private final ObjectMapper mapper;
    private final EmailService emailService;
    private final TokenActivacionService tokenActivacionService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.saed.backend.platform.service.OnboardingService onboardingService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.saed.backend.audit.AuditService auditService;

    public void setAuditService(com.saed.backend.audit.AuditService auditService) {
        this.auditService = auditService;
    }

    private final com.saed.backend.platform.service.MembershipHistoryService membershipHistoryService;

    public WompiServiceImpl(NamedParameterJdbcTemplate jdbcTemplate,
                            FinanzasService finanzasService,
                            ObjectMapper mapper,
                            EmailService emailService,
                            @org.springframework.context.annotation.Lazy TokenActivacionService tokenActivacionService,
                            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                            @org.springframework.context.annotation.Lazy com.saed.backend.platform.service.OnboardingService onboardingService,
                            @org.springframework.context.annotation.Lazy com.saed.backend.platform.service.MembershipHistoryService membershipHistoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.finanzasService = finanzasService;
        this.mapper = mapper;
        this.emailService = emailService;
        this.tokenActivacionService = tokenActivacionService;
        this.passwordEncoder = passwordEncoder;
        this.onboardingService = onboardingService;
        this.membershipHistoryService = membershipHistoryService;
    }

    public String getPublicKey() {
        if (wompiPublicKey != null && !wompiPublicKey.isBlank()) return wompiPublicKey;
        String env = System.getenv("WOMPI_PUBLIC_KEY");
        if (env != null && !env.isBlank()) return env;
        return "pub_test_IZg6dmwtip4WYXjPP8G7zYvWzCU5wRaH";
    }

    public void setPublicKey(String key) {
        this.wompiPublicKey = key;
    }

    public String getIntegritySecret() {
        if (wompiIntegritySecret != null && !wompiIntegritySecret.isBlank()) return wompiIntegritySecret;
        String env = System.getenv("WOMPI_INTEGRITY_SECRET");
        if (env != null && !env.isBlank()) return env;
        return "test_integrity_CT3taBwOqVtSQITYMGUTcrJftHdoLoPQ";
    }

    public void setIntegritySecret(String secret) {
        this.wompiIntegritySecret = secret;
    }

    public String getEventsSecret() {
        if (wompiEventsSecret != null && !wompiEventsSecret.isBlank()) return wompiEventsSecret;
        String env = System.getenv("WOMPI_EVENTS_SECRET");
        if (env != null && !env.isBlank()) return env;
        return "test_events_VgrGtdTPd9q6XKtBz3iRTgTHJSUuwBBy";
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
                ? "SELECT ID_CUOTA, ID_UNIDAD, SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = :id AND ID_UNIDAD = :u AND ESTADO IN ('PENDIENTE', 'VENCIDA')"
                : "SELECT ID_CUOTA, ID_UNIDAD, SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = :id AND ESTADO IN ('PENDIENTE', 'VENCIDA')";
            MapSqlParameterSource params = new MapSqlParameterSource("id", idItem);
            if (idUnidad != null) params.addValue("u", idUnidad);

            List<Map<String, Object>> cuotas = jdbcTemplate.queryForList(sqlCuota, params);
            if (cuotas.isEmpty() && idUnidad != null) {
                // Fallback sin restriccion estricta de unidad en caso de inconsistencia de contexto
                cuotas = jdbcTemplate.queryForList(
                    "SELECT ID_CUOTA, ID_UNIDAD, SALDO_PENDIENTE FROM CUOTAS WHERE ID_CUOTA = :id AND ESTADO IN ('PENDIENTE', 'VENCIDA')",
                    new MapSqlParameterSource("id", idItem)
                );
            }
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

        if (idUnidad == null) {
            throw new IllegalStateException("Pago residencial requiere una unidad válida asociada");
        }

        // Esquema 2.0 real de TRANSACCIONES_PAGO para pagos residenciales
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

    @Override
    @Transactional
    @Auditable(action = "CREATE_INTENTION", resource = "WOMPI_MEMBRESIA", category = AuditCategory.FINANCIAL, severity = AuditSeverity.CRITICAL)
    public Map<String, Object> crearIntencionMembresia(String tipoOperacion, Long idPlanDestino, String cicloFacturacion) throws Exception {
        if (!"RENOVACION".equalsIgnoreCase(tipoOperacion) && !"UPGRADE".equalsIgnoreCase(tipoOperacion)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Operación inválida: debe ser RENOVACION o UPGRADE"
            );
        }
        String op = tipoOperacion.toUpperCase();

        String pubKey = getPublicKey();
        String integritySec = getIntegritySecret();
        if (pubKey == null || pubKey.isBlank() || integritySec == null || integritySec.isBlank()) {
            throw new RuntimeException("Wompi no configurado.");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getOrganizationId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("No se encontró contexto de organización activo");
        }
        Long orgId = ctx.getOrganizationId();

        // Consultar membresía activa, de prueba o expirada de la organización
        String sqlMem = """
            SELECT m.ID_MEMBRESIA, m.ID_ORGANIZACION, m.ID_PLAN, m.ESTADO, m.FECHA_INICIO, m.FECHA_FIN, m.ES_PRUEBA,
                   p.CODIGO AS PLAN_CODIGO, p.NOMBRE AS PLAN_NOMBRE, p.PRECIO_MENSUAL AS PLAN_PRECIO, p.ESTADO AS PLAN_ESTADO
            FROM MEMBRESIAS m
            JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN
            WHERE m.ID_ORGANIZACION = :orgId AND m.ESTADO IN ('ACTIVA', 'PRUEBA', 'EXPIRADA')
            ORDER BY m.ID_MEMBRESIA DESC
            """;
        List<Map<String, Object>> memList = jdbcTemplate.queryForList(sqlMem, new MapSqlParameterSource("orgId", orgId));
        if (memList.isEmpty()) {
            List<Map<String, Object>> otherMems = jdbcTemplate.queryForList(
                "SELECT ESTADO FROM MEMBRESIAS WHERE ID_ORGANIZACION = :orgId ORDER BY ID_MEMBRESIA DESC",
                new MapSqlParameterSource("orgId", orgId)
            );
            if (!otherMems.isEmpty()) {
                String est = (String) otherMems.get(0).get("ESTADO");
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "La membresía de la organización se encuentra en estado '" + est + "' y no permite renovación o upgrade por autoservicio."
                );
            }
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "No se encontró una membresía registrada para la organización"
            );
        }

        Map<String, Object> currentMem = memList.get(0);
        Long idMembresia = ((Number) currentMem.get("ID_MEMBRESIA")).longValue();
        Long idPlanActual = ((Number) currentMem.get("ID_PLAN")).longValue();
        BigDecimal precioActual = (BigDecimal) currentMem.get("PLAN_PRECIO");
        String estadoPlanActual = (String) currentMem.get("PLAN_ESTADO");

        Long idPlanCobrar;
        String nombrePlanCobrar;
        BigDecimal precioMensualCobrar;

        if ("RENOVACION".equals(op)) {
            if (!"ACTIVO".equalsIgnoreCase(estadoPlanActual)) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El plan actual de la membresía ya no se encuentra activo en el catálogo"
                );
            }
            if (precioActual == null || precioActual.compareTo(BigDecimal.ZERO) <= 0) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El plan actual gratuito no requiere pago a través de pasarela"
                );
            }
            idPlanCobrar = idPlanActual;
            nombrePlanCobrar = (String) currentMem.get("PLAN_NOMBRE");
            precioMensualCobrar = precioActual;
        } else {
            // UPGRADE
            if (idPlanDestino == null) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El identificador del plan destino (idPlanNuevo) es obligatorio para un upgrade"
                );
            }
            if (idPlanDestino.equals(idPlanActual)) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El plan destino debe ser diferente al plan actual"
                );
            }

            List<Map<String, Object>> targetPlanList = jdbcTemplate.queryForList(
                "SELECT ID_PLAN, CODIGO, NOMBRE, PRECIO_MENSUAL, ESTADO FROM PLANES WHERE ID_PLAN = :p",
                new MapSqlParameterSource("p", idPlanDestino)
            );
            if (targetPlanList.isEmpty() || !"ACTIVO".equalsIgnoreCase((String) targetPlanList.get(0).get("ESTADO"))) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "El plan destino no existe o no se encuentra activo"
                );
            }

            Map<String, Object> targetPlan = targetPlanList.get(0);
            BigDecimal precioNuevo = (BigDecimal) targetPlan.get("PRECIO_MENSUAL");
            if (precioNuevo == null || (precioActual != null && precioNuevo.compareTo(precioActual) <= 0)) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "La operación no es un upgrade válido: el plan destino debe ser de nivel y tarifa superior"
                );
            }

            idPlanCobrar = idPlanDestino;
            nombrePlanCobrar = (String) targetPlan.get("NOMBRE");
            precioMensualCobrar = precioNuevo;
        }

        // Política de cobro determinista centralizada en PlanPricingPolicy
        boolean esAnual = com.saed.backend.finanzas.service.PlanPricingPolicy.esCicloAnual(cicloFacturacion);
        BigDecimal montoPesos = com.saed.backend.finanzas.service.PlanPricingPolicy.calcularMontoPesos(precioMensualCobrar, cicloFacturacion);
        long montoCentavos = com.saed.backend.finanzas.service.PlanPricingPolicy.calcularMontoCentavos(precioMensualCobrar, cicloFacturacion);

        if (montoCentavos <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "El monto a cobrar calculado debe ser superior a cero"
            );
        }

        long ts = System.currentTimeMillis();
        String referencia = "RENOVACION".equals(op)
            ? "SAED-RENOVACION-" + orgId + "-" + idMembresia + "-" + ts
            : "SAED-UPGRADE-" + orgId + "-" + idMembresia + "-" + idPlanDestino + "-" + ts;

        String firma = firmaIntegridad(referencia, montoCentavos);

        // GAP-F6-03: Segregación Financiera Plataforma SaaS vs Propiedad Horizontal
        // Transacciones de suscripción SaaS (MEMBRESIA, RENOVACION, UPGRADE) NO pertenecen a unidades residenciales.
        // ID_UNIDAD es NULL e ID_ORGANIZACION registra la entidad corporativa pagadora.
        String sqlTx = """
            INSERT INTO TRANSACCIONES_PAGO (
                ID_UNIDAD, ID_ORGANIZACION, ID_PAGO, PASARELA, ID_TRANSACCION_PASARELA, REFERENCIA_INTERNA,
                MONTO_CENTAVOS, MONEDA, ESTADO_PASARELA, METODO_ORIGEN, FIRMA_CHECKSUM
            ) VALUES (
                NULL, :org, NULL, 'WOMPI', :ref, :ref, :mc, 'COP', 'PENDIENTE', :concepto, :firma
            )
            """;
        jdbcTemplate.update(sqlTx, new MapSqlParameterSource()
            .addValue("org", orgId)
            .addValue("ref", referencia)
            .addValue("mc", montoCentavos)
            .addValue("concepto", op)
            .addValue("firma", firma)
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("referencia", referencia);
        resp.put("montoCentavos", montoCentavos);
        resp.put("moneda", "COP");
        resp.put("publicKey", pubKey);
        resp.put("firmaIntegridad", firma);
        resp.put("operacion", op);
        resp.put("idMembresia", idMembresia);
        resp.put("idPlan", idPlanCobrar);
        resp.put("planNombre", nombrePlanCobrar);
        resp.put("cicloFacturacion", esAnual ? "ANUAL" : "MENSUAL");
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
        SaedContext systemCtx = SaedContext.builder()
            .userId(1L)
            .organizationId(1L)
            .propertyId(1L)
            .roleCode("SUPERADMIN")
            .roleScope("GLOBAL")
            .build();
        SaedContextHolder.setContext(systemCtx);

        DataSource dataSource = jdbcTemplate.getJdbcTemplate().getDataSource();
        Connection activeConn = null;
        if (dataSource != null) {
            try {
                activeConn = DataSourceUtils.getConnection(dataSource);
            } catch (Exception e) {
                log.warn("[Wompi] Could not acquire active transactional connection: {}", e.getMessage());
            }
        }
        try {
            try {
                if (activeConn != null && !activeConn.isClosed()) {
                    try (CallableStatement cs = activeConn.prepareCall("{call PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?)}")) {
                        cs.setLong(1, 1L);
                        cs.execute();
                    }
                    try (CallableStatement cs = activeConn.prepareCall("{call PKG_SAED_SESSION.SET_CONTEXT(?, ?, ?, ?)}")) {
                        cs.setLong(1, 1L);
                        cs.setLong(2, 1L);
                        cs.setLong(3, 1L);
                        cs.setString(4, "SUPERADMIN");
                        cs.execute();
                    }
                } else {
                    jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                    jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
                }
            } catch (Exception e) {
                log.warn("[Wompi] Warning setting initial SUPERADMIN context: {}", e.getMessage());
            }

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
            Long idUnidad = intencion.get("ID_UNIDAD") != null ? ((Number) intencion.get("ID_UNIDAD")).longValue() : null;
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
            if (idUnidad != null) {
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
            } else if (referencia.startsWith("SAED-MEMBRESIA-") || referencia.startsWith("SAED-RENOVACION-") || referencia.startsWith("SAED-UPGRADE-")) {
                String[] parts = referencia.split("-");
                if (parts.length >= 3) {
                    try {
                        idOrganizacion = Long.parseLong(parts[2]);
                    } catch (Exception ignored) {}
                }
            }

            if (idOrganizacion != null) {
                long targetOrg = idOrganizacion;
                long targetProp = idPropiedad != null ? idPropiedad : 1L;
                long targetUnit = idUnidad != null ? idUnidad : 1L;
                SaedContext tenantCtx = SaedContext.builder()
                    .userId(1L)
                    .organizationId(targetOrg)
                    .propertyId(targetProp)
                    .unitId(targetUnit)
                    .roleCode("SUPERADMIN")
                    .roleScope("GLOBAL")
                    .build();
                SaedContextHolder.setContext(tenantCtx);
                try {
                    if (activeConn != null && !activeConn.isClosed()) {
                        try (CallableStatement cs = activeConn.prepareCall("{call PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(?)}")) {
                            cs.setLong(1, 1L);
                            cs.execute();
                        }
                        try (CallableStatement cs = activeConn.prepareCall("{call PKG_SAED_SESSION.SET_CONTEXT(?, ?, ?, ?)}")) {
                            cs.setLong(1, 1L);
                            cs.setLong(2, targetOrg);
                            cs.setLong(3, targetProp);
                            cs.setString(4, "SUPERADMIN");
                            cs.execute();
                        }
                    } else {
                        jdbcTemplate.getJdbcOperations().execute(String.format(
                            "BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, %d, %d, 'SUPERADMIN'); END;",
                            targetOrg, targetProp
                        ));
                    }
                } catch (Exception e) {
                    log.warn("[Wompi] Warning re-scoping context to org {}: {}", targetOrg, e.getMessage());
                }
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
                Long idItem = null;
                try {
                    idItem = refParts.length >= 3 ? Long.parseLong(refParts[2]) : null;
                } catch (NumberFormatException ignored) {}

                try {
                    if ("CUOTA".equals(concepto) && idItem != null) {
                        finanzasService.registrarPago(new PagoRequestDTO(
                            idItem,
                            java.time.LocalDate.now(),
                            montoPesos,
                            "PASARELA_WOMPI",
                            referencia
                        ));
                    } else if ("MULTA".equals(concepto) && idItem != null && idUnidad != null) {
                        jdbcTemplate.update(
                            "UPDATE MULTAS SET ESTADO = 'PAGADA' WHERE ID_MULTA = :id AND ID_UNIDAD = :u",
                            new MapSqlParameterSource("id", idItem).addValue("u", idUnidad)
                        );
                    } else if ("MEMBRESIA".equals(concepto) && idItem != null) {
                        Long idOrg = idItem;
                        jdbcTemplate.update(
                            "UPDATE ORGANIZACIONES SET ESTADO = 'ACTIVA' WHERE ID_ORGANIZACION = :org",
                            new MapSqlParameterSource("org", idOrg)
                        );
                        jdbcTemplate.update(
                            "UPDATE MEMBRESIAS SET ESTADO = 'ACTIVA' WHERE ID_ORGANIZACION = :org AND (ESTADO = 'SUSPENDIDA' OR ESTADO = 'PENDIENTE')",
                            new MapSqlParameterSource("org", idOrg)
                        );

                        // Buscar el usuario administrador principal de la organización
                        List<Map<String, Object>> admins = jdbcTemplate.queryForList(
                            "SELECT UA.ID_USUARIO, U.EMAIL, U.NOMBRE_USUARIO, P.PRIMER_NOMBRE, P.PRIMER_APELLIDO, " +
                            "O.NOMBRE AS ORG_NOMBRE, PL.NOMBRE AS PLAN_NOMBRE, M.ID_MEMBRESIA, M.ID_PLAN " +
                            "FROM USUARIO_ASIGNACIONES UA " +
                            "JOIN USUARIOS U ON UA.ID_USUARIO = U.ID_USUARIO " +
                            "JOIN PERSONAS P ON U.ID_PERSONA = P.ID_PERSONA " +
                            "JOIN ORGANIZACIONES O ON UA.ID_ORGANIZACION = O.ID_ORGANIZACION " +
                            "LEFT JOIN MEMBRESIAS M ON M.ID_ORGANIZACION = O.ID_ORGANIZACION " +
                            "LEFT JOIN PLANES PL ON M.ID_PLAN = PL.ID_PLAN " +
                            "WHERE UA.ID_ORGANIZACION = :org AND UA.ESTADO IN ('ACTIVA', 'ACTIVO') AND ROWNUM = 1",
                            new MapSqlParameterSource("org", idOrg)
                        );

                        if (!admins.isEmpty()) {
                            Map<String, Object> adminData = admins.get(0);
                            Long idAdmin = ((Number) adminData.get("ID_USUARIO")).longValue();
                            String emailAdmin = (String) adminData.get("EMAIL");
                            String usernameAdmin = (String) adminData.get("NOMBRE_USUARIO");

                            if (adminData.get("ID_MEMBRESIA") != null && adminData.get("ID_PLAN") != null) {
                                Long idMem = ((Number) adminData.get("ID_MEMBRESIA")).longValue();
                                Long idPlan = ((Number) adminData.get("ID_PLAN")).longValue();
                                try {
                                    membershipHistoryService.recordChange(
                                        idMem, idPlan, idPlan, "REACTIVACION",
                                        "Reactivación de membresía aprobada vía Wompi (Ref: " + referencia + ")",
                                        idAdmin
                                    );
                                } catch (Exception exHist) {
                                    log.warn("[Wompi] Error registrando reactivación en historial: {}", exHist.getMessage());
                                }
                            }
                            String nombreAdmin = ((adminData.get("PRIMER_NOMBRE") != null ? adminData.get("PRIMER_NOMBRE") : "") + " " +
                                                  (adminData.get("PRIMER_APELLIDO") != null ? adminData.get("PRIMER_APELLIDO") : "")).trim();
                            String orgNombre = (String) adminData.get("ORG_NOMBRE");
                            String planNombre = (String) adminData.get("PLAN_NOMBRE");

                            String autoPass = com.saed.backend.common.util.PasswordGenerator.generate();
                            jdbcTemplate.update(
                                "UPDATE USUARIOS SET ESTADO = 'ACTIVO', HASH_PASSWORD = :pwd WHERE ID_USUARIO = :usr",
                                new MapSqlParameterSource("usr", idAdmin)
                                    .addValue("pwd", passwordEncoder.encode(autoPass))
                            );

                            try {
                                emailService.enviarBienvenidaCredenciales(
                                    emailAdmin,
                                    nombreAdmin,
                                    orgNombre != null ? orgNombre : "SAED",
                                    planNombre != null ? planNombre : "Plan Comercial",
                                    "ADMIN_ORGANIZACION",
                                    usernameAdmin != null ? usernameAdmin : emailAdmin,
                                    autoPass,
                                    "https://saedfront.vercel.app/login"
                                );
                            } catch (Exception exCreds) {
                                log.warn("[Wompi] Error enviando credenciales de bienvenida a {}: {}", emailAdmin, exCreds.getMessage());
                            }

                            try {
                                emailService.enviarReciboPago(emailAdmin, "MEMBRESIA_SAED", montoPesos, referencia, java.time.LocalDate.now().toString());
                            } catch (Exception exEmail) {
                                log.warn("[Wompi] Error enviando comprobante de membresía a {}", emailAdmin, exEmail);
                            }
                        }
                    } else if ("ONBOARDING".equals(concepto) || referencia.contains("-ONB-")) {
                        onboardingService.materializarOrganizacion(referencia, expectedCentavos, idWompi);
                    } else if ("RENOVACION".equals(concepto) || referencia.startsWith("SAED-RENOVACION-")) {
                        String[] parts = referencia.split("-");
                        if (parts.length >= 4) {
                            Long idOrg = Long.parseLong(parts[2]);
                            Long idMem = Long.parseLong(parts[3]);

                            // Bloqueo pesimista FOR UPDATE para serialización estricta de callbacks concurrentes
                            List<Map<String, Object>> memRows = jdbcTemplate.queryForList(
                                "SELECT ID_MEMBRESIA, ID_ORGANIZACION, ID_PLAN, FECHA_FIN, ESTADO FROM MEMBRESIAS WHERE ID_MEMBRESIA = :id AND ID_ORGANIZACION = :org FOR UPDATE",
                                new MapSqlParameterSource("id", idMem).addValue("org", idOrg)
                            );
                            if (!memRows.isEmpty()) {
                                Map<String, Object> memRow = memRows.get(0);
                                Long idPlan = ((Number) memRow.get("ID_PLAN")).longValue();
                                String estadoAnterior = (String) memRow.get("ESTADO");

                                List<Map<String, Object>> pRows = jdbcTemplate.queryForList(
                                    "SELECT PRECIO_MENSUAL FROM PLANES WHERE ID_PLAN = :p",
                                    new MapSqlParameterSource("p", idPlan)
                                );
                                long precioMensual = !pRows.isEmpty() ? ((Number) pRows.get(0).get("PRECIO_MENSUAL")).longValue() : 0L;
                                int meses = (precioMensual > 0 && expectedCentavos >= precioMensual * 100 * 10) ? 12 : 1;

                                jdbcTemplate.update("""
                                    UPDATE MEMBRESIAS
                                    SET FECHA_FIN = ADD_MONTHS(GREATEST(NVL(FECHA_FIN, TRUNC(SYSDATE)), TRUNC(SYSDATE)), :meses),
                                        FECHA_RENOVACION = TRUNC(SYSDATE),
                                        ESTADO = 'ACTIVA',
                                        ES_PRUEBA = 'N'
                                    WHERE ID_MEMBRESIA = :id
                                    """,
                                    new MapSqlParameterSource("id", idMem).addValue("meses", meses)
                                );

                                jdbcTemplate.update(
                                    "UPDATE ORGANIZACIONES SET ESTADO = 'ACTIVA' WHERE ID_ORGANIZACION = :org",
                                    new MapSqlParameterSource("org", idOrg)
                                );

                                Long idAdmin = resolverAdminOrg(idOrg);

                                membershipHistoryService.recordChange(
                                    idMem,
                                    idPlan,
                                    idPlan,
                                    "RENOVACION",
                                    "Renovación de membresía por " + meses + " mes(es) aprobada vía Wompi (Ref: " + referencia + ")",
                                    idAdmin
                                );

                                if (auditService != null) {
                                    auditService.recordSuccess(idAdmin, idOrg, null, "PAGO", "MEMBRESIAS", idMem, "127.0.0.1", "WompiWebhook", estadoAnterior, "ACTIVA");
                                }

                                enviarComprobanteAdmin(idOrg, "RENOVACION_MEMBRESIA", montoPesos, referencia);
                            }
                        }
                    } else if ("UPGRADE".equals(concepto) || referencia.startsWith("SAED-UPGRADE-")) {
                        String[] parts = referencia.split("-");
                        if (parts.length >= 5) {
                            Long idOrg = Long.parseLong(parts[2]);
                            Long idMem = Long.parseLong(parts[3]);
                            Long idPlanNuevo = Long.parseLong(parts[4]);

                            List<Map<String, Object>> memRows = jdbcTemplate.queryForList(
                                "SELECT ID_MEMBRESIA, ID_ORGANIZACION, ID_PLAN, FECHA_FIN, ESTADO FROM MEMBRESIAS WHERE ID_MEMBRESIA = :id AND ID_ORGANIZACION = :org FOR UPDATE",
                                new MapSqlParameterSource("id", idMem).addValue("org", idOrg)
                            );
                            if (!memRows.isEmpty()) {
                                Map<String, Object> memRow = memRows.get(0);
                                Long idPlanActual = ((Number) memRow.get("ID_PLAN")).longValue();
                                String estadoAnterior = (String) memRow.get("ESTADO");

                                List<Map<String, Object>> pRows = jdbcTemplate.queryForList(
                                    "SELECT PRECIO_MENSUAL, NOMBRE FROM PLANES WHERE ID_PLAN = :p",
                                    new MapSqlParameterSource("p", idPlanNuevo)
                                );
                                long precioMensual = !pRows.isEmpty() ? ((Number) pRows.get(0).get("PRECIO_MENSUAL")).longValue() : 0L;
                                String planNombre = !pRows.isEmpty() ? (String) pRows.get(0).get("NOMBRE") : ("Plan " + idPlanNuevo);
                                int meses = (precioMensual > 0 && expectedCentavos >= precioMensual * 100 * 10) ? 12 : 1;

                                jdbcTemplate.update("""
                                    UPDATE MEMBRESIAS
                                    SET ID_PLAN = :idPlanNuevo,
                                        FECHA_INICIO = TRUNC(SYSDATE),
                                        FECHA_FIN = ADD_MONTHS(TRUNC(SYSDATE), :meses),
                                        FECHA_RENOVACION = TRUNC(SYSDATE),
                                        ESTADO = 'ACTIVA',
                                        ES_PRUEBA = 'N'
                                    WHERE ID_MEMBRESIA = :id
                                    """,
                                    new MapSqlParameterSource("id", idMem)
                                        .addValue("idPlanNuevo", idPlanNuevo)
                                        .addValue("meses", meses)
                                );

                                jdbcTemplate.update(
                                    "UPDATE ORGANIZACIONES SET ESTADO = 'ACTIVA' WHERE ID_ORGANIZACION = :org",
                                    new MapSqlParameterSource("org", idOrg)
                                );

                                Long idAdmin = resolverAdminOrg(idOrg);

                                membershipHistoryService.recordChange(
                                    idMem,
                                    idPlanActual,
                                    idPlanNuevo,
                                    "UPGRADE",
                                    "Upgrade a " + planNombre + " (" + meses + " mes(es)) aprobado vía Wompi (Ref: " + referencia + ")",
                                    idAdmin
                                );

                                if (auditService != null) {
                                    auditService.recordSuccess(idAdmin, idOrg, null, "PAGO", "MEMBRESIAS", idMem, "127.0.0.1", "WompiWebhook", estadoAnterior, "ACTIVA");
                                }

                                enviarComprobanteAdmin(idOrg, "UPGRADE_MEMBRESIA", montoPesos, referencia);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[Wompi] Error registrando pago aprobado", e);
                }

                // Recibo por correo para unidades residenciales
                if (idUnidad != null && !"MEMBRESIA".equals(concepto) && !"RENOVACION".equals(concepto) && !"UPGRADE".equals(concepto) && !referencia.startsWith("SAED-RENOVACION-") && !referencia.startsWith("SAED-UPGRADE-")) {
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
        } finally {
            try {
                if (activeConn != null && !activeConn.isClosed()) {
                    try (CallableStatement cs = activeConn.prepareCall("{call PKG_SAED_SESSION.CLEAR_CONTEXT()}")) {
                        cs.execute();
                    }
                } else {
                    jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
                }
            } catch (Exception e) {
                // [C5][H-07][SEC-03] CRITICAL: failed to CLEAR Oracle session context after Wompi webhook.
                // Abort the connection to force eviction by HikariCP, preventing residual privileged context in the pool.
                log.error("[SECURITY][SEC-03] CRITICAL: failed to CLEAR Oracle session context after Wompi webhook. "
                        + "Aborting connection to prevent SUPERADMIN context bleed in connection pool. Error: {}", e.getMessage(), e);
                try {
                    if (activeConn != null && !activeConn.isClosed()) {
                        activeConn.abort(Runnable::run);
                    }
                } catch (Exception abortEx) {
                    log.error("[SECURITY][SEC-03] Failed to abort tainted connection: {}", abortEx.getMessage(), abortEx);
                }
            } finally {
                if (activeConn != null && dataSource != null) {
                    try {
                        DataSourceUtils.releaseConnection(activeConn, dataSource);
                    } catch (Exception ignored) {}
                }
                if (prevCtx != null) {
                    SaedContextHolder.setContext(prevCtx);
                } else {
                    SaedContextHolder.clearContext();
                }
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

    private Long resolverAdminOrg(Long idOrg) {
        try {
            List<Long> admins = jdbcTemplate.queryForList(
                "SELECT UA.ID_USUARIO FROM USUARIO_ASIGNACIONES UA " +
                "JOIN ROLES R ON UA.ID_ROL = R.ID_ROL " +
                "WHERE UA.ID_ORGANIZACION = :org AND R.CODIGO = 'ADMIN_ORGANIZACION' AND UA.ESTADO IN ('ACTIVA', 'ACTIVO') " +
                "ORDER BY UA.ID_USUARIO ASC",
                new MapSqlParameterSource("org", idOrg),
                Long.class
            );
            if (!admins.isEmpty() && admins.get(0) != null) return admins.get(0);
        } catch (Exception ignored) {}
        return null;
    }

    private void enviarComprobanteAdmin(Long idOrg, String concepto, BigDecimal montoPesos, String referencia) {
        try {
            List<String> emails = jdbcTemplate.queryForList(
                "SELECT U.EMAIL FROM USUARIO_ASIGNACIONES UA " +
                "JOIN USUARIOS U ON UA.ID_USUARIO = U.ID_USUARIO " +
                "JOIN ROLES R ON UA.ID_ROL = R.ID_ROL " +
                "WHERE UA.ID_ORGANIZACION = :org AND R.CODIGO = 'ADMIN_ORGANIZACION' AND UA.ESTADO IN ('ACTIVA', 'ACTIVO') AND U.EMAIL IS NOT NULL " +
                "ORDER BY UA.ID_USUARIO ASC",
                new MapSqlParameterSource("org", idOrg),
                String.class
            );
            if (!emails.isEmpty() && emails.get(0) != null) {
                emailService.enviarReciboPago(emails.get(0), concepto, montoPesos, referencia, java.time.LocalDate.now().toString());
            }
        } catch (Exception ex) {
            log.warn("[Wompi] Error enviando comprobante a admin de org {}: {}", idOrg, ex.getMessage());
        }
    }
}