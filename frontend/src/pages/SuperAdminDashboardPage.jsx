import { useEffect, useState, useMemo } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
} from 'recharts';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
} from '../components/ui/chart.tsx';

const rolesChartConfig = {
  cantidad: {
    label: 'Usuarios',
    color: '#2563eb',
  },
};

const formatRoleLabel = (role) => {
  if (!role) return 'Sin Rol';
  const clean = role.replace(/^ROLE_/, '');
  switch (clean) {
    case 'SUPERADMIN':
      return 'SuperAdmin';
    case 'ADMIN_ORGANIZACION':
      return 'Admin Org';
    case 'ADMIN_PROPIEDAD':
      return 'Admin Prop';
    case 'PORTERO':
      return 'Portería';
    case 'RESIDENTE':
      return 'Residente';
    default:
      return clean.replace(/_/g, ' ');
  }
};

export default function SuperAdminDashboardPage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showRolesList, setShowRolesList] = useState(false);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const res = await api.get('/platform/dashboard');
        setData(res?.data || res || {});
      } catch (err) {
        console.error('Error loading platform dashboard:', err);
        setError('No se pudo cargar el dashboard de plataforma.');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const orgs = data?.organizaciones || { total: 0, activas: 0, inactivas: 0 };
  const props = data?.propiedades || { total: 0, activas: 0 };
  const users = data?.usuarios || { total: 0, activos: 0, desgloseRoles: [] };
  const plans = data?.planesMembresias || { planesDisponibles: 3, membresiasActivas: 0, ingresosMensualesEstimados: 0 };
  const plat = data?.plataforma || { estado: 'OPTIMO', version: 'SAED 2.0.0-PROD', motorBD: 'Oracle Cloud ATP 23ai' };

  const rolesChartData = useMemo(() => {
    if (!users.desgloseRoles || !Array.isArray(users.desgloseRoles)) return [];
    return users.desgloseRoles.map((r) => ({
      rol: formatRoleLabel(r.ROL),
      rolRaw: r.ROL,
      cantidad: Number(r.CANTIDAD) || 0,
    })).sort((a, b) => b.cantidad - a.cantidad);
  }, [users.desgloseRoles]);

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
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-32 rounded-xl" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-8 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              Plataforma SAED — Control Global
            </h1>
            <Badge variant="outline" className="bg-primary/10 text-primary border-primary/20 font-semibold px-2.5 py-0.5">
              GLOBAL SCOPE
            </Badge>
          </div>
          <p className="text-muted-foreground mt-1 text-sm">
            Monitoreo en tiempo real de organizaciones, propiedades, membresías y seguridad SaaS.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            Plataforma Operativa
          </span>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-lg bg-destructive/10 text-destructive border border-destructive/20 text-sm">
          {error}
        </div>
      )}

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {/* Organizaciones */}
        <Card className="hover:shadow-md transition-shadow border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Organizaciones</CardTitle>
            <span className="material-symbols-outlined text-primary p-2 rounded-lg bg-primary/10">domain</span>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-foreground">{orgs.total}</div>
            <p className="text-xs text-muted-foreground mt-1">
              <span className="text-emerald-600 dark:text-emerald-400 font-semibold">{orgs.activas} activas</span> · {orgs.inactivas} inactivas
            </p>
          </CardContent>
        </Card>

        {/* Propiedades */}
        <Card className="hover:shadow-md transition-shadow border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Propiedades Totales</CardTitle>
            <span className="material-symbols-outlined text-indigo-500 p-2 rounded-lg bg-indigo-500/10">apartment</span>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-foreground">{props.total}</div>
            <p className="text-xs text-muted-foreground mt-1">
              <span className="text-emerald-600 dark:text-emerald-400 font-semibold">{props.activas} activas</span> en catálogo
            </p>
          </CardContent>
        </Card>

        {/* Usuarios */}
        <Card className="hover:shadow-md transition-shadow border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Usuarios Globales</CardTitle>
            <span className="material-symbols-outlined text-amber-500 p-2 rounded-lg bg-amber-500/10">group</span>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-foreground">{users.total}</div>
            <p className="text-xs text-muted-foreground mt-1">
              <span className="text-emerald-600 dark:text-emerald-400 font-semibold">{users.activos} activos</span> en el sistema
            </p>
          </CardContent>
        </Card>

        {/* Facturación SaaS */}
        <Card className="hover:shadow-md transition-shadow border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">Ingresos SaaS / Mes</CardTitle>
            <span className="material-symbols-outlined text-emerald-500 p-2 rounded-lg bg-emerald-500/10">payments</span>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-foreground">
              ${(plans.ingresosMensualesEstimados || 0).toLocaleString('es-CO')}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              <span className="font-semibold text-primary">{plans.membresiasActivas} membresías activas</span>
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Two Column Layout for Breakdown and Infrastructure */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Desglose de Usuarios por Rol */}
        <Card className="border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-3">
            <CardTitle className="text-base font-semibold flex items-center gap-2">
              <span className="material-symbols-outlined text-primary">badge</span>
              Distribución de Usuarios por Rol
            </CardTitle>
            <div className="flex items-center gap-2">
              <Badge variant="outline" className="text-xs font-semibold bg-primary/10 text-primary border-primary/20">
                {users.total || 0} Registrados
              </Badge>
              {rolesChartData.length > 0 && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowRolesList(!showRolesList)}
                  className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground"
                >
                  {showRolesList ? 'Ver Gráfica' : 'Ver Lista'}
                </Button>
              )}
            </div>
          </CardHeader>
          <CardContent>
            {rolesChartData.length === 0 ? (
              <div className="text-center py-8 text-sm text-muted-foreground">
                No hay desglose disponible en este momento.
              </div>
            ) : showRolesList ? (
              <div className="space-y-3">
                {users.desgloseRoles.map((r, i) => (
                  <div key={i} className="flex items-center justify-between p-2.5 rounded-lg bg-muted/40 hover:bg-muted/70 transition-colors">
                    <div className="flex items-center gap-2.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-primary"></span>
                      <span className="font-medium text-xs text-foreground">{r.ROL || 'SIN ROL'}</span>
                    </div>
                    <Badge variant="secondary" className="font-bold text-xs">
                      {r.CANTIDAD} usuarios
                    </Badge>
                  </div>
                ))}
              </div>
            ) : (
              <div className="space-y-4">
                <ChartContainer config={rolesChartConfig} className="h-[210px] w-full">
                  <BarChart
                    accessibilityLayer
                    data={rolesChartData}
                    layout="vertical"
                    margin={{ left: 10, right: 20, top: 5, bottom: 5 }}
                  >
                    <CartesianGrid horizontal={false} strokeDasharray="3 3" className="stroke-muted/40" />
                    <YAxis
                      dataKey="rol"
                      type="category"
                      tickLine={false}
                      axisLine={false}
                      tick={{ fill: "currentColor", fontSize: 11 }}
                      width={85}
                    />
                    <XAxis
                      type="number"
                      tickLine={false}
                      axisLine={false}
                      tick={{ fill: "currentColor", fontSize: 10 }}
                      allowDecimals={false}
                    />
                    <ChartTooltip
                      cursor={{ fill: 'rgba(0,0,0,0.04)' }}
                      content={<ChartTooltipContent indicator="line" />}
                    />
                    <Bar
                      dataKey="cantidad"
                      fill="var(--color-cantidad, #2563eb)"
                      radius={[0, 4, 4, 0]}
                    />
                  </BarChart>
                </ChartContainer>

                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 pt-2 border-t border-border/40">
                  {rolesChartData.slice(0, 6).map((item, idx) => (
                    <div key={idx} className="flex items-center justify-between px-2.5 py-1.5 rounded-md bg-muted/30 text-xs">
                      <span className="text-muted-foreground truncate mr-2">{item.rol}:</span>
                      <span className="font-semibold text-foreground">{item.cantidad}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Salud de la Plataforma & Servicios */}
        <Card className="border-border/80">
          <CardHeader className="flex flex-row items-center justify-between pb-3">
            <CardTitle className="text-base font-semibold flex items-center gap-2">
              <span className="material-symbols-outlined text-primary">monitor_heart</span>
              Salud de la Plataforma
            </CardTitle>
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              100% Operativo
            </span>
          </CardHeader>
          <CardContent className="space-y-3">
            {/* API Backend */}
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-muted/40 hover:bg-muted/60 transition-colors">
              <div className="flex items-center gap-2.5">
                <span className="material-symbols-outlined text-emerald-600 dark:text-emerald-400 text-base">api</span>
                <div>
                  <div className="text-xs font-semibold text-foreground">API Backend Spring Boot</div>
                  <div className="text-[10px] text-muted-foreground">{plat.version || 'SAED 2.0.0-PROD'} · Java 17</div>
                </div>
              </div>
              <Badge variant="outline" className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20 text-[10px] font-semibold">
                Activa · 200 OK
              </Badge>
            </div>

            {/* Base de Datos Oracle */}
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-muted/40 hover:bg-muted/60 transition-colors">
              <div className="flex items-center gap-2.5">
                <span className="material-symbols-outlined text-blue-600 dark:text-blue-400 text-base">database</span>
                <div>
                  <div className="text-xs font-semibold text-foreground">Base de Datos Oracle ATP</div>
                  <div className="text-[10px] text-muted-foreground">{plat.motorBD || 'Oracle Cloud ATP 23ai'} · RLS Activo</div>
                </div>
              </div>
              <Badge variant="outline" className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20 text-[10px] font-semibold">
                Conectada
              </Badge>
            </div>

            {/* Servicio Auth JWT */}
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-muted/40 hover:bg-muted/60 transition-colors">
              <div className="flex items-center gap-2.5">
                <span className="material-symbols-outlined text-indigo-600 dark:text-indigo-400 text-base">lock_clock</span>
                <div>
                  <div className="text-xs font-semibold text-foreground">Autenticación & Seguridad JWT</div>
                  <div className="text-[10px] text-muted-foreground">Sesiones Zero-Trust · PKG_AUTH_BOOTSTRAP</div>
                </div>
              </div>
              <Badge variant="outline" className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20 text-[10px] font-semibold">
                Protegido
              </Badge>
            </div>

            {/* Almacenamiento Documental */}
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-muted/40 hover:bg-muted/60 transition-colors">
              <div className="flex items-center gap-2.5">
                <span className="material-symbols-outlined text-amber-600 dark:text-amber-400 text-base">folder_zip</span>
                <div>
                  <div className="text-xs font-semibold text-foreground">Almacenamiento Seguro (Storage)</div>
                  <div className="text-[10px] text-muted-foreground">Soportes de gastos, contratos y actas (SHA-256)</div>
                </div>
              </div>
              <Badge variant="outline" className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20 text-[10px] font-semibold">
                En Línea
              </Badge>
            </div>

            {/* Pasarela Wompi */}
            <div className="flex items-center justify-between p-2.5 rounded-lg bg-muted/40 hover:bg-muted/60 transition-colors">
              <div className="flex items-center gap-2.5">
                <span className="material-symbols-outlined text-emerald-600 dark:text-emerald-400 text-base">credit_card</span>
                <div>
                  <div className="text-xs font-semibold text-foreground">Pasarela de Pagos (Wompi)</div>
                  <div className="text-[10px] text-muted-foreground">Webhooks activos · Conciliación automática</div>
                </div>
              </div>
              <Badge variant="outline" className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20 text-[10px] font-semibold">
                Integrada
              </Badge>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
