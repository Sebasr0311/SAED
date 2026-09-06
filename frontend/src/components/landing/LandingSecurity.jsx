import {
  Lock,
  Database,
  Key,
  Layers,
  Scale,
  Sparkles,
  ShieldCheck,
} from 'lucide-react';
import { useScrollReveal } from '../../lib/animations.js';

const SECURITY_PIPELINE = [
  { step: '01', name: 'Usuario', detail: 'Credenciales verificadas' },
  { step: '02', name: 'JWT Stateless', detail: 'Firma HMAC-SHA256' },
  { step: '03', name: 'Contexto de Sesión', detail: 'X-Assignment-Id' },
  { step: '04', name: 'RLS / VPD', detail: 'Políticas en BD' },
  { step: '05', name: 'Tenant Aislado', detail: 'Filtro por Propiedad' },
  { step: '06', name: 'Datos Protegidos', detail: 'Cero fuga de información' },
];

const SECURITY_PILLARS = [
  {
    icon: Database,
    title: 'Aislamiento Multi-Tenant en BD',
    desc: 'Políticas de Virtual Private Database (VPD / RLS) a nivel de motor relacional. Ninguna consulta SQL puede acceder a registros de otra copropiedad.',
  },
  {
    icon: Lock,
    title: 'Autenticación Criptográfica JWT',
    desc: 'Tokens de sesión sin estado (stateless) protegidos con firmas criptográficas, expiración controlada y resolución de identidad en cada petición API.',
  },
  {
    icon: Key,
    title: 'Control de Acceso por Roles (RBAC)',
    desc: 'Matriz estricta de autorización para los 5 perfiles del sistema. La interfaz y los endpoints validan permisos de manera independiente.',
  },
  {
    icon: Layers,
    title: 'Trazabilidad y Auditoría Transaccional',
    desc: 'Registro cronológico con operador, fecha y contexto para cada evento sensible: validaciones de QR, retiros de paquetes y conciliaciones de cartera.',
  },
];

export default function LandingSecurity() {
  const containerRef = useScrollReveal({
    selector: '.security-card',
    stagger: 80,
    distance: 24,
  });

  return (
    <section
      id="seguridad"
      className="py-20 sm:py-28 lg:py-32 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      {/* Subtle glow accents */}
      <div className="absolute top-1/3 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[750px] h-[360px] bg-sky-500/5 blur-[160px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16 sm:mb-20">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20">
            <Sparkles className="w-3.5 h-3.5 text-sky-400" />
            SEGURIDAD Y AISLAMIENTO
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Seguridad desde la arquitectura.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            El aislamiento de cada propiedad no depende únicamente de la interfaz. Diseñado con aislamiento Multi-Tenant y controles de autorización a nivel de aplicación y base de datos.
          </p>
        </div>

        {/* The Security Pipeline Flow */}
        <div className="max-w-5xl mx-auto mb-16 sm:mb-20">
          <div className="p-6 sm:p-8 rounded-3xl bg-slate-900/60 backdrop-blur-md border border-slate-800/90 shadow-2xl space-y-6">
            <div className="flex items-center justify-between border-b border-slate-800/80 pb-4">
              <span className="text-xs font-bold uppercase tracking-wider text-sky-400 flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-sky-400" />
                Flujo de Aislamiento y Resolución de Datos
              </span>
              <span className="text-xs font-mono text-slate-500">Pipeline de Consulta</span>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
              {SECURITY_PIPELINE.map((p) => (
                <div
                  key={p.step}
                  className="p-3.5 rounded-xl bg-slate-950/80 border border-slate-800/90 hover:border-sky-500/40 transition-all duration-300 text-center space-y-1 relative group"
                >
                  <span className="text-[10px] font-mono text-sky-400 font-bold block">{p.step}</span>
                  <p className="text-xs font-bold text-white group-hover:text-sky-300 transition-colors">{p.name}</p>
                  <p className="text-[10px] text-slate-400 leading-tight">{p.detail}</p>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 4 Pillars Grid */}
        <div ref={containerRef} className="grid grid-cols-1 md:grid-cols-2 gap-5 max-w-5xl mx-auto mb-16 sm:mb-20">
          {SECURITY_PILLARS.map((p) => {
            const Icon = p.icon;
            return (
              <div
                key={p.title}
                className="security-card p-6 sm:p-7 rounded-2xl bg-slate-900/60 backdrop-blur-md border border-slate-800/90 hover:border-sky-500/40 hover:shadow-xl hover:shadow-sky-500/5 hover:-translate-y-1 transition-all duration-300 space-y-3"
              >
                <div className="w-10 h-10 rounded-xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-400 shadow-inner">
                  <Icon className="w-5 h-5" />
                </div>
                <h3 className="text-base font-bold text-white font-['Plus_Jakarta_Sans']">
                  {p.title}
                </h3>
                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                  {p.desc}
                </p>
              </div>
            );
          })}
        </div>

        {/* Ley 675 Institutional Block */}
        <div className="max-w-4xl mx-auto p-6 sm:p-8 rounded-2xl bg-gradient-to-r from-slate-900 via-slate-900/90 to-slate-900 border border-slate-800 flex flex-col sm:flex-row items-start sm:items-center gap-5">
          <div className="w-12 h-12 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400 shrink-0">
            <Scale className="w-6 h-6" />
          </div>
          <div className="space-y-1">
            <span className="text-[11px] font-bold uppercase tracking-wider text-amber-400 block">
              Marco Legal Colombiano
            </span>
            <h4 className="text-base font-bold text-white font-['Plus_Jakarta_Sans']">
              Diseñado bajo el marco operativo de la propiedad horizontal en Colombia (Ley 675 de 2001)
            </h4>
            <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
              Estructurado para respaldar el censo de copropietarios, coeficientes de copropiedad, quórum de asambleas y rendición de cuentas conforme a las exigencias jurídicas colombianas.
            </p>
          </div>
        </div>

      </div>
    </section>
  );
}
