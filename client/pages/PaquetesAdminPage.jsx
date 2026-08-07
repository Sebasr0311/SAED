import { useState } from 'react';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import Toast from '../components/ui/Toast.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatDate } from '../lib/utils.js';

export default function PaquetesAdminPage() {
  const [page] = useState(0);
  const [toast] = useState(null);

  const { data, loading, refetch } = useFetch(() => api.get(`/paquetes?page=${page}&size=20`), [page]);

  const columns = [
    { key: 'idPaquete', label: 'ID', width: 60 },
    { key: 'apartamento', label: 'Apartamento' },
    { key: 'residente', label: 'Residente' },
    { key: 'descripcion', label: 'Descripción' },
    { key: 'fechaRecepcion', label: 'Recibido', render: (r) => formatDate(r.fechaRecepcion) },
    { key: 'fechaEntrega', label: 'Entregado', render: (r) => formatDate(r.fechaEntrega) },
    { key: 'estado', label: 'Estado' },
  ];

  return (
    <div>
      <PageHeader title="Paquetes" subtitle="Registro de paquetes recibidos" />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No hay paquetes"
        keyField="idPaquete"
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
