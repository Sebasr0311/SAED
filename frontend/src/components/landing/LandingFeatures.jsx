import {
  QrCode,
  CreditCard,
  Package,
  Car,
  Users,
  Building2,
} from 'lucide-react';

const MODULES = [
  {
    icon: QrCode,
    badge: 'Módulo Principal',
    badgeColor: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    title: 'Control de Acceso Inteligente',
    desc: 'Pases digitales temporales con código QR. Validación rápida en portería sin llamadas telefónicas ni demoras en la entrada.',
    features: ['Token de uso único o temporal', 'Validación ágil en portería', 'Registro de fecha y hora'],
  },
  {
    icon: CreditCard,
    badge: 'Finanzas & Recaudo',
    badgeColor: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    title: 'Cartera y Recaudo Digital',
    desc: 'Liquidación de cuotas ordinarias y extraordinarias con pasarela Wompi integrada (PSE y tarjetas) para conciliación inmediata.',
    features: ['Estados de cuenta en línea', 'Pagos integrados con Wompi', 'Certificados de paz y salvo'],
  },
  {
    icon: Package,
    badge: 'Logística en Garita',
    badgeColor: 'bg-teal-500/10 text-teal-400 border-teal-500/20',
    title: 'Custodia de Paquetería',
    desc: 'Recepción clasificada de encomiendas con asignación de PIN de seguridad notificado al casillero digital del residente.',
    features: ['Retiro seguro mediante PIN', 'Aviso al buzón del residente', 'Registro de transportadora'],
  },
  {
    icon: Car,
    badge: 'Movilidad & Puestos',
    badgeColor: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    title: 'Parqueaderos y Vehículos',
    desc: 'Gestión en tiempo real de cupos para visitantes, asociación de placa vehicular y liberación automática al salir.',
    features: ['Disponibilidad de cupos en vivo', 'Asociación de placa vehicular', 'Control de permanencia'],
  },
  {
    icon: Users,
    badge: 'Portal del Habitante',
    badgeColor: 'bg-indigo-500/10 text-indigo-400 border-indigo-500/20',
    title: 'Portal del Residente',
    desc: 'Autonomía web para consultar saldos, autorizar invitados, agendar visitas frecuentes y radicar peticiones o reclamos.',
    features: ['Acceso web sin descargas', 'Generación ágil de pases QR', 'Radicación y estado de PQRS'],
  },
  {
    icon: Building2,
    badge: 'Gobernanza',
    badgeColor: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
    title: 'Gestión de Copropiedades',
    desc: 'Censo de unidades, coeficientes de copropiedad, contratos de arrendamiento y asambleas bajo arquitectura multi-tenant.',
    features: ['Padrón de unidades y residentes', 'Aislamiento de datos por conjunto', 'Historial para auditoría'],
  },
];

export default function LandingFeatures() {
  return (
    <section id="funcionalidades" className="py-20 bg-[#0F172A] text-white relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto space-y-4">
          <span className="text-xs font-bold uppercase tracking-widest text-teal-400 bg-teal-500/10 px-3 py-1 rounded-full border border-teal-500/20 inline-block">
            MÓDULOS DE LA PLATAFORMA
          </span>
          <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight font-['Plus_Jakarta_Sans']">
            Todo lo que necesitas para administrar mejor
          </h2>
          <p className="text-base sm:text-lg text-slate-300 leading-relaxed">
            Una suite modular que cubre desde la seguridad perimetral y la recepción de visitantes hasta el balance financiero y la convivencia diaria.
          </p>
        </div>

        {/* Feature Cards Grid */}
        <div className="mt-14 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {MODULES.map((item) => {
            const Icon = item.icon;
            return (
              <div
                key={item.title}
                className="group p-6 rounded-2xl bg-slate-900/70 border border-slate-800 hover:border-emerald-500/40 hover:bg-slate-900 transition-all duration-200 hover:-translate-y-1 shadow-lg shadow-black/20 flex flex-col justify-between"
              >
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <div className="w-12 h-12 rounded-xl bg-[#0A1628] border border-slate-700/60 flex items-center justify-center text-emerald-400 group-hover:border-emerald-500/40 transition-colors">
                      <Icon className="w-6 h-6" />
                    </div>
                    <span className={`px-2.5 py-1 rounded-full text-xs font-semibold border ${item.badgeColor}`}>
                      {item.badge}
                    </span>
                  </div>

                  <h3 className="text-lg font-bold text-white group-hover:text-emerald-300 transition-colors">
                    {item.title}
                  </h3>

                  <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                    {item.desc}
                  </p>
                </div>

                <div className="mt-6 pt-4 border-t border-slate-800/80 space-y-2">
                  {item.features.map((feat) => (
                    <div key={feat} className="flex items-center gap-2 text-xs text-slate-300 font-medium">
                      <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 shrink-0" />
                      <span>{feat}</span>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
