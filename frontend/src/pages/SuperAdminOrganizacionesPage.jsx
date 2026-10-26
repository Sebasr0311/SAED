import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input as SearchInput } from '../components/ui/input.tsx';
import { Input } from '../components/ui/Form.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { Building, Lock, PauseCircle, PlayCircle, Plus, Search, Trash2 } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog.tsx';
import { toast } from 'sonner';
import LocationSelector from '../components/ui/LocationSelector';

export default function SuperAdminOrganizacionesPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [formErrors, setFormErrors] = useState({});
  const [showModal, setShowModal] = useState(false);
  const [search, setSearch] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [orgToDelete, setOrgToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [deletePin, setDeletePin] = useState('');
  const [pinError, setPinError] = useState('');

  const INITIAL_FORM = {
    nombre: '',
    identificacionFiscal: '',
    emailContacto: '',
    telefonoContacto: '',
    direccion: '',
    departamento: '',
    ciudad: '',
    pais: 'Colombia',
  };

  const [form, setForm] = useState(INITIAL_FORM);

  function resetForm() {
    setForm(INITIAL_FORM);
    setFormErrors({});
  }

  function setField(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
    if (formErrors[field]) setFormErrors((prev) => ({ ...prev, [field]: undefined }));
  }

  function handleOpenChange(open) {
    setShowModal(open);
    if (!open) {
      resetForm();
    }
  }

  async function loadData() {
    try {
      setLoading(true);
      setLoadError(null);
      const res = await api.get('/organizations');
      const list = res?.data || res || [];
      setItems(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar organizaciones');
      setLoadError(err?.message || 'No fue posible consultar las organizaciones.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const filteredItems = useMemo(() => {
    if (!search.trim()) return items;
    const q = search.toLowerCase();
    return items.filter(
      (o) =>
        (o.nombre && o.nombre.toLowerCase().includes(q)) ||
        (o.identificacionFiscal && o.identificacionFiscal.toLowerCase().includes(q)) ||
        (o.emailContacto && o.emailContacto.toLowerCase().includes(q)) ||
        (o.ciudad && o.ciudad.toLowerCase().includes(q))
    );
  }, [items, search]);

  const NIT_REGEX = /^\d{7,10}(-\d)?$/;
  const EMAIL_REGEX = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;

  async function handleCreate(e) {
    e.preventDefault();
    const cleanNit = form.identificacionFiscal.trim();
    if (!form.nombre.trim() || !cleanNit || !form.emailContacto.trim()) {
      setFormErrors({
        nombre: !form.nombre.trim() ? 'El nombre es obligatorio.' : undefined,
        identificacionFiscal: !cleanNit ? 'El NIT es obligatorio.' : undefined,
        emailContacto: !form.emailContacto.trim() ? 'El email es obligatorio.' : undefined,
      });
      toast.error('Por favor completa los campos obligatorios (*)');
      return;
    }
    if (!EMAIL_REGEX.test(form.emailContacto.trim())) {
      setFormErrors({ emailContacto: 'Ingresa un correo válido (ej. nombre@dominio.com).' });
      toast.error('Por favor ingrese un correo electrónico válido (ej. nombre@dominio.com)');
      return;
    }
    if (!NIT_REGEX.test(cleanNit)) {
      setFormErrors({ identificacionFiscal: 'Usa solo números, con dígito de verificación opcional (901234567-8).' });
      toast.error('El NIT debe ser numérico válido (ej. 901234567 o 901234567-8)');
      return;
    }
    if (form.telefonoContacto && form.telefonoContacto.replace(/[^0-9]/g, '').length < 7) {
      setFormErrors({ telefonoContacto: 'El teléfono debe tener al menos 7 dígitos.' });
      toast.error('El teléfono debe tener al menos 7 dígitos');
      return;
    }
    if (!form.departamento || !form.ciudad) {
      toast.error('Por favor selecciona el departamento y ciudad/municipio de ubicación');
      return;
    }

    try {
      setSubmitting(true);
      await api.post('/organizations', {
        nombre: form.nombre.trim(),
        identificacionFiscal: cleanNit,
        emailContacto: form.emailContacto.trim(),
        telefonoContacto: form.telefonoContacto?.trim() || null,
        direccion: form.direccion?.trim() || null,
        ciudad: form.ciudad,
        departamento: form.departamento,
        pais: form.pais || 'Colombia',
      });
      toast.success('Organización creada exitosamente');
      setShowModal(false);
      resetForm();
      loadData();
    } catch (err) {
      console.error(err);
      toast.error('Error al crear la organización');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggleStatus(org) {
    const nextStatus = org.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    try {
      await api.patch(`/organizations/${org.id || org.idOrganizacion}/status`, { estado: nextStatus });
      toast.success(`Organización ${nextStatus === 'ACTIVA' ? 'activada' : 'desactivada'}`);
      loadData();
    } catch (err) {
      console.error(err);
      toast.error('No se pudo actualizar el estado de la organización');
    }
  }

  async function handleDeleteOrg() {
    if (!orgToDelete) return;
    const orgId = orgToDelete.id || orgToDelete.idOrganizacion;
    if (!deletePin || deletePin.trim().length < 4) {
      setPinError('El PIN o clave de seguridad de administrador es requerido (mínimo 4 dígitos)');
      return;
    }

    try {
      setDeleting(true);
      setPinError('');
      await api.post('/auth/verify-pin', { pin: deletePin.trim() });
    } catch (err) {
      setPinError(err.response?.data?.error || err.response?.data?.message || 'Contraseña de administrador incorrecta');
      setDeleting(false);
      return;
    }

    try {
      await api.delete(`/organizations/${orgId}`);
      toast.success(`Organización "${orgToDelete.nombre}" eliminada correctamente`);
      setOrgToDelete(null);
      setDeletePin('');
      setPinError('');
      loadData();
    } catch (err) {
      console.warn('DELETE directo falló, intentando desactivación lógica:', err);
      try {
        await api.patch(`/organizations/${orgId}/status`, { estado: 'INACTIVA' });
        toast.success(`Organización "${orgToDelete.nombre}" inactivada (posee historial asociado)`);
        setOrgToDelete(null);
        setDeletePin('');
        setPinError('');
        loadData();
      } catch (patchErr) {
        console.error(patchErr);
        toast.error(err.response?.data?.message || err.message || 'No se pudo eliminar ni inactivar la organización');
      }
    } finally {
      setDeleting(false);
    }
  }

  function closeDelete() {
    setOrgToDelete(null);
    setDeletePin('');
    setPinError('');
  }

  return (
    <PageContainer>
      <PageHeader
        title="Organizaciones"
        subtitle="Empresas administradoras y constructoras de copropiedades clientes de SAED."
        action={
          <Button onClick={() => setShowModal(true)} className="min-h-10">
            <Plus aria-hidden="true" />
            Nueva organización
          </Button>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative w-full sm:max-w-sm">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <SearchInput
            id="org-search-input"
            type="search"
            aria-label="Buscar organizaciones"
            placeholder="Buscar por nombre, NIT, email o ciudad…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-10 pl-9 text-sm"
          />
        </div>
        <div className="text-xs font-semibold text-text-secondary sm:ml-auto">
          <span className="app-numeral">{filteredItems.length}</span>{' '}
          {filteredItems.length === 1 ? 'organización' : 'organizaciones'}
        </div>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-[15px]">Organizaciones registradas</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2" role="status" aria-busy="true" aria-label="Cargando organizaciones">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-11 w-full" />
              ))}
            </div>
          ) : loadError ? (
            <ErrorState title="No se pudo cargar la lista" message={loadError} onRetry={loadData} />
          ) : filteredItems.length === 0 ? (
            <EmptyState
              icon={Building}
              title={search ? 'Sin coincidencias' : 'No hay organizaciones registradas'}
              subtitle={
                search
                  ? 'No se encontraron organizaciones con ese criterio.'
                  : 'Registra la primera organización cliente para asignarle plan y propiedades.'
              }
            >
              {search ? (
                <Button variant="outline" size="sm" className="mt-3" onClick={() => setSearch('')}>
                  Limpiar búsqueda
                </Button>
              ) : (
                <Button size="sm" className="mt-3" onClick={() => setShowModal(true)}>
                  <Plus aria-hidden="true" />
                  Nueva organización
                </Button>
              )}
            </EmptyState>
          ) : (
            <div className="table-container max-h-[70vh] overflow-y-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Nombre comercial</th>
                    <th scope="col">NIT</th>
                    <th scope="col">Contacto</th>
                    <th scope="col">Ubicación</th>
                    <th scope="col" className="text-center">Estado</th>
                    <th scope="col" className="text-right">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredItems.map((org) => {
                    const activa = (org.estado || 'ACTIVA') === 'ACTIVA';
                    return (
                      <tr key={org.id || org.idOrganizacion}>
                        <td className="font-mono text-xs text-text-secondary tabular-nums">#{org.id || org.idOrganizacion}</td>
                        <td className="min-w-[10rem] font-semibold text-on-surface">{org.nombre}</td>
                        <td className="whitespace-nowrap font-mono text-xs text-text-secondary">
                          {org.identificacionFiscal || org.nit || '—'}
                        </td>
                        <td>
                          <div className="flex flex-col">
                            <span className="text-xs text-on-surface">{org.emailContacto || org.email || '—'}</span>
                            {org.telefonoContacto && (
                              <span className="font-mono text-xs text-text-muted">{org.telefonoContacto}</span>
                            )}
                          </div>
                        </td>
                        <td className="text-xs text-text-secondary">
                          {org.ciudad ? `${org.ciudad}, ${org.pais || 'Colombia'}` : org.pais || 'Colombia'}
                        </td>
                        <td className="text-center">
                          <Badge variant={activa ? 'success' : 'secondary'}>{org.estado || 'ACTIVA'}</Badge>
                        </td>
                        <td className="text-right">
                          <div className="flex items-center justify-end gap-1">
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleToggleStatus(org)}
                              title={org.estado === 'ACTIVA' ? 'Desactivar organización' : 'Activar organización'}
                              aria-label={`${org.estado === 'ACTIVA' ? 'Desactivar' : 'Activar'} ${org.nombre}`}
                              className="h-10 w-10"
                            >
                              {org.estado === 'ACTIVA' ? (
                                <PauseCircle className="text-tint-amber-fg" aria-hidden="true" />
                              ) : (
                                <PlayCircle className="text-tint-green-fg" aria-hidden="true" />
                              )}
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => setOrgToDelete(org)}
                              title="Eliminar organización"
                              aria-label={`Eliminar ${org.nombre}`}
                              className="h-10 w-10 hover:bg-tint-red-bg hover:text-destructive"
                            >
                              <Trash2 aria-hidden="true" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={showModal} onOpenChange={handleOpenChange}>
        <DialogContent className="max-h-[90dvh] max-w-lg overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Registrar organización</DialogTitle>
            <DialogDescription>Los campos marcados con * son obligatorios.</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreate} className="space-y-4 pt-1" noValidate>
            <Input
              id="org-nombre"
              label="Nombre comercial o razón social"
              required
              value={form.nombre}
              onChange={(e) => setField('nombre', e.target.value)}
              placeholder="Ej. Inversiones Residenciales S.A.S."
              error={formErrors.nombre}
            />

            <Input
              id="org-nit"
              label="NIT"
              required
              value={form.identificacionFiscal}
              onChange={(e) => setField('identificacionFiscal', e.target.value)}
              placeholder="Ej. 901234567-8"
              inputMode="numeric"
              error={formErrors.identificacionFiscal}
              className="font-mono"
            />

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Input
                id="org-email"
                label="Email de contacto"
                type="email"
                required
                value={form.emailContacto}
                onChange={(e) => setField('emailContacto', e.target.value)}
                placeholder="admin@empresa.com"
                error={formErrors.emailContacto}
              />

              <Input
                id="org-tel"
                label="Teléfono"
                type="tel"
                value={form.telefonoContacto}
                onChange={(e) => setField('telefonoContacto', e.target.value.replace(/[^0-9+\s()-]/g, '').slice(0, 20))}
                placeholder="+57 300 123 4567"
                error={formErrors.telefonoContacto}
              />
            </div>

            <LocationSelector
              idPrefix="org"
              pais={form.pais}
              departamento={form.departamento}
              ciudad={form.ciudad}
              onChange={({ pais, departamento, ciudad }) =>
                setForm((prev) => ({ ...prev, pais, departamento, ciudad }))
              }
            />

            <Input
              id="org-dir"
              label="Dirección comercial"
              value={form.direccion}
              onChange={(e) => setField('direccion', e.target.value)}
              placeholder="Calle 100 # 15-20 Of. 401"
            />

            <DialogFooter className="gap-2 pt-3">
              <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting ? 'Guardando…' : 'Crear organización'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={Boolean(orgToDelete)} onOpenChange={(open) => !open && closeDelete()}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>¿Eliminar organización?</DialogTitle>
            <DialogDescription>
              Se eliminará o inactivará la organización{' '}
              <strong className="text-on-surface">"{orgToDelete?.nombre}"</strong> (NIT:{' '}
              {orgToDelete?.identificacionFiscal || orgToDelete?.nit}). Si tiene historial asociado, quedará inactiva.
            </DialogDescription>
          </DialogHeader>

          <form
            className="space-y-2 py-1"
            onSubmit={(e) => {
              e.preventDefault();
              if (!deleting && deletePin.trim().length >= 4) handleDeleteOrg();
            }}
          >
            <label htmlFor="org-delete-pin" className="flex items-center gap-1.5 text-sm font-semibold text-on-surface">
              <Lock className="h-3.5 w-3.5 text-text-secondary" aria-hidden="true" />
              Contraseña de administrador
            </label>
            <SearchInput
              id="org-delete-pin"
              type="password"
              placeholder="Ingresa tu contraseña de administrador"
              value={deletePin}
              maxLength={50}
              autoComplete="current-password"
              aria-invalid={Boolean(pinError)}
              aria-describedby={pinError ? 'org-delete-pin-error' : 'org-delete-pin-hint'}
              onChange={(e) => {
                setDeletePin(e.target.value);
                setPinError('');
              }}
              className="text-sm"
              autoFocus
            />
            {pinError && (
              <p id="org-delete-pin-error" role="alert" className="text-xs font-semibold text-destructive">
                {pinError}
              </p>
            )}
            <p id="org-delete-pin-hint" className="text-xs text-text-muted">
              Acción protegida: quedará registrada en la bitácora de auditoría.
            </p>
          </form>

          <DialogFooter className="gap-2 pt-2 sm:gap-0">
            <Button variant="outline" onClick={closeDelete} disabled={deleting}>
              Cancelar
            </Button>
            <Button variant="destructive" onClick={handleDeleteOrg} disabled={deleting || deletePin.trim().length < 4}>
              {deleting ? 'Eliminando…' : 'Sí, eliminar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
