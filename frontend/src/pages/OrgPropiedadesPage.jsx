import { useEffect, useState } from 'react';
import api from '../lib/api.js';
import { Card, CardHeader, CardTitle, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { Skeleton } from '../components/ui/skeleton.tsx';
import { Button } from '../components/ui/button.tsx';
import {
  Home, Plus, Search, MapPin, Building, AlertCircle, CheckCircle2, Power, Eye,
  Trash2, ShieldAlert, KeyRound, Clock, AlertTriangle, RefreshCw, Check
} from 'lucide-react';
import LocationSelector from '../components/ui/LocationSelector';

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
  const [newProp, setNewProp] = useState({
    nombre: '',
    idTipoPropiedad: 1,
    direccion: '',
    departamento: 'Bogotá D.C.',
    ciudad: 'Bogotá',
    pais: 'Colombia',
    tipoOcupacionPredominante: 'MIXTA',
  });

  async function loadData() {
    try {
      setLoading(true);
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
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
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
        tipoOcupacionPredominante: newProp.tipoOcupacionPredominante || 'MIXTA',
      });
      setSuccessMsg('Propiedad registrada exitosamente.');
      setIsModalOpen(false);
      setNewProp({
        nombre: '',
        idTipoPropiedad: 1,
        direccion: '',
        departamento: 'Bogotá D.C.',
        ciudad: 'Bogotá',
        pais: 'Colombia',
        tipoOcupacionPredominante: 'MIXTA',
      });
      await loadData();
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (err) {
      console.error('Error creating property:', err);
      if (err?.response?.status === 409 || err?.response?.data?.code === 'PLAN_LIMIT_EXCEEDED') {
        setCreateError(
          'Límite de propiedades alcanzado. Su plan actual no permite agregar más copropiedades. Actualice su suscripción para continuar.'
        );
      } else {
        setCreateError(err?.response?.data?.message || 'Error al registrar la propiedad.');
      }
    } finally {
      setCreating(false);
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

  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <Skeleton className="h-8 w-64 mb-2" />
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-44 rounded-xl" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-8 animate-fadeIn">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-border pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold tracking-tight text-foreground">
              Propiedades de la Organización
            </h1>
            <Badge variant="outline" className="text-xs px-2.5 py-0.5 font-medium">
              {used} / {limit > 0 ? limit : '∞'} utilizadas
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            Gestione y supervise todas las copropiedades, conjuntos y edificios bajo su administración.
          </p>
        </div>
        <div>
          <Button
            onClick={() => setIsModalOpen(true)}
            className="flex items-center gap-2"
          >
            <Plus className="w-4 h-4" />
            <span>Nueva Propiedad</span>
          </Button>
        </div>
      </div>

      {/* Notifications */}
      {successMsg && (
        <div className="bg-emerald-500/15 border border-emerald-500/30 text-emerald-700 dark:text-emerald-400 px-4 py-3 rounded-lg flex items-center gap-3 animate-fadeIn">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{successMsg}</span>
        </div>
      )}

      {error && (
        <div className="bg-destructive/15 border border-destructive text-destructive px-4 py-3 rounded-lg flex items-center gap-3 animate-fadeIn">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{error}</span>
        </div>
      )}

      {/* Plan limit warning banner */}
      {isAtLimit && (
        <div className="bg-amber-500/15 border border-amber-500/30 text-amber-700 dark:text-amber-400 px-4 py-3 rounded-lg flex items-center gap-3">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <div className="text-sm">
            <span className="font-semibold">Ha alcanzado el límite máximo de propiedades ({limit}).</span> Para registrar nuevas copropiedades, solicite una ampliación de su plan SaaS.
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-4 justify-between items-center">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Buscar por nombre o ciudad..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-9 pr-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </div>
        <div className="flex items-center gap-2 w-full sm:w-auto">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          >
            <option value="ALL">Todos los Estados</option>
            <option value="ACTIVA">Solo Activas</option>
            <option value="INACTIVA">Solo Inactivas</option>
          </select>
        </div>
      </div>

      {/* Grid of properties */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filtered.length === 0 ? (
          <div className="col-span-full py-12 text-center text-muted-foreground">
            <Home className="w-12 h-12 mx-auto mb-3 opacity-40" />
            <p className="font-medium text-base">No se encontraron propiedades</p>
            <p className="text-xs mt-1">Cree una nueva propiedad o ajuste los filtros de búsqueda.</p>
          </div>
        ) : (
          filtered.map((prop) => (
            <Card key={prop.id} className="border border-border/80 shadow-sm hover:shadow-md transition-all flex flex-col justify-between">
              <CardHeader className="border-b border-border/40 pb-3">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <CardTitle className="text-base font-bold text-foreground">
                      {prop.nombre}
                    </CardTitle>
                    <Badge variant="outline" className="text-[11px] mt-1">
                      {prop.tipoPropiedadNombre || 'Edificio Residencial'}
                    </Badge>
                  </div>
                  <Badge
                    variant={prop.estado === 'ACTIVA' ? 'default' : 'secondary'}
                    className={
                      prop.estado === 'ACTIVA'
                        ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-emerald-500/20 text-xs'
                        : 'text-xs'
                    }
                  >
                    {prop.estado || 'ACTIVA'}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent className="pt-4 space-y-3 flex-grow">
                <div className="text-xs text-muted-foreground flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 flex-shrink-0" />
                  <span>{prop.direccion || 'Sin dirección'}, {prop.ciudad || 'Colombia'}</span>
                </div>
                <div className="text-xs text-muted-foreground flex items-center gap-1.5">
                  <Building className="w-3.5 h-3.5 flex-shrink-0" />
                  <span>Ocupación: {prop.tipoOcupacionPredominante || 'MIXTA'}</span>
                </div>
              </CardContent>
              <div className="p-4 pt-0 border-t border-border/40 flex items-center justify-between mt-auto">
                <span className="text-[11px] font-mono text-muted-foreground">
                  ID: #{prop.id}
                </span>
                <div className="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleToggleStatus(prop)}
                    className="text-xs gap-1"
                  >
                    <Power className="w-3.5 h-3.5" />
                    <span>{prop.estado === 'ACTIVA' ? 'Desactivar' : 'Activar'}</span>
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => openDeleteModal(prop)}
                    className="text-xs gap-1 text-destructive hover:bg-destructive/10 hover:text-destructive"
                    title="Eliminación segura con PIN/OTP"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>Eliminar</span>
                  </Button>
                </div>
              </div>
            </Card>
          ))
        )}
      </div>

      {/* Create Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 animate-fadeIn">
          <Card className="w-full max-w-lg bg-background border-border shadow-xl">
            <CardHeader className="border-b border-border pb-4">
              <CardTitle className="text-lg font-bold text-foreground">
                Registrar Nueva Propiedad
              </CardTitle>
            </CardHeader>
            <CardContent className="pt-6">
              {createError && (
                <div className="bg-destructive/15 border border-destructive text-destructive px-3 py-2 rounded-lg text-xs flex items-center gap-2 mb-4">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  <span>{createError}</span>
                </div>
              )}
              <form onSubmit={handleCreate} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                    Nombre de la Copropiedad / Edificio *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="Ej. Torres del Parque"
                    value={newProp.nombre}
                    onChange={(e) => setNewProp({ ...newProp, nombre: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Tipo de Propiedad
                    </label>
                    <select
                      value={newProp.idTipoPropiedad}
                      onChange={(e) => setNewProp({ ...newProp, idTipoPropiedad: Number(e.target.value) })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    >
                      <option value={1}>Edificio Residencial</option>
                      <option value={2}>Conjunto Cerrado</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                      Ocupación
                    </label>
                    <select
                      value={newProp.tipoOcupacionPredominante}
                      onChange={(e) => setNewProp({ ...newProp, tipoOcupacionPredominante: e.target.value })}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                    >
                      <option value="PROPIETARIOS">Propietarios</option>
                      <option value="ARRENDATARIOS">Arrendatarios</option>
                      <option value="MIXTA">Mixta</option>
                    </select>
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase mb-1">
                    Dirección
                  </label>
                  <input
                    type="text"
                    placeholder="Ej. Calle 123 # 45-67"
                    value={newProp.direccion}
                    onChange={(e) => setNewProp({ ...newProp, direccion: e.target.value })}
                    className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
                  />
                </div>
                <LocationSelector
                  idPrefix="org-prop"
                  pais={newProp.pais}
                  departamento={newProp.departamento}
                  ciudad={newProp.ciudad}
                  onChange={({ pais, departamento, ciudad }) =>
                    setNewProp((prev) => ({ ...prev, pais, departamento, ciudad }))
                  }
                />

                <div className="flex justify-end gap-3 pt-4 border-t border-border">
                  <Button type="button" variant="outline" onClick={() => setIsModalOpen(false)}>
                    Cancelar
                  </Button>
                  <Button type="submit" disabled={creating}>
                    {creating ? 'Guardando...' : 'Crear Propiedad'}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Confirmation Modal for Suspension / Deactivation */}
      {statusConfirmProp && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 animate-fadeIn">
          <Card className="w-full max-w-md bg-background border-border shadow-xl">
            <CardHeader className="border-b border-border pb-4">
              <CardTitle className="text-base font-bold text-foreground flex items-center gap-2">
                <AlertCircle className="w-5 h-5 text-amber-500 shrink-0" />
                Suspender Operaciones de Copropiedad
              </CardTitle>
            </CardHeader>
            <CardContent className="pt-4 space-y-3 text-xs text-muted-foreground">
              <p>
                Está a punto de cambiar el estado de la copropiedad{' '}
                <strong className="text-foreground">{statusConfirmProp.nombre}</strong> a{' '}
                <span className="font-semibold text-amber-600 dark:text-amber-400">INACTIVA</span>.
              </p>
              <div className="p-3 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-700 dark:text-amber-300 space-y-1.5 text-[11px]">
                <div className="font-semibold flex items-center gap-1.5">
                  <AlertCircle className="w-3.5 h-3.5" />
                  Impacto Operativo:
                </div>
                <ul className="list-disc list-inside space-y-0.5">
                  <li>Se congelarán las operaciones de residentes, cartera y visitas para esta propiedad.</li>
                  <li>El historial y registros contables/auditoría se mantendrán intactos.</li>
                  <li>Podrá reactivar la copropiedad en cualquier momento desde esta consola.</li>
                </ul>
              </div>
              <div className="flex justify-end gap-2 pt-3 border-t border-border">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setStatusConfirmProp(null)}
                >
                  Cancelar
                </Button>
                <Button
                  type="button"
                  variant="destructive"
                  size="sm"
                  onClick={() => executeToggleStatus(statusConfirmProp)}
                >
                  Confirmar Suspensión
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Secure Property Deletion Modal with OTP Challenge (P1-01) */}
      {deleteModalProp && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4 animate-fadeIn">
          <Card className="w-full max-w-lg bg-background border-destructive/40 shadow-2xl overflow-hidden">
            {/* Modal Header */}
            <CardHeader className="border-b border-border bg-destructive/5 pb-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <div className="w-9 h-9 rounded-full bg-destructive/15 flex items-center justify-center text-destructive">
                    {deletePhase === 'INIT' && <ShieldAlert className="w-5 h-5" />}
                    {deletePhase === 'OTP' && <KeyRound className="w-5 h-5" />}
                    {deletePhase === 'CONFIRM' && <AlertTriangle className="w-5 h-5" />}
                    {deletePhase === 'SUCCESS' && <CheckCircle2 className="w-5 h-5 text-emerald-500" />}
                  </div>
                  <div>
                    <CardTitle className="text-base font-bold text-foreground">
                      {deletePhase === 'INIT' && 'Eliminación Segura de Copropiedad'}
                      {deletePhase === 'OTP' && 'Verificación de Seguridad (OTP)'}
                      {deletePhase === 'CONFIRM' && 'Confirmación Definitiva de Destrucción'}
                      {deletePhase === 'SUCCESS' && 'Copropiedad Eliminada'}
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      {deleteModalProp.nombre} • ID #{deleteModalProp.id}
                    </p>
                  </div>
                </div>

                {deletePhase !== 'SUCCESS' && (
                  <Badge variant="outline" className="text-[10px] font-mono border-destructive/30 text-destructive bg-destructive/10">
                    {deletePhase === 'INIT' && 'Paso 1 de 3'}
                    {deletePhase === 'OTP' && 'Paso 2 de 3'}
                    {deletePhase === 'CONFIRM' && 'Paso 3 de 3'}
                  </Badge>
                )}
              </div>
            </CardHeader>

            <CardContent className="pt-5 space-y-4">
              {/* Error banner */}
              {deleteModalError && (
                <div className="bg-destructive/15 border border-destructive text-destructive px-3 py-2.5 rounded-lg text-xs flex items-start gap-2 animate-fadeIn">
                  <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                  <span className="flex-1 font-medium">{deleteModalError}</span>
                </div>
              )}

              {/* PHASE 1: INITIAL WARNING */}
              {deletePhase === 'INIT' && (
                <div className="space-y-4 text-xs">
                  <div className="p-3.5 rounded-lg bg-destructive/10 border border-destructive/25 text-destructive space-y-2">
                    <div className="font-semibold text-sm flex items-center gap-1.5">
                      <AlertTriangle className="w-4 h-4 shrink-0" />
                      Acción Crítica e Irreversible
                    </div>
                    <p className="text-[12px] leading-relaxed text-destructive/90">
                      La eliminación de una copropiedad destruirá en cascada todos sus datos asociados. Esta acción no se puede deshacer.
                    </p>
                  </div>

                  <div className="space-y-2 text-muted-foreground text-xs">
                    <p className="font-semibold text-foreground">Elementos que serán eliminados de la base de datos:</p>
                    <ul className="grid grid-cols-2 gap-1.5 list-disc list-inside text-[11px]">
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

                  <div className="p-3 rounded-lg bg-muted border border-border text-[11px] text-muted-foreground space-y-1">
                    <div className="font-semibold text-foreground flex items-center gap-1.5">
                      <KeyRound className="w-3.5 h-3.5 text-primary" />
                      Protocolo de Seguridad SAED:
                    </div>
                    <p>
                      Para proceder, el sistema enviará un código de verificación de 6 dígitos (OTP) al correo del Administrador de la Organización.
                    </p>
                  </div>

                  <div className="flex justify-end gap-2 pt-3 border-t border-border">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={closeDeleteModal}
                      disabled={deleteActionLoading}
                    >
                      Cancelar
                    </Button>
                    <Button
                      type="button"
                      variant="destructive"
                      size="sm"
                      onClick={handleRequestDeletionOtp}
                      disabled={deleteActionLoading}
                      className="gap-1.5"
                    >
                      {deleteActionLoading ? (
                        <>
                          <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                          <span>Generando desafío...</span>
                        </>
                      ) : (
                        <>
                          <KeyRound className="w-3.5 h-3.5" />
                          <span>Solicitar Código de Seguridad (OTP)</span>
                        </>
                      )}
                    </Button>
                  </div>
                </div>
              )}

              {/* PHASE 2: OTP VERIFICATION */}
              {deletePhase === 'OTP' && (
                <form onSubmit={handleVerifyDeletionOtp} className="space-y-4 text-xs">
                  <div className="text-center space-y-1.5">
                    <p className="text-muted-foreground">
                      Hemos enviado un código PIN/OTP de 6 dígitos a su correo institucional:
                    </p>
                    <div className="inline-block font-mono font-bold text-foreground text-sm bg-muted px-3 py-1 rounded border border-border">
                      {deleteChallenge?.maskedEmail || 'su correo registrado'}
                    </div>
                  </div>

                  {/* Timer and attempts */}
                  <div className="flex items-center justify-between px-2 py-1.5 rounded bg-muted/60 text-[11px] text-muted-foreground">
                    <div className="flex items-center gap-1">
                      <Clock className="w-3.5 h-3.5" />
                      <span>Válido por:</span>
                      <span className={`font-mono font-bold ${deleteCountdown < 60 ? 'text-destructive animate-pulse' : 'text-foreground'}`}>
                        {formatTime(deleteCountdown)}
                      </span>
                    </div>
                    <div className="flex items-center gap-1">
                      <span>Intentos restantes:</span>
                      <Badge variant={otpAttemptsLeft <= 2 ? 'destructive' : 'secondary'} className="text-[10px] px-1.5 py-0">
                        {otpAttemptsLeft}
                      </Badge>
                    </div>
                  </div>

                  {/* OTP Input */}
                  <div className="space-y-1 text-center">
                    <label className="block text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                      Ingrese el Código de 6 Dígitos
                    </label>
                    <input
                      type="text"
                      inputMode="numeric"
                      autoFocus
                      maxLength={6}
                      placeholder="• • • • • •"
                      value={otpCode}
                      onChange={(e) => setOtpCode(e.target.value.replace(/[^a-zA-Z0-9]/g, '').slice(0, 6))}
                      className="w-48 mx-auto block text-center font-mono text-2xl font-bold tracking-[0.35em] px-3 py-2 border-2 border-primary/40 rounded-lg bg-background text-foreground focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/20 uppercase"
                    />
                    <p className="text-[10px] text-muted-foreground mt-1">
                      Revise su bandeja de entrada o spam.
                    </p>
                  </div>

                  <div className="flex items-center justify-between pt-3 border-t border-border">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={handleRequestDeletionOtp}
                      disabled={deleteActionLoading || deleteCountdown > 240}
                      className="text-xs text-muted-foreground gap-1"
                      title={deleteCountdown > 240 ? 'Espere 60 segundos para reenviar' : 'Reenviar código OTP'}
                    >
                      <RefreshCw className={`w-3.5 h-3.5 ${deleteActionLoading ? 'animate-spin' : ''}`} />
                      <span>Reenviar Código</span>
                    </Button>

                    <div className="flex gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={closeDeleteModal}
                        disabled={deleteActionLoading}
                      >
                        Cancelar
                      </Button>
                      <Button
                        type="submit"
                        variant="default"
                        size="sm"
                        disabled={deleteActionLoading || otpCode.trim().length < 6 || deleteCountdown === 0 || otpAttemptsLeft === 0}
                        className="gap-1.5"
                      >
                        {deleteActionLoading ? (
                          <>
                            <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                            <span>Validando...</span>
                          </>
                        ) : (
                          <>
                            <Check className="w-3.5 h-3.5" />
                            <span>Verificar Código</span>
                          </>
                        )}
                      </Button>
                    </div>
                  </div>
                </form>
              )}

              {/* PHASE 3: SECOND CONFIRMATION */}
              {deletePhase === 'CONFIRM' && (
                <form onSubmit={handleConfirmFinalDeletion} className="space-y-4 text-xs">
                  <div className="p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-700 dark:text-emerald-400 flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span className="font-medium">Identidad confirmada mediante OTP por correo.</span>
                  </div>

                  <div className="p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs space-y-1">
                    <p className="font-bold">Propiedad a destruir definitivamente:</p>
                    <p className="font-mono text-sm">{deleteModalProp.nombre} (ID: #{deleteModalProp.id})</p>
                  </div>

                  <div className="space-y-1.5">
                    <label className="block text-[11px] font-semibold text-foreground">
                      Para confirmar la destrucción final, escriba exactamente la siguiente frase:
                    </label>
                    <div className="p-2 rounded bg-muted border border-border font-mono font-bold text-center text-destructive text-xs select-all">
                      {expectedConfirmPhrase}
                    </div>
                    <input
                      type="text"
                      autoFocus
                      placeholder={expectedConfirmPhrase}
                      value={confirmPhraseInput}
                      onChange={(e) => setConfirmPhraseInput(e.target.value)}
                      className="w-full px-3 py-2 border border-input rounded-lg bg-background text-sm font-mono text-foreground focus:outline-none focus:ring-2 focus:ring-destructive"
                    />
                  </div>

                  <label className="flex items-start gap-2.5 p-2.5 rounded-lg border border-destructive/30 bg-destructive/5 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={confirmChecked}
                      onChange={(e) => setConfirmChecked(e.target.checked)}
                      className="mt-0.5 rounded border-destructive text-destructive focus:ring-destructive"
                    />
                    <span className="text-[11px] text-foreground font-medium leading-tight">
                      Confirmo expresamente que deseo destruir esta propiedad, sus unidades y todos los datos asociados de forma definitiva. Entiendo que esta operación es irreversible.
                    </span>
                  </label>

                  <div className="flex justify-end gap-2 pt-3 border-t border-border">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={closeDeleteModal}
                      disabled={deleteActionLoading}
                    >
                      Cancelar
                    </Button>
                    <Button
                      type="submit"
                      variant="destructive"
                      size="sm"
                      disabled={
                        deleteActionLoading ||
                        !confirmChecked ||
                        confirmPhraseInput.trim().toUpperCase() !== expectedConfirmPhrase.trim().toUpperCase()
                      }
                      className="gap-1.5"
                    >
                      {deleteActionLoading ? (
                        <>
                          <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                          <span>Destruyendo copropiedad...</span>
                        </>
                      ) : (
                        <>
                          <Trash2 className="w-3.5 h-3.5" />
                          <span>Eliminar Definitivamente</span>
                        </>
                      )}
                    </Button>
                  </div>
                </form>
              )}

              {/* PHASE 4: SUCCESS */}
              {deletePhase === 'SUCCESS' && (
                <div className="py-6 text-center space-y-3 animate-fadeIn">
                  <div className="w-12 h-12 rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 flex items-center justify-center mx-auto">
                    <CheckCircle2 className="w-7 h-7" />
                  </div>
                  <div className="space-y-1">
                    <h3 className="text-base font-bold text-foreground">
                      Propiedad Eliminada Exitosamente
                    </h3>
                    <p className="text-xs text-muted-foreground max-w-sm mx-auto">
                      La copropiedad ha sido removida del sistema. La bitácora de auditoría ha registrado el evento criptográficamente.
                    </p>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
