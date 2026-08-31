import { DataTable } from '../components/ui/DataTable.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { formatCurrency, formatDate, periodoLabel } from '../lib/utils.js';

const ESTADO_BADGE = {
  PAGADA: 'badge-activo',
  PENDIENTE: 'badge-pendiente-firma',
  VENCIDA: 'badge-danger',
  ANULADA: 'badge-cancelado',
};

export default function ResCuotasPage() {
  const { user } = useAuth();
  const { data, loading, error } = useFetch(
    () => (user?.idResidente ? api.get(`/residentes/${user.idResidente}/dashboard`) : Promise.resolve(null)),
    [user]
  );
  const info = data?.raw || data || {};
  const cuotas = info.cuotas || [];

  const columns = [
    { key: 'id', label: 'ID', width: 60, render: (r) => r.id || r.idCuota },
    {
      key: 'periodo',
      label: 'Periodo',
      render: (r) => periodoLabel(r.anio, r.mes),
    },
    {
      key: 'valorTotal',
      label: 'Monto',
      render: (r) => formatCurrency(r.valorTotal),
    },
    {
      key: 'fechaLimite',
      label: 'Vencimiento',
      render: (r) => formatDate(r.fechaLimite),
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
                empty={{ icon: 'receipt_long', title: 'No tienes cuotas', subtitle: 'Las cuotas de tu apartamento aparecerán aquí.' }}
        error={error?.message}
        keyField="id"
      />
    </div>
  );
}
