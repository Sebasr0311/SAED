import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Home } from 'lucide-react';
import '../components/landing/manual.css';
import '../components/landing/manual-public.css';

// Rendered inside the app shell (authenticated); unauthenticated unknown routes redirect to /login.
export default function NotFoundPage() {
  const navigate = useNavigate();
  return (
    <section className="saed-manual m-pub-embed" aria-labelledby="not-found-title">
      <div className="m-pub-404">
        <div className="m-pub-404-text">
          <p className="m-numeral m-pub-404-numeral" aria-hidden="true">
            404
          </p>
          <h1 id="not-found-title" className="m-h3">
            Página no encontrada
          </h1>
          <p className="m-body">
            La dirección que buscas no existe o fue movida. Verifica el enlace o vuelve al inicio del
            sistema.
          </p>
          <div className="m-actions">
            <button type="button" className="m-btn m-btn--primary" onClick={() => navigate('/')}>
              <Home aria-hidden="true" />
              <span>Ir al inicio</span>
            </button>
            <button type="button" className="m-btn m-btn--glass" onClick={() => navigate(-1)}>
              <ArrowLeft aria-hidden="true" />
              <span>Volver atrás</span>
            </button>
          </div>
        </div>
        <div className="m-pub-404-plate" aria-hidden="true">
          <span />
          <span />
          <span />
          <span />
          <span className="is-missing" />
          <span />
        </div>
      </div>
    </section>
  );
}
