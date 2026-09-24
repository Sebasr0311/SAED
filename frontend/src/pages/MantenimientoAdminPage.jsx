import React, { useState, useEffect, useMemo } from 'react';
import {
  Wrench,
  Plus,
  Search,
  Eye,
  Edit2,
  Calendar,
  Clock,
  CheckCircle2,
  AlertTriangle,
  Ban,
  Layers,
  Building2,
  DollarSign,
  FileText,
  ShieldAlert,
  Play,
  ArrowRight,
  ExternalLink,
  MapPin,
  User,
  Phone,
  Mail,
  RefreshCw,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { api } from '../lib/api.js';
import { formatCurrency, formatDate } from '../lib/utils.js';
import { toast } from 'sonner';

const TIPOS_MANTENIMIENTO = [
  { id: 'PREVENTIVO', label: 'Preventivo', badge: 'bg-blue-500/10 text-blue-700 dark:text-blue-300 border-blue-500/20' },
  { id: 'CORRECTIVO', label: 'Correctivo', badge: 'bg-amber-500/10 text-amber-700 dark:text-amber-300 border-amber-500/20' },
  { id: 'PREDICTIVO', label: 'Predictivo', badge: 'bg-purple-500/10 text-purple-700 dark:text-purple-300 border-purple-500/20' },
  { id: 'EMERGENCIA', label: 'Emergencia', badge: 'bg-rose-500/10 text-rose-700 dark:text-rose-300 border-rose-500/20' },
  { id: 'LOCATIVO', label: 'Locativo', badge: 'bg-slate-500/10 text-slate-700 dark:text-slate-300 border-slate-500/20' },
];

const PRIORIDADES = [
  { id: 'BAJA', label: 'Baja', badge: 'bg-slate-500/10 text-slate-700 dark:text-slate-300 border-slate-500/20' },
  { id: 'MEDIA', label: 'Media', badge: 'bg-blue-500/10 text-blue-700 dark:text-blue-300 border-blue-500/20' },
  { id: 'ALTA', label: 'Alta', badge: 'bg-amber-500/10 text-amber-700 dark:text-amber-300 border-amber-500/20' },
  { id: 'URGENTE', label: 'Urgente', badge: 'bg-red-500/10 text-red-700 dark:text-red-300 border-red-500/20' },
];

const ESTADOS = [
  { id: 'PROGRAMADO', label: 'Programado', badge: 'bg-sky-500/10 text-sky-700 dark:text-sky-300 border-sky-500/20' },
  { id: 'EN_PROCESO', label: 'En Proceso', badge: 'bg-amber-500/10 text-amber-700 dark:text-amber-300 border-amber-500/20' },
  { id: 'COMPLETADO', label: 'Completado', badge: 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/20' },
  { id: 'REPROGRAMADO', label: 'Reprogramado', badge: 'bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border-indigo-500/20' },
  { id: 'CANCELADO', label: 'Cancelado', badge: 'bg-zinc-500/10 text-zinc-700 dark:text-zinc-400 border-zinc-500/20' },
];

export default function MantenimientoAdminPage() {
  const [mantenimientos, setMantenimientos] = useState([]);
  const [activos, setActivos] = useState([]);
  const [proveedores, setProveedores] = useState([]);
  const [zonasComunes, setZonasComunes] = useState([]);
  const [kpis, setKpis] = useState(null);

  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterEstado, setFilterEstado] = useState('TODOS');
  const [filterTipo, setFilterTipo] = useState('TODOS');
  const [filterPrioridad, setFilterPrioridad] = useState('TODAS');

  // Modales
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showCompleteModal, setShowCompleteModal] = useState(false);
  const [showRescheduleModal, setShowRescheduleModal] = useState(false);
  const [cancelTarget, setCancelTarget] = useState(null);
  const [startTarget, setStartTarget] = useState(null);
  const [selectedMantenimiento, setSelectedMantenimiento] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Form states
  const initialCreateForm = {
    titulo: '',
    tipoMantenimiento: 'PREVENTIVO',
    prioridad: 'MEDIA',
    descripcionTrabajo: '',
    fechaProgramada: new Date().toISOString().split('T')[0],
    costoEstimado: '',
    tecnicoResponsable: '',
    idActivo: '',
    idProveedorServicio: '',
    evidenciaAntesUrl: '',
    requiereBloqueoZona: false,
    idZonaBloqueo: '',
    fechaInicioBloqueo: '',
    fechaFinBloqueo: '',
    motivoBloqueo: '',
  };
  const [createForm, setCreateForm] = useState(initialCreateForm);

  const [editForm, setEditForm] = useState({
    titulo: '',
    tipoMantenimiento: 'PREVENTIVO',
    prioridad: 'MEDIA',
    descripcionTrabajo: '',
    fechaProgramada: '',
    costoEstimado: '',
    tecnicoResponsable: '',
    idActivo: '',
    idProveedorServicio: '',
    evidenciaAntesUrl: '',
  });

  const [completeForm, setCompleteForm] = useState({
    costoReal: '',
    fechaEjecucion: new Date().toISOString().slice(0, 16),
    notasCierre: '',
    informeTecnicoUrl: '',
    evidenciaDespuesUrl: '',
  });

  const [rescheduleForm, setRescheduleForm] = useState({
    nuevaFechaProgramada: '',
    nuevaFechaInicioBloqueo: '',
    nuevaFechaFinBloqueo: '',
    motivo: '',
  });

  const cargarDatos = async () => {
    try {
      setLoading(true);
      const [mantsRes, kpisRes, activosRes, provsRes, zonasRes] = await Promise.allSettled([
        api.get('/mantenimientos'),
        api.get('/mantenimientos/kpis'),
        api.get('/activos'),
        api.get('/proveedores'),
        api.get('/reservas/zonas'),
      ]);

      if (mantsRes.status === 'fulfilled') {
        const data = mantsRes.value?.data || mantsRes.value || [];
        setMantenimientos(Array.isArray(data) ? data : []);
      }
      if (kpisRes.status === 'fulfilled') {
        setKpis(kpisRes.value?.data || kpisRes.value || null);
      }
      if (activosRes.status === 'fulfilled') {
        const data = activosRes.value?.data || activosRes.value || [];
        setActivos(Array.isArray(data) ? data : []);
      }
      if (provsRes.status === 'fulfilled') {
        const data = provsRes.value?.data || provsRes.value || [];
        setProveedores(Array.isArray(data) ? data : []);
      }
      if (zonasRes.status === 'fulfilled') {
        const data = zonasRes.value?.data || zonasRes.value || [];
        setZonasComunes(Array.isArray(data) ? data : []);
      }
    } catch (err) {
      console.error('Error cargando módulo de mantenimientos:', err);
      toast.error('No se pudo cargar la información de mantenimientos');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    cargarDatos();
  }, []);

  const stats = useMemo(() => {
    if (kpis) {
      return {
        total: Number(kpis.TOTAL || 0),
        programados: Number(kpis.PROGRAMADOS || 0) + Number(kpis.REPROGRAMADOS || 0),
        enProceso: Number(kpis.EN_PROCESO || 0),
        completados: Number(kpis.COMPLETADOS || 0),
        urgentes: Number(kpis.URGENTES || 0),
        gastoTotalReal: Number(kpis.GASTO_TOTAL_REAL || 0),
        costoEstimadoPendiente: Number(kpis.COSTO_ESTIMADO_PENDIENTE || 0),
      };
    }
    const total = mantenimientos.length;
    const programados = mantenimientos.filter((m) => m.estado === 'PROGRAMADO' || m.estado === 'REPROGRAMADO').length;
    const enProceso = mantenimientos.filter((m) => m.estado === 'EN_PROCESO').length;
    const completados = mantenimientos.filter((m) => m.estado === 'COMPLETADO').length;
    const urgentes = mantenimientos.filter(
      (m) => m.prioridad === 'URGENTE' && m.estado !== 'COMPLETADO' && m.estado !== 'CANCELADO'
    ).length;
    return { total, programados, enProceso, completados, urgentes, gastoTotalReal: 0, costoEstimadoPendiente: 0 };
  }, [mantenimientos, kpis]);

  const mantenimientosFiltrados = useMemo(() => {
    return mantenimientos.filter((m) => {
      const matchSearch =
        searchTerm === '' ||
        (m.titulo && m.titulo.toLowerCase().includes(searchTerm.toLowerCase())) ||
        (m.nombreActivo && m.nombreActivo.toLowerCase().includes(searchTerm.toLowerCase())) ||
        (m.codigoActivo && m.codigoActivo.toLowerCase().includes(searchTerm.toLowerCase())) ||
        (m.tecnicoResponsable && m.tecnicoResponsable.toLowerCase().includes(searchTerm.toLowerCase())) ||
        (m.razonSocialProveedor && m.razonSocialProveedor.toLowerCase().includes(searchTerm.toLowerCase()));

      const matchEstado = filterEstado === 'TODOS' || m.estado === filterEstado;
      const matchTipo = filterTipo === 'TODOS' || m.tipoMantenimiento === filterTipo;
      const matchPrioridad = filterPrioridad === 'TODAS' || m.prioridad === filterPrioridad;

      return matchSearch && matchEstado && matchTipo && matchPrioridad;
    });
  }, [mantenimientos, searchTerm, filterEstado, filterTipo, filterPrioridad]);

  const getTipoBadge = (tipo) => {
    const item = TIPOS_MANTENIMIENTO.find((t) => t.id === tipo);
    return item ? item.badge : 'bg-slate-500/10 text-slate-700 dark:text-slate-300 border-slate-500/20';
  };

  const getPrioridadBadge = (prio) => {
    const item = PRIORIDADES.find((p) => p.id === prio);
    return item ? item.badge : 'bg-slate-500/10 text-slate-700 dark:text-slate-300 border-slate-500/20';
  };

  const getEstadoBadge = (est) => {
    const item = ESTADOS.find((e) => e.id === est);
    return item ? item.badge : 'bg-slate-500/10 text-slate-700 dark:text-slate-300 border-slate-500/20';
  };

  // Handlers
  const handleCreate = async (e) => {
    e.preventDefault();
    if (!createForm.titulo.trim()) {
      toast.error('El título del mantenimiento es obligatorio');
      return;
    }
    if (!createForm.descripcionTrabajo.trim()) {
      toast.error('La descripción del trabajo es obligatoria');
      return;
    }
    if (!createForm.fechaProgramada) {
      toast.error('La fecha programada es obligatoria');
      return;
    }

    const payload = {
      titulo: createForm.titulo.trim(),
      tipoMantenimiento: createForm.tipoMantenimiento,
      prioridad: createForm.prioridad,
      descripcionTrabajo: createForm.descripcionTrabajo.trim(),
      fechaProgramada: createForm.fechaProgramada,
      costoEstimado: createForm.costoEstimado ? Number(createForm.costoEstimado) : 0,
      tecnicoResponsable: createForm.tecnicoResponsable?.trim() || null,
      idActivo: createForm.idActivo ? Number(createForm.idActivo) : null,
      idProveedorServicio: createForm.idProveedorServicio ? Number(createForm.idProveedorServicio) : null,
      evidenciaAntesUrl: createForm.evidenciaAntesUrl?.trim() || null,
    };

    if (createForm.requiereBloqueoZona && createForm.idZonaBloqueo) {
      if (!createForm.fechaInicioBloqueo || !createForm.fechaFinBloqueo) {
        toast.error('Debe especificar inicio y fin del bloqueo de zona');
        return;
      }
      payload.idZonaBloqueo = Number(createForm.idZonaBloqueo);
      payload.fechaInicioBloqueo = new Date(createForm.fechaInicioBloqueo).toISOString();
      payload.fechaFinBloqueo = new Date(createForm.fechaFinBloqueo).toISOString();
      payload.motivoBloqueo = createForm.motivoBloqueo?.trim() || `Bloqueo por mantenimiento: ${createForm.titulo}`;
    }

    try {
      setIsSubmitting(true);
      await api.post('/mantenimientos', payload);
      toast.success('Orden de mantenimiento creada exitosamente');
      setShowCreateModal(false);
      setCreateForm(initialCreateForm);
      await cargarDatos();
    } catch (err) {
      console.error('Error creando mantenimiento:', err);
      const msg = err.response?.data?.message || 'Error al crear la orden de mantenimiento';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEdit = async (e) => {
    e.preventDefault();
    if (!selectedMantenimiento) return;
    if (!editForm.titulo.trim()) {
      toast.error('El título es obligatorio');
      return;
    }

    const payload = {
      titulo: editForm.titulo.trim(),
      tipoMantenimiento: editForm.tipoMantenimiento,
      prioridad: editForm.prioridad,
      descripcionTrabajo: editForm.descripcionTrabajo.trim(),
      fechaProgramada: editForm.fechaProgramada,
      costoEstimado: editForm.costoEstimado !== '' ? Number(editForm.costoEstimado) : 0,
      tecnicoResponsable: editForm.tecnicoResponsable?.trim() || null,
      idActivo: editForm.idActivo ? Number(editForm.idActivo) : null,
      idProveedorServicio: editForm.idProveedorServicio ? Number(editForm.idProveedorServicio) : null,
      evidenciaAntesUrl: editForm.evidenciaAntesUrl?.trim() || null,
    };

    try {
      setIsSubmitting(true);
      await api.put(`/mantenimientos/${selectedMantenimiento.idMantenimiento}`, payload);
      toast.success('Mantenimiento actualizado exitosamente');
      setShowEditModal(false);
      setSelectedMantenimiento(null);
      await cargarDatos();
    } catch (err) {
      console.error('Error actualizando mantenimiento:', err);
      const msg = err.response?.data?.message || 'Error al actualizar el mantenimiento';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleStart = async () => {
    if (!startTarget) return;
    try {
      setIsSubmitting(true);
      await api.patch(`/mantenimientos/${startTarget.idMantenimiento}/estado`, {
        nuevoEstado: 'EN_PROCESO',
      });
      toast.success('Mantenimiento iniciado. El activo asociado ha pasado a MANTENIMIENTO.');
      setStartTarget(null);
      await cargarDatos();
    } catch (err) {
      console.error('Error iniciando mantenimiento:', err);
      const msg = err.response?.data?.message || 'Error al iniciar el mantenimiento';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleComplete = async (e) => {
    e.preventDefault();
    if (!selectedMantenimiento) return;

    const payload = {
      nuevoEstado: 'COMPLETADO',
      costoReal: completeForm.costoReal !== '' ? Number(completeForm.costoReal) : Number(selectedMantenimiento.costoEstimado || 0),
      fechaEjecucion: completeForm.fechaEjecucion ? new Date(completeForm.fechaEjecucion).toISOString() : new Date().toISOString(),
      notasCierre: completeForm.notasCierre?.trim() || 'Mantenimiento ejecutado a conformidad.',
      informeTecnicoUrl: completeForm.informeTecnicoUrl?.trim() || null,
      evidenciaDespuesUrl: completeForm.evidenciaDespuesUrl?.trim() || null,
    };

    try {
      setIsSubmitting(true);
      await api.patch(`/mantenimientos/${selectedMantenimiento.idMantenimiento}/estado`, payload);
      toast.success('Mantenimiento completado con éxito. El activo ha regresado a OPERATIVO.');
      setShowCompleteModal(false);
      setSelectedMantenimiento(null);
      await cargarDatos();
    } catch (err) {
      console.error('Error completando mantenimiento:', err);
      const msg = err.response?.data?.message || 'Error al completar el mantenimiento';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReschedule = async (e) => {
    e.preventDefault();
    if (!selectedMantenimiento) return;
    if (!rescheduleForm.nuevaFechaProgramada) {
      toast.error('Debe especificar la nueva fecha programada');
      return;
    }

    const payload = {
      nuevaFechaProgramada: rescheduleForm.nuevaFechaProgramada,
      motivo: rescheduleForm.motivo?.trim() || 'Reprogramación de orden de mantenimiento',
    };

    if (selectedMantenimiento.idBloqueoZona && rescheduleForm.nuevaFechaInicioBloqueo && rescheduleForm.nuevaFechaFinBloqueo) {
      payload.nuevaFechaInicioBloqueo = new Date(rescheduleForm.nuevaFechaInicioBloqueo).toISOString();
      payload.nuevaFechaFinBloqueo = new Date(rescheduleForm.nuevaFechaFinBloqueo).toISOString();
    }

    try {
      setIsSubmitting(true);
      await api.put(`/mantenimientos/${selectedMantenimiento.idMantenimiento}/reprogramar`, payload);
      toast.success('Mantenimiento reprogramado exitosamente');
      setShowRescheduleModal(false);
      setSelectedMantenimiento(null);
      await cargarDatos();
    } catch (err) {
      console.error('Error reprogramando mantenimiento:', err);
      const msg = err.response?.data?.message || 'Error al reprogramar el mantenimiento';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancel = async () => {
    if (!cancelTarget) return;
    try {
      setIsSubmitting(true);
      await api.put(`/mantenimientos/${cancelTarget.idMantenimiento}/cancelar`, {
        motivo: 'Cancelado por administración de copropiedad.',
      });
      toast.success('Mantenimiento cancelado. El activo y zonas bloqueadas han sido liberados.');
      setCancelTarget(null);
      await cargarDatos();
    } catch (err) {
      console.error('Error cancelando mantenimiento:', err);
      const msg = err.response?.data?.message || 'Error al cancelar el mantenimiento';
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const openEditModal = (mant) => {
    setSelectedMantenimiento(mant);
    setEditForm({
      titulo: mant.titulo || '',
      tipoMantenimiento: mant.tipoMantenimiento || 'PREVENTIVO',
      prioridad: mant.prioridad || 'MEDIA',
      descripcionTrabajo: mant.descripcionTrabajo || '',
      fechaProgramada: mant.fechaProgramada || '',
      costoEstimado: mant.costoEstimado != null ? String(mant.costoEstimado) : '',
      tecnicoResponsable: mant.tecnicoResponsable || '',
      idActivo: mant.idActivo ? String(mant.idActivo) : '',
      idProveedorServicio: mant.idProveedorServicio ? String(mant.idProveedorServicio) : '',
      evidenciaAntesUrl: mant.evidenciaAntesUrl || '',
    });
    setShowEditModal(true);
  };

  const openCompleteModal = (mant) => {
    setSelectedMantenimiento(mant);
    setCompleteForm({
      costoReal: mant.costoReal != null && mant.costoReal > 0 ? String(mant.costoReal) : mant.costoEstimado ? String(mant.costoEstimado) : '',
      fechaEjecucion: new Date().toISOString().slice(0, 16),
      notasCierre: mant.notasCierre || '',
      informeTecnicoUrl: mant.informeTecnicoUrl || '',
      evidenciaDespuesUrl: mant.evidenciaDespuesUrl || '',
    });
    setShowCompleteModal(true);
  };

  const openRescheduleModal = (mant) => {
    setSelectedMantenimiento(mant);
    setRescheduleForm({
      nuevaFechaProgramada: mant.fechaProgramada || '',
      nuevaFechaInicioBloqueo: mant.fechaInicioBloqueo ? new Date(mant.fechaInicioBloqueo).toISOString().slice(0, 16) : '',
      nuevaFechaFinBloqueo: mant.fechaFinBloqueo ? new Date(mant.fechaFinBloqueo).toISOString().slice(0, 16) : '',
      motivo: '',
    });
    setShowRescheduleModal(true);
  };

  const openDetailModal = (mant) => {
    setSelectedMantenimiento(mant);
    setShowDetailModal(true);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <PageHeader
        title="Órdenes de Mantenimiento y Control de Zonas"
        subtitle="Gestión del ciclo integral de mantenimiento preventivo, correctivo, predictivo y bloqueos de áreas comunes."
      >
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            onClick={cargarDatos}
            disabled={loading}
            className="flex items-center gap-2"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            Actualizar
          </Button>
          <Button
            onClick={() => {
              setCreateForm(initialCreateForm);
              setShowCreateModal(true);
            }}
            className="flex items-center gap-2"
          >
            <Plus className="h-4 w-4" />
            Nueva Orden
          </Button>
        </div>
      </PageHeader>

      {/* KPIs Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <MetricCard
          title="Total Órdenes"
          value={stats.total}
          icon={Layers}
          trend={`${stats.completados} completadas`}
          trendPositive={stats.completados > 0}
        />
        <MetricCard
          title="Programadas"
          value={stats.programados}
          icon={Calendar}
          className="border-sky-500/20"
        />
        <MetricCard
          title="En Proceso"
          value={stats.enProceso}
          icon={Wrench}
          className="border-amber-500/20"
        />
        <MetricCard
          title="Completadas"
          value={stats.completados}
          icon={CheckCircle2}
          className="border-emerald-500/20"
        />
        <MetricCard
          title="Urgentes Activas"
          value={stats.urgentes}
          icon={AlertTriangle}
          className={stats.urgentes > 0 ? 'border-red-500/30 text-red-600' : ''}
        />
      </div>

      {/* Filters Bar */}
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-4 shadow-sm md:flex-row md:items-center md:justify-between">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por título, activo, código, técnico o contratista..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full rounded-lg border border-input bg-background pl-9 pr-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <select
            value={filterEstado}
            onChange={(e) => setFilterEstado(e.target.value)}
            className="rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          >
            <option value="TODOS">Todos los Estados</option>
            {ESTADOS.map((e) => (
              <option key={e.id} value={e.id}>{e.label}</option>
            ))}
          </select>

          <select
            value={filterTipo}
            onChange={(e) => setFilterTipo(e.target.value)}
            className="rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          >
            <option value="TODOS">Todos los Tipos</option>
            {TIPOS_MANTENIMIENTO.map((t) => (
              <option key={t.id} value={t.id}>{t.label}</option>
            ))}
          </select>

          <select
            value={filterPrioridad}
            onChange={(e) => setFilterPrioridad(e.target.value)}
            className="rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          >
            <option value="TODAS">Todas las Prioridades</option>
            {PRIORIDADES.map((p) => (
              <option key={p.id} value={p.id}>{p.label}</option>
            ))}
          </select>

          {(searchTerm || filterEstado !== 'TODOS' || filterTipo !== 'TODOS' || filterPrioridad !== 'TODAS') && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setSearchTerm('');
                setFilterEstado('TODOS');
                setFilterTipo('TODOS');
                setFilterPrioridad('TODAS');
              }}
              className="text-xs text-muted-foreground"
            >
              Limpiar filtros
            </Button>
          )}
        </div>
      </div>

      {/* Main Table Content */}
      <div className="rounded-xl border border-border bg-card shadow-sm overflow-hidden">
        {loading ? (
          <div className="p-8">
            <LoadingState text="Cargando órdenes de mantenimiento..." />
          </div>
        ) : mantenimientosFiltrados.length === 0 ? (
          <EmptyState
            icon={Wrench}
            title="No se encontraron órdenes de mantenimiento"
            description={
              searchTerm || filterEstado !== 'TODOS' || filterTipo !== 'TODOS'
                ? 'Prueba modificando los criterios de búsqueda o filtros.'
                : 'Comienza programando tu primer mantenimiento preventivo o correctivo.'
            }
            action={
              searchTerm || filterEstado !== 'TODOS' ? null : (
                <Button onClick={() => setShowCreateModal(true)}>
                  <Plus className="mr-2 h-4 w-4" />
                  Programar Primer Mantenimiento
                </Button>
              )
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border bg-muted/50 text-xs font-semibold uppercase text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Orden & Tipo</th>
                  <th className="px-4 py-3">Activo / Zona Común</th>
                  <th className="px-4 py-3">Contratista / Técnico</th>
                  <th className="px-4 py-3">Programación & Prioridad</th>
                  <th className="px-4 py-3">Presupuesto</th>
                  <th className="px-4 py-3">Estado</th>
                  <th className="px-4 py-3 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {mantenimientosFiltrados.map((m) => {
                  const isTerminal = m.estado === 'COMPLETADO' || m.estado === 'CANCELADO';
                  const isProgramado = m.estado === 'PROGRAMADO' || m.estado === 'REPROGRAMADO';
                  const isEnProceso = m.estado === 'EN_PROCESO';

                  return (
                    <tr key={m.idMantenimiento} className="hover:bg-muted/30 transition-colors">
                      {/* Orden & Tipo */}
                      <td className="px-4 py-3">
                        <div className="font-semibold text-foreground flex items-center gap-2">
                          <span>#{m.idMantenimiento}</span>
                          <span className="truncate max-w-[200px]" title={m.titulo}>
                            {m.titulo}
                          </span>
                        </div>
                        <div className="mt-1 flex items-center gap-2">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${getTipoBadge(m.tipoMantenimiento)}`}>
                            {m.tipoMantenimiento}
                          </span>
                          {m.idBloqueoZona && (
                            <span className="inline-flex items-center gap-1 text-[11px] text-amber-600 dark:text-amber-400 font-medium">
                              <ShieldAlert className="h-3 w-3" />
                              Zona Bloqueada
                            </span>
                          )}
                        </div>
                      </td>

                      {/* Activo / Zona */}
                      <td className="px-4 py-3">
                        {m.idActivo ? (
                          <div>
                            <div className="font-medium text-foreground flex items-center gap-1.5">
                              <Building2 className="h-3.5 w-3.5 text-muted-foreground" />
                              <span className="truncate max-w-[180px]">{m.nombreActivo}</span>
                            </div>
                            <span className="text-xs text-muted-foreground font-mono">
                              {m.codigoActivo}
                            </span>
                          </div>
                        ) : m.nombreZonaBloqueada ? (
                          <div className="flex items-center gap-1 text-xs text-amber-700 dark:text-amber-300 font-medium">
                            <MapPin className="h-3.5 w-3.5" />
                            <span>{m.nombreZonaBloqueada}</span>
                          </div>
                        ) : (
                          <span className="text-xs text-muted-foreground italic">
                            Locativo / General
                          </span>
                        )}
                      </td>

                      {/* Contratista / Técnico */}
                      <td className="px-4 py-3">
                        {m.razonSocialProveedor ? (
                          <div>
                            <div className="font-medium text-foreground truncate max-w-[180px]">
                              {m.razonSocialProveedor}
                            </div>
                            {m.tecnicoResponsable && (
                              <div className="text-xs text-muted-foreground flex items-center gap-1">
                                <User className="h-3 w-3" />
                                <span>{m.tecnicoResponsable}</span>
                              </div>
                            )}
                          </div>
                        ) : m.tecnicoResponsable ? (
                          <div className="text-xs text-foreground flex items-center gap-1">
                            <User className="h-3.5 w-3.5 text-muted-foreground" />
                            <span>{m.tecnicoResponsable}</span>
                          </div>
                        ) : (
                          <span className="text-xs text-muted-foreground italic">No asignado</span>
                        )}
                      </td>

                      {/* Programación & Prioridad */}
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1 text-xs font-medium text-foreground">
                          <Calendar className="h-3.5 w-3.5 text-muted-foreground" />
                          <span>{formatDate(m.fechaProgramada)}</span>
                        </div>
                        <div className="mt-1">
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${getPrioridadBadge(m.prioridad)}`}>
                            {m.prioridad}
                          </span>
                        </div>
                      </td>

                      {/* Costos */}
                      <td className="px-4 py-3">
                        <div className="text-xs">
                          <span className="text-muted-foreground">Est: </span>
                          <span className="font-medium text-foreground">
                            {formatCurrency(m.costoEstimado || 0)}
                          </span>
                        </div>
                        {m.costoReal != null && m.costoReal > 0 && (
                          <div className="text-xs mt-0.5">
                            <span className="text-emerald-600 font-medium">Real: </span>
                            <span className="font-semibold text-emerald-700 dark:text-emerald-400">
                              {formatCurrency(m.costoReal)}
                            </span>
                          </div>
                        )}
                      </td>

                      {/* Estado */}
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${getEstadoBadge(m.estado)}`}>
                          {m.estado}
                        </span>
                      </td>

                      {/* Acciones */}
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            title="Ver detalle"
                            onClick={() => openDetailModal(m)}
                          >
                            <Eye className="h-4 w-4" />
                          </Button>

                          {isProgramado && (
                            <>
                              <Button
                                variant="ghost"
                                size="icon"
                                title="Iniciar mantenimiento"
                                className="text-amber-600 hover:text-amber-700 hover:bg-amber-50 dark:hover:bg-amber-950/30"
                                onClick={() => setStartTarget(m)}
                              >
                                <Play className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                title="Editar orden"
                                onClick={() => openEditModal(m)}
                              >
                                <Edit2 className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                title="Reprogramar"
                                onClick={() => openRescheduleModal(m)}
                              >
                                <Clock className="h-4 w-4" />
                              </Button>
                            </>
                          )}

                          {isEnProceso && (
                            <Button
                              variant="ghost"
                              size="icon"
                              title="Completar y certificar"
                              className="text-emerald-600 hover:text-emerald-700 hover:bg-emerald-50 dark:hover:bg-emerald-950/30"
                              onClick={() => openCompleteModal(m)}
                            >
                              <CheckCircle2 className="h-4 w-4" />
                            </Button>
                          )}

                          {!isTerminal && (
                            <Button
                              variant="ghost"
                              size="icon"
                              title="Cancelar orden"
                              className="text-red-500 hover:text-red-700 hover:bg-red-50 dark:hover:bg-red-950/30"
                              onClick={() => setCancelTarget(m)}
                            >
                              <Ban className="h-4 w-4" />
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal Crear Mantenimiento */}
      <Modal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Programar Nueva Orden de Mantenimiento"
        description="Diligencia los datos técnicos, contratista y bloqueo de áreas para la orden."
        maxWidth="max-w-2xl"
      >
        <form onSubmit={handleCreate} className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-foreground mb-1">
                Título del Mantenimiento *
              </label>
              <input
                type="text"
                required
                maxLength={150}
                placeholder="Ej. Mantenimiento Preventivo Bimestral Ascensor Torre 1"
                value={createForm.titulo}
                onChange={(e) => setCreateForm({ ...createForm, titulo: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Tipo de Mantenimiento *
              </label>
              <select
                value={createForm.tipoMantenimiento}
                onChange={(e) => setCreateForm({ ...createForm, tipoMantenimiento: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                {TIPOS_MANTENIMIENTO.map((t) => (
                  <option key={t.id} value={t.id}>{t.label}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Prioridad *
              </label>
              <select
                value={createForm.prioridad}
                onChange={(e) => setCreateForm({ ...createForm, prioridad: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                {PRIORIDADES.map((p) => (
                  <option key={p.id} value={p.id}>{p.label}</option>
                ))}
              </select>
            </div>

            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-foreground mb-1">
                Descripción del Trabajo a Realizar *
              </label>
              <textarea
                required
                rows={3}
                placeholder="Describa el alcance de la inspección, repuestos requeridos o protocolo de servicio..."
                value={createForm.descripcionTrabajo}
                onChange={(e) => setCreateForm({ ...createForm, descripcionTrabajo: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Activo Asociado (Inventario Físico)
              </label>
              <select
                value={createForm.idActivo}
                onChange={(e) => setCreateForm({ ...createForm, idActivo: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">Sin activo específico (Mantenimiento Locativo/General)</option>
                {activos
                  .filter((a) => a.estado !== 'DADO_DE_BAJA')
                  .map((a) => (
                    <option key={a.idActivo} value={a.idActivo}>
                      {a.codigoActivo} - {a.nombre} ({a.estado})
                    </option>
                  ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Proveedor / Contratista (Catálogo Maestro)
              </label>
              <select
                value={createForm.idProveedorServicio}
                onChange={(e) => setCreateForm({ ...createForm, idProveedorServicio: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">Gestión Interna / Sin Contratista</option>
                {proveedores
                  .filter((p) => p.estado === 'ACTIVO')
                  .map((p) => (
                    <option key={p.idProveedor} value={p.idProveedor}>
                      {p.razonSocial} (NIT: {p.nitIdentificacion})
                    </option>
                  ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Fecha Programada *
              </label>
              <input
                type="date"
                required
                value={createForm.fechaProgramada}
                onChange={(e) => setCreateForm({ ...createForm, fechaProgramada: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Presupuesto / Costo Estimado (COP)
              </label>
              <input
                type="number"
                min="0"
                step="any"
                placeholder="0"
                value={createForm.costoEstimado}
                onChange={(e) => setCreateForm({ ...createForm, costoEstimado: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Técnico Responsable Asignado
              </label>
              <input
                type="text"
                maxLength={150}
                placeholder="Nombre del técnico o cuadrilla"
                value={createForm.tecnicoResponsable}
                onChange={(e) => setCreateForm({ ...createForm, tecnicoResponsable: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                URL Evidencia Previa / Falla
              </label>
              <input
                type="url"
                maxLength={500}
                placeholder="https://..."
                value={createForm.evidenciaAntesUrl}
                onChange={(e) => setCreateForm({ ...createForm, evidenciaAntesUrl: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            {/* Sección de Bloqueo de Zona Común */}
            <div className="sm:col-span-2 rounded-lg border border-amber-500/30 bg-amber-50/50 dark:bg-amber-950/20 p-4 space-y-3">
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="chkBloqueo"
                  checked={createForm.requiereBloqueoZona}
                  onChange={(e) => setCreateForm({ ...createForm, requiereBloqueoZona: e.target.checked })}
                  className="h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
                />
                <label htmlFor="chkBloqueo" className="text-sm font-semibold text-foreground flex items-center gap-1.5 cursor-pointer">
                  <ShieldAlert className="h-4 w-4 text-amber-600" />
                  ¿Requiere Bloqueo Temporal de Zona Común?
                </label>
              </div>

              {createForm.requiereBloqueoZona && (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 pt-2">
                  <div className="sm:col-span-2">
                    <label className="block text-xs font-semibold text-foreground mb-1">
                      Zona Común a Inhabilitar *
                    </label>
                    <select
                      required={createForm.requiereBloqueoZona}
                      value={createForm.idZonaBloqueo}
                      onChange={(e) => setCreateForm({ ...createForm, idZonaBloqueo: e.target.value })}
                      className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                    >
                      <option value="">Seleccione una zona común...</option>
                      {zonasComunes.map((z) => (
                        <option key={z.idZona} value={z.idZona}>
                          {z.nombre} ({z.tipo})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1">
                      Fecha y Hora Inicio Bloqueo *
                    </label>
                    <input
                      type="datetime-local"
                      required={createForm.requiereBloqueoZona}
                      value={createForm.fechaInicioBloqueo}
                      onChange={(e) => setCreateForm({ ...createForm, fechaInicioBloqueo: e.target.value })}
                      className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-foreground mb-1">
                      Fecha y Hora Fin Bloqueo *
                    </label>
                    <input
                      type="datetime-local"
                      required={createForm.requiereBloqueoZona}
                      value={createForm.fechaFinBloqueo}
                      onChange={(e) => setCreateForm({ ...createForm, fechaFinBloqueo: e.target.value })}
                      className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>

                  <div className="sm:col-span-2">
                    <label className="block text-xs font-semibold text-foreground mb-1">
                      Motivo visible para residentes
                    </label>
                    <input
                      type="text"
                      maxLength={250}
                      placeholder="Ej. Inhabilitado por mantenimiento preventivo y desinfección anual"
                      value={createForm.motivoBloqueo}
                      onChange={(e) => setCreateForm({ ...createForm, motivoBloqueo: e.target.value })}
                      className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                    />
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowCreateModal(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Guardando...' : 'Programar Mantenimiento'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Editar Mantenimiento */}
      <Modal
        isOpen={showEditModal}
        onClose={() => setShowEditModal(false)}
        title={`Editar Orden #${selectedMantenimiento?.idMantenimiento}`}
        description="Actualización de datos técnicos y económicos de la orden."
        maxWidth="max-w-2xl"
      >
        <form onSubmit={handleEdit} className="space-y-4">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-foreground mb-1">
                Título del Mantenimiento *
              </label>
              <input
                type="text"
                required
                maxLength={150}
                value={editForm.titulo}
                onChange={(e) => setEditForm({ ...editForm, titulo: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Tipo de Mantenimiento *
              </label>
              <select
                value={editForm.tipoMantenimiento}
                onChange={(e) => setEditForm({ ...editForm, tipoMantenimiento: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                {TIPOS_MANTENIMIENTO.map((t) => (
                  <option key={t.id} value={t.id}>{t.label}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Prioridad *
              </label>
              <select
                value={editForm.prioridad}
                onChange={(e) => setEditForm({ ...editForm, prioridad: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                {PRIORIDADES.map((p) => (
                  <option key={p.id} value={p.id}>{p.label}</option>
                ))}
              </select>
            </div>

            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-foreground mb-1">
                Descripción del Trabajo *
              </label>
              <textarea
                required
                rows={3}
                value={editForm.descripcionTrabajo}
                onChange={(e) => setEditForm({ ...editForm, descripcionTrabajo: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Fecha Programada *
              </label>
              <input
                type="date"
                required
                value={editForm.fechaProgramada}
                onChange={(e) => setEditForm({ ...editForm, fechaProgramada: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Presupuesto Estimado (COP)
              </label>
              <input
                type="number"
                min="0"
                step="any"
                value={editForm.costoEstimado}
                onChange={(e) => setEditForm({ ...editForm, costoEstimado: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Técnico Responsable
              </label>
              <input
                type="text"
                maxLength={150}
                value={editForm.tecnicoResponsable}
                onChange={(e) => setEditForm({ ...editForm, tecnicoResponsable: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Proveedor de Servicios
              </label>
              <select
                value={editForm.idProveedorServicio}
                onChange={(e) => setEditForm({ ...editForm, idProveedorServicio: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">Sin proveedor asignado</option>
                {proveedores.map((p) => (
                  <option key={p.idProveedor} value={p.idProveedor}>
                    {p.razonSocial}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowEditModal(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Guardando...' : 'Actualizar Orden'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Completar / Certificar Mantenimiento */}
      <Modal
        isOpen={showCompleteModal}
        onClose={() => setShowCompleteModal(false)}
        title="Certificar y Completar Mantenimiento"
        description="Liquidación económica final, informe técnico y retorno del activo a estado operativo."
        maxWidth="max-w-xl"
      >
        <form onSubmit={handleComplete} className="space-y-4">
          <div className="rounded-lg bg-emerald-50/50 dark:bg-emerald-950/20 border border-emerald-500/20 p-3 text-xs text-emerald-800 dark:text-emerald-300">
            Al completar esta orden, el activo vinculado regresará a estado <strong>OPERATIVO</strong> y cualquier bloqueo de área común será liberado.
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Costo Real Ejecutado (COP) *
              </label>
              <input
                type="number"
                min="0"
                step="any"
                required
                value={completeForm.costoReal}
                onChange={(e) => setCompleteForm({ ...completeForm, costoReal: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-foreground mb-1">
                Fecha / Hora de Ejecución *
              </label>
              <input
                type="datetime-local"
                required
                value={completeForm.fechaEjecucion}
                onChange={(e) => setCompleteForm({ ...completeForm, fechaEjecucion: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-foreground mb-1">
                Notas de Cierre y Diagnóstico Final *
              </label>
              <textarea
                required
                rows={3}
                placeholder="Detalle de las intervenciones realizadas, repuestos cambiados o recomendaciones..."
                value={completeForm.notasCierre}
                onChange={(e) => setCompleteForm({ ...completeForm, notasCierre: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-foreground mb-1">
                URL del Informe Técnico / Acta de Entrega
              </label>
              <input
                type="url"
                placeholder="https://..."
                value={completeForm.informeTecnicoUrl}
                onChange={(e) => setCompleteForm({ ...completeForm, informeTecnicoUrl: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div className="sm:col-span-2">
              <label className="block text-xs font-semibold text-foreground mb-1">
                URL de Evidencia Posterior (Fotografía)
              </label>
              <input
                type="url"
                placeholder="https://..."
                value={completeForm.evidenciaDespuesUrl}
                onChange={(e) => setCompleteForm({ ...completeForm, evidenciaDespuesUrl: e.target.value })}
                className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowCompleteModal(false)}
              disabled={isSubmitting}
            >
              Volver
            </Button>
            <Button type="submit" disabled={isSubmitting} className="bg-emerald-600 hover:bg-emerald-700">
              {isSubmitting ? 'Completando...' : 'Certificar y Finalizar'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Reprogramar */}
      <Modal
        isOpen={showRescheduleModal}
        onClose={() => setShowRescheduleModal(false)}
        title="Reprogramar Orden de Mantenimiento"
        description="Establece una nueva fecha programada y actualiza las ventanas de bloqueo asociadas."
        maxWidth="max-w-md"
      >
        <form onSubmit={handleReschedule} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              Nueva Fecha Programada *
            </label>
            <input
              type="date"
              required
              value={rescheduleForm.nuevaFechaProgramada}
              onChange={(e) => setRescheduleForm({ ...rescheduleForm, nuevaFechaProgramada: e.target.value })}
              className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>

          {selectedMantenimiento?.idBloqueoZona && (
            <div className="space-y-3 rounded-lg border border-amber-500/20 bg-amber-50/40 p-3">
              <span className="text-xs font-semibold text-amber-800 dark:text-amber-300 flex items-center gap-1">
                <ShieldAlert className="h-3.5 w-3.5" />
                Actualización de Bloqueo de Zona Común
              </span>
              <div>
                <label className="block text-[11px] font-medium text-foreground mb-1">
                  Nueva Fecha/Hora Inicio Bloqueo
                </label>
                <input
                  type="datetime-local"
                  value={rescheduleForm.nuevaFechaInicioBloqueo}
                  onChange={(e) => setRescheduleForm({ ...rescheduleForm, nuevaFechaInicioBloqueo: e.target.value })}
                  className="w-full rounded-lg border border-input bg-background px-2.5 py-1.5 text-xs"
                />
              </div>
              <div>
                <label className="block text-[11px] font-medium text-foreground mb-1">
                  Nueva Fecha/Hora Fin Bloqueo
                </label>
                <input
                  type="datetime-local"
                  value={rescheduleForm.nuevaFechaFinBloqueo}
                  onChange={(e) => setRescheduleForm({ ...rescheduleForm, nuevaFechaFinBloqueo: e.target.value })}
                  className="w-full rounded-lg border border-input bg-background px-2.5 py-1.5 text-xs"
                />
              </div>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-foreground mb-1">
              Motivo de la Reprogramación
            </label>
            <textarea
              rows={2}
              placeholder="Indique la causa del aplazamiento..."
              value={rescheduleForm.motivo}
              onChange={(e) => setRescheduleForm({ ...rescheduleForm, motivo: e.target.value })}
              className="w-full rounded-lg border border-input bg-background px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowRescheduleModal(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Reprogramando...' : 'Confirmar Reprogramación'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal Detalle de Orden */}
      <Modal
        isOpen={showDetailModal}
        onClose={() => setShowDetailModal(false)}
        title={`Detalle de Orden de Mantenimiento #${selectedMantenimiento?.idMantenimiento}`}
        description="Ficha técnica integral, contratistas, bitácora y auditoría."
        maxWidth="max-w-2xl"
      >
        {selectedMantenimiento && (
          <div className="space-y-4">
            {/* Header info */}
            <div className="flex flex-wrap items-center justify-between gap-2 p-3 bg-muted/40 rounded-lg border border-border">
              <div>
                <h3 className="font-semibold text-foreground text-base">
                  {selectedMantenimiento.titulo}
                </h3>
                <span className="text-xs text-muted-foreground">
                  Registrada el {formatDate(selectedMantenimiento.fechaCreacion)}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border ${getEstadoBadge(selectedMantenimiento.estado)}`}>
                  {selectedMantenimiento.estado}
                </span>
                <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border ${getPrioridadBadge(selectedMantenimiento.prioridad)}`}>
                  {selectedMantenimiento.prioridad}
                </span>
              </div>
            </div>

            {/* General Info */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground block mb-1">Tipo de Mantenimiento</span>
                <span className="font-medium text-foreground">{selectedMantenimiento.tipoMantenimiento}</span>
              </div>
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground block mb-1">Fecha Programada</span>
                <span className="font-medium text-foreground">{formatDate(selectedMantenimiento.fechaProgramada)}</span>
              </div>
            </div>

            {/* Activo & Proveedor */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground block mb-1 flex items-center gap-1">
                  <Building2 className="h-3.5 w-3.5" /> Activo Físico Asociado
                </span>
                {selectedMantenimiento.idActivo ? (
                  <div>
                    <span className="font-medium text-foreground">{selectedMantenimiento.nombreActivo}</span>
                    <div className="text-xs text-muted-foreground font-mono mt-0.5">
                      Código: {selectedMantenimiento.codigoActivo}
                    </div>
                  </div>
                ) : (
                  <span className="text-muted-foreground italic text-xs">Mantenimiento General / Locativo</span>
                )}
              </div>

              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground block mb-1 flex items-center gap-1">
                  <User className="h-3.5 w-3.5" /> Contratista / Técnico
                </span>
                {selectedMantenimiento.razonSocialProveedor ? (
                  <div>
                    <span className="font-medium text-foreground">{selectedMantenimiento.razonSocialProveedor}</span>
                    <div className="text-xs text-muted-foreground mt-0.5">
                      NIT: {selectedMantenimiento.nitProveedor}
                    </div>
                  </div>
                ) : selectedMantenimiento.tecnicoResponsable ? (
                  <span className="font-medium text-foreground">{selectedMantenimiento.tecnicoResponsable}</span>
                ) : (
                  <span className="text-muted-foreground italic text-xs">No asignado</span>
                )}
              </div>
            </div>

            {/* Bloqueo de Zona Común si aplica */}
            {selectedMantenimiento.idBloqueoZona && (
              <div className="p-3 rounded-lg border border-amber-500/30 bg-amber-50/50 dark:bg-amber-950/20 text-sm">
                <span className="text-xs font-semibold text-amber-800 dark:text-amber-300 flex items-center gap-1 mb-1">
                  <ShieldAlert className="h-3.5 w-3.5" /> Bloqueo de Zona Común Activo
                </span>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs mt-2">
                  <div>
                    <span className="text-muted-foreground">Área: </span>
                    <strong className="text-foreground">{selectedMantenimiento.nombreZonaBloqueada}</strong>
                  </div>
                  <div>
                    <span className="text-muted-foreground">Motivo: </span>
                    <span className="text-foreground">{selectedMantenimiento.motivoBloqueo}</span>
                  </div>
                  {selectedMantenimiento.fechaInicioBloqueo && (
                    <div>
                      <span className="text-muted-foreground">Inicio: </span>
                      <span className="text-foreground">{new Date(selectedMantenimiento.fechaInicioBloqueo).toLocaleString()}</span>
                    </div>
                  )}
                  {selectedMantenimiento.fechaFinBloqueo && (
                    <div>
                      <span className="text-muted-foreground">Fin: </span>
                      <span className="text-foreground">{new Date(selectedMantenimiento.fechaFinBloqueo).toLocaleString()}</span>
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Descripción */}
            <div className="p-3 rounded-lg border border-border bg-card text-sm">
              <span className="text-xs text-muted-foreground block mb-1">Descripción del Trabajo</span>
              <p className="text-foreground whitespace-pre-line text-xs">
                {selectedMantenimiento.descripcionTrabajo}
              </p>
            </div>

            {/* Económico */}
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground block mb-1">Costo Estimado</span>
                <span className="font-semibold text-foreground">
                  {formatCurrency(selectedMantenimiento.costoEstimado || 0)}
                </span>
              </div>
              <div className="p-3 rounded-lg border border-border bg-card">
                <span className="text-xs text-muted-foreground block mb-1">Costo Real Liquidado</span>
                <span className="font-semibold text-emerald-600">
                  {formatCurrency(selectedMantenimiento.costoReal || 0)}
                </span>
              </div>
            </div>

            {/* Cierre si completado */}
            {selectedMantenimiento.notasCierre && (
              <div className="p-3 rounded-lg border border-border bg-card text-sm">
                <span className="text-xs text-muted-foreground block mb-1">Notas de Cierre</span>
                <p className="text-foreground text-xs">{selectedMantenimiento.notasCierre}</p>
              </div>
            )}

            {/* Enlaces y Evidencias */}
            <div className="flex flex-wrap gap-2 pt-2">
              {selectedMantenimiento.evidenciaAntesUrl && (
                <a
                  href={selectedMantenimiento.evidenciaAntesUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-xs text-primary hover:underline"
                >
                  <ExternalLink className="h-3.5 w-3.5" /> Evidencia Previa
                </a>
              )}
              {selectedMantenimiento.evidenciaDespuesUrl && (
                <a
                  href={selectedMantenimiento.evidenciaDespuesUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-xs text-emerald-600 hover:underline"
                >
                  <ExternalLink className="h-3.5 w-3.5" /> Evidencia Posterior
                </a>
              )}
              {selectedMantenimiento.informeTecnicoUrl && (
                <a
                  href={selectedMantenimiento.informeTecnicoUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-xs text-blue-600 hover:underline"
                >
                  <FileText className="h-3.5 w-3.5" /> Informe Técnico
                </a>
              )}
            </div>

            <div className="flex justify-end pt-3 border-t border-border">
              <Button variant="outline" onClick={() => setShowDetailModal(false)}>
                Cerrar
              </Button>
            </div>
          </div>
        )}
      </Modal>

      {/* Confirm Iniciar Mantenimiento */}
      <ConfirmDialog
        isOpen={Boolean(startTarget)}
        title="Iniciar Orden de Mantenimiento"
        description={`¿Deseas iniciar la orden #${startTarget?.idMantenimiento} ('${startTarget?.titulo}')? Si tiene un activo vinculado, su estado pasará automáticamente a MANTENIMIENTO.`}
        confirmText={isSubmitting ? 'Iniciando...' : 'Iniciar Mantenimiento'}
        variant="default"
        onConfirm={handleStart}
        onCancel={() => setStartTarget(null)}
      />

      {/* Confirm Cancelar Mantenimiento */}
      <ConfirmDialog
        isOpen={Boolean(cancelTarget)}
        title="Cancelar Orden de Mantenimiento"
        description={`¿Estás seguro de cancelar la orden #${cancelTarget?.idMantenimiento}? Esta acción es irreversible. Se liberarán el activo y las zonas comunes bloqueadas.`}
        confirmText={isSubmitting ? 'Cancelando...' : 'Sí, Cancelar Orden'}
        variant="destructive"
        onConfirm={handleCancel}
        onCancel={() => setCancelTarget(null)}
      />
    </div>
  );
}
