package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.BlockDTO;
import com.saed.backend.authorization.dto.BlockRequestDTO;
import com.saed.backend.authorization.dto.BlockTreeDTO;
import com.saed.backend.authorization.repository.BlockRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.platform.service.PlanLimitService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BlockServiceImpl implements BlockService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "TORRE", "BLOQUE", "ETAPA", "MANZANA", "PISO", "SECTOR"
    );

    private final BlockRepository blockRepository;
    private final PlanLimitService planLimitService;

    public BlockServiceImpl(BlockRepository blockRepository, PlanLimitService planLimitService) {
        this.blockRepository = blockRepository;
        this.planLimitService = planLimitService;
    }

    private void validatePropertyAccess(Long propertyId) {
        if (propertyId == null) {
            throw new IllegalArgumentException("ID de propiedad requerido");
        }
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) return;

        String role = ctx.getRoleCode();
        String scope = ctx.getRoleScope();

        if ("SUPERADMIN".equals(role) || "GLOBAL".equals(scope)) {
            return;
        }

        if ("ORGANIZACION".equals(scope) || "ADMIN_ORGANIZACION".equals(role)) {
            Long userOrgId = ctx.getOrganizationId();
            if (userOrgId == null) {
                throw new AccessDeniedException("No tiene permisos sobre la propiedad indicada: organización no establecida");
            }
            Long propOrgId = planLimitService.getOrganizationIdForProperty(propertyId);
            if (propOrgId == null || !userOrgId.equals(propOrgId)) {
                throw new AccessDeniedException("No tiene permisos sobre la propiedad indicada");
            }
            return;
        }

        Long callerPropId = ctx.getPropertyId();
        if (callerPropId == null || !callerPropId.equals(propertyId)) {
            throw new AccessDeniedException("No tiene permisos sobre la propiedad indicada");
        }
    }

    private void validateBlockData(Long propertyId, Long id, BlockRequestDTO request) {
        if (request.getTipo() == null || !ALLOWED_TYPES.contains(request.getTipo().trim().toUpperCase())) {
            throw new IllegalArgumentException("Tipo de bloque inválido. Permitidos: " + ALLOWED_TYPES);
        }
        request.setTipo(request.getTipo().trim().toUpperCase());

        if (request.getCodigo() == null || request.getCodigo().trim().isEmpty()) {
            throw new IllegalArgumentException("El código del bloque es requerido");
        }

        Long padreId = request.getIdBloquePadre();
        if (padreId != null) {
            if (id != null && id.equals(padreId)) {
                throw new IllegalArgumentException("Un bloque no puede ser padre de sí mismo");
            }

            BlockDTO parentBlock = blockRepository.findById(padreId)
                    .orElseThrow(() -> new IllegalArgumentException("El bloque padre especificado no existe"));

            if (!propertyId.equals(parentBlock.getIdPropiedad())) {
                throw new IllegalArgumentException("El bloque padre debe pertenecer a la misma propiedad");
            }

            // Cycle detection & depth check
            Long curr = padreId;
            int depth = 1;
            while (curr != null) {
                if (id != null && curr.equals(id)) {
                    throw new IllegalArgumentException("La jerarquía no puede contener ciclos");
                }
                BlockDTO node = blockRepository.findById(curr).orElse(null);
                if (node == null) break;
                curr = node.getIdBloquePadre();
                depth++;
                if (depth > 5) {
                    throw new IllegalArgumentException("La profundidad de la jerarquía no puede superar los 5 niveles");
                }
            }
        }
    }

    @Override
    public List<BlockDTO> findByPropertyId(Long propertyId) {
        validatePropertyAccess(propertyId);
        return blockRepository.findByPropertyId(propertyId);
    }

    @Override
    public List<BlockTreeDTO> findTreeByPropertyId(Long propertyId) {
        validatePropertyAccess(propertyId);
        List<BlockDTO> all = blockRepository.findByPropertyId(propertyId);

        Map<Long, BlockTreeDTO> map = new LinkedHashMap<>();
        for (BlockDTO b : all) {
            map.put(b.getId(), new BlockTreeDTO(b));
        }

        List<BlockTreeDTO> roots = new ArrayList<>();
        for (BlockDTO b : all) {
            BlockTreeDTO node = map.get(b.getId());
            Long parentId = b.getIdBloquePadre();
            if (parentId == null || !map.containsKey(parentId)) {
                roots.add(node);
            } else {
                map.get(parentId).addChild(node);
            }
        }

        return roots;
    }

    @Override
    public BlockDTO findById(Long propertyId, Long id) {
        validatePropertyAccess(propertyId);
        BlockDTO dto = blockRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Bloque no encontrado"));
        if (!propertyId.equals(dto.getIdPropiedad())) {
            throw new AccessDeniedException("El bloque no pertenece a la propiedad especificada");
        }
        return dto;
    }

    @Override
    @Transactional
    public Long create(Long propertyId, BlockRequestDTO request) {
        validatePropertyAccess(propertyId);
        request.setIdPropiedad(propertyId);
        validateBlockData(propertyId, null, request);
        return blockRepository.create(request);
    }

    @Override
    @Transactional
    public void update(Long propertyId, Long id, BlockRequestDTO request) {
        validatePropertyAccess(propertyId);
        BlockDTO existing = findById(propertyId, id);
        request.setIdPropiedad(propertyId);
        validateBlockData(propertyId, existing.getId(), request);
        blockRepository.update(id, request);
    }

    @Override
    @Transactional
    public void updateStatus(Long propertyId, Long id, String estado) {
        validatePropertyAccess(propertyId);
        findById(propertyId, id);
        if (estado == null || (!estado.equalsIgnoreCase("ACTIVO") && !estado.equalsIgnoreCase("INACTIVO"))) {
            throw new IllegalArgumentException("Estado inválido. Debe ser ACTIVO o INACTIVO");
        }
        blockRepository.updateStatus(id, estado.toUpperCase());
    }

    @Override
    @Transactional
    public void delete(Long propertyId, Long id) {
        validatePropertyAccess(propertyId);
        findById(propertyId, id);

        int children = blockRepository.countChildren(id);
        if (children > 0) {
            throw new IllegalStateException("No se puede eliminar un bloque que contiene sub-bloques o pisos");
        }

        int units = blockRepository.countUnits(id);
        if (units > 0) {
            throw new IllegalStateException("No se puede eliminar un bloque con unidades asociadas");
        }

        blockRepository.delete(id);
    }
}
