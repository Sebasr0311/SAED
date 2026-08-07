import { useState } from 'react';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency, formatDate } from '../lib/utils.js';

export default function MultasPage() {
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get(`/multas?page=${page}&size=20`), [page]);

  const columns = [
    { key: 'idMulta', label: 'ID', width: 60 },
    { key: 'apartamento', label: 'Apartamento' },
    { key: 'residente', label: 'Residente' },
    { key: 'motivo', label: 'Motivo' },
    { key: 'monto', label: 'Monto', render: (r) => formatCurrency(r.monto) },
    { key: 'fecha', label: 'Fecha', render: (r) => formatDate(r.fecha) },
    { key: 'estado', label: 'Estado' },
  ];

  return (
    <div>
      <PageHeader title="Multas" subtitle="Registro de multas del edificio" />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay multas"
        keyField="idMulta"
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
