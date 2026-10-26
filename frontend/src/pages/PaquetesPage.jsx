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
  Lock,
  Check,
  RotateCcw,
  Boxes,
} from 'lucide-react';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import { formatDate, formatDateTime, formatTime, formatApto, imageSrc } from '../lib/utils.js';
import PageContainer from '../components/layout/PageContainer.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { Badge } from '../components/ui/badge.tsx';

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
  const [residentesUnidad, setResidentesUnidad] = useState([]);
  const [loadingResidentes, setLoadingResidentes] = useState(false);
  const [idPersonaDestinatario, setIdPersonaDestinatario] = useState('');
  const [destinatario, setDestinatario] = useState('');
  const [empresaMensajeria, setEmpresaMensajeria] = useState('Servientrega');
  const [otraEmpresa, setOtraEmpresa] = useState('');
  const [numeroGuia, setNumeroGuia] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [tamano, setTamano] = useState('MEDIANO');
  const [foto, setFoto] = useState(null);
  const [registrando, setRegistrando] = useState(false);
  const [errors, setErrors] = useState({});

  // Cámara
  const [camaraActiva, setCamaraActiva] = useState(false);
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const streamRef = useRef(null);

  // Modales
  const [modalSuccessPin, setModalSuccessPin] = useState(null); // { paquete } (Zero-Knowledge, el PIN no se expone al portero)
  const [modalEntrega, setModalEntrega] = useState(null); // Paquete a entregar
  const [pinIngresado, setPinIngresado] = useState('');
  const [entregando, setEntregando] = useState(false);
  const [detalleModal, setDetalleModal] = useState(null);

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
    error: errorPaquetes,
    refetch: refetchPaquetes,
  } = useFetch(() => api.get('/paquetes'), []);

  const paquetes = useMemo(() => {
    const list = paquetesRaw?.items || paquetesRaw || [];
    return Array.isArray(list) ? list : [];
  }, [paquetesRaw]);

  // Manejo de Cámara y Compresión Ligera
  useEffect(() => {
    return () => detenerCamara();
  }, []);

  function comprimirImagen(source, maxDim = 360, quality = 0.5) {
    const canvas = document.createElement('canvas');
    let w = source.videoWidth || source.naturalWidth || source.width || 640;
    let h = source.videoHeight || source.naturalHeight || source.height || 480;
    if (w > maxDim || h > maxDim) {
      if (w > h) {
        h = Math.round((h * maxDim) / w);
        w = maxDim;
      } else {
        w = Math.round((w * maxDim) / h);
        h = maxDim;
      }
    }
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(source, 0, 0, w, h);
    return canvas.toDataURL('image/jpeg', quality);
  }

  async function abrirCamara() {
    try {
      let stream;
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment', width: { ideal: 640 }, height: { ideal: 480 } },
          audio: false,
        });
      } catch {
        stream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: false,
        });
      }
      streamRef.current = stream;
      setCamaraActiva(true);
      setFoto(null);
      setTimeout(() => {
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play().catch(() => {});
        }
      }, 50);
    } catch (err) {
      toast.error('No se pudo acceder a la cámara: ' + err.message + '. Puede subir una foto desde archivo.');
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
    if (!video) return;
    const dataUrl = comprimirImagen(video);
    setFoto(dataUrl);
    detenerCamara();
  }

  function handleFileUpload(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new window.FileReader();
    reader.onload = (evt) => {
      const img = new window.Image();
      img.onload = () => {
        const compressed = comprimirImagen(img);
        setFoto(compressed);
      };
      img.src = evt.target?.result;
    };
    reader.readAsDataURL(file);
    e.target.value = '';
  }

  // Cargar residentes destinatarios cuando se elige unidad
  async function handleSelectUnidad(val) {
    setSelectedUnidad(val);
    setIdPersonaDestinatario('');
    setDestinatario('');
    if (!val) {
      setResidentesUnidad([]);
      return;
    }
    setLoadingResidentes(true);
    try {
      let list = [];
      try {
        const res = await api.get('/residentes', { params: { idApartamento: val } });
        list = Array.isArray(res) ? res : res?.items || [];
      } catch {
        list = [];
      }

      if (list.length === 0) {
        try {
          const resUnits = await api.get(`/units/${val}/residents`);
          const raw = Array.isArray(resUnits) ? resUnits : resUnits?.items || [];
          list = raw.map((r) => ({
            ID_PERSONA: r.persona?.idPersona || r.idPersona,
            NOMBRES: r.persona?.primerNombre || r.primerNombre,
            APELLIDOS: r.persona?.primerApellido || r.primerApellido,
            NUMERO_DOCUMENTO: r.persona?.numeroDocumento || r.numeroDocumento,
            TIPO: r.tipoResidente || 'RESIDENTE',
          }));
        } catch {
          list = [];
        }
      }

      setResidentesUnidad(list);
      if (list.length > 0) {
        const primer = list[0];
        const nom = `${primer.NOMBRES || primer.primerNombre || ''} ${primer.APELLIDOS || primer.primerApellido || ''}`.trim();
        setIdPersonaDestinatario(String(primer.ID_PERSONA || primer.idPersona || ''));
        setDestinatario(nom);
      } else {
        const u = unidades.find((x) => String(x.idApartamento || x.idUnidad) === String(val));
        if (u?.nombrePropietario) {
          setDestinatario(u.nombrePropietario);
        }
      }
    } catch {
      setResidentesUnidad([]);
    } finally {
      setLoadingResidentes(false);
    }
  }

  // Registrar Paquete
  async function handleRegistrar(e) {
    e.preventDefault();
    const newErrors = {};
    if (!selectedUnidad) {
      newErrors.selectedUnidad = 'Seleccione el apartamento destinatario';
    }
    // La descripción y número de guía ya no son obligatorios; la foto o datos básicos bastan
    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      toast.error('Seleccione el apartamento destinatario');
      return;
    }

    const companyFinal =
      empresaMensajeria === 'Otro' ? otraEmpresa.trim() || 'Servicio de Envíos' : empresaMensajeria;

    const descFinal = descripcion.trim() || (foto ? 'Paquete verificado con foto de evidencia' : 'Paquete recibido en portería');

    setRegistrando(true);
    setErrors({});
    try {
      const payload = {
        idUnidad: Number(selectedUnidad),
        idPersonaDestinatario: idPersonaDestinatario ? Number(idPersonaDestinatario) : null,
        empresaMensajeria: companyFinal,
        numeroGuia: numeroGuia.trim() || null,
        descripcion: descFinal,
        tamano: tamano,
        fotoPaqueteUrl: foto || null,
        idPorteria: 1,
      };

      const res = await api.post('/paquetes', payload);
      const creado = res?.data || res;

      toast.success('Paquete registrado y notificación enviada al residente');

      setModalSuccessPin({
        paquete: creado,
      });

      // Limpiar form
      setSelectedUnidad('');
      setResidentesUnidad([]);
      setIdPersonaDestinatario('');
      setDestinatario('');
      setNumeroGuia('');
      setDescripcion('');
      setFoto(null);
      setErrors({});
      refetchPaquetes();
    } catch (err) {
      toast.error(err.message || 'Error al registrar el paquete');
      const apiErrors = err.errors || err.response?.data?.errors;
      if (apiErrors && typeof apiErrors === 'object') {
        setErrors((prev) => ({ ...prev, ...apiErrors }));
      }
    } finally {
      setRegistrando(false);
    }
  }

  // Entregar Paquete
  async function handleConfirmarEntrega() {
    if (!modalEntrega) return;
    if (!pinIngresado.trim()) {
      toast.error('Debe ingresar el código PIN de 4 dígitos suministrado por el residente');
      return;
    }
    setEntregando(true);
    try {
      await api.post(`/paquetes/${modalEntrega.idPaquete || modalEntrega.idMensaje}/entrega`, {
        codigoRetiroPin: pinIngresado.trim(),
        idPersonaRecibe: user?.userId || 1,
        idPorteria: 1,
        firmaUrl: null,
      });
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

  return (
    <PageContainer
      breadcrumbs={[
        { label: 'Panel de portería', href: '/portero-dashboard' },
        { label: 'Paquetes', active: true },
      ]}
    >
      <PageHeader
        title="Recepción y custodia de paquetería"
        subtitle="Registre cada encomienda con foto; el residente recibe un PIN de retiro y usted confirma la entrega con ese PIN."
        action={
          <button
            type="button"
            onClick={() => refetchPaquetes()}
            className={'inline-flex min-h-11 items-center justify-center gap-2 rounded-md border border-input/60 bg-surface px-3.5 text-sm font-semibold text-on-surface shadow-xs transition-colors duration-150 hover:border-primary/70 hover:bg-surface-container sm:min-h-10'}
            aria-label="Actualizar encomiendas"
          >
            <RefreshCw className={`h-4 w-4 ${loadingPaquetes ? 'animate-spin' : ''}`} aria-hidden="true" />
            Actualizar
          </button>
        }
      />

      {/* 3. Strip de KPIs Operativos */}
      <div className="grid grid-cols-1 gap-3 min-[420px]:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="En custodia"
          value={paquetesEnCustodia.length}
          context="Pendientes de entrega"
          icon={Inbox}
          variant={paquetesEnCustodia.length > 0 ? 'warning' : 'primary'}
        />
        <MetricCard
          label="Recibidos hoy"
          value={totalHoy}
          context="Ingresados a la garita"
          icon={Package}
          variant="primary"
        />
        <MetricCard
          label="Entregados hoy"
          value={entregadosHoy}
          context="Entregados con PIN"
          icon={CheckCircle2}
          variant="success"
        />
        <MetricCard
          label="Total registrado"
          value={paquetes.length}
          context="Paquetes procesados"
          icon={Boxes}
          variant="secondary"
        />
      </div>

      {/* 4. Selector de Pestañas Corporativas */}
      <div className="flex gap-1 overflow-x-auto rounded-lg border border-border bg-surface-container p-1" role="group" aria-label="Secciones de paquetería">
        <button
          type="button"
          onClick={() => setActiveTab('recibir')}
          aria-pressed={activeTab === 'recibir'}
          className={`flex min-h-11 shrink-0 items-center gap-2 whitespace-nowrap rounded-md px-4 text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
            activeTab === 'recibir'
              ? 'bg-surface text-primary-text shadow-sm'
              : 'text-text-secondary hover:bg-surface/60 hover:text-on-surface'
          }`}
        >
          <PlusCircle className="w-4 h-4" />
          Registrar Recepción
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('pendientes')}
          aria-pressed={activeTab === 'pendientes'}
          className={`flex min-h-11 shrink-0 items-center gap-2 whitespace-nowrap rounded-md px-4 text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
            activeTab === 'pendientes'
              ? 'bg-surface text-primary-text shadow-sm'
              : 'text-text-secondary hover:bg-surface/60 hover:text-on-surface'
          }`}
        >
          <Inbox className="w-4 h-4" />
          En Custodia (<span className="tabular-nums">{paquetesEnCustodia.length}</span>)
        </button>

        <button
          type="button"
          onClick={() => setActiveTab('historial')}
          aria-pressed={activeTab === 'historial'}
          className={`flex min-h-11 shrink-0 items-center gap-2 whitespace-nowrap rounded-md px-4 text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
            activeTab === 'historial'
              ? 'bg-surface text-primary-text shadow-sm'
              : 'text-text-secondary hover:bg-surface/60 hover:text-on-surface'
          }`}
        >
          <Clock className="w-4 h-4" />
          Historial Entregados (<span className="tabular-nums">{paquetesEntregados.length}</span>)
        </button>
      </div>

      {/* 5. Contenido por Pestaña */}
      {activeTab === 'recibir' && (
        <div className="rounded-lg border border-border bg-surface p-4 shadow-sm sm:p-6">
          <form onSubmit={handleRegistrar} className="space-y-6">
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
              {/* Columna Izquierda: Datos del Envío */}
              <div className="lg:col-span-7 space-y-5">
                <div className="border-b border-border/60 pb-3">
                  <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                    <Building2 className="w-4 h-4 text-primary-text" />
                    Datos de la Encomienda y destinatario
                  </h3>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    El residente recibe una notificación con el PIN de retiro.
                  </p>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="paq-unidad" className="mb-1.5 block text-sm font-semibold text-on-surface">
                      Apartamento destino <span className="text-destructive" aria-hidden="true">*</span>
                    </label>
                    <select
                      id="paq-unidad"
                      value={selectedUnidad}
                      onChange={(e) => {
                        handleSelectUnidad(e.target.value);
                        setErrors((prev) => ({ ...prev, selectedUnidad: undefined }));
                      }}
                      required
                      aria-invalid={Boolean(errors.selectedUnidad)}
                      aria-describedby={errors.selectedUnidad ? 'paq-unidad-error' : undefined}
                      className={`h-10 w-full rounded-md border bg-surface px-3 text-sm text-on-surface transition-[border-color,box-shadow] duration-150 focus-visible:outline-none focus-visible:ring-[3px] ${
                        errors.selectedUnidad
                          ? '!border-destructive focus-visible:ring-destructive/30'
                          : 'border-input focus-visible:border-ring focus-visible:ring-ring/25'
                      }`}
                    >
                      <option value="">Seleccionar unidad...</option>
                      {unidades.map((u) => {
                        const id = u.idApartamento || u.idUnidad;
                        const num = u.numero || u.numeroApartamento || u.identificador;
                        return (
                          <option key={id} value={id}>
                            {formatApto(num)} {u.torre ? `(Torre ${u.torre})` : ''}
                          </option>
                        );
                      })}
                    </select>
                    {errors.selectedUnidad && (
                      <p id="paq-unidad-error" role="alert" className="text-xs text-destructive font-medium flex items-center gap-1.5 mt-1 animate-fadeIn">
                        <span className="inline-block w-1.5 h-1.5 rounded-full bg-destructive shrink-0" />
                        <span>{errors.selectedUnidad}</span>
                      </p>
                    )}
                  </div>

                  <div>
                    <label htmlFor="paq-destinatario" className="mb-1.5 block text-sm font-semibold text-on-surface">
                      Destinatario
                    </label>
                    {loadingResidentes ? (
                      <div className="flex h-10 items-center gap-2 rounded-md border border-border bg-surface-container px-3 text-xs text-text-secondary" role="status">
                        <RefreshCw className="w-3.5 h-3.5 animate-spin text-primary-text shrink-0" />
                        <span>Cargando residentes...</span>
                      </div>
                    ) : residentesUnidad.length > 0 ? (
                      <div className="space-y-1.5">
                        <select
                          id="paq-destinatario"
                          value={idPersonaDestinatario}
                          onChange={(e) => {
                            const val = e.target.value;
                            setIdPersonaDestinatario(val);
                            const r = residentesUnidad.find((x) => String(x.ID_PERSONA || x.idPersona) === String(val));
                            if (r) {
                              const nom = `${r.NOMBRES || r.primerNombre || ''} ${r.APELLIDOS || r.primerApellido || ''}`.trim();
                              setDestinatario(nom);
                            } else {
                              setDestinatario('');
                            }
                          }}
                          className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 px-3"
                        >
                          <option value="">Toda la unidad</option>
                          {residentesUnidad.map((r) => {
                            const idPers = r.ID_PERSONA || r.idPersona;
                            const nom = `${r.NOMBRES || r.primerNombre || ''} ${r.APELLIDOS || r.primerApellido || ''}`.trim();
                            const doc = r.NUMERO_DOCUMENTO || r.numeroDocumento;
                            const tipo = r.TIPO || r.tipoResidente || '';
                            const tagTipo = tipo === 'RESIDENTE_CONVIVENCIA' || tipo === 'CONVIVIENTE'
                              ? ' (Conviviente)'
                              : tipo === 'RESIDENTE' || tipo === 'PROPIETARIO'
                              ? ' (Titular)'
                              : tipo ? ` (${tipo})` : '';
                            return (
                              <option key={idPers} value={idPers}>
                                {nom}{tagTipo}{doc ? ` - Doc: ${doc}` : ''}
                              </option>
                            );
                          })}
                        </select>
                        {destinatario && (
                          <p className="text-xs text-muted-foreground flex items-center gap-1">
                            <Check className="w-3 h-3 text-primary-text" />
                            <span>Destinatario: <strong>{destinatario}</strong></span>
                          </p>
                        )}
                      </div>
                    ) : (
                      <div className="relative">
                        <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
                        <input
                          id="paq-destinatario"
                          type="text"
                          placeholder={selectedUnidad ? 'Nombre o dejar para toda la unidad' : 'Seleccione primero el apartamento'}
                          value={destinatario}
                          onChange={(e) => setDestinatario(e.target.value)}
                          className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 pl-9 pr-3"
                        />
                      </div>
                    )}
                  </div>
                </div>

                {/* Empresa de Mensajería */}
                <div>
                  <p id="paq-empresa-label" className="mb-1.5 block text-sm font-semibold text-on-surface">
                    Empresa de mensajería o transporte <span className="text-destructive" aria-hidden="true">*</span>
                  </p>
                  <div className="mb-2 flex flex-wrap gap-1.5" role="group" aria-labelledby="paq-empresa-label">
                    {COMPANIAS_RAPIDAS.map((c) => (
                      <button
                        key={c}
                        type="button"
                        onClick={() => setEmpresaMensajeria(c)}
                        aria-pressed={empresaMensajeria === c}
                        className={`min-h-10 rounded-md border px-3 text-sm font-semibold transition-colors duration-150 ${
                          empresaMensajeria === c
                            ? 'border-btn-primary bg-btn-primary text-white'
                            : 'border-input/60 bg-surface text-on-surface hover:border-primary/70'
                        }`}
                      >
                        {c}
                      </button>
                    ))}
                  </div>

                  {empresaMensajeria === 'Otro' && (
                    <input
                      type="text"
                      aria-label="Otra empresa o mensajero"
                      placeholder="Especifique empresa o mensajero particular"
                      value={otraEmpresa}
                      onChange={(e) => setOtraEmpresa(e.target.value)}
                      required
                      className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 mt-2 px-3"
                    />
                  )}
                </div>

                {/* Número de Guía y Tamaño */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label htmlFor="paq-guia" className="mb-1.5 block text-sm font-semibold text-on-surface">
                      Número de guía (opcional)
                    </label>
                    <div className="relative">
                      <Hash className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
                      <input
                        id="paq-guia"
                        type="text"
                        placeholder="ej. TRK-99214"
                        value={numeroGuia}
                        onChange={(e) => setNumeroGuia(e.target.value)}
                        className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 pl-9 pr-3 tabular-nums"
                      />
                    </div>
                  </div>

                  <div>
                    <label htmlFor="paq-tamano" className="mb-1.5 block text-sm font-semibold text-on-surface">
                      Tamaño de la encomienda <span className="text-destructive" aria-hidden="true">*</span>
                    </label>
                    <select
                      id="paq-tamano"
                      value={tamano}
                      onChange={(e) => setTamano(e.target.value)}
                      className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 px-3"
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
                  <label htmlFor="paq-descripcion" className="mb-1.5 block text-sm font-semibold text-on-surface">
                    Descripción del paquete (opcional)
                  </label>
                  <textarea
                    id="paq-descripcion"
                    rows={2}
                    placeholder="Opcional - con la fotografía basta, o describa brevemente el paquete"
                    value={descripcion}
                    onChange={(e) => {
                      setDescripcion(e.target.value);
                      setErrors((prev) => ({ ...prev, descripcion: undefined }));
                    }}
                    aria-invalid={Boolean(errors.descripcion)}
                    className="w-full rounded-md border border-input bg-surface px-3 py-2 text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25"
                  />
                  {errors.descripcion && (
                    <p role="alert" className="text-xs text-destructive font-medium flex items-center gap-1.5 mt-1 animate-fadeIn">
                      <span className="inline-block w-1.5 h-1.5 rounded-full bg-destructive shrink-0" />
                      <span>{errors.descripcion}</span>
                    </p>
                  )}
                </div>
              </div>

              {/* Columna Derecha: Evidencia Fotográfica */}
              <div className="lg:col-span-5 space-y-4">
                <div className="border-b border-border/60 pb-3">
                  <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                    <Camera className="w-4 h-4 text-primary-text" />
                    Foto de evidencia
                  </h3>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Respalda el estado del paquete al recibirlo y al entregarlo.
                  </p>
                </div>

                {/* Viewport de Cámara o Foto */}
                <div className="relative flex aspect-video flex-col items-center justify-center overflow-hidden rounded-lg border border-dashed border-input bg-surface-container text-text-muted">
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
                      <div className="absolute inset-4 pointer-events-none border-2 border-[#A3B0FF]/70 rounded-lg flex flex-col justify-between p-2">
                        <div className="flex justify-between">
                          <div className="w-4 h-4 border-t-2 border-l-2 border-[#A3B0FF]" />
                          <div className="w-4 h-4 border-t-2 border-r-2 border-[#A3B0FF]" />
                        </div>
                        <div className="flex justify-between">
                          <div className="w-4 h-4 border-b-2 border-l-2 border-[#A3B0FF]" />
                          <div className="w-4 h-4 border-b-2 border-r-2 border-[#A3B0FF]" />
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
                        className="absolute right-2 top-2 flex h-10 w-10 items-center justify-center rounded-md border border-border bg-surface text-on-surface shadow-sm transition-colors hover:bg-surface-container"
                        aria-label="Eliminar foto"
                      >
                        <X className="h-4 w-4" aria-hidden="true" />
                      </button>
                    </div>
                  ) : (
                    <div className="p-6 text-center space-y-2">
                      <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-md border border-dashed border-input bg-surface text-text-secondary">
                        <Camera className="w-6 h-6" />
                      </div>
                      <p className="text-xs text-text-muted max-w-[200px] mx-auto">
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
                        className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold rounded-lg bg-btn-primary hover:bg-btn-primary-hover text-white transition-colors shadow-sm min-h-[44px]"
                      >
                        <Camera className="w-4 h-4" />
                        Abrir cámara
                      </button>
                      <label className="inline-flex min-h-11 items-center justify-center gap-2 rounded-md border border-input/60 bg-surface px-3.5 text-sm font-semibold text-on-surface shadow-xs transition-colors duration-150 hover:border-primary/70 hover:bg-surface-container sm:min-h-10 cursor-pointer focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2">
                        <span>Subir archivo</span>
                        <input
                          type="file"
                          accept="image/*"
                          capture="environment"
                          className="sr-only"
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
                        className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-lg bg-btn-primary hover:bg-btn-primary-hover text-white transition-colors shadow-sm min-h-[44px]"
                      >
                        <Camera className="w-4 h-4" />
                        Capturar foto
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
                      Repetir foto
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
                className="inline-flex h-12 items-center justify-center gap-2 rounded-md bg-btn-primary px-6 text-base font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-btn-primary-hover disabled:opacity-50"
              >
                {registrando ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    Registrando y notificando...
                  </>
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    Registrar y Notificar envío
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
          <div className="flex flex-col items-stretch justify-between gap-3 rounded-lg border border-border bg-surface p-3 shadow-sm sm:flex-row sm:items-center sm:p-4">
            <div className="relative w-full sm:w-80">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
              <input
                type="search"
                aria-label="Buscar paquetes"
                placeholder="Buscar por apto, residente o guía"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 pl-9 pr-10"
              />
              {search && (
                <button
                  type="button"
                  onClick={() => setSearch('')}
                  aria-label="Limpiar búsqueda"
                  className="absolute right-0 top-0 flex h-10 w-10 items-center justify-center text-text-secondary hover:text-on-surface"
                >
                  <X className="h-4 w-4" aria-hidden="true" />
                </button>
              )}
            </div>

            <div className="flex items-center gap-2 w-full sm:w-auto">
              <label htmlFor="paq-filtro-tamano" className="whitespace-nowrap text-sm font-semibold text-text-secondary">
                Tamaño
              </label>
              <select
                id="paq-filtro-tamano"
                value={filtroTamano}
                onChange={(e) => setFiltroTamano(e.target.value)}
                className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 px-3 sm:w-48"
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

          {loadingPaquetes && paquetes.length === 0 ? (
            <div className="rounded-lg border border-border bg-surface shadow-sm">
              <LoadingState message="Cargando encomiendas..." />
            </div>
          ) : errorPaquetes && paquetes.length === 0 ? (
            <ErrorState
              title="No se pudieron cargar las encomiendas"
              message={errorPaquetes.message || 'Revise la conexión e intente de nuevo.'}
              onRetry={refetchPaquetes}
            />
          ) : (
          <>
          {/* Vista Desktop: Tabla Analítica */}
          <div className="hidden overflow-x-auto rounded-lg border border-border bg-surface shadow-sm md:block">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border bg-surface-container text-xs font-bold text-text-secondary">
                <tr>
                  <th className="px-4 py-3">Unidad</th>
                  <th className="px-4 py-3">Destinatario</th>
                  <th className="px-4 py-3">Mensajería</th>
                  <th className="px-4 py-3">Descripción</th>
                  <th className="px-4 py-3">Recepción</th>
                  <th className="px-4 py-3">Foto</th>
                  <th className="px-4 py-3">Estado</th>
                  <th className="px-4 py-3 text-right">Acción</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {paquetesFiltrados.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="p-0">
                      <EmptyState
                        icon={Inbox}
                        title={search || filtroTamano !== 'TODOS' ? 'Ningún paquete coincide con los filtros' : activeTab === 'pendientes' ? 'No hay paquetes en custodia' : 'Aún no hay entregas registradas'}
                        subtitle={
                          search || filtroTamano !== 'TODOS'
                            ? 'Cambie la búsqueda o el tamaño.'
                            : activeTab === 'pendientes'
                            ? 'Los paquetes que reciba aparecerán aquí hasta su entrega.'
                            : 'Las entregas confirmadas con PIN aparecerán aquí.'
                        }
                        className="py-10"
                      >
                        {activeTab === 'pendientes' && !search && filtroTamano === 'TODOS' && (
                          <button type="button" onClick={() => setActiveTab('recibir')} className={'inline-flex min-h-11 items-center justify-center gap-2 rounded-md border border-input/60 bg-surface px-3.5 text-sm font-semibold text-on-surface shadow-xs transition-colors duration-150 hover:border-primary/70 hover:bg-surface-container sm:min-h-10 mt-4'}>
                            <PlusCircle className="h-4 w-4" aria-hidden="true" />
                            Registrar recepción
                          </button>
                        )}
                      </EmptyState>
                    </td>
                  </tr>
                ) : (
                  paquetesFiltrados.map((p) => {
                    const id = p.idPaquete || p.idMensaje;
                    const isEntregado = p.entregado || p.estado === 'ENTREGADO';
                    return (
                      <tr key={id} className="transition-colors hover:bg-surface-container">
                        <td className="px-4 py-3 font-semibold text-foreground whitespace-nowrap">
                          {formatApto(p.numeroApartamento)}
                        </td>
                        <td className="px-4 py-3 text-foreground whitespace-nowrap">
                          {p.nombreResidente || p.nombreDestinatario || 'Residente'}
                        </td>
                        <td className="px-4 py-3 text-foreground whitespace-nowrap">
                          <span className="font-medium text-on-surface">
                            {p.empresaMensajeria || 'Mensajería'}
                          </span>
                          {p.numeroGuia && (
                            <span className="block text-xs tabular-nums text-muted-foreground">
                              {p.numeroGuia}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-foreground max-w-xs truncate">
                          <span className="font-medium">{p.descripcion || p.titulo || 'Sin descripción'}</span>
                          {p.tamano && (
                            <Badge variant="secondary" className="ml-2">
                              {p.tamano}
                            </Badge>
                          )}
                        </td>
                        <td className="px-4 py-3 text-xs whitespace-nowrap">
                          <div className="font-semibold text-foreground">{formatDate(p.fechaRecepcion || p.fechaCreacion)}</div>
                          <div className="text-xs text-muted-foreground flex items-center gap-1 tabular-nums mt-0.5">
                            <Clock className="h-3 w-3 shrink-0 text-text-muted" aria-hidden="true" />
                            {formatTime(p.fechaRecepcion || p.fechaCreacion)}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          {p.fotoPaqueteUrl || p.fotoCaptura ? (
                            <img
                              src={imageSrc(p.fotoPaqueteUrl || p.fotoCaptura)}
                              alt="Foto del paquete"
                              onClick={() => setDetalleModal(p)}
                              className="h-10 w-10 cursor-pointer rounded-md border border-border object-cover transition-opacity hover:opacity-80"
                            />
                          ) : (
                            <span className="text-xs text-muted-foreground italic">Sin foto</span>
                          )}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          <Badge variant={isEntregado ? 'success' : 'warning'}>
                            {isEntregado ? 'Entregado' : 'En custodia'}
                          </Badge>
                        </td>
                        <td className="px-4 py-3 text-right whitespace-nowrap">
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              type="button"
                              onClick={() => setDetalleModal(p)}
                              className="inline-flex h-9 items-center gap-1 rounded-md border border-input/60 bg-surface px-2.5 text-xs font-semibold text-on-surface transition-colors hover:border-primary/70 hover:bg-surface-container"
                              title="Ver información completa y foto de evidencia"
                            >
                              <Eye className="h-3.5 w-3.5 text-text-secondary" aria-hidden="true" />
                              Detalle
                            </button>
                            {!isEntregado && (
                              <button
                                type="button"
                                onClick={() => {
                                  setModalEntrega(p);
                                  setPinIngresado('');
                                }}
                                className="inline-flex h-9 items-center gap-1.5 rounded-md bg-btn-primary px-3 text-xs font-semibold text-white shadow-sm transition-colors hover:bg-btn-primary-hover"
                              >
                                <CheckCircle2 className="w-3.5 h-3.5" />
                                Entregar
                              </button>
                            )}
                          </div>
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
              <EmptyState
                icon={Inbox}
                title={activeTab === 'pendientes' ? 'No hay paquetes en custodia' : 'Aún no hay entregas registradas'}
                subtitle={search || filtroTamano !== 'TODOS' ? 'Cambie la búsqueda o el tamaño.' : undefined}
                className="rounded-lg border border-border bg-surface py-8"
              />
            ) : (
              paquetesFiltrados.map((p) => {
                const id = p.idPaquete || p.idMensaje;
                const isEntregado = p.entregado || p.estado === 'ENTREGADO';
                return (
                  <div
                    key={id}
                    className="space-y-3 rounded-lg border border-border bg-surface p-4 shadow-sm"
                  >
                    <div className="flex items-center justify-between border-b border-border/60 pb-2">
                      <div className="flex items-center gap-2">
                        <span className="text-base font-bold text-foreground">
                          {formatApto(p.numeroApartamento)}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          • {p.nombreResidente || p.nombreDestinatario || 'Residente'}
                        </span>
                      </div>
                      <Badge variant={isEntregado ? 'success' : 'warning'}>{isEntregado ? 'Entregado' : 'En custodia'}</Badge>
                    </div>

                    <div className="grid grid-cols-2 gap-2 text-xs">
                      <div>
                        <span className="text-muted-foreground block">Mensajería:</span>
                        <span className="font-semibold text-foreground">
                          {p.empresaMensajeria || 'Mensajería'}
                        </span>
                        {p.numeroGuia && (
                          <span className="tabular-nums text-muted-foreground block text-xs">
                            {p.numeroGuia}
                          </span>
                        )}
                      </div>
                      <div>
                        <span className="text-muted-foreground block text-xs">Recepción:</span>
                        <span className="text-foreground font-medium flex items-center gap-1 text-xs">
                          <Clock className="w-3 h-3 text-primary-text shrink-0" />
                          {formatDateTime(p.fechaRecepcion || p.fechaCreacion)}
                        </span>
                      </div>
                    </div>

                    <div className="text-xs bg-muted/40 p-2.5 rounded-lg text-foreground">
                      <span className="font-medium">{p.descripcion || p.titulo || 'Sin descripción'}</span>
                      {p.tamano && (
                        <span className="ml-2 px-1.5 py-0.5 text-xs font-semibold bg-card border border-border rounded text-muted-foreground">
                          {p.tamano}
                        </span>
                      )}
                    </div>

                    <div className="flex items-center justify-between pt-1">
                      <button
                        type="button"
                        onClick={() => setDetalleModal(p)}
                        className="text-xs text-primary-text font-semibold flex items-center gap-1 min-h-[44px]"
                      >
                        {p.fotoPaqueteUrl || p.fotoCaptura ? (
                          <>
                            <Camera className="w-3.5 h-3.5" />
                            Ver foto
                          </>
                        ) : (
                          <>
                            <Eye className="w-3.5 h-3.5 text-muted-foreground" />
                            <span className="text-muted-foreground">Detalle</span>
                          </>
                        )}
                      </button>

                      {!isEntregado ? (
                        <button
                          type="button"
                          onClick={() => {
                            setModalEntrega(p);
                            setPinIngresado('');
                          }}
                          className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-lg bg-btn-primary hover:bg-btn-primary-hover text-white shadow-sm min-h-[44px]"
                        >
                          <CheckCircle2 className="w-4 h-4" />
                          Entregar paquete
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setDetalleModal(p)}
                          className="px-3 py-1.5 text-xs font-semibold rounded-lg border border-border bg-card text-foreground min-h-[44px]"
                        >
                          Ver detalle
                        </button>
                      )}
                    </div>
                  </div>
                );
              })
            )}
          </div>
          </>
          )}
        </div>
      )}

      {/* MODAL 1: Confirmación de Recepción con PIN */}
      <Modal
        open={!!modalSuccessPin}
        onClose={() => setModalSuccessPin(null)}
        title="Encomienda registrada"
      >
        {modalSuccessPin && (
          <div className="space-y-5 text-center p-2">
            <div className="w-14 h-14 rounded-full bg-tint-green-bg text-tint-green-fg flex items-center justify-center mx-auto border border-tint-green-fg/25">
              <ShieldCheck className="w-7 h-7" />
            </div>

            <div className="space-y-1">
              <h3 className="text-lg font-bold text-foreground">
                Paquete en custodia de la garita
              </h3>
              <p className="text-xs text-muted-foreground">
                Se ha generado el código PIN confidencial y se notificó a los residentes de la unidad.
              </p>
            </div>

            {/* Tarjeta de Seguridad Zero-Knowledge: El PIN no se expone al portero */}
            <div className="space-y-2 rounded-md border border-tint-blue-fg/25 bg-tint-blue-bg p-4 text-left">
              <div className="flex items-center gap-2 text-primary-text font-semibold text-xs">
                <Lock className="w-4 h-4 text-primary-text shrink-0" />
                <span>Código de Retiro Protegido</span>
              </div>
              <p className="text-xs text-muted-foreground leading-relaxed">
                Por seguridad de la unidad, el código PIN de 4 dígitos <strong>no es visible en portería</strong>. Se generó de forma confidencial y se envió a las notificaciones y buzón de los residentes del apartamento.
              </p>
              <div className="p-2.5 rounded-lg bg-background/80 border border-border/60 text-xs text-muted-foreground flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-primary-text shrink-0" />
                <span>El residente o conviviente autorizado deberá suministrar este PIN al retirar su paquete para que puedas confirmar la entrega.</span>
              </div>
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
                <div className="flex justify-between tabular-nums">
                  <span className="text-muted-foreground">Guía:</span>
                  <span className="text-foreground">{modalSuccessPin.paquete.numeroGuia}</span>
                </div>
              )}
            </div>

            <button
              type="button"
              onClick={() => setModalSuccessPin(null)}
              className="w-full py-2.5 rounded-xl bg-btn-primary hover:bg-btn-primary-hover text-white font-semibold text-sm transition-colors min-h-[44px]"
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
        title="Registrar entrega de paquete"
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
              {(modalEntrega.fotoPaqueteUrl || modalEntrega.fotoCaptura) && (
                <div className="pt-2 flex items-center justify-between border-t border-border/40">
                  <span className="text-muted-foreground">Evidencia Fotográfica:</span>
                  <button
                    type="button"
                    onClick={() => setDetalleModal(modalEntrega)}
                    className="flex items-center gap-1.5 text-xs text-primary-text hover:underline font-semibold"
                  >
                    <img
                      src={imageSrc(modalEntrega.fotoPaqueteUrl || modalEntrega.fotoCaptura)}
                      alt="Miniatura paquete"
                      className="w-10 h-10 rounded-lg object-cover border border-border"
                    />
                    <span>Ver ampliada</span>
                  </button>
                </div>
              )}
            </div>

            <div className="space-y-2">
              <label htmlFor="paq-pin" className="block text-sm font-semibold text-on-surface">
                PIN de retiro que presenta el residente <span className="text-destructive" aria-hidden="true">*</span>
              </label>
              <div className="relative">
                <KeyRound className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
                <input
                  id="paq-pin"
                  type="text"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  aria-describedby="paq-pin-hint"
                  maxLength={4}
                  placeholder="Ingrese el PIN de 4 dígitos"
                  value={pinIngresado}
                  onChange={(e) => setPinIngresado(e.target.value.replace(/\D/g, '').slice(0, 4))}
                  className="h-12 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-center text-xl font-bold tracking-[0.4em] text-on-surface tabular-nums placeholder:text-sm placeholder:font-normal placeholder:tracking-normal placeholder:text-text-hint focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25"
                />
              </div>
              <p id="paq-pin-hint" className="text-xs text-muted-foreground">
                El residente debe suministrar el código PIN de 4 dígitos recibido en su notificación para autorizar la entrega.
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
                className="inline-flex items-center gap-2 px-5 py-2 text-xs font-semibold rounded-lg bg-btn-primary hover:bg-btn-primary-hover text-white transition-colors shadow-sm disabled:opacity-50 min-h-[44px]"
              >
                {entregando ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                    Confirmando entrega...
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
        title="Detalle de la encomienda"
      >
        {detalleModal && (
          <div className="space-y-4 p-1">
            {detalleModal.fotoPaqueteUrl || detalleModal.fotoCaptura ? (
              <div className="aspect-video w-full rounded-xl overflow-hidden border border-border bg-surface-dim">
                <img
                  src={imageSrc(detalleModal.fotoPaqueteUrl || detalleModal.fotoCaptura)}
                  alt="Foto del paquete"
                  className="w-full h-full object-contain"
                />
              </div>
            ) : (
              <div className="p-4 rounded-xl border border-dashed border-border bg-muted/20 text-center text-xs text-muted-foreground">
                <Camera className="mx-auto mb-1 h-5 w-5 text-text-muted" aria-hidden="true" />
                <span>Sin foto de evidencia</span>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg bg-muted/40 border border-border/60">
                <span className="text-muted-foreground block">Apartamento:</span>
                <span className="font-bold text-foreground text-sm">
                  {formatApto(detalleModal.numeroApartamento)}
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
                <span className="tabular-nums text-foreground">{detalleModal.numeroGuia || 'N/A'}</span>
              </div>
            </div>

            <div className="p-3 rounded-lg bg-muted/40 border border-border/60 text-xs space-y-1">
              <span className="text-muted-foreground block">Descripción:</span>
              <p className="text-foreground">{detalleModal.descripcion || detalleModal.titulo}</p>
            </div>

            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 text-xs text-muted-foreground pt-2 border-t border-border/60">
              <span className="flex items-center gap-1.5 font-medium text-foreground">
                <Clock className="w-3.5 h-3.5 text-primary-text shrink-0" />
                Recibido: {formatDateTime(detalleModal.fechaRecepcion || detalleModal.fechaCreacion)}
              </span>
              {detalleModal.fechaEntrega && (
                <span className="flex items-center gap-1.5 text-primary-text font-medium">
                  <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
                  Entregado: {formatDateTime(detalleModal.fechaEntrega)}
                </span>
              )}
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
