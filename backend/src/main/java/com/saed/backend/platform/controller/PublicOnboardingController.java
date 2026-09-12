package com.saed.backend.platform.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.identity.service.TokenActivacionService;
import com.saed.backend.platform.dto.OnboardingRegistroRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Tag(name = "Public Onboarding", description = "Endpoints públicos para adquisición de planes SaaS SAED y registro de organizaciones")
@RestController
@RequestMapping("/api/v1/auth/onboarding")
public class PublicOnboardingController {

    private static final Logger log = LoggerFactory.getLogger(PublicOnboardingController.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TokenActivacionService tokenActivacionService;
    private final PasswordEncoder passwordEncoder;
    private final com.saed.backend.common.service.EmailService emailService;

    @Value("${wompi.public.key:pub_test_Q5yDA9xoKdePzhSGeVe9HAez7HgGObFG}")
    private String wompiPublicKey;

    @Value("${wompi.integrity.secret:stagtest_integrity_test_key_saed_2026}")
    private String wompiIntegritySecret;

    public PublicOnboardingController(
            NamedParameterJdbcTemplate jdbcTemplate,
            TokenActivacionService tokenActivacionService,
            PasswordEncoder passwordEncoder,
            com.saed.backend.common.service.EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenActivacionService = tokenActivacionService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Operation(summary = "Catálogo público de planes disponibles para suscripción")
    @GetMapping("/planes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listarPlanesPublicos() {
        String sql = """
            SELECT ID_PLAN AS "idPlan",
                   CODIGO AS "codigo",
                   NOMBRE AS "nombre",
                   DESCRIPCION AS "descripcion",
                   PRECIO_MENSUAL AS "precioMensual",
                   LIMITE_PROPIEDADES AS "limitePropiedades",
                   LIMITE_UNIDADES AS "limiteUnidades",
                   LIMITE_USUARIOS AS "limiteUsuarios",
                   LIMITE_ALMACENAMIENTO_GB AS "limiteAlmacenamientoGb"
            FROM PLANES
            WHERE ESTADO = 'ACTIVO'
            ORDER BY PRECIO_MENSUAL ASC
            """;
        List<Map<String, Object>> planes = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        return ResponseEntity.ok(ApiResponse.success(planes));
    }

    @Operation(summary = "Registro público de organización y suscripción a plan SaaS (Requisitos #18, #19, #20)")
    @PostMapping("/registro")
    @Transactional
    @Auditable(action = "ONBOARDING_REGISTER", resource = "ORGANIZACION", category = AuditCategory.ADMINISTRATIVE, severity = AuditSeverity.CRITICAL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> registrarOrganizacion(
            @Valid @RequestBody OnboardingRegistroRequestDTO request,
            HttpServletRequest httpRequest) {

        String cleanNit = request.getIdentificacionFiscal().replaceAll("[^0-9A-Za-z-]", "").trim();
        String adminEmail = request.getEmailAdmin().toLowerCase().trim();

        // 1. Establecer contexto de ejecución sistema
        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            SaedContextHolder.setContext(SaedContext.builder()
                    .userId(1L)
                    .organizationId(1L)
                    .propertyId(1L)
                    .roleCode("SUPERADMIN")
                    .roleScope("GLOBAL")
                    .build());
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); END;");
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
            } catch (Exception ignored) {}

            // 2. Validaciones de unicidad
            Integer existingOrg = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM ORGANIZACIONES WHERE IDENTIFICACION_FISCAL = :nit",
                    new MapSqlParameterSource("nit", cleanNit),
                    Integer.class
            );
            if (existingOrg != null && existingOrg > 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        "Ya existe una organización registrada con el NIT o identificación " + cleanNit));
            }

            Integer existingUser = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USUARIOS WHERE LOWER(EMAIL) = :email",
                    new MapSqlParameterSource("email", adminEmail),
                    Integer.class
            );
            if (existingUser != null && existingUser > 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        "El correo " + adminEmail + " ya se encuentra registrado para otro usuario"));
            }

            String rawUsername = request.getAdminUsername();
            String adminUsername = (rawUsername != null && !rawUsername.trim().isBlank())
                    ? rawUsername.trim().toLowerCase()
                    : (adminEmail.contains("@") ? adminEmail.substring(0, adminEmail.indexOf('@')).toLowerCase().trim() : adminEmail);

            Integer existingUserByUsername = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = :u",
                    new MapSqlParameterSource("u", adminUsername),
                    Integer.class
            );
            if (existingUserByUsername != null && existingUserByUsername > 0) {
                return ResponseEntity.badRequest().body(ApiResponse.error(
                        "El nombre de usuario '" + adminUsername + "' ya se encuentra en uso. Por favor elija otro."));
            }

            // 3. Consultar datos del plan
            List<Map<String, Object>> planRows = jdbcTemplate.queryForList(
                    "SELECT ID_PLAN, CODIGO, NOMBRE, PRECIO_MENSUAL FROM PLANES WHERE ID_PLAN = :p AND ESTADO = 'ACTIVO'",
                    new MapSqlParameterSource("p", request.getIdPlan())
            );
            if (planRows.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("El plan seleccionado no existe o no está activo"));
            }

            Map<String, Object> plan = planRows.get(0);
            Number precioNum = (Number) plan.get("PRECIO_MENSUAL");
            long precioMensual = precioNum != null ? precioNum.longValue() : 0L;
            boolean esGratisOPrueba = request.getEsPrueba() || precioMensual == 0;

            String orgEstado = esGratisOPrueba ? "ACTIVA" : "INACTIVA";

            // 4. Crear ORGANIZACIONES
            String insertOrgSql = """
                INSERT INTO ORGANIZACIONES (NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, TELEFONO_CONTACTO, DIRECCION, CIUDAD, PAIS, ESTADO)
                VALUES (:nom, :nit, :email, :tel, :dir, :ciu, 'Colombia', :est)
                """;
            MapSqlParameterSource orgParams = new MapSqlParameterSource()
                    .addValue("nom", request.getNombreOrganizacion().trim())
                    .addValue("nit", cleanNit)
                    .addValue("email", request.getEmailOrganizacion().trim())
                    .addValue("tel", request.getTelefonoOrganizacion())
                    .addValue("dir", request.getDireccion())
                    .addValue("ciu", request.getCiudad() != null ? request.getCiudad() : "Bogotá")
                    .addValue("est", orgEstado);

            KeyHolder khOrg = new GeneratedKeyHolder();
            jdbcTemplate.update(insertOrgSql, orgParams, khOrg, new String[]{"ID_ORGANIZACION"});
            Number orgIdNum = khOrg.getKey();
            if (orgIdNum == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("No se pudo generar ID para la organización"));
            }
            Long idOrganizacion = orgIdNum.longValue();

            // 5. Crear PERSONA para el administrador
            String insertPerSql = """
                INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA, PRIMER_NOMBRE, PRIMER_APELLIDO, EMAIL, TELEFONO, ESTADO)
                VALUES (1, :doc, 'NATURAL', :nom, :ape, :email, :tel, 'ACTIVO')
                """;
            MapSqlParameterSource perParams = new MapSqlParameterSource()
                    .addValue("doc", request.getNumeroDocumento().trim())
                    .addValue("nom", request.getPrimerNombre().trim())
                    .addValue("ape", request.getPrimerApellido().trim())
                    .addValue("email", adminEmail)
                    .addValue("tel", request.getTelefonoAdmin());

            KeyHolder khPer = new GeneratedKeyHolder();
            jdbcTemplate.update(insertPerSql, perParams, khPer, new String[]{"ID_PERSONA"});
            Number perIdNum = khPer.getKey();
            Long idPersona = perIdNum != null ? perIdNum.longValue() : 1L;

            // 6. Crear USUARIO con estado inicial
            String userEstado = esGratisOPrueba ? "ACTIVO" : "PENDIENTE_VERIFICACION";
            String randomPass = com.saed.backend.common.util.PasswordGenerator.generate();
            String insertUsrSql = """
                INSERT INTO USUARIOS (ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS)
                VALUES (:idPer, :user, :email, :pwd, :est, 0)
                """;
            MapSqlParameterSource usrParams = new MapSqlParameterSource()
                    .addValue("idPer", idPersona)
                    .addValue("user", adminUsername)
                    .addValue("email", adminEmail)
                    .addValue("pwd", passwordEncoder.encode(randomPass))
                    .addValue("est", userEstado);

            KeyHolder khUsr = new GeneratedKeyHolder();
            jdbcTemplate.update(insertUsrSql, usrParams, khUsr, new String[]{"ID_USUARIO"});
            Number usrIdNum = khUsr.getKey();
            Long idUsuario = usrIdNum != null ? usrIdNum.longValue() : 1L;

            // 7. Crear Asignación de rol ADMIN_ORGANIZACION (id_rol = 2)
            Long idRolAdminOrg = 2L;
            try {
                List<Long> rolIds = jdbcTemplate.query(
                        "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'",
                        (rs, rowNum) -> rs.getLong("ID_ROL")
                );
                if (!rolIds.isEmpty()) idRolAdminOrg = rolIds.get(0);
            } catch (Exception ignored) {}

            String insertAsigSql = """
                INSERT INTO USUARIO_ASIGNACIONES (ID_USUARIO, ID_ROL, ID_ORGANIZACION, ESTADO)
                VALUES (:usr, :rol, :org, 'ACTIVA')
                """;
            jdbcTemplate.update(insertAsigSql, new MapSqlParameterSource()
                    .addValue("usr", idUsuario)
                    .addValue("rol", idRolAdminOrg)
                    .addValue("org", idOrganizacion)
            );

            String ip = httpRequest != null ? httpRequest.getRemoteAddr() : "0.0.0.0";

            // 8. Crear Membresía
            if (esGratisOPrueba) {
                // Periodo de prueba o plan gratuito: Activación inmediata
                String insertMembSql = """
                    INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA, DIAS_PRUEBA)
                    VALUES (:org, :plan, TRUNC(SYSDATE), ADD_MONTHS(TRUNC(SYSDATE), 1), 'PRUEBA', 'S', 14)
                    """;
                KeyHolder khMemb = new GeneratedKeyHolder();
                jdbcTemplate.update(insertMembSql, new MapSqlParameterSource()
                        .addValue("org", idOrganizacion)
                        .addValue("plan", request.getIdPlan()),
                        khMemb, new String[]{"ID_MEMBRESIA"});

                // Despachar correo de bienvenida con credenciales automáticas
                String adminFullName = (request.getPrimerNombre().trim() + " " + request.getPrimerApellido().trim()).trim();
                String planNombre = (String) plan.get("NOMBRE");
                try {
                    emailService.enviarBienvenidaCredencialesAsync(
                            adminEmail,
                            adminFullName,
                            request.getNombreOrganizacion().trim(),
                            planNombre,
                            "ADMIN_ORGANIZACION",
                            adminUsername,
                            randomPass,
                            "https://saedfront.vercel.app/login"
                    );
                } catch (Exception e) {
                    log.error("Error despachando credenciales de bienvenida: ", e);
                }

                Map<String, Object> resp = new HashMap<>();
                resp.put("idOrganizacion", idOrganizacion);
                resp.put("requierePago", false);
                resp.put("esPrueba", true);
                resp.put("email", adminEmail);
                resp.put("adminUsername", adminUsername);
                resp.put("mensaje", "¡Bienvenido a SAED 2.0! Tu suscripción ha sido activada. Hemos enviado tus credenciales de acceso (usuario: " + adminUsername + " y contraseña temporal) al correo " + adminEmail + ".");

                return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
            } else {
                // Plan comercial: Crear intención de pago con Wompi
                String insertMembSql = """
                    INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
                    VALUES (:org, :plan, TRUNC(SYSDATE), ADD_MONTHS(TRUNC(SYSDATE), 12), 'SUSPENDIDA', 'N')
                    """;
                KeyHolder khMemb = new GeneratedKeyHolder();
                jdbcTemplate.update(insertMembSql, new MapSqlParameterSource()
                        .addValue("org", idOrganizacion)
                        .addValue("plan", request.getIdPlan()),
                        khMemb, new String[]{"ID_MEMBRESIA"});

                // Calcular monto anual con 20% descuento si aplica
                boolean esAnual = "ANUAL".equalsIgnoreCase(request.getCicloFacturacion());
                long montoPesos = esAnual ? Math.round(precioMensual * 12 * 0.80) : precioMensual;
                long montoCentavos = montoPesos * 100;

                String referencia = "SAED-MEMBRESIA-" + idOrganizacion + "-" + System.currentTimeMillis();
                String firmaIntegridad = calcularFirmaIntegridad(referencia, montoCentavos);

                // Registrar en TRANSACCIONES_PAGO
                Long unidadFallback = 1L;
                try {
                    List<Long> uIds = jdbcTemplate.queryForList("SELECT MIN(ID_UNIDAD) FROM UNIDADES", new MapSqlParameterSource(), Long.class);
                    if (!uIds.isEmpty() && uIds.get(0) != null) {
                        unidadFallback = uIds.get(0);
                    }
                } catch (Exception ignored) {}

                String insertTxSql = """
                    INSERT INTO TRANSACCIONES_PAGO (ID_UNIDAD, ID_PAGO, PASARELA, ID_TRANSACCION_PASARELA, REFERENCIA_INTERNA, MONTO_CENTAVOS, MONEDA, ESTADO_PASARELA, METODO_ORIGEN, FIRMA_CHECKSUM)
                    VALUES (:u, NULL, 'WOMPI', :ref, :ref, :mc, 'COP', 'PENDIENTE', 'MEMBRESIA', :firma)
                    """;
                jdbcTemplate.update(insertTxSql, new MapSqlParameterSource()
                        .addValue("u", unidadFallback)
                        .addValue("ref", referencia)
                        .addValue("mc", montoCentavos)
                        .addValue("firma", firmaIntegridad)
                );

                Map<String, Object> resp = new HashMap<>();
                resp.put("idOrganizacion", idOrganizacion);
                resp.put("requierePago", true);
                resp.put("esPrueba", false);
                resp.put("referencia", referencia);
                resp.put("montoCentavos", montoCentavos);
                resp.put("montoPesos", montoPesos);
                resp.put("moneda", "COP");
                resp.put("wompiPublicKey", getPublicKey());
                resp.put("firmaIntegridad", firmaIntegridad);
                resp.put("adminEmail", adminEmail);
                resp.put("mensaje", "Organización creada en estado pendiente. Procede al pago seguro de tu membresía con Wompi.");

                return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resp));
            }

        } finally {
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
            } catch (Exception ignored) {}
            SaedContextHolder.setContext(prevCtx);
        }
    }

    @Operation(summary = "Consultar estado de transacción de membresía")
    @GetMapping("/estado-pago")
    public ResponseEntity<ApiResponse<Map<String, Object>>> consultarEstadoPago(@RequestParam String referencia) {
        String sql = """
            SELECT ESTADO_PASARELA, MONTO_CENTAVOS, REFERENCIA_INTERNA, FECHA_REGISTRO
            FROM TRANSACCIONES_PAGO
            WHERE REFERENCIA_INTERNA = :ref
            """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("ref", referencia));
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Referencia de pago no encontrada"));
        }
        Map<String, Object> tx = rows.get(0);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "referencia", tx.get("REFERENCIA_INTERNA"),
                "estado", tx.get("ESTADO_PASARELA"),
                "montoCentavos", tx.get("MONTO_CENTAVOS")
        )));
    }

    private String getPublicKey() {
        String env = System.getenv("WOMPI_PUBLIC_KEY");
        return (env != null && !env.isBlank()) ? env : wompiPublicKey;
    }

    private String getIntegritySecret() {
        String env = System.getenv("WOMPI_INTEGRITY_SECRET");
        return (env != null && !env.isBlank()) ? env : wompiIntegritySecret;
    }

    private String calcularFirmaIntegridad(String referencia, long montoCentavos) {
        try {
            String s = referencia + montoCentavos + "COP" + getIntegritySecret();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Error calculando firma de integridad Wompi: ", e);
            return "";
        }
    }
}
