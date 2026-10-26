import { useEffect, useState, useMemo } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Button } from '../components/ui/button.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Input as SearchInput } from '../components/ui/input.tsx';
import { Input, Select } from '../components/ui/Form.jsx';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog.tsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import { formatDate } from '../lib/utils.js';
import { ArrowDown, ArrowLeftRight, ArrowUp, BadgeCheck, Link2, Minus, Search, User } from 'lucide-react';
import { toast } from 'sonner';

export default function SuperAdminMembresiasPage() {
  const [memberships, setMemberships] = useState([]);
  const [orgs, setOrgs] = useState([]);
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [suspendTarget, setSuspendTarget] = useState(null);
  const [search, setSearch] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Modal para cambio manual de plan (Upgrade / Downgrade / Asignación por SuperAdmin)
  const [showPlanModal, setShowPlanModal] = useState(false);
  const [selectedMembership, setSelectedMembership] = useState(null);
  const [targetPlanId, setTargetPlanId] = useState('');
  const [targetEstado, setTargetEstado] = useState('ACTIVA');
  const [changeMotivo, setChangeMotivo] = useState('');
  const [updatingPlan, setUpdatingPlan] = useState(false);

  const [form, setForm] = useState({
    idOrganizacion: '',
    idPlan: '',
    estado: 'ACTIVA',
    autoRenovar: 'S',
  });

  async function loadData() {
    try {
      setLoading(true);
      setLoadError(null);
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
      setLoadError(err?.message || 'No fue posible consultar las membresías.');
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
        (m.adminNombre && m.adminNombre.toLowerCase().includes(q)) ||
        (m.adminEmail && m.adminEmail.toLowerCase().includes(q)) ||
        (m.adminUsuario && m.adminUsuario.toLowerCase().includes(q)) ||
        (m.estado && m.estado.toLowerCase().includes(q))
    );
  }, [memberships, search]);

  function openPlanModal(mem) {
    setSelectedMembership(mem);
    setTargetPlanId(String(mem.idPlan || ''));
    setTargetEstado(mem.estado || 'ACTIVA');
    setChangeMotivo('');
    setShowPlanModal(true);
  }

  const selectedTargetPlan = useMemo(() => {
    if (!targetPlanId) return null;
    return plans.find((p) => String(p.id || p.idPlan || p.ID_PLAN) === String(targetPlanId));
  }, [plans, targetPlanId]);

  const changeType = useMemo(() => {
    if (!selectedMembership || !selectedTargetPlan) return null;
    const currentPrice = Number(selectedMembership.precioMensual || 0);
    const newPrice = Number(selectedTargetPlan.precioMensual || selectedTargetPlan.PRECIO_MENSUAL || 0);
    if (newPrice > currentPrice) return 'UPGRADE';
    if (newPrice < currentPrice) return 'DOWNGRADE';
    return 'RENOVACION';
  }, [selectedMembership, selectedTargetPlan]);

  async function handleCambiarPlan(e) {
    e.preventDefault();
    if (!targetPlanId) {
      toast.error('Por favor selecciona un plan');
      return;
    }
    try {
      setUpdatingPlan(true);
      const res = await api.put(`/platform/memberships/${selectedMembership.id}/plan`, {
        idPlan: Number(targetPlanId),
        estado: targetEstado,
        motivo: changeMotivo.trim() || 'Cambio manual por SUPERADMIN',
      });
      toast.success(res?.data?.mensaje || 'Plan actualizado exitosamente');
      setShowPlanModal(false);
      setSelectedMembership(null);
      loadData();
    } catch (err) {
      console.error(err);
      toast.error(err.response?.data?.message || err.message || 'Error al cambiar el plan');
    } finally {
      setUpdatingPlan(false);
    }
  }

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

  const changeBadge = {
    UPGRADE: { variant: 'success', icon: ArrowUp, label: 'Upgrade' },
    DOWNGRADE: { variant: 'warning', icon: ArrowDown, label: 'Downgrade' },
    RENOVACION: { variant: 'secondary', icon: Minus, label: 'Mismo plan' },
  }[changeType];

  return (
    <PageContainer>
      <PageHeader
        title="Membresías de organizaciones"
        subtitle="Suscripciones, vigencias y tarifas de las organizaciones cliente en SAED."
        action={
          <Button onClick={() => setShowModal(true)} className="min-h-10">
            <Link2 aria-hidden="true" />
            Asignar suscripción
          </Button>
        }
      />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative w-full sm:max-w-sm">
          <Search
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <SearchInput
            id="mem-search-input"
            type="search"
            aria-label="Buscar membresías"
            placeholder="Buscar por organización o plan…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-10 pl-9 text-sm"
          />
        </div>
        <div className="text-xs font-semibold text-text-secondary sm:ml-auto">
          <span className="app-numeral">{filteredMemberships.length}</span>{' '}
          {filteredMemberships.length === 1 ? 'membresía' : 'membresías'}
        </div>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-[15px]">Suscripciones registradas</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2" role="status" aria-busy="true" aria-label="Cargando membresías">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : loadError ? (
            <ErrorState title="No se pudo cargar la lista" message={loadError} onRetry={loadData} />
          ) : filteredMemberships.length === 0 ? (
            <EmptyState
              icon={BadgeCheck}
              title={search ? 'Sin coincidencias' : 'No hay membresías registradas'}
              subtitle={
                search
                  ? 'No se encontraron membresías con ese filtro.'
                  : 'Asigna un plan a una organización para crear su primera suscripción.'
              }
            >
              {search ? (
                <Button variant="outline" size="sm" className="mt-3" onClick={() => setSearch('')}>
                  Limpiar búsqueda
                </Button>
              ) : (
                <Button size="sm" className="mt-3" onClick={() => setShowModal(true)}>
                  <Link2 aria-hidden="true" />
                  Asignar suscripción
                </Button>
              )}
            </EmptyState>
          ) : (
            <div className="table-container max-h-[70vh] overflow-y-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Organización</th>
                    <th scope="col">Plan</th>
                    <th scope="col" className="text-right">Tarifa mensual</th>
                    <th scope="col">Vigencia</th>
                    <th scope="col" className="text-center">Estado</th>
                    <th scope="col" className="text-right">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredMemberships.map((m) => (
                    <tr key={m.id}>
                      <td className="font-mono text-xs text-text-secondary tabular-nums">#{m.id}</td>
                      <td className="min-w-[12rem]">
                        <div className="font-semibold text-on-surface">{m.organizacionNombre}</div>
                        {(m.adminNombre || m.adminEmail) && (
                          <div className="mt-0.5 flex flex-wrap items-center gap-1 text-xs text-text-muted">
                            <User className="h-3 w-3" aria-hidden="true" />
                            <span>{m.adminNombre || m.adminEmail}</span>
                            {m.adminNombre && m.adminEmail && <span>({m.adminEmail})</span>}
                          </div>
                        )}
                      </td>
                      <td>
                        <Badge variant="outline" className="font-mono">
                          {m.planNombre || m.planCodigo}
                        </Badge>
                      </td>
                      <td className="whitespace-nowrap text-right text-sm font-semibold tabular-nums">
                        {formatCurrency(m.precioMensual)}
                      </td>
                      <td className="whitespace-nowrap text-xs tabular-nums text-text-secondary">
                        {m.fechaInicio ? formatDate(m.fechaInicio) : '—'} al {m.fechaFin ? formatDate(m.fechaFin) : 'Indefinido'}
                      </td>
                      <td className="text-center">
                        <Badge
                          variant={
                            (m.estado || 'ACTIVA') === 'ACTIVA'
                              ? 'success'
                              : m.estado === 'SUSPENDIDA'
                              ? 'destructive'
                              : m.estado === 'PRUEBA'
                              ? 'info'
                              : 'secondary'
                          }
                        >
                          {m.estado || 'ACTIVA'}
                        </Badge>
                      </td>
                      <td className="text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-10 gap-1 px-2.5 text-xs sm:h-8"
                            onClick={() => openPlanModal(m)}
                            title="Cambiar o reasignar plan (upgrade / downgrade)"
                          >
                            <ArrowLeftRight aria-hidden="true" />
                            Cambiar plan
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-10 px-2.5 text-xs sm:h-8"
                            onClick={() =>
                              m.estado === 'ACTIVA' ? setSuspendTarget(m) : handleToggleStatus(m.id, m.estado)
                            }
                          >
                            {m.estado === 'ACTIVA' ? 'Suspender' : 'Activar'}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={!!suspendTarget}
        onClose={() => setSuspendTarget(null)}
        onConfirm={() => handleToggleStatus(suspendTarget.id, suspendTarget.estado)}
        title="Suspender membresía"
        message={`La organización ${suspendTarget?.organizacionNombre || ''} quedará con la suscripción suspendida hasta que se reactive.`}
        confirmLabel="Suspender"
        danger
      />

      <Dialog open={showModal} onOpenChange={setShowModal}>
        <DialogContent className="max-h-[90dvh] max-w-md overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Asignar plan a organización</DialogTitle>
            <DialogDescription>La tarifa proviene del catálogo de planes vigente.</DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreate} className="space-y-4 pt-1">
            <Select
              id="mem-org"
              label="Organización"
              required
              value={form.idOrganizacion}
              onChange={(e) => setForm({ ...form, idOrganizacion: e.target.value })}
            >
              <option value="">Selecciona una organización</option>
              {orgs.map((o) => (
                <option key={o.id || o.idOrganizacion} value={o.id || o.idOrganizacion}>
                  {o.nombre} ({o.identificacionFiscal || 'Sin NIT'})
                </option>
              ))}
            </Select>

            <Select
              id="mem-plan"
              label="Plan"
              required
              value={form.idPlan}
              onChange={(e) => setForm({ ...form, idPlan: e.target.value })}
            >
              <option value="">Selecciona un plan</option>
              {plans.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nombre} — {formatCurrency(p.precioMensual)}/mes
                </option>
              ))}
            </Select>

            <Select
              id="mem-estado"
              label="Estado inicial"
              value={form.estado}
              onChange={(e) => setForm({ ...form, estado: e.target.value })}
            >
              <option value="ACTIVA">ACTIVA</option>
              <option value="PRUEBA">PRUEBA (periodo de prueba)</option>
              <option value="SUSPENDIDA">SUSPENDIDA</option>
            </Select>

            <DialogFooter className="gap-2 pt-3">
              <Button type="button" variant="outline" onClick={() => setShowModal(false)}>
                Cancelar
              </Button>
              <Button type="submit" disabled={submitting}>
                {submitting ? 'Guardando…' : 'Crear membresía'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={showPlanModal} onOpenChange={setShowPlanModal}>
        <DialogContent className="max-h-[90dvh] max-w-lg overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Cambiar plan de suscripción</DialogTitle>
            <DialogDescription>El cambio queda registrado en la auditoría con el motivo indicado.</DialogDescription>
          </DialogHeader>

          {selectedMembership && (
            <form onSubmit={handleCambiarPlan} className="space-y-4 pt-1">
              <dl className="space-y-1.5 rounded-lg border border-border bg-surface-container p-3 text-xs">
                <div className="flex items-center justify-between gap-3">
                  <dt className="font-medium text-text-secondary">Organización</dt>
                  <dd className="text-right font-bold text-on-surface">{selectedMembership.organizacionNombre}</dd>
                </div>
                {(selectedMembership.adminNombre || selectedMembership.adminEmail) && (
                  <div className="flex items-center justify-between gap-3">
                    <dt className="font-medium text-text-secondary">Titular</dt>
                    <dd className="text-right font-semibold text-on-surface">
                      {selectedMembership.adminNombre || selectedMembership.adminEmail}
                    </dd>
                  </div>
                )}
                <div className="flex items-center justify-between gap-3">
                  <dt className="font-medium text-text-secondary">Plan actual</dt>
                  <dd className="flex flex-wrap items-center justify-end gap-1.5">
                    <Badge variant="outline" className="font-mono">
                      {selectedMembership.planNombre || selectedMembership.planCodigo}
                    </Badge>
                    <span className="tabular-nums text-text-secondary">
                      ({formatCurrency(selectedMembership.precioMensual)}/mes)
                    </span>
                  </dd>
                </div>
              </dl>

              <div className="space-y-1.5">
                <Select
                  id="target-plan"
                  label="Nuevo plan"
                  required
                  value={targetPlanId}
                  onChange={(e) => setTargetPlanId(e.target.value)}
                >
                  <option value="">Selecciona el plan destino</option>
                  {plans.map((p) => {
                    const pid = p.id || p.idPlan || p.ID_PLAN;
                    const pnom = p.nombre || p.NOMBRE;
                    const pprecio = p.precioMensual || p.PRECIO_MENSUAL || 0;
                    return (
                      <option key={pid} value={pid}>
                        {pnom} — {formatCurrency(pprecio)}/mes
                      </option>
                    );
                  })}
                </Select>
                {changeBadge && (
                  <div aria-live="polite">
                    <Badge variant={changeBadge.variant}>
                      <changeBadge.icon className="h-3 w-3" aria-hidden="true" />
                      {changeBadge.label}
                    </Badge>
                  </div>
                )}
              </div>

              {selectedTargetPlan && (
                <dl className="grid grid-cols-3 divide-x divide-border rounded-md border border-border p-2.5 text-center text-xs">
                  <div className="px-1">
                    <dt className="text-text-muted">Propiedades</dt>
                    <dd className="font-bold tabular-nums text-on-surface">
                      {selectedTargetPlan.limitePropiedades || selectedTargetPlan.LIMITE_PROPIEDADES || 'Ilimitadas'}
                    </dd>
                  </div>
                  <div className="px-1">
                    <dt className="text-text-muted">Unidades</dt>
                    <dd className="font-bold tabular-nums text-on-surface">
                      {selectedTargetPlan.limiteUnidades || selectedTargetPlan.LIMITE_UNIDADES || 'Ilimitadas'}
                    </dd>
                  </div>
                  <div className="px-1">
                    <dt className="text-text-muted">Usuarios</dt>
                    <dd className="font-bold tabular-nums text-on-surface">
                      {selectedTargetPlan.limiteUsuarios || selectedTargetPlan.LIMITE_USUARIOS || 'Ilimitados'}
                    </dd>
                  </div>
                </dl>
              )}

              <Select
                id="target-estado"
                label="Estado de la membresía"
                value={targetEstado}
                onChange={(e) => setTargetEstado(e.target.value)}
              >
                <option value="ACTIVA">ACTIVA</option>
                <option value="PRUEBA">PRUEBA (periodo de prueba)</option>
                <option value="SUSPENDIDA">SUSPENDIDA</option>
              </Select>

              <Input
                id="target-motivo"
                label="Motivo o justificación"
                placeholder="Ej.: solicitud comercial, ajuste de cuota, cortesía…"
                value={changeMotivo}
                onChange={(e) => setChangeMotivo(e.target.value)}
              />

              <DialogFooter className="gap-2 pt-3">
                <Button type="button" variant="outline" onClick={() => setShowPlanModal(false)} disabled={updatingPlan}>
                  Cancelar
                </Button>
                <Button type="submit" disabled={updatingPlan || !targetPlanId}>
                  {updatingPlan ? 'Aplicando…' : 'Confirmar cambio de plan'}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
