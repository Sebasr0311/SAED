import { useState } from 'react';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { Button } from '../components/ui/Button.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { Select } from '../components/ui/Form.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate, imageSrc } from '../lib/utils.js';

const ESTADO_BADGE = {
  PENDIENTE: 'badge-pendiente-firma',
  PAGADA: 'badge-activo',
  VENCIDA: 'badge-danger',
  ANULADA: 'badge-cancelado',
};

const TIPO_BADGE = {
  RUIDO: 'badge-warn',
  PARQUEADERO: 'badge-info',
};

const PAGE_SIZE = 10;

export default function MultasPage() {
  const [page, setPage] = useState(0);
  const [filtroEstado, setFiltroEstado] = useState('');
  const [toast, setToast] = useState(null);
  const [detalle, setDetalle] = useState(null);
  const [loadingDetalle, setLoadingDetalle] = useState(false);
  const [fotoGrande, setFotoGrande] = useState(null);

  const { data, loading, error, refetch } = useFetch(() => api.get('/multas/todas'), []);

  const items = (data?.items || data || []).filter((m) => !filtroEstado || m.estado === filtroEstado);
  const totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const rows = items.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  async function verDetalle(row) {
    setLoadingDetalle(true);
    try {
      const d = await api.get(`/multas/${row.idMulta}`);
      setDetalle(d);
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    } finally {
      setLoadingDetalle(false);
    }
  }

  async function marcarPagada(row) {
    try {
      await api.put(`/multas/${row.idMulta}/pagar`, { metodoPago: 'EFECTIVO' });
      setToast({ message: 'Multa marcada como pagada', type: 'success' });
      refetch();
      if (detalle?.idMulta === row.idMulta) setDetalle(null);
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  async function anular(row) {
    if (!window.confirm(`¿Anular la multa #${row.idMulta}?`)) return;
    try {
      await api.put(`/multas/${row.idMulta}/anular`);
      setToast({ message: 'Multa anulada', type: 'success' });
      refetch();
      if (detalle?.idMulta === row.idMulta) setDetalle(null);
    } catch (err) {
      setToast({ message: err.message, type: 'error' });
    }
  }

  const columns = [
    { key: 'idMulta', label: 'ID', width: 60 },
    { key: 'numeroApartamento', label: 'Apartamento' },
    { key: 'nombreResidente', label: 'Residente' },
    {
      key: 'tipo',
      label: 'Tipo',
      render: (r) => <span className={`badge ${TIPO_BADGE[r.tipo] || 'badge-neutral'}`}>{r.tipo}</span>,
    },
    { key: 'monto', label: 'Monto', render: (r) => formatCurrency(r.monto) },
    { key: 'fechaCreacion', label: 'Fecha', render: (r) => formatDate(r.fechaCreacion) },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
    {
      key: 'actions',
      label: 'Acciones',
      width: 140,
      render: (row) => (
        <div style={{ display: 'flex', gap: '4px' }}>
          <button
            onClick={(e) => {
              e.stopPropagation();
              verDetalle(row);
            }}
            className="btn btn-ghost btn-sm"
            aria-label="Ver detalle"
            title="Ver detalle"
          >
            <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>visibility</span>
          </button>
          {row.estado === 'PENDIENTE' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                marcarPagada(row);
              }}
              className="btn btn-ghost btn-sm"
              aria-label="Marcar pagada"
              title="Marcar pagada"
            >
              <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--accent-green)' }}>
                payments
              </span>
            </button>
          )}
          {row.estado !== 'ANULADA' && row.estado !== 'PAGADA' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                anular(row);
              }}
              className="btn btn-ghost btn-sm"
              aria-label="Anular"
              title="Anular"
            >
              <span className="material-symbols-outlined" style={{ fontSize: '18px', color: 'var(--error)' }}>
                block
              </span>
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Multas"
        subtitle="Registro de multas del edificio"
        action={
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
            <option value="PENDIENTE">Pendientes</option>
            <option value="PAGADA">Pagadas</option>
            <option value="VENCIDA">Vencidas</option>
            <option value="ANULADA">Anuladas</option>
          </Select>
        }
      />
      <DataTable
        columns={columns}
        rows={rows}
        loading={loading}
                empty={{ icon: 'gavel', title: 'No hay multas', subtitle: 'Las multas generadas aparecerán aquí.' }}
            error={error?.message}
        keyField="idMulta"
        onRowClick={verDetalle}
      />
      <Pagination
        page={safePage}
        totalPages={totalPages}
        totalItems={items.length}
        pageSize={PAGE_SIZE}
        onPageChange={setPage}
      />

      <Modal open={!!detalle} onClose={() => setDetalle(null)} title="Detalle de Multa" size="md">
        {loadingDetalle && <p>Cargando...</p>}
        {detalle && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div className="detail-row">
              <span>ID</span>
              <span>{detalle.idMulta}</span>
            </div>
            <div className="detail-row">
              <span>Tipo</span>
              <span>
                <span className={`badge ${TIPO_BADGE[detalle.tipo] || 'badge-neutral'}`}>{detalle.tipo}</span>
              </span>
            </div>
            <div className="detail-row">
              <span>Apartamento</span>
              <span>{detalle.numeroApartamento}</span>
            </div>
            <div className="detail-row">
              <span>Residente</span>
              <span>{detalle.nombreResidente}</span>
            </div>
            <div className="detail-row">
              <span>Monto</span>
              <span style={{ fontWeight: 700 }}>{formatCurrency(detalle.monto)}</span>
            </div>
            <div className="detail-row">
              <span>Estado</span>
              <span>
                <span className={`badge ${ESTADO_BADGE[detalle.estado] || 'badge-neutral'}`}>
                  {detalle.estado}
                </span>
              </span>
            </div>
            <div className="detail-row">
              <span>Fecha</span>
              <span>{formatDate(detalle.fechaCreacion)}</span>
            </div>
            {detalle.nombrePortero && (
              <div className="detail-row">
                <span>Generada por</span>
                <span>{detalle.nombrePortero}</span>
              </div>
            )}
            {detalle.fechaAvisoRuido && (
              <div className="detail-row">
                <span>Aviso de ruido previo</span>
                <span>{formatDate(detalle.fechaAvisoRuido)}</span>
              </div>
            )}
            {detalle.descripcion && (
              <div>
                <div style={{ fontSize: '11px', color: 'var(--text-secondary)', fontWeight: 600 }}>Descripción</div>
                <div style={{ fontSize: '13px' }}>{detalle.descripcion}</div>
              </div>
            )}
            {detalle.fotoEvidencia && (
              <img
                  src={imageSrc(detalle.fotoEvidencia)}
                  alt="Evidencia"
                  loading="lazy"
                style={{ maxWidth: '100%', borderRadius: '8px', marginTop: '8px', cursor: 'zoom-in' }}
                onClick={() => setFotoGrande(imageSrc(detalle.fotoEvidencia))}
              />
            )}
            {detalle.estado === 'PENDIENTE' && (
              <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                <Button onClick={() => marcarPagada(detalle)} style={{ flex: 1 }}>
                  Marcar Pagada
                </Button>
                <Button variant="danger" onClick={() => anular(detalle)} style={{ flex: 1 }}>
                  Anular
                </Button>
              </div>
            )}
          </div>
        )}
      </Modal>

      {fotoGrande && (
        <div
          onClick={() => setFotoGrande(null)}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.85)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'zoom-out',
          }}
        >
          <img
            src={fotoGrande}
            alt="Evidencia"
            style={{ maxWidth: '90%', maxHeight: '90%', borderRadius: '8px' }}
          />
        </div>
      )}

      <Toast toast={toast} />
    </div>
  );
}
