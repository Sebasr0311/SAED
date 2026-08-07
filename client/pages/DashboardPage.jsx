import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency } from '../lib/utils.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';

function StatCard({ icon, value, label, color = 'primary' }) {
  return (
    <div className="stat-card">
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="stat-body">
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
      </div>
      <div className="stat-badge">
        <span className="material-symbols-outlined">{icon}</span>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { data: residentes } = useFetch(() => api.get('/residentes?size=1'), []);
  const { data: apartamentos } = useFetch(() => api.get('/apartamentos?size=1'), []);
  const { data: contratos } = useFetch(() => api.get('/contratos?size=1&estado=ACTIVO'), []);

  return (
    <div>
      <PageHeader
        title="Resumen general"
        subtitle="Vista rápida de los indicadores clave del edificio"
      />

      <div className="card-grid-3">
        <StatCard
          icon="groups"
          value={residentes?.totalItems ?? '—'}
          label="Residentes"
          color="primary"
        />
        <StatCard
          icon="domain"
          value={apartamentos?.totalItems ?? '—'}
          label="Apartamentos"
          color="blue"
        />
        <StatCard
          icon="description"
          value={contratos?.totalItems ?? '—'}
          label="Contratos Activos"
          color="green"
        />
      </div>

      <div className="card" style={{ marginTop: '20px' }}>
        <h3 className="card-title">Bienvenido al panel</h3>
        <p className="text-sm text-on-surface-variant">
          Desde aquí puedes gestionar residentes, apartamentos, contratos, pagos y más. Usa el menú
          lateral para navegar entre las secciones.
        </p>
      </div>
    </div>
  );
}
