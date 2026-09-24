package com.saed.backend.finanzas.controller;

import com.saed.backend.common.service.FileStorageService;
import com.saed.backend.finanzas.dto.*;
import com.saed.backend.finanzas.service.FinanzasService;
import com.saed.backend.finanzas.service.WompiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Pagos", description = "API para la gestión del ciclo de vida de Pagos y Comprobantes")
@RestController
@RequestMapping("/api/v1")
public class PagosController {
    private static final Logger log = LoggerFactory.getLogger(PagosController.class);

    private final FinanzasService finanzasService;
    private final WompiService wompiService;

    public PagosController(FinanzasService finanzasService, WompiService wompiService) {
        this.finanzasService = finanzasService;
        this.wompiService = wompiService;
    }

    @GetMapping("/cuotas")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<List<CuotaDTO>> getCuotasPendientes(@RequestParam(required = false) Boolean pendientes) {
        return ResponseEntity.ok(finanzasService.getCuotasPendientes());
    }

    @GetMapping("/pagos")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_RESIDENTE')")
    @Operation(summary = "Listar pagos con filtros de auditoría y estado")
    public ResponseEntity<List<PagoResponseDTO>> getPagos(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long idUnidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String metodoPago) {
        return ResponseEntity.ok(finanzasService.getPagos(estado, idUnidad, fechaDesde, fechaHasta, metodoPago));
    }

    @GetMapping("/pagos/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_RESIDENTE')")
    @Operation(summary = "Consultar detalle de un pago por ID")
    public ResponseEntity<PagoResponseDTO> getPagoById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(finanzasService.getPagoDetalle(id));
    }

    @PostMapping("/pagos")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_RESIDENTE')")
    @Operation(summary = "Registrar un pago (JSON)")
    public ResponseEntity<Map<String, Object>> registrarPago(@Valid @RequestBody PagoRequestDTO request) {
        finanzasService.registrarPago(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "message", "Pago registrado exitosamente"
        ));
    }

    @PostMapping(value = "/pagos/manual", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_RESIDENTE')")
    @Operation(summary = "Registrar pago manual con comprobante físico adjunto")
    public ResponseEntity<Map<String, Object>> registrarPagoManual(
            @RequestPart("pago") @Valid PagoRequestDTO request,
            @RequestPart(value = "comprobante", required = false) MultipartFile comprobante) {
        Long idPago = finanzasService.registrarPagoManual(request, comprobante);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "idPago", idPago,
            "estado", "PENDIENTE_APROBACION",
            "message", "Pago manual registrado pendiente de aprobación"
        ));
    }

    @PostMapping("/pagos/comprobante")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_RESIDENTE')")
    @Operation(summary = "Subir archivo de comprobante de pago")
    public ResponseEntity<FileStorageService.StoredFile> subirComprobante(@RequestParam("file") MultipartFile file) {
        FileStorageService.StoredFile stored = finanzasService.subirComprobante(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(stored);
    }

    @GetMapping("/pagos/{id}/comprobante")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN', 'SCOPE_RESIDENTE')")
    @Operation(summary = "Descargar o visualizar el comprobante de un pago")
    public ResponseEntity<Resource> descargarComprobante(@PathVariable("id") Long id) {
        PagoResponseDTO pago = finanzasService.getPagoDetalle(id);
        Resource resource = finanzasService.descargarComprobante(id);

        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        if (pago.comprobanteUrl() != null) {
            String lower = pago.comprobanteUrl().toLowerCase();
            if (lower.endsWith(".pdf")) contentType = MediaType.APPLICATION_PDF_VALUE;
            else if (lower.endsWith(".png")) contentType = MediaType.IMAGE_PNG_VALUE;
            else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) contentType = MediaType.IMAGE_JPEG_VALUE;
            else if (lower.endsWith(".webp")) contentType = "image/webp";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"comprobante-" + id + "\"")
                .body(resource);
    }

    @PostMapping("/pagos/{id}/aprobar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
    @Operation(summary = "Aprobar un pago manual en estado PENDIENTE_APROBACION")
    public ResponseEntity<PagoResponseDTO> aprobarPago(@PathVariable("id") Long id) {
        PagoResponseDTO aprobado = finanzasService.aprobarPago(id);
        return ResponseEntity.ok(aprobado);
    }

    @PostMapping("/pagos/{id}/rechazar")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_SUPERADMIN')")
    @Operation(summary = "Rechazar un pago manual en estado PENDIENTE_APROBACION con motivo obligatorio")
    public ResponseEntity<PagoResponseDTO> rechazarPago(
            @PathVariable("id") Long id,
            @Valid @RequestBody PagoRechazoRequestDTO request) {
        PagoResponseDTO rechazado = finanzasService.rechazarPago(id, request.motivoRechazo());
        return ResponseEntity.ok(rechazado);
    }

    @PostMapping({"/pagos/wompi/webhook", "/pagos/notificacion"})
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> wompiWebhook(@RequestBody String payload) {
        try {
            wompiService.procesarWebhook(payload);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error procesando webhook Wompi", e);
            return ResponseEntity.ok().build(); // Wompi espera 200 siempre si no es error de red
        }
    }
}


