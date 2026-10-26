import { useEffect } from 'react';
import { MessageCircle } from 'lucide-react';
import LandingPricing from '../components/landing/LandingPricing.jsx';
import LandingFooter from '../components/landing/LandingFooter.jsx';
import LandingNavbar from '../components/landing/LandingNavbar.jsx';
import LandingFAQ from '../components/landing/LandingFAQ.jsx';
import { useSaedTheme } from '../lib/theme.js';
import '../components/landing/manual.css';

const SUBSCRIPTION_FAQS = [
  {
    q: '¿Cómo se factura el servicio?',
    a: 'Cada plan se puede pagar de forma mensual o anual. El pago anual tiene descuento frente a pagar mes a mes; en cada plan verás el valor anual y su equivalente mensual.',
  },
  {
    q: '¿Cómo se pone en marcha el conjunto?',
    a: 'La administración registra las unidades y los residentes del conjunto en la plataforma. Cada persona recibe en su correo un enlace para activar su cuenta y crear su propia contraseña.',
  },
  {
    q: '¿Necesito comprar equipos o instalar algo?',
    a: 'No. SAED funciona en el navegador de un computador, tableta o celular con conexión a internet.',
  },
  {
    q: '¿Puedo cambiar de plan más adelante?',
    a: 'Sí. La organización consulta su plan y su suscripción desde su consola, en la sección Plan y suscripción.',
  },
];

export default function SuscripcionesPage() {
  const { theme, toggleTheme } = useSaedTheme();

  useEffect(() => {
    document.title = 'Planes | SAED 2.0';
    window.scrollTo(0, 0);

    const metaDescription = document.querySelector('meta[name="description"]');
    if (metaDescription) {
      metaDescription.setAttribute(
        'content',
        'Planes de SAED según el número de unidades de tu conjunto, con pago mensual o anual.'
      );
    }
  }, []);

  return (
    <div className={`saed-manual m-root ${theme === 'dark' ? 'dark' : ''}`}>
      <a href="#m-main" className="m-skip">
        Saltar al contenido
      </a>
      <LandingNavbar theme={theme} onToggleTheme={toggleTheme} onLanding={false} />

      <main id="m-main" className="m-main">
        <section className="m-pricing-hero" aria-labelledby="m-plans-title">
          <div className="m-container">
            <div className="m-section-head">
              <h1 id="m-plans-title" className="m-display m-film-title">
                Planes para tu conjunto
              </h1>
              <p className="m-lede">
                Ajusta el número de unidades y el ciclo de pago. Los planes y precios vigentes vienen
                del catálogo de SAED.
              </p>
            </div>
          </div>
        </section>

        <LandingPricing />

        <LandingFAQ
          id="preguntas-planes"
          items={SUBSCRIPTION_FAQS}
          title="Preguntas sobre los planes"
          intro="Lo que conviene saber antes de suscribirte."
        />

        <section className="m-section m-advisor-section" aria-labelledby="m-advisor-title">
          <div className="m-container">
            <div className="m-advisor m-studs">
              <div>
                <h2 id="m-advisor-title" className="m-h3">
                  ¿Administras varias copropiedades?
                </h2>
                <p className="m-body">
                  Escríbenos y revisamos contigo el plan que mejor se ajusta a tu organización.
                </p>
              </div>
              <a
                href="https://wa.me/573026555881?text=Hola,%20quisiera%20cotizar%20un%20plan%20corporativo%20SAED%202.0"
                target="_blank"
                rel="noopener noreferrer"
                className="m-btn m-btn--primary m-btn--lg"
              >
                <MessageCircle aria-hidden="true" />
                <span>Hablar con un asesor</span>
              </a>
            </div>
          </div>
        </section>
      </main>

      <LandingFooter />
    </div>
  );
}
