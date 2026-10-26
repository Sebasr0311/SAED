// Geometry of the illustrative complex model used across the landing "build manual".
// Coordinates are in stud modules; origin is the bottom-left corner of the baseplate.

export const MODEL_W = 30;
export const MODEL_H = 22;

// Matches the units slider on /suscripciones so the value can be handed over.
export const UNITS_MIN = 10;
export const UNITS_MAX = 500;
export const UNITS_STEP = 10;
export const UNITS_DEFAULT = 240;

const UNITS_PER_TOWER_FLOOR = 12;
const TOWER_ZONE_X = 7;
const TOWER_ZONE_W = 15;
const TOWER_W = 3;

export function clampUnits(value) {
  const n = Number.parseInt(value, 10);
  if (Number.isNaN(n)) return UNITS_DEFAULT;
  return Math.min(UNITS_MAX, Math.max(UNITS_MIN, n));
}

// Maps a unit count to a schematic massing: 1-4 towers, 3-12 floors.
export function massingFor(units) {
  const u = clampUnits(units);
  const towers = Math.min(4, Math.max(1, Math.ceil(u / 150)));
  const floors = Math.min(12, Math.max(3, Math.ceil(u / (towers * UNITS_PER_TOWER_FLOOR))));
  return { towers, floors };
}

export function towerPieces(units) {
  const { towers, floors } = massingFor(units);
  const total = towers * TOWER_W + (towers - 1);
  const startX = TOWER_ZONE_X + (TOWER_ZONE_W - total) / 2;
  return Array.from({ length: towers }, (_, i) => ({
    id: `torre-${i + 1}`,
    step: 1,
    kind: 'tower',
    x: startX + i * (TOWER_W + 1),
    y: 1,
    w: TOWER_W,
    h: floors,
    floors,
    dy: 5,
  }));
}

// Pieces seated by steps 2-6. `dy` is how far above its seat a piece starts its drop.
export const FIXED_PIECES = [
  { id: 'base', step: 1, kind: 'plate', x: 0, y: 0, w: 30, h: 1, dy: 3 },
  { id: 'porteria', step: 2, kind: 'gate', x: 1, y: 1, w: 5, h: 3, dy: 5, label: 'Portería QR', side: 'right' },
  { id: 'paquetes', step: 3, kind: 'locker', x: 2, y: 3.4, w: 4, h: 2, dy: 5, label: 'Paquetes', side: 'right' },
  { id: 'parqueadero', step: 3, kind: 'parking', x: 23, y: 1, w: 6, h: 2, dy: 5, label: 'Parqueadero', side: 'left' },
  { id: 'cartera', step: 4, kind: 'office', x: 23, y: 3, w: 6, h: 3, dy: 5, label: 'Cartera y Wompi', side: 'left' },
  { id: 'salon', step: 5, kind: 'hall', x: 23.5, y: 5.6, w: 4, h: 3, dy: 4, label: 'Asambleas y reservas', side: 'left' },
  { id: 'celular', step: 6, kind: 'phone', x: 24.5, y: 8.4, w: 5, h: 8, dy: 3, label: 'Residente', side: 'left' },
];

export function modelPieces(units) {
  const [base, ...rest] = FIXED_PIECES;
  return [base, ...towerPieces(units), ...rest];
}

// 'hidden' (not built yet), 'new' (this step's piece), 'ghost' (earlier steps), 'built' (finished set).
export function pieceState(pieceStep, activeStep) {
  if (activeStep > 6) return 'built';
  if (pieceStep < activeStep) return 'ghost';
  if (pieceStep === activeStep) return 'new';
  return 'hidden';
}

export function describeModel(units) {
  const { towers, floors } = massingFor(units);
  const torres = towers === 1 ? '1 torre' : `${towers} torres`;
  return `${torres} de ${floors} pisos`;
}
