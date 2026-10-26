import { Suspense, useEffect, useMemo, useRef, useState } from 'react';
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
  Key,
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
import { normalizeRole } from '../../lib/access.js';
import { getInitialTheme, applyTheme, persistTheme } from '../../lib/theme.js';
import ErrorBoundary from '../ErrorBoundary.jsx';
import NotificationBell from '../ui/NotificationBell.jsx';
import BreadcrumbNav from '../ui/Breadcrumb.jsx';
import TenantSwitcher from './TenantSwitcher.jsx';
import ChangePasswordModal from '../ui/ChangePasswordModal.jsx';
import LoadingState from '../ui/LoadingState.jsx';
import { cn } from '../../lib/utils.js';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../ui/dialog.tsx';
import { Button } from '../ui/button.tsx';

/**
 * Etiquetas legibles por rol para evitar guiones bajos crudos en la interfaz.
 */
export const ROLE_DISPLAY_LABELS = {
  RESIDENTE_CONVIVENCIA: 'Residente Conviviente',
  RESIDENTE: 'Residente Titular',
  PROPIETARIO: 'Propietario',
  ADMIN_EDIFICIO: 'Administrador',
  ADMIN_PROPIEDAD: 'Administrador',
  ADMIN_ORGANIZACION: 'Admin Organización',
  SUPER_ADMIN: 'Super Admin',
  SUPERADMIN: 'Super Admin',
  PORTERO: 'Portería & Seguridad',
  CONTADOR: 'Contabilidad',
  AUDITOR: 'Auditoría',
  COMITE_CONVIVENCIA: 'Comité de Convivencia',
};

function formatRoleLabel(rol) {
  if (!rol) return 'Rol';
  const upper = String(rol).toUpperCase();
  return ROLE_DISPLAY_LABELS[rol] || ROLE_DISPLAY_LABELS[upper] || String(rol).replace(/_/g, ' ');
}

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
      items: [{ path: '/superadmin/dashboard', label: 'Dashboard Global', icon: 'dashboard' }],
    },
    {
      id: 'plataforma',
      label: 'Plataforma SaaS',
      icon: 'domain',
      items: [
        { path: '/superadmin/organizaciones', label: 'Organizaciones', icon: 'domain' },
        { path: '/superadmin/propiedades', label: 'Propiedades Globales', icon: 'apartment' },
        { path: '/superadmin/planes', label: 'Planes SaaS', icon: 'credit_card' },
        { path: '/superadmin/membresias', label: 'Membresías', icon: 'card_membership' },
        { path: '/superadmin/onboarding', label: 'Suscripciones & Onboarding', icon: 'how_to_reg' },
      ],
    },
    {
      id: 'seguridad',
      label: 'Seguridad y Control',
      icon: 'security',
      items: [
        { path: '/superadmin/administradores', label: 'Administradores SAED', icon: 'admin_panel_settings' },
        { path: '/superadmin/auditoria', label: 'Auditoría Global', icon: 'policy' },
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
      items: [{ path: '/org/dashboard', label: 'Dashboard Organizacional', icon: 'dashboard' }],
    },
    {
      id: 'organizacion',
      label: 'Organización',
      icon: 'domain',
      items: [
        { path: '/org/organizacion', label: 'Mi Organización', icon: 'domain' },
        { path: '/org/propiedades', label: 'Propiedades', icon: 'apartment' },
        { path: '/org/admins', label: 'Administradores', icon: 'admin_panel_settings' },
        { path: '/org/plantillas', label: 'Plantillas Contratos', icon: 'description' },
      ],
    },
    {
      id: 'supervision',
      label: 'Supervisión',
      icon: 'assessment',
      items: [
        { path: '/org/cartera', label: 'Cartera Consolidada', icon: 'account_balance_wallet' },
        { path: '/org/gastos', label: 'Gastos y Facturas', icon: 'receipt_long' },
        { path: '/org/comunicaciones', label: 'Comunicaciones', icon: 'forum' },
        { path: '/org/reportes', label: 'Reportes Gerenciales', icon: 'description' },
        { path: '/org/analitica', label: 'Analítica Comparativa', icon: 'analytics' },
        { path: '/polizas', label: 'Pólizas y Seguros', icon: 'policy' },
        { path: '/emergencias', label: 'Planes Emergencia', icon: 'emergency' },
      ],
    },
    {
      id: 'comercial',
      label: 'Plan y Control',
      icon: 'credit_card',
      items: [
        { path: '/org/plan', label: 'Plan y Suscripción', icon: 'card_membership' },
        { path: '/org/auditoria', label: 'Auditoría Organizacional', icon: 'policy' },
      ],
    },
  ],
  ADMIN_PROPIEDAD: [
    {
      id: 'inicio',
      label: 'Inicio',
      icon: 'home',
      items: [{ path: '/dashboard', label: 'Dashboard Operativo', icon: 'dashboard' }],
    },
    {
      id: 'administracion',
      label: 'Administración',
      icon: 'domain',
      items: [
        { path: '/residentes', label: 'Residentes', icon: 'groups' },
        { path: '/empleados', label: 'Empleados', icon: 'manage_accounts' },
        { path: '/unidades', label: 'Unidades', icon: 'apartment' },
        { path: '/contratos', label: 'Contratos Arriendo', icon: 'description' },
        { path: '/coarrendatarios', label: 'Coarrendatarios', icon: 'group_add' },
        { path: '/contratos-proveedor', label: 'Contratos Proveedores', icon: 'handshake' },
        { path: '/reportes', label: 'Reportes', icon: 'assessment' },
      ],
    },
    {
      id: 'operacion',
      label: 'Accesos y Operación',
      icon: 'how_to_reg',
      items: [
        { path: '/visitas', label: 'Visitas', icon: 'how_to_reg' },
        { path: '/historial-visitas', label: 'Historial Visitas', icon: 'history' },
        { path: '/porterias', label: 'Puntos de Portería', icon: 'door_sliding' },
        { path: '/parqueaderos', label: 'Parqueaderos', icon: 'local_parking' },
        { path: '/paquetes-admin', label: 'Paquetes', icon: 'inventory_2' },
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
        { path: '/facturacion', label: 'Facturación', icon: 'request_quote' },
        { path: '/gastos', label: 'Gastos', icon: 'receipt_long' },
        { path: '/flujo-caja', label: 'Flujo de Caja', icon: 'show_chart' },
        { path: '/conciliaciones', label: 'Conciliaciones', icon: 'fact_check' },
        { path: '/paz-y-salvos', label: 'Paz y Salvos', icon: 'verified' },
      ],
    },
    {
      id: 'comunidad',
      label: 'Comunidad',
      icon: 'support_agent',
      items: [
        { path: '/quejas-admin', label: 'PQRS (Atención)', icon: 'support_agent' },
        { path: '/sanciones-admin', label: 'Sanciones', icon: 'gavel' },
        { path: '/reservas-admin', label: 'Reservas Z.C.', icon: 'event' },
      ],
    },
    {
      id: 'mantenimiento-control',
      label: 'Mantenimiento y Control',
      icon: 'build',
      items: [
        { path: '/mantenimientos', label: 'Mantenimientos', icon: 'build' },
        { path: '/activos', label: 'Activos y Equipos', icon: 'inventory' },
        { path: '/obras-admin', label: 'Obras y Remodelaciones', icon: 'construction' },
        { path: '/polizas', label: 'Pólizas y Seguros', icon: 'policy' },
        { path: '/emergencias', label: 'Planes Emergencia', icon: 'emergency' },
        { path: '/consumos', label: 'Consumos y Sostenibilidad', icon: 'energy_savings_leaf' },
        { path: '/automatizaciones', label: 'Automatizaciones ECA', icon: 'smart_toy' },
        { path: '/incidentes-admin', label: 'Incidentes', icon: 'warning' },
        { path: '/asambleas', label: 'Asambleas', icon: 'groups' },
      ],
    },
    {
      id: 'comunicacion',
      label: 'Comunicación',
      icon: 'campaign',
      items: [
        { path: '/comunicaciones', label: 'Centro de Comunicaciones', icon: 'campaign' },
      ],
    },
    {
      id: 'documentos',
      label: 'Documentos',
      icon: 'folder',
      items: [
        { path: '/documentos', label: 'Documentación Oficial', icon: 'folder' },
        { path: '/reglamentos', label: 'Reglamentos y Normativa', icon: 'gavel' },
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
  PROPIETARIO: [
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
        { path: '/res-perfil', label: 'Mi Perfil e Inmuebles', icon: 'person' },
        { path: '/res-convivientes', label: 'Mis Convivientes', icon: 'groups' },
      ],
    },
    {
      id: 'inmueble',
      label: 'Mi Inmueble',
      icon: 'apartment',
      items: [
        { path: '/res-cuotas', label: 'Mis Cuotas y Pagos', icon: 'credit_card' },
      ],
    },
    {
      id: 'documentos',
      label: 'Documentación Oficial',
      icon: 'folder',
      items: [
        { path: '/res-asambleas', label: 'Asambleas y Quórum', icon: 'groups' },
        { path: '/res-documentos', label: 'Documentos', icon: 'description' },
        { path: '/res-reglamentos', label: 'Reglamentos y Normativa', icon: 'gavel' },
        { path: '/res-seguros', label: 'Pólizas de Seguros', icon: 'shield' },
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
        { path: '/res-convivientes', label: 'Mis Convivientes', icon: 'groups' },
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
        { path: '/res-visitas', label: 'Visitas', icon: 'how_to_reg' },
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
        { path: '/res-asambleas', label: 'Asambleas Copropietarios', icon: 'groups' },
        { path: '/res-documentos', label: 'Documentos', icon: 'description' },
        { path: '/res-reglamentos', label: 'Reglamentos y Normativa', icon: 'gavel' },
        { path: '/res-seguros', label: 'Pólizas de Seguros', icon: 'shield' },
      ],
    },
  ],
  RESIDENTE_CONVIVENCIA: [
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
      items: [{ path: '/res-perfil', label: 'Mi Perfil', icon: 'person' }],
    },
    {
      id: 'visitas',
      label: 'Visitas',
      icon: 'how_to_reg',
      items: [
        { path: '/res-visitas', label: 'Visitas', icon: 'how_to_reg' },
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
        { path: '/res-incidentes', label: 'Mis Incidentes', icon: 'warning' },
        { path: '/res-sanciones', label: 'Mis sanciones', icon: 'gavel' },
        { path: '/res-asambleas', label: 'Asambleas Copropietarios', icon: 'groups' },
        { path: '/res-documentos', label: 'Documentos', icon: 'description' },
        { path: '/res-reglamentos', label: 'Reglamentos y Normativa', icon: 'gavel' },
        { path: '/res-seguros', label: 'Pólizas de Seguros', icon: 'shield' },
      ],
    },
  ],
};

const ICON_COMPONENT_MAP = {
  home: Home,
  dashboard: LayoutDashboard,
  domain: Building2,
  apartment: Building,
  credit_card: CreditCard,
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
const DESKTOP_QUERY = '(min-width: 1024px)';
const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

// Active nav item: the Índigo "piece" (light) / luminous Índigo glass (dark).
const NAV_ACTIVE_PIECE =
  'font-semibold text-white bg-[#5267DA] ring-1 ring-inset ring-white/20 shadow-[inset_0_1px_0_rgba(255,255,255,0.18),0_8px_14px_-10px_rgba(0,0,0,0.7)] dark:bg-[rgba(98,116,232,0.72)] dark:ring-[#A3B0FF]/70';
const HEADER_ICON_BUTTON =
  'items-center justify-center h-10 w-10 min-h-[44px] min-w-[44px] rounded-md border border-border bg-surface text-text-secondary hover:text-on-surface hover:border-keyline/50 transition-colors';
const USER_PILL = 'items-center gap-2.5 h-11 pl-1.5 pr-3 rounded-md border border-border bg-surface text-left transition-colors';
const USER_PILL_AVATAR =
  'app-stamp h-8 w-8 flex-shrink-0 rounded-[4px] bg-[#EEE8FF] text-[#1D2942] dark:bg-[#1f2a4d] dark:text-[#C2CAFF] flex items-center justify-center text-[13px]';

/** La ruta activa es la ruta exacta o un prefijo de segmento (evita /visitas vs /historial-visitas). */
function isItemActive(pathname, path) {
  return pathname === path || pathname.startsWith(path + '/');
}

export default function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const displayRole = useMemo(() => formatRoleLabel(user?.rol), [user?.rol]);

  const groups = useMemo(() => {
    let role = normalizeRole(user?.rol);
    if (
      role === 'PROPIETARIO' &&
      (user?.tipoResidente === 'PROPIETARIO_RESIDENTE' ||
        user?.tipoRelacion === 'PROPIETARIO_RESIDENTE' ||
        user?.tipoResidente === 'TITULAR' ||
        user?.tipoRelacion === 'TITULAR' ||
        user?.idUnidad != null ||
        user?.identificadorUnidad != null ||
        (user?.tipoResidente && user?.tipoResidente !== 'PROPIETARIO_NO_RESIDENTE'))
    ) {
      role = 'RESIDENTE';
    }
    return NAV_BY_ROLE[role] || NAV_BY_ROLE[user?.rol] || NAV_BY_ROLE[user?.rol?.toUpperCase()] || [];
  }, [user?.rol, user?.tipoResidente, user?.tipoRelacion, user?.idUnidad, user?.identificadorUnidad]);
  const allItems = useMemo(() => groups.flatMap((g) => g.items), [groups]);

  // Tema: localStorage gana; por defecto identidad clara SAED 2.0.
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

  const [showLogoutModal, setShowLogoutModal] = useState(false);
  const [showChangePasswordModal, setShowChangePasswordModal] = useState(false);

  function toggleTheme() {
    setDark((prev) => {
      const next = !prev;
      const nextTheme = next ? 'dark' : 'light';
      applyTheme(nextTheme);
      persistTheme(nextTheme);
      try {
        localStorage.setItem('saed_landing_theme', nextTheme);
      } catch {
        /* almacenamiento no disponible */
      }
      return next;
    });
  }

  // Barra lateral expandida por defecto en desktop; el usuario puede colapsarla.
  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem(COLLAPSED_KEY) === 'true';
    } catch {
      return false;
    }
  });
  const [mobileOpen, setMobileOpen] = useState(false);

  // Todos los grupos abiertos por defecto para acceso directo a todos los módulos.
  const [openGroups, setOpenGroups] = useState(() => {
    const initial = new Set();
    for (const group of groups) {
      initial.add(group.id);
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

  // Drawer móvil: el aside es diálogo modal solo por debajo de 1024px.
  const asideRef = useRef(null);
  const menuButtonRef = useRef(null);
  const [isDesktop, setIsDesktop] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(DESKTOP_QUERY).matches
  );
  useEffect(() => {
    const mq = window.matchMedia(DESKTOP_QUERY);
    function onChange(e) {
      setIsDesktop(e.matches);
      if (e.matches) setMobileOpen(false);
    }
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  const drawerMode = !isDesktop && mobileOpen;

  // Fuera de pantalla en móvil, la barra lateral no recibe foco ni lectores de pantalla.
  useEffect(() => {
    if (asideRef.current) asideRef.current.inert = !isDesktop && !mobileOpen;
  }, [isDesktop, mobileOpen]);

  // Trampa de foco + bloqueo de scroll mientras el drawer está abierto; al cerrar vuelve al botón de menú.
  useEffect(() => {
    if (!drawerMode) return undefined;
    const aside = asideRef.current;
    if (!aside) return undefined;
    const getFocusables = () =>
      Array.from(aside.querySelectorAll(FOCUSABLE_SELECTOR)).filter((el) => el.offsetParent !== null);
    const raf = requestAnimationFrame(() => getFocusables()[0]?.focus());
    function onKeyDown(e) {
      if (e.key !== 'Tab') return;
      const items = getFocusables();
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
    aside.addEventListener('keydown', onKeyDown);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const trigger = menuButtonRef.current;
    return () => {
      cancelAnimationFrame(raf);
      aside.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = prevOverflow;
      trigger?.focus();
    };
  }, [drawerMode]);

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
    setShowLogoutModal(true);
  }

  function confirmLogout() {
    setShowLogoutModal(false);
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
      currentTitle !== 'Mi Panel' &&
      items[items.length - 1]?.label.toLowerCase() !== currentTitle.toLowerCase()
    ) {
      items.push({ label: currentTitle });
    }
    return items;
  }, [activeGroup, currentTitle, groups]);

  const expanded = !collapsed || mobileOpen;
  const brandSubtitle =
    user?.rol === 'SUPERADMIN'
      ? 'Plataforma SaaS'
      : user?.rol === 'ADMIN_ORGANIZACION'
      ? 'Gestión Organizacional'
      : 'Administración Residencial';
  const userInitial = user?.username?.[0]?.toUpperCase() || 'U';

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col antialiased">
      {/* Skip link accesible (WCAG 2.4.1) */}
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-[100] focus:px-4 focus:py-2 focus:bg-btn-primary focus:text-white focus:rounded-md focus:shadow-md focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 font-semibold text-xs"
      >
        Saltar al contenido principal
      </a>

      {/* Drawer overlay en móvil (vidrio esmerilado sobre el contenido) */}
      <div
        className={cn(
          'app-overlay fixed inset-0 z-40 transition-opacity duration-200 ease-out lg:hidden',
          mobileOpen ? 'opacity-100 pointer-events-auto' : 'opacity-0 pointer-events-none'
        )}
        onClick={() => setMobileOpen(false)}
        aria-hidden="true"
      />

      {/* SIDEBAR */}
      <aside
        ref={asideRef}
        id="app-sidebar"
        data-open={mobileOpen ? 'true' : 'false'}
        role={drawerMode ? 'dialog' : undefined}
        aria-modal={drawerMode ? 'true' : undefined}
        aria-label={drawerMode ? 'Menú principal' : undefined}
        className={cn(
          'app-sidebar fixed top-0 bottom-0 left-0 z-50 flex flex-col bg-sidebar text-[#C9D3EA] border-r border-white/10 select-none',
          // Mobile: drawer deslizante
          mobileOpen ? 'translate-x-0 w-72 shadow-xl' : '-translate-x-full lg:translate-x-0',
          // Desktop: expandido o colapsado
          collapsed && !mobileOpen ? 'lg:w-[72px]' : 'lg:w-64'
        )}
      >
        {/* Encabezado / Marca */}
        <div
          className={cn(
            'h-16 flex items-center gap-3 px-4 border-b border-white/10 flex-shrink-0',
            !expanded && 'lg:justify-center lg:px-0'
          )}
        >
          <span className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-[6px] bg-white shadow-slab ring-1 ring-white/70">
            <img
              src={`${import.meta.env.BASE_URL}imagenes/saed_logo_emblem_only.png`}
              alt="SAED"
              className="w-7 h-7 object-contain"
            />
          </span>
          {expanded && (
            <div className="flex flex-col min-w-0">
              <div className="flex items-center gap-1.5">
                <span className="app-display text-[17px] leading-none text-white [font-variation-settings:'wdth'_118,'wght'_820]">
                  SAED
                </span>
                <span className="app-stamp rounded-[2px] border border-white/40 px-1 text-[10.5px] leading-[16px] text-[#DCF4EB]">
                  2.0
                </span>
              </div>
              <span className="mt-1 text-[11.5px] leading-none text-[#B4C0DC] truncate">{brandSubtitle}</span>
            </div>
          )}

          {/* Botón de cierre en drawer móvil */}
          <button
            type="button"
            className="lg:hidden ml-auto inline-flex h-10 w-10 items-center justify-center rounded-md text-[#C9D3EA] hover:text-white hover:bg-white/10 transition-colors"
            onClick={() => setMobileOpen(false)}
            aria-label="Cerrar menú"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        {/* Selector de Tenant en drawer móvil */}
        {mobileOpen && (
          <div className="px-3 py-2.5 border-b border-white/10 lg:hidden">
            <TenantSwitcher className="w-full justify-between" />
          </div>
        )}

        {/* Área de Navegación */}
        <nav
          className={cn('app-sidebar-scroll flex-1 overflow-y-auto py-4', expanded ? 'px-3' : 'px-2')}
          aria-label="Navegación principal"
        >
          {groups.map((group, groupIndex) => {
            const isOpen = openGroupsDisplay.has(group.id);
            const isGroupActive = activeGroup?.id === group.id;

            return (
              <div key={group.id} className={cn(groupIndex > 0 && (expanded ? 'mt-4' : 'mt-2'))}>
                {/* Botón de grupo / categoría */}
                {expanded ? (
                  <button
                    type="button"
                    onClick={() => toggleGroup(group.id)}
                    className={cn(
                      'w-full flex items-center justify-between h-8 px-2 rounded-[4px] text-[12px] font-semibold text-[#8F9BB8] hover:text-white transition-colors group/header',
                      isGroupActive && 'text-[#DFEBFF]'
                    )}
                    aria-expanded={isOpen}
                  >
                    <span className="flex items-center gap-2 min-w-0">
                      {renderNavIcon(group.icon, 'h-3.5 w-3.5 flex-shrink-0')}
                      <span className="truncate">{group.label}</span>
                    </span>
                    <ChevronRight
                      className={cn('h-3.5 w-3.5 flex-shrink-0 transition-transform duration-200 ease-out', isOpen && 'rotate-90')}
                      aria-hidden="true"
                    />
                  </button>
                ) : (
                  // Rail colapsado: separador punteado con el ícono del grupo (expande la barra)
                  <button
                    type="button"
                    onClick={() => {
                      setCollapsed(false);
                      toggleGroup(group.id);
                    }}
                    className="w-full flex flex-col items-center gap-1.5 pt-1 pb-1 text-[#6F7C9C] hover:text-white transition-colors"
                    title={`Expandir categoría: ${group.label}`}
                    aria-label={`Expandir categoría: ${group.label}`}
                  >
                    {groupIndex > 0 && (
                      <span className="block h-0 w-8 border-0 border-t border-dashed border-white/20" aria-hidden="true" />
                    )}
                    {renderNavIcon(group.icon, 'h-3 w-3')}
                  </button>
                )}

                {/* Sub-items del grupo */}
                {expanded ? (
                  isOpen && (
                    <div className="mt-1 space-y-0.5">
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
                              'relative w-full flex items-center gap-3 h-9 px-2.5 rounded-[6px] text-[13px] transition-[background-color,color,box-shadow] duration-150 ease-out group/item',
                              active ? NAV_ACTIVE_PIECE : 'font-medium text-[#C9D3EA] hover:text-white hover:bg-white/[0.06]'
                            )}
                            title={item.label}
                            aria-current={active ? 'page' : undefined}
                          >
                            <span
                              className={cn(
                                'flex-shrink-0 transition-colors',
                                active ? 'text-white' : 'text-[#8F9BB8] group-hover/item:text-white'
                              )}
                            >
                              {renderNavIcon(item.icon, 'h-4 w-4')}
                            </span>

                            <span className="truncate flex-1 text-left">{item.label}</span>
                          </button>
                        );
                      })}
                    </div>
                  )
                ) : (
                  // Modo rail colapsado: todos los ítems como botones de íconos con tooltip
                  <div className="space-y-1">
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
                            'relative mx-auto flex h-10 w-10 items-center justify-center rounded-[6px] transition-[background-color,color,box-shadow] duration-150 ease-out group/item',
                            active ? NAV_ACTIVE_PIECE : 'text-[#8F9BB8] hover:text-white hover:bg-white/[0.08]'
                          )}
                          title={item.label}
                          aria-label={item.label}
                          aria-current={active ? 'page' : undefined}
                        >
                          {renderNavIcon(item.icon, 'h-4 w-4')}
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
        <div className={cn('flex-shrink-0 border-t border-white/10 space-y-1', expanded ? 'p-3' : 'p-2')}>
          <div
            className={cn(
              'flex items-center gap-3 rounded-[6px] bg-white/[0.04] ring-1 ring-inset ring-white/10',
              expanded ? 'p-2 mb-1' : 'lg:justify-center lg:p-1.5 lg:mb-1'
            )}
          >
            <div
              className="app-stamp h-9 w-9 flex-shrink-0 rounded-[6px] bg-[#EEE8FF] text-[#1D2942] flex items-center justify-center text-sm"
              aria-hidden="true"
            >
              {userInitial}
            </div>
            {expanded && (
              <div className="flex-1 min-w-0">
                <p className="text-[13px] font-semibold text-white truncate leading-tight">
                  {user?.username || 'Usuario'}
                </p>
                <span className="block text-[11.5px] text-[#B4C0DC] mt-0.5 truncate">{displayRole}</span>
              </div>
            )}
          </div>

          {user?.rol !== 'PORTERO' && (
            <button
              type="button"
              onClick={() => setShowChangePasswordModal(true)}
              className={cn(
                'w-full flex items-center gap-2.5 h-9 px-2.5 rounded-[6px] text-[13px] font-medium text-[#C9D3EA] hover:text-white hover:bg-white/[0.06] transition-colors group/pwd',
                !expanded && 'lg:justify-center lg:px-0'
              )}
              title="Cambiar contraseña"
              aria-label="Cambiar contraseña"
            >
              <Key className="h-4 w-4 text-[#8F9BB8] group-hover/pwd:text-white transition-colors" aria-hidden="true" />
              {expanded && <span>Cambiar clave</span>}
            </button>
          )}

          <button
            type="button"
            onClick={handleLogout}
            className={cn(
              'w-full flex items-center gap-2.5 h-9 px-2.5 rounded-[6px] text-[13px] font-semibold text-[#C9D3EA] hover:text-[#FFB3C0] hover:bg-[#FF8FA3]/10 transition-colors group/logout',
              !expanded && 'lg:justify-center lg:px-0'
            )}
            title="Cerrar sesión"
            aria-label="Cerrar sesión"
          >
            <LogOut className="h-4 w-4 text-[#8F9BB8] group-hover/logout:text-[#FFB3C0] transition-colors" aria-hidden="true" />
            {expanded && <span>Cerrar sesión</span>}
          </button>
        </div>
      </aside>

      {/* CONTENIDO Y TOPBAR */}
      <div
        className={cn(
          'flex-1 flex flex-col min-w-0 transition-[padding] duration-200 ease-out',
          collapsed ? 'lg:pl-[72px]' : 'lg:pl-64'
        )}
      >
        {/* TOPBAR: barra de vidrio fija sobre el contenido que se desplaza */}
        <header className="app-glass-bar sticky top-0 z-30 h-16 px-4 sm:px-6 lg:px-8 flex items-center justify-between gap-3">
          {/* Lado izquierdo: Toggles + Breadcrumbs en Desktop / Marca en Móvil */}
          <div className="flex items-center gap-3 min-w-0">
            {/* Toggle móvil */}
            <button
              ref={menuButtonRef}
              type="button"
              className={cn('lg:hidden', HEADER_ICON_BUTTON)}
              onClick={() => setMobileOpen(true)}
              aria-label="Abrir menú"
              aria-expanded={mobileOpen}
              aria-controls="app-sidebar"
            >
              <Menu className="h-5 w-5" aria-hidden="true" />
            </button>

            {/* Marca en móvil */}
            <div className="sm:hidden flex items-center gap-1.5">
              <span className="app-display text-[16px] leading-none text-on-surface [font-variation-settings:'wdth'_118,'wght'_820]">
                SAED
              </span>
              <span className="app-stamp rounded-[2px] border border-keyline/40 px-1 text-[10.5px] leading-[16px] text-on-surface">
                2.0
              </span>
            </div>

            {/* Toggle desktop collapse */}
            <button
              type="button"
              className={cn('hidden lg:inline-flex', HEADER_ICON_BUTTON, 'h-9 w-9 min-h-0 min-w-0')}
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
              <BreadcrumbNav items={breadcrumbs} className="text-[13px]" />
            </div>
          </div>

          {/* Lado derecho: Tenant + Theme + Notif + User */}
          <div className="flex items-center gap-2 flex-shrink-0">
            {/* Multi-tenant Context (Desktop / Tablet) */}
            <div className="hidden sm:inline-flex">
              <TenantSwitcher />
            </div>

            {/* Toggle de Tema (Desktop / Tablet) */}
            <button
              type="button"
              onClick={toggleTheme}
              className={cn('hidden sm:inline-flex', HEADER_ICON_BUTTON)}
              aria-label={dark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              title={dark ? 'Modo claro' : 'Modo oscuro'}
            >
              {dark ? <Sun className="h-4 w-4" aria-hidden="true" /> : <Moon className="h-4 w-4" aria-hidden="true" />}
            </button>

            {/* Campana de Notificaciones (Siempre visible) */}
            <NotificationBell />

            {/* Pastilla de Usuario en Desktop */}
            {user?.rol === 'PORTERO' ? (
              <div className={cn('hidden md:flex', USER_PILL)}>
                <span className={USER_PILL_AVATAR} aria-hidden="true">
                  {userInitial}
                </span>
                <span className="flex flex-col text-left min-w-0">
                  <span className="text-[13px] font-bold text-on-surface leading-none truncate max-w-[160px]">
                    {user?.username}
                  </span>
                  <span className="mt-1 text-[11.5px] leading-none text-text-muted truncate max-w-[160px]">{displayRole}</span>
                </span>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setShowChangePasswordModal(true)}
                className={cn('hidden md:flex group', USER_PILL, 'hover:border-keyline/50 hover:bg-surface-container')}
                title="Mi Perfil / Cambiar Contraseña"
              >
                <span className={USER_PILL_AVATAR} aria-hidden="true">
                  {userInitial}
                </span>
                <span className="flex flex-col text-left min-w-0">
                  <span className="text-[13px] font-bold text-on-surface leading-none truncate max-w-[160px]">
                    {user?.username}
                  </span>
                  <span className="mt-1 text-[11.5px] leading-none text-text-muted truncate max-w-[160px]">{displayRole}</span>
                </span>
                <Key
                  className="w-3.5 h-3.5 text-text-muted ml-1 group-hover:text-primary-text transition-colors"
                  aria-hidden="true"
                />
              </button>
            )}

            {/* Botón Logout Rápido (Desktop) */}
            <button
              type="button"
              onClick={handleLogout}
              className={cn(
                'hidden sm:inline-flex',
                HEADER_ICON_BUTTON,
                'hover:text-tint-red-fg hover:border-tint-red-fg/40 hover:bg-tint-red-bg'
              )}
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
          className="flex-1 min-w-0 bg-background min-h-[calc(100vh-64px)] p-4 sm:p-6 lg:p-8"
        >
          <ErrorBoundary routeKey={location.pathname}>
            <Suspense
              fallback={<LoadingState message="Cargando vista..." className="mx-auto max-w-3xl" />}
            >
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>

      {/* Modal de cambio de contraseña */}
      <ChangePasswordModal
        open={showChangePasswordModal}
        onOpenChange={setShowChangePasswordModal}
      />

      {/* Modal de confirmación para cerrar sesión */}
      <Dialog open={showLogoutModal} onOpenChange={setShowLogoutModal}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <LogOut className="h-5 w-5 text-tint-red-fg" aria-hidden="true" />
              ¿Cerrar sesión?
            </DialogTitle>
            <DialogDescription className="text-text-secondary text-sm">
              ¿Estás seguro de que deseas salir del sistema? Se guardará tu sesión actual y tendrás que ingresar tus credenciales nuevamente.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter className="pt-2">
            <Button variant="outline" onClick={() => setShowLogoutModal(false)}>
              Cancelar
            </Button>
            <Button variant="destructive" onClick={confirmLogout}>
              Sí, cerrar sesión
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
