package com.saed.backend.finanzas.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.common.service.TemplateRenderService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.math.BigDecimal;

@Service
public class FinanzasServiceImpl implements FinanzasService {
    private static final Logger log = LoggerFactory.getLogger(FinanzasServiceImpl.class);

    private final FinanzasRepository finanzasRepository;
    private final PdfService pdfService;
    private final EmailService emailService;
    private final TemplateRenderService templateService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinanzasServiceImpl(FinanzasRepository finanzasRepository, PdfService pdfService, EmailService emailService, TemplateRenderService templateService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.finanzasRepository = finanzasRepository;
        this.pdfService = pdfService;
        this.emailService = emailService;
        this.templateService = templateService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ContratoDTO> getContratos() {
        return finanzasRepository.getContratos();
    }

    @Override
    @Transactional
    public Long createContrato(ContratoRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de contrato no puede ser nula");
        }
        if (request.idApartamento() == null) {
            throw new IllegalArgumentException("El ID de apartamento es obligatorio");
        }
        if (request.idResidente() == null) {
            throw new IllegalArgumentException("El ID de residente es obligatorio");
        }
        if (request.canonMensual() == null || request.canonMensual().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El canon mensual debe ser mayor a cero");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role) || "PORTERO".equalsIgnoreCase(role)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para crear contratos de arrendamiento");
            }
        }

        // 1. Validar unidad y resolver propiedad / organización
        List<Map<String, Object>> unitInfo = null;
        try {
            setElevatedContext();
            unitInfo = jdbcTemplate.queryForList(
                "SELECT u.ID_PROPIEDAD, p.ID_ORGANIZACION FROM UNIDADES u " +
                "JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD " +
                "WHERE u.ID_UNIDAD = :unitId",
                new MapSqlParameterSource("unitId", request.idApartamento())
            );
        } finally {
            restoreSaedContext(ctx);
        }

        if (unitInfo == null || unitInfo.isEmpty()) {
            throw new IllegalArgumentException("Unidad no encontrada: " + request.idApartamento());
        }
        Long unitPropId = ((Number) unitInfo.get(0).get("ID_PROPIEDAD")).longValue();
        Long unitOrgId = ((Number) unitInfo.get(0).get("ID_ORGANIZACION")).longValue();

        // 2. Control de acceso por propiedad / organización (Aislamiento IDOR)
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role) && ctx.getPropertyId() != null && !ctx.getPropertyId().equals(unitPropId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para gestionar contratos en otra propiedad");
            }
            if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role) && ctx.getOrganizationId() != null && !ctx.getOrganizationId().equals(unitOrgId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para gestionar contratos en otra organización");
            }
        }

        // 3. Validar plantilla contractual organizacional y aislamiento IDOR de plantilla
        Integer countPlantillasActivas = null;
        try {
            setElevatedContext();
            countPlantillasActivas = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM PLANTILLAS_CONTRATOS WHERE ID_ORGANIZACION = :orgId AND ESTADO = 'ACTIVA'",
                new MapSqlParameterSource("orgId", unitOrgId),
                Integer.class
            );
        } finally {
            restoreSaedContext(ctx);
        }

        if (countPlantillasActivas != null && countPlantillasActivas > 0 && request.idPlantilla() == null) {
            throw new IllegalArgumentException("La organización requiere el uso de una plantilla de contrato activa");
        }

        if (request.idPlantilla() != null) {
            List<Map<String, Object>> templateInfo = null;
            try {
                setElevatedContext();
                templateInfo = jdbcTemplate.queryForList(
                    "SELECT ID_ORGANIZACION, ESTADO FROM PLANTILLAS_CONTRATOS WHERE ID_PLANTILLA = :tplId",
                    new MapSqlParameterSource("tplId", request.idPlantilla())
                );
            } finally {
                restoreSaedContext(ctx);
            }

            if (templateInfo == null || templateInfo.isEmpty()) {
                throw new IllegalArgumentException("Plantilla de contrato no encontrada");
            }
            Long tplOrgId = ((Number) templateInfo.get(0).get("ID_ORGANIZACION")).longValue();
            String tplEstado = (String) templateInfo.get(0).get("ESTADO");

            if (!unitOrgId.equals(tplOrgId)) {
                throw new org.springframework.security.access.AccessDeniedException("La plantilla de contrato pertenece a otra organización");
            }
            if (!"ACTIVA".equalsIgnoreCase(tplEstado)) {
                throw new IllegalArgumentException("La plantilla de contrato seleccionada no está activa");
            }
        }

        // 4. Validar existencia de la persona arrendataria
        Integer countPersona = 0;
        try {
            setElevatedContext();
            countPersona = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = :pId",
                new MapSqlParameterSource("pId", request.idResidente()),
                Integer.class
            );
        } finally {
            restoreSaedContext(ctx);
        }
        if (countPersona == null || countPersona == 0) {
            throw new IllegalArgumentException("Persona arrendataria no encontrada");
        }

        // 5. Validar que la unidad no tenga ya un contrato activo
        Integer activeContractCount = null;
        try {
            setElevatedContext();
            activeContractCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM CONTRATOS WHERE ID_UNIDAD = :unitId AND ESTADO = 'ACTIVO'",
                new MapSqlParameterSource("unitId", request.idApartamento()),
                Integer.class
            );
        } finally {
            restoreSaedContext(ctx);
        }
        if (activeContractCount != null && activeContractCount > 0) {
            throw new IllegalStateException("La unidad ya cuenta con un contrato de arrendamiento activo");
        }

        String numContrato = "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long id = finanzasRepository.createContrato(request, numContrato);
        
        finanzasRepository.generarCuotasIniciales(id);

        try {
            ContratoDetalleDTO detalle = finanzasRepository.getContratoDetalle(id);
            if (detalle != null && detalle.getCorreoResidente() != null && !detalle.getCorreoResidente().isBlank()) {
                String html = templateService.renderizar(detalle.getTipoContrato(), detalle);
                html = templateService.validarYLimpiarHtml(html);
                byte[] pdf = pdfService.generarPdf(html);
                emailService.enviarEmailContrato(detalle.getCorreoResidente(), detalle, pdf, "Contrato_" + numContrato + ".pdf");
            }
        } catch (Exception e) {
            log.error("Error generando PDF/Email para contrato", e);
        }
        
        return id;
    }

    @Override
    @Transactional
    public void actualizarEstadoContrato(Long id, String estado) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role) || "PORTERO".equalsIgnoreCase(role)) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para modificar contratos");
            }
            if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role) && ctx.getPropertyId() != null) {
                List<Long> propList = jdbcTemplate.queryForList(
                    "SELECT u.ID_PROPIEDAD FROM CONTRATOS c JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD WHERE c.ID_CONTRATO = :id",
                    new MapSqlParameterSource("id", id), Long.class);
                if (!propList.isEmpty() && !ctx.getPropertyId().equals(propList.get(0))) {
                    throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para modificar contratos de otra propiedad");
                }
            } else if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role) && ctx.getOrganizationId() != null) {
                List<Long> orgList = jdbcTemplate.queryForList(
                    "SELECT pr.ID_ORGANIZACION FROM CONTRATOS c JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD WHERE c.ID_CONTRATO = :id",
                    new MapSqlParameterSource("id", id), Long.class);
                if (!orgList.isEmpty() && !ctx.getOrganizationId().equals(orgList.get(0))) {
                    throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para modificar contratos de otra organización");
                }
            }
        }

        if ("ACTIVO".equalsIgnoreCase(estado)) {
            Integer otherActive = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM CONTRATOS c1 JOIN CONTRATOS c2 ON c1.ID_UNIDAD = c2.ID_UNIDAD " +
                "WHERE c1.ID_CONTRATO = :id AND c2.ID_CONTRATO != :id AND c2.ESTADO = 'ACTIVO'",
                new MapSqlParameterSource("id", id), Integer.class);
            if (otherActive != null && otherActive > 0) {
                throw new IllegalStateException("La unidad ya cuenta con otro contrato activo");
            }
        }

        finanzasRepository.updateEstadoContrato(id, estado);

        if ("CANCELADO".equalsIgnoreCase(estado) || "TERMINADO".equalsIgnoreCase(estado) || "INACTIVO".equalsIgnoreCase(estado)) {
            jdbcTemplate.update(
                "UPDATE RESIDENTES_UNIDAD SET ESTADO = 'INACTIVO' " +
                "WHERE ID_UNIDAD = (SELECT ID_UNIDAD FROM CONTRATOS WHERE ID_CONTRATO = :id) " +
                "  AND ID_PERSONA = (SELECT ID_ARRENDATARIO_PRINCIPAL FROM CONTRATOS WHERE ID_CONTRATO = :id) " +
                "  AND TIPO_RESIDENTE = 'ARRENDATARIO'",
                new MapSqlParameterSource("id", id)
            );
        } else if ("ACTIVO".equalsIgnoreCase(estado)) {
            jdbcTemplate.update(
                "UPDATE RESIDENTES_UNIDAD SET ESTADO = 'ACTIVO' " +
                "WHERE ID_UNIDAD = (SELECT ID_UNIDAD FROM CONTRATOS WHERE ID_CONTRATO = :id) " +
                "  AND ID_PERSONA = (SELECT ID_ARRENDATARIO_PRINCIPAL FROM CONTRATOS WHERE ID_CONTRATO = :id) " +
                "  AND TIPO_RESIDENTE = 'ARRENDATARIO'",
                new MapSqlParameterSource("id", id)
            );
        }
    }

    @Override
    public List<CuotaDTO> getCuotasPendientes() {
        return finanzasRepository.getCuotasPendientes();
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "PAGO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.CRITICAL)
    public void registrarPago(PagoRequestDTO request) {
        List<CuotaDTO> pendientes = finanzasRepository.getCuotasPendientes();
        CuotaDTO cuota = pendientes.stream().filter(c -> c.id().equals(request.idCuota())).findFirst()
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada o ya pagada"));
                
        finanzasRepository.registrarPago(request, cuota.idUnidad());
        finanzasRepository.actualizarSaldoCuota(request.idCuota(), request.valorPagado());

        // Enviar recibo por email (non-blocking)
        try {
            List<Map<String, Object>> residentes = jdbcTemplate.queryForList(
                "SELECT P.EMAIL FROM PERSONAS P " +
                "JOIN RESIDENTES_UNIDAD RU ON RU.ID_PERSONA = P.ID_PERSONA " +
                "WHERE RU.ID_UNIDAD = :u AND P.EMAIL IS NOT NULL",
                new MapSqlParameterSource("u", cuota.idUnidad()));
            if (!residentes.isEmpty()) {
                String destinatario = (String) residentes.get(0).get("EMAIL");
                String referencia = "PAGO-" + request.idCuota();
                emailService.enviarReciboPago(destinatario, cuota.concepto(), cuota.valorTotal(), referencia, LocalDate.now().toString());
            }
        } catch (Exception e) {
            log.error("Error enviando recibo de pago", e);
        }
    }

    @Override
    public ResidenteDashboardDTO getDashboardResidente(Long idResidente) {
        SaedContext ctx = SaedContextHolder.getContext();
        String role = ctx.getRoleCode();
        if ("RESIDENTE".equals(role) || "UNIDAD".equals(ctx.getRoleScope())) {
            Long userId = ctx.getUserId();
            if (userId != null) {
                try {
                    Long myPersonaId = jdbcTemplate.queryForObject(
                        "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                        new MapSqlParameterSource("u", userId), Long.class);
                    if (myPersonaId != null && !myPersonaId.equals(idResidente)) {
                        throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para ver las finanzas de otro residente");
                    }
                } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {}
            }
        }

        List<CuotaDTO> cuotas = finanzasRepository.getCuotasByResidente(idResidente);
        return new ResidenteDashboardDTO(idResidente, cuotas);
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
