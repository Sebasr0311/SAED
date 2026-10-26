import { useState, useMemo, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import {
  Calendar,
  Clock,
  Users,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Sparkles,
  Plus,
  Search,
  Eye,
  Building,
  UtensilsCrossed,
  Trophy,
  Waves,
  Dumbbell,
  ShieldCheck,
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
import { useFetch } from '../lib/hooks.js';
import { formatDate, formatCurrency } from '../lib/utils.js';

const ESTADO_BADGE = {
  APROBADA: {
    label: 'Aprobada',
    badgeVariant: 'success',
    colorClass: 'text-tint-green-fg',
    bgClass: 'bg-tint-green-bg border-tint-green-fg/25',
    icon: CheckCircle2,
  },
  PENDIENTE: {
    label: 'Pendiente de Aprobación',
    badgeVariant: 'warning',
    colorClass: 'text-tint-amber-fg',
    bgClass: 'bg-tint-amber-bg border-tint-amber-fg/25',
    icon: Clock,
  },
  RECHAZADA: {
    label: 'Rechazada',
    badgeVariant: 'destructive',
    colorClass: 'text-tint-red-fg',
    bgClass: 'bg-tint-red-bg border-tint-red-fg/25',
    icon: XCircle,
  },
  CANCELADA: {
    label: 'Cancelada',
    badgeVariant: 'secondary',
    colorClass: 'text-text-secondary',
    bgClass: 'bg-surface-dim border-border',
    icon: AlertCircle,
  },
  COMPLETADA: {
    label: 'Completada',
    badgeVariant: 'secondary',
    colorClass: 'text-tint-blue-fg',
    bgClass: 'bg-tint-blue-bg border-tint-blue-fg/25',
    icon: CheckCircle2,
  },
};

const emptyForm = {
  idZona: '',
  fechaReserva: '',
  horaInicio: '14:00',
  horaFin: '18:00',
  cantidadAsistentes: 1,
  observaciones: '',
};

function getZonaIcon(nombre) {
  const n = (nombre || '').toLowerCase();
  if (n.includes('bbq') || n.includes('parrilla') || n.includes('asador')) return UtensilsCrossed;
  if (n.includes('piscina') || n.includes('acuatic') || n.includes('jacuzzi')) return Waves;
  if (n.includes('gimnasio') || n.includes('gym') || n.includes('fitness')) return Dumbbell;
  if (n.includes('cancha') || n.includes('futbol') || n.includes('squash') || n.includes('tenis')) return Trophy;
  if (n.includes('social') || n.includes('salon') || n.includes('eventos') || n.includes('comunal')) return Users;
  return Sparkles;
}

export default function ResReservasPage() {
  const navigate = useNavigate();
  const { user, isConviviente } = useAuth();
  const [form, setForm] = useState(emptyForm);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalDetalle, setModalDetalle] = useState(null);
  const [reservaACancelar, setReservaACancelar] = useState(null);
  const [cancelando, setCancelando] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [errors, setErrors] = useState({});

  const [tabFiltro, setTabFiltro] = useState('TODAS'); // 'TODAS' | 'APROBADAS' | 'PENDIENTES' | 'HISTORIAL'
  const [searchTerm, setSearchTerm] = useState('');

  // 1. Zonas comunes disponibles
  const { data: zonasData, loading: loadingZonas, error: errorZonas, refetch: refetchZonas } = useFetch(
    () => api.get('/zonas-comunes'),
    []
  );
  const zonas = useMemo(() => {
    const list = Array.isArray(zonasData) ? zonasData : zonasData?.items || [];
    return Array.isArray(list) ? list : [];
  }, [zonasData]);

  // 2. Mis reservas
  const {
    data: reservasData,
    loading: loadingReservas,
    error: errorReservas,
    refetch,
  } = useFetch(() => api.get('/reservas/mis-reservas'), [user]);

  const reservas = useMemo(() => {
    const list = Array.isArray(reservasData) ? reservasData : reservasData?.items || [];
    return Array.isArray(list) ? list : [];
  }, [reservasData]);

  // 3. Estado financiero de la unidad (GAP-F8-08 Banner Preventivo de Mora).
  // RESIDENTE_CONVIVENCIA has no financial access: skip the call and never show the arrears notice.
  const {
    data: estadoFinancieroData,
    loading: loadingEstadoFinanciero,
  } = useFetch(
    () => (isConviviente ? Promise.resolve(null) : api.get('/paz-y-salvos/mi-estado-financiero')),
    [user, isConviviente]
  );

  const estadoFinanciero = useMemo(() => {
    return estadoFinancieroData?.data || estadoFinancieroData || null;
  }, [estadoFinancieroData]);

  const enMora = useMemo(() => {
    if (isConviviente || !estadoFinanciero) return false;
    return Boolean(
      estadoFinanciero.enMora === true ||
      estadoFinanciero.pazYSalvo === false ||
      (estadoFinanciero.saldoTotalExigible != null && Number(estadoFinanciero.saldoTotalExigible) > 0)
    );
  }, [estadoFinanciero, isConviviente]);

  // Métricas
  const stats = useMemo(() => {
    const total = reservas.length;
    const pendientes = reservas.filter((r) => r.estado === 'PENDIENTE').length;
    const aprobadas = reservas.filter((r) => r.estado === 'APROBADA').length;

    // Próxima reserva aprobada
    const proxima = [...reservas]
      .filter((r) => r.estado === 'APROBADA' && new Date(r.fechaReserva) >= new Date(new Date().setHours(0, 0, 0, 0)))
      .sort((a, b) => new Date(a.fechaReserva) - new Date(b.fechaReserva))[0];

    return {
      total,
      pendientes,
      aprobadas,
      proxima,
      totalZonas: zonas.length,
    };
  }, [reservas, zonas]);

  // Filtrado de reservas
  const filteredReservas = useMemo(() => {
    return reservas.filter((r) => {
      if (tabFiltro === 'PENDIENTES' && r.estado !== 'PENDIENTE') return false;
      if (tabFiltro === 'APROBADAS' && r.estado !== 'APROBADA') return false;
      if (tabFiltro === 'HISTORIAL' && r.estado !== 'RECHAZADA' && r.estado !== 'CANCELADA' && r.estado !== 'COMPLETADA') return false;

      if (searchTerm.trim()) {
        const q = searchTerm.toLowerCase().trim();
        const zNom = (r.nombreZona || '').toLowerCase();
        const obs = (r.observaciones || '').toLowerCase();
        const f = (r.fechaReserva || '').toLowerCase();
        return zNom.includes(q) || obs.includes(q) || f.includes(q);
      }
      return true;
    });
  }, [reservas, tabFiltro, searchTerm]);

  function abrirReservaZona(zonaId) {
    if (enMora) {
      toast.warning('Tu unidad presenta saldo pendiente en mora. La reserva requerirá paz y salvo para ser aprobada.');
    }
    setForm({
      ...emptyForm,
      idZona: zonaId ? String(zonaId) : '',
      fechaReserva: new Date().toISOString().slice(0, 10),
    });
    setErrors({});
    setModalOpen(true);
  }

  function validate() {
    const e = {};
    if (!form.idZona) e.idZona = 'Selecciona el espacio o zona común a reservar.';
    if (!form.fechaReserva) {
      e.fechaReserva = 'Selecciona la fecha para la reserva.';
    } else {
      const hoy = new Date();
      hoy.setHours(0, 0, 0, 0);
      const seleccionada = new Date(form.fechaReserva + 'T00:00:00');
      if (seleccionada < hoy) {
        e.fechaReserva = 'La fecha de reserva no puede ser anterior al día de hoy.';
      }
    }
    if (!form.horaInicio) e.horaInicio = 'Indica la hora de inicio.';
    if (!form.horaFin) e.horaFin = 'Indica la hora de finalización.';
    if (form.horaInicio && form.horaFin && form.horaInicio >= form.horaFin) {
      e.horaFin = 'La hora de finalización debe ser posterior a la de inicio.';
    }

    const selectedZone = zonas.find((z) => String(z.idZona) === String(form.idZona));
    if (selectedZone && selectedZone.aforoMaximo) {
      if (Number(form.cantidadAsistentes) > Number(selectedZone.aforoMaximo)) {
        e.cantidadAsistentes = `El aforo máximo para este espacio es de ${selectedZone.aforoMaximo} personas.`;
      }
    }
    if (!form.cantidadAsistentes || Number(form.cantidadAsistentes) < 1) {
      e.cantidadAsistentes = 'Debes ingresar al menos 1 asistente.';
    }

    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!validate()) return;
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const payload = {
        idZona: Number(form.idZona),
        fechaReserva: form.fechaReserva,
        horaInicio: form.horaInicio,
        horaFin: form.horaFin,
        cantidadAsistentes: Number(form.cantidadAsistentes),
        observaciones: form.observaciones.trim(),
      };
      await api.post('/reservas', payload);
      toast.success('¡Solicitud de reserva radicada con éxito! Queda pendiente de aprobación administrativa.');
      setModalOpen(false);
      setForm(emptyForm);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al radicar la solicitud de reserva');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  async function handleConfirmarCancelacion() {
    if (!reservaACancelar) return;
    setCancelando(true);
    try {
      await api.put(`/reservas/${reservaACancelar.idReserva}/cancelar`);
      toast.success('Reserva cancelada exitosamente.');
      setReservaACancelar(null);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al cancelar la reserva');
    } finally {
      setCancelando(false);
    }
  }

  const zonaSeleccionadaEnModal = useMemo(() => {
    return zonas.find((z) => String(z.idZona) === String(form.idZona));
  }, [zonas, form.idZona]);

  return (
    <PageContainer>
      <PageHeader
        title="Zonas comunes y reservas"
        subtitle="Consulta aforo y normas de uso, y agenda los espacios sociales de la copropiedad."
        action={
          <Button onClick={() => abrirReservaZona('')} className="min-h-10">
            <Plus className="h-4 w-4" aria-hidden="true" />
            Nueva reserva
          </Button>
        }
      />

      {/* Banner Preventivo de Mora Financiera (GAP-F8-08) */}
      {!loadingEstadoFinanciero && enMora && estadoFinanciero && (
        <div
          data-testid="mora-preventive-banner"
          className="flex flex-col items-start justify-between gap-4 rounded-lg border border-tint-red-fg/25 bg-tint-red-bg p-4 md:flex-row md:items-center"
        >
          <div className="flex items-start gap-3">
            <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-tint-red-fg" aria-hidden="true" />
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <h2 className="text-sm font-bold text-on-surface">
                  Unidad con obligaciones financieras pendientes
                </h2>
                <Badge variant="destructive" className="text-[10px] px-1.5 py-0">
                  En mora
                </Badge>
              </div>
              <p className="text-xs leading-relaxed text-text-secondary">
                Tu unidad {estadoFinanciero.identificadorUnidad ? `(${estadoFinanciero.identificadorUnidad})` : ''} presenta un saldo pendiente
                {/* Convivencia receives only the arrears flag (F5-H03): the amount is shown when the API provides it. */}
                {estadoFinanciero.saldoTotalExigible != null && (
                  <>
                    {' '}de{' '}
                    <strong className="font-semibold tabular-nums text-on-surface">
                      {formatCurrency(estadoFinanciero.saldoTotalExigible)}
                    </strong>
                  </>
                )}
                . Conforme al reglamento de propiedad horizontal, la aprobación y confirmación de nuevas reservas de zonas comunes se encuentra restringida hasta normalizar el estado de cartera.
              </p>
              {Array.isArray(estadoFinanciero.motivosBloqueo) && estadoFinanciero.motivosBloqueo.length > 0 && (
                <ul className="list-inside list-disc space-y-0.5 pt-1 text-xs text-text-secondary">
                  {estadoFinanciero.motivosBloqueo.map((motivo, idx) => (
                    <li key={idx}>{motivo}</li>
                  ))}
                </ul>
              )}
            </div>
          </div>
          <Button
            variant="destructive"
            size="sm"
            className="min-h-10 shrink-0 self-end md:self-center"
            onClick={() => navigate('/res-cuotas')}
          >
            Ir a mis cuotas
          </Button>
        </div>
      )}

      {/* 1. KPIs Ejecutivos */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="Zonas disponibles"
          value={loadingZonas ? '…' : errorZonas ? '—' : String(stats.totalZonas)}
          icon={Building}
          variant="primary"
          context="Espacios comunitarios"
        />
        <MetricCard
          label="Reservas aprobadas"
          value={loadingReservas ? '…' : String(stats.aprobadas)}
          icon={CheckCircle2}
          variant="success"
          context="Confirmadas por la administración"
        />
        <MetricCard
          label="En revisión"
          value={loadingReservas ? '…' : String(stats.pendientes)}
          icon={Clock}
          variant={stats.pendientes > 0 ? 'warning' : 'primary'}
          context={stats.pendientes > 0 ? 'Pendiente administración' : 'Sin trámites pendientes'}
        />
        <MetricCard
          label="Próxima reserva"
          value={stats.proxima ? formatDate(stats.proxima.fechaReserva) : 'Ninguna'}
          icon={Calendar}
          variant="primary"
          context={stats.proxima ? `${stats.proxima.nombreZona} (${stats.proxima.horaInicio})` : 'Sin fechas agendadas'}
        />
      </div>

      {/* 2. Catálogo Visual de Espacios Comunes (Amenities) */}
      <section aria-labelledby="zonas-title">
        <div className="mb-3.5 flex flex-wrap items-end justify-between gap-2">
          <div>
            <h2 id="zonas-title" className="text-base font-bold text-on-surface">Espacios de la copropiedad</h2>
            <p className="text-xs text-muted-foreground">
              Elige un espacio para agendar tu fecha
            </p>
          </div>
          <Badge variant="outline" className="tabular-nums">
            {zonas.length} espacios activos
          </Badge>
        </div>

        {loadingZonas ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="app-skeleton h-40 rounded-lg" aria-hidden="true" />
            ))}
          </div>
        ) : errorZonas ? (
          <ErrorState
            compact
            title="No se pudieron cargar las zonas comunes"
            message="Revisa tu conexión e intenta de nuevo."
            onRetry={() => refetchZonas().catch(() => {})}
          />
        ) : zonas.length === 0 ? (
          <EmptyState
            icon={Building}
            title="Aún no hay zonas comunes configuradas"
            subtitle="Cuando la administración habilite espacios reservables, aparecerán aquí."
          />
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {zonas.map((z) => {
              const Icon = getZonaIcon(z.nombre);
              const costo = z.costoReserva != null && Number(z.costoReserva) > 0;

              return (
                <div
                  key={z.idZona}
                  className="flex flex-col justify-between rounded-lg border border-border bg-surface p-5 shadow-sm transition-colors duration-150 hover:border-primary/50"
                >
                  <div>
                    <div className="flex items-start justify-between gap-3 mb-3">
                      <div className="rounded-md bg-tint-purple-bg p-2.5 text-tint-purple-fg">
                        <Icon className="w-5 h-5" />
                      </div>
                      <div className="flex flex-col items-end gap-1">
                        <Badge variant="secondary" className="text-xs font-semibold">
                          <Users className="h-3 w-3" aria-hidden="true" />
                          Aforo: {z.aforoMaximo || 'N/A'}
                        </Badge>
                        {costo ? (
                          <span className="text-xs font-bold tabular-nums text-on-surface">
                            {formatCurrency(z.costoReserva)}
                          </span>
                        ) : (
                          <span className="rounded-[3px] bg-tint-green-bg px-2 py-0.5 text-xs font-bold text-tint-green-fg">
                            Uso gratuito
                          </span>
                        )}
                      </div>
                    </div>

                    <h3 className="text-[15px] font-bold text-on-surface">
                      {z.nombre}
                    </h3>
                    {z.tipo && (
                      <p className="text-xs text-muted-foreground mt-0.5">
                        Categoría: {z.tipo}
                      </p>
                    )}
                  </div>

                  <div className="pt-4 mt-2 border-t border-border/60 flex items-center justify-between">
                    <span className="text-xs text-muted-foreground">Requiere reserva previa</span>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => abrirReservaZona(z.idZona)}
                      aria-label={`Reservar ${z.nombre}`}
                      className="min-h-10 sm:min-h-8"
                    >
                      Reservar
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* 3. Mis Reservas Solicitadas */}
      <div className="space-y-4">
        <div className="flex flex-col items-stretch justify-between gap-3 sm:flex-row sm:items-center">
          <div className="flex gap-2 items-center flex-wrap w-full sm:w-auto">
            {[
              { id: 'TODAS', label: `Todas (${stats.total})` },
              { id: 'APROBADAS', label: `Aprobadas (${stats.aprobadas})` },
              { id: 'PENDIENTES', label: `En revisión (${stats.pendientes})` },
              { id: 'HISTORIAL', label: 'Historial' },
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

          <div className="w-full sm:w-72">
            <div className="relative">
              <Search className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Buscar por espacio, fecha u observación..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                aria-label="Buscar reservas"
                className="h-10 w-full rounded-md border border-input bg-surface pl-9 pr-3 text-sm text-on-surface placeholder:text-text-muted focus:border-primary focus:outline-none focus:ring-2 focus:ring-ring/25"
              />
            </div>
          </div>
        </div>

        {loadingReservas ? (
          <div className="rounded-lg border border-border bg-surface shadow-sm">
            <LoadingState message="Consultando tus reservas…" />
          </div>
        ) : errorReservas ? (
          <ErrorState
            title="No se pudieron cargar tus reservas"
            message="Revisa tu conexión e intenta de nuevo."
            onRetry={() => refetch().catch(() => {})}
          />
        ) : filteredReservas.length === 0 ? (
          <EmptyState
            icon={Calendar}
            title="No tienes reservas registradas"
            subtitle={
              tabFiltro === 'PENDIENTES'
                ? 'No tienes solicitudes pendientes de aprobación.'
                : 'Usa «Nueva reserva» o elige un espacio de la lista superior.'
            }
          />
        ) : (
          <div className="space-y-3">
            {filteredReservas.map((r) => {
              const config = ESTADO_BADGE[r.estado] || ESTADO_BADGE.PENDIENTE;
              const Icon = getZonaIcon(r.nombreZona);
              const BadgeIcon = config.icon;

              return (
                <div
                  key={r.idReserva}
                  className="bg-card border border-border rounded-xl p-4 sm:p-5 hover:border-primary/30 transition-all shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4"
                >
                  <div className="flex items-start gap-3.5 min-w-0">
                    <div className="p-3 rounded-xl bg-primary/10 text-primary shrink-0">
                      <Icon className="w-5 h-5" />
                    </div>
                    <div className="space-y-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="text-base font-bold text-foreground truncate">
                          {r.nombreZona || 'Zona Común'}
                        </h3>
                        <Badge variant={config.badgeVariant} className="text-xs">
                          <BadgeIcon className="w-3 h-3 mr-1" />
                          {config.label}
                        </Badge>
                      </div>
                      <div className="flex items-center gap-4 text-xs text-muted-foreground flex-wrap">
                        <span className="flex items-center gap-1 text-foreground font-medium">
                          <Calendar className="w-3.5 h-3.5 text-primary" />
                          {formatDate(r.fechaReserva)}
                        </span>
                        <span className="flex items-center gap-1">
                          <Clock className="w-3.5 h-3.5" />
                          {r.horaInicio} - {r.horaFin}
                        </span>
                        <span className="flex items-center gap-1">
                          <Users className="w-3.5 h-3.5" />
                          {r.cantidadAsistentes} asistente(s)
                        </span>
                        {r.costoTotal && Number(r.costoTotal) > 0 && (
                          <span className="font-semibold text-primary">
                            Costo: {formatCurrency(r.costoTotal)}
                          </span>
                        )}
                      </div>
                      {r.observaciones && (
                        <p className="text-xs text-muted-foreground italic truncate max-w-xl">
                          &quot;{r.observaciones}&quot;
                        </p>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center justify-between md:justify-end gap-2 shrink-0 pt-3 md:pt-0 border-t md:border-t-0 border-border">
                    {(r.estado === 'PENDIENTE' || r.estado === 'APROBADA') && (
                      <Button
                        variant="outline"
                        size="sm"
                        className="text-tint-red-fg hover:text-tint-red-fg hover:bg-tint-red-bg border-tint-red-fg/25"
                        onClick={() => setReservaACancelar(r)}
                      >
                        <XCircle className="w-4 h-4 mr-1.5" />
                        Cancelar
                      </Button>
                    )}
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setModalDetalle(r)}
                    >
                      <Eye className="w-4 h-4 mr-1.5" />
                      Ver ficha
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 4. Modal Nueva Reserva */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Solicitar reserva de espacio común"
        footer={
          <div className="flex items-center justify-end gap-2 w-full">
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={handleSubmit} disabled={saving}>
              {saving ? 'Enviando solicitud…' : 'Radicar Reserva'}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          {enMora && (
            <div className="p-3 rounded-xl bg-tint-red-bg border border-tint-red-fg/25 flex items-start gap-2.5 text-xs text-tint-red-fg">
              <AlertCircle className="w-4 h-4 shrink-0 text-tint-red-fg mt-0.5" />
              <div>
                <strong>Aviso de Cartera:</strong> Tu unidad registra obligaciones pendientes por{' '}
                <strong>{formatCurrency(estadoFinanciero?.saldoTotalExigible || 0)}</strong>. Recuerda que la confirmación de la reserva requiere encontrarse a paz y salvo.
              </div>
            </div>
          )}

          <div className="form-group">
            <Select
              id="idZona"
              label="Espacio comunitario *"
              value={form.idZona}
              onChange={(e) => setForm((f) => ({ ...f, idZona: e.target.value }))}
              error={errors.idZona}
            >
              <option value="">— Seleccione el espacio a reservar —</option>
              {zonas.map((z) => (
                <option key={z.idZona} value={z.idZona}>
                  {z.nombre} (Aforo Máx: {z.aforoMaximo || 'N/A'})
                </option>
              ))}
            </Select>
          </div>

          {zonaSeleccionadaEnModal && (
            <div className="p-3 rounded-xl bg-tint-blue-bg border border-tint-blue-fg/25 flex items-start gap-2.5 text-xs text-tint-blue-fg">
              <Info className="w-4 h-4 shrink-0 text-tint-blue-fg mt-0.5" />
              <div>
                <strong>{zonaSeleccionadaEnModal.nombre}:</strong> Aforo permitido de{' '}
                <strong>{zonaSeleccionadaEnModal.aforoMaximo || 'N/A'} personas</strong>.
                {zonaSeleccionadaEnModal.costoReserva && Number(zonaSeleccionadaEnModal.costoReserva) > 0
                  ? ` Requiere un depósito/canon de ${formatCurrency(zonaSeleccionadaEnModal.costoReserva)}.`
                  : ' Uso sin costo adicional para residentes al día.'}
              </div>
            </div>
          )}

          <div className="form-row">
            <Input
              id="fechaReserva"
              label="Fecha de la reserva *"
              type="date"
              min={new Date().toISOString().slice(0, 10)}
              value={form.fechaReserva}
              onChange={(e) => setForm((f) => ({ ...f, fechaReserva: e.target.value }))}
              error={errors.fechaReserva}
            />
            <Input
              id="cantidadAsistentes"
              label="Cantidad de asistentes *"
              type="number"
              min={1}
              max={zonaSeleccionadaEnModal?.aforoMaximo || 200}
              value={form.cantidadAsistentes}
              onChange={(e) => setForm((f) => ({ ...f, cantidadAsistentes: e.target.value }))}
              error={errors.cantidadAsistentes}
            />
          </div>

          <div className="form-row">
            <Input
              id="horaInicio"
              label="Hora de inicio *"
              type="time"
              value={form.horaInicio}
              onChange={(e) => setForm((f) => ({ ...f, horaInicio: e.target.value }))}
              error={errors.horaInicio}
            />
            <Input
              id="horaFin"
              label="Hora de finalización *"
              type="time"
              value={form.horaFin}
              onChange={(e) => setForm((f) => ({ ...f, horaFin: e.target.value }))}
              error={errors.horaFin}
            />
          </div>

          <div className="form-group">
            <Textarea
              id="observaciones"
              label="Motivo del evento u observaciones (opcional)"
              rows={3}
              placeholder="Ej. Cumpleaños familiar, reunión de estudio o convivencia..."
              value={form.observaciones}
              onChange={(e) => setForm((f) => ({ ...f, observaciones: e.target.value }))}
            />
          </div>

          <div className="text-xs text-muted-foreground flex items-center gap-1.5">
            <ShieldCheck className="w-3.5 h-3.5 text-tint-green-fg" />
            La reserva quedará sujeta al reglamento interno y aprobación de la administración.
          </div>
        </form>
      </Modal>

      {/* 5. Modal Detalle de Reserva */}
      <Modal
        open={!!modalDetalle}
        onClose={() => setModalDetalle(null)}
        title={`Detalle de Reserva #${modalDetalle?.idReserva || ''}`}
        footer={
          <div className="flex items-center justify-between w-full">
            {modalDetalle && (modalDetalle.estado === 'PENDIENTE' || modalDetalle.estado === 'APROBADA') ? (
              <Button
                variant="outline"
                size="sm"
                className="text-tint-red-fg hover:text-tint-red-fg hover:bg-tint-red-bg border-tint-red-fg/25"
                onClick={() => {
                  const r = modalDetalle;
                  setModalDetalle(null);
                  setReservaACancelar(r);
                }}
              >
                <XCircle className="w-4 h-4 mr-1.5" />
                Cancelar reserva
              </Button>
            ) : (
              <div />
            )}
            <Button variant="outline" onClick={() => setModalDetalle(null)}>
              Cerrar ficha
            </Button>
          </div>
        }
      >
        {modalDetalle && (
          <div className="space-y-4 text-sm">
            <div className="grid grid-cols-2 gap-3 p-3.5 rounded-xl bg-muted/50 border border-border">
              <div>
                <div className="text-xs text-muted-foreground font-medium">Espacio</div>
                <div className="text-sm font-bold text-foreground">{modalDetalle.nombreZona}</div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground font-medium">Estado de Solicitud</div>
                <div className="mt-0.5">
                  <Badge variant={ESTADO_BADGE[modalDetalle.estado]?.badgeVariant || 'secondary'}>
                    {ESTADO_BADGE[modalDetalle.estado]?.label || modalDetalle.estado}
                  </Badge>
                </div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground font-medium">Fecha Agendada</div>
                <div className="text-sm font-semibold text-foreground">
                  {formatDate(modalDetalle.fechaReserva)}
                </div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground font-medium">Horario</div>
                <div className="text-sm font-semibold text-foreground">
                  {modalDetalle.horaInicio} - {modalDetalle.horaFin}
                </div>
              </div>
            </div>

            <div className="p-3.5 rounded-xl border border-border space-y-2">
              <div className="text-xs font-bold text-muted-foreground">
                Detalles del evento
              </div>
              <div className="flex justify-between text-xs py-1 border-b border-border/50">
                <span className="text-muted-foreground">Asistentes estimados:</span>
                <span className="font-semibold text-foreground">{modalDetalle.cantidadAsistentes} personas</span>
              </div>
              <div className="flex justify-between text-xs py-1 border-b border-border/50">
                <span className="text-muted-foreground">Fecha de radicación:</span>
                <span className="font-semibold text-foreground">{formatDate(modalDetalle.fechaSolicitud)}</span>
              </div>
              {modalDetalle.costoTotal && Number(modalDetalle.costoTotal) > 0 && (
                <div className="flex justify-between text-xs py-1 border-b border-border/50">
                  <span className="text-muted-foreground">Canon / garantía:</span>
                  <span className="font-bold text-primary">{formatCurrency(modalDetalle.costoTotal)}</span>
                </div>
              )}
              {modalDetalle.observaciones && (
                <div className="pt-2 text-xs">
                  <span className="text-muted-foreground font-semibold">Observaciones:</span>
                  <p className="mt-1 p-2 rounded bg-muted text-foreground italic">
                    {modalDetalle.observaciones}
                  </p>
                </div>
              )}
            </div>

            <div className="p-3 rounded-xl bg-tint-amber-bg border border-tint-amber-fg/25 text-xs text-tint-amber-fg space-y-1">
              <div className="font-bold flex items-center gap-1.5">
                <AlertCircle className="w-4 h-4 text-tint-amber-fg shrink-0" />
                Reglas de convivencia
              </div>
              <p>
                El residente es responsable del cuidado del mobiliario, orden y entrega del espacio en el mismo
                estado en que fue recibido. Aplican normas de sonido y horarios del manual de propiedad horizontal.
              </p>
            </div>
          </div>
        )}
      </Modal>

      {/* 6. Modal Confirmar Cancelación */}
      <Modal
        open={!!reservaACancelar}
        onClose={() => !cancelando && setReservaACancelar(null)}
        title="Cancelar reserva"
        footer={
          <div className="flex items-center justify-end gap-2 w-full">
            <Button
              variant="outline"
              onClick={() => setReservaACancelar(null)}
              disabled={cancelando}
            >
              Volver
            </Button>
            <Button
              variant="destructive"
              onClick={handleConfirmarCancelacion}
              disabled={cancelando}
            >
              {cancelando ? 'Cancelando...' : 'Confirmar Cancelación'}
            </Button>
          </div>
        }
      >
        {reservaACancelar && (
          <div className="space-y-3 text-sm">
            <p className="text-muted-foreground">
              ¿Estás seguro de que deseas cancelar tu reserva para el espacio{' '}
              <strong className="text-foreground">{reservaACancelar.nombreZona}</strong> programada para el{' '}
              <strong className="text-foreground">{formatDate(reservaACancelar.fechaReserva)}</strong> ({reservaACancelar.horaInicio} - {reservaACancelar.horaFin})?
            </p>
            <div className="p-3 rounded-xl bg-tint-amber-bg border border-tint-amber-fg/25 text-xs text-tint-amber-fg">
              Esta acción liberará el espacio para otros residentes y no se puede deshacer.
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
