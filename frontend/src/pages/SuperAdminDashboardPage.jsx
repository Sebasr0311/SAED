import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';

export default function SuperAdminDashboardPage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

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

  const orgs = data?.organizaciones || { total: 0, activas: 0, inactivas: 0 };
  const props = data?.propiedades || { total: 0, activas: 0 };
  const users = data?.usuarios || { total: 0, activos: 0, desgloseRoles: [] };
  const plans = data?.planesMembresias || { planesDisponibles: 3, membresiasActivas: 0, ingresosMensualesEstimados: 0 };
  const plat = data?.plataforma || { estado: 'OPTIMO', version: 'SAED 2.0.0-PROD', motorBD: 'Oracle Cloud ATP 23ai' };

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
          <CardHeader>
            <CardTitle className="text-base font-semibold flex items-center gap-2">
              <span className="material-symbols-outlined text-primary">badge</span>
              Distribución de Usuarios por Rol
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {users.desgloseRoles && users.desgloseRoles.length > 0 ? (
              users.desgloseRoles.map((r, i) => (
                <div key={i} className="flex items-center justify-between p-3 rounded-lg bg-muted/40 hover:bg-muted/70 transition-colors">
                  <div className="flex items-center gap-3">
                    <span className="w-2.5 h-2.5 rounded-full bg-primary"></span>
                    <span className="font-medium text-sm text-foreground">{r.ROL || 'SIN ROL'}</span>
                  </div>
                  <Badge variant="secondary" className="font-bold">
                    {r.CANTIDAD} usuarios
                  </Badge>
                </div>
              ))
            ) : (
              <div className="text-center py-6 text-sm text-muted-foreground">
                No hay desglose disponible en este momento.
              </div>
            )}
          </CardContent>
        </Card>

        {/* Estado de la Infraestructura */}
        <Card className="border-border/80">
          <CardHeader>
            <CardTitle className="text-base font-semibold flex items-center gap-2">
              <span className="material-symbols-outlined text-primary">dns</span>
              Infraestructura & Seguridad SaaS
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between p-3 rounded-lg bg-muted/40">
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-muted-foreground">cloud</span>
                <span className="text-sm text-foreground">Motor de Base de Datos</span>
              </div>
              <span className="text-sm font-semibold text-foreground">{plat.motorBD}</span>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg bg-muted/40">
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-muted-foreground">security</span>
                <span className="text-sm text-foreground">Aislamiento Multi-Tenant</span>
              </div>
              <span className="text-sm font-semibold text-emerald-600 dark:text-emerald-400">{plat.seguridad}</span>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg bg-muted/40">
              <div className="flex items-center gap-3">
                <span className="material-symbols-outlined text-muted-foreground">deployed_code</span>
                <span className="text-sm text-foreground">Versión del Backend</span>
              </div>
              <Badge variant="outline">{plat.version}</Badge>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
