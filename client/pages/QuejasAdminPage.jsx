import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { Select, Textarea } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

const ESTADOS = ['PENDIENTE', 'EN_REVISION', 'RESUELTA', 'CERRADA'];
const PRIORIDADES = ['ALTA', 'MEDIA', 'BAJA'];

const ESTADO_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  EN_REVISION: 'badge-info',
  RESUELTA: 'badge-activo',
  CERRADA: 'badge-neutral',
};
const PRIORIDAD_BADGE = {
  ALTA: 'badge-danger',
  MEDIA: 'badge-warn',
  BAJA: 'badge-neutral',
};

export default function QuejasAdminPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroTipo, setFiltroTipo] = useState('');
  const [toast, setToast] = useState(null);
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState({ estado: '', prioridad: '', respuesta: '' });
  const [saving, setSaving] = useState(false);

  const qs = new URLSearchParams({
    page,
    size: 20,
    ...(filtroEstado ? { estado: filtroEstado } : {}),
    ...(filtroTipo ? { tipo: filtroTipo } : {}),
  });
  const { data, loading, refetch } = useFetch(() => api.get(`/quejas?${qs}`), [page, filtroEstado, filtroTipo]);

  const columns = [
    { key: 'id', label: 'ID', width: 60 },
    { key: 'tipo', label: 'Tipo' },
    { key: 'titulo', label: 'Título' },
    { key: 'apartamento', label: 'Apto' },
    { key: 'residente', label: 'Residente' },
    { key: 'categoria', label: 'Categoría' },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    {
      key: 'prioridad',
      label: 'Prioridad',
      render: (r) => <span className={`badge ${PRIORIDAD_BADGE[r.prioridad] || 'badge-neutral'}`}>{r.prioridad}</span>,
    },
    { key: 'fechaCreacion', label: 'Fecha', render: (r) => formatDate(r.fechaCreacion) },
  ];

  function openDetalle(row) {
    setModal(row);
    setForm({ estado: row.estado, prioridad: row.prioridad, respuesta: row.respuesta || '' });
  }
  async function save() {
    if (!modal) return;
    setSaving(true);
    try {
      await api.put(`/quejas/${modal.id}`, form);
      setToast({ message: 'Solicitud actualizada', type: 'success' });
      setModal(null);
      refetch();
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader title="Solicitudes" subtitle="Quejas, sugerencias y apelaciones" />
      <div className="card" style={{ marginBottom: '16px' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center' }}>
          <Select
            id="f-estado"
            value={filtroEstado}
            onChange={(e) => {
              setFiltroEstado(e.target.value);
              setPage(0);
            }}
            className="filter-select"
          >
            <option value="">Todos los estados</option>
            {ESTADOS.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </Select>
          <Select
            id="f-tipo"
            value={filtroTipo}
            onChange={(e) => {
              setFiltroTipo(e.target.value);
              setPage(0);
            }}
            className="filter-select"
          >
            <option value="">Todos los tipos</option>
            <option value="QUEJA">Queja</option>
            <option value="SUGERENCIA">Sugerencia</option>
            <option value="APELACION">Apelación</option>
          </Select>
        </div>
      </div>
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay solicitudes"
        keyField="id"
        onRowClick={openDetalle}
      />
      <Pagination
        page={page}
        totalPages={data?.totalPages || 1}
        totalItems={data?.totalItems}
        pageSize={20}
        onPageChange={setPage}
      />

      <Modal
        open={!!modal}
        onClose={() => setModal(null)}
        title="Detalle de la Solicitud"
        size="lg"
        footer={
          <>
            <Button variant="outline" onClick={() => setModal(null)}>Cancelar</Button>
            <Button onClick={save} disabled={saving}>{saving ? 'Guardando...' : 'Guardar'}</Button>
          </>
        }
      >
        {modal && (
          <>
            <div className="card-grid-2" style={{ marginBottom: '16px' }}>
              <div>
                <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Tipo</div>
                <div style={{ fontSize: '13px' }}>{modal.tipo}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Categoría</div>
                <div style={{ fontSize: '13px' }}>{modal.categoria}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Apartamento</div>
                <div style={{ fontSize: '13px' }}>{modal.apartamento}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Residente</div>
                <div style={{ fontSize: '13px' }}>{modal.residente}</div>
              </div>
            </div>
            <div className="form-group">
              <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Título</div>
              <div style={{ fontSize: '13px' }}>{modal.titulo}</div>
            </div>
            <div className="form-group">
              <div style={{ fontSize: '11px', color: '#475569', fontWeight: 600 }}>Descripción</div>
              <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap' }}>{modal.descripcion}</div>
            </div>
            <div className="form-row">
              <Select
                id="estado"
                label="Estado"
                value={form.estado}
                onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
              >
                {ESTADOS.map((e) => (
                  <option key={e} value={e}>{e}</option>
                ))}
              </Select>
              <Select
                id="prioridad"
                label="Prioridad"
                value={form.prioridad}
                onChange={(e) => setForm((f) => ({ ...f, prioridad: e.target.value }))}
              >
                {PRIORIDADES.map((p) => (
                  <option key={p} value={p}>{p}</option>
                ))}
              </Select>
            </div>
            <div className="form-group">
              <Textarea
                id="respuesta"
                label="Respuesta al residente (opcional)"
                rows={4}
                value={form.respuesta}
                onChange={(e) => setForm((f) => ({ ...f, respuesta: e.target.value }))}
              />
            </div>
          </>
        )}
      </Modal>
      <Toast toast={toast} />
    </div>
  );
}
