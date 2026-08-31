package com.saed.backend.seguros.service;

import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.seguros.dto.PolizaSeguroDTO;
import com.saed.backend.seguros.repository.PolizaSeguroRepository;
import org.springframework.stereotype.Service;

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

    public PolizaSeguroDTO getPolizaById(Long id) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        return repository.findByIdAndPropiedad(id, propiedadId)
                .orElseThrow(() -> new RuntimeException("Poliza no encontrada"));
    }

    public void createPoliza(PolizaSeguroDTO dto) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        dto.setIdPropiedad(propiedadId);
        repository.insert(dto);
    }

    public void updatePoliza(Long id, PolizaSeguroDTO dto) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        dto.setIdPoliza(id);
        dto.setIdPropiedad(propiedadId);
        repository.update(dto);
    }

    public void deletePoliza(Long id) {
        Long propiedadId = SaedContextHolder.getContext().getPropertyId();
        repository.delete(id, propiedadId);
    }
}
