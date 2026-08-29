import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
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
import { Skeleton } from '../components/ui/skeleton.tsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { toast } from 'sonner';

const fmtCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

const EMPTY_FORM = {
  fecha: '', categoria: '', beneficiario: '', monto: 0,
  metodoPago: 'EFECTIVO', estado: 'PENDIENTE', presupuestoId: '',
};

export default function GastosPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/gastos'), []);
  const { data: presupData } = useFetch(() => api.get('/presupuestos'), []);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [filtroPresupuesto, setFiltroPresupuesto] = useState('');

  const gastos = data?.items || data || [];
  const presupuestos = presupData?.items || presupData || [];

  const gastosFiltrados = filtroPresupuesto
    ? gastos.filter((g) => String(g.ID_PRESUPUESTO || g.id_presupuesto || g.idPresupuesto) === filtroPresupuesto)
    : gastos;

  function abrirCrear() {
    setEditando(null);
    setForm(EMPTY_FORM);
    setDialogOpen(true);
  }

  function abrirEditar(g) {
    setEditando(g);
    setForm({
      fecha: g.FECHA_GASTO || g.fecha_gasto || '',
      categoria: g.CATEGORIA || g.categoria || '',
      beneficiario: g.BENEFICIARIO || g.beneficiario || '',
      monto: g.MONTO || g.monto || 0,
      metodoPago: g.METODO_PAGO || g.metodo_pago || g.metodoPago || 'EFECTIVO',
      estado: g.ESTADO || g.estado || 'REGISTRADO',
      presupuestoId: g.ID_PRESUPUESTO || g.id_presupuesto || g.idPresupuesto || '',
    });
    setDialogOpen(true);
  }

  async function guardar() {
    if (!form.categoria || !form.beneficiario) {
      toast.error('Categoría y beneficiario son obligatorios');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        fechaGasto: form.fecha || undefined,
        categoria: form.categoria,
        beneficiario: form.beneficiario,
        monto: Number(form.monto),
        metodoPago: form.metodoPago,
        estado: form.estado,
        idPresupuesto: form.presupuestoId ? Number(form.presupuestoId) : undefined,
      };
      if (editando) {
        await api.put(`/gastos/${editando.ID_GASTO || editando.id || editando.id_gasto}`, payload);
        toast.success('Gasto actualizado');
      } else {
        await api.post('/gastos', payload);
        toast.success('Gasto registrado');
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

  async function eliminar() {
    if (!deleteTarget) return;
    try {
      await api.del(`/gastos/${deleteTarget.ID_GASTO || deleteTarget.id || deleteTarget.id_gasto}`);
      toast.success('Gasto eliminado');
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar');
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Gastos"
        subtitle="Registro y control de egresos"
        action={<Button onClick={abrirCrear}><span className="material-symbols-outlined text-base mr-1">add</span>Nuevo Gasto</Button>}
      />

      {/* Filtro */}
      <div className="flex gap-3 items-center">
        <label className="text-sm font-medium">Filtrar por presupuesto:</label>
        <select
          className="border rounded px-3 py-1.5 text-sm"
          value={filtroPresupuesto}
          onChange={(e) => setFiltroPresupuesto(e.target.value)}
        >
          <option value="">Todos</option>
          {presupuestos.map((p) => (
            <option key={p.ID || p.id} value={p.ID || p.id}>{p.RUBRO || p.rubro}</option>
          ))}
        </select>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="space-y-2"><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /></div>
          ) : gastosFiltrados.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">No hay gastos registrados.</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Fecha</TableHead>
                    <TableHead>Categoría</TableHead>
                    <TableHead>Beneficiario</TableHead>
                    <TableHead className="text-right">Monto</TableHead>
                    <TableHead>Método Pago</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead>Presupuesto Asociado</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {gastosFiltrados.map((g) => {
                    const presup = presupuestos.find((p) => (p.ID || p.id) === (g.ID_PRESUPUESTO || g.id_presupuesto || g.idPresupuesto));
                    return (
                      <TableRow key={g.ID_GASTO || g.id || g.id_gasto}>
                        <TableCell className="text-xs whitespace-nowrap">{g.FECHA_GASTO || g.fecha_gasto || '-'}</TableCell>
                        <TableCell>{g.CATEGORIA || g.categoria}</TableCell>
                        <TableCell>{g.BENEFICIARIO || g.beneficiario}</TableCell>
                        <TableCell className="text-right font-bold">{fmtCOP.format(Number(g.MONTO || g.monto || 0))}</TableCell>
                        <TableCell>{g.METODO_PAGO || g.metodo_pago || g.metodoPago}</TableCell>
                        <TableCell>
                          <Badge variant={(g.ESTADO || g.estado) === 'CONFIRMADO' ? 'default' : 'secondary'}>
                            {g.ESTADO || g.estado}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-xs">{presup?.RUBRO || presup?.rubro || '-'}</TableCell>
                        <TableCell className="text-right space-x-1">
                          <Button variant="ghost" size="sm" onClick={() => abrirEditar(g)} aria-label="Editar">
                            <span className="material-symbols-outlined text-base">edit</span>
                          </Button>
                          <Button variant="ghost" size="sm" onClick={() => setDeleteTarget(g)} aria-label="Eliminar">
                            <span className="material-symbols-outlined text-base text-red-600">delete</span>
                          </Button>
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

      {/* Dialog Crear/Editar */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editando ? 'Editar Gasto' : 'Nuevo Gasto'}</DialogTitle>
            <DialogDescription>
              {editando ? 'Modifique los datos del gasto.' : 'Registre un nuevo egreso.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <label className="text-sm font-medium">Fecha</label>
                <input type="date" className="border rounded px-3 py-2 text-sm" value={form.fecha}
                  onChange={(e) => setForm((f) => ({ ...f, fecha: e.target.value }))} />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">Categoría *</label>
                <input className="border rounded px-3 py-2 text-sm" value={form.categoria}
                  onChange={(e) => setForm((f) => ({ ...f, categoria: e.target.value }))}
                  placeholder="Ej: Mantenimiento, Servicios" />
              </div>
            </div>
            <div className="grid gap-2">
              <label className="text-sm font-medium">Beneficiario *</label>
              <input className="border rounded px-3 py-2 text-sm" value={form.beneficiario}
                onChange={(e) => setForm((f) => ({ ...f, beneficiario: e.target.value }))}
                placeholder="Nombre del beneficiario" />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <label className="text-sm font-medium">Monto (COP) *</label>
                <input type="number" className="border rounded px-3 py-2 text-sm" value={form.monto}
                  onChange={(e) => setForm((f) => ({ ...f, monto: e.target.value }))} />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">Método de Pago</label>
                <select className="border rounded px-3 py-2 text-sm" value={form.metodoPago}
                  onChange={(e) => setForm((f) => ({ ...f, metodoPago: e.target.value }))}>
                  <option value="EFECTIVO">Efectivo</option>
                  <option value="TRANSFERENCIA">Transferencia</option>
                  <option value="TARJETA">Tarjeta</option>
                  <option value="CHEQUE">Cheque</option>
                </select>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <label className="text-sm font-medium">Estado</label>
                <select className="border rounded px-3 py-2 text-sm" value={form.estado}
                  onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}>
                  <option value="PENDIENTE">Pendiente</option>
                  <option value="CONFIRMADO">Confirmado</option>
                  <option value="REGISTRADO">Registrado</option>
                  <option value="CANCELADO">Cancelado</option>
                </select>
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">Presupuesto Asociado</label>
                <select className="border rounded px-3 py-2 text-sm" value={form.presupuestoId}
                  onChange={(e) => setForm((f) => ({ ...f, presupuestoId: e.target.value }))}>
                  <option value="">Ninguno</option>
                  {presupuestos.map((p) => (
                    <option key={p.ID || p.id} value={p.ID || p.id}>{p.RUBRO || p.rubro}</option>
                  ))}
                </select>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancelar</Button>
            <Button onClick={guardar} disabled={saving}>{saving ? 'Guardando…' : 'Guardar'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={eliminar}
        title="Eliminar gasto"
        message={`¿Está seguro de eliminar el gasto "${deleteTarget?.BENEFICIARIO || deleteTarget?.beneficiario || ''}"? Esta acción no se puede deshacer.`}
        confirmLabel="Eliminar"
        danger
      />
    </div>
  );
}
