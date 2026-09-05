import { XCircle, CheckCircle2, FileText, AlertTriangle, MessageSquareOff, ShieldCheck, TrendingUp, BellRing } from 'lucide-react';

const PAIN_POINTS = [
  {
    icon: FileText,
    title: 'Minutas y planillas de papel',
    desc: 'Registros manuales propensos a errores, letra ilegible, pérdida física y cero respaldo ante reclamos.',
  },
  {
    icon: AlertTriangle,
    title: 'Descontrol de visitantes y vehículos',
    desc: 'Visitas sin autorización previa del residente y parqueaderos ocupados sin control de placas ni tiempos.',
  },
  {
    icon: TrendingUp,
    title: 'Cartera dispersa y morosidad',
    desc: 'Conciliación bancaria manual, comprobantes perdidos por chat y cobros tardíos que afectan la liquidez.',
  },
  {
    icon: MessageSquareOff,
    title: 'Paquetes extraviados y reclamos',
    desc: 'Cajas acumuladas en garita sin notificación oportuna ni firma de retiro, generando fricción con los residentes.',
  },
];

const SAED_SOLUTIONS = [
  {
    icon: ShieldCheck,
    title: 'Bitácora digital inviolable',
    desc: 'Validación en garita mediante código QR único, con registro inmutable de hora, portero y placa.',
  },
  {
    icon: CheckCircle2,
    title: 'Parqueaderos y visitas coordinadas',
    desc: 'Asignación dinámica de puestos y liberación automática al registrar la salida del vehículo.',
  },
  {
    icon: TrendingUp,
    title: 'Cartera en tiempo real con Wompi',
    desc: 'Estados de cuenta automáticos por unidad y pagos en línea con acreditación y recibo instantáneo.',
  },
  {
    icon: BellRing,
    title: 'Custodia con PIN de seguridad',
    desc: 'Recepción clasificada, notificación automática al buzón del residente y retiro seguro con código de un solo uso.',
  },
];

export default function LandingProblemSolution() {
  return (
    <section id="solucion" className="py-20 bg-[#0A1628] text-white relative border-t border-slate-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto space-y-4">
          <span className="text-xs font-bold uppercase tracking-widest text-amber-400 bg-amber-500/10 px-3 py-1 rounded-full border border-amber-500/20 inline-block">
            TRANSFORMACIÓN DIGITAL
          </span>
          <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight font-['Plus_Jakarta_Sans']">
            Del desorden administrativo a la eficiencia total
          </h2>
          <p className="text-base sm:text-lg text-slate-300 leading-relaxed">
            Comparamos el método tradicional de gestión residencial frente a la automatización estructurada que brinda SAED 2.0.
          </p>
        </div>

        {/* Comparison Grid */}
        <div className="mt-14 grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Card: EL PROBLEMA (ANTES) */}
          <div className="p-6 sm:p-8 rounded-2xl bg-slate-900/90 border border-rose-500/30 shadow-xl shadow-rose-950/10 space-y-6">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400">
                  <XCircle className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-lg font-bold text-white">Administración Tradicional</h3>
                  <p className="text-xs text-rose-400 font-medium">Procesos manuales, fragmentados y vulnerables</p>
                </div>
              </div>
              <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
                ANTES
              </span>
            </div>

            <div className="space-y-4">
              {PAIN_POINTS.map((item) => {
                const Icon = item.icon;
                return (
                  <div key={item.title} className="flex items-start gap-3.5 p-3.5 rounded-xl bg-slate-800/40 border border-slate-800/60">
                    <Icon className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                    <div className="space-y-0.5">
                      <h4 className="text-sm font-semibold text-slate-200">{item.title}</h4>
                      <p className="text-xs text-slate-400 leading-relaxed">{item.desc}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Card: LA SOLUCIÓN (CON SAED) */}
          <div className="p-6 sm:p-8 rounded-2xl bg-gradient-to-b from-[#0F2044]/90 to-slate-900/90 border border-emerald-500/40 shadow-xl shadow-emerald-950/20 space-y-6 relative overflow-hidden">
            <div className="absolute -top-10 -right-10 w-40 h-40 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

            <div className="flex items-center justify-between border-b border-slate-800 pb-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                  <CheckCircle2 className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-lg font-bold text-white">Ecosistema SAED 2.0</h3>
                  <p className="text-xs text-emerald-400 font-medium">Plataforma centralizada y automatizada</p>
                </div>
              </div>
              <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                AHORA
              </span>
            </div>

            <div className="space-y-4">
              {SAED_SOLUTIONS.map((item) => {
                const Icon = item.icon;
                return (
                  <div key={item.title} className="flex items-start gap-3.5 p-3.5 rounded-xl bg-emerald-950/20 border border-emerald-500/20 hover:border-emerald-500/40 transition-colors">
                    <Icon className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                    <div className="space-y-0.5">
                      <h4 className="text-sm font-semibold text-white">{item.title}</h4>
                      <p className="text-xs text-slate-300 leading-relaxed">{item.desc}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
