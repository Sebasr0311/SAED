import {
  ArrowLeftRight,
  BadgeCheck,
  Building2,
  CalendarDays,
  CarFront,
  ClipboardList,
  CreditCard,
  DoorOpen,
  FileSignature,
  FileText,
  Gavel,
  Landmark,
  MessageSquareWarning,
  Package,
  PiggyBank,
  QrCode,
  ShieldCheck,
  Smartphone,
  TriangleAlert,
  Users,
  Wallet,
  Wrench,
} from 'lucide-react';

// size: L = role, M = core module, S = module. Sizes follow one fixed ramp.
const GROUPS = [
  {
    title: 'Roles',
    tone: 'lav',
    parts: [
      { label: 'Administración', icon: Building2, size: 'L' },
      { label: 'Portería', icon: DoorOpen, size: 'L' },
      { label: 'Residente', icon: Smartphone, size: 'L' },
      { label: 'Consola de organización', icon: ClipboardList, size: 'M' },
    ],
  },
  {
    title: 'Finanzas',
    tone: 'mint',
    parts: [
      { label: 'Cartera y cuotas', icon: Wallet, size: 'M' },
      { label: 'Pagos Wompi', icon: CreditCard, size: 'M' },
      { label: 'Presupuestos y gastos', icon: PiggyBank, size: 'S' },
      { label: 'Conciliaciones', icon: ArrowLeftRight, size: 'S' },
      { label: 'Paz y salvo', icon: BadgeCheck, size: 'S' },
      { label: 'Multas', icon: Gavel, size: 'S' },
    ],
  },
  {
    title: 'Operación y comunidad',
    tone: 'mint',
    parts: [
      { label: 'Unidades y residentes', icon: Users, size: 'M' },
      { label: 'Visitas QR', icon: QrCode, size: 'M' },
      { label: 'Asambleas', icon: Landmark, size: 'M' },
      { label: 'Contratos', icon: FileSignature, size: 'S' },
      { label: 'Paquetes', icon: Package, size: 'S' },
      { label: 'Parqueadero de visitantes', icon: CarFront, size: 'S' },
      { label: 'Documentos y reglamentos', icon: FileText, size: 'S' },
      { label: 'PQRS', icon: MessageSquareWarning, size: 'S' },
      { label: 'Reservas de zonas comunes', icon: CalendarDays, size: 'S' },
      { label: 'Mantenimiento', icon: Wrench, size: 'S' },
      { label: 'Seguros', icon: ShieldCheck, size: 'S' },
      { label: 'Incidentes', icon: TriangleAlert, size: 'S' },
    ],
  },
];

export default function LandingParts() {
  return (
    <section id="piezas" className="m-section" aria-labelledby="m-parts-title">
      <div className="m-container">
        <div className="m-section-head">
          <h2 id="m-parts-title" className="m-h2">
            Lo que viene en la caja
          </h2>
          <p className="m-body">
            Tres roles, dieciocho módulos y una consola para administradoras con varios conjuntos.
            Todas las piezas encajan en el mismo registro: lo que una registra, las demás lo ven.
          </p>
        </div>

        <div className="m-parts-page m-studs">
          {GROUPS.map((group) => (
            <div key={group.title} className="m-parts-group">
              <h3 className="m-parts-title">{group.title}</h3>
              <ul className="m-parts" aria-label={group.title}>
                {group.parts.map(({ label, icon: Icon, size }) => (
                  <li key={label} className={`m-part is-${size} tone-${group.tone}`}>
                    <span className="m-part-piece" aria-hidden="true">
                      <Icon strokeWidth={1.75} />
                      <span className="m-part-count">x1</span>
                    </span>
                    <span className="m-part-label">{label}</span>
                  </li>
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
