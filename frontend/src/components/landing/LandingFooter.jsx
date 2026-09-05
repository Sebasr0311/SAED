import { ArrowRight, CheckCircle2 } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function LandingFooter() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="bg-card border-t border-border text-card-foreground pt-16 pb-12">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Top Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-10 pb-12 border-b border-border">
          {/* Brand Column */}
          <div className="lg:col-span-2 space-y-4">
            <div className="flex items-center gap-3">
              <img
                src={`${import.meta.env.BASE_URL}imagenes/saed_logo_final_blue.png`}
                alt="SAED Logo"
                className="h-9 w-auto object-contain"
                onError={(e) => {
                  e.currentTarget.style.display = 'none';
                }}
              />
              <div className="flex items-center gap-1.5">
                <span className="text-xl font-black text-foreground tracking-tight">SAED</span>
                <span className="text-xs font-bold px-1.5 py-0.5 rounded bg-primary/15 text-primary border border-primary/20">
                  2.0
                </span>
              </div>
            </div>

            <p className="text-sm text-muted-foreground max-w-sm leading-relaxed">
              Plataforma SaaS de gestión integral y control de acceso inteligente para edificios, conjuntos residenciales y copropiedades.
            </p>

            <div className="flex items-center gap-2 text-xs font-semibold text-emerald-600 dark:text-emerald-400 pt-2">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span>Infraestructura Cloud Activa</span>
            </div>

            <div className="pt-2">
              <Link
                to="/login"
                className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors shadow-sm min-h-[40px]"
              >
                <span>Acceder a la plataforma</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>

          {/* Column 1: Plataforma */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-foreground mb-4">
              Módulos Principales
            </h4>
            <ul className="space-y-2.5 text-sm text-muted-foreground">
              <li>
                <a href="#seguridad" className="hover:text-primary transition-colors py-0.5 block">
                  Control de Visitas QR
                </a>
              </li>
              <li>
                <a href="#operacion" className="hover:text-primary transition-colors py-0.5 block">
                  Consola de Portería
                </a>
              </li>
              <li>
                <a href="#operacion" className="hover:text-primary transition-colors py-0.5 block">
                  Paquetería con PIN
                </a>
              </li>
              <li>
                <a href="#operacion" className="hover:text-primary transition-colors py-0.5 block">
                  Parqueaderos de Visitantes
                </a>
              </li>
              <li>
                <a href="#funcionalidades" className="hover:text-primary transition-colors py-0.5 block">
                  Cartera & Pasarela Wompi
                </a>
              </li>
              <li>
                <a href="#funcionalidades" className="hover:text-primary transition-colors py-0.5 block">
                  Portal del Residente
                </a>
              </li>
            </ul>
          </div>

          {/* Column 2: Perfiles */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-foreground mb-4">
              Perfiles y Cobertura
            </h4>
            <ul className="space-y-2.5 text-sm text-muted-foreground">
              <li>
                <a href="#audiencia" className="hover:text-primary transition-colors py-0.5 block">
                  Administradores de Copropiedad
                </a>
              </li>
              <li>
                <a href="#audiencia" className="hover:text-primary transition-colors py-0.5 block">
                  Personal de Seguridad y Porteros
                </a>
              </li>
              <li>
                <a href="#audiencia" className="hover:text-primary transition-colors py-0.5 block">
                  Residentes y Propietarios
                </a>
              </li>
              <li>
                <a href="#audiencia" className="hover:text-primary transition-colors py-0.5 block">
                  Empresas Multi-Edificio
                </a>
              </li>
              <li>
                <a href="#planes" className="hover:text-primary transition-colors py-0.5 block">
                  Planes Comerciales
                </a>
              </li>
              <li>
                <a href="#faq" className="hover:text-primary transition-colors py-0.5 block">
                  Preguntas Frecuentes
                </a>
              </li>
            </ul>
          </div>

          {/* Column 3: Seguridad & Privacidad */}
          <div>
            <h4 className="text-xs font-bold uppercase tracking-wider text-foreground mb-4">
              Seguridad y Privacidad
            </h4>
            <ul className="space-y-2.5 text-sm text-muted-foreground">
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                <span>Datos protegidos</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                <span>Acceso por roles</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                <span>Trazabilidad</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                <span>Arquitectura multi-copropiedad</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 shrink-0" />
                <span>Marco normativo Ley 675</span>
              </li>
            </ul>
          </div>
        </div>

        {/* Bottom Copyright & Status */}
        <div className="pt-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-muted-foreground">
          <p>
            &copy; {currentYear} SAED 2.0 — Sistema Automatizado para Edificios Digitales. Todos los derechos reservados.
          </p>
          <div className="flex items-center gap-6">
            <Link to="/login" className="hover:text-primary transition-colors font-semibold py-1">
              Iniciar sesión
            </Link>
            <a href="#hero" className="hover:text-primary transition-colors py-1">
              Volver arriba ↑
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
