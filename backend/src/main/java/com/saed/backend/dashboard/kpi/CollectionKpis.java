package com.saed.backend.dashboard.kpi;

import java.math.BigDecimal;

/**
 * Collection and arrears KPIs. Rates are null when there is no base (nothing billed / no units), never a
 * fabricated 0 or 100.
 */
public record CollectionKpis(BigDecimal facturado, BigDecimal recaudado, BigDecimal tasaRecaudo,
                             BigDecimal carteraPendiente, BigDecimal carteraMorosa, long cuotasPendientes,
                             long unidadesEnMora, BigDecimal porcentajeMora) {
}
