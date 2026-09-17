import { useState, useEffect, useRef, Fragment } from 'react';
import {
  Check,
  ArrowRight,
  Building2,
  Layers,
  Crown,
  ChevronDown,
  Sparkles,
  Sliders,
  Calendar,
  RefreshCw,
  AlertCircle,
  HardDrive,
  Users,
  Building,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { animate } from 'animejs';
import { useScrollReveal } from '../../lib/animations.js';
import api from '../../lib/api.js';

function formatCOP(val) {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(val);
}

export default function LandingPricing() {
  const [billingCycle, setBillingCycle] = useState('annual'); // 'monthly' | 'annual'
  const [unitsCount, setUnitsCount] = useState(80);
  const [showMatrix, setShowMatrix] = useState(false);

  // Dynamic API state — Oracle es la única fuente de verdad
  const [planes, setPlanes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const orbRef = useRef(null);
  const matrixContainerRef = useRef(null);
  const cardsRef = useScrollReveal({
    selector: '.pricing-card',
    stagger: 100,
    distance: 28,
  });

  // Carga del catálogo comercial oficial desde el backend
  const fetchPlanes = async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await api.get('/planes?solo_activos=true');
      const data = res?.data || res || [];
      if (Array.isArray(data) && data.length > 0) {
        setPlanes(data);
      } else {
        setError('El catálogo no contiene planes activos disponibles en este momento.');
        setPlanes([]);
      }
    } catch (err) {
      console.error('Error al consultar el catálogo de planes comerciales:', err);
      setError('No fue posible conectar con el catálogo oficial de planes. Por favor verifica tu conexión y reintenta.');
      setPlanes([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPlanes();
  }, []);

  // Ambient breathing orb
  useEffect(() => {
    if (orbRef.current) {
      animate(orbRef.current, {
        translateY: [-20, 20],
        scale: [1, 1.12],
        duration: 9000,
        direction: 'alternate',
        loop: true,
        ease: 'inOutSine',
      });
    }
  }, []);

  // Animate Matrix table reveal when toggled
  useEffect(() => {
    if (showMatrix && matrixContainerRef.current) {
      animate(matrixContainerRef.current, {
        opacity: [0, 1],
        translateY: [20, 0],
        duration: 500,
        ease: 'outExpo',
      });
    }
  }, [showMatrix]);

  // =========================================================================
  // Estimador de Escala: Derivado 100% de los límites reales de Oracle
  // =========================================================================
  const sortedPlans = [...planes].sort(
    (a, b) =>
      Number(a.limiteUnidades || a.LIMITE_UNIDADES || 0) -
      Number(b.limiteUnidades || b.LIMITE_UNIDADES || 0)
  );

  // El plan recomendado es el primer plan que cubre la cantidad de unidades seleccionada
  const recommendedPlan =
    sortedPlans.find(
      (p) => Number(p.limiteUnidades || p.LIMITE_UNIDADES || 0) >= unitsCount
    ) || (sortedPlans.length > 0 ? sortedPlans[sortedPlans.length - 1] : null);

  const recommendedPlanCode = (
    recommendedPlan?.codigo ||
    recommendedPlan?.CODIGO ||
    'PRO'
  ).toUpperCase();

  const getPlanIcon = (codigo) => {
    switch ((codigo || '').toUpperCase()) {
      case 'FREE':
        return Building2;
      case 'PRO':
        return Layers;
      case 'ENTERPRISE':
        return Crown;
      default:
        return Building2;
    }
  };

  const getPlanTag = (codigo) => {
    switch ((codigo || '').toUpperCase()) {
      case 'FREE':
        return 'Para Comenzar';
      case 'PRO':
        return 'Más Elegido';
      case 'ENTERPRISE':
        return 'Corporativo';
      default:
        return 'Plan Comercial';
    }
  };

  const getPlanCtaText = (codigo) => {
    switch ((codigo || '').toUpperCase()) {
      case 'FREE':
        return 'Comenzar 14 días gratis';
      case 'PRO':
        return 'Suscribir Plan Profesional';
      case 'ENTERPRISE':
        return 'Suscribir Plan Empresarial';
      default:
        return 'Seleccionar Plan';
    }
  };

  // Planes por código para la matriz comparativa
  const freePlan = planes.find((p) => (p.codigo || p.CODIGO || '').toUpperCase() === 'FREE');
  const proPlan = planes.find((p) => (p.codigo || p.CODIGO || '').toUpperCase() === 'PRO');
  const entPlan = planes.find((p) => (p.codigo || p.CODIGO || '').toUpperCase() === 'ENTERPRISE');

  const getPropLimit = (p, fb) => (p?.limitePropiedades || p?.LIMITE_PROPIEDADES ? String(p.limitePropiedades || p.LIMITE_PROPIEDADES) : fb);
  const getUnitLimit = (p, fb) => (p?.limiteUnidades || p?.LIMITE_UNIDADES ? String(p.limiteUnidades || p.LIMITE_UNIDADES) : fb);
  const getUserLimit = (p, fb) => (p?.limiteUsuarios || p?.LIMITE_USUARIOS ? String(p.limiteUsuarios || p.LIMITE_USUARIOS) : fb);
  const getStorageLimit = (p, fb) => (p?.limiteAlmacenamientoGb || p?.LIMITE_ALMACENAMIENTO_GB ? `${p.limiteAlmacenamientoGb || p.LIMITE_ALMACENAMIENTO_GB} GB` : fb);

  const hasModule = (plan, modCode) => {
    if (!plan) return false;
    const mods = plan.modulosCodigos || plan.MODULOS_CODIGOS || [];
    return mods.includes(modCode);
  };

  const matrixCategories = [
    {
      name: 'Control de Acceso y Garita',
      items: [
        { feature: 'Pases de visita con código QR dinámico', free: true, pro: true, enterprise: true },
        { feature: 'Consola web para garita (PC o tablet)', free: true, pro: true, enterprise: true },
        {
          feature: 'Directorio de copropiedad y censo de unidades',
          free: `Hasta ${getUnitLimit(freePlan, '10')}`,
          pro: `Hasta ${getUnitLimit(proPlan, '100')}`,
          enterprise: `Hasta ${getUnitLimit(entPlan, '9.999')}`,
        },
        { feature: 'Control de bahías de parqueadero y visitantes', free: hasModule(freePlan, 'PARQUEADEROS'), pro: hasModule(proPlan, 'PARQUEADEROS') || true, enterprise: true },
      ],
    },
    {
      name: 'Operaciones y Entitlements Modulares',
      items: [
        { feature: 'Custodia de paquetes con PIN de 6 dígitos', free: hasModule(freePlan, 'PAQUETES'), pro: hasModule(proPlan, 'PAQUETES') || true, enterprise: true },
        { feature: 'Radicación de PQRS con trazabilidad de respuesta', free: hasModule(freePlan, 'PQRS'), pro: hasModule(proPlan, 'PQRS') || true, enterprise: true },
        { feature: 'Reservas de zonas comunes y amenidades', free: hasModule(freePlan, 'RESERVAS'), pro: hasModule(proPlan, 'RESERVAS') || true, enterprise: true },
        { feature: 'Gestión de obras y reformas privadas', free: hasModule(freePlan, 'OBRAS'), pro: hasModule(proPlan, 'OBRAS') || true, enterprise: true },
        { feature: 'Control de pólizas de seguro de copropiedad', free: hasModule(freePlan, 'POLIZAS'), pro: hasModule(proPlan, 'POLIZAS') || true, enterprise: true },
      ],
    },
    {
      name: 'Finanzas y Recaudo Digital',
      items: [
        { feature: 'Emisión digital de estados de cuenta', free: true, pro: true, enterprise: true },
        { feature: 'Pasarela de pagos en línea Wompi (PSE / Tarjetas)', free: hasModule(freePlan, 'FINANZAS'), pro: hasModule(proPlan, 'FINANZAS') || true, enterprise: true },
        { feature: 'Conciliación bancaria y recibos de caja automáticos', free: false, pro: true, enterprise: true },
      ],
    },
    {
      name: 'Límites Modelo C y Almacenamiento (GAP-ENT-06)',
      items: [
        {
          feature: 'Límite de copropiedades permitidas',
          free: getPropLimit(freePlan, '1'),
          pro: `Hasta ${getPropLimit(proPlan, '5')}`,
          enterprise: `Hasta ${getPropLimit(entPlan, '999')}`,
        },
        {
          feature: 'Límite de unidades residenciales',
          free: getUnitLimit(freePlan, '10'),
          pro: getUnitLimit(proPlan, '100'),
          enterprise: getUnitLimit(entPlan, '9.999'),
        },
        {
          feature: 'Límite de usuarios autorizados',
          free: getUserLimit(freePlan, '5'),
          pro: getUserLimit(proPlan, '50'),
          enterprise: getUserLimit(entPlan, '999'),
        },
        {
          feature: 'Cuota de almacenamiento global',
          free: getStorageLimit(freePlan, '1 GB'),
          pro: getStorageLimit(proPlan, '10 GB'),
          enterprise: getStorageLimit(entPlan, '100 GB'),
        },
        { feature: 'Asambleas y votaciones Ley 675', free: hasModule(freePlan, 'ASAMBLEAS'), pro: hasModule(proPlan, 'ASAMBLEAS'), enterprise: hasModule(entPlan, 'ASAMBLEAS') || true },
        { feature: 'Aislamiento de base de datos (Oracle RLS VPD)', free: true, pro: true, enterprise: true },
      ],
    },
  ];

  return (
    <section
      id="planes"
      className="py-20 sm:py-28 lg:py-32 bg-[#061018] text-white relative border-t border-cyan-400/10 overflow-hidden"
    >
      {/* Ambient Breathing Background Glow */}
      <div
        ref={orbRef}
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[750px] h-[400px] bg-sky-500/5 blur-[160px] rounded-full pointer-events-none"
      />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
        {/* Section Header */}
        <div className="max-w-3xl mx-auto text-center space-y-4 mb-14 sm:mb-16">
          <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-bold uppercase tracking-widest text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 shadow-sm shadow-emerald-500/10">
            <Sparkles className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
            TARIFAS TRANSPARENTES Y ESCALABLES
          </span>

          <h2 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white leading-tight font-['Plus_Jakarta_Sans']">
            Inversión ajustada al tamaño exacto de tu copropiedad.
          </h2>

          <p className="text-base sm:text-lg text-slate-300 font-normal leading-relaxed max-w-2xl mx-auto">
            Sin costos ocultos por hardware propietario ni contratos de permanencia forzosa. Escala de licenciamiento transparente calculada por copropiedad y capacidad residencial.
          </p>

          {/* Billing Cycle Switcher */}
          <div className="pt-4 flex flex-col sm:flex-row items-center justify-center gap-3">
            <div className="p-1 bg-slate-900/90 rounded-2xl border border-slate-800 flex items-center shadow-inner">
              <button
                type="button"
                onClick={() => setBillingCycle('monthly')}
                className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all ${
                  billingCycle === 'monthly'
                    ? 'bg-slate-800 text-white shadow-sm border border-slate-700'
                    : 'text-slate-300 hover:text-white'
                }`}
              >
                Facturación Mensual
              </button>
              <button
                type="button"
                onClick={() => setBillingCycle('annual')}
                className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all flex items-center gap-2 ${
                  billingCycle === 'annual'
                    ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/40 shadow-sm'
                    : 'text-slate-300 hover:text-white'
                }`}
              >
                <span>Facturación Anual</span>
                <span className="px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-emerald-400 text-[#061525]">
                  -20% DCTO
                </span>
              </button>
            </div>
          </div>
        </div>

        {/* ========================================================================= */}
        {/* UI STATES: LOADING / ERROR / EMPTY / SUCCESS                             */}
        {/* ========================================================================= */}

        {loading ? (
          /* SKELETON LOADING STATE */
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 max-w-6xl mx-auto mb-16">
            {[1, 2, 3].map((idx) => (
              <div
                key={idx}
                className="p-6 sm:p-8 rounded-3xl border border-slate-800 bg-slate-900/40 animate-pulse flex flex-col justify-between space-y-6 min-h-[480px]"
              >
                <div className="space-y-4">
                  <div className="flex justify-between items-center">
                    <div className="w-12 h-12 rounded-2xl bg-slate-800" />
                    <div className="w-24 h-6 rounded-full bg-slate-800" />
                  </div>
                  <div className="w-3/4 h-7 rounded-lg bg-slate-800" />
                  <div className="w-full h-12 rounded-lg bg-slate-800/60" />
                  <div className="w-full h-16 rounded-2xl bg-slate-800/80" />
                  <div className="space-y-2 pt-4">
                    <div className="w-full h-4 rounded bg-slate-800" />
                    <div className="w-5/6 h-4 rounded bg-slate-800" />
                    <div className="w-4/6 h-4 rounded bg-slate-800" />
                  </div>
                </div>
                <div className="w-full h-12 rounded-xl bg-slate-800" />
              </div>
            ))}
          </div>
        ) : error ? (
          /* ERROR STATE CON REINTENTO */
          <div className="max-w-md mx-auto mb-16 p-8 rounded-3xl bg-red-950/30 border border-red-800/60 text-center space-y-4 shadow-xl">
            <AlertCircle className="w-10 h-10 text-red-400 mx-auto" />
            <h3 className="text-lg font-bold text-white">No se pudo cargar el catálogo</h3>
            <p className="text-xs text-slate-300 leading-relaxed">{error}</p>
            <button
              type="button"
              onClick={fetchPlanes}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-white text-xs font-bold border border-slate-700 transition-all shadow-md"
            >
              <RefreshCw className="w-4 h-4" />
              Reintentar conexión al catálogo
            </button>
          </div>
        ) : planes.length === 0 ? (
          /* EMPTY STATE */
          <div className="max-w-md mx-auto mb-16 p-8 rounded-3xl bg-slate-900/60 border border-slate-800 text-center space-y-3">
            <Building className="w-10 h-10 text-slate-500 mx-auto" />
            <h3 className="text-lg font-bold text-white">Catálogo temporalmente no disponible</h3>
            <p className="text-xs text-slate-400">Pronto se habilitarán nuevos planes para suscripción.</p>
          </div>
        ) : (
          /* SUCCESS: 3 TIER PLAN CARDS */
          <div ref={cardsRef} className="grid grid-cols-1 lg:grid-cols-3 gap-6 max-w-6xl mx-auto mb-16">
            {planes.map((plan) => {
              const codigo = (plan.codigo || plan.CODIGO || '').toUpperCase();
              const Icon = getPlanIcon(codigo);
              const tag = getPlanTag(codigo);
              const ctaText = getPlanCtaText(codigo);
              const isRecommended = codigo === recommendedPlanCode;

              // Precios canónicos dinámicos de Oracle
              const precioMensual = Number(plan.precioMensual || plan.PRECIO_MENSUAL || 0);
              const precioAnual = Number(
                plan.precioAnual || plan.PRECIO_ANUAL || Math.round(precioMensual * 12 * 0.8)
              );
              const precioEquivMensual =
                precioMensual > 0 ? Math.round(precioAnual / 12) : 0;

              // Límites canónicos Modelo C
              const limProp = plan.limitePropiedades || plan.LIMITE_PROPIEDADES || 1;
              const limUni = plan.limiteUnidades || plan.LIMITE_UNIDADES || 10;
              const limStorage = plan.limiteAlmacenamientoGb || plan.LIMITE_ALMACENAMIENTO_GB || 1;

              // Features dinámicas desde backend o generadas con límites reales
              const featureList =
                Array.isArray(plan.features) && plan.features.length > 0
                  ? plan.features
                  : [
                      limProp === 1 ? '1 Copropiedad' : `Hasta ${limProp} copropiedades`,
                      `Hasta ${limUni} unidades residenciales`,
                      `${limStorage} GB almacenamiento seguro`,
                      'Pases de visita con código QR dinámico',
                    ];

              return (
                <div
                  key={plan.idPlan || plan.ID_PLAN || codigo}
                  className={`pricing-card p-5 sm:p-8 rounded-3xl border flex flex-col justify-between transition-all duration-300 relative ${
                    isRecommended
                      ? 'bg-gradient-to-b from-[#0F224A] via-[#09152E] to-[#070E1E] border-sky-400/80 shadow-2xl shadow-sky-950/60 lg:-translate-y-2 ring-1 ring-sky-400/40'
                      : 'bg-slate-900/60 backdrop-blur-md border-slate-800 hover:border-slate-700 hover:shadow-xl hover:-translate-y-1'
                  }`}
                >
                  {/* Floating Recommended Pill */}
                  {isRecommended && (
                    <div className="absolute -top-3 left-1/2 -translate-x-1/2 px-3 py-0.5 rounded-full bg-gradient-to-r from-sky-500 to-emerald-500 text-slate-950 text-[10px] font-extrabold uppercase tracking-wider shadow-md">
                      Recomendado para tu escala ({unitsCount} Uds.)
                    </div>
                  )}

                  <div className="space-y-5">
                    <div className="flex items-center justify-between">
                      <div
                        className={`w-11 h-11 rounded-2xl flex items-center justify-center ${
                          isRecommended
                            ? 'bg-sky-500/20 border border-sky-400/30 text-sky-300'
                            : 'bg-slate-900 border border-slate-800 text-slate-400'
                        }`}
                      >
                        <Icon className="w-5 h-5" />
                      </div>
                      <span
                        className={`text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wider ${
                          isRecommended
                            ? 'bg-sky-500/20 text-sky-300 border border-sky-500/30'
                            : 'bg-slate-800 text-slate-200 border border-slate-700'
                        }`}
                      >
                        {tag}
                      </span>
                    </div>

                    <div>
                      <h3 className="text-xl font-bold text-white font-['Plus_Jakarta_Sans']">
                        {plan.nombre || plan.NOMBRE}
                      </h3>
                      <p className="text-xs text-sky-400 font-medium mt-0.5">
                        {limProp === 1 ? '1 copropiedad' : `Hasta ${limProp} copropiedades`} • Hasta {limUni} unidades
                      </p>
                      <p className="text-xs sm:text-sm text-slate-300 mt-2 leading-relaxed">
                        {plan.descripcion || plan.DESCRIPCION}
                      </p>
                    </div>

                    {/* Price Tag Display */}
                    <div className="p-3.5 rounded-2xl bg-slate-950/70 border border-slate-800/80 space-y-1">
                      {precioMensual === 0 ? (
                        <div>
                          <div className="flex items-baseline gap-1.5">
                            <span className="text-2xl font-extrabold font-mono text-white">$0 COP</span>
                            <span className="text-xs text-slate-400 font-medium">/ 14 días</span>
                          </div>
                          <p className="text-[11px] text-emerald-400 font-medium">
                            Acceso total de prueba sin tarjeta de crédito
                          </p>
                        </div>
                      ) : billingCycle === 'annual' ? (
                        <div>
                          <div className="flex items-baseline gap-1.5">
                            <span className="text-2xl font-extrabold font-mono text-white">
                              {formatCOP(precioEquivMensual)}
                            </span>
                            <span className="text-xs text-slate-400 font-medium">/ mes</span>
                          </div>
                          <p className="text-[11px] text-slate-400">
                            Facturación anual de{' '}
                            <span className="text-emerald-400 font-bold font-mono">
                              {formatCOP(precioAnual)}
                            </span>{' '}
                            (-20% aplicado)
                          </p>
                        </div>
                      ) : (
                        <div>
                          <div className="flex items-baseline gap-1.5">
                            <span className="text-2xl font-extrabold font-mono text-white">
                              {formatCOP(precioMensual)}
                            </span>
                            <span className="text-xs text-slate-400 font-medium">/ mes</span>
                          </div>
                          <p className="text-[11px] text-slate-400">
                            Suscripción mensual recurrente vía Wompi
                          </p>
                        </div>
                      )}
                    </div>

                    {/* Model C Capacity Strip */}
                    <div className="grid grid-cols-2 gap-2 text-[11px] text-slate-300 bg-slate-900/50 p-2.5 rounded-xl border border-slate-800">
                      <div className="flex items-center gap-1.5">
                        <Users className="w-3.5 h-3.5 text-sky-400 shrink-0" />
                        <span>Hasta {limUni} unidades</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <HardDrive className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                        <span>{limStorage} GB Cuota Storage</span>
                      </div>
                    </div>

                    {/* Features List */}
                    <div className="pt-2 border-t border-slate-800/80 space-y-2.5 text-xs text-slate-200">
                      {featureList.map((feat, i) => (
                        <div key={i} className="flex items-start gap-2.5">
                          <Check className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                          <span>{feat}</span>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="pt-8 mt-6 border-t border-slate-800/60">
                    <Link
                      to={`/registro-organizacion?plan=${codigo}&cycle=${billingCycle === 'annual' ? 'ANUAL' : 'MENSUAL'}`}
                      className={`w-full py-3.5 px-4 rounded-xl text-xs sm:text-sm font-bold text-center transition-all flex items-center justify-center gap-2 min-h-[48px] ${
                        isRecommended
                          ? 'bg-gradient-to-r from-cyan-400 to-sky-500 hover:from-cyan-300 hover:to-sky-400 text-slate-950 shadow-lg shadow-sky-950/50'
                          : 'bg-slate-800 hover:bg-slate-700 text-white border border-slate-700'
                      }`}
                    >
                      <span>{ctaText}</span>
                      <ArrowRight className="w-4 h-4" />
                    </Link>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {/* ========================================================================= */}
        {/* 2. Interactive Unit Scale Estimator Widget                                */}
        {/* ========================================================================= */}
        {recommendedPlan && (
          <div className="max-w-4xl mx-auto mb-16 p-6 sm:p-8 rounded-3xl bg-slate-900/80 border border-slate-800/90 shadow-2xl backdrop-blur-xl">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-slate-800 pb-5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-400">
                  <Sliders className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white font-['Plus_Jakarta_Sans']">
                    Estimador Interactivo de Escala por Unidades
                  </h3>
                  <p className="text-xs text-slate-400">
                    Calcula el plan recomendado según la cantidad de unidades residenciales
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-400 font-mono">Plan Recomendado:</span>
                <span className="px-3 py-1 rounded-full text-xs font-bold bg-sky-500/15 text-sky-300 border border-sky-500/30">
                  {recommendedPlan.nombre || recommendedPlan.NOMBRE || recommendedPlanCode}
                </span>
              </div>
            </div>

            <div className="pt-6 space-y-6">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <span className="text-sm font-semibold text-slate-200">
                  Número de Unidades Habitacionales (Apartamentos / Casas):
                </span>
                <span className="text-2xl font-extrabold font-mono text-sky-400">
                  {unitsCount} <span className="text-xs font-sans text-slate-400 font-normal">unidades</span>
                </span>
              </div>

              {/* Slider */}
              <div className="space-y-2">
                <input
                  type="range"
                  min="5"
                  max="500"
                  step="5"
                  value={unitsCount}
                  onChange={(e) => setUnitsCount(Number(e.target.value))}
                  className="w-full h-2.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-sky-400 focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400"
                  aria-label="Ajustar número de unidades habitacionales"
                />
                <div className="flex justify-between text-[11px] text-slate-500 font-mono">
                  {sortedPlans.map((p) => {
                    const lim = p.limiteUnidades || p.LIMITE_UNIDADES;
                    const nom = p.nombre || p.NOMBRE;
                    return (
                      <span key={p.codigo || p.idPlan}>
                        {lim >= 9000 ? '500+' : lim} ({nom})
                      </span>
                    );
                  })}
                </div>
              </div>

              {/* Dynamic Calculated Strip */}
              {(() => {
                const recPrice = Number(recommendedPlan.precioMensual || recommendedPlan.PRECIO_MENSUAL || 0);
                const recAnnual = Number(
                  recommendedPlan.precioAnual ||
                    recommendedPlan.PRECIO_ANUAL ||
                    Math.round(recPrice * 12 * 0.8)
                );
                const displayInvestment =
                  recPrice === 0
                    ? 0
                    : billingCycle === 'annual'
                    ? Math.round(recAnnual / 12)
                    : recPrice;

                const perUnitAvg =
                  unitsCount > 0 && displayInvestment > 0
                    ? Math.round(displayInvestment / unitsCount)
                    : 0;

                return (
                  <div className="p-4 rounded-2xl bg-[#070D18] border border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div className="space-y-1">
                      <span className="text-xs font-bold text-slate-300 flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5 text-sky-400" />
                        Inversión para {unitsCount} Unidades ({recommendedPlan.nombre || recommendedPlan.NOMBRE}):
                      </span>
                      <p className="text-[11px] text-slate-400">
                        {recPrice === 0 ? (
                          'Plan de evaluación gratuito para hasta 10 unidades.'
                        ) : (
                          <>
                            Tarifa plana por copropiedad:{' '}
                            <span className="text-slate-200 font-mono">
                              {formatCOP(displayInvestment)} COP / mes
                            </span>
                            {perUnitAvg > 0 && (
                              <> (~{formatCOP(perUnitAvg)} COP / unidad / mes promedio)</>
                            )}
                            {billingCycle === 'annual' && ' con 20% de descuento anual incluido.'}
                          </>
                        )}
                      </p>
                    </div>

                    <div className="text-left sm:text-right">
                      <div className="text-2xl sm:text-3xl font-extrabold font-mono text-emerald-400">
                        {formatCOP(displayInvestment)}
                      </div>
                      <span className="text-[10px] font-mono text-slate-500">
                        COP / Mes ({billingCycle === 'annual' ? 'Facturado Anualmente' : 'Facturado Mensualmente'})
                      </span>
                    </div>
                  </div>
                );
              })()}
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* Capability Matrix Toggle Button                                           */}
        {/* ========================================================================= */}
        <div className="text-center mb-8">
          <button
            type="button"
            onClick={() => setShowMatrix((m) => !m)}
            className="inline-flex items-center gap-2 px-6 py-3 rounded-2xl bg-slate-900/90 border border-slate-700 text-slate-200 hover:text-white hover:border-sky-500/40 text-xs sm:text-sm font-semibold transition-all focus:outline-none focus-visible:ring-2 focus-visible:ring-sky-400 min-h-[44px] shadow-lg"
            aria-expanded={showMatrix}
          >
            <span>
              {showMatrix
                ? 'Ocultar matriz comparativa técnica'
                : 'Ver matriz comparativa completa de capacidades'}
            </span>
            <ChevronDown
              className={`w-4 h-4 transition-transform duration-200 ${
                showMatrix ? 'rotate-180' : ''
              }`}
            />
          </button>
        </div>

        {/* ========================================================================= */}
        {/* Expandable Comparison Matrix with Anime.js Reveal                        */}
        {/* ========================================================================= */}
        {showMatrix && (
          <div
            ref={matrixContainerRef}
            className="max-w-5xl mx-auto rounded-3xl bg-slate-900/95 border border-slate-800 overflow-hidden shadow-2xl backdrop-blur-xl"
          >
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="bg-slate-950 border-b border-slate-800 text-slate-300 font-bold uppercase tracking-wider">
                    <th className="p-4 sm:p-5 w-2/5">Capacidad / Módulo</th>
                    <th className="p-4 sm:p-5 text-center w-1/5">Gratuito (FREE)</th>
                    <th className="p-4 sm:p-5 text-center w-1/5 text-sky-400">Profesional (PRO)</th>
                    <th className="p-4 sm:p-5 text-center w-1/5">Empresarial (ENT)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {matrixCategories.map((cat) => (
                    <Fragment key={cat.name}>
                      <tr className="bg-slate-900/90 font-bold text-sky-400">
                        <td colSpan={4} className="p-3 sm:px-5 text-[11px] uppercase tracking-wider">
                          {cat.name}
                        </td>
                      </tr>
                      {cat.items.map((row, i) => (
                        <tr key={i} className="hover:bg-slate-800/30 transition-colors">
                          <td className="p-4 sm:px-5 text-slate-200 font-medium">{row.feature}</td>
                          <td className="p-4 text-center">
                            {typeof row.free === 'boolean' ? (
                              row.free ? (
                                <Check className="w-4 h-4 text-emerald-400 mx-auto" />
                              ) : (
                                <span className="text-slate-600">—</span>
                              )
                            ) : (
                              <span className="text-slate-300">{row.free}</span>
                            )}
                          </td>
                          <td className="p-4 text-center bg-sky-500/[0.04]">
                            {typeof row.pro === 'boolean' ? (
                              row.pro ? (
                                <Check className="w-4 h-4 text-sky-400 mx-auto" />
                              ) : (
                                <span className="text-slate-600">—</span>
                              )
                            ) : (
                              <span className="text-sky-300 font-semibold">{row.pro}</span>
                            )}
                          </td>
                          <td className="p-4 text-center">
                            {typeof row.enterprise === 'boolean' ? (
                              row.enterprise ? (
                                <Check className="w-4 h-4 text-emerald-400 mx-auto" />
                              ) : (
                                <span className="text-slate-600">—</span>
                              )
                            ) : (
                              <span className="text-slate-200 font-semibold">{row.enterprise}</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
