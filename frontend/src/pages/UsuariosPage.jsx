import { useState, useRef, useMemo } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { valUsername, valPassword, valNombre, valApellido, valDocumento, valTelefono, valEmail } from '../lib/validation.js';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import { ActionButtons } from '../components/ui/ActionButtons.jsx';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';

const ROLES = ['PORTERO', 'RESIDENTE', 'ADMIN_PROPIEDAD'];
const PAGE_SIZE = 12;

const emptyForm = {
  username: '',
  password: '',
  rol: 'PORTERO',
  modoPersona: 'nueva', // 'nueva' | 'existente'
  idResidente: '',
  primerNombre: '',
  primerApellido: '',
  numeroDocumento: '',
  telefono: '',
  email: '',
  activo: true,
};

const ROL_BADGE = {
  ADMIN_PROPIEDAD: 'badge-navy',
  ADMINISTRADOR: 'badge-navy',
  PORTERO: 'badge-info',
  RESIDENTE: 'badge-success',
};

export default function UsuariosPage() {
  const [page, setPage] = useState(0);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);
  const [pwdConfirmOpen, setPwdConfirmOpen] = useState(false);
  const [filtroRol, setFiltroRol] = useState('TODOS');
  const [busqueda, setBusqueda] = useState('');
  const { touch, fieldError } = useLiveValidation();

  const { data, loading, refetch } = useFetch(() => api.get('/usuarios'), []);
  const { data: residentes } = useFetch(() => api.get('/personas'), []);

  const items = useMemo(() => {
    return (data?.items || data || []).map((u) => ({
      idUsuario: u.ID_USUARIO ?? u.idUsuario,
      username: u.NOMBRE_USUARIO ?? u.username,
      email: u.EMAIL ?? u.email,
      rol: u.ROL ?? u.rol,
      rolNombre: u.ROL_NOMBRE ?? u.rolNombre,
      nombreResidente: u.NOMBRE_COMPLETO ?? u.nombreResidente,
      numeroDocumento: u.NUMERO_DOCUMENTO ?? u.numeroDocumento,
      telefono: u.TELEFONO ?? u.telefono,
      activo: (u.ESTADO ?? u.estado) === 'ACTIVO',
      idResidente: u.ID_PERSONA ?? u.idResidente,
    }));
  }, [data]);

  const metrics = useMemo(() => {
    const total = items.length;
    const porteros = items.filter((u) => u.rol === 'PORTERO').length;
    const residentesCount = items.filter((u) => u.rol === 'RESIDENTE').length;
    const admins = items.filter((u) => u.rol === 'ADMIN_PROPIEDAD' || u.rol === 'ADMINISTRADOR').length;
    return { total, porteros, residentesCount, admins };
  }, [items]);

  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (filtroRol !== 'TODOS' && item.rol !== filtroRol) {
        return false;
      }
      if (busqueda.trim()) {
        const query = busqueda.toLowerCase().trim();
        const matchUser = (item.username || '').toLowerCase().includes(query);
        const matchEmail = (item.email || '').toLowerCase().includes(query);
        const matchNombre = (item.nombreResidente || '').toLowerCase().includes(query);
        const matchDoc = (item.numeroDocumento || '').toLowerCase().includes(query);
        const matchTel = (item.telefono || '').toLowerCase().includes(query);
        return matchUser || matchEmail || matchNombre || matchDoc || matchTel;
      }
      return true;
    });
  }, [items, filtroRol, busqueda]);

  const totalPages = Math.max(1, Math.ceil(filteredItems.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = filteredItems.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  const columns = [
    { key: 'idUsuario', label: 'ID', width: 60 },
    {
      key: 'username',
      label: 'Usuario',
      render: (r) => (
        <div>
          <div className="font-semibold text-foreground">{r.username}</div>
          {r.email && <div className="text-xs text-muted-foreground">{r.email}</div>}
        </div>
      ),
    },
    {
      key: 'rol',
      label: 'Rol',
      render: (r) => (
        <span className={`badge ${ROL_BADGE[r.rol] || 'badge-neutral'}`}>
          {r.rol === 'PORTERO' ? '🛡️ PORTERO' : r.rol}
        </span>
      ),
    },
    {
      key: 'nombreResidente',
      label: 'Persona Vinculada',
      render: (r) => (
        <div>
          <div className="text-sm font-medium text-foreground">
            {r.nombreResidente || <span className="text-muted-foreground italic">Sin vincular</span>}
          </div>
          {r.numeroDocumento && (
            <div className="text-xs text-muted-foreground">Doc: {r.numeroDocumento}</div>
          )}
        </div>
      ),
    },
    {
      key: 'telefono',
      label: 'Contacto',
      render: (r) => (
        <span className="text-sm text-gray-600">
          {r.telefono ? `📞 ${r.telefono}` : '—'}
        </span>
      ),
    },
    {
      key: 'activo',
      label: 'Estado',
      render: (r) => (
        <span className={`badge ${r.activo ? 'badge-activo' : 'badge-neutral'}`}>
          {r.activo ? 'Activo' : 'Inactivo'}
        </span>
      ),
    },
    {
      key: 'actions',
      label: 'Acciones',
      width: 100,
      render: (row) => (
        <ActionButtons
          onEdit={(e) => {
            e.stopPropagation();
            setEditing(row);
            setForm({
              username: row.username,
              password: '',
              rol: row.rol,
              modoPersona: row.idResidente ? 'existente' : 'nueva',
              idResidente: row.idResidente || '',
              primerNombre: '',
              primerApellido: '',
              numeroDocumento: '',
              telefono: '',
              email: row.email || '',
              activo: row.activo,
            });
            setErrors({});
            setModalOpen(true);
          }}
          onDelete={(e) => {
            e.stopPropagation();
            setConfirmDel(row);
          }}
        />
      ),
    },
  ];

  function update(k, v) {
    setErrors((prev) => (prev[k] ? { ...prev, [k]: undefined } : prev));
    setForm((f) => ({ ...f, [k]: v }));
  }

  function validate() {
    const e = {};
    const u = valUsername(form.username);
    if (!u.ok) e.username = u.mensaje;

    if (!editing) {
      const p = valPassword(form.password);
      if (!p.ok) e.password = p.mensaje;

      if (form.modoPersona === 'nueva') {
        const nom = valNombre(form.primerNombre, 'El primer nombre');
        if (!nom.ok) e.primerNombre = nom.mensaje;

        const ape = valApellido(form.primerApellido, 'El primer apellido');
        if (!ape.ok) e.primerApellido = ape.mensaje;

        const doc = valDocumento(form.numeroDocumento, 'CC', 'El número de documento');
        if (!doc.ok) e.numeroDocumento = doc.mensaje;

        if (form.telefono) {
          const tel = valTelefono(form.telefono, { required: false });
          if (!tel.ok) e.telefono = tel.mensaje;
        }

        if (form.email) {
          const em = valEmail(form.email, { required: false });
          if (!em.ok) e.email = em.mensaje;
        }
      } else if (form.modoPersona === 'existente' && form.rol === 'RESIDENTE' && !form.idResidente) {
        e.idResidente = 'Seleccione la persona existente para el residente';
      }
    }

    if (!form.rol) e.rol = 'Seleccione el rol';

    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function save() {
    if (!validate()) return;
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      if (editing) {
        const payload = {
          rol: form.rol,
          activo: form.activo,
        };
        if (form.password && form.password.trim()) {
          payload.password = form.password.trim();
        }
        await api.put(`/usuarios/${editing.idUsuario}`, payload);
        toast.success('Usuario actualizado');
      } else {
        const payload = {
          username: form.username.trim(),
          password: form.password.trim(),
          rol: form.rol,
          activo: form.activo,
        };

        if (form.modoPersona === 'existente' && form.idResidente) {
          payload.idPersona = Number(form.idResidente);
        } else if (form.modoPersona === 'nueva') {
          payload.primerNombre = form.primerNombre.trim();
          payload.primerApellido = form.primerApellido.trim();
          payload.numeroDocumento = form.numeroDocumento.trim();
          payload.telefono = form.telefono.trim();
          payload.email = form.email.trim() || `${form.username.trim()}@saed.com`;
        }

        await api.post('/usuarios', payload);
        toast.success(
          form.rol === 'PORTERO'
            ? 'Portero creado y asignado exitosamente'
            : 'Usuario creado exitosamente'
        );
      }
      setModalOpen(false);
      setEditing(null);
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al guardar el usuario');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!confirmDel) return;
    try {
      await api.del(`/usuarios/${confirmDel.idUsuario}`);
      toast.success('Usuario eliminado');
      refetch();
    } catch (err) {
      toast.error(err.message || 'Error al eliminar usuario');
    } finally {
      setConfirmDel(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Usuarios y Accesos"
        subtitle="Control de cuentas, credenciales y asignación de porteros y residentes"
        action={
          <Button
            onClick={() => {
              setEditing(null);
              setForm(emptyForm);
              setErrors({});
              setModalOpen(true);
            }}
          >
            + Nuevo Usuario
          </Button>
        }
      />

      {/* Tarjetas métricas */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
        <div className="bg-card border border-border rounded-xl p-4 shadow-sm">
          <div className="text-xs text-muted-foreground font-medium">Total Usuarios</div>
          <div className="text-2xl font-bold text-foreground mt-1">{metrics.total}</div>
        </div>
        <div className="bg-blue-50/50 dark:bg-blue-950/20 border border-blue-100 dark:border-blue-900/40 rounded-xl p-4 shadow-sm">
          <div className="text-xs text-blue-700 dark:text-blue-300 font-medium">Porteros</div>
          <div className="text-2xl font-bold text-blue-900 dark:text-blue-100 mt-1">{metrics.porteros}</div>
        </div>
        <div className="bg-emerald-50/50 dark:bg-emerald-950/20 border border-emerald-100 dark:border-emerald-900/40 rounded-xl p-4 shadow-sm">
          <div className="text-xs text-emerald-700 dark:text-emerald-300 font-medium">Residentes</div>
          <div className="text-2xl font-bold text-emerald-900 dark:text-emerald-100 mt-1">{metrics.residentesCount}</div>
        </div>
        <div className="bg-indigo-50/50 dark:bg-indigo-950/20 border border-indigo-100 dark:border-indigo-900/40 rounded-xl p-4 shadow-sm">
          <div className="text-xs text-indigo-700 dark:text-indigo-300 font-medium">Administradores</div>
          <div className="text-2xl font-bold text-indigo-900 dark:text-indigo-100 mt-1">{metrics.admins}</div>
        </div>
      </div>

      {/* Barra de Filtros y Búsqueda */}
      <div className="flex flex-col sm:flex-row gap-3 items-center justify-between mb-4">
        <div className="flex gap-2 items-center flex-wrap">
          {['TODOS', 'PORTERO', 'RESIDENTE', 'ADMIN_PROPIEDAD'].map((rol) => (
            <button
              key={rol}
              type="button"
              onClick={() => {
                setFiltroRol(rol);
                setPage(0);
              }}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                filtroRol === rol
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'bg-card border border-border text-foreground hover:bg-muted'
              }`}
            >
              {rol === 'TODOS'
                ? 'Todos'
                : rol === 'PORTERO'
                ? 'Porteros'
                : rol === 'RESIDENTE'
                ? 'Residentes'
                : 'Administradores'}
            </button>
          ))}
        </div>

        <div className="w-full sm:w-72">
          <input
            type="text"
            placeholder="Buscar por usuario, nombre, cédula..."
            value={busqueda}
            onChange={(e) => {
              setBusqueda(e.target.value);
              setPage(0);
            }}
            className="w-full border border-border rounded-lg px-3 py-1.5 text-sm bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </div>
      </div>

      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        empty={{
          icon: 'manage_accounts',
          title: 'No hay usuarios registrados',
          subtitle: 'Crea el primer usuario con el botón "Nuevo Usuario".',
        }}
        keyField="idUsuario"
      />

      <Pagination
        page={safePage}
        totalPages={totalPages}
        totalItems={filteredItems.length}
        pageSize={PAGE_SIZE}
        onPageChange={setPage}
      />

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? `Editar Usuario: ${editing.username}` : 'Nuevo Usuario'}
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={saving}>
              {saving ? 'Guardando...' : 'Guardar'}
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {/* Selección de Rol */}
          <div className="form-group">
            <Select
              id="rol"
              label="Rol del Usuario"
              value={form.rol}
              onChange={(e) => update('rol', e.target.value)}
              disabled={!!editing}
              error={errors.rol}
            >
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {r === 'PORTERO' ? 'Portero (Seguridad / Vigilancia)' : r === 'RESIDENTE' ? 'Residente' : 'Administrador de Propiedad'}
                </option>
              ))}
            </Select>
          </div>

          {!editing && (
            <div className="border border-blue-100 dark:border-blue-900/40 bg-blue-50/50 dark:bg-blue-950/20 p-3 rounded-lg space-y-3">
              <div className="text-xs font-semibold text-blue-900 dark:text-blue-200 uppercase tracking-wider">
                Datos de la Persona Asignada
              </div>
              <div className="flex gap-4 text-sm">
                <label className="flex items-center gap-1.5 cursor-pointer text-foreground">
                  <input
                    type="radio"
                    name="modoPersona"
                    value="nueva"
                    checked={form.modoPersona === 'nueva'}
                    onChange={() => update('modoPersona', 'nueva')}
                  />
                  <span>
                    {form.rol === 'PORTERO' ? 'Nuevo Portero (Datos Personales)' : 'Nueva Persona'}
                  </span>
                </label>
                <label className="flex items-center gap-1.5 cursor-pointer text-foreground">
                  <input
                    type="radio"
                    name="modoPersona"
                    value="existente"
                    checked={form.modoPersona === 'existente'}
                    onChange={() => update('modoPersona', 'existente')}
                  />
                  <span>Seleccionar del Directorio</span>
                </label>
              </div>

              {form.modoPersona === 'nueva' ? (
                <div className="space-y-3 pt-2">
                  <div className="form-row">
                    <Input
                      id="primerNombre"
                      label="Primer Nombre *"
                      value={form.primerNombre}
                      onChange={(e) => update('primerNombre', e.target.value)}
                      placeholder="Ej. Carlos"
                      error={errors.primerNombre}
                    />
                    <Input
                      id="primerApellido"
                      label="Primer Apellido *"
                      value={form.primerApellido}
                      onChange={(e) => update('primerApellido', e.target.value)}
                      placeholder="Ej. Rodríguez"
                      error={errors.primerApellido}
                    />
                  </div>
                  <div className="form-row">
                    <Input
                      id="numeroDocumento"
                      label="Número de Documento (Cédula) *"
                      value={form.numeroDocumento}
                      onChange={(e) => update('numeroDocumento', e.target.value)}
                      placeholder="Ej. 1023456789"
                      error={errors.numeroDocumento}
                    />
                    <Input
                      id="telefono"
                      label="Teléfono / Celular"
                      value={form.telefono}
                      onChange={(e) => update('telefono', e.target.value)}
                      placeholder="Ej. 3001234567"
                      error={errors.telefono}
                    />
                  </div>
                  <div className="form-group">
                    <Input
                      id="email"
                      label="Correo Electrónico (opcional)"
                      type="email"
                      value={form.email}
                      onChange={(e) => update('email', e.target.value)}
                      placeholder="Ej. porteria@edificio.com"
                      error={errors.email}
                    />
                  </div>
                </div>
              ) : (
                <div className="pt-2">
                  <Select
                    id="idResidente"
                    label="Persona Registrada *"
                    value={form.idResidente}
                    onChange={(e) => update('idResidente', e.target.value)}
                    error={errors.idResidente}
                  >
                    <option value="">— Seleccionar Persona —</option>
                    {(residentes?.items || residentes || []).map((r) => (
                      <option key={r.id || r.idPersona} value={r.id || r.idPersona}>
                        {r.primerNombre || r.nombres} {r.primerApellido || r.apellidos} ({r.numeroDocumento || 'Sin doc'})
                      </option>
                    ))}
                  </Select>
                </div>
              )}
            </div>
          )}

          {/* Credenciales de Acceso */}
          <div className="space-y-3">
            <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
              Credenciales de Acceso al Sistema
            </div>
            <div className="form-row">
              <Input
                id="username"
                label="Nombre de Usuario *"
                value={form.username}
                onChange={(e) => update('username', e.target.value)}
                onBlur={() => touch('username')}
                disabled={!!editing}
                placeholder="Ej. portero_turno_1"
                error={fieldError('username', valUsername(form.username)) || errors.username}
              />
              <Input
                id="password"
                label={editing ? 'Nueva contraseña (opcional)' : 'Contraseña Inicial *'}
                type="password"
                value={form.password}
                onChange={(e) => update('password', e.target.value)}
                onBlur={() => touch('password')}
                placeholder={editing ? 'Dejar en blanco para conservar' : 'Mínimo 6 caracteres'}
                error={
                  (editing && !form.password
                    ? undefined
                    : fieldError('password', valPassword(form.password))) || errors.password
                }
              />
            </div>
          </div>

          <div className="form-group pt-2">
            <label className="checkbox-label flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={form.activo}
                onChange={(e) => update('activo', e.target.checked)}
              />
              <span className="text-sm font-medium text-foreground">Usuario activo en el sistema</span>
            </label>
          </div>
        </div>
      </Modal>

      <Modal
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        title="Eliminar usuario"
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmDel(null)}>
              Cancelar
            </Button>
            <Button variant="danger" onClick={() => setPwdConfirmOpen(true)}>
              Eliminar
            </Button>
          </>
        }
      >
        <p className="text-sm text-gray-600">
          ¿Está seguro de desactivar al usuario <strong>{confirmDel?.username}</strong>?
          Perderá acceso inmediato a las funciones de su rol ({confirmDel?.rol}).
        </p>
      </Modal>

      <ConfirmPasswordDialog
        open={pwdConfirmOpen}
        onClose={() => setPwdConfirmOpen(false)}
        onConfirmed={() => {
          setPwdConfirmOpen(false);
          handleDelete();
        }}
        descripcion={`eliminar al usuario ${confirmDel?.username}`}
      />
    </div>
  );
}
