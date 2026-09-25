package com.saed.backend.authorization.service;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.authorization.dto.AssignmentRequestDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.repository.AssignmentRepository;
import com.saed.backend.authorization.repository.RoleRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.repository.PropertyRepository;

@Service
public class AssignmentManagementService {

    private final AssignmentRepository assignmentRepository;
    private final RoleRepository roleRepository;
    private final PropertyRepository propertyRepository;
    private final com.saed.backend.platform.service.PlanLimitService planLimitService;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate;

    public AssignmentManagementService(AssignmentRepository assignmentRepository,
                                       RoleRepository roleRepository,
                                       PropertyRepository propertyRepository,
                                       com.saed.backend.platform.service.PlanLimitService planLimitService,
                                       org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate) {
        this.assignmentRepository = assignmentRepository;
        this.roleRepository = roleRepository;
        this.propertyRepository = propertyRepository;
        this.planLimitService = planLimitService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Auditable(action = "CREATE", resource = "ASIGNACION", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.CRITICAL)
    @Transactional
    public Long create(AssignmentRequestDTO request) {
        RoleDTO targetRole = roleRepository.findById(request.getIdRol())
                .orElseThrow(() -> new IllegalArgumentException("Role not found or inactive"));

        SaedContext ctx = SaedContextHolder.getContext();
        String currentScope = ctx.getRoleScope();
        String currentCode = ctx.getRoleCode();

        if (!"SUPERADMIN".equals(currentCode)) {
            // Anti-privilege escalation: non-superadmin cannot assign GLOBAL/SUPERADMIN
            if ("GLOBAL".equals(targetRole.getAlcance()) || "SUPERADMIN".equals(targetRole.getCodigo())) {
                throw new AccessDeniedException("Cannot assign GLOBAL scope or SUPERADMIN role");
            }
            if ("ORGANIZACION".equals(currentScope) || "ADMIN_ORGANIZACION".equals(currentCode)) {
                if ("ORGANIZACION".equals(targetRole.getAlcance()) || "ADMIN_ORGANIZACION".equals(targetRole.getCodigo())) {
                    throw new AccessDeniedException("ADMIN_ORGANIZACION no puede crear ni asignar el rol ADMIN_ORGANIZACION");
                }
                if (!"PROPIEDAD".equals(targetRole.getAlcance())) {
                    throw new AccessDeniedException("ADMIN_ORGANIZACION solo puede asignar roles de alcance PROPIEDAD");
                }
            }
            if ("PROPIEDAD".equals(currentScope) &&
                    ("GLOBAL".equals(targetRole.getAlcance()) || "ORGANIZACION".equals(targetRole.getAlcance()) || "ADMIN_PROPIEDAD".equals(targetRole.getCodigo()))) {
                throw new AccessDeniedException("Cannot assign higher or administrative scope than yours");
            }
            if ("UNIDAD".equals(currentScope) || "RESIDENTE".equals(currentCode) || "PORTERO".equals(currentCode)) {
                throw new AccessDeniedException("No permission to create assignments");
            }

            // Anti-spoofing: lock organization to context
            if (!"GLOBAL".equals(currentScope)) {
                Long orgId = ctx.getOrganizationId();
                if (orgId == null) {
                    throw new AccessDeniedException("No organization context available");
                }
                request.setIdOrganizacion(orgId);
            }
            if ("PROPIEDAD".equals(currentScope)) {
                request.setIdPropiedad(ctx.getPropertyId());
            }

            // BD-02: Strict tenant check for target property
            if ("PROPIEDAD".equals(targetRole.getAlcance()) && request.getIdPropiedad() != null) {
                PropertyDTO prop = propertyRepository.findById(request.getIdPropiedad())
                        .orElseThrow(() -> new AccessDeniedException("Cannot assign administrator to a property outside your organization"));
                if (!prop.getIdOrganizacion().equals(ctx.getOrganizationId())) {
                    throw new AccessDeniedException("Cannot assign administrator to a property outside your organization");
                }
            }
        }

        // Validate scope constraints from V3.9 CK_ROLES_ALCANCE
        switch (targetRole.getAlcance()) {
            case "GLOBAL":
                request.setIdOrganizacion(null);
                request.setIdPropiedad(null);
                request.setIdUnidad(null);
                break;
            case "ORGANIZACION":
                if (request.getIdOrganizacion() == null) {
                    throw new IllegalArgumentException("Organization ID required for ORGANIZACION scope");
                }
                request.setIdPropiedad(null);
                request.setIdUnidad(null);
                break;
            case "PROPIEDADES_SELECCIONADAS":
                if (request.getIdOrganizacion() == null) {
                    throw new IllegalArgumentException("Organization ID required for PROPIEDADES_SELECCIONADAS scope");
                }
                request.setIdPropiedad(null);
                request.setIdUnidad(null);
                break;
            case "PROPIEDAD":
                if (request.getIdOrganizacion() == null || request.getIdPropiedad() == null) {
                    throw new IllegalArgumentException("Organization and Property IDs required for PROPIEDAD scope");
                }
                request.setIdUnidad(null);
                break;
            case "UNIDAD":
                if (request.getIdOrganizacion() == null || request.getIdPropiedad() == null || request.getIdUnidad() == null) {
                    throw new IllegalArgumentException("Organization, Property and Unit IDs required for UNIDAD scope");
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown role scope: " + targetRole.getAlcance());
        }

        if (request.getIdOrganizacion() != null) {
            planLimitService.validateAndLockUserLimit(request.getIdOrganizacion(), request.getIdUsuario());
        }

        return assignmentRepository.create(request, ctx.getUserId());
    }

    @Auditable(action = "UPDATE_STATUS", resource = "ASIGNACION", category = AuditCategory.AUTHORIZATION, severity = AuditSeverity.CRITICAL)
    @Transactional
    public void updateStatus(Long id, String estado) {
        SaedContext ctx = SaedContextHolder.getContext();
        String currentCode = ctx.getRoleCode();
        String currentScope = ctx.getRoleScope();

        if (!"SUPERADMIN".equals(currentCode) && !"ADMIN_ORGANIZACION".equals(currentCode) && !"ADMIN_PROPIEDAD".equals(currentCode)) {
            throw new AccessDeniedException("No tiene permisos para modificar el estado de asignaciones");
        }

        if ("ADMIN_PROPIEDAD".equals(currentCode)) {
            java.util.List<java.util.Map<String, Object>> targetRows = jdbcTemplate.queryForList(
                "SELECT ua.ID_PROPIEDAD, ua.ID_USUARIO, r.CODIGO AS ROL_CODIGO FROM USUARIO_ASIGNACIONES ua JOIN ROLES r ON r.ID_ROL = ua.ID_ROL WHERE ua.ID_ASIGNACION = :id",
                java.util.Map.of("id", id)
            );
            if (targetRows.isEmpty()) {
                throw new AccessDeniedException("Asignación no encontrada en el ámbito de su propiedad");
            }
            java.util.Map<String, Object> target = targetRows.get(0);
            Number targetProp = (Number) target.get("ID_PROPIEDAD");
            String targetRole = (String) target.get("ROL_CODIGO");
            if (targetProp == null || ctx.getPropertyId() == null || !ctx.getPropertyId().equals(targetProp.longValue())) {
                throw new AccessDeniedException("No tiene permisos para modificar asignaciones fuera de la propiedad activa");
            }
            if ("SUPERADMIN".equals(targetRole) || "ADMIN_ORGANIZACION".equals(targetRole) || "ADMIN_PROPIEDAD".equals(targetRole)) {
                throw new AccessDeniedException("Un administrador de propiedad no puede modificar asignaciones administrativas");
            }
        }

        if ("ACTIVA".equalsIgnoreCase(estado) || "ACTIVO".equalsIgnoreCase(estado)) {
            java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT ID_ORGANIZACION, ID_USUARIO, ESTADO FROM USUARIO_ASIGNACIONES WHERE ID_ASIGNACION = :id",
                    java.util.Map.of("id", id)
            );
            if (!rows.isEmpty()) {
                java.util.Map<String, Object> row = rows.get(0);
                String currentEstado = (String) row.get("ESTADO");
                Number orgIdNum = (Number) row.get("ID_ORGANIZACION");
                Number userIdNum = (Number) row.get("ID_USUARIO");
                if (orgIdNum != null && !"ACTIVA".equalsIgnoreCase(currentEstado) && !"ACTIVO".equalsIgnoreCase(currentEstado)) {
                    planLimitService.validateAndLockUserLimit(orgIdNum.longValue(), userIdNum != null ? userIdNum.longValue() : null);
                }
            }
        }

        assignmentRepository.updateStatus(id, estado);
    }

    public java.util.List<java.util.Map<String, Object>> listAssignments() {
        SaedContext ctx = SaedContextHolder.getContext();
        String roleCode = ctx != null ? ctx.getRoleCode() : "";
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        Long propId = ctx != null ? ctx.getPropertyId() : null;

        StringBuilder sql = new StringBuilder("""
            SELECT ua.ID_ASIGNACION,
                   ua.ID_USUARIO,
                   u.NOMBRE_USUARIO,
                   u.EMAIL,
                   u.ESTADO AS USUARIO_ESTADO,
                   p.ID_PERSONA,
                   TRIM(p.PRIMER_NOMBRE || ' ' || COALESCE(p.SEGUNDO_NOMBRE, '') || ' ' ||
                        p.PRIMER_APELLIDO || ' ' || COALESCE(p.SEGUNDO_APELLIDO, '')) AS NOMBRE_COMPLETO,
                   p.NUMERO_DOCUMENTO,
                   r.ID_ROL,
                   r.CODIGO AS ROL_CODIGO,
                   r.NOMBRE AS ROL_NOMBRE,
                   r.ALCANCE AS ROL_ALCANCE,
                   ua.ID_ORGANIZACION,
                   o.NOMBRE AS ORGANIZACION_NOMBRE,
                   ua.ID_PROPIEDAD,
                   prop.NOMBRE AS PROPIEDAD_NOMBRE,
                   ua.ID_UNIDAD,
                   un.IDENTIFICADOR AS UNIDAD_IDENTIFICADOR,
                   ua.ESTADO,
                   ua.FECHA_INICIO,
                   ua.FECHA_FIN
            FROM USUARIO_ASIGNACIONES ua
            JOIN USUARIOS u ON u.ID_USUARIO = ua.ID_USUARIO
            LEFT JOIN PERSONAS p ON p.ID_PERSONA = u.ID_PERSONA
            JOIN ROLES r ON r.ID_ROL = ua.ID_ROL
            LEFT JOIN ORGANIZACIONES o ON o.ID_ORGANIZACION = ua.ID_ORGANIZACION
            LEFT JOIN PROPIEDADES prop ON prop.ID_PROPIEDAD = ua.ID_PROPIEDAD
            LEFT JOIN UNIDADES un ON un.ID_UNIDAD = ua.ID_UNIDAD
            WHERE 1=1
        """);

        org.springframework.jdbc.core.namedparam.MapSqlParameterSource params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource();

        if ("ADMIN_PROPIEDAD".equals(roleCode)) {
            if (propId != null) {
                sql.append(" AND ua.ID_PROPIEDAD = :propId");
                params.addValue("propId", propId);
            } else {
                sql.append(" AND 1=0");
            }
        } else if ("ADMIN_ORGANIZACION".equals(roleCode)) {
            if (orgId != null) {
                sql.append(" AND ua.ID_ORGANIZACION = :orgId");
                params.addValue("orgId", orgId);
            } else {
                sql.append(" AND 1=0");
            }
        } else if (!"SUPERADMIN".equals(roleCode)) {
            Long userId = ctx != null ? ctx.getUserId() : null;
            if (userId != null) {
                sql.append(" AND ua.ID_USUARIO = :userId");
                params.addValue("userId", userId);
            } else {
                sql.append(" AND 1=0");
            }
        }

        sql.append(" ORDER BY ua.ID_ASIGNACION DESC");

        java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> r : rows) {
            java.util.Map<String, Object> item = new java.util.HashMap<>(r);
            item.put("idAsignacion", r.get("ID_ASIGNACION"));
            item.put("idUsuario", r.get("ID_USUARIO"));
            item.put("nombreUsuario", r.get("NOMBRE_USUARIO"));
            item.put("email", r.get("EMAIL"));
            item.put("nombreCompleto", r.get("NOMBRE_COMPLETO"));
            item.put("numeroDocumento", r.get("NUMERO_DOCUMENTO"));
            item.put("idRol", r.get("ID_ROL"));
            item.put("rolCodigo", r.get("ROL_CODIGO"));
            item.put("rolNombre", r.get("ROL_NOMBRE"));
            item.put("rolAlcance", r.get("ROL_ALCANCE"));
            item.put("idOrganizacion", r.get("ID_ORGANIZACION"));
            item.put("organizacionNombre", r.get("ORGANIZACION_NOMBRE"));
            item.put("idPropiedad", r.get("ID_PROPIEDAD"));
            item.put("propiedadNombre", r.get("PROPIEDAD_NOMBRE"));
            item.put("idUnidad", r.get("ID_UNIDAD"));
            item.put("unidadIdentificador", r.get("UNIDAD_IDENTIFICADOR"));
            item.put("estado", r.get("ESTADO"));
            item.put("fechaInicio", r.get("FECHA_INICIO"));
            item.put("fechaFin", r.get("FECHA_FIN"));
            result.add(item);
        }
        return result;
    }
}
