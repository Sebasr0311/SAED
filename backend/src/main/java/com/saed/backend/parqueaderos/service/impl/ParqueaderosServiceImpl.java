package com.saed.backend.parqueaderos.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoRequestDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoMasivoRequestDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoRequestDTO;
import com.saed.backend.parqueaderos.repository.ParqueaderosRepository;
import com.saed.backend.parqueaderos.service.ParqueaderosService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ParqueaderosServiceImpl implements ParqueaderosService {

    private final ParqueaderosRepository parqueaderosRepository;

    public ParqueaderosServiceImpl(ParqueaderosRepository parqueaderosRepository) {
        this.parqueaderosRepository = parqueaderosRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParqueaderoDTO> getParqueaderos() {
        return parqueaderosRepository.getParqueaderos();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParqueaderoDTO> getParqueaderos(String estado, String tipo) {
        return parqueaderosRepository.getParqueaderos(estado, tipo);
    }

    @Override
    @Transactional(readOnly = true)
    public ParqueaderoDTO getParqueaderoById(Long id) {
        return parqueaderosRepository.getParqueaderoById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Parqueadero no encontrado"));
    }

    @Override
    @Transactional
    public ParqueaderoDTO registrarParqueadero(ParqueaderoRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        return parqueaderosRepository.registrarParqueadero(request, ctx.getPropertyId());
    }

    @Override
    @Transactional
    public List<ParqueaderoDTO> registrarParqueaderosMasivo(ParqueaderoMasivoRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        return parqueaderosRepository.registrarParqueaderosMasivo(request, ctx.getPropertyId());
    }

    @Override
    @Transactional
    public ParqueaderoDTO actualizarParqueadero(Long id, ParqueaderoRequestDTO request) {
        return parqueaderosRepository.actualizarParqueadero(id, request);
    }

    @Override
    @Transactional
    public void eliminarParqueadero(Long id) {
        parqueaderosRepository.eliminarParqueadero(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AsignacionParqueaderoDTO> getAsignaciones() {
        return parqueaderosRepository.getAsignaciones();
    }

    @Override
    @Transactional(readOnly = true)
    public AsignacionParqueaderoDTO getAsignacionById(Long id) {
        return parqueaderosRepository.getAsignacionById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Asignacion no encontrada"));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "ASIGNACION_PARQUEADERO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public AsignacionParqueaderoDTO crearAsignacion(AsignacionParqueaderoRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getPropertyId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Missing property context");
        }
        ParqueaderoDTO pq = getParqueaderoById(request.idParqueadero());
        if (!ctx.getPropertyId().equals(pq.idPropiedad())) {
            throw new org.springframework.security.access.AccessDeniedException("El parqueadero no pertenece a la propiedad");
        }
        // F07-03: conditional claim (DISPONIBLE -> ASIGNADO); a concurrent assignment of the same spot loses.
        if (!parqueaderosRepository.reservarParqueadero(request.idParqueadero(), ctx.getPropertyId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "El parqueadero no se encuentra disponible");
        }
        return parqueaderosRepository.crearAsignacion(request);
    }

    @Override
    @Transactional
    @Auditable(action = "FINALIZE", resource = "ASIGNACION_PARQUEADERO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public void finalizarAsignacion(Long id) {
        AsignacionParqueaderoDTO asignacion = getAsignacionById(id);
        parqueaderosRepository.finalizarAsignacion(id);
        parqueaderosRepository.actualizarEstadoParqueadero(asignacion.idParqueadero(), "DISPONIBLE");
    }
}
