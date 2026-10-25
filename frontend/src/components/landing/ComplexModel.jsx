import { CarFront, Landmark, QrCode, Wallet } from 'lucide-react';
import { MODEL_H, MODEL_W, describeModel, modelPieces, pieceState } from './manualModel.js';

const pct = (value, total) => `${(value / total) * 100}%`;

function PieceBody({ piece }) {
  switch (piece.kind) {
    case 'tower':
      return (
        <span
          className="m-windows"
          style={{ gridTemplateRows: `repeat(${piece.floors}, minmax(0, 1fr))` }}
        >
          {Array.from({ length: piece.floors * 2 }, (_, i) => (
            <span key={i} className="m-window" />
          ))}
        </span>
      );
    case 'gate':
      return (
        <>
          <span className="m-ico">
            <QrCode strokeWidth={1.75} />
          </span>
          <span className="m-gate-arm" />
        </>
      );
    case 'locker':
      return (
        <span className="m-lockers">
          {Array.from({ length: 6 }, (_, i) => (
            <span key={i} className={i === 4 ? 'is-full' : undefined} />
          ))}
        </span>
      );
    case 'parking':
      return (
        <span className="m-ico">
          <CarFront strokeWidth={1.75} />
        </span>
      );
    case 'office':
      return (
        <span className="m-ico">
          <Wallet strokeWidth={1.75} />
        </span>
      );
    case 'hall':
      return (
        <span className="m-ico">
          <Landmark strokeWidth={1.75} />
        </span>
      );
    case 'phone':
      return (
        <span className="m-phone-screen">
          <span className="m-phone-line is-strong" />
          <span className="m-phone-line" />
          <span className="m-phone-block" />
          <span className="m-phone-line" />
          <span className="m-phone-line is-short" />
        </span>
      );
    default:
      return null;
  }
}

function GuideArrow({ piece, rows }) {
  const cx = piece.x + piece.w / 2;
  const top = piece.y + piece.h;
  const startY = rows - (top + piece.dy) + 0.2;
  const endY = rows - top - 0.4;
  return (
    <g className="m-guide">
      <path d={`M ${cx} ${startY} L ${cx} ${endY}`} className="m-guide-line" />
      <path
        d={`M ${cx - 0.45} ${endY - 0.5} L ${cx} ${endY} L ${cx + 0.45} ${endY - 0.5}`}
        className="m-guide-head"
      />
    </g>
  );
}

/** Giant step numeral paired with "de N" and a tick track so it reads as a step. */
export function StepMark({ step, total = 6, className = '' }) {
  return (
    <span className={`m-stepmark ${className}`} aria-hidden="true">
      <span className="m-numeral m-stepmark-num">{step}</span>
      <span className="m-stepmark-of">de {total}</span>
      <span className="m-stepmark-ticks">
        {Array.from({ length: total }, (_, i) => (
          <span key={i} data-on={i < step ? 'true' : undefined} />
        ))}
      </span>
    </span>
  );
}

/**
 * Illustrative complex model. `activeStep` decides which pieces are ghosted,
 * new (Índigo, dropping onto the base), not yet built, or built (step 7).
 * `cols`/`rows` crop the view; `children` render as overlays inside the model.
 */
export default function ComplexModel({
  units,
  activeStep,
  numeral = null,
  numeralSide = 'left',
  intro = false,
  rows = MODEL_H,
  cols = MODEL_W,
  settle,
  showLabels = true,
  className = '',
  children = null,
}) {
  const pieces = modelPieces(units);
  const newPieces = pieces.filter((p) => pieceState(p.step, activeStep) === 'new' && p.kind !== 'plate');

  return (
    <div className={`m-model-wrap ${className}`}>
      <div
        className="m-model"
        data-intro={intro ? 'true' : undefined}
        data-crop={cols < MODEL_W ? 'true' : undefined}
        data-settle={settle}
        style={{ aspectRatio: `${cols} / ${rows}`, '--cols': cols }}
      >
        <span className="m-sr">{`Modelo ilustrativo del conjunto: ${describeModel(units)}.`}</span>
        {numeral !== null && (
          <span className={`m-numeral m-model-numeral is-${numeralSide}`} aria-hidden="true">
            {numeral}
          </span>
        )}

        <svg
          className="m-guides"
          viewBox={`0 0 ${cols} ${rows}`}
          preserveAspectRatio="none"
          aria-hidden="true"
          focusable="false"
        >
          {newPieces.map((p) => (
            <GuideArrow key={`${p.id}-${activeStep}`} piece={p} rows={rows} />
          ))}
        </svg>

        {pieces.map((piece, i) => {
          const state = pieceState(piece.step, activeStep);
          return (
            <span
              key={piece.id}
              className={`m-piece m-piece--${piece.kind}`}
              data-state={state}
              aria-hidden="true"
              style={{
                left: pct(piece.x, cols),
                bottom: pct(piece.y, rows),
                width: pct(piece.w, cols),
                height: pct(piece.h, rows),
                '--dy': piece.dy ?? 5,
                '--i': i,
              }}
            >
              <PieceBody piece={piece} />
              {showLabels && piece.label && state === 'new' && (
                <span className={`m-tag is-${piece.side || 'right'}`}>{piece.label}</span>
              )}
            </span>
          );
        })}

        {children}
      </div>
    </div>
  );
}
