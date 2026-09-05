import { useState, useRef, useEffect, useMemo } from 'react';
import { toast } from 'sonner';
import {
  Package,
  PlusCircle,
  Inbox,
  CheckCircle2,
  Clock,
  Search,
  X,
  Camera,
  RefreshCw,
  ShieldCheck,
  Send,
  Building2,
  User,
  Hash,
  KeyRound,
  Eye,
  Copy,
  Check,
  RotateCcw,
  Boxes,
} from 'lucide-react';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import { formatDate, imageSrc } from '../lib/utils.js';
import PageContainer from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import {
  Breadcrumb,
  BreadcrumbList,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbSeparator,
  BreadcrumbPage,
} from '../components/ui/Breadcrumb.jsx';

const COMPANIAS_RAPIDAS = [
  'Servientrega',
  'Coordinadora',
  'Interrapidísimo',
  'DHL',
  'FedEx',
  'Amazon',
  'Mercado Libre',
  'Envía',
  'TCC',
  'Otro',
];

const TAMANOS = [
  { id: 'SOBRE', label: 'Sobre / Documento', desc: 'Correspondencia plana' },
  { id: 'PEQUENO', label: 'Pequeño', desc: 'Caja o paquete de mano' },
  { id: 'MEDIANO', label: 'Mediano', desc: 'Caja estándar hasta 40cm' },
  { id: 'GRANDE', label: 'Grande', desc: 'Caja voluminosa o pesada' },
  { id: 'VOLUMINOSO', label: 'Voluminoso', desc: 'Electrodoméstico o mueble' },
];

export default function PaquetesPage() {
  const api = useTenantApi();
  const { user } = useAuth();

  const [activeTab, setActiveTab] = useState('recibir'); // 'recibir' | 'pendientes' | 'historial'
  const [search, setSearch] = useState('');
  const [filtroTamano, setFiltroTamano] = useState('TODOS');

  // Formulario de recepción
  const [selectedUnidad, setSelectedUnidad] = useState('');
  const [destinatario, setDestinatario] = useState('');
  const [empresaMensajeria, setEmpresaMensajeria] = useState('Servientrega');
  const [otraEmpresa, setOtraEmpresa] = useState('');
  const [numeroGuia, setNumeroGuia] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [tamano, setTamano] = useState('MEDIANO');
  const [foto, setFoto] = useState(null);
  const [registrando, setRegistrando] = useState(false);

  // Cámara
  const [camaraActiva, setCamaraActiva] = useState(false);
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const streamRef = useRef(null);

  // Modales
  const [modalSuccessPin, setModalSuccessPin] = useState(null); // { pin, paquete }
  const [modalEntrega, setModalEntrega] = useState(null); // Paquete a entregar
  const [pinIngresado, setPinIngresado] = useState('');
  const [entregando, setEntregando] = useState(false);
  const [detalleModal, setDetalleModal] = useState(null);
  const [copiedPin, setCopiedPin] = useState(false);

  // Carga de Unidades
  const { data: unidadesRaw } = useFetch(() => api.get('/units'), []);
  const unidades = useMemo(() => {
    const list = unidadesRaw?.items || unidadesRaw || [];
    return Array.isArray(list) ? list : [];
  }, [unidadesRaw]);

  // Carga de Paquetes
  const {
    data: paquetesRaw,
    loading: loadingPaquetes,
    refetch: refetchPaquetes,
  } = useFetch(() => api.get('/paquetes'), []);

  const paquetes = useMemo(() => {
    const list = paquetesRaw?.items || paquetesRaw || [];
    return Array.isArray(list) ? list : [];
  }, [paquetesRaw]);

  // Manejo de Cámara
  useEffect(() => {
    return () => detenerCamara();
  }, []);

  async function abrirCamara() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } },
        audio: false,
      });
      streamRef.current = stream;
      setCamaraActiva(true);
      setFoto(null);
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play();
        }
      }, 50);
    } catch (err) {
      toast.error('No se pudo acceder a la cámara: ' + err.message);
    }
  }

  function detenerCamara() {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
    setCamaraActiva(false);
  }

  function capturarFoto() {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas) return;
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const dataUrl = canvas.toDataURL('image/jpeg', 0.82);
    setFoto(dataUrl);
    detenerCamara();
  }

  function handleFileUpload(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new window.FileReader();
    reader.onload = (evt) => {
      setFoto(evt.target?.result);
    };
    reader.readAsDataURL(file);
  }

  // Auto-completar nombre destinatario cuando se elige unidad
  function handleSelectUnidad(val) {
    setSelectedUnidad(val);
    const u = unidades.find((x) => String(x.idApartamento || x.idUnidad) === String(val));
    if (u?.nombrePropietario) {
      setDestinatario(u.nombrePropietario);
    }
  }

  // Registrar Paquete
  async function handleRegistrar(e) {
    e.preventDefault();
    if (!selectedUnidad) {
      toast.error('Seleccione el apartamento destinatario');
      return;
    }
    if (!descripcion.trim()) {
      toast.error('Ingrese una breve descripción del paquete');
      return;
    }

    const companyFinal =
      empresaMensajeria === 'Otro' ? otraEmpresa.trim() || 'Servicio de Envíos' : empresaMensajeria;

    setRegistrando(true);
    try {
      const payload = {
        idUnidad: Number(selectedUnidad),
        idPersonaDestinatario: null,
        empresaMensajeria: companyFinal,
        numeroGuia: numeroGuia.trim() || null,
        descripcion: descripcion.trim(),
        tamano: tamano,
        fotoPaqueteUrl: foto || null,
        idPorteria: 1,
      };

      const res = await api.post('/paquetes', payload);
      const creado = res?.data || res;
      const pin = creado?.codigoRetiroPin || 'GENERADO';

      toast.success('Paquete registrado y notificación enviada al residente');

      setModalSuccessPin({
        pin: pin,
        paquete: creado,
      });

      // Limpiar form
      setSelectedUnidad('');
      setDestinatario('');
      setNumeroGuia('');
      setDescripcion('');
      setFoto(null);
      refetchPaquetes();
    } catch (err) {
      toast.error(err.message || 'Error al registrar el paquete');
    } finally {
      setRegistrando(false);
    }
  }

  // Entregar Paquete
  async function handleConfirmarEntrega() {
    if (!modalEntrega) return;
    setEntregando(true);
    try {
      if (pinIngresado.trim()) {
        // Validación con PIN en endpoint oficial
        await api.post(`/paquetes/${modalEntrega.idPaquete || modalEntrega.idMensaje}/entrega`, {
          codigoRetiroPin: pinIngresado.trim().toUpperCase(),
          idPersonaRecibe: user?.userId || 1,
          idPorteria: 1,
          firmaUrl: null,
        });
      } else {
        // Entrega directa supervisada por portería
        await api.put(`/buzon/${modalEntrega.idPaquete || modalEntrega.idMensaje}/entregado`);
      }
      toast.success('Paquete entregado con éxito');
      setModalEntrega(null);
      setPinIngresado('');
      refetchPaquetes();
    } catch (err) {
      toast.error(err.message || 'PIN incorrecto o error en la entrega');
    } finally {
      setEntregando(false);
    }
  }

  // Filtrado de paquetes
  const paquetesEnCustodia = useMemo(() => {
    return paquetes.filter((p) => !p.entregado && p.estado !== 'ENTREGADO');
  }, [paquetes]);

  const paquetesEntregados = useMemo(() => {
    return paquetes.filter((p) => p.entregado || p.estado === 'ENTREGADO');
  }, [paquetes]);

  const paquetesFiltrados = useMemo(() => {
    const base = activeTab === 'historial' ? paquetesEntregados : paquetesEnCustodia;
    return base.filter((p) => {
      const matchTamano = filtroTamano === 'TODOS' || p.tamano === filtroTamano;
      if (!matchTamano) return false;

      if (!search.trim()) return true;
      const q = search.toLowerCase();
      const numApto = String(p.numeroApartamento || '').toLowerCase();
      const resName = String(p.nombreResidente || p.nombreDestinatario || '').toLowerCase();
      const empresa = String(p.empresaMensajeria || '').toLowerCase();
      const guia = String(p.numeroGuia || '').toLowerCase();
      const desc = String(p.descripcion || p.titulo || '').toLowerCase();
      return (
        numApto.includes(q) ||
        resName.includes(q) ||
        empresa.includes(q) ||
        guia.includes(q) ||
        desc.includes(q)
      );
    });
  }, [activeTab, paquetesEnCustodia, paquetesEntregados, filtroTamano, search]);

  // Métricas
  const totalHoy = useMemo(() => {
    const todayStr = new Date().toISOString().slice(0, 10);
    return paquetes.filter((p) => (p.fechaRecepcion || p.fechaCreacion || '').startsWith(todayStr))
      .length;
  }, [paquetes]);

  const entregadosHoy = useMemo(() => {
    const todayStr = new Date().toISOString().slice(0, 10);
    return paquetesEntregados.filter((p) =>
      (p.fechaEntrega || p.fechaCreacion || '').startsWith(todayStr)
    ).length;
  }, [paquetesEntregados]);

  function copyToClipboard(text) {
    navigator.clipboard.writeText(text);
    setCopiedPin(true);
    setTimeout(() => setCopiedPin(false), 2000);
    toast.success('PIN copiado al portapapeles');
  }

  return (
    <PageContainer>
      {/* 1. Breadcrumb de Navegación */}
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink to="/portero-dashboard">Garita Principal</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>Control de Paquetería</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      {/* 2. Cabecera Contextual Enterprise */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border/60 pb-5">
        <div className="space-y-1">
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Recepción y Custodia de Paquetería
            </h1>
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
              Custodia Activa
            </span>
            <span className="px-2 py-0.5 text-xs font-semibold bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 rounded border border-slate-200 dark:border-slate-700">
              PORTERO
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            Recepción segura de encomiendas, generación de PIN anti-fraude y notificación automática a residentes.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => refetchPaquetes()}
            className="inline-flex items-center gap-2 px-3 py-2 text-xs font-semibold rounded-lg bg-card border border-border hover:bg-muted text-foreground transition-colors shadow-sm"
            title="Refrescar encomiendas"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loadingPaquetes ? 'animate-spin text-emerald-500' : ''}`} />
            Actualizar
          </button>
        </div>
      </div>

      {/* 3. Strip de KPIs Operativos */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          label="En Custodia"
          value={paquetesEnCustodia.length}
          context="Pendientes de entrega"
          icon={Inbox}
          variant={paquetesEnCustodia.length > 0 ? 'warning' : 'primary'}
        />
        <MetricCard
          label="Recibidos Hoy"
          value={totalHoy}
          context="Ingresados a garita"
          icon={Package}
          variant="primary"
        />
        <MetricCard
          label="Entregados Hoy"
          value={entregadosHoy}
          context="Despachados con éxito"
          icon={CheckCircle2}
          variant="success"
        />
        <MetricCard
          label="Total Histórico"
          value={paquetes.length}
          context="Paquetes procesados"
          icon={Boxes}
          variant="neutral"
        />
      </div>

      {/* 4. Selector de Pestañas Corporativas */}
      <div className="flex items-center gap-2 border-b border-border/80 pb-px overflow-x-auto">
        <button
          onClick={() => setActiveTab('recibir')}
          className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 transition-all whitespace-nowrap min-h-[44px] ${
            activeTab === 'recibir'
              ? 'border-emerald-500 text-emerald-600 dark:text-emerald-400 bg-emerald-50/50 dark:bg-emerald-950/20'
              : 'border-transparent text-muted-foreground hover:text-foreground hover:bg-muted/50'
          }`}
        >
          <PlusCircle className="w-4 h-4" />
          Registrar Recepción
        </button>

        <button
          onClick={() => setActiveTab('pendientes')}
          className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 transition-all whitespace-nowrap min-h-[44px] ${
            activeTab === 'pendientes'
              ? 'border-emerald-500 text-emerald-600 dark:text-emerald-400 bg-emerald-50/50 dark:bg-emerald-950/20'
              : 'border-transparent text-muted-foreground hover:text-foreground hover:bg-muted/50'
          }`}
        >
          <Inbox className="w-4 h-4" />
          En Custodia ({paquetesEnCustodia.length})
        </button>

        <button
          onClick={() => setActiveTab('historial')}
          className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 transition-all whitespace-nowrap min-h-[44px] ${
            activeTab === 'historial'
              ? 'border-emerald-500 text-emerald-600 dark:text-emerald-400 bg-emerald-50/50 dark:bg-emerald-950/20'
              : 'border-transparent text-muted-foreground hover:text-foreground hover:bg-muted/50'
          }`}
        >
          <Clock className="w-4 h-4" />
          Historial Entregados ({paquetesEntregados.length})
        </button>
      </div>

      {/* 5. Contenido por Pestaña */}
      {activeTab === 'recibir' && (
        <div className="bg-card rounded-xl border border-border/80 shadow-sm overflow-hidden p-6">
          <form onSubmit={handleRegistrar} className="space-y-6">
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
              {/* Columna Izquierda: Datos del Envío */}
              <div className="lg:col-span-7 space-y-5">
                <div className="border-b border-border/60 pb-3">
                  <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                    <Building2 className="w-4 h-4 text-emerald-500" />
                    Datos de la Encomienda y Destinatario
                  </h3>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    El residente recibirá una notificación push e in-app con el PIN generado.
                  </p>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1.5">
                      Apartamento Destino *
                    </label>
                    <select
                      value={selectedUnidad}
                      onChange={(e) => handleSelectUnidad(e.target.value)}
                      required
                      className="w-full px-3 py-2.5 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
                    >
                      <option value="">Seleccionar unidad...</option>
                      {unidades.map((u) => {
                        const id = u.idApartamento || u.idUnidad;
                        const num = u.numero || u.numeroApartamento || u.identificador;
                        return (
                          <option key={id} value={id}>
                            Apto {num} {u.torre ? `(Torre ${u.torre})` : ''}
                          </option>
                        );
                      })}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1.5">
                      Destinatario (Residente)
                    </label>
                    <div className="relative">
                      <User className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground" />
                      <input
                        type="text"
                        placeholder="Nombre o dejar para la unidad"
                        value={destinatario}
                        onChange={(e) => setDestinatario(e.target.value)}
                        className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
                      />
                    </div>
                  </div>
                </div>

                {/* Empresa de Mensajería */}
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5">
                    Empresa de Mensajería o Transporte *
                  </label>
                  <div className="flex flex-wrap gap-1.5 mb-2">
                    {COMPANIAS_RAPIDAS.map((c) => (
                      <button
                        key={c}
                        type="button"
                        onClick={() => setEmpresaMensajeria(c)}
                        className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition-all ${
                          empresaMensajeria === c
                            ? 'bg-emerald-500/15 border-emerald-500/50 text-emerald-600 dark:text-emerald-400'
                            : 'bg-muted/40 border-border text-muted-foreground hover:text-foreground hover:bg-muted'
                        }`}
                      >
                        {c}
                      </button>
                    ))}
                  </div>

                  {empresaMensajeria === 'Otro' && (
                    <input
                      type="text"
                      placeholder="Especifique empresa o mensajero particular"
                      value={otraEmpresa}
                      onChange={(e) => setOtraEmpresa(e.target.value)}
                      required
                      className="w-full mt-2 px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
                    />
                  )}
                </div>

                {/* Número de Guía y Tamaño */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1.5">
                      Número de Guía / Tracking
                    </label>
                    <div className="relative">
                      <Hash className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground" />
                      <input
                        type="text"
                        placeholder="Ej. TRK-99214"
                        value={numeroGuia}
                        onChange={(e) => setNumeroGuia(e.target.value)}
                        className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 font-mono"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1.5">
                      Tamaño de la Encomienda *
                    </label>
                    <select
                      value={tamano}
                      onChange={(e) => setTamano(e.target.value)}
                      className="w-full px-3 py-2.5 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
                    >
                      {TAMANOS.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.label}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {/* Descripción */}
                <div>
                  <label className="block text-xs font-semibold text-foreground mb-1.5">
                    Descripción del Paquete / Contenido *
                  </label>
                  <textarea
                    rows={2}
                    placeholder="Ej. Caja mediana de cartón sellada con cinta Mercado Libre"
                    value={descripcion}
                    onChange={(e) => setDescripcion(e.target.value)}
                    required
                    className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
                  />
                </div>
              </div>

              {/* Columna Derecha: Evidencia Fotográfica */}
              <div className="lg:col-span-5 space-y-4">
                <div className="border-b border-border/60 pb-3">
                  <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                    <Camera className="w-4 h-4 text-emerald-500" />
                    Evidencia Visual de Garita
                  </h3>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Fotografía del paquete para respaldar la custodia y estado de entrega.
                  </p>
                </div>

                {/* Viewport de Cámara o Foto */}
                <div className="relative aspect-video rounded-xl border-2 border-dashed border-border bg-slate-900 overflow-hidden flex flex-col items-center justify-center text-slate-400">
                  {camaraActiva ? (
                    <>
                      <video
                        ref={videoRef}
                        autoPlay
                        playsInline
                        muted
                        className="w-full h-full object-cover"
                      />
                      {/* HUD de encuadre */}
                      <div className="absolute inset-4 pointer-events-none border-2 border-emerald-500/50 rounded-lg flex flex-col justify-between p-2">
                        <div className="flex justify-between">
                          <div className="w-4 h-4 border-t-2 border-l-2 border-emerald-400" />
                          <div className="w-4 h-4 border-t-2 border-r-2 border-emerald-400" />
                        </div>
                        <div className="flex justify-between">
                          <div className="w-4 h-4 border-b-2 border-l-2 border-emerald-400" />
                          <div className="w-4 h-4 border-b-2 border-r-2 border-emerald-400" />
                        </div>
                      </div>
                    </>
                  ) : foto ? (
                    <div className="relative w-full h-full">
                      <img
                        src={imageSrc(foto)}
                        alt="Evidencia del paquete"
                        className="w-full h-full object-cover"
                      />
                      <button
                        type="button"
                        onClick={() => setFoto(null)}
                        className="absolute top-2 right-2 p-1.5 rounded-full bg-slate-900/80 text-white hover:bg-slate-900 transition-colors shadow"
                        title="Eliminar foto"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  ) : (
                    <div className="p-6 text-center space-y-2">
                      <div className="w-12 h-12 rounded-full bg-slate-800 flex items-center justify-center mx-auto text-slate-400">
                        <Camera className="w-6 h-6" />
                      </div>
                      <p className="text-xs text-slate-400 max-w-[200px] mx-auto">
                        Tome una foto con la cámara de garita o suba una imagen.
                      </p>
                    </div>
                  )}
                  <canvas ref={canvasRef} className="hidden" />
                </div>

                {/* Acciones de Cámara */}
                <div className="flex items-center gap-2 justify-center">
                  {!camaraActiva && !foto && (
                    <>
                      <button
                        type="button"
                        onClick={abrirCamara}
                        className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors shadow-sm min-h-[44px]"
                      >
                        <Camera className="w-4 h-4" />
                        Abrir Cámara
                      </button>
                      <label className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold rounded-lg border border-border bg-card hover:bg-muted text-foreground transition-colors cursor-pointer min-h-[44px]">
                        <span>Subir Archivo</span>
                        <input
                          type="file"
                          accept="image/*"
                          className="hidden"
                          onChange={handleFileUpload}
                        />
                      </label>
                    </>
                  )}

                  {camaraActiva && (
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={capturarFoto}
                        className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors shadow-sm min-h-[44px]"
                      >
                        <Camera className="w-4 h-4" />
                        Capturar Foto
                      </button>
                      <button
                        type="button"
                        onClick={detenerCamara}
                        className="px-3 py-2 text-xs font-semibold rounded-lg border border-border bg-card hover:bg-muted text-foreground min-h-[44px]"
                      >
                        Cancelar
                      </button>
                    </div>
                  )}

                  {foto && !camaraActiva && (
                    <button
                      type="button"
                      onClick={() => {
                        setFoto(null);
                        abrirCamara();
                      }}
                      className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold rounded-lg border border-border bg-card hover:bg-muted text-foreground min-h-[44px]"
                    >
                      <RotateCcw className="w-3.5 h-3.5" />
                      Repetir Foto
                    </button>
                  )}
                </div>
              </div>
            </div>

            {/* Botón de Enviar */}
            <div className="pt-4 border-t border-border/60 flex items-center justify-end gap-3">
              <button
                type="submit"
                disabled={registrando}
                className="inline-flex items-center gap-2 px-6 py-2.5 text-sm font-semibold rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white transition-all shadow-md hover:shadow-lg disabled:opacity-50 min-h-[44px]"
              >
                {registrando ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    Registrando y Notificando...
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    Registrar y Notificar Envío
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Pestañas de Listado (En Custodia o Historial) */}
      {activeTab !== 'recibir' && (
        <div className="space-y-4">
          {/* Barra de Filtros */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-card p-4 rounded-xl border border-border/80 shadow-sm">
            <div className="relative w-full sm:w-80">
              <Search className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground" />
              <input
                type="text"
                placeholder="Buscar por apto, residente o guía..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-9 pr-8 py-2 text-sm rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
              />
              {search && (
                <button
                  onClick={() => setSearch('')}
                  className="absolute right-2.5 top-2.5 text-muted-foreground hover:text-foreground"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>

            <div className="flex items-center gap-2 w-full sm:w-auto">
              <label className="text-xs font-semibold text-muted-foreground whitespace-nowrap">
                Tamaño:
              </label>
              <select
                value={filtroTamano}
                onChange={(e) => setFiltroTamano(e.target.value)}
                className="px-3 py-2 text-xs font-medium rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20"
              >
                <option value="TODOS">Todos los tamaños</option>
                {TAMANOS.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Vista Desktop: Tabla Analítica */}
          <div className="hidden md:block bg-card rounded-xl border border-border/80 shadow-sm overflow-hidden">
            <table className="w-full text-left text-sm">
              <thead className="bg-muted/40 border-b border-border/80 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Unidad</th>
                  <th className="px-4 py-3">Destinatario</th>
                  <th className="px-4 py-3">Mensajería</th>
                  <th className="px-4 py-3">Descripción</th>
                  <th className="px-4 py-3">Fecha Recepción</th>
                  <th className="px-4 py-3">Foto</th>
                  <th className="px-4 py-3">Estado</th>
                  <th className="px-4 py-3 text-right">Acción</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {paquetesFiltrados.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-4 py-12 text-center text-muted-foreground">
                      <Inbox className="w-10 h-10 mx-auto text-muted-foreground/40 mb-2" />
                      <p className="font-semibold text-foreground">No se encontraron paquetes</p>
                      <p className="text-xs text-muted-foreground mt-1">
                        {activeTab === 'pendientes'
                          ? 'No hay paquetes pendientes en custodia en este momento.'
                          : 'No hay paquetes entregados registrados.'}
                      </p>
                    </td>
                  </tr>
                ) : (
                  paquetesFiltrados.map((p) => {
                    const id = p.idPaquete || p.idMensaje;
                    const isEntregado = p.entregado || p.estado === 'ENTREGADO';
                    return (
                      <tr key={id} className="hover:bg-muted/30 transition-colors">
                        <td className="px-4 py-3 font-semibold text-foreground whitespace-nowrap">
                          Apto {p.numeroApartamento || 'N/A'}
                        </td>
                        <td className="px-4 py-3 text-foreground whitespace-nowrap">
                          {p.nombreResidente || p.nombreDestinatario || 'Residente'}
                        </td>
                        <td className="px-4 py-3 text-foreground whitespace-nowrap">
                          <span className="font-medium text-emerald-600 dark:text-emerald-400">
                            {p.empresaMensajeria || 'Mensajería'}
                          </span>
                          {p.numeroGuia && (
                            <span className="block text-xs font-mono text-muted-foreground">
                              {p.numeroGuia}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-foreground max-w-xs truncate">
                          <span className="font-medium">{p.descripcion || p.titulo || 'Sin descripción'}</span>
                          {p.tamano && (
                            <span className="ml-2 inline-block px-1.5 py-0.5 text-[10px] font-semibold bg-muted text-muted-foreground rounded">
                              {p.tamano}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                          {formatDate(p.fechaRecepcion || p.fechaCreacion)}
                        </td>
                        <td className="px-4 py-3">
                          {p.fotoPaqueteUrl || p.fotoCaptura ? (
                            <img
                              src={imageSrc(p.fotoPaqueteUrl || p.fotoCaptura)}
                              alt="Foto paquete"
                              onClick={() => setDetalleModal(p)}
                              className="w-10 h-10 object-cover rounded-lg border border-border cursor-pointer hover:opacity-80 transition-opacity"
                            />
                          ) : (
                            <span className="text-xs text-muted-foreground italic">Sin foto</span>
                          )}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          <span
                            className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                              isEntregado
                                ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                                : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20'
                            }`}
                          >
                            {isEntregado ? 'Entregado' : 'En Custodia'}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right whitespace-nowrap">
                          {!isEntregado ? (
                            <button
                              onClick={() => {
                                setModalEntrega(p);
                                setPinIngresado('');
                              }}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors shadow-sm min-h-[36px]"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              Entregar
                            </button>
                          ) : (
                            <button
                              onClick={() => setDetalleModal(p)}
                              className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-semibold rounded-lg border border-border bg-muted/40 hover:bg-muted text-foreground transition-colors"
                            >
                              <Eye className="w-3.5 h-3.5" />
                              Detalle
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          {/* Vista Móvil: Tarjetas Adaptativas (Priority: Estado -> Info -> Acción) */}
          <div className="md:hidden space-y-3">
            {paquetesFiltrados.length === 0 ? (
              <div className="bg-card rounded-xl border border-border/80 p-8 text-center text-muted-foreground">
                <Inbox className="w-10 h-10 mx-auto text-muted-foreground/40 mb-2" />
                <p className="font-semibold text-foreground">No hay paquetes que mostrar</p>
              </div>
            ) : (
              paquetesFiltrados.map((p) => {
                const id = p.idPaquete || p.idMensaje;
                const isEntregado = p.entregado || p.estado === 'ENTREGADO';
                return (
                  <div
                    key={id}
                    className="bg-card rounded-xl border border-border/80 p-4 space-y-3 shadow-sm"
                  >
                    <div className="flex items-center justify-between border-b border-border/60 pb-2">
                      <div className="flex items-center gap-2">
                        <span className="text-base font-bold text-foreground">
                          Apto {p.numeroApartamento || 'N/A'}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          • {p.nombreResidente || p.nombreDestinatario || 'Residente'}
                        </span>
                      </div>
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                          isEntregado
                            ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20'
                            : 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20'
                        }`}
                      >
                        {isEntregado ? 'Entregado' : 'En Custodia'}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-xs">
                      <div>
                        <span className="text-muted-foreground block">Mensajería:</span>
                        <span className="font-semibold text-foreground">
                          {p.empresaMensajeria || 'Mensajería'}
                        </span>
                        {p.numeroGuia && (
                          <span className="font-mono text-muted-foreground block text-[11px]">
                            {p.numeroGuia}
                          </span>
                        )}
                      </div>
                      <div>
                        <span className="text-muted-foreground block">Recepción:</span>
                        <span className="text-foreground">
                          {formatDate(p.fechaRecepcion || p.fechaCreacion)}
                        </span>
                      </div>
                    </div>

                    <div className="text-xs bg-muted/40 p-2.5 rounded-lg text-foreground">
                      <span className="font-medium">{p.descripcion || p.titulo || 'Sin descripción'}</span>
                      {p.tamano && (
                        <span className="ml-2 px-1.5 py-0.5 text-[10px] font-semibold bg-card border border-border rounded text-muted-foreground">
                          {p.tamano}
                        </span>
                      )}
                    </div>

                    <div className="flex items-center justify-between pt-1">
                      {p.fotoPaqueteUrl || p.fotoCaptura ? (
                        <button
                          type="button"
                          onClick={() => setDetalleModal(p)}
                          className="text-xs text-emerald-600 dark:text-emerald-400 font-semibold flex items-center gap-1 min-h-[44px]"
                        >
                          <Camera className="w-3.5 h-3.5" />
                          Ver Foto
                        </button>
                      ) : (
                        <span className="text-xs text-muted-foreground">Sin foto</span>
                      )}

                      {!isEntregado ? (
                        <button
                          type="button"
                          onClick={() => {
                            setModalEntrega(p);
                            setPinIngresado('');
                          }}
                          className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm min-h-[44px]"
                        >
                          <CheckCircle2 className="w-4 h-4" />
                          Entregar Paquete
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setDetalleModal(p)}
                          className="px-3 py-1.5 text-xs font-semibold rounded-lg border border-border bg-card text-foreground min-h-[44px]"
                        >
                          Ver Detalle
                        </button>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}

      {/* MODAL 1: Confirmación de Recepción con PIN */}
      <Modal
        open={!!modalSuccessPin}
        onClose={() => setModalSuccessPin(null)}
        title="Encomienda Registrada Exitosamente"
      >
        {modalSuccessPin && (
          <div className="space-y-5 text-center p-2">
            <div className="w-14 h-14 rounded-full bg-emerald-500/10 text-emerald-500 flex items-center justify-center mx-auto border border-emerald-500/20">
              <ShieldCheck className="w-7 h-7" />
            </div>

            <div className="space-y-1">
              <h3 className="text-lg font-bold text-foreground">
                Paquete en Custodia de Garita
              </h3>
              <p className="text-xs text-muted-foreground">
                Se ha generado el código PIN de retiro y se notificó a la unidad.
              </p>
            </div>

            {/* Tarjeta de PIN */}
            <div className="p-4 rounded-xl bg-slate-950 border border-emerald-500/40 space-y-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-emerald-400">
                Código de Retiro PIN
              </span>
              <div className="flex items-center justify-center gap-3">
                <span className="text-3xl font-black font-mono tracking-widest text-emerald-400">
                  {modalSuccessPin.pin}
                </span>
                <button
                  type="button"
                  onClick={() => copyToClipboard(modalSuccessPin.pin)}
                  className="p-2 rounded-lg bg-slate-800 text-slate-300 hover:text-white hover:bg-slate-700 transition-colors"
                  title="Copiar PIN"
                >
                  {copiedPin ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                </button>
              </div>
              <p className="text-[11px] text-slate-400">
                El residente debe presentar este código para retirar la encomienda.
              </p>
            </div>

            <div className="text-left text-xs bg-muted/40 p-3 rounded-lg space-y-1 border border-border/60">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Apartamento:</span>
                <span className="font-semibold text-foreground">
                  Apto {modalSuccessPin.paquete?.numeroApartamento || 'N/A'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Empresa:</span>
                <span className="font-semibold text-foreground">
                  {modalSuccessPin.paquete?.empresaMensajeria || 'Mensajería'}
                </span>
              </div>
              {modalSuccessPin.paquete?.numeroGuia && (
                <div className="flex justify-between font-mono">
                  <span className="text-muted-foreground">Guía:</span>
                  <span className="text-foreground">{modalSuccessPin.paquete.numeroGuia}</span>
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={() => setModalSuccessPin(null)}
              className="w-full py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-sm transition-colors min-h-[44px]"
            >
              Continuar
            </button>
          </div>
        )}
      </Modal>

      {/* MODAL 2: Entrega de Paquete (Validación de PIN) */}
      <Modal
        open={!!modalEntrega}
        onClose={() => setModalEntrega(null)}
        title="Registrar Entrega de Paquete"
      >
        {modalEntrega && (
          <div className="space-y-4 p-1">
            <div className="bg-muted/40 p-3 rounded-lg border border-border/60 space-y-1 text-xs">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Apartamento Destino:</span>
                <span className="font-bold text-foreground">
                  Apto {modalEntrega.numeroApartamento || 'N/A'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Destinatario:</span>
                <span className="text-foreground">
                  {modalEntrega.nombreResidente || modalEntrega.nombreDestinatario || 'Residente'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Empresa / Guía:</span>
                <span className="text-foreground">
                  {modalEntrega.empresaMensajeria} {modalEntrega.numeroGuia ? `(${modalEntrega.numeroGuia})` : ''}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Descripción:</span>
                <span className="text-foreground">{modalEntrega.descripcion || modalEntrega.titulo}</span>
              </div>
            </div>

            <div className="space-y-2">
              <label className="block text-xs font-semibold text-foreground">
                PIN de Seguridad Presentado por el Residente (Opcional si es entrega supervisada)
              </label>
              <div className="relative">
                <KeyRound className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground" />
                <input
                  type="text"
                  placeholder="Ingrese el PIN de 6 dígitos"
                  value={pinIngresado}
                  onChange={(e) => setPinIngresado(e.target.value.toUpperCase())}
                  className="w-full pl-9 pr-3 py-2 text-sm font-mono tracking-wider uppercase rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
                />
              </div>
              <p className="text-[11px] text-muted-foreground">
                Si el residente presenta su PIN, se verificará contra el registro. Si entrega personalmente tras acreditar identidad, puede autorizar directamente.
              </p>
            </div>

            <div className="flex items-center justify-end gap-2 pt-3 border-t border-border/60">
              <button
                type="button"
                onClick={() => setModalEntrega(null)}
                className="px-4 py-2 text-xs font-semibold rounded-lg border border-border bg-card hover:bg-muted text-foreground min-h-[44px]"
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={handleConfirmarEntrega}
                disabled={entregando}
                className="inline-flex items-center gap-2 px-5 py-2 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors shadow-sm disabled:opacity-50 min-h-[44px]"
              >
                {entregando ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                    Confirmando Entrega...
                  </>
                ) : (
                  <>
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    Confirmar Entrega
                  </>
                )}
              </button>
            </div>
          </div>
        )}
      </Modal>

      {/* MODAL 3: Detalle y Foto Ampliada */}
      <Modal
        open={!!detalleModal}
        onClose={() => setDetalleModal(null)}
        title="Detalle de Encomienda"
      >
        {detalleModal && (
          <div className="space-y-4 p-1">
            {(detalleModal.fotoPaqueteUrl || detalleModal.fotoCaptura) && (
              <div className="aspect-video w-full rounded-xl overflow-hidden border border-border bg-slate-950">
                <img
                  src={imageSrc(detalleModal.fotoPaqueteUrl || detalleModal.fotoCaptura)}
                  alt="Foto del paquete"
                  className="w-full h-full object-contain"
                />
              </div>
            )}

            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg bg-muted/40 border border-border/60">
                <span className="text-muted-foreground block">Apartamento:</span>
                <span className="font-bold text-foreground text-sm">
                  Apto {detalleModal.numeroApartamento || 'N/A'}
                </span>
              </div>
              <div className="p-2.5 rounded-lg bg-muted/40 border border-border/60">
                <span className="text-muted-foreground block">Destinatario:</span>
                <span className="font-semibold text-foreground">
                  {detalleModal.nombreResidente || detalleModal.nombreDestinatario || 'Residente'}
                </span>
              </div>
              <div className="p-2.5 rounded-lg bg-muted/40 border border-border/60">
                <span className="text-muted-foreground block">Empresa de Mensajería:</span>
                <span className="font-semibold text-foreground">{detalleModal.empresaMensajeria}</span>
              </div>
              <div className="p-2.5 rounded-lg bg-muted/40 border border-border/60">
                <span className="text-muted-foreground block">Número de Guía:</span>
                <span className="font-mono text-foreground">{detalleModal.numeroGuia || 'N/A'}</span>
              </div>
            </div>

            <div className="p-3 rounded-lg bg-muted/40 border border-border/60 text-xs space-y-1">
              <span className="text-muted-foreground block">Descripción:</span>
              <p className="text-foreground">{detalleModal.descripcion || detalleModal.titulo}</p>
            </div>

            <div className="flex items-center justify-between text-xs text-muted-foreground pt-2 border-t border-border/60">
              <span>Recibido: {formatDate(detalleModal.fechaRecepcion || detalleModal.fechaCreacion)}</span>
              {detalleModal.fechaEntrega && <span>Entregado: {formatDate(detalleModal.fechaEntrega)}</span>}
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
