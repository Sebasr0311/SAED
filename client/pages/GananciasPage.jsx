import { useState } from 'react';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate } from '../lib/utils.js';

export default function GananciasPage() {
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get(`/ganancias?page=${page}&size=20`), [page]);
  const { data: stats } = useFetch(() => api.get('/ganancias/stats'), []);

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
        <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div className="card">
            <div className="text-xs text-on-surface-variant">Total del mes</div>
            <div className="text-2xl font-bold text-accent-green">
              {formatCurrency(stats.mesActual)}
            </div>
          </div>
          <div className="card">
            <div className="text-xs text-on-surface-variant">Total año</div>
            <div className="text-2xl font-bold text-on-surface">{formatCurrency(stats.anioActual)}</div>
          </div>
          <div className="card">
            <div className="text-xs text-on-surface-variant">Pendiente</div>
            <div className="text-2xl font-bold text-warn-amber">{formatCurrency(stats.pendiente)}</div>
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
        onPageChange={setPage}
      />
      <Toast toast={toast} />
    </div>
  );
}
