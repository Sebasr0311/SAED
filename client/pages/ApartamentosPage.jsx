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

const ESTADOS = ['DISPONIBLE', 'OCUPADO', 'MANTENIMIENTO'];
const emptyForm = { numero: '', piso: 1, tipo: 'NORMAL', area: '', estado: 'DISPONIBLE' };

const ESTADO_BADGE = {
  DISPONIBLE: 'badge-activo',
  OCUPADO: 'badge-ocupado',
  MANTENIMIENTO: 'badge-en-mantenimiento',
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

function EstadoBadge({ estado }) {
  return <span className={`badge ${ESTADO_BADGE[estado] || 'badge-neutral'}`}>{estado}</span>;
}

export default function ApartamentosPage() {
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get(`/apartamentos?page=${page}&size=20`), [page]);

  const columns = [
    { key: 'idApartamento', label: 'ID', width: 60 },
    { key: 'numero', label: 'Número' },
    { key: 'piso', label: 'Piso' },
    { key: 'tipo', label: 'Tipo' },
    { key: 'area', label: 'Área (m²)' },
    {
      key: 'estado',
      label: 'Estado',
      render: (row) => <EstadoBadge estado={row.estado} />,
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
              numero: row.numero,
              piso: row.piso,
              tipo: row.tipo,
              area: row.area,
              estado: row.estado,
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
    if (!form.numero) e.numero = 'Requerido';
    if (!form.piso) e.piso = 'Requerido';
    setErrors(e);
    return Object.keys(e).length === 0;
  }
  async function save() {
    if (!validate()) return;
    try {
      const payload = { ...form, piso: Number(form.piso), area: form.area ? Number(form.area) : null };
      if (editing) {
        await api.put(`/apartamentos/${editing.idApartamento}`, payload);
        setToast({ message: 'Apartamento actualizado', type: 'success' });
      } else {
        await api.post('/apartamentos', payload);
        setToast({ message: 'Apartamento creado', type: 'success' });
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
      await api.del(`/apartamentos/${confirmDel.idApartamento}`);
      setToast({ message: 'Apartamento eliminado', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  return (
    <div>
      <PageHeader
        title="Apartamentos"
        subtitle="Inventario de apartamentos del edificio"
        action={
          <Button
            onClick={() => {
              setEditing(null);
              setForm(emptyForm);
              setErrors({});
              setModalOpen(true);
            }}
          >
            + Nuevo Apartamento
          </Button>
        }
      />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay apartamentos"
        keyField="idApartamento"
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
        title={editing ? 'Editar Apartamento' : 'Nuevo Apartamento'}
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
            id="numero"
            label="Número"
            value={form.numero}
            onChange={(e) => update('numero', e.target.value)}
            error={errors.numero}
          />
          <Input
            id="piso"
            label="Piso"
            type="number"
            value={form.piso}
            onChange={(e) => update('piso', e.target.value)}
            error={errors.piso}
          />
        </div>
        <div className="form-row">
          <Select id="tipo" label="Tipo" value={form.tipo} onChange={(e) => update('tipo', e.target.value)}>
            <option value="NORMAL">Normal</option>
            <option value="PENTHOUSE">Penthouse</option>
            <option value="LOCAL">Local</option>
          </Select>
          <Input
            id="area"
            label="Área (m²)"
            type="number"
            value={form.area}
            onChange={(e) => update('area', e.target.value)}
          />
        </div>
        <div className="form-group">
          <Select
            id="estado"
            label="Estado"
            value={form.estado}
            onChange={(e) => update('estado', e.target.value)}
          >
            {ESTADOS.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </Select>
        </div>
      </Modal>

      <ConfirmDialog
        open={!!confirmDel}
        onClose={() => setConfirmDel(null)}
        onConfirm={handleDelete}
        title="Eliminar apartamento"
        message={`¿Eliminar apartamento #${confirmDel?.numero}?`}
        confirmLabel="Eliminar"
        danger
      />
      <Toast toast={toast} />
    </div>
  );
}
