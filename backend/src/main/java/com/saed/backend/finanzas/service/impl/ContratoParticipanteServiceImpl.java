package com.saed.backend.finanzas.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.CoarrendatarioCreateDTO;
import com.saed.backend.finanzas.dto.CoarrendatarioDTO;
import com.saed.backend.finanzas.repository.CoarrendatarioRepository;
import com.saed.backend.finanzas.service.ContratoParticipanteService;
import com.saed.backend.person.service.ConvivienteQuotaService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class ContratoParticipanteServiceImpl implements ContratoParticipanteService {

    private static final String NOT_FOUND = "Contrato no encontrado";

    private final CoarrendatarioRepository repository;
    private final ConvivienteQuotaService convivienteQuotaService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ContratoParticipanteServiceImpl(CoarrendatarioRepository repository,
                                           ConvivienteQuotaService convivienteQuotaService,
                                           NamedParameterJdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.convivienteQuotaService = convivienteQuotaService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Verifies, in the caller's own database context, that the contract belongs to the caller's property or
     * organization. A missing contract and a contract of another tenant are indistinguishable (404). Returns the
     * contract's organization.
     */
    private Long validarAccesoContrato(Long idContrato) {
        if (idContrato == null) {
            throw new IllegalArgumentException("El ID de contrato no puede ser nulo");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new AccessDeniedException("Se requiere un usuario autenticado");
        }
        String role = ctx.getRoleCode();
        if ("SUPERADMIN".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("La administracion de plataforma no accede a contratos de arrendamiento");
        }
        boolean orgAdmin = "ADMIN_ORGANIZACION".equalsIgnoreCase(role);
        boolean propAdmin = "ADMIN_PROPIEDAD".equalsIgnoreCase(role);
        if (!orgAdmin && !propAdmin) {
            throw new AccessDeniedException("Rol no autorizado para gestionar participantes de contrato");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT c.ID_CONTRATO, c.ID_UNIDAD, c.ESTADO, u.ID_PROPIEDAD, p.ID_ORGANIZACION " +
            "FROM CONTRATOS c " +
            "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
            "JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD " +
            "WHERE c.ID_CONTRATO = :id",
            Map.of("id", idContrato)
        );
        if (rows == null || rows.isEmpty()) {
            throw new NoSuchElementException(NOT_FOUND);
        }
        Long contractPropId = ((Number) rows.get(0).get("ID_PROPIEDAD")).longValue();
        Long contractOrgId = ((Number) rows.get(0).get("ID_ORGANIZACION")).longValue();

        boolean inScope = orgAdmin
                ? ctx.getOrganizationId() != null && ctx.getOrganizationId().equals(contractOrgId)
                : ctx.getPropertyId() != null && ctx.getPropertyId().equals(contractPropId);
        if (!inScope) {
            throw new NoSuchElementException(NOT_FOUND);
        }
        return contractOrgId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoarrendatarioDTO> listarCoarrendatarios(Long idContrato) {
        validarAccesoContrato(idContrato);
        return repository.listarPorContrato(idContrato);
    }

    @Override
    @Transactional
    public CoarrendatarioDTO agregarCoarrendatario(CoarrendatarioCreateDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud no puede ser nula");
        }
        if (request.idContrato() == null) {
            throw new IllegalArgumentException("El ID de contrato es obligatorio");
        }
        if (request.idPersona() == null) {
            throw new IllegalArgumentException("El ID de persona es obligatorio");
        }

        Long contractOrgId = validarAccesoContrato(request.idContrato());

        // 1. The person must belong to the contract's organization. A foreign identity is reported as not found
        // (PERSONAS has no row-level policy, DEC-01), so ids cannot be probed.
        Integer countPersona = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = :pId AND ID_ORGANIZACION = :orgId",
            Map.of("pId", request.idPersona(), "orgId", contractOrgId),
            Integer.class
        );
        if (countPersona == null || countPersona == 0) {
            throw new NoSuchElementException("Persona no encontrada");
        }

        // 2. La persona no puede ser ya el arrendatario principal del contrato
        Map<String, Object> contratoInfo = jdbcTemplate.queryForMap(
            "SELECT ID_ARRENDATARIO_PRINCIPAL, ID_UNIDAD FROM CONTRATOS WHERE ID_CONTRATO = :id",
            Map.of("id", request.idContrato())
        );
        Object principal = contratoInfo.get("ID_ARRENDATARIO_PRINCIPAL");
        Long idUnidad = ((Number) contratoInfo.get("ID_UNIDAD")).longValue();
        if (principal != null && ((Number) principal).longValue() == request.idPersona()) {
            throw new IllegalArgumentException("La persona ya es el arrendatario principal de este contrato");
        }

        // 3. Sin duplicados en CONTRATO_RESIDENTE
        if (repository.existeEnContrato(request.idContrato(), request.idPersona())) {
            throw new IllegalStateException("La persona ya se encuentra vinculada a este contrato");
        }

        // 4. Si requiere sincronización de habitabilidad, validar cupo de convivientes
        if (Boolean.TRUE.equals(request.sincronizarHabitabilidad()) && convivienteQuotaService != null) {
            convivienteQuotaService.validateAndLockQuota(idUnidad);
        }

        return repository.crear(request);
    }

    @Override
    @Transactional
    public void actualizarEstadoCoarrendatario(Long idContratoResidente, String estado) {
        if (idContratoResidente == null) {
            throw new IllegalArgumentException("El ID de coarrendatario es obligatorio");
        }
        CoarrendatarioDTO dto = repository.buscarPorId(idContratoResidente)
                .orElseThrow(() -> new NoSuchElementException("Coarrendatario no encontrado"));
        validarAccesoContrato(dto.idContrato());
        repository.actualizarEstado(idContratoResidente, estado);
    }

    @Override
    @Transactional
    public void eliminarCoarrendatario(Long idContratoResidente) {
        if (idContratoResidente == null) {
            throw new IllegalArgumentException("El ID de coarrendatario es obligatorio");
        }
        CoarrendatarioDTO dto = repository.buscarPorId(idContratoResidente)
                .orElseThrow(() -> new NoSuchElementException("Coarrendatario no encontrado"));
        validarAccesoContrato(dto.idContrato());
        if ("ARRENDATARIO_PRINCIPAL".equalsIgnoreCase(dto.tipoVinculo())) {
            throw new IllegalArgumentException("No se puede eliminar al arrendatario principal desde este módulo");
        }
        repository.eliminar(idContratoResidente);
    }

    @Override
    @Transactional
    public void eliminarCoarrendatario(Long idContrato, Long idPersona) {
        if (idContrato == null || idPersona == null) {
            throw new IllegalArgumentException("idContrato e idPersona son obligatorios");
        }
        validarAccesoContrato(idContrato);
        repository.eliminarPorContratoYPersona(idContrato, idPersona);
    }
}
