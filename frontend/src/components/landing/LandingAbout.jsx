import { Building2, Users, Shield, Landmark, Layers, Smartphone, Lock } from 'lucide-react';

export default function LandingAbout() {
  return (
    <section id="que-es-saed" className="py-20 bg-[#0F172A] text-white relative">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto space-y-4">
          <span className="text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 px-3 py-1 rounded-full border border-emerald-500/20 inline-block">
            CONCEPTO DE PLATAFORMA
          </span>
          <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight font-['Plus_Jakarta_Sans']">
            Una nueva forma de administrar tu propiedad
          </h2>
          <p className="text-base sm:text-lg text-slate-300 leading-relaxed">
            SAED es una plataforma SaaS de grado empresarial diseñada para unificar, automatizar y digitalizar la convivencia, la logística y las finanzas de edificios y conjuntos residenciales.
          </p>
        </div>

        {/* The Equation Formula */}
        <div className="mt-14 max-w-5xl mx-auto">
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 sm:gap-4 items-center">
            {/* Box 1 */}
            <div className="p-5 rounded-2xl bg-slate-800/80 border border-slate-700/80 text-center space-y-2 hover:border-emerald-500/40 transition-colors">
              <div className="w-12 h-12 mx-auto rounded-xl bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400">
                <Building2 className="w-6 h-6" />
              </div>
              <h3 className="font-bold text-white text-sm sm:text-base">Administración</h3>
              <p className="text-xs text-slate-400">Control de unidades, contratos y copropiedad</p>
            </div>

            {/* Plus 1 */}
            <div className="hidden lg:flex justify-center text-2xl font-bold text-slate-600">
              +
            </div>

            {/* Box 2 */}
            <div className="p-5 rounded-2xl bg-slate-800/80 border border-slate-700/80 text-center space-y-2 hover:border-emerald-500/40 transition-colors">
              <div className="w-12 h-12 mx-auto rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                <Users className="w-6 h-6" />
              </div>
              <h3 className="font-bold text-white text-sm sm:text-base">Residentes</h3>
              <p className="text-xs text-slate-400">Autogestión de visitas, cuotas y casillero</p>
            </div>

            {/* Plus 2 */}
            <div className="hidden lg:flex justify-center text-2xl font-bold text-slate-600">
              +
            </div>

            {/* Box 3 */}
            <div className="p-5 rounded-2xl bg-slate-800/80 border border-slate-700/80 text-center space-y-2 hover:border-emerald-500/40 transition-colors">
              <div className="w-12 h-12 mx-auto rounded-xl bg-teal-500/10 border border-teal-500/20 flex items-center justify-center text-teal-400">
                <Shield className="w-6 h-6" />
              </div>
              <h3 className="font-bold text-white text-sm sm:text-base">Portería</h3>
              <p className="text-xs text-slate-400">Validación QR, encomiendas y parqueaderos</p>
            </div>
          </div>

          <div className="mt-4 grid grid-cols-2 lg:grid-cols-5 gap-3 sm:gap-4 items-center">
            {/* Box 4 */}
            <div className="p-5 rounded-2xl bg-slate-800/80 border border-slate-700/80 text-center space-y-2 hover:border-emerald-500/40 transition-colors">
              <div className="w-12 h-12 mx-auto rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
                <Landmark className="w-6 h-6" />
              </div>
              <h3 className="font-bold text-white text-sm sm:text-base">Finanzas</h3>
              <p className="text-xs text-slate-400">Cartera en tiempo real y pasarela Wompi</p>
            </div>

            {/* Equal Sign */}
            <div className="col-span-2 lg:col-span-4 p-5 rounded-2xl bg-gradient-to-r from-emerald-950/60 to-[#0F2044] border border-emerald-500/30 flex flex-col sm:flex-row items-center justify-between gap-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-emerald-500/20 border border-emerald-500/30 flex items-center justify-center text-emerald-300 font-bold text-lg">
                  =
                </div>
                <div>
                  <h4 className="text-lg font-bold text-white">SAED 2.0</h4>
                  <p className="text-xs text-slate-300">
                    Un único ecosistema conectado que reemplaza planillas, chats informales y hojas de cálculo.
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2 text-xs font-semibold text-emerald-400 shrink-0">
                <Lock className="w-4 h-4" />
                <span>Datos protegidos y aislados</span>
              </div>
            </div>
          </div>
        </div>

        {/* Core Pillars - Visual Scanning Optimized */}
        <div className="mt-14 grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-3">
            <Layers className="w-6 h-6 text-emerald-400" />
            <h3 className="text-lg font-bold text-white">Arquitectura Multi-Tenant</h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Cada copropiedad opera en un entorno lógico aislado. Permite supervisar múltiples propiedades sin cruce ni filtración de información.
            </p>
          </div>

          <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-3">
            <Smartphone className="w-6 h-6 text-teal-400" />
            <h3 className="text-lg font-bold text-white">Experiencia Web Sin Fricción</h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Acceso ágil desde cualquier navegador móvil o de escritorio, sin instalaciones pesadas ni requerimiento de descargas en tiendas.
            </p>
          </div>

          <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-3">
            <Shield className="w-6 h-6 text-blue-400" />
            <h3 className="text-lg font-bold text-white">Trazabilidad y Auditoría</h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Registro histórico con fecha, hora exacta y operador de turno para respaldar revisiones claras y transparentes ante el consejo.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
