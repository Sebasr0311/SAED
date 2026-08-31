import { useState } from 'react';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch } from '../lib/hooks.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { Card, CardContent } from '../components/ui/card.tsx';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from '../components/ui/dialog.tsx';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '../components/ui/table.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../components/ui/select.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';

/**
 * PropiedadesPage 2.0 — propiedades dentro de una organizacion.
 * Jerarquia: Organizacion -> Propiedad (Edificio/Conjunto) -> Bloques -> Unidades.
 * Consume /api/v1/properties (GET/POST/PUT) con useTenantApi + catalogos.
 */
const ESTADO_BADGE = {
  ACTIVA: 'default',
  SUSPENDIDA: 'warning',
  INACTIVA: 'secondary',
};

const emptyForm = {
  idOrganizacion: '',
  idTipoPropiedad: '',
  nombre: '',
  direccion: '',
  ciudad: '',
  tipoOcupacionPredominante: 'MIXTA',
};

export default function PropiedadesPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const { data, loading, refetch } = useFetch(
    () => tenantApi.get('/properties'),
    [tenant.activeAssignmentId]
  );
  const { data: orgsData } = useFetch(
    () => tenantApi.get('/organizations'),
    [tenant.activeAssignmentId]
  );
  const { data: tiposData } = useFetch(
    () => tenantApi.get('/tipos-propiedad'),
    [tenant.activeAssignmentId]
  );

  const propiedades = Array.isArray(data) ? data : data?.items || [];
  const organizaciones = orgsArray.isArray(data) ? data : data?.items || [];
  const tipos = tiposArray.isArray(data) ? data : data?.items || [];

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);

  async function guardar() {
    if (!form.nombre.trim() || !form.direccion.trim() || !form.ciudad.trim()) {
      toast.error('Nombre, direcci\u00f3n y ciudad son obligatorios');
      return;
    }
    if (!form.idOrganizacion || !form.idTipoPropiedad) {
      toast.error('Seleccione organizaci\u00f3n y tipo de propiedad');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        idOrganizacion: Number(form.idOrganizacion),
        idTipoPropiedad: Number(form.idTipoPropiedad),
        nombre: form.nombre.trim(),
        direccion: form.direccion.trim(),
        ciudad: form.ciudad.trim(),
        tipoOcupacionPredominante: form.tipoOcupacionPredominante || 'MIXTA',
      };
      if (editing) {
        await tenantApi.put(`/properties/${editing.id}`, payload);
        toast.success('Propiedad actualizada');
      } else {
        await tenantApi.post('/properties', payload);
        toast.success('Propiedad creada');
      }
      setDialogOpen(false);
      refetch();
    } catch (err) {
      toast.error(err.message || 'No se pudo guardar la propiedad');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="propiedades-page space-y-6">
      <PageHeader
        title="Propiedades"
        subtitle="Propiedades de las organizaciones (Edificio / Conjunto Cerrado)"
      >
        <Button onClick={() => { setEditing(null); setForm({ ...emptyForm, idOrganizacion: tenant.activeOrgId != null ? String(tenant.activeOrgId) : '' }); setDialogOpen(true); }}>
          <span className="material-symbols-outlined text-base mr-1">add</span>
          Nueva Propiedad
        </Button>
      </PageHeader>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardContent className="flex items-center gap-3 pt-6">
            <div className="rounded-lg bg-primary/10 p-2.5 text-primary">
              <span className="material-symbols-outlined">apartment</span>
            </div>
            <div>
              <p className="text-2xl font-bold">{propiedades.length}</p>
              <p className="text-sm text-muted-foreground">Propiedades</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-3 pt-6">
            <div className="rounded-lg bg-green-500/10 p-2.5 text-green-600">
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
            <div className="rounded-lg bg-blue-500/10 p-2.5 text-blue-600">
              <span className="material-symbols-outlined">category</span>
            </div>
            <div>
              <p className="text-2xl font-bold">{tipos.length}</p>
              <p className="text-sm text-muted-foreground">Tipos</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : propiedades.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">
              No hay propiedades registradas.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Nombre</TableHead>
                    <TableHead>Tipo</TableHead>
                    <TableHead>Organizaci\u00f3n</TableHead>
                    <TableHead>Ciudad</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {propiedades.map((p) => (
                    <TableRow key={p.id}>
                      <TableCell className="font-medium">{p.nombre}</TableCell>
                      <TableCell>{p.tipoPropiedadNombre || p.tipoPropiedadCodigo || '—'}</TableCell>
                      <TableCell>{p.organizacionNombre || `Org ${p.idOrganizacion}`}</TableCell>
                      <TableCell>{p.ciudad || '—'}</TableCell>
                      <TableCell>
                        <Badge variant={ESTADO_BADGE[p.estado] || 'default'}>{p.estado}</Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <Button variant="ghost" size="sm" onClick={() => { setEditing(p); setForm({ idOrganizacion: p.idOrganizacion != null ? String(p.idOrganizacion) : '', idTipoPropiedad: p.idTipoPropiedad != null ? String(p.idTipoPropiedad) : '', nombre: p.nombre || '', direccion: p.direccion || '', ciudad: p.ciudad || '', tipoOcupacionPredominante: p.tipoOcupacionPredominante || 'MIXTA' }); setDialogOpen(true); }} aria-label={`Editar ${p.nombre}`}>
                          <span className="material-symbols-outlined text-base">edit</span>
                        </Button>
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
            <DialogTitle>{editing ? 'Editar Propiedad' : 'Nueva Propiedad'}</DialogTitle>
            <DialogDescription>
              {editing ? `Actualice los datos de ${editing.nombre}.` : 'Registre una propiedad en una organizaci\u00f3n.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-4">
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
              <div className="grid gap-2">
                <Label>Tipo *</Label>
                <Select value={form.idTipoPropiedad} onValueChange={(v) => setForm((f) => ({ ...f, idTipoPropiedad: v }))}>
                  <SelectTrigger><SelectValue placeholder="Seleccione" /></SelectTrigger>
                  <SelectContent>
                    {tipos.map((t) => (
                      <SelectItem key={t.idTipoPropiedad} value={String(t.idTipoPropiedad)}>{t.nombre} ({t.codigo})</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="prop-nombre">Nombre *</Label>
              <Input id="prop-nombre" value={form.nombre} onChange={(e) => setForm((f) => ({ ...f, nombre: e.target.value }))} placeholder="Ej: Torre Norte" />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="prop-dir">Direcci\u00f3n *</Label>
              <Input id="prop-dir" value={form.direccion} onChange={(e) => setForm((f) => ({ ...f, direccion: e.target.value }))} placeholder="Ej: Av 123 #45-67" />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="prop-ciudad">Ciudad *</Label>
                <Input id="prop-ciudad" value={form.ciudad} onChange={(e) => setForm((f) => ({ ...f, ciudad: e.target.value }))} placeholder="Ej: Bogot\u00e1" />
              </div>
              <div className="grid gap-2">
                <Label>Ocupaci\u00f3n</Label>
                <Select value={form.tipoOcupacionPredominante} onValueChange={(v) => setForm((f) => ({ ...f, tipoOcupacionPredominante: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="RESIDENCIAL">Residencial</SelectItem>
                    <SelectItem value="COMERCIAL">Comercial</SelectItem>
                    <SelectItem value="MIXTA">Mixta</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancelar</Button>
            <Button onClick={guardar} disabled={saving}>
              {saving ? 'Guardando\u2026' : editing ? 'Guardar cambios' : 'Crear propiedad'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}