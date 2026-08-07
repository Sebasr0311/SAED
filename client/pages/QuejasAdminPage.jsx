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
    { key: 'estado', label: 'Estado' },
    { key: 'prioridad', label: 'Prioridad' },
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
      <div className="card mb-4 flex flex-wrap items-center gap-3">
        <Select
          id="f-estado"
          value={filtroEstado}
          onChange={(e) => {
            setFiltroEstado(e.target.value);
            setPage(0);
          }}
          className="w-auto"
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
          className="w-auto"
        >
          <option value="">Todos los tipos</option>
          <option value="QUEJA">Queja</option>
          <option value="SUGERENCIA">Sugerencia</option>
          <option value="APELACION">Apelación</option>
        </Select>
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
            <Button variant="outline" onClick={() => setModal(null)}>
              Cancelar
            </Button>
            <Button onClick={save} loading={saving} icon="save">
              Guardar
            </Button>
          </>
        }
      >
        {modal && (
          <div className="space-y-4">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <div className="text-xs text-on-surface-variant">Tipo</div>
                <div className="text-sm font-medium">{modal.tipo}</div>
              </div>
              <div>
                <div className="text-xs text-on-surface-variant">Categoría</div>
                <div className="text-sm font-medium">{modal.categoria}</div>
              </div>
              <div>
                <div className="text-xs text-on-surface-variant">Apartamento</div>
                <div className="text-sm font-medium">{modal.apartamento}</div>
              </div>
              <div>
                <div className="text-xs text-on-surface-variant">Residente</div>
                <div className="text-sm font-medium">{modal.residente}</div>
              </div>
            </div>
            <div>
              <div className="text-xs text-on-surface-variant">Título</div>
              <div className="text-sm font-medium">{modal.titulo}</div>
            </div>
            <div>
              <div className="text-xs text-on-surface-variant">Descripción</div>
              <div className="whitespace-pre-wrap text-sm">{modal.descripcion}</div>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Select
                id="estado"
                label="Estado"
                value={form.estado}
                onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
              >
                {ESTADOS.map((e) => (
                  <option key={e} value={e}>
                    {e}
                  </option>
                ))}
              </Select>
              <Select
                id="prioridad"
                label="Prioridad"
                value={form.prioridad}
                onChange={(e) => setForm((f) => ({ ...f, prioridad: e.target.value }))}
              >
                {PRIORIDADES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </Select>
            </div>
            <Textarea
              id="respuesta"
              label="Respuesta al residente (opcional)"
              rows={4}
              value={form.respuesta}
              onChange={(e) => setForm((f) => ({ ...f, respuesta: e.target.value }))}
            />
          </div>
        )}
      </Modal>
      <Toast toast={toast} />
    </div>
  );
}
