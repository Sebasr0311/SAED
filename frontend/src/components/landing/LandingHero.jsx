import { useEffect, useRef } from 'react';
import { ArrowRight, Building2, ChevronDown, CheckCircle2, Lock, ShieldCheck, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import { animate, createTimeline } from 'animejs';

const HERO_IMAGE = 'https://hebbkx1anhila5yf.public.blob.vercel-storage.com/WhatsApp%20Image%202026-09-06%20at%206.39.14%20PM-ujE9VaFjw7dhhyJKvkWpJiEpKmtoaM.jpeg';
const SAED_EMBLEM = 'https://hebbkx1anhila5yf.public.blob.vercel-storage.com/saed_logo_emblem_only%20%281%29-f69pKiXJhvmpHezDHIpHBhzbADaXx7.png';

const metrics = [
  { icon: Building2, value: '+138', label: 'Unidades en gestión', tone: 'text-sky-300' },
  { icon: Lock, value: '8', label: 'Fugas RLS', detail: 'Oracle VPD Aislado', tone: 'text-sky-300' },
  { icon: ShieldCheck, value: 'PSE & Tarjetas', label: 'Pasarela Wompi', tone: 'text-sky-300' },
  { icon: CheckCircle2, value: 'Ley 675 / 2001', label: 'Marco Legal CO', tone: 'text-sky-300' },
];

export default function LandingHero() {
  const contentRef = useRef(null);
  const panelRef = useRef(null);

  useEffect(() => {
    const timeline = createTimeline({ defaults: { ease: 'outExpo' } });
    if (contentRef.current) {
      timeline.add(contentRef.current, { opacity: [0, 1], translateY: [28, 0], duration: 900 });
    }
    if (panelRef.current) {
      timeline.add(panelRef.current, { opacity: [0, 1], translateX: [24, 0], duration: 800 }, '-=600');
    }
    return () => timeline.pause();
  }, []);

  const scrollTo = (id) => {
    const element = document.querySelector(id);
    if (element) element.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  return (
    <section id="hero" className="relative isolate min-h-[760px] overflow-hidden bg-[#071016] text-white">
      <div className="absolute inset-0 -z-20 bg-cover bg-center" style={{ backgroundImage: `url(${HERO_IMAGE})` }} aria-hidden="true" />
      <div className="absolute inset-0 -z-10 bg-[linear-gradient(90deg,rgba(3,10,14,1)_0%,rgba(3,10,14,.98)_28%,rgba(3,10,14,.45)_65%,rgba(3,10,14,.5)_100%)]" aria-hidden="true" />
      <div className="absolute inset-0 -z-10 bg-[linear-gradient(180deg,rgba(3,10,14,.3),rgba(3,10,14,.08)_52%,#071016_100%)]" aria-hidden="true" />

      <div className="mx-auto flex min-h-[760px] max-w-7xl flex-col justify-center px-5 pb-32 pt-36 sm:px-8 lg:px-12">
        <div ref={contentRef} className="max-w-2xl">
          <img src={SAED_EMBLEM} alt="Emblema SAED" className="mb-6 h-14 w-14 rounded-2xl bg-white/95 p-1 object-contain shadow-lg shadow-cyan-950/30" />
          <div className="mb-7 inline-flex items-center gap-2 rounded-full border border-sky-400/45 bg-slate-950/35 px-3.5 py-1.5 text-[10px] font-bold tracking-[.16em] text-sky-300 backdrop-blur-md sm:text-xs">
            <Sparkles className="h-3.5 w-3.5" /> PLATAFORMA PROPTECH ENTERPRISE
          </div>
          <h1 className="max-w-2xl text-balance text-5xl font-extrabold leading-[.98] tracking-[-.045em] sm:text-7xl lg:text-[5.35rem]">
            Todo tu conjunto.<br />
            En un <span className="text-sky-300">solo lugar.</span>
          </h1>
          <p className="mt-7 max-w-xl text-pretty text-base leading-7 text-slate-200 sm:text-xl sm:leading-8">
            Administración, residentes, portería, cartera y operación conectados en una sola plataforma.
          </p>
          <div className="mt-9 flex flex-col items-start gap-4 sm:flex-row sm:items-center">
            <a href="#roles" onClick={(event) => { event.preventDefault(); scrollTo('#roles'); }} className="inline-flex min-h-14 items-center gap-3 rounded-full bg-sky-300 px-7 text-sm font-bold text-slate-950 shadow-[0_12px_35px_rgba(56,189,248,.24)] transition hover:bg-sky-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-200">
              Ver cómo funciona <ArrowRight className="h-4 w-4" />
            </a>
            <Link to="/login" className="inline-flex min-h-12 items-center gap-2 px-2 text-sm font-medium text-slate-200 transition hover:text-sky-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-300">
              Iniciar sesión <ArrowRight className="h-4 w-4 text-sky-300" />
            </Link>
          </div>
        </div>

        <div ref={panelRef} className="pointer-events-none absolute right-[5%] top-[29%] hidden w-44 rounded-2xl border border-white/15 bg-slate-950/55 p-4 text-sm shadow-2xl backdrop-blur-xl lg:block">
          <div className="mb-4 flex items-center gap-2 text-sky-300"><Building2 className="h-4 w-4" /><span className="font-semibold">Inicio</span></div>
          {['Residentes', 'Cartera', 'Operación', 'Reportes'].map((item) => <div key={item} className="flex items-center gap-2 py-2 text-xs text-slate-300"><span className="h-1.5 w-1.5 rounded-full bg-slate-500" />{item}</div>)}
        </div>

        <div className="absolute bottom-8 left-5 right-5 grid grid-cols-2 overflow-hidden rounded-2xl border border-white/15 bg-slate-950/60 backdrop-blur-xl sm:left-8 sm:right-8 sm:grid-cols-4 lg:left-12 lg:right-12">
          {metrics.map(({ icon: Icon, value, label, detail, tone }, index) => (
            <div key={value} className={`flex items-center gap-3 px-4 py-4 sm:px-6 ${index > 0 ? 'border-t border-white/10 lg:border-l lg:border-t-0' : ''}`}>
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-sky-400/10"><Icon className={`h-5 w-5 ${tone}`} /></div>
              <div className="min-w-0"><div className="truncate text-sm font-bold text-white">{value}</div><div className="truncate text-[11px] text-slate-300">{label}</div>{detail && <div className="truncate text-[10px] text-sky-300">{detail}</div>}</div>
            </div>
          ))}
        </div>
      </div>
      <button type="button" onClick={() => scrollTo('#producto')} className="absolute bottom-2 left-1/2 hidden -translate-x-1/2 p-2 text-slate-300 transition hover:text-white lg:block" aria-label="Desplazarse hacia el producto"><ChevronDown className="h-5 w-5 animate-bounce" /></button>
    </section>
  );
}
