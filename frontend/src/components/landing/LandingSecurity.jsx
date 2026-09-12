import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ShieldCheck,
  Sparkles,
  TrendingDown,
  Clock,
  FileSpreadsheet,
  Scale,
  Receipt,
  CheckCircle2,
  ArrowRight,
  DollarSign,
  Building2,
  Lock,
  Zap,
} from 'lucide-react';

const SAVINGS_TIERS = [
  {
    units: 50,
    label: '50 Uds. (Torre Única)',
    annualPaperSavings: '$1.800.000',
    adminHoursSaved: '18 horas / mes',
    collectionSpeed: '+30% recaudo puntual',
    incidentsPrevented: '100% trazabilidad',
    detail: 'Ideal para condominios pequeños que buscan orden financiero y garita ágil sin contratar personal extra.',
  },
  {
    units: 120,
    label: '120 Uds. (Conjunto Promedio)',
    annualPaperSavings: '$3.400.000',
    adminHoursSaved: '32 horas / mes',
    collectionSpeed: '+42% recaudo puntual',
    incidentsPrevented: '0 paquetes extraviados',
    detail: 'Elimina las filas de pago y las disputas de parqueadero de visitantes con asignación en tiempo real.',
  },
  {
    units: 250,
    label: '250 Uds. (Multi-Torre)',
    annualPaperSavings: '$5.200.000',
    adminHoursSaved: '55 horas / mes',
    collectionSpeed: '+48% recaudo puntual',
    incidentsPrevented: 'Asambleas 100% blindadas',
    detail: 'Quórum automático por coeficientes Ley 675 y conciliación bancaria Wompi sin errores humanos.',
  },
  {
    units: 500,
    label: '500+ Uds. (Macro-Proyecto)',
    annualPaperSavings: '$8.900.000',
    adminHoursSaved: '90 horas / mes',
    collectionSpeed: '+55% recaudo puntual',
    incidentsPrevented: 'Auditoría corporativa total',
    detail: 'Consolidación ejecutiva para empresas administradoras con múltiples conjuntos a su cargo.',
  },
];

const AUTOMATION_PILLARS = [
  {
    icon: DollarSign,
    badge: 'Ahorro Financiero Directo',
    badgeClass: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    title: 'Cero gastos en papel, minutas y talonarios',
    desc: 'Sustituye minutas físicas de garita, recibos de caja, circulares impresas y papelería de asambleas por registros digitales inmutables en la nube.',
    metric: 'Hasta $5.2M COP / año ahorrados',
  },
  {
    icon: Receipt,
    badge: 'Recaudo Automatizado',
    badgeClass: 'bg-sky-500/10 text-sky-400 border-sky-500/20',
    title: 'Cobranza Wompi 24/7 y conciliación bancaria en vivo',
    desc: 'Los residentes pagan con PSE, Bancolombia o tarjeta desde su celular. El sistema concilia el pago al instante y emite el paz y salvo automático.',
    metric: '-38% de morosidad en cuotas',
  },
  {
    icon: Scale,
    badge: 'Blindaje Ley 675 de 2001',
    badgeClass: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
    title: 'Asambleas y votaciones sin riesgo de impugnación',
    desc: 'Cálculo de quórum certificado en tiempo real mediante los coeficientes de copropiedad registrados. Actas generadas con firma digital.',
    metric: '0 impugnaciones por quórum',
  },
  {
    icon: Lock,
    badge: 'Seguridad y Privacidad',
    badgeClass: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    title: 'Protección de datos (Habeas Data Ley 1581)',
    desc: 'Cada conjunto opera en una burbuja digital hermética. Los datos bancarios y personales de los residentes nunca se cruzan ni quedan expuestos.',
    metric: 'Aislamiento bancario de datos',
  },
];

export default function LandingSecurity() {
  const [selectedTierIdx, setSelectedTierIdx] = useState(1); // Default to 120 units
  const currentTier = SAVINGS_TIERS[selectedTierIdx];

  return (
    <section
      id="beneficios"
      className="py-20 sm:py-28 bg-[#0A1628] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      {/* Ambient Glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[400px] bg-emerald-500/5 blur-[160px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-14 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20">
            <Sparkles className="w-3.5 h-3.5 text-emerald-400" />
            BENEFICIOS E IMPACTO
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Automatiza procesos y ahorra tiempo y dinero desde el primer mes.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Menos trabajo manual para el administrador, mayor recaudo de cuotas y total tranquilidad jurídica para el consejo de administración.
          </p>
        </div>

        {/* 4 Business Automation Pillars */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 max-w-6xl mx-auto mb-16">
          {AUTOMATION_PILLARS.map((pillar, idx) => {
            const Icon = pillar.icon;
            return (
              <div
                key={idx}
                className="p-6 rounded-2xl bg-slate-900/80 border border-slate-800/90 hover:border-slate-700 transition-all duration-300 flex flex-col justify-between space-y-4 shadow-xl hover:-translate-y-1"
              >
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="w-10 h-10 rounded-xl bg-slate-800 border border-slate-700 flex items-center justify-center text-sky-400">
                      <Icon className="w-5 h-5" />
                    </div>
                    <span className={`text-[10px] font-bold px-2.5 py-0.5 rounded-full border ${pillar.badgeClass}`}>
                      {pillar.badge}
                    </span>
                  </div>

                  <h3 className="text-base font-bold text-white font-['Plus_Jakarta_Sans'] leading-snug">
                    {pillar.title}
                  </h3>

                  <p className="text-xs text-slate-300 leading-relaxed">
                    {pillar.desc}
                  </p>
                </div>

                <div className="pt-3 border-t border-slate-800/80 flex items-center gap-1.5 text-xs font-bold text-emerald-400">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                  <span>{pillar.metric}</span>
                </div>
              </div>
            );
          })}
        </div>

        {/* ========================================================================= */}
        {/* Interactive Savings & Automation Estimator for Decision Makers */}
        {/* ========================================================================= */}
        <div className="max-w-4xl mx-auto p-6 sm:p-8 rounded-3xl bg-slate-900/90 border border-slate-800/90 shadow-2xl backdrop-blur-xl">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
            <div className="flex items-center gap-3">
              <div className="w-11 h-11 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                <TrendingDown className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base sm:text-lg font-bold text-white font-['Plus_Jakarta_Sans']">
                  Calculadora de Ahorro y Eficiencia Operativa
                </h3>
                <p className="text-xs text-slate-400">Selecciona la escala de tu copropiedad para ver el impacto estimado</p>
              </div>
            </div>

            <span className="text-xs font-bold px-3 py-1 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 self-start sm:self-auto">
              Retorno Inmediato
            </span>
          </div>

          {/* Scale Tier Switcher Buttons */}
          <div className="pt-6 grid grid-cols-2 sm:grid-cols-4 gap-2.5 mb-6">
            {SAVINGS_TIERS.map((tier, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => setSelectedTierIdx(idx)}
                className={`p-3 rounded-xl border text-center transition-all text-xs font-bold ${
                  selectedTierIdx === idx
                    ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/50 shadow-md ring-1 ring-emerald-500/30'
                    : 'bg-slate-900/80 text-slate-300 border-slate-700/80 hover:bg-slate-800 hover:text-white'
                }`}
              >
                {tier.label}
              </button>
            ))}
          </div>

          {/* Dynamic 4 Metric Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5 mb-6">
            <div className="p-4 rounded-2xl bg-slate-950/70 border border-slate-800/80 space-y-1">
              <span className="text-[11px] text-slate-400 flex items-center gap-1.5">
                <DollarSign className="w-3.5 h-3.5 text-emerald-400" />
                Ahorro en Papel
              </span>
              <div className="text-xl sm:text-2xl font-extrabold font-mono text-white">
                {currentTier.annualPaperSavings}
              </div>
              <span className="text-[10px] text-slate-500 block">COP al año en insumos</span>
            </div>

            <div className="p-4 rounded-2xl bg-slate-950/70 border border-slate-800/80 space-y-1">
              <span className="text-[11px] text-slate-400 flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5 text-sky-400" />
                Tiempo Ahorrado
              </span>
              <div className="text-xl sm:text-2xl font-extrabold font-mono text-sky-400">
                {currentTier.adminHoursSaved}
              </div>
              <span className="text-[10px] text-slate-500 block">En cobros y conciliación</span>
            </div>

            <div className="p-4 rounded-2xl bg-slate-950/70 border border-slate-800/80 space-y-1">
              <span className="text-[11px] text-slate-400 flex items-center gap-1.5">
                <Zap className="w-3.5 h-3.5 text-amber-400" />
                Aceleración Cartera
              </span>
              <div className="text-xl sm:text-2xl font-extrabold font-mono text-emerald-400">
                {currentTier.collectionSpeed}
              </div>
              <span className="text-[10px] text-slate-500 block">Primeros 10 días del mes</span>
            </div>

            <div className="p-4 rounded-2xl bg-slate-950/70 border border-slate-800/80 space-y-1">
              <span className="text-[11px] text-slate-400 flex items-center gap-1.5">
                <ShieldCheck className="w-3.5 h-3.5 text-purple-400" />
                Seguridad Operativa
              </span>
              <div className="text-sm font-bold text-white leading-snug pt-1">
                {currentTier.incidentsPrevented}
              </div>
              <span className="text-[10px] text-slate-500 block">Con respaldo Ley 675</span>
            </div>
          </div>

          <p className="text-xs text-slate-300 bg-slate-950/60 p-3.5 rounded-xl border border-slate-800/80 mb-6 leading-relaxed">
            💡 <strong>Enfoque para el administrador:</strong> {currentTier.detail}
          </p>

          <div className="text-center pt-2">
            <Link
              to="/suscripciones"
              className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-gradient-to-r from-emerald-400 via-sky-400 to-cyan-400 hover:from-emerald-300 hover:to-sky-300 text-slate-950 font-bold text-xs sm:text-sm shadow-lg shadow-sky-950/50 transition-all transform active:scale-[0.98]"
            >
              <span>Ver planes y cotizar para mi conjunto</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>

      </div>
    </section>
  );
}
