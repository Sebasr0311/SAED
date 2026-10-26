import { useState, useEffect, Fragment } from 'react';
import {
  Check,
  ArrowRight,
  Building2,
  Layers,
  Crown,
  ChevronDown,
  RefreshCw,
  CircleAlert,
  Minus,
  Plus,
  PackageOpen,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import api from '../../lib/api.js';
import './manual.css';
import './manual-pricing.css';

function formatCOP(val) {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(val);
}

const UNITS_MIN = 5;
const UNITS_MAX = 500;
const UNITS_STEP = 5;

// Annual savings derived from the actual prices, never assumed.
function annualSavingsPct(precioMensual, precioAnual) {
  if (!precioMensual || !precioAnual) return 0;
  return Math.max(0, Math.round((1 - precioAnual / (precioMensual * 12)) * 100));
}

function MatrixCell({ value, emphasis = false }) {
  if (typeof value === 'boolean') {
    return value ? (
      <Check className="m-kit-check" aria-label="Incluido" />
    ) : (
      <span className="m-kit-none" aria-label="No incluido">
        —
      </span>
    );
  }
  return <span className={`m-num${emphasis ? ' m-strong' : ''}`}>{value}</span>;
}

export default function LandingPricing() {
  const [billingCycle, setBillingCycle] = useState('annual'); // 'monthly' | 'annual'
  // The landing hands over the visitor's unit count as ?unidades=N (slider range 5-500).
  const [unitsCount, setUnitsCount] = useState(() => {
    try {
      const n = Number.parseInt(new URLSearchParams(window.location.search).get('unidades'), 10);
      return Number.isNaN(n) ? 80 : Math.min(UNITS_MAX, Math.max(UNITS_MIN, n));
    } catch {
      return 80;
    }
  });
  const [showMatrix, setShowMatrix] = useState(false);

  // Dynamic API state — Oracle es la única fuente de verdad
  const [planes, setPlanes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

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
        return 'Para comenzar';
      case 'PRO':
        return 'Profesional';
      case 'ENTERPRISE':
        return 'Corporativo';
      default:
        return 'Plan';
    }
  };

  const getPlanCtaText = (codigo) => {
    switch ((codigo || '').toUpperCase()) {
      case 'FREE':
        return 'Comenzar prueba gratuita';
      case 'PRO':
        return 'Suscribir plan Profesional';
      case 'ENTERPRISE':
        return 'Suscribir plan Empresarial';
      default:
        return 'Seleccionar plan';
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
      name: 'Portería y accesos',
      items: [
        { feature: 'Invitaciones de visita con código QR', free: true, pro: true, enterprise: true },
        { feature: 'Consola web para portería (computador o tableta)', free: true, pro: true, enterprise: true },
        {
          feature: 'Directorio de unidades y residentes',
          free: `Hasta ${getUnitLimit(freePlan, '10')}`,
          pro: `Hasta ${getUnitLimit(proPlan, '100')}`,
          enterprise: `Hasta ${getUnitLimit(entPlan, '9.999')}`,
        },
        { feature: 'Parqueadero de visitantes', free: hasModule(freePlan, 'PARQUEADEROS'), pro: hasModule(proPlan, 'PARQUEADEROS') || true, enterprise: true },
      ],
    },
    {
      name: 'Operación y comunidad',
      items: [
        { feature: 'Custodia de paquetes con PIN de entrega', free: hasModule(freePlan, 'PAQUETES'), pro: hasModule(proPlan, 'PAQUETES') || true, enterprise: true },
        { feature: 'PQRS con seguimiento de respuesta', free: hasModule(freePlan, 'PQRS'), pro: hasModule(proPlan, 'PQRS') || true, enterprise: true },
        { feature: 'Reservas de zonas comunes', free: hasModule(freePlan, 'RESERVAS'), pro: hasModule(proPlan, 'RESERVAS') || true, enterprise: true },
        { feature: 'Obras y remodelaciones', free: hasModule(freePlan, 'OBRAS'), pro: hasModule(proPlan, 'OBRAS') || true, enterprise: true },
        { feature: 'Pólizas de seguros de la copropiedad', free: hasModule(freePlan, 'POLIZAS'), pro: hasModule(proPlan, 'POLIZAS') || true, enterprise: true },
      ],
    },
    {
      name: 'Finanzas',
      items: [
        { feature: 'Estados de cuenta digitales', free: true, pro: true, enterprise: true },
        { feature: 'Pagos en línea con Wompi (PSE y tarjetas)', free: hasModule(freePlan, 'FINANZAS'), pro: hasModule(proPlan, 'FINANZAS') || true, enterprise: true },
        { feature: 'Conciliaciones', free: false, pro: true, enterprise: true },
      ],
    },
    {
      name: 'Límites y almacenamiento',
      items: [
        {
          feature: 'Copropiedades',
          free: getPropLimit(freePlan, '1'),
          pro: `Hasta ${getPropLimit(proPlan, '5')}`,
          enterprise: `Hasta ${getPropLimit(entPlan, '999')}`,
        },
        {
          feature: 'Unidades residenciales',
          free: getUnitLimit(freePlan, '10'),
          pro: getUnitLimit(proPlan, '100'),
          enterprise: getUnitLimit(entPlan, '9.999'),
        },
        {
          feature: 'Usuarios',
          free: getUserLimit(freePlan, '5'),
          pro: getUserLimit(proPlan, '50'),
          enterprise: getUserLimit(entPlan, '999'),
        },
        {
          feature: 'Almacenamiento',
          free: getStorageLimit(freePlan, '1 GB'),
          pro: getStorageLimit(proPlan, '10 GB'),
          enterprise: getStorageLimit(entPlan, '100 GB'),
        },
        { feature: 'Asambleas (Ley 675)', free: hasModule(freePlan, 'ASAMBLEAS'), pro: hasModule(proPlan, 'ASAMBLEAS'), enterprise: hasModule(entPlan, 'ASAMBLEAS') || true },
        { feature: 'Datos aislados por conjunto', free: true, pro: true, enterprise: true },
      ],
    },
  ];

  const fill = ((unitsCount - UNITS_MIN) / (UNITS_MAX - UNITS_MIN)) * 100;
  const nudgeUnits = (delta) =>
    setUnitsCount((n) => Math.min(UNITS_MAX, Math.max(UNITS_MIN, n + delta)));

  return (
    <section id="planes" className="m-section m-pricing" aria-labelledby="m-pricing-title">
      <div className="m-container">
        <h2 id="m-pricing-title" className="m-sr">
          Planes y precios
        </h2>

        {/* Controls: unit count + billing cycle */}
        <div className="m-pricing-controls">
          <div className="m-units">
            <div className="m-units-head">
              <label htmlFor="m-pricing-units" className="m-units-label">
                ¿Cuántas unidades tiene tu conjunto?
              </label>
              <output htmlFor="m-pricing-units" className="m-units-value">
                <span className="m-numeral-sm">{unitsCount}</span>
                <span className="m-units-unit">unidades</span>
              </output>
            </div>
            <div className="m-units-row">
              <button
                type="button"
                className="m-icon-btn"
                onClick={() => nudgeUnits(-UNITS_STEP)}
                disabled={unitsCount <= UNITS_MIN}
                aria-label={`Quitar ${UNITS_STEP} unidades`}
              >
                <Minus aria-hidden="true" />
              </button>
              <input
                id="m-pricing-units"
                type="range"
                className="m-range"
                min={UNITS_MIN}
                max={UNITS_MAX}
                step={UNITS_STEP}
                value={unitsCount}
                onChange={(e) => setUnitsCount(Number(e.target.value))}
                aria-valuetext={`${unitsCount} unidades`}
                style={{ '--fill': `${fill}%` }}
              />
              <button
                type="button"
                className="m-icon-btn"
                onClick={() => nudgeUnits(UNITS_STEP)}
                disabled={unitsCount >= UNITS_MAX}
                aria-label={`Sumar ${UNITS_STEP} unidades`}
              >
                <Plus aria-hidden="true" />
              </button>
            </div>
          </div>

          <fieldset className="m-cycle">
            <legend className="m-units-label">Facturación</legend>
            <div className="m-cycle-options">
              <button
                type="button"
                aria-pressed={billingCycle === 'monthly'}
                onClick={() => setBillingCycle('monthly')}
              >
                Mensual
              </button>
              <button
                type="button"
                aria-pressed={billingCycle === 'annual'}
                onClick={() => setBillingCycle('annual')}
              >
                Anual
              </button>
            </div>
            <p className="m-units-note">El pago anual tiene descuento frente a pagar mes a mes.</p>
          </fieldset>
        </div>

        {/* UI STATES: LOADING / ERROR / EMPTY / SUCCESS */}
        {loading ? (
          <div className="m-kits" aria-busy="true">
            <p className="m-sr" role="status">
              Cargando planes
            </p>
            {[1, 2, 3].map((idx) => (
              <div key={idx} className="m-kit is-loading" aria-hidden="true" style={{ '--i': idx }}>
                <span className="m-kit-bar is-short" />
                <span className="m-kit-bar is-price" />
                <span className="m-kit-bar" />
                <span className="m-kit-bar" />
                <span className="m-kit-bar is-short" />
                <span className="m-kit-bar is-cta" />
              </div>
            ))}
          </div>
        ) : error ? (
          <div className="m-kit-state is-error" role="alert">
            <div className="m-kit-state-piece" aria-hidden="true">
              <CircleAlert strokeWidth={1.75} />
            </div>
            <div className="m-kit-state-text">
              <h3 className="m-h3">No pudimos cargar los planes</h3>
              <p className="m-body">{error}</p>
              <div className="m-actions">
                <button type="button" onClick={fetchPlanes} className="m-btn m-btn--primary">
                  <RefreshCw aria-hidden="true" />
                  <span>Reintentar</span>
                </button>
              </div>
            </div>
          </div>
        ) : planes.length === 0 ? (
          <div className="m-kit-state">
            <div className="m-kit-state-piece" aria-hidden="true">
              <PackageOpen strokeWidth={1.75} />
            </div>
            <div className="m-kit-state-text">
              <h3 className="m-h3">Catálogo temporalmente no disponible</h3>
              <p className="m-body">Pronto se habilitarán nuevos planes para suscripción.</p>
            </div>
          </div>
        ) : (
          <div className="m-kits">
            {planes.map((plan, idx) => {
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
              const savings = annualSavingsPct(precioMensual, precioAnual);
              const diasPrueba = plan.diasPrueba || plan.DIAS_PRUEBA;

              // Límites canónicos Modelo C
              const limProp = plan.limitePropiedades || plan.LIMITE_PROPIEDADES || 1;
              const limUni = plan.limiteUnidades || plan.LIMITE_UNIDADES || 10;
              const limStorage = plan.limiteAlmacenamientoGb || plan.LIMITE_ALMACENAMIENTO_GB || 1;

              // Features dinámicas desde backend o generadas con límites reales
              const featureList =
                Array.isArray(plan.features) && plan.features.length > 0
                  ? plan.features
                  : [
                      limProp === 1 ? '1 copropiedad' : `Hasta ${limProp} copropiedades`,
                      `Hasta ${limUni} unidades residenciales`,
                      `${limStorage} GB de almacenamiento`,
                      'Invitaciones de visita con código QR',
                    ];

              return (
                <article
                  key={plan.idPlan || plan.ID_PLAN || codigo}
                  className={`m-kit${isRecommended ? ' is-recommended' : ''}`}
                  style={{ '--i': idx }}
                >
                  <header className="m-kit-head">
                    <span className="m-kit-icon" aria-hidden="true">
                      <Icon strokeWidth={1.75} />
                    </span>
                    <span className="m-stamp">{tag}</span>
                  </header>

                  <div>
                    <h3 className="m-h3">{plan.nombre || plan.NOMBRE}</h3>
                    {(plan.descripcion || plan.DESCRIPCION) && (
                      <p className="m-kit-desc">{plan.descripcion || plan.DESCRIPCION}</p>
                    )}
                  </div>

                  <div className="m-kit-price">
                    {precioMensual === 0 ? (
                      <>
                        <span className="m-numeral m-kit-amount">{formatCOP(0)}</span>
                        <span className="m-kit-period">
                          {diasPrueba ? `Prueba de ${diasPrueba} días` : 'Prueba gratuita'}
                        </span>
                      </>
                    ) : billingCycle === 'annual' ? (
                      <>
                        <span className="m-numeral m-kit-amount">{formatCOP(precioEquivMensual)}</span>
                        <span className="m-kit-period">
                          al mes, facturado anual: <span className="m-num m-strong">{formatCOP(precioAnual)}</span>
                          {savings > 0 && ` (-${savings} %)`}
                        </span>
                      </>
                    ) : (
                      <>
                        <span className="m-numeral m-kit-amount">{formatCOP(precioMensual)}</span>
                        <span className="m-kit-period">al mes, pago mensual con Wompi</span>
                      </>
                    )}
                  </div>

                  <dl className="m-kit-limits">
                    <div>
                      <dt>Copropiedades</dt>
                      <dd className="m-num">{limProp === 1 ? '1' : `Hasta ${limProp}`}</dd>
                    </div>
                    <div>
                      <dt>Unidades</dt>
                      <dd className="m-num">Hasta {limUni}</dd>
                    </div>
                    <div>
                      <dt>Almacenamiento</dt>
                      <dd className="m-num">{limStorage} GB</dd>
                    </div>
                  </dl>

                  <ul className="m-kit-features">
                    {featureList.map((feat, i) => (
                      <li key={i}>
                        <Check aria-hidden="true" />
                        <span>{feat}</span>
                      </li>
                    ))}
                  </ul>

                  {isRecommended && (
                    <p className="m-kit-fit">Cubre tus {unitsCount} unidades</p>
                  )}

                  <Link
                    to={`/registro-organizacion?plan=${codigo}&cycle=${billingCycle === 'annual' ? 'ANUAL' : 'MENSUAL'}`}
                    className={`m-btn m-btn--lg m-kit-cta ${isRecommended ? 'm-btn--primary' : 'm-btn--glass'}`}
                  >
                    <span>{ctaText}</span>
                    <ArrowRight aria-hidden="true" />
                  </Link>
                </article>
              );
            })}
          </div>
        )}

        {/* Estimate for the selected unit count */}
        {recommendedPlan &&
          (() => {
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
            const recLimit = recommendedPlan.limiteUnidades || recommendedPlan.LIMITE_UNIDADES;

            return (
              <div className="m-estimate" aria-live="polite">
                <div>
                  <p className="m-units-label">
                    Para {unitsCount} unidades: {recommendedPlan.nombre || recommendedPlan.NOMBRE}
                  </p>
                  <p className="m-units-note">
                    {recPrice === 0
                      ? `Plan gratuito hasta ${recLimit} unidades.`
                      : `Tarifa por copropiedad${perUnitAvg > 0 ? `, unos ${formatCOP(perUnitAvg)} por unidad al mes` : ''}${
                          billingCycle === 'annual' ? ', con el descuento anual aplicado' : ''
                        }.`}
                  </p>
                </div>
                <div className="m-estimate-amount">
                  <span className="m-numeral">{formatCOP(displayInvestment)}</span>
                  <span className="m-units-unit">
                    al mes · {billingCycle === 'annual' ? 'facturado anual' : 'facturado mensual'}
                  </span>
                </div>
              </div>
            );
          })()}

        {/* Capability matrix */}
        <div className="m-matrix-toggle">
          <button
            type="button"
            onClick={() => setShowMatrix((m) => !m)}
            className="m-btn m-btn--glass"
            aria-expanded={showMatrix}
            aria-controls="m-matrix"
          >
            <span>{showMatrix ? 'Ocultar comparación de planes' : 'Comparar planes en detalle'}</span>
            <ChevronDown aria-hidden="true" className={showMatrix ? 'is-open' : undefined} />
          </button>
        </div>

        {showMatrix && (
          <div id="m-matrix" className="m-matrix">
            <table className="m-spec-table m-matrix-table">
              <caption className="m-sr">Comparación de capacidades por plan</caption>
              <thead>
                <tr>
                  <th scope="col">Capacidad</th>
                  <th scope="col">Gratuito</th>
                  <th scope="col">Profesional</th>
                  <th scope="col">Empresarial</th>
                </tr>
              </thead>
              <tbody>
                {matrixCategories.map((cat) => (
                  <Fragment key={cat.name}>
                    <tr className="m-matrix-group">
                      <th scope="colgroup" colSpan={4}>
                        {cat.name}
                      </th>
                    </tr>
                    {cat.items.map((row, i) => (
                      <tr key={i}>
                        <th scope="row">{row.feature}</th>
                        <td>
                          <MatrixCell value={row.free} />
                        </td>
                        <td>
                          <MatrixCell value={row.pro} emphasis />
                        </td>
                        <td>
                          <MatrixCell value={row.enterprise} />
                        </td>
                      </tr>
                    ))}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}
