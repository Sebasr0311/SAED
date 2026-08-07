import { useState } from 'react';
import { Button } from '../components/ui/Button.jsx';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate } from '../lib/utils.js';

function Stat({ icon, value, label, color = 'primary' }) {
  const colors = {
    primary: 'bg-primary/10 text-primary',
    green: 'bg-accent-green-bg text-accent-green',
    blue: 'bg-blue-50 text-blue-700',
    amber: 'bg-warn-amber-bg text-warn-amber',
  };
  return (
    <div className="card flex items-center gap-4">
      <div className={`flex h-12 w-12 items-center justify-center rounded-xl ${colors[color]}`}>
        <span className="material-symbols-outlined text-2xl">{icon}</span>
      </div>
      <div>
        <div className="text-2xl font-bold text-on-surface">{value}</div>
        <div className="text-xs text-on-surface-variant">{label}</div>
      </div>
    </div>
  );
}

export default function PagosPage() {
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get(`/pagos?page=${page}&size=20`), [page]);
  const { data: stats } = useFetch(() => api.get('/pagos/stats'), []);

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
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
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
        onPageChange={setPage}
      />
      <Toast toast={toast} />
    </div>
  );
}
