import { useState } from 'react';
import { ChevronDown, HelpCircle, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';

const FAQS = [
  {
    q: '¿Qué es SAED 2.0 y en qué se diferencia de un software tradicional?',
    a: 'SAED 2.0 es una plataforma integral de gestión operativa y control de acceso diseñada para copropiedades y edificios residenciales. A diferencia de un sistema contable aislado, SAED conecta en tiempo real la portería (control QR de visitas, bitácora y custodia de paquetes por PIN), la administración (cartera, unidades, residentes y asambleas) y a los residentes en una sola experiencia web unificada.',
  },
  {
    q: '¿Los residentes deben descargar una aplicación móvil pesada?',
    a: 'No. SAED 2.0 fue concebido bajo arquitectura web moderna y responsiva. Funciona de manera inmediata en cualquier navegador (Chrome, Safari, Edge) en smartphones, tablets o computadores, sin agotar almacenamiento en el dispositivo ni lidiar con actualizaciones en tiendas de apps.',
  },
  {
    q: '¿Cómo funciona el control de acceso de visitas con código QR?',
    a: 'El residente genera la invitación desde su portal ingresando los datos básicos del visitante. El sistema emite un pase con código QR temporal y seguro. Al presentarse en portería, el guardia lee el código desde la consola web; el sistema valida al instante su vigencia, muestra la unidad destino y asienta automáticamente el ingreso en la bitácora de auditoría.',
  },
  {
    q: '¿Cómo se garantiza la entrega segura de paquetes y encomiendas?',
    a: 'Al recibir una encomienda, el portero registra la empresa de mensajería y la unidad destinataria. El sistema asigna un código PIN criptográfico único visible únicamente en el portal del residente. Para retirar el paquete, el residente debe presentar dicho PIN en portería, garantizando custodia verificable y cero entregas erróneas.',
  },
  {
    q: '¿Cómo se administra el pago de la cuota de administración?',
    a: 'Los estados de cuenta se emiten digitalmente para cada unidad. Los residentes pueden realizar el pago en línea mediante PSE, tarjetas de crédito o débito a través de la pasarela de pagos integrada Wompi. Al completarse la transacción, el sistema actualiza el saldo de cartera al instante y permite descargar el comprobante oficial.',
  },
  {
    q: '¿Qué requisitos técnicos o equipos se necesitan en la portería?',
    a: 'Solo se requiere un computador de escritorio, portátil o tablet con conexión a internet y un navegador web estándar. Para la lectura de códigos QR se puede utilizar la cámara del equipo o cualquier lector óptico USB convencional.',
  },
  {
    q: '¿Cómo se garantiza la seguridad y privacidad de los datos de la copropiedad?',
    a: 'SAED 2.0 implementa una arquitectura Multi-Tenant con políticas estrictas de aislamiento de datos en el motor relacional (Row Level Security / VPD). Cada copropiedad opera en un entorno lógico blindado: ningún usuario puede ver, filtrar ni consultar datos de otra copropiedad bajo ninguna circunstancia.',
  },
  {
    q: '¿Cómo puedo implementar SAED en mi edificio o conjunto residencial?',
    a: 'La implementación se realiza mediante un proceso ágil de parametrización de unidades y roles. Nuestro equipo comercial estructura una cotización adaptada a la escala de tu copropiedad y brinda acompañamiento y capacitación inicial para el personal de administración y seguridad.',
  },
];

export default function LandingFAQ() {
  const [openIdx, setOpenIdx] = useState(null);

  function toggle(idx) {
    setOpenIdx(openIdx === idx ? null : idx);
  }

  return (
    <section id="faq" className="py-28 sm:py-36 bg-slate-50 dark:bg-[#080E1A] border-t border-slate-200/80 dark:border-slate-800/80 relative">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center mb-16 sm:mb-20 space-y-4">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest bg-primary/10 text-primary border border-primary/20">
            <HelpCircle className="w-3.5 h-3.5" />
            PREGUNTAS FRECUENTES
          </span>
          <h2 className="text-3xl sm:text-5xl font-extrabold text-slate-900 dark:text-white tracking-tight leading-tight font-['Plus_Jakarta_Sans']">
            Todo lo que necesitas saber sobre SAED 2.0
          </h2>
          <p className="text-base sm:text-lg text-slate-600 dark:text-slate-400 leading-relaxed max-w-2xl mx-auto">
            Respuestas claras sobre la operación diaria, arquitectura tecnológica y puesta en marcha de la plataforma en tu comunidad.
          </p>
        </div>

        {/* Accordion List */}
        <div className="space-y-4">
          {FAQS.map((faq, idx) => {
            const isOpen = openIdx === idx;
            return (
              <div
                key={idx}
                className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800/90 rounded-2xl overflow-hidden transition-all duration-200 shadow-sm"
              >
                <button
                  type="button"
                  onClick={() => toggle(idx)}
                  className="w-full text-left p-6 sm:p-7 flex items-center justify-between gap-4 hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary min-h-[64px]"
                  aria-expanded={isOpen}
                >
                  <span className="text-base sm:text-lg font-bold text-slate-900 dark:text-white pr-2">
                    {faq.q}
                  </span>
                  <div
                    className={`w-9 h-9 rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center shrink-0 text-slate-500 dark:text-slate-400 transition-transform duration-200 ${
                      isOpen ? 'rotate-180 text-primary bg-primary/10 dark:bg-primary/20' : ''
                    }`}
                  >
                    <ChevronDown className="w-4 h-4" />
                  </div>
                </button>

                {isOpen && (
                  <div className="px-6 pb-6 sm:px-7 sm:pb-7 text-sm sm:text-base text-slate-600 dark:text-slate-300 leading-relaxed border-t border-slate-100 dark:border-slate-800 pt-5 bg-slate-50/50 dark:bg-slate-800/20">
                    {faq.a}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Help & Support Callout */}
        <div className="mt-14 p-7 sm:p-9 rounded-3xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-6 shadow-sm">
          <div className="text-left space-y-1">
            <h4 className="text-lg font-bold text-slate-900 dark:text-white">
              ¿Tienes una consulta específica sobre tu edificio?
            </h4>
            <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-400">
              Nuestro equipo está a tu disposición para asesorarte y mostrarte el sistema con los datos de tu conjunto.
            </p>
          </div>
          <Link
            to="/login"
            className="shrink-0 inline-flex items-center gap-2 px-6 py-3.5 rounded-2xl bg-primary text-primary-foreground font-bold text-sm hover:bg-primary/90 transition-all min-h-[48px] shadow-sm"
          >
            <span>Iniciar sesión</span>
            <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      </div>
    </section>
  );
}
