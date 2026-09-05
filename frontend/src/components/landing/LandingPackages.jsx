import { Package, KeyRound, Bell, CheckCircle2, ShieldCheck, ArrowRight, Truck } from 'lucide-react';

const PACKAGE_FLOW = [
  { step: '01', title: 'Recepción', desc: 'Garita registra la empresa de mensajería y la guía' },
  { step: '02', title: 'Custodia', desc: 'El paquete se ubica en el casillero físico de portería' },
  { step: '03', title: 'PIN Cifrado', desc: 'El sistema genera una clave única de 6 dígitos' },
  { step: '04', title: 'Notificación', desc: 'El residente recibe la alerta en su portal web' },
  { step: '05', title: 'Entrega', desc: 'Validación obligatoria del PIN antes de entregar' },
];

export default function LandingPackages() {
  return (
    <section
      id="paqueteria"
      className="py-20 sm:py-28 lg:py-32 bg-[#0F172A] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16 sm:mb-20">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-teal-400 bg-teal-500/10 border border-teal-500/20">
            LOGÍSTICA DE PAQUETERÍA
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Cada paquete tiene un recorrido.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            La correspondencia y las compras en línea dejan de ser un dolor de cabeza en la portería con un protocolo digital verificable con PIN de entrega.
          </p>
        </div>

        {/* 5-Step Linear Sequence Flow */}
        <div className="grid grid-cols-1 sm:grid-cols-3 lg:grid-cols-5 gap-3 sm:gap-4 max-w-6xl mx-auto mb-16">
          {PACKAGE_FLOW.map((f, idx) => (
            <div
              key={f.step}
              className="p-5 rounded-2xl bg-slate-900/80 border border-slate-800 flex flex-col justify-between space-y-3"
            >
              <div className="flex items-center justify-between">
                <span className="text-2xl font-black font-mono text-teal-400">{f.step}</span>
                {idx < PACKAGE_FLOW.length - 1 && (
                  <ArrowRight className="w-4 h-4 text-slate-600 hidden lg:block" />
                )}
              </div>
              <div>
                <h3 className="text-sm font-bold text-white font-['Plus_Jakarta_Sans']">{f.title}</h3>
                <p className="text-xs text-slate-400 mt-1 leading-relaxed">{f.desc}</p>
              </div>
            </div>
          ))}
        </div>

        {/* Big Product Visual Split Layout */}
        <div className="max-w-5xl mx-auto p-6 sm:p-10 rounded-3xl bg-slate-900/90 border border-slate-800 shadow-2xl">
          <div className="grid grid-cols-1 md:grid-cols-12 gap-8 items-center">
            
            {/* Left: Realistic Package Details Card */}
            <div className="md:col-span-6 bg-slate-950 p-6 rounded-2xl border border-slate-800 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg bg-teal-500/10 border border-teal-500/20 flex items-center justify-center text-teal-400">
                    <Package className="w-4 h-4" />
                  </div>
                  <span className="text-xs font-bold text-white uppercase tracking-wider">
                    Ficha de Custodia · Garita
                  </span>
                </div>
                <span className="text-[10px] px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-400 border border-amber-500/20 font-semibold">
                  En Custodia
                </span>
              </div>

              <div className="space-y-2 text-xs">
                <div className="flex justify-between py-1 border-b border-slate-900">
                  <span className="text-slate-400">Transportadora:</span>
                  <span className="font-bold text-white flex items-center gap-1.5">
                    <Truck className="w-3.5 h-3.5 text-teal-400" />
                    Servientrega
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-slate-900">
                  <span className="text-slate-400">Guía de envío:</span>
                  <span className="font-mono text-white">#AMZ-889021-CO</span>
                </div>
                <div className="flex justify-between py-1 border-b border-slate-900">
                  <span className="text-slate-400">Unidad destino:</span>
                  <span className="font-bold text-teal-300">Apartamento 101 (Torre 1)</span>
                </div>
                <div className="flex justify-between py-1">
                  <span className="text-slate-400">Destinatario registrado:</span>
                  <span className="text-slate-200">Carlos Martínez</span>
                </div>
              </div>

              {/* Hashed PIN box */}
              <div className="p-4 rounded-xl bg-teal-950/40 border border-teal-500/30 text-center space-y-1.5">
                <div className="flex items-center justify-center gap-1.5 text-xs text-teal-300 font-semibold">
                  <KeyRound className="w-3.5 h-3.5" />
                  <span>PIN ÚNICO DE RETIRO REQUERIDO</span>
                </div>
                <div className="font-mono text-xl font-black text-white tracking-[0.3em] bg-slate-900/90 py-2 px-4 rounded-lg border border-teal-500/40 inline-block">
                  8 4 9 2 0 1
                </div>
                <p className="text-[10px] text-slate-400">
                  Visible únicamente en el portal personal del habitante
                </p>
              </div>
            </div>

            {/* Right: Operational Guarantees */}
            <div className="md:col-span-6 space-y-5">
              <div className="space-y-2">
                <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-teal-500/10 text-teal-400 border border-teal-500/20 text-xs font-semibold">
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>Protocolo de Entrega Segura</span>
                </div>
                <h3 className="text-xl sm:text-2xl font-bold text-white font-['Plus_Jakarta_Sans']">
                  Garantía contra entregas equivocadas
                </h3>
                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                  El operador de garita no puede marcar un paquete como 'Entregado' en el sistema sin ingresar el PIN que el residente le proporciona en persona.
                </p>
              </div>

              <div className="space-y-2 text-xs">
                <div className="p-3 rounded-xl bg-slate-950/70 border border-slate-800 flex items-center gap-3">
                  <Bell className="w-4 h-4 text-teal-400 shrink-0" />
                  <span className="text-slate-300">
                    Notificación instantánea en el casillero web del apartamento sin spam.
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

          </div>
        </div>

      </div>
    </section>
  );
}
