import { useState, useMemo, useRef, useCallback } from 'react';
import { toast } from 'sonner';
import {
  Shield,
  UserPlus,
  Pencil,
  Trash2,
  Search,
  RefreshCw,
  Mail,
  Phone,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  KeyRound,
  ShieldAlert,
  UserCheck,
  UserX,
  Lock,
} from 'lucide-react';
import {
  valNombre,
  valApellido,
  valDocumento,
  valTelefono,
  valEmail,
  valUsername,
  valPassword,
  getDocPlaceholder,
} from '../lib/validation.js';
import { useTenant } from '../lib/TenantContext.jsx';
import { useTenantApi } from '../lib/useTenantApi.js';
import { useFetch, useTiposDocumento, useLiveValidation } from '../lib/hooks.js';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageContainer } from '../components/layout/PageContainer.jsx';
import { Card, CardContent } from '../components/ui/card.tsx';
import { Badge } from '../components/ui/badge.tsx';
import { MetricCard } from '../components/ui/MetricCard.jsx';
import { LoadingState } from '../components/ui/LoadingState.jsx';
import { ErrorState } from '../components/ui/ErrorState.jsx';
import EmptyState from '../components/ui/EmptyState.jsx';

const PAGE_SIZE = 12;

const emptyForm = {
  idTipoDoc: 1,
  numeroDocumento: '',
  primerNombre: '',
  segundoNombre: '',
  primerApellido: '',
  segundoApellido: '',
  telefono: '',
  email: '',
  username: '',
  password: '',
  generarPasswordAuto: true,
  cargo: 'PORTERO',
  activo: true,
};

/**
 * EmpleadosPage — Gestión de Empleados y Porteros de la Copropiedad.
 * Estándar SAED 2.0: Sobriedad institucional, alta densidad de datos y
 * control estricto de accesos y credenciales.
 */
export default function EmpleadosPage() {
  const tenant = useTenant();
  const tenantApi = useTenantApi();

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [filterEstado, setFilterEstado] = useState('TODOS');
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [confirmDel, setConfirmDel] = useState(null);
  const [deleting, setDeleting] = useState(false);
  const [statusToggling, setStatusToggling] = useState(null);

  const { tiposDoc } = useTiposDocumento();
  const { touch, touchAll, resetTouched, fieldError } = useLiveValidation();

  // Consulta de usuarios en el perímetro multi-tenant activo
  const {
    data: rawUsers,
    loading,
    error,
    refetch,
  } = useFetch(() => tenantApi.get('/usuarios'), [tenant.activeAssignmentId]);

  // Filtrar exclusivamente empleados del conjunto (por el momento PORTERO)
  const empleados = useMemo(() => {
    const list = Array.isArray(rawUsers) ? rawUsers : rawUsers?.items || rawUsers?.data || [];
    return list
      .map((u) => {
        const idUsuario = u.ID_USUARIO ?? u.idUsuario;
        const username = u.NOMBRE_USUARIO ?? u.username ?? '';
        const email = u.EMAIL ?? u.email ?? '';
        const rol = (u.ROL ?? u.rol ?? '').toUpperCase();
        const rolNombre = u.ROL_NOMBRE ?? u.rolNombre ?? 'Portero';
        const idPersona = u.ID_PERSONA ?? u.idPersona;
        const numeroDoc = u.NUMERO_DOCUMENTO ?? u.numeroDocumento ?? '';
        const telefono = u.TELEFONO ?? u.telefono ?? '';
        const nombreCompleto = (u.NOMBRE_COMPLETO ?? u.nombreCompleto ?? username).trim();
        const rawEstado = (u.ESTADO ?? u.estado ?? 'ACTIVO').toUpperCase();
        const activo = rawEstado === 'ACTIVO' || rawEstado === 'ACTIVA';

        return {
          idUsuario,
          idPersona,
          username,
          email,
          rol,
          rolNombre,
          numeroDocumento: numeroDoc,
          telefono,
          nombreCompleto,
          activo,
          estado: rawEstado,
        };
      })
      .filter((u) => u.rol === 'PORTERO');
  }, [rawUsers]);

  // Métricas
  const metrics = useMemo(() => {
    const total = empleados.length;
    const activos = empleados.filter((e) => e.activo).length;
    const suspendidos = empleados.filter((e) => !e.activo).length;
    return { total, activos, suspendidos };
  }, [empleados]);

  // Filtrado y búsqueda
  const filteredEmpleados = useMemo(() => {
    return empleados.filter((emp) => {
      if (filterEstado === 'ACTIVOS' && !emp.activo) return false;
      if (filterEstado === 'SUSPENDIDOS' && emp.activo) return false;

      if (!search.trim()) return true;
      const term = search.toLowerCase().trim();
      return (
        emp.nombreCompleto.toLowerCase().includes(term) ||
        emp.numeroDocumento.toLowerCase().includes(term) ||
        emp.username.toLowerCase().includes(term) ||
        emp.email.toLowerCase().includes(term) ||
        emp.telefono.includes(term)
      );
    });
  }, [empleados, filterEstado, search]);

  const totalPages = Math.max(1, Math.ceil(filteredEmpleados.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = filteredEmpleados.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  // Apertura de modal de creación
  const openCreate = useCallback(() => {
    setEditing(null);
    const ccId = tiposDoc.find((t) => t.codigo === 'CC')?.idTipoDoc || 1;
    setForm({
      ...emptyForm,
      idTipoDoc: ccId,
    });
    setErrors({});
    resetTouched();
    setModalOpen(true);
  }, [tiposDoc, resetTouched]);

  // Apertura de modal de edición
  const openEdit = useCallback(
    (row) => {
      setEditing(row);
      const nombresSplit = row.nombreCompleto.split(' ');
      setForm({
        idTipoDoc: 1,
        numeroDocumento: row.numeroDocumento,
        primerNombre: nombresSplit[0] || '',
        segundoNombre: nombresSplit.length > 2 ? nombresSplit.slice(1, -1).join(' ') : '',
        primerApellido: nombresSplit[nombresSplit.length - 1] || '',
        segundoApellido: '',
        telefono: row.telefono,
        email: row.email,
        username: row.username,
        password: '',
        generarPasswordAuto: false,
        cargo: 'PORTERO',
        activo: row.activo,
      });
      setErrors({});
      resetTouched();
      setModalOpen(true);
    },
    [resetTouched]
  );

  const update = useCallback((k, v) => {
    setForm((f) => {
      const next = { ...f, [k]: v };
      // Auto-sugerir username cuando se ingresa nombre y documento
      if (!editing && (k === 'primerNombre' || k === 'primerApellido' || k === 'numeroDocumento')) {
        const nom = (k === 'primerNombre' ? v : next.primerNombre).toLowerCase().trim();
        const ape = (k === 'primerApellido' ? v : next.primerApellido).toLowerCase().trim();
        const doc = (k === 'numeroDocumento' ? v : next.numeroDocumento).trim();
        if (nom && ape) {
          next.username = `${nom.charAt(0)}${ape}${doc.slice(-3)}`.replace(/[^a-z0-9]/g, '');
        }
      }
      return next;
    });
  }, [editing]);

  // Validación
  const validate = useCallback(() => {
    touchAll(['numeroDocumento', 'primerNombre', 'primerApellido', 'email', 'username']);
    const e = {};

    const rNom = valNombre(form.primerNombre, 'El primer nombre');
    if (!rNom.ok) e.primerNombre = rNom.mensaje;

    const rApe = valApellido(form.primerApellido, 'El primer apellido');
    if (!rApe.ok) e.primerApellido = rApe.mensaje;

    const rDoc = valDocumento(form.numeroDocumento, 'CC', 'El número de documento');
    if (!rDoc.ok) e.numeroDocumento = rDoc.mensaje;

    if (form.telefono) {
      const rTel = valTelefono(form.telefono, { required: false });
      if (!rTel.ok) e.telefono = rTel.mensaje;
    }

    const rEm = valEmail(form.email, { required: true });
    if (!rEm.ok) e.email = 'El correo es obligatorio para enviar las credenciales de acceso a portería';

    const rUsr = valUsername(form.username);
    if (!rUsr.ok) e.username = rUsr.mensaje;

    if (!editing && !form.generarPasswordAuto) {
      const rPwd = valPassword(form.password);
      if (!rPwd.ok) e.password = rPwd.mensaje;
    } else if (editing && form.password && form.password.trim()) {
      const rPwd = valPassword(form.password);
      if (!rPwd.ok) e.password = rPwd.mensaje;
    }

    setErrors(e);
    return Object.keys(e).length === 0;
  }, [form, editing, touchAll]);

  // Guardar (Crear o Editar)
  const save = useCallback(async () => {
    if (!validate()) return;
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);

    try {
      if (editing) {
        // Actualizar usuario
        const payload = {
          rol: 'PORTERO',
          activo: form.activo,
        };
        if (form.password && form.password.trim()) {
          payload.password = form.password.trim();
        }
        await tenantApi.put(`/usuarios/${editing.idUsuario}`, payload);

        // Si cambió contacto, actualizar la persona vinculada
        if (editing.idPersona) {
          await tenantApi.put(`/personas/${editing.idPersona}`, {
            tipoDocumentoId: Number(form.idTipoDoc || 1),
            numeroDocumento: form.numeroDocumento,
            tipoPersona: 'NATURAL',
            primerNombre: form.primerNombre.trim(),
            segundoNombre: form.segundoNombre?.trim() || '',
            primerApellido: form.primerApellido.trim(),
            segundoApellido: form.segundoApellido?.trim() || '',
            email: form.email.trim(),
            telefono: form.telefono?.trim() || '',
          });
        }
        toast.success('Empleado y credenciales actualizados con éxito');
      } else {
        // Crear nuevo empleado (PORTERO)
        const payload = {
          username: form.username.trim().toLowerCase(),
          password: form.generarPasswordAuto ? undefined : form.password.trim(),
          enviarCorreoActivacion: true,
          rol: 'PORTERO',
          activo: form.activo,
          tipoDocumentoId: Number(form.idTipoDoc || 1),
          numeroDocumento: form.numeroDocumento.trim(),
          primerNombre: form.primerNombre.trim(),
          segundoNombre: form.segundoNombre?.trim() || '',
          primerApellido: form.primerApellido.trim(),
          segundoApellido: form.segundoApellido?.trim() || '',
          email: form.email.trim().toLowerCase(),
          telefono: form.telefono?.trim() || '',
        };

        const res = await tenantApi.post('/usuarios', payload);
        const passMsg = res?.data?.passwordGenerada || res?.passwordGenerada;
        if (passMsg) {
          toast.success(
            `Portero creado con éxito. Clave temporal asignada: ${passMsg}. Se enviaron las credenciales a ${form.email}.`,
            { duration: 8000 }
          );
        } else {
          toast.success(`Portero creado con éxito. Se enviaron las credenciales de acceso a ${form.email}.`);
        }
      }

      setModalOpen(false);
      setEditing(null);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al procesar el empleado');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }, [editing, form, refetch, tenantApi, validate]);

  // Alternar estado activo / suspendido de forma inmediata
  const toggleStatus = useCallback(
    async (emp) => {
      setStatusToggling(emp.idUsuario);
      try {
        const nuevoEstado = !emp.activo;
        await tenantApi.put(`/usuarios/${emp.idUsuario}`, {
          activo: nuevoEstado,
          estado: nuevoEstado ? 'ACTIVO' : 'INACTIVO',
          rol: 'PORTERO',
        });
        toast.success(
          nuevoEstado
            ? `Cuenta de ${emp.nombreCompleto} reactivada correctamente`
            : `Cuenta de ${emp.nombreCompleto} suspendida (acceso revocado)`
        );
        refetch();
      } catch (err) {
        toast.error(err.message || 'No se pudo cambiar el estado de la cuenta');
      } finally {
        setStatusToggling(null);
      }
    },
    [refetch, tenantApi]
  );

  // Eliminar empleado
  const handleDelete = useCallback(async () => {
    if (!confirmDel) return;
    setDeleting(true);
    try {
      await tenantApi.del(`/usuarios/${confirmDel.idUsuario}`);
      toast.success(`Empleado ${confirmDel.nombreCompleto} desvinculado y accesos revocados.`);
      setConfirmDel(null);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al desvincular el empleado');
    } finally {
      setDeleting(false);
    }
  }, [confirmDel, refetch, tenantApi]);

  const activeCodigoDoc = useMemo(() => {
    return tiposDoc.find((t) => Number(t.idTipoDoc) === Number(form.idTipoDoc))?.codigo || 'CC';
  }, [tiposDoc, form.idTipoDoc]);

  return (
    <PageContainer>
      {/* 1. Encabezado */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between pb-2 border-b border-border/60">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              Personal Operativo & Control de Acceso
            </span>
            <span className="text-muted-foreground/40">•</span>
            <span className="text-xs font-medium text-primary">
              {tenant.activePropertyName || 'Propiedad Activa'}
            </span>
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl mt-0.5">
            Empleados de Portería
          </h1>
          <p className="text-xs sm:text-sm text-muted-foreground mt-0.5">
            Registro, control de credenciales y administración de turnos para el personal de vigilancia y acceso.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={loading}
            className="text-xs min-h-[40px] sm:min-h-9"
          >
            <RefreshCw className={`h-3.5 w-3.5 mr-1.5 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
            Actualizar
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={openCreate}
            className="text-xs min-h-[40px] sm:min-h-9 shadow-xs"
          >
            <UserPlus className="h-3.5 w-3.5 mr-1.5" aria-hidden="true" />
            Nuevo Empleado
          </Button>
        </div>
      </div>

      {/* 2. KPIs de Personal */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <MetricCard
          label="Total Empleados"
          value={metrics.total}
          subtitle="Personal de vigilancia asignado"
          icon={Shield}
        />
        <MetricCard
          label="Cuentas Activas"
          value={metrics.activos}
          subtitle="Con acceso habilitado al módulo"
          icon={UserCheck}
        />
        <MetricCard
          label="Cuentas Suspendidas"
          value={metrics.suspendidos}
          subtitle="Accesos bloqueados temporalmente"
          icon={UserX}
        />
      </div>

      {/* 3. Barra de Búsqueda y Filtros */}
      <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" aria-hidden="true" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por nombre, documento, usuario o teléfono..."
            className="w-full pl-9 pr-4 py-2 text-xs rounded-lg border border-border bg-card text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20 focus:border-primary transition-colors"
          />
        </div>

        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground font-medium whitespace-nowrap">Filtrar por:</span>
          <select
            value={filterEstado}
            onChange={(e) => setFilterEstado(e.target.value)}
            className="text-xs py-2 px-3 rounded-lg border border-border bg-card text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20"
          >
            <option value="TODOS">Todos los estados</option>
            <option value="ACTIVOS">Solo Cuentas Activas</option>
            <option value="SUSPENDIDOS">Solo Cuentas Suspendidas</option>
          </select>
        </div>
      </div>

      {/* 4. Tabla de Empleados */}
      {loading ? (
        <LoadingState message="Cargando personal operativo de la copropiedad..." />
      ) : error ? (
        <ErrorState message="No se pudo cargar la lista de empleados" onRetry={() => refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No hay empleados registrados"
          description={
            search
              ? 'No se encontraron empleados que coincidan con los filtros de búsqueda.'
              : 'Aún no has registrado porteros para esta propiedad. Haz clic en "Nuevo Empleado" para registrar el personal de turno.'
          }
          action={
            !search && (
              <Button variant="primary" size="sm" onClick={openCreate}>
                <UserPlus className="h-4 w-4 mr-1.5" /> Registrar Primer Portero
              </Button>
            )
          }
        />
      ) : (
        <Card className="border border-border/80 shadow-xs overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-muted/40 text-muted-foreground font-medium border-b border-border/60">
                <tr>
                  <th className="py-3 px-4">Empleado</th>
                  <th className="py-3 px-4">Cargo / Función</th>
                  <th className="py-3 px-4">Cuenta de Acceso</th>
                  <th className="py-3 px-4">Teléfono</th>
                  <th className="py-3 px-4 text-center">Estado de Cuenta</th>
                  <th className="py-3 px-4 text-right">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/40">
                {rows.map((emp) => (
                  <tr key={emp.idUsuario} className="hover:bg-muted/20 transition-colors">
                    <td className="py-3 px-4">
                      <div className="font-semibold text-foreground">{emp.nombreCompleto}</div>
                      <div className="text-[11px] text-muted-foreground">Doc: {emp.numeroDocumento || 'Sin registrar'}</div>
                    </td>
                    <td className="py-3 px-4">
                      <Badge variant="outline" className="bg-primary/5 text-primary border-primary/20 text-[10px] font-semibold flex items-center gap-1 w-fit">
                        <Shield className="h-3 w-3" />
                        Portería y Control
                      </Badge>
                    </td>
                    <td className="py-3 px-4">
                      <div className="font-mono text-foreground font-medium">@{emp.username}</div>
                      <div className="text-[11px] text-muted-foreground flex items-center gap-1 mt-0.5">
                        <Mail className="h-3 w-3" />
                        {emp.email}
                      </div>
                    </td>
                    <td className="py-3 px-4">
                      {emp.telefono ? (
                        <div className="flex items-center gap-1 text-muted-foreground">
                          <Phone className="h-3 w-3" />
                          <span>{emp.telefono}</span>
                        </div>
                      ) : (
                        <span className="text-muted-foreground/60 italic">—</span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-center">
                      {emp.activo ? (
                        <Badge className="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20 text-[10px] font-semibold">
                          Cuenta Activa
                        </Badge>
                      ) : (
                        <Badge variant="destructive" className="text-[10px] font-semibold">
                          Cuenta Suspendida
                        </Badge>
                      )}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => toggleStatus(emp)}
                          disabled={statusToggling === emp.idUsuario}
                          className="h-8 px-2 text-xs"
                          title={emp.activo ? 'Suspender acceso' : 'Reactivar acceso'}
                        >
                          {statusToggling === emp.idUsuario ? (
                            <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                          ) : emp.activo ? (
                            <UserX className="h-3.5 w-3.5 text-amber-600 dark:text-amber-400" />
                          ) : (
                            <UserCheck className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
                          )}
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => openEdit(emp)}
                          className="h-8 px-2 text-xs"
                          title="Editar empleado y credenciales"
                        >
                          <Pencil className="h-3.5 w-3.5 text-muted-foreground hover:text-foreground" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setConfirmDel(emp)}
                          className="h-8 px-2 text-xs text-destructive hover:bg-destructive/10"
                          title="Desvincular empleado"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* 5. Modal Crear / Editar Empleado */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar Empleado y Credenciales' : 'Registrar Nuevo Portero / Empleado'}
        size="lg"
        footer={
          <>
            <Button
              variant="outline"
              onClick={() => setModalOpen(false)}
              className="text-xs min-h-[40px] sm:min-h-9"
            >
              Cancelar
            </Button>
            <Button
              variant="primary"
              onClick={save}
              disabled={saving}
              className="text-xs min-h-[40px] sm:min-h-9"
            >
              {saving ? 'Guardando...' : editing ? 'Guardar Cambios' : 'Registrar Portero'}
            </Button>
          </>
        }
      >
        <div className="space-y-4 pt-1">
          {/* Cargo */}
          <div className="bg-primary/5 border border-primary/20 p-3 rounded-lg flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <Shield className="h-5 w-5 text-primary shrink-0" />
              <div>
                <strong className="text-xs font-semibold text-foreground block">
                  Cargo Operativo: Portero (Control de Acceso)
                </strong>
                <span className="text-[11px] text-muted-foreground">
                  Personal encargado de registrar visitas, paquetes y vehículos en portería.
                </span>
              </div>
            </div>
            <Badge variant="outline" className="text-[10px] font-bold border-primary/30 text-primary">
              ROL_PORTERO
            </Badge>
          </div>

          {/* Documento */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Select
              id="idTipoDoc"
              label="Tipo de Documento *"
              value={form.idTipoDoc}
              onChange={(e) => update('idTipoDoc', Number(e.target.value))}
            >
              {tiposDoc.map((t) => (
                <option key={t.idTipoDoc ?? t.id ?? t.value} value={t.idTipoDoc ?? t.id ?? t.value}>
                  {t.nombre} ({t.codigo})
                </option>
              ))}
            </Select>
            <Input
              id="numeroDocumento"
              label="Número de Documento *"
              value={form.numeroDocumento}
              onChange={(e) => update('numeroDocumento', e.target.value)}
              onBlur={() => touch('numeroDocumento')}
              error={fieldError('numeroDocumento', valDocumento(form.numeroDocumento, activeCodigoDoc, 'El número de documento')) || errors.numeroDocumento}
              placeholder={getDocPlaceholder(activeCodigoDoc)}
            />
          </div>

          {/* Nombres y Apellidos */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="primerNombre"
              label="Primer Nombre *"
              value={form.primerNombre}
              onChange={(e) => update('primerNombre', e.target.value)}
              onBlur={() => touch('primerNombre')}
              error={fieldError('primerNombre', valNombre(form.primerNombre, 'El primer nombre')) || errors.primerNombre}
              placeholder="Ej. Roberto"
            />
            <Input
              id="segundoNombre"
              label="Segundo Nombre"
              value={form.segundoNombre}
              onChange={(e) => update('segundoNombre', e.target.value)}
              placeholder="Ej. Carlos"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="primerApellido"
              label="Primer Apellido *"
              value={form.primerApellido}
              onChange={(e) => update('primerApellido', e.target.value)}
              onBlur={() => touch('primerApellido')}
              error={fieldError('primerApellido', valApellido(form.primerApellido, 'El primer apellido')) || errors.primerApellido}
              placeholder="Ej. Gómez"
            />
            <Input
              id="segundoApellido"
              label="Segundo Apellido"
              value={form.segundoApellido}
              onChange={(e) => update('segundoApellido', e.target.value)}
              placeholder="Ej. Silva"
            />
          </div>

          {/* Teléfono y Correo */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              id="telefono"
              label="Teléfono Celular"
              type="tel"
              inputMode="numeric"
              maxLength={10}
              value={form.telefono}
              onChange={(e) => update('telefono', e.target.value)}
              onBlur={() => touch('telefono')}
              error={fieldError('telefono', valTelefono(form.telefono, { required: false })) || errors.telefono}
              placeholder="Ej. 3101234567"
            />
            <Input
              id="email"
              label="Correo Electrónico (Para envío de credenciales) *"
              type="email"
              value={form.email}
              onChange={(e) => update('email', e.target.value)}
              onBlur={() => touch('email')}
              error={fieldError('email', valEmail(form.email, { required: true })) || errors.email}
              placeholder="porteria@edificio.com"
            />
          </div>

          {/* Sección de Cuenta de Acceso */}
          <div className="border-t border-border pt-3 space-y-3">
            <div className="flex items-center gap-2">
              <KeyRound className="h-4 w-4 text-primary" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-foreground">
                Credenciales de Acceso al Portal
              </h3>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                id="username"
                label="Nombre de Usuario *"
                value={form.username}
                onChange={(e) => update('username', e.target.value)}
                onBlur={() => touch('username')}
                error={fieldError('username', valUsername(form.username)) || errors.username}
                placeholder="Ej. rgomez101"
              />

              {editing ? (
                <Input
                  id="password"
                  label="Nueva Contraseña (Opcional)"
                  type="password"
                  value={form.password}
                  onChange={(e) => update('password', e.target.value)}
                  placeholder="Dejar en blanco para mantener la actual"
                  error={errors.password}
                />
              ) : form.generarPasswordAuto ? (
                <div className="flex flex-col justify-end">
                  <div className="text-[11px] bg-muted/60 p-2.5 rounded-lg border border-border text-muted-foreground flex items-center gap-2">
                    <Lock className="h-4 w-4 text-primary shrink-0" />
                    <span>Se generará una contraseña segura aleatoria y se enviará por correo.</span>
                  </div>
                </div>
              ) : (
                <Input
                  id="password"
                  label="Contraseña de Acceso *"
                  type="password"
                  value={form.password}
                  onChange={(e) => update('password', e.target.value)}
                  placeholder="Mínimo 8 caracteres"
                  error={errors.password}
                />
              )}
            </div>

            {!editing && (
              <label className="flex items-center gap-2 text-xs text-foreground cursor-pointer pt-1">
                <input
                  type="checkbox"
                  checked={form.generarPasswordAuto}
                  onChange={(e) => update('generarPasswordAuto', e.target.checked)}
                  className="rounded-sm border-border text-primary focus:ring-primary/20"
                />
                <span>Generar contraseña temporal segura automáticamente y enviar por correo</span>
              </label>
            )}

            <div className="flex items-center justify-between pt-1 border-t border-border/40">
              <span className="text-xs font-medium text-foreground">Estado de la cuenta:</span>
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={form.activo}
                  onChange={(e) => update('activo', e.target.checked)}
                  className="rounded-sm border-border text-primary focus:ring-primary/20"
                />
                <span className={`text-xs font-semibold ${form.activo ? 'text-emerald-600' : 'text-destructive'}`}>
                  {form.activo ? 'Habilitada (Activa)' : 'Suspendida (Inactiva)'}
                </span>
              </label>
            </div>
          </div>
        </div>
      </Modal>

      {/* 6. Modal Confirmación de Eliminación */}
      <Modal
        open={confirmDel !== null}
        onClose={() => setConfirmDel(null)}
        title="Desvincular Empleado de Portería"
        size="sm"
        footer={
          <>
            <Button
              variant="outline"
              onClick={() => setConfirmDel(null)}
              disabled={deleting}
              className="text-xs min-h-[40px] sm:min-h-9"
            >
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              disabled={deleting}
              className="text-xs min-h-[40px] sm:min-h-9"
            >
              {deleting ? 'Desvinculando...' : 'Sí, Desvincular'}
            </Button>
          </>
        }
      >
        <div className="space-y-3 py-1">
          <div className="flex items-start gap-3 p-3 bg-destructive/10 border border-destructive/20 rounded-lg text-xs text-destructive-foreground">
            <AlertTriangle className="h-5 w-5 text-destructive shrink-0 mt-0.5" />
            <div>
              <strong className="font-semibold block mb-0.5">Revocación Inmediata de Acceso:</strong>
              Al desvincular a <strong>{confirmDel?.nombreCompleto}</strong> (@{confirmDel?.username}), se revocarán de inmediato todos sus accesos al portal de vigilancia y portería de esta propiedad.
            </div>
          </div>
          <p className="text-xs text-muted-foreground">
            ¿Deseas proceder con la desvinculación definitiva?
          </p>
        </div>
      </Modal>
    </PageContainer>
  );
}
