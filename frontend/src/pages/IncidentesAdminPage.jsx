import React, { useState, useMemo } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { toast } from 'sonner';
import { PageHeader } from '../components/ui/PageHeader.jsx';
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
  LEVE: { label: 'Leve', badge: 'bg-slate-500/10 text-slate-700 dark:text-slate-300 border-slate-500/20' },
  MODERADA: { label: 'Moderada', badge: 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/20' },
  GRAVE: { label: 'Grave', badge: 'bg-orange-500/10 text-orange-700 dark:text-orange-400 border-orange-500/20' },
  CRITICA: { label: 'Crítica', badge: 'bg-destructive/10 text-destructive border-destructive/20 font-bold' },
};

const ESTADOS_INCIDENTE = {
  REPORTADO: { label: 'Reportado', badge: 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/20' },
  EN_INVESTIGACION: { label: 'En Investigación', badge: 'bg-blue-500/10 text-blue-700 dark:text-blue-400 border-blue-500/20' },
  ESCALADO_A_SANCION: { label: 'Escalado a Sanción', badge: 'bg-purple-500/10 text-purple-700 dark:text-purple-400 border-purple-500/20' },
  ACCION_TOMADA: { label: 'Acción Tomada', badge: 'bg-cyan-500/10 text-cyan-700 dark:text-cyan-400 border-cyan-500/20' },
  CERRADO: { label: 'Cerrado', badge: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20' },
};

export default function IncidentesAdminPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/incidentes/admin'), []);

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
      await api.post(`/incidentes/${selectedIncidente.idIncidente}/reabrir`, { motivoReapertura });
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
        title="Gestión de Incidentes y Bitácora"
        subtitle="Registro operativo de novedades, investigación interna, personas involucradas y escalamiento a sanciones"
        action={
          <Button onClick={() => setModalOpen(true)} className="flex items-center gap-2">
            <Plus className="w-4 h-4" />
            Reportar Incidente
          </Button>
        }
      />

      {/* KPI Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-card border border-border rounded-xl p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-muted-foreground">Total Incidentes</span>
            <AlertTriangle className="w-4 h-4 text-muted-foreground" />
          </div>
          <div className="mt-2 text-2xl font-bold text-foreground">{kpis.total}</div>
          <div className="text-xs text-muted-foreground mt-1">Histórico registrado</div>
        </div>

        <div className="bg-card border border-border rounded-xl p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-blue-600 dark:text-blue-400">En Investigación</span>
            <Clock className="w-4 h-4 text-blue-500" />
          </div>
          <div className="mt-2 text-2xl font-bold text-blue-600 dark:text-blue-400">{kpis.investigacion}</div>
          <div className="text-xs text-muted-foreground mt-1">Con pesquisas abiertas</div>
        </div>

        <div className="bg-card border border-border rounded-xl p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-amber-600 dark:text-amber-400">Escalados a Sanción</span>
            <ArrowUpRight className="w-4 h-4 text-amber-500" />
          </div>
          <div className="mt-2 text-2xl font-bold text-amber-600 dark:text-amber-400">{kpis.escalados}</div>
          <div className="text-xs text-muted-foreground mt-1">Para comité de convivencia</div>
        </div>

        <div className="bg-card border border-border rounded-xl p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-emerald-600 dark:text-emerald-400">Cerrados / Resueltos</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-500" />
          </div>
          <div className="mt-2 text-2xl font-bold text-emerald-600 dark:text-emerald-400">{kpis.cerrados}</div>
          <div className="text-xs text-muted-foreground mt-1">Con resolución final</div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-card border border-border rounded-xl p-4 flex flex-col sm:flex-row gap-3 items-center justify-between">
        <div className="flex flex-wrap gap-2 items-center w-full sm:w-auto">
          <select
            value={filtroEstado}
            onChange={(e) => setFiltroEstado(e.target.value)}
            className="text-xs font-medium bg-background border border-border rounded-lg px-3 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
          >
            <option value="TODOS">Todos los Estados</option>
            <option value="REPORTADO">Reportados</option>
            <option value="EN_INVESTIGACION">En Investigación</option>
            <option value="ESCALADO_A_SANCION">Escalados a Sanción</option>
            <option value="CERRADO">Cerrados</option>
          </select>

          <select
            value={filtroSeveridad}
            onChange={(e) => setFiltroSeveridad(e.target.value)}
            className="text-xs font-medium bg-background border border-border rounded-lg px-3 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
          >
            <option value="TODOS">Todas las Severidades</option>
            <option value="LEVE">Leve</option>
            <option value="MODERADA">Moderada</option>
            <option value="GRAVE">Grave</option>
            <option value="CRITICA">Crítica</option>
          </select>
        </div>

        <div className="relative w-full sm:w-72">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por título, hecho o investigador..."
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 text-xs bg-background border border-border rounded-lg text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </div>
      </div>

      {/* Data Table */}
      {loading ? (
        <div className="p-12 text-center text-muted-foreground bg-card border border-border rounded-xl">
          <div className="animate-spin w-8 h-8 border-4 border-primary border-t-transparent rounded-full mx-auto mb-3" />
          <p className="text-sm">Cargando incidentes y bitácora de seguridad...</p>
        </div>
      ) : filteredItems.length === 0 ? (
        <div className="p-12 text-center bg-card border border-border rounded-xl">
          <ShieldAlert className="w-12 h-12 text-muted-foreground/40 mx-auto mb-3" />
          <h3 className="text-base font-semibold text-foreground">No se encontraron incidentes</h3>
          <p className="text-xs text-muted-foreground mt-1 max-w-sm mx-auto">
            {busqueda || filtroEstado !== 'TODOS' || filtroSeveridad !== 'TODOS'
              ? 'No hay registros que coincidan con los filtros aplicados.'
              : 'No hay novedades o incidentes reportados en la copropiedad.'}
          </p>
        </div>
      ) : (
        <div className="bg-card border border-border rounded-xl overflow-hidden shadow-xs">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-muted/40 border-b border-border text-muted-foreground font-semibold">
                  <th className="py-3 px-4">Fecha / ID</th>
                  <th className="py-3 px-4">Título y Hechos</th>
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
                    <tr key={i.idIncidente} className="hover:bg-muted/30 transition-colors">
                      <td className="py-3 px-4 align-top whitespace-nowrap">
                        <div className="font-semibold text-foreground">
                          {i.fechaHoraIncidente ? new Date(i.fechaHoraIncidente).toLocaleDateString() : '—'}
                        </div>
                        <div className="text-[11px] text-muted-foreground">
                          {i.fechaHoraIncidente ? new Date(i.fechaHoraIncidente).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                        </div>
                        <span className="text-[10px] text-muted-foreground font-mono">#{i.idIncidente}</span>
                      </td>

                      <td className="py-3 px-4 align-top max-w-xs sm:max-w-sm">
                        <div className="font-semibold text-foreground text-sm">{i.titulo}</div>
                        <div className="text-[11px] text-muted-foreground line-clamp-2 mt-0.5">
                          {i.descripcionHechos}
                        </div>
                        <div className="flex flex-wrap items-center gap-2 mt-1.5">
                          <span className="text-[10px] bg-secondary text-secondary-foreground px-2 py-0.5 rounded font-medium">
                            {TIPOS_INCIDENTE.find((t) => t.id === i.tipoIncidente)?.label || i.tipoIncidente}
                          </span>
                          {i.investigadorAsignado && (
                            <span className="text-[10px] text-blue-600 dark:text-blue-400 font-medium">
                              Investigador: {i.investigadorAsignado}
                            </span>
                          )}
                        </div>
                      </td>

                      <td className="py-3 px-4 align-top whitespace-nowrap">
                        <span className={`inline-block px-2 py-0.5 rounded-full text-[10px] border font-medium ${sevCfg.badge}`}>
                          {sevCfg.label}
                        </span>
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
                        <span className={`inline-block px-2.5 py-0.5 rounded-full text-[11px] border font-medium ${estCfg.badge}`}>
                          {estCfg.label}
                        </span>
                      </td>

                      <td className="py-3 px-4 align-top text-right whitespace-nowrap">
                        <div className="flex items-center justify-end gap-1.5">
                          {/* Detalle */}
                          <button
                            onClick={() => {
                              setSelectedIncidente(i);
                              setDetalleOpen(true);
                            }}
                            className="p-1 text-muted-foreground hover:text-foreground rounded hover:bg-muted"
                            title="Ver Bitácora Completa"
                          >
                            <Eye className="w-4 h-4" />
                          </button>

                          {/* Involucrados */}
                          <button
                            onClick={() => loadInvolucrados(i)}
                            className="px-2 py-1 text-xs font-medium border border-border rounded-lg bg-card text-foreground hover:bg-muted transition-colors flex items-center gap-1"
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
                              className="px-2 py-1 text-xs font-medium bg-blue-500/10 text-blue-700 dark:text-blue-300 border border-blue-500/20 rounded-lg hover:bg-blue-500/20 transition-colors"
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
                              className="px-2 py-1 text-xs font-medium bg-purple-500/10 text-purple-700 dark:text-purple-300 border border-purple-500/20 rounded-lg hover:bg-purple-500/20 transition-colors"
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
                              className="px-2 py-1 text-xs font-medium bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-500/20 rounded-lg hover:bg-emerald-500/20 transition-colors"
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
                              className="px-2 py-1 text-xs font-medium bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20 rounded-lg hover:bg-amber-500/20 transition-colors flex items-center gap-1"
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
            <label className="block text-xs font-semibold text-foreground mb-1">Título del Incidente *</label>
            <input
              type="text"
              value={form.titulo}
              onChange={(e) => setForm({ ...form, titulo: e.target.value })}
              placeholder="Ej: Daño a puerta garita vehicular principal"
              className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">Tipo de Novedad</label>
              <select
                value={form.tipoIncidente}
                onChange={(e) => setForm({ ...form, tipoIncidente: e.target.value })}
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                {TIPOS_INCIDENTE.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">Nivel de Severidad</label>
              <select
                value={form.nivelSeveridad}
                onChange={(e) => setForm({ ...form, nivelSeveridad: e.target.value })}
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              >
                <option value="LEVE">Leve</option>
                <option value="MODERADA">Moderada</option>
                <option value="GRAVE">Grave</option>
                <option value="CRITICA">Crítica</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">Descripción de los Hechos *</label>
            <textarea
              rows={3}
              value={form.descripcionHechos}
              onChange={(e) => setForm({ ...form, descripcionHechos: e.target.value })}
              placeholder="Detalle lugar exacto, circunstancias de tiempo y modo..."
              className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>

          <div className="p-3 bg-muted/30 border border-border rounded-lg space-y-2">
            <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-foreground">
              <input
                type="checkbox"
                checked={form.requirioAutoridades === 'S'}
                onChange={(e) => setForm({ ...form, requirioAutoridades: e.target.checked ? 'S' : 'N' })}
                className="rounded border-border text-primary focus:ring-primary/20"
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
                  className="w-full text-xs bg-background border border-border rounded-lg px-3 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
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
              <div className="p-3 border border-blue-500/20 bg-blue-500/5 rounded-lg space-y-1">
                <span className="font-semibold text-blue-700 dark:text-blue-300">Investigación Oficial</span>
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
              <div className="p-3 border border-emerald-500/20 bg-emerald-500/5 rounded-lg space-y-1">
                <span className="font-semibold text-emerald-700 dark:text-emerald-300">Resolución y Cierre</span>
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
              <label className="block text-xs font-semibold text-foreground mb-1">Oficial / Investigador Asignado</label>
              <input
                type="text"
                value={invForm.investigadorAsignado}
                onChange={(e) => setInvForm({ ...invForm, investigadorAsignado: e.target.value })}
                placeholder="Nombre del administrador o supervisor de seguridad..."
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">Líneas de Investigación</label>
              <textarea
                rows={2}
                value={invForm.lineasInvestigacion}
                onChange={(e) => setInvForm({ ...invForm, lineasInvestigacion: e.target.value })}
                placeholder="Revisión de cámaras CCTV torre 2, entrevistas a vigilante de turno..."
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">Hallazgos y Evidencias</label>
              <textarea
                rows={3}
                value={invForm.hallazgos}
                onChange={(e) => setInvForm({ ...invForm, hallazgos: e.target.value })}
                placeholder="Hallazgos preliminares o definitivos recaudados..."
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </div>

            <label className="flex items-center gap-2 cursor-pointer text-xs font-semibold text-foreground p-2 bg-muted/40 rounded-lg">
              <input
                type="checkbox"
                checked={invForm.concluir}
                onChange={(e) => setInvForm({ ...invForm, concluir: e.target.checked })}
                className="rounded border-border text-primary focus:ring-primary/20"
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
              <label className="block text-xs font-semibold text-foreground mb-1">Justificación del Escalamiento *</label>
              <textarea
                rows={3}
                value={escForm.justificacionEscalamiento}
                onChange={(e) => setEscForm({ ...escForm, justificacionEscalamiento: e.target.value })}
                placeholder="Motivo por el cual la falta amerita trámite disciplinario y sanción pecuniaria..."
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">Sanción Sugerida (Opcional)</label>
              <input
                type="text"
                value={escForm.sancionSugerida}
                onChange={(e) => setEscForm({ ...escForm, sancionSugerida: e.target.value })}
                placeholder="Ej: Multa económica tipo 1 (0.5 cuotas ordinarias)"
                className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
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
              className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
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
              className="w-full text-xs bg-background border border-border rounded-lg px-3 py-2 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
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
                          <span className="px-2 py-0.5 rounded text-[10px] font-medium bg-secondary text-secondary-foreground">
                            {inv.rolInvolucrado}
                          </span>
                          {inv.idPersona ? (
                            <span className="text-[10px] text-blue-600 dark:text-blue-400 font-medium">Residente</span>
                          ) : (
                            <span className="text-[10px] text-muted-foreground">Externo</span>
                          )}
                        </div>
                        {inv.declaracionRendida && (
                          <p className="text-[11px] text-muted-foreground mt-1 italic">
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
                  <label className="block text-[11px] font-medium text-foreground mb-1">Rol en el Incidente</label>
                  <select
                    value={newInvolucrado.rolInvolucrado}
                    onChange={(e) => setNewInvolucrado({ ...newInvolucrado, rolInvolucrado: e.target.value })}
                    className="w-full text-xs bg-background border border-border rounded-lg px-2.5 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
                  >
                    <option value="AFECTADO">Afectado / Víctima</option>
                    <option value="TESTIGO">Testigo Presencial</option>
                    <option value="PRESUNTO_RESPONSABLE">Presunto Responsable</option>
                    <option value="INTERVINIENTE">Interviniente</option>
                    <option value="OTRO">Otro</option>
                  </select>
                </div>

                <div>
                  <label className="block text-[11px] font-medium text-foreground mb-1">ID Persona (Si es Residente registrado)</label>
                  <input
                    type="number"
                    value={newInvolucrado.idPersona}
                    onChange={(e) => setNewInvolucrado({ ...newInvolucrado, idPersona: e.target.value })}
                    placeholder="ID en el sistema..."
                    className="w-full text-xs bg-background border border-border rounded-lg px-2.5 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-medium text-foreground mb-1">
                  Nombre e Identificación Externa (Si es visitante o tercero)
                </label>
                <input
                  type="text"
                  value={newInvolucrado.nombreIdentificacionExterna}
                  onChange={(e) => setNewInvolucrado({ ...newInvolucrado, nombreIdentificacionExterna: e.target.value })}
                  placeholder="Ej: Carlos Mendoza - CC 10203040"
                  className="w-full text-xs bg-background border border-border rounded-lg px-2.5 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
                />
              </div>

              <div>
                <label className="block text-[11px] font-medium text-foreground mb-1">Declaración o Testimonio</label>
                <textarea
                  rows={2}
                  value={newInvolucrado.declaracionRendida}
                  onChange={(e) => setNewInvolucrado({ ...newInvolucrado, declaracionRendida: e.target.value })}
                  placeholder="Manifestación o declaración voluntaria rendida..."
                  className="w-full text-xs bg-background border border-border rounded-lg px-2.5 py-1.5 text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
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
