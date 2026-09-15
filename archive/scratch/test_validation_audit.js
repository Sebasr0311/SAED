import {
  valDocumento,
  valNombre,
  valApellido,
  valEmail,
  valFechaNacimiento,
  valRequerido,
  valSelect,
  valNumero,
  valEntero,
  valLongitud,
  valPlaca,
  validarFechas,
  soloNumeros,
  soloLetras,
  soloAlfanumerico,
  valTelefono,
  valUsername,
  valPassword,
  valNumeroApartamento,
  calcularEdad,
} from '../frontend/src/lib/validation.js';

console.log('=== EXHAUSTIVE VALIDATION AUDIT SUITE ===\n');

const results = [];

function test(name, fn) {
  try {
    const res = fn();
    results.push({ name, status: 'PASS', details: res });
  } catch (err) {
    results.push({ name, status: 'CRASH', error: err.message, stack: err.stack });
  }
}

// 1. TESTS DE VALNUMERO Y VALENTERO (INYECCIÓN DE TEXTO, SÍMBOLOS, CASOS BORDE)
console.log('--- 1. Testing valNumero & valEntero ---');

const numericFuzzInputs = [
  { val: 'abc', expectedOk: false, desc: 'String de letras' },
  { val: '12abc', expectedOk: false, desc: 'Letras al final' },
  { val: 'abc12', expectedOk: false, desc: 'Letras al inicio' },
  { val: '1.2.3', expectedOk: false, desc: 'Múltiples puntos decimales' },
  { val: '1,5', expectedOk: false, desc: 'Coma decimal en string' },
  { val: '   ', expectedOk: false, desc: 'Espacios en blanco' },
  { val: null, expectedOk: false, desc: 'Null' },
  { val: undefined, expectedOk: false, desc: 'Undefined' },
  { val: NaN, expectedOk: false, desc: 'NaN literal' },
  { val: Infinity, expectedOk: true, desc: 'Infinity' }, // Warning! Is Infinity a valid number?
  { val: -Infinity, expectedOk: true, desc: '-Infinity' },
  { val: '1e5', expectedOk: true, desc: 'Notación científica' },
  { val: '1e309', expectedOk: true, desc: 'Overflow científico a Infinity' },
  { val: '0', expectedOk: true, desc: 'Cero en string' },
  { val: 0, expectedOk: true, desc: 'Cero numérico' },
  { val: -5, expectedOk: true, desc: 'Número negativo sin flag positivo' },
  { val: '0x1F', expectedOk: true, desc: 'Hexadecimal en string (Number("0x1F") === 31)' },
  { val: '<script>1</script>', expectedOk: false, desc: 'XSS payload' },
  { val: "' OR 1=1 --", expectedOk: false, desc: 'SQLi payload' },
];

for (const input of numericFuzzInputs) {
  test(`valNumero: ${input.desc} ("${input.val}")`, () => {
    const r = valNumero(input.val);
    return `ok: ${r.ok}, mensaje: "${r.mensaje || ''}"`;
  });

  test(`valEntero: ${input.desc} ("${input.val}")`, () => {
    const r = valEntero(input.val);
    return `ok: ${r.ok}, mensaje: "${r.mensaje || ''}"`;
  });
}

// Check valEntero with floating point numbers
test('valEntero con decimal 10.5', () => {
  const r = valEntero('10.5');
  if (r.ok) throw new Error('10.5 no debería ser entero válido');
  return r;
});

test('valEntero con 0.0000001', () => {
  const r = valEntero('0.0000001');
  if (r.ok) throw new Error('0.0000001 no debería ser entero');
  return r;
});

test('valNumero con positivo: true y valor 0', () => {
  const r = valNumero(0, { positivo: true });
  if (r.ok) throw new Error('0 no es positivo (>0)');
  return r;
});

test('valNumero con positivo: true y valor -1', () => {
  const r = valNumero(-1, { positivo: true });
  if (r.ok) throw new Error('-1 no es positivo');
  return r;
});

// 2. TESTS DE VALDOCUMENTO (COLOMBIA)
console.log('--- 2. Testing valDocumento ---');
const docFuzzInputs = [
  // CC (solo números 6-10)
  { val: '123456', tipo: 'CC', expect: true, desc: 'CC 6 dígitos' },
  { val: '1234567890', tipo: 'CC', expect: true, desc: 'CC 10 dígitos' },
  { val: '12345', tipo: 'CC', expect: false, desc: 'CC 5 dígitos (muy corta)' },
  { val: '12345678901', tipo: 'CC', expect: false, desc: 'CC 11 dígitos (muy larga)' },
  { val: '12345678A', tipo: 'CC', expect: false, desc: 'CC con letra' },
  { val: '123 456', tipo: 'CC', expect: false, desc: 'CC con espacio' },
  { val: '123-456-789', tipo: 'CC', expect: false, desc: 'CC con guiones' },
  { val: '<script>', tipo: 'CC', expect: false, desc: 'CC con XSS' },
  
  // NIT (8-10 dígitos, con o sin DV)
  { val: '900123456', tipo: 'NIT', expect: true, desc: 'NIT 9 dígitos sin DV' },
  { val: '900123456-1', tipo: 'NIT', expect: true, desc: 'NIT 9 dígitos con DV' },
  { val: '12345678-9', tipo: 'NIT', expect: true, desc: 'NIT 8 dígitos con DV' },
  { val: '900123456-12', tipo: 'NIT', expect: false, desc: 'NIT con 2 dígitos DV' },
  { val: '900123456A', tipo: 'NIT', expect: false, desc: 'NIT con letra' },
  
  // CE (5-11 alfanumérico)
  { val: 'E123456', tipo: 'CE', expect: true, desc: 'CE válido con letra E' },
  { val: '123456', tipo: 'CE', expect: true, desc: 'CE solo números' },
  { val: '1234', tipo: 'CE', expect: false, desc: 'CE 4 chars (muy corto)' },
  { val: '123456789012', tipo: 'CE', expect: false, desc: 'CE 12 chars (muy largo)' },
  { val: 'CE-12345', tipo: 'CE', expect: false, desc: 'CE con guión' },
  
  // TI (8-11 dígitos)
  { val: '10203040', tipo: 'TI', expect: true, desc: 'TI 8 dígitos' },
  { val: '10203040506', tipo: 'TI', expect: true, desc: 'TI 11 dígitos (NUIP)' },
  { val: '1020304', tipo: 'TI', expect: false, desc: 'TI 7 dígitos' },
  { val: '102030405060', tipo: 'TI', expect: false, desc: 'TI 12 dígitos' },
  
  // PAS (6-16 alfanumérico)
  { val: 'AB123456', tipo: 'PAS', expect: true, desc: 'Pasaporte alfanumérico' },
  { val: 'A1234', tipo: 'PAS', expect: false, desc: 'Pasaporte 5 chars' },
];

for (const input of docFuzzInputs) {
  test(`valDocumento ${input.tipo}: ${input.desc} ("${input.val}")`, () => {
    const r = valDocumento(input.val, input.tipo);
    if (r.ok !== input.expect) {
      throw new Error(`Esperaba ok=${input.expect} pero obtuvo ok=${r.ok} (${r.mensaje})`);
    }
    return r;
  });
}

// 3. TESTS DE VALNOMBRE Y VALAPELLIDO (XSS, SQLi, Caracteres Raros, Longitudes)
console.log('--- 3. Testing valNombre & valApellido ---');
const nameFuzzInputs = [
  { val: 'Juan', expect: true, desc: 'Nombre común' },
  { val: 'María José', expect: true, desc: 'Nombre con tilde y espacio' },
  { val: "D'Angelo", expect: true, desc: 'Nombre con apóstrofe' },
  { val: 'A', expect: false, desc: 'Nombre 1 letra (mínimo 2)' },
  { val: 'EsteEsUnNombreDemasiadoLargoSuperaVeinticinco', expect: false, desc: 'Nombre > 25 chars' },
  { val: 'Juan123', expect: false, desc: 'Nombre con números' },
  { val: '<script>', expect: false, desc: 'Nombre con tags HTML' },
  { val: "Robert'); DROP TABLE--", expect: false, desc: 'SQLi en nombre' },
  { val: '   ', expect: false, desc: 'Espacios vacíos' },
  { val: null, expect: false, desc: 'Null' },
  { val: undefined, expect: false, desc: 'Undefined' },
];

for (const input of nameFuzzInputs) {
  test(`valNombre: ${input.desc} ("${input.val}")`, () => {
    const r = valNombre(input.val);
    if (r.ok !== input.expect) {
      throw new Error(`Esperaba ok=${input.expect} pero obtuvo ok=${r.ok} (${r.mensaje})`);
    }
    return r;
  });
}

// 4. TESTS DE VALEMAIL
console.log('--- 4. Testing valEmail ---');
const emailFuzzInputs = [
  { val: 'admin@saed.com', expect: true, desc: 'Email estándar' },
  { val: 'user.name+tag@sub.domain.co', expect: true, desc: 'Email complejo' },
  { val: 'plainaddress', expect: false, desc: 'Sin @ ni dominio' },
  { val: '@missingusername.com', expect: false, desc: 'Sin usuario' },
  { val: 'username@.com', expect: false, desc: 'Dominio sin nombre' },
  { val: 'username@domain.c', expect: false, desc: 'TLD 1 letra' },
  { val: 'username@domain..com', expect: false, desc: 'Doble punto' },
  { val: 'a'.repeat(35) + '@saed.com', expect: false, desc: 'Longitud > 40 caracteres (regla legacy SAED)' },
  { val: '<script>@domain.com', expect: false, desc: 'Email con XSS' },
];

for (const input of emailFuzzInputs) {
  test(`valEmail: ${input.desc} ("${input.val}")`, () => {
    const r = valEmail(input.val);
    if (r.ok !== input.expect) {
      throw new Error(`Esperaba ok=${input.expect} pero obtuvo ok=${r.ok} (${r.mensaje})`);
    }
    return r;
  });
}

// 5. TESTS DE VALFECHANACIMIENTO Y PARSING DE FECHAS
console.log('--- 5. Testing valFechaNacimiento ---');
const dateFuzzInputs = [
  { val: '1990-05-15', expect: true, desc: 'Fecha válida adulta' },
  { val: '2050-01-01', expect: false, desc: 'Fecha futura' },
  { val: '1899-12-31', expect: false, desc: 'Año anterior a 1900' },
  { val: 'invalid-date', expect: false, desc: 'String no fecha' },
  { val: '2020-02-30', expect: false, desc: '30 de febrero' },
  { val: '2020-13-01', expect: false, desc: 'Mes 13' },
  { val: '2020-00-01', expect: false, desc: 'Mes 0' },
  { val: '99999999-99-99', expect: false, desc: 'Overflow de fecha' },
  { val: null, expect: false, desc: 'Null' },
];

for (const input of dateFuzzInputs) {
  test(`valFechaNacimiento: ${input.desc} ("${input.val}")`, () => {
    const r = valFechaNacimiento(input.val);
    if (r.ok !== input.expect) {
      throw new Error(`Esperaba ok=${input.expect} pero obtuvo ok=${r.ok} (${r.mensaje})`);
    }
    return r;
  });
}

// 6. TESTS DE VALPLACA (CARRO Y MOTO)
console.log('--- 6. Testing valPlaca ---');
const placaFuzzInputs = [
  { val: 'ABC123', tipo: 'CARRO', expect: true, desc: 'Placa carro sin espacio' },
  { val: 'ABC 123', tipo: 'CARRO', expect: true, desc: 'Placa carro con espacio' },
  { val: 'ABC12D', tipo: 'CARRO', expect: false, desc: 'Placa moto en carro' },
  { val: 'AB1234', tipo: 'CARRO', expect: false, desc: 'Formato inválido' },
  { val: 'ABC12D', tipo: 'MOTO', expect: true, desc: 'Placa moto válida' },
  { val: 'ABC 12D', tipo: 'MOTO', expect: true, desc: 'Placa moto con espacio' },
  { val: 'ABC123', tipo: 'MOTO', expect: false, desc: 'Placa carro en moto' },
  { val: '', tipo: 'CARRO', expect: true, desc: 'Placa vacía (opcional)' },
  { val: 'ABCDEFGHIJKLMN', tipo: 'CARRO', expect: false, desc: 'Placa > 10 chars' },
];

for (const input of placaFuzzInputs) {
  test(`valPlaca: ${input.desc} ("${input.val}")`, () => {
    const r = valPlaca(input.val, input.tipo);
    if (r.ok !== input.expect) {
      throw new Error(`Esperaba ok=${input.expect} pero obtuvo ok=${r.ok} (${r.mensaje})`);
    }
    return r;
  });
}

// 7. TESTS DE SANITIZADORES (soloNumeros, soloLetras, soloAlfanumerico)
console.log('--- 7. Testing soloNumeros, soloLetras, soloAlfanumerico ---');
test('soloNumeros elimina letras y símbolos', () => {
  const res = soloNumeros('123abc#$45', 10);
  if (res !== '12345') throw new Error(`Esperaba "12345", obtuvo "${res}"`);
  return res;
});

test('soloNumeros respeta maxLength', () => {
  const res = soloNumeros('1234567890', 5);
  if (res !== '12345') throw new Error(`Esperaba "12345", obtuvo "${res}"`);
  return res;
});

test('soloLetras elimina números y símbolos', () => {
  const res = soloLetras('Juan123 Carlos!@#', 20);
  if (res !== 'Juan Carlos') throw new Error(`Esperaba "Juan Carlos", obtuvo "${res}"`);
  return res;
});

test('soloAlfanumerico permite guiones si está habilitado', () => {
  const res = soloAlfanumerico('ABC-123_45!', 10, true);
  if (res !== 'ABC-12345') throw new Error(`Esperaba "ABC-12345", obtuvo "${res}"`);
  return res;
});

// RESUMEN
console.log('\n=== AUDIT RESULTS SUMMARY ===');
const passed = results.filter((r) => r.status === 'PASS').length;
const crashed = results.filter((r) => r.status === 'CRASH').length;
console.log(`Total tests: ${results.length}`);
console.log(`Passed: ${passed}`);
console.log(`Crashed: ${crashed}`);

if (crashed > 0) {
  console.log('\nCRASHED TESTS:');
  for (const c of results.filter((r) => r.status === 'CRASH')) {
    console.error(`- ${c.name}: ${c.error}`);
  }
}
