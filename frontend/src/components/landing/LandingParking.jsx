import { useState, useRef } from 'react';
import {
  Car,
  Clock,
  CheckCircle2,
  Sparkles,
  Plus,
  ArrowRight,
  LogOut,
  AlertCircle,
} from 'lucide-react';
import { animate } from 'animejs';

const INITIAL_BAYS = [
  { id: 'V-01', type: 'Automóvil', status: 'OCUPADO', plate: 'DEM-123', unit: 'Apto 204', duration: '1h 15m' },
  { id: 'V-02', type: 'Automóvil', status: 'DISPONIBLE', plate: null, unit: null, duration: null },
  { id: 'V-03', type: 'Automóvil', status: 'OCUPADO', plate: 'KLR-901', unit: 'Apto 102', duration: '45m' },
  { id: 'V-04', type: 'Automóvil', status: 'DISPONIBLE', plate: null, unit: null, duration: null },
  { id: 'MV-01', type: 'Motocicleta', status: 'DISPONIBLE', plate: null, unit: null, duration: null },
  { id: 'MV-02', type: 'Motocicleta', status: 'OCUPADO', plate: 'M-440', unit: 'Apto 305', duration: '2h 10m' },
];

export default function LandingParking() {
  const [bays, setBays] = useState(INITIAL_BAYS);
  const [filter, setFilter] = useState('ALL'); // 'ALL' | 'AVAILABLE' | 'OCCUPIED'
  const gridRef = useRef(null);

  const toggleBay = (bayId) => {
    setBays((prev) =>
      prev.map((bay) => {
        if (bay.id === bayId) {
          const isNowOccupied = bay.status === 'DISPONIBLE';
          return {
            ...bay,
            status: isNowOccupied ? 'OCUPADO' : 'DISPONIBLE',
            plate: isNowOccupied ? (bay.type === 'Motocicleta' ? 'M-882' : 'XYZ-554') : null,
            unit: isNowOccupied ? 'Apto 105' : null,
            duration: isNowOccupied ? 'Justo ahora' : null,
          };
        }
        return bay;
      })
    );
  };

  const handleSimulateEntry = () => {
    // Find first available bay
    const freeBay = bays.find((b) => b.status === 'DISPONIBLE');
    if (freeBay) {
      toggleBay(freeBay.id);
    }
  };

  const handleSimulateExit = () => {
    // Find first occupied bay
    const occBay = bays.find((b) => b.status === 'OCUPADO');
    if (occBay) {
      toggleBay(occBay.id);
    }
  };

  const filteredBays = bays.filter((bay) => {
    if (filter === 'OCCUPIED') return bay.status === 'OCUPADO';
    if (filter === 'AVAILABLE') return bay.status === 'DISPONIBLE';
    return true;
  });

  const occupiedCount = bays.filter((b) => b.status === 'OCUPADO').length;
  const freeCount = bays.length - occupiedCount;
  const occupancyPercentage = Math.round((occupiedCount / bays.length) * 100);

  return (
    <section
      id="parqueaderos"
      className="py-20 sm:py-28 lg:py-32 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      <div className="absolute top-1/2 right-1/4 w-[600px] h-[350px] bg-sky-500/5 blur-[160px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-14 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20">
            <Sparkles className="w-3.5 h-3.5 text-sky-400" />
            CONTROL VEHICULAR Y BAHÍAS
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Visibilidad en tiempo real sobre cada puesto.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            El sistema asigna la bahía al registrar el vehículo del visitante y la libera automáticamente al asentar la salida en garita, eliminando disputas diarias por cupos.
          </p>
        </div>

        {/* Real Product Simulation: Interactive Bays Console */}
        <div className="max-w-5xl mx-auto p-6 sm:p-10 rounded-3xl bg-slate-900/80 border border-slate-800 shadow-2xl backdrop-blur-xl space-y-8">
          
          {/* Header & Filter Controls */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-400">
                <Car className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base sm:text-lg font-bold text-white font-['Plus_Jakarta_Sans']">
                  Mapa Interactivo de Bahías de Visitantes en Vivo
                </h3>
                <p className="text-xs text-slate-400">Haz clic en cualquier bahía o usa las acciones rápidas</p>
              </div>
            </div>

            {/* Filter Pills */}
            <div className="flex items-center gap-1.5 bg-slate-950 p-1 rounded-xl border border-slate-800 self-start sm:self-auto">
              <button
                type="button"
                onClick={() => setFilter('ALL')}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors min-h-[32px] ${
                  filter === 'ALL' ? 'bg-sky-400 text-[#061525] shadow-sm' : 'text-muted-foreground hover:text-white'
                }`}
              >
                Todas ({bays.length})
              </button>
              <button
                type="button"
                onClick={() => setFilter('AVAILABLE')}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors min-h-[32px] ${
                  filter === 'AVAILABLE' ? 'bg-sky-400 text-[#061525] shadow-sm' : 'text-muted-foreground hover:text-white'
                }`}
              >
                Libres ({freeCount})
              </button>
              <button
                type="button"
                onClick={() => setFilter('OCCUPIED')}
                className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors min-h-[32px] ${
                  filter === 'OCCUPIED' ? 'bg-sky-400 text-[#061525] shadow-sm' : 'text-muted-foreground hover:text-white'
                }`}
              >
                Ocupadas ({occupiedCount})
              </button>
            </div>
          </div>

          {/* Quick Simulation Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 p-3.5 rounded-2xl bg-slate-950 border border-slate-800 text-xs">
            <div className="flex items-center gap-4">
              <div>
                <span className="text-[10px] text-slate-500 uppercase font-bold tracking-wider block">Ocupación:</span>
                <span className="font-mono font-bold text-sky-400 text-sm">{occupancyPercentage}% ({occupiedCount}/6)</span>
              </div>
              <div className="h-6 w-px bg-slate-800 hidden sm:block" />
              <div className="hidden sm:block">
                <span className="text-[10px] text-slate-500 uppercase font-bold tracking-wider block">Disponibles:</span>
                <span className="font-mono font-bold text-emerald-400 text-sm">{freeCount} Puestos</span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleSimulateEntry}
                disabled={freeCount === 0}
                className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white font-bold text-xs flex items-center gap-1.5 transition-colors disabled:opacity-40"
              >
                <Plus className="w-3.5 h-3.5 text-sky-400" />
                <span>Simular Ingreso Vehicular</span>
              </button>
              <button
                type="button"
                onClick={handleSimulateExit}
                disabled={occupiedCount === 0}
                className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white font-bold text-xs flex items-center gap-1.5 transition-colors disabled:opacity-40"
              >
                <LogOut className="w-3.5 h-3.5 text-amber-400" />
                <span>Simular Salida Vehicular</span>
              </button>
            </div>
          </div>

          {/* Bays Grid */}
          <div ref={gridRef} className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
            {filteredBays.map((bay) => {
              const isOccupied = bay.status === 'OCUPADO';
              return (
                <button
                  key={bay.id}
                  type="button"
                  onClick={() => toggleBay(bay.id)}
                  className={`p-5 rounded-2xl border transition-all text-left space-y-3 transform active:scale-95 group ${
                    isOccupied
                      ? 'bg-rose-950/20 border-rose-500/30 text-rose-300 hover:border-rose-400/60'
                      : 'bg-emerald-950/20 border-emerald-500/30 text-emerald-300 hover:border-emerald-400/60'
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
                        Puesto libre · Clic para asignar ingreso
                      </div>
                    )}
                  </div>
                </button>
              );
            })}
          </div>

          {/* Bottom Operational Note */}
          <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-400">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
              <span>Control de estancia máxima para evitar invasión de bahías de visitantes.</span>
            </div>
            <span className="font-mono text-sky-400 font-semibold">Trazabilidad en tiempo real</span>
          </div>

        </div>

      </div>
    </section>
  );
}
