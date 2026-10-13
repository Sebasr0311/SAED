package com.saed.backend.identity.service;

import com.saed.backend.context.SaedContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

/** Authorizes registration targets without changing the authenticated database context. */
public final class UserRegistrationPolicy {
    private static final Set<String> ROLES = Set.of("SUPERADMIN", "ADMIN_ORGANIZACION",
            "ADMIN_PROPIEDAD", "PORTERO", "RESIDENTE", "PROPIETARIO", "RESIDENTE_CONVIVENCIA");
    private static final Set<String> ADMIN_ROLES = Set.of("SUPERADMIN", "ADMIN_ORGANIZACION", "ADMIN_PROPIEDAD");
    private final NamedParameterJdbcTemplate jdbc;

    public UserRegistrationPolicy(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Scope(Long organizationId, Long propertyId, Long unitId) { }

    public Scope authorizeScope(SaedContext caller, String targetRole, Object requestedUnit,
            Object requestedProperty, Object requestedOrganization) {
        if (caller == null || caller.getUserId() == null || caller.getUserId() <= 0 || !ROLES.contains(targetRole)) {
            deny();
        }
        String role = caller.getRoleCode();
        if (role == null || !Set.of("SUPERADMIN", "ADMIN_ORGANIZACION", "ADMIN_PROPIEDAD", "RESIDENTE").contains(role)) {
            deny();
        }
        if ("ADMIN_PROPIEDAD".equals(role) && ADMIN_ROLES.contains(targetRole)) {
            deny();
        }
        if ("ADMIN_ORGANIZACION".equals(role)
                && Set.of("SUPERADMIN", "ADMIN_ORGANIZACION").contains(targetRole)) {
            deny();
        }
        Long organization = caller.getOrganizationId();
        Long property = caller.getPropertyId();
        Long unit = id(requestedUnit);
        if ("RESIDENTE".equals(role)) {
            if (!"RESIDENTE_CONVIVENCIA".equals(targetRole) || caller.getUnitId() == null
                    || (unit != null && !unit.equals(caller.getUnitId()))) {
                deny();
            }
            unit = caller.getUnitId();
        }
        if (!"SUPERADMIN".equals(role) && organization == null) {
            deny();
        }
        if (("ADMIN_PROPIEDAD".equals(role) || "RESIDENTE".equals(role)) && property == null) {
            deny();
        }
        Long requestedOrg = id(requestedOrganization);
        Long requestedProp = id(requestedProperty);
        if ("SUPERADMIN".equals(role)) {
            if (requestedOrg != null) {
                organization = requestedOrg;
            }
            if (requestedProp != null) {
                property = requestedProp;
            }
        } else {
            if (requestedOrg != null && !requestedOrg.equals(organization)) {
                deny();
            }
            if (requestedProp != null) {
                if (!"ADMIN_ORGANIZACION".equals(role) && !requestedProp.equals(property)) {
                    deny();
                }
                property = requestedProp;
            }
        }
        if (unit != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT u.ID_PROPIEDAD, p.ID_ORGANIZACION
                    FROM UNIDADES u JOIN PROPIEDADES p ON p.ID_PROPIEDAD = u.ID_PROPIEDAD
                    WHERE u.ID_UNIDAD = :unitId
                    """, Map.of("unitId", unit));
            if (rows.size() != 1) {
                deny();
            }
            Long actualProperty = ((Number) rows.get(0).get("ID_PROPIEDAD")).longValue();
            Long actualOrganization = ((Number) rows.get(0).get("ID_ORGANIZACION")).longValue();
            if (!"SUPERADMIN".equals(role) && !actualOrganization.equals(organization)) {
                deny();
            }
            if (!"SUPERADMIN".equals(role) && !"ADMIN_ORGANIZACION".equals(role)
                    && !actualProperty.equals(property)) {
                deny();
            }
            if (requestedProp != null && !requestedProp.equals(actualProperty)) {
                deny();
            }
            if (requestedOrg != null && !requestedOrg.equals(actualOrganization)) {
                deny();
            }
            organization = actualOrganization;
            property = actualProperty;
        } else if (property != null) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT ID_PROPIEDAD, ID_ORGANIZACION FROM PROPIEDADES WHERE ID_PROPIEDAD = :propertyId
                    """, Map.of("propertyId", property));
            if (rows.size() != 1) {
                deny();
            }
            Long actualOrganization = ((Number) rows.get(0).get("ID_ORGANIZACION")).longValue();
            if (!"SUPERADMIN".equals(role) && !actualOrganization.equals(organization)) {
                deny();
            }
            organization = actualOrganization;
        }
        if (organization == null) {
            deny();
        }
        if (!Set.of("SUPERADMIN", "ADMIN_ORGANIZACION").contains(targetRole) && property == null) {
            deny();
        }
        if (Set.of("RESIDENTE", "PROPIETARIO", "RESIDENTE_CONVIVENCIA").contains(targetRole) && unit == null) {
            deny();
        }
        return new Scope(organization, property, unit);
    }

    public void authorizePerson(SaedContext caller, Scope scope, Long personId) {
        if ("SUPERADMIN".equals(caller.getRoleCode())) {
            Integer exists = jdbc.queryForObject("SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = :personId",
                    new MapSqlParameterSource("personId", personId), Integer.class);
            if (exists == null || exists != 1) {
                deny();
            }
            return;
        }
        String boundary = "ADMIN_ORGANIZACION".equals(caller.getRoleCode()) ? ""
                : " AND t.ID_PROPIEDAD = :propertyId";
        if ("RESIDENTE".equals(caller.getRoleCode())) {
            boundary += " AND t.ID_UNIDAD = :unitId";
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(1) FROM (
                    SELECT ua.ID_ORGANIZACION, ua.ID_PROPIEDAD, ua.ID_UNIDAD
                    FROM USUARIO_ASIGNACIONES ua JOIN USUARIOS us ON us.ID_USUARIO = ua.ID_USUARIO
                    WHERE us.ID_PERSONA = :personId AND ua.ESTADO IN ('ACTIVA', 'ACTIVO')
                    UNION ALL
                    SELECT pr.ID_ORGANIZACION, u.ID_PROPIEDAD, u.ID_UNIDAD
                    FROM RESIDENTES_UNIDAD ru JOIN UNIDADES u ON u.ID_UNIDAD = ru.ID_UNIDAD
                    JOIN PROPIEDADES pr ON pr.ID_PROPIEDAD = u.ID_PROPIEDAD
                    WHERE ru.ID_PERSONA = :personId AND ru.ESTADO IN ('ACTIVO', 'ACTIVA')
                    UNION ALL
                    SELECT pr.ID_ORGANIZACION, u.ID_PROPIEDAD, u.ID_UNIDAD
                    FROM PROPIETARIOS_UNIDAD pu JOIN UNIDADES u ON u.ID_UNIDAD = pu.ID_UNIDAD
                    JOIN PROPIEDADES pr ON pr.ID_PROPIEDAD = u.ID_PROPIEDAD
                    WHERE pu.ID_PERSONA = :personId AND pu.ESTADO = 'ACTIVO'
                ) t WHERE t.ID_ORGANIZACION = :organizationId
                """ + boundary, new MapSqlParameterSource("personId", personId)
                .addValue("organizationId", scope.organizationId()).addValue("propertyId", scope.propertyId())
                .addValue("unitId", scope.unitId()), Integer.class);
        if (count == null || count == 0) {
            deny();
        }
    }

    public void authorizeAccount(String state, List<Map<String, Object>> assignments) {
        if (!"ACTIVO".equals(state)) {
            deny();
        }
        if (assignments.stream().anyMatch(a -> ADMIN_ROLES.contains(a.get("ROL_CODIGO")))) {
            deny();
        }
    }

    private static Long id(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.toString());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) { }
        throw new AccessDeniedException("El destino del registro no es válido");
    }

    private static void deny() {
        throw new AccessDeniedException("No tiene autorización sobre el destino del registro");
    }
}
