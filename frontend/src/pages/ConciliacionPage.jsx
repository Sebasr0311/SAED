import { useState } from 'react';
import { AlertTriangle, CheckCircle2, Clock, Landmark, Pencil, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { cn } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Button as IconButton } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from '../components/ui/dialog.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';

const fmtCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

const EMPTY_FORM = {
  periodo: '', cuentaBanco: '', saldoBanco: 0, saldoLibros: 0, estado: 'EN_PROCESO',
};

export default function ConciliacionPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/conciliaciones'), []);
  const {
    data: resumenData,
    loading: resumenLoading,
    error: resumenError,
  } = useFetch(() => api.get('/conciliaciones/resumen'), []);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [intentado, setIntentado] = useState(false);

  const conciliaciones = data?.items || data || [];
  const resumen = resumenData?.items || resumenData?.raw || resumenData || {};

  function abrirCrear() {
    setEditando(null);
    setForm(EMPTY_FORM);
    setIntentado(false);
    setDialogOpen(true);
  }

  function abrirEditar(c) {
    setEditando(c);
    setForm({
      periodo: c.PERIODO || c.periodo || '',
      cuentaBanco: c.BANCO_CUENTA || c.banco_cuenta || c.cuentaBanco || '',
      saldoBanco: c.SALDO_BANCO || c.saldo_banco || c.saldoBanco || 0,
      saldoLibros: c.SALDO_LIBROS || c.saldo_libros || c.saldoLibros || 0,
      estado: c.ESTADO || c.estado || 'EN_PROCESO',
    });
    setIntentado(false);
    setDialogOpen(true);
  }

  async function guardar() {
    setIntentado(true);
    if (!form.periodo?.trim() || !form.cuentaBanco?.trim()) {
      toast.error('Período y cuenta banco son obligatorios');
      return;
    }
    const sb = Number(form.saldoBanco);
    const sl = Number(form.saldoLibros);
    if (form.saldoBanco === '' || form.saldoBanco == null) {
      toast.error('El saldo en banco es obligatorio');
      return;
    }
    if (Number.isNaN(sb) || !Number.isFinite(sb)) {
      toast.error('El saldo en banco debe ser un número válido (no ingrese letras)');
      return;
    }
    if (form.saldoLibros === '' || form.saldoLibros == null) {
      toast.error('El saldo en libros es obligatorio');
      return;
    }
    if (Number.isNaN(sl) || !Number.isFinite(sl)) {
      toast.error('El saldo en libros debe ser un número válido (no ingrese letras)');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        periodo: form.periodo.trim(),
        bancoCuenta: form.cuentaBanco.trim(),
        saldoBanco: sb,
        saldoLibros: sl,
        estado: form.estado,
      };
      if (editando) {
        await api.put(`/conciliaciones/${editando.ID_CONCILIACION || editando.id || editando.id_conciliacion}`, payload);
        toast.success('Conciliación actualizada');
      } else {
        await api.post('/conciliaciones', payload);
        toast.success('Conciliación creada');
      }
      setDialogOpen(false);
      setForm(EMPTY_FORM);
      refetch().catch(() => {});
    } catch (err) {
      toast.error(err.message || 'Error al guardar');
    } finally {
      setSaving(false);
    }
  }

  function getBadgeVariant(estado) {
    switch (estado) {
      case 'CONCILIADO':
      case 'CONCILIADA': return 'success';
      case 'EN_PROCESO': return 'warning';
      case 'CON_DIFERENCIAS':
      case 'DISCREPANCIA': return 'destructive';
      default: return 'secondary';
    }
  }

  function getEstadoLabel(estado) {
    switch (estado) {
      case 'CONCILIADO':
      case 'CONCILIADA': return 'Conciliado';
      case 'EN_PROCESO': return 'En proceso';
      case 'CON_DIFERENCIAS':
      case 'DISCREPANCIA': return 'Con diferencias';
      default: return (estado || '').replace('_', ' ');
    }
  }

  const numError = (v, label) => {
    if (!intentado) return undefined;
    if (v === '' || v == null) return `${label} es obligatorio`;
    if (!Number.isFinite(Number(v))) return `${label} debe ser un número válido`;
    return undefined;
  };
  const diferenciaForm = Number(form.saldoBanco) - Number(form.saldoLibros);
  const resumenValue = (v) => (resumenLoading ? '…' : resumenError ? '—' : v);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Conciliación bancaria"
        subtitle="Cuadre mensual entre el saldo del extracto bancario y el saldo en libros"
        action={
          <Button onClick={abrirCrear} icon={<Plus className="h-4 w-4" />}>
            Nueva conciliación
          </Button>
        }
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <MetricCard
          icon={CheckCircle2}
          value={resumenValue(resumen.CONCILIADOS || resumen.CONCILIADAS || 0)}
          label="Conciliadas"
          subtitle="Periodos cuadrados"
          variant="success"
        />
        <MetricCard
          icon={Clock}
          value={resumenValue(resumen.EN_PROCESO || 0)}
          label="En proceso"
          subtitle="Pendientes de cierre"
          variant="warning"
        />
        <MetricCard
          icon={AlertTriangle}
          value={resumenValue(resumen.CON_DIFERENCIAS || resumen.CON_DISCREPANCIAS || 0)}
          label="Con diferencias"
          subtitle="Requieren revisión"
          variant="danger"
        />
      </div>

      <section className="table-container" aria-label="Conciliaciones registradas">
        {loading ? (
          <div className="space-y-3 p-4" role="status" aria-label="Cargando conciliaciones">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        ) : error ? (
          <div className="p-4">
            <ErrorState
              compact
              title="No se pudieron cargar las conciliaciones"
              message={error.message || 'Revise su conexión e intente de nuevo.'}
              onRetry={() => refetch().catch(() => {})}
            />
          </div>
        ) : conciliaciones.length === 0 ? (
          <EmptyState
            icon={Landmark}
            title="No hay conciliaciones registradas"
            subtitle="Registre la primera conciliación con el saldo del extracto y el saldo en libros del periodo."
          >
            <div className="mt-3">
              <Button size="sm" onClick={abrirCrear}>Nueva conciliación</Button>
            </div>
          </EmptyState>
        ) : (
          <div className="overflow-x-auto">
            <table className="data-table">
              <caption className="sr-only">Conciliaciones bancarias, valores en pesos colombianos</caption>
              <thead>
                <tr>
                  <th scope="col">Periodo</th>
                  <th scope="col">Cuenta bancaria</th>
                  <th scope="col" className="text-right">Saldo banco</th>
                  <th scope="col" className="text-right">Saldo libros</th>
                  <th scope="col" className="text-right">Diferencia</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Fecha de conciliación</th>
                  <th scope="col" className="text-right"><span className="sr-only">Acciones</span></th>
                </tr>
              </thead>
              <tbody>
                {conciliaciones.map((c) => {
                  const banco = Number(c.SALDO_BANCO || c.saldo_banco || 0);
                  const libros = Number(c.SALDO_LIBROS || c.saldo_libros || 0);
                  const diferencia = banco - libros;
                  const periodo = c.PERIODO || c.periodo;
                  return (
                    <tr key={c.ID_CONCILIACION || c.id || c.id_conciliacion}>
                      <td className="whitespace-nowrap font-semibold tabular-nums">{periodo}</td>
                      <td className="tabular-nums">{c.BANCO_CUENTA || c.banco_cuenta || c.cuentaBanco}</td>
                      <td className="whitespace-nowrap text-right tabular-nums">{fmtCOP.format(banco)}</td>
                      <td className="whitespace-nowrap text-right tabular-nums">{fmtCOP.format(libros)}</td>
                      <td
                        className={cn(
                          'whitespace-nowrap text-right font-bold tabular-nums',
                          diferencia !== 0 ? 'text-destructive' : 'text-tint-green-fg'
                        )}
                      >
                        {fmtCOP.format(diferencia)}
                      </td>
                      <td>
                        <Badge variant={getBadgeVariant(c.ESTADO || c.estado)}>
                          {getEstadoLabel(c.ESTADO || c.estado)}
                        </Badge>
                      </td>
                      <td className="whitespace-nowrap tabular-nums text-text-secondary">
                        {c.FECHA_CONCILIACION || c.fecha_conciliacion || '—'}
                      </td>
                      <td className="text-right">
                        <IconButton
                          variant="ghost"
                          size="icon"
                          onClick={() => abrirEditar(c)}
                          aria-label={`Editar conciliación ${periodo || ''}`}
                          className="h-10 w-10"
                        >
                          <Pencil aria-hidden="true" />
                        </IconButton>
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
        <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editando ? 'Editar conciliación' : 'Nueva conciliación'}</DialogTitle>
            <DialogDescription>
              {editando ? 'Modifique los datos de la conciliación.' : 'Registre una nueva conciliación bancaria.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                id="conc-periodo"
                label="Periodo"
                required
                value={form.periodo}
                onChange={(e) => setForm((f) => ({ ...f, periodo: e.target.value }))}
                placeholder="Ej. 2026-01"
                error={intentado && !form.periodo?.trim() ? 'El periodo es obligatorio' : undefined}
              />
              <Input
                id="conc-cuenta"
                label="Cuenta bancaria"
                required
                value={form.cuentaBanco}
                onChange={(e) => setForm((f) => ({ ...f, cuentaBanco: e.target.value }))}
                placeholder="Número de cuenta"
                error={intentado && !form.cuentaBanco?.trim() ? 'La cuenta bancaria es obligatoria' : undefined}
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                id="conc-saldo-banco"
                label="Saldo en banco (COP)"
                required
                type="text"
                inputMode="decimal"
                className="text-right tabular-nums"
                value={form.saldoBanco}
                onChange={(e) => {
                  const v = e.target.value;
                  if (/^[0-9.-]*$/.test(v)) setForm((f) => ({ ...f, saldoBanco: v }));
                }}
                placeholder="Ej. 5000000"
                error={numError(form.saldoBanco, 'El saldo en banco')}
              />
              <Input
                id="conc-saldo-libros"
                label="Saldo en libros (COP)"
                required
                type="text"
                inputMode="decimal"
                className="text-right tabular-nums"
                value={form.saldoLibros}
                onChange={(e) => {
                  const v = e.target.value;
                  if (/^[0-9.-]*$/.test(v)) setForm((f) => ({ ...f, saldoLibros: v }));
                }}
                placeholder="Ej. 5000000"
                error={numError(form.saldoLibros, 'El saldo en libros')}
              />
            </div>
            {Number.isFinite(diferenciaForm) && (
              <p className="flex items-center justify-between rounded-md border border-border bg-surface-container px-3 py-2 text-sm">
                <span className="text-text-secondary">Diferencia (banco − libros)</span>
                <span
                  className={cn(
                    'font-bold tabular-nums',
                    diferenciaForm !== 0 ? 'text-destructive' : 'text-tint-green-fg'
                  )}
                >
                  {fmtCOP.format(diferenciaForm)}
                </span>
              </p>
            )}
            <Select
              id="conc-estado"
              label="Estado"
              value={form.estado}
              onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
            >
              <option value="EN_PROCESO">En proceso</option>
              <option value="CONCILIADO">Conciliado</option>
              <option value="CON_DIFERENCIAS">Con diferencias</option>
            </Select>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={saving}>Cancelar</Button>
            <Button onClick={guardar} disabled={saving} loading={saving}>{saving ? 'Guardando…' : 'Guardar'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
