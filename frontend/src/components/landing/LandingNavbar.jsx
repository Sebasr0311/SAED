import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Building2, Menu, X, ArrowRight, LogIn } from 'lucide-react';

const NAV_LINKS = [
  { name: 'Producto', href: '#hero' },
  { name: 'Soluciones', href: '#soluciones' },
  { name: 'Seguridad', href: '#seguridad' },
  { name: 'Planes', href: '#planes' },
];

export default function LandingNavbar() {
  const [isScrolled, setIsScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const handleLinkClick = (e, href) => {
    e.preventDefault();
    setMobileMenuOpen(false);
    const target = document.querySelector(href);
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
        isScrolled
          ? 'bg-[#0A1628]/90 backdrop-blur-xl border-b border-slate-800/80 shadow-lg shadow-black/25 py-3.5'
          : 'bg-[#0A1628]/60 backdrop-blur-md border-b border-white/5 py-4 sm:py-5'
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between">
          {/* Brand Identity */}
          <a
            href="#hero"
            onClick={(e) => handleLinkClick(e, '#hero')}
            className="flex items-center gap-3 group focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500/50 rounded-xl py-1 px-1.5 transition-colors"
            aria-label="Ir al inicio de SAED 2.0"
          >
            <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-gradient-to-br from-[#1E4080] to-[#0A1628] border border-emerald-500/30 flex items-center justify-center shadow-sm shadow-emerald-500/10 group-hover:border-emerald-500/60 transition-colors">
              <Building2 className="w-4 h-4 sm:w-5 sm:h-5 text-emerald-400" />
            </div>
            <div className="flex items-center gap-2">
              <span className="text-xl sm:text-2xl font-extrabold tracking-tight text-white font-['Plus_Jakarta_Sans']">
                SAED
              </span>
              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/25 tracking-wide">
                2.0
              </span>
            </div>
          </a>

          {/* Desktop Minimalist Navigation */}
          <nav
            className="hidden md:flex items-center gap-1 lg:gap-2 px-3 py-1.5 rounded-full bg-white/[0.03] border border-white/5 backdrop-blur-md"
            aria-label="Navegación principal"
          >
            {NAV_LINKS.map((link) => (
              <a
                key={link.name}
                href={link.href}
                onClick={(e) => handleLinkClick(e, link.href)}
                className="px-3.5 py-1.5 text-xs lg:text-sm font-medium text-slate-300 hover:text-white rounded-full hover:bg-white/10 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
              >
                {link.name}
              </a>
            ))}
          </nav>

          {/* Actions & Login CTA */}
          <div className="hidden sm:flex items-center gap-3">
            <Link
              to="/login"
              className="px-4 py-2 text-xs sm:text-sm font-semibold text-slate-200 hover:text-white hover:bg-white/5 rounded-xl border border-transparent hover:border-slate-800 transition-all flex items-center gap-1.5 min-h-[42px]"
            >
              <LogIn className="w-3.5 h-3.5 opacity-80" />
              <span>Iniciar sesión</span>
            </Link>

            <a
              href="#que-es-saed"
              onClick={(e) => handleLinkClick(e, '#que-es-saed')}
              className="inline-flex items-center gap-2 px-4 sm:px-5 py-2 text-xs sm:text-sm font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-xl shadow-md shadow-emerald-950/40 hover:shadow-emerald-700/50 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 transition-all transform active:scale-95 min-h-[42px]"
            >
              <span>Conocer SAED</span>
              <ArrowRight className="w-3.5 h-3.5 opacity-90" />
            </a>
          </div>

          {/* Mobile Actions & Toggle */}
          <div className="flex items-center gap-2 sm:hidden">
            <Link
              to="/login"
              className="px-3.5 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-500 rounded-lg shadow-sm flex items-center gap-1 min-h-[40px]"
            >
              <LogIn className="w-3.5 h-3.5" />
              <span>Ingresar</span>
            </Link>

            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="min-h-[40px] min-w-[40px] p-2 rounded-lg text-slate-300 hover:text-white hover:bg-white/10 flex items-center justify-center focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400"
              aria-label={mobileMenuOpen ? 'Cerrar menú' : 'Abrir menú'}
              aria-expanded={mobileMenuOpen}
            >
              {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Drawer Menu */}
      {mobileMenuOpen && (
        <div className="sm:hidden bg-[#0A1628]/95 backdrop-blur-2xl border-b border-slate-800/80 px-4 pt-4 pb-6 space-y-3 animate-fadeIn">
          <nav className="space-y-1" aria-label="Menú móvil">
            {NAV_LINKS.map((link) => (
              <a
                key={link.name}
                href={link.href}
                onClick={(e) => handleLinkClick(e, link.href)}
                className="block px-3.5 py-2.5 text-base font-medium text-slate-200 hover:text-white hover:bg-white/5 rounded-xl transition-colors min-h-[44px] flex items-center"
              >
                {link.name}
              </a>
            ))}
          </nav>

          <div className="pt-3 border-t border-slate-800/80 space-y-2">
            <Link
              to="/login"
              onClick={() => setMobileMenuOpen(false)}
              className="w-full min-h-[44px] flex items-center justify-center gap-2 px-4 py-3 text-sm font-semibold text-white bg-emerald-600 hover:bg-emerald-500 rounded-xl shadow-md transition-colors"
            >
              <LogIn className="w-4 h-4" />
              <span>Iniciar sesión en SAED</span>
            </Link>

            <a
              href="#que-es-saed"
              onClick={(e) => handleLinkClick(e, '#que-es-saed')}
              className="w-full min-h-[44px] flex items-center justify-center gap-2 px-4 py-3 text-sm font-semibold text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 rounded-xl transition-colors"
            >
              <span>Conocer más de la plataforma</span>
            </a>
          </div>
        </div>
      )}
    </header>
  );
}
