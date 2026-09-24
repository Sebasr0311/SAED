import { useState, useEffect, useMemo, useCallback } from 'react';
import { toast } from 'sonner';
import {
  Car,
  Plus,
  Pencil,
  Trash2,
  RefreshCw,
  Tag,
  CheckCircle2,
  AlertCircle,
  Shield,
  Search,
} from 'lucide-react';

import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../ui/card.tsx';
import { Badge } from '../ui/badge.tsx';
import { Button } from '../ui/button.tsx';
import { Modal } from '../ui/Modal.jsx';
import { ConfirmDialog } from '../ui/ConfirmDialog.jsx';
import { Skeleton } from '../ui/skeleton.tsx';
import { Input, Select } from '../ui/Form.jsx';
import api from '../../lib/api.js';

const TIPOS_VEHICULO = [
  { value: 'AUTOMOVIL', label: 'Automóvil' },
  { value: 'MOTOCICLETA', label: 'Motocicleta' },
  { value: 'BICICLETA', label: 'Bicicleta' },
  { value: 'ELECTRICO', label: 'Vehículo Eléctrico' },
  { value: 'DE_CARGA', label: 'Vehículo de Carga' },
  { value: 'OTRO', label: 'Otro' },
];

const ESTADOS_VEHICULO = [
  { value: 'ACTIVO', label: 'Activo' },
  { value: 'INACTIVO', label: 'Inactivo' },
];

const emptyVehicleForm = {
  placa: '',
  tipoVehiculo: 'AUTOMOVIL',
  marca: '',
  modelo: '',
  color: '',
  tagRfid: '',
  estado: 'ACTIVO',
};

export default function ResidentVehiclesSection({
  unitId,
  defaultPersonaId = null,
  readOnly = false,
  apiClient = api,
  onRefreshParent,
}) {
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingVehicle, setEditingVehicle] = useState(null);
  const [form, setForm] = useState(emptyVehicleForm);
  const [formErrors, setFormErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState({ open: false, vehicle: null });

  const fetchVehicles = useCallback(async (isSilent = false) => {
    if (!unitId) {
      setVehicles([]);
      setLoading(false);
      return;
    }
    if (!isSilent) setLoading(true);
    else setRefreshing(true);

    try {
      const res = await apiClient.get(`/unidades/${unitId}/vehiculos`);
      const list = Array.isArray(res) ? res : res?.items || [];
      setVehicles(list);
    } catch (err) {
      toast.error('No se pudo cargar la lista de vehículos: ' + (err.message || 'error de conexión'));
      setVehicles([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [unitId, apiClient]);

  useEffect(() => {
    fetchVehicles();
  }, [fetchVehicles]);

  const openCreateModal = () => {
    setEditingVehicle(null);
    setForm(emptyVehicleForm);
    setFormErrors({});
    setModalOpen(true);
  };

  const openEditModal = (veh) => {
    setEditingVehicle(veh);
    setForm({
      placa: veh.placa || '',
      tipoVehiculo: (veh.tipoVehiculo || 'AUTOMOVIL').toUpperCase(),
      marca: veh.marca || '',
      modelo: veh.modelo || '',
      color: veh.color || '',
      tagRfid: veh.tagRfid || '',
      estado: (veh.estado || 'ACTIVO').toUpperCase(),
    });
    setFormErrors({});
    setModalOpen(true);
  };

  const validateForm = () => {
    const errs = {};
    const placaClean = (form.placa || '').trim();
    if (!placaClean) {
      errs.placa = 'La placa o identificador es obligatorio';
    } else if (placaClean.length < 3) {
      errs.placa = 'La placa debe tener al menos 3 caracteres';
    } else if (placaClean.length > 15) {
      errs.placa = 'La placa no puede exceder 15 caracteres';
    }

    if (!form.tipoVehiculo) {
      errs.tipoVehiculo = 'Seleccione el tipo de vehículo';
    }
    setFormErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSave = async (e) => {
    e?.preventDefault();
    if (!validateForm()) return;

    setSaving(true);
    const payload = {
      unidadId: Number(unitId),
      personaId: Number(editingVehicle?.personaId || defaultPersonaId || 1),
      placa: form.placa.trim().toUpperCase(),
      tipoVehiculo: form.tipoVehiculo.toUpperCase(),
      marca: form.marca ? form.marca.trim() : null,
      modelo: form.modelo ? form.modelo.trim() : null,
      color: form.color ? form.color.trim() : null,
      tagRfid: form.tagRfid ? form.tagRfid.trim().toUpperCase() : null,
      estado: form.estado.toUpperCase(),
    };

    try {
      if (editingVehicle) {
        await apiClient.put(`/vehiculos/${editingVehicle.idVehiculo}`, payload);
        toast.success(`Vehículo ${payload.placa} actualizado exitosamente`);
      } else {
        await apiClient.post('/vehiculos', payload);
        toast.success(`Vehículo ${payload.placa} registrado exitosamente`);
      }
      setModalOpen(false);
      fetchVehicles(true);
      if (onRefreshParent) onRefreshParent();
    } catch (err) {
      toast.error(err.message || 'Error al guardar el vehículo');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!confirmDelete.vehicle) return;
    try {
      await apiClient.delete(`/vehiculos/${confirmDelete.vehicle.idVehiculo}`);
      toast.success(`Vehículo ${confirmDelete.vehicle.placa} eliminado`);
      setConfirmDelete({ open: false, vehicle: null });
      fetchVehicles(true);
      if (onRefreshParent) onRefreshParent();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar el vehículo');
    }
  };

  const totalActivos = useMemo(() => {
    return vehicles.filter((v) => (v.estado || '').toUpperCase() === 'ACTIVO').length;
  }, [vehicles]);

  return (
    <Card className="border-border/70 shadow-xs">
      <CardHeader className="pb-3 border-b border-border/50">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-primary/10 text-primary">
              <Car className="w-5 h-5" aria-hidden="true" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <CardTitle className="text-base font-semibold text-foreground">
                  Vehículos de la Unidad
                </CardTitle>
                <Badge variant="outline" className="text-xs font-medium">
                  {totalActivos} {totalActivos === 1 ? 'activo' : 'activos'}
                </Badge>
              </div>
              <CardDescription className="text-xs text-muted-foreground mt-0.5">
                Censo vehicular, placas autorizadas y control de acceso RFID
              </CardDescription>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => fetchVehicles(true)}
              disabled={refreshing || loading}
              className="text-xs min-h-[36px]"
              aria-label="Actualizar lista de vehículos"
            >
              <RefreshCw
                className={`h-3.5 w-3.5 mr-1.5 ${refreshing ? 'animate-spin text-primary' : ''}`}
                aria-hidden="true"
              />
              Actualizar
            </Button>
            {!readOnly && (
              <Button
                variant="primary"
                size="sm"
                onClick={openCreateModal}
                className="text-xs min-h-[36px] gap-1.5 shadow-xs"
              >
                <Plus className="w-3.5 h-3.5" aria-hidden="true" />
                <span>Registrar Vehículo</span>
              </Button>
            )}
          </div>
        </div>
      </CardHeader>

      <CardContent className="pt-4">
        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {[1, 2].map((i) => (
              <div key={i} className="p-4 rounded-xl border border-border/60 bg-muted/20 space-y-2">
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-6 w-32" />
                <Skeleton className="h-3 w-40" />
              </div>
            ))}
          </div>
        ) : vehicles.length === 0 ? (
          <div className="p-8 text-center border border-dashed border-border rounded-xl bg-muted/10 space-y-3">
            <div className="w-12 h-12 rounded-full bg-primary/10 text-primary flex items-center justify-center mx-auto">
              <Car className="w-6 h-6 opacity-80" aria-hidden="true" />
            </div>
            <div className="space-y-1">
              <p className="text-sm font-semibold text-foreground">Sin vehículos registrados</p>
              <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                Esta unidad no tiene automóviles, motocicletas o bicicletas registradas para control de acceso y parqueo.
              </p>
            </div>
            {!readOnly && (
              <Button
                variant="outline"
                size="sm"
                onClick={openCreateModal}
                className="text-xs min-h-[36px] gap-1.5 mt-2"
              >
                <Plus className="w-3.5 h-3.5 text-primary" aria-hidden="true" />
                <span>Registrar Primer Vehículo</span>
              </Button>
            )}
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {vehicles.map((veh) => {
              const isActivo = (veh.estado || 'ACTIVO').toUpperCase() === 'ACTIVO';
              const tipoLabel =
                TIPOS_VEHICULO.find((t) => t.value === (veh.tipoVehiculo || '').toUpperCase())?.label ||
                veh.tipoVehiculo ||
                'Vehículo';

              return (
                <div
                  key={veh.idVehiculo}
                  className="p-4 rounded-xl border border-border/70 bg-card hover:border-primary/40 hover:shadow-xs transition-all space-y-3"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-center gap-2.5">
                      <div className="p-2 rounded-lg bg-primary/10 text-primary shrink-0">
                        <Car className="w-4 h-4" aria-hidden="true" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-mono text-base font-black tracking-wider text-foreground px-2 py-0.5 rounded bg-muted border border-border">
                            {veh.placa}
                          </span>
                          <Badge
                            variant={isActivo ? 'success' : 'secondary'}
                            className="text-[10px] font-semibold"
                          >
                            {isActivo ? 'Activo' : 'Inactivo'}
                          </Badge>
                        </div>
                        <p className="text-xs font-medium text-muted-foreground mt-1">
                          {tipoLabel}
                          {veh.marca ? ` · ${veh.marca}` : ''}
                          {veh.modelo ? ` ${veh.modelo}` : ''}
                          {veh.color ? ` · Color ${veh.color}` : ''}
                        </p>
                      </div>
                    </div>

                    {!readOnly && (
                      <div className="flex items-center gap-1 shrink-0">
                        <button
                          type="button"
                          onClick={() => openEditModal(veh)}
                          className="p-1.5 rounded-lg text-muted-foreground hover:text-primary hover:bg-primary/10 transition-colors"
                          aria-label={`Editar vehículo ${veh.placa}`}
                        >
                          <Pencil className="w-3.5 h-3.5" aria-hidden="true" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmDelete({ open: true, vehicle: veh })}
                          className="p-1.5 rounded-lg text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
                          aria-label={`Eliminar vehículo ${veh.placa}`}
                        >
                          <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                        </button>
                      </div>
                    )}
                  </div>

                  {veh.tagRfid && (
                    <div className="pt-2 border-t border-border/50 flex items-center justify-between text-xs">
                      <span className="text-muted-foreground flex items-center gap-1.5">
                        <Tag className="w-3.5 h-3.5 text-primary/70" aria-hidden="true" />
                        Tag RFID Asignado:
                      </span>
                      <span className="font-mono font-bold text-foreground text-[11px] bg-primary/5 px-2 py-0.5 rounded border border-primary/20">
                        {veh.tagRfid}
                      </span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </CardContent>

      {/* Modal Registrar / Editar Vehículo */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingVehicle ? 'Editar Vehículo' : 'Registrar Nuevo Vehículo'}
        description="Datos de identificación vehicular para portería y asignación de parqueo."
      >
        <form onSubmit={handleSave} className="space-y-4 pt-2">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <Input
                label="Placa o Identificador *"
                value={form.placa}
                onChange={(e) =>
                  setForm({ ...form, placa: e.target.value.toUpperCase().replace(/\s+/g, '') })
                }
                placeholder="Ej. ABC123"
                error={formErrors.placa}
                className="font-mono uppercase font-bold"
                required
              />
            </div>
            <div>
              <Select
                label="Tipo de Vehículo *"
                value={form.tipoVehiculo}
                onChange={(e) => setForm({ ...form, tipoVehiculo: e.target.value })}
                error={formErrors.tipoVehiculo}
                options={TIPOS_VEHICULO}
                required
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <Input
                label="Marca"
                value={form.marca}
                onChange={(e) => setForm({ ...form, marca: e.target.value })}
                placeholder="Ej. Toyota"
              />
            </div>
            <div>
              <Input
                label="Modelo / Línea"
                value={form.modelo}
                onChange={(e) => setForm({ ...form, modelo: e.target.value })}
                placeholder="Ej. Corolla"
              />
            </div>
            <div>
              <Input
                label="Color"
                value={form.color}
                onChange={(e) => setForm({ ...form, color: e.target.value })}
                placeholder="Ej. Gris Plata"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <Input
                label="Tag RFID (Antena Portería)"
                value={form.tagRfid}
                onChange={(e) =>
                  setForm({ ...form, tagRfid: e.target.value.toUpperCase() })
                }
                placeholder="Ej. RFID-201-A"
              />
            </div>
            <div>
              <Select
                label="Estado"
                value={form.estado}
                onChange={(e) => setForm({ ...form, estado: e.target.value })}
                options={ESTADOS_VEHICULO}
              />
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 pt-4 border-t border-border">
            <Button
              type="button"
              variant="outline"
              onClick={() => setModalOpen(false)}
              disabled={saving}
            >
              Cancelar
            </Button>
            <Button type="submit" variant="primary" disabled={saving}>
              {saving ? 'Guardando...' : editingVehicle ? 'Guardar Cambios' : 'Registrar Vehículo'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Confirmar Eliminación */}
      <ConfirmDialog
        open={confirmDelete.open}
        onClose={() => setConfirmDelete({ open: false, vehicle: null })}
        onConfirm={handleDelete}
        title="¿Eliminar registro vehicular?"
        message={`Esta acción removerá el vehículo con placa ${confirmDelete.vehicle?.placa} del censo de la unidad. La portería ya no podrá reconocer automáticamente este vehículo.`}
        confirmLabel="Eliminar Vehículo"
        danger
      />
    </Card>
  );
}
