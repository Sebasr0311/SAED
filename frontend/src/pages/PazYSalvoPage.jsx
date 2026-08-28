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
import { toast } from 'sonner';

const fmtCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

export default function PazYSalvoPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/paz-y-salvos'), []);
  const { data: unidadesData } = useFetch(() => api.get('/unidades'), []);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [generando, setGenerando] = useState(false);
  const [form, setForm] = useState({ unidadId: '', motivo: '' });

  const [codigoVerificacion, setCodigoVerificacion] = useState('');
  const [verificando, setVerificando] = useState(false);
  const [resultadoVerificacion, setResultadoVerificacion] = useState(null);

  const pazYSalvos = data?.items || data || [];
  const unidades = unidadesData?.items || unidadesData || [];

  async function generar() {
    if (!form.unidadId) {
      toast.error('Seleccione una unidad');
      return;
    }
    setGenerando(true);
    try {
      await api.post('/paz-y-salvos', { unidadId: Number(form.unidadId), motivo: form.monto });
      toast.success('Paz y salvo generado exitosamente');
      setDialogOpen(false);
      setForm({ unidadId: '', motivo: '' });
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al generar paz y salvo');
    } finally {
      setGenerando(false);
    }
  }

  async function verificar() {
    if (!codigoVerificacion) {
      toast.error('Ingrese un código de verificación');
      return;
    }
    setVerificando(true);
    setResultadoVerificacion(null);
    try {
      const result = await api.get(`/paz-y-salvos/verificar/${encodeURIComponent(codigoVerificacion)}`);
      setResultadoVerificacion(result);
    } catch (err) {
      setResultadoVerificacion({ valido: false, mensaje: err.message || 'Código no válido' });
    } finally {
      setVerificando(false);
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Paz y Salvo"
        subtitle="Generación y verificación de paz y salvos"
        action={
          <Button onClick={() => { setForm({ unidadId: '', motivo: '' }); setDialogOpen(true); }}>
            <span className="material-symbols-outlined text-base mr-1">add</span>
            Generar Paz y Salvo
          </Button>
        }
      />

      {/* Sección de verificación */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Verificar Paz y Salvo</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex gap-3 items-end">
            <div className="grid gap-2 flex-1">
              <label className="text-sm font-medium">Código de Verificación</label>
              <input
                className="border rounded px-3 py-2 text-sm"
                value={codigoVerificacion}
                onChange={(e) => setCodigoVerificacion(e.target.value)}
                placeholder="Ingrese el código"
              />
            </div>
            <Button onClick={verificar} disabled={verificando}>
              {verificando ? 'Verificando…' : 'Verificar'}
            </Button>
          </div>
          {resultadoVerificacion && (
            <div className={`mt-4 p-4 rounded border ${
              resultadoVerificacion.valido === false || resultadoVerificacion.valido === 0
                ? 'bg-red-50 border-red-200 text-red-800'
                : 'bg-green-50 border-green-200 text-green-800'
            }`}>
              <p className="font-medium">
                {resultadoVerificacion.valido === false || resultadoVerificacion.valido === 0
                  ? 'Paz y salvo NO válido'
                  : 'Paz y salvo VÁLIDO'}
              </p>
              {resultadoVerificacion.mensaje && (
                <p className="text-sm mt-1">{resultadoVerificacion.mensaje}</p>
              )}
              {resultadoVerificacion.unidad && (
                <p className="text-sm mt-1">Unidad: {resultadoVerificacion.unidad}</p>
              )}
              {resultadoVerificacion.fechaEmision && (
                <p className="text-sm mt-1">Fecha emisión: {resultadoVerificacion.fechaEmision}</p>
              )}
              {resultadoVerificacion.vencimiento && (
                <p className="text-sm mt-1">Vencimiento: {resultadoVerificacion.vencimiento}</p>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Tabla de paz y salvos */}
      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="space-y-2"><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /><Skeleton className="h-8 w-full" /></div>
          ) : pazYSalvos.length === 0 ? (
            <p className="py-8 text-center text-muted-foreground">No hay paz y salvos generados.</p>
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Unidad</TableHead>
                    <TableHead>Solicitante</TableHead>
                    <TableHead>Código Verificación</TableHead>
                    <TableHead>Fecha Emisión</TableHead>
                    <TableHead>Vencimiento</TableHead>
                    <TableHead className="text-right">Saldo</TableHead>
                    <TableHead>Estado</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {pazYSalvos.map((p, i) => (
                    <TableRow key={p.ID || p.id || i}>
                      <TableCell className="font-mono text-sm">{p.UNIDAD || p.unidad}</TableCell>
                      <TableCell>{p.SOLICITANTE || p.solicitante}</TableCell>
                      <TableCell className="font-mono text-xs">{p.CODIGO_VERIFICACION || p.codigo_verificacion || p.codigoVerificacion}</TableCell>
                      <TableCell className="text-xs whitespace-nowrap">{p.FECHA_EMISION || p.fecha_emision || '-'}</TableCell>
                      <TableCell className="text-xs whitespace-nowrap">{p.VENCIMIENTO || p.vencimiento || '-'}</TableCell>
                      <TableCell className="text-right font-bold">{fmtCOP.format(Number(p.SALDO || p.saldo || 0))}</TableCell>
                      <TableCell>
                        <Badge variant={(p.ESTADO || p.estado) === 'VIGENTE' ? 'default' : 'secondary'}>
                          {p.ESTADO || p.estado}
                        </Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Dialog Generar */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Generar Paz y Salvo</DialogTitle>
            <DialogDescription>
              Seleccione la unidad y el motivo para generar el paz y salvo.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-2">
              <label className="text-sm font-medium">Unidad *</label>
              <select className="border rounded px-3 py-2 text-sm" value={form.unidadId}
                onChange={(e) => setForm((f) => ({ ...f, unidadId: e.target.value }))}>
                <option value="">Seleccione una unidad</option>
                {unidades.map((u) => (
                  <option key={u.ID || u.id} value={u.ID || u.id}>{u.CODIGO || u.codigo || u.NOMBRE || u.nombre}</option>
                ))}
              </select>
            </div>
            <div className="grid gap-2">
              <label className="text-sm font-medium">Motivo</label>
              <textarea className="border rounded px-3 py-2 text-sm" rows={2} value={form.motivo}
                onChange={(e) => setForm((f) => ({ ...f, motivo: e.target.value }))}
                placeholder="Motivo de la solicitud" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancelar</Button>
            <Button onClick={generar} disabled={generando}>{generando ? 'Generando…' : 'Generar'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
