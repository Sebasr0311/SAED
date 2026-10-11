package com.saed.backend.finanzas.controller;

import com.saed.backend.common.dto.ApiResponse;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import com.saed.backend.finanzas.billing.BillingService;
import com.saed.backend.finanzas.billing.BillingSettings;
import com.saed.backend.finanzas.billing.GenerationResult;
import com.saed.backend.finanzas.billing.TransitionResult;
import com.saed.backend.platform.annotation.RequireModule;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Periodic billing of the session property (F06-02, DEC-08). The property always comes from the
 * session context; the same {@link BillingService} methods are what the scheduler will call.
 */
@Tag(name = "Facturacion", description = "Generacion mensual de cuotas, descuento por pronto pago e interes de mora")
@RequireModule("FINANZAS")
@RestController
@RequestMapping("/api/v1/finanzas/facturacion")
@PreAuthorize("hasAuthority('SCOPE_ADMIN_PROPIEDAD')")
public class BillingController {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /** Settings payload; absent percentages mean "disabled" (0). */
    public record SettingsRequest(BigDecimal cuotaFija, BigDecimal porcDescuentoProntoPago, Integer diasProntoPago,
                                  BigDecimal porcInteresMoraMensual, BigDecimal topeInteresMoraMensual,
                                  Integer diasGracia, Integer diaVencimiento) {
    }

    public record GenerateRequest(String periodo) {
    }

    @GetMapping("/configuracion")
    public ApiResponse<BillingSettings> configuracion() {
        return ApiResponse.success(billingService.obtenerConfiguracion(sessionProperty()));
    }

    @PutMapping("/configuracion")
    public ApiResponse<BillingSettings> guardarConfiguracion(@RequestBody SettingsRequest request) {
        Long propId = sessionProperty();
        BillingSettings settings = new BillingSettings(propId, request.cuotaFija(),
                orZero(request.porcDescuentoProntoPago()), orZero(request.diasProntoPago()),
                orZero(request.porcInteresMoraMensual()), request.topeInteresMoraMensual(),
                orZero(request.diasGracia()),
                request.diaVencimiento() == null ? BillingSettings.DEFAULT_DUE_DAY : request.diaVencimiento());
        return ApiResponse.success(billingService.guardarConfiguracion(propId, settings));
    }

    @PostMapping("/generar")
    public ResponseEntity<ApiResponse<GenerationResult>> generar(@RequestBody GenerateRequest request) {
        YearMonth period = parsePeriod(request == null ? null : request.periodo());
        return ResponseEntity.ok(ApiResponse.success(
                billingService.generarCuotasMensuales(sessionProperty(), period, today())));
    }

    @PostMapping("/vencimientos")
    public ApiResponse<TransitionResult> vencimientos() {
        return ApiResponse.success(billingService.procesarVencimientos(sessionProperty(), today()));
    }

    private static Long sessionProperty() {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || ctx.getPropertyId() == null) {
            throw new AccessDeniedException("Se requiere una propiedad activa en la sesion");
        }
        return ctx.getPropertyId();
    }

    private static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private static YearMonth parsePeriod(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("El periodo (AAAA-MM) es obligatorio");
        }
        try {
            return YearMonth.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El periodo debe tener el formato AAAA-MM");
        }
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
