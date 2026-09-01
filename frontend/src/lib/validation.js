/**
 * Validadores reutilizables del frontend — portados del sistema original
 * (prueba_proyeccto/frontend/js/utils.js) a React.
 *
 * Convención: cada función devuelve { ok: boolean, mensaje?: string }.
 * La UI decide cómo mostrar el mensaje (junto al campo).
 * No manipulan el DOM: reciben el valor y devuelven el veredicto.
 */

/** Regex de documento por código de tipo (Colombia). */
const DOC_PATTERNS = {
  CC: /^\d{7,10}$/,            // Cédula de Ciudadanía: solo números, 7-10
  TI: /^\d{7,10}$/,            // Tarjeta de Identidad: solo números, 7-10
  RC: /^\d{7,10}$/,            // Registro Civil: solo números, 7-10
  CE: /^[A-Za-z]\d{5,11}$/,    // Cédula de Extranjería: letra + 5-11 dígitos
  PP: /^[A-Z0-9]{7,15}$/i,     // Pasaporte
  PASAPORTE: /^[A-Z0-9]{7,15}$/i,
  PEP: /^[A-Z0-9]{8,15}$/i,    // Permiso Especial de Permanencia
  NIT: /^\d{9,13}$/,           // NIT
};

const DOC_MESSAGES = {
  CC: 'Cédula: solo números, entre 7 y 10 dígitos',
  TI: 'TI: solo números, entre 7 y 10 dígitos',
  RC: 'Registro Civil: solo números, entre 7 y 10 dígitos',
  CE: 'CE: letra seguida de 5 a 11 dígitos (ej: E123456)',
  PP: 'Pasaporte: letras y números, entre 7 y 15 caracteres',
  PASAPORTE: 'Pasaporte: letras y números, entre 7 y 15 caracteres',
  PEP: 'PEP: alfanumérico, entre 8 y 15 caracteres',
  NIT: 'NIT: solo números, entre 9 y 13 dígitos',
};

/**
 * Valida un documento según el código de tipo de documento.
 * @param {string} value
 * @param {string} tipoDocCodigo  código del catálogo (CC, TI, CE, PP, PEP, RC, NIT)
 * @param {string} [label]       nombre del campo para el mensaje
 */
export function valDocumento(value, tipoDocCodigo, label = 'El documento') {
  const v = (value || '').trim();
  if (!v) return { ok: false, mensaje: `${label} es obligatorio` };
  if (!tipoDocCodigo) {
    // Sin tipo definido: acepta alfanumérico genérico 4-30 (comportamiento legacy)
    if (!/^[A-Za-z0-9-]{4,30}$/.test(v))
      return { ok: false, mensaje: 'Documento inválido' };
    return { ok: true };
  }
  const pattern = DOC_PATTERNS[tipoDocCodigo];
  if (!pattern) return { ok: true }; // tipo desconocido: no bloquear
  if (!pattern.test(v))
    return { ok: false, mensaje: DOC_MESSAGES[tipoDocCodigo] || 'Documento inválido' };
  return { ok: true };
}

const NOMBRE_RE = /^[A-Za-zÁÉÍÓÚáéíóúÑñÜü' ]{2,25}$/;

/** Valida nombre (2-25, letras y espacios). */
export function valNombre(value, label = 'El nombre') {
  const v = (value || '').trim();
  if (!v) return { ok: false, mensaje: `${label} es obligatorio` };
  if (!NOMBRE_RE.test(v))
    return { ok: false, mensaje: `${label} debe tener entre 2 y 25 caracteres y solo letras` };
  return { ok: true };
}

/** Valida apellido (2-25, letras y espacios). */
export function valApellido(value, label = 'El apellido') {
  return valNombre(value, label);
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/**
 * Valida email: formato + longitud máxima 40 (regla legacy).
 * @param {string} value
 * @param {{required?: boolean, max?: number}} [opts]
 */
export function valEmail(value, opts = {}) {
  const { required = true, max = 40 } = opts;
  const v = (value || '').trim();
  if (!v) {
    if (required) return { ok: false, mensaje: 'El email es obligatorio' };
    return { ok: true };
  }
  if (v.length > max) return { ok: false, mensaje: `El email no puede superar ${max} caracteres` };
  if (!EMAIL_RE.test(v)) return { ok: false, mensaje: 'Email inválido' };
  return { ok: true };
}

/** Convierte fecha ISO 'YYYY-MM-DD' a Date local sin desfase de zona. */
function parseISO(value) {
  const parts = String(value).split('-').map(Number);
  if (parts.length !== 3 || parts.some(Number.isNaN)) return new Date(NaN);
  return new Date(parts[0], parts[1] - 1, parts[2]);
}

/** Calcula edad a partir de fecha ISO. */
export function calcularEdad(fechaISO) {
  if (!fechaISO) return null;
  const nac = parseISO(fechaISO);
  if (Number.isNaN(nac.getTime())) return null;
  const hoy = new Date();
  let edad = hoy.getFullYear() - nac.getFullYear();
  const m = hoy.getMonth() - nac.getMonth();
  if (m < 0 || (m === 0 && hoy.getDate() < nac.getDate())) edad--;
  return edad;
}

/**
 * Valida fecha de nacimiento.
 * Reglas: obligatoria, válida, no futura, no de hoy (min 1 día de nacido),
 * edad <= 115, edadMin/edadMax.
 */
export function valFechaNacimiento(value, opts = {}) {
  const { edadMin = 0, edadMax = 115 } = opts;
  if (!value) return { ok: false, mensaje: 'La fecha de nacimiento es obligatoria' };
  const fecha = parseISO(value);
  if (Number.isNaN(fecha.getTime())) return { ok: false, mensaje: 'Fecha inválida' };
  if (fecha.getFullYear() < 1900)
    return { ok: false, mensaje: 'Fecha de nacimiento no válida (año anterior a 1900)' };
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
  if (fecha > hoy) return { ok: false, mensaje: 'La fecha de nacimiento no puede ser futura' };
  // Minimo 1 dia de nacido: hoy mismo no es valido
  const ayer = new Date(hoy); ayer.setDate(hoy.getDate() - 1);
  if (fecha > ayer) return { ok: false, mensaje: 'La fecha de nacimiento debe ser al menos de ayer (mínimo 1 día de nacido)' };
  const edad = calcularEdad(value);
  if (edad === null) return { ok: false, mensaje: 'Fecha inválida' };
  if (edad > edadMax) return { ok: false, mensaje: `La edad no puede superar ${edadMax} años` };
  if (edad < edadMin) return { ok: false, mensaje: `La edad mínima es ${edadMin} año(s)` };
  return { ok: true };
}

/** Valida que el valor no esté vacío. */
export function valRequerido(value, label = 'Este campo') {
  if (value == null || String(value).trim() === '')
    return { ok: false, mensaje: `${label} es obligatorio` };
  return { ok: true };
}

/** Valida que un select tenga opción elegida. */
export function valSelect(value, label = 'Seleccione una opción') {
  if (value == null || value === '' || value === '0')
    return { ok: false, mensaje: label };
  return { ok: true };
}

/**
 * Valida número.
 * @param {string|number} value
 * @param {{min?: number, max?: number, positivo?: boolean}} [opts]
 */
export function valNumero(value, opts = {}) {
  const { min, max, positivo = false } = opts;
  if (value == null || value === '') return { ok: false, mensaje: 'Este campo es obligatorio' };
  const n = Number(value);
  if (Number.isNaN(n)) return { ok: false, mensaje: 'Debe ser un número' };
  if (positivo && n <= 0) return { ok: false, mensaje: 'Debe ser mayor que 0' };
  if (min != null && n < min) return { ok: false, mensaje: `Mínimo ${min}` };
  if (max != null && n > max) return { ok: false, mensaje: `Máximo ${max}` };
  return { ok: true };
}

/**
 * Valida entero.
 * @param {string|number} value
 * @param {{min?: number, max?: number, positivo?: boolean}} [opts]
 */
export function valEntero(value, opts = {}) {
  const base = valNumero(value, opts);
  if (!base.ok) return base;
  if (!Number.isInteger(Number(value))) return { ok: false, mensaje: 'Debe ser un número entero' };
  return { ok: true };
}

/** Valida longitud entre min y max. */
export function valLongitud(value, opts = {}) {
  const { min = 0, max = Infinity } = opts;
  const len = String(value || '').length;
  if (len < min) return { ok: false, mensaje: `Mínimo ${min} caracteres` };
  if (len > max) return { ok: false, mensaje: `Máximo ${max} caracteres` };
  return { ok: true };
}

const PLACA_CARRO = /^[A-Z]{3}\s?\d{3}$/i;
const PLACA_MOTO = /^[A-Z]{3}\s?\d{2}[A-Z]$/i;

/**
 * Valida placa según tipo de vehículo.
 * VEHICULO/CARRO: 3 letras + 3 números; MOTO: 3 letras + 2 números + letra;
 * BICICLETA/OTRO: alfanumérico 3-10. Placa vacía = válida (opcional).
 */
export function valPlaca(value, tipoVehiculo) {
  const v = (value || '').trim();
  if (!v) return { ok: true }; // opcional
  if (v.length > 10) return { ok: false, mensaje: 'Máximo 10 caracteres' };
  const tipo = String(tipoVehiculo || '').toUpperCase();
  if (tipo === 'VEHICULO' || tipo === 'CARRO') {
    if (!PLACA_CARRO.test(v))
      return { ok: false, mensaje: 'Formato de placa de carro: 3 letras + 3 números (Ej: ABC 123)' };
  } else if (tipo === 'MOTO') {
    if (!PLACA_MOTO.test(v))
      return { ok: false, mensaje: 'Formato de placa de moto: 3 letras + 2 números + 1 letra (Ej: ABC 12D)' };
  } else {
    if (!/^[A-Za-z0-9-]{3,10}$/.test(v))
      return { ok: false, mensaje: 'Placa inválida (3-10 caracteres alfanuméricos)' };
  }
  return { ok: true };
}

/**
 * Valida rangos de fechas (reportes/filtros): no futuras, no anteriores al
 * inicio del año cuando corresponda, inicio <= fin.
 * @param {{fechaInicio?: string, fechaFin?: string, noFuturas?: boolean, desdeInicioAnio?: boolean}} opts
 */
export function validarFechas({ fechaInicio, fechaFin, noFuturas = true, desdeInicioAnio = true }) {
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
  const inicioAnio = new Date(hoy.getFullYear(), 0, 1);

  if (!fechaInicio || !fechaFin) return { ok: false, mensaje: 'Las fechas de inicio y fin son obligatorias' };

  const ini = parseISO(fechaInicio);
  const fin = parseISO(fechaFin);
  if (Number.isNaN(ini.getTime()) || Number.isNaN(fin.getTime()))
    return { ok: false, mensaje: 'Fechas inválidas' };

  if (desdeInicioAnio && ini < inicioAnio)
    return { ok: false, mensaje: 'La fecha de inicio no puede ser anterior al inicio del año' };
  if (noFuturas && ini > hoy)
    return { ok: false, mensaje: 'La fecha de inicio no puede ser futura' };
  if (noFuturas && fin > hoy)
    return { ok: false, mensaje: 'La fecha de fin no puede ser futura' };
  if (ini > fin) return { ok: false, mensaje: 'La fecha de inicio no puede ser mayor que la de fin' };

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

/** Valida teléfono: exactamente 10 dígitos (opcional si allowEmpty). */
export function valTelefono(value, opts = {}) {
  const { required = true } = opts;
  const v = String(value || '').replace(/\D/g, '');
  if (!v) return required ? { ok: false, mensaje: 'El teléfono es obligatorio' } : { ok: true };
  if (!/^\d{10}$/.test(v)) return { ok: false, mensaje: 'El teléfono debe tener 10 dígitos' };
  return { ok: true };
}

/** Valida username o email: 3-100 caracteres, [a-zA-Z0-9_.@+-] */
export function valUsername(value) {
  const v = (value || '').trim();
  if (!v) return { ok: false, mensaje: 'El usuario o correo es obligatorio' };
  if (!/^[a-zA-Z0-9_.@+-]{3,100}$/.test(v))
    return { ok: false, mensaje: 'Usuario o correo no válido' };
  return { ok: true, username: v };
}

/** Valida password: 6-100. */
export function valPassword(value) {
  const v = value || '';
  if (!v) return { ok: false, mensaje: 'La contraseña es obligatoria' };
  if (v.length < 6 || v.length > 100)
    return { ok: false, mensaje: 'La contraseña debe tener entre 6 y 100 caracteres' };
  return { ok: true };
}

/** Valida el numero de un apartamento: alfanumerico, 1-15 chars, sin simbolos. */
export function valNumeroApartamento(value) {
  if (!value || !String(value).trim()) return { ok: false, mensaje: 'El numero del apartamento es obligatorio' };
  const s = String(value).trim();
  if (s.length > 15) return { ok: false, mensaje: 'Maximo 15 caracteres' };
  if (!/^[A-Za-z0-9][A-Za-z0-9 -]*$/.test(s))
    return { ok: false, mensaje: 'Solo letras, numeros, espacios y guiones' };
  return { ok: true };
}
