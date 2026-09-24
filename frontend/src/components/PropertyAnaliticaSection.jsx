import { useState, useMemo } from 'react';
import {
  BarChart,
  Bar,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
} from 'recharts';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useTenant } from '../lib/TenantContext.jsx';
import { useFetch } from '../lib/hooks.js';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from './ui/card.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from './ui/tabs.tsx';
import { Badge } from './ui/badge.tsx';
import { Button } from './ui/Button.jsx';
import { Skeleton } from './ui/skeleton.tsx';
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent,
} from './ui/chart.tsx';
import {
  TrendingUp,
  Activity,
  AlertTriangle,
  RefreshCw,
  Receipt,
  Users,
  Calendar,
  Package,
  UserCheck,
  ShieldCheck,
  DollarSign,
  ArrowUpRight,
  Table as TableIcon,
} from 'lucide-react';

const finChartConfig = {
  facturado: {
    label: 'Facturado',
    color: '#2563eb', // primary blue
  },
  recaudado: {
    label: 'Recaudado',
    color: '#10b981', // emerald
  },
  gastos: {
    label: 'Gastos',
    color: '#f43f5e', // rose
  },
};

const opChartConfig = {
  visitas: {
    label: 'Visitas',
    color: '#2563eb', // primary blue
  },
  paquetes: {
    label: 'Paquetes',
    color: '#f59e0b', // amber
  },
  pqrs: {
    label: 'PQRS',
    color: '#8b5cf6', // purple
  },
};

export function PropertyAnaliticaSection() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();
  const [meses, setMeses] = useState(12);
  const [showTable, setShowTable] = useState(false);

  const {
    data: analiticaRaw,
    loading,
    error,
    refetch,
  } = useFetch(
    () =>
      tenant.activeAssignmentId
        ? tenantApi.get(`/dashboard/propiedad/analitica?meses=${meses}`)
        : Promise.resolve(null),
    [tenant.activeAssignmentId, meses]
  );

  const analitica = useMemo(() => {
    if (!analiticaRaw) return null;
    return analiticaRaw.raw || analiticaRaw.data || analiticaRaw;
  }, [analiticaRaw]);

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

  const finTrend = useMemo(() => analitica?.tendenciaFinanciera || [], [analitica]);
  const opTrend = useMemo(() => analitica?.tendenciaOperativa || [], [analitica]);
  const ocupacionPct = Number(analitica?.ocupacionActualPct || 0);

  // Totales acumulados del periodo financiero
  const finTotals = useMemo(() => {
    return finTrend.reduce(
      (acc, curr) => {
        acc.facturado += Number(curr.facturado) || 0;
        acc.recaudado += Number(curr.recaudado) || 0;
        acc.gastos += Number(curr.gastos) || 0;
        acc.balance += Number(curr.balanceNeto) || 0;
        return acc;
      },
      { facturado: 0, recaudado: 0, gastos: 0, balance: 0 }
    );
  }, [finTrend]);

  // Eficiencia de recaudo
  const tasaRecaudo = useMemo(() => {
    if (finTotals.facturado <= 0) return 0;
    return Math.min(100, Math.round((finTotals.recaudado / finTotals.facturado) * 100));
  }, [finTotals]);

  // Totales operativos
  const opTotals = useMemo(() => {
    return opTrend.reduce(
      (acc, curr) => {
        acc.visitas += Number(curr.totalVisitas) || 0;
        acc.paquetes += Number(curr.totalPaquetes) || 0;
        acc.pqrs += Number(curr.pqrsRadicadas) || 0;
        return acc;
      },
      { visitas: 0, paquetes: 0, pqrs: 0 }
    );
  }, [opTrend]);

  const slaPromedio = useMemo(() => {
    if (opTrend.length === 0) return 0;
    const sumSla = opTrend.reduce((acc, curr) => acc + (Number(curr.pqrsResueltasEnSlaPct) || 0), 0);
    return (sumSla / opTrend.length).toFixed(1);
  }, [opTrend]);

  // Transformar datos para Recharts
  const chartDataFin = useMemo(() => {
    return finTrend.map((item) => ({
      periodo: item.periodo,
      facturado: Number(item.facturado) || 0,
      recaudado: Number(item.recaudado) || 0,
      gastos: Number(item.gastos) || 0,
      balance: Number(item.balanceNeto) || 0,
    }));
  }, [finTrend]);

  const chartDataOp = useMemo(() => {
    return opTrend.map((item) => ({
      periodo: item.periodo,
      visitas: Number(item.totalVisitas) || 0,
      paquetes: Number(item.totalPaquetes) || 0,
      pqrs: Number(item.pqrsRadicadas) || 0,
      sla: Number(item.pqrsResueltasEnSlaPct) || 0,
    }));
  }, [opTrend]);

  if (loading) {
    return (
      <Card className="border-border/80 shadow-xs">
        <CardHeader className="pb-3 border-b border-border/50">
          <div className="flex justify-between items-center">
            <div className="space-y-1.5">
              <Skeleton className="h-5 w-64" />
              <Skeleton className="h-3.5 w-44" />
            </div>
            <Skeleton className="h-8 w-28" />
          </div>
        </CardHeader>
        <CardContent className="pt-6 space-y-4">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Skeleton className="h-16 rounded-lg" />
            <Skeleton className="h-16 rounded-lg" />
            <Skeleton className="h-16 rounded-lg" />
            <Skeleton className="h-16 rounded-lg" />
          </div>
          <Skeleton className="h-72 w-full rounded-xl" />
        </CardContent>
      </Card>
    );
  }

  if (error) {
    const errorMsg =
      error?.response?.data?.message ||
      error?.message ||
      (typeof error === 'string'
        ? error
        : 'No se pudieron sincronizar las métricas analíticas.');

    return (
      <div className="bg-destructive/10 border border-destructive/30 text-destructive p-4 rounded-xl flex items-center justify-between">
        <div className="flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          <span className="text-xs font-medium">{errorMsg}</span>
        </div>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          Reintentar
        </Button>
      </div>
    );
  }

  if (!analitica) {
    return (
      <Card className="border-border/80 shadow-xs p-8 text-center text-xs text-muted-foreground">
        No hay datos analíticos registrados para esta copropiedad.
      </Card>
    );
  }

  return (
    <Card className="border-border/80 shadow-xs">
      <CardHeader className="pb-4 border-b border-border/60">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <CardTitle className="text-base font-bold flex items-center gap-2 text-foreground">
                <TrendingUp className="h-4 w-4 text-primary" />
                Tendencias y Rendimiento
              </CardTitle>
              <Badge variant="outline" className="text-[10px] bg-primary/10 text-primary border-primary/20 font-semibold">
                COPROPIEDAD
              </Badge>
            </div>
            <CardDescription className="text-xs text-muted-foreground mt-1 flex items-center gap-2">
              <Calendar className="h-3.5 w-3.5" />
              <span>Horizonte:</span>
              <span className="font-mono font-medium text-foreground">
                {analitica?.horizonteTemporal || 'Periodo analizado'}
              </span>
            </CardDescription>
          </div>

          <div className="flex items-center gap-3 flex-wrap">
            {/* Ocupación Actual Pill */}
            <div className="hidden sm:flex items-center gap-2 px-3 py-1 rounded-lg bg-muted/60 border border-border/70 text-xs">
              <Users className="h-3.5 w-3.5 text-muted-foreground" />
              <span className="text-muted-foreground">Ocupación:</span>
              <span className="font-mono font-bold text-foreground">
                {ocupacionPct.toFixed(1)}%
              </span>
            </div>

            {/* Selector 6m / 12m */}
            <div className="flex items-center bg-muted p-0.5 rounded-lg border border-border">
              <button
                type="button"
                onClick={() => setMeses(6)}
                className={`px-2.5 py-1 text-xs font-medium rounded-md transition-all ${
                  meses === 6
                    ? 'bg-background text-foreground shadow-xs'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                6m
              </button>
              <button
                type="button"
                onClick={() => setMeses(12)}
                className={`px-2.5 py-1 text-xs font-medium rounded-md transition-all ${
                  meses === 12
                    ? 'bg-background text-foreground shadow-xs'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                12m
              </button>
            </div>

            {/* Botón Ver Tabla / Gráfica */}
            <Button
              variant="outline"
              size="sm"
              onClick={() => setShowTable(!showTable)}
              className="h-8 px-2.5 text-xs text-muted-foreground hover:text-foreground"
              title={showTable ? 'Ver vista de gráficas' : 'Ver datos en tabla'}
            >
              <TableIcon className="h-3.5 w-3.5 mr-1" />
              {showTable ? 'Gráfica' : 'Tabla'}
            </Button>

            {/* Refresco */}
            <Button
              variant="outline"
              size="sm"
              onClick={() => refetch()}
              className="h-8 px-2.5 text-xs"
              aria-label="Actualizar datos analíticos"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin text-primary' : ''}`} />
            </Button>
          </div>
        </div>
      </CardHeader>

      <CardContent className="pt-4 space-y-4">
        <Tabs defaultValue="financiero" className="w-full">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border/40 pb-3">
            <TabsList className="h-9 bg-muted/70 p-1">
              <TabsTrigger value="financiero" className="text-xs font-semibold gap-1.5 px-3">
                <DollarSign className="h-3.5 w-3.5" />
                Flujo Financiero
              </TabsTrigger>
              <TabsTrigger value="operativo" className="text-xs font-semibold gap-1.5 px-3">
                <Activity className="h-3.5 w-3.5" />
                Operación y Portería
              </TabsTrigger>
            </TabsList>

            {/* Mini resumen de la pestaña activa */}
            <div className="flex items-center gap-4 text-xs text-muted-foreground">
              <span className="sm:hidden flex items-center gap-1 font-mono">
                Ocupación: <strong className="text-foreground">{ocupacionPct.toFixed(1)}%</strong>
              </span>
            </div>
          </div>

          {/* TAB 1: FLUJO FINANCIERO */}
          <TabsContent value="financiero" className="space-y-4 mt-4">
            {/* Tarjetas resumen del periodo financiero */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
                <p className="text-[11px] text-muted-foreground font-medium">Facturado Acumulado</p>
                <p className="text-sm sm:text-base font-bold font-mono text-primary">
                  {formatCompactCOP(finTotals.facturado)}
                </p>
              </div>
              <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
                <p className="text-[11px] text-muted-foreground font-medium">Recaudo Efectivo</p>
                <p className="text-sm sm:text-base font-bold font-mono text-emerald-600 dark:text-emerald-400">
                  {formatCompactCOP(finTotals.recaudado)}
                </p>
              </div>
              <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
                <p className="text-[11px] text-muted-foreground font-medium">Gastos Registrados</p>
                <p className="text-sm sm:text-base font-bold font-mono text-rose-600 dark:text-rose-400">
                  {formatCompactCOP(finTotals.gastos)}
                </p>
              </div>
              <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
                <p className="text-[11px] text-muted-foreground font-medium">Eficiencia de Cobro</p>
                <div className="flex items-center gap-2">
                  <p className="text-sm sm:text-base font-bold font-mono text-foreground">
                    {tasaRecaudo}%
                  </p>
                  <Badge
                    variant="outline"
                    className={`text-[9px] px-1 py-0 font-bold ${
                      tasaRecaudo >= 80
                        ? 'text-emerald-600 border-emerald-500/30 bg-emerald-500/10'
                        : 'text-amber-600 border-amber-500/30 bg-amber-500/10'
                    }`}
                  >
                    {tasaRecaudo >= 80 ? 'Óptimo' : 'Atención'}
                  </Badge>
                </div>
              </div>
            </div>

            {/* Visualización Gráfica vs Tabla */}
            {finTrend.length === 0 ? (
              <div className="py-12 text-center text-xs text-muted-foreground">
                Sin movimientos financieros en la ventana temporal seleccionada.
              </div>
            ) : showTable ? (
              /* Vista Tabular Alternativa */
              <div className="overflow-x-auto border border-border/60 rounded-lg">
                <table className="w-full text-left text-xs">
                  <thead className="bg-muted/40 text-muted-foreground font-semibold border-b border-border/60">
                    <tr>
                      <th className="py-2.5 px-3">Periodo</th>
                      <th className="py-2.5 px-3 text-right">Facturado</th>
                      <th className="py-2.5 px-3 text-right">Recaudado</th>
                      <th className="py-2.5 px-3 text-right">Gastos</th>
                      <th className="py-2.5 px-3 text-right">Balance Neto</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/50">
                    {finTrend.map((row) => (
                      <tr key={row.periodo} className="hover:bg-muted/20 transition-colors">
                        <td className="py-2 px-3 font-mono font-medium text-foreground">
                          {row.periodo}
                        </td>
                        <td className="py-2 px-3 text-right font-mono text-primary">
                          {formatCOP(row.facturado)}
                        </td>
                        <td className="py-2 px-3 text-right font-mono text-emerald-600 dark:text-emerald-400">
                          {formatCOP(row.recaudado)}
                        </td>
                        <td className="py-2 px-3 text-right font-mono text-rose-600 dark:text-rose-400">
                          {formatCOP(row.gastos)}
                        </td>
                        <td
                          className={`py-2 px-3 text-right font-mono font-bold ${
                            Number(row.balanceNeto) >= 0
                              ? 'text-emerald-600 dark:text-emerald-400'
                              : 'text-rose-600 dark:text-rose-400'
                          }`}
                        >
                          {formatCOP(row.balanceNeto)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              /* shadcn UI Bar Chart */
              <div className="pt-2">
                <ChartContainer config={finChartConfig} className="h-[280px] w-full">
                  <BarChart
                    data={chartDataFin}
                    margin={{ top: 15, right: 10, left: 10, bottom: 0 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" vertical={false} className="stroke-border/50" />
                    <XAxis
                      dataKey="periodo"
                      stroke="currentColor"
                      className="text-[11px] text-muted-foreground"
                      tickLine={false}
                      axisLine={false}
                    />
                    <YAxis
                      stroke="currentColor"
                      className="text-[11px] text-muted-foreground"
                      tickLine={false}
                      axisLine={false}
                      tickFormatter={formatCompactCOP}
                    />
                    <ChartTooltip
                      content={
                        <ChartTooltipContent
                          formatter={(value, name) => [
                            formatCOP(value),
                            finChartConfig[name]?.label || name,
                          ]}
                        />
                      }
                    />
                    <ChartLegend content={<ChartLegendContent />} />
                    <Bar
                      dataKey="facturado"
                      fill="var(--color-facturado)"
                      radius={[4, 4, 0, 0]}
                    />
                    <Bar
                      dataKey="recaudado"
                      fill="var(--color-recaudado)"
                      radius={[4, 4, 0, 0]}
                    />
                    <Bar
                      dataKey="gastos"
                      fill="var(--color-gastos)"
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                </ChartContainer>
              </div>
            )}
          </TabsContent>

          {/* TAB 2: OPERACIÓN Y PORTERÍA */}
          <TabsContent value="operativo" className="space-y-4 mt-4">
            {/* Tarjetas resumen del periodo operativo */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
                <p className="text-[11px] text-muted-foreground font-medium">Visitas Atendidas</p>
                <p className="text-sm sm:text-base font-bold font-mono text-primary flex items-center gap-1.5">
                  <UserCheck className="h-4 w-4" />
                  {opTotals.visitas}
                </p>
              </div>
              <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
                <p className="text-[11px] text-muted-foreground font-medium">Paquetería Custodiada</p>
                <p className="text-sm sm:text-base font-bold font-mono text-amber-600 dark:text-amber-400 flex items-center gap-1.5">
                  <Package className="h-4 w-4" />
                  {opTotals.paquetes}
                </p>
              </div>
              <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
                <p className="text-[11px] text-muted-foreground font-medium">PQRS Radicadas</p>
                <p className="text-sm sm:text-base font-bold font-mono text-purple-600 dark:text-purple-400 flex items-center gap-1.5">
                  <Receipt className="h-4 w-4" />
                  {opTotals.pqrs}
                </p>
              </div>
              <div className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1">
                <p className="text-[11px] text-muted-foreground font-medium">SLA Promedio</p>
                <div className="flex items-center gap-2">
                  <p className="text-sm sm:text-base font-bold font-mono text-foreground">
                    {slaPromedio}%
                  </p>
                  <Badge
                    variant="outline"
                    className="text-[9px] px-1 py-0 font-bold text-emerald-600 border-emerald-500/30 bg-emerald-500/10"
                  >
                    <ShieldCheck className="h-2.5 w-2.5 mr-0.5" />
                    Cumplido
                  </Badge>
                </div>
              </div>
            </div>

            {/* Visualización Gráfica vs Tabla */}
            {opTrend.length === 0 ? (
              <div className="py-12 text-center text-xs text-muted-foreground">
                Sin registros operativos en el periodo analizado.
              </div>
            ) : showTable ? (
              <div className="overflow-x-auto border border-border/60 rounded-lg">
                <table className="w-full text-left text-xs">
                  <thead className="bg-muted/40 text-muted-foreground font-semibold border-b border-border/60">
                    <tr>
                      <th className="py-2.5 px-3">Periodo</th>
                      <th className="py-2.5 px-3 text-center">Visitas</th>
                      <th className="py-2.5 px-3 text-center">Paquetes</th>
                      <th className="py-2.5 px-3 text-center">PQRS</th>
                      <th className="py-2.5 px-3 text-center">SLA Resuelto</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/50">
                    {opTrend.map((row) => (
                      <tr key={row.periodo} className="hover:bg-muted/20 transition-colors">
                        <td className="py-2 px-3 font-mono font-medium text-foreground">
                          {row.periodo}
                        </td>
                        <td className="py-2 px-3 text-center font-mono">{row.totalVisitas}</td>
                        <td className="py-2 px-3 text-center font-mono">{row.totalPaquetes}</td>
                        <td className="py-2 px-3 text-center font-mono">{row.pqrsRadicadas}</td>
                        <td className="py-2 px-3 text-center">
                          <Badge
                            variant="outline"
                            className="font-mono text-[10px] bg-emerald-500/10 text-emerald-600 border-emerald-500/30"
                          >
                            {Number(row.pqrsResueltasEnSlaPct || 0).toFixed(1)}%
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              /* shadcn UI Area Chart */
              <div className="pt-2">
                <ChartContainer config={opChartConfig} className="h-[280px] w-full">
                  <AreaChart
                    data={chartDataOp}
                    margin={{ top: 15, right: 10, left: 10, bottom: 0 }}
                  >
                    <defs>
                      <linearGradient id="gradientVisitas" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="var(--color-visitas)" stopOpacity={0.35} />
                        <stop offset="95%" stopColor="var(--color-visitas)" stopOpacity={0.0} />
                      </linearGradient>
                      <linearGradient id="gradientPaquetes" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="var(--color-paquetes)" stopOpacity={0.35} />
                        <stop offset="95%" stopColor="var(--color-paquetes)" stopOpacity={0.0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} className="stroke-border/50" />
                    <XAxis
                      dataKey="periodo"
                      stroke="currentColor"
                      className="text-[11px] text-muted-foreground"
                      tickLine={false}
                      axisLine={false}
                    />
                    <YAxis
                      stroke="currentColor"
                      className="text-[11px] text-muted-foreground"
                      tickLine={false}
                      axisLine={false}
                    />
                    <ChartTooltip content={<ChartTooltipContent />} />
                    <ChartLegend content={<ChartLegendContent />} />
                    <Area
                      type="monotone"
                      dataKey="visitas"
                      stroke="var(--color-visitas)"
                      fillOpacity={1}
                      fill="url(#gradientVisitas)"
                      strokeWidth={2}
                    />
                    <Area
                      type="monotone"
                      dataKey="paquetes"
                      stroke="var(--color-paquetes)"
                      fillOpacity={1}
                      fill="url(#gradientPaquetes)"
                      strokeWidth={2}
                    />
                  </AreaChart>
                </ChartContainer>
              </div>
            )}
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  );
}
