import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  Building2,
  Sparkles,
  ArrowLeft,
  LogIn,
  ShieldCheck,
  Zap,
  HelpCircle,
  PhoneCall,
  CheckCircle2,
} from 'lucide-react';
import LandingPricing from '../components/landing/LandingPricing.jsx';
import LandingFooter from '../components/landing/LandingFooter.jsx';

const SUBSCRIPTION_FAQS = [
  {
    q: '¿Cómo se factura el servicio en SAED 2.0?',
    a: 'La facturación se realiza mensualmente o anualmente por cada unidad habitacional (apartamento o casa) registrada en la propiedad. Si eliges facturación anual, obtienes un 20% de descuento directo.',
  },
  {
    q: '¿Qué incluye la implementación inicial?',
    a: 'Todos los planes incluyen la carga masiva del censo de residentes, parametrización de cuotas, capacitación virtual para la administración y guardas de portería, y acceso inmediato a la plataforma.',
  },
  {
    q: '¿Se requiere compra de hardware o equipos especiales?',
    a: 'No. SAED 2.0 funciona 100% en la nube a través de navegador web en cualquier computador, tablet o smartphone común con acceso a internet. No necesitas lectores QR ni servidores propietarios.',
  },
  {
    q: '¿Puedo cambiar de plan más adelante?',
    a: 'Sí, en cualquier momento puedes actualizar tu suscripción si el conjunto habilita nuevas etapas o requiere módulos avanzados como Asambleas Ley 675 o múltiples garitas concurrentes.',
  },
];

export default function SuscripcionesPage() {
  useEffect(() => {
    document.title = 'Suscripciones y Planes | SAED 2.0';
    window.scrollTo(0, 0);

    const metaDescription = document.querySelector('meta[name="description"]');
    if (metaDescription) {
      metaDescription.setAttribute(
        'content',
        'Tarifas transparentes por unidad habitacional. Planes Básico, Profesional y Empresarial con calculadora interactiva y recaudo Wompi.'
      );
    }
  }, []);

  return (
    <div className="min-h-screen bg-[#061018] text-slate-100 flex flex-col selection:bg-cyan-500/25 selection:text-cyan-300">
      {/* 1. Header Minimalista Dedicado */}
      <header className="sticky top-0 z-50 bg-[#070B14]/90 backdrop-blur-xl border-b border-slate-800/80 shadow-2xl py-3.5 sm:py-4">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center justify-between">
          {/* Logo Brand */}
          <Link
            to="/"
            className="flex items-center gap-3 group focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 rounded-xl py-1 px-1.5 transition-colors"
            aria-label="Volver al inicio de SAED 2.0"
          >
            <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-gradient-to-br from-[#122347] to-[#070B14] border border-sky-500/30 flex items-center justify-center shadow-sm shadow-sky-500/10 group-hover:border-sky-400 transition-colors">
              <Building2 className="w-4 h-4 sm:w-5 sm:h-5 text-sky-400" />
            </div>
            <div className="flex items-center gap-2">
              <span className="text-xl sm:text-2xl font-extrabold tracking-tight text-white font-['Plus_Jakarta_Sans']">
                SAED
              </span>
              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-sky-500/10 text-sky-400 border border-sky-500/25 tracking-wide">
                2.0
              </span>
            </div>
          </Link>

          {/* Navigation Actions */}
          <div className="flex items-center gap-3">
            <Link
              to="/"
              className="hidden sm:flex items-center gap-1.5 px-3.5 py-2 text-xs sm:text-sm font-medium text-slate-300 hover:text-white hover:bg-white/[0.06] rounded-xl border border-transparent hover:border-white/10 transition-all min-h-[40px]"
            >
              <ArrowLeft className="w-4 h-4 text-slate-400" />
              <span>Volver al inicio</span>
            </Link>

            <Link
              to="/login"
              className="px-4 py-2 text-xs sm:text-sm font-semibold text-white bg-slate-800/90 hover:bg-slate-700 border border-slate-700/80 rounded-xl transition-all flex items-center gap-1.5 min-h-[40px]"
            >
              <LogIn className="w-4 h-4 text-sky-400" />
              <span>Iniciar sesión</span>
            </Link>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1">
        {/* 2. Hero de Suscripciones */}
        <section className="relative pt-16 pb-12 sm:pt-24 sm:pb-16 overflow-hidden bg-gradient-to-b from-[#061018] via-[#0B1B27] to-[#061018]">
          {/* Subtle grid accent */}
          <div className="absolute inset-0 bg-[radial-gradient(#1E3A6E_1px,transparent_1px)] [background-size:36px_36px] opacity-15 pointer-events-none" />

          <div className="relative max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 text-center space-y-6">
            <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-slate-900/80 border border-sky-500/30 text-sky-400 text-xs font-bold tracking-widest uppercase shadow-lg shadow-sky-950/40 backdrop-blur-xl">
              <Sparkles className="w-3.5 h-3.5 text-sky-400 animate-pulse" />
              <span>TARIFAS TRANSPARENTES Y ESCALABLES</span>
            </div>

            <h1 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
              Planes diseñados para la escala de tu{' '}
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-cyan-300 via-sky-300 to-blue-400">
                copropiedad
              </span>
            </h1>

            <p className="text-base sm:text-lg lg:text-xl text-slate-300 max-w-2xl mx-auto leading-relaxed">
              Calcula la tarifa exacta para tu número de unidades. Todo incluido, sin costos de licenciamiento sorpresa y con aislamiento de datos bancario.
            </p>

            {/* Quick Guarantees Strip */}
            <div className="pt-4 flex flex-wrap items-center justify-center gap-4 sm:gap-6 text-xs text-slate-300">
              <span className="flex items-center gap-1.5 bg-slate-900/60 px-3 py-1.5 rounded-lg border border-slate-800">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                20% de ahorro en pago anual
              </span>
              <span className="flex items-center gap-1.5 bg-slate-900/60 px-3 py-1.5 rounded-lg border border-slate-800">
                <ShieldCheck className="w-3.5 h-3.5 text-sky-400" />
                Aislamiento RLS en Oracle Cloud
              </span>
              <span className="flex items-center gap-1.5 bg-slate-900/60 px-3 py-1.5 rounded-lg border border-slate-800">
                <Zap className="w-3.5 h-3.5 text-amber-400" />
                Activación en 24 horas hábiles
              </span>
            </div>
          </div>
        </section>

        {/* 3. Módulo Completo de Pricing (Planes, Deslizador y Matriz) */}
        <LandingPricing />

        {/* 4. Preguntas Frecuentes de Suscripción */}
        <section className="py-16 sm:py-20 bg-[#0A101E] border-t border-slate-800/80">
          <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="text-center space-y-3 mb-12">
              <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full bg-slate-900 text-sky-400 text-xs font-semibold border border-slate-800">
                <HelpCircle className="w-3.5 h-3.5" />
                <span>RESOLVEMOS TUS DUDAS</span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-bold text-white font-['Plus_Jakarta_Sans']">
                Preguntas frecuentes sobre suscripciones
              </h2>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              {SUBSCRIPTION_FAQS.map((faq, i) => (
                <div
                  key={i}
                  className="p-5 sm:p-6 rounded-2xl bg-slate-900/80 border border-slate-800/90 space-y-2 hover:border-slate-700 transition-colors"
                >
                  <h3 className="text-sm sm:text-base font-bold text-white flex items-start gap-2">
                    <span className="text-sky-400">Q.</span>
                    {faq.q}
                  </h3>
                  <p className="text-xs sm:text-sm text-slate-300 leading-relaxed pl-5">
                    {faq.a}
                  </p>
                </div>
              ))}
            </div>

            {/* Custom Assistance CTA */}
            <div className="mt-12 p-6 rounded-2xl bg-gradient-to-r from-sky-950/40 via-slate-900 to-slate-900 border border-sky-500/20 text-center sm:text-left flex flex-col sm:flex-row items-center justify-between gap-4">
              <div className="space-y-1">
                <h4 className="text-base font-bold text-white">¿Tienes un macro-proyecto o administras más de 5 copropiedades?</h4>
                <p className="text-xs sm:text-sm text-slate-300">
                  Escríbenos para un plan corporativo multi-propiedad con acuerdos de nivel de servicio (SLA) a medida.
                </p>
              </div>
              <a
                href="https://wa.me/573000000000?text=Hola,%20quisiera%20cotizar%20un%20plan%20corporativo%20SAED%202.0"
                target="_blank"
                rel="noopener noreferrer"
                className="shrink-0 px-5 py-2.5 rounded-xl bg-sky-500 hover:bg-sky-400 text-slate-950 font-bold text-xs sm:text-sm transition-colors flex items-center gap-2"
              >
                <PhoneCall className="w-4 h-4" />
                <span>Hablar con un asesor</span>
              </a>
            </div>
          </div>
        </section>
      </main>

      {/* 5. Footer */}
      <LandingFooter />
    </div>
  );
}
