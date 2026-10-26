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
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Button } from '../components/ui/button.tsx';
import { Modal } from '../components/ui/Modal';
import { useFetch } from '../lib/hooks';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { BASE_URL } from '../lib/api.js';
import { TOKEN_KEY } from '../lib/storage.js';
import { toast } from 'sonner';

const ESTADOS_ASAMBLEA = {
  BORRADOR: { label: 'Borrador', color: 'bg-surface-dim text-text-muted border border-border' },
  CONVOCADA: { label: 'Convocada', color: 'bg-tint-blue-bg text-tint-blue-fg border border-tint-blue-fg/25' },
  EN_CURSO: { label: 'En Curso', color: 'bg-tint-green-bg text-tint-green-fg border border-tint-green-fg/25' },
  EN_RECESO: { label: 'En Receso', color: 'bg-tint-amber-bg text-tint-amber-fg border border-tint-amber-fg/25' },
  FINALIZADA: { label: 'Finalizada', color: 'bg-surface-dim text-text-secondary border border-border' },
  CANCELADA: { label: 'Cancelada', color: 'bg-tint-red-bg text-tint-red-fg border border-tint-red-fg/25' },
};

const MODALIDADES = {
  PRESENCIAL: { label: 'Presencial', icon: Building },
  VIRTUAL: { label: 'Virtual', icon: Video },
  MIXTA: { label: 'Mixta', icon: Users },
};

export default function ResAsambleasPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();
  const { user } = useAuth();
  const [tabActiva, setTabActiva] = useState('proximas');
  const [asambleaSeleccionadaId, setAsambleaSeleccionadaId] = useState(null);
  const [modalPoderOpen, setModalPoderOpen] = useState(false);
  const [submittingPoder, setSubmittingPoder] = useState(false);
  const [votingId, setVotingId] = useState(null);
  const [votoExitosoMap, setVotoExitosoMap] = useState({});
  // Unit the resident votes for (own unit or a unit represented through an approved proxy).
  const [unidadVotoSel, setUnidadVotoSel] = useState('');

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
    error: errorAsambleas,
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

  // The caller's own person (needed to recognise the proxies granted to them).
  const { data: miPersona } = useFetch(
    useCallback(() => tenantApi.get('/personas/me').catch(() => null), [tenant.activeAssignmentId])
  );
  const miPersonaId = miPersona?.id ?? user?.idPersona ?? null;
  const ownUnitId = tenant.activeAssignment?.idUnidad ?? user?.idUnidad ?? null;
  const ownUnitLabel = user?.identificadorUnidad || (ownUnitId ? `#${ownUnitId}` : '');
  // Only an owner may grant a proxy (Ley 675); a cohabitant has no access to the unit's owners/residents either.
  const canFileProxy = !(
    user?.rol === 'RESIDENTE_CONVIVENCIA' ||
    user?.rolCodigo === 'RESIDENTE_CONVIVENCIA' ||
    user?.tipoResidente === 'CONVIVIENTE' ||
    user?.tipoRelacion === 'CONVIVIENTE'
  );

  // People of the caller's own unit (units/{id}/owners and /residents are granted to owners/residents, not cohabitants).
  const { data: ownersRaw } = useFetch(
    useCallback(
      () => (canFileProxy && ownUnitId ? tenantApi.get(`/units/${ownUnitId}/owners`).catch(() => []) : Promise.resolve([])),
      [canFileProxy, ownUnitId, tenant.activeAssignmentId]
    )
  );
  const { data: residentsRaw } = useFetch(
    useCallback(
      () => (canFileProxy && ownUnitId ? tenantApi.get(`/units/${ownUnitId}/residents`).catch(() => []) : Promise.resolve([])),
      [canFileProxy, ownUnitId, tenant.activeAssignmentId]
    )
  );

  const personaNombre = (p) =>
    [p?.primerNombre, p?.segundoNombre, p?.primerApellido, p?.segundoApellido].filter(Boolean).join(' ') ||
    `Persona ${p?.id}`;

  const propietariosUnidad = useMemo(() => {
    const list = Array.isArray(ownersRaw) ? ownersRaw : [];
    return list
      .filter((o) => o.persona?.id && (!o.estado || o.estado === 'ACTIVO'))
      .map((o) => ({ id: o.persona.id, nombre: personaNombre(o.persona) }));
  }, [ownersRaw]);

  const personasUnidad = useMemo(() => {
    const byId = new Map();
    const add = (p) => {
      if (p?.id && !byId.has(p.id)) byId.set(p.id, { id: p.id, nombre: personaNombre(p) });
    };
    (Array.isArray(ownersRaw) ? ownersRaw : []).forEach((o) => add(o.persona));
    (Array.isArray(residentsRaw) ? residentsRaw : []).forEach((r) => add(r.persona));
    return [...byId.values()];
  }, [ownersRaw, residentsRaw]);

  // Units this person may vote for in the selected assembly: own unit + units with an APROBADO proxy in their favour.
  const unidadesVoto = useMemo(() => {
    const list = [];
    if (ownUnitId) list.push({ idUnidad: Number(ownUnitId), label: `Mi unidad ${ownUnitLabel}`, representada: false });
    poderes.forEach((p) => {
      if (
        p.estado === 'APROBADO' &&
        miPersonaId != null &&
        Number(p.idPersonaApoderado) === Number(miPersonaId) &&
        !list.some((u) => u.idUnidad === Number(p.idUnidad))
      ) {
        list.push({
          idUnidad: Number(p.idUnidad),
          label: `Unidad ${p.unidadIdentificador || `#${p.idUnidad}`} (por poder)`,
          representada: true,
        });
      }
    });
    return list;
  }, [ownUnitId, ownUnitLabel, poderes, miPersonaId]);

  const unidadVoto =
    unidadesVoto.find((u) => String(u.idUnidad) === String(unidadVotoSel)) || unidadesVoto[0] || null;

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

    if (!ownUnitId || !formPoder.idPersonaPropietario || !formPoder.idPersonaApoderado) {
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
        idUnidad: Number(ownUnitId),
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
      const msg = err.response?.data?.message || err.message || 'Error al radicar el poder.';
      toast.error(msg);
    } finally {
      setSubmittingPoder(false);
    }
  };

  // Emitir voto interactivo como residente / apoderado
  const handleVotar = async (idVotacion, opcion) => {
    if (!asambleaSeleccionadaId) return;
    const unitId = unidadVoto?.idUnidad;
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
      toast.success(`Voto '${opcion}' registrado exitosamente para ${unidadVoto.label}`);
      setVotoExitosoMap((prev) => ({ ...prev, [`${idVotacion}:${unitId}`]: opcion }));
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
    <PageContainer className="pb-12">
      <PageHeader
        title="Asambleas y gobernanza"
        subtitle="Consulta las convocatorias, sigue el quórum en tiempo real y radica poderes de representación."
        action={
          <Button
            variant="outline"
            onClick={() => {
              const quiet = () => {};
              refetchAsambleas().catch(quiet);
              refetchQuorum().catch(quiet);
              refetchPoderes().catch(quiet);
              refetchVotaciones().catch(quiet);
              toast.info('Datos de asambleas actualizados.');
            }}
            className="min-h-10"
          >
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            Actualizar
          </Button>
        }
      />

      {/* Tabs */}
      <div className="flex overflow-x-auto border-b border-border" role="tablist" aria-label="Asambleas">
        <button
          onClick={() => setTabActiva('proximas')}
          role="tab"
          aria-selected={tabActiva === 'proximas'}
          className={`min-h-10 shrink-0 border-b-2 px-4 pb-2.5 text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring ${
            tabActiva === 'proximas'
              ? 'border-primary text-primary-text'
              : 'border-transparent text-text-muted hover:text-on-surface'
          }`}
        >
          Convocatorias activas <span className="tabular-nums">({asambleasProximas.length})</span>
        </button>
        <button
          onClick={() => setTabActiva('historial')}
          role="tab"
          aria-selected={tabActiva === 'historial'}
          className={`min-h-10 shrink-0 border-b-2 px-4 pb-2.5 text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring ${
            tabActiva === 'historial'
              ? 'border-primary text-primary-text'
              : 'border-transparent text-text-muted hover:text-on-surface'
          }`}
        >
          Historial y actas <span className="tabular-nums">({asambleasHistorial.length})</span>
        </button>
      </div>

      {loadingAsambleas ? (
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState message="Cargando asambleas…" />
        </div>
      ) : errorAsambleas ? (
        <ErrorState
          title="No se pudieron cargar las asambleas"
          message="Revisa tu conexión e intenta de nuevo."
          onRetry={() => refetchAsambleas().catch(() => {})}
        />
      ) : asambleas.length === 0 ? (
        <EmptyState
          icon={Calendar}
          title="No hay asambleas registradas"
          subtitle="La administración no ha convocado asambleas ordinarias ni extraordinarias por ahora."
        />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Lista de asambleas en columna izquierda */}
          <div className="lg:col-span-1 space-y-3">
            <h3 className="text-sm font-semibold text-text-muted px-1">
              {tabActiva === 'proximas' ? 'Asambleas activas' : 'Historial de asambleas'}
            </h3>

            {(tabActiva === 'proximas' ? asambleasProximas : asambleasHistorial).map((item) => {
              const esSeleccionada = item.idAsamblea === asambleaSeleccionadaId;
              const estadoConfig = ESTADOS_ASAMBLEA[item.estado] || ESTADOS_ASAMBLEA.CONVOCADA;
              const ModIcon = MODALIDADES[item.modalidad]?.icon || Building;

              return (
                <div
                  key={item.idAsamblea}
                  onClick={() => setAsambleaSeleccionadaId(item.idAsamblea)}
                  role="button"
                  tabIndex={0}
                  aria-pressed={esSeleccionada}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setAsambleaSeleccionadaId(item.idAsamblea);
                    }
                  }}
                  className={`cursor-pointer rounded-lg border p-4 transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
                    esSeleccionada
                      ? 'border-primary bg-tint-purple-bg shadow-sm'
                      : 'border-border hover:border-primary/50 bg-surface'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${estadoConfig.color}`}
                    >
                      {estadoConfig.label}
                    </span>
                    <span className="text-xs text-text-muted">
                      Conv. #{item.convocatoriaNumero || 1}
                    </span>
                  </div>

                  <h4 className="font-semibold text-sm text-on-surface line-clamp-2">
                    {item.titulo}
                  </h4>

                  <div className="mt-3 space-y-1.5 text-xs text-text-muted">
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
                <div className="bg-surface border border-border rounded-lg shadow-sm p-4 sm:p-6">
                  <div className="flex flex-wrap items-center justify-between gap-4 border-b border-border pb-4 mb-4">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span
                          className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                            ESTADOS_ASAMBLEA[asambleaActiva.estado]?.color || ''
                          }`}
                        >
                          {ESTADOS_ASAMBLEA[asambleaActiva.estado]?.label || asambleaActiva.estado}
                        </span>
                        <span className="text-xs text-text-muted">
                          {asambleaActiva.tipo} • Convocatoria #{asambleaActiva.convocatoriaNumero || 1}
                        </span>
                      </div>
                      <h2 className="text-xl font-bold text-on-surface">
                        {asambleaActiva.titulo}
                      </h2>
                    </div>

                    {/* Botón Radicar Poder si no está finalizada */}
                    {canFileProxy && asambleaActiva.estado !== 'FINALIZADA' && asambleaActiva.estado !== 'CANCELADA' && (
                      <button
                        onClick={() => setModalPoderOpen(true)}
                        className="inline-flex items-center gap-2 px-3.5 py-2 text-sm font-medium rounded-lg bg-primary text-white hover:bg-primary/90 transition-colors shadow-sm"
                      >
                        <ShieldCheck className="w-4 h-4" />
                        Radicar poder
                      </button>
                    )}
                  </div>

                  {/* Grid de Metadatos Clave */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm mb-6">
                    <div className="p-3 bg-surface-container rounded-lg">
                      <span className="text-xs font-medium text-text-muted block mb-1">
                        Primera convocatoria
                      </span>
                      <p className="font-semibold text-on-surface">
                        {formatearFecha(asambleaActiva.fechaHoraPrimeraConv)}
                      </p>
                    </div>

                    <div className="p-3 bg-surface-container rounded-lg">
                      <span className="text-xs font-medium text-text-muted block mb-1">
                        Segunda convocatoria (Ley 675)
                      </span>
                      <p className="font-semibold text-on-surface">
                        {asambleaActiva.fechaHoraSegundaConv
                          ? formatearFecha(asambleaActiva.fechaHoraSegundaConv)
                          : 'No requerida'}
                      </p>
                    </div>

                    <div className="p-3 bg-surface-container rounded-lg">
                      <span className="text-xs font-medium text-text-muted block mb-1">
                        Modalidad y lugar / enlace
                      </span>
                      <p className="font-semibold text-on-surface break-words">
                        {asambleaActiva.modalidad}: {asambleaActiva.lugarOEnlace}
                      </p>
                    </div>

                    <div className="p-3 bg-surface-container rounded-lg">
                      <span className="text-xs font-medium text-text-muted block mb-1">
                        Quórum legal requerido
                      </span>
                      <p className="font-semibold text-on-surface">
                        {asambleaActiva.quorumRequeridoPct || 50.01}% de coeficiente de copropiedad
                      </p>
                    </div>
                  </div>

                  {/* Documento Convocatoria Oficial (F10-01 integration) */}
                  {asambleaActiva.idDocumento && (
                    <div className="mb-6 p-4 rounded-xl border border-tint-blue-fg/25 bg-tint-blue-bg flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <FileText className="w-5 h-5 text-tint-blue-fg" />
                        <div>
                          <p className="text-sm font-semibold text-tint-blue-fg">
                            Convocatoria oficial adjunta
                          </p>
                          <p className="text-xs text-text-muted">
                            Documento oficial F10-01 con anexos y estados financieros
                          </p>
                        </div>
                      </div>
                      <a
                        href={`/api/v1/documentos/${asambleaActiva.idDocumento}/descargar`}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-tint-blue-fg text-tint-blue-fg hover:bg-tint-blue-fg hover:text-white transition-colors"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                        Descargar PDF
                      </a>
                    </div>
                  )}

                  {/* Acta Oficial Publicada (F10-05 integration - Art. 47 Ley 675) */}
                  {actaOficial && actaOficial.estado === 'PUBLICADA_OFICIAL' && (
                    <div className="mb-6 p-5 rounded-xl border border-tint-green-fg/25 bg-tint-green-bg space-y-4">
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-tint-green-fg/25 pb-3">
                        <div className="flex items-center gap-3">
                          <div className="p-2.5 rounded-lg bg-tint-green-bg text-tint-green-fg">
                            <FileCheck className="w-6 h-6" />
                          </div>
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-base text-tint-green-fg">
                                Acta Oficial Nº {actaOficial.numeroActa}
                              </span>
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-tint-green-bg text-tint-green-fg">
                                <Lock className="w-3 h-3" /> Publicada Oficialmente
                              </span>
                            </div>
                            <p className="text-xs text-text-muted">
                              Aprobada por comisión verificadora • conforme a Ley 675 de 2001
                            </p>
                          </div>
                        </div>

                        {(actaOficial.idDocumento || actaOficial.documentoFirmadoUrl) && (
                          <button
                            onClick={() => handleDescargarDocumentoActa(actaOficial)}
                            className="inline-flex items-center justify-center gap-1.5 px-3.5 py-2 text-xs font-semibold rounded-lg bg-btn-success hover:bg-btn-success/90 text-white transition-colors shadow-sm"
                          >
                            <Download className="w-4 h-4" />
                            Descargar acta oficial (PDF)
                          </button>
                        )}
                      </div>

                      {/* Resumen de Gobernanza del Acta */}
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                        <div className="p-2.5 bg-white/70 rounded-lg border border-tint-green-fg/25">
                          <span className="text-text-muted block">Asistentes</span>
                          <span className="font-bold text-on-surface">
                            {actaOficial.totalAsistentes || 0} personas
                          </span>
                        </div>
                        <div className="p-2.5 bg-white/70 rounded-lg border border-tint-green-fg/25">
                          <span className="text-text-muted block">Coeficiente total</span>
                          <span className="font-bold text-on-surface">
                            {Number(actaOficial.totalCoeficienteAsistentes || 0).toFixed(4)}
                          </span>
                        </div>
                        <div className="p-2.5 bg-white/70 rounded-lg border border-tint-green-fg/25">
                          <span className="text-text-muted block">Poderes validados</span>
                          <span className="font-bold text-on-surface">
                            {actaOficial.totalPoderesAprobados || 0}
                          </span>
                        </div>
                        <div className="p-2.5 bg-white/70 rounded-lg border border-tint-green-fg/25">
                          <span className="text-text-muted block">Votaciones</span>
                          <span className="font-bold text-on-surface">
                            {actaOficial.totalVotaciones || 0} puntos
                          </span>
                        </div>
                      </div>

                      {/* Texto del Acta */}
                      {actaOficial.contenidoTexto && (
                        <details className="text-xs group">
                          <summary className="cursor-pointer font-semibold text-tint-green-fg hover:underline flex items-center gap-1">
                            Ver texto íntegro del acta
                          </summary>
                          <div className="mt-2 p-3 bg-white/80 rounded-lg border border-border font-mono text-[11px] whitespace-pre-wrap max-h-60 overflow-y-auto leading-relaxed text-text-secondary">
                            {actaOficial.contenidoTexto}
                          </div>
                        </details>
                      )}
                    </div>
                  )}

                  {/* Aviso si la asamblea finalizó pero el acta aún está en trámite */}
                  {asambleaActiva.estado === 'FINALIZADA' && (!actaOficial || actaOficial.estado !== 'PUBLICADA_OFICIAL') && (
                    <div className="mb-6 p-4 rounded-xl border border-tint-amber-fg/25 bg-tint-amber-bg flex items-start gap-3">
                      <Clock className="w-5 h-5 text-tint-amber-fg mt-0.5 shrink-0" />
                      <div className="text-xs">
                        <p className="font-semibold text-tint-amber-fg">
                          Acta en proceso de redacción y revisión
                        </p>
                        <p className="text-text-muted mt-0.5 leading-relaxed">
                          Conforme al artículo 47 de la Ley 675 de 2001, la comisión verificadora dispone de un plazo de hasta veinte (20) días hábiles para revisar, firmar y publicar el acta oficial de esta asamblea. Una vez publicada, estará disponible para consulta y descarga aquí.
                        </p>
                      </div>
                    </div>
                  )}

                  {/* Quórum en Tiempo Real (Live Gauge) */}
                  <div className="mb-6 p-5 rounded-xl border border-border bg-surface-container">
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center gap-2">
                        <Percent className="w-5 h-5 text-primary" />
                        <h4 className="font-semibold text-sm text-on-surface">
                          Quórum deliberatorio y decisorio
                        </h4>
                      </div>
                      <span
                        className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                          quorumLive?.tieneQuorum
                            ? 'bg-tint-green-bg text-tint-green-fg'
                            : 'bg-tint-amber-bg text-tint-amber-fg'
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
                      <div className="w-full bg-surface-dim h-3 rounded-full overflow-hidden relative">
                        {/* Target line at quorumRequeridoPct */}
                        <div
                          className="absolute top-0 bottom-0 w-0.5 bg-neutral-900 z-10"
                          style={{
                            left: `${Math.min(asambleaActiva.quorumRequeridoPct || 50.01, 100)}%`,
                          }}
                          title={`Meta: ${asambleaActiva.quorumRequeridoPct || 50.01}%`}
                        />
                        <div
                          className={`h-full transition-all duration-500 rounded-full ${
                            quorumLive?.tieneQuorum ? 'bg-tint-green-fg' : 'bg-primary'
                          }`}
                          style={{
                            width: `${Math.min(quorumLive?.quorumAlcanzadoPct || 0, 100)}%`,
                          }}
                        />
                      </div>

                      <div className="flex items-center justify-between text-xs text-text-muted">
                        <span>
                          Alcanzado:{' '}
                          <strong className="text-on-surface">
                            {Number(quorumLive?.quorumAlcanzadoPct || 0).toFixed(2)}%
                          </strong>
                        </span>
                        <span>
                          Requerido: {Number(asambleaActiva.quorumRequeridoPct || 50.01).toFixed(2)}%
                        </span>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4 mt-4 pt-4 border-t border-border text-xs">
                      <div>
                        <span className="text-text-muted block">Unidades registradas</span>
                        <span className="font-bold text-sm text-on-surface">
                          {quorumLive?.totalUnidadesRegistradas || 0}
                        </span>
                      </div>
                      <div>
                        <span className="text-text-muted block">Coeficiente total presente</span>
                        <span className="font-bold text-sm text-on-surface">
                          {Number(quorumLive?.totalCoeficienteRegistrado || 0).toFixed(4)}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Orden del Día */}
                  <div>
                    <h4 className="font-semibold text-sm text-on-surface mb-2">
                      Orden del día propuesto
                    </h4>
                    <div className="bg-surface-container p-4 rounded-xl border border-border text-sm whitespace-pre-wrap text-text-secondary font-sans leading-relaxed">
                      {asambleaActiva.ordenDelDia}
                    </div>
                  </div>
                </div>

                {/* Votaciones en Asamblea */}
                <div className="bg-surface border border-border rounded-lg shadow-sm p-4 sm:p-6">
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                      <Vote className="w-5 h-5 text-primary" />
                      <h3 className="font-bold text-base text-on-surface">
                        Votaciones y decisiones
                      </h3>
                    </div>
                    <span className="text-xs text-text-muted">
                      {votaciones.length} punto(s) de votación
                    </span>
                  </div>

                  {loadingVotaciones ? (
                    <div className="p-4 text-center text-xs text-text-muted">Cargando votaciones...</div>
                  ) : votaciones.length === 0 ? (
                    <div className="text-center py-6 text-text-muted text-sm">
                      No hay puntos de votación activos o registrados para esta asamblea.
                    </div>
                  ) : (
                    <div className="space-y-4">
                      {votaciones.map((v) => (
                        <div
                          key={v.idVotacion}
                          className="p-4 rounded-xl border border-border space-y-3"
                        >
                          <div className="flex items-center justify-between gap-2">
                            <div className="flex items-center gap-2">
                              <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-surface-dim text-text-secondary">
                                Punto #{v.puntoOrdenDia}
                              </span>
                              <h5 className="font-semibold text-sm text-on-surface">
                                {v.titulo}
                              </h5>
                            </div>
                            <span
                              className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                                v.estado === 'ABIERTA'
                                  ? 'bg-tint-green-bg text-tint-green-fg'
                                  : 'bg-surface-dim text-text-secondary'
                              }`}
                            >
                              {v.estado}
                            </span>
                          </div>

                          {v.descripcion && (
                            <p className="text-xs text-text-secondary">
                              {v.descripcion}
                            </p>
                          )}

                          {/* Emisión interactiva de voto si la votación está ABIERTA */}
                          {v.estado === 'ABIERTA' && (
                            <div className="p-3 bg-surface-container rounded-lg space-y-2 border border-border">
                              <div className="flex items-center justify-between">
                                <span className="text-xs font-semibold text-text-secondary">
                                  Emitir voto
                                </span>
                                {unidadVoto && votoExitosoMap[`${v.idVotacion}:${unidadVoto.idUnidad}`] && (
                                  <span className="text-xs font-semibold text-tint-green-fg flex items-center gap-1">
                                    <CheckCircle2 className="w-3.5 h-3.5" /> Voto registrado: {votoExitosoMap[`${v.idVotacion}:${unidadVoto.idUnidad}`]}
                                  </span>
                                )}
                              </div>
                              {unidadesVoto.length === 0 ? (
                                <p className="text-xs text-tint-amber-fg">
                                  No tiene una unidad propia ni poderes aprobados para votar.
                                </p>
                              ) : (
                                <div>
                                  <label
                                    htmlFor={`unidad-voto-${v.idVotacion}`}
                                    className="block text-[11px] font-semibold text-text-muted mb-1"
                                  >
                                    Votar por la unidad
                                  </label>
                                  <select
                                    id={`unidad-voto-${v.idVotacion}`}
                                    value={unidadVoto ? String(unidadVoto.idUnidad) : ''}
                                    onChange={(e) => setUnidadVotoSel(e.target.value)}
                                    className="w-full px-3 py-1.5 text-sm rounded-lg border border-border bg-surface"
                                  >
                                    {unidadesVoto.map((u) => (
                                      <option key={u.idUnidad} value={u.idUnidad}>
                                        {u.label}
                                      </option>
                                    ))}
                                  </select>
                                </div>
                              )}
                              <div className="grid grid-cols-4 gap-2 pt-1">
                                <button
                                  type="button"
                                  disabled={votingId === v.idVotacion || !unidadVoto || !!votoExitosoMap[`${v.idVotacion}:${unidadVoto?.idUnidad}`]}
                                  onClick={() => handleVotar(v.idVotacion, 'SI')}
                                  className="px-3 py-2 text-xs font-semibold rounded-lg bg-btn-success hover:bg-btn-success/90 text-white transition-colors disabled:opacity-50 flex items-center justify-center gap-1 shadow-sm"
                                >
                                  <CheckCircle2 className="w-3.5 h-3.5" /> SÍ
                                </button>
                                <button
                                  type="button"
                                  disabled={votingId === v.idVotacion || !unidadVoto || !!votoExitosoMap[`${v.idVotacion}:${unidadVoto?.idUnidad}`]}
                                  onClick={() => handleVotar(v.idVotacion, 'NO')}
                                  className="px-3 py-2 text-xs font-semibold rounded-lg bg-btn-danger hover:bg-btn-danger-hover text-white transition-colors disabled:opacity-50 flex items-center justify-center gap-1 shadow-sm"
                                >
                                  NO
                                </button>
                                <button
                                  type="button"
                                  disabled={votingId === v.idVotacion || !unidadVoto || !!votoExitosoMap[`${v.idVotacion}:${unidadVoto?.idUnidad}`]}
                                  onClick={() => handleVotar(v.idVotacion, 'BLANCO')}
                                  className="px-3 py-2 text-xs font-semibold rounded-lg bg-surface-dim hover:bg-neutral-300 text-on-surface transition-colors disabled:opacity-50 shadow-sm"
                                >
                                  BLANCO
                                </button>
                                <button
                                  type="button"
                                  disabled={votingId === v.idVotacion || !unidadVoto || !!votoExitosoMap[`${v.idVotacion}:${unidadVoto?.idUnidad}`]}
                                  onClick={() => handleVotar(v.idVotacion, 'ABSTENCION')}
                                  className="px-3 py-2 text-xs font-semibold rounded-lg bg-tint-amber-bg hover:bg-tint-amber-bg text-tint-amber-fg border border-tint-amber-fg/25 transition-colors disabled:opacity-50 shadow-sm"
                                >
                                  ABSTENCIÓN
                                </button>
                              </div>
                            </div>
                          )}

                          {/* Resultados si está cerrada */}
                          {v.estado === 'CERRADA' && (
                            <div className="p-3 bg-surface-container rounded-lg text-xs space-y-1">
                              <div className="flex justify-between font-medium">
                                <span>Resultado:</span>
                                <span
                                  className={v.aprobada ? 'text-tint-green-fg font-bold' : 'text-tint-red-fg font-bold'}
                                >
                                  {v.aprobada ? 'APROBADA' : 'NO APROBADA'}
                                </span>
                              </div>
                              <div className="grid grid-cols-4 gap-2 text-center pt-2">
                                <div className="bg-tint-green-bg p-1.5 rounded">
                                  <span className="block text-text-muted">SÍ</span>
                                  <span className="font-bold text-tint-green-fg">
                                    {v.votosSi || 0}
                                  </span>
                                </div>
                                <div className="bg-tint-red-bg p-1.5 rounded">
                                  <span className="block text-text-muted">NO</span>
                                  <span className="font-bold text-tint-red-fg">
                                    {v.votosNo || 0}
                                  </span>
                                </div>
                                <div className="bg-surface-dim p-1.5 rounded">
                                  <span className="block text-text-muted">BLANCO</span>
                                  <span className="font-bold">{v.votosBlanco || 0}</span>
                                </div>
                                <div className="bg-surface-dim p-1.5 rounded">
                                  <span className="block text-text-muted">ABSTENCIÓN</span>
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
                <div className="bg-surface border border-border rounded-lg shadow-sm p-4 sm:p-6">
                  <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                      <ShieldCheck className="w-5 h-5 text-primary" />
                      <h3 className="font-bold text-base text-on-surface">
                        Poderes de representación registrados
                      </h3>
                    </div>
                    <span className="text-xs text-text-muted">
                      {poderes.length} poder(es) registrado(s)
                    </span>
                  </div>

                  {loadingPoderes ? (
                    <div className="p-4 text-center text-xs text-text-muted">Cargando poderes...</div>
                  ) : poderes.length === 0 ? (
                    <div className="text-center py-6 text-text-muted text-sm">
                      No hay poderes radicados para esta asamblea.
                    </div>
                  ) : (
                    <div className="divide-y divide-border-subtle ">
                      {poderes.map((p) => (
                        <div key={p.idPoder} className="py-3 flex items-center justify-between text-sm">
                          <div>
                            <span className="font-semibold text-on-surface">
                              Unidad {p.unidadIdentificador || `ID ${p.idUnidad}`}
                            </span>
                            <p className="text-xs text-text-muted">
                              Otorgante: {p.nombrePropietario || `ID ${p.idPersonaPropietario}`} →
                              Apoderado: {p.nombreApoderado || `ID ${p.idPersonaApoderado}`}
                            </p>
                          </div>
                          <span
                            className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                              p.estado === 'APROBADO'
                                ? 'bg-tint-green-bg text-tint-green-fg'
                                : p.estado === 'RECHAZADO'
                                ? 'bg-tint-red-bg text-tint-red-fg'
                                : 'bg-tint-amber-bg text-tint-amber-fg'
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
              <div className="p-8 text-center text-text-muted border border-border rounded-xl">
                Selecciona una asamblea del listado para ver su detalle y quórum.
              </div>
            )}
          </div>
        </div>
      )}

      {/* Modal Radicar Poder */}
      {modalPoderOpen && (
        <Modal
          open={modalPoderOpen}
          onClose={() => setModalPoderOpen(false)}
          title="Radicar poder de representación (Ley 675)"
        >
          <form onSubmit={handleRadicarPoder} className="space-y-4">
            <div className="p-3 bg-tint-blue-bg border border-tint-blue-fg/25 rounded-lg text-xs text-tint-blue-fg">
              Conforme a la Ley 675 y el reglamento de la copropiedad, todo copropietario puede hacerse
              representar mediante poder debidamente conferido. No se permite la auto-representación.
            </div>

            <div>
              <label className="block text-xs font-semibold text-text-muted mb-1">
                Unidad / inmueble
              </label>
              <div className="px-3 py-2 text-sm rounded-lg border border-border bg-surface-container">
                {ownUnitId ? `Unidad ${ownUnitLabel}` : 'No tiene una unidad asignada activa.'}
              </div>
            </div>

            <div>
              <label
                htmlFor="poder-propietario"
                className="block text-xs font-semibold text-text-muted mb-1"
              >
                Propietario (Poderdante) *
              </label>
              <select
                id="poder-propietario"
                required
                value={formPoder.idPersonaPropietario}
                onChange={(e) => setFormPoder({ ...formPoder, idPersonaPropietario: e.target.value })}
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">Selecciona…</option>
                {propietariosUnidad.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.nombre}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label
                htmlFor="poder-apoderado"
                className="block text-xs font-semibold text-text-muted mb-1"
              >
                Apoderado (Representante) *
              </label>
              <select
                id="poder-apoderado"
                required
                value={formPoder.idPersonaApoderado}
                onChange={(e) => setFormPoder({ ...formPoder, idPersonaApoderado: e.target.value })}
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">Selecciona…</option>
                {personasUnidad
                  .filter((p) => String(p.id) !== String(formPoder.idPersonaPropietario))
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.nombre}
                    </option>
                  ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-text-muted mb-1">
                Enlace / URL de carta poder escaneada (opcional)
              </label>
              <input
                type="url"
                value={formPoder.documentoPoderUrl}
                onChange={(e) =>
                  setFormPoder({ ...formPoder, documentoPoderUrl: e.target.value })
                }
                className="w-full px-3 py-2 text-sm rounded-lg border border-border bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="https://..."
              />
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t border-border">
              <button
                type="button"
                onClick={() => setModalPoderOpen(false)}
                className="px-4 py-2 text-sm font-medium rounded-lg border border-border hover:bg-surface-dim transition-colors"
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
    </PageContainer>
  );
}
