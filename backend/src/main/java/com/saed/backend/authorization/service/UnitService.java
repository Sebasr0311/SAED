package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import com.saed.backend.authorization.repository.UnitRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UnitService {

    private final UnitRepository unitRepository;

    public UnitService(UnitRepository unitRepository) {
        this.unitRepository = unitRepository;
    }

    @Transactional
    public Long create(UnitRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        String scope = ctx.getRoleScope();
        if ("UNIDAD".equals(scope) || "RESIDENTE".equals(ctx.getRoleCode())) {
            throw new AccessDeniedException("No permission to create units");
        }
        // Anti-spoofing: lock to property context if not global/org scope
        if (!"GLOBAL".equals(scope) && !"SUPERADMIN".equals(ctx.getRoleCode()) && !"ORGANIZACION".equals(scope)) {
            request.setIdPropiedad(ctx.getPropertyId());
        }
        return unitRepository.create(request);
    }

    public UnitDTO findById(Long id) {
        SaedContext ctx = SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && !ctx.getUnitId().equals(id)) {
                throw new AccessDeniedException("No tiene permisos para consultar otra unidad");
            }
        }
        return unitRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Unit not found"));
    }

    public List<UnitDTO> findAll() {
        return unitRepository.findAll();
    }

    @Transactional
    public void update(Long id, UnitRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        String scope = ctx.getRoleScope();
        if ("UNIDAD".equals(scope) || "RESIDENTE".equals(ctx.getRoleCode())) {
            throw new AccessDeniedException("No permission to update units");
        }
        if (!"GLOBAL".equals(scope) && !"SUPERADMIN".equals(ctx.getRoleCode()) && !"ORGANIZACION".equals(scope)) {
            request.setIdPropiedad(ctx.getPropertyId());
        }
        unitRepository.update(id, request);
    }
}
