import './manual.css';
import { Link } from 'react-router-dom';

const SAED_EMBLEM = '/imagenes/saed_logo_emblem_only.png';

const SECTION_LINKS = [
  { name: 'Piezas', hash: '#piezas' },
  { name: 'Pasos', hash: '#pasos' },
  { name: 'Especificaciones', hash: '#especificaciones' },
  { name: 'Preguntas', hash: '#preguntas' },
];

// Shared by the landing and /suscripciones, so it carries its own manual scope.
export default function LandingFooter() {
  const year = new Date().getFullYear();

  return (
    <footer className="saed-manual m-footer">
      <div className="m-container m-footer-inner">
        <div className="m-footer-brand">
          <Link to="/" className="m-brand" aria-label="SAED 2.0, inicio">
            <img src={SAED_EMBLEM} alt="" className="m-brand-mark" width="32" height="32" />
            <span className="m-brand-name">SAED</span>
            <span className="m-brand-ver">2.0</span>
          </Link>
          <p className="m-footer-note">
            Sistema de administración para conjuntos de propiedad horizontal en Colombia.
          </p>
        </div>

        <nav className="m-footer-nav" aria-label="Manual">
          <h2 className="m-footer-title">Manual</h2>
          <ul>
            {SECTION_LINKS.map((link) => (
              <li key={link.hash}>
                <Link to={{ pathname: '/', hash: link.hash }} className="m-link">
                  {link.name}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <nav className="m-footer-nav" aria-label="Cuenta">
          <h2 className="m-footer-title">Cuenta</h2>
          <ul>
            <li>
              <Link to="/suscripciones" className="m-link">
                Planes
              </Link>
            </li>
            <li>
              <Link to="/login" className="m-link">
                Iniciar sesión
              </Link>
            </li>
          </ul>
        </nav>
      </div>
      <div className="m-container m-footer-base">
        <p>© {year} SAED. Pantallas y datos de la página con fines ilustrativos.</p>
      </div>
    </footer>
  );
}
