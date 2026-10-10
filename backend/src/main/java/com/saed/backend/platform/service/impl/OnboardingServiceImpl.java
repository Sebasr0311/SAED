package com.saed.backend.platform.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.service.TokenActivacionService;
import com.saed.backend.platform.dto.OnboardingRegistroRequestDTO;
import com.saed.backend.platform.service.DefaultContractTemplateProvisioner;
import com.saed.backend.platform.service.MembershipHistoryService;
import com.saed.backend.platform.service.OnboardingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Public SaaS onboarding (DEC-06, F3-04, F3-05, F3-06, F3-08, DEC-09).
 *
 * <ul>
 *   <li>The flow is decided on the server: a zero-price plan is provisioned free, a plan flagged
 *       {@code PLANES.PERMITE_PRUEBA = 'S'} gets one trial per tax id / e-mail, everything else
 *       requires a verified payment. The client {@code esPrueba} flag is ignored.</li>
 *   <li>Registration never purges, deletes or overwrites existing organizations, persons or users;
 *       any collision (tax id, e-mail, username, document) is a 409.</li>
 *   <li>The administrator account is created {@code PENDIENTE_VERIFICACION} and receives a
 *       single-use activation link; no password is generated or e-mailed.</li>
 *   <li>A verified payment that cannot be provisioned (rejected/stale intention, amount mismatch,
 *       collision, unreadable data, unknown reference) moves the intention to
 *       {@code PAGO_REQUIERE_REVISION} for platform recovery instead of being dropped.</li>
 * </ul>
 *
 * Provisioning a new tenant is a platform operation; it runs in the platform provisioning
 * context because the tenant does not exist yet.
 */
@Service
public class OnboardingServiceImpl implements OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingServiceImpl.class);

    static final String ESTADO_PENDIENTE = "PENDIENTE";
    static final String ESTADO_COMPLETADA = "COMPLETADA";
    static final String ESTADO_REVISION = "PAGO_REQUIERE_REVISION";

    private static final String COLLISION_MESSAGE =
            "Ya existe un registro con los datos suministrados (NIT, correo, usuario o documento). "
            + "Si es su organización, contacte a soporte.";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final MembershipHistoryService membershipHistoryService;
    private final TokenActivacionService tokenActivacionService;
    private final DefaultContractTemplateProvisioner templateProvisioner;
    private final SecureRandom random = new SecureRandom();

    // Keys come only from configuration (saed.wompi.*); no hardcoded or environment fallback.
    @Value("${saed.wompi.public-key:}")
    private String wompiPublicKey;

    @Value("${saed.wompi.integrity-secret:}")
    private String wompiIntegritySecret;

    public OnboardingServiceImpl(
            NamedParameterJdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            ObjectMapper objectMapper,
            MembershipHistoryService membershipHistoryService,
            @Lazy TokenActivacionService tokenActivacionService,
            DefaultContractTemplateProvisioner templateProvisioner) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.membershipHistoryService = membershipHistoryService;
        this.tokenActivacionService = tokenActivacionService;
        this.templateProvisioner = templateProvisioner;
    }

    @Override
    public List<Map<String, Object>> listarPlanesPublicos() {
        String sql = """
            SELECT ID_PLAN AS "idPlan",
                   CODIGO AS "codigo",
                   NOMBRE AS "nombre",
                   DESCRIPCION AS "descripcion",
                   PRECIO_MENSUAL AS "precioMensual",
                   LIMITE_PROPIEDADES AS "limitePropiedades",
                   LIMITE_UNIDADES AS "limiteUnidades",
                   LIMITE_USUARIOS AS "limiteUsuarios",
                   LIMITE_ALMACENAMIENTO_GB AS "limiteAlmacenamientoGb",
                   PERMITE_PRUEBA AS "permitePrueba"
            FROM PLANES
            WHERE ESTADO = 'ACTIVO'
            ORDER BY PRECIO_MENSUAL ASC
            """;
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> p : list) {
            Map<String, Object> map = new LinkedHashMap<>(p);
            Number pm = (Number) map.get("precioMensual");
            long mensual = pm != null ? pm.longValue() : 0L;
            map.put("precioAnual", com.saed.backend.finanzas.service.PlanPricingPolicy.calcularPrecioAnual(mensual));
            map.put("descuentoAnual", com.saed.backend.finanzas.service.PlanPricingPolicy.DESCUENTO_ANUAL_PORCENTAJE);

            Long idPlan = ((Number) map.get("idPlan")).longValue();
            List<Map<String, Object>> mods = jdbcTemplate.queryForList("""
                SELECT m.CODIGO AS "codigo", m.NOMBRE AS "nombre", m.DESCRIPCION AS "descripcion"
                FROM PLAN_MODULOS pm
                JOIN MODULOS m ON pm.ID_MODULO = m.ID_MODULO
                WHERE pm.ID_PLAN = :idPlan AND pm.HABILITADO = 'S'
                ORDER BY m.ID_MODULO ASC
                """, new MapSqlParameterSource("idPlan", idPlan));
            map.put("modulos", mods);
            map.put("modulosCodigos", mods.stream().map(m -> (String) m.get("codigo")).toList());
            enriched.add(map);
        }
        return enriched;
    }

    // ------------------------------------------------------------------ registration

    @Override
    @Transactional
    public Map<String, Object> registrar(OnboardingRegistroRequestDTO request) {
        Registration reg = Registration.of(request);
        if (reg.document().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de documento del administrador es obligatorio");
        }

        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            establecerContextoProvisionamiento();

            if (findCollision(reg) != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, COLLISION_MESSAGE);
            }

            Map<String, Object> plan = loadActivePlan(request.getIdPlan());
            if (plan == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El plan seleccionado no existe o no está activo");
            }
            long precioMensual = toLong(plan.get("PRECIO_MENSUAL"));

            // DEC-06: the client esPrueba flag is ignored; eligibility is derived from the plan.
            if (precioMensual == 0) {
                return provisionWithoutPayment(request, reg, plan, "GRATIS");
            }
            if ("S".equalsIgnoreCase(String.valueOf(plan.get("PERMITE_PRUEBA"))) && !trialAlreadyUsed(reg)) {
                return provisionWithoutPayment(request, reg, plan, "PRUEBA");
            }
            return createPaymentIntention(request, reg, precioMensual);
        } finally {
            restaurarContexto(prevCtx);
        }
    }

    private Map<String, Object> createPaymentIntention(OnboardingRegistroRequestDTO request, Registration reg,
                                                       long precioMensual) {
        if (isBlank(wompiPublicKey) || isBlank(wompiIntegritySecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La pasarela de pagos no está configurada; intente más tarde.");
        }
        long montoPesos = com.saed.backend.finanzas.service.PlanPricingPolicy.calcularMontoPesos(precioMensual, request.getCicloFacturacion());
        long montoCentavos = com.saed.backend.finanzas.service.PlanPricingPolicy.calcularMontoCentavos(precioMensual, request.getCicloFacturacion());

        // Unpredictable reference (F3-12); no '-' inside the random part (webhook splits on '-').
        String referencia = "SAED-MEMBRESIA-ONB-" + UUID.randomUUID().toString().replace("-", "");
        String firmaIntegridad = calcularFirmaIntegridad(referencia, montoCentavos);

        jdbcTemplate.update("""
            INSERT INTO ONBOARDING_INTENCIONES (REFERENCIA, DATOS_REGISTRO, MONTO_CENTAVOS, ESTADO,
                FECHA_CREACION, FECHA_ACTUALIZACION, TIPO_FLUJO, IDENTIFICACION_FISCAL, EMAIL_ADMIN, ID_PLAN)
            VALUES (:ref, :datos, :monto, 'PENDIENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :tipo, :nit, :email, :plan)
            """, new MapSqlParameterSource()
                .addValue("ref", referencia)
                .addValue("datos", toJson(request))
                .addValue("monto", montoCentavos)
                .addValue("tipo", "PAGO")
                .addValue("nit", reg.nit())
                .addValue("email", reg.email())
                .addValue("plan", request.getIdPlan()));

        // GAP-F6-03: platform onboarding payment does not belong to a residential unit.
        jdbcTemplate.update("""
            INSERT INTO TRANSACCIONES_PAGO (ID_UNIDAD, ID_ORGANIZACION, ID_PAGO, PASARELA, ID_TRANSACCION_PASARELA, REFERENCIA_INTERNA, MONTO_CENTAVOS, MONEDA, ESTADO_PASARELA, METODO_ORIGEN, FIRMA_CHECKSUM)
            VALUES (NULL, NULL, NULL, 'WOMPI', :ref, :ref, :mc, 'COP', 'PENDIENTE', 'ONBOARDING', :firma)
            """, new MapSqlParameterSource()
                .addValue("ref", referencia)
                .addValue("mc", montoCentavos)
                .addValue("firma", firmaIntegridad));

        Map<String, Object> resp = new HashMap<>();
        resp.put("idOrganizacion", null);
        resp.put("requierePago", true);
        resp.put("esPrueba", false);
        resp.put("referencia", referencia);
        resp.put("montoCentavos", montoCentavos);
        resp.put("montoPesos", montoPesos);
        resp.put("moneda", "COP");
        resp.put("wompiPublicKey", wompiPublicKey);
        resp.put("firmaIntegridad", firmaIntegridad);
        resp.put("adminEmail", reg.email());
        resp.put("mensaje", "Intención de registro generada exitosamente. Procede al pago seguro de tu membresía con Wompi.");
        return resp;
    }

    private Map<String, Object> provisionWithoutPayment(OnboardingRegistroRequestDTO request, Registration reg,
                                                        Map<String, Object> plan, String tipoFlujo) {
        Provisioned created = provision(request, reg, 1, true,
                "PRUEBA".equals(tipoFlujo) ? "Membresía de prueba creada en Onboarding"
                        : "Membresía de plan gratuito creada en Onboarding");

        String referencia = "SAED-" + tipoFlujo + "-ONB-" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
            INSERT INTO ONBOARDING_INTENCIONES (REFERENCIA, DATOS_REGISTRO, MONTO_CENTAVOS, ESTADO,
                FECHA_CREACION, FECHA_ACTUALIZACION, ID_ORGANIZACION, ID_USUARIO, CORREO_ESTADO, CORREO_FECHA,
                TIPO_FLUJO, IDENTIFICACION_FISCAL, EMAIL_ADMIN, ID_PLAN)
            VALUES (:ref, :datos, 0, 'COMPLETADA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :org, :usr, 'ENLACE_ACTIVACION',
                CURRENT_TIMESTAMP, :tipo, :nit, :email, :plan)
            """, new MapSqlParameterSource()
                .addValue("ref", referencia)
                .addValue("datos", toJson(request))
                .addValue("org", created.idOrganizacion())
                .addValue("usr", created.idUsuario())
                .addValue("tipo", tipoFlujo)
                .addValue("nit", reg.nit())
                .addValue("email", reg.email())
                .addValue("plan", request.getIdPlan()));

        tokenActivacionService.generarYEnviarTokenActivacion(created.idUsuario(), null);

        Map<String, Object> resp = new HashMap<>();
        resp.put("idOrganizacion", created.idOrganizacion());
        resp.put("requierePago", false);
        resp.put("esPrueba", "PRUEBA".equals(tipoFlujo));
        resp.put("email", reg.email());
        resp.put("adminUsername", created.username());
        resp.put("mensaje", "¡Bienvenido a SAED 2.0! Tu organización fue creada. Enviamos al correo " + reg.email()
                + " un enlace para activar tu cuenta y definir tu contraseña.");
        return resp;
    }

    // ------------------------------------------------------------------ verified payment

    @Override
    @Transactional
    public boolean materializarOrganizacion(String referencia, Long expectedCentavos, String idTransaccionPasarela) {
        return provisionPaidIntention(referencia, expectedCentavos, false, null);
    }

    @Override
    @Transactional
    public boolean aprobarManualmente(String referencia, String motivo) {
        return provisionPaidIntention(referencia, null, true, motivo);
    }

    private boolean provisionPaidIntention(String referencia, Long paidCentavos, boolean manualApproval, String motivo) {
        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            establecerContextoProvisionamiento();

            // Row lock serializes concurrent webhook / status-poll / approval attempts.
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT REFERENCIA, DATOS_REGISTRO, MONTO_CENTAVOS, ESTADO FROM ONBOARDING_INTENCIONES WHERE REFERENCIA = :ref FOR UPDATE",
                    new MapSqlParameterSource("ref", referencia));

            if (rows.isEmpty()) {
                if (manualApproval) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Intención de onboarding no encontrada");
                }
                log.error("[Onboarding] Verified payment {} has no onboarding intention; recorded for recovery.", referencia);
                jdbcTemplate.update("""
                    INSERT INTO ONBOARDING_INTENCIONES (REFERENCIA, DATOS_REGISTRO, MONTO_CENTAVOS, ESTADO,
                        FECHA_CREACION, FECHA_ACTUALIZACION, TIPO_FLUJO, MOTIVO_ESTADO)
                    VALUES (:ref, '{}', :monto, 'PAGO_REQUIERE_REVISION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'PAGO', :motivo)
                    """, new MapSqlParameterSource()
                        .addValue("ref", referencia)
                        .addValue("monto", paidCentavos != null ? paidCentavos : 0L)
                        .addValue("motivo", "Pago verificado sin intención de onboarding registrada"));
                return false;
            }

            Map<String, Object> intencion = rows.get(0);
            String estado = String.valueOf(intencion.get("ESTADO"));
            if (ESTADO_COMPLETADA.equalsIgnoreCase(estado)) {
                return true;
            }

            boolean provisionable = ESTADO_PENDIENTE.equalsIgnoreCase(estado)
                    || (manualApproval && ESTADO_REVISION.equalsIgnoreCase(estado));
            if (!provisionable) {
                if (manualApproval) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "La solicitud en estado " + estado + " no puede aprobarse.");
                }
                return requireReview(referencia, "Pago verificado para una solicitud en estado " + estado);
            }

            long expected = toLong(intencion.get("MONTO_CENTAVOS"));
            if (!manualApproval && (paidCentavos == null || paidCentavos != expected)) {
                return requireReview(referencia, "El monto pagado no coincide con el monto de la solicitud");
            }

            OnboardingRegistroRequestDTO request;
            try {
                request = objectMapper.readValue(String.valueOf(intencion.get("DATOS_REGISTRO")), OnboardingRegistroRequestDTO.class);
            } catch (JsonProcessingException unreadable) {
                log.error("[Onboarding] Registration data of {} is unreadable ({}); recorded for recovery.",
                        referencia, unreadable.getClass().getSimpleName());
                return manualFailOrReview(manualApproval, referencia, "Datos de registro ilegibles");
            }

            Registration reg = Registration.of(request);
            String collision = findCollision(reg);
            if (collision != null) {
                return manualFailOrReview(manualApproval, referencia,
                        "No se puede aprovisionar: ya existe un registro con el mismo " + collision);
            }
            if (loadActivePlan(request.getIdPlan()) == null) {
                return manualFailOrReview(manualApproval, referencia, "El plan de la solicitud ya no está activo");
            }

            int meses = "ANUAL".equalsIgnoreCase(request.getCicloFacturacion()) ? 12 : 1;
            Provisioned created = provision(request, reg, meses, false, manualApproval
                    ? "Membresía inicial activada por aprobación manual de plataforma"
                    : "Membresía inicial comercial (" + (meses == 12 ? "ANUAL" : "MENSUAL") + ") activada tras pago Onboarding");

            int updated = jdbcTemplate.update("""
                UPDATE ONBOARDING_INTENCIONES
                SET ESTADO = 'COMPLETADA', FECHA_ACTUALIZACION = CURRENT_TIMESTAMP,
                    ID_ORGANIZACION = :org, ID_USUARIO = :usr, MOTIVO_ESTADO = :motivo,
                    CORREO_ESTADO = 'ENLACE_ACTIVACION', CORREO_FECHA = CURRENT_TIMESTAMP
                WHERE REFERENCIA = :ref AND ESTADO IN ('PENDIENTE', 'PAGO_REQUIERE_REVISION')
                """, new MapSqlParameterSource()
                    .addValue("org", created.idOrganizacion())
                    .addValue("usr", created.idUsuario())
                    .addValue("motivo", manualApproval ? "APROBACION_MANUAL: " + (motivo != null ? motivo : "") : null)
                    .addValue("ref", referencia));
            if (updated != 1) {
                throw new IllegalStateException("Onboarding intention changed concurrently: " + referencia);
            }

            tokenActivacionService.generarYEnviarTokenActivacion(created.idUsuario(), null);
            if (!manualApproval) {
                try {
                    emailService.enviarReciboPago(reg.email(), "MEMBRESIA_SAED",
                            new BigDecimal(expected).divide(new BigDecimal(100)), referencia, LocalDate.now().toString());
                } catch (Exception receiptFailure) {
                    // The receipt is informational; provisioning must not be rolled back for it.
                    log.warn("[Onboarding] Payment receipt for {} was not sent ({}).", referencia,
                            receiptFailure.getClass().getSimpleName());
                }
            }
            log.info("[Onboarding] Organization {} provisioned for intention {}.", created.idOrganizacion(), referencia);
            return true;
        } finally {
            restaurarContexto(prevCtx);
        }
    }

    private boolean manualFailOrReview(boolean manualApproval, String referencia, String motivo) {
        if (manualApproval) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, motivo);
        }
        return requireReview(referencia, motivo);
    }

    private boolean requireReview(String referencia, String motivo) {
        log.error("[Onboarding] Verified payment {} requires platform review: {}", referencia, motivo);
        jdbcTemplate.update("""
            UPDATE ONBOARDING_INTENCIONES
            SET ESTADO = 'PAGO_REQUIERE_REVISION', MOTIVO_ESTADO = :motivo, FECHA_ACTUALIZACION = CURRENT_TIMESTAMP
            WHERE REFERENCIA = :ref
            """, new MapSqlParameterSource().addValue("motivo", motivo).addValue("ref", referencia));
        return false;
    }

    @Override
    public Map<String, Object> consultarEstadoPago(String referencia) {
        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            establecerContextoProvisionamiento();

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT ESTADO_PASARELA, MONTO_CENTAVOS, REFERENCIA_INTERNA, FECHA_REGISTRO
                FROM TRANSACCIONES_PAGO
                WHERE REFERENCIA_INTERNA = :ref
                """, new MapSqlParameterSource("ref", referencia));
            if (rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Referencia de pago no encontrada");
            }
            Map<String, Object> tx = rows.get(0);
            String estadoPasarela = (String) tx.get("ESTADO_PASARELA");

            if ("APROBADO".equalsIgnoreCase(estadoPasarela)) {
                materializarOrganizacion(referencia, toLong(tx.get("MONTO_CENTAVOS")), null);
            }
            List<String> stg = jdbcTemplate.queryForList(
                    "SELECT ESTADO FROM ONBOARDING_INTENCIONES WHERE REFERENCIA = :ref",
                    new MapSqlParameterSource("ref", referencia), String.class);
            String estadoIntencion = stg.isEmpty() ? null : stg.get(0);
            String estadoOrg = ESTADO_COMPLETADA.equalsIgnoreCase(estadoIntencion) ? "ACTIVA"
                    : ESTADO_REVISION.equalsIgnoreCase(estadoIntencion) ? "EN_REVISION" : null;

            Map<String, Object> resp = new HashMap<>();
            resp.put("referencia", tx.get("REFERENCIA_INTERNA"));
            resp.put("estado", estadoPasarela);
            resp.put("estadoPasarela", estadoPasarela);
            resp.put("montoCentavos", tx.get("MONTO_CENTAVOS"));
            resp.put("estadoOrganizacion", estadoOrg);
            return resp;
        } finally {
            restaurarContexto(prevCtx);
        }
    }

    // ------------------------------------------------------------------ provisioning

    private record Provisioned(Long idOrganizacion, Long idUsuario, String username) {
    }

    private Provisioned provision(OnboardingRegistroRequestDTO request, Registration reg, int meses,
                                  boolean sinPago, String historialObservacion) {
        Long idOrganizacion = insertReturningKey("""
            INSERT INTO ORGANIZACIONES (NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, TELEFONO_CONTACTO, DIRECCION, CIUDAD, PAIS, ESTADO)
            VALUES (:nom, :nit, :email, :tel, :dir, :ciu, 'Colombia', 'ACTIVA')
            """, new MapSqlParameterSource()
                .addValue("nom", request.getNombreOrganizacion().trim())
                .addValue("nit", reg.nit())
                .addValue("email", request.getEmailOrganizacion() != null ? request.getEmailOrganizacion().trim() : null)
                .addValue("tel", request.getTelefonoOrganizacion())
                .addValue("dir", request.getDireccion())
                .addValue("ciu", resolverCiudad(request.getCiudad(), request.getDepartamento())), "ID_ORGANIZACION");

        Long idPersona = insertReturningKey("""
            INSERT INTO PERSONAS (
                ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA,
                PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO,
                EMAIL, TELEFONO, ESTADO, ID_ORGANIZACION
            ) VALUES (
                :tipoDoc, :doc, 'NATURAL', :nom, :snom, :ape, :sape, :email, :tel, 'ACTIVO', :org
            )
            """, new MapSqlParameterSource()
                .addValue("tipoDoc", resolverIdTipoDocumento(request.getTipoDocumento()))
                .addValue("doc", reg.document())
                .addValue("nom", request.getPrimerNombre().trim())
                .addValue("snom", isBlank(request.getSegundoNombre()) ? null : request.getSegundoNombre().trim())
                .addValue("ape", request.getPrimerApellido().trim())
                .addValue("sape", isBlank(request.getSegundoApellido()) ? null : request.getSegundoApellido().trim())
                .addValue("email", reg.email())
                .addValue("tel", request.getTelefonoAdmin())
                .addValue("org", idOrganizacion), "ID_PERSONA");

        // Unusable random hash: the administrator defines the password through the activation link.
        byte[] unusable = new byte[32];
        random.nextBytes(unusable);
        Long idUsuario = insertReturningKey("""
            INSERT INTO USUARIOS (ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS)
            VALUES (:idPer, :user, :email, :pwd, 'PENDIENTE_VERIFICACION', 0)
            """, new MapSqlParameterSource()
                .addValue("idPer", idPersona)
                .addValue("user", reg.username())
                .addValue("email", reg.email())
                .addValue("pwd", passwordEncoder.encode(HexFormat.of().formatHex(unusable))), "ID_USUARIO");

        jdbcTemplate.update("""
            INSERT INTO USUARIO_ASIGNACIONES (ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO)
            VALUES (:usr, :rol, :org, NULL, NULL, 'ACTIVA')
            """, new MapSqlParameterSource()
                .addValue("usr", idUsuario)
                .addValue("rol", resolverIdRolAdminOrganizacion())
                .addValue("org", idOrganizacion));

        Long idMembresia = insertReturningKey("""
            INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
            VALUES (:org, :plan, TRUNC(SYSDATE), ADD_MONTHS(TRUNC(SYSDATE), :m), 'ACTIVA', :prueba)
            """, new MapSqlParameterSource()
                .addValue("org", idOrganizacion)
                .addValue("plan", request.getIdPlan())
                .addValue("m", meses)
                .addValue("prueba", sinPago ? "S" : "N"), "ID_MEMBRESIA");

        membershipHistoryService.recordChange(idMembresia, null, request.getIdPlan(), "INICIO", historialObservacion, idUsuario);

        // DEC-09: the default contract template is part of organization provisioning.
        templateProvisioner.createDefaultTemplate(idOrganizacion, idUsuario);

        return new Provisioned(idOrganizacion, idUsuario, reg.username());
    }

    private Long insertReturningKey(String sql, MapSqlParameterSource params, String keyColumn) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(sql, params, kh, new String[]{keyColumn});
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("No generated key for " + keyColumn);
        }
        return key.longValue();
    }

    /** Returns the colliding attribute name, or null when the registration is unique. */
    private String findCollision(Registration reg) {
        if (count("SELECT COUNT(1) FROM ORGANIZACIONES WHERE IDENTIFICACION_FISCAL = :nit",
                new MapSqlParameterSource("nit", reg.nit())) > 0) {
            return "NIT";
        }
        if (count("SELECT COUNT(1) FROM USUARIOS WHERE LOWER(EMAIL) = :email",
                new MapSqlParameterSource("email", reg.email())) > 0) {
            return "correo";
        }
        if (count("SELECT COUNT(1) FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = :u",
                new MapSqlParameterSource("u", reg.username())) > 0) {
            return "usuario";
        }
        if (count("SELECT COUNT(1) FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc",
                new MapSqlParameterSource("doc", reg.document())) > 0) {
            return "documento";
        }
        return null;
    }

    private boolean trialAlreadyUsed(Registration reg) {
        return count("""
            SELECT (SELECT COUNT(1) FROM ONBOARDING_INTENCIONES
                    WHERE TIPO_FLUJO = 'PRUEBA' AND (IDENTIFICACION_FISCAL = :nit OR EMAIL_ADMIN = :email))
                 + (SELECT COUNT(1) FROM MEMBRESIAS m JOIN ORGANIZACIONES o ON o.ID_ORGANIZACION = m.ID_ORGANIZACION
                    WHERE m.ES_PRUEBA = 'S' AND o.IDENTIFICACION_FISCAL = :nit)
            FROM DUAL
            """, new MapSqlParameterSource().addValue("nit", reg.nit()).addValue("email", reg.email())) > 0;
    }

    private Map<String, Object> loadActivePlan(Long idPlan) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ID_PLAN, CODIGO, NOMBRE, PRECIO_MENSUAL, PERMITE_PRUEBA FROM PLANES WHERE ID_PLAN = :p AND ESTADO = 'ACTIVO'",
                new MapSqlParameterSource("p", idPlan));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbcTemplate.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    private Long resolverIdTipoDocumento(String docType) {
        String cod = !isBlank(docType) ? docType.trim().toUpperCase(Locale.ROOT) : "CC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ID_TIPO_DOCUMENTO FROM TIPOS_DOCUMENTO WHERE CODIGO = :cod",
                new MapSqlParameterSource("cod", cod));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de documento no soportado");
        }
        return toLong(rows.get(0).get("ID_TIPO_DOCUMENTO"));
    }

    private Long resolverIdRolAdminOrganizacion() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'", new MapSqlParameterSource());
        if (rows.isEmpty()) {
            throw new IllegalStateException("Role ADMIN_ORGANIZACION is missing from the catalog");
        }
        return toLong(rows.get(0).get("ID_ROL"));
    }

    private String resolverCiudad(String ciudad, String departamento) {
        String c = !isBlank(ciudad) ? ciudad.trim() : "Bogotá D.C.";
        if (!isBlank(departamento) && !c.toLowerCase(Locale.ROOT).contains(departamento.toLowerCase(Locale.ROOT))) {
            String combinada = c + " (" + departamento.trim() + ")";
            if (combinada.length() <= 80) {
                return combinada;
            }
        }
        return c;
    }

    /** Platform provisioning context: the new tenant does not exist yet. Failures propagate. */
    private void establecerContextoProvisionamiento() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L)
                .organizationId(1L)
                .propertyId(1L)
                .roleCode("SUPERADMIN")
                .roleScope("GLOBAL")
                .build());
        jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
    }

    private void restaurarContexto(SaedContext prevCtx) {
        if (prevCtx != null && prevCtx.getUserId() != null) {
            SaedContextHolder.setContext(prevCtx);
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(" + prevCtx.getUserId() + "); "
                    + "PKG_SAED_SESSION.SET_CONTEXT(" + prevCtx.getUserId() + ", "
                    + (prevCtx.getOrganizationId() != null ? prevCtx.getOrganizationId() : "NULL") + ", "
                    + (prevCtx.getPropertyId() != null ? prevCtx.getPropertyId() : "NULL") + ", "
                    + (prevCtx.getRoleCode() != null ? "'" + prevCtx.getRoleCode().replace("'", "") + "'" : "NULL") + "); END;");
        } else {
            SaedContextHolder.clearContext();
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
        }
    }

    private String calcularFirmaIntegridad(String referencia, long montoCentavos) {
        try {
            String s = referencia + montoCentavos + "COP" + wompiIntegritySecret;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String toJson(OnboardingRegistroRequestDTO request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Registration data could not be serialized", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /** Normalized registration identity. */
    private record Registration(String nit, String email, String username, String document) {
        static Registration of(OnboardingRegistroRequestDTO request) {
            String nit = request.getIdentificacionFiscal() == null ? ""
                    : request.getIdentificacionFiscal().replaceAll("[^0-9A-Za-z-]", "").trim();
            String email = request.getEmailAdmin() == null ? "" : request.getEmailAdmin().toLowerCase(Locale.ROOT).trim();
            String raw = request.getAdminUsername();
            String username = !isBlank(raw) ? raw.trim().toLowerCase(Locale.ROOT)
                    : (email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
            String document = request.getNumeroDocumento() == null ? "" : request.getNumeroDocumento().trim();
            return new Registration(nit, email, username, document);
        }
    }
}
