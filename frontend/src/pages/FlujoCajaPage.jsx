import React, { useState, useEffect, useMemo } from 'react';
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
  CheckCircle2,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { api } from '../lib/api.js';
import { formatCurrency, formatDate } from '../lib/utils.js';
import { toast } from 'sonner';

export default function FlujoCajaPage() {
  const [resumen, setResumen] = useState(null);
  const [movimientos, setMovimientos] = useState([]);
  const [proyeccion, setProyeccion] = useState([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('resumen');

  const cargarDatos = async () => {
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
    } catch (e) {
      toast.error('No se pudo cargar la información de flujo de caja');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    cargarDatos();
  }, []);

  const fmt = (v) => {
    const n = Number(v);
    if (isNaN(n)) return '$ 0';
    return formatCurrency(n);
  };

  if (loading && !resumen) {
    return <LoadingState message="Calculando balances y proyecciones de tesorería..." />;
  }

  return (
    <div className="space-y-6 animate-fadeIn pb-12">
      {/* Header Principal */}
      <PageHeader
        title="Flujo de Caja y Proyección"
        subtitle="Control de tesorería en tiempo real, conciliación de ingresos, gastos ejecutados y liquidez estimada"
        action={
          <Button
            variant="outline"
            size="sm"
            onClick={cargarDatos}
            icon={<RefreshCw className="w-3.5 h-3.5" />}
          >
            Actualizar Balance
          </Button>
        }
      />

      {/* Métricas Ejecutivas */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
        <MetricCard
          label="Saldo en Bancos"
          value={fmt(resumen?.saldoActual)}
          subtitle="Saldo consolidado disponible"
          icon={<Wallet className="w-5 h-5 text-primary" />}
          variant="primary"
        />
        <MetricCard
          label="Ingresos Realizados"
          value={fmt(resumen?.totalIngresos)}
          subtitle="Cuotas y recaudos confirmados"
          icon={<TrendingUp className="w-5 h-5 text-emerald-500" />}
          variant="success"
        />
        <MetricCard
          label="Egresos Ejecutados"
          value={fmt(resumen?.totalEgresos)}
          subtitle="Gastos operativos pagados"
          icon={<TrendingDown className="w-5 h-5 text-rose-500" />}
          variant="danger"
        />
        <MetricCard
          label="Ingresos Esperados"
          value={fmt(resumen?.ingresosEsperados)}
          subtitle="Cartera pendiente del mes"
          icon={<Clock className="w-5 h-5 text-amber-500" />}
          variant="warning"
        />
        <MetricCard
          label="Gastos Programados"
          value={fmt(resumen?.gastosProgramados)}
          subtitle="Facturas y servicios por pagar"
          icon={<Receipt className="w-5 h-5 text-amber-500" />}
          variant="warning"
        />
        <MetricCard
          label="Proyección Final"
          value={fmt(resumen?.proyeccionSaldo)}
          subtitle="Liquidez estimada al cierre"
          icon={<BarChart3 className="w-5 h-5 text-sky-500" />}
          variant="info"
        />
      </div>

      {/* Tabs de Navegación */}
      <div className="flex border-b border-border gap-1 overflow-x-auto">
        {[
          { id: 'resumen', label: 'Resumen Ejecutivo', icon: BarChart3 },
          { id: 'movimientos', label: 'Movimientos Recientes', icon: Receipt },
          { id: 'proyeccion', label: 'Proyecciones Pendientes', icon: Calendar },
        ].map((t) => {
          const Icon = t.icon;
          const isActive = tab === t.id;
          return (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`flex items-center gap-2 px-4 py-2.5 text-xs sm:text-sm font-semibold transition-all border-b-2 -mb-px whitespace-nowrap ${
                isActive
                  ? 'border-primary text-primary bg-primary/5'
                  : 'border-transparent text-muted-foreground hover:text-foreground hover:bg-muted/40'
              }`}
            >
              <Icon className={`w-4 h-4 ${isActive ? 'text-primary' : 'text-muted-foreground'}`} />
              <span>{t.label}</span>
            </button>
          );
        })}
      </div>

      {/* Tab: Resumen */}
      {tab === 'resumen' && (
        <div className="bg-card border border-border rounded-xl shadow-sm p-6 space-y-6">
          <div>
            <h3 className="text-lg font-bold text-foreground">Balance Consolidado del Periodo</h3>
            <p className="text-xs text-muted-foreground mt-0.5">
              Cálculo automatizado basado en recaudo de cartera y ejecución presupuestal
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="bg-emerald-500/5 border border-emerald-500/20 rounded-xl p-5 space-y-3">
              <div className="flex items-center gap-2 text-emerald-600 dark:text-emerald-400 font-bold text-sm">
                <ArrowUpRight className="w-4 h-4" />
                <span>Flujo Positivo (Ingresos)</span>
              </div>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between items-center py-1.5 border-b border-emerald-500/10">
                  <span className="text-muted-foreground">Recaudo Confirmado:</span>
                  <span className="font-bold text-foreground">{fmt(resumen?.totalIngresos)}</span>
                </div>
                <div className="flex justify-between items-center py-1.5">
                  <span className="text-muted-foreground">Recaudo Pendiente Estimado:</span>
                  <span className="font-semibold text-amber-600 dark:text-amber-400">{fmt(resumen?.ingresosEsperados)}</span>
                </div>
              </div>
            </div>

            <div className="bg-rose-500/5 border border-rose-500/20 rounded-xl p-5 space-y-3">
              <div className="flex items-center gap-2 text-rose-600 dark:text-rose-400 font-bold text-sm">
                <ArrowDownRight className="w-4 h-4" />
                <span>Flujo Negativo (Egresos)</span>
              </div>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between items-center py-1.5 border-b border-rose-500/10">
                  <span className="text-muted-foreground">Egresos Pagados:</span>
                  <span className="font-bold text-foreground">{fmt(resumen?.totalEgresos)}</span>
                </div>
                <div className="flex justify-between items-center py-1.5">
                  <span className="text-muted-foreground">Gastos Comprometidos / Pendientes:</span>
                  <span className="font-semibold text-rose-600 dark:text-rose-400">{fmt(resumen?.gastosProgramados)}</span>
                </div>
              </div>
            </div>
          </div>

          <div className="bg-muted/30 border border-border p-5 rounded-xl flex flex-col sm:flex-row justify-between items-center gap-4">
            <div>
              <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block">
                Fórmula de Proyección
              </span>
              <p className="text-xs text-muted-foreground mt-0.5">
                Saldo Actual + Ingresos Estimados - Egresos Programados
              </p>
            </div>
            <div className="text-right">
              <span className="text-xs text-muted-foreground block">Saldo Proyectado al Cierre</span>
              <span className="text-2xl font-black text-primary tracking-tight">
                {fmt(resumen?.proyeccionSaldo)}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Tab: Movimientos Recientes */}
      {tab === 'movimientos' && (
        <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
          {movimientos.length === 0 ? (
            <div className="py-12">
              <EmptyState
                icon={<Receipt className="w-12 h-12 text-muted-foreground" />}
                title="Sin movimientos recientes"
                description="No se registran transacciones bancarias o pagos en el periodo seleccionado."
              />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border bg-muted/30 text-muted-foreground font-semibold text-xs uppercase tracking-wider">
                    <th className="py-3 px-4">Fecha</th>
                    <th className="py-3 px-4">Tipo</th>
                    <th className="py-3 px-4">Categoría</th>
                    <th className="py-3 px-4">Descripción</th>
                    <th className="py-3 px-4 text-right">Monto</th>
                    <th className="py-3 px-4 text-center">Estado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {movimientos.map((m, i) => {
                    const isIngreso = m.tipo === 'INGRESO';
                    return (
                      <tr key={i} className="hover:bg-muted/20 transition-colors">
                        <td className="py-3 px-4 whitespace-nowrap text-xs text-muted-foreground">
                          {formatDate(m.fecha)}
                        </td>
                        <td className="py-3 px-4 whitespace-nowrap">
                          <span
                            className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                              isIngreso
                                ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20'
                                : 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border-rose-500/20'
                            }`}
                          >
                            {isIngreso ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                            {m.tipo}
                          </span>
                        </td>
                        <td className="py-3 px-4 font-medium text-foreground">{m.categoria || 'Operativo'}</td>
                        <td className="py-3 px-4 text-muted-foreground max-w-xs truncate">{m.descripcion || '-'}</td>
                        <td
                          className={`py-3 px-4 text-right font-bold whitespace-nowrap ${
                            isIngreso ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'
                          }`}
                        >
                          {isIngreso ? '+' : '-'}{fmt(m.monto)}
                        </td>
                        <td className="py-3 px-4 text-center">
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
        </div>
      )}

      {/* Tab: Proyección */}
      {tab === 'proyeccion' && (
        <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
          {proyeccion.length === 0 ? (
            <div className="py-12">
              <EmptyState
                icon={<Calendar className="w-12 h-12 text-muted-foreground" />}
                title="Sin proyecciones pendientes"
                description="Todas las obligaciones y cuotas proyectadas han sido conciliadas."
              />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border bg-muted/30 text-muted-foreground font-semibold text-xs uppercase tracking-wider">
                    <th className="py-3 px-4">Fecha Estimada</th>
                    <th className="py-3 px-4">Naturaleza</th>
                    <th className="py-3 px-4">Rubro</th>
                    <th className="py-3 px-4">Concepto</th>
                    <th className="py-3 px-4 text-right">Monto Estimado</th>
                    <th className="py-3 px-4">Unidad / Destino</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {proyeccion.map((p, i) => {
                    const isIngreso = String(p.tipo || '').toUpperCase().includes('INGRESO');
                    return (
                      <tr key={i} className="hover:bg-muted/20 transition-colors">
                        <td className="py-3 px-4 whitespace-nowrap text-xs text-muted-foreground">
                          {formatDate(p.fecha)}
                        </td>
                        <td className="py-3 px-4 whitespace-nowrap">
                          <span
                            className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                              isIngreso
                                ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20'
                                : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20'
                            }`}
                          >
                            {isIngreso ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                            {p.tipo}
                          </span>
                        </td>
                        <td className="py-3 px-4 font-medium text-foreground">{p.categoria || 'Presupuestal'}</td>
                        <td className="py-3 px-4 text-muted-foreground">{p.descripcion || '-'}</td>
                        <td className="py-3 px-4 text-right font-bold text-foreground whitespace-nowrap">
                          {fmt(p.monto)}
                        </td>
                        <td className="py-3 px-4 text-muted-foreground">{p.unidad || 'General'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

