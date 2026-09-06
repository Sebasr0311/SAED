import { useState, useEffect, useRef } from 'react';
import {
  ShieldAlert,
  Building2,
  Building,
  ShieldCheck,
  UserCheck,
  Sparkles,
  ArrowRight,
  Check,
  Lock,
  Layers,
} from 'lucide-react';
import { animate } from 'animejs';
import { useScrollReveal } from '../../lib/animations.js';

const ROLES = [
  {
    code: 'SUPERADMIN',
    title: 'Superadministrador',
    scope: 'Plataforma SaaS Global',
    icon: ShieldAlert,
    color: 'purple',
    badgeClass: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
    borderActiveClass: 'border-purple-500/60 shadow-purple-500/10',
    description: 'Control de infraestructura de la plataforma, planes SaaS y auditoría transversal del sistema.',
    vpdPredicate: 'SIN FILTRO (Acceso de Mantenimiento Global de Plataforma)',
    capabilities: [
      'Aprovisionamiento y suspensión de organizaciones cliente',
      'Configuración de planes y tarifas de licenciamiento',
      'Auditoría transversal y métricas de rendimiento cloud',
      'Monitoreo de seguridad y fallos de autenticación',
    ],
  },
  {
    code: 'ADMIN_ORGANIZACION',
    title: 'Administrador de Organización',
    scope: 'Multi-Propiedad Corporativa',
    icon: Building,
    color: 'blue',
    badgeClass: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    borderActiveClass: 'border-blue-500/60 shadow-blue-500/10',
    description: 'Gestión corporativa de empresas administradoras con múltiples copropiedades y conjuntos a su cargo.',
    vpdPredicate: 'WHERE ORGANIZACION_ID = SYS_CONTEXT(\'SAED_CTX\', \'ORGANIZACION_ID\')',
    capabilities: [
      'Visión consolidada de múltiples edificios y conjuntos',
      'Comparativa de recaudo y cartera entre copropiedades',
      'Asignación de administradores y supervisores por conjunto',
      'Reportes ejecutivos descargables en Excel y PDF',
    ],
  },
  {
    code: 'ADMIN_PROPIEDAD',
    title: 'Administrador de Propiedad',
    scope: 'Copropiedad / Conjunto Residencial',
    icon: Building2,
    color: 'emerald',
    badgeClass: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    borderActiveClass: 'border-emerald-500/60 shadow-emerald-500/10',
    description: 'Gestión operativa y financiera: padrón de residentes, cartera, cuotas, proveedores y asambleas.',
    vpdPredicate: 'WHERE PROPIEDAD_ID = SYS_CONTEXT(\'SAED_CTX\', \'PROPIEDAD_ID\')',
    capabilities: [
      'Directorio y censo oficial de propietarios y residentes',
      'Emisión de cuotas y conciliación de recaudo Wompi en tiempo real',
      'Convocatoria y votación de asambleas con quórum Ley 675',
      'Gestión de proveedores, pólizas de seguro y mantenimiento',
    ],
  },
  {
    code: 'PORTERO',
    title: 'Personal de Garita / Portería',
    scope: 'Acceso Físico y Bitácora',
    icon: ShieldCheck,
    color: 'sky',
    badgeClass: 'bg-sky-500/10 text-sky-400 border-sky-500/20',
    borderActiveClass: 'border-sky-500/60 shadow-sky-500/10',
    description: 'Operación ágil en pantalla: escaneo de pases QR, recepción y entrega de paquetes por PIN y parqueaderos.',
    vpdPredicate: 'WHERE PROPIEDAD_ID = SYS_CONTEXT(\'SAED_CTX\', \'PROPIEDAD_ID\')',
    capabilities: [
      'Escaneo óptico de pases QR temporales de visitantes',
      'Asignación y liberación de bahías de parqueadero en vivo',
      'Custodia de correspondencia y validación obligatoria con PIN',
      'Bitácora inmutable de eventos de entrada y salida',
    ],
  },
  {
    code: 'RESIDENTE',
    title: 'Copropietario / Habitante',
    scope: 'Unidad Privada (Apartamento / Casa)',
    icon: UserCheck,
    color: 'teal',
    badgeClass: 'bg-teal-500/10 text-teal-400 border-teal-500/20',
    borderActiveClass: 'border-teal-500/60 shadow-teal-500/10',
    description: 'Portal web ágil: emisión de invitaciones QR, pago de cuotas con Wompi, casillero y radicación de PQRS.',
    vpdPredicate: 'WHERE PROPIEDAD_ID = CTX AND UNIDAD_ID = SYS_CONTEXT(\'SAED_CTX\', \'UNIDAD_ID\')',
    capabilities: [
      'Generación de códigos QR para invitados con vigencia en horas',
      'Pago en línea de administración mediante PSE / Tarjeta Wompi',
      'Recepción de correspondencia y visualización de PIN de entrega',
      'Radicación y seguimiento de PQRS oficiales con la administración',
    ],
  },
];

export default function LandingTrust() {
  const [selectedRoleIdx, setSelectedRoleIdx] = useState(2); // Default to ADMIN_PROPIEDAD
  const detailCardRef = useRef(null);

  const containerRef = useScrollReveal({
    selector: '.role-btn',
    stagger: 70,
    distance: 20,
  });

  // Animate detail card on role switch with Anime.js
  useEffect(() => {
    if (detailCardRef.current) {
      animate(detailCardRef.current, {
        opacity: [0.4, 1],
        translateY: [12, 0],
        duration: 400,
        ease: 'outExpo',
      });
    }
  }, [selectedRoleIdx]);

  const activeRole = ROLES[selectedRoleIdx];

  return (
    <section
      id="soluciones"
      className="py-20 sm:py-28 lg:py-32 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      <div className="absolute top-1/3 right-1/4 w-[550px] h-[320px] bg-sky-500/5 blur-[150px] rounded-full pointer-events-none" />
      
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Editorial Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-14 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20">
            <Sparkles className="w-3.5 h-3.5 text-sky-400" />
            GOBERNANZA, ROLES Y AUTORIZACIÓN
          </span>

          <h2 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Una plataforma.<br />
            Cinco roles.<br />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-white via-cyan-200 to-sky-400">
              Una operación conectada.
            </span>
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto pt-2">
            Cada perfil interactúa con la plataforma bajo una matriz estricta de autorización en la interfaz y en el motor de base de datos. Selecciona un rol para explorar su alcance:
          </p>
        </div>

        {/* 2-Column Interactive Role Explorer Layout */}
        <div className="max-w-6xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* Left Column: 5 Selectable Role Tabs */}
          <div ref={containerRef} className="lg:col-span-5 space-y-3">
            {ROLES.map((role, idx) => {
              const Icon = role.icon;
              const isSelected = selectedRoleIdx === idx;
              return (
                <button
                  key={role.code}
                  type="button"
                  onClick={() => setSelectedRoleIdx(idx)}
                  className={`role-btn w-full p-4 sm:p-4.5 rounded-2xl border text-left transition-all duration-300 flex items-center justify-between gap-3 group ${
                    isSelected
                      ? `bg-slate-900/90 ${role.borderActiveClass} ring-1 ring-white/10 shadow-lg`
                      : 'bg-slate-950/60 border-slate-800/80 hover:border-slate-700 hover:bg-slate-900/40'
                  }`}
                >
                  <div className="flex items-center gap-3.5 min-w-0">
                    <div
                      className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border transition-transform ${
                        isSelected
                          ? 'bg-sky-500/20 border-sky-400/40 text-sky-300 scale-105'
                          : 'bg-slate-900 border-slate-800 text-slate-400 group-hover:text-slate-200'
                      }`}
                    >
                      <Icon className="w-5 h-5" />
                    </div>
                    <div className="truncate">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-white font-['Plus_Jakarta_Sans'] block truncate">
                          {role.title}
                        </span>
                      </div>
                      <span className="text-[11px] font-mono text-slate-400 block truncate">
                        {role.code}
                      </span>
                    </div>
                  </div>

                  <ArrowRight
                    className={`w-4 h-4 shrink-0 transition-transform ${
                      isSelected ? 'text-sky-400 translate-x-0.5' : 'text-slate-600 group-hover:text-slate-400'
                    }`}
                  />
                </button>
              );
            })}
          </div>

          {/* Right Column: Deep-Dive Role Profile & Capabilities Panel */}
          <div
            ref={detailCardRef}
            className="lg:col-span-7 rounded-3xl bg-slate-900/90 border border-slate-800/90 shadow-2xl p-6 sm:p-8 space-y-6 backdrop-blur-xl"
          >
            {/* Active Role Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-2xl bg-sky-500/10 border border-sky-500/30 flex items-center justify-center text-sky-400 shadow-inner">
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
                    <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold border ${activeRole.badgeClass}`}>
                      {activeRole.code}
                    </span>
                  </div>
                  <span className="text-xs text-slate-400 font-medium">{activeRole.scope}</span>
                </div>
              </div>

              <div className="px-3 py-1.5 rounded-xl bg-slate-950 border border-slate-800 self-start sm:self-auto">
                <span className="text-[10px] font-mono text-slate-400 uppercase tracking-wider block">Nivel RBAC</span>
                <span className="text-xs font-bold text-sky-300">Aislamiento por Token</span>
              </div>
            </div>

            {/* Role Purpose Description */}
            <p className="text-sm text-slate-300 leading-relaxed">
              {activeRole.description}
            </p>

            {/* Core Capabilities List */}
            <div className="space-y-2.5">
              <span className="text-xs font-bold uppercase tracking-wider text-slate-400 block">
                Capacidades Operativas del Perfil
              </span>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {activeRole.capabilities.map((cap, i) => (
                  <div
                    key={i}
                    className="p-3 rounded-xl bg-slate-950/70 border border-slate-800/80 flex items-start gap-2.5 text-xs text-slate-200"
                  >
                    <Check className="w-4 h-4 text-sky-400 shrink-0 mt-0.5" />
                    <span>{cap}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Oracle VPD Policy Applied */}
            <div className="p-4 rounded-2xl bg-[#060910] border border-sky-500/25 space-y-2 font-mono text-xs">
              <div className="flex items-center justify-between text-[10px] text-slate-500 uppercase font-bold tracking-wider">
                <span className="flex items-center gap-1.5 text-sky-400">
                  <Lock className="w-3.5 h-3.5" />
                  Restricción en Base de Datos (Oracle VPD / RLS)
                </span>
                <span className="text-slate-400">Kernel Security</span>
              </div>
              <p className="text-slate-300 text-[11px] bg-slate-950 p-2.5 rounded-lg border border-slate-800/80 text-sky-300 overflow-x-auto">
                {activeRole.vpdPredicate}
              </p>
              <p className="text-[10px] text-slate-400 font-sans">
                Las consultas SQL ejecutadas por este usuario quedan matemáticamente acotadas a su ámbito autorizado.
              </p>
            </div>

          </div>

        </div>

      </div>
    </section>
  );
}
