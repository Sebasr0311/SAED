import { useState, useMemo, useRef } from 'react';
import { toast } from 'sonner';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDateTime } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  Calendar,
  Search,
  Plus,
  Eye,
  ShieldCheck,
  Siren,
  ShieldAlert,
  ExternalLink,
} from 'lucide-react';

const SEVERIDAD_CONFIG = {
  LEVE: {
    label: 'Leve',
    badgeVariant: 'secondary',
    borderClass: 'border-slate-300 dark:border-slate-700',
    colorClass: 'text-slate-600 dark:text-slate-400',
    bgClass: 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300',
  },
  MODERADA: {
    label: 'Moderada',
    badgeVariant: 'info',
    borderClass: 'border-blue-300 dark:border-blue-700',
    colorClass: 'text-blue-600 dark:text-blue-400',
    bgClass: 'bg-blue-100 dark:bg-blue-950/60 text-blue-700 dark:text-blue-300',
  },
  ALTA: {
    label: 'Alta',
    badgeVariant: 'warning',
    borderClass: 'border-amber-300 dark:border-amber-700',
    colorClass: 'text-amber-600 dark:text-amber-400',
    bgClass: 'bg-amber-100 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300',
  },
  GRAVE: {
    label: 'Grave',
    badgeVariant: 'warning',
    borderClass: 'border-amber-400 dark:border-amber-600',
    colorClass: 'text-amber-600 dark:text-amber-400',
    bgClass: 'bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-200',
  },
  CRITICA: {
    label: 'Crítica',
    badgeVariant: 'destructive',
    borderClass: 'border-rose-400 dark:border-rose-600',
    colorClass: 'text-rose-600 dark:text-rose-400',
    bgClass: 'bg-rose-100 dark:bg-rose-950/60 text-rose-700 dark:text-rose-300',
  },
};

const TIPOS_MAP = {
  DANO_BIEN_COMUN: 'Daño Locativo / Estructural',
  FALLA_CRITICA_INFRAESTRUCTURA: 'Falla Crítica de Infraestructura',
  CONVIVENCIA_RUIDO: 'Convivencia: Ruido Excesivo',
  CONVIVENCIA_DISPUTA: 'Convivencia: Disputa Vecinal',
  SEGURIDAD_HURTO: 'Seguridad: Hurto o Tentativa',
  ACCESO_NO_AUTORIZADO: 'Seguridad: Ingreso no Autorizado',
  ACCIDENTE_PERSONA: 'Accidente / Emergencia Médica',
  OTRO: 'Otra Novedad o Contingencia',
};

function getLocalISOString() {
  const now = new Date();
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
  return now.toISOString().slice(0, 16);
}

const INITIAL_FORM = {
  titulo: '',
  tipoIncidente: 'DANO_BIEN_COMUN',
  nivelSeveridad: 'MODERADA',
  descripcionHechos: '',
  fechaHoraIncidente: '',
  requirioAutoridades: 'N',
  entidadAutoridad: '',
  numeroDenunciaPolicia: '',
  accionesInmediatas: '',
  evidenciasUrls: '',
};

export default function ResIncidentesPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/incidentes/mis-incidentes'));
  const [modalOpen, setModalOpen] = useState(false);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [selectedIncidente, setSelectedIncidente] = useState(null);

  const [statusFilter, setStatusFilter] = useState('TODOS');
  const [categoryFilter, setCategoryFilter] = useState('TODAS');
  const [searchTerm, setSearchTerm] = useState('');

  const [form, setForm] = useState(INITIAL_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const submittingRef = useRef(false);

  const items = useMemo(() => {
    return data?.items || (Array.isArray(data) ? data : []);
  }, [data]);

  // Executive KPIs
  const stats = useMemo(() => {
    const total = items.length;
    const abiertos = items.filter((i) => i.estado === 'REPORTADO').length;
    const cerrados = items.filter((i) => i.estado === 'CERRADO').length;
    const criticos = items.filter(
      (i) => i.nivelSeveridad === 'CRITICA' || i.nivelSeveridad === 'GRAVE' || i.nivelSeveridad === 'ALTA'
    ).length;
    return { total, abiertos, cerrados, criticos };
  }, [items]);

  // Filtered List
  const filteredItems = useMemo(() => {
    return items.filter((i) => {
      if (statusFilter !== 'TODOS' && i.estado !== statusFilter) return false;
      if (categoryFilter !== 'TODAS' && i.tipoIncidente !== categoryFilter) return false;
      if (!searchTerm.trim()) return true;

      const q = searchTerm.toLowerCase();
      const tit = (i.titulo || '').toLowerCase();
      const desc = (i.descripcionHechos || '').toLowerCase();
      const tipo = (TIPOS_MAP[i.tipoIncidente] || i.tipoIncidente || '').toLowerCase();
      const aut = (i.entidadAutoridad || '').toLowerCase();
      const numDen = (i.numeroDenunciaPolicia || '').toLowerCase();

      return tit.includes(q) || desc.includes(q) || tipo.includes(q) || aut.includes(q) || numDen.includes(q);
    });
  }, [items, statusFilter, categoryFilter, searchTerm]);

  function handleOpenCreate() {
    setForm({
      ...INITIAL_FORM,
      fechaHoraIncidente: getLocalISOString(),
    });
    setFormErrors({});
    setModalOpen(true);
  }

  function handleOpenDetail(incidente) {
    setSelectedIncidente(incidente);
    setDetailModalOpen(true);
  }

  function validateForm() {
    const errs = {};
    if (!form.titulo || form.titulo.trim().length < 5) {
      errs.titulo = 'Ingresa un título descriptivo de al menos 5 caracteres.';
    }
    if (!form.tipoIncidente) {
      errs.tipoIncidente = 'Selecciona la categoría del incidente.';
    }
    if (!form.descripcionHechos || form.descripcionHechos.trim().length < 15) {
      errs.descripcionHechos = 'Detalla lo sucedido con al menos 15 caracteres.';
    }
    if (form.requirioAutoridades === 'S' && (!form.entidadAutoridad || form.entidadAutoridad.trim().length < 3)) {
      errs.entidadAutoridad = 'Indica la autoridad o cuadrante que asistió (ej. Policía Nacional, Bomberos).';
    }
    setFormErrors(errs);
    return Object.keys(errs).length === 0;
  }

  async function handleCreate(e) {
    e?.preventDefault();
    if (submittingRef.current) return;
    if (!validateForm()) return;

    submittingRef.current = true;
    setIsSubmitting(true);

    try {
      const payload = {
        titulo: form.titulo.trim(),
        tipoIncidente: form.tipoIncidente,
        nivelSeveridad: form.nivelSeveridad || 'MODERADA',
        descripcionHechos: form.descripcionHechos.trim(),
        fechaHoraIncidente: form.fechaHoraIncidente ? new Date(form.fechaHoraIncidente).toISOString() : new Date().toISOString(),
        requirioAutoridades: form.requirioAutoridades === 'S' ? 'S' : 'N',
        entidadAutoridad: form.requirioAutoridades === 'S' ? form.entidadAutoridad.trim() : null,
        numeroDenunciaPolicia: form.requirioAutoridades === 'S' && form.numeroDenunciaPolicia ? form.numeroDenunciaPolicia.trim() : null,
        accionesInmediatas: form.accionesInmediatas ? form.accionesInmediatas.trim() : null,
        evidenciasUrls: form.evidenciasUrls ? form.evidenciasUrls.trim() : null,
      };

      await api.post('/incidentes', payload);
      toast.success('Incidente reportado exitosamente. La administración ha sido notificada.');
      setModalOpen(false);
      setForm(INITIAL_FORM);
      refetch();
    } catch (err) {
      toast.error('Error al registrar el reporte: ' + (err.message || 'Error de conexión'));
    } finally {
      submittingRef.current = false;
      setIsSubmitting(false);
    }
  }

  return (
    <div className="space-y-6 pb-12 animate-saed-fade">
      {/* Encabezado */}
      <PageHeader
        title="Mis Incidentes & Novedades"
        subtitle="Registro y seguimiento en tiempo real de novedades locativas, emergencias o contingencias en tu unidad"
        action={
          <Button
            onClick={handleOpenCreate}
            className="flex items-center gap-2 shadow-sm font-semibold"
          >
            <Plus className="h-4 w-4" />
            <span>Reportar Nuevo Incidente</span>
          </Button>
        }
      />

      {/* Tarjetas de Métricas / KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          icon="report_problem"
          value={stats.total}
          label="Total Incidentes"
          color="primary"
        />
        <StatCard
          icon="pending_actions"
          value={stats.abiertos}
          label="En Atención"
          color="amber"
        />
        <StatCard
          icon="task_alt"
          value={stats.cerrados}
          label="Casos Resueltos"
          color="green"
        />
        <StatCard
          icon="crisis_alert"
          value={stats.criticos}
          label="Alta Prioridad"
          color="red"
        />
      </div>

      {/* Barra de Filtros y Búsqueda */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3 bg-surface p-3 rounded-xl border border-border shadow-sm">
        {/* Pestañas de Estado */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0 scrollbar-none">
          {[
            { key: 'TODOS', label: 'Todos' },
            { key: 'REPORTADO', label: 'En Atención' },
            { key: 'CERRADO', label: 'Resueltos' },
          ].map((tab) => {
            const isActive = statusFilter === tab.key;
            return (
              <button
                key={tab.key}
                type="button"
                onClick={() => setStatusFilter(tab.key)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors shrink-0 ${
                  isActive
                    ? 'bg-primary text-primary-foreground font-semibold shadow-xs'
                    : 'text-muted-foreground hover:bg-muted/70 hover:text-foreground'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>

        {/* Filtros Secundarios y Búsqueda */}
        <div className="flex items-center gap-2 flex-1 md:justify-end">
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="text-xs px-2.5 py-1.5 rounded-lg border border-input bg-background text-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            aria-label="Filtrar por categoría"
          >
            <option value="TODAS">Todas las categorías</option>
            {Object.entries(TIPOS_MAP).map(([key, val]) => (
              <option key={key} value={key}>
                {val}
              </option>
            ))}
          </select>

          <div className="relative min-w-[180px] sm:min-w-[220px]">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Buscar título o detalle..."
              className="w-full pl-8 pr-3 py-1.5 text-xs rounded-lg border border-input bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring"
            />
          </div>
        </div>
      </div>

      {/* Contenido Principal */}
      {loading ? (
        <LoadingState message="Cargando bitácora de incidentes..." />
      ) : error ? (
        <div className="rounded-xl border border-rose-200 dark:border-rose-900/60 bg-rose-50/50 dark:bg-rose-950/20 p-6 text-center">
          <AlertTriangle className="mx-auto h-8 w-8 text-rose-500 mb-2" />
          <h4 className="font-semibold text-rose-700 dark:text-rose-400">Error al cargar incidentes</h4>
          <p className="text-xs text-muted-foreground mt-1">{error.message || 'Error de comunicación'}</p>
          <Button variant="outline" size="sm" onClick={() => refetch()} className="mt-4">
            Reintentar
          </Button>
        </div>
      ) : filteredItems.length === 0 ? (
        <EmptyState
          icon={<ShieldCheck className="h-10 w-10 text-muted-foreground" />}
          title={
            searchTerm || statusFilter !== 'TODOS' || categoryFilter !== 'TODAS'
              ? 'No se encontraron incidentes con ese criterio'
              : 'No hay incidentes reportados en tu unidad'
          }
          subtitle={
            searchTerm || statusFilter !== 'TODOS' || categoryFilter !== 'TODAS'
              ? 'Intenta cambiar los filtros de estado, categoría o búsqueda.'
              : 'Si ocurre alguna novedad o emergencia locativa, puedes crear un reporte formal para que la administración lo atienda.'
          }
        >
          {!searchTerm && statusFilter === 'TODOS' && categoryFilter === 'TODAS' && (
            <Button onClick={handleOpenCreate} className="mt-2 flex items-center gap-2">
              <Plus className="h-4 w-4" />
              <span>Registrar Primer Incidente</span>
            </Button>
          )}
        </EmptyState>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredItems.map((incidente) => {
            const isCerrado = incidente.estado === 'CERRADO';
            const sevConfig = SEVERIDAD_CONFIG[incidente.nivelSeveridad] || SEVERIDAD_CONFIG.MODERADA;

            return (
              <div
                key={incidente.idIncidente}
                className="group relative flex flex-col justify-between rounded-xl border border-border bg-card p-5 shadow-sm transition-all duration-200 hover:shadow-md hover:border-border/80"
              >
                <div>
                  {/* Encabezado de la Tarjeta */}
                  <div className="flex items-start justify-between gap-2 mb-2.5">
                    <div className="flex items-center gap-2">
                      <span className={`px-2 py-0.5 rounded text-[11px] font-semibold border ${sevConfig.bgClass} ${sevConfig.borderClass}`}>
                        {sevConfig.label}
                      </span>
                      <span className="text-xs text-muted-foreground font-medium">
                        #{incidente.idIncidente}
                      </span>
                    </div>

                    <Badge variant={isCerrado ? 'success' : 'warning'} className="flex items-center gap-1 text-[11px] py-0.5">
                      {isCerrado ? <CheckCircle2 className="h-3 w-3" /> : <Clock className="h-3 w-3" />}
                      <span>{isCerrado ? 'Resuelto' : 'En Atención'}</span>
                    </Badge>
                  </div>

                  {/* Título del Incidente */}
                  <h3 className="font-semibold text-foreground text-sm leading-snug line-clamp-2 mb-1.5">
                    {incidente.titulo}
                  </h3>

                  {/* Categoría y Fecha */}
                  <div className="flex flex-wrap items-center gap-y-1 gap-x-2 text-[11px] text-muted-foreground mb-3">
                    <span className="font-medium text-foreground bg-muted/60 px-1.5 py-0.5 rounded">
                      {TIPOS_MAP[incidente.tipoIncidente] || incidente.tipoIncidente}
                    </span>
                    <span>•</span>
                    <span className="flex items-center gap-1">
                      <Calendar className="h-3 w-3" />
                      <span>{formatDateTime(incidente.fechaHoraIncidente)}</span>
                    </span>
                  </div>

                  {/* Descripción de los Hechos */}
                  <p className="text-xs text-muted-foreground leading-relaxed line-clamp-3 mb-3.5">
                    {incidente.descripcionHechos}
                  </p>

                  {/* Alerta de Autoridades si aplica */}
                  {incidente.requirioAutoridades === 'S' && (
                    <div className="rounded-lg bg-rose-500/10 border border-rose-200 dark:border-rose-900/60 p-2 mb-3 text-[11px] text-rose-700 dark:text-rose-300 flex items-center gap-2">
                      <Siren className="h-3.5 w-3.5 shrink-0 text-rose-600 dark:text-rose-400" />
                      <div className="truncate">
                        <span className="font-semibold">Intervención de Autoridad:</span> {incidente.entidadAutoridad || 'Asistida'}
                        {incidente.numeroDenunciaPolicia && (
                          <span className="block text-[10px] text-rose-600 dark:text-rose-400/90 truncate">
                            Radicado: {incidente.numeroDenunciaPolicia}
                          </span>
                        )}
                      </div>
                    </div>
                  )}

                  {/* Conclusión de Cierre si está cerrado */}
                  {isCerrado && incidente.conclusionesCierre && (
                    <div className="rounded-lg bg-emerald-500/10 border border-emerald-200 dark:border-emerald-900/60 p-2.5 mb-3 text-xs text-emerald-800 dark:text-emerald-300">
                      <div className="flex items-center gap-1.5 font-semibold text-[11px] mb-1">
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
                        <span>Resolución de Administración:</span>
                      </div>
                      <p className="text-[11px] leading-relaxed line-clamp-2 text-emerald-700 dark:text-emerald-400/90">
                        {incidente.conclusionesCierre}
                      </p>
                    </div>
                  )}
                </div>

                {/* Footer de Tarjeta con Botón de Detalle */}
                <div className="pt-3 border-t border-border flex items-center justify-between">
                  <div className="text-[11px] text-muted-foreground">
                    <span>Unidad {incidente.idUnidad || 'Residencial'}</span>
                  </div>

                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleOpenDetail(incidente)}
                    className="h-8 text-xs flex items-center gap-1"
                  >
                    <Eye className="h-3.5 w-3.5" />
                    <span>Ver Detalle</span>
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal: Reportar Nuevo Incidente */}
      <Modal
        open={modalOpen}
        onClose={() => !isSubmitting && setModalOpen(false)}
        title="Reportar Incidente o Novedad"
        size="lg"
        footer={
          <div className="flex items-center justify-end gap-2 w-full">
            <Button
              type="button"
              variant="outline"
              onClick={() => setModalOpen(false)}
              disabled={isSubmitting}
            >
              Cancelar
            </Button>
            <Button
              type="button"
              onClick={handleCreate}
              disabled={isSubmitting}
              className="flex items-center gap-2"
            >
              {isSubmitting ? (
                <>
                  <span className="inline-block w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                  <span>Enviando...</span>
                </>
              ) : (
                <>
                  <ShieldAlert className="h-4 w-4" />
                  <span>Enviar Reporte</span>
                </>
              )}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreate} className="space-y-4 py-1">
          <Input
            label="Título Breve del Incidente"
            required
            id="incidente-titulo"
            placeholder="Ej: Fuga de agua en tubería principal, Ruido excesivo después de medianoche..."
            value={form.titulo}
            error={formErrors.titulo}
            onChange={(e) => setForm({ ...form, titulo: e.target.value })}
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Select
              label="Tipo de Novedad / Problema"
              required
              id="incidente-tipo"
              value={form.tipoIncidente}
              error={formErrors.tipoIncidente}
              onChange={(e) => setForm({ ...form, tipoIncidente: e.target.value })}
            >
              {Object.entries(TIPOS_MAP).map(([key, val]) => (
                <option key={key} value={key}>
                  {val}
                </option>
              ))}
            </Select>

            <Input
              type="datetime-local"
              label="Fecha y Hora de Ocurrencia"
              required
              id="incidente-fecha-hora"
              value={form.fechaHoraIncidente}
              onChange={(e) => setForm({ ...form, fechaHoraIncidente: e.target.value })}
            />
          </div>

          {/* Selector Visual de Nivel de Severidad */}
          <div>
            <label className="text-sm font-medium text-foreground block mb-1.5">
              Nivel de Severidad / Urgencia
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {[
                { key: 'LEVE', label: 'Leve', desc: 'Molestia o arreglo menor', color: 'border-slate-300 dark:border-slate-700' },
                { key: 'MODERADA', label: 'Moderada', desc: 'Afecta rutina normal', color: 'border-blue-400' },
                { key: 'GRAVE', label: 'Grave', desc: 'Daño o riesgo material', color: 'border-amber-500' },
                { key: 'CRITICA', label: 'Crítica', desc: 'Amenaza a seguridad o vida', color: 'border-rose-500' },
              ].map((sev) => {
                const isSelected = form.nivelSeveridad === sev.key;
                return (
                  <button
                    key={sev.key}
                    type="button"
                    onClick={() => setForm({ ...form, nivelSeveridad: sev.key })}
                    className={`p-2.5 rounded-lg border text-left transition-all ${
                      isSelected
                        ? `ring-2 ring-primary border-primary bg-primary/5 dark:bg-primary/10`
                        : 'border-border bg-card hover:bg-muted/40'
                    }`}
                  >
                    <div className="font-semibold text-xs text-foreground flex items-center justify-between">
                      <span>{sev.label}</span>
                      {isSelected && <span className="h-1.5 w-1.5 rounded-full bg-primary" />}
                    </div>
                    <div className="text-[10px] text-muted-foreground mt-0.5 leading-tight">{sev.desc}</div>
                  </button>
                );
              })}
            </div>
          </div>

          <Textarea
            label="Descripción Detallada de los Hechos"
            required
            id="incidente-descripcion"
            rows={4}
            placeholder="Explica detalladamente qué ocurrió, personas involucradas y daños observados..."
            value={form.descripcionHechos}
            error={formErrors.descripcionHechos}
            onChange={(e) => setForm({ ...form, descripcionHechos: e.target.value })}
          />

          {/* Switch de Requerimiento de Autoridades */}
          <div className="rounded-lg border border-border p-3.5 space-y-3 bg-muted/20">
            <label className="flex items-center gap-2.5 cursor-pointer select-none">
              <input
                type="checkbox"
                id="incidente-autoridades"
                checked={form.requirioAutoridades === 'S'}
                onChange={(e) => setForm({ ...form, requirioAutoridades: e.target.checked ? 'S' : 'N' })}
                className="h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary"
              />
              <span className="text-xs font-semibold text-foreground">
                ¿Se requirió presencia o llamada a Autoridades (Policía, Bomberos, Cuadrante)?
              </span>
            </label>

            {form.requirioAutoridades === 'S' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t border-border/70 animate-saed-fade">
                <Input
                  label="Entidad u Organismo Asistente"
                  required
                  id="incidente-entidad"
                  placeholder="Ej: Policía Nacional Cuadrante 14, Bomberos Estación Central"
                  value={form.entidadAutoridad}
                  error={formErrors.entidadAutoridad}
                  onChange={(e) => setForm({ ...form, entidadAutoridad: e.target.value })}
                />

                <Input
                  label="Número de Denuncia / Radicado POLNAL (Opcional)"
                  id="incidente-radicado"
                  placeholder="Ej: POL-2026-98124"
                  value={form.numeroDenunciaPolicia}
                  onChange={(e) => setForm({ ...form, numeroDenunciaPolicia: e.target.value })}
                />
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              label="Medidas Inmediatas Tomadas (Opcional)"
              id="incidente-acciones"
              placeholder="Ej: Se cerró la llave de paso general del apartamento..."
              value={form.accionesInmediatas}
              onChange={(e) => setForm({ ...form, accionesInmediatas: e.target.value })}
            />

            <Input
              type="url"
              label="Enlace a Fotos / Evidencias (Opcional)"
              id="incidente-evidencias"
              placeholder="https://drive.google.com/... o enlace de imagen"
              value={form.evidenciasUrls}
              onChange={(e) => setForm({ ...form, evidenciasUrls: e.target.value })}
            />
          </div>
        </form>
      </Modal>

      {/* Modal: Ver Detalle Completo de Incidente */}
      {selectedIncidente && (
        <Modal
          open={detailModalOpen}
          onClose={() => setDetailModalOpen(false)}
          title={`Caso de Incidente #${selectedIncidente.idIncidente}`}
          size="lg"
          footer={
            <Button variant="outline" onClick={() => setDetailModalOpen(false)}>
              Cerrar
            </Button>
          }
        >
          <div className="space-y-4 py-2 text-sm">
            {/* Cabecera con Estado y Severidad */}
            <div className="flex flex-wrap items-center justify-between gap-2 p-3 rounded-lg bg-muted/30 border border-border">
              <div>
                <span className="text-xs text-muted-foreground font-medium block">Categoría</span>
                <span className="font-semibold text-foreground">
                  {TIPOS_MAP[selectedIncidente.tipoIncidente] || selectedIncidente.tipoIncidente}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <span className={`px-2 py-0.5 rounded text-xs font-semibold border ${SEVERIDAD_CONFIG[selectedIncidente.nivelSeveridad]?.bgClass || ''} ${SEVERIDAD_CONFIG[selectedIncidente.nivelSeveridad]?.borderClass || ''}`}>
                  Severidad {SEVERIDAD_CONFIG[selectedIncidente.nivelSeveridad]?.label || selectedIncidente.nivelSeveridad}
                </span>
                <Badge variant={selectedIncidente.estado === 'CERRADO' ? 'success' : 'warning'}>
                  {selectedIncidente.estado === 'CERRADO' ? 'Resuelto' : 'En Atención'}
                </Badge>
              </div>
            </div>

            {/* Título y Hechos */}
            <div>
              <h4 className="font-semibold text-base text-foreground mb-1.5">{selectedIncidente.titulo}</h4>
              <p className="text-xs text-muted-foreground font-medium mb-1">Descripción de los hechos:</p>
              <div className="p-3.5 rounded-lg bg-background border border-border text-foreground leading-relaxed text-xs">
                {selectedIncidente.descripcionHechos}
              </div>
            </div>

            {/* Cronograma de Fechas */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Fecha y Hora del Incidente</span>
                <span className="font-medium text-foreground">{formatDateTime(selectedIncidente.fechaHoraIncidente)}</span>
              </div>
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Fecha de Radicación en Plataforma</span>
                <span className="font-medium text-foreground">{formatDateTime(selectedIncidente.fechaRegistro || selectedIncidente.fechaHoraIncidente)}</span>
              </div>
            </div>

            {/* Acciones inmediatas */}
            {selectedIncidente.accionesInmediatas && (
              <div className="p-3 rounded-lg border border-border bg-muted/20 text-xs">
                <span className="font-semibold text-foreground block mb-0.5">Acciones inmediatas tomadas:</span>
                <p className="text-muted-foreground leading-relaxed">{selectedIncidente.accionesInmediatas}</p>
              </div>
            )}

            {/* Información de Autoridades */}
            {selectedIncidente.requirioAutoridades === 'S' && (
              <div className="p-3 rounded-lg border border-rose-200 dark:border-rose-900/60 bg-rose-50/50 dark:bg-rose-950/20 text-xs text-rose-800 dark:text-rose-300 space-y-1">
                <div className="flex items-center gap-1.5 font-semibold">
                  <Siren className="h-4 w-4 text-rose-600 dark:text-rose-400" />
                  <span>Intervención de Autoridades Asistida</span>
                </div>
                <p><strong>Entidad:</strong> {selectedIncidente.entidadAutoridad || 'No especificada'}</p>
                {selectedIncidente.numeroDenunciaPolicia && (
                  <p><strong>Número de Denuncia:</strong> {selectedIncidente.numeroDenunciaPolicia}</p>
                )}
              </div>
            )}

            {/* Evidencias URL */}
            {selectedIncidente.evidenciasUrls && (
              <div className="p-3 rounded-lg border border-border bg-card text-xs flex items-center justify-between">
                <span className="text-muted-foreground">Evidencia o Anexos:</span>
                <a
                  href={selectedIncidente.evidenciasUrls}
                  target="_blank"
                  rel="noreferrer"
                  className="font-medium text-primary hover:underline flex items-center gap-1"
                >
                  <span>Abrir enlace de evidencia</span>
                  <ExternalLink className="h-3 w-3" />
                </a>
              </div>
            )}

            {/* Conclusiones de Cierre */}
            {selectedIncidente.estado === 'CERRADO' && (
              <div className="p-3.5 rounded-lg border border-emerald-300 dark:border-emerald-800 bg-emerald-50/60 dark:bg-emerald-950/30 text-xs text-emerald-900 dark:text-emerald-200 space-y-1.5">
                <div className="flex items-center gap-1.5 font-semibold text-emerald-800 dark:text-emerald-300">
                  <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                  <span>Caso Cerrado y Dictamen de la Administración</span>
                </div>
                {selectedIncidente.fechaCierre && (
                  <span className="text-[11px] text-emerald-700 dark:text-emerald-400/90 block">
                    Cerrado el: {formatDateTime(selectedIncidente.fechaCierre)}
                  </span>
                )}
                <p className="leading-relaxed bg-background/80 p-2.5 rounded border border-emerald-200 dark:border-emerald-900/60">
                  {selectedIncidente.conclusionesCierre || 'El caso fue atendido y resuelto de conformidad.'}
                </p>
              </div>
            )}
          </div>
        </Modal>
      )}
    </div>
  );
}
