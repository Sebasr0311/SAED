import { useState } from 'react';
import { Car, Clock, CheckCircle2 } from 'lucide-react';

const INITIAL_BAYS = [
  { id: 'V-01', type: 'Automóvil', status: 'OCUPADO', plate: 'DEM-123', unit: 'Apto 204', duration: '1h 15m' },
  { id: 'V-02', type: 'Automóvil', status: 'DISPONIBLE', plate: null, unit: null, duration: null },
  { id: 'V-03', type: 'Automóvil', status: 'OCUPADO', plate: 'KLR-901', unit: 'Apto 102', duration: '45m' },
  { id: 'V-04', type: 'Automóvil', status: 'DISPONIBLE', plate: null, unit: null, duration: null },
  { id: 'MV-01', type: 'Motocicleta', status: 'DISPONIBLE', plate: null, unit: null, duration: null },
  { id: 'MV-02', type: 'Motocicleta', status: 'OCUPADO', plate: 'M-440', unit: 'Apto 305', duration: '2h 10m' },
];

export default function LandingParking() {
  const [filter, setFilter] = useState('ALL');

  const filteredBays = INITIAL_BAYS.filter((bay) => {
    if (filter === 'OCCUPIED') return bay.status === 'OCUPADO';
    if (filter === 'AVAILABLE') return bay.status === 'DISPONIBLE';
    return true;
  });

  return (
    <section
      id="parqueaderos"
      className="py-20 sm:py-28 lg:py-32 bg-[#0A1628] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16 sm:mb-20">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
            GESTIÓN VEHICULAR
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Visibilidad sobre cada puesto.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            El sistema asigna la bahía al registrar el vehículo del visitante y la libera automáticamente al asentar la salida en garita, eliminando disputas por cupos.
          </p>
        </div>

        {/* Real Product Simulation: Interactive Bays Console */}
        <div className="max-w-5xl mx-auto p-6 sm:p-10 rounded-3xl bg-slate-900/90 border border-slate-800 shadow-2xl space-y-8">
          
          {/* Header & Filter Controls */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                <Car className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base sm:text-lg font-bold text-white font-['Plus_Jakarta_Sans']">
                  Mapa de Bahías de Visitantes en Vivo
                </h3>
                <p className="text-xs text-slate-400">Consola de monitoreo de garita</p>
              </div>
            </div>

            {/* Filter Pills */}
            <div className="flex items-center gap-1.5 bg-slate-950 p-1 rounded-xl border border-slate-800 self-start sm:self-auto">
              <button
                type="button"
                onClick={() => setFilter('ALL')}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors min-h-[32px] ${
                  filter === 'ALL' ? 'bg-emerald-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
                }`}
              >
                Todas (6)
              </button>
              <button
                type="button"
                onClick={() => setFilter('AVAILABLE')}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors min-h-[32px] ${
                  filter === 'AVAILABLE' ? 'bg-emerald-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
                }`}
              >
                Libres (3)
              </button>
              <button
                type="button"
                onClick={() => setFilter('OCCUPIED')}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors min-h-[32px] ${
                  filter === 'OCCUPIED' ? 'bg-emerald-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
                }`}
              >
                Ocupadas (3)
              </button>
            </div>
          </div>

          {/* Bays Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
            {filteredBays.map((bay) => {
              const isOccupied = bay.status === 'OCUPADO';
              return (
                <div
                  key={bay.id}
                  className={`p-5 rounded-2xl border transition-all space-y-3 ${
                    isOccupied
                      ? 'bg-rose-950/20 border-rose-500/30 text-rose-300'
                      : 'bg-emerald-950/20 border-emerald-500/30 text-emerald-300'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-lg font-black text-white">{bay.id}</span>
                    <span
                      className={`text-[10px] font-bold px-2.5 py-0.5 rounded-full uppercase tracking-wider ${
                        isOccupied
                          ? 'bg-rose-500/20 text-rose-300 border border-rose-500/30'
                          : 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                      }`}
                    >
                      {bay.status}
                    </span>
                  </div>

                  <div className="space-y-1 text-xs">
                    <div className="text-slate-400 text-[11px]">Tipo: {bay.type}</div>
                    {isOccupied ? (
                      <>
                        <div className="font-mono font-bold text-white text-sm">Placa: {bay.plate}</div>
                        <div className="text-slate-300">Destino: <strong className="text-white">{bay.unit}</strong></div>
                        <div className="flex items-center gap-1 text-[11px] text-slate-400 pt-1">
                          <Clock className="w-3 h-3 text-amber-400" />
                          <span>Permanencia: {bay.duration}</span>
                        </div>
                      </>
                    ) : (
                      <div className="py-3 text-slate-400 text-xs">
                        Bahía disponible para asignación inmediata en garita
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {/* Bottom Operational Note */}
          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-400">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
              <span>Control de estancia máxima para evitar invasión de puestos por residentes.</span>
            </div>
            <span className="font-mono text-emerald-400 font-semibold">Trazabilidad en tiempo real</span>
          </div>

        </div>

      </div>
    </section>
  );
}
