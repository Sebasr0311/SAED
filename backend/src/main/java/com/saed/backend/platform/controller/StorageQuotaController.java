package com.saed.backend.platform.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.dto.StorageQuotaDTO;
import com.saed.backend.platform.service.StorageQuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Storage Quota", description = "Consulta de cuota y consumo de almacenamiento por organización")
@RestController
@RequestMapping("/api/v1/storage/quota")
public class StorageQuotaController {

    private final StorageQuotaService storageQuotaService;

    public StorageQuotaController(StorageQuotaService storageQuotaService) {
        this.storageQuotaService = storageQuotaService;
    }

    @Operation(summary = "Obtener cuota y métricas de almacenamiento de la organización")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<StorageQuotaDTO>> getStorageQuota(
            @RequestParam(value = "organizationId", required = false) Long requestedOrgId) {

        SaedContext ctx = SaedContextHolder.getContext();
        Long contextOrgId = ctx != null ? ctx.getOrganizationId() : null;
        String roleCode = ctx != null ? ctx.getRoleCode() : null;

        Long targetOrgId;

        // Anti-IDOR & Tenant Isolation: only SUPERADMIN can specify a target organizationId
        if ("SUPERADMIN".equalsIgnoreCase(roleCode)) {
            targetOrgId = requestedOrgId != null ? requestedOrgId : contextOrgId;
        } else {
            if (contextOrgId == null) {
                throw new AccessDeniedException("Identificador de organización no disponible en el contexto de seguridad");
            }
            if (requestedOrgId != null && !requestedOrgId.equals(contextOrgId)) {
                throw new AccessDeniedException("No está autorizado para consultar la cuota de almacenamiento de otra organización");
            }
            targetOrgId = contextOrgId;
        }

        if (targetOrgId == null) {
            throw new AccessDeniedException("Identificador de organización requerido");
        }

        StorageQuotaDTO quota = storageQuotaService.getQuota(targetOrgId);
        return ResponseEntity.ok(ApiResponse.success(quota));
    }
}
