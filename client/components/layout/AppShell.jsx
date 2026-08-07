import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../lib/AuthContext.jsx';

const NAV_BY_ROLE = {
  ADMINISTRADOR: [
    { path: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/residentes', label: 'Residentes', icon: 'groups' },
    { path: '/apartamentos', label: 'Apartamentos', icon: 'domain' },
    { path: '/contratos', label: 'Contratos', icon: 'description' },
    { path: '/usuarios', label: 'Usuarios', icon: 'people' },
    { path: '/visitas', label: 'Visitas', icon: 'how_to_reg' },
    { path: '/parqueaderos', label: 'Parqueaderos', icon: 'local_parking' },
    { path: '/pagos', label: 'Pagos', icon: 'payments' },
    { path: '/multas', label: 'Multas', icon: 'gavel' },
    { path: '/alertas', label: 'Alertas', icon: 'notifications' },
    { path: '/avisos', label: 'Avisos', icon: 'campaign' },
    { path: '/quejas-admin', label: 'Solicitudes', icon: 'support_agent' },
  ],
  PORTERO: [
    { path: '/portero-dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/visitas', label: 'Visitas', icon: 'how_to_reg' },
    { path: '/paquetes', label: 'Paquetes', icon: 'inventory_2' },
    { path: '/parqueaderos', label: 'Parqueaderos', icon: 'local_parking' },
    { path: '/escanner-qr', label: 'Escáner QR', icon: 'qr_code_scanner' },
  ],
  RESIDENTE: [
    { path: '/residente-dashboard', label: 'Mi Panel', icon: 'dashboard' },
    { path: '/res-perfil', label: 'Mi Perfil', icon: 'person' },
    { path: '/res-apartamento', label: 'Mi Apartamento', icon: 'apartment' },
    { path: '/res-cuotas', label: 'Cuotas', icon: 'payments' },
    { path: '/res-frecuentes', label: 'Frecuentes', icon: 'how_to_reg' },
    { path: '/res-buzon', label: 'Buzón', icon: 'mail' },
    { path: '/res-visita', label: 'Nueva Visita', icon: 'add' },
    { path: '/res-quejas', label: 'Solicitudes', icon: 'support_agent' },
  ],
};

export default function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const items = NAV_BY_ROLE[user?.rol] || [];
  const initial = user?.username?.[0]?.toUpperCase() || 'U';
  const currentTitle = items.find((i) => location.pathname.startsWith(i.path))?.label || 'SAED';

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="flex h-screen w-full overflow-hidden bg-background">
      <aside className="hidden w-64 flex-col bg-primary text-on-primary md:flex">
        <div className="flex items-center gap-3 px-6 py-6">
          <img
            src={`${import.meta.env.BASE_URL}imagenes/saed_logo_emblem_only.png`}
            alt="SAED"
            className="h-9 w-9"
            onError={(e) => {
              e.currentTarget.style.display = 'none';
            }}
          />
          <div className="leading-tight">
            <div className="text-lg font-extrabold">SAED</div>
            <div className="text-[11px] uppercase tracking-wider opacity-80">
              Administración Residencial
            </div>
          </div>
        </div>

        <nav className="flex-1 space-y-1 px-3">
          {items.map((item) => {
            const active = location.pathname.startsWith(item.path);
            return (
              <button
                key={item.path}
                onClick={() => navigate(item.path)}
                className={`flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                  active
                    ? 'bg-white/15 text-on-primary'
                    : 'text-on-primary/80 hover:bg-white/10 hover:text-on-primary'
                }`}
              >
                <span className="material-symbols-outlined text-xl">{item.icon}</span>
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>

        <div className="border-t border-white/10 p-3">
          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-on-primary/80 transition-colors hover:bg-white/10 hover:text-on-primary"
          >
            <span className="material-symbols-outlined text-xl">logout</span>
            <span>Cerrar Sesión</span>
          </button>
        </div>
      </aside>

      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-20 items-center justify-between border-b border-outline-variant bg-surface px-8">
          <h1 className="text-2xl font-semibold text-on-background">{currentTitle}</h1>
          <div className="flex items-center gap-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary text-sm font-bold text-on-primary">
              {initial}
            </div>
            <span className="text-sm text-on-surface">{user?.username}</span>
            <span className="rounded-full bg-surface-container px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-on-surface-variant">
              {user?.rol}
            </span>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
