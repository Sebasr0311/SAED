import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import { CreditCard, Check, Sparkles, HardDrive, Building, Users, Calendar, AlertCircle } from 'lucide-react';

export default function OrgPlanPage() {
  const [sub, setSub] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        setError(null);
        const res = await api.get('/org/subscription');
        setSub(res?.data || res || {});
      } catch (err) {
        console.error('Error loading org subscription:', err);
        setError('No se pudo cargar la información de suscripción.');
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const formatCOP = (val) =>
    new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val || 0);

  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <Skeleton className="h-8 w-64 mb-2" />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <Skeleton className="h-80 md:col-span-1 rounded-xl" />
          <Skeleton className="h-80 md:col-span-2 rounded-xl" />
        </div>
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

  return (
    <div className="p-6 space-y-8 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              Plan y Suscripción SaaS
            </h1>
            <Badge
              variant={sub?.membresiaEstado === 'ACTIVA' ? 'default' : 'secondary'}
              className={
                sub?.membresiaEstado === 'ACTIVA'
                  ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-emerald-500/20 text-xs px-2.5 py-0.5'
                  : 'text-xs px-2.5 py-0.5'
              }
            >
              {sub?.membresiaEstado || 'ACTIVA'}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Detalles de su plan contratado, límites de infraestructura y consumo de cuotas.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Active Plan Card */}
        <Card className="border-primary/40 shadow-md bg-card flex flex-col justify-between">
          <CardHeader className="border-b border-border/40 pb-4">
            <div className="flex items-center justify-between">
              <Badge variant="outline" className="bg-primary/10 text-primary border-primary/20 text-xs font-semibold uppercase">
                {sub?.planCodigo || 'PLAN_ACTIVO'}
              </Badge>
              <Sparkles className="w-5 h-5 text-primary" />
            </div>
            <CardTitle className="text-2xl font-extrabold text-foreground mt-2">
              {sub?.planNombre || 'Plan Contratado'}
            </CardTitle>
            <p className="text-xs text-muted-foreground mt-1">
              {sub?.planDescripcion || 'Licencia multi-propiedad para administración corporativa.'}
            </p>
          </CardHeader>

          <CardContent className="pt-6 space-y-6 flex-grow">
            <div>
              <span className="text-xs text-muted-foreground uppercase font-semibold block">Tarifa Mensual</span>
              <div className="text-3xl font-extrabold text-foreground mt-1">
                {formatCOP(sub?.precioMensualCop)}
                <span className="text-xs font-normal text-muted-foreground ml-1">/ mes</span>
              </div>
            </div>

            <div className="space-y-2 border-t border-border/40 pt-4 text-xs">
              <div className="flex items-center justify-between text-muted-foreground">
                <span className="flex items-center gap-1.5"><Calendar className="w-3.5 h-3.5" /> Inicio Vigencia</span>
                <span className="font-medium text-foreground">
                  {sub?.fechaInicio ? new Date(sub.fechaInicio).toLocaleDateString('es-CO') : 'N/A'}
                </span>
              </div>
              <div className="flex items-center justify-between text-muted-foreground">
                <span className="flex items-center gap-1.5"><Calendar className="w-3.5 h-3.5" /> Fin Vigencia</span>
                <span className="font-medium text-foreground">
                  {sub?.fechaFin ? new Date(sub.fechaFin).toLocaleDateString('es-CO') : 'Indefinida'}
                </span>
              </div>
              <div className="flex items-center justify-between text-muted-foreground">
                <span className="flex items-center gap-1.5"><CreditCard className="w-3.5 h-3.5" /> Tipo Periodo</span>
                <span className="font-medium text-foreground">{sub?.tipoPeriodo || 'MENSUAL'}</span>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Resource Limits & Metering */}
        <div className="lg:col-span-2 space-y-6">
          <Card className="border border-border/80 shadow-sm">
            <CardHeader className="border-b border-border/40 pb-4">
              <CardTitle className="text-base font-semibold text-foreground">
                Capacidad y Cuotas de Infraestructura
              </CardTitle>
              <p className="text-xs text-muted-foreground mt-0.5">
                Consumo en tiempo real de los recursos asignados a su organización.
              </p>
            </CardHeader>
            <CardContent className="pt-6 space-y-6">
              {/* Propiedades */}
              <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="font-medium text-foreground flex items-center gap-2">
                    <Building className="w-4 h-4 text-primary" />
                    <span>Límite de Propiedades</span>
                  </span>
                  <span className="text-xs font-semibold text-foreground">
                    {sub?.propiedadesUsadas || 0} / {sub?.limitePropiedades || '∞'} ({sub?.porcentajePropiedades || 0}%)
                  </span>
                </div>
                <div className="w-full bg-secondary h-3 rounded-full overflow-hidden">
                  <div
                    className="bg-primary h-full transition-all duration-500"
                    style={{ width: `${Math.min(100, sub?.porcentajePropiedades || 0)}%` }}
                  />
                </div>
              </div>

              {/* Unidades */}
              <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="font-medium text-foreground flex items-center gap-2">
                    <Building className="w-4 h-4 text-blue-600" />
                    <span>Límite de Unidades Habitacionales</span>
                  </span>
                  <span className="text-xs font-semibold text-foreground">
                    {sub?.unidadesUsadas || 0} / {sub?.limiteUnidades || '∞'} ({sub?.porcentajeUnidades || 0}%)
                  </span>
                </div>
                <div className="w-full bg-secondary h-3 rounded-full overflow-hidden">
                  <div
                    className="bg-blue-600 h-full transition-all duration-500"
                    style={{ width: `${Math.min(100, sub?.porcentajeUnidades || 0)}%` }}
                  />
                </div>
              </div>

              {/* Usuarios */}
              <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="font-medium text-foreground flex items-center gap-2">
                    <Users className="w-4 h-4 text-emerald-600" />
                    <span>Límite de Usuarios de Plataforma</span>
                  </span>
                  <span className="text-xs font-semibold text-foreground">
                    {sub?.usuariosUsados || 0} / {sub?.limiteUsuarios || '∞'} ({sub?.porcentajeUsuarios || 0}%)
                  </span>
                </div>
                <div className="w-full bg-secondary h-3 rounded-full overflow-hidden">
                  <div
                    className="bg-emerald-600 h-full transition-all duration-500"
                    style={{ width: `${Math.min(100, sub?.porcentajeUsuarios || 0)}%` }}
                  />
                </div>
              </div>

              {/* Almacenamiento */}
              <div className="pt-2 flex items-center justify-between text-xs text-muted-foreground border-t border-border/40">
                <span className="flex items-center gap-2">
                  <HardDrive className="w-4 h-4 text-purple-600" />
                  <span>Almacenamiento en la Nube Asignado</span>
                </span>
                <span className="font-semibold text-foreground">{sub?.limiteAlmacenamientoGb || 10} GB</span>
              </div>
            </CardContent>
          </Card>

          {/* Plan Features Guarantee */}
          <Card className="border border-border/80 shadow-sm bg-muted/10">
            <CardContent className="pt-6">
              <h4 className="text-sm font-semibold text-foreground mb-3">
                Beneficios Incluidos en su Plan
              </h4>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 text-xs text-muted-foreground">
                <div className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-emerald-600" />
                  <span>Aislamiento de datos con Oracle RLS</span>
                </div>
                <div className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-emerald-600" />
                  <span>Pista de auditoría inmutable</span>
                </div>
                <div className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-emerald-600" />
                  <span>Gestión de múltiples administradores</span>
                </div>
                <div className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-emerald-600" />
                  <span>Pasarela de pagos Wompi integrada</span>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
