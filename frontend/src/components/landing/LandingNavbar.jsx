import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Menu, Moon, Sun, X } from 'lucide-react';
import './manual.css';

const SAED_EMBLEM = '/imagenes/saed_logo_emblem_only.png';

const NAV_LINKS = [
  { name: 'Piezas', href: '#piezas' },
  { name: 'Pasos', href: '#pasos' },
  { name: 'Especificaciones', href: '#especificaciones' },
  { name: 'Preguntas', href: '#preguntas' },
];

// On the landing, section links are in-page anchors; elsewhere they route to "/#section".
function SectionLink({ href, onLanding, className, onClick, children }) {
  if (onLanding) {
    return (
      <a href={href} className={className} onClick={onClick}>
        {children}
      </a>
    );
  }
  return (
    <Link to={{ pathname: '/', hash: href }} className={className} onClick={onClick}>
      {children}
    </Link>
  );
}

export default function LandingNavbar({ theme = 'light', onToggleTheme, onLanding = true }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [condensed, setCondensed] = useState(false);
  const toggleRef = useRef(null);
  const menuRef = useRef(null);
  const isDark = theme === 'dark';

  useEffect(() => {
    if (!menuOpen) return undefined;
    menuRef.current?.querySelector('a')?.focus();
    const onKey = (e) => {
      if (e.key === 'Escape') {
        setMenuOpen(false);
        toggleRef.current?.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [menuOpen]);

  useEffect(() => {
    const onScroll = () => setCondensed(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const closeMenu = () => setMenuOpen(false);

  return (
    <header className="saed-manual m-strip" data-condensed={condensed ? 'true' : undefined}>
      <div className="m-container m-strip-inner">
        <SectionLink href="#inicio" onLanding={onLanding} className="m-brand">
          <img src={SAED_EMBLEM} alt="" className="m-brand-mark" width="32" height="32" />
          <span className="m-brand-name">SAED</span>
          <span className="m-brand-ver">2.0</span>
          <span className="m-sr">, ir al inicio</span>
        </SectionLink>

        <nav className="m-strip-nav" aria-label="Secciones">
          {NAV_LINKS.map((link) => (
            <SectionLink key={link.href} href={link.href} onLanding={onLanding} className="m-strip-link">
              {link.name}
            </SectionLink>
          ))}
        </nav>

        <div className="m-strip-actions">
          {onToggleTheme && (
            <button
              type="button"
              onClick={onToggleTheme}
              className="m-icon-btn"
              aria-label={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              title={isDark ? 'Modo claro' : 'Modo oscuro'}
            >
              {isDark ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
            </button>
          )}
          <Link to="/login" className="m-btn m-btn--quiet m-hide-mobile">
            Iniciar sesión
          </Link>
          <Link to="/suscripciones" className="m-btn m-btn--primary m-hide-mobile">
            Ver planes
          </Link>
          <button
            ref={toggleRef}
            type="button"
            className="m-icon-btn m-menu-toggle"
            aria-expanded={menuOpen}
            aria-controls="m-mobile-menu"
            aria-label={menuOpen ? 'Cerrar menú' : 'Abrir menú'}
            onClick={() => setMenuOpen((open) => !open)}
          >
            {menuOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
          </button>
        </div>
      </div>

      <div id="m-mobile-menu" ref={menuRef} className="m-mobile-menu" hidden={!menuOpen}>
        <nav aria-label="Secciones" className="m-container m-mobile-nav">
          {NAV_LINKS.map((link) => (
            <SectionLink
              key={link.href}
              href={link.href}
              onLanding={onLanding}
              className="m-mobile-link"
              onClick={closeMenu}
            >
              {link.name}
            </SectionLink>
          ))}
          <div className="m-mobile-actions">
            <Link to="/login" className="m-btn m-btn--quiet" onClick={closeMenu}>
              Iniciar sesión
            </Link>
            <Link to="/suscripciones" className="m-btn m-btn--primary" onClick={closeMenu}>
              Ver planes
            </Link>
          </div>
        </nav>
      </div>
    </header>
  );
}
