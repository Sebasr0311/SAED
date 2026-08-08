import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';

const ROLES = ['ADMINISTRADOR', 'PORTERO', 'RESIDENTE'];
const PAGE_SIZE = 15;
const emptyForm = { username: '', password: '', rol: 'RESIDENTE', idResidente: '', activo: true };

const ROL_BADGE = {
  ADMINISTRADOR: 'badge-navy',
  PORTERO: 'badge-info',
  RESIDENTE: 'badge-success',
};

function ActionButtons({ onEdit, onDelete }) {
  return (
    <div style={{ display: 'flex', gap: '4px' }}>
      <button onClick={onEdit} className="btn btn-ghost btn-sm" aria-label="Editar">
        <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>edit</span>
      </button>
      <button onClick={onDelete} className="btn btn-ghost btn-sm" aria-label="Eliminar">
        <span className="material-symbols-outlined" style={{ fontSize: '18px', color: '#e11d48' }}>
          delete
        </span>
      </button>
    </div>
  );
}

export default function UsuariosPage() {
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);
  const [pwdConfirmOpen, setPwdConfirmOpen] = useState(false);

  const { data, loading, refetch } = useFetch(() => api.get('/usuarios'), []);
  const { data: residentes } = useFetch(() => api.get('/residentes'), []);

  const items = data?.items || [];
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
    setForm((f) => ({ ...f, [k]: v }));
  }
  function validate() {
    const e = {};
    if (!form.username.trim()) e.username = 'Requerido';
    if (!editing && !form.password) e.password = 'Requerido';
    setErrors(e);
    return Object.keys(e).length === 0;
  }
  async function save() {
    if (!validate()) return;
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
        setToast({ message: 'Usuario actualizado', type: 'success' });
      } else {
        await api.post('/usuarios', payload);
        setToast({ message: 'Usuario creado', type: 'success' });
      }
      setModalOpen(false);
      setEditing(null);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }
  async function handleDelete() {
    if (!confirmDel) return;
    try {
      await api.del(`/usuarios/${confirmDel.idUsuario}`);
      setToast({ message: 'Usuario eliminado', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
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
        empty="No hay usuarios"
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
            <Button onClick={save}>Guardar</Button>
          </>
        }
      >
        <div className="form-row">
          <Input
            id="username"
            label="Username"
            value={form.username}
            onChange={(e) => update('username', e.target.value)}
            error={errors.username}
          />
          <Input
            id="password"
            label={editing ? 'Nueva contraseña (opcional)' : 'Contraseña'}
            type="password"
            value={form.password}
            onChange={(e) => update('password', e.target.value)}
            error={errors.password}
          />
        </div>
        <div className="form-row">
          <Select id="rol" label="Rol" value={form.rol} onChange={(e) => update('rol', e.target.value)}>
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
            {(residentes?.items || []).map((r) => (
              <option key={r.id} value={r.id}>
                {r.nombres} {r.apellidos}
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
      <Toast toast={toast} />
    </div>
  );
}
