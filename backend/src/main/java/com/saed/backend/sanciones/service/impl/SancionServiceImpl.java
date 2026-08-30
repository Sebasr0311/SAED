package com.saed.backend.sanciones.service.impl;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.sanciones.dto.SancionDTO;
import com.saed.backend.sanciones.repository.SancionRepository;
import com.saed.backend.sanciones.service.SancionService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SancionServiceImpl implements SancionService {

    private final SancionRepository sancionRepository;

    public SancionServiceImpl(SancionRepository sancionRepository) {
        this.sancionRepository = sancionRepository;
    }

    @Override
    public List<SancionDTO> getAllSanciones() {
        return sancionRepository.findAllSanciones();
    }

    @Override
    public List<SancionDTO> getMisSanciones() {
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        Long idPersona = sancionRepository.getIdPersonaFromUsuario(idUsuario);
        return sancionRepository.findSancionesByPersona(idPersona);
    }

    @Override
    public SancionDTO getSancionById(Long idSancion) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        return sancionRepository.findById(idSancion, idPropiedad)
                .orElseThrow(() -> new IllegalArgumentException("Sancion no encontrada o sin acceso"));
    }

    @Override
    public Long createSancion(SancionDTO request) {
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long creadoPor = SaedContextHolder.getContext().getUserId();
        
        if (idPropiedad == null) {
            throw new IllegalStateException("Contexto sin propiedad asignada");
        }

        // Validate that a sanction must start as NOTIFICADA and not automatically APPLIED
        // This is the core business rule from the Acceptance Criteria
        request.setEstado("NOTIFICADA");
        request.setNumeroExpediente("EXP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        
        // Give 5 days for descargos by default
        if (request.getFechaLimiteDescargos() == null) {
            request.setFechaLimiteDescargos(LocalDate.now().plusDays(5));
        }

        return sancionRepository.createSancion(request, idPropiedad, creadoPor);
    }

    @Override
    public void submitDescargos(Long idSancion, Map<String, String> payload) {
        SancionDTO sancion = getSancionById(idSancion);
        Long idUsuario = SaedContextHolder.getContext().getUserId();
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        Long idPersona = sancionRepository.getIdPersonaFromUsuario(idUsuario);
        
        // Tenant and Ownership verification
        if (!sancion.getIdPersonaImputada().equals(idPersona)) {
            throw new SecurityException("No tiene permiso para presentar descargos de esta sancion");
        }
        if (!"NOTIFICADA".equals(sancion.getEstado())) {
            throw new IllegalStateException("La sancion no esta en estado NOTIFICADA");
        }
        
        String descargos = payload.get("descargos");
        if (descargos == null || descargos.trim().isEmpty()) {
            throw new IllegalArgumentException("El texto de descargos no puede estar vacio");
        }

        // 1. Guardar los descargos reales
        sancionRepository.saveDescargos(idSancion, idPersona, descargos, idUsuario);
        // 2. Mover estado
        sancionRepository.updateEstado(idSancion, idPropiedad, "EN_DESCARGOS", null);
    }

    @Override
    public void emitirResolucion(Long idSancion, Map<String, String> payload) {
        SancionDTO sancion = getSancionById(idSancion);
        Long idPropiedad = SaedContextHolder.getContext().getPropertyId();
        
        // Due process enforcement: 
        if (!"EN_DESCARGOS".equals(sancion.getEstado()) && !"NOTIFICADA".equals(sancion.getEstado())) {
            throw new IllegalStateException("Proceso invalido para resolucion");
        }

        String decision = payload.get("decision"); // ABSUELTA or APLICADA
        String resolucion = payload.get("resolucionFinal");

        if (decision == null || resolucion == null) {
            throw new IllegalArgumentException("Se requiere decision y resolucion final");
        }
        
        if (!"ABSUELTA".equals(decision) && !"APLICADA".equals(decision)) {
            throw new IllegalArgumentException("Decision invalida. Debe ser ABSUELTA o APLICADA");
        }

        sancionRepository.updateEstado(idSancion, idPropiedad, decision, resolucion);
    }
}
