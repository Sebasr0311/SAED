import {
  FileSpreadsheet,
  BookOpen,
  Receipt,
  PackageX,
  Car,
  MessageSquareOff,
  Building2,
  Users,
  ShieldCheck,
  CreditCard,
  CheckCircle2,
  Plus,
  Equal,
} from 'lucide-react';

const FRAGMENTED_ISSUES = [
  {
    icon: FileSpreadsheet,
    title: 'Información dispersa',
    desc: 'Bases de datos en hojas de cálculo desactualizadas entre el computador del administrador y la garita.',
  },
  {
    icon: BookOpen,
    title: 'Visitantes en papel',
    desc: 'Minutas físicas vulnerables a pérdida o deterioro que impiden cualquier auditoría histórica confiable.',
  },
  {
    icon: Receipt,
    title: 'Cartera fragmentada',
    desc: 'Comprobantes enviados por WhatsApp, desconfianza en saldos y conciliación bancaria manual tardía.',
  },
  {
    icon: PackageX,
    title: 'Paquetes sin custodia',
    desc: 'Entregas por confusión y paquetes retenidos en garita sin notificación oficial ni firma de entrega.',
  },
  {
    icon: Car,
    title: 'Parqueaderos a ciegas',
    desc: 'Vehículos sin control de permanencia, bahías copadas por residentes y disputas diarias en portería.',
  },
  {
    icon: MessageSquareOff,
    title: 'Comunicación informal',
    desc: 'Grupos de chat caóticos donde los comunicados de asamblea y cuotas se pierden entre reclamos.',
  },
];

export default function LandingProblemSolution() {
  return (
    <section
      id="soluciones"
      className="py-20 sm:py-28 lg:py-32 bg-[#0F172A] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Editorial Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16 sm:mb-20">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-amber-400 bg-amber-500/10 border border-amber-500/20">
            EL RETO OPERATIVO
          </span>

          <h2 className="text-3xl sm:text-5xl lg:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            La administración residencial no debería vivir en diez herramientas.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto pt-2">
            La mayoría de copropiedades dependen de canales dispersos que consumen tiempo, multiplican el error humano y deterioran la convivencia.
          </p>
        </div>

        {/* 6 Fragmented Issues Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 max-w-6xl mx-auto mb-20 sm:mb-28">
          {FRAGMENTED_ISSUES.map((item, idx) => {
            const Icon = item.icon;
            return (
              <div
                key={idx}
                className="p-6 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-slate-700 transition-colors space-y-3"
              >
                <div className="w-10 h-10 rounded-xl bg-rose-500/10 border border-rose-500/20 flex items-center justify-center text-rose-400">
                  <Icon className="w-5 h-5" />
                </div>
                <h3 className="text-base font-bold text-white font-['Plus_Jakarta_Sans']">
                  {item.title}
                </h3>
                <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                  {item.desc}
                </p>
              </div>
            );
          })}
        </div>

        {/* The Transition: SAED Conecta la Operación */}
        <div className="max-w-5xl mx-auto p-8 sm:p-12 rounded-3xl bg-gradient-to-br from-[#0A1628] via-[#0F2044] to-[#0A1628] border border-emerald-500/30 shadow-2xl text-center space-y-8">
          
          <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-bold uppercase tracking-wider">
            <CheckCircle2 className="w-3.5 h-3.5" />
            <span>LA SOLUCIÓN UNIFICADA</span>
          </div>

          <h3 className="text-2xl sm:text-4xl font-extrabold text-white tracking-tight font-['Plus_Jakarta_Sans']">
            SAED conecta la operación.
          </h3>

          <p className="text-sm sm:text-base text-slate-300 max-w-xl mx-auto leading-relaxed">
            Eliminamos el cruce de información conectando a todos los actores en una sola arquitectura digital sincronizada en tiempo real.
          </p>

          {/* The Unified Equation */}
          <div className="pt-4 flex flex-wrap items-center justify-center gap-3 sm:gap-4 text-xs sm:text-sm font-bold">
            <div className="px-4 py-3 rounded-xl bg-slate-900/90 border border-slate-700 text-slate-200 flex items-center gap-2">
              <Building2 className="w-4 h-4 text-blue-400" />
              <span>Administración</span>
            </div>

            <Plus className="w-4 h-4 text-emerald-400" />

            <div className="px-4 py-3 rounded-xl bg-slate-900/90 border border-slate-700 text-slate-200 flex items-center gap-2">
              <Users className="w-4 h-4 text-emerald-400" />
              <span>Residentes</span>
            </div>

            <Plus className="w-4 h-4 text-emerald-400" />

            <div className="px-4 py-3 rounded-xl bg-slate-900/90 border border-slate-700 text-slate-200 flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-teal-400" />
              <span>Portería</span>
            </div>

            <Plus className="w-4 h-4 text-emerald-400" />

            <div className="px-4 py-3 rounded-xl bg-slate-900/90 border border-slate-700 text-slate-200 flex items-center gap-2">
              <CreditCard className="w-4 h-4 text-amber-400" />
              <span>Finanzas</span>
            </div>

            <Equal className="w-4 h-4 text-emerald-400 hidden sm:block" />

            <div className="w-full sm:w-auto px-5 py-3 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 text-white font-bold tracking-wide shadow-lg shadow-emerald-950/40">
              SAED 2.0
            </div>
          </div>

        </div>

      </div>
    </section>
  );
}
