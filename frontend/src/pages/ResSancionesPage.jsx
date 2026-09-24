import { useState, useMemo, useRef } from 'react';
import { toast } from 'sonner';
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
  Receipt,
  DollarSign,
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
  GRAVE: {
    label: 'Grave',
    badgeVariant: 'warning',
    borderClass: 'border-amber-400 dark:border-amber-600',
  },
  GRAVISIMA: {
    label: 'Gravísima',
    badgeVariant: 'destructive',
    borderClass: 'border-rose-400 dark:border-rose-600',
  },
};

const MULTAS_ESTADO_CONFIG = {
  IMPUESTA: {
    label: 'Impuesta',
    badgeVariant: 'warning',
  },
  EN_DESCARGOS: {
    label: 'En Descargos',
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
    <div className="space-y-6 pb-12 animate-saed-fade">
      {/* Encabezado */}
      <PageHeader
        title={mainTab === 'sanciones' ? 'Mis Procesos & Sanciones' : 'Mis Multas Económicas'}
        subtitle={
          mainTab === 'sanciones'
            ? 'Consulta tus expedientes disciplinarios, requerimientos normativos y ejerce tu derecho al debido proceso y descargos'
            : 'Consulta las multas económicas impuestas, soporte reglamentario, evidencias y estado de pago'
        }
      />

      {/* Selector Principal de Módulo: Sanciones vs Multas */}
      <div className="flex items-center gap-2 border-b border-border pb-3">
        <button
          type="button"
          onClick={() => setMainTab('sanciones')}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs sm:text-sm font-semibold transition-all ${
            mainTab === 'sanciones'
              ? 'bg-primary text-primary-foreground shadow-sm'
              : 'bg-muted/50 text-muted-foreground hover:bg-muted hover:text-foreground'
          }`}
        >
          <Scale className="h-4 w-4" />
          <span>Procesos Disciplinarios ({items.length})</span>
        </button>

        <button
          type="button"
          onClick={() => setMainTab('multas')}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg text-xs sm:text-sm font-semibold transition-all ${
            mainTab === 'multas'
              ? 'bg-primary text-primary-foreground shadow-sm'
              : 'bg-muted/50 text-muted-foreground hover:bg-muted hover:text-foreground'
          }`}
        >
          <Receipt className="h-4 w-4" />
          <span>Multas Económicas ({multasItems.length})</span>
        </button>
      </div>

      {mainTab === 'sanciones' && (
        <>
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

          {/* Contenido Principal Sanciones */}
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
        </>
      )}

      {/* Vista de Multas Económicas */}
      {mainTab === 'multas' && (
        <>
          {/* Tarjetas de Métricas Multas */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <StatCard
              icon="receipt_long"
              value={multasStats.total}
              label="Total Multas"
              color="primary"
            />
            <StatCard
              icon="pending_actions"
              value={multasStats.pendientes}
              label="Impuestas / En Cobro"
              color="amber"
            />
            <StatCard
              icon="verified"
              value={multasStats.pagadas}
              label="Multas Pagadas"
              color="green"
            />
            <StatCard
              icon="payments"
              value={formatCurrency(multasStats.totalSaldo)}
              label="Total Saldo Pendiente"
              color="primary"
            />
          </div>

          {/* Barra de Filtros y Búsqueda Multas */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-surface p-3 rounded-xl border border-border shadow-sm">
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0 scrollbar-none">
              {[
                { key: 'TODOS', label: 'Todas' },
                { key: 'IMPUESTA', label: 'Impuestas' },
                { key: 'EN_DESCARGOS', label: 'En Descargos' },
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

            <div className="relative min-w-[220px]">
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
            <div className="rounded-xl border border-rose-200 dark:border-rose-900/60 bg-rose-50/50 dark:bg-rose-950/20 p-6 text-center">
              <AlertTriangle className="mx-auto h-8 w-8 text-rose-500 mb-2" />
              <h4 className="font-semibold text-rose-700 dark:text-rose-400">Error al cargar multas</h4>
              <p className="text-xs text-muted-foreground mt-1">{multasError.message || 'Error de comunicación'}</p>
              <Button variant="outline" size="sm" onClick={() => refetchMultas()} className="mt-4">
                Reintentar
              </Button>
            </div>
          ) : filteredMultas.length === 0 ? (
            <EmptyState
              icon={<Receipt className="h-10 w-10 text-emerald-600 dark:text-emerald-400" />}
              title={
                multaSearchTerm || multaStatusFilter !== 'TODOS'
                  ? 'No se encontraron multas con ese filtro'
                  : 'Sin multas económicas pendientes'
              }
              subtitle={
                multaSearchTerm || multaStatusFilter !== 'TODOS'
                  ? 'Intenta restablecer los filtros para ver todas tus multas.'
                  : 'No registras multas económicas impuestas asociadas a tu unidad.'
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
                        <span className="text-[11px] text-muted-foreground block font-medium">Monto de la Multa</span>
                        <span className="text-lg font-bold text-primary block mt-0.5">
                          {formatCurrency(multa.monto)}
                        </span>
                      </div>

                      {/* Sanción de Origen */}
                      {multa.idSancionOrigen && (
                        <div className="text-[11px] text-muted-foreground mb-3 flex items-center justify-between p-2 rounded bg-muted/40 border border-border/50">
                          <span>Sanción Origen:</span>
                          <span className="font-semibold text-foreground font-mono">Expediente #{multa.idSancionOrigen}</span>
                        </div>
                      )}

                      {/* Evidencias */}
                      {(multa.fotoEvidencia || multa.evidenciaUrl) && (
                        <div className="mb-3">
                          <a
                            href={multa.fotoEvidencia || multa.evidenciaUrl}
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
                        <span>Ver Detalle Completo</span>
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
                <span className="text-xs text-muted-foreground font-medium block">Estado de la Multa</span>
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
                <span className="text-muted-foreground block text-[11px]">Unidad / Inmueble</span>
                <span className="font-semibold text-foreground text-sm">
                  {selectedMulta.numeroApartamento ? `Apto ${selectedMulta.numeroApartamento}` : `Unidad #${selectedMulta.idUnidad}`}
                </span>
              </div>
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Valor de la Multa</span>
                <span className="font-bold text-foreground text-sm text-primary">
                  {formatCurrency(selectedMulta.monto)}
                </span>
              </div>
            </div>

            {selectedMulta.motivo && (
              <div>
                <span className="text-xs text-muted-foreground font-semibold uppercase block mb-1">Motivo</span>
                <p className="p-2.5 rounded-lg border border-border bg-background text-xs font-medium text-foreground">
                  {selectedMulta.motivo}
                </p>
              </div>
            )}

            {selectedMulta.descripcion && (
              <div>
                <span className="text-xs text-muted-foreground font-semibold uppercase block mb-1">Descripción de los Hechos</span>
                <p className="p-2.5 rounded-lg border border-border bg-background text-xs text-muted-foreground leading-relaxed">
                  {selectedMulta.descripcion}
                </p>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block text-[11px]">Fecha de Imposición</span>
                <span className="font-medium text-foreground">{formatDate(selectedMulta.fechaCreacion)}</span>
              </div>
              {selectedMulta.idSancionOrigen && (
                <div className="p-2.5 rounded-lg border border-border bg-card">
                  <span className="text-muted-foreground block text-[11px]">Expediente Disciplinario Origen</span>
                  <span className="font-medium text-foreground">#{selectedMulta.idSancionOrigen}</span>
                </div>
              )}
            </div>

            {(selectedMulta.fotoEvidencia || selectedMulta.evidenciaUrl) && (
              <div className="p-2.5 rounded-lg border border-border bg-card text-xs flex items-center justify-between">
                <span className="text-muted-foreground">Evidencia Adjunta:</span>
                <a
                  href={selectedMulta.fotoEvidencia || selectedMulta.evidenciaUrl}
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
    </div>
  );
}
