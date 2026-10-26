import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../lib/api.js';
import { cn } from '../lib/utils.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { Building2, Home, Users, DollarSign, ArrowUpRight, Plus } from 'lucide-react';
import { KpiSummaryPanel } from '../components/KpiSummaryPanel.jsx';

/** Plan consumption meter: Índigo fill, amber from 80 %, danger at the limit. */
function UsageMeter({ label, used, limit, percent }) {
  const pct = Math.max(0, Math.min(100, Number(percent) || 0));
  const tone = pct >= 100 ? 'bg-btn-danger' : pct >= 80 ? 'bg-btn-warn' : 'bg-btn-primary';
  return (
    <div className="space-y-2">
      <div className="flex items-baseline justify-between gap-2 text-xs">
        <span className="font-semibold text-on-surface">{label}</span>
        <span className="tabular-nums text-text-secondary">
          {used || 0} / {limit || '∞'}
        </span>
      </div>
      <div
        className="h-2 w-full overflow-hidden rounded-[2px] bg-surface-dim"
        role="progressbar"
        aria-label={`${label}: ${pct}% utilizado`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={pct}
      >
        <div className={cn('h-full transition-[width] duration-300 ease-out', tone)} style={{ width: `${pct}%` }} />
      </div>
      <p className={cn('text-right text-xs tabular-nums', pct >= 80 ? 'font-semibold text-tint-amber-fg' : 'text-text-muted')}>
        {pct}% utilizado
      </p>
    </div>
  );
}

export default function OrgDashboardPage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get('/org/dashboard');
      setData(res?.data || res || {});
    } catch (err) {
      console.error('Error loading org dashboard:', err);
      setError('No se pudo cargar el dashboard organizacional.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const props = data?.propiedades || { total: 0, activas: 0, inactivas: 0 };
  const units = data?.unidades || { total: 0 };
  const admins = data?.administradores || { activos: 0 };
  const users = data?.usuarios || { total: 0 };
  const sub = data?.suscripcion || {};
  const finanzas = data?.finanzas || { totalRecaudado: 0, carteraPendiente: 0 };
  const recientes = data?.propiedadesRecientes || [];

  const formatCOP = (val) =>
    new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val || 0);

  const header = (
    <PageHeader
      title="Consola de organización"
      subtitle="Supervisión de sus propiedades, límites de suscripción y cartera agregada."
      badge={!loading && !error && sub?.planNombre ? <Badge variant="info">{sub.planNombre}</Badge> : null}
      action={
        <Button asChild>
          <Link to="/org/propiedades">
            <Home aria-hidden="true" />
            Gestionar propiedades
          </Link>
        </Button>
      }
    />
  );

  if (loading) {
    return (
      <PageContainer>
        {header}
        <div role="status" aria-busy="true" aria-label="Cargando consola" className="space-y-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-28 rounded-lg" />
            ))}
          </div>
          <Skeleton className="h-40 w-full rounded-lg" />
          <Skeleton className="h-64 w-full rounded-lg" />
        </div>
      </PageContainer>
    );
  }

  if (error) {
    return (
      <PageContainer>
        {header}
        <ErrorState title="No se pudo cargar la consola" message={error} onRetry={load} />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      {header}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Propiedades activas"
          value={props.activas}
          context={`${props.total} registradas · ${sub.porcentajePropiedades || 0}% del límite`}
          icon={Home}
        />
        <MetricCard
          label="Unidades totales"
          value={units.total}
          context={`En todas las copropiedades · ${sub.porcentajeUnidades || 0}% del límite`}
          icon={Building2}
          variant="info"
        />
        <MetricCard
          label="Administradores"
          value={admins.activos}
          context={`${users.total} usuarios en la organización`}
          icon={Users}
          variant="secondary"
        />
        <MetricCard
          label="Recaudo consolidado"
          value={formatCOP(finanzas.totalRecaudado)}
          context={`Por cobrar: ${formatCOP(finanzas.carteraPendiente)}`}
          icon={DollarSign}
          variant="success"
        />
      </div>

      <KpiSummaryPanel />

      <Card>
        <CardHeader className="border-b border-border pb-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <CardTitle className="text-[15px]">Consumo de límites del plan</CardTitle>
              <p className="mt-0.5 text-xs text-text-muted">
                Capacidad contratada según el plan {sub.planNombre || 'vigente'}.
              </p>
            </div>
            <Button asChild variant="ghost" size="sm" className="min-h-10 text-primary-text sm:min-h-8">
              <Link to="/org/plan">
                Ver plan completo
                <ArrowUpRight aria-hidden="true" />
              </Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent className="pt-6">
          <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
            <UsageMeter
              label="Propiedades"
              used={sub.consumoPropiedades}
              limit={sub.limitePropiedades}
              percent={sub.porcentajePropiedades}
            />
            <UsageMeter
              label="Unidades residenciales"
              used={sub.consumoUnidades}
              limit={sub.limiteUnidades}
              percent={sub.porcentajeUnidades}
            />
            <UsageMeter
              label="Usuarios de plataforma"
              used={sub.consumoUsuarios}
              limit={sub.limiteUsuarios}
              percent={sub.porcentajeUsuarios}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="border-b border-border pb-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <CardTitle className="text-[15px]">Propiedades de la organización</CardTitle>
              <p className="mt-0.5 text-xs text-text-muted">Últimas copropiedades registradas.</p>
            </div>
            <Button asChild variant="outline" size="sm" className="min-h-10 sm:min-h-8">
              <Link to="/org/propiedades">Ver todas ({props.total})</Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {recientes.length === 0 ? (
            <EmptyState
              icon={Home}
              title="No hay propiedades registradas aún"
              subtitle="Registra la primera copropiedad para empezar a administrarla desde la organización."
            >
              <Button asChild size="sm" className="mt-3">
                <Link to="/org/propiedades">
                  <Plus aria-hidden="true" />
                  Registrar propiedad
                </Link>
              </Button>
            </EmptyState>
          ) : (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Copropiedad</th>
                    <th scope="col">Ubicación</th>
                    <th scope="col">Tipo</th>
                    <th scope="col" className="text-right">Unidades</th>
                    <th scope="col" className="text-right">Administradores</th>
                    <th scope="col" className="text-center">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {recientes.map((p) => (
                    <tr key={p.ID_PROPIEDAD}>
                      <td className="font-mono text-xs font-semibold text-text-secondary tabular-nums">#{p.ID_PROPIEDAD}</td>
                      <td className="min-w-[10rem] font-semibold text-on-surface">{p.NOMBRE}</td>
                      <td className="min-w-[10rem] text-xs text-text-secondary">
                        {p.DIRECCION || 'Sin dirección'}, {p.CIUDAD || 'N/A'}
                      </td>
                      <td>
                        <Badge variant="outline">{p.TIPO_PROPIEDAD_NOMBRE || 'Edificio'}</Badge>
                      </td>
                      <td className="app-numeral text-right text-sm">{p.TOTAL_UNIDADES || 0}</td>
                      <td className="app-numeral text-right text-sm">{p.TOTAL_ADMINS || 0}</td>
                      <td className="text-center">
                        <Badge variant={(p.ESTADO || 'ACTIVA') === 'ACTIVA' ? 'success' : 'secondary'}>
                          {(p.ESTADO || 'ACTIVA') === 'ACTIVA' ? 'Activa' : p.ESTADO}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </PageContainer>
  );
}
