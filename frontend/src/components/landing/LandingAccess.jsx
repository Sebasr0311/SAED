import { QrCode, ShieldCheck, ArrowRight, Check } from 'lucide-react';

const ACCESS_FLOW = [
  { step: '01', title: 'Residente', desc: 'Emite la invitación desde el portal web en segundos' },
  { step: '02', title: 'Código QR', desc: 'Pase temporal cifrado con vigencia máxima parametrizable' },
  { step: '03', title: 'Portería', desc: 'El guardia escanea el código en pantalla sin tocar el móvil' },
  { step: '04', title: 'Validación', desc: 'El sistema confirma autenticidad y unidad destino' },
  { step: '05', title: 'Entrada registrada', desc: 'Asentamiento inmutable en la bitácora con operador y hora' },
];

export default function LandingAccess() {
  return (
    <section
      id="acceso"
      className="py-20 sm:py-28 lg:py-32 bg-[#0A1628] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16 sm:mb-20">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
            CONTROL DE ACCESO
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            El acceso empieza antes de llegar a la portería.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Cada visita sigue un flujo controlado, validado y trazable, eliminando las llamadas telefónicas molestas y los registros en papel.
          </p>
        </div>

        {/* 5-Step Linear Flow */}
        <div className="grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-5 gap-3 sm:gap-4 max-w-6xl mx-auto mb-16">
          {ACCESS_FLOW.map((f, idx) => (
            <div
              key={f.step}
              className="p-5 rounded-2xl bg-slate-900/80 border border-slate-800 flex flex-col justify-between space-y-3"
            >
              <div className="flex items-center justify-between">
                <span className="text-2xl font-black font-mono text-emerald-400">{f.step}</span>
                {idx < ACCESS_FLOW.length - 1 && (
                  <ArrowRight className="w-4 h-4 text-slate-600 hidden lg:block" />
                )}
              </div>
              <div>
                <h3 className="text-sm font-bold text-white font-['Plus_Jakarta_Sans']">{f.title}</h3>
                <p className="text-xs text-slate-400 mt-1 leading-relaxed">{f.desc}</p>
              </div>
            </div>
          ))}
        </div>

        {/* Big Product Visual Split Layout */}
        <div className="max-w-5xl mx-auto p-6 sm:p-10 rounded-3xl bg-slate-900/90 border border-slate-800 shadow-2xl">
          <div className="grid grid-cols-1 md:grid-cols-12 gap-8 items-center">
            
            {/* Left: Interactive Resident Pass View */}
            <div className="md:col-span-5 bg-slate-950 p-6 rounded-2xl border border-slate-800 space-y-4 text-center">
              <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 block border-b border-slate-800 pb-2">
                Pase de Visitante · Generado por Residente
              </span>
              
              <div className="w-40 h-40 mx-auto rounded-2xl bg-white p-3 flex items-center justify-center shadow-lg">
                <QrCode className="w-full h-full text-slate-950" />
              </div>

              <div className="space-y-1 text-xs">
                <p className="font-bold text-white text-sm">Laura Gómez</p>
                <p className="text-slate-400">Autorizado para: <strong className="text-emerald-400">Apto 302</strong></p>
                <span className="inline-block px-2.5 py-0.5 rounded-full text-[10px] font-mono bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 mt-1">
                  TOKEN: #QR-9942 · VÁLIDO
                </span>
              </div>
            </div>

            {/* Right: Guardhouse Console Verification */}
            <div className="md:col-span-7 space-y-5">
              <div className="space-y-2">
                <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-xs font-semibold">
                  <Check className="w-3.5 h-3.5" />
                  <span>Validación en Consola de Portería</span>
                </div>
                <h3 className="text-xl sm:text-2xl font-bold text-white font-['Plus_Jakarta_Sans']">
                  Confirmación instantánea en garita
                </h3>
                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                  Al leer el código, la pantalla de garita exhibe la fotografía de referencia, nombre completo, placa vehicular y la unidad habitacional que autoriza el acceso.
                </p>
              </div>

              <div className="space-y-2 text-xs">
                <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center justify-between">
                  <span className="text-slate-400">Hora de lectura:</span>
                  <span className="font-mono text-white">14:32:10 · Servidor Central</span>
                </div>
                <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center justify-between">
                  <span className="text-slate-400">Operador responsable:</span>
                  <span className="font-medium text-white">Carlos Mendoza (Portería 1)</span>
                </div>
                <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center justify-between">
                  <span className="text-slate-400">Bahía asignada:</span>
                  <span className="font-mono font-bold text-emerald-400">V-03 (Visitante)</span>
                </div>
              </div>

              <div className="p-3.5 rounded-xl bg-emerald-950/30 border border-emerald-500/30 text-emerald-300 text-xs flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Asentado automáticamente en bitácora inmutable de auditoría.</span>
              </div>
            </div>

          </div>
        </div>

      </div>
    </section>
  );
}
