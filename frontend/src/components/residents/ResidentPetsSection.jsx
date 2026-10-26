import { useState, useEffect, useMemo, useCallback } from 'react';
import { toast } from 'sonner';
import {
  Heart,
  Plus,
  Pencil,
  Trash2,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  FileText,
  ExternalLink,
} from 'lucide-react';

import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../ui/card.tsx';
import { Badge } from '../ui/badge.tsx';
import { Button } from '../ui/button.tsx';
import { Modal } from '../ui/Modal.jsx';
import { ConfirmDialog } from '../ui/ConfirmDialog.jsx';
import { Skeleton } from '../ui/skeleton.tsx';
import { Input, Select } from '../ui/Form.jsx';
import api from '../../lib/api.js';

const ESPECIES_MASCOTA = [
  { value: 'PERRO', label: 'Perro' },
  { value: 'GATO', label: 'Gato' },
  { value: 'AVE', label: 'Ave' },
  { value: 'ROEDOR', label: 'Roedor' },
  { value: 'PECES', label: 'Peces' },
  { value: 'OTRO', label: 'Otro' },
];

const GENEROS_MASCOTA = [
  { value: 'M', label: 'Macho' },
  { value: 'H', label: 'Hembra' },
];

const ESTADOS_MASCOTA = [
  { value: 'ACTIVA', label: 'Activa' },
  { value: 'INACTIVA', label: 'Inactiva' },
  { value: 'TRASLADADA', label: 'Trasladada' },
  { value: 'FALLECIDA', label: 'Fallecida' },
];

const emptyPetForm = {
  nombre: '',
  especie: 'PERRO',
  raza: '',
  color: '',
  genero: 'M',
  fechaNacimientoAprox: '',
  pesoKg: '',
  numeroMicrochip: '',
  esRazaManejoEspecial: 'N',
  polizaResponsabilidadUrl: '',
  carnetVacunacionUrl: '',
  fotoUrl: '',
  estado: 'ACTIVA',
};

export default function ResidentPetsSection({
  unitId,
  defaultPersonaId = null,
  readOnly = false,
  apiClient = api,
  onRefreshParent,
}) {
  const [pets, setPets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingPet, setEditingPet] = useState(null);
  const [form, setForm] = useState(emptyPetForm);
  const [formErrors, setFormErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState({ open: false, pet: null });

  const fetchPets = useCallback(async (isSilent = false) => {
    if (!unitId) {
      setPets([]);
      setLoading(false);
      return;
    }
    if (!isSilent) setLoading(true);
    else setRefreshing(true);

    try {
      const res = await apiClient.get(`/unidades/${unitId}/mascotas`);
      const list = Array.isArray(res) ? res : res?.items || [];
      setPets(list);
    } catch (err) {
      toast.error('No se pudo cargar el censo de mascotas: ' + (err.message || 'error de conexión'));
      setPets([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [unitId, apiClient]);

  useEffect(() => {
    fetchPets();
  }, [fetchPets]);

  const openCreateModal = () => {
    setEditingPet(null);
    setForm(emptyPetForm);
    setFormErrors({});
    setModalOpen(true);
  };

  const openEditModal = (pet) => {
    setEditingPet(pet);
    setForm({
      nombre: pet.nombre || '',
      especie: (pet.especie || 'PERRO').toUpperCase(),
      raza: pet.raza || '',
      color: pet.color || '',
      genero: (pet.genero || 'M').toUpperCase(),
      fechaNacimientoAprox: pet.fechaNacimientoAprox || '',
      pesoKg: pet.pesoKg != null ? String(pet.pesoKg) : '',
      numeroMicrochip: pet.numeroMicrochip || '',
      esRazaManejoEspecial: pet.esRazaManejoEspecial === 'S' ? 'S' : 'N',
      polizaResponsabilidadUrl: pet.polizaResponsabilidadUrl || '',
      carnetVacunacionUrl: pet.carnetVacunacionUrl || '',
      fotoUrl: pet.fotoUrl || '',
      estado: (pet.estado || 'ACTIVA').toUpperCase(),
    });
    setFormErrors({});
    setModalOpen(true);
  };

  const validateForm = () => {
    const errs = {};
    if (!form.nombre.trim()) {
      errs.nombre = 'El nombre de la mascota es obligatorio';
    }
    if (!form.especie) {
      errs.especie = 'Seleccione la especie';
    }
    if (form.esRazaManejoEspecial === 'S' && !form.polizaResponsabilidadUrl?.trim()) {
      errs.polizaResponsabilidadUrl = 'Las razas de manejo especial requieren registro de póliza (Ley 1801)';
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
      responsableId: Number(editingPet?.responsableId || defaultPersonaId) || null,
      nombre: form.nombre.trim(),
      especie: form.especie.toUpperCase(),
      raza: form.raza ? form.raza.trim() : null,
      color: form.color ? form.color.trim() : null,
      genero: form.genero.toUpperCase(),
      fechaNacimientoAprox: form.fechaNacimientoAprox || null,
      pesoKg: form.pesoKg ? parseFloat(form.pesoKg) : null,
      numeroMicrochip: form.numeroMicrochip ? form.numeroMicrochip.trim().toUpperCase() : null,
      esRazaManejoEspecial: form.esRazaManejoEspecial === 'S' ? 'S' : 'N',
      polizaResponsabilidadUrl: form.polizaResponsabilidadUrl ? form.polizaResponsabilidadUrl.trim() : null,
      carnetVacunacionUrl: form.carnetVacunacionUrl ? form.carnetVacunacionUrl.trim() : null,
      fotoUrl: form.fotoUrl ? form.fotoUrl.trim() : null,
      estado: form.estado.toUpperCase(),
    };

    try {
      if (editingPet) {
        await apiClient.put(`/mascotas/${editingPet.idMascota}`, payload);
        toast.success(`Mascota ${payload.nombre} actualizada exitosamente`);
      } else {
        await apiClient.post('/mascotas', payload);
        toast.success(`Mascota ${payload.nombre} censada exitosamente`);
      }
      setModalOpen(false);
      fetchPets(true);
      if (onRefreshParent) onRefreshParent();
    } catch (err) {
      toast.error(err.message || 'Error al guardar la mascota');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!confirmDelete.pet) return;
    try {
      await apiClient.delete(`/mascotas/${confirmDelete.pet.idMascota}`);
      toast.success(`Mascota ${confirmDelete.pet.nombre} eliminada del censo`);
      setConfirmDelete({ open: false, pet: null });
      fetchPets(true);
      if (onRefreshParent) onRefreshParent();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar la mascota');
    }
  };

  const totalActivas = useMemo(() => {
    return pets.filter((p) => (p.estado || '').toUpperCase() === 'ACTIVA').length;
  }, [pets]);

  return (
    <Card className="border-border bg-surface shadow-xs">
      <CardHeader className="pb-3 border-b border-border bg-surface-container rounded-t-xl">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-tint-purple-bg text-primary-text">
              <Heart className="w-5 h-5" aria-hidden="true" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <CardTitle className="text-base font-bold text-on-surface">
                  Mascotas de la unidad
                </CardTitle>
                <Badge variant="lavender" className="text-xs font-semibold">
                  {totalActivas} {totalActivas === 1 ? 'activa' : 'activas'}
                </Badge>
              </div>
              <CardDescription className="text-xs text-text-muted mt-0.5">
                Censo animal, microchips y cumplimiento de Ley 1801 (manejo especial)
              </CardDescription>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => fetchPets(true)}
              disabled={refreshing || loading}
              className="text-xs min-h-[36px]"
              aria-label="Actualizar lista de mascotas"
            >
              <RefreshCw
                className={`h-3.5 w-3.5 mr-1.5 ${refreshing ? 'animate-spin text-primary-text' : ''}`}
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
                <span>Registrar mascota</span>
              </Button>
            )}
          </div>
        </div>
      </CardHeader>

      <CardContent className="pt-4">
        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {[1, 2].map((i) => (
              <div key={i} className="p-4 rounded-xl border border-border bg-surface-container space-y-2">
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-6 w-32" />
                <Skeleton className="h-3 w-40" />
              </div>
            ))}
          </div>
        ) : pets.length === 0 ? (
          <div className="p-8 text-center border border-dashed border-border rounded-xl bg-surface-container space-y-3">
            <div className="w-12 h-12 rounded-full bg-tint-purple-bg text-primary-text flex items-center justify-center mx-auto">
              <Heart className="w-6 h-6 opacity-90" aria-hidden="true" />
            </div>
            <div className="space-y-1">
              <p className="text-sm font-bold text-on-surface">Sin mascotas registradas</p>
              <p className="text-xs text-text-muted max-w-sm mx-auto">
                No hay caninos, felinos u otras mascotas registradas para el censo de convivencia de esta unidad.
              </p>
            </div>
            {!readOnly && (
              <Button
                variant="outline"
                size="sm"
                onClick={openCreateModal}
                className="text-xs min-h-[36px] gap-1.5 mt-2"
              >
                <Plus className="w-3.5 h-3.5 text-primary-text" aria-hidden="true" />
                <span>Registrar primera mascota</span>
              </Button>
            )}
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {pets.map((pet) => {
              const isActiva = (pet.estado || 'ACTIVA').toUpperCase() === 'ACTIVA';
              const especieLabel =
                ESPECIES_MASCOTA.find((e) => e.value === (pet.especie || '').toUpperCase())?.label ||
                pet.especie ||
                'Mascota';
              const isManejoEspecial = pet.esRazaManejoEspecial === 'S';

              return (
                <div
                  key={pet.idMascota}
                  className="p-4 rounded-xl border border-border bg-surface hover:border-primary/50 hover:shadow-xs transition-all space-y-3"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="flex items-center gap-2.5">
                      <div className="p-2 rounded-xl bg-tint-purple-bg text-primary-text shrink-0">
                        <Heart className="w-4 h-4" aria-hidden="true" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-base font-bold text-on-surface">
                            {pet.nombre}
                          </span>
                          <Badge
                            variant={isActiva ? 'success' : 'secondary'}
                            className="text-[10px] font-semibold"
                          >
                            {pet.estado || 'Activa'}
                          </Badge>
                          {pet.genero && (
                            <span className="text-[11px] font-semibold px-2 py-0.5 rounded-md bg-tint-purple-bg text-primary-text">
                              {pet.genero === 'H' ? 'Hembra' : 'Macho'}
                            </span>
                          )}
                        </div>
                        <p className="text-xs font-medium text-text-muted mt-0.5">
                          {especieLabel}
                          {pet.raza ? ` · ${pet.raza}` : ''}
                          {pet.color ? ` · Color ${pet.color}` : ''}
                          {pet.pesoKg ? ` · ${pet.pesoKg} kg` : ''}
                        </p>
                      </div>
                    </div>

                    {!readOnly && (
                      <div className="flex items-center gap-1 shrink-0">
                        <button
                          type="button"
                          onClick={() => openEditModal(pet)}
                          className="p-1.5 rounded-lg text-text-muted hover:text-primary-text hover:bg-tint-purple-bg transition-colors"
                          aria-label={`Editar mascota ${pet.nombre}`}
                        >
                          <Pencil className="w-3.5 h-3.5" aria-hidden="true" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmDelete({ open: true, pet })}
                          className="p-1.5 rounded-lg text-text-muted hover:text-destructive hover:bg-destructive/10 transition-colors"
                          aria-label={`Eliminar mascota ${pet.nombre}`}
                        >
                          <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                        </button>
                      </div>
                    )}
                  </div>

                  {/* Microchip & Ley 1801 */}
                  <div className="space-y-1.5 pt-2 border-t border-border text-xs">
                    {pet.numeroMicrochip && (
                      <div className="flex items-center justify-between text-text-muted">
                        <span>Microchip de identificación:</span>
                        <span className="font-mono font-bold text-on-surface text-[11px] bg-surface-container px-2 py-0.5 rounded-md border border-border">
                          {pet.numeroMicrochip}
                        </span>
                      </div>
                    )}

                    {isManejoEspecial ? (
                      <div className="p-2.5 rounded-xl bg-tint-amber-bg border border-tint-amber-fg/25 text-tint-amber-fg flex items-start gap-2">
                        <ShieldAlert className="w-4 h-4 shrink-0 text-tint-amber-fg mt-0.5" />
                        <div className="space-y-0.5">
                          <p className="font-semibold text-[11px]">Raza de Manejo Especial (Ley 1801)</p>
                          {pet.polizaResponsabilidadUrl ? (
                            <a
                              href={pet.polizaResponsabilidadUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-[10px] font-semibold text-primary-text hover:underline flex items-center gap-1"
                            >
                              <ShieldCheck className="w-3 h-3 text-tint-green-fg" />
                              Ver póliza de responsabilidad civil
                              <ExternalLink className="w-2.5 h-2.5" />
                            </a>
                          ) : (
                            <p className="text-[10px] text-destructive font-medium">
                              Pendiente adjuntar póliza de responsabilidad civil vigente
                            </p>
                          )}
                        </div>
                      </div>
                    ) : null}

                    {pet.carnetVacunacionUrl && (
                      <div className="flex items-center justify-between pt-1">
                        <span className="text-text-muted">Carnet de vacunación:</span>
                        <a
                          href={pet.carnetVacunacionUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-primary-text hover:underline font-semibold flex items-center gap-1 text-[11px]"
                        >
                          <FileText className="w-3 h-3" />
                          Consultar carnet
                          <ExternalLink className="w-2.5 h-2.5" />
                        </a>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>

      {/* Modal Registrar / Editar Mascota */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingPet ? 'Editar Registro de Mascota' : 'Registrar Nueva Mascota'}
        description="Censo de animales de compañía y control de convivencia comunitaria."
      >
        <form onSubmit={handleSave} className="space-y-4 pt-2">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <Input
                label="Nombre de la mascota *"
                value={form.nombre}
                onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                placeholder="Ej. Firulais, Luna"
                error={formErrors.nombre}
                required
              />
            </div>
            <div>
              <Select
                label="Especie *"
                value={form.especie}
                onChange={(e) => setForm({ ...form, especie: e.target.value })}
                error={formErrors.especie}
                options={ESPECIES_MASCOTA}
                required
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <Input
                label="Raza"
                value={form.raza}
                onChange={(e) => setForm({ ...form, raza: e.target.value })}
                placeholder="Ej. Golden Retriever"
              />
            </div>
            <div>
              <Input
                label="Color / características"
                value={form.color}
                onChange={(e) => setForm({ ...form, color: e.target.value })}
                placeholder="Ej. Dorado, Blanco"
              />
            </div>
            <div>
              <Select
                label="Género"
                value={form.genero}
                onChange={(e) => setForm({ ...form, genero: e.target.value })}
                options={GENEROS_MASCOTA}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <Input
                type="date"
                label="Nacimiento aprox."
                value={form.fechaNacimientoAprox}
                onChange={(e) => setForm({ ...form, fechaNacimientoAprox: e.target.value })}
              />
            </div>
            <div>
              <Input
                type="number"
                step="0.1"
                min="0"
                label="Peso (kg)"
                value={form.pesoKg}
                onChange={(e) => setForm({ ...form, pesoKg: e.target.value })}
                placeholder="Ej. 18.5"
              />
            </div>
            <div>
              <Input
                label="Número de microchip"
                value={form.numeroMicrochip}
                onChange={(e) =>
                  setForm({ ...form, numeroMicrochip: e.target.value.toUpperCase() })
                }
                placeholder="Ej. 900215000"
                className="font-mono"
              />
            </div>
          </div>

          {/* Toggle Raza de Manejo Especial (Ley 1801) */}
          <div className="p-3 rounded-xl border border-border bg-surface-container space-y-2">
            <div className="flex items-center justify-between">
              <div>
                <label className="text-xs font-semibold text-on-surface flex items-center gap-1.5 cursor-pointer">
                  <span>¿Es raza de manejo especial?</span>
                  <span className="text-[10px] text-text-muted font-normal">(Ley 1801 de 2016)</span>
                </label>
                <p className="text-[11px] text-text-muted">
                  Aplica a caninos con características físicas o razas tipificadas por el código de convivencia.
                </p>
              </div>
              <Select
                value={form.esRazaManejoEspecial}
                onChange={(e) => setForm({ ...form, esRazaManejoEspecial: e.target.value })}
                options={[
                  { value: 'N', label: 'No' },
                  { value: 'S', label: 'Sí (Ley 1801)' },
                ]}
                className="w-28 text-xs"
              />
            </div>

            {form.esRazaManejoEspecial === 'S' && (
              <div className="pt-2 border-t border-border">
                <Input
                  label="URL póliza de responsabilidad civil (obligatoria) *"
                  value={form.polizaResponsabilidadUrl}
                  onChange={(e) =>
                    setForm({ ...form, polizaResponsabilidadUrl: e.target.value })
                  }
                  placeholder="https://seguros.com/poliza-123.pdf"
                  error={formErrors.polizaResponsabilidadUrl}
                  required
                />
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <Input
                label="URL carnet de vacunación (opcional)"
                value={form.carnetVacunacionUrl}
                onChange={(e) => setForm({ ...form, carnetVacunacionUrl: e.target.value })}
                placeholder="https://..."
              />
            </div>
            <div>
              <Select
                label="Estado"
                value={form.estado}
                onChange={(e) => setForm({ ...form, estado: e.target.value })}
                options={ESTADOS_MASCOTA}
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
              {saving ? 'Guardando...' : editingPet ? 'Guardar Cambios' : 'Registrar Mascota'}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Confirmar Eliminación */}
      <ConfirmDialog
        open={confirmDelete.open}
        onClose={() => setConfirmDelete({ open: false, pet: null })}
        onConfirm={handleDelete}
        title="¿Remover del censo animal?"
        message={`Esta acción eliminará a ${confirmDelete.pet?.nombre} del censo de mascotas de la unidad.`}
        confirmLabel="Eliminar mascota"
        danger
      />
    </Card>
  );
}
