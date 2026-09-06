import { Link } from 'react-router-dom';
import {
  Sparkles,
  ArrowRight,
  Building2,
  Layers,
  Crown,
  CheckCircle2,
  ShieldCheck,
  Calculator,
} from 'lucide-react';

const TEASER_PLANS = [
  {
    name: 'Básico Residencial',
    icon: Building2,
    badge: 'Hasta 50 unidades',
    price: '$1.520',
    unitText: 'COP / unidad / mes (anual)',
    highlights: [
      'Pases QR dinámicos para visitas',
      'Consola web para garita en PC o tablet',
      'Portal web residentes sin descargas',
      'Aislamiento estricto de datos (RLS)',
    ],
    borderClass: 'border-slate-800 hover:border-slate-700',
  },
  {
    name: 'Profesional Condominio',
    icon: Layers,
    badge: '51 a 200 unidades · Más Elegido',
    featured: true,
    price: '$2.080',
    unitText: 'COP / unidad / mes (anual)',
    highlights: [
      'Recaudo en línea Wompi (PSE y tarjetas)',
      'Custodia y entrega por PIN de 6 dígitos',
      'Bahías de parqueadero en tiempo real',
      'Módulo de PQRS y estados de cuenta',
    ],
    borderClass: 'border-sky-500/50 shadow-lg shadow-sky-950/30',
  },
  {
    name: 'Empresarial Multi-Torre',
    icon: Crown,
    badge: '201+ unidades o Macro-proyectos',
    price: '$2.720',
    unitText: 'COP / unidad / mes (anual)',
    highlights: [
      'Gestión centralizada multi-propiedad',
      'Votación y quórum asambleas Ley 675',
      'Múltiples garitas o accesos vehiculares',
      'Auditoría y conciliación avanzada',
    ],
    borderClass: 'border-slate-800 hover:border-slate-700',
  },
];

export default function LandingPricingTeaser() {
  return (
    <section
      id="planes"
      className="py-16 sm:py-24 bg-gradient-to-b from-[#070B14] via-[#091122] to-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[350px] bg-sky-500/5 blur-[150px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-12 sm:mb-14">
          <div className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
            <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
            <span>SUSCRIPCIONES TRANSPARENTES</span>
          </div>

          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Tarifas diseñadas para la escala de tu copropiedad
          </h2>

          <p className="text-base sm:text-lg text-slate-300 max-w-2xl mx-auto leading-relaxed">
            Planes mensuales o anuales con 20% de ahorro. Sin costos de licenciamiento sorpresa ni equipos propietarios.
          </p>
        </div>

        {/* 3 Compact Plan Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 max-w-6xl mx-auto mb-10">
          {TEASER_PLANS.map((plan, idx) => {
            const Icon = plan.icon;
            return (
              <div
                key={idx}
                className={`rounded-2xl bg-slate-900/80 p-6 sm:p-7 border flex flex-col justify-between transition-all duration-300 hover:-translate-y-1 ${plan.borderClass} ${
                  plan.featured ? 'bg-gradient-to-b from-sky-950/20 via-slate-900/90 to-slate-900/90' : ''
                }`}
              >
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <div className="w-10 h-10 rounded-xl bg-slate-800 border border-slate-700 flex items-center justify-center text-sky-400">
                      <Icon className="w-5 h-5" />
                    </div>
                    <span
                      className={`text-[11px] font-bold px-2.5 py-0.5 rounded-full border ${
                        plan.featured
                          ? 'bg-sky-500/20 text-sky-300 border-sky-500/40'
                          : 'bg-slate-800 text-slate-400 border-slate-700'
                      }`}
                    >
                      {plan.badge}
                    </span>
                  </div>

                  <div>
                    <h3 className="text-lg font-bold text-white font-['Plus_Jakarta_Sans']">
                      {plan.name}
                    </h3>
                    <div className="mt-2 flex items-baseline gap-1.5">
                      <span className="text-2xl sm:text-3xl font-extrabold text-white font-mono">
                        {plan.price}
                      </span>
                      <span className="text-xs text-slate-400">{plan.unitText}</span>
                    </div>
                  </div>

                  <ul className="space-y-2.5 pt-2 border-t border-slate-800/80">
                    {plan.highlights.map((h, i) => (
                      <li key={i} className="flex items-start gap-2 text-xs sm:text-sm text-slate-300">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>{h}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
            );
          })}
        </div>

        {/* Central CTA Strip to the dedicated Subscriptions Page */}
        <div className="max-w-2xl mx-auto text-center space-y-4 pt-2">
          <Link
            to="/suscripciones"
            className="inline-flex items-center justify-center gap-2.5 px-8 py-4 text-sm sm:text-base font-bold text-slate-950 bg-gradient-to-r from-cyan-400 via-sky-400 to-cyan-400 hover:from-cyan-300 hover:to-sky-300 rounded-xl shadow-xl shadow-sky-500/25 hover:shadow-sky-400/40 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 transition-all transform active:scale-[0.98] min-h-[50px]"
          >
            <Calculator className="w-5 h-5 text-slate-950" />
            <span>Calcular tarifa exacta y ver comparativa completa</span>
            <ArrowRight className="w-4 h-4" />
          </Link>
          <p className="text-xs text-slate-400">
            Incluye calculadora interactiva por número de unidades y tabla detallada de capacidades.
          </p>
        </div>
      </div>
    </section>
  );
}
