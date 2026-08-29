import { useState } from 'react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from '../components/ui/dialog.tsx';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '../components/ui/table.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';

/**
 * OrganizacionesPage 2.0 — CRUD de organizaciones (tenants).
 *
 * Consume /api/v1/organizations (GET/POST/PUT + PATCH /{id}/status) con
 * useTenantApi. Solo SUPERADMIN deberia poder gestionar organizaciones; el
 * backend ya protege con RLS/scope.
 */
const ESTADO_BADGE = {
  ACTIVA: 'default',
  SUSPENDIDA: 'warning',
  INACTIVA: 'secondary',
};

const emptyForm = {
  nombre: '',
  identificacionFiscal: '',
  emailContacto: '',
  telefonoContacto: '',
  direccion: '',
  ciudad: '',
};

export default function OrganizacionesPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const { data, loading, refetch } = useFetch(
    () => tenantApi.get('/organizations'),
    [tenant.activeAssignmentId]
  );

  const organizaciones = data?.items || [];

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);

  async function guardar() {
    if (!form.nombre.trim() || !form.identificacionFiscal.trim() || !form.emailContacto.trim()) {
      toast.error('Nombre, identificaci\u00f3n fiscal y email son obligatorios');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        nombre: form.nombre.trim(),
        identificacionFiscal: form.identificacionFiscal.trim(),
        emailContacto: form.emailContacto.trim(),
        telefonoContacto: form.telefonoContacto.trim() || null,
        direccion: form.direccion.trim() || null,
        ciudad: form.ciudad.trim() || null,
      };
      if (editing) {
        await tenantApi.put(`/organizations/${editing.id}`, payload);
        toast.success('Organizaci\u00f3n actualizada');
      } else {
        await tenantApi.post('/organizations', payload);
        toast.success('Organizaci\u00f3n creada');
      }
      setDialogOpen(false);
      refetch();
    } catch (err) {
      toast.error(err.message || 'No se pudo guardar la organizaci\u00f3n');
    } finally {
      setSaving(false);
    }
  }

  async function cambiarEstado(org, nuevoEstado) {
    try {
      await tenantApi.patch(`/organizations/${org.id}/status`, { estado: nuevoEstado });
      toast.success(`Organizaci\u00f3n ${nuevoEstado === 'ACTIVA' ? 'activada' : nuevoEstado === 'SUSPENDIDA' ? 'suspendida' : 'desactivada'}`);
      refetch();
    } catch (err) {
      toast.error(err.message || 'No se pudo cambiar el estado');
    }
  }

  const activas = organizaciones.filter((o) => o.estado === 'ACTIVA').length;

  return (
    <div className="organizaciones-page space-y-6">
      <PageHeader
        title="Organizaciones"
        subtitle="Tenants del SaaS: alta, edici\u00f3n y estado"
      >
        <Button onClick={() => { setEditing(null); setForm(emptyForm); setDialogOpen(true); }}>
          <span className="material-symbols-outlined text-base mr-1">add</span>
          Nueva Organizaci\u00f3n
        </Button>
      </PageHeader>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Card>
          <CardContent className="flex items-center gap-3 pt-6">
            <div className="rounded-lg bg-primary/10 p-2.5 text-primary">
              <span className="material-symbols-outlined">domain</span>
            </div>
            <div>
              <p className="text-2xl font-bold">{organizaciones.length}</p>
              <p className="text-sm text-muted-foreground">Organizaciones</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-3 pt-6">
            <div className="rounded-lg bg-green-500/10 p-2.5 text-green-600">
              <span className="material-symbols-outlined">check_circle</span>
            </div>
            <div>
              <p className="text-2xl font-bold">{activas}</p>
              <p className="text-sm text-muted-foreground">Activas</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-3 pt-6">
            <div className="rounded-lg bg-amber-500/10 p-2.5 text-amber-600">
              <span className="material-symbols-outlined">warning</span>
            </div>
            <div>
              <p className="text-2xl font-bold">{organizaciones.length - activas}</p>
              <p className="text-sm text-muted-foreground">No activas</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Listado de Organizaciones</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : organizaciones.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">No hay organizaciones registradas.</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nombre</TableHead>
                    <TableHead>NIT</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Ciudad</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {organizaciones.map((o) => (
                    <TableRow key={o.id}>
                      <TableCell className="font-medium">{o.nombre}</TableCell>
                      <TableCell>{o.identificacionFiscal}</TableCell>
                      <TableCell>{o.emailContacto}</TableCell>
                      <TableCell>{o.ciudad || '—'}</TableCell>
                      <TableCell>
                        <Badge variant={ESTADO_BADGE[o.estado] || 'default'}>{o.estado}</Badge>
                      </TableCell>
                      <TableCell className="text-right whitespace-nowrap">
                        <Button variant="ghost" size="sm" onClick={() => { setEditing(o); setForm({ nombre: o.nombre || '', identificacionFiscal: o.identificacionFiscal || '', emailContacto: o.emailContacto || '', telefonoContacto: o.telefonoContacto || '', direccion: o.direccion || '', ciudad: o.ciudad || '' }); setDialogOpen(true); }} aria-label={`Editar ${o.nombre}`}>
                          <span className="material-symbols-outlined text-base">edit</span>
                        </Button>
                        {o.estado === 'ACTIVA' ? (
                          <Button variant="ghost" size="sm" onClick={() => cambiarEstado(o, 'SUSPENDIDA')} aria-label={`Suspender ${o.nombre}`}>
                            <span className="material-symbols-outlined text-base text-amber-600">pause_circle</span>
                          </Button>
                        ) : (
                          <Button variant="ghost" size="sm" onClick={() => cambiarEstado(o, 'ACTIVA')} aria-label={`Activar ${o.nombre}`}>
                            <span className="material-symbols-outlined text-base text-green-600">play_circle</span>
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editing ? 'Editar Organizaci\u00f3n' : 'Nueva Organizaci\u00f3n'}</DialogTitle>
            <DialogDescription>
              {editing ? `Actualice los datos de ${editing.nombre}.` : 'Registre un nuevo tenant en la plataforma.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-2">
              <Label htmlFor="org-nombre">Nombre *</Label>
              <Input id="org-nombre" value={form.nombre} onChange={(e) => setForm((f) => ({ ...f, nombre: e.target.value }))} placeholder="Ej: Conjunto Residencial Horizonte" />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="org-nit">Identificaci\u00f3n fiscal *</Label>
                <Input id="org-nit" value={form.identificacionFiscal} onChange={(e) => setForm((f) => ({ ...f, identificacionFiscal: e.target.value }))} placeholder="Ej: NIT-900000001-1" />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="org-email">Email contacto *</Label>
                <Input id="org-email" type="email" value={form.emailContacto} onChange={(e) => setForm((f) => ({ ...f, emailContacto: e.target.value }))} placeholder="admin@tenant.com" />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="org-tel">Tel\u00e9fono</Label>
                <Input id="org-tel" value={form.telefonoContacto} onChange={(e) => setForm((f) => ({ ...f, telefonoContacto: e.target.value }))} placeholder="Ej: 3001234567" />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="org-ciudad">Ciudad</Label>
                <Input id="org-ciudad" value={form.ciudad} onChange={(e) => setForm((f) => ({ ...f, ciudad: e.target.value }))} placeholder="Ej: Bogot\u00e1" />
              </div>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="org-dir">Direcci\u00f3n</Label>
              <Input id="org-dir" value={form.direccion} onChange={(e) => setForm((f) => ({ ...f, direccion: e.target.value }))} placeholder="Ej: Av 123 #45-67" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancelar</Button>
            <Button onClick={guardar} disabled={saving}>
              {saving ? 'Guardando\u2026' : editing ? 'Guardar cambios' : 'Crear organizaci\u00f3n'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}