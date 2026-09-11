package com.saed.backend.asambleas.service.impl;

import com.saed.backend.asambleas.dto.*;
import com.saed.backend.asambleas.repository.AsambleaRepository;
import com.saed.backend.asambleas.service.AsambleaService;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class AsambleaServiceImpl implements AsambleaService {

    private static final Logger log = LoggerFactory.getLogger(AsambleaServiceImpl.class);

    private final AsambleaRepository repository;

    public AsambleaServiceImpl(AsambleaRepository repository) {
        this.repository = repository;
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

    @Override
    public List<AsambleaDTO> listarAsambleas() {
        Long propId = resolvePropertyId(null);
        return repository.findAllByPropiedad(propId);
    }

    @Override
    public AsambleaDTO obtenerDetalleAsamblea(Long idAsamblea) {
        return repository.findById(idAsamblea)
                .orElseThrow(() -> new IllegalArgumentException("Asamblea no encontrada con ID: " + idAsamblea));
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
        log.info("Convocada nueva asamblea ID: {} para propiedad ID: {}", idAsamblea, propId);
        return obtenerDetalleAsamblea(idAsamblea);
    }

    @Override
    @Transactional
    public AsambleaDTO actualizarEstado(Long idAsamblea, String nuevoEstado) {
        AsambleaDTO asamblea = obtenerDetalleAsamblea(idAsamblea);

        if ("FINALIZADA".equals(asamblea.getEstado()) || "CANCELADA".equals(asamblea.getEstado())) {
            throw new IllegalStateException("No se puede modificar el estado de una asamblea " + asamblea.getEstado());
        }

        repository.updateEstado(idAsamblea, nuevoEstado);
        log.info("Asamblea ID: {} cambió estado de {} a {}", idAsamblea, asamblea.getEstado(), nuevoEstado);
        return obtenerDetalleAsamblea(idAsamblea);
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
        repository.retirarAsistencia(idAsamblea, idUnidad);

        BigDecimal nuevoQuorum = repository.calcularQuorumAlcanzadoPct(idAsamblea, asamblea.getIdPropiedad());
        repository.updateQuorumAlcanzado(idAsamblea, nuevoQuorum);
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
        obtenerDetalleAsamblea(idAsamblea);

        if (Objects.equals(request.getIdPersonaPropietario(), request.getIdPersonaApoderado())) {
            throw new IllegalArgumentException("El propietario no puede otorgarse poder de representación a sí mismo");
        }

        if (repository.existePoderUnidad(idAsamblea, request.getIdUnidad())) {
            throw new IllegalArgumentException("Ya existe un poder radicado para la unidad ID: " + request.getIdUnidad() + " en esta asamblea");
        }

        Long idPoder = repository.registrarPoder(idAsamblea, request);
        log.info("Poder radicado ID: {} para asamblea ID: {} y unidad ID: {}", idPoder, idAsamblea, request.getIdUnidad());

        return repository.findPoderes(idAsamblea).stream()
                .filter(p -> Objects.equals(p.getIdPoder(), idPoder))
                .findFirst()
                .orElse(null);
    }

    @Override
    @Transactional
    public PoderDTO decidirPoder(Long idPoder, String nuevoEstado) {
        Long userId = resolveUserId();
        repository.decidirPoder(idPoder, nuevoEstado, userId);
        log.info("Poder ID: {} decidido con estado: {} por usuario ID: {}", idPoder, nuevoEstado, userId);

        // Devolver el poder actualizado
        // Buscamos a través de la lista de la asamblea correspondiente
        Long idAsamblea = repository.findPoderes(1L).stream()
                .filter(p -> Objects.equals(p.getIdPoder(), idPoder))
                .map(PoderDTO::getIdAsamblea)
                .findFirst()
                .orElse(null);

        if (idAsamblea != null) {
            return repository.findPoderes(idAsamblea).stream()
                    .filter(p -> Objects.equals(p.getIdPoder(), idPoder))
                    .findFirst()
                    .orElse(null);
        }
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

        return repository.findVotacionById(idVotacion)
                .orElseThrow(() -> new IllegalArgumentException("Votación creada no encontrada"));
    }

    @Override
    @Transactional
    public VotacionDTO cerrarVotacion(Long idVotacion) {
        VotacionDTO votacion = repository.findVotacionById(idVotacion)
                .orElseThrow(() -> new IllegalArgumentException("Votación no encontrada con ID: " + idVotacion));

        if (!"ABIERTA".equals(votacion.getEstado())) {
            throw new IllegalStateException("La votación ya se encuentra " + votacion.getEstado());
        }

        repository.cerrarVotacion(idVotacion);
        log.info("Votación ID: {} cerrada con éxito", idVotacion);

        return repository.findVotacionById(idVotacion)
                .orElseThrow(() -> new IllegalArgumentException("Votación no encontrada tras cerrar"));
    }

    @Override
    @Transactional
    public void emitirVoto(Long idVotacion, VotoRequestDTO request) {
        VotacionDTO votacion = repository.findVotacionById(idVotacion)
                .orElseThrow(() -> new IllegalArgumentException("Votación no encontrada con ID: " + idVotacion));

        if (!"ABIERTA".equals(votacion.getEstado())) {
            throw new IllegalStateException("No se puede votar en una votación en estado " + votacion.getEstado());
        }

        if (repository.existeVotoUnidad(idVotacion, request.getIdUnidad())) {
            throw new IllegalArgumentException("La unidad ID: " + request.getIdUnidad() + " ya emitió su voto en esta votación");
        }

        BigDecimal coeficiente = request.getCoeficienteVoto();
        if (coeficiente == null || coeficiente.compareTo(BigDecimal.ZERO) <= 0) {
            coeficiente = repository.findCoeficienteUnidad(request.getIdUnidad());
        }

        repository.registrarVoto(idVotacion, request, coeficiente);
        log.info("Voto registrado en votación ID: {} por unidad ID: {} con opción: {}", idVotacion, request.getIdUnidad(), request.getOpcionVoto());
    }
}
