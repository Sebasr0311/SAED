import api from '../lib/api.js';
import { useFetch } from '../lib/hooks.js';
import { formatCurrency } from '../lib/utils.js';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card.tsx';

/**
 * F11-02: reconciled KPIs of the signed-in profile from GET /dashboard/kpis.
 * Sections the profile may not see are absent from the response and are not rendered; a rate without
 * a base (nothing billed, no units, no organizations) is shown as "Sin datos", never as a fabricated value.
 */
const pct = (v) => (v === null || v === undefined ? 'Sin datos' : `${Number(v).toFixed(1)} %`);
const money = (v) => (v === null || v === undefined ? 'Sin datos' : formatCurrency(Number(v)));

function Kpi({ label, value, hint }) {
  return (
    <div className="rounded-lg border border-border/50 bg-muted/20 p-3">
      <div className="text-[11px] font-medium text-muted-foreground">{label}</div>
      <div className="text-lg font-semibold text-foreground">{value}</div>
      {hint ? <div className="text-[10px] text-muted-foreground">{hint}</div> : null}
    </div>
  );
}

export function KpiSummaryPanel({ deps = [] }) {
  const { data, loading, error } = useFetch(() => api.get('/dashboard/kpis'), deps);

  if (loading) {
    return <p className="text-xs text-muted-foreground">Cargando indicadores...</p>;
  }
  if (error || !data) {
    return <p className="text-xs text-destructive">No fue posible cargar los indicadores del tablero.</p>;
  }

  const { finanzas, pqrs, reservas, plataforma } = data;

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">Indicadores clave</CardTitle>
      </CardHeader>
      <CardContent className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {finanzas ? (
          <>
            <Kpi label="Recaudo" value={pct(finanzas.tasaRecaudo)}
              hint={`${money(finanzas.recaudado)} de ${money(finanzas.facturado)}`} />
            <Kpi label="Cartera pendiente" value={money(finanzas.carteraPendiente)}
              hint={`${finanzas.cuotasPendientes} cuota(s)`} />
            <Kpi label="Cartera morosa" value={money(finanzas.carteraMorosa)}
              hint={`${finanzas.unidadesEnMora} unidad(es) en mora`} />
            <Kpi label="% unidades en mora" value={pct(finanzas.porcentajeMora)} />
          </>
        ) : null}
        {pqrs ? (
          <>
            <Kpi label="PQRS abiertas" value={pqrs.abiertas} />
            <Kpi label="PQRS en riesgo (SLA ≥ 75 %)" value={pqrs.enRiesgo} />
            <Kpi label="PQRS vencidas" value={pqrs.vencidas} />
          </>
        ) : null}
        {reservas ? (
          <>
            <Kpi label="Reservas pendientes" value={reservas.pendientes} />
            <Kpi label="Reservas aprobadas próximas" value={reservas.proximasAprobadas}
              hint={`${reservas.hoy} para hoy`} />
          </>
        ) : null}
        {plataforma ? (
          <>
            <Kpi label="Organizaciones activas" value={plataforma.organizacionesActivas}
              hint={`${plataforma.organizacionesTotal} en total`} />
            <Kpi label="Membresías activas" value={plataforma.membresiasActivas}
              hint={`${plataforma.membresiasPorVencer} por vencer en 30 días`} />
            <Kpi label="Planes activos" value={plataforma.planesActivos} />
            <Kpi label="Churn 30 días" value={pct(plataforma.churn30Dias?.tasaChurn)}
              hint={`${plataforma.churn30Dias?.organizacionesPerdidas ?? 0} de ${plataforma.churn30Dias?.organizacionesInicio ?? 0} organizaciones`} />
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}
