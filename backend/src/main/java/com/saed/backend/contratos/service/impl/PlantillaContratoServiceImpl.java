package com.saed.backend.contratos.service.impl;

import com.saed.backend.authorization.repository.PropertyRepository;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.contratos.dto.PlantillaContratoDTO;
import com.saed.backend.contratos.dto.PlantillaContratoRequestDTO;
import com.saed.backend.contratos.repository.PlantillaContratoRepository;
import com.saed.backend.contratos.service.PlantillaContratoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PlantillaContratoServiceImpl implements PlantillaContratoService {

    private static final Logger log = LoggerFactory.getLogger(PlantillaContratoServiceImpl.class);

    private static final List<String> VARIABLES_ESTANDAR = List.of(
            "organizacion.nombre",
            "organizacion.nit",
            "propiedad.nombre",
            "propiedad.direccion",
            "propiedad.ciudad",
            "unidad.identificador",
            "residente.nombreCompleto",
            "residente.numeroDocumento",
            "residente.tipoDocumento",
            "residente.telefono",
            "residente.email",
            "contrato.fechaInicio",
            "contrato.fechaFin",
            "contrato.valorMensual",
            "contrato.tipoContrato",
            "contrato.notas",
            "fechaActual"
    );

    private final PlantillaContratoRepository plantillaRepository;
    private final PropertyRepository propertyRepository;

    public PlantillaContratoServiceImpl(PlantillaContratoRepository plantillaRepository,
                                       PropertyRepository propertyRepository) {
        this.plantillaRepository = plantillaRepository;
        this.propertyRepository = propertyRepository;
    }

    private Long resolveOrganizationId() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) {
            throw new AccessDeniedException("No security context found");
        }

        if (ctx.getOrganizationId() != null) {
            return ctx.getOrganizationId();
        }

        // If propertyId is present, resolve organization from property
        if (ctx.getPropertyId() != null) {
            return propertyRepository.findById(ctx.getPropertyId())
                    .map(p -> p.getIdOrganizacion())
                    .orElseThrow(() -> new NoSuchElementException("Propiedad no encontrada para resolver organización"));
        }

        if ("SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode())) {
            throw new IllegalArgumentException("Superadmin debe especificar la organización objetivo");
        }

        throw new AccessDeniedException("No se pudo determinar la organización de la sesión");
    }

    @Override
    public List<PlantillaContratoDTO> listarPorOrganizacion(String estado) {
        Long orgId = resolveOrganizationId();
        return plantillaRepository.findByOrganizacionId(orgId, estado);
    }

    @Override
    public List<PlantillaContratoDTO> listarActivasParaPropiedad() {
        Long orgId = resolveOrganizationId();
        return plantillaRepository.findActivasByOrganizacionId(orgId);
    }

    @Override
    public PlantillaContratoDTO obtenerPorId(Long id) {
        Long orgId = resolveOrganizationId();
        PlantillaContratoDTO dto = plantillaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Plantilla de contrato no encontrada: " + id));

        SaedContext ctx = SaedContextHolder.getContext();
        boolean isSuperadmin = ctx != null && "SUPERADMIN".equalsIgnoreCase(ctx.getRoleCode());

        if (!isSuperadmin && !orgId.equals(dto.getIdOrganizacion())) {
            throw new AccessDeniedException("No tiene permisos para consultar esta plantilla");
        }
        return dto;
    }

    @Override
    @Transactional
    public PlantillaContratoDTO crear(PlantillaContratoRequestDTO dto) {
        Long orgId = resolveOrganizationId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = (ctx != null) ? ctx.getUserId() : null;

        // Auto-detect version if not set
        if (dto.getVersion() == null || dto.getVersion() <= 0) {
            int maxVer = plantillaRepository.getMaxVersion(orgId, dto.getCodigo().toUpperCase());
            dto.setVersion(maxVer + 1);
        }

        // Populate standard variables if not provided
        if (dto.getVariablesDisponibles() == null || dto.getVariablesDisponibles().isEmpty()) {
            dto.setVariablesDisponibles(VARIABLES_ESTANDAR);
        }

        Long id = plantillaRepository.create(dto, orgId, userId);
        return obtenerPorId(id);
    }

    @Override
    @Transactional
    public PlantillaContratoDTO actualizar(Long id, PlantillaContratoRequestDTO dto) {
        PlantillaContratoDTO actual = obtenerPorId(id);
        if ("HISTORICA".equalsIgnoreCase(actual.getEstado())) {
            throw new IllegalStateException("Las plantillas en estado HISTÓRICA son inmutables. Debe crear una nueva versión.");
        }

        plantillaRepository.update(id, dto);
        return obtenerPorId(id);
    }

    @Override
    @Transactional
    public PlantillaContratoDTO crearNuevaVersion(Long id, PlantillaContratoRequestDTO dto) {
        PlantillaContratoDTO anterior = obtenerPorId(id);
        Long orgId = anterior.getIdOrganizacion();
        SaedContext ctx = SaedContextHolder.getContext();
        Long userId = (ctx != null) ? ctx.getUserId() : null;

        // Mark previous version as HISTORICA
        plantillaRepository.updateStatus(id, "HISTORICA");

        // Prepare new version
        dto.setCodigo(anterior.getCodigo());
        if (dto.getNombre() == null || dto.getNombre().isBlank()) {
            dto.setNombre(anterior.getNombre());
        }
        if (dto.getTipoContrato() == null || dto.getTipoContrato().isBlank()) {
            dto.setTipoContrato(anterior.getTipoContrato());
        }
        if (dto.getContenidoHtml() == null || dto.getContenidoHtml().isBlank()) {
            dto.setContenidoHtml(anterior.getContenidoHtml());
        }
        dto.setVersion(anterior.getVersion() + 1);
        dto.setEstado("ACTIVA");
        dto.setVigenciaDesde(LocalDate.now());

        Long nuevoId = plantillaRepository.create(dto, orgId, userId);
        return obtenerPorId(nuevoId);
    }

    @Override
    @Transactional
    public void cambiarEstado(Long id, String nuevoEstado) {
        PlantillaContratoDTO actual = obtenerPorId(id);
        String estadoNorm = nuevoEstado.toUpperCase();
        if (!List.of("ACTIVA", "BORRADOR", "HISTORICA").contains(estadoNorm)) {
            throw new IllegalArgumentException("Estado no válido: " + nuevoEstado);
        }
        plantillaRepository.updateStatus(id, estadoNorm);
    }

    @Override
    public String renderizarPlantilla(Long idPlantilla, Map<String, Object> variables) {
        PlantillaContratoDTO plantilla = obtenerPorId(idPlantilla);
        String html = plantilla.getContenidoHtml();
        if (html == null) return "";

        Map<String, Object> vars = new HashMap<>(variables != null ? variables : Collections.emptyMap());
        if (!vars.containsKey("fechaActual")) {
            vars.put("fechaActual", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }

        // Replace placeholders formatted as ${variable.nombre}
        Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z0-9_.]+)\\}");
        Matcher matcher = pattern.matcher(html);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object val = vars.get(key);
            String replacement = (val != null) ? Matcher.quoteReplacement(String.valueOf(val)) : "";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @Override
    public List<String> getVariablesSoportadas() {
        return VARIABLES_ESTANDAR;
    }
}
