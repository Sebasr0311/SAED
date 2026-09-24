import { useEffect, useState, useCallback } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/Button.jsx';
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
} from 'lucide-react';

export default function OrgAnaliticaPage() {
  const [meses, setMeses] = useState(12);
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

  // Calcular valor máximo para escalar barras de tendencia
  const maxMontoTrend = Math.max(
    ...tendencia.flatMap((t) => [Number(t.facturado) || 0, Number(t.recaudado) || 0]),
    1
  );

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

      {/* Tendencia Mensual: Facturado vs Recaudado */}
      <Card className="border-border/80 shadow-xs">
        <CardHeader className="pb-3 border-b border-border/60">
          <div className="flex items-center justify-between">
            <CardTitle className="text-sm font-bold flex items-center gap-2">
              <TrendingUp className="h-4 w-4 text-primary" />
              Tendencia Mensual Consolidada (Facturado vs Recaudado)
            </CardTitle>
            <div className="flex items-center gap-4 text-xs">
              <div className="flex items-center gap-1.5">
                <span className="h-3 w-3 rounded-xs bg-primary inline-block" />
                <span className="text-muted-foreground">Facturado</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-3 w-3 rounded-xs bg-emerald-500 inline-block" />
                <span className="text-muted-foreground">Recaudado</span>
              </div>
            </div>
          </div>
        </CardHeader>
        <CardContent className="pt-5 space-y-4">
          {tendencia.length === 0 ? (
            <p className="text-xs text-muted-foreground text-center py-8">
              No hay registros para la ventana seleccionada.
            </p>
          ) : (
            <div className="space-y-3">
              {tendencia.map((m) => {
                const factVal = Number(m.facturado) || 0;
                const recVal = Number(m.recaudado) || 0;
                const pctFact = Math.max(Math.round((factVal / maxMontoTrend) * 100), factVal > 0 ? 3 : 0);
                const pctRec = Math.max(Math.round((recVal / maxMontoTrend) * 100), recVal > 0 ? 3 : 0);

                return (
                  <div key={m.periodo} className="p-2.5 rounded-lg border border-border/50 bg-card/40 space-y-1.5">
                    <div className="flex justify-between items-center text-xs">
                      <span className="font-mono font-semibold text-foreground">{m.periodo}</span>
                      <div className="flex items-center gap-3 font-mono text-[11px]">
                        <span className="text-primary font-medium">Fact: {formatCOP(factVal)}</span>
                        <span className="text-muted-foreground/40">|</span>
                        <span className="text-emerald-600 dark:text-emerald-400 font-medium">
                          Rec: {formatCOP(recVal)}
                        </span>
                      </div>
                    </div>
                    {/* Barras comparativas */}
                    <div className="space-y-1">
                      <div className="w-full bg-muted/60 rounded-full h-2 overflow-hidden">
                        <div
                          className="bg-primary h-2 rounded-full transition-all duration-500"
                          style={{ width: `${pctFact}%` }}
                          title={`Facturado: ${formatCOP(factVal)}`}
                        />
                      </div>
                      <div className="w-full bg-muted/60 rounded-full h-2 overflow-hidden">
                        <div
                          className="bg-emerald-500 h-2 rounded-full transition-all duration-500"
                          style={{ width: `${pctRec}%` }}
                          title={`Recaudado: ${formatCOP(recVal)}`}
                        />
                      </div>
                    </div>
                  </div>
                );
              })}
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
