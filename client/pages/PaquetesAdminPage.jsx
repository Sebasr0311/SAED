import { useState, useMemo } from 'react';
import { Input } from '../components/ui/Form.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Modal } from '../components/ui/Modal.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate, imageSrc } from '../lib/utils.js';

function Stat({ icon, value, label, color = 'primary' }) {
  return (
    <div className="stat-card">
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="stat-body">
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}

export default function PaquetesAdminPage() {
  const [toast] = useState(null);
  const [search, setSearch] = useState('');
  const [detalle, setDetalle] = useState(null);

  const { data: paquetes, loading, error, refetch } = useFetch(() => api.get('/buzon/paquetes'), []);
  const all = paquetes?.items || paquetes || [];

  const filtrados = useMemo(() => {
    if (!search) return all;
    const term = search.toLowerCase();
    return all.filter((p) =>
      [p.numeroApartamento, p.nombreResidente, p.titulo].filter(Boolean).some((v) => String(v).toLowerCase().includes(term))
    );
  }, [all, search]);

  const stats = {
    total: filtrados.length,
    entregados: filtrados.filter((p) => p.entregado).length,
    pendientes: filtrados.filter((p) => !p.entregado).length,
  };

  const columns = [
    { key: 'idMensaje', label: 'ID', width: 60 },
    { key: 'numeroApartamento', label: 'Apartamento' },
    { key: 'nombreResidente', label: 'Residente' },
    { key: 'titulo', label: 'DescripciÃ³n' },
    { key: 'fechaCreacion', label: 'Recibido', render: (r) => formatDate(r.fechaCreacion) },
    {
      key: 'entregado',
      label: 'Estado',
      render: (r) => (
        <span className={`badge ${r.entregado ? 'badge-activo' : 'badge-pendiente-firma'}`}>
          {r.entregado ? 'Entregado' : 'Pendiente'}
        </span>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Paquetes"
        subtitle="Registro de paquetes recibidos"
        action={
          <Input
            id="search" aria-label="Buscar"
            placeholder="Buscar apto o residente..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
      />
      <div className="card-grid-3" style={{ marginBottom: '20px' }}>
        <Stat icon="inventory_2" value={stats.total} label="Total" color="primary" />
        <Stat icon="check_circle" value={stats.entregados} label="Entregados" color="green" />
        <Stat icon="pending" value={stats.pendientes} label="Pendientes" color="amber" />
      </div>
      <DataTable
        columns={columns}
        rows={filtrados}
        loading={loading}
        empty="No hay paquetes"
        error={error?.message}
        onRetry={refetch}
        keyField="idMensaje"
        onRowClick={setDetalle}
      />

      <Modal open={!!detalle} onClose={() => setDetalle(null)} title="Detalle del Paquete">
        {detalle && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div className="detail-row">
              <span>Apartamento</span>
              <span>{detalle.numeroApartamento}</span>
            </div>
            <div className="detail-row">
              <span>Residente</span>
              <span>{detalle.nombreResidente}</span>
            </div>
            <div className="detail-row">
              <span>DescripciÃ³n</span>
              <span>{detalle.titulo}</span>
            </div>
            <div className="detail-row">
              <span>Recibido</span>
              <span>{formatDate(detalle.fechaCreacion)}</span>
            </div>
            {detalle.fotoCaptura && (
              <img
                src={imageSrc(detalle.fotoCaptura)}
                alt="Foto del paquete"
                style={{ maxWidth: '100%', borderRadius: '8px', marginTop: '8px', cursor: 'zoom-in' }}
                onClick={(e) => window.open(e.target.src, '_blank')}
              />
            )}
          </div>
        )}
      </Modal>
      <Toast toast={toast} />
    </div>
  );
}
