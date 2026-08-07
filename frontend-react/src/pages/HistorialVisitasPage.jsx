import { useState } from 'react';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

export default function HistorialVisitasPage() {
  const [page, setPage] = useState(0);
  const [toast, setToast] = useState(null);

  const { data, loading, refetch } = useFetch(
    () => api.get(`/visitas/historial?page=${page}&size=20`),
    [page]
  );

  const columns = [
    { key: 'idVisita', label: 'ID', width: 60 },
    { key: 'visitante', label: 'Visitante' },
    { key: 'documento', label: 'Documento' },
    { key: 'apartamento', label: 'Apto' },
    { key: 'fechaIngreso', label: 'Ingreso', render: (r) => formatDate(r.fechaIngreso) },
    { key: 'fechaSalida', label: 'Salida', render: (r) => formatDate(r.fechaSalida) },
    { key: 'duracion', label: 'Duración' },
  ];

  return (
    <div>
      <PageHeader title="Historial de Visitas" subtitle="Registro histórico de visitas" />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay historial"
        keyField="idVisita"
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
