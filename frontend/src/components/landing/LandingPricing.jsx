import { useState, useEffect, useRef, Fragment } from 'react';
import {
  Check,
  ArrowRight,
  Building2,
  Layers,
  Crown,
  ChevronDown,
  Sparkles,
  Sliders,
  Calendar,
  CheckCircle2,
  TrendingDown,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { animate } from 'animejs';
import { useScrollReveal } from '../../lib/animations.js';

const BASE_RATES = {
  basic: {
    monthly: 1900,
    annual: 1520, // 20% discount
    minUnits: 20,
    maxUnits: 50,
  },
  pro: {
    monthly: 2600,
    annual: 2080,
    minUnits: 51,
    maxUnits: 200,
  },
  enterprise: {
    monthly: 3400,
    annual: 2720,
    minUnits: 201,
    maxUnits: 500,
  },
};

const PLANS = [
  {
    id: 'basic',
    name: 'Básico Residencial',
    icon: Building2,
    target: 'Comunidades o torres de hasta 50 unidades.',
    description: 'Control de acceso moderno para visitas y directorio de residentes sin complicaciones de hardware propietario.',
    tag: 'Acceso y Garita',
    features: [
      'Pases de visita con código QR dinámico',
      'Consola web para garita (PC o tablet)',
      'Portal web para residentes sin descargas',
      'Directorio de unidades y copropietarios',
      'Aislamiento estricto de base de datos (RLS)',
    ],
    ctaText: 'Solicitar cotización',
  },
  {
    id: 'pro',
    name: 'Profesional Condominio',
    icon: Layers,
    target: 'Conjuntos cerrados y urbanizaciones de 51 a 200 unidades.',
    description: 'Gestión operativa completa con recaudo en línea Wompi, custodia de paquetes por PIN de 6 dígitos y bahías de parqueadero.',
    tag: 'Más Elegido',
    features: [
      'Todo lo incluido en el plan Básico',
      'Recaudo en línea con pasarela Wompi (PSE y tarjetas)',
      'Custodia y entrega de paquetes con PIN de 6 dígitos',
      'Control dinámico de bahías de visitantes y placas',
      'Emisión de estados de cuenta y paz y salvos',
      'Módulo de PQRS con trazabilidad de respuestas',
    ],
    ctaText: 'Agendar demostración',
  },
  {
    id: 'enterprise',
    name: 'Empresarial Multi-Torre',
    icon: Crown,
    target: 'Macro-proyectos, complejos mixtos o administradoras de PH.',
    description: 'Supervisión centralizada multi-propiedad con reportería ejecutiva avanzada y gobernanza de asambleas bajo Ley 675.',
    tag: 'Corporativo',
    features: [
      'Todo lo incluido en el plan Profesional',
      'Gestión centralizada de múltiples copropiedades',
      'Asambleas, votaciones y coeficientes Ley 675',
      'Múltiples garitas o accesos vehiculares concurrentes',
      'Exportación avanzada de auditoría y conciliación bancaria',
      'Acompañamiento y capacitación para personal de garita',
    ],
    ctaText: 'Consultar corporativo',
  },
];

const MATRIX_CATEGORIES = [
  {
    name: 'Control de Acceso y Portería',
    items: [
      { feature: 'Pases de visita con código QR dinámico', basic: true, pro: true, enterprise: true },
      { feature: 'Consola web de portería con validación en pantalla', basic: true, pro: true, enterprise: true },
      { feature: 'Bitácora inmutable de ingresos y salidas', basic: 'Estándar', pro: 'Detallada', enterprise: 'Auditoría Total' },
      { feature: 'Control de bahías de estacionamiento de visitantes', basic: false, pro: true, enterprise: true },
      { feature: 'Garitas o accesos vehiculares concurrentes', basic: '1 garita', pro: 'Hasta 2', enterprise: 'Ilimitadas' },
    ],
  },
  {
    name: 'Logística y Operaciones',
    items: [
      { feature: 'Custodia de paquetes con PIN criptográfico de 6 dígitos', basic: false, pro: true, enterprise: true },
      { feature: 'Notificación de correspondencia en portal de habitante', basic: false, pro: true, enterprise: true },
      { feature: 'Directorio y censo de habitantes por unidad', basic: 'Hasta 50', pro: 'Hasta 200', enterprise: 'Sin límite' },
      { feature: 'Módulo oficial de radicación de PQRS con SLAs', basic: false, pro: true, enterprise: true },
    ],
  },
  {
    name: 'Finanzas y Recaudo',
    items: [
      { feature: 'Emisión digital de estados de cuenta', basic: true, pro: true, enterprise: true },
      { feature: 'Pasarela de pagos en línea Wompi (PSE / Tarjetas)', basic: false, pro: true, enterprise: true },
      { feature: 'Conciliación bancaria en tiempo real', basic: false, pro: true, enterprise: true },
      { feature: 'Generación automática de certificados de Paz y Salvo', basic: false, pro: true, enterprise: true },
    ],
  },
  {
    name: 'Seguridad y Plataforma',
    items: [
      { feature: 'Aislamiento Multi-Tenant en base de datos (Oracle VPD / RLS)', basic: true, pro: true, enterprise: true },
      { feature: 'Gestión multi-propiedad para administradoras', basic: false, pro: false, enterprise: true },
      { feature: 'Módulo de asambleas y coeficientes Ley 675', basic: false, pro: false, enterprise: true },
      { feature: 'Auditoría de seguridad y trazabilidad de operadores', basic: '30 días', pro: '1 año', enterprise: 'Indefinida' },
    ],
  },
];

function formatCOP(val) {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(val);
}

export default function LandingPricing() {
  const [billingCycle, setBillingCycle] = useState('annual'); // 'monthly' | 'annual'
  const [unitsCount, setUnitsCount] = useState(80);
  const [showMatrix, setShowMatrix] = useState(false);

  const orbRef = useRef(null);
  const matrixContainerRef = useRef(null);
  const cardsRef = useScrollReveal({
    selector: '.pricing-card',
    stagger: 100,
    distance: 28,
  });

  // Determine recommended plan based on slider
  const recommendedPlanId =
    unitsCount <= 50 ? 'basic' : unitsCount <= 200 ? 'pro' : 'enterprise';

  // Ambient breathing orb
  useEffect(() => {
    if (orbRef.current) {
      animate(orbRef.current, {
        translateY: [-20, 20],
        scale: [1, 1.12],
        duration: 9000,
        direction: 'alternate',
        loop: true,
        ease: 'inOutSine',
      });
    }
  }, []);

  // Animate Matrix table reveal when toggled
  useEffect(() => {
    if (showMatrix && matrixContainerRef.current) {
      animate(matrixContainerRef.current, {
        opacity: [0, 1],
        translateY: [20, 0],
        duration: 500,
        ease: 'outExpo',
      });
    }
  }, [showMatrix]);

  return (
    <section
      id="planes"
      className="py-20 sm:py-28 lg:py-32 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      {/* Ambient Breathing Background Glow */}
      <div
        ref={orbRef}
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[750px] h-[400px] bg-sky-500/5 blur-[160px] rounded-full pointer-events-none"
      />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-14 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 shadow-sm shadow-emerald-500/10">
            <Sparkles className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
            TARIFAS TRANSPARENTES Y ESCALABLES
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Inversión ajustada al tamaño exacto de tu copropiedad.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Sin costos ocultos por hardware propietario ni contratos de permanencia forzosa. Escala de licenciamiento transparente calculada por unidad residencial.
          </p>

          {/* Billing Cycle Switcher */}
          <div className="pt-4 flex flex-col sm:flex-row items-center justify-center gap-3">
            <div className="p-1 bg-slate-900/90 rounded-2xl border border-slate-800 flex items-center shadow-inner">
              <button
                type="button"
                onClick={() => setBillingCycle('monthly')}
                className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all ${
                  billingCycle === 'monthly'
                    ? 'bg-slate-800 text-white shadow-sm border border-slate-700'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Facturación Mensual
              </button>
              <button
                type="button"
                onClick={() => setBillingCycle('annual')}
                className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all flex items-center gap-2 ${
                  billingCycle === 'annual'
                    ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 shadow-sm'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                <span>Facturación Anual</span>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-emerald-500 text-slate-950">
                  -20% DCTO
                </span>
              </button>
            </div>
          </div>
        </div>

        {/* ========================================================================= */}
        {/* Interactive Unit Scale Estimator Widget */}
        {/* ========================================================================= */}
        <div className="max-w-4xl mx-auto mb-16 p-6 sm:p-8 rounded-3xl bg-slate-900/80 border border-slate-800/90 shadow-2xl backdrop-blur-xl">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-400">
                <Sliders className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-bold text-white font-['Plus_Jakarta_Sans']">
                  Estimador Interactivo de Escala
                </h3>
                <p className="text-xs text-slate-400">Desplaza para calcular la inversión estimada de tu conjunto</p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <span className="text-xs font-mono text-slate-400">Plan Recomendado:</span>
              <span className="px-3 py-1 rounded-full text-xs font-bold bg-sky-500/20 text-sky-300 border border-sky-500/30">
                {PLANS.find((p) => p.id === recommendedPlanId)?.name}
              </span>
            </div>
          </div>

          <div className="pt-6 space-y-6">
            {/* Slider Value Heading */}
            <div className="flex items-baseline justify-between">
              <span className="text-xs sm:text-sm font-semibold text-slate-300">
                Número de Unidades Habitacionales (Apartamentos / Casas):
              </span>
              <div className="text-right">
                <span className="text-3xl font-extrabold font-mono text-sky-400">
                  {unitsCount}
                </span>
                <span className="text-xs text-slate-400 ml-1.5 font-medium">unidades</span>
              </div>
            </div>

            {/* Custom Range Slider */}
            <div className="space-y-2">
              <input
                type="range"
                min="20"
                max="500"
                step="5"
                value={unitsCount}
                onChange={(e) => setUnitsCount(Number(e.target.value))}
                className="w-full h-2.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-sky-400 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400"
              />
              <div className="flex justify-between text-[11px] font-mono text-slate-500">
                <span>20 (Torre Única)</span>
                <span>80 (Conjunto Promedio)</span>
                <span>200 (Multi-Torre)</span>
                <span>500+ (Macro-Proyecto)</span>
              </div>
            </div>

            {/* Estimated Total Bar */}
            <div className="p-4 rounded-2xl bg-slate-950/80 border border-slate-800/80 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="space-y-1">
                <span className="text-xs font-bold text-slate-300 flex items-center gap-1.5">
                  <Calendar className="w-3.5 h-3.5 text-emerald-400" />
                  Inversión Mensual Estimada para {unitsCount} Unidades:
                </span>
                <p className="text-[11px] text-slate-400">
                  Tarifa base por unidad: {formatCOP(BASE_RATES[recommendedPlanId][billingCycle])} COP / mes
                  {billingCycle === 'annual' && ' (con 20% de descuento anual aplicado)'}.
                </p>
              </div>

              <div className="text-left sm:text-right">
                <div className="text-2xl sm:text-3xl font-extrabold font-mono text-emerald-400">
                  {formatCOP(unitsCount * BASE_RATES[recommendedPlanId][billingCycle])}
                </div>
                <span className="text-[10px] font-mono text-slate-500">COP / Mes (Liquidación {billingCycle === 'annual' ? 'Anual' : 'Mensual'})</span>
              </div>
            </div>
          </div>
        </div>

        {/* ========================================================================= */}
        {/* 3 Tier Plan Cards with Dynamic Highlight */}
        {/* ========================================================================= */}
        <div ref={cardsRef} className="grid grid-cols-1 lg:grid-cols-3 gap-6 max-w-6xl mx-auto mb-14">
          {PLANS.map((plan) => {
            const Icon = plan.icon;
            const isRecommended = plan.id === recommendedPlanId;
            const rate = BASE_RATES[plan.id][billingCycle];
            const estimatedTotal = unitsCount * rate;

            return (
              <div
                key={plan.id}
                className={`pricing-card p-7 sm:p-8 rounded-3xl border flex flex-col justify-between transition-all duration-300 relative ${
                  isRecommended
                    ? 'bg-gradient-to-b from-[#0F224A] via-[#09152E] to-[#070E1E] border-sky-400/80 shadow-2xl shadow-sky-950/60 lg:-translate-y-2 ring-1 ring-sky-400/40'
                    : 'bg-slate-900/60 backdrop-blur-md border-slate-800 hover:border-slate-700 hover:shadow-xl hover:-translate-y-1'
                }`}
              >
                {/* Floating Recommended Pill */}
                {isRecommended && (
                  <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-0.5 rounded-full bg-gradient-to-r from-sky-500 to-emerald-500 text-slate-950 text-[10px] font-extrabold uppercase tracking-wider shadow-md">
                    Recomendado para tu escala ({unitsCount} Uds.)
                  </div>
                )}

                <div className="space-y-5">
                  <div className="flex items-center justify-between">
                    <div
                      className={`w-11 h-11 rounded-2xl flex items-center justify-center ${
                        isRecommended
                          ? 'bg-sky-500/20 border border-sky-400/30 text-sky-300'
                          : 'bg-slate-900 border border-slate-800 text-slate-400'
                      }`}
                    >
                      <Icon className="w-5 h-5" />
                    </div>
                    <span
                      className={`text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wider ${
                        isRecommended
                          ? 'bg-sky-500/20 text-sky-300 border border-sky-500/30'
                          : 'bg-slate-800 text-slate-400 border border-slate-700'
                      }`}
                    >
                      {plan.tag}
                    </span>
                  </div>

                  <div>
                    <h3 className="text-xl font-bold text-white font-['Plus_Jakarta_Sans']">
                      {plan.name}
                    </h3>
                    <p className="text-xs text-sky-400 font-medium mt-0.5">{plan.target}</p>
                    <p className="text-xs sm:text-sm text-slate-300 mt-2 leading-relaxed">
                      {plan.description}
                    </p>
                  </div>

                  {/* Price Tag Display */}
                  <div className="p-3.5 rounded-2xl bg-slate-950/70 border border-slate-800/80 space-y-1">
                    <div className="flex items-baseline gap-1.5">
                      <span className="text-2xl font-extrabold font-mono text-white">
                        {formatCOP(rate)}
                      </span>
                      <span className="text-xs text-slate-400 font-medium">/ unidad / mes</span>
                    </div>
                    <p className="text-[11px] text-slate-400">
                      Total estimado para {unitsCount} uds: <span className="text-emerald-400 font-bold font-mono">{formatCOP(estimatedTotal)}</span> / mes
                    </p>
                  </div>

                  {/* Features List */}
                  <div className="pt-2 border-t border-slate-800/80 space-y-2.5 text-xs text-slate-200">
                    {plan.features.map((feat, i) => (
                      <div key={i} className="flex items-start gap-2.5">
                        <Check className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                        <span>{feat}</span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="pt-8 mt-6 border-t border-slate-800/60">
                  <Link
                    to="/login"
                    className={`w-full py-3.5 px-4 rounded-xl text-xs sm:text-sm font-bold text-center transition-all flex items-center justify-center gap-2 min-h-[48px] ${
                      isRecommended
                        ? 'bg-gradient-to-r from-sky-500 to-emerald-500 hover:from-sky-400 hover:to-emerald-400 text-slate-950 shadow-lg shadow-sky-950/50'
                        : 'bg-slate-800 hover:bg-slate-700 text-white border border-slate-700'
                    }`}
                  >
                    <span>{plan.ctaText}</span>
                    <ArrowRight className="w-4 h-4" />
                  </Link>
                </div>
              </div>
            );
          })}
        </div>

        {/* ========================================================================= */}
        {/* Capability Matrix Toggle Button */}
        {/* ========================================================================= */}
        <div className="text-center mb-8">
          <button
            type="button"
            onClick={() => setShowMatrix((m) => !m)}
            className="inline-flex items-center gap-2 px-6 py-3 rounded-2xl bg-slate-900/90 border border-slate-700 text-slate-200 hover:text-white hover:border-sky-500/40 text-xs sm:text-sm font-semibold transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 min-h-[44px] shadow-lg"
            aria-expanded={showMatrix}
          >
            <span>{showMatrix ? 'Ocultar matriz comparativa técnica' : 'Ver matriz comparativa completa de capacidades'}</span>
            <ChevronDown className={`w-4 h-4 transition-transform duration-200 ${showMatrix ? 'rotate-180' : ''}`} />
          </button>
        </div>

        {/* ========================================================================= */}
        {/* Expandable Comparison Matrix with Anime.js Reveal */}
        {/* ========================================================================= */}
        {showMatrix && (
          <div
            ref={matrixContainerRef}
            className="max-w-5xl mx-auto rounded-3xl bg-slate-900/95 border border-slate-800 overflow-hidden shadow-2xl backdrop-blur-xl"
          >
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-bold uppercase tracking-wider">
                    <th className="p-4 sm:p-5 w-2/5">Capacidad / Módulo</th>
                    <th className="p-4 sm:p-5 text-center w-1/5">Básico</th>
                    <th className="p-4 sm:p-5 text-center w-1/5 text-sky-400">Profesional</th>
                    <th className="p-4 sm:p-5 text-center w-1/5">Empresarial</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {MATRIX_CATEGORIES.map((cat) => (
                    <Fragment key={cat.name}>
                      <tr className="bg-slate-900/90 font-bold text-sky-400">
                        <td colSpan={4} className="p-3 sm:px-5 text-[11px] uppercase tracking-wider">
                          {cat.name}
                        </td>
                      </tr>
                      {cat.items.map((row, i) => (
                        <tr key={i} className="hover:bg-slate-800/30 transition-colors">
                          <td className="p-4 sm:px-5 text-slate-200 font-medium">{row.feature}</td>
                          <td className="p-4 text-center">
                            {typeof row.basic === 'boolean' ? (
                              row.basic ? (
                                <Check className="w-4 h-4 text-emerald-400 mx-auto" />
                              ) : (
                                <span className="text-slate-600">—</span>
                              )
                            ) : (
                              <span className="text-slate-300">{row.basic}</span>
                            )}
                          </td>
                          <td className="p-4 text-center bg-sky-500/[0.04]">
                            {typeof row.pro === 'boolean' ? (
                              row.pro ? (
                                <Check className="w-4 h-4 text-sky-400 mx-auto" />
                              ) : (
                                <span className="text-slate-600">—</span>
                              )
                            ) : (
                              <span className="text-sky-300 font-semibold">{row.pro}</span>
                            )}
                          </td>
                          <td className="p-4 text-center">
                            {typeof row.enterprise === 'boolean' ? (
                              row.enterprise ? (
                                <Check className="w-4 h-4 text-emerald-400 mx-auto" />
                              ) : (
                                <span className="text-slate-600">—</span>
                              )
                            ) : (
                              <span className="text-slate-200 font-semibold">{row.enterprise}</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

      </div>
    </section>
  );
}
