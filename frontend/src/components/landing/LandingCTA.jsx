import { ArrowRight, ShieldCheck, Sparkles, CheckCircle2 } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function LandingCTA() {
  return (
    <section className="py-28 sm:py-36 bg-[#0A1628] text-white relative overflow-hidden border-t border-slate-800">
      {/* Cinematic subtle glow background effects */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[450px] bg-primary/20 rounded-full blur-[140px] pointer-events-none opacity-60" />
      <div className="absolute top-0 right-1/4 w-[400px] h-[300px] bg-emerald-500/10 rounded-full blur-[100px] pointer-events-none" />

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 text-center">
        {/* Subtle Eyebrow */}
        <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full text-xs font-bold uppercase tracking-widest bg-white/10 text-emerald-400 border border-white/10 mb-8 backdrop-blur-md">
          <Sparkles className="w-3.5 h-3.5" />
          <span>OPERACIÓN RESIDENCIAL DE GRADO EMPRESARIAL</span>
        </div>

        {/* Monumental Headline */}
        <h2 className="text-4xl sm:text-6xl lg:text-7xl font-extrabold tracking-tight text-white leading-[1.08] font-['Plus_Jakarta_Sans']">
          Una propiedad. <br className="hidden sm:inline" />
          Una plataforma. <br />
          <span className="bg-clip-text text-transparent bg-gradient-to-r from-blue-400 via-indigo-200 to-emerald-400">
            SAED.
          </span>
        </h2>

        {/* Editorial Subheadline */}
        <p className="mt-6 text-lg sm:text-xl text-slate-300 max-w-2xl mx-auto leading-relaxed font-normal">
          Empieza a transformar la operación, seguridad y finanzas de tu copropiedad hoy mismo con la plataforma PropTech más avanzada de Colombia.
        </p>

        {/* Dual Primary Action CTAs */}
        <div className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link
            to="/login"
            className="w-full sm:w-auto px-9 py-4 rounded-2xl bg-primary text-white font-bold text-base shadow-xl shadow-primary/30 hover:bg-primary/90 hover:scale-[1.02] active:scale-[0.99] transition-all flex items-center justify-center gap-2.5 group min-h-[52px]"
          >
            <span>Entrar a la plataforma</span>
            <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
          </Link>

          <a
            href="#planes"
            className="w-full sm:w-auto px-8 py-4 rounded-2xl bg-white/10 text-white font-semibold text-base border border-white/15 hover:bg-white/15 transition-all flex items-center justify-center gap-2 min-h-[52px]"
          >
            <span>Ver planes y cotización</span>
          </a>
        </div>

        {/* Trust Indicators */}
        <div className="mt-12 pt-8 border-t border-white/10 flex flex-wrap items-center justify-center gap-6 sm:gap-10 text-xs sm:text-sm text-slate-400">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Acceso web inmediato desde cualquier navegador</span>
          </div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Datos protegidos bajo arquitectura multi-tenant</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Demostración guiada con datos reales</span>
          </div>
        </div>
      </div>
    </section>
  );
}
