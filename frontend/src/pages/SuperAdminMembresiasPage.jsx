import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
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

export default function SuperAdminMembresiasPage() {
  const [memberships, setMemberships] = useState([]);
  const [orgs, setOrgs] = useState([]);
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState({
    idOrganizacion: '',
    idPlan: '',
    estado: 'ACTIVA',
    autoRenovar: 'S',
  });

  async function loadData() {
    try {
      setLoading(true);
      const [resMem, resOrgs, resPlans] = await Promise.all([
        api.get('/platform/memberships'),
        api.get('/organizations'),
        api.get('/platform/plans'),
      ]);

      const memList = resMem?.data || resMem || [];
      const orgList = resOrgs?.data || resOrgs || [];
      const planList = resPlans?.data || resPlans || [];

      setMemberships(Array.isArray(memList) ? memList : []);
      setOrgs(Array.isArray(orgList) ? orgList : []);
      setPlans(Array.isArray(planList) ? planList : []);
    } catch (err) {
      console.error(err);
      toast.error('Error al cargar datos de membresías');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  const filteredMemberships = useMemo(() => {
    if (!search.trim()) return memberships;
    const q = search.toLowerCase();
    return memberships.filter(
      (m) =>
        (m.organizacionNombre && m.organizacionNombre.toLowerCase().includes(q)) ||
        (m.planNombre && m.planNombre.toLowerCase().includes(q)) ||
        (m.estado && m.estado.toLowerCase().includes(q))
    );
  }, [memberships, search]);

  async function handleCreate(e) {
    e.preventDefault();
    if (!form.idOrganizacion || !form.idPlan) {
      toast.error('Por favor selecciona la organización y el plan');
      return;
    }
    try {
      setSubmitting(true);
      await api.post('/platform/memberships', {
        idOrganizacion: Number(form.idOrganizacion),
        idPlan: Number(form.idPlan),
        estado: form.estado,
        autoRenovar: form.autoRenovar,
      });
      toast.success('Membresía asignada exitosamente');
      setShowModal(false);
      setForm({ idOrganizacion: '', idPlan: '', estado: 'ACTIVA', autoRenovar: 'S' });
      loadData();
    } catch (err) {
      console.error(err);
      toast.error('Error al asignar la membresía');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggleStatus(id, currentStatus) {
    const nextStatus = currentStatus === 'ACTIVA' ? 'SUSPENDIDA' : 'ACTIVA';
    try {
      await api.put(`/platform/memberships/${id}/estado`, { estado: nextStatus });
      toast.success(`Membresía ${nextStatus === 'ACTIVA' ? 'activada' : 'suspendida'} exitosamente`);
      loadData();
    } catch (err) {
      console.error(err);
      toast.error('Error al cambiar el estado de la membresía');
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
            <span className="material-symbols-outlined text-primary text-2xl">card_membership</span>
            Membresías de Organizaciones
          </h1>
          <p className="text-muted-foreground mt-1 text-sm">
            Control de suscripciones, vigencias y cobros de las organizaciones cliente en SAED.
          </p>
        </div>
        <Button onClick={() => setShowModal(true)} className="gap-2 shrink-0">
          <span className="material-symbols-outlined text-sm">add_link</span>
          Asignar Suscripción
        </Button>
      </div>

      {/* Barra de Filtro */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">
            search
          </span>
          <Input
            id="mem-search-input"
            type="search"
            placeholder="Buscar por organización o plan…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9 text-sm"
          />
        </div>
        <div className="text-xs text-muted-foreground ml-auto font-medium">
          {filteredMemberships.length} {filteredMemberships.length === 1 ? 'membresía' : 'membresías'}
        </div>
      </div>

      {/* Tabla */}
      <Card className="border-border/80 shadow-sm">
        <CardHeader className="pb-3">
          <CardTitle className="text-base font-semibold">Suscripciones Registradas en Oracle</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-14 w-full rounded-lg" />
              ))}
            </div>
          ) : filteredMemberships.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground space-y-2">
              <span className="material-symbols-outlined text-4xl text-muted-foreground/60">credit_card_off</span>
              <p className="text-sm font-medium">
                {search ? 'No se encontraron membresías con ese filtro.' : 'No hay membresías registradas actualmente.'}
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm border-collapse">
                <thead>
                  <tr className="border-b border-border text-muted-foreground text-xs font-semibold uppercase tracking-wider">
                    <th className="py-3 px-4">ID</th>
                    <th className="py-3 px-4">Organización</th>
                    <th className="py-3 px-4">Plan SaaS</th>
                    <th className="py-3 px-4">Tarifa</th>
                    <th className="py-3 px-4">Vigencia</th>
                    <th className="py-3 px-4 text-center">Estado</th>
                    <th className="py-3 px-4 text-right">Acción</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {filteredMemberships.map((m) => (
                    <tr key={m.id} className="hover:bg-muted/40 transition-colors">
                      <td className="py-3.5 px-4 font-mono text-xs text-muted-foreground">#{m.id}</td>
                      <td className="py-3.5 px-4 font-semibold text-foreground">{m.organizacionNombre}</td>
                      <td className="py-3.5 px-4">
                        <Badge variant="outline" className="font-mono text-xs">
                          {m.planNombre || m.planCodigo}
                        </Badge>
                      </td>
                      <td className="py-3.5 px-4 font-mono text-xs">{formatCurrency(m.precioMensual)}</td>
                      <td className="py-3.5 px-4 text-xs text-muted-foreground">
                        {m.fechaInicio || '—'} al {m.fechaFin || 'Indefinido'}
                      </td>
                      <td className="py-3.5 px-4 text-center">
                        <Badge
                          variant={m.estado === 'ACTIVA' ? 'default' : 'secondary'}
                          className={
                            m.estado === 'ACTIVA'
                              ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 text-xs'
                              : 'text-xs'
                          }
                        >
                          {m.estado || 'ACTIVA'}
                        </Badge>
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-xs h-8 px-2"
                          onClick={() => handleToggleStatus(m.id, m.estado)}
                        >
                          {m.estado === 'ACTIVA' ? 'Suspender' : 'Activar'}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Modal Asignar Suscripción */}
      <Dialog open={showModal} onOpenChange={setShowModal}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-lg font-bold">
              <span className="material-symbols-outlined text-primary">add_link</span>
              Asignar Plan a Organización
            </DialogTitle>
          </DialogHeader>

          <form onSubmit={handleCreate} className="space-y-4 pt-2">
            <div className="space-y-1.5">
              <Label htmlFor="mem-org" className="text-xs font-semibold uppercase text-muted-foreground">
                Organización *
              </Label>
              <select
                id="mem-org"
                required
                value={form.idOrganizacion}
                onChange={(e) => setForm({ ...form, idOrganizacion: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">-- Selecciona una Organización --</option>
                {orgs.map((o) => (
                  <option key={o.id || o.idOrganizacion} value={o.id || o.idOrganizacion}>
                    {o.nombre} ({o.identificacionFiscal || 'Sin NIT'})
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="mem-plan" className="text-xs font-semibold uppercase text-muted-foreground">
                Plan SaaS *
              </Label>
              <select
                id="mem-plan"
                required
                value={form.idPlan}
                onChange={(e) => setForm({ ...form, idPlan: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="">-- Selecciona un Plan --</option>
                {plans.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.nombre} — {formatCurrency(p.precioMensual)}/mes
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="mem-estado" className="text-xs font-semibold uppercase text-muted-foreground">
                Estado Inicial
              </Label>
              <select
                id="mem-estado"
                value={form.estado}
                onChange={(e) => setForm({ ...form, estado: e.target.value })}
                className="w-full px-3 py-2 rounded-lg border border-input bg-background text-foreground text-sm focus:outline-none focus:ring-2 focus:ring-primary"
              >
                <option value="ACTIVA">ACTIVA</option>
                <option value="PRUEBA">PRUEBA (Trial)</option>
                <option value="SUSPENDIDA">SUSPENDIDA</option>
              </select>
            </div>

            <DialogFooter className="pt-3 gap-2">
              <Button type="button" variant="outline" onClick={() => setShowModal(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={submitting} className="gap-2">
                {submitting ? 'Guardando…' : 'Crear Membresía'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
