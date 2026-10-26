import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Activity,
  ArrowRight,
  BarChart3,
  BellRing,
  Building,
  Building2,
  CheckCircle2,
  ChevronRight,
  Clock,
  CreditCard,
  Database,
  History,
  RefreshCw,
  Server,
  ShieldCheck,
  UserCheck,
  Users,
  BadgeCheck,
} from 'lucide-react';
import api from '../lib/api.js';
import { formatDateTime } from '../lib/utils.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import { Table, TableHeader, TableBody, TableHead, TableRow, TableCell } from '../components/ui/table.tsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { KpiSummaryPanel } from '../components/KpiSummaryPanel.jsx';

const HEALTHY = ['UP', 'OK', 'OPERATIVO', 'ACTIVO', 'HEALTHY', 'DISPONIBLE'];

function healthVariant(estado) {
  if (!estado) return 'secondary';
  return HEALTHY.includes(String(estado).toUpperCase()) ? 'success' : 'warning';
}

const QUICK_LINKS = [
  { label: 'Organizaciones', icon: Building, ruta: '/superadmin/organizaciones', desc: 'Clientes de la plataforma' },
  { label: 'Propiedades', icon: Building2, ruta: '/superadmin/propiedades', desc: 'Copropiedades' },
  { label: 'Planes', icon: CreditCard, ruta: '/superadmin/planes', desc: 'Tarifas y cupos' },
  { label: 'Membresías', icon: BadgeCheck, ruta: '/superadmin/membresias', desc: 'Suscripciones' },
  { label: 'Onboarding', icon: UserCheck, ruta: '/superadmin/onboarding', desc: 'Solicitudes' },
  { label: 'Administradores', icon: ShieldCheck, ruta: '/superadmin/administradores', desc: 'Equipo SAED' },
  { label: 'Métricas globales', icon: BarChart3, ruta: '/superadmin/metricas', desc: 'Analítica de plataforma' },
];

function SectionTitle({ icon: Icon, title, description }) {
  return (
    <div className="flex min-w-0 items-start gap-2.5">
      <Icon className="mt-0.5 h-4 w-4 shrink-0 text-text-secondary" aria-hidden="true" />
      <div className="min-w-0">
        <CardTitle className="text-[15px]">{title}</CardTitle>
        {description && <p className="mt-0.5 text-xs text-text-muted">{description}</p>}
      </div>
    </div>
  );
}

export default function SuperAdminDashboardPage() {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get('/platform/dashboard');
      setData(res?.data || res || {});
    } catch (err) {
      console.error('Error cargando platform dashboard:', err);
      setError('No se pudo cargar el dashboard de plataforma.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const orgs = data?.organizaciones || { total: 0, activas: 0, inactivas: 0 };
  const props = data?.propiedades || { total: 0, activas: 0, inactivas: 0 };
  const users = data?.usuarios || { total: 0, activos: 0, inactivos: 0 };
  const plans = data?.planesMembresias || { planesDisponibles: 0, membresiasActivas: 0, porVencer: 0, vencidas: 0 };
  const salud = data?.saludTecnica || {};
  const alertas = Array.isArray(data?.alertas) ? data.alertas : [];
  const actividad = Array.isArray(data?.actividadReciente) ? data.actividadReciente : [];

  const servicios = [
    { key: 'api', icon: Server, label: 'API backend', detail: salud.api?.version || 'Sin medición registrada', estado: salud.api?.estado },
    { key: 'db', icon: Database, label: 'Base de datos Oracle', detail: 'Estado reportado por la consulta del tablero', estado: salud.database?.estado },
    { key: 'jobs', icon: Clock, label: 'Tareas programadas', detail: 'Sin monitoreo de tareas programadas', estado: salud.schedulers?.estado },
  ];
  const medidos = servicios.filter((s) => s.estado);
  const saludResumen =
    medidos.length === 0
      ? { label: 'Sin datos', variant: 'secondary' }
      : medidos.every((s) => healthVariant(s.estado) === 'success')
      ? { label: medidos.length === servicios.length ? 'Operativo' : 'Medición parcial', variant: medidos.length === servicios.length ? 'success' : 'secondary' }
      : { label: 'Revisar', variant: 'warning' };

  const header = (
    <PageHeader
      title="Dashboard operativo SAED"
      subtitle="Estado de la plataforma, alertas que requieren atención y salud de los servicios."
      action={
        <>
          <Button variant="outline" size="sm" onClick={loadData} disabled={loading} className="min-h-10 sm:min-h-9">
            <RefreshCw aria-hidden="true" />
            Actualizar
          </Button>
          <Button size="sm" onClick={() => navigate('/superadmin/metricas')} className="min-h-10 sm:min-h-9">
            <BarChart3 aria-hidden="true" />
            Ver métricas globales
          </Button>
        </>
      }
    />
  );

  if (loading) {
    return (
      <PageContainer>
        {header}
        <div role="status" aria-busy="true" aria-label="Cargando dashboard" className="space-y-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-28 rounded-lg" />
            ))}
          </div>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <Skeleton className="h-64 rounded-lg lg:col-span-2" />
            <Skeleton className="h-64 rounded-lg" />
          </div>
        </div>
      </PageContainer>
    );
  }

  if (error) {
    return (
      <PageContainer>
        {header}
        <ErrorState title="No se pudo cargar el dashboard" message={error} onRetry={loadData} />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      {header}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Organizaciones activas"
          value={orgs.activas}
          context={`De ${orgs.total} registradas · ${orgs.inactivas} inactivas`}
          icon={Building}
        />
        <MetricCard
          label="Propiedades activas"
          value={props.activas}
          context={`De ${props.total} en el catálogo global`}
          icon={Building2}
          variant="info"
        />
        <MetricCard
          label="Usuarios activos"
          value={users.activos}
          context={`De ${users.total} cuentas en el sistema`}
          icon={Users}
          variant="secondary"
        />
        <MetricCard
          label="Membresías activas"
          value={plans.membresiasActivas}
          context={`${plans.porVencer > 0 ? `${plans.porVencer} por vencer` : 'Ninguna por vencer'} · ${plans.planesDisponibles} planes`}
          icon={BadgeCheck}
          variant={plans.porVencer > 0 ? 'warning' : 'success'}
        />
      </div>

      <KpiSummaryPanel />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="flex flex-col lg:col-span-2">
          <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0 border-b border-border pb-3">
            <SectionTitle
              icon={BellRing}
              title="Alertas y atención requerida"
              description="Eventos del sistema que necesitan revisión o intervención administrativa."
            />
            <Badge variant={alertas.length > 0 ? 'destructive' : 'secondary'}>
              {alertas.length} {alertas.length === 1 ? 'alerta' : 'alertas'}
            </Badge>
          </CardHeader>
          <CardContent className="flex-1 pt-4">
            {alertas.length === 0 ? (
              <EmptyState
                icon={CheckCircle2}
                title="Sin alertas pendientes"
                subtitle="No hay situaciones críticas pendientes de atención en este momento."
              />
            ) : (
              <ul className="divide-y divide-border rounded-lg border border-border">
                {alertas.map((alerta) => {
                  const variant =
                    alerta.tipo === 'DANGER' ? 'destructive' : alerta.tipo === 'WARNING' ? 'warning' : 'info';
                  return (
                    <li
                      key={alerta.id}
                      className="flex flex-col justify-between gap-3 p-3.5 transition-colors duration-150 hover:bg-surface-container sm:flex-row sm:items-center"
                    >
                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge variant={variant}>{alerta.tipo || 'ALERTA'}</Badge>
                          <span className="text-sm font-semibold text-on-surface">{alerta.titulo}</span>
                        </div>
                        <p className="text-xs text-text-secondary">{alerta.mensaje}</p>
                      </div>
                      {alerta.ruta && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => navigate(alerta.ruta)}
                          className="h-10 shrink-0 self-start text-xs text-primary-text sm:h-8 sm:self-auto"
                        >
                          Atender
                          <ArrowRight aria-hidden="true" />
                        </Button>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card className="flex flex-col">
          <CardHeader className="flex flex-row items-start justify-between gap-3 space-y-0 border-b border-border pb-3">
            <SectionTitle icon={Activity} title="Salud técnica" description="Infraestructura y ejecución" />
            <Badge variant={saludResumen.variant}>{saludResumen.label}</Badge>
          </CardHeader>
          <CardContent className="flex-1 pt-4">
            <ul className="divide-y divide-border rounded-lg border border-border">
              {servicios.map((s) => (
                <li key={s.key} className="flex items-center justify-between gap-3 p-3">
                  <div className="flex min-w-0 items-center gap-3">
                    <s.icon className="h-4 w-4 shrink-0 text-text-secondary" aria-hidden="true" />
                    <div className="min-w-0">
                      <div className="text-[13px] font-semibold text-on-surface">{s.label}</div>
                      <div className="truncate text-xs text-text-muted">{s.detail}</div>
                    </div>
                  </div>
                  <Badge variant={healthVariant(s.estado)} className="shrink-0">
                    {s.estado || 'SIN DATOS'}
                  </Badge>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3 space-y-0 border-b border-border pb-3">
          <SectionTitle
            icon={History}
            title="Actividad reciente de plataforma"
            description="Últimos eventos administrativos registrados en la auditoría."
          />
          <Button
            variant="outline"
            size="sm"
            onClick={() => navigate('/superadmin/auditoria')}
            className="h-10 text-xs sm:h-8"
          >
            Ver auditoría completa
            <ArrowRight aria-hidden="true" />
          </Button>
        </CardHeader>
        <CardContent className="pt-2">
          {actividad.length === 0 ? (
            <EmptyState
              icon={History}
              title="Sin actividad reciente"
              subtitle="No hay eventos recientes registrados en el log de auditoría."
            />
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="text-xs">Fecha y hora</TableHead>
                    <TableHead className="text-xs">Acción</TableHead>
                    <TableHead className="text-xs">Entidad</TableHead>
                    <TableHead className="text-xs">Usuario</TableHead>
                    <TableHead className="text-xs">IP de origen</TableHead>
                    <TableHead className="text-right text-xs">Resultado</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {actividad.map((item, idx) => {
                    const isSuccess = !item.resultado || item.resultado === 'SUCCESS' || item.resultado === 'EXITO';
                    return (
                      <TableRow key={item.idLog || idx}>
                        <TableCell className="whitespace-nowrap text-xs tabular-nums text-text-secondary">
                          {item.fechaHora ? formatDateTime(item.fechaHora) : '—'}
                        </TableCell>
                        <TableCell className="text-xs font-semibold text-on-surface">{item.accion || 'OPERACION'}</TableCell>
                        <TableCell className="text-xs">
                          <Badge variant="outline">{item.entidad || 'GLOBAL'}</Badge>
                        </TableCell>
                        <TableCell className="text-xs font-medium text-on-surface">{item.usuario || 'Sistema'}</TableCell>
                        <TableCell className="font-mono text-xs text-text-secondary">{item.ip || '—'}</TableCell>
                        <TableCell className="text-right text-xs">
                          <Badge variant={isSuccess ? 'success' : 'destructive'}>{item.resultado || 'SUCCESS'}</Badge>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      <section aria-labelledby="sa-accesos" className="space-y-3">
        <h2 id="sa-accesos" className="text-[15px] font-bold text-on-surface">
          Accesos de gestión
        </h2>
        <ul className="grid grid-cols-1 overflow-hidden rounded-lg border border-border bg-surface sm:grid-cols-2 lg:grid-cols-4">
          {QUICK_LINKS.map((item) => (
            <li key={item.ruta} className="border-b border-border last:border-b-0 sm:border-r">
              <button
                type="button"
                onClick={() => navigate(item.ruta)}
                className="group flex min-h-12 w-full items-center gap-3 px-4 py-3 text-left transition-colors duration-150 hover:bg-surface-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
              >
                <item.icon className="h-4 w-4 shrink-0 text-text-secondary" aria-hidden="true" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[13px] font-semibold text-on-surface">{item.label}</span>
                  <span className="block truncate text-xs text-text-muted">{item.desc}</span>
                </span>
                <ChevronRight
                  className="h-4 w-4 shrink-0 text-text-muted transition-transform duration-150 ease-out group-hover:translate-x-0.5"
                  aria-hidden="true"
                />
              </button>
            </li>
          ))}
        </ul>
      </section>
    </PageContainer>
  );
}
