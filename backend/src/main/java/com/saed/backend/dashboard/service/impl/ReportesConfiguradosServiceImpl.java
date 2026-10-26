package com.saed.backend.dashboard.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.dashboard.dto.GenerarReporteResponseDTO;
import com.saed.backend.dashboard.dto.HistorialReporteDTO;
import com.saed.backend.dashboard.dto.ReporteConfiguradoCreateRequest;
import com.saed.backend.dashboard.dto.ReporteConfiguradoDTO;
import com.saed.backend.dashboard.dto.ReporteConfiguradoUpdateRequest;
import com.saed.backend.dashboard.export.ExportFormat;
import com.saed.backend.dashboard.export.ExportResult;
import com.saed.backend.dashboard.export.ReportExportService;
import com.saed.backend.dashboard.registry.InvalidReportKeyException;
import com.saed.backend.dashboard.registry.ReportAccessDeniedException;
import com.saed.backend.dashboard.registry.ReportDefinition;
import com.saed.backend.dashboard.registry.ReportDefinitionRegistry;
import com.saed.backend.dashboard.registry.ReportExecutionParams;
import com.saed.backend.dashboard.registry.ReportExecutionResult;
import com.saed.backend.dashboard.repository.HistorialReportesRepository;
import com.saed.backend.dashboard.repository.ReporteConfiguradoRepository;
import com.saed.backend.dashboard.service.ReportesConfiguradosService;
import com.saed.backend.dashboard.service.ReportesService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Configured reports (F11-01). Scope always comes from the server context: a property administrator is
 * bound to its context property, an organization administrator to its organization's properties, and
 * SUPERADMIN never reads these operational reports (DEC-02). Malformed filters and unknown formats are
 * rejected with 400; foreign resources are indistinguishable from missing ones (404).
 */
@Service
public class ReportesConfiguradosServiceImpl implements ReportesConfiguradosService {

    private static final String ROLE_ADMIN_PROPIEDAD = "ADMIN_PROPIEDAD";
    private static final String ROLE_ADMIN_ORGANIZACION = "ADMIN_ORGANIZACION";
    private static final Set<String> ALLOWED_STATES = Set.of("ACTIVO", "INACTIVO");
    private static final Set<String> ALLOWED_FILTERS = Set.of("fechaInicio", "fechaFin", "periodoInicio", "periodoFin",
            "vigenciaAnio", "vigencia", "propertyId", "page", "size");
    private static final Pattern PERIOD = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])$");
    private static final Pattern INTEGER_TEXT = Pattern.compile("^-?[0-9]{1,18}$");
    private static final int MAX_ROWS = 10001;

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

    /** Server-side tenant scope of the caller. */
    private record Scope(String role, Long orgId, Long propId, Long userId) {
        boolean propertyAdmin() {
            return ROLE_ADMIN_PROPIEDAD.equals(role);
        }
    }

    private Scope requireScope() {
        SaedContext context = SaedContextHolder.getContext();
        String role = context != null && context.getRoleCode() != null ? context.getRoleCode() : "";
        if (!ROLE_ADMIN_PROPIEDAD.equals(role) && !ROLE_ADMIN_ORGANIZACION.equals(role)) {
            throw new AccessDeniedException("Rol no autorizado para gestionar o consultar reportes.");
        }
        if (context.getOrganizationId() == null || context.getUserId() == null) {
            throw new AccessDeniedException("No hay un contexto de organización activo.");
        }
        if (ROLE_ADMIN_PROPIEDAD.equals(role) && context.getPropertyId() == null) {
            throw new AccessDeniedException("No hay un contexto de propiedad activo.");
        }
        Long propId = ROLE_ADMIN_PROPIEDAD.equals(role) ? context.getPropertyId() : null;
        return new Scope(role, context.getOrganizationId(), propId, context.getUserId());
    }

    /**
     * Resolves the property a request may target. A property administrator is bound to the context
     * property; an organization administrator may target only properties of its own organization.
     */
    private Long resolveRequestedProperty(Scope scope, Long requested) {
        if (scope.propertyAdmin()) {
            if (requested != null && !requested.equals(scope.propId())) {
                throw new AccessDeniedException("No está autorizado a consultar otra propiedad.");
            }
            return scope.propId();
        }
        if (requested == null) {
            return null;
        }
        if (!reporteConfiguradoRepository.propertyBelongsToOrganization(requested, scope.orgId())) {
            throw new NoSuchElementException("Propiedad no encontrada.");
        }
        return requested;
    }

    private ReporteConfiguradoDTO findVisible(Long id, Scope scope) {
        return reporteConfiguradoRepository.findById(id)
                .filter(config -> isVisible(config, scope))
                .orElseThrow(() -> new NoSuchElementException("Reporte configurado no encontrado."));
    }

    private static boolean isVisible(ReporteConfiguradoDTO config, Scope scope) {
        if (config.getIdOrganizacion() == null) {
            return true;
        }
        if (!scope.orgId().equals(config.getIdOrganizacion())) {
            return false;
        }
        return !scope.propertyAdmin() || config.getIdPropiedad() == null
                || config.getIdPropiedad().equals(scope.propId());
    }

    @Override
    public List<ReporteConfiguradoDTO> listarConfiguraciones(Long propertyId) {
        Scope scope = requireScope();
        Long effectivePropId = scope.propertyAdmin() ? scope.propId() : resolveRequestedProperty(scope, propertyId);
        return reporteConfiguradoRepository.findAllVisible(scope.orgId(), effectivePropId);
    }

    @Override
    public ReporteConfiguradoDTO obtenerPorId(Long id) {
        return findVisible(id, requireScope());
    }

    @Override
    @Transactional
    public ReporteConfiguradoDTO crearConfiguracion(ReporteConfiguradoCreateRequest request) {
        Scope scope = requireScope();
        if (!reportDefinitionRegistry.isAllowlisted(request.getConsultaOrigenClave())) {
            throw new InvalidReportKeyException(request.getConsultaOrigenClave());
        }
        if (request.getFormatoSalidaDefecto() != null && !request.getFormatoSalidaDefecto().isBlank()) {
            request.setFormatoSalidaDefecto(ExportFormat.fromString(request.getFormatoSalidaDefecto()).name());
        }
        parseFilters(request.getParametrosFiltroJson());
        Long propId = resolveRequestedProperty(scope, request.getIdPropiedad());
        return reporteConfiguradoRepository.create(request, scope.orgId(), propId, scope.userId());
    }

    @Override
    @Transactional
    public ReporteConfiguradoDTO actualizarConfiguracion(Long id, ReporteConfiguradoUpdateRequest request) {
        Scope scope = requireScope();
        ReporteConfiguradoDTO existing = findVisible(id, scope);
        if (existing.isEsPlantillaSistema()) {
            throw new ReportAccessDeniedException("Las plantillas del sistema no pueden ser modificadas por administradores locales.");
        }
        if (request.getConsultaOrigenClave() != null && !request.getConsultaOrigenClave().isBlank()
                && !reportDefinitionRegistry.isAllowlisted(request.getConsultaOrigenClave())) {
            throw new InvalidReportKeyException(request.getConsultaOrigenClave());
        }
        if (request.getFormatoSalidaDefecto() != null && !request.getFormatoSalidaDefecto().isBlank()) {
            request.setFormatoSalidaDefecto(ExportFormat.fromString(request.getFormatoSalidaDefecto()).name());
        }
        if (request.getEstado() != null && !request.getEstado().isBlank()
                && !ALLOWED_STATES.contains(request.getEstado().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Estado de reporte no válido. Valores permitidos: ACTIVO, INACTIVO.");
        }
        parseFilters(request.getParametrosFiltroJson());
        return reporteConfiguradoRepository.update(id, request, scope.userId());
    }

    @Override
    @Transactional
    public void desactivarConfiguracion(Long id) {
        Scope scope = requireScope();
        ReporteConfiguradoDTO existing = findVisible(id, scope);
        if (existing.isEsPlantillaSistema()) {
            throw new ReportAccessDeniedException("Las plantillas de sistema no pueden ser desactivadas por administradores locales.");
        }
        reporteConfiguradoRepository.deactivate(id);
    }

    @Override
    @Transactional
    public ExportResult generarReporte(Long idConfig, String overrideFormato, String overrideFiltrosJson) {
        return executeInternal(idConfig, overrideFormato, overrideFiltrosJson).exportResult();
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
        Scope scope = requireScope();
        ReporteConfiguradoDTO config = findVisible(idConfig, scope);

        if ("INACTIVO".equalsIgnoreCase(config.getEstado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No es posible generar reportes a partir de una plantilla de reporte inactiva.");
        }

        String rawFormat = (overrideFormato != null && !overrideFormato.isBlank())
                ? overrideFormato
                : config.getFormatoSalidaDefecto();
        ExportFormat exportFormat = ExportFormat.fromString(rawFormat);

        ReportDefinition definition = reportDefinitionRegistry.getDefinition(config.getConsultaOrigenClave());

        String effectiveFiltrosJson = (overrideFiltrosJson != null && !overrideFiltrosJson.isBlank())
                ? overrideFiltrosJson
                : config.getParametrosFiltroJson();
        ParsedFilters filters = parseFilters(effectiveFiltrosJson);

        Long requestedProp = filters.propertyId() != null ? filters.propertyId() : config.getIdPropiedad();
        Long effectivePropId = resolveRequestedProperty(scope, requestedProp);
        ReportExecutionParams params = new ReportExecutionParams(effectivePropId, filters.fechaInicio(), filters.fechaFin(),
                filters.periodoInicio(), filters.periodoFin(), filters.vigenciaAnio(), filters.page(), filters.size());

        long startTime = System.currentTimeMillis();
        ReportExecutionResult executionResult = definition.execute(reportesService, reportExportService, params, exportFormat);
        long durationMs = System.currentTimeMillis() - startTime;

        byte[] content = executionResult.exportResult().content();
        String sha256 = computeSha256(content);

        HistorialReporteDTO hist = new HistorialReporteDTO();
        hist.setIdReporteConfig(config.getIdReporteConfig());
        hist.setIdOrganizacion(scope.orgId());
        hist.setIdPropiedad(effectivePropId);
        hist.setIdUsuarioEjecuto(scope.userId());
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

    /** Typed, validated report filters. */
    record ParsedFilters(LocalDate fechaInicio, LocalDate fechaFin, String periodoInicio, String periodoFin,
                         Integer vigenciaAnio, Long propertyId, int page, int size) {
    }

    /**
     * Parses stored or request filters strictly: malformed JSON, unknown keys, invalid dates or periods and
     * inverted ranges are rejected with {@link IllegalArgumentException} (HTTP 400) instead of being ignored.
     */
    ParsedFilters parseFilters(String filtrosJson) {
        if (filtrosJson == null || filtrosJson.isBlank()) {
            return new ParsedFilters(null, null, null, null, null, null, 0, MAX_ROWS);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(filtrosJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Los filtros del reporte no son un JSON válido.");
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Los filtros del reporte deben ser un objeto JSON.");
        }
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!ALLOWED_FILTERS.contains(name)) {
                throw new IllegalArgumentException("Filtro de reporte no permitido: " + name);
            }
        }
        LocalDate fechaInicio = dateFilter(root, "fechaInicio");
        LocalDate fechaFin = dateFilter(root, "fechaFin");
        if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin)) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin.");
        }
        String periodoInicio = periodFilter(root, "periodoInicio");
        String periodoFin = periodFilter(root, "periodoFin");
        if (periodoInicio != null && periodoFin != null && periodoInicio.compareTo(periodoFin) > 0) {
            throw new IllegalArgumentException("El periodo de inicio no puede ser posterior al periodo de fin.");
        }
        Long vigencia = longFilter(root, root.hasNonNull("vigenciaAnio") ? "vigenciaAnio" : "vigencia");
        if (vigencia != null && (vigencia < 2000 || vigencia > 2100)) {
            throw new IllegalArgumentException("La vigencia debe ser un año entre 2000 y 2100.");
        }
        Long propertyId = longFilter(root, "propertyId");
        Long page = longFilter(root, "page");
        Long size = longFilter(root, "size");
        if (page != null && (page < 0 || page > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("El número de página no es válido.");
        }
        if (size != null && (size < 1 || size > MAX_ROWS)) {
            throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y " + MAX_ROWS + ".");
        }
        return new ParsedFilters(fechaInicio, fechaFin, periodoInicio, periodoFin,
                vigencia != null ? vigencia.intValue() : null, propertyId,
                page != null ? page.intValue() : 0, size != null ? size.intValue() : MAX_ROWS);
    }

    private static LocalDate dateFilter(JsonNode root, String name) {
        if (!root.hasNonNull(name)) {
            return null;
        }
        JsonNode node = root.get(name);
        if (!node.isTextual()) {
            throw new IllegalArgumentException("El filtro " + name + " debe ser una fecha YYYY-MM-DD.");
        }
        try {
            return LocalDate.parse(node.asText());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El filtro " + name + " debe ser una fecha YYYY-MM-DD válida.");
        }
    }

    private static String periodFilter(JsonNode root, String name) {
        if (!root.hasNonNull(name)) {
            return null;
        }
        JsonNode node = root.get(name);
        if (!node.isTextual() || !PERIOD.matcher(node.asText()).matches()) {
            throw new IllegalArgumentException("El filtro " + name + " debe tener formato YYYY-MM.");
        }
        return node.asText();
    }

    private static Long longFilter(JsonNode root, String name) {
        if (!root.hasNonNull(name)) {
            return null;
        }
        JsonNode node = root.get(name);
        if (node.isIntegralNumber() && node.canConvertToLong()) {
            return node.asLong();
        }
        if (node.isTextual() && INTEGER_TEXT.matcher(node.asText()).matches()) {
            return Long.parseLong(node.asText());
        }
        throw new IllegalArgumentException("El filtro " + name + " debe ser un número entero.");
    }

    @Override
    public List<HistorialReporteDTO> listarHistorial(Long propertyId, int page, int size) {
        Scope scope = requireScope();
        Long effectivePropId = scope.propertyAdmin() ? scope.propId() : resolveRequestedProperty(scope, propertyId);
        int effPage = Math.max(0, page);
        int effSize = Math.min(200, Math.max(1, size));
        return historialReportesRepository.findHistorial(scope.orgId(), effectivePropId, effPage, effSize);
    }

    @Override
    public HistorialReporteDTO obtenerHistorialPorId(Long idHistorial) {
        Scope scope = requireScope();
        return historialReportesRepository.findById(idHistorial)
                .filter(hist -> scope.orgId().equals(hist.getIdOrganizacion()))
                .filter(hist -> !scope.propertyAdmin() || hist.getIdPropiedad() == null
                        || hist.getIdPropiedad().equals(scope.propId()))
                .orElseThrow(() -> new NoSuchElementException("Registro de historial de reporte no encontrado."));
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
