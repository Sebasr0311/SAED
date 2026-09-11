// Node.js 18+ has built-in fetch

const BASE_URL = 'https://saed-backend.onrender.com/api/v1';

// Colores para reporte en consola
const RED = '\x1b[31m';
const GREEN = '\x1b[32m';
const YELLOW = '\x1b[33m';
const CYAN = '\x1b[36m';
const RESET = '\x1b[0m';

async function login(username, password, retries = 3) {
  for (let i = 0; i < retries; i++) {
    try {
      const res = await fetch(`${BASE_URL}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(`Login failed (${res.status}): ${JSON.stringify(data)}`);
      return data.token;
    } catch (err) {
      if (i === retries - 1) throw err;
      await new Promise((r) => setTimeout(r, 1500));
    }
  }
}

const report = [];

function recordFinding(category, testName, endpoint, payload, response, status) {
  let severity = 'LOW';
  let issueType = 'CLEAN';

  if (status === 500) {
    severity = 'HIGH';
    issueType = 'INTERNAL_SERVER_ERROR_500';
  } else if (status === 200 || status === 201) {
    // Si era un payload inválido y fue aceptado con 200/201, es una falla de validación
    severity = 'CRITICAL';
    issueType = 'UNVALIDATED_ACCEPTED_200';
  } else if (status === 400 || status === 422) {
    severity = 'INFO';
    issueType = 'PROPERLY_VALIDATED_400';
  } else if (status === 403 || status === 401) {
    severity = 'INFO';
    issueType = 'SECURITY_BLOCKED';
  }

  const finding = {
    category,
    testName,
    endpoint,
    payload,
    status,
    issueType,
    severity,
    responseBody: response,
  };
  report.push(finding);

  const color = severity === 'HIGH' || severity === 'CRITICAL' ? RED : (severity === 'INFO' ? GREEN : YELLOW);
  console.log(`${color}[${severity}] [${issueType}] ${testName} -> HTTP ${status}${RESET}`);
  if (severity === 'HIGH' || severity === 'CRITICAL') {
    console.log(`   Endpoint: ${endpoint}`);
    console.log(`   Payload: ${JSON.stringify(payload)}`);
    console.log(`   Response: ${JSON.stringify(response).slice(0, 150)}...\n`);
  }
}

async function runTests() {
  console.log(`${CYAN}=== INICIANDO AUDITORÍA Y FUZZING DE VALIDACIONES EN BUILD REAL ===${RESET}`);
  console.log(`Target Backend: ${BASE_URL}\n`);

  let tokenAdmin;
  let tokenSuper;

  try {
    tokenAdmin = await login('admin', 'admin123');
    tokenSuper = await login('admin_global', 'admin_global123');
    console.log(`${GREEN}✓ Tokens de autenticación obtenidos (ADMIN_PROPIEDAD y SUPERADMIN)${RESET}\n`);
  } catch (err) {
    console.error(`${RED}Error crítico al autenticar para pruebas: ${err.message}${RESET}`);
    process.exit(1);
  }

  async function send(method, path, token, body) {
    try {
      const res = await fetch(`${BASE_URL}${path}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        },
        body: body ? JSON.stringify(body) : undefined,
      });
      let json;
      try {
        json = await res.json();
      } catch (_) {
        json = await res.text();
      }
      return { status: res.status, data: json };
    } catch (e) {
      return { status: 0, data: { networkError: e.message } };
    }
  }

  // =========================================================================
  // GRUPO 1: LETRAS / TEXTO EN CAMPOS NUMÉRICOS (INTEGER / DECIMAL TYPE CONFUSION)
  // =========================================================================
  console.log(`${CYAN}--- GRUPO 1: Inyección de Letras en Campos Numéricos ---${RESET}`);

  // 1.1 Gastos: monto con letras
  {
    const p = { categoria: 'MANTENIMIENTO', beneficiario: 'Proveedor SAS', monto: 'quinientos mil pesos' };
    const res = await send('POST', '/gastos', tokenAdmin, p);
    recordFinding('TYPE_CONFUSION', 'Gastos con monto alfanumérico', '/gastos', p, res.data, res.status);
  }

  // 1.2 Gastos: monto con símbolos / NaN
  {
    const p = { categoria: 'MANTENIMIENTO', beneficiario: 'Proveedor SAS', monto: '$120.000,50' };
    const res = await send('POST', '/gastos', tokenAdmin, p);
    recordFinding('TYPE_CONFUSION', 'Gastos con monto formateado moneda', '/gastos', p, res.data, res.status);
  }

  // 1.3 Presupuestos: montoPresupuestado con texto
  {
    const p = { descripcion: 'Presupuesto 2026', montoPresupuestado: 'cien millones', vigenciaAnio: 'este anio' };
    const res = await send('POST', '/presupuestos', tokenAdmin, p);
    recordFinding('TYPE_CONFUSION', 'Presupuesto con montos en texto', '/presupuestos', p, res.data, res.status);
  }

  // 1.4 Conciliaciones: saldoBanco y saldoLibros con texto
  {
    const p = { fechaCorte: '2026-09-01', saldoBanco: 'saldo_invalido', saldoLibros: 'saldo_invalido' };
    const res = await send('POST', '/conciliaciones', tokenAdmin, p);
    recordFinding('TYPE_CONFUSION', 'Conciliación con saldos texto', '/conciliaciones', p, res.data, res.status);
  }

  // 1.5 Planes: precioMensual con texto
  {
    const p = { codigo: 'FUZZ_PLAN', nombre: 'Plan Fuzz', precioMensual: 'gratis' };
    const res = await send('POST', '/planes', tokenSuper, p);
    recordFinding('TYPE_CONFUSION', 'Plan con precio mensual texto', '/planes', p, res.data, res.status);
  }

  // 1.6 Paz y Salvos: idUnidad con texto
  {
    const p = { idUnidad: 'apto_101_letras', motivo: 'Venta' };
    const res = await send('POST', '/paz-y-salvos', tokenAdmin, p);
    recordFinding('TYPE_CONFUSION', 'Paz y salvo con idUnidad alfanumérico', '/paz-y-salvos', p, res.data, res.status);
  }

  // =========================================================================
  // GRUPO 2: NÚMEROS NEGATIVOS EN CAMPOS QUE DEBEN SER ESTRICTAMENTE POSITIVOS
  // =========================================================================
  console.log(`\n${CYAN}--- GRUPO 2: Números Negativos o Cero Ilegítimo ---${RESET}`);

  // 2.1 Gastos con monto negativo
  {
    const p = { categoria: 'SERVICIOS', beneficiario: 'Empresa Energia', monto: -500000 };
    const res = await send('POST', '/gastos', tokenAdmin, p);
    recordFinding('NEGATIVE_NUMBERS', 'Gasto con monto negativo (-500.000)', '/gastos', p, res.data, res.status);
  }

  // 2.2 Plan con precio negativo
  {
    const p = { codigo: 'NEG_PLAN', nombre: 'Plan Negativo', precioMensual: -99000 };
    const res = await send('POST', '/planes', tokenSuper, p);
    recordFinding('NEGATIVE_NUMBERS', 'Plan con precio mensual negativo (-99.000)', '/planes', p, res.data, res.status);
  }

  // 2.3 Presupuesto con monto negativo
  {
    const p = { descripcion: 'Presupuesto Negativo', montoPresupuestado: -10000000, vigenciaAnio: 2026 };
    const res = await send('POST', '/presupuestos', tokenAdmin, p);
    recordFinding('NEGATIVE_NUMBERS', 'Presupuesto con monto negativo', '/presupuestos', p, res.data, res.status);
  }

  // =========================================================================
  // GRUPO 3: FECHAS INVÁLIDAS Y DATE ROLLOVER EN ORACLE
  // =========================================================================
  console.log(`\n${CYAN}--- GRUPO 3: Fechas Inválidas / Desborde de Calendario ---${RESET}`);

  // 3.1 Gasto con 30 de febrero (fecha inexistente)
  {
    const p = { categoria: 'ASEO', beneficiario: 'Limpieza SA', monto: 100000, fechaGasto: '2026-02-30' };
    const res = await send('POST', '/gastos', tokenAdmin, p);
    recordFinding('DATE_VALIDATION', 'Gasto con fecha 30 de febrero', '/gastos', p, res.data, res.status);
  }

  // 3.2 Gasto con mes 13
  {
    const p = { categoria: 'ASEO', beneficiario: 'Limpieza SA', monto: 100000, fechaGasto: '2026-13-01' };
    const res = await send('POST', '/gastos', tokenAdmin, p);
    recordFinding('DATE_VALIDATION', 'Gasto con mes 13', '/gastos', p, res.data, res.status);
  }

  // 3.3 Gasto con formato totalmente inválido
  {
    const p = { categoria: 'ASEO', beneficiario: 'Limpieza SA', monto: 100000, fechaGasto: 'hoy_en_la_tarde' };
    const res = await send('POST', '/gastos', tokenAdmin, p);
    recordFinding('DATE_VALIDATION', 'Gasto con fecha en formato no ISO', '/gastos', p, res.data, res.status);
  }

  // =========================================================================
  // GRUPO 4: DESBORDE DE LONGITUD DE CARACTERES (VARCHAR2 OVERFLOW / BUFFER STRESS)
  // =========================================================================
  console.log(`\n${CYAN}--- GRUPO 4: Desborde de Longitud de Cadenas (Buffer Stress) ---${RESET}`);

  // 4.1 Categoría de gasto de 5.000 caracteres
  {
    const p = { categoria: 'X'.repeat(5000), beneficiario: 'Proveedor', monto: 10000 };
    const res = await send('POST', '/gastos', tokenAdmin, p);
    recordFinding('BUFFER_OVERFLOW', 'Gasto con categoría de 5.000 caracteres', '/gastos', p, res.data, res.status);
  }

  // 4.2 Código de plan de 1.000 caracteres
  {
    const p = { codigo: 'P'.repeat(1000), nombre: 'Plan Gigante', precioMensual: 1000 };
    const res = await send('POST', '/planes', tokenSuper, p);
    recordFinding('BUFFER_OVERFLOW', 'Plan con código de 1.000 caracteres', '/planes', p, res.data, res.status);
  }

  // =========================================================================
  // GRUPO 5: INYECCIÓN DE PAYLOADS DE SEGURIDAD (SQLi / XSS)
  // =========================================================================
  console.log(`\n${CYAN}--- GRUPO 5: Inyección SQLi y XSS en Campos de Entrada ---${RESET}`);

  // 5.1 SQL Injection clásico en beneficiario
  {
    const p = { categoria: 'LEGAL', beneficiario: "'; DROP TABLE USUARIOS; --", monto: 150000 };
    const res = await send('POST', '/gastos', tokenAdmin, p);
    recordFinding('SQLI_PROBE', "SQLi en beneficiario (' DROP TABLE)", '/gastos', p, res.data, res.status);
  }

  // 5.2 SQL Injection con UNION SELECT
  {
    const p = { categoria: 'LEGAL', beneficiario: "' UNION SELECT 1, 'admin', 'hacked' FROM DUAL --", monto: 150000 };
    const res = await send('POST', '/gastos', tokenAdmin, p);
    recordFinding('SQLI_PROBE', 'SQLi UNION SELECT en beneficiario', '/gastos', p, res.data, res.status);
  }

  // 5.3 XSS Payload en nombre de plan
  {
    const p = { codigo: 'XSS_PLAN', nombre: '<script>alert("XSS_SAED")</script>', precioMensual: 50000 };
    const res = await send('POST', '/planes', tokenSuper, p);
    recordFinding('XSS_PROBE', 'XSS payload en nombre de plan', '/planes', p, res.data, res.status);
  }

  // =========================================================================
  // GRUPO 6: PATH VARIABLE TYPE MISMATCH (LETRAS EN LUGAR DE IDS NUMÉRICOS)
  // =========================================================================
  console.log(`\n${CYAN}--- GRUPO 6: Letras en Path Variables Numéricas ({id}) ---${RESET}`);

  // 6.1 GET /gastos/abc (debe ser Long)
  {
    const res = await send('GET', '/gastos/letras_no_id', tokenAdmin);
    recordFinding('PATH_TYPE_MISMATCH', 'GET /gastos/{id} con letras', '/gastos/letras_no_id', null, res.data, res.status);
  }

  // 6.2 GET /propiedades/abc
  {
    const res = await send('GET', '/propiedades/identificador_invalido', tokenAdmin);
    recordFinding('PATH_TYPE_MISMATCH', 'GET /propiedades/{id} con letras', '/propiedades/identificador_invalido', null, res.data, res.status);
  }

  // 6.3 PUT /planes/letras/status
  {
    const res = await send('PATCH', '/planes/codigo_en_vez_de_id/status', tokenSuper, { estado: 'ACTIVO' });
    recordFinding('PATH_TYPE_MISMATCH', 'PATCH /planes/{id}/status con texto', '/planes/codigo_en_vez_de_id/status', null, res.data, res.status);
  }

  // =========================================================================
  // RESUMEN Y BALANCE FINAL
  // =========================================================================
  console.log(`\n${CYAN}======================================================${RESET}`);
  console.log(`${CYAN}               RESUMEN DE AUDITORÍA                   ${RESET}`);
  console.log(`${CYAN}======================================================${RESET}`);

  const total = report.length;
  const critical = report.filter(r => r.severity === 'CRITICAL').length;
  const high = report.filter(r => r.severity === 'HIGH').length;
  const info = report.filter(r => r.severity === 'INFO').length;

  console.log(`Pruebas ejecutadas: ${total}`);
  console.log(`${RED}Hallazgos Críticos (Datos ilegítimos aceptados 200/201): ${critical}${RESET}`);
  console.log(`${YELLOW}Errores de Servidor 500 (Unhandled Exceptions / ORA): ${high}${RESET}`);
  console.log(`${GREEN}Respuestas Correctas (400 Bad Request / 403 Forbidden): ${info}${RESET}\n`);

  if (critical > 0 || high > 0) {
    console.log(`${RED}DETALLE DE FALLAS ENCONTRADAS:${RESET}`);
    report.filter(r => r.severity === 'CRITICAL' || r.severity === 'HIGH').forEach(f => {
      console.log(`- [${f.severity}] [${f.issueType}] ${f.testName} (${f.endpoint}) -> HTTP ${f.status}`);
    });
  }
}

runTests();
