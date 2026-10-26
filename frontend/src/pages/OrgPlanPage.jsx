import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '../components/ui/dialog.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { cn, formatDate } from '../lib/utils.js';
import { toast } from 'sonner';
import {
  CreditCard,
  Check,
  HardDrive,
  Building,
  Users,
  Calendar,
  AlertCircle,
  RefreshCw,
  ArrowUpCircle,
  ArrowDownCircle,
  Loader2
} from 'lucide-react';

// Annual billing factor (20 % discount), same computation used by the plan catalog elsewhere.
const ANNUAL_FACTOR = 0.8;

function QuotaMeter({ icon: Icon, label, used, limit, percent }) {
  const pct = Math.max(0, Math.min(100, Number(percent) || 0));
  const tone = pct >= 100 ? 'bg-btn-danger' : pct >= 80 ? 'bg-btn-warn' : 'bg-btn-primary';
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className="flex items-center gap-2 font-semibold text-on-surface">
          <Icon className="h-4 w-4 text-text-secondary" aria-hidden="true" />
          {label}
        </span>
        <span className="text-xs font-semibold tabular-nums text-on-surface">
          {used || 0} / {limit || '∞'}{' '}
          <span className={cn(pct >= 80 ? 'text-tint-amber-fg' : 'text-text-muted')}>({pct}%)</span>
        </span>
      </div>
      <div
        className="h-2.5 w-full overflow-hidden rounded-[2px] bg-surface-dim"
        role="progressbar"
        aria-label={`${label}: ${pct}% utilizado`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={pct}
      >
        <div className={cn('h-full transition-[width] duration-300 ease-out', tone)} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

export default function OrgPlanPage() {
  const [sub, setSub] = useState(null);
  const [planesCatalogo, setPlanesCatalogo] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [ciclo, setCiclo] = useState('MENSUAL');
  const [upgradeModalOpen, setUpgradeModalOpen] = useState(false);
  const [procesandoPago, setProcesandoPago] = useState(false);
  const [planesError, setPlanesError] = useState(false);

  async function load() {
    try {
      setLoading(true);
      setError(null);
      setPlanesError(false);
      const [resSub, resPlanes] = await Promise.all([
        api.get('/org/subscription'),
        api.get('/planes?solo_activos=true').catch((err) => {
          // Plan changes depend on the catalog; surface its failure instead of hiding the action silently.
          console.error('Error loading plan catalog:', err);
          setPlanesError(true);
          return { data: [] };
        })
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
        checkout.open(async (_result) => {
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

  const headerBase = {
    title: 'Plan y suscripción',
    subtitle: 'Plan contratado, límites de infraestructura y consumo de cuotas de su organización.',
  };

  if (loading) {
    return (
      <PageContainer>
        <PageHeader {...headerBase} />
        <div role="status" aria-busy="true" aria-label="Cargando suscripción" className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <Skeleton className="h-80 rounded-lg" />
          <Skeleton className="h-80 rounded-lg lg:col-span-2" />
        </div>
      </PageContainer>
    );
  }

  if (error) {
    return (
      <PageContainer>
        <PageHeader {...headerBase} />
        <ErrorState title="No se pudo cargar la suscripción" message={error} onRetry={load} />
      </PageContainer>
    );
  }

  // All available plans except the current one (supports both upgrade and downgrade)
  const currentPrice = sub?.precioMensualCop || 0;
  const planesCambio = planesCatalogo.filter(
    (p) => (p.idPlan || p.ID_PLAN) !== sub?.idPlan && (p.precioMensual || p.PRECIO_MENSUAL || 0) > 0
  );
  // Keep planesUpgrade alias for button visibility
  const planesUpgrade = planesCambio;
  const estado = sub?.membresiaEstado || 'ACTIVA';

  return (
    <PageContainer>
      <PageHeader
        {...headerBase}
        badge={<Badge variant={estado === 'ACTIVA' ? 'success' : 'secondary'}>{estado}</Badge>}
        action={
          <>
            <div
              role="group"
              aria-label="Ciclo de facturación"
              className="inline-flex rounded-md border border-border bg-surface-dim p-1"
            >
              {[
                { value: 'MENSUAL', label: 'Mensual' },
                { value: 'ANUAL', label: 'Anual' },
              ].map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  aria-pressed={ciclo === opt.value}
                  onClick={() => setCiclo(opt.value)}
                  className={cn(
                    'flex min-h-9 items-center gap-1.5 rounded px-3 text-xs font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                    ciclo === opt.value
                      ? 'bg-surface text-primary-text shadow-sm'
                      : 'text-text-secondary hover:text-on-surface'
                  )}
                >
                  {opt.label}
                  {opt.value === 'ANUAL' && (
                    <span className="rounded-[2px] bg-tint-green-bg px-1 text-xs font-bold text-tint-green-fg">
                      -{Math.round((1 - ANNUAL_FACTOR) * 100)}%
                    </span>
                  )}
                </button>
              ))}
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={() => handleIniciarPago('RENOVACION', null)}
              disabled={procesandoPago}
              className="min-h-10 sm:min-h-9"
            >
              {procesandoPago ? (
                <Loader2 className="animate-spin" aria-hidden="true" />
              ) : (
                <RefreshCw aria-hidden="true" />
              )}
              Renovar plan
            </Button>

            {planesUpgrade.length > 0 && (
              <Button
                size="sm"
                onClick={() => setUpgradeModalOpen(true)}
                disabled={procesandoPago}
                className="min-h-10 sm:min-h-9"
              >
                <CreditCard aria-hidden="true" />
                Cambiar plan
              </Button>
            )}
          </>
        }
      />

      {planesError && (
        <ErrorState
          compact
          title="No se pudo cargar el catálogo de planes"
          message="Tu suscripción actual se muestra correctamente; el cambio de plan no está disponible hasta reintentar."
          onRetry={load}
        />
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="flex flex-col justify-between border-primary/40">
          <CardHeader className="border-b border-border pb-4">
            <Badge variant="info" className="self-start font-mono">
              {sub?.planCodigo || 'PLAN'}
            </Badge>
            <CardTitle className="app-display mt-2 text-[1.375rem]">{sub?.planNombre || 'Plan contratado'}</CardTitle>
            <p className="mt-1 text-xs text-text-secondary">
              {sub?.planDescripcion || 'Licencia para administración de propiedad horizontal.'}
            </p>
          </CardHeader>

          <CardContent className="flex-grow space-y-6 pt-6">
            <div>
              <span className="block text-xs font-semibold text-text-muted">Tarifa base</span>
              <div className="app-numeral mt-1 text-[1.75rem] text-on-surface">
                {formatCOP(sub?.precioMensualCop)}
                <span className="ml-1 font-sans text-xs font-normal text-text-muted">/ mes</span>
              </div>
            </div>

            <dl className="space-y-2.5 border-t border-border pt-4 text-xs">
              <div className="flex items-center justify-between gap-3 text-text-secondary">
                <dt className="flex items-center gap-1.5">
                  <Calendar className="h-3.5 w-3.5" aria-hidden="true" /> Inicio de vigencia
                </dt>
                <dd className="font-semibold tabular-nums text-on-surface">
                  {sub?.fechaInicio ? formatDate(sub.fechaInicio) : 'N/A'}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-3 text-text-secondary">
                <dt className="flex items-center gap-1.5">
                  <Calendar className="h-3.5 w-3.5" aria-hidden="true" /> Fin de vigencia
                </dt>
                <dd className="font-semibold tabular-nums text-on-surface">
                  {sub?.fechaFin ? formatDate(sub.fechaFin) : 'Indefinida'}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-3 text-text-secondary">
                <dt className="flex items-center gap-1.5">
                  <CreditCard className="h-3.5 w-3.5" aria-hidden="true" /> Medio de pago
                </dt>
                <dd className="text-right font-semibold text-on-surface">Wompi (PSE y tarjetas)</dd>
              </div>
            </dl>
          </CardContent>
        </Card>

        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader className="border-b border-border pb-4">
              <CardTitle className="text-[15px]">Capacidad y cuotas</CardTitle>
              <p className="mt-0.5 text-xs text-text-muted">Consumo actual de los recursos asignados a su organización.</p>
            </CardHeader>
            <CardContent className="space-y-6 pt-6">
              <QuotaMeter
                icon={Building}
                label="Propiedades"
                used={sub?.propiedadesUsadas}
                limit={sub?.limitePropiedades}
                percent={sub?.porcentajePropiedades}
              />
              <QuotaMeter
                icon={Building}
                label="Unidades habitacionales"
                used={sub?.unidadesUsadas}
                limit={sub?.limiteUnidades}
                percent={sub?.porcentajeUnidades}
              />
              <QuotaMeter
                icon={Users}
                label="Usuarios de plataforma"
                used={sub?.usuariosUsados}
                limit={sub?.limiteUsuarios}
                percent={sub?.porcentajeUsuarios}
              />

              <div className="flex items-center justify-between border-t border-border pt-3 text-xs text-text-secondary">
                <span className="flex items-center gap-2">
                  <HardDrive className="h-4 w-4" aria-hidden="true" />
                  Almacenamiento asignado
                </span>
                <span className="font-semibold tabular-nums text-on-surface">
                  {sub?.limiteAlmacenamientoGb ? `${sub.limiteAlmacenamientoGb} GB` : '—'}
                </span>
              </div>
            </CardContent>
          </Card>

          <Card className="bg-surface-container">
            <CardContent className="pt-6">
              <h2 className="mb-3 text-sm font-bold text-on-surface">Seguridad de la plataforma</h2>
              <ul className="grid grid-cols-1 gap-2.5 text-xs text-text-secondary sm:grid-cols-2">
                {[
                  'Aislamiento de datos por organización en la base de datos',
                  'Pista de auditoría de acciones',
                  'Pagos con firma de integridad de Wompi',
                  'Límites del plan aplicados al confirmar el pago',
                ].map((item) => (
                  <li key={item} className="flex items-start gap-2">
                    <Check className="mt-0.5 h-4 w-4 shrink-0 text-tint-green-fg" aria-hidden="true" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>
      </div>

      <Dialog open={upgradeModalOpen} onOpenChange={setUpgradeModalOpen}>
        <DialogContent className="max-h-[90dvh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Cambiar plan de suscripción</DialogTitle>
            <DialogDescription>
              Seleccione el plan al que desea migrar su organización. Los upgrades se aplican al confirmar el pago. Los
              downgrades requieren que su uso actual no exceda los límites del plan destino.
            </DialogDescription>
          </DialogHeader>

          {planesCambio.length === 0 ? (
            <EmptyState icon={CreditCard} title="No hay otros planes disponibles" subtitle="El catálogo no tiene planes alternativos activos." />
          ) : (
            <div className="mt-2 grid grid-cols-1 gap-4 md:grid-cols-2">
              {planesCambio.map((plan) => {
                const idPlan = plan.idPlan || plan.ID_PLAN;
                const precioMensual = plan.precioMensual || plan.PRECIO_MENSUAL || 0;
                const precioCiclo = ciclo === 'ANUAL'
                  ? Math.round(precioMensual * 12 * ANNUAL_FACTOR)
                  : precioMensual;
                const isUpgrade = precioMensual > currentPrice;
                const isDowngrade = precioMensual < currentPrice;

                // Check if downgrade would be blocked by current usage
                const propLimit = plan.limitePropiedades || plan.LIMITE_PROPIEDADES;
                const unitLimit = plan.limiteUnidades || plan.LIMITE_UNIDADES;
                const usrLimit = plan.limiteUsuarios || plan.LIMITE_USUARIOS;
                const usedProp = sub?.propiedadesUsadas || 0;
                const usedUnit = sub?.unidadesUsadas || 0;
                const usedUsr = sub?.usuariosUsados || 0;

                const blockedReasons = [];
                if (isDowngrade) {
                  if (propLimit && propLimit > 0 && usedProp > propLimit)
                    blockedReasons.push(`${usedProp} propiedades activas (máx. ${propLimit})`);
                  if (unitLimit && unitLimit > 0 && usedUnit > unitLimit)
                    blockedReasons.push(`${usedUnit} unidades (máx. ${unitLimit})`);
                  if (usrLimit && usrLimit > 0 && usedUsr > usrLimit)
                    blockedReasons.push(`${usedUsr} usuarios activos (máx. ${usrLimit})`);
                }
                const isBlocked = blockedReasons.length > 0;

                return (
                  <Card
                    key={idPlan}
                    className={cn(
                      'flex flex-col justify-between transition-colors duration-150',
                      isBlocked ? 'border-tint-red-fg/40' : 'hover:border-primary/60'
                    )}
                  >
                    <CardHeader className="pb-3">
                      <div className="flex items-center justify-between gap-2">
                        <Badge variant="outline" className="font-mono">{plan.codigo || plan.CODIGO}</Badge>
                        {isUpgrade ? (
                          <Badge variant="success">
                            <ArrowUpCircle className="h-3.5 w-3.5" aria-hidden="true" /> Upgrade
                          </Badge>
                        ) : (
                          <Badge variant="warning">
                            <ArrowDownCircle className="h-3.5 w-3.5" aria-hidden="true" /> Downgrade
                          </Badge>
                        )}
                      </div>
                      <CardTitle className="mt-1 text-base">{plan.nombre || plan.NOMBRE}</CardTitle>
                      <p className="line-clamp-2 text-xs text-text-secondary">{plan.descripcion || plan.DESCRIPCION}</p>
                    </CardHeader>
                    <CardContent className="space-y-4 pt-0">
                      <div>
                        <div className="app-numeral text-[1.5rem] text-on-surface">{formatCOP(precioCiclo)}</div>
                        <span className="text-xs text-text-muted">
                          {ciclo === 'ANUAL'
                            ? `Facturado anualmente (ahorro del ${Math.round((1 - ANNUAL_FACTOR) * 100)}%)`
                            : 'Facturado mensualmente'}
                        </span>
                      </div>

                      <dl className="space-y-1.5 border-t border-border pt-3 text-xs text-text-secondary">
                        <div className="flex justify-between gap-2">
                          <dt>Propiedades</dt>
                          <dd className="font-semibold tabular-nums text-on-surface">{propLimit || 'Ilimitadas'}</dd>
                        </div>
                        <div className="flex justify-between gap-2">
                          <dt>Unidades</dt>
                          <dd className="font-semibold tabular-nums text-on-surface">{unitLimit || 'Ilimitadas'}</dd>
                        </div>
                        <div className="flex justify-between gap-2">
                          <dt>Usuarios</dt>
                          <dd className="font-semibold tabular-nums text-on-surface">{usrLimit || 'Ilimitados'}</dd>
                        </div>
                      </dl>

                      {isBlocked && (
                        <div role="note" className="space-y-1 rounded-md border border-tint-red-fg/25 bg-tint-red-bg p-3 text-xs text-tint-red-fg">
                          <div className="flex items-center gap-1.5 font-bold">
                            <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" /> Downgrade bloqueado
                          </div>
                          <ul className="list-disc space-y-0.5 pl-5">
                            {blockedReasons.map((r, i) => (
                              <li key={i}>{r}</li>
                            ))}
                          </ul>
                          <p className="pt-1 text-text-secondary">Reduzca el uso antes de cambiar a este plan.</p>
                        </div>
                      )}

                      <Button
                        className="mt-2 w-full"
                        variant={isDowngrade ? 'outline' : 'default'}
                        onClick={() => handleIniciarPago('UPGRADE', idPlan)}
                        disabled={procesandoPago || isBlocked}
                      >
                        {procesandoPago ? (
                          <Loader2 className="animate-spin" aria-hidden="true" />
                        ) : isUpgrade ? (
                          <ArrowUpCircle aria-hidden="true" />
                        ) : (
                          <ArrowDownCircle aria-hidden="true" />
                        )}
                        {isBlocked ? 'Uso excede límites' : isUpgrade ? 'Seleccionar y pagar' : 'Confirmar downgrade'}
                      </Button>
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
