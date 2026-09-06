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
  RefreshCw,
  Zap,
} from 'lucide-react';
import { animate, stagger } from 'animejs';

export default function LandingProductShowcase() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const tabContentRef = useRef(null);

  // Tab 1 (Dashboard) Live Interactive State
  const [recaudado, setRecaudado] = useState(48200000);
  const [carteraPendiente, setCarteraPendiente] = useState(250000);
  const [simulatedPayments, setSimulatedPayments] = useState([
    {
      id: 1,
      apto: '402',
      unidad: 'Torre 2 · Apartamento 402',
      concepto: 'Cuota ordinaria Administración Septiembre',
      monto: '$350.000 COP',
      time: 'Aprobado Wompi (PSE) · 10:24 AM',
    },
    {
      id: 2,
      apto: '105',
      unidad: 'Torre 1 · Apartamento 105',
      concepto: 'Reserva Salón Comunal',
      monto: '$80.000 COP',
      time: 'Aprobado Wompi (Tarjeta) · 09:15 AM',
    },
  ]);

  // Tab 2 (Portería & QR) Interactive State
  const [visitorMode, setVisitorMode] = useState(0); // 0 or 1
  const [isScanning, setIsScanning] = useState(false);
  const scannerBeamRef = useRef(null);

  const visitors = [
    {
      nombre: 'Laura Gómez',
      cc: 'C.C. 1.098.441.***',
      unidad: 'Apto 302 · Torre 1',
      familia: 'Familia Ramírez (Propietario)',
      tipo: 'Familiar / Temporal',
      vigencia: '4 Horas restantes',
      vehiculo: 'Automóvil · ABC-123',
      bahia: 'V-03',
      token: '#QR-9942',
    },
    {
      nombre: 'Mateo Cárdenas',
      cc: 'C.C. 80.231.992',
      unidad: 'Apto 504 · Torre 2',
      familia: 'Héctor Cárdenas (Residente)',
      tipo: 'Domicilio / Mensajería',
      vigencia: '45 Minutos restantes',
      vehiculo: 'Motocicleta · KLM-88D',
      bahia: 'MV-01',
      token: '#QR-7718',
    },
  ];

  // Tab 3 (Paquetería & Parqueadero) Interactive State
  const [pkgStatus, setPkgStatus] = useState('custodia'); // 'custodia' | 'entregado'
  const [bays, setBays] = useState([
    { id: 'V-01', status: 'OCUPADO', plate: 'DEM-123', isMoto: false },
    { id: 'V-02', status: 'DISPONIBLE', plate: 'Libre', isMoto: false },
    { id: 'V-03', status: 'OCUPADO', plate: 'KLR-901', isMoto: false },
    { id: 'V-04', status: 'DISPONIBLE', plate: 'Libre', isMoto: false },
    { id: 'MV-01', status: 'DISPONIBLE', plate: 'Moto Libre', isMoto: true },
    { id: 'MV-02', status: 'OCUPADO', plate: 'M-440', isMoto: true },
  ]);

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

  // Tab 1 action: simulate new Wompi payment
  const handleSimulatePayment = () => {
    if (carteraPendiente === 0) {
      // Reset
      setRecaudado(48200000);
      setCarteraPendiente(250000);
      setSimulatedPayments((prev) => prev.slice(0, 2));
      return;
    }

    setRecaudado((prev) => prev + 250000);
    setCarteraPendiente(0);
    setSimulatedPayments((prev) => [
      {
        id: Date.now(),
        apto: '204',
        unidad: 'Torre 1 · Apartamento 204',
        concepto: 'Cuota ordinaria Administración Septiembre (Al día)',
        monto: '$250.000 COP',
        time: 'Aprobado Wompi (PSE Bancolombia) · Justo ahora',
      },
      ...prev,
    ]);
  };

  // Tab 2 action: simulate QR scan
  const handleScanVisitor = () => {
    setIsScanning(true);
    if (scannerBeamRef.current) {
      animate(scannerBeamRef.current, {
        translateY: [-40, 40],
        opacity: [0.8, 1, 0.8],
        duration: 800,
        direction: 'alternate',
        loop: 2,
        ease: 'easeInOutQuad',
        onComplete: () => {
          setIsScanning(false);
          setVisitorMode((prev) => (prev === 0 ? 1 : 0));
        },
      });
    } else {
      setTimeout(() => {
        setIsScanning(false);
        setVisitorMode((prev) => (prev === 0 ? 1 : 0));
      }, 700);
    }
  };

  // Tab 3 action: toggle parking bay
  const toggleBay = (bayId) => {
    setBays((prev) =>
      prev.map((b) => {
        if (b.id === bayId) {
          const isNowOccupied = b.status === 'DISPONIBLE';
          return {
            ...b,
            status: isNowOccupied ? 'OCUPADO' : 'DISPONIBLE',
            plate: isNowOccupied ? (b.isMoto ? 'M-991' : 'XYZ-789') : b.isMoto ? 'Moto Libre' : 'Libre',
          };
        }
        return b;
      })
    );
  };

  const currentVisitor = visitors[visitorMode];
  const occupiedBaysCount = bays.filter((b) => b.status === 'OCUPADO').length;
  const freeBaysCount = bays.length - occupiedBaysCount;

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
            EXPERIENCIA DE PRODUCTO INTERACTIVA
          </span>
          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Una interfaz diseñada para la velocidad operativa
          </h2>
          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed">
            Sin curvas de aprendizaje complejas. Prueba los controles en vivo en el siguiente entorno interactivo.
          </p>
        </div>

        {/* Browser Frame */}
        <div className="max-w-6xl mx-auto rounded-2xl sm:rounded-3xl border border-slate-800/90 bg-[#090E17] shadow-2xl shadow-black/90 overflow-hidden backdrop-blur-xl">
          
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
              <span>Entorno Demo Interactivo</span>
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
            
            {/* ========================================================================= */}
            {/* TAB 01: DASHBOARD & CARTERA */}
            {/* ========================================================================= */}
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
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={handleSimulatePayment}
                      className="px-3.5 py-1.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white text-xs font-bold shadow-md shadow-emerald-950/40 flex items-center gap-1.5 transition-all transform active:scale-95"
                    >
                      <Zap className="w-3.5 h-3.5" />
                      <span>{carteraPendiente > 0 ? 'Simular Recaudo PSE Wompi' : 'Reiniciar Cartera Demo'}</span>
                    </button>
                    <span className="px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-semibold text-xs hidden sm:inline">
                      {carteraPendiente === 0 ? '100% Recaudo Efectivo' : '94% Recaudo Efectivo'}
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
                    <div className="text-xl sm:text-2xl font-bold text-white font-mono">
                      ${recaudado.toLocaleString('es-CO')}
                    </div>
                    <span className="text-[11px] text-emerald-400 font-medium block">Al día en cuotas</span>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-900/90 border border-slate-800 space-y-1.5">
                    <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
                      <span>Cartera Pendiente Demo</span>
                      <TrendingUp className="w-3.5 h-3.5 text-amber-400" />
                    </div>
                    <div className={`text-xl sm:text-2xl font-bold font-mono transition-colors ${carteraPendiente === 0 ? 'text-emerald-400' : 'text-amber-400'}`}>
                      ${carteraPendiente.toLocaleString('es-CO')}
                    </div>
                    <span className="text-[11px] text-slate-400 block">
                      {carteraPendiente === 0 ? '¡Cero morosidad registrada!' : 'Apto 204 (1 cuota vencida)'}
                    </span>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-900/90 border border-slate-800 space-y-1.5">
                    <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
                      <span>Censo de Residentes</span>
                      <Users className="w-3.5 h-3.5 text-sky-400" />
                    </div>
                    <div className="text-xl sm:text-2xl font-bold text-white font-mono">128 / 130</div>
                    <span className="text-[11px] text-sky-400 font-medium block">98% Ocupación</span>
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
                      Últimos Recaudos Registrados (Demo Interactivo)
                    </span>
                    <span className="text-[11px] text-slate-400">Conciliación bancaria en tiempo real</span>
                  </div>
                  <div className="divide-y divide-slate-800 text-xs">
                    {simulatedPayments.map((p) => (
                      <div
                        key={p.id}
                        className="p-3.5 flex items-center justify-between flex-wrap gap-2 hover:bg-slate-800/30 transition-colors"
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-lg bg-emerald-500/10 text-emerald-400 flex items-center justify-center font-bold font-mono">
                            {p.apto}
                          </div>
                          <div>
                            <p className="font-bold text-white">{p.unidad}</p>
                            <p className="text-[11px] text-slate-400">{p.concepto}</p>
                          </div>
                        </div>
                        <div className="text-right">
                          <span className="font-bold font-mono text-emerald-400 text-sm">{p.monto}</span>
                          <span className="block text-[10px] text-slate-400">{p.time}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {/* ========================================================================= */}
            {/* TAB 02: PORTERÍA & QR */}
            {/* ========================================================================= */}
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
                    <button
                      type="button"
                      onClick={handleScanVisitor}
                      disabled={isScanning}
                      className="px-3.5 py-1.5 rounded-xl bg-sky-600 hover:bg-sky-500 text-white font-bold text-xs shadow-md shadow-sky-950/40 flex items-center gap-1.5 transition-all transform active:scale-95 disabled:opacity-50"
                    >
                      <RefreshCw className={`w-3.5 h-3.5 ${isScanning ? 'animate-spin' : ''}`} />
                      <span>{isScanning ? 'Escaneando código...' : 'Escanear Siguiente Pase'}</span>
                    </button>
                    <span className="px-2.5 py-1 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-semibold flex items-center gap-1.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                      Lector QR Activo
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-center">
                  {/* Left: Validation Result Card */}
                  <div className="md:col-span-7 bg-slate-900/90 border border-emerald-500/30 rounded-2xl p-5 sm:p-6 space-y-4 relative overflow-hidden">
                    {/* Laser Scanner Beam Overlay */}
                    {isScanning && (
                      <div
                        ref={scannerBeamRef}
                        className="absolute inset-x-0 h-1 bg-gradient-to-r from-transparent via-cyan-400 to-transparent shadow-[0_0_15px_#38bdf8] pointer-events-none z-20"
                        style={{ top: '50%' }}
                      />
                    )}

                    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                      <div className="flex items-center gap-2.5">
                        <div className="w-7 h-7 rounded-lg bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                          <CheckCircle2 className="w-4 h-4" />
                        </div>
                        <span className="text-xs font-bold uppercase tracking-wider text-emerald-400">
                          Pase de Acceso Válido
                        </span>
                      </div>
                      <span className="text-[11px] font-mono text-sky-400">{currentVisitor.token}</span>
                    </div>

                    <div className="grid grid-cols-2 gap-4 text-xs">
                      <div>
                        <span className="text-slate-400 block text-[11px]">Visitante:</span>
                        <p className="font-bold text-white text-sm mt-0.5">{currentVisitor.nombre}</p>
                        <span className="text-slate-500 text-[10px]">{currentVisitor.cc}</span>
                      </div>
                      <div>
                        <span className="text-slate-400 block text-[11px]">Unidad que autoriza:</span>
                        <p className="font-bold text-emerald-400 text-sm mt-0.5">{currentVisitor.unidad}</p>
                        <span className="text-slate-500 text-[10px]">{currentVisitor.familia}</span>
                      </div>
                      <div>
                        <span className="text-slate-400 block text-[11px]">Tipo de acceso:</span>
                        <p className="font-bold text-white mt-0.5">{currentVisitor.tipo}</p>
                        <span className="text-slate-500 text-[10px]">Vigencia: {currentVisitor.vigencia}</span>
                      </div>
                      <div>
                        <span className="text-slate-400 block text-[11px]">Vehículo visitante:</span>
                        <p className="font-bold text-white mt-0.5">{currentVisitor.vehiculo}</p>
                        <span className="text-emerald-400 text-[10px] font-semibold">Bahía: {currentVisitor.bahia}</span>
                      </div>
                    </div>

                    <div className="pt-2 flex items-center gap-3">
                      <div className="w-full py-2.5 px-4 rounded-xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 font-bold text-xs flex items-center justify-center gap-1.5">
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Ingreso Asentado en Bitácora Inmutable (Demo)</span>
                      </div>
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

            {/* ========================================================================= */}
            {/* TAB 03: PAQUETERÍA & PARQUEADEROS */}
            {/* ========================================================================= */}
            {activeTab === 'operacion' && (
              <div className="space-y-6 animate-in fade-in duration-200">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800/80 pb-4">
                  <div>
                    <h3 className="text-lg sm:text-xl font-bold text-white font-['Plus_Jakarta_Sans']">
                      Operación Física · Paquetería con PIN &amp; Bahías de Estacionamiento
                    </h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Haz clic en cualquier bahía para simular ocupación o liberación en vivo
                    </p>
                  </div>
                  <div className="flex items-center gap-2 text-xs">
                    <span className="px-2.5 py-1 rounded-lg bg-sky-500/10 text-sky-400 border border-sky-500/20 font-semibold">
                      {occupiedBaysCount} Ocupadas / {freeBaysCount} Libres
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                  {/* Left: Custodia de Paquetes */}
                  <div className="p-5 rounded-2xl bg-slate-900/90 border border-slate-800 space-y-4">
                    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                      <div className="flex items-center gap-2.5">
                        <Package className="w-4 h-4 text-sky-400" />
                        <h4 className="text-sm font-bold text-white">Casillero Activo en Portería</h4>
                      </div>
                      <span
                        className={`text-[10px] px-2.5 py-0.5 rounded-full font-semibold border ${
                          pkgStatus === 'custodia'
                            ? 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                            : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                        }`}
                      >
                        {pkgStatus === 'custodia' ? 'En Custodia Garita' : 'Entregado a Residente'}
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
                    <div className="p-3.5 rounded-xl bg-slate-950 border border-sky-500/30 flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <KeyRound className="w-4 h-4 text-sky-400" />
                        <span className="text-xs text-sky-300 font-medium">PIN Criptográfico:</span>
                      </div>
                      <span className="font-mono text-sm font-bold text-sky-200 tracking-widest bg-slate-900 px-2.5 py-1 rounded border border-sky-500/40">
                        8 4 9 2 0 1
                      </span>
                    </div>

                    <button
                      type="button"
                      onClick={() => setPkgStatus((prev) => (prev === 'custodia' ? 'entregado' : 'custodia'))}
                      className="w-full py-2.5 px-4 rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-2 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                      <span>{pkgStatus === 'custodia' ? 'Simular Validación y Entrega con PIN' : 'Restablecer Paquete a Custodia'}</span>
                    </button>
                  </div>

                  {/* Right: Parqueaderos en Tiempo Real */}
                  <div className="p-5 rounded-2xl bg-slate-900/90 border border-slate-800 space-y-4">
                    <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                      <div className="flex items-center gap-2.5">
                        <Car className="w-4 h-4 text-sky-400" />
                        <h4 className="text-sm font-bold text-white">Bahías de Visitantes en Vivo</h4>
                      </div>
                      <span className="text-[10px] text-slate-400">Clic para cambiar estado</span>
                    </div>

                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 text-xs">
                      {bays.map((bay) => {
                        const isOccupied = bay.status === 'OCUPADO';
                        return (
                          <button
                            key={bay.id}
                            type="button"
                            onClick={() => toggleBay(bay.id)}
                            className={`p-2.5 rounded-xl border text-center space-y-1 transition-all transform active:scale-95 ${
                              isOccupied
                                ? 'bg-rose-500/10 border-rose-500/30 hover:bg-rose-500/20'
                                : 'bg-emerald-500/10 border-emerald-500/30 hover:bg-emerald-500/20'
                            }`}
                          >
                            <span className="font-mono font-bold text-white text-xs block">{bay.id}</span>
                            <span
                              className={`text-[10px] font-bold uppercase block ${
                                isOccupied ? 'text-rose-400' : 'text-emerald-400'
                              }`}
                            >
                              {bay.status}
                            </span>
                            <span className="text-[10px] font-mono text-slate-400 block truncate">
                              {bay.plate}
                            </span>
                          </button>
                        );
                      })}
                    </div>

                    <p className="text-[11px] text-slate-500 leading-relaxed">
                      El guardia asigna la bahía al registrar el ingreso vehicular y el puesto se libera automáticamente al asentar la salida.
                    </p>
                  </div>
                </div>
              </div>
            )}

          </div>

          {/* Bottom Browser Status Bar */}
          <div className="px-4 py-2.5 bg-[#0A1628] border-t border-slate-800 flex items-center justify-between text-[11px] text-slate-500">
            <span>SAED 2.0 Core Interface · Visualización Interactiva</span>
            <span>Datos reactivos del entorno demo</span>
          </div>

        </div>

      </div>
    </section>
  );
}
