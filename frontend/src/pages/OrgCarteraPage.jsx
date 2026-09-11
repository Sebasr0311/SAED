import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/Button.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Wallet, AlertTriangle, TrendingUp, Search, RefreshCw, CheckCircle2 } from 'lucide-react';

export default function OrgCarteraPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [dashboardData, setDashboardData] = useState(null);
  const [carteraDetalle, setCarteraDetalle] = useState([]);
  const [search, setSearch] = useState('');

  async function loadData() {
    try {
      setLoading(true);
      setError(null);
      const [dashRes, carteraRes] = await Promise.all([
        api.get('/org/dashboard'),
        api.get('/org/dashboard/cartera-detalle').catch(() => ({ data: [] })),
      ]);
      setDashboardData(dashRes?.data || dashRes || {});
      const list = carteraRes?.data || carteraRes?.items || carteraRes || [];
      setCarteraDetalle(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error('Error cargando cartera consolidada:', err);
      setError('No se pudo cargar la información de cartera de la organización.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const formatCOP = (val) =>
    new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val || 0);

  const finanzas = dashboardData?.finanzas || { totalRecaudado: 0, carteraPendiente: 0 };
  const totalCarteraMora = useMemo(() => {
    return carteraDetalle.reduce((acc, row) => acc + (Number(row.CARTERA_MORA || row.cartera_mora) || 0), 0);
  }, [carteraDetalle]);

  const tasaRecaudo = useMemo(() => {
    const total = (finanzas.totalRecaudado || 0) + (finanzas.carteraPendiente || 0);
    if (total <= 0) return 100;
    return Math.round(((finanzas.totalRecaudado || 0) / total) * 1000) / 10;
  }, [finanzas]);

  const filtered = useMemo(() => {
    if (!search.trim()) return carteraDetalle;
    const q = search.toLowerCase();
    return carteraDetalle.filter(
      (r) =>
        (r.NOMBRE || r.nombre || '').toLowerCase().includes(q) ||
        (r.CIUDAD || r.ciudad || '').toLowerCase().includes(q)
    );
  }, [carteraDetalle, search]);

  if (loading) {
    return (
      <div className="p-6 space-y-6 animate-fadeIn">
        <Skeleton className="h-10 w-72" />
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
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
          <Button variant="outline" size="sm" onClick={loadData}>
            Reintentar
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-5">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Supervisión de Cartera Consolidada
            </h1>
            <Badge variant="outline" className="text-xs">
              GERENCIAL
            </Badge>
          </div>
          <p className="text-xs sm:text-sm text-muted-foreground mt-1">
            Control agregado de recaudo, cuentas por cobrar y morosidad de todas las propiedades de la organización.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={loadData} className="flex items-center gap-2">
            <RefreshCw className="h-4 w-4" />
            <span>Actualizar</span>
          </Button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="border-border/80 shadow-xs">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Cartera Pendiente</span>
              <Wallet className="h-4 w-4 text-primary" />
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold tracking-tight text-foreground">
              {formatCOP(finanzas.carteraPendiente)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">Total por cobrar en la cartera</p>
          </CardContent>
        </Card>

        <Card className="border-border/80 shadow-xs">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Recaudo Aprobado</span>
              <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold tracking-tight text-emerald-600 dark:text-emerald-400">
              {formatCOP(finanzas.totalRecaudado)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">Pagos aprobados registrados</p>
          </CardContent>
        </Card>

        <Card className="border-border/80 shadow-xs">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Cartera en Mora</span>
              <AlertTriangle className="h-4 w-4 text-rose-600 dark:text-rose-400" />
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold tracking-tight text-rose-600 dark:text-rose-400">
              {formatCOP(totalCarteraMora)}
            </div>
            <p className="text-xs text-muted-foreground mt-1">Saldos vencidos críticos</p>
          </CardContent>
        </Card>

        <Card className="border-border/80 shadow-xs">
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center justify-between">
              <span>Efectividad de Recaudo</span>
              <TrendingUp className="h-4 w-4 text-sky-600 dark:text-sky-400" />
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold tracking-tight text-sky-600 dark:text-sky-400">
              {tasaRecaudo}%
            </div>
            <p className="text-xs text-muted-foreground mt-1">Recaudo vs Cartera total</p>
          </CardContent>
        </Card>
      </div>

      {/* Tabla de Detalle por Propiedad */}
      <Card className="border-border/80 shadow-xs">
        <CardHeader className="pb-3 border-b border-border/60">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <CardTitle className="text-base font-bold">Estado de Cartera por Propiedad</CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                Desglose financiero consolidado por copropiedad administrada.
              </p>
            </div>

            <div className="relative w-full sm:w-64">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <input
                type="text"
                placeholder="Filtrar por propiedad o ciudad..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-border bg-background text-xs text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>
          </div>
        </CardHeader>
        <CardContent className="pt-4">
          <DataTable
            columns={[
              {
                key: 'nombre',
                label: 'Propiedad',
                render: (r) => (
                  <div>
                    <span className="font-semibold text-foreground text-sm">{r.NOMBRE || r.nombre}</span>
                    <span className="block text-xs text-muted-foreground">{r.CIUDAD || r.ciudad || 'Sin ciudad'}</span>
                  </div>
                ),
              },
              {
                key: 'unidades',
                label: 'Unidades',
                render: (r) => (
                  <Badge variant="outline" className="font-mono text-xs">
                    {r.TOTAL_UNIDADES || r.total_unidades || 0}
                  </Badge>
                ),
              },
              {
                key: 'recaudo',
                label: 'Recaudo Aprobado',
                render: (r) => (
                  <span className="font-mono font-medium text-xs text-emerald-600 dark:text-emerald-400">
                    {formatCOP(r.TOTAL_RECAUDADO || r.total_recaudado)}
                  </span>
                ),
              },
              {
                key: 'pendiente',
                label: 'Cartera Pendiente',
                render: (r) => (
                  <span className="font-mono font-medium text-xs text-foreground">
                    {formatCOP(r.CARTERA_PENDIENTE || r.cartera_pendiente)}
                  </span>
                ),
              },
              {
                key: 'mora',
                label: 'Cartera en Mora',
                render: (r) => {
                  const mora = Number(r.CARTERA_MORA || r.cartera_mora) || 0;
                  return (
                    <span
                      className={`font-mono font-medium text-xs ${
                        mora > 0 ? 'text-rose-600 dark:text-rose-400' : 'text-muted-foreground'
                      }`}
                    >
                      {formatCOP(mora)}
                    </span>
                  );
                },
              },
              {
                key: 'estado',
                label: 'Estado',
                render: (r) => (
                  <Badge variant={(r.ESTADO || r.estado) === 'ACTIVA' ? 'success' : 'secondary'} className="text-[10px]">
                    {r.ESTADO || r.estado || 'ACTIVA'}
                  </Badge>
                ),
              },
            ]}
            rows={filtered}
            pageSize={10}
            empty={{
              icon: 'account_balance_wallet',
              title: 'No hay datos de cartera',
              subtitle: search
                ? 'Ninguna propiedad coincide con la búsqueda.'
                : 'No se encontraron copropiedades con movimientos de cartera registrados.',
            }}
            keyField="id_propiedad"
          />
        </CardContent>
      </Card>
    </div>
  );
}
