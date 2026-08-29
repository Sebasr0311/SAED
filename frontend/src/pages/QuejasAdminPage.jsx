import { useState, useRef } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { Select, Textarea } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

const ESTADOS = ['RADICADO', 'EN_REVISION', 'RESUELTO', 'CERRADO'];
const TIPOS = ['PETICION', 'QUEJA', 'RECLAMO', 'SUGERENCIA'];
const PAGE_SIZE = 15;

const ESTADO_BADGE = {
  RADICADO: 'badge-pendiente-firma',
  EN_REVISION: 'badge-info',
  RESUELTO: 'badge-activo',
  CERRADO: 'badge-neutral',
};

export default function QuejasAdminPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [filtroTipo, setFiltroTipo] = useState('');
  const [search, setSearch] = useState('');
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState({ estado: '', observacion: '' });
  const [saving, setSaving] = useState(false);

  const { data, loading, error, refetch } = useFetch(() => api.get('/pqrs/todos'), []);
  const all = data?.items || [];

  const stats = {
    total: all.length,
    radicados: all.filter((i) => i.estado === 'RADICADO').length,
    revision: all.filter((i) => i.estado === 'EN_REVISION').length,
    resueltos: all.filter((i) => i.estado === 'RESUELTO' || i.estado === 'CERRADO').length,
  };

  const filtradas = all.filter((i) => {
    if (filtroEstado && i.estado !== filtroEstado) return false;
    if (filtroTipo && i.tipo !== filtroTipo) return false;
    if (search) {
      const q = search.toLowerCase();
      if (!i.asunto?.toLowerCase().includes(q) && !i.numeroRadicado?.toLowerCase().includes(q)) {
        return false;
      }
    }
    return true;
  });

  const totalPages = Math.ceil(filtradas.length / PAGE_SIZE) || 1;
  const safePage = Math.min(page, totalPages - 1);
  const rows = filtradas.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

  const openDetalle = (row) => {
    setForm({ estado: row.estado, observacion: row.observacionCierre || '' });
    setModal(row);
  };

  const save = async () => {
    if (!form.estado) return;
    setSaving(true);
    try {
      await api.put(`/pqrs/${modal.idTicket}/estado`, null, { params: { estado: form.estado } });
      toast.success('Estado del PQRS actualizado con éxito');
      setModal(null);
      refetch();
    } catch (err) {
      toast.error('Error al actualizar PQRS');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { key: 'numeroRadicado', label: 'Radicado' },
    { key: 'tipo', label: 'Tipo' },
    { key: 'asunto', label: 'Asunto' },
    { 
      key: 'estado', 
      label: 'Estado', 
      render: (r) => (
        <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>
          {r.estado}
        </span>
      ) 
    },
    { key: 'fechaRadicacion', label: 'Radicado El', render: (r) => formatDate(r.fechaRadicacion) },
    { 
      key: 'fechaLimiteSla', 
      label: 'Vence (SLA)', 
      render: (r) => {
        const vence = new Date(r.fechaLimiteSla);
        const hoy = new Date();
        const isVencido = vence < hoy && r.estado !== 'RESUELTO' && r.estado !== 'CERRADO';
        return (
          <span style={{ color: isVencido ? 'var(--danger-color)' : 'inherit', fontWeight: isVencido ? 600 : 'normal' }}>
            {formatDate(r.fechaLimiteSla)}
          </span>
        );
      }
    },
  ];

  return (
    <div>
      <PageHeader title="PQRS (Tickets)" subtitle="Peticiones, Quejas, Reclamos y Sugerencias" />

      <div className="card-grid-4" style={{ marginBottom: '20px' }}>
        <StatCard icon="confirmation_number" value={stats.total} label="Total PQRS" color="primary" />
        <StatCard icon="pending" value={stats.radicados} label="Nuevos (Radicados)" color="amber" />
        <StatCard icon="visibility" value={stats.revision} label="En Revisión" color="blue" />
        <StatCard icon="check_circle" value={stats.resueltos} label="Resueltos" color="green" />
      </div>

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
            {TIPOS.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </Select>
          <input
            id="search" aria-label="Buscar"
            type="text"
            placeholder="Buscar radicado o asunto..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="form-control"
            style={{ flex: 1, minWidth: '200px' }}
          />
        </div>
      </div>
      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
        empty={{ icon: 'support_agent', title: 'No hay PQRS', subtitle: 'Las PQRS de los residentes aparecerán aquí.' }}
        error={error?.message}
        keyField="idTicket"
        onRowClick={openDetalle}
      />
      <Pagination
        page={safePage}
        totalPages={totalPages}
        totalItems={filtradas.length}
        pageSize={PAGE_SIZE}
        onPageChange={setPage}
      />

      <Modal
        open={!!modal}
        onClose={() => setModal(null)}
        title={`Detalle PQRS: ${modal?.numeroRadicado}`}
        size="lg"
        footer={
          <>
            <Button variant="outline" onClick={() => setModal(null)} disabled={saving}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={saving}>
              {saving ? 'Guardando...' : 'Guardar Estado'}
            </Button>
          </>
        }
      >
        {modal && (
          <>
            <div className="card-grid-2" style={{ marginBottom: '16px' }}>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Tipo</div>
                <div style={{ fontSize: '13px' }}>{modal.tipo}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Categoría</div>
                <div style={{ fontSize: '13px' }}>{modal.categoria}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Fecha de Radicación</div>
                <div style={{ fontSize: '13px' }}>{formatDate(modal.fechaRadicacion)}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Vencimiento SLA</div>
                <div style={{ fontSize: '13px', color: new Date(modal.fechaLimiteSla) < new Date() ? 'var(--danger-color)' : 'inherit' }}>
                  {formatDate(modal.fechaLimiteSla)}
                </div>
              </div>
            </div>
            <div className="form-group">
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Asunto</div>
              <div style={{ fontSize: '14px', fontWeight: 500 }}>{modal.asunto}</div>
            </div>
            <div className="form-group">
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Descripción de la solicitud</div>
              <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap', background: 'var(--bg-secondary)', padding: '12px', borderRadius: '6px' }}>
                {modal.descripcion}
              </div>
            </div>
            
            <hr style={{ margin: '20px 0', borderColor: 'var(--border-color)' }} />
            
            <div className="form-group">
              <Select
                id="estado"
                label="Actualizar Estado"
                value={form.estado}
                onChange={(e) => setForm((f) => ({ ...f, estado: e.target.value }))}
              >
                {ESTADOS.map((e) => (
                  <option key={e} value={e}>
                    {e}
                  </option>
                ))}
              </Select>
            </div>
          </>
        )}
      </Modal>
    </div>
  );
}
