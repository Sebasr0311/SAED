import { ArrowRight, ChevronDown } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function LandingHero() {
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
    <section
      id="hero"
      className="relative pt-32 pb-20 sm:pt-40 sm:pb-28 lg:pt-48 lg:pb-32 overflow-hidden bg-gradient-to-b from-[#0A1628] via-[#0F1D36] to-[#0A1628] text-white"
    >
      {/* Subtle Atmospheric Accents */}
      <div className="absolute inset-0 bg-[radial-gradient(#1E4080_1px,transparent_1px)] [background-size:32px_32px] opacity-15 pointer-events-none" />
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[650px] h-[320px] bg-emerald-500/10 blur-[130px] rounded-full pointer-events-none" />
      <div className="absolute bottom-10 right-10 w-[450px] h-[250px] bg-blue-600/10 blur-[120px] rounded-full pointer-events-none" />

      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
        <div className="max-w-4xl mx-auto space-y-6 sm:space-y-8">
          
          {/* Eyebrow */}
          <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/25 text-emerald-400 text-xs font-bold tracking-widest uppercase shadow-sm backdrop-blur-md">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <span>PLATAFORMA PROPTECH ENTERPRISE</span>
          </div>

          {/* Monumental Headline */}
          <h1 className="text-4xl sm:text-6xl md:text-7xl lg:text-[5.5rem] font-extrabold tracking-tight text-white leading-[1.06] font-['Plus_Jakarta_Sans']">
            Todo tu conjunto.<br />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 via-teal-300 to-cyan-300">
              En un solo lugar.
            </span>
          </h1>

          {/* Editorial Subheadline */}
          <p className="text-lg sm:text-xl lg:text-2xl text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Administración, residentes, portería, cartera y operación conectados en una sola plataforma.
          </p>

          {/* Primary Action Group */}
          <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-2">
            <a
              href="#producto"
              onClick={(e) => {
                e.preventDefault();
                scrollTo('#producto');
              }}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-8 py-4 text-base font-bold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-xl shadow-xl shadow-emerald-950/50 hover:shadow-emerald-900/60 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 transition-all transform active:scale-[0.99] min-h-[50px]"
            >
              <span>Conocer SAED</span>
              <ArrowRight className="w-4 h-4" />
            </a>

            <a
              href="#planes"
              onClick={(e) => {
                e.preventDefault();
                scrollTo('#planes');
              }}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-8 py-4 text-base font-semibold text-slate-200 hover:text-white bg-white/5 hover:bg-white/10 border border-slate-700/80 rounded-xl transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-400 min-h-[50px]"
            >
              <span>Ver planes</span>
            </a>
          </div>

          {/* Direct Login Link */}
          <div className="pt-2">
            <Link
              to="/login"
              className="inline-flex items-center gap-1.5 text-xs sm:text-sm text-slate-400 hover:text-emerald-300 transition-colors py-1.5 focus:outline-none focus-visible:underline"
            >
              <span>¿Ya eres usuario? Iniciar sesión</span>
              <ArrowRight className="w-3.5 h-3.5 text-emerald-400" />
            </Link>
          </div>

          {/* Scroll Down Indicator */}
          <div className="pt-10 flex justify-center">
            <button
              type="button"
              onClick={() => scrollTo('#producto')}
              className="p-2 rounded-full text-slate-500 hover:text-slate-300 hover:bg-white/5 transition-colors animate-bounce focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
              aria-label="Desplazarse hacia el showcase de producto"
            >
              <ChevronDown className="w-5 h-5" />
            </button>
          </div>

        </div>
      </div>
    </section>
  );
}
