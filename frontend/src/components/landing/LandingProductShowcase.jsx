import { useState, useEffect, useRef } from 'react';
import {
  BarChart3,
  QrCode,
  Package,
  Car,
  CheckCircle2,
  Clock,
  ShieldCheck,
  TrendingUp,
  CreditCard,
  Users,
  KeyRound,
  Sparkles,
} from 'lucide-react';
import { animate, stagger } from 'animejs';

export default function LandingProductShowcase() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const tabContentRef = useRef(null);

  const tabs = [
    { id: 'dashboard', label: '01 Dashboard & Cartera', icon: BarChart3 },
    { id: 'porteria', label: '02 Portería & QR', icon: QrCode },
    { id: 'operacion', label: '03 Paquetería & Parqueaderos', icon: Package },
  ];

  // Trigger Anime.js staggered reveal whenever activeTab changes
  useEffect(() => {
    if (tabContentRef.current) {
      animate(tabContentRef.current.children, {
        opacity: [0, 1],
        translateY: [18, 0],
        duration: 550,
        delay: stagger(70),
        ease: 'outExpo',
      });
    }
  }, [activeTab]);

  return (
    <section
      id="producto"
      className="py-20 sm:py-28 lg:py-32 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      {/* Background ambient lighting */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[850px] h-[420px] bg-sky-500/10 blur-[160px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-12 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20">
            <Sparkles className="w-3.5 h-3.5 text-sky-400" />
            EXPERIENCIA DE PRODUCTO
          </span>
          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Una interfaz diseñada para la velocidad operativa
          </h2>
          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed">
            Sin curvas de aprendizaje complejas. Navegación fluida para administradores, personal de garita y residentes.
          </p>
        </div>

        {/* Browser Frame */}
        <div className="max-w-6xl mx-auto rounded-2xl sm:rounded-3xl border border-slate-800/90 bg-[#090E17] shadow-2xl shadow-black/90 overflow-hidden">
          
          {/* Top Browser Frame Chrome */}
          <div className="px-4 py-3 bg-[#060910] border-b border-slate-800/90 flex items-center justify-between flex-wrap gap-3">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 rounded-full bg-rose-500/80" />
              <div className="w-3 h-3 rounded-full bg-amber-500/80" />
              <div className="w-3 h-3 rounded-full bg-emerald-500/80" />
              <span className="ml-2 text-xs font-mono text-slate-400 hidden sm:inline">
                https://app.saed.com/operacion/copropiedad
              </span>
            </div>

            <div className="flex items-center gap-2 text-xs text-slate-300 bg-slate-900/90 px-3 py-1 rounded-full border border-slate-800">
              <span className="w-2 h-2 rounded-full bg-sky-400 animate-pulse" />
              <span>Entorno Demo Activo</span>
            </div>
          </div>

          {/* Interactive Navigation Tabs */}
          <div className="bg-slate-950/90 border-b border-slate-800 px-4 sm:px-6 pt-3 flex items-center gap-2 overflow-x-auto no-scrollbar" role="tablist">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              const isActive = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  role="tab"
                  aria-selected={isActive}
                  type="button"
                  onClick={() => setActiveTab(tab.id)}
                  className={`flex items-center gap-2.5 px-4 sm:px-5 py-3 rounded-t-xl text-xs sm:text-sm font-bold transition-all whitespace-nowrap border-b-2 min-h-[44px] ${
                    isActive
                      ? 'bg-[#090E17] text-white border-sky-400 shadow-sm'
                      : 'text-slate-400 hover:text-slate-200 border-transparent hover:bg-slate-900/40'
                  }`}
                >
                  <Icon className={`w-4 h-4 ${isActive ? 'text-sky-400' : 'text-slate-500'}`} />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </div>

          {/* Mockup Workspace Area */}
          <div ref={tabContentRef} className="p-4 sm:p-6 lg:p-8 min-h-[440px] bg-[#090E17]">
            
            {/* TAB 01: DASHBOARD & CARTERA */}
            {activeTab === 'dashboard' && (
              <div className="space-y-6 animate-in fade-in duration-200">
                {/* Header Subbar */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800/80 pb-4">
                  <div>
                    <h3 className="text-lg sm:text-xl font-bold text-white font-['Plus_Jakarta_Sans']">
                      Conjunto Residencial Las Acacias · Consolidado
                    </h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Periodo actual: Septiembre 2026 · 130 Unidades habitacionales
                    </p>
                  </div>
                  <div className="flex items-center gap-2 text-xs">
                    <span className="px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-semibold">
                      94% Recaudo Efectivo
                    </span>
                  </div>
                </div>

                {/* 4 Real KPI Cards */}
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-3.5 sm:gap-4">
                  <div className="p-4 rounded-xl bg-slate-900/90 border border-slate-800 space-y-1.5">
                    <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
                      <span>Cartera Corriente</span>
                      <CreditCard className="w-3.5 h-3.5 text-emerald-400" />
                    </div>
                    <div className="text-xl sm:text-2xl font-bold text-white font-mono">$48.200.000</div>
                    <span className="text-[11px] text-emerald-400 font-medium block">Al día en cuotas</span>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-900/90 border border-slate-800 space-y-1.5">
                    <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
                      <span>Cartera Pendiente Demo</span>
                      <TrendingUp className="w-3.5 h-3.5 text-amber-400" />
                    </div>
                    <div className="text-xl sm:text-2xl font-bold text-amber-400 font-mono">$250.000</div>
                    <span className="text-[11px] text-slate-400 block">Apto 204 (1 cuota vencida)</span>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-900/90 border border-slate-800 space-y-1.5">
                    <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
                      <span>Censo de Residentes</span>
                      <Users className="w-3.5 h-3.5 text-blue-400" />
                    </div>
                    <div className="text-xl sm:text-2xl font-bold text-white font-mono">128 / 130</div>
                    <span className="text-[11px] text-blue-400 font-medium block">98% Ocupación</span>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-900/90 border border-slate-800 space-y-1.5">
                    <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
                      <span>Pasarela Wompi</span>
                      <ShieldCheck className="w-3.5 h-3.5 text-teal-400" />
                    </div>
                    <div className="text-xl sm:text-2xl font-bold text-white font-mono">Conectada</div>
                    <span className="text-[11px] text-teal-400 font-medium block">PSE y Tarjetas Activo</span>
                  </div>
                </div>

                {/* Simulated Recent Collections Table */}
                <div className="rounded-xl border border-slate-800 bg-slate-900/70 overflow-hidden">
                  <div className="px-4 py-3 bg-slate-900 border-b border-slate-800 flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-300 uppercase tracking-wider">
                      Últimos Recaudos Registrados (Demo)
                    </span>
                    <span className="text-[11px] text-slate-500">Conciliación bancaria en tiempo real</span>
                  </div>
                  <div className="divide-y divide-slate-800 text-xs">
                    <div className="p-3.5 flex items-center justify-between flex-wrap gap-2 hover:bg-slate-800/30 transition-colors">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-400 flex items-center justify-center font-bold font-mono">
                          402
                        </div>
                        <div>
                          <p className="font-bold text-white">Torre 2 · Apartamento 402</p>
                          <p className="text-[11px] text-slate-400">Cuota ordinaria Administración Septiembre</p>
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="font-bold font-mono text-emerald-400 text-sm">$350.000 COP</span>
                        <span className="block text-[10px] text-slate-400">Aprobado Wompi (PSE) · 10:24 AM</span>
                      </div>
                    </div>

                    <div className="p-3.5 flex items-center justify-between flex-wrap gap-2 hover:bg-slate-800/30 transition-colors">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-400 flex items-center justify-center font-bold font-mono">
                          105
                        </div>
                        <div>
                          <p className="font-bold text-white">Torre 1 · Apartamento 105</p>
                          <p className="text-[11px] text-slate-400">Reserva Salón Comunal</p>
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="font-bold font-mono text-emerald-400 text-sm">$80.000 COP</span>
                        <span className="block text-[10px] text-slate-400">Aprobado Wompi (Tarjeta) · 09:15 AM</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* TAB 02: PORTERÍA & QR */}
            {activeTab === 'porteria' && (
              <div className="space-y-6 animate-in fade-in duration-200">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800/80 pb-4">
                  <div>
                    <h3 className="text-lg sm:text-xl font-bold text-white font-['Plus_Jakarta_Sans']">
                      Consola de Garita · HUD de Validación QR
                    </h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Operador en turno: Carlos Mendoza (Portero) · Garita Principal
                    </p>
                  </div>
                  <div className="flex items-center gap-2 text-xs">
                    <span className="px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-semibold flex items-center gap-1.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                      Lector QR Activo
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-center">
                  {/* Left: Validation Result Card */}
                  <div className="md:col-span-7 bg-slate-900/90 border border-emerald-500/30 rounded-2xl p-5 sm:p-6 space-y-4">
                    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                      <div className="flex items-center gap-2.5">
                        <div className="w-7 h-7 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                          <CheckCircle2 className="w-4 h-4" />
                        </div>
                        <span className="text-xs font-bold uppercase tracking-wider text-emerald-400">
                          Pase de Acceso Válido
                        </span>
                      </div>
                      <span className="text-[11px] font-mono text-slate-400">TOKEN: #QR-9942</span>
                    </div>

                    <div className="grid grid-cols-2 gap-4 text-xs">
                      <div>
                        <span className="text-slate-400 block text-[11px]">Visitante:</span>
                        <p className="font-bold text-white text-sm mt-0.5">Laura Gómez</p>
                        <span className="text-slate-500 text-[10px]">C.C. 1.098.441.***</span>
                      </div>
                      <div>
                        <span className="text-slate-400 block text-[11px]">Unidad que autoriza:</span>
                        <p className="font-bold text-emerald-400 text-sm mt-0.5">Apto 302 · Torre 1</p>
                        <span className="text-slate-500 text-[10px]">Familia Ramírez (Propietario)</span>
                      </div>
                      <div>
                        <span className="text-slate-400 block text-[11px]">Tipo de acceso:</span>
                        <p className="font-bold text-white mt-0.5">Familiar / Temporal</p>
                        <span className="text-slate-500 text-[10px]">Vigencia: 4 Horas restantes</span>
                      </div>
                      <div>
                        <span className="text-slate-400 block text-[11px]">Vehículo visitante:</span>
                        <p className="font-bold text-white mt-0.5">Automóvil · ABC-123</p>
                        <span className="text-emerald-400 text-[10px] font-semibold">Bahía Asignada: V-03</span>
                      </div>
                    </div>

                    <div className="pt-2 flex items-center gap-3">
                      <button
                        type="button"
                        className="flex-1 py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs shadow-md shadow-emerald-950/40 transition-colors flex items-center justify-center gap-1.5"
                      >
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Confirmar Ingreso a Bitácora</span>
                      </button>
                    </div>
                  </div>

                  {/* Right: Live Gatehouse Activity Stream */}
                  <div className="md:col-span-5 bg-slate-900/60 border border-slate-800 rounded-2xl p-5 space-y-3">
                    <span className="text-xs font-bold text-slate-300 uppercase tracking-wider block border-b border-slate-800 pb-2">
                      Bitácora de Garita en Vivo
                    </span>
                    <div className="space-y-2.5 text-xs">
                      <div className="p-2.5 rounded-lg bg-slate-800/40 border border-slate-700/40 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <Clock className="w-3.5 h-3.5 text-slate-400" />
                          <span className="text-slate-200">14:32 · Peatonal Apto 501</span>
                        </div>
                        <span className="text-[10px] text-emerald-400 font-semibold">Ingresó</span>
                      </div>
                      <div className="p-2.5 rounded-lg bg-slate-800/40 border border-slate-700/40 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <Clock className="w-3.5 h-3.5 text-slate-400" />
                          <span className="text-slate-200">14:15 · Vehículo KLR-901</span>
                        </div>
                        <span className="text-[10px] text-emerald-400 font-semibold">Ingresó (V-01)</span>
                      </div>
                      <div className="p-2.5 rounded-lg bg-slate-800/40 border border-slate-700/40 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <Clock className="w-3.5 h-3.5 text-slate-400" />
                          <span className="text-slate-200">13:50 · Salida Vehículo TTT-404</span>
                        </div>
                        <span className="text-[10px] text-slate-400">Liberó Bahía</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* TAB 03: PAQUETERÍA & PARQUEADEROS */}
            {activeTab === 'operacion' && (
              <div className="space-y-6 animate-in fade-in duration-200">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800/80 pb-4">
                  <div>
                    <h3 className="text-lg sm:text-xl font-bold text-white font-['Plus_Jakarta_Sans']">
                      Operación Física · Paquetería con PIN & Bahías de Estacionamiento
                    </h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Custodia digital trazable y disponibilidad vehicular en tiempo real
                    </p>
                  </div>
                  <div className="flex items-center gap-2 text-xs">
                    <span className="px-2.5 py-1 rounded-lg bg-teal-500/10 text-teal-400 border border-teal-500/20 font-semibold">
                      4 Bahías Ocupadas / 2 Libres
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  {/* Left: Custodia de Paquetes */}
                  <div className="p-5 rounded-2xl bg-slate-900/90 border border-slate-800 space-y-4">
                    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                      <div className="flex items-center gap-2.5">
                        <Package className="w-4 h-4 text-teal-400" />
                        <h4 className="text-sm font-bold text-white">Casillero Activo en Portería</h4>
                      </div>
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-400 border border-amber-500/20 font-semibold">
                        Pendiente Retiro
                      </span>
                    </div>

                    <div className="space-y-2 text-xs">
                      <div className="flex justify-between">
                        <span className="text-slate-400">Guía de Mensajería:</span>
                        <span className="font-mono font-bold text-white">Servientrega #AMZ-8890</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-400">Destinatario:</span>
                        <span className="text-white font-medium">Apto 101 · Carlos Martínez</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-400">Recepción en garita:</span>
                        <span className="text-slate-300">Hoy a las 11:20 AM</span>
                      </div>
                    </div>

                    {/* PIN Security Pill */}
                    <div className="p-3 rounded-xl bg-teal-950/40 border border-teal-500/30 flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <KeyRound className="w-4 h-4 text-teal-400" />
                        <span className="text-xs text-teal-300 font-medium">PIN de Retiro Cifrado:</span>
                      </div>
                      <span className="font-mono text-sm font-bold text-teal-200 tracking-widest bg-slate-900 px-2.5 py-1 rounded border border-teal-500/40">
                        8 4 9 2 0 1
                      </span>
                    </div>

                    <p className="text-[11px] text-slate-500 leading-relaxed">
                      El paquete solo se entrega cuando el residente dicta su PIN en portería. Cero entregas por confusión.
                    </p>
                  </div>

                  {/* Right: Parqueaderos en Tiempo Real */}
                  <div className="p-5 rounded-2xl bg-slate-900/90 border border-slate-800 space-y-4">
                    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                      <div className="flex items-center gap-2.5">
                        <Car className="w-4 h-4 text-emerald-400" />
                        <h4 className="text-sm font-bold text-white">Bahías de Visitantes en Vivo</h4>
                      </div>
                      <span className="text-[10px] text-slate-400">Actualización en tiempo real</span>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 text-xs">
                      <div className="p-2.5 rounded-xl bg-rose-500/10 border border-rose-500/30 text-center space-y-1">
                        <span className="font-mono font-bold text-white text-xs block">V-01</span>
                        <span className="text-[10px] font-bold text-rose-400 uppercase block">OCUPADO</span>
                        <span className="text-[10px] font-mono text-slate-400 block">DEM-123</span>
                      </div>

                      <div className="p-2.5 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-center space-y-1">
                        <span className="font-mono font-bold text-white text-xs block">V-02</span>
                        <span className="text-[10px] font-bold text-emerald-400 uppercase block">DISPONIBLE</span>
                        <span className="text-[10px] text-slate-500 block">Libre</span>
                      </div>

                      <div className="p-2.5 rounded-xl bg-rose-500/10 border border-rose-500/30 text-center space-y-1">
                        <span className="font-mono font-bold text-white text-xs block">V-03</span>
                        <span className="text-[10px] font-bold text-rose-400 uppercase block">OCUPADO</span>
                        <span className="text-[10px] font-mono text-slate-400 block">KLR-901</span>
                      </div>

                      <div className="p-2.5 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-center space-y-1">
                        <span className="font-mono font-bold text-white text-xs block">V-04</span>
                        <span className="text-[10px] font-bold text-emerald-400 uppercase block">DISPONIBLE</span>
                        <span className="text-[10px] text-slate-500 block">Libre</span>
                      </div>

                      <div className="p-2.5 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-center space-y-1">
                        <span className="font-mono font-bold text-white text-xs block">MV-01</span>
                        <span className="text-[10px] font-bold text-emerald-400 uppercase block">DISPONIBLE</span>
                        <span className="text-[10px] text-slate-500 block">Moto Libre</span>
                      </div>

                      <div className="p-2.5 rounded-xl bg-rose-500/10 border border-rose-500/30 text-center space-y-1">
                        <span className="font-mono font-bold text-white text-xs block">MV-02</span>
                        <span className="text-[10px] font-bold text-rose-400 uppercase block">OCUPADO</span>
                        <span className="text-[10px] font-mono text-slate-400 block">M-440</span>
                      </div>
                    </div>

                    <p className="text-[11px] text-slate-500 leading-relaxed">
                      El sistema asigna la bahía al registrar el vehículo y la libera automáticamente al asentar la salida en garita.
                    </p>
                  </div>
                </div>
              </div>
            )}

          </div>

          {/* Bottom Browser Status Bar */}
          <div className="px-4 py-2.5 bg-[#0A1628] border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-500">
            <span>SAED 2.0 Core Interface · Visualización Interactiva</span>
            <span>Datos representativos del entorno demo</span>
          </div>

        </div>

      </div>
    </section>
  );
}
