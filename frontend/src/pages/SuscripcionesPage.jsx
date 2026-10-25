import { useEffect } from 'react';
import {
  ShieldCheck,
  PhoneCall,
  CheckCircle2,
} from 'lucide-react';
import LandingPricing from '../components/landing/LandingPricing.jsx';
import LandingFooter from '../components/landing/LandingFooter.jsx';
import LandingNavbar from '../components/landing/LandingNavbar.jsx';
import { useSaedTheme } from '../lib/theme.js';

const SUBSCRIPTION_FAQS = [
  {
    q: '¿Cómo se factura el servicio en SAED 2.0?',
    a: 'La facturación se realiza mensualmente o anualmente según el plan y la escala de unidades habitacionales (apartamentos o casas) de tu copropiedad. Si eliges facturación anual, obtienes un 20% de descuento directo.',
  },
  {
    q: '¿Cómo se pone en marcha el conjunto?',
    a: 'La administración registra las unidades y los residentes del conjunto en la plataforma. Cada persona recibe en su correo un enlace para activar su cuenta y crear su propia contraseña.',
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
  const { theme, toggleTheme } = useSaedTheme();

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
    <div
      className={`min-h-screen bg-[#F7F9FE] dark:bg-[#070B14] text-[#1D2942] dark:text-slate-100 flex flex-col selection:bg-[#DFEBFF] selection:text-[#1D2942] dark:selection:bg-sky-500/25 dark:selection:text-sky-300 transition-colors duration-300 ${
        theme === 'dark' ? 'dark' : ''
      }`}
    >
      <LandingNavbar theme={theme} onToggleTheme={toggleTheme} onLanding={false} />

      {/* Main Content */}
      <main className="flex-1">
        {/* 2. Hero de Suscripciones */}
        <section className="relative pt-14 pb-10 sm:pt-20 sm:pb-14 overflow-hidden bg-gradient-to-b from-white via-[#F7F9FE] to-[#F7F9FE] dark:from-[#070B14] dark:via-[#0B1B27] dark:to-[#070B14]">
          {/* Subtle grid accent */}
          <div className="absolute inset-0 bg-[radial-gradient(#5267DA_1px,transparent_1px)] [background-size:36px_36px] opacity-[0.08] dark:opacity-15 pointer-events-none" />

          <div className="relative max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 text-center space-y-6">

            <h1 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-[#1D2942] dark:text-white leading-tight font-['Plus_Jakarta_Sans']">
              Planes diseñados para la escala de tu copropiedad
            </h1>

            <p className="text-base sm:text-lg lg:text-xl text-[#475569] dark:text-slate-300 max-w-2xl mx-auto leading-relaxed">
              Elige el plan según el número de unidades de tu conjunto. Los precios vigentes se muestran abajo.
            </p>

            {/* Quick Guarantees Strip */}
            <div className="pt-2 flex flex-wrap items-center justify-center gap-3 sm:gap-4 text-xs font-bold">
              <span className="flex items-center gap-1.5 bg-[#DCF4EB] text-[#065F46] dark:bg-emerald-500/15 dark:text-emerald-300 px-3.5 py-2 rounded-xl border border-[#A7F3D0] dark:border-emerald-500/30">
                <CheckCircle2 className="w-4 h-4" />
                20% de ahorro en pago anual
              </span>
              <span className="flex items-center gap-1.5 bg-[#DFEBFF] text-[#1D2942] dark:bg-sky-500/15 dark:text-sky-300 px-3.5 py-2 rounded-xl border border-[#5267DA]/20 dark:border-sky-500/30">
                <ShieldCheck className="w-4 h-4 text-[#5267DA] dark:text-sky-400" />
                Datos aislados por conjunto en la base de datos
              </span>
            </div>
          </div>
        </section>

        {/* 3. Módulo Completo de Pricing (Planes, Deslizador y Matriz) */}
        <LandingPricing />

        {/* 4. Preguntas Frecuentes de Suscripción */}
        <section className="py-16 sm:py-20 bg-white dark:bg-[#0A101E] border-t border-[#DFEBFF] dark:border-slate-800/80 transition-colors">
          <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="text-center space-y-3 mb-12">
              <h2 className="text-2xl sm:text-3xl font-extrabold text-[#1D2942] dark:text-white font-['Plus_Jakarta_Sans']">
                Preguntas frecuentes sobre suscripciones
              </h2>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              {SUBSCRIPTION_FAQS.map((faq, i) => (
                <div
                  key={i}
                  className="p-6 rounded-2xl bg-[#F7F9FE] dark:bg-slate-900/80 border border-[#DFEBFF] dark:border-slate-800/90 space-y-2 hover:border-[#5267DA]/40 dark:hover:border-slate-700 transition-colors"
                >
                  <h3 className="text-sm sm:text-base font-extrabold text-[#1D2942] dark:text-white flex items-start gap-2">
                    <span className="text-[#5267DA] dark:text-sky-400">Q.</span>
                    {faq.q}
                  </h3>
                  <p className="text-xs sm:text-sm text-[#475569] dark:text-slate-300 leading-relaxed pl-5">
                    {faq.a}
                  </p>
                </div>
              ))}
            </div>

            {/* Custom Assistance CTA */}
            <div className="mt-12 p-6 sm:p-8 rounded-3xl bg-[#1D2942] dark:bg-gradient-to-r dark:from-sky-950/40 dark:via-slate-900 dark:to-slate-900 border border-[#1D2942] dark:border-sky-500/20 text-center sm:text-left flex flex-col sm:flex-row items-center justify-between gap-5 shadow-xl">
              <div className="space-y-1.5">
                <h4 className="text-base sm:text-lg font-extrabold text-white font-['Plus_Jakarta_Sans']">
                  ¿Tienes un macro-proyecto o administras más de 5 copropiedades?
                </h4>
                <p className="text-xs sm:text-sm text-[#DFEBFF] dark:text-slate-300">
                  Escríbenos para un plan corporativo multi-propiedad con acuerdos de nivel de servicio (SLA) a medida.
                </p>
              </div>
              <a
                href="https://wa.me/573026555881?text=Hola,%20quisiera%20cotizar%20un%20plan%20corporativo%20SAED%202.0"
                target="_blank"
                rel="noopener noreferrer"
                className="shrink-0 px-5 py-3 rounded-xl bg-[#5267DA] hover:bg-[#4155C6] text-white font-extrabold text-xs sm:text-sm transition-colors flex items-center gap-2 shadow-lg shadow-[#5267DA]/30"
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
