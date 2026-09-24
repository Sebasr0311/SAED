import React, { useState, useMemo, useCallback } from 'react';
import {
  Calendar,
  Users,
  Vote,
  FileText,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Clock,
  Plus,
  Play,
  Square,
  UserCheck,
  ShieldCheck,
  ChevronRight,
  RefreshCw,
  Percent,
  Award,
  Video,
  Building,
  ArrowRight,
  Check,
  X,
  ExternalLink,
  Upload,
  Download,
  Lock,
  Edit3,
  Save,
  AlertCircle,
  FileCheck,
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
  BORRADOR: { label: 'Borrador', color: 'bg-muted text-muted-foreground border border-border' },
  CONVOCADA: { label: 'Convocada', color: 'bg-blue-500/15 text-blue-700 dark:text-blue-400 border border-blue-500/30' },
  EN_CURSO: { label: 'En Curso', color: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30' },
  EN_RECESO: { label: 'En Receso', color: 'bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30' },
  FINALIZADA: { label: 'Finalizada', color: 'bg-gray-500/15 text-gray-700 dark:text-gray-400' },
  CANCELADA: { label: 'Cancelada', color: 'bg-red-500/15 text-red-700 dark:text-red-400' },
};

const ESTADOS_ACTA = {
  BORRADOR: { label: 'Borrador', color: 'bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30' },
  EN_REVISION_COMISION: { label: 'En Revisión (Comisión)', color: 'bg-blue-500/15 text-blue-700 dark:text-blue-400 border border-blue-500/30' },
  APROBADA: { label: 'Aprobada por Comisión', color: 'bg-purple-500/15 text-purple-700 dark:text-purple-400 border border-purple-500/30' },
  PUBLICADA_OFICIAL: { label: 'Publicada Oficialmente (Ley 675)', color: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30' },
};

const MAYORIAS = {
  SIMPLE_50_MAS_1: 'Mayoría Simple (50% + 1 de coeficientes presentes)',
  CALIFICADA_70_PCT: 'Mayoría Calificada (70% del coeficiente total de la copropiedad)',
  UNANIMIDAD_100_PCT: 'Unanimidad (100% de los coeficientes)',
};

export default function AsambleasAdminPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();
  const [tabActiva, setTabActiva] = useState('asambleas');
  const [filtroEstado, setFiltroEstado] = useState('');
  const [asambleaSeleccionadaId, setAsambleaSeleccionadaId] = useState(null);

  // Modales
  const [modalConvocarOpen, setModalConvocarOpen] = useState(false);
  const [modalAsistenciaOpen, setModalAsistenciaOpen] = useState(false);
  const [modalPoderOpen, setModalPoderOpen] = useState(false);
  const [modalVotacionOpen, setModalVotacionOpen] = useState(false);
  const [modalVotoOpen, setModalVotoOpen] = useState(false);
  const [votacionParaVotar, setVotacionParaVotar] = useState(null);

  // Formularios
  const [formConvocar, setFormConvocar] = useState({
    tipo: 'ORDINARIA',
    modalidad: 'MIXTA',
    titulo: '',
    convocatoriaNumero: 1,
    fechaHoraPrimeraConv: '',
    fechaHoraSegundaConv: '',
    lugarOEnlace: '',
    ordenDelDia: '',
    quorumRequeridoPct: 50.01,
  });

  const [formAsistencia, setFormAsistencia] = useState({
    idUnidad: '',
    idPersonaAsistente: '',
    esPropietarioDirecto: 'S',
    idPoder: '',
    coeficientePonderado: '',
  });

  const [formPoder, setFormPoder] = useState({
    idUnidad: '',
    idPersonaPropietario: '',
    idPersonaApoderado: '',
    documentoPoderUrl: '',
  });

  const [formVotacion, setFormVotacion] = useState({
    puntoOrdenDia: 1,
    titulo: '',
    descripcion: '',
    tipoMayoriaRequerida: 'SIMPLE_50_MAS_1',
  });

  const [formVoto, setFormVoto] = useState({
    idUnidad: '',
    idPersonaVotante: '',
    opcionVoto: 'SI',
  });

  const [submitting, setSubmitting] = useState(false);

  // 1. Cargar Asambleas
  const {
    data: asambleasRaw,
    loading: loadingAsambleas,
    error: errorAsambleas,
    refetch: refetchAsambleas,
  } = useFetch(() => tenantApi.get('/asambleas'), [tenant.activeAssignmentId]);

  const asambleas = useMemo(() => {
    return Array.isArray(asambleasRaw) ? asambleasRaw : asambleasRaw?.items || [];
  }, [asambleasRaw]);

  // Selección automática de asamblea activa si no hay seleccionada
  const asambleaActiva = useMemo(() => {
    if (asambleaSeleccionadaId) {
      const encontrada = asambleas.find((a) => a.idAsamblea === asambleaSeleccionadaId);
      if (encontrada) return encontrada;
    }
    // Priorizar una que esté EN_CURSO o CONVOCADA
    const enCurso = asambleas.find((a) => a.estado === 'EN_CURSO');
    if (enCurso) return enCurso;
    const convocada = asambleas.find((a) => a.estado === 'CONVOCADA');
    if (convocada) return convocada;
    return asambleas[0] || null;
  }, [asambleas, asambleaSeleccionadaId]);

  const activeId = asambleaActiva?.idAsamblea;

  // 2. Cargar Quórum en tiempo real de la asamblea seleccionada
  const { data: quorumLive, refetch: refetchQuorum } = useFetch(
    () => (activeId ? tenantApi.get(`/asambleas/${activeId}/quorum`) : Promise.resolve(null)),
    [activeId, tenant.activeAssignmentId]
  );

  // 3. Cargar Asistencias
  const { data: asistenciasRaw, refetch: refetchAsistencias } = useFetch(
    () => (activeId ? tenantApi.get(`/asambleas/${activeId}/asistencias`) : Promise.resolve([])),
    [activeId, tenant.activeAssignmentId]
  );
  const asistencias = useMemo(() => {
    return Array.isArray(asistenciasRaw) ? asistenciasRaw : asistenciasRaw?.items || [];
  }, [asistenciasRaw]);

  // 4. Cargar Poderes
  const { data: poderesRaw, refetch: refetchPoderes } = useFetch(
    () => (activeId ? tenantApi.get(`/asambleas/${activeId}/poderes`) : Promise.resolve([])),
    [activeId, tenant.activeAssignmentId]
  );
  const poderes = useMemo(() => {
    return Array.isArray(poderesRaw) ? poderesRaw : poderesRaw?.items || [];
  }, [poderesRaw]);

  // 5. Cargar Votaciones
  const { data: votacionesRaw, refetch: refetchVotaciones } = useFetch(
    () => (activeId ? tenantApi.get(`/asambleas/${activeId}/votaciones`) : Promise.resolve([])),
    [activeId, tenant.activeAssignmentId]
  );
  const votaciones = useMemo(() => {
    return Array.isArray(votacionesRaw) ? votacionesRaw : votacionesRaw?.items || [];
  }, [votacionesRaw]);

  // 6. Cargar Unidades y Personas del Tenant para selectors
  const { data: unidadesRaw } = useFetch(() => tenantApi.get('/units'), [tenant.activeAssignmentId]);
  const unidades = useMemo(() => {
    return Array.isArray(unidadesRaw) ? unidadesRaw : unidadesRaw?.items || [];
  }, [unidadesRaw]);

  const { data: personasRaw } = useFetch(() => tenantApi.get('/personas?page=0&size=200'), [tenant.activeAssignmentId]);
  const personas = useMemo(() => {
    return Array.isArray(personasRaw) ? personasRaw : personasRaw?.items || [];
  }, [personasRaw]);

  // 7. Cargar Acta de la asamblea seleccionada
  const {
    data: actaData,
    loading: loadingActa,
    refetch: refetchActa,
  } = useFetch(
    () =>
      activeId
        ? tenantApi.get(`/actas/asamblea/${activeId}`).catch((err) => {
            if (err?.status === 404 || err?.response?.status === 404) return null;
            throw err;
          })
        : Promise.resolve(null),
    [activeId, tenant.activeAssignmentId]
  );

  // Estados de gestión de acta
  const [editandoActa, setEditandoActa] = useState(false);
  const [formActa, setFormActa] = useState({
    numeroActa: '',
    contenidoTexto: '',
  });
  const [submittingActa, setSubmittingActa] = useState(false);
  const [uploadingDoc, setUploadingDoc] = useState(false);

  // Sincronizar formActa cuando cambia el acta o la asamblea activa
  React.useEffect(() => {
    if (actaData) {
      setFormActa({
        numeroActa: actaData.numeroActa || '',
        contenidoTexto: actaData.contenidoTexto || '',
      });
      setEditandoActa(false);
    } else if (asambleaActiva) {
      setFormActa({
        numeroActa: `ACTA-${asambleaActiva.idAsamblea}-${new Date().getFullYear()}`,
        contenidoTexto: `ACTA DE ASAMBLEA GENERAL DE COPROPIETARIOS\n\n1. CONVOCATORIA:\nAsamblea ${asambleaActiva.tipo} celebrada el ${asambleaActiva.fechaHoraPrimeraConv ? new Date(asambleaActiva.fechaHoraPrimeraConv).toLocaleString() : ''} bajo modalidad ${asambleaActiva.modalidad}.\n\n2. ORDEN DEL DÍA:\n${asambleaActiva.ordenDelDia || ''}\n\n3. VERIFICACIÓN DEL QUÓRUM:\nSe verificó el quórum legal deliberatorio y decisorio.\n\n4. DESARROLLO Y DECISIONES:\n(Detalle de deliberaciones y resoluciones adoptadas)\n\n5. CIERRE Y COMISIÓN VERIFICADORA:\nEn constancia de lo actuado, se firma la presente acta para constancia legal conforme al Art. 47 de la Ley 675 de 2001.`,
      });
      setEditandoActa(false);
    }
  }, [actaData, asambleaActiva]);

  // Validaciones previas para publicación oficial de acta (Art. 47 Ley 675)
  const asambleaFinalizada = asambleaActiva?.estado === 'FINALIZADA';
  const todasVotacionesCerradas = votaciones.every((v) => v.estado === 'CERRADA' || v.estado === 'ANULADA');
  const tieneDocumentoAdjunto = Boolean(actaData?.idDocumento || actaData?.documentoFirmadoUrl);
  const puedePublicarOficial = asambleaFinalizada && todasVotacionesCerradas && tieneDocumentoAdjunto;

  // Recarga unificada
  const [sincronizando, setSincronizando] = useState(false);
  const handleRefetchAll = useCallback(() => {
    setSincronizando(true);
    Promise.allSettled([
      refetchAsambleas(),
      refetchQuorum(),
      refetchAsistencias(),
      refetchPoderes(),
      refetchVotaciones(),
      refetchActa(),
    ]).finally(() => {
      setTimeout(() => setSincronizando(false), 400);
      toast.success('Datos de asambleas sincronizados');
    });
  }, [refetchAsambleas, refetchQuorum, refetchAsistencias, refetchPoderes, refetchVotaciones, refetchActa]);

  // Handlers de Acta
  const handleCrearBorradorActa = async (e) => {
    e?.preventDefault();
    if (!formActa.numeroActa.trim() || !formActa.contenidoTexto.trim()) {
      toast.error('Complete el número de acta y el contenido');
      return;
    }
    setSubmittingActa(true);
    try {
      await tenantApi.post('/actas', {
        idAsamblea: activeId,
        numeroActa: formActa.numeroActa.trim(),
        contenidoTexto: formActa.contenidoTexto,
      });
      toast.success('Borrador de acta creado exitosamente');
      refetchActa();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al crear borrador');
    } finally {
      setSubmittingActa(false);
    }
  };

  const handleGuardarContenidoActa = async () => {
    if (!actaData?.idActa) return;
    setSubmittingActa(true);
    try {
      await tenantApi.put(`/actas/${actaData.idActa}`, {
        numeroActa: formActa.numeroActa.trim(),
        contenidoTexto: formActa.contenidoTexto,
      });
      toast.success('Contenido del acta actualizado');
      setEditandoActa(false);
      refetchActa();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al actualizar acta');
    } finally {
      setSubmittingActa(false);
    }
  };

  const handleCambiarEstadoActa = async (nuevoEstado) => {
    if (!actaData?.idActa) return;
    setSubmittingActa(true);
    try {
      await tenantApi.put(`/actas/${actaData.idActa}/estado`, {
        nuevoEstado,
        observaciones: `Transición a ${nuevoEstado} por administración`,
      });
      toast.success(`Acta actualizada a: ${nuevoEstado}`);
      refetchActa();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al cambiar estado del acta');
    } finally {
      setSubmittingActa(false);
    }
  };

  const handleSubirDocumentoActa = async (file) => {
    if (!file || !actaData?.idActa) return;
    setUploadingDoc(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('descripcion', `Documento firmado de acta ${actaData.numeroActa}`);
      await tenantApi.post(`/actas/${actaData.idActa}/documento`, formData);
      toast.success('Documento firmado adjuntado exitosamente');
      refetchActa();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al subir documento');
    } finally {
      setUploadingDoc(false);
    }
  };

  const handleDescargarDocumentoActa = async () => {
    if (!actaData?.idActa) return;
    try {
      const token = sessionStorage.getItem(TOKEN_KEY) || sessionStorage.getItem('token');
      const assignmentId = tenant.activeAssignmentId;
      const res = await fetch(`${BASE_URL}/actas/${actaData.idActa}/documento`, {
        headers: {
          Authorization: `Bearer ${token}`,
          ...(assignmentId ? { 'X-Assignment-Id': String(assignmentId) } : {}),
        },
      });
      if (!res.ok) throw new Error('No se pudo descargar el documento firmado');
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `Acta_${actaData.numeroActa || actaData.idActa}.pdf`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      toast.error(err.message || 'Error al descargar documento');
    }
  };

  // Handlers de Acciones de Asamblea
  const handleConvocar = async (e) => {
    e.preventDefault();
    if (!formConvocar.titulo.trim() || !formConvocar.fechaHoraPrimeraConv) {
      toast.error('Complete el título y la fecha de primera convocatoria');
      return;
    }
    setSubmitting(true);
    try {
      const res = await tenantApi.post('/asambleas', {
        ...formConvocar,
        convocatoriaNumero: Number(formConvocar.convocatoriaNumero) || 1,
        quorumRequeridoPct: Number(formConvocar.quorumRequeridoPct) || 50.01,
      });
      toast.success('Asamblea convocada exitosamente');
      setModalConvocarOpen(false);
      setFormConvocar({
        tipo: 'ORDINARIA',
        modalidad: 'MIXTA',
        titulo: '',
        convocatoriaNumero: 1,
        fechaHoraPrimeraConv: '',
        fechaHoraSegundaConv: '',
        lugarOEnlace: '',
        ordenDelDia: '',
        quorumRequeridoPct: 50.01,
      });
      refetchAsambleas();
      if (res?.idAsamblea) setAsambleaSeleccionadaId(res.idAsamblea);
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al convocar la asamblea');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCambiarEstado = async (idAsamblea, nuevoEstado) => {
    try {
      await tenantApi.put(`/asambleas/${idAsamblea}/estado`, { nuevoEstado });
      toast.success(`Asamblea actualizada a estado: ${nuevoEstado}`);
      refetchAsambleas();
      refetchQuorum();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al cambiar estado');
    }
  };

  // Handlers de Asistencia
  const handleRegistrarAsistencia = async (e) => {
    e.preventDefault();
    if (!formAsistencia.idUnidad || !formAsistencia.idPersonaAsistente) {
      toast.error('Seleccione unidad y persona asistente');
      return;
    }
    setSubmitting(true);
    try {
      await tenantApi.post(`/asambleas/${activeId}/asistencias`, {
        idUnidad: Number(formAsistencia.idUnidad),
        idPersonaAsistente: Number(formAsistencia.idPersonaAsistente),
        esPropietarioDirecto: formAsistencia.esPropietarioDirecto,
        idPoder: formAsistencia.idPoder ? Number(formAsistencia.idPoder) : null,
        coeficientePonderado: formAsistencia.coeficientePonderado
          ? Number(formAsistencia.coeficientePonderado)
          : null,
      });
      toast.success('Asistencia registrada y quórum actualizado');
      setModalAsistenciaOpen(false);
      setFormAsistencia({
        idUnidad: '',
        idPersonaAsistente: '',
        esPropietarioDirecto: 'S',
        idPoder: '',
        coeficientePonderado: '',
      });
      refetchAsistencias();
      refetchQuorum();
      refetchAsambleas();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al registrar asistencia');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRetirarAsistencia = async (idUnidad) => {
    try {
      await tenantApi.put(`/asambleas/${activeId}/asistencias/${idUnidad}/retiro`);
      toast.info('Retiro de asistencia registrado');
      refetchAsistencias();
      refetchQuorum();
      refetchAsambleas();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al registrar retiro');
    }
  };

  // Handlers de Poderes
  const handleRadicarPoder = async (e) => {
    e.preventDefault();
    if (!formPoder.idUnidad || !formPoder.idPersonaPropietario || !formPoder.idPersonaApoderado) {
      toast.error('Complete unidad, propietario y apoderado');
      return;
    }
    if (formPoder.idPersonaPropietario === formPoder.idPersonaApoderado) {
      toast.error('El propietario y apoderado no pueden ser la misma persona');
      return;
    }
    setSubmitting(true);
    try {
      await tenantApi.post(`/asambleas/${activeId}/poderes`, {
        idUnidad: Number(formPoder.idUnidad),
        idPersonaPropietario: Number(formPoder.idPersonaPropietario),
        idPersonaApoderado: Number(formPoder.idPersonaApoderado),
        documentoPoderUrl: formPoder.documentoPoderUrl,
      });
      toast.success('Poder radicado exitosamente');
      setModalPoderOpen(false);
      setFormPoder({ idUnidad: '', idPersonaPropietario: '', idPersonaApoderado: '', documentoPoderUrl: '' });
      refetchPoderes();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al radicar poder');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDecidirPoder = async (idPoder, estado) => {
    try {
      await tenantApi.put(`/asambleas/${activeId}/poderes/${idPoder}/decision`, { estado });
      toast.success(`Poder ${estado === 'APROBADO' ? 'aprobado' : 'rechazado'} exitosamente`);
      refetchPoderes();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al actualizar poder');
    }
  };

  // Handlers de Votaciones
  const handleCrearVotacion = async (e) => {
    e.preventDefault();
    if (!formVotacion.titulo.trim()) {
      toast.error('Ingrese el título del punto a votar');
      return;
    }
    setSubmitting(true);
    try {
      await tenantApi.post(`/asambleas/${activeId}/votaciones`, {
        ...formVotacion,
        puntoOrdenDia: Number(formVotacion.puntoOrdenDia) || 1,
      });
      toast.success('Punto de votación abierto exitosamente');
      setModalVotacionOpen(false);
      setFormVotacion({
        puntoOrdenDia: votaciones.length + 1,
        titulo: '',
        descripcion: '',
        tipoMayoriaRequerida: 'SIMPLE_50_MAS_1',
      });
      refetchVotaciones();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al abrir votación');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCerrarVotacion = async (idVotacion) => {
    try {
      await tenantApi.put(`/asambleas/${activeId}/votaciones/${idVotacion}/cerrar`);
      toast.success('Votación cerrada y cómputo de resultados fijado');
      refetchVotaciones();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al cerrar votación');
    }
  };

  const handleEmitirVoto = async (e) => {
    e.preventDefault();
    if (!formVoto.idUnidad || !formVoto.idPersonaVotante) {
      toast.error('Seleccione la unidad y el votante');
      return;
    }
    setSubmitting(true);
    try {
      await tenantApi.post(`/asambleas/${activeId}/votaciones/${votacionParaVotar.idVotacion}/votar`, {
        idUnidad: Number(formVoto.idUnidad),
        idPersonaVotante: Number(formVoto.idPersonaVotante),
        opcionVoto: formVoto.opcionVoto,
      });
      toast.success(`Voto [${formVoto.opcionVoto}] registrado exitosamente`);
      setModalVotoOpen(false);
      setFormVoto({ idUnidad: '', idPersonaVotante: '', opcionVoto: 'SI' });
      refetchVotaciones();
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Error al emitir voto');
    } finally {
      setSubmitting(false);
    }
  };

  // KPIs
  const proximaAsamblea = useMemo(() => {
    return asambleas.find((a) => a.estado === 'EN_CURSO') || asambleas.find((a) => a.estado === 'CONVOCADA');
  }, [asambleas]);

  const poderesPendientes = useMemo(() => {
    return poderes.filter((p) => p.estado === 'PENDIENTE_REVISION').length;
  }, [poderes]);

  const asambleasFiltradas = useMemo(() => {
    if (!filtroEstado) return asambleas;
    return asambleas.filter((a) => a.estado === filtroEstado);
  }, [asambleas, filtroEstado]);

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-12">
      {/* 1. Header Ejecutivo */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <PageHeader
          title="Asambleas y Decisiones Copropietarias"
          description="Convocatorias legales, quórum en tiempo real conforme a la Ley 675, poderes de representación y votaciones vinculantes."
        />
        <div className="flex items-center gap-3">
          <button
            onClick={handleRefetchAll}
            disabled={sincronizando}
            className="inline-flex items-center gap-2 px-3 py-1.5 text-xs font-medium rounded-lg border border-border bg-card text-foreground hover:bg-muted transition-colors shadow-xs"
            title="Sincronizar quórum y asambleas"
          >
            <RefreshCw className={`w-4 h-4 ${sincronizando ? 'animate-spin text-primary' : ''}`} />
            Sincronizar
          </button>
          <button
            onClick={() => setModalConvocarOpen(true)}
            className="inline-flex items-center gap-2 px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-xs"
          >
            <Plus className="w-4 h-4" />
            Convocar Asamblea
          </button>
        </div>
      </div>

      {/* 2. Tarjetas KPI de Alto Impacto */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-card text-card-foreground border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Próxima Convocatoria
            </span>
            <div className="p-2 rounded-lg bg-blue-500/10 text-blue-600">
              <Calendar className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-lg font-bold truncate">
              {proximaAsamblea ? proximaAsamblea.titulo : 'Sin asambleas pendientes'}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {proximaAsamblea
                ? `${proximaAsamblea.tipo} • ${proximaAsamblea.modalidad}`
                : 'Todas las asambleas están al día'}
            </p>
          </div>
        </div>

        <div className="bg-card text-card-foreground border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Quórum en Vivo
            </span>
            <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-600">
              <Percent className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <div className="text-2xl font-bold">
              {quorumLive ? `${quorumLive.quorumAlcanzadoPct || 0}%` : `${asambleaActiva?.quorumAlcanzadoPct || 0}%`}
            </div>
            <span className="text-xs text-muted-foreground">
              / req. {asambleaActiva?.quorumRequeridoPct || 50.01}%
            </span>
          </div>
          <div className="mt-2 flex items-center gap-1.5">
            {quorumLive?.tieneQuorum ? (
              <span className="badge badge-xs bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 font-medium">
                ✅ Quórum Válido
              </span>
            ) : (
              <span className="badge badge-xs bg-amber-500/15 text-amber-700 dark:text-amber-400 font-medium">
                ⏳ Quórum en Registro
              </span>
            )}
            <span className="text-xs text-muted-foreground">
              ({quorumLive?.totalUnidadesRegistradas || 0} unidades)
            </span>
          </div>
        </div>

        <div className="bg-card text-card-foreground border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Historial Asambleas
            </span>
            <div className="p-2 rounded-lg bg-purple-500/10 text-purple-600">
              <Award className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-bold">{asambleas.length}</div>
            <p className="text-xs text-muted-foreground mt-1">
              {asambleas.filter((a) => a.estado === 'FINALIZADA').length} concluidas formalmente
            </p>
          </div>
        </div>

        <div className="bg-card text-card-foreground border border-border rounded-xl p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Poderes por Revisar
            </span>
            <div className="p-2 rounded-lg bg-amber-500/10 text-amber-600">
              <ShieldCheck className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-bold">{poderesPendientes}</div>
            <p className="text-xs text-muted-foreground mt-1">
              {poderesPendientes > 0
                ? 'Requieren validación de administración'
                : 'Todos los poderes revisados'}
            </p>
          </div>
        </div>
      </div>

      {/* 3. Selector de Asamblea Activa para Salas y Votaciones */}
      {asambleas.length > 0 && (
        <div className="bg-card text-card-foreground border border-border rounded-xl p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-lg bg-primary/10 text-primary">
              <Building className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs text-muted-foreground font-medium uppercase">Asamblea en Foco Operativo</div>
              <div className="font-semibold text-base">
                {asambleaActiva?.titulo || 'Seleccione una asamblea'}
              </div>
            </div>
          </div>

          <div className="flex items-center gap-3 w-full sm:w-auto">
            <select
              className="w-full sm:w-72 px-3 py-1.5 text-xs rounded-lg border border-border bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 shadow-xs"
              value={asambleaActiva?.idAsamblea || ''}
              onChange={(e) => setAsambleaSeleccionadaId(Number(e.target.value))}
            >
              {asambleas.map((a) => (
                <option key={a.idAsamblea} value={a.idAsamblea}>
                  [{a.estado}] {a.titulo}
                </option>
              ))}
            </select>

            {asambleaActiva && asambleaActiva.estado === 'CONVOCADA' && (
              <button
                onClick={() => handleCambiarEstado(asambleaActiva.idAsamblea, 'EN_CURSO')}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 transition-colors shadow-xs"
                title="Iniciar sesión formal"
              >
                <Play className="w-3.5 h-3.5" /> Iniciar
              </button>
            )}

            {asambleaActiva && asambleaActiva.estado === 'EN_CURSO' && (
              <button
                onClick={() => handleCambiarEstado(asambleaActiva.idAsamblea, 'FINALIZADA')}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-destructive/30 text-destructive hover:bg-destructive/10 transition-colors"
                title="Finalizar asamblea"
              >
                <Square className="w-3.5 h-3.5" /> Finalizar
              </button>
            )}
          </div>
        </div>
      )}

      {/* 4. Pestañas de Navegación del Módulo */}
      <div className="tabs tabs-boxed bg-muted/60 p-1 rounded-xl flex flex-wrap gap-1">
        <button
          onClick={() => setTabActiva('asambleas')}
          className={`tab tab-sm font-medium transition-all ${tabActiva === 'asambleas' ? 'tab-active bg-card text-foreground shadow-xs' : 'text-muted-foreground'}`}
        >
          <Calendar className="w-4 h-4 mr-1.5" />
          Convocatorias y Asambleas ({asambleas.length})
        </button>
        <button
          onClick={() => setTabActiva('quorum')}
          className={`tab tab-sm font-medium transition-all ${tabActiva === 'quorum' ? 'tab-active bg-card text-foreground shadow-xs' : 'text-muted-foreground'}`}
        >
          <UserCheck className="w-4 h-4 mr-1.5" />
          Quórum y Asistencia en Vivo ({asistencias.filter((a) => !a.horaRetiro).length})
        </button>
        <button
          onClick={() => setTabActiva('poderes')}
          className={`tab tab-sm font-medium transition-all ${tabActiva === 'poderes' ? 'tab-active bg-card text-foreground shadow-xs' : 'text-muted-foreground'}`}
        >
          <ShieldCheck className="w-4 h-4 mr-1.5" />
          Poderes y Representación ({poderes.length})
        </button>
        <button
          onClick={() => setTabActiva('votaciones')}
          className={`tab tab-sm font-medium transition-all ${tabActiva === 'votaciones' ? 'tab-active bg-card text-foreground shadow-xs' : 'text-muted-foreground'}`}
        >
          <Vote className="w-4 h-4 mr-1.5" />
          Votaciones y Escrutinio ({votaciones.length})
        </button>
        <button
          onClick={() => setTabActiva('actas')}
          className={`tab tab-sm font-medium transition-all ${tabActiva === 'actas' ? 'tab-active bg-card text-foreground shadow-xs' : 'text-muted-foreground'}`}
        >
          <FileText className="w-4 h-4 mr-1.5" />
          Acta Oficial (Ley 675) {actaData ? `(#${actaData.numeroActa})` : ''}
        </button>
      </div>

      {/* TAB 1: LISTADO DE ASAMBLEAS */}
      {tabActiva === 'asambleas' && (
        <div className="bg-card text-card-foreground border border-border rounded-xl p-6 shadow-sm space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/60">
            <div>
              <h3 className="text-base font-semibold">Registro de Asambleas Generales</h3>
              <p className="text-xs text-muted-foreground">Historial oficial y estado de convocatorias ordinarias y extraordinarias.</p>
            </div>
            <div className="flex items-center gap-2">
              <select
                className="px-2.5 py-1 text-xs rounded-md border border-border bg-background text-foreground focus:outline-none focus:ring-1 focus:ring-primary/20 shadow-xs"
                value={filtroEstado}
                onChange={(e) => setFiltroEstado(e.target.value)}
              >
                <option value="">Todos los estados</option>
                <option value="CONVOCADA">Convocadas</option>
                <option value="EN_CURSO">En Curso</option>
                <option value="FINALIZADA">Finalizadas</option>
                <option value="CANCELADA">Canceladas</option>
              </select>
            </div>
          </div>

          {loadingAsambleas ? (
            <div className="text-center py-12 text-muted-foreground">Cargando asambleas...</div>
          ) : asambleasFiltradas.length === 0 ? (
            <div className="text-center py-12 border border-dashed border-border rounded-xl">
              <Calendar className="w-10 h-10 text-muted-foreground mx-auto mb-2 opacity-50" />
              <p className="font-semibold text-sm">No hay asambleas registradas</p>
              <p className="text-xs text-muted-foreground mt-1">Convoque la primera asamblea para comenzar.</p>
              <button
                onClick={() => setModalConvocarOpen(true)}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-xs mt-4"
              >
                Convocar Ahora
              </button>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left border-collapse">
                <thead>
                  <tr className="border-b border-border bg-muted/40 text-muted-foreground text-[11px] uppercase tracking-wider">
                    <th className="py-2.5 px-3 font-semibold">Título & Convocatoria</th>
                    <th className="py-2.5 px-3 font-semibold">Tipo</th>
                    <th className="py-2.5 px-3 font-semibold">Modalidad</th>
                    <th className="py-2.5 px-3 font-semibold">1ra Convocatoria</th>
                    <th className="py-2.5 px-3 font-semibold">Quórum Alcanzado</th>
                    <th className="py-2.5 px-3 font-semibold">Estado</th>
                    <th className="py-2.5 px-3 font-semibold text-right">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/40">
                  {asambleasFiltradas.map((a) => {
                    const estadoMeta = ESTADOS_ASAMBLEA[a.estado] || { label: a.estado, color: 'bg-muted text-muted-foreground border border-border' };
                    return (
                      <tr key={a.idAsamblea} className="hover:bg-muted/40 transition-colors">
                        <td className="py-2.5 px-3">
                          <div className="font-semibold text-sm">{a.titulo}</div>
                          <div className="text-xs text-muted-foreground">Convocatoria #{a.convocatoriaNumero || 1}</div>
                        </td>
                        <td className="py-2.5 px-3">
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium border border-border bg-secondary/50 text-secondary-foreground">{a.tipo}</span>
                        </td>
                        <td className="py-2.5 px-3">
                          <span className="text-xs font-medium flex items-center gap-1">
                            {a.modalidad === 'VIRTUAL' && <Video className="w-3.5 h-3.5 text-blue-500" />}
                            {a.modalidad === 'PRESENCIAL' && <Users className="w-3.5 h-3.5 text-emerald-500" />}
                            {a.modalidad === 'MIXTA' && <Building className="w-3.5 h-3.5 text-amber-500" />}
                            {a.modalidad}
                          </span>
                        </td>
                        <td className="py-2.5 px-3 text-xs">
                          {a.fechaHoraPrimeraConv ? new Date(a.fechaHoraPrimeraConv).toLocaleString('es-CO') : 'Sin fecha'}
                        </td>
                        <td className="py-2.5 px-3">
                          <div className="flex items-center gap-2">
                            <div className="w-20 bg-muted rounded-full h-2 overflow-hidden">
                              <div
                                className={`h-full ${a.quorumAlcanzadoPct >= a.quorumRequeridoPct ? 'bg-emerald-500' : 'bg-primary'}`}
                                style={{ width: `${Math.min(a.quorumAlcanzadoPct || 0, 100)}%` }}
                              />
                            </div>
                            <span className="text-xs font-semibold">{a.quorumAlcanzadoPct || 0}%</span>
                          </div>
                        </td>
                        <td className="py-2.5 px-3">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium ${estadoMeta.color}`}>{estadoMeta.label}</span>
                        </td>
                        <td className="py-2.5 px-3 text-right">
                          <button
                            onClick={() => {
                              setAsambleaSeleccionadaId(a.idAsamblea);
                              setTabActiva('quorum');
                            }}
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md border border-primary/30 text-primary hover:bg-primary/10 transition-colors"
                          >
                            Abrir Sala <ChevronRight className="w-3 h-3" />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* TAB 2: QUÓRUM Y ASISTENCIA EN VIVO */}
      {tabActiva === 'quorum' && asambleaActiva && (
        <div className="space-y-6">
          {/* Tarjeta de Control de Quórum Legal */}
          <div className="bg-card text-card-foreground border border-border rounded-xl p-6 shadow-sm">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-border/60">
              <div>
                <span className="text-xs font-semibold uppercase text-primary tracking-wider">
                  Control de Asistencia y Quórum Legal (Ley 675 / 2001)
                </span>
                <h3 className="text-xl font-bold mt-0.5">{asambleaActiva.titulo}</h3>
                <p className="text-xs text-muted-foreground mt-1">
                  Modalidad: {asambleaActiva.modalidad} • Ubicación/Enlace: {asambleaActiva.lugarOEnlace}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setModalAsistenciaOpen(true)}
                  disabled={asambleaActiva.estado === 'FINALIZADA' || asambleaActiva.estado === 'CANCELADA'}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-xs disabled:opacity-50"
                >
                  <UserCheck className="w-4 h-4" />
                  Registrar Asistente
                </button>
              </div>
            </div>

            {/* Barra Visual de Quórum */}
            <div className="mt-6 p-5 bg-muted/40 rounded-xl border border-border/80 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-semibold">Progreso de Quórum Ponderado:</span>
                  <span className="text-lg font-extrabold text-primary">
                    {quorumLive?.quorumAlcanzadoPct || asambleaActiva.quorumAlcanzadoPct || 0}%
                  </span>
                </div>
                <div className="text-xs font-medium text-muted-foreground">
                  Quórum Mínimo Requerido: <span className="font-bold text-foreground">{asambleaActiva.quorumRequeridoPct || 50.01}%</span>
                </div>
              </div>

              {/* Progress Bar con Marcador */}
              <div className="relative w-full bg-border rounded-full h-4 overflow-hidden">
                <div
                  className={`h-full transition-all duration-500 ${(quorumLive?.quorumAlcanzadoPct || asambleaActiva.quorumAlcanzadoPct || 0) >= asambleaActiva.quorumRequeridoPct ? 'bg-emerald-500' : 'bg-primary'}`}
                  style={{ width: `${Math.min(quorumLive?.quorumAlcanzadoPct || asambleaActiva.quorumAlcanzadoPct || 0, 100)}%` }}
                />
              </div>

              <div className="flex items-center justify-between text-xs pt-1">
                <span className="text-muted-foreground">
                  Unidades presentes: <strong className="text-foreground">{asistencias.filter((a) => !a.horaRetiro).length}</strong>
                </span>
                {quorumLive?.tieneQuorum ? (
                  <span className="text-emerald-600 dark:text-emerald-400 font-bold flex items-center gap-1">
                    <CheckCircle2 className="w-4 h-4" /> Quórum legal deliberatorio y decisorio alcanzado
                  </span>
                ) : (
                  <span className="text-amber-600 dark:text-amber-400 font-medium flex items-center gap-1">
                    <AlertTriangle className="w-4 h-4" /> Pendiente para deliberar válidamente
                  </span>
                )}
              </div>
            </div>

            {/* Tabla de Asistentes */}
            <div className="mt-6">
              <h4 className="text-sm font-bold mb-3 flex items-center gap-2">
                <Users className="w-4 h-4 text-primary" /> Registro Detallado de Asistencias
              </h4>
              {asistencias.length === 0 ? (
                <div className="text-center py-8 border border-dashed border-border rounded-lg text-xs text-muted-foreground">
                  Aún no se han registrado unidades en esta asamblea.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="table table-xs w-full">
                    <thead>
                      <tr className="border-b border-border text-muted-foreground">
                        <th>Unidad / Apto</th>
                        <th>Asistente</th>
                        <th>Identificación</th>
                        <th>Calidad</th>
                        <th>Coeficiente</th>
                        <th>Hora Entrada</th>
                        <th>Estado</th>
                        <th className="text-right">Acción</th>
                      </tr>
                    </thead>
                    <tbody>
                      {asistencias.map((asist) => (
                        <tr key={asist.idAsistencia} className={asist.horaRetiro ? 'opacity-50' : ''}>
                          <td className="font-bold">{asist.unidadIdentificador || `Unidad ${asist.idUnidad}`}</td>
                          <td>{asist.nombreAsistente || 'N/D'}</td>
                          <td className="font-mono text-xs">{asist.documentoAsistente || '-'}</td>
                          <td>
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium border border-border bg-secondary/50 text-secondary-foreground">
                              {asist.esPropietarioDirecto === 'S' ? 'Propietario Directo' : 'Apoderado'}
                            </span>
                          </td>
                          <td className="font-semibold">{asist.coeficientePonderado || '0.00'}</td>
                          <td className="text-xs">
                            {asist.horaRegistro ? new Date(asist.horaRegistro).toLocaleTimeString('es-CO') : '-'}
                          </td>
                          <td>
                            {asist.horaRetiro ? (
                              <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-muted text-muted-foreground">Retirado ({new Date(asist.horaRetiro).toLocaleTimeString('es-CO')})</span>
                            ) : (
                              <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">Presente</span>
                            )}
                          </td>
                          <td className="text-right">
                            {!asist.horaRetiro && (
                              <button
                                onClick={() => handleRetirarAsistencia(asist.idUnidad)}
                                className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md border border-destructive/30 text-destructive hover:bg-destructive/10 transition-colors"
                                title="Registrar salida / retiro de sala"
                              >
                                Retirar
                              </button>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* TAB 3: PODERES DE REPRESENTACIÓN */}
      {tabActiva === 'poderes' && asambleaActiva && (
        <div className="bg-card text-card-foreground border border-border rounded-xl p-6 shadow-sm space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/60">
            <div>
              <h3 className="text-base font-semibold">Poderes y Delegaciones de Voto</h3>
              <p className="text-xs text-muted-foreground">
                Revisión y validación legal de cartas poder otorgadas por propietarios que no asisten directamente.
              </p>
            </div>
            <button
              onClick={() => setModalPoderOpen(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-xs"
            >
              <Plus className="w-4 h-4" /> Radicar Poder
            </button>
          </div>

          {poderes.length === 0 ? (
            <div className="text-center py-12 border border-dashed border-border rounded-xl text-muted-foreground">
              <ShieldCheck className="w-10 h-10 mx-auto mb-2 opacity-40" />
              <p className="font-semibold text-sm">No hay poderes radicados</p>
              <p className="text-xs mt-1">Los poderes subidos por copropietarios aparecerán aquí para aprobación.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left border-collapse">
                <thead>
                  <tr className="border-b border-border bg-muted/40 text-muted-foreground text-[11px] uppercase tracking-wider">
                    <th className="py-2.5 px-3 font-semibold">Unidad</th>
                    <th className="py-2.5 px-3 font-semibold">Propietario Otorgante</th>
                    <th className="py-2.5 px-3 font-semibold">Apoderado Receptor</th>
                    <th className="py-2.5 px-3 font-semibold">Soporte / Documento</th>
                    <th className="py-2.5 px-3 font-semibold">Fecha Registro</th>
                    <th className="py-2.5 px-3 font-semibold">Estado</th>
                    <th className="py-2.5 px-3 font-semibold text-right">Decisión</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/40">
                  {poderes.map((p) => (
                    <tr key={p.idPoder} className="hover:bg-muted/40 transition-colors">
                      <td className="py-2.5 px-3 font-bold">{p.unidadIdentificador || `Unidad ${p.idUnidad}`}</td>
                      <td className="py-2.5 px-3">
                        <div className="font-medium text-sm">{p.nombrePropietario}</div>
                        <div className="text-xs text-muted-foreground font-mono">{p.documentoPropietario}</div>
                      </td>
                      <td className="py-2.5 px-3">
                        <div className="font-medium text-sm">{p.nombreApoderado}</div>
                        <div className="text-xs text-muted-foreground font-mono">{p.documentoApoderado}</div>
                      </td>
                      <td className="py-2.5 px-3">
                        {p.documentoPoderUrl ? (
                          <a
                            href={p.documentoPoderUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md border border-primary/30 text-primary hover:bg-primary/10 transition-colors"
                          >
                            <ExternalLink className="w-3 h-3" /> Ver Carta
                          </a>
                        ) : (
                          <span className="text-xs text-muted-foreground">Físico / Sin archivo</span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-xs">
                        {p.fechaRegistro ? new Date(p.fechaRegistro).toLocaleDateString('es-CO') : '-'}
                      </td>
                      <td className="py-2.5 px-3">
                        {p.estado === 'PENDIENTE_REVISION' && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30">Por Validar</span>
                        )}
                        {p.estado === 'APROBADO' && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30">Aprobado</span>
                        )}
                        {p.estado === 'RECHAZADO' && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-destructive/15 text-destructive border border-destructive/30">Rechazado</span>
                        )}
                      </td>
                      <td className="py-2.5 px-3 text-right">
                        {p.estado === 'PENDIENTE_REVISION' && (
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              onClick={() => handleDecidirPoder(p.idPoder, 'APROBADO')}
                              className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md bg-emerald-600 text-white hover:bg-emerald-700 transition-colors shadow-xs"
                            >
                              <Check className="w-3 h-3" /> Aprobar
                            </button>
                            <button
                              onClick={() => handleDecidirPoder(p.idPoder, 'RECHAZADO')}
                              className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md border border-destructive/30 text-destructive hover:bg-destructive/10 transition-colors"
                            >
                              <X className="w-3 h-3" /> Rechazar
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* TAB 4: VOTACIONES Y ESCRUTINIO */}
      {tabActiva === 'votaciones' && asambleaActiva && (
        <div className="space-y-6">
          <div className="bg-card text-card-foreground border border-border rounded-xl p-6 shadow-sm space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/60">
              <div>
                <h3 className="text-base font-semibold">Puntos de Votación y Decisiones Vinculantes</h3>
                <p className="text-xs text-muted-foreground">
                  Escrutinio por coeficiente ponderado según la Ley 675 (Mayoría Simple, Calificada o Unanimidad).
                </p>
              </div>
              <button
                onClick={() => {
                  setFormVotacion((prev) => ({ ...prev, puntoOrdenDia: votaciones.length + 1 }));
                  setModalVotacionOpen(true);
                }}
                disabled={asambleaActiva.estado === 'FINALIZADA' || asambleaActiva.estado === 'CANCELADA'}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-xs disabled:opacity-50"
              >
                <Plus className="w-4 h-4" /> Crear Punto de Votación
              </button>
            </div>

            {votaciones.length === 0 ? (
              <div className="text-center py-12 border border-dashed border-border rounded-xl text-muted-foreground">
                <Vote className="w-10 h-10 mx-auto mb-2 opacity-40" />
                <p className="font-semibold text-sm">No hay votaciones registradas</p>
                <p className="text-xs mt-1">Abra un punto del orden del día para iniciar el escrutinio.</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-5">
                {votaciones.map((v) => {
                  const si = Number(v.votosSi) || 0;
                  const no = Number(v.votosNo) || 0;
                  const blanco = Number(v.votosBlanco) || 0;
                  const abst = Number(v.votosAbstencion) || 0;
                  const totalCoef = si + no + blanco + abst || 1;
                  const pctSi = ((si / totalCoef) * 100).toFixed(1);
                  const pctNo = ((no / totalCoef) * 100).toFixed(1);

                  return (
                    <div
                      key={v.idVotacion}
                      className="border border-border rounded-xl p-5 bg-card/60 hover:bg-card transition-all space-y-4"
                    >
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                        <div className="flex items-center gap-3">
                          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-bold bg-primary text-primary-foreground">Punto #{v.puntoOrdenDia}</span>
                          <h4 className="font-bold text-base">{v.titulo}</h4>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground font-medium">
                            {MAYORIAS[v.tipoMayoriaRequerida] || v.tipoMayoriaRequerida}
                          </span>
                          {v.estado === 'ABIERTA' ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30 animate-pulse">Votación Abierta</span>
                          ) : (
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium ${v.aprobada ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30' : 'bg-muted text-muted-foreground border border-border'}`}>
                              {v.aprobada ? '✓ APROBADA' : '✕ NO APROBADA'}
                            </span>
                          )}
                        </div>
                      </div>

                      {v.descripcion && (
                        <p className="text-xs text-muted-foreground">{v.descripcion}</p>
                      )}

                      {/* Escrutinio Visual */}
                      <div className="space-y-2 pt-2">
                        <div className="flex items-center justify-between text-xs font-semibold">
                          <span className="text-emerald-600 dark:text-emerald-400">SÍ: {si} ({pctSi}%)</span>
                          <span className="text-red-600 dark:text-red-400">NO: {no} ({pctNo}%)</span>
                        </div>
                        <div className="w-full bg-muted rounded-full h-3 flex overflow-hidden">
                          <div className="bg-emerald-500 h-full transition-all" style={{ width: `${pctSi}%` }} />
                          <div className="bg-red-500 h-full transition-all" style={{ width: `${pctNo}%` }} />
                        </div>
                        <div className="flex items-center justify-between text-[11px] text-muted-foreground pt-1">
                          <span>Blanco: {blanco} • Abstención: {abst}</span>
                          <span>Total Votos Emitidos: {v.totalVotos}</span>
                        </div>
                      </div>

                      {/* Acciones de Votación */}
                      {v.estado === 'ABIERTA' && (
                        <div className="flex items-center justify-end gap-2 pt-2 border-t border-border/40">
                          <button
                            onClick={() => {
                              setVotacionParaVotar(v);
                              setModalVotoOpen(true);
                            }}
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-xs"
                          >
                            <Vote className="w-3.5 h-3.5" /> Registrar Voto
                          </button>
                          <button
                            onClick={() => handleCerrarVotacion(v.idVotacion)}
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md border border-destructive/30 text-destructive hover:bg-destructive/10 transition-colors"
                          >
                            <Square className="w-3.5 h-3.5" /> Cerrar Votación
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB 5: ACTA OFICIAL (LEY 675) */}
      {tabActiva === 'actas' && (
        <div className="space-y-6">
          {!asambleaActiva ? (
            <div className="bg-card text-card-foreground border border-border rounded-xl p-8 text-center text-muted-foreground">
              <FileText className="w-10 h-10 mx-auto mb-2 opacity-40" />
              <p>Seleccione una asamblea del listado para gestionar o consultar su acta oficial.</p>
            </div>
          ) : loadingActa ? (
            <div className="bg-card text-card-foreground border border-border rounded-xl p-8 text-center text-muted-foreground">
              <RefreshCw className="w-6 h-6 animate-spin mx-auto mb-2 text-primary" />
              <p>Cargando información del acta oficial...</p>
            </div>
          ) : !actaData ? (
            <div className="bg-card text-card-foreground border border-border rounded-xl p-6 shadow-sm space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border/60">
                <div>
                  <h3 className="text-lg font-bold">Acta Oficial de la Asamblea</h3>
                  <p className="text-xs text-muted-foreground">
                    Asamblea #{asambleaActiva.idAsamblea}: {asambleaActiva.titulo}
                  </p>
                </div>
                <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30">Sin Acta Creada</span>
              </div>

              <div className="p-4 rounded-xl bg-muted/40 border border-border/60 text-sm space-y-2">
                <div className="flex items-center gap-2 font-semibold text-foreground">
                  <AlertCircle className="w-4 h-4 text-amber-500" />
                  Marco Legal — Artículo 47 Ley 675 de 2001
                </div>
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Las decisiones de la asamblea se harán constar en actas firmadas por el presidente y el secretario de la asamblea, así como por los miembros de la comisión verificadora de la redacción del acta. El administrador pondrá a disposición de los copropietarios copia completa del texto del acta dentro de un lapso no superior a veinte (20) días hábiles siguientes a la reunión.
                </p>
              </div>

              {/* Formulario de Creación de Borrador */}
              <form onSubmit={handleCrearBorradorActa} className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold mb-1">Número de Acta *</label>
                    <input
                      type="text"
                      required
                      className="w-full px-3 py-1.5 text-xs rounded-lg border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 shadow-xs"
                      value={formActa.numeroActa}
                      onChange={(e) => setFormActa({ ...formActa, numeroActa: e.target.value })}
                      placeholder="Ej. ACTA-001-2026"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold mb-1">Contenido / Texto del Acta *</label>
                  <textarea
                    required
                    rows={12}
                    className="w-full px-3 py-2 text-xs font-mono rounded-lg border border-input bg-background text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 shadow-xs"
                    value={formActa.contenidoTexto}
                    onChange={(e) => setFormActa({ ...formActa, contenidoTexto: e.target.value })}
                  />
                </div>

                <div className="flex justify-end">
                  <button
                    type="submit"
                    disabled={submittingActa}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-xs disabled:opacity-50"
                  >
                    <FileText className="w-4 h-4" />
                    {submittingActa ? 'Creando Borrador...' : 'Crear Borrador de Acta'}
                  </button>
                </div>
              </form>
            </div>
          ) : (
            <div className="space-y-6">
              {/* Tarjeta Principal de Estado del Acta */}
              <div className="bg-card text-card-foreground border border-border rounded-xl p-6 shadow-sm space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border/60">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                          ESTADOS_ACTA[actaData.estado]?.color || ''
                        }`}
                      >
                        {ESTADOS_ACTA[actaData.estado]?.label || actaData.estado}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        Asamblea #{actaData.idAsamblea}
                      </span>
                    </div>
                    <h3 className="text-xl font-bold flex items-center gap-2">
                      <FileText className="w-5 h-5 text-primary" />
                      Acta Nº {actaData.numeroActa}
                    </h3>
                  </div>

                  {/* Acciones de Transición de Estado */}
                  <div className="flex flex-wrap items-center gap-2">
                    {actaData.estado === 'BORRADOR' && (
                      <button
                        onClick={() => handleCambiarEstadoActa('EN_REVISION_COMISION')}
                        disabled={submittingActa}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-blue-500/30 text-blue-600 dark:text-blue-400 hover:bg-blue-500/10 transition-colors shadow-xs"
                      >
                        <UserCheck className="w-4 h-4" />
                        Enviar a Comisión Verificadora
                      </button>
                    )}

                    {actaData.estado === 'EN_REVISION_COMISION' && (
                      <>
                        <button
                          onClick={() => handleCambiarEstadoActa('BORRADOR')}
                          disabled={submittingActa}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                        >
                          Devolver a Borrador
                        </button>
                        <button
                          onClick={() => handleCambiarEstadoActa('APROBADA')}
                          disabled={submittingActa}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-primary/30 text-primary hover:bg-primary/10 transition-colors shadow-xs"
                        >
                          <CheckCircle2 className="w-4 h-4" />
                          Aprobar por Comisión
                        </button>
                      </>
                    )}

                    {actaData.estado === 'APROBADA' && (
                      <>
                        <button
                          onClick={() => handleCambiarEstadoActa('EN_REVISION_COMISION')}
                          disabled={submittingActa}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                        >
                          Devolver a Revisión
                        </button>
                        <button
                          onClick={() => handleCambiarEstadoActa('PUBLICADA_OFICIAL')}
                          disabled={submittingActa || !puedePublicarOficial}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 transition-colors shadow-xs disabled:opacity-50"
                          title={
                            !puedePublicarOficial
                              ? 'Requiere asamblea finalizada, votos cerrados y documento firmado adjunto'
                              : 'Publicar oficialmente a todos los copropietarios'
                          }
                        >
                          <ShieldCheck className="w-4 h-4" />
                          Publicar Oficialmente (Art. 47)
                        </button>
                      </>
                    )}

                    {actaData.estado === 'PUBLICADA_OFICIAL' && (
                      <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30">
                        <Lock className="w-3.5 h-3.5" />
                        Oficial e Inmutable
                      </span>
                    )}
                  </div>
                </div>

                {/* Checklist de Requisitos de Publicación si está en APROBADA o EN_REVISION_COMISION */}
                {(actaData.estado === 'APROBADA' || actaData.estado === 'EN_REVISION_COMISION') && (
                  <div className="p-4 rounded-xl bg-muted/40 border border-border/60 text-xs space-y-2">
                    <span className="font-semibold text-foreground uppercase tracking-wider block">
                      Condiciones Previas para Publicación Oficial (Art. 47 Ley 675):
                    </span>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 pt-1">
                      <div className="flex items-center gap-2">
                        {asambleaFinalizada ? (
                          <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                        ) : (
                          <XCircle className="w-4 h-4 text-amber-500 shrink-0" />
                        )}
                        <span>
                          Asamblea Finalizada: <strong>{asambleaActiva.estado}</strong>
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        {todasVotacionesCerradas ? (
                          <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                        ) : (
                          <XCircle className="w-4 h-4 text-amber-500 shrink-0" />
                        )}
                        <span>
                          Votaciones Cerradas ({votaciones.filter((v) => v.estado === 'ABIERTA').length} abiertas)
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        {tieneDocumentoAdjunto ? (
                          <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                        ) : (
                          <XCircle className="w-4 h-4 text-amber-500 shrink-0" />
                        )}
                        <span>Documento Firmado Adjunto</span>
                      </div>
                    </div>
                  </div>
                )}

                {/* Métricas de Gobernanza / Snapshot de Asamblea */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
                  <div className="p-3 bg-muted/30 rounded-lg">
                    <span className="text-muted-foreground block">Total Asistentes</span>
                    <span className="text-base font-bold text-foreground">
                      {actaData.totalAsistentes ?? asistencias.length}
                    </span>
                  </div>
                  <div className="p-3 bg-muted/30 rounded-lg">
                    <span className="text-muted-foreground block">Coeficiente Presente</span>
                    <span className="text-base font-bold text-foreground">
                      {Number(actaData.totalCoeficienteAsistentes ?? quorumLive?.totalCoeficienteRegistrado ?? 0).toFixed(4)}
                    </span>
                  </div>
                  <div className="p-3 bg-muted/30 rounded-lg">
                    <span className="text-muted-foreground block">Poderes Aprobados</span>
                    <span className="text-base font-bold text-foreground">
                      {actaData.totalPoderesAprobados ?? poderes.filter((p) => p.estado === 'APROBADO').length}
                    </span>
                  </div>
                  <div className="p-3 bg-muted/30 rounded-lg">
                    <span className="text-muted-foreground block">Votaciones Realizadas</span>
                    <span className="text-base font-bold text-foreground">
                      {actaData.totalVotaciones ?? votaciones.length}
                    </span>
                  </div>
                </div>
              </div>

              {/* Sección de Documento Firmado (F10-01) */}
              <div className="bg-card text-card-foreground border border-border rounded-xl p-6 shadow-sm space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/60">
                  <div className="flex items-center gap-2">
                    <FileCheck className="w-5 h-5 text-primary" />
                    <h4 className="font-bold text-base">Documento Firmado Oficial (PDF)</h4>
                  </div>
                  {tieneDocumentoAdjunto && (
                    <button
                      onClick={handleDescargarDocumentoActa}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-primary/30 text-primary hover:bg-primary/10 transition-colors shadow-xs"
                    >
                      <Download className="w-4 h-4" />
                      Descargar Documento Firmado
                    </button>
                  )}
                </div>

                {tieneDocumentoAdjunto ? (
                  <div className="p-4 rounded-xl border border-emerald-500/20 bg-emerald-500/5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                    <div>
                      <p className="font-semibold text-emerald-800 dark:text-emerald-300">
                        Documento oficial adjunto e integrado con F10-01 Gestión Documental
                      </p>
                      <p className="text-muted-foreground">
                        {actaData.idDocumento
                          ? `ID Documento: #${actaData.idDocumento} — Almacenamiento seguro SHA-256`
                          : `URL: ${actaData.documentoFirmadoUrl}`}
                      </p>
                    </div>
                    {actaData.estado !== 'PUBLICADA_OFICIAL' && (
                      <label className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-md border border-border bg-card text-foreground hover:bg-muted transition-colors cursor-pointer">
                        <Upload className="w-3.5 h-3.5" />
                        {uploadingDoc ? 'Subiendo...' : 'Reemplazar Archivo'}
                        <input
                          type="file"
                          accept=".pdf,.docx,.doc"
                          className="hidden"
                          disabled={uploadingDoc}
                          onChange={(e) => {
                            if (e.target.files?.[0]) handleSubirDocumentoActa(e.target.files[0]);
                          }}
                        />
                      </label>
                    )}
                  </div>
                ) : (
                  <div className="p-6 rounded-xl border border-dashed border-border flex flex-col items-center justify-center text-center space-y-3">
                    <Upload className="w-8 h-8 text-muted-foreground opacity-50" />
                    <div>
                      <p className="text-sm font-semibold">Adjuntar Acta Firmada Escaneada o Digital</p>
                      <p className="text-xs text-muted-foreground">
                        Formato recomendado: PDF con firmas del presidente, secretario y comisión.
                      </p>
                    </div>
                    <label className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors shadow-xs cursor-pointer">
                      <Upload className="w-4 h-4" />
                      {uploadingDoc ? 'Subiendo...' : 'Seleccionar Archivo PDF'}
                      <input
                        type="file"
                        accept=".pdf,.docx,.doc"
                        className="hidden"
                        disabled={uploadingDoc}
                        onChange={(e) => {
                          if (e.target.files?.[0]) handleSubirDocumentoActa(e.target.files[0]);
                        }}
                      />
                    </label>
                  </div>
                )}
              </div>

              {/* Redacción y Texto del Acta */}
              <div className="bg-card text-card-foreground border border-border rounded-xl p-6 shadow-sm space-y-4">
                <div className="flex items-center justify-between pb-2 border-b border-border/60">
                  <div className="flex items-center gap-2">
                    <Edit3 className="w-5 h-5 text-primary" />
                    <h4 className="font-bold text-base">Cuerpo del Acta</h4>
                  </div>
                  {actaData.estado !== 'PUBLICADA_OFICIAL' && !editandoActa && (
                    <button
                      onClick={() => setEditandoActa(true)}
                      className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-lg border border-primary text-primary hover:bg-primary/10 transition-colors"
                    >
                      <Edit3 className="w-3.5 h-3.5" /> Editar Texto
                    </button>
                  )}
                  {editandoActa && (
                    <div className="flex gap-2">
                      <button
                        onClick={() => {
                          setFormActa({
                            numeroActa: actaData.numeroActa || '',
                            contenidoTexto: actaData.contenidoTexto || '',
                          });
                          setEditandoActa(false);
                        }}
                        className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-lg text-muted-foreground hover:bg-muted transition-colors"
                      >
                        Cancelar
                      </button>
                      <button
                        onClick={handleGuardarContenidoActa}
                        disabled={submittingActa}
                        className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
                      >
                        <Save className="w-3.5 h-3.5" />
                        {submittingActa ? 'Guardando...' : 'Guardar Cambios'}
                      </button>
                    </div>
                  )}
                </div>

                {editandoActa ? (
                  <div className="space-y-3">
                    <div>
                      <label className="block text-xs font-semibold mb-1">Número de Acta</label>
                      <input
                        type="text"
                        className="w-full max-w-xs px-3 py-1.5 text-xs rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                        value={formActa.numeroActa}
                        onChange={(e) => setFormActa({ ...formActa, numeroActa: e.target.value })}
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold mb-1">Texto Completo</label>
                      <textarea
                        rows={14}
                        className="w-full px-3 py-2 text-xs font-mono leading-relaxed rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                        value={formActa.contenidoTexto}
                        onChange={(e) => setFormActa({ ...formActa, contenidoTexto: e.target.value })}
                      />
                    </div>
                  </div>
                ) : (
                  <div className="p-4 rounded-xl bg-muted/20 border border-border/60 text-xs font-mono whitespace-pre-wrap leading-relaxed max-h-[500px] overflow-y-auto">
                    {actaData.contenidoTexto || 'Sin contenido registrado.'}
                  </div>
                )}
              </div>

              {/* Decisiones y Votaciones Consolidadas */}
              {actaData.votaciones && actaData.votaciones.length > 0 && (
                <div className="bg-card text-card-foreground border border-border rounded-xl p-6 shadow-sm space-y-4">
                  <div className="flex items-center gap-2 pb-2 border-b border-border/60">
                    <Vote className="w-5 h-5 text-primary" />
                    <h4 className="font-bold text-base">Escrutinio y Resoluciones en el Acta</h4>
                  </div>
                  <div className="divide-y border-border/60">
                    {actaData.votaciones.map((v) => (
                      <div key={v.idVotacion} className="py-3 text-xs space-y-1">
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-sm">
                            Punto #{v.puntoOrdenDia}: {v.titulo}
                          </span>
                          <span
                            className={`px-2 py-0.5 rounded-full font-semibold ${
                              v.aprobada
                                ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400'
                                : 'bg-red-500/15 text-red-700 dark:text-red-400'
                            }`}
                          >
                            {v.aprobada ? 'APROBADA' : 'NO APROBADA'}
                          </span>
                        </div>
                        <p className="text-muted-foreground">{v.descripcion}</p>
                        <div className="flex gap-4 pt-1 text-muted-foreground">
                          <span>SÍ: <strong>{v.votosSi}</strong> ({Number(v.coeficienteSi || 0).toFixed(2)}%)</span>
                          <span>NO: <strong>{v.votosNo}</strong> ({Number(v.coeficienteNo || 0).toFixed(2)}%)</span>
                          <span>Blanco: <strong>{v.votosBlanco}</strong></span>
                          <span>Abstención: <strong>{v.votosAbstencion}</strong></span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* MODAL 1: CONVOCAR ASAMBLEA */}
      <Modal
        isOpen={modalConvocarOpen}
        onClose={() => setModalConvocarOpen(false)}
        title="Convocar Asamblea de Copropietarios"
      >
        <form onSubmit={handleConvocar} className="space-y-4 text-sm">
          <div>
            <label className="block text-xs font-semibold mb-1">Título de la Convocatoria *</label>
            <input
              type="text"
              required
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              placeholder="Ej. Asamblea General Ordinaria 2026"
              value={formConvocar.titulo}
              onChange={(e) => setFormConvocar({ ...formConvocar, titulo: e.target.value })}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold mb-1">Tipo de Asamblea *</label>
              <select
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                value={formConvocar.tipo}
                onChange={(e) => setFormConvocar({ ...formConvocar, tipo: e.target.value })}
              >
                <option value="ORDINARIA">Ordinaria</option>
                <option value="EXTRAORDINARIA">Extraordinaria</option>
                <option value="SEGUNDA_CONVOCATORIA">Segunda Convocatoria</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold mb-1">Modalidad *</label>
              <select
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                value={formConvocar.modalidad}
                onChange={(e) => setFormConvocar({ ...formConvocar, modalidad: e.target.value })}
              >
                <option value="MIXTA">Mixta (Presencial y Virtual)</option>
                <option value="PRESENCIAL">Presencial</option>
                <option value="VIRTUAL">Virtual</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold mb-1">1ra Convocatoria (Fecha y Hora) *</label>
              <input
                type="datetime-local"
                required
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                value={formConvocar.fechaHoraPrimeraConv}
                onChange={(e) => setFormConvocar({ ...formConvocar, fechaHoraPrimeraConv: e.target.value })}
              />
            </div>
            <div>
              <label className="block text-xs font-semibold mb-1">2da Convocatoria (Opcional)</label>
              <input
                type="datetime-local"
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                value={formConvocar.fechaHoraSegundaConv}
                onChange={(e) => setFormConvocar({ ...formConvocar, fechaHoraSegundaConv: e.target.value })}
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Lugar o Enlace Virtual *</label>
            <input
              type="text"
              required
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              placeholder="Ej. Salón Comunal Bloque 1 / Enlace Zoom o Teams"
              value={formConvocar.lugarOEnlace}
              onChange={(e) => setFormConvocar({ ...formConvocar, lugarOEnlace: e.target.value })}
            />
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Orden del Día *</label>
            <textarea
              required
              rows={4}
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              placeholder="1. Verificación del quórum&#10;2. Elección presidente y secretario&#10;3. Aprobación de estados financieros..."
              value={formConvocar.ordenDelDia}
              onChange={(e) => setFormConvocar({ ...formConvocar, ordenDelDia: e.target.value })}
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setModalConvocarOpen(false)}
              className="px-4 py-2 text-sm font-medium rounded-lg text-muted-foreground hover:bg-muted transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {submitting ? 'Guardando...' : 'Publicar Convocatoria'}
            </button>
          </div>
        </form>
      </Modal>

      {/* MODAL 2: REGISTRAR ASISTENCIA */}
      <Modal
        isOpen={modalAsistenciaOpen}
        onClose={() => setModalAsistenciaOpen(false)}
        title="Registrar Asistencia en Sala"
      >
        <form onSubmit={handleRegistrarAsistencia} className="space-y-4 text-sm">
          <div>
            <label className="block text-xs font-semibold mb-1">Unidad / Apartamento *</label>
            <select
              required
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              value={formAsistencia.idUnidad}
              onChange={(e) => setFormAsistencia({ ...formAsistencia, idUnidad: e.target.value })}
            >
              <option value="">Seleccione apartamento...</option>
              {unidades.map((u) => (
                <option key={u.idUnidad || u.id} value={u.idUnidad || u.id}>
                  {u.identificador || `Unidad ${u.idUnidad || u.id}`} {u.coeficienteCopropiedad ? `(Coef: ${u.coeficienteCopropiedad})` : ''}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Persona Asistente *</label>
            <select
              required
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              value={formAsistencia.idPersonaAsistente}
              onChange={(e) => setFormAsistencia({ ...formAsistencia, idPersonaAsistente: e.target.value })}
            >
              <option value="">Seleccione persona...</option>
              {personas.map((p) => (
                <option key={p.idPersona} value={p.idPersona}>
                  {p.primerNombre} {p.primerApellido} ({p.numeroDocumento || p.email})
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold mb-1">Calidad del Asistente *</label>
              <select
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                value={formAsistencia.esPropietarioDirecto}
                onChange={(e) => setFormAsistencia({ ...formAsistencia, esPropietarioDirecto: e.target.value })}
              >
                <option value="S">Propietario Directo</option>
                <option value="N">Apoderado / Representante</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold mb-1">Coeficiente (Opcional)</label>
              <input
                type="number"
                step="0.0001"
                placeholder="Auto-calculado de unidad"
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                value={formAsistencia.coeficientePonderado}
                onChange={(e) => setFormAsistencia({ ...formAsistencia, coeficientePonderado: e.target.value })}
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setModalAsistenciaOpen(false)}
              className="px-4 py-2 text-sm font-medium rounded-lg text-muted-foreground hover:bg-muted transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {submitting ? 'Registrando...' : 'Registrar Ingreso'}
            </button>
          </div>
        </form>
      </Modal>

      {/* MODAL 3: RADICAR PODER */}
      <Modal
        isOpen={modalPoderOpen}
        onClose={() => setModalPoderOpen(false)}
        title="Radicar Poder de Representación"
      >
        <form onSubmit={handleRadicarPoder} className="space-y-4 text-sm">
          <div>
            <label className="block text-xs font-semibold mb-1">Unidad / Apartamento *</label>
            <select
              required
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              value={formPoder.idUnidad}
              onChange={(e) => setFormPoder({ ...formPoder, idUnidad: e.target.value })}
            >
              <option value="">Seleccione apartamento...</option>
              {unidades.map((u) => (
                <option key={u.idUnidad || u.id} value={u.idUnidad || u.id}>
                  {u.identificador || `Unidad ${u.idUnidad || u.id}`}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Propietario Otorgante *</label>
            <select
              required
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              value={formPoder.idPersonaPropietario}
              onChange={(e) => setFormPoder({ ...formPoder, idPersonaPropietario: e.target.value })}
            >
              <option value="">Seleccione propietario...</option>
              {personas.map((p) => (
                <option key={p.idPersona} value={p.idPersona}>
                  {p.primerNombre} {p.primerApellido} ({p.numeroDocumento})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Apoderado Receptor *</label>
            <select
              required
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              value={formPoder.idPersonaApoderado}
              onChange={(e) => setFormPoder({ ...formPoder, idPersonaApoderado: e.target.value })}
            >
              <option value="">Seleccione apoderado...</option>
              {personas.map((p) => (
                <option key={p.idPersona} value={p.idPersona}>
                  {p.primerNombre} {p.primerApellido} ({p.numeroDocumento})
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Enlace a Documento / Carta Firmada (Opcional)</label>
            <input
              type="url"
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              placeholder="https://saed-docs.../carta-poder.pdf"
              value={formPoder.documentoPoderUrl}
              onChange={(e) => setFormPoder({ ...formPoder, documentoPoderUrl: e.target.value })}
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setModalPoderOpen(false)}
              className="px-4 py-2 text-sm font-medium rounded-lg text-muted-foreground hover:bg-muted transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {submitting ? 'Radicando...' : 'Radicar Poder'}
            </button>
          </div>
        </form>
      </Modal>

      {/* MODAL 4: CREAR PUNTO DE VOTACIÓN */}
      <Modal
        isOpen={modalVotacionOpen}
        onClose={() => setModalVotacionOpen(false)}
        title="Crear Punto de Votación"
      >
        <form onSubmit={handleCrearVotacion} className="space-y-4 text-sm">
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-semibold mb-1">Punto del Orden *</label>
              <input
                type="number"
                required
                min={1}
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                value={formVotacion.puntoOrdenDia}
                onChange={(e) => setFormVotacion({ ...formVotacion, puntoOrdenDia: e.target.value })}
              />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-semibold mb-1">Título del Punto *</label>
              <input
                type="text"
                required
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
                placeholder="Ej. Aprobación del Presupuesto 2027"
                value={formVotacion.titulo}
                onChange={(e) => setFormVotacion({ ...formVotacion, titulo: e.target.value })}
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Tipo de Mayoría Requerida *</label>
            <select
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              value={formVotacion.tipoMayoriaRequerida}
              onChange={(e) => setFormVotacion({ ...formVotacion, tipoMayoriaRequerida: e.target.value })}
            >
              <option value="SIMPLE_50_MAS_1">Mayoría Simple (50% + 1 coeficientes presentes)</option>
              <option value="CALIFICADA_70_PCT">Mayoría Calificada (70% coeficiente total de la copropiedad)</option>
              <option value="UNANIMIDAD_100_PCT">Unanimidad (100% de los coeficientes)</option>
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Descripción / Texto de la Proposición</label>
            <textarea
              rows={3}
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              placeholder="Detalle o texto de la decisión a someter a votación..."
              value={formVotacion.descripcion}
              onChange={(e) => setFormVotacion({ ...formVotacion, descripcion: e.target.value })}
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setModalVotacionOpen(false)}
              className="px-4 py-2 text-sm font-medium rounded-lg text-muted-foreground hover:bg-muted transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {submitting ? 'Abriendo...' : 'Abrir Votación'}
            </button>
          </div>
        </form>
      </Modal>

      {/* MODAL 5: EMITIR VOTO DE UNIDAD */}
      <Modal
        isOpen={modalVotoOpen}
        onClose={() => setModalVotoOpen(false)}
        title={`Emitir Voto - ${votacionParaVotar?.titulo || ''}`}
      >
        <form onSubmit={handleEmitirVoto} className="space-y-4 text-sm">
          <div>
            <label className="block text-xs font-semibold mb-1">Unidad Votante *</label>
            <select
              required
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              value={formVoto.idUnidad}
              onChange={(e) => setFormVoto({ ...formVoto, idUnidad: e.target.value })}
            >
              <option value="">Seleccione apartamento...</option>
              {unidades.map((u) => (
                <option key={u.idUnidad || u.id} value={u.idUnidad || u.id}>
                  {u.identificador || `Unidad ${u.idUnidad || u.id}`}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Persona que Vota (Propietario o Apoderado) *</label>
            <select
              required
              className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-background focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              value={formVoto.idPersonaVotante}
              onChange={(e) => setFormVoto({ ...formVoto, idPersonaVotante: e.target.value })}
            >
              <option value="">Seleccione persona...</option>
              {personas.map((p) => (
                <option key={p.idPersona} value={p.idPersona}>
                  {p.primerNombre} {p.primerApellido}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Sentido del Voto *</label>
            <div className="grid grid-cols-4 gap-2 pt-1">
              {['SI', 'NO', 'BLANCO', 'ABSTENCION'].map((opt) => (
                <button
                  key={opt}
                  type="button"
                  onClick={() => setFormVoto({ ...formVoto, opcionVoto: opt })}
                  className={`px-3 py-2 text-sm font-semibold rounded-lg border transition-colors ${
                    formVoto.opcionVoto === opt
                      ? opt === 'SI'
                        ? 'bg-emerald-600 text-white border-emerald-600'
                        : opt === 'NO'
                        ? 'bg-red-600 text-white border-red-600'
                        : 'bg-primary text-primary-foreground border-primary'
                      : 'border-border bg-background text-foreground hover:bg-muted'
                  }`}
                >
                  {opt}
                </button>
              ))}
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-4">
            <button
              type="button"
              onClick={() => setModalVotoOpen(false)}
              className="px-4 py-2 text-sm font-medium rounded-lg text-muted-foreground hover:bg-muted transition-colors"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 text-sm font-medium rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition-colors disabled:opacity-50"
            >
              {submitting ? 'Registrando...' : 'Confirmar Voto'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
