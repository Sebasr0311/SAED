import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '../components/ui/dialog.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';
import {
  CreditCard,
  Check,
  Sparkles,
  HardDrive,
  Building,
  Users,
  Calendar,
  AlertCircle,
  RefreshCw,
  ArrowUpCircle,
  Loader2
} from 'lucide-react';

export default function OrgPlanPage() {
  const [sub, setSub] = useState(null);
  const [planesCatalogo, setPlanesCatalogo] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [ciclo, setCiclo] = useState('MENSUAL');
  const [upgradeModalOpen, setUpgradeModalOpen] = useState(false);
  const [procesandoPago, setProcesandoPago] = useState(false);

  async function load() {
    try {
      setLoading(true);
      setError(null);
      const [resSub, resPlanes] = await Promise.all([
        api.get('/org/subscription'),
        api.get('/planes?solo_activos=true').catch(() => ({ data: [] }))
      ]);
      setSub(resSub?.data || resSub || {});
      const catalog = resPlanes?.data?.items || resPlanes?.data || resPlanes || [];
      setPlanesCatalogo(Array.isArray(catalog) ? catalog : []);
    } catch (err) {
      console.error('Error loading org subscription:', err);
      setError('No se pudo cargar la información de suscripción.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const formatCOP = (val) =>
    new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(val || 0);

  function cargarWidgetWompi() {
    if (window.WidgetCheckout) return Promise.resolve();
    if (window._wompiWidgetCargando) return window._wompiWidgetCargando;
    const promesa = new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = 'https://checkout.wompi.co/widget.js';
      s.async = true;
      const timer = setTimeout(() => {
        window._wompiWidgetCargando = null;
        reject(new Error('El servicio de pasarela tardó en responder. Por favor reintenta.'));
      }, 15000);
      s.onload = () => {
        clearTimeout(timer);
        window._wompiWidgetCargando = null;
        resolve();
      };
      s.onerror = () => {
        clearTimeout(timer);
        window._wompiWidgetCargando = null;
        reject(new Error('No se pudo conectar con la pasarela segura de pago Wompi.'));
      };
      document.head.appendChild(s);
    });
    window._wompiWidgetCargando = promesa;
    return promesa;
  }

  async function handleIniciarPago(tipo, idPlanDestino) {
    try {
      setProcesandoPago(true);
      await cargarWidgetWompi();

      const endpoint = tipo === 'RENOVACION' ? '/membresias/renovar' : '/membresias/upgrade';
      const payload = tipo === 'RENOVACION'
        ? { cicloFacturacion: ciclo }
        : { idPlanNuevo: idPlanDestino, cicloFacturacion: ciclo };

      const res = await api.post(endpoint, payload);
      const intencion = res?.data || res;

      if (!intencion?.referencia || !intencion?.montoCentavos) {
        throw new Error('Respuesta incompleta de la pasarela.');
      }

      if (typeof window.WidgetCheckout !== 'undefined') {
        const checkout = new window.WidgetCheckout({
          currency: 'COP',
          amountInCents: intencion.montoCentavos,
          reference: intencion.referencia,
          publicKey: intencion.publicKey,
          signature: { integrity: intencion.firmaIntegridad }
        });
        checkout.open(async (result) => {
          toast.info('Transacción enviada. Actualizando estado de la suscripción...');
          await load();
          setUpgradeModalOpen(false);
        });
      } else {
        toast.success(`Intención registrada exitosamente: ${intencion.referencia}`);
      }
    } catch (err) {
      console.error('Error al procesar pago Wompi:', err);
      const msg = err?.response?.data?.message || err?.message || 'Error al iniciar pago en pasarela';
      toast.error(msg);
    } finally {
      setProcesandoPago(false);
    }
  }

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

  // Planes disponibles para upgrade (precio superior al actual)
  const currentPrice = sub?.precioMensualCop || 0;
  const planesUpgrade = planesCatalogo.filter(
    (p) => (p.idPlan || p.ID_PLAN) !== sub?.idPlan && (p.precioMensual || p.PRECIO_MENSUAL || 0) > currentPrice
  );

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

        {/* Ciclo Selector & Acciones Comerciales */}
        <div className="flex items-center gap-3 flex-wrap">
          <div className="inline-flex rounded-lg border border-border p-1 bg-muted/30">
            <button
              onClick={() => setCiclo('MENSUAL')}
              className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
                ciclo === 'MENSUAL'
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              Mensual
            </button>
            <button
              onClick={() => setCiclo('ANUAL')}
              className={`px-3 py-1 text-xs font-medium rounded-md transition-colors flex items-center gap-1 ${
                ciclo === 'ANUAL'
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              <span>Anual</span>
              <span className="text-[10px] bg-emerald-500/15 text-emerald-600 px-1 rounded font-semibold">-20%</span>
            </button>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={() => handleIniciarPago('RENOVACION', null)}
            disabled={procesandoPago}
            className="flex items-center gap-1.5"
          >
            {procesandoPago ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <RefreshCw className="w-4 h-4 text-primary" />
            )}
            <span>Renovar Plan</span>
          </Button>

          {planesUpgrade.length > 0 && (
            <Button
              size="sm"
              onClick={() => setUpgradeModalOpen(true)}
              disabled={procesandoPago}
              className="flex items-center gap-1.5"
            >
              <ArrowUpCircle className="w-4 h-4" />
              <span>Mejorar Plan</span>
            </Button>
          )}
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
              {sub?.planDescripcion || 'Licencia corporativa para administración de propiedad horizontal.'}
            </p>
          </CardHeader>

          <CardContent className="pt-6 space-y-6 flex-grow">
            <div>
              <span className="text-xs text-muted-foreground uppercase font-semibold block">Tarifa Base</span>
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
                <span className="flex items-center gap-1.5"><CreditCard className="w-3.5 h-3.5" /> Pasarela de Pago</span>
                <span className="font-medium text-foreground">Wompi Segura (Bancolombia)</span>
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
                Garantías de Seguridad SAED Enterprise
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
                  <span>Facturación y firmas criptográficas Wompi</span>
                </div>
                <div className="flex items-center gap-2">
                  <Check className="w-4 h-4 text-emerald-600" />
                  <span>Actualización automática de entitlements</span>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Modal de Upgrade a Plan Superior */}
      <Dialog open={upgradeModalOpen} onOpenChange={setUpgradeModalOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle className="text-xl font-bold flex items-center gap-2">
              <ArrowUpCircle className="w-5 h-5 text-primary" />
              <span>Planes Disponibles para Upgrade</span>
            </DialogTitle>
            <DialogDescription>
              Seleccione el plan al que desea migrar su organización. Las tarifas se calculan automáticamente según el ciclo seleccionado ({ciclo.toLowerCase()}).
            </DialogDescription>
          </DialogHeader>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-4">
            {planesUpgrade.map((plan) => {
              const idPlan = plan.idPlan || plan.ID_PLAN;
              const precioMensual = plan.precioMensual || plan.PRECIO_MENSUAL || 0;
              const precioCiclo = ciclo === 'ANUAL'
                ? Math.round(precioMensual * 12 * 0.80)
                : precioMensual;

              return (
                <Card key={idPlan} className="border border-border/80 flex flex-col justify-between hover:border-primary/60 transition-colors">
                  <CardHeader className="pb-3">
                    <div className="flex justify-between items-center">
                      <Badge variant="outline" className="text-xs uppercase">{plan.codigo || plan.CODIGO}</Badge>
                      <span className="text-xs font-semibold text-emerald-600">Upgrade</span>
                    </div>
                    <CardTitle className="text-lg font-bold mt-1">{plan.nombre || plan.NOMBRE}</CardTitle>
                    <p className="text-xs text-muted-foreground line-clamp-2">{plan.descripcion || plan.DESCRIPCION}</p>
                  </CardHeader>
                  <CardContent className="space-y-4 pt-0">
                    <div>
                      <div className="text-2xl font-black text-foreground">{formatCOP(precioCiclo)}</div>
                      <span className="text-[11px] text-muted-foreground">
                        {ciclo === 'ANUAL' ? 'Facturado anualmente (ahorro del 20%)' : 'Facturado mensualmente'}
                      </span>
                    </div>

                    <div className="text-xs space-y-1.5 text-muted-foreground border-t pt-3">
                      <div>• Límite Propiedades: <span className="font-semibold text-foreground">{plan.limitePropiedades || plan.LIMITE_PROPIEDADES || 'Ilimitadas'}</span></div>
                      <div>• Límite Unidades: <span className="font-semibold text-foreground">{plan.limiteUnidades || plan.LIMITE_UNIDADES || 'Ilimitadas'}</span></div>
                      <div>• Límite Usuarios: <span className="font-semibold text-foreground">{plan.limiteUsuarios || plan.LIMITE_USUARIOS || 'Ilimitados'}</span></div>
                    </div>

                    <Button
                      className="w-full mt-2"
                      onClick={() => handleIniciarPago('UPGRADE', idPlan)}
                      disabled={procesandoPago}
                    >
                      {procesandoPago ? (
                        <Loader2 className="w-4 h-4 animate-spin mr-2" />
                      ) : (
                        <ArrowUpCircle className="w-4 h-4 mr-2" />
                      )}
                      <span>Seleccionar y Pagar</span>
                    </Button>
                  </CardContent>
                </Card>
              );
            })}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
