import { useState, useMemo, useRef } from 'react';
import { toast } from 'sonner';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, formatCurrency, todayStr } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Input, Textarea } from '../components/ui/Form.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import {
  HardHat,
  Hammer,
  Clock,
  CheckCircle2,
  XCircle,
  ShieldCheck,
  Plus,
  Search,
  Calendar,
  Phone,
  Eye,
  Info,
  AlertTriangle,
  ExternalLink,
} from 'lucide-react';

const ESTADOS_MAP = {
  SOLICITADA: {
    label: 'En Revisión',
    badgeVariant: 'warning',
    icon: Clock,
  },
  APROBADA: {
    label: 'Aprobada / En Curso',
    badgeVariant: 'success',
    icon: CheckCircle2,
  },
  FINALIZADA: {
    label: 'Finalizada',
    badgeVariant: 'info',
    icon: ShieldCheck,
  },
  RECHAZADA: {
    label: 'No Autorizada',
    badgeVariant: 'destructive',
    icon: XCircle,
  },
};

const INITIAL_FORM = {
  descripcion: '',
  fechaInicio: '',
  fechaFinEstimada: '',
  responsableObra: '',
  telefonoResponsable: '',
  depositoGarantia: '',
  licenciaUrbanisticaUrl: '',
};

export default function ResObrasPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/obras/mis-obras'));
  const [modalOpen, setModalOpen] = useState(false);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [selectedObra, setSelectedObra] = useState(null);

  const [filterTab, setFilterTab] = useState('TODAS');
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
    const solicitadas = items.filter((o) => o.estado === 'SOLICITADA').length;
    const aprobadas = items.filter((o) => o.estado === 'APROBADA').length;
    const finalizadas = items.filter((o) => o.estado === 'FINALIZADA').length;
    return { total, solicitadas, aprobadas, finalizadas };
  }, [items]);

  // Filtered List
  const filteredItems = useMemo(() => {
    return items.filter((o) => {
      const matchFilter = filterTab === 'TODAS' || o.estado === filterTab;
      if (!matchFilter) return false;
      if (!searchTerm.trim()) return true;

      const q = searchTerm.toLowerCase();
      const desc = (o.descripcion || '').toLowerCase();
      const resp = (o.responsableObra || '').toLowerCase();
      const tel = (o.telefonoResponsable || '').toLowerCase();
      const id = String(o.idObra || '');

      return desc.includes(q) || resp.includes(q) || tel.includes(q) || id.includes(q);
    });
  }, [items, filterTab, searchTerm]);

  function handleOpenCreate() {
    setForm({
      ...INITIAL_FORM,
      fechaInicio: todayStr(),
    });
    setFormErrors({});
    setModalOpen(true);
  }

  function handleOpenDetail(obra) {
    setSelectedObra(obra);
    setDetailModalOpen(true);
  }

  function validateForm() {
    const errs = {};
    if (!form.descripcion || form.descripcion.trim().length < 10) {
      errs.descripcion = 'Describe el tipo de obra con al menos 10 caracteres.';
    }
    if (!form.fechaInicio) {
      errs.fechaInicio = 'La fecha de inicio es obligatoria.';
    }
    if (!form.fechaFinEstimada) {
      errs.fechaFinEstimada = 'La fecha estimada de fin es obligatoria.';
    } else if (form.fechaInicio && form.fechaFinEstimada < form.fechaInicio) {
      errs.fechaFinEstimada = 'La fecha de fin no puede ser anterior a la de inicio.';
    }
    if (!form.responsableObra || form.responsableObra.trim().length < 3) {
      errs.responsableObra = 'Ingresa el nombre o empresa del contratista responsable.';
    }
    if (!form.telefonoResponsable || form.telefonoResponsable.trim().length < 7) {
      errs.telefonoResponsable = 'Ingresa un teléfono de contacto válido.';
    }
    if (form.depositoGarantia && Number(form.depositoGarantia) < 0) {
      errs.depositoGarantia = 'El depósito no puede ser un valor negativo.';
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
        descripcion: form.descripcion.trim(),
        fechaInicio: form.fechaInicio,
        fechaFinEstimada: form.fechaFinEstimada,
        responsableObra: form.responsableObra.trim(),
        telefonoResponsable: form.telefonoResponsable.trim(),
        depositoGarantia: form.depositoGarantia ? Number(form.depositoGarantia) : 0,
        licenciaUrbanisticaUrl: form.licenciaUrbanisticaUrl?.trim() || null,
      };

      await api.post('/obras', payload);
      toast.success('Solicitud de obra registrada con éxito. Pendiente de aprobación.');
      setModalOpen(false);
      setForm(INITIAL_FORM);
      refetch();
    } catch (err) {
      toast.error('Error al registrar la obra: ' + (err.message || 'Error de conexión'));
    } finally {
      submittingRef.current = false;
      setIsSubmitting(false);
    }
  }

  function calculateDays(start, end) {
    if (!start || !end) return null;
    const d1 = new Date(start);
    const d2 = new Date(end);
    const diff = Math.ceil((d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24));
    return diff >= 0 ? diff + 1 : 0;
  }

  return (
    <div className="space-y-6 pb-12 animate-saed-fade">
      {/* Encabezado */}
      <PageHeader
        title="Mis Obras & Reformas"
        subtitle="Solicitud, seguimiento y permisos de remodelaciones locativas autorizadas en tu unidad"
        action={
          <Button
            onClick={handleOpenCreate}
            className="flex items-center gap-2 shadow-sm font-semibold"
          >
            <Plus className="h-4 w-4" />
            <span>Solicitar Permiso de Obra</span>
          </Button>
        }
      />

      {/* Tarjetas de Métricas / KPIs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          icon="construction"
          value={stats.total}
          label="Total Obras"
          color="primary"
        />
        <StatCard
          icon="schedule"
          value={stats.solicitadas}
          label="En Revisión"
          color="amber"
        />
        <StatCard
          icon="engineering"
          value={stats.aprobadas}
          label="Aprobadas / Activas"
          color="green"
        />
        <StatCard
          icon="verified"
          value={stats.finalizadas}
          label="Finalizadas"
          color="blue"
        />
      </div>

      {/* Barra de Filtros y Búsqueda */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-surface p-3 rounded-xl border border-border shadow-sm">
        {/* Pestañas de Estado */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0 scrollbar-none">
          {[
            { key: 'TODAS', label: 'Todas' },
            { key: 'SOLICITADA', label: 'En Revisión' },
            { key: 'APROBADA', label: 'Aprobadas' },
            { key: 'FINALIZADA', label: 'Finalizadas' },
            { key: 'RECHAZADA', label: 'Rechazadas' },
          ].map((tab) => {
            const isActive = filterTab === tab.key;
            return (
              <button
                key={tab.key}
                type="button"
                onClick={() => setFilterTab(tab.key)}
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

        {/* Búsqueda */}
        <div className="relative min-w-[220px]">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Buscar contratista o labor..."
            className="w-full pl-8 pr-3 py-1.5 text-xs rounded-lg border border-input bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring"
          />
        </div>
      </div>

      {/* Contenido Principal */}
      {loading ? (
        <LoadingState message="Cargando registro de obras..." />
      ) : error ? (
        <div className="rounded-xl border border-rose-200 dark:border-rose-900/60 bg-rose-50/50 dark:bg-rose-950/20 p-6 text-center">
          <AlertTriangle className="mx-auto h-8 w-8 text-rose-500 mb-2" />
          <h4 className="font-semibold text-rose-700 dark:text-rose-400">Error al cargar obras</h4>
          <p className="text-xs text-muted-foreground mt-1">{error.message || 'Error de comunicación'}</p>
          <Button variant="outline" size="sm" onClick={() => refetch()} className="mt-4">
            Reintentar
          </Button>
        </div>
      ) : filteredItems.length === 0 ? (
        <EmptyState
          icon={<Hammer className="h-10 w-10 text-muted-foreground" />}
          title={
            searchTerm || filterTab !== 'TODAS'
              ? 'No se encontraron obras con ese criterio'
              : 'No tienes obras o remodelaciones registradas'
          }
          subtitle={
            searchTerm || filterTab !== 'TODAS'
              ? 'Intenta cambiar los filtros de estado o el término de búsqueda.'
              : 'Si vas a realizar reparaciones, pintura o cambios locativos, registra una solicitud para autorizar el ingreso del personal.'
          }
        >
          {!searchTerm && filterTab === 'TODAS' && (
            <Button onClick={handleOpenCreate} className="mt-2 flex items-center gap-2">
              <Plus className="h-4 w-4" />
              <span>Registrar Primera Obra</span>
            </Button>
          )}
        </EmptyState>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredItems.map((obra) => {
            const estadoConfig = ESTADOS_MAP[obra.estado] || ESTADOS_MAP.SOLICITADA;
            const IconEstado = estadoConfig.icon;
            const diasDuracion = calculateDays(obra.fechaInicio, obra.fechaFinEstimada);

            return (
              <div
                key={obra.idObra}
                className="group relative flex flex-col justify-between rounded-xl border border-border bg-card p-5 shadow-sm transition-all duration-200 hover:shadow-md hover:border-border/80"
              >
                <div>
                  {/* Encabezado de la Tarjeta */}
                  <div className="flex items-start justify-between gap-2 mb-3">
                    <div className="flex items-center gap-2">
                      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary font-bold text-xs">
                        #{obra.idObra}
                      </div>
                      <span className="text-xs font-semibold text-muted-foreground">
                        Unidad {obra.idUnidad || 'Residencial'}
                      </span>
                    </div>

                    <Badge variant={estadoConfig.badgeVariant} className="flex items-center gap-1 text-[11px] py-0.5">
                      <IconEstado className="h-3 w-3" />
                      <span>{estadoConfig.label}</span>
                    </Badge>
                  </div>

                  {/* Descripción de la Obra */}
                  <h3 className="font-semibold text-foreground text-sm leading-snug line-clamp-2 mb-3">
                    {obra.descripcion}
                  </h3>

                  {/* Fechas / Cronograma */}
                  <div className="rounded-lg bg-muted/40 p-2.5 mb-3.5 space-y-1.5 border border-border/50 text-xs">
                    <div className="flex items-center justify-between text-muted-foreground">
                      <span className="flex items-center gap-1.5">
                        <Calendar className="h-3.5 w-3.5 text-primary" />
                        <span>Cronograma:</span>
                      </span>
                      {diasDuracion && (
                        <span className="font-medium text-foreground bg-background px-1.5 py-0.5 rounded border border-border/50">
                          {diasDuracion} {diasDuracion === 1 ? 'día' : 'días'}
                        </span>
                      )}
                    </div>
                    <div className="text-foreground font-medium pl-5 flex items-center justify-between">
                      <span>{formatDate(obra.fechaInicio)}</span>
                      <span className="text-muted-foreground">→</span>
                      <span>{formatDate(obra.fechaFinEstimada)}</span>
                    </div>
                  </div>

                  {/* Datos del Contratista */}
                  <div className="space-y-1.5 text-xs text-muted-foreground mb-4">
                    <div className="flex items-center gap-2 text-foreground font-medium">
                      <HardHat className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                      <span className="truncate">{obra.responsableObra || 'Sin contratista asignado'}</span>
                    </div>
                    {obra.telefonoResponsable && (
                      <div className="flex items-center gap-2 pl-5">
                        <Phone className="h-3 w-3 text-muted-foreground shrink-0" />
                        <a
                          href={`tel:${obra.telefonoResponsable}`}
                          className="hover:underline text-primary"
                          onClick={(e) => e.stopPropagation()}
                        >
                          {obra.telefonoResponsable}
                        </a>
                      </div>
                    )}
                  </div>
                </div>

                {/* Footer de Tarjeta con Depósito y Botón de Detalle */}
                <div className="pt-3 border-t border-border flex items-center justify-between gap-2">
                  <div className="text-xs">
                    {obra.depositoGarantia && Number(obra.depositoGarantia) > 0 ? (
                      <div>
                        <span className="text-muted-foreground text-[10px] uppercase font-semibold block">Garantía</span>
                        <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                          {formatCurrency(obra.depositoGarantia)}
                        </span>
                      </div>
                    ) : (
                      <span className="text-muted-foreground text-[11px]">Sin depósito</span>
                    )}
                  </div>

                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleOpenDetail(obra)}
                    className="h-8 text-xs flex items-center gap-1"
                  >
                    <Eye className="h-3.5 w-3.5" />
                    <span>Ver Detalles</span>
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal: Solicitar Permiso de Obra */}
      <Modal
        open={modalOpen}
        onClose={() => !isSubmitting && setModalOpen(false)}
        title="Solicitar Permiso de Obra o Remodelación"
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
                  <HardHat className="h-4 w-4" />
                  <span>Radicar Solicitud</span>
                </>
              )}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreate} className="space-y-4 py-1">
          {/* Banner Informativo */}
          <div className="rounded-lg bg-blue-50/70 dark:bg-blue-950/20 border border-blue-200/60 dark:border-blue-800/40 p-3 text-xs text-blue-800 dark:text-blue-300 flex items-start gap-2.5">
            <Info className="h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400 mt-0.5" />
            <div>
              <p className="font-semibold">Reglamento de Propiedad Horizontal</p>
              <p className="mt-0.5 text-blue-700 dark:text-blue-400/90 leading-relaxed">
                Toda obra debe respetar los horarios permitidos: Lunes a Viernes de 8:00 AM a 5:00 PM y Sábados de 8:00 AM a 1:00 PM. El contratista debe contar con ARL vigente al ingresar.
              </p>
            </div>
          </div>

          <Textarea
            label="Descripción y Alcance de la Obra"
            required
            id="obra-descripcion"
            rows={3}
            placeholder="Ej: Pintura interior, cambio de piso cerámico en sala y adecuación de mesón en cocina..."
            value={form.descripcion}
            error={formErrors.descripcion}
            onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
          />

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="date"
              label="Fecha de Inicio"
              required
              id="obra-fecha-inicio"
              value={form.fechaInicio}
              error={formErrors.fechaInicio}
              onChange={(e) => setForm({ ...form, fechaInicio: e.target.value })}
            />

            <Input
              type="date"
              label="Fecha Estimada de Finalización"
              required
              id="obra-fecha-fin"
              min={form.fechaInicio || todayStr()}
              value={form.fechaFinEstimada}
              error={formErrors.fechaFinEstimada}
              onChange={(e) => setForm({ ...form, fechaFinEstimada: e.target.value })}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              label="Contratista o Maestro Responsable"
              required
              id="obra-contratista"
              placeholder="Ej: Construcciones López S.A.S o Pedro Gómez"
              value={form.responsableObra}
              error={formErrors.responsableObra}
              onChange={(e) => setForm({ ...form, responsableObra: e.target.value })}
            />

            <Input
              type="tel"
              label="Teléfono del Contratista"
              required
              id="obra-telefono"
              placeholder="Ej: 3001234567"
              value={form.telefonoResponsable}
              error={formErrors.telefonoResponsable}
              onChange={(e) => setForm({ ...form, telefonoResponsable: e.target.value })}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="number"
              min="0"
              step="50000"
              label="Depósito de Garantía (COP, si aplica)"
              id="obra-deposito"
              placeholder="0"
              value={form.depositoGarantia}
              error={formErrors.depositoGarantia}
              onChange={(e) => setForm({ ...form, depositoGarantia: e.target.value })}
            />

            <Input
              type="url"
              label="Enlace a Licencia o Planos (Opcional)"
              id="obra-licencia"
              placeholder="https://drive.google.com/..."
              value={form.licenciaUrbanisticaUrl}
              onChange={(e) => setForm({ ...form, licenciaUrbanisticaUrl: e.target.value })}
            />
          </div>
        </form>
      </Modal>

      {/* Modal: Ver Detalle de Obra */}
      {selectedObra && (
        <Modal
          open={detailModalOpen}
          onClose={() => setDetailModalOpen(false)}
          title={`Detalle de Obra #${selectedObra.idObra}`}
          size="md"
          footer={
            <Button variant="outline" onClick={() => setDetailModalOpen(false)}>
              Cerrar
            </Button>
          }
        >
          <div className="space-y-4 py-2 text-sm">
            {/* Estado y fechas */}
            <div className="flex items-center justify-between p-3 rounded-lg bg-muted/30 border border-border">
              <div>
                <span className="text-xs text-muted-foreground font-medium block">Estado Actual</span>
                <span className="font-semibold text-foreground">
                  {ESTADOS_MAP[selectedObra.estado]?.label || selectedObra.estado}
                </span>
              </div>
              <Badge variant={ESTADOS_MAP[selectedObra.estado]?.badgeVariant || 'default'}>
                {selectedObra.estado}
              </Badge>
            </div>

            <div>
              <span className="text-xs text-muted-foreground font-semibold uppercase block mb-1">Descripción</span>
              <p className="text-foreground leading-relaxed bg-background p-3 rounded-lg border border-border">
                {selectedObra.descripcion}
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3 text-xs">
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block">Fecha Inicio</span>
                <span className="font-medium text-foreground text-sm">{formatDate(selectedObra.fechaInicio)}</span>
              </div>
              <div className="p-2.5 rounded-lg border border-border bg-card">
                <span className="text-muted-foreground block">Fecha Fin Estimada</span>
                <span className="font-medium text-foreground text-sm">{formatDate(selectedObra.fechaFinEstimada)}</span>
              </div>
            </div>

            <div className="p-3 rounded-lg border border-border space-y-2 text-xs">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Responsable / Contratista:</span>
                <span className="font-semibold text-foreground">{selectedObra.responsableObra || 'No especificado'}</span>
              </div>
              {selectedObra.telefonoResponsable && (
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Teléfono Contacto:</span>
                  <a
                    href={`tel:${selectedObra.telefonoResponsable}`}
                    className="font-medium text-primary hover:underline flex items-center gap-1"
                  >
                    <Phone className="h-3 w-3" />
                    <span>{selectedObra.telefonoResponsable}</span>
                  </a>
                </div>
              )}
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Depósito de Garantía:</span>
                <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                  {formatCurrency(selectedObra.depositoGarantia || 0)}
                </span>
              </div>
              {selectedObra.licenciaUrbanisticaUrl && (
                <div className="flex items-center justify-between pt-1 border-t border-border">
                  <span className="text-muted-foreground">Documento / Licencia:</span>
                  <a
                    href={selectedObra.licenciaUrbanisticaUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="font-medium text-primary hover:underline flex items-center gap-1"
                  >
                    <span>Ver documento</span>
                    <ExternalLink className="h-3 w-3" />
                  </a>
                </div>
              )}
            </div>

            {selectedObra.fechaAprobacion && (
              <div className="text-xs text-muted-foreground p-2.5 bg-muted/20 rounded-lg flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />
                <span>Autorizada formalmente por la administración el {formatDate(selectedObra.fechaAprobacion)}.</span>
              </div>
            )}
          </div>
        </Modal>
      )}
    </div>
  );
}
