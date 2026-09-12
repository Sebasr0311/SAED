import { ArrowRight, ShieldCheck, CheckCircle2, Sparkles, LogIn } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useScrollReveal } from '../../lib/animations.js';

export default function LandingCTA() {
  const containerRef = useScrollReveal({
    distance: 24,
    duration: 800,
  });

  const scrollTo = (id) => {
    const el = document.querySelector(id);
    if (el) {
      const topOffset = 84;
      const elementPosition = el.getBoundingClientRect().top;
      const offsetPosition = elementPosition + window.pageYOffset - topOffset;
      window.scrollTo({ top: offsetPosition, behavior: 'smooth' });
    }
  };

  return (
    <section className="py-24 sm:py-32 lg:py-36 bg-[#070B14] text-white relative overflow-hidden border-t border-slate-800/80">
      {/* Cinematic subtle glow background effects */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[750px] h-[420px] bg-gradient-to-tr from-sky-500/10 to-cyan-500/10 rounded-full blur-[160px] pointer-events-none" />

      <div ref={containerRef} className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 text-center">
        
        {/* Eyebrow */}
        <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full text-xs font-bold uppercase tracking-widest bg-sky-500/10 text-sky-400 border border-sky-500/20 mb-8 backdrop-blur-md">
          <Sparkles className="w-3.5 h-3.5 text-sky-400" />
          <span>OPERACIÓN RESIDENCIAL DE GRADO EMPRESARIAL</span>
        </div>

        {/* Monumental Headline */}
        <h2 className="text-4xl sm:text-6xl md:text-7xl lg:text-[5.5rem] font-extrabold tracking-tight text-white leading-[1.06] font-['Plus_Jakarta_Sans']">
          Una propiedad.<br />
          Una plataforma.<br />
          <span className="text-sky-400 font-black">
            SAED.
          </span>
        </h2>

        {/* Editorial Subheadline */}
        <p className="mt-6 text-base sm:text-xl text-slate-300 max-w-2xl mx-auto leading-relaxed font-normal">
          Conecta administración, residentes y operación en un solo lugar.
        </p>

        {/* Action CTAs */}
        <div className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link
            to="/suscripciones"
            className="w-full sm:w-auto px-9 py-4 rounded-xl bg-gradient-to-r from-cyan-400 via-sky-400 to-cyan-400 hover:from-cyan-300 hover:to-sky-300 text-slate-950 font-bold text-base shadow-xl shadow-sky-500/25 hover:shadow-sky-400/40 transition-all flex items-center justify-center gap-2 min-h-[50px] transform active:scale-[0.98]"
          >
            <span>Ver planes y suscripciones</span>
            <ArrowRight className="w-4 h-4" />
          </Link>

          <Link
            to="/login"
            className="w-full sm:w-auto px-8 py-4 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] text-slate-200 hover:text-white font-semibold text-base border border-white/10 hover:border-white/20 backdrop-blur-md transition-all flex items-center justify-center gap-2 min-h-[50px]"
          >
            <LogIn className="w-4 h-4 text-sky-400" />
            <span>Iniciar sesión</span>
          </Link>
        </div>

        {/* Trust Indicators */}
        <div className="mt-14 pt-8 border-t border-slate-800/80 flex flex-wrap items-center justify-center gap-6 sm:gap-10 text-xs text-slate-400">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-sky-400 shrink-0" />
            <span>Acceso web inmediato sin descargas</span>
          </div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-sky-400 shrink-0" />
            <span>Aislamiento Multi-Tenant verificado</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-sky-400 shrink-0" />
            <span>Integración oficial pasarela Wompi</span>
          </div>
        </div>

      </div>
    </section>
  );
}
