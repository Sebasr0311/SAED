import { Suspense, useEffect, useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../lib/AuthContext.jsx';
import { getInitialTheme, applyTheme, persistTheme } from '../../lib/theme.js';
import ErrorBoundary from '../ErrorBoundary.jsx';
import NotificationBell from '../ui/NotificationBell.jsx';
import TenantSwitcher from './TenantSwitcher.jsx';

/**
 * Navegación agrupada por relación funcional, por rol.
 * Las rutas NO cambian: solo se reorganiza su presentación.
 */
const NAV_BY_ROLE = {
  ADMINISTRADOR: [
    {
      id: 'inicio',
      label: 'Inicio',
      icon: 'home',
      items: [{ path: '/dashboard', label: 'Dashboard', icon: 'dashboard' }],
    },
    {
      id: 'administracion',
      label: 'Administración',
      icon: 'domain',
      items: [
        { path: '/organizaciones', label: 'Organizaciones', icon: 'domain' },
        { path: '/propiedades', label: 'Propiedades', icon: 'apartment' },
        { path: '/personas', label: 'Personas', icon: 'person' },
        { path: '/residentes', label: 'Residentes', icon: 'groups' },
        { path: '/unidades', label: 'Unidades', icon: 'apartment' },
        { path: '/apartamentos', label: 'Apartamentos (legacy)', icon: 'apartment' },
        { path: '/contratos', label: 'Contratos', icon: 'description' },
        { path: '/usuarios', label: 'Usuarios', icon: 'manage_accounts' },
        { path: '/roles-asignaciones', label: 'Roles y Asignaciones', icon: 'admin_panel_settings' },
        { path: '/planes', label: 'Planes', icon: 'pricing_plan' },
        { path: '/membresias', label: 'Membresías', icon: 'card_membership' },
        { path: '/reportes', label: 'Reportes', icon: 'assessment' },
      ],
    },
    {
      id: 'operacion',
      label: 'Operación',
      icon: 'how_to_reg',
      items: [
        { path: '/visitas', label: 'Visitas', icon: 'how_to_reg' },
        { path: '/historial-visitas', label: 'Historial Visitas', icon: 'history' },
        { path: '/paquetes-admin', label: 'Paquetes', icon: 'inventory_2' },
        { path: '/parqueaderos', label: 'Parqueaderos', icon: 'local_parking' },
        { path: '/escanner-qr', label: 'Escáner QR', icon: 'qr_code_scanner' },
      ],
    },
    {
      id: 'finanzas',
      label: 'Finanzas',
      icon: 'payments',
      items: [
        { path: '/pagos', label: 'Pagos', icon: 'payments' },
        { path: '/ganancias', label: 'Ganancias', icon: 'trending_up' },
        { path: '/multas', label: 'Multas', icon: 'gavel' },
      ],
    },
    {
      id: 'comunicacion',
      label: 'Comunicación',
      icon: 'campaign',
      items: [
        { path: '/alertas', label: 'Alertas', icon: 'notifications' },
        { path: '/avisos', label: 'Avisos', icon: 'campaign' },
      ],
    },
    {
      id: 'gestion',
      label: 'Gestión',
      icon: 'support_agent',
      items: [{ path: '/quejas-admin', label: 'Solicitudes', icon: 'support_agent' }],
    },
  ],
  PORTERO: [
    {
      id: 'inicio',
      label: 'Inicio',
      icon: 'home',
      items: [{ path: '/portero-dashboard', label: 'Dashboard', icon: 'dashboard' }],
    },
    {
      id: 'operacion',
      label: 'Operación',
      icon: 'how_to_reg',
      items: [
        { path: '/visitas', label: 'Visitas', icon: 'how_to_reg' },
        { path: '/paquetes', label: 'Paquetes', icon: 'inventory_2' },
        { path: '/parqueaderos', label: 'Parqueaderos', icon: 'local_parking' },
        { path: '/escanner-qr', label: 'Escáner QR', icon: 'qr_code_scanner' },
      ],
    },
  ],
  RESIDENTE: [
    {
      id: 'inicio',
      label: 'Inicio',
      icon: 'home',
      items: [{ path: '/residente-dashboard', label: 'Mi Panel', icon: 'dashboard' }],
    },
    {
      id: 'mi-cuenta',
      label: 'Mi Cuenta',
      icon: 'account_circle',
      items: [
        { path: '/res-perfil', label: 'Mi Perfil', icon: 'person' },
        { path: '/res-apartamento', label: 'Mi Apartamento', icon: 'apartment' },
      ],
    },
    {
      id: 'finanzas',
      label: 'Finanzas',
      icon: 'payments',
      items: [{ path: '/res-cuotas', label: 'Cuotas', icon: 'payments' }],
    },
    {
      id: 'visitas',
      label: 'Visitas',
      icon: 'how_to_reg',
      items: [
        { path: '/res-frecuentes', label: 'Frecuentes', icon: 'group_add' },
        { path: '/res-visita', label: 'Nueva Visita', icon: 'add_circle' },
      ],
    },
    {
      id: 'comunicacion',
      label: 'Comunicación',
      icon: 'campaign',
      items: [
        { path: '/res-buzon', label: 'Buzón', icon: 'mail' },
        { path: '/res-quejas', label: 'Solicitudes', icon: 'support_agent' },
      ],
    },
  ],
};

const COLLAPSED_KEY = 'saed_sidebar_collapsed';

/** La ruta activa es la ruta exacta o un prefijo de segmento (evita /visitas vs /historial-visitas). */
function isItemActive(pathname, path) {
  return pathname === path || pathname.startsWith(path + '/');
}

export default function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const groups = NAV_BY_ROLE[user?.rol] || [];
  const allItems = groups.flatMap((g) => g.items);

  // Tema: localStorage gana; si no hay preferencia, se sigue la del sistema.
  // Se aplica como atributo data-theme en <html> para que los tokens CSS y las
  // variantes dark: de Tailwind resuelvan juntas.
  const [dark, setDark] = useState(() => getInitialTheme() === 'dark');
  useEffect(() => {
    applyTheme(dark ? 'dark' : 'light');
  }, [dark]);

  // Checkout de pago Wompi: encuadre global. El widget se inyecta ocupando todo
  // el ancho disponible (~1100px); lo envolvemos visualmente con un backdrop
  // (position fixed + CSS en index.css) SIN mover el iframe del DOM (el widget
  // lo localiza en su contenedor original para ajustar la altura). Aplica en
  // cualquier pagina donde se abra el pago (dashboard, cuotas, frecuentes...).
  useEffect(() => {
    let frameObserver = null;
    let currentFrame = null;
    let currentHost = null;

    const prepareFrame = (frame) => {
      if (currentFrame === frame) return;
      frameObserver?.disconnect();
      currentFrame = frame;
      currentHost?.classList.remove('wompi-modal-host');
      currentHost = frame.parentElement;
      currentHost?.classList.add('wompi-modal-host');

      const allowScroll = () => {
        if (frame.isConnected && frame.getAttribute('scrolling') !== 'yes') {
          frame.setAttribute('scrolling', 'yes');
        }
      };
      allowScroll();
      frameObserver = new MutationObserver(allowScroll);
      frameObserver.observe(frame, { attributes: true, attributeFilter: ['scrolling'] });
    };

    const obs = new MutationObserver(() => {
      const frame = document.querySelector('iframe[src*="checkout.wompi.co"]');
      if (frame) {
        prepareFrame(frame);
        if (!document.querySelector('.wompi-overlay')) {
          const overlay = document.createElement('div');
          overlay.className = 'wompi-overlay';
          frame.parentNode.insertBefore(overlay, frame);
        }
      } else {
        frameObserver?.disconnect();
        frameObserver = null;
        currentFrame = null;
        currentHost?.classList.remove('wompi-modal-host');
        currentHost = null;
        const ov = document.querySelector('.wompi-overlay');
        if (ov) ov.remove();
      }
    });
    obs.observe(document.body, { childList: true, subtree: true });
    return () => {
      obs.disconnect();
      frameObserver?.disconnect();
      currentHost?.classList.remove('wompi-modal-host');
    };
  }, []);

  // impeccable/harden: los iconos material-symbols dentro de botones legacy son
  // decorativos (el texto del boton es el accesible); se marcan aria-hidden
  // para que los lectores de pantalla no los anuncien.
  useEffect(() => {
    const marcar = () => {
      document.querySelectorAll('.btn .material-symbols-outlined, .topbar-logout-btn .material-symbols-outlined')
        .forEach((el) => { if (!el.hasAttribute('aria-hidden')) el.setAttribute('aria-hidden', 'true'); });
    };
    marcar();
    const obs = new MutationObserver(marcar);
    obs.observe(document.body, { childList: true, subtree: true });
    return () => obs.disconnect();
  }, []);
  function toggleTheme() {
    setDark((prev) => {
      const next = !prev;
      persistTheme(next ? 'dark' : 'light');
      return next;
    });
  }

  // Rail colapsado por defecto; el usuario puede fijarlo abierto (persistido).
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem(COLLAPSED_KEY) !== 'false';
    } catch {
      return true;
    }
  });
  const [mobileOpen, setMobileOpen] = useState(false);

  // Grupos abiertos: por defecto, el grupo que contiene la ruta activa.
  const [openGroups, setOpenGroups] = useState(() => {
    const initial = new Set();
    for (const group of groups) {
      if (group.items.some((i) => isItemActive(location.pathname, i.path))) initial.add(group.id);
    }
    return initial;
  });

  const activeGroup = groups.find((g) => g.items.some((i) => isItemActive(location.pathname, i.path)));
  const currentTitle = allItems.find((i) => isItemActive(location.pathname, i.path))?.label || 'SAED';

  // Titulo de pestana dinamico: "Apartamentos — SAED".
  useEffect(() => {
    document.title = currentTitle === 'SAED' ? 'SAED — Administración Residencial' : `${currentTitle} — SAED`;
  }, [currentTitle]);

  // Al navegar, el grupo de la ruta activa se abre automáticamente.
  useEffect(() => {
    if (activeGroup && !openGroups.has(activeGroup.id)) {
      setOpenGroups((prev) => new Set(prev).add(activeGroup.id));
    }
  }, [location.pathname]); // eslint-disable-line react-hooks/exhaustive-deps

  // Cerrar el drawer móvil con Escape.
  useEffect(() => {
    if (!mobileOpen) return undefined;
    function onKey(e) {
      if (e.key === 'Escape') setMobileOpen(false);
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [mobileOpen]);

  function toggleCollapsed() {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(COLLAPSED_KEY, String(next));
      } catch {
        /* almacenamiento no disponible */
      }
      return next;
    });
  }

  function toggleGroup(id) {
    setOpenGroups((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  // En modo rail (colapsado), al retirar el cursor del sidebar se cierran los
  // grupos abiertos y solo se conserva el de la ruta activa. Así el submenú no
  // queda mostrando los iconos cuando el rail se vuelve a colapsar.
  function handleSidebarMouseLeave() {
    if (!collapsed || mobileOpen) return;
    setOpenGroups(() => {
      const next = new Set();
      if (activeGroup) next.add(activeGroup.id);
      return next;
    });
  }

  const sidebarClasses = [
    'sidebar-desktop',
    collapsed && !mobileOpen ? 'sidebar-rail' : 'sidebar-open',
    mobileOpen ? 'sidebar-mobile-open' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className="app-shell">
      <a href="#main-content" className="skip-link">
        Saltar al contenido
      </a>
      <div className="app-shell-body">
        <aside className={sidebarClasses} onMouseLeave={handleSidebarMouseLeave}>
          <div className="sidebar-logo-area">
            <div className="sidebar-logo-icon">
              <img src={`${import.meta.env.BASE_URL}imagenes/saed_logo_emblem_only.png`} alt="SAED" />
            </div>
            <div className="sidebar-logo-text">
              <span>SAED</span>
              <small>Administración Residencial</small>
            </div>
          </div>

          <nav className="sidebar-nav-area" aria-label="Menú principal">
            {groups.map((group) => {
              const open = openGroups.has(group.id);
              const groupActive = activeGroup?.id === group.id;
              return (
                <div
                  key={group.id}
                  className={`sidebar-group ${open ? 'group-open' : ''} ${groupActive ? 'group-active' : ''}`}
                >
                  <button
                    type="button"
                    className="sidebar-group-btn"
                    title={group.label}
                    aria-expanded={open}
                    onClick={() => toggleGroup(group.id)}
                  >
                    <span className="material-symbols-outlined" aria-hidden="true">
                      {group.icon}
                    </span>
                    <span className="sidebar-label">{group.label}</span>
                    <span className="material-symbols-outlined sidebar-arrow" aria-hidden="true">
                      chevron_right
                    </span>
                  </button>

                  <div className="sidebar-submenu">
                    <div className="sidebar-submenu-inner">
                      {group.items.map((item) => {
                        const active = isItemActive(location.pathname, item.path);
                        return (
                          <button
                            key={item.path}
                            type="button"
                            className={`sidebar-item-btn ${active ? 'active' : ''}`}
                            title={item.label}
                            onClick={() => {
                              navigate(item.path);
                              setMobileOpen(false);
                            }}
                          >
                            <span className="material-symbols-outlined" aria-hidden="true">
                              {item.icon}
                            </span>
                            <span className="sidebar-label">{item.label}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                </div>
              );
            })}
          </nav>

          <div className="sidebar-bottom">
            <button type="button" onClick={handleLogout} className="sidebar-group-btn" title="Cerrar Sesión">
              <span className="material-symbols-outlined" aria-hidden="true">
                logout
              </span>
              <span className="sidebar-label">Cerrar Sesión</span>
            </button>
          </div>
        </aside>

        {mobileOpen && <div className="sidebar-overlay show" aria-hidden="true" onClick={() => setMobileOpen(false)} />}

        <div className="app-body">
          <header className="topbar">
            <div className="topbar-left">
              <button
                type="button"
                className="hamburger-btn sidebar-toggle-mobile"
                onClick={() => setMobileOpen(true)}
                aria-label="Abrir menú"
              >
                <span className="material-symbols-outlined" aria-hidden="true">
                  menu
                </span>
              </button>
              <button
                type="button"
                className="hamburger-btn sidebar-toggle-desktop"
                onClick={toggleCollapsed}
                aria-label={collapsed ? 'Expandir menú' : 'Colapsar menú'}
                title={collapsed ? 'Expandir menú' : 'Colapsar menú'}
              >
                <span className="material-symbols-outlined" aria-hidden="true">
                  {collapsed ? 'menu_open' : 'menu'}
                </span>
              </button>
              <h1 className="page-title">{currentTitle}</h1>
            </div>
            <div className="topbar-right">
              <TenantSwitcher />
              <button
                type="button"
                onClick={toggleTheme}
                className="topbar-logout-btn"
                aria-label={dark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
                title={dark ? 'Modo claro' : 'Modo oscuro'}
              >
                <span className="material-symbols-outlined" aria-hidden="true">
                  {dark ? 'light_mode' : 'dark_mode'}
                </span>
              </button>
              <NotificationBell />
              <div className="user-info">
                <div className="user-avatar">{user?.username?.[0]?.toUpperCase() || 'U'}</div>
                <span className="user-name">{user?.username}</span>
                <span className="badge-role" data-rol={user?.rol}>{user?.rol}</span>
              </div>
              <button
                type="button"
                onClick={handleLogout}
                className="topbar-logout-btn"
                aria-label="Cerrar sesión"
                title="Cerrar sesión"
              >
                <span className="material-symbols-outlined" aria-hidden="true">
                  logout
                </span>
              </button>
            </div>
          </header>

          <main id="main-content" className="content-area">
            <ErrorBoundary routeKey={location.pathname}>
            <Suspense
              fallback={
                <div className="table-container p-8 text-center text-on-surface-variant">
                  Cargando...
                </div>
              }
            >
              <Outlet />
            </Suspense>
            </ErrorBoundary>
          </main>
        </div>
      </div>
    </div>
  );
}
