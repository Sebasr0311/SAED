import { useState, useMemo } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { toast } from 'sonner';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import {
  AlertTriangle,
  ShieldAlert,
  CheckCircle2,
  Clock,
  Users,
  Plus,
  ArrowUpRight,
  RotateCcw,
  Search,
  FileText,
  BadgeAlert,
  Trash2,
  Phone,
  Eye,
} from 'lucide-react';

const TIPOS_INCIDENTE = [
  { id: 'DANO_BIEN_COMUN', label: 'Daño a Bien Común' },
  { id: 'SEGURIDAD_HURTO', label: 'Seguridad / Hurto' },
  { id: 'CONVIVENCIA_RUIDO', label: 'Convivencia (Ruido)' },
  { id: 'CONVIVENCIA_DISPUTA', label: 'Convivencia (Disputa)' },
  { id: 'ACCESO_NO_AUTORIZADO', label: 'Acceso No Autorizado' },
  { id: 'ACCIDENTE_PERSONA', label: 'Accidente de Persona' },
  { id: 'FALLA_CRITICA_INFRAESTRUCTURA', label: 'Falla Crítica Infraestructura' },
  { id: 'OTRO', label: 'Otro Incidente' },
];

const SEVERIDADES = {
  LEVE: { label: 'Leve', variant: 'secondary' },
  MODERADA: { label: 'Moderada', variant: 'warning' },
  GRAVE: { label: 'Grave', variant: 'warning' },
  CRITICA: { label: 'Crítica', variant: 'destructive' },
};

const ESTADOS_INCIDENTE = {
  REPORTADO: { label: 'Reportado', variant: 'warning' },
  EN_INVESTIGACION: { label: 'En investigación', variant: 'info' },
  ESCALADO_A_SANCION: { label: 'Escalado a sanción', variant: 'lavender' },
  ACCION_TOMADA: { label: 'Acción tomada', variant: 'info' },
  CERRADO: { label: 'Cerrado', variant: 'success' },
};

export default function IncidentesAdminPage() {
  const { data, loading, error: loadError, refetch } = useFetch(() => api.get('/incidentes/admin'), []);

  // Modals state
  const [modalOpen, setModalOpen] = useState(false);
  const [cierreOpen, setCierreOpen] = useState(false);
  const [investigacionOpen, setInvestigacionOpen] = useState(false);
  const [escalarOpen, setEscalarOpen] = useState(false);
  const [reabrirOpen, setReabrirOpen] = useState(false);
  const [involucradosOpen, setInvolucradosOpen] = useState(false);
  const [detalleOpen, setDetalleOpen] = useState(false);

  const [selectedIncidente, setSelectedIncidente] = useState(null);
  const [conclusiones, setConclusiones] = useState('');
  const [motivoReapertura, setMotivoReapertura] = useState('');

  // Filters state
  const [busqueda, setBusqueda] = useState('');
  const [filtroEstado, setFiltroEstado] = useState('TODOS');
  const [filtroSeveridad, setFiltroSeveridad] = useState('TODOS');

  // Investigation form
  const [invForm, setInvForm] = useState({
    investigadorAsignado: '',
    lineasInvestigacion: '',
    hallazgos: '',
    concluir: false,
  });

  // Escalation form
  const [escForm, setEscForm] = useState({
    justificacionEscalamiento: '',
    sancionSugerida: '',
  });

  // Involucrados state
  const [involucradosList, setInvolucradosList] = useState([]);
  const [invLoading, setInvLoading] = useState(false);
  const [newInvolucrado, setNewInvolucrado] = useState({
    rolInvolucrado: 'TESTIGO',
    idPersona: '',
    nombreIdentificacionExterna: '',
    declaracionRendida: '',
  });

  // Create incident form
  const [form, setForm] = useState({
    titulo: '',
    tipoIncidente: 'DANO_BIEN_COMUN',
    nivelSeveridad: 'MODERADA',
    descripcionHechos: '',
    requirioAutoridades: 'N',
    entidadAutoridad: '',
  });

  const [saving, setSaving] = useState(false);

  const items = useMemo(() => {
    const raw = data?.items || (Array.isArray(data) ? data : []);
    return raw.map((i) => ({
      ...i,
      idIncidente: i.idIncidente ?? i.ID_INCIDENTE,
      titulo: i.titulo ?? i.TITULO,
      tipoIncidente: i.tipoIncidente ?? i.TIPO_INCIDENTE,
      nivelSeveridad: i.nivelSeveridad ?? i.NIVEL_SEVERIDAD,
      descripcionHechos: i.descripcionHechos ?? i.DESCRIPCION_HECHOS,
      requirioAutoridades: i.requirioAutoridades ?? i.REQUIRIO_AUTORIDADES,
      entidadAutoridad: i.entidadAutoridad ?? i.ENTIDAD_AUTORIDAD,
      fechaHoraIncidente: i.fechaHoraIncidente ?? i.FECHA_HORA_INCIDENTE,
      estado: i.estado ?? i.ESTADO ?? 'REPORTADO',
      investigadorAsignado: i.investigadorAsignado ?? i.INVESTIGADOR_ASIGNADO,
      lineasInvestigacion: i.lineasInvestigacion ?? i.LINEAS_INVESTIGACION,
      hallazgosInvestigacion: i.hallazgosInvestigacion ?? i.HALLAZGOS_INVESTIGACION,
      conclusionesCierre: i.conclusionesCierre ?? i.CONCLUSIONES_CIERRE,
    }));
  }, [data]);

  const kpis = useMemo(() => {
    const total = items.length;
    const investigacion = items.filter((i) => i.estado === 'EN_INVESTIGACION').length;
    const escalados = items.filter((i) => i.estado === 'ESCALADO_A_SANCION').length;
    const cerrados = items.filter((i) => i.estado === 'CERRADO').length;
    return { total, investigacion, escalados, cerrados };
  }, [items]);

  const filteredItems = useMemo(() => {
    return items.filter((i) => {
      const matchSearch =
        !busqueda.trim() ||
        (i.titulo || '').toLowerCase().includes(busqueda.toLowerCase()) ||
        (i.descripcionHechos || '').toLowerCase().includes(busqueda.toLowerCase()) ||
        (i.investigadorAsignado || '').toLowerCase().includes(busqueda.toLowerCase());

      const matchEstado = filtroEstado === 'TODOS' || i.estado === filtroEstado;
      const matchSeveridad = filtroSeveridad === 'TODOS' || i.nivelSeveridad === filtroSeveridad;

      return matchSearch && matchEstado && matchSeveridad;
    });
  }, [items, busqueda, filtroEstado, filtroSeveridad]);

  const handleCreate = async () => {
    if (!form.titulo.trim() || !form.descripcionHechos.trim()) {
      toast.error('Título y descripción son obligatorios.');
      return;
    }
    setSaving(true);
    try {
      await api.post('/incidentes', { ...form, fechaHoraIncidente: new Date().toISOString() });
      toast.success('Incidente reportado correctamente');
      setModalOpen(false);
      setForm({
        titulo: '',
        tipoIncidente: 'DANO_BIEN_COMUN',
        nivelSeveridad: 'MODERADA',
        descripcionHechos: '',
        requirioAutoridades: 'N',
        entidadAutoridad: '',
      });
      refetch();
    } catch (e) {
      toast.error('Error al reportar el incidente: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  const handleCerrar = async () => {
    if (!conclusiones.trim()) {
      toast.error('Indique las conclusiones para cerrar el caso.');
      return;
    }
    setSaving(true);
    try {
      await api.post(`/incidentes/${selectedIncidente.idIncidente}/cerrar`, { conclusiones });
      toast.success('Incidente cerrado correctamente');
      setCierreOpen(false);
      setSelectedIncidente(null);
      setConclusiones('');
      refetch();
    } catch (e) {
      toast.error('Error al cerrar: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  const handleReabrir = async () => {
    if (!motivoReapertura.trim()) {
      toast.error('Indique el motivo de la reapertura.');
      return;
    }
    setSaving(true);
    try {
      await api.post(`/incidentes/${selectedIncidente.idIncidente}/reabrir`, { motivo: motivoReapertura.trim() });
      toast.success('Incidente reabierto en etapa de investigación');
      setReabrirOpen(false);
      setSelectedIncidente(null);
      setMotivoReapertura('');
      refetch();
    } catch (e) {
      toast.error('Error al reabrir: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  const handleInvestigacion = async () => {
    setSaving(true);
    try {
      const endpoint = invForm.concluir
        ? `/incidentes/${selectedIncidente.idIncidente}/investigacion/concluir`
        : selectedIncidente.estado === 'REPORTADO'
          ? `/incidentes/${selectedIncidente.idIncidente}/investigacion/iniciar`
          : `/incidentes/${selectedIncidente.idIncidente}/investigacion`;

      const method = !invForm.concluir && selectedIncidente.estado !== 'REPORTADO' ? api.put : api.post;
      await method(endpoint, {
        investigadorAsignado: invForm.investigadorAsignado,
        lineasInvestigacion: invForm.lineasInvestigacion,
        hallazgos: invForm.hallazgos,
      });

      toast.success(invForm.concluir ? 'Investigación concluida' : 'Investigación actualizada');
      setInvestigacionOpen(false);
      setSelectedIncidente(null);
      refetch();
    } catch (e) {
      toast.error('Error en investigación: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  const handleEscalar = async () => {
    if (!escForm.justificacionEscalamiento.trim()) {
      toast.error('La justificación de escalamiento es obligatoria.');
      return;
    }
    setSaving(true);
    try {
      await api.post(`/incidentes/${selectedIncidente.idIncidente}/escalar`, escForm);
      toast.success('Incidente escalado a comité de sanciones');
      setEscalarOpen(false);
      setSelectedIncidente(null);
      setEscForm({ justificacionEscalamiento: '', sancionSugerida: '' });
      refetch();
    } catch (e) {
      toast.error('Error al escalar: ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  const loadInvolucrados = async (incidente) => {
    setSelectedIncidente(incidente);
    setInvLoading(true);
    setInvolucradosOpen(true);
    try {
      const res = await api.get(`/incidentes/${incidente.idIncidente}/involucrados`);
      setInvolucradosList(res?.items || (Array.isArray(res) ? res : []));
    } catch (e) {
      toast.error('Error cargando personas involucradas: ' + (e.response?.data?.message || e.message));
      setInvolucradosList([]);
    } finally {
      setInvLoading(false);
    }
  };

  const handleAddInvolucrado = async () => {
    try {
      const payload = {
        rolInvolucrado: newInvolucrado.rolInvolucrado,
        declaracionRendida: newInvolucrado.declaracionRendida || null,
      };
      if (newInvolucrado.idPersona) {
        payload.idPersona = Number(newInvolucrado.idPersona);
      }
      if (newInvolucrado.nombreIdentificacionExterna) {
        payload.nombreIdentificacionExterna = newInvolucrado.nombreIdentificacionExterna;
      }

      await api.post(`/incidentes/${selectedIncidente.idIncidente}/involucrados`, payload);
      toast.success('Persona vinculada al caso');
      setNewInvolucrado({
        rolInvolucrado: 'TESTIGO',
        idPersona: '',
        nombreIdentificacionExterna: '',
        declaracionRendida: '',
      });
      loadInvolucrados(selectedIncidente);
    } catch (e) {
      toast.error('Error al añadir involucrado: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleDeleteInvolucrado = async (idInvolucrado) => {
    try {
      await api.delete(`/incidentes/${selectedIncidente.idIncidente}/involucrados/${idInvolucrado}`);
      toast.success('Involucrado removido');
      loadInvolucrados(selectedIncidente);
    } catch (e) {
      toast.error('Error removiendo involucrado: ' + (e.response?.data?.message || e.message));
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Incidentes y bitácora"
        subtitle="Novedades de seguridad y convivencia: investigación, personas involucradas y escalamiento a sanción."
        action={
          <Button onClick={() => setModalOpen(true)} className="flex items-center gap-2">
            <Plus className="w-4 h-4" />
            Reportar incidente
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-3 min-[420px]:grid-cols-2 lg:grid-cols-4">
        <MetricCard label="Total de incidentes" value={kpis.total} context="Registrados" icon={AlertTriangle} variant="secondary" />
        <MetricCard label="En investigación" value={kpis.investigacion} context="Con indagación abierta" icon={Clock} variant="info" />
        <MetricCard label="Escalados a sanción" value={kpis.escalados} context="Para el comité de convivencia" icon={ArrowUpRight} variant="warning" />
        <MetricCard label="Cerrados" value={kpis.cerrados} context="Con resolución final" icon={CheckCircle2} variant="success" />
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col items-stretch justify-between gap-3 rounded-lg border border-border bg-surface p-3 shadow-sm sm:flex-row sm:items-center sm:p-4">
        <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto">
          <select
            value={filtroEstado}
            onChange={(e) => setFiltroEstado(e.target.value)}
            aria-label="Filtrar por estado"
            className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 w-auto px-3"
          >
            <option value="TODOS">Todos los estados</option>
            <option value="REPORTADO">Reportados</option>
            <option value="EN_INVESTIGACION">En investigación</option>
            <option value="ESCALADO_A_SANCION">Escalados a sanción</option>
            <option value="CERRADO">Cerrados</option>
          </select>

          <select
            value={filtroSeveridad}
            onChange={(e) => setFiltroSeveridad(e.target.value)}
            aria-label="Filtrar por severidad"
            className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 w-auto px-3"
          >
            <option value="TODOS">Todas las severidades</option>
            <option value="LEVE">Leve</option>
            <option value="MODERADA">Moderada</option>
            <option value="GRAVE">Grave</option>
            <option value="CRITICA">Crítica</option>
          </select>
        </div>

        <div className="relative w-full sm:w-72">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
          <input
            type="search"
            aria-label="Buscar incidentes"
            placeholder="Título, hechos o investigador"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="h-10 w-full rounded-md border border-input bg-surface text-sm text-on-surface placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25 pl-9 pr-3"
          />
        </div>
      </div>

      {/* Data Table */}
      {loading ? (
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState message="Cargando incidentes y bitácora..." />
        </div>
      ) : loadError ? (
        <ErrorState
          title="No se pudieron cargar los incidentes"
          message={loadError.response?.data?.message || loadError.message || 'Error de comunicación con el servidor.'}
          onRetry={() => refetch().catch(() => undefined)}
        />
      ) : filteredItems.length === 0 ? (
        <EmptyState
          icon={ShieldAlert}
          title={busqueda || filtroEstado !== 'TODOS' || filtroSeveridad !== 'TODOS' ? 'Ningún incidente coincide con los filtros' : 'No hay incidentes reportados'}
          subtitle={
            busqueda || filtroEstado !== 'TODOS' || filtroSeveridad !== 'TODOS'
              ? 'Cambie la búsqueda o los filtros.'
              : 'Las novedades que se reporten en la copropiedad aparecerán aquí.'
          }
          className="rounded-lg border border-border bg-surface py-10"
        >
          {!(busqueda || filtroEstado !== 'TODOS' || filtroSeveridad !== 'TODOS') && (
            <Button className="mt-4" onClick={() => setModalOpen(true)} icon={<Plus className="h-4 w-4" />}>
              Reportar incidente
            </Button>
          )}
        </EmptyState>
      ) : (
        <div className="overflow-hidden rounded-lg border border-border bg-surface shadow-sm">
          <div className="max-h-[70vh] overflow-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="sticky top-0 z-10 border-b border-border bg-surface-container text-text-secondary font-bold">
                  <th className="py-3 px-4">Fecha</th>
                  <th className="py-3 px-4">Título y hechos</th>
                  <th className="py-3 px-4">Severidad</th>
                  <th className="py-3 px-4">Autoridades</th>
                  <th className="py-3 px-4">Estado</th>
                  <th className="py-3 px-4 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filteredItems.map((i) => {
                  const sevCfg = SEVERIDADES[i.nivelSeveridad] || SEVERIDADES.MODERADA;
                  const estCfg = ESTADOS_INCIDENTE[i.estado] || ESTADOS_INCIDENTE.REPORTADO;

                  return (
                    <tr key={i.idIncidente} className="transition-colors hover:bg-surface-container">
                      <td className="py-3 px-4 align-top whitespace-nowrap">
                        <div className="font-semibold text-foreground tabular-nums">
                          {i.fechaHoraIncidente ? new Date(i.fechaHoraIncidente).toLocaleDateString('es-CO') : '—'}
                        </div>
                        <div className="text-xs text-muted-foreground tabular-nums">
                          {i.fechaHoraIncidente ? new Date(i.fechaHoraIncidente).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' }) : ''}
                        </div>
                        <span className="text-xs text-muted-foreground tabular-nums">#{i.idIncidente}</span>
                      </td>

                      <td className="py-3 px-4 align-top max-w-xs sm:max-w-sm">
                        <div className="font-semibold text-foreground text-sm">{i.titulo}</div>
                        <div className="text-xs text-muted-foreground line-clamp-2 mt-0.5">
                          {i.descripcionHechos}
                        </div>
                        <div className="flex flex-wrap items-center gap-2 mt-1.5">
                          <Badge variant="secondary">
                            {TIPOS_INCIDENTE.find((t) => t.id === i.tipoIncidente)?.label || i.tipoIncidente}
                          </Badge>
                          {i.investigadorAsignado && (
                            <span className="text-xs text-tint-blue-fg font-medium">
                              Investigador: {i.investigadorAsignado}
                            </span>
                          )}
                        </div>
                      </td>

                      <td className="py-3 px-4 align-top whitespace-nowrap">
                        <Badge variant={sevCfg.variant}>{sevCfg.label}</Badge>
                      </td>

                      <td className="py-3 px-4 align-top whitespace-nowrap">
                        {i.requirioAutoridades === 'S' ? (
                          <div className="text-destructive font-medium">
                            Sí <span className="text-muted-foreground font-normal">({i.entidadAutoridad || 'Asistida'})</span>
                          </div>
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </td>

                      <td className="py-3 px-4 align-top whitespace-nowrap">
                        <Badge variant={estCfg.variant}>{estCfg.label}</Badge>
                      </td>

                      <td className="py-3 px-4 align-top text-right whitespace-nowrap">
                        <div className="flex items-center justify-end gap-1.5">
                          {/* Detalle */}
                          <button
                            onClick={() => {
                              setSelectedIncidente(i);
                              setDetalleOpen(true);
                            }}
                            type="button"
                            className="flex h-9 w-9 items-center justify-center rounded-md text-text-secondary hover:bg-surface-container hover:text-on-surface"
                            title="Ver bitácora completa"
                            aria-label={`Ver bitácora del incidente ${i.idIncidente}`}
                          >
                            <Eye className="h-4 w-4" aria-hidden="true" />
                          </button>

                          {/* Involucrados */}
                          <button
                            onClick={() => loadInvolucrados(i)}
                            type="button"
                            className="flex h-9 items-center gap-1 rounded-md border border-input/60 bg-surface px-2.5 text-xs font-semibold text-on-surface transition-colors hover:border-primary/70 hover:bg-surface-container"
                            title="Gestionar Personas Involucradas"
                          >
                            <Users className="w-3.5 h-3.5" />
                            Involucrados
                          </button>

                          {/* Investigar */}
                          {i.estado !== 'CERRADO' && (
                            <button
                              onClick={() => {
                                setSelectedIncidente(i);
                                setInvForm({
                                  investigadorAsignado: i.investigadorAsignado || '',
                                  lineasInvestigacion: i.lineasInvestigacion || '',
                                  hallazgos: i.hallazgosInvestigacion || '',
                                  concluir: false,
                                });
                                setInvestigacionOpen(true);
                              }}
                              type="button"
                              className="h-9 rounded-md px-2.5 text-xs font-semibold bg-tint-blue-bg text-tint-blue-fg border border-tint-blue-fg/25 transition-opacity hover:opacity-90"
                            >
                              {i.estado === 'REPORTADO' ? 'Investigar' : 'Actualizar'}
                            </button>
                          )}

                          {/* Escalar */}
                          {i.estado !== 'CERRADO' && i.estado !== 'ESCALADO_A_SANCION' && (
                            <button
                              onClick={() => {
                                setSelectedIncidente(i);
                                setEscForm({ justificacionEscalamiento: '', sancionSugerida: '' });
                                setEscalarOpen(true);
                              }}
                              type="button"
                              className="h-9 rounded-md px-2.5 text-xs font-semibold bg-tint-purple-bg text-tint-purple-fg border border-tint-purple-fg/25 transition-opacity hover:opacity-90"
                            >
                              Escalar
                            </button>
                          )}

                          {/* Cerrar */}
                          {i.estado !== 'CERRADO' && (
                            <button
                              onClick={() => {
                                setSelectedIncidente(i);
                                setConclusiones('');
                                setCierreOpen(true);
                              }}
                              type="button"
                              className="h-9 rounded-md px-2.5 text-xs font-semibold bg-tint-green-bg text-tint-green-fg border border-tint-green-fg/25 transition-opacity hover:opacity-90"
                            >
                              Cerrar
                            </button>
                          )}

                          {/* Reabrir */}
                          {i.estado === 'CERRADO' && (
                            <button
                              onClick={() => {
                                setSelectedIncidente(i);
                                setMotivoReapertura('');
                                setReabrirOpen(true);
                              }}
                              className="px-2 py-1 text-xs font-medium bg-tint-amber-bg text-tint-amber-fg border border-tint-amber-fg/25 rounded-lg hover:bg-tint-amber-bg transition-colors flex items-center gap-1"
                            >
                              <RotateCcw className="w-3 h-3" />
                              Reabrir
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Modal: Reportar Incidente */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Reportar Nuevo Incidente Operativo"
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={handleCreate} disabled={saving || !form.titulo.trim() || !form.descripcionHechos.trim()}>
              {saving ? 'Guardando...' : 'Registrar Incidente'}
            </Button>
          </div>
        }
      >
        <div className="space-y-4 pt-2">
          <div>
            <label htmlFor="inc-f1" className="block text-xs font-semibold text-foreground mb-1">Título del Incidente *</label>
            <input id="inc-f1"
              type="text"
              value={form.titulo}
              onChange={(e) => setForm({ ...form, titulo: e.target.value })}
              placeholder="Ej: Daño a puerta garita vehicular principal"
              className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label htmlFor="inc-f2" className="block text-xs font-semibold text-foreground mb-1">Tipo de Novedad</label>
              <select id="inc-f2"
                value={form.tipoIncidente}
                onChange={(e) => setForm({ ...form, tipoIncidente: e.target.value })}
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
              >
                {TIPOS_INCIDENTE.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="inc-f3" className="block text-xs font-semibold text-foreground mb-1">Nivel de Severidad</label>
              <select id="inc-f3"
                value={form.nivelSeveridad}
                onChange={(e) => setForm({ ...form, nivelSeveridad: e.target.value })}
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
              >
                <option value="LEVE">Leve</option>
                <option value="MODERADA">Moderada</option>
                <option value="GRAVE">Grave</option>
                <option value="CRITICA">Crítica</option>
              </select>
            </div>
          </div>

          <div>
            <label htmlFor="inc-f4" className="block text-xs font-semibold text-foreground mb-1">Descripción de los Hechos *</label>
            <textarea id="inc-f4"
              rows={3}
              value={form.descripcionHechos}
              onChange={(e) => setForm({ ...form, descripcionHechos: e.target.value })}
              placeholder="Detalle lugar exacto, circunstancias de tiempo y modo..."
              className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
            />
          </div>

          <div className="p-3 bg-muted/30 border border-border rounded-lg space-y-2">
            <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-foreground">
              <input
                type="checkbox"
                checked={form.requirioAutoridades === 'S'}
                onChange={(e) => setForm({ ...form, requirioAutoridades: e.target.checked ? 'S' : 'N' })}
                className="rounded border-border text-primary focus:ring-ring/25"
              />
              ¿Requirió intervención de autoridades externas? (Policía Nacional, Bomberos, Defensa Civil)
            </label>

            {form.requirioAutoridades === 'S' && (
              <div>
                <input
                  type="text"
                  value={form.entidadAutoridad}
                  onChange={(e) => setForm({ ...form, entidadAutoridad: e.target.value })}
                  placeholder="Ej: Cuadrante 12 Policía / Móvil Bomberos Estación Norte"
                  className="w-full text-xs bg-background border border-border rounded-lg px-3 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
                />
              </div>
            )}
          </div>
        </div>
      </Modal>

      {/* Modal: Ver Detalle y Bitácora */}
      {selectedIncidente && (
        <Modal
          open={detalleOpen}
          onClose={() => setDetalleOpen(false)}
          title={`Bitácora de Caso #${selectedIncidente.idIncidente}`}
          footer={
            <Button variant="outline" onClick={() => setDetalleOpen(false)}>
              Cerrar
            </Button>
          }
        >
          <div className="space-y-4 pt-2 text-xs">
            <div>
              <span className="font-semibold text-foreground text-sm">{selectedIncidente.titulo}</span>
              <p className="text-muted-foreground mt-1">{selectedIncidente.descripcionHechos}</p>
            </div>

            <div className="grid grid-cols-2 gap-3 p-3 bg-muted/30 rounded-lg">
              <div>
                <span className="text-muted-foreground">Severidad:</span>
                <p className="font-semibold text-foreground">{selectedIncidente.nivelSeveridad}</p>
              </div>
              <div>
                <span className="text-muted-foreground">Estado actual:</span>
                <p className="font-semibold text-foreground">{selectedIncidente.estado}</p>
              </div>
              <div>
                <span className="text-muted-foreground">Fecha / Hora:</span>
                <p className="font-semibold text-foreground">
                  {selectedIncidente.fechaHoraIncidente ? new Date(selectedIncidente.fechaHoraIncidente).toLocaleString() : '—'}
                </p>
              </div>
              <div>
                <span className="text-muted-foreground">Autoridades:</span>
                <p className="font-semibold text-foreground">
                  {selectedIncidente.requirioAutoridades === 'S' ? selectedIncidente.entidadAutoridad || 'Asistieron' : 'No requirió'}
                </p>
              </div>
            </div>

            {selectedIncidente.investigadorAsignado && (
              <div className="p-3 border border-tint-blue-fg/25 bg-tint-blue-bg rounded-lg space-y-1">
                <span className="font-semibold text-tint-blue-fg">Investigación Oficial</span>
                <p className="text-foreground">
                  <strong>Investigador:</strong> {selectedIncidente.investigadorAsignado}
                </p>
                {selectedIncidente.lineasInvestigacion && (
                  <p className="text-muted-foreground">
                    <strong>Líneas:</strong> {selectedIncidente.lineasInvestigacion}
                  </p>
                )}
                {selectedIncidente.hallazgosInvestigacion && (
                  <p className="text-foreground mt-1">
                    <strong>Hallazgos:</strong> {selectedIncidente.hallazgosInvestigacion}
                  </p>
                )}
              </div>
            )}

            {selectedIncidente.conclusionesCierre && (
              <div className="p-3 border border-tint-green-fg/25 bg-tint-green-bg rounded-lg space-y-1">
                <span className="font-semibold text-tint-green-fg">Resolución y Cierre</span>
                <p className="text-foreground">{selectedIncidente.conclusionesCierre}</p>
              </div>
            )}
          </div>
        </Modal>
      )}

      {/* Modal: Gestión de Investigación */}
      {selectedIncidente && (
        <Modal
          open={investigacionOpen}
          onClose={() => setInvestigacionOpen(false)}
          title={`Investigación de Caso #${selectedIncidente.idIncidente}`}
          footer={
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setInvestigacionOpen(false)}>
                Cancelar
              </Button>
              <Button onClick={handleInvestigacion} disabled={saving}>
                {saving ? 'Guardando...' : invForm.concluir ? 'Concluir Investigación' : 'Guardar Pesquisas'}
              </Button>
            </div>
          }
        >
          <div className="space-y-4 pt-2">
            <div>
              <label htmlFor="inc-f5" className="block text-xs font-semibold text-foreground mb-1">Oficial / Investigador Asignado</label>
              <input id="inc-f5"
                type="text"
                value={invForm.investigadorAsignado}
                onChange={(e) => setInvForm({ ...invForm, investigadorAsignado: e.target.value })}
                placeholder="Nombre del administrador o supervisor de seguridad..."
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
              />
            </div>

            <div>
              <label htmlFor="inc-f6" className="block text-xs font-semibold text-foreground mb-1">Líneas de Investigación</label>
              <textarea id="inc-f6"
                rows={2}
                value={invForm.lineasInvestigacion}
                onChange={(e) => setInvForm({ ...invForm, lineasInvestigacion: e.target.value })}
                placeholder="Revisión de cámaras CCTV torre 2, entrevistas a vigilante de turno..."
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
              />
            </div>

            <div>
              <label htmlFor="inc-f7" className="block text-xs font-semibold text-foreground mb-1">Hallazgos y Evidencias</label>
              <textarea id="inc-f7"
                rows={3}
                value={invForm.hallazgos}
                onChange={(e) => setInvForm({ ...invForm, hallazgos: e.target.value })}
                placeholder="Hallazgos preliminares o definitivos recaudados..."
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
              />
            </div>

            <label className="flex items-center gap-2 cursor-pointer text-xs font-semibold text-foreground p-2 bg-muted/40 rounded-lg">
              <input
                type="checkbox"
                checked={invForm.concluir}
                onChange={(e) => setInvForm({ ...invForm, concluir: e.target.checked })}
                className="rounded border-border text-primary focus:ring-ring/25"
              />
              Concluir formalmente la etapa de investigación
            </label>
          </div>
        </Modal>
      )}

      {/* Modal: Escalar a Sanción */}
      {selectedIncidente && (
        <Modal
          open={escalarOpen}
          onClose={() => setEscalarOpen(false)}
          title={`Escalar Incidente #${selectedIncidente.idIncidente} a Sanción`}
          footer={
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setEscalarOpen(false)}>
                Cancelar
              </Button>
              <Button onClick={handleEscalar} disabled={saving || !escForm.justificacionEscalamiento.trim()}>
                {saving ? 'Escalando...' : 'Escalar a Comité'}
              </Button>
            </div>
          }
        >
          <div className="space-y-4 pt-2">
            <p className="text-xs text-muted-foreground">
              Este procedimiento remite el caso al Comité de Convivencia y Consejo de Administración para apertura de pliego de cargos según el Reglamento de Propiedad Horizontal.
            </p>

            <div>
              <label htmlFor="inc-f8" className="block text-xs font-semibold text-foreground mb-1">Justificación del Escalamiento *</label>
              <textarea id="inc-f8"
                rows={3}
                value={escForm.justificacionEscalamiento}
                onChange={(e) => setEscForm({ ...escForm, justificacionEscalamiento: e.target.value })}
                placeholder="Motivo por el cual la falta amerita trámite disciplinario y sanción pecuniaria..."
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
              />
            </div>

            <div>
              <label htmlFor="inc-f9" className="block text-xs font-semibold text-foreground mb-1">Sanción Sugerida (Opcional)</label>
              <input id="inc-f9"
                type="text"
                value={escForm.sancionSugerida}
                onChange={(e) => setEscForm({ ...escForm, sancionSugerida: e.target.value })}
                placeholder="Ej: Multa económica tipo 1 (0.5 cuotas ordinarias)"
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
              />
            </div>
          </div>
        </Modal>
      )}

      {/* Modal: Cerrar Incidente */}
      {selectedIncidente && (
        <Modal
          open={cierreOpen}
          onClose={() => setCierreOpen(false)}
          title={`Cerrar Definitivamente Incidente #${selectedIncidente.idIncidente}`}
          footer={
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setCierreOpen(false)}>
                Cancelar
              </Button>
              <Button onClick={handleCerrar} disabled={saving || !conclusiones.trim()}>
                {saving ? 'Cerrando...' : 'Confirmar Cierre'}
              </Button>
            </div>
          }
        >
          <div className="space-y-3 pt-2">
            <p className="text-xs text-muted-foreground">
              Indique cómo se subsanó la novedad o el acuerdo alcanzado entre las partes.
            </p>
            <textarea
              rows={3}
              value={conclusiones}
              onChange={(e) => setConclusiones(e.target.value)}
              placeholder="Conclusiones finales y acuerdos de cierre..."
              className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
            />
          </div>
        </Modal>
      )}

      {/* Modal: Reabrir Incidente */}
      {selectedIncidente && (
        <Modal
          open={reabrirOpen}
          onClose={() => setReabrirOpen(false)}
          title={`Reabrir Incidente #${selectedIncidente.idIncidente}`}
          footer={
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setReabrirOpen(false)}>
                Cancelar
              </Button>
              <Button onClick={handleReabrir} disabled={saving || !motivoReapertura.trim()}>
                {saving ? 'Reabriendo...' : 'Confirmar Reapertura'}
              </Button>
            </div>
          }
        >
          <div className="space-y-3 pt-2">
            <p className="text-xs text-muted-foreground">
              El caso volverá al estado <strong>En Investigación</strong>. Indique la causa formal (nuevas pruebas, queja reiterada, etc.).
            </p>
            <textarea
              rows={3}
              value={motivoReapertura}
              onChange={(e) => setMotivoReapertura(e.target.value)}
              placeholder="Motivo formal de la reapertura..."
              className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
            />
          </div>
        </Modal>
      )}

      {/* Modal: Personas Involucradas */}
      {selectedIncidente && (
        <Modal
          open={involucradosOpen}
          onClose={() => setInvolucradosOpen(false)}
          title={`Personas Involucradas — Caso #${selectedIncidente.idIncidente}`}
          size="lg"
          footer={
            <Button variant="outline" onClick={() => setInvolucradosOpen(false)}>
              Cerrar
            </Button>
          }
        >
          <div className="space-y-4 pt-2">
            <div className="text-xs text-muted-foreground font-medium">
              Caso: <strong className="text-foreground">{selectedIncidente.titulo}</strong>
            </div>

            {/* List */}
            <div className="border border-border rounded-xl p-3 bg-muted/20">
              <span className="text-xs font-semibold text-foreground block mb-2">Personas Vinculadas al Incidente</span>
              {invLoading ? (
                <div className="py-4 text-center text-xs text-muted-foreground">Cargando...</div>
              ) : involucradosList.length === 0 ? (
                <div className="py-6 text-center text-xs text-muted-foreground">
                  No hay personas vinculadas a este incidente aún.
                </div>
              ) : (
                <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
                  {involucradosList.map((inv) => (
                    <div
                      key={inv.idInvolucrado}
                      className="p-2.5 bg-card border border-border rounded-lg flex items-start justify-between gap-3 text-xs"
                    >
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-foreground">
                            {inv.nombreCompleto || inv.nombreIdentificacionExterna || 'Anónimo'}
                          </span>
                          <span className="px-2 py-0.5 rounded text-xs font-medium bg-secondary text-secondary-foreground">
                            {inv.rolInvolucrado}
                          </span>
                          {inv.idPersona ? (
                            <span className="text-xs text-tint-blue-fg font-medium">Residente</span>
                          ) : (
                            <span className="text-xs text-muted-foreground">Externo</span>
                          )}
                        </div>
                        {inv.declaracionRendida && (
                          <p className="text-xs text-muted-foreground mt-1 italic">
                            "{inv.declaracionRendida}"
                          </p>
                        )}
                      </div>
                      <button
                        onClick={() => handleDeleteInvolucrado(inv.idInvolucrado)}
                        className="text-destructive hover:bg-destructive/10 p-1 rounded transition-colors"
                        title="Remover"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Form to add */}
            <div className="border-t border-border pt-3 space-y-3">
              <span className="text-xs font-semibold text-foreground block">Vincular Nueva Persona</span>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label htmlFor="inc-f10" className="block text-xs font-medium text-foreground mb-1">Rol en el Incidente</label>
                  <select id="inc-f10"
                    value={newInvolucrado.rolInvolucrado}
                    onChange={(e) => setNewInvolucrado({ ...newInvolucrado, rolInvolucrado: e.target.value })}
                    className="w-full text-xs bg-background border border-border rounded-lg px-2.5 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
                  >
                    <option value="AFECTADO">Afectado / Víctima</option>
                    <option value="TESTIGO">Testigo Presencial</option>
                    <option value="PRESUNTO_RESPONSABLE">Presunto Responsable</option>
                    <option value="INTERVINIENTE">Interviniente</option>
                    <option value="OTRO">Otro</option>
                  </select>
                </div>

                <div>
                  <label htmlFor="inc-f11" className="block text-xs font-medium text-foreground mb-1">ID Persona (Si es Residente registrado)</label>
                  <input id="inc-f11"
                    type="number"
                    value={newInvolucrado.idPersona}
                    onChange={(e) => setNewInvolucrado({ ...newInvolucrado, idPersona: e.target.value })}
                    placeholder="ID en el sistema..."
                    className="w-full text-xs bg-background border border-border rounded-lg px-2.5 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
                  />
                </div>
              </div>

              <div>
                <label htmlFor="inc-f12" className="block text-xs font-medium text-foreground mb-1">
                  Nombre e Identificación Externa (Si es visitante o tercero)
                </label>
                <input id="inc-f12"
                  type="text"
                  value={newInvolucrado.nombreIdentificacionExterna}
                  onChange={(e) => setNewInvolucrado({ ...newInvolucrado, nombreIdentificacionExterna: e.target.value })}
                  placeholder="Ej: Carlos Mendoza - CC 10203040"
                  className="w-full text-xs bg-background border border-border rounded-lg px-2.5 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
                />
              </div>

              <div>
                <label htmlFor="inc-f13" className="block text-xs font-medium text-foreground mb-1">Declaración o Testimonio</label>
                <textarea id="inc-f13"
                  rows={2}
                  value={newInvolucrado.declaracionRendida}
                  onChange={(e) => setNewInvolucrado({ ...newInvolucrado, declaracionRendida: e.target.value })}
                  placeholder="Manifestación o declaración voluntaria rendida..."
                  className="w-full text-xs bg-background border border-border rounded-lg px-2.5 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-ring/25"
                />
              </div>

              <Button
                size="sm"
                onClick={handleAddInvolucrado}
                disabled={!newInvolucrado.idPersona && !newInvolucrado.nombreIdentificacionExterna}
                className="w-full"
              >
                Vincular al Caso
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
