package com.saed.backend.finanzas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * PlanPricingPolicy — Fuente única de verdad para el cálculo comercial de planes SaaS en SAED 2.0.
 *
 * Política Comercial Canónica:
 * - Ciclo MENSUAL: Tarifa plena mensual obtenida de Oracle (PRECIO_MENSUAL).
 * - Ciclo ANUAL: 12 meses con 20% de descuento comercial (factor: 12 * 0.80 = 9.60 meses).
 * - Descuento Anual: 20%.
 * - Moneda: Pesos Colombianos (COP).
 */
public final class PlanPricingPolicy {

    public static final int DESCUENTO_ANUAL_PORCENTAJE = 20;
    public static final BigDecimal FACTOR_DESCUENTO_ANUAL = new BigDecimal("0.80");
    public static final BigDecimal MESES_ANUAL = new BigDecimal("12");

    private PlanPricingPolicy() {
        // Utility class
    }

    /**
     * Calcula el precio anual aplicando el 20% de descuento sobre 12 mensualidades.
     */
    public static long calcularPrecioAnual(long precioMensual) {
        if (precioMensual <= 0) {
            return 0L;
        }
        return Math.round(precioMensual * 12.0 * 0.80);
    }

    /**
     * Calcula el monto en pesos (COP) según el ciclo ('ANUAL' o 'MENSUAL').
     */
    public static long calcularMontoPesos(long precioMensual, String cicloFacturacion) {
        if (precioMensual <= 0) {
            return 0L;
        }
        return esCicloAnual(cicloFacturacion)
                ? calcularPrecioAnual(precioMensual)
                : precioMensual;
    }

    /**
     * Sobrecarga para BigDecimal (usada en liquidaciones de Wompi / pasarela).
     */
    public static BigDecimal calcularMontoPesos(BigDecimal precioMensual, String cicloFacturacion) {
        if (precioMensual == null || precioMensual.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (esCicloAnual(cicloFacturacion)) {
            BigDecimal factor = MESES_ANUAL.multiply(FACTOR_DESCUENTO_ANUAL); // 9.60
            return precioMensual.multiply(factor).setScale(0, RoundingMode.HALF_UP);
        }
        return precioMensual.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el monto en centavos para pasarelas de pago (Wompi).
     */
    public static long calcularMontoCentavos(long precioMensual, String cicloFacturacion) {
        return calcularMontoPesos(precioMensual, cicloFacturacion) * 100L;
    }

    public static long calcularMontoCentavos(BigDecimal precioMensual, String cicloFacturacion) {
        return calcularMontoPesos(precioMensual, cicloFacturacion).multiply(new BigDecimal(100)).longValue();
    }

    /**
     * Determina si el ciclo solicitado corresponde a facturación anual.
     */
    public static boolean esCicloAnual(String ciclo) {
        return ciclo != null && "ANUAL".equalsIgnoreCase(ciclo.trim());
    }
}
