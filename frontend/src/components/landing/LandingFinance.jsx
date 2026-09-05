import { CreditCard, ShieldCheck, FileCheck, CheckCircle2 } from 'lucide-react';

export default function LandingFinance() {
  return (
    <section
      id="finanzas"
      className="py-20 sm:py-28 lg:py-32 bg-[#0F172A] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16 sm:mb-20">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-amber-400 bg-amber-500/10 border border-amber-500/20">
            GESTIÓN DE CARTERA Y RECAUDO
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Las finanzas también forman parte de la operación.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            De los comprobantes perdidos en chats a la conciliación bancaria instantánea. Cobro de cuotas ordinarias y extraordinarias con pasarela de pagos integrada.
          </p>
        </div>

        {/* Big Product Financial Split Layout */}
        <div className="max-w-5xl mx-auto p-6 sm:p-10 rounded-3xl bg-slate-900/90 border border-slate-800 shadow-2xl space-y-8">
          
          {/* Header Bar */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
                <CreditCard className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base sm:text-lg font-bold text-white font-['Plus_Jakarta_Sans']">
                  Consola de Cartera y Conciliación Wompi
                </h3>
                <p className="text-xs text-slate-400">Módulo financiero del administrador de propiedad</p>
              </div>
            </div>

            <div className="flex items-center gap-2 text-xs">
              <span className="px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-semibold">
                Integración Oficial Wompi
              </span>
            </div>
          </div>

          {/* Real Metrics Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 space-y-1.5">
              <span className="text-xs text-slate-400">Recaudado Mes Actual</span>
              <div className="text-2xl font-bold font-mono text-white">$48.200.000</div>
              <span className="text-[11px] text-emerald-400 font-semibold block">94% del presupuesto mensual</span>
            </div>

            <div className="p-5 rounded-2xl bg-slate-950 border border-amber-500/30 space-y-1.5">
              <span className="text-xs text-slate-400">Cartera Pendiente (Demo)</span>
              <div className="text-2xl font-bold font-mono text-amber-400">$250.000</div>
              <span className="text-[11px] text-amber-400 font-semibold block">Apto 204 (1 cuota pendiente)</span>
            </div>

            <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 space-y-1.5">
              <span className="text-xs text-slate-400">Certificados Emitidos</span>
              <div className="text-2xl font-bold font-mono text-white">122 Unidades</div>
              <span className="text-[11px] text-blue-400 font-semibold block">Paz y salvo automático</span>
            </div>
          </div>

          {/* Aging of Debt Analysis */}
          <div className="p-5 rounded-2xl bg-slate-950/70 border border-slate-800 space-y-3">
            <div className="flex items-center justify-between text-xs">
              <span className="font-bold text-slate-300 uppercase tracking-wider">Antigüedad de Saldos (Aging)</span>
              <span className="text-slate-500">Actualizado con cada transacción</span>
            </div>

            <div className="grid grid-cols-3 gap-3 text-xs text-center">
              <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                <span className="text-slate-400 block text-[11px]">Corriente (Al día)</span>
                <span className="font-mono font-bold text-emerald-400 text-sm mt-0.5 block">$48.200.000</span>
                <span className="text-[10px] text-slate-500">122 Apartamentos</span>
              </div>
              <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                <span className="text-slate-400 block text-[11px]">30 Días</span>
                <span className="font-mono font-bold text-amber-400 text-sm mt-0.5 block">$1.200.000</span>
                <span className="text-[10px] text-slate-500">3 Apartamentos</span>
              </div>
              <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                <span className="text-slate-400 block text-[11px]">60+ Días</span>
                <span className="font-mono font-bold text-rose-400 text-sm mt-0.5 block">$250.000</span>
                <span className="text-[10px] text-slate-500">1 Apartamento (Demo)</span>
              </div>
            </div>
          </div>

          {/* Wompi Integration Guarantees */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
            <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
              <div className="flex items-center gap-2 text-white font-bold">
                <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                <span>PSE & Tarjetas Bancarias</span>
              </div>
              <p className="text-slate-400 leading-relaxed">
                El habitante paga directamente desde su cuenta bancaria o tarjeta sin salir de la plataforma.
              </p>
            </div>

            <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
              <div className="flex items-center gap-2 text-white font-bold">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span>Conciliación Instantánea</span>
              </div>
              <p className="text-slate-400 leading-relaxed">
                Los webhooks de Wompi actualizan el saldo de la unidad en milisegundos tras la aprobación del banco.
              </p>
            </div>

            <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
              <div className="flex items-center gap-2 text-white font-bold">
                <FileCheck className="w-4 h-4 text-emerald-400" />
                <span>Paz y Salvo Inmediato</span>
              </div>
              <p className="text-slate-400 leading-relaxed">
                Certificado oficial descargable en PDF sin requerir firmas presenciales ni esperas.
              </p>
            </div>
          </div>

        </div>

      </div>
    </section>
  );
}
