import { useState, Fragment } from 'react';
import { Check, ArrowRight, ShieldCheck, Sparkles, Building2, Layers, Crown } from 'lucide-react';
import { Link } from 'react-router-dom';

const PLANS = [
  {
    name: 'Básico Residencial',
    icon: Building2,
    target: 'Para torres individuales o comunidades de hasta 60 unidades.',
    benefit: 'Digitalización ágil del control de acceso de visitas y portería sin infraestructura compleja.',
    priceBadge: 'Cotización según unidades',
    highlighted: false,
    badge: null,
    keyFeatures: [
      'Control de acceso de visitas con código QR dinámico',
      'Consola web para portería (PC o tablet, cero hardware propietario)',
      'Portal web para residentes (cero descargas obligatorias)',
      'Directorio de unidades y registro de residentes',
      'Aislamiento estricto de base de datos por copropiedad',
    ],
    ctaText: 'Solicitar cotización',
  },
  {
    name: 'Profesional Condominio',
    icon: Layers,
    target: 'Para conjuntos cerrados y urbanizaciones de 60 a 250 unidades.',
    benefit: 'Control operativo integral con recaudo digital, pasarela Wompi y custodia de paquetería por PIN.',
    priceBadge: 'Plan más seleccionado',
    highlighted: true,
    badge: 'Más Elegido',
    keyFeatures: [
      'Todo lo incluido en el plan Básico',
      'Recaudo digital integrado con pasarela Wompi (PSE, tarjetas)',
      'Custodia y entrega de paquetería con PIN criptográfico',
      'Control dinámico de parqueaderos de visitantes y placas',
      'Canal oficial de PQRS con trazabilidad de respuestas',
      'Estados de cuenta digitales y certificados de paz y salvo',
    ],
    ctaText: 'Agendar demostración',
  },
  {
    name: 'Empresarial Multi-Torre',
    icon: Crown,
    target: 'Para macro-proyectos, complejos mixtos o administradoras de múltiples copropiedades.',
    benefit: 'Supervisión consolidada con reportería avanzada de auditoría, asambleas y gestión multi-inmueble.',
    priceBadge: 'A la medida del portafolio',
    highlighted: false,
    badge: 'Corporativo',
    keyFeatures: [
      'Todo lo incluido en el plan Profesional',
      'Gestión multi-propiedad centralizada para administradoras',
      'Módulo de asambleas, votaciones y coeficientes Ley 675',
      'Múltiples bodegas de paquetería y varios puntos de acceso',
      'Exportación avanzada de auditoría y conciliación financiera',
      'Acompañamiento y capacitación personalizada para el equipo',
    ],
    ctaText: 'Consultar plan empresarial',
  },
];

const MATRIX_FEATURES = [
  {
    category: 'Control de Acceso y Portería',
    features: [
      { name: 'Generación de QR para visitantes', basic: true, pro: true, enterprise: true },
      { name: 'Escáner QR en portería con validación en pantalla', basic: true, pro: true, enterprise: true },
      { name: 'Bitácora de ingresos y salidas con operador', basic: 'Básica', pro: 'Completa', enterprise: 'Auditoría Total' },
      { name: 'Control de parqueaderos de visitantes y placas', basic: false, pro: true, enterprise: true },
      { name: 'Múltiples garitas o accesos vehiculares', basic: '1 punto', pro: 'Hasta 2', enterprise: 'Ilimitados' },
    ],
  },
  {
    category: 'Logística y Operaciones',
    features: [
      { name: 'Custodia y recepción de paquetes en portería', basic: 'Registro simple', pro: 'PIN criptográfico', enterprise: 'PIN + Múltiples bodegas' },
      { name: 'Notificación de correspondencia en portal de residente', basic: true, pro: true, enterprise: true },
      { name: 'Gestión de unidades y padrón de residentes', basic: 'Hasta 60', pro: 'Hasta 250', enterprise: 'Ilimitadas' },
      { name: 'Canal de PQRS con seguimiento de estados', basic: false, pro: true, enterprise: true },
    ],
  },
  {
    category: 'Finanzas y Recaudo',
    features: [
      { name: 'Emisión digital de estados de cuenta de administración', basic: true, pro: true, enterprise: true },
      { name: 'Pasarela de pagos en línea Wompi (PSE / Tarjetas)', basic: false, pro: true, enterprise: true },
      { name: 'Conciliación bancaria en tiempo real', basic: false, pro: true, enterprise: true },
      { name: 'Generación automática de certificados de paz y salvo', basic: false, pro: true, enterprise: true },
    ],
  },
  {
    category: 'Gobernanza y Seguridad',
    features: [
      { name: 'Aislamiento lógico de base de datos (VPD / RLS)', basic: true, pro: true, enterprise: true },
      { name: 'Módulo de asambleas y registro de coeficientes Ley 675', basic: false, pro: 'Opcional', enterprise: true },
      { name: 'Trazabilidad y auditoría de acciones operativas', basic: 'Estándar', pro: 'Avanzada', enterprise: 'Forense completa' },
      { name: 'Soporte y acompañamiento técnico', basic: 'Guía digital', pro: 'Prioritario', enterprise: 'Dedicado 24/7' },
    ],
  },
];

export default function LandingPricing() {
  const [showMatrix, setShowMatrix] = useState(false);

  return (
    <section id="planes" className="py-28 sm:py-36 bg-slate-50 dark:bg-[#070D18] border-t border-slate-200/80 dark:border-slate-800/80 relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-20 space-y-4">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest bg-primary/10 text-primary border border-primary/20">
            ESQUEMA COMERCIAL B2B
          </span>
          <h2 className="text-3xl sm:text-5xl font-extrabold text-slate-900 dark:text-white tracking-tight leading-tight font-['Plus_Jakarta_Sans']">
            Planes adaptados a la escala de tu comunidad.
          </h2>
          <p className="text-base sm:text-lg text-slate-600 dark:text-slate-400 leading-relaxed">
            Inversión proporcionada a las unidades y requerimientos de tu copropiedad. Sin costos sorpresa de infraestructura ni licencias individuales por residente.
          </p>
        </div>

        {/* Pricing Cards */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-stretch mb-16">
          {PLANS.map((plan, idx) => {
            const Icon = plan.icon;
            return (
              <div
                key={idx}
                className={`rounded-3xl p-8 sm:p-9 flex flex-col justify-between transition-all duration-300 relative ${
                  plan.highlighted
                    ? 'bg-white dark:bg-[#0B1528] border-2 border-primary shadow-2xl shadow-primary/15 lg:-translate-y-2'
                    : 'bg-white/80 dark:bg-slate-900/60 border border-slate-200 dark:border-slate-800/80 hover:border-slate-300 dark:hover:border-slate-700 shadow-sm'
                }`}
              >
                {plan.badge && (
                  <div className="absolute -top-3.5 left-1/2 -translate-x-1/2">
                    <span className="inline-flex items-center gap-1.5 px-4 py-1 rounded-full text-xs font-bold bg-primary text-primary-foreground shadow-md shadow-primary/30 uppercase tracking-wide">
                      <Sparkles className="w-3.5 h-3.5" /> {plan.badge}
                    </span>
                  </div>
                )}

                <div>
                  <div className="flex items-center gap-3 mb-4">
                    <div
                      className={`w-11 h-11 rounded-2xl flex items-center justify-center ${
                        plan.highlighted
                          ? 'bg-primary text-primary-foreground shadow-md shadow-primary/20'
                          : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300'
                      }`}
                    >
                      <Icon className="w-5 h-5" />
                    </div>
                    <h3 className="text-2xl font-bold text-slate-900 dark:text-white">
                      {plan.name}
                    </h3>
                  </div>

                  <div className="py-3 px-4 rounded-2xl bg-slate-50 dark:bg-slate-800/60 border border-slate-200/80 dark:border-slate-700/60 mb-6 text-center">
                    <span className="text-sm font-bold text-slate-900 dark:text-slate-100">
                      {plan.priceBadge}
                    </span>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                      Tarifa mensual ajustada a unidades
                    </p>
                  </div>

                  {/* Target Audience */}
                  <div className="space-y-1.5 mb-4">
                    <span className="text-[11px] font-bold uppercase tracking-wider text-primary">
                      Para quién:
                    </span>
                    <p className="text-xs sm:text-sm text-slate-700 dark:text-slate-300 leading-relaxed">
                      {plan.target}
                    </p>
                  </div>

                  {/* Main Benefit */}
                  <div className="space-y-1.5 mb-6">
                    <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
                      Beneficio clave:
                    </span>
                    <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-400 leading-relaxed">
                      {plan.benefit}
                    </p>
                  </div>

                  {/* Features List */}
                  <div className="space-y-3 pt-6 border-t border-slate-100 dark:border-slate-800">
                    <p className="text-xs font-bold uppercase tracking-wider text-slate-400 dark:text-slate-500">
                      Capacidades destacadas:
                    </p>
                    {plan.keyFeatures.map((feat, i) => (
                      <div key={i} className="flex items-start gap-2.5 text-xs sm:text-sm text-slate-800 dark:text-slate-200">
                        <Check className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
                        <span className="leading-snug">{feat}</span>
                      </div>
                    ))}
                  </div>
                </div>

                <div className="mt-8 pt-6 border-t border-slate-100 dark:border-slate-800">
                  <Link
                    to="/login"
                    className={`w-full py-3.5 px-5 rounded-2xl font-bold text-sm flex items-center justify-center gap-2 transition-all min-h-[48px] ${
                      plan.highlighted
                        ? 'bg-primary text-primary-foreground hover:bg-primary/90 shadow-lg shadow-primary/25'
                        : 'bg-slate-100 dark:bg-slate-800 text-slate-900 dark:text-white hover:bg-slate-200 dark:hover:bg-slate-700 border border-slate-200 dark:border-slate-700'
                    }`}
                  >
                    <span>{plan.ctaText}</span>
                    <ArrowRight className="w-4 h-4" />
                  </Link>
                  <p className="text-[11px] text-center text-slate-400 dark:text-slate-500 mt-2.5">
                    Demostración guiada con datos de prueba
                  </p>
                </div>
              </div>
            );
          })}
        </div>

        {/* Toggle Comparison Matrix */}
        <div className="text-center mb-10">
          <button
            type="button"
            onClick={() => setShowMatrix(!showMatrix)}
            className="inline-flex items-center gap-2 px-6 py-3 rounded-2xl text-sm font-semibold bg-white dark:bg-slate-800/80 hover:bg-slate-100 dark:hover:bg-slate-800 text-slate-900 dark:text-slate-100 border border-slate-200 dark:border-slate-700 shadow-sm transition-all min-h-[48px]"
          >
            <span>{showMatrix ? 'Ocultar matriz comparativa' : 'Comparar capacidades completas de la plataforma'}</span>
            <ArrowRight className={`w-4 h-4 transition-transform duration-200 ${showMatrix ? 'rotate-90' : ''}`} />
          </button>
        </div>

        {/* Detailed Table (Visible when toggled) */}
        {showMatrix && (
          <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-3xl overflow-hidden shadow-xl transition-all duration-300">
            <div className="p-6 sm:p-8 bg-slate-50/70 dark:bg-slate-800/40 border-b border-slate-200 dark:border-slate-800">
              <h4 className="text-xl font-bold text-slate-900 dark:text-white">
                Matriz Comparativa de Capacidades de la Plataforma
              </h4>
              <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">
                Todas las capacidades están respaldadas por nuestra arquitectura multi-tenant y base de datos relacional de grado empresarial.
              </p>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-sm">
                <thead>
                  <tr className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-800/20">
                    <th className="py-4 px-6 font-bold text-slate-900 dark:text-white w-1/2">Funcionalidad</th>
                    <th className="py-4 px-4 font-bold text-slate-900 dark:text-white text-center">Básico</th>
                    <th className="py-4 px-4 font-bold text-primary text-center bg-primary/5">Profesional</th>
                    <th className="py-4 px-4 font-bold text-slate-900 dark:text-white text-center">Empresarial</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800/70">
                  {MATRIX_FEATURES.map((cat, cIdx) => (
                    <Fragment key={`group-${cIdx}`}>
                      <tr>
                        <td colSpan={4} className="py-3 px-6 font-bold text-xs uppercase tracking-wider text-slate-500 dark:text-slate-400 bg-slate-100/70 dark:bg-slate-800/60">
                          {cat.category}
                        </td>
                      </tr>
                      {cat.features.map((f, fIdx) => (
                        <tr key={`feat-${cIdx}-${fIdx}`} className="hover:bg-slate-50/80 dark:hover:bg-slate-800/30 transition-colors">
                          <td className="py-3.5 px-6 text-slate-800 dark:text-slate-200 font-medium">
                            {f.name}
                          </td>
                          <td className="py-3.5 px-4 text-center">
                            {typeof f.basic === 'boolean' ? (
                              f.basic ? <Check className="w-4 h-4 text-emerald-500 mx-auto" /> : <span className="text-slate-400">—</span>
                            ) : (
                              <span className="text-xs text-slate-500 dark:text-slate-400 font-medium">{f.basic}</span>
                            )}
                          </td>
                          <td className="py-3.5 px-4 text-center bg-primary/5 font-semibold">
                            {typeof f.pro === 'boolean' ? (
                              f.pro ? <Check className="w-4 h-4 text-emerald-500 mx-auto" /> : <span className="text-slate-400">—</span>
                            ) : (
                              <span className="text-xs text-primary font-bold">{f.pro}</span>
                            )}
                          </td>
                          <td className="py-3.5 px-4 text-center">
                            {typeof f.enterprise === 'boolean' ? (
                              f.enterprise ? <Check className="w-4 h-4 text-emerald-500 mx-auto" /> : <span className="text-slate-400">—</span>
                            ) : (
                              <span className="text-xs text-slate-800 dark:text-slate-200 font-medium">{f.enterprise}</span>
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

        {/* Security & Reliability Assurance Banner */}
        <div className="mt-14 p-7 sm:p-8 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-6 shadow-sm">
          <div className="flex items-center gap-5">
            <div className="w-14 h-14 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-600 dark:text-emerald-400 shrink-0">
              <ShieldCheck className="w-7 h-7" />
            </div>
            <div>
              <h4 className="text-lg font-bold text-slate-900 dark:text-white">
                Aislamiento de Datos y Privacidad Garantizada
              </h4>
              <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-400 mt-1 max-w-xl">
                Todos los planes incluyen separación lógica de copropiedad (RLS/VPD), cifrado en tránsito y copias de seguridad continuas.
              </p>
            </div>
          </div>
          <Link
            to="/login"
            className="shrink-0 px-6 py-3.5 rounded-2xl bg-primary text-primary-foreground font-bold text-sm hover:bg-primary/90 shadow-md shadow-primary/20 transition-all min-h-[48px] flex items-center justify-center"
          >
            Acceder a demostración
          </Link>
        </div>
      </div>
    </section>
  );
}
