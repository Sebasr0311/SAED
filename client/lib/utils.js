export function formatDate(value) {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
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

export function classNames(...parts) {
  return parts.filter(Boolean).join(' ');
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
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleDateString('es-CO', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}
