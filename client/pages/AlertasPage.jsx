import { useState } from 'react';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

export default function AlertasPage() {
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get(`/alertas?page=${page}&size=20`), [page]);

  const columns = [
    { key: 'idAlerta', label: 'ID', width: 60 },
    { key: 'tipo', label: 'Tipo' },
    { key: 'apartamento', label: 'Apartamento' },
    { key: 'residente', label: 'Residente' },
    { key: 'periodo', label: 'Periodo' },
    { key: 'canal', label: 'Canal' },
    { key: 'leida', label: 'Leída', render: (r) => (r.leida ? 'Sí' : 'No') },
    { key: 'fechaEnvio', label: 'Enviada', render: (r) => formatDate(r.fechaEnvio) },
  ];

  return (
    <div>
      <PageHeader title="Alertas" subtitle="Notificaciones enviadas a residentes" />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay alertas"
        keyField="idAlerta"
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
