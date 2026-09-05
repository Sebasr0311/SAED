import { Building2, ShieldCheck, Users, Layers, CheckCircle2, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';

const AUDIENCES = [
  {
    badge: 'Gestión Administrativa',
    badgeColor: 'bg-primary/10 text-primary border-primary/20',
    icon: Building2,
    role: 'Administradores y Consejos de Copropiedad',
    who: 'Líderes de la gestión operativa, legal y financiera del conjunto o edificio.',
    solves: 'Centraliza cartera, conciliación de pagos con Wompi, unidades y asambleas en una sola consola con información al día.',
    capabilities: [
      'Monitoreo en tiempo real de cartera y recaudos',
      'Gestión de unidades, residentes y contratos',
      'Emisión automatizada de paz y salvos certificados',
    ],
  },
  {
    badge: 'Operación en Garita',
    badgeColor: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20',
    icon: ShieldCheck,
    role: 'Personal de Portería y Seguridad',
    who: 'Operadores de control de acceso y recepción en las entradas del conjunto.',
    solves: 'Agiliza ingresos peatonales y vehiculares en segundos sin recurrir a llamadas telefónicas ni minutas físicas en papel.',
    capabilities: [
      'Validación de visitas con lector QR en pantalla táctil o PC',
      'Recepción y entrega de paquetería con PIN de seguridad',
      'Control y asignación de parqueaderos de visitantes',
    ],
  },
  {
    badge: 'Experiencia del Habitante',
    badgeColor: 'bg-sky-500/10 text-sky-600 dark:text-sky-400 border-sky-500/20',
    icon: Users,
    role: 'Residentes y Copropietarios',
    who: 'Habitantes de apartamentos y unidades residenciales o comerciales.',
    solves: 'Autonomía total desde el navegador web de cualquier teléfono o computador, sin descargas obligatorias de tiendas de aplicaciones.',
    capabilities: [
      'Generación instantánea de pases QR para invitados',
      'Consulta y pago de cuotas de administración en línea',
      'Alertas de correspondencia y radicación de PQRS',
    ],
  },
  {
    badge: 'Escalabilidad Corporativa',
    badgeColor: 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border-amber-500/20',
    icon: Layers,
    role: 'Empresas de Administración Multi-Edificio',
    who: 'Empresas gestoras y administradoras que coordinan múltiples copropiedades.',
    solves: 'Supervisión consolidada de todo el portafolio inmobiliario bajo un único acceso corporativo con estricta separación de datos.',
    capabilities: [
      'Arquitectura multi-tenant con aislamiento por copropiedad',
      'Dashboard global de operaciones y cartera consolidada',
      'Estandarización de protocolos de seguridad en todas las sedes',
    ],
  },
];

export default function LandingAudience() {
  return (
    <section id="audiencia" className="py-24 bg-background border-t border-border relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider bg-primary/10 text-primary border border-primary/20">
            Perfiles & Cobertura
          </span>
          <h2 className="mt-4 text-3xl sm:text-4xl font-extrabold text-foreground tracking-tight">
            Diseñado a la medida de cada actor de la comunidad
          </h2>
          <p className="mt-4 text-base sm:text-lg text-muted-foreground leading-relaxed">
            Una experiencia ergonómica pensada para resolver las necesidades específicas de administradores, guardias, residentes y empresas operadoras.
          </p>
        </div>

        {/* Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {AUDIENCES.map((item, idx) => {
            const Icon = item.icon;
            return (
              <div
                key={idx}
                className="bg-card border border-border hover:border-primary/40 rounded-2xl p-7 sm:p-8 flex flex-col justify-between transition-all duration-200 hover:shadow-lg hover:shadow-primary/5 group"
              >
                <div>
                  <div className="flex items-center justify-between gap-3 mb-5">
                    <span className={`inline-flex items-center px-2.5 py-1 rounded-md text-xs font-semibold border ${item.badgeColor}`}>
                      {item.badge}
                    </span>
                    <div className="w-11 h-11 rounded-xl bg-muted/60 border border-border flex items-center justify-center text-primary group-hover:scale-105 transition-transform duration-200">
                      <Icon className="w-5 h-5" />
                    </div>
                  </div>

                  <h3 className="text-xl font-bold text-foreground mb-3">
                    {item.role}
                  </h3>

                  {/* Quién es */}
                  <div className="space-y-1 mb-3">
                    <span className="text-xs font-semibold uppercase tracking-wider text-primary">
                      Perfil:
                    </span>
                    <p className="text-sm text-foreground/80 leading-relaxed">
                      {item.who}
                    </p>
                  </div>

                  {/* Qué resuelve */}
                  <div className="space-y-1 mb-6">
                    <span className="text-xs font-semibold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
                      Qué resuelve SAED:
                    </span>
                    <p className="text-sm text-muted-foreground leading-relaxed">
                      {item.solves}
                    </p>
                  </div>

                  {/* Capacidades clave */}
                  <div className="space-y-2 pt-4 border-t border-border/70">
                    <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                      Capacidades destacadas:
                    </p>
                    {item.capabilities.map((cap, i) => (
                      <div key={i} className="flex items-start gap-2.5 text-xs sm:text-sm text-foreground/90">
                        <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
                        <span>{cap}</span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="mt-8 pt-4 border-t border-border/60 flex items-center justify-between">
                  <span className="text-xs text-muted-foreground font-medium">
                    Experiencia especializada para {item.badge.toLowerCase()}
                  </span>
                  <Link
                    to="/login"
                    className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:text-primary/80 transition-colors shrink-0"
                  >
                    <span>Ingresar</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </Link>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
