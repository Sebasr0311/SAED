package com.saed.backend.org.service;

import com.saed.backend.org.dto.CreateOrgAdminRequestDTO;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the account of a property administrator created by an organization administrator
 * (F3-03 / C-05).
 *
 * <ul>
 *   <li>Runs under the caller's own database context; never sets bootstrap/global context.</li>
 *   <li>An existing account is reused only when it already belongs to the caller's organization
 *       and holds no privileged role; it is never modified (no password, e-mail or person change).</li>
 *   <li>An existing person without account is reused only when it belongs to the organization.</li>
 *   <li>A new account is created {@code PENDIENTE_VERIFICACION} with an unusable random hash; the
 *       requested password is ignored and the user sets one through the activation link.</li>
 * </ul>
 */
@Service
public class OrgAdminAccountService {

    private static final String CONFLICT_MESSAGE =
            "La identidad indicada ya está registrada y no puede vincularse desde esta organización.";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public OrgAdminAccountService(NamedParameterJdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    /** Result of resolving the administrator account. */
    public record AccountResolution(Long idUsuario, boolean created) {
    }

    public AccountResolution resolveOrCreate(Long orgId, CreateOrgAdminRequestDTO request) {
        if (orgId == null) {
            throw new AccessDeniedException("No se encontró contexto de organización activo");
        }
        String doc = request.getNumeroDocumento() == null ? "" : request.getNumeroDocumento().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase(Locale.ROOT);

        Long idPersona = null;
        Long personaOrg = null;
        if (!doc.isEmpty()) {
            List<Map<String, Object>> personas = jdbcTemplate.queryForList(
                    "SELECT ID_PERSONA, ID_ORGANIZACION FROM PERSONAS WHERE NUMERO_DOCUMENTO = :doc",
                    Map.of("doc", doc));
            if (!personas.isEmpty()) {
                idPersona = toLong(personas.get(0).get("ID_PERSONA"));
                personaOrg = toLong(personas.get(0).get("ID_ORGANIZACION"));
            }
        }

        Long existingUser = null;
        if (idPersona != null) {
            existingUser = firstUser(jdbcTemplate.queryForList(
                    "SELECT ID_USUARIO FROM USUARIOS WHERE ID_PERSONA = :p", Map.of("p", idPersona)));
        }
        if (existingUser == null && !email.isEmpty()) {
            existingUser = firstUser(jdbcTemplate.queryForList(
                    "SELECT ID_USUARIO FROM USUARIOS WHERE LOWER(EMAIL) = :email", Map.of("email", email)));
        }

        if (existingUser != null) {
            if (count("""
                    SELECT COUNT(1) FROM USUARIO_ASIGNACIONES ua
                    WHERE ua.ID_USUARIO = :uid AND ua.ID_ORGANIZACION = :org
                    """, existingUser, orgId) == 0
                    || count("""
                    SELECT COUNT(1) FROM USUARIO_ASIGNACIONES ua
                    WHERE ua.ID_USUARIO = :uid AND ua.ID_ORGANIZACION <> :org
                    """, existingUser, orgId) > 0
                    || count("""
                    SELECT COUNT(1) FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
                    WHERE ua.ID_USUARIO = :uid AND r.CODIGO IN ('SUPERADMIN', 'ADMIN_ORGANIZACION')
                    """, existingUser, orgId) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, CONFLICT_MESSAGE);
            }
            return new AccountResolution(existingUser, false);
        }

        if (idPersona != null && !orgId.equals(personaOrg)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CONFLICT_MESSAGE);
        }

        try {
            if (idPersona == null) {
                idPersona = insertPersona(orgId, request, doc, email);
            }
            return new AccountResolution(insertPendingUser(request, idPersona, email), true);
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CONFLICT_MESSAGE, duplicate);
        }
    }

    /**
     * Identity attributes (e-mail, person data, credentials) may only be changed by an
     * organization administrator when the account belongs exclusively to that organization.
     */
    public void assertIdentityManagedByOrganization(Long orgId, Long userId) {
        boolean inOrg = count("""
                SELECT COUNT(1) FROM USUARIO_ASIGNACIONES ua
                WHERE ua.ID_USUARIO = :uid AND ua.ID_ORGANIZACION = :org
                """, userId, orgId) > 0;
        boolean elsewhere = count("""
                SELECT COUNT(1) FROM USUARIO_ASIGNACIONES ua
                WHERE ua.ID_USUARIO = :uid AND ua.ID_ORGANIZACION <> :org
                """, userId, orgId) > 0;
        if (!inOrg || elsewhere) {
            throw new AccessDeniedException(
                    "La cuenta pertenece también a otra organización; sus datos de identidad no pueden modificarse desde aquí.");
        }
    }

    private Long insertPersona(Long orgId, CreateOrgAdminRequestDTO request, String doc, String email) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO PERSONAS (ID_TIPO_DOCUMENTO, NUMERO_DOCUMENTO, PRIMER_NOMBRE, PRIMER_APELLIDO,
                                      EMAIL, TELEFONO, ESTADO, ID_ORGANIZACION)
                VALUES (:tipoDoc, :doc, :nombre, :apellido, :email, :tel, 'ACTIVO', :org)
                """, new MapSqlParameterSource()
                        .addValue("tipoDoc", request.getIdTipoDocumento() != null ? request.getIdTipoDocumento() : 1L)
                        .addValue("doc", doc)
                        .addValue("nombre", request.getPrimerNombre())
                        .addValue("apellido", request.getPrimerApellido())
                        .addValue("email", email)
                        .addValue("tel", request.getTelefono())
                        .addValue("org", orgId),
                kh, new String[]{"ID_PERSONA"});
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo generar el ID para la persona");
        }
        return key.longValue();
    }

    private Long insertPendingUser(CreateOrgAdminRequestDTO request, Long idPersona, String email) {
        String username = request.getNombreUsuario() == null || request.getNombreUsuario().isBlank()
                ? email.split("@")[0].replaceAll("[^a-z0-9]", ".")
                : request.getNombreUsuario().trim();
        Long taken = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM USUARIOS WHERE LOWER(NOMBRE_USUARIO) = LOWER(:u)",
                Map.of("u", username), Long.class);
        if (taken != null && taken > 0) {
            username = username + "." + (100 + random.nextInt(900));
        }
        byte[] unusable = new byte[32];
        random.nextBytes(unusable);

        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update("""
                INSERT INTO USUARIOS (ID_PERSONA, NOMBRE_USUARIO, EMAIL, HASH_PASSWORD, ESTADO, INTENTOS_FALLIDOS)
                VALUES (:idPersona, :username, :email, :pwd, 'PENDIENTE_VERIFICACION', 0)
                """, new MapSqlParameterSource()
                        .addValue("idPersona", idPersona)
                        .addValue("username", username)
                        .addValue("email", email)
                        .addValue("pwd", passwordEncoder.encode(HexFormat.of().formatHex(unusable))),
                kh, new String[]{"ID_USUARIO"});
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo generar el ID para el usuario");
        }
        return key.longValue();
    }

    private long count(String sql, Long userId, Long orgId) {
        Long value = jdbcTemplate.queryForObject(sql, Map.of("uid", userId, "org", orgId), Long.class);
        return value == null ? 0L : value;
    }

    private static Long firstUser(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? null : toLong(rows.get(0).get("ID_USUARIO"));
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
