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

/** Valida placa de vehiculo colombiano: carro (ABC123) o moto (ABC12D). */
export function valPlaca(placa, tipoVehiculo) {
  if (!placa) return false;
  const p = placa.toUpperCase().replace(/[^A-Z0-9]/g, '');
  if (tipoVehiculo === 'MOTO') {
    return /^[A-Z]{3}\d{2}[A-Z]$/.test(p);
  }
  return /^[A-Z]{3}\d{3}$/.test(p);
}

/** Valida telefono colombiano: exactamente 10 digitos. */
export function valTelefono(telefono) {
  if (!telefono) return false;
  return /^\d{10}$/.test(telefono.replace(/\D/g, ''));
}

/** Valida username: letras, numeros, _ y . — entre 3 y 50 caracteres. */
export function valUsername(username) {
  if (!username) return false;
  return /^[a-zA-Z0-9_.]{3,50}$/.test(username);
}

/** Valida password: entre 6 y 100 caracteres. */
export function valPassword(password) {
  if (!password) return false;
  return password.length >= 6 && password.length <= 100;
}
