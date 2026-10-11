package com.saed.backend.finanzas.billing;

import com.saed.backend.audit.AuditCategory;
import com.saed.backend.audit.AuditSeverity;
import com.saed.backend.audit.Auditable;
import com.saed.backend.context.SaedContext;
import com.saed.backend.context.SaedContextHolder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic billing of a property (F06-02, DEC-08): monthly fee generation from the approved budget
 * share by coefficient (or a configured fixed fee), early-payment discount and late interest as
 * per-property settings that default to disabled, and the overdue transition.
 *
 * <p>Both the manual endpoint and the future scheduler (F12) call these methods inside the acting
 * administrator's property context; there is no privileged path. Every operation is idempotent: a
 * replay of the same period or the same day changes nothing.
 */
@Service
public class BillingService {
    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BillingRepository repository;

    public BillingService(BillingRepository repository) {
        this.repository = repository;
    }

    public BillingSettings obtenerConfiguracion(Long idPropiedad) {
        requirePropertyAdmin(idPropiedad);
        return repository.findSettings(idPropiedad).orElseGet(() -> BillingSettings.disabled(idPropiedad));
    }

    @Transactional
    @Auditable(action = "UPDATE", resource = "CONFIGURACION_COBRO", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public BillingSettings guardarConfiguracion(Long idPropiedad, BillingSettings requested) {
        requirePropertyAdmin(idPropiedad);
        validate(requested);
        BillingSettings toSave = new BillingSettings(idPropiedad, requested.cuotaFija(),
                requested.porcDescuentoProntoPago(), requested.diasProntoPago(), requested.porcInteresMoraMensual(),
                requested.topeInteresMoraMensual(), requested.diasGracia(), requested.diaVencimiento());
        repository.saveSettings(toSave);
        return toSave;
    }

    @Transactional
    @Auditable(action = "EXECUTE", resource = "GENERACION_CUOTAS", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public GenerationResult generarCuotasMensuales(Long idPropiedad, YearMonth periodo, LocalDate today) {
        requirePropertyAdmin(idPropiedad);
        BillingSettings settings = repository.findSettings(idPropiedad).orElseGet(() -> BillingSettings.disabled(idPropiedad));

        BigDecimal annualBudget = BigDecimal.ZERO;
        if (settings.cuotaFija() == null) {
            annualBudget = repository.approvedAnnualExpenseBudget(idPropiedad, periodo.getYear());
            if (annualBudget == null || annualBudget.signum() <= 0) {
                throw new IllegalStateException("La propiedad no tiene presupuesto de egresos aprobado para "
                        + periodo.getYear() + " ni cuota fija configurada");
            }
        }

        List<UnitShare> units = repository.findActiveUnits(idPropiedad);
        Map<Long, BigDecimal> fees = FeeCalculator.unitFees(settings, annualBudget, units);
        int withoutCoefficient = (int) units.stream().filter(u -> !fees.containsKey(u.idUnidad())).count();
        if (withoutCoefficient > 0) {
            log.warn("[Billing] {} unit(s) of property {} have no coefficient and were skipped for {}",
                    withoutCoefficient, idPropiedad, periodo);
        }

        Long conceptId = repository.ensureAdministrationConcept(idPropiedad);
        LocalDate dueDate = FeeCalculator.dueDate(periodo, settings.diaVencimiento());
        LocalDate discountDeadline = settings.discountEnabled()
                ? FeeCalculator.discountDeadline(periodo, settings.diasProntoPago()) : null;
        boolean discountOpen = discountDeadline != null && !today.isAfter(discountDeadline);

        int created = 0;
        int existing = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (UnitShare unit : units) {
            BigDecimal base = fees.get(unit.idUnidad());
            if (base == null) {
                continue;
            }
            BigDecimal discount = discountOpen
                    ? FeeCalculator.discount(base, settings.porcDescuentoProntoPago()) : BigDecimal.ZERO.setScale(2);
            NewFee fee = new NewFee(unit.idUnidad(), conceptId, periodo.toString(), base, discount,
                    base.subtract(discount), dueDate, discountOpen ? discountDeadline : null);
            if (repository.insertFeeIfAbsent(fee)) {
                created++;
            } else {
                existing++;
            }
            total = total.add(base);
        }
        return new GenerationResult(periodo.toString(), created, existing, withoutCoefficient, total);
    }

    /**
     * Overdue pass for a property: PENDIENTE fees past their due date become VENCIDA, expired early-payment
     * discounts are forfeited once, and late interest accrues once per started month when enabled.
     */
    @Transactional
    @Auditable(action = "EXECUTE", resource = "VENCIMIENTO_CUOTAS", category = AuditCategory.FINANCIAL, severity = AuditSeverity.HIGH)
    public TransitionResult procesarVencimientos(Long idPropiedad, LocalDate today) {
        requirePropertyAdmin(idPropiedad);
        BillingSettings settings = repository.findSettings(idPropiedad).orElseGet(() -> BillingSettings.disabled(idPropiedad));

        int overdue = repository.markOverdue(idPropiedad, today);
        int forfeited = 0;
        int withInterest = 0;
        for (OpenFee fee : repository.findOutstandingFees(idPropiedad)) {
            BigDecimal balance = fee.saldoPendiente();
            BigDecimal discount = fee.valorDescuento() == null ? BigDecimal.ZERO : fee.valorDescuento();
            BigDecimal interest = fee.valorInteresMora() == null ? BigDecimal.ZERO : fee.valorInteresMora();
            boolean changed = false;

            if (discount.signum() > 0 && fee.fechaDescuentoProntoPago() != null
                    && fee.fechaDescuentoProntoPago().isBefore(today)) {
                balance = balance.add(discount);
                discount = BigDecimal.ZERO.setScale(2);
                forfeited++;
                changed = true;
            }

            if (settings.interestEnabled() && fee.fechaVencimiento() != null) {
                int months = FeeCalculator.monthsOverdue(fee.fechaVencimiento(), settings.diasGracia(), today);
                BigDecimal principal = balance.subtract(interest).max(BigDecimal.ZERO);
                BigDecimal target = FeeCalculator.accruedInterest(principal, settings.porcInteresMoraMensual(),
                        settings.topeInteresMoraMensual(), months);
                BigDecimal delta = target.subtract(interest);
                if (delta.signum() > 0) {
                    balance = balance.add(delta);
                    interest = target;
                    withInterest++;
                    changed = true;
                }
            }

            if (changed) {
                repository.applyAdjustment(fee.idCuota(), balance.setScale(2, RoundingMode.HALF_UP),
                        discount.setScale(2, RoundingMode.HALF_UP), interest.setScale(2, RoundingMode.HALF_UP));
            }
        }
        return new TransitionResult(overdue, forfeited, withInterest);
    }

    private static void validate(BillingSettings s) {
        if (s == null) {
            throw new IllegalArgumentException("La configuracion de cobro es obligatoria");
        }
        if (s.cuotaFija() != null && s.cuotaFija().signum() < 0) {
            throw new IllegalArgumentException("La cuota fija no puede ser negativa");
        }
        requirePercentage(s.porcDescuentoProntoPago(), "El descuento por pronto pago");
        requirePercentage(s.porcInteresMoraMensual(), "El interes de mora mensual");
        if (s.topeInteresMoraMensual() != null) {
            requirePercentage(s.topeInteresMoraMensual(), "El tope del interes de mora");
        }
        if (s.diasProntoPago() < 0 || s.diasProntoPago() > 31) {
            throw new IllegalArgumentException("Los dias de pronto pago deben estar entre 0 y 31");
        }
        if (s.diasGracia() < 0 || s.diasGracia() > 365) {
            throw new IllegalArgumentException("Los dias de gracia deben estar entre 0 y 365");
        }
        if (s.diaVencimiento() < 1 || s.diaVencimiento() > 28) {
            throw new IllegalArgumentException("El dia de vencimiento debe estar entre 1 y 28");
        }
    }

    private static void requirePercentage(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(label + " debe estar entre 0 y 100");
        }
    }

    /** Only the administrator of that very property (the scheduler acts as one); never SUPERADMIN (DEC-02). */
    private static void requirePropertyAdmin(Long idPropiedad) {
        SaedContext ctx = SaedContextHolder.getContext();
        if (ctx == null || idPropiedad == null || ctx.getPropertyId() == null || !idPropiedad.equals(ctx.getPropertyId())) {
            throw new AccessDeniedException("La facturacion solo puede ejecutarse sobre la propiedad activa de la sesion");
        }
        String role = ctx.getRoleCode();
        if (!"ADMIN_PROPIEDAD".equalsIgnoreCase(role) && !"ADMIN_ORGANIZACION".equalsIgnoreCase(role)) {
            throw new AccessDeniedException("Solo la administracion puede gestionar la facturacion");
        }
    }
}
