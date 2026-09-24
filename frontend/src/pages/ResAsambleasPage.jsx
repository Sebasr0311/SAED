import React, { useState, useMemo, useCallback } from 'react';
import {
  Calendar,
  Users,
  Vote,
  FileText,
  CheckCircle2,
  Clock,
  Plus,
  Video,
  Building,
  ExternalLink,
  ShieldCheck,
  Percent,
  RefreshCw,
  Award,
  AlertCircle,
  FileCheck,
  UserCheck,
  ChevronRight,
  Download,
  Lock,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { Modal } from '../components/ui/Modal';
import { useFetch } from '../lib/hooks';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { BASE_URL } from '../lib/api.js';
import { TOKEN_KEY } from '../lib/storage.js';
import { toast } from 'sonner';

const ESTADOS_ASAMBLEA = {
  BORRADOR: { label: 'Borrador', color: 'bg-neutral-500/10 text-neutral-400 border border-neutral-500/20' },
  CONVOCADA: { label: 'Convocada', color: 'bg-blue-500/15 text-blue-700 dark:text-blue-400 border border-blue-500/30' },
  EN_CURSO: { label: 'En Curso', color: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30' },
  EN_RECESO: { label: 'En Receso', color: 'bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30' },
  FINALIZADA: { label: 'Finalizada', color: 'bg-gray-500/15 text-gray-700 dark:text-gray-400 border border-gray-500/20' },
  CANCELADA: { label: 'Cancelada', color: 'bg-red-500/15 text-red-700 dark:text-red-400 border border-red-500/20' },
};

const MODALIDADES = {
  PRESENCIAL: { label: 'Presencial', icon: Building },
  VIRTUAL: { label: 'Virtual', icon: Video },
  MIXTA: { label: 'Mixta', icon: Users },
};

export default function ResAsambleasPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();
  const [tabActiva, setTabActiva] = useState('proximas');
  const [asambleaSeleccionadaId, setAsambleaSeleccionadaId] = useState(null);
  const [modalPoderOpen, setModalPoderOpen] = useState(false);
  const [submittingPoder, setSubmittingPoder] = useState(false);
  const [votingId, setVotingId] = useState(null);
  const [votoExitosoMap, setVotoExitosoMap] = useState({});

  // Formulario radicación de poder
  const [formPoder, setFormPoder] = useState({
    idUnidad: '',
    idPersonaPropietario: '',
    idPersonaApoderado: '',
    documentoPoderUrl: '',
  });

  // Fetch asambleas
  const {
    data: asambleasRaw,
    loading: loadingAsambleas,
    refetch: refetchAsambleas,
  } = useFetch(
    useCallback(() => tenantApi.get('/asambleas'), [tenant.activeAssignmentId])
  );

  const asambleas = useMemo(() => {
    if (!asambleasRaw) return [];
    if (Array.isArray(asambleasRaw)) return asambleasRaw;
    if (Array.isArray(asambleasRaw.items)) return asambleasRaw.items;
    return [];
  }, [asambleasRaw]);

  // Selección inicial
  React.useEffect(() => {
    if (asambleas.length > 0 && !asambleaSeleccionadaId) {
      // Priorizar EN_CURSO o CONVOCADA
      const activa = asambleas.find((a) => a.estado === 'EN_CURSO' || a.estado === 'CONVOCADA');
      setAsambleaSeleccionadaId(activa ? activa.idAsamblea : asambleas[0].idAsamblea);
    }
  }, [asambleas, asambleaSeleccionadaId]);

  const asambleaActiva = useMemo(
    () => asambleas.find((a) => a.idAsamblea === asambleaSeleccionadaId) || null,
    [asambleas, asambleaSeleccionadaId]
  );

  // Fetch Quórum en vivo para la asamblea seleccionada
  const {
    data: quorumLive,
    loading: loadingQuorum,
    refetch: refetchQuorum,
  } = useFetch(
    useCallback(() => {
      if (!asambleaSeleccionadaId) return Promise.resolve(null);
      return tenantApi.get(`/asambleas/${asambleaSeleccionadaId}/quorum`);
    }, [asambleaSeleccionadaId, tenant.activeAssignmentId])
  );

  // Fetch Poderes de la asamblea
  const {
    data: poderesRaw,
    loading: loadingPoderes,
    refetch: refetchPoderes,
  } = useFetch(
    useCallback(() => {
      if (!asambleaSeleccionadaId) return Promise.resolve([]);
      return tenantApi.get(`/asambleas/${asambleaSeleccionadaId}/poderes`);
    }, [asambleaSeleccionadaId, tenant.activeAssignmentId])
  );

  const poderes = useMemo(() => {
    if (!poderesRaw) return [];
    if (Array.isArray(poderesRaw)) return poderesRaw;
    if (Array.isArray(poderesRaw.items)) return poderesRaw.items;
    return [];
  }, [poderesRaw]);

  // Fetch Votaciones de la asamblea seleccionada
  const {
    data: votacionesRaw,
    loading: loadingVotaciones,
    refetch: refetchVotaciones,
  } = useFetch(
    useCallback(() => {
      if (!asambleaSeleccionadaId) return Promise.resolve([]);
      return tenantApi.get(`/asambleas/${asambleaSeleccionadaId}/votaciones`);
    }, [asambleaSeleccionadaId, tenant.activeAssignmentId])
  );

  const votaciones = useMemo(() => {
    if (!votacionesRaw) return [];
    if (Array.isArray(votacionesRaw)) return votacionesRaw;
    if (Array.isArray(votacionesRaw.items)) return votacionesRaw.items;
    return [];
  }, [votacionesRaw]);

  // Fetch Acta Oficial si está disponible/publicada
  const {
    data: actaOficial,
    loading: loadingActa,
    refetch: refetchActa,
  } = useFetch(
    useCallback(() => {
      if (!asambleaSeleccionadaId) return Promise.resolve(null);
      return tenantApi.get(`/actas/asamblea/${asambleaSeleccionadaId}`).catch(() => null);
    }, [asambleaSeleccionadaId, tenant.activeAssignmentId])
  );

  const handleDescargarDocumentoActa = async (acta) => {
    if (!acta?.idActa) return;
    try {
      const token = sessionStorage.getItem(TOKEN_KEY) || sessionStorage.getItem('token');
      const assignmentId = tenant.activeAssignmentId;
      const res = await fetch(`${BASE_URL}/actas/${acta.idActa}/documento`, {
        headers: {
          Authorization: `Bearer ${token}`,
          ...(assignmentId ? { 'X-Assignment-Id': String(assignmentId) } : {}),
        },
      });
      if (!res.ok) throw new Error('No se pudo descargar el documento firmado del acta');
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Acta_Oficial_${acta.numeroActa || acta.idActa}.pdf`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      toast.error(err.message || 'Error al descargar el acta');
    }
  };

  // Manejar radicación de poder
  const handleRadicarPoder = async (e) => {
    e.preventDefault();
    if (!asambleaSeleccionadaId) return;

    if (!formPoder.idUnidad || !formPoder.idPersonaPropietario || !formPoder.idPersonaApoderado) {
      toast.error('Complete los datos obligatorios de la unidad y los intervinientes.');
      return;
    }

    if (String(formPoder.idPersonaPropietario) === String(formPoder.idPersonaApoderado)) {
      toast.error('El propietario no puede otorgarse poder a sí mismo.');
      return;
    }

    setSubmittingPoder(true);
    try {
      await tenantApi.post(`/asambleas/${asambleaSeleccionadaId}/poderes`, {
        idUnidad: Number(formPoder.idUnidad),
        idPersonaPropietario: Number(formPoder.idPersonaPropietario),
        idPersonaApoderado: Number(formPoder.idPersonaApoderado),
        documentoPoderUrl: formPoder.documentoPoderUrl || null,
      });

      toast.success('Poder de representación radicado exitosamente. Pendiente de verificación por la mesa.');
      setModalPoderOpen(false);
      setFormPoder({
        idUnidad: '',
        idPersonaPropietario: '',
        idPersonaApoderado: '',
        documentoPoderUrl: '',
      });
      refetchPoderes();
    } catch (err) {
      const msg = err.response?.data?.message || 'Error al radicar el poder.';
      toast.error(msg);
    } finally {
      setSubmittingPoder(false);
    }
  };

  // Emitir voto interactivo como residente / apoderado
  const handleVotar = async (idVotacion, opcion) => {
    if (!asambleaSeleccionadaId) return;
    const unitId = tenant.activeAssignment?.idUnidad;
    if (!unitId) {
      toast.error('No tiene una unidad asignada activa para emitir su voto.');
      return;
    }

    setVotingId(idVotacion);
    try {
      await tenantApi.post(`/asambleas/${asambleaSeleccionadaId}/votaciones/${idVotacion}/votar`, {
        idUnidad: Number(unitId),
        opcionVoto: opcion,
      });
      toast.success(`Voto '${opcion}' registrado exitosamente para su unidad (ID ${unitId})`);
      setVotoExitosoMap((prev) => ({ ...prev, [idVotacion]: opcion }));
      refetchVotaciones();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Error al emitir el voto';
      toast.error(msg);
    } finally {
      setVotingId(null);
    }
  };

  const formatearFecha = (str) => {
    if (!str) return 'No especificada';
    try {
      const d = new Date(str);
      return d.toLocaleDateString('es-CO', {
        weekday: 'short',
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return str;
    }
  };

  const asambleasProximas = useMemo(
    () => asambleas.filter((a) => a.estado !== 'FINALIZADA' && a.estado !== 'CANCELADA'),
    [asambleas]
  );

  const asambleasHistorial = useMemo(
    () => asambleas.filter((a) => a.estado === 'FINALIZADA' || a.estado === 'CANCELADA'),
    [asambleas]
  );

  return (
    <div className="space-y-6 pb-12">
      <PageHeader
        title="Asambleas y Gobernanza"
        subtitle="Consulte convocatorias oficiales, siga el quórum en tiempo real y radique poderes de representación para su copropiedad."
      >
        <button
          onClick={() => {
            refetchAsambleas();
            refetchQuorum();
            refetchPoderes();
            refetchVotaciones();
            toast.info('Datos de asambleas actualizados.');
          }}
          className="inline-flex items-center gap-2 px-3 py-1.5 text-sm font-medium rounded-lg border border-neutral-200 dark:border-neutral-800 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition-colors"
        >
          <RefreshCw className="w-4 h-4 text-neutral-500" />
          Actualizar
        </button>
      </PageHeader>

      {/* Tabs */}
      <div className="flex border-b border-neutral-200 dark:border-neutral-800">
        <button
          onClick={() => setTabActiva('proximas')}
          className={`pb-3 px-4 text-sm font-medium transition-colors border-b-2 ${
            tabActiva === 'proximas'
              ? 'border-primary text-primary'
              : 'border-transparent text-neutral-500 hover:text-neutral-700 dark:hover:text-neutral-300'
          }`}
        >
          Convocatorias Activas ({asambleasProximas.length})
        </button>
        <button
          onClick={() => setTabActiva('historial')}
          className={`pb-3 px-4 text-sm font-medium transition-colors border-b-2 ${
            tabActiva === 'historial'
              ? 'border-primary text-primary'
              : 'border-transparent text-neutral-500 hover:text-neutral-700 dark:hover:text-neutral-300'
          }`}
        >
          Historial y Actas ({asambleasHistorial.length})
        </button>
      </div>

      {loadingAsambleas ? (
        <div className="p-8 text-center text-neutral-500 flex flex-col items-center justify-center gap-2">
          <RefreshCw className="w-6 h-6 animate-spin text-primary" />
          <p>Cargando información de asambleas...</p>
        </div>
      ) : asambleas.length === 0 ? (
        <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-xl p-8 text-center">
          <Calendar className="w-12 h-12 text-neutral-400 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-neutral-800 dark:text-neutral-100">
            No hay asambleas registradas
          </h3>
          <p className="text-sm text-neutral-500 mt-1 max-w-md mx-auto">
            La administración de su copropiedad no ha convocado asambleas ordinarias ni extraordinarias en este momento.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Lista de asambleas en columna izquierda */}
          <div className="lg:col-span-1 space-y-3">
            <h3 className="text-sm font-semibold uppercase tracking-wider text-neutral-500 px-1">
              {tabActiva === 'proximas' ? 'Asambleas Activas' : 'Historial de Asambleas'}
            </h3>

            {(tabActiva === 'proximas' ? asambleasProximas : asambleasHistorial).map((item) => {
              const esSeleccionada = item.idAsamblea === asambleaSeleccionadaId;
              const estadoConfig = ESTADOS_ASAMBLEA[item.estado] || ESTADOS_ASAMBLEA.CONVOCADA;
              const ModIcon = MODALIDADES[item.modalidad]?.icon || Building;

              return (
                <div
                  key={item.idAsamblea}
                  onClick={() => setAsambleaSeleccionadaId(item.idAsamblea)}
                  className={`p-4 rounded-xl border cursor-pointer transition-all ${
                    esSeleccionada
                      ? 'border-primary bg-primary/5 shadow-sm'
                      : 'border-neutral-200 dark:border-neutral-800 hover:border-neutral-300 dark:hover:border-neutral-700 bg-white dark:bg-neutral-900'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${estadoConfig.color}`}
                    >
                      {estadoConfig.label}
                    </span>
                    <span className="text-xs text-neutral-400">
                      Conv. #{item.convocatoriaNumero || 1}
                    </span>
                  </div>

                  <h4 className="font-semibold text-sm text-neutral-900 dark:text-neutral-100 line-clamp-2">
                    {item.titulo}
                  </h4>

                  <div className="mt-3 space-y-1.5 text-xs text-neutral-500">
                    <div className="flex items-center gap-1.5">
                      <Calendar className="w-3.5 h-3.5" />
                      <span>{formatearFecha(item.fechaHoraPrimeraConv)}</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <ModIcon className="w-3.5 h-3.5" />
                      <span>{item.modalidad}</span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Panel de Detalle y Quórum en columna derecha */}
          <div className="lg:col-span-2 space-y-6">
            {asambleaActiva ? (
              <>
                {/* Tarjeta Principal de Detalle */}
                <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-xl p-6 shadow-sm">
                  <div className="flex flex-wrap items-center justify-between gap-4 border-b border-neutral-100 dark:border-neutral-800 pb-4 mb-4">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span
                          className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                            ESTADOS_ASAMBLEA[asambleaActiva.estado]?.color || ''
                          }`}
                        >
                          {ESTADOS_ASAMBLEA[asambleaActiva.estado]?.label || asambleaActiva.estado}
                        </span>
                        <span className="text-xs text-neutral-400">
                          {asambleaActiva.tipo} • Convocatoria #{asambleaActiva.convocatoriaNumero || 1}
                        </span>
                      </div>
                      <h2 className="text-xl font-bold text-neutral-900 dark:text-neutral-100">
                        {asambleaActiva.titulo}
                      </h2>
                    </div>

                    {/* Botón Radicar Poder si no está finalizada */}
                    {asambleaActiva.estado !== 'FINALIZADA' && asambleaActiva.estado !== 'CANCELADA' && (
                      <button
                        onClick={() => setModalPoderOpen(true)}
                        className="inline-flex items-center gap-2 px-3.5 py-2 text-sm font-medium rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors shadow-sm"
                      >
                        <ShieldCheck className="w-4 h-4" />
                        Radicar Poder
                      </button>
                    )}
                  </div>

                  {/* Grid de Metadatos Clave */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm mb-6">
                    <div className="p-3 bg-neutral-50 dark:bg-neutral-800/50 rounded-lg">
                      <span className="text-xs font-medium text-neutral-500 uppercase tracking-wider block mb-1">
                        Primera Convocatoria
                      </span>
                      <p className="font-semibold text-neutral-800 dark:text-neutral-200">
                        {formatearFecha(asambleaActiva.fechaHoraPrimeraConv)}
                      </p>
                    </div>

                    <div className="p-3 bg-neutral-50 dark:bg-neutral-800/50 rounded-lg">
                      <span className="text-xs font-medium text-neutral-500 uppercase tracking-wider block mb-1">
                        Segunda Convocatoria (Ley 675)
                      </span>
                      <p className="font-semibold text-neutral-800 dark:text-neutral-200">
                        {asambleaActiva.fechaHoraSegundaConv
                          ? formatearFecha(asambleaActiva.fechaHoraSegundaConv)
                          : 'No requerida'}
                      </p>
                    </div>

                    <div className="p-3 bg-neutral-50 dark:bg-neutral-800/50 rounded-lg">
                      <span className="text-xs font-medium text-neutral-500 uppercase tracking-wider block mb-1">
                        Modalidad y Lugar / Enlace
                      </span>
                      <p className="font-semibold text-neutral-800 dark:text-neutral-200 break-words">
                        {asambleaActiva.modalidad}: {asambleaActiva.lugarOEnlace}
                      </p>
                    </div>

                    <div className="p-3 bg-neutral-50 dark:bg-neutral-800/50 rounded-lg">
                      <span className="text-xs font-medium text-neutral-500 uppercase tracking-wider block mb-1">
                        Quórum Legal Requerido
                      </span>
                      <p className="font-semibold text-neutral-800 dark:text-neutral-200">
                        {asambleaActiva.quorumRequeridoPct || 50.01}% de coeficiente de copropiedad
                      </p>
                    </div>
                  </div>

                  {/* Documento Convocatoria Oficial (F10-01 integration) */}
                  {asambleaActiva.idDocumento && (
                    <div className="mb-6 p-4 rounded-xl border border-blue-500/20 bg-blue-500/5 flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <FileText className="w-5 h-5 text-blue-600" />
                        <div>
                          <p className="text-sm font-semibold text-blue-900 dark:text-blue-300">
                            Convocatoria Oficial Adjunta
                          </p>
                          <p className="text-xs text-neutral-500">
                            Documento oficial F10-01 con anexos y estados financieros
                          </p>
                        </div>
                      </div>
                      <a
                        href={`/api/v1/documentos/${asambleaActiva.idDocumento}/descargar`}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-blue-600 text-blue-600 hover:bg-blue-600 hover:text-white transition-colors"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                        Descargar PDF
                      </a>
                    </div>
                  )}

                  {/* Acta Oficial Publicada (F10-05 integration - Art. 47 Ley 675) */}
                  {actaOficial && actaOficial.estado === 'PUBLICADA_OFICIAL' && (
                    <div className="mb-6 p-5 rounded-xl border border-emerald-500/30 bg-emerald-500/5 space-y-4">
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-emerald-500/20 pb-3">
                        <div className="flex items-center gap-3">
                          <div className="p-2.5 rounded-lg bg-emerald-500/10 text-emerald-600">
                            <FileCheck className="w-6 h-6" />
                          </div>
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-base text-emerald-950 dark:text-emerald-200">
                                Acta Oficial Nº {actaOficial.numeroActa}
                              </span>
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/20 text-emerald-700 dark:text-emerald-300">
                                <Lock className="w-3 h-3" /> Publicada Oficialmente
                              </span>
                            </div>
                            <p className="text-xs text-neutral-500">
                              Aprobada por comisión verificadora • Conforme a Ley 675 de 2001
                            </p>
                          </div>
                        </div>

                        {(actaOficial.idDocumento || actaOficial.documentoFirmadoUrl) && (
                          <button
                            onClick={() => handleDescargarDocumentoActa(actaOficial)}
                            className="inline-flex items-center justify-center gap-1.5 px-3.5 py-2 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors shadow-sm"
                          >
                            <Download className="w-4 h-4" />
                            Descargar Acta Oficial (PDF)
                          </button>
                        )}
                      </div>

                      {/* Resumen de Gobernanza del Acta */}
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                        <div className="p-2.5 bg-white/70 dark:bg-neutral-800/70 rounded-lg border border-emerald-500/10">
                          <span className="text-neutral-500 block">Asistentes</span>
                          <span className="font-bold text-neutral-800 dark:text-neutral-200">
                            {actaOficial.totalAsistentes || 0} personas
                          </span>
                        </div>
                        <div className="p-2.5 bg-white/70 dark:bg-neutral-800/70 rounded-lg border border-emerald-500/10">
                          <span className="text-neutral-500 block">Coeficiente Total</span>
                          <span className="font-bold text-neutral-800 dark:text-neutral-200">
                            {Number(actaOficial.totalCoeficienteAsistentes || 0).toFixed(4)}
                          </span>
                        </div>
                        <div className="p-2.5 bg-white/70 dark:bg-neutral-800/70 rounded-lg border border-emerald-500/10">
                          <span className="text-neutral-500 block">Poderes Validados</span>
                          <span className="font-bold text-neutral-800 dark:text-neutral-200">
                            {actaOficial.totalPoderesAprobados || 0}
                          </span>
                        </div>
                        <div className="p-2.5 bg-white/70 dark:bg-neutral-800/70 rounded-lg border border-emerald-500/10">
                          <span className="text-neutral-500 block">Votaciones</span>
                          <span className="font-bold text-neutral-800 dark:text-neutral-200">
                            {actaOficial.totalVotaciones || 0} puntos
                          </span>
                        </div>
                      </div>

                      {/* Texto del Acta */}
                      {actaOficial.contenidoTexto && (
                        <details className="text-xs group">
                          <summary className="cursor-pointer font-semibold text-emerald-800 dark:text-emerald-300 hover:underline flex items-center gap-1">
                            Ver texto íntegro del acta
                          </summary>
                          <div className="mt-2 p-3 bg-white/80 dark:bg-neutral-800/80 rounded-lg border border-neutral-200 dark:border-neutral-700 font-mono text-[11px] whitespace-pre-wrap max-h-60 overflow-y-auto leading-relaxed text-neutral-700 dark:text-neutral-300">
                            {actaOficial.contenidoTexto}
                          </div>
                        </details>
                      )}
                    </div>
                  )}

                  {/* Aviso si la asamblea finalizó pero el acta aún está en trámite */}
                  {asambleaActiva.estado === 'FINALIZADA' && (!actaOficial || actaOficial.estado !== 'PUBLICADA_OFICIAL') && (
                    <div className="mb-6 p-4 rounded-xl border border-amber-500/20 bg-amber-500/5 flex items-start gap-3">
                      <Clock className="w-5 h-5 text-amber-600 mt-0.5 shrink-0" />
                      <div className="text-xs">
                        <p className="font-semibold text-amber-900 dark:text-amber-300">
                          Acta en Proceso de Redacción y Revisión
                        </p>
                        <p className="text-neutral-500 mt-0.5 leading-relaxed">
                          Conforme al Artículo 47 de la Ley 675 de 2001, la comisión verificadora dispone de un plazo de hasta veinte (20) días hábiles para revisar, firmar y publicar el acta oficial de esta asamblea. Una vez publicada, estará disponible para consulta y descarga aquí.
                        </p>
                      </div>
                    </div>
                  )}

                  {/* Quórum en Tiempo Real (Live Gauge) */}
                  <div className="mb-6 p-5 rounded-xl border border-neutral-200 dark:border-neutral-800 bg-neutral-50/50 dark:bg-neutral-800/30">
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <Percent className="w-5 h-5 text-primary" />
                        <h4 className="font-semibold text-sm text-neutral-900 dark:text-neutral-100">
                          Quórum Deliberatorio y Decisorio
                        </h4>
                      </div>
                      <span
                        className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                          quorumLive?.tieneQuorum
                            ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400'
                            : 'bg-amber-500/15 text-amber-700 dark:text-amber-400'
                        }`}
                      >
                        {quorumLive?.tieneQuorum ? (
                          <>
                            <CheckCircle2 className="w-3.5 h-3.5" /> Quórum Alcanzado
                          </>
                        ) : (
                          <>
                            <Clock className="w-3.5 h-3.5" /> En espera de quórum
                          </>
                        )}
                      </span>
                    </div>

                    {/* Progress Bar */}
                    <div className="space-y-2">
                      <div className="w-full bg-neutral-200 dark:bg-neutral-700 h-3 rounded-full overflow-hidden relative">
                        {/* Target line at quorumRequeridoPct */}
                        <div
                          className="absolute top-0 bottom-0 w-0.5 bg-neutral-900 dark:bg-neutral-100 z-10"
                          style={{
                            left: `${Math.min(asambleaActiva.quorumRequeridoPct || 50.01, 100)}%`,
                          }}
                          title={`Meta: ${asambleaActiva.quorumRequeridoPct || 50.01}%`}
                        />
                        <div
                          className={`h-full transition-all duration-500 rounded-full ${
                            quorumLive?.tieneQuorum ? 'bg-emerald-500' : 'bg-primary'
                          }`}
                          style={{
                            width: `${Math.min(quorumLive?.quorumAlcanzadoPct || 0, 100)}%`,
                          }}
                        />
                      </div>

                      <div className="flex items-center justify-between text-xs text-neutral-500">
                        <span>
                          Alcanzado:{' '}
                          <strong className="text-neutral-800 dark:text-neutral-200">
                            {Number(quorumLive?.quorumAlcanzadoPct || 0).toFixed(2)}%
                          </strong>
                        </span>
                        <span>
                          Requerido: {Number(asambleaActiva.quorumRequeridoPct || 50.01).toFixed(2)}%
                        </span>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4 mt-4 pt-4 border-t border-neutral-200 dark:border-neutral-700/50 text-xs">
                      <div>
                        <span className="text-neutral-500 block">Unidades Registradas</span>
                        <span className="font-bold text-sm text-neutral-800 dark:text-neutral-200">
                          {quorumLive?.totalUnidadesRegistradas || 0}
                        </span>
                      </div>
                      <div>
                        <span className="text-neutral-500 block">Coeficiente Total Presente</span>
                        <span className="font-bold text-sm text-neutral-800 dark:text-neutral-200">
                          {Number(quorumLive?.totalCoeficienteRegistrado || 0).toFixed(4)}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Orden del Día */}
                  <div>
                    <h4 className="font-semibold text-sm text-neutral-900 dark:text-neutral-100 mb-2">
                      Orden del Día Propuesto
                    </h4>
                    <div className="bg-neutral-50 dark:bg-neutral-800/40 p-4 rounded-xl border border-neutral-200 dark:border-neutral-800 text-sm whitespace-pre-wrap text-neutral-700 dark:text-neutral-300 font-sans leading-relaxed">
                      {asambleaActiva.ordenDelDia}
                    </div>
                  </div>
                </div>

                {/* Votaciones en Asamblea */}
                <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-xl p-6 shadow-sm">
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                      <Vote className="w-5 h-5 text-primary" />
                      <h3 className="font-bold text-base text-neutral-900 dark:text-neutral-100">
                        Votaciones y Decisiones
                      </h3>
                    </div>
                    <span className="text-xs text-neutral-400">
                      {votaciones.length} punto(s) de votación
                    </span>
                  </div>

                  {loadingVotaciones ? (
                    <div className="p-4 text-center text-xs text-neutral-400">Cargando votaciones...</div>
                  ) : votaciones.length === 0 ? (
                    <div className="text-center py-6 text-neutral-400 text-sm">
                      No hay puntos de votación activos o registrados para esta asamblea.
                    </div>
                  ) : (
                    <div className="space-y-4">
                      {votaciones.map((v) => (
                        <div
                          key={v.idVotacion}
                          className="p-4 rounded-xl border border-neutral-200 dark:border-neutral-800 space-y-3"
                        >
                          <div className="flex items-center justify-between gap-2">
                            <div className="flex items-center gap-2">
                              <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-neutral-100 dark:bg-neutral-800 text-neutral-700 dark:text-neutral-300">
                                Punto #{v.puntoOrdenDia}
                              </span>
                              <h5 className="font-semibold text-sm text-neutral-900 dark:text-neutral-100">
                                {v.titulo}
                              </h5>
                            </div>
                            <span
                              className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                                v.estado === 'ABIERTA'
                                  ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400'
                                  : 'bg-neutral-200 dark:bg-neutral-800 text-neutral-600 dark:text-neutral-400'
                              }`}
                            >
                              {v.estado}
                            </span>
                          </div>

                          {v.descripcion && (
                            <p className="text-xs text-neutral-600 dark:text-neutral-400">
                              {v.descripcion}
                            </p>
                          )}

                          {/* Emisión interactiva de voto si la votación está ABIERTA */}
                          {v.estado === 'ABIERTA' && (
                            <div className="p-3 bg-neutral-50 dark:bg-neutral-800/40 rounded-lg space-y-2 border border-neutral-200 dark:border-neutral-800">
                              <div className="flex items-center justify-between">
                                <span className="text-xs font-semibold text-neutral-700 dark:text-neutral-300">
                                  Emitir Voto (Unidad {tenant.activeAssignment?.idUnidad ? `#${tenant.activeAssignment.idUnidad}` : 'no asignada'}):
                                </span>
                                {votoExitosoMap[v.idVotacion] && (
                                  <span className="text-xs font-semibold text-emerald-600 flex items-center gap-1">
                                    <CheckCircle2 className="w-3.5 h-3.5" /> Voto registrado: {votoExitosoMap[v.idVotacion]}
                                  </span>
                                )}
                              </div>
                              <div className="grid grid-cols-4 gap-2 pt-1">
                                <button
                                  type="button"
                                  disabled={votingId === v.idVotacion || !!votoExitosoMap[v.idVotacion]}
                                  onClick={() => handleVotar(v.idVotacion, 'SI')}
                                  className="px-3 py-2 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white transition-colors disabled:opacity-50 flex items-center justify-center gap-1 shadow-sm"
                                >
                                  <CheckCircle2 className="w-3.5 h-3.5" /> SÍ
                                </button>
                                <button
                                  type="button"
                                  disabled={votingId === v.idVotacion || !!votoExitosoMap[v.idVotacion]}
                                  onClick={() => handleVotar(v.idVotacion, 'NO')}
                                  className="px-3 py-2 text-xs font-semibold rounded-lg bg-red-600 hover:bg-red-700 text-white transition-colors disabled:opacity-50 flex items-center justify-center gap-1 shadow-sm"
                                >
                                  NO
                                </button>
                                <button
                                  type="button"
                                  disabled={votingId === v.idVotacion || !!votoExitosoMap[v.idVotacion]}
                                  onClick={() => handleVotar(v.idVotacion, 'BLANCO')}
                                  className="px-3 py-2 text-xs font-semibold rounded-lg bg-neutral-200 dark:bg-neutral-700 hover:bg-neutral-300 dark:hover:bg-neutral-600 text-neutral-800 dark:text-neutral-200 transition-colors disabled:opacity-50 shadow-sm"
                                >
                                  BLANCO
                                </button>
                                <button
                                  type="button"
                                  disabled={votingId === v.idVotacion || !!votoExitosoMap[v.idVotacion]}
                                  onClick={() => handleVotar(v.idVotacion, 'ABSTENCION')}
                                  className="px-3 py-2 text-xs font-semibold rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-800 dark:text-amber-300 border border-amber-500/30 transition-colors disabled:opacity-50 shadow-sm"
                                >
                                  ABSTENCIÓN
                                </button>
                              </div>
                            </div>
                          )}

                          {/* Resultados si está cerrada */}
                          {v.estado === 'CERRADA' && (
                            <div className="p-3 bg-neutral-50 dark:bg-neutral-800/60 rounded-lg text-xs space-y-1">
                              <div className="flex justify-between font-medium">
                                <span>Resultado:</span>
                                <span
                                  className={v.aprobada ? 'text-emerald-600 font-bold' : 'text-red-600 font-bold'}
                                >
                                  {v.aprobada ? 'APROBADA' : 'NO APROBADA'}
                                </span>
                              </div>
                              <div className="grid grid-cols-4 gap-2 text-center pt-2">
                                <div className="bg-emerald-500/10 p-1.5 rounded">
                                  <span className="block text-neutral-400">SÍ</span>
                                  <span className="font-bold text-emerald-700 dark:text-emerald-400">
                                    {v.votosSi || 0}
                                  </span>
                                </div>
                                <div className="bg-red-500/10 p-1.5 rounded">
                                  <span className="block text-neutral-400">NO</span>
                                  <span className="font-bold text-red-700 dark:text-red-400">
                                    {v.votosNo || 0}
                                  </span>
                                </div>
                                <div className="bg-neutral-500/10 p-1.5 rounded">
                                  <span className="block text-neutral-400">BLANCO</span>
                                  <span className="font-bold">{v.votosBlanco || 0}</span>
                                </div>
                                <div className="bg-neutral-500/10 p-1.5 rounded">
                                  <span className="block text-neutral-400">ABSTENCIÓN</span>
                                  <span className="font-bold">{v.votosAbstencion || 0}</span>
                                </div>
                              </div>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Poderes Radicados para esta Asamblea */}
                <div className="bg-white dark:bg-neutral-900 border border-neutral-200 dark:border-neutral-800 rounded-xl p-6 shadow-sm">
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                      <ShieldCheck className="w-5 h-5 text-primary" />
                      <h3 className="font-bold text-base text-neutral-900 dark:text-neutral-100">
                        Poderes de Representación Registrados
                      </h3>
                    </div>
                    <span className="text-xs text-neutral-400">
                      {poderes.length} poder(es) registrado(s)
                    </span>
                  </div>

                  {loadingPoderes ? (
                    <div className="p-4 text-center text-xs text-neutral-400">Cargando poderes...</div>
                  ) : poderes.length === 0 ? (
                    <div className="text-center py-6 text-neutral-400 text-sm">
                      No hay poderes radicados para esta asamblea.
                    </div>
                  ) : (
                    <div className="divide-y divide-neutral-100 dark:divide-neutral-800">
                      {poderes.map((p) => (
                        <div key={p.idPoder} className="py-3 flex items-center justify-between text-sm">
                          <div>
                            <span className="font-semibold text-neutral-800 dark:text-neutral-200">
                              Unidad {p.unidadIdentificador || `ID ${p.idUnidad}`}
                            </span>
                            <p className="text-xs text-neutral-500">
                              Otorgante: {p.nombrePropietario || `ID ${p.idPersonaPropietario}`} →
                              Apoderado: {p.nombreApoderado || `ID ${p.idPersonaApoderado}`}
                            </p>
                          </div>
                          <span
                            className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                              p.estado === 'APROBADO'
                                ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400'
                                : p.estado === 'RECHAZADO'
                                ? 'bg-red-500/15 text-red-700 dark:text-red-400'
                                : 'bg-amber-500/15 text-amber-700 dark:text-amber-400'
                            }`}
                          >
                            {p.estado}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </>
            ) : (
              <div className="p-8 text-center text-neutral-400 border border-neutral-200 dark:border-neutral-800 rounded-xl">
                Seleccione una asamblea del listado lateral para ver su detalle y quórum.
              </div>
            )}
          </div>
        </div>
      )}

      {/* Modal Radicar Poder */}
      {modalPoderOpen && (
        <Modal
          isOpen={modalPoderOpen}
          onClose={() => setModalPoderOpen(false)}
          title="Radicar Poder de Representación (Ley 675)"
        >
          <form onSubmit={handleRadicarPoder} className="space-y-4">
            <div className="p-3 bg-blue-500/10 border border-blue-500/20 rounded-lg text-xs text-blue-900 dark:text-blue-300">
              Conforme a la Ley 675 y el reglamento de la copropiedad, todo copropietario puede hacerse
              representar mediante poder debidamente conferido. No se permite la auto-representación.
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-500 mb-1">
                ID de la Unidad / Inmueble *
              </label>
              <input
                type="number"
                required
                value={formPoder.idUnidad}
                onChange={(e) => setFormPoder({ ...formPoder, idUnidad: e.target.value })}
                className="w-full px-3 py-2 text-sm rounded-lg border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="Ej. 101"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-500 mb-1">
                ID Persona Propietario (Poderdante) *
              </label>
              <input
                type="number"
                required
                value={formPoder.idPersonaPropietario}
                onChange={(e) =>
                  setFormPoder({ ...formPoder, idPersonaPropietario: e.target.value })
                }
                className="w-full px-3 py-2 text-sm rounded-lg border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="ID de su persona registrada"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-500 mb-1">
                ID Persona Apoderado (Representante) *
              </label>
              <input
                type="number"
                required
                value={formPoder.idPersonaApoderado}
                onChange={(e) =>
                  setFormPoder({ ...formPoder, idPersonaApoderado: e.target.value })
                }
                className="w-full px-3 py-2 text-sm rounded-lg border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="ID de la persona a quien delega"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-neutral-500 mb-1">
                Enlace / URL de Carta Poder Escaneada (Opcional)
              </label>
              <input
                type="url"
                value={formPoder.documentoPoderUrl}
                onChange={(e) =>
                  setFormPoder({ ...formPoder, documentoPoderUrl: e.target.value })
                }
                className="w-full px-3 py-2 text-sm rounded-lg border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-neutral-900 focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="https://..."
              />
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t border-neutral-100 dark:border-neutral-800">
              <button
                type="button"
                onClick={() => setModalPoderOpen(false)}
                className="px-4 py-2 text-sm font-medium rounded-lg border border-neutral-200 dark:border-neutral-800 hover:bg-neutral-100 dark:hover:bg-neutral-800 transition-colors"
              >
                Cancelar
              </button>
              <button
                type="submit"
                disabled={submittingPoder}
                className="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors disabled:opacity-50"
              >
                {submittingPoder ? 'Radicando...' : 'Radicar Poder'}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
