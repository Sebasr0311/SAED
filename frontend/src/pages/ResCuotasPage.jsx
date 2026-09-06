import { useState, useMemo, useEffect } from 'react';
import { toast } from 'sonner';
import {
  Wallet,
  CreditCard,
  CheckCircle2,
  AlertCircle,
  Calendar,
  Clock,
  ShieldCheck,
  RefreshCw,
  Search,
  Eye,
  Sparkles,
} from 'lucide-react';

import { PageContainer } from '../components/layout/PageContainer.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Card } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';

import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate, periodoLabel } from '../lib/utils.js';

const ESTADO_CONFIG = {
  PAGADA: {
    label: 'Pagada',
    badgeVariant: 'success',
    colorClass: 'text-emerald-700 dark:text-emerald-400',
    bgClass: 'bg-emerald-50 dark:bg-emerald-950/40 border-emerald-200 dark:border-emerald-800/60',
    icon: CheckCircle2,
  },
  PENDIENTE: {
    label: 'Pendiente de Pago',
    badgeVariant: 'warning',
    colorClass: 'text-amber-700 dark:text-amber-400',
    bgClass: 'bg-amber-50 dark:bg-amber-950/40 border-amber-200 dark:border-amber-800/60',
    icon: Clock,
  },
  VENCIDA: {
    label: 'Vencida',
    badgeVariant: 'destructive',
    colorClass: 'text-rose-700 dark:text-rose-400',
    bgClass: 'bg-rose-50 dark:bg-rose-950/40 border-rose-200 dark:border-rose-800/60',
    icon: AlertCircle,
  },
  ANULADA: {
    label: 'Anulada',
    badgeVariant: 'secondary',
    colorClass: 'text-slate-600 dark:text-slate-400',
    bgClass: 'bg-slate-100 dark:bg-slate-800 border-slate-200 dark:border-slate-700',
    icon: AlertCircle,
  },
};

export default function ResCuotasPage() {
  const { user } = useAuth();
  const residentId = user?.idResidente || user?.idPersona || user?.idUsuario;

  const { data, loading, error, refetch } = useFetch(
    () => (residentId ? api.get(`/residentes/${residentId}/dashboard`) : Promise.resolve(null)),
    [residentId]
  );

  const [tabFiltro, setTabFiltro] = useState('TODAS'); // 'TODAS' | 'PENDIENTES' | 'PAGADAS'
  const [searchTerm, setSearchTerm] = useState('');
  const [modalDetalle, setModalDetalle] = useState(null);
  const [pagando, setPagando] = useState(null);

  const info = useMemo(() => data?.raw || data || {}, [data]);
  const cuotas = useMemo(() => info.cuotas || [], [info]);
  const apartamento = useMemo(() => info.apartamento || {}, [info]);

  // Métricas financieras
  const stats = useMemo(() => {
    const totalCuotas = cuotas.length;
    const pendientes = cuotas.filter((c) => c.estado === 'PENDIENTE' || c.estado === 'VENCIDA');
    const pagadas = cuotas.filter((c) => c.estado === 'PAGADA');

    const saldoPendienteTotal = pendientes.reduce(
      (sum, c) => sum + Number(c.saldoPendiente ?? c.valorTotal ?? 0),
      0
    );

    const totalPagado = pagadas.reduce(
      (sum, c) => sum + Number(c.valorTotal ?? 0),
      0
    );

    // Próxima cuota a vencer
    const proximaVencer = [...pendientes].sort(
      (a, b) => new Date(a.fechaLimite || 0) - new Date(b.fechaLimite || 0)
    )[0];

    const alDia = saldoPendienteTotal === 0;

    return {
      totalCuotas,
      pendientesCount: pendientes.length,
      pagadasCount: pagadas.length,
      saldoPendienteTotal,
      totalPagado,
      proximaVencer,
      alDia,
    };
  }, [cuotas]);

  // Cuotas filtradas
  const filteredCuotas = useMemo(() => {
    return cuotas.filter((c) => {
      if (tabFiltro === 'PENDIENTES' && c.estado !== 'PENDIENTE' && c.estado !== 'VENCIDA') {
        return false;
      }
      if (tabFiltro === 'PAGADAS' && c.estado !== 'PAGADA') {
        return false;
      }
      if (searchTerm.trim()) {
        const q = searchTerm.toLowerCase().trim();
        const per = periodoLabel(c.anio, c.mes).toLowerCase();
        const conc = (c.concepto || '').toLowerCase();
        const id = String(c.id || c.idCuota || '');
        return per.includes(q) || conc.includes(q) || id.includes(q);
      }
      return true;
    });
  }, [cuotas, tabFiltro, searchTerm]);

  // Integración Pasarela Wompi
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

  useEffect(() => {
    cargarWidgetWompi().catch(() => {});
  }, []);

  async function pollEstadoWompi(referencia) {
    // eslint-disable-next-line react-hooks/purity
    const t0 = Date.now();
    // eslint-disable-next-line react-hooks/purity
    while (Date.now() - t0 < 180000) {
      await new Promise((r) => setTimeout(r, 2500));
      try {
        const est = await api.get(`/pagos/wompi/estado?referencia=${encodeURIComponent(referencia)}`);
        const estado = est?.estado || 'PENDIENTE';
        if (['APROBADO', 'RECHAZADO', 'VENCIDO', 'ERROR'].includes(estado)) {
          if (estado === 'APROBADO') {
            toast.success('¡Pago confirmado exitosamente! Tu estado de cuenta ha sido actualizado.');
            refetch();
          } else {
            toast.error(`La transacción fue ${estado.toLowerCase()}.`);
          }
          return;
        }
      } catch {
        /* reintento en segundo plano */
      }
    }
    toast.info('Tu pago quedó en validación bancaria; te notificaremos por correo una vez finalice.');
    refetch();
  }

  async function pagarCuotaConWompi(cuota) {
    const cuotaId = cuota.id || cuota.idCuota;
    if (pagando) return;
    setPagando(cuotaId);

    const timer = setTimeout(() => {
      setPagando(null);
      toast.info('El checkout de pago se cerró o finalizó.');
    }, 60000);

    const finalizar = () => {
      clearTimeout(timer);
      setPagando(null);
      refetch();
    };

    try {
      const sol = await api.post('/pagos/wompi/solicitud', {
        concepto: 'CUOTA',
        id: cuotaId,
      });

      if (sol.idTransaccionWompi) {
        toast.info('Ya existe una transacción en proceso bancario para esta cuota.');
        await pollEstadoWompi(sol.referencia);
        finalizar();
        return;
      }

      await cargarWidgetWompi();

      const customerData = user?.email
        ? {
            email: user.email,
            fullName: user.nombreCompleto || undefined,
          }
        : undefined;

      const checkout = new window.WidgetCheckout({
        currency: 'COP',
        amountInCents: sol.montoCentavos,
        reference: sol.referencia,
        publicKey: sol.publicKey,
        signature: { integrity: sol.firmaIntegridad },
        ...(customerData ? { customerData } : {}),
      });

      checkout.open(async (result) => {
        if (result?.transaction?.id) {
          try {
            await api.post('/pagos/wompi/transaccion', {
              referencia: sol.referencia,
              idTransaccionWompi: result.transaction.id,
            });
          } catch {
            /* best effort */
          }
        }
        await pollEstadoWompi(sol.referencia);
        finalizar();
      });
    } catch (err) {
      clearTimeout(timer);
      toast.error(err.message || 'No se pudo iniciar el proceso de pago con Wompi');
      setPagando(null);
    }
  }

  return (
    <PageContainer>
      <PageHeader
        title="Mis Cuotas de Administración"
        subtitle={`Estado de cuenta, pasarela en línea y comprobantes de tu unidad ${
          apartamento?.numero || user?.unidad || ''
        }`}
        action={
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RefreshCw className="w-4 h-4 mr-1.5" />
            Actualizar
          </Button>
        }
      />

      {/* 1. Tarjetas de KPIs Financieros */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <MetricCard
          label="Saldo Total Pendiente"
          value={formatCurrency(stats.saldoPendienteTotal)}
          icon={Wallet}
          variant={stats.alDia ? 'success' : 'warning'}
          context={stats.alDia ? 'Paz y Salvo Vigente' : `${stats.pendientesCount} cuota(s) pendiente(s)`}
        />
        <MetricCard
          label="Próximo Vencimiento"
          value={
            stats.proximaVencer?.fechaLimite
              ? formatDate(stats.proximaVencer.fechaLimite)
              : 'Sin vencimientos'
          }
          icon={Calendar}
          variant="primary"
          context={
            stats.proximaVencer
              ? periodoLabel(stats.proximaVencer.anio, stats.proximaVencer.mes)
              : 'Al día en cuotas'
          }
        />
        <MetricCard
          label="Cuotas Pagadas"
          value={String(stats.pagadasCount)}
          icon={CheckCircle2}
          variant="success"
          context={`Total: ${formatCurrency(stats.totalPagado)}`}
        />
        <MetricCard
          label="Estado de Cartera"
          value={stats.alDia ? 'Al Día' : 'En Mora'}
          icon={ShieldCheck}
          variant={stats.alDia ? 'success' : 'danger'}
          context={user?.nombrePropiedad || 'Copropiedad'}
        />
      </div>

      {/* Banner de Paz y Salvo cuando está al día */}
      {stats.alDia && cuotas.length > 0 && (
        <div className="mb-6 p-4 rounded-xl border border-emerald-200 dark:border-emerald-800/60 bg-gradient-to-r from-emerald-50 to-teal-50/50 dark:from-emerald-950/20 dark:to-teal-950/10 flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-full bg-emerald-100 dark:bg-emerald-900/50 text-emerald-700 dark:text-emerald-300">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-bold text-emerald-900 dark:text-emerald-200">
                ¡Felicidades! Tu inmueble se encuentra al día
              </h4>
              <p className="text-xs text-emerald-700/80 dark:text-emerald-400 mt-0.5">
                No tienes cobros vencidos ni cuotas extraordinarias pendientes en la administración.
              </p>
            </div>
          </div>
          <Badge variant="outline" className="bg-white/80 border-emerald-300 text-emerald-800 font-semibold shrink-0">
            Paz y Salvo Activo
          </Badge>
        </div>
      )}

      {/* 2. Barra de Filtros y Búsqueda */}
      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between mb-4">
        <div className="flex gap-2 items-center flex-wrap w-full sm:w-auto">
          {[
            { id: 'TODAS', label: `Todas (${stats.totalCuotas})` },
            { id: 'PENDIENTES', label: `Pendientes (${stats.pendientesCount})` },
            { id: 'PAGADAS', label: `Pagadas (${stats.pagadasCount})` },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setTabFiltro(tab.id)}
              className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                tabFiltro === tab.id
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'bg-card border border-border text-muted-foreground hover:text-foreground hover:bg-muted/50'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="w-full sm:w-72">
          <div className="relative">
            <Search className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Buscar por período o concepto..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-9 pr-3 py-1.5 text-sm rounded-lg border border-border bg-card text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>
        </div>
      </div>

      {/* 3. Listado de Cuotas */}
      {loading ? (
        <LoadingState text="Consultando estado de cuenta con la administración..." />
      ) : error ? (
        <Card className="p-8 text-center">
          <AlertCircle className="w-8 h-8 text-destructive mx-auto mb-2" />
          <p className="text-sm font-medium text-destructive">{error.message || 'Error al cargar cuotas'}</p>
          <Button variant="outline" size="sm" className="mt-4" onClick={() => refetch()}>
            Reintentar
          </Button>
        </Card>
      ) : filteredCuotas.length === 0 ? (
        <EmptyState
          icon="receipt_long"
          title="No hay cuotas en este criterio"
          subtitle={
            tabFiltro === 'PENDIENTES'
              ? 'No tienes pagos pendientes pendientes. ¡Estás al día!'
              : 'Las cuotas generadas por la administración aparecerán aquí.'
          }
        />
      ) : (
        <div className="space-y-3">
          {filteredCuotas.map((c) => {
            const cuotaId = c.id || c.idCuota;
            const config = ESTADO_CONFIG[c.estado] || ESTADO_CONFIG.PENDIENTE;
            const Icon = config.icon;
            const esPendiente = c.estado === 'PENDIENTE' || c.estado === 'VENCIDA';
            const estaPagando = pagando === cuotaId;

            return (
              <div
                key={cuotaId}
                className="bg-card border border-border rounded-xl p-4 sm:p-5 hover:border-primary/30 transition-all shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4"
              >
                {/* Info principal */}
                <div className="flex items-start gap-3.5 min-w-0">
                  <div className={`p-2.5 rounded-xl shrink-0 ${config.bgClass}`}>
                    <Icon className={`w-5 h-5 ${config.colorClass}`} />
                  </div>
                  <div className="space-y-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <h3 className="text-base font-bold text-foreground truncate">
                        {periodoLabel(c.anio, c.mes)}
                      </h3>
                      <Badge variant={config.badgeVariant} className="text-xs">
                        {config.label}
                      </Badge>
                      {c.concepto && (
                        <span className="text-xs text-muted-foreground bg-muted px-2 py-0.5 rounded">
                          {c.concepto}
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-4 text-xs text-muted-foreground flex-wrap">
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5" />
                        Límite de Pago: <strong className="text-foreground">{formatDate(c.fechaLimite)}</strong>
                      </span>
                      {c.fechaPago && (
                        <span className="flex items-center gap-1 text-emerald-600 dark:text-emerald-400">
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          Pagado el: {formatDate(c.fechaPago)}
                        </span>
                      )}
                      <span>ID Cuota: #{cuotaId}</span>
                    </div>
                  </div>
                </div>

                {/* Importe y Acciones */}
                <div className="flex items-center justify-between md:justify-end gap-4 shrink-0 pt-3 md:pt-0 border-t md:border-t-0 border-border">
                  <div className="text-left md:text-right">
                    <div className="text-xs text-muted-foreground font-medium">Monto Total</div>
                    <div className="text-lg sm:text-xl font-black text-foreground">
                      {formatCurrency(c.valorTotal)}
                    </div>
                    {esPendiente && c.saldoPendiente != null && c.saldoPendiente !== c.valorTotal && (
                      <div className="text-xs text-amber-600 dark:text-amber-400 font-semibold">
                        Saldo: {formatCurrency(c.saldoPendiente)}
                      </div>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setModalDetalle(c)}
                      title="Ver desglose completo de la cuota"
                    >
                      <Eye className="w-4 h-4 sm:mr-1.5" />
                      <span className="hidden sm:inline">Detalles</span>
                    </Button>

                    {esPendiente && (
                      <Button
                        size="sm"
                        disabled={estaPagando}
                        onClick={() => pagarCuotaConWompi(c)}
                        className="bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm font-semibold"
                      >
                        <CreditCard className="w-4 h-4 mr-1.5" />
                        {estaPagando ? 'Procesando...' : 'Pagar con Wompi'}
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* 4. Modal de Detalle de Cuota */}
      <Modal
        open={!!modalDetalle}
        onClose={() => setModalDetalle(null)}
        title={`Detalle de Cobro: ${modalDetalle ? periodoLabel(modalDetalle.anio, modalDetalle.mes) : ''}`}
        footer={
          <div className="flex items-center justify-between w-full">
            <Button variant="outline" onClick={() => setModalDetalle(null)}>
              Cerrar
            </Button>
            {modalDetalle && (modalDetalle.estado === 'PENDIENTE' || modalDetalle.estado === 'VENCIDA') && (
              <Button
                disabled={pagando === (modalDetalle.id || modalDetalle.idCuota)}
                onClick={() => {
                  const target = modalDetalle;
                  setModalDetalle(null);
                  pagarCuotaConWompi(target);
                }}
                className="bg-emerald-600 hover:bg-emerald-700 text-white font-semibold"
              >
                <CreditCard className="w-4 h-4 mr-1.5" />
                Pagar Ahora ({formatCurrency(modalDetalle.saldoPendiente || modalDetalle.valorTotal)})
              </Button>
            )}
          </div>
        }
      >
        {modalDetalle && (
          <div className="space-y-4 text-sm">
            {/* Cabecera del cobro */}
            <div className="grid grid-cols-2 gap-3 p-3.5 rounded-xl bg-muted/50 border border-border">
              <div>
                <div className="text-xs text-muted-foreground font-medium">Unidad Residencial</div>
                <div className="text-sm font-bold text-foreground">
                  {modalDetalle.numeroApartamento || apartamento?.numero || `Unidad #${modalDetalle.idUnidad || ''}`}
                </div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground font-medium">Estado del Cobro</div>
                <div className="mt-0.5">
                  <Badge variant={ESTADO_CONFIG[modalDetalle.estado]?.badgeVariant || 'secondary'}>
                    {ESTADO_CONFIG[modalDetalle.estado]?.label || modalDetalle.estado}
                  </Badge>
                </div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground font-medium">Fecha Límite</div>
                <div className="text-sm font-semibold text-foreground">
                  {formatDate(modalDetalle.fechaLimite)}
                </div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground font-medium">Concepto</div>
                <div className="text-sm font-semibold text-foreground truncate">
                  {modalDetalle.concepto || 'Cuota de Administración'}
                </div>
              </div>
            </div>

            {/* Desglose financiero */}
            <div className="border border-border rounded-xl p-3.5 space-y-2">
              <div className="text-xs font-bold text-muted-foreground uppercase tracking-wider">
                Desglose Liquidado
              </div>
              <div className="flex justify-between text-xs py-1 border-b border-border/50">
                <span className="text-muted-foreground">Valor Base de Administración:</span>
                <span className="font-semibold text-foreground">
                  {formatCurrency(modalDetalle.valorBase || modalDetalle.valorTotal)}
                </span>
              </div>
              {modalDetalle.recargoMora && Number(modalDetalle.recargoMora) > 0 && (
                <div className="flex justify-between text-xs py-1 border-b border-border/50 text-rose-600">
                  <span>Intereses de Mora:</span>
                  <span className="font-semibold">{formatCurrency(modalDetalle.recargoMora)}</span>
                </div>
              )}
              {modalDetalle.descuentoProntoPago && Number(modalDetalle.descuentoProntoPago) > 0 && (
                <div className="flex justify-between text-xs py-1 border-b border-border/50 text-emerald-600">
                  <span>Descuento Pronto Pago:</span>
                  <span className="font-semibold">-{formatCurrency(modalDetalle.descuentoProntoPago)}</span>
                </div>
              )}
              <div className="flex justify-between text-sm pt-1.5 font-bold">
                <span>Total a Pagar:</span>
                <span className="text-primary text-base font-black">
                  {formatCurrency(modalDetalle.valorTotal)}
                </span>
              </div>
            </div>

            {/* Opciones de Pago */}
            <div className="p-3.5 rounded-xl border border-blue-100 dark:border-blue-900/40 bg-blue-50/50 dark:bg-blue-950/20 text-xs text-blue-900 dark:text-blue-300 space-y-1.5">
              <div className="font-bold flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4 text-blue-600 dark:text-blue-400" />
                Medios de Pago Autorizados
              </div>
              <p>
                Puedes pagar con tarjeta de crédito/débito o cuenta bancaria PSE directamente a través del
                botón seguro de <strong>Wompi Bancolombia</strong>. La confirmación se refleja automáticamente en
                tu historial y estado de cuenta.
              </p>
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
