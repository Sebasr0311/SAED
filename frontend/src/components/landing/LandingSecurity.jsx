import { useState, useEffect, useRef } from 'react';
import {
  Lock,
  Database,
  Key,
  Layers,
  Scale,
  Sparkles,
  ShieldCheck,
  Terminal,
  CheckCircle2,
  XCircle,
  Play,
  Pause,
  ArrowRight,
  ShieldAlert,
  Code2,
} from 'lucide-react';
import { animate } from 'animejs';
import { useScrollReveal } from '../../lib/animations.js';

const SECURITY_PIPELINE = [
  {
    step: '01',
    name: 'Usuario',
    detail: 'Credenciales verificadas',
    tech: 'Bcrypt Hash + Control de Intentos',
    description: 'El usuario envía sus credenciales sobre TLS 1.3. La contraseña se valida contra el hash Bcrypt sin exponerse en memoria.',
    codeSnippet: 'POST /api/v1/auth/login\n{\n  "correo": "admin@saed.com",\n  "passwordHash": "$2a$12$e8Y..."\n}',
  },
  {
    step: '02',
    name: 'JWT Stateless',
    detail: 'Firma HMAC-SHA256',
    tech: 'Tokens criptográficos sin estado',
    description: 'Se emite un token JWT criptográficamente sellado con roles, expiración corta y clave de firma rotativa.',
    codeSnippet: 'JWT Payload:\n{\n  "sub": "user_492",\n  "rol": "ADMIN_PROPIEDAD",\n  "exp": 1725642000\n}',
  },
  {
    step: '03',
    name: 'Contexto de Sesión',
    detail: 'X-Assignment-Id',
    tech: 'SaedContextHolder (ThreadLocal)',
    description: 'Cada petición HTTP inyecta la asignación activa. Spring Boot valida que el usuario pertenezca formalmente a esa copropiedad.',
    codeSnippet: 'Header:\nX-Assignment-Id: 101\n\nSaedContextHolder.setContext(\n  tenantId: 101, role: "ADMIN_PROPIEDAD"\n);',
  },
  {
    step: '04',
    name: 'Motor RLS / VPD',
    detail: 'Políticas en BD',
    tech: 'Oracle Virtual Private Database',
    description: 'A nivel del kernel relacional, Oracle intercepta la consulta SQL antes de parsearla e inyecta dinámicamente la función de predicado.',
    codeSnippet: 'DBMS_RLS.ADD_POLICY(\n  object_name => \'PERSONAS\',\n  policy_function => \'FN_FILTRO_PROPIEDAD\'\n);',
  },
  {
    step: '05',
    name: 'Tenant Aislado',
    detail: 'Filtro por Propiedad',
    tech: 'SYS_CONTEXT(\'SAED_CTX\')',
    description: 'La cláusula WHERE se evalúa con el contexto seguro de sesión en memoria SGA. Es físicamente imposible omitir este filtro.',
    codeSnippet: 'SQL Reescrito por el Motor:\nSELECT * FROM PERSONAS\nWHERE PROPIEDAD_ID = SYS_CONTEXT(\'SAED_CTX\', \'PROPIEDAD_ID\');',
  },
  {
    step: '06',
    name: 'Datos Protegidos',
    detail: 'Cero fuga de información',
    tech: 'Zero-Trust Data Delivery',
    description: 'El cliente recibe únicamente los registros de su copropiedad. Las copropiedades vecinas quedan matemáticamente invisibles.',
    codeSnippet: 'Status: 200 OK\nPayload: {\n  "registros": 3,\n  "fugasDetectadas": 0,\n  "aislamiento": "100% Hermético"\n}',
  },
];

const SECURITY_PILLARS = [
  {
    icon: Database,
    title: 'Aislamiento Multi-Tenant en BD',
    badge: 'Virtual Private Database',
    desc: 'Políticas de VPD / RLS directamente en el motor de base de datos relacional. Ninguna consulta SQL puede leer o alterar datos de otra copropiedad, incluso ante errores de código de aplicación.',
  },
  {
    icon: Lock,
    title: 'Autenticación Criptográfica JWT',
    badge: 'HMAC-SHA256',
    desc: 'Tokens de sesión sin estado (stateless) protegidos con firmas criptográficas, expiración controlada y validación de contexto de asignación en cada petición a los servicios REST.',
  },
  {
    icon: Key,
    title: 'Control de Acceso por Roles (RBAC)',
    badge: '5 Perfiles Aislados',
    desc: 'Matriz estricta de autorización para SuperAdmin, Admin Organización, Admin Propiedad, Portero y Residente. Endpoints y vistas aplican validación dual independiente.',
  },
  {
    icon: Layers,
    title: 'Trazabilidad y Auditoría Transaccional',
    badge: 'Logs Inmutables',
    desc: 'Registro cronológico con operador, fecha, IP y contexto para cada evento sensible: validaciones de QR, entregas de paquetes con PIN y conciliaciones de cartera.',
  },
];

export default function LandingSecurity() {
  const [activeStep, setActiveStep] = useState(3);
  const [isPlayingPipeline, setIsPlayingPipeline] = useState(true);
  const [simMode, setSimMode] = useState('legit'); // 'legit' | 'attack'

  const orb1Ref = useRef(null);
  const orb2Ref = useRef(null);
  const terminalBodyRef = useRef(null);
  const activeStepCardRef = useRef(null);

  const containerRef = useScrollReveal({
    selector: '.security-card',
    stagger: 80,
    distance: 24,
  });

  // Ambient breathing orbs
  useEffect(() => {
    if (orb1Ref.current && orb2Ref.current) {
      animate(orb1Ref.current, {
        translateY: [-20, 20],
        translateX: [-15, 15],
        scale: [1, 1.15],
        duration: 8000,
        direction: 'alternate',
        loop: true,
        ease: 'inOutSine',
      });

      animate(orb2Ref.current, {
        translateY: [25, -25],
        translateX: [18, -18],
        scale: [1.1, 0.95],
        duration: 9500,
        direction: 'alternate',
        loop: true,
        ease: 'inOutSine',
      });
    }
  }, []);

  // Automated pipeline sequencer
  useEffect(() => {
    if (!isPlayingPipeline) return;

    const timer = setInterval(() => {
      setActiveStep((prev) => (prev + 1) % SECURITY_PIPELINE.length);
    }, 3800);

    return () => clearInterval(timer);
  }, [isPlayingPipeline]);

  // Anime.js trigger when step changes
  useEffect(() => {
    if (activeStepCardRef.current) {
      animate(activeStepCardRef.current, {
        opacity: [0.3, 1],
        translateY: [8, 0],
        duration: 400,
        ease: 'outExpo',
      });
    }
  }, [activeStep]);

  // Anime.js trigger when simulator mode toggles
  useEffect(() => {
    if (terminalBodyRef.current) {
      animate(terminalBodyRef.current, {
        opacity: [0.4, 1],
        translateY: [10, 0],
        duration: 450,
        ease: 'outExpo',
      });
    }
  }, [simMode]);

  const currentStepData = SECURITY_PIPELINE[activeStep];

  return (
    <section
      id="seguridad"
      className="py-20 sm:py-28 lg:py-32 bg-[#070B14] text-white relative border-t border-slate-800/80 overflow-hidden"
    >
      {/* Dynamic Ambient Glowing Orbs with Anime.js */}
      <div
        ref={orb1Ref}
        className="absolute top-1/4 left-1/4 -translate-x-1/2 -translate-y-1/2 w-[650px] h-[350px] bg-sky-500/10 blur-[150px] rounded-full pointer-events-none"
      />
      <div
        ref={orb2Ref}
        className="absolute bottom-1/4 right-1/4 translate-x-1/3 translate-y-1/3 w-[550px] h-[320px] bg-emerald-500/10 blur-[160px] rounded-full pointer-events-none"
      />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-16 sm:mb-20">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-sky-400 bg-sky-500/10 border border-sky-500/20 shadow-sm shadow-sky-500/10">
            <Sparkles className="w-3.5 h-3.5 text-sky-400 animate-pulse" />
            SEGURIDAD Y AISLAMIENTO DE DATOS
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Seguridad garantizada desde el motor relacional.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            El aislamiento de cada copropiedad no se confía a filtros en pantalla. Está protegido en la base de datos con políticas VPD / RLS y autenticación criptográfica.
          </p>
        </div>

        {/* ========================================================================= */}
        {/* Interactive 6-Step Security Pipeline with Live Inspector */}
        {/* ========================================================================= */}
        <div className="max-w-5xl mx-auto mb-16 sm:mb-20">
          <div className="p-6 sm:p-8 rounded-3xl bg-slate-900/75 backdrop-blur-xl border border-slate-800/90 shadow-2xl space-y-6">
            
            {/* Pipeline Header Controls */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800/80 pb-4">
              <div className="flex items-center gap-2.5">
                <div className="w-7 h-7 rounded-lg bg-sky-500/10 border border-sky-500/30 flex items-center justify-center text-sky-400">
                  <ShieldCheck className="w-4 h-4" />
                </div>
                <div>
                  <span className="text-xs font-bold uppercase tracking-wider text-sky-400 block">
                    Pipeline de Seguridad y Resolución Cero-Fugas
                  </span>
                  <span className="text-[11px] text-slate-400">Flujo transaccional inspeccionable paso a paso</span>
                </div>
              </div>

              <div className="flex items-center gap-2 self-start sm:self-auto">
                <button
                  type="button"
                  onClick={() => setIsPlayingPipeline((v) => !v)}
                  className="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 text-xs font-medium flex items-center gap-1.5 transition-colors"
                  title={isPlayingPipeline ? 'Pausar secuencia automática' : 'Reanudar secuencia automática'}
                >
                  {isPlayingPipeline ? (
                    <>
                      <Pause className="w-3 h-3 text-sky-400" />
                      <span className="text-[11px]">Pausar</span>
                    </>
                  ) : (
                    <>
                      <Play className="w-3 h-3 text-emerald-400" />
                      <span className="text-[11px]">Animar</span>
                    </>
                  )}
                </button>
                <span className="text-[11px] font-mono text-slate-500 hidden sm:inline">Paso {activeStep + 1}/6</span>
              </div>
            </div>

            {/* 6 Sequential Step Tabs */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5">
              {SECURITY_PIPELINE.map((p, index) => {
                const isActive = activeStep === index;
                return (
                  <button
                    key={p.step}
                    type="button"
                    onClick={() => {
                      setActiveStep(index);
                      setIsPlayingPipeline(false);
                    }}
                    className={`p-3 rounded-xl text-center space-y-1 relative transition-all duration-300 text-left sm:text-center group border ${
                      isActive
                        ? 'bg-sky-950/60 border-sky-400/80 shadow-lg shadow-sky-500/10 ring-1 ring-sky-400/50 scale-[1.02]'
                        : 'bg-slate-950/60 border-slate-800/80 hover:border-slate-700 hover:bg-slate-900/60'
                    }`}
                  >
                    <div className="flex items-center justify-between sm:justify-center gap-1">
                      <span className={`text-[10px] font-mono font-bold ${isActive ? 'text-sky-300' : 'text-slate-500'}`}>
                        {p.step}
                      </span>
                      {isActive && (
                        <span className="w-1.5 h-1.5 rounded-full bg-sky-400 animate-ping inline-block" />
                      )}
                    </div>
                    <p className={`text-xs font-bold transition-colors ${isActive ? 'text-white' : 'text-slate-300 group-hover:text-white'}`}>
                      {p.name}
                    </p>
                    <p className="text-[10px] text-slate-400 leading-tight line-clamp-1">{p.detail}</p>
                  </button>
                );
              })}
            </div>

            {/* Active Step Deep-Dive Inspector Panel */}
            <div
              ref={activeStepCardRef}
              className="p-4 sm:p-5 rounded-2xl bg-slate-950/80 border border-sky-500/30 grid grid-cols-1 lg:grid-cols-12 gap-4 items-center shadow-inner"
            >
              <div className="lg:col-span-7 space-y-2">
                <div className="flex items-center gap-2">
                  <span className="px-2.5 py-0.5 rounded-md text-[10px] font-mono font-bold bg-sky-500/20 text-sky-300 border border-sky-500/30">
                    PASO {currentStepData.step} • {currentStepData.tech}
                  </span>
                </div>
                <h4 className="text-base font-bold text-white font-['Plus_Jakarta_Sans'] flex items-center gap-2">
                  <span>{currentStepData.name}</span>
                  <ArrowRight className="w-4 h-4 text-sky-400 shrink-0" />
                  <span className="text-xs font-mono font-normal text-slate-400">{currentStepData.detail}</span>
                </h4>
                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                  {currentStepData.description}
                </p>
              </div>

              <div className="lg:col-span-5 bg-[#0A0F1D] p-3.5 rounded-xl border border-slate-800 text-[11px] font-mono text-sky-300 overflow-x-auto shadow-sm">
                <div className="flex items-center justify-between pb-2 mb-2 border-b border-slate-800/80 text-[10px] text-slate-500 font-bold uppercase">
                  <span>Inspección Técnica</span>
                  <Code2 className="w-3.5 h-3.5 text-slate-400" />
                </div>
                <pre className="whitespace-pre-wrap leading-relaxed text-slate-200">
                  {currentStepData.codeSnippet}
                </pre>
              </div>
            </div>

          </div>
        </div>

        {/* ========================================================================= */}
        {/* Interactive Terminal Simulator: Zero-Trust Oracle VPD vs Cross-Tenant */}
        {/* ========================================================================= */}
        <div className="max-w-5xl mx-auto mb-16 sm:mb-20">
          <div className="rounded-3xl bg-slate-900/90 border border-slate-800 shadow-2xl overflow-hidden backdrop-blur-xl">
            
            {/* Terminal Top Bar */}
            <div className="p-4 sm:p-5 bg-slate-950 border-b border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-center gap-2.5">
                <div className="flex gap-1.5">
                  <div className="w-3 h-3 rounded-full bg-rose-500/80" />
                  <div className="w-3 h-3 rounded-full bg-amber-500/80" />
                  <div className="w-3 h-3 rounded-full bg-emerald-500/80" />
                </div>
                <span className="text-xs font-mono text-slate-400 font-bold flex items-center gap-1.5 ml-2">
                  <Terminal className="w-3.5 h-3.5 text-sky-400" />
                  SIMULADOR INTERACTIVO DE AISLAMIENTO MULTI-TENANT
                </span>
              </div>

              {/* Mode Toggle Tabs */}
              <div className="flex items-center p-1 bg-slate-900 rounded-xl border border-slate-800 self-start sm:self-auto">
                <button
                  type="button"
                  onClick={() => setSimMode('legit')}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all flex items-center gap-1.5 ${
                    simMode === 'legit'
                      ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 shadow-sm'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                  <span>Consulta Legítima</span>
                </button>
                <button
                  type="button"
                  onClick={() => setSimMode('attack')}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all flex items-center gap-1.5 ${
                    simMode === 'attack'
                      ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40 shadow-sm'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  <ShieldAlert className="w-3.5 h-3.5 text-rose-400" />
                  <span>Simulación Cross-Tenant</span>
                </button>
              </div>
            </div>

            {/* Terminal Body Content */}
            <div ref={terminalBodyRef} className="p-6 sm:p-8 space-y-6">
              {simMode === 'legit' ? (
                <div className="space-y-5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="px-2.5 py-0.5 rounded text-[11px] font-mono font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                          200 OK
                        </span>
                        <span className="text-xs font-bold text-white">
                          Edificio Mirador del Parque (Propiedad ID: 101)
                        </span>
                      </div>
                      <p className="text-xs text-slate-400">
                        El administrador consulta residentes de su propia copropiedad debidamente autenticado.
                      </p>
                    </div>

                    <div className="px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-semibold flex items-center gap-1.5">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>Zero-Trust Aprobado (0 Fugas)</span>
                    </div>
                  </div>

                  {/* SQL Execution Block */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs font-mono">
                    <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2">
                      <span className="text-[10px] text-slate-500 uppercase font-bold tracking-wider block">
                        Petición HTTP &amp; Contexto Recibido
                      </span>
                      <p className="text-sky-300">GET /api/v1/personas/residentes</p>
                      <p className="text-slate-400">Header: X-Assignment-Id: 101</p>
                      <p className="text-emerald-400">SYS_CONTEXT(&apos;SAED_CTX&apos;, &apos;PROPIEDAD_ID&apos;) = 101</p>
                    </div>

                    <div className="p-4 rounded-xl bg-slate-950 border border-emerald-500/30 space-y-2">
                      <span className="text-[10px] text-emerald-400 uppercase font-bold tracking-wider block">
                        Predicado Inyectado por Oracle VPD
                      </span>
                      <p className="text-slate-300">WHERE ROL = &apos;RESIDENTE&apos;</p>
                      <p className="text-emerald-300 font-bold bg-emerald-950/40 p-1 rounded border border-emerald-500/30">
                        AND PROPIEDAD_ID = 101
                      </p>
                      <p className="text-[11px] text-slate-400">Resultado: 3 residentes devueltos de Torre 1.</p>
                    </div>
                  </div>

                  {/* Simulated Result Records */}
                  <div className="overflow-x-auto rounded-xl border border-slate-800">
                    <table className="w-full text-left text-xs font-mono">
                      <thead className="bg-slate-950 text-slate-400 border-b border-slate-800">
                        <tr>
                          <th className="p-2.5">ID</th>
                          <th className="p-2.5">Nombre</th>
                          <th className="p-2.5">Unidad</th>
                          <th className="p-2.5">Copropiedad</th>
                          <th className="p-2.5 text-right">Estado RLS</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/60 bg-slate-950/50">
                        <tr>
                          <td className="p-2.5 text-sky-400">#401</td>
                          <td className="p-2.5 text-white font-sans font-medium">Carlos Mendoza</td>
                          <td className="p-2.5 text-slate-300">Apto 101</td>
                          <td className="p-2.5 text-emerald-400 font-sans">Mirador del Parque (101)</td>
                          <td className="p-2.5 text-right text-emerald-400">Filtrado Seguro</td>
                        </tr>
                        <tr>
                          <td className="p-2.5 text-sky-400">#402</td>
                          <td className="p-2.5 text-white font-sans font-medium">Lucía Gómez</td>
                          <td className="p-2.5 text-slate-300">Apto 102</td>
                          <td className="p-2.5 text-emerald-400 font-sans">Mirador del Parque (101)</td>
                          <td className="p-2.5 text-right text-emerald-400">Filtrado Seguro</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              ) : (
                <div className="space-y-5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="px-2.5 py-0.5 rounded text-[11px] font-mono font-bold bg-rose-500/20 text-rose-300 border border-rose-500/40">
                          BLOQUEO AUTOMÁTICO EN KERNEL
                        </span>
                        <span className="text-xs font-bold text-white">
                          Intento de Inyección o Fuga hacia Copropiedad Ajena (ID: 205)
                        </span>
                      </div>
                      <p className="text-xs text-slate-400">
                        Un cliente o usuario malintencionado intenta solicitar datos de &quot;Condominio Los Robles (205)&quot; estando autenticado en la copropiedad 101.
                      </p>
                    </div>

                    <div className="px-3 py-1 rounded-full bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs font-semibold flex items-center gap-1.5">
                      <XCircle className="w-3.5 h-3.5" />
                      <span>Acceso Denegado por Predicado VPD</span>
                    </div>
                  </div>

                  {/* SQL Execution Block */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs font-mono">
                    <div className="p-4 rounded-xl bg-slate-950 border border-rose-500/30 space-y-2">
                      <span className="text-[10px] text-rose-400 uppercase font-bold tracking-wider block">
                        Intento No Autorizado
                      </span>
                      <p className="text-rose-300">GET /api/v1/personas/residentes?propiedadId=205</p>
                      <p className="text-slate-400">Sesión Real: X-Assignment-Id: 101</p>
                      <p className="text-rose-400 font-bold">Contexto de Motor: SAED_CTX = 101</p>
                    </div>

                    <div className="p-4 rounded-xl bg-slate-950 border border-slate-800 space-y-2">
                      <span className="text-[10px] text-slate-400 uppercase font-bold tracking-wider block">
                        Evaluación Matemática en Base de Datos
                      </span>
                      <p className="text-slate-300">WHERE PROPIEDAD_ID = 205</p>
                      <p className="text-rose-300 font-bold bg-rose-950/40 p-1 rounded border border-rose-500/30">
                        AND (PROPIEDAD_ID = 101) &rarr; EVALÚA FALSE
                      </p>
                      <p className="text-[11px] text-emerald-400">Filas retornadas: 0 (Imposible fuga de datos)</p>
                    </div>
                  </div>

                  {/* Security Log Output */}
                  <div className="p-3.5 rounded-xl bg-slate-950 border border-slate-800 font-mono text-[11px] text-slate-300 space-y-1">
                    <p className="text-rose-400 font-bold flex items-center gap-1.5">
                      <ShieldAlert className="w-4 h-4" />
                      [ALERTA DE AUDITORÍA REGISTRADA]
                    </p>
                    <p className="text-slate-400">
                      Evento registrado en `SAED_SEC_MASTER.AUDITORIA_ACCESOS`: Intento cross-tenant bloqueado. Cero bytes de copropiedades ajenas transmitidos.
                    </p>
                  </div>
                </div>
              )}
            </div>

          </div>
        </div>

        {/* ========================================================================= */}
        {/* 4 Pillars Grid with Interactive Hover Tilt & Badges */}
        {/* ========================================================================= */}
        <div ref={containerRef} className="grid grid-cols-1 md:grid-cols-2 gap-5 max-w-5xl mx-auto mb-16 sm:mb-20">
          {SECURITY_PILLARS.map((p) => {
            const Icon = p.icon;
            return (
              <div
                key={p.title}
                className="security-card p-6 sm:p-7 rounded-2xl bg-slate-900/60 backdrop-blur-md border border-slate-800/90 hover:border-sky-500/40 hover:shadow-xl hover:shadow-sky-500/5 hover:-translate-y-1 transition-all duration-300 space-y-3 group"
              >
                <div className="flex items-center justify-between">
                  <div className="w-10 h-10 rounded-xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-400 group-hover:scale-110 transition-transform">
                    <Icon className="w-5 h-5" />
                  </div>
                  <span className="text-[11px] font-mono font-bold px-2.5 py-0.5 rounded-md bg-slate-800 text-sky-300 border border-slate-700">
                    {p.badge}
                  </span>
                </div>
                <h3 className="text-base font-bold text-white font-['Plus_Jakarta_Sans'] group-hover:text-sky-300 transition-colors">
                  {p.title}
                </h3>
                <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
                  {p.desc}
                </p>
              </div>
            );
          })}
        </div>

        {/* ========================================================================= */}
        {/* Ley 675 Institutional Block */}
        {/* ========================================================================= */}
        <div className="max-w-4xl mx-auto p-6 sm:p-8 rounded-3xl bg-gradient-to-r from-slate-900 via-slate-900/90 to-slate-900 border border-amber-500/20 shadow-xl flex flex-col sm:flex-row items-start sm:items-center gap-5 relative overflow-hidden">
          <div className="absolute top-0 right-0 w-48 h-48 bg-amber-500/5 blur-3xl rounded-full pointer-events-none" />
          <div className="w-12 h-12 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-400 shrink-0 shadow-inner">
            <Scale className="w-6 h-6" />
          </div>
          <div className="space-y-1 relative z-10">
            <span className="text-[11px] font-bold uppercase tracking-wider text-amber-400 block">
              Marco Jurídico Colombiano Vigente
            </span>
            <h4 className="text-base font-bold text-white font-['Plus_Jakarta_Sans']">
              Diseñado estrictamente bajo la Ley 675 de 2001 (Régimen de Propiedad Horizontal)
            </h4>
            <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
              Estructurado para respaldar el censo oficial de copropietarios, coeficientes de copropiedad, quórum calificado de asambleas ordinarias y extraordinarias, y rendición de cuentas inalterable conforme a la legislación colombiana.
            </p>
          </div>
        </div>

      </div>
    </section>
  );
}
