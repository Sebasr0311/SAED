import { Package, Car, KeyRound, ArrowRight } from 'lucide-react';

const PARCEL_STEPS = [
  { step: '1', title: 'Paquete recibido', desc: 'Garita registra empresa y unidad de destino' },
  { step: '2', title: 'PIN generado', desc: 'Clave única y cifrada asignada al paquete' },
  { step: '3', title: 'Residente notificado', desc: 'Alerta instantánea en el casillero web' },
  { step: '4', title: 'Entrega confirmada', desc: 'Validación obligatoria de PIN para el retiro' },
];

const PARKING_STEPS = [
  { step: '1', title: 'Vehículo visitante', desc: 'Identificación y registro de placa en garita' },
  { step: '2', title: 'Cupo asignado', desc: 'Asignación inmediata de espacio disponible' },
  { step: '3', title: 'Vehículo dentro', desc: 'Monitoreo de permanencia en bitácora' },
  { step: '4', title: 'Salida registrada', desc: 'Liberación automática del cupo en el sistema' },
];

export default function LandingParcelsParking() {
  return (
    <section
      id="operacion"
      className="py-24 sm:py-32 lg:py-36 bg-[#0F172A] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Editorial Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-5">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-teal-400 bg-teal-500/10 border border-teal-500/20">
            OPERACIÓN FÍSICA Y LOGÍSTICA
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Las operaciones físicas también forman parte del sistema.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            La custodia de correspondencia y el control vehicular dejan de ser un punto ciego gracias a flujos estructurados de principio a fin.
          </p>
        </div>

        {/* Two Grand Architectural Blocks */}
        <div className="mt-16 sm:mt-24 space-y-12 max-w-5xl mx-auto">
          {/* BLOCK 1: PAQUETERÍA */}
          <div className="p-7 sm:p-10 lg:p-12 rounded-3xl bg-slate-900/80 border border-teal-500/30 shadow-2xl space-y-8">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-6">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-2xl bg-teal-500/10 border border-teal-500/25 flex items-center justify-center text-teal-400 shrink-0">
                  <Package className="w-6 h-6" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-2xl font-bold text-white tracking-tight">Custodia de Paquetería</h3>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-teal-500/20 text-teal-300 font-semibold">
                      PIN Único
                    </span>
                  </div>
                  <p className="text-xs sm:text-sm text-slate-400 mt-0.5">
                    Entrega autorizada exclusivamente cuando el habitante valida su clave de un solo uso
                  </p>
                </div>
              </div>
            </div>

            {/* Step Progression */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
              {PARCEL_STEPS.map((s, idx) => (
                <div
                  key={s.step}
                  className="p-4 rounded-xl bg-slate-800/50 border border-slate-700/60 flex flex-col justify-between"
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="w-6 h-6 rounded-full bg-teal-500/20 text-teal-300 text-xs font-bold font-mono flex items-center justify-center">
                      {s.step}
                    </span>
                    {idx < 3 && <ArrowRight className="w-3.5 h-3.5 text-slate-600 hidden lg:block" />}
                  </div>
                  <div>
                    <h4 className="text-xs font-bold text-white">{s.title}</h4>
                    <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">{s.desc}</p>
                  </div>
                </div>
              ))}
            </div>

            {/* Real Product Simulation Card */}
            <div className="p-5 sm:p-6 rounded-2xl bg-slate-950/70 border border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-6">
              <div className="space-y-1.5 text-xs">
                <span className="text-[11px] font-mono text-teal-400 uppercase tracking-wider block">
                  Registro Activo en Consola de Garita
                </span>
                <p className="text-sm font-bold text-white">Guía Servientrega AMZ-9988 · Destinatario: Apto 101 (Carlos Martinez)</p>
                <p className="text-slate-400">Notificado al casillero digital del residente · Esperando retiro</p>
              </div>

              <div className="flex items-center gap-3 shrink-0 bg-slate-900 px-4 py-2.5 rounded-xl border border-teal-500/30">
                <KeyRound className="w-5 h-5 text-teal-400" />
                <div>
                  <span className="text-[10px] text-slate-400 uppercase tracking-wider block">PIN de Retiro</span>
                  <span className="font-mono text-sm font-bold text-teal-300 tracking-widest">482910</span>
                </div>
              </div>
            </div>
          </div>

          {/* BLOCK 2: PARQUEADEROS */}
          <div className="p-7 sm:p-10 lg:p-12 rounded-3xl bg-slate-900/80 border border-blue-500/30 shadow-2xl space-y-8">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-6">
              <div className="flex items-center gap-4">
                <div className="w-12 h-12 rounded-2xl bg-blue-500/10 border border-blue-500/25 flex items-center justify-center text-blue-400 shrink-0">
                  <Car className="w-6 h-6" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-2xl font-bold text-white tracking-tight">Parqueaderos de Visitantes</h3>
                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-blue-500/20 text-blue-300 font-semibold">
                      En Tiempo Real
                    </span>
                  </div>
                  <p className="text-xs sm:text-sm text-slate-400 mt-0.5">
                    Asignación inteligente de puestos libres y liberación automática al registrar la salida
                  </p>
                </div>
              </div>
            </div>

            {/* Step Progression */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
              {PARKING_STEPS.map((s, idx) => (
                <div
                  key={s.step}
                  className="p-4 rounded-xl bg-slate-800/50 border border-slate-700/60 flex flex-col justify-between"
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="w-6 h-6 rounded-full bg-blue-500/20 text-blue-300 text-xs font-bold font-mono flex items-center justify-center">
                      {s.step}
                    </span>
                    {idx < 3 && <ArrowRight className="w-3.5 h-3.5 text-slate-600 hidden lg:block" />}
                  </div>
                  <div>
                    <h4 className="text-xs font-bold text-white">{s.title}</h4>
                    <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">{s.desc}</p>
                  </div>
                </div>
              ))}
            </div>

            {/* Real Product Simulation Slots */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="p-4 sm:p-5 rounded-2xl bg-slate-950/70 border border-slate-800 flex items-center justify-between">
                <div className="space-y-1 text-xs">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
                    <span className="font-bold text-white text-sm">Puesto V-01 (Visitantes)</span>
                  </div>
                  <p className="text-slate-400">Placa asociada: <span className="font-mono text-amber-300 font-bold">DEM-123</span></p>
                  <p className="text-[11px] text-slate-500">Unidad anfitriona: Apartamento 101</p>
                </div>
                <span className="px-2.5 py-1 rounded text-xs font-bold bg-rose-500/15 text-rose-400 border border-rose-500/25">
                  OCUPADO
                </span>
              </div>

              <div className="p-4 sm:p-5 rounded-2xl bg-slate-950/70 border border-slate-800 flex items-center justify-between">
                <div className="space-y-1 text-xs">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
                    <span className="font-bold text-white text-sm">Puesto V-02 (Visitantes)</span>
                  </div>
                  <p className="text-slate-400">Disponible para nuevos vehículos</p>
                  <p className="text-[11px] text-emerald-400 font-medium">Asignación automática por garita</p>
                </div>
                <span className="px-2.5 py-1 rounded text-xs font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/25">
                  DISPONIBLE
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
