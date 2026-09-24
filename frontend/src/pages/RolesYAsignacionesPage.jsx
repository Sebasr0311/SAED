import { useState } from 'react';
import { useAuth } from '../lib/AuthContext.jsx';
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
import { Label } from '../components/ui/label.tsx';
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
  const { user } = useAuth();
  const esSuperAdmin = user?.rol === 'SUPERADMIN' || user?.rol === 'ROLE_SUPERADMIN';

  const { data, loading, refetch } = useFetch(
    () => tenantApi.get('/assignments'),
    [tenant.activeAssignmentId]
  );
  const [filtro, setFiltro] = useState('');
  const { data: rolesData } = useFetch(() => tenantApi.get('/roles'), [tenant.activeAssignmentId]);
  const { data: usuariosData } = useFetch(() => tenantApi.get('/usuarios'), [tenant.activeAssignmentId]);
  const { data: orgsData } = useFetch(
    () => (esSuperAdmin ? tenantApi.get('/organizations') : Promise.resolve([])),
    [tenant.activeAssignmentId, esSuperAdmin]
  );
  const { data: propsData } = useFetch(() => tenantApi.get('/properties'), [tenant.activeAssignmentId]);
  const { data: unitsData } = useFetch(() => tenantApi.get('/units'), [tenant.activeAssignmentId]);

  const asignaciones = Array.isArray(data?.data) ? data.data : Array.isArray(data) ? data : data?.items || [];
  const roles = Array.isArray(rolesData) ? rolesData : rolesData?.items || [];
  const usuarios = Array.isArray(usuariosData) ? usuariosData : usuariosData?.items || [];
  const organizaciones = Array.isArray(orgsData) ? orgsData : orgsData?.items || [];
  const propiedades = Array.isArray(propsData) ? propsData : propsData?.items || [];
  const unidades = Array.isArray(unitsData) ? unitsData : unitsData?.items || [];

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

  async function cambiarEstado(asigOrId, nuevo) {
    const id = typeof asigOrId === 'object' ? (asigOrId.idAsignacion || asigOrId.ID_ASIGNACION) : asigOrId;
    try {
      await tenantApi.patch(`/assignments/${id}/status`, { estado: nuevo });
      toast.success(nuevo === 'ACTIVA' ? 'Asignación activada' : 'Asignación desactivada');
      refetch();
    } catch (err) {
      toast.error(err.message || 'No se pudo cambiar el estado');
    }
  }

  const necesitaOrg = ['ORGANIZACION', 'PROPIEDADES_SELECCIONADAS', 'PROPIEDAD', 'UNIDAD'].includes(alcance);
  const necesitaProp = ['PROPIEDAD', 'UNIDAD'].includes(alcance);
  const necesitaUnidad = alcance === 'UNIDAD';

  const asignacionesFiltradas = asignaciones.filter((a) => {
    if (!filtro) return true;
    const term = filtro.toLowerCase();
    const usuario = String(a.nombreUsuario || a.NOMBRE_USUARIO || a.username || '').toLowerCase();
    const nombre = String(a.nombreCompleto || a.NOMBRE_COMPLETO || '').toLowerCase();
    const email = String(a.email || a.EMAIL || '').toLowerCase();
    const rol = String(a.rolCodigo || a.ROL_CODIGO || a.rol?.codigo || a.roleCode || a.rol || '').toLowerCase();
    return usuario.includes(term) || nombre.includes(term) || email.includes(term) || rol.includes(term);
  });

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
        <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <CardTitle className="text-base">Asignaciones y Roles de Usuarios</CardTitle>
            <p className="text-xs text-muted-foreground mt-0.5">
              Gestión de roles y alcances asignados a los usuarios del sistema
            </p>
          </div>
          <div className="w-full sm:w-64">
            <input
              type="text"
              placeholder="Buscar por usuario o rol..."
              value={filtro}
              onChange={(e) => setFiltro(e.target.value)}
              className="w-full px-3 py-1.5 text-xs rounded-md border border-input bg-background focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2"><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /></div>
          ) : asignacionesFiltradas.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">
              {filtro ? 'No se encontraron asignaciones que coincidan con la búsqueda.' : 'No hay asignaciones registradas.'}
            </p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Usuario</TableHead>
                    <TableHead>Rol</TableHead>
                    <TableHead>Alcance</TableHead>
                    <TableHead>Propiedad</TableHead>
                    <TableHead>Unidad</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {asignacionesFiltradas.map((a) => {
                    const idAsig = a.idAsignacion || a.ID_ASIGNACION;
                    const userName = a.nombreUsuario || a.NOMBRE_USUARIO || a.username || '—';
                    const fullName = a.nombreCompleto || a.NOMBRE_COMPLETO || '';
                    const email = a.email || a.EMAIL || '';
                    const rolCod = a.rolCodigo || a.ROL_CODIGO || a.rol?.codigo || a.roleCode || a.rol;
                    const rolAlc = a.rolAlcance || a.ROL_ALCANCE || a.rol?.alcance || a.scope;
                    const propNom = a.propiedadNombre || a.PROPIEDAD_NOMBRE || a.propiedad?.nombre || a.idPropiedad || '—';
                    const unitNom = a.unidadIdentificador || a.UNIDAD_IDENTIFICADOR || a.unidad?.identificador || a.idUnidad || '—';
                    const est = a.estado || a.ESTADO || 'ACTIVA';

                    return (
                      <TableRow key={idAsig || Math.random()}>
                        <TableCell>
                          <div className="font-medium text-sm text-foreground">{userName}</div>
                          {fullName && <div className="text-xs text-muted-foreground">{fullName}</div>}
                          {email && <div className="text-xs text-muted-foreground/80">{email}</div>}
                        </TableCell>
                        <TableCell>
                          <span className="font-semibold text-primary text-xs">{rolCod}</span>
                        </TableCell>
                        <TableCell>
                          <Badge variant={SCOPE_BADGE[rolAlc] || 'default'}>
                            {rolAlc}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-sm">{propNom}</TableCell>
                        <TableCell className="text-sm">{unitNom}</TableCell>
                        <TableCell>
                          <Badge variant={ESTADO_BADGE[est] || 'default'}>{est}</Badge>
                        </TableCell>
                        <TableCell className="text-right">
                          {est === 'ACTIVA' ? (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => cambiarEstado(idAsig, 'INACTIVA')}
                              title="Desactivar asignación"
                              aria-label="Desactivar asignación"
                              className="text-xs h-8 gap-1.5 border-amber-500/30 text-amber-600 dark:text-amber-400 hover:bg-amber-500/10"
                            >
                              <span className="material-symbols-outlined text-sm">pause_circle</span>
                              <span>Desactivar</span>
                            </Button>
                          ) : (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => cambiarEstado(idAsig, 'ACTIVA')}
                              title="Activar asignación"
                              aria-label="Activar asignación"
                              className="text-xs h-8 gap-1.5 border-emerald-500/30 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/10"
                            >
                              <span className="material-symbols-outlined text-sm">play_circle</span>
                              <span>Activar</span>
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
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
                    {roles
                      .filter((r) => {
                        const rolCodigo = r.codigo || r.CODIGO;
                        if (user?.rol === 'ADMIN_PROPIEDAD') {
                          return rolCodigo !== 'ADMIN_PROPIEDAD' && rolCodigo !== 'ADMIN_ORGANIZACION' && rolCodigo !== 'SUPERADMIN';
                        }
                        return true;
                      })
                      .map((r) => (
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