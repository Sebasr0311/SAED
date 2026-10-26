import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/Button.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Input } from '../components/ui/input.tsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { Wallet, AlertTriangle, TrendingUp, Search, RefreshCw, CheckCircle2 } from 'lucide-react';

export default function OrgCarteraPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [dashboardData, setDashboardData] = useState(null);
  const [carteraDetalle, setCarteraDetalle] = useState([]);
  const [search, setSearch] = useState('');
  const [detalleError, setDetalleError] = useState(false);

  async function loadData() {
    try {
      setLoading(true);
      setError(null);
      setDetalleError(false);
      const [dashRes, carteraRes] = await Promise.all([
        api.get('/org/dashboard'),
        api.get('/org/dashboard/cartera-detalle').catch((err) => {
          // The detail is optional, but its failure must be visible instead of reading as "no data".
          console.error('Error cargando detalle de cartera:', err);
          setDetalleError(true);
          return { data: [] };
        }),
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

  const header = (
    <PageHeader
      title="Supervisión de cartera consolidada"
      subtitle="Recaudo, cuentas por cobrar y morosidad agregados de todas las propiedades de la organización."
      action={
        <Button variant="outline" size="sm" onClick={loadData} loading={loading}>
          {!loading && <RefreshCw className="h-4 w-4" aria-hidden="true" />}
          Actualizar
        </Button>
      }
    />
  );

  if (loading) {
    return (
      <PageContainer>
        {header}
        <div role="status" aria-busy="true" aria-label="Cargando cartera" className="space-y-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-28 rounded-lg" />
            ))}
          </div>
          <Skeleton className="h-96 w-full rounded-lg" />
        </div>
      </PageContainer>
    );
  }

  if (error) {
    return (
      <PageContainer>
        {header}
        <ErrorState title="No se pudo cargar la cartera" message={error} onRetry={loadData} />
      </PageContainer>
    );
  }

  const hayBaseRecaudo = (finanzas.totalRecaudado || 0) + (finanzas.carteraPendiente || 0) > 0;

  return (
    <PageContainer>
      {header}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Cartera pendiente"
          value={formatCOP(finanzas.carteraPendiente)}
          context="Total por cobrar"
          icon={Wallet}
        />
        <MetricCard
          label="Recaudo aprobado"
          value={formatCOP(finanzas.totalRecaudado)}
          context="Pagos aprobados registrados"
          icon={CheckCircle2}
          variant="success"
        />
        <MetricCard
          label="Cartera en mora"
          value={formatCOP(totalCarteraMora)}
          context="Saldos vencidos por propiedad"
          icon={AlertTriangle}
          variant={totalCarteraMora > 0 ? 'danger' : 'secondary'}
        />
        <MetricCard
          label="Efectividad de recaudo"
          value={hayBaseRecaudo ? `${tasaRecaudo}%` : '—'}
          context={hayBaseRecaudo ? 'Recaudo frente a cartera total' : 'Sin movimientos registrados'}
          icon={TrendingUp}
          variant="info"
        />
      </div>

      <Card>
        <CardHeader className="border-b border-border pb-3">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle className="text-[15px]">Estado de cartera por propiedad</CardTitle>
              <p className="mt-0.5 text-xs text-text-muted">Desglose consolidado por copropiedad administrada.</p>
            </div>

            <div className="relative w-full sm:w-64">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
                aria-hidden="true"
              />
              <Input
                type="search"
                aria-label="Filtrar por propiedad o ciudad"
                placeholder="Filtrar por propiedad o ciudad..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="h-10 pl-9 text-sm"
              />
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-3 pt-4">
          {detalleError && (
            <ErrorState
              compact
              title="No se pudo cargar el detalle por propiedad"
              message="Los totales superiores sí están actualizados. Reintenta para ver el desglose."
              onRetry={loadData}
            />
          )}
          <DataTable
            columns={[
              {
                key: 'nombre',
                label: 'Propiedad',
                render: (r) => (
                  <div className="min-w-[10rem]">
                    <span className="text-sm font-semibold text-on-surface">{r.NOMBRE || r.nombre}</span>
                    <span className="block text-xs text-text-muted">{r.CIUDAD || r.ciudad || 'Sin ciudad'}</span>
                  </div>
                ),
              },
              {
                key: 'unidades',
                label: 'Unidades',
                render: (r) => <span className="app-numeral text-sm">{r.TOTAL_UNIDADES || r.total_unidades || 0}</span>,
              },
              {
                key: 'recaudo',
                label: 'Recaudo aprobado',
                render: (r) => (
                  <span className="whitespace-nowrap text-sm font-semibold tabular-nums text-tint-green-fg">
                    {formatCOP(r.TOTAL_RECAUDADO || r.total_recaudado)}
                  </span>
                ),
              },
              {
                key: 'pendiente',
                label: 'Cartera pendiente',
                render: (r) => (
                  <span className="whitespace-nowrap text-sm tabular-nums text-on-surface">
                    {formatCOP(r.CARTERA_PENDIENTE || r.cartera_pendiente)}
                  </span>
                ),
              },
              {
                key: 'mora',
                label: 'Cartera en mora',
                render: (r) => {
                  const mora = Number(r.CARTERA_MORA || r.cartera_mora) || 0;
                  return (
                    <span
                      className={`whitespace-nowrap text-sm tabular-nums ${
                        mora > 0 ? 'font-semibold text-destructive' : 'text-text-muted'
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
                render: (r) => {
                  const estado = r.ESTADO || r.estado || 'ACTIVA';
                  return (
                    <Badge variant={estado === 'ACTIVA' ? 'success' : 'secondary'}>
                      {estado === 'ACTIVA' ? 'Activa' : estado}
                    </Badge>
                  );
                },
              },
            ]}
            rows={filtered}
            pageSize={10}
            empty={{
              icon: Wallet,
              title: search ? 'Sin coincidencias' : 'No hay datos de cartera',
              subtitle: search
                ? 'Ninguna propiedad coincide con la búsqueda.'
                : 'No se encontraron copropiedades con movimientos de cartera registrados.',
            }}
            keyField="id_propiedad"
          />
        </CardContent>
      </Card>
    </PageContainer>
  );
}
