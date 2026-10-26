import { useState, useMemo, useRef, useEffect, useCallback } from 'react';
import { toast } from 'sonner';
import {
  Clock,
  CheckCircle2,
  AlertCircle,
  Plus,
  Search,
  Eye,
  FileText,
  ShieldCheck,
  Layers,
  Calendar,
  Info,
} from 'lucide-react';

import { PageContainer } from '../components/layout/PageContainer.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';

import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import { formatDate, formatDateTime } from '../lib/utils.js';

const CATS = [
  { value: 'ADMINISTRACION', label: 'Administración y Finanzas' },
  { value: 'MANTENIMIENTO', label: 'Mantenimiento y Reparaciones' },
  { value: 'CONVIVENCIA', label: 'Convivencia y Vecindad' },
  { value: 'SEGURIDAD', label: 'Seguridad y Vigilancia' },
  { value: 'ZONAS_COMUNES', label: 'Zonas Comunes y Parqueaderos' },
  { value: 'OTRO', label: 'Otra Consulta o Gestión' },
];

const TIPOS = [
  { value: 'PETICION', label: 'Petición (Derecho de Consulta / Información)', desc: 'Solicitud formal de información o gestión' },
  { value: 'QUEJA', label: 'Queja (Inconformidad con un servicio o conducta)', desc: 'Manifestación de desacuerdo' },
  { value: 'RECLAMO', label: 'Reclamo (Incumplimiento o falla en servicio)', desc: 'Exigencia de cumplimiento o reparación' },
  { value: 'SUGERENCIA', label: 'Sugerencia (Propuesta de mejora)', desc: 'Idea para mejorar la copropiedad' },
];

const PRIORIDADES = [
  { value: 'BAJA', label: 'Baja' },
  { value: 'MEDIA', label: 'Media (Estándar)' },
  { value: 'ALTA', label: 'Alta (Prioridad Operativa)' },
];

const ESTADO_CONFIG = {
  RADICADO: {
    label: 'Radicado',
    step: 1,
    badgeVariant: 'warning',
    colorClass: 'text-tint-amber-fg',
    bgClass: 'bg-tint-amber-bg border-tint-amber-fg/25',
    icon: Clock,
  },
  ASIGNADO: {
    label: 'Asignado',
    step: 2,
    badgeVariant: 'info',
    colorClass: 'text-tint-blue-fg',
    bgClass: 'bg-tint-blue-bg border-tint-blue-fg/25',
    icon: Layers,
  },
  EN_GESTION: {
    label: 'En Gestión',
    step: 2,
    badgeVariant: 'info',
    colorClass: 'text-tint-blue-fg',
    bgClass: 'bg-tint-blue-bg border-tint-blue-fg/25',
    icon: Layers,
  },
  EN_REVISION: {
    label: 'En Revisión',
    step: 2,
    badgeVariant: 'info',
    colorClass: 'text-tint-blue-fg',
    bgClass: 'bg-tint-blue-bg border-tint-blue-fg/25',
    icon: Layers,
  },
  ESCALADO: {
    label: 'Escalado',
    step: 2,
    badgeVariant: 'warning',
    colorClass: 'text-tint-purple-fg',
    bgClass: 'bg-tint-purple-bg border-tint-purple-fg/25',
    icon: Layers,
  },
  RESUELTO: {
    label: 'Resuelto con Éxito',
    step: 3,
    badgeVariant: 'success',
    colorClass: 'text-tint-green-fg',
    bgClass: 'bg-tint-green-bg border-tint-green-fg/25',
    icon: CheckCircle2,
  },
  CERRADO: {
    label: 'Cerrado',
    step: 3,
    badgeVariant: 'secondary',
    colorClass: 'text-text-secondary',
    bgClass: 'bg-surface-dim border-border',
    icon: ShieldCheck,
  },
  RECHAZADO: {
    label: 'Rechazado',
    step: 3,
    badgeVariant: 'destructive',
    colorClass: 'text-tint-red-fg',
    bgClass: 'bg-tint-red-bg border-tint-red-fg/25',
    icon: AlertCircle,
  },
};

const emptyForm = {
  tipo: 'PETICION',
  categoria: 'ADMINISTRACION',
  prioridad: 'MEDIA',
  asunto: '',
  descripcion: '',
};

export default function ResQuejasPage() {
  const { user } = useAuth();
  const { touch, touchAll, resetTouched, fieldError } = useLiveValidation();

  const [form, setForm] = useState(emptyForm);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalDetalle, setModalDetalle] = useState(null);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [errors, setErrors] = useState({});
  const [traza, setTraza] = useState([]);
  const [trazaLoading, setTrazaLoading] = useState(false);
  const [trazaError, setTrazaError] = useState('');
  const [respuesta, setRespuesta] = useState('');
  const [replying, setReplying] = useState(false);
  const [replyError, setReplyError] = useState('');

  const [tabFiltro, setTabFiltro] = useState('TODOS'); // 'TODOS' | 'EN_TRAMITE' | 'RESUELTOS'
  const [searchTerm, setSearchTerm] = useState('');

  // 1. Carga de mis tickets PQRS
  const {
    data,
    loading,
    error,
    refetch,
  } = useFetch(() => api.get('/pqrs/mis-tickets'), [user]);

  const tickets = useMemo(() => {
    const list = Array.isArray(data) ? data : data?.items || [];
    return Array.isArray(list) ? list : [];
  }, [data]);

  // Métricas
  const stats = useMemo(() => {
    const total = tickets.length;
    const enTramite = tickets.filter(
      (t) => t.estado === 'RADICADO' || t.estado === 'ASIGNADO' || t.estado === 'EN_GESTION' || t.estado === 'EN_REVISION' || t.estado === 'ESCALADO'
    ).length;
    const resueltos = tickets.filter(
      (t) => t.estado === 'RESUELTO' || t.estado === 'CERRADO' || t.estado === 'RECHAZADO'
    ).length;

    return { total, enTramite, resueltos };
  }, [tickets]);

  // Filtrado
  const filteredTickets = useMemo(() => {
    return tickets.filter((t) => {
      const isTramite = t.estado === 'RADICADO' || t.estado === 'ASIGNADO' || t.estado === 'EN_GESTION' || t.estado === 'EN_REVISION' || t.estado === 'ESCALADO';
      const isResuelto = t.estado === 'RESUELTO' || t.estado === 'CERRADO' || t.estado === 'RECHAZADO';
      if (tabFiltro === 'EN_TRAMITE' && !isTramite) {
        return false;
      }
      if (tabFiltro === 'RESUELTOS' && !isResuelto) {
        return false;
      }
      if (searchTerm.trim()) {
        const q = searchTerm.toLowerCase().trim();
        const rad = (t.numeroRadicado || '').toLowerCase();
        const asu = (t.asunto || '').toLowerCase();
        const cat = (t.categoria || '').toLowerCase();
        const desc = (t.descripcion || '').toLowerCase();
        return rad.includes(q) || asu.includes(q) || cat.includes(q) || desc.includes(q);
      }
      return true;
    });
  }, [tickets, tabFiltro, searchTerm]);

  const idDetalle = modalDetalle?.idTicket;
  const loadTraza = useCallback(async (id) => {
    setTrazaLoading(true);
    setTrazaError('');
    try {
      const res = await api.get(`/pqrs/${id}/trazabilidad`);
      setTraza(Array.isArray(res) ? res : res?.items || []);
    } catch (err) {
      setTraza([]);
      setTrazaError(err?.message || 'No se pudo cargar el historial');
    } finally {
      setTrazaLoading(false);
    }
  }, []);

  useEffect(() => {
    if (idDetalle) loadTraza(idDetalle);
  }, [idDetalle, loadTraza]);

  function openDetalle(t) {
    setRespuesta('');
    setReplyError('');
    setModalDetalle(t);
  }

  async function handleReply() {
    if (respuesta.trim().length < 3) {
      setReplyError('El mensaje debe tener al menos 3 caracteres.');
      return;
    }
    setReplying(true);
    setReplyError('');
    try {
      await api.post(`/pqrs/${modalDetalle.idTicket}/responder`, { respuesta: respuesta.trim() });
      toast.success('Mensaje enviado a la administración');
      setRespuesta('');
      loadTraza(modalDetalle.idTicket);
      refetch();
    } catch (err) {
      setReplyError(err?.message || 'No se pudo enviar el mensaje');
    } finally {
      setReplying(false);
    }
  }

  function validate() {
    const e = {};
    if (!form.asunto?.trim() || form.asunto.trim().length < 5) {
      e.asunto = 'El asunto debe ser claro y tener al menos 5 caracteres.';
    }
    if (!form.descripcion?.trim() || form.descripcion.trim().length < 10) {
      e.descripcion = 'Por favor describe los detalles y antecedentes con al menos 10 caracteres.';
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    touchAll(['asunto', 'descripcion']);
    if (!validate()) return;
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const payload = {
        tipo: form.tipo,
        categoria: form.categoria,
        prioridad: form.prioridad,
        asunto: form.asunto.trim(),
        descripcion: form.descripcion.trim(),
      };
      await api.post('/pqrs', payload);
      toast.success('¡Solicitud radicada exitosamente con número de radicado oficial!');
      setModalOpen(false);
      setForm(emptyForm);
      resetTouched();
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al radicar la solicitud PQRS');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <PageContainer>
      <PageHeader
        title="PQRS — atención y solicitudes"
        subtitle="Radica peticiones, quejas, reclamos y sugerencias, y sigue su respuesta y tiempos de atención."
        action={
          <Button
            onClick={() => {
              setForm(emptyForm);
              setErrors({});
              resetTouched();
              setModalOpen(true);
            }}
            className="min-h-10"
          >
            <Plus className="h-4 w-4" aria-hidden="true" />
            Radicar solicitud
          </Button>
        }
      />

      {/* 1. Métricas Ejecutivas */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <MetricCard
          label="Total radicados"
          value={loading ? '…' : error ? '—' : String(stats.total)}
          icon={FileText}
          variant="primary"
          context="Historial de solicitudes"
        />
        <MetricCard
          label="En trámite"
          value={loading ? '…' : error ? '—' : String(stats.enTramite)}
          icon={Clock}
          variant={stats.enTramite > 0 ? 'warning' : 'primary'}
          context={stats.enTramite > 0 ? 'En revisión administrativa' : 'Sin trámites pendientes'}
        />
        <MetricCard
          label="Resueltos / cerrados"
          value={loading ? '…' : error ? '—' : String(stats.resueltos)}
          icon={CheckCircle2}
          variant="success"
          context="Gestión completada"
        />
      </div>

      {/* 2. Barra de Filtros y Búsqueda */}
      <div className="flex flex-col items-stretch justify-between gap-3 sm:flex-row sm:items-center">
        <div className="flex gap-2 items-center flex-wrap w-full sm:w-auto">
          {[
            { id: 'TODOS', label: `Todos (${stats.total})` },
            { id: 'EN_TRAMITE', label: `En trámite (${stats.enTramite})` },
            { id: 'RESUELTOS', label: `Resueltos (${stats.resueltos})` },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setTabFiltro(tab.id)}
              aria-pressed={tabFiltro === tab.id}
              className={`min-h-10 rounded-md px-3.5 text-xs font-semibold tabular-nums transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:min-h-9 ${
                tabFiltro === tab.id
                  ? 'bg-btn-primary text-white shadow-sm'
                  : 'bg-surface border border-border text-text-secondary hover:text-on-surface hover:bg-surface-container'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="w-full sm:w-80">
          <div className="relative">
            <Search className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Buscar por radicado, asunto, categoría..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              aria-label="Buscar solicitudes"
              className="h-10 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm text-on-surface placeholder:text-text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/25"
            />
          </div>
        </div>
      </div>

      {/* 3. Listado de Tickets PQRS */}
      {loading ? (
        <div className="rounded-lg border border-border bg-surface shadow-sm">
          <LoadingState message="Consultando tus solicitudes…" />
        </div>
      ) : error ? (
        <ErrorState
          title="No se pudieron cargar tus solicitudes"
          message="Revisa tu conexión e intenta de nuevo."
          onRetry={() => refetch().catch(() => {})}
        />
      ) : filteredTickets.length === 0 ? (
        <EmptyState
          icon={FileText}
          title="No hay solicitudes en este filtro"
          subtitle={
            tabFiltro === 'EN_TRAMITE'
              ? 'No tienes requerimientos en trámite actualmente.'
              : 'Radica tu primera solicitud con el botón «Radicar solicitud».'
          }
        />
      ) : (
        <div className="space-y-3">
          {filteredTickets.map((t) => {
            const config = ESTADO_CONFIG[t.estado] || ESTADO_CONFIG.RADICADO;
            const Icon = config.icon;
            const esVencidoSla = t.slaEstado === 'VENCIDO';
            const enRiesgoSla = t.slaEstado === 'EN_RIESGO';

            return (
              <div
                key={t.idTicket || t.numeroRadicado}
                className="flex flex-col justify-between gap-4 rounded-lg border border-border bg-surface p-4 shadow-sm transition-colors duration-150 hover:border-primary/50 sm:p-5 md:flex-row md:items-center"
              >
                <div className="flex items-start gap-3.5 min-w-0">
                  <div className={`shrink-0 rounded-md border p-2.5 ${config.bgClass}`}>
                    <Icon className={`w-5 h-5 ${config.colorClass}`} />
                  </div>
                  <div className="space-y-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-mono text-xs font-bold px-2 py-0.5 rounded bg-muted text-foreground">
                        {t.numeroRadicado || `#T-${t.idTicket}`}
                      </span>
                      <h3 className="min-w-0 break-words text-[15px] font-bold text-on-surface">
                        {t.asunto}
                      </h3>
                      <Badge variant={config.badgeVariant} className="text-xs">
                        {config.label}
                      </Badge>
                      {t.tipo && (
                        <span className="text-xs text-muted-foreground bg-muted px-2 py-0.5 rounded">
                          {t.tipo}
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-muted-foreground line-clamp-1">
                      {t.descripcion}
                    </p>
                    <div className="flex items-center gap-4 text-xs text-muted-foreground flex-wrap pt-1">
                      <span className="flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5" />
                        Radicado: <strong className="text-foreground">{formatDate(t.fechaRadicacion)}</strong>
                      </span>
                      {t.categoria && (
                        <span>Categoría: <strong>{t.categoria}</strong></span>
                      )}
                      {t.fechaLimiteSla && (
                        <span
                          className={
                            esVencidoSla
                              ? 'text-tint-red-fg font-semibold'
                              : 'text-muted-foreground'
                          }
                        >
                          Vence SLA: {formatDate(t.fechaLimiteSla)}
                          {esVencidoSla ? ' (vencido)' : enRiesgoSla ? ' (en riesgo)' : ''}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between md:justify-end gap-3 shrink-0 pt-3 md:pt-0 border-t md:border-t-0 border-border">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => openDetalle(t)}
                    className="min-h-10 sm:min-h-8"
                  >
                    <Eye className="h-4 w-4" aria-hidden="true" />
                    Ver trazabilidad
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* 4. Modal de Radicación de PQRS */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Radicar nueva solicitud PQRS"
        footer={
          <div className="flex items-center justify-end gap-2 w-full">
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={handleSubmit} disabled={saving}>
              {saving ? 'Radicando...' : 'Radicar PQRS'}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="form-row">
            <Select
              id="tipo"
              label="Tipo de solicitud *"
              value={form.tipo}
              onChange={(e) => setForm((f) => ({ ...f, tipo: e.target.value }))}
            >
              {TIPOS.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </Select>
            <Select
              id="categoria"
              label="Categoría del asunto *"
              value={form.categoria}
              onChange={(e) => setForm((f) => ({ ...f, categoria: e.target.value }))}
            >
              {CATS.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </Select>
          </div>

          <div className="form-group">
            <Select
              id="prioridad"
              label="Nivel de prioridad"
              value={form.prioridad}
              onChange={(e) => setForm((f) => ({ ...f, prioridad: e.target.value }))}
            >
              {PRIORIDADES.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </Select>
          </div>

          <div className="form-group">
            <Input
              id="asunto"
              label="Asunto de la solicitud *"
              placeholder="Ej. Filtración de agua en zona común de piso 3"
              value={form.asunto}
              onChange={(e) => setForm((f) => ({ ...f, asunto: e.target.value }))}
              onBlur={() => touch('asunto')}
              error={
                fieldError(
                  'asunto',
                  form.asunto.trim().length >= 5
                    ? { ok: true }
                    : { ok: false, mensaje: 'El asunto debe tener al menos 5 caracteres' }
                ) || errors.asunto
              }
              required
            />
          </div>

          <div className="form-group">
            <Textarea
              id="descripcion"
              label="Descripción detallada de los hechos *"
              rows={4}
              placeholder="Explica tu requerimiento con claridad indicando antecedentes, ubicación precisa y cualquier información que facilite la gestión..."
              value={form.descripcion}
              onChange={(e) => setForm((f) => ({ ...f, descripcion: e.target.value }))}
              onBlur={() => touch('descripcion')}
              error={
                fieldError(
                  'descripcion',
                  form.descripcion.trim().length >= 10
                    ? { ok: true }
                    : { ok: false, mensaje: 'La descripción debe tener al menos 10 caracteres' }
                ) || errors.descripcion
              }
              required
            />
          </div>

          <div className="p-3 rounded-xl bg-tint-blue-bg border border-tint-blue-fg/25 flex items-start gap-2.5 text-xs text-tint-blue-fg">
            <Info className="w-4 h-4 shrink-0 text-tint-blue-fg mt-0.5" />
            <div>
              Al enviar, el sistema genera automáticamente un <strong>código de radicado formal</strong> con sello de
              tiempo para garantizar el seguimiento legal conforme a la Ley de Propiedad Horizontal.
            </div>
          </div>
        </form>
      </Modal>

      {/* 5. Modal de Trazabilidad y Detalle del Ticket */}
      <Modal
        open={!!modalDetalle}
        onClose={() => setModalDetalle(null)}
        title={`Trazabilidad Radicado: ${modalDetalle?.numeroRadicado || ''}`}
        footer={
          <Button variant="outline" onClick={() => setModalDetalle(null)}>
            Cerrar ficha
          </Button>
        }
      >
        {modalDetalle && (
          <div className="space-y-4 text-sm">
            {/* Cabecera del ticket */}
            <div className="grid grid-cols-2 gap-3 p-3.5 rounded-xl bg-muted/50 border border-border">
              <div>
                <div className="text-xs text-muted-foreground font-medium">Radicado Oficial</div>
                <div className="font-mono text-sm font-bold text-foreground">
                  {modalDetalle.numeroRadicado || `#T-${modalDetalle.idTicket}`}
                </div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground font-medium">Estado del Trámite</div>
                <div className="mt-0.5">
                  <Badge variant={ESTADO_CONFIG[modalDetalle.estado]?.badgeVariant || 'secondary'}>
                    {ESTADO_CONFIG[modalDetalle.estado]?.label || modalDetalle.estado}
                  </Badge>
                </div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground font-medium">Tipo y Categoría</div>
                <div className="text-sm font-semibold text-foreground truncate">
                  {modalDetalle.tipo} • {modalDetalle.categoria}
                </div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground font-medium">Fecha de Radicación</div>
                <div className="text-sm font-semibold text-foreground">
                  {formatDate(modalDetalle.fechaRadicacion)}
                </div>
              </div>
              {modalDetalle.fechaLimiteSla && (
                <div>
                  <div className="text-xs text-muted-foreground font-medium">Vencimiento SLA</div>
                  <div className="text-sm font-semibold text-foreground">
                    {formatDateTime(modalDetalle.fechaLimiteSla)}
                  </div>
                </div>
              )}
            </div>

            {/* Asunto y Descripción */}
            <div className="p-3.5 rounded-xl border border-border space-y-2">
              <div className="text-xs font-bold text-muted-foreground">
                Contenido de la solicitud
              </div>
              <div className="text-sm font-bold text-foreground">
                {modalDetalle.asunto}
              </div>
              <p className="text-xs text-muted-foreground bg-muted/50 p-3 rounded-lg leading-relaxed whitespace-pre-wrap">
                {modalDetalle.descripcion}
              </p>
            </div>

            {/* Historial de movimientos */}
            <div className="p-3.5 rounded-xl border border-border space-y-2">
              <div className="text-xs font-bold text-muted-foreground">
                Historial y respuestas
              </div>
              {trazaLoading ? (
                <p className="text-xs text-muted-foreground">Cargando historial...</p>
              ) : trazaError ? (
                <p role="alert" className="text-xs text-destructive">{trazaError}</p>
              ) : traza.length === 0 ? (
                <p className="text-xs text-muted-foreground">Sin movimientos registrados.</p>
              ) : (
                <ul className="space-y-2">
                  {traza.map((x) => (
                    <li key={x.idTrazabilidad} className="text-xs bg-muted/50 p-2.5 rounded-lg">
                      <div className="font-semibold text-foreground">
                        {x.tipoIntervencion}
                        {x.estadoNuevo && x.estadoAnterior !== x.estadoNuevo
                          ? ` · ${x.estadoAnterior || '—'} → ${x.estadoNuevo}`
                          : ''}
                      </div>
                      <div className="text-muted-foreground">
                        {x.nombreUsuario || 'Sistema'} · {formatDateTime(x.fechaHora)}
                      </div>
                      {x.comentario && <div className="whitespace-pre-wrap mt-1">{x.comentario}</div>}
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* Responder a la administración (no cambia el estado) */}
            {!['CERRADO', 'RECHAZADO'].includes(modalDetalle.estado) && (
              <div className="p-3.5 rounded-xl border border-border space-y-2">
                <Textarea
                  id="respuesta-residente"
                  label="Responder a la administración"
                  rows={3}
                  placeholder="Agrega información o responde a la administración..."
                  value={respuesta}
                  onChange={(e) => setRespuesta(e.target.value)}
                />
                {replyError && <p role="alert" className="text-xs text-destructive">{replyError}</p>}
                <Button size="sm" onClick={handleReply} disabled={replying}>
                  {replying ? 'Enviando...' : 'Enviar mensaje'}
                </Button>
              </div>
            )}

            {/* Respuesta formal de la administración si existe */}
            {modalDetalle.observacionCierre || modalDetalle.respuestaAdministracion || modalDetalle.ultimaRespuesta ? (
              <div className="p-3.5 rounded-xl bg-tint-green-bg border border-tint-green-fg/25 space-y-1.5">
                <div className="text-xs font-bold text-tint-green-fg flex items-center gap-1.5">
                  <CheckCircle2 className="w-4 h-4 text-tint-green-fg" />
                  Respuesta oficial de la administración
                </div>
                <p className="text-xs text-tint-green-fg leading-relaxed whitespace-pre-wrap">
                  {modalDetalle.observacionCierre || modalDetalle.respuestaAdministracion || modalDetalle.ultimaRespuesta}
                </p>
                {modalDetalle.fechaCierre && (
                  <div className="text-[11px] text-tint-green-fg pt-1">
                    Atendido formalmente el {formatDateTime(modalDetalle.fechaCierre)}
                  </div>
                )}
              </div>
            ) : (
              <div className="p-3 rounded-xl bg-tint-blue-bg border border-tint-blue-fg/25 text-xs text-tint-blue-fg flex items-center gap-2">
                <Clock className="w-4 h-4 text-tint-blue-fg shrink-0" />
                <span>
                  Tu solicitud se encuentra en curso. El equipo de administración revisará los hechos y te
                  notificará por correo y en este buzón.
                </span>
              </div>
            )}
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
