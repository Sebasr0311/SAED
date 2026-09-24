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
  UploadCloud,
  FileText,
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

  const { data: misPagosData, loading: loadingMisPagos, refetch: refetchMisPagos } = useFetch(
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
            { id: 'PENDIENTES', label: `Pendientes (${stats.pendientesCount})` },
            { id: 'REPORTADOS', label: `Pagos Reportados (${misPagos.length})` },
            { id: 'PAGADAS', label: `Pagadas (${stats.pagadasCount})` },
            { id: 'TODAS', label: `Todas (${stats.totalCuotas})` },
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

      {/* 3. Listado de Cuotas o Pagos Reportados */}
      {tabFiltro === 'REPORTADOS' ? (
        loadingMisPagos ? (
          <LoadingState text="Cargando tus pagos reportados..." />
        ) : misPagos.length === 0 ? (
          <EmptyState
            icon="payments"
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
                  className="bg-card border border-border rounded-xl p-4 sm:p-5 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4"
                >
                  <div className="space-y-1.5">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-bold text-base text-foreground">Pago #{p.idPago}</span>
                      {esPend && (
                        <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-800 border border-amber-300">
                          Pendiente de Verificación
                        </span>
                      )}
                      {esAprob && (
                        <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-emerald-100 text-emerald-800 border border-emerald-300">
                          Aprobado
                        </span>
                      )}
                      {esRech && (
                        <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-rose-100 text-rose-800 border border-rose-300">
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
                      <div className="p-2.5 rounded-lg bg-rose-50 dark:bg-rose-950/30 border border-rose-200 dark:border-rose-900 text-xs text-rose-800 dark:text-rose-300">
                        <strong>Motivo de rechazo:</strong> {p.observaciones}
                      </div>
                    )}
                    {esPend && (
                      <p className="text-xs text-amber-700/90 dark:text-amber-400">
                        La administración está verificando este soporte. El saldo de tu cuota se actualizará tan pronto sea aprobado.
                      </p>
                    )}
                  </div>

                  <div className="flex items-center justify-between md:justify-end gap-4 shrink-0 pt-3 md:pt-0 border-t md:border-t-0 border-border">
                    <div className="text-left md:text-right">
                      <div className="text-xs text-muted-foreground font-medium">Monto Reportado</div>
                      <div className="text-lg font-black text-foreground">
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
                        Ver Soporte
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )
      ) : loading ? (
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
                      <>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => abrirReporteTransferencia(c)}
                          className="border-amber-500/50 text-amber-700 dark:text-amber-400 hover:bg-amber-50 dark:hover:bg-amber-950/30"
                          title="Reportar comprobante de consignación o transferencia bancaria"
                        >
                          <UploadCloud className="w-4 h-4 mr-1.5" />
                          <span className="hidden sm:inline">Reportar Transferencia</span>
                          <span className="sm:hidden">Reportar</span>
                        </Button>
                        <Button
                          size="sm"
                          disabled={estaPagando}
                          onClick={() => pagarCuotaConWompi(c)}
                          className="bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm font-semibold"
                        >
                          <CreditCard className="w-4 h-4 mr-1.5" />
                          {estaPagando ? 'Procesando...' : 'Pagar con Wompi'}
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
                Puedes pagar en línea con tarjeta o PSE vía <strong>Wompi</strong>, o reportar una consignación bancaria adjuntando el comprobante para revisión y aprobación de la administración.
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
        title={`Reportar Pago por Transferencia — Cuota #${modalReporte?.id || modalReporte?.idCuota || ''}`}
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
              className="bg-amber-600 hover:bg-amber-700 text-white font-semibold"
            >
              {enviandoReporte ? 'Enviando Comprobante...' : 'Enviar a Aprobación'}
            </Button>
          </div>
        }
      >
        {modalReporte && (
          <form onSubmit={enviarReportePago} className="space-y-3.5 text-sm">
            <div className="p-3 rounded-lg bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 text-xs text-amber-800 dark:text-amber-300">
              <strong>Nota Importante:</strong> El pago ingresará en estado <em>Pendiente de Aprobación</em>. El saldo de tu cuota no se descontará hasta que el administrador verifique y apruebe el soporte.
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold mb-1">Monto Pagado ($)</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  value={reporteForm.valor}
                  onChange={(e) => setReporteForm((f) => ({ ...f, valor: e.target.value }))}
                  className="w-full p-2 text-sm rounded border border-border bg-card text-foreground"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold mb-1">Fecha de Pago</label>
                <input
                  type="date"
                  required
                  value={reporteForm.fecha}
                  onChange={(e) => setReporteForm((f) => ({ ...f, fecha: e.target.value }))}
                  className="w-full p-2 text-sm rounded border border-border bg-card text-foreground"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold mb-1">Método de Pago</label>
                <select
                  value={reporteForm.metodo}
                  onChange={(e) => setReporteForm((f) => ({ ...f, metodo: e.target.value }))}
                  className="w-full p-2 text-sm rounded border border-border bg-card text-foreground"
                >
                  <option value="TRANSFERENCIA">Transferencia Bancaria</option>
                  <option value="CONSIGNACION">Consignación Bancaria</option>
                  <option value="EFECTIVO">Efectivo</option>
                  <option value="OTRO">Otro Medio</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold mb-1">Número de Aprobación / Ref</label>
                <input
                  type="text"
                  placeholder="Ej. TRANSF-091823"
                  value={reporteForm.referencia}
                  onChange={(e) => setReporteForm((f) => ({ ...f, referencia: e.target.value }))}
                  className="w-full p-2 text-sm rounded border border-border bg-card text-foreground"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold mb-1">
                Adjuntar Comprobante (JPG, PNG, WEBP o PDF - Máx 5MB)
              </label>
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp,application/pdf"
                onChange={(e) => {
                  const file = e.target.files?.[0] || null;
                  setReporteForm((f) => ({ ...f, archivo: file }));
                }}
                className="w-full text-xs file:mr-3 file:py-1.5 file:px-3 file:rounded file:border-0 file:text-xs file:bg-muted file:text-foreground hover:file:bg-muted/80"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold mb-1">Observaciones / Notas</label>
              <textarea
                rows={2}
                placeholder="Indica información adicional sobre la consignación..."
                value={reporteForm.notas}
                onChange={(e) => setReporteForm((f) => ({ ...f, notas: e.target.value }))}
                className="w-full p-2 text-xs rounded border border-border bg-card text-foreground"
              />
            </div>
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
                Descargar Comprobante Original
              </a>
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
