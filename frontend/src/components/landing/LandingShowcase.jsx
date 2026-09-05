import { Smartphone, Zap, Database, Lock, CheckCircle2 } from 'lucide-react';

const HIGHLIGHTS = [
  {
    icon: Database,
    title: '5 Perfiles Operativos',
    subtitle: 'RBAC Certificado',
    description: 'Espacios y permisos independientes para SuperAdmin, Administrador de Organización, Administrador de Copropiedad, Portero y Residente.',
  },
  {
    icon: Zap,
    title: 'Validación Inmediata',
    subtitle: 'Lectura QR en Portería',
    description: 'Los pases QR de visitantes se verifican en pantalla en milisegundos, registrando automáticamente la unidad destino y hora de ingreso.',
  },
  {
    icon: Smartphone,
    title: 'Cero Descargas Obligatorias',
    subtitle: 'Arquitectura Web Responsive',
    description: 'Accesible desde cualquier navegador en PC, tablet o smartphone sin instalar aplicaciones pesadas ni ocupar memoria del dispositivo.',
  },
  {
    icon: Lock,
    title: 'Aislamiento Multi-Tenant',
    subtitle: 'Seguridad en Base de Datos',
    description: 'Políticas estrictas de aislamiento a nivel relacional (RLS). Ninguna propiedad puede consultar ni filtrar datos de otra comunidad.',
  },
];

export default function LandingShowcase() {
  return (
    <section className="py-24 sm:py-32 bg-slate-900 text-white border-t border-slate-800 relative overflow-hidden">
      {/* Background Accent Gradients */}
      <div className="absolute top-0 right-1/4 w-96 h-96 bg-primary/10 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-0 left-1/4 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center mb-16 sm:mb-20 space-y-4">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-blue-400 bg-blue-500/10 border border-blue-500/20">
            ARQUITECTURA DE SOFTWARE CERTIFICADA
          </span>
          <h2 className="text-3xl sm:text-5xl font-extrabold text-white tracking-tight leading-tight font-['Plus_Jakarta_Sans']">
            Diseñada para la escala real de la copropiedad moderna.
          </h2>
          <p className="text-base sm:text-lg text-slate-300 leading-relaxed font-normal">
            Cada módulo de SAED 2.0 responde a un flujo operativo auditado, garantizando disponibilidad, confidencialidad y rapidez.
          </p>
        </div>

        {/* 4 Architectural Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {HIGHLIGHTS.map((item, idx) => {
            const Icon = item.icon;
            return (
              <div
                key={idx}
                className="bg-slate-800/60 border border-slate-700/70 hover:border-blue-500/50 rounded-3xl p-7 flex flex-col justify-between transition-all duration-200 group shadow-lg"
              >
                <div>
                  <div className="w-12 h-12 rounded-2xl bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400 mb-6 group-hover:scale-105 transition-transform">
                    <Icon className="w-6 h-6" />
                  </div>

                  <span className="text-xs font-bold uppercase tracking-wider text-blue-400">
                    {item.subtitle}
                  </span>
                  <h3 className="text-xl font-bold text-white mt-1 mb-3">
                    {item.title}
                  </h3>
                  <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                    {item.description}
                  </p>
                </div>

                <div className="mt-6 pt-4 border-t border-slate-700/60 flex items-center gap-2 text-xs text-slate-400">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                  <span>Capacidad en producción</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
