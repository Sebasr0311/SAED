import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent, CardDescription, CardFooter } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input } from '../components/ui/input.tsx';
import { Label } from '../components/ui/label.tsx';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog.tsx';
import { toast } from 'sonner';

export default function SuperAdminPlanesPage() {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState({
    codigo: '',
    nombre: '',
    descripcion: '',
    precioMensual: 150000,
    maxPropiedades: 1,
    maxUnidades: 50,
    maxUsuarios: 100,
    maxAlmacenamientoGb: 5,
  });

  async function loadData() {
    try {
      setLoading(true);
      const res = await api.get('/platform/plans');
      const list = res?.data || res || [];
      setPlans(Array.isArray(list) ? list : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar planes de plataforma');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  async function handleCreate(e) {
    e.preventDefault();
    if (!form.nombre.trim()) {
      toast.error('El nombre del plan es obligatorio');
      return;
    }
    try {
      setSubmitting(true);
      await api.post('/platform/plans', form);
      toast.success('Plan SaaS creado exitosamente');
      setShowModal(false);
      setForm({
        codigo: '',
        nombre: '',
        descripcion: '',
        precioMensual: 150000,
        maxPropiedades: 1,
        maxUnidades: 50,
        maxUsuarios: 100,
        maxAlmacenamientoGb: 5,
      });
      loadData();
    } catch (err) {
      console.error(err);
      toast.error('Error al crear el plan');
    } finally {
      setSubmitting(false);
    }
  }

  function formatCurrency(amount) {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 0,
    }).format(amount || 0);
  }

  return (
    <div className="p-6 space-y-6 animate-fadeIn">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-border pb-6">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-2xl">loyalty</span>
            Planes y Tarifas SaaS
          </h1>
          <p className="text-muted-foreground mt-1 text-sm">
            Catálogo global de suscripciones para las organizaciones clientes de SAED.
          </p>
        </div>
        <Button onClick={() => setShowModal(true)} className="gap-2 shrink-0">
          <span className="material-symbols-outlined text-sm">add_circle</span>
          Nuevo Plan
        </Button>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-96 w-full rounded-xl" />
          ))}
        </div>
      ) : plans.length === 0 ? (
        <div className="text-center py-16 text-muted-foreground space-y-2">
          <span className="material-symbols-outlined text-5xl text-muted-foreground/60">inventory_2</span>
          <p className="text-base font-medium">No hay planes registrados en la base de datos.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {plans.map((plan) => (
            <Card
              key={plan.id || plan.codigo}
              className="flex flex-col justify-between border-border/80 hover:border-primary/50 transition-all shadow-sm"
            >
              <CardHeader className="space-y-3 pb-4">
                <div className="flex justify-between items-start">
                  <div>
                    <Badge variant="outline" className="font-mono text-[10px] uppercase mb-1.5">
                      {plan.codigo}
                    </Badge>
                    <CardTitle className="text-xl font-bold">{plan.nombre}</CardTitle>
                  </div>
                  <Badge
                    variant={plan.estado === 'ACTIVO' ? 'default' : 'secondary'}
                    className={
                      plan.estado === 'ACTIVO'
                        ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20 text-xs'
                        : 'text-xs'
                    }
                  >
                    {plan.estado || 'ACTIVO'}
                  </Badge>
                </div>
                <CardDescription className="text-xs min-h-[32px]">
                  {plan.descripcion || 'Plan estándar para administración y control residencial.'}
                </CardDescription>
                <div className="pt-2 border-t border-border">
                  <div className="text-2xl font-extrabold text-foreground">{formatCurrency(plan.precioMensual)}</div>
                  <span className="text-xs text-muted-foreground">facturado mensual</span>
                </div>
              </CardHeader>

              <CardContent className="space-y-3 text-sm pt-2">
                <div className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Límites y Capacidades
                </div>
                <ul className="space-y-2 text-xs">
                  <li className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-sm text-primary">apartment</span>
                    <span>
                      Hasta <strong className="text-foreground">{plan.maxPropiedades || 1}</strong> propiedades
                    </span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-sm text-primary">home</span>
                    <span>
                      Hasta <strong className="text-foreground">{plan.maxUnidades || 50}</strong> unidades residenciales
                    </span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-sm text-primary">group</span>
                    <span>
                      Hasta <strong className="text-foreground">{plan.maxUsuarios || 100}</strong> usuarios registrados
                    </span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-sm text-primary">cloud</span>
                    <span>
                      <strong className="text-foreground">{plan.maxAlmacenamientoGb || 5} GB</strong> almacenamiento
                    </span>
                  </li>
                </ul>
              </CardContent>

              <CardFooter className="pt-4 border-t border-border/60">
                <div className="w-full text-center text-xs text-muted-foreground font-mono">
                  ID Plan: #{plan.id}
                </div>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}

      {/* Modal Accesible Crear Plan */}
      <Dialog open={showModal} onOpenChange={setShowModal}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg font-bold">
              <span className="material-symbols-outlined text-primary">add_circle</span>
              Registrar Plan SaaS
            </DialogTitle>
          </DialogHeader>

          <form onSubmit={handleCreate} className="space-y-4 pt-2">
            <div className="space-y-1.5">
              <Label htmlFor="plan-nombre" className="text-xs font-semibold uppercase text-muted-foreground">
                Nombre del Plan *
              </Label>
              <Input
                id="plan-nombre"
                required
                value={form.nombre}
                onChange={(e) => setForm({ ...form, nombre: e.target.value })}
                placeholder="Ej. Plan Residencial Pro"
                className="text-sm"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="plan-codigo" className="text-xs font-semibold uppercase text-muted-foreground">
                Código Único
              </Label>
              <Input
                id="plan-codigo"
                value={form.codigo}
                onChange={(e) => setForm({ ...form, codigo: e.target.value })}
                placeholder="Ej. PRO_MENSUAL (Auto si se deja vacío)"
                className="text-sm font-mono uppercase"
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="plan-precio" className="text-xs font-semibold uppercase text-muted-foreground">
                Precio Mensual (COP) *
              </Label>
              <Input
                id="plan-precio"
                type="number"
                min="0"
                required
                value={form.precioMensual}
                onChange={(e) => setForm({ ...form, precioMensual: Number(e.target.value) })}
                className="text-sm font-mono"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="plan-props" className="text-xs font-semibold uppercase text-muted-foreground">
                  Máx. Propiedades
                </Label>
                <Input
                  id="plan-props"
                  type="number"
                  min="1"
                  value={form.maxPropiedades}
                  onChange={(e) => setForm({ ...form, maxPropiedades: Number(e.target.value) })}
                  className="text-sm font-mono"
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="plan-unidades" className="text-xs font-semibold uppercase text-muted-foreground">
                  Máx. Unidades
                </Label>
                <Input
                  id="plan-unidades"
                  type="number"
                  min="1"
                  value={form.maxUnidades}
                  onChange={(e) => setForm({ ...form, maxUnidades: Number(e.target.value) })}
                  className="text-sm font-mono"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="plan-desc" className="text-xs font-semibold uppercase text-muted-foreground">
                Descripción Corta
              </Label>
              <Input
                id="plan-desc"
                value={form.descripcion}
                onChange={(e) => setForm({ ...form, descripcion: e.target.value })}
                placeholder="Descripción comercial de capacidades…"
                className="text-sm"
              />
            </div>

            <DialogFooter className="pt-3 gap-2">
              <Button type="button" variant="outline" onClick={() => setShowModal(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={submitting} className="gap-2">
                {submitting ? 'Guardando…' : 'Crear Plan'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
