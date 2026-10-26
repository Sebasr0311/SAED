import {
  ArrowLeftRight,
  BadgeCheck,
  Building2,
  CalendarDays,
  CarFront,
  ChartColumn,
  ClipboardList,
  CreditCard,
  DoorOpen,
  FileSignature,
  FileText,
  Gavel,
  HardHat,
  House,
  Inbox,
  KeyRound,
  Landmark,
  Megaphone,
  MessageSquareWarning,
  Package,
  PiggyBank,
  QrCode,
  Receipt,
  ScanLine,
  ShieldCheck,
  Siren,
  Smartphone,
  TriangleAlert,
  UserRound,
  Users,
  UsersRound,
  Wallet,
  Wrench,
} from 'lucide-react';

// Roles and modules mirror the current app: role definitions in src/lib/access.js and the
// per-role navigation in src/components/layout/AppShell.jsx (SUPERADMIN is platform-only).
const ROLES = [
  {
    label: 'Organización',
    icon: Building2,
    desc: 'Supervisa varias copropiedades: administradores, cartera consolidada, reportes y plan.',
  },
  {
    label: 'Administrador',
    icon: ClipboardList,
    desc: 'Opera la copropiedad: unidades, residentes, finanzas, comunidad y mantenimiento.',
  },
  {
    label: 'Portería',
    icon: DoorOpen,
    desc: 'Registra visitas, valida invitaciones QR, recibe paquetes y asigna parqueaderos.',
  },
  {
    label: 'Residente',
    icon: Smartphone,
    desc: 'Paga sus cuotas, invita visitas, reserva zonas comunes y radica PQRS.',
  },
  {
    label: 'Residente de convivencia',
    icon: UsersRound,
    desc: 'Vive en la unidad sin acceso financiero: visitas, reservas, PQRS y documentos.',
  },
  {
    label: 'Propietario no residente',
    icon: KeyRound,
    desc: 'Titular que no vive en la unidad: cuotas y pagos, asambleas y documentos.',
  },
];

const GROUPS = [
  {
    title: 'Para la organización',
    parts: [
      { label: 'Propiedades', icon: Building2, size: 'M' },
      { label: 'Administradores', icon: UserRound, size: 'S' },
      { label: 'Plantillas de contratos', icon: FileSignature, size: 'S' },
      { label: 'Cartera consolidada', icon: Wallet, size: 'S' },
      { label: 'Reportes y analítica', icon: ChartColumn, size: 'S' },
      { label: 'Plan y suscripción', icon: BadgeCheck, size: 'S' },
    ],
  },
  {
    title: 'Para la administración',
    parts: [
      { label: 'Unidades y residentes', icon: Users, size: 'M' },
      { label: 'Cartera y cuotas', icon: Wallet, size: 'M' },
      { label: 'Asambleas y actas', icon: Landmark, size: 'M' },
      { label: 'Contratos de arriendo', icon: FileSignature, size: 'S' },
      { label: 'Contratos de proveedores', icon: FileText, size: 'S' },
      { label: 'Empleados', icon: UserRound, size: 'S' },
      { label: 'Facturación', icon: Receipt, size: 'S' },
      { label: 'Presupuestos y gastos', icon: PiggyBank, size: 'S' },
      { label: 'Conciliaciones', icon: ArrowLeftRight, size: 'S' },
      { label: 'Paz y salvo', icon: BadgeCheck, size: 'S' },
      { label: 'Sanciones', icon: Gavel, size: 'S' },
      { label: 'PQRS', icon: MessageSquareWarning, size: 'S' },
      { label: 'Reservas de zonas comunes', icon: CalendarDays, size: 'S' },
      { label: 'Mantenimiento y activos', icon: Wrench, size: 'S' },
      { label: 'Obras y remodelaciones', icon: HardHat, size: 'S' },
      { label: 'Pólizas de seguros', icon: ShieldCheck, size: 'S' },
      { label: 'Planes de emergencia', icon: Siren, size: 'S' },
      { label: 'Incidentes', icon: TriangleAlert, size: 'S' },
      { label: 'Comunicaciones', icon: Megaphone, size: 'S' },
      { label: 'Documentos y reglamentos', icon: FileText, size: 'S' },
    ],
  },
  {
    title: 'Para la portería',
    parts: [
      { label: 'Visitas', icon: Users, size: 'M' },
      { label: 'Escáner QR', icon: ScanLine, size: 'M' },
      { label: 'Paquetes', icon: Package, size: 'S' },
      { label: 'Parqueadero de visitantes', icon: CarFront, size: 'S' },
    ],
  },
  {
    title: 'Para residentes y propietarios',
    parts: [
      { label: 'Cuotas y pagos Wompi', icon: CreditCard, size: 'M' },
      { label: 'Invitaciones QR', icon: QrCode, size: 'M' },
      { label: 'Mi unidad y convivientes', icon: House, size: 'S' },
      { label: 'Buzón', icon: Inbox, size: 'S' },
      { label: 'PQRS', icon: MessageSquareWarning, size: 'S' },
      { label: 'Reservas', icon: CalendarDays, size: 'S' },
      { label: 'Asambleas', icon: Landmark, size: 'S' },
      { label: 'Documentos y pólizas', icon: FileText, size: 'S' },
    ],
  },
];

function Part({ label, icon: Icon, size, tone, desc, index }) {
  return (
    <li className={`m-part is-${size} tone-${tone}`} style={{ '--i': index }}>
      <span className="m-part-piece" aria-hidden="true">
        <Icon strokeWidth={1.75} />
        <span className="m-part-count">x1</span>
      </span>
      <span className="m-part-label">
        {label}
        {desc && <span className="m-part-desc">{desc}</span>}
      </span>
    </li>
  );
}

export default function LandingParts() {
  return (
    <section id="piezas" className="m-section" aria-labelledby="m-parts-title">
      <div className="m-container">
        <div className="m-section-head m-reveal">
          <h2 id="m-parts-title" className="m-h2">
            Lo que viene en la caja
          </h2>
          <p className="m-body">
            Seis roles y los módulos que usa cada uno. Todas las piezas encajan en el mismo
            registro: lo que una registra, las demás lo ven.
          </p>
        </div>

        <div className="m-parts-page">
          <div className="m-parts-group" data-reveal-group>
            <h3 className="m-parts-title">Roles</h3>
            <ul className="m-parts m-parts--roles" aria-label="Roles">
              {ROLES.map((role, i) => (
                <Part key={role.label} {...role} size="L" tone="lav" index={i} />
              ))}
            </ul>
          </div>

          {GROUPS.map((group) => (
            <div key={group.title} className="m-parts-group" data-reveal-group>
              <h3 className="m-parts-title">{group.title}</h3>
              <ul className="m-parts" aria-label={group.title}>
                {group.parts.map((part, i) => (
                  <Part key={part.label} {...part} tone="mint" index={i} />
                ))}
              </ul>
            </div>
          ))}
          <p className="m-parts-legend">
            Piezas grandes: roles. Medianas: módulos centrales. Pequeñas: módulos de apoyo.
          </p>
        </div>
      </div>
    </section>
  );
}
