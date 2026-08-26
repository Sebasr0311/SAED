package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyRequestDTO;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    @Transactional
    public Long create(PropertyRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        String scope = ctx.getRoleScope();
        if (!"SUPERADMIN".equals(ctx.getRoleCode()) && !"GLOBAL".equals(scope) && !"ORGANIZACION".equals(scope)) {
            throw new AccessDeniedException("No permission to create properties");
        }
        // Anti-spoofing: non-global users are locked to their organization
        if (!"GLOBAL".equals(scope) && !"SUPERADMIN".equals(ctx.getRoleCode())) {
            request.setIdOrganizacion(ctx.getOrganizationId());
        }
        return propertyRepository.create(request);
    }

    public PropertyDTO findById(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Property not found"));
    }

    public List<PropertyDTO> findAll() {
        return propertyRepository.findAll();
    }

    @Transactional
    public void update(Long id, PropertyRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        String scope = ctx.getRoleScope();
        if (!"SUPERADMIN".equals(ctx.getRoleCode()) && !"GLOBAL".equals(scope)) {
            request.setIdOrganizacion(ctx.getOrganizationId());
        }
        propertyRepository.update(id, request);
    }
}
