/**
 * Validadores reutilizables del frontend SAED 2.0.
 *
 * Cumplen con:
 * - Normativa estatutaria colombiana (documentos, telefonía celular, NIT, placas).
 * - Feedback en tiempo real con mensajes explicativos ("Qué pasó + Qué debo hacer").
 * - Convención: cada función devuelve { ok: boolean, mensaje?: string, [extra] }.
 * - No manipulan el DOM: reciben el valor y devuelven el veredicto para que la UI
 *   muestre los estados visuales (borde rojo, labels, aria-invalid, aria-describedby).
 */

/** Regex de documento por código de tipo (Normativa Colombiana). */
export const DOC_PATTERNS = {
  CC: /^\d{6,10}$/,               // Cédula de Ciudadanía: solo números, 6 a 10 dígitos
  TI: /^\d{8,11}$/,               // Tarjeta de Identidad: solo números, 8 a 11 dígitos (NUIP)
  RC: /^\d{10,11}$/,              // Registro Civil: solo números, 10 a 11 dígitos
  CE: /^[A-Za-z0-9]{5,11}$/,      // Cédula de Extranjería: 5 a 11 caracteres alfanuméricos
  PAS: /^[A-Za-z0-9]{6,16}$/i,    // Pasaporte
  PP: /^[A-Za-z0-9]{6,16}$/i,     // Pasaporte abreviado
  PASAPORTE: /^[A-Za-z0-9]{6,16}$/i,
  PPT: /^[A-Za-z0-9]{6,12}$/i,    // Permiso por Protección Temporal
  PEP: /^[A-Za-z0-9]{8,15}$/i,    // Permiso Especial de Permanencia
  NIT: /^\d{8,10}(-\d)?$/,        // NIT: 8 a 10 dígitos, con o sin dígito de verificación
};

export const DOC_MESSAGES = {
  CC: 'Cédula de Ciudadanía: ingresa entre 6 y 10 dígitos numéricos.',
  TI: 'Tarjeta de Identidad: ingresa entre 8 y 11 dígitos numéricos.',
  RC: 'Registro Civil: ingresa entre 10 y 11 dígitos numéricos.',
  CE: 'Cédula de Extranjería: ingresa entre 5 y 11 caracteres alfanuméricos.',
  PAS: 'Pasaporte: ingresa entre 6 y 16 caracteres alfanuméricos.',
  PP: 'Pasaporte: ingresa entre 6 y 16 caracteres alfanuméricos.',
  PASAPORTE: 'Pasaporte: ingresa entre 6 y 16 caracteres alfanuméricos.',
  PPT: 'Permiso por Protección Temporal (PPT): ingresa entre 6 y 12 caracteres.',
  PEP: 'Permiso Especial de Permanencia (PEP): ingresa entre 8 y 15 caracteres.',
  NIT: 'NIT: ingresa entre 8 y 10 dígitos (ej: 900123456 o 900123456-1).',
};

export const DOC_PLACEHOLDERS = {
  CC: 'Ej. 1098765432 (Cédula)',
  TI: 'Ej. 1023456789 (Tarjeta Identidad)',
  RC: 'Ej. 10234567890 (Registro Civil)',
  CE: 'Ej. 987654 (Cédula Extranjería)',
  PAS: 'Ej. AB123456 (Pasaporte)',
  PP: 'Ej. AB123456 (Pasaporte)',
  PASAPORTE: 'Ej. AB123456 (Pasaporte)',
  PPT: 'Ej. 1234567 (PPT)',
  PEP: 'Ej. 12345678 (PEP)',
  NIT: 'Ej. 900123456-1 (NIT con DV)',
};

export const DOC_HINTS = {
  CC: '6 a 10 dígitos numéricos',
  TI: '8 a 11 dígitos numéricos (NUIP)',
  RC: '10 a 11 dígitos numéricos',
  CE: '5 a 11 caracteres alfanuméricos',
  PAS: '6 a 16 caracteres alfanuméricos',
  PP: '6 a 16 caracteres alfanuméricos',
  PASAPORTE: '6 a 16 caracteres alfanuméricos',
  PPT: '6 a 12 caracteres alfanuméricos',
  PEP: '8 a 15 caracteres alfanuméricos',
  NIT: '8 a 10 dígitos, opcional guion y dígito de verificación',
};

/**
 * Retorna el placeholder adaptativo según el tipo de documento.
 * @param {string} tipoDocCodigo - Código (CC, TI, CE, NIT, etc.)
 * @param {boolean} [conciso=false] - Si es true, retorna solo el formato numérico/alfanumérico sin texto adicional
 */
export function getDocPlaceholder(tipoDocCodigo, conciso = false) {
  if (!tipoDocCodigo) return conciso ? 'Ej. 1098765432' : 'Ej. 1098765432 (Cédula)';
  const cod = String(tipoDocCodigo).trim().toUpperCase();
  if (conciso) {
    const map = {
      CC: 'Ej. 1098765432',
      TI: 'Ej. 1023456789',
      RC: 'Ej. 10234567890',
      CE: 'Ej. 987654',
      PAS: 'Ej. AB123456',
      PP: 'Ej. AB123456',
      PASAPORTE: 'Ej. AB123456',
      PPT: 'Ej. 1234567',
      PEP: 'Ej. 12345678',
      NIT: 'Ej. 900123456-1',
    };
    return map[cod] || 'Ej. 1098765432';
  }
  return DOC_PLACEHOLDERS[cod] || 'Ej. 1098765432';
}

/**
 * Retorna la descripción del formato normativo esperado para el tipo de documento.
 * @param {string} tipoDocCodigo - Código del documento
 */
export function getDocHint(tipoDocCodigo) {
  if (!tipoDocCodigo) return 'Número de documento de identidad';
  const cod = String(tipoDocCodigo).trim().toUpperCase();
  return DOC_HINTS[cod] || 'Número de documento válido';
}

/**
 * Valida un documento según el código de tipo de documento colombiano.
 * @param {string} value
 * @param {string} tipoDocCodigo  código del catálogo (CC, TI, CE, PP, PEP, RC, NIT)
 * @param {string} [label]       nombre del campo para el mensaje
 */
export function valDocumento(value, tipoDocCodigo, label = 'El número de documento') {
  const v = (value || '').trim();
  if (!v) return { ok: false, mensaje: `${label} es obligatorio.` };
  if (!tipoDocCodigo) {
    if (!/^[A-Za-z0-9-]{4,30}$/.test(v))
      return { ok: false, mensaje: 'Ingresa un número de documento válido (entre 4 y 30 caracteres).' };
    return { ok: true };
  }
  const cod = String(tipoDocCodigo).toUpperCase();
  const pattern = DOC_PATTERNS[cod];
  if (!pattern) return { ok: true }; // tipo desconocido: no bloquear
  if (!pattern.test(v)) {
    return { ok: false, mensaje: DOC_MESSAGES[cod] || 'Ingresa un número de documento válido.' };
  }
  return { ok: true };
}

const NOMBRE_RE = /^[A-Za-zÁÉÍÓÚáéíóúÑñÜü' ]{2,60}$/;
const JURIDICA_RE = /^[A-Za-zÁÉÍÓÚáéíóúÑñÜü0-9&.,' -]{2,100}$/;

/**
 * Valida nombres y apellidos colombianos:
 * - No permite números ni caracteres arbitrarios.
 * - Permite tildes, ñ, espacios, apóstrofes.
 * - Normaliza espacios múltiples.
 */
export function valNombre(value, label = 'El nombre', opts = {}) {
  const { esJuridica = false, required = true } = opts;
  const raw = (value || '').trim().replace(/\s+/g, ' ');
  if (!raw) {
    return required ? { ok: false, mensaje: `${label} es obligatorio.` } : { ok: true };
  }
  if (esJuridica) {
    if (!JURIDICA_RE.test(raw)) {
      return { ok: false, mensaje: 'Ingresa una razón social válida usando letras, números y signos comunes.' };
    }
    return { ok: true, normalizado: raw };
  }
  if (/\d/.test(raw) || !NOMBRE_RE.test(raw)) {
    return { ok: false, mensaje: 'Ingresa un nombre válido usando letras y espacios.' };
  }
  return { ok: true, normalizado: raw };
}

/** Valida apellido (letras, espacios, tildes, ñ, apóstrofes). */
export function valApellido(value, label = 'El apellido', opts = {}) {
  const { required = true } = opts;
  const raw = (value || '').trim().replace(/\s+/g, ' ');
  if (!raw) {
    return required ? { ok: false, mensaje: `${label} es obligatorio.` } : { ok: true };
  }
  if (/\d/.test(raw) || !NOMBRE_RE.test(raw)) {
    return { ok: false, mensaje: 'Ingresa un apellido válido usando letras y espacios.' };
  }
  return { ok: true, normalizado: raw };
}

const EMAIL_RE = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;

/**
 * Valida correo electrónico:
 * - Estructura razonable usuario@dominio.tld
 * - No permite espacios
 * - Longitud máxima 60 caracteres
 */
export function valEmail(value, opts = {}) {
  const { required = true, max = 60 } = opts;
  const v = (value || '').trim();
  if (!v) {
    return required ? { ok: false, mensaje: 'El correo electrónico es obligatorio.' } : { ok: true };
  }
  if (/\s/.test(v)) {
    return { ok: false, mensaje: 'El correo electrónico no puede contener espacios.' };
  }
  if (v.length > max) {
    return { ok: false, mensaje: `El correo no puede superar los ${max} caracteres.` };
  }
  if (v.includes('..') || !EMAIL_RE.test(v)) {
    return { ok: false, mensaje: 'Ingresa un correo electrónico válido (ejemplo: usuario@correo.com).' };
  }
  return { ok: true };
}

/**
 * Valida celular en formato colombiano:
 * - Exactamente 10 dígitos numéricos.
 * - No permite letras ni símbolos arbitrarios.
 */
export function valTelefono(value, opts = {}) {
  const { required = true, label = 'El número celular' } = opts;
  const raw = String(value ?? '').trim();
  if (!raw) {
    return required ? { ok: false, mensaje: `${label} es obligatorio.` } : { ok: true };
  }
  // Si contiene letras o caracteres no numéricos prohibidos
  if (/[^\d\s-]/.test(raw)) {
    return { ok: false, mensaje: 'Ingresa un número celular colombiano de 10 dígitos.' };
  }
  const digits = raw.replace(/\D/g, '');
  if (digits.length !== 10) {
    return { ok: false, mensaje: 'Ingresa un número celular colombiano de 10 dígitos.' };
  }
  return { ok: true, normalizado: digits };
}

/** Convierte fecha ISO 'YYYY-MM-DD' a Date local verificando no-rollover de calendario. */
export function parseISO(value) {
  if (!value || typeof value !== 'string') return new Date(NaN);
  const parts = value.split('-').map(Number);
  if (parts.length !== 3 || parts.some(Number.isNaN)) return new Date(NaN);
  const [year, month, day] = parts;
  if (month < 1 || month > 12 || day < 1 || day > 31) return new Date(NaN);
  const d = new Date(year, month - 1, day);
  if (Number.isNaN(d.getTime())) return new Date(NaN);
  // Verificación estricta de no-rollover de calendario (ej: 30 de febrero o 31 de abril)
  if (d.getFullYear() !== year || d.getMonth() !== month - 1 || d.getDate() !== day) {
    return new Date(NaN);
  }
  return d;
}

/**
 * Calcula la edad exacta considerando año, mes y día de nacimiento.
 */
export function calcularEdad(fechaISO) {
  if (!fechaISO) return null;
  const nac = parseISO(fechaISO);
  if (Number.isNaN(nac.getTime())) return null;
  const hoy = new Date();
  let edad = hoy.getFullYear() - nac.getFullYear();
  const m = hoy.getMonth() - nac.getMonth();
  if (m < 0 || (m === 0 && hoy.getDate() < nac.getDate())) {
    edad--;
  }
  return edad;
}

/**
 * Valida fecha de nacimiento:
 * - No permite fechas futuras.
 * - No permite mayores a 115 años (cálculo día/mes/año).
 * - Opcional edad mínima y máxima.
 */
export function valFechaNacimiento(value, opts = {}) {
  const { edadMin = 0, edadMax = 115, required = true } = opts;
  if (!value) {
    return required ? { ok: false, mensaje: 'La fecha de nacimiento es obligatoria.' } : { ok: true };
  }
  const date = parseISO(value);
  if (Number.isNaN(date.getTime())) {
    return { ok: false, mensaje: 'La fecha de nacimiento no es válida.' };
  }
  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);

  if (date > hoy) {
    return { ok: false, mensaje: 'La fecha de nacimiento no puede ser futura.' };
  }

  const edad = calcularEdad(value);
  if (edad === null) {
    return { ok: false, mensaje: 'No fue posible calcular la edad con la fecha ingresada.' };
  }

  if (edad < edadMin) {
    return { ok: false, mensaje: `La edad mínima requerida es de ${edadMin} años.` };
  }
  if (edad > edadMax) {
    return { ok: false, mensaje: `La fecha indica una edad superior a ${edadMax} años. Por favor verifica el año.` };
  }

  return { ok: true, edad, esMenor: edad < 18 };
}

/** Valida que una fecha no sea futura (ej. fecha de pago). */
export function valFechaNoFutura(value, label = 'La fecha') {
  if (!value) return { ok: false, mensaje: `${label} es obligatoria.` };
  const d = parseISO(value);
  if (Number.isNaN(d.getTime())) return { ok: false, mensaje: `${label} no es válida.` };
  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);
  if (d > hoy) return { ok: false, mensaje: `${label} no puede ser posterior a hoy.` };
  return { ok: true };
}

/** Valida que el valor no esté vacío. */
export function valRequerido(value, label = 'Este campo') {
  if (value == null || String(value).trim() === '') {
    return { ok: false, mensaje: `${label} es obligatorio.` };
  }
  return { ok: true };
}

/** Valida que un select tenga una opción válida elegida. */
export function valSelect(value, label = 'Selecciona una opción') {
  if (value == null || value === '' || value === '0' || value === 0) {
    return { ok: false, mensaje: `${label}.` };
  }
  return { ok: true };
}

/**
 * Valida valor numérico (enteros, decimales, rangos).
 */
export function valNumero(value, opts = {}) {
  const { min, max, positivo = false, entero = false, required = true, label = 'El valor' } = opts;
  const raw = value == null ? '' : String(value).trim();
  if (!raw) {
    return required ? { ok: false, mensaje: `${label} es obligatorio.` } : { ok: true };
  }
  const n = Number(raw);
  if (Number.isNaN(n) || !Number.isFinite(n)) {
    return { ok: false, mensaje: `${label} debe ser un número válido.` };
  }
  if (positivo && n <= 0) {
    return { ok: false, mensaje: `${label} debe ser mayor que 0.` };
  }
  if (entero && !Number.isInteger(n)) {
    return { ok: false, mensaje: `${label} debe ser un número entero sin decimales.` };
  }
  if (min != null && n < min) {
    return { ok: false, mensaje: `${label} debe ser mayor o igual a ${min}.` };
  }
  if (max != null && n > max) {
    return { ok: false, mensaje: `${label} no puede superar ${max}.` };
  }
  return { ok: true, valor: n };
}

/** Valida número entero. */
export function valEntero(value, opts = {}) {
  return valNumero(value, { entero: true, label: 'El valor', ...opts });
}

/** Valida longitud entre min y max. */
export function valLongitud(value, opts = {}) {
  const { min = 0, max = Infinity } = opts;
  const len = String(value || '').length;
  if (len < min) return { ok: false, mensaje: `Mínimo ${min} caracteres` };
  if (len > max) return { ok: false, mensaje: `Máximo ${max} caracteres` };
  return { ok: true };
}

/** Valida cantidad entera positiva (ej. cupos, unidades, personas). */
export function valCantidad(value, opts = {}) {
  return valNumero(value, { min: 1, entero: true, positivo: true, label: 'La cantidad', ...opts });
}

/** Valida valores monetarios en COP. */
export function valMoneda(value, opts = {}) {
  return valNumero(value, { min: 0, label: 'El monto', ...opts });
}

/** Formatea número a Pesos Colombianos (COP). */
export function formatoCOP(valor) {
  const n = Number(valor) || 0;
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(n);
}

const PLACA_CARRO = /^[A-Z]{3}[-\s]?\d{3}$/i;
const PLACA_MOTO = /^[A-Z]{3}[-\s]?\d{2}[A-Z]$/i;

/**
 * Valida placa según el tipo de vehículo colombiano.
 */
export function valPlaca(value, tipoVehiculo) {
  const v = (value || '').trim().toUpperCase();
  if (!v) return { ok: true }; // Placa opcional si no viene vehículo
  if (v.length > 10) return { ok: false, mensaje: 'La placa no puede superar los 10 caracteres.' };
  const tipo = String(tipoVehiculo || '').toUpperCase();
  if (tipo === 'VEHICULO' || tipo === 'CARRO') {
    if (!PLACA_CARRO.test(v)) {
      return { ok: false, mensaje: 'Formato de placa de carro: 3 letras y 3 números (Ej: DEM-123 o ABC123).' };
    }
  } else if (tipo === 'MOTO') {
    if (!PLACA_MOTO.test(v)) {
      return { ok: false, mensaje: 'Formato de placa de moto: 3 letras, 2 números y 1 letra (Ej: ABC12D).' };
    }
  } else {
    if (!/^[A-Z0-9-]{3,10}$/i.test(v)) {
      return { ok: false, mensaje: 'Placa inválida: debe tener entre 3 y 10 caracteres alfanuméricos.' };
    }
  }
  return { ok: true, normalizada: v.replace(/\s+/g, '') };
}

/**
 * Valida rangos de fechas:
 * - fechaInicio y fechaFin válidas
 * - fechaFin no puede ser anterior a fechaInicio
 */
export function validarFechas({ fechaInicio, fechaFin, noFuturas = false, desdeInicioAnio = false }) {
  if (!fechaInicio || !fechaFin) {
    return { ok: false, mensaje: 'Las fechas de inicio y fin son obligatorias.' };
  }
  const ini = parseISO(fechaInicio);
  const fin = parseISO(fechaFin);
  if (Number.isNaN(ini.getTime()) || Number.isNaN(fin.getTime())) {
    return { ok: false, mensaje: 'Las fechas ingresadas no son válidas.' };
  }
  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);
  const inicioAnio = new Date(hoy.getFullYear(), 0, 1);

  if (desdeInicioAnio && ini < inicioAnio) {
    return { ok: false, mensaje: 'La fecha de inicio no puede ser anterior al inicio del año.' };
  }
  if (noFuturas && (ini > hoy || fin > hoy)) {
    return { ok: false, mensaje: 'Las fechas no pueden ser posteriores a hoy.' };
  }
  if (ini > fin) {
    return { ok: false, mensaje: 'La fecha de inicio no puede ser posterior a la fecha de fin.' };
  }
  return { ok: true };
}

/** Valida longitud y contenido de texto libre (textareas, motivos, descripciones). */
export function valTexto(value, label = 'Este campo', opts = {}) {
  const { min = 1, max = 500, required = true } = opts;
  const v = (value || '').trim();
  if (!v) {
    return required ? { ok: false, mensaje: `${label} es obligatorio.` } : { ok: true };
  }
  if (v.length < min) {
    return { ok: false, mensaje: `${label} debe tener al menos ${min} caracteres.` };
  }
  if (v.length > max) {
    return { ok: false, mensaje: `${label} no puede superar los ${max} caracteres.` };
  }
  return { ok: true };
}

/** Valida contraseña (mínimo 6 caracteres). */
export function valPassword(value, label = 'La contraseña') {
  const v = value || '';
  if (!v) return { ok: false, mensaje: `${label} es obligatoria.` };
  if (v.length < 6) return { ok: false, mensaje: `${label} debe tener mínimo 6 caracteres.` };
  if (v.length > 100) return { ok: false, mensaje: `${label} no puede superar los 100 caracteres.` };
  return { ok: true };
}

/** Valida nombre de usuario para login (3-50 caracteres, sin espacios). */
export function valUsername(value) {
  const v = (value || '').trim();
  if (!v) return { ok: false, mensaje: 'El nombre de usuario o correo es obligatorio.' };
  if (/\s/.test(v)) return { ok: false, mensaje: 'El nombre de usuario no puede contener espacios.' };
  if (!/^[a-zA-Z0-9_.@+-]{3,50}$/.test(v)) {
    return { ok: false, mensaje: 'El usuario debe tener entre 3 y 50 caracteres alfanuméricos.' };
  }
  return { ok: true, username: v };
}

/** Valida número o identificador de apartamento/unidad. */
export function valNumeroApartamento(value) {
  const s = String(value || '').trim();
  if (!s) return { ok: false, mensaje: 'El identificador de la unidad es obligatorio.' };
  if (s.length > 20) return { ok: false, mensaje: 'Máximo 20 caracteres.' };
  if (!/^[A-Za-z0-9][A-Za-z0-9 -]*$/.test(s)) {
    return { ok: false, mensaje: 'Solo letras, números, espacios y guiones.' };
  }
  return { ok: true };
}

/* ===== Filtros de entrada (sanitización mientras se escribe) ===== */

/** Devuelve solo dígitos (máx maxLength). */
export function soloNumeros(value, maxLength) {
  let v = String(value || '').replace(/\D/g, '');
  if (maxLength != null) v = v.slice(0, maxLength);
  return v;
}

/** Devuelve solo letras y espacios (máx maxLength). */
export function soloLetras(value, maxLength) {
  let v = String(value || '').replace(/[^A-Za-zÁÉÍÓÚáéíóúÑñÜü ]/g, '');
  if (maxLength != null) v = v.slice(0, maxLength);
  return v;
}

/** Devuelve solo alfanumérico (máx maxLength; guiones opcionales). */
export function soloAlfanumerico(value, maxLength, permitirGuiones = false) {
  let v = String(value || '');
  v = permitirGuiones ? v.replace(/[^A-Za-z0-9-]/g, '') : v.replace(/[^A-Za-z0-9]/g, '');
  if (maxLength != null) v = v.slice(0, maxLength);
  return v;
}
