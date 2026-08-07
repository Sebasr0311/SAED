import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency } from '../lib/utils.js';

function StatCard({ icon, value, label, color = 'primary' }) {
  const colors = {
    primary: 'bg-primary/10 text-primary',
    green: 'bg-accent-green-bg text-accent-green',
    blue: 'bg-blue-50 text-blue-700',
    amber: 'bg-warn-amber-bg text-warn-amber',
  };
  return (
    <div className="card flex items-center gap-4">
      <div
        className={`flex h-12 w-12 items-center justify-center rounded-xl ${colors[color]}`}
      >
        <span className="material-symbols-outlined text-2xl">{icon}</span>
      </div>
      <div>
        <div className="text-2xl font-bold text-on-surface">{value}</div>
        <div className="text-xs text-on-surface-variant">{label}</div>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { data: residentes } = useFetch(() => api.get('/residentes?size=1'), []);
  const { data: apartamentos } = useFetch(() => api.get('/apartamentos?size=1'), []);
  const { data: contratos } = useFetch(() => api.get('/contratos?size=1&estado=ACTIVO'), []);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-on-background">Resumen general</h2>
        <p className="text-sm text-on-surface-variant">
          Vista rápida de los indicadores clave del edificio
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
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

      <div className="card">
        <h3 className="mb-2 text-base font-semibold text-on-surface">Bienvenido al panel</h3>
        <p className="text-sm text-on-surface-variant">
          Desde aquí puedes gestionar residentes, apartamentos, contratos, pagos y más. Usa el menú
          lateral para navegar entre las secciones.
        </p>
      </div>
    </div>
  );
}
