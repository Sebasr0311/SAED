import { Building2, Users, Shield, Landmark, Lock } from 'lucide-react';

export default function LandingAbout() {
  return (
    <section
      id="que-es-saed"
      className="py-24 sm:py-32 lg:py-36 bg-[#0F172A] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Storytelling Editorial Header */}
        <div className="max-w-3xl mx-auto text-center space-y-5 sm:space-y-6">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
            ARQUITECTURA UNIFICADA
          </span>

          <h2 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Administrar un conjunto no debería requerir cinco sistemas.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Minutas en papel, comprobantes dispersos por chat, llamadas telefónicas interrumpidas y hojas de cálculo desactualizadas. SAED integra cada faceta operativa en una única plataforma en la nube.
          </p>
        </div>

        {/* The Equation: 4 Core Pillars + Unified SAED 2.0 Ecosystem */}
        <div className="mt-16 sm:mt-24 max-w-5xl mx-auto space-y-6">
          {/* 4 Pillars Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6">
            {/* Pillar 1: Administración */}
            <div className="p-6 sm:p-7 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-blue-500/40 transition-colors flex flex-col justify-between group">
              <div className="space-y-3">
                <div className="w-11 h-11 rounded-xl bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400 group-hover:scale-105 transition-transform">
                  <Building2 className="w-5 h-5" />
                </div>
                <h3 className="text-lg font-bold text-white">Administración</h3>
                <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                  Supervisión de propiedades, censo de residentes, contratos y convivencia bajo normativa colombiana.
                </p>
              </div>
              <div className="pt-4 mt-4 border-t border-slate-800/80 text-[11px] font-semibold text-blue-400 uppercase tracking-wider">
                Gobernanza central
              </div>
            </div>

            {/* Pillar 2: Residentes */}
            <div className="p-6 sm:p-7 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-emerald-500/40 transition-colors flex flex-col justify-between group">
              <div className="space-y-3">
                <div className="w-11 h-11 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400 group-hover:scale-105 transition-transform">
                  <Users className="w-5 h-5" />
                </div>
                <h3 className="text-lg font-bold text-white">Residentes</h3>
                <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                  Autonomía web para expedir pases QR, consultar estados de cuenta y recibir notificaciones de paquetería.
                </p>
              </div>
              <div className="pt-4 mt-4 border-t border-slate-800/80 text-[11px] font-semibold text-emerald-400 uppercase tracking-wider">
                Autogestión sin apps
              </div>
            </div>

            {/* Pillar 3: Portería */}
            <div className="p-6 sm:p-7 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-teal-500/40 transition-colors flex flex-col justify-between group">
              <div className="space-y-3">
                <div className="w-11 h-11 rounded-xl bg-teal-500/10 border border-teal-500/20 flex items-center justify-center text-teal-400 group-hover:scale-105 transition-transform">
                  <Shield className="w-5 h-5" />
                </div>
                <h3 className="text-lg font-bold text-white">Portería</h3>
                <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                  Validación QR en garita, registro de placas, control de permanencia y custodia de correspondencia con PIN.
                </p>
              </div>
              <div className="pt-4 mt-4 border-t border-slate-800/80 text-[11px] font-semibold text-teal-400 uppercase tracking-wider">
                Operación en garita
              </div>
            </div>

            {/* Pillar 4: Finanzas */}
            <div className="p-6 sm:p-7 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-amber-500/40 transition-colors flex flex-col justify-between group">
              <div className="space-y-3">
                <div className="w-11 h-11 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400 group-hover:scale-105 transition-transform">
                  <Landmark className="w-5 h-5" />
                </div>
                <h3 className="text-lg font-bold text-white">Finanzas</h3>
                <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">
                  Cartera al día, liquidación de cuotas ordinarias y pasarela de pagos Wompi con conciliación bancaria inmediata.
                </p>
              </div>
              <div className="pt-4 mt-4 border-t border-slate-800/80 text-[11px] font-semibold text-amber-400 uppercase tracking-wider">
                Recaudo digital
              </div>
            </div>
          </div>

          {/* Connected Equal Banner */}
          <div className="p-6 sm:p-8 rounded-2xl bg-gradient-to-r from-emerald-950/70 via-slate-900 to-[#0F2044] border border-emerald-500/30 flex flex-col md:flex-row items-center justify-between gap-6 shadow-xl">
            <div className="flex items-center gap-4">
              <div className="w-12 h-12 rounded-2xl bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center text-emerald-300 font-extrabold text-2xl shrink-0">
                =
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h4 className="text-xl font-bold text-white">SAED 2.0</h4>
                  <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 font-medium">
                    Ecosistema Único
                  </span>
                </div>
                <p className="text-xs sm:text-sm text-slate-300 mt-1 leading-relaxed">
                  Toda la comunidad conectada bajo una misma fuente de verdad, sin duplicar datos ni perder trazabilidad.
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2 text-xs font-semibold text-emerald-400 shrink-0 bg-slate-900/60 px-4 py-2.5 rounded-xl border border-slate-800">
              <Lock className="w-4 h-4 text-emerald-400" />
              <span>Contexto multi-tenant aislado</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
