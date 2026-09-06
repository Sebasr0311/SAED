import { useState, useMemo, useRef } from 'react';
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
import { Card } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
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
    colorClass: 'text-amber-700 dark:text-amber-400',
    bgClass: 'bg-amber-50 dark:bg-amber-950/40 border-amber-200 dark:border-amber-800/60',
    icon: Clock,
  },
  EN_REVISION: {
    label: 'En Revisión',
    step: 2,
    badgeVariant: 'info',
    colorClass: 'text-blue-700 dark:text-blue-400',
    bgClass: 'bg-blue-50 dark:bg-blue-950/40 border-blue-200 dark:border-blue-800/60',
    icon: Layers,
  },
  RESUELTO: {
    label: 'Resuelto con Éxito',
    step: 3,
    badgeVariant: 'success',
    colorClass: 'text-emerald-700 dark:text-emerald-400',
    bgClass: 'bg-emerald-50 dark:bg-emerald-950/40 border-emerald-200 dark:border-emerald-800/60',
    icon: CheckCircle2,
  },
  CERRADO: {
    label: 'Cerrado',
    step: 3,
    badgeVariant: 'secondary',
    colorClass: 'text-slate-600 dark:text-slate-400',
    bgClass: 'bg-slate-100 dark:bg-slate-800 border-slate-200 dark:border-slate-700',
    icon: ShieldCheck,
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
      (t) => t.estado === 'RADICADO' || t.estado === 'EN_REVISION'
    ).length;
    const resueltos = tickets.filter(
      (t) => t.estado === 'RESUELTO' || t.estado === 'CERRADO'
    ).length;

    return { total, enTramite, resueltos };
  }, [tickets]);

  // Filtrado
  const filteredTickets = useMemo(() => {
    return tickets.filter((t) => {
      if (tabFiltro === 'EN_TRAMITE' && t.estado !== 'RADICADO' && t.estado !== 'EN_REVISION') {
        return false;
      }
      if (tabFiltro === 'RESUELTOS' && t.estado !== 'RESUELTO' && t.estado !== 'CERRADO') {
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
        title="PQRS — Atención y Solicitudes"
        subtitle="Radica peticiones, quejas, reclamos y sugerencias con seguimiento oficial y tiempos SLA"
        action={
          <Button
            onClick={() => {
              setForm(emptyForm);
              setErrors({});
              resetTouched();
              setModalOpen(true);
            }}
          >
            <Plus className="w-4 h-4 mr-1.5" />
            Radicar Solicitud
          </Button>
        }
      />

      {/* 1. Métricas Ejecutivas */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        <MetricCard
          label="Total Radicados"
          value={String(stats.total)}
          icon={FileText}
          variant="primary"
          context="Historial de solicitudes"
        />
        <MetricCard
          label="En Trámite"
          value={String(stats.enTramite)}
          icon={Clock}
          variant={stats.enTramite > 0 ? 'warning' : 'primary'}
          context={stats.enTramite > 0 ? 'En revisión administrativa' : 'Sin trámites pendientes'}
        />
        <MetricCard
          label="Resueltos / Cerrados"
          value={String(stats.resueltos)}
          icon={CheckCircle2}
          variant="success"
          context="Gestión completada"
        />
      </div>

      {/* 2. Barra de Filtros y Búsqueda */}
      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between mb-4">
        <div className="flex gap-2 items-center flex-wrap w-full sm:w-auto">
          {[
            { id: 'TODOS', label: `Todos (${stats.total})` },
            { id: 'EN_TRAMITE', label: `En Trámite (${stats.enTramite})` },
            { id: 'RESUELTOS', label: `Resueltos (${stats.resueltos})` },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setTabFiltro(tab.id)}
              className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                tabFiltro === tab.id
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'bg-card border border-border text-muted-foreground hover:text-foreground hover:bg-muted/50'
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
              className="w-full pl-9 pr-3 py-1.5 text-sm rounded-lg border border-border bg-card text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>
        </div>
      </div>

      {/* 3. Listado de Tickets PQRS */}
      {loading ? (
        <LoadingState text="Consultando historial de radicados PQRS..." />
      ) : error ? (
        <Card className="p-8 text-center">
          <AlertCircle className="w-8 h-8 text-destructive mx-auto mb-2" />
          <p className="text-sm font-medium text-destructive">{error.message || 'Error al cargar tickets'}</p>
          <Button variant="outline" size="sm" className="mt-4" onClick={() => refetch()}>
            Reintentar
          </Button>
        </Card>
      ) : filteredTickets.length === 0 ? (
        <EmptyState
          icon="support_agent"
          title="No hay solicitudes en este filtro"
          subtitle={
            tabFiltro === 'EN_TRAMITE'
              ? 'No tienes requerimientos en trámite actualmente.'
              : 'Radica tu primera solicitud con el botón "Radicar Solicitud".'
          }
        />
      ) : (
        <div className="space-y-3">
          {filteredTickets.map((t) => {
            const config = ESTADO_CONFIG[t.estado] || ESTADO_CONFIG.RADICADO;
            const Icon = config.icon;
            const esVencidoSla =
              t.fechaLimiteSla &&
              new Date(t.fechaLimiteSla) < new Date() &&
              t.estado !== 'RESUELTO' &&
              t.estado !== 'CERRADO';

            return (
              <div
                key={t.idTicket || t.numeroRadicado}
                className="bg-card border border-border rounded-xl p-4 sm:p-5 hover:border-primary/30 transition-all shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4"
              >
                <div className="flex items-start gap-3.5 min-w-0">
                  <div className={`p-2.5 rounded-xl shrink-0 ${config.bgClass}`}>
                    <Icon className={`w-5 h-5 ${config.colorClass}`} />
                  </div>
                  <div className="space-y-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-mono text-xs font-bold px-2 py-0.5 rounded bg-muted text-foreground">
                        {t.numeroRadicado || `#T-${t.idTicket}`}
                      </span>
                      <h3 className="text-base font-bold text-foreground truncate">
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
                              ? 'text-rose-600 dark:text-rose-400 font-semibold'
                              : 'text-muted-foreground'
                          }
                        >
                          Vence SLA: {formatDate(t.fechaLimiteSla)}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                <div className="flex items-center justify-between md:justify-end gap-3 shrink-0 pt-3 md:pt-0 border-t md:border-t-0 border-border">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setModalDetalle(t)}
                  >
                    <Eye className="w-4 h-4 mr-1.5" />
                    Ver Trazabilidad
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
        title="Radicar Nueva Solicitud PQRS"
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
              label="Tipo de Solicitud *"
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
              label="Categoría del Asunto *"
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
              label="Nivel de Prioridad"
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
              label="Asunto de la Solicitud *"
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
              label="Descripción Detallada de los Hechos *"
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

          <div className="p-3 rounded-xl bg-blue-50/60 dark:bg-blue-950/30 border border-blue-100 dark:border-blue-900/50 flex items-start gap-2.5 text-xs text-blue-900 dark:text-blue-300">
            <Info className="w-4 h-4 shrink-0 text-blue-600 mt-0.5" />
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
            Cerrar Ficha
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
            </div>

            {/* Asunto y Descripción */}
            <div className="p-3.5 rounded-xl border border-border space-y-2">
              <div className="text-xs font-bold text-muted-foreground uppercase tracking-wider">
                Contenido de la Solicitud
              </div>
              <div className="text-sm font-bold text-foreground">
                {modalDetalle.asunto}
              </div>
              <p className="text-xs text-muted-foreground bg-muted/50 p-3 rounded-lg leading-relaxed whitespace-pre-wrap">
                {modalDetalle.descripcion}
              </p>
            </div>

            {/* Respuesta formal de la administración si existe */}
            {modalDetalle.observacionCierre || modalDetalle.respuestaAdministracion ? (
              <div className="p-3.5 rounded-xl bg-emerald-50 dark:bg-emerald-950/30 border border-emerald-200 dark:border-emerald-800/60 space-y-1.5">
                <div className="text-xs font-bold text-emerald-900 dark:text-emerald-300 flex items-center gap-1.5">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                  Respuesta Oficial de la Administración
                </div>
                <p className="text-xs text-emerald-950 dark:text-emerald-200 leading-relaxed whitespace-pre-wrap">
                  {modalDetalle.observacionCierre || modalDetalle.respuestaAdministracion}
                </p>
                {modalDetalle.fechaCierre && (
                  <div className="text-[11px] text-emerald-700/80 dark:text-emerald-400 pt-1">
                    Atendido formalmente el {formatDateTime(modalDetalle.fechaCierre)}
                  </div>
                )}
              </div>
            ) : (
              <div className="p-3 rounded-xl bg-blue-50/50 dark:bg-blue-950/20 border border-blue-100 dark:border-blue-900/40 text-xs text-blue-900 dark:text-blue-300 flex items-center gap-2">
                <Clock className="w-4 h-4 text-blue-600 shrink-0" />
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
