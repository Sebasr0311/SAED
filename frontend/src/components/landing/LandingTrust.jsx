import { ShieldAlert, Building2, Building, ShieldCheck, UserCheck, Sparkles } from 'lucide-react';
import { useScrollReveal } from '../../lib/animations.js';

const ROLES = [
  {
    code: 'SUPERADMIN',
    title: 'Superadministrador',
    scope: 'Alcance Global',
    description: 'Control de infraestructura de la plataforma, planes SaaS y auditoría transversal del sistema.',
    icon: ShieldAlert,
    badgeColor: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
    borderColor: 'border-purple-500/20',
  },
  {
    code: 'ADMIN_ORGANIZACION',
    title: 'Administrador de Organización',
    scope: 'Multi-Propiedad',
    description: 'Gestión corporativa de empresas administradoras con múltiples copropiedades y conjuntos a su cargo.',
    icon: Building,
    badgeColor: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    borderColor: 'border-blue-500/20',
  },
  {
    code: 'ADMIN_PROPIEDAD',
    title: 'Administrador de Propiedad',
    scope: 'Copropiedad',
    description: 'Gestión operativa y financiera: padrón de residentes, cartera, cuotas, proveedores y asambleas.',
    icon: Building2,
    badgeColor: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    borderColor: 'border-emerald-500/30',
  },
  {
    code: 'PORTERO',
    title: 'Personal de Portería',
    scope: 'Garita & Accesos',
    description: 'Operación ágil en pantalla: escaneo de pases QR, recepción y entrega de paquetes por PIN y parqueaderos.',
    icon: ShieldCheck,
    badgeColor: 'bg-teal-500/10 text-teal-400 border-teal-500/20',
    borderColor: 'border-teal-500/20',
  },
  {
    code: 'RESIDENTE',
    title: 'Copropietario / Habitante',
    scope: 'Unidad Privada',
    description: 'Portal web ágil: emisión de invitaciones QR, pago de cuotas con Wompi, casillero y radicación de PQRS.',
    icon: UserCheck,
    badgeColor: 'bg-sky-500/10 text-sky-400 border-sky-500/20',
    borderColor: 'border-sky-500/20',
  },
];

export default function LandingTrust() {
  const containerRef = useScrollReveal({
    selector: '.role-card',
    stagger: 90,
    distance: 24,
  });

  return (
    <section className="py-20 sm:py-28 lg:py-32 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden">
      <div className="absolute top-1/3 right-1/4 w-[500px] h-[300px] bg-sky-500/5 blur-[140px] rounded-full pointer-events-none" />
      
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Editorial Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16 sm:mb-20">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20">
            <Sparkles className="w-3.5 h-3.5 text-sky-400" />
            GOBERNANZA Y AUTORIZACIÓN
          </span>

          <h2 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Una plataforma.<br />
            Cinco roles.<br />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-white via-cyan-200 to-sky-400">
              Una operación conectada.
            </span>
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto pt-2">
            Cada usuario interactúa con la plataforma bajo permisos estrictamente delimitados por su rol, garantizando seguridad y confidencialidad absoluta en cada consulta.
          </p>
        </div>

        {/* Hierarchical Role Composition */}
        <div ref={containerRef} className="max-w-5xl mx-auto space-y-4">
          {ROLES.map((role) => {
            const Icon = role.icon;
            return (
              <div
                key={role.code}
                className={`role-card p-5 sm:p-6 rounded-2xl bg-slate-900/60 backdrop-blur-md border ${role.borderColor} hover:border-sky-500/40 transition-all duration-300 flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-xl hover:shadow-sky-500/5 hover:-translate-y-0.5`}
              >
                <div className="flex items-start sm:items-center gap-4">
                  <div className="w-12 h-12 rounded-xl bg-slate-950 border border-slate-800 flex items-center justify-center text-sky-400 shrink-0 shadow-inner">
                    <Icon className="w-6 h-6" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2.5 flex-wrap">
                      <h3 className="text-base sm:text-lg font-bold text-white font-['Plus_Jakarta_Sans']">
                        {role.title}
                      </h3>
                      <span className={`px-2.5 py-0.5 rounded-full text-[11px] font-mono font-bold border ${role.badgeColor}`}>
                        {role.code}
                      </span>
                    </div>
                    <p className="text-xs sm:text-sm text-slate-300 mt-1 leading-relaxed">
                      {role.description}
                    </p>
                  </div>
                </div>

                <div className="sm:text-right shrink-0">
                  <span className="text-xs font-semibold text-slate-400 bg-slate-950/80 px-3 py-1.5 rounded-lg border border-slate-800 inline-block">
                    {role.scope}
                  </span>
                </div>
              </div>
            );
          })}
        </div>

      </div>
    </section>
  );
}
