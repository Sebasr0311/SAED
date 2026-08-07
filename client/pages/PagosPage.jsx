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

export default function PagosPage() {
  const [page] = useState(0);
  const [toast] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get(`/pagos?page=${page}&size=20`), [page]);
  const { data: stats } = useFetch(() => api.get('/pagos/stats').catch(() => null), []);

  const columns = [
    { key: 'idPago', label: 'ID', width: 60 },
    { key: 'apartamento', label: 'Apartamento' },
    { key: 'residente', label: 'Residente' },
    { key: 'concepto', label: 'Concepto' },
    { key: 'monto', label: 'Monto', render: (r) => formatCurrency(r.monto) },
    { key: 'fechaPago', label: 'Fecha', render: (r) => formatDate(r.fechaPago) },
  ];

  return (
    <div>
      <PageHeader title="Pagos" subtitle="Cuotas de arriendo y multas" />
      <div className="card-grid-3" style={{ marginBottom: '20px' }}>
        <Stat icon="receipt_long" value={formatCurrency(stats?.cuotasPendientes)} label="Cuotas pendientes" color="primary" />
        <Stat icon="gavel" value={formatCurrency(stats?.multasPendientes)} label="Multas pendientes" color="amber" />
        <Stat icon="apartment" value={stats?.aptosConSaldo || 0} label="Aptos con saldo" color="blue" />
      </div>
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay pagos"
        keyField="idPago"
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
