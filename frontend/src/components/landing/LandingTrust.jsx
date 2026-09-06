import { useState, useEffect, useRef } from 'react';
import {
  Building,
  Building2,
  ShieldCheck,
  UserCheck,
  Sparkles,
  ArrowRight,
  Check,
  Shield,
  Clock,
  TrendingUp,
  FileCheck2,
  Users,
} from 'lucide-react';
import { animate } from 'animejs';
import { useScrollReveal } from '../../lib/animations.js';

const BUSINESS_ROLES = [
  {
    code: 'EMPRESA_ADMINISTRADORA',
    title: 'Empresa Administradora',
    scope: 'Gestión Multi-Propiedad Corporativa',
    icon: Building,
    badgeClass: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    borderActiveClass: 'border-blue-500/60 shadow-blue-500/10',
    highlightBadge: 'Multi-Conjunto',
    benefitSummary: 'Controla 10, 20 o 50 copropiedades desde una única pantalla con analítica ejecutiva.',
    savingsMetric: { value: '-70%', label: 'Tiempo en consolidación de informes mensuales' },
    whatItDoes: [
      'Visión general del estado financiero y operativo de todos los conjuntos asignados.',
      'Supervisión del desempeño de administradores delegados y personal en campo.',
      'Descarga de reportes ejecutivos consolidados en Excel y PDF para juntas directivas.',
      'Comparativas de morosidad y recaudo para tomar decisiones oportunas de cartera.',
    ],
    permissions: [
      'Acceso exclusivo a las copropiedades bajo su contrato de gestión.',
      'Asignación de administradores y supervisores por conjunto.',
      'Monitoreo de auditoría y movimientos sensibles entre propiedades.',
      'Consulta de finanzas consolidadas sin cruces de datos entre conjuntos.',
    ],
  },
  {
    code: 'ADMIN_PROPIEDAD',
    title: 'Administrador de Copropiedad',
    scope: 'Operación Diaria y Gobierno del Conjunto',
    icon: Building2,
    badgeClass: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    borderActiveClass: 'border-emerald-500/60 shadow-emerald-500/10',
    highlightBadge: 'Gestión Total',
    benefitSummary: 'Automatiza cobros, censo y asambleas eliminando hasta 25 horas de papeleo al mes.',
    savingsMetric: { value: '25 h/mes', label: 'Ahorro de tiempo administrativo directo' },
    whatItDoes: [
      'Emisión mensual automática de cuotas ordinarias y extraordinarias con recaudo Wompi.',
      'Convocatoria y votaciones de asamblea virtual o presencial con quórum real Ley 675.',
      'Directorio oficial de copropietarios, residentes y vehículos siempre al día.',
      'Gestión de proveedores, cotizaciones, contratos y órdenes de mantenimiento.',
    ],
    permissions: [
      'Control total de los módulos de su conjunto residencial activo.',
      'Generación de certificados oficiales de Paz y Salvo con firma digital.',
      'Aprobación y respuesta formal a PQRS y solicitudes de residentes.',
      'Supervisión de la bitácora de portería, ingresos y control de parqueaderos.',
    ],
  },
  {
    code: 'PORTERIA_SEGURIDAD',
    title: 'Personal de Portería',
    scope: 'Control de Acceso Físico y Garita',
    icon: ShieldCheck,
    badgeClass: 'bg-sky-500/10 text-sky-400 border-sky-500/20',
    borderActiveClass: 'border-sky-500/60 shadow-sky-500/10',
    highlightBadge: 'Garita Ágil',
    benefitSummary: 'Validación en 5 segundos sin tocar el celular del visitante y cero minutas en papel.',
    savingsMetric: { value: '5 seg', label: 'Tiempo promedio para autorizar un ingreso' },
    whatItDoes: [
      'Escaneo óptico de pases QR temporales presentados por visitantes y domiciliarios.',
      'Asignación inmediata de bahías de parqueadero disponibles para visitantes.',
      'Custodia de paquetería con entrega obligatoria mediante PIN de 6 dígitos.',
      'Bitácora digital inmutable con hora exacta, portero en turno y placa del vehículo.',
    ],
    permissions: [
      'Consola de garita simplificada y táctil diseñada para PC o tablet.',
      'Validación de pases sin acceso a información privada ni financiera de los residentes.',
      'Recepción y entrega de correspondencia con validación de identidad.',
      'Registro rápido de novedades o incidentes de convivencia para la administración.',
    ],
  },
  {
    code: 'RESIDENTES_PROPIETARIOS',
    title: 'Residentes y Propietarios',
    scope: 'Autonomía y Convivencia Digital',
    icon: UserCheck,
    badgeClass: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20',
    borderActiveClass: 'border-cyan-500/60 shadow-cyan-500/10',
    highlightBadge: '100% Web',
    benefitSummary: 'Paga cuotas con PSE, autoriza visitas y reserva zonas comunes desde cualquier navegador.',
    savingsMetric: { value: '0 Filas', label: 'Paz y salvos y pagos al instante sin intermediarios' },
    whatItDoes: [
      'Generación de pases de acceso QR para familiares y amigos con vigencia programada.',
      'Pago en línea de administración con PSE, Bancolombia o tarjeta sin comisiones sorpresa.',
      'Consulta del PIN secreto de entrega cuando llega un paquete a portería.',
      'Reserva de salón comunal, BBQ y radicación de PQRS con seguimiento en tiempo real.',
    ],
    permissions: [
      'Acceso estricto y seguro únicamente a la información de su propia unidad habitacional.',
      'Votación por coeficiente en asambleas habilitadas por la administración.',
      'Descarga directa de estados de cuenta y comprobantes de pago conciliados.',
      'Gestión de sus propios visitantes frecuentes y vehículos autorizados.',
    ],
  },
];

export default function LandingTrust() {
  const [selectedRoleIdx, setSelectedRoleIdx] = useState(1); // Default to Admin de Propiedad
  const detailCardRef = useRef(null);

  const containerRef = useScrollReveal({
    selector: '.role-card-btn',
    stagger: 90,
    distance: 24,
  });

  const activeRole = BUSINESS_ROLES[selectedRoleIdx];

  // Smooth micro-interaction animation when changing role
  useEffect(() => {
    if (detailCardRef.current) {
      animate(detailCardRef.current, {
        opacity: [0.3, 1],
        translateY: [12, 0],
        duration: 350,
        ease: 'outExpo',
      });
    }
  }, [selectedRoleIdx]);

  return (
    <section
      id="roles"
      className="py-20 sm:py-28 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      {/* Subtle Glow */}
      <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-[700px] h-[350px] bg-sky-500/5 blur-[150px] rounded-full pointer-events-none" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-12 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20">
            <Sparkles className="w-3.5 h-3.5 text-sky-400" />
            UN SISTEMA · 4 PERFILES INTEGRADOS
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Diseñado para lo que cada persona necesita hacer.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Cada usuario cuenta con una interfaz hecha a la medida de sus responsabilidades, garantizando agilidad, privacidad y cero complicaciones operativas.
          </p>
        </div>

        {/* Interactive Roles Explorer */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start max-w-6xl mx-auto">
          
          {/* Left Column: 4 Interactive Role Buttons */}
          <div ref={containerRef} className="lg:col-span-5 space-y-3">
            {BUSINESS_ROLES.map((role, idx) => {
              const Icon = role.icon;
              const isSelected = idx === selectedRoleIdx;

              return (
                <button
                  key={role.code}
                  type="button"
                  onClick={() => setSelectedRoleIdx(idx)}
                  className={`role-card-btn w-full p-4 sm:p-5 rounded-2xl border text-left transition-all duration-200 flex items-center justify-between group focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 ${
                    isSelected
                      ? `bg-slate-900/90 border-sky-400/60 shadow-lg shadow-sky-950/40 ring-1 ring-sky-400/30`
                      : 'bg-slate-900/40 border-slate-800/80 hover:bg-slate-900/70 hover:border-slate-700'
                  }`}
                  aria-pressed={isSelected}
                >
                  <div className="flex items-center gap-3.5 min-w-0">
                    <div
                      className={`w-11 h-11 rounded-xl flex items-center justify-center transition-all ${
                        isSelected
                          ? 'bg-sky-500/20 border border-sky-400/40 text-sky-300 scale-105 shadow-sm'
                          : 'bg-slate-800/80 border border-slate-700 text-slate-400 group-hover:text-slate-200'
                      }`}
                    >
                      <Icon className="w-5 h-5" />
                    </div>
                    <div className="truncate">
                      <span className="text-sm sm:text-base font-bold text-white font-['Plus_Jakarta_Sans'] block truncate">
                        {role.title}
                      </span>
                      <span className="text-xs text-slate-400 block truncate font-medium">
                        {role.scope}
                      </span>
                    </div>
                  </div>

                  <ArrowRight
                    className={`w-4 h-4 shrink-0 transition-transform ${
                      isSelected ? 'text-sky-400 translate-x-1' : 'text-slate-600 group-hover:text-slate-400'
                    }`}
                  />
                </button>
              );
            })}
          </div>

          {/* Right Column: Deep-Dive Role Details & Impact */}
          <div
            ref={detailCardRef}
            className="lg:col-span-7 rounded-3xl bg-slate-900/90 border border-slate-800/90 shadow-2xl p-6 sm:p-8 space-y-6 backdrop-blur-xl"
          >
            {/* Header of Active Role */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
              <div className="flex items-center gap-3.5">
                <div className="w-12 h-12 rounded-2xl bg-sky-500/15 border border-sky-500/30 flex items-center justify-center text-sky-400 shadow-inner">
                  {(() => {
                    const ActiveIcon = activeRole.icon;
                    return <ActiveIcon className="w-6 h-6" />;
                  })()}
                </div>
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="text-xl font-bold text-white font-['Plus_Jakarta_Sans']">
                      {activeRole.title}
                    </h3>
                    <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${activeRole.badgeClass}`}>
                      {activeRole.highlightBadge}
                    </span>
                  </div>
                  <span className="text-xs text-slate-400">{activeRole.scope}</span>
                </div>
              </div>

              {/* Key Impact Metric Badge */}
              <div className="px-3.5 py-2 rounded-xl bg-slate-950 border border-slate-800/90 self-start sm:self-auto text-left sm:text-right">
                <span className="text-base font-extrabold text-emerald-400 font-mono block">
                  {activeRole.savingsMetric.value}
                </span>
                <span className="text-[10px] text-slate-400 block font-medium">
                  {activeRole.savingsMetric.label}
                </span>
              </div>
            </div>

            {/* Benefit Summary */}
            <p className="text-sm sm:text-base text-slate-200 leading-relaxed font-medium">
              {activeRole.benefitSummary}
            </p>

            {/* What it does in daily operations */}
            <div className="space-y-3">
              <span className="text-xs font-bold uppercase tracking-wider text-sky-400 flex items-center gap-1.5">
                <Clock className="w-3.5 h-3.5" />
                Qué automatiza y resuelve en el día a día
              </span>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {activeRole.whatItDoes.map((item, i) => (
                  <div
                    key={i}
                    className="p-3 rounded-xl bg-slate-950/70 border border-slate-800/80 flex items-start gap-2.5 text-xs text-slate-300"
                  >
                    <Check className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                    <span>{item}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Permissions & Security boundaries (in business language, zero SQL) */}
            <div className="p-4 rounded-2xl bg-[#091122] border border-sky-500/25 space-y-2">
              <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                <Shield className="w-3.5 h-3.5 text-sky-400" />
                Control de acceso y privacidad garantizada
              </span>
              <ul className="space-y-1.5 pt-1">
                {activeRole.permissions.map((perm, i) => (
                  <li key={i} className="text-xs text-slate-300 flex items-start gap-2">
                    <span className="text-sky-400 font-bold">•</span>
                    <span>{perm}</span>
                  </li>
                ))}
              </ul>
            </div>

          </div>

        </div>

      </div>
    </section>
  );
}
