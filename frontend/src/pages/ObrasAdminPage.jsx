import React, { useState, useMemo } from 'react';
import {
  HardHat,
  Clock,
  CheckCircle2,
  XCircle,
  ShieldCheck,
  Plus,
  Search,
  Calendar,
  Phone,
  Eye,
  AlertTriangle,
  ExternalLink,
  RefreshCw,
  Building2,
  DollarSign,
  Check,
  X,
  User,
} from 'lucide-react';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Button } from '../components/ui/Button.jsx';
import { Input, Textarea } from '../components/ui/Form.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import LoadingState from '../components/ui/LoadingState.jsx';
import { useFetch } from '../lib/hooks.js';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { formatDate, formatCurrency, todayStr } from '../lib/utils.js';
import { toast } from 'sonner';

const ESTADOS_MAP = {
  SOLICITADA: {
    label: 'En Revisión',
    badgeVariant: 'warning',
    color: 'border-amber-500/30 text-amber-600 dark:text-amber-400 bg-amber-500/10',
    icon: Clock,
  },
  APROBADA: {
    label: 'Aprobada / En Curso',
    badgeVariant: 'success',
    color: 'border-emerald-500/30 text-emerald-600 dark:text-emerald-400 bg-emerald-500/10',
    icon: CheckCircle2,
  },
  FINALIZADA: {
    label: 'Finalizada',
    badgeVariant: 'info',
    color: 'border-blue-500/30 text-blue-600 dark:text-blue-400 bg-blue-500/10',
    icon: ShieldCheck,
  },
  RECHAZADA: {
    label: 'No Autorizada',
    badgeVariant: 'destructive',
    color: 'border-rose-500/30 text-rose-600 dark:text-rose-400 bg-rose-500/10',
    icon: XCircle,
  },
};

const INITIAL_FORM = {
  idUnidad: '',
  descripcion: '',
  fechaInicio: '',
  fechaFinEstimada: '',
  responsableObra: '',
  telefonoResponsable: '',
  depositoGarantia: '',
  licenciaUrbanisticaUrl: '',
};

export default function ObrasAdminPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const { data: obrasData, loading: loadingObras, refetch: refetchObras } = useFetch(
    () => tenantApi.get('/obras/admin'),
    [tenant?.activeAssignmentId]
  );

  const { data: unitsData } = useFetch(
    () => tenantApi.get('/units'),
    [tenant?.activeAssignmentId]
  );

  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [confirmModal, setConfirmModal] = useState({ open: false, type: null, obra: null });
  const [selectedObra, setSelectedObra] = useState(null);

  const [filterTab, setFilterTab] = useState('TODAS');
  const [searchTerm, setSearchTerm] = useState('');
  const [form, setForm] = useState(INITIAL_FORM);
  const [formErrors, setFormErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Map units by ID for quick lookup
  const unitsList = useMemo(() => {
    return unitsData?.items || (Array.isArray(unitsData) ? unitsData : []);
  }, [unitsData]);

  const unitsMap = useMemo(() => {
    const map = {};
    unitsList.forEach((u) => {
      const id = u.idUnidad || u.id;
      if (id) {
        map[id] = u.identificador || u.numero || `Unidad ${id}`;
      }
    });
    return map;
  }, [unitsList]);

  const items = useMemo(() => {
    return obrasData?.items || (Array.isArray(obrasData) ? obrasData : []);
  }, [obrasData]);

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
      const unitLabel = (unitsMap[o.idUnidad] || String(o.idUnidad || '')).toLowerCase();

      return (
        desc.includes(q) ||
        resp.includes(q) ||
        tel.includes(q) ||
        id.includes(q) ||
        unitLabel.includes(q)
      );
    });
  }, [items, filterTab, searchTerm, unitsMap]);

  function handleOpenCreate() {
    setForm({
      ...INITIAL_FORM,
      fechaInicio: todayStr(),
    });
    setFormErrors({});
    setCreateModalOpen(true);
  }

  function handleOpenDetail(obra) {
    setSelectedObra(obra);
    setDetailModalOpen(true);
  }

  function validateForm() {
    const errors = {};
    if (!form.idUnidad) {
      errors.idUnidad = 'Selecciona o indica la unidad a intervenir';
    } else if (isNaN(Number(form.idUnidad))) {
      errors.idUnidad = 'El identificador de unidad debe ser numérico';
    }

    if (!form.descripcion || form.descripcion.trim().length < 5) {
      errors.descripcion = 'La descripción debe tener al menos 5 caracteres';
    }

    if (!form.fechaInicio) {
      errors.fechaInicio = 'Indica la fecha prevista de inicio';
    }

    if (!form.fechaFinEstimada) {
      errors.fechaFinEstimada = 'Indica la fecha estimada de culminación';
    } else if (form.fechaInicio && form.fechaFinEstimada < form.fechaInicio) {
      errors.fechaFinEstimada = 'La fecha de fin no puede ser anterior a la de inicio';
    }

    if (!form.responsableObra || form.responsableObra.trim().length < 3) {
      errors.responsableObra = 'Indica el nombre del contratista o maestro a cargo';
    }

    if (!form.telefonoResponsable || form.telefonoResponsable.trim().length < 7) {
      errors.telefonoResponsable = 'Indica un teléfono de contacto válido (mínimo 7 dígitos)';
    }

    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleCreate(e) {
    e?.preventDefault?.();
    if (!validateForm()) {
      toast.error('Corrige los campos obligatorios antes de continuar');
      return;
    }

    setIsSubmitting(true);
    try {
      const payload = {
        idUnidad: Number(form.idUnidad),
        descripcion: form.descripcion.trim(),
        fechaInicio: form.fechaInicio,
        fechaFinEstimada: form.fechaFinEstimada,
        responsableObra: form.responsableObra.trim(),
        telefonoResponsable: form.telefonoResponsable.trim(),
        depositoGarantia: form.depositoGarantia ? Number(form.depositoGarantia) : 0,
        licenciaUrbanisticaUrl: form.licenciaUrbanisticaUrl?.trim() || null,
      };

      await tenantApi.post('/obras', payload);
      toast.success('Obra registrada y radicada exitosamente');
      setCreateModalOpen(false);
      refetchObras();
    } catch (err) {
      toast.error(err?.response?.data?.message || err.message || 'Error al registrar la obra');
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleActionClick(obra, type) {
    setConfirmModal({ open: true, type, obra });
  }

  async function executeConfirmAction() {
    if (!confirmModal.obra || !confirmModal.type) return;

    setIsSubmitting(true);
    const { idObra } = confirmModal.obra;
    try {
      let endpoint = '';
      if (confirmModal.type === 'APROBAR') endpoint = `/obras/${idObra}/aprobar`;
      if (confirmModal.type === 'RECHAZAR') endpoint = `/obras/${idObra}/rechazar`;
      if (confirmModal.type === 'FINALIZADA') endpoint = `/obras/${idObra}/finalizar`;

      await tenantApi.post(endpoint);
      toast.success(
        confirmModal.type === 'APROBAR'
          ? 'Obra autorizada correctamente'
          : confirmModal.type === 'RECHAZAR'
          ? 'Obra no autorizada / rechazada'
          : 'Obra marcada como finalizada'
      );
      setConfirmModal({ open: false, type: null, obra: null });
      refetchObras();
    } catch (err) {
      toast.error(err?.response?.data?.message || err.message || 'Error al cambiar estado');
    } finally {
      setIsSubmitting(false);
    }
  }

  if (loadingObras && items.length === 0) {
    return <LoadingState message="Cargando registro de obras y remodelaciones..." />;
  }

  return (
    <div className="space-y-6 animate-fadeIn pb-12">
      {/* Header Principal */}
      <PageHeader
        title="Obras y Remodelaciones"
        subtitle="Control normativo, verificación de contratistas y seguimiento de intervenciones privadas"
        action={
          <Button
            variant="primary"
            icon={<Plus className="w-4 h-4" />}
            onClick={handleOpenCreate}
          >
            Registrar Obra
          </Button>
        }
      />

      {/* Tarjetas Ejecutivas de Estado */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          label="Total Intervenciones"
          value={stats.total}
          subtitle="Histórico en la copropiedad"
          icon={<HardHat className="w-5 h-5 text-primary" />}
          variant="primary"
        />
        <MetricCard
          label="En Revisión"
          value={stats.solicitadas}
          subtitle="Pendientes de aprobación"
          icon={<Clock className="w-5 h-5 text-amber-500" />}
          variant="warning"
        />
        <MetricCard
          label="Aprobadas / En Curso"
          value={stats.aprobadas}
          subtitle="Con póliza y depósito activo"
          icon={<CheckCircle2 className="w-5 h-5 text-emerald-500" />}
          variant="success"
        />
        <MetricCard
          label="Finalizadas"
          value={stats.finalizadas}
          subtitle="Obras cerradas a conformidad"
          icon={<ShieldCheck className="w-5 h-5 text-blue-500" />}
          variant="info"
        />
      </div>

      {/* Barra de Búsqueda y Pestañas de Filtro */}
      <div className="bg-card border border-border rounded-xl p-4 shadow-sm flex flex-col md:flex-row gap-4 items-center justify-between">
        <div className="relative w-full md:w-96">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por unidad, contratista o descripción..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-4 py-2 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
          />
        </div>

        <div className="flex flex-wrap items-center gap-1.5 w-full md:w-auto">
          {['TODAS', 'SOLICITADA', 'APROBADA', 'FINALIZADA', 'RECHAZADA'].map((st) => (
            <button
              key={st}
              onClick={() => setFilterTab(st)}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-all ${
                filterTab === st
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'bg-muted/50 text-muted-foreground hover:bg-muted hover:text-foreground'
              }`}
            >
              {st === 'TODAS'
                ? 'Todas'
                : st === 'SOLICITADA'
                ? 'En Revisión'
                : st === 'APROBADA'
                ? 'Aprobadas'
                : st === 'FINALIZADA'
                ? 'Finalizadas'
                : 'Rechazadas'}
            </button>
          ))}

          <Button
            variant="outline"
            size="sm"
            onClick={() => refetchObras()}
            icon={<RefreshCw className="w-3.5 h-3.5" />}
            title="Recargar datos"
          >
            Actualizar
          </Button>
        </div>
      </div>

      {/* Tabla Principal */}
      <div className="bg-card border border-border rounded-xl shadow-sm overflow-hidden">
        {filteredItems.length === 0 ? (
          <div className="py-12">
            <EmptyState
              icon={<HardHat className="w-12 h-12 text-muted-foreground" />}
              title="No hay obras encontradas"
              description={
                searchTerm || filterTab !== 'TODAS'
                  ? 'No se encontraron resultados para los filtros seleccionados.'
                  : 'Aún no se han registrado proyectos de obra o remodelación.'
              }
              action={
                <Button variant="outline" size="sm" onClick={handleOpenCreate}>
                  Registrar Primera Obra
                </Button>
              }
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/30 text-muted-foreground font-semibold text-xs uppercase tracking-wider">
                  <th className="py-3.5 px-4">Unidad</th>
                  <th className="py-3.5 px-4">Descripción de Obra</th>
                  <th className="py-3.5 px-4">Cronograma</th>
                  <th className="py-3.5 px-4">Contratista / Contacto</th>
                  <th className="py-3.5 px-4">Garantía</th>
                  <th className="py-3.5 px-4 text-center">Estado</th>
                  <th className="py-3.5 px-4 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filteredItems.map((o) => {
                  const est = ESTADOS_MAP[o.estado] || {
                    label: o.estado,
                    badgeVariant: 'secondary',
                    color: 'text-muted-foreground',
                    icon: AlertTriangle,
                  };
                  const EstIcon = est.icon;
                  const unitLabel = unitsMap[o.idUnidad] || `Unidad #${o.idUnidad}`;

                  return (
                    <tr
                      key={o.idObra}
                      className="hover:bg-muted/20 transition-colors group"
                    >
                      {/* Unidad */}
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2 font-medium text-foreground">
                          <Building2 className="w-4 h-4 text-primary shrink-0" />
                          <span>{unitLabel}</span>
                        </div>
                        <span className="text-xs text-muted-foreground ml-6">
                          ID: {o.idUnidad}
                        </span>
                      </td>

                      {/* Descripción */}
                      <td className="py-3 px-4 max-w-xs">
                        <p className="font-medium text-foreground line-clamp-2" title={o.descripcion}>
                          {o.descripcion}
                        </p>
                        {o.licenciaUrbanisticaUrl && (
                          <a
                            href={o.licenciaUrbanisticaUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1 text-xs text-primary hover:underline mt-1"
                          >
                            <ExternalLink className="w-3 h-3" />
                            Licencia Curaduría
                          </a>
                        )}
                      </td>

                      {/* Fechas */}
                      <td className="py-3 px-4 text-xs whitespace-nowrap">
                        <div className="flex items-center gap-1.5 text-foreground font-medium">
                          <Calendar className="w-3.5 h-3.5 text-muted-foreground" />
                          <span>{formatDate(o.fechaInicio)}</span>
                        </div>
                        <div className="text-muted-foreground pl-5 text-[11px]">
                          hasta {formatDate(o.fechaFinEstimada)}
                        </div>
                      </td>

                      {/* Contratista */}
                      <td className="py-3 px-4 text-xs">
                        <div className="flex items-center gap-1.5 font-medium text-foreground">
                          <User className="w-3.5 h-3.5 text-muted-foreground" />
                          <span>{o.responsableObra || 'No especificado'}</span>
                        </div>
                        {o.telefonoResponsable && (
                          <div className="flex items-center gap-1 text-muted-foreground mt-0.5">
                            <Phone className="w-3 h-3" />
                            <a
                              href={`tel:${o.telefonoResponsable}`}
                              className="hover:text-primary transition-colors"
                            >
                              {o.telefonoResponsable}
                            </a>
                          </div>
                        )}
                      </td>

                      {/* Garantía */}
                      <td className="py-3 px-4 text-xs font-medium">
                        {o.depositoGarantia > 0 ? (
                          <span className="text-foreground">
                            {formatCurrency(o.depositoGarantia)}
                          </span>
                        ) : (
                          <span className="text-muted-foreground">Sin depósito</span>
                        )}
                      </td>

                      {/* Estado */}
                      <td className="py-3 px-4 text-center whitespace-nowrap">
                        <span
                          className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold border ${est.color}`}
                        >
                          <EstIcon className="w-3.5 h-3.5 shrink-0" />
                          {est.label}
                        </span>
                      </td>

                      {/* Acciones */}
                      <td className="py-3 px-4 text-right whitespace-nowrap">
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleOpenDetail(o)}
                            icon={<Eye className="w-4 h-4 text-muted-foreground" />}
                            title="Ver ficha técnica de la obra"
                          />

                          {o.estado === 'SOLICITADA' && (
                            <>
                              <button
                                type="button"
                                onClick={() => handleActionClick(o, 'APROBAR')}
                                title="Autorizar obra"
                                className="inline-flex items-center justify-center w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-600 hover:bg-emerald-500/20 transition-all"
                              >
                                <Check className="w-4 h-4" />
                              </button>
                              <button
                                type="button"
                                onClick={() => handleActionClick(o, 'RECHAZAR')}
                                title="Rechazar obra"
                                className="inline-flex items-center justify-center w-8 h-8 rounded-lg bg-rose-500/10 text-rose-600 hover:bg-rose-500/20 transition-all"
                              >
                                <X className="w-4 h-4" />
                              </button>
                            </>
                          )}

                          {o.estado === 'APROBADA' && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleActionClick(o, 'FINALIZADA')}
                              icon={<ShieldCheck className="w-3.5 h-3.5 text-blue-600" />}
                              className="text-xs border-blue-200 hover:bg-blue-50 dark:border-blue-900 dark:hover:bg-blue-950/50"
                            >
                              Finalizar
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal Registrar Obra */}
      <Modal
        open={createModalOpen}
        onClose={() => !isSubmitting && setCreateModalOpen(false)}
        title="Registrar Proyecto de Obra / Remodelación"
        size="lg"
        footer={
          <div className="flex items-center justify-end gap-2 w-full">
            <Button
              variant="outline"
              disabled={isSubmitting}
              onClick={() => setCreateModalOpen(false)}
            >
              Cancelar
            </Button>
            <Button
              variant="primary"
              loading={isSubmitting}
              onClick={handleCreate}
              icon={<Check className="w-4 h-4" />}
            >
              Radicar Obra
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreate} className="space-y-4 py-2">
          {/* Selector de Unidad */}
          <div>
            <label className="text-sm font-medium text-foreground block mb-1.5">
              Unidad o Apartamento <span className="text-destructive">*</span>
            </label>
            {unitsList.length > 0 ? (
              <select
                value={form.idUnidad}
                onChange={(e) => setForm({ ...form, idUnidad: e.target.value })}
                className="w-full py-2 px-3 text-sm bg-background border border-input rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all"
              >
                <option value="">Selecciona una unidad...</option>
                {unitsList.map((u) => (
                  <option key={u.idUnidad || u.id} value={u.idUnidad || u.id}>
                    {u.identificador || u.numero || `Unidad ${u.idUnidad || u.id}`}
                  </option>
                ))}
              </select>
            ) : (
              <Input
                placeholder="Ej. 101"
                value={form.idUnidad}
                onChange={(e) =>
                  setForm({ ...form, idUnidad: e.target.value.replace(/\D/g, '') })
                }
              />
            )}
            {formErrors.idUnidad && (
              <p className="text-xs text-destructive mt-1 font-medium">
                {formErrors.idUnidad}
              </p>
            )}
          </div>

          {/* Descripción */}
          <Textarea
            label="Descripción del Proyecto *"
            placeholder="Detalla las reformas a efectuar (ej: cambio de pisos, remodelación cocina, pintura)..."
            value={form.descripcion}
            onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
            error={formErrors.descripcion}
            rows={3}
          />

          {/* Fechas */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="date"
              label="Fecha de Inicio *"
              value={form.fechaInicio}
              onChange={(e) => setForm({ ...form, fechaInicio: e.target.value })}
              error={formErrors.fechaInicio}
            />
            <Input
              type="date"
              label="Fecha Fin Estimada *"
              value={form.fechaFinEstimada}
              onChange={(e) => setForm({ ...form, fechaFinEstimada: e.target.value })}
              error={formErrors.fechaFinEstimada}
            />
          </div>

          {/* Contratista y Teléfono */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              label="Responsable / Contratista *"
              placeholder="Ej. Ing. Carlos Pérez o Constructora XYZ"
              value={form.responsableObra}
              onChange={(e) => setForm({ ...form, responsableObra: e.target.value })}
              error={formErrors.responsableObra}
            />
            <Input
              type="tel"
              label="Teléfono del Responsable *"
              placeholder="Ej. 3001234567"
              value={form.telefonoResponsable}
              onChange={(e) =>
                setForm({
                  ...form,
                  telefonoResponsable: e.target.value.replace(/[^0-9+\s()-]/g, ''),
                })
              }
              error={formErrors.telefonoResponsable}
            />
          </div>

          {/* Depósito y Licencia */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              type="number"
              min="0"
              step="1000"
              label="Depósito de Garantía (COP)"
              placeholder="0"
              value={form.depositoGarantia}
              onChange={(e) => setForm({ ...form, depositoGarantia: e.target.value })}
            />
            <Input
              type="url"
              label="URL Licencia Urbanística (Opcional)"
              placeholder="https://..."
              value={form.licenciaUrbanisticaUrl}
              onChange={(e) =>
                setForm({ ...form, licenciaUrbanisticaUrl: e.target.value })
              }
            />
          </div>
        </form>
      </Modal>

      {/* Modal Ficha Detallada */}
      <Modal
        open={detailModalOpen}
        onClose={() => setDetailModalOpen(false)}
        title="Ficha Técnica de la Obra"
        size="md"
        footer={
          <Button variant="outline" onClick={() => setDetailModalOpen(false)}>
            Cerrar
          </Button>
        }
      >
        {selectedObra && (
          <div className="space-y-4 py-2 text-sm">
            <div className="bg-muted/40 p-3 rounded-lg border border-border flex items-center justify-between">
              <div>
                <span className="text-xs text-muted-foreground block">Ubicación</span>
                <span className="font-semibold text-foreground text-base">
                  {unitsMap[selectedObra.idUnidad] || `Unidad #${selectedObra.idUnidad}`}
                </span>
              </div>
              <span
                className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold border ${
                  ESTADOS_MAP[selectedObra.estado]?.color
                }`}
              >
                {ESTADOS_MAP[selectedObra.estado]?.label || selectedObra.estado}
              </span>
            </div>

            <div>
              <span className="text-xs text-muted-foreground font-semibold uppercase tracking-wider block mb-1">
                Descripción de Intervención
              </span>
              <p className="bg-card p-3 rounded-lg border border-border text-foreground leading-relaxed">
                {selectedObra.descripcion}
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="bg-card p-3 rounded-lg border border-border">
                <span className="text-xs text-muted-foreground block">Fecha Inicio</span>
                <span className="font-medium text-foreground">
                  {formatDate(selectedObra.fechaInicio)}
                </span>
              </div>
              <div className="bg-card p-3 rounded-lg border border-border">
                <span className="text-xs text-muted-foreground block">Culminación Prevista</span>
                <span className="font-medium text-foreground">
                  {formatDate(selectedObra.fechaFinEstimada)}
                </span>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="bg-card p-3 rounded-lg border border-border">
                <span className="text-xs text-muted-foreground block">Contratista</span>
                <span className="font-medium text-foreground">
                  {selectedObra.responsableObra || 'N/A'}
                </span>
              </div>
              <div className="bg-card p-3 rounded-lg border border-border">
                <span className="text-xs text-muted-foreground block">Teléfono</span>
                <span className="font-medium text-foreground">
                  {selectedObra.telefonoResponsable || 'N/A'}
                </span>
              </div>
            </div>

            <div className="bg-card p-3 rounded-lg border border-border flex items-center justify-between">
              <div>
                <span className="text-xs text-muted-foreground block">Depósito de Garantía</span>
                <span className="font-bold text-foreground text-base">
                  {selectedObra.depositoGarantia > 0
                    ? formatCurrency(selectedObra.depositoGarantia)
                    : 'Sin depósito requerido'}
                </span>
              </div>
              {selectedObra.licenciaUrbanisticaUrl && (
                <a
                  href={selectedObra.licenciaUrbanisticaUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary/10 text-primary text-xs font-semibold hover:bg-primary/20 transition-colors"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  Ver Licencia
                </a>
              )}
            </div>
          </div>
        )}
      </Modal>

      {/* Modal de Confirmación de Acción (Aprobar / Rechazar / Finalizar) */}
      <Modal
        open={confirmModal.open}
        onClose={() => !isSubmitting && setConfirmModal({ open: false, type: null, obra: null })}
        title={
          confirmModal.type === 'APROBAR'
            ? 'Autorizar Obra y Remodelación'
            : confirmModal.type === 'RECHAZAR'
            ? 'Rechazar Solicitud de Obra'
            : 'Finalizar Proyecto de Obra'
        }
        size="sm"
        footer={
          <div className="flex items-center justify-end gap-2 w-full">
            <Button
              variant="outline"
              disabled={isSubmitting}
              onClick={() => setConfirmModal({ open: false, type: null, obra: null })}
            >
              Cancelar
            </Button>
            <Button
              variant={confirmModal.type === 'RECHAZAR' ? 'danger' : 'primary'}
              loading={isSubmitting}
              onClick={executeConfirmAction}
            >
              {confirmModal.type === 'APROBAR'
                ? 'Confirmar Aprobación'
                : confirmModal.type === 'RECHAZAR'
                ? 'Confirmar Rechazo'
                : 'Confirmar Finalización'}
            </Button>
          </div>
        }
      >
        <div className="py-3 text-sm text-foreground space-y-2">
          <p>
            ¿Estás seguro de que deseas{' '}
            <strong className="font-semibold">
              {confirmModal.type === 'APROBAR'
                ? 'aprobar e iniciar'
                : confirmModal.type === 'RECHAZAR'
                ? 'rechazar'
                : 'marcar como finalizada'}
            </strong>{' '}
            la obra en{' '}
            <strong>
              {unitsMap[confirmModal.obra?.idUnidad] ||
                `Unidad #${confirmModal.obra?.idUnidad}`}
            </strong>
            ?
          </p>
          <p className="text-xs text-muted-foreground">
            {confirmModal.type === 'APROBAR'
              ? 'El residente podrá iniciar labores conforme a los horarios de copropiedad establecidos.'
              : confirmModal.type === 'RECHAZAR'
              ? 'Se notificará al residente que la obra no cumple los requerimientos técnicos o de reglamento.'
              : 'Se dará por cerrada la intervención y se habilitará la liquidación o devolución del depósito de garantía.'}
          </p>
        </div>
      </Modal>
    </div>
  );
}
