import { useState } from 'react';
import { ChevronDown, HelpCircle, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';

const FAQS = [
  {
    q: '¿Cómo puedo implementar SAED en mi copropiedad?',
    a: 'SAED utiliza un modelo de implementación y cotización adaptado al número de unidades y a las necesidades operativas de cada copropiedad.',
  },
  {
    q: '¿Cuánto cuesta SAED?',
    a: 'El precio se determina según la escala de la copropiedad y las capacidades requeridas. Solicita una cotización para conocer el plan adecuado.',
  },
  {
    q: '¿Qué es SAED 2.0 y en qué se diferencia de un software contable tradicional?',
    a: 'SAED 2.0 es una plataforma integral de gestión operativa y seguridad para copropiedades. Conecta en tiempo real la portería (control QR, bitácora y paquetería con PIN), la administración (cartera, unidades y asambleas) y a los residentes en una sola experiencia web unificada.',
  },
  {
    q: '¿Los residentes deben descargar aplicaciones pesadas de tiendas de apps?',
    a: 'No. SAED 2.0 fue diseñado bajo arquitectura web moderna y responsiva. Funciona de manera inmediata en cualquier navegador (Chrome, Safari, Edge) en smartphones o computadores, sin agotar almacenamiento en el dispositivo ni lidiar con actualizaciones de tiendas.',
  },
  {
    q: '¿Cómo se garantiza el aislamiento y la privacidad de los datos entre copropiedades?',
    a: 'SAED 2.0 implementa una arquitectura Multi-Tenant con políticas estrictas de aislamiento de datos en el motor relacional y validación segura de sesiones. Cada copropiedad opera en un espacio lógico blindado: ningún usuario puede ver, filtrar ni consultar datos de otra copropiedad.',
  },
  {
    q: '¿Cómo funciona la validación de visitas con código QR en portería?',
    a: 'El residente genera la invitación desde su portal ingresando los datos del visitante. El sistema crea un pase con código QR seguro. Al llegar a la portería, el guardia lee el código en pantalla; el sistema valida inmediatamente su vigencia, muestra la unidad destino y registra el ingreso en la bitácora de auditoría.',
  },
  {
    q: '¿Cómo previene el sistema la entrega equivocada de encomiendas?',
    a: 'Al recibir un paquete, el portero registra la empresa transportadora y la unidad destinataria. El sistema genera un PIN criptográfico único visible únicamente en el portal del residente. Para retirar el paquete, el residente debe presentar dicho PIN en portería, garantizando custodia transparente y entrega verificada.',
  },
  {
    q: '¿Cómo se integra el recaudo de cartera con la pasarela Wompi?',
    a: 'Los estados de cuenta de administración se emiten de forma digital. Los residentes pueden pagar en línea mediante PSE, tarjetas de crédito/débito o botón Bancolombia a través de Wompi. Al completarse la transacción, el sistema actualiza el saldo al instante y genera el certificado de paz y salvo.',
  },
  {
    q: '¿Qué requisitos técnicos o hardware se necesitan en la portería del edificio?',
    a: 'Solo se requiere un computador de escritorio, portátil o tablet con conexión a internet y un navegador web moderno. Para la lectura de códigos QR se puede utilizar la cámara del equipo o un lector óptico USB estándar.',
  },
];

export default function LandingFAQ() {
  const [openIdx, setOpenIdx] = useState(null);

  function toggle(idx) {
    setOpenIdx(openIdx === idx ? null : idx);
  }

  return (
    <section id="faq" className="py-24 bg-background border-t border-border relative">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center mb-16">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider bg-primary/10 text-primary border border-primary/20">
            <HelpCircle className="w-3.5 h-3.5" />
            Preguntas Frecuentes
          </span>
          <h2 className="mt-4 text-3xl sm:text-4xl font-extrabold text-foreground tracking-tight">
            Todo lo que necesitas saber sobre SAED 2.0
          </h2>
          <p className="mt-4 text-base sm:text-lg text-muted-foreground leading-relaxed">
            Respuestas claras sobre la implementación, arquitectura y funcionamiento de la plataforma en tu comunidad.
          </p>
        </div>

        {/* Accordion */}
        <div className="space-y-3 sm:space-y-4">
          {FAQS.map((faq, idx) => {
            const isOpen = openIdx === idx;
            return (
              <div
                key={idx}
                className="bg-card border border-border rounded-2xl overflow-hidden transition-all duration-200"
              >
                <button
                  type="button"
                  onClick={() => toggle(idx)}
                  className="w-full text-left p-5 sm:p-6 flex items-center justify-between gap-4 hover:bg-muted/30 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary min-h-[56px]"
                  aria-expanded={isOpen}
                >
                  <span className="text-base sm:text-lg font-bold text-foreground pr-2">
                    {faq.q}
                  </span>
                  <div
                    className={`w-8 h-8 rounded-lg bg-muted flex items-center justify-center shrink-0 text-muted-foreground transition-transform duration-200 ${
                      isOpen ? 'rotate-180 text-primary bg-primary/10' : ''
                    }`}
                  >
                    <ChevronDown className="w-4 h-4" />
                  </div>
                </button>

                {isOpen && (
                  <div className="px-5 pb-5 sm:px-6 sm:pb-6 text-sm sm:text-base text-muted-foreground leading-relaxed border-t border-border/60 pt-4 bg-muted/10">
                    {faq.a}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Support Callout */}
        <div className="mt-12 text-center p-6 sm:p-8 rounded-2xl bg-muted/40 border border-border flex flex-col sm:flex-row items-center justify-between gap-6">
          <div className="text-left">
            <h4 className="text-base font-bold text-foreground">
              ¿Tienes una pregunta sobre tu caso particular?
            </h4>
            <p className="text-xs sm:text-sm text-muted-foreground mt-1">
              Nuestro equipo está disponible para asesorarte sobre las características y planes para tu conjunto.
            </p>
          </div>
          <Link
            to="/login"
            className="shrink-0 inline-flex items-center gap-2 px-5 py-3 rounded-xl bg-primary text-primary-foreground font-semibold text-sm hover:bg-primary/90 transition-colors min-h-[44px]"
          >
            <span>Iniciar sesión</span>
            <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      </div>
    </section>
  );
}
