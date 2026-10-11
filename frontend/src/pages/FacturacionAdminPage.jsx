import { useEffect, useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import { api } from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card.tsx';
import { toast } from 'sonner';

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
  const { data, loading, refetch } = useFetch(() => api.get('/finanzas/facturacion/configuracion'), []);
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
      refetch();
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

  const field = 'border rounded px-3 py-2 text-sm w-full';

  return (
    <div className="space-y-6">
      <PageHeader
        title="Facturación mensual"
        subtitle="Cuotas de administración por coeficiente, pronto pago e interés de mora (desactivados por defecto)"
      />

      <Card>
        <CardHeader>
          <CardTitle>Configuración de cobro</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <p className="text-sm text-muted-foreground">Cargando configuración…</p>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-1 text-sm font-medium">
                Cuota fija mensual por unidad (vacío = presupuesto por coeficiente)
                <input type="number" min="0" className={field} value={form.cuotaFija}
                  onChange={(e) => set('cuotaFija', e.target.value)} />
              </label>
              <label className="flex flex-col gap-1 text-sm font-medium">
                Día de vencimiento (1-28)
                <input type="number" min="1" max="28" className={field} value={form.diaVencimiento}
                  onChange={(e) => set('diaVencimiento', e.target.value)} />
              </label>
              <label className="flex flex-col gap-1 text-sm font-medium">
                Descuento por pronto pago (%)
                <input type="number" min="0" max="100" step="0.01" className={field}
                  value={form.porcDescuentoProntoPago}
                  onChange={(e) => set('porcDescuentoProntoPago', e.target.value)} />
              </label>
              <label className="flex flex-col gap-1 text-sm font-medium">
                Días del mes con descuento
                <input type="number" min="0" max="31" className={field} value={form.diasProntoPago}
                  onChange={(e) => set('diasProntoPago', e.target.value)} />
              </label>
              <label className="flex flex-col gap-1 text-sm font-medium">
                Interés de mora mensual simple (%)
                <input type="number" min="0" max="100" step="0.01" className={field}
                  value={form.porcInteresMoraMensual}
                  onChange={(e) => set('porcInteresMoraMensual', e.target.value)} />
              </label>
              <label className="flex flex-col gap-1 text-sm font-medium">
                Tope mensual del interés (%, opcional)
                <input type="number" min="0" max="100" step="0.01" className={field}
                  value={form.topeInteresMoraMensual}
                  onChange={(e) => set('topeInteresMoraMensual', e.target.value)} />
              </label>
              <label className="flex flex-col gap-1 text-sm font-medium">
                Días de gracia antes de la mora
                <input type="number" min="0" max="365" className={field} value={form.diasGracia}
                  onChange={(e) => set('diasGracia', e.target.value)} />
              </label>
            </div>
          )}
          <div className="mt-4">
            <Button onClick={guardar} disabled={saving || loading}>
              {saving ? 'Guardando…' : 'Guardar configuración'}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Generar cuotas del periodo</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">
            Genera una cuota por unidad con coeficiente registrado. Repetir el mismo periodo no crea cuotas duplicadas.
          </p>
          <div className="flex flex-wrap items-end gap-3">
            <label className="flex flex-col gap-1 text-sm font-medium">
              Periodo (AAAA-MM)
              <input type="month" className="border rounded px-3 py-2 text-sm" value={periodo}
                onChange={(e) => setPeriodo(e.target.value)} />
            </label>
            <Button onClick={generar} disabled={running || !periodo}>Generar cuotas</Button>
            <Button variant="outline" onClick={vencimientos} disabled={running}>Procesar vencimientos</Button>
          </div>
          {result && (
            <pre className="rounded bg-muted p-3 text-xs">{JSON.stringify(result, null, 2)}</pre>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
