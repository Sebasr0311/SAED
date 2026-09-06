import { useEffect, useRef } from 'react';
import {
  ArrowRight,
  ChevronDown,
  Sparkles,
  ShieldCheck,
  Building,
  CheckCircle2,
  Lock,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { animate, createTimeline } from 'animejs';

export default function LandingHero() {
  const heroRef = useRef(null);
  const badgeRef = useRef(null);
  const titleRef = useRef(null);
  const subtitleRef = useRef(null);
  const ctaRef = useRef(null);
  const loginRef = useRef(null);
  const statsRef = useRef(null);
  const scrollBtnRef = useRef(null);
  const orb1Ref = useRef(null);
  const orb2Ref = useRef(null);

  useEffect(() => {
    // 1. Ambient atmospheric breathing animations with Anime.js v4
    if (orb1Ref.current && orb2Ref.current) {
      animate(orb1Ref.current, {
        translateY: [-22, 22],
        translateX: [-16, 16],
        scale: [1, 1.12],
        duration: 8000,
        direction: 'alternate',
        loop: true,
        ease: 'inOutSine',
      });

      animate(orb2Ref.current, {
        translateY: [20, -20],
        translateX: [14, -14],
        scale: [1.08, 0.94],
        duration: 9500,
        direction: 'alternate',
        loop: true,
        ease: 'inOutSine',
      });
    }

    // 2. Orchestrated Hero Entrance Timeline
    const tl = createTimeline({
      defaults: {
        ease: 'outExpo',
      },
    });

    if (badgeRef.current) {
      tl.add(badgeRef.current, {
        opacity: [0, 1],
        translateY: [-16, 0],
        scale: [0.94, 1],
        duration: 750,
      });
    }

    if (titleRef.current) {
      tl.add(
        titleRef.current,
        {
          opacity: [0, 1],
          translateY: [32, 0],
          duration: 900,
        },
        '-=450'
      );
    }

    if (subtitleRef.current) {
      tl.add(
        subtitleRef.current,
        {
          opacity: [0, 1],
          translateY: [20, 0],
          duration: 800,
        },
        '-=600'
      );
    }

    if (ctaRef.current) {
      tl.add(
        ctaRef.current,
        {
          opacity: [0, 1],
          translateY: [16, 0],
          scale: [0.97, 1],
          duration: 700,
        },
        '-=500'
      );
    }

    if (loginRef.current) {
      tl.add(
        loginRef.current,
        {
          opacity: [0, 1],
          translateY: [10, 0],
          duration: 600,
        },
        '-=450'
      );
    }

    if (statsRef.current) {
      tl.add(
        statsRef.current,
        {
          opacity: [0, 1],
          translateY: [16, 0],
          duration: 750,
        },
        '-=350'
      );
    }

    if (scrollBtnRef.current) {
      tl.add(
        scrollBtnRef.current,
        {
          opacity: [0, 1],
          duration: 700,
        },
        '-=300'
      );
    }
  }, []);

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
      ref={heroRef}
      className="relative pt-32 pb-20 sm:pt-40 sm:pb-28 lg:pt-48 lg:pb-32 overflow-hidden bg-gradient-to-b from-[#070B14] via-[#0D1527] to-[#070B14] text-white"
    >
      {/* Subtle Atmospheric Accents with Ambient Anime.js floating motion */}
      <div className="absolute inset-0 bg-[radial-gradient(#1E3A6E_1px,transparent_1px)] [background-size:36px_36px] opacity-15 pointer-events-none" />
      
      <div
        ref={orb1Ref}
        className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[720px] h-[360px] bg-gradient-to-tr from-cyan-500/15 to-sky-500/10 blur-[150px] rounded-full pointer-events-none"
      />
      <div
        ref={orb2Ref}
        className="absolute bottom-10 right-10 w-[520px] h-[300px] bg-gradient-to-tl from-sky-600/15 to-blue-600/10 blur-[140px] rounded-full pointer-events-none"
      />

      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
        <div className="max-w-4xl mx-auto space-y-6 sm:space-y-8">
          
          {/* Eyebrow badge */}
          <div
            ref={badgeRef}
            className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-slate-900/80 border border-sky-500/30 text-sky-400 text-xs font-bold tracking-widest uppercase shadow-lg shadow-sky-950/40 backdrop-blur-xl"
          >
            <Sparkles className="w-3.5 h-3.5 text-sky-400 animate-pulse" />
            <span>PLATAFORMA PROPTECH ENTERPRISE</span>
          </div>

          {/* Monumental Headline */}
          <h1
            ref={titleRef}
            className="text-4xl sm:text-6xl md:text-7xl lg:text-[5.5rem] font-extrabold tracking-tight text-white leading-[1.06] font-['Plus_Jakarta_Sans']"
          >
            Todo tu conjunto.<br />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-white via-cyan-200 to-sky-400 drop-shadow-sm">
              En un solo lugar.
            </span>
          </h1>

          {/* Editorial Subheadline */}
          <p
            ref={subtitleRef}
            className="text-lg sm:text-xl lg:text-2xl text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto"
          >
            Administración, residentes, portería, cartera y operación conectados en una sola plataforma.
          </p>

          {/* Primary Action Group */}
          <div
            ref={ctaRef}
            className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-2"
          >
            <a
              href="#roles"
              onClick={(e) => {
                e.preventDefault();
                scrollTo('#roles');
              }}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-9 py-4 text-base font-bold text-slate-950 bg-gradient-to-r from-cyan-400 via-sky-400 to-cyan-400 hover:from-cyan-300 hover:to-sky-300 rounded-xl shadow-xl shadow-sky-500/25 hover:shadow-sky-400/40 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 transition-all transform active:scale-[0.98] min-h-[50px]"
            >
              <span>Ver cómo funciona</span>
              <ArrowRight className="w-4 h-4" />
            </a>
          </div>

          {/* Direct Login Link */}
          <div ref={loginRef} className="pt-2">
            <Link
              to="/login"
              className="inline-flex items-center gap-1.5 text-xs sm:text-sm text-slate-400 hover:text-sky-300 transition-colors py-1.5 focus:outline-none focus-visible:underline"
            >
              <span>¿Ya eres usuario? Iniciar sesión</span>
              <ArrowRight className="w-3.5 h-3.5 text-sky-400" />
            </Link>
          </div>

          {/* Live Operational Ticker Strip */}
          <div
            ref={statsRef}
            className="pt-6 max-w-3xl mx-auto grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs"
          >
            <div className="p-3 rounded-xl bg-slate-900/70 border border-slate-800/80 backdrop-blur-md flex items-center gap-2.5">
              <Building className="w-4 h-4 text-sky-400 shrink-0" />
              <div className="text-left">
                <span className="font-bold text-white font-mono block">130+ Unidades</span>
                <span className="text-[10px] text-slate-400">Entorno Demo Activo</span>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-slate-900/70 border border-slate-800/80 backdrop-blur-md flex items-center gap-2.5">
              <Lock className="w-4 h-4 text-emerald-400 shrink-0" />
              <div className="text-left">
                <span className="font-bold text-white font-mono block">0 Fugas RLS</span>
                <span className="text-[10px] text-slate-400">Oracle VPD Aislado</span>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-slate-900/70 border border-slate-800/80 backdrop-blur-md flex items-center gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-teal-400 shrink-0" />
              <div className="text-left">
                <span className="font-bold text-white font-mono block">PSE &amp; Tarjetas</span>
                <span className="text-[10px] text-slate-400">Pasarela Wompi</span>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-slate-900/70 border border-slate-800/80 backdrop-blur-md flex items-center gap-2.5">
              <ShieldCheck className="w-4 h-4 text-amber-400 shrink-0" />
              <div className="text-left">
                <span className="font-bold text-white font-mono block">Ley 675 / 2001</span>
                <span className="text-[10px] text-slate-400">Marco Legal CO</span>
              </div>
            </div>
          </div>

          {/* Scroll Down Indicator */}
          <div ref={scrollBtnRef} className="pt-6 flex justify-center">
            <button
              type="button"
              onClick={() => scrollTo('#producto')}
              className="p-2.5 rounded-full text-slate-400 hover:text-white hover:bg-white/5 transition-all animate-bounce focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400"
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
