import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
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
import { Skeleton } from '../components/ui/skeleton.tsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { toast } from 'sonner';

const fmtCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

const EMPTY_FORM = { rubro: '', tipo: 'INGRESO', montoPresupuestado: 0, estado: 'ACTIVO', vigenciaAnio: new Date().getFullYear() };

const TABS = [
  { id: 'lista', label: 'Presupuestos', icon: 'list' },
  { id: 'ejecucion', label: 'Ejecución', icon: 'trending_up' },
  { id: 'resumen', label: 'Resumen', icon: 'summarize' },
];

export default function PresupuestoPage() {
  const [tabActiva, setTabActiva] = useState('lista');
  const { data, loading, refetch } = useFetch(() => api.get('/presupuestos'), []);
  const { data: ejecucionData } = useFetch(() => api.get('/presupuestos/ejecucion'), [tabActiva === 'ejecucion']);
  const { data: resumenData } = useFetch(() => api.get('/presupuestos/resumen'), [tabActiva === 'resumen']);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);

  const presupuestos = data?.items || data || [];
  const ejecucion = ejecucionData?.items || ejecucionData || [];
  const resumen = resumenData?.items || resumenData?.raw || resumenData || {};

  function abrirCrear() {
    setEditando(null);
    setForm(EMPTY_FORM);
    setDialogOpen(true);
  }

  function abrirEditar(p) {
    setEditando(p);
    setForm({
      rubro: p.RUBRO || p.rubro || '',
      tipo: p.TIPO || p.tipo || 'INGRESO',
      montoPresupuestado: p.MONTO_PRESUPUESTADO || p.monto_presupuestado || p.montoPresupuestado || 0,
      estado: p.ESTADO || p.estado || 'ACTIVO',
      vigenciaAnio: p.VIGENCIA_ANIO || p.vigencia_anio || p.vigenciaAnio || new Date().getFullYear(),
    });
    setDialogOpen(true);
  }

  async function guardar() {
    if (!form.rubro) {
      toast.error('El rubro es obligatorio');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        rubro: form.rubro,
        tipo: form.tipo,
        montoPresupuestado: Number(form.montoPresupuestado),
        vigenciaAnio: Number(form.vigenciaAnio),
      };
      if (editando) {
        await api.put(`/presupuestos/${editando.ID || editando.id}`, payload);
        toast.success('Presupuesto actualizado');
      } else {
        await api.post('/presupuestos', payload);
        toast.success('Presupuesto creado');
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
      await api.del(`/presupuestos/${deleteTarget.ID || deleteTarget.id}`);
      toast.success('Presupuesto eliminado');
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar');
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Presupuesto"
        subtitle="Gestión presupuestal del edificio"
        action={<Button onClick={abrirCrear}><span className="material-symbols-outlined text-base mr-1">add</span>Nuevo Presupuesto</Button>}
      />

      <div className="flex gap-2 border-b pb-2">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTabActiva(t.id)}
            className={`flex items-center gap-1 px-3 py-1.5 text-sm rounded-t font-medium transition-colors ${
              tabActiva === t.id
                ? 'border-b-2 border-primary text-foreground'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            <span className="material-symbols-outlined text-base">{t.icon}</span>
            {t.label}
          </button>
        ))}
      </div>

      {/* TAB: Lista */}
      {tabActiva === 'lista' && (
        <Card>
          <CardContent className="pt-6">
            {loading ? (
              <div className="space-y-2"><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /></div>
            ) : presupuestos.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground">No hay presupuestos registrados.</p>
            ) : (
              <div className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Rubro</TableHead>
                      <TableHead>Tipo</TableHead>
                      <TableHead className="text-right">Monto Presupuestado</TableHead>
                      <TableHead className="text-right">Monto Ejecutado</TableHead>
                      <TableHead className="text-right">% Ejecución</TableHead>
                      <TableHead>Estado</TableHead>
                      <TableHead className="text-right">Acciones</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {presupuestos.map((p) => {
                      const presupuestado = Number(p.MONTO_PRESUPUESTADO || p.monto_presupuestado || p.montoPresupuestado || 0);
                      const ejecutado = Number(p.MONTO_EJECUTADO || p.monto_ejecutado || p.montoEjecutado || 0);
                      const pct = presupuestado > 0 ? (ejecutado / presupuestado) * 100 : 0;
                      return (
                        <TableRow key={p.ID || p.id}>
                          <TableCell className="font-medium">{p.RUBRO || p.rubro}</TableCell>
                          <TableCell>
                            <Badge variant={(p.TIPO || p.tipo) === 'INGRESO' ? 'default' : 'secondary'}>
                              {p.TIPO || p.tipo}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-right">{fmtCOP.format(presupuestado)}</TableCell>
                          <TableCell className="text-right">{fmtCOP.format(ejecutado)}</TableCell>
                          <TableCell className="text-right">{pct.toFixed(1)}%</TableCell>
                          <TableCell>
                            <Badge variant={(p.ESTADO || p.estado) === 'ACTIVO' ? 'default' : 'secondary'}>
                              {p.ESTADO || p.estado}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-right space-x-1">
                            <Button variant="ghost" size="sm" onClick={() => abrirEditar(p)} aria-label="Editar">
                              <span className="material-symbols-outlined text-base">edit</span>
                            </Button>
                            <Button variant="ghost" size="sm" onClick={() => setDeleteTarget(p)} aria-label="Eliminar">
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
      )}

      {/* TAB: Ejecución */}
      {tabActiva === 'ejecucion' && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Ejecución Presupuestal por Rubro</CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="space-y-2"><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /></div>
            ) : ejecucion.length === 0 ? (
              <p className="py-8 text-center text-muted-foreground">No hay datos de ejecución disponibles.</p>
            ) : (
              <div className="space-y-4">
                {ejecucion.map((e, i) => {
                  const presupuestado = Number(e.MONTO_PRESUPUESTADO || e.monto_presupuestado || 0);
                  const ejecutado = Number(e.MONTO_EJECUTADO || e.monto_ejecutado || 0);
                  const pct = presupuestado > 0 ? (ejecutado / presupuestado) * 100 : 0;
                  return (
                    <div key={i} className="space-y-1">
                      <div className="flex justify-between text-sm">
                        <span className="font-medium">{e.RUBRO || e.rubro}</span>
                        <span className="text-muted-foreground">{fmtCOP.format(ejecutado)} / {fmtCOP.format(presupuestado)}</span>
                      </div>
                      <div className="flex-1 h-3 bg-gray-200 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all ${pct > 100 ? 'bg-red-500' : pct > 80 ? 'bg-amber-500' : 'bg-green-500'}`}
                          style={{ width: `${Math.min(pct, 100)}%` }}
                        />
                      </div>
                      <div className="text-xs text-muted-foreground text-right">{pct.toFixed(1)}%</div>
                    </div>
                  );
                })}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* TAB: Resumen */}
      {tabActiva === 'resumen' && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard icon="trending_up" value={fmtCOP.format(Number(resumen.TOTAL_INGRESOS || 0))} label="Total Ingresos" color="success" />
          <StatCard icon="trending_down" value={fmtCOP.format(Number(resumen.TOTAL_EGRESOS || 0))} label="Total Egresos" color="danger" />
          <StatCard icon="savings" value={fmtCOP.format(Number(resumen.SALDO || 0))} label="Saldo" color="primary" />
          <StatCard icon="pie_chart" value={`${Number(resumen.PORCENTAJE_EJECUCION || 0).toFixed(1)}%`} label="% Ejecución General" color="warning" />
        </div>
      )}

      {/* Dialog Crear/Editar */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editando ? 'Editar Presupuesto' : 'Nuevo Presupuesto'}</DialogTitle>
            <DialogDescription>
              {editando ? 'Modifique los datos del presupuesto.' : 'Defina un nuevo rubro presupuestal.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-2">
              <label className="text-sm font-medium">Rubro *</label>
              <input className="border rounded px-3 py-2 text-sm" value={form.rubro}
                onChange={(e) => setForm((f) => ({ ...f, rubro: e.target.value }))}
                placeholder="Ej: Mantenimiento, Servicios públicos" />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <label className="text-sm font-medium">Tipo *</label>
                <select className="border rounded px-3 py-2 text-sm" value={form.tipo}
                  onChange={(e) => setForm((f) => ({ ...f, tipo: e.target.value }))}>
                  <option value="INGRESO">INGRESO</option>
                  <option value="EGRESO">EGRESO</option>
                </select>
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">Estado</label>
                <select className="border rounded px-3 py-2 text-sm" value={form.estado}
                  onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}>
                  <option value="ACTIVO">ACTIVO</option>
                  <option value="INACTIVO">INACTIVO</option>
                </select>
              </div>
            </div>
            <div className="grid gap-2">
              <label className="text-sm font-medium">Monto Presupuestado (COP) *</label>
              <input type="number" className="border rounded px-3 py-2 text-sm" value={form.montoPresupuestado}
                onChange={(e) => setForm((f) => ({ ...f, montoPresupuestado: e.target.value }))} />
            </div>
            <div className="grid gap-2">
              <label className="text-sm font-medium">Vigencia (Año) *</label>
              <input type="number" className="border rounded px-3 py-2 text-sm" value={form.vigenciaAnio}
                onChange={(e) => setForm((f) => ({ ...f, vigenciaAnio: e.target.value }))} min="2020" max="2099" />
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
        title="Eliminar presupuesto"
        message={`¿Está seguro de eliminar el rubro "${deleteTarget?.RUBRO || deleteTarget?.rubro || ''}"? Esta acción no se puede deshacer.`}
        confirmLabel="Eliminar"
        danger
      />
    </div>
  );
}
