import { useState, useEffect, useRef } from 'react';
import {
  FileSpreadsheet,
  BookOpen,
  Receipt,
  PackageX,
  Car,
  MessageSquareOff,
  Building2,
  Users,
  ShieldCheck,
  CreditCard,
  CheckCircle2,
  Plus,
  Equal,
  Sparkles,
  ArrowRight,
  ShieldAlert,
  Zap,
} from 'lucide-react';
import { animate } from 'animejs';

const FRAGMENTED_ISSUES = [
  {
    icon: FileSpreadsheet,
    title: 'Información dispersa en Excels',
    desc: 'Hojas de cálculo desactualizadas entre el computador personal del administrador y las libretas de garita.',
    badge: 'Descoordinación',
  },
  {
    icon: BookOpen,
    title: 'Visitantes anotados en papel',
    desc: 'Minutas físicas vulnerables a pérdida o deterioro que imposibilitan cualquier auditoría histórica confiable.',
    badge: 'Riesgo de Seguridad',
  },
  {
    icon: Receipt,
    title: 'Cartera y recibos en WhatsApp',
    desc: 'Comprobantes enviados por chat privado, desconfianza en saldos morosos y conciliación bancaria manual tardía.',
    badge: 'Fuga Financiera',
  },
  {
    icon: PackageX,
    title: 'Paquetes sin custodia ni PIN',
    desc: 'Entregas por confusión y encomiendas acumuladas en garita sin notificación oficial ni firma de entrega.',
    badge: 'Reclamos Frecuentes',
  },
  {
    icon: Car,
    title: 'Bahías vehiculares a ciegas',
    desc: 'Vehículos sin control de permanencia, bahías copadas por residentes y disputas diarias en portería.',
    badge: 'Fricción Diaria',
  },
  {
    icon: MessageSquareOff,
    title: 'Comunicados informales perdidos',
    desc: 'Grupos de chat caóticos donde los comunicados de asambleas y cobros se pierden entre quejas y spam.',
    badge: 'Sin Trazabilidad',
  },
];

const UNIFIED_SOLUTIONS = [
  {
    icon: Building2,
    title: 'Censo digital único en la nube',
    desc: 'Directorio centralizado con coeficientes Ley 675, propietarios y habitantes actualizados en tiempo real.',
    badge: 'Dato Único Confiable',
  },
  {
    icon: ShieldCheck,
    title: 'Pases QR seguros con vigencia',
    desc: 'Invitaciones generadas por residentes con expiración parametrizable y validación óptica en garita.',
    badge: 'Auditoría Total',
  },
  {
    icon: CreditCard,
    title: 'Recaudo Wompi y conciliación',
    desc: 'Pago de cuotas con PSE, Bancolombia y tarjetas, con emisión inmediata de estados de cuenta y paz y salvos.',
    badge: 'Recaudo Automatizado',
  },
  {
    icon: ShieldCheck,
    title: 'Custodia de paquetes por PIN',
    desc: 'Protocolo de casillero con clave criptográfica única de 6 dígitos que solo el residente puede ver.',
    badge: 'Cero Entregas Erróneas',
  },
  {
    icon: Car,
    title: 'Bahías de visitantes en vivo',
    desc: 'Asignación automática de cupo al registrar el ingreso vehicular y liberación inmediata al registrar la salida.',
    badge: 'Cupos Transparentes',
  },
  {
    icon: CheckCircle2,
    title: 'Canales formales y PQRS con SLA',
    desc: 'Radicación oficial de quejas, asambleas virtuales con votación por coeficiente y actas descargables.',
    badge: 'Convivencia Ordenada',
  },
];

export default function LandingProblemSolution() {
  const [viewMode, setViewMode] = useState('unified'); // 'traditional' | 'unified'
  const cardsGridRef = useRef(null);

  // Animate grid reveal on mode toggle with Anime.js
  useEffect(() => {
    if (cardsGridRef.current) {
      animate(cardsGridRef.current.children, {
        opacity: [0.3, 1],
        translateY: [16, 0],
        duration: 400,
        ease: 'outExpo',
      });
    }
  }, [viewMode]);

  const items = viewMode === 'traditional' ? FRAGMENTED_ISSUES : UNIFIED_SOLUTIONS;

  return (
    <section
      id="soluciones"
      className="py-20 sm:py-28 lg:py-32 bg-[#0A1628] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[380px] bg-sky-500/5 blur-[160px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Editorial Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-12 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20">
            <Sparkles className="w-3.5 h-3.5 text-sky-400" />
            EL RETO VS LA SOLUCIÓN
          </span>

          <h2 className="text-3xl sm:text-5xl lg:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            La administración residencial no debería vivir en diez herramientas.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto pt-2">
            Compara la realidad operativa de los conjuntos tradicionales frente a la tranquilidad de operar con SAED 2.0:
          </p>

          {/* Interactive Mode Toggle Pill */}
          <div className="pt-4 flex justify-center">
            <div className="p-1 bg-slate-900/90 rounded-2xl border border-slate-800 flex items-center shadow-inner">
              <button
                type="button"
                onClick={() => setViewMode('traditional')}
                className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all flex items-center gap-2 ${
                  viewMode === 'traditional'
                    ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40 shadow-sm'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                <ShieldAlert className="w-3.5 h-3.5 text-rose-400" />
                <span>Gestión Tradicional Dispersa</span>
              </button>

              <button
                type="button"
                onClick={() => setViewMode('unified')}
                className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all flex items-center gap-2 ${
                  viewMode === 'unified'
                    ? 'bg-sky-500/20 text-sky-300 border border-sky-500/40 shadow-sm'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                <Zap className="w-3.5 h-3.5 text-sky-400" />
                <span>Gestión Unificada con SAED 2.0</span>
              </button>
            </div>
          </div>
        </div>

        {/* 6 Cards Interactive Comparison Grid */}
        <div
          ref={cardsGridRef}
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 max-w-6xl mx-auto mb-16 sm:mb-20"
        >
          {items.map((item, idx) => {
            const Icon = item.icon;
            const isTraditional = viewMode === 'traditional';
            return (
              <div
                key={idx}
                className={`p-6 rounded-2xl border transition-all duration-300 space-y-3 shadow-xl hover:-translate-y-1 ${
                  isTraditional
                    ? 'bg-slate-900/70 border-rose-500/25 hover:border-rose-500/50 shadow-rose-950/10'
                    : 'bg-slate-900/70 border-sky-500/25 hover:border-sky-500/50 shadow-sky-950/10'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div
                    className={`w-10 h-10 rounded-xl flex items-center justify-center border shadow-inner ${
                      isTraditional
                        ? 'bg-rose-500/10 border-rose-500/30 text-rose-400'
                        : 'bg-sky-500/10 border-sky-500/30 text-sky-400'
                    }`}
                  >
                    <Icon className="w-5 h-5" />
                  </div>
                  <span
                    className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded-full border ${
                      isTraditional
                        ? 'bg-rose-500/10 text-rose-300 border-rose-500/30'
                        : 'bg-sky-500/10 text-sky-300 border-sky-500/30'
                    }`}
                  >
                    {item.badge}
                  </span>
                </div>

                <h3 className="text-base font-bold text-white font-['Plus_Jakarta_Sans']">
                  {item.title}
                </h3>
                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                  {item.desc}
                </p>
              </div>
            );
          })}
        </div>

        {/* ========================================================================= */}
        {/* The Transition: SAED Conecta la Operación (Monumental Equation) */}
        {/* ========================================================================= */}
        <div className="max-w-5xl mx-auto p-8 sm:p-12 rounded-3xl bg-gradient-to-br from-[#0A1628] via-[#0F2044] to-[#0A1628] border border-sky-500/30 shadow-2xl text-center space-y-8 backdrop-blur-xl">
          
          <div className="inline-flex items-center gap-2 px-3.5 py-1 rounded-full bg-sky-500/10 border border-sky-500/20 text-sky-400 text-xs font-bold uppercase tracking-wider">
            <CheckCircle2 className="w-3.5 h-3.5" />
            <span>LA SOLUCIÓN UNIFICADA</span>
          </div>

          <h3 className="text-2xl sm:text-4xl font-extrabold text-white tracking-tight font-['Plus_Jakarta_Sans']">
            SAED conecta la operación.
          </h3>

          <p className="text-sm sm:text-base text-slate-300 max-w-xl mx-auto leading-relaxed">
            Eliminamos el cruce de información conectando a todos los actores en una sola arquitectura digital sincronizada en tiempo real.
          </p>

          {/* The Unified Equation */}
          <div className="pt-4 flex flex-wrap items-center justify-center gap-3 sm:gap-4 text-xs sm:text-sm font-bold">
            <div className="px-4 py-3 rounded-xl bg-slate-900/90 border border-slate-700 text-slate-200 flex items-center gap-2">
              <Building2 className="w-4 h-4 text-sky-400" />
              <span>Administración</span>
            </div>

            <Plus className="w-4 h-4 text-sky-400" />

            <div className="px-4 py-3 rounded-xl bg-slate-900/90 border border-slate-700 text-slate-200 flex items-center gap-2">
              <Users className="w-4 h-4 text-emerald-400" />
              <span>Residentes</span>
            </div>

            <Plus className="w-4 h-4 text-sky-400" />

            <div className="px-4 py-3 rounded-xl bg-slate-900/90 border border-slate-700 text-slate-200 flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-teal-400" />
              <span>Portería</span>
            </div>

            <Plus className="w-4 h-4 text-sky-400" />

            <div className="px-4 py-3 rounded-xl bg-slate-900/90 border border-slate-700 text-slate-200 flex items-center gap-2">
              <CreditCard className="w-4 h-4 text-amber-400" />
              <span>Finanzas</span>
            </div>

            <Equal className="w-4 h-4 text-sky-400 hidden sm:block" />

            <div className="w-full sm:w-auto px-6 py-3 rounded-xl bg-gradient-to-r from-cyan-400 via-sky-400 to-cyan-400 text-slate-950 font-extrabold tracking-wide shadow-lg shadow-sky-950/50 flex items-center justify-center gap-2">
              <span>SAED 2.0</span>
              <ArrowRight className="w-4 h-4" />
            </div>
          </div>

        </div>

      </div>
    </section>
  );
}
