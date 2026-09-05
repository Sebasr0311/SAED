import { ShieldCheck, CreditCard, Smartphone, Lock, Scale, CloudCheck, Check } from 'lucide-react';

const BENEFITS = [
  {
    icon: ShieldCheck,
    title: 'Trazabilidad y Auditoría',
    desc: 'Cada validación QR, recepción de correspondencia y recaudo queda asentada en la bitácora con registro de fecha, hora y usuario responsable.',
    tag: 'Auditoría Continua',
  },
  {
    icon: CreditCard,
    title: 'Recaudo Integrado con Wompi',
    desc: 'Facilita el pago oportuno de cuotas ordinarias y extraordinarias mediante PSE y tarjetas, con conciliación bancaria en tiempo real.',
    tag: 'Pagos Digitales',
  },
  {
    icon: Smartphone,
    title: 'Acceso Web Sin Instalaciones',
    desc: 'Los residentes y visitantes interactúan sin necesidad de descargar aplicaciones pesadas, directamente desde cualquier navegador móvil o de escritorio.',
    tag: 'Acceso Universal',
  },
  {
    icon: Lock,
    title: 'Aislamiento Multi-Tenant',
    desc: 'Arquitectura diseñada para que la información de cada copropiedad permanezca separada e inaccesible para otros conjuntos o usuarios no autorizados.',
    tag: 'Privacidad de Datos',
  },
  {
    icon: Scale,
    title: 'Diseñado bajo el marco de la Ley 675',
    desc: 'Estructura adaptada al régimen de propiedad horizontal en Colombia: coeficientes, asambleas, cuotas ordinarias, multas y certificados de paz y salvo.',
    tag: 'Marco Normativo',
  },
  {
    icon: CloudCheck,
    title: 'Infraestructura de Alta Disponibilidad',
    desc: 'Arquitectura cloud basada en motor de base de datos relacional robusto, con copias de seguridad continuas y cifrado de conexiones.',
    tag: 'Alta Disponibilidad',
  },
];

export default function LandingBenefits() {
  return (
    <section id="beneficios" className="py-24 bg-card/50 border-t border-border relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold uppercase tracking-wider bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20">
            Ventajas Operativas
          </span>
          <h2 className="mt-4 text-3xl sm:text-4xl font-extrabold text-foreground tracking-tight">
            ¿Por qué las copropiedades eligen SAED 2.0?
          </h2>
          <p className="mt-4 text-base sm:text-lg text-muted-foreground leading-relaxed">
            Ingeniería de software de grado empresarial diseñada para ordenar la operación diaria y respaldar la gestión del consejo y la administración.
          </p>
        </div>

        {/* Benefits Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 sm:gap-8">
          {BENEFITS.map((b, idx) => {
            const Icon = b.icon;
            return (
              <div
                key={idx}
                className="bg-card border border-border/80 hover:border-primary/40 rounded-2xl p-6 sm:p-7 transition-all duration-200 hover:shadow-md hover:shadow-primary/5 flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between gap-3 mb-5">
                    <div className="w-12 h-12 rounded-xl bg-primary/10 border border-primary/20 flex items-center justify-center text-primary">
                      <Icon className="w-6 h-6" />
                    </div>
                    <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-muted text-muted-foreground border border-border">
                      {b.tag}
                    </span>
                  </div>

                  <h3 className="text-lg font-bold text-foreground mb-2">
                    {b.title}
                  </h3>

                  <p className="text-sm text-muted-foreground leading-relaxed">
                    {b.desc}
                  </p>
                </div>

                <div className="mt-6 pt-4 border-t border-border/50 flex items-center gap-2 text-xs font-medium text-emerald-600 dark:text-emerald-400">
                  <Check className="w-4 h-4 shrink-0" />
                  <span>Capacidad nativa de SAED 2.0</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
