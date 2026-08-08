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
  ANULADA: 'badge-cancelado',
};

export default function ResCuotasPage() {
  const { user } = useAuth();
  const { data, loading } = useFetch(
    () => api.get(`/residentes/${user?.idResidente}/dashboard`),
    [user]
  );
  const info = data?.raw || data || {};
  const cuotas = info.cuotas || [];

  const columns = [
    { key: 'id', label: 'ID', width: 60, render: (r) => r.id || r.idCuota },
    { key: 'periodo', label: 'Periodo' },
    {
      key: 'monto',
      label: 'Monto',
      render: (r) => formatCurrency(r.valorTotal || r.monto || r.montoPendiente),
    },
    {
      key: 'fechaVencimiento',
      label: 'Vencimiento',
      render: (r) => formatDate(r.fechaVencimiento || r.fechaLimite),
    },
    {
      key: 'estado',
      label: 'Estado',
      render: (r) => (
        <span className={`badge ${ESTADO_BADGE[r.estado] || 'badge-neutral'}`}>{r.estado}</span>
      ),
    },
  ];

  return (
    <div>
      <PageHeader title="Mis Cuotas" subtitle="Historial de cuotas de arriendo" />
      <DataTable
        columns={columns}
        rows={cuotas}
        loading={loading}
        empty="No tienes cuotas"
        keyField="id"
      />
      <Pagination
        page={0}
        totalPages={1}
        totalItems={cuotas.length}
        pageSize={50}
        onPageChange={() => {}}
      />
    </div>
  );
}
