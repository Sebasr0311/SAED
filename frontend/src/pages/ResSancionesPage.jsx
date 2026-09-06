import { useState, useMemo, useRef } from 'react';
import { toast } from 'sonner';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, formatDateTime } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Textarea } from '../components/ui/Form.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import {
  ShieldCheck,
  Clock,
  CheckCircle2,
  XCircle,
  Search,
  Eye,
  Send,
  AlertTriangle,
  ExternalLink,
  Scale,
} from 'lucide-react';

const ESTADOS_CONFIG = {
  NOTIFICADA: {
    label: 'Pliego Notificado',
    sublabel: 'Pendiente de Descargos',
    badgeVariant: 'warning',
    colorClass: 'text-amber-600 dark:text-amber-400',
    bgClass: 'bg-amber-500/10 border-amber-200 dark:border-amber-800/60',
    icon: Clock,
  },
  EN_DESCARGOS: {
    label: 'En Descargos',
    sublabel: 'En Revisión por Comité/Admin',
    badgeVariant: 'info',
    colorClass: 'text-blue-600 dark:text-blue-400',
    bgClass: 'bg-blue-500/10 border-blue-200 dark:border-blue-800/60',
    icon: Scale,
  },
  ABSUELTA: {
    label: 'Absuelta / Exonerada',
    sublabel: 'Sin Responsabilidad',
    badgeVariant: 'success',
    colorClass: 'text-emerald-600 dark:text-emerald-400',
    bgClass: 'bg-emerald-500/10 border-emerald-200 dark:border-emerald-800/60',
    icon: CheckCircle2,
  },
  APLICADA: {
    label: 'Sanción en Firme',
    sublabel: 'Sanción Ratificada',
    badgeVariant: 'destructive',
    colorClass: 'text-rose-600 dark:text-rose-400',
    bgClass: 'bg-rose-500/10 border-rose-200 dark:border-rose-800/60',
    icon: XCircle,
  },
  SANCION_APLICADA: {
    label: 'Sanción en Firme',
    sublabel: 'Sanción Ratificada',
    badgeVariant: 'destructive',
    colorClass: 'text-rose-600 dark:text-rose-400',
    bgClass: 'bg-rose-500/10 border-rose-200 dark:border-rose-800/60',
    icon: XCircle,
  },
  ARCHIVADA: {
    label: 'Archivada',
    sublabel: 'Proceso Cerrado',
    badgeVariant: 'secondary',
    colorClass: 'text-slate-600 dark:text-slate-400',
    bgClass: 'bg-slate-100 dark:bg-slate-800 border-slate-200 dark:border-slate-700',
    icon: ShieldCheck,
  },
};

const GRAVEDAD_CONFIG = {
  LEVE: {
    label: 'Leve',
    badgeVariant: 'secondary',
    borderClass: 'border-slate-300 dark:border-slate-700',
  },
  MODERADA: {
    label: 'Moderada',
    badgeVariant: 'info',
    borderClass: 'border-blue-300 dark:border-blue-700',
  },
  GRAVE: {
    label: 'Grave',
    badgeVariant: 'destructive',
    borderClass: 'border-rose-400 dark:border-rose-600',
  },
};

export default function ResSancionesPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/sanciones/mis-sanciones'));

  const [statusFilter, setStatusFilter] = useState('TODOS');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedSancion, setSelectedSancion] = useState(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);

  // Modal para presentar descargos
  const [descargosModalOpen, setDescargosModalOpen] = useState(false);
  const [descargosTexto, setDescargosTexto] = useState('');
  const [descargosError, setDescargosError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);

  const items = useMemo(() => {
    return Array.isArray(data) ? data : data?.items || [];
  }, [data]);

  // Executive KPIs
  const stats = useMemo(() => {
    const total = items.length;
    const notificada = items.filter((s) => s.estado === 'NOTIFICADA').length;
    const enDescargos = items.filter((s) => s.estado === 'EN_DESCARGOS').length;
    const absueltaOArchivada = items.filter(
      (s) => s.estado === 'ABSUELTA' || s.estado === 'ARCHIVADA'
    ).length;
    const aplicadas = items.filter(
      (s) => s.estado === 'APLICADA' || s.estado === 'SANCION_APLICADA'
    ).length;

    return { total, notificada, enDescargos, absueltaOArchivada, aplicadas };
  }, [items]);

  // Filtered list
  const filteredItems = useMemo(() => {
    return items.filter((s) => {
      if (statusFilter !== 'TODOS') {
        if (statusFilter === 'APLICADA') {
          if (s.estado !== 'APLICADA' && s.estado !== 'SANCION_APLICADA') return false;
        } else if (statusFilter === 'ABSUELTA') {
          if (s.estado !== 'ABSUELTA' && s.estado !== 'ARCHIVADA') return false;
        } else if (s.estado !== statusFilter) {
          return false;
        }
      }

      if (!searchTerm.trim()) return true;
      const q = searchTerm.toLowerCase();
      const exp = (s.numeroExpediente || '').toLowerCase();
      const falta = (s.tipoFalta || '').toLowerCase();
      const hechos = (s.descripcionHechos || '').toLowerCase();
      const art = (s.articuloReglamentoViolado || '').toLowerCase();

      return exp.includes(q) || falta.includes(q) || hechos.includes(q) || art.includes(q);
    });
  }, [items, statusFilter, searchTerm]);

  function handleOpenDescargos(sancion) {
    setSelectedSancion(sancion);
    setDescargosTexto('');
    setDescargosError('');
    setDescargosModalOpen(true);
  }

  function handleOpenDetail(sancion) {
    setSelectedSancion(sancion);
    setDetailModalOpen(true);
  }

  async function handleSubmitDescargos(e) {
    e?.preventDefault();
    if (submittingRef.current) return;

    if (!descargosTexto || descargosTexto.trim().length < 20) {
      setDescargosError('Por favor redacta tus descargos con al menos 20 caracteres.');
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);

    try {
      await api.post(`/sanciones/${selectedSancion.idSancion}/descargos`, {
        descargos: descargosTexto.trim(),
      });
      toast.success('Descargos radicados correctamente ante la administración');
      setDescargosModalOpen(false);
      setDescargosTexto('');
      refetch();
    } catch (err) {
      toast.error('Error al enviar los descargos: ' + (err.message || 'Error de conexión'));
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-6 pb-12 animate-saed-fade">
      {/* Encabezado */}
      <PageHeader
        title="Mis Procesos & Sanciones"
        subtitle="Consulta tus expedientes disciplinarios, requerimientos normativos y ejerce tu derecho al debido proceso y descargos"
      />

      {/* Tarjetas de Métricas / KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          icon="gavel"
          value={stats.total}
          label="Total Procesos"
          color="primary"
        />
        <StatCard
          icon="pending_actions"
          value={stats.notificada}
          label="Por Responder (Pliegos)"
          color="amber"
        />
        <StatCard
          icon="balance"
          value={stats.enDescargos}
          label="En Revisión"
          color="blue"
        />
        <StatCard
          icon="verified"
          value={stats.absueltaOArchivada}
          label="Absueltas / Cerradas"
          color="green"
        />
      </div>

      {/* Barra de Filtros y Búsqueda */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-surface p-3 rounded-xl border border-border shadow-sm">
        {/* Pestañas de Estado */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0 scrollbar-none">
          {[
            { key: 'TODOS', label: 'Todos' },
            { key: 'NOTIFICADA', label: 'Por Responder' },
            { key: 'EN_DESCARGOS', label: 'En Revisión' },
            { key: 'APLICADA', label: 'Sanciones Aplicadas' },
            { key: 'ABSUELTA', label: 'Absueltas' },
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

        {/* Buscador */}
        <div className="relative min-w-[220px]">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Buscar por expediente, falta o artículo..."
            className="w-full pl-8 pr-3 py-1.5 text-xs rounded-lg border border-input bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring"
          />
        </div>
      </div>

      {/* Contenido Principal */}
      {loading ? (
        <LoadingState message="Consultando expedientes disciplinarios..." />
      ) : error ? (
        <div className="rounded-xl border border-rose-200 dark:border-rose-900/60 bg-rose-50/50 dark:bg-rose-950/20 p-6 text-center">
          <AlertTriangle className="mx-auto h-8 w-8 text-rose-500 mb-2" />
          <h4 className="font-semibold text-rose-700 dark:text-rose-400">Error al cargar expedientes</h4>
          <p className="text-xs text-muted-foreground mt-1">{error.message || 'Error de comunicación'}</p>
          <Button variant="outline" size="sm" onClick={() => refetch()} className="mt-4">
            Reintentar
          </Button>
        </div>
      ) : filteredItems.length === 0 ? (
        <EmptyState
          icon={<ShieldCheck className="h-10 w-10 text-emerald-600 dark:text-emerald-400" />}
          title={
            searchTerm || statusFilter !== 'TODOS'
              ? 'No se encontraron expedientes con ese filtro'
              : 'Todo en orden: No tienes sanciones ni pliegos activos'
          }
          subtitle={
            searchTerm || statusFilter !== 'TODOS'
              ? 'Intenta restablecer los filtros para ver todos los expedientes.'
              : 'Estás al día con las normas de convivencia de la copropiedad. No registras quejas sancionatorias ni procesos en curso.'
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredItems.map((sancion) => {
            const estadoCfg = ESTADOS_CONFIG[sancion.estado] || ESTADOS_CONFIG.NOTIFICADA;
            const IconEstado = estadoCfg.icon;
            const gravedadCfg = GRAVEDAD_CONFIG[sancion.gravedad] || GRAVEDAD_CONFIG.MODERADA;
            const requiereDescargos = sancion.estado === 'NOTIFICADA';

            return (
              <div
                key={sancion.idSancion}
                className="group relative flex flex-col justify-between rounded-xl border border-border bg-card p-5 shadow-sm transition-all duration-200 hover:shadow-md hover:border-border/80"
              >
                <div>
                  {/* Encabezado de la Tarjeta */}
                  <div className="flex items-start justify-between gap-2 mb-3">
                    <div className="flex items-center gap-2">
                      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary font-bold text-xs font-mono">
                        #{sancion.idSancion}
                      </div>
                      <div>
                        <span className="text-xs font-semibold text-foreground block font-mono">
                          {sancion.numeroExpediente || `EXP-${sancion.idSancion}`}
                        </span>
                        <Badge variant={gravedadCfg.badgeVariant} className="text-[10px] py-0 px-1.5 font-medium">
                          {sancion.gravedad || 'Moderada'}
                        </Badge>
                      </div>
                    </div>

                    <Badge variant={estadoCfg.badgeVariant} className="flex items-center gap-1 text-[11px] py-0.5">
                      <IconEstado className="h-3 w-3" />
                      <span>{estadoCfg.label}</span>
                    </Badge>
                  </div>

                  {/* Tipo de Falta y Artículo */}
                  <div className="mb-2">
                    <h3 className="font-semibold text-foreground text-sm leading-snug line-clamp-2">
                      {sancion.tipoFalta || 'Infracción al Reglamento de Convivencia'}
                    </h3>
                    {sancion.articuloReglamentoViolado && (
                      <span className="text-[11px] text-muted-foreground font-medium block mt-0.5">
                        {sancion.articuloReglamentoViolado}
                      </span>
                    )}
                  </div>

                  {/* Hechos Imputados */}
                  <p className="text-xs text-muted-foreground leading-relaxed line-clamp-3 mb-3.5 bg-muted/20 p-2.5 rounded-lg border border-border/50">
                    {sancion.descripcionHechos || 'Sin descripción de los hechos imputados.'}
                  </p>

                  {/* Cronograma y Vencimiento */}
                  <div className="space-y-1.5 text-xs text-muted-foreground mb-3.5">
                    {sancion.fechaAperturaPliego && (
                      <div className="flex items-center justify-between text-[11px]">
                        <span>Apertura de Pliego:</span>
                        <span className="font-medium text-foreground">{formatDate(sancion.fechaAperturaPliego)}</span>
                      </div>
                    )}
                    {sancion.fechaLimiteDescargos && (
                      <div className="flex items-center justify-between text-[11px] p-1.5 rounded bg-amber-500/10 border border-amber-200 dark:border-amber-900/60 text-amber-800 dark:text-amber-300">
                        <span className="flex items-center gap-1 font-semibold">
                          <Clock className="h-3 w-3" />
                          <span>Límite Descargos:</span>
                        </span>
                        <span className="font-bold">{formatDate(sancion.fechaLimiteDescargos)}</span>
                      </div>
                    )}
                  </div>

                  {/* Sanción Propuesta o Dictamen */}
                  {sancion.tipoSancionPropuesta && (
                    <div className="text-[11px] text-muted-foreground mb-3 flex items-center justify-between p-2 rounded bg-muted/40 border border-border/50">
                      <span>Sanción Propuesta:</span>
                      <span className="font-semibold text-foreground">{sancion.tipoSancionPropuesta}</span>
                    </div>
                  )}

                  {/* Dictamen de Administración si aplica */}
                  {sancion.resolucionFinal && (
                    <div className="rounded-lg bg-emerald-500/10 border border-emerald-200 dark:border-emerald-900/60 p-2.5 mb-3 text-xs text-emerald-800 dark:text-emerald-300">
                      <div className="flex items-center gap-1.5 font-semibold text-[11px] mb-1">
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
                        <span>Resolución de la Administración:</span>
                      </div>
                      <p className="text-[11px] leading-relaxed line-clamp-2 text-emerald-700 dark:text-emerald-400/90">
                        {sancion.resolucionFinal}
                      </p>
                    </div>
                  )}
                </div>

                {/* Footer de Tarjeta con Acciones */}
                <div className="pt-3 border-t border-border flex items-center justify-between gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleOpenDetail(sancion)}
                    className="h-8 text-xs flex items-center gap-1"
                  >
                    <Eye className="h-3.5 w-3.5" />
                    <span>Ver Expediente</span>
                  </Button>

                  {requiereDescargos && (
                    <Button
                      size="sm"
                      onClick={() => handleOpenDescargos(sancion)}
                      className="h-8 text-xs flex items-center gap-1 font-semibold"
                    >
                      <Send className="h-3.5 w-3.5" />
                      <span>Presentar Descargos</span>
                    </Button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal: Presentar Descargos */}
      {selectedSancion && (
        <Modal
          open={descargosModalOpen}
          onClose={() => !submitting && setDescargosModalOpen(false)}
          title={`Presentar Descargos - Expediente ${selectedSancion.numeroExpediente || `#${selectedSancion.idSancion}`}`}
          size="lg"
          footer={
            <div className="flex items-center justify-end gap-2 w-full">
              <Button
                type="button"
                variant="outline"
                onClick={() => setDescargosModalOpen(false)}
                disabled={submitting}
              >
                Cancelar
              </Button>
              <Button
                type="button"
                onClick={handleSubmitDescargos}
                disabled={submitting}
                className="flex items-center gap-2"
              >
                {submitting ? (
                  <>
                    <span className="inline-block w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                    <span>Radicando...</span>
                  </>
                ) : (
                  <>
                    <Send className="h-4 w-4" />
                    <span>Radicar Descargos</span>
                  </>
                )}
              </Button>
            </div>
          }
        >
          <form onSubmit={handleSubmitDescargos} className="space-y-4 py-1">
            {/* Banner de Garantía de Debido Proceso */}
            <div className="rounded-lg bg-blue-50/70 dark:bg-blue-950/20 border border-blue-200/60 dark:border-blue-800/40 p-3 text-xs text-blue-800 dark:text-blue-300 flex items-start gap-2.5">
              <Scale className="h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400 mt-0.5" />
              <div>
                <p className="font-semibold">Garantía Constitucional y Ley 675 de 2001</p>
                <p className="mt-0.5 text-blue-700 dark:text-blue-400/90 leading-relaxed">
                  Tienes derecho al debido proceso y a la contradicción. Describe tu versión de los hechos, aclaraciones y justificaciones. Este documento será revisado formalmente por el Comité de Convivencia y la Administración.
                </p>
              </div>
            </div>

            {/* Resumen del pliego */}
            <div className="p-3 rounded-lg border border-border bg-muted/20 text-xs space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Falta Imputada:</span>
                <span className="font-semibold text-foreground">{selectedSancion.tipoFalta}</span>
              </div>
              {selectedSancion.articuloReglamentoViolado && (
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Artículo Violado:</span>
                  <span className="font-medium text-foreground">{selectedSancion.articuloReglamentoViolado}</span>
                </div>
              )}
              {selectedSancion.fechaLimiteDescargos && (
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Fecha Límite:</span>
                  <span className="font-bold text-amber-600 dark:text-amber-400">
                    {formatDate(selectedSancion.fechaLimiteDescargos)}
                  </span>
                </div>
              )}
              <div className="pt-1 border-t border-border text-muted-foreground">
                <span className="font-medium block text-foreground mb-0.5">Hechos Notificados:</span>
                <p className="line-clamp-3">{selectedSancion.descripcionHechos}</p>
              </div>
            </div>

            <Textarea
              label="Tus Descargos, Aclaraciones y Pruebas"
              required
              id="descargos-texto"
              rows={5}
              placeholder="Explica detalladamente tu versión de los hechos, razones justificativas o aclaraciones..."
              value={descargosTexto}
              error={descargosError}
              onChange={(e) => {
                setDescargosTexto(e.target.value);
                if (descargosError) setDescargosError('');
              }}
            />
          </form>
        </Modal>
      )}

      {/* Modal: Ver Expediente Completo */}
      {selectedSancion && (
        <Modal
          open={detailModalOpen}
          onClose={() => setDetailModalOpen(false)}
          title={`Expediente Disciplinario #${selectedSancion.numeroExpediente || selectedSancion.idSancion}`}
          size="lg"
          footer={
            <div className="flex items-center justify-between w-full">
              {selectedSancion.estado === 'NOTIFICADA' ? (
                <Button
                  size="sm"
                  onClick={() => {
                    setDetailModalOpen(false);
                    handleOpenDescargos(selectedSancion);
                  }}
                  className="flex items-center gap-1.5"
                >
                  <Send className="h-3.5 w-3.5" />
                  <span>Presentar Descargos</span>
                </Button>
              ) : <div />}

              <Button variant="outline" onClick={() => setDetailModalOpen(false)}>
                Cerrar
              </Button>
            </div>
          }
        >
          <div className="space-y-4 py-2 text-sm">
            {/* Estado y Gravedad */}
            <div className="flex flex-wrap items-center justify-between gap-2 p-3 rounded-lg bg-muted/30 border border-border">
              <div>
                <span className="text-xs text-muted-foreground font-medium block">Estado del Proceso</span>
                <span className="font-semibold text-foreground">
                  {ESTADOS_CONFIG[selectedSancion.estado]?.label || selectedSancion.estado}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant={GRAVEDAD_CONFIG[selectedSancion.gravedad]?.badgeVariant || 'secondary'}>
                  Gravedad: {selectedSancion.gravedad || 'Moderada'}
                </Badge>
                <Badge variant={ESTADOS_CONFIG[selectedSancion.estado]?.badgeVariant || 'default'}>
                  {selectedSancion.estado}
                </Badge>
              </div>
            </div>

            {/* Falta y Normativa */}
            <div>
              <span className="text-xs text-muted-foreground font-semibold uppercase block mb-1">Motivo / Falta</span>
              <h4 className="font-semibold text-foreground text-sm mb-1">{selectedSancion.tipoFalta}</h4>
              {selectedSancion.articuloReglamentoViolado && (
                <span className="text-xs text-muted-foreground">
                  Referencia reglamentaria: {selectedSancion.articuloReglamentoViolado}
                </span>
              )}
            </div>

            {/* Hechos */}
            <div>
              <span className="text-xs text-muted-foreground font-semibold uppercase block mb-1">Hechos Imputados</span>
              <div className="p-3 rounded-lg border border-border bg-background text-xs leading-relaxed text-foreground">
                {selectedSancion.descripcionHechos}
              </div>
            </div>

            {/* Fechas */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Fecha de Notificación</span>
                <span className="font-medium text-foreground">
                  {formatDateTime(selectedSancion.fechaAperturaPliego)}
                </span>
              </div>
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Fecha Límite para Descargos</span>
                <span className="font-medium text-amber-600 dark:text-amber-400">
                  {formatDate(selectedSancion.fechaLimiteDescargos)}
                </span>
              </div>
            </div>

            {/* Sanción Propuesta */}
            {selectedSancion.tipoSancionPropuesta && (
              <div className="p-2.5 rounded-lg border border-border bg-muted/20 text-xs flex items-center justify-between">
                <span className="text-muted-foreground">Sanción o Medida Propuesta:</span>
                <span className="font-semibold text-foreground">{selectedSancion.tipoSancionPropuesta}</span>
              </div>
            )}

            {/* Evidencias si existen */}
            {selectedSancion.evidenciasUrls && (
              <div className="p-2.5 rounded-lg border border-border bg-card text-xs flex items-center justify-between">
                <span className="text-muted-foreground">Anexos / Evidencias:</span>
                <a
                  href={selectedSancion.evidenciasUrls}
                  target="_blank"
                  rel="noreferrer"
                  className="font-medium text-primary hover:underline flex items-center gap-1"
                >
                  <span>Ver evidencia adjunta</span>
                  <ExternalLink className="h-3 w-3" />
                </a>
              </div>
            )}

            {/* Dictamen / Resolución Final */}
            {selectedSancion.resolucionFinal && (
              <div className="p-3.5 rounded-lg border border-emerald-300 dark:border-emerald-800 bg-emerald-50/60 dark:bg-emerald-950/30 text-xs text-emerald-900 dark:text-emerald-200 space-y-1.5">
                <div className="flex items-center gap-1.5 font-semibold text-emerald-800 dark:text-emerald-300">
                  <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                  <span>Resolución y Fallo de la Administración</span>
                </div>
                {selectedSancion.fechaResolucion && (
                  <span className="text-[11px] text-emerald-700 dark:text-emerald-400/90 block">
                    Emitida el: {formatDateTime(selectedSancion.fechaResolucion)}
                  </span>
                )}
                <p className="leading-relaxed bg-background/80 p-2.5 rounded border border-emerald-200 dark:border-emerald-900/60">
                  {selectedSancion.resolucionFinal}
                </p>
              </div>
            )}
          </div>
        </Modal>
      )}
    </div>
  );
}
