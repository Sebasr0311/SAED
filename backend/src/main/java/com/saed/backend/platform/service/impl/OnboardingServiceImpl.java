package com.saed.backend.platform.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.common.util.PasswordGenerator;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.dto.OnboardingRegistroRequestDTO;
import com.saed.backend.platform.service.OnboardingService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.LocalDate;
import java.util.*;

@Service
public class OnboardingServiceImpl implements OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingServiceImpl.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${wompi.public.key:${WOMPI_PUBLIC_KEY:pub_test_IZg6dmwtip4WYXjPP8G7zYvWzCU5wRaH}}")
    private String wompiPublicKey;

    @Value("${wompi.integrity.secret:${WOMPI_INTEGRITY_SECRET:test_integrity_CT3taBwOqVtSQITYMGUTcrJftHdoLoPQ}}")
    private String wompiIntegritySecret;

    public OnboardingServiceImpl(
            NamedParameterJdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        inicializarEsquemaStaging();
        purgarRegistrosFalsos();
    }

    private void inicializarEsquemaStaging() {
        try {
            jdbcTemplate.getJdbcOperations().execute("""
                CREATE TABLE ONBOARDING_INTENCIONES (
                    REFERENCIA VARCHAR(100) PRIMARY KEY,
                    DATOS_REGISTRO CLOB NOT NULL,
                    MONTO_CENTAVOS NUMERIC(14,0) NOT NULL,
                    ESTADO VARCHAR(30) DEFAULT 'PENDIENTE' NOT NULL,
                    FECHA_CREACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                    FECHA_ACTUALIZACION TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
            """);
            log.info("[Onboarding] Tabla ONBOARDING_INTENCIONES creada exitosamente.");
        } catch (Exception e) {
            // Ignorar error si la tabla ya existe (ORA-00955 o H2 already exists)
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (!msg.contains("ora-00955") && !msg.contains("already exists")) {
                log.warn("[Onboarding] Aviso al inicializar ONBOARDING_INTENCIONES: {}", e.getMessage());
            }
        }
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
                   LIMITE_ALMACENAMIENTO_GB AS "limiteAlmacenamientoGb"
            FROM PLANES
            WHERE ESTADO = 'ACTIVO'
            ORDER BY PRECIO_MENSUAL ASC
            """;
        return jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
    }

    @Override
    @Transactional
    public Map<String, Object> registrar(OnboardingRegistroRequestDTO request) {
        String cleanNit = request.getIdentificacionFiscal().replaceAll("[^0-9A-Za-z-]", "").trim();
        String adminEmail = request.getEmailAdmin().toLowerCase().trim();
        String rawUsername = request.getAdminUsername();
        String adminUsername = (rawUsername != null && !rawUsername.trim().isBlank())
                ? rawUsername.trim().toLowerCase()
                : (adminEmail.contains("@") ? adminEmail.substring(0, adminEmail.indexOf('@')).toLowerCase().trim() : adminEmail);
        String cleanDoc = (request.getNumeroDocumento() != null) ? request.getNumeroDocumento().trim() : "";

        if (cleanDoc.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El número de documento del administrador es obligatorio");
        }

        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            establecerContextoAdminGlobal();

            // 1. Purga de intentos previos incompletos (borradores pendientes de pago) para liberar credenciales
            purgarIntentoPrevioIncompleto(adminEmail, cleanNit, cleanDoc);

            // 2. Validaciones de unicidad contra entidades ACTIVAS
            Integer existingActiveOrg = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM ORGANIZACIONES WHERE IDENTIFICACION_FISCAL = :nit AND ESTADO != 'INACTIVA'",
                    new MapSqlParameterSource("nit", cleanNit),
                    Integer.class
            );
            if (existingActiveOrg != null && existingActiveOrg > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ya existe una organización activa registrada con el NIT o identificación " + cleanNit);
            }

            Integer existingActiveUser = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USUARIOS WHERE LOWER(EMAIL) = :email AND ESTADO != 'PENDIENTE_VERIFICACION'",
                    new MapSqlParameterSource("email", adminEmail),
                    Integer.class
            );
            if (existingActiveUser != null && existingActiveUser > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El correo " + adminEmail + " ya se encuentra registrado para otro usuario activo");
            }

            Integer existingActiveUsername = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = :u AND ESTADO != 'PENDIENTE_VERIFICACION'",
                    new MapSqlParameterSource("u", adminUsername),
                    Integer.class
            );
            if (existingActiveUsername != null && existingActiveUsername > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El nombre de usuario '" + adminUsername + "' ya se encuentra en uso por un usuario activo.");
            }

            Long idTipoDoc = resolverIdTipoDocumento(request.getTipoDocumento());

            List<Map<String, Object>> existingPersonaRows = jdbcTemplate.queryForList("""
                SELECT p.ID_PERSONA, u.ID_USUARIO, u.NOMBRE_USUARIO, u.ESTADO AS USER_ESTADO
                FROM PERSONAS p
                LEFT JOIN USUARIOS u ON u.ID_PERSONA = p.ID_PERSONA
                WHERE p.NUMERO_DOCUMENTO = :doc AND (p.ID_TIPO_DOCUMENTO = :tipoDoc OR :tipoDoc IS NULL)
                """,
                new MapSqlParameterSource().addValue("doc", cleanDoc).addValue("tipoDoc", idTipoDoc)
            );

            Long idPersonaExistente = null;
            if (!existingPersonaRows.isEmpty()) {
                Map<String, Object> firstRow = existingPersonaRows.get(0);
                Object existingUserId = firstRow.get("ID_USUARIO");
                String userEstado = (String) firstRow.get("USER_ESTADO");
                if (existingUserId != null && !"PENDIENTE_VERIFICACION".equals(userEstado)) {
                    String uName = (String) firstRow.get("NOMBRE_USUARIO");
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "El número de documento " + cleanDoc + " ya se encuentra vinculado a la cuenta de usuario activa '" +
                            (uName != null ? uName : "existente") + "'.");
                }
                idPersonaExistente = ((Number) firstRow.get("ID_PERSONA")).longValue();
            }

            // 3. Consultar datos del plan
            List<Map<String, Object>> planRows = jdbcTemplate.queryForList(
                    "SELECT ID_PLAN, CODIGO, NOMBRE, PRECIO_MENSUAL FROM PLANES WHERE ID_PLAN = :p AND ESTADO = 'ACTIVO'",
                    new MapSqlParameterSource("p", request.getIdPlan())
            );
            if (planRows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El plan seleccionado no existe o no está activo");
            }

            Map<String, Object> plan = planRows.get(0);
            Number precioNum = (Number) plan.get("PRECIO_MENSUAL");
            long precioMensual = precioNum != null ? precioNum.longValue() : 0L;
            boolean esGratisOPrueba = Boolean.TRUE.equals(request.getEsPrueba()) || precioMensual == 0;

            // 4. FLUJO PLAN GRATIS / PRUEBA: Aprovisionamiento Inmediato (sin pasarela)
            if (esGratisOPrueba) {
                return aprovisionarOrganizacionGratis(request, cleanNit, adminEmail, adminUsername, cleanDoc, idTipoDoc, idPersonaExistente, plan);
            }

            // 5. FLUJO PLAN COMERCIAL: Patrón Staging (No se tocan tablas maestras hasta el pago aprobado)
            boolean esAnual = "ANUAL".equalsIgnoreCase(request.getCicloFacturacion());
            long montoPesos = esAnual ? Math.round(precioMensual * 12 * 0.80) : precioMensual;
            long montoCentavos = montoPesos * 100;

            String referencia = "SAED-MEMBRESIA-ONB-" + System.currentTimeMillis();
            String firmaIntegridad = calcularFirmaIntegridad(referencia, montoCentavos);

            // Guardar intención en tabla staging
            try {
                String jsonDatos = objectMapper.writeValueAsString(request);
                jdbcTemplate.update("""
                    INSERT INTO ONBOARDING_INTENCIONES (REFERENCIA, DATOS_REGISTRO, MONTO_CENTAVOS, ESTADO, FECHA_CREACION, FECHA_ACTUALIZACION)
                    VALUES (:ref, :datos, :monto, 'PENDIENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    new MapSqlParameterSource()
                        .addValue("ref", referencia)
                        .addValue("datos", jsonDatos)
                        .addValue("monto", montoCentavos)
                );
            } catch (Exception e) {
                log.error("[Onboarding] Error serializando datos de intención de registro: ", e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo registrar la intención de suscripción");
            }

            // Registrar transacción en TRANSACCIONES_PAGO para Wompi
            Long unidadFallback = 1L;
            try {
                List<Long> uIds = jdbcTemplate.queryForList("SELECT MIN(ID_UNIDAD) FROM UNIDADES", new MapSqlParameterSource(), Long.class);
                if (!uIds.isEmpty() && uIds.get(0) != null) {
                    unidadFallback = uIds.get(0);
                }
            } catch (Exception ignored) {}

            String insertTxSql = """
                INSERT INTO TRANSACCIONES_PAGO (ID_UNIDAD, ID_PAGO, PASARELA, ID_TRANSACCION_PASARELA, REFERENCIA_INTERNA, MONTO_CENTAVOS, MONEDA, ESTADO_PASARELA, METODO_ORIGEN, FIRMA_CHECKSUM)
                VALUES (:u, NULL, 'WOMPI', :ref, :ref, :mc, 'COP', 'PENDIENTE', 'ONBOARDING', :firma)
                """;
            jdbcTemplate.update(insertTxSql, new MapSqlParameterSource()
                    .addValue("u", unidadFallback)
                    .addValue("ref", referencia)
                    .addValue("mc", montoCentavos)
                    .addValue("firma", firmaIntegridad)
            );

            Map<String, Object> resp = new HashMap<>();
            resp.put("idOrganizacion", null);
            resp.put("requierePago", true);
            resp.put("esPrueba", false);
            resp.put("referencia", referencia);
            resp.put("montoCentavos", montoCentavos);
            resp.put("montoPesos", montoPesos);
            resp.put("moneda", "COP");
            resp.put("wompiPublicKey", getPublicKey());
            resp.put("firmaIntegridad", firmaIntegridad);
            resp.put("adminEmail", adminEmail);
            resp.put("mensaje", "Intención de registro generada exitosamente. Procede al pago seguro de tu membresía con Wompi.");

            return resp;

        } finally {
            limpiarContexto(prevCtx);
        }
    }

    private Map<String, Object> aprovisionarOrganizacionGratis(
            OnboardingRegistroRequestDTO request,
            String cleanNit,
            String adminEmail,
            String adminUsername,
            String cleanDoc,
            Long idTipoDoc,
            Long idPersonaExistente,
            Map<String, Object> plan) {

        String ciudadFinal = resolverCiudad(request.getCiudad(), request.getDepartamento());

        // Insertar ORGANIZACIONES
        String insertOrgSql = """
            INSERT INTO ORGANIZACIONES (NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, TELEFONO_CONTACTO, DIRECCION, CIUDAD, PAIS, ESTADO)
            VALUES (:nom, :nit, :email, :tel, :dir, :ciu, 'Colombia', 'ACTIVA')
            """;
        KeyHolder khOrg = new GeneratedKeyHolder();
        jdbcTemplate.update(insertOrgSql, new MapSqlParameterSource()
                .addValue("nom", request.getNombreOrganizacion().trim())
                .addValue("nit", cleanNit)
                .addValue("email", request.getEmailOrganizacion().trim())
                .addValue("tel", request.getTelefonoOrganizacion())
                .addValue("dir", request.getDireccion())
                .addValue("ciu", ciudadFinal),
                khOrg, new String[]{"ID_ORGANIZACION"});

        Number orgIdNum = khOrg.getKey();
        if (orgIdNum == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar ID para la organización");
        }
        Long idOrganizacion = orgIdNum.longValue();

        // Insertar/actualizar PERSONAS
        Long idPersona = guardarOActualizarPersona(request, cleanDoc, idTipoDoc, adminEmail, idPersonaExistente);

        // Insertar USUARIOS
        String randomPass = PasswordGenerator.generate();
        String insertUsrSql = """
            INSERT INTO USUARIOS (ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS)
            VALUES (:idPer, :user, :email, :pwd, 'ACTIVO', 0)
            """;
        KeyHolder khUsr = new GeneratedKeyHolder();
        jdbcTemplate.update(insertUsrSql, new MapSqlParameterSource()
                .addValue("idPer", idPersona)
                .addValue("user", adminUsername)
                .addValue("email", adminEmail)
                .addValue("pwd", passwordEncoder.encode(randomPass)),
                khUsr, new String[]{"ID_USUARIO"});
        Number usrIdNum = khUsr.getKey();
        Long idUsuario = usrIdNum != null ? usrIdNum.longValue() : 1L;

        // Asignar rol ADMIN_ORGANIZACION
        Long idRolAdminOrg = resolverIdRolAdminOrganizacion();
        jdbcTemplate.update("""
            INSERT INTO USUARIO_ASIGNACIONES (ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO)
            VALUES (:usr, :rol, :org, NULL, NULL, 'ACTIVA')
            """,
            new MapSqlParameterSource()
                .addValue("usr", idUsuario)
                .addValue("rol", idRolAdminOrg)
                .addValue("org", idOrganizacion)
        );

        // Crear Membresía ACTIVA
        jdbcTemplate.update("""
            INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
            VALUES (:org, :plan, TRUNC(SYSDATE), ADD_MONTHS(TRUNC(SYSDATE), 1), 'ACTIVA', 'S')
            """,
            new MapSqlParameterSource()
                .addValue("org", idOrganizacion)
                .addValue("plan", request.getIdPlan())
        );

        // Despachar correo de bienvenida con credenciales
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
            log.error("[Onboarding] Error despachando credenciales de bienvenida: ", e);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("idOrganizacion", idOrganizacion);
        resp.put("requierePago", false);
        resp.put("esPrueba", true);
        resp.put("email", adminEmail);
        resp.put("adminUsername", adminUsername);
        resp.put("mensaje", "¡Bienvenido a SAED 2.0! Tu período de prueba ha sido activado. Hemos enviado tus credenciales de acceso al correo " + adminEmail + ".");

        return resp;
    }

    @Override
    @Transactional
    public boolean materializarOrganizacion(String referencia, Long expectedCentavos, String idTransaccionPasarela) {
        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            establecerContextoAdminGlobal();

            // 1. Verificar intención en staging de forma atómica (idempotencia)
            List<Map<String, Object>> intencionList = jdbcTemplate.queryForList(
                    "SELECT REFERENCIA, DATOS_REGISTRO, MONTO_CENTAVOS, ESTADO FROM ONBOARDING_INTENCIONES WHERE REFERENCIA = :ref",
                    new MapSqlParameterSource("ref", referencia)
            );

            if (intencionList.isEmpty()) {
                log.warn("[Onboarding] No se encontró intención de onboarding con referencia: {}", referencia);
                return false;
            }

            Map<String, Object> intencion = intencionList.get(0);
            String estadoIntencion = (String) intencion.get("ESTADO");
            if ("COMPLETADA".equalsIgnoreCase(estadoIntencion)) {
                log.info("[Onboarding] La intención {} ya fue materializada previamente.", referencia);
                return true;
            }

            // Marcar como completada para evitar condiciones de carrera (idempotencia atómica)
            int updated = jdbcTemplate.update(
                    "UPDATE ONBOARDING_INTENCIONES SET ESTADO = 'COMPLETADA', FECHA_ACTUALIZACION = CURRENT_TIMESTAMP WHERE REFERENCIA = :ref AND ESTADO = 'PENDIENTE'",
                    new MapSqlParameterSource("ref", referencia)
            );
            if (updated == 0) {
                log.info("[Onboarding] Intención {} actualizada por otro hilo concurrentemente.", referencia);
                return true;
            }

            String datosJson = (String) intencion.get("DATOS_REGISTRO");
            OnboardingRegistroRequestDTO request;
            try {
                request = objectMapper.readValue(datosJson, OnboardingRegistroRequestDTO.class);
            } catch (Exception e) {
                log.error("[Onboarding] Error deserializando datos de registro para referencia {}: ", referencia, e);
                return false;
            }

            String cleanNit = request.getIdentificacionFiscal().replaceAll("[^0-9A-Za-z-]", "").trim();
            String adminEmail = request.getEmailAdmin().toLowerCase().trim();
            String rawUsername = request.getAdminUsername();
            String adminUsername = (rawUsername != null && !rawUsername.trim().isBlank())
                    ? rawUsername.trim().toLowerCase()
                    : (adminEmail.contains("@") ? adminEmail.substring(0, adminEmail.indexOf('@')).toLowerCase().trim() : adminEmail);
            String cleanDoc = (request.getNumeroDocumento() != null) ? request.getNumeroDocumento().trim() : "";
            Long idTipoDoc = resolverIdTipoDocumento(request.getTipoDocumento());

            // 2. Limpiar borradores previos residuales antes de crear
            purgarIntentoPrevioIncompleto(adminEmail, cleanNit, cleanDoc);

            // 3. Crear ORGANIZACIONES (ACTIVA)
            String ciudadFinal = resolverCiudad(request.getCiudad(), request.getDepartamento());
            String insertOrgSql = """
                INSERT INTO ORGANIZACIONES (NOMBRE, IDENTIFICACION_FISCAL, EMAIL_CONTACTO, TELEFONO_CONTACTO, DIRECCION, CIUDAD, PAIS, ESTADO)
                VALUES (:nom, :nit, :email, :tel, :dir, :ciu, 'Colombia', 'ACTIVA')
                """;
            KeyHolder khOrg = new GeneratedKeyHolder();
            jdbcTemplate.update(insertOrgSql, new MapSqlParameterSource()
                    .addValue("nom", request.getNombreOrganizacion().trim())
                    .addValue("nit", cleanNit)
                    .addValue("email", request.getEmailOrganizacion().trim())
                    .addValue("tel", request.getTelefonoOrganizacion())
                    .addValue("dir", request.getDireccion())
                    .addValue("ciu", ciudadFinal),
                    khOrg, new String[]{"ID_ORGANIZACION"});
            Number orgIdNum = khOrg.getKey();
            Long idOrganizacion = orgIdNum != null ? orgIdNum.longValue() : 1L;

            // 4. Crear PERSONAS
            Long idPersona = guardarOActualizarPersona(request, cleanDoc, idTipoDoc, adminEmail, null);

            // 5. Crear USUARIOS (ACTIVO)
            String autoPass = PasswordGenerator.generate();
            String insertUsrSql = """
                INSERT INTO USUARIOS (ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS)
                VALUES (:idPer, :user, :email, :pwd, 'ACTIVO', 0)
                """;
            KeyHolder khUsr = new GeneratedKeyHolder();
            jdbcTemplate.update(insertUsrSql, new MapSqlParameterSource()
                    .addValue("idPer", idPersona)
                    .addValue("user", adminUsername)
                    .addValue("email", adminEmail)
                    .addValue("pwd", passwordEncoder.encode(autoPass)),
                    khUsr, new String[]{"ID_USUARIO"});
            Number usrIdNum = khUsr.getKey();
            Long idUsuario = usrIdNum != null ? usrIdNum.longValue() : 1L;

            // 6. Asignar rol ADMIN_ORGANIZACION
            Long idRolAdminOrg = resolverIdRolAdminOrganizacion();
            jdbcTemplate.update("""
                INSERT INTO USUARIO_ASIGNACIONES (ID_USUARIO, ID_ROL, ID_ORGANIZACION, ID_PROPIEDAD, ID_UNIDAD, ESTADO)
                VALUES (:usr, :rol, :org, NULL, NULL, 'ACTIVA')
                """,
                new MapSqlParameterSource()
                    .addValue("usr", idUsuario)
                    .addValue("rol", idRolAdminOrg)
                    .addValue("org", idOrganizacion)
            );

            // 7. Crear Membresía ACTIVA (12 meses si es anual, 1 mes si mensual)
            boolean esAnual = "ANUAL".equalsIgnoreCase(request.getCicloFacturacion());
            int meses = esAnual ? 12 : 1;
            jdbcTemplate.update("""
                INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, FECHA_INICIO, FECHA_FIN, ESTADO, ES_PRUEBA)
                VALUES (:org, :plan, TRUNC(SYSDATE), ADD_MONTHS(TRUNC(SYSDATE), :m), 'ACTIVA', 'N')
                """,
                new MapSqlParameterSource()
                    .addValue("org", idOrganizacion)
                    .addValue("plan", request.getIdPlan())
                    .addValue("m", meses)
            );

            // 8. Actualizar TRANSACCIONES_PAGO
            jdbcTemplate.update("""
                UPDATE TRANSACCIONES_PAGO
                SET ESTADO_PASARELA = 'APROBADO',
                    ID_TRANSACCION_PASARELA = COALESCE(:wompi, ID_TRANSACCION_PASARELA)
                WHERE REFERENCIA_INTERNA = :ref
                """,
                new MapSqlParameterSource()
                    .addValue("ref", referencia)
                    .addValue("wompi", idTransaccionPasarela != null ? idTransaccionPasarela : referencia)
            );

            // 9. Despachar correos
            String nombreAdmin = (request.getPrimerNombre().trim() + " " + request.getPrimerApellido().trim()).trim();
            String planNombre = "Plan Comercial";
            try {
                List<String> pList = jdbcTemplate.queryForList("SELECT NOMBRE FROM PLANES WHERE ID_PLAN = :p",
                        new MapSqlParameterSource("p", request.getIdPlan()), String.class);
                if (!pList.isEmpty()) planNombre = pList.get(0);
            } catch (Exception ignored) {}

            try {
                emailService.enviarBienvenidaCredenciales(
                        adminEmail,
                        nombreAdmin,
                        request.getNombreOrganizacion().trim(),
                        planNombre,
                        "ADMIN_ORGANIZACION",
                        adminUsername,
                        autoPass,
                        "https://saedfront.vercel.app/login"
                );
            } catch (Exception e) {
                log.warn("[Onboarding] Error enviando credenciales tras pago a {}: {}", adminEmail, e.getMessage());
            }

            try {
                BigDecimal montoPesos = expectedCentavos != null
                        ? new BigDecimal(expectedCentavos).divide(new BigDecimal(100))
                        : BigDecimal.ZERO;
                emailService.enviarReciboPago(adminEmail, "MEMBRESIA_SAED", montoPesos, referencia, LocalDate.now().toString());
            } catch (Exception e) {
                log.warn("[Onboarding] Error enviando comprobante tras pago a {}: {}", adminEmail, e.getMessage());
            }

            log.info("[Onboarding] ¡Organización {} y usuario {} materializados exitosamente tras pago confirmado!",
                    idOrganizacion, adminUsername);
            return true;

        } finally {
            limpiarContexto(prevCtx);
        }
    }

    @Override
    public Map<String, Object> consultarEstadoPago(String referencia) {
        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            establecerContextoAdminGlobal();

            String sql = """
                SELECT ESTADO_PASARELA, MONTO_CENTAVOS, REFERENCIA_INTERNA, FECHA_REGISTRO
                FROM TRANSACCIONES_PAGO
                WHERE REFERENCIA_INTERNA = :ref
                """;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource("ref", referencia));
            if (rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Referencia de pago no encontrada");
            }
            Map<String, Object> tx = rows.get(0);
            String estadoPasarela = (String) tx.get("ESTADO_PASARELA");

            // Si está aprobada, verificar si la intención ya fue materializada
            String estadoOrg = null;
            if ("APROBADO".equalsIgnoreCase(estadoPasarela)) {
                estadoOrg = "ACTIVA";
                materializarOrganizacion(referencia, ((Number) tx.get("MONTO_CENTAVOS")).longValue(), null);
            } else {
                // Verificar si en ONBOARDING_INTENCIONES ya está COMPLETADA
                List<String> stgList = jdbcTemplate.queryForList(
                        "SELECT ESTADO FROM ONBOARDING_INTENCIONES WHERE REFERENCIA = :ref",
                        new MapSqlParameterSource("ref", referencia),
                        String.class
                );
                if (!stgList.isEmpty() && "COMPLETADA".equalsIgnoreCase(stgList.get(0))) {
                    estadoPasarela = "APROBADO";
                    estadoOrg = "ACTIVA";
                }
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("referencia", tx.get("REFERENCIA_INTERNA"));
            resp.put("estado", estadoPasarela);
            resp.put("estadoPasarela", estadoPasarela);
            resp.put("montoCentavos", tx.get("MONTO_CENTAVOS"));
            resp.put("estadoOrganizacion", estadoOrg);

            return resp;

        } finally {
            limpiarContexto(prevCtx);
        }
    }

    @Override
    public int purgarRegistrosFalsos() {
        SaedContext prevCtx = SaedContextHolder.getContext();
        int totalPurgados = 0;
        try {
            establecerContextoAdminGlobal();

            // Buscar organizaciones inactivas huérfanas creadas por onboarding sin pagos aprobados
            List<Long> orgsInactivas = jdbcTemplate.queryForList("""
                SELECT o.ID_ORGANIZACION
                FROM ORGANIZACIONES o
                WHERE o.ID_ORGANIZACION > 1
                  AND (o.ESTADO = 'INACTIVA' OR o.ID_ORGANIZACION IN (
                      SELECT ua.ID_ORGANIZACION FROM USUARIO_ASIGNACIONES ua
                      JOIN USUARIOS u ON u.ID_USUARIO = ua.ID_USUARIO
                      WHERE LOWER(u.EMAIL) IN ('sebasrusso95@gmail.com', 'sebasthompson95@gmail.com')
                         OR u.ESTADO = 'PENDIENTE_VERIFICACION'
                  ))
                  AND NOT EXISTS (
                      SELECT 1 FROM MEMBRESIAS m
                      WHERE m.ID_ORGANIZACION = o.ID_ORGANIZACION AND m.ESTADO = 'ACTIVA'
                  )
                """, new MapSqlParameterSource(), Long.class);

            for (Long idOrg : orgsInactivas) {
                purgarEstructuraOrganizacionInactiva(idOrg);
                totalPurgados++;
            }

            // Buscar usuarios residuales en PENDIENTE_VERIFICACION o correos específicos de pruebas
            List<Map<String, Object>> usuariosResiduales = jdbcTemplate.queryForList("""
                SELECT u.ID_USUARIO, u.ID_PERSONA, u.EMAIL
                FROM USUARIOS u
                WHERE u.ID_USUARIO > 1
                  AND (u.ESTADO = 'PENDIENTE_VERIFICACION'
                       OR LOWER(u.EMAIL) IN ('sebasrusso95@gmail.com', 'sebasthompson95@gmail.com'))
                  AND NOT EXISTS (
                      SELECT 1 FROM USUARIO_ASIGNACIONES ua
                      JOIN ORGANIZACIONES o ON o.ID_ORGANIZACION = ua.ID_ORGANIZACION
                      WHERE ua.ID_USUARIO = u.ID_USUARIO AND o.ESTADO = 'ACTIVA'
                  )
                """, new MapSqlParameterSource());

            for (Map<String, Object> uRow : usuariosResiduales) {
                Long idUsr = ((Number) uRow.get("ID_USUARIO")).longValue();
                Long idPer = uRow.get("ID_PERSONA") != null ? ((Number) uRow.get("ID_PERSONA")).longValue() : null;
                purgarUsuarioYPersona(idUsr, idPer);
                totalPurgados++;
            }

            if (totalPurgados > 0) {
                log.info("[Onboarding] Purga de registros falsos/huérfanos completada: {} entidades depuradas.", totalPurgados);
            }
        } catch (Exception e) {
            log.warn("[Onboarding] Aviso durante purga de registros falsos: {}", e.getMessage());
        } finally {
            limpiarContexto(prevCtx);
        }
        return totalPurgados;
    }

    private void purgarIntentoPrevioIncompleto(String email, String cleanNit, String cleanDoc) {
        try {
            // Buscar si existe usuario pendiente de verificación con ese email
            List<Map<String, Object>> pendientes = jdbcTemplate.queryForList("""
                SELECT u.ID_USUARIO, u.ID_PERSONA, ua.ID_ORGANIZACION
                FROM USUARIOS u
                LEFT JOIN USUARIO_ASIGNACIONES ua ON ua.ID_USUARIO = u.ID_USUARIO
                WHERE u.ID_USUARIO > 1
                  AND LOWER(u.EMAIL) = :em
                  AND u.ESTADO = 'PENDIENTE_VERIFICACION'
                """, new MapSqlParameterSource("em", email.toLowerCase().trim()));

            for (Map<String, Object> p : pendientes) {
                Long idUsr = ((Number) p.get("ID_USUARIO")).longValue();
                Long idPer = p.get("ID_PERSONA") != null ? ((Number) p.get("ID_PERSONA")).longValue() : null;
                Long idOrg = p.get("ID_ORGANIZACION") != null ? ((Number) p.get("ID_ORGANIZACION")).longValue() : null;

                if (idOrg != null && idOrg > 1) {
                    purgarEstructuraOrganizacionInactiva(idOrg);
                }
                purgarUsuarioYPersona(idUsr, idPer);
            }

            // Buscar si existe organización inactiva con ese NIT
            List<Long> orgsInactivasPorNit = jdbcTemplate.queryForList("""
                SELECT ID_ORGANIZACION FROM ORGANIZACIONES
                WHERE IDENTIFICACION_FISCAL = :nit AND ESTADO = 'INACTIVA' AND ID_ORGANIZACION > 1
                """, new MapSqlParameterSource("nit", cleanNit), Long.class);

            for (Long idOrg : orgsInactivasPorNit) {
                purgarEstructuraOrganizacionInactiva(idOrg);
            }

        } catch (Exception e) {
            log.warn("[Onboarding] Advertencia al depurar intento previo incompleto: {}", e.getMessage());
        }
    }

    private void purgarEstructuraOrganizacionInactiva(Long idOrg) {
        if (idOrg == null || idOrg <= 1) return;
        try {
            jdbcTemplate.update("DELETE FROM TRANSACCIONES_PAGO WHERE REFERENCIA_INTERNA LIKE :ref AND ESTADO_PASARELA = 'PENDIENTE'",
                    new MapSqlParameterSource("ref", "SAED-MEMBRESIA-" + idOrg + "-%"));
            jdbcTemplate.update("DELETE FROM MEMBRESIAS WHERE ID_ORGANIZACION = :org AND ESTADO != 'ACTIVA'",
                    new MapSqlParameterSource("org", idOrg));
            jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_ORGANIZACION = :org",
                    new MapSqlParameterSource("org", idOrg));
            jdbcTemplate.update("DELETE FROM ORGANIZACIONES WHERE ID_ORGANIZACION = :org AND ESTADO = 'INACTIVA'",
                    new MapSqlParameterSource("org", idOrg));
        } catch (Exception e) {
            log.warn("[Onboarding] Error eliminando estructura de org inactiva {}: {}", idOrg, e.getMessage());
        }
    }

    private void purgarUsuarioYPersona(Long idUsr, Long idPer) {
        if (idUsr != null && idUsr > 1) {
            try {
                jdbcTemplate.update("DELETE FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :usr",
                        new MapSqlParameterSource("usr", idUsr));
                jdbcTemplate.update("DELETE FROM USUARIOS WHERE ID_USUARIO = :usr AND ESTADO = 'PENDIENTE_VERIFICACION'",
                        new MapSqlParameterSource("usr", idUsr));
                jdbcTemplate.update("DELETE FROM USUARIOS WHERE ID_USUARIO = :usr AND LOWER(EMAIL) IN ('sebasrusso95@gmail.com', 'sebasthompson95@gmail.com')",
                        new MapSqlParameterSource("usr", idUsr));
            } catch (Exception e) {
                log.warn("[Onboarding] Error eliminando usuario huérfano {}: {}", idUsr, e.getMessage());
            }
        }
        if (idPer != null && idPer > 1) {
            try {
                Integer remainingUsers = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM USUARIOS WHERE ID_PERSONA = :p",
                        new MapSqlParameterSource("p", idPer),
                        Integer.class
                );
                if (remainingUsers == null || remainingUsers == 0) {
                    jdbcTemplate.update("DELETE FROM PERSONAS WHERE ID_PERSONA = :p",
                            new MapSqlParameterSource("p", idPer));
                }
            } catch (Exception e) {
                log.warn("[Onboarding] Error eliminando persona huérfana {}: {}", idPer, e.getMessage());
            }
        }
    }

    private Long guardarOActualizarPersona(OnboardingRegistroRequestDTO request, String cleanDoc, Long idTipoDoc, String adminEmail, Long idPersonaExistente) {
        if (idPersonaExistente != null) {
            String updatePerSql = """
                UPDATE PERSONAS
                SET PRIMER_NOMBRE = :nom,
                    SEGUNDO_NOMBRE = :snom,
                    PRIMER_APELLIDO = :ape,
                    SEGUNDO_APELLIDO = :sape,
                    EMAIL = :email,
                    TELEFONO = :tel,
                    ESTADO = 'ACTIVO'
                WHERE ID_PERSONA = :idPer
                """;
            MapSqlParameterSource perParams = new MapSqlParameterSource()
                    .addValue("idPer", idPersonaExistente)
                    .addValue("nom", request.getPrimerNombre().trim())
                    .addValue("snom", (request.getSegundoNombre() != null && !request.getSegundoNombre().isBlank()) ? request.getSegundoNombre().trim() : null)
                    .addValue("ape", request.getPrimerApellido().trim())
                    .addValue("sape", (request.getSegundoApellido() != null && !request.getSegundoApellido().isBlank()) ? request.getSegundoApellido().trim() : null)
                    .addValue("email", adminEmail)
                    .addValue("tel", request.getTelefonoAdmin());
            jdbcTemplate.update(updatePerSql, perParams);
            return idPersonaExistente;
        } else {
            String insertPerSql = """
                INSERT INTO PERSONAS (
                    ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, TIPO_PERSONA,
                    PRIMER_NOMBRE, SEGUNDO_NOMBRE, PRIMER_APELLIDO, SEGUNDO_APELLIDO,
                    EMAIL, TELEFONO, ESTADO
                ) VALUES (
                    :tipoDoc, :doc, 'NATURAL',
                    :nom, :snom, :ape, :sape,
                    :email, :tel, 'ACTIVO'
                )
                """;
            MapSqlParameterSource perParams = new MapSqlParameterSource()
                    .addValue("tipoDoc", idTipoDoc)
                    .addValue("doc", cleanDoc)
                    .addValue("nom", request.getPrimerNombre().trim())
                    .addValue("snom", (request.getSegundoNombre() != null && !request.getSegundoNombre().isBlank()) ? request.getSegundoNombre().trim() : null)
                    .addValue("ape", request.getPrimerApellido().trim())
                    .addValue("sape", (request.getSegundoApellido() != null && !request.getSegundoApellido().isBlank()) ? request.getSegundoApellido().trim() : null)
                    .addValue("email", adminEmail)
                    .addValue("tel", request.getTelefonoAdmin());

            KeyHolder khPer = new GeneratedKeyHolder();
            jdbcTemplate.update(insertPerSql, perParams, khPer, new String[]{"ID_PERSONA"});
            Number perIdNum = khPer.getKey();
            return perIdNum != null ? perIdNum.longValue() : 1L;
        }
    }

    private Long resolverIdTipoDocumento(String docType) {
        Long idTipoDoc = 1L;
        try {
            String cod = (docType != null && !docType.isBlank()) ? docType.trim().toUpperCase() : "CC";
            List<Long> tdList = jdbcTemplate.queryForList(
                    "SELECT ID_TIPO_DOCUMENTO FROM TIPOS_DOCUMENTO WHERE CODIGO = :cod",
                    new MapSqlParameterSource("cod", cod),
                    Long.class
            );
            if (!tdList.isEmpty() && tdList.get(0) != null) {
                idTipoDoc = tdList.get(0);
            }
        } catch (Exception ignored) {}
        return idTipoDoc;
    }

    private Long resolverIdRolAdminOrganizacion() {
        Long idRol = 2L;
        try {
            List<Long> rolIds = jdbcTemplate.query(
                    "SELECT ID_ROL FROM ROLES WHERE CODIGO = 'ADMIN_ORGANIZACION'",
                    (rs, rowNum) -> rs.getLong("ID_ROL")
            );
            if (!rolIds.isEmpty()) idRol = rolIds.get(0);
        } catch (Exception ignored) {}
        return idRol;
    }

    private String resolverCiudad(String ciudad, String departamento) {
        String c = (ciudad != null && !ciudad.isBlank()) ? ciudad.trim() : "Bogotá D.C.";
        if (departamento != null && !departamento.isBlank() && !c.toLowerCase().contains(departamento.toLowerCase())) {
            String combinada = c + " (" + departamento.trim() + ")";
            if (combinada.length() <= 80) return combinada;
        }
        return c;
    }

    private void establecerContextoAdminGlobal() {
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
    }

    private void limpiarContexto(SaedContext prevCtx) {
        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
        } catch (Exception ignored) {}
        SaedContextHolder.setContext(prevCtx);
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
            log.error("[Onboarding] Error calculando firma de integridad Wompi: ", e);
            return "";
        }
    }
}
