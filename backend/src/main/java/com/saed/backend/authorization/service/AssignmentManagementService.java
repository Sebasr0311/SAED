package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.AssignmentRequestDTO;
import com.saed.backend.authorization.dto.RoleDTO;
import com.saed.backend.authorization.repository.AssignmentRepository;
import com.saed.backend.authorization.repository.RoleRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentManagementService {

    private final AssignmentRepository assignmentRepository;
    private final RoleRepository roleRepository;

    public AssignmentManagementService(AssignmentRepository assignmentRepository, RoleRepository roleRepository) {
        this.assignmentRepository = assignmentRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public Long create(AssignmentRequestDTO request) {
        RoleDTO targetRole = roleRepository.findById(request.getIdRol())
                .orElseThrow(() -> new IllegalArgumentException("Role not found or inactive"));

        SaedContext ctx = SaedContextHolder.getContext();
        String currentScope = ctx.getRoleScope();
        String currentCode = ctx.getRoleCode();

        if (!"SUPERADMIN".equals(currentCode)) {
            // Anti-privilege escalation
            if ("PROPIEDAD".equals(currentScope) &&
                    ("GLOBAL".equals(targetRole.getAlcance()) || "ORGANIZACION".equals(targetRole.getAlcance()))) {
                throw new AccessDeniedException("Cannot assign higher scope than yours");
            }
            if ("UNIDAD".equals(currentScope) || "RESIDENTE".equals(currentCode)) {
                throw new AccessDeniedException("No permission to create assignments");
            }

            // Anti-spoofing: lock organization to context
            if (!"GLOBAL".equals(currentScope)) {
                request.setIdOrganizacion(ctx.getOrganizationId());
            }
            if ("PROPIEDAD".equals(currentScope)) {
                request.setIdPropiedad(ctx.getPropertyId());
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

    @Transactional
    public void updateStatus(Long id, String estado) {
        assignmentRepository.updateStatus(id, estado);
    }
}
