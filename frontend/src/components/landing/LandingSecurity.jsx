import { ShieldCheck, Lock, Database, FileText, CheckCircle2 } from 'lucide-react';

const SECURITY_PILLARS = [
  {
    icon: Database,
    title: 'Aislamiento Multi-Tenant',
    highlight: 'Cada organización opera dentro de su propio contexto.',
    description:
      'Políticas estrictas de seguridad a nivel de motor de datos (Row Level Security / VPD). Ninguna copropiedad puede consultar, filtrar ni visualizar información de otra.',
  },
  {
    icon: Lock,
    title: 'Control de Acceso Riguroso',
    highlight: 'Permisos delimitados por perfil operativo.',
    description:
      'Autenticación basada en tokens criptográficos (JWT) con validación contextual en cada petición. SuperAdmin, administradores, guardias y residentes acceden solo a su área.',
  },
  {
    icon: FileText,
    title: 'Bitácora de Auditoría Inmutable',
    highlight: 'Trazabilidad completa con sello de tiempo.',
    description:
      'Cada ingreso QR validado, paquete recibido, asignación de parqueadero o recaudo asentado queda registrado con fecha, hora exacta y operador responsable.',
  },
  {
    icon: ShieldCheck,
    title: 'Gobernanza bajo Ley 675',
    highlight: 'Conforme al marco legal colombiano.',
    description:
      'Estructura diseñada para responder al régimen de propiedad horizontal: coeficientes de copropiedad, quórum de asambleas, cuotas ordinarias y paz y salvos oficiales.',
  },
];

export default function LandingSecurity() {
  return (
    <section
      id="seguridad-arquitectura"
      className="py-24 sm:py-32 lg:py-36 bg-[#0F172A] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="max-w-3xl mx-auto text-center space-y-5">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
            INTEGRIDAD & PROTECCIÓN
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Seguridad desde la arquitectura.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            La privacidad de tu comunidad no es una capa superficial; está incorporada desde el modelo de datos hasta la interfaz del usuario.
          </p>
        </div>

        {/* 4 Pillars Grid */}
        <div className="mt-16 sm:mt-24 grid grid-cols-1 md:grid-cols-2 gap-6 sm:gap-8 max-w-5xl mx-auto">
          {SECURITY_PILLARS.map((p) => {
            const Icon = p.icon;
            return (
              <div
                key={p.title}
                className="p-7 sm:p-9 rounded-3xl bg-slate-900/80 border border-slate-800 hover:border-emerald-500/40 transition-colors shadow-2xl flex flex-col justify-between space-y-6 group"
              >
                <div className="space-y-4">
                  <div className="w-12 h-12 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 group-hover:scale-105 transition-transform">
                    <Icon className="w-6 h-6" />
                  </div>

                  <div>
                    <h3 className="text-xl font-bold text-white tracking-tight">
                      {p.title}
                    </h3>
                    <p className="text-xs sm:text-sm text-emerald-400 font-medium mt-1">
                      {p.highlight}
                    </p>
                  </div>

                  <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                    {p.description}
                  </p>
                </div>

                <div className="pt-4 border-t border-slate-800/80 flex items-center gap-2 text-xs text-slate-400">
                  <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                  <span>Estándar certificado en SAED 2.0</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
