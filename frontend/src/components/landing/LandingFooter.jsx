import { Building2, LogIn } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function LandingFooter() {
  const currentYear = new Date().getFullYear();

  const scrollTo = (id) => {
    const el = document.querySelector(id);
    if (el) {
      const topOffset = 84;
      const elementPosition = el.getBoundingClientRect().top;
      const offsetPosition = elementPosition + window.pageYOffset - topOffset;
      window.scrollTo({ top: offsetPosition, behavior: 'smooth' });
    }
  };

  return (
    <footer className="bg-[#050810] border-t border-slate-800/80 text-slate-400 pt-16 pb-12 text-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Main Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-10 pb-12 border-b border-slate-800/80">
          
          {/* Brand Identity Column */}
          <div className="lg:col-span-2 space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-[#122347] to-[#070B14] border border-sky-500/30 flex items-center justify-center text-sky-400 shadow-sm shadow-sky-500/10">
                <Building2 className="w-5 h-5" />
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xl font-extrabold text-white font-['Plus_Jakarta_Sans']">SAED</span>
                <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-sky-500/10 text-sky-400 border border-sky-500/25">
                  2.0
                </span>
              </div>
            </div>

            <p className="text-slate-400 max-w-sm leading-relaxed text-xs">
              Plataforma PropTech de gestión integral, control de acceso y finanzas para propiedades horizontales y conjuntos residenciales en Colombia.
            </p>

            <div className="flex items-center gap-2 text-sky-400 font-medium pt-1">
              <span className="w-2 h-2 rounded-full bg-sky-400 animate-pulse" />
              <span>Infraestructura Cloud Operativa</span>
            </div>

            <div className="pt-2">
              <Link
                to="/login"
                className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] text-white font-semibold transition-all border border-white/10 hover:border-white/20 backdrop-blur-md min-h-[44px]"
              >
                <LogIn className="w-4 h-4 text-sky-400" />
                <span>Acceder a la plataforma</span>
              </Link>
            </div>
          </div>

          {/* Navigation: Producto */}
          <div className="space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-white">Producto</h4>
            <ul className="space-y-2">
              <li>
                <a
                  href="#producto"
                  onClick={(e) => { e.preventDefault(); scrollTo('#producto'); }}
                  className="hover:text-sky-400 transition-colors py-1 block"
                >
                  Showcase Interactivo
                </a>
              </li>
              <li>
                <a
                  href="#soluciones"
                  onClick={(e) => { e.preventDefault(); scrollTo('#soluciones'); }}
                  className="hover:text-sky-400 transition-colors py-1 block"
                >
                  El Reto y la Solución
                </a>
              </li>
              <li>
                <a
                  href="#acceso"
                  onClick={(e) => { e.preventDefault(); scrollTo('#acceso'); }}
                  className="hover:text-sky-400 transition-colors py-1 block"
                >
                  Control de Visitas QR
                </a>
              </li>
              <li>
                <a
                  href="#paqueteria"
                  onClick={(e) => { e.preventDefault(); scrollTo('#paqueteria'); }}
                  className="hover:text-sky-400 transition-colors py-1 block"
                >
                  Paquetería con PIN
                </a>
              </li>
              <li>
                <a
                  href="#parqueaderos"
                  onClick={(e) => { e.preventDefault(); scrollTo('#parqueaderos'); }}
                  className="hover:text-sky-400 transition-colors py-1 block"
                >
                  Parqueaderos en Vivo
                </a>
              </li>
            </ul>
          </div>

          {/* Navigation: Seguridad & Marco */}
          <div className="space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-white">Seguridad</h4>
            <ul className="space-y-2">
              <li>
                <a
                  href="#seguridad"
                  onClick={(e) => { e.preventDefault(); scrollTo('#seguridad'); }}
                  className="hover:text-sky-400 transition-colors py-1 block"
                >
                  Aislamiento Multi-Tenant
                </a>
              </li>
              <li>
                <a
                  href="#seguridad"
                  onClick={(e) => { e.preventDefault(); scrollTo('#seguridad'); }}
                  className="hover:text-sky-400 transition-colors py-1 block"
                >
                  Políticas RLS en Base de Datos
                </a>
              </li>
              <li>
                <a
                  href="#seguridad"
                  onClick={(e) => { e.preventDefault(); scrollTo('#seguridad'); }}
                  className="hover:text-sky-400 transition-colors py-1 block"
                >
                  Marco Legal Ley 675
                </a>
              </li>
              <li>
                <a
                  href="#faq"
                  onClick={(e) => { e.preventDefault(); scrollTo('#faq'); }}
                  className="hover:text-sky-400 transition-colors py-1 block"
                >
                  Preguntas Frecuentes
                </a>
              </li>
            </ul>
          </div>

          {/* Navigation: Planes & Acceso */}
          <div className="space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-white">Comunidad</h4>
            <ul className="space-y-2">
              <li>
                <Link
                  to="/suscripciones"
                  className="hover:text-sky-400 transition-colors py-1 block"
                >
                  Planes y Suscripciones
                </Link>
              </li>
              <li>
                <Link to="/login" className="hover:text-emerald-300 transition-colors py-1 block">
                  Portal de Administradores
                </Link>
              </li>
              <li>
                <Link to="/login" className="hover:text-emerald-300 transition-colors py-1 block">
                  Consola de Garita
                </Link>
              </li>
              <li>
                <Link to="/login" className="hover:text-emerald-300 transition-colors py-1 block">
                  Portal de Copropietarios
                </Link>
              </li>
            </ul>
          </div>

        </div>

        {/* Bottom Bar */}
        <div className="pt-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-slate-500 text-[11px]">
          <p>© {currentYear} SAED 2.0 — Sistema Automatizado para Edificios Digitales.</p>
          <p>PropTech para propiedad horizontal · Diseñado bajo el marco operativo colombiano.</p>
        </div>

      </div>
    </footer>
  );
}
