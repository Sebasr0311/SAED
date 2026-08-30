import { useState, useRef } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { valUsername, valPassword } from '../lib/validation.js';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import { ActionButtons } from '../components/ui/ActionButtons.jsx';
import { useFetch, useLiveValidation } from '../lib/hooks.js';
import api from '../lib/api.js';

const ROLES = ['ADMINISTRADOR', 'PORTERO', 'RESIDENTE'];
const PAGE_SIZE = 15;
const emptyForm = { username: '', password: '', rol: 'RESIDENTE', idResidente: '', activo: true };

const ROL_BADGE = {
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
  const { touch, fieldError } = useLiveValidation();

  const { data, loading, refetch } = useFetch(() => api.get('/usuarios'), []);
  const { data: residentes } = useFetch(() => api.get('/personas'), []);

  const items = (data?.items || data || []).map((u) => ({
    idUsuario: u.ID_USUARIO ?? u.idUsuario,
    username: u.NOMBRE_USUARIO ?? u.username,
    email: u.EMAIL ?? u.email,
    rol: u.ROL ?? u.rol,
    nombreResidente: u.NOMBRE_COMPLETO ?? u.nombreResidente,
    activo: (u.ESTADO ?? u.estado) === 'ACTIVO',
    idResidente: u.ID_PERSONA ?? u.idResidente,
  }));
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = items.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  const columns = [
    { key: 'idUsuario', label: 'ID', width: 60 },
    { key: 'username', label: 'Username' },
    {
      key: 'rol',
      label: 'Rol',
      render: (r) => <span className={`badge ${ROL_BADGE[r.rol] || 'badge-neutral'}`}>{r.rol}</span>,
    },
    {
      key: 'nombreResidente',
      label: 'Residente',
      render: (r) => r.nombreResidente || '-',
    },
    {
      key: 'activo',
      label: 'Activo',
      render: (r) => <span className={`badge ${r.activo ? 'badge-activo' : 'badge-neutral'}`}>{r.activo ? 'Sí' : 'No'}</span>,
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
              idResidente: row.idResidente || '',
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
    }
    if (form.idResidente && form.rol !== 'RESIDENTE') {
      e.rol = 'Si se asigna un residente, el rol debe ser RESIDENTE';
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
      const payload = { ...form };
      if (!payload.password) delete payload.password;
      else {
        payload.passwordHash = payload.password;
        delete payload.password;
      }
      payload.idResidente = payload.idResidente ? Number(payload.idResidente) : null;
      if (editing) {
        await api.put(`/usuarios/${editing.idUsuario}`, payload);
        toast.success('Usuario actualizado');
      } else {
        await api.post('/usuarios', payload);
        toast.success('Usuario creado');
      }
      setModalOpen(false);
      setEditing(null);
      refetch();
    } catch (err) {
      toast.error(err.message);
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
      toast.error(err.message);
    } finally {
      setConfirmDel(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Usuarios"
        subtitle="Cuentas de acceso al sistema"
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
      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
                empty={{ icon: 'manage_accounts', title: 'No hay usuarios', subtitle: 'Crea el primer usuario con el botón "Nuevo Usuario".' }}
        keyField="idUsuario"
      />
      <Pagination
        page={safePage}
        totalPages={totalPages}
        totalItems={items.length}
        pageSize={PAGE_SIZE}
        onPageChange={setPage}
      />

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? 'Editar Usuario' : 'Nuevo Usuario'}
        footer={
          <>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={saving}>{saving ? 'Guardando...' : 'Guardar'}</Button>
          </>
        }
      >
        <div className="form-row">
          <Input
            id="username"
            label="Username"
            value={form.username}
            onChange={(e) => update('username', e.target.value)}
            onBlur={() => touch('username')}
            error={fieldError('username', valUsername(form.username)) || errors.username}
          />
          <Input
            id="password"
            label={editing ? 'Nueva contraseña (opcional)' : 'Contraseña'}
            type="password"
            value={form.password}
            onChange={(e) => update('password', e.target.value)}
            onBlur={() => touch('password')}
            error={
              (editing && !form.password
                ? undefined
                : fieldError('password', valPassword(form.password))) || errors.password
            }
          />
        </div>
        <div className="form-row">
          <Select id="rol" label="Rol" value={form.rol} onChange={(e) => update('rol', e.target.value)} onBlur={() => touch('rol')} error={fieldError('rol', !form.rol ? { ok: false, mensaje: 'Seleccione el rol' } : form.idResidente && form.rol !== 'RESIDENTE' ? { ok: false, mensaje: 'Si se asigna un residente, el rol debe ser RESIDENTE' } : { ok: true }) || errors.rol}>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </Select>
          <Select
            id="idResidente"
            label="Residente (opcional)"
            value={form.idResidente}
            onChange={(e) => update('idResidente', e.target.value)}
          >
            <option value="">— Ninguno —</option>
            {(residentes?.items || residentes || []).map((r) => (
              <option key={r.id} value={r.id}>
                {r.primerNombre || r.nombres} {r.primerApellido || r.apellidos}
              </option>
            ))}
          </Select>
        </div>
        <div className="form-group">
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={form.activo}
              onChange={(e) => update('activo', e.target.checked)}
            />
            <span>Usuario activo</span>
          </label>
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
        <p>¿Eliminar usuario {confirmDel?.username}?</p>
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
