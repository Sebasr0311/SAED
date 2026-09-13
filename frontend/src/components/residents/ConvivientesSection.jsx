import { useState, useMemo } from 'react';
import { toast } from 'sonner';
import {
  Users,
  UserPlus,
  UserCheck,
  UserX,
  UserMinus,
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Shield,
  Trash2,
  RotateCcw,
  Info,
  Mail,
  Phone,
  RefreshCw,
  Copy,
  Key,
} from 'lucide-react';

import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../ui/card.tsx';
import { Badge } from '../ui/badge.tsx';
import { Button } from '../ui/button.tsx';
import { Modal } from '../ui/Modal.jsx';
import { ConfirmDialog } from '../ui/ConfirmDialog.jsx';
import { Skeleton } from '../ui/skeleton.tsx';
import { Input, Select } from '../ui/Form.jsx';
import api from '../../lib/api.js';
import { useLiveValidation } from '../../lib/hooks.js';
import {
  valNombre,
  valApellido,
  valTelefono,
  valEmail,
  valDocumento,
  valUsername,
  valPassword,
  getDocPlaceholder,
  getDocHint,
  soloLetras,
  soloNumeros,
  soloAlfanumerico,
} from '../../lib/validation.js';

export function ConvivientesSection({
  unitId,
  residentId,
  tiposDoc = [],
  titularFallback = null,
  quotaData = null,
  quotaLoading = false,
  residentsData = null,
  residentsLoading = false,
  onRefresh = () => {},
}) {
  // Modal de registro de conviviente
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [form, setForm] = useState({
    primerNombre: '',
    primerApellido: '',
    idTipoDocumento: 1,
    numeroDocumento: '',
    telefono: '',
    email: '',
    nombreUsuario: '',
    password: '',
  });
  const [formError, setFormError] = useState(null);
  const [formErrors, setFormErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const [createdCredentials, setCreatedCredentials] = useState(null);
  const [showHistory, setShowHistory] = useState(false);

  // Hook de live validation
  const { touch, touchAll, resetTouched, fieldError } = useLiveValidation();

  // Estados de diálogo de confirmación
  const [confirmDialog, setConfirmDialog] = useState({
    open: false,
    type: null, // 'SUSPEND' | 'REACTIVATE' | 'UNLINK' | 'DELETE_PERMANENT'
    resident: null,
    title: '',
    message: '',
    confirmLabel: '',
    danger: false,
  });

  // Auxiliares seguros para resolución de IDs y estados
  const getResidentId = (r) => r?.id ?? r?.idResidenteUnidad ?? r?.persona?.idPersona ?? r?.idPersona;
  const getResidentEstado = (r) => (r?.estado ?? r?.estadoResidente ?? 'ACTIVO').toUpperCase();

  // Código activo de tipo de documento para hints y placeholders
  const activeDocCodigo = useMemo(() => {
    const selected = (tiposDoc || []).find((t) => Number(t.idTipoDoc) === Number(form.idTipoDocumento));
    return selected?.codigo || 'CC';
  }, [tiposDoc, form.idTipoDocumento]);

  // Normalización de Quota (soporta contrato real del backend y aliases)
  const quota = useMemo(() => quotaData?.raw || quotaData || null, [quotaData]);
  const limit = quota?.limiteConfigurado ?? quota?.limit ?? 4;
  const currentCount = quota?.convivientesActivos ?? quota?.currentCount ?? 0;
  const available = quota?.cuposDisponibles ?? quota?.available ?? Math.max(0, limit - currentCount);
  const reached = quota?.limiteAlcanzado ?? quota?.reached ?? (available <= 0);
  const isLastSpot = available === 1 && !reached;

  // Normalización de habitantes
  const rawResidents = useMemo(() => {
    if (Array.isArray(residentsData)) return residentsData;
    if (Array.isArray(residentsData?.items)) return residentsData.items;
    return [];
  }, [residentsData]);

  const listaHabitantes = useMemo(() => {
    if (rawResidents.length > 0) {
      if (titularFallback) {
        const titularDoc = (titularFallback.numeroDocumento || titularFallback.persona?.numeroDocumento || '').trim();
        const titularId = titularFallback.id || titularFallback.idPersona;
        const exists = rawResidents.some((r) => {
          const rDoc = (r.persona?.numeroDocumento || r.numeroDocumento || '').trim();
          const rId = r.persona?.idPersona || r.idPersona || r.id;
          const isTit = (r.tipoResidente || '').toUpperCase();
          return (
            (titularDoc && titularDoc !== '—' && rDoc === titularDoc) ||
            (titularId && rId === titularId) ||
            isTit === 'TITULAR' ||
            isTit === 'PROPIETARIO' ||
            isTit === 'PROPIETARIO_RESIDENTE'
          );
        });
        if (!exists) {
          return [titularFallback, ...rawResidents];
        }
      }
      return rawResidents;
    }
    if (titularFallback) return [titularFallback];
    return [];
  }, [rawResidents, titularFallback]);

  // Habitantes activos del hogar (excluye aquellos con fechaFin de desvinculación)
  const habitantesActivos = useMemo(() => {
    return listaHabitantes.filter((h) => !h.fechaFin);
  }, [listaHabitantes]);

  // Historial de habitantes desvinculados de la unidad
  const habitantesDesvinculados = useMemo(() => {
    return listaHabitantes.filter((h) => !!h.fechaFin);
  }, [listaHabitantes]);

  // Manejador de apertura de modal con validación previa de cupo
  const handleOpenAddModal = () => {
    if (reached) {
      toast.error('Límite alcanzado: Esta unidad ya cuenta con el máximo de convivientes permitidos.');
      return;
    }
    setFormError(null);
    setFormErrors({});
    resetTouched();
    setForm({
      primerNombre: '',
      primerApellido: '',
      idTipoDocumento: tiposDoc[0]?.idTipoDoc || 1,
      numeroDocumento: '',
      telefono: '',
      email: '',
      nombreUsuario: '',
      password: '',
    });
    setAddModalOpen(true);
  };

  // Envío del formulario de registro de conviviente con validación exhaustiva
  const handleSaveConviviente = async (e) => {
    e.preventDefault();
    if (reached) {
      setFormError('Esta unidad ya ha alcanzado el límite de convivientes permitidos.');
      return;
    }

    touchAll([
      'primerNombre',
      'primerApellido',
      'numeroDocumento',
      'email',
      'telefono',
      'nombreUsuario',
      'password',
    ]);

    const errors = {};
    const vNombre = valNombre(form.primerNombre, 'El primer nombre', { required: true });
    if (!vNombre.ok) errors.primerNombre = vNombre.mensaje;

    const vApellido = valApellido(form.primerApellido, 'El primer apellido', { required: true });
    if (!vApellido.ok) errors.primerApellido = vApellido.mensaje;

    const vDoc = valDocumento(form.numeroDocumento, activeDocCodigo, 'El número de documento');
    if (!vDoc.ok) errors.numeroDocumento = vDoc.mensaje;

    const vEmail = valEmail(form.email, { required: true });
    if (!vEmail.ok) errors.email = vEmail.mensaje;

    if (form.telefono) {
      const vTel = valTelefono(form.telefono, { required: false });
      if (!vTel.ok) errors.telefono = vTel.mensaje;
    }

    const vUser = valUsername(form.nombreUsuario);
    if (!vUser.ok) errors.nombreUsuario = vUser.mensaje;

    if (form.password) {
      const vPass = valPassword(form.password, 'La contraseña');
      if (!vPass.ok) errors.password = vPass.mensaje;
    }

    if (Object.keys(errors).length > 0) {
      setFormErrors(errors);
      const firstError = Object.values(errors)[0];
      setFormError(firstError);
      return;
    }

    setSaving(true);
    setFormError(null);
    setFormErrors({});

    try {
      const resp = await api.post('/usuarios', {
        primerNombre: form.primerNombre.trim(),
        primerApellido: form.primerApellido.trim(),
        tipoDocumentoId: Number(form.idTipoDocumento),
        numeroDocumento: form.numeroDocumento.trim(),
        telefono: form.telefono ? form.telefono.trim() : null,
        email: form.email.trim().toLowerCase(),
        nombreUsuario: form.nombreUsuario.trim().toLowerCase(),
        password: form.password ? form.password : undefined,
        rol: 'RESIDENTE_CONVIVENCIA',
        idUnidad: Number(unitId),
      });

      const uData = resp?.data || resp || {};
      setCreatedCredentials({
        username: uData.username || form.nombreUsuario.trim().toLowerCase(),
        password: uData.passwordGenerada || form.password || '(Autogenerada y enviada al correo)',
        email: uData.email || form.email.trim().toLowerCase(),
        nombre: `${form.primerNombre.trim()} ${form.primerApellido.trim()}`,
      });

      toast.success('Residente de convivencia registrado exitosamente. Se han enviado las credenciales de acceso por correo.');
      setAddModalOpen(false);
      resetTouched();
      onRefresh();
    } catch (err) {
      const respData = err?.response?.data;
      const errorMsg = respData?.message || respData?.error || err?.message || '';
      const isQuota =
        respData?.code === 'CONVIVIENTE_LIMIT_EXCEEDED' ||
        errorMsg.toLowerCase().includes('límite') ||
        errorMsg.toLowerCase().includes('limite') ||
        errorMsg.toLowerCase().includes('cupo');

      if (isQuota) {
        setFormError('No fue posible registrar el conviviente: la unidad ha alcanzado el límite máximo de convivientes activos permitido.');
      } else if (errorMsg) {
        setFormError(errorMsg);
      } else {
        setFormError('Error al registrar el habitante de convivencia.');
      }
    } finally {
      setSaving(false);
    }
  };

  // Apertura de diálogo de confirmación para Suspender
  const promptSuspend = (resident) => {
    const nombre = resident.persona
      ? `${resident.persona.primerNombre || ''} ${resident.persona.primerApellido || ''}`.trim()
      : `${resident.nombres || ''} ${resident.apellidos || ''}`.trim();

    setConfirmDialog({
      open: true,
      type: 'SUSPEND',
      resident,
      title: '¿Suspender habitante de convivencia?',
      message: `¿Deseas suspender a ${nombre}? El habitante pasará a estado inactivo y liberará un cupo activo en la unidad. Podrás reactivarlo cuando haya cupo disponible.`,
      confirmLabel: 'Suspender Habitante',
      danger: false,
    });
  };

  // Apertura de diálogo de confirmación para Reactivar
  const promptReactivate = (resident) => {
    if (reached) {
      toast.error('No es posible reactivar: la unidad ya tiene el límite de convivientes activos alcanzado.');
      return;
    }

    const nombre = resident.persona
      ? `${resident.persona.primerNombre || ''} ${resident.persona.primerApellido || ''}`.trim()
      : `${resident.nombres || ''} ${resident.apellidos || ''}`.trim();

    setConfirmDialog({
      open: true,
      type: 'REACTIVATE',
      resident,
      title: '¿Reactivar habitante de convivencia?',
      message: `¿Deseas reactivar a ${nombre}? Ocupará 1 de los ${available} cupo(s) disponible(s) en la unidad.`,
      confirmLabel: 'Reactivar Habitante',
      danger: false,
    });
  };

  // Apertura de diálogo de confirmación para Desvincular
  const promptUnlink = (resident) => {
    const nombre = resident.persona
      ? `${resident.persona.primerNombre || ''} ${resident.persona.primerApellido || ''}`.trim()
      : `${resident.nombres || ''} ${resident.apellidos || ''}`.trim();

    setConfirmDialog({
      open: true,
      type: 'UNLINK',
      resident,
      title: '¿Desvincular habitante de la unidad?',
      message: `¿Deseas desvincular a ${nombre} de esta unidad? La persona dejará de figurar en el censo activo de la unidad. Su historial y registros previos se conservarán intactos.`,
      confirmLabel: 'Desvincular',
      danger: true,
    });
  };

  // Apertura de diálogo de confirmación para Eliminar Definitivamente
  const promptDeletePermanent = (resident) => {
    const nombre = resident.persona
      ? `${resident.persona.primerNombre || ''} ${resident.persona.primerApellido || ''}`.trim()
      : `${resident.nombres || ''} ${resident.apellidos || ''}`.trim();

    setConfirmDialog({
      open: true,
      type: 'DELETE_PERMANENT',
      resident,
      title: '¿Eliminar habitante definitivamente?',
      message: `¿Deseas eliminar definitivamente a ${nombre}? Se eliminará permanentemente de la unidad y se liberará su cupo activo. Esta acción no se puede deshacer.`,
      confirmLabel: 'Eliminar Definitivamente',
      danger: true,
    });
  };

  // Ejecución de la acción confirmada
  const handleConfirmAction = async () => {
    const { type, resident } = confirmDialog;
    const resId = getResidentId(resident);
    if (!resident || !resId) {
      toast.error('No fue posible identificar al habitante.');
      return;
    }

    try {
      if (type === 'SUSPEND') {
        await api.patch(`/units/${unitId}/residents/${resId}/status`, { estado: 'INACTIVO' });
        toast.success('Habitante suspendido exitosamente. Se ha liberado un cupo.');
      } else if (type === 'REACTIVATE') {
        await api.patch(`/units/${unitId}/residents/${resId}/status`, { estado: 'ACTIVO' });
        toast.success('Habitante reactivado exitosamente.');
      } else if (type === 'UNLINK') {
        await api.delete(`/units/${unitId}/residents/${resId}`);
        toast.success('Habitante desvinculado exitosamente de la unidad.');
      } else if (type === 'DELETE_PERMANENT') {
        await api.delete(`/units/${unitId}/residents/${resId}?permanent=true`);
        toast.success('Habitante eliminado definitivamente de la unidad.');
      }
      setConfirmDialog((prev) => ({ ...prev, open: false, resident: null }));
      onRefresh();
    } catch (err) {
      const respData = err?.response?.data;
      const errorMsg = respData?.message || respData?.error || err?.message || '';
      const isQuota =
        respData?.code === 'CONVIVIENTE_LIMIT_EXCEEDED' ||
        errorMsg.toLowerCase().includes('límite') ||
        errorMsg.toLowerCase().includes('limite') ||
        errorMsg.toLowerCase().includes('cupo');

      if (isQuota) {
        toast.error('No hay cupos disponibles en la unidad para completar esta acción.');
      } else if (errorMsg) {
        toast.error(errorMsg);
      } else {
        toast.error('Error al procesar la solicitud.');
      }
    }
  };

  return (
    <Card className="lg:col-span-3 border-border/80 shadow-sm">
      <CardHeader className="pb-4">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div>
            <div className="flex items-center gap-2.5 flex-wrap">
              <CardTitle className="text-lg flex items-center gap-2 text-foreground">
                <Users className="w-5 h-5 text-primary" />
                <span>Núcleo Familiar y Habitantes de la Unidad</span>
              </CardTitle>

              {/* Badge de Estado del Cupo */}
              {!quotaLoading && quota && (
                <>
                  {reached ? (
                    <Badge variant="destructive" className="gap-1 px-2 py-0.5 text-xs font-semibold">
                      <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                      Límite alcanzado
                    </Badge>
                  ) : isLastSpot ? (
                    <Badge className="bg-amber-500/15 text-amber-700 dark:text-amber-400 border border-amber-500/30 gap-1 px-2 py-0.5 text-xs font-semibold">
                      <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                      Último cupo disponible
                    </Badge>
                  ) : (
                    <Badge className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 gap-1 px-2 py-0.5 text-xs font-semibold">
                      <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
                      {available} {available === 1 ? 'cupo disponible' : 'cupos disponibles'}
                    </Badge>
                  )}
                </>
              )}
            </div>
            <CardDescription className="mt-1">
              Personas autorizadas formalmente para residir en el inmueble y asignación de cupos de convivencia
            </CardDescription>
          </div>

          {/* Botón de Agregar Conviviente */}
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={onRefresh}
              className="h-9 w-9 p-0 shrink-0 text-muted-foreground hover:text-foreground"
              title="Actualizar habitantes y cupo"
              aria-label="Actualizar habitantes y cupo"
            >
              <RefreshCw className={`w-4 h-4 ${quotaLoading || residentsLoading ? 'animate-spin' : ''}`} />
            </Button>

            <Button
              size="sm"
              onClick={handleOpenAddModal}
              disabled={reached || quotaLoading}
              className="gap-1.5 shrink-0"
              title={reached ? 'Límite de convivientes alcanzado para esta unidad' : 'Registrar nuevo habitante'}
            >
              <UserPlus className="w-4 h-4" />
              <span>Agregar Conviviente</span>
            </Button>
          </div>
        </div>

        {/* METER / INDICADOR VISUAL DEL CUPO PARAMETRIZADO */}
        <div className="mt-4 p-3.5 rounded-xl border border-border/70 bg-muted/20 space-y-3">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 text-xs">
            <div className="flex items-center gap-2">
              <span className="font-semibold text-foreground uppercase tracking-wider text-[11px]">
                Cupo de Convivientes:
              </span>
              {quotaLoading ? (
                <Skeleton className="h-4 w-28" />
              ) : (
                <span className="font-medium text-foreground">
                  <strong className="text-primary font-bold">{currentCount}</strong> de <strong>{limit}</strong> utilizados
                </span>
              )}
            </div>
            <div className="flex items-center gap-2">
              <span className="text-muted-foreground">Disponibles en unidad:</span>
              {quotaLoading ? (
                <Skeleton className="h-4 w-12" />
              ) : (
                <span
                  className={`font-mono font-bold ${
                    reached
                      ? 'text-destructive'
                      : isLastSpot
                      ? 'text-amber-600 dark:text-amber-400'
                      : 'text-emerald-600 dark:text-emerald-400'
                  }`}
                >
                  {available} {available === 1 ? 'cupo' : 'cupos'}
                </span>
              )}
            </div>
          </div>

          {/* Barra de Progreso Continua de Ocupación */}
          <div className="space-y-1">
            <div className="w-full bg-muted/70 h-2 rounded-full overflow-hidden border border-border/40" role="progressbar" aria-valuenow={currentCount} aria-valuemin={0} aria-valuemax={limit} aria-label="Porcentaje de ocupación de cupo de convivientes">
              <div
                className={`h-full rounded-full transition-all duration-500 ease-out ${
                  reached
                    ? 'bg-destructive'
                    : isLastSpot
                    ? 'bg-amber-500'
                    : 'bg-primary'
                }`}
                style={{ width: `${limit > 0 ? Math.min(100, Math.round((currentCount / limit) * 100)) : 0}%` }}
              />
            </div>
            <div className="flex items-center justify-between text-[10px] text-muted-foreground pt-0.5">
              <span>Saturación del límite: {limit > 0 ? Math.min(100, Math.round((currentCount / limit) * 100)) : 0}%</span>
              <span>Tope máximo: {limit} personas</span>
            </div>
          </div>

          {/* Ranuras visuales de cupo (Slots) */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
            {Array.from({ length: limit }).map((_, index) => {
              const isOccupied = index < currentCount;
              return (
                <div
                  key={index}
                  className={`flex items-center gap-2 p-2 rounded-lg border text-xs transition-colors ${
                    isOccupied
                      ? 'bg-primary/10 border-primary/30 text-foreground font-medium'
                      : 'bg-background/60 border-dashed border-border/80 text-muted-foreground'
                  }`}
                >
                  <div
                    className={`w-2 h-2 rounded-full shrink-0 ${
                      isOccupied
                        ? reached
                          ? 'bg-destructive'
                          : isLastSpot
                          ? 'bg-amber-500'
                          : 'bg-primary'
                        : 'bg-muted-foreground/30'
                    }`}
                  />
                  <span className="truncate">
                    {isOccupied ? `Ocupado #${index + 1}` : `Libre #${index + 1}`}
                  </span>
                </div>
              );
            })}
          </div>

          {/* Mensajes de Contexto y Reglas de Negocio */}
          {reached ? (
            <div role="alert" className="p-2.5 rounded-lg bg-destructive/10 border border-destructive/20 text-xs text-destructive flex items-start gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" />
              <div>
                <strong>Límite de convivientes alcanzado:</strong> Esta unidad ya tiene el máximo de {limit} convivientes activos permitido por reglamento. Para registrar o activar a otro miembro, suspende o desvincula un habitante existente.
              </div>
            </div>
          ) : isLastSpot ? (
            <div role="status" className="p-2.5 rounded-lg bg-amber-500/10 border border-amber-500/20 text-xs text-amber-700 dark:text-amber-400 flex items-start gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden="true" />
              <div>
                <strong>Último cupo disponible:</strong> Queda únicamente 1 cupo activo antes de alcanzar el tope configurado para esta unidad.
              </div>
            </div>
          ) : (
            <div className="text-[11px] text-muted-foreground flex items-center gap-2">
              <Info className="w-3.5 h-3.5 text-primary shrink-0" aria-hidden="true" />
              <span>El titular de la unidad no consume cupo de convivencia. Cada conviviente activo cuenta con usuario y credenciales propias.</span>
            </div>
          )}
        </div>
      </CardHeader>

      <CardContent>
        {residentsLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <Skeleton className="h-36 rounded-xl" />
            <Skeleton className="h-36 rounded-xl" />
            <Skeleton className="h-36 rounded-xl" />
          </div>
        ) : habitantesActivos.length === 0 ? (
          <div className="p-8 text-center rounded-xl border border-dashed border-border bg-muted/10 space-y-3">
            <Users className="w-10 h-10 text-muted-foreground mx-auto opacity-50" />
            <div className="space-y-1">
              <h4 className="text-sm font-semibold text-foreground">Sin habitantes adicionales registrados</h4>
              <p className="text-xs text-muted-foreground max-w-md mx-auto">
                No hay convivientes registrados en esta unidad. Puedes agregar hasta {available} conviviente(s) con acceso independiente.
              </p>
            </div>
            {!reached && (
              <Button size="sm" onClick={handleOpenAddModal} className="gap-1.5 mt-2">
                <UserPlus className="w-4 h-4" />
                <span>Agregar Primer Conviviente</span>
              </Button>
            )}
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {habitantesActivos.map((h, idx) => {
              const hNombre = h.persona
                ? `${h.persona.primerNombre || ''} ${h.persona.primerApellido || ''}`.trim()
                : `${h.nombres || ''} ${h.apellidos || ''}`.trim();
              const hDoc = h.persona?.numeroDocumento || h.numeroDocumento || '—';
              const hEmail = h.persona?.email || h.email || '';
              const hTel = h.persona?.telefono || h.telefono || '';
              const hRol = (h.tipoResidente || (h === titularFallback ? 'TITULAR' : 'CONVIVIENTE')).toUpperCase();
              const isTitular = hRol === 'TITULAR' || hRol === 'PROPIETARIO' || hRol === 'PROPIETARIO_RESIDENTE';
              const isActivo = getResidentEstado(h) === 'ACTIVO';
              const hInitials = (hNombre[0] || 'R').toUpperCase();
              const hId = getResidentId(h);

              return (
                <div
                  key={hId || idx}
                  className={`flex flex-col justify-between p-4 rounded-xl border transition-all duration-150 ${
                    isTitular
                      ? 'bg-card border-primary/30 shadow-sm'
                      : isActivo
                      ? 'bg-card border-border/80 hover:border-border'
                      : 'bg-muted/30 border-dashed border-border/80 opacity-75'
                  }`}
                >
                  <div className="space-y-3">
                    {/* Encabezado de la tarjeta */}
                    <div className="flex items-start gap-3">
                      <div
                        className={`w-10 h-10 rounded-full flex items-center justify-center font-bold text-sm shrink-0 border ${
                          isTitular
                            ? 'bg-primary/15 text-primary border-primary/30'
                            : isActivo
                            ? 'bg-secondary text-secondary-foreground border-border'
                            : 'bg-muted text-muted-foreground border-border'
                        }`}
                      >
                        {hInitials}
                      </div>

                      <div className="min-w-0 flex-1">
                        <div className="flex items-center justify-between gap-1 flex-wrap">
                          <h4 className="text-sm font-bold text-foreground truncate">{hNombre}</h4>
                          <Badge
                            variant={isTitular ? 'default' : 'secondary'}
                            className="text-[10px] px-1.5 py-0 font-medium"
                          >
                            {isTitular ? 'TITULAR' : 'CONVIVIENTE'}
                          </Badge>
                        </div>
                        <p className="text-xs text-muted-foreground font-mono mt-0.5">Doc: {hDoc}</p>
                      </div>
                    </div>

                    {/* Metadata y Estado */}
                    <div className="space-y-1 pt-1 border-t border-border/40 text-xs">
                      {hEmail && (
                        <div className="flex items-center gap-1.5 text-muted-foreground truncate">
                          <Mail className="w-3 h-3 shrink-0" />
                          <span className="truncate">{hEmail}</span>
                        </div>
                      )}
                      {hTel && (
                        <div className="flex items-center gap-1.5 text-muted-foreground">
                          <Phone className="w-3 h-3 shrink-0" />
                          <span>{hTel}</span>
                        </div>
                      )}

                      <div className="pt-1 flex items-center justify-between">
                        <span className="text-muted-foreground text-[11px]">Condición:</span>
                        {isActivo ? (
                          <span className="inline-flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-400 font-semibold">
                            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 shrink-0" />
                            Activo
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground font-semibold">
                            <span className="w-1.5 h-1.5 rounded-full bg-muted-foreground shrink-0" />
                            Inactivo (Suspendido)
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Acciones para Convivientes (No aplicables al Titular) */}
                  {!isTitular && (
                    <div className="pt-3 mt-3 border-t border-border/50 flex items-center justify-end gap-1.5 flex-wrap">
                      {isActivo ? (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => promptSuspend(h)}
                          className="h-7 px-2.5 text-xs text-muted-foreground hover:text-foreground gap-1"
                          title="Suspender temporalmente para liberar cupo"
                          aria-label={`Suspender a ${hNombre} para liberar cupo`}
                        >
                          <UserX className="w-3.5 h-3.5 text-amber-500" aria-hidden="true" />
                          <span>Suspender</span>
                        </Button>
                      ) : (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={reached}
                          onClick={() => promptReactivate(h)}
                          className="h-7 px-2.5 text-xs text-emerald-600 dark:text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/10 gap-1 disabled:opacity-50"
                          title={reached ? 'Cupo lleno en la unidad' : 'Reactivar en la unidad'}
                          aria-label={`Reactivar a ${hNombre}`}
                        >
                          <RotateCcw className="w-3.5 h-3.5" aria-hidden="true" />
                          <span>Reactivar</span>
                        </Button>
                      )}

                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => promptUnlink(h)}
                        className="h-7 px-2 text-xs text-muted-foreground hover:bg-muted hover:text-foreground gap-1"
                        title="Desvincular habitante de la unidad (conservar historial)"
                        aria-label={`Desvincular a ${hNombre} de la unidad`}
                      >
                        <UserMinus className="w-3.5 h-3.5" aria-hidden="true" />
                        <span>Desvincular</span>
                      </Button>

                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => promptDeletePermanent(h)}
                        className="h-7 px-2 text-xs text-destructive hover:bg-destructive/10 hover:text-destructive gap-1"
                        title="Eliminar habitante definitivamente"
                        aria-label={`Eliminar definitivamente a ${hNombre}`}
                      >
                        <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                        <span>Eliminar</span>
                      </Button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {/* Historial de habitantes desvinculados */}
        {habitantesDesvinculados.length > 0 && (
          <div className="mt-6 pt-4 border-t border-border/60">
            <button
              type="button"
              onClick={() => setShowHistory(!showHistory)}
              className="flex items-center justify-between w-full text-xs font-semibold text-muted-foreground hover:text-foreground py-2 transition-colors cursor-pointer"
            >
              <div className="flex items-center gap-2">
                <RotateCcw className="w-3.5 h-3.5 text-muted-foreground" />
                <span>Historial de Habitantes Desvinculados ({habitantesDesvinculados.length})</span>
              </div>
              <span className="text-[11px] font-mono underline text-primary">
                {showHistory ? 'Ocultar historial' : 'Ver historial'}
              </span>
            </button>

            {showHistory && (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mt-3">
                {habitantesDesvinculados.map((h, idx) => {
                  const hNombre = h.persona
                    ? `${h.persona.primerNombre || ''} ${h.persona.primerApellido || ''}`.trim()
                    : `${h.nombres || ''} ${h.apellidos || ''}`.trim();
                  const hDoc = h.persona?.numeroDocumento || h.numeroDocumento || '—';
                  const hId = getResidentId(h);
                  return (
                    <div
                      key={hId || idx}
                      className="p-3 rounded-lg border border-dashed border-border bg-muted/20 text-xs space-y-2 flex flex-col justify-between"
                    >
                      <div className="space-y-1">
                        <div className="flex items-center justify-between gap-1">
                          <span className="font-semibold text-foreground truncate">{hNombre}</span>
                          <Badge variant="outline" className="text-[10px] text-muted-foreground shrink-0">
                            Desvinculado
                          </Badge>
                        </div>
                        <p className="text-[11px] text-muted-foreground font-mono">Doc: {hDoc}</p>
                        <p className="text-[10px] text-muted-foreground">
                          Fecha desvinculación: {h.fechaFin || 'Previa'}
                        </p>
                      </div>

                      <div className="pt-2 border-t border-border/40 flex items-center justify-end gap-1.5">
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={reached}
                          onClick={() => promptReactivate(h)}
                          className="h-6 px-2 text-[11px] text-emerald-600 dark:text-emerald-400 border-emerald-500/30 hover:bg-emerald-500/10 gap-1 disabled:opacity-50"
                          title={reached ? 'Cupo lleno en la unidad' : 'Reincorporar habitante a la unidad'}
                        >
                          <RotateCcw className="w-3 h-3" />
                          <span>Reactivar</span>
                        </Button>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => promptDeletePermanent(h)}
                          className="h-6 px-2 text-[11px] text-destructive hover:bg-destructive/10 hover:text-destructive gap-1"
                          title="Eliminar habitante definitivamente del historial y la base de datos"
                        >
                          <Trash2 className="w-3 h-3" />
                          <span>Eliminar</span>
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </CardContent>

      {/* MODAL DE AGREGAR CONVIVIENTE */}
      <Modal
        open={addModalOpen}
        onClose={() => !saving && setAddModalOpen(false)}
        title="Registrar Residente de Convivencia"
        size="md"
        footer={
          <>
            <Button
              type="button"
              variant="outline"
              onClick={() => setAddModalOpen(false)}
              disabled={saving}
            >
              Cancelar
            </Button>
            <Button
              type="submit"
              form="form-conviviente"
              disabled={saving || reached}
              className="gap-2"
            >
              <UserPlus className="w-4 h-4" />
              {saving ? 'Registrando...' : 'Registrar Conviviente'}
            </Button>
          </>
        }
      >
        <form id="form-conviviente" onSubmit={handleSaveConviviente} className="space-y-4 py-2">
          {formError && (
            <div className="p-3 rounded-lg bg-destructive/15 border border-destructive text-destructive text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{formError}</span>
            </div>
          )}

          {reached && (
            <div className="p-3 rounded-lg bg-destructive/10 border border-destructive/20 text-destructive text-xs flex items-start gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>
                <strong>Límite de cupo alcanzado:</strong> Esta unidad ya tiene {limit} convivientes activos. Debes suspender o desvincular a uno para poder agregar otro.
              </span>
            </div>
          )}

          <div className="p-3 rounded-lg bg-primary/5 border border-primary/20 text-xs text-muted-foreground flex items-start gap-2.5">
            <Shield className="w-4 h-4 text-primary shrink-0 mt-0.5" />
            <span>
              Registra a un miembro de tu unidad. Se le creará un usuario de acceso y el sistema le enviará sus credenciales automáticamente por correo electrónico. Ocupará 1 cupo activo.
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              id="conv-primer-nombre"
              label="Primer Nombre"
              required
              disabled={saving || reached}
              placeholder="Ej. María"
              value={form.primerNombre}
              onChange={(e) => {
                setForm((f) => ({ ...f, primerNombre: soloLetras(e.target.value, 50) }));
                if (formErrors.primerNombre) setFormErrors((fe) => ({ ...fe, primerNombre: null }));
              }}
              onBlur={() => touch('primerNombre')}
              error={fieldError('primerNombre', valNombre(form.primerNombre, 'El primer nombre', { required: true })) || formErrors.primerNombre}
            />

            <Input
              id="conv-primer-apellido"
              label="Primer Apellido"
              required
              disabled={saving || reached}
              placeholder="Ej. Gómez"
              value={form.primerApellido}
              onChange={(e) => {
                setForm((f) => ({ ...f, primerApellido: soloLetras(e.target.value, 50) }));
                if (formErrors.primerApellido) setFormErrors((fe) => ({ ...fe, primerApellido: null }));
              }}
              onBlur={() => touch('primerApellido')}
              error={fieldError('primerApellido', valApellido(form.primerApellido, 'El primer apellido', { required: true })) || formErrors.primerApellido}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Select
              id="conv-id-tipo-doc"
              label="Tipo de Documento"
              required
              value={form.idTipoDocumento}
              disabled={saving || reached}
              onChange={(e) => {
                setForm((f) => ({ ...f, idTipoDocumento: Number(e.target.value) }));
              }}
            >
              {(tiposDoc || []).map((t) => (
                <option key={t.idTipoDoc} value={t.idTipoDoc}>
                  {t.codigo} - {t.nombre}
                </option>
              ))}
            </Select>

            <div>
              <Input
                id="conv-num-doc"
                label="Número de Documento"
                required
                disabled={saving || reached}
                placeholder={getDocPlaceholder(activeDocCodigo)}
                value={form.numeroDocumento}
                onChange={(e) => {
                  const isNumeric = ['CC', 'TI', 'RC'].includes(activeDocCodigo);
                  const sanitized = isNumeric ? soloNumeros(e.target.value, 15) : soloAlfanumerico(e.target.value, 20);
                  setForm((f) => ({ ...f, numeroDocumento: sanitized }));
                  if (formErrors.numeroDocumento) setFormErrors((fe) => ({ ...fe, numeroDocumento: null }));
                }}
                onBlur={() => touch('numeroDocumento')}
                error={fieldError('numeroDocumento', valDocumento(form.numeroDocumento, activeDocCodigo, 'El número de documento')) || formErrors.numeroDocumento}
              />
              <p className="text-[11px] text-muted-foreground mt-1">{getDocHint(activeDocCodigo)}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              id="conv-email"
              type="email"
              label="Correo Electrónico"
              required
              disabled={saving || reached}
              placeholder="familiar@ejemplo.com"
              value={form.email}
              onChange={(e) => {
                setForm((f) => ({ ...f, email: e.target.value.trim().toLowerCase() }));
                if (formErrors.email) setFormErrors((fe) => ({ ...fe, email: null }));
              }}
              onBlur={() => touch('email')}
              error={fieldError('email', valEmail(form.email, { required: true })) || formErrors.email}
            />

            <Input
              id="conv-telefono"
              type="tel"
              label="Teléfono Celular (Opcional)"
              disabled={saving || reached}
              placeholder="Ej. 3001234567"
              value={form.telefono}
              onChange={(e) => {
                setForm((f) => ({ ...f, telefono: soloNumeros(e.target.value, 10) }));
                if (formErrors.telefono) setFormErrors((fe) => ({ ...fe, telefono: null }));
              }}
              onBlur={() => touch('telefono')}
              error={fieldError('telefono', valTelefono(form.telefono, { required: false })) || formErrors.telefono}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <Input
              id="conv-usuario"
              label="Usuario de Ingreso"
              required
              disabled={saving || reached}
              placeholder="Ej. mgomez"
              value={form.nombreUsuario}
              onChange={(e) => {
                setForm((f) => ({ ...f, nombreUsuario: soloAlfanumerico(e.target.value.toLowerCase(), 30, false) }));
                if (formErrors.nombreUsuario) setFormErrors((fe) => ({ ...fe, nombreUsuario: null }));
              }}
              onBlur={() => touch('nombreUsuario')}
              error={fieldError('nombreUsuario', valUsername(form.nombreUsuario)) || formErrors.nombreUsuario}
            />

            <Input
              id="conv-password"
              type="password"
              label="Contraseña Temporal (Opcional)"
              disabled={saving || reached}
              placeholder="Autogenerada si se deja vacía"
              value={form.password}
              onChange={(e) => {
                setForm((f) => ({ ...f, password: e.target.value }));
                if (formErrors.password) setFormErrors((fe) => ({ ...fe, password: null }));
              }}
              onBlur={() => touch('password')}
              error={form.password ? fieldError('password', valPassword(form.password, 'La contraseña')) || formErrors.password : undefined}
            />
          </div>
        </form>
      </Modal>

      {/* DIÁLOGO DE CONFIRMACIÓN */}
      <ConfirmDialog
        open={confirmDialog.open}
        onClose={() => setConfirmDialog((prev) => ({ ...prev, open: false }))}
        onConfirm={handleConfirmAction}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmLabel={confirmDialog.confirmLabel}
        danger={confirmDialog.danger}
      />
      {/* MODAL DE CREDENCIALES GENERADAS */}
      <Modal
        open={!!createdCredentials}
        onClose={() => setCreatedCredentials(null)}
        title="Credenciales de Acceso Asignadas"
        size="md"
        footer={
          <Button
            type="button"
            onClick={() => setCreatedCredentials(null)}
            className="w-full sm:w-auto"
          >
            Entendido
          </Button>
        }
      >
        {createdCredentials && (
          <div className="space-y-4 py-2">
            <div className="flex items-start gap-3 p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-700 dark:text-emerald-400 text-xs">
              <CheckCircle2 className="w-5 h-5 shrink-0 mt-0.5 text-emerald-600 dark:text-emerald-400" />
              <div className="space-y-1">
                <p className="font-semibold text-sm text-foreground">
                  ¡Habitante de convivencia registrado exitosamente!
                </p>
                <p className="text-muted-foreground">
                  Se ha despachado un correo electrónico con estas credenciales de acceso a{' '}
                  <strong className="text-foreground">{createdCredentials.email}</strong>.
                </p>
              </div>
            </div>

            <div className="p-4 rounded-xl border border-border bg-muted/30 space-y-3">
              <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                Datos de inicio de sesión para {createdCredentials.nombre}
              </div>

              <div className="space-y-2 text-xs">
                <div className="flex items-center justify-between p-2.5 rounded-lg bg-background border border-border/80">
                  <div>
                    <span className="text-muted-foreground block text-[11px]">Usuario:</span>
                    <span className="font-mono font-bold text-foreground text-sm">{createdCredentials.username}</span>
                  </div>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      navigator.clipboard.writeText(createdCredentials.username);
                      toast.success('Usuario copiado al portapapeles');
                    }}
                    className="h-8 px-2.5 text-xs gap-1"
                  >
                    <Copy className="w-3.5 h-3.5" />
                    Copiar
                  </Button>
                </div>

                <div className="flex items-center justify-between p-2.5 rounded-lg bg-background border border-border/80">
                  <div>
                    <span className="text-muted-foreground block text-[11px]">Contraseña temporal:</span>
                    <span className="font-mono font-bold text-foreground text-sm">{createdCredentials.password}</span>
                  </div>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      navigator.clipboard.writeText(createdCredentials.password);
                      toast.success('Contraseña copiada al portapapeles');
                    }}
                    className="h-8 px-2.5 text-xs gap-1"
                  >
                    <Copy className="w-3.5 h-3.5" />
                    Copiar
                  </Button>
                </div>

                <div className="flex items-center justify-between p-2.5 rounded-lg bg-background border border-border/80">
                  <div>
                    <span className="text-muted-foreground block text-[11px]">Portal de ingreso:</span>
                    <span className="font-mono text-primary text-xs">https://saedfront.vercel.app/login</span>
                  </div>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      navigator.clipboard.writeText('https://saedfront.vercel.app/login');
                      toast.success('Enlace copiado al portapapeles');
                    }}
                    className="h-8 px-2.5 text-xs gap-1"
                  >
                    <Copy className="w-3.5 h-3.5" />
                    Copiar
                  </Button>
                </div>
              </div>
            </div>

            <p className="text-[11px] text-muted-foreground italic text-center">
              El habitante podrá acceder al sistema de inmediato para registrar visitas, generar carnets QR y consultar correspondencia.
            </p>
          </div>
        )}
      </Modal>
    </Card>
  );
}

export default ConvivientesSection;
