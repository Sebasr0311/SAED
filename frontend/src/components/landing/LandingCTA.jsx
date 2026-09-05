import { ArrowRight, ShieldCheck, CheckCircle2, Sparkles, LogIn } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function LandingCTA() {
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
    <section className="py-24 sm:py-32 lg:py-36 bg-[#0A1628] text-white relative overflow-hidden border-t border-slate-800/80">
      {/* Cinematic subtle glow background effects */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[400px] bg-emerald-500/10 rounded-full blur-[140px] pointer-events-none" />

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10 text-center">
        
        {/* Eyebrow */}
        <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full text-xs font-bold uppercase tracking-widest bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 mb-8 backdrop-blur-md">
          <Sparkles className="w-3.5 h-3.5" />
          <span>OPERACIÓN RESIDENCIAL DE GRADO EMPRESARIAL</span>
        </div>

        {/* Monumental Headline */}
        <h2 className="text-4xl sm:text-6xl md:text-7xl lg:text-[5.5rem] font-extrabold tracking-tight text-white leading-[1.06] font-['Plus_Jakarta_Sans']">
          Una propiedad.<br />
          Una plataforma.<br />
          <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 via-teal-300 to-cyan-300">
            SAED.
          </span>
        </h2>

        {/* Editorial Subheadline */}
        <p className="mt-6 text-base sm:text-xl text-slate-300 max-w-2xl mx-auto leading-relaxed font-normal">
          Conecta administración, residentes y operación en un solo lugar.
        </p>

        {/* Action CTAs */}
        <div className="mt-10 flex flex-col sm:flex-row items-center justify-center gap-4">
          <a
            href="#producto"
            onClick={(e) => {
              e.preventDefault();
              scrollTo('#producto');
            }}
            className="w-full sm:w-auto px-9 py-4 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-bold text-base shadow-xl shadow-emerald-950/50 hover:shadow-emerald-900/60 transition-all flex items-center justify-center gap-2 min-h-[50px] transform active:scale-[0.99]"
          >
            <span>Conocer SAED</span>
            <ArrowRight className="w-4 h-4" />
          </a>

          <Link
            to="/login"
            className="w-full sm:w-auto px-8 py-4 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-200 hover:text-white font-semibold text-base border border-slate-700 transition-all flex items-center justify-center gap-2 min-h-[50px]"
          >
            <LogIn className="w-4 h-4 text-emerald-400" />
            <span>Iniciar sesión</span>
          </Link>
        </div>

        {/* Trust Indicators */}
        <div className="mt-14 pt-8 border-t border-slate-800/80 flex flex-wrap items-center justify-center gap-6 sm:gap-10 text-xs text-slate-400">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Acceso web inmediato sin descargas</span>
          </div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Aislamiento Multi-Tenant verificado</span>
          </div>
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
            <span>Integración oficial pasarela Wompi</span>
          </div>
        </div>

      </div>
    </section>
  );
}
