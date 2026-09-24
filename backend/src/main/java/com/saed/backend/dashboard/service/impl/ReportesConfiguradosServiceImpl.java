package com.saed.backend.dashboard.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.dto.*;
import com.saed.backend.dashboard.export.ExportFormat;
import com.saed.backend.dashboard.export.ExportResult;
import com.saed.backend.dashboard.export.ReportExportService;
import com.saed.backend.dashboard.registry.*;
import com.saed.backend.dashboard.repository.HistorialReportesRepository;
import com.saed.backend.dashboard.repository.ReporteConfiguradoRepository;
import com.saed.backend.dashboard.service.ReportesConfiguradosService;
import com.saed.backend.dashboard.service.ReportesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ReportesConfiguradosServiceImpl implements ReportesConfiguradosService {

    private static final Logger log = LoggerFactory.getLogger(ReportesConfiguradosServiceImpl.class);

    private final ReporteConfiguradoRepository reporteConfiguradoRepository;
    private final HistorialReportesRepository historialReportesRepository;
    private final ReportDefinitionRegistry reportDefinitionRegistry;
    private final ReportesService reportesService;
    private final ReportExportService reportExportService;
    private final ObjectMapper objectMapper;

    public ReportesConfiguradosServiceImpl(
            ReporteConfiguradoRepository reporteConfiguradoRepository,
            HistorialReportesRepository historialReportesRepository,
            ReportDefinitionRegistry reportDefinitionRegistry,
            ReportesService reportesService,
            ReportExportService reportExportService,
            ObjectMapper objectMapper
    ) {
        this.reporteConfiguradoRepository = reporteConfiguradoRepository;
        this.historialReportesRepository = historialReportesRepository;
        this.reportDefinitionRegistry = reportDefinitionRegistry;
        this.reportesService = reportesService;
        this.reportExportService = reportExportService;
        this.objectMapper = objectMapper;
    }

    private SaedContext validateAndGetContext() {
        SaedContext context = SaedContextHolder.getContext();
        String role = (context != null && context.getRoleCode() != null) ? context.getRoleCode() : "";

        if (!role.equals("SUPERADMIN") && !role.equals("ADMIN_ORGANIZACION") && !role.equals("ADMIN_PROPIEDAD")) {
            throw new ReportAccessDeniedException("Rol no autorizado para gestionar o consultar reportes del sistema.");
        }
        return context;
    }

    @Override
    public List<ReporteConfiguradoDTO> listarConfiguraciones(Long propertyId) {
        SaedContext context = validateAndGetContext();
        String role = context.getRoleCode();

        Long effectiveOrgId = context.getOrganizationId();
        Long effectivePropId = null;

        if ("SUPERADMIN".equals(role)) {
            effectiveOrgId = null;
            effectivePropId = propertyId;
        } else if ("ADMIN_PROPIEDAD".equals(role)) {
            effectivePropId = context.getPropertyId();
        } else if ("ADMIN_ORGANIZACION".equals(role)) {
            effectivePropId = propertyId;
        }

        return reporteConfiguradoRepository.findAllVisible(effectiveOrgId, effectivePropId);
    }

    @Override
    public ReporteConfiguradoDTO obtenerPorId(Long id) {
        SaedContext context = validateAndGetContext();
        ReporteConfiguradoDTO config = reporteConfiguradoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Reporte configurado no encontrado con ID: " + id));

        validateTenantAccess(config, context);
        return config;
    }

    @Override
    @Transactional
    public ReporteConfiguradoDTO crearConfiguracion(ReporteConfiguradoCreateRequest request) {
        SaedContext context = validateAndGetContext();
        String role = context.getRoleCode();

        // 1. Validar clave allowlistada
        if (!reportDefinitionRegistry.isAllowlisted(request.getConsultaOrigenClave())) {
            throw new InvalidReportKeyException(request.getConsultaOrigenClave());
        }

        Long orgId = context.getOrganizationId();
        Long propId = null;

        if ("SUPERADMIN".equals(role)) {
            orgId = null; // Plantilla global o específica si asignada
            propId = request.getIdPropiedad();
        } else if ("ADMIN_PROPIEDAD".equals(role)) {
            propId = context.getPropertyId();
            if (request.getIdPropiedad() != null && !request.getIdPropiedad().equals(propId)) {
                throw new ReportAccessDeniedException("Un administrador de propiedad no puede crear reportes para propiedades ajenas.");
            }
        } else if ("ADMIN_ORGANIZACION".equals(role)) {
            propId = request.getIdPropiedad();
        }

        return reporteConfiguradoRepository.create(request, orgId, propId, context.getUserId());
    }

    @Override
    @Transactional
    public ReporteConfiguradoDTO actualizarConfiguracion(Long id, ReporteConfiguradoUpdateRequest request) {
        SaedContext context = validateAndGetContext();
        ReporteConfiguradoDTO existing = reporteConfiguradoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Reporte configurado no encontrado con ID: " + id));

        validateTenantAccess(existing, context);

        if (existing.isEsPlantillaSistema() && !"SUPERADMIN".equals(context.getRoleCode())) {
            throw new ReportAccessDeniedException("Las plantillas canónicas del sistema solo pueden ser modificadas por administradores globales.");
        }

        if (request.getConsultaOrigenClave() != null && !request.getConsultaOrigenClave().isBlank()) {
            if (!reportDefinitionRegistry.isAllowlisted(request.getConsultaOrigenClave())) {
                throw new InvalidReportKeyException(request.getConsultaOrigenClave());
            }
        }

        return reporteConfiguradoRepository.update(id, request, context.getUserId());
    }

    @Override
    @Transactional
    public void desactivarConfiguracion(Long id) {
        SaedContext context = validateAndGetContext();
        ReporteConfiguradoDTO existing = reporteConfiguradoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Reporte configurado no encontrado con ID: " + id));

        validateTenantAccess(existing, context);

        if (existing.isEsPlantillaSistema() && !"SUPERADMIN".equals(context.getRoleCode())) {
            throw new ReportAccessDeniedException("Las plantillas de sistema no pueden ser desactivadas por administradores locales.");
        }

        reporteConfiguradoRepository.deactivate(id);
    }

    @Override
    @Transactional
    public ExportResult generarReporte(Long idConfig, String overrideFormato, String overrideFiltrosJson) {
        ReportExecutionPayload payload = executeInternal(idConfig, overrideFormato, overrideFiltrosJson);
        return payload.exportResult();
    }

    @Override
    @Transactional
    public GenerarReporteResponseDTO generarReporteMetadata(Long idConfig, String overrideFormato, String overrideFiltrosJson) {
        ReportExecutionPayload payload = executeInternal(idConfig, overrideFormato, overrideFiltrosJson);
        return new GenerarReporteResponseDTO(
                payload.historialId(),
                payload.config().getIdReporteConfig(),
                payload.config().getNombre(),
                payload.format().name(),
                payload.exportResult().filename(),
                payload.sha256(),
                payload.totalRows(),
                payload.durationMs(),
                OffsetDateTime.now()
        );
    }

    private record ReportExecutionPayload(
            ReporteConfiguradoDTO config,
            ExportFormat format,
            ExportResult exportResult,
            int totalRows,
            String sha256,
            long durationMs,
            Long historialId
    ) {}

    private ReportExecutionPayload executeInternal(Long idConfig, String overrideFormato, String overrideFiltrosJson) {
        SaedContext context = validateAndGetContext();
        ReporteConfiguradoDTO config = reporteConfiguradoRepository.findById(idConfig)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Reporte configurado no encontrado con ID: " + idConfig));

        validateTenantAccess(config, context);

        if ("INACTIVO".equalsIgnoreCase(config.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No es posible generar reportes a partir de una plantilla de reporte inactiva.");
        }

        // 1. Determinar formato efectivo
        String rawFormat = (overrideFormato != null && !overrideFormato.isBlank())
                ? overrideFormato
                : config.getFormatoSalidaDefecto();
        ExportFormat exportFormat = ExportFormat.fromString(rawFormat);

        // 2. Resolver definición Java segura
        ReportDefinition definition = reportDefinitionRegistry.getDefinition(config.getConsultaOrigenClave());

        // 3. Parsear y normalizar parámetros
        String effectiveFiltrosJson = (overrideFiltrosJson != null && !overrideFiltrosJson.isBlank())
                ? overrideFiltrosJson
                : config.getParametrosFiltroJson();

        ReportExecutionParams params = parseExecutionParams(effectiveFiltrosJson, config, context);

        // 4. Ejecución del reporte
        long startTime = System.currentTimeMillis();
        ReportExecutionResult executionResult = definition.execute(reportesService, reportExportService, params, exportFormat);
        long durationMs = System.currentTimeMillis() - startTime;

        // 5. Cálculo determinista del hash SHA-256 sobre el resultado binario
        byte[] content = executionResult.exportResult().content();
        String sha256 = computeSha256(content);

        // 6. Registro inmutable en HISTORIAL_REPORTES
        Long effectiveOrgId = (context.getOrganizationId() != null && context.getOrganizationId() > 0)
                ? context.getOrganizationId()
                : (config.getIdOrganizacion() != null ? config.getIdOrganizacion() : 1L);

        HistorialReporteDTO hist = new HistorialReporteDTO();
        hist.setIdReporteConfig(config.getIdReporteConfig());
        hist.setIdOrganizacion(effectiveOrgId);
        hist.setIdPropiedad(params.propertyId());
        hist.setIdUsuarioEjecuto(context.getUserId() != null ? context.getUserId() : 1L);
        hist.setFormatoGenerado(exportFormat.name());
        hist.setParametrosFiltroJson(effectiveFiltrosJson);
        hist.setArchivoGeneradoUrl(executionResult.exportResult().filename());
        hist.setArchivoSha256(sha256);
        hist.setTiempoGeneracionMs(durationMs);
        hist.setRegistrosProcesados(executionResult.totalRows());

        HistorialReporteDTO savedHist = historialReportesRepository.save(hist);

        return new ReportExecutionPayload(
                config,
                exportFormat,
                executionResult.exportResult(),
                executionResult.totalRows(),
                sha256,
                durationMs,
                savedHist.getIdHistorialReporte()
        );
    }

    private ReportExecutionParams parseExecutionParams(String filtrosJson, ReporteConfiguradoDTO config, SaedContext context) {
        String role = context.getRoleCode();
        Long assignedPropId = context.getPropertyId();

        LocalDate fechaInicio = null;
        LocalDate fechaFin = null;
        String periodoInicio = null;
        String periodoFin = null;
        Integer vigenciaAnio = null;
        Long requestedPropId = null;
        int page = 0;
        int size = 10001;

        if (filtrosJson != null && !filtrosJson.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(filtrosJson);
                if (root.hasNonNull("fechaInicio")) {
                    fechaInicio = LocalDate.parse(root.get("fechaInicio").asText());
                }
                if (root.hasNonNull("fechaFin")) {
                    fechaFin = LocalDate.parse(root.get("fechaFin").asText());
                }
                if (root.hasNonNull("periodoInicio")) {
                    periodoInicio = root.get("periodoInicio").asText();
                }
                if (root.hasNonNull("periodoFin")) {
                    periodoFin = root.get("periodoFin").asText();
                }
                if (root.hasNonNull("vigenciaAnio")) {
                    vigenciaAnio = root.get("vigenciaAnio").asInt();
                } else if (root.hasNonNull("vigencia")) {
                    vigenciaAnio = root.get("vigencia").asInt();
                }
                if (root.hasNonNull("propertyId")) {
                    requestedPropId = root.get("propertyId").asLong();
                }
                if (root.hasNonNull("page")) {
                    page = Math.max(0, root.get("page").asInt());
                }
                if (root.hasNonNull("size")) {
                    size = Math.min(10001, Math.max(1, root.get("size").asInt()));
                }
            } catch (Exception e) {
                log.warn("No se pudo parsear filtros JSON de reporte, usando valores por defecto: {}", e.getMessage());
            }
        }

        Long effectivePropId;
        if ("ADMIN_PROPIEDAD".equals(role)) {
            effectivePropId = assignedPropId;
        } else if ("ADMIN_ORGANIZACION".equals(role)) {
            effectivePropId = (requestedPropId != null) ? requestedPropId : config.getIdPropiedad();
        } else {
            effectivePropId = (requestedPropId != null) ? requestedPropId : config.getIdPropiedad();
        }

        return new ReportExecutionParams(
                effectivePropId,
                fechaInicio,
                fechaFin,
                periodoInicio,
                periodoFin,
                vigenciaAnio,
                page,
                size
        );
    }

    private void validateTenantAccess(ReporteConfiguradoDTO config, SaedContext context) {
        String role = context.getRoleCode();
        if ("SUPERADMIN".equals(role)) {
            return;
        }

        // Si es plantilla global del sistema, todos los administradores pueden leerla
        if (config.isEsPlantillaSistema()) {
            return;
        }

        Long userOrg = context.getOrganizationId();
        if (userOrg == null || !userOrg.equals(config.getIdOrganizacion())) {
            throw new ReportAccessDeniedException("Acceso denegado: el reporte configurado no pertenece a su organización.");
        }

        if ("ADMIN_PROPIEDAD".equals(role)) {
            Long userProp = context.getPropertyId();
            if (config.getIdPropiedad() != null && !config.getIdPropiedad().equals(userProp)) {
                throw new ReportAccessDeniedException("Acceso denegado: el reporte configurado pertenece a otra propiedad.");
            }
        }
    }

    @Override
    public List<HistorialReporteDTO> listarHistorial(Long propertyId, int page, int size) {
        SaedContext context = validateAndGetContext();
        String role = context.getRoleCode();

        Long effectiveOrgId = context.getOrganizationId();
        Long effectivePropId = null;

        if ("SUPERADMIN".equals(role)) {
            effectiveOrgId = null;
            effectivePropId = propertyId;
        } else if ("ADMIN_PROPIEDAD".equals(role)) {
            effectivePropId = context.getPropertyId();
        } else if ("ADMIN_ORGANIZACION".equals(role)) {
            // Sección 9: propertyId puede ser null para ver todo el historial de la organización (incluyendo consolidados)
            effectivePropId = propertyId;
        }

        int effPage = Math.max(0, page);
        int effSize = Math.min(200, Math.max(1, size));

        return historialReportesRepository.findHistorial(effectiveOrgId, effectivePropId, effPage, effSize);
    }

    @Override
    public HistorialReporteDTO obtenerHistorialPorId(Long idHistorial) {
        SaedContext context = validateAndGetContext();
        HistorialReporteDTO hist = historialReportesRepository.findById(idHistorial)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Registro de historial de reporte no encontrado con ID: " + idHistorial));

        String role = context.getRoleCode();
        if (!"SUPERADMIN".equals(role)) {
            Long userOrg = context.getOrganizationId();
            if (userOrg == null || !userOrg.equals(hist.getIdOrganizacion())) {
                throw new ReportAccessDeniedException("Acceso denegado al historial de reporte de otra organización.");
            }
            if ("ADMIN_PROPIEDAD".equals(role)) {
                Long userProp = context.getPropertyId();
                if (hist.getIdPropiedad() != null && !hist.getIdPropiedad().equals(userProp)) {
                    throw new ReportAccessDeniedException("Acceso denegado al historial de reporte de otra propiedad.");
                }
            }
        }

        return hist;
    }

    public static String computeSha256(byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo criptográfico SHA-256 no disponible.", e);
        }
    }
}
