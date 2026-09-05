import { useState } from 'react';
import { ChevronDown, HelpCircle } from 'lucide-react';

const FAQS = [
  {
    q: '¿Qué es SAED 2.0?',
    a: 'SAED 2.0 es una plataforma SaaS PropTech de gestión integral y seguridad para propiedades horizontales y conjuntos residenciales. Conecta en una sola arquitectura web la administración, la portería (control QR, bitácora y paquetería por PIN) y a los residentes.',
  },
  {
    q: '¿Para qué tipo de propiedades está diseñado?',
    a: 'Está concebido para edificios residenciales, torres independientes, conjuntos cerrados, condominios campestres y macro-proyectos inmobiliarios de cualquier escala que busquen digitalizar su operación sin incurrir en hardware propietario.',
  },
  {
    q: '¿Qué roles de usuario maneja la plataforma?',
    a: 'Maneja 5 roles con aislamiento y permisos estrictos: SUPERADMIN (gestión global de la plataforma), ADMIN_ORGANIZACION (administradoras de múltiples edificios), ADMIN_PROPIEDAD (gestión del conjunto y finanzas), PORTERO (operación de garita y correspondencia) y RESIDENTE (pases de visita y pagos).',
  },
  {
    q: '¿Cómo funciona el control de visitantes con código QR?',
    a: 'El habitante emite una invitación desde su portal indicando los datos del visitante y vigencia. El sistema genera un código QR criptográfico seguro que el visitante muestra en garita. El guardia lo escanea en pantalla, confirmando vigencia y registrando automáticamente el ingreso en la bitácora de auditoría.',
  },
  {
    q: '¿Cómo funciona el control y entrega de paquetes?',
    a: 'Al llegar una encomienda a portería, el guardia registra la guía y la unidad de destino. El sistema genera un PIN criptográfico de 6 dígitos que solo el residente puede ver en su portal. Para retirar el paquete, el residente dicta este PIN en garita, garantizando cero entregas por error.',
  },
  {
    q: '¿SAED maneja cartera y pagos en línea?',
    a: 'Sí. Permite emitir cuotas ordinarias y extraordinarias, visualizar el estado de morosidad y pagar en línea mediante integración oficial con la pasarela Wompi (PSE, Bancolombia y tarjetas). Al confirmarse la transacción, el sistema actualiza el saldo y genera el certificado de paz y salvo.',
  },
  {
    q: '¿SAED funciona para administradores con múltiples propiedades?',
    a: 'Sí. A través del rol ADMIN_ORGANIZACION, una empresa administradora puede supervisar múltiples conjuntos residenciales desde un solo panel consolidado, manteniendo la separación física y lógica de datos de cada copropiedad mediante políticas Multi-Tenant (RLS).',
  },
  {
    q: '¿Cómo es el proceso de implementación?',
    a: 'Es 100% web y no requiere instalación de servidores locales ni descargas en smartphones. El equipo de administración parametriza las torres, unidades y residentes mediante carga asistida y el personal de garita puede empezar a operar de inmediato desde cualquier navegador moderno.',
  },
];

export default function LandingFAQ() {
  const [openIdx, setOpenIdx] = useState(null);

  function toggle(idx) {
    setOpenIdx(openIdx === idx ? null : idx);
  }

  return (
    <section
      id="faq"
      className="py-20 sm:py-28 lg:py-32 bg-[#0A1628] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center mb-16 sm:mb-20 space-y-4">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
            <HelpCircle className="w-3.5 h-3.5" />
            <span>PREGUNTAS FRECUENTES</span>
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Todo lo que necesitas saber sobre SAED 2.0
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Respuestas directas sobre la operación diaria, el aislamiento de datos y la puesta en marcha de la plataforma.
          </p>
        </div>

        {/* Accordion List */}
        <div className="space-y-3.5">
          {FAQS.map((faq, idx) => {
            const isOpen = openIdx === idx;
            return (
              <div
                key={idx}
                className="bg-slate-900/90 border border-slate-800 rounded-2xl overflow-hidden transition-all shadow-md"
              >
                <button
                  type="button"
                  onClick={() => toggle(idx)}
                  className="w-full text-left p-5 sm:p-6 flex items-center justify-between gap-4 hover:bg-slate-800/40 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 min-h-[56px]"
                  aria-expanded={isOpen}
                >
                  <span className="text-base sm:text-lg font-bold text-white font-['Plus_Jakarta_Sans'] pr-2">
                    {faq.q}
                  </span>
                  <div
                    className={`w-8 h-8 rounded-xl bg-slate-800 border border-slate-700 flex items-center justify-center shrink-0 text-slate-400 transition-transform duration-200 ${
                      isOpen ? 'rotate-180 text-emerald-400 bg-emerald-500/10 border-emerald-500/30' : ''
                    }`}
                  >
                    <ChevronDown className="w-4 h-4" />
                  </div>
                </button>

                {isOpen && (
                  <div className="px-5 pb-6 sm:px-6 sm:pb-7 text-slate-300 text-xs sm:text-sm leading-relaxed border-t border-slate-800/60 pt-4 animate-in fade-in duration-200">
                    {faq.a}
                  </div>
                )}
              </div>
            );
          })}
        </div>

      </div>
    </section>
  );
}
