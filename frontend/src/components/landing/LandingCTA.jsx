import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import ComplexModel from './ComplexModel.jsx';

export default function LandingCTA({ units }) {
  const frameRef = useRef(null);
  // undefined = static (no JS / reduced motion), 'pending' = waiting offscreen, 'go' = settle once.
  const [settle, setSettle] = useState(undefined);

  useEffect(() => {
    const node = frameRef.current;
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (!node || reduced || typeof IntersectionObserver === 'undefined') return undefined;
    setSettle('pending');
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setSettle('go');
          observer.disconnect();
        }
      },
      { threshold: 0.35 }
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return (
    <section id="ultimo-paso" className="m-section" aria-labelledby="m-cta-title">
      <div className="m-container">
        <div ref={frameRef} className="m-final m-studs">
          <div className="m-final-text">
            <span className="m-numeral m-final-numeral" aria-hidden="true">
              7
            </span>
            <h2 id="m-cta-title" className="m-h2">
              Último paso: elige tu plan
            </h2>
            <p className="m-body">
              Revisa los planes vigentes para {units} unidades y entra cuando tu cuenta esté activa.
            </p>
            <div className="m-actions">
              <Link to={`/suscripciones?unidades=${units}`} className="m-btn m-btn--primary m-btn--lg">
                <span>Ver planes</span>
                <ArrowRight aria-hidden="true" />
              </Link>
              <Link to="/login" className="m-btn m-btn--glass m-btn--lg">
                Iniciar sesión
              </Link>
            </div>
          </div>
          <ComplexModel units={units} activeStep={7} rows={19} settle={settle} className="m-final-model" />
        </div>
      </div>
    </section>
  );
}
