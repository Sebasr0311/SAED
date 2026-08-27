package com.saed.backend.person.controller;

import com.saed.backend.person.dto.*;
import com.saed.backend.person.service.DependentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DependentController {

    private final DependentService dependentService;

    public DependentController(DependentService dependentService) {
        this.dependentService = dependentService;
    }

    // --- Mascotas ---
    @PostMapping("/mascotas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public MascotaDTO createMascota(@RequestBody @Valid MascotaRequestDTO request) {
        return dependentService.createMascota(request);
    }

    @GetMapping("/mascotas/{id}")
    public MascotaDTO getMascotaById(@PathVariable Long id) {
        return dependentService.getMascotaById(id);
    }

    @GetMapping("/unidades/{unidadId}/mascotas")
    public List<MascotaDTO> getMascotasByUnidad(@PathVariable Long unidadId) {
        return dependentService.getMascotasByUnidad(unidadId);
    }

    @PutMapping("/mascotas/{id}")
    public MascotaDTO updateMascota(@PathVariable Long id, @RequestBody @Valid MascotaRequestDTO request) {
        return dependentService.updateMascota(id, request);
    }

    @DeleteMapping("/mascotas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMascota(@PathVariable Long id) {
        dependentService.deleteMascota(id);
    }

    // --- Vehiculos ---
    @PostMapping("/vehiculos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public VehiculoDTO createVehiculo(@RequestBody @Valid VehiculoRequestDTO request) {
        return dependentService.createVehiculo(request);
    }

    @GetMapping("/vehiculos/{id}")
    public VehiculoDTO getVehiculoById(@PathVariable Long id) {
        return dependentService.getVehiculoById(id);
    }

    @GetMapping("/unidades/{unidadId}/vehiculos")
    public List<VehiculoDTO> getVehiculosByUnidad(@PathVariable Long unidadId) {
        return dependentService.getVehiculosByUnidad(unidadId);
    }

    @PutMapping("/vehiculos/{id}")
    public VehiculoDTO updateVehiculo(@PathVariable Long id, @RequestBody @Valid VehiculoRequestDTO request) {
        return dependentService.updateVehiculo(id, request);
    }

    @DeleteMapping("/vehiculos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVehiculo(@PathVariable Long id) {
        dependentService.deleteVehiculo(id);
    }

    // --- Tutores ---
    @PostMapping("/tutores")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public TutorDTO createTutor(@RequestBody @Valid TutorRequestDTO request) {
        return dependentService.createTutor(request);
    }

    @GetMapping("/tutores/{id}")
    public TutorDTO getTutorById(@PathVariable Long id) {
        return dependentService.getTutorById(id);
    }

    @GetMapping("/personas/{menorId}/tutores")
    public List<TutorDTO> getTutoresByMenor(@PathVariable Long menorId) {
        return dependentService.getTutoresByMenor(menorId);
    }

    @PutMapping("/tutores/{id}")
    public TutorDTO updateTutor(@PathVariable Long id, @RequestBody @Valid TutorRequestDTO request) {
        return dependentService.updateTutor(id, request);
    }

    @DeleteMapping("/tutores/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTutor(@PathVariable Long id) {
        dependentService.deleteTutor(id);
    }

    // --- Visitantes ---
    @PostMapping("/visitantes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO') or hasAuthority('SCOPE_RESIDENTE')")
    public VisitanteDTO createVisitante(@RequestBody @Valid VisitanteRequestDTO request) {
        return dependentService.createVisitante(request);
    }

    @GetMapping("/visitantes/{id}")
    public VisitanteDTO getVisitanteById(@PathVariable Long id) {
        return dependentService.getVisitanteById(id);
    }

    @GetMapping("/personas/{personaId}/visitante")
    public VisitanteDTO getVisitanteByPersona(@PathVariable Long personaId) {
        return dependentService.getVisitanteByPersona(personaId);
    }

    @PutMapping("/visitantes/{id}")
    public VisitanteDTO updateVisitante(@PathVariable Long id, @RequestBody @Valid VisitanteRequestDTO request) {
        return dependentService.updateVisitante(id, request);
    }

    @DeleteMapping("/visitantes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVisitante(@PathVariable Long id) {
        dependentService.deleteVisitante(id);
    }
}
