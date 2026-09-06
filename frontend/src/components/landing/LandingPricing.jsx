import { useState, Fragment } from 'react';
import { Check, ArrowRight, Building2, Layers, Crown, ChevronDown, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useScrollReveal } from '../../lib/animations.js';

const PLANS = [
  {
    name: 'Básico Residencial',
    icon: Building2,
    target: 'Torres individuales o comunidades de hasta 50 unidades.',
    description: 'Control de acceso moderno para visitas y censo poblacional sin complicaciones de hardware.',
    tag: 'Acceso y Garita',
    highlighted: false,
    features: [
      'Pases de acceso con código QR dinámico',
      'Consola web para garita (PC o tablet)',
      'Portal web para residentes sin descargas',
      'Directorio de unidades y copropietarios',
      'Aislamiento estricto de base de datos',
    ],
    ctaText: 'Solicitar cotización',
  },
  {
    name: 'Profesional Condominio',
    icon: Layers,
    target: 'Conjuntos cerrados y urbanizaciones de 51 a 200 unidades.',
    description: 'Gestión operativa completa con recaudo en línea Wompi, custodia por PIN y parqueaderos.',
    tag: 'Más Elegido',
    highlighted: true,
    features: [
      'Todo lo incluido en el plan Básico',
      'Recaudo en línea con pasarela Wompi (PSE, tarjetas)',
      'Custodia y entrega de paquetes con PIN de 6 dígitos',
      'Control dinámico de bahías de visitantes y placas',
      'Emisión de estados de cuenta y paz y salvos',
      'Módulo de PQRS con trazabilidad de respuestas',
    ],
    ctaText: 'Agendar demostración',
  },
  {
    name: 'Empresarial Multi-Torre',
    icon: Crown,
    target: 'Macro-proyectos, complejos mixtos o administradoras inmobiliarias.',
    description: 'Supervisión centralizada multi-propiedad con reportería avanzada y gobernanza de asambleas.',
    tag: 'Corporativo',
    highlighted: false,
    features: [
      'Todo lo incluido en el plan Profesional',
      'Gestión centralizada de múltiples copropiedades',
      'Asambleas, votaciones y coeficientes Ley 675',
      'Múltiples garitas o accesos vehiculares concurrentes',
      'Exportación avanzada de auditoría y conciliación',
      'Acompañamiento y capacitación para personal de garita',
    ],
    ctaText: 'Consultar corporativo',
  },
];

const MATRIX_CATEGORIES = [
  {
    name: 'Control de Acceso y Portería',
    items: [
      { feature: 'Pases de visita con código QR', basic: true, pro: true, enterprise: true },
      { feature: 'Consola web de portería con validación en pantalla', basic: true, pro: true, enterprise: true },
      { feature: 'Bitácora inmutable de ingresos y salidas', basic: 'Estándar', pro: 'Detallada', enterprise: 'Auditoría Total' },
      { feature: 'Control de bahías de estacionamiento de visitantes', basic: false, pro: true, enterprise: true },
      { feature: 'Garitas o accesos vehiculares concurrentes', basic: '1 punto', pro: 'Hasta 2', enterprise: 'Ilimitados' },
    ],
  },
  {
    name: 'Logística y Operaciones',
    items: [
      { feature: 'Custodia de paquetes con PIN criptográfico', basic: false, pro: true, enterprise: true },
      { feature: 'Notificación de correspondencia en portal de habitante', basic: false, pro: true, enterprise: true },
      { feature: 'Directorio y censo de habitantes por unidad', basic: 'Hasta 50', pro: 'Hasta 200', enterprise: 'Sin límite' },
      { feature: 'Módulo oficial de radicación de PQRS', basic: false, pro: true, enterprise: true },
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
      { feature: 'Aislamiento Multi-Tenant en base de datos (RLS)', basic: true, pro: true, enterprise: true },
      { feature: 'Gestión multi-propiedad para administradoras', basic: false, pro: false, enterprise: true },
      { feature: 'Módulo de asambleas y coeficientes Ley 675', basic: false, pro: false, enterprise: true },
    ],
  },
];

export default function LandingPricing() {
  const [showMatrix, setShowMatrix] = useState(false);
  const cardsRef = useScrollReveal({
    selector: '.pricing-card',
    stagger: 100,
    distance: 28,
  });

  return (
    <section
      id="planes"
      className="py-20 sm:py-28 lg:py-32 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[750px] h-[400px] bg-sky-500/5 blur-[160px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16 sm:mb-20">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20">
            <Sparkles className="w-3.5 h-3.5 text-sky-400" />
            ESCALABILIDAD Y ALCANCE
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Planes transparentes adaptados al tamaño de tu copropiedad
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Cotización basada en la escala de unidades habitacionales. Sin costos ocultos por hardware propietario ni contratos de permanencia forzosa.
          </p>
        </div>

        {/* 3 Editorial Tiers Grid */}
        <div ref={cardsRef} className="grid grid-cols-1 lg:grid-cols-3 gap-6 max-w-6xl mx-auto mb-12">
          {PLANS.map((plan) => {
            const Icon = plan.icon;
            return (
              <div
                key={plan.name}
                className={`pricing-card p-7 sm:p-8 rounded-3xl border flex flex-col justify-between transition-all duration-300 ${
                  plan.highlighted
                    ? 'bg-gradient-to-b from-[#0F224A] via-[#09152E] to-[#070E1E] border-sky-400/50 shadow-2xl shadow-sky-950/40 relative lg:-translate-y-2 hover:border-sky-400 hover:shadow-sky-500/10'
                    : 'bg-slate-900/60 backdrop-blur-md border-slate-800 hover:border-slate-700 hover:shadow-xl hover:-translate-y-1'
                }`}
              >
                <div className="space-y-5">
                  <div className="flex items-center justify-between">
                    <div className="w-11 h-11 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center text-emerald-400">
                      <Icon className="w-5 h-5" />
                    </div>
                    <span
                      className={`text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wider ${
                        plan.highlighted
                          ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
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
                    <p className="text-xs text-emerald-400 font-medium mt-0.5">{plan.target}</p>
                    <p className="text-xs sm:text-sm text-slate-300 mt-2 leading-relaxed">
                      {plan.description}
                    </p>
                  </div>

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
                      plan.highlighted
                        ? 'bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white shadow-lg shadow-emerald-950/50'
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

        {/* Matrix Toggle Button */}
        <div className="text-center mb-8">
          <button
            type="button"
            onClick={() => setShowMatrix((m) => !m)}
            className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-slate-900 border border-slate-700 text-slate-200 hover:text-white hover:border-slate-600 text-xs sm:text-sm font-semibold transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 min-h-[44px]"
            aria-expanded={showMatrix}
          >
            <span>{showMatrix ? 'Ocultar matriz comparativa' : 'Ver matriz comparativa completa'}</span>
            <ChevronDown className={`w-4 h-4 transition-transform duration-200 ${showMatrix ? 'rotate-180' : ''}`} />
          </button>
        </div>

        {/* Expandable Comparison Matrix */}
        {showMatrix && (
          <div className="max-w-5xl mx-auto rounded-3xl bg-slate-900/95 border border-slate-800 overflow-hidden shadow-2xl animate-in fade-in duration-200">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-bold uppercase tracking-wider">
                    <th className="p-4 sm:p-5 w-2/5">Capacidad / Módulo</th>
                    <th className="p-4 sm:p-5 text-center w-1/5">Básico</th>
                    <th className="p-4 sm:p-5 text-center w-1/5 text-emerald-400">Profesional</th>
                    <th className="p-4 sm:p-5 text-center w-1/5">Empresarial</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {MATRIX_CATEGORIES.map((cat) => (
                    <Fragment key={cat.name}>
                      <tr className="bg-slate-900/90 font-bold text-emerald-400">
                        <td colSpan={4} className="p-3 sm:px-5 text-[11px] uppercase tracking-wider">
                          {cat.name}
                        </td>
                      </tr>
                      {cat.items.map((row, i) => (
                        <tr key={i} className="hover:bg-slate-800/30 transition-colors">
                          <td className="p-4 sm:px-5 text-slate-200 font-medium">{row.feature}</td>
                          <td className="p-4 text-center">
                            {typeof row.basic === 'boolean' ? (
                              row.basic ? <Check className="w-4 h-4 text-emerald-400 mx-auto" /> : <span className="text-slate-600">—</span>
                            ) : (
                              <span className="text-slate-300">{row.basic}</span>
                            )}
                          </td>
                          <td className="p-4 text-center bg-emerald-500/[0.03]">
                            {typeof row.pro === 'boolean' ? (
                              row.pro ? <Check className="w-4 h-4 text-emerald-400 mx-auto" /> : <span className="text-slate-600">—</span>
                            ) : (
                              <span className="text-emerald-300 font-semibold">{row.pro}</span>
                            )}
                          </td>
                          <td className="p-4 text-center">
                            {typeof row.enterprise === 'boolean' ? (
                              row.enterprise ? <Check className="w-4 h-4 text-emerald-400 mx-auto" /> : <span className="text-slate-600">—</span>
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
