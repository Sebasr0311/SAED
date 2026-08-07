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
import { formatDate, formatCurrency } from '../lib/utils.js';

const ESTADOS = ['', 'ACTIVO', 'SUSPENDIDO', 'VENCIDO', 'PENDIENTE_FIRMA'];
const TIPOS = ['INICIAL', 'RENOVACION', 'PERMANENCIA'];
const emptyForm = {
  idApartamento: '',
  idResidente: '',
  fechaInicio: '',
  fechaFin: '',
  tipo: 'INICIAL',
  valorMensual: '',
  estado: 'ACTIVO',
};

const ESTADO_BADGE = {
  ACTIVO: 'badge-activo',
  SUSPENDIDO: 'badge-warn',
  VENCIDO: 'badge-danger',
  PENDIENTE_FIRMA: 'badge-pendiente-firma',
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

export default function ContratosPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [toast, setToast] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [editing, setEditing] = useState(null);
  const [confirmDel, setConfirmDel] = useState(null);

  const qs = new URLSearchParams({
    page,
    size: 20,
    ...(filtroEstado ? { estado: filtroEstado } : {}),
  });
  const { data, loading, refetch } = useFetch(() => api.get(`/contratos?${qs}`), [page, filtroEstado]);
  const { data: apartamentos } = useFetch(() => api.get('/apartamentos?size=200'), []);
  const { data: residentes } = useFetch(() => api.get('/residentes?size=200'), []);

  const columns = [
    { key: 'idContrato', label: 'ID', width: 60 },
    { key: 'apartamento', label: 'Apartamento' },
    { key: 'arrendatario', label: 'Arrendatario' },
    { key: 'fechaInicio', label: 'Inicio', render: (r) => formatDate(r.fechaInicio) },
    { key: 'fechaFin', label: 'Fin', render: (r) => formatDate(r.fechaFin) },
    { key: 'tipo', label: 'Tipo' },
    { key: 'valorMensual', label: 'Valor Mensual', render: (r) => formatCurrency(r.valorMensual) },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
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
              idApartamento: row.idApartamento || '',
              idResidente: row.idResidente || '',
              fechaInicio: row.fechaInicio || '',
              fechaFin: row.fechaFin || '',
              tipo: row.tipo || 'INICIAL',
              valorMensual: row.valorMensual || '',
              estado: row.estado || 'ACTIVO',
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
    if (!form.idApartamento) e.idApartamento = 'Requerido';
    if (!form.idResidente) e.idResidente = 'Requerido';
    if (!form.fechaInicio) e.fechaInicio = 'Requerido';
    if (!form.valorMensual) e.valorMensual = 'Requerido';
    setErrors(e);
    return Object.keys(e).length === 0;
  }
  async function save() {
    if (!validate()) return;
    try {
      const payload = {
        ...form,
        valorMensual: Number(form.valorMensual),
        idApartamento: Number(form.idApartamento),
        idResidente: Number(form.idResidente),
      };
      if (editing) {
        await api.put(`/contratos/${editing.idContrato}`, payload);
        setToast({ message: 'Contrato actualizado', type: 'success' });
      } else {
        await api.post('/contratos', payload);
        setToast({ message: 'Contrato creado', type: 'success' });
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
      await api.del(`/contratos/${confirmDel.idContrato}`);
      setToast({ message: 'Contrato eliminado', type: 'success' });
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  return (
    <div>
      <PageHeader
        title="Contratos"
        subtitle="Contratos de arrendamiento"
      />
      <div className="table-toolbar" style={{ marginBottom: '12px' }}>
        <div className="filters">
          <Select
            id="filtroEstado"
            value={filtroEstado}
            onChange={(e) => {
              setFiltroEstado(e.target.value);
              setPage(0);
            }}
            className="filter-select"
          >
            {ESTADOS.map((e) => (
              <option key={e || 'all'} value={e}>
                {e || 'Todos los estados'}
              </option>
            ))}
          </Select>
        </div>
        <div className="actions">
          <Button
            onClick={() => {
              setEditing(null);
              setForm(emptyForm);
              setErrors({});
              setModalOpen(true);
            }}
          >
            + Nuevo Contrato
          </Button>
        </div>
      </div>
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay contratos"
        keyField="idContrato"
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
        title={editing ? 'Editar Contrato' : 'Nuevo Contrato'}
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
            id="idApartamento"
            label="Apartamento"
            value={form.idApartamento}
            onChange={(e) => update('idApartamento', e.target.value)}
            error={errors.idApartamento}
          >
            <option value="">— Seleccionar —</option>
            {(apartamentos?.items || []).map((a) => (
              <option key={a.idApartamento} value={a.idApartamento}>
                Apto {a.numero}
              </option>
            ))}
          </Select>
          <Select
            id="idResidente"
            label="Residente"
            value={form.idResidente}
            onChange={(e) => update('idResidente', e.target.value)}
            error={errors.idResidente}
          >
            <option value="">— Seleccionar —</option>
            {(residentes?.items || []).map((r) => (
              <option key={r.idResidente} value={r.idResidente}>
                {r.nombres} {r.apellidos}
              </option>
            ))}
          </Select>
        </div>
        <div className="form-row">
          <Input
            id="fechaInicio"
            label="Fecha Inicio"
            type="date"
            value={form.fechaInicio}
            onChange={(e) => update('fechaInicio', e.target.value)}
            error={errors.fechaInicio}
          />
          <Input
            id="fechaFin"
            label="Fecha Fin"
            type="date"
            value={form.fechaFin}
            onChange={(e) => update('fechaFin', e.target.value)}
          />
        </div>
        <div className="form-row">
          <Select id="tipo" label="Tipo" value={form.tipo} onChange={(e) => update('tipo', e.target.value)}>
            {TIPOS.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </Select>
          <Input
            id="valorMensual"
            label="Valor Mensual"
            type="number"
            value={form.valorMensual}
            onChange={(e) => update('valorMensual', e.target.value)}
            error={errors.valorMensual}
          />
        </div>
        <div className="form-group">
          <Select
            id="estado"
            label="Estado"
            value={form.estado}
            onChange={(e) => update('estado', e.target.value)}
          >
            {ESTADOS.filter(Boolean).map((e) => (
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
        title="Eliminar contrato"
        message={`¿Eliminar contrato #${confirmDel?.idContrato}?`}
        confirmLabel="Eliminar"
        danger
      />
      <Toast toast={toast} />
    </div>
  );
}
