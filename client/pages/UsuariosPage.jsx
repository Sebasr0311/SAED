import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmDialog } from '../components/ui/ConfirmDialog.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';

const ROLES = ['ADMINISTRADOR', 'PORTERO', 'RESIDENTE'];
const emptyForm = { username: '', password: '', rol: 'RESIDENTE', idResidente: '', activo: true };

export default function UsuariosPage() {
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get(`/usuarios?page=${page}&size=20`), [page]);
  const { data: residentes } = useFetch(() => api.get('/residentes?size=200'), []);

  const columns = [
    { key: 'idUsuario', label: 'ID', width: 60 },
    { key: 'username', label: 'Username' },
    { key: 'rol', label: 'Rol' },
    { key: 'residente', label: 'Residente' },
    {
      key: 'activo',
      label: 'Activo',
      render: (r) => (r.activo ? 'Sí' : 'No'),
    },
    {
      key: 'actions',
      label: 'Acciones',
      width: 140,
      render: (row) => (
        <div className="flex gap-1">
          <button
            onClick={(e) => {
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
            className="rounded-full p-1.5 text-on-surface-variant hover:bg-surface-container"
          >
            <span className="material-symbols-outlined text-lg">edit</span>
          </button>
          <button
            onClick={(e) => {
              e.stopPropagation();
              setConfirmDel(row);
            }}
            className="rounded-full p-1.5 text-on-surface-variant hover:bg-error-container hover:text-error"
          >
            <span className="material-symbols-outlined text-lg">delete</span>
          </button>
        </div>
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
    }
  }

  return (
    <div>
      <PageHeader
        title="Usuarios"
        subtitle="Cuentas de acceso al sistema"
        action={
          <Button
            icon="add"
            onClick={() => {
              setEditing(null);
              setForm(emptyForm);
              setErrors({});
              setModalOpen(true);
            }}
          >
            Nuevo Usuario
          </Button>
        }
      />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay usuarios"
        keyField="idUsuario"
      />
      <Pagination
        page={page}
        totalPages={data?.totalPages || 1}
        totalItems={data?.totalItems}
        pageSize={20}
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
            <Button onClick={save} icon="save">
              Guardar
            </Button>
          </>
        }
      >
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            id="username"
            label="Username"
            value={form.username}
            onChange={(e) => update('username', e.target.value)}
            error={errors.username}
            required
          />
          <Input
            id="password"
            label={editing ? 'Nueva contraseña (opcional)' : 'Contraseña'}
            type="password"
            value={form.password}
            onChange={(e) => update('password', e.target.value)}
            error={errors.password}
            required={!editing}
          />
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
              <option key={r.idResidente} value={r.idResidente}>
                {r.nombres} {r.apellidos}
              </option>
            ))}
          </Select>
          <label className="flex items-center gap-2 sm:col-span-2">
            <input
              type="checkbox"
              checked={form.activo}
              onChange={(e) => update('activo', e.target.checked)}
              className="h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
            />
            <span className="text-sm text-on-surface">Usuario activo</span>
          </label>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        onConfirm={handleDelete}
        title="Eliminar usuario"
        message={`¿Eliminar usuario ${confirmDel?.username}?`}
        confirmLabel="Eliminar"
        danger
      />
      <Toast toast={toast} />
    </div>
  );
}
