import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
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
  const [showModal, setShowModal] = useState(false);
  const [search, setSearch] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [orgToDelete, setOrgToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

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
      const res = await api.get('/organizations');
      const list = res?.data || res || [];
      setItems(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar organizaciones');
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
      toast.error('Por favor completa los campos obligatorios (*)');
      return;
    }
    if (!EMAIL_REGEX.test(form.emailContacto.trim())) {
      toast.error('Por favor ingrese un correo electrónico válido (ej. nombre@dominio.com)');
      return;
    }
    if (!NIT_REGEX.test(cleanNit)) {
      toast.error('El NIT debe ser numérico válido (ej. 901234567 o 901234567-8)');
      return;
    }
    if (form.telefonoContacto && form.telefonoContacto.replace(/[^0-9]/g, '').length < 7) {
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
    try {
      setDeleting(true);
      await api.delete(`/organizations/${orgId}`);
      toast.success(`Organización "${orgToDelete.nombre}" eliminada correctamente`);
      setOrgToDelete(null);
      loadData();
    } catch (err) {
      console.warn('DELETE directo falló, intentando desactivación lógica:', err);
      try {
        await api.patch(`/organizations/${orgId}/status`, { estado: 'INACTIVA' });
        toast.success(`Organización "${orgToDelete.nombre}" inactivada (posee historial asociado)`);
        setOrgToDelete(null);
        loadData();
      } catch (patchErr) {
        console.error(patchErr);
        toast.error(err.response?.data?.message || err.message || 'No se pudo eliminar ni inactivar la organización');
      }
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-2xl">domain</span>
            Organizaciones SaaS
          </h1>
          <p className="text-muted-foreground mt-1 text-sm">
            Empresas administradoras y constructoras de copropiedades clientes de SAED.
          </p>
        </div>
        <Button onClick={() => setShowModal(true)} className="gap-2 shrink-0">
          <span className="material-symbols-outlined text-sm">add_business</span>
          Nueva Organización
        </Button>
      </div>

      {/* Barra de Filtros */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
            search
          </span>
          <Input
            id="org-search-input"
            type="search"
            placeholder="Buscar por nombre, NIT, email o ciudad…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9 text-sm"
          />
        </div>
        <div className="text-xs text-muted-foreground ml-auto font-medium">
          {filteredItems.length} {filteredItems.length === 1 ? 'organización' : 'organizaciones'}
        </div>
      </div>

      {/* Listado */}
      <Card className="border-border/80 shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base font-semibold">Listado de Organizaciones Registradas</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-14 w-full rounded-lg" />
              ))}
            </div>
          ) : filteredItems.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground space-y-2">
              <span className="material-symbols-outlined text-4xl text-muted-foreground/60">domain_disabled</span>
              <p className="text-sm font-medium">
                {search ? 'No se encontraron organizaciones con ese criterio.' : 'No hay organizaciones registradas en la plataforma.'}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm border-collapse">
                <thead>
                  <tr className="border-b border-border text-muted-foreground text-xs font-semibold uppercase tracking-wider">
                    <th className="py-3 px-4">ID</th>
                    <th className="py-3 px-4">Nombre Comercial</th>
                    <th className="py-3 px-4">NIT / ID Fiscal</th>
                    <th className="py-3 px-4">Contacto</th>
                    <th className="py-3 px-4">Ubicación</th>
                    <th className="py-3 px-4 text-center">Estado</th>
                    <th className="py-3 px-4 text-right">Acciones</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {filteredItems.map((org) => (
                    <tr key={org.id || org.idOrganizacion} className="hover:bg-muted/40 transition-colors">
                      <td className="py-3.5 px-4 font-mono text-xs text-muted-foreground">#{org.id || org.idOrganizacion}</td>
                      <td className="py-3.5 px-4 font-semibold text-foreground">{org.nombre}</td>
                      <td className="py-3.5 px-4 text-muted-foreground font-mono text-xs">{org.identificacionFiscal || org.nit || '—'}</td>
                      <td className="py-3.5 px-4">
                        <div className="flex flex-col">
                          <span className="text-foreground text-xs">{org.emailContacto || org.email || '—'}</span>
                          {org.telefonoContacto && (
                            <span className="text-muted-foreground text-[11px] font-mono">{org.telefonoContacto}</span>
                          )}
                        </div>
                      </td>
                      <td className="py-3.5 px-4 text-xs text-muted-foreground">
                        {org.ciudad ? `${org.ciudad}, ${org.pais || 'Colombia'}` : org.pais || 'Colombia'}
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        <Badge
                          variant={org.estado === 'ACTIVA' ? 'default' : 'secondary'}
                          className={
                            org.estado === 'ACTIVA'
                              ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 text-xs'
                              : 'text-xs'
                          }
                        >
                          {org.estado || 'ACTIVA'}
                        </Badge>
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleToggleStatus(org)}
                            title={org.estado === 'ACTIVA' ? 'Desactivar organización' : 'Activar organización'}
                            className="h-8 px-2 text-xs"
                          >
                            <span
                              className={`material-symbols-outlined text-base ${
                                org.estado === 'ACTIVA' ? 'text-amber-500' : 'text-emerald-500'
                              }`}
                            >
                              {org.estado === 'ACTIVA' ? 'pause_circle' : 'play_circle'}
                            </span>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setOrgToDelete(org)}
                            title="Eliminar organización"
                            className="h-8 px-2 text-xs text-muted-foreground hover:text-rose-600 hover:bg-rose-500/10"
                          >
                            <span className="material-symbols-outlined text-base">delete</span>
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Modal Accesible con Radix UI Dialog */}
      <Dialog open={showModal} onOpenChange={handleOpenChange}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg font-bold">
              <span className="material-symbols-outlined text-primary">add_business</span>
              Registrar Organización SaaS
            </DialogTitle>
          </DialogHeader>

          <form onSubmit={handleCreate} className="space-y-4 pt-2">
            <div className="space-y-1.5">
              <Label htmlFor="org-nombre" className="text-xs font-semibold uppercase text-muted-foreground">
                Nombre Comercial / Razón Social *
              </Label>
              <Input
                id="org-nombre"
                required
                value={form.nombre}
                onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                placeholder="Ej. Inversiones Residenciales S.A.S."
                className="text-sm"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="org-nit" className="text-xs font-semibold uppercase text-muted-foreground">
                NIT / Identificación Fiscal *
              </Label>
              <Input
                id="org-nit"
                required
                value={form.identificacionFiscal}
                onChange={(e) => setForm({ ...form, identificacionFiscal: e.target.value })}
                placeholder="Ej. 901234567-8"
                className="text-sm font-mono"
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="org-email" className="text-xs font-semibold uppercase text-muted-foreground">
                  Email de Contacto *
                </Label>
                <Input
                  id="org-email"
                  type="email"
                  required
                  value={form.emailContacto}
                  onChange={(e) => setForm({ ...form, emailContacto: e.target.value })}
                  placeholder="admin@empresa.com"
                  className="text-sm"
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="org-tel" className="text-xs font-semibold uppercase text-muted-foreground">
                  Teléfono
                </Label>
                <Input
                  id="org-tel"
                  value={form.telefonoContacto}
                  onChange={(e) => setForm({ ...form, telefonoContacto: e.target.value.replace(/[^0-9+\s()\-]/g, '').slice(0, 20) })}
                  placeholder="+57 300 123 4567"
                  className="text-sm"
                />
              </div>
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

            <div className="space-y-1.5">
              <Label htmlFor="org-dir" className="text-xs font-semibold uppercase text-muted-foreground">
                Dirección Comercial
              </Label>
              <Input
                id="org-dir"
                value={form.direccion}
                onChange={(e) => setForm({ ...form, direccion: e.target.value })}
                placeholder="Calle 100 # 15-20 Of. 401"
                className="text-sm"
              />
            </div>

            <DialogFooter className="pt-3 gap-2">
              <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={submitting} className="gap-2">
                {submitting ? 'Guardando…' : 'Crear Organización'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Modal de confirmación para eliminar organización */}
      <Dialog open={Boolean(orgToDelete)} onOpenChange={(open) => !open && setOrgToDelete(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-foreground font-semibold">
              <span className="material-symbols-outlined text-rose-500">delete</span>
              ¿Eliminar Organización?
            </DialogTitle>
            <DialogDescription className="text-muted-foreground text-sm">
              ¿Estás seguro de que deseas eliminar o inactivar la organización{' '}
              <strong className="text-foreground">"{orgToDelete?.nombre}"</strong> (NIT: {orgToDelete?.identificacionFiscal || orgToDelete?.nit})?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="gap-2 sm:gap-0 pt-2">
            <Button variant="outline" onClick={() => setOrgToDelete(null)} disabled={deleting}>
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteOrg}
              disabled={deleting}
              className="bg-rose-600 hover:bg-rose-700 text-white"
            >
              {deleting ? 'Eliminando…' : 'Sí, eliminar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
