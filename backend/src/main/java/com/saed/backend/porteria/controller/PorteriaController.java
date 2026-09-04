package com.saed.backend.porteria.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.porteria.dto.*;
import com.saed.backend.porteria.service.PorteriaService;
import com.saed.backend.common.service.EmailService;
import com.saed.backend.context.SaedContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.time.ZonedDateTime;
import java.util.logging.Logger;

@Tag(name = "Porteria", description = "API para la gestion de Porteria")
@RestController
@RequestMapping("/api/v1/porteria")
public class PorteriaController {

    private static final Logger log = Logger.getLogger(PorteriaController.class.getName());
    private final PorteriaService porteriaService;
    private final EmailService emailService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PorteriaController(PorteriaService porteriaService, EmailService emailService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.porteriaService = porteriaService;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- ADMIN CRUD PORTERIAS ---
    @Operation(summary = "Listar porterias de la propiedad")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<ApiResponse<List<PorteriaDTO>>> listar() {
        return ResponseEntity.ok(ApiResponse.success(porteriaService.listarPorterias()));
    }

    @Operation(summary = "Obtener porteria por ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public ResponseEntity<ApiResponse<PorteriaDTO>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(porteriaService.getPorteriaById(id)));
    }

    @Operation(summary = "Crear porteria")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<PorteriaDTO>> crear(@RequestBody @Valid PorteriaCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(porteriaService.crearPorteria(request)));
    }

    @Operation(summary = "Actualizar porteria")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    public ResponseEntity<ApiResponse<PorteriaDTO>> actualizar(@PathVariable Long id, @RequestBody @Valid PorteriaCreateDTO request) {
        return ResponseEntity.ok(ApiResponse.success(porteriaService.actualizarPorteria(id, request)));
    }

    @Operation(summary = "Eliminar porteria")
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
    public Map<String, Object> programarVisita(@RequestBody @Valid VisitaRequestDTO request) {
        VisitaDTO visita = porteriaService.programarVisita(request);
        
        // Fase E: Generar QR automatico y notificar al VISITANTE
        String token = UUID.randomUUID().toString();
        QrAccesoRequestDTO qrReq = new QrAccesoRequestDTO(
            visita.idVisita(),
            token,
            ZonedDateTime.now().plusHours(24),
            1,
            "ACTIVO",
            SaedContextHolder.getContext().getUserId()
        );
        
        // Lo guardamos en DB
        QrAccesoDTO qr = porteriaService.generarQrAcceso(qrReq);
        
        // Buscamos el email del VISITANTE y enviamos
        try {
            List<Map<String, Object>> visitantes = jdbcTemplate.queryForList(
                "SELECT EMAIL FROM PERSONAS WHERE ID_PERSONA = :p AND EMAIL IS NOT NULL", 
                Map.of("p", request.visitanteId())
            );
            if (!visitantes.isEmpty()) {
                String email = (String) visitantes.get(0).get("EMAIL");
                if (email != null && !email.isBlank()) {
                    emailService.enviarCorreoQR(email, token, qr.fechaExpiracion().toString(), "Visitante SAED");
                    log.info("QR enviado exitosamente al visitante: " + email);
                }
            }
        } catch (Exception e) {
            log.warning("Fallo al enviar correo QR a visitante: " + e.getMessage());
        }

        // Devolver respuesta unificada para que VisitasPage.jsx de React no falle
        Map<String, Object> response = new HashMap<>();
        response.put("idVisita", visita.idVisita());
        response.put("codigoQr", token);
        return response;
    }

    @GetMapping("/visitas/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PORTERO')")
    public VisitaDTO getVisitaById(@PathVariable Long id) {
        return porteriaService.getVisitaById(id);
    }

    @GetMapping("/unidades/{unidadId}/visitas")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE')")
    public List<VisitaDTO> getVisitasByUnidad(@PathVariable Long unidadId) {
        return porteriaService.getVisitasByUnidad(unidadId);
    }

    @PutMapping("/visitas/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE')")
    public VisitaDTO actualizarVisita(@PathVariable Long id, @RequestBody @Valid VisitaRequestDTO request) {
        return porteriaService.actualizarVisita(id, request);
    }

    @PutMapping("/visitas/{id}/salida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public void registrarSalidaVisita(@PathVariable Long id) {
        porteriaService.registrarSalidaVisita(id);
    }

    @GetMapping("/visitas-resumen")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public List<VisitaListDTO> getVisitasResumen() {
        return porteriaService.getVisitasResumen();
    }

    @GetMapping("/visitas/historial")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public List<VisitaHistorialDTO> getVisitasHistorial(
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin) {
        return porteriaService.getVisitasHistorial(fechaInicio, fechaFin);
    }

    @GetMapping("/visitas/{id}/detalle")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PORTERO')")
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
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
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
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_RESIDENTE', 'SCOPE_PORTERO')")
    public QrAccesoDTO getQrAccesoById(@PathVariable Long id) {
        return porteriaService.getQrAccesoById(id);
    }

    @PostMapping("/qr/validar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public Map<String, Object> validarQr(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            token = body.get("codigoQr");
        }
        if (token == null || token.isBlank()) {
            return Map.of("valido", false, "mensaje", "Token o código QR no proporcionado");
        }
        return porteriaService.validarQrDetalle(token);
    }

    @PostMapping("/qr/notificar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public Map<String, Object> notificarVisita(@RequestBody Map<String, String> body) {
        String token = body.get("codigoQr");
        if (token == null || token.isBlank()) {
            token = body.get("token");
        }
        String fotoCaptura = body.get("fotoCaptura");
        return porteriaService.notificarVisitaQr(token, fotoCaptura);
    }

    @PostMapping("/qr/entrada")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_PORTERO')")
    public Map<String, Object> registrarEntradaQr(@RequestBody Map<String, String> body) {
        String token = body.get("codigoQr");
        if (token == null || token.isBlank()) {
            token = body.get("token");
        }
        String medioTransporte = body.get("medioTransporte");
        String placa = body.get("placa");
        String descripcion = body.get("descripcion");
        return porteriaService.registrarEntradaQr(token, medioTransporte, placa, descripcion);
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
