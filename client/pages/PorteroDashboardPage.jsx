import { useState } from 'react';
import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';

function Stat({ icon, value, label, color = 'primary' }) {
  const colors = {
    primary: 'bg-primary/10 text-primary',
    green: 'bg-accent-green-bg text-accent-green',
    blue: 'bg-blue-50 text-blue-700',
    amber: 'bg-warn-amber-bg text-warn-amber',
    cyan: 'bg-cyan-50 text-cyan-700',
    orange: 'bg-orange-50 text-orange-700',
  };
  return (
    <div className="card flex items-center gap-4">
      <div className={`flex h-12 w-12 items-center justify-center rounded-xl ${colors[color]}`}>
        <span className="material-symbols-outlined text-2xl">{icon}</span>
      </div>
      <div>
        <div className="text-2xl font-bold text-on-surface">{value}</div>
        <div className="text-xs text-on-surface-variant">{label}</div>
      </div>
    </div>
  );
}

const QUICK = [
  { label: 'Registrar Visita', icon: 'edit_note', path: '/visitas' },
  { label: 'Registrar Paquete', icon: 'inventory_2', path: '/paquetes' },
  { label: 'Gestionar Parqueaderos', icon: 'local_parking', path: '/parqueaderos' },
  { label: 'Escáner QR', icon: 'qr_code_scanner', path: '/escanner-qr' },
];

export default function PorteroDashboardPage() {
  const { data: stats } = useFetch(() => api.get('/portero/stats'), []);

  return (
    <div>
      <PageHeader title="Panel de Portería" />
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat icon="today" value={stats?.visitasHoy ?? '—'} label="Visitas Hoy" color="amber" />
        <Stat icon="how_to_reg" value={stats?.visitasActivas ?? '—'} label="Visitas Activas" color="cyan" />
        <Stat
          icon="local_parking"
          value={stats?.parqueaderosDisponibles ?? '—'}
          label="Parqueaderos Visitantes"
          color="green"
        />
        <Stat
          icon="inventory_2"
          value={stats?.paquetesPendientes ?? '—'}
          label="Paquetes Pendientes"
          color="orange"
        />
      </div>
      <div className="card">
        <h3 className="mb-4 text-base font-semibold">Accesos Rápidos</h3>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {QUICK.map((q) => (
            <a
              key={q.path}
              href={`#${q.path}`}
              className="flex flex-col items-center gap-2 rounded-xl border border-outline-variant bg-surface p-4 text-center transition-colors hover:bg-surface-container"
            >
              <span className="material-symbols-outlined text-3xl text-primary">{q.icon}</span>
              <span className="text-xs font-medium text-on-surface">{q.label}</span>
            </a>
          ))}
        </div>
      </div>
    </div>
  );
}
