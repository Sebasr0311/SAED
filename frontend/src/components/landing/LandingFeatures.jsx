import {
  Building2,
  QrCode,
  CreditCard,
  CheckCircle2,
  UserCheck,
  ShieldCheck,
  FileCheck,
} from 'lucide-react';

const CAPABILITIES = [
  {
    number: '01',
    category: 'Gobernanza & Censo',
    title: 'Administración',
    subtitle: 'Gestiona propiedades, residentes, unidades y operaciones.',
    description:
      'Control centralizado del censo de copropietarios y arrendatarios, coeficientes de copropiedad bajo Ley 675, contratos de vivienda y trazabilidad de eventos sin depender de canales informales.',
    highlights: [
      'Padrón completo de unidades y habitantes',
      'Contratos de arrendamiento y coarrendatarios',
      'Aislamiento estricto de datos por copropiedad',
    ],
    accentColor: 'from-blue-500/20 to-blue-600/5',
    borderColor: 'border-blue-500/30',
    tagColor: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    icon: Building2,
    previewType: 'admin',
  },
  {
    number: '02',
    category: 'Seguridad Perimetral',
    title: 'Control de acceso',
    subtitle: 'QR para visitantes y operación digital de portería.',
    description:
      'Pases digitales generados por el residente desde su navegador web. Validación instantánea en la garita con registro cronológico de operador, vehículo y cupo de estacionamiento asignado.',
    highlights: [
      'Tokens criptográficos temporales o de uso único',
      'Consola web para PC o tablet en portería',
      'Bitácora inmutable con registro de hora y placa',
    ],
    accentColor: 'from-emerald-500/20 to-emerald-600/5',
    borderColor: 'border-emerald-500/30',
    tagColor: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    icon: QrCode,
    previewType: 'access',
  },
  {
    number: '03',
    category: 'Recaudo & Contabilidad',
    title: 'Finanzas',
    subtitle: 'Cartera, cuotas y pagos centralizados.',
    description:
      'Emisión automatizada de cuotas ordinarias y extraordinarias. Integración oficial con pasarela Wompi para pagos seguros vía PSE y tarjetas bancarias con conciliación y paz y salvo inmediato.',
    highlights: [
      'Estados de cuenta por apartamento en tiempo real',
      'Pasarela Wompi (PSE, Bancolombia y tarjetas)',
      'Certificados de paz y salvo digitales inmediatos',
    ],
    accentColor: 'from-amber-500/20 to-amber-600/5',
    borderColor: 'border-amber-500/30',
    tagColor: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    icon: CreditCard,
    previewType: 'finance',
  },
];

export default function LandingFeatures() {
  return (
    <section
      id="funcionalidades"
      className="py-24 sm:py-32 lg:py-36 bg-[#0F172A] text-white relative border-t border-slate-800/80"
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Editorial Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-5">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-teal-400 bg-teal-500/10 border border-teal-500/20">
            CAPACIDADES DE LA PLATAFORMA
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Tres pilares para una gestión sin fricción
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            En lugar de múltiples herramientas desconectadas, SAED articula las tres dimensiones críticas de tu conjunto en una experiencia fluida.
          </p>
        </div>

        {/* 3 Grand Architectural Capability Blocks */}
        <div className="mt-16 sm:mt-24 space-y-8 lg:space-y-12 max-w-6xl mx-auto">
          {CAPABILITIES.map((cap, idx) => {
            const Icon = cap.icon;
            const isReversed = idx % 2 === 1;

            return (
              <div
                key={cap.number}
                className={`p-7 sm:p-10 lg:p-12 rounded-3xl bg-slate-900/80 border ${cap.borderColor} shadow-2xl transition-all duration-300 hover:border-opacity-60 flex flex-col ${
                  isReversed ? 'lg:flex-row-reverse' : 'lg:flex-row'
                } items-center gap-8 lg:gap-14`}
              >
                {/* Column: Text Content */}
                <div className="w-full lg:w-1/2 space-y-6">
                  <div className="flex items-center gap-3">
                    <span className="text-4xl sm:text-5xl font-extrabold font-mono text-slate-600">
                      {cap.number}
                    </span>
                    <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider border ${cap.tagColor}`}>
                      <Icon className="w-3.5 h-3.5" />
                      {cap.category}
                    </span>
                  </div>

                  <div>
                    <h3 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
                      {cap.title}
                    </h3>
                    <p className="text-base sm:text-lg text-emerald-400 font-medium mt-1">
                      {cap.subtitle}
                    </p>
                  </div>

                  <p className="text-sm sm:text-base text-slate-300 leading-relaxed">
                    {cap.description}
                  </p>

                  <div className="pt-2 space-y-2.5 border-t border-slate-800/80">
                    {cap.highlights.map((item) => (
                      <div key={item} className="flex items-center gap-2.5 text-xs sm:text-sm text-slate-200">
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                        <span>{item}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Column: Visual Micro-UI Representation */}
                <div className="w-full lg:w-1/2">
                  <div className={`rounded-2xl bg-gradient-to-br ${cap.accentColor} p-5 sm:p-7 border border-slate-700/60 shadow-inner`}>
                    {/* Render specific interactive mockup based on capability */}
                    {cap.previewType === 'admin' && (
                      <div className="bg-slate-900/90 rounded-xl p-5 border border-slate-700/50 space-y-4">
                        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                          <div className="flex items-center gap-2">
                            <Building2 className="w-4 h-4 text-blue-400" />
                            <span className="text-xs font-bold text-white uppercase tracking-wider">
                              Unidad 101 · Residente Principal
                            </span>
                          </div>
                          <span className="text-[11px] font-bold px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300">
                            Activo
                          </span>
                        </div>
                        <div className="grid grid-cols-2 gap-3 text-xs">
                          <div>
                            <span className="text-slate-400 block text-[11px]">Propietario:</span>
                            <span className="font-semibold text-white">Carlos Martinez</span>
                          </div>
                          <div>
                            <span className="text-slate-400 block text-[11px]">Identificación:</span>
                            <span className="font-semibold text-white">C.C. 1000000004</span>
                          </div>
                          <div>
                            <span className="text-slate-400 block text-[11px]">Contrato:</span>
                            <span className="font-semibold text-blue-300 font-mono">CNT-2026-001</span>
                          </div>
                          <div>
                            <span className="text-slate-400 block text-[11px]">Coeficiente:</span>
                            <span className="font-semibold text-emerald-400 font-mono">0.025000</span>
                          </div>
                        </div>
                        <div className="pt-2 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-400">
                          <span className="flex items-center gap-1">
                            <UserCheck className="w-3.5 h-3.5 text-emerald-400" />
                            Padrón verificado
                          </span>
                          <span className="text-slate-500 font-mono">SAED Context v4</span>
                        </div>
                      </div>
                    )}

                    {cap.previewType === 'access' && (
                      <div className="bg-slate-900/90 rounded-xl p-5 border border-slate-700/50 space-y-4">
                        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                          <div className="flex items-center gap-2">
                            <QrCode className="w-4 h-4 text-emerald-400" />
                            <span className="text-xs font-bold text-white uppercase tracking-wider">
                              Validación de Pase Garita
                            </span>
                          </div>
                          <span className="text-[11px] font-bold px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300">
                            Autorizado
                          </span>
                        </div>
                        <div className="flex items-center gap-4 bg-slate-950/70 p-3 rounded-lg border border-slate-800">
                          <div className="w-12 h-12 bg-white rounded-lg flex items-center justify-center shrink-0">
                            <QrCode className="w-10 h-10 text-slate-950" />
                          </div>
                          <div className="text-xs space-y-0.5">
                            <p className="font-bold text-white">Visitante Demo</p>
                            <p className="text-slate-400">Destino: Apto 101</p>
                            <p className="text-amber-300 font-mono font-semibold">Placa: DEM-123 · Puesto V-01</p>
                          </div>
                        </div>
                        <div className="text-xs text-emerald-400 flex items-center justify-between pt-1">
                          <span className="flex items-center gap-1 font-semibold">
                            <ShieldCheck className="w-3.5 h-3.5" />
                            Operador: Portero 01
                          </span>
                          <span className="text-slate-400 font-mono">10:30 AM · Hoy</span>
                        </div>
                      </div>
                    )}

                    {cap.previewType === 'finance' && (
                      <div className="bg-slate-900/90 rounded-xl p-5 border border-slate-700/50 space-y-4">
                        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                          <div className="flex items-center gap-2">
                            <CreditCard className="w-4 h-4 text-amber-400" />
                            <span className="text-xs font-bold text-white uppercase tracking-wider">
                              Liquidación de Cartera
                            </span>
                          </div>
                          <span className="text-[11px] font-bold px-2 py-0.5 rounded bg-amber-500/20 text-amber-300">
                            Wompi PSE
                          </span>
                        </div>
                        <div className="space-y-2 text-xs">
                          <div className="flex justify-between py-1 border-b border-slate-800/80">
                            <span className="text-slate-400">Concepto:</span>
                            <span className="text-white font-medium">Cuota Ordinaria Septiembre</span>
                          </div>
                          <div className="flex justify-between py-1 border-b border-slate-800/80">
                            <span className="text-slate-400">Monto Liquidado:</span>
                            <span className="text-white font-bold">$250.000 COP</span>
                          </div>
                          <div className="flex justify-between py-1 border-b border-slate-800/80">
                            <span className="text-slate-400">Estado en Banco:</span>
                            <span className="text-emerald-400 font-semibold flex items-center gap-1">
                              <CheckCircle2 className="w-3.5 h-3.5" /> Conciliado al instante
                            </span>
                          </div>
                        </div>
                        <div className="pt-1 flex items-center justify-between text-xs text-slate-400">
                          <span className="flex items-center gap-1">
                            <FileCheck className="w-3.5 h-3.5 text-blue-400" />
                            Paz y salvo emitido
                          </span>
                          <span className="text-emerald-400 font-mono text-[11px]">Transacción OK</span>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
