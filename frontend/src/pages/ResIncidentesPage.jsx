import { useState, useMemo, useRef } from 'react';
import { toast } from 'sonner';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDateTime } from '../lib/utils.js';
import { safeHttpUrls } from '../lib/safeUrl.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
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
    borderClass: 'border-border',
    colorClass: 'text-text-secondary',
    bgClass: 'bg-surface-dim text-text-secondary',
  },
  MODERADA: {
    label: 'Moderada',
    badgeVariant: 'info',
    borderClass: 'border-tint-blue-fg/25',
    colorClass: 'text-tint-blue-fg',
    bgClass: 'bg-tint-blue-bg text-tint-blue-fg',
  },
  ALTA: {
    label: 'Alta',
    badgeVariant: 'warning',
    borderClass: 'border-tint-amber-fg/25',
    colorClass: 'text-tint-amber-fg',
    bgClass: 'bg-tint-amber-bg text-tint-amber-fg',
  },
  GRAVE: {
    label: 'Grave',
    badgeVariant: 'warning',
    borderClass: 'border-tint-amber-fg',
    colorClass: 'text-tint-amber-fg',
    bgClass: 'bg-tint-amber-bg text-tint-amber-fg',
  },
  CRITICA: {
    label: 'Crítica',
    badgeVariant: 'destructive',
    borderClass: 'border-tint-red-fg',
    colorClass: 'text-tint-red-fg',
    bgClass: 'bg-tint-red-bg text-tint-red-fg',
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
    <PageContainer className="pb-12">
      {/* Encabezado */}
      <PageHeader
        title="Mis incidentes & novedades"
        subtitle="Registro y seguimiento en tiempo real de novedades locativas, emergencias o contingencias en tu unidad"
        action={
          <Button
            onClick={handleOpenCreate}
            className="flex items-center gap-2 shadow-sm font-semibold"
          >
            <Plus className="h-4 w-4" />
            <span>Reportar nuevo incidente</span>
          </Button>
        }
      />

      {/* Tarjetas de Métricas / KPIs */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard icon={AlertTriangle} value={loading ? '…' : error ? '—' : stats.total} label="Total incidentes" variant="primary" />
        <MetricCard icon={Clock} value={loading ? '…' : error ? '—' : stats.abiertos} label="En atención" variant="warning" />
        <MetricCard icon={CheckCircle2} value={loading ? '…' : error ? '—' : stats.cerrados} label="Casos resueltos" variant="success" />
        <MetricCard icon={Siren} value={loading ? '…' : error ? '—' : stats.criticos} label="Alta prioridad" variant="danger" />
      </div>

      {/* Barra de Filtros y Búsqueda */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3 bg-surface p-3 rounded-lg border border-border shadow-sm">
        {/* Pestañas de Estado */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0 scrollbar-none">
          {[
            { key: 'TODOS', label: 'Todos' },
            { key: 'REPORTADO', label: 'En atención' },
            { key: 'CERRADO', label: 'Resueltos' },
          ].map((tab) => {
            const isActive = statusFilter === tab.key;
            return (
              <button
                key={tab.key}
                type="button"
                onClick={() => setStatusFilter(tab.key)}
                aria-pressed={isActive}
                className={`min-h-10 shrink-0 rounded-md px-3 text-xs font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:min-h-9 ${
                  isActive
                    ? 'bg-btn-primary text-white shadow-sm'
                    : 'text-text-secondary hover:bg-surface-container hover:text-on-surface'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>

        {/* Filtros Secundarios y Búsqueda */}
        <div className="flex flex-1 flex-col gap-2 sm:flex-row sm:items-center md:justify-end">
          <select
            value={categoryFilter}
            onChange={(e) => setCategoryFilter(e.target.value)}
            className="h-10 rounded-md border border-input bg-surface px-2.5 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/25 sm:h-9"
            aria-label="Filtrar por categoría"
          >
            <option value="TODAS">Todas las categorías</option>
            {Object.entries(TIPOS_MAP).map(([key, val]) => (
              <option key={key} value={key}>
                {val}
              </option>
            ))}
          </select>

          <div className="relative min-w-0 sm:min-w-[220px]">
            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Buscar título o detalle..."
              className="h-10 w-full rounded-md border border-input bg-surface pl-8 pr-3 text-sm text-on-surface placeholder:text-text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/25 sm:h-9"
            />
          </div>
        </div>
      </div>

      {/* Contenido Principal */}
      {loading ? (
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState message="Cargando bitácora de incidentes…" />
        </div>
      ) : error ? (
        <ErrorState
          title="No se pudieron cargar incidentes"
          message="Revisa tu conexión e intenta de nuevo."
          onRetry={() => refetch().catch(() => {})}
        />
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
              <span>Registrar primer incidente</span>
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
                className="group relative flex flex-col justify-between rounded-lg border border-border bg-surface p-5 shadow-sm transition-all duration-200 hover:shadow-md hover:border-primary/50"
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
                    <div className="rounded-lg bg-tint-red-bg border border-tint-red-fg/25 p-2 mb-3 text-[11px] text-tint-red-fg flex items-center gap-2">
                      <Siren className="h-3.5 w-3.5 shrink-0 text-tint-red-fg" />
                      <div className="truncate">
                        <span className="font-semibold">Intervención de autoridad:</span> {incidente.entidadAutoridad || 'Asistida'}
                        {incidente.numeroDenunciaPolicia && (
                          <span className="block text-[10px] text-tint-red-fg truncate">
                            Radicado: {incidente.numeroDenunciaPolicia}
                          </span>
                        )}
                      </div>
                    </div>
                  )}

                  {/* Conclusión de Cierre si está cerrado */}
                  {isCerrado && incidente.conclusionesCierre && (
                    <div className="rounded-lg bg-tint-green-bg border border-tint-green-fg/25 p-2.5 mb-3 text-xs text-tint-green-fg">
                      <div className="flex items-center gap-1.5 font-semibold text-[11px] mb-1">
                        <CheckCircle2 className="h-3.5 w-3.5 text-tint-green-fg" />
                        <span>Resolución de administración:</span>
                      </div>
                      <p className="text-[11px] leading-relaxed line-clamp-2 text-tint-green-fg">
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
                    <span>Ver detalle</span>
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
        title="Reportar incidente o novedad"
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
                  <span>Enviar reporte</span>
                </>
              )}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreate} className="space-y-4 py-1">
          <Input
            label="Título breve del incidente"
            required
            id="incidente-titulo"
            placeholder="Ej: Fuga de agua en tubería principal, Ruido excesivo después de medianoche..."
            value={form.titulo}
            error={formErrors.titulo}
            onChange={(e) => setForm({ ...form, titulo: e.target.value })}
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Select
              label="Tipo de novedad / problema"
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
              label="Fecha y hora de ocurrencia"
              required
              id="incidente-fecha-hora"
              value={form.fechaHoraIncidente}
              onChange={(e) => setForm({ ...form, fechaHoraIncidente: e.target.value })}
            />
          </div>

          {/* Selector Visual de Nivel de Severidad */}
          <div>
            <label className="text-sm font-medium text-foreground block mb-1.5">
              Nivel de severidad / urgencia
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {[
                { key: 'LEVE', label: 'Leve', desc: 'Molestia o arreglo menor', color: 'border-border' },
                { key: 'MODERADA', label: 'Moderada', desc: 'Afecta rutina normal', color: 'border-tint-blue-fg' },
                { key: 'GRAVE', label: 'Grave', desc: 'Daño o riesgo material', color: 'border-tint-amber-fg' },
                { key: 'CRITICA', label: 'Crítica', desc: 'Amenaza a seguridad o vida', color: 'border-tint-red-fg' },
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
            label="Descripción detallada de los hechos"
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
                className="h-4 w-4 rounded border-border text-primary focus:ring-primary"
              />
              <span className="text-xs font-semibold text-foreground">
                ¿Se requirió presencia o llamada a Autoridades (Policía, Bomberos, Cuadrante)?
              </span>
            </label>

            {form.requirioAutoridades === 'S' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t border-border/70">
                <Input
                  label="Entidad u organismo asistente"
                  required
                  id="incidente-entidad"
                  placeholder="Ej: Policía Nacional Cuadrante 14, Bomberos Estación Central"
                  value={form.entidadAutoridad}
                  error={formErrors.entidadAutoridad}
                  onChange={(e) => setForm({ ...form, entidadAutoridad: e.target.value })}
                />

                <Input
                  label="Número de denuncia / radicado POLNAL (opcional)"
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
              label="Medidas inmediatas tomadas (opcional)"
              id="incidente-acciones"
              placeholder="Ej: Se cerró la llave de paso general del apartamento..."
              value={form.accionesInmediatas}
              onChange={(e) => setForm({ ...form, accionesInmediatas: e.target.value })}
            />

            <Input
              type="url"
              label="Enlace a fotos / evidencias (opcional)"
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
                <span className="text-muted-foreground block text-[11px]">Fecha y hora del incidente</span>
                <span className="font-medium text-foreground">{formatDateTime(selectedIncidente.fechaHoraIncidente)}</span>
              </div>
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Fecha de radicación en plataforma</span>
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
              <div className="p-3 rounded-lg border border-tint-red-fg/25 bg-tint-red-bg text-xs text-tint-red-fg space-y-1">
                <div className="flex items-center gap-1.5 font-semibold">
                  <Siren className="h-4 w-4 text-tint-red-fg" />
                  <span>Intervención de autoridades asistida</span>
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
                <span className="text-muted-foreground">Evidencia o anexos:</span>
                {safeHttpUrls(selectedIncidente.evidenciasUrls).length > 0 ? (
                  <span className="flex flex-col items-end gap-1">
                    {safeHttpUrls(selectedIncidente.evidenciasUrls).map((href, i) => (
                      <a
                        key={href}
                        href={href}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="font-medium text-primary hover:underline flex items-center gap-1"
                      >
                        <span>Abrir enlace de evidencia {i + 1}</span>
                        <ExternalLink className="h-3 w-3" />
                      </a>
                    ))}
                  </span>
                ) : (
                  <span className="text-muted-foreground">Adjunto no enlazable</span>
                )}
              </div>
            )}

            {/* Conclusiones de Cierre */}
            {selectedIncidente.estado === 'CERRADO' && (
              <div className="p-3.5 rounded-lg border border-tint-green-fg/25 bg-tint-green-bg text-xs text-tint-green-fg space-y-1.5">
                <div className="flex items-center gap-1.5 font-semibold text-tint-green-fg">
                  <CheckCircle2 className="h-4 w-4 text-tint-green-fg" />
                  <span>Caso cerrado y dictamen de la administración</span>
                </div>
                {selectedIncidente.fechaCierre && (
                  <span className="text-[11px] text-tint-green-fg block">
                    Cerrado el: {formatDateTime(selectedIncidente.fechaCierre)}
                  </span>
                )}
                <p className="leading-relaxed bg-background/80 p-2.5 rounded border border-tint-green-fg/25">
                  {selectedIncidente.conclusionesCierre || 'El caso fue atendido y resuelto de conformidad.'}
                </p>
              </div>
            )}
          </div>
        </Modal>
      )}
    </PageContainer>
  );
}
