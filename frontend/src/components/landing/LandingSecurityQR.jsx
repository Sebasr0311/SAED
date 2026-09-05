import { ShieldCheck, ArrowRight, UserCheck, Lock } from 'lucide-react';

const QR_STEPS = [
  {
    step: '01',
    title: 'Residente genera el pase',
    desc: 'Ingresa los datos del invitado en su portal y define vigencia temporal o número de usos.',
    tag: 'Portal Residente',
  },
  {
    step: '02',
    title: 'Visitante recibe la invitación',
    desc: 'Recibe el enlace o código digital para presentarlo en la portería al momento de llegar.',
    tag: 'Pase Digital',
  },
  {
    step: '03',
    title: 'Portería valida en pantalla',
    desc: 'El guardia escanea el código en la consola web, confirmando validez y unidad anfitriona.',
    tag: 'Escáner Garita',
  },
  {
    step: '04',
    title: 'Registro y acceso autorizado',
    desc: 'Se asocia la placa vehicular si aplica, asignando parqueadero y sellando la bitácora.',
    tag: 'Acceso Concedido',
  },
];

export default function LandingSecurityQR() {
  return (
    <section id="seguridad" className="py-20 bg-[#0A1628] text-white relative border-t border-slate-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center max-w-3xl mx-auto space-y-4">
          <span className="text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 px-3 py-1 rounded-full border border-emerald-500/20 inline-block">
            SEGURIDAD DIGITAL & TRAZABILIDAD
          </span>
          <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight font-['Plus_Jakarta_Sans']">
            Control de acceso inteligente con código QR
          </h2>
          <p className="text-base sm:text-lg text-slate-300 leading-relaxed">
            Elimina las llamadas telefónicas interminables y las minutas físicas. Cada ingreso cuenta con registro de fecha, hora y trazabilidad en tiempo real.
          </p>
        </div>

        {/* Step-by-Step Flow */}
        <div className="mt-14 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {QR_STEPS.map((item, idx) => (
            <div
              key={item.step}
              className="relative p-6 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-emerald-500/40 transition-colors flex flex-col justify-between"
            >
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-2xl font-extrabold text-emerald-400/80 font-mono">
                    {item.step}
                  </span>
                  <span className="text-xs font-semibold uppercase px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                    {item.tag}
                  </span>
                </div>

                <h3 className="text-base font-bold text-white">{item.title}</h3>
                <p className="text-xs sm:text-sm text-slate-400 leading-relaxed">{item.desc}</p>
              </div>

              <div className="mt-4 pt-3 border-t border-slate-800/60 flex items-center justify-between text-xs text-emerald-400 font-semibold">
                <span>Paso {idx + 1}</span>
                {idx < QR_STEPS.length - 1 && <ArrowRight className="w-3.5 h-3.5 text-slate-500 hidden lg:block" />}
              </div>
            </div>
          ))}
        </div>

        {/* Security Highlights Banner */}
        <div className="mt-12 p-6 sm:p-8 rounded-2xl bg-gradient-to-r from-emerald-950/40 via-slate-900/80 to-[#0F2044] border border-emerald-500/30">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="flex items-start gap-3">
              <ShieldCheck className="w-5 h-5 text-emerald-400 shrink-0 mt-1" />
              <div>
                <h4 className="font-bold text-white text-sm">Prevención de Suplantación</h4>
                <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
                  Tokens con vigencia y límite de usos que previenen la reutilización de capturas de pantalla antiguas.
                </p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <UserCheck className="w-5 h-5 text-teal-400 shrink-0 mt-1" />
              <div>
                <h4 className="font-bold text-white text-sm">Aislamiento por Rol y Unidad</h4>
                <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
                  Los residentes gestionan únicamente las visitas de su apartamento con total privacidad.
                </p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <Lock className="w-5 h-5 text-blue-400 shrink-0 mt-1" />
              <div>
                <h4 className="font-bold text-white text-sm">Bitácora y Auditoría</h4>
                <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
                  Registro sellado con operador de turno, fecha/hora y placa vehicular para respaldo de convivencia.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
