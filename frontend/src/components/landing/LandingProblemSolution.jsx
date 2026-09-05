import { XCircle, CheckCircle2, ArrowDown, Layers } from 'lucide-react';

const BEFORE_ITEMS = [
  {
    title: 'Procesos dispersos',
    desc: 'Minutas físicas en papel, llamadas que nadie contesta y registros propensos a pérdida o deterioro.',
  },
  {
    title: 'Información fragmentada',
    desc: 'Comprobantes enviados por chat, saldos desactualizados y desconfianza en la rendición de cuentas.',
  },
  {
    title: 'Control manual',
    desc: 'Vehículos sin control de permanencia y paquetería entregada sin constancia verificable en garita.',
  },
];

const AFTER_ITEMS = [
  {
    title: 'Más control operativo',
    desc: 'Validación previa de visitantes con código QR y bitácora digital inmutable con operador de turno.',
  },
  {
    title: 'Más trazabilidad financiera',
    desc: 'Recaudo en línea con pasarela Wompi (PSE y tarjetas), conciliación instantánea y paz y salvos automáticos.',
  },
  {
    title: 'Mejor experiencia de convivencia',
    desc: 'Acceso web inmediato sin descargas obligatorias para residentes, administración y guardias.',
  },
];

export default function LandingProblemSolution() {
  return (
    <section
      id="soluciones"
      className="py-24 sm:py-32 lg:py-36 bg-[#0A1628] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="max-w-3xl mx-auto text-center space-y-5">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-amber-400 bg-amber-500/10 border border-amber-500/20">
            EVOLUCIÓN OPERATIVA
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Del proceso manual al control digital integrado
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Comparamos la operación tradicional de copropiedades frente a la experiencia unificada que ofrece SAED 2.0.
          </p>
        </div>

        {/* Transition Architecture Flow: ANTES -> SAED -> DESPUÉS */}
        <div className="mt-16 sm:mt-24 max-w-5xl mx-auto space-y-8">
          {/* 1. ANTES */}
          <div className="p-7 sm:p-9 rounded-3xl bg-slate-900/60 border border-rose-500/20 shadow-xl space-y-6">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-rose-500/10 border border-rose-500/25 flex items-center justify-center text-rose-400">
                  <XCircle className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-lg sm:text-xl font-bold text-white">Método Tradicional</h3>
                  <p className="text-xs text-rose-400 font-medium">Gestión manual y canales fragmentados</p>
                </div>
              </div>
              <span className="px-3 py-1 rounded-full text-xs font-bold bg-rose-500/10 text-rose-400 border border-rose-500/20 tracking-wide">
                ANTES
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 sm:gap-6">
              {BEFORE_ITEMS.map((item) => (
                <div
                  key={item.title}
                  className="p-4 sm:p-5 rounded-2xl bg-slate-800/40 border border-slate-800/80 space-y-2"
                >
                  <h4 className="text-sm font-bold text-slate-200 flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-rose-400" />
                    {item.title}
                  </h4>
                  <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                    {item.desc}
                  </p>
                </div>
              ))}
            </div>
          </div>

          {/* 2. THE TRANSITION BRIDGE (SAED) */}
          <div className="flex flex-col items-center justify-center text-center py-4 space-y-3">
            <div className="w-10 h-10 rounded-full bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shadow-lg shadow-emerald-950/40 animate-bounce">
              <ArrowDown className="w-5 h-5" />
            </div>
            <div className="inline-flex items-center gap-2 px-5 py-2 rounded-full bg-gradient-to-r from-emerald-600/20 via-teal-600/20 to-emerald-600/20 border border-emerald-500/30 text-emerald-300 text-xs sm:text-sm font-bold tracking-wide">
              <Layers className="w-4 h-4 text-emerald-400" />
              <span>SAED · Una sola plataforma digital</span>
            </div>
          </div>

          {/* 3. DESPUÉS */}
          <div className="p-7 sm:p-9 rounded-3xl bg-gradient-to-b from-[#0F2044]/90 to-slate-900/90 border border-emerald-500/35 shadow-2xl shadow-emerald-950/20 space-y-6">
            <div className="flex items-center justify-between border-b border-slate-800 pb-4">
              <div className="flex items-center gap-3">
                <div className="w-9 h-9 rounded-xl bg-emerald-500/10 border border-emerald-500/25 flex items-center justify-center text-emerald-400">
                  <CheckCircle2 className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-lg sm:text-xl font-bold text-white">Ecosistema SAED 2.0</h3>
                  <p className="text-xs text-emerald-400 font-medium">Operación centralizada, segura y en tiempo real</p>
                </div>
              </div>
              <span className="px-3 py-1 rounded-full text-xs font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 tracking-wide">
                DESPUÉS
              </span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 sm:gap-6">
              {AFTER_ITEMS.map((item) => (
                <div
                  key={item.title}
                  className="p-4 sm:p-5 rounded-2xl bg-slate-800/60 border border-emerald-500/20 hover:border-emerald-500/40 transition-colors space-y-2"
                >
                  <h4 className="text-sm font-bold text-white flex items-center gap-2">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                    {item.title}
                  </h4>
                  <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                    {item.desc}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
