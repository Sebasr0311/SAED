import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../lib/api.js';
import { cn } from '../lib/utils.js';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog.tsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';
import ErrorState from '../components/ui/ErrorState.jsx';
import {
  Home, Plus, Search, MapPin, AlertCircle, CheckCircle2, Power,
  Trash2, ShieldAlert, KeyRound, Clock, AlertTriangle, RefreshCw, Check
} from 'lucide-react';
import LocationSelector from '../components/ui/LocationSelector';

const CONTROL =
  'h-10 w-full rounded-md border border-input bg-surface px-3 text-sm text-on-surface transition-[border-color,box-shadow] duration-150 focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25';

export default function OrgPropiedadesPage() {
  const [properties, setProperties] = useState([]);
  const [subscription, setSubscription] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [statusConfirmProp, setStatusConfirmProp] = useState(null);

  // Secure Deletion states (P1-01)
  const [deleteModalProp, setDeleteModalProp] = useState(null);
  const [deletePhase, setDeletePhase] = useState('INIT'); // 'INIT' | 'OTP' | 'CONFIRM' | 'SUCCESS'
  const [deleteChallenge, setDeleteChallenge] = useState(null);
  const [otpCode, setOtpCode] = useState('');
  const [otpAttemptsLeft, setOtpAttemptsLeft] = useState(5);
  const [deleteCountdown, setDeleteCountdown] = useState(300);
  const [confirmPhraseInput, setConfirmPhraseInput] = useState('');
  const [expectedConfirmPhrase, setExpectedConfirmPhrase] = useState('');
  const [confirmChecked, setConfirmChecked] = useState(false);
  const [deleteActionLoading, setDeleteActionLoading] = useState(false);
  const [deleteModalError, setDeleteModalError] = useState(null);

  // Modal create state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState(null);
  const [tiposPropiedad, setTiposPropiedad] = useState([
    { idTipoPropiedad: 1, nombre: 'Edificio Residencial' },
    { idTipoPropiedad: 2, nombre: 'Conjunto Cerrado' },
  ]);
  const [newProp, setNewProp] = useState({
    nombre: '',
    idTipoPropiedad: 1,
    direccion: '',
    departamento: 'Bogotá D.C.',
    ciudad: 'Bogotá',
    pais: 'Colombia',
    tipoOcupacionPredominante: 'MIXTA',
  });

  async function loadTipos() {
    try {
      const res = await api.get('/catalogos/tipos-propiedad');
      const raw = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);
      if (raw.length > 0) setTiposPropiedad(raw);
    } catch {
      // Keep the built-in property types when the catalog is unavailable.
    }
  }

  async function loadData(showSpinner = true) {
    try {
      if (showSpinner) {
        setLoading(true);
      }
      setError(null);
      const [propsRes, subRes] = await Promise.all([
        api.get('/properties'),
        api.get('/org/subscription').catch(() => null),
      ]);
      setProperties(Array.isArray(propsRes?.data) ? propsRes.data : Array.isArray(propsRes) ? propsRes : []);
      if (subRes) {
        setSubscription(subRes?.data || subRes || null);
      }
    } catch (err) {
      console.error('Error loading properties:', err);
      setError('No se pudieron cargar las propiedades de la organización.');
    } finally {
      if (showSpinner) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    loadTipos();
    loadData(true);
  }, []);

  async function handleCreate(e) {
    e.preventDefault();
    try {
      setCreating(true);
      setCreateError(null);
      await api.post('/properties', {
        nombre: newProp.nombre.trim(),
        idTipoPropiedad: Number(newProp.idTipoPropiedad),
        direccion: newProp.direccion.trim(),
        ciudad: newProp.ciudad || 'Bogotá',
        departamento: newProp.departamento || null,
        pais: newProp.pais || 'Colombia',
        tipoOcupacionPredominante: newProp.tipoOcupacionPredominante || 'MIXTA',
      });
      setSuccessMsg('Propiedad registrada exitosamente.');
      setIsModalOpen(false);
      setCreating(false);
      setNewProp({
        nombre: '',
        idTipoPropiedad: 1,
        direccion: '',
        departamento: 'Bogotá D.C.',
        ciudad: 'Bogotá',
        pais: 'Colombia',
        tipoOcupacionPredominante: 'MIXTA',
      });
      loadData(false);
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      setCreating(false);
      console.error('Error creating property:', err);
      if (err?.response?.status === 409 || err?.response?.data?.code === 'PLAN_LIMIT_EXCEEDED') {
        setCreateError(
          'Límite de propiedades alcanzado. Su plan actual no permite agregar más copropiedades. Actualice su suscripción para continuar.'
        );
      } else {
        setCreateError(err?.response?.data?.message || 'Error al registrar la propiedad.');
      }
    }
  }

  function handleToggleStatus(prop) {
    if (prop.estado === 'ACTIVA') {
      setStatusConfirmProp(prop);
    } else {
      executeToggleStatus(prop);
    }
  }

  async function executeToggleStatus(prop) {
    const nextStatus = prop.estado === 'ACTIVA' ? 'INACTIVA' : 'ACTIVA';
    try {
      await api.patch(`/properties/${prop.id}/status`, { estado: nextStatus });
      setSuccessMsg(`Propiedad ${prop.nombre} ahora se encuentra ${nextStatus}.`);
      setStatusConfirmProp(null);
      await loadData();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      console.error('Error updating property status:', err);
      setError('No se pudo cambiar el estado de la propiedad.');
    }
  }

  // Secure Deletion Timer Effect
  useEffect(() => {
    let interval = null;
    if (deleteModalProp && (deletePhase === 'OTP' || deletePhase === 'CONFIRM') && deleteCountdown > 0) {
      interval = setInterval(() => {
        setDeleteCountdown((prev) => {
          if (prev <= 1) {
            clearInterval(interval);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [deleteModalProp, deletePhase, deleteCountdown]);

  function openDeleteModal(prop) {
    setDeleteModalProp(prop);
    setDeletePhase('INIT');
    setDeleteChallenge(null);
    setOtpCode('');
    setOtpAttemptsLeft(5);
    setDeleteCountdown(300);
    setConfirmPhraseInput('');
    setExpectedConfirmPhrase('');
    setConfirmChecked(false);
    setDeleteActionLoading(false);
    setDeleteModalError(null);
  }

  function closeDeleteModal() {
    setDeleteModalProp(null);
    setDeletePhase('INIT');
    setDeleteChallenge(null);
    setOtpCode('');
    setConfirmPhraseInput('');
    setExpectedConfirmPhrase('');
    setConfirmChecked(false);
    setDeleteActionLoading(false);
    setDeleteModalError(null);
  }

  function formatTime(seconds) {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }

  async function handleRequestDeletionOtp() {
    if (!deleteModalProp) return;
    try {
      setDeleteActionLoading(true);
      setDeleteModalError(null);
      const res = await api.post(`/properties/${deleteModalProp.id}/deletion/request`);
      const data = res?.data || res;
      setDeleteChallenge(data);
      setOtpAttemptsLeft(data?.maxAttempts ?? 5);
      setDeleteCountdown(data?.expiresInSeconds ?? 300);
      setDeletePhase('OTP');
    } catch (err) {
      console.error('Error requesting deletion OTP:', err);
      const msg = err?.response?.data?.message || err?.message || 'Error al solicitar el código de seguridad OTP.';
      setDeleteModalError(msg);
    } finally {
      setDeleteActionLoading(false);
    }
  }

  async function handleVerifyDeletionOtp(e) {
    if (e) e.preventDefault();
    if (!deleteModalProp || !deleteChallenge || !otpCode.trim()) return;
    try {
      setDeleteActionLoading(true);
      setDeleteModalError(null);
      const res = await api.post(`/properties/${deleteModalProp.id}/deletion/verify`, {
        challengeId: deleteChallenge.challengeId,
        code: otpCode.trim(),
      });
      const data = res?.data || res;
      if (data?.verified) {
        setExpectedConfirmPhrase(data?.confirmationPhrase || `ELIMINAR DEFINITIVAMENTE ${deleteModalProp.nombre.toUpperCase()}`);
        setDeletePhase('CONFIRM');
      } else {
        setOtpAttemptsLeft(data?.remainingAttempts ?? (otpAttemptsLeft - 1));
        setDeleteModalError(data?.message || 'Código incorrecto. Verifíquelo e intente de nuevo.');
      }
    } catch (err) {
      console.error('Error verifying deletion OTP:', err);
      const data = err?.response?.data;
      if (data?.remainingAttempts !== undefined) {
        setOtpAttemptsLeft(data.remainingAttempts);
      }
      setDeleteModalError(data?.message || err?.message || 'Error al verificar el código OTP.');
    } finally {
      setDeleteActionLoading(false);
    }
  }

  async function handleConfirmFinalDeletion(e) {
    if (e) e.preventDefault();
    if (!deleteModalProp || !deleteChallenge) return;
    if (confirmPhraseInput.trim().toUpperCase() !== expectedConfirmPhrase.trim().toUpperCase()) {
      setDeleteModalError('La frase de confirmación no coincide exactamente.');
      return;
    }
    if (!confirmChecked) {
      setDeleteModalError('Debe marcar la casilla de confirmación para continuar.');
      return;
    }
    try {
      setDeleteActionLoading(true);
      setDeleteModalError(null);
      await api.post(`/properties/${deleteModalProp.id}/deletion/confirm`, {
        challengeId: deleteChallenge.challengeId,
        confirmationPhrase: confirmPhraseInput.trim(),
        understandIrreversible: true,
      });
      setDeletePhase('SUCCESS');
      setSuccessMsg(`La copropiedad "${deleteModalProp.nombre}" ha sido eliminada permanentemente del sistema.`);
      await loadData();
      setTimeout(() => {
        closeDeleteModal();
        setTimeout(() => setSuccessMsg(null), 5000);
      }, 2500);
    } catch (err) {
      console.error('Error confirming deletion:', err);
      const msg = err?.response?.data?.message || err?.message || 'Error al ejecutar la eliminación destructiva.';
      setDeleteModalError(msg);
    } finally {
      setDeleteActionLoading(false);
    }
  }

  const filtered = properties.filter((p) => {
    const matchesSearch =
      (p.nombre || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (p.ciudad || '').toLowerCase().includes(searchTerm.toLowerCase()) ||
      (p.direccion || '').toLowerCase().includes(searchTerm.toLowerCase());
    const matchesStatus = statusFilter === 'ALL' || p.estado === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const used = subscription?.propiedadesUsadas ?? properties.filter((p) => p.estado === 'ACTIVA').length;
  const limit = subscription?.limitePropiedades ?? 0;
  const isAtLimit = limit > 0 && used >= limit;

  const headerBase = {
    title: 'Propiedades de la organización',
    subtitle: 'Gestione y supervise las copropiedades, conjuntos y edificios bajo su administración.',
  };

  if (loading) {
    return (
      <PageContainer>
        <PageHeader {...headerBase} />
        <div role="status" aria-busy="true" aria-label="Cargando propiedades" className="space-y-3">
          <Skeleton className="h-10 w-full max-w-md" />
          {[1, 2, 3, 4, 5].map((i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      </PageContainer>
    );
  }

  if (error && properties.length === 0) {
    return (
      <PageContainer>
        <PageHeader {...headerBase} />
        <ErrorState title="No se pudieron cargar las propiedades" message={error} onRetry={() => loadData(true)} />
      </PageContainer>
    );
  }

  const deleteTitle = {
    INIT: 'Eliminación segura de copropiedad',
    OTP: 'Verificación de seguridad',
    CONFIRM: 'Confirmación definitiva',
    SUCCESS: 'Copropiedad eliminada',
  }[deletePhase];
  const deleteStep = { INIT: 1, OTP: 2, CONFIRM: 3 }[deletePhase];
  const hayFiltros = Boolean(searchTerm || statusFilter !== 'ALL');

  return (
    <PageContainer>
      <PageHeader
        {...headerBase}
        badge={
          <Badge variant={isAtLimit ? 'warning' : 'outline'} className="tabular-nums">
            {used} / {limit > 0 ? limit : '∞'} utilizadas
          </Badge>
        }
        action={
          <Button onClick={() => setIsModalOpen(true)} className="min-h-10">
            <Plus aria-hidden="true" />
            Nueva propiedad
          </Button>
        }
      />

      <div aria-live="polite" className="space-y-3 empty:hidden">
        {successMsg && (
          <div className="flex items-center gap-3 rounded-md border border-tint-green-fg/25 bg-tint-green-bg px-4 py-3 text-tint-green-fg">
            <CheckCircle2 className="h-5 w-5 shrink-0" aria-hidden="true" />
            <span className="text-sm font-semibold">{successMsg}</span>
          </div>
        )}
        {error && <ErrorState compact title="No se pudo completar la operación" message={error} />}
      </div>

      {isAtLimit && (
        <div role="note" className="flex items-start gap-3 rounded-md border border-tint-amber-fg/25 bg-tint-amber-bg px-4 py-3 text-tint-amber-fg">
          <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
          <div className="text-sm">
            <span className="font-bold">Ha alcanzado el límite de propiedades de su plan ({limit}).</span> Para registrar
            nuevas copropiedades, amplíe el plan desde{' '}
            <Link to="/org/plan" className="font-semibold underline underline-offset-2">
              Plan y suscripción
            </Link>
            .
          </div>
        </div>
      )}

      <div className="flex flex-col items-stretch justify-between gap-3 sm:flex-row sm:items-center">
        <div className="relative w-full sm:w-80">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" aria-hidden="true" />
          <input
            type="search"
            aria-label="Buscar por nombre o ciudad"
            placeholder="Buscar por nombre o ciudad..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className={`${CONTROL} pl-9`}
          />
        </div>
        <select
          aria-label="Filtrar por estado"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className={`${CONTROL} sm:w-auto`}
        >
          <option value="ALL">Todos los estados</option>
          <option value="ACTIVA">Solo activas</option>
          <option value="INACTIVA">Solo inactivas</option>
        </select>
      </div>

      {filtered.length === 0 ? (
        <div className="table-container">
          <EmptyState
            icon={Home}
            title={hayFiltros ? 'No se encontraron propiedades' : 'Aún no hay propiedades'}
            subtitle={
              hayFiltros
                ? 'Ajuste la búsqueda o el filtro de estado.'
                : 'Registre la primera copropiedad para administrarla desde la organización.'
            }
          >
            {hayFiltros ? (
              <Button
                variant="outline"
                size="sm"
                className="mt-3"
                onClick={() => {
                  setSearchTerm('');
                  setStatusFilter('ALL');
                }}
              >
                Limpiar filtros
              </Button>
            ) : (
              <Button size="sm" className="mt-3" onClick={() => setIsModalOpen(true)}>
                <Plus aria-hidden="true" />
                Nueva propiedad
              </Button>
            )}
          </EmptyState>
        </div>
      ) : (
        <div className="table-container max-h-[70vh] overflow-y-auto">
          <table className="data-table">
            <thead>
              <tr>
                <th scope="col">Copropiedad</th>
                <th scope="col">Ubicación</th>
                <th scope="col">Ocupación</th>
                <th scope="col" className="text-center">Estado</th>
                <th scope="col" className="text-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((prop) => {
                const activa = (prop.estado || 'ACTIVA') === 'ACTIVA';
                return (
                  <tr key={prop.id}>
                    <td className="min-w-[12rem]">
                      <div className="font-semibold text-on-surface">{prop.nombre}</div>
                      <div className="flex flex-wrap items-center gap-1.5 text-xs text-text-muted">
                        <span>{prop.tipoPropiedadNombre || 'Edificio residencial'}</span>
                        <span aria-hidden="true">·</span>
                        <span className="font-mono tabular-nums">ID #{prop.id}</span>
                      </div>
                    </td>
                    <td className="min-w-[10rem] text-xs text-text-secondary">
                      <span className="inline-flex items-start gap-1.5">
                        <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                        {prop.direccion || 'Sin dirección'}, {prop.ciudad || 'Colombia'}
                      </span>
                    </td>
                    <td>
                      <Badge variant="outline">{prop.tipoOcupacionPredominante || 'MIXTA'}</Badge>
                    </td>
                    <td className="text-center">
                      <Badge variant={activa ? 'success' : 'secondary'}>{prop.estado || 'ACTIVA'}</Badge>
                    </td>
                    <td className="text-right">
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleToggleStatus(prop)}
                          className="h-10 gap-1 text-xs sm:h-8"
                        >
                          <Power aria-hidden="true" />
                          {prop.estado === 'ACTIVA' ? 'Desactivar' : 'Activar'}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => openDeleteModal(prop)}
                          className="h-10 gap-1 text-xs text-destructive hover:bg-tint-red-bg hover:text-destructive sm:h-8"
                          title="Eliminación segura con código de verificación"
                        >
                          <Trash2 aria-hidden="true" />
                          Eliminar
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Create dialog */}
      <Dialog open={isModalOpen} onOpenChange={(o) => !creating && setIsModalOpen(o)}>
        <DialogContent className="max-h-[90dvh] max-w-lg overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Registrar nueva propiedad</DialogTitle>
            <DialogDescription>Los campos marcados con * son obligatorios.</DialogDescription>
          </DialogHeader>
          {createError && <ErrorState compact title="No se pudo registrar la propiedad" message={createError} />}
          <form onSubmit={handleCreate} className="space-y-4">
            <Input
              id="org-prop-nombre"
              label="Nombre de la copropiedad o edificio"
              type="text"
              required
              placeholder="Ej. Torres del Parque"
              value={newProp.nombre}
              onChange={(e) => setNewProp({ ...newProp, nombre: e.target.value })}
            />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Select
                id="org-prop-tipo"
                label="Tipo de propiedad"
                value={newProp.idTipoPropiedad}
                onChange={(e) => setNewProp({ ...newProp, idTipoPropiedad: Number(e.target.value) })}
              >
                {tiposPropiedad.map((tp) => (
                  <option key={tp.idTipoPropiedad || tp.id} value={tp.idTipoPropiedad || tp.id}>
                    {tp.nombre || tp.codigo}
                  </option>
                ))}
              </Select>
              <Select
                id="org-prop-ocupacion"
                label="Ocupación"
                value={newProp.tipoOcupacionPredominante}
                onChange={(e) => setNewProp({ ...newProp, tipoOcupacionPredominante: e.target.value })}
              >
                <option value="PROPIETARIOS">Propietarios</option>
                <option value="ARRENDATARIOS">Arrendatarios</option>
                <option value="MIXTA">Mixta</option>
              </Select>
            </div>
            <Input
              id="org-prop-direccion"
              label="Dirección"
              type="text"
              placeholder="Ej. Calle 123 # 45-67"
              value={newProp.direccion}
              onChange={(e) => setNewProp({ ...newProp, direccion: e.target.value })}
            />
            <LocationSelector
              idPrefix="org-prop"
              pais={newProp.pais}
              departamento={newProp.departamento}
              ciudad={newProp.ciudad}
              onChange={({ pais, departamento, ciudad }) =>
                setNewProp((prev) => ({ ...prev, pais, departamento, ciudad }))
              }
            />

            <DialogFooter className="gap-2 border-t border-border pt-4">
              <Button type="button" variant="outline" onClick={() => setIsModalOpen(false)} disabled={creating}>
                Cancelar
              </Button>
              <Button type="submit" disabled={creating}>
                {creating ? 'Guardando...' : 'Crear propiedad'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Deactivation confirmation */}
      <Dialog open={!!statusConfirmProp} onOpenChange={(o) => !o && setStatusConfirmProp(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Suspender operaciones de la copropiedad</DialogTitle>
            <DialogDescription>
              La copropiedad <strong className="text-on-surface">{statusConfirmProp?.nombre}</strong> pasará a estado
              INACTIVA.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5 rounded-md border border-tint-amber-fg/25 bg-tint-amber-bg p-3 text-xs text-tint-amber-fg">
            <div className="flex items-center gap-1.5 font-bold">
              <AlertCircle className="h-3.5 w-3.5" aria-hidden="true" />
              Impacto operativo
            </div>
            <ul className="list-inside list-disc space-y-0.5">
              <li>Se congelarán las operaciones de residentes, cartera y visitas para esta propiedad.</li>
              <li>El historial contable y la auditoría se mantienen intactos.</li>
              <li>Podrá reactivar la copropiedad en cualquier momento desde esta consola.</li>
            </ul>
          </div>
          <DialogFooter className="gap-2">
            <Button type="button" variant="outline" onClick={() => setStatusConfirmProp(null)}>
              Cancelar
            </Button>
            <Button type="button" variant="destructive" onClick={() => executeToggleStatus(statusConfirmProp)}>
              Confirmar suspensión
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Secure deletion with emailed code (P1-01) */}
      <Dialog
        open={!!deleteModalProp}
        onOpenChange={(o) => {
          if (!o && !deleteActionLoading) closeDeleteModal();
        }}
      >
        <DialogContent className="max-h-[90dvh] max-w-lg overflow-y-auto border-tint-red-fg/40">
          {deleteModalProp && (
            <>
              <DialogHeader>
                <div className="flex flex-wrap items-start justify-between gap-2 pr-8">
                  <div className="flex items-center gap-2.5">
                    <div
                      className={cn(
                        'flex h-9 w-9 shrink-0 items-center justify-center rounded-md',
                        deletePhase === 'SUCCESS' ? 'bg-tint-green-bg text-tint-green-fg' : 'bg-tint-red-bg text-destructive'
                      )}
                      aria-hidden="true"
                    >
                      {deletePhase === 'INIT' && <ShieldAlert className="h-5 w-5" />}
                      {deletePhase === 'OTP' && <KeyRound className="h-5 w-5" />}
                      {deletePhase === 'CONFIRM' && <AlertTriangle className="h-5 w-5" />}
                      {deletePhase === 'SUCCESS' && <CheckCircle2 className="h-5 w-5" />}
                    </div>
                    <div className="text-left">
                      <DialogTitle>{deleteTitle}</DialogTitle>
                      <DialogDescription className="tabular-nums">
                        {deleteModalProp.nombre} · ID #{deleteModalProp.id}
                      </DialogDescription>
                    </div>
                  </div>
                  {deleteStep && (
                    <Badge variant="destructive" className="tabular-nums">
                      Paso {deleteStep} de 3
                    </Badge>
                  )}
                </div>
              </DialogHeader>

              <div className="space-y-4">
                {deleteModalError && <ErrorState compact title="No se pudo continuar" message={deleteModalError} />}

                {deletePhase === 'INIT' && (
                  <div className="space-y-4 text-xs">
                    <div className="space-y-2 rounded-md border border-tint-red-fg/25 bg-tint-red-bg p-3.5 text-tint-red-fg">
                      <div className="flex items-center gap-1.5 text-sm font-bold">
                        <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
                        Acción crítica e irreversible
                      </div>
                      <p className="text-[13px] leading-relaxed">
                        La eliminación de una copropiedad borra en cascada todos sus datos asociados. Esta acción no se
                        puede deshacer.
                      </p>
                    </div>

                    <div className="space-y-2 text-text-secondary">
                      <p className="font-semibold text-on-surface">Elementos que se eliminarán:</p>
                      <ul className="grid list-inside list-disc grid-cols-1 gap-1.5 sm:grid-cols-2">
                        <li>Unidades residenciales</li>
                        <li>Asignaciones y residentes</li>
                        <li>Historial de pagos y cuotas</li>
                        <li>PQRS y quejas</li>
                        <li>Registro de paquetes</li>
                        <li>Visitas y códigos QR</li>
                        <li>Asambleas y votos</li>
                        <li>Pólizas y mantenimientos</li>
                      </ul>
                    </div>

                    <div className="space-y-1 rounded-md border border-border bg-surface-container p-3 text-text-secondary">
                      <div className="flex items-center gap-1.5 font-semibold text-on-surface">
                        <KeyRound className="h-3.5 w-3.5" aria-hidden="true" />
                        Verificación requerida
                      </div>
                      <p>
                        Para continuar, el sistema enviará un código de verificación de 6 dígitos al correo del
                        administrador de la organización.
                      </p>
                    </div>

                    <DialogFooter className="gap-2 border-t border-border pt-3">
                      <Button type="button" variant="outline" onClick={closeDeleteModal} disabled={deleteActionLoading}>
                        Cancelar
                      </Button>
                      <Button
                        type="button"
                        variant="destructive"
                        onClick={handleRequestDeletionOtp}
                        disabled={deleteActionLoading}
                      >
                        {deleteActionLoading ? (
                          <RefreshCw className="animate-spin" aria-hidden="true" />
                        ) : (
                          <KeyRound aria-hidden="true" />
                        )}
                        {deleteActionLoading ? 'Generando código...' : 'Solicitar código de verificación'}
                      </Button>
                    </DialogFooter>
                  </div>
                )}

                {deletePhase === 'OTP' && (
                  <form onSubmit={handleVerifyDeletionOtp} className="space-y-4 text-xs">
                    <div className="space-y-1.5 text-center">
                      <p className="text-text-secondary">Enviamos un código de 6 dígitos a su correo institucional:</p>
                      <div className="inline-block rounded border border-border bg-surface-dim px-3 py-1 font-mono text-sm font-bold text-on-surface">
                        {deleteChallenge?.maskedEmail || 'su correo registrado'}
                      </div>
                    </div>

                    <div className="flex flex-wrap items-center justify-between gap-2 rounded bg-surface-container px-2.5 py-1.5 text-text-secondary">
                      <div className="flex items-center gap-1">
                        <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                        <span>Válido por:</span>
                        <span
                          className={cn(
                            'font-mono font-bold tabular-nums',
                            deleteCountdown < 60 ? 'text-destructive' : 'text-on-surface'
                          )}
                        >
                          {formatTime(deleteCountdown)}
                        </span>
                      </div>
                      <div className="flex items-center gap-1">
                        <span>Intentos restantes:</span>
                        <Badge variant={otpAttemptsLeft <= 2 ? 'destructive' : 'secondary'} className="tabular-nums">
                          {otpAttemptsLeft}
                        </Badge>
                      </div>
                    </div>

                    <div className="space-y-1 text-center">
                      <label htmlFor="org-prop-otp" className="block text-sm font-semibold text-on-surface">
                        Código de 6 dígitos
                      </label>
                      <input
                        id="org-prop-otp"
                        type="text"
                        inputMode="numeric"
                        autoComplete="one-time-code"
                        autoFocus
                        maxLength={6}
                        placeholder="••••••"
                        aria-describedby="org-prop-otp-hint"
                        value={otpCode}
                        onChange={(e) => setOtpCode(e.target.value.replace(/[^a-zA-Z0-9]/g, '').slice(0, 6))}
                        className="mx-auto block h-12 w-48 rounded-md border-2 border-input bg-surface px-3 text-center font-mono text-2xl font-bold uppercase tracking-[0.35em] text-on-surface focus-visible:border-ring focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/25"
                      />
                      <p id="org-prop-otp-hint" className="mt-1 text-xs text-text-muted">
                        Revise su bandeja de entrada o la carpeta de spam.
                      </p>
                    </div>

                    <div className="flex flex-col-reverse gap-2 border-t border-border pt-3 sm:flex-row sm:items-center sm:justify-between">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={handleRequestDeletionOtp}
                        disabled={deleteActionLoading || deleteCountdown > 240}
                        className="min-h-10 gap-1 text-xs"
                        title={deleteCountdown > 240 ? 'Espere 60 segundos para reenviar' : 'Reenviar código'}
                      >
                        <RefreshCw className={deleteActionLoading ? 'animate-spin' : ''} aria-hidden="true" />
                        Reenviar código
                      </Button>

                      <div className="flex gap-2">
                        <Button type="button" variant="outline" onClick={closeDeleteModal} disabled={deleteActionLoading}>
                          Cancelar
                        </Button>
                        <Button
                          type="submit"
                          disabled={
                            deleteActionLoading || otpCode.trim().length < 6 || deleteCountdown === 0 || otpAttemptsLeft === 0
                          }
                        >
                          {deleteActionLoading ? <RefreshCw className="animate-spin" aria-hidden="true" /> : <Check aria-hidden="true" />}
                          {deleteActionLoading ? 'Validando...' : 'Verificar código'}
                        </Button>
                      </div>
                    </div>
                  </form>
                )}

                {deletePhase === 'CONFIRM' && (
                  <form onSubmit={handleConfirmFinalDeletion} className="space-y-4 text-xs">
                    <div className="flex items-center gap-2 rounded-md border border-tint-green-fg/25 bg-tint-green-bg p-3 text-tint-green-fg">
                      <CheckCircle2 className="h-4 w-4 shrink-0" aria-hidden="true" />
                      <span className="font-semibold">Identidad confirmada con el código enviado por correo.</span>
                    </div>

                    <div className="space-y-1 rounded-md border border-tint-red-fg/25 bg-tint-red-bg p-3 text-tint-red-fg">
                      <p className="font-bold">Propiedad que se eliminará definitivamente:</p>
                      <p className="font-mono text-sm tabular-nums">
                        {deleteModalProp.nombre} (ID: #{deleteModalProp.id})
                      </p>
                    </div>

                    <div className="space-y-1.5">
                      <label htmlFor="org-prop-phrase" className="block text-[13px] font-semibold text-on-surface">
                        Para confirmar, escriba exactamente la siguiente frase:
                      </label>
                      <div className="select-all rounded border border-border bg-surface-dim p-2 text-center font-mono text-xs font-bold text-destructive">
                        {expectedConfirmPhrase}
                      </div>
                      <input
                        id="org-prop-phrase"
                        type="text"
                        autoFocus
                        autoComplete="off"
                        placeholder={expectedConfirmPhrase}
                        value={confirmPhraseInput}
                        onChange={(e) => setConfirmPhraseInput(e.target.value)}
                        className={`${CONTROL} font-mono`}
                      />
                    </div>

                    <label className="flex cursor-pointer items-start gap-2.5 rounded-md border border-tint-red-fg/30 bg-tint-red-bg/50 p-2.5">
                      <input
                        type="checkbox"
                        checked={confirmChecked}
                        onChange={(e) => setConfirmChecked(e.target.checked)}
                        className="mt-0.5 h-4 w-4 shrink-0 accent-[var(--btn-danger)]"
                      />
                      <span className="text-xs font-medium leading-snug text-on-surface">
                        Confirmo que deseo eliminar esta propiedad, sus unidades y todos los datos asociados de forma
                        definitiva. Entiendo que esta operación es irreversible.
                      </span>
                    </label>

                    <DialogFooter className="gap-2 border-t border-border pt-3">
                      <Button type="button" variant="outline" onClick={closeDeleteModal} disabled={deleteActionLoading}>
                        Cancelar
                      </Button>
                      <Button
                        type="submit"
                        variant="destructive"
                        disabled={
                          deleteActionLoading ||
                          !confirmChecked ||
                          confirmPhraseInput.trim().toUpperCase() !== expectedConfirmPhrase.trim().toUpperCase()
                        }
                      >
                        {deleteActionLoading ? <RefreshCw className="animate-spin" aria-hidden="true" /> : <Trash2 aria-hidden="true" />}
                        {deleteActionLoading ? 'Eliminando copropiedad...' : 'Eliminar definitivamente'}
                      </Button>
                    </DialogFooter>
                  </form>
                )}

                {deletePhase === 'SUCCESS' && (
                  <div className="space-y-1 py-4 text-center" role="status">
                    <h3 className="text-base font-bold text-on-surface">Propiedad eliminada</h3>
                    <p className="mx-auto max-w-sm text-xs text-text-secondary">
                      La copropiedad fue removida del sistema y el evento quedó registrado en la bitácora de auditoría.
                    </p>
                  </div>
                )}
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
