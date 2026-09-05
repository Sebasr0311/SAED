import { Suspense, useEffect, useMemo, useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  AlertTriangle,
  BarChart3,
  Building,
  Building2,
  Calculator,
  Calendar,
  Car,
  CheckCircle2,
  ChevronRight,
  ClipboardCheck,
  CreditCard,
  DollarSign,
  FileCheck,
  FileText,
  Hammer,
  Headphones,
  History,
  Home,
  LayoutDashboard,
  LogOut,
  Mail,
  Megaphone,
  Menu,
  Moon,
  Package,
  PanelLeft,
  PanelLeftClose,
  PlusCircle,
  QrCode,
  Receipt,
  Scale,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Sun,
  TrendingUp,
  User,
  UserCheck,
  UserCircle,
  UserCog,
  UserPlus,
  Users,
  Wallet,
  Wrench,
  X,
} from 'lucide-react';
import { useAuth } from '../../lib/AuthContext.jsx';
import { getInitialTheme, applyTheme, persistTheme } from '../../lib/theme.js';
import ErrorBoundary from '../ErrorBoundary.jsx';
import NotificationBell from '../ui/NotificationBell.jsx';
import BreadcrumbNav from '../ui/Breadcrumb.jsx';
import TenantSwitcher from './TenantSwitcher.jsx';
import { cn } from '../../lib/utils.js';

/**
 * Navegación agrupada por relación funcional, por rol (SAED 2.0).
 * Las rutas y permisos NO cambian: solo se moderniza la presentación visual.
 */
const NAV_BY_ROLE = {
  SUPERADMIN: [
    {
      id: 'inicio',
      label: 'Inicio',
      icon: 'home',
      items: [{ path: '/superadmin/dashboard', label: 'Dashboard Plataforma', icon: 'dashboard' }],
    },
    {
      id: 'plataforma',
      label: 'Plataforma SaaS',
      icon: 'domain',
      items: [
        { path: '/superadmin/organizaciones', label: 'Organizaciones', icon: 'domain' },
        { path: '/superadmin/propiedades', label: 'Propiedades Globales', icon: 'apartment' },
        { path: '/superadmin/planes', label: 'Planes SaaS', icon: 'pricing_plan' },
        { path: '/superadmin/membresias', label: 'Membresías', icon: 'card_membership' },
      ],
    },
    {
      id: 'seguridad',
      label: 'Seguridad y Control',
      icon: 'security',
      items: [
        { path: '/superadmin/administradores', label: 'Administradores SAED', icon: 'admin_panel_settings' },
        { path: '/superadmin/auditoria', label: 'Pista de Auditoría', icon: 'policy' },
      ],
    },
    {
      id: 'analitica',
      label: 'Analítica',
      icon: 'assessment',
      items: [
        { path: '/superadmin/metricas', label: 'Métricas Globales', icon: 'analytics' },
      ],
    },
  ],
  ADMIN_ORGANIZACION: [
    {
      id: 'inicio',
      label: 'Inicio',
      icon: 'home',
      items: [{ path: '/org/dashboard', label: 'Dashboard', icon: 'dashboard' }],
    },
    {
      id: 'organizacion',
      label: 'Organización',
      icon: 'domain',
      items: [
        { path: '/org/organizacion', label: 'Mi Organización', icon: 'domain' },
        { path: '/org/propiedades', label: 'Propiedades', icon: 'apartment' },
        { path: '/org/admins', label: 'Administradores', icon: 'admin_panel_settings' },
      ],
    },
    {
      id: 'comercial',
      label: 'Plan y Control',
      icon: 'pricing_plan',
      items: [
        { path: '/org/plan', label: 'Plan y Suscripción', icon: 'card_membership' },
        { path: '/org/auditoria', label: 'Pista de Auditoría', icon: 'policy' },
      ],
    },
  ],
  ADMIN_PROPIEDAD: [
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
        { path: '/personas', label: 'Personas', icon: 'person' },
        { path: '/residentes', label: 'Residentes', icon: 'groups' },
        { path: '/unidades', label: 'Unidades', icon: 'apartment' },
        { path: '/contratos', label: 'Contratos', icon: 'description' },
        { path: '/usuarios', label: 'Usuarios', icon: 'manage_accounts' },
        { path: '/roles-asignaciones', label: 'Roles y Asignaciones', icon: 'admin_panel_settings' },
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
        { path: '/cartera', label: 'Cartera', icon: 'account_balance_wallet' },
        { path: '/presupuestos', label: 'Presupuestos', icon: 'calculate' },
        { path: '/gastos', label: 'Gastos', icon: 'receipt_long' },
        { path: '/flujo-caja', label: 'Flujo de Caja', icon: 'show_chart' },
        { path: '/conciliaciones', label: 'Conciliaciones', icon: 'fact_check' },
        { path: '/paz-y-salvos', label: 'Paz y Salvos', icon: 'verified' },
        { path: '/ganancias', label: 'Ganancias', icon: 'trending_up' },
      ],
    },
    {
      id: 'mantenimiento-control',
      label: 'Mantenimiento y Control',
      icon: 'build',
      items: [
        { path: '/mantenimiento-admin', label: 'Mantenimientos', icon: 'build' },
        { path: '/asambleas-admin', label: 'Asambleas', icon: 'groups' },
        { path: '/polizas-admin', label: 'Pólizas y Seguros', icon: 'policy' },
        { path: '/emergencias-admin', label: 'Planes Emergencia', icon: 'emergency' },
        { path: '/sanciones-admin', label: 'Sanciones', icon: 'gavel' },
        { path: '/obras-admin', label: 'Obras y Remodelaciones', icon: 'construction' },
        { path: '/incidentes-admin', label: 'Incidentes', icon: 'warning' },
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
      items: [
        { path: '/quejas-admin', label: 'PQRS (Tickets)', icon: 'support_agent' },
        { path: '/reservas-admin', label: 'Reservas Z.C.', icon: 'event' },
      ],
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
        { path: '/res-quejas', label: 'PQRS', icon: 'support_agent' },
        { path: '/res-reservas', label: 'Zonas Comunes', icon: 'event' },
        { path: '/res-sanciones', label: 'Sanciones', icon: 'gavel' },
        { path: '/res-obras', label: 'Mis Obras', icon: 'construction' },
        { path: '/res-incidentes', label: 'Mis Incidentes', icon: 'warning' },
      ],
    },
  ],
};

const ICON_COMPONENT_MAP = {
  home: Home,
  dashboard: LayoutDashboard,
  domain: Building2,
  apartment: Building,
  pricing_plan: CreditCard,
  card_membership: ShieldCheck,
  security: Shield,
  admin_panel_settings: ShieldAlert,
  policy: FileCheck,
  assessment: BarChart3,
  analytics: TrendingUp,
  person: User,
  groups: Users,
  description: FileText,
  manage_accounts: UserCog,
  how_to_reg: UserCheck,
  history: History,
  inventory_2: Package,
  local_parking: Car,
  qr_code_scanner: QrCode,
  payments: DollarSign,
  account_balance_wallet: Wallet,
  calculate: Calculator,
  receipt_long: Receipt,
  show_chart: TrendingUp,
  fact_check: ClipboardCheck,
  verified: CheckCircle2,
  trending_up: TrendingUp,
  build: Wrench,
  construction: Hammer,
  emergency: AlertTriangle,
  warning: AlertTriangle,
  gavel: Scale,
  campaign: Megaphone,
  notifications: Megaphone,
  support_agent: Headphones,
  event: Calendar,
  account_circle: UserCircle,
  group_add: UserPlus,
  add_circle: PlusCircle,
  mail: Mail,
};

function renderNavIcon(iconKey, className = 'h-4 w-4') {
  const IconComponent = ICON_COMPONENT_MAP[iconKey] || Building;
  return <IconComponent className={className} aria-hidden="true" />;
}

const COLLAPSED_KEY = 'saed_sidebar_collapsed';

/** La ruta activa es la ruta exacta o un prefijo de segmento (evita /visitas vs /historial-visitas). */
function isItemActive(pathname, path) {
  return pathname === path || pathname.startsWith(path + '/');
}

export default function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const groups = useMemo(() => {
    return NAV_BY_ROLE[user?.rol] || NAV_BY_ROLE[user?.rol?.toUpperCase()] || [];
  }, [user?.rol]);
  const allItems = useMemo(() => groups.flatMap((g) => g.items), [groups]);

  // Tema: localStorage gana; si no hay preferencia, se sigue la del sistema.
  const [dark, setDark] = useState(() => getInitialTheme() === 'dark');
  useEffect(() => {
    applyTheme(dark ? 'dark' : 'light');
  }, [dark]);

  // Checkout de pago Wompi: encuadre global conservado intacto.
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
    const target = document.getElementById('root') || document.body;
    obs.observe(target, { childList: true, subtree: true });
    return () => {
      obs.disconnect();
      frameObserver?.disconnect();
      currentHost?.classList.remove('wompi-modal-host');
    };
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
      return false;
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

  const openGroupsDisplay = useMemo(() => {
    if (!activeGroup) return openGroups;
    if (openGroups.has(activeGroup.id)) return openGroups;
    const next = new Set(openGroups);
    next.add(activeGroup.id);
    return next;
  }, [openGroups, activeGroup]);

  const currentTitle = allItems.find((i) => isItemActive(location.pathname, i.path))?.label || 'SAED';

  // Titulo de pestaña dinámico
  useEffect(() => {
    document.title = currentTitle === 'SAED' ? 'SAED — Administración Residencial' : `${currentTitle} — SAED`;
  }, [currentTitle]);

  // Cerrar el drawer móvil con Escape
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

  // Construcción de breadcrumbs contextuales
  const breadcrumbs = useMemo(() => {
    const homePath = groups[0]?.items[0]?.path || '/';
    const items = [{ label: 'Inicio', href: homePath }];
    if (activeGroup && activeGroup.label !== 'Inicio') {
      items.push({ label: activeGroup.label });
    }
    if (
      currentTitle &&
      currentTitle !== 'Inicio' &&
      currentTitle !== 'Dashboard' &&
      currentTitle !== 'Dashboard Plataforma' &&
      currentTitle !== 'Mi Panel'
    ) {
      items.push({ label: currentTitle });
    }
    return items;
  }, [activeGroup, currentTitle, groups]);

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col antialiased">
      {/* Skip link accesible (WCAG 2.4.1) */}
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-[100] focus:px-4 focus:py-2 focus:bg-primary focus:text-primary-foreground focus:rounded-lg focus:shadow-lg focus:outline-none focus:ring-2 focus:ring-ring font-medium text-xs"
      >
        Saltar al contenido principal
      </a>

      {/* Drawer overlay en móvil */}
      <div
        className={cn(
          'fixed inset-0 z-40 bg-slate-900/60 backdrop-blur-sm transition-opacity duration-300 lg:hidden',
          mobileOpen ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
        )}
        onClick={() => setMobileOpen(false)}
        aria-hidden="true"
      />

      {/* SIDEBAR */}
      <aside
        className={cn(
          'fixed top-0 bottom-0 left-0 z-50 flex flex-col bg-[#0A1628] text-slate-200 border-r border-[#1E293B] transition-all duration-300 ease-in-out select-none shadow-2xl lg:shadow-none',
          // Mobile: drawer deslizante
          mobileOpen ? 'translate-x-0 w-72' : '-translate-x-full lg:translate-x-0',
          // Desktop: expandido o colapsado
          collapsed && !mobileOpen ? 'lg:w-[72px] group/sidebar' : 'lg:w-64'
        )}
      >
        {/* Encabezado / Logo */}
        <div className="h-16 flex items-center justify-between px-4 border-b border-[#1E293B]/80 flex-shrink-0">
          <div
            className={cn(
              'flex items-center gap-3 transition-opacity duration-200',
              collapsed && !mobileOpen ? 'lg:justify-center lg:w-full' : ''
            )}
          >
            <div className="h-9 w-9 rounded-xl bg-gradient-to-tr from-primary to-blue-500 p-0.5 shadow-md flex-shrink-0 flex items-center justify-center">
              <img
                src={`${import.meta.env.BASE_URL}imagenes/saed_logo_emblem_only.png`}
                alt="SAED"
                className="w-7 h-7 object-contain"
              />
            </div>
            {(!collapsed || mobileOpen) && (
              <div className="flex flex-col min-w-0">
                <div className="flex items-center gap-1.5">
                  <span className="font-bold text-sm tracking-tight text-white">SAED</span>
                  <span className="px-1.5 py-0.2 text-[10px] font-bold rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                    2.0
                  </span>
                </div>
                <span className="text-[11px] text-slate-400 truncate">
                  {user?.rol === 'SUPERADMIN'
                    ? 'Plataforma SaaS'
                    : user?.rol === 'ADMIN_ORGANIZACION'
                    ? 'Gestión Organizacional'
                    : 'Administración Residencial'}
                </span>
              </div>
            )}
          </div>

          {/* Botón de cierre en drawer móvil */}
          <button
            type="button"
            className="lg:hidden p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800/80 transition-colors"
            onClick={() => setMobileOpen(false)}
            aria-label="Cerrar menú"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        {/* Selector de Tenant en drawer móvil */}
        {mobileOpen && (
          <div className="px-3 py-2 border-b border-[#1E293B]/60 lg:hidden">
            <TenantSwitcher className="w-full justify-between" />
          </div>
        )}

        {/* Área de Navegación */}
        <nav
          className="flex-1 overflow-y-auto px-3 py-4 space-y-3 custom-scrollbar"
          aria-label="Navegación principal"
        >
          {groups.map((group) => {
            const isOpen = openGroupsDisplay.has(group.id);
            const isGroupActive = activeGroup?.id === group.id;

            return (
              <div key={group.id} className="space-y-1">
                {/* Botón de grupo / categoría */}
                {(!collapsed || mobileOpen) ? (
                  <button
                    type="button"
                    onClick={() => toggleGroup(group.id)}
                    className={cn(
                      'w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg text-xs font-semibold tracking-wider text-slate-400 hover:text-slate-200 transition-colors group/header',
                      isGroupActive && 'text-slate-200'
                    )}
                    aria-expanded={isOpen}
                  >
                    <div className="flex items-center gap-2">
                      {renderNavIcon(group.icon, 'h-3.5 w-3.5 text-slate-400 group-hover/header:text-slate-200')}
                      <span className="uppercase text-[11px] tracking-wider">{group.label}</span>
                    </div>
                    <ChevronRight
                      className={cn(
                        'h-3.5 w-3.5 text-slate-400 transition-transform duration-200',
                        isOpen && 'rotate-90'
                      )}
                      aria-hidden="true"
                    />
                  </button>
                ) : (
                  // Ícono indicador en modo rail colapsado
                  <div
                    className="flex justify-center py-1 text-slate-400"
                    title={group.label}
                  >
                    {renderNavIcon(group.icon, 'h-4 w-4')}
                  </div>
                )}

                {/* Sub-items del grupo */}
                {(!collapsed || mobileOpen || isOpen) && (
                  <div
                    className={cn(
                      'space-y-0.5 pl-1',
                      collapsed && !mobileOpen && 'lg:pl-0'
                    )}
                  >
                    {group.items.map((item) => {
                      const active = isItemActive(location.pathname, item.path);

                      return (
                        <button
                          key={item.path}
                          type="button"
                          onClick={() => {
                            navigate(item.path);
                            setMobileOpen(false);
                          }}
                          className={cn(
                            'relative w-full flex items-center gap-3 px-3 py-2 rounded-lg text-xs font-medium transition-all group/item',
                            collapsed && !mobileOpen ? 'lg:justify-center lg:px-2' : '',
                            active
                              ? 'bg-primary/20 text-white font-semibold shadow-sm'
                              : 'text-slate-300 hover:text-white hover:bg-slate-800/60'
                          )}
                          title={item.label}
                          aria-current={active ? 'page' : undefined}
                        >
                          {/* Indicador de activo */}
                          {active && (
                            <span
                              className="absolute left-0 top-1.5 bottom-1.5 w-1 rounded-r-full bg-primary"
                              aria-hidden="true"
                            />
                          )}

                          <span
                            className={cn(
                              'flex-shrink-0 transition-colors',
                              active ? 'text-primary' : 'text-slate-400 group-hover/item:text-slate-200'
                            )}
                          >
                            {renderNavIcon(item.icon, 'h-4 w-4')}
                          </span>

                          {(!collapsed || mobileOpen) && (
                            <span className="truncate flex-1 text-left">{item.label}</span>
                          )}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            );
          })}
        </nav>

        {/* Pie del Sidebar / Usuario y Logout */}
        <div className="p-3 border-t border-[#1E293B]/80 flex-shrink-0 bg-[#07101E]/60 space-y-2">
          <div
            className={cn(
              'flex items-center gap-3 p-2 rounded-xl bg-slate-800/40 border border-slate-700/40',
              collapsed && !mobileOpen ? 'lg:justify-center lg:p-1.5' : ''
            )}
          >
            <div className="h-8 w-8 rounded-full bg-primary/20 text-primary border border-primary/30 font-bold flex items-center justify-center text-xs flex-shrink-0">
              {user?.username?.[0]?.toUpperCase() || 'U'}
            </div>
            {(!collapsed || mobileOpen) && (
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-white truncate leading-tight">
                  {user?.username || 'Usuario'}
                </p>
                <span className="inline-block text-[10px] font-medium text-slate-400 uppercase tracking-wider mt-0.5 truncate">
                  {user?.rol || 'Rol'}
                </span>
              </div>
            )}
          </div>

          <button
            type="button"
            onClick={handleLogout}
            className={cn(
              'w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs font-semibold text-slate-300 hover:text-rose-400 hover:bg-rose-500/10 transition-colors group/logout',
              collapsed && !mobileOpen ? 'lg:justify-center lg:px-2' : ''
            )}
            title="Cerrar sesión"
            aria-label="Cerrar sesión"
          >
            <LogOut className="h-4 w-4 text-slate-400 group-hover/logout:text-rose-400 transition-colors" aria-hidden="true" />
            {(!collapsed || mobileOpen) && <span>Cerrar sesión</span>}
          </button>
        </div>
      </aside>

      {/* CONTENIDO Y TOPBAR */}
      <div
        className={cn(
          'flex-1 flex flex-col min-w-0 transition-all duration-300 ease-in-out',
          collapsed ? 'lg:pl-[72px]' : 'lg:pl-64'
        )}
      >
        {/* TOPBAR */}
        <header className="sticky top-0 z-30 h-16 border-b border-border/80 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80 px-4 sm:px-6 lg:px-8 flex items-center justify-between shadow-xs">
          {/* Lado izquierdo: Toggles + Breadcrumbs en Desktop / Brand en Móvil */}
          <div className="flex items-center gap-3 min-w-0">
            {/* Toggle móvil */}
            <button
              type="button"
              className="lg:hidden inline-flex items-center justify-center h-10 w-10 min-h-[44px] min-w-[44px] rounded-lg border border-border/70 text-muted-foreground hover:text-foreground hover:bg-muted/60 transition-colors"
              onClick={() => setMobileOpen(true)}
              aria-label="Abrir menú"
            >
              <Menu className="h-5 w-5" aria-hidden="true" />
            </button>

            {/* Marca en móvil (sección 15: [☰] SAED 2.0 ... [🔔]) */}
            <div className="sm:hidden flex items-center gap-2">
              <span className="font-bold text-sm tracking-tight text-foreground">SAED</span>
              <span className="px-1.5 py-0.2 text-[10px] font-bold rounded bg-emerald-500/20 text-emerald-600 dark:text-emerald-400 border border-emerald-500/30">
                2.0
              </span>
            </div>

            {/* Toggle desktop collapse */}
            <button
              type="button"
              className="hidden lg:inline-flex items-center justify-center h-9 w-9 rounded-lg border border-border/70 text-muted-foreground hover:text-foreground hover:bg-muted/60 transition-colors"
              onClick={toggleCollapsed}
              aria-label={collapsed ? 'Expandir menú lateral' : 'Colapsar menú lateral'}
              title={collapsed ? 'Expandir menú lateral' : 'Colapsar menú lateral'}
            >
              {collapsed ? (
                <PanelLeft className="h-4 w-4" aria-hidden="true" />
              ) : (
                <PanelLeftClose className="h-4 w-4" aria-hidden="true" />
              )}
            </button>

            {/* Breadcrumbs en Desktop */}
            <div className="hidden sm:block min-w-0">
              <BreadcrumbNav items={breadcrumbs} className="text-xs" />
            </div>
          </div>

          {/* Lado derecho: Tenant + Theme + Notif + User */}
          <div className="flex items-center gap-2 sm:gap-3 flex-shrink-0">
            {/* Multi-tenant Context (Desktop / Tablet) */}
            <div className="hidden sm:inline-flex">
              <TenantSwitcher />
            </div>

            {/* Toggle de Tema (Desktop / Tablet) */}
            <button
              type="button"
              onClick={toggleTheme}
              className="hidden sm:inline-flex items-center justify-center h-10 w-10 min-h-[44px] min-w-[44px] rounded-lg border border-border/70 text-muted-foreground hover:text-foreground hover:bg-muted/60 transition-colors"
              aria-label={dark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              title={dark ? 'Modo claro' : 'Modo oscuro'}
            >
              {dark ? (
                <Sun className="h-4 w-4 text-amber-500" aria-hidden="true" />
              ) : (
                <Moon className="h-4 w-4 text-slate-600" aria-hidden="true" />
              )}
            </button>

            {/* Campana de Notificaciones (Siempre visible) */}
            <NotificationBell />

            {/* Pastilla de Usuario en Desktop */}
            <div className="hidden md:flex items-center gap-2 pl-2 border-l border-border/60">
              <div className="h-8 w-8 rounded-full bg-primary/10 border border-primary/20 text-primary font-bold flex items-center justify-center text-xs">
                {user?.username?.[0]?.toUpperCase() || 'U'}
              </div>
              <div className="flex flex-col text-left">
                <span className="text-xs font-semibold text-foreground leading-none">
                  {user?.username}
                </span>
                <span className="text-[10px] text-muted-foreground font-medium uppercase mt-0.5">
                  {user?.rol}
                </span>
              </div>
            </div>

            {/* Botón Logout Rápido (Desktop) */}
            <button
              type="button"
              onClick={handleLogout}
              className="hidden sm:inline-flex items-center justify-center h-10 w-10 min-h-[44px] min-w-[44px] rounded-lg border border-border/70 text-muted-foreground hover:text-rose-600 hover:bg-rose-500/10 hover:border-rose-300 dark:hover:border-rose-900 transition-colors"
              aria-label="Cerrar sesión"
              title="Cerrar sesión"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
            </button>
          </div>
        </header>

        {/* ÁREA DE CONTENIDO */}
        <main
          id="main-content"
          className="flex-1 min-w-0 bg-background-subtle min-h-[calc(100vh-64px)] p-4 sm:p-6 lg:p-8"
        >
          <ErrorBoundary routeKey={location.pathname}>
            <Suspense
              fallback={
                <div className="flex items-center justify-center min-h-[300px] text-muted-foreground text-sm font-medium">
                  <div className="h-6 w-6 border-2 border-primary border-t-transparent rounded-full animate-spin mr-3" />
                  Cargando vista...
                </div>
              }
            >
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>
    </div>
  );
}
