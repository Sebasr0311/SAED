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
    <div className="app-shell">
      <div style={{ display: 'flex', height: '100%' }}>
        <aside className="sidebar-desktop">
          <div className="sidebar-logo-area">
            <div className="sidebar-logo-icon">
              <img src={`${import.meta.env.BASE_URL}imagenes/saed_logo_emblem_only.png`} alt="SAED" />
            </div>
            <div className="sidebar-logo-text">
              <span>SAED</span>
              <small>Administración Residencial</small>
            </div>
          </div>

          <nav className="sidebar-nav-area">
            {items.map((item) => {
              const active = location.pathname.startsWith(item.path);
              return (
                <button
                  key={item.path}
                  onClick={() => navigate(item.path)}
                  className="sidebar-item-btn"
                  style={active ? { color: 'white', background: 'rgba(59,130,246,0.12)' } : {}}
                >
                  <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>
                    {item.icon}
                  </span>
                  <span className="sidebar-label">{item.label}</span>
                </button>
              );
            })}
          </nav>

          <div className="sidebar-bottom">
            <button onClick={handleLogout} className="sidebar-group-btn" title="Cerrar Sesión">
              <span className="material-symbols-outlined">logout</span>
              <span className="sidebar-label">Cerrar Sesión</span>
            </button>
          </div>
        </aside>

        <div style={{ marginLeft: '72px', flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
          <header className="topbar">
            <div className="topbar-left">
              <h1 className="page-title">{currentTitle}</h1>
            </div>
            <div className="topbar-right">
              <div className="user-info">
                <div className="user-avatar">{initial}</div>
                <span className="user-name">{user?.username}</span>
                <span className="badge-role">{user?.rol}</span>
              </div>
            </div>
          </header>

          <main className="content-area">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
