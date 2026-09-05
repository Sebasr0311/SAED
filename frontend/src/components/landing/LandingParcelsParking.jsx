import { Package, Car, KeyRound, ArrowRight } from 'lucide-react';

const PARCEL_FLOW = [
  { step: '1', label: 'Paquete recibido', desc: 'Garita registra transportadora y unidad' },
  { step: '2', label: 'PIN generado', desc: 'Código único asignado al paquete' },
  { step: '3', label: 'Residente notificado', desc: 'Aviso directo en casillero web' },
  { step: '4', label: 'Entrega confirmada', desc: 'Validación de PIN al retirar' },
];

const PARKING_FLOW = [
  { step: '1', label: 'Vehículo visitante', desc: 'Identificación de placa en garita' },
  { step: '2', label: 'Cupo asignado', desc: 'Asignación dinámica de puesto libre' },
  { step: '3', label: 'Vehículo dentro', desc: 'Permanencia activa en bitácora' },
  { step: '4', label: 'Salida registrada', desc: 'Liberación automática del cupo' },
];

export default function LandingParcelsParking() {
  return (
    <section id="operacion" className="py-20 bg-[#0F172A] text-white relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto space-y-4">
          <span className="text-xs font-bold uppercase tracking-widest text-teal-400 bg-teal-500/10 px-3 py-1 rounded-full border border-teal-500/20 inline-block">
            LOGÍSTICA & OPERACIÓN DIARIA
          </span>
          <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight font-['Plus_Jakarta_Sans']">
            Paquetería con PIN y Parqueaderos en Tiempo Real
          </h2>
          <p className="text-base sm:text-lg text-slate-300 leading-relaxed">
            Flujos operativos claros para resolver la acumulación de encomiendas y el descontrol vehicular en portería.
          </p>
        </div>

        {/* Dual Cards Grid */}
        <div className="mt-14 grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Card 1: Paquetería */}
          <div className="p-6 sm:p-8 rounded-2xl bg-slate-900/80 border border-slate-800 space-y-6 flex flex-col justify-between">
            <div>
              <div className="flex items-center gap-3 border-b border-slate-800 pb-4">
                <div className="w-10 h-10 rounded-xl bg-teal-500/10 border border-teal-500/20 flex items-center justify-center text-teal-400">
                  <Package className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-lg font-bold text-white">Custodia de Paquetería con PIN</h3>
                  <p className="text-xs text-slate-400">Entrega validada exclusivamente con clave de un solo uso</p>
                </div>
              </div>

              {/* Visual 4-Step Flow */}
              <div className="mt-6 space-y-3">
                <p className="text-xs font-bold uppercase tracking-wider text-teal-400">
                  Flujo de Custodia:
                </p>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  {PARCEL_FLOW.map((f, i) => (
                    <div
                      key={f.step}
                      className="p-3 rounded-xl bg-slate-800/60 border border-slate-700/60 flex flex-col justify-between relative"
                    >
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="w-5 h-5 rounded-full bg-teal-500/20 text-teal-400 text-xs font-bold flex items-center justify-center">
                          {f.step}
                        </span>
                        {i < 3 && <ArrowRight className="w-3 h-3 text-slate-500 hidden sm:block" />}
                      </div>
                      <div>
                        <h4 className="font-semibold text-white text-xs leading-tight">{f.label}</h4>
                        <p className="text-[11px] text-slate-400 mt-1 leading-snug">{f.desc}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Interactive Demo Highlight */}
              <div className="mt-6 p-4 rounded-xl bg-slate-800/40 border border-slate-700/60 space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-slate-300">Ejemplo en garita: Apto 101</span>
                  <span className="font-mono text-emerald-400 font-bold bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-500/30">
                    PIN: 482910
                  </span>
                </div>
                <p className="text-xs text-slate-400">
                  El portero entrega la encomienda únicamente cuando el residente dicta o valida su PIN en pantalla.
                </p>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-teal-950/30 border border-teal-500/20 text-xs text-teal-300 flex items-center gap-2">
              <KeyRound className="w-4 h-4 text-teal-400 shrink-0" />
              <span>Custodia transparente y trazabilidad completa de entrega.</span>
            </div>
          </div>

          {/* Card 2: Parqueaderos */}
          <div className="p-6 sm:p-8 rounded-2xl bg-slate-900/80 border border-slate-800 space-y-6 flex flex-col justify-between">
            <div>
              <div className="flex items-center gap-3 border-b border-slate-800 pb-4">
                <div className="w-10 h-10 rounded-xl bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400">
                  <Car className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-lg font-bold text-white">Control Dinámico de Parqueaderos</h3>
                  <p className="text-xs text-slate-400">Asignación de puestos y seguimiento de placas vehiculares</p>
                </div>
              </div>

              {/* Visual 4-Step Flow */}
              <div className="mt-6 space-y-3">
                <p className="text-xs font-bold uppercase tracking-wider text-blue-400">
                  Flujo de Parqueadero:
                </p>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  {PARKING_FLOW.map((f, i) => (
                    <div
                      key={f.step}
                      className="p-3 rounded-xl bg-slate-800/60 border border-slate-700/60 flex flex-col justify-between relative"
                    >
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="w-5 h-5 rounded-full bg-blue-500/20 text-blue-400 text-xs font-bold flex items-center justify-center">
                          {f.step}
                        </span>
                        {i < 3 && <ArrowRight className="w-3 h-3 text-slate-500 hidden sm:block" />}
                      </div>
                      <div>
                        <h4 className="font-semibold text-white text-xs leading-tight">{f.label}</h4>
                        <p className="text-[11px] text-slate-400 mt-1 leading-snug">{f.desc}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Slot Demo Status */}
              <div className="mt-6 space-y-2 text-xs">
                <div className="p-3 rounded-xl bg-slate-800/60 border border-slate-700/60 flex items-center justify-between">
                  <div>
                    <span className="font-bold text-white block">Puesto V-01 (Visitante)</span>
                    <span className="text-slate-400">Placa: <span className="font-mono text-amber-300 font-bold">DEM-123</span></span>
                  </div>
                  <span className="px-2 py-0.5 rounded text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
                    Ocupado (Dentro)
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-slate-800/60 border border-slate-700/60 flex items-center justify-between">
                  <div>
                    <span className="font-bold text-white block">Puesto V-02 (Visitante)</span>
                    <span className="text-slate-400">Disponible para nuevos arribos</span>
                  </div>
                  <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    Disponible
                  </span>
                </div>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-blue-950/30 border border-blue-500/20 text-xs text-blue-300 flex items-center gap-2">
              <Car className="w-4 h-4 text-blue-400 shrink-0" />
              <span>Liberación automática del cupo al marcar la salida en la consola.</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
