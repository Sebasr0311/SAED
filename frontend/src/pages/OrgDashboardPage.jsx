import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import { Building2, Home, Users, DollarSign, ShieldCheck, ArrowUpRight, TrendingUp, AlertCircle } from 'lucide-react';

export default function OrgDashboardPage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        const res = await api.get('/org/dashboard');
        setData(res?.data || res || {});
      } catch (err) {
        console.error('Error loading org dashboard:', err);
        setError('No se pudo cargar el dashboard organizacional.');
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
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6">
        <div className="bg-destructive/15 border border-destructive text-destructive px-4 py-3 rounded-lg flex items-center gap-3">
          <AlertCircle className="w-5 h-5" />
          <span>{error}</span>
        </div>
      </div>
    );
  }

  const props = data?.propiedades || { total: 0, activas: 0, inactivas: 0 };
  const units = data?.unidades || { total: 0 };
  const admins = data?.administradores || { activos: 0 };
  const users = data?.usuarios || { total: 0 };
  const sub = data?.suscripcion || {};
  const finanzas = data?.finanzas || { totalRecaudado: 0, carteraPendiente: 0 };
  const recientes = data?.propiedadesRecientes || [];

  const formatCOP = (val) =>
    new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val || 0);

  return (
    <div className="p-6 space-y-8 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              Consola de Organización
            </h1>
            <Badge variant="outline" className="bg-primary/10 text-primary border-primary/20 text-xs px-2.5 py-0.5 font-medium">
              {sub?.planNombre || 'Plan Activo'}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Supervisión institucional, límites de suscripción y cartera agregada de sus propiedades.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link to="/org/propiedades">
            <Button className="flex items-center gap-2">
              <Home className="w-4 h-4" />
              <span>Gestionar Propiedades</span>
            </Button>
          </Link>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <Card className="border border-border/80 shadow-sm hover:shadow-md transition-shadow">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Propiedades
            </CardTitle>
            <div className="p-2 bg-primary/10 text-primary rounded-lg">
              <Home className="w-5 h-5" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-extrabold text-foreground">{props.activas}</div>
            <p className="text-xs text-muted-foreground mt-1 flex items-center justify-between">
              <span>{props.total} registradas en total</span>
              <span className="font-semibold text-primary">{sub.porcentajePropiedades || 0}% límite</span>
            </p>
          </CardContent>
        </Card>

        <Card className="border border-border/80 shadow-sm hover:shadow-md transition-shadow">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Unidades Totales
            </CardTitle>
            <div className="p-2 bg-blue-500/10 text-blue-600 rounded-lg">
              <Building2 className="w-5 h-5" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-extrabold text-foreground">{units.total}</div>
            <p className="text-xs text-muted-foreground mt-1 flex items-center justify-between">
              <span>Distribuidas en copropiedades</span>
              <span className="font-semibold text-blue-600">{sub.porcentajeUnidades || 0}% límite</span>
            </p>
          </CardContent>
        </Card>

        <Card className="border border-border/80 shadow-sm hover:shadow-md transition-shadow">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Administradores
            </CardTitle>
            <div className="p-2 bg-emerald-500/10 text-emerald-600 rounded-lg">
              <Users className="w-5 h-5" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-extrabold text-foreground">{admins.activos}</div>
            <p className="text-xs text-muted-foreground mt-1">
              {users.total} usuarios totales en la organización
            </p>
          </CardContent>
        </Card>

        <Card className="border border-border/80 shadow-sm hover:shadow-md transition-shadow">
          <CardHeader className="flex flex-row items-center justify-between pb-2 space-y-0">
            <CardTitle className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Recaudo Consolidado
            </CardTitle>
            <div className="p-2 bg-emerald-500/10 text-emerald-600 rounded-lg">
              <DollarSign className="w-5 h-5" />
            </div>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground truncate">
              {formatCOP(finanzas.totalRecaudado)}
            </div>
            <p className="text-xs text-amber-600 font-medium mt-1">
              Cartera por cobrar: {formatCOP(finanzas.carteraPendiente)}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Plan Limits Consumption Progress */}
      <Card className="border border-border/80 shadow-sm">
        <CardHeader className="border-b border-border/40 pb-4">
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="text-base font-semibold text-foreground">
                Consumo de Límites del Plan SaaS
              </CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                Capacidad contratada para su organización según el plan {sub.planNombre || 'SaaS'}
              </p>
            </div>
            <Link to="/org/plan">
              <Button variant="ghost" size="sm" className="text-xs gap-1 text-primary">
                <span>Ver Plan Completo</span>
                <ArrowUpRight className="w-3.5 h-3.5" />
              </Button>
            </Link>
          </div>
        </CardHeader>
        <CardContent className="pt-6">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Propiedades */}
            <div className="space-y-2">
              <div className="flex justify-between text-xs">
                <span className="font-medium text-foreground">Propiedades</span>
                <span className="text-muted-foreground">{sub.consumoPropiedades || 0} / {sub.limitePropiedades || '∞'}</span>
              </div>
              <div className="w-full bg-secondary h-2.5 rounded-full overflow-hidden">
                <div
                  className="bg-primary h-full transition-all duration-500"
                  style={{ width: `${Math.min(100, sub.porcentajePropiedades || 0)}%` }}
                />
              </div>
              <p className="text-[11px] text-muted-foreground text-right">{sub.porcentajePropiedades || 0}% utilizado</p>
            </div>

            {/* Unidades */}
            <div className="space-y-2">
              <div className="flex justify-between text-xs">
                <span className="font-medium text-foreground">Unidades Residenciales</span>
                <span className="text-muted-foreground">{sub.consumoUnidades || 0} / {sub.limiteUnidades || '∞'}</span>
              </div>
              <div className="w-full bg-secondary h-2.5 rounded-full overflow-hidden">
                <div
                  className="bg-blue-600 h-full transition-all duration-500"
                  style={{ width: `${Math.min(100, sub.porcentajeUnidades || 0)}%` }}
                />
              </div>
              <p className="text-[11px] text-muted-foreground text-right">{sub.porcentajeUnidades || 0}% utilizado</p>
            </div>

            {/* Usuarios */}
            <div className="space-y-2">
              <div className="flex justify-between text-xs">
                <span className="font-medium text-foreground">Usuarios de Plataforma</span>
                <span className="text-muted-foreground">{sub.consumoUsuarios || 0} / {sub.limiteUsuarios || '∞'}</span>
              </div>
              <div className="w-full bg-secondary h-2.5 rounded-full overflow-hidden">
                <div
                  className="bg-emerald-600 h-full transition-all duration-500"
                  style={{ width: `${Math.min(100, sub.porcentajeUsuarios || 0)}%` }}
                />
              </div>
              <p className="text-[11px] text-muted-foreground text-right">{sub.porcentajeUsuarios || 0}% utilizado</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Recent Properties Section */}
      <Card className="border border-border/80 shadow-sm">
        <CardHeader className="flex flex-row items-center justify-between border-b border-border/40 pb-4">
          <div>
            <CardTitle className="text-base font-semibold text-foreground">
              Propiedades de la Organización
            </CardTitle>
            <p className="text-xs text-muted-foreground mt-0.5">
              Últimas copropiedades y edificios registrados
            </p>
          </div>
          <Link to="/org/propiedades">
            <Button variant="outline" size="sm" className="text-xs">
              Ver todas ({props.total})
            </Button>
          </Link>
        </CardHeader>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-muted-foreground bg-muted/30 border-b border-border">
                <tr>
                  <th className="px-6 py-3 font-semibold">ID</th>
                  <th className="px-6 py-3 font-semibold">Nombre de Copropiedad</th>
                  <th className="px-6 py-3 font-semibold">Ubicación</th>
                  <th className="px-6 py-3 font-semibold">Tipo</th>
                  <th className="px-6 py-3 font-semibold text-center">Unidades</th>
                  <th className="px-6 py-3 font-semibold text-center">Admins</th>
                  <th className="px-6 py-3 font-semibold text-center">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {recientes.length === 0 ? (
                  <tr>
                    <td colSpan="7" className="px-6 py-8 text-center text-muted-foreground">
                      No hay propiedades registradas aún.
                    </td>
                  </tr>
                ) : (
                  recientes.map((p) => (
                    <tr key={p.ID_PROPIEDAD} className="hover:bg-muted/20 transition-colors">
                      <td className="px-6 py-4 font-mono text-xs font-semibold text-muted-foreground">
                        #{p.ID_PROPIEDAD}
                      </td>
                      <td className="px-6 py-4 font-medium text-foreground">
                        {p.NOMBRE}
                      </td>
                      <td className="px-6 py-4 text-xs text-muted-foreground">
                        {p.DIRECCION || 'Sin dirección'}, {p.CIUDAD || 'N/A'}
                      </td>
                      <td className="px-6 py-4 text-xs">
                        <Badge variant="outline" className="text-[11px]">
                          {p.TIPO_PROPIEDAD_NOMBRE || 'Edificio'}
                        </Badge>
                      </td>
                      <td className="px-6 py-4 text-xs text-center font-semibold">
                        {p.TOTAL_UNIDADES || 0}
                      </td>
                      <td className="px-6 py-4 text-xs text-center font-semibold">
                        {p.TOTAL_ADMINS || 0}
                      </td>
                      <td className="px-6 py-4 text-center">
                        <Badge
                          variant={p.ESTADO === 'ACTIVA' ? 'default' : 'secondary'}
                          className={
                            p.ESTADO === 'ACTIVA'
                              ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-emerald-500/20 text-xs'
                              : 'text-xs'
                          }
                        >
                          {p.ESTADO || 'ACTIVA'}
                        </Badge>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
