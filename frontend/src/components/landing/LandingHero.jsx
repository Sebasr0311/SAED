import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ShieldCheck,
  QrCode,
  Building,
  CreditCard,
  Package,
  Car,
  CheckCircle2,
  ArrowRight,
  Sparkles,
  ChevronRight,
  TrendingUp,
  Clock
} from 'lucide-react';

export default function LandingHero() {
  const [activeTab, setActiveTab] = useState('dashboard');

  const scrollTo = (id) => {
    const el = document.querySelector(id);
    if (el) {
      const topOffset = 80;
      const elementPosition = el.getBoundingClientRect().top;
      const offsetPosition = elementPosition + window.pageYOffset - topOffset;
      window.scrollTo({ top: offsetPosition, behavior: 'smooth' });
    }
  };

  return (
    <section
      id="hero"
      className="relative pt-28 pb-20 md:pt-36 md:pb-28 overflow-hidden bg-gradient-to-b from-[#0A1628] via-[#0F2044] to-[#0A1628] text-white"
    >
      {/* Background Decorative Grid & Glows */}
      <div className="absolute inset-0 bg-[radial-gradient(#1E4080_1px,transparent_1px)] [background-size:24px_24px] opacity-20 pointer-events-none" />
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[350px] bg-emerald-500/10 blur-[120px] rounded-full pointer-events-none" />
      <div className="absolute bottom-10 right-10 w-[400px] h-[250px] bg-blue-600/10 blur-[100px] rounded-full pointer-events-none" />

      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-3xl mx-auto space-y-6">
          {/* Eyebrow Badge */}
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-semibold tracking-wide shadow-sm backdrop-blur-sm">
            <Sparkles className="w-3.5 h-3.5" />
            <span>ADMINISTRACIÓN RESIDENCIAL INTELIGENTE</span>
          </div>

          {/* Main Headline */}
          <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Todo tu conjunto residencial,{' '}
            <span className="bg-clip-text text-transparent bg-gradient-to-r from-emerald-400 via-teal-300 to-cyan-400">
              en un solo lugar.
            </span>
          </h1>

          {/* Subheadline */}
          <p className="text-base sm:text-lg lg:text-xl text-slate-300 leading-relaxed max-w-2xl mx-auto">
            SAED centraliza la administración, residentes, visitas, cartera, paquetes, parqueaderos y comunicaciones en una plataforma digital segura, moderna y fácil de usar.
          </p>

          {/* CTAs with strict hierarchy */}
          <div className="flex flex-col sm:flex-row items-center justify-center gap-3 pt-2">
            <button
              onClick={() => scrollTo('#que-es-saed')}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-7 py-3.5 text-base font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-xl shadow-lg shadow-emerald-900/40 hover:shadow-emerald-700/50 focus:outline-none focus:ring-2 focus:ring-emerald-400 focus:ring-offset-2 focus:ring-offset-[#0A1628] transition-all transform active:scale-95 min-h-[48px]"
            >
              <span>Conocer SAED</span>
              <ArrowRight className="w-4 h-4" />
            </button>

            <button
              onClick={() => scrollTo('#planes')}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-7 py-3.5 text-base font-semibold text-slate-200 hover:text-white bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700 rounded-xl transition-colors focus:outline-none focus:ring-2 focus:ring-slate-400 min-h-[48px]"
            >
              <span>Ver planes</span>
            </button>
          </div>

          {/* Subtle existing user link */}
          <div className="pt-1">
            <Link
              to="/login"
              className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-emerald-300 transition-colors py-1"
            >
              <span>¿Ya eres usuario de una copropiedad? Iniciar sesión</span>
              <ChevronRight className="w-3.5 h-3.5 opacity-70" />
            </Link>
          </div>

          {/* Enterprise Trust Pills */}
          <div className="pt-4 grid grid-cols-2 sm:grid-cols-4 gap-3 text-left">
            <div className="flex items-center gap-2 p-2.5 rounded-lg bg-white/[0.03] border border-white/5 backdrop-blur-sm">
              <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
              <span className="text-xs text-slate-300 font-medium">Multi-Tenant Seguro</span>
            </div>
            <div className="flex items-center gap-2 p-2.5 rounded-lg bg-white/[0.03] border border-white/5 backdrop-blur-sm">
              <QrCode className="w-4 h-4 text-teal-400 shrink-0" />
              <span className="text-xs text-slate-300 font-medium">Control de Acceso QR</span>
            </div>
            <div className="flex items-center gap-2 p-2.5 rounded-lg bg-white/[0.03] border border-white/5 backdrop-blur-sm">
              <CreditCard className="w-4 h-4 text-amber-400 shrink-0" />
              <span className="text-xs text-slate-300 font-medium">Cartera & Wompi</span>
            </div>
            <div className="flex items-center gap-2 p-2.5 rounded-lg bg-white/[0.03] border border-white/5 backdrop-blur-sm">
              <Building className="w-4 h-4 text-blue-400 shrink-0" />
              <span className="text-xs text-slate-300 font-medium">100% Web & Móvil</span>
            </div>
          </div>
        </div>

        {/* Interactive Product Mockup Showcase */}
        <div className="mt-12 sm:mt-16 max-w-5xl mx-auto">
          <div className="rounded-2xl border border-slate-700/80 bg-[#0F172A] shadow-2xl shadow-black/60 overflow-hidden">
            {/* Window Header */}
            <div className="px-4 py-3 bg-[#0A1628] border-b border-slate-800 flex items-center justify-between flex-wrap gap-2">
              <div className="flex items-center gap-2">
                <div className="flex gap-1.5">
                  <div className="w-3 h-3 rounded-full bg-rose-500/80" />
                  <div className="w-3 h-3 rounded-full bg-amber-500/80" />
                  <div className="w-3 h-3 rounded-full bg-emerald-500/80" />
                </div>
                <span className="text-xs font-mono text-slate-400 ml-2 hidden sm:inline">app.saed.com · Demo Oficial</span>
              </div>

              {/* Interactive Mockup Tabs */}
              <div className="flex items-center gap-1 bg-slate-900/90 p-1 rounded-lg border border-slate-800 text-xs w-full sm:w-auto justify-between sm:justify-start">
                <button
                  type="button"
                  onClick={() => setActiveTab('dashboard')}
                  className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
                    activeTab === 'dashboard'
                      ? 'bg-emerald-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  Dashboard & Cartera
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab('qr')}
                  className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
                    activeTab === 'qr'
                      ? 'bg-emerald-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  Control QR
                </button>
                <button
                  type="button"
                  onClick={() => setActiveTab('logistica')}
                  className={`px-3 py-1.5 rounded-md font-medium transition-colors ${
                    activeTab === 'logistica'
                      ? 'bg-emerald-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  Paquetes & Puestos
                </button>
              </div>
            </div>

            {/* Mockup Canvas */}
            <div className="p-4 sm:p-6 bg-gradient-to-b from-[#0F172A] to-[#0A1628]">
              {activeTab === 'dashboard' && (
                <div className="space-y-4 animate-fadeIn">
                  {/* Top KPI Cards Row */}
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-slate-400 font-medium">Unidades Habitadas</span>
                        <Building className="w-4 h-4 text-blue-400" />
                      </div>
                      <p className="text-2xl font-bold text-white mt-1">4 Unidades</p>
                      <span className="text-xs text-emerald-400 flex items-center gap-1 mt-1 font-medium">
                        <CheckCircle2 className="w-3.5 h-3.5" /> Edificio Residencial SAED
                      </span>
                    </div>

                    <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-slate-400 font-medium">Cartera Total Pendiente</span>
                        <TrendingUp className="w-4 h-4 text-amber-400" />
                      </div>
                      <p className="text-2xl font-bold text-amber-300 mt-1">$250.000 COP</p>
                      <span className="text-xs text-slate-400 mt-1 block">
                        Apto 101 pendiente · Apto 102 al día
                      </span>
                    </div>

                    <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-slate-400 font-medium">Seguridad & Acceso</span>
                        <ShieldCheck className="w-4 h-4 text-emerald-400" />
                      </div>
                      <p className="text-2xl font-bold text-emerald-400 mt-1">100% Operativo</p>
                      <span className="text-xs text-slate-400 mt-1 block">
                        Portería Principal activa
                      </span>
                    </div>
                  </div>

                  {/* Demonstration Cartera Table */}
                  <div className="rounded-xl border border-slate-700/60 bg-slate-900/80 overflow-hidden">
                    <div className="px-4 py-2.5 bg-slate-800/50 border-b border-slate-700/60 flex justify-between items-center text-xs">
                      <span className="font-semibold text-slate-200">Estado de Cuenta por Apartamento</span>
                      <span className="text-emerald-400">Conciliación en línea</span>
                    </div>
                    <div className="divide-y divide-slate-800 text-xs">
                      <div className="px-4 py-2.5 flex items-center justify-between hover:bg-white/[0.02]">
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-white">Apto 101</span>
                          <span className="text-slate-400">Carlos Martinez</span>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="text-amber-300 font-semibold">$250.000 COP</span>
                          <span className="px-2 py-0.5 rounded text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
                            Pendiente
                          </span>
                        </div>
                      </div>

                      <div className="px-4 py-2.5 flex items-center justify-between hover:bg-white/[0.02]">
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-white">Apto 102</span>
                          <span className="text-slate-400">Ana Gomez</span>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="text-emerald-400 font-semibold">$0 COP</span>
                          <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                            Al Día · Wompi
                          </span>
                        </div>
                      </div>

                      <div className="px-4 py-2.5 flex items-center justify-between hover:bg-white/[0.02]">
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-white">Apto 201</span>
                          <span className="text-slate-400">Propietario 201</span>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="text-emerald-400 font-semibold">$0 COP</span>
                          <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                            Al Día
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'qr' && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 animate-fadeIn">
                  <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold text-emerald-400 uppercase tracking-wider">
                        Pase Digital de Visitante
                      </span>
                      <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-500/20 text-emerald-300">
                        ACTIVO
                      </span>
                    </div>
                    <div className="flex items-center gap-4 bg-slate-900/80 p-3 rounded-lg border border-slate-700/40">
                      <div className="w-16 h-16 bg-white p-1 rounded-lg flex items-center justify-center shrink-0">
                        <QrCode className="w-14 h-14 text-slate-950" />
                      </div>
                      <div className="space-y-1 text-xs">
                        <p className="font-bold text-white">Visitante Demo</p>
                        <p className="text-slate-400">C.C. 1000000010</p>
                        <p className="text-xs text-emerald-400 font-mono">SAED-DEMO-QR-2026</p>
                      </div>
                    </div>
                    <div className="text-xs text-slate-400 flex items-center gap-2">
                      <Clock className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                      <span>Válido para Apto 101 (Carlos Martinez)</span>
                    </div>
                  </div>

                  <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-3 flex flex-col justify-between">
                    <div>
                      <span className="text-xs font-semibold text-slate-300 uppercase tracking-wider block mb-2">
                        Resultado de Validación en Garita
                      </span>
                      <div className="space-y-2 text-xs">
                        <div className="flex justify-between py-1 border-b border-slate-700/40">
                          <span className="text-slate-400">Estado de Validación:</span>
                          <span className="text-emerald-400 font-semibold flex items-center gap-1">
                            <CheckCircle2 className="w-3.5 h-3.5" /> Válido & Autorizado
                          </span>
                        </div>
                        <div className="flex justify-between py-1 border-b border-slate-700/40">
                          <span className="text-slate-400">Medio de Transporte:</span>
                          <span className="text-white font-medium">Vehículo Particular</span>
                        </div>
                        <div className="flex justify-between py-1 border-b border-slate-700/40">
                          <span className="text-slate-400">Placa Registrada:</span>
                          <span className="text-amber-300 font-mono font-bold">DEM-123</span>
                        </div>
                        <div className="flex justify-between py-1 border-b border-slate-700/40">
                          <span className="text-slate-400">Parqueadero Asignado:</span>
                          <span className="text-teal-400 font-bold">V-01 (Visitantes)</span>
                        </div>
                      </div>
                    </div>
                    <div className="pt-2">
                      <div className="w-full py-1.5 rounded-lg bg-emerald-600/20 text-emerald-300 border border-emerald-500/30 text-center text-xs font-semibold">
                        Acceso Concedido · Visita En Curso
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'logistica' && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 animate-fadeIn">
                  {/* Paqueteria Card */}
                  <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Package className="w-4 h-4 text-teal-400" />
                        <span className="text-xs font-bold text-white">Custodia de Paquetería</span>
                      </div>
                      <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-500/20 text-emerald-300">
                        RECIBIDO
                      </span>
                    </div>
                    <div className="bg-slate-900/70 p-3 rounded-lg space-y-1.5 text-xs">
                      <div className="flex justify-between">
                        <span className="text-slate-400">Destinatario:</span>
                        <span className="text-white font-medium">Apto 101 · C. Martinez</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-400">Empresa:</span>
                        <span className="text-white">Servientrega (Guía AMZ-9988)</span>
                      </div>
                      <div className="flex justify-between items-center pt-1 border-t border-slate-800">
                        <span className="text-slate-400">PIN de Retiro:</span>
                        <span className="font-mono text-xs font-bold text-emerald-400 tracking-wider bg-emerald-950/60 px-2 py-0.5 rounded border border-emerald-500/30">
                          482910
                        </span>
                      </div>
                    </div>
                    <p className="text-xs text-slate-400">
                      Notificación enviada al casillero digital del residente.
                    </p>
                  </div>

                  {/* Parqueaderos Card */}
                  <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-3">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Car className="w-4 h-4 text-blue-400" />
                        <span className="text-xs font-bold text-white">Puestos de Parqueadero</span>
                      </div>
                      <span className="text-xs text-slate-400">Edificio SAED</span>
                    </div>
                    <div className="space-y-2">
                      <div className="p-2 rounded-lg bg-slate-900/80 border border-slate-800 flex items-center justify-between text-xs">
                        <div className="flex items-center gap-2">
                          <span className="w-2 h-2 rounded-full bg-rose-500" />
                          <span className="font-bold text-white">V-01</span>
                          <span className="text-xs text-slate-400">Visitantes</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-amber-300 font-semibold">DEM-123</span>
                          <span className="px-1.5 py-0.5 rounded text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
                            OCUPADO
                          </span>
                        </div>
                      </div>

                      <div className="p-2 rounded-lg bg-slate-900/80 border border-slate-800 flex items-center justify-between text-xs">
                        <div className="flex items-center gap-2">
                          <span className="w-2 h-2 rounded-full bg-emerald-500" />
                          <span className="font-bold text-white">V-02</span>
                          <span className="text-xs text-slate-400">Visitantes</span>
                        </div>
                        <span className="px-1.5 py-0.5 rounded text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                          DISPONIBLE
                        </span>
                      </div>
                    </div>
                    <p className="text-xs text-slate-400">
                      Liberación inmediata al marcar la salida en portería.
                    </p>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
