import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { formatCurrency } from '../lib/utils.js';
import { useAuth } from '../lib/AuthContext.jsx';
import { PageHeader } from '../components/ui/PageHeader.jsx';

function Stat({ icon, value, label, color = 'primary' }) {
  const colors = {
    primary: 'bg-primary/10 text-primary',
    green: 'bg-accent-green-bg text-accent-green',
    blue: 'bg-blue-50 text-blue-700',
    amber: 'bg-warn-amber-bg text-warn-amber',
  };
  return (
    <div className="card flex items-center gap-4">
      <div className={`flex h-12 w-12 items-center justify-center rounded-xl ${colors[color]}`}>
        <span className="material-symbols-outlined text-2xl">{icon}</span>
      </div>
      <div className="min-w-0">
        <div className="truncate text-xl font-bold text-on-surface">{value}</div>
        <div className="text-xs text-on-surface-variant">{label}</div>
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
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
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
