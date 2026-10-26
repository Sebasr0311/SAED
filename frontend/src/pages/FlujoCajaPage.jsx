import { useState, useEffect, useCallback } from 'react';
import {
  Wallet,
  TrendingUp,
  TrendingDown,
  Clock,
  Calendar,
  BarChart3,
  RefreshCw,
  ArrowUpRight,
  ArrowDownRight,
  Receipt,
} from 'lucide-react';
import { toast } from 'sonner';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import { api } from '../lib/api.js';
import { formatCurrency, formatDate } from '../lib/utils.js';

export default function FlujoCajaPage() {
  const [resumen, setResumen] = useState(null);
  const [movimientos, setMovimientos] = useState([]);
  const [proyeccion, setProyeccion] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [tab, setTab] = useState('resumen');

  const cargarDatos = useCallback(async () => {
    try {
      setLoading(true);
      const [resumenRes, movRes, proyRes] = await Promise.all([
        api.get('/flujo-caja/resumen'),
        api.get('/flujo-caja/movimientos?limite=25'),
        api.get('/flujo-caja/proyeccion'),
      ]);
      setResumen(resumenRes?.data ?? resumenRes ?? {});
      setMovimientos(Array.isArray(movRes?.data) ? movRes.data : Array.isArray(movRes) ? movRes : []);
      setProyeccion(Array.isArray(proyRes?.data) ? proyRes.data : Array.isArray(proyRes) ? proyRes : []);
      setLoadError(null);
    } catch (e) {
      setLoadError(e);
      toast.error('No se pudo cargar la información de flujo de caja');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    cargarDatos();
  }, [cargarDatos]);

  const fmt = (v) => {
    const n = Number(v);
    if (isNaN(n)) return '$ 0';
    return formatCurrency(n);
  };

  const header = (
    <PageHeader
      title="Flujo de caja y proyección"
      subtitle="Ingresos confirmados, egresos ejecutados y liquidez estimada al cierre del periodo"
      action={
        <Button
          variant="outline"
          size="sm"
          onClick={cargarDatos}
          disabled={loading}
          icon={<RefreshCw className={loading ? 'h-3.5 w-3.5 animate-spin' : 'h-3.5 w-3.5'} />}
        >
          Actualizar balance
        </Button>
      }
    />
  );

  if (loading && !resumen) {
    return (
      <div className="space-y-6">
        {header}
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState size="lg" message="Calculando balances y proyecciones…" />
        </div>
      </div>
    );
  }

  // Without a successful load the figures would read as zero balances, so show the error instead.
  if (loadError && !resumen) {
    return (
      <div className="space-y-6">
        {header}
        <ErrorState
          title="No se pudo cargar el flujo de caja"
          message="Los saldos y proyecciones no están disponibles. Revise su conexión e intente de nuevo."
          onRetry={cargarDatos}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6 pb-12">
      {header}

      {loadError && (
        <ErrorState
          compact
          title="No se pudo actualizar el balance"
          message="Se muestran los últimos datos cargados."
          onRetry={cargarDatos}
        />
      )}

      <div className="space-y-3">
        <h2 className="text-[13px] font-semibold text-text-secondary">Realizado</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <MetricCard
            label="Saldo en bancos"
            value={fmt(resumen?.saldoActual)}
            subtitle="Saldo consolidado disponible"
            icon={Wallet}
            variant="primary"
          />
          <MetricCard
            label="Ingresos realizados"
            value={fmt(resumen?.totalIngresos)}
            subtitle="Cuotas y recaudos confirmados"
            icon={TrendingUp}
            variant="success"
          />
          <MetricCard
            label="Egresos ejecutados"
            value={fmt(resumen?.totalEgresos)}
            subtitle="Gastos operativos pagados"
            icon={TrendingDown}
            variant="danger"
          />
        </div>
        <h2 className="pt-2 text-[13px] font-semibold text-text-secondary">Proyectado</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <MetricCard
            label="Ingresos esperados"
            value={fmt(resumen?.ingresosEsperados)}
            subtitle="Cartera pendiente del mes"
            icon={Clock}
            variant="warning"
          />
          <MetricCard
            label="Gastos programados"
            value={fmt(resumen?.gastosProgramados)}
            subtitle="Facturas y servicios por pagar"
            icon={Receipt}
            variant="warning"
          />
          <MetricCard
            label="Proyección al cierre"
            value={fmt(resumen?.proyeccionSaldo)}
            subtitle="Liquidez estimada"
            icon={BarChart3}
            variant="info"
          />
        </div>
      </div>

      <Tabs value={tab} onValueChange={setTab}>
        <div className="-mx-1 overflow-x-auto px-1 pb-1">
          <TabsList>
            <TabsTrigger value="resumen" className="gap-1.5">
              <BarChart3 className="h-4 w-4" aria-hidden="true" />
              Resumen
            </TabsTrigger>
            <TabsTrigger value="movimientos" className="gap-1.5">
              <Receipt className="h-4 w-4" aria-hidden="true" />
              Movimientos recientes
            </TabsTrigger>
            <TabsTrigger value="proyeccion" className="gap-1.5">
              <Calendar className="h-4 w-4" aria-hidden="true" />
              Proyecciones pendientes
            </TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="resumen" className="mt-4">
          <section className="rounded-lg border border-border bg-surface shadow-sm">
            <div className="border-b border-border px-4 py-3.5 sm:px-5">
              <h2 className="text-base font-bold text-on-surface">Balance consolidado del periodo</h2>
              <p className="mt-0.5 text-xs text-text-muted">
                Calculado con el recaudo de cartera y la ejecución presupuestal
              </p>
            </div>
            <div className="grid grid-cols-1 divide-y divide-border-subtle md:grid-cols-2 md:divide-x md:divide-y-0">
              <div className="p-4 sm:p-5">
                <h3 className="mb-2 flex items-center gap-1.5 text-sm font-bold text-tint-green-fg">
                  <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
                  Entradas
                </h3>
                <dl>
                <BalanceLine label="Recaudo confirmado" value={fmt(resumen?.totalIngresos)} />
                <BalanceLine label="Recaudo pendiente estimado" value={fmt(resumen?.ingresosEsperados)} muted />
                </dl>
              </div>
              <div className="p-4 sm:p-5">
                <h3 className="mb-2 flex items-center gap-1.5 text-sm font-bold text-tint-red-fg">
                  <ArrowDownRight className="h-4 w-4" aria-hidden="true" />
                  Salidas
                </h3>
                <dl>
                <BalanceLine label="Egresos pagados" value={fmt(resumen?.totalEgresos)} />
                <BalanceLine label="Gastos comprometidos o pendientes" value={fmt(resumen?.gastosProgramados)} muted />
                </dl>
              </div>
            </div>
            <div className="flex flex-col gap-3 border-t border-border bg-surface-container px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
              <div>
                <p className="text-[13px] font-semibold text-on-surface">Fórmula de proyección</p>
                <p className="mt-0.5 text-xs text-text-secondary">
                  Saldo actual + ingresos esperados − gastos programados
                </p>
              </div>
              <div className="sm:text-right">
                <p className="text-xs text-text-secondary">Saldo proyectado al cierre</p>
                <p className="app-numeral mt-1 text-2xl text-on-surface">{fmt(resumen?.proyeccionSaldo)}</p>
              </div>
            </div>
          </section>
        </TabsContent>

        <TabsContent value="movimientos" className="mt-4">
          <section className="table-container">
            {movimientos.length === 0 ? (
              <EmptyState
                icon={Receipt}
                title="Sin movimientos recientes"
                subtitle="No se registran ingresos ni egresos en el periodo."
              />
            ) : (
              <div className="max-h-[70vh] overflow-auto">
                <table className="data-table">
                  <caption className="sr-only">Últimos 25 movimientos de caja</caption>
                  <thead>
                    <tr>
                      <th scope="col">Fecha</th>
                      <th scope="col">Tipo</th>
                      <th scope="col">Categoría</th>
                      <th scope="col">Descripción</th>
                      <th scope="col" className="text-right">Monto</th>
                      <th scope="col">Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {movimientos.map((m, i) => {
                      const isIngreso = m.tipo === 'INGRESO';
                      return (
                        <tr key={i}>
                          <td className="whitespace-nowrap tabular-nums text-text-secondary">{formatDate(m.fecha)}</td>
                          <td className="whitespace-nowrap">
                            <FlowBadge ingreso={isIngreso} label={m.tipo} />
                          </td>
                          <td className="font-medium">{m.categoria || 'Operativo'}</td>
                          <td className="max-w-xs truncate text-text-secondary">{m.descripcion || '—'}</td>
                          <td className="whitespace-nowrap text-right font-bold tabular-nums">
                            {isIngreso ? '+' : '−'}
                            {fmt(m.monto)}
                          </td>
                          <td>
                            <Badge variant={m.estado === 'PAGADO' || m.estado === 'CONFIRMADO' ? 'success' : 'secondary'}>
                              {m.estado || 'PROCESADO'}
                            </Badge>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </TabsContent>

        <TabsContent value="proyeccion" className="mt-4">
          <section className="table-container">
            {proyeccion.length === 0 ? (
              <EmptyState
                icon={Calendar}
                title="Sin proyecciones pendientes"
                subtitle="No hay cuotas ni obligaciones proyectadas por conciliar."
              />
            ) : (
              <div className="max-h-[70vh] overflow-auto">
                <table className="data-table">
                  <caption className="sr-only">Ingresos y egresos proyectados</caption>
                  <thead>
                    <tr>
                      <th scope="col">Fecha estimada</th>
                      <th scope="col">Naturaleza</th>
                      <th scope="col">Rubro</th>
                      <th scope="col">Concepto</th>
                      <th scope="col" className="text-right">Monto estimado</th>
                      <th scope="col">Unidad o destino</th>
                    </tr>
                  </thead>
                  <tbody>
                    {proyeccion.map((p, i) => {
                      const isIngreso = String(p.tipo || '').toUpperCase().includes('INGRESO');
                      return (
                        <tr key={i}>
                          <td className="whitespace-nowrap tabular-nums text-text-secondary">{formatDate(p.fecha)}</td>
                          <td className="whitespace-nowrap">
                            <FlowBadge ingreso={isIngreso} label={p.tipo} pending />
                          </td>
                          <td className="font-medium">{p.categoria || 'Presupuestal'}</td>
                          <td className="text-text-secondary">{p.descripcion || '—'}</td>
                          <td className="whitespace-nowrap text-right font-bold tabular-nums">
                            {isIngreso ? '+' : '−'}
                            {fmt(p.monto)}
                          </td>
                          <td className="text-text-secondary">{p.unidad || 'General'}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </TabsContent>
      </Tabs>
    </div>
  );
}

function BalanceLine({ label, value, muted = false }) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b border-border-subtle py-2 last:border-b-0">
      <dt className="text-sm text-text-secondary">{label}</dt>
      <dd className={muted ? 'text-sm font-semibold tabular-nums text-text-secondary' : 'text-sm font-bold tabular-nums text-on-surface'}>
        {value}
      </dd>
    </div>
  );
}

function FlowBadge({ ingreso, label, pending = false }) {
  const Icon = ingreso ? ArrowUpRight : ArrowDownRight;
  return (
    <Badge variant={ingreso ? 'success' : pending ? 'warning' : 'destructive'}>
      <Icon className="h-3 w-3" aria-hidden="true" />
      {label}
    </Badge>
  );
}
