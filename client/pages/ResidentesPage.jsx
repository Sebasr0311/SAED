import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Input, Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { ConfirmPasswordDialog } from '../components/ui/ConfirmPasswordDialog.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch, useTiposDocumento } from '../lib/hooks.js';
import api from '../lib/api.js';

const emptyForm = {
  idTipoDocumento: 1,
  numeroDocumento: '',
  nombres: '',
  apellidos: '',
  fechaNacimiento: '',
  telefono: '',
  email: '',
  idApartamento: '',
  esPropietario: false,
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

export default function ResidentesPage() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);
  const [pwdConfirmOpen, setPwdConfirmOpen] = useState(false);

  const { data, loading, refetch } = useFetch(
    () => api.get(`/residentes?page=${page}&size=20&search=${encodeURIComponent(search)}`),
    [page, search]
  );
  const { tiposDoc } = useTiposDocumento();
  const { data: apartamentos } = useFetch(() => api.get('/apartamentos?size=200'), []);

  const columns = [
    { key: 'idResidente', label: 'ID', width: 60 },
    { key: 'nombres', label: 'Nombres' },
    { key: 'apellidos', label: 'Apellidos' },
    { key: 'numeroDocumento', label: 'Documento' },
    { key: 'telefono', label: 'Teléfono' },
    { key: 'email', label: 'Email' },
    {
      key: 'actions',
      label: 'Acciones',
      width: 100,
      render: (row) => (
        <ActionButtons
          onEdit={(e) => {
            e.stopPropagation();
            openEdit(row);
          }}
          onDelete={(e) => {
            e.stopPropagation();
            setConfirmDel(row);
          }}
        />
      ),
    },
  ];

  function openCreate() {
    setEditing(null);
    setForm(emptyForm);
    setErrors({});
    setModalOpen(true);
  }
  function openEdit(row) {
    setEditing(row);
    setForm({
      idTipoDocumento: row.idTipoDocumento || 1,
      numeroDocumento: row.numeroDocumento || '',
      nombres: row.nombres || '',
      apellidos: row.apellidos || '',
      fechaNacimiento: row.fechaNacimiento || '',
      telefono: row.telefono || '',
      email: row.email || '',
      idApartamento: row.idApartamento || '',
      esPropietario: !!row.esPropietario,
    });
    setErrors({});
    setModalOpen(true);
  }
  function update(k, v) {
    setForm((f) => ({ ...f, [k]: v }));
  }
  function validate() {
    const e = {};
    if (!form.nombres.trim()) e.nombres = 'Requerido';
    if (!form.apellidos.trim()) e.apellidos = 'Requerido';
    if (!form.numeroDocumento.trim()) e.numeroDocumento = 'Requerido';
    if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) e.email = 'Email inválido';
    setErrors(e);
    return Object.keys(e).length === 0;
  }
  async function save() {
    if (!validate()) return;
    try {
      if (editing) {
        await api.put(`/residentes/${editing.idResidente}`, form);
        setToast({ message: 'Residente actualizado', type: 'success' });
      } else {
        await api.post('/residentes', form);
        setToast({ message: 'Residente creado', type: 'success' });
      }
      setModalOpen(false);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }
  async function handleDelete() {
    if (!confirmDel) return;
    try {
      await api.del(`/residentes/${confirmDel.idResidente}`);
      setToast({ message: 'Residente eliminado', type: 'success' });
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
        title="Residentes"
        subtitle="Gestión de residentes del edificio"
        action={
          <>
            <Input
              id="search"
              placeholder="Buscar..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              style={{ width: '200px' }}
            />
            <Button onClick={openCreate}>+ Nuevo Residente</Button>
          </>
        }
      />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay residentes registrados"
        keyField="idResidente"
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
        title={editing ? 'Editar Residente' : 'Nuevo Residente'}
        size="lg"
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
          <Select
            id="idTipoDocumento"
            label="Tipo Documento"
            value={form.idTipoDocumento}
            onChange={(e) => update('idTipoDocumento', Number(e.target.value))}
          >
            {tiposDoc.map((t) => (
              <option key={t.idTipoDocumento ?? t.idTipoDoc ?? t.value} value={t.idTipoDocumento ?? t.idTipoDoc ?? t.value}>
                {t.descripcion || t.nombre}
              </option>
            ))}
          </Select>
          <Input
            id="numeroDocumento"
            label="Número Documento"
            value={form.numeroDocumento}
            onChange={(e) => update('numeroDocumento', e.target.value)}
            error={errors.numeroDocumento}
          />
        </div>
        <div className="form-row">
          <Input
            id="nombres"
            label="Nombres"
            value={form.nombres}
            onChange={(e) => update('nombres', e.target.value)}
            error={errors.nombres}
          />
          <Input
            id="apellidos"
            label="Apellidos"
            value={form.apellidos}
            onChange={(e) => update('apellidos', e.target.value)}
            error={errors.apellidos}
          />
        </div>
        <div className="form-row">
          <Input
            id="fechaNacimiento"
            label="Fecha de Nacimiento"
            type="date"
            value={form.fechaNacimiento}
            onChange={(e) => update('fechaNacimiento', e.target.value)}
          />
          <Input
            id="telefono"
            label="Teléfono"
            value={form.telefono}
            onChange={(e) => update('telefono', e.target.value)}
          />
        </div>
        <div className="form-row">
          <Input
            id="email"
            label="Email"
            type="email"
            value={form.email}
            onChange={(e) => update('email', e.target.value)}
            error={errors.email}
          />
          <Select
            id="idApartamento"
            label="Apartamento"
            value={form.idApartamento}
            onChange={(e) => update('idApartamento', e.target.value)}
          >
            <option value="">— Sin asignar —</option>
            {(apartamentos?.items || []).map((a) => (
              <option key={a.idApartamento} value={a.idApartamento}>
                Apto {a.numero} - Piso {a.piso}
              </option>
            ))}
          </Select>
        </div>
      </Modal>

      <Modal
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        title="Eliminar residente"
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
        <p>
          ¿Eliminar a {confirmDel?.nombres} {confirmDel?.apellidos}?
        </p>
      </Modal>

      <ConfirmPasswordDialog
        open={pwdConfirmOpen}
        onClose={() => setPwdConfirmOpen(false)}
        onConfirmed={() => {
          setPwdConfirmOpen(false);
          handleDelete();
        }}
        descripcion={`eliminar a ${confirmDel?.nombres} ${confirmDel?.apellidos}`}
      />

      <Toast toast={toast} />
    </div>
  );
}
