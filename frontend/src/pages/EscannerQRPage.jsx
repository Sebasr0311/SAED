import { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import jsQR from 'jsqr';
import { toast } from 'sonner';
import {
  AlertTriangle,
  Bike,
  Building,
  Camera,
  CameraOff,
  Car,
  CheckCircle2,
  Clock,
  Copy,
  FileText,
  Footprints,
  HelpCircle,
  LogIn,
  LogOut,
  QrCode,
  Radio,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
  X,
} from 'lucide-react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { formatDate, formatDateTime, parseDateSafe } from '../lib/utils.js';
import { valPlaca } from '../lib/validation.js';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
import { Button as UiButton } from '../components/ui/button.tsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';

const TABS = [
  { id: 'validar', label: 'Validar QR', icon: QrCode },
  { id: 'activas', label: 'Visitas dentro', icon: LogOut },
  { id: 'parqueaderos', label: 'Cupos de parqueadero', icon: Car },
  { id: 'historial', label: 'Historial de accesos', icon: Clock },
];

const MEDIOS_TRANSPORTE = [
  { id: 'A_PIE', label: 'A pie / Peatonal', icon: Footprints },
  { id: 'CARRO', label: 'Automóvil', icon: Car },
  { id: 'MOTO', label: 'Motocicleta', icon: Bike },
  { id: 'BICICLETA', label: 'Bicicleta', icon: Bike },
  { id: 'OTRO', label: 'Otro vehículo', icon: HelpCircle },
];

/**
 * EscannerQRPage 2.0 — Centro de Operaciones y Control de Acceso de Portería.
 * Modern Enterprise SaaS / PropTech Premium.
 *
 * Flujo operativo certificado:
 *   VALIDAR QR -> AUTORIZAR -> REGISTRAR ENTRADA -> CONTROL VEHÍCULO / PARQUEADERO -> REGISTRAR SALIDA
 *
 * Endpoints integrados:
 *   - POST /api/v1/porteria/qr/validar
 *   - POST /api/v1/porteria/qr/notificar
 *   - POST /api/v1/porteria/qr/entrada
 *   - GET  /api/v1/porteria/visitas-resumen
 *   - PUT  /api/v1/porteria/visitas/{id}/salida
 *   - GET  /api/v1/parqueaderos
 */
export default function EscannerQRPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const [tabActiva, setTabActiva] = useState('validar');
  const [modoEscaneo, setModoEscaneo] = useState('camara'); // 'camara' | 'manual'

  // Estados de escaneo manual y por cámara
  const [codigoManual, setCodigoManual] = useState('');
  const [validando, setValidando] = useState(false);
  const [resultadoQr, setResultadoQr] = useState(null);
  const [errorValidacion, setErrorValidacion] = useState('');

  // Estados del flujo de entrada
  const [medioTransporte, setMedioTransporte] = useState('A_PIE');
  const [placa, setPlaca] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [errorEntrada, setErrorEntrada] = useState('');
  const [registrandoEntrada, setRegistrandoEntrada] = useState(false);
  const [parqueaderoAsignado, setParqueaderoAsignado] = useState(null);
  const [entradaRegistrada, setEntradaRegistrada] = useState(false);

  // Estados de cámara web / móvil
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const canvasRef = useRef(null);
  const [streaming, setStreaming] = useState(false);
  const [errorCamara, setErrorCamara] = useState('');

  // Búsquedas y filtros en listas
  const [searchActivas, setSearchActivas] = useState('');
  const [filtroParq, setFiltroParq] = useState('TODOS');
  const [registrandoSalidaId, setRegistrandoSalidaId] = useState(null);

  // 1. Carga de visitas en tiempo real
  const {
    data: visitasRaw,
    loading: visitasLoading,
    error: visitasError,
    refetch: refetchVisitas,
  } = useFetch(() => tenantApi.get('/porteria/visitas-resumen'), [tenant.activeAssignmentId]);

  // 2. Carga de parqueaderos
  const {
    data: parqRaw,
    loading: parqLoading,
    error: parqError,
    refetch: refetchParqueaderos,
  } = useFetch(() => tenantApi.get('/parqueaderos'), [tenant.activeAssignmentId]);

  const refrescarTodo = useCallback(() => {
    refetchVisitas();
    refetchParqueaderos();
  }, [refetchVisitas, refetchParqueaderos]);

  // Visitas procesadas
  const todasVisitas = useMemo(() => {
    const list = Array.isArray(visitasRaw) ? visitasRaw : visitasRaw?.items ?? [];
    return Array.isArray(list) ? list : [];
  }, [visitasRaw]);

  const visitasActivas = useMemo(() => {
    return todasVisitas.filter(
      (v) =>
        v.estado === 'EN_CURSO' ||
        v.estado === 'ACTIVA' ||
        v.estado === 'PENDIENTE' ||
        v.estado === 'PROGRAMADA'
    );
  }, [todasVisitas]);

  const visitasDentro = useMemo(() => {
    return todasVisitas.filter((v) => v.estado === 'EN_CURSO' || v.estado === 'ACTIVA');
  }, [todasVisitas]);

  // Parqueaderos procesados
  const todosParqueaderos = useMemo(() => {
    const list = Array.isArray(parqRaw) ? parqRaw : parqRaw?.items ?? [];
    return Array.isArray(list) ? list : [];
  }, [parqRaw]);

  const parqVisitantes = useMemo(() => {
    return todosParqueaderos.filter((p) => p.esVisitante);
  }, [todosParqueaderos]);

  const parqVisitantesDisponibles = useMemo(() => {
    return parqVisitantes.filter((p) => p.estado === 'DISPONIBLE');
  }, [parqVisitantes]);

  const parqVisitantesOcupados = useMemo(() => {
    return parqVisitantes.filter((p) => p.estado === 'OCUPADO');
  }, [parqVisitantes]);

  // Control de cámara: Detener
  const detenerCamara = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    if (videoRef.current) {
      if (videoRef.current.srcObject) {
        try {
          videoRef.current.srcObject.getTracks().forEach((t) => t.stop());
        } catch {
          // ignore
        }
        videoRef.current.srcObject = null;
      }
    }
    setStreaming(false);
  }, []);

  useEffect(() => {
    return () => {
      detenerCamara();
    };
  }, [detenerCamara]);

  // Validación de credencial QR
  const ejecutarValidacion = useCallback(
    async (tokenAValidar) => {
      const t = (tokenAValidar != null ? tokenAValidar : codigoManual).trim();
      if (!t || t.length < 3) {
        setErrorValidacion('Ingrese o escanee un código QR válido');
        return;
      }
      setValidando(true);
      setErrorValidacion('');
      setResultadoQr(null);
      setErrorEntrada('');
      setParqueaderoAsignado(null);
      setEntradaRegistrada(false);

      try {
        const res = await tenantApi.post('/porteria/qr/validar', { codigoQr: t, token: t });
        if (res && res.valido === false) {
          setErrorValidacion(res.mensaje || 'El código QR no es válido o ha expirado');
          setResultadoQr(null);
          toast.error(res.mensaje || 'Código QR inválido');
        } else {
          setResultadoQr(res);
          toast.success('Credencial QR acreditada con éxito');
        }
      } catch (err) {
        const msg = err.response?.data?.message || err.message || 'Error validando credencial QR';
        setErrorValidacion(msg);
        setResultadoQr(null);
        toast.error(msg);
      } finally {
        setValidando(false);
      }
    },
    [codigoManual, tenantApi]
  );

  // Loop de escaneo con requestAnimationFrame y jsQR bundled
  useEffect(() => {
    if (!streaming) return undefined;
    let animId;
    const tick = () => {
      const v = videoRef.current;
      const c = canvasRef.current;
      if (v && c && v.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && v.videoWidth > 0 && v.videoHeight > 0) {
        c.width = v.videoWidth;
        c.height = v.videoHeight;
        const ctx = c.getContext('2d', { willReadFrequently: true });
        if (ctx) {
          ctx.drawImage(v, 0, 0, c.width, c.height);
          const imgData = ctx.getImageData(0, 0, c.width, c.height);
          const qr = jsQR(imgData.data, imgData.width, imgData.height, {
            inversionAttempts: 'dontInvert',
          });
          if (qr && qr.data && qr.data.trim().length >= 4) {
            detenerCamara();
            const found = qr.data.trim();
            setCodigoManual(found);
            ejecutarValidacion(found);
            return;
          }
        }
      }
      animId = requestAnimationFrame(tick);
    };
    animId = requestAnimationFrame(tick);
    return () => {
      if (animId) cancelAnimationFrame(animId);
    };
  }, [streaming, detenerCamara, ejecutarValidacion]);

  // Iniciar cámara con fallback inteligente para móviles y webcams
  const iniciarCamara = useCallback(async () => {
    setErrorCamara('');
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }

    if (!navigator?.mediaDevices?.getUserMedia) {
      setErrorCamara('Tu navegador o dispositivo no soporta acceso a la cámara o requiere HTTPS.');
      return;
    }

    let stream;
    try {
      // Intento 1: Cámara trasera en dispositivos móviles
      stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
    } catch {
      try {
        // Intento 2: Cualquier cámara disponible (webcams desktop, laptop, etc.)
        stream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: false,
        });
      } catch (fallbackErr) {
        console.error('Error al acceder a la cámara:', fallbackErr);
        if (fallbackErr.name === 'NotAllowedError' || fallbackErr.name === 'PermissionDeniedError') {
          setErrorCamara('Permiso denegado. Permite el acceso a la cámara en los ajustes de tu navegador.');
        } else if (fallbackErr.name === 'NotFoundError' || fallbackErr.name === 'DevicesNotFoundError') {
          setErrorCamara('No se detectó ninguna cámara disponible en tu dispositivo.');
        } else if (fallbackErr.name === 'NotReadableError' || fallbackErr.name === 'TrackStartError') {
          setErrorCamara('La cámara está siendo usada por otra aplicación o pestaña.');
        } else {
          setErrorCamara('No se pudo iniciar la cámara: ' + (fallbackErr.message || 'Error desconocido'));
        }
        setStreaming(false);
        return;
      }
    }

    streamRef.current = stream;
    const v = videoRef.current;
    if (v) {
      v.srcObject = stream;
      v.setAttribute('playsinline', 'true');
      v.setAttribute('muted', 'true');
      try {
        await v.play();
      } catch (playErr) {
        console.warn('Error al reproducir video:', playErr);
      }
    }
    setStreaming(true);
  }, []);

  const limpiarFormulario = useCallback(() => {
    setResultadoQr(null);
    setCodigoManual('');
    setErrorValidacion('');
    setErrorEntrada('');
    setPlaca('');
    setDescripcion('');
    setParqueaderoAsignado(null);
    setEntradaRegistrada(false);
  }, []);

  // Registro de Entrada
  const registrarEntrada = useCallback(async () => {
    if (!resultadoQr?.codigoQr || entradaRegistrada || registrandoEntrada) return;

    if (medioTransporte === 'CARRO' || medioTransporte === 'MOTO') {
      const chk = valPlaca(placa, medioTransporte === 'CARRO' ? 'CARRO' : 'MOTO');
      if (!chk.ok) {
        setErrorEntrada(chk.mensaje);
        return;
      }
    }
    if (medioTransporte === 'BICICLETA' || medioTransporte === 'OTRO') {
      if (!descripcion.trim()) {
        setErrorEntrada('Indique una breve descripción o características del vehículo');
        return;
      }
    }

    setRegistrandoEntrada(true);
    setErrorEntrada('');

    try {
      const payload = {
        codigoQr: resultadoQr.codigoQr,
        token: resultadoQr.codigoQr,
        medioTransporte,
      };
      if (medioTransporte === 'CARRO' || medioTransporte === 'MOTO') {
        payload.placa = placa.trim().toUpperCase();
      }
      if (medioTransporte === 'BICICLETA' || medioTransporte === 'OTRO') {
        payload.descripcion = descripcion.trim();
      }

      const res = await tenantApi.post('/porteria/qr/entrada', payload);
      toast.success(res.mensaje || 'Entrada registrada exitosamente');
      if (res.parqueadero) {
        setParqueaderoAsignado(res.parqueadero);
      }
      setEntradaRegistrada(true);

      // Refrescar visitas y parqueaderos
      refrescarTodo();

      // Si no hubo asignación de cupo, limpiar automáticamente tras 3.5s
      setTimeout(() => {
        if (!res.parqueadero) {
          limpiarFormulario();
        }
      }, 3500);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Error registrando entrada';
      setErrorEntrada(msg);
      toast.error(msg);
    } finally {
      setRegistrandoEntrada(false);
    }
  }, [resultadoQr, entradaRegistrada, registrandoEntrada, medioTransporte, placa, descripcion, tenantApi, refrescarTodo, limpiarFormulario]);

  // Registro de Salida
  const registrarSalida = useCallback(
    async (idVisita) => {
      setRegistrandoSalidaId(idVisita);
      try {
        await tenantApi.put(`/porteria/visitas/${idVisita}/salida`);
        toast.success(`Salida registrada para visita #${idVisita}`);
        refrescarTodo();
      } catch (err) {
        toast.error(err.response?.data?.message || err.message || 'Error registrando salida');
      } finally {
        setRegistrandoSalidaId(null);
      }
    },
    [tenantApi, refrescarTodo]
  );

  const pegarPortapapeles = useCallback(async () => {
    try {
      if (navigator?.clipboard?.readText) {
        const text = await navigator.clipboard.readText();
        if (text) {
          setCodigoManual(text.trim());
          ejecutarValidacion(text.trim());
        }
      }
    } catch {
      toast.info('Pegue el código manualmente en el campo');
    }
  }, [ejecutarValidacion]);

  // Filtrado de visitas activas
  const visitasActivasFiltradas = useMemo(() => {
    if (!searchActivas.trim()) return visitasActivas;
    const q = searchActivas.toLowerCase();
    return visitasActivas.filter(
      (v) =>
        (v.nombreVisitante || '').toLowerCase().includes(q) ||
        (v.numeroApartamento || '').toLowerCase().includes(q) ||
        (v.documentoVisitante || '').toLowerCase().includes(q) ||
        String(v.idVisita).includes(q)
    );
  }, [visitasActivas, searchActivas]);

  // Filtrado de parqueaderos
  const parqueaderosFiltrados = useMemo(() => {
    if (filtroParq === 'TODOS') return todosParqueaderos;
    if (filtroParq === 'VISITANTES') return parqVisitantes;
    if (filtroParq === 'DISPONIBLES')
      return parqVisitantes.filter((p) => p.estado === 'DISPONIBLE');
    if (filtroParq === 'OCUPADOS') return parqVisitantes.filter((p) => p.estado === 'OCUPADO');
    return todosParqueaderos;
  }, [todosParqueaderos, parqVisitantes, filtroParq]);

  const breadcrumbs = useMemo(
    () => [
      { label: 'Inicio', href: '/' },
      { label: 'Operación', href: '/visitas' },
      { label: 'Escáner QR', active: true },
    ],
    []
  );

  return (
    <PageContainer breadcrumbs={breadcrumbs} maxWidth="max-w-7xl">
      <PageHeader
        title="Escáner QR y control de acceso"
        subtitle="Valide el pase del visitante, registre su ingreso y marque la salida."
        action={
          <UiButton
            variant="outline"
            onClick={refrescarTodo}
            className="h-10"
            aria-label="Actualizar datos de portería"
          >
            <RefreshCw
              className={`h-4 w-4 ${visitasLoading || parqLoading ? 'animate-spin' : ''}`}
              aria-hidden="true"
            />
            <span className="hidden sm:inline">Actualizar</span>
          </UiButton>
        }
      />

      {visitasError && (
        <ErrorState
          compact
          title="No se pudieron consultar las visitas"
          message={`${visitasError.message || 'Error de conexión'}. El escáner sigue disponible.`}
          onRetry={refrescarTodo}
        />
      )}

      {/* 2. Tira de KPIs Operativos */}
      <div className="grid grid-cols-1 gap-3 min-[420px]:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          title="Visitas en curso"
          value={visitasDentro.length}
          subtitle="Visitantes dentro del predio"
          icon={UserCheck}
          variant="primary"
        />
        <MetricCard
          title="Activas hoy"
          value={visitasActivas.length}
          subtitle="Programadas y en curso"
          icon={LogIn}
          variant="info"
        />
        <MetricCard
          title="Cupos de visitantes libres"
          value={`${parqVisitantesDisponibles.length} / ${parqVisitantes.length || 0}`}
          subtitle={
            parqVisitantesDisponibles.length === 0
              ? 'Cupos de visitantes agotados'
              : `${parqVisitantesOcupados.length} cupos en uso`
          }
          icon={Car}
          variant={parqVisitantesDisponibles.length > 2 ? 'success' : 'warning'}
        />
        <MetricCard
          title="Salidas pendientes"
          value={visitasDentro.length}
          subtitle="Por marcar egreso en garita"
          icon={LogOut}
          variant="secondary"
        />
      </div>

      {/* 3. Navegación por Pestañas Corporativas */}
      <div
        className="flex gap-1 overflow-x-auto rounded-lg border border-border bg-surface-container p-1"
        role="tablist"
        aria-label="Secciones del escáner"
      >
        {TABS.map((t) => {
          const Icon = t.icon;
          const activa = tabActiva === t.id;
          return (
            <button
              key={t.id}
              role="tab"
              aria-selected={activa}
              id={`tab-${t.id}`}
              aria-controls={`panel-${t.id}`}
              onClick={() => {
                setTabActiva(t.id);
                if (t.id !== 'validar') detenerCamara();
              }}
              className={`flex min-h-11 shrink-0 items-center gap-2 whitespace-nowrap rounded-md px-4 text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
                activa
                  ? 'bg-surface text-primary-text shadow-sm'
                  : 'text-text-secondary hover:bg-surface/60 hover:text-on-surface'
              }`}
            >
              <Icon className="h-4 w-4" aria-hidden="true" />
              <span>{t.label}</span>
              {t.id === 'activas' && visitasActivas.length > 0 && (
                <span className="rounded-[3px] bg-tint-primary-bg px-1.5 py-0.5 text-xs font-bold text-tint-primary-fg tabular-nums">
                  {visitasActivas.length}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* 4. Contenido de las Pestañas */}

      {/* TAB 1: VALIDAR Y ACCESO QR (PROTAGONISTA) */}
      {tabActiva === 'validar' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12" id="panel-validar" role="tabpanel">
          {/* Columna Izquierda: Escáner y Entrada del Token */}
          <div className="space-y-6 lg:col-span-6">
            <Card className="border-border/80 shadow-xs">
              <CardHeader className="pb-3 border-b border-border/60">
                <div className="flex items-center justify-between">
                  <CardTitle className="flex items-center gap-2 text-base font-bold">
                    <QrCode className="h-5 w-5 text-primary" />
                    Lector de pases QR
                  </CardTitle>
                  <div className="flex rounded-md border border-border bg-surface-container p-0.5" role="group" aria-label="Modo de lectura">
                    <button
                      type="button"
                      onClick={() => {
                        setModoEscaneo('camara');
                        iniciarCamara();
                      }}
                      aria-pressed={modoEscaneo === 'camara'}
                      className={`flex min-h-9 items-center gap-1.5 rounded px-3 text-xs font-semibold transition-colors duration-150 ${
                        modoEscaneo === 'camara'
                          ? 'bg-surface text-primary-text shadow-sm'
                          : 'text-muted-foreground hover:text-foreground'
                      }`}
                    >
                      <Camera className="h-3.5 w-3.5" />
                      Cámara
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setModoEscaneo('manual');
                        detenerCamara();
                      }}
                      aria-pressed={modoEscaneo === 'manual'}
                      className={`flex min-h-9 items-center gap-1.5 rounded px-3 text-xs font-semibold transition-colors duration-150 ${
                        modoEscaneo === 'manual'
                          ? 'bg-surface text-primary-text shadow-sm'
                          : 'text-muted-foreground hover:text-foreground'
                      }`}
                    >
                      <FileText className="h-3.5 w-3.5" />
                      Manual o USB
                    </button>
                  </div>
                </div>
              </CardHeader>

              <CardContent className="pt-4 space-y-4">
                {/* MODO CÁMARA */}
                {modoEscaneo === 'camara' && (
                  <div className="space-y-3">
                    <div className="relative mx-auto flex aspect-[4/3] w-full max-w-md flex-col items-center justify-center overflow-hidden rounded-lg border border-border bg-[#0B1222] text-[#EEF3FF]">
                      {/* Video siempre montado en el DOM para evitar refs nulas al activar */}
                      <video
                        ref={videoRef}
                        autoPlay
                        playsInline
                        muted
                        className={`h-full w-full object-cover ${streaming ? 'block' : 'hidden'}`}
                      />

                      {streaming ? (
                        <>
                          {/* Viewfinder */}
                          <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
                            <div className="relative h-48 w-48">
                              <div className="absolute left-0 top-0 h-6 w-6 border-l-[3px] border-t-[3px] border-[#A3B0FF]" />
                              <div className="absolute right-0 top-0 h-6 w-6 border-r-[3px] border-t-[3px] border-[#A3B0FF]" />
                              <div className="absolute bottom-0 left-0 h-6 w-6 border-b-[3px] border-l-[3px] border-[#A3B0FF]" />
                              <div className="absolute bottom-0 right-0 h-6 w-6 border-b-[3px] border-r-[3px] border-[#A3B0FF]" />
                            </div>
                          </div>
                          <div className="absolute bottom-3 rounded-md bg-[#0B1222]/85 px-3 py-1.5 text-xs font-semibold text-[#EEF3FF]" role="status">
                            Alinee el código QR dentro del recuadro
                          </div>
                        </>
                      ) : (
                        <div className="flex flex-col items-center gap-3 p-6 text-center text-[#B4C0DC]">
                          <Camera className="h-10 w-10 stroke-[1.5]" aria-hidden="true" />
                          <p className="max-w-xs text-sm">
                            Active la cámara de la garita para escanear el pase QR del visitante.
                          </p>
                          <Button size="lg" onClick={iniciarCamara}>
                            <Camera className="h-4 w-4" aria-hidden="true" />
                            Activar cámara
                          </Button>
                        </div>
                      )}
                      <canvas ref={canvasRef} className="hidden" />
                    </div>

                    {errorCamara && (
                      <div className="flex items-center gap-2 rounded-lg border border-tint-amber-fg/25 bg-tint-amber-bg p-3 text-xs font-medium text-tint-amber-fg">
                        <AlertTriangle className="h-4 w-4 shrink-0" />
                        <span>{errorCamara}</span>
                      </div>
                    )}

                    {streaming && (
                      <div className="flex justify-center">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={detenerCamara}
                          className="flex items-center gap-1.5 text-xs text-muted-foreground"
                        >
                          <CameraOff className="h-3.5 w-3.5" aria-hidden="true" />
                          Detener cámara
                        </Button>
                      </div>
                    )}
                  </div>
                )}

                {/* MODO MANUAL / USB */}
                <div className="space-y-3">
                  <label
                    htmlFor="input-codigo-qr"
                    className="text-sm font-semibold text-on-surface"
                  >
                    Código del pase
                  </label>
                  <div className="relative">
                    <input
                      id="input-codigo-qr"
                      type="text"
                      value={codigoManual}
                      onChange={(e) => setCodigoManual(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') ejecutarValidacion();
                      }}
                      placeholder="Escriba el código o token, o escanee con el lector USB"
                      autoComplete="off"
                      className="h-12 w-full rounded-md border border-input bg-surface px-3.5 pr-11 text-base text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25"
                    />
                    {codigoManual && (
                      <button
                        type="button"
                        onClick={() => setCodigoManual('')}
                        className="absolute right-1 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-md text-text-secondary hover:bg-surface-container hover:text-on-surface"
                        aria-label="Limpiar campo de código"
                      >
                        <X className="h-4 w-4" />
                      </button>
                    )}
                  </div>

                  <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={pegarPortapapeles}
                      className="flex items-center gap-1.5 text-xs border-border/80 text-muted-foreground hover:text-foreground"
                    >
                      <Copy className="h-3.5 w-3.5" />
                      Pegar código
                    </Button>

                    <Button
                      type="button"
                      onClick={() => ejecutarValidacion()}
                      disabled={!codigoManual.trim() || validando}
                      size="lg"
                    >
                      {validando ? (
                        <>
                          <RefreshCw className="h-4 w-4 animate-spin" />
                          Validando...
                        </>
                      ) : (
                        <>
                          <ShieldCheck className="h-4 w-4" />
                          Validar pase
                        </>
                      )}
                    </Button>
                  </div>
                </div>

                {/* ALERTA DE ERROR EN VALIDACIÓN */}
                {errorValidacion && (
                  <div className="flex items-start gap-3 rounded-xl border border-tint-red-fg/25 bg-tint-red-bg p-4 text-tint-red-fg">
                    <ShieldAlert className="h-5 w-5 shrink-0 text-tint-red-fg mt-0.5" />
                    <div className="space-y-1 text-xs">
                      <p className="text-sm font-bold">Acceso denegado: pase inválido</p>
                      <p className="text-tint-red-fg">{errorValidacion}</p>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Guía Rápida de Operación de Garita */}
            <Card className="border-border/80 bg-muted/20 shadow-xs">
              <CardContent className="p-4">
                <div className="flex items-start gap-3 text-xs text-muted-foreground">
                  <Radio className="h-4 w-4 shrink-0 text-primary mt-0.5" />
                  <div>
                    <span className="font-semibold text-on-surface">Procedimiento:</span>{' '}
                    escanee el pase QR del visitante; el sistema verifica que siga vigente. Luego
                    seleccione el medio de transporte para registrar el ingreso y, si aplica,
                    asignar un cupo de parqueadero.
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Columna Derecha: Acreditación y Autorización de Entrada */}
          <div className="space-y-6 lg:col-span-6">
            {resultadoQr ? (
              <Card className="border-tint-green-fg/25 shadow-xs ring-1 ring-tint-green-fg/25">
                <CardHeader className="border-b border-border/60 bg-tint-green-bg pb-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <CheckCircle2 className="h-5 w-5 text-tint-green-fg" />
                      <CardTitle className="text-base font-bold text-on-surface">
                        Pase verificado
                      </CardTitle>
                    </div>
                    <Badge variant="success" className="font-semibold">
                      Autorizado
                    </Badge>
                  </div>
                </CardHeader>

                <CardContent className="pt-4 space-y-5">
                  {/* Datos del Visitante y Anfitrión */}
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 rounded-lg border border-border/80 bg-card p-4">
                    <div className="space-y-1">
                      <span className="text-xs font-semibold text-muted-foreground">
                        Visitante
                      </span>
                      <div className="flex items-center gap-2">
                        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary font-bold text-xs">
                          {(resultadoQr.nombreVisitante || 'V').slice(0, 2).toUpperCase()}
                        </div>
                        <div>
                          <p className="text-sm font-bold text-foreground">
                            {resultadoQr.nombreVisitante || 'Visitante Autorizado'}
                          </p>
                          <p className="text-xs tabular-nums text-muted-foreground">
                            Doc: {resultadoQr.documentoVisitante || 'Sin registrar'}
                          </p>
                        </div>
                      </div>
                    </div>

                    <div className="space-y-1 sm:border-l sm:border-border/60 sm:pl-4">
                      <span className="text-xs font-semibold text-muted-foreground">
                        Destino / Anfitrión
                      </span>
                      <div className="flex items-center gap-2">
                        <Building className="h-5 w-5 text-muted-foreground" />
                        <div>
                          <Badge variant="secondary" className="font-bold">
                            {resultadoQr.numeroApartamento
                              ? `Apto ${resultadoQr.numeroApartamento}`
                              : 'Unidad'}
                          </Badge>
                          <p className="text-xs text-muted-foreground mt-0.5">
                            Anfitrión: {resultadoQr.nombreResidente || 'Residente titular'}
                          </p>
                        </div>
                      </div>
                    </div>

                    {resultadoQr.fechaExpiracion && (() => {
                      const expDate = parseDateSafe(resultadoQr.fechaExpiracion);
                      const now = new Date();
                      const diffMin = expDate ? Math.round((expDate.getTime() - now.getTime()) / 60000) : null;
                      const expiraPronto = diffMin !== null && diffMin > 0 && diffMin <= 15;
                      const expirado = diffMin !== null && diffMin <= 0;

                      return (
                        <div className="sm:col-span-2 border-t border-border/40 pt-2 text-xs text-muted-foreground flex flex-col sm:flex-row sm:items-center justify-between gap-1.5">
                          <span className="flex items-center gap-1.5 font-medium">
                            <Clock className="h-3.5 w-3.5 text-muted-foreground" />
                            Válido hasta:
                          </span>
                          <div className="flex items-center gap-2 tabular-nums">
                            <span className="font-semibold text-foreground">
                              {formatDateTime(resultadoQr.fechaExpiracion)}
                            </span>
                            {expirado ? (
                              <span className="inline-flex items-center rounded-sm bg-destructive/10 px-1.5 py-0.5 text-xs font-bold text-destructive">
                                Expiró hace {Math.abs(diffMin)}m
                              </span>
                            ) : diffMin !== null ? (
                              <span className={`inline-flex items-center rounded-sm px-1.5 py-0.5 text-xs font-bold ${
                                expiraPronto ? 'bg-tint-amber-bg text-tint-amber-fg' : 'bg-tint-green-bg text-tint-green-fg'
                              }`}>
                                Vence en {diffMin}m
                              </span>
                            ) : null}
                          </div>
                        </div>
                      );
                    })()}
                  </div>

                  {/* Formulario de Transporte y Placa */}
                  <div className="space-y-3 pt-1">
                    <p className="text-sm font-semibold text-on-surface" id="medio-transporte-label">
                      Medio de transporte del visitante
                    </p>

                    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3" role="group" aria-labelledby="medio-transporte-label">
                      {MEDIOS_TRANSPORTE.map((m) => {
                        const Icon = m.icon;
                        const seleccionado = medioTransporte === m.id;
                        return (
                          <button
                            key={m.id}
                            type="button"
                            disabled={entradaRegistrada}
                            aria-pressed={seleccionado}
                            onClick={() => {
                              if (entradaRegistrada) return;
                              setMedioTransporte(m.id);
                              setErrorEntrada('');
                            }}
                            className={`flex min-h-12 items-center gap-2 rounded-md border px-3 text-left text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
                              seleccionado
                                ? 'border-btn-primary bg-btn-primary text-white shadow-sm'
                                : 'border-input/60 bg-surface text-on-surface hover:border-primary/70'
                            } ${entradaRegistrada ? 'opacity-60 cursor-not-allowed' : ''}`}
                          >
                            <Icon className="h-4 w-4 shrink-0" />
                            <span className="truncate">{m.label}</span>
                          </button>
                        );
                      })}
                    </div>

                    {/* Campos condicionales según vehículo */}
                    {(medioTransporte === 'CARRO' || medioTransporte === 'MOTO') && (
                      <div className="rounded-lg border border-border/80 bg-muted/20 p-3.5 space-y-2">
                        <div className="flex items-center justify-between">
                          <label
                            htmlFor="input-placa"
                            className="text-xs font-bold text-foreground"
                          >
                            Placa del vehículo <span className="text-destructive" aria-hidden="true">*</span>
                          </label>
                          <span className="text-xs text-muted-foreground">
                            {medioTransporte === 'CARRO'
                              ? 'Formato: ABC 123'
                              : 'Formato: ABC 12D'}
                          </span>
                        </div>
                        <input
                          id="input-placa"
                          type="text"
                          disabled={entradaRegistrada}
                          value={placa}
                          onChange={(e) => setPlaca(e.target.value.toUpperCase())}
                          placeholder={
                            medioTransporte === 'CARRO' ? 'Ej: ABC 123' : 'Ej: ABC 12D'
                          }
                          className="h-11 w-full rounded-md border border-input bg-surface px-3 text-base font-bold text-on-surface placeholder:normal-case placeholder:tracking-normal placeholder:text-text-hint focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 disabled:cursor-not-allowed disabled:opacity-60"
                        />
                      </div>
                    )}

                    {(medioTransporte === 'BICICLETA' || medioTransporte === 'OTRO') && (
                      <div className="rounded-lg border border-border/80 bg-muted/20 p-3.5 space-y-2">
                        <label
                          htmlFor="input-desc-vehiculo"
                          className="text-xs font-bold text-foreground"
                        >
                          Descripción del vehículo <span className="text-destructive" aria-hidden="true">*</span>
                        </label>
                        <input
                          id="input-desc-vehiculo"
                          type="text"
                          disabled={entradaRegistrada}
                          value={descripcion}
                          onChange={(e) => setDescripcion(e.target.value)}
                          placeholder="Marca, color, modelo o características..."
                          className="h-11 w-full rounded-md border border-input bg-surface px-3 text-sm text-on-surface placeholder:text-text-hint focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 disabled:cursor-not-allowed disabled:opacity-60"
                        />
                      </div>
                    )}

                    {errorEntrada && (
                      <div className="flex items-center gap-2 rounded-lg border border-tint-red-fg/25 bg-tint-red-bg p-3 text-xs font-medium text-tint-red-fg">
                        <AlertTriangle className="h-4 w-4 shrink-0" />
                        <span>{errorEntrada}</span>
                      </div>
                    )}
                  </div>

                  {/* Feedback de Entrada Exitosa y Parqueadero Asignado */}
                  {entradaRegistrada && (
                    <div className="space-y-3">
                      <div className="flex items-center gap-2.5 rounded-lg border border-tint-green-fg/25 bg-tint-green-bg p-3.5 text-xs font-semibold text-tint-green-fg">
                        <CheckCircle2 className="h-5 w-5 shrink-0 text-tint-green-fg" />
                        <span>Ingreso autorizado y registrado en la bitácora de portería.</span>
                      </div>

                      {parqueaderoAsignado && (
                        <div className="rounded-xl border border-tint-green-fg/25 p-5 text-center shadow-xs">
                          <span className="text-xs font-bold text-tint-green-fg">
                            Cupo de parqueadero asignado
                          </span>
                          <div className="mt-2 text-3xl font-extrabold tracking-tight text-on-surface">
                            {parqueaderoAsignado}
                          </div>
                          <p className="mt-1 text-xs text-muted-foreground">
                            Indique este número de cupo al visitante al ingresar a la copropiedad.
                          </p>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Botones de Acción */}
                  <div className="flex items-center gap-3 pt-2">
                    {entradaRegistrada ? (
                      <Button
                        type="button"
                        onClick={limpiarFormulario}
                        className="flex-1 bg-btn-success hover:opacity-90 text-white font-bold shadow-xs py-2.5 text-sm"
                      >
                        <CheckCircle2 className="mr-2 h-4 w-4" />
                        Finalizar y escanear el siguiente
                      </Button>
                    ) : (
                      <>
                        <Button
                          type="button"
                          variant="outline"
                          onClick={limpiarFormulario}
                          className="border-border/80 text-muted-foreground hover:text-foreground"
                        >
                          Cancelar
                        </Button>

                        <Button
                          type="button"
                          onClick={registrarEntrada}
                          disabled={registrandoEntrada}
                          className="flex-1 bg-btn-success hover:opacity-90 text-white font-bold shadow-xs py-2.5 text-sm"
                        >
                          {registrandoEntrada ? (
                            <>
                              <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                              Registrando ingreso...
                            </>
                          ) : (
                            <>
                              <LogIn className="mr-2 h-4 w-4" />
                              Autorizar y registrar entrada
                            </>
                          )}
                        </Button>
                      </>
                    )}
                  </div>
                </CardContent>
              </Card>
            ) : (
              /* Estado Inactivo / Esperando Validación */
              <Card className="border-border/80 shadow-xs">
                <CardContent className="flex flex-col items-center justify-center p-12 text-center">
                  <div className="flex h-16 w-16 items-center justify-center rounded-full bg-muted/60 text-muted-foreground/80 mb-4">
                    <ShieldCheck className="h-8 w-8 stroke-[1.5]" />
                  </div>
                  <h3 className="text-base font-bold text-foreground">
                    Esperando un pase
                  </h3>
                  <p className="mt-1 max-w-sm text-xs text-muted-foreground">
                    Escanee el código QR con la cámara o escriba el código del pase para verificar
                    los permisos del visitante.
                  </p>
                </CardContent>
              </Card>
            )}
          </div>
        </div>
      )}

      {/* TAB 2: VISITAS DENTRO / REGISTRAR SALIDA */}
      {tabActiva === 'activas' && (
        <Card className="border-border/80 shadow-xs" id="panel-activas" role="tabpanel">
          <CardHeader className="border-b border-border/60 pb-3">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle className="text-base font-bold">
                  Visitantes dentro de la copropiedad
                </CardTitle>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Pases activos. Marque la salida cuando el visitante abandone las instalaciones.
                </p>
              </div>

              <div className="relative w-full sm:w-64">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
                <input
                  type="search"
                  aria-label="Buscar visitante o apartamento"
                  value={searchActivas}
                  onChange={(e) => setSearchActivas(e.target.value)}
                  placeholder="Buscar visitante o apartamento"
                  className="h-10 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm text-on-surface placeholder:text-text-hint focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25"
                />
              </div>
            </div>
          </CardHeader>

          <CardContent className="p-0">
            {visitasLoading ? (
              <div className="p-6">
                <LoadingState message="Cargando visitantes activos..." />
              </div>
            ) : visitasError ? (
              <div className="p-4 sm:p-6">
                <ErrorState
                  compact
                  title="No se pudieron cargar las visitas activas"
                  message={visitasError.message || 'Revise la conexión e intente de nuevo.'}
                  onRetry={refrescarTodo}
                />
              </div>
            ) : visitasActivasFiltradas.length === 0 ? (
              <EmptyState
                icon={UserCheck}
                title={searchActivas ? 'Ninguna visita coincide con la búsqueda' : 'No hay visitantes dentro'}
                subtitle={
                  searchActivas
                    ? 'Revise el nombre o el apartamento.'
                    : 'Cuando registre un ingreso, el visitante aparecerá aquí para marcar su salida.'
                }
                className="py-10"
              >
                {!searchActivas && (
                  <Button variant="outline" size="sm" onClick={() => setTabActiva('validar')} className="mt-4">
                    <QrCode className="h-4 w-4" aria-hidden="true" />
                    Validar un pase
                  </Button>
                )}
              </EmptyState>
            ) : (
              <>
                {/* Tabla Desktop (hidden md:table) */}
                <div className="hidden md:block overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead className="border-b border-border bg-surface-container text-xs font-bold text-text-secondary">
                      <tr>
                        <th className="px-4 py-3">ID / Visitante</th>
                        <th className="px-4 py-3">Documento</th>
                        <th className="px-4 py-3">Apartamento</th>
                        <th className="px-4 py-3">Ingreso / Fecha</th>
                        <th className="px-4 py-3">Estado</th>
                        <th className="px-4 py-3 text-right">Acción Operativa</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/60">
                      {visitasActivasFiltradas.map((v) => (
                        <tr key={v.idVisita} className="hover:bg-muted/30 transition-colors">
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-2">
                              <div className="flex h-7 w-7 items-center justify-center rounded-full bg-primary/10 text-primary font-bold text-xs">
                                {(v.nombreVisitante || 'V').slice(0, 1).toUpperCase()}
                              </div>
                              <div>
                                <p className="font-bold text-foreground">
                                  {v.nombreVisitante || 'Visitante'}
                                </p>
                                <span className="text-xs text-muted-foreground tabular-nums">
                                  Pase #{v.idVisita}
                                </span>
                              </div>
                            </div>
                          </td>
                          <td className="px-4 py-3 tabular-nums text-muted-foreground">
                            {v.documentoVisitante || '-'}
                          </td>
                          <td className="px-4 py-3">
                            <Badge variant="secondary" className="font-semibold">
                              Apto {v.numeroApartamento || '-'}
                            </Badge>
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">
                            {formatDate(v.fechaVisita) || '-'}
                          </td>
                          <td className="px-4 py-3">
                            <Badge
                              variant={
                                v.estado === 'EN_CURSO' || v.estado === 'ACTIVA'
                                  ? 'success'
                                  : 'secondary'
                              }
                            >
                              {v.estado}
                            </Badge>
                          </td>
                          <td className="px-4 py-3 text-right">
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => registrarSalida(v.idVisita)}
                              disabled={registrandoSalidaId === v.idVisita}
                              className="border-border/80 hover:bg-tint-red-bg hover:text-tint-red-fg hover:border-tint-red-fg/25 text-xs font-semibold"
                            >
                              {registrandoSalidaId === v.idVisita ? (
                                <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                              ) : (
                                <>
                                  <LogOut className="mr-1.5 h-3.5 w-3.5" />
                                  Marcar salida
                                </>
                              )}
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Lista Adaptativa Móvil (md:hidden) */}
                <div className="md:hidden divide-y divide-border/60">
                  {visitasActivasFiltradas.map((v) => (
                    <div key={v.idVisita} className="p-4 space-y-3">
                      <div className="flex items-start justify-between">
                        <div className="flex items-center gap-2.5">
                          <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10 text-primary font-bold text-xs">
                            {(v.nombreVisitante || 'V').slice(0, 2).toUpperCase()}
                          </div>
                          <div>
                            <p className="text-sm font-bold text-foreground">
                              {v.nombreVisitante || 'Visitante'}
                            </p>
                            <p className="text-xs text-muted-foreground tabular-nums">
                              Doc: {v.documentoVisitante || 'Sin documento'}
                            </p>
                          </div>
                        </div>
                        <Badge
                          variant={
                            v.estado === 'EN_CURSO' || v.estado === 'ACTIVA'
                              ? 'success'
                              : 'secondary'
                          }
                        >
                          {v.estado}
                        </Badge>
                      </div>

                      <div className="flex items-center justify-between text-xs text-muted-foreground border-t border-border/40 pt-2">
                        <span>Apto {v.numeroApartamento || '-'}</span>
                        <span>{formatDate(v.fechaVisita)}</span>
                      </div>

                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => registrarSalida(v.idVisita)}
                        disabled={registrandoSalidaId === v.idVisita}
                        className="w-full border-border/80 hover:bg-tint-red-bg hover:text-tint-red-fg text-xs font-semibold py-2.5 min-h-[44px]"
                      >
                        {registrandoSalidaId === v.idVisita ? (
                          <RefreshCw className="h-4 w-4 animate-spin" />
                        ) : (
                          <>
                            <LogOut className="mr-2 h-4 w-4" />
                            Registrar salida
                          </>
                        )}
                      </Button>
                    </div>
                  ))}
                </div>
              </>
            )}
          </CardContent>
        </Card>
      )}

      {/* TAB 3: CUPOS DE PARQUEADERO */}
      {tabActiva === 'parqueaderos' && (
        <div className="space-y-6" id="panel-parqueaderos" role="tabpanel">
          <Card className="border-border/80 shadow-xs">
            <CardHeader className="border-b border-border/60 pb-3">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <CardTitle className="text-base font-bold">
                    Ocupación de parqueaderos
                  </CardTitle>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Disponibilidad de cupos según el último registro
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <select
                    value={filtroParq}
                    onChange={(e) => setFiltroParq(e.target.value)}
                    aria-label="Filtrar parqueaderos"
                    className="h-10 rounded-md border border-input bg-surface px-3 text-sm text-on-surface focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25"
                  >
                    <option value="TODOS">Todos los parqueaderos</option>
                    <option value="VISITANTES">Solo visitantes</option>
                    <option value="DISPONIBLES">Solo disponibles</option>
                    <option value="OCUPADOS">Solo ocupados</option>
                  </select>
                </div>
              </div>
            </CardHeader>

            <CardContent className="pt-4">
              {parqLoading ? (
                <div className="p-8">
                  <LoadingState message="Consultando cupos de parqueadero..." />
                </div>
              ) : parqError ? (
                <ErrorState
                  compact
                  title="No se pudo cargar el estado de los parqueaderos"
                  message={parqError.message || 'Revise la conexión e intente de nuevo.'}
                  onRetry={refrescarTodo}
                />
              ) : parqueaderosFiltrados.length === 0 ? (
                <EmptyState
                  icon={Car}
                  title="Ningún parqueadero coincide con el filtro"
                  subtitle="Cambie el filtro para ver otros cupos."
                  className="py-10"
                >
                  {filtroParq !== 'TODOS' && (
                    <Button variant="outline" size="sm" onClick={() => setFiltroParq('TODOS')} className="mt-4">
                      Ver todos los parqueaderos
                    </Button>
                  )}
                </EmptyState>
              ) : (
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
                  {parqueaderosFiltrados.map((p) => {
                    const ocupado = p.estado === 'OCUPADO';
                    const disponible = p.estado === 'DISPONIBLE';
                    return (
                      <div
                        key={p.idParqueadero}
                        className={`flex flex-col justify-between rounded-md border p-3.5 ${
                          ocupado
                            ? 'border-tint-red-fg/25 bg-tint-red-bg'
                            : disponible
                              ? 'border-tint-green-fg/25 bg-tint-green-bg'
                              : 'border-border bg-surface-container'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="tabular-nums text-sm font-extrabold text-foreground">
                            {p.codigo || p.numeroParqueadero}
                          </span>
                          <Car
                            className={`h-4 w-4 ${
                              ocupado
                                ? 'text-tint-red-fg'
                                : disponible
                                  ? 'text-tint-green-fg'
                                  : 'text-muted-foreground'
                            }`}
                          />
                        </div>

                        <div className="mt-3 space-y-1">
                          <Badge
                            variant={ocupado ? 'destructive' : disponible ? 'success' : 'secondary'}
                            className="text-xs font-bold"
                          >
                            {p.estado}
                          </Badge>
                          <p className="text-xs text-muted-foreground truncate">
                            {p.esVisitante ? 'Visitante' : 'Residente'} · {p.tipo || 'Vehículo'}
                          </p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {/* TAB 4: HISTORIAL DE ACCESOS */}
      {tabActiva === 'historial' && (
        <Card className="border-border/80 shadow-xs" id="panel-historial" role="tabpanel">
          <CardHeader className="border-b border-border/60 pb-3">
            <CardTitle className="text-base font-bold">
              Historial de accesos recientes
            </CardTitle>
            <p className="text-xs text-muted-foreground mt-0.5">
              Últimos 20 ingresos y salidas registrados en la garita
            </p>
          </CardHeader>

          <CardContent className="p-0">
            {visitasLoading ? (
              <div className="p-8">
                <LoadingState message="Cargando historial de accesos..." />
              </div>
            ) : visitasError ? (
              <div className="p-4 sm:p-6">
                <ErrorState
                  compact
                  title="No se pudo cargar el historial"
                  message={visitasError.message || 'Revise la conexión e intente de nuevo.'}
                  onRetry={refrescarTodo}
                />
              </div>
            ) : todasVisitas.length === 0 ? (
              <EmptyState
                icon={Clock}
                title="Aún no hay accesos registrados"
                subtitle="Los ingresos y salidas aparecerán aquí a medida que se registren."
                className="py-10"
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="sticky top-0 border-b border-border bg-surface-container text-xs font-bold text-text-secondary">
                    <tr>
                      <th className="px-4 py-3">Pase</th>
                      <th className="px-4 py-3">Visitante</th>
                      <th className="px-4 py-3">Apartamento</th>
                      <th className="px-4 py-3">Fecha</th>
                      <th className="px-4 py-3">Estado</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {todasVisitas.slice(0, 20).map((v) => (
                      <tr key={v.idVisita} className="hover:bg-muted/30 transition-colors">
                        <td className="px-4 py-3 tabular-nums font-semibold text-muted-foreground">
                          #{v.idVisita}
                        </td>
                        <td className="px-4 py-3 font-bold text-foreground">
                          {v.nombreVisitante || 'Visitante'}
                        </td>
                        <td className="px-4 py-3">
                          <Badge variant="secondary" className="font-semibold">
                            Apto {v.numeroApartamento || '-'}
                          </Badge>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          {formatDate(v.fechaVisita) || '-'}
                        </td>
                        <td className="px-4 py-3">
                          <Badge
                            variant={
                              v.estado === 'FINALIZADA'
                                ? 'secondary'
                                : v.estado === 'ACTIVA' || v.estado === 'EN_CURSO'
                                  ? 'success'
                                  : 'secondary'
                            }
                          >
                            {v.estado}
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </PageContainer>
  );
}
