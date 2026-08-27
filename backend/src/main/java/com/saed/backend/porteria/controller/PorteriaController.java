package com.saed.backend.porteria.controller;

import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.service.PorteriaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/porteria")
public class PorteriaController {

    private final PorteriaService porteriaService;

    public PorteriaController(PorteriaService porteriaService) {
        this.porteriaService = porteriaService;
    }

    // --- VISITAS ---
    @PostMapping("/visitas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_PORTERO')")
    public VisitaDTO programarVisita(@RequestBody @Valid VisitaRequestDTO request) {
        return porteriaService.programarVisita(request);
    }

    @GetMapping("/visitas/{id}")
    public VisitaDTO getVisitaById(@PathVariable Long id) {
        return porteriaService.getVisitaById(id);
    }

    @GetMapping("/unidades/{unidadId}/visitas")
    public List<VisitaDTO> getVisitasByUnidad(@PathVariable Long unidadId) {
        return porteriaService.getVisitasByUnidad(unidadId);
    }

    @PutMapping("/visitas/{id}")
    public VisitaDTO actualizarVisita(@PathVariable Long id, @RequestBody @Valid VisitaRequestDTO request) {
        return porteriaService.actualizarVisita(id, request);
    }

    
    @GetMapping("/visitas-resumen")
    public List<VisitaListDTO> getVisitasResumen() {
        return porteriaService.getVisitasResumen();
    }

    // --- REGISTROS DE ACCESO ---
    @PostMapping("/registros/entrada")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO')")
    public RegistroAccesoDTO registrarEntrada(@RequestBody @Valid RegistroAccesoRequestDTO request) {
        return porteriaService.registrarEntrada(request);
    }

    @PostMapping("/registros/salida")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO')")
    public RegistroAccesoDTO registrarSalida(@RequestBody @Valid RegistroAccesoRequestDTO request) {
        return porteriaService.registrarSalida(request);
    }

    @GetMapping("/propiedades/{propiedadId}/registros")
    public List<RegistroAccesoDTO> getRegistrosByPropiedad(@PathVariable Long propiedadId) {
        return porteriaService.getRegistrosByPropiedad(propiedadId);
    }

    // --- QR ACCESOS ---
    @PostMapping("/qr")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE')")
    public QrAccesoDTO generarQrAcceso(@RequestBody @Valid QrAccesoRequestDTO request) {
        return porteriaService.generarQrAcceso(request);
    }

    @GetMapping("/qr/{id}")
    public QrAccesoDTO getQrAccesoById(@PathVariable Long id) {
        return porteriaService.getQrAccesoById(id);
    }

    @PostMapping("/qr/validar")
    public Map<String, Boolean> validarQr(@RequestBody Map<String, String> body) {
        boolean valid = porteriaService.validarQr(body.get("token"));
        return Map.of("valido", valid);
    }

    // --- VEHICULOS VISITA ---
    @PostMapping("/vehiculos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO')")
    public VehiculoVisitaDTO registrarIngresoVehiculo(@RequestBody @Valid VehiculoVisitaRequestDTO request) {
        return porteriaService.registrarIngresoVehiculo(request);
    }

    @PostMapping("/vehiculos/{id}/salida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_PORTERO')")
    public void registrarSalidaVehiculo(@PathVariable Long id, @RequestBody Map<String, BigDecimal> body) {
        porteriaService.registrarSalidaVehiculo(id, body.getOrDefault("costoTotal", BigDecimal.ZERO));
    }
}


