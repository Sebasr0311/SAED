import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
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
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';

const EMPTY_FORM = {
  codigo: '', nombre: '', descripcion: '', precioMensual: 0,
  limitePropiedades: '', limiteUnidades: '', limiteUsuarios: '',
  limiteAlmacenamientoGb: '',
};

export default function PlanesPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/planes'), []);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const planes = data?.items || data || [];

  function abrirCrear() {
    setEditando(null);
    setForm(EMPTY_FORM);
    setDialogOpen(true);
  }

  function abrirEditar(plan) {
    setEditando(plan);
    setForm({
      codigo: plan.CODIGO || plan.codigo || '',
      nombre: plan.NOMBRE || plan.nombre || '',
      descripcion: plan.DESCRIPCION || plan.descripcion || '',
      precioMensual: plan.PRECIO_MENSUAL || plan.precio_mensual || plan.precioMensual || 0,
      limitePropiedades: plan.LIMITE_PROPIEDADES || plan.limite_propiedades || '',
      limiteUnidades: plan.LIMITE_UNIDADES || plan.limite_unidades || '',
      limiteUsuarios: plan.LIMITE_USUARIOS || plan.limite_usuarios || '',
      limiteAlmacenamientoGb: plan.LIMITE_ALMACENAMIENTO_GB || plan.limite_almacenamiento_gb || '',
    });
    setDialogOpen(true);
  }

  async function guardar() {
    if (!form.codigo || !form.nombre) {
      toast.error('Código y nombre son obligatorios');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        codigo: form.codigo,
        nombre: form.nombre,
        descripcion: form.descripcion,
        precioMensual: Number(form.precioMensual),
        limitePropiedades: form.limitePropiedades ? Number(form.limitePropiedades) : null,
        limiteUnidades: form.limiteUnidades ? Number(form.limiteUnidades) : null,
        limiteUsuarios: form.limiteUsuarios ? Number(form.limiteUsuarios) : null,
        limiteAlmacenamientoGb: form.limiteAlmacenamientoGb ? Number(form.limiteAlmacenamientoGb) : null,
      };

      if (editando) {
        await api.put(`/planes/${editando.ID_PLAN || editando.id_plan || editando.idPlan}`, payload);
        toast.success('Plan actualizado');
      } else {
        await api.post('/planes', payload);
        toast.success('Plan creado');
      }
      setDialogOpen(false);
      setForm(EMPTY_FORM);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al guardar');
    } finally {
      setSaving(false);
    }
  }

  async function toggleEstado(plan) {
    const nuevo = (plan.ESTADO || plan.estado) === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO';
    try {
      await api.patch(`/planes/${plan.ID_PLAN || plan.id_plan || plan.idPlan}/status`, { estado: nuevo });
      toast.success(`Plan ${nuevo.toLowerCase()}`);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error');
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Planes" subtitle="Catálogo de planes comerciales del SaaS">
        <Button onClick={abrirCrear}>
          <span className="material-symbols-outlined text-base mr-1">add</span>
          Nuevo Plan
        </Button>
      </PageHeader>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="space-y-2"><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /></div>
          ) : planes.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">No hay planes configurados.</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Código</TableHead>
                    <TableHead>Nombre</TableHead>
                    <TableHead>Precio/mes</TableHead>
                    <TableHead>Propiedades</TableHead>
                    <TableHead>Unidades</TableHead>
                    <TableHead>Usuarios</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {planes.map((p) => (
                    <TableRow key={p.ID_PLAN || p.id_plan || p.idPlan}>
                      <TableCell className="font-mono text-sm">{p.CODIGO || p.codigo}</TableCell>
                      <TableCell className="font-medium">{p.NOMBRE || p.nombre}</TableCell>
                      <TableCell>${Number(p.PRECIO_MENSUAL || p.precio_mensual || p.precioMensual || 0).toLocaleString('es-CO')}</TableCell>
                      <TableCell>{p.LIMITE_PROPIEDADES || p.limite_propiedades || p.limitePropiedades || '∞'}</TableCell>
                      <TableCell>{p.LIMITE_UNIDADES || p.limite_unidades || p.limiteUnidades || '∞'}</TableCell>
                      <TableCell>{p.LIMITE_USUARIOS || p.limite_usuarios || p.limiteUsuarios || '∞'}</TableCell>
                      <TableCell>
                        <Badge variant={(p.ESTADO || p.estado) === 'ACTIVO' ? 'default' : 'secondary'}>{p.ESTADO || p.estado}</Badge>
                      </TableCell>
                      <TableCell className="text-right space-x-1">
                        <Button variant="ghost" size="sm" onClick={() => abrirEditar(p)} aria-label="Editar">
                          <span className="material-symbols-outlined text-base">edit</span>
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => toggleEstado(p)} aria-label={(p.ESTADO || p.estado) === 'ACTIVO' ? 'Desactivar' : 'Activar'}>
                          <span className={`material-symbols-outlined text-base ${(p.ESTADO || p.estado) === 'ACTIVO' ? 'text-amber-600' : 'text-green-600'}`}>
                            {(p.ESTADO || p.estado) === 'ACTIVO' ? 'pause_circle' : 'play_circle'}
                          </span>
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
            <DialogTitle>{editando ? 'Editar Plan' : 'Nuevo Plan'}</DialogTitle>
            <DialogDescription>
              {editando ? 'Modifique los datos del plan.' : 'Defina un nuevo plan comercial.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            {!editando && (
              <div className="grid gap-2">
                <Label>Código *</Label>
                <input className="border rounded px-3 py-2 text-sm" value={form.codigo}
                  onChange={(e) => setForm((f) => ({ ...f, codigo: e.target.value.toUpperCase() }))}
                  placeholder="Ej: FREE, PRO, ENTERPRISE" />
              </div>
            )}
            <div className="grid gap-2">
              <Label>Nombre *</Label>
              <input className="border rounded px-3 py-2 text-sm" value={form.nombre}
                onChange={(e) => setForm((f) => ({ ...f, nombre: e.target.value }))}
                placeholder="Nombre del plan" />
            </div>
            <div className="grid gap-2">
              <Label>Descripción</Label>
              <textarea className="border rounded px-3 py-2 text-sm" rows={2} value={form.descripcion}
                onChange={(e) => setForm((f) => ({ ...f, descripcion: e.target.value }))}
                placeholder="Descripción del plan" />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label>Precio mensual (COP) *</Label>
                <input type="number" className="border rounded px-3 py-2 text-sm" value={form.precioMensual}
                  onChange={(e) => setForm((f) => ({ ...f, precioMensual: e.target.value }))} />
              </div>
              <div className="grid gap-2">
                <Label>Límite propiedades</Label>
                <input type="number" className="border rounded px-3 py-2 text-sm" value={form.limitePropiedades}
                  onChange={(e) => setForm((f) => ({ ...f, limitePropiedades: e.target.value }))}
                  placeholder="Sin límite" />
              </div>
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div className="grid gap-2">
                <Label>Límite unidades</Label>
                <input type="number" className="border rounded px-3 py-2 text-sm" value={form.limiteUnidades}
                  onChange={(e) => setForm((f) => ({ ...f, limiteUnidades: e.target.value }))}
                  placeholder="∞" />
              </div>
              <div className="grid gap-2">
                <Label>Límite usuarios</Label>
                <input type="number" className="border rounded px-3 py-2 text-sm" value={form.limiteUsuarios}
                  onChange={(e) => setForm((f) => ({ ...f, limiteUsuarios: e.target.value }))}
                  placeholder="∞" />
              </div>
              <div className="grid gap-2">
                <Label>Almacenamiento (GB)</Label>
                <input type="number" className="border rounded px-3 py-2 text-sm" value={form.limiteAlmacenamientoGb}
                  onChange={(e) => setForm((f) => ({ ...f, limiteAlmacenamientoGb: e.target.value }))}
                  placeholder="∞" />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancelar</Button>
            <Button onClick={guardar} disabled={saving}>{saving ? 'Guardando…' : 'Guardar'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Label({ children }) {
  return <label className="text-sm font-medium">{children}</label>;
}
