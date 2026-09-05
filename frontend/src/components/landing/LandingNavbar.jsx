import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Building2, Menu, X, ArrowRight, LogIn } from 'lucide-react';

const NAV_LINKS = [
  { name: 'Inicio', href: '#hero' },
  { name: '¿Qué es SAED?', href: '#que-es-saed' },
  { name: 'Módulos', href: '#funcionalidades' },
  { name: 'Seguridad & QR', href: '#seguridad' },
  { name: 'Operación', href: '#operacion' },
  { name: 'Planes', href: '#planes' },
  { name: 'FAQ', href: '#faq' },
];

export default function LandingNavbar() {
  const [isScrolled, setIsScrolled] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const handleLinkClick = (e, href) => {
    e.preventDefault();
    setMobileMenuOpen(false);
    const target = document.querySelector(href);
    if (target) {
      const topOffset = 80;
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
          ? 'bg-[#0A1628]/95 backdrop-blur-md border-b border-[#1E293B] shadow-lg shadow-black/20 py-3'
          : 'bg-[#0A1628]/80 backdrop-blur-sm border-b border-white/5 py-4'
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between">
          {/* Brand Logo */}
          <a
            href="#hero"
            onClick={(e) => handleLinkClick(e, '#hero')}
            className="flex items-center gap-3 group focus:outline-none focus:ring-2 focus:ring-emerald-500/50 rounded-lg p-1"
          >
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#1E4080] to-[#0A1628] border border-emerald-500/30 flex items-center justify-center shadow-md shadow-emerald-500/10 group-hover:border-emerald-500/60 transition-colors">
              <Building2 className="w-5 h-5 text-emerald-400" />
            </div>
            <div className="flex flex-col">
              <div className="flex items-center gap-2">
                <span className="text-xl font-bold tracking-tight text-white font-['Plus_Jakarta_Sans']">
                  SAED
                </span>
                <span className="inline-flex items-center px-1.5 py-0.5 rounded text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  2.0
                </span>
              </div>
              <span className="text-xs text-slate-400 -mt-0.5 hidden sm:block tracking-wider uppercase">
                Administración Residencial
              </span>
            </div>
          </a>

          {/* Desktop Navigation Links */}
          <nav className="hidden md:flex items-center gap-1 lg:gap-2">
            {NAV_LINKS.map((link) => (
              <a
                key={link.name}
                href={link.href}
                onClick={(e) => handleLinkClick(e, link.href)}
                className="px-3 py-2 text-sm font-medium text-slate-300 hover:text-white rounded-md hover:bg-white/5 transition-colors focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
              >
                {link.name}
              </a>
            ))}
          </nav>

          {/* Actions & Login CTA */}
          <div className="hidden sm:flex items-center gap-3">
            <Link
              to="/login"
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-lg shadow-md shadow-emerald-900/30 hover:shadow-emerald-700/40 focus:outline-none focus:ring-2 focus:ring-emerald-400 focus:ring-offset-2 focus:ring-offset-[#0A1628] transition-all transform active:scale-95"
            >
              <LogIn className="w-4 h-4" />
              <span>Iniciar sesión</span>
              <ArrowRight className="w-3.5 h-3.5 opacity-80" />
            </Link>
          </div>

          {/* Mobile Hamburger Toggle & Quick Login */}
          <div className="flex items-center gap-2 md:hidden">
            <Link
              to="/login"
              className="min-h-[40px] px-3.5 py-2 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-500 rounded-lg flex items-center justify-center shadow-sm"
            >
              Ingresar
            </Link>
            <button
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              className="min-h-[40px] min-w-[40px] p-2 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 flex items-center justify-center focus:outline-none focus:ring-2 focus:ring-emerald-500"
              aria-label={mobileMenuOpen ? 'Cerrar menú' : 'Abrir menú'}
            >
              {mobileMenuOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Drawer Menu */}
      {mobileMenuOpen && (
        <div className="md:hidden bg-[#0F172A] border-b border-slate-800 px-4 pt-3 pb-6 space-y-2 animate-fadeIn">
          {NAV_LINKS.map((link) => (
            <a
              key={link.name}
              href={link.href}
              onClick={(e) => handleLinkClick(e, link.href)}
              className="block px-3 py-2.5 text-base font-medium text-slate-300 hover:text-white hover:bg-slate-800/60 rounded-lg transition-colors"
            >
              {link.name}
            </a>
          ))}
          <div className="pt-3 border-t border-slate-800">
            <Link
              to="/login"
              onClick={() => setMobileMenuOpen(false)}
              className="w-full min-h-[44px] flex items-center justify-center gap-2 px-4 py-3 text-sm font-semibold text-white bg-emerald-600 hover:bg-emerald-500 rounded-lg shadow-sm"
            >
              <LogIn className="w-4 h-4" />
              <span>Iniciar sesión en SAED</span>
            </Link>
          </div>
        </div>
      )}
    </header>
  );
}
