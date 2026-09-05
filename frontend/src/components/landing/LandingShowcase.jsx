import { ArrowRight, ShieldCheck, Zap, BarChart3, CheckCircle2 } from 'lucide-react';
import { Link } from 'react-router-dom';

const METRICS = [
  {
    value: '5',
    label: 'Perfiles de Acceso',
    desc: 'SuperAdmin, Administrador, Portero, Residente y Organización.',
  },
  {
    value: 'QR',
    label: 'Control de Visitantes',
    desc: 'Pases digitales con validación ágil en la consola de portería.',
  },
  {
    value: 'Multi-Tenant',
    label: 'Arquitectura Aislada',
    desc: 'Espacio de datos independiente para cada copropiedad.',
  },
  {
    value: 'Tiempo Real',
    label: 'Operación Centralizada',
    desc: 'Sincronización inmediata entre administración, garita y residentes.',
  },
];

export default function LandingShowcase() {
  return (
    <section className="py-20 bg-card/60 border-t border-border relative overflow-hidden">
      {/* Background Accent Gradients */}
      <div className="absolute top-0 right-1/4 w-96 h-96 bg-primary/5 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-0 left-1/4 w-96 h-96 bg-emerald-500/5 rounded-full blur-3xl pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        {/* Metric Cards Row */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6 mb-16">
          {METRICS.map((m, idx) => (
            <div
              key={idx}
              className="bg-card border border-border/80 rounded-2xl p-6 text-center shadow-sm hover:border-primary/30 transition-colors"
            >
              <div className="text-2xl sm:text-3xl lg:text-4xl font-extrabold text-foreground tracking-tight mb-1">
                {m.value}
              </div>
              <div className="text-xs sm:text-sm font-semibold text-primary mb-1">
                {m.label}
              </div>
              <p className="text-xs text-muted-foreground leading-relaxed">
                {m.desc}
              </p>
            </div>
          ))}
        </div>

        {/* Big Conversion Banner */}
        <div className="bg-gradient-to-br from-primary/10 via-card to-background border-2 border-primary/20 rounded-3xl p-8 sm:p-12 lg:p-16 text-center relative shadow-lg">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold bg-primary/20 text-primary border border-primary/30 mb-6">
            <Zap className="w-3.5 h-3.5 text-primary" />
            <span>Listo para Demostración y Operación</span>
          </div>

          <h2 className="text-3xl sm:text-4xl lg:text-5xl font-black text-foreground tracking-tight max-w-3xl mx-auto">
            Seguridad, orden y finanzas claras para tu copropiedad
          </h2>

          <p className="mt-4 text-base sm:text-lg text-muted-foreground max-w-2xl mx-auto leading-relaxed">
            Descubre cómo SAED 2.0 simplifica el día a día de administradores, guardias y residentes eliminando el papel y centralizando la operación en tiempo real.
          </p>

          <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link
              to="/login"
              className="w-full sm:w-auto px-8 py-4 rounded-xl bg-primary text-primary-foreground font-bold text-base shadow-lg shadow-primary/25 hover:bg-primary/90 transition-all flex items-center justify-center gap-2 group min-h-[48px]"
            >
              <span>Entrar a la plataforma</span>
              <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
            </Link>

            <a
              href="#funcionalidades"
              className="w-full sm:w-auto px-8 py-4 rounded-xl bg-secondary text-secondary-foreground font-semibold text-base border border-border hover:bg-muted/80 transition-colors flex items-center justify-center gap-2 min-h-[48px]"
            >
              <span>Explorar capacidades</span>
            </a>
          </div>

          {/* Micro trust indicators */}
          <div className="mt-10 pt-8 border-t border-border/60 flex flex-wrap items-center justify-center gap-6 sm:gap-8 text-xs text-muted-foreground">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
              <span>Sin instalaciones en teléfonos</span>
            </div>
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-emerald-500 shrink-0" />
              <span>Aislamiento de datos por copropiedad</span>
            </div>
            <div className="flex items-center gap-2">
              <BarChart3 className="w-4 h-4 text-emerald-500 shrink-0" />
              <span>Informes financieros con Wompi</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
