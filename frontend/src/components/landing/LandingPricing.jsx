import { useState, Fragment } from 'react';
import { Check, ArrowRight, ShieldCheck, Sparkles } from 'lucide-react';
import { Link } from 'react-router-dom';

const PLANS = [
  {
    name: 'Básico Residencial',
    target: 'Para edificios individuales o comunidades de hasta 60 unidades.',
    benefit: 'Digitalización ágil del control de acceso de visitas y portería sin infraestructura compleja.',
    priceBadge: 'Cotización según unidades',
    highlighted: false,
    badge: null,
    keyFeatures: [
      'Control de acceso de visitas con código QR',
      'Consola web para portería (PC o tablet)',
      'Portal web para residentes (cero descargas)',
    ],
    ctaText: 'Solicitar cotización',
  },
  {
    name: 'Profesional Condominio',
    target: 'Para conjuntos cerrados y urbanizaciones de 60 a 250 unidades que buscan gestión integral.',
    benefit: 'Control operativo completo con recaudo digital integrado y custodia de encomiendas por PIN.',
    priceBadge: 'Plan más seleccionado',
    highlighted: true,
    badge: 'Más Popular',
    keyFeatures: [
      'Recaudo digital con pasarela Wompi (PSE y tarjetas)',
      'Custodia de paquetería con PIN de seguridad',
      'Control dinámico de parqueaderos de visitantes',
    ],
    ctaText: 'Agendar demostración',
  },
  {
    name: 'Empresarial Multi-Torre',
    target: 'Para macro-proyectos, edificios mixtos o administradoras con múltiples conjuntos.',
    benefit: 'Supervisión consolidada de múltiples propiedades con reportes avanzados de auditoría.',
    priceBadge: 'A la medida de tu portafolio',
    highlighted: false,
    badge: 'Corporativo',
    keyFeatures: [
      'Gestión multi-propiedad centralizada para administradoras',
      'Módulo de asambleas, votaciones y coeficientes Ley 675',
      'Exportación avanzada de informes de cartera y conciliación',
    ],
    ctaText: 'Consultar plan empresarial',
  },
];

const MATRIX_FEATURES = [
  { category: 'Acceso y Seguridad', features: [
    { name: 'Generación de QR para visitantes', basic: true, pro: true, enterprise: true },
    { name: 'Escáner QR en portería con validación en pantalla', basic: true, pro: true, enterprise: true },
    { name: 'Bitácora de ingresos y salidas con operador', basic: 'Básica', pro: 'Completa', enterprise: 'Auditoría Total' },
    { name: 'Control de parqueaderos de visitantes y placas', basic: false, pro: true, enterprise: true },
  ]},
  { category: 'Operación y Logística', features: [
    { name: 'Custodia y recepción de paquetes en portería', basic: 'Registro simple', pro: 'PIN de seguridad', enterprise: 'PIN + Múltiples bodegas' },
    { name: 'Notificación de correspondencia en buzón digital', basic: true, pro: true, enterprise: true },
    { name: 'Gestión de unidades y padrón de residentes', basic: 'Hasta 60', pro: 'Hasta 250', enterprise: 'Ilimitadas' },
    { name: 'Canal de PQRS con seguimiento de estados', basic: false, pro: true, enterprise: true },
  ]},
  { category: 'Finanzas y Cartera', features: [
    { name: 'Emisión de estados de cuenta de administración', basic: true, pro: true, enterprise: true },
    { name: 'Pasarela de pagos en línea Wompi (PSE / Tarjetas)', basic: false, pro: true, enterprise: true },
    { name: 'Conciliación bancaria en tiempo real', basic: false, pro: true, enterprise: true },
    { name: 'Certificados de paz y salvo automáticos', basic: false, pro: true, enterprise: true },
  ]},
  { category: 'Soporte y Gobernanza', features: [
    { name: 'Aislamiento lógico de base de datos por copropiedad', basic: true, pro: true, enterprise: true },
    { name: 'Soporte y acompañamiento técnico', basic: 'Estándar', pro: 'Prioritario', enterprise: 'Dedicado' },
    { name: 'Capacitación a administradores y personal de portería', basic: 'Guía digital', pro: 'Virtual guiada', enterprise: 'Personalizada' },
  ]},
];

export default function LandingPricing() {
  const [showMatrix, setShowMatrix] = useState(false);

  return (
    <section id="planes" className="py-24 bg-background border-t border-border relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider bg-primary/10 text-primary border border-primary/20">
            Esquema Comercial B2B
          </span>
          <h2 className="mt-4 text-3xl sm:text-4xl font-extrabold text-foreground tracking-tight">
            Planes adaptados a la escala de tu copropiedad
          </h2>
          <p className="mt-4 text-base sm:text-lg text-muted-foreground leading-relaxed">
            Inversión proporcional a las unidades y requerimientos de tu conjunto residencial o edificio. Sin costos de instalación sorpresivos ni cobros por usuario final.
          </p>
        </div>

        {/* Pricing Cards */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 items-stretch mb-12">
          {PLANS.map((plan, idx) => (
            <div
              key={idx}
              className={`rounded-2xl p-7 sm:p-8 flex flex-col justify-between transition-all duration-200 relative ${
                plan.highlighted
                  ? 'bg-card border-2 border-primary shadow-xl shadow-primary/10 lg:-translate-y-2'
                  : 'bg-card border border-border hover:border-border/80'
              }`}
            >
              {plan.badge && (
                <div className="absolute -top-3.5 left-1/2 -translate-x-1/2">
                  <span className="inline-flex items-center gap-1 px-3 py-0.5 rounded-full text-xs font-bold bg-primary text-primary-foreground shadow-sm">
                    <Sparkles className="w-3 h-3" /> {plan.badge}
                  </span>
                </div>
              )}

              <div>
                <h3 className="text-2xl font-bold text-foreground mb-2">
                  {plan.name}
                </h3>

                <div className="py-3 px-4 rounded-xl bg-muted/50 border border-border/60 mb-5 text-center">
                  <span className="text-sm font-semibold text-foreground">
                    {plan.priceBadge}
                  </span>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    Tarifa mensual ajustada a la copropiedad
                  </p>
                </div>

                {/* Para quién */}
                <div className="space-y-1 mb-4">
                  <span className="text-xs font-bold uppercase tracking-wider text-primary">
                    Para quién:
                  </span>
                  <p className="text-xs sm:text-sm text-foreground/80 leading-relaxed">
                    {plan.target}
                  </p>
                </div>

                {/* Beneficio principal */}
                <div className="space-y-1 mb-5">
                  <span className="text-xs font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
                    Beneficio principal:
                  </span>
                  <p className="text-xs sm:text-sm text-muted-foreground leading-relaxed">
                    {plan.benefit}
                  </p>
                </div>

                {/* Capacidades clave */}
                <div className="space-y-2.5 pt-4 border-t border-border">
                  <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    Capacidades destacadas:
                  </p>
                  {plan.keyFeatures.map((feat, i) => (
                    <div key={i} className="flex items-start gap-2 text-xs sm:text-sm text-foreground/90">
                      <Check className="w-4 h-4 text-emerald-500 shrink-0 mt-0.5" />
                      <span>{feat}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="mt-8 pt-6 border-t border-border">
                <Link
                  to="/login"
                  className={`w-full py-3 px-4 rounded-xl font-semibold text-sm flex items-center justify-center gap-2 transition-colors min-h-[44px] ${
                    plan.highlighted
                      ? 'bg-primary text-primary-foreground hover:bg-primary/90 shadow-md shadow-primary/20'
                      : 'bg-secondary text-secondary-foreground hover:bg-secondary/80 border border-border'
                  }`}
                >
                  <span>{plan.ctaText}</span>
                  <ArrowRight className="w-4 h-4" />
                </Link>
                <p className="text-xs text-center text-muted-foreground mt-2">
                  Demostración guiada con datos reales de prueba
                </p>
              </div>
            </div>
          ))}
        </div>

        {/* Toggle Comparison Matrix */}
        <div className="text-center mb-8">
          <button
            type="button"
            onClick={() => setShowMatrix(!showMatrix)}
            className="inline-flex items-center gap-2 px-5 py-3 rounded-xl text-sm font-semibold bg-muted hover:bg-muted/80 text-foreground border border-border transition-colors min-h-[44px]"
          >
            <span>{showMatrix ? 'Ocultar comparativa de capacidades' : 'Comparar capacidades completas de la plataforma'}</span>
            <ArrowRight className={`w-4 h-4 transition-transform duration-200 ${showMatrix ? 'rotate-90' : ''}`} />
          </button>
        </div>

        {/* Detailed Table (Visible when toggled) */}
        {showMatrix && (
          <div className="bg-card border border-border rounded-2xl overflow-hidden shadow-sm transition-all duration-300">
            <div className="p-6 bg-muted/40 border-b border-border">
              <h4 className="text-lg font-bold text-foreground">
                Matriz Comparativa de Capacidades de la Plataforma
              </h4>
              <p className="text-sm text-muted-foreground mt-1">
                Todas las funcionalidades están respaldadas por nuestra arquitectura multi-tenant y base de datos relacional.
              </p>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border bg-muted/20">
                    <th className="py-4 px-6 font-semibold text-foreground w-1/2">Funcionalidad</th>
                    <th className="py-4 px-4 font-semibold text-foreground text-center">Básico</th>
                    <th className="py-4 px-4 font-semibold text-primary text-center bg-primary/5">Profesional</th>
                    <th className="py-4 px-4 font-semibold text-foreground text-center">Empresarial</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {MATRIX_FEATURES.map((cat, cIdx) => (
                    <Fragment key={`group-${cIdx}`}>
                      <tr>
                        <td colSpan={4} className="py-2.5 px-6 font-bold text-xs uppercase tracking-wider text-muted-foreground bg-muted/50">
                          {cat.category}
                        </td>
                      </tr>
                      {cat.features.map((f, fIdx) => (
                        <tr key={`feat-${cIdx}-${fIdx}`} className="hover:bg-muted/20 transition-colors">
                          <td className="py-3 px-6 text-foreground font-medium flex items-center gap-2">
                            <span>{f.name}</span>
                          </td>
                          <td className="py-3 px-4 text-center">
                            {typeof f.basic === 'boolean' ? (
                              f.basic ? <Check className="w-4 h-4 text-emerald-500 mx-auto" /> : <span className="text-muted-foreground/50">—</span>
                            ) : (
                              <span className="text-xs text-muted-foreground">{f.basic}</span>
                            )}
                          </td>
                          <td className="py-3 px-4 text-center bg-primary/5 font-semibold">
                            {typeof f.pro === 'boolean' ? (
                              f.pro ? <Check className="w-4 h-4 text-emerald-500 mx-auto" /> : <span className="text-muted-foreground/50">—</span>
                            ) : (
                              <span className="text-xs text-primary">{f.pro}</span>
                            )}
                          </td>
                          <td className="py-3 px-4 text-center">
                            {typeof f.enterprise === 'boolean' ? (
                              f.enterprise ? <Check className="w-4 h-4 text-emerald-500 mx-auto" /> : <span className="text-muted-foreground/50">—</span>
                            ) : (
                              <span className="text-xs text-foreground">{f.enterprise}</span>
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
        <div className="mt-12 p-6 rounded-2xl bg-card border border-border flex flex-col sm:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-600 dark:text-emerald-400 shrink-0">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <h4 className="text-base font-bold text-foreground">
                Aislamiento de Datos y Privacidad Garantizada
              </h4>
              <p className="text-xs sm:text-sm text-muted-foreground mt-0.5">
                Todos los planes incluyen separación lógica de copropiedad, cifrado en tránsito y copias de seguridad continuas.
              </p>
            </div>
          </div>
          <Link
            to="/login"
            className="shrink-0 px-5 py-2.5 rounded-xl bg-primary text-primary-foreground font-semibold text-xs sm:text-sm hover:bg-primary/90 transition-colors min-h-[44px] flex items-center justify-center"
          >
            Acceder a demostración
          </Link>
        </div>
      </div>
    </section>
  );
}
