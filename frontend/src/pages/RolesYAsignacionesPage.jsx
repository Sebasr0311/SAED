import { useEffect, useState } from 'react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from '../components/ui/dialog.tsx';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '../components/ui/table.tsx';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../components/ui/select.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';

/**
 * RolesYAsignacionesPage 2.0 — asignaciones de rol por usuario.
 *
 * Muestra las asignaciones del usuario autenticado (GET /auth/assignments)
 * y permite crear nuevas (POST /assignments) + cambiar estado
 * (PATCH /assignments/{id}/status). El backend valida anti-escalada y
 * constraints de scope (CK_ROLES_ALCANCE).
 */
const SCOPE_BADGE = {
  GLOBAL: 'default',
  ORGANIZACION: 'secondary',
  PROPIEDAD: 'warning',
  PROPIEDADES_SELECCIONADAS: 'warning',
  UNIDAD: 'outline',
};

const ESTADO_BADGE = {
  ACTIVA: 'default',
  INACTIVA: 'secondary',
};

const emptyForm = {
  idUsuario: '',
  idRol: '',
  idOrganizacion: '',
  idPropiedad: '',
  idUnidad: '',
};

export default function RolesYAsignacionesPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const { data, loading, refetch } = useFetch(
    () => tenantApi.get('/auth/assignments'),
    [tenant.activeAssignmentId]
  );
  const { data: rolesData } = useFetch(() => tenantApi.get('/roles'), [tenant.activeAssignmentId]);
  const { data: usuariosData } = useFetch(() => tenantApi.get('/usuarios'), [tenant.activeAssignmentId]);
  const { data: orgsData } = useFetch(() => tenantApi.get('/organizations'), [tenant.activeAssignmentId]);
  const { data: propsData } = useFetch(() => tenantApi.get('/properties'), [tenant.activeAssignmentId]);
  const { data: unitsData } = useFetch(() => tenantApi.get('/units'), [tenant.activeAssignmentId]);

  const asignaciones = data?.data || data?.items || [];
  const roles = rolesData?.items || [];
  const usuarios = usuariosData?.items || [];
  const organizaciones = orgsData?.items || [];
  const propiedades = propsData?.items || [];
  const unidades = unitsData?.items || [];

  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);

  const rolSeleccionado = roles.find((r) => String(r.idRol) === form.idRol) || roles.find((r) => String(r.id_rol) === form.idRol);

  // Campos visibles segun el alcance del rol seleccionado
  const alcance = rolSeleccionado?.alcance || rolSeleccionado?.ALCANCE || '';

  async function guardar() {
    if (!form.idUsuario || !form.idRol) {
      toast.error('Seleccione usuario y rol');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        idUsuario: Number(form.idUsuario),
        idRol: Number(form.idRol),
        idOrganizacion: form.idOrganizacion ? Number(form.idOrganizacion) : null,
        idPropiedad: form.idPropiedad ? Number(form.idPropiedad) : null,
        idUnidad: form.idUnidad ? Number(form.idUnidad) : null,
      };
      await tenantApi.post('/assignments', payload);
      toast.success('Asignaci\u00f3n creada');
      setDialogOpen(false);
      setForm(emptyForm);
      refetch();
    } catch (err) {
      toast.error(err.message || 'No se pudo crear la asignaci\u00f3n');
    } finally {
      setSaving(false);
    }
  }

  async function cambiarEstado(asig, nuevo) {
    try {
      await tenantApi.patch(`/assignments/${asig.idAsignacion}/status`, { estado: nuevo });
      toast.success(nuevo === 'ACTIVA' ? 'Asignaci\u00f3n activada' : 'Asignaci\u00f3n desactivada');
      refetch();
    } catch (err) {
      toast.error(err.message || 'No se pudo cambiar el estado');
    }
  }

  const necesitaOrg = ['ORGANIZACION', 'PROPIEDADES_SELECCIONADAS', 'PROPIEDAD', 'UNIDAD'].includes(alcance);
  const necesitaProp = ['PROPIEDAD', 'UNIDAD'].includes(alcance);
  const necesitaUnidad = alcance === 'UNIDAD';

  return (
    <div className="roles-page space-y-6">
      <PageHeader
        title="Roles y Asignaciones"
        subtitle="Asigne roles a usuarios seg\u00fan organizaci\u00f3n / propiedad / unidad"
      >
        <Button onClick={() => { setForm(emptyForm); setDialogOpen(true); }}>
          <span className="material-symbols-outlined text-base mr-1">add</span>
          Nueva Asignaci\u00f3n
        </Button>
      </PageHeader>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Mis Asignaciones</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2"><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /></div>
          ) : asignaciones.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">No hay asignaciones.</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Rol</TableHead>
                    <TableHead>Alcance</TableHead>
                    <TableHead>Org</TableHead>
                    <TableHead>Prop</TableHead>
                    <TableHead>Unidad</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {asignaciones.map((a) => (
                    <TableRow key={a.idAsignacion}>
                      <TableCell className="font-medium">{a.rol?.codigo || a.roleCode || a.rol}</TableCell>
                      <TableCell>
                        <Badge variant={SCOPE_BADGE[a.rol?.alcance || a.scope] || 'default'}>
                          {a.rol?.alcance || a.scope}
                        </Badge>
                      </TableCell>
                      <TableCell>{a.organizacion?.nombre || a.idOrganizacion || '—'}</TableCell>
                      <TableCell>{a.propiedad?.nombre || a.idPropiedad || '—'}</TableCell>
                      <TableCell>{a.unidad?.identificador || a.idUnidad || '—'}</TableCell>
                      <TableCell>
                        <Badge variant={ESTADO_BADGE[a.estado] || 'default'}>{a.estado || 'ACTIVA'}</Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        {a.estado === 'ACTIVA' ? (
                          <Button variant="ghost" size="sm" onClick={() => cambiarEstado(a, 'INACTIVA')} aria-label="Desactivar">
                            <span className="material-symbols-outlined text-base text-amber-600">pause_circle</span>
                          </Button>
                        ) : (
                          <Button variant="ghost" size="sm" onClick={() => cambiarEstado(a, 'ACTIVA')} aria-label="Activar">
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
            <DialogTitle>Nueva Asignaci\u00f3n de Rol</DialogTitle>
            <DialogDescription>
              Asigne un rol a un usuario. Los campos dependen del alcance del rol.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label>Usuario *</Label>
                <Select value={form.idUsuario} onValueChange={(v) => setForm((f) => ({ ...f, idUsuario: v }))}>
                  <SelectTrigger><SelectValue placeholder="Seleccione" /></SelectTrigger>
                  <SelectContent>
                    {usuarios.map((u) => (
                      <SelectItem key={u.idUsuario || u.id} value={String(u.idUsuario || u.id)}>
                        {u.nombreUsuario || u.nombre_usuario}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label>Rol *</Label>
                <Select value={form.idRol} onValueChange={(v) => setForm((f) => ({ ...f, idRol: v, idOrganizacion: '', idPropiedad: '', idUnidad: '' }))}>
                  <SelectTrigger><SelectValue placeholder="Seleccione" /></SelectTrigger>
                  <SelectContent>
                    {roles.map((r) => (
                      <SelectItem key={r.idRol || r.id_rol} value={String(r.idRol || r.id_rol)}>
                        {r.nombre || r.NOMBRE} ({r.codigo || r.CODIGO})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            {alcance && (
              <p className="text-sm text-muted-foreground">
                Alcance del rol: <Badge variant="outline">{alcance}</Badge>
              </p>
            )}

            {necesitaOrg && (
              <div className="grid gap-2">
                <Label>Organizaci\u00f3n *</Label>
                <Select value={form.idOrganizacion} onValueChange={(v) => setForm((f) => ({ ...f, idOrganizacion: v }))}>
                  <SelectTrigger><SelectValue placeholder="Seleccione" /></SelectTrigger>
                  <SelectContent>
                    {organizaciones.map((o) => (
                      <SelectItem key={o.id} value={String(o.id)}>{o.nombre}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            {necesitaProp && (
              <div className="grid gap-2">
                <Label>Propiedad *</Label>
                <Select value={form.idPropiedad} onValueChange={(v) => setForm((f) => ({ ...f, idPropiedad: v }))}>
                  <SelectTrigger><SelectValue placeholder="Seleccione" /></SelectTrigger>
                  <SelectContent>
                    {propiedades.map((p) => (
                      <SelectItem key={p.id} value={String(p.id)}>{p.nombre}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            {necesitaUnidad && (
              <div className="grid gap-2">
                <Label>Unidad *</Label>
                <Select value={form.idUnidad} onValueChange={(v) => setForm((f) => ({ ...f, idUnidad: v }))}>
                  <SelectTrigger><SelectValue placeholder="Seleccione" /></SelectTrigger>
                  <SelectContent>
                    {unidades.map((u) => (
                      <SelectItem key={u.id} value={String(u.id)}>{u.identificador}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancelar</Button>
            <Button onClick={guardar} disabled={saving}>
              {saving ? 'Guardando\u2026' : 'Crear asignaci\u00f3n'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}