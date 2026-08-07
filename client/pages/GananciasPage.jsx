import { useState } from 'react';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate } from '../lib/utils.js';

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

export default function GananciasPage() {
  const [page] = useState(0);
  const [toast] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get(`/ganancias?page=${page}&size=20`), [page]);
  const { data: stats } = useFetch(() => api.get('/ganancias/stats').catch(() => null), []);

  const columns = [
    { key: 'id', label: 'ID', width: 60 },
    { key: 'concepto', label: 'Concepto' },
    { key: 'monto', label: 'Monto', render: (r) => formatCurrency(r.monto) },
    { key: 'fecha', label: 'Fecha', render: (r) => formatDate(r.fecha) },
    { key: 'categoria', label: 'Categoría' },
  ];

  return (
    <div>
      <PageHeader title="Ganancias" subtitle="Ingresos del edificio" />
      {stats && (
        <div className="card-grid-3" style={{ marginBottom: '20px' }}>
          <div className="card">
            <div className="stat-label">Total del mes</div>
            <div style={{ fontSize: '24px', fontWeight: 700, color: '#065f46' }}>
              {formatCurrency(stats.mesActual)}
            </div>
          </div>
          <div className="card">
            <div className="stat-label">Total año</div>
            <div style={{ fontSize: '24px', fontWeight: 700 }}>{formatCurrency(stats.anioActual)}</div>
          </div>
          <div className="card">
            <div className="stat-label">Pendiente</div>
            <div style={{ fontSize: '24px', fontWeight: 700, color: '#92400e' }}>
              {formatCurrency(stats.pendiente)}
            </div>
          </div>
        </div>
      )}
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay ganancias registradas"
        keyField="id"
      />
      <Pagination
        page={page}
        totalPages={data?.totalPages || 1}
        totalItems={data?.totalItems}
        pageSize={20}
        onPageChange={() => {}}
      />
      <Toast toast={toast} />
    </div>
  );
}
