import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import { formatCurrency, cn } from '../lib/utils.js';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card.tsx';
import ErrorState from './ui/ErrorState.jsx';
import LoadingState from './ui/LoadingState.jsx';

/**
 * F11-02: reconciled KPIs of the signed-in profile from GET /dashboard/kpis.
 * Sections the profile may not see are absent from the response and are not rendered; a rate without
 * a base (nothing billed, no units, no organizations) is shown as "Sin datos", never as a fabricated value.
 */
const pct = (v) => (v === null || v === undefined ? 'Sin datos' : `${Number(v).toFixed(1)} %`);
const money = (v) => (v === null || v === undefined ? 'Sin datos' : formatCurrency(Number(v)));

// Tile tint = a 3px top keyline in the pinned inventory colors; the tile itself stays paper.
const TINT_STYLES = {
  indigo: 'before:bg-tint-primary-fg',
  mint: 'before:bg-tint-green-fg',
  lavender: 'before:bg-tint-purple-fg',
  blue: 'before:bg-tint-blue-fg',
};

function Kpi({ label, value, hint, tint = 'indigo' }) {
  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-md border border-border bg-surface p-3.5 pt-4 before:absolute before:inset-x-0 before:top-0 before:h-[3px]',
        TINT_STYLES[tint] || TINT_STYLES.indigo
      )}
    >
      <div className="text-xs font-semibold text-text-secondary">{label}</div>
      <div className="app-numeral mt-1.5 text-xl text-on-surface">{value}</div>
      {hint ? <div className="mt-1 text-[11px] text-text-muted">{hint}</div> : null}
    </div>
  );
}

export function KpiSummaryPanel({ deps = [] }) {
  const { user } = useAuth();
  const isConviviente =
    user?.rol === 'RESIDENTE_CONVIVENCIA' ||
    user?.rolCodigo === 'RESIDENTE_CONVIVENCIA' ||
    user?.tipoResidente === 'CONVIVIENTE' ||
    user?.tipoRelacion === 'CONVIVIENTE';

  const { data, loading, error } = useFetch(() => api.get('/dashboard/kpis'), deps);

  if (loading) {
    return (
      <div className="rounded-lg border border-border bg-surface shadow-sm">
        <LoadingState size="sm" message="Cargando indicadores..." />
      </div>
    );
  }
  if (error || !data) {
    return (
      <ErrorState
        compact
        title="No fue posible cargar los indicadores del tablero."
        message="Los demás módulos siguen disponibles."
      />
    );
  }

  const { finanzas, pqrs, reservas, plataforma } = data;
  const showFinanzas = finanzas && !isConviviente;

  return (
    <Card>
      <CardHeader className="pb-3 flex flex-row items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="h-2.5 w-2.5 rounded-[2px] bg-btn-primary" aria-hidden="true" />
          <CardTitle className="text-[15px] font-bold">
            {isConviviente ? 'Indicadores Operativos de Convivencia' : 'Indicadores Clave'}
          </CardTitle>
        </div>
        {isConviviente && (
          <span className="rounded-[3px] border border-tint-purple-fg/25 bg-tint-purple-bg px-2 py-0.5 text-[11px] font-bold text-tint-purple-fg">
            Vista conviviente
          </span>
        )}
      </CardHeader>
      <CardContent
        className={cn(
          'grid gap-3',
          isConviviente ? 'grid-cols-1 sm:grid-cols-2' : 'grid-cols-2 md:grid-cols-4'
        )}
      >
        {showFinanzas ? (
          <>
            <Kpi
              label="Recaudo"
              value={pct(finanzas.tasaRecaudo)}
              hint={`${money(finanzas.recaudado)} de ${money(finanzas.facturado)}`}
              tint="mint"
            />
            <Kpi
              label="Cartera pendiente"
              value={money(finanzas.carteraPendiente)}
              hint={`${finanzas.cuotasPendientes} cuota(s)`}
              tint="blue"
            />
            <Kpi
              label="Cartera morosa"
              value={money(finanzas.carteraMorosa)}
              hint={`${finanzas.unidadesEnMora} unidad(es) en mora`}
              tint="lavender"
            />
            <Kpi label="% unidades en mora" value={pct(finanzas.porcentajeMora)} tint="indigo" />
          </>
        ) : null}
        {pqrs ? (
          isConviviente ? (
            <Kpi
              label="PQRS abiertas"
              value={pqrs.abiertas ?? 0}
              hint={
                pqrs.enRiesgo
                  ? `${pqrs.enRiesgo} en seguimiento prioritario`
                  : 'Solicitudes y reportes activos'
              }
              tint="lavender"
            />
          ) : (
            <>
              <Kpi label="PQRS abiertas" value={pqrs.abiertas} tint="lavender" />
              <Kpi label="PQRS en riesgo (SLA ≥ 75 %)" value={pqrs.enRiesgo} tint="indigo" />
              <Kpi label="PQRS vencidas" value={pqrs.vencidas} tint="blue" />
            </>
          )
        ) : null}
        {reservas ? (
          isConviviente ? (
            <Kpi
              label="Reservas próximas"
              value={reservas.proximasAprobadas ?? reservas.pendientes ?? 0}
              hint={`${reservas.hoy ?? 0} programada(s) para hoy · ${reservas.pendientes ?? 0} pendiente(s)`}
              tint="mint"
            />
          ) : (
            <>
              <Kpi label="Reservas pendientes" value={reservas.pendientes} tint="indigo" />
              <Kpi
                label="Reservas aprobadas próximas"
                value={reservas.proximasAprobadas}
                hint={`${reservas.hoy} para hoy`}
                tint="mint"
              />
            </>
          )
        ) : null}
        {plataforma && !isConviviente ? (
          <>
            <Kpi
              label="Organizaciones activas"
              value={plataforma.organizacionesActivas}
              hint={`${plataforma.organizacionesTotal} en total`}
              tint="mint"
            />
            <Kpi
              label="Membresías activas"
              value={plataforma.membresiasActivas}
              hint={`${plataforma.membresiasPorVencer} por vencer en 30 días`}
              tint="blue"
            />
            <Kpi label="Planes activos" value={plataforma.planesActivos} tint="lavender" />
            <Kpi
              label="Churn 30 días"
              value={pct(plataforma.churn30Dias?.tasaChurn)}
              hint={`${plataforma.churn30Dias?.organizacionesPerdidas ?? 0} de ${plataforma.churn30Dias?.organizacionesInicio ?? 0} organizaciones`}
              tint="indigo"
            />
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}
