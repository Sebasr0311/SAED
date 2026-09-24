import { useEffect, useState, useCallback } from 'react';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useTenant } from '../lib/TenantContext.jsx';
import { Card, CardHeader, CardTitle, CardContent } from './ui/card.tsx';
import { Badge } from './ui/badge.tsx';
import { Button } from './ui/Button.jsx';
import { Skeleton } from './ui/skeleton.tsx';
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
} from 'lucide-react';

export function PropertyAnaliticaSection() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();
  const [meses, setMeses] = useState(12);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [analitica, setAnalitica] = useState(null);
  const [activeTab, setActiveTab] = useState('financiero');

  const loadAnalitica = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await tenantApi.get(`/dashboard/propiedad/analitica?meses=${meses}`);
      const payload = res?.data?.data || res?.data || null;
      setAnalitica(payload);
    } catch (err) {
      console.error('Error cargando analítica de propiedad:', err);
      setError(
        err?.response?.data?.message ||
        'No se pudieron cargar las tendencias analíticas de la propiedad.'
      );
    } finally {
      setLoading(false);
    }
  }, [tenantApi, meses]);

  useEffect(() => {
    if (tenant.activeAssignmentId) {
      loadAnalitica();
    }
  }, [loadAnalitica, tenant.activeAssignmentId]);

  const formatCOP = (val) =>
    new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 0,
    }).format(Number(val) || 0);

  if (loading) {
    return (
      <div className="space-y-4 pt-4">
        <div className="flex justify-between items-center">
          <Skeleton className="h-6 w-56" />
          <Skeleton className="h-8 w-32" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Skeleton className="h-24 rounded-xl" />
          <Skeleton className="h-24 rounded-xl" />
          <Skeleton className="h-24 rounded-xl" />
        </div>
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-destructive/10 border border-destructive/30 text-destructive p-4 rounded-xl flex items-center justify-between mt-4">
        <div className="flex items-center gap-2">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          <span className="text-xs font-medium">{error}</span>
        </div>
        <Button variant="outline" size="sm" onClick={loadAnalitica}>
          Reintentar
        </Button>
      </div>
    );
  }

  const finTrend = analitica?.tendenciaFinanciera || [];
  const opTrend = analitica?.tendenciaOperativa || [];
  const ocupacionPct = Number(analitica?.ocupacionActualPct || 0);

  const maxFinMonto = Math.max(
    ...finTrend.flatMap((t) => [
      Number(t.facturado) || 0,
      Number(t.recaudado) || 0,
      Number(t.gastos) || 0,
    ]),
    1
  );

  return (
    <Card className="border-border/80 shadow-xs mt-6">
      <CardHeader className="pb-3 border-b border-border/60">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <CardTitle className="text-base font-bold flex items-center gap-2">
                <TrendingUp className="h-4 w-4 text-primary" />
                Tendencias y Analítica Operativa F11-05
              </CardTitle>
              <Badge variant="outline" className="text-[10px] bg-primary/10 text-primary border-primary/20">
                PROPIEDAD
              </Badge>
            </div>
            <p className="text-xs text-muted-foreground mt-1 flex items-center gap-2">
              <Calendar className="h-3 w-3" />
              <span>Horizonte:</span>
              <span className="font-mono font-medium text-foreground">
                {analitica?.horizonteTemporal || 'Periodo activo'}
              </span>
            </p>
          </div>

          <div className="flex items-center gap-2.5">
            {/* 6 / 12 meses selector */}
            <div className="flex items-center bg-muted p-0.5 rounded-lg border border-border">
              <button
                type="button"
                onClick={() => setMeses(6)}
                className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors ${
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
                className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors ${
                  meses === 12
                    ? 'bg-background text-foreground shadow-xs'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                12m
              </button>
            </div>

            <Button variant="outline" size="sm" onClick={loadAnalitica} className="h-8 px-2.5 text-xs">
              <RefreshCw className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>

        {/* Tabs de cambio de vista */}
        <div className="flex items-center justify-between pt-3">
          <div className="flex items-center gap-2 border-b border-border/40 w-full">
            <button
              type="button"
              onClick={() => setActiveTab('financiero')}
              className={`pb-2 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 ${
                activeTab === 'financiero'
                  ? 'border-primary text-primary'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              <DollarSign className="h-3.5 w-3.5" />
              Tendencia Financiera
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('operativo')}
              className={`pb-2 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 ${
                activeTab === 'operativo'
                  ? 'border-primary text-primary'
                  : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              <Activity className="h-3.5 w-3.5" />
              Tendencia Operativa
            </button>
          </div>

          {/* Ocupación Actual (KPI Atómico) */}
          <div className="flex items-center gap-2 shrink-0 pl-3">
            <Users className="h-3.5 w-3.5 text-muted-foreground" />
            <span className="text-xs text-muted-foreground">Ocupación Actual:</span>
            <span className="text-xs font-bold font-mono text-foreground">
              {ocupacionPct.toFixed(1)}%
            </span>
          </div>
        </div>
      </CardHeader>

      <CardContent className="pt-4">
        {activeTab === 'financiero' ? (
          <div className="space-y-4">
            <div className="flex items-center gap-4 text-[11px] justify-end">
              <div className="flex items-center gap-1">
                <span className="h-2 w-2 rounded-xs bg-primary inline-block" />
                <span className="text-muted-foreground">Facturado</span>
              </div>
              <div className="flex items-center gap-1">
                <span className="h-2 w-2 rounded-xs bg-emerald-500 inline-block" />
                <span className="text-muted-foreground">Recaudado</span>
              </div>
              <div className="flex items-center gap-1">
                <span className="h-2 w-2 rounded-xs bg-rose-500 inline-block" />
                <span className="text-muted-foreground">Gastos</span>
              </div>
            </div>

            {finTrend.length === 0 ? (
              <p className="text-xs text-muted-foreground text-center py-6">
                Sin movimientos financieros en la ventana seleccionada.
              </p>
            ) : (
              <div className="divide-y divide-border/50">
                {finTrend.map((f) => {
                  const fact = Number(f.facturado) || 0;
                  const rec = Number(f.recaudado) || 0;
                  const gas = Number(f.gastos) || 0;
                  const bal = Number(f.balanceNeto) || 0;

                  const pctFact = Math.max(Math.round((fact / maxFinMonto) * 100), fact > 0 ? 3 : 0);
                  const pctRec = Math.max(Math.round((rec / maxFinMonto) * 100), rec > 0 ? 3 : 0);
                  const pctGas = Math.max(Math.round((gas / maxFinMonto) * 100), gas > 0 ? 3 : 0);

                  return (
                    <div key={f.periodo} className="py-2.5 space-y-1.5">
                      <div className="flex justify-between items-center text-xs">
                        <span className="font-mono font-semibold text-foreground">{f.periodo}</span>
                        <div className="flex items-center gap-3 font-mono text-[11px]">
                          <span className="text-primary">Fact: {formatCOP(fact)}</span>
                          <span className="text-emerald-600 dark:text-emerald-400">
                            Rec: {formatCOP(rec)}
                          </span>
                          <span className="text-rose-600 dark:text-rose-400">
                            Gas: {formatCOP(gas)}
                          </span>
                          <span className="text-muted-foreground/40">|</span>
                          <span
                            className={`font-semibold ${
                              bal >= 0
                                ? 'text-emerald-600 dark:text-emerald-400'
                                : 'text-rose-600 dark:text-rose-400'
                            }`}
                          >
                            Bal: {formatCOP(bal)}
                          </span>
                        </div>
                      </div>

                      <div className="space-y-0.5">
                        <div className="w-full bg-muted/60 rounded-full h-1.5 overflow-hidden">
                          <div
                            className="bg-primary h-1.5 rounded-full"
                            style={{ width: `${pctFact}%` }}
                            title={`Facturado: ${formatCOP(fact)}`}
                          />
                        </div>
                        <div className="w-full bg-muted/60 rounded-full h-1.5 overflow-hidden">
                          <div
                            className="bg-emerald-500 h-1.5 rounded-full"
                            style={{ width: `${pctRec}%` }}
                            title={`Recaudado: ${formatCOP(rec)}`}
                          />
                        </div>
                        <div className="w-full bg-muted/60 rounded-full h-1.5 overflow-hidden">
                          <div
                            className="bg-rose-500 h-1.5 rounded-full"
                            style={{ width: `${pctGas}%` }}
                            title={`Gastos: ${formatCOP(gas)}`}
                          />
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        ) : (
          <div className="space-y-3">
            {opTrend.length === 0 ? (
              <p className="text-xs text-muted-foreground text-center py-6">
                Sin movimientos operativos en la ventana seleccionada.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-border bg-muted/30 text-muted-foreground font-semibold">
                      <th className="py-2 px-3">Periodo</th>
                      <th className="py-2 px-3 text-center">Visitas</th>
                      <th className="py-2 px-3 text-center">Paquetes</th>
                      <th className="py-2 px-3 text-center">PQRS Radicadas</th>
                      <th className="py-2 px-3 text-center">SLA Cumplido</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {opTrend.map((o) => {
                      const slaPct = Number(o.pqrsResueltasEnSlaPct || 0);
                      return (
                        <tr key={o.periodo} className="hover:bg-muted/30 transition-colors">
                          <td className="py-2.5 px-3 font-mono font-medium text-foreground">
                            {o.periodo}
                          </td>
                          <td className="py-2.5 px-3 text-center font-mono">
                            <span className="inline-flex items-center gap-1">
                              <UserCheck className="h-3 w-3 text-primary" />
                              {o.totalVisitas}
                            </span>
                          </td>
                          <td className="py-2.5 px-3 text-center font-mono">
                            <span className="inline-flex items-center gap-1">
                              <Package className="h-3 w-3 text-amber-500" />
                              {o.totalPaquetes}
                            </span>
                          </td>
                          <td className="py-2.5 px-3 text-center font-mono">
                            <span className="inline-flex items-center gap-1">
                              <Receipt className="h-3 w-3 text-muted-foreground" />
                              {o.pqrsRadicadas}
                            </span>
                          </td>
                          <td className="py-2.5 px-3 text-center">
                            <Badge
                              variant="outline"
                              className={`font-mono text-[10px] ${
                                slaPct >= 80
                                  ? 'bg-emerald-500/10 text-emerald-600 border-emerald-500/30'
                                  : slaPct >= 50
                                  ? 'bg-amber-500/10 text-amber-600 border-amber-500/30'
                                  : 'bg-rose-500/10 text-rose-600 border-rose-500/30'
                              }`}
                            >
                              <ShieldCheck className="h-2.5 w-2.5 mr-1" />
                              {slaPct.toFixed(1)}%
                            </Badge>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
