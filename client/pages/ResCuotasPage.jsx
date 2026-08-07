import { useState } from 'react';
import { DataTable } from '../components/ui/DataTable.jsx';
import { Pagination } from '../components/ui/Pagination.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { formatCurrency, formatDate } from '../lib/utils.js';

const ESTADO_BADGE = {
  PAGADA: 'badge-activo',
  PENDIENTE: 'badge-pendiente-firma',
  VENCIDA: 'badge-danger',
};

export default function ResCuotasPage() {
  const { user } = useAuth();
  const [page] = useState(0);
  const { data, loading } = useFetch(
    () => api.get(`/cuotas/residente/${user?.idResidente}?page=${page}&size=20`),
    [user, page]
  );

  const columns = [
    { key: 'id', label: 'ID', width: 60 },
    { key: 'periodo', label: 'Periodo' },
    { key: 'monto', label: 'Monto', render: (r) => formatCurrency(r.monto) },
    { key: 'fechaVencimiento', label: 'Vencimiento', render: (r) => formatDate(r.fechaVencimiento) },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>,
    },
  ];

  return (
    <div>
      <PageHeader title="Mis Cuotas" subtitle="Historial de cuotas de arriendo" />
      <DataTable
        columns={columns}
        rows={data?.items || []}
        loading={loading}
        empty="No tienes cuotas"
        keyField="id"
      />
      <Pagination
        page={page}
        totalPages={data?.totalPages || 1}
        totalItems={data?.totalItems}
        pageSize={20}
        onPageChange={() => {}}
      />
    </div>
  );
}
