import { useState, useRef, useEffect } from 'react';
import {
  QrCode,
  ShieldCheck,
  ArrowRight,
  Check,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Sparkles,
  RefreshCw,
  Camera,
  Car,
  Clock,
  Key,
} from 'lucide-react';
import { animate } from 'animejs';

const ACCESS_FLOW = [
  { step: '01', title: 'Residente Emite', desc: 'Genera el pase desde el portal web indicando datos y vigencia en horas.' },
  { step: '02', title: 'Código QR Dinámico', desc: 'Pase criptográfico firmado que no puede ser falsificado ni alterado.' },
  { step: '03', title: 'Lectura en Pantalla', desc: 'El guardia escanea el pase en consola web sin tocar el móvil del visitante.' },
  { step: '04', title: 'Validación en Motor', desc: 'Oracle verifica vigencia, estado de consumo y unidad habitacional destino.' },
  { step: '05', title: 'Bitácora Inmutable', desc: 'Registro permanente con fotografía, operador, timestamp y bahía vehicular.' },
];

const SCAN_SCENARIOS = [
  {
    id: 'valid',
    label: '1. Pase Válido (Autorizado)',
    visitor: 'Laura Gómez',
    cc: 'C.C. 1.098.441.***',
    unit: 'Apto 302 · Torre 1',
    host: 'Familia Ramírez (Propietario)',
    token: '#QR-9942',
    vehicle: 'Automóvil · ABC-123',
    bay: 'V-03 (Visitante)',
    status: 'VÁLIDO',
    statusCode: '200 OK',
    statusClass: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30',
    detail: 'Acceso autorizado. El visitante cuenta con 4 horas de vigencia.',
    barrierOpen: true,
  },
  {
    id: 'expired',
    label: '2. Pase Expirado (Caducado)',
    visitor: 'Camilo Vargas (Técnico Enel)',
    cc: 'C.C. 79.882.104',
    unit: 'Apto 104 · Torre 1',
    host: 'Sofía Morales (Arrendataria)',
    token: '#QR-4091',
    vehicle: 'Furgón · TTT-555',
    bay: 'Sin asignar',
    status: 'EXPIRADO',
    statusCode: '400 CADUCADO',
    statusClass: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
    detail: 'Vigencia terminada a las 11:30 AM. Requiere emisión de nuevo pase por residente.',
    barrierOpen: false,
  },
  {
    id: 'consumed',
    label: '3. Pase Ya Utilizado (Anti-Clonación)',
    visitor: 'Andrea Ruiz (Invitada)',
    cc: 'C.C. 52.419.002',
    unit: 'Apto 205 · Torre 2',
    host: 'Carlos Herrera (Propietario)',
    token: '#QR-1102',
    vehicle: 'Peatonal',
    bay: 'N/A',
    status: 'YA CONSUMIDO',
    statusCode: '409 CONFLICTO',
    statusClass: 'bg-rose-500/10 text-rose-400 border-rose-500/30',
    detail: 'Este código QR de un solo uso ya fue consumido hoy a las 09:12 AM en Garita Norte.',
    barrierOpen: false,
  },
];

export default function LandingAccess() {
  const [activeScenarioIdx, setActiveScenarioIdx] = useState(0);
  const [isScanning, setIsScanning] = useState(false);
  const laserBeamRef = useRef(null);
  const consoleHUDRef = useRef(null);

  const scenario = SCAN_SCENARIOS[activeScenarioIdx];

  // Animate laser beam and console update on scan trigger
  const triggerScan = (idx) => {
    setActiveScenarioIdx(idx);
    setIsScanning(true);

    if (laserBeamRef.current) {
      animate(laserBeamRef.current, {
        top: ['5%', '90%', '5%'],
        opacity: [0.8, 1, 0.8],
        duration: 700,
        ease: 'easeInOutQuad',
        onComplete: () => {
          setIsScanning(false);
        },
      });
    } else {
      setTimeout(() => setIsScanning(false), 600);
    }

    if (consoleHUDRef.current) {
      animate(consoleHUDRef.current, {
        opacity: [0.3, 1],
        translateY: [10, 0],
        duration: 400,
        ease: 'outExpo',
      });
    }
  };

  return (
    <section
      id="acceso"
      className="py-20 sm:py-28 lg:py-32 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      <div className="absolute top-1/3 left-1/4 w-[600px] h-[340px] bg-sky-500/5 blur-[160px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-14 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20">
            <Sparkles className="w-3.5 h-3.5 text-sky-400" />
            CONTROL DE ACCESO Y GARITA
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            El acceso empieza antes de llegar a la portería.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Cada visita sigue un flujo controlado, validado y trazable, eliminando las llamadas telefónicas molestas y las minutas físicas en papel.
          </p>
        </div>

        {/* 5-Step Process Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-5 gap-3.5 max-w-6xl mx-auto mb-16">
          {ACCESS_FLOW.map((f, idx) => (
            <div
              key={f.step}
              className="p-5 rounded-2xl bg-slate-900/70 border border-slate-800/90 hover:border-sky-500/30 transition-all flex flex-col justify-between space-y-3 group"
            >
              <div className="flex items-center justify-between">
                <span className="text-2xl font-black font-mono text-sky-400 group-hover:scale-110 transition-transform">
                  {f.step}
                </span>
                {idx < ACCESS_FLOW.length - 1 && (
                  <ArrowRight className="w-4 h-4 text-slate-700 hidden lg:block" />
                )}
              </div>
              <div>
                <h3 className="text-sm font-bold text-white font-['Plus_Jakarta_Sans']">{f.title}</h3>
                <p className="text-xs text-slate-400 mt-1 leading-relaxed">{f.desc}</p>
              </div>
            </div>
          ))}
        </div>

        {/* ========================================================================= */}
        {/* Interactive QR Scanner Simulator Layout */}
        {/* ========================================================================= */}
        <div className="max-w-5xl mx-auto p-6 sm:p-10 rounded-3xl bg-slate-900/80 border border-slate-800 shadow-2xl backdrop-blur-xl space-y-6">
          
          {/* Header Controls: Simulation Scenario Switcher */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
            <div>
              <h3 className="text-base sm:text-lg font-bold text-white font-['Plus_Jakarta_Sans'] flex items-center gap-2">
                <Camera className="w-5 h-5 text-sky-400" />
                <span>Simulador de Escaneo Óptico en Garita</span>
              </h3>
              <p className="text-xs text-slate-400">Prueba los 3 escenarios de validación instantánea</p>
            </div>

            <div className="flex flex-wrap gap-2">
              {SCAN_SCENARIOS.map((sc, idx) => (
                <button
                  key={sc.id}
                  type="button"
                  onClick={() => triggerScan(idx)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-all ${
                    activeScenarioIdx === idx
                      ? 'bg-sky-400 text-[#061525] shadow-md shadow-sky-400/25'
                      : 'bg-slate-950 text-slate-300 border border-slate-800 hover:border-slate-700 hover:text-white'
                  }`}
                >
                  {sc.label}
                </button>
              ))}
            </div>
          </div>

          {/* Interactive Split Scanner View */}
          <div className="grid grid-cols-1 md:grid-cols-12 gap-8 items-center pt-2">
            
            {/* Left: Resident Mobile Pass with Animated Laser Scanline */}
            <div className="md:col-span-5 bg-slate-950 p-6 rounded-2xl border border-slate-800 space-y-4 text-center relative overflow-hidden shadow-inner">
              <div className="flex items-center justify-between border-b border-slate-800 pb-2 text-[11px] text-slate-400">
                <span className="font-bold uppercase tracking-wider">Pase de Visitante</span>
                <span className="font-mono text-sky-400">{scenario.token}</span>
              </div>

              {/* QR Container with Laser Scanline */}
              <div className="w-44 h-44 mx-auto rounded-2xl bg-white p-3.5 flex items-center justify-center shadow-xl relative overflow-hidden">
                <QrCode className="w-full h-full text-slate-950" />
                
                {/* Anime.js Laser Beam */}
                <div
                  ref={laserBeamRef}
                  className="absolute inset-x-0 h-1 bg-gradient-to-r from-transparent via-cyan-400 to-transparent shadow-[0_0_15px_#38bdf8] pointer-events-none z-20"
                  style={{ top: '20%' }}
                />
              </div>

              <div className="space-y-1 text-xs">
                <p className="font-bold text-white text-sm">{scenario.visitor}</p>
                <p className="text-slate-400">Destino: <strong className="text-sky-300">{scenario.unit}</strong></p>
                <span className={`inline-block px-3 py-0.5 rounded-full text-[10px] font-mono font-bold border mt-1 ${scenario.statusClass}`}>
                  {scenario.statusCode}
                </span>
              </div>

              <button
                type="button"
                onClick={() => triggerScan(activeScenarioIdx)}
                className="w-full py-2 px-3 rounded-xl bg-slate-900 hover:bg-slate-800 text-xs text-sky-300 border border-slate-800 font-semibold flex items-center justify-center gap-1.5 transition-colors"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${isScanning ? 'animate-spin' : ''}`} />
                <span>Simular Re-escaneo</span>
              </button>
            </div>

            {/* Right: Guardhouse Console Verification HUD */}
            <div ref={consoleHUDRef} className="md:col-span-7 space-y-5">
              <div className="space-y-2">
                <div className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-bold border ${scenario.statusClass}`}>
                  {scenario.barrierOpen ? (
                    <>
                      <CheckCircle2 className="w-4 h-4" />
                      <span>Validación Exitosa · Apertura Autorizada</span>
                    </>
                  ) : (
                    <>
                      <XCircle className="w-4 h-4" />
                      <span>Acceso Denegado · Barrera Bloqueada</span>
                    </>
                  )}
                </div>

                <h3 className="text-xl sm:text-2xl font-bold text-white font-['Plus_Jakarta_Sans']">
                  Confirmación instantánea en garita
                </h3>

                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                  {scenario.detail}
                </p>
              </div>

              <div className="space-y-2.5 text-xs">
                <div className="p-3.5 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center justify-between">
                  <span className="text-slate-400">Anfitrión / Propietario:</span>
                  <span className="font-medium text-white">{scenario.host}</span>
                </div>

                <div className="p-3.5 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center justify-between">
                  <span className="text-slate-400">Vehículo Registrado:</span>
                  <span className="font-mono text-white flex items-center gap-1.5">
                    <Car className="w-3.5 h-3.5 text-sky-400" />
                    {scenario.vehicle}
                  </span>
                </div>

                <div className="p-3.5 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center justify-between">
                  <span className="text-slate-400">Bahía Asignada:</span>
                  <span className="font-mono font-bold text-sky-400">{scenario.bay}</span>
                </div>
              </div>

              <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 text-xs flex items-center gap-2.5 text-slate-300">
                <ShieldCheck className="w-4 h-4 text-sky-400 shrink-0" />
                <span>Asentado automáticamente en bitácora inmutable de auditoría con operador en turno.</span>
              </div>
            </div>

          </div>
        </div>

      </div>
    </section>
  );
}
