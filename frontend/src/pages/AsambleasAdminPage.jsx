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
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader';
import { Modal } from '../components/ui/Modal';
import { useFetch } from '../lib/hooks';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { toast } from 'sonner';

const ESTADOS_ASAMBLEA = {
  BORRADOR: { label: 'Borrador', color: 'bg-neutral/20 text-neutral-content' },
  CONVOCADA: { label: 'Convocada', color: 'bg-blue-500/15 text-blue-700 dark:text-blue-400 border border-blue-500/30' },
  EN_CURSO: { label: 'En Curso', color: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border border-emerald-500/30' },
  EN_RECESO: { label: 'En Receso', color: 'bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30' },
  FINALIZADA: { label: 'Finalizada', color: 'bg-gray-500/15 text-gray-700 dark:text-gray-400' },
  CANCELADA: { label: 'Cancelada', color: 'bg-red-500/15 text-red-700 dark:text-red-400' },
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
    ]).finally(() => {
      setTimeout(() => setSincronizando(false), 400);
      toast.success('Datos de asambleas sincronizados');
    });
  }, [refetchAsambleas, refetchQuorum, refetchAsistencias, refetchPoderes, refetchVotaciones]);

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
            className="btn btn-outline btn-sm gap-2"
            title="Sincronizar quórum y asambleas"
          >
            <RefreshCw className={`w-4 h-4 ${sincronizando ? 'animate-spin text-primary' : ''}`} />
            Sincronizar
          </button>
          <button
            onClick={() => setModalConvocarOpen(true)}
            className="btn btn-primary btn-sm gap-2 shadow-sm"
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
              className="select select-bordered select-sm w-full sm:w-72"
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
                className="btn btn-sm btn-success gap-1.5"
                title="Iniciar sesión formal"
              >
                <Play className="w-3.5 h-3.5" /> Iniciar
              </button>
            )}

            {asambleaActiva && asambleaActiva.estado === 'EN_CURSO' && (
              <button
                onClick={() => handleCambiarEstado(asambleaActiva.idAsamblea, 'FINALIZADA')}
                className="btn btn-sm btn-outline btn-error gap-1.5"
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
                className="select select-bordered select-xs"
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
              <Calendar className="w-10 h-10 text-muted-foreground mx-auto mb-2" />
              <p className="font-semibold text-sm">No hay asambleas registradas</p>
              <p className="text-xs text-muted-foreground mt-1">Convoque la primera asamblea para comenzar.</p>
              <button
                onClick={() => setModalConvocarOpen(true)}
                className="btn btn-primary btn-xs mt-4"
              >
                Convocar Ahora
              </button>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="table table-sm w-full">
                <thead>
                  <tr className="border-b border-border text-muted-foreground">
                    <th>Título & Convocatoria</th>
                    <th>Tipo</th>
                    <th>Modalidad</th>
                    <th>1ra Convocatoria</th>
                    <th>Quórum Alcanzado</th>
                    <th>Estado</th>
                    <th className="text-right">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {asambleasFiltradas.map((a) => {
                    const estadoMeta = ESTADOS_ASAMBLEA[a.estado] || { label: a.estado, color: 'bg-neutral' };
                    return (
                      <tr key={a.idAsamblea} className="hover:bg-muted/40 transition-colors">
                        <td>
                          <div className="font-semibold text-sm">{a.titulo}</div>
                          <div className="text-xs text-muted-foreground">Convocatoria #{a.convocatoriaNumero || 1}</div>
                        </td>
                        <td>
                          <span className="badge badge-sm badge-outline font-medium">{a.tipo}</span>
                        </td>
                        <td>
                          <span className="text-xs font-medium flex items-center gap-1">
                            {a.modalidad === 'VIRTUAL' && <Video className="w-3.5 h-3.5 text-blue-500" />}
                            {a.modalidad === 'PRESENCIAL' && <Users className="w-3.5 h-3.5 text-emerald-500" />}
                            {a.modalidad === 'MIXTA' && <Building className="w-3.5 h-3.5 text-purple-500" />}
                            {a.modalidad}
                          </span>
                        </td>
                        <td className="text-xs">
                          {a.fechaHoraPrimeraConv ? new Date(a.fechaHoraPrimeraConv).toLocaleString('es-CO') : 'Sin fecha'}
                        </td>
                        <td>
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
                        <td>
                          <span className={`badge badge-sm ${estadoMeta.color}`}>{estadoMeta.label}</span>
                        </td>
                        <td className="text-right">
                          <button
                            onClick={() => {
                              setAsambleaSeleccionadaId(a.idAsamblea);
                              setTabActiva('quorum');
                            }}
                            className="btn btn-xs btn-outline btn-primary gap-1"
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
                  className="btn btn-primary btn-sm gap-2"
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
                            <span className="badge badge-xs badge-outline">
                              {asist.esPropietarioDirecto === 'S' ? 'Propietario Directo' : 'Apoderado'}
                            </span>
                          </td>
                          <td className="font-semibold">{asist.coeficientePonderado || '0.00'}</td>
                          <td className="text-xs">
                            {asist.horaRegistro ? new Date(asist.horaRegistro).toLocaleTimeString('es-CO') : '-'}
                          </td>
                          <td>
                            {asist.horaRetiro ? (
                              <span className="badge badge-xs badge-ghost">Retirado ({new Date(asist.horaRetiro).toLocaleTimeString('es-CO')})</span>
                            ) : (
                              <span className="badge badge-xs bg-emerald-500/20 text-emerald-700 dark:text-emerald-400">Presente</span>
                            )}
                          </td>
                          <td className="text-right">
                            {!asist.horaRetiro && (
                              <button
                                onClick={() => handleRetirarAsistencia(asist.idUnidad)}
                                className="btn btn-xs btn-outline btn-error"
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
              className="btn btn-primary btn-sm gap-2"
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
              <table className="table table-sm w-full">
                <thead>
                  <tr className="border-b border-border text-muted-foreground">
                    <th>Unidad</th>
                    <th>Propietario Otorgante</th>
                    <th>Apoderado Receptor</th>
                    <th>Soporte / Documento</th>
                    <th>Fecha Registro</th>
                    <th>Estado</th>
                    <th className="text-right">Decisión</th>
                  </tr>
                </thead>
                <tbody>
                  {poderes.map((p) => (
                    <tr key={p.idPoder} className="hover:bg-muted/40 transition-colors">
                      <td className="font-bold">{p.unidadIdentificador || `Unidad ${p.idUnidad}`}</td>
                      <td>
                        <div className="font-medium text-sm">{p.nombrePropietario}</div>
                        <div className="text-xs text-muted-foreground font-mono">{p.documentoPropietario}</div>
                      </td>
                      <td>
                        <div className="font-medium text-sm">{p.nombreApoderado}</div>
                        <div className="text-xs text-muted-foreground font-mono">{p.documentoApoderado}</div>
                      </td>
                      <td>
                        {p.documentoPoderUrl ? (
                          <a
                            href={p.documentoPoderUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="btn btn-xs btn-outline gap-1 text-primary"
                          >
                            <ExternalLink className="w-3 h-3" /> Ver Carta
                          </a>
                        ) : (
                          <span className="text-xs text-muted-foreground">Físico / Sin archivo</span>
                        )}
                      </td>
                      <td className="text-xs">
                        {p.fechaRegistro ? new Date(p.fechaRegistro).toLocaleDateString('es-CO') : '-'}
                      </td>
                      <td>
                        {p.estado === 'PENDIENTE_REVISION' && (
                          <span className="badge badge-sm badge-warning">Por Validar</span>
                        )}
                        {p.estado === 'APROBADO' && (
                          <span className="badge badge-sm badge-success">Aprobado</span>
                        )}
                        {p.estado === 'RECHAZADO' && (
                          <span className="badge badge-sm badge-error">Rechazado</span>
                        )}
                      </td>
                      <td className="text-right">
                        {p.estado === 'PENDIENTE_REVISION' && (
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              onClick={() => handleDecidirPoder(p.idPoder, 'APROBADO')}
                              className="btn btn-xs btn-success gap-1"
                            >
                              <Check className="w-3 h-3" /> Aprobar
                            </button>
                            <button
                              onClick={() => handleDecidirPoder(p.idPoder, 'RECHAZADO')}
                              className="btn btn-xs btn-error btn-outline gap-1"
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
                className="btn btn-primary btn-sm gap-2"
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
                          <span className="badge badge-primary font-bold">Punto #{v.puntoOrdenDia}</span>
                          <h4 className="font-bold text-base">{v.titulo}</h4>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-muted-foreground font-medium">
                            {MAYORIAS[v.tipoMayoriaRequerida] || v.tipoMayoriaRequerida}
                          </span>
                          {v.estado === 'ABIERTA' ? (
                            <span className="badge badge-sm badge-success animate-pulse">Votación Abierta</span>
                          ) : (
                            <span className={`badge badge-sm ${v.aprobada ? 'badge-success' : 'badge-neutral'}`}>
                              {v.aprobada ? '✅ APROBADA' : '❌ NO APROBADA'}
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
                            className="btn btn-xs btn-primary gap-1"
                          >
                            <Vote className="w-3.5 h-3.5" /> Registrar Voto
                          </button>
                          <button
                            onClick={() => handleCerrarVotacion(v.idVotacion)}
                            className="btn btn-xs btn-outline btn-error gap-1"
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
              className="input input-bordered input-sm w-full"
              placeholder="Ej. Asamblea General Ordinaria 2026"
              value={formConvocar.titulo}
              onChange={(e) => setFormConvocar({ ...formConvocar, titulo: e.target.value })}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold mb-1">Tipo de Asamblea *</label>
              <select
                className="select select-bordered select-sm w-full"
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
                className="select select-bordered select-sm w-full"
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
                className="input input-bordered input-sm w-full"
                value={formConvocar.fechaHoraPrimeraConv}
                onChange={(e) => setFormConvocar({ ...formConvocar, fechaHoraPrimeraConv: e.target.value })}
              />
            </div>
            <div>
              <label className="block text-xs font-semibold mb-1">2da Convocatoria (Opcional)</label>
              <input
                type="datetime-local"
                className="input input-bordered input-sm w-full"
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
              className="input input-bordered input-sm w-full"
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
              className="textarea textarea-bordered textarea-sm w-full"
              placeholder="1. Verificación del quórum&#10;2. Elección presidente y secretario&#10;3. Aprobación de estados financieros..."
              value={formConvocar.ordenDelDia}
              onChange={(e) => setFormConvocar({ ...formConvocar, ordenDelDia: e.target.value })}
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setModalConvocarOpen(false)}
              className="btn btn-ghost btn-sm"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="btn btn-primary btn-sm"
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
              className="select select-bordered select-sm w-full"
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
              className="select select-bordered select-sm w-full"
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
                className="select select-bordered select-sm w-full"
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
                className="input input-bordered input-sm w-full"
                value={formAsistencia.coeficientePonderado}
                onChange={(e) => setFormAsistencia({ ...formAsistencia, coeficientePonderado: e.target.value })}
              />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setModalAsistenciaOpen(false)}
              className="btn btn-ghost btn-sm"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="btn btn-primary btn-sm"
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
              className="select select-bordered select-sm w-full"
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
              className="select select-bordered select-sm w-full"
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
              className="select select-bordered select-sm w-full"
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
              className="input input-bordered input-sm w-full"
              placeholder="https://saed-docs.../carta-poder.pdf"
              value={formPoder.documentoPoderUrl}
              onChange={(e) => setFormPoder({ ...formPoder, documentoPoderUrl: e.target.value })}
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setModalPoderOpen(false)}
              className="btn btn-ghost btn-sm"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="btn btn-primary btn-sm"
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
                className="input input-bordered input-sm w-full"
                value={formVotacion.puntoOrdenDia}
                onChange={(e) => setFormVotacion({ ...formVotacion, puntoOrdenDia: e.target.value })}
              />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-semibold mb-1">Título del Punto *</label>
              <input
                type="text"
                required
                className="input input-bordered input-sm w-full"
                placeholder="Ej. Aprobación del Presupuesto 2027"
                value={formVotacion.titulo}
                onChange={(e) => setFormVotacion({ ...formVotacion, titulo: e.target.value })}
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold mb-1">Tipo de Mayoría Requerida *</label>
            <select
              className="select select-bordered select-sm w-full"
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
              className="textarea textarea-bordered textarea-sm w-full"
              placeholder="Detalle o texto de la decisión a someter a votación..."
              value={formVotacion.descripcion}
              onChange={(e) => setFormVotacion({ ...formVotacion, descripcion: e.target.value })}
            />
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={() => setModalVotacionOpen(false)}
              className="btn btn-ghost btn-sm"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="btn btn-primary btn-sm"
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
              className="select select-bordered select-sm w-full"
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
              className="select select-bordered select-sm w-full"
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
                  className={`btn btn-sm ${formVoto.opcionVoto === opt ? (opt === 'SI' ? 'btn-success' : opt === 'NO' ? 'btn-error' : 'btn-primary') : 'btn-outline'}`}
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
              className="btn btn-ghost btn-sm"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="btn btn-primary btn-sm"
            >
              {submitting ? 'Registrando...' : 'Confirmar Voto'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
