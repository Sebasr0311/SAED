import { useState } from 'react';
import { List, Pencil, PieChart, Plus, Scale, Trash2, TrendingDown, TrendingUp, Wallet } from 'lucide-react';
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
import { Tabs, TabsList, TabsTrigger, TabsContent } from '../components/ui/tabs.tsx';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from '../components/ui/dialog.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';

const fmtCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' });

const EMPTY_FORM = { rubro: '', tipo: 'INGRESO', montoPresupuestado: 0, estado: 'APROBADO', vigenciaAnio: new Date().getFullYear() };

const ESTADO_LABEL = { BORRADOR: 'Borrador', APROBADO: 'Aprobado', CERRADO: 'Cerrado' };
const TIPO_LABEL = { INGRESO: 'Ingreso', EGRESO: 'Egreso' };

function SkeletonRows({ n = 3 }) {
  return (
    <div className="space-y-3 p-4" role="status" aria-label="Cargando">
      {Array.from({ length: n }).map((_, i) => (
        <Skeleton key={i} className="h-9 w-full" />
      ))}
    </div>
  );
}

// Execution status always ships with a text label, never colour alone.
function ejecucionEstado(pct) {
  if (pct > 100) return { variant: 'destructive', label: 'Sobreejecutado', bar: 'bg-btn-danger' };
  if (pct > 80) return { variant: 'warning', label: 'Cerca del tope', bar: 'bg-tint-amber-fg' };
  return { variant: 'secondary', label: 'En rango', bar: 'bg-on-surface/70' };
}

export default function PresupuestoPage() {
  const [tabActiva, setTabActiva] = useState('lista');
  const { data, loading, error, refetch } = useFetch(() => api.get('/presupuestos'), []);
  const {
    data: ejecucionData,
    loading: ejecucionLoading,
    error: ejecucionError,
    refetch: refetchEjecucion,
  } = useFetch(() => api.get('/presupuestos/ejecucion'), [tabActiva === 'ejecucion']);
  const {
    data: resumenData,
    loading: resumenLoading,
    error: resumenError,
    refetch: refetchResumen,
  } = useFetch(() => api.get('/presupuestos/resumen'), [tabActiva === 'resumen']);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editando, setEditando] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [intentado, setIntentado] = useState(false);

  const presupuestos = data?.items || data || [];
  const ejecucion = ejecucionData?.items || ejecucionData || [];
  const resumen = resumenData?.items || resumenData?.raw || resumenData || {};

  function abrirCrear() {
    setEditando(null);
    setForm(EMPTY_FORM);
    setIntentado(false);
    setDialogOpen(true);
  }

  function abrirEditar(p) {
    setEditando(p);
    setForm({
      rubro: p.RUBRO || p.rubro || '',
      tipo: p.TIPO || p.tipo || 'INGRESO',
      montoPresupuestado: p.MONTO_PRESUPUESTADO || p.monto_presupuestado || p.montoPresupuestado || 0,
      estado: p.ESTADO || p.estado || 'APROBADO',
      vigenciaAnio: p.VIGENCIA_ANIO || p.vigencia_anio || p.vigenciaAnio || new Date().getFullYear(),
    });
    setIntentado(false);
    setDialogOpen(true);
  }

  async function guardar() {
    setIntentado(true);
    if (!form.rubro?.trim()) {
      toast.error('El rubro es obligatorio');
      return;
    }
    const monto = Number(form.montoPresupuestado);
    if (form.montoPresupuestado === '' || Number.isNaN(monto) || !Number.isFinite(monto) || monto <= 0) {
      toast.error('El monto presupuestado debe ser un número positivo mayor a 0');
      return;
    }
    const anio = Number(form.vigenciaAnio);
    if (form.vigenciaAnio === '' || Number.isNaN(anio) || !Number.isInteger(anio) || anio < 2000 || anio > 2100) {
      toast.error('La vigencia debe ser un año válido (ej: 2026)');
      return;
    }
    setSaving(true);
    try {
      const payload = {
        rubro: form.rubro.trim(),
        tipo: form.tipo,
        montoPresupuestado: monto,
        vigenciaAnio: anio,
        estado: form.estado,
      };
      if (editando) {
        const id = editando.ID_PRESUPUESTO || editando.idPresupuesto || editando.ID || editando.id;
        await api.put(`/presupuestos/${id}`, payload);
        toast.success('Presupuesto actualizado');
      } else {
        await api.post('/presupuestos', payload);
        toast.success('Presupuesto creado');
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

  async function eliminar() {
    if (!deleteTarget) return;
    try {
      const id = deleteTarget.ID_PRESUPUESTO || deleteTarget.idPresupuesto || deleteTarget.ID || deleteTarget.id;
      await api.del(`/presupuestos/${id}`);
      toast.success('Presupuesto eliminado');
      refetch().catch(() => {});
    } catch (err) {
      toast.error(err.message || 'Error al eliminar');
    }
  }

  const montoNum = Number(form.montoPresupuestado);
  const anioNum = Number(form.vigenciaAnio);
  const errRubro = intentado && !form.rubro?.trim() ? 'El rubro es obligatorio' : undefined;
  const errMonto =
    intentado && (form.montoPresupuestado === '' || !Number.isFinite(montoNum) || montoNum <= 0)
      ? 'Ingrese un monto mayor a 0'
      : undefined;
  const errAnio =
    intentado && (form.vigenciaAnio === '' || !Number.isInteger(anioNum) || anioNum < 2000 || anioNum > 2100)
      ? 'Ingrese un año válido, por ejemplo 2026'
      : undefined;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Presupuesto"
        subtitle="Rubros presupuestales de ingresos y egresos de la copropiedad y su ejecución"
        action={
          <Button onClick={abrirCrear} icon={<Plus className="h-4 w-4" />}>
            Nuevo presupuesto
          </Button>
        }
      />

      <Tabs value={tabActiva} onValueChange={setTabActiva}>
        <div className="-mx-1 overflow-x-auto px-1 pb-1">
        <TabsList>
          <TabsTrigger value="lista" className="gap-1.5">
            <List className="h-4 w-4" aria-hidden="true" />
            Presupuestos
          </TabsTrigger>
          <TabsTrigger value="ejecucion" className="gap-1.5">
            <TrendingUp className="h-4 w-4" aria-hidden="true" />
            Ejecución
          </TabsTrigger>
          <TabsTrigger value="resumen" className="gap-1.5">
            <PieChart className="h-4 w-4" aria-hidden="true" />
            Resumen
          </TabsTrigger>
        </TabsList>
        </div>

        <TabsContent value="lista" className="mt-4">
          <section className="table-container" aria-label="Rubros presupuestales">
            {loading ? (
              <SkeletonRows />
            ) : error ? (
              <div className="p-4">
                <ErrorState
                  compact
                  title="No se pudieron cargar los presupuestos"
                  message={error.message || 'Revise su conexión e intente de nuevo.'}
                  onRetry={() => refetch().catch(() => {})}
                />
              </div>
            ) : presupuestos.length === 0 ? (
              <EmptyState
                icon={Wallet}
                title="No hay presupuestos registrados"
                subtitle="Defina los rubros de ingreso y egreso de la vigencia para seguir su ejecución."
              >
                <div className="mt-3">
                  <Button size="sm" onClick={abrirCrear}>Nuevo presupuesto</Button>
                </div>
              </EmptyState>
            ) : (
              <div className="max-h-[70vh] overflow-auto">
                <table className="data-table">
                  <caption className="sr-only">Rubros presupuestales, valores en pesos colombianos</caption>
                  <thead>
                    <tr>
                      <th scope="col">Rubro</th>
                      <th scope="col">Tipo</th>
                      <th scope="col" className="text-right">Presupuestado</th>
                      <th scope="col" className="text-right">Ejecutado</th>
                      <th scope="col" className="text-right">Ejecución</th>
                      <th scope="col">Estado</th>
                      <th scope="col" className="text-right"><span className="sr-only">Acciones</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {presupuestos.map((p) => {
                      const presupuestado = Number(p.MONTO_PRESUPUESTADO || p.monto_presupuestado || p.montoPresupuestado || 0);
                      const ejecutado = Number(p.MONTO_EJECUTADO || p.monto_ejecutado || p.montoEjecutado || 0);
                      const pct = presupuestado > 0 ? (ejecutado / presupuestado) * 100 : 0;
                      const tipo = p.TIPO || p.tipo;
                      const estado = p.ESTADO || p.estado;
                      const rubro = p.RUBRO || p.rubro;
                      return (
                        <tr key={p.ID_PRESUPUESTO || p.idPresupuesto || p.ID || p.id}>
                          <td className="min-w-[10rem] font-semibold">{rubro}</td>
                          <td>
                            <Badge variant={tipo === 'INGRESO' ? 'success' : 'lavender'}>
                              {TIPO_LABEL[tipo] || tipo}
                            </Badge>
                          </td>
                          <td className="whitespace-nowrap text-right tabular-nums">{fmtCOP.format(presupuestado)}</td>
                          <td className="whitespace-nowrap text-right tabular-nums">{fmtCOP.format(ejecutado)}</td>
                          <td
                            className={cn(
                              'whitespace-nowrap text-right font-semibold tabular-nums',
                              pct > 100 && 'text-destructive'
                            )}
                          >
                            {pct.toFixed(1)} %
                          </td>
                          <td>
                            <Badge variant={estado === 'APROBADO' ? 'success' : estado === 'CERRADO' ? 'secondary' : 'outline'}>
                              {ESTADO_LABEL[estado] || estado}
                            </Badge>
                          </td>
                          <td className="whitespace-nowrap text-right">
                            <IconButton
                              variant="ghost"
                              size="icon"
                              className="h-10 w-10"
                              onClick={() => abrirEditar(p)}
                              aria-label={`Editar ${rubro || 'presupuesto'}`}
                            >
                              <Pencil aria-hidden="true" />
                            </IconButton>
                            <IconButton
                              variant="ghost"
                              size="icon"
                              className="h-10 w-10 text-destructive hover:text-destructive"
                              onClick={() => setDeleteTarget(p)}
                              aria-label={`Eliminar ${rubro || 'presupuesto'}`}
                            >
                              <Trash2 aria-hidden="true" />
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
        </TabsContent>

        <TabsContent value="ejecucion" className="mt-4">
          <section className="rounded-lg border border-border bg-surface shadow-sm">
            <div className="border-b border-border px-4 py-3.5 sm:px-5">
              <h2 className="text-base font-bold text-on-surface">Ejecución presupuestal por rubro</h2>
              <p className="mt-0.5 text-xs text-text-muted">Monto ejecutado frente al presupuestado</p>
            </div>
            <div className="p-4 sm:p-5">
              {ejecucionLoading ? (
                <SkeletonRows n={2} />
              ) : ejecucionError ? (
                <ErrorState
                  compact
                  title="No se pudo cargar la ejecución presupuestal"
                  onRetry={() => refetchEjecucion().catch(() => {})}
                />
              ) : ejecucion.length === 0 ? (
                <EmptyState
                  icon={TrendingUp}
                  title="No hay datos de ejecución disponibles"
                  subtitle="La ejecución aparece cuando se registran gastos asociados a un rubro."
                />
              ) : (
                <ul className="space-y-5">
                  {ejecucion.map((e, i) => {
                    const presupuestado = Number(e.MONTO_PRESUPUESTADO || e.monto_presupuestado || 0);
                    const ejecutado = Number(e.MONTO_EJECUTADO || e.monto_ejecutado || 0);
                    const pct = presupuestado > 0 ? (ejecutado / presupuestado) * 100 : 0;
                    const st = ejecucionEstado(pct);
                    const rubro = e.RUBRO || e.rubro;
                    return (
                      <li key={i} className="space-y-1.5">
                        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
                          <div className="flex items-center gap-2">
                            <span className="text-sm font-semibold text-on-surface">{rubro}</span>
                            {st.variant !== 'secondary' && <Badge variant={st.variant}>{st.label}</Badge>}
                          </div>
                          <span className="text-sm tabular-nums text-text-secondary">
                            <span className="font-bold text-on-surface">{fmtCOP.format(ejecutado)}</span> de{' '}
                            {fmtCOP.format(presupuestado)}
                          </span>
                        </div>
                        <div
                          className="h-2.5 w-full overflow-hidden rounded-[3px] bg-surface-dim"
                          role="img"
                          aria-label={`${rubro}: ${pct.toFixed(1)} % ejecutado`}
                        >
                          <div
                            className={cn('h-full rounded-[3px] transition-[width] duration-300 ease-out', st.bar)}
                            style={{ width: `${Math.min(pct, 100)}%` }}
                          />
                        </div>
                        <div className="text-right text-xs font-semibold tabular-nums text-text-secondary">
                          {pct.toFixed(1)} %
                        </div>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          </section>
        </TabsContent>

        <TabsContent value="resumen" className="mt-4">
          {resumenError ? (
            <ErrorState
              title="No se pudo cargar el resumen presupuestal"
              message="Revise su conexión e intente de nuevo."
              onRetry={() => refetchResumen().catch(() => {})}
            />
          ) : (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <MetricCard
                icon={TrendingUp}
                value={resumenLoading ? '…' : fmtCOP.format(Number(resumen.TOTAL_INGRESOS || 0))}
                label="Total ingresos"
                variant="success"
              />
              <MetricCard
                icon={TrendingDown}
                value={resumenLoading ? '…' : fmtCOP.format(Number(resumen.TOTAL_EGRESOS || 0))}
                label="Total egresos"
                variant="danger"
              />
              <MetricCard
                icon={Scale}
                value={resumenLoading ? '…' : fmtCOP.format(Number(resumen.SALDO || 0))}
                label="Saldo"
                variant="primary"
              />
              <MetricCard
                icon={PieChart}
                value={resumenLoading ? '…' : `${Number(resumen.PORCENTAJE_EJECUCION || 0).toFixed(1)} %`}
                label="Ejecución general"
                variant="warning"
              />
            </div>
          )}
        </TabsContent>
      </Tabs>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editando ? 'Editar presupuesto' : 'Nuevo presupuesto'}</DialogTitle>
            <DialogDescription>
              {editando ? 'Modifique los datos del presupuesto.' : 'Defina un nuevo rubro presupuestal.'}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-2">
            <Input
              id="presu-rubro"
              label="Rubro"
              required
              value={form.rubro}
              onChange={(e) => setForm((f) => ({ ...f, rubro: e.target.value }))}
              placeholder="Ej. Mantenimiento, servicios públicos"
              error={errRubro}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <Select
                id="presu-tipo"
                label="Tipo"
                required
                value={form.tipo}
                onChange={(e) => setForm((f) => ({ ...f, tipo: e.target.value }))}
              >
                <option value="INGRESO">Ingreso</option>
                <option value="EGRESO">Egreso</option>
              </Select>
              <Select
                id="presu-estado"
                label="Estado"
                value={form.estado}
                onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
              >
                <option value="BORRADOR">Borrador</option>
                <option value="APROBADO">Aprobado</option>
                <option value="CERRADO">Cerrado</option>
              </Select>
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                id="presu-monto"
                label="Monto presupuestado (COP)"
                required
                type="number"
                min="1"
                className="tabular-nums"
                value={form.montoPresupuestado}
                onChange={(e) => setForm((f) => ({ ...f, montoPresupuestado: e.target.value }))}
                error={errMonto}
              />
              <Input
                id="presu-vigencia"
                label="Vigencia (año)"
                required
                type="number"
                min="2020"
                max="2099"
                className="tabular-nums"
                value={form.vigenciaAnio}
                onChange={(e) => setForm((f) => ({ ...f, vigenciaAnio: e.target.value }))}
                error={errAnio}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={saving}>Cancelar</Button>
            <Button onClick={guardar} disabled={saving} loading={saving}>{saving ? 'Guardando…' : 'Guardar'}</Button>
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
