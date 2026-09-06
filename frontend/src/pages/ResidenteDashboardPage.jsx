import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  ArrowRight,
  Bell,
  Building2,
  Calendar,
  CheckCircle2,
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
  Wallet,
} from 'lucide-react';
import { toast } from 'sonner';

import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import {
  formatCurrency,
  formatDate,
  formatDateTime,
  imageSrc,
  cn,
} from '../lib/utils.js';

import { PageContainer } from '../components/layout/PageContainer.jsx';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/button.tsx';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';

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
  const navigate = useNavigate();
  const residentId = user?.idResidente || user?.idPersona || user?.idUsuario;

  // 1. Perfil del residente
  const { data: perfilData, refetch: refetchPerfil } = useFetch(
    () => (residentId ? api.get(`/personas/${residentId}`) : Promise.resolve(null)),
    [residentId]
  );
  const perfil = useMemo(() => perfilData?.raw || perfilData || {}, [perfilData]);

  const nombreResidente = useMemo(() => {
    const pNombre = perfil.primerNombre || perfil.nombres || user?.nombreCompleto || 'Carlos';
    const sNombre = perfil.segundoNombre || '';
    const pApellido = perfil.primerApellido || perfil.apellidos || (user?.nombreCompleto ? '' : 'Martínez');
    const sApellido = perfil.segundoApellido || '';
    return `${pNombre} ${sNombre} ${pApellido} ${sApellido}`.replace(/\s+/g, ' ').trim();
  }, [perfil, user]);

  const iniciales = useMemo(() => {
    const partes = nombreResidente.split(' ').filter(Boolean);
    if (!partes.length) return 'RE';
    return (partes[0][0] + (partes[1]?.[0] || '')).toUpperCase();
  }, [nombreResidente]);

  // 2. Dashboard financiero y de unidad
  const { data: dashData, refetch: refetchDashboard } = useFetch(
    () => (residentId ? api.get(`/residentes/${residentId}/dashboard`) : Promise.resolve(null)),
    [residentId]
  );
  const dashboard = useMemo(() => dashData?.raw || dashData || {}, [dashData]);
  const aptoInfo = useMemo(() => dashboard.apartamento || {}, [dashboard]);
  const cuotas = useMemo(() => dashboard.cuotas || [], [dashboard]);

  // 3. Ficha básica de la unidad para el saludo
  const unitId =
    user?.idUnidad || perfil.idApartamento || perfil.idUnidad || aptoInfo.idApartamento || aptoInfo.id || 1;
  const { data: unitData, refetch: refetchUnit } = useFetch(
    () => (unitId ? api.get(`/units/${unitId}`) : Promise.resolve(null)),
    [unitId]
  );
  const u = useMemo(() => unitData?.raw || unitData || {}, [unitData]);

  const numeroApto =
    u.identificador ||
    u.numero ||
    aptoInfo.numero ||
    perfil.numeroApartamento ||
    (user?.idUnidad ? `Apto 20${user.idUnidad}` : 'Apto 101');

  // 4. Códigos QR activos para visitas
  const { data: qrsRaw, refetch: refetchQrs } = useFetch(
    () => (residentId ? api.get(`/residentes/${residentId}/qr-activos`) : Promise.resolve([])),
    [residentId]
  );
  const qrActivos = useMemo(() => (Array.isArray(qrsRaw) ? qrsRaw : qrsRaw?.items || []), [qrsRaw]);

  // 5. Buzón de novedades y paquetería
  const { data: buzonRaw, refetch: refetchBuzon } = useFetch(() => api.get('/buzon'), []);
  const buzonItems = useMemo(() => (Array.isArray(buzonRaw) ? buzonRaw : buzonRaw?.items || []), [buzonRaw]);

  const paquetesPendientes = useMemo(
    () => buzonItems.filter((m) => m.tipo === 'PAQUETE' && !m.leido),
    [buzonItems]
  );

  // 6. Avisos y comunicados oficiales de la administración
  const { data: avisosRaw, refetch: refetchAvisos } = useFetch(() => api.get('/buzon/avisos'), []);
  const avisosOficiales = useMemo(() => (Array.isArray(avisosRaw) ? avisosRaw : avisosRaw?.items || []), [avisosRaw]);

  // 7. Mis Solicitudes PQRS
  const { data: pqrsRaw, refetch: refetchPqrs } = useFetch(() => api.get('/pqrs/mis-tickets'), [user]);
  const misTickets = useMemo(() => (Array.isArray(pqrsRaw) ? pqrsRaw : pqrsRaw?.items || []), [pqrsRaw]);
  const ticketsEnTramite = useMemo(
    () => misTickets.filter((t) => t.estado !== 'CERRADO' && t.estado !== 'RESUELTO'),
    [misTickets]
  );

  // 8. Mis Reservas de Zonas Comunes
  const { data: reservasRaw, refetch: refetchReservas } = useFetch(() => api.get('/reservas/mis-reservas'), [user]);
  const misReservas = useMemo(() => (Array.isArray(reservasRaw) ? reservasRaw : reservasRaw?.items || []), [reservasRaw]);
  const reservasFuturas = useMemo(
    () => misReservas.filter((r) => r.estado === 'PENDIENTE' || r.estado === 'APROBADA'),
    [misReservas]
  );

  // 9. Historial Wompi
  const { data: wompiRaw, refetch: refetchWompi } = useFetch(() => api.get('/pagos/wompi/historial'), []);
  const wompiHistorial = useMemo(() => (Array.isArray(wompiRaw) ? wompiRaw : wompiRaw?.items || []), [wompiRaw]);

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
        return <Badge variant="secondary" className="font-semibold text-amber-600">Vencido</Badge>;
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

  function qrImageUrl(codigoQr) {
    return 'https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=' + encodeURIComponent(codigoQr);
  }

  function compartirTelegram(codigoQr, nombre) {
    const imgUrl = qrImageUrl(codigoQr);
    const text = encodeURIComponent(`Código QR de acceso para ${nombre || 'tu visita'}\n\nAbre la imagen para ingresar:\n${imgUrl}`);
    window.open(`https://t.me/share/url?url=${encodeURIComponent(imgUrl)}&text=${text}`, '_blank');
  }

  function compartirSMS(codigoQr, telefono) {
    const imgUrl = qrImageUrl(codigoQr);
    const body = encodeURIComponent(`Tu código QR de acceso en portería es: ${codigoQr} - Imagen: ${imgUrl}`);
    window.open(telefono ? `sms:${telefono}?body=${body}` : `sms:?body=${body}`);
  }

  function compartirCorreo(codigoQr, nombre, email) {
    const imgUrl = qrImageUrl(codigoQr);
    const subject = encodeURIComponent('Pase de Acceso con Código QR — SAED');
    const body = encodeURIComponent(
      `Hola,\n\nHas recibido un código QR de acceso${nombre ? ` para ${nombre}` : ''}.\n\n` +
      `Código: ${codigoQr}\n\nPresenta esta imagen al guardia de portería:\n${imgUrl}\n\n` +
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

  return (
    <PageContainer>
      {/* 1. HERO OPERATIVO DEL RESIDENTE (Sin ficha estática repetida) */}
      <div className="relative overflow-hidden rounded-2xl border border-primary/20 bg-gradient-to-br from-slate-900 via-primary/95 to-slate-900 text-white shadow-xl">
        <div
          className="absolute inset-0 opacity-5 pointer-events-none"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 30%, white 1px, transparent 1px), radial-gradient(circle at 80% 70%, white 1px, transparent 1px)',
            backgroundSize: '24px 24px',
          }}
        />

        <div className="relative p-6 sm:p-8 flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
          <div className="flex items-center gap-4 sm:gap-5 min-w-0">
            <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-2xl bg-white/10 backdrop-blur-md border border-white/20 flex items-center justify-center text-white text-xl sm:text-2xl font-black shrink-0 shadow-lg">
              {iniciales}
            </div>
            <div className="space-y-1.5 min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-white truncate">
                  {nombreResidente}
                </h1>
                <Badge variant="secondary" className="bg-white/20 text-white border-white/20">
                  Residente Titular
                </Badge>
                {alDia ? (
                  <Badge variant="outline" className="bg-emerald-500/20 text-emerald-300 border-emerald-500/30">
                    <CheckCircle2 className="w-3 h-3 mr-1" />
                    Paz y Salvo Vigente
                  </Badge>
                ) : (
                  <Badge variant="outline" className="bg-amber-500/20 text-amber-300 border-amber-500/30">
                    <AlertCircle className="w-3 h-3 mr-1" />
                    {formatCurrency(totalDeudaPendiente)} Pendiente
                  </Badge>
                )}
              </div>
              <p className="text-sm text-white/80 font-medium flex items-center gap-2 flex-wrap">
                <span className="flex items-center gap-1.5 text-white font-semibold">
                  <Home className="w-4 h-4 text-emerald-400 shrink-0" />
                  {numeroApto}
                </span>
                <span className="text-white/40">•</span>
                <span className="text-white/70">{user?.nombrePropiedad || 'Edificio Residencial SAED'}</span>
                <span className="text-white/40">•</span>
                <span className="text-emerald-300 text-xs">
                  {paquetesPendientes.length > 0
                    ? `${paquetesPendientes.length} paquete(s) por retirar`
                    : 'Sin entregas pendientes'}
                </span>
              </p>
            </div>
          </div>

          {/* Acciones de Cabecera */}
          <div className="w-full md:w-auto flex items-center justify-between md:justify-end gap-3 border-t md:border-t-0 border-white/15 pt-4 md:pt-0">
            <Button
              variant="outline"
              size="sm"
              onClick={refetchAll}
              disabled={refreshing}
              className="bg-white/10 hover:bg-white/20 text-white border-white/25 shadow-sm gap-1.5"
            >
              <RefreshCw className={cn('w-3.5 h-3.5', refreshing && 'animate-spin')} />
              <span className="hidden sm:inline">Actualizar</span>
            </Button>
            <Button
              variant="default"
              size="sm"
              onClick={() => navigate('/res-perfil')}
              className="bg-white text-slate-900 hover:bg-white/90 shadow-md font-semibold gap-1.5"
            >
              <Building2 className="w-3.5 h-3.5 text-primary" />
              Mi Perfil Completo
            </Button>
          </div>
        </div>
      </div>

      {/* 2. STRIP DE 4 KPIS OPERATIVOS (Enfocados en el día a día) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* KPI 1: Cartera & Obligaciones */}
        <Card
          onClick={() => navigate('/res-cuotas')}
          className="hover:shadow-md transition-all duration-200 hover:-translate-y-0.5 border-border/70 cursor-pointer group"
        >
          <CardContent className="p-5 flex items-center gap-4">
            <div
              className={cn(
                'p-3 rounded-xl shrink-0 transition-transform group-hover:scale-105',
                alDia
                  ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                  : 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
              )}
            >
              <Wallet className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Estado de Cartera</p>
                <ArrowRight className="w-3.5 h-3.5 text-muted-foreground group-hover:text-primary transition-colors" />
              </div>
              <h3 className="text-xl font-bold text-foreground truncate">
                {alDia ? 'Al Día' : formatCurrency(totalDeudaPendiente)}
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                {alDia ? 'Paz y Salvo vigente' : `${cuotasPendientes.length} cuota(s) pendiente(s)`}
              </p>
            </div>
          </CardContent>
        </Card>

        {/* KPI 2: Paquetería en Portería */}
        <Card
          onClick={() => navigate('/res-buzon')}
          className="hover:shadow-md transition-all duration-200 hover:-translate-y-0.5 border-border/70 cursor-pointer group"
        >
          <CardContent className="p-5 flex items-center gap-4">
            <div
              className={cn(
                'p-3 rounded-xl shrink-0 transition-transform group-hover:scale-105',
                paquetesPendientes.length > 0
                  ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
                  : 'bg-slate-500/10 text-slate-600 dark:text-slate-400'
              )}
            >
              <Package className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Correspondencia</p>
                <ArrowRight className="w-3.5 h-3.5 text-muted-foreground group-hover:text-primary transition-colors" />
              </div>
              <h3 className="text-xl font-bold text-foreground truncate">
                {paquetesPendientes.length > 0 ? `${paquetesPendientes.length} en espera` : '0 paquetes'}
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                {paquetesPendientes.length > 0 ? 'En custodia en portería' : 'Recepción al día'}
              </p>
            </div>
          </CardContent>
        </Card>

        {/* KPI 3: Control de Visitas QR */}
        <Card
          onClick={() => navigate('/res-visitas')}
          className="hover:shadow-md transition-all duration-200 hover:-translate-y-0.5 border-border/70 cursor-pointer group"
        >
          <CardContent className="p-5 flex items-center gap-4">
            <div className="p-3 rounded-xl bg-purple-500/10 text-purple-600 dark:text-purple-400 shrink-0 transition-transform group-hover:scale-105">
              <QrCode className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Pases de Acceso</p>
                <ArrowRight className="w-3.5 h-3.5 text-muted-foreground group-hover:text-primary transition-colors" />
              </div>
              <h3 className="text-xl font-bold text-foreground truncate">
                {qrActivos.length} Activo(s)
              </h3>
              <p className="text-xs text-muted-foreground truncate">Invitaciones para portería</p>
            </div>
          </CardContent>
        </Card>

        {/* KPI 4: Gestiones y Solicitudes en Curso */}
        <Card
          onClick={() => navigate('/res-quejas')}
          className="hover:shadow-md transition-all duration-200 hover:-translate-y-0.5 border-border/70 cursor-pointer group"
        >
          <CardContent className="p-5 flex items-center gap-4">
            <div className="p-3 rounded-xl bg-blue-500/10 text-blue-600 dark:text-blue-400 shrink-0 transition-transform group-hover:scale-105">
              <FileText className="w-6 h-6" />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center justify-between">
                <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Gestiones en Curso</p>
                <ArrowRight className="w-3.5 h-3.5 text-muted-foreground group-hover:text-primary transition-colors" />
              </div>
              <h3 className="text-xl font-bold text-foreground truncate">
                {ticketsEnTramite.length + reservasFuturas.length} Trámite(s)
              </h3>
              <p className="text-xs text-muted-foreground truncate">
                {ticketsEnTramite.length} PQRS · {reservasFuturas.length} Reserva(s)
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* 3. BARRA DE ACCIONES RÁPIDAS DE AUTOSERVICIO */}
      <Card className="border-border/80 bg-card/60 backdrop-blur-sm">
        <CardContent className="p-4 sm:p-5">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-primary" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                Acciones Rápidas & Autoservicio
              </h3>
            </div>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
            <Button
              variant="outline"
              onClick={() => navigate('/res-visitas')}
              className="h-auto py-3 px-3 flex flex-col items-center justify-center text-center gap-1.5 hover:border-primary/50 hover:bg-primary/5"
            >
              <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
                <Plus className="w-4 h-4" />
              </div>
              <span className="text-xs font-semibold text-foreground">Nueva Visita</span>
            </Button>

            <Button
              variant="outline"
              onClick={() => navigate('/res-cuotas')}
              className="h-auto py-3 px-3 flex flex-col items-center justify-center text-center gap-1.5 hover:border-primary/50 hover:bg-primary/5"
            >
              <div className="p-2 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
                <CreditCard className="w-4 h-4" />
              </div>
              <span className="text-xs font-semibold text-foreground">Pagar Cuotas</span>
            </Button>

            <Button
              variant="outline"
              onClick={() => navigate('/res-reservas')}
              className="h-auto py-3 px-3 flex flex-col items-center justify-center text-center gap-1.5 hover:border-primary/50 hover:bg-primary/5"
            >
              <div className="p-2 rounded-lg bg-purple-500/10 text-purple-600 dark:text-purple-400">
                <Calendar className="w-4 h-4" />
              </div>
              <span className="text-xs font-semibold text-foreground">Reservar Zona</span>
            </Button>

            <Button
              variant="outline"
              onClick={() => navigate('/res-quejas')}
              className="h-auto py-3 px-3 flex flex-col items-center justify-center text-center gap-1.5 hover:border-primary/50 hover:bg-primary/5"
            >
              <div className="p-2 rounded-lg bg-rose-500/10 text-rose-600 dark:text-rose-400">
                <MessageSquare className="w-4 h-4" />
              </div>
              <span className="text-xs font-semibold text-foreground">Radicar PQRS</span>
            </Button>

            <Button
              variant="outline"
              onClick={() => navigate('/res-visitas')}
              className="h-auto py-3 px-3 flex flex-col items-center justify-center text-center gap-1.5 hover:border-primary/50 hover:bg-primary/5"
            >
              <div className="p-2 rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400">
                <Users className="w-4 h-4" />
              </div>
              <span className="text-xs font-semibold text-foreground">Visitas</span>
            </Button>

            <Button
              variant="outline"
              onClick={() => navigate('/res-buzon')}
              className="h-auto py-3 px-3 flex flex-col items-center justify-center text-center gap-1.5 hover:border-primary/50 hover:bg-primary/5"
            >
              <div className="p-2 rounded-lg bg-slate-500/10 text-slate-600 dark:text-slate-400">
                <Bell className="w-4 h-4" />
              </div>
              <span className="text-xs font-semibold text-foreground">Buzón & Avisos</span>
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 4. PESTAÑAS PRINCIPALES DEL DASHBOARD */}
      <Tabs defaultValue="resumen" className="space-y-6">
        <TabsList className="grid w-full grid-cols-3 max-w-xl h-11 p-1 bg-muted/80 rounded-xl border border-border">
          <TabsTrigger value="resumen" className="gap-2 text-xs sm:text-sm font-semibold">
            <Sparkles className="w-4 h-4" />
            Resumen Diario
          </TabsTrigger>
          <TabsTrigger value="finanzas" className="gap-2 text-xs sm:text-sm font-semibold">
            <CreditCard className="w-4 h-4" />
            Finanzas & Wompi
            {cuotasPendientes.length > 0 && (
              <Badge variant="destructive" className="ml-1 text-[10px] px-1.5 py-0">
                {cuotasPendientes.length}
              </Badge>
            )}
          </TabsTrigger>
          <TabsTrigger value="comunidad" className="gap-2 text-xs sm:text-sm font-semibold">
            <Bell className="w-4 h-4" />
            Avisos de Administración
            {avisosOficiales.length > 0 && (
              <Badge variant="secondary" className="ml-1 text-[10px] px-1.5 py-0">
                {avisosOficiales.length}
              </Badge>
            )}
          </TabsTrigger>
        </TabsList>

        {/* TAB 1: RESUMEN DIARIO & OPERACIONES (Sin ficha estática) */}
        <TabsContent value="resumen" className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Columna Izquierda (2 Cols): Pagos, Pases y Solicitudes en curso */}
            <div className="lg:col-span-2 space-y-6">
              {/* 1.1: Pagos y Cuotas Inmediatas (Wompi) */}
              <Card>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg flex items-center gap-2">
                        <Wallet className="w-5 h-5 text-primary" />
                        Obligaciones & Cuotas del Mes
                      </CardTitle>
                      <CardDescription>
                        Pago directo en línea con PSE, Nequi, Bancolombia o tarjeta
                      </CardDescription>
                    </div>
                    {alDia ? (
                      <Badge variant="success" className="font-semibold gap-1">
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        Paz y Salvo Vigente
                      </Badge>
                    ) : (
                      <Badge variant="destructive" className="font-semibold">
                        {formatCurrency(totalDeudaPendiente)} Pendiente
                      </Badge>
                    )}
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  {cuotasPendientes.length === 0 && multasPendientesList.length === 0 ? (
                    <div className="p-6 rounded-xl border border-emerald-500/20 bg-emerald-500/5 text-center space-y-2">
                      <CheckCircle2 className="w-10 h-10 text-emerald-500 mx-auto" />
                      <h4 className="text-base font-bold text-foreground">¡Estás al día con la administración!</h4>
                      <p className="text-xs text-muted-foreground max-w-md mx-auto">
                        No registras cobros pendientes para el inmueble {numeroApto}. Tu historial de pagos se encuentra validado.
                      </p>
                      <div className="pt-2">
                        <Button variant="outline" size="sm" onClick={() => navigate('/res-cuotas')} className="text-xs gap-1.5">
                          <FileText className="w-3.5 h-3.5" />
                          Ver Historial de Recibos
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <div className="space-y-2.5">
                      {cuotasPendientes.map((c) => (
                        <div
                          key={`c-${c.idCuota}`}
                          className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl border border-border bg-card hover:bg-muted/20 transition-all"
                        >
                          <div className="space-y-0.5">
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-bold text-foreground">
                                {c.tipoCuota === 'ADMINISTRACION' ? 'Cuota de Administración' : 'Cuota Extraordinaria'}
                              </span>
                              <Badge variant="outline" className="text-[10px]">
                                {MESES_W[(c.mes || 1) - 1]} {c.anio}
                              </Badge>
                            </div>
                            <p className="text-xs text-muted-foreground">
                              {c.fechaLimite ? `Vencimiento: ${formatDate(c.fechaLimite)}` : 'Cobro reglamentario'}
                            </p>
                          </div>
                          <div className="flex items-center justify-between sm:justify-end gap-4">
                            <div className="text-right">
                              <span className="text-xs text-muted-foreground block">Monto a Pagar</span>
                              <span className="text-base font-black text-foreground font-mono">
                                {formatCurrency(c.saldoPendiente ?? c.valorTotal)}
                              </span>
                            </div>
                            <Button
                              onClick={() => pagarConWompi('CUOTA', c.idCuota, `Cuota ${c.anio}/${c.mes}`)}
                              disabled={!!pagando}
                              className="bg-emerald-600 hover:bg-emerald-500 text-white font-semibold gap-1.5 shadow-sm shrink-0"
                            >
                              <CreditCard className="w-4 h-4" />
                              {pagando?.id === c.idCuota && pagando?.concepto === 'CUOTA' ? 'Abriendo...' : 'Pagar'}
                            </Button>
                          </div>
                        </div>
                      ))}

                      {multasPendientesList.map((m) => (
                        <div
                          key={`m-${m.idMulta}`}
                          className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 rounded-xl border border-rose-500/20 bg-rose-500/5"
                        >
                          <div className="space-y-0.5">
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-bold text-foreground">Sanción / Multa</span>
                              <Badge variant="destructive" className="text-[10px]">{m.tipo}</Badge>
                            </div>
                            <p className="text-xs text-muted-foreground">{m.descripcion || 'Incumplimiento al reglamento'}</p>
                          </div>
                          <div className="flex items-center justify-between sm:justify-end gap-4">
                            <div className="text-right">
                              <span className="text-xs text-muted-foreground block">Monto</span>
                              <span className="text-base font-black text-rose-600 dark:text-rose-400 font-mono">
                                {formatCurrency(m.monto)}
                              </span>
                            </div>
                            <Button
                              variant="outline"
                              onClick={() => pagarConWompi('MULTA', m.idMulta, `Multa ${m.tipo}`)}
                              disabled={!!pagando}
                              className="font-semibold gap-1.5 border-rose-300 text-rose-700 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-300 shrink-0"
                            >
                              <CreditCard className="w-4 h-4" />
                              {pagando?.id === m.idMulta && pagando?.concepto === 'MULTA' ? 'Abriendo...' : 'Pagar'}
                            </Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* 1.2: Pases de Visita QR Activos */}
              <Card>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg flex items-center gap-2">
                        <QrCode className="w-5 h-5 text-primary" />
                        Pases de Acceso Activos ({qrActivos.length})
                      </CardTitle>
                      <CardDescription>
                        Invitaciones vigentes autorizadas para lectura en portería
                      </CardDescription>
                    </div>
                    <Button
                      size="sm"
                      onClick={() => navigate('/res-visitas')}
                      className="gap-1 text-xs shadow-sm"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      Nueva Visita
                    </Button>
                  </div>
                </CardHeader>
                <CardContent>
                  {qrActivos.length === 0 ? (
                    <div className="p-6 rounded-xl border border-dashed border-border text-center space-y-2">
                      <QrCode className="w-8 h-8 text-muted-foreground mx-auto opacity-50" />
                      <p className="text-sm font-semibold text-foreground">No tienes códigos QR activos en este momento</p>
                      <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                        Genera una invitación rápida con QR para que tus amigos, familiares o domiciliarios ingresen sin demoras.
                      </p>
                      <div className="pt-2">
                        <Button size="sm" onClick={() => navigate('/res-visitas')} className="gap-1.5">
                          <Plus className="w-3.5 h-3.5" />
                          Crear Autorización de Acceso
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      {qrActivos.map((qr) => (
                        <div
                          key={qr.idQr}
                          className="flex flex-col justify-between p-4 rounded-xl border border-border bg-card hover:shadow-sm transition-all"
                        >
                          <div className="flex items-start gap-3.5">
                            <img
                              src={qrImageUrl(qr.codigoQr)}
                              alt={`QR ${qr.nombreVisitante || 'Visita'}`}
                              width="64"
                              height="64"
                              onClick={() => setQrZoom(qr)}
                              className="w-16 h-16 rounded-lg border border-border cursor-zoom-in bg-white p-1 shrink-0"
                            />
                            <div className="min-w-0 flex-1">
                              <h4 className="text-sm font-bold text-foreground truncate">
                                {qr.nombreVisitante || 'Visitante Autorizado'}
                              </h4>
                              <p className="text-xs text-muted-foreground flex items-center gap-1 mt-0.5">
                                <Users className="w-3 h-3" />
                                {qr.cantidadPersonas || 1} persona(s)
                              </p>
                              <p className="text-[11px] text-muted-foreground flex items-center gap-1 mt-0.5">
                                <Clock className="w-3 h-3 text-amber-500" />
                                Expira: {formatDateTime(qr.fechaExpiracion)}
                              </p>
                            </div>
                          </div>

                          <div className="mt-3 pt-3 border-t border-border flex items-center justify-between gap-1">
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => setQrZoom(qr)}
                              className="text-xs text-primary px-2 h-8 gap-1"
                            >
                              <Eye className="w-3.5 h-3.5" />
                              Ver
                            </Button>
                            <div className="flex items-center gap-1">
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => compartirTelegram(qr.codigoQr, qr.nombreVisitante)}
                                title="Enviar por Telegram"
                                className="h-8 px-2 text-xs"
                              >
                                <Send className="w-3 h-3 text-blue-500" />
                              </Button>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => compartirSMS(qr.codigoQr, '')}
                                title="Enviar por SMS"
                                className="h-8 px-2 text-xs"
                              >
                                <Phone className="w-3 h-3 text-emerald-500" />
                              </Button>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => compartirCorreo(qr.codigoQr, qr.nombreVisitante, '')}
                                title="Enviar por Correo"
                                className="h-8 px-2 text-xs"
                              >
                                <Mail className="w-3 h-3 text-purple-500" />
                              </Button>
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => copiarQR(qr.codigoQr)}
                                title="Copiar código al portapapeles"
                                className="h-8 px-2 text-xs"
                              >
                                <Copy className="w-3 h-3 text-slate-500" />
                              </Button>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* 1.3: Mis Solicitudes PQRS en Trámite */}
              <Card>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <CardTitle className="text-lg flex items-center gap-2">
                        <MessageSquare className="w-5 h-5 text-primary" />
                        Mis Solicitudes & Reportes en Trámite ({ticketsEnTramite.length})
                      </CardTitle>
                      <CardDescription>
                        Seguimiento en tiempo real a peticiones y quejas enviadas a la administración
                      </CardDescription>
                    </div>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => navigate('/res-quejas')}
                      className="gap-1 text-xs"
                    >
                      <Plus className="w-3.5 h-3.5" />
                      Radicar PQRS
                    </Button>
                  </div>
                </CardHeader>
                <CardContent>
                  {ticketsEnTramite.length === 0 ? (
                    <div className="p-4 rounded-xl border border-dashed border-border text-center space-y-1">
                      <CheckCircle2 className="w-7 h-7 text-emerald-500 mx-auto opacity-60" />
                      <p className="text-xs font-semibold text-foreground">No tienes solicitudes pendientes</p>
                      <p className="text-[11px] text-muted-foreground">
                        Todas tus peticiones o reportes anteriores han sido atendidos y cerrados.
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-2.5">
                      {ticketsEnTramite.slice(0, 4).map((t) => (
                        <div
                          key={t.idTicket}
                          className="flex items-center justify-between p-3.5 rounded-xl border border-border bg-card hover:bg-muted/20 transition-colors"
                        >
                          <div className="space-y-1 min-w-0 flex-1 pr-3">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="text-xs font-bold text-foreground truncate">{t.asunto}</span>
                              <Badge
                                variant={
                                  t.estado === 'RESUELTO'
                                    ? 'success'
                                    : t.estado === 'EN_REVISION'
                                    ? 'default'
                                    : 'secondary'
                                }
                                className="text-[10px]"
                              >
                                {t.estado === 'EN_REVISION' ? 'En Revisión' : t.estado}
                              </Badge>
                            </div>
                            <p className="text-xs text-muted-foreground line-clamp-1">
                              {t.descripcion || 'Sin detalles'}
                            </p>
                            <span className="text-[10px] text-muted-foreground block">
                              Radicado: {formatDate(t.fechaCreacion)} · Categoría: {t.categoria || 'Administración'}
                            </span>
                          </div>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => navigate('/res-quejas')}
                            className="h-8 text-xs text-primary shrink-0"
                          >
                            Ver →
                          </Button>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>

            {/* Columna Derecha (1 Col): Paquetería, Reservas y Contacto Rápido */}
            <div className="space-y-6">
              {/* 1. Paquetes en Custodia de Portería */}
              <Card>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-base flex items-center gap-2">
                      <Package className="w-4 h-4 text-amber-500" />
                      Paquetes en Portería
                    </CardTitle>
                    {paquetesPendientes.length > 0 && (
                      <Badge variant="warning" className="text-[10px]">
                        {paquetesPendientes.length} Pendiente(s)
                      </Badge>
                    )}
                  </div>
                  <CardDescription>Correspondencia y encomiendas en recepción</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  {paquetesPendientes.length === 0 ? (
                    <div className="p-4 rounded-xl border border-dashed border-border text-center space-y-1">
                      <Package className="w-7 h-7 text-muted-foreground mx-auto opacity-40" />
                      <p className="text-xs font-semibold text-foreground">Sin paquetes en portería</p>
                      <p className="text-[11px] text-muted-foreground">
                        Cuando recibas un paquete el personal de vigilancia lo registrará y te notificará de inmediato.
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-2">
                      {paquetesPendientes.map((p) => (
                        <div
                          key={p.idMensaje}
                          className="p-3 rounded-lg border border-amber-500/30 bg-amber-500/5 space-y-1"
                        >
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-bold text-foreground">{p.titulo}</span>
                            <span className="text-[10px] text-muted-foreground font-mono">
                              {formatDate(p.fechaCreacion)}
                            </span>
                          </div>
                          {p.cuerpo && <p className="text-xs text-muted-foreground">{p.cuerpo}</p>}
                          <div className="pt-1 flex items-center justify-between">
                            <span className="text-[10px] font-semibold text-amber-600 dark:text-amber-400">
                              En custodia en portería
                            </span>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => navigate('/res-buzon')}
                              className="h-6 text-[11px] text-primary p-0 hover:bg-transparent"
                            >
                              Ver detalles →
                            </Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* 2. Próximas Reservas de Zonas Comunes */}
              <Card>
                <CardHeader className="pb-3">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-base flex items-center gap-2">
                      <Calendar className="w-4 h-4 text-purple-500" />
                      Mis Próximas Reservas
                    </CardTitle>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => navigate('/res-reservas')}
                      className="text-xs text-primary p-0 h-auto"
                    >
                      Reservar →
                    </Button>
                  </div>
                  <CardDescription>Zonas sociales y áreas comunes</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  {reservasFuturas.length === 0 ? (
                    <div className="p-4 rounded-xl border border-dashed border-border text-center space-y-1">
                      <Calendar className="w-6 h-6 text-muted-foreground mx-auto opacity-40" />
                      <p className="text-xs font-semibold text-foreground">Sin reservas programadas</p>
                      <p className="text-[11px] text-muted-foreground">
                        Reserva el salón social, zona BBQ o cancha para tus eventos.
                      </p>
                    </div>
                  ) : (
                    <div className="space-y-2.5">
                      {reservasFuturas.slice(0, 3).map((r) => (
                        <div
                          key={r.idReserva}
                          className="p-3 rounded-lg border border-border bg-card/60 space-y-1 hover:bg-muted/20 transition-colors"
                        >
                          <div className="flex items-center justify-between">
                            <span className="text-xs font-bold text-foreground">
                              {r.nombreZona || 'Zona Común'}
                            </span>
                            <Badge
                              variant={r.estado === 'APROBADA' ? 'success' : 'secondary'}
                              className="text-[10px]"
                            >
                              {r.estado}
                            </Badge>
                          </div>
                          <p className="text-xs text-muted-foreground flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            {formatDate(r.fechaReserva)} · {r.horaInicio || '14:00'} - {r.horaFin || '18:00'}
                          </p>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* 3. Canales de Asistencia, Portería & Emergencias */}
              <Card className="border-border/80">
                <CardHeader className="pb-3">
                  <CardTitle className="text-base flex items-center gap-2">
                    <Phone className="w-4 h-4 text-primary" />
                    Canales de Asistencia Inmediata
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-2.5 text-xs">
                  <div className="flex justify-between items-center py-2 border-b border-border/50">
                    <div>
                      <p className="font-bold text-foreground">Portería Principal (24/7)</p>
                      <p className="text-muted-foreground">Citofonía directa</p>
                    </div>
                    <span className="font-mono font-bold text-primary bg-primary/10 px-2 py-0.5 rounded">
                      Ext. 100
                    </span>
                  </div>

                  <div className="flex justify-between items-center py-2 border-b border-border/50">
                    <div>
                      <p className="font-bold text-foreground">Oficina de Administración</p>
                      <p className="text-muted-foreground">Lunes a Viernes 8am - 5pm</p>
                    </div>
                    <span className="font-mono font-semibold text-foreground">
                      (601) 321 4567
                    </span>
                  </div>

                  <div className="flex justify-between items-center py-2">
                    <div>
                      <p className="font-bold text-rose-600 dark:text-rose-400">Línea Única de Emergencias</p>
                      <p className="text-muted-foreground">Policía / Bomberos / Ambulancias</p>
                    </div>
                    <span className="font-mono font-bold text-rose-600 dark:text-rose-400 bg-rose-500/10 px-2 py-0.5 rounded">
                      123
                    </span>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        </TabsContent>

        {/* TAB 2: FINANZAS & HISTORIAL WOMPI */}
        <TabsContent value="finanzas" className="space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* 2.1: Historial Wompi & Pasarela */}
            <Card className="lg:col-span-2">
              <CardHeader className="pb-3">
                <CardTitle className="text-lg flex items-center gap-2">
                  <CreditCard className="w-5 h-5 text-primary" />
                  Transacciones en Línea (Wompi)
                </CardTitle>
                <CardDescription>
                  Registro de pagos procesados mediante pasarela bancaria certificada
                </CardDescription>
              </CardHeader>
              <CardContent>
                {wompiHistorial.length === 0 ? (
                  <EmptyState
                    icon="payments"
                    title="No hay transacciones en línea recientes"
                    subtitle="Cuando efectúes un pago con Wompi, el comprobante y estado bancario se listarán aquí."
                  />
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="border-b border-border text-muted-foreground text-left">
                          <th className="py-2.5 px-3 font-semibold">Referencia</th>
                          <th className="py-2.5 px-3 font-semibold">Fecha</th>
                          <th className="py-2.5 px-3 font-semibold text-right">Monto</th>
                          <th className="py-2.5 px-3 font-semibold text-center">Estado</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border/60">
                        {wompiHistorial.slice(0, 8).map((h) => (
                          <tr key={h.referencia} className="hover:bg-muted/20">
                            <td className="py-3 px-3 font-mono text-foreground">
                              #{String(h.referencia).slice(0, 20)}...
                            </td>
                            <td className="py-3 px-3 text-muted-foreground">
                              {formatDate(h.fechaCreacion || new Date())}
                            </td>
                            <td className="py-3 px-3 font-bold text-foreground text-right font-mono">
                              {formatCurrency(h.montoCentavos ? h.montoCentavos / 100 : h.monto)}
                            </td>
                            <td className="py-3 px-3 text-center">
                              {badgeWompi(h.estado)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </CardContent>
            </Card>

            {/* 2.2: Métodos de Pago & Resumen de Cartera */}
            <div className="space-y-6">
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base flex items-center gap-2">
                    <ShieldCheck className="w-4 h-4 text-emerald-500" />
                    Métodos de Pago Habilitados
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3 text-xs">
                  <div className="p-3 rounded-lg border border-border bg-card/60 flex items-center gap-3">
                    <div className="p-2 rounded-md bg-primary/10 text-primary font-bold">PSE</div>
                    <div>
                      <p className="font-bold text-foreground">Débito Bancario (PSE)</p>
                      <p className="text-muted-foreground">Todos los bancos de Colombia</p>
                    </div>
                  </div>
                  <div className="p-3 rounded-lg border border-border bg-card/60 flex items-center gap-3">
                    <div className="p-2 rounded-md bg-purple-500/10 text-purple-600 font-bold">NEQUI</div>
                    <div>
                      <p className="font-bold text-foreground">Nequi & Daviplata</p>
                      <p className="text-muted-foreground">Notificación y débito al instante</p>
                    </div>
                  </div>
                  <div className="p-3 rounded-lg border border-border bg-card/60 flex items-center gap-3">
                    <div className="p-2 rounded-md bg-blue-500/10 text-blue-600 font-bold">VISA</div>
                    <div>
                      <p className="font-bold text-foreground">Tarjetas de Crédito / Débito</p>
                      <p className="text-muted-foreground">Visa, Mastercard, American Express</p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base flex items-center gap-2">
                    <FileText className="w-4 h-4 text-primary" />
                    Paz y Salvo de Administración
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  <p className="text-xs text-muted-foreground">
                    Para trámites notariales, arriendos o traspasos de inmueble, solicita tu certificado oficial de paz y salvo expedido por la administración.
                  </p>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => navigate('/res-cuotas')}
                    className="w-full gap-2 text-xs"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                    Ir al Portal Financiero
                  </Button>
                </CardContent>
              </Card>
            </div>
          </div>
        </TabsContent>

        {/* TAB 3: COMUNIDAD & AVISOS DE ADMINISTRACIÓN */}
        <TabsContent value="comunidad" className="space-y-6">
          <Card>
            <CardHeader className="pb-3">
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-lg flex items-center gap-2">
                    <Bell className="w-5 h-5 text-primary" />
                    Circulares y Comunicados de la Administración
                  </CardTitle>
                  <CardDescription>
                    Información oficial sobre mantenimientos, asambleas y convivencia
                  </CardDescription>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={refetchAvisos}
                  className="gap-1 text-xs"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  Actualizar
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {avisosOficiales.length === 0 ? (
                <EmptyState
                  icon="campaign"
                  title="No hay circulares publicadas recientemente"
                  subtitle="Cuando la administración emita comunicados o convocatorias oficiales, aparecerán en este tablón."
                />
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {avisosOficiales.map((aviso, idx) => (
                    <div
                      key={aviso.idMensaje || aviso.idComunicado || `aviso-${idx}`}
                      className="p-5 rounded-xl border border-border bg-card hover:shadow-sm transition-all space-y-2.5 flex flex-col justify-between"
                    >
                      <div className="space-y-2">
                        <div className="flex items-center justify-between gap-2">
                          <h4 className="text-sm font-bold text-foreground">
                            {aviso.titulo || aviso.TITULO || 'Comunicado Oficial'}
                          </h4>
                          <Badge variant="outline" className="text-[10px] shrink-0">
                            {formatDate(aviso.fechaCreacion || aviso.FECHA_PUBLICACION || new Date())}
                          </Badge>
                        </div>
                        <p className="text-xs text-muted-foreground whitespace-pre-line leading-relaxed">
                          {aviso.cuerpo || aviso.CONTENIDO || aviso.mensaje || ''}
                        </p>
                      </div>
                      <div className="pt-2 border-t border-border/50 flex items-center justify-between text-[11px] text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <Building2 className="w-3 h-3 text-primary" />
                          Administración SAED
                        </span>
                        <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                          Vigente
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* 5. MODAL DE CONFIRMACIÓN DE VISITAS EN TIEMPO REAL (PORTERÍA B1) */}
      <Modal
        open={!!confirmarPendiente}
        onClose={() => setConfirmarPendiente(null)}
        title="Solicitud de Acceso en Portería"
      >
        {confirmarPendiente && (
          <div className="space-y-4 py-2">
            <div className="p-3.5 rounded-xl border border-amber-500/30 bg-amber-500/10 flex items-start gap-3">
              <ShieldAlert className="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
              <div className="text-xs space-y-1">
                <p className="font-bold text-foreground">Un visitante se encuentra en portería esperando tu autorización</p>
                <p className="text-muted-foreground">
                  Confirma si autorizas su ingreso hacia tu apartamento ({numeroApto}).
                </p>
              </div>
            </div>

            <div className="space-y-2">
              <h4 className="text-sm font-bold text-foreground">{confirmarPendiente.titulo}</h4>
              <p className="text-xs text-muted-foreground">{confirmarPendiente.cuerpo}</p>
            </div>

            {confirmarPendiente.fotoCaptura && (
              <div className="space-y-1.5">
                <span className="text-xs font-semibold text-muted-foreground">Registro fotográfico de portería:</span>
                <img
                  src={imageSrc(confirmarPendiente.fotoCaptura)}
                  alt="Foto del visitante"
                  loading="lazy"
                  className="w-full max-h-64 object-contain rounded-xl border border-border bg-black/5"
                />
              </div>
            )}

            <div className="flex items-center justify-end gap-3 pt-2">
              <Button
                variant="outline"
                onClick={() => responderConfirmacion(0)}
                disabled={confirmando}
                className="text-rose-600 border-rose-200 hover:bg-rose-50 dark:border-rose-800 dark:text-rose-400"
              >
                Rechazar Acceso
              </Button>
              <Button
                onClick={() => responderConfirmacion(1)}
                disabled={confirmando}
                className="bg-emerald-600 hover:bg-emerald-500 text-white gap-2 font-semibold"
              >
                <CheckCircle2 className="w-4 h-4" />
                {confirmando ? 'Confirmando...' : 'Confirmar Acceso'}
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* 6. MODAL PARA VISUALIZAR QR EN GRANDE */}
      <Modal
        open={!!qrZoom}
        onClose={() => setQrZoom(null)}
        title="Código QR de Acceso"
      >
        {qrZoom && (
          <div className="flex flex-col items-center justify-center p-4 space-y-4 text-center">
            <div className="p-4 bg-white rounded-2xl border-2 border-primary/20 shadow-md">
              <img
                src={qrImageUrl(qrZoom.codigoQr)}
                alt="QR Ampliado"
                className="w-64 h-64 mx-auto"
              />
            </div>
            <div>
              <h3 className="text-lg font-bold text-foreground">{qrZoom.nombreVisitante || 'Visitante Autorizado'}</h3>
              <p className="text-xs text-muted-foreground mt-1">
                Presenta este código al lector de la portería vehicular o peatonal
              </p>
              <p className="text-xs font-mono text-muted-foreground mt-0.5">
                Código: #{qrZoom.codigoQr}
              </p>
              <p className="text-xs text-amber-600 dark:text-amber-400 font-semibold mt-1">
                Expira: {formatDateTime(qrZoom.fechaExpiracion)}
              </p>
            </div>
            <div className="flex items-center gap-2 pt-2">
              <Button variant="outline" onClick={() => copiarQR(qrZoom.codigoQr)} className="gap-1.5 text-xs">
                <Copy className="w-3.5 h-3.5" />
                Copiar Código
              </Button>
              <Button onClick={() => setQrZoom(null)} className="text-xs">
                Cerrar
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
