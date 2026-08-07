import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency } from '../lib/utils.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';

function Stat({ icon, value, label, color = 'primary' }) {
  return (
    <div className="stat-card">
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="stat-body" style={{ minWidth: 0 }}>
        <div className="stat-value" style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{value}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}

export default function ResidenteDashboardPage() {
  const { user } = useAuth();
  const { data } = useFetch(() => api.get(`/residentes/${user?.idResidente}/resumen`), [user]);
  const resumen = data || {};

  return (
    <div>
      <PageHeader
        title="Mi Panel"
        subtitle={`Bienvenido${user?.username ? `, ${user.username}` : ''}`}
      />
      <div className="card-grid-4">
        <Stat icon="apartment" value={resumen.apartamento || '—'} label="Apartamento" color="blue" />
        <Stat icon="description" value={resumen.estadoContrato || '—'} label="Estado Contrato" color="amber" />
        <Stat
          icon="payments"
          value={formatCurrency(resumen.cuotasArriendo)}
          label="Cuotas de Arriendo"
          color="green"
        />
        <Stat icon="gavel" value={formatCurrency(resumen.multas)} label="Multas Pendientes" color="amber" />
      </div>
    </div>
  );
}
