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
  AlertTriangle,
  RefreshCw,
  Wallet,
  Receipt,
  Users,
  Award,
  Calendar,
  Percent,
  Table as TableIcon,
} from 'lucide-react';

const orgFinChartConfig = {
  facturado: {
    label: 'Facturado',
    color: '#2563eb', // primary blue
  },
  recaudado: {
    label: 'Recaudado',
    color: '#10b981', // emerald
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

  if (loading) {
    return (
      <div className="p-6 space-y-6 animate-fadeIn">
        <div className="flex justify-between items-center">
          <Skeleton className="h-10 w-64" />
          <Skeleton className="h-9 w-36" />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-72 w-full rounded-xl" />
        <Skeleton className="h-96 w-full rounded-xl" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6">
        <div className="bg-destructive/15 border border-destructive text-destructive p-4 rounded-xl flex items-center justify-between">
          <div className="flex items-center gap-3">
            <AlertTriangle className="h-5 w-5 shrink-0" />
            <p className="text-sm font-medium">{error}</p>
          </div>
          <Button variant="outline" size="sm" onClick={loadAnalytics}>
            Reintentar
          </Button>
        </div>
      </div>
    );
  }

  const kpis = analytics?.kpisGlobales || {};
  const benchmark = analytics?.benchmarkPropiedades || [];
  const tendencia = analytics?.tendenciaMensual || [];

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

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-5">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Analítica Organizacional
            </h1>
            <Badge variant="outline" className="text-xs bg-primary/10 text-primary border-primary/20">
              F11-05
            </Badge>
          </div>
          <p className="text-xs sm:text-sm text-muted-foreground mt-1 flex items-center gap-2">
            <Calendar className="h-3.5 w-3.5" />
            <span>Horizonte activo:</span>
            <span className="font-semibold text-foreground font-mono">
              {analytics?.horizonteTemporal || 'Periodo actual'}
            </span>
            <span className="text-muted-foreground/60">•</span>
            <span>{analytics?.mesesEvaluados || meses} meses evaluados</span>
          </p>
        </div>

        <div className="flex items-center gap-3">
          {/* Selector 6 / 12 meses */}
          <div className="flex items-center bg-muted p-1 rounded-lg border border-border">
            <button
              type="button"
              onClick={() => setMeses(6)}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                meses === 6
                  ? 'bg-background text-foreground shadow-xs'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              6 meses
            </button>
            <button
              type="button"
              onClick={() => setMeses(12)}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                meses === 12
                  ? 'bg-background text-foreground shadow-xs'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              12 meses
            </button>
          </div>

          <Button variant="outline" size="sm" onClick={loadAnalytics} className="flex items-center gap-2">
            <RefreshCw className="h-4 w-4" />
            <span>Actualizar</span>
          </Button>
        </div>
      </div>

      {/* KPI Cards (6 cards) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
        {/* 1. Facturado */}
        <Card className="border-border/80 shadow-xs">
          <CardContent className="pt-4 pb-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">Facturado</span>
              <Receipt className="h-4 w-4 text-primary" />
            </div>
            <div className="mt-2">
              <span className="text-lg font-bold font-mono text-foreground">
                {formatCOP(kpis.totalFacturadoPeriodo)}
              </span>
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">Cuotas en ventana</p>
          </CardContent>
        </Card>

        {/* 2. Recaudado */}
        <Card className="border-border/80 shadow-xs">
          <CardContent className="pt-4 pb-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">Recaudado</span>
              <TrendingUp className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
            </div>
            <div className="mt-2">
              <span className="text-lg font-bold font-mono text-emerald-600 dark:text-emerald-400">
                {formatCOP(kpis.totalRecaudadoPeriodo)}
              </span>
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">Pagos aprobados</p>
          </CardContent>
        </Card>

        {/* 3. Cartera Viva */}
        <Card className="border-border/80 shadow-xs">
          <CardContent className="pt-4 pb-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">Cartera Viva</span>
              <Wallet className="h-4 w-4 text-amber-600 dark:text-amber-400" />
            </div>
            <div className="mt-2">
              <span className="text-lg font-bold font-mono text-amber-600 dark:text-amber-400">
                {formatCOP(kpis.totalCarteraVivaActual)}
              </span>
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">
              Ventana: {formatCOP(kpis.totalCarteraPeriodo)}
            </p>
          </CardContent>
        </Card>

        {/* 4. Efectividad de Recaudo */}
        <Card className="border-border/80 shadow-xs">
          <CardContent className="pt-4 pb-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">Efectividad</span>
              <Percent className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
            </div>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="text-lg font-bold font-mono text-emerald-600 dark:text-emerald-400">
                {Number(kpis.efectividadRecaudoGlobalPct || 0).toFixed(1)}%
              </span>
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">Recaudo vs Facturación</p>
          </CardContent>
        </Card>

        {/* 5. Índice de Morosidad */}
        <Card className="border-border/80 shadow-xs">
          <CardContent className="pt-4 pb-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">Morosidad</span>
              <Percent className="h-4 w-4 text-rose-600 dark:text-rose-400" />
            </div>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="text-lg font-bold font-mono text-rose-600 dark:text-rose-400">
                {Number(kpis.indiceMorosidadGlobalPct || 0).toFixed(1)}%
              </span>
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">Cartera vs Facturación</p>
          </CardContent>
        </Card>

        {/* 6. Ocupación Promedio */}
        <Card className="border-border/80 shadow-xs">
          <CardContent className="pt-4 pb-4">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-muted-foreground">Ocupación</span>
              <Users className="h-4 w-4 text-primary" />
            </div>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="text-lg font-bold font-mono text-foreground">
                {Number(kpis.ocupacionPromedioPct || 0).toFixed(1)}%
              </span>
            </div>
            <p className="text-[11px] text-muted-foreground mt-1">
              {kpis.totalUnidades || 0} unidades totales
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Tendencia Mensual: Facturado vs Recaudado con shadcn UI Chart */}
      <Card className="border-border/80 shadow-xs">
        <CardHeader className="pb-3 border-b border-border/60">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <CardTitle className="text-sm font-bold flex items-center gap-2">
                <TrendingUp className="h-4 w-4 text-primary" />
                Tendencia Mensual Consolidada (Facturado vs Recaudado)
              </CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                Evolución agregada de emisión y recaudo efectivo en las copropiedades
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowTable(!showTable)}
                className="h-8 px-2.5 text-xs text-muted-foreground hover:text-foreground"
              >
                <TableIcon className="h-3.5 w-3.5 mr-1" />
                {showTable ? 'Gráfica' : 'Tabla'}
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent className="pt-5 space-y-4">
          {/* Summary Strip */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
              <p className="text-[11px] text-muted-foreground font-medium">Facturado Acumulado</p>
              <p className="text-sm sm:text-base font-bold font-mono text-primary">
                {formatCompactCOP(tendenciaTotals.facturado)}
              </p>
            </div>
            <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
              <p className="text-[11px] text-muted-foreground font-medium">Recaudo Efectivo</p>
              <p className="text-sm sm:text-base font-bold font-mono text-emerald-600 dark:text-emerald-400">
                {formatCompactCOP(tendenciaTotals.recaudado)}
              </p>
            </div>
            <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
              <p className="text-[11px] text-muted-foreground font-medium">Efectividad Global</p>
              <div className="flex items-center gap-2">
                <p className="text-sm sm:text-base font-bold font-mono text-foreground">
                  {tasaRecaudoTendencia}%
                </p>
                <Badge
                  variant="outline"
                  className={`text-[9px] px-1 py-0 font-bold ${
                    tasaRecaudoTendencia >= 80
                      ? 'text-emerald-600 border-emerald-500/30 bg-emerald-500/10'
                      : 'text-amber-600 border-amber-500/30 bg-amber-500/10'
                  }`}
                >
                  {tasaRecaudoTendencia >= 80 ? 'Óptimo' : 'Atención'}
                </Badge>
              </div>
            </div>
          </div>

          {tendencia.length === 0 ? (
            <p className="text-xs text-muted-foreground text-center py-8">
              No hay registros para la ventana seleccionada.
            </p>
          ) : showTable ? (
            <div className="overflow-x-auto border border-border/60 rounded-lg">
              <table className="w-full text-left text-xs">
                <thead className="bg-muted/40 text-muted-foreground font-semibold border-b border-border/60">
                  <tr>
                    <th className="py-2.5 px-3">Periodo</th>
                    <th className="py-2.5 px-3 text-right">Facturado</th>
                    <th className="py-2.5 px-3 text-right">Recaudado</th>
                    <th className="py-2.5 px-3 text-right">Efectividad</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/50">
                  {tendencia.map((row) => {
                    const f = Number(row.facturado) || 0;
                    const r = Number(row.recaudado) || 0;
                    const pct = f > 0 ? Math.round((r / f) * 100) : 0;
                    return (
                      <tr key={row.periodo} className="hover:bg-muted/20 transition-colors">
                        <td className="py-2 px-3 font-mono font-medium text-foreground">{row.periodo}</td>
                        <td className="py-2 px-3 text-right font-mono text-primary">{formatCOP(f)}</td>
                        <td className="py-2 px-3 text-right font-mono text-emerald-600 dark:text-emerald-400">{formatCOP(r)}</td>
                        <td className="py-2 px-3 text-right font-mono font-bold">{pct}%</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="pt-2">
              <ChartContainer config={orgFinChartConfig} className="h-[280px] w-full">
                <BarChart data={chartDataTendencia} margin={{ top: 15, right: 10, left: 10, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} className="stroke-border/50" />
                  <XAxis dataKey="periodo" stroke="currentColor" className="text-[11px] text-muted-foreground" tickLine={false} axisLine={false} />
                  <YAxis stroke="currentColor" className="text-[11px] text-muted-foreground" tickLine={false} axisLine={false} tickFormatter={formatCompactCOP} />
                  <ChartTooltip
                    content={
                      <ChartTooltipContent
                        formatter={(value, name) => [formatCOP(value), orgFinChartConfig[name]?.label || name]}
                      />
                    }
                  />
                  <ChartLegend content={<ChartLegendContent />} />
                  <Bar dataKey="facturado" fill="var(--color-facturado)" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="recaudado" fill="var(--color-recaudado)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ChartContainer>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Benchmark de Propiedades */}
      <Card className="border-border/80 shadow-xs">
        <CardHeader className="pb-3 border-b border-border/60">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-bold flex items-center gap-2">
              <Award className="h-4 w-4 text-primary" />
              Benchmark y Rankings de Propiedades
            </CardTitle>
            <span className="text-xs text-muted-foreground">
              {benchmark.length} {benchmark.length === 1 ? 'propiedad evaluada' : 'propiedades evaluadas'}
            </span>
          </div>
        </CardHeader>
        <CardContent className="pt-4 p-0 sm:p-4">
          {benchmark.length === 0 ? (
            <p className="text-xs text-muted-foreground text-center py-8">
              No hay propiedades registradas en esta organización.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead>
                  <tr className="border-b border-border bg-muted/40 text-muted-foreground font-semibold">
                    <th className="py-2.5 px-3">Propiedad</th>
                    <th className="py-2.5 px-3">Ciudad</th>
                    <th className="py-2.5 px-3 text-center">Unidades / Ocupación</th>
                    <th className="py-2.5 px-3 text-right">Facturado</th>
                    <th className="py-2.5 px-3 text-right">Recaudado</th>
                    <th className="py-2.5 px-3 text-center">Efectividad</th>
                    <th className="py-2.5 px-3 text-center">Morosidad</th>
                    <th className="py-2.5 px-3 text-right">Cartera Viva</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {benchmark.map((p) => {
                    const ocupPct = Number(p.ocupacionActualPct || 0);
                    const efectPct = Number(p.efectividadRecaudoPct || 0);
                    const morosPct = Number(p.indiceMorosidadPct || 0);

                    return (
                      <tr key={p.idPropiedad} className="hover:bg-muted/30 transition-colors">
                        <td className="py-3 px-3">
                          <div className="font-semibold text-foreground">{p.nombre}</div>
                          <div className="text-[10px] text-muted-foreground font-mono">ID: {p.idPropiedad}</div>
                        </td>
                        <td className="py-3 px-3 text-muted-foreground">{p.ciudad || 'Colombia'}</td>
                        <td className="py-3 px-3">
                          <div className="flex flex-col items-center gap-1">
                            <span className="font-mono text-[11px]">
                              {p.unidadesOcupadas} / {p.totalUnidades} ({ocupPct.toFixed(1)}%)
                            </span>
                            <div className="w-24 bg-muted rounded-full h-1.5 overflow-hidden">
                              <div
                                className="bg-primary h-1.5 rounded-full"
                                style={{ width: `${Math.min(ocupPct, 100)}%` }}
                              />
                            </div>
                            <span className="text-[9px] text-muted-foreground font-mono">
                              Rank #{p.rankingOcupacion}
                            </span>
                          </div>
                        </td>
                        <td className="py-3 px-3 text-right font-mono text-muted-foreground">
                          {formatCOP(p.facturadoPeriodo)}
                        </td>
                        <td className="py-3 px-3 text-right font-mono text-emerald-600 dark:text-emerald-400 font-semibold">
                          {formatCOP(p.recaudadoPeriodo)}
                        </td>
                        <td className="py-3 px-3 text-center">
                          <Badge
                            variant="outline"
                            className={`font-mono text-[10px] ${
                              efectPct >= 80
                                ? 'bg-emerald-500/10 text-emerald-600 border-emerald-500/30'
                                : efectPct >= 50
                                ? 'bg-amber-500/10 text-amber-600 border-amber-500/30'
                                : 'bg-rose-500/10 text-rose-600 border-rose-500/30'
                            }`}
                          >
                            {efectPct.toFixed(1)}% (#{p.rankingEfectividad})
                          </Badge>
                        </td>
                        <td className="py-3 px-3 text-center">
                          <Badge
                            variant="outline"
                            className={`font-mono text-[10px] ${
                              morosPct <= 10
                                ? 'bg-emerald-500/10 text-emerald-600 border-emerald-500/30'
                                : morosPct <= 30
                                ? 'bg-amber-500/10 text-amber-600 border-amber-500/30'
                                : 'bg-rose-500/10 text-rose-600 border-rose-500/30'
                            }`}
                          >
                            {morosPct.toFixed(1)}% (#{p.rankingMorosidad})
                          </Badge>
                        </td>
                        <td className="py-3 px-3 text-right font-mono text-rose-600 dark:text-rose-400 font-medium">
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
    </div>
  );
}
