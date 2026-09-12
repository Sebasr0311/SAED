package com.saed.backend.seguros.service;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.dto.ResumenPolizasDTO;
import com.saed.backend.seguros.repository.PolizaSeguroRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class PolizaSeguroService {

    private final PolizaSeguroRepository repository;

    public PolizaSeguroService(PolizaSeguroRepository repository) {
        this.repository = repository;
    }

    public List<PolizaSeguroDTO> getAllPolizas() {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        return repository.findAllByPropiedad(propiedadId);
    }

    public ResumenPolizasDTO getResumen() {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        return repository.getResumenPolizas(propiedadId);
    }

    public PolizaSeguroDTO getPolizaById(Long id) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        return repository.findByIdAndPropiedad(id, propiedadId)
                .orElseThrow(() -> new IllegalArgumentException("Póliza no encontrada con ID: " + id));
    }

    public void createPoliza(PolizaSeguroDTO dto) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        dto.setIdPropiedad(propiedadId);
        normalizarEstado(dto);
        repository.insert(dto);
    }

    public void updatePoliza(Long id, PolizaSeguroDTO dto) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        dto.setIdPoliza(id);
        dto.setIdPropiedad(propiedadId);
        normalizarEstado(dto);
        repository.update(dto);
    }

    public void deletePoliza(Long id) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        repository.delete(id, propiedadId);
    }

    private void normalizarEstado(PolizaSeguroDTO dto) {
        if (dto.getDiasAlertaVencimiento() == null || dto.getDiasAlertaVencimiento() <= 0) {
            dto.setDiasAlertaVencimiento(45);
        }
        if (dto.getEstado() == null || dto.getEstado().isBlank() || !"CANCELADA".equalsIgnoreCase(dto.getEstado())) {
            LocalDate hoy = LocalDate.now();
            if (dto.getFechaFin() != null) {
                if (hoy.isAfter(dto.getFechaFin())) {
                    dto.setEstado("VENCIDA");
                } else if (!hoy.isBefore(dto.getFechaFin().minusDays(dto.getDiasAlertaVencimiento()))) {
                    dto.setEstado("POR_VENCER");
                } else {
                    dto.setEstado("VIGENTE");
                }
            } else {
                dto.setEstado("VIGENTE");
            }
        }
    }
}

