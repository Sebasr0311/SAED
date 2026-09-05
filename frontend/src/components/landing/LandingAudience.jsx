import { Building2, ShieldCheck, Users, Layers, CheckCircle2, ArrowDown, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';

const HIERARCHY_ROLES = [
  {
    code: 'SUPERADMIN',
    name: 'Superadministrador',
    scope: 'Alcance Global',
    desc: 'Supervisión técnica de la plataforma, planes y organizaciones.',
    badgeColor: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
  },
  {
    code: 'ADMIN_ORGANIZACION',
    name: 'Administrador de Organización',
    scope: 'Multi-Propiedad',
    desc: 'Gestión consolidada del portafolio inmobiliario y empresas gestoras.',
    badgeColor: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
  },
  {
    code: 'ADMIN_PROPIEDAD',
    name: 'Administrador de Propiedad',
    scope: 'Copropiedad / Edificio',
    desc: 'Control financiero, censo de residentes, cartera y asambleas.',
    badgeColor: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  },
];

const PROFILES = [
  {
    roleTitle: 'ADMINISTRACIÓN',
    subheadline: 'Control operativo y financiero.',
    desc: 'Supervisión en tiempo real de cartera, conciliación bancaria con Wompi, contratos de arrendamiento y censo de residentes sin dispersión de datos.',
    icon: Building2,
    accent: 'border-blue-500/30 bg-blue-950/20',
    iconColor: 'text-blue-400 bg-blue-500/10 border-blue-500/20',
    points: [
      'Monitoreo en vivo de cartera y morosidad',
      'Padrón verificado de unidades y habitantes',
      'Certificados de paz y salvo automatizados',
    ],
  },
  {
    roleTitle: 'PORTERÍA',
    subheadline: 'Acceso y operación en tiempo real.',
    desc: 'Consola web táctil o de escritorio para escanear pases QR en segundos, registrar placas vehiculares y custodiar correspondencia con PIN único.',
    icon: ShieldCheck,
    accent: 'border-teal-500/30 bg-teal-950/20',
    iconColor: 'text-teal-400 bg-teal-500/10 border-teal-500/20',
    points: [
      'Validación de visitantes mediante lector QR',
      'Entrega de paquetes validada con PIN de retiro',
      'Disponibilidad dinámica de parqueaderos',
    ],
  },
  {
    roleTitle: 'RESIDENTES',
    subheadline: 'Servicios y comunicación desde un solo lugar.',
    desc: 'Portal web ágil accesible desde cualquier smartphone sin descargar aplicaciones pesadas. Emisión de pases QR, pagos en línea y radicación de PQRS.',
    icon: Users,
    accent: 'border-emerald-500/30 bg-emerald-950/20',
    iconColor: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20',
    points: [
      'Generación instantánea de pases de acceso QR',
      'Pago de cuotas en línea mediante PSE / Wompi',
      'Buzón digital de avisos oficiales y paquetería',
    ],
  },
];

export default function LandingAudience() {
  return (
    <section
      id="audiencia"
      className="py-24 sm:py-32 lg:py-36 bg-[#0A1628] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Editorial Header */}
        <div className="max-w-3xl mx-auto text-center space-y-5">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
            PERFILES Y GOBERNANZA
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Creado para cada persona que mueve la propiedad.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Una arquitectura de roles estrictamente delimitada donde cada usuario accede a las herramientas exactas para su función.
          </p>
        </div>

        {/* Visual Hierarchy Flow */}
        <div className="mt-16 sm:mt-20 max-w-4xl mx-auto p-7 sm:p-9 rounded-3xl bg-slate-900/80 border border-slate-800 shadow-2xl space-y-6">
          <div className="flex items-center justify-between border-b border-slate-800 pb-4">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-300 uppercase tracking-wider">
              <Layers className="w-4 h-4 text-emerald-400" />
              <span>Jerarquía de Control SAED</span>
            </div>
            <span className="text-[11px] font-mono text-emerald-400 bg-emerald-950/60 px-2.5 py-0.5 rounded border border-emerald-500/25">
              Zero-Trust RBAC
            </span>
          </div>

          <div className="space-y-4">
            {HIERARCHY_ROLES.map((r, i) => (
              <div key={r.code} className="space-y-2">
                <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div className="flex items-center gap-3">
                    <span className="font-mono text-xs font-bold text-slate-500">
                      0{i + 1}
                    </span>
                    <div>
                      <span className="font-bold text-white text-sm block sm:inline mr-2">
                        {r.name}
                      </span>
                      <span className="font-mono text-[11px] text-slate-400">
                        ({r.code})
                      </span>
                    </div>
                  </div>
                  <span className={`px-2.5 py-0.5 rounded text-xs font-semibold border self-start sm:self-auto ${r.badgeColor}`}>
                    {r.scope}
                  </span>
                </div>
                {i < HIERARCHY_ROLES.length - 1 && (
                  <div className="flex justify-center text-slate-600">
                    <ArrowDown className="w-4 h-4" />
                  </div>
                )}
              </div>
            ))}

            {/* Split row: PORTERO + RESIDENTE */}
            <div className="flex justify-center text-slate-600">
              <ArrowDown className="w-4 h-4" />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div className="p-4 rounded-xl bg-teal-950/30 border border-teal-500/30 flex items-center justify-between">
                <div>
                  <span className="font-bold text-white text-sm block">Personal de Portería</span>
                  <span className="font-mono text-[11px] text-teal-300">(PORTERO)</span>
                </div>
                <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-teal-500/20 text-teal-300 border border-teal-500/30">
                  Operación Garita
                </span>
              </div>

              <div className="p-4 rounded-xl bg-emerald-950/30 border border-emerald-500/30 flex items-center justify-between">
                <div>
                  <span className="font-bold text-white text-sm block">Habitantes & Propietarios</span>
                  <span className="font-mono text-[11px] text-emerald-300">(RESIDENTE)</span>
                </div>
                <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                  Autonomía Web
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* 3 Main Operational Audiences */}
        <div className="mt-16 sm:mt-24 grid grid-cols-1 lg:grid-cols-3 gap-6 sm:gap-8 max-w-6xl mx-auto">
          {PROFILES.map((p) => {
            const Icon = p.icon;
            return (
              <div
                key={p.roleTitle}
                className={`p-7 sm:p-9 rounded-3xl bg-slate-900/80 border ${p.accent} shadow-2xl flex flex-col justify-between group hover:border-opacity-60 transition-all`}
              >
                <div className="space-y-5">
                  <div className="w-12 h-12 rounded-2xl border flex items-center justify-center shrink-0 group-hover:scale-105 transition-transform" />
                  <div className="-mt-16 mb-2">
                    <div className={`w-12 h-12 rounded-2xl border flex items-center justify-center ${p.iconColor}`}>
                      <Icon className="w-6 h-6" />
                    </div>
                  </div>

                  <div>
                    <span className="text-xs font-mono font-bold text-emerald-400 uppercase tracking-widest block mb-1">
                      {p.roleTitle}
                    </span>
                    <h3 className="text-xl font-bold text-white tracking-tight">
                      {p.subheadline}
                    </h3>
                  </div>

                  <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                    {p.desc}
                  </p>

                  <div className="pt-4 border-t border-slate-800/80 space-y-2">
                    {p.points.map((point) => (
                      <div key={point} className="flex items-start gap-2.5 text-xs text-slate-300">
                        <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-0.5" />
                        <span>{point}</span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="mt-8 pt-4 border-t border-slate-800/80 flex items-center justify-between">
                  <span className="text-xs text-slate-500">Diseñado para la labor diaria</span>
                  <Link
                    to="/login"
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-400 hover:text-emerald-300 transition-colors"
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
