package com.saed.backend.consumos.controller;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.consumos.dto.ConsumoTendenciaDTO;
import com.saed.backend.consumos.dto.ConsumosSummaryDTO;
import com.saed.backend.consumos.dto.MedicionConsumoDTO;
import com.saed.backend.consumos.dto.MedicionConsumoRequestDTO;
import com.saed.backend.consumos.service.MedicionConsumoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Tag(name = "Consumos y Sostenibilidad", description = "Control de mediciones de servicios públicos, detección de anomalías y tendencias")
@RestController
@RequestMapping("/api/v1/consumos")
public class ConsumosController {

    private final MedicionConsumoService service;

    public ConsumosController(MedicionConsumoService service) {
        this.service = service;
    }

    @Operation(summary = "Listar mediciones de consumo con filtros opcionales")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_RESIDENTE')")
    public ResponseEntity<List<MedicionConsumoDTO>> getAll(
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String tipoServicio,
            @RequestParam(required = false) Long idUnidad,
            @RequestParam(defaultValue = "false") boolean areasComunes) {
        return ResponseEntity.ok(service.getAll(periodo, tipoServicio, idUnidad, areasComunes));
    }

    @Operation(summary = "Resumen de consumo consolidado y costos por período")
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_RESIDENTE')")
    public ResponseEntity<ConsumosSummaryDTO> getSummary(@RequestParam(required = false) String periodo) {
        return ResponseEntity.ok(service.getSummary(periodo));
    }

    @Operation(summary = "Tendencia histórica de consumo y costos por servicio")
    @GetMapping("/tendencias")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_RESIDENTE')")
    public ResponseEntity<List<ConsumoTendenciaDTO>> getTendencias(
            @RequestParam(required = false) String tipoServicio,
            @RequestParam(required = false) Integer ultimosMeses,
            @RequestParam(required = false) Integer meses) {
        int window = ultimosMeses != null ? ultimosMeses : (meses != null ? meses : 12);
        return ResponseEntity.ok(service.getTendencias(tipoServicio, window));
    }

    @Operation(summary = "Consultar última lectura registrada para un medidor",
            description = "Sin idUnidad se consulta un medidor de zona común. Con periodo (YYYY-MM) solo se consideran "
                    + "periodos anteriores. Responde encontrada=false cuando no hay lecturas previas.")
    @GetMapping("/ultima-lectura")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION')")
    public ResponseEntity<Map<String, Object>> getUltimaLectura(
            @RequestParam(required = false) Long idUnidad,
            @RequestParam String numeroMedidor,
            @RequestParam(required = false) String tipoServicio,
            @RequestParam(required = false) String periodo) {
        if (periodo != null && !periodo.isBlank() && !periodo.matches("^[0-9]{4}-(0[1-9]|1[0-2])$")) {
            throw new IllegalArgumentException("El período debe tener formato YYYY-MM.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        Optional<MedicionConsumoDTO> last = service.getUltimaLectura(idUnidad, numeroMedidor, tipoServicio, periodo);
        body.put("encontrada", last.isPresent());
        last.ifPresent(m -> {
            body.put("lecturaActual", m.getLecturaActual());
            body.put("periodo", m.getPeriodo());
            body.put("numeroMedidor", m.getNumeroMedidor());
            body.put("tipoServicio", m.getTipoServicio());
            body.put("idUnidad", m.getIdUnidad());
            body.put("unidadMedida", m.getUnidadMedida());
            body.put("tarifaUnitaria", m.getTarifaUnitaria());
            body.put("fechaTomaLectura", m.getFechaTomaLectura());
        });
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Detalle de una medición de consumo")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_ADMIN_PROPIEDAD', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_RESIDENTE')")
    public ResponseEntity<MedicionConsumoDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @Operation(summary = "Registrar nueva medición de consumo")
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "CREATE", resource = "MEDICION_CONSUMO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody MedicionConsumoRequestDTO dto) {
        Long id = service.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("idMedicion", id, "message", "Medición registrada exitosamente"));
    }

    @Operation(summary = "Actualizar medición de consumo")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "UPDATE", resource = "MEDICION_CONSUMO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.INFO)
    public ResponseEntity<Map<String, String>> update(@PathVariable Long id, @Valid @RequestBody MedicionConsumoRequestDTO dto) {
        service.update(id, dto);
        return ResponseEntity.ok(Map.of("message", "Medición actualizada exitosamente"));
    }

    @Operation(summary = "Eliminar medición de consumo")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
    @Auditable(action = "DELETE", resource = "MEDICION_CONSUMO", category = AuditCategory.OPERATIONAL, severity = AuditSeverity.WARN)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
