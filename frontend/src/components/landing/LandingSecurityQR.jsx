import { QrCode, ShieldCheck, CheckCircle2, UserCheck, ArrowRight, Smartphone } from 'lucide-react';

const QR_FLOW = [
  {
    step: '01',
    role: 'Residente',
    title: 'Genera el pase',
    desc: 'Registra los datos del invitado en su portal web y define si es de uso único o temporal.',
  },
  {
    step: '02',
    role: 'Visitante',
    title: 'Recibe el código',
    desc: 'Obtiene el pase digital seguro para presentarlo en pantalla al llegar a la garita.',
  },
  {
    step: '03',
    role: 'Portero',
    title: 'Valida en pantalla',
    desc: 'La consola de garita escanea el código, confirma vigencia y muestra la unidad de destino.',
  },
  {
    step: '04',
    role: 'Sistema',
    title: 'Entrada registrada',
    desc: 'Se asienta el ingreso en la bitácora inmutable con operador, placa y parqueadero asignado.',
  },
];

export default function LandingSecurityQR() {
  return (
    <section
      id="seguridad"
      className="py-24 sm:py-32 lg:py-36 bg-[#0A1628] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Editorial Header */}
        <div className="max-w-3xl mx-auto text-center space-y-5">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
            CONTROL DE ACCESO INTELIGENTE
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            El acceso empieza antes de llegar a la portería.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Sustituye llamadas telefónicas e interrupciones por validaciones digitales verificables y trazables en tiempo real.
          </p>
        </div>

        {/* 4-Step Sequence Flow */}
        <div className="mt-16 sm:mt-24 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-6 max-w-6xl mx-auto">
          {QR_FLOW.map((item, idx) => (
            <div
              key={item.step}
              className="p-6 sm:p-7 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-emerald-500/40 transition-colors flex flex-col justify-between"
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-3xl font-extrabold font-mono text-emerald-400">
                    {item.step}
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold uppercase tracking-wider bg-slate-800 text-slate-300 border border-slate-700">
                    {item.role}
                  </span>
                </div>

                <h3 className="text-lg font-bold text-white">{item.title}</h3>
                <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">{item.desc}</p>
              </div>

              <div className="mt-6 pt-3 border-t border-slate-800/80 flex items-center justify-between text-xs font-semibold text-emerald-400">
                <span>Fase {idx + 1}</span>
                {idx < QR_FLOW.length - 1 && <ArrowRight className="w-4 h-4 text-slate-600 hidden lg:block" />}
              </div>
            </div>
          ))}
        </div>

        {/* Grand Visual Flow Mockup */}
        <div className="mt-12 sm:mt-16 max-w-5xl mx-auto rounded-3xl bg-gradient-to-br from-[#0F2044] via-slate-900 to-[#0A1628] border border-emerald-500/30 p-6 sm:p-10 shadow-2xl">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-center">
            {/* Left: Resident View */}
            <div className="bg-slate-950/80 rounded-2xl p-6 border border-slate-800 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <div className="flex items-center gap-2 text-xs font-bold text-slate-200">
                  <Smartphone className="w-4 h-4 text-emerald-400" />
                  <span>Portal del Residente · Apto 101</span>
                </div>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300">
                  Emisión Inmediata
                </span>
              </div>

              <div className="flex items-center gap-4 bg-slate-900/90 p-4 rounded-xl border border-slate-800">
                <div className="w-20 h-20 bg-white rounded-xl p-1.5 flex items-center justify-center shrink-0 shadow-md">
                  <QrCode className="w-full h-full text-slate-950" />
                </div>
                <div className="space-y-1 text-xs">
                  <span className="text-[10px] font-mono text-emerald-400 uppercase tracking-wider block">
                    Pase de Acceso Único
                  </span>
                  <p className="font-bold text-white text-sm">Visitante Demo</p>
                  <p className="text-slate-400">Doc: C.C. 1000000010</p>
                  <p className="text-slate-400">Válido hasta: 11:59 PM (Hoy)</p>
                </div>
              </div>

              <p className="text-xs text-slate-400 leading-relaxed">
                El invitado presenta este código digital sin necesidad de bajarse del vehículo ni dictar datos sensibles en voz alta.
              </p>
            </div>

            {/* Right: Guard Station Verification Outcome */}
            <div className="bg-slate-950/80 rounded-2xl p-6 border border-slate-800 space-y-4">
              <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                <div className="flex items-center gap-2 text-xs font-bold text-slate-200">
                  <ShieldCheck className="w-4 h-4 text-teal-400" />
                  <span>Consola Garita · Validación Instantánea</span>
                </div>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-teal-500/20 text-teal-300">
                  Bitácora OK
                </span>
              </div>

              <div className="space-y-2.5 text-xs bg-slate-900/90 p-4 rounded-xl border border-slate-800">
                <div className="flex justify-between py-1 border-b border-slate-800">
                  <span className="text-slate-400">Resultado de Lectura:</span>
                  <span className="text-emerald-400 font-bold flex items-center gap-1">
                    <CheckCircle2 className="w-3.5 h-3.5" /> Token Válido & Autorizado
                  </span>
                </div>
                <div className="flex justify-between py-1 border-b border-slate-800">
                  <span className="text-slate-400">Unidad Anfitriona:</span>
                  <span className="text-white font-semibold">Apartamento 101 (Torre A)</span>
                </div>
                <div className="flex justify-between py-1 border-b border-slate-800">
                  <span className="text-slate-400">Vehículo Asociado:</span>
                  <span className="text-amber-300 font-mono font-bold">DEM-123</span>
                </div>
                <div className="flex justify-between py-1">
                  <span className="text-slate-400">Puesto Asignado:</span>
                  <span className="text-teal-400 font-bold">V-01 (Visitantes)</span>
                </div>
              </div>

              <div className="flex items-center gap-2 text-[11px] text-slate-400 pt-1">
                <UserCheck className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                <span>Registrado por Portero 01 con sello de fecha y hora exacta.</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
