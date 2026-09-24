package com.saed.backend.finanzas.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.finanzas.dto.PazYSalvoDetalleDTO;
import com.saed.backend.finanzas.dto.PazYSalvoEstadoFinancieroDTO;
import com.saed.backend.finanzas.service.PazYSalvoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * PazYSalvoController - Emisión, verificación y descarga documental oficial de Paz y Salvo.
 *
 * Contrato:
 *   GET    /api/v1/paz-y-salvos                           - listar certificados con aislamiento tenant
 *   POST   /api/v1/paz-y-salvos                           - emitir paz y salvo con validación financiera integral y PDF
 *   GET    /api/v1/paz-y-salvos/{id}                      - detalle del paz y salvo
 *   GET    /api/v1/paz-y-salvos/{id}/descargar            - descarga segura del PDF oficial con hash SHA-256
 *   GET    /api/v1/paz-y-salvos/{id}/pdf                  - alias de descarga segura del PDF
 *   GET    /api/v1/paz-y-salvos/unidad/{id}/estado-financiero - consulta en tiempo real de deuda y viabilidad
 *   GET    /api/v1/paz-y-salvos/verificar/{codigo}        - verificación de autenticidad por código
 */
@Tag(name = "Paz y Salvos", description = "Emisión, verificación y descarga de certificados de paz y salvo")
@RestController
@RequestMapping("/api/v1/paz-y-salvos")
@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_RESIDENTE_CONVIVENCIA')")
public class PazYSalvoController {

    private final PazYSalvoService pazYSalvoService;

    public PazYSalvoController(PazYSalvoService pazYSalvoService) {
        this.pazYSalvoService = pazYSalvoService;
    }

    // --- LISTAR ---

    @Operation(summary = "Listar certificados de paz y salvo visibles según el rol del usuario")
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listar() {
        return ApiResponse.success(pazYSalvoService.listar());
    }

    // --- ESTADO FINANCIERO DE UNIDAD ---

    @Operation(summary = "Consultar estado financiero de la unidad del usuario autenticado (residente o conviviente)")
    @GetMapping("/mi-estado-financiero")
    @PreAuthorize("hasAuthority('SCOPE_RESIDENTE') or hasAuthority('SCOPE_RESIDENTE_CONVIVENCIA') or hasAuthority('SCOPE_ADMIN_PROPIEDAD') or hasAuthority('SCOPE_SUPERADMIN')")
    public ResponseEntity<ApiResponse<PazYSalvoEstadoFinancieroDTO>> miEstadoFinanciero() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getUnitId() == null) {
            throw new AccessDeniedException("El contexto de usuario no tiene una unidad asignada.");
        }
        PazYSalvoEstadoFinancieroDTO estado = pazYSalvoService.verificarEstadoFinanciero(ctx.getUnitId());
        return ResponseEntity.ok(ApiResponse.success(estado));
    }

    @Operation(summary = "Consultar estado financiero integral de una unidad (cartera + multas sin doble conteo)")
    @GetMapping("/unidad/{idUnidad}/estado-financiero")
    public ResponseEntity<ApiResponse<PazYSalvoEstadoFinancieroDTO>> estadoFinanciero(@PathVariable Long idUnidad) {
        PazYSalvoEstadoFinancieroDTO estado = pazYSalvoService.verificarEstadoFinanciero(idUnidad);
        return ResponseEntity.ok(ApiResponse.success(estado));
    }

    // --- GENERAR ---

    @Operation(summary = "Generar certificado oficial de paz y salvo (solo administración)")
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_SUPERADMIN') or hasAuthority('SCOPE_ADMIN_ORGANIZACION') or hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "CREATE", resource = "PAZ_Y_SALVO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public ResponseEntity<ApiResponse<Map<String, Object>>> generar(@RequestBody Map<String, Object> body) {
        Object rawIdUnidad = body != null ? body.get("idUnidad") : null;
        if (rawIdUnidad == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("idUnidad es obligatorio"));
        }

        Long idUnidad;
        if (rawIdUnidad instanceof Number num) {
            idUnidad = num.longValue();
        } else {
            try {
                idUnidad = Long.parseLong(rawIdUnidad.toString().trim());
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(ApiResponse.error("idUnidad no es válido"));
            }
        }

        String motivo = body.get("motivo") != null ? body.get("motivo").toString() : "";

        try {
            Map<String, Object> resultado = pazYSalvoService.generarPazYSalvo(idUnidad, motivo);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(resultado));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // --- DETALLE ---

    @Operation(summary = "Detalle de un paz y salvo con validación anti-IDOR")
    @GetMapping("/{id}")
    public ApiResponse<Object> detalle(@PathVariable Long id) {
        try {
            PazYSalvoDetalleDTO detalle = pazYSalvoService.obtenerDetalle(id);
            return ApiResponse.success(detalle);
        } catch (NoSuchElementException e) {
            return ApiResponse.error("Paz y salvos no encontrado");
        }
    }

    // --- DESCARGAR PDF ---

    @Operation(summary = "Descargar el documento PDF oficial del paz y salvo con validación de integridad SHA-256")
    @GetMapping("/{id}/descargar")
    public ResponseEntity<Resource> descargar(@PathVariable Long id) {
        return buildPdfDownloadResponse(id);
    }

    @Operation(summary = "Alias de descarga del PDF oficial del paz y salvo")
    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> descargarPdfAlias(@PathVariable Long id) {
        return buildPdfDownloadResponse(id);
    }

    private ResponseEntity<Resource> buildPdfDownloadResponse(Long id) {
        PazYSalvoDetalleDTO detalle = pazYSalvoService.obtenerDetalle(id);
        Resource resource = pazYSalvoService.descargarPdf(id);

        String codigo = detalle.codigoVerificacion() != null ? detalle.codigoVerificacion().substring(0, 8) : String.valueOf(id);
        String filename = "paz_y_salvo_" + detalle.numeroUnidad() + "_" + codigo + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        if (detalle.documentoHash() != null) {
            headers.add("X-Content-Sha256", detalle.documentoHash());
        }

        return ResponseEntity.ok().headers(headers).body(resource);
    }

    // --- VERIFICAR POR CODIGO ---

    @Operation(summary = "Verificar autenticidad pública o autenticada de un paz y salvo por su código")
    @GetMapping("/verificar/{codigo}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verificar(@PathVariable String codigo) {
        Map<String, Object> pazSalvo = pazYSalvoService.verificarPorCodigo(codigo);

        if (pazSalvo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Codigo de verificacion no valido"));
        }

        Boolean esValido = (Boolean) pazSalvo.get("esValido");
        if (Boolean.FALSE.equals(esValido)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Paz y salvos vencido"));
        }

        return ResponseEntity.ok(ApiResponse.success(pazSalvo));
    }
}