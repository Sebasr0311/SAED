package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.*;
import com.saed.backend.asambleas.repository.ActaRepository;
import com.saed.backend.asambleas.repository.AsambleaRepository;
import com.saed.backend.asambleas.service.ActaService;
import com.saed.backend.asambleas.service.AsambleaService;
import com.saed.backend.audit.AuditService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.documentos.service.DocumentoService;
import com.saed.backend.documentos.service.DocumentoService.DocumentoDescarga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ActaServiceImpl implements ActaService {

    private static final Logger log = LoggerFactory.getLogger(ActaServiceImpl.class);

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "BORRADOR", Set.of("EN_REVISION_COMISION"),
            "EN_REVISION_COMISION", Set.of("APROBADA", "BORRADOR"),
            "APROBADA", Set.of("PUBLICADA_OFICIAL")
    );

    private final ActaRepository actaRepository;
    private final AsambleaRepository asambleaRepository;
    private final AsambleaService asambleaService;
    private final DocumentoService documentoService;
    private final AuditService auditService;

    public ActaServiceImpl(ActaRepository actaRepository,
                           AsambleaRepository asambleaRepository,
                           AsambleaService asambleaService,
                           DocumentoService documentoService,
                           AuditService auditService) {
        this.actaRepository = actaRepository;
        this.asambleaRepository = asambleaRepository;
        this.asambleaService = asambleaService;
        this.documentoService = documentoService;
        this.auditService = auditService;
    }

    private Long resolveUserId() {
        SaedContext ctx = SaedContextHolder.getContext();
        return (ctx != null && ctx.getUserId() != null) ? ctx.getUserId() : 1L;
    }

    private void checkTenantScope(ActaDTO acta) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) return;

        String role = ctx.getRoleCode();
        if ("SUPERADMIN".equalsIgnoreCase(role)) return;

        if (ctx.getOrganizationId() != null) {
            AsambleaDTO asamblea = asambleaService.obtenerDetalleAsamblea(acta.getIdAsamblea());
            if (asamblea.getIdOrganizacion() != null && !ctx.getOrganizationId().equals(asamblea.getIdOrganizacion())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: acta pertenece a otra organización");
            }
        }

        if (ctx.getPropertyId() != null && !ctx.getPropertyId().equals(acta.getIdPropiedad())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: acta pertenece a otra propiedad");
        }
    }

    private void checkResidentReadPermission(ActaDTO acta) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) return;

        String role = ctx.getRoleCode();
        if ("RESIDENTE".equalsIgnoreCase(role) || "PROPIETARIO_UNIDAD".equalsIgnoreCase(role)
                || "PROPIETARIO".equalsIgnoreCase(role) || "RESIDENTE_CONVIVENCIA".equalsIgnoreCase(role)) {
            if (!"PUBLICADA_OFICIAL".equals(acta.getEstado())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Los residentes solo pueden acceder a actas PUBLICADAS_OFICIAL");
            }
            if (ctx.getPropertyId() != null && !ctx.getPropertyId().equals(acta.getIdPropiedad())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para consultar actas de otra propiedad");
            }
        } else if ("PORTERO".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El rol PORTERO no tiene acceso a las actas de asamblea");
        } else {
            checkTenantScope(acta);
        }
    }

    private ActaDTO enrichActaWithGovernanceData(ActaDTO acta) {
        if (acta == null) return null;

        Long idAsamblea = acta.getIdAsamblea();
        List<VotacionDTO> votaciones = asambleaRepository.findVotaciones(idAsamblea);
        acta.setVotaciones(votaciones);
        acta.setTotalVotaciones(votaciones.size());

        List<AsistenciaDTO> asistencias = asambleaRepository.findAsistencias(idAsamblea);
        acta.setTotalAsistentes(asistencias.size());

        BigDecimal totalCoef = asistencias.stream()
                .map(AsistenciaDTO::getCoeficientePonderado)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        acta.setTotalCoeficienteAsistentes(totalCoef);

        List<PoderDTO> poderes = asambleaRepository.findPoderes(idAsamblea);
        long poderesAprobados = poderes.stream()
                .filter(p -> "APROBADO".equalsIgnoreCase(p.getEstado()))
                .count();
        acta.setTotalPoderesAprobados((int) poderesAprobados);

        return acta;
    }

    @Override
    @Transactional
    public ActaDTO crearBorrador(ActaCreateRequestDTO request) {
        AsambleaDTO asamblea = asambleaService.obtenerDetalleAsamblea(request.getIdAsamblea());

        if ("CANCELADA".equals(asamblea.getEstado()) || "BORRADOR".equals(asamblea.getEstado())) {
            throw new IllegalStateException("No se puede crear un acta para una asamblea en estado " + asamblea.getEstado());
        }

        if (actaRepository.existsByAsambleaId(request.getIdAsamblea())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un acta registrada para la asamblea ID: " + request.getIdAsamblea());
        }

        Long userId = resolveUserId();
        Long idActa;
        try {
            idActa = actaRepository.createActa(request, userId);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicto de concurrencia: ya existe un acta para la asamblea ID: " + request.getIdAsamblea(), e);
        }

        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();

        auditService.recordSuccess(
                userId, orgId, asamblea.getIdPropiedad(),
                "CREAR_ACTA", "ACTAS_ASAMBLEA", idActa,
                "127.0.0.1", "SAED-Core", null, "BORRADOR"
        );

        log.info("Acta ID: {} creada en estado BORRADOR para asamblea ID: {}", idActa, request.getIdAsamblea());
        return obtenerPorId(idActa);
    }

    @Override
    @Transactional
    public ActaDTO actualizarActa(Long idActa, ActaUpdateRequestDTO request) {
        actaRepository.lockActaForUpdate(idActa);
        ActaDTO existing = actaRepository.findById(idActa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Acta no encontrada con ID: " + idActa));

        checkTenantScope(existing);

        if ("PUBLICADA_OFICIAL".equals(existing.getEstado())) {
            throw new IllegalStateException("No se puede modificar un acta que ya ha sido publicada oficialmente");
        }

        actaRepository.updateActa(idActa, request);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null) ? ctx.getOrganizationId() : null;

        auditService.recordSuccess(
                userId, orgId, existing.getIdPropiedad(),
                "MODIFICAR_ACTA", "ACTAS_ASAMBLEA", idActa,
                "127.0.0.1", "SAED-Core", existing.getEstado(), existing.getEstado()
        );

        log.info("Acta ID: {} modificada por usuario ID: {}", idActa, userId);
        return obtenerPorId(idActa);
    }

    @Override
    @Transactional
    public ActaDTO cambiarEstado(Long idActa, ActaEstadoUpdateRequestDTO request) {
        actaRepository.lockActaForUpdate(idActa);
        ActaDTO existing = actaRepository.findById(idActa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Acta no encontrada con ID: " + idActa));

        checkTenantScope(existing);

        String estadoActual = existing.getEstado();
        String nuevoEstado = request.getNuevoEstado();

        if (estadoActual.equals(nuevoEstado)) {
            return enrichActaWithGovernanceData(existing);
        }

        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(estadoActual, Collections.emptySet());
        if (!allowed.contains(nuevoEstado)) {
            throw new IllegalStateException(
                    String.format("Transición de estado no permitida para el acta: %s -> %s", estadoActual, nuevoEstado)
            );
        }

        AsambleaDTO asamblea = asambleaService.obtenerDetalleAsamblea(existing.getIdAsamblea());

        // Validaciones estrictas al publicar
        if ("PUBLICADA_OFICIAL".equals(nuevoEstado)) {
            if (!"FINALIZADA".equals(asamblea.getEstado())) {
                throw new IllegalStateException("Solo se puede publicar el acta oficial de una asamblea FINALIZADA");
            }

            List<VotacionDTO> votaciones = asambleaRepository.findVotaciones(existing.getIdAsamblea());
            boolean hasOpenVotes = votaciones.stream().anyMatch(v -> "ABIERTA".equalsIgnoreCase(v.getEstado()));
            if (hasOpenVotes) {
                throw new IllegalStateException("No se puede publicar el acta si existen votaciones aún ABIERTAS en la asamblea");
            }

            Long idDoc = (request.getIdDocumento() != null) ? request.getIdDocumento() : existing.getIdDocumento();
            String docUrl = (request.getDocumentoFirmadoUrl() != null) ? request.getDocumentoFirmadoUrl() : existing.getDocumentoFirmadoUrl();

            if (idDoc == null && (docUrl == null || docUrl.isBlank())) {
                throw new IllegalStateException("Se requiere adjuntar o vincular el documento oficial del acta antes de su publicación");
            }

            if (request.getIdDocumento() != null || request.getDocumentoFirmadoUrl() != null) {
                actaRepository.asociarDocumento(idActa, idDoc, docUrl);
            }
        }

        actaRepository.updateEstado(idActa, nuevoEstado);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();

        auditService.recordSuccess(
                userId, orgId, asamblea.getIdPropiedad(),
                "CAMBIO_ESTADO_ACTA", "ACTAS_ASAMBLEA", idActa,
                "127.0.0.1", "SAED-Core", estadoActual, nuevoEstado
        );

        log.info("Acta ID: {} cambió estado de {} a {} por usuario ID: {}", idActa, estadoActual, nuevoEstado, userId);
        return obtenerPorId(idActa);
    }

    @Override
    @Transactional
    public ActaDTO asociarDocumento(Long idActa, Long idDocumento, String documentoUrl) {
        actaRepository.lockActaForUpdate(idActa);
        ActaDTO existing = actaRepository.findById(idActa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Acta no encontrada con ID: " + idActa));

        checkTenantScope(existing);

        if (idDocumento != null) {
            // Valida existencia del documento en F10-01
            documentoService.getDocumentoById(idDocumento);
        }

        actaRepository.asociarDocumento(idActa, idDocumento, documentoUrl);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null) ? ctx.getOrganizationId() : null;

        auditService.recordSuccess(
                userId, orgId, existing.getIdPropiedad(),
                "ASOCIAR_DOCUMENTO_ACTA", "ACTAS_ASAMBLEA", idActa,
                "127.0.0.1", "SAED-Core", null, "DOC_" + idDocumento
        );

        log.info("Documento ID: {} asociado al Acta ID: {}", idDocumento, idActa);
        return obtenerPorId(idActa);
    }

    @Override
    @Transactional
    public ActaDTO subirYAsociarDocumento(Long idActa, MultipartFile file) {
        actaRepository.lockActaForUpdate(idActa);
        ActaDTO existing = actaRepository.findById(idActa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Acta no encontrada con ID: " + idActa));

        checkTenantScope(existing);

        String titulo = "Acta Oficial - " + existing.getNumeroActa();
        String descripcion = "Acta oficial de la asamblea " + existing.getAsambleaTitulo();

        Long idDoc = documentoService.uploadDocumentoMultipart(
                file, titulo, "ACTA_ASAMBLEA", descripcion, "S", "RESIDENTE"
        );

        return asociarDocumento(idActa, idDoc, null);
    }

    @Override
    public ActaDTO obtenerPorId(Long idActa) {
        ActaDTO acta = actaRepository.findById(idActa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Acta no encontrada con ID: " + idActa));

        checkResidentReadPermission(acta);
        return enrichActaWithGovernanceData(acta);
    }

    @Override
    public ActaDTO obtenerPorAsamblea(Long idAsamblea) {
        ActaDTO acta = actaRepository.findByAsambleaId(idAsamblea)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe acta registrada para la asamblea ID: " + idAsamblea));

        checkResidentReadPermission(acta);
        return enrichActaWithGovernanceData(acta);
    }

    @Override
    public List<ActaDTO> listarAdmin(Long idPropiedad, String estado) {
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null) ? ctx.getOrganizationId() : null;
        Long propId = (idPropiedad != null) ? idPropiedad : ((ctx != null) ? ctx.getPropertyId() : null);

        List<ActaDTO> list = actaRepository.findAllAdmin(propId, orgId, estado);
        list.forEach(this::enrichActaWithGovernanceData);
        return list;
    }

    @Override
    public List<ActaDTO> listarResidente() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getPropertyId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario sin asignación de propiedad activa");
        }

        String role = ctx.getRoleCode();
        if ("PORTERO".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El rol PORTERO no tiene acceso a actas de asamblea");
        }

        List<ActaDTO> list = actaRepository.findAllResidente(ctx.getPropertyId());
        list.forEach(this::enrichActaWithGovernanceData);
        return list;
    }

    @Override
    public DocumentoDescarga descargarDocumento(Long idActa) {
        ActaDTO acta = obtenerPorId(idActa); // aplica control de permisos de lectura
        if (acta.getIdDocumento() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El acta no cuenta con un documento digital cargado en el repositorio");
        }
        return documentoService.downloadDocumento(acta.getIdDocumento());
    }
}
