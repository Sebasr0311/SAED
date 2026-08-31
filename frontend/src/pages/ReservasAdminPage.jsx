import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '../components/ui/Button.jsx';
import { Select } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { StatCard } from '../components/ui/StatCard.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

const ESTADOS = ['PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA'];
const PAGE_SIZE = 15;

const ESTADO_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  APROBADA: 'badge-activo',
  RECHAZADA: 'badge-danger',
  CANCELADA: 'badge-neutral',
};

export default function ReservasAdminPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [search, setSearch] = useState('');
  const [modal, setModal] = useState(null);
  const [form, setForm] = useState({ estado: '' });
  const [saving, setSaving] = useState(false);

  const { data, loading, error, refetch } = useFetch(() => api.get('/reservas/todas'), []);
  const all = Array.isArray(data) ? data : data?.items || [];

  const stats = {
    total: all.length,
    pendientes: all.filter((i) => i.estado === 'PENDIENTE').length,
    aprobadas: all.filter((i) => i.estado === 'APROBADA').length,
    rechazadas: all.filter((i) => i.estado === 'RECHAZADA').length,
  };

  const filtradas = all.filter((i) => {
    if (filtroEstado && i.estado !== filtroEstado) return false;
    if (search) {
      const q = search.toLowerCase();
      if (!i.nombreZona?.toLowerCase().includes(q)) {
        return false;
      }
    }
    return true;
  });

  const totalPages = Math.ceil(filtradas.length / PAGE_SIZE) || 1;
  const safePage = Math.min(page, totalPages - 1);
  const rows = filtradas.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

  const openDetalle = (row) => {
    setForm({ estado: row.estado });
    setModal(row);
  };

  const save = async () => {
    if (!form.estado) return;
    setSaving(true);
    try {
      await api.put(`/reservas/${modal.idReserva}/estado`, { estado: form.estado });
      toast.success('Estado de reserva actualizado');
      setModal(null);
      refetch();
    } catch (err) {
      toast.error('Error al actualizar reserva');
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    { key: 'nombreZona', label: 'Zona Común' },
    { key: 'fechaReserva', label: 'Día Reservado', render: (r) => r.fechaReserva },
    { key: 'horario', label: 'Horario', render: (r) => `${r.horaInicio} - ${r.horaFin}` },
    { key: 'cantidadAsistentes', label: 'Asistentes' },
    { 
      key: 'estado', 
      label: 'Estado', 
      render: (r) => (
        <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>
          {r.estado}
        </span>
      ) 
    },
    { key: 'fechaSolicitud', label: 'Solicitado el', render: (r) => formatDate(r.fechaSolicitud) },
  ];

  return (
    <div>
      <PageHeader title="Gestión de Reservas" subtitle="Administra las reservas de Zonas Comunes" />

      <div className="card-grid-4" style={{ marginBottom: '20px' }}>
        <StatCard icon="event_available" value={stats.total} label="Total Reservas" color="primary" />
        <StatCard icon="pending_actions" value={stats.pendientes} label="Pendientes" color="amber" />
        <StatCard icon="check_circle" value={stats.aprobadas} label="Aprobadas" color="green" />
        <StatCard icon="cancel" value={stats.rechazadas} label="Rechazadas" color="danger" />
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
          <input
            id="search" aria-label="Buscar"
            type="text"
            placeholder="Buscar por zona..."
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
        empty={{ icon: 'event_busy', title: 'No hay reservas', subtitle: 'Las solicitudes de los residentes aparecerán aquí.' }}
        error={error?.message}
        keyField="idReserva"
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
        title={`Detalle Reserva: ${modal?.nombreZona}`}
        size="md"
        footer={
          <>
            <Button variant="outline" onClick={() => setModal(null)} disabled={saving}>
              Cerrar
            </Button>
            <Button onClick={save} disabled={saving}>
              {saving ? 'Guardando...' : 'Actualizar Estado'}
            </Button>
          </>
        }
      >
        {modal && (
          <>
            <div className="card-grid-2" style={{ marginBottom: '16px' }}>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Fecha Reservada</div>
                <div style={{ fontSize: '13px' }}>{modal.fechaReserva}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Horario</div>
                <div style={{ fontSize: '13px' }}>{modal.horaInicio} - {modal.horaFin}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Asistentes</div>
                <div style={{ fontSize: '13px' }}>{modal.cantidadAsistentes}</div>
              </div>
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Costo Total</div>
                <div style={{ fontSize: '13px' }}>${Number(modal.costoTotal || 0).toLocaleString()}</div>
              </div>
            </div>
            
            <div className="form-group">
              <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Observaciones</div>
              <div style={{ fontSize: '13px', whiteSpace: 'pre-wrap', background: 'var(--bg-secondary)', padding: '12px', borderRadius: '6px' }}>
                {modal.observaciones || 'Sin observaciones'}
              </div>
            </div>
            
            <hr style={{ margin: '20px 0', borderColor: 'var(--border-color)' }} />
            
            <div className="form-group">
              <Select
                id="estado"
                label="Estado de la Reserva"
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
