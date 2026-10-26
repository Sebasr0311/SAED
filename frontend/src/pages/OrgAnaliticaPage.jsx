import { useEffect, useState, useCallback, useMemo } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
} from 'recharts';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/Button.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { cn } from '../lib/utils.js';
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent,
} from '../components/ui/chart.tsx';
import {
  TrendingUp,
  Building,
  RefreshCw,
  Wallet,
  Receipt,
  Users,
  Award,
  Percent,
  Table as TableIcon,
  BarChart3,
} from 'lucide-react';

function pctVariant(value, goodHigh, okHigh, higherIsBetter = true) {
  if (higherIsBetter) return value >= goodHigh ? 'success' : value >= okHigh ? 'warning' : 'destructive';
  return value <= goodHigh ? 'success' : value <= okHigh ? 'warning' : 'destructive';
}

const orgFinChartConfig = {
  facturado: {
    label: 'Facturado',
    color: 'var(--chart-1)',
  },
  recaudado: {
    label: 'Recaudado',
    color: 'var(--tint-green-fg)',
  },
};

export default function OrgAnaliticaPage() {
  const [meses, setMeses] = useState(12);
  const [showTable, setShowTable] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [analytics, setAnalytics] = useState(null);

  const loadAnalytics = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get(`/org/dashboard/analytics?meses=${meses}`);
      const payload = res?.data?.data || res?.data || null;
      setAnalytics(payload);
    } catch (err) {
      console.error('Error cargando analitica:', err);
      setError(
        err?.response?.data?.message ||
        'No se pudieron cargar las métricas analíticas de la organización.'
      );
    } finally {
      setLoading(false);
    }
  }, [meses]);

  useEffect(() => {
    loadAnalytics();
  }, [loadAnalytics]);

  const formatCOP = (val) =>
    new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 0,
    }).format(Number(val) || 0);

  const formatCompactCOP = (val) => {
    const num = Number(val) || 0;
    if (Math.abs(num) >= 1000000) {
      return `$${(num / 1000000).toFixed(1)}M`;
    }
    if (Math.abs(num) >= 1000) {
      return `$${(num / 1000).toFixed(0)}k`;
    }
    return `$${num}`;
  };

  const chartDataTendencia = useMemo(() => {
    return (analytics?.tendenciaMensual || []).map((t) => ({
      periodo: t.periodo,
      facturado: Number(t.facturado) || 0,
      recaudado: Number(t.recaudado) || 0,
    }));
  }, [analytics]);

  const tendenciaTotals = useMemo(() => {
    return (analytics?.tendenciaMensual || []).reduce(
      (acc, curr) => {
        acc.facturado += Number(curr.facturado) || 0;
        acc.recaudado += Number(curr.recaudado) || 0;
        return acc;
      },
      { facturado: 0, recaudado: 0 }
    );
  }, [analytics]);

  const tasaRecaudoTendencia = useMemo(() => {
    if (tendenciaTotals.facturado <= 0) return 0;
    return Math.min(100, Math.round((tendenciaTotals.recaudado / tendenciaTotals.facturado) * 100));
  }, [tendenciaTotals]);

  const header = (
    <PageHeader
      title="Analítica organizacional"
      subtitle={
        loading || error
          ? 'Facturación, recaudo y comparación entre las propiedades de la organización.'
          : `Horizonte: ${analytics?.horizonteTemporal || 'periodo actual'} · ${analytics?.mesesEvaluados || meses} meses evaluados`
      }
      action={
        <>
          <div role="group" aria-label="Ventana de análisis" className="inline-flex rounded-md border border-border bg-surface-dim p-1">
            {[6, 12].map((m) => (
              <button
                key={m}
                type="button"
                aria-pressed={meses === m}
                onClick={() => setMeses(m)}
                className={cn(
                  'min-h-9 rounded px-3 text-xs font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  meses === m ? 'bg-surface text-primary-text shadow-sm' : 'text-text-secondary hover:text-on-surface'
                )}
              >
                {m} meses
              </button>
            ))}
          </div>
          <Button variant="outline" size="sm" onClick={loadAnalytics} loading={loading}>
            {!loading && <RefreshCw className="h-4 w-4" aria-hidden="true" />}
            Actualizar
          </Button>
        </>
      }
    />
  );

  if (loading) {
    return (
      <PageContainer>
        {header}
        <div role="status" aria-busy="true" aria-label="Cargando analítica" className="space-y-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <Skeleton key={i} className="h-24 rounded-lg" />
            ))}
          </div>
          <Skeleton className="h-72 w-full rounded-lg" />
          <Skeleton className="h-96 w-full rounded-lg" />
        </div>
      </PageContainer>
    );
  }

  if (error) {
    return (
      <PageContainer>
        {header}
        <ErrorState title="No se pudo cargar la analítica" message={error} onRetry={loadAnalytics} />
      </PageContainer>
    );
  }

  const kpis = analytics?.kpisGlobales || {};
  const benchmark = analytics?.benchmarkPropiedades || [];
  const tendencia = analytics?.tendenciaMensual || [];
  const morosidad = Number(kpis.indiceMorosidadGlobalPct || 0);

  return (
    <PageContainer>
      {header}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <MetricCard label="Facturado" value={formatCOP(kpis.totalFacturadoPeriodo)} context="Cuotas emitidas en la ventana" icon={Receipt} />
        <MetricCard
          label="Recaudado"
          value={formatCOP(kpis.totalRecaudadoPeriodo)}
          context="Pagos aprobados"
          icon={TrendingUp}
          variant="success"
        />
        <MetricCard
          label="Cartera viva"
          value={formatCOP(kpis.totalCarteraVivaActual)}
          context={`En la ventana: ${formatCOP(kpis.totalCarteraPeriodo)}`}
          icon={Wallet}
          variant="warning"
        />
        <MetricCard
          label="Efectividad de recaudo"
          value={`${Number(kpis.efectividadRecaudoGlobalPct || 0).toFixed(1)}%`}
          context="Recaudo frente a facturación"
          icon={Percent}
          variant="success"
        />
        <MetricCard
          label="Índice de morosidad"
          value={`${morosidad.toFixed(1)}%`}
          context="Cartera frente a facturación"
          icon={Percent}
          variant={morosidad > 30 ? 'danger' : morosidad > 10 ? 'warning' : 'secondary'}
        />
        <MetricCard
          label="Ocupación promedio"
          value={`${Number(kpis.ocupacionPromedioPct || 0).toFixed(1)}%`}
          context={`${kpis.totalUnidades || 0} unidades totales`}
          icon={Users}
          variant="info"
        />
      </div>

      <Card>
        <CardHeader className="border-b border-border pb-3">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle className="text-[15px]">Tendencia mensual: facturado y recaudado</CardTitle>
              <p className="mt-0.5 text-xs text-text-muted">Evolución agregada de emisión y recaudo en las copropiedades.</p>
            </div>
            {tendencia.length > 0 && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowTable(!showTable)}
                aria-pressed={showTable}
              >
                {showTable ? <BarChart3 className="h-4 w-4" aria-hidden="true" /> : <TableIcon className="h-4 w-4" aria-hidden="true" />}
                {showTable ? 'Ver gráfica' : 'Ver tabla'}
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent className="space-y-4 pt-5">
          <dl className="grid grid-cols-1 divide-y divide-border rounded-lg border border-border sm:grid-cols-3 sm:divide-x sm:divide-y-0">
            <div className="space-y-1 p-3">
              <dt className="text-xs font-semibold text-text-muted">Facturado acumulado</dt>
              <dd className="app-numeral text-lg text-on-surface">{formatCOP(tendenciaTotals.facturado)}</dd>
            </div>
            <div className="space-y-1 p-3">
              <dt className="text-xs font-semibold text-text-muted">Recaudo efectivo</dt>
              <dd className="app-numeral text-lg text-tint-green-fg">{formatCOP(tendenciaTotals.recaudado)}</dd>
            </div>
            <div className="space-y-1 p-3">
              <dt className="text-xs font-semibold text-text-muted">Efectividad en la ventana</dt>
              <dd className="flex items-center gap-2">
                <span className="app-numeral text-lg text-on-surface">
                  {tendenciaTotals.facturado > 0 ? `${tasaRecaudoTendencia}%` : '—'}
                </span>
                {tendenciaTotals.facturado > 0 && (
                  <Badge variant={tasaRecaudoTendencia >= 80 ? 'success' : 'warning'}>
                    {tasaRecaudoTendencia >= 80 ? 'Óptimo' : 'Atención'}
                  </Badge>
                )}
              </dd>
            </div>
          </dl>

          {tendencia.length === 0 ? (
            <EmptyState
              icon={BarChart3}
              title="Sin registros en la ventana"
              subtitle={meses === 6 ? 'Prueba con la ventana de 12 meses.' : 'Aún no hay cuotas emitidas en este periodo.'}
            >
              {meses === 6 && (
                <Button variant="outline" size="sm" className="mt-3" onClick={() => setMeses(12)}>
                  Ver 12 meses
                </Button>
              )}
            </EmptyState>
          ) : showTable ? (
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Periodo</th>
                    <th scope="col" className="text-right">Facturado</th>
                    <th scope="col" className="text-right">Recaudado</th>
                    <th scope="col" className="text-right">Efectividad</th>
                  </tr>
                </thead>
                <tbody>
                  {tendencia.map((row) => {
                    const f = Number(row.facturado) || 0;
                    const r = Number(row.recaudado) || 0;
                    const pct = f > 0 ? Math.round((r / f) * 100) : 0;
                    return (
                      <tr key={row.periodo}>
                        <td className="font-medium tabular-nums text-on-surface">{row.periodo}</td>
                        <td className="whitespace-nowrap text-right tabular-nums">{formatCOP(f)}</td>
                        <td className="whitespace-nowrap text-right tabular-nums text-tint-green-fg">{formatCOP(r)}</td>
                        <td className="text-right font-bold tabular-nums">{f > 0 ? `${pct}%` : '—'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="pt-2">
              <ChartContainer
                config={orgFinChartConfig}
                className="h-[280px] w-full"
                role="img"
                aria-label="Gráfica de barras de facturado y recaudado por mes. Usa Ver tabla para los valores exactos."
              >
                <BarChart data={chartDataTendencia} margin={{ top: 15, right: 10, left: 0, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="var(--border)" />
                  <XAxis dataKey="periodo" tickLine={false} axisLine={false} tick={{ fill: 'var(--text-secondary)', fontSize: 11 }} />
                  <YAxis
                    tickLine={false}
                    axisLine={false}
                    tickFormatter={formatCompactCOP}
                    width={56}
                    tick={{ fill: 'var(--text-secondary)', fontSize: 11 }}
                  />
                  <ChartTooltip
                    cursor={{ fill: 'var(--surface-container)' }}
                    content={
                      <ChartTooltipContent
                        formatter={(value, name) => [formatCOP(value), orgFinChartConfig[name]?.label || name]}
                      />
                    }
                  />
                  <ChartLegend content={<ChartLegendContent />} />
                  <Bar dataKey="facturado" fill="var(--color-facturado)" radius={[2, 2, 0, 0]} maxBarSize={28} />
                  <Bar dataKey="recaudado" fill="var(--color-recaudado)" radius={[2, 2, 0, 0]} maxBarSize={28} />
                </BarChart>
              </ChartContainer>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="border-b border-border pb-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <CardTitle className="flex items-center gap-2 text-[15px]">
              <Award className="h-4 w-4 text-text-secondary" aria-hidden="true" />
              Comparativo entre propiedades
            </CardTitle>
            <span className="text-xs text-text-muted tabular-nums">
              {benchmark.length} {benchmark.length === 1 ? 'propiedad evaluada' : 'propiedades evaluadas'}
            </span>
          </div>
        </CardHeader>
        <CardContent className="p-0 sm:p-4">
          {benchmark.length === 0 ? (
            <EmptyState
              icon={Building}
              title="No hay propiedades registradas"
              subtitle="Cuando la organización tenga propiedades, aquí se comparará su desempeño."
            />
          ) : (
            <div className="table-container max-h-[70vh] overflow-y-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Propiedad</th>
                    <th scope="col">Ciudad</th>
                    <th scope="col">Ocupación</th>
                    <th scope="col" className="text-right">Facturado</th>
                    <th scope="col" className="text-right">Recaudado</th>
                    <th scope="col" className="text-center">Efectividad</th>
                    <th scope="col" className="text-center">Morosidad</th>
                    <th scope="col" className="text-right">Cartera viva</th>
                  </tr>
                </thead>
                <tbody>
                  {benchmark.map((p) => {
                    const ocupPct = Number(p.ocupacionActualPct || 0);
                    const efectPct = Number(p.efectividadRecaudoPct || 0);
                    const morosPct = Number(p.indiceMorosidadPct || 0);

                    return (
                      <tr key={p.idPropiedad}>
                        <td className="min-w-[10rem]">
                          <div className="font-semibold text-on-surface">{p.nombre}</div>
                          <div className="font-mono text-xs text-text-muted tabular-nums">ID: {p.idPropiedad}</div>
                        </td>
                        <td className="text-text-secondary">{p.ciudad || 'Colombia'}</td>
                        <td className="min-w-[9rem]">
                          <div className="flex flex-col gap-1">
                            <span className="text-xs tabular-nums">
                              {p.unidadesOcupadas} / {p.totalUnidades} ({ocupPct.toFixed(1)}%)
                            </span>
                            <div
                              className="h-1.5 w-24 overflow-hidden rounded-[2px] bg-surface-dim"
                              role="progressbar"
                              aria-label={`Ocupación ${ocupPct.toFixed(1)}%`}
                              aria-valuemin={0}
                              aria-valuemax={100}
                              aria-valuenow={Math.round(Math.min(ocupPct, 100))}
                            >
                              <div className="h-full bg-btn-primary" style={{ width: `${Math.min(ocupPct, 100)}%` }} />
                            </div>
                            <span className="text-xs text-text-muted tabular-nums">Puesto #{p.rankingOcupacion}</span>
                          </div>
                        </td>
                        <td className="whitespace-nowrap text-right tabular-nums text-text-secondary">
                          {formatCOP(p.facturadoPeriodo)}
                        </td>
                        <td className="whitespace-nowrap text-right font-semibold tabular-nums text-tint-green-fg">
                          {formatCOP(p.recaudadoPeriodo)}
                        </td>
                        <td className="text-center">
                          <Badge variant={pctVariant(efectPct, 80, 50, true)} className="tabular-nums">
                            {efectPct.toFixed(1)}% · #{p.rankingEfectividad}
                          </Badge>
                        </td>
                        <td className="text-center">
                          <Badge variant={pctVariant(morosPct, 10, 30, false)} className="tabular-nums">
                            {morosPct.toFixed(1)}% · #{p.rankingMorosidad}
                          </Badge>
                        </td>
                        <td
                          className={cn(
                            'whitespace-nowrap text-right font-semibold tabular-nums',
                            Number(p.carteraTotalActual) > 0 ? 'text-destructive' : 'text-text-muted'
                          )}
                        >
                          {formatCOP(p.carteraTotalActual)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </PageContainer>
  );
}
