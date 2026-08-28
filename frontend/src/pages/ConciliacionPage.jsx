import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/button.tsx';
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
import { StatCard } from '../components/ui/StatCard.jsx';
import { toast } from 'sonner';

const fmtCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

const EMPTY_FORM = {
  periodo: '', cuentaBanco: '', saldoBanco: 0, saldoLibros: 0, estado: 'EN_PROCESO',
};

export default function ConciliacionPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/conciliaciones'), []);
  const { data: resumenData } = useFetch(() => api.get('/conciliaciones/resumen'), []);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const conciliaciones = data?.items || data || [];
  const resumen = resumenData?.items || resumenData?.raw || resumenData || {};

  function abrirCrear() {
    setEditando(null);
    setForm(EMPTY_FORM);
    setDialogOpen(true);
  }

  function abrirEditar(c) {
    setEditando(c);
    setForm({
      periodo: c.PERIODO || c.periodo || '',
      cuentaBanco: c.CUENTA_BANCO || c.cuenta_banco || c.cuentaBanco || '',
      saldoBanco: c.SALDO_BANCO || c.saldo_banco || c.saldoBanco || 0,
      saldoLibros: c.SALDO_LIBROS || c.saldo_libros || c.saldoLibros || 0,
      estado: c.ESTADO || c.estado || 'EN_PROCESO',
    });
    setDialogOpen(true);
  }

  async function guardar() {
    if (!form.periodo || !form.cuentaBanco) {
      toast.error('Período y cuenta banco son obligatorios');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        periodo: form.periodo,
        cuentaBanco: form.cuentaBanco,
        saldoBanco: Number(form.saldoBanco),
        saldoLibros: Number(form.saldoLibros),
        estado: form.estado,
      };
      if (editando) {
        await api.patch(`/conciliaciones/${editando.ID || editando.id}`, payload);
        toast.success('Conciliación actualizada');
      } else {
        await api.post('/conciliaciones', payload);
        toast.success('Conciliación creada');
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

  function getBadgeVariant(estado) {
    switch (estado) {
      case 'CONCILIADA': return 'default';
      case 'EN_PROCESO': return 'secondary';
      case 'DISCREPANCIA': return 'destructive';
      default: return 'outline';
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Conciliación Bancaria"
        subtitle="Control de conciliación entre banco y libros"
        action={<Button onClick={abrirCrear}><span className="material-symbols-outlined text-base mr-1">add</span>Nueva Conciliación</Button>}
      />

      {/* Resumen cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard icon="check_circle" value={resumen.conciliadas || 0} label="Conciliadas" color="success" />
        <StatCard icon="pending" value={resumen.enProceso || resumen.en_proceso || 0} label="En Proceso" color="warning" />
        <StatCard icon="warning" value={resumen.conDiscrepancias || resumen.con_discrepancias || 0} label="Con Discrepancias" color="danger" />
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="space-y-2"><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /></div>
          ) : conciliaciones.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">No hay conciliaciones registradas.</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Período</TableHead>
                    <TableHead>Cuenta Banco</TableHead>
                    <TableHead className="text-right">Saldo Banco</TableHead>
                    <TableHead className="text-right">Saldo Libros</TableHead>
                    <TableHead className="text-right">Diferencia</TableHead>
                    <TableHead>Estado</TableHead>
                    <TableHead>Fecha Conciliación</TableHead>
                    <TableHead className="text-right">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {conciliaciones.map((c) => {
                    const banco = Number(c.SALDO_BANCO || c.saldo_banco || c.saldoBanco || 0);
                    const libros = Number(c.SALDO_LIBROS || c.saldo_libros || c.saldoLibros || 0);
                    const diferencia = banco - libros;
                    return (
                      <TableRow key={c.ID || c.id}>
                        <TableCell className="font-mono text-sm">{c.PERIODO || c.periodo}</TableCell>
                        <TableCell>{c.CUENTA_BANCO || c.cuenta_banco || c.cuentaBanco}</TableCell>
                        <TableCell className="text-right">{fmtCOP.format(banco)}</TableCell>
                        <TableCell className="text-right">{fmtCOP.format(libros)}</TableCell>
                        <TableCell className={`text-right font-bold ${diferencia !== 0 ? 'text-red-600' : 'text-green-600'}`}>
                          {fmtCOP.format(diferencia)}
                        </TableCell>
                        <TableCell>
                          <Badge variant={getBadgeVariant(c.ESTADO || c.estado)}>
                            {(c.ESTADO || c.estado || '').replace('_', ' ')}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-xs whitespace-nowrap">{c.FECHA_CONCILIACION || c.fecha_conciliacion || '-'}</TableCell>
                        <TableCell className="text-right">
                          <Button variant="ghost" size="sm" onClick={() => abrirEditar(c)} aria-label="Editar">
                            <span className="material-symbols-outlined text-base">edit</span>
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
            <DialogTitle>{editando ? 'Editar Conciliación' : 'Nueva Conciliación'}</DialogTitle>
            <DialogDescription>
              {editando ? 'Modifique los datos de la conciliación.' : 'Registre una nueva conciliación bancaria.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <label className="text-sm font-medium">Período *</label>
                <input className="border rounded px-3 py-2 text-sm" value={form.periodo}
                  onChange={(e) => setForm((f) => ({ ...f, periodo: e.target.value }))}
                  placeholder="Ej: 2026-01" />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">Cuenta Banco *</label>
                <input className="border rounded px-3 py-2 text-sm" value={form.cuentaBanco}
                  onChange={(e) => setForm((f) => ({ ...f, cuentaBanco: e.target.value }))}
                  placeholder="Número de cuenta" />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <label className="text-sm font-medium">Saldo Banco (COP)</label>
                <input type="number" className="border rounded px-3 py-2 text-sm" value={form.saldoBanco}
                  onChange={(e) => setForm((f) => ({ ...f, saldoBanco: e.target.value }))} />
              </div>
              <div className="grid gap-2">
                <label className="text-sm font-medium">Saldo Libros (COP)</label>
                <input type="number" className="border rounded px-3 py-2 text-sm" value={form.saldoLibros}
                  onChange={(e) => setForm((f) => ({ ...f, saldoLibros: e.target.value }))} />
              </div>
            </div>
            <div className="grid gap-2">
              <label className="text-sm font-medium">Estado</label>
              <select className="border rounded px-3 py-2 text-sm" value={form.estado}
                onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}>
                <option value="EN_PROCESO">En Proceso</option>
                <option value="CONCILIADA">Conciliada</option>
                <option value="DISCREPANCIA">Discrepancia</option>
              </select>
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
