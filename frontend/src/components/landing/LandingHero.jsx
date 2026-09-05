import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ShieldCheck,
  QrCode,
  Building,
  Package,
  Car,
  CheckCircle2,
  ArrowRight,
  TrendingUp,
  Clock,
} from 'lucide-react';

export default function LandingHero() {
  const [activeTab, setActiveTab] = useState('dashboard');

  const scrollTo = (id) => {
    const el = document.querySelector(id);
    if (el) {
      const topOffset = 84;
      const elementPosition = el.getBoundingClientRect().top;
      const offsetPosition = elementPosition + window.pageYOffset - topOffset;
      window.scrollTo({ top: offsetPosition, behavior: 'smooth' });
    }
  };

  return (
    <section
      id="hero"
      className="relative pt-32 pb-24 sm:pt-40 sm:pb-32 lg:pt-44 lg:pb-36 overflow-hidden bg-gradient-to-b from-[#0A1628] via-[#0F2044] to-[#0A1628] text-white"
    >
      {/* Subtle Atmospheric Gradients & Glows */}
      <div className="absolute inset-0 bg-[radial-gradient(#1E4080_1px,transparent_1px)] [background-size:32px_32px] opacity-15 pointer-events-none" />
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[350px] bg-emerald-500/10 blur-[140px] rounded-full pointer-events-none" />
      <div className="absolute bottom-20 right-10 w-[500px] h-[300px] bg-teal-500/10 blur-[120px] rounded-full pointer-events-none" />

      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Editorial Hero Header */}
        <div className="text-center max-w-4xl mx-auto space-y-6 sm:space-y-8">
          {/* Eyebrow */}
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/25 text-emerald-400 text-xs font-bold tracking-widest uppercase shadow-sm backdrop-blur-md">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            <span>PLATAFORMA PROPTECH ENTERPRISE</span>
          </div>

          {/* Grand Headline */}
          <h1 className="text-5xl sm:text-7xl lg:text-[5.5rem] font-extrabold tracking-tight text-white leading-[1.05] font-['Plus_Jakarta_Sans']">
            Todo tu conjunto.<br className="hidden sm:inline" />{' '}
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 via-teal-300 to-cyan-300">
              En un solo lugar.
            </span>
          </h1>

          {/* Subheadline */}
          <p className="text-base sm:text-xl text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            SAED conecta administración, residentes, portería y operaciones en una sola plataforma digital, moderna y segura.
          </p>

          {/* Primary Action Buttons */}
          <div className="flex flex-col sm:flex-row items-center justify-center gap-3.5 pt-2">
            <a
              href="#que-es-saed"
              onClick={(e) => {
                e.preventDefault();
                scrollTo('#que-es-saed');
              }}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-8 py-4 text-base font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-xl shadow-lg shadow-emerald-950/50 hover:shadow-emerald-700/50 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-400 transition-all transform active:scale-95 min-h-[48px]"
            >
              <span>Conocer SAED</span>
              <ArrowRight className="w-4 h-4 opacity-90" />
            </a>

            <button
              type="button"
              onClick={() => scrollTo('#showcase-frame')}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-7 py-4 text-base font-semibold text-slate-200 hover:text-white bg-white/5 hover:bg-white/10 border border-slate-700/80 rounded-xl transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-slate-400 min-h-[48px]"
            >
              <span>Ver la plataforma</span>
            </button>
          </div>

          {/* Direct Login Link */}
          <div className="pt-1">
            <Link
              to="/login"
              className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-emerald-300 transition-colors py-1"
            >
              <span>¿Ya eres parte de una copropiedad? Iniciar sesión</span>
              <ArrowRight className="w-3.5 h-3.5 opacity-70" />
            </Link>
          </div>
        </div>

        {/* GRAND PRODUCT SHOWCASE (Application Browser Frame) */}
        <div id="showcase-frame" className="mt-16 sm:mt-24 max-w-5xl mx-auto">
          <div className="rounded-2xl sm:rounded-3xl border border-slate-700/70 bg-[#0F172A] shadow-2xl shadow-black/80 overflow-hidden">
            {/* Top Browser Frame Chrome */}
            <div className="px-4 py-3.5 bg-[#0A1628] border-b border-slate-800 flex items-center justify-between flex-wrap gap-2.5">
              <div className="flex items-center gap-3">
                <div className="flex gap-1.5" aria-hidden="true">
                  <div className="w-3 h-3 rounded-full bg-rose-500/80" />
                  <div className="w-3 h-3 rounded-full bg-amber-500/80" />
                  <div className="w-3 h-3 rounded-full bg-emerald-500/80" />
                </div>
                <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-slate-900/90 border border-slate-800 text-[11px] font-mono text-slate-400">
                  <span>app.saed.com</span>
                  <span className="text-slate-600">/</span>
                  <span className="text-emerald-400">demo-oficial</span>
                </div>
              </div>

              {/* View Selector Tabs */}
              <div
                className="flex items-center gap-1 bg-slate-900/90 p-1 rounded-xl border border-slate-800 text-xs w-full sm:w-auto justify-between sm:justify-start"
                role="tablist"
                aria-label="Vistas del producto"
              >
                <button
                  type="button"
                  role="tab"
                  aria-selected={activeTab === 'dashboard'}
                  onClick={() => setActiveTab('dashboard')}
                  className={`px-3.5 py-1.5 rounded-lg font-semibold transition-all ${
                    activeTab === 'dashboard'
                      ? 'bg-emerald-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  Dashboard & Cartera
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={activeTab === 'qr'}
                  onClick={() => setActiveTab('qr')}
                  className={`px-3.5 py-1.5 rounded-lg font-semibold transition-all ${
                    activeTab === 'qr'
                      ? 'bg-emerald-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  Portería & QR
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={activeTab === 'operaciones'}
                  onClick={() => setActiveTab('operaciones')}
                  className={`px-3.5 py-1.5 rounded-lg font-semibold transition-all ${
                    activeTab === 'operaciones'
                      ? 'bg-emerald-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  Paquetes & Puestos
                </button>
              </div>
            </div>

            {/* Mockup Canvas with Real SAED Product Data */}
            <div className="p-4 sm:p-7 bg-gradient-to-b from-[#0F172A] to-[#0A1628]">
              {/* VIEW 1: Dashboard & Cartera */}
              {activeTab === 'dashboard' && (
                <div className="space-y-4 animate-fadeIn" role="tabpanel">
                  {/* Executive Metric Cards */}
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
                    <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-slate-400 font-medium">Unidades Registradas</span>
                        <Building className="w-4 h-4 text-blue-400" />
                      </div>
                      <p className="text-2xl font-bold text-white mt-1.5">4 Unidades</p>
                      <span className="text-xs text-emerald-400 flex items-center gap-1 mt-1 font-medium">
                        <CheckCircle2 className="w-3.5 h-3.5" /> Edificio Residencial SAED
                      </span>
                    </div>

                    <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-slate-400 font-medium">Cartera Total Pendiente</span>
                        <TrendingUp className="w-4 h-4 text-amber-400" />
                      </div>
                      <p className="text-2xl font-bold text-amber-300 mt-1.5">$250.000 COP</p>
                      <span className="text-xs text-slate-400 mt-1 block">
                        Apto 101 pendiente · Apto 102 al día
                      </span>
                    </div>

                    <div className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60">
                      <div className="flex items-center justify-between">
                        <span className="text-xs text-slate-400 font-medium">Operación de Portería</span>
                        <ShieldCheck className="w-4 h-4 text-emerald-400" />
                      </div>
                      <p className="text-2xl font-bold text-emerald-400 mt-1.5">Activa & Trazable</p>
                      <span className="text-xs text-slate-400 mt-1 block">
                        Bitácora digital en tiempo real
                      </span>
                    </div>
                  </div>

                  {/* Operational Ledger Table */}
                  <div className="rounded-xl border border-slate-700/60 bg-slate-900/80 overflow-hidden">
                    <div className="px-4 py-3 bg-slate-800/50 border-b border-slate-700/60 flex justify-between items-center text-xs">
                      <span className="font-bold text-slate-200 uppercase tracking-wider">
                        Estado de Cartera por Apartamento
                      </span>
                      <span className="text-emerald-400 font-medium flex items-center gap-1">
                        <CheckCircle2 className="w-3 h-3" /> Pasarela Wompi integrada
                      </span>
                    </div>
                    <div className="divide-y divide-slate-800 text-xs">
                      <div className="px-4 py-3 flex items-center justify-between hover:bg-white/[0.02] transition-colors">
                        <div className="flex items-center gap-2.5">
                          <span className="font-bold text-white">Apto 101</span>
                          <span className="text-slate-400">Carlos Martinez</span>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="text-amber-300 font-semibold">$250.000 COP</span>
                          <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/25">
                            Pendiente
                          </span>
                        </div>
                      </div>

                      <div className="px-4 py-3 flex items-center justify-between hover:bg-white/[0.02] transition-colors">
                        <div className="flex items-center gap-2.5">
                          <span className="font-bold text-white">Apto 102</span>
                          <span className="text-slate-400">Ana Gomez</span>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="text-emerald-400 font-semibold">$0 COP</span>
                          <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/25">
                            Al Día · PSE
                          </span>
                        </div>
                      </div>

                      <div className="px-4 py-3 flex items-center justify-between hover:bg-white/[0.02] transition-colors">
                        <div className="flex items-center gap-2.5">
                          <span className="font-bold text-white">Apto 201</span>
                          <span className="text-slate-400">Propietario 201</span>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="text-emerald-400 font-semibold">$0 COP</span>
                          <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/25">
                            Al Día
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* VIEW 2: Portería & QR */}
              {activeTab === 'qr' && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 animate-fadeIn" role="tabpanel">
                  {/* Digital Visitor Pass */}
                  <div className="p-4 sm:p-5 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-3.5">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-bold text-emerald-400 uppercase tracking-wider">
                        Pase Digital de Visitante
                      </span>
                      <span className="px-2 py-0.5 rounded text-xs font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                        ACTIVO
                      </span>
                    </div>

                    <div className="flex items-center gap-4 bg-slate-900/80 p-3.5 rounded-lg border border-slate-700/50">
                      <div className="w-16 h-16 bg-white p-1 rounded-lg flex items-center justify-center shrink-0">
                        <QrCode className="w-14 h-14 text-slate-950" />
                      </div>
                      <div className="space-y-1 text-xs">
                        <p className="font-bold text-white">Visitante Demo</p>
                        <p className="text-slate-400">C.C. 1000000010</p>
                        <p className="text-[11px] text-emerald-400 font-mono font-semibold">SAED-DEMO-QR-2026-TOKEN</p>
                      </div>
                    </div>

                    <div className="text-xs text-slate-400 flex items-center gap-2 pt-1">
                      <Clock className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                      <span>Destino: Apartamento 101 (Carlos Martinez)</span>
                    </div>
                  </div>

                  {/* Guard Station Scanner Outcome */}
                  <div className="p-4 sm:p-5 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-3.5 flex flex-col justify-between">
                    <div>
                      <span className="text-xs font-bold text-slate-300 uppercase tracking-wider block mb-2.5">
                        Consola de Garita · Validación
                      </span>
                      <div className="space-y-2.5 text-xs">
                        <div className="flex justify-between py-1 border-b border-slate-700/50">
                          <span className="text-slate-400">Validación de Token:</span>
                          <span className="text-emerald-400 font-semibold flex items-center gap-1">
                            <CheckCircle2 className="w-3.5 h-3.5" /> Válido & Autorizado
                          </span>
                        </div>
                        <div className="flex justify-between py-1 border-b border-slate-700/50">
                          <span className="text-slate-400">Medio de Transporte:</span>
                          <span className="text-white font-medium">Vehículo Particular</span>
                        </div>
                        <div className="flex justify-between py-1 border-b border-slate-700/50">
                          <span className="text-slate-400">Placa Registrada:</span>
                          <span className="text-amber-300 font-mono font-bold">DEM-123</span>
                        </div>
                        <div className="flex justify-between py-1 border-b border-slate-700/50">
                          <span className="text-slate-400">Parqueadero Asignado:</span>
                          <span className="text-teal-400 font-bold">V-01 (Visitantes)</span>
                        </div>
                      </div>
                    </div>

                    <div className="pt-2">
                      <div className="w-full py-2 rounded-lg bg-emerald-600/20 text-emerald-300 border border-emerald-500/30 text-center text-xs font-bold tracking-wide">
                        Acceso Concedido · Registro Asentado
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* VIEW 3: Paquetes & Puestos */}
              {activeTab === 'operaciones' && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 animate-fadeIn" role="tabpanel">
                  {/* Paquetería Card */}
                  <div className="p-4 sm:p-5 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-3.5">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Package className="w-4 h-4 text-teal-400" />
                        <span className="text-xs font-bold text-white">Custodia de Paquetería</span>
                      </div>
                      <span className="px-2 py-0.5 rounded text-xs font-bold bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                        RECIBIDO
                      </span>
                    </div>

                    <div className="bg-slate-900/70 p-3.5 rounded-lg space-y-2 text-xs">
                      <div className="flex justify-between">
                        <span className="text-slate-400">Destinatario:</span>
                        <span className="text-white font-medium">Apto 101 · C. Martinez</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-slate-400">Empresa:</span>
                        <span className="text-white">Servientrega (Guía AMZ-9988)</span>
                      </div>
                      <div className="flex justify-between items-center pt-1.5 border-t border-slate-800">
                        <span className="text-slate-400">PIN de Retiro:</span>
                        <span className="font-mono text-xs font-bold text-emerald-400 tracking-wider bg-emerald-950/60 px-2.5 py-0.5 rounded border border-emerald-500/30">
                          482910
                        </span>
                      </div>
                    </div>

                    <p className="text-[11px] text-slate-400 leading-relaxed">
                      Entrega verificada exclusivamente cuando el residente presenta o dicta el PIN en garita.
                    </p>
                  </div>

                  {/* Parqueaderos Card */}
                  <div className="p-4 sm:p-5 rounded-xl bg-slate-800/60 border border-slate-700/60 space-y-3.5">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Car className="w-4 h-4 text-blue-400" />
                        <span className="text-xs font-bold text-white">Parqueaderos de Visitantes</span>
                      </div>
                      <span className="text-xs text-slate-400">En tiempo real</span>
                    </div>

                    <div className="space-y-2 text-xs">
                      <div className="p-2.5 rounded-lg bg-slate-900/80 border border-slate-800 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="w-2 h-2 rounded-full bg-rose-500" />
                          <span className="font-bold text-white">Puesto V-01</span>
                          <span className="text-slate-400">Visitantes</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-amber-300 font-semibold">DEM-123</span>
                          <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-rose-500/15 text-rose-400 border border-rose-500/25">
                            OCUPADO
                          </span>
                        </div>
                      </div>

                      <div className="p-2.5 rounded-lg bg-slate-900/80 border border-slate-800 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <span className="w-2 h-2 rounded-full bg-emerald-500" />
                          <span className="font-bold text-white">Puesto V-02</span>
                          <span className="text-slate-400">Visitantes</span>
                        </div>
                        <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/25">
                          DISPONIBLE
                        </span>
                      </div>
                    </div>

                    <p className="text-[11px] text-slate-400 leading-relaxed">
                      El cupo se libera de forma automática al registrar la salida del vehículo en la consola.
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
