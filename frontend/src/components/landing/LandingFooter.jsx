import { ArrowRight, CheckCircle2 } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function LandingFooter() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="bg-white dark:bg-[#060B14] border-t border-slate-200/80 dark:border-slate-800/80 text-slate-800 dark:text-slate-200 pt-20 pb-12">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Top Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-10 pb-16 border-b border-slate-200 dark:border-slate-800/80">
          {/* Brand Column */}
          <div className="lg:col-span-2 space-y-5">
            <div className="flex items-center gap-3">
              <img
                src={`${import.meta.env.BASE_URL}imagenes/saed_logo_final_blue.png`}
                alt="SAED Logo"
                className="h-10 w-auto object-contain"
                onError={(e) => {
                  e.currentTarget.style.display = 'none';
                }}
              />
              <div className="flex items-center gap-2">
                <span className="text-2xl font-black text-slate-900 dark:text-white tracking-tight">SAED</span>
                <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-primary/10 text-primary border border-primary/20">
                  2.0
                </span>
              </div>
            </div>

            <p className="text-sm text-slate-600 dark:text-slate-400 max-w-sm leading-relaxed">
              Plataforma PropTech de gestión integral, control de acceso y finanzas para copropiedades y edificios residenciales en Colombia.
            </p>

            <div className="flex items-center gap-2 text-xs font-semibold text-emerald-600 dark:text-emerald-400 pt-1">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
              <span>Infraestructura Cloud Operativa</span>
            </div>

            <div className="pt-2">
              <Link
                to="/login"
                className="inline-flex items-center gap-2 px-5 py-3 rounded-2xl bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-all shadow-md shadow-primary/20 min-h-[44px]"
              >
                <span>Acceder a la plataforma</span>
                <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          </div>

          {/* Column 1: Plataforma */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-widest text-slate-900 dark:text-white mb-4">
              Módulos Principales
            </h4>
            <ul className="space-y-3 text-sm text-slate-600 dark:text-slate-400">
              <li>
                <a href="#seguridad-acceso" className="hover:text-primary transition-colors py-1 block">
                  Control de Visitas QR
                </a>
              </li>
              <li>
                <a href="#operacion-fisica" className="hover:text-primary transition-colors py-1 block">
                  Consola de Portería
                </a>
              </li>
              <li>
                <a href="#operacion-fisica" className="hover:text-primary transition-colors py-1 block">
                  Paquetería con PIN
                </a>
              </li>
              <li>
                <a href="#operacion-fisica" className="hover:text-primary transition-colors py-1 block">
                  Parqueaderos de Visitantes
                </a>
              </li>
              <li>
                <a href="#capacidades" className="hover:text-primary transition-colors py-1 block">
                  Recaudo con Wompi
                </a>
              </li>
              <li>
                <a href="#capacidades" className="hover:text-primary transition-colors py-1 block">
                  Portal del Residente
                </a>
              </li>
            </ul>
          </div>

          {/* Column 2: Perfiles & Soluciones */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-widest text-slate-900 dark:text-white mb-4">
              Perfiles y Cobertura
            </h4>
            <ul className="space-y-3 text-sm text-slate-600 dark:text-slate-400">
              <li>
                <a href="#audiencia" className="hover:text-primary transition-colors py-1 block">
                  Administración de Copropiedad
                </a>
              </li>
              <li>
                <a href="#audiencia" className="hover:text-primary transition-colors py-1 block">
                  Personal de Seguridad y Porteros
                </a>
              </li>
              <li>
                <a href="#audiencia" className="hover:text-primary transition-colors py-1 block">
                  Residentes y Propietarios
                </a>
              </li>
              <li>
                <a href="#audiencia" className="hover:text-primary transition-colors py-1 block">
                  Administradoras Multi-Edificio
                </a>
              </li>
              <li>
                <a href="#planes" className="hover:text-primary transition-colors py-1 block">
                  Planes Comerciales
                </a>
              </li>
              <li>
                <a href="#faq" className="hover:text-primary transition-colors py-1 block">
                  Preguntas Frecuentes
                </a>
              </li>
            </ul>
          </div>

          {/* Column 3: Seguridad & Conformidad */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-widest text-slate-900 dark:text-white mb-4">
              Seguridad & Conformidad
            </h4>
            <ul className="space-y-3 text-sm text-slate-600 dark:text-slate-400">
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                <span>Aislamiento Multi-Tenant</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                <span>Control de roles RBAC</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                <span>Bitácora inmutable de eventos</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                <span>Régimen Ley 675 de 2001</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                <span>Cifrado de datos en tránsito</span>
              </li>
            </ul>
          </div>
        </div>

        {/* Bottom Bar */}
        <div className="pt-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-500 dark:text-slate-400">
          <p>
            &copy; {currentYear} SAED 2.0 — Sistema Automatizado para Edificios Digitales. Todos los derechos reservados.
          </p>
          <div className="flex items-center gap-6">
            <Link to="/login" className="hover:text-primary transition-colors font-bold py-1 min-h-[36px] flex items-center">
              Iniciar sesión
            </Link>
            <a href="#hero" className="hover:text-primary transition-colors py-1 min-h-[36px] flex items-center">
              Volver al inicio ↑
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
