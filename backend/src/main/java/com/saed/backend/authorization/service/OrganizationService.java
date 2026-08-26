package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.OrganizationDTO;
import com.saed.backend.authorization.dto.OrganizationRequestDTO;
import com.saed.backend.authorization.repository.OrganizationRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public Long create(OrganizationRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (!"SUPERADMIN".equals(ctx.getRoleCode()) && !"GLOBAL".equals(ctx.getRoleScope())) {
            throw new AccessDeniedException("Only GLOBAL scope admins can create organizations");
        }
        return organizationRepository.create(request, ctx.getUserId());
    }

    public OrganizationDTO findById(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Organization not found"));
    }

    public List<OrganizationDTO> findAll() {
        return organizationRepository.findAll();
    }

    @Transactional
    public void update(Long id, OrganizationRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (!"SUPERADMIN".equals(ctx.getRoleCode()) && !"GLOBAL".equals(ctx.getRoleScope())) {
            if (!id.equals(ctx.getOrganizationId())) {
                throw new AccessDeniedException("Cannot update another organization");
            }
        }
        organizationRepository.update(id, request);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (!"SUPERADMIN".equals(ctx.getRoleCode()) && !"GLOBAL".equals(ctx.getRoleScope())) {
            throw new AccessDeniedException("Only GLOBAL scope admins can change organization status");
        }
        organizationRepository.updateStatus(id, status);
    }
}
