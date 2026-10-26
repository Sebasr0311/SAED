import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/Button.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { FileText, Printer, RefreshCw, Building, Users, Wallet } from 'lucide-react';

export default function OrgReportesPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [data, setData] = useState(null);

  async function loadReport() {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get('/org/dashboard');
      setData(res?.data || res || {});
    } catch (err) {
      console.error('Error cargando informe de organización:', err);
      setError('No se pudo generar el reporte consolidado.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadReport();
  }, []);

  const formatCOP = (val) =>
    new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val || 0);

  const header = (
    <PageHeader
      title="Reportes gerenciales consolidados"
      subtitle="Resumen consolidado de infraestructura, administración, licenciamiento y finanzas de la organización."
      action={
        <>
          <Button variant="outline" size="sm" onClick={() => window.print()} disabled={loading || !!error}>
            <Printer className="h-4 w-4" aria-hidden="true" />
            Imprimir informe
          </Button>
          <Button variant="outline" size="sm" onClick={loadReport} loading={loading}>
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
        <div role="status" aria-busy="true" aria-label="Cargando reporte" className="space-y-6">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-28 rounded-lg" />
            ))}
          </div>
          <Skeleton className="h-80 w-full rounded-lg" />
        </div>
      </PageContainer>
    );
  }

  if (error) {
    return (
      <PageContainer>
        {header}
        <ErrorState title="No se pudo cargar el reporte" message={error} onRetry={loadReport} />
      </PageContainer>
    );
  }

  const props = data?.propiedades || { total: 0, activas: 0, inactivas: 0 };
  const units = data?.unidades || { total: 0 };
  const admins = data?.administradores || { activos: 0 };
  const finanzas = data?.finanzas || { totalRecaudado: 0, carteraPendiente: 0 };
  const recientes = data?.propiedadesRecientes || [];
  const sub = data?.suscripcion || {};

  return (
    <PageContainer>
      {header}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <MetricCard
          label="Infraestructura gestionada"
          value={`${props.total} propiedades`}
          context={`${props.activas} activas · ${units.total} unidades`}
          icon={Building}
        />
        <MetricCard
          label="Administradores y licencia"
          value={`${admins.activos} administradores`}
          context={`Plan: ${sub.planNombre || 'sin plan registrado'}`}
          icon={Users}
          variant="info"
        />
        <MetricCard
          label="Recaudo agregado"
          value={formatCOP(finanzas.totalRecaudado)}
          context={`Cartera por cobrar: ${formatCOP(finanzas.carteraPendiente)}`}
          icon={Wallet}
          variant="success"
        />
      </div>

      <Card>
        <CardHeader className="border-b border-border pb-3">
          <CardTitle className="flex items-center gap-2 text-[15px]">
            <FileText className="h-4 w-4 text-text-secondary" aria-hidden="true" />
            Padrón de copropiedades de la organización
          </CardTitle>
        </CardHeader>
        <CardContent className="pt-4">
          <DataTable
            columns={[
              {
                key: 'nombre',
                label: 'Copropiedad',
                render: (r) => (
                  <div className="min-w-[10rem]">
                    <span className="text-sm font-semibold text-on-surface">{r.NOMBRE || r.nombre}</span>
                    <span className="block text-xs text-text-muted">{r.DIRECCION || r.direccion || ''}</span>
                  </div>
                ),
              },
              {
                key: 'ciudad',
                label: 'Ciudad',
                render: (r) => <span className="text-xs text-on-surface">{r.CIUDAD || r.ciudad || '—'}</span>,
              },
              {
                key: 'unidades',
                label: 'Unidades',
                render: (r) => (
                  <span className="app-numeral text-sm">{r.TOTAL_UNIDADES || r.total_unidades || 0}</span>
                ),
              },
              {
                key: 'admins',
                label: 'Administradores',
                render: (r) => <span className="app-numeral text-sm">{r.TOTAL_ADMINS || r.total_admins || 0}</span>,
              },
              {
                key: 'estado',
                label: 'Estado',
                render: (r) => (
                  <Badge variant={(r.ESTADO || r.estado || 'ACTIVA') === 'ACTIVA' ? 'success' : 'secondary'}>
                    {(r.ESTADO || r.estado || 'ACTIVA') === 'ACTIVA' ? 'Activa' : r.ESTADO || r.estado}
                  </Badge>
                ),
              },
            ]}
            rows={recientes}
            pageSize={10}
            empty={{
              icon: Building,
              title: 'No hay propiedades registradas',
              subtitle: 'Las propiedades dadas de alta en la organización se listarán aquí.',
            }}
            keyField="id_propiedad"
          />
        </CardContent>
      </Card>
    </PageContainer>
  );
}
