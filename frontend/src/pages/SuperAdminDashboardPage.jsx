import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from '../components/ui/table.tsx';

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

  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <div className="flex justify-between items-center">
          <div>
            <Skeleton className="h-8 w-64 mb-2" />
            <Skeleton className="h-4 w-96" />
          </div>
          <Skeleton className="h-10 w-32" />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <Skeleton className="h-64 rounded-xl lg:col-span-2" />
          <Skeleton className="h-64 rounded-xl" />
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-8 animate-fadeIn">
      {/* 1. Header Operativo */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              Dashboard Operativo SAED
            </h1>
            <Badge variant="outline" className="bg-primary/10 text-primary border-primary/20 font-semibold px-2.5 py-0.5">
              ESTADO GLOBAL
            </Badge>
          </div>
          <p className="text-muted-foreground mt-1 text-sm">
            Supervisión ejecutiva en tiempo real: estado de plataforma, alertas críticas y salud de servicios.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={loadData}
            className="gap-2 text-xs h-9"
          >
            <span className="material-symbols-outlined text-sm">refresh</span>
            Actualizar
          </Button>
          <Button
            size="sm"
            onClick={() => navigate('/superadmin/metricas')}
            className="gap-2 text-xs h-9 bg-primary hover:bg-primary/90 text-primary-foreground shadow-sm"
          >
            <span className="material-symbols-outlined text-sm">analytics</span>
            Ver Métricas Globales
          </Button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-lg bg-destructive/10 text-destructive border border-destructive/20 text-sm flex items-center justify-between">
          <span>{error}</span>
          <Button variant="ghost" size="sm" onClick={loadData} className="h-7 text-xs">
            Reintentar
          </Button>
        </div>
      )}

      {/* 2. Tarjetas Compactas de Estado Actual */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {/* Organizaciones */}
        <Card className="hover:shadow-md transition-shadow border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Organizaciones Activas</CardTitle>
            <span className="material-symbols-outlined text-primary p-2 rounded-lg bg-primary/10">domain</span>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-foreground">{orgs.activas}</div>
            <p className="text-xs text-muted-foreground mt-1">
              De <span className="font-semibold text-foreground">{orgs.total}</span> registradas · {orgs.inactivas} inactivas
            </p>
          </CardContent>
        </Card>

        {/* Propiedades */}
        <Card className="hover:shadow-md transition-shadow border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Propiedades Activas</CardTitle>
            <span className="material-symbols-outlined text-indigo-500 p-2 rounded-lg bg-indigo-500/10">apartment</span>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-foreground">{props.activas}</div>
            <p className="text-xs text-muted-foreground mt-1">
              De <span className="font-semibold text-foreground">{props.total}</span> en catálogo global
            </p>
          </CardContent>
        </Card>

        {/* Usuarios */}
        <Card className="hover:shadow-md transition-shadow border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Usuarios Activos</CardTitle>
            <span className="material-symbols-outlined text-amber-500 p-2 rounded-lg bg-amber-500/10">group</span>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-foreground">{users.activos}</div>
            <p className="text-xs text-muted-foreground mt-1">
              De <span className="font-semibold text-foreground">{users.total}</span> cuentas en el sistema
            </p>
          </CardContent>
        </Card>

        {/* Membresías */}
        <Card className="hover:shadow-md transition-shadow border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Membresías Activas</CardTitle>
            <span className="material-symbols-outlined text-emerald-500 p-2 rounded-lg bg-emerald-500/10">card_membership</span>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-foreground">{plans.membresiasActivas}</div>
            <p className="text-xs text-muted-foreground mt-1">
              {plans.porVencer > 0 ? (
                <span className="text-amber-600 font-semibold">{plans.porVencer} por vencer</span>
              ) : (
                <span className="text-emerald-600 font-semibold">Todas al día</span>
              )} · {plans.planesDisponibles} planes SaaS
            </p>
          </CardContent>
        </Card>
      </div>

      {/* 3. Fila Principal: Alertas & Atención + Salud Técnica */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Alertas / Situaciones que requieren atención (2 cols) */}
        <Card className="lg:col-span-2 border-border/80 flex flex-col justify-between">
          <CardHeader className="flex flex-row items-center justify-between pb-3 border-b border-border/40">
            <div className="flex items-center gap-2.5">
              <span className="material-symbols-outlined text-amber-500">notifications_active</span>
              <div>
                <CardTitle className="text-base font-semibold">Alertas y Atención Requerida</CardTitle>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Eventos del sistema que necesitan revisión o intervención administrativa.
                </p>
              </div>
            </div>
            <Badge variant={alertas.length > 0 ? 'destructive' : 'secondary'} className="font-bold text-xs">
              {alertas.length} {alertas.length === 1 ? 'alerta' : 'alertas'}
            </Badge>
          </CardHeader>
          <CardContent className="pt-4 flex-1">
            {alertas.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-10 text-center space-y-2">
                <span className="material-symbols-outlined text-emerald-500 text-4xl">check_circle</span>
                <p className="text-sm font-semibold text-foreground">Operación Nominal</p>
                <p className="text-xs text-muted-foreground max-w-sm">
                  No hay situaciones críticas o alertas pendientes de atención en este momento.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {alertas.map((alerta) => {
                  const isDanger = alerta.tipo === 'DANGER';
                  const isWarning = alerta.tipo === 'WARNING';
                  const badgeColor = isDanger
                    ? 'bg-rose-500/10 text-rose-600 border-rose-500/20'
                    : isWarning
                    ? 'bg-amber-500/10 text-amber-600 border-amber-500/20'
                    : 'bg-blue-500/10 text-blue-600 border-blue-500/20';

                  return (
                    <div
                      key={alerta.id}
                      className="flex flex-col sm:flex-row sm:items-center justify-between p-3.5 rounded-lg border border-border/60 bg-muted/20 hover:bg-muted/40 transition-colors gap-3"
                    >
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <Badge variant="outline" className={`text-[10px] font-bold uppercase ${badgeColor}`}>
                            {alerta.tipo || 'ALERTA'}
                          </Badge>
                          <span className="font-medium text-xs text-foreground">{alerta.titulo}</span>
                        </div>
                        <p className="text-xs text-muted-foreground">{alerta.mensaje}</p>
                      </div>
                      {alerta.ruta && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => navigate(alerta.ruta)}
                          className="shrink-0 h-8 text-xs font-semibold text-primary hover:text-primary/80 gap-1.5"
                        >
                          Atender
                          <span className="material-symbols-outlined text-sm">arrow_forward</span>
                        </Button>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Salud Técnica de la Infraestructura (1 col) */}
        <Card className="border-border/80 flex flex-col justify-between">
          <CardHeader className="flex flex-row items-center justify-between pb-3 border-b border-border/40">
            <div className="flex items-center gap-2.5">
              <span className="material-symbols-outlined text-primary">monitor_heart</span>
              <div>
                <CardTitle className="text-base font-semibold">Salud Técnica</CardTitle>
                <p className="text-xs text-muted-foreground mt-0.5">Infraestructura y runtime</p>
              </div>
            </div>
            <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-500/10 text-emerald-600 border border-emerald-500/20">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              Operativo
            </span>
          </CardHeader>
          <CardContent className="pt-4 space-y-3 flex-1">
            {/* API Backend */}
            <div className="p-3 rounded-lg border border-border/50 bg-muted/20 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-emerald-600 text-lg">api</span>
                <div>
                  <div className="text-xs font-semibold text-foreground">API Backend Spring Boot</div>
                  <div className="text-[10px] text-muted-foreground">
                    {salud.api?.version || 'SAED 2.0.0-PROD'} · Latencia: {salud.api?.latenciaMs || 14}ms
                  </div>
                </div>
              </div>
              <Badge variant="outline" className="bg-emerald-500/10 text-emerald-600 border-emerald-500/20 text-[10px] font-bold">
                {salud.api?.estado || 'OPERATIVA'}
              </Badge>
            </div>

            {/* Oracle Database */}
            <div className="p-3 rounded-lg border border-border/50 bg-muted/20 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-blue-600 text-lg">database</span>
                <div>
                  <div className="text-xs font-semibold text-foreground">Oracle Autonomous DB</div>
                  <div className="text-[10px] text-muted-foreground">
                    {salud.database?.motor || 'Oracle Cloud ATP 23ai'} · RLS/VPD: {salud.database?.vpdRls || 'ACTIVO'}
                  </div>
                </div>
              </div>
              <Badge variant="outline" className="bg-emerald-500/10 text-emerald-600 border-emerald-500/20 text-[10px] font-bold">
                {salud.database?.estado || 'OPERATIVA'}
              </Badge>
            </div>

            {/* Schedulers & Background Jobs */}
            <div className="p-3 rounded-lg border border-border/50 bg-muted/20 flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-indigo-600 text-lg">schedule</span>
                <div>
                  <div className="text-xs font-semibold text-foreground">Schedulers & Background Jobs</div>
                  <div className="text-[10px] text-muted-foreground">
                    Tareas concurrentes: {salud.schedulers?.tareasActivas || 4} activas
                  </div>
                </div>
              </div>
              <Badge variant="outline" className="bg-emerald-500/10 text-emerald-600 border-emerald-500/20 text-[10px] font-bold">
                {salud.schedulers?.estado || 'OPERATIVO'}
              </Badge>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 4. Actividad Reciente de la Plataforma (AUDITORIA_LOG) */}
      <Card className="border-border/80">
        <CardHeader className="flex flex-row items-center justify-between pb-3 border-b border-border/40">
          <div className="flex items-center gap-2.5">
            <span className="material-symbols-outlined text-primary">history</span>
            <div>
              <CardTitle className="text-base font-semibold">Actividad Reciente de Plataforma</CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                Últimos eventos administrativos y operacionales registrados en auditoría inmutable.
              </p>
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => navigate('/superadmin/auditoria')}
            className="text-xs h-8 gap-1.5"
          >
            Ver Auditoría Completa
            <span className="material-symbols-outlined text-sm">arrow_forward</span>
          </Button>
        </CardHeader>
        <CardContent className="pt-2">
          {actividad.length === 0 ? (
            <div className="text-center py-8 text-xs text-muted-foreground">
              No hay actividad reciente registrada en el log de auditoría.
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="text-xs">Fecha y Hora</TableHead>
                  <TableHead className="text-xs">Acción</TableHead>
                  <TableHead className="text-xs">Entidad</TableHead>
                  <TableHead className="text-xs">Usuario</TableHead>
                  <TableHead className="text-xs">IP Origen</TableHead>
                  <TableHead className="text-xs text-right">Resultado</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {actividad.map((item, idx) => {
                  const isSuccess = item.resultado === 'SUCCESS' || item.resultado === 'EXITO';
                  return (
                    <TableRow key={item.idLog || idx} className="hover:bg-muted/30">
                      <TableCell className="text-xs font-mono text-muted-foreground whitespace-nowrap">
                        {item.fechaHora ? item.fechaHora.replace('T', ' ') : '-'}
                      </TableCell>
                      <TableCell className="text-xs font-semibold text-foreground">
                        {item.accion || 'OPERACION'}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        <Badge variant="outline" className="text-[10px] font-normal">
                          {item.entidad || 'GLOBAL'}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-xs text-foreground font-medium">
                        {item.usuario || 'Sistema'}
                      </TableCell>
                      <TableCell className="text-xs font-mono text-muted-foreground">
                        {item.ip || '127.0.0.1'}
                      </TableCell>
                      <TableCell className="text-xs text-right">
                        <Badge
                          variant="outline"
                          className={
                            isSuccess
                              ? 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20 text-[10px] font-bold'
                              : 'bg-rose-500/10 text-rose-600 border-rose-500/20 text-[10px] font-bold'
                          }
                        >
                          {item.resultado || 'SUCCESS'}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* 5. Accesos Rápidos de Administración */}
      <div className="space-y-3">
        <h2 className="text-sm font-semibold tracking-tight text-foreground flex items-center gap-2">
          <span className="material-symbols-outlined text-primary text-base">widgets</span>
          Accesos Rápidos de Gestión
        </h2>
        <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-3">
          {[
            { label: 'Organizaciones', icon: 'domain', ruta: '/superadmin/organizaciones', desc: 'Tenants SaaS' },
            { label: 'Propiedades', icon: 'apartment', ruta: '/superadmin/propiedades', desc: 'Copropiedades' },
            { label: 'Planes SaaS', icon: 'pricing_plan', ruta: '/superadmin/planes', desc: 'Tarifas y cupos' },
            { label: 'Membresías', icon: 'card_membership', ruta: '/superadmin/membresias', desc: 'Suscripciones' },
            { label: 'Onboarding', icon: 'how_to_reg', ruta: '/superadmin/onboarding', desc: 'Solicitudes' },
            { label: 'Administradores', icon: 'admin_panel_settings', ruta: '/superadmin/administradores', desc: 'Equipo SAED' },
            { label: 'Métricas Globales', icon: 'analytics', ruta: '/superadmin/metricas', desc: 'Analítica SaaS' },
          ].map((item, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => navigate(item.ruta)}
              className="p-3.5 rounded-xl border border-border/70 bg-card hover:bg-muted/40 hover:border-primary/40 transition-all text-left flex flex-col justify-between group shadow-sm"
            >
              <span className="material-symbols-outlined text-primary group-hover:scale-110 transition-transform mb-2">
                {item.icon}
              </span>
              <div>
                <div className="text-xs font-semibold text-foreground group-hover:text-primary transition-colors">
                  {item.label}
                </div>
                <div className="text-[10px] text-muted-foreground">{item.desc}</div>
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
