import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { useTenant } from '../lib/TenantContext.jsx';
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

const ESTADO_BADGE = {
  ACTIVA: 'default',
  INACTIVA: 'secondary',
  PRUEBA: 'outline',
  PENDIENTE: 'warning',
  SUSPENDIDA: 'destructive',
};

export default function MembresiasPage() {
  const tenant = useTenant();
  const { data, loading, refetch } = useFetch(() => api.get('/membresias'), []);
  const { data: planesData } = useFetch(() => api.get('/planes/catalogo'), []);
  const { data: orgsData } = useFetch(() => api.get('/organizations'), []);

  const membresias = data?.items || data || [];
  const planes = planesData?.items || planesData || [];
  const orgs = orgsData?.items || orgsData || [];

  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState({ idOrganizacion: '', idPlan: '', estado: 'PRUEBA', esPrueba: false, diasPrueba: '' });
  const [saving, setSaving] = useState(false);

  async function crear() {
    if (!form.idOrganizacion || !form.idPlan) {
      toast.error('Organización y plan son obligatorios');
      return;
    }
    setSaving(true);
    try {
      await api.post('/membresias', {
        idOrganizacion: Number(form.idOrganizacion),
        idPlan: Number(form.idPlan),
        estado: form.estado,
        esPrueba: form.esPrueba,
        diasPrueba: form.diasPrueba ? Number(form.diasPrueba) : null,
      });
      toast.success('Membresía creada');
      setDialogOpen(false);
      setForm({ idOrganizacion: '', idPlan: '', estado: 'PRUEBA', esPrueba: false, diasPrueba: '' });
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al crear');
    } finally {
      setSaving(false);
    }
  }

  async function cambiarEstado(memb, nuevo) {
    try {
      await api.patch(`/membresias/${memb.ID_MEMBRESIA || memb.id_membresia || memb.idMembresia}/status`, { estado: nuevo });
      toast.success(`Membresía → ${nuevo}`);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error');
    }
  }

  async function cancelar(memb) {
    if (!confirm('¿Cancelar esta membresía?')) return;
    try {
      await api.delete(`/membresias/${memb.ID_MEMBRESIA || memb.id_membresia || memb.idMembresia}`);
      toast.success('Membresía cancelada');
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error');
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Membresías" subtitle="Suscripciones de organizaciones a planes">
        <Button onClick={() => setDialogOpen(true)}>
          <span className="material-symbols-outlined text-base mr-1">add</span>
          Nueva Membresía
        </Button>
      </PageHeader>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="space-y-2"><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /></div>
          ) : membresias.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">No hay membresías registradas.</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Organización</TableHead>
                    <TableHead>Plan</TableHead>
                    <TableHead>Inicio</TableHead>
                    <TableHead>Fin</TableHead>
                    <TableHead>Prueba</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {membresias.map((m) => (
                    <TableRow key={m.ID_MEMBRESIA || m.id_membresia || m.idMembresia}>
                      <TableCell className="font-medium">{m.ORG_NOMBRE || m.org_nombre || m.orgNombre || m.ID_ORGANIZACION}</TableCell>
                      <TableCell>{m.PLAN_NOMBRE || m.plan_nombre || m.planNombre || m.PLAN_CODIGO || m.plan_codigo || m.planCodigo}</TableCell>
                      <TableCell>{m.FECHA_INICIO || m.fecha_inicio || m.fechaInicio}</TableCell>
                      <TableCell>{m.FECHA_FIN || m.fecha_fin || m.fechaFin || '—'}</TableCell>
                      <TableCell>
                        {(m.ES_PRUEBA === 'S' || m.es_prueba === 'S' || m.esPrueba === 'S') ? (
                          <Badge variant="outline">{m.DIAS_PRUEBA || m.dias_prueba || m.diasPrueba || ''} días</Badge>
                        ) : '—'}
                      </TableCell>
                      <TableCell>
                        <Badge variant={ESTADO_BADGE[m.ESTADO || m.estado] || 'default'}>{m.ESTADO || m.estado}</Badge>
                      </TableCell>
                      <TableCell className="text-right space-x-1">
                        {(m.ESTADO || m.estado) === 'ACTIVA' || (m.ESTADO || m.estado) === 'PRUEBA' ? (
                          <Button variant="ghost" size="sm" onClick={() => cambiarEstado(m, 'SUSPENDIDA')} aria-label="Suspender">
                            <span className="material-symbols-outlined text-base text-amber-600">pause_circle</span>
                          </Button>
                        ) : (m.ESTADO || m.estado) === 'SUSPENDIDA' ? (
                          <Button variant="ghost" size="sm" onClick={() => cambiarEstado(m, 'ACTIVA')} aria-label="Reactivar">
                            <span className="material-symbols-outlined text-base text-green-600">play_circle</span>
                          </Button>
                        ) : null}
                        {(m.ESTADO || m.estado) !== 'INACTIVA' && (
                          <Button variant="ghost" size="sm" onClick={() => cancelar(m)} aria-label="Cancelar">
                            <span className="material-symbols-outlined text-base text-red-600">cancel</span>
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
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Nueva Membresía</DialogTitle>
            <DialogDescription>Suscriba una organización a un plan comercial.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-2">
              <Label>Organización *</Label>
              <Select value={form.idOrganizacion} onValueChange={(v) => setForm((f) => ({ ...f, idOrganizacion: v }))}>
                <SelectTrigger><SelectValue placeholder="Seleccione" /></SelectTrigger>
                <SelectContent>
                  {orgs.map((o) => (
                    <SelectItem key={o.id} value={String(o.id)}>{o.nombre}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>Plan *</Label>
              <Select value={form.idPlan} onValueChange={(v) => setForm((f) => ({ ...f, idPlan: v }))}>
                <SelectTrigger><SelectValue placeholder="Seleccione" /></SelectTrigger>
                <SelectContent>
                  {planes.map((p) => (
                    <SelectItem key={p.id_plan || p.idPlan} value={String(p.id_plan || p.idPlan)}>
                      {p.nombre} — ${Number(p.precio_mensual || p.precioMensual || 0).toLocaleString('es-CO')}/mes
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label>Estado inicial</Label>
                <Select value={form.estado} onValueChange={(v) => setForm((f) => ({ ...f, estado: v }))}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ACTIVA">Activa</SelectItem>
                    <SelectItem value="PRUEBA">Prueba</SelectItem>
                    <SelectItem value="PENDIENTE">Pendiente</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label>Días de prueba</Label>
                <input type="number" className="border rounded px-3 py-2 text-sm" value={form.diasPrueba}
                  onChange={(e) => setForm((f) => ({ ...f, diasPrueba: e.target.value }))}
                  placeholder="Opcional" />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancelar</Button>
            <Button onClick={crear} disabled={saving}>{saving ? 'Creando…' : 'Crear'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Label({ children }) {
  return <label className="text-sm font-medium">{children}</label>;
}
