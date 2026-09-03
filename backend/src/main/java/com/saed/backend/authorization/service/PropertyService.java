package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.PropertyDTO;
import com.saed.backend.authorization.dto.PropertyRequestDTO;
import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saed.backend.common.exception.PlanLimitExceededException;
import java.util.Optional;
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
        String roleCode = ctx.getRoleCode();

        if (!"SUPERADMIN".equals(roleCode) && !"GLOBAL".equals(scope) && !"ORGANIZACION".equals(scope)) {
            throw new AccessDeniedException("No permission to create properties");
        }

        // Anti-spoofing: non-global users are locked to their authenticated organization
        if (!"GLOBAL".equals(scope) && !"SUPERADMIN".equals(roleCode)) {
            Long orgId = ctx.getOrganizationId();
            if (orgId == null) {
                throw new AccessDeniedException("No organization context found");
            }
            if (request.getIdOrganizacion() != null && !request.getIdOrganizacion().equals(orgId)) {
                throw new AccessDeniedException("No puede crear propiedades en otra organización");
            }
            request.setIdOrganizacion(orgId);

            // BD-01: Plan limit enforcement server-side
            Optional<Long> maxLimitOpt = propertyRepository.getPropertyLimit(orgId);
            if (maxLimitOpt.isPresent()) {
                long maxLimit = maxLimitOpt.get();
                long currentCount = propertyRepository.countByOrganization(orgId);
                if (currentCount >= maxLimit) {
                    throw new PlanLimitExceededException("PROPIEDADES", currentCount, maxLimit);
                }
            }
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

    @Transactional
    public void updateStatus(Long id, String estado) {
        propertyRepository.updateStatus(id, estado);
    }
}
