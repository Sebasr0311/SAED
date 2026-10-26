import { useEffect, useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Input } from '../components/ui/Form.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { toast } from 'sonner';

const RESULT_LABELS = {
  periodo: 'Periodo',
  creadas: 'Cuotas creadas',
  existentes: 'Ya existían',
  vencidas: 'Cuotas vencidas',
  cuotasConInteres: 'Cuotas con interés',
};

function resultLabel(key) {
  if (RESULT_LABELS[key]) return RESULT_LABELS[key];
  const spaced = key.replace(/([A-Z])/g, ' $1').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

function resultValue(value) {
  if (value == null) return '—';
  if (typeof value === 'number') return value.toLocaleString('es-CO');
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

const EMPTY = {
  cuotaFija: '',
  porcDescuentoProntoPago: 0,
  diasProntoPago: 0,
  porcInteresMoraMensual: 0,
  topeInteresMoraMensual: '',
  diasGracia: 0,
  diaVencimiento: 10,
};

function currentPeriod() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function numberOrNull(value) {
  return value === '' || value == null ? null : Number(value);
}

/**
 * Monthly billing of the property (F06-02, DEC-08): fee settings (fixed fee or budget share by
 * coefficient, early-payment discount, late interest, both disabled by default), manual generation of a
 * period (replaying it creates nothing) and the overdue pass.
 */
export default function FacturacionAdminPage() {
  const { data, loading, error, refetch } = useFetch(() => api.get('/finanzas/facturacion/configuracion'), []);
  const [form, setForm] = useState(EMPTY);
  const [periodo, setPeriodo] = useState(currentPeriod());
  const [saving, setSaving] = useState(false);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState(null);

  useEffect(() => {
    const settings = data?.data ?? data;
    if (settings && typeof settings === 'object') {
      setForm({
        cuotaFija: settings.cuotaFija ?? '',
        porcDescuentoProntoPago: settings.porcDescuentoProntoPago ?? 0,
        diasProntoPago: settings.diasProntoPago ?? 0,
        porcInteresMoraMensual: settings.porcInteresMoraMensual ?? 0,
        topeInteresMoraMensual: settings.topeInteresMoraMensual ?? '',
        diasGracia: settings.diasGracia ?? 0,
        diaVencimiento: settings.diaVencimiento ?? 10,
      });
    }
  }, [data]);

  function set(field, value) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function guardar() {
    setSaving(true);
    try {
      await api.put('/finanzas/facturacion/configuracion', {
        cuotaFija: numberOrNull(form.cuotaFija),
        porcDescuentoProntoPago: Number(form.porcDescuentoProntoPago) || 0,
        diasProntoPago: Number(form.diasProntoPago) || 0,
        porcInteresMoraMensual: Number(form.porcInteresMoraMensual) || 0,
        topeInteresMoraMensual: numberOrNull(form.topeInteresMoraMensual),
        diasGracia: Number(form.diasGracia) || 0,
        diaVencimiento: Number(form.diaVencimiento) || 10,
      });
      toast.success('Configuración de cobro guardada');
      refetch().catch(() => {});
    } catch (err) {
      toast.error(err.message || 'No se pudo guardar la configuración');
    } finally {
      setSaving(false);
    }
  }

  async function generar() {
    setRunning(true);
    try {
      const res = await api.post('/finanzas/facturacion/generar', { periodo });
      const out = res?.data ?? res;
      setResult({ tipo: 'generacion', ...out });
      toast.success(`Periodo ${out.periodo}: ${out.creadas} cuotas creadas, ${out.existentes} ya existían`);
    } catch (err) {
      toast.error(err.message || 'No se pudieron generar las cuotas');
    } finally {
      setRunning(false);
    }
  }

  async function vencimientos() {
    setRunning(true);
    try {
      const res = await api.post('/finanzas/facturacion/vencimientos', {});
      const out = res?.data ?? res;
      setResult({ tipo: 'vencimientos', ...out });
      toast.success(`${out.vencidas} cuotas vencidas, ${out.cuotasConInteres} con interés`);
    } catch (err) {
      toast.error(err.message || 'No se pudo procesar los vencimientos');
    } finally {
      setRunning(false);
    }
  }

  const numField = (id, label, field, extra = {}) => (
    <Input
      id={id}
      label={label}
      type="number"
      inputMode="decimal"
      className="tabular-nums"
      value={form[field]}
      onChange={(e) => set(field, e.target.value)}
      {...extra}
    />
  );

  return (
    <div className="space-y-6">
      <PageHeader
        title="Facturación mensual"
        subtitle="Cuotas de administración por coeficiente, pronto pago e interés de mora (desactivados por defecto)"
      />

      <section
        aria-labelledby="fact-config-title"
        className="rounded-lg border border-border bg-surface shadow-sm"
      >
        <div className="border-b border-border px-4 py-3.5 sm:px-5">
          <h2 id="fact-config-title" className="text-base font-bold text-on-surface">
            Configuración de cobro
          </h2>
          <p className="mt-0.5 text-xs text-text-muted">
            Deje la cuota fija vacía para distribuir el presupuesto según el coeficiente de cada unidad.
          </p>
        </div>
        <div className="p-4 sm:p-5">
          {loading ? (
            <div className="grid gap-4 sm:grid-cols-2" role="status" aria-label="Cargando configuración">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-14 w-full" />
              ))}
            </div>
          ) : error ? (
            <ErrorState
              compact
              title="No se pudo cargar la configuración de cobro"
              message="Para no sobrescribirla con valores por defecto, el guardado queda deshabilitado hasta recargarla."
              onRetry={() => refetch().catch(() => {})}
            />
          ) : (
            <div className="grid gap-x-5 gap-y-4 sm:grid-cols-2">
              {numField('fact-cuota-fija', 'Cuota fija mensual por unidad (COP, opcional)', 'cuotaFija', { min: '0', placeholder: 'Presupuesto por coeficiente' })}
              {numField('fact-dia-venc', 'Día de vencimiento (1 a 28)', 'diaVencimiento', { min: '1', max: '28' })}
              {numField('fact-desc', 'Descuento por pronto pago (%)', 'porcDescuentoProntoPago', { min: '0', max: '100', step: '0.01' })}
              {numField('fact-dias-desc', 'Días del mes con descuento', 'diasProntoPago', { min: '0', max: '31' })}
              {numField('fact-interes', 'Interés de mora mensual simple (%)', 'porcInteresMoraMensual', { min: '0', max: '100', step: '0.01' })}
              {numField('fact-tope', 'Tope mensual del interés (%, opcional)', 'topeInteresMoraMensual', { min: '0', max: '100', step: '0.01' })}
              {numField('fact-gracia', 'Días de gracia antes de la mora', 'diasGracia', { min: '0', max: '365' })}
            </div>
          )}
          <div className="mt-5 flex justify-end border-t border-border-subtle pt-4">
            <Button onClick={guardar} disabled={saving || loading || Boolean(error)} loading={saving}>
              {saving ? 'Guardando…' : 'Guardar configuración'}
            </Button>
          </div>
        </div>
      </section>

      <section
        aria-labelledby="fact-generar-title"
        className="rounded-lg border border-border bg-surface shadow-sm"
      >
        <div className="border-b border-border px-4 py-3.5 sm:px-5">
          <h2 id="fact-generar-title" className="text-base font-bold text-on-surface">
            Generar cuotas del periodo
          </h2>
          <p className="mt-0.5 text-xs text-text-muted">
            Genera una cuota por unidad con coeficiente registrado. Repetir el mismo periodo no crea cuotas duplicadas.
          </p>
        </div>
        <div className="space-y-4 p-4 sm:p-5">
          <div className="flex flex-wrap items-end gap-3">
            <Input
              id="fact-periodo"
              label="Periodo"
              type="month"
              value={periodo}
              onChange={(e) => setPeriodo(e.target.value)}
              className="w-48 tabular-nums"
            />
            <Button onClick={generar} disabled={running || !periodo} loading={running}>
              Generar cuotas
            </Button>
            <Button variant="outline" onClick={vencimientos} disabled={running}>
              Procesar vencimientos
            </Button>
          </div>
          {result && (
            <div
              role="status"
              className="rounded-md border border-tint-green-fg/25 bg-tint-green-bg/60 p-3"
            >
              <p className="mb-2 text-[13px] font-bold text-on-surface">
                {result.tipo === 'generacion' ? 'Resultado de la generación' : 'Resultado del proceso de vencimientos'}
              </p>
              <dl className="grid grid-cols-2 gap-x-4 gap-y-2 sm:grid-cols-4">
                {Object.entries(result)
                  .filter(([key]) => key !== 'tipo')
                  .map(([key, value]) => (
                    <div key={key} className="min-w-0">
                      <dt className="text-xs text-text-secondary">{resultLabel(key)}</dt>
                      <dd className="truncate text-sm font-bold tabular-nums text-on-surface">{resultValue(value)}</dd>
                    </div>
                  ))}
              </dl>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
