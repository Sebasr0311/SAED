package com.saed.backend.authorization.service;

import com.saed.backend.authorization.dto.UnitDTO;
import com.saed.backend.authorization.dto.UnitRequestDTO;
import com.saed.backend.authorization.repository.UnitRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saed.backend.authorization.dto.UnitCoefficientSummaryDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class UnitService {

    private static final int COEFFICIENT_SCALE = 4;

    private final UnitRepository unitRepository;
    private final com.saed.backend.platform.service.PlanLimitService planLimitService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UnitService(UnitRepository unitRepository,
                       com.saed.backend.platform.service.PlanLimitService planLimitService,
                       NamedParameterJdbcTemplate jdbcTemplate) {
        this.unitRepository = unitRepository;
        this.planLimitService = planLimitService;
        this.jdbcTemplate = jdbcTemplate;
    }

    private void validateBlockAndType(Long propertyId, Long idBloque, Long idTipoUnidad) {
        if (jdbcTemplate == null) return;
        if (idBloque != null) {
            String sql = "SELECT id_propiedad, estado FROM BLOQUES WHERE id_bloque = :idBloque";
            List<BlockValidationInfo> list = jdbcTemplate.query(
                    sql,
                    new MapSqlParameterSource("idBloque", idBloque),
                    (rs, rowNum) -> new BlockValidationInfo(rs.getLong("id_propiedad"), rs.getString("estado"))
            );
            if (list.isEmpty()) {
                throw new IllegalArgumentException("El bloque especificado no existe");
            }
            BlockValidationInfo info = list.get(0);
            if (propertyId != null && !propertyId.equals(info.propiedadId())) {
                throw new IllegalArgumentException("El bloque no pertenece a la propiedad de la unidad");
            }
            if (info.estado() != null && !"ACTIVO".equalsIgnoreCase(info.estado())) {
                throw new IllegalArgumentException("El bloque seleccionado no se encuentra activo");
            }
        }
        if (idTipoUnidad != null) {
            String sql = "SELECT COUNT(*) FROM TIPOS_UNIDAD WHERE id_tipo_unidad = :idTipoUnidad";
            Integer count = jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource("idTipoUnidad", idTipoUnidad),
                    Integer.class
            );
            if (count == null || count == 0) {
                throw new IllegalArgumentException("El tipo de unidad especificado no es válido");
            }
        }
    }

    @Transactional
    public Long create(UnitRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        String scope = ctx.getRoleScope();
        String roleCode = ctx.getRoleCode();

        if ("UNIDAD".equals(scope) || "RESIDENTE".equals(roleCode)
                || "RESIDENTE_CONVIVENCIA".equals(roleCode) || "PORTERO".equals(roleCode)) {
            throw new AccessDeniedException("No permission to create units");
        }

        // Anti-spoofing: lock to property context if not global/org scope
        if (!"GLOBAL".equals(scope) && !"SUPERADMIN".equals(roleCode) && !"ORGANIZACION".equals(scope)) {
            request.setIdPropiedad(ctx.getPropertyId());
        }

        if (request.getIdPropiedad() == null) {
            throw new IllegalArgumentException("Property ID required to create unit");
        }

        validateCoefficientRange(request.getCoeficienteCopropiedad());
        validateBlockAndType(request.getIdPropiedad(), request.getIdBloque(), request.getIdTipoUnidad());

        // Resolve owning organization for property and check tenant isolation
        Long propOrgId = planLimitService.getOrganizationIdForProperty(request.getIdPropiedad());
        if (!"GLOBAL".equals(scope) && !"SUPERADMIN".equals(roleCode)) {
            Long userOrgId = ctx.getOrganizationId();
            if (userOrgId != null && !userOrgId.equals(propOrgId)) {
                throw new AccessDeniedException("No puede crear unidades en una propiedad fuera de su organización");
            }
        }

        validateUniqueIdentifier(request, null);

        // Enforce plan limit with pessimistic lock on organization membership
        planLimitService.validateAndLockUnitLimit(propOrgId);

        validateCoefficientSum(request.getIdPropiedad(), request.getCoeficienteCopropiedad(), null);

        return unitRepository.create(request);
    }

    /** DEC-04: a coefficient is a fraction, 0 &lt; c &le; 1. */
    private static void validateCoefficientRange(BigDecimal coefficient) {
        if (coefficient == null) {
            return;
        }
        if (coefficient.signum() <= 0 || coefficient.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "El coeficiente de copropiedad debe ser una fracción mayor que 0 y menor o igual a 1");
        }
    }

    /**
     * DEC-04: the property sum may not exceed 1.0000 (4-decimal tolerance). The property row is locked so two
     * concurrent saves cannot both pass the check.
     */
    private void validateCoefficientSum(Long propertyId, BigDecimal coefficient, Long excludeUnitId) {
        if (coefficient == null) {
            return;
        }
        BigDecimal others = unitRepository.lockPropertyAndSumCoefficients(propertyId, excludeUnitId);
        BigDecimal total = (others == null ? BigDecimal.ZERO : others).add(coefficient)
                .setScale(COEFFICIENT_SCALE, RoundingMode.HALF_UP);
        if (total.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("La suma de coeficientes de la propiedad superaría 1.0000 (resultado: "
                    + total.toPlainString() + ")");
        }
    }

    private void validateUniqueIdentifier(UnitRequestDTO request, Long excludeUnitId) {
        if (request.getIdentificador() != null && unitRepository.existsIdentifier(request.getIdPropiedad(),
                request.getIdBloque(), request.getIdentificador(), excludeUnitId)) {
            throw new IllegalStateException("Ya existe una unidad con ese identificador en la misma propiedad y bloque");
        }
    }

    /** Coefficient census of a property; an incomplete sum is reported as a warning, not an error (DEC-04). */
    public UnitCoefficientSummaryDTO coefficientSummary(Long propertyId) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || propertyId == null) {
            throw new AccessDeniedException("Sin contexto de propiedad");
        }
        if ("ORGANIZACION".equals(ctx.getRoleScope())) {
            Long propOrgId = planLimitService.getOrganizationIdForProperty(propertyId);
            if (ctx.getOrganizationId() == null || !ctx.getOrganizationId().equals(propOrgId)) {
                throw new AccessDeniedException("Propiedad fuera de su organización");
            }
        } else if (!"PROPIEDAD".equals(ctx.getRoleScope()) || !propertyId.equals(ctx.getPropertyId())) {
            throw new AccessDeniedException("Propiedad fuera de su alcance");
        }
        UnitCoefficientSummaryDTO summary = unitRepository.coefficientSummary(propertyId);
        BigDecimal sum = summary.getSumaCoeficientes() == null ? BigDecimal.ZERO : summary.getSumaCoeficientes();
        sum = sum.setScale(COEFFICIENT_SCALE, RoundingMode.HALF_UP);
        summary.setSumaCoeficientes(sum);
        BigDecimal missing = BigDecimal.ONE.setScale(COEFFICIENT_SCALE).subtract(sum);
        summary.setFaltante(missing.signum() > 0 ? missing : BigDecimal.ZERO.setScale(COEFFICIENT_SCALE));
        summary.setCompleto(sum.compareTo(BigDecimal.ONE) == 0 && summary.getUnidadesSinCoeficiente() == 0);
        if (!summary.isCompleto()) {
            summary.setAdvertencia("Censo de coeficientes incompleto: la suma es " + sum.toPlainString()
                    + " de 1.0000 y " + summary.getUnidadesSinCoeficiente() + " unidad(es) no tienen coeficiente");
        }
        return summary;
    }

    public UnitDTO findById(Long id) {
        SaedContext ctx = SaedContextHolder.getContext();
        if ("RESIDENTE".equals(ctx.getRoleCode()) || "RESIDENTE_CONVIVENCIA".equals(ctx.getRoleCode()) || "UNIDAD".equals(ctx.getRoleScope())) {
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

    public List<UnitDTO> findAll(Long propertyId) {
        return unitRepository.findAll(propertyId);
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

        UnitDTO existing = findById(id);
        if (request.getIdPropiedad() == null) {
            request.setIdPropiedad(existing.getIdPropiedad());
        }

        validateCoefficientRange(request.getCoeficienteCopropiedad());
        validateBlockAndType(request.getIdPropiedad(), request.getIdBloque(), request.getIdTipoUnidad());
        validateUniqueIdentifier(request, id);
        validateCoefficientSum(request.getIdPropiedad(), request.getCoeficienteCopropiedad(), id);

        unitRepository.update(id, request);
    }

    private record BlockValidationInfo(Long propiedadId, String estado) {}
}
