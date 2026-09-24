import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api, BASE_URL } from '../lib/api.js';
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

const fmtCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

export default function PazYSalvoPage() {
  const { data, loading, refetch } = useFetch(() => api.get('/paz-y-salvos'), []);
  const { data: unidadesData } = useFetch(() => api.get('/units'), []);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [generando, setGenerando] = useState(false);
  const [form, setForm] = useState({ unidadId: '', motivo: '' });
  const [estadoFinanciero, setEstadoFinanciero] = useState(null);
  const [consultandoFinanzas, setConsultandoFinanzas] = useState(false);
  const [descargandoId, setDescargandoId] = useState(null);

  const [codigoVerificacion, setCodigoVerificacion] = useState('');
  const [verificando, setVerificando] = useState(false);
  const [resultadoVerificacion, setResultadoVerificacion] = useState(null);

  const pazYSalvos = data?.items || data || [];
  const unidades = unidadesData?.items || unidadesData || [];

  async function consultarEstadoFinanciero(unidadId) {
    if (!unidadId) {
      setEstadoFinanciero(null);
      return;
    }
    setConsultandoFinanzas(true);
    try {
      const result = await api.get(`/paz-y-salvos/unidad/${unidadId}/estado-financiero`);
      setEstadoFinanciero(result);
    } catch (err) {
      setEstadoFinanciero(null);
      toast.error(err.message || 'Error al consultar estado financiero');
    } finally {
      setConsultandoFinanzas(false);
    }
  }

  async function generar() {
    const uid = Number(form.unidadId);
    if (!form.unidadId || Number.isNaN(uid) || uid <= 0) {
      toast.error('Seleccione una unidad válida');
      return;
    }
    if (estadoFinanciero && !estadoFinanciero.pazYSalvo) {
      toast.error('No se puede generar: La unidad registra obligaciones financieras pendientes');
      return;
    }
    setGenerando(true);
    try {
      await api.post('/paz-y-salvos', { idUnidad: uid, motivo: form.motivo?.trim() || '' });
      toast.success('Paz y salvo generado exitosamente');
      setDialogOpen(false);
      setForm({ unidadId: '', motivo: '' });
      setEstadoFinanciero(null);
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
      // Backend returns the paz y salvo record on success; treat any successful response as valid
      setResultadoVerificacion({ valido: true, ...result });
    } catch (err) {
      setResultadoVerificacion({ valido: false, mensaje: err.message || 'Código no válido' });
    } finally {
      setVerificando(false);
    }
  }

  async function descargarPdf(pazSalvo) {
    const id = pazSalvo.ID_PAZ_SALVO || pazSalvo.id;
    if (!id) return;
    setDescargandoId(id);
    try {
      const token = sessionStorage.getItem('saed_token');
      const activeAssignment = sessionStorage.getItem('saed_active_assignment_id');
      const headers = {};
      if (token) headers['Authorization'] = `Bearer ${token}`;
      if (activeAssignment) headers['X-Assignment-Id'] = activeAssignment;

      const res = await fetch(`${BASE_URL}/paz-y-salvos/${id}/descargar`, {
        method: 'GET',
        headers,
      });

      if (!res.ok) {
        let msg = `Error al descargar PDF (${res.status})`;
        try {
          const errJson = await res.json();
          msg = errJson.message || errJson.mensaje || msg;
        } catch (_) {}
        throw new Error(msg);
      }

      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;

      let filename = `paz_y_salvo_${pazSalvo.CODIGO_VERIFICACION || id}.pdf`;
      const disposition = res.headers.get('Content-Disposition');
      if (disposition && disposition.includes('filename=')) {
        const match = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
        if (match && match[1]) {
          filename = match[1].replace(/['"]/g, '');
        }
      }

      a.download = filename;
      document.body.appendChild(a);
      a.click();
      setTimeout(() => {
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      }, 150);

      toast.success('Certificado descargado exitosamente', {
        description: pazSalvo.DOCUMENTO_HASH ? `SHA-256: ${pazSalvo.DOCUMENTO_HASH.substring(0, 16)}...` : undefined,
      });
    } catch (err) {
      toast.error(err.message || 'Error al descargar documento PDF');
    } finally {
      setDescargandoId(null);
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Paz y Salvo"
        subtitle="Generación y verificación de paz y salvos"
        action={
          <Button onClick={() => { setForm({ unidadId: '', motivo: '' }); setEstadoFinanciero(null); setDialogOpen(true); }}>
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
              {resultadoVerificacion.ID_UNIDAD && (
                <p className="text-sm mt-1">Unidad: {resultadoVerificacion.ID_UNIDAD}</p>
              )}
              {resultadoVerificacion.FECHA_EMISION && (
                <p className="text-sm mt-1">Fecha emisión: {resultadoVerificacion.FECHA_EMISION}</p>
              )}
              {resultadoVerificacion.FECHA_VENCIMIENTO && (
                <p className="text-sm mt-1">Vencimiento: {resultadoVerificacion.FECHA_VENCIMIENTO}</p>
              )}
              {resultadoVerificacion.SALDO_A_LA_FECHA != null && (
                <p className="text-sm mt-1">Saldo: {fmtCOP.format(Number(resultadoVerificacion.SALDO_A_LA_FECHA))}</p>
              )}
              {resultadoVerificacion.ESTADO && (
                <p className="text-sm mt-1">Estado: {resultadoVerificacion.ESTADO}</p>
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
                    <TableHead className="text-center">Acciones</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {pazYSalvos.map((p, i) => (
                    <TableRow key={p.ID_PAZ_SALVO || p.id || i}>
                      <TableCell className="font-mono text-sm">{p.ID_UNIDAD || p.id_unidad}</TableCell>
                      <TableCell>{p.ID_PERSONA_SOLICITANTE || p.id_persona_solicitante}</TableCell>
                      <TableCell className="font-mono text-xs">{p.CODIGO_VERIFICACION || p.codigo_verificacion || p.codigoVerificacion}</TableCell>
                      <TableCell className="text-xs whitespace-nowrap">{p.FECHA_EMISION || p.fecha_emision || '-'}</TableCell>
                      <TableCell className="text-xs whitespace-nowrap">{p.FECHA_VENCIMIENTO || p.fecha_vencimiento || '-'}</TableCell>
                      <TableCell className="text-right font-bold">{fmtCOP.format(Number(p.SALDO_A_LA_FECHA || p.saldo_a_la_fecha || 0))}</TableCell>
                      <TableCell>
                        <Badge variant={(p.ESTADO || p.estado) === 'VALIDO' ? 'default' : 'secondary'}>
                          {p.ESTADO || p.estado}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-center">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => descargarPdf(p)}
                          disabled={descargandoId === (p.ID_PAZ_SALVO || p.id)}
                          title={p.DOCUMENTO_HASH ? `SHA-256: ${p.DOCUMENTO_HASH}` : 'Descargar PDF'}
                        >
                          <span className="material-symbols-outlined text-sm mr-1">
                            {descargandoId === (p.ID_PAZ_SALVO || p.id) ? 'hourglass_top' : 'download'}
                          </span>
                          PDF
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

      {/* Dialog Generar */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Generar Paz y Salvo</DialogTitle>
            <DialogDescription>
              Seleccione la unidad para verificar su balance y generar el certificado oficial.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-2">
              <label className="text-sm font-medium">Unidad *</label>
              <select
                className="border rounded px-3 py-2 text-sm"
                value={form.unidadId}
                onChange={(e) => {
                  const val = e.target.value;
                  setForm((f) => ({ ...f, unidadId: val }));
                  consultarEstadoFinanciero(val);
                }}
              >
                <option value="">Seleccione una unidad</option>
                {unidades.map((u) => (
                  <option key={u.ID || u.id} value={u.ID || u.id}>{u.CODIGO || u.codigo || u.NOMBRE || u.nombre}</option>
                ))}
              </select>
            </div>

            {consultandoFinanzas && (
              <div className="text-xs text-muted-foreground flex items-center gap-1.5 py-1">
                <span className="material-symbols-outlined text-sm animate-spin">progress_activity</span>
                Verificando obligaciones financieras (cartera + multas)...
              </div>
            )}

            {estadoFinanciero && !consultandoFinanzas && (
              <div className={`p-3 rounded-lg border text-sm ${
                estadoFinanciero.pazYSalvo
                  ? 'bg-emerald-50/70 border-emerald-200 text-emerald-900'
                  : 'bg-rose-50/70 border-rose-200 text-rose-900'
              }`}>
                <div className="flex items-center justify-between mb-2">
                  <span className="font-semibold text-xs uppercase tracking-wider">Estado Financiero</span>
                  <Badge variant={estadoFinanciero.pazYSalvo ? 'default' : 'destructive'} className="text-xs">
                    {estadoFinanciero.pazYSalvo ? 'AL DÍA' : 'SALDO PENDIENTE'}
                  </Badge>
                </div>
                <div className="grid grid-cols-3 gap-2 text-xs py-1 border-t border-b border-current/10 my-1">
                  <div>
                    <span className="text-muted-foreground block text-[11px]">Cartera / Cuotas</span>
                    <span className="font-semibold">{fmtCOP.format(Number(estadoFinanciero.saldoCartera || 0))}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground block text-[11px]">Multas / Sanciones</span>
                    <span className="font-semibold">{fmtCOP.format(Number(estadoFinanciero.saldoMultas || 0))}</span>
                  </div>
                  <div>
                    <span className="text-muted-foreground block text-[11px]">Total Exigible</span>
                    <span className="font-bold">{fmtCOP.format(Number(estadoFinanciero.saldoTotalExigible || 0))}</span>
                  </div>
                </div>
                {!estadoFinanciero.pazYSalvo && estadoFinanciero.motivosBloqueo?.length > 0 && (
                  <div className="mt-2 text-xs space-y-1">
                    <p className="font-medium text-rose-800">Causales de bloqueo:</p>
                    <ul className="list-disc pl-4 text-rose-700">
                      {estadoFinanciero.motivosBloqueo.map((m, idx) => (
                        <li key={idx}>{m}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}

            <div className="grid gap-2">
              <label className="text-sm font-medium">Motivo</label>
              <textarea
                className="border rounded px-3 py-2 text-sm"
                rows={2}
                value={form.motivo}
                onChange={(e) => setForm((f) => ({ ...f, motivo: e.target.value }))}
                placeholder="Motivo de la solicitud (opcional)"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancelar</Button>
            <Button
              onClick={generar}
              disabled={generando || consultandoFinanzas || (estadoFinanciero && !estadoFinanciero.pazYSalvo)}
            >
              {generando ? 'Generando…' : 'Generar'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
