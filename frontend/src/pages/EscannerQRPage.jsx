import { useState, useRef, useEffect, useCallback, useMemo } from 'react';
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
import { formatDate } from '../lib/utils.js';
import { valPlaca } from '../lib/validation.js';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';

const TABS = [
  { id: 'validar', label: 'Validar y Acceso QR', icon: QrCode },
  { id: 'activas', label: 'Visitas Dentro / Salida', icon: LogOut },
  { id: 'parqueaderos', label: 'Cupos de Parqueadero', icon: Car },
  { id: 'historial', label: 'Historial de Accesos', icon: Clock },
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

  // Estados de cámara web / móvil
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const [streaming, setStreaming] = useState(false);
  const [errorCamara, setErrorCamara] = useState('');
  const [jsqrListo, setJsqrListo] = useState(typeof window !== 'undefined' && !!window.jsQR);

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

  // Carga bajo demanda de jsQR
  useEffect(() => {
    if (typeof window === 'undefined' || window.jsQR) {
      setJsqrListo(true);
      return undefined;
    }
    const script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.js';
    script.onload = () => setJsqrListo(true);
    script.onerror = () => setJsqrListo(false);
    document.head.appendChild(script);
    return () => {
      script.onload = null;
      script.onerror = null;
    };
  }, []);

  // Control de cámara: Detener
  const detenerCamara = useCallback(() => {
    if (videoRef.current && videoRef.current.srcObject) {
      const tracks = videoRef.current.srcObject.getTracks();
      tracks.forEach((track) => track.stop());
      videoRef.current.srcObject = null;
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

  // Loop de escaneo con requestAnimationFrame
  useEffect(() => {
    if (!streaming) return undefined;
    let animId;
    const tick = () => {
      const v = videoRef.current;
      const c = canvasRef.current;
      if (v && c && v.videoWidth > 0 && v.videoHeight > 0) {
        c.width = v.videoWidth;
        c.height = v.videoHeight;
        const ctx = c.getContext('2d', { willReadFrequently: true });
        if (ctx) {
          ctx.drawImage(v, 0, 0, c.width, c.height);
          const imgData = ctx.getImageData(0, 0, c.width, c.height);
          if (typeof window !== 'undefined' && window.jsQR) {
            const qr = window.jsQR(imgData.data, imgData.width, imgData.height, {
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
      }
      animId = requestAnimationFrame(tick);
    };
    animId = requestAnimationFrame(tick);
    return () => {
      if (animId) cancelAnimationFrame(animId);
    };
  }, [streaming, detenerCamara, ejecutarValidacion]);

  // Iniciar cámara
  const iniciarCamara = useCallback(async () => {
    setErrorCamara('');
    try {
      if (!navigator?.mediaDevices?.getUserMedia) {
        throw new Error('La cámara no es soportada en este navegador');
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
        audio: false,
      });
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
        setStreaming(true);
      }
    } catch (err) {
      setErrorCamara('No se pudo acceder a la cámara: ' + (err.message || 'Permiso denegado'));
      setStreaming(false);
    }
  }, []);

  // Registro de Entrada
  const registrarEntrada = useCallback(async () => {
    if (!resultadoQr?.codigoQr) return;

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

      // Refrescar visitas y parqueaderos
      refrescarTodo();

      // Limpiar datos tras 4 segundos o mantener visibles si hay parqueadero
      setTimeout(() => {
        if (!res.parqueadero) {
          setResultadoQr(null);
          setCodigoManual('');
          setPlaca('');
          setDescripcion('');
        }
      }, 3000);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Error registrando entrada';
      setErrorEntrada(msg);
      toast.error(msg);
    } finally {
      setRegistrandoEntrada(false);
    }
  }, [resultadoQr, medioTransporte, placa, descripcion, tenantApi, refrescarTodo]);

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

  const limpiarFormulario = useCallback(() => {
    setResultadoQr(null);
    setCodigoManual('');
    setErrorValidacion('');
    setErrorEntrada('');
    setPlaca('');
    setDescripcion('');
    setParqueaderoAsignado(null);
  }, []);

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
      { label: 'Control de Acceso y QR', active: true },
    ],
    []
  );

  if (visitasError) {
    return (
      <PageContainer breadcrumbs={breadcrumbs} maxWidth="max-w-7xl">
        <ErrorState
          title="Error al cargar datos de portería"
          message={visitasError.message || 'No se pudieron consultar los accesos'}
          onRetry={refrescarTodo}
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer breadcrumbs={breadcrumbs} maxWidth="max-w-7xl">
      {/* 1. Header Contextual de Operación */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              Control de Acceso y Validación QR
            </h1>
            <Badge variant="outline" className="text-xs font-semibold uppercase tracking-wider">
              PORTERO
            </Badge>
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <span className="flex items-center gap-1.5 font-medium text-emerald-600 dark:text-emerald-400">
              <span className="inline-block h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
              Garita Activa
            </span>
            <span>·</span>
            <span>Acreditación en garita, autorización de visitantes y control vehicular</span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={refrescarTodo}
            className="flex items-center gap-2 border-border/80 hover:bg-muted/50"
            aria-label="Actualizar datos de portería"
          >
            <RefreshCw
              className={`h-4 w-4 ${visitasLoading || parqLoading ? 'animate-spin' : ''}`}
            />
            <span className="hidden sm:inline">Actualizar</span>
          </Button>
        </div>
      </div>

      {/* 2. Tira de KPIs Operativos */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          title="Visitas en Curso"
          value={visitasDentro.length}
          subtitle="Visitantes dentro del predio"
          icon={UserCheck}
          variant="primary"
        />
        <MetricCard
          title="Total Activas / Hoy"
          value={visitasActivas.length}
          subtitle="Programadas y en curso"
          icon={LogIn}
          variant="info"
        />
        <MetricCard
          title="Cupos Visitantes Libres"
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
          title="Salidas Pendientes"
          value={visitasDentro.length}
          subtitle="Por marcar egreso en garita"
          icon={LogOut}
          variant="secondary"
        />
      </div>

      {/* 3. Navegación por Pestañas Corporativas */}
      <div className="flex border-b border-border/80 overflow-x-auto" role="tablist">
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
              className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-semibold transition-colors whitespace-nowrap focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                activa
                  ? 'border-primary text-primary'
                  : 'border-transparent text-muted-foreground hover:border-border hover:text-foreground'
              }`}
            >
              <Icon className="h-4 w-4" />
              <span>{t.label}</span>
              {t.id === 'activas' && visitasActivas.length > 0 && (
                <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-bold text-primary">
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
                    Lector de Credenciales QR
                  </CardTitle>
                  <div className="flex rounded-lg border border-border/80 bg-muted/30 p-0.5">
                    <button
                      type="button"
                      onClick={() => {
                        setModoEscaneo('camara');
                        iniciarCamara();
                      }}
                      className={`flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-semibold transition-all ${
                        modoEscaneo === 'camara'
                          ? 'bg-background text-foreground shadow-xs'
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
                      className={`flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-semibold transition-all ${
                        modoEscaneo === 'manual'
                          ? 'bg-background text-foreground shadow-xs'
                          : 'text-muted-foreground hover:text-foreground'
                      }`}
                    >
                      <FileText className="h-3.5 w-3.5" />
                      Manual / USB
                    </button>
                  </div>
                </div>
              </CardHeader>

              <CardContent className="pt-4 space-y-4">
                {/* MODO CÁMARA */}
                {modoEscaneo === 'camara' && (
                  <div className="space-y-3">
                    <div className="relative mx-auto flex h-64 w-full max-w-sm flex-col items-center justify-center overflow-hidden rounded-xl border-2 border-dashed border-border/80 bg-slate-950/90 text-slate-100 shadow-inner">
                      {streaming ? (
                        <>
                          <video
                            ref={videoRef}
                            autoPlay
                            playsInline
                            muted
                            className="h-full w-full object-cover"
                          />
                          {/* HUD Visor Overlay */}
                          <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
                            <div className="relative h-44 w-44 rounded-lg border-2 border-emerald-400/80 shadow-[0_0_20px_rgba(52,211,153,0.3)]">
                              <div className="absolute -top-1 -left-1 h-4 w-4 border-t-4 border-l-4 border-emerald-400" />
                              <div className="absolute -top-1 -right-1 h-4 w-4 border-t-4 border-r-4 border-emerald-400" />
                              <div className="absolute -bottom-1 -left-1 h-4 w-4 border-b-4 border-l-4 border-emerald-400" />
                              <div className="absolute -bottom-1 -right-1 h-4 w-4 border-b-4 border-r-4 border-emerald-400" />
                              {/* Scanline */}
                              <div className="absolute inset-x-0 top-0 h-0.5 bg-gradient-to-r from-transparent via-emerald-300 to-transparent animate-pulse" />
                            </div>
                          </div>
                          <div className="absolute bottom-2 rounded-full bg-slate-900/80 px-3 py-1 text-xs font-medium text-emerald-400 backdrop-blur-xs">
                            Alinee el código QR en el visor
                          </div>
                        </>
                      ) : (
                        <div className="flex flex-col items-center gap-3 p-6 text-center text-slate-400">
                          <Camera className="h-10 w-10 stroke-[1.5] text-slate-500" />
                          <p className="text-xs max-w-xs">
                            Active la cámara de la garita o dispositivo móvil para escanear el pase
                            QR del visitante.
                          </p>
                          <Button
                            size="sm"
                            onClick={iniciarCamara}
                            className="flex items-center gap-2 bg-primary text-primary-foreground shadow-xs"
                          >
                            <Camera className="h-4 w-4" />
                            Activar Cámara
                          </Button>
                        </div>
                      )}
                      <canvas ref={canvasRef} className="hidden" />
                    </div>

                    {!jsqrListo && (
                      <p className="text-center text-xs text-muted-foreground">
                        Cargando motor de reconocimiento de escaneo...
                      </p>
                    )}

                    {errorCamara && (
                      <div className="flex items-center gap-2 rounded-lg border border-amber-500/20 bg-amber-500/10 p-3 text-xs font-medium text-amber-700 dark:text-amber-400">
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
                          <CameraOff className="h-3.5 w-3.5" />
                          Detener Cámara
                        </Button>
                      </div>
                    )}
                  </div>
                )}

                {/* MODO MANUAL / USB */}
                <div className="space-y-3">
                  <label
                    htmlFor="input-codigo-qr"
                    className="text-xs font-bold uppercase tracking-wider text-muted-foreground"
                  >
                    Código o Token de la Credencial
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
                      placeholder="Ingrese el token alfanumérico o escanee con lector USB..."
                      className="w-full rounded-lg border border-border/80 bg-background px-3.5 py-2.5 font-mono text-sm tracking-wide text-foreground placeholder:text-muted-foreground/60 focus:border-primary focus:ring-2 focus:ring-primary/20 focus:outline-none"
                    />
                    {codigoManual && (
                      <button
                        type="button"
                        onClick={() => setCodigoManual('')}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
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
                      Pegar portapapeles
                    </Button>

                    <Button
                      type="button"
                      onClick={() => ejecutarValidacion()}
                      disabled={!codigoManual.trim() || validando}
                      className="flex items-center gap-2 bg-primary text-primary-foreground shadow-xs font-semibold"
                    >
                      {validando ? (
                        <>
                          <RefreshCw className="h-4 w-4 animate-spin" />
                          Validando...
                        </>
                      ) : (
                        <>
                          <ShieldCheck className="h-4 w-4" />
                          Validar Credencial
                        </>
                      )}
                    </Button>
                  </div>
                </div>

                {/* ALERTA DE ERROR EN VALIDACIÓN */}
                {errorValidacion && (
                  <div className="flex items-start gap-3 rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-rose-800 dark:text-rose-300">
                    <ShieldAlert className="h-5 w-5 shrink-0 text-rose-600 dark:text-rose-400 mt-0.5" />
                    <div className="space-y-1 text-xs">
                      <p className="font-bold">Acceso Denegado / Pase Inválido</p>
                      <p className="text-rose-700/90 dark:text-rose-300/90">{errorValidacion}</p>
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
                    <span className="font-semibold text-foreground">Procedimiento de Garita:</span>{' '}
                    Escanee el código QR presentado por el visitante. El sistema certificará la
                    vigencia en Oracle ATP. Seleccione el medio de transporte para registrar el
                    ingreso y asignar cupo de parqueadero automáticamente.
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Columna Derecha: Acreditación y Autorización de Entrada */}
          <div className="space-y-6 lg:col-span-6">
            {resultadoQr ? (
              <Card className="border-emerald-500/40 shadow-xs ring-1 ring-emerald-500/20">
                <CardHeader className="border-b border-border/60 bg-emerald-500/5 pb-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <CheckCircle2 className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />
                      <CardTitle className="text-base font-bold text-emerald-950 dark:text-emerald-100">
                        Credencial Verificada
                      </CardTitle>
                    </div>
                    <Badge variant="success" className="font-semibold">
                      AUTORIZADO
                    </Badge>
                  </div>
                </CardHeader>

                <CardContent className="pt-4 space-y-5">
                  {/* Datos del Visitante y Anfitrión */}
                  <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 rounded-lg border border-border/80 bg-card p-4">
                    <div className="space-y-1">
                      <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
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
                          <p className="text-xs font-mono text-muted-foreground">
                            Doc: {resultadoQr.documentoVisitante || 'Sin registrar'}
                          </p>
                        </div>
                      </div>
                    </div>

                    <div className="space-y-1 sm:border-l sm:border-border/60 sm:pl-4">
                      <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
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

                    {resultadoQr.fechaExpiracion && (
                      <div className="sm:col-span-2 border-t border-border/40 pt-2 text-xs text-muted-foreground flex items-center justify-between">
                        <span className="flex items-center gap-1.5">
                          <Clock className="h-3.5 w-3.5 text-muted-foreground" />
                          Vigencia de la credencial:
                        </span>
                        <span className="font-mono font-semibold text-foreground">
                          {formatDate(resultadoQr.fechaExpiracion)}
                        </span>
                      </div>
                    )}
                  </div>

                  {/* Formulario de Transporte y Placa */}
                  <div className="space-y-3 pt-1">
                    <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                      Medio de Transporte del Visitante
                    </label>

                    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                      {MEDIOS_TRANSPORTE.map((m) => {
                        const Icon = m.icon;
                        const seleccionado = medioTransporte === m.id;
                        return (
                          <button
                            key={m.id}
                            type="button"
                            onClick={() => {
                              setMedioTransporte(m.id);
                              setErrorEntrada('');
                            }}
                            className={`flex items-center gap-2 rounded-lg border p-2.5 text-left text-xs font-semibold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                              seleccionado
                                ? 'border-primary bg-primary/10 text-primary shadow-xs'
                                : 'border-border/80 bg-background text-muted-foreground hover:border-border hover:text-foreground'
                            }`}
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
                            Placa del Vehículo <span className="text-rose-500">*</span>
                          </label>
                          <span className="text-[11px] text-muted-foreground">
                            {medioTransporte === 'CARRO'
                              ? 'Formato: ABC 123'
                              : 'Formato: ABC 12D'}
                          </span>
                        </div>
                        <input
                          id="input-placa"
                          type="text"
                          value={placa}
                          onChange={(e) => setPlaca(e.target.value.toUpperCase())}
                          placeholder={
                            medioTransporte === 'CARRO' ? 'Ej: ABC 123' : 'Ej: ABC 12D'
                          }
                          className="w-full rounded-md border border-border/80 bg-background px-3 py-2 font-mono text-sm font-bold tracking-widest text-foreground uppercase placeholder:text-muted-foreground/60 focus:border-primary focus:ring-2 focus:ring-primary/20 focus:outline-none"
                        />
                      </div>
                    )}

                    {(medioTransporte === 'BICICLETA' || medioTransporte === 'OTRO') && (
                      <div className="rounded-lg border border-border/80 bg-muted/20 p-3.5 space-y-2">
                        <label
                          htmlFor="input-desc-vehiculo"
                          className="text-xs font-bold text-foreground"
                        >
                          Descripción del Vehículo <span className="text-rose-500">*</span>
                        </label>
                        <input
                          id="input-desc-vehiculo"
                          type="text"
                          value={descripcion}
                          onChange={(e) => setDescripcion(e.target.value)}
                          placeholder="Marca, color, modelo o características..."
                          className="w-full rounded-md border border-border/80 bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground/60 focus:border-primary focus:ring-2 focus:ring-primary/20 focus:outline-none"
                        />
                      </div>
                    )}

                    {errorEntrada && (
                      <div className="flex items-center gap-2 rounded-lg border border-rose-500/20 bg-rose-500/10 p-3 text-xs font-medium text-rose-700 dark:text-rose-300">
                        <AlertTriangle className="h-4 w-4 shrink-0" />
                        <span>{errorEntrada}</span>
                      </div>
                    )}
                  </div>

                  {/* Feedback de Parqueadero Asignado */}
                  {parqueaderoAsignado && (
                    <div className="rounded-xl border border-emerald-500/40 bg-gradient-to-br from-emerald-500/20 via-emerald-500/10 to-transparent p-5 text-center shadow-xs">
                      <span className="text-xs font-bold uppercase tracking-wider text-emerald-800 dark:text-emerald-300">
                        Cupo de Parqueadero Asignado Automáticamente
                      </span>
                      <div className="mt-2 text-3xl font-extrabold tracking-tight text-emerald-950 dark:text-emerald-50">
                        {parqueaderoAsignado}
                      </div>
                      <p className="mt-1 text-xs text-muted-foreground">
                        Indique este número de cupo al visitante al ingresar a la copropiedad.
                      </p>
                    </div>
                  )}

                  {/* Botones de Acción */}
                  <div className="flex items-center gap-3 pt-2">
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
                      className="flex-1 bg-emerald-600 hover:bg-emerald-700 text-white font-bold shadow-xs py-2.5 text-sm"
                    >
                      {registrandoEntrada ? (
                        <>
                          <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                          Registrando Ingreso...
                        </>
                      ) : (
                        <>
                          <LogIn className="mr-2 h-4 w-4" />
                          Autorizar y Registrar Entrada
                        </>
                      )}
                    </Button>
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
                    Esperando Validación de Credencial
                  </h3>
                  <p className="mt-1 max-w-sm text-xs text-muted-foreground">
                    Escanee el código QR con la cámara de garita o ingrese el código alfanumérico en
                    el panel lateral para verificar los permisos del visitante.
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
                  Visitantes Actualmente en la Copropiedad
                </CardTitle>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Registro de pases activos. Marque la salida al momento de abandono de las
                  instalaciones.
                </p>
              </div>

              <div className="relative w-full sm:w-64">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
                <input
                  type="text"
                  value={searchActivas}
                  onChange={(e) => setSearchActivas(e.target.value)}
                  placeholder="Buscar visitante o apartamento..."
                  className="w-full rounded-md border border-border/80 bg-background pl-8 pr-3 py-1.5 text-xs text-foreground placeholder:text-muted-foreground/60 focus:border-primary focus:outline-none"
                />
              </div>
            </div>
          </CardHeader>

          <CardContent className="p-0">
            {visitasLoading ? (
              <div className="p-8">
                <LoadingState message="Cargando visitantes activos..." />
              </div>
            ) : visitasActivasFiltradas.length === 0 ? (
              <div className="p-12 text-center">
                <UserCheck className="mx-auto h-10 w-10 text-muted-foreground/60" />
                <h4 className="mt-2 text-sm font-bold text-foreground">
                  No hay visitantes registrados en curso
                </h4>
                <p className="mt-1 text-xs text-muted-foreground">
                  {searchActivas
                    ? 'No se encontraron coincidencias para la búsqueda.'
                    : 'Todas las visitas han marcado salida o no hay ingresos activos en este momento.'}
                </p>
              </div>
            ) : (
              <>
                {/* Tabla Desktop (hidden md:table) */}
                <div className="hidden md:block overflow-x-auto">
                  <table className="w-full text-left text-xs">
                    <thead className="border-b border-border/60 bg-muted/40 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
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
                                <span className="text-[10px] text-muted-foreground font-mono">
                                  Pase #{v.idVisita}
                                </span>
                              </div>
                            </div>
                          </td>
                          <td className="px-4 py-3 font-mono text-muted-foreground">
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
                              className="border-border/80 hover:bg-rose-500/10 hover:text-rose-600 hover:border-rose-500/30 text-xs font-semibold"
                            >
                              {registrandoSalidaId === v.idVisita ? (
                                <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                              ) : (
                                <>
                                  <LogOut className="mr-1.5 h-3.5 w-3.5" />
                                  Marcar Salida
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
                            <p className="text-xs text-muted-foreground font-mono">
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
                        className="w-full border-border/80 hover:bg-rose-500/10 hover:text-rose-600 text-xs font-semibold py-2.5 min-h-[44px]"
                      >
                        {registrandoSalidaId === v.idVisita ? (
                          <RefreshCw className="h-4 w-4 animate-spin" />
                        ) : (
                          <>
                            <LogOut className="mr-2 h-4 w-4" />
                            Registrar Salida de Copropiedad
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
                    Ocupación de Parqueaderos de Visitantes
                  </CardTitle>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Disponibilidad y asignación de cupos en tiempo real
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <select
                    value={filtroParq}
                    onChange={(e) => setFiltroParq(e.target.value)}
                    className="rounded-md border border-border/80 bg-background px-3 py-1.5 text-xs text-foreground focus:border-primary focus:outline-none"
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
                <div className="p-6 text-center text-xs text-rose-600">
                  No se pudo cargar el estado de parqueaderos.
                </div>
              ) : parqueaderosFiltrados.length === 0 ? (
                <div className="p-12 text-center text-muted-foreground">
                  <Car className="mx-auto h-10 w-10 text-muted-foreground/60" />
                  <p className="mt-2 text-xs">No hay parqueaderos que coincidan con el filtro.</p>
                </div>
              ) : (
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
                  {parqueaderosFiltrados.map((p) => {
                    const ocupado = p.estado === 'OCUPADO';
                    const disponible = p.estado === 'DISPONIBLE';
                    return (
                      <div
                        key={p.idParqueadero}
                        className={`flex flex-col justify-between rounded-xl border p-3.5 transition-all ${
                          ocupado
                            ? 'border-rose-500/30 bg-rose-500/5 dark:bg-rose-500/10'
                            : disponible
                              ? 'border-emerald-500/30 bg-emerald-500/5 dark:bg-emerald-500/10'
                              : 'border-border/80 bg-muted/20'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-mono text-sm font-extrabold text-foreground">
                            {p.codigo || p.numeroParqueadero}
                          </span>
                          <Car
                            className={`h-4 w-4 ${
                              ocupado
                                ? 'text-rose-500'
                                : disponible
                                  ? 'text-emerald-500'
                                  : 'text-muted-foreground'
                            }`}
                          />
                        </div>

                        <div className="mt-3 space-y-1">
                          <Badge
                            variant={ocupado ? 'danger' : disponible ? 'success' : 'secondary'}
                            className="text-[10px] font-bold"
                          >
                            {p.estado}
                          </Badge>
                          <p className="text-[10px] text-muted-foreground truncate">
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
              Historial de Accesos Recientes en Garita
            </CardTitle>
            <p className="text-xs text-muted-foreground mt-0.5">
              Auditoría cronológica de ingresos y salidas en el condominio
            </p>
          </CardHeader>

          <CardContent className="p-0">
            {visitasLoading ? (
              <div className="p-8">
                <LoadingState message="Cargando historial de accesos..." />
              </div>
            ) : todasVisitas.length === 0 ? (
              <div className="p-12 text-center text-muted-foreground">
                <Clock className="mx-auto h-10 w-10 text-muted-foreground/60" />
                <p className="mt-2 text-xs">No hay registros de acceso en el historial.</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="border-b border-border/60 bg-muted/40 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
                    <tr>
                      <th className="px-4 py-3">Pase #</th>
                      <th className="px-4 py-3">Visitante</th>
                      <th className="px-4 py-3">Apartamento</th>
                      <th className="px-4 py-3">Fecha y Hora</th>
                      <th className="px-4 py-3">Estado Final</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {todasVisitas.slice(0, 20).map((v) => (
                      <tr key={v.idVisita} className="hover:bg-muted/30 transition-colors">
                        <td className="px-4 py-3 font-mono font-semibold text-muted-foreground">
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
                                ? 'neutral'
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
