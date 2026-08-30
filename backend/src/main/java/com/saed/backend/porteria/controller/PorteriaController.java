package com.saed.backend.porteria.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.service.PorteriaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "Porteria", description = "API para la gestion de Porteria")
@RestController
@RequestMapping("/api/v1/porteria")
public class PorteriaController {

    private final PorteriaService porteriaService;

    public PorteriaController(PorteriaService porteriaService) {
        this.porteriaService = porteriaService;
    }

    // --- ADMIN CRUD PORTERÍAS ---
    @Operation(summary = "Listar porterías de la propiedad")
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<List<PorteriaDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.success(porteriaService.listarPorterias()));
    }

    @Operation(summary = "Obtener portería por ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<PorteriaDTO>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(porteriaService.getPorteriaById(id)));
    }

    @Operation(summary = "Crear portería")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<PorteriaDTO>> crear(@RequestBody @Valid PorteriaCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(porteriaService.crearPorteria(request)));
    }

    @Operation(summary = "Actualizar portería")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<PorteriaDTO>> actualizar(@PathVariable Long id, @RequestBody @Valid PorteriaCreateDTO request) {
        return ResponseEntity.ok(ApiResponse.success(porteriaService.actualizarPorteria(id, request)));
    }

    @Operation(summary = "Eliminar portería")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        porteriaService.eliminarPorteria(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // --- VISITAS ---
    @PostMapping("/visitas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_PORTERO')")
    public VisitaDTO programarVisita(@RequestBody @Valid VisitaRequestDTO request) {
        return porteriaService.programarVisita(request);
    }

    @GetMapping("/visitas/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PORTERO')")
    public VisitaDTO getVisitaById(@PathVariable Long id) {
        return porteriaService.getVisitaById(id);
    }

    @GetMapping("/unidades/{unidadId}/visitas")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE')")
    public List<VisitaDTO> getVisitasByUnidad(@PathVariable Long unidadId) {
        return porteriaService.getVisitasByUnidad(unidadId);
    }

    @PutMapping("/visitas/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE')")
    public VisitaDTO actualizarVisita(@PathVariable Long id, @RequestBody @Valid VisitaRequestDTO request) {
        return porteriaService.actualizarVisita(id, request);
    }

    @PutMapping("/visitas/{id}/salida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public void registrarSalidaVisita(@PathVariable Long id) {
        porteriaService.registrarSalidaVisita(id);
    }

    @GetMapping("/visitas-resumen")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public List<VisitaListDTO> getVisitasResumen() {
        return porteriaService.getVisitasResumen();
    }

    @GetMapping("/visitas/historial")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD')")
    public List<VisitaHistorialDTO> getVisitasHistorial(
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin) {
        return porteriaService.getVisitasHistorial(fechaInicio, fechaFin);
    }

    @GetMapping("/visitas/{id}/detalle")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PORTERO')")
    public VisitaDetalleDTO getVisitaDetalle(@PathVariable Long id) {
        return porteriaService.getVisitaDetalle(id);
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
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
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
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PORTERO')")
    public QrAccesoDTO getQrAccesoById(@PathVariable Long id) {
        return porteriaService.getQrAccesoById(id);
    }

    @PostMapping("/qr/validar")
    @PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
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




