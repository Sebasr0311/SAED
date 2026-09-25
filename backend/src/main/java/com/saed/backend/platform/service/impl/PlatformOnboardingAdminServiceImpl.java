package com.saed.backend.platform.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.common.util.PasswordGenerator;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.dto.ActualizarCredencialesRequestDTO;
import com.saed.backend.platform.dto.EmailDispatchResponseDTO;
import com.saed.backend.platform.dto.OnboardingAdminSummaryDTO;
import com.saed.backend.platform.dto.OnboardingRegistroRequestDTO;
import com.saed.backend.platform.dto.ReenviarCredencialesRequestDTO;
import com.saed.backend.platform.service.OnboardingService;
import com.saed.backend.platform.service.PlatformOnboardingAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

@Service
public class PlatformOnboardingAdminServiceImpl implements PlatformOnboardingAdminService {

    private static final Logger log = LoggerFactory.getLogger(PlatformOnboardingAdminServiceImpl.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final OnboardingService onboardingService;

    public PlatformOnboardingAdminServiceImpl(
            NamedParameterJdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            ObjectMapper objectMapper,
            OnboardingService onboardingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.onboardingService = onboardingService;
    }

    @Override
    public List<OnboardingAdminSummaryDTO> listarSolicitudes() {
        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            establecerContextoAdminGlobal();

            String sqlIntenciones = """
                SELECT 
                    oi.REFERENCIA,
                    oi.DATOS_REGISTRO,
                    oi.MONTO_CENTAVOS,
                    oi.ESTADO AS ESTADO_INTENCION,
                    TO_CHAR(oi.FECHA_CREACION, 'YYYY-MM-DD HH24:MI:SS') AS FECHA_REGISTRO,
                    oi.CORREO_ESTADO,
                    oi.CORREO_DETALLE,
                    TO_CHAR(oi.CORREO_FECHA, 'YYYY-MM-DD HH24:MI:SS') AS CORREO_FECHA,
                    oi.ID_ORGANIZACION,
                    oi.ID_USUARIO,
                    tp.ESTADO_PASARELA,
                    tp.ID_TRANSACCION_PASARELA,
                    o.NOMBRE AS ORG_NOMBRE,
                    o.IDENTIFICACION_FISCAL AS ORG_NIT,
                    o.ESTADO AS ORG_ESTADO,
                    o.EMAIL_CONTACTO AS ORG_EMAIL,
                    u.NOMBRE_USUARIO,
                    u.EMAIL AS USUARIO_EMAIL,
                    u.ESTADO AS USUARIO_ESTADO,
                    p.PRIMER_NOMBRE,
                    p.PRIMER_APELLIDO,
                    p.NUMERO_DOCUMENTO,
                    p.TELEFONO,
                    pl.ID_PLAN,
                    pl.NOMBRE AS PLAN_NOMBRE,
                    m.ESTADO AS MEMBRESIA_ESTADO,
                    m.ES_PRUEBA AS MEMBRESIA_ES_PRUEBA
                FROM ONBOARDING_INTENCIONES oi
                LEFT JOIN TRANSACCIONES_PAGO tp ON oi.REFERENCIA = tp.REFERENCIA_INTERNA
                LEFT JOIN ORGANIZACIONES o ON oi.ID_ORGANIZACION = o.ID_ORGANIZACION
                LEFT JOIN USUARIOS u ON oi.ID_USUARIO = u.ID_USUARIO
                LEFT JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA
                LEFT JOIN MEMBRESIAS m ON o.ID_ORGANIZACION = m.ID_ORGANIZACION AND m.ESTADO = 'ACTIVA'
                LEFT JOIN PLANES pl ON m.ID_PLAN = pl.ID_PLAN
                ORDER BY oi.FECHA_CREACION DESC
                """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sqlIntenciones, new MapSqlParameterSource());
            List<OnboardingAdminSummaryDTO> results = new ArrayList<>();
            Set<Long> processedOrgIds = new HashSet<>();

            for (Map<String, Object> r : rows) {
                OnboardingAdminSummaryDTO dto = new OnboardingAdminSummaryDTO();
                String ref = (String) r.get("REFERENCIA");
                dto.setReferencia(ref);
                dto.setFechaRegistro((String) r.get("FECHA_REGISTRO"));
                dto.setEstadoIntencion((String) r.get("ESTADO_INTENCION"));

                Number montoCent = (Number) r.get("MONTO_CENTAVOS");
                dto.setMontoPesos(montoCent != null ? montoCent.longValue() / 100 : 0L);

                String estadoPasarela = (String) r.get("ESTADO_PASARELA");
                dto.setEstadoPago(estadoPasarela != null ? estadoPasarela : "PENDIENTE");

                String cEstado = (String) r.get("CORREO_ESTADO");
                dto.setEstadoCorreo(cEstado != null ? cEstado : "PENDIENTE");
                dto.setDetalleCorreo((String) r.get("CORREO_DETALLE"));
                dto.setFechaCorreo((String) r.get("CORREO_FECHA"));

                Number idOrgNum = (Number) r.get("ID_ORGANIZACION");
                if (idOrgNum != null) {
                    dto.setIdOrganizacion(idOrgNum.longValue());
                    processedOrgIds.add(idOrgNum.longValue());
                }

                Number idUsrNum = (Number) r.get("ID_USUARIO");
                if (idUsrNum != null) {
                    dto.setIdUsuario(idUsrNum.longValue());
                }

                dto.setNombreOrganizacion((String) r.get("ORG_NOMBRE"));
                dto.setIdentificacionFiscal((String) r.get("ORG_NIT"));

                String pNombre = (String) r.get("PRIMER_NOMBRE");
                String pApellido = (String) r.get("PRIMER_APELLIDO");
                if (pNombre != null || pApellido != null) {
                    dto.setAdminNombreCompleto(((pNombre != null ? pNombre : "") + " " + (pApellido != null ? pApellido : "")).trim());
                }

                String usrEmail = (String) r.get("USUARIO_EMAIL");
                String orgEmail = (String) r.get("ORG_EMAIL");
                String resolvedAdminEmail = (orgEmail != null && !orgEmail.isBlank())
                    ? orgEmail
                    : ((usrEmail != null && !usrEmail.isBlank()) ? usrEmail : null);

                dto.setAdminUsername((String) r.get("NOMBRE_USUARIO"));
                dto.setAdminEmail(resolvedAdminEmail);
                dto.setNumeroDocumento((String) r.get("NUMERO_DOCUMENTO"));
                dto.setTelefono((String) r.get("TELEFONO"));
                dto.setPlanNombre((String) r.get("PLAN_NOMBRE"));

                Number idPlanNum = (Number) r.get("ID_PLAN");
                if (idPlanNum != null) dto.setIdPlan(idPlanNum.longValue());

                String esPruebaStr = (String) r.get("MEMBRESIA_ES_PRUEBA");
                dto.setEsPrueba("S".equalsIgnoreCase(esPruebaStr) || (montoCent != null && montoCent.longValue() == 0));

                // Deserializar JSON si faltan datos en DB (por ejemplo si aún está en staging o pendiente)
                String rawJson = (String) r.get("DATOS_REGISTRO");
                if (rawJson != null && !rawJson.isBlank()) {
                    try {
                        OnboardingRegistroRequestDTO req = objectMapper.readValue(rawJson, OnboardingRegistroRequestDTO.class);
                        if (dto.getNombreOrganizacion() == null) dto.setNombreOrganizacion(req.getNombreOrganizacion());
                        if (dto.getIdentificacionFiscal() == null) dto.setIdentificacionFiscal(req.getIdentificacionFiscal());
                        dto.setTipoPersona(req.getTipoPersona() != null ? req.getTipoPersona() : "JURIDICA");
                        dto.setTipoDocumento(req.getTipoDocumento());
                        if (dto.getNumeroDocumento() == null) dto.setNumeroDocumento(req.getNumeroDocumento());
                        if (dto.getAdminNombreCompleto() == null) {
                            dto.setAdminNombreCompleto((req.getPrimerNombre() + " " + req.getPrimerApellido()).trim());
                        }
                        if (dto.getAdminEmail() == null) dto.setAdminEmail(req.getEmailAdmin());
                        if (dto.getAdminUsername() == null) dto.setAdminUsername(req.getAdminUsername());
                        if (dto.getTelefono() == null) dto.setTelefono(req.getTelefonoAdmin() != null ? req.getTelefonoAdmin() : req.getTelefonoOrganizacion());
                        dto.setCicloFacturacion(req.getCicloFacturacion() != null ? req.getCicloFacturacion() : "MENSUAL");
                        if (dto.getIdPlan() == null) dto.setIdPlan(req.getIdPlan());
                        if (dto.getEsPrueba() == null) dto.setEsPrueba(req.getEsPrueba());
                    } catch (Exception e) {
                        log.debug("Error parsing raw DATOS_REGISTRO JSON: {}", e.getMessage());
                    }
                }

                if (dto.getTipoPersona() == null) dto.setTipoPersona("JURIDICA");
                if (dto.getCicloFacturacion() == null) dto.setCicloFacturacion("MENSUAL");

                if (dto.getPlanNombre() == null && dto.getIdPlan() != null) {
                    try {
                        String pNom = jdbcTemplate.queryForObject(
                            "SELECT NOMBRE FROM PLANES WHERE ID_PLAN = :p",
                            new MapSqlParameterSource("p", dto.getIdPlan()),
                            String.class
                        );
                        dto.setPlanNombre(pNom);
                    } catch (Exception ignored) {}
                }

                results.add(dto);
            }

            // Además, incluir cualquier ORGANIZACIÓN activa existente que no esté en ONBOARDING_INTENCIONES
            String sqlOrgs = """
                SELECT o.ID_ORGANIZACION,
                       o.NOMBRE AS ORG_NOMBRE,
                       o.IDENTIFICACION_FISCAL AS ORG_NIT,
                       o.EMAIL_CONTACTO,
                       o.TELEFONO_CONTACTO,
                       TO_CHAR(o.FECHA_CREACION, 'YYYY-MM-DD HH24:MI:SS') AS FECHA_REGISTRO,
                       u.ID_USUARIO,
                       u.NOMBRE_USUARIO,
                       u.EMAIL AS USUARIO_EMAIL,
                       p.PRIMER_NOMBRE,
                       p.PRIMER_APELLIDO,
                       p.NUMERO_DOCUMENTO,
                       p.TELEFONO,
                       pl.ID_PLAN,
                       pl.NOMBRE AS PLAN_NOMBRE,
                       m.ESTADO AS MEMBRESIA_ESTADO,
                       m.ES_PRUEBA AS MEMBRESIA_ES_PRUEBA
                FROM ORGANIZACIONES o
                JOIN USUARIO_ASIGNACIONES ua ON o.ID_ORGANIZACION = ua.ID_ORGANIZACION AND ua.ESTADO = 'ACTIVA'
                JOIN ROLES r ON ua.ID_ROL = r.ID_ROL AND r.CODIGO = 'ADMIN_ORGANIZACION'
                JOIN USUARIOS u ON ua.ID_USUARIO = u.ID_USUARIO
                LEFT JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA
                LEFT JOIN MEMBRESIAS m ON o.ID_ORGANIZACION = m.ID_ORGANIZACION AND m.ESTADO = 'ACTIVA'
                LEFT JOIN PLANES pl ON m.ID_PLAN = pl.ID_PLAN
                WHERE o.ESTADO != 'INACTIVA'
                ORDER BY o.ID_ORGANIZACION DESC
                """;
            List<Map<String, Object>> orgRows = jdbcTemplate.queryForList(sqlOrgs, new MapSqlParameterSource());
            for (Map<String, Object> orgRow : orgRows) {
                Number orgId = (Number) orgRow.get("ID_ORGANIZACION");
                if (orgId != null && !processedOrgIds.contains(orgId.longValue())) {
                    processedOrgIds.add(orgId.longValue());
                    OnboardingAdminSummaryDTO dto = new OnboardingAdminSummaryDTO();
                    dto.setReferencia("ORG-EXISTENTE-" + orgId);
                    dto.setIdOrganizacion(orgId.longValue());
                    dto.setFechaRegistro((String) orgRow.get("FECHA_REGISTRO"));
                    dto.setNombreOrganizacion((String) orgRow.get("ORG_NOMBRE"));
                    dto.setIdentificacionFiscal((String) orgRow.get("ORG_NIT"));
                    dto.setTipoPersona("JURIDICA");
                    dto.setTipoDocumento("NIT");
                    dto.setNumeroDocumento((String) orgRow.get("NUMERO_DOCUMENTO"));

                    String pNombre = (String) orgRow.get("PRIMER_NOMBRE");
                    String pApellido = (String) orgRow.get("PRIMER_APELLIDO");
                    if (pNombre != null || pApellido != null) {
                        dto.setAdminNombreCompleto(((pNombre != null ? pNombre : "") + " " + (pApellido != null ? pApellido : "")).trim());
                    }

                    Number usrId = (Number) orgRow.get("ID_USUARIO");
                    if (usrId != null) dto.setIdUsuario(usrId.longValue());

                    String usrEmail = (String) orgRow.get("USUARIO_EMAIL");
                    String orgEmail = (String) orgRow.get("EMAIL_CONTACTO");
                    String resolvedAdminEmail = (orgEmail != null && !orgEmail.isBlank())
                        ? orgEmail
                        : ((usrEmail != null && !usrEmail.isBlank()) ? usrEmail : null);

                    dto.setAdminUsername((String) orgRow.get("NOMBRE_USUARIO"));
                    dto.setAdminEmail(resolvedAdminEmail);
                    dto.setTelefono((String) orgRow.get("TELEFONO"));
                    dto.setPlanNombre((String) orgRow.get("PLAN_NOMBRE"));

                    Number idPlan = (Number) orgRow.get("ID_PLAN");
                    if (idPlan != null) dto.setIdPlan(idPlan.longValue());

                    dto.setEstadoIntencion("COMPLETADA");
                    dto.setEstadoPago("APROBADO");
                    dto.setEstadoCorreo("ENVIADO");
                    dto.setDetalleCorreo("Organización registrada en el sistema.");
                    dto.setEsPrueba("S".equalsIgnoreCase((String) orgRow.get("MEMBRESIA_ES_PRUEBA")));
                    dto.setCicloFacturacion("MENSUAL");
                    dto.setMontoPesos(0L);

                    results.add(dto);
                }
            }

            return results;
        } finally {
            limpiarContexto(prevCtx);
        }
    }

    @Override
    @Transactional
    public EmailDispatchResponseDTO reenviarCredenciales(ReenviarCredencialesRequestDTO request) {
        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            establecerContextoAdminGlobal();

            Long idUsuario = request.getIdUsuario();
            String referencia = request.getReferencia();

            if (idUsuario == null && referencia != null) {
                List<Long> uIds = jdbcTemplate.query(
                    "SELECT ID_USUARIO FROM ONBOARDING_INTENCIONES WHERE REFERENCIA = :ref AND ID_USUARIO IS NOT NULL",
                    new MapSqlParameterSource("ref", referencia),
                    (rs, rowNum) -> rs.getLong("ID_USUARIO")
                );
                if (!uIds.isEmpty()) idUsuario = uIds.get(0);
            }

            if (idUsuario == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se encontró un usuario materializado asociado para reenviar credenciales.");
            }

            String userSql = """
                SELECT u.ID_USUARIO, u.NOMBRE_USUARIO, u.EMAIL AS USER_EMAIL,
                       p.PRIMER_NOMBRE, p.PRIMER_APELLIDO,
                       o.ID_ORGANIZACION, o.NOMBRE AS ORG_NOMBRE,
                       o.EMAIL_CONTACTO AS ORG_EMAIL,
                       pl.NOMBRE AS PLAN_NOMBRE
                FROM USUARIOS u
                JOIN PERSONAS p ON u.ID_PERSONA = p.ID_PERSONA
                LEFT JOIN USUARIO_ASIGNACIONES ua ON u.ID_USUARIO = ua.ID_USUARIO AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
                LEFT JOIN ORGANIZACIONES o ON ua.ID_ORGANIZACION = o.ID_ORGANIZACION
                LEFT JOIN MEMBRESIAS m ON o.ID_ORGANIZACION = m.ID_ORGANIZACION AND m.ESTADO = 'ACTIVA'
                LEFT JOIN PLANES pl ON m.ID_PLAN = pl.ID_PLAN
                WHERE u.ID_USUARIO = :id
                """;
            List<Map<String, Object>> userRows = jdbcTemplate.queryForList(userSql, new MapSqlParameterSource("id", idUsuario));
            if (userRows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
            }

            Map<String, Object> uRow = userRows.get(0);
            String username = (String) uRow.get("NOMBRE_USUARIO");
            String currentEmail = (String) uRow.get("USER_EMAIL");
            String orgEmail = (String) uRow.get("ORG_EMAIL");
            Long idOrganizacion = uRow.get("ID_ORGANIZACION") != null ? ((Number) uRow.get("ID_ORGANIZACION")).longValue() : null;

            String targetEmail;
            if (request.getEmailDestino() != null && !request.getEmailDestino().isBlank()) {
                targetEmail = request.getEmailDestino().trim().toLowerCase();
            } else if (orgEmail != null && !orgEmail.isBlank()) {
                targetEmail = orgEmail.trim().toLowerCase();
            } else if (currentEmail != null && !currentEmail.isBlank()) {
                targetEmail = currentEmail.trim().toLowerCase();
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se encontró un correo válido para el envío de credenciales.");
            }

            String pNombre = (String) uRow.get("PRIMER_NOMBRE");
            String pApellido = (String) uRow.get("PRIMER_APELLIDO");
            String nombreCompleto = ((pNombre != null ? pNombre : "") + " " + (pApellido != null ? pApellido : "")).trim();
            if (nombreCompleto.isBlank()) nombreCompleto = "Administrador";

            String orgNombre = (String) uRow.get("ORG_NOMBRE");
            if (orgNombre == null) orgNombre = "SAED";
            String planNombre = (String) uRow.get("PLAN_NOMBRE");
            if (planNombre == null) planNombre = "Plan SAED";

            String passwordToSend;
            if (request.getPasswordManual() != null && !request.getPasswordManual().isBlank()) {
                passwordToSend = request.getPasswordManual().trim();
                jdbcTemplate.update("UPDATE USUARIOS SET HASH_PASSWORD = :pwd, ESTADO = 'ACTIVO' WHERE ID_USUARIO = :id",
                        new MapSqlParameterSource("pwd", passwordEncoder.encode(passwordToSend)).addValue("id", idUsuario));
            } else {
                passwordToSend = PasswordGenerator.generate();
                jdbcTemplate.update("UPDATE USUARIOS SET HASH_PASSWORD = :pwd, ESTADO = 'ACTIVO' WHERE ID_USUARIO = :id",
                        new MapSqlParameterSource("pwd", passwordEncoder.encode(passwordToSend)).addValue("id", idUsuario));
            }

            // Sincronizar el correo en USUARIOS, PERSONAS y ORGANIZACIONES
            try {
                jdbcTemplate.update("UPDATE USUARIOS SET EMAIL = :e WHERE ID_USUARIO = :id",
                        new MapSqlParameterSource("e", targetEmail).addValue("id", idUsuario));
                jdbcTemplate.update("UPDATE PERSONAS SET EMAIL = :e WHERE ID_PERSONA = (SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :id)",
                        new MapSqlParameterSource("e", targetEmail).addValue("id", idUsuario));
                if (idOrganizacion != null) {
                    jdbcTemplate.update("UPDATE ORGANIZACIONES SET EMAIL_CONTACTO = :e WHERE ID_ORGANIZACION = :orgId",
                            new MapSqlParameterSource("e", targetEmail).addValue("orgId", idOrganizacion));
                    jdbcTemplate.update("""
                        UPDATE ONBOARDING_INTENCIONES
                        SET CORREO_DETALLE = 'Credenciales reenviadas al correo ' || :e
                        WHERE ID_ORGANIZACION = :orgId OR ID_USUARIO = :id
                        """, new MapSqlParameterSource("e", targetEmail).addValue("orgId", idOrganizacion).addValue("id", idUsuario));
                }
            } catch (Exception e) {
                log.warn("Aviso al sincronizar email del usuario {}: {}. Se despachará el correo directamente a {}.", idUsuario, e.getMessage(), targetEmail);
            }

            EmailService.EmailDispatchResult result = emailService.enviarBienvenidaCredencialesConResultado(
                    targetEmail,
                    nombreCompleto,
                    orgNombre,
                    planNombre,
                    "ADMIN_ORGANIZACION",
                    username,
                    passwordToSend,
                    "https://saedfront.vercel.app/login"
            );

            if (referencia != null) {
                try {
                    jdbcTemplate.update("""
                        UPDATE ONBOARDING_INTENCIONES
                        SET CORREO_ESTADO = :cEst,
                            CORREO_DETALLE = :cDet,
                            CORREO_FECHA = CURRENT_TIMESTAMP
                        WHERE REFERENCIA = :ref
                        """,
                        new MapSqlParameterSource()
                            .addValue("cEst", result.estado())
                            .addValue("cDet", result.detalle())
                            .addValue("ref", referencia)
                    );
                } catch (Exception ignored) {}
            }

            return new EmailDispatchResponseDTO(
                    result.estado(),
                    result.detalle(),
                    result.real(),
                    targetEmail,
                    username,
                    passwordToSend,
                    LocalDate.now().toString()
            );
        } finally {
            limpiarContexto(prevCtx);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> actualizarCredenciales(ActualizarCredencialesRequestDTO request) {
        SaedContext prevCtx = SaedContextHolder.getContext();
        try {
            establecerContextoAdminGlobal();

            Long idUsuario = request.getIdUsuario();
            List<Map<String, Object>> usrList = jdbcTemplate.queryForList(
                    "SELECT ID_USUARIO, NOMBRE_USUARIO, EMAIL, ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :id",
                    new MapSqlParameterSource("id", idUsuario)
            );
            if (usrList.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario con ID " + idUsuario + " no encontrado");
            }

            Map<String, Object> currentUsr = usrList.get(0);
            String currentUsername = (String) currentUsr.get("NOMBRE_USUARIO");
            String currentEmail = (String) currentUsr.get("EMAIL");
            Long idPersona = ((Number) currentUsr.get("ID_PERSONA")).longValue();

            String nuevoUsername = (request.getNuevoUsername() != null && !request.getNuevoUsername().isBlank())
                    ? request.getNuevoUsername().trim().toLowerCase()
                    : currentUsername;
            String nuevoEmail = (request.getNuevoEmail() != null && !request.getNuevoEmail().isBlank())
                    ? request.getNuevoEmail().trim().toLowerCase()
                    : currentEmail;
            String nuevaPassword = request.getNuevaPassword();

            if (!nuevoUsername.equalsIgnoreCase(currentUsername)) {
                Integer countUser = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = :u AND ID_USUARIO != :id",
                        new MapSqlParameterSource("u", nuevoUsername).addValue("id", idUsuario),
                        Integer.class
                );
                if (countUser != null && countUser > 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario '" + nuevoUsername + "' ya está en uso.");
                }
            }

            if (!nuevoEmail.equalsIgnoreCase(currentEmail)) {
                Integer countEmail = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM USUARIOS WHERE LOWER(EMAIL) = :e AND ID_USUARIO != :id",
                        new MapSqlParameterSource("e", nuevoEmail).addValue("id", idUsuario),
                        Integer.class
                );
                if (countEmail != null && countEmail > 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo electrónico '" + nuevoEmail + "' ya está registrado.");
                }
            }

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("id", idUsuario)
                    .addValue("user", nuevoUsername)
                    .addValue("email", nuevoEmail);

            if (nuevaPassword != null && !nuevaPassword.isBlank()) {
                params.addValue("pwd", passwordEncoder.encode(nuevaPassword.trim()));
                jdbcTemplate.update("""
                    UPDATE USUARIOS
                    SET NOMBRE_USUARIO = :user,
                        EMAIL = :email,
                        HASH_PASSWORD = :pwd,
                        INTENTOS_FALLIDOS = 0
                    WHERE ID_USUARIO = :id
                    """, params);
            } else {
                jdbcTemplate.update("""
                    UPDATE USUARIOS
                    SET NOMBRE_USUARIO = :user,
                        EMAIL = :email
                    WHERE ID_USUARIO = :id
                    """, params);
            }

            jdbcTemplate.update("UPDATE PERSONAS SET EMAIL = :email WHERE ID_PERSONA = :idP",
                    new MapSqlParameterSource("email", nuevoEmail).addValue("idP", idPersona));

            // Sincronizar también con ORGANIZACIONES y ONBOARDING_INTENCIONES si este usuario es ADMIN_ORGANIZACION
            try {
                jdbcTemplate.update("""
                    UPDATE ORGANIZACIONES
                    SET EMAIL_CONTACTO = :email
                    WHERE ID_ORGANIZACION IN (
                        SELECT ua.ID_ORGANIZACION
                        FROM USUARIO_ASIGNACIONES ua
                        JOIN ROLES r ON ua.ID_ROL = r.ID_ROL AND r.CODIGO = 'ADMIN_ORGANIZACION'
                        WHERE ua.ID_USUARIO = :id AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
                    )
                    """, new MapSqlParameterSource("email", nuevoEmail).addValue("id", idUsuario));

                jdbcTemplate.update("""
                    UPDATE ONBOARDING_INTENCIONES
                    SET CORREO_DETALLE = 'Credenciales actualizadas por Superadmin a ' || :email
                    WHERE ID_USUARIO = :id
                    """, new MapSqlParameterSource("email", nuevoEmail).addValue("id", idUsuario));
            } catch (Exception e) {
                log.warn("Aviso al sincronizar ORGANIZACIONES.EMAIL_CONTACTO en actualizarCredenciales: {}", e.getMessage());
            }

            EmailService.EmailDispatchResult dispatchResult = null;
            if (Boolean.TRUE.equals(request.getReenviarCorreo()) && nuevaPassword != null && !nuevaPassword.isBlank()) {
                List<String> names = jdbcTemplate.query(
                        "SELECT PRIMER_NOMBRE || ' ' || PRIMER_APELLIDO FROM PERSONAS WHERE ID_PERSONA = :id",
                        new MapSqlParameterSource("id", idPersona),
                        (rs, rowNum) -> rs.getString(1)
                );
                String fullName = !names.isEmpty() ? names.get(0) : "Administrador";

                dispatchResult = emailService.enviarBienvenidaCredencialesConResultado(
                        nuevoEmail,
                        fullName,
                        "SAED",
                        "Plan SAED",
                        "ADMIN_ORGANIZACION",
                        nuevoUsername,
                        nuevaPassword.trim(),
                        "https://saedfront.vercel.app/login"
                );
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("idUsuario", idUsuario);
            resp.put("nombreUsuario", nuevoUsername);
            resp.put("email", nuevoEmail);
            resp.put("passwordActualizada", nuevaPassword != null && !nuevaPassword.isBlank());
            resp.put("mensaje", "Credenciales actualizadas exitosamente");
            if (dispatchResult != null) {
                resp.put("correoEstado", dispatchResult.estado());
                resp.put("correoDetalle", dispatchResult.detalle());
            }

            return resp;
        } finally {
            limpiarContexto(prevCtx);
        }
    }

    @Override
    @Transactional
    public Map<String, Object> aprobarManualmente(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referencia es obligatoria");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT MONTO_CENTAVOS FROM ONBOARDING_INTENCIONES WHERE REFERENCIA = :ref",
                new MapSqlParameterSource("ref", referencia)
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Intención de onboarding no encontrada");
        }
        Number monto = (Number) rows.get(0).get("MONTO_CENTAVOS");
        Long centavos = monto != null ? monto.longValue() : 0L;

        boolean ok = onboardingService.materializarOrganizacion(referencia, centavos, "APROBADO_MANUAL_SUPERADMIN");
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo materializar la organización");
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("referencia", referencia);
        resp.put("mensaje", "Organización y usuario activados y materializados exitosamente.");
        return resp;
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
        SaedContextHolder.setContext(prevCtx);
        if (prevCtx != null && prevCtx.getUserId() != null) {
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(" + prevCtx.getUserId() + "); "
                        + "PKG_SAED_SESSION.SET_CONTEXT(" + prevCtx.getUserId() + ", "
                        + (prevCtx.getOrganizationId() != null ? prevCtx.getOrganizationId() : "NULL") + ", "
                        + (prevCtx.getPropertyId() != null ? prevCtx.getPropertyId() : "NULL") + ", "
                        + (prevCtx.getRoleCode() != null ? "'" + prevCtx.getRoleCode() + "'" : "NULL") + "); END;");
            } catch (Exception ignored) {}
        } else {
            try {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT(); END;");
            } catch (Exception ignored) {}
        }
    }
}
