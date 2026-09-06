import { useState, useRef } from 'react';
import {
  Package,
  KeyRound,
  Bell,
  CheckCircle2,
  ShieldCheck,
  ArrowRight,
  Truck,
  Sparkles,
  Delete,
  RotateCcw,
  Lock,
  Unlock,
  AlertTriangle,
} from 'lucide-react';
import { animate } from 'animejs';

const PACKAGE_FLOW = [
  { step: '01', title: 'Recepción en Garita', desc: 'El guardia registra la empresa transportadora, guía y apartamento destino.' },
  { step: '02', title: 'Casillero y Custodia', desc: 'El paquete queda resguardado físicamente en el casillero numerado de garita.' },
  { step: '03', title: 'PIN Criptográfico', desc: 'El sistema genera una clave única de 6 dígitos visible solo para el residente.' },
  { step: '04', title: 'Alerta en Portal', desc: 'El residente recibe la notificación oficial en su teléfono o computador.' },
  { step: '05', title: 'Entrega Verificada', desc: 'El residente dicta su PIN al retirar. El sistema valida y asienta la entrega.' },
];

const CORRECT_PIN = '849201';

export default function LandingPackages() {
  const [pinInput, setPinInput] = useState('');
  const [deliveryState, setDeliveryState] = useState('pending'); // 'pending' | 'success' | 'error'
  const keypadRef = useRef(null);
  const resultRef = useRef(null);

  const handleDigit = (digit) => {
    if (deliveryState === 'success') return;
    if (pinInput.length < 6) {
      const next = pinInput + digit;
      setPinInput(next);
      if (next.length === 6) {
        verifyPin(next);
      }
    }
  };

  const handleDelete = () => {
    if (deliveryState === 'success') return;
    setPinInput((prev) => prev.slice(0, -1));
    setDeliveryState('pending');
  };

  const handleReset = () => {
    setPinInput('');
    setDeliveryState('pending');
  };

  const verifyPin = (candidate) => {
    if (candidate === CORRECT_PIN) {
      setDeliveryState('success');
      if (resultRef.current) {
        animate(resultRef.current, {
          scale: [0.95, 1],
          opacity: [0.4, 1],
          duration: 400,
          ease: 'outExpo',
        });
      }
    } else {
      setDeliveryState('error');
      if (keypadRef.current) {
        animate(keypadRef.current, {
          translateX: [-10, 10, -6, 6, -3, 3, 0],
          duration: 450,
          ease: 'easeInOutQuad',
        });
      }
    }
  };

  const handleQuickSuccess = () => {
    setPinInput(CORRECT_PIN);
    verifyPin(CORRECT_PIN);
  };

  const handleQuickFail = () => {
    const wrong = '123456';
    setPinInput(wrong);
    verifyPin(wrong);
  };

  return (
    <section
      id="paqueteria"
      className="py-20 sm:py-28 lg:py-32 bg-[#0A1628] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      <div className="absolute top-1/2 left-1/3 w-[650px] h-[360px] bg-teal-500/5 blur-[160px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-14 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-teal-400 bg-teal-500/10 border border-teal-500/20">
            <Sparkles className="w-3.5 h-3.5 text-teal-400" />
            LOGÍSTICA DE PAQUETERÍA Y CORRESPONDENCIA
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Cada encomienda tiene un recorrido trazable.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Las compras en línea dejan de ser un dolor de cabeza en garita. Cero entregas por confusión con un protocolo seguro de retiro mediante PIN criptográfico.
          </p>
        </div>

        {/* 5-Step Linear Sequence Flow */}
        <div className="grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-5 gap-3.5 max-w-6xl mx-auto mb-16">
          {PACKAGE_FLOW.map((f, idx) => (
            <div
              key={f.step}
              className="p-5 rounded-2xl bg-slate-900/70 border border-slate-800/90 hover:border-teal-500/30 transition-all flex flex-col justify-between space-y-3 group"
            >
              <div className="flex items-center justify-between">
                <span className="text-2xl font-black font-mono text-teal-400 group-hover:scale-110 transition-transform">
                  {f.step}
                </span>
                {idx < PACKAGE_FLOW.length - 1 && (
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
        {/* Interactive PIN Verification Simulator */}
        {/* ========================================================================= */}
        <div className="max-w-5xl mx-auto p-6 sm:p-10 rounded-3xl bg-slate-900/80 border border-slate-800 shadow-2xl backdrop-blur-xl space-y-6">
          
          {/* Header Bar */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
            <div>
              <h3 className="text-base sm:text-lg font-bold text-white font-['Plus_Jakarta_Sans'] flex items-center gap-2">
                <KeyRound className="w-5 h-5 text-teal-400" />
                <span>Simulador Interactivo de Retiro en Garita</span>
              </h3>
              <p className="text-xs text-slate-400">Ingresa los 6 dígitos del PIN asignado al paquete</p>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleQuickSuccess}
                className="px-3 py-1.5 rounded-xl bg-teal-500/20 text-teal-300 border border-teal-500/30 text-xs font-bold hover:bg-teal-500/30 transition-colors"
              >
                Probar PIN Válido (849201)
              </button>
              <button
                type="button"
                onClick={handleQuickFail}
                className="px-3 py-1.5 rounded-xl bg-rose-500/20 text-rose-300 border border-rose-500/30 text-xs font-bold hover:bg-rose-500/30 transition-colors"
              >
                Probar PIN Incorrecto
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-12 gap-8 items-center pt-2">
            
            {/* Left: Custody Package Card & Keypad */}
            <div className="md:col-span-6 bg-slate-950 p-6 rounded-2xl border border-slate-800 space-y-4 shadow-inner">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-teal-500/10 border border-teal-500/20 flex items-center justify-center text-teal-400">
                    <Package className="w-4 h-4" />
                  </div>
                  <span className="text-xs font-bold text-white uppercase tracking-wider">
                    Ficha de Encomienda · Casillero #14
                  </span>
                </div>
                <span
                  className={`text-[10px] px-2.5 py-0.5 rounded-full font-semibold border ${
                    deliveryState === 'success'
                      ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                      : 'bg-amber-500/10 text-amber-400 border-amber-500/30'
                  }`}
                >
                  {deliveryState === 'success' ? 'Entregado a Residente' : 'En Custodia'}
                </span>
              </div>

              <div className="space-y-1.5 text-xs">
                <div className="flex justify-between py-1 border-b border-slate-900">
                  <span className="text-slate-400">Transportadora:</span>
                  <span className="font-bold text-white flex items-center gap-1.5">
                    <Truck className="w-3.5 h-3.5 text-teal-400" />
                    Servientrega
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-slate-900">
                  <span className="text-slate-400">Guía de mensajería:</span>
                  <span className="font-mono text-white">#AMZ-889021-CO</span>
                </div>
                <div className="flex justify-between py-1 border-b border-slate-900">
                  <span className="text-slate-400">Unidad destino:</span>
                  <span className="font-bold text-teal-300">Apto 101 · Torre 1</span>
                </div>
                <div className="flex justify-between py-1">
                  <span className="text-slate-400">Destinatario registrado:</span>
                  <span className="text-slate-200">Carlos Martínez</span>
                </div>
              </div>

              {/* Digits Display Box */}
              <div className="p-4 rounded-xl bg-slate-900/90 border border-slate-800 text-center space-y-2">
                <span className="text-[11px] font-mono text-slate-400 uppercase tracking-wider block">
                  PIN Digitado por Portero:
                </span>
                
                {/* 6 Digit Cells */}
                <div className="flex items-center justify-center gap-2">
                  {[0, 1, 2, 3, 4, 5].map((i) => {
                    const char = pinInput[i];
                    return (
                      <div
                        key={i}
                        className={`w-9 h-11 rounded-lg border flex items-center justify-center font-mono text-lg font-bold transition-all ${
                          char
                            ? 'bg-teal-950/60 border-teal-400 text-teal-300 shadow-sm shadow-teal-500/20'
                            : 'bg-slate-950 border-slate-800 text-slate-600'
                        }`}
                      >
                        {char || '·'}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Interactive Keypad */}
              <div ref={keypadRef} className="grid grid-cols-3 gap-2 pt-1">
                {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((digit) => (
                  <button
                    key={digit}
                    type="button"
                    onClick={() => handleDigit(String(digit))}
                    className="py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 hover:border-teal-500/40 text-white font-mono font-bold text-sm transition-all transform active:scale-95 min-h-[42px]"
                  >
                    {digit}
                  </button>
                ))}
                <button
                  type="button"
                  onClick={handleReset}
                  className="py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-400 hover:text-white text-xs font-semibold flex items-center justify-center gap-1 min-h-[42px]"
                  title="Reiniciar"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                  <span>Limpiar</span>
                </button>
                <button
                  type="button"
                  onClick={() => handleDigit('0')}
                  className="py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 hover:border-teal-500/40 text-white font-mono font-bold text-sm transition-all transform active:scale-95 min-h-[42px]"
                >
                  0
                </button>
                <button
                  type="button"
                  onClick={handleDelete}
                  className="py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-400 hover:text-white text-xs font-semibold flex items-center justify-center gap-1 min-h-[42px]"
                  title="Borrar dígito"
                >
                  <Delete className="w-3.5 h-3.5" />
                  <span>Borrar</span>
                </button>
              </div>

            </div>

            {/* Right: Real-time Verification Result HUD */}
            <div ref={resultRef} className="md:col-span-6 space-y-5">
              {deliveryState === 'success' ? (
                <div className="space-y-4 p-5 rounded-2xl bg-emerald-950/20 border border-emerald-500/40">
                  <div className="flex items-center gap-2.5 text-emerald-400">
                    <div className="w-9 h-9 rounded-xl bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center">
                      <Unlock className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="text-base font-bold text-white font-['Plus_Jakarta_Sans']">
                        PIN Verificado · Entrega Autorizada
                      </h4>
                      <span className="text-xs text-emerald-300 font-mono font-semibold">200 OK · Clave Consumida</span>
                    </div>
                  </div>

                  <p className="text-xs text-slate-300 leading-relaxed">
                    El paquete #AMZ-889021-CO fue entregado formalmente a Carlos Martínez. El PIN 849201 queda anulado de inmediato para prevenir reutilizaciones.
                  </p>

                  <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 space-y-1 text-xs">
                    <div className="flex justify-between text-slate-400">
                      <span>Operador en garita:</span>
                      <span className="text-white font-medium">Carlos Mendoza (Portería 1)</span>
                    </div>
                    <div className="flex justify-between text-slate-400">
                      <span>Timestamp de entrega:</span>
                      <span className="font-mono text-emerald-400">Hoy · 11:24:08 AM</span>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={handleReset}
                    className="w-full py-2.5 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-bold text-white transition-colors"
                  >
                    Simular Otra Recepción
                  </button>
                </div>
              ) : deliveryState === 'error' ? (
                <div className="space-y-4 p-5 rounded-2xl bg-rose-950/20 border border-rose-500/40">
                  <div className="flex items-center gap-2.5 text-rose-400">
                    <div className="w-9 h-9 rounded-xl bg-rose-500/20 border border-rose-500/30 flex items-center justify-center">
                      <Lock className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="text-base font-bold text-white font-['Plus_Jakarta_Sans']">
                        PIN Inválido · Paquete Retenido
                      </h4>
                      <span className="text-xs text-rose-400 font-mono font-semibold">403 Acceso Denegado</span>
                    </div>
                  </div>

                  <p className="text-xs text-slate-300 leading-relaxed">
                    El PIN ingresado no coincide con el emitido criptográficamente para la unidad Apto 101. La entrega queda bloqueada en el sistema.
                  </p>

                  <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 text-xs text-slate-400 flex items-center gap-2">
                    <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0" />
                    <span>Intento erróneo asentado en la bitácora con IP y operador en turno.</span>
                  </div>

                  <button
                    type="button"
                    onClick={handleReset}
                    className="w-full py-2.5 px-4 rounded-xl bg-slate-800 hover:bg-slate-700 text-xs font-bold text-white transition-colors"
                  >
                    Intentar Nuevamente
                  </button>
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="space-y-2">
                    <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-teal-500/10 text-teal-400 border border-teal-500/20 text-xs font-semibold">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>Protocolo de Entrega Segura</span>
                    </div>
                    <h3 className="text-xl sm:text-2xl font-bold text-white font-['Plus_Jakarta_Sans']">
                      Garantía contra entregas equivocadas
                    </h3>
                    <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                      El operador de garita no puede marcar un paquete como 'Entregado' en el sistema sin ingresar el PIN de 6 dígitos que el residente le proporciona en persona al acercarse a portería.
                    </p>
                  </div>

                  <div className="space-y-2 text-xs">
                    <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center gap-3">
                      <Bell className="w-4 h-4 text-teal-400 shrink-0" />
                      <span className="text-slate-300">
                        Notificación instantánea en el casillero web del apartamento sin spam en WhatsApp.
                      </span>
                    </div>
                    <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center gap-3">
                      <ShieldCheck className="w-4 h-4 text-teal-400 shrink-0" />
                      <span className="text-slate-300">
                        Registro fotográfico opcional del comprobante físico para máxima auditoría.
                      </span>
                    </div>
                    <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center gap-3">
                      <KeyRound className="w-4 h-4 text-teal-400 shrink-0" />
                      <span className="text-slate-300">
                        Clave criptográfica de un solo uso que se anula automáticamente al retirar.
                      </span>
                    </div>
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
