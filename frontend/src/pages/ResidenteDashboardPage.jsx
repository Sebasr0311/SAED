import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  ArrowRight,
  Bell,
  Building2,
  Calendar,
  CheckCircle2,
  ChevronRight,
  Clock,
  Copy,
  CreditCard,
  ExternalLink,
  Eye,
  FileText,
  Home,
  Mail,
  MessageSquare,
  Package,
  Phone,
  Plus,
  QrCode,
  RefreshCw,
  Send,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Users,
  UserPlus,
  Wallet,
} from 'lucide-react';
import { toast } from 'sonner';

import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useTenant } from '../lib/TenantContext.jsx';
import { useFetch } from '../lib/hooks.js';
import {
  formatCurrency,
  formatDate,
  formatDateTime,
  imageSrc,
  cn,
} from '../lib/utils.js';

import { PageContainer } from '../components/layout/PageContainer.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { LocalQRCode } from '../components/ui/LocalQRCode.jsx';
import { KpiSummaryPanel } from '../components/KpiSummaryPanel.jsx';

const MESES_W = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'
];

/**
 * ResidenteDashboardPage 2.0 — Centro Operativo del Residente
 * Foco exclusivo en operaciones del día a día: pagos Wompi, pases QR,
 * paquetería en portería, solicitudes PQRS en curso y avisos de comunidad.
 * (La ficha técnica estática del inmueble vive en Mi Perfil para evitar redundancia).
 */
export default function ResidenteDashboardPage() {
  const { user } = useAuth();
  const tenant = useTenant();
  const navigate = useNavigate();
  const isConviviente =
    user?.rol === 'RESIDENTE_CONVIVENCIA' ||
    user?.rolCodigo === 'RESIDENTE_CONVIVENCIA' ||
    user?.tipoResidente === 'CONVIVIENTE' ||
    user?.tipoRelacion === 'CONVIVIENTE';
  const residentId = user?.idPersona || user?.idResidente || user?.idUsuario;

  // 1. Perfil del residente
  const { data: perfilData, loading: loadingPerfil, refetch: refetchPerfil } = useFetch(
    () => api.get('/personas/me').catch(() => (residentId ? api.get(`/personas/${residentId}`) : Promise.resolve(null))),
    [residentId]
  );
  const perfil = useMemo(() => perfilData?.raw || perfilData || {}, [perfilData]);
  const resolvedPersonaId = perfil.id || perfil.idPersona || user?.idPersona || user?.idResidente || residentId;

  const nombreResidente = useMemo(() => {
    const pNombre = perfil.primerNombre || perfil.nombres;
    const sNombre = perfil.segundoNombre || '';
    const pApellido = perfil.primerApellido || perfil.apellidos;
    const sApellido = perfil.segundoApellido || '';
    const fullFromPerfil = `${pNombre || ''} ${sNombre} ${pApellido || ''} ${sApellido}`.replace(/\s+/g, ' ').trim();
    if (fullFromPerfil) return fullFromPerfil;
    if (user?.nombreCompleto) return user.nombreCompleto;
    if (user?.nombreUsuario || user?.username) return `@${user.nombreUsuario || user.username}`;
    return 'Residente';
  }, [perfil, user]);

  const iniciales = useMemo(() => {
    const partes = nombreResidente.split(' ').filter(Boolean);
    if (!partes.length) return 'RE';
    return (partes[0][0] + (partes[1]?.[0] || '')).toUpperCase();
  }, [nombreResidente]);

  // 2. Dashboard financiero y de unidad (Solo residente titular)
  const { data: dashData, loading: loadingDash, error: errorDash, refetch: refetchDashboard } = useFetch(
    () => (!isConviviente && resolvedPersonaId ? api.get(`/residentes/${resolvedPersonaId}/dashboard`) : Promise.resolve(null)),
    [resolvedPersonaId, isConviviente]
  );
  const dashboard = useMemo(() => dashData?.raw || dashData || {}, [dashData]);
  const aptoInfo = useMemo(() => dashboard.apartamento || {}, [dashboard]);
  const cuotas = useMemo(() => dashboard.cuotas || [], [dashboard]);

  // 3. Ficha básica de la unidad para el saludo
  const unitId =
    dashboard.idUnidad || user?.idUnidad || perfil.idApartamento || perfil.idUnidad || aptoInfo.idApartamento || aptoInfo.id;
  const { data: unitData, loading: loadingUnit, refetch: refetchUnit } = useFetch(
    () => (!dashboard.identificadorUnidad && unitId ? api.get(`/units/${unitId}`) : Promise.resolve(null)),
    [unitId, dashboard.identificadorUnidad]
  );
  const u = useMemo(() => unitData?.raw || unitData || {}, [unitData]);

  const rawNumeroApto =
    dashboard.identificadorUnidad ||
    u.identificador ||
    u.numero ||
    aptoInfo.numero ||
    aptoInfo.identificador ||
    perfil.numeroApartamento ||
    user?.identificadorUnidad ||
    tenant?.activeUnitNumber;

  const numeroApto = rawNumeroApto || (loadingDash || loadingPerfil || (unitId && loadingUnit) ? '...' : 'Sin Asignar');

  // 4. Códigos QR activos para visitas
  const { data: qrsRaw, loading: loadingQrs, error: errorQrs, refetch: refetchQrs } = useFetch(
    () => (resolvedPersonaId ? api.get(`/residentes/${resolvedPersonaId}/qr-activos`) : Promise.resolve([])),
    [resolvedPersonaId]
  );
  const qrActivos = useMemo(() => (Array.isArray(qrsRaw) ? qrsRaw : qrsRaw?.items || []), [qrsRaw]);

  // 5. Buzón de novedades y paquetería
  const {
    data: buzonRaw,
    loading: loadingBuzon,
    error: errorBuzon,
    refetch: refetchBuzon,
  } = useFetch(() => api.get('/buzon'), []);
  const buzonItems = useMemo(() => (Array.isArray(buzonRaw) ? buzonRaw : buzonRaw?.items || []), [buzonRaw]);

  const paquetesPendientes = useMemo(
    () => buzonItems.filter((m) => m.tipo === 'PAQUETE' && !m.leido),
    [buzonItems]
  );

  // 6. Avisos y comunicados oficiales de la administración
  const {
    data: avisosRaw,
    loading: loadingAvisos,
    error: errorAvisos,
    refetch: refetchAvisos,
  } = useFetch(() => api.get('/buzon/avisos'), []);
  const avisosOficiales = useMemo(() => (Array.isArray(avisosRaw) ? avisosRaw : avisosRaw?.items || []), [avisosRaw]);

  // 7. Mis Solicitudes PQRS
  const {
    data: pqrsRaw,
    loading: loadingPqrs,
    error: errorPqrs,
    refetch: refetchPqrs,
  } = useFetch(() => api.get('/pqrs/mis-tickets'), [user]);
  const misTickets = useMemo(() => (Array.isArray(pqrsRaw) ? pqrsRaw : pqrsRaw?.items || []), [pqrsRaw]);
  const ticketsEnTramite = useMemo(
    () => misTickets.filter((t) => t.estado !== 'CERRADO' && t.estado !== 'RESUELTO'),
    [misTickets]
  );

  // 8. Mis Reservas de Zonas Comunes
  const {
    data: reservasRaw,
    loading: loadingReservas,
    error: errorReservas,
    refetch: refetchReservas,
  } = useFetch(() => api.get('/reservas/mis-reservas'), [user]);
  const misReservas = useMemo(() => (Array.isArray(reservasRaw) ? reservasRaw : reservasRaw?.items || []), [reservasRaw]);
  const reservasFuturas = useMemo(
    () => misReservas.filter((r) => r.estado === 'PENDIENTE' || r.estado === 'APROBADA'),
    [misReservas]
  );

  // 9. Historial Wompi (Solo residente titular)
  const { data: wompiRaw, loading: loadingWompi, error: errorWompi, refetch: refetchWompi } = useFetch(
    () => (!isConviviente ? api.get('/pagos/wompi/historial') : Promise.resolve([])),
    [isConviviente]
  );
  const wompiHistorial = useMemo(() => (Array.isArray(wompiRaw) ? wompiRaw : wompiRaw?.items || []), [wompiRaw]);

  // 10. Minuta Táctica de Emergencia (Contactos Prioritarios)
  const { data: minutaRaw, loading: loadingMinuta, error: errorMinuta } = useFetch(
    () => api.get('/emergencias/contactos/minuta'),
    [user]
  );
  const contactosMinuta = useMemo(() => {
    if (!minutaRaw) return [];
    if (Array.isArray(minutaRaw)) return minutaRaw;
    if (Array.isArray(minutaRaw.items)) return minutaRaw.items;
    return minutaRaw.data || [];
  }, [minutaRaw]);

  // Cálculos financieros
  const cuotasPendientes = useMemo(() => cuotas.filter((c) => c.estado !== 'PAGADA'), [cuotas]);
  const multasPendientesList = useMemo(
    () => (dashboard.multas || []).filter((m) => m.estado === 'PENDIENTE'),
    [dashboard]
  );

  const saldoCuotasPendientes = useMemo(
    () => cuotasPendientes.reduce((s, c) => s + Number(c.saldoPendiente ?? c.valorTotal ?? 0), 0),
    [cuotasPendientes]
  );
  const saldoMultasPendientes = useMemo(
    () => multasPendientesList.reduce((s, m) => s + Number(m.monto || 0), 0),
    [multasPendientesList]
  );
  const totalDeudaPendiente = saldoCuotasPendientes + saldoMultasPendientes;
  const alDia = totalDeudaPendiente === 0;

  // Refresco unificado
  const [refreshing, setRefreshing] = useState(false);
  const refetchAll = useCallback(() => {
    setRefreshing(true);
    Promise.allSettled([
      refetchPerfil(),
      refetchDashboard(),
      refetchUnit(),
      refetchQrs(),
      refetchBuzon(),
      refetchAvisos(),
      refetchPqrs(),
      refetchReservas(),
      refetchWompi(),
    ]).finally(() => {
      setTimeout(() => setRefreshing(false), 400);
      toast.success('Información operativa actualizada');
    });
  }, [
    refetchPerfil,
    refetchDashboard,
    refetchUnit,
    refetchQrs,
    refetchBuzon,
    refetchAvisos,
    refetchPqrs,
    refetchReservas,
    refetchWompi,
  ]);

  // ==== Wompi Pagos en Línea ====
  const [pagando, setPagando] = useState(null);

  function cargarWidgetWompi() {
    if (window.WidgetCheckout) return Promise.resolve();
    if (window._wompiWidgetCargando) return window._wompiWidgetCargando;
    const promesa = new Promise((res, rej) => {
      const s = document.createElement('script');
      s.src = 'https://checkout.wompi.co/widget.js';
      s.async = true;
      const timer = setTimeout(() => {
        window._wompiWidgetCargando = null;
        rej(new Error('El widget de pago tarda demasiado en cargar. Por favor verifica tu conexión.'));
      }, 15000);
      s.onload = () => {
        clearTimeout(timer);
        window._wompiWidgetCargando = null;
        res();
      };
      s.onerror = () => {
        clearTimeout(timer);
        window._wompiWidgetCargando = null;
        rej(new Error('No se pudo cargar el widget de pago'));
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
      await new Promise((r) => setTimeout(r, 2000));
      try {
        const est = await api.get(`/pagos/wompi/estado?referencia=${encodeURIComponent(referencia)}`);
        const estado = est.estado || 'PENDIENTE';
        if (['APROBADO', 'RECHAZADO', 'VENCIDO', 'ERROR'].includes(estado)) {
          if (estado === 'APROBADO') {
            toast.success('¡Pago confirmado exitosamente! Recibirás el recibo formal por correo.');
          } else {
            toast.error(`El pago fue ${estado.toLowerCase()}.`);
          }
          return;
        }
      } catch {
        /* reintento */
      }
    }
    toast.info('El pago quedó en validación bancaria; te avisaremos al correo.');
  }

  async function pagarConWompi(concepto, id, label) {
    if (pagando) return;
    setPagando({ concepto, id, label });
    const timer = setTimeout(() => {
      setPagando(null);
      toast.info('La pasarela de pago se cerró o expiró.');
    }, 60000);

    const finalizar = () => {
      clearTimeout(timer);
      setPagando(null);
      refetchWompi();
      refetchDashboard();
    };

    try {
      const sol = await api.post('/pagos/wompi/solicitud', { concepto, id });
      if (sol.idTransaccionWompi) {
        toast.info('Ya hay una transacción en curso. Esperando confirmación bancaria...');
        await pollEstadoWompi(sol.referencia);
        finalizar();
        return;
      }
      await cargarWidgetWompi();
      const customerData = user?.email
        ? { email: user.email, fullName: nombreResidente || undefined }
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
        if (result && result.transaction && result.transaction.id) {
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
      toast.error(err.message || 'No se pudo iniciar el pago');
      setPagando(null);
    }
  }

  const badgeWompi = (estado) => {
    switch (estado) {
      case 'APROBADO':
        return <Badge variant="success" className="font-semibold">Aprobado</Badge>;
      case 'RECHAZADO':
        return <Badge variant="destructive" className="font-semibold">Rechazado</Badge>;
      case 'VENCIDO':
        return <Badge variant="warning">Vencido</Badge>;
      case 'ERROR':
        return <Badge variant="destructive" className="font-semibold">Error</Badge>;
      default:
        return <Badge variant="outline" className="font-semibold">Pendiente</Badge>;
    }
  };

  // ==== Confirmación de Visitas en Tiempo Real (Portería B1) ====
  const [confirmarPendiente, setConfirmarPendiente] = useState(null);
  const [confirmando, setConfirmando] = useState(false);
  const confirmandoRef = useRef(false);
  const failCountRef = useRef(0);
  const [backoffActivo, setBackoffActivo] = useState(false);

  const tickConfirmacion = useCallback(async () => {
    if (confirmarPendiente) return;
    if (document.visibilityState !== 'visible') return;
    try {
      const pendientes = await api.get('/buzon/confirmar-pendiente');
      const lista = Array.isArray(pendientes) ? pendientes : pendientes?.items || [];
      failCountRef.current = 0;
      if (backoffActivo) setBackoffActivo(false);
      if (lista.length > 0) setConfirmarPendiente(lista[0]);
    } catch {
      failCountRef.current += 1;
      if (failCountRef.current >= 5 && !backoffActivo) {
        setBackoffActivo(true);
      }
    }
  }, [confirmarPendiente, backoffActivo, setConfirmarPendiente]);

  useEffect(() => {
    const interval = setInterval(tickConfirmacion, backoffActivo ? 30000 : 5000);
    return () => clearInterval(interval);
  }, [tickConfirmacion, backoffActivo]);

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === 'visible') tickConfirmacion();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [tickConfirmacion]);

  async function responderConfirmacion(confirmado) {
    if (confirmandoRef.current) return;
    if (!confirmarPendiente) return;
    confirmandoRef.current = true;
    setConfirmando(true);
    try {
      await api.post('/buzon/confirmar', { idMensaje: confirmarPendiente.idMensaje, confirmado });
      toast.success(confirmado === 1 ? 'Acceso autorizado al visitante' : 'Acceso denegado');
      setConfirmarPendiente(null);
    } catch (err) {
      toast.error(err.message || 'Error al procesar la confirmación');
    } finally {
      confirmandoRef.current = false;
      setConfirmando(false);
    }
  }

  // ==== Pases QR & Compartir ====
  const [qrZoom, setQrZoom] = useState(null);
  const [tab, setTab] = useState('resumen');

  function compartirTelegram(codigoQr, nombre) {
    const text = encodeURIComponent(
      `Código de acceso SAED para ${nombre || 'tu visita'}\n\n` +
      `Código alfanumérico: ${codigoQr}\n` +
      `Presenta este código en portería para ingresar.`
    );
    window.open(`https://t.me/share/url?text=${text}`, '_blank');
  }

  function compartirSMS(codigoQr, telefono) {
    const body = encodeURIComponent(`Tu código de acceso SAED en portería es: ${codigoQr}`);
    window.open(telefono ? `sms:${telefono}?body=${body}` : `sms:?body=${body}`);
  }

  function compartirCorreo(codigoQr, nombre, email) {
    const subject = encodeURIComponent('Pase de Acceso — SAED');
    const body = encodeURIComponent(
      `Hola,\n\nHas recibido una autorización de acceso${nombre ? ` para ${nombre}` : ''}.\n\n` +
      `Código de Acceso: ${codigoQr}\n\n` +
      `Presenta este código al oficial de portería para validar tu ingreso.\n\n` +
      `Conjunto / Edificio: ${user?.nombrePropiedad || 'Copropiedad'}`
    );
    window.open(email ? `mailto:${email}?subject=${subject}&body=${body}` : `mailto:?subject=${subject}&body=${body}`);
  }

  async function copiarQR(codigoQr) {
    try {
      await navigator.clipboard.writeText(codigoQr);
      toast.success('Código QR copiado al portapapeles');
    } catch {
      toast.error('No se pudo copiar el código');
    }
  }

  // useFetch().refetch rethrows; retry buttons swallow it because the panel already shows the error.
  const retry = (fn) => () => {
    fn().catch(() => {});
  };

  const saludoNombre =
    perfil.primerNombre || perfil.nombres?.split(' ')[0] || user?.primerNombre || nombreResidente.split(' ')[0];
  const mensajesSinLeer = buzonItems.filter((m) => m.tipo !== 'PAQUETE' && !m.leido);
  const proximaReserva = reservasFuturas[0];
  const avisosRecientes = avisosOficiales.slice(0, 3);

  const attentionRows = [
    !isConviviente && {
      id: 'cuotas',
      icon: Wallet,
      title: 'Cuotas y multas pendientes',
      count: cuotasPendientes.length + multasPendientesList.length,
      detail:
        cuotasPendientes.length + multasPendientesList.length > 0
          ? `${formatCurrency(totalDeudaPendiente)} por pagar`
          : 'Estás al día con la administración.',
      loading: loadingDash,
      error: errorDash,
      onRetry: retry(refetchDashboard),
      to: '/res-cuotas',
      actionLabel: 'Ir a mis cuotas',
    },
    {
      id: 'visitas',
      icon: ShieldAlert,
      title: 'Visitantes esperando en portería',
      count: confirmarPendiente ? 1 : 0,
      detail: confirmarPendiente
        ? confirmarPendiente.titulo || 'Un visitante espera tu autorización.'
        : backoffActivo
        ? 'Sin conexión con portería. Reintentamos cada 30 segundos.'
        : 'Te avisaremos aquí cuando alguien espere tu autorización.',
      warn: backoffActivo && !confirmarPendiente,
      to: '/res-visitas',
      actionLabel: 'Ir a visitas',
    },
    {
      id: 'paquetes',
      icon: Package,
      title: 'Paquetes por retirar',
      count: paquetesPendientes.length,
      detail:
        paquetesPendientes.length > 0 ? 'En custodia en portería.' : 'No tienes paquetes en portería.',
      loading: loadingBuzon,
      error: errorBuzon,
      onRetry: retry(refetchBuzon),
      to: '/res-buzon',
      actionLabel: 'Ir al buzón',
    },
    {
      id: 'buzon',
      icon: Mail,
      title: 'Mensajes sin leer',
      count: mensajesSinLeer.length,
      detail: mensajesSinLeer.length > 0 ? mensajesSinLeer[0].titulo || 'Tienes mensajes nuevos.' : 'Estás al día con tu buzón.',
      loading: loadingBuzon,
      error: errorBuzon,
      onRetry: retry(refetchBuzon),
      to: '/res-buzon',
      actionLabel: 'Ir al buzón',
    },
    {
      id: 'reservas',
      icon: Calendar,
      title: 'Próximas reservas',
      count: reservasFuturas.length,
      detail: proximaReserva
        ? `${proximaReserva.nombreZona || 'Zona común'} · ${formatDate(proximaReserva.fechaReserva)}`
        : 'Sin reservas programadas.',
      loading: loadingReservas,
      error: errorReservas,
      onRetry: retry(refetchReservas),
      to: '/res-reservas',
      actionLabel: 'Ir a zonas comunes',
    },
    {
      id: 'pqrs',
      icon: MessageSquare,
      title: 'Solicitudes PQRS abiertas',
      count: ticketsEnTramite.length,
      detail: ticketsEnTramite[0]?.asunto || 'No tienes solicitudes abiertas.',
      loading: loadingPqrs,
      error: errorPqrs,
      onRetry: retry(refetchPqrs),
      to: '/res-quejas',
      actionLabel: 'Ir a PQRS',
    },
  ].filter(Boolean);

  const settledRows = attentionRows.filter((r) => !r.loading && !r.error);
  const pendingTotal = settledRows.reduce((sum, r) => sum + r.count, 0);
  const anyAttentionError = attentionRows.some((r) => r.error);
  const anyAttentionLoading = attentionRows.some((r) => r.loading);

  const quickActions = [
    { label: 'Nueva visita', icon: Plus, to: '/res-visitas' },
    !isConviviente && { label: 'Pagar cuotas', icon: CreditCard, to: '/res-cuotas' },
    { label: 'Reservar zona', icon: Calendar, to: '/res-reservas' },
    { label: 'Radicar PQRS', icon: MessageSquare, to: '/res-quejas' },
    !isConviviente && {
      label: 'Convivientes',
      icon: UserPlus,
      to: user?.rol === 'RESIDENTE' ? '/res-convivientes' : '/res-perfil',
    },
    { label: 'Buzón y avisos', icon: Bell, to: '/res-buzon' },
  ].filter(Boolean);

  return (
    <PageContainer>
      {/* 1. SALUDO + UNIDAD */}
      <section
        aria-labelledby="res-dash-title"
        className="relative overflow-hidden rounded-lg border border-border bg-surface shadow-sm"
      >
        <div
          className="app-studs pointer-events-none absolute inset-y-0 right-0 hidden w-1/2 sm:block [mask-image:linear-gradient(to_left,black,transparent)]"
          aria-hidden="true"
        />
        <div className="relative flex flex-col gap-5 p-5 sm:p-6 md:flex-row md:items-center md:justify-between">
          <div className="flex min-w-0 items-center gap-4">
            <div
              className="app-numeral flex h-14 w-14 shrink-0 items-center justify-center rounded-[6px] bg-[#EEE8FF] text-xl text-[#1D2942] ring-1 ring-inset ring-[#1D2942]/15"
              aria-hidden="true"
            >
              {iniciales}
            </div>
            <div className="min-w-0">
              <h1 id="res-dash-title" className="app-display truncate text-2xl leading-tight text-on-surface sm:text-[1.75rem]">
                Hola, {saludoNombre}
              </h1>
              <p className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm text-text-secondary">
                <span className="inline-flex items-center gap-1.5 font-semibold text-on-surface">
                  <Home className="h-4 w-4 shrink-0 text-primary-text" aria-hidden="true" />
                  {numeroApto}
                </span>
                <span className="text-text-muted" aria-hidden="true">·</span>
                <span className="min-w-0 truncate">{user?.nombrePropiedad || 'Edificio Residencial SAED'}</span>
              </p>
              <div className="mt-2.5 flex flex-wrap items-center gap-2">
                <Badge variant="lavender">{isConviviente ? 'Residente conviviente' : 'Residente titular'}</Badge>
                {isConviviente ? (
                  <Badge variant="success">
                    <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
                    Conviviente autorizado
                  </Badge>
                ) : loadingDash || errorDash ? null : alDia ? (
                  <Badge variant="success">
                    <CheckCircle2 className="h-3 w-3" aria-hidden="true" />
                    Paz y salvo vigente
                  </Badge>
                ) : (
                  <Badge variant="warning">
                    <AlertCircle className="h-3 w-3" aria-hidden="true" />
                    {formatCurrency(totalDeudaPendiente)} pendiente
                  </Badge>
                )}
              </div>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" size="sm" onClick={refetchAll} disabled={refreshing}>
              <RefreshCw className={cn('h-3.5 w-3.5', refreshing && 'animate-spin')} aria-hidden="true" />
              Actualizar
            </Button>
            {!isConviviente && user?.rol === 'RESIDENTE' && (
              <Button variant="outline" size="sm" onClick={() => navigate('/res-convivientes')}>
                <Users className="h-3.5 w-3.5" aria-hidden="true" />
                Mis convivientes
              </Button>
            )}
            <Button size="sm" onClick={() => navigate('/res-perfil')}>
              <Building2 className="h-3.5 w-3.5" aria-hidden="true" />
              Mi perfil completo
            </Button>
          </div>
        </div>
      </section>

      {/* 2. LO QUE REQUIERE ATENCIÓN + ACCIONES RÁPIDAS + AVISOS */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <section
          aria-labelledby="res-attention-title"
          className="rounded-lg border border-border bg-surface shadow-sm lg:col-span-2"
        >
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3.5 sm:px-5">
            <h2 id="res-attention-title" className="text-base font-bold text-on-surface">
              Lo que requiere tu atención
            </h2>
            <span className="text-xs font-semibold text-text-muted" aria-live="polite">
              {anyAttentionLoading
                ? 'Consultando…'
                : anyAttentionError
                ? 'Algunos datos no se pudieron cargar'
                : pendingTotal > 0
                ? `${pendingTotal} pendiente${pendingTotal === 1 ? '' : 's'}`
                : 'Todo al día'}
            </span>
          </div>
          <ul className="divide-y divide-border-subtle">
            {attentionRows.map((row) => (
              <AttentionRow key={row.id} row={row} onOpen={() => navigate(row.to)} />
            ))}
          </ul>
        </section>

        <div className="space-y-6">
          <section
            aria-labelledby="res-quick-title"
            className="rounded-lg border border-border bg-surface p-4 shadow-sm sm:p-5"
          >
            <h2 id="res-quick-title" className="mb-3 text-base font-bold text-on-surface">
              Acciones rápidas
            </h2>
            <div className="grid grid-cols-2 gap-2">
              {quickActions.map(({ label, icon: Icon, to }) => (
                <Button
                  key={label}
                  variant="outline"
                  onClick={() => navigate(to)}
                  className="h-auto min-h-11 justify-start gap-2 whitespace-normal px-3 py-2.5 text-left text-[13px]"
                >
                  <Icon className="text-primary-text" aria-hidden="true" />
                  {label}
                </Button>
              ))}
            </div>
          </section>

          <section
            aria-labelledby="res-notices-title"
            className="rounded-lg border border-border bg-surface shadow-sm"
          >
            <div className="flex items-center justify-between gap-2 border-b border-border px-4 py-3.5 sm:px-5">
              <h2 id="res-notices-title" className="text-base font-bold text-on-surface">
                Avisos de la comunidad
              </h2>
              {avisosOficiales.length > 0 && (
                <Button variant="link" size="sm" className="h-auto px-0" onClick={() => setTab('comunidad')}>
                  Ver todos
                </Button>
              )}
            </div>
            <div className="p-4 sm:p-5">
              {loadingAvisos ? (
                <LoadingState size="sm" message="Cargando avisos…" className="p-0" />
              ) : errorAvisos ? (
                <ErrorState
                  compact
                  title="No se pudieron cargar los avisos"
                  message="Revisa tu conexión e inténtalo de nuevo."
                  onRetry={retry(refetchAvisos)}
                />
              ) : avisosRecientes.length === 0 ? (
                <p className="text-sm text-text-muted">
                  La administración no ha publicado comunicados recientes.
                </p>
              ) : (
                <ul className="space-y-3">
                  {avisosRecientes.map((aviso, idx) => (
                    <li key={aviso.idMensaje || aviso.idComunicado || `aviso-${idx}`} className="min-w-0">
                      <p className="truncate text-sm font-semibold text-on-surface">
                        {aviso.titulo || aviso.TITULO || 'Comunicado oficial'}
                      </p>
                      <p className="text-xs text-text-muted tabular-nums">
                        {formatDate(aviso.fechaCreacion || aviso.FECHA_PUBLICACION || new Date())}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>
        </div>
      </div>

      {/* 3. INDICADORES DEL PERFIL */}
      <KpiSummaryPanel />

      {/* 4. DETALLE POR ÁREA */}
      <Tabs value={tab} onValueChange={setTab} className="space-y-5">
        <TabsList
          className={cn(
            'grid h-auto w-full',
            isConviviente ? 'max-w-md grid-cols-2' : 'max-w-2xl grid-cols-3'
          )}
        >
          <TabsTrigger value="resumen" className="gap-2 text-xs sm:text-sm">
            <Sparkles className="hidden h-4 w-4 sm:block" aria-hidden="true" />
            Resumen
          </TabsTrigger>
          {!isConviviente && (
            <TabsTrigger value="finanzas" className="gap-2 text-xs sm:text-sm">
              <CreditCard className="hidden h-4 w-4 sm:block" aria-hidden="true" />
              Finanzas
              {cuotasPendientes.length > 0 && (
                <Badge variant="destructive" className="px-1.5 py-0 text-[10px] tabular-nums">
                  {cuotasPendientes.length}
                </Badge>
              )}
            </TabsTrigger>
          )}
          <TabsTrigger value="comunidad" className="gap-2 text-xs sm:text-sm">
            <Bell className="hidden h-4 w-4 sm:block" aria-hidden="true" />
            Avisos
            {avisosOficiales.length > 0 && (
              <Badge variant="secondary" className="px-1.5 py-0 text-[10px] tabular-nums">
                {avisosOficiales.length}
              </Badge>
            )}
          </TabsTrigger>
        </TabsList>

        {/* TAB 1: RESUMEN */}
        <TabsContent value="resumen" className="space-y-6">
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <div className="space-y-6 lg:col-span-2">
              {/* Obligaciones (solo titular) */}
              {!isConviviente && (
                <Panel
                  icon={Wallet}
                  title="Cuotas y obligaciones del mes"
                  description="Pago en línea con Wompi (PSE o tarjeta)."
                  action={
                    loadingDash || errorDash ? null : alDia ? (
                      <Badge variant="success">
                        <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
                        Paz y salvo vigente
                      </Badge>
                    ) : (
                      <Badge variant="destructive">{formatCurrency(totalDeudaPendiente)} pendiente</Badge>
                    )
                  }
                >
                  {loadingDash ? (
                    <LoadingState size="sm" message="Consultando tu estado de cuenta…" className="p-0" />
                  ) : errorDash ? (
                    <ErrorState
                      compact
                      title="No se pudo cargar tu estado de cuenta"
                      message="No mostramos saldos hasta confirmar la información con la administración."
                      onRetry={retry(refetchDashboard)}
                    />
                  ) : cuotasPendientes.length === 0 && multasPendientesList.length === 0 ? (
                    <EmptyState
                      icon={CheckCircle2}
                      title="Estás al día con la administración"
                      subtitle={`No registras cobros pendientes para el inmueble ${numeroApto}.`}
                      className="py-6"
                    >
                      <Button variant="outline" size="sm" onClick={() => navigate('/res-cuotas')} className="mt-3">
                        <FileText className="h-3.5 w-3.5" aria-hidden="true" />
                        Ver historial de recibos
                      </Button>
                    </EmptyState>
                  ) : (
                    <ul className="divide-y divide-border-subtle rounded-md border border-border">
                      {cuotasPendientes.map((c) => (
                        <li
                          key={`c-${c.idCuota}`}
                          className="flex flex-col justify-between gap-3 p-4 sm:flex-row sm:items-center"
                        >
                          <div className="min-w-0 space-y-0.5">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-sm font-bold text-on-surface">
                                {c.tipoCuota === 'ADMINISTRACION' ? 'Cuota de administración' : 'Cuota extraordinaria'}
                              </span>
                              <Badge variant="outline" className="text-[10px]">
                                {MESES_W[(c.mes || 1) - 1]} {c.anio}
                              </Badge>
                            </div>
                            <p className="text-xs text-text-muted">
                              {c.fechaLimite ? `Vence el ${formatDate(c.fechaLimite)}` : 'Cobro reglamentario'}
                            </p>
                          </div>
                          <div className="flex items-center justify-between gap-4 sm:justify-end">
                            <div className="text-right">
                              <span className="block text-xs text-text-muted">Monto a pagar</span>
                              <span className="app-numeral text-base text-on-surface">
                                {formatCurrency(c.saldoPendiente ?? c.valorTotal)}
                              </span>
                            </div>
                            <Button
                              onClick={() => pagarConWompi('CUOTA', c.idCuota, `Cuota ${c.anio}/${c.mes}`)}
                              disabled={!!pagando}
                              className="shrink-0"
                            >
                              <CreditCard aria-hidden="true" />
                              {pagando?.id === c.idCuota && pagando?.concepto === 'CUOTA' ? 'Abriendo…' : 'Pagar'}
                            </Button>
                          </div>
                        </li>
                      ))}

                      {multasPendientesList.map((m) => (
                        <li
                          key={`m-${m.idMulta}`}
                          className="flex flex-col justify-between gap-3 bg-tint-red-bg/50 p-4 sm:flex-row sm:items-center"
                        >
                          <div className="min-w-0 space-y-0.5">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-sm font-bold text-on-surface">Sanción o multa</span>
                              <Badge variant="destructive" className="text-[10px]">{m.tipo}</Badge>
                            </div>
                            <p className="text-xs text-text-muted">{m.descripcion || 'Incumplimiento al reglamento'}</p>
                          </div>
                          <div className="flex items-center justify-between gap-4 sm:justify-end">
                            <div className="text-right">
                              <span className="block text-xs text-text-muted">Monto</span>
                              <span className="app-numeral text-base text-tint-red-fg">{formatCurrency(m.monto)}</span>
                            </div>
                            <Button
                              variant="outline"
                              onClick={() => pagarConWompi('MULTA', m.idMulta, `Multa ${m.tipo}`)}
                              disabled={!!pagando}
                              className="shrink-0"
                            >
                              <CreditCard aria-hidden="true" />
                              {pagando?.id === m.idMulta && pagando?.concepto === 'MULTA' ? 'Abriendo…' : 'Pagar'}
                            </Button>
                          </div>
                        </li>
                      ))}
                    </ul>
                  )}
                </Panel>
              )}

              {/* Pases de visita QR activos */}
              <Panel
                icon={QrCode}
                title={`Pases de acceso activos${loadingQrs || errorQrs ? '' : ` (${qrActivos.length})`}`}
                description="Invitaciones vigentes para lectura en portería."
                action={
                  <Button size="sm" onClick={() => navigate('/res-visitas')}>
                    <Plus className="h-3.5 w-3.5" aria-hidden="true" />
                    Nueva visita
                  </Button>
                }
              >
                {loadingQrs ? (
                  <LoadingState size="sm" message="Cargando pases…" className="p-0" />
                ) : errorQrs ? (
                  <ErrorState
                    compact
                    title="No se pudieron cargar tus pases"
                    message="Revisa tu conexión e inténtalo de nuevo."
                    onRetry={retry(refetchQrs)}
                  />
                ) : qrActivos.length === 0 ? (
                  <EmptyState
                    icon={QrCode}
                    title="No tienes pases activos"
                    subtitle="Crea una invitación con QR para que tus visitas ingresen sin demoras en portería."
                    className="py-6"
                  />
                ) : (
                  <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                    {qrActivos.map((qr) => (
                      <div
                        key={qr.idQr}
                        className="flex flex-col justify-between rounded-md border border-border bg-surface p-4"
                      >
                        <div className="flex items-start gap-3.5">
                          <button
                            type="button"
                            onClick={() => setQrZoom(qr)}
                            className="flex h-16 w-16 shrink-0 cursor-zoom-in items-center justify-center overflow-hidden rounded-[4px] border border-border bg-white p-1"
                            title={`Ver código QR de ${qr.nombreVisitante || 'Visita'}`}
                            aria-label={`Ver código QR de ${qr.nombreVisitante || 'Visita'}`}
                          >
                            <LocalQRCode
                              value={qr.codigoQr}
                              size={56}
                              alt={`QR ${qr.nombreVisitante || 'Visita'}`}
                            />
                          </button>
                          <div className="min-w-0 flex-1 space-y-0.5">
                            <h4 className="truncate text-sm font-bold text-on-surface">
                              {qr.nombreVisitante || 'Visitante autorizado'}
                            </h4>
                            {qr.documentoVisitante && (
                              <p className="text-xs text-text-muted tabular-nums">Doc: {qr.documentoVisitante}</p>
                            )}
                            <p className="flex items-center gap-1 text-xs text-text-muted">
                              <Users className="h-3 w-3" aria-hidden="true" />
                              {qr.cantidadPersonas || 1} persona(s)
                            </p>
                            {qr.fechaCreacion && (
                              <p className="flex items-center gap-1 text-[11px] text-text-muted">
                                <Calendar className="h-3 w-3" aria-hidden="true" />
                                Generado: {formatDateTime(qr.fechaCreacion)}
                              </p>
                            )}
                            <p className="flex items-center gap-1 text-[11px] font-semibold text-tint-amber-fg">
                              <Clock className="h-3 w-3" aria-hidden="true" />
                              Expira: {formatDateTime(qr.fechaExpiracion)}
                            </p>
                          </div>
                        </div>

                        <div className="mt-3 flex items-center justify-between gap-1 border-t border-border-subtle pt-3">
                          <Button variant="ghost" size="sm" onClick={() => setQrZoom(qr)} className="px-2 text-primary-text">
                            <Eye className="h-3.5 w-3.5" aria-hidden="true" />
                            Ver
                          </Button>
                          <div className="flex items-center gap-1">
                            <Button
                              variant="outline"
                              size="icon"
                              onClick={() => compartirTelegram(qr.codigoQr, qr.nombreVisitante)}
                              title="Enviar por Telegram"
                              aria-label="Enviar por Telegram"
                              className="h-8 w-8"
                            >
                              <Send className="h-3.5 w-3.5" aria-hidden="true" />
                            </Button>
                            <Button
                              variant="outline"
                              size="icon"
                              onClick={() => compartirSMS(qr.codigoQr, '')}
                              title="Enviar por SMS"
                              aria-label="Enviar por SMS"
                              className="h-8 w-8"
                            >
                              <Phone className="h-3.5 w-3.5" aria-hidden="true" />
                            </Button>
                            <Button
                              variant="outline"
                              size="icon"
                              onClick={() => compartirCorreo(qr.codigoQr, qr.nombreVisitante, '')}
                              title="Enviar por correo"
                              aria-label="Enviar por correo"
                              className="h-8 w-8"
                            >
                              <Mail className="h-3.5 w-3.5" aria-hidden="true" />
                            </Button>
                            <Button
                              variant="outline"
                              size="icon"
                              onClick={() => copiarQR(qr.codigoQr)}
                              title="Copiar código al portapapeles"
                              aria-label="Copiar código al portapapeles"
                              className="h-8 w-8"
                            >
                              <Copy className="h-3.5 w-3.5" aria-hidden="true" />
                            </Button>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </Panel>

              {/* Solicitudes PQRS en trámite */}
              <Panel
                icon={MessageSquare}
                title={`Solicitudes en trámite${loadingPqrs || errorPqrs ? '' : ` (${ticketsEnTramite.length})`}`}
                description="Seguimiento de peticiones y reportes enviados a la administración."
                action={
                  <Button variant="outline" size="sm" onClick={() => navigate('/res-quejas')}>
                    <Plus className="h-3.5 w-3.5" aria-hidden="true" />
                    Radicar PQRS
                  </Button>
                }
              >
                {loadingPqrs ? (
                  <LoadingState size="sm" message="Cargando solicitudes…" className="p-0" />
                ) : errorPqrs ? (
                  <ErrorState
                    compact
                    title="No se pudieron cargar tus solicitudes"
                    message="Revisa tu conexión e inténtalo de nuevo."
                    onRetry={retry(refetchPqrs)}
                  />
                ) : ticketsEnTramite.length === 0 ? (
                  <EmptyState
                    icon={CheckCircle2}
                    title="No tienes solicitudes abiertas"
                    subtitle="Tus peticiones y reportes anteriores ya fueron atendidos."
                    className="py-6"
                  />
                ) : (
                  <ul className="divide-y divide-border-subtle rounded-md border border-border">
                    {ticketsEnTramite.slice(0, 4).map((t) => (
                      <li key={t.idTicket} className="flex items-center justify-between gap-3 p-3.5">
                        <div className="min-w-0 flex-1 space-y-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="truncate text-[13px] font-bold text-on-surface">{t.asunto}</span>
                            <Badge
                              variant={
                                t.estado === 'RESUELTO' ? 'success' : t.estado === 'EN_REVISION' ? 'info' : 'secondary'
                              }
                              className="text-[10px]"
                            >
                              {t.estado === 'EN_REVISION' ? 'En revisión' : t.estado}
                            </Badge>
                          </div>
                          <p className="line-clamp-1 text-xs text-text-muted">{t.descripcion || 'Sin detalles'}</p>
                          <span className="block text-[11px] text-text-muted">
                            Radicado: {formatDate(t.fechaCreacion)} · Categoría: {t.categoria || 'Administración'}
                          </span>
                        </div>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => navigate('/res-quejas')}
                          className="shrink-0 text-primary-text"
                        >
                          Ver
                          <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                        </Button>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>
            </div>

            {/* Columna derecha: paquetes, reservas y emergencias */}
            <div className="space-y-6">
              <Panel
                icon={Package}
                title="Paquetes en portería"
                description="Correspondencia y encomiendas en recepción."
                action={
                  paquetesPendientes.length > 0 && (
                    <Badge variant="warning" className="text-[10px]">
                      {paquetesPendientes.length} pendiente(s)
                    </Badge>
                  )
                }
              >
                {loadingBuzon ? (
                  <LoadingState size="sm" message="Cargando paquetes…" className="p-0" />
                ) : errorBuzon ? (
                  <ErrorState
                    compact
                    title="No se pudo cargar tu buzón"
                    onRetry={retry(refetchBuzon)}
                  />
                ) : paquetesPendientes.length === 0 ? (
                  <p className="text-sm text-text-muted">
                    Sin paquetes en portería. Cuando llegue uno, portería lo registrará y te avisará.
                  </p>
                ) : (
                  <ul className="space-y-2">
                    {paquetesPendientes.map((p) => (
                      <li key={p.idMensaje} className="space-y-1 rounded-md border border-tint-amber-fg/30 bg-tint-amber-bg/60 p-3">
                        <div className="flex items-center justify-between gap-2">
                          <span className="truncate text-xs font-bold text-on-surface">{p.titulo}</span>
                          <span className="shrink-0 text-[11px] text-text-muted tabular-nums">{formatDate(p.fechaCreacion)}</span>
                        </div>
                        {p.cuerpo && <p className="text-xs text-text-secondary">{p.cuerpo}</p>}
                        <div className="flex items-center justify-between pt-1">
                          <span className="text-[11px] font-semibold text-tint-amber-fg">En custodia en portería</span>
                          <Button
                            variant="link"
                            size="sm"
                            onClick={() => navigate('/res-buzon')}
                            className="h-auto px-0 text-[12px]"
                          >
                            Ver detalles
                          </Button>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>

              <Panel
                icon={Calendar}
                title="Mis próximas reservas"
                description="Zonas sociales y áreas comunes."
                action={
                  <Button variant="link" size="sm" onClick={() => navigate('/res-reservas')} className="h-auto px-0">
                    Reservar
                  </Button>
                }
              >
                {loadingReservas ? (
                  <LoadingState size="sm" message="Cargando reservas…" className="p-0" />
                ) : errorReservas ? (
                  <ErrorState
                    compact
                    title="No se pudieron cargar tus reservas"
                    onRetry={retry(refetchReservas)}
                  />
                ) : reservasFuturas.length === 0 ? (
                  <p className="text-sm text-text-muted">
                    Sin reservas programadas. Reserva el salón social, la zona BBQ o la cancha para tus eventos.
                  </p>
                ) : (
                  <ul className="divide-y divide-border-subtle rounded-md border border-border">
                    {reservasFuturas.slice(0, 3).map((r) => (
                      <li key={r.idReserva} className="space-y-1 p-3">
                        <div className="flex items-center justify-between gap-2">
                          <span className="truncate text-xs font-bold text-on-surface">{r.nombreZona || 'Zona común'}</span>
                          <Badge variant={r.estado === 'APROBADA' ? 'success' : 'secondary'} className="text-[10px]">
                            {r.estado}
                          </Badge>
                        </div>
                        <p className="flex items-center gap-1 text-xs text-text-muted tabular-nums">
                          <Clock className="h-3 w-3" aria-hidden="true" />
                          {formatDate(r.fechaReserva)} · {r.horaInicio || '14:00'} - {r.horaFin || '18:00'}
                        </p>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>

              <Panel icon={Phone} title="Emergencias y asistencia" description="Líneas prioritarias de la copropiedad.">
                {loadingMinuta ? (
                  <LoadingState size="sm" message="Cargando líneas de emergencia…" className="p-0" />
                ) : errorMinuta ? (
                  <ErrorState
                    compact
                    title="No se pudieron cargar las líneas de emergencia"
                    message="Ante una emergencia, comunícate directamente con la línea 123."
                  />
                ) : contactosMinuta.length === 0 ? (
                  <p className="text-sm text-text-muted">
                    La administración aún no ha configurado números de emergencia para esta propiedad.
                  </p>
                ) : (
                  <ul className="divide-y divide-border-subtle">
                    {contactosMinuta.map((c, idx) => (
                      <li
                        key={c.idContactoEmergencia || idx}
                        className="flex items-center justify-between gap-3 py-2.5 first:pt-0 last:pb-0"
                      >
                        <div className="min-w-0">
                          <p className="text-xs font-bold text-on-surface">{c.entidad}</p>
                          <p className="text-[11px] text-text-muted">
                            {c.tipoServicio ? c.tipoServicio.replace(/_/g, ' ') : 'Servicio de emergencia'}
                            {c.direccion ? ` · ${c.direccion}` : ''}
                          </p>
                        </div>
                        <div className="shrink-0 text-right">
                          <a
                            href={`tel:${c.telefonoPrincipal}`}
                            className="inline-flex items-center gap-1 rounded-[4px] border border-border bg-tint-primary-bg px-2.5 py-1 text-xs font-bold text-tint-primary-fg tabular-nums transition-colors hover:border-primary/60"
                          >
                            <Phone className="h-3 w-3" aria-hidden="true" />
                            {c.telefonoPrincipal}
                          </a>
                          {c.telefonoAlterno && (
                            <p className="mt-0.5 text-[11px] text-text-muted tabular-nums">Alt: {c.telefonoAlterno}</p>
                          )}
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </Panel>
            </div>
          </div>
        </TabsContent>

        {/* TAB 2: FINANZAS & HISTORIAL WOMPI (Solo Residente Titular) */}
        {!isConviviente && (
          <TabsContent value="finanzas" className="space-y-6">
            <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
              <Panel
                icon={CreditCard}
                title="Transacciones en línea (Wompi)"
                description="Pagos procesados mediante la pasarela bancaria."
                className="lg:col-span-2"
              >
                {loadingWompi ? (
                  <LoadingState size="sm" message="Cargando transacciones…" className="p-0" />
                ) : errorWompi ? (
                  <ErrorState
                    compact
                    title="No se pudo cargar el historial de pagos"
                    onRetry={retry(refetchWompi)}
                  />
                ) : wompiHistorial.length === 0 ? (
                  <EmptyState
                    icon="payments"
                    title="No hay transacciones en línea recientes"
                    subtitle="Cuando pagues con Wompi, el comprobante y su estado bancario aparecerán aquí."
                    className="py-6"
                  />
                ) : (
                  <div className="overflow-x-auto rounded-md border border-border">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="border-b border-border bg-surface-container text-left text-text-secondary">
                          <th className="px-3 py-2.5 font-bold">Referencia</th>
                          <th className="px-3 py-2.5 font-bold">Fecha</th>
                          <th className="px-3 py-2.5 text-right font-bold">Monto</th>
                          <th className="px-3 py-2.5 text-center font-bold">Estado</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border-subtle">
                        {wompiHistorial.slice(0, 8).map((h) => (
                          <tr key={h.referencia} className="hover:bg-surface-container">
                            <td className="px-3 py-3 font-mono text-on-surface">#{String(h.referencia).slice(0, 20)}…</td>
                            <td className="px-3 py-3 text-text-muted tabular-nums">
                              {formatDate(h.fechaCreacion || new Date())}
                            </td>
                            <td className="px-3 py-3 text-right font-bold text-on-surface tabular-nums">
                              {formatCurrency(h.montoCentavos ? h.montoCentavos / 100 : h.monto)}
                            </td>
                            <td className="px-3 py-3 text-center">{badgeWompi(h.estado)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </Panel>

              <div className="space-y-6">
                <Panel icon={ShieldCheck} title="Métodos de pago habilitados">
                  <ul className="space-y-2 text-xs">
                    {[
                      { tag: 'PSE', name: 'Débito bancario (PSE)', note: 'Todos los bancos de Colombia' },
                      { tag: 'NEQUI', name: 'Nequi y Daviplata', note: 'Notificación y débito al instante' },
                      { tag: 'VISA', name: 'Tarjetas de crédito o débito', note: 'Visa, Mastercard, American Express' },
                    ].map((m) => (
                      <li key={m.tag} className="flex items-center gap-3 rounded-md border border-border p-3">
                        <span className="app-stamp w-14 shrink-0 rounded-[2px] border border-keyline/30 bg-surface-container py-0.5 text-center text-[11px] text-on-surface">
                          {m.tag}
                        </span>
                        <div className="min-w-0">
                          <p className="font-bold text-on-surface">{m.name}</p>
                          <p className="text-text-muted">{m.note}</p>
                        </div>
                      </li>
                    ))}
                  </ul>
                </Panel>

                <Panel icon={FileText} title="Paz y salvo de administración">
                  <p className="text-xs text-text-secondary">
                    Para trámites notariales, arriendos o traspasos, solicita tu certificado oficial de paz y salvo
                    expedido por la administración.
                  </p>
                  <Button variant="outline" size="sm" onClick={() => navigate('/res-cuotas')} className="mt-3 w-full">
                    <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
                    Ir al portal financiero
                  </Button>
                </Panel>
              </div>
            </div>
          </TabsContent>
        )}

        {/* TAB 3: COMUNIDAD & AVISOS DE ADMINISTRACIÓN */}
        <TabsContent value="comunidad" className="space-y-6">
          <Panel
            icon={Bell}
            title="Circulares y comunicados de la administración"
            description="Información oficial sobre mantenimientos, asambleas y convivencia."
            action={
              <Button variant="outline" size="sm" onClick={retry(refetchAvisos)}>
                <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
                Actualizar
              </Button>
            }
          >
            {loadingAvisos ? (
              <LoadingState message="Cargando comunicados…" className="p-0" />
            ) : errorAvisos ? (
              <ErrorState
                title="No se pudieron cargar los comunicados"
                message="Revisa tu conexión e inténtalo de nuevo."
                onRetry={retry(refetchAvisos)}
              />
            ) : avisosOficiales.length === 0 ? (
              <EmptyState
                icon="campaign"
                title="No hay circulares publicadas recientemente"
                subtitle="Cuando la administración emita comunicados o convocatorias oficiales, aparecerán en este tablón."
              />
            ) : (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {avisosOficiales.map((aviso, idx) => (
                  <article
                    key={aviso.idMensaje || aviso.idComunicado || `aviso-${idx}`}
                    className="flex flex-col justify-between space-y-2.5 rounded-md border border-border p-5"
                  >
                    <div className="space-y-2">
                      <div className="flex items-start justify-between gap-2">
                        <h4 className="text-sm font-bold text-on-surface">
                          {aviso.titulo || aviso.TITULO || 'Comunicado oficial'}
                        </h4>
                        <Badge variant="outline" className="shrink-0 text-[10px] tabular-nums">
                          {formatDate(aviso.fechaCreacion || aviso.FECHA_PUBLICACION || new Date())}
                        </Badge>
                      </div>
                      <p className="whitespace-pre-line text-xs leading-relaxed text-text-secondary">
                        {aviso.cuerpo || aviso.CONTENIDO || aviso.mensaje || ''}
                      </p>
                    </div>
                    <div className="flex items-center justify-between border-t border-border-subtle pt-2 text-[11px] text-text-muted">
                      <span className="flex items-center gap-1">
                        <Building2 className="h-3 w-3" aria-hidden="true" />
                        Administración
                      </span>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </Panel>
        </TabsContent>
      </Tabs>

      {/* 5. MODAL DE CONFIRMACIÓN DE VISITAS EN TIEMPO REAL (PORTERÍA B1) */}
      <Modal
        open={!!confirmarPendiente}
        onClose={() => setConfirmarPendiente(null)}
        title="Solicitud de acceso en portería"
      >
        {confirmarPendiente && (
          <div className="space-y-4 py-2">
            <div className="flex items-start gap-3 rounded-md border border-tint-amber-fg/30 bg-tint-amber-bg p-3.5">
              <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-tint-amber-fg" aria-hidden="true" />
              <div className="space-y-1 text-xs">
                <p className="font-bold text-on-surface">Un visitante está en portería esperando tu autorización</p>
                <p className="text-text-secondary">
                  Confirma si autorizas su ingreso a tu apartamento ({numeroApto}).
                </p>
              </div>
            </div>

            <div className="space-y-2">
              <h4 className="text-sm font-bold text-on-surface">{confirmarPendiente.titulo}</h4>
              <p className="text-xs text-text-secondary">{confirmarPendiente.cuerpo}</p>
            </div>

            {confirmarPendiente.fotoCaptura && (
              <div className="space-y-1.5">
                <span className="text-xs font-semibold text-text-secondary">Registro fotográfico de portería:</span>
                <img
                  src={imageSrc(confirmarPendiente.fotoCaptura)}
                  alt="Foto del visitante"
                  loading="lazy"
                  className="max-h-64 w-full rounded-md border border-border bg-black/5 object-contain"
                />
              </div>
            )}

            <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
              <Button
                variant="outline"
                onClick={() => responderConfirmacion(0)}
                disabled={confirmando}
                className="text-tint-red-fg hover:border-tint-red-fg/50 hover:bg-tint-red-bg"
              >
                Rechazar acceso
              </Button>
              <Button
                onClick={() => responderConfirmacion(1)}
                disabled={confirmando}
                className="bg-btn-success hover:bg-btn-accent-hover"
              >
                <CheckCircle2 aria-hidden="true" />
                {confirmando ? 'Confirmando…' : 'Confirmar acceso'}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* 6. MODAL PARA VISUALIZAR QR EN GRANDE */}
      <Modal open={!!qrZoom} onClose={() => setQrZoom(null)} title="Código QR de acceso">
        {qrZoom && (
          <div className="flex flex-col items-center justify-center space-y-4 p-4 text-center">
            <div className="flex flex-col items-center rounded-md border border-border bg-white p-4 shadow-sm">
              <LocalQRCode
                value={qrZoom.codigoQr}
                size={240}
                showDownload={true}
                downloadFileName={`saed-qr-${qrZoom.codigoQr}.png`}
                alt={`Pase QR de ${qrZoom.nombreVisitante || 'Visitante'}`}
              />
            </div>
            <div>
              <h3 className="text-lg font-bold text-on-surface">{qrZoom.nombreVisitante || 'Visitante autorizado'}</h3>
              <p className="mt-1 text-xs text-text-muted">
                Presenta este código al lector de la portería vehicular o peatonal
              </p>
              <p className="mt-0.5 font-mono text-xs text-text-muted">Código: #{qrZoom.codigoQr}</p>
              <p className="mt-1 text-xs font-semibold text-tint-amber-fg">
                Expira: {formatDateTime(qrZoom.fechaExpiracion)}
              </p>
            </div>
            <div className="flex items-center gap-2 pt-2">
              <Button variant="outline" onClick={() => copiarQR(qrZoom.codigoQr)}>
                <Copy className="h-3.5 w-3.5" aria-hidden="true" />
                Copiar código
              </Button>
              <Button onClick={() => setQrZoom(null)}>Cerrar</Button>
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}

/** Section card with a title row; the body owns its loading, error and empty states. */
function Panel({ icon: Icon, title, description, action, className, children }) {
  return (
    <section className={cn('rounded-lg border border-border bg-surface shadow-sm', className)}>
      <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-2 px-4 pt-4 sm:px-5 sm:pt-5">
        <div className="min-w-0">
          <h3 className="flex items-center gap-2 text-[15px] font-bold text-on-surface">
            {Icon && <Icon className="h-4 w-4 shrink-0 text-text-secondary" aria-hidden="true" />}
            {title}
          </h3>
          {description && <p className="mt-0.5 text-xs text-text-muted">{description}</p>}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
      <div className="p-4 sm:p-5">{children}</div>
    </section>
  );
}

/**
 * One line of "Lo que requiere tu atención". Items that need the resident are an Índigo piece;
 * settled ones stay as a ghosted dashed piece (the manual's built/unbuilt vocabulary).
 */
function AttentionRow({ row, onOpen }) {
  const { icon: Icon, title, count, detail, loading, error, onRetry, warn, actionLabel } = row;
  const needsAction = !loading && !error && count > 0;

  return (
    <li className="flex items-stretch">
      <button
        type="button"
        onClick={onOpen}
        aria-label={`${title}: ${loading ? 'cargando' : error ? 'sin datos' : count}. ${actionLabel}`}
        className="group flex min-w-0 flex-1 items-center gap-3 px-4 py-3.5 text-left transition-colors duration-150 hover:bg-surface-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring sm:gap-4 sm:px-5"
      >
        <span
          className={cn(
            'h-3 w-3 shrink-0 rounded-[3px]',
            needsAction
              ? 'bg-btn-primary ring-1 ring-[#4155C6] dark:bg-[rgba(98,116,232,0.72)] dark:ring-[#A3B0FF]'
              : 'border-[1.5px] border-dashed border-keyline/45'
          )}
          aria-hidden="true"
        />
        <Icon className="hidden h-5 w-5 shrink-0 text-text-secondary sm:block" aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold leading-snug text-on-surface">{title}</span>
          {loading ? (
            <span className="app-skeleton mt-1.5 block h-2.5 w-2/3" aria-hidden="true" />
          ) : (
            <span
              className={cn(
                'mt-0.5 line-clamp-2 text-xs sm:line-clamp-1',
                error ? 'font-semibold text-tint-red-fg' : warn ? 'font-semibold text-tint-amber-fg' : 'text-text-muted'
              )}
            >
              {error ? 'No se pudo cargar esta información.' : detail}
            </span>
          )}
        </span>
        <span
          className={cn(
            'app-numeral w-8 shrink-0 text-right text-[1.625rem]',
            needsAction ? 'text-primary-text' : 'text-text-muted'
          )}
          aria-hidden="true"
        >
          {loading ? '' : error ? '—' : count}
        </span>
        <ChevronRight
          className="h-4 w-4 shrink-0 text-text-muted transition-transform duration-150 ease-out group-hover:translate-x-0.5"
          aria-hidden="true"
        />
      </button>
      {error && onRetry && (
        <div className="flex items-center pr-3 sm:pr-4">
          <Button variant="outline" size="sm" onClick={onRetry} aria-label={`Reintentar: ${title}`}>
            <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
            <span className="hidden sm:inline">Reintentar</span>
          </Button>
        </div>
      )}
    </li>
  );
}
