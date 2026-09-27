import { useEffect, useState, useMemo } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
} from 'recharts';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import {
  Tabs,
  TabsList,
  TabsTrigger,
  TabsContent,
} from '../components/ui/tabs.tsx';
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from '../components/ui/table.tsx';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select.tsx';

const PERIODOS = [
  { value: 'hoy', label: 'Hoy' },
  { value: '7d', label: 'Últimos 7 días' },
  { value: '30d', label: 'Últimos 30 días' },
  { value: '90d', label: 'Últimos 3 meses' },
  { value: '1y', label: 'Este año' },
  { value: 'todo', label: 'Histórico completo' },
];

const formatRoleLabel = (role) => {
  if (!role) return 'Sin Rol';
  const clean = role.replace(/^ROLE_/, '');
  switch (clean) {
    case 'SUPERADMIN':
      return 'SuperAdmin';
    case 'ADMIN_ORGANIZACION':
      return 'Admin Organización';
    case 'ADMIN_PROPIEDAD':
      return 'Admin Propiedad';
    case 'PORTERO':
      return 'Portería';
    case 'RESIDENTE':
      return 'Residente';
    default:
      return clean.replace(/_/g, ' ');
  }
};

export default function SuperAdminMetricasPage() {
  const [analytics, setAnalytics] = useState(null);
  const [organizaciones, setOrganizaciones] = useState([]);
  const [periodo, setPeriodo] = useState('30d');
  const [selectedOrg, setSelectedOrg] = useState('GLOBAL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Cargar lista de organizaciones para el filtro
  useEffect(() => {
    async function loadOrgs() {
      try {
        const res = await api.get('/organizations');
        const list = res?.data || res || [];
        setOrganizaciones(Array.isArray(list) ? list : []);
      } catch (e) {
        console.warn('No se pudieron cargar organizaciones para el filtro:', e);
      }
    }
    loadOrgs();
  }, []);

  // Cargar analítica según periodo y organización
  const loadAnalytics = async () => {
    try {
      setLoading(true);
      setError(null);
      const params = new URLSearchParams();
      if (periodo) params.append('periodo', periodo);
      if (selectedOrg && selectedOrg !== 'GLOBAL') {
        params.append('idOrganizacion', selectedOrg);
      }

      const res = await api.get(`/platform/dashboard/analytics?${params.toString()}`);
      setAnalytics(res?.data || res || {});
    } catch (err) {
      console.error('Error cargando platform analytics:', err);
      setError('No se pudieron cargar las métricas analíticas globales.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAnalytics();
  }, [periodo, selectedOrg]);

  // Datos extraídos de la respuesta
  const resumen = analytics?.resumenGlobal || {};
  const orgStats = resumen.organizaciones || { total: 0, activas: 0, inactivas: 0 };
  const propStats = resumen.propiedades || { total: 0, activas: 0, inactivas: 0 };
  const unitStats = resumen.unidades || { total: 0 };
  const userStats = resumen.usuarios || { total: 0, activos: 0, inactivos: 0 };
  const resStats = resumen.residentes || { total: 0, activos: 0, inactivos: 0 };
  const trabStats = resumen.trabajadores || { total: 0, activos: 0, inactivos: 0 };

  const retencion = analytics?.tasaRetencionOrganizacionesPct != null
    ? analytics.tasaRetencionOrganizacionesPct
    : 100.0;

  const propTipos = Array.isArray(analytics?.distribucionPropiedadesPorTipo)
    ? analytics.distribucionPropiedadesPorTipo
    : [];

  const unitTipos = Array.isArray(analytics?.distribucionUnidadesPorTipo)
    ? analytics.distribucionUnidadesPorTipo
    : [];

  const ciudades = Array.isArray(analytics?.distribucionPropiedadesPorCiudad)
    ? analytics.distribucionPropiedadesPorCiudad
    : [];

  const roles = Array.isArray(analytics?.distribucionRoles)
    ? analytics.distribucionRoles
    : [];

  const planes = Array.isArray(analytics?.distribucionPlanes)
    ? analytics.distribucionPlanes
    : [];

  const membresias = Array.isArray(analytics?.membresiasPorEstado)
    ? analytics.membresiasPorEstado
    : [];

  const entitlements = Array.isArray(analytics?.entitlementsModulos)
    ? analytics.entitlementsModulos
    : [];

  const actividadOperativa = analytics?.actividadOperativa || {
    visitas: 0,
    paquetes: 0,
    pqrs: 0,
    mantenimientos: 0,
    reservas: 0,
    comunicados: 0,
  };

  const sandbox = analytics?.transaccionesSandbox || {
    total: 0,
    aprobadas: 0,
    rechazadas: 0,
    pendientes: 0,
    montoAprobadoCentavos: 0,
  };

  const seguridad = analytics?.metricasSeguridad || {
    loginsExitosos: 0,
    loginsFallidos: 0,
    accionesAuditadas: 0,
  };

  const crecimientoMensual = useMemo(() => {
    if (!Array.isArray(analytics?.crecimientoOrganizacionesMensual)) return [];
    return analytics.crecimientoOrganizacionesMensual.map((item) => ({
      mes: item.mes,
      nuevas: Number(item.nuevasOrganizaciones) || 0,
    }));
  }, [analytics?.crecimientoOrganizacionesMensual]);

  const rolesChartData = useMemo(() => {
    return roles.map((r) => ({
      rol: formatRoleLabel(r.rol || r.ROL),
      cantidad: Number(r.cantidad || r.CANTIDAD) || 0,
    })).sort((a, b) => b.cantidad - a.cantidad);
  }, [roles]);

  return (
    <div className="p-6 space-y-8 animate-fadeIn">
      {/* 1. Header con Filtros de Periodo y Organización */}
      <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 border-b border-border pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              Métricas Globales SAED
            </h1>
            <Badge variant="outline" className="bg-primary/10 text-primary border-primary/20 font-semibold px-2.5 py-0.5">
              ANALÍTICA ECOSISTEMA
            </Badge>
          </div>
          <p className="text-muted-foreground mt-1 text-sm">
            Distribución dimensional, tendencias de crecimiento, adopción de catálogo y transaccionalidad.
          </p>
        </div>

        {/* Filtros */}
        <div className="flex flex-wrap items-center gap-3">
          {/* Selector de Organización */}
          <div className="w-56">
            <Select value={selectedOrg} onValueChange={setSelectedOrg}>
              <SelectTrigger className="h-9 text-xs">
                <SelectValue placeholder="Todas las organizaciones" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="GLOBAL" className="text-xs">
                  🌍 Global (Todas las Orgs)
                </SelectItem>
                {organizaciones.map((org) => {
                  const orgId = String(org.id || org.idOrganizacion || org.ID_ORGANIZACION);
                  return (
                    <SelectItem key={orgId} value={orgId} className="text-xs">
                      {org.nombre || `Organización #${orgId}`}
                    </SelectItem>
                  );
                })}
              </SelectContent>
            </Select>
          </div>

          {/* Selector de Periodo */}
          <div className="w-44">
            <Select value={periodo} onValueChange={setPeriodo}>
              <SelectTrigger className="h-9 text-xs">
                <SelectValue placeholder="Periodo" />
              </SelectTrigger>
              <SelectContent>
                {PERIODOS.map((p) => (
                  <SelectItem key={p.value} value={p.value} className="text-xs">
                    {p.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={loadAnalytics}
            className="h-9 px-3 text-xs gap-1.5"
            disabled={loading}
          >
            <span className="material-symbols-outlined text-sm">sync</span>
            Refrescar
          </Button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-lg bg-destructive/10 text-destructive border border-destructive/20 text-sm flex items-center justify-between">
          <span>{error}</span>
          <Button variant="ghost" size="sm" onClick={loadAnalytics} className="h-7 text-xs">
            Reintentar
          </Button>
        </div>
      )}

      {loading ? (
        <div className="space-y-6">
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            {[1, 2, 3, 4, 5, 6].map((i) => (
              <Skeleton key={i} className="h-24 rounded-xl" />
            ))}
          </div>
          <Skeleton className="h-96 rounded-xl" />
        </div>
      ) : (
        <>
          {/* 2. Resumen Dimensional de Tamaño Global (6 Indicadores) */}
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            {/* Organizaciones */}
            <Card className="border-border/80">
              <CardContent className="pt-4 pb-3">
                <div className="flex items-center justify-between text-muted-foreground text-xs font-medium mb-1">
                  <span>Organizaciones</span>
                  <span className="material-symbols-outlined text-primary text-base">domain</span>
                </div>
                <div className="text-2xl font-bold text-foreground">{orgStats.total}</div>
                <div className="text-[11px] text-muted-foreground mt-1">
                  <span className="text-emerald-600 font-semibold">{orgStats.activas} activas</span> · {orgStats.inactivas} inact.
                </div>
              </CardContent>
            </Card>

            {/* Propiedades */}
            <Card className="border-border/80">
              <CardContent className="pt-4 pb-3">
                <div className="flex items-center justify-between text-muted-foreground text-xs font-medium mb-1">
                  <span>Propiedades</span>
                  <span className="material-symbols-outlined text-indigo-500 text-base">apartment</span>
                </div>
                <div className="text-2xl font-bold text-foreground">{propStats.total}</div>
                <div className="text-[11px] text-muted-foreground mt-1">
                  <span className="text-emerald-600 font-semibold">{propStats.activas} activas</span> · {propStats.inactivas} inact.
                </div>
              </CardContent>
            </Card>

            {/* Unidades */}
            <Card className="border-border/80">
              <CardContent className="pt-4 pb-3">
                <div className="flex items-center justify-between text-muted-foreground text-xs font-medium mb-1">
                  <span>Unidades Totales</span>
                  <span className="material-symbols-outlined text-blue-500 text-base">meeting_room</span>
                </div>
                <div className="text-2xl font-bold text-foreground">{unitStats.total}</div>
                <div className="text-[11px] text-muted-foreground mt-1">
                  Inmuebles en catálogo
                </div>
              </CardContent>
            </Card>

            {/* Usuarios */}
            <Card className="border-border/80">
              <CardContent className="pt-4 pb-3">
                <div className="flex items-center justify-between text-muted-foreground text-xs font-medium mb-1">
                  <span>Usuarios</span>
                  <span className="material-symbols-outlined text-amber-500 text-base">group</span>
                </div>
                <div className="text-2xl font-bold text-foreground">{userStats.total}</div>
                <div className="text-[11px] text-muted-foreground mt-1">
                  <span className="text-emerald-600 font-semibold">{userStats.activos} activos</span> · {userStats.inactivos} inact.
                </div>
              </CardContent>
            </Card>

            {/* Residentes */}
            <Card className="border-border/80">
              <CardContent className="pt-4 pb-3">
                <div className="flex items-center justify-between text-muted-foreground text-xs font-medium mb-1">
                  <span>Residentes</span>
                  <span className="material-symbols-outlined text-emerald-500 text-base">person</span>
                </div>
                <div className="text-2xl font-bold text-foreground">{resStats.total}</div>
                <div className="text-[11px] text-muted-foreground mt-1">
                  <span className="text-emerald-600 font-semibold">{resStats.activos} activos</span> · {resStats.inactivos} inact.
                </div>
              </CardContent>
            </Card>

            {/* Trabajadores */}
            <Card className="border-border/80">
              <CardContent className="pt-4 pb-3">
                <div className="flex items-center justify-between text-muted-foreground text-xs font-medium mb-1">
                  <span>Trabajadores</span>
                  <span className="material-symbols-outlined text-purple-500 text-base">badge</span>
                </div>
                <div className="text-2xl font-bold text-foreground">{trabStats.total}</div>
                <div className="text-[11px] text-muted-foreground mt-1">
                  <span className="text-emerald-600 font-semibold">{trabStats.activos} activos</span> · {trabStats.inactivos} inact.
                </div>
              </CardContent>
            </Card>
          </div>

          {/* 3. Secciones Analíticas en Tabs de Shadcn UI */}
          <Tabs defaultValue="crecimiento" className="w-full space-y-6">
            <TabsList className="grid grid-cols-2 md:grid-cols-6 h-auto p-1 bg-muted/60 rounded-xl gap-1">
              <TabsTrigger value="crecimiento" className="text-xs py-2">
                Crecimiento & Tenencia
              </TabsTrigger>
              <TabsTrigger value="propiedades" className="text-xs py-2">
                Propiedades & Unidades
              </TabsTrigger>
              <TabsTrigger value="usuarios" className="text-xs py-2">
                Usuarios & Roles
              </TabsTrigger>
              <TabsTrigger value="planes" className="text-xs py-2">
                Planes & Entitlements
              </TabsTrigger>
              <TabsTrigger value="operativa" className="text-xs py-2">
                Actividad Operativa
              </TabsTrigger>
              <TabsTrigger value="sandbox" className="text-xs py-2">
                Sandbox & Seguridad
              </TabsTrigger>
            </TabsList>

            {/* TAB 1: Crecimiento & Tenencia */}
            <TabsContent value="crecimiento" className="space-y-6">
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Gráfica de Nuevas Organizaciones por Mes */}
                <Card className="lg:col-span-2 border-border/80">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-base font-semibold flex items-center justify-between">
                      <span>Nuevas Organizaciones Registradas (Últimos 12 Meses)</span>
                      <Badge variant="outline" className="text-xs font-normal">
                        Histórico Anual
                      </Badge>
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      Evolución temporal del alta de tenants en la plataforma SAED.
                    </p>
                  </CardHeader>
                  <CardContent className="pt-4">
                    {crecimientoMensual.length === 0 ? (
                      <div className="text-center py-16 text-xs text-muted-foreground">
                        No hay datos de crecimiento mensual disponibles.
                      </div>
                    ) : (
                      <div className="h-64 w-full">
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={crecimientoMensual} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                            <CartesianGrid strokeDasharray="3 3" className="stroke-muted/40" />
                            <XAxis dataKey="mes" tick={{ fontSize: 11 }} />
                            <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                            <RechartsTooltip
                              content={({ active, payload, label }) => {
                                if (active && payload && payload.length) {
                                  return (
                                    <div className="p-2 rounded-lg border bg-popover text-popover-foreground shadow-md text-xs">
                                      <div className="font-semibold">{label}</div>
                                      <div className="text-primary font-bold">
                                        {payload[0].value} {payload[0].value === 1 ? 'organización' : 'organizaciones'}
                                      </div>
                                    </div>
                                  );
                                }
                                return null;
                              }}
                            />
                            <Bar dataKey="nuevas" fill="#2563eb" radius={[4, 4, 0, 0]} />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    )}
                  </CardContent>
                </Card>

                {/* Retención y Geografía */}
                <Card className="border-border/80 flex flex-col justify-between">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-base font-semibold">Salud de Tenencia</CardTitle>
                    <p className="text-xs text-muted-foreground">Métricas de retención y estado de suscripción.</p>
                  </CardHeader>
                  <CardContent className="space-y-4 pt-2">
                    <div className="p-4 rounded-xl border border-border/60 bg-muted/20 text-center space-y-1">
                      <div className="text-xs font-medium text-muted-foreground">Tasa de Retención de Tenants</div>
                      <div className="text-3xl font-extrabold text-emerald-600">
                        {retencion.toFixed(1)}%
                      </div>
                      <p className="text-[11px] text-muted-foreground">
                        {orgStats.activas} de {orgStats.total} organizaciones operativas
                      </p>
                    </div>

                    <div className="space-y-2">
                      <div className="text-xs font-semibold text-foreground">Distribución Geográfica (Ciudades)</div>
                      {ciudades.length === 0 ? (
                        <div className="text-xs text-muted-foreground">Sin datos de ubicación.</div>
                      ) : (
                        <div className="space-y-1.5 max-h-36 overflow-y-auto pr-1">
                          {ciudades.map((c, i) => (
                            <div key={i} className="flex items-center justify-between text-xs p-2 rounded-lg bg-muted/30">
                              <span className="font-medium text-foreground">{c.ciudad || c.CIUDAD}</span>
                              <Badge variant="secondary" className="text-[10px] font-bold">
                                {c.total || c.TOTAL} {c.total === 1 ? 'propiedad' : 'propiedades'}
                              </Badge>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </CardContent>
                </Card>
              </div>
            </TabsContent>

            {/* TAB 2: Propiedades & Unidades */}
            <TabsContent value="propiedades" className="space-y-6">
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Tipos de Propiedad */}
                <Card className="border-border/80">
                  <CardHeader>
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                      <span className="material-symbols-outlined text-primary text-base">location_city</span>
                      Distribución por Tipo de Propiedad
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      Categorización registrada en el catálogo canónico (TIPOS_PROPIEDAD).
                    </p>
                  </CardHeader>
                  <CardContent>
                    {propTipos.length === 0 ? (
                      <div className="text-center py-8 text-xs text-muted-foreground">
                        No hay propiedades registradas con tipo asignado.
                      </div>
                    ) : (
                      <div className="space-y-3">
                        {propTipos.map((item, idx) => {
                          const cant = Number(item.cantidad || item.CANTIDAD) || 0;
                          const total = propStats.total || 1;
                          const pct = Math.round((cant / total) * 100);
                          return (
                            <div key={idx} className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1.5">
                              <div className="flex items-center justify-between text-xs">
                                <span className="font-semibold text-foreground">
                                  {item.tipo || item.TIPO || item.codigo || 'TIPO GENERAL'}
                                </span>
                                <span className="text-muted-foreground font-mono font-medium">
                                  {cant} ({pct}%)
                                </span>
                              </div>
                              <div className="w-full bg-muted rounded-full h-2 overflow-hidden">
                                <div
                                  className="bg-indigo-600 h-2 rounded-full transition-all"
                                  style={{ width: `${Math.min(100, Math.max(0, pct))}%` }}
                                />
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </CardContent>
                </Card>

                {/* Tipos de Unidad */}
                <Card className="border-border/80">
                  <CardHeader>
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                      <span className="material-symbols-outlined text-blue-500 text-base">door_front</span>
                      Distribución por Tipo de Unidad
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      Desglose de inmuebles registrados (TIPOS_UNIDAD: Apartamentos, Casas, Locales, etc.).
                    </p>
                  </CardHeader>
                  <CardContent>
                    {unitTipos.length === 0 ? (
                      <div className="text-center py-8 text-xs text-muted-foreground">
                        No hay unidades con tipo asignado.
                      </div>
                    ) : (
                      <div className="space-y-3">
                        {unitTipos.map((item, idx) => {
                          const cant = Number(item.cantidad || item.CANTIDAD) || 0;
                          const total = unitStats.total || 1;
                          const pct = Math.round((cant / total) * 100);
                          return (
                            <div key={idx} className="p-3 rounded-lg border border-border/60 bg-muted/20 space-y-1.5">
                              <div className="flex items-center justify-between text-xs">
                                <span className="font-semibold text-foreground">
                                  {item.tipo || item.TIPO || item.codigo || 'UNIDAD'}
                                </span>
                                <span className="text-muted-foreground font-mono font-medium">
                                  {cant} ({pct}%)
                                </span>
                              </div>
                              <div className="w-full bg-muted rounded-full h-2 overflow-hidden">
                                <div
                                  className="bg-blue-600 h-2 rounded-full transition-all"
                                  style={{ width: `${Math.min(100, Math.max(0, pct))}%` }}
                                />
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </CardContent>
                </Card>
              </div>
            </TabsContent>

            {/* TAB 3: Usuarios & Roles */}
            <TabsContent value="usuarios" className="space-y-6">
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Gráfica de Usuarios por Rol (2 cols) */}
                <Card className="lg:col-span-2 border-border/80">
                  <CardHeader>
                    <CardTitle className="text-base font-semibold flex items-center justify-between">
                      <span>Distribución de Usuarios por Rol Canónico</span>
                      <Badge variant="outline" className="text-xs">
                        {userStats.total} Cuentas
                      </Badge>
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      Conteo de asignaciones activas por rol en la plataforma.
                    </p>
                  </CardHeader>
                  <CardContent>
                    {rolesChartData.length === 0 ? (
                      <div className="text-center py-12 text-xs text-muted-foreground">
                        No hay datos de roles disponibles.
                      </div>
                    ) : (
                      <div className="h-64 w-full">
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={rolesChartData} layout="vertical" margin={{ left: 20, right: 20, top: 10, bottom: 10 }}>
                            <CartesianGrid horizontal={false} strokeDasharray="3 3" className="stroke-muted/40" />
                            <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
                            <YAxis dataKey="rol" type="category" width={120} tick={{ fontSize: 11 }} />
                            <RechartsTooltip
                              content={({ active, payload }) => {
                                if (active && payload && payload.length) {
                                  return (
                                    <div className="p-2 rounded-lg border bg-popover text-popover-foreground shadow-md text-xs">
                                      <div className="font-semibold">{payload[0].payload.rol}</div>
                                      <div className="text-primary font-bold">
                                        {payload[0].value} usuarios
                                      </div>
                                    </div>
                                  );
                                }
                                return null;
                              }}
                            />
                            <Bar dataKey="cantidad" fill="#2563eb" radius={[0, 4, 4, 0]} />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    )}
                  </CardContent>
                </Card>

                {/* Tabla de Roles y Detalle */}
                <Card className="border-border/80 flex flex-col justify-between">
                  <CardHeader className="pb-2">
                    <CardTitle className="text-base font-semibold">Detalle de Asignaciones</CardTitle>
                    <p className="text-xs text-muted-foreground">Participación por rol en el sistema.</p>
                  </CardHeader>
                  <CardContent className="pt-2 flex-1">
                    <div className="space-y-2">
                      {roles.map((r, i) => {
                        const cant = Number(r.cantidad || r.CANTIDAD) || 0;
                        const pct = userStats.total > 0 ? Math.round((cant / userStats.total) * 100) : 0;
                        return (
                          <div key={i} className="flex items-center justify-between p-2 rounded-lg bg-muted/30 text-xs">
                            <span className="font-medium text-foreground">{formatRoleLabel(r.rol || r.ROL)}</span>
                            <div className="flex items-center gap-2">
                              <span className="text-[10px] text-muted-foreground font-mono">{pct}%</span>
                              <Badge variant="secondary" className="font-bold text-xs">
                                {cant}
                              </Badge>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </CardContent>
                </Card>
              </div>
            </TabsContent>

            {/* TAB 4: Planes & Membresías */}
            <TabsContent value="planes" className="space-y-6">
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Organizaciones por Plan Canónico */}
                <Card className="border-border/80">
                  <CardHeader>
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                      <span className="material-symbols-outlined text-primary text-base">pricing_plan</span>
                      Organizaciones por Plan
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">Distribución de suscripciones por nivel de servicio.</p>
                  </CardHeader>
                  <CardContent>
                    {planes.length === 0 ? (
                      <div className="text-center py-8 text-xs text-muted-foreground">
                        No hay planes canónicos con suscripciones.
                      </div>
                    ) : (
                      <div className="space-y-3">
                        {planes.map((p, idx) => (
                          <div key={idx} className="p-3 rounded-lg border border-border/60 bg-muted/20 flex items-center justify-between">
                            <div>
                              <div className="text-xs font-bold text-foreground">{p.planNombre || p.planCodigo}</div>
                              <div className="text-[11px] text-muted-foreground">
                                ${(p.precioMensual || 0).toLocaleString('es-CO')} / mes
                              </div>
                            </div>
                            <Badge variant="secondary" className="text-xs font-bold">
                              {p.organizaciones || 0} orgs
                            </Badge>
                          </div>
                        ))}
                      </div>
                    )}
                  </CardContent>
                </Card>

                {/* Membresías por Estado */}
                <Card className="border-border/80">
                  <CardHeader>
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                      <span className="material-symbols-outlined text-emerald-500 text-base">card_membership</span>
                      Membresías por Estado
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">Ciclo de vida de membresías registradas.</p>
                  </CardHeader>
                  <CardContent>
                    {membresias.length === 0 ? (
                      <div className="text-center py-8 text-xs text-muted-foreground">
                        No hay estados de membresía registrados.
                      </div>
                    ) : (
                      <div className="space-y-2.5">
                        {membresias.map((m, idx) => {
                          const est = m.estado || m.ESTADO || 'DESCONOCIDO';
                          const isAct = est === 'ACTIVA';
                          return (
                            <div key={idx} className="flex items-center justify-between p-2.5 rounded-lg bg-muted/30 text-xs">
                              <span className="font-semibold text-foreground">{est}</span>
                              <Badge
                                variant="outline"
                                className={
                                  isAct
                                    ? 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20 font-bold'
                                    : 'bg-muted text-muted-foreground'
                                }
                              >
                                {m.cantidad || m.CANTIDAD || 0}
                              </Badge>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </CardContent>
                </Card>

                {/* Entitlements de Módulos */}
                <Card className="border-border/80">
                  <CardHeader>
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                      <span className="material-symbols-outlined text-indigo-500 text-base">extension</span>
                      Módulos Habilitados
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">Catálogo de funcionalidades por plan.</p>
                  </CardHeader>
                  <CardContent>
                    {entitlements.length === 0 ? (
                      <div className="text-center py-8 text-xs text-muted-foreground">
                        No hay catálogo de módulos registrado.
                      </div>
                    ) : (
                      <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                        {entitlements.map((mod, idx) => (
                          <div key={idx} className="flex items-center justify-between p-2 rounded-lg bg-muted/30 text-xs">
                            <div>
                              <div className="font-semibold text-foreground">{mod.nombre || mod.codigo}</div>
                              <div className="text-[10px] text-muted-foreground">{mod.categoria || 'GENERAL'}</div>
                            </div>
                            <Badge variant="outline" className="text-[10px] font-medium">
                              {mod.planesHabilitados || 0} planes
                            </Badge>
                          </div>
                        ))}
                      </div>
                    )}
                  </CardContent>
                </Card>
              </div>
            </TabsContent>

            {/* TAB 5: Actividad Operativa Global */}
            <TabsContent value="operativa" className="space-y-6">
              <Card className="border-border/80">
                <CardHeader>
                  <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                    <div>
                      <CardTitle className="text-base font-semibold flex items-center gap-2">
                        <span className="material-symbols-outlined text-primary text-base">bolt</span>
                        Actividad Operativa Global del Periodo
                      </CardTitle>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        Métricas transaccionales acumuladas en las copropiedades durante el periodo seleccionado.
                      </p>
                    </div>
                    <Badge variant="secondary" className="w-fit text-xs font-mono">
                      Periodo: {PERIODOS.find((p) => p.value === periodo)?.label || periodo}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent className="pt-2">
                  <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
                    {[
                      { label: 'Visitas', val: actividadOperativa.visitas, icon: 'badge', color: 'text-blue-500' },
                      { label: 'Paquetes', val: actividadOperativa.paquetes, icon: 'package_2', color: 'text-amber-500' },
                      { label: 'PQRS Radicadas', val: actividadOperativa.pqrs, icon: 'contact_support', color: 'text-rose-500' },
                      { label: 'Mantenimientos', val: actividadOperativa.mantenimientos, icon: 'build', color: 'text-indigo-500' },
                      { label: 'Reservas Zonas', val: actividadOperativa.reservas, icon: 'event', color: 'text-emerald-500' },
                      { label: 'Comunicados', val: actividadOperativa.comunicados, icon: 'campaign', color: 'text-purple-500' },
                    ].map((item, idx) => (
                      <div key={idx} className="p-4 rounded-xl border border-border/60 bg-muted/20 text-center space-y-1">
                        <span className={`material-symbols-outlined text-2xl ${item.color}`}>
                          {item.icon}
                        </span>
                        <div className="text-2xl font-bold text-foreground">{item.val}</div>
                        <div className="text-xs text-muted-foreground">{item.label}</div>
                      </div>
                    ))}
                  </div>

                  <div className="mt-4 p-3 rounded-lg border border-border/40 bg-muted/10 text-xs text-muted-foreground flex items-center gap-2">
                    <span className="material-symbols-outlined text-primary text-base">info</span>
                    <span>
                      Estos valores representan eventos reales de operación agregados desde las tablas transaccionales de Oracle.
                    </span>
                  </div>
                </CardContent>
              </Card>
            </TabsContent>

            {/* TAB 6: Sandbox Wompi & Seguridad */}
            <TabsContent value="sandbox" className="space-y-6">
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Transacciones Sandbox Wompi */}
                <Card className="border-border/80">
                  <CardHeader>
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                      <span className="material-symbols-outlined text-amber-500 text-base">credit_card</span>
                      Actividad Transaccional (Sandbox Wompi)
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      Validación técnica de pasarela de pagos en entorno de pruebas.
                    </p>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    {/* Banner de Aviso de Sandbox */}
                    <div className="p-3.5 rounded-lg border border-amber-500/30 bg-amber-500/10 text-amber-900 dark:text-amber-200 text-xs flex items-start gap-2.5">
                      <span className="material-symbols-outlined text-amber-600 text-lg shrink-0 mt-0.5">warning</span>
                      <div>
                        <div className="font-semibold">Entorno de Pruebas Activo</div>
                        <p className="text-[11px] opacity-90 mt-0.5">
                          {sandbox.aviso || 'Las transacciones registradas corresponden a pruebas de integración técnica en Wompi Sandbox y no constituyen facturación o ingresos reales.'}
                        </p>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                      <div className="p-3 rounded-lg bg-muted/30 text-center">
                        <div className="text-xl font-bold text-foreground">{sandbox.total}</div>
                        <div className="text-[11px] text-muted-foreground">Total Intentos</div>
                      </div>
                      <div className="p-3 rounded-lg bg-emerald-500/10 text-center">
                        <div className="text-xl font-bold text-emerald-600">{sandbox.aprobadas}</div>
                        <div className="text-[11px] text-emerald-700 dark:text-emerald-300">Aprobadas</div>
                      </div>
                      <div className="p-3 rounded-lg bg-rose-500/10 text-center">
                        <div className="text-xl font-bold text-rose-600">{sandbox.rechazadas}</div>
                        <div className="text-[11px] text-rose-700 dark:text-rose-300">Rechazadas</div>
                      </div>
                      <div className="p-3 rounded-lg bg-amber-500/10 text-center">
                        <div className="text-xl font-bold text-amber-600">{sandbox.pendientes}</div>
                        <div className="text-[11px] text-amber-700 dark:text-amber-300">Pendientes</div>
                      </div>
                    </div>

                    <div className="p-3 rounded-lg border border-border/50 bg-muted/20 flex items-center justify-between text-xs">
                      <span className="text-muted-foreground font-medium">Volumen Validado Sandbox:</span>
                      <span className="font-bold text-foreground text-sm">
                        ${((sandbox.montoAprobadoCentavos || 0) / 100).toLocaleString('es-CO')} COP
                      </span>
                    </div>
                  </CardContent>
                </Card>

                {/* Seguridad y Auditoría Global */}
                <Card className="border-border/80">
                  <CardHeader>
                    <CardTitle className="text-base font-semibold flex items-center gap-2">
                      <span className="material-symbols-outlined text-primary text-base">shield</span>
                      Seguridad y Auditoría Global
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      Control de acceso, intentos fallidos y eventos auditados.
                    </p>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="grid grid-cols-3 gap-3">
                      <div className="p-3 rounded-lg bg-emerald-500/10 text-center">
                        <div className="text-xl font-bold text-emerald-600">{seguridad.loginsExitosos}</div>
                        <div className="text-[11px] text-emerald-700 dark:text-emerald-300">Logins Exitosos</div>
                      </div>
                      <div className="p-3 rounded-lg bg-rose-500/10 text-center">
                        <div className="text-xl font-bold text-rose-600">{seguridad.loginsFallidos}</div>
                        <div className="text-[11px] text-rose-700 dark:text-rose-300">Intentos Fallidos</div>
                      </div>
                      <div className="p-3 rounded-lg bg-muted/30 text-center">
                        <div className="text-xl font-bold text-foreground">{seguridad.accionesAuditadas}</div>
                        <div className="text-[11px] text-muted-foreground">Acciones Auditadas</div>
                      </div>
                    </div>

                    <div className="p-3 rounded-lg border border-border/50 bg-muted/20 space-y-2">
                      <div className="text-xs font-semibold text-foreground flex items-center gap-1.5">
                        <span className="material-symbols-outlined text-emerald-600 text-sm">verified_user</span>
                        Postura de Seguridad Activa
                      </div>
                      <p className="text-[11px] text-muted-foreground leading-relaxed">
                        Aislamiento Zero-Trust con políticas de seguridad VPD/RLS a nivel de base de datos Oracle ATP. Los registros de auditoría en <span className="font-mono text-foreground font-semibold">AUDITORIA_LOG</span> son inmutables.
                      </p>
                    </div>
                  </CardContent>
                </Card>
              </div>
            </TabsContent>
          </Tabs>
        </>
      )}
    </div>
  );
}
