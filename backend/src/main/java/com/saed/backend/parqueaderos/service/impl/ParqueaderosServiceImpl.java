package com.saed.backend.parqueaderos.service.impl;

import com.saed.backend.audit.Auditable;
import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;

import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoDTO;
import com.saed.backend.parqueaderos.dto.AsignacionParqueaderoRequestDTO;
import com.saed.backend.parqueaderos.dto.ParqueaderoDTO;
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
    public ParqueaderoDTO getParqueaderoById(Long id) {
        return parqueaderosRepository.getParqueaderoById(id)
                .orElseThrow(() -> new RuntimeException("Parqueadero no encontrado"));
    }

    @Override
    @Transactional
    public ParqueaderoDTO registrarParqueadero(ParqueaderoRequestDTO request) {
        SaedContext ctx = SaedContextHolder.getContext();
        return parqueaderosRepository.registrarParqueadero(request, ctx.getPropertyId());
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
                .orElseThrow(() -> new RuntimeException("Asignacion no encontrada"));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", resource = "ASIGNACION_PARQUEADERO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public AsignacionParqueaderoDTO crearAsignacion(AsignacionParqueaderoRequestDTO request) {
        ParqueaderoDTO pq = getParqueaderoById(request.idParqueadero());
        if (!"DISPONIBLE".equals(pq.estado())) {
            throw new RuntimeException("El parqueadero no se encuentra disponible");
        }
        AsignacionParqueaderoDTO asignacion = parqueaderosRepository.crearAsignacion(request);
        parqueaderosRepository.actualizarEstadoParqueadero(request.idParqueadero(), "ASIGNADO");
        return asignacion;
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
