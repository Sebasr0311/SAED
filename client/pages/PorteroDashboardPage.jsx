import { useFetch } from '../lib/hooks.js';
import api from '../lib/api.js';
import { PageHeader } from '../components/ui/PageHeader.jsx';

function Stat({ icon, value, label, color = 'primary' }) {
  return (
    <div className="stat-card">
      <div className={`stat-icon ${color}`}>
        <span className="material-symbols-outlined">{icon}</span>
      </div>
      <div className="stat-body">
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
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
  // Sin endpoint agregado /portero/stats — se calcula client-side combinando endpoints existentes
  const { data: visitasHoy } = useFetch(() => api.get('/visitas/hoy'), []);
  const { data: parqueaderos } = useFetch(() => api.get('/parqueaderos?estado=DISPONIBLE'), []);
  const { data: paquetesPendientes } = useFetch(() => api.get('/buzon/paquetes-pendientes'), []);

  const visitasHoyCount = visitasHoy?.length ?? '—';
  const visitasActivas = (visitasHoy || []).filter(
    (v) => v.estado === 'EN_CURSO' || v.estado === 'PENDIENTE'
  ).length;
  const parqDisponibles = (parqueaderos || []).filter((p) => p.esVisitante).length;
  const paquetesCount = paquetesPendientes?.count ?? '—';

  return (
    <div>
      <PageHeader title="Panel de Portería" />
      <div className="card-grid-4" style={{ marginBottom: '20px' }}>
        <Stat icon="today" value={visitasHoyCount} label="Visitas Hoy" color="amber" />
        <Stat icon="how_to_reg" value={visitasActivas} label="Visitas Activas" color="cyan" />
        <Stat icon="local_parking" value={parqDisponibles} label="Parqueaderos Visitantes" color="green" />
        <Stat icon="inventory_2" value={paquetesCount} label="Paquetes Pendientes" color="orange" />
      </div>
      <div className="card">
        <h3 className="card-title">
          <span className="material-symbols-outlined">dashboard</span>
          Accesos Rápidos
        </h3>
        <div className="card-grid-4" style={{ marginTop: '12px' }}>
          {QUICK.map((q) => (
            <a key={q.path} href={`#${q.path}`} className="action-card">
              <span className="action-card-icon">
                <span className="material-symbols-outlined" style={{ fontSize: '32px' }}>
                  {q.icon}
                </span>
              </span>
              <span className="action-card-label">{q.label}</span>
            </a>
          ))}
        </div>
      </div>
    </div>
  );
}
