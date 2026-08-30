package com.saed.backend.incidentes.service.impl;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.incidentes.dto.IncidenteDTO;
import com.saed.backend.incidentes.repository.IncidenteRepository;
import com.saed.backend.incidentes.service.IncidenteService;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class IncidenteServiceImpl implements IncidenteService {

    private final IncidenteRepository incidenteRepository;

    public IncidenteServiceImpl(IncidenteRepository incidenteRepository) {
        this.incidenteRepository = incidenteRepository;
    }

    @Override
    public List<IncidenteDTO> getAllIncidentes() {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        return incidenteRepository.findAllByPropiedad(idPropiedad);
    }

    @Override
    public List<IncidenteDTO> getMisIncidentes() {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long idUnidad = SaedContextHolder.getContext().getUnitId();
        if (idUnidad == null) {
            throw new SecurityException("Usuario no tiene unidad asignada");
        }
        return incidenteRepository.findAllByUnidad(idUnidad, idPropiedad);
    }

    @Override
    public IncidenteDTO getIncidenteById(Long idIncidente) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        IncidenteDTO incidente = incidenteRepository.findById(idIncidente, idPropiedad)
                .orElseThrow(() -> new IllegalArgumentException("Incidente no encontrado o acceso denegado"));
                
        String roleCode = SaedContextHolder.getContext().getRoleCode();
        boolean isStaff = "SUPERADMIN".equals(roleCode) || "ADMIN_PROPIEDAD".equals(roleCode) || "PORTERO".equals(roleCode);
        
        if (!isStaff) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null || !userUnitId.equals(incidente.getIdUnidad())) {
                throw new SecurityException("Acceso denegado: el incidente no pertenece a su unidad");
            }
        }
        
        return incidente;
    }

    @Override
    public Long reportarIncidente(IncidenteDTO request) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long registradoPor = SaedContextHolder.getContext().getUserId();
        String roleCode = SaedContextHolder.getContext().getRoleCode();
        
        boolean isStaff = "SUPERADMIN".equals(roleCode) || "ADMIN_PROPIEDAD".equals(roleCode) || "PORTERO".equals(roleCode);
        
        if (!isStaff) {
            Long userUnitId = SaedContextHolder.getContext().getUnitId();
            if (userUnitId == null) {
                throw new SecurityException("Usuario no tiene unidad asignada");
            }
            // Strict IDOR prevention: force the unit ID from context for residents
            request.setIdUnidad(userUnitId);
            // Residents cannot report common area or security gate issues, only unit issues
            request.setIdZonaComun(null);
            request.setIdPorteria(null);
        }

        if (request.getTitulo() == null || request.getTitulo().isBlank()) {
            throw new IllegalArgumentException("El titulo es obligatorio");
        }
        
        if (request.getTipoIncidente() == null || request.getTipoIncidente().isBlank()) {
            throw new IllegalArgumentException("El tipo de incidente es obligatorio");
        }

        if (request.getDescripcionHechos() == null || request.getDescripcionHechos().isBlank()) {
            throw new IllegalArgumentException("La descripcion de los hechos es obligatoria");
        }

        if (request.getFechaHoraIncidente() == null) {
            request.setFechaHoraIncidente(ZonedDateTime.now());
        }

        return incidenteRepository.createIncidente(request, idPropiedad, registradoPor);
    }

    @Override
    public void cerrarIncidente(Long idIncidente, String conclusiones) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        
        IncidenteDTO incidente = getIncidenteById(idIncidente);
        if ("CERRADO".equals(incidente.getEstado())) {
            throw new IllegalStateException("El incidente ya esta cerrado");
        }
        
        incidenteRepository.updateEstado(idIncidente, idPropiedad, "CERRADO", conclusiones);
    }
}
