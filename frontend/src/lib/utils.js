export function parseDateSafe(value) {
  if (!value) return null;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  const str = String(value).replace(/\[.*?\]$/, '').trim();
  const d = new Date(str);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function formatDate(value) {
  if (!value) return '';
  const d = parseDateSafe(value);
  if (!d) return typeof value === 'string' ? value.replace(/\[.*?\]$/, '') : '';
  return d.toLocaleDateString('es-CO', { year: 'numeric', month: '2-digit', day: '2-digit' });
}

export function formatCurrency(value) {
  if (value == null) return '$0';
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(value);
}

/** Formatea número o identificador de apartamento evitando duplicación 'Apto Apto'. */
export function formatApto(val, defaultVal = '-') {
  if (!val) return defaultVal;
  const str = String(val).trim();
  if (str.toLowerCase().startsWith('apto')) return str;
  return `Apto ${str}`;
}

export function classNames(...parts) {
  return parts.filter(Boolean).join(' ');
}

import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** shadcn/ui: merge de clases Tailwind sin conflictos. */
export function cn(...inputs) {
  return twMerge(clsx(inputs));
}

/**
 * Devuelve un src listo para <img>: si el valor ya trae el prefijo data:image/...
 * se usa tal cual; si no, se envuelve en data:image/jpeg;base64,.
 * Defensivo: el backend a veces devuelve la foto ya prefijada y otras solo el base64.
 */
export function imageSrc(value) {
  if (value == null || value === '') return '';
  if (String(value).startsWith('data:image/')) return value;
  return `data:image/jpeg;base64,${value}`;
}

/** Fecha de hoy en formato YYYY-MM-DD, timezone-safe para America/Bogota. */
export function todayStr() {
  const now = new Date();
  const bogota = new Date(now.toLocaleString('en-US', { timeZone: 'America/Bogota' }));
  const y = bogota.getFullYear();
  const m = String(bogota.getMonth() + 1).padStart(2, '0');
  const d = String(bogota.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function dateToStr(date) {
  if (!date) return '';
  const d = new Date(date);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Formatea un número con separadores de miles (estilo es-CO: 1.500.000). */
export function formatMiles(value) {
  if (value == null || value === '') return '';
  const num = String(value).replace(/\D/g, '');
  if (!num) return '';
  return Number(num).toLocaleString('es-CO');
}

export function parseMiles(value) {
  if (!value) return 0;
  return Number(String(value).replace(/\D/g, '')) || 0;
}

/** Etiqueta de periodo (mes año) — usado por Pagos, Alertas, ResCuotas y Dashboard. */
const MESES_ES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
export function periodoLabel(anio, mes) {
  if (anio == null || mes == null) return '-';
  return `${MESES_ES[mes - 1] || mes} ${anio}`;
}

// Usado en modales de Dashboard Admin (multa, contrato, estado del sistema) —
// NO eliminar sin verificar contra el legacy (FASE 4.3 lo eliminó por error).
export function formatDateTime(value) {
  if (!value) return '';
  const d = parseDateSafe(value);
  if (!d) return typeof value === 'string' ? value.replace(/\[.*?\]$/, '') : '';
  return d.toLocaleString('es-CO', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: true,
  });
}

export function formatTime(value) {
  if (!value) return '';
  const d = parseDateSafe(value);
  if (!d) return '';
  return d.toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', hour12: true });
}

/**
 * Corrige secuencias de codificación defectuosa (mojibake) producidas por
 * doble codificación UTF-8 / Latin-1 (e.g. mal decodificados a nivel de transporte/DB).
 * Es idempotente y completamente segura para strings ya codificados correctamente.
 */
export function sanitizeEncoding(value) {
  if (typeof value !== 'string' || !value) return value;
  if (!/[\u00C3\u00C2\u00E2\u00F0\uFFFD]/.test(value)) return value;

  let str = value;
  try {
    const fixed = decodeURIComponent(escape(str));
    if (fixed && !/[\u00C3\u00C2\u00E2\u00F0]/.test(fixed)) {
      return fixed.replace(/\uFFFD/g, '');
    }
  } catch {
    // Continuar con sustitución directa del mapa
  }

  const MOJIBAKE_MAP = [
    // Minúsculas acentuadas
    [/\u00C3\u00A1/g, 'á'],
    [/\u00C3\u00A9/g, 'é'],
    [/\u00C3\u00AD/g, 'í'],
    [/\u00C3\u00B3/g, 'ó'],
    [/\u00C3\u00BA/g, 'ú'],
    [/\u00C3\u00B1/g, 'ñ'],
    [/\u00C3\u00BC/g, 'ü'],
    // Mayúsculas acentuadas
    [/\u00C3\u0081/g, 'Á'],
    [/\u00C3\u0089/g, 'É'],
    [/\u00C3\u008D/g, 'Í'],
    [/\u00C3\u0093/g, 'Ó'],
    [/\u00C3\u009A/g, 'Ú'],
    [/\u00C3\u0091/g, 'Ñ'],
    [/\u00C3\u009C/g, 'Ü'],
    // Puntuación y símbolos
    [/\u00C2\u00BF/g, '¿'],
    [/\u00C2\u00A1/g, '¡'],
    [/\u00C2\u00B0/g, '°'],
    [/\u00C2\u00B7/g, '·'],
    [/\u00E2\u20AC\u2122/g, "'"],
    [/\u00E2\u20AC\u02DC/g, "'"],
    [/\u00E2\u20AC\u0153/g, '"'],
    [/\u00E2\u20AC\u009D/g, '"'],
    [/\u00E2\u20AC\u201C/g, '–'],
    [/\u00E2\u20AC\u201D/g, '—'],
    [/\u00E2\u20AC\u00A6/g, '…'],
    // Palabras compuestas y terminaciones comunes
    [/C\u00C3\u00A9dula de Ciudadan\u00C3a/g, 'Cédula de Ciudadanía'],
    [/Ciudadan\u00C3a/g, 'Ciudadanía'],
    [/an\u00C3a\b/g, 'anía'],
    [/\u00C3a\b/g, 'ía'],
    [/C\u00C3\u00A9dula/g, 'Cédula'],
    [/Tel\u00C3\u00A9fono/g, 'Teléfono'],
    [/Informaci\u00C3\u00B3n/g, 'Información'],
    [/Administraci\u00C3\u00B3n/g, 'Administración'],
    [/Ni\u00C3\u00B1o/g, 'Niño'],
    [/A\u00C3\u00B1adir/g, 'Añadir'],
    [/C\u00C3\u00B3digo/g, 'Código'],
    [/Descripci\u00C3\u00B3n/g, 'Descripción'],
    [/Direcci\u00C3\u00B3n/g, 'Dirección'],
    [/\u00C2([¿¡°·áéíóúÁÉÍÓÚñÑ])/g, '$1'],
    [/\uFFFD/g, '']
  ];

  for (const [pattern, replacement] of MOJIBAKE_MAP) {
    str = str.replace(pattern, replacement);
  }
  return str;
}

/**
 * Recorre recursivamente objetos o arrays sanitizando todas las cadenas de texto contra mojibake.
 */
export function sanitizeData(data) {
  if (data == null) return data;
  if (typeof data === 'string') return sanitizeEncoding(data);
  if (Array.isArray(data)) return data.map(sanitizeData);
  if (typeof data === 'object' && !(data instanceof Date) && !(data instanceof Blob) && !(data instanceof FormData)) {
    const res = {};
    for (const key of Object.keys(data)) {
      res[key] = sanitizeData(data[key]);
    }
    return res;
  }
  return data;
}

