import { useState } from 'react';
import { CheckCircle2, Download, FileCheck2, Loader2, Plus, ShieldCheck, XCircle } from 'lucide-react';
import { toast } from 'sonner';
import { useFetch } from '../lib/hooks.js';
import { api, BASE_URL } from '../lib/api.js';
import { TOKEN_KEY } from '../lib/storage.js';
import { cn } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Badge } from '../components/ui/badge.tsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Input, Select, Textarea } from '../components/ui/Form.jsx';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from '../components/ui/dialog.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';

const fmtCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

export default function PazYSalvoPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/paz-y-salvos'), []);
  const { data: unidadesData, loading: unidadesLoading, error: unidadesError } = useFetch(() => api.get('/units'), []);

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
      refetch().catch(() => {});
    } catch (err) {
      toast.error(err.message || 'Error al generar paz y salvo');
    } finally {
      setGenerando(false);
    }
  }

  async function verificar(e) {
    e?.preventDefault();
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
      // Fixed: the session token lives under TOKEN_KEY ('auth_token'), not 'saed_token'.
      const token = sessionStorage.getItem(TOKEN_KEY);
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
        } catch { /* non-JSON error body */ }
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

  function abrirGenerar() {
    setForm({ unidadId: '', motivo: '' });
    setEstadoFinanciero(null);
    setDialogOpen(true);
  }

  const invalido = resultadoVerificacion && (resultadoVerificacion.valido === false || resultadoVerificacion.valido === 0);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Paz y salvo"
        subtitle="Generación de certificados por unidad y verificación de su autenticidad"
        action={
          <Button onClick={abrirGenerar} icon={<Plus className="h-4 w-4" />}>
            Generar paz y salvo
          </Button>
        }
      />

      <section aria-labelledby="pys-verificar-title" className="rounded-lg border border-border bg-surface shadow-sm">
        <div className="border-b border-border px-4 py-3.5 sm:px-5">
          <h2 id="pys-verificar-title" className="text-base font-bold text-on-surface">
            Verificar paz y salvo
          </h2>
          <p className="mt-0.5 text-xs text-text-muted">Ingrese el código impreso en el certificado.</p>
        </div>
        <div className="p-4 sm:p-5">
          <form onSubmit={verificar} className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <Input
              id="pys-codigo"
              label="Código de verificación"
              value={codigoVerificacion}
              onChange={(e) => setCodigoVerificacion(e.target.value)}
              placeholder="Ingrese el código"
              className="tabular-nums"
            />
            <Button type="submit" disabled={verificando} loading={verificando} className="sm:w-auto">
              {verificando ? 'Verificando…' : 'Verificar'}
            </Button>
          </form>
          {resultadoVerificacion && (
            <div
              role="status"
              className={cn(
                'mt-4 rounded-md border p-4',
                invalido
                  ? 'border-tint-red-fg/25 bg-tint-red-bg'
                  : 'border-tint-green-fg/25 bg-tint-green-bg'
              )}
            >
              <p className={cn('flex items-center gap-2 font-bold', invalido ? 'text-tint-red-fg' : 'text-tint-green-fg')}>
                {invalido ? <XCircle className="h-4 w-4" aria-hidden="true" /> : <CheckCircle2 className="h-4 w-4" aria-hidden="true" />}
                {invalido ? 'Paz y salvo no válido' : 'Paz y salvo válido'}
              </p>
              {resultadoVerificacion.mensaje && (
                <p className="mt-1 text-sm text-on-surface">{resultadoVerificacion.mensaje}</p>
              )}
              <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-sm sm:grid-cols-4">
                {[
                  ['Unidad', resultadoVerificacion.ID_UNIDAD],
                  ['Fecha de emisión', resultadoVerificacion.FECHA_EMISION],
                  ['Vencimiento', resultadoVerificacion.FECHA_VENCIMIENTO],
                  ['Estado', resultadoVerificacion.ESTADO],
                ]
                  .filter(([, v]) => v)
                  .map(([k, v]) => (
                    <div key={k}>
                      <dt className="text-xs text-text-secondary">{k}</dt>
                      <dd className="font-semibold tabular-nums text-on-surface">{v}</dd>
                    </div>
                  ))}
              </dl>
            </div>
          )}
        </div>
      </section>

      <section className="table-container" aria-label="Paz y salvos generados">
        {loading ? (
          <div className="space-y-3 p-4" role="status" aria-label="Cargando paz y salvos">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        ) : error ? (
          <div className="p-4">
            <ErrorState
              compact
              title="No se pudieron cargar los paz y salvos"
              message={error.message || 'Revise su conexión e intente de nuevo.'}
              onRetry={() => refetch().catch(() => {})}
            />
          </div>
        ) : pazYSalvos.length === 0 ? (
          <EmptyState
            icon={FileCheck2}
            title="No hay paz y salvos generados"
            subtitle="Genere el certificado de una unidad sin obligaciones pendientes."
          >
            <div className="mt-3">
              <Button size="sm" onClick={abrirGenerar}>Generar paz y salvo</Button>
            </div>
          </EmptyState>
        ) : (
          <div className="max-h-[70vh] overflow-auto">
            <table className="data-table">
              <caption className="sr-only">Paz y salvos generados</caption>
              <thead>
                <tr>
                  <th scope="col">Unidad</th>
                  <th scope="col">Solicitante</th>
                  <th scope="col">Código de verificación</th>
                  <th scope="col">Emisión</th>
                  <th scope="col">Vencimiento</th>
                  <th scope="col" className="text-right">Saldo a la fecha</th>
                  <th scope="col">Estado</th>
                  <th scope="col" className="text-right"><span className="sr-only">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {pazYSalvos.map((p, i) => {
                  const id = p.ID_PAZ_SALVO || p.id;
                  const estado = p.ESTADO || p.estado;
                  return (
                    <tr key={id || i}>
                      <td className="font-semibold tabular-nums">{p.ID_UNIDAD || p.id_unidad}</td>
                      <td className="tabular-nums">{p.ID_PERSONA_SOLICITANTE || p.id_persona_solicitante}</td>
                      <td className="font-mono text-xs">{p.CODIGO_VERIFICACION || p.codigo_verificacion || p.codigoVerificacion}</td>
                      <td className="whitespace-nowrap tabular-nums">{p.FECHA_EMISION || p.fecha_emision || '—'}</td>
                      <td className="whitespace-nowrap tabular-nums">{p.FECHA_VENCIMIENTO || p.fecha_vencimiento || '—'}</td>
                      <td className="whitespace-nowrap text-right font-bold tabular-nums">
                        {fmtCOP.format(Number(p.SALDO_A_LA_FECHA || p.saldo_a_la_fecha || 0))}
                      </td>
                      <td>
                        <Badge variant={estado === 'VALIDO' ? 'success' : 'secondary'}>
                          {estado === 'VALIDO' ? 'Válido' : estado}
                        </Badge>
                      </td>
                      <td className="text-right">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => descargarPdf(p)}
                          disabled={descargandoId === id}
                          icon={
                            descargandoId === id ? (
                              <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            ) : (
                              <Download className="h-3.5 w-3.5" />
                            )
                          }
                        >
                          PDF
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Generar paz y salvo</DialogTitle>
            <DialogDescription>
              Seleccione la unidad para verificar su balance y generar el certificado.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <Select
              id="pys-unidad"
              label="Unidad"
              required
              value={form.unidadId}
              disabled={unidadesLoading}
              error={unidadesError ? 'No se pudieron cargar las unidades' : undefined}
              onChange={(e) => {
                const val = e.target.value;
                setForm((f) => ({ ...f, unidadId: val }));
                consultarEstadoFinanciero(val);
              }}
            >
              <option value="">{unidadesLoading ? 'Cargando unidades…' : 'Seleccione una unidad'}</option>
              {unidades.map((u) => (
                <option key={u.ID || u.id} value={u.ID || u.id}>{u.CODIGO || u.codigo || u.NOMBRE || u.nombre}</option>
              ))}
            </Select>

            {consultandoFinanzas && (
              <p role="status" className="flex items-center gap-1.5 text-xs text-text-secondary">
                <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                Verificando obligaciones financieras (cartera y multas)…
              </p>
            )}

            {estadoFinanciero && !consultandoFinanzas && (
              <div
                className={cn(
                  'rounded-md border p-3 text-sm',
                  estadoFinanciero.pazYSalvo
                    ? 'border-tint-green-fg/25 bg-tint-green-bg'
                    : 'border-tint-red-fg/25 bg-tint-red-bg'
                )}
              >
                <div className="mb-2 flex items-center justify-between gap-2">
                  <span className="text-[13px] font-bold text-on-surface">Estado financiero</span>
                  <Badge variant={estadoFinanciero.pazYSalvo ? 'success' : 'destructive'}>
                    {estadoFinanciero.pazYSalvo ? (
                      <ShieldCheck className="h-3 w-3" aria-hidden="true" />
                    ) : (
                      <XCircle className="h-3 w-3" aria-hidden="true" />
                    )}
                    {estadoFinanciero.pazYSalvo ? 'Al día' : 'Saldo pendiente'}
                  </Badge>
                </div>
                <dl className="grid grid-cols-3 gap-2 border-t border-border-subtle pt-2 text-xs">
                  <div>
                    <dt className="text-text-secondary">Cartera</dt>
                    <dd className="font-semibold tabular-nums text-on-surface">{fmtCOP.format(Number(estadoFinanciero.saldoCartera || 0))}</dd>
                  </div>
                  <div>
                    <dt className="text-text-secondary">Multas</dt>
                    <dd className="font-semibold tabular-nums text-on-surface">{fmtCOP.format(Number(estadoFinanciero.saldoMultas || 0))}</dd>
                  </div>
                  <div>
                    <dt className="text-text-secondary">Total exigible</dt>
                    <dd className="font-bold tabular-nums text-on-surface">{fmtCOP.format(Number(estadoFinanciero.saldoTotalExigible || 0))}</dd>
                  </div>
                </dl>
                {!estadoFinanciero.pazYSalvo && estadoFinanciero.motivosBloqueo?.length > 0 && (
                  <div className="mt-2 space-y-1 text-xs">
                    <p className="font-semibold text-tint-red-fg">Causales de bloqueo:</p>
                    <ul className="list-disc pl-4 text-on-surface">
                      {estadoFinanciero.motivosBloqueo.map((m, idx) => (
                        <li key={idx}>{m}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}

            <Textarea
              id="pys-motivo"
              label="Motivo (opcional)"
              rows={2}
              value={form.motivo}
              onChange={(e) => setForm((f) => ({ ...f, motivo: e.target.value }))}
              placeholder="Motivo de la solicitud"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={generando}>Cancelar</Button>
            <Button
              onClick={generar}
              loading={generando}
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
