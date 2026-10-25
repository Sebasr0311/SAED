import { Link } from 'react-router-dom';
import { ArrowDown, ArrowRight, Minus, Plus } from 'lucide-react';
import ComplexModel, { StepMark } from './ComplexModel.jsx';
import { QrInviteCallout } from './ManualCallouts.jsx';
import {
  UNITS_MAX,
  UNITS_MIN,
  UNITS_STEP,
  clampUnits,
  describeModel,
  towerPieces,
} from './manualModel.js';

function UnitsControl({ units, onUnitsChange }) {
  const fill = ((units - UNITS_MIN) / (UNITS_MAX - UNITS_MIN)) * 100;
  const nudge = (delta) => onUnitsChange(clampUnits(units + delta));

  return (
    <div className="m-units">
      <div className="m-units-head">
        <label htmlFor="m-units-range" className="m-units-label">
          ¿Cuántas unidades tiene tu conjunto?
        </label>
        <output htmlFor="m-units-range" className="m-units-value">
          <span className="m-numeral-sm">{units}</span>
          <span className="m-units-unit">unidades</span>
        </output>
      </div>
      <div className="m-units-row">
        <button
          type="button"
          className="m-icon-btn"
          onClick={() => nudge(-UNITS_STEP)}
          disabled={units <= UNITS_MIN}
          aria-label={`Quitar ${UNITS_STEP} unidades`}
        >
          <Minus aria-hidden="true" />
        </button>
        <input
          id="m-units-range"
          type="range"
          className="m-range"
          min={UNITS_MIN}
          max={UNITS_MAX}
          step={UNITS_STEP}
          value={units}
          onChange={(e) => onUnitsChange(clampUnits(e.target.value))}
          aria-valuetext={`${units} unidades`}
          style={{ '--fill': `${fill}%` }}
        />
        <button
          type="button"
          className="m-icon-btn"
          onClick={() => nudge(UNITS_STEP)}
          disabled={units >= UNITS_MAX}
          aria-label={`Sumar ${UNITS_STEP} unidades`}
        >
          <Plus aria-hidden="true" />
        </button>
      </div>
      <p className="m-units-note">El modelo se arma con {describeModel(units)}.</p>
    </div>
  );
}

export default function LandingHero({ units, onUnitsChange }) {
  // Crop the hero view to the gatehouse and towers so the build fills the page.
  const towers = towerPieces(units);
  const last = towers[towers.length - 1];
  const cols = Math.max(19, Math.ceil(last.x + last.w + 1));
  return (
    <section id="inicio" className="m-hero" aria-labelledby="m-hero-title">
      <div className="m-container">
        <div className="m-spread">
          <div className="m-page m-page--left">
            <h1 id="m-hero-title" className="m-display">
              Tu conjunto, armado pieza por pieza.
            </h1>
            <p className="m-lede">
              Administración, portería, residentes y cartera sobre un mismo registro. Cada módulo
              de SAED encaja en la misma base, pensada para propiedad horizontal en Colombia.
            </p>

            <UnitsControl units={units} onUnitsChange={onUnitsChange} />

            <div className="m-actions">
              <Link to={`/suscripciones?unidades=${units}`} className="m-btn m-btn--primary m-btn--lg">
                <span>Ver planes para {units} unidades</span>
                <ArrowRight aria-hidden="true" />
              </Link>
              <a href="#pasos" className="m-btn m-btn--glass m-btn--lg">
                <span>Ver cómo se arma</span>
                <ArrowDown aria-hidden="true" />
              </a>
            </div>
          </div>

          <div className="m-page m-page--right m-studs">
            <ComplexModel units={units} activeStep={2} cols={cols} rows={16} intro className="m-hero-model">
              <StepMark step={2} className="m-hero-mark" />
              <QrInviteCallout className="m-hero-callout m-glass" />
            </ComplexModel>
            <p className="m-model-caption">
              Paso 2: la portería encaja sobre la base de unidades y residentes.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
