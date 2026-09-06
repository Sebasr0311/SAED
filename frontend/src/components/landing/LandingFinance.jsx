import { useState, useRef } from 'react';
import {
  CreditCard,
  ShieldCheck,
  FileCheck,
  CheckCircle2,
  Sparkles,
  Zap,
  RotateCcw,
  ArrowRight,
  TrendingDown,
  Building,
  Download,
} from 'lucide-react';
import { animate } from 'animejs';

export default function LandingFinance() {
  const [isPaid, setIsPaid] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const cardRef = useRef(null);

  const handlePayWompi = () => {
    setIsProcessing(true);
    setTimeout(() => {
      setIsProcessing(false);
      setIsPaid(true);

      if (cardRef.current) {
        animate(cardRef.current, {
          scale: [0.97, 1],
          opacity: [0.4, 1],
          duration: 450,
          ease: 'outExpo',
        });
      }
    }, 900);
  };

  const handleReset = () => {
    setIsPaid(false);
  };

  const recaudadoTotal = isPaid ? 48450000 : 48200000;
  const carteraPendiente = isPaid ? 0 : 250000;
  const aptosAlDia = isPaid ? 123 : 122;

  return (
    <section
      id="finanzas"
      className="py-20 sm:py-28 lg:py-32 bg-[#0A1628] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      <div className="absolute top-1/2 left-1/4 w-[700px] h-[360px] bg-amber-500/5 blur-[160px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-14 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-amber-400 bg-amber-500/10 border border-amber-500/20">
            <Sparkles className="w-3.5 h-3.5 text-amber-400" />
            GESTIÓN DE CARTERA Y RECAUDO EN LÍNEA
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Las finanzas también forman parte de la operación.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            De los comprobantes perdidos en chats de WhatsApp a la conciliación bancaria instantánea. Cobro de cuotas ordinarias y extraordinarias con pasarela oficial integrada.
          </p>
        </div>

        {/* Big Product Financial Split Layout */}
        <div className="max-w-5xl mx-auto p-6 sm:p-10 rounded-3xl bg-slate-900/90 border border-slate-800 shadow-2xl backdrop-blur-xl space-y-8">
          
          {/* Header Bar with Live Simulation Action */}
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
              <span className="px-3 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-semibold flex items-center gap-1.5">
                <ShieldCheck className="w-3.5 h-3.5" />
                <span>Integración Oficial Wompi (Bancolombia)</span>
              </span>
            </div>
          </div>

          {/* 3 Real Metrics Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 space-y-1.5">
              <span className="text-xs text-slate-400">Recaudado Mes Actual</span>
              <div className="text-2xl font-bold font-mono text-white">
                ${recaudadoTotal.toLocaleString('es-CO')}
              </div>
              <span className="text-[11px] text-emerald-400 font-semibold block">
                {isPaid ? '100% del presupuesto mensual' : '94% del presupuesto mensual'}
              </span>
            </div>

            <div className={`p-5 rounded-2xl bg-slate-950 border space-y-1.5 transition-colors ${carteraPendiente === 0 ? 'border-emerald-500/30' : 'border-amber-500/30'}`}>
              <span className="text-xs text-slate-400">Cartera Pendiente Demo</span>
              <div className={`text-2xl font-bold font-mono ${carteraPendiente === 0 ? 'text-emerald-400' : 'text-amber-400'}`}>
                ${carteraPendiente.toLocaleString('es-CO')}
              </div>
              <span className="text-[11px] text-slate-400 font-semibold block">
                {carteraPendiente === 0 ? '¡Sin cuotas vencidas!' : 'Apto 204 (1 cuota vencida)'}
              </span>
            </div>

            <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 space-y-1.5">
              <span className="text-xs text-slate-400">Certificados Emitidos</span>
              <div className="text-2xl font-bold font-mono text-white">
                {aptosAlDia} Unidades
              </div>
              <span className="text-[11px] text-sky-400 font-semibold block">Paz y salvo automático</span>
            </div>
          </div>

          {/* Interactive Simulation Action Box */}
          <div ref={cardRef} className="p-5 rounded-2xl bg-slate-950/80 border border-slate-800 space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="space-y-0.5">
                <span className="text-xs font-bold text-white flex items-center gap-2">
                  <Zap className="w-4 h-4 text-amber-400" />
                  <span>Simulador de Pago de Cuota en Línea (Wompi PSE)</span>
                </span>
                <p className="text-[11px] text-slate-400">
                  Experimenta cómo el habitante del Apto 204 liquida su cuota de administración y genera su paz y salvo al instante.
                </p>
              </div>

              {!isPaid ? (
                <button
                  type="button"
                  onClick={handlePayWompi}
                  disabled={isProcessing}
                  className="px-4 py-2.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-bold text-xs shadow-md shadow-emerald-950/50 flex items-center gap-2 transition-all transform active:scale-95 disabled:opacity-50 shrink-0"
                >
                  <CreditCard className="w-3.5 h-3.5" />
                  <span>{isProcessing ? 'Procesando con Bancolombia...' : 'Simular Pago PSE ($250.000 COP)'}</span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={handleReset}
                  className="px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold text-xs flex items-center gap-1.5 transition-colors shrink-0"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Restablecer Demostración</span>
                </button>
              )}
            </div>

            {/* If Paid: Digital Paz y Salvo Receipt */}
            {isPaid && (
              <div className="p-4 rounded-xl bg-emerald-950/20 border border-emerald-500/40 flex flex-col sm:flex-row sm:items-center justify-between gap-3 animate-in fade-in duration-300">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-lg bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shrink-0">
                    <FileCheck className="w-5 h-5" />
                  </div>
                  <div className="text-xs">
                    <p className="font-bold text-white">Certificado de Paz y Salvo Generado Automáticamente</p>
                    <p className="text-[11px] text-slate-300">
                      ID: #PYS-2026-0921 · Apto 204 · Firma Digital Criptográfica HMAC-SHA256
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2 text-xs text-emerald-400 font-semibold">
                  <CheckCircle2 className="w-4 h-4" />
                  <span>Conciliado 100% en Banco</span>
                </div>
              </div>
            )}
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
                <span className="font-mono font-bold text-emerald-400 text-sm mt-0.5 block">
                  ${recaudadoTotal.toLocaleString('es-CO')}
                </span>
                <span className="text-[10px] text-slate-500">{aptosAlDia} Apartamentos</span>
              </div>
              <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                <span className="text-slate-400 block text-[11px]">30 Días</span>
                <span className="font-mono font-bold text-amber-400 text-sm mt-0.5 block">$1.200.000</span>
                <span className="text-[10px] text-slate-500">3 Apartamentos</span>
              </div>
              <div className="p-3 rounded-xl bg-slate-900 border border-slate-800">
                <span className="text-slate-400 block text-[11px]">60+ Días</span>
                <span className={`font-mono font-bold text-sm mt-0.5 block ${carteraPendiente === 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                  ${carteraPendiente.toLocaleString('es-CO')}
                </span>
                <span className="text-[10px] text-slate-500">
                  {carteraPendiente === 0 ? '0 Apartamentos' : '1 Apartamento (Demo)'}
                </span>
              </div>
            </div>
          </div>

          {/* Wompi Integration Guarantees */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
            <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
              <div className="flex items-center gap-2 text-white font-bold">
                <ShieldCheck className="w-4 h-4 text-emerald-400" />
                <span>Pasarela Oficial Wompi</span>
              </div>
              <p className="text-slate-400 leading-relaxed">
                Recaudo directo a la cuenta bancaria de la copropiedad mediante PSE, Bancolombia, Nequi y tarjetas de crédito.
              </p>
            </div>

            <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
              <div className="flex items-center gap-2 text-white font-bold">
                <CheckCircle2 className="w-4 h-4 text-sky-400" />
                <span>Conciliación Instantánea</span>
              </div>
              <p className="text-slate-400 leading-relaxed">
                Cada pago actualiza el saldo de la unidad en milisegundos sin requerir cruces manuales de extractos bancarios.
              </p>
            </div>

            <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-1.5">
              <div className="flex items-center gap-2 text-white font-bold">
                <FileCheck className="w-4 h-4 text-amber-400" />
                <span>Paz y Salvo Inmediato</span>
              </div>
              <p className="text-slate-400 leading-relaxed">
                El sistema emite el certificado en PDF con firma digital al momento de la liquidación, listo para descarga.
              </p>
            </div>
          </div>

        </div>

      </div>
    </section>
  );
}
