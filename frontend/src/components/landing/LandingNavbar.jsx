import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Building2, Menu, X, ArrowRight, LogIn, Sparkles } from 'lucide-react';

const NAV_LINKS = [
  { name: 'Perfiles', href: '#roles' },
  { name: 'Módulos', href: '#soluciones' },
  { name: 'Beneficios', href: '#beneficios' },
  { name: 'Preguntas', href: '#faq' },
];

export default function LandingNavbar() {
  const [isScrolled, setIsScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  useEffect(() => {
    if (mobileMenuOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
    return () => {
      document.body.style.overflow = '';
    };
  }, [mobileMenuOpen]);

  const handleLinkClick = (e, link) => {
    if (link.isRoute || link.href.startsWith('/')) {
      // Let standard router Link or navigate handle it
      return;
    }
    e.preventDefault();
    setMobileMenuOpen(false);
    const target = document.querySelector(link.href);
    if (target) {
      const topOffset = 84;
      const elementPosition = target.getBoundingClientRect().top;
      const offsetPosition = elementPosition + window.pageYOffset - topOffset;
      window.scrollTo({
        top: offsetPosition,
        behavior: 'smooth',
      });
    }
  };

  return (
    <header
      className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${
        mobileMenuOpen
          ? 'bg-[#070B14] border-b border-slate-800 py-3.5 shadow-2xl'
          : isScrolled
          ? 'bg-[#070B14]/90 backdrop-blur-xl border-b border-slate-800/80 shadow-2xl shadow-black/60 py-3.5'
          : 'bg-[#070B14]/60 backdrop-blur-md border-b border-white/[0.06] py-4 sm:py-5'
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between">
          {/* Brand Identity */}
          <a
            href="#hero"
            onClick={(e) => handleLinkClick(e, { href: '#hero' })}
            className="flex items-center gap-3 group focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 rounded-xl py-1 px-1.5 transition-colors"
            aria-label="Ir al inicio de SAED 2.0"
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
          </a>

          {/* Desktop Minimalist Navigation */}
          <nav
            className="hidden md:flex items-center gap-1 lg:gap-2 px-4 py-1.5 rounded-full bg-white/[0.04] border border-white/10 backdrop-blur-md"
            aria-label="Navegación principal"
          >
            {NAV_LINKS.map((link) =>
              link.isRoute ? (
                <Link
                  key={link.name}
                  to={link.href}
                  className="px-3.5 py-1.5 text-xs lg:text-sm font-medium text-slate-300 hover:text-white rounded-full hover:bg-white/10 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 min-h-[36px] flex items-center"
                >
                  {link.name}
                </Link>
              ) : (
                <a
                  key={link.name}
                  href={link.href}
                  onClick={(e) => handleLinkClick(e, link)}
                  className="px-3.5 py-1.5 text-xs lg:text-sm font-medium text-slate-300 hover:text-white rounded-full hover:bg-white/10 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 min-h-[36px] flex items-center"
                >
                  {link.name}
                </a>
              )
            )}
          </nav>

          {/* Actions & Login CTA */}
          <div className="hidden sm:flex items-center gap-3">
            <Link
              to="/login"
              className="px-4 py-2 text-xs sm:text-sm font-semibold text-slate-300 hover:text-white hover:bg-white/[0.06] rounded-xl border border-transparent hover:border-white/10 transition-all flex items-center gap-1.5 min-h-[44px]"
            >
              <LogIn className="w-4 h-4 text-sky-400" />
              <span>Iniciar sesión</span>
            </Link>

            <Link
              to="/suscripciones"
              className="px-5 py-2.5 text-xs sm:text-sm font-bold text-slate-950 bg-gradient-to-r from-cyan-400 via-sky-400 to-cyan-400 hover:from-cyan-300 hover:to-sky-300 rounded-xl shadow-md shadow-sky-500/20 hover:shadow-sky-400/35 transition-all flex items-center gap-1.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 min-h-[44px]"
            >
              <Sparkles className="w-3.5 h-3.5 text-slate-950" />
              <span>Suscripciones</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          {/* Mobile Hamburger Button */}
          <button
            type="button"
            onClick={() => setMobileMenuOpen((open) => !open)}
            className="md:hidden p-2.5 rounded-xl text-slate-300 hover:text-white bg-slate-900/80 hover:bg-slate-800 border border-slate-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 min-w-[44px] min-h-[44px] flex items-center justify-center"
            aria-expanded={mobileMenuOpen}
            aria-label={mobileMenuOpen ? 'Cerrar menú' : 'Abrir menú'}
          >
            {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>
      </div>

      {/* Mobile Backdrop & Drawer Navigation */}
      {mobileMenuOpen && (
        <>
          {/* Backdrop oscurecido total para evitar que el texto del landing se vea o colisione */}
          <div
            className="md:hidden fixed inset-0 bg-black/80 backdrop-blur-md z-40 transition-opacity"
            onClick={() => setMobileMenuOpen(false)}
            aria-hidden="true"
          />

          {/* Menú móvil 100% opaco y pegado al navbar */}
          <div
            className="md:hidden fixed inset-x-0 top-[60px] sm:top-[68px] bg-[#070B14] border-b border-slate-800 shadow-2xl p-5 space-y-4 z-50 animate-in fade-in slide-in-from-top-2 duration-200 max-h-[calc(100vh-70px)] overflow-y-auto"
          >
            <nav className="flex flex-col space-y-2">
              {NAV_LINKS.map((link) =>
                link.isRoute ? (
                  <Link
                    key={link.name}
                    to={link.href}
                    onClick={() => setMobileMenuOpen(false)}
                    className="px-4 py-3 rounded-xl text-base font-medium text-slate-200 hover:text-white bg-slate-900/50 hover:bg-slate-800 border border-slate-800/60 transition-colors flex items-center justify-between min-h-[48px]"
                  >
                    <span>{link.name}</span>
                    <ArrowRight className="w-4 h-4 text-sky-400" />
                  </Link>
                ) : (
                  <a
                    key={link.name}
                    href={link.href}
                    onClick={(e) => handleLinkClick(e, link)}
                    className="px-4 py-3 rounded-xl text-base font-medium text-slate-200 hover:text-white bg-slate-900/50 hover:bg-slate-800 border border-slate-800/60 transition-colors flex items-center justify-between min-h-[48px]"
                  >
                    <span>{link.name}</span>
                    <ArrowRight className="w-4 h-4 text-sky-400" />
                  </a>
                )
              )}
            </nav>

            <div className="pt-4 border-t border-slate-800 flex flex-col gap-3">
              <Link
                to="/login"
                onClick={() => setMobileMenuOpen(false)}
                className="w-full py-3.5 px-4 rounded-xl text-sm font-semibold text-center text-white bg-slate-800 hover:bg-slate-700 border border-slate-700 transition-colors flex items-center justify-center gap-2 min-h-[48px]"
              >
                <LogIn className="w-4 h-4 text-sky-400" />
                <span>Iniciar sesión</span>
              </Link>

              <Link
                to="/suscripciones"
                onClick={() => setMobileMenuOpen(false)}
                className="w-full py-3.5 px-4 rounded-xl text-sm font-bold text-center text-slate-950 bg-gradient-to-r from-cyan-400 via-sky-400 to-cyan-400 hover:from-cyan-300 hover:to-sky-300 shadow-lg shadow-sky-950/50 transition-all flex items-center justify-center gap-2 min-h-[48px]"
              >
                <Sparkles className="w-4 h-4" />
                <span>Suscripciones</span>
                <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>
        </>
      )}
    </header>
  );
}
