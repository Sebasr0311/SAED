package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.*;
import com.saed.backend.asambleas.repository.AsambleaRepository;
import com.saed.backend.asambleas.service.AsambleaService;
import com.saed.backend.audit.AuditService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AsambleaServiceImpl implements AsambleaService {

    private static final Logger log = LoggerFactory.getLogger(AsambleaServiceImpl.class);

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "BORRADOR", Set.of("CONVOCADA", "CANCELADA"),
            "CONVOCADA", Set.of("EN_CURSO", "CANCELADA", "BORRADOR"),
            "EN_CURSO", Set.of("EN_RECESO", "FINALIZADA", "CANCELADA"),
            "EN_RECESO", Set.of("EN_CURSO", "FINALIZADA", "CANCELADA")
    );

    private final AsambleaRepository repository;
    private final AuditService auditService;

    public AsambleaServiceImpl(AsambleaRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    private Long resolvePropertyId(Long overridePropId) {
        if (overridePropId != null) return overridePropId;
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx != null && ctx.getPropertyId() != null) {
            return ctx.getPropertyId();
        }
        throw new IllegalStateException("No hay una propiedad activa en el contexto de sesión");
    }

    private Long resolveUserId() {
        SaedContext ctx = SaedContextHolder.getContext();
        return (ctx != null && ctx.getUserId() != null) ? ctx.getUserId() : 1L;
    }

    private void checkTenantScope(AsambleaDTO asamblea) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null) return;
        String role = ctx.getRoleCode();
        if ("SUPERADMIN".equalsIgnoreCase(role)) return;

        if (ctx.getOrganizationId() != null && asamblea.getIdOrganizacion() != null
                && !ctx.getOrganizationId().equals(asamblea.getIdOrganizacion())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para acceder a asambleas de otra organización");
        }

        if ("ADMIN_PROPIEDAD".equalsIgnoreCase(role)) {
            if (ctx.getPropertyId() != null && asamblea.getIdPropiedad() != null
                    && !ctx.getPropertyId().equals(asamblea.getIdPropiedad())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado para acceder a asambleas de otra propiedad");
            }
        }
    }

    @Override
    public List<AsambleaDTO> listarAsambleas() {
        Long propId = resolvePropertyId(null);
        return repository.findAllByPropiedad(propId);
    }

    @Override
    public AsambleaDTO obtenerDetalleAsamblea(Long idAsamblea) {
        AsambleaDTO asamblea = repository.findById(idAsamblea)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asamblea no encontrada con ID: " + idAsamblea));
        checkTenantScope(asamblea);
        return asamblea;
    }

    @Override
    @Transactional
    public AsambleaDTO convocarAsamblea(AsambleaCreateRequestDTO request) {
        Long propId = resolvePropertyId(request.getIdPropiedad());
        Long userId = resolveUserId();

        if (request.getQuorumRequeridoPct() == null) {
            request.setQuorumRequeridoPct(BigDecimal.valueOf(50.01));
        }

        Long idAsamblea = repository.createAsamblea(request, propId, userId);
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : repository.findOrganizacionPropiedad(propId);

        auditService.recordSuccess(
                userId, orgId, propId,
                "CONVOCAR_ASAMBLEA", "ASAMBLEAS", idAsamblea,
                "127.0.0.1", "SAED-Core", null, (request.getEstado() != null ? request.getEstado() : "CONVOCADA")
        );

        log.info("Convocada nueva asamblea ID: {} para propiedad ID: {}", idAsamblea, propId);
        return repository.findById(idAsamblea).orElseThrow();
    }

    @Override
    @Transactional
    public AsambleaDTO actualizarAsamblea(Long idAsamblea, AsambleaUpdateRequestDTO request) {
        AsambleaDTO asamblea = obtenerDetalleAsamblea(idAsamblea);

        if (!"BORRADOR".equals(asamblea.getEstado()) && !"CONVOCADA".equals(asamblea.getEstado())) {
            throw new IllegalStateException("Solo se puede editar una asamblea en estado BORRADOR o CONVOCADA");
        }

        repository.lockAsambleaForUpdate(idAsamblea);
        repository.updateAsamblea(idAsamblea, request);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();

        auditService.recordSuccess(
                userId, orgId, asamblea.getIdPropiedad(),
                "ACTUALIZAR_ASAMBLEA", "ASAMBLEAS", idAsamblea,
                "127.0.0.1", "SAED-Core", asamblea.getTitulo(), request.getTitulo()
        );

        log.info("Asamblea ID: {} actualizada exitosamente", idAsamblea);
        return repository.findById(idAsamblea).orElseThrow();
    }

    @Override
    @Transactional
    public AsambleaDTO actualizarEstado(Long idAsamblea, String nuevoEstado) {
        AsambleaDTO asamblea = obtenerDetalleAsamblea(idAsamblea);

        if ("FINALIZADA".equals(asamblea.getEstado()) || "CANCELADA".equals(asamblea.getEstado())) {
            throw new IllegalStateException("No se puede modificar el estado de una asamblea " + asamblea.getEstado());
        }

        Set<String> allowed = ALLOWED_TRANSITIONS.get(asamblea.getEstado());
        if (allowed == null || !allowed.contains(nuevoEstado)) {
            throw new IllegalStateException("Transición de estado no permitida: " + asamblea.getEstado() + " -> " + nuevoEstado);
        }

        repository.lockAsambleaForUpdate(idAsamblea);
        repository.updateEstado(idAsamblea, nuevoEstado);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();

        auditService.recordSuccess(
                userId, orgId, asamblea.getIdPropiedad(),
                "CAMBIO_ESTADO_ASAMBLEA", "ASAMBLEAS", idAsamblea,
                "127.0.0.1", "SAED-Core", asamblea.getEstado(), nuevoEstado
        );

        log.info("Asamblea ID: {} cambió estado de {} a {}", idAsamblea, asamblea.getEstado(), nuevoEstado);
        return repository.findById(idAsamblea).orElseThrow();
    }

    @Override
    public QuorumLiveDTO obtenerQuorumEnVivo(Long idAsamblea) {
        AsambleaDTO asamblea = obtenerDetalleAsamblea(idAsamblea);
        BigDecimal quorumAlcanzado = repository.calcularQuorumAlcanzadoPct(idAsamblea, asamblea.getIdPropiedad());

        boolean tieneQuorum = quorumAlcanzado.compareTo(asamblea.getQuorumRequeridoPct()) >= 0;

        List<AsistenciaDTO> asistencias = repository.findAsistencias(idAsamblea);
        long presentes = asistencias.stream().filter(a -> a.getHoraRetiro() == null).count();
        BigDecimal coefPresentes = asistencias.stream()
                .filter(a -> a.getHoraRetiro() == null && a.getCoeficientePonderado() != null)
                .map(AsistenciaDTO::getCoeficientePonderado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return QuorumLiveDTO.builder()
                .idAsamblea(idAsamblea)
                .quorumRequeridoPct(asamblea.getQuorumRequeridoPct())
                .quorumAlcanzadoPct(quorumAlcanzado)
                .tieneQuorum(tieneQuorum)
                .totalUnidadesRegistradas((int) presentes)
                .totalCoeficienteRegistrado(coefPresentes)
                .estadoAsamblea(asamblea.getEstado())
                .build();
    }

    @Override
    public List<AsistenciaDTO> listarAsistencias(Long idAsamblea) {
        obtenerDetalleAsamblea(idAsamblea);
        return repository.findAsistencias(idAsamblea);
    }

    @Override
    @Transactional
    public AsistenciaDTO registrarAsistencia(Long idAsamblea, AsistenciaRequestDTO request) {
        AsambleaDTO asamblea = obtenerDetalleAsamblea(idAsamblea);

        if ("FINALIZADA".equals(asamblea.getEstado()) || "CANCELADA".equals(asamblea.getEstado())) {
            throw new IllegalStateException("No se puede registrar asistencia en una asamblea " + asamblea.getEstado());
        }

        repository.lockAsambleaForUpdate(idAsamblea);

        Long propUnidad = repository.findPropiedadUnidad(request.getIdUnidad());
        if (propUnidad == null || !propUnidad.equals(asamblea.getIdPropiedad())) {
            throw new IllegalArgumentException("La unidad ID " + request.getIdUnidad() + " no pertenece a la copropiedad de la asamblea");
        }

        if (repository.existeAsistenciaUnidad(idAsamblea, request.getIdUnidad())) {
            throw new IllegalArgumentException("La unidad ID " + request.getIdUnidad() + " ya tiene asistencia registrada en esta asamblea");
        }

        BigDecimal coeficiente = request.getCoeficientePonderado();
        if (coeficiente == null || coeficiente.compareTo(BigDecimal.ZERO) <= 0) {
            coeficiente = repository.findCoeficienteUnidad(request.getIdUnidad());
        }

        Long idAsistencia = repository.registrarAsistencia(idAsamblea, request, coeficiente);

        // Recalcular quórum alcanzado y actualizar en cabecera
        BigDecimal nuevoQuorum = repository.calcularQuorumAlcanzadoPct(idAsamblea, asamblea.getIdPropiedad());
        repository.updateQuorumAlcanzado(idAsamblea, nuevoQuorum);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();

        auditService.recordSuccess(
                userId, orgId, asamblea.getIdPropiedad(),
                "REGISTRAR_ASISTENCIA", "ASISTENCIAS_ASAMBLEA", idAsistencia,
                "127.0.0.1", "SAED-Core", null, "PRESENTE"
        );

        log.info("Asistencia registrada ID: {} en asamblea ID: {}. Nuevo quórum: {}%", idAsistencia, idAsamblea, nuevoQuorum);

        return repository.findAsistencias(idAsamblea).stream()
                .filter(a -> Objects.equals(a.getIdAsistencia(), idAsistencia))
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional
    public void retirarAsistencia(Long idAsamblea, Long idUnidad) {
        AsambleaDTO asamblea = obtenerDetalleAsamblea(idAsamblea);

        if ("FINALIZADA".equals(asamblea.getEstado()) || "CANCELADA".equals(asamblea.getEstado())) {
            throw new IllegalStateException("No se puede retirar asistencia en una asamblea " + asamblea.getEstado());
        }

        repository.lockAsambleaForUpdate(idAsamblea);
        repository.retirarAsistencia(idAsamblea, idUnidad);

        BigDecimal nuevoQuorum = repository.calcularQuorumAlcanzadoPct(idAsamblea, asamblea.getIdPropiedad());
        repository.updateQuorumAlcanzado(idAsamblea, nuevoQuorum);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();

        auditService.recordSuccess(
                userId, orgId, asamblea.getIdPropiedad(),
                "RETIRAR_ASISTENCIA", "ASISTENCIAS_ASAMBLEA", idUnidad,
                "127.0.0.1", "SAED-Core", "PRESENTE", "RETIRADO"
        );

        log.info("Retiro de unidad ID: {} en asamblea ID: {}. Nuevo quórum: {}%", idUnidad, idAsamblea, nuevoQuorum);
    }

    @Override
    public List<PoderDTO> listarPoderes(Long idAsamblea) {
        obtenerDetalleAsamblea(idAsamblea);
        return repository.findPoderes(idAsamblea);
    }

    @Override
    @Transactional
    public PoderDTO radicarPoder(Long idAsamblea, PoderRequestDTO request) {
        AsambleaDTO asamblea = obtenerDetalleAsamblea(idAsamblea);

        if (Objects.equals(request.getIdPersonaPropietario(), request.getIdPersonaApoderado())) {
            throw new IllegalArgumentException("El propietario no puede otorgarse poder de representación a sí mismo");
        }

        Long propUnidad = repository.findPropiedadUnidad(request.getIdUnidad());
        if (propUnidad == null || !propUnidad.equals(asamblea.getIdPropiedad())) {
            throw new IllegalArgumentException("La unidad ID " + request.getIdUnidad() + " no pertenece a la copropiedad de la asamblea");
        }

        if (repository.existePoderUnidad(idAsamblea, request.getIdUnidad())) {
            throw new IllegalArgumentException("Ya existe un poder radicado para la unidad ID: " + request.getIdUnidad() + " en esta asamblea");
        }

        Long idPoder = repository.registrarPoder(idAsamblea, request);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();

        auditService.recordSuccess(
                userId, orgId, asamblea.getIdPropiedad(),
                "RADICAR_PODER", "PODERES_REPRESENTACION", idPoder,
                "127.0.0.1", "SAED-Core", null, "RADICADO"
        );

        log.info("Poder radicado ID: {} para asamblea ID: {} y unidad ID: {}", idPoder, idAsamblea, request.getIdUnidad());

        return repository.findPoderes(idAsamblea).stream()
                .filter(p -> Objects.equals(p.getIdPoder(), idPoder))
                .findFirst()
                .orElseGet(() -> PoderDTO.builder()
                        .idPoder(idPoder)
                        .idAsamblea(idAsamblea)
                        .idUnidad(request.getIdUnidad())
                        .idPersonaPropietario(request.getIdPersonaPropietario())
                        .idPersonaApoderado(request.getIdPersonaApoderado())
                        .documentoPoderUrl(request.getDocumentoPoderUrl())
                        .estado("PENDIENTE_REVISION")
                        .build());
    }

    @Override
    @Transactional
    public PoderDTO decidirPoder(Long idPoder, String nuevoEstado) {
        if (!"APROBADO".equals(nuevoEstado) && !"RECHAZADO".equals(nuevoEstado)) {
            throw new IllegalArgumentException("Estado de decisión de poder inválido: " + nuevoEstado);
        }

        Long userId = resolveUserId();
        repository.decidirPoder(idPoder, nuevoEstado, userId);
        log.info("Poder ID: {} decidido con estado: {} por usuario ID: {}", idPoder, nuevoEstado, userId);

        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null) ? ctx.getOrganizationId() : null;
        Long propId = (ctx != null) ? ctx.getPropertyId() : null;

        auditService.recordSuccess(
                userId, orgId, propId,
                "DECIDIR_PODER", "PODERES_REPRESENTACION", idPoder,
                "127.0.0.1", "SAED-Core", "RADICADO", nuevoEstado
        );

        return PoderDTO.builder().idPoder(idPoder).estado(nuevoEstado).validadoPor(userId).build();
    }

    @Override
    public List<VotacionDTO> listarVotaciones(Long idAsamblea) {
        obtenerDetalleAsamblea(idAsamblea);
        return repository.findVotaciones(idAsamblea);
    }

    @Override
    @Transactional
    public VotacionDTO crearPuntoVotacion(Long idAsamblea, VotacionCreateRequestDTO request) {
        AsambleaDTO asamblea = obtenerDetalleAsamblea(idAsamblea);

        if (!"EN_CURSO".equals(asamblea.getEstado()) && !"CONVOCADA".equals(asamblea.getEstado())) {
            throw new IllegalStateException("Solo se pueden crear puntos de votación en asambleas CONVOCADAS o EN_CURSO");
        }

        Long idVotacion = repository.createVotacion(idAsamblea, request);
        log.info("Punto de votación ID: {} creado en asamblea ID: {}", idVotacion, idAsamblea);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();

        auditService.recordSuccess(
                userId, orgId, asamblea.getIdPropiedad(),
                "CREAR_PUNTO_VOTACION", "VOTACIONES", idVotacion,
                "127.0.0.1", "SAED-Core", null, "ABIERTA"
        );

        return repository.findVotacionById(idVotacion)
                .orElseThrow(() -> new IllegalArgumentException("Votación creada no encontrada"));
    }

    @Override
    @Transactional
    public VotacionDTO cerrarVotacion(Long idVotacion) {
        repository.lockVotacionForUpdate(idVotacion);

        VotacionDTO votacion = repository.findVotacionById(idVotacion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Votación no encontrada con ID: " + idVotacion));

        AsambleaDTO asamblea = obtenerDetalleAsamblea(votacion.getIdAsamblea());

        if (!"ABIERTA".equals(votacion.getEstado())) {
            throw new IllegalStateException("La votación ya se encuentra " + votacion.getEstado());
        }

        repository.cerrarVotacion(idVotacion);
        log.info("Votación ID: {} cerrada con éxito", idVotacion);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();
        Long propId = (ctx != null && ctx.getPropertyId() != null) ? ctx.getPropertyId() : asamblea.getIdPropiedad();

        auditService.recordSuccess(
                userId, orgId, propId,
                "CERRAR_VOTACION", "VOTACIONES", idVotacion,
                "127.0.0.1", "SAED-Core", "ABIERTA", "CERRADA"
        );

        return repository.findVotacionById(idVotacion)
                .orElseThrow(() -> new IllegalArgumentException("Votación no encontrada tras cerrar"));
    }

    @Override
    @Transactional
    public VotacionDTO anularVotacion(Long idVotacion) {
        repository.lockVotacionForUpdate(idVotacion);

        VotacionDTO votacion = repository.findVotacionById(idVotacion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Votación no encontrada con ID: " + idVotacion));

        AsambleaDTO asamblea = obtenerDetalleAsamblea(votacion.getIdAsamblea());

        if ("ANULADA".equals(votacion.getEstado())) {
            throw new IllegalStateException("La votación ya se encuentra ANULADA");
        }

        repository.anularVotacion(idVotacion);
        log.info("Votación ID: {} anulada con éxito", idVotacion);

        Long userId = resolveUserId();
        SaedContext ctx = SaedContextHolder.getContext();
        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();
        Long propId = (ctx != null && ctx.getPropertyId() != null) ? ctx.getPropertyId() : asamblea.getIdPropiedad();

        auditService.recordSuccess(
                userId, orgId, propId,
                "ANULAR_VOTACION", "VOTACIONES", idVotacion,
                "127.0.0.1", "SAED-Core", votacion.getEstado(), "ANULADA"
        );

        return repository.findVotacionById(idVotacion)
                .orElseThrow(() -> new IllegalArgumentException("Votación no encontrada tras anular"));
    }

    @Override
    @Transactional
    public void emitirVoto(Long idVotacion, VotoRequestDTO request) {
        repository.lockVotacionForUpdate(idVotacion);

        VotacionDTO votacion = repository.findVotacionById(idVotacion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Votación no encontrada con ID: " + idVotacion));

        AsambleaDTO asamblea = obtenerDetalleAsamblea(votacion.getIdAsamblea());

        if (!"EN_CURSO".equals(asamblea.getEstado()) && !"CONVOCADA".equals(asamblea.getEstado())) {
            throw new IllegalStateException("Solo se puede votar en asambleas CONVOCADAS o EN_CURSO (actual: " + asamblea.getEstado() + ")");
        }

        if (!"ABIERTA".equals(votacion.getEstado())) {
            throw new IllegalStateException("No se puede votar en una votación en estado " + votacion.getEstado());
        }

        // Cross-check unit coproperty
        Long propUnidad = repository.findPropiedadUnidad(request.getIdUnidad());
        if (propUnidad == null || !propUnidad.equals(asamblea.getIdPropiedad())) {
            throw new IllegalArgumentException("La unidad ID " + request.getIdUnidad() + " no pertenece a la copropiedad de la asamblea");
        }

        SaedContext ctx = SaedContextHolder.getContext();
        String role = (ctx != null) ? ctx.getRoleCode() : null;
        Long userId = resolveUserId();

        // Check caller eligibility and persona
        Long callerPersonaId = repository.findPersonaUsuario(userId);
        boolean isAdmin = role != null && (
                "SUPERADMIN".equalsIgnoreCase(role) ||
                "ADMIN_ORGANIZACION".equalsIgnoreCase(role) ||
                "ADMIN_PROPIEDAD".equalsIgnoreCase(role)
        );

        if (!isAdmin) {
            if (callerPersonaId == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario no tiene una persona asociada para votar");
            }

            boolean isOwnerOrInhabitant = repository.usuarioPerteneceAUnidad(userId, request.getIdUnidad(), callerPersonaId);
            boolean isApprovedProxy = repository.esApoderadoAprobado(asamblea.getIdAsamblea(), request.getIdUnidad(), callerPersonaId);

            if (!isOwnerOrInhabitant && !isApprovedProxy) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene autorización para emitir voto por la unidad ID " + request.getIdUnidad());
            }

            // Always enforce caller's persona ID to avoid spoofing
            request.setIdPersonaVotante(callerPersonaId);
        } else {
            if (request.getIdPersonaVotante() == null) {
                request.setIdPersonaVotante(callerPersonaId != null ? callerPersonaId : 1L);
            }
        }

        if (repository.existeVotoUnidad(idVotacion, request.getIdUnidad())) {
            throw new IllegalArgumentException("La unidad ID: " + request.getIdUnidad() + " ya emitió su voto en esta votación");
        }

        BigDecimal coeficiente = request.getCoeficienteVoto();
        if (coeficiente == null || coeficiente.compareTo(BigDecimal.ZERO) <= 0) {
            coeficiente = repository.findCoeficienteUnidad(request.getIdUnidad());
        }

        Long idVoto = repository.registrarVoto(idVotacion, request, coeficiente);
        log.info("Voto registrado en votación ID: {} por unidad ID: {} con opción: {}", idVotacion, request.getIdUnidad(), request.getOpcionVoto());

        Long orgId = (ctx != null && ctx.getOrganizationId() != null) ? ctx.getOrganizationId() : asamblea.getIdOrganizacion();
        Long propId = (ctx != null && ctx.getPropertyId() != null) ? ctx.getPropertyId() : asamblea.getIdPropiedad();

        auditService.recordSuccess(
                userId, orgId, propId,
                "EMITIR_VOTO", "VOTOS", idVoto,
                "127.0.0.1", "SAED-Core", null, request.getOpcionVoto()
        );
    }
}
