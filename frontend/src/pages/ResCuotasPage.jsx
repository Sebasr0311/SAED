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
  UploadCloud,
  FileText,
} from 'lucide-react';

import { PageContainer } from '../components/layout/PageContainer.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';

import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate, periodoLabel } from '../lib/utils.js';

const ESTADO_CONFIG = {
  PAGADA: {
    label: 'Pagada',
    badgeVariant: 'success',
    colorClass: 'text-tint-green-fg',
    bgClass: 'bg-tint-green-bg border-tint-green-fg/25',
    icon: CheckCircle2,
  },
  PENDIENTE: {
    label: 'Pendiente de Pago',
    badgeVariant: 'warning',
    colorClass: 'text-tint-amber-fg',
    bgClass: 'bg-tint-amber-bg border-tint-amber-fg/25',
    icon: Clock,
  },
  VENCIDA: {
    label: 'Vencida',
    badgeVariant: 'destructive',
    colorClass: 'text-tint-red-fg',
    bgClass: 'bg-tint-red-bg border-tint-red-fg/25',
    icon: AlertCircle,
  },
  ANULADA: {
    label: 'Anulada',
    badgeVariant: 'secondary',
    colorClass: 'text-text-secondary',
    bgClass: 'bg-surface-dim border-border',
    icon: AlertCircle,
  },
};

export default function ResCuotasPage() {
  const { user } = useAuth();
  const { data: personaData } = useFetch(() => (!user?.idPersona ? api.get('/personas/me').catch(() => null) : Promise.resolve(null)), [user?.idPersona]);
  const resolvedPersonaId = user?.idPersona || user?.idResidente || personaData?.id || personaData?.idPersona || user?.idUsuario;

  const { data, loading, error, refetch } = useFetch(
    () => (resolvedPersonaId ? api.get(`/residentes/${resolvedPersonaId}/dashboard`) : Promise.resolve(null)),
    [resolvedPersonaId]
  );

  const { data: misPagosData, loading: loadingMisPagos, error: errorMisPagos, refetch: refetchMisPagos } = useFetch(
    () => api.get('/pagos'),
    []
  );

  const [tabFiltro, setTabFiltro] = useState('PENDIENTES'); // 'PENDIENTES' | 'REPORTADOS' | 'PAGADAS' | 'TODAS'
  const [searchTerm, setSearchTerm] = useState('');
  const [modalDetalle, setModalDetalle] = useState(null);
  const [pagando, setPagando] = useState(null);

  const [modalReporte, setModalReporte] = useState(null); // cuota being reported
  const [reporteForm, setReporteForm] = useState({
    valor: '',
    metodo: 'TRANSFERENCIA',
    referencia: '',
    fecha: '',
    notas: '',
    archivo: null,
  });
  const [enviandoReporte, setEnviandoReporte] = useState(false);
  const [comprobanteModal, setComprobanteModal] = useState(null);

  const misPagos = useMemo(() => {
    const list = misPagosData?.items || misPagosData || [];
    return Array.isArray(list) ? list : [];
  }, [misPagosData]);

  function abrirReporteTransferencia(cuota) {
    setModalReporte(cuota);
    setReporteForm({
      valor: String(cuota.saldoPendiente ?? cuota.valorTotal ?? ''),
      metodo: 'TRANSFERENCIA',
      referencia: '',
      fecha: new Date().toISOString().split('T')[0],
      notas: '',
      archivo: null,
    });
  }

  async function enviarReportePago(e) {
    e.preventDefault();
    if (!modalReporte || enviandoReporte) return;
    const valorNum = Number(reporteForm.valor);
    if (!valorNum || valorNum <= 0) {
      toast.error('El valor pagado debe ser mayor que 0');
      return;
    }
    const saldo = Number(modalReporte.saldoPendiente ?? modalReporte.valorTotal ?? 0);
    if (valorNum > saldo) {
      toast.error(`El valor no puede superar el saldo pendiente (${formatCurrency(saldo)})`);
      return;
    }
    if (reporteForm.archivo && reporteForm.archivo.size > 5 * 1024 * 1024) {
      toast.error('El comprobante no puede superar 5MB');
      return;
    }

    setEnviandoReporte(true);
    try {
      const cuotaId = modalReporte.id || modalReporte.idCuota;
      const formData = new FormData();
      const pagoPayload = {
        idCuota: cuotaId,
        fechaPago: reporteForm.fecha,
        valorPagado: valorNum,
        metodoPago: reporteForm.metodo,
        referencia: reporteForm.referencia,
        notas: reporteForm.notas,
      };
      formData.append('pago', new Blob([JSON.stringify(pagoPayload)], { type: 'application/json' }));
      if (reporteForm.archivo) {
        formData.append('comprobante', reporteForm.archivo);
      }
      await api.post('/pagos/manual', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      toast.success('Pago reportado exitosamente. Se encuentra pendiente de verificación por la administración.');
      setModalReporte(null);
      refetch();
      refetchMisPagos();
    } catch (err) {
      toast.error(err.message || 'Error al reportar pago');
    } finally {
      setEnviandoReporte(false);
    }
  }

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
    const t0 = Date.now();
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
        title="Mis cuotas de administración"
        subtitle={`Estado de cuenta, pago en línea y comprobantes de tu unidad ${
          apartamento?.numero || user?.unidad || ''
        }`}
        action={
          <Button
            variant="outline"
            onClick={() => {
              refetch().catch(() => {});
              refetchMisPagos().catch(() => {});
            }}
            className="min-h-10"
          >
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            Actualizar
          </Button>
        }
      />

      {/* 1. Tarjetas de KPIs Financieros */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Saldo total pendiente"
          value={loading ? '…' : error ? '—' : formatCurrency(stats.saldoPendienteTotal)}
          icon={Wallet}
          variant={stats.alDia ? 'success' : 'warning'}
          context={error ? 'No se pudo consultar' : stats.alDia ? 'Paz y salvo vigente' : `${stats.pendientesCount} cuota(s) pendiente(s)`}
        />
        <MetricCard
          label="Próximo vencimiento"
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
          label="Cuotas pagadas"
          value={loading ? '…' : String(stats.pagadasCount)}
          icon={CheckCircle2}
          variant="success"
          context={`Total: ${formatCurrency(stats.totalPagado)}`}
        />
        <MetricCard
          label="Estado de cartera"
          value={loading ? '…' : error ? '—' : stats.alDia ? 'Al día' : 'En mora'}
          icon={ShieldCheck}
          variant={error ? 'secondary' : stats.alDia ? 'success' : 'danger'}
          context={user?.nombrePropiedad || 'Copropiedad'}
        />
      </div>

      {/* Banner de Paz y Salvo cuando está al día */}
      {stats.alDia && cuotas.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-tint-green-fg/25 bg-tint-green-bg px-4 py-3">
          <div className="flex items-start gap-3">
            <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-tint-green-fg" aria-hidden="true" />
            <div>
              <h2 className="text-sm font-bold text-on-surface">Tu unidad está al día</h2>
              <p className="mt-0.5 text-xs text-text-secondary">
                No tienes cobros vencidos ni cuotas extraordinarias pendientes.
              </p>
            </div>
          </div>
          <Badge variant="success">Paz y salvo activo</Badge>
        </div>
      )}

      {/* 2. Barra de Filtros y Búsqueda */}
      <div className="flex flex-col items-stretch justify-between gap-3 sm:flex-row sm:items-center">
        <div className="flex gap-2 items-center flex-wrap w-full sm:w-auto">
          {[
            { id: 'PENDIENTES', label: `Pendientes (${stats.pendientesCount})` },
            { id: 'REPORTADOS', label: `Pagos reportados (${misPagos.length})` },
            { id: 'PAGADAS', label: `Pagadas (${stats.pagadasCount})` },
            { id: 'TODAS', label: `Todas (${stats.totalCuotas})` },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setTabFiltro(tab.id)}
              aria-pressed={tabFiltro === tab.id}
              className={`min-h-10 rounded-md px-3.5 text-xs font-semibold tabular-nums transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:min-h-9 ${
                tabFiltro === tab.id
                  ? 'bg-btn-primary text-white shadow-sm'
                  : 'border border-border bg-surface text-text-secondary hover:bg-surface-container hover:text-on-surface'
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
              aria-label="Buscar cuotas"
              className="h-10 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm text-on-surface placeholder:text-text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/25"
            />
          </div>
        </div>
      </div>

      {/* 3. Listado de Cuotas o Pagos Reportados */}
      {tabFiltro === 'REPORTADOS' ? (
        loadingMisPagos ? (
          <div className="rounded-lg border border-border bg-surface shadow-sm">
            <LoadingState message="Cargando tus pagos reportados…" />
          </div>
        ) : errorMisPagos ? (
          <ErrorState
            title="No se pudieron cargar tus pagos reportados"
            message="Revisa tu conexión e intenta de nuevo."
            onRetry={() => refetchMisPagos().catch(() => {})}
          />
        ) : misPagos.length === 0 ? (
          <EmptyState
            icon={UploadCloud}
            title="No has reportado pagos manuales"
            subtitle="Cuando reportes una consignación o transferencia con comprobante, podrás hacerle seguimiento aquí."
          />
        ) : (
          <div className="space-y-3">
            {misPagos.map((p) => {
              const esPend = p.estado === 'PENDIENTE_APROBACION';
              const esAprob = p.estado === 'APROBADO';
              const esRech = p.estado === 'RECHAZADO';
              return (
                <div
                  key={p.idPago}
                  className="flex flex-col justify-between gap-4 rounded-lg border border-border bg-surface p-4 shadow-sm sm:p-5 md:flex-row md:items-center"
                >
                  <div className="space-y-1.5">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-base text-foreground">Pago #{p.idPago}</span>
                      {esPend && (
                        <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-tint-amber-bg text-tint-amber-fg border border-tint-amber-fg/25">
                          Pendiente de verificación
                        </span>
                      )}
                      {esAprob && (
                        <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-tint-green-bg text-tint-green-fg border border-tint-green-fg/25">
                          Aprobado
                        </span>
                      )}
                      {esRech && (
                        <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-tint-red-bg text-tint-red-fg border border-tint-red-fg/25">
                          Rechazado
                        </span>
                      )}
                      <span className="text-xs text-muted-foreground bg-muted px-2 py-0.5 rounded">
                        {p.metodoPago}
                      </span>
                    </div>
                    <div className="text-xs text-muted-foreground flex items-center gap-4 flex-wrap">
                      <span>Concepto: <strong className="text-foreground">{p.conceptoCuota || 'Cuota de Administración'}</strong></span>
                      <span>Fecha: <strong className="text-foreground">{formatDate(p.fechaPago)}</strong></span>
                      {p.referenciaComprobante && <span>Ref: <strong>{p.referenciaComprobante}</strong></span>}
                    </div>
                    {esRech && p.observaciones && (
                      <div className="p-2.5 rounded-lg bg-tint-red-bg border border-tint-red-fg/25 text-xs text-tint-red-fg">
                        <strong>Motivo de rechazo:</strong> {p.observaciones}
                      </div>
                    )}
                    {esPend && (
                      <p className="text-xs text-tint-amber-fg">
                        La administración está verificando este soporte. El saldo de tu cuota se actualizará tan pronto sea aprobado.
                      </p>
                    )}
                  </div>

                  <div className="flex items-center justify-between md:justify-end gap-4 shrink-0 pt-3 md:pt-0 border-t md:border-t-0 border-border">
                    <div className="text-left md:text-right">
                      <div className="text-xs text-muted-foreground font-medium">Monto Reportado</div>
                      <div className="app-numeral text-xl text-on-surface">
                        {formatCurrency(p.montoTotal)}
                      </div>
                    </div>
                    {p.comprobanteUrl && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setComprobanteModal(p)}
                      >
                        <FileText className="w-4 h-4 mr-1.5" />
                        Ver soporte
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )
      ) : loading ? (
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState message="Consultando tu estado de cuenta…" />
        </div>
      ) : error ? (
        <ErrorState
          title="No se pudo consultar tu estado de cuenta"
          message="Revisa tu conexión e intenta de nuevo."
          onRetry={() => refetch().catch(() => {})}
        />
      ) : filteredCuotas.length === 0 ? (
        <EmptyState
          icon={FileText}
          title="No hay cuotas en este criterio"
          subtitle={
            tabFiltro === 'PENDIENTES'
              ? 'No tienes cuotas pendientes. Estás al día.'
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
                className="flex flex-col justify-between gap-4 rounded-lg border border-border bg-surface p-4 shadow-sm transition-colors duration-150 hover:border-primary/40 sm:p-5 md:flex-row md:items-center"
              >
                {/* Info principal */}
                <div className="flex items-start gap-3.5 min-w-0">
                  <div className={`shrink-0 rounded-md border p-2.5 ${config.bgClass}`}>
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
                        <span className="flex items-center gap-1 text-tint-green-fg">
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
                    <div className="app-numeral text-xl text-on-surface">
                      {formatCurrency(c.valorTotal)}
                    </div>
                    {esPendiente && c.saldoPendiente != null && c.saldoPendiente !== c.valorTotal && (
                      <div className="text-xs text-tint-amber-fg font-semibold">
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
                      <>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => abrirReporteTransferencia(c)}
                          className="border-tint-amber-fg/25 text-tint-amber-fg hover:bg-tint-amber-bg"
                          title="Reportar comprobante de consignación o transferencia bancaria"
                        >
                          <UploadCloud className="w-4 h-4 mr-1.5" />
                          <span className="hidden sm:inline">Reportar transferencia</span>
                          <span className="sm:hidden">Reportar</span>
                        </Button>
                        <Button
                          size="sm"
                          disabled={estaPagando}
                          onClick={() => pagarCuotaConWompi(c)}
                          className="min-h-10 sm:min-h-8"
                        >
                          <CreditCard className="h-4 w-4" aria-hidden="true" />
                          {estaPagando ? 'Procesando…' : 'Pagar con Wompi'}
                        </Button>
                      </>
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
        title={`Detalle de cobro: ${modalDetalle ? periodoLabel(modalDetalle.anio, modalDetalle.mes) : ''}`}
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
              >
                <CreditCard className="h-4 w-4" aria-hidden="true" />
                Pagar ahora ({formatCurrency(modalDetalle.saldoPendiente || modalDetalle.valorTotal)})
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
              <div className="text-xs font-bold text-muted-foreground">
                Desglose liquidado
              </div>
              <div className="flex justify-between text-xs py-1 border-b border-border/50">
                <span className="text-muted-foreground">Valor base de administración:</span>
                <span className="font-semibold text-foreground">
                  {formatCurrency(modalDetalle.valorBase || modalDetalle.valorTotal)}
                </span>
              </div>
              {modalDetalle.recargoMora && Number(modalDetalle.recargoMora) > 0 && (
                <div className="flex justify-between text-xs py-1 border-b border-border/50 text-tint-red-fg">
                  <span>Intereses de mora:</span>
                  <span className="font-semibold">{formatCurrency(modalDetalle.recargoMora)}</span>
                </div>
              )}
              {modalDetalle.descuentoProntoPago && Number(modalDetalle.descuentoProntoPago) > 0 && (
                <div className="flex justify-between text-xs py-1 border-b border-border/50 text-tint-green-fg">
                  <span>Descuento pronto pago:</span>
                  <span className="font-semibold">-{formatCurrency(modalDetalle.descuentoProntoPago)}</span>
                </div>
              )}
              <div className="flex justify-between text-sm pt-1.5 font-bold">
                <span>Total a pagar:</span>
                <span className="text-primary text-base font-black">
                  {formatCurrency(modalDetalle.valorTotal)}
                </span>
              </div>
            </div>

            {/* Opciones de Pago */}
            <div className="p-3.5 rounded-xl border border-tint-blue-fg/25 bg-tint-blue-bg text-xs text-tint-blue-fg space-y-1.5">
              <div className="font-bold flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4 text-tint-blue-fg" />
                Medios de pago autorizados
              </div>
              <p>
                Puedes pagar en línea con PSE o tarjeta a través de <strong>Wompi</strong>, o reportar una consignación bancaria adjuntando el comprobante para revisión y aprobación de la administración.
              </p>
            </div>
          </div>
        )}
      </Modal>

      {/* 5. Modal Reportar Pago Manual / Transferencia */}
      <Modal
        open={!!modalReporte}
        onClose={() => {
          if (!enviandoReporte) setModalReporte(null);
        }}
        title={`Reportar pago por transferencia — cuota #${modalReporte?.id || modalReporte?.idCuota || ''}`}
        footer={
          <div className="flex items-center justify-between w-full">
            <Button
              variant="outline"
              onClick={() => setModalReporte(null)}
              disabled={enviandoReporte}
            >
              Cancelar
            </Button>
            <Button
              onClick={enviarReportePago}
              disabled={enviandoReporte}
              className="bg-btn-warn hover:bg-btn-warn-hover text-white font-semibold"
            >
              {enviandoReporte ? 'Enviando comprobante…' : 'Enviar a aprobación'}
            </Button>
          </div>
        }
      >
        {modalReporte && (
          <form onSubmit={enviarReportePago} className="space-y-3.5 text-sm">
            <div className="p-3 rounded-lg bg-tint-amber-bg border border-tint-amber-fg/25 text-xs text-tint-amber-fg">
              <strong>Importante:</strong> el pago quedará en estado <em>pendiente de aprobación</em>. El saldo de tu cuota no se descontará hasta que el administrador verifique y apruebe el soporte.
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Input
                id="reporteValor"
                label="Monto pagado (COP)"
                type="number"
                step="0.01"
                inputMode="decimal"
                required
                value={reporteForm.valor}
                onChange={(e) => setReporteForm((f) => ({ ...f, valor: e.target.value }))}
              />
              <Input
                id="reporteFecha"
                label="Fecha de pago"
                type="date"
                required
                value={reporteForm.fecha}
                onChange={(e) => setReporteForm((f) => ({ ...f, fecha: e.target.value }))}
              />
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Select
                id="reporteMetodo"
                label="Método de pago"
                value={reporteForm.metodo}
                onChange={(e) => setReporteForm((f) => ({ ...f, metodo: e.target.value }))}
              >
                <option value="TRANSFERENCIA">Transferencia bancaria</option>
                <option value="CONSIGNACION">Consignación bancaria</option>
                <option value="EFECTIVO">Efectivo</option>
                <option value="OTRO">Otro medio</option>
              </Select>
              <Input
                id="reporteReferencia"
                label="Número de aprobación o referencia"
                type="text"
                placeholder="Ej. TRANSF-091823"
                value={reporteForm.referencia}
                onChange={(e) => setReporteForm((f) => ({ ...f, referencia: e.target.value }))}
              />
            </div>

            <div>
              <label htmlFor="reporteArchivo" className="mb-1 block text-xs font-semibold text-on-surface">
                Adjuntar comprobante (JPG, PNG, WEBP o PDF, máx. 5 MB)
              </label>
              <input
                id="reporteArchivo"
                type="file"
                accept="image/jpeg,image/png,image/webp,application/pdf"
                onChange={(e) => {
                  const file = e.target.files?.[0] || null;
                  setReporteForm((f) => ({ ...f, archivo: file }));
                }}
                className="w-full text-xs text-text-secondary file:mr-3 file:min-h-9 file:rounded file:border file:border-border file:bg-surface-container file:px-3 file:text-xs file:font-semibold file:text-on-surface hover:file:bg-surface-dim"
              />
            </div>

            <Textarea
              id="reporteNotas"
              label="Observaciones"
              rows={2}
              placeholder="Información adicional sobre la consignación…"
              value={reporteForm.notas}
              onChange={(e) => setReporteForm((f) => ({ ...f, notas: e.target.value }))}
            />
          </form>
        )}
      </Modal>

      {/* 6. Modal para Visualizar Comprobante Propio */}
      <Modal
        open={!!comprobanteModal}
        onClose={() => setComprobanteModal(null)}
        title={`Comprobante de Pago #${comprobanteModal?.idPago || ''}`}
        size="lg"
      >
        {comprobanteModal && (
          <div className="text-center p-3">
            <div className="mb-3 text-xs text-muted-foreground">
              Monto: <strong>{formatCurrency(comprobanteModal.montoTotal)}</strong> | Método: <strong>{comprobanteModal.metodoPago}</strong> | Referencia: <strong>{comprobanteModal.referenciaComprobante || 'N/A'}</strong>
            </div>
            {comprobanteModal.comprobanteUrl?.toLowerCase().endsWith('.pdf') ? (
              <iframe
                src={`/api/v1/pagos/${comprobanteModal.idPago}/comprobante`}
                title="Comprobante PDF"
                style={{ width: '100%', height: '480px', border: 'none', borderRadius: '8px' }}
              />
            ) : (
              <img
                src={`/api/v1/pagos/${comprobanteModal.idPago}/comprobante`}
                alt="Comprobante de pago"
                style={{ maxWidth: '100%', maxHeight: '480px', margin: '0 auto', borderRadius: '8px', objectFit: 'contain' }}
              />
            )}
            <div className="mt-4">
              <a
                href={`/api/v1/pagos/${comprobanteModal.idPago}/comprobante`}
                target="_blank"
                rel="noreferrer"
                className="inline-block text-xs px-3.5 py-2 rounded-lg border border-border bg-card text-foreground font-semibold hover:bg-muted/50"
              >
                Descargar comprobante original
              </a>
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
