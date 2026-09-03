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

    public AssignmentManagementService(AssignmentRepository assignmentRepository,
                                       RoleRepository roleRepository,
                                       PropertyRepository propertyRepository) {
        this.assignmentRepository = assignmentRepository;
        this.roleRepository = roleRepository;
        this.propertyRepository = propertyRepository;
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
            if ("ORGANIZACION".equals(currentScope)) {
                if (!"PROPIEDAD".equals(targetRole.getAlcance()) && !"ORGANIZACION".equals(targetRole.getAlcance())) {
                    throw new AccessDeniedException("ADMIN_ORGANIZACION cannot assign role with scope " + targetRole.getAlcance());
                }
            }
            if ("PROPIEDAD".equals(currentScope) &&
                    ("GLOBAL".equals(targetRole.getAlcance()) || "ORGANIZACION".equals(targetRole.getAlcance()))) {
                throw new AccessDeniedException("Cannot assign higher scope than yours");
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

        assignmentRepository.updateStatus(id, estado);
    }
}
