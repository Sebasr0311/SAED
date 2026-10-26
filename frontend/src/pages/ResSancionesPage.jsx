import { useState, useMemo, useRef } from 'react';
import { toast } from 'sonner';
import { safeHttpUrl, safeHttpUrls } from '../lib/safeUrl.js';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, formatDateTime, formatCurrency } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Textarea } from '../components/ui/Form.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { useAuth } from '../lib/AuthContext.jsx';
import {
  ShieldCheck,
  Clock,
  CheckCircle2,
  XCircle,
  Search,
  Eye,
  Send,
  ExternalLink,
  Scale,
  Receipt,
} from 'lucide-react';

const ESTADOS_CONFIG = {
  NOTIFICADA: {
    label: 'Pliego Notificado',
    sublabel: 'Pendiente de Descargos',
    badgeVariant: 'warning',
    colorClass: 'text-tint-amber-fg',
    bgClass: 'bg-tint-amber-bg border-tint-amber-fg/25',
    icon: Clock,
  },
  EN_DESCARGOS: {
    label: 'En descargos',
    sublabel: 'En Revisión por Comité/Admin',
    badgeVariant: 'info',
    colorClass: 'text-tint-blue-fg',
    bgClass: 'bg-tint-blue-bg border-tint-blue-fg/25',
    icon: Scale,
  },
  ABSUELTA: {
    label: 'Absuelta / Exonerada',
    sublabel: 'Sin Responsabilidad',
    badgeVariant: 'success',
    colorClass: 'text-tint-green-fg',
    bgClass: 'bg-tint-green-bg border-tint-green-fg/25',
    icon: CheckCircle2,
  },
  APLICADA: {
    label: 'Sanción en Firme',
    sublabel: 'Sanción Ratificada',
    badgeVariant: 'destructive',
    colorClass: 'text-tint-red-fg',
    bgClass: 'bg-tint-red-bg border-tint-red-fg/25',
    icon: XCircle,
  },
  SANCION_APLICADA: {
    label: 'Sanción en Firme',
    sublabel: 'Sanción Ratificada',
    badgeVariant: 'destructive',
    colorClass: 'text-tint-red-fg',
    bgClass: 'bg-tint-red-bg border-tint-red-fg/25',
    icon: XCircle,
  },
  ARCHIVADA: {
    label: 'Archivada',
    sublabel: 'Proceso Cerrado',
    badgeVariant: 'secondary',
    colorClass: 'text-text-secondary',
    bgClass: 'bg-surface-dim border-border',
    icon: ShieldCheck,
  },
};

const GRAVEDAD_CONFIG = {
  LEVE: {
    label: 'Leve',
    badgeVariant: 'secondary',
    borderClass: 'border-border',
  },
  GRAVE: {
    label: 'Grave',
    badgeVariant: 'warning',
    borderClass: 'border-tint-amber-fg',
  },
  GRAVISIMA: {
    label: 'Gravísima',
    badgeVariant: 'destructive',
    borderClass: 'border-tint-red-fg',
  },
};

const MULTAS_ESTADO_CONFIG = {
  IMPUESTA: {
    label: 'Impuesta',
    badgeVariant: 'warning',
  },
  EN_DESCARGOS: {
    label: 'En descargos',
    badgeVariant: 'info',
  },
  RATIFICADA: {
    label: 'Ratificada',
    badgeVariant: 'destructive',
  },
  CONDONADA: {
    label: 'Condonada',
    badgeVariant: 'secondary',
  },
  PAGADA: {
    label: 'Pagada',
    badgeVariant: 'success',
  },
  ANULADA: {
    label: 'Anulada',
    badgeVariant: 'secondary',
  },
  PENDIENTE: {
    label: 'Impuesta',
    badgeVariant: 'warning',
  },
};

export default function ResSancionesPage() {
  // Household members see only the cases issued to them and have no financial actions.
  const { isConviviente } = useAuth();
  const searchParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.search) : null;
  const initialTab = searchParams?.get('tab') === 'multas' ? 'multas' : 'sanciones';
  const [mainTab, setMainTab] = useState(initialTab);

  const { data, loading, error, refetch } = useFetch(() => api.get('/sanciones/mis-sanciones'));
  const { data: multasData, loading: multasLoading, error: multasError, refetch: refetchMultas } = useFetch(() => api.get('/multas/mis-multas'));

  const [statusFilter, setStatusFilter] = useState('TODOS');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedSancion, setSelectedSancion] = useState(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);

  // Multas state
  const [multaStatusFilter, setMultaStatusFilter] = useState('TODOS');
  const [multaSearchTerm, setMultaSearchTerm] = useState('');
  const [selectedMulta, setSelectedMulta] = useState(null);
  const [multaModalOpen, setMultaModalOpen] = useState(false);

  // Modal para presentar descargos
  const [descargosModalOpen, setDescargosModalOpen] = useState(false);
  const [descargosTexto, setDescargosTexto] = useState('');
  const [descargosError, setDescargosError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);

  const items = useMemo(() => {
    return Array.isArray(data) ? data : data?.items || [];
  }, [data]);

  const multasItems = useMemo(() => {
    return Array.isArray(multasData) ? multasData : multasData?.items || [];
  }, [multasData]);

  // Executive KPIs Sanciones
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

  // Executive KPIs Multas
  const multasStats = useMemo(() => {
    const total = multasItems.length;
    const pendientes = multasItems.filter(
      (m) => m.estado === 'IMPUESTA' || m.estado === 'RATIFICADA' || m.estado === 'PENDIENTE'
    ).length;
    const pagadas = multasItems.filter((m) => m.estado === 'PAGADA').length;
    const totalSaldo = multasItems
      .filter((m) => m.estado === 'IMPUESTA' || m.estado === 'RATIFICADA' || m.estado === 'PENDIENTE')
      .reduce((acc, m) => acc + (Number(m.monto) || 0), 0);

    return { total, pendientes, pagadas, totalSaldo };
  }, [multasItems]);

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

  const filteredMultas = useMemo(() => {
    return multasItems.filter((m) => {
      if (multaStatusFilter !== 'TODOS' && m.estado !== multaStatusFilter) {
        return false;
      }
      if (!multaSearchTerm.trim()) return true;
      const q = multaSearchTerm.toLowerCase();
      const apto = (m.numeroApartamento || '').toLowerCase();
      const motivo = (m.motivo || '').toLowerCase();
      const desc = (m.descripcion || '').toLowerCase();
      const tipo = (m.tipo || '').toLowerCase();
      const id = String(m.idMulta);
      const sancId = m.idSancionOrigen ? String(m.idSancionOrigen) : '';
      return (
        apto.includes(q) ||
        motivo.includes(q) ||
        desc.includes(q) ||
        tipo.includes(q) ||
        id.includes(q) ||
        sancId.includes(q)
      );
    });
  }, [multasItems, multaStatusFilter, multaSearchTerm]);

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
    <PageContainer className="pb-12">
      <PageHeader
        title={mainTab === 'sanciones' ? 'Mis procesos y sanciones' : 'Mis multas'}
        subtitle={
          mainTab === 'sanciones'
            ? 'Consulta tus expedientes disciplinarios y ejerce tu derecho al debido proceso presentando descargos.'
            : isConviviente
              ? 'Consulta las multas impuestas, su soporte reglamentario y las evidencias.'
              : 'Consulta las multas impuestas, su soporte reglamentario, las evidencias y su estado de pago.'
        }
      />

      {isConviviente && (
        <p className="flex items-start gap-2 rounded-md border border-border bg-surface px-3 py-2.5 text-[13px] text-text-secondary">
          <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-text-muted" aria-hidden="true" />
          Solo ves los expedientes y multas emitidos a tu nombre. Los pagos de la unidad los gestiona el residente titular.
        </p>
      )}

      {/* Selector Principal de Módulo: Sanciones vs Multas */}
      <div className="-mx-1 overflow-x-auto px-1 pb-1">
      <div className="inline-flex items-center gap-1 rounded-lg border border-border bg-surface-container p-1" role="group" aria-label="Tipo de expediente">
        <button
          type="button"
          onClick={() => setMainTab('sanciones')}
          aria-pressed={mainTab === 'sanciones'}
          className={`inline-flex min-h-10 shrink-0 items-center gap-2 rounded-md px-3 text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
            mainTab === 'sanciones'
              ? 'bg-surface text-primary-text shadow-sm ring-1 ring-border'
              : 'text-text-secondary hover:text-on-surface'
          }`}
        >
          <Scale className="h-4 w-4" />
          <span>Procesos disciplinarios <span className="tabular-nums">({items.length})</span></span>
        </button>

        <button
          type="button"
          onClick={() => setMainTab('multas')}
          aria-pressed={mainTab === 'multas'}
          className={`inline-flex min-h-10 shrink-0 items-center gap-2 rounded-md px-3 text-sm font-semibold transition-colors duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
            mainTab === 'multas'
              ? 'bg-surface text-primary-text shadow-sm ring-1 ring-border'
              : 'text-text-secondary hover:text-on-surface'
          }`}
        >
          <Receipt className="h-4 w-4" />
          <span>Multas <span className="tabular-nums">({multasItems.length})</span></span>
        </button>
      </div>
      </div>

      {mainTab === 'sanciones' && (
        <>
          {/* Tarjetas de Métricas / KPIs */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard icon={Scale} value={loading ? '…' : error ? '—' : stats.total} label="Total procesos" variant="primary" />
            <MetricCard
              icon={Clock}
              value={loading ? '…' : error ? '—' : stats.notificada}
              label="Por responder"
              context="Pliegos notificados"
              variant={stats.notificada > 0 ? 'warning' : 'secondary'}
            />
            <MetricCard icon={Search} value={loading ? '…' : error ? '—' : stats.enDescargos} label="En revisión" variant="info" />
            <MetricCard
              icon={CheckCircle2}
              value={loading ? '…' : error ? '—' : stats.absueltaOArchivada}
              label="Absueltas o cerradas"
              variant="success"
            />
          </div>

          {/* Barra de Filtros y Búsqueda */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-surface p-3 rounded-xl border border-border shadow-sm">
            {/* Pestañas de Estado */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0 scrollbar-none">
              {[
                { key: 'TODOS', label: 'Todos' },
                { key: 'NOTIFICADA', label: 'Por responder' },
                { key: 'EN_DESCARGOS', label: 'En revisión' },
                { key: 'APLICADA', label: 'Sanciones aplicadas' },
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
            <div className="relative min-w-0 sm:min-w-[220px]">
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

          {/* Contenido Principal Sanciones */}
          {loading ? (
            <LoadingState message="Consultando expedientes disciplinarios..." />
          ) : error ? (
            <ErrorState
              title="No se pudieron cargar tus expedientes"
              message="Revisa tu conexión e intenta de nuevo."
              onRetry={() => refetch().catch(() => {})}
            />
          ) : filteredItems.length === 0 ? (
            <EmptyState
              icon={ShieldCheck}
              title={
                searchTerm || statusFilter !== 'TODOS'
                  ? 'No se encontraron expedientes con ese filtro'
                  : 'No tienes sanciones ni pliegos activos'
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
                const gravedadCfg = GRAVEDAD_CONFIG[sancion.gravedad] || GRAVEDAD_CONFIG.LEVE;
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
                              {sancion.gravedad || 'Leve'}
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
                            <span>Apertura de pliego:</span>
                            <span className="font-medium text-foreground">{formatDate(sancion.fechaAperturaPliego)}</span>
                          </div>
                        )}
                        {sancion.fechaLimiteDescargos && (
                          <div className="flex items-center justify-between text-[11px] p-1.5 rounded bg-tint-amber-bg border border-tint-amber-fg/25 text-tint-amber-fg">
                            <span className="flex items-center gap-1 font-semibold">
                              <Clock className="h-3 w-3" />
                              <span>Límite descargos:</span>
                            </span>
                            <span className="font-bold">{formatDate(sancion.fechaLimiteDescargos)}</span>
                          </div>
                        )}
                      </div>

                      {/* Sanción Propuesta o Dictamen */}
                      {sancion.tipoSancionPropuesta && (
                        <div className="text-[11px] text-muted-foreground mb-3 flex items-center justify-between p-2 rounded bg-muted/40 border border-border/50">
                          <span>Sanción propuesta:</span>
                          <span className="font-semibold text-foreground">{sancion.tipoSancionPropuesta}</span>
                        </div>
                      )}

                      {/* Dictamen de Administración si aplica */}
                      {sancion.resolucionFinal && (
                        <div className="rounded-lg bg-tint-green-bg border border-tint-green-fg/25 p-2.5 mb-3 text-xs text-tint-green-fg">
                          <div className="flex items-center gap-1.5 font-semibold text-[11px] mb-1">
                            <CheckCircle2 className="h-3.5 w-3.5 text-tint-green-fg" />
                            <span>Resolución de la administración:</span>
                          </div>
                          <p className="text-[11px] leading-relaxed line-clamp-2 text-tint-green-fg">
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
                        <span>Ver expediente</span>
                      </Button>

                      {requiereDescargos && (
                        <Button
                          size="sm"
                          onClick={() => handleOpenDescargos(sancion)}
                          className="h-8 text-xs flex items-center gap-1 font-semibold"
                        >
                          <Send className="h-3.5 w-3.5" />
                          <span>Presentar descargos</span>
                        </Button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}

      {/* Vista de Multas Económicas */}
      {mainTab === 'multas' && (
        <>
          {/* Tarjetas de Métricas Multas */}
          <div className={`grid grid-cols-1 gap-4 sm:grid-cols-2 ${isConviviente ? 'lg:grid-cols-3' : 'lg:grid-cols-4'}`}>
            <MetricCard icon={Receipt} value={multasLoading ? '…' : multasError ? '—' : multasStats.total} label="Total multas" variant="primary" />
            <MetricCard
              icon={Clock}
              value={multasLoading ? '…' : multasError ? '—' : multasStats.pendientes}
              label="Impuestas o en cobro"
              variant={multasStats.pendientes > 0 ? 'warning' : 'secondary'}
            />
            <MetricCard
              icon={CheckCircle2}
              value={multasLoading ? '…' : multasError ? '—' : multasStats.pagadas}
              label="Multas pagadas"
              variant="success"
            />
            {!isConviviente && (
              <MetricCard
                icon={Receipt}
                value={multasLoading ? '…' : multasError ? '—' : formatCurrency(multasStats.totalSaldo)}
                label="Saldo pendiente"
                variant="secondary"
              />
            )}
          </div>

          {/* Barra de Filtros y Búsqueda Multas */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-surface p-3 rounded-xl border border-border shadow-sm">
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0 scrollbar-none">
              {[
                { key: 'TODOS', label: 'Todas' },
                { key: 'IMPUESTA', label: 'Impuestas' },
                { key: 'EN_DESCARGOS', label: 'En descargos' },
                { key: 'RATIFICADA', label: 'Ratificadas' },
                { key: 'PAGADA', label: 'Pagadas' },
                { key: 'ANULADA', label: 'Anuladas' },
              ].map((tab) => {
                const isActive = multaStatusFilter === tab.key;
                return (
                  <button
                    key={tab.key}
                    type="button"
                    onClick={() => setMultaStatusFilter(tab.key)}
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

            <div className="relative min-w-0 sm:min-w-[220px]">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                value={multaSearchTerm}
                onChange={(e) => setMultaSearchTerm(e.target.value)}
                placeholder="Buscar por apartamento, motivo, ID..."
                className="w-full pl-8 pr-3 py-1.5 text-xs rounded-lg border border-input bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring"
              />
            </div>
          </div>

          {/* Contenido Multas */}
          {multasLoading ? (
            <LoadingState message="Consultando multas económicas..." />
          ) : multasError ? (
            <ErrorState
              title="No se pudieron cargar tus multas"
              message="Revisa tu conexión e intenta de nuevo."
              onRetry={() => refetchMultas().catch(() => {})}
            />
          ) : filteredMultas.length === 0 ? (
            <EmptyState
              icon={Receipt}
              title={
                multaSearchTerm || multaStatusFilter !== 'TODOS'
                  ? 'No se encontraron multas con ese filtro'
                  : 'Sin multas económicas pendientes'
              }
              subtitle={
                multaSearchTerm || multaStatusFilter !== 'TODOS'
                  ? 'Intenta restablecer los filtros para ver todas tus multas.'
                  : isConviviente
                    ? 'No registras multas emitidas a tu nombre.'
                    : 'No registras multas impuestas asociadas a tu unidad.'
              }
            />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
              {filteredMultas.map((multa) => {
                const estadoCfg = MULTAS_ESTADO_CONFIG[multa.estado] || MULTAS_ESTADO_CONFIG.IMPUESTA;
                return (
                  <div
                    key={multa.idMulta}
                    className="group relative flex flex-col justify-between rounded-xl border border-border bg-card p-5 shadow-sm transition-all duration-200 hover:shadow-md hover:border-border/80"
                  >
                    <div>
                      {/* Header Multa */}
                      <div className="flex items-start justify-between gap-2 mb-3">
                        <div className="flex items-center gap-2">
                          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary font-bold text-xs font-mono">
                            #{multa.idMulta}
                          </div>
                          <div>
                            <span className="text-xs font-semibold text-foreground block font-mono">
                              {multa.numeroApartamento ? `Apto ${multa.numeroApartamento}` : `Unidad #${multa.idUnidad}`}
                            </span>
                            <span className="text-[10px] text-muted-foreground">
                              {formatDate(multa.fechaCreacion)}
                            </span>
                          </div>
                        </div>

                        <Badge variant={estadoCfg.badgeVariant} className="text-[11px] py-0.5">
                          {estadoCfg.label || multa.estado}
                        </Badge>
                      </div>

                      {/* Motivo */}
                      <div className="mb-2">
                        <h3 className="font-semibold text-foreground text-sm leading-snug line-clamp-2">
                          {multa.motivo || multa.descripcion || multa.tipo || 'Multa Económica'}
                        </h3>
                      </div>

                      {/* Monto Prominente */}
                      <div className="rounded-lg bg-primary/5 border border-primary/20 p-3 mb-3 text-center">
                        <span className="text-[11px] text-muted-foreground block font-medium">Monto de la multa</span>
                        <span className="text-lg font-bold text-primary block mt-0.5">
                          {formatCurrency(multa.monto)}
                        </span>
                      </div>

                      {/* Sanción de Origen */}
                      {multa.idSancionOrigen && (
                        <div className="text-[11px] text-muted-foreground mb-3 flex items-center justify-between p-2 rounded bg-muted/40 border border-border/50">
                          <span>Sanción origen:</span>
                          <span className="font-semibold text-foreground font-mono">Expediente #{multa.idSancionOrigen}</span>
                        </div>
                      )}

                      {/* Evidencias */}
                      {safeHttpUrl(multa.fotoEvidencia || multa.evidenciaUrl) && (
                        <div className="mb-3">
                          <a
                            href={safeHttpUrl(multa.fotoEvidencia || multa.evidenciaUrl)}
                            target="_blank"
                            rel="noreferrer"
                            className="text-xs text-primary hover:underline flex items-center gap-1 font-medium"
                          >
                            <ExternalLink className="h-3 w-3" />
                            <span>Ver soporte de evidencia</span>
                          </a>
                        </div>
                      )}
                    </div>

                    {/* Footer */}
                    <div className="pt-3 border-t border-border flex items-center justify-between gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setSelectedMulta(multa);
                          setMultaModalOpen(true);
                        }}
                        className="h-8 text-xs flex items-center gap-1 w-full justify-center"
                      >
                        <Eye className="h-3.5 w-3.5" />
                        <span>Ver detalle completo</span>
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </>
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
                    <span>Radicar descargos</span>
                  </>
                )}
              </Button>
            </div>
          }
        >
          <form onSubmit={handleSubmitDescargos} className="space-y-4 py-1">
            {/* Banner de Garantía de Debido Proceso */}
            <div className="rounded-lg bg-tint-blue-bg border border-tint-blue-fg/25 p-3 text-xs text-tint-blue-fg flex items-start gap-2.5">
              <Scale className="h-4 w-4 shrink-0 text-tint-blue-fg mt-0.5" />
              <div>
                <p className="font-semibold">Garantía Constitucional y Ley 675 de 2001</p>
                <p className="mt-0.5 text-tint-blue-fg leading-relaxed">
                  Tienes derecho al debido proceso y a la contradicción. Describe tu versión de los hechos, aclaraciones y justificaciones. Este documento será revisado formalmente por el comité de convivencia y la administración.
                </p>
              </div>
            </div>

            {/* Resumen del pliego */}
            <div className="p-3 rounded-lg border border-border bg-muted/20 text-xs space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Falta imputada:</span>
                <span className="font-semibold text-foreground">{selectedSancion.tipoFalta}</span>
              </div>
              {selectedSancion.articuloReglamentoViolado && (
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Artículo violado:</span>
                  <span className="font-medium text-foreground">{selectedSancion.articuloReglamentoViolado}</span>
                </div>
              )}
              {selectedSancion.fechaLimiteDescargos && (
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Fecha límite:</span>
                  <span className="font-bold text-tint-amber-fg">
                    {formatDate(selectedSancion.fechaLimiteDescargos)}
                  </span>
                </div>
              )}
              <div className="pt-1 border-t border-border text-muted-foreground">
                <span className="font-medium block text-foreground mb-0.5">Hechos notificados:</span>
                <p className="line-clamp-3">{selectedSancion.descripcionHechos}</p>
              </div>
            </div>

            <Textarea
              label="Tus descargos, aclaraciones y pruebas"
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
                  <span>Presentar descargos</span>
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
                <span className="text-xs text-muted-foreground font-medium block">Estado del proceso</span>
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
              <span className="text-xs text-muted-foreground font-semibold block mb-1">Motivo / falta</span>
              <h4 className="font-semibold text-foreground text-sm mb-1">{selectedSancion.tipoFalta}</h4>
              {selectedSancion.articuloReglamentoViolado && (
                <span className="text-xs text-muted-foreground">
                  Referencia reglamentaria: {selectedSancion.articuloReglamentoViolado}
                </span>
              )}
            </div>

            {/* Hechos */}
            <div>
              <span className="text-xs text-muted-foreground font-semibold block mb-1">Hechos imputados</span>
              <div className="p-3 rounded-lg border border-border bg-background text-xs leading-relaxed text-foreground">
                {selectedSancion.descripcionHechos}
              </div>
            </div>

            {/* Fechas */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Fecha de notificación</span>
                <span className="font-medium text-foreground">
                  {formatDateTime(selectedSancion.fechaAperturaPliego)}
                </span>
              </div>
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Fecha límite para descargos</span>
                <span className="font-medium text-tint-amber-fg">
                  {formatDate(selectedSancion.fechaLimiteDescargos)}
                </span>
              </div>
            </div>

            {/* Sanción Propuesta */}
            {selectedSancion.tipoSancionPropuesta && (
              <div className="p-2.5 rounded-lg border border-border bg-muted/20 text-xs flex items-center justify-between">
                <span className="text-muted-foreground">Sanción o medida propuesta:</span>
                <span className="font-semibold text-foreground">{selectedSancion.tipoSancionPropuesta}</span>
              </div>
            )}

            {/* Evidencias si existen */}
            {selectedSancion.evidenciasUrls && (
              <div className="p-2.5 rounded-lg border border-border bg-card text-xs flex items-center justify-between">
                <span className="text-muted-foreground">Anexos / evidencias:</span>
                {safeHttpUrls(selectedSancion.evidenciasUrls).length > 0 ? (
                  <span className="flex flex-col items-end gap-1">
                    {safeHttpUrls(selectedSancion.evidenciasUrls).map((href, i) => (
                      <a
                        key={href}
                        href={href}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="font-medium text-primary hover:underline flex items-center gap-1"
                      >
                        <span>Ver evidencia adjunta {i + 1}</span>
                        <ExternalLink className="h-3 w-3" />
                      </a>
                    ))}
                  </span>
                ) : (
                  <span className="text-muted-foreground">Adjunto no enlazable</span>
                )}
              </div>
            )}

            {/* Dictamen / Resolución Final */}
            {selectedSancion.resolucionFinal && (
              <div className="p-3.5 rounded-lg border border-tint-green-fg/25 bg-tint-green-bg text-xs text-tint-green-fg space-y-1.5">
                <div className="flex items-center gap-1.5 font-semibold text-tint-green-fg">
                  <CheckCircle2 className="h-4 w-4 text-tint-green-fg" />
                  <span>Resolución y fallo de la administración</span>
                </div>
                {selectedSancion.fechaResolucion && (
                  <span className="text-[11px] text-tint-green-fg block">
                    Emitida el: {formatDateTime(selectedSancion.fechaResolucion)}
                  </span>
                )}
                <p className="leading-relaxed bg-background/80 p-2.5 rounded border border-tint-green-fg/25">
                  {selectedSancion.resolucionFinal}
                </p>
              </div>
            )}
          </div>
        </Modal>
      )}

      {/* Modal: Detalle de Multa Económica */}
      {selectedMulta && (
        <Modal
          open={multaModalOpen}
          onClose={() => setMultaModalOpen(false)}
          title={`Multa Económica #${selectedMulta.idMulta}`}
          size="md"
          footer={
            <div className="flex items-center justify-end w-full">
              <Button variant="outline" onClick={() => setMultaModalOpen(false)}>
                Cerrar
              </Button>
            </div>
          }
        >
          <div className="space-y-4 py-2 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-2 p-3 rounded-lg bg-muted/30 border border-border">
              <div>
                <span className="text-xs text-muted-foreground font-medium block">Estado de la multa</span>
                <span className="font-semibold text-foreground">
                  {MULTAS_ESTADO_CONFIG[selectedMulta.estado]?.label || selectedMulta.estado}
                </span>
              </div>
              <Badge variant={MULTAS_ESTADO_CONFIG[selectedMulta.estado]?.badgeVariant || 'default'}>
                {selectedMulta.estado}
              </Badge>
            </div>

            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Unidad / inmueble</span>
                <span className="font-semibold text-foreground text-sm">
                  {selectedMulta.numeroApartamento ? `Apto ${selectedMulta.numeroApartamento}` : `Unidad #${selectedMulta.idUnidad}`}
                </span>
              </div>
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Valor de la multa</span>
                <span className="font-bold text-foreground text-sm text-primary">
                  {formatCurrency(selectedMulta.monto)}
                </span>
              </div>
            </div>

            {selectedMulta.motivo && (
              <div>
                <span className="text-xs text-muted-foreground font-semibold block mb-1">Motivo</span>
                <p className="p-2.5 rounded-lg border border-border bg-background text-xs font-medium text-foreground">
                  {selectedMulta.motivo}
                </p>
              </div>
            )}

            {selectedMulta.descripcion && (
              <div>
                <span className="text-xs text-muted-foreground font-semibold block mb-1">Descripción de los hechos</span>
                <p className="p-2.5 rounded-lg border border-border bg-background text-xs text-muted-foreground leading-relaxed">
                  {selectedMulta.descripcion}
                </p>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Fecha de imposición</span>
                <span className="font-medium text-foreground">{formatDate(selectedMulta.fechaCreacion)}</span>
              </div>
              {selectedMulta.idSancionOrigen && (
                <div className="p-2.5 rounded-lg border border-border bg-card">
                  <span className="text-muted-foreground block text-[11px]">Expediente disciplinario origen</span>
                  <span className="font-medium text-foreground">#{selectedMulta.idSancionOrigen}</span>
                </div>
              )}
            </div>

            {safeHttpUrl(selectedMulta.fotoEvidencia || selectedMulta.evidenciaUrl) && (
              <div className="p-2.5 rounded-lg border border-border bg-card text-xs flex items-center justify-between">
                <span className="text-muted-foreground">Evidencia adjunta:</span>
                <a
                  href={safeHttpUrl(selectedMulta.fotoEvidencia || selectedMulta.evidenciaUrl)}
                  target="_blank"
                  rel="noreferrer"
                  className="font-medium text-primary hover:underline flex items-center gap-1"
                >
                  <span>Ver soporte de evidencia</span>
                  <ExternalLink className="h-3 w-3" />
                </a>
              </div>
            )}
          </div>
        </Modal>
      )}
    </PageContainer>
  );
}
