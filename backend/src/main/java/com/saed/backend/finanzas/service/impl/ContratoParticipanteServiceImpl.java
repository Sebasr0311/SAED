package com.saed.backend.finanzas.service.impl;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.CoarrendatarioCreateDTO;
import com.saed.backend.finanzas.dto.CoarrendatarioDTO;
import com.saed.backend.finanzas.repository.CoarrendatarioRepository;
import com.saed.backend.finanzas.service.ContratoParticipanteService;
import com.saed.backend.person.service.ConvivienteQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class ContratoParticipanteServiceImpl implements ContratoParticipanteService {

    private static final Logger log = LoggerFactory.getLogger(ContratoParticipanteServiceImpl.class);

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

    private void validarAccesoContrato(Long idContrato) {
        if (idContrato == null) {
            throw new IllegalArgumentException("El ID de contrato no puede ser nulo");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        List<Map<String, Object>> rows;
        try {
            setElevatedContext();
            rows = jdbcTemplate.queryForList(
                "SELECT c.ID_CONTRATO, c.ID_UNIDAD, c.ESTADO, u.ID_PROPIEDAD, p.ID_ORGANIZACION " +
                "FROM CONTRATOS c " +
                "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
                "JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD " +
                "WHERE c.ID_CONTRATO = :id",
                Map.of("id", idContrato)
            );
        } finally {
            restoreSaedContext(ctx);
        }

        if (rows == null || rows.isEmpty()) {
            throw new NoSuchElementException("Contrato no encontrado: " + idContrato);
        }

        if (ctx != null) {
            String role = ctx.getRoleCode();
            Long contractPropId = ((Number) rows.get(0).get("ID_PROPIEDAD")).longValue();
            Long contractOrgId = ((Number) rows.get(0).get("ID_ORGANIZACION")).longValue();

            if ("SUPERADMIN".equalsIgnoreCase(role)) {
                // Permitido
            } else if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role)) {
                if (ctx.getOrganizationId() != null && !ctx.getOrganizationId().equals(contractOrgId)) {
                    throw new AccessDeniedException("No tiene permisos sobre contratos de otra organización");
                }
            } else if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
                if (ctx.getPropertyId() != null && !ctx.getPropertyId().equals(contractPropId)) {
                    throw new AccessDeniedException("No tiene permisos sobre contratos de otra propiedad");
                }
            } else {
                throw new AccessDeniedException("Rol no autorizado para gestionar participantes de contrato");
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CoarrendatarioDTO> listarCoarrendatarios(Long idContrato) {
        validarAccesoContrato(idContrato);
        SaedContext ctx = SaedContextHolder.getContext();
        try {
            setElevatedContext();
            return repository.listarPorContrato(idContrato);
        } finally {
            restoreSaedContext(ctx);
        }
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

        validarAccesoContrato(request.idContrato());

        SaedContext ctx = SaedContextHolder.getContext();

        // 1. Validar que la persona existe
        Integer countPersona;
        try {
            setElevatedContext();
            countPersona = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = :pId",
                Map.of("pId", request.idPersona()),
                Integer.class
            );
        } finally {
            restoreSaedContext(ctx);
        }
        if (countPersona == null || countPersona == 0) {
            throw new IllegalArgumentException("Persona no encontrada con ID: " + request.idPersona());
        }

        // 2. Validar que la persona no es ya el arrendatario principal del contrato
        Long idArrendatarioPrincipal;
        Long idUnidad;
        try {
            setElevatedContext();
            Map<String, Object> contratoInfo = jdbcTemplate.queryForMap(
                "SELECT ID_ARRENDATARIO_PRINCIPAL, ID_UNIDAD FROM CONTRATOS WHERE ID_CONTRATO = :id",
                Map.of("id", request.idContrato())
            );
            idArrendatarioPrincipal = ((Number) contratoInfo.get("ID_ARRENDATARIO_PRINCIPAL")).longValue();
            idUnidad = ((Number) contratoInfo.get("ID_UNIDAD")).longValue();
        } finally {
            restoreSaedContext(ctx);
        }

        if (idArrendatarioPrincipal != null && idArrendatarioPrincipal.equals(request.idPersona())) {
            throw new IllegalArgumentException("La persona ya es el arrendatario principal de este contrato");
        }

        // 3. Validar que no esté duplicado en CONTRATO_RESIDENTE
        try {
            setElevatedContext();
            if (repository.existeEnContrato(request.idContrato(), request.idPersona())) {
                throw new IllegalStateException("La persona ya se encuentra vinculada a este contrato");
            }
        } finally {
            restoreSaedContext(ctx);
        }

        // 4. Si requiere sincronización de habitabilidad, validar cupo de convivientes
        if (Boolean.TRUE.equals(request.sincronizarHabitabilidad())) {
            if (convivienteQuotaService != null) {
                convivienteQuotaService.validateAndLockQuota(idUnidad);
            }
        }

        try {
            setElevatedContext();
            return repository.crear(request);
        } finally {
            restoreSaedContext(ctx);
        }
    }

    @Override
    @Transactional
    public void actualizarEstadoCoarrendatario(Long idContratoResidente, String estado) {
        if (idContratoResidente == null) {
            throw new IllegalArgumentException("El ID de coarrendatario es obligatorio");
        }
        SaedContext ctx = SaedContextHolder.getContext();
        CoarrendatarioDTO dto;
        try {
            setElevatedContext();
            dto = repository.buscarPorId(idContratoResidente)
                    .orElseThrow(() -> new NoSuchElementException("Coarrendatario no encontrado: " + idContratoResidente));
        } finally {
            restoreSaedContext(ctx);
        }

        validarAccesoContrato(dto.idContrato());
        try {
            setElevatedContext();
            repository.actualizarEstado(idContratoResidente, estado);
        } finally {
            restoreSaedContext(ctx);
        }
    }

    @Override
    @Transactional
    public void eliminarCoarrendatario(Long idContratoResidente) {
        if (idContratoResidente == null) {
            throw new IllegalArgumentException("El ID de coarrendatario es obligatorio");
        }
        SaedContext ctx = SaedContextHolder.getContext();
        CoarrendatarioDTO dto;
        try {
            setElevatedContext();
            dto = repository.buscarPorId(idContratoResidente)
                    .orElseThrow(() -> new NoSuchElementException("Coarrendatario no encontrado: " + idContratoResidente));
        } finally {
            restoreSaedContext(ctx);
        }

        if ("ARRENDATARIO_PRINCIPAL".equalsIgnoreCase(dto.tipoVinculo())) {
            throw new IllegalArgumentException("No se puede eliminar al arrendatario principal desde este módulo");
        }

        validarAccesoContrato(dto.idContrato());
        try {
            setElevatedContext();
            repository.eliminar(idContratoResidente);
        } finally {
            restoreSaedContext(ctx);
        }
    }

    @Override
    @Transactional
    public void eliminarCoarrendatario(Long idContrato, Long idPersona) {
        if (idContrato == null || idPersona == null) {
            throw new IllegalArgumentException("idContrato e idPersona son obligatorios");
        }
        validarAccesoContrato(idContrato);
        SaedContext ctx = SaedContextHolder.getContext();
        try {
            setElevatedContext();
            repository.eliminarPorContratoYPersona(idContrato, idPersona);
        } finally {
            restoreSaedContext(ctx);
        }
    }

    private void setElevatedContext() {
        SaedContextHolder.setContext(SaedContext.builder()
                .userId(1L).organizationId(1L).propertyId(1L).roleCode("SUPERADMIN").roleScope("GLOBAL").build());
        try {
            jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
        } catch (Exception ignored) {}
    }

    private void restoreSaedContext(SaedContext ctx) {
        if (ctx != null) {
            SaedContextHolder.setContext(ctx);
        } else {
            SaedContextHolder.clearContext();
        }
        try {
            if (ctx == null || ctx.getUserId() == null) {
                jdbcTemplate.getJdbcOperations().execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
            } else {
                long u = ctx.getUserId();
                String o = ctx.getOrganizationId() != null ? String.valueOf(ctx.getOrganizationId()) : "NULL";
                String p = ctx.getPropertyId() != null ? String.valueOf(ctx.getPropertyId()) : "NULL";
                String r = ctx.getRoleCode() != null ? ctx.getRoleCode() : "ANONYMOUS";
                jdbcTemplate.getJdbcOperations().execute(
                    String.format("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(%d); PKG_SAED_SESSION.SET_CONTEXT(%d, %s, %s, '%s'); END;", u, u, o, p, r)
                );
            }
        } catch (Exception ignored) {}
    }
}
