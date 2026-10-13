package com.saed.backend.finanzas.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.common.service.PdfService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.common.service.TemplateRenderService;
import com.saed.backend.common.service.VariableResolverService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.exception.GatewayPaymentMethodNotAllowedException;
import com.saed.backend.finanzas.repository.FinanzasRepository;
import com.saed.backend.finanzas.service.AfterCommit;
import com.saed.backend.finanzas.service.FinanzasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saed.backend.person.service.ConvivienteQuotaService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class FinanzasServiceImpl implements FinanzasService {
    private static final Logger log = LoggerFactory.getLogger(FinanzasServiceImpl.class);

    private static final List<String> ESTADOS_CONTRATO_PERMITIDOS = List.of(
            "BORRADOR", "PENDIENTE_FIRMA", "ACTIVO", "VENCIDO", "TERMINADO_ANTICIPADO", "CANCELADO"
    );

    private final FinanzasRepository finanzasRepository;
    private final PdfService pdfService;
    private final EmailService emailService;
    private final TemplateRenderService templateService;
    private final VariableResolverService variableResolverService;
    private final FileStorageService fileStorageService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ConvivienteQuotaService convivienteQuotaService;

    public FinanzasServiceImpl(FinanzasRepository finanzasRepository,
                               PdfService pdfService,
                               EmailService emailService,
                               TemplateRenderService templateService,
                               VariableResolverService variableResolverService,
                               FileStorageService fileStorageService,
                               NamedParameterJdbcTemplate jdbcTemplate,
                               @org.springframework.beans.factory.annotation.Autowired(required = false)
                               ConvivienteQuotaService convivienteQuotaService) {
        this.finanzasRepository = finanzasRepository;
        this.pdfService = pdfService;
        this.emailService = emailService;
        this.templateService = templateService;
        this.variableResolverService = variableResolverService;
        this.fileStorageService = fileStorageService;
        this.jdbcTemplate = jdbcTemplate;
        this.convivienteQuotaService = convivienteQuotaService;
    }

    @Override
    public List<ContratoDTO> getContratos() {
        SaedContext ctx = SaedContextHolder.getContext();
        denyOperationalFinanceRead(ctx);
        if (ctx != null && "RESIDENTE".equalsIgnoreCase(ctx.getRoleCode())) {
            Long myPersonaId = resolvePersonaIdForUser(ctx.getUserId());
            Long unitId = ctx.getUnitId();
            return finanzasRepository.getContratos().stream()
                    .filter(c -> (unitId != null && unitId.equals(c.idUnidad())) || (myPersonaId != null && myPersonaId.equals(c.idArrendatario())))
                    .toList();
        }
        return finanzasRepository.getContratos();
    }

    @Override
    public ContratoDetalleDTO getContratoDetalle(Long idContrato) {
        SaedContext ctx = SaedContextHolder.getContext();
        denyOperationalFinanceRead(ctx);

        // Read under the caller's own tenant context (no privilege elevation): the database already hides
        // contracts of other tenants, and the checks below are defense in depth. Out-of-scope and missing
        // contracts are indistinguishable (404).
        ContratoDetalleDTO detalle = finanzasRepository.getContratoDetalle(idContrato);
        if (detalle == null) {
            throw new NoSuchElementException("Contrato no encontrado: " + idContrato);
        }

        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role)) {
                if (ctx.getOrganizationId() != null && !ctx.getOrganizationId().equals(detalle.getIdOrganizacion())) {
                    throw new NoSuchElementException("Contrato no encontrado: " + idContrato);
                }
            } else if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
                if (ctx.getPropertyId() != null && !ctx.getPropertyId().equals(detalle.getIdPropiedad())) {
                    throw new NoSuchElementException("Contrato no encontrado: " + idContrato);
                }
            } else if ("RESIDENTE".equalsIgnoreCase(role)) {
                boolean matchesUnit = ctx.getUnitId() != null && ctx.getUnitId().equals(detalle.getIdUnidad());
                Long myPersonaId = resolvePersonaIdForUser(ctx.getUserId());
                boolean matchesPersona = myPersonaId != null && myPersonaId.equals(detalle.getIdArrendatarioPrincipal());
                if (!matchesUnit && !matchesPersona) {
                    throw new NoSuchElementException("Contrato no encontrado: " + idContrato);
                }
            } else {
                throw new AccessDeniedException("Rol no autorizado para consultar contratos");
            }
        }

        return detalle;
    }

    @Override
    public Resource descargarPdfContrato(Long idContrato) {
        ContratoDetalleDTO detalle = getContratoDetalle(idContrato);
        if (detalle.getDocumentoUrl() == null || detalle.getDocumentoUrl().isBlank()) {
            throw new IllegalStateException("El documento PDF no ha sido generado para este contrato (DOCUMENTO_URL es nulo)");
        }

        return fileStorageService.loadAsResource(detalle.getDocumentoUrl());
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
                throw new AccessDeniedException("No tiene permisos para crear contratos de arrendamiento");
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
            throw new NoSuchElementException("Unidad no encontrada: " + request.idApartamento());
        }
        Long unitPropId = ((Number) unitInfo.get(0).get("ID_PROPIEDAD")).longValue();
        Long unitOrgId = ((Number) unitInfo.get(0).get("ID_ORGANIZACION")).longValue();

        // 2. Control de acceso por propiedad / organización (Aislamiento IDOR)
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role) && ctx.getPropertyId() != null && !ctx.getPropertyId().equals(unitPropId)) {
                throw new AccessDeniedException("No tiene permisos para gestionar contratos en otra propiedad");
            }
            if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role) && ctx.getOrganizationId() != null && !ctx.getOrganizationId().equals(unitOrgId)) {
                throw new AccessDeniedException("No tiene permisos para gestionar contratos en otra organización");
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

        String rawHtmlTemplate = null;
        if (request.idPlantilla() != null) {
            List<Map<String, Object>> templateInfo = null;
            try {
                setElevatedContext();
                templateInfo = jdbcTemplate.queryForList(
                    "SELECT ID_ORGANIZACION, ESTADO, CONTENIDO_HTML FROM PLANTILLAS_CONTRATOS WHERE ID_PLANTILLA = :tplId",
                    new MapSqlParameterSource("tplId", request.idPlantilla())
                );
            } finally {
                restoreSaedContext(ctx);
            }

            if (templateInfo == null || templateInfo.isEmpty()) {
                throw new NoSuchElementException("Plantilla de contrato no encontrada");
            }
            Long tplOrgId = ((Number) templateInfo.get(0).get("ID_ORGANIZACION")).longValue();
            String tplEstado = (String) templateInfo.get(0).get("ESTADO");

            if (!unitOrgId.equals(tplOrgId)) {
                throw new AccessDeniedException("La plantilla de contrato pertenece a otra organización");
            }
            if (!"ACTIVA".equalsIgnoreCase(tplEstado)) {
                throw new IllegalArgumentException("La plantilla de contrato seleccionada no está activa");
            }
            rawHtmlTemplate = (String) templateInfo.get(0).get("CONTENIDO_HTML");
        } else {
            try {
                rawHtmlTemplate = templateService.cargarPlantilla(request.tipoContrato());
            } catch (Exception e) {
                log.warn("No se pudo cargar plantilla de fallback del classpath: {}", e.getMessage());
                rawHtmlTemplate = "<h2>CONTRATO DE ARRENDAMIENTO</h2><p>Contrato entre ${propiedad.nombre} y ${residente.nombreCompleto}.</p>";
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
            throw new NoSuchElementException("Persona arrendataria no encontrada");
        }

        // 4b. Validar si el arrendatario es menor de edad y requerir tutor
        java.sql.Date fechaNac = null;
        try {
            setElevatedContext();
            List<Map<String, Object>> pRows = jdbcTemplate.queryForList(
                "SELECT FECHA_NACIMIENTO FROM PERSONAS WHERE ID_PERSONA = :pId",
                new MapSqlParameterSource("pId", request.idResidente())
            );
            if (!pRows.isEmpty() && pRows.get(0).get("FECHA_NACIMIENTO") != null) {
                Object fnObj = pRows.get(0).get("FECHA_NACIMIENTO");
                if (fnObj instanceof java.sql.Date d) fechaNac = d;
                else if (fnObj instanceof java.sql.Timestamp ts) fechaNac = new java.sql.Date(ts.getTime());
            }
        } finally {
            restoreSaedContext(ctx);
        }

        boolean esMenor = false;
        if (fechaNac != null) {
            LocalDate birth = fechaNac.toLocalDate();
            LocalDate now = LocalDate.now();
            int age = java.time.Period.between(birth, now).getYears();
            if (age < 18) {
                esMenor = true;
            }
        }

        if (esMenor && request.idTutor() == null) {
            throw new IllegalArgumentException("El arrendatario principal es menor de edad y requiere un tutor legal registrado.");
        }

        // 4c. Validar tutor si fue suministrado
        if (request.idTutor() != null) {
            if (request.idTutor().equals(request.idResidente())) {
                throw new IllegalArgumentException("El tutor legal no puede ser la misma persona que el arrendatario principal");
            }
            Integer countTutor = 0;
            try {
                setElevatedContext();
                countTutor = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = :tId",
                    new MapSqlParameterSource("tId", request.idTutor()),
                    Integer.class
                );
            } finally {
                restoreSaedContext(ctx);
            }
            if (countTutor == null || countTutor == 0) {
                throw new NoSuchElementException("Persona tutor no encontrada con ID: " + request.idTutor());
            }

            if (esMenor) {
                Integer countRelTutor = 0;
                try {
                    setElevatedContext();
                    countRelTutor = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM TUTORES WHERE ID_PERSONA_MENOR = :menorId AND ID_PERSONA_TUTOR = :tutorId AND ESTADO = 'ACTIVO'",
                        new MapSqlParameterSource("menorId", request.idResidente()).addValue("tutorId", request.idTutor()),
                        Integer.class
                    );
                } finally {
                    restoreSaedContext(ctx);
                }
                if (countRelTutor == null || countRelTutor == 0) {
                    throw new IllegalArgumentException("No existe una relación de tutoría legal activa entre el menor y el tutor seleccionado");
                }
            }
        }

        // 4d. Validar coarrendatarios si fueron suministrados
        if (request.coarrendatarios() != null && !request.coarrendatarios().isEmpty()) {
            Set<Long> vistos = new HashSet<>();
            boolean syncCualquiera = Boolean.TRUE.equals(request.sincronizarHabitabilidad());
            for (CoarrendatarioCreateDTO co : request.coarrendatarios()) {
                if (co.idPersona() == null) {
                    throw new IllegalArgumentException("El ID de persona de cada coarrendatario es obligatorio");
                }
                if (co.idPersona().equals(request.idResidente())) {
                    throw new IllegalArgumentException("Un coarrendatario no puede ser el mismo arrendatario principal");
                }
                if (!vistos.add(co.idPersona())) {
                    throw new IllegalArgumentException("Coarrendatario duplicado en la solicitud: ID " + co.idPersona());
                }
                Integer countCo = 0;
                try {
                    setElevatedContext();
                    countCo = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM PERSONAS WHERE ID_PERSONA = :coId",
                        new MapSqlParameterSource("coId", co.idPersona()),
                        Integer.class
                    );
                } finally {
                    restoreSaedContext(ctx);
                }
                if (countCo == null || countCo == 0) {
                    throw new NoSuchElementException("Persona coarrendataria no encontrada con ID: " + co.idPersona());
                }
                if (Boolean.TRUE.equals(co.sincronizarHabitabilidad())) {
                    syncCualquiera = true;
                }
            }

            if (syncCualquiera && convivienteQuotaService != null) {
                convivienteQuotaService.validateAndLockQuota(request.idApartamento());
            }
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

        // 6. Inserción del contrato en base de datos
        String numContrato = "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long id = finanzasRepository.createContrato(request, numContrato);
        finanzasRepository.generarCuotasIniciales(id);

        // 7. Pipeline Documental: Snapshot HTML -> PDF -> SHA-256 -> Storage -> Metadata
        FileStorageService.StoredFile storedFile = null;
        try {
            ContratoDetalleDTO detalle = null;
            try {
                setElevatedContext();
                detalle = finanzasRepository.getContratoDetalle(id);
            } finally {
                restoreSaedContext(ctx);
            }
            if (detalle != null) {
                Map<String, Object> variables = variableResolverService.construirMapaVariables(detalle);
                String htmlCongelado = variableResolverService.resolverVariables(rawHtmlTemplate, variables);
                htmlCongelado = templateService.validarYLimpiarHtml(htmlCongelado);
                byte[] pdfBytes = pdfService.generarPdf(htmlCongelado);

                // Persistir archivo físico a través de FileStorageService con validación de StorageQuota
                String filename = "contrato_" + numContrato + ".pdf";
                storedFile = fileStorageService.storeBytes(pdfBytes, filename, "application/pdf", "contratos", unitOrgId);

                // Persistir metadata documental en la tabla CONTRATOS
                finanzasRepository.actualizarDocumentoContrato(
                        id,
                        storedFile.relativePath(),
                        storedFile.sha256(),
                        storedFile.sizeBytes(),
                        htmlCongelado
                );

                // Envío de email (no bloqueante)
                if (detalle.getCorreoResidente() != null && !detalle.getCorreoResidente().isBlank()) {
                    try {
                        emailService.enviarEmailContrato(detalle.getCorreoResidente(), detalle, pdfBytes, filename);
                    } catch (Exception eMail) {
                        log.warn("No se pudo enviar correo de contrato {} a {}: {}", numContrato, detalle.getCorreoResidente(), eMail.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error en pipeline documental de contrato {}", id, e);
            // Requisito #13: Limpieza garantizada de archivos huérfanos ante fallo de persistencia
            if (storedFile != null) {
                try {
                    fileStorageService.delete(storedFile.relativePath());
                    log.info("Archivo huérfano limpiado exitosamente: {}", storedFile.relativePath());
                } catch (Exception ignored) {}
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Error al generar y almacenar documento de contrato", e);
        }

        return id;
    }

    @Override
    @Transactional
    public void actualizarEstadoContrato(Long id, String estado) {
        String estadoNorm = estado != null ? estado.trim().toUpperCase() : "";
        if (!ESTADOS_CONTRATO_PERMITIDOS.contains(estadoNorm)) {
            throw new IllegalArgumentException("Estado no permitido según restricción contractual: " + estado);
        }

        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role) || "PORTERO".equalsIgnoreCase(role)) {
                throw new AccessDeniedException("No tiene permisos para modificar contratos");
            }
            if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role) && ctx.getPropertyId() != null) {
                List<Long> propList = jdbcTemplate.queryForList(
                    "SELECT u.ID_PROPIEDAD FROM CONTRATOS c JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD WHERE c.ID_CONTRATO = :id",
                    new MapSqlParameterSource("id", id), Long.class);
                if (!propList.isEmpty() && !ctx.getPropertyId().equals(propList.get(0))) {
                    throw new AccessDeniedException("No tiene permisos para modificar contratos de otra propiedad");
                }
            } else if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role) && ctx.getOrganizationId() != null) {
                List<Long> orgList = jdbcTemplate.queryForList(
                    "SELECT pr.ID_ORGANIZACION FROM CONTRATOS c JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD JOIN PROPIEDADES pr ON u.ID_PROPIEDAD = pr.ID_PROPIEDAD WHERE c.ID_CONTRATO = :id",
                    new MapSqlParameterSource("id", id), Long.class);
                if (!orgList.isEmpty() && !ctx.getOrganizationId().equals(orgList.get(0))) {
                    throw new AccessDeniedException("No tiene permisos para modificar contratos de otra organización");
                }
            }
        }

        if ("ACTIVO".equalsIgnoreCase(estadoNorm)) {
            Integer otherActive = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM CONTRATOS c1 JOIN CONTRATOS c2 ON c1.ID_UNIDAD = c2.ID_UNIDAD " +
                "WHERE c1.ID_CONTRATO = :id AND c2.ID_CONTRATO != :id AND c2.ESTADO = 'ACTIVO'",
                new MapSqlParameterSource("id", id), Integer.class);
            if (otherActive != null && otherActive > 0) {
                throw new IllegalStateException("La unidad ya cuenta con otro contrato activo");
            }
        }

        finanzasRepository.updateEstadoContrato(id, estadoNorm);

        if ("CANCELADO".equalsIgnoreCase(estadoNorm) || "TERMINADO_ANTICIPADO".equalsIgnoreCase(estadoNorm)) {
            jdbcTemplate.update(
                "UPDATE RESIDENTES_UNIDAD SET ESTADO = 'INACTIVO' " +
                "WHERE ID_UNIDAD = (SELECT ID_UNIDAD FROM CONTRATOS WHERE ID_CONTRATO = :id) " +
                "  AND ID_PERSONA = (SELECT ID_ARRENDATARIO_PRINCIPAL FROM CONTRATOS WHERE ID_CONTRATO = :id) " +
                "  AND TIPO_RESIDENTE = 'ARRENDATARIO'",
                new MapSqlParameterSource("id", id)
            );
        } else if ("ACTIVO".equalsIgnoreCase(estadoNorm)) {
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
        denyOperationalFinanceRead(SaedContextHolder.getContext());
        return finanzasRepository.getCuotasPendientes();
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "PAGO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.CRITICAL)
    public void registrarPago(PagoRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de pago no puede ser nula");
        }
        if (request.idCuota() == null) {
            throw new IllegalArgumentException("El ID de cuota es obligatorio");
        }
        if (request.valorPagado() == null || request.valorPagado().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El valor pagado debe ser mayor a cero");
        }

        denyConvivencia(SaedContextHolder.getContext());

        // F06/C-09: a client-supplied gateway method must never approve a payment. Reject it
        // before any lookup or ledger write; gateway settlement is webhook-only.
        rechazarMetodoPasarela(request.metodoPago());

        CuotaDTO cuota = findCuotaById(request.idCuota());
        validarPermisoUnidadResidente(cuota.idUnidad());
        validarCuotaPagable(cuota, request.valorPagado());

        // Ordinary payments are ALWAYS registered as PENDIENTE_APROBACION: no fee debit,
        // no portfolio recalculation and no receipt until an administrator approves them.
        finanzasRepository.registrarPago(request, cuota.idUnidad(), "PENDIENTE_APROBACION", null, null);
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "PAGO_PASARELA", category = AuditCategory.FINANCIAL, severity = AuditSeverity.CRITICAL)
    public Long registrarPagoPasarelaVerificado(PagoRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de pago no puede ser nula");
        }
        if (request.idCuota() == null) {
            throw new IllegalArgumentException("El ID de cuota es obligatorio");
        }
        if (request.valorPagado() == null || request.valorPagado().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El valor pagado debe ser mayor a cero");
        }

        CuotaDTO cuota = findCuotaById(request.idCuota());
        validarPermisoUnidadResidente(cuota.idUnidad());

        // The provider already collected the money: the payment is always stored. A charge that
        // exceeds the outstanding balance (double charge: two intents, gateway plus manual payment)
        // is not rejected, it is flagged on the payment so it can be refunded (F6-08).
        Map<String, Object> locked = lockCuota(request.idCuota());
        BigDecimal saldo = saldoDe(locked);
        PagoRequestDTO toStore = request;
        if (request.valorPagado().compareTo(saldo) > 0 || !esCuotaPagable(estadoDe(locked))) {
            BigDecimal excess = request.valorPagado().subtract(saldo.max(BigDecimal.ZERO));
            log.warn("[Finanzas] Double charge detected on fee {}: paid {} with outstanding {}",
                    request.idCuota(), request.valorPagado(), saldo);
            toStore = new PagoRequestDTO(request.idCuota(), request.fechaPago(), request.valorPagado(),
                    request.metodoPago(), request.referencia(), request.comprobanteUrl(),
                    "SOBREPAGO: excedente " + excess.toPlainString() + " sobre saldo " + saldo.toPlainString(),
                    request.idempotencyKey());
        }

        // Single debit owner: the V5.17 trigger TRG_APLICAR_PAGO_CUOTA debits the fee when the
        // APROBADO payment detail is inserted. Java never debits CUOTAS (F06-03).
        Long idPago = finanzasRepository.registrarPago(toStore, cuota.idUnidad(), "APROBADO", null, null);
        finanzasRepository.recalcularCarteraUnidad(cuota.idUnidad());
        // The single receipt of a gateway payment belongs to the webhook, sent after commit.
        return idPago;
    }

    private static void rechazarMetodoPasarela(String metodoPago) {
        if (metodoPago == null) {
            return;
        }
        String normalized = metodoPago.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("PASARELA") || normalized.contains("WOMPI")) {
            throw new GatewayPaymentMethodNotAllowedException();
        }
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "PAGO_MANUAL", category = AuditCategory.FINANCIAL, severity = AuditSeverity.CRITICAL)
    public Long registrarPagoManual(PagoRequestDTO request, org.springframework.web.multipart.MultipartFile comprobante) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de pago no puede ser nula");
        }
        if (request.idCuota() == null) {
            throw new IllegalArgumentException("El ID de cuota es obligatorio");
        }
        if (request.valorPagado() == null || request.valorPagado().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El valor pagado debe ser mayor a cero");
        }
        denyConvivencia(SaedContextHolder.getContext());
        rechazarMetodoPasarela(request.metodoPago());

        CuotaDTO cuota = findCuotaById(request.idCuota());
        validarPermisoUnidadResidente(cuota.idUnidad());
        validarCuotaPagable(cuota, request.valorPagado());

        String compUrl = request.comprobanteUrl();
        String compHash = null;
        Long compSize = null;

        if (comprobante != null && !comprobante.isEmpty()) {
            FileStorageService.StoredFile stored = subirComprobante(comprobante);
            compUrl = stored.relativePath();
            compHash = stored.sha256();
            compSize = stored.sizeBytes();
        }

        PagoRequestDTO reqConUrl = new PagoRequestDTO(
            request.idCuota(),
            request.fechaPago() != null ? request.fechaPago() : LocalDate.now(),
            request.valorPagado(),
            request.metodoPago() != null ? request.metodoPago() : "TRANSFERENCIA",
            request.referencia(),
            compUrl,
            request.notas(),
            request.idempotencyKey()
        );

        return finanzasRepository.registrarPago(reqConUrl, cuota.idUnidad(), "PENDIENTE_APROBACION", compHash, compSize);
    }

    @Override
    public List<PagoResponseDTO> getPagos(String estado, Long idUnidad, LocalDate fechaDesde, LocalDate fechaHasta, String metodoPago) {
        SaedContext ctx = SaedContextHolder.getContext();
        denyOperationalFinanceRead(ctx);
        Long filterUnidad = idUnidad;
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("RESIDENTE".equalsIgnoreCase(role) || "UNIDAD".equalsIgnoreCase(ctx.getRoleScope())) {
                if (ctx.getUnitId() != null) {
                    filterUnidad = ctx.getUnitId();
                } else {
                    Long myPersonaId = resolvePersonaIdForUser(ctx.getUserId());
                    List<Long> unidades = jdbcTemplate.queryForList(
                        "SELECT ID_UNIDAD FROM RESIDENTES_UNIDAD WHERE ID_PERSONA = :p AND ESTADO = 'ACTIVA'",
                        new MapSqlParameterSource("p", myPersonaId), Long.class
                    );
                    if (!unidades.isEmpty()) {
                        filterUnidad = unidades.get(0);
                    }
                }
            }
        }
        return finanzasRepository.getPagos(estado, filterUnidad, fechaDesde, fechaHasta, metodoPago);
    }

    @Override
    public PagoResponseDTO getPagoDetalle(Long idPago) {
        denyOperationalFinanceRead(SaedContextHolder.getContext());
        PagoResponseDTO pago = finanzasRepository.getPagoById(idPago);
        if (pago == null) {
            throw new NoSuchElementException("Pago no encontrado: " + idPago);
        }
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("RESIDENTE".equalsIgnoreCase(role) || "UNIDAD".equalsIgnoreCase(ctx.getRoleScope())) {
                validarPermisoUnidadResidente(pago.idUnidad());
            } else if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role) && ctx.getPropertyId() != null) {
                Integer countProp = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = :un AND ID_PROPIEDAD = :propId",
                    new MapSqlParameterSource("un", pago.idUnidad()).addValue("propId", ctx.getPropertyId()),
                    Integer.class
                );
                if (countProp == null || countProp == 0) {
                    throw new AccessDeniedException("No tiene permisos para consultar pagos de otra propiedad");
                }
            } else if ("ADMIN_ORGANIZACION".equalsIgnoreCase(role) && ctx.getOrganizationId() != null) {
                Integer countOrg = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM UNIDADES u JOIN PROPIEDADES p ON u.ID_PROPIEDAD = p.ID_PROPIEDAD " +
                    "WHERE u.ID_UNIDAD = :un AND p.ID_ORGANIZACION = :orgId",
                    new MapSqlParameterSource("un", pago.idUnidad()).addValue("orgId", ctx.getOrganizationId()),
                    Integer.class
                );
                if (countOrg == null || countOrg == 0) {
                    throw new AccessDeniedException("No tiene permisos para consultar pagos de otra organización");
                }
            }
        }
        return pago;
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", resource = "PAGO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.CRITICAL)
    public PagoResponseDTO aprobarPago(Long idPago) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role) || "PORTERO".equalsIgnoreCase(role)) {
                throw new AccessDeniedException("No tiene permisos para aprobar pagos");
            }
        }
        if (ctx == null || ctx.getUserId() == null) {
            throw new AccessDeniedException("Se requiere un usuario autenticado para aprobar pagos");
        }

        List<Map<String, Object>> pagoRows = jdbcTemplate.queryForList(
            "SELECT ID_PAGO, ID_UNIDAD, MONTO_TOTAL, ESTADO FROM PAGOS WHERE ID_PAGO = :id FOR UPDATE",
            new MapSqlParameterSource("id", idPago)
        );
        if (pagoRows.isEmpty()) {
            throw new NoSuchElementException("Pago no encontrado: " + idPago);
        }
        Map<String, Object> pagoRow = pagoRows.get(0);
        String estadoActual = (String) pagoRow.get("ESTADO");
        Long idUnidad = ((Number) pagoRow.get("ID_UNIDAD")).longValue();

        if (!"PENDIENTE_APROBACION".equalsIgnoreCase(estadoActual)) {
            throw new IllegalStateException("Solo se pueden aprobar pagos en estado PENDIENTE_APROBACION. Estado actual: " + estadoActual);
        }

        if ("ADMIN_PROPIEDAD".equalsIgnoreCase(ctx.getRoleCode()) && ctx.getPropertyId() != null) {
            Integer countProp = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = :un AND ID_PROPIEDAD = :propId",
                new MapSqlParameterSource("un", idUnidad).addValue("propId", ctx.getPropertyId()),
                Integer.class
            );
            if (countProp == null || countProp == 0) {
                throw new AccessDeniedException("No tiene permisos para aprobar pagos de otra propiedad");
            }
        }

        Long idAprobador = ctx.getUserId();

        List<Map<String, Object>> detalles = jdbcTemplate.queryForList(
            "SELECT ID_CUOTA, MONTO_APLICADO FROM PAGO_DETALLE WHERE ID_PAGO = :idPago",
            new MapSqlParameterSource("idPago", idPago)
        );

        // Lock every fee and refuse an approval that would pay more than what is still owed (another
        // payment settled it meanwhile). The balance itself is debited by the database trigger on the
        // PENDIENTE_APROBACION -> APROBADO transition, never from Java (F06-03).
        for (Map<String, Object> det : detalles) {
            Long idCuota = ((Number) det.get("ID_CUOTA")).longValue();
            BigDecimal monto = new BigDecimal(det.get("MONTO_APLICADO").toString());
            Map<String, Object> locked = lockCuota(idCuota);
            if (!esCuotaPagable(estadoDe(locked)) || monto.compareTo(saldoDe(locked)) > 0) {
                throw new IllegalStateException("El pago excede el saldo pendiente de la cuota " + idCuota
                        + " (ya fue pagada o abonada); rechace este pago");
            }
        }

        finanzasRepository.aprobarPago(idPago, idAprobador);
        finanzasRepository.recalcularCarteraUnidad(idUnidad);

        // The receipt leaves only after the approval is committed.
        if (!detalles.isEmpty()) {
            Long idCuota = ((Number) detalles.get(0).get("ID_CUOTA")).longValue();
            CuotaDTO cuota = findCuotaById(idCuota);
            PagoResponseDTO response = finanzasRepository.getPagoById(idPago);
            programarReciboPago(cuota, new PagoRequestDTO(
                idCuota, response.montoTotal(), response.metodoPago(), response.referenciaComprobante(), response.fechaPago()
            ));
        }

        return finanzasRepository.getPagoById(idPago);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", resource = "PAGO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public PagoResponseDTO rechazarPago(Long idPago, String motivoRechazo) {
        if (motivoRechazo == null || motivoRechazo.trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo de rechazo es obligatorio");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null) {
            String role = ctx.getRoleCode();
            if ("RESIDENTE".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role) || "PORTERO".equalsIgnoreCase(role)) {
                throw new AccessDeniedException("No tiene permisos para rechazar pagos");
            }
        }

        List<Map<String, Object>> pagoRows = jdbcTemplate.queryForList(
            "SELECT ID_PAGO, ID_UNIDAD, ESTADO FROM PAGOS WHERE ID_PAGO = :id FOR UPDATE",
            new MapSqlParameterSource("id", idPago)
        );
        if (pagoRows.isEmpty()) {
            throw new NoSuchElementException("Pago no encontrado: " + idPago);
        }
        Map<String, Object> pagoRow = pagoRows.get(0);
        String estadoActual = (String) pagoRow.get("ESTADO");
        Long idUnidad = ((Number) pagoRow.get("ID_UNIDAD")).longValue();

        if (!"PENDIENTE_APROBACION".equalsIgnoreCase(estadoActual)) {
            throw new IllegalStateException("Solo se pueden rechazar pagos en estado PENDIENTE_APROBACION. Estado actual: " + estadoActual);
        }

        if (ctx != null) {
            if ("ADMIN_PROPIEDAD".equalsIgnoreCase(ctx.getRoleCode()) && ctx.getPropertyId() != null) {
                Integer countProp = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = :un AND ID_PROPIEDAD = :propId",
                    new MapSqlParameterSource("un", idUnidad).addValue("propId", ctx.getPropertyId()),
                    Integer.class
                );
                if (countProp == null || countProp == 0) {
                    throw new AccessDeniedException("No tiene permisos para rechazar pagos de otra propiedad");
                }
            }
        }

        if (ctx == null || ctx.getUserId() == null) {
            throw new AccessDeniedException("Se requiere un usuario autenticado para rechazar pagos");
        }
        Long idRechazador = ctx.getUserId();
        finanzasRepository.rechazarPago(idPago, idRechazador, motivoRechazo.trim());

        return finanzasRepository.getPagoById(idPago);
    }

    @Override
    public FileStorageService.StoredFile subirComprobante(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo de comprobante no puede estar vacío");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("El archivo excede el tamaño máximo permitido de 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equalsIgnoreCase("image/jpeg") &&
                                    !contentType.equalsIgnoreCase("image/png") &&
                                    !contentType.equalsIgnoreCase("image/webp") &&
                                    !contentType.equalsIgnoreCase("application/pdf"))) {
            throw new IllegalArgumentException("Formato no soportado. Formatos permitidos: JPG, PNG, WEBP, PDF");
        }
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = ctx != null ? ctx.getOrganizationId() : null;
        return fileStorageService.store(file, "comprobantes", orgId);
    }

    @Override
    public Resource descargarComprobante(Long idPago) {
        PagoResponseDTO pago = getPagoDetalle(idPago);
        if (pago.comprobanteUrl() == null || pago.comprobanteUrl().isBlank()) {
            throw new NoSuchElementException("El pago no cuenta con comprobante adjunto");
        }
        return fileStorageService.loadAsResource(pago.comprobanteUrl());
    }

    private CuotaDTO findCuotaById(Long idCuota) {
        List<CuotaDTO> list = jdbcTemplate.query(
            "SELECT c.ID_CUOTA, c.ID_UNIDAD, u.IDENTIFICADOR as numeroApartamento, " +
            "NVL(p.PRIMER_NOMBRE || ' ' || p.PRIMER_APELLIDO, 'Residente') as nombreResidente, " +
            "c.ID_CONTRATO, co.NOMBRE as concepto, c.PERIODO, c.VALOR_BASE, (c.VALOR_BASE - c.VALOR_DESCUENTO + c.VALOR_INTERES_MORA) AS VALOR_TOTAL, c.SALDO_PENDIENTE, " +
            "c.FECHA_VENCIMIENTO as FECHA_LIMITE, c.ESTADO " +
            "FROM CUOTAS c " +
            "JOIN UNIDADES u ON c.ID_UNIDAD = u.ID_UNIDAD " +
            "JOIN CONCEPTOS_COBRO co ON c.ID_CONCEPTO = co.ID_CONCEPTO " +
            "LEFT JOIN CONTRATOS con ON c.ID_CONTRATO = con.ID_CONTRATO " +
            "LEFT JOIN PERSONAS p ON con.ID_ARRENDATARIO_PRINCIPAL = p.ID_PERSONA " +
            "WHERE c.ID_CUOTA = :id",
            new MapSqlParameterSource("id", idCuota),
            (rs, rowNum) -> new CuotaDTO(
                rs.getLong("ID_CUOTA"), rs.getLong("ID_UNIDAD"), rs.getString("numeroApartamento"), rs.getString("nombreResidente"),
                rs.getLong("ID_CONTRATO"), rs.getString("concepto"), rs.getString("PERIODO"),
                rs.getBigDecimal("VALOR_BASE"), rs.getBigDecimal("VALOR_TOTAL"), rs.getBigDecimal("SALDO_PENDIENTE"),
                rs.getDate("FECHA_LIMITE") != null ? rs.getDate("FECHA_LIMITE").toLocalDate() : null, rs.getString("ESTADO")
            )
        );
        if (list.isEmpty()) {
            throw new NoSuchElementException("Cuota no encontrada: " + idCuota);
        }
        return list.get(0);
    }

    private void validarPermisoUnidadResidente(Long idUnidad) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) return;
        String role = ctx.getRoleCode();
        if ("RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("El rol de convivencia no tiene acceso a informacion financiera");
        }
        if ("RESIDENTE".equalsIgnoreCase(role) || "UNIDAD".equalsIgnoreCase(ctx.getRoleScope())) {
            if (ctx.getUnitId() != null && ctx.getUnitId().equals(idUnidad)) {
                return;
            }
            Long userId = ctx.getUserId();
            if (userId != null) {
                Integer countAsig = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM USUARIO_ASIGNACIONES WHERE ID_USUARIO = :u AND ID_UNIDAD = :un AND ESTADO = 'ACTIVA'",
                    new MapSqlParameterSource("u", userId).addValue("un", idUnidad),
                    Integer.class
                );
                if (countAsig != null && countAsig > 0) {
                    return;
                }
                Integer countRes = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM RESIDENTES_UNIDAD ru JOIN USUARIOS u ON ru.ID_PERSONA = u.ID_PERSONA " +
                    "WHERE u.ID_USUARIO = :u AND ru.ID_UNIDAD = :un AND ru.ESTADO = 'ACTIVA'",
                    new MapSqlParameterSource("u", userId).addValue("un", idUnidad),
                    Integer.class
                );
                if (countRes != null && countRes > 0) {
                    return;
                }
            }
            throw new AccessDeniedException("No tiene permisos para registrar pagos sobre unidades ajenas");
        } else if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role) && ctx.getPropertyId() != null) {
            Integer countProp = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM UNIDADES WHERE ID_UNIDAD = :un AND ID_PROPIEDAD = :propId",
                new MapSqlParameterSource("un", idUnidad).addValue("propId", ctx.getPropertyId()),
                Integer.class
            );
            if (countProp == null || countProp == 0) {
                throw new AccessDeniedException("No tiene permisos para gestionar pagos de otra propiedad");
            }
        }
    }

    /**
     * Resolves the recipient inside the transaction (tenant context still valid) and e-mails the
     * receipt only after the transaction commits, so a rollback or a retried request never sends it.
     */
    private void programarReciboPago(CuotaDTO cuota, PagoRequestDTO request) {
        String destinatario;
        try {
            List<Map<String, Object>> residentes = jdbcTemplate.queryForList(
                "SELECT P.EMAIL FROM PERSONAS P " +
                "JOIN RESIDENTES_UNIDAD RU ON RU.ID_PERSONA = P.ID_PERSONA " +
                "WHERE RU.ID_UNIDAD = :u AND P.EMAIL IS NOT NULL",
                new MapSqlParameterSource("u", cuota.idUnidad()));
            if (residentes.isEmpty()) {
                return;
            }
            destinatario = (String) residentes.get(0).get("EMAIL");
        } catch (RuntimeException e) {
            log.warn("No se pudo resolver el destinatario del recibo de la cuota {}", cuota.id());
            return;
        }
        String referencia = request.referencia() != null ? request.referencia() : "PAGO-" + request.idCuota();
        String concepto = cuota.concepto();
        BigDecimal valor = request.valorPagado();
        String fecha = LocalDate.now().toString();
        AfterCommit.run("recibo de pago " + referencia,
                () -> emailService.enviarReciboPago(destinatario, concepto, valor, referencia, fecha));
    }

    private static final Set<String> ESTADOS_CUOTA_PAGABLES = Set.of("PENDIENTE", "VENCIDA", "PAGADA_PARCIAL");

    private static boolean esCuotaPagable(String estado) {
        return estado != null && ESTADOS_CUOTA_PAGABLES.contains(estado.trim().toUpperCase(Locale.ROOT));
    }

    private void validarCuotaPagable(CuotaDTO cuota, BigDecimal valorPagado) {
        if (!esCuotaPagable(cuota.estado())) {
            throw new IllegalStateException("La cuota " + cuota.id() + " no admite pagos en estado " + cuota.estado());
        }
        if (cuota.saldoPendiente() != null && valorPagado.compareTo(cuota.saldoPendiente()) > 0) {
            throw new IllegalArgumentException("El valor pagado excede el saldo pendiente de la cuota");
        }
    }

    private Map<String, Object> lockCuota(Long idCuota) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT ID_CUOTA, SALDO_PENDIENTE, ESTADO FROM CUOTAS WHERE ID_CUOTA = :idCuota FOR UPDATE",
            new MapSqlParameterSource("idCuota", idCuota));
        if (rows.isEmpty()) {
            throw new NoSuchElementException("Cuota no encontrada: " + idCuota);
        }
        return rows.get(0);
    }

    private static BigDecimal saldoDe(Map<String, Object> cuotaRow) {
        Object v = cuotaRow.get("SALDO_PENDIENTE");
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }

    private static String estadoDe(Map<String, Object> cuotaRow) {
        Object v = cuotaRow.get("ESTADO");
        return v == null ? null : v.toString();
    }

    @Override
    public ResidenteDashboardDTO getDashboardResidente(Long idResidente) {
        SaedContext ctx = SaedContextHolder.getContext();
        denyOperationalFinanceRead(ctx);
        if (ctx == null) {
            throw new AccessDeniedException("Se requiere una sesion autenticada");
        }
        String role = ctx.getRoleCode();
        boolean household = "RESIDENTE".equalsIgnoreCase(role) || "PROPIETARIO".equalsIgnoreCase(role)
                || "UNIDAD".equalsIgnoreCase(ctx.getRoleScope());
        MapSqlParameterSource params = new MapSqlParameterSource("idResidente", idResidente);
        StringBuilder sql = new StringBuilder("SELECT u.ID_UNIDAD, u.IDENTIFICADOR FROM UNIDADES u WHERE u.ID_UNIDAD IN (" +
                "SELECT ru.ID_UNIDAD FROM RESIDENTES_UNIDAD ru WHERE ru.ID_PERSONA = :idResidente AND ru.ESTADO IN ('ACTIVO', 'ACTIVA') " +
                "UNION SELECT pu.ID_UNIDAD FROM PROPIETARIOS_UNIDAD pu WHERE pu.ID_PERSONA = :idResidente AND pu.ESTADO IN ('ACTIVO', 'ACTIVA') " +
                "UNION SELECT c.ID_UNIDAD FROM CONTRATOS c WHERE c.ID_ARRENDATARIO_PRINCIPAL = :idResidente AND c.ESTADO = 'ACTIVO') ");
        if (household) {
            // A household member sees only their own dashboard; anyone else's is simply not found.
            Long myPersonaId = resolvePersonaIdForUser(ctx.getUserId());
            if (myPersonaId == null || !myPersonaId.equals(idResidente)) {
                throw new NoSuchElementException("Residente no encontrado: " + idResidente);
            }
            if (ctx.getUnitId() != null) {
                sql.append("AND u.ID_UNIDAD = :unitId ");
                params.addValue("unitId", ctx.getUnitId());
            }
        } else if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            if (ctx.getPropertyId() == null) {
                throw new AccessDeniedException("Se requiere una propiedad activa en la sesion");
            }
            sql.append("AND u.ID_PROPIEDAD = :propId ");
            params.addValue("propId", ctx.getPropertyId());
        } else {
            throw new AccessDeniedException("Rol no autorizado para consultar el estado de cuenta de un residente");
        }
        sql.append("ORDER BY u.ID_UNIDAD FETCH FIRST 1 ROWS ONLY");

        List<Map<String, Object>> unit = jdbcTemplate.queryForList(sql.toString(), params);
        if (unit.isEmpty()) {
            throw new NoSuchElementException("Residente no encontrado: " + idResidente);
        }
        Long idUnidad = ((Number) unit.get(0).get("ID_UNIDAD")).longValue();
        String identificadorUnidad = (String) unit.get(0).get("IDENTIFICADOR");

        List<CuotaDTO> cuotas = finanzasRepository.getCuotasByResidente(idResidente);
        return new ResidenteDashboardDTO(idResidente, idUnidad, identificadorUnidad, cuotas);
    }

    /** RESIDENTE_CONVIVENCIA receives no monetary detail (F06-06). */
    private static void denyConvivencia(SaedContext ctx) {
        if (ctx != null && "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(ctx.getRoleCode())) {
            throw new AccessDeniedException("El rol de convivencia no tiene acceso a informacion financiera");
        }
    }

    /** Operational finance reads: no cohabitant (F06-06) and no SUPERADMIN (DEC-02). */
    private static void denyOperationalFinanceRead(SaedContext ctx) {
        denyConvivencia(ctx);
        if (ctx != null && "SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode())) {
            throw new AccessDeniedException("La administracion de plataforma no accede a datos financieros operativos");
        }
    }

    private Long resolvePersonaIdForUser(Long userId) {
        if (userId == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                "SELECT ID_PERSONA FROM USUARIOS WHERE ID_USUARIO = :u",
                new MapSqlParameterSource("u", userId), Long.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
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
