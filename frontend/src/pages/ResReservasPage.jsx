import { useState, useMemo, useRef } from 'react';
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
import { Card } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';

import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { useFetch } from '../lib/hooks.js';
import { formatDate, formatCurrency } from '../lib/utils.js';

const ESTADO_BADGE = {
  APROBADA: {
    label: 'Aprobada',
    badgeVariant: 'success',
    colorClass: 'text-emerald-700 dark:text-emerald-400',
    bgClass: 'bg-emerald-50 dark:bg-emerald-950/40 border-emerald-200 dark:border-emerald-800/60',
    icon: CheckCircle2,
  },
  PENDIENTE: {
    label: 'Pendiente de Aprobación',
    badgeVariant: 'warning',
    colorClass: 'text-amber-700 dark:text-amber-400',
    bgClass: 'bg-amber-50 dark:bg-amber-950/40 border-amber-200 dark:border-amber-800/60',
    icon: Clock,
  },
  RECHAZADA: {
    label: 'Rechazada',
    badgeVariant: 'destructive',
    colorClass: 'text-rose-700 dark:text-rose-400',
    bgClass: 'bg-rose-50 dark:bg-rose-950/40 border-rose-200 dark:border-rose-800/60',
    icon: XCircle,
  },
  CANCELADA: {
    label: 'Cancelada',
    badgeVariant: 'secondary',
    colorClass: 'text-slate-600 dark:text-slate-400',
    bgClass: 'bg-slate-100 dark:bg-slate-800 border-slate-200 dark:border-slate-700',
    icon: AlertCircle,
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
  const { user } = useAuth();
  const [form, setForm] = useState(emptyForm);
  const [modalOpen, setModalOpen] = useState(false);
  const [modalDetalle, setModalDetalle] = useState(null);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [errors, setErrors] = useState({});

  const [tabFiltro, setTabFiltro] = useState('TODAS'); // 'TODAS' | 'APROBADAS' | 'PENDIENTES' | 'HISTORIAL'
  const [searchTerm, setSearchTerm] = useState('');

  // 1. Zonas comunes disponibles
  const { data: zonasData, loading: loadingZonas } = useFetch(() => api.get('/zonas-comunes'), []);
  const zonas = useMemo(() => {
    const list = Array.isArray(zonasData) ? zonasData : zonasData?.items || [];
    return Array.isArray(list) ? list : [];
  }, [zonasData]);

  // 2. Mis reservas
  const {
    data: reservasData,
    loading: loadingReservas,
    refetch,
  } = useFetch(() => api.get('/reservas/mis-reservas'), [user]);

  const reservas = useMemo(() => {
    const list = Array.isArray(reservasData) ? reservasData : reservasData?.items || [];
    return Array.isArray(list) ? list : [];
  }, [reservasData]);

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
      if (tabFiltro === 'HISTORIAL' && r.estado !== 'RECHAZADA' && r.estado !== 'CANCELADA') return false;

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

  const zonaSeleccionadaEnModal = useMemo(() => {
    return zonas.find((z) => String(z.idZona) === String(form.idZona));
  }, [zonas, form.idZona]);

  return (
    <PageContainer>
      <PageHeader
        title="Zonas Comunes y Reservas"
        subtitle="Consulta aforo, normas de uso y agenda los espacios sociales de la copropiedad"
        action={
          <Button onClick={() => abrirReservaZona('')}>
            <Plus className="w-4 h-4 mr-1.5" />
            Nueva Reserva
          </Button>
        }
      />

      {/* 1. KPIs Ejecutivos */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <MetricCard
          label="Zonas Disponibles"
          value={String(stats.totalZonas)}
          icon={Building}
          variant="primary"
          context="Espacios comunitarios"
        />
        <MetricCard
          label="Reservas Aprobadas"
          value={String(stats.aprobadas)}
          icon={CheckCircle2}
          variant="success"
          context="Listas para disfrutar"
        />
        <MetricCard
          label="En Revisión"
          value={String(stats.pendientes)}
          icon={Clock}
          variant={stats.pendientes > 0 ? 'warning' : 'primary'}
          context={stats.pendientes > 0 ? 'Pendiente administración' : 'Sin trámites pendientes'}
        />
        <MetricCard
          label="Próxima Reserva"
          value={stats.proxima ? formatDate(stats.proxima.fechaReserva) : 'Ninguna'}
          icon={Calendar}
          variant="primary"
          context={stats.proxima ? `${stats.proxima.nombreZona} (${stats.proxima.horaInicio})` : 'Sin fechas agendadas'}
        />
      </div>

      {/* 2. Catálogo Visual de Espacios Comunes (Amenities) */}
      <div className="mb-8">
        <div className="flex items-center justify-between mb-3.5">
          <div>
            <h2 className="text-lg font-bold text-foreground">Espacios de la Copropiedad</h2>
            <p className="text-xs text-muted-foreground">
              Haz clic en cualquier espacio para agendar tu fecha preferida
            </p>
          </div>
          <Badge variant="outline" className="text-xs font-semibold">
            {zonas.length} espacios activos
          </Badge>
        </div>

        {loadingZonas ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="h-40 rounded-xl border border-border bg-card animate-pulse" />
            ))}
          </div>
        ) : zonas.length === 0 ? (
          <Card className="p-6 text-center text-muted-foreground text-sm">
            No se registran zonas comunes configuradas en esta propiedad.
          </Card>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {zonas.map((z) => {
              const Icon = getZonaIcon(z.nombre);
              const costo = z.costoReserva != null && Number(z.costoReserva) > 0;

              return (
                <div
                  key={z.idZona}
                  className="group bg-card border border-border rounded-xl p-4 hover:border-primary/40 hover:shadow-md transition-all flex flex-col justify-between"
                >
                  <div>
                    <div className="flex items-start justify-between gap-3 mb-3">
                      <div className="p-3 rounded-xl bg-primary/10 text-primary group-hover:bg-primary group-hover:text-primary-foreground transition-colors">
                        <Icon className="w-5 h-5" />
                      </div>
                      <div className="flex flex-col items-end gap-1">
                        <Badge variant="secondary" className="text-xs font-semibold">
                          👥 Aforo: {z.aforoMaximo || 'N/A'}
                        </Badge>
                        {costo ? (
                          <span className="text-xs font-bold text-primary">
                            {formatCurrency(z.costoReserva)}
                          </span>
                        ) : (
                          <span className="text-xs font-medium text-emerald-600 dark:text-emerald-400">
                            Uso Gratuito
                          </span>
                        )}
                      </div>
                    </div>

                    <h3 className="text-base font-bold text-foreground group-hover:text-primary transition-colors">
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
                      className="group-hover:bg-primary group-hover:text-primary-foreground transition-all"
                    >
                      Reservar
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 3. Mis Reservas Solicitadas */}
      <div className="space-y-4">
        <div className="flex flex-col sm:flex-row gap-3 items-center justify-between">
          <div className="flex gap-2 items-center flex-wrap w-full sm:w-auto">
            {[
              { id: 'TODAS', label: `Todas (${stats.total})` },
              { id: 'APROBADAS', label: `Aprobadas (${stats.aprobadas})` },
              { id: 'PENDIENTES', label: `En Revisión (${stats.pendientes})` },
              { id: 'HISTORIAL', label: 'Historial' },
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

          <div className="w-full sm:w-72">
            <div className="relative">
              <Search className="w-4 h-4 text-muted-foreground absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Buscar por espacio, fecha u observación..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 text-sm rounded-lg border border-border bg-card text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </div>
          </div>
        </div>

        {loadingReservas ? (
          <LoadingState text="Consultando historial de reservas..." />
        ) : filteredReservas.length === 0 ? (
          <EmptyState
            icon="event"
            title="No tienes reservas registradas"
            subtitle={
              tabFiltro === 'PENDIENTES'
                ? 'No tienes solicitudes pendientes de aprobación.'
                : 'Usa el botón "Nueva Reserva" o selecciona un espacio del catálogo superior.'
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

                  <div className="flex items-center justify-between md:justify-end gap-3 shrink-0 pt-3 md:pt-0 border-t md:border-t-0 border-border">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => setModalDetalle(r)}
                    >
                      <Eye className="w-4 h-4 mr-1.5" />
                      Ver Ficha
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
        title="Solicitar Reserva de Espacio Común"
        footer={
          <div className="flex items-center justify-end gap-2 w-full">
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={handleSubmit} disabled={saving}>
              {saving ? 'Enviando Solicitud...' : 'Radicar Reserva'}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="form-group">
            <Select
              id="idZona"
              label="Espacio Comunitario *"
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
            <div className="p-3 rounded-xl bg-blue-50/60 dark:bg-blue-950/30 border border-blue-100 dark:border-blue-900/50 flex items-start gap-2.5 text-xs text-blue-900 dark:text-blue-300">
              <Info className="w-4 h-4 shrink-0 text-blue-600 mt-0.5" />
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
              label="Fecha de la Reserva *"
              type="date"
              min={new Date().toISOString().slice(0, 10)}
              value={form.fechaReserva}
              onChange={(e) => setForm((f) => ({ ...f, fechaReserva: e.target.value }))}
              error={errors.fechaReserva}
            />
            <Input
              id="cantidadAsistentes"
              label="Cantidad de Asistentes *"
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
              label="Hora de Inicio *"
              type="time"
              value={form.horaInicio}
              onChange={(e) => setForm((f) => ({ ...f, horaInicio: e.target.value }))}
              error={errors.horaInicio}
            />
            <Input
              id="horaFin"
              label="Hora de Finalización *"
              type="time"
              value={form.horaFin}
              onChange={(e) => setForm((f) => ({ ...f, horaFin: e.target.value }))}
              error={errors.horaFin}
            />
          </div>

          <div className="form-group">
            <Textarea
              id="observaciones"
              label="Motivo del Evento u Observaciones (Opcional)"
              rows={3}
              placeholder="Ej. Cumpleaños familiar, reunión de estudio o convivencia..."
              value={form.observaciones}
              onChange={(e) => setForm((f) => ({ ...f, observaciones: e.target.value }))}
            />
          </div>

          <div className="text-xs text-muted-foreground flex items-center gap-1.5">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
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
          <Button variant="outline" onClick={() => setModalDetalle(null)}>
            Cerrar Ficha
          </Button>
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
              <div className="text-xs font-bold text-muted-foreground uppercase tracking-wider">
                Detalles del Evento
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
                  <span className="text-muted-foreground">Canon / Garantía:</span>
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

            <div className="p-3 rounded-xl bg-amber-50/50 dark:bg-amber-950/20 border border-amber-200/60 dark:border-amber-900/40 text-xs text-amber-900 dark:text-amber-300 space-y-1">
              <div className="font-bold flex items-center gap-1.5">
                <AlertCircle className="w-4 h-4 text-amber-600 shrink-0" />
                Reglas de Convivencia
              </div>
              <p>
                El residente es responsable del cuidado del mobiliario, orden y entrega del espacio en el mismo
                estado en que fue recibido. Aplican normas de sonido y horarios del manual de propiedad horizontal.
              </p>
            </div>
          </div>
        )}
      </Modal>
    </PageContainer>
  );
}
