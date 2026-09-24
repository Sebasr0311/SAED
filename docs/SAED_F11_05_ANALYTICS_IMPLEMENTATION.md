# SAED 2.0 — F11-05 ANALYTICS: DOCUMENTO DE IMPLEMENTACIÓN, RECONCILIACIÓN Y CERTIFICACIÓN TÉCNICA

---

## 1. Resumen Ejecutivo

El módulo **F11-05 Analytics** completa el subsistema analítico de SAED 2.0, proveyendo inteligencia de negocio, consolidación financiera, indicadores de morosidad, efectividad de recaudo, ocupación actual en tiempo real y benchmarking comparativo entre propiedades.

La implementación se realizó de forma estricta sobre la arquitectura nativa del proyecto (Spring Boot 3, Java 17, Oracle ATP/XE, React 18, Tailwind CSS), respetando el aislamiento multi-tenant a través de `SaedContextHolder`, Virtual Private Database (VPD/RLS), y una interfaz responsiva construida con componentes puros de Tailwind CSS (sin dependencias externas de librerías de gráficos).

Se certificaron los **38 casos de prueba de integración** en base de datos real Oracle XE, logrando un 100% de éxito (38/38) y verificando la suite de regresión completa de F11 con 119 tests aprobados y cero fallos.

---

## 2. Arquitectura de F11-05

F11-05 se estructura siguiendo los principios de Clean Architecture y la separación por capas estándar de SAED:

```
+---------------------------------------------------------------------------------+
|                                 FRONTEND                                        |
|  - OrgAnaliticaPage.jsx (ADMIN_ORGANIZACION: KPIs, Benchmark, Tendencia pura)   |
|  - PropertyAnaliticaSection.jsx (ADMIN_PROPIEDAD: Financiero, Operativo, Ocup)  |
|  - DashboardPage.jsx (Integración contextual para el Administrador de Propiedad)|
+----------------------------------------+----------------------------------------+
                                         | REST / JSON (CamelCase)
+----------------------------------------v----------------------------------------+
|                               CONTROLLERS                                       |
|  - OrgDashboardController (/api/v1/org/dashboard/analytics)                    |
|  - DashboardOperativoController (/api/v1/dashboard/propiedad/analitica)         |
|  - PlatformDashboardController (/api/v1/platform/dashboard/analytics)          |
+----------------------------------------+----------------------------------------+
                                         | DTOs Tipados
+----------------------------------------v----------------------------------------+
|                             SERVICE LAYER                                       |
|  - AnalyticsService / AnalyticsServiceImpl                                     |
|    * Resolución determinista de ventanas temporales (6 / 12 meses / año)        |
|    * Cálculos agregados SQL en bloque único                                    |
|    * Normalización FACTURADO = 0 (100.0% efectividad) y clamping al 100%       |
|    * Algoritmos de ranking independiente con ordenamiento determinista          |
+----------------------------------------+----------------------------------------+
                                         | NamedParameterJdbcTemplate
+----------------------------------------v----------------------------------------+
|                           ORACLE XE / ATP RLS                                  |
|  - CUOTAS, PAGOS, GASTOS, PROPIEDADES, UNIDADES, RESIDENTES_UNIDAD, VISITAS,    |
|    PAQUETES, PQRS_TICKETS, ORGANIZACIONES                                       |
+---------------------------------------------------------------------------------+
```

---

## 3. Contratos de API Oficiales

### 3.1. Consolidado Organizacional (ADMIN_ORGANIZACION / SUPERADMIN)
- **Endpoint**: `GET /api/v1/org/dashboard/analytics`
- **Parámetros**:
  - `meses` (Integer, opcional, por defecto 12. Válidos: 6, 12)
  - `anio` (Integer, opcional, si se envía toma todo el año calendario 2000-2100 y prevalece sobre `meses`)
- **Cabeceras**: `Authorization: Bearer <jwt>`, `X-Assignment-Id: <id>`
- **Autorización**: `SCOPE_ADMIN_ORGANIZACION`, `SCOPE_SUPERADMIN`
- **Payload de Respuesta**: `ApiResponse<OrgAnalyticsDTO>`
  - `horizonteTemporal`: e.g. `"2025-10 a 2026-09"`
  - `mesesEvaluados`: 12
  - `kpisGlobales`: `OrgAnalyticsKpisDTO`
  - `benchmarkPropiedades`: `List<PropertyBenchmarkDTO>`
  - `tendenciaMensual`: `List<MonthlyTrendDTO>`

### 3.2. Analítica de Propiedad (ADMIN_PROPIEDAD) — Endpoint Canónico Reconciliado
- **Endpoint**: `GET /api/v1/dashboard/propiedad/analitica`
- **Parámetros**: `meses` (Integer, opcional, por defecto 12. Válidos: 6, 12)
- **Cabeceras**: `Authorization: Bearer <jwt>`, `X-Assignment-Id: <id>`
- **Autorización**: `SCOPE_ADMIN_PROPIEDAD`
- **Seguridad**: El ID de propiedad se extrae **únicamente** de `SaedContextHolder` (Anti-IDOR).
- **Payload de Respuesta**: `ApiResponse<PropertyAnalyticsDTO>`
  - `idPropiedad`: Long
  - `horizonteTemporal`: String
  - `ocupacionActualPct`: Double
  - `tendenciaFinanciera`: `List<PropertyFinancialTrendDTO>` (periodo, facturado, recaudado, gastos, balanceNeto)
  - `tendenciaOperativa`: `List<PropertyOperationalTrendDTO>` (periodo, totalVisitas, totalPaquetes, pqrsRadicadas, pqrsResueltasEnSlaPct)

### 3.3. Analítica Global de Plataforma (SUPERADMIN)
- **Endpoint**: `GET /api/v1/platform/dashboard/analytics`
- **Parámetros**: `meses` (Integer, opcional, por defecto 12)
- **Cabeceras**: `Authorization: Bearer <jwt>`, `X-Assignment-Id: <id>`
- **Autorización**: `SCOPE_SUPERADMIN`
- **Payload de Respuesta**: `ApiResponse<PlatformAnalyticsDTO>`
  - `tasaRetencionOrganizacionesPct`: Double
  - `distribucionPropiedadesPorCiudad`: `List<Map<String, Object>>`
  - `crecimientoOrganizacionesMensual`: `List<Map<String, Object>>` (últimos 12 meses)

---

## 4. Modelo Semántico Temporal (6 y 12 meses)

- **Definición de Ventana**: La ventana de `meses=N` incluye siempre el mes actual calendario más los `N-1` meses anteriores.
  - Para `meses=6` evaluado en `2026-09`, la ventana es `2026-04` a `2026-09` (6 meses continuos).
  - Para `meses=12` evaluado en `2026-09`, la ventana es `2025-10` a `2026-09` (12 meses continuos).
- **Tratamiento del parámetro `anio`**: Si se especifica `anio` (e.g. `anio=2026`), prevalece sobre `meses` y fija la ventana del `YYYY-01` al `YYYY-12`.
- **Relleno de Meses sin Movimiento**: Si un mes dentro del horizonte no registra transacciones, el backend genera el periodo con valores numéricos en cero (`0.00`), garantizando series temporales completas y sin huecos en el frontend.

---

## 5. Definiciones Matemáticas y Fórmulas Reconciliadas

### 5.1. FACTURADO DEL PERIODO
`facturadoPeriodo = SUM(CUOTAS.VALOR_BASE)`
- **Filtro**: `CUOTAS.PERIODO` dentro del horizonte evaluado (e.g. `['2025-10', ..., '2026-09']`).
- **Estados incluidos**: `PENDIENTE`, `PAGADA_PARCIAL`, `PAGADA`, `VENCIDA`.
- **Estados excluidos**: `ANULADA`.
- **Restricción de esquema**: En Oracle XE no existe el estado `EN_MORA` (restringido por check constraint `CK_CUOTAS_ESTADO`).

### 5.2. RECAUDADO DEL PERIODO
`recaudadoPeriodo = SUM(PAGOS.MONTO_TOTAL)`
- **Filtro**: `ESTADO = 'APROBADO'` y `FECHA_PAGO` dentro de la ventana (`fechaInicio` a `fechaFin`).
- **Estados excluidos**: `RECHAZADO`, `PENDIENTE`, `ANULADO`.

### 5.3. CARTERA DEL PERIODO
`carteraPeriodo = SUM(CUOTAS.SALDO_PENDIENTE)`
- **Filtro**: `CUOTAS.PERIODO` dentro del horizonte evaluado.
- **Estados incluidos**: `PENDIENTE`, `PAGADA_PARCIAL`, `VENCIDA`.
- **Estados excluidos**: `PAGADA`, `ANULADA`.

### 5.4. CARTERA TOTAL VIVA ACTUAL
`carteraTotalActual = SUM(CUOTAS.SALDO_PENDIENTE)`
- **Filtro**: Sin límite temporal inferior (todo saldo pendiente vivo en el sistema a la fecha actual).
- **Estados incluidos**: `PENDIENTE`, `PAGADA_PARCIAL`, `VENCIDA`.
- **Estados excluidos**: `PAGADA`, `ANULADA`.

### 5.5. EFECTIVIDAD DE RECAUDO
- **Si Facturado == 0**:
  `efectividadRecaudoPct = 100.0%`
  *(Aplica tanto si Recaudado == 0 como si Recaudado > 0. En caso de existir recaudo con facturación cero, el monto real en `recaudadoPeriodo` se preserva intacto sin alteración por el cálculo del porcentaje).*
- **Si Facturado > 0**:
  `efectividadRecaudoPct = min(100.0, (recaudadoPeriodo / facturadoPeriodo) * 100)`
  *(El monto monetario de recaudo nunca se modifica por el clamping).*

### 5.6. ÍNDICE DE MOROSIDAD
- **Si Facturado == 0**:
  `indiceMorosidadPct = 0.0%`
- **Si Facturado > 0**:
  `indiceMorosidadPct = min(100.0, (carteraPeriodo / facturadoPeriodo) * 100)`
- **Aclaración contable**: No se debe asumir que `efectividad + morosidad = 100%`, debido a que el recaudo mide flujos de caja efectivos de pagos aprobados en el periodo mientras que la morosidad mide el saldo insoluto de las cuotas causadas en dicho horizonte.

### 5.7. OCUPACIÓN ACTUAL (CURRENT_STATE_ONLY)
`ocupadas` = Unidades no eliminadas (`ESTADO != 'ELIMINADA'`) que poseen al menos un residente activo (`RESIDENTES_UNIDAD.ESTADO IN ('ACTIVO', 'ACTIVA')`).
`ocupacionActualPct = (ocupadas / totalUnidades) * 100`
- **Si Total Unidades == 0**: `ocupacionActualPct = 0.0%`.

### 5.8. BALANCE NETO MENSUAL
`balanceNeto = recaudado - gastos`
- `recaudado`: `SUM(PAGOS.MONTO_TOTAL)` de pagos aprobados en el mes.
- `gastos`: `SUM(GASTOS.MONTO)` de gastos del mes.

### 5.9. CUMPLIMIENTO DE SLA EN PQRS
`pqrsResueltasEnSlaPct = (ticketsResueltosEnSla / totalRadicadas) * 100`
- **Numerador**: Tickets radicados en el periodo con `FECHA_CIERRE IS NOT NULL` AND `FECHA_CIERRE <= FECHA_LIMITE_SLA`.
- **Denominador**: `TOTAL_RADICADAS` en el periodo.
- **Tickets abiertos**: Tickets con `FECHA_CIERRE IS NULL` computan en el denominador, pero NO suman en el numerador.
- **Si Total Radicadas == 0**: `pqrsResueltasEnSlaPct = 100.0%` (estado neutral).

---

## 6. Matriz de Roles y Alcance Multi-Tenant

| Rol | Alcance Permitido | Parámetros Permitidos | Prohibiciones / Controles |
|---|---|---|---|
| **SUPERADMIN** | Global / Multi-Organización | `meses`, `anio` | No puede alterar registros de auditoría ni operar cuentas residenciales. Acceso analítico global. |
| **ADMIN_ORGANIZACION** | Todas las propiedades de su organización | `meses`, `anio` | Recibe 403 si intenta acceder a datos de otra organización. OrgId derivado del token. |
| **ADMIN_PROPIEDAD** | Únicamente su propiedad asignada | `meses` | El `propertyId` se toma del token/contexto (`SaedContextHolder`). No se permite parámetro URL (Anti-IDOR). |
| **PORTERO** | Ninguno | N/A | Retorna 403 Forbidden. |
| **RESIDENTE** | Ninguno | N/A | Retorna 403 Forbidden. |
| **ANÓNIMO** | Ninguno | N/A | Retorna 401 Unauthorized. |

---

## 7. Reconciliación de Contratos y Decisiones Técnicas

### 7.1. Reconciliación de Endpoint de Analítica de Propiedad
- **Contrato Oficial Único**: `GET /api/v1/dashboard/propiedad/analitica`
- **Decisión Arquitectónica**: `DashboardOperativoController` está anotado a nivel de clase con `@RequestMapping("/api/v1/dashboard")` y el método con `@GetMapping("/propiedad/analitica")`. El frontend `PropertyAnaliticaSection.jsx` consume exactamente este path (`tenantApi.get('/dashboard/propiedad/analitica?meses=' + meses)`). Se corrigió la errata en la documentación previa para reflejar este endpoint único canónico.

### 7.2. Reconciliación de Semántica FACTURADO = 0
- **Regla Oficial Aprobada**:
  - `FACTURADO = 0` y `RECAUDADO = 0` $\rightarrow$ `efectividadRecaudoPct = 100.0%`, `morosidad = 0.0%`.
  - `FACTURADO = 0` y `RECAUDADO > 0` $\rightarrow$ `efectividadRecaudoPct = 100.0%`, `recaudadoPeriodo` conserva su valor monetario real.
- **Implementación**: `AnalyticsServiceImpl` evalúa `facturadoPeriodo.compareTo(BigDecimal.ZERO) == 0 ? 100.0 : ...`, garantizando consistencia matemática y evitando penalizar edificios sin cuotas generadas en el periodo.

### 7.3. Reconciliación de Criterios de Ordenamiento en Rankings
Se implementó el ordenamiento determinista multicriterio estricto aprobado por el Scope Hardening:
- **`rankingEfectividad`**:
  1. `efectividadRecaudoPct DESC`
  2. `recaudoPeriodo DESC`
  3. `nombre ASC`
  4. `idPropiedad ASC` (desempate técnico final)
- **`rankingMorosidad`**:
  1. `indiceMorosidadPct ASC`
  2. `carteraPeriodo ASC`
  3. `nombre ASC`
  4. `idPropiedad ASC` (desempate técnico final)
- **`rankingOcupacion`**:
  1. `ocupacionActualPct DESC`
  2. `totalUnidades DESC`
  3. `nombre ASC`
  4. `idPropiedad ASC` (desempate técnico final)

### 7.4. Reconciliación de Estados de CUOTAS en Oracle XE
- Restricción real en Oracle XE: `CK_CUOTAS_ESTADO` admite exclusivamente: `('PENDIENTE', 'PAGADA_PARCIAL', 'PAGADA', 'VENCIDA', 'ANULADA')`.
- El estado `EN_MORA` no existe en la base de datos y fue erradicado de todas las consultas SQL de analítica.
- `FACTURADO` incluye: `('PENDIENTE', 'PAGADA_PARCIAL', 'PAGADA', 'VENCIDA')`.
- `CARTERA` incluye: `('PENDIENTE', 'PAGADA_PARCIAL', 'VENCIDA')` tomando como métrica `SALDO_PENDIENTE`.

---

## 8. Verificación de Índices en Oracle XE

### 8.1. A. Índices Comprobados Existentes en Oracle XE
A través de consulta directa al catálogo de metadatos del esquema activo (`USER_IND_COLUMNS` y `USER_IND_EXPRESSIONS`), se comprobó la existencia física de los siguientes índices en la base de datos viva:

| Tabla | Índice | Columnas / Expresión | Tipo |
|---|---|---|---|
| **CUOTAS** | `PK_CUOTAS` | `ID_CUOTA` | B-Tree (PK) |
| **CUOTAS** | `UQ_CUOTA_UNICA` | `ID_UNIDAD, ID_CONCEPTO, PERIODO` | B-Tree (Unique) |
| **CUOTAS** | `IX_CUOTAS_PERIODO` | `PERIODO` | B-Tree |
| **CUOTAS** | `IX_CUOTAS_UNIDAD` | `ID_UNIDAD, ESTADO` | B-Tree |
| **CUOTAS** | `IX_CUOTAS_VENCIMIENTO` | `FECHA_VENCIMIENTO, ESTADO` | B-Tree |
| **PAGOS** | `PK_PAGOS` | `ID_PAGO` | B-Tree (PK) |
| **PAGOS** | `IX_PAGOS_UNIDAD` | `ID_UNIDAD` | B-Tree |
| **PAGOS** | `IX_PAGOS_ESTADO` | `ESTADO` | B-Tree |
| **PAGOS** | `IX_PAGOS_FECHA` | `SYS_EXTRACT_UTC("FECHA_PAGO")` | Function-Based |
| **GASTOS** | `PK_GASTOS` | `ID_GASTO` | B-Tree (PK) |
| **GASTOS** | `IX_GASTOS_PROP` | `ID_PROPIEDAD` | B-Tree |
| **GASTOS** | `IX_GASTOS_FECHA` | `FECHA_GASTO` | B-Tree |
| **UNIDADES** | `PK_UNIDADES` | `ID_UNIDAD` | B-Tree (PK) |
| **UNIDADES** | `IX_UNIDADES_PROPIEDAD`| `ID_PROPIEDAD` | B-Tree |
| **UNIDADES** | `IX_UNIDADES_ESTADO` | `ESTADO` | B-Tree |
| **RESIDENTES_UNIDAD** | `PK_RESIDENTES_UNIDAD` | `ID_RESIDENTE_UNIDAD` | B-Tree (PK) |
| **RESIDENTES_UNIDAD** | `IX_RESIDUNIDAD_UNIDAD` | `ID_UNIDAD` | B-Tree |
| **RESIDENTES_UNIDAD** | `UQ_RESIDUNIDAD_ACTIVA` | `ID_UNIDAD, ID_PERSONA, ESTADO` | B-Tree (Unique) |
| **VISITAS** | `PK_VISITAS` | `ID_VISITA` | B-Tree (PK) |
| **VISITAS** | `IX_VISITAS_UNIDAD` | `ID_UNIDAD` | B-Tree |
| **VISITAS** | `IX_VISITAS_ESTADO` | `ESTADO` | B-Tree |
| **PAQUETES** | `PK_PAQUETES` | `ID_PAQUETE` | B-Tree (PK) |
| **PAQUETES** | `IX_PAQUETES_PROP` | `ID_PROPIEDAD, SYS_EXTRACT_UTC("FECHA_RECEPCION")` | Function-Based |
| **PQRS_TICKETS** | `PK_PQRS_TICKETS` | `ID_TICKET` | B-Tree (PK) |
| **PQRS_TICKETS** | `IX_PQRS_PROP` | `ID_PROPIEDAD, ESTADO` | B-Tree |
| **PQRS_TICKETS** | `IX_PQRS_SLA` | `SYS_EXTRACT_UTC("FECHA_LIMITE_SLA"), ESTADO` | Function-Based |
| **PROPIEDADES** | `PK_PROPIEDADES` | `ID_PROPIEDAD` | B-Tree (PK) |
| **PROPIEDADES** | `IX_PROPIEDADES_ORG` | `ID_ORGANIZACION` | B-Tree |
| **ORGANIZACIONES** | `PK_ORGANIZACIONES`| `ID_ORGANIZACION` | B-Tree (PK) |
| **ORGANIZACIONES** | `IX_ORGANIZACIONES_ESTADO` | `ESTADO` | B-Tree |

### 8.2. B. Uso Esperado y Compatibilidad de Predicados
Las consultas SQL implementadas en `AnalyticsServiceImpl` fueron diseñadas para alinearse sintácticamente con los índices comprobados:
- Filtros por horizonte temporal `c.PERIODO IN (:periodos)` son compatibles con `IX_CUOTAS_PERIODO`.
- Filtros de estado `c.ESTADO IN (...)` y joins por unidad aprovechan `IX_CUOTAS_UNIDAD`.
- Filtros de rango de fechas de pagos `p.FECHA_PAGO >= :inicio AND p.FECHA_PAGO <= :fin` coinciden con la expresión de `IX_PAGOS_FECHA`.
- Cruces por propiedad en `UNIDADES`, `GASTOS`, `PAQUETES` y `PQRS_TICKETS` son compatibles con sus respectivos índices `IX_*_PROP` o `IX_*_PROPIEDAD`.
- La verificación de ocupación actual mediante `RESIDENTES_UNIDAD` concuerda con `IX_RESIDUNIDAD_UNIDAD` y `UQ_RESIDUNIDAD_ACTIVA`.

### 8.3. C. Delimitación Metodológica y Evidencia de Runtime
Se establece con rigor técnico que la evidencia recolectada corresponde a la **existencia física comprobada** de los índices en el catálogo de Oracle y a la **compatibilidad de diseño** de las consultas. **No se afirma** que el optimizador de costos (CBO) de Oracle seleccione imperativamente un índice específico en todos los escenarios de runtime, ya que la generación de planes de ejecución (`EXPLAIN PLAN` / `DBMS_XPLAN`) depende del volumen de filas en tablas vivas, la cardinalidad de los filtros y las estadísticas del optimizador, las cuales no fueron objeto de benchmarking sintético adicional.

---

## 9. Criterios de Rendimiento y Arquitectura de Consultas

1. **Consultas Agregadas en Lote (Batch SQL)**:
   Se evitan patrones N+1 ejecutando consultas consolidadas con cláusulas `IN (:propIds)` y agrupaciones `GROUP BY u.ID_PROPIEDAD, c.PERIODO`. Los resultados se indexan en Java Streams en memoria.
2. **Volumen de Transferencia Eficiente**:
   Las respuestas JSON retornan estructuras compactas y agregadas, adecuadas para clientes web y móviles sin sobrecarga de datos crudos.
3. **Aislamiento Multi-Tenant con VPD / RLS**:
   Las consultas operan bajo el contexto establecido por `PKG_SAED_SESSION`, asegurando que las políticas de seguridad de Oracle filtren automáticamente filas a nivel de base de datos sin comprometer el plan de ejecución.

---

## 10. Frontend y Experiencia de Usuario (Tailwind CSS Puro)

- **`OrgAnaliticaPage.jsx`**: Dashboard analítico consolidado para `ADMIN_ORGANIZACION` con selector dinámico 6/12 meses (por defecto 12 meses), KPI Cards de recaudo y morosidad, comparador de tendencias con gráficos de barras CSS nativos y tabla de benchmark con insignias de ranking independientes.
- **`PropertyAnaliticaSection.jsx`**: Sección analítica embebida en `DashboardPage.jsx` para `ADMIN_PROPIEDAD` (por defecto 12 meses), con desglose de tendencias financieras y operativas (visitas, paquetes, PQRS y cumplimiento SLA).
- **Cero Nuevas Dependencias**: No se instalaron paquetes externos de visualización; la UI utiliza exclusivamente Tailwind CSS y componentes de diseño atómico existentes.

---

## 11. Resultados de la Suite de Pruebas Automatizadas

### 11.1. Suite F11-05 Analytics (`F11_05_AnalyticsIntegrationTest`)
- **Total Tests Ejecutados**: 38
- **Fallos**: 0
- **Errores**: 0
- **Skipped**: 0
- **Resultado**: **BUILD SUCCESS**
- **Casos Destacados**:
  - `test01_adminOrganizacionRecibeAnalyticsDeSuOrganizacion`: Valida llamada sin parámetro `meses`, verificando `mesesEvaluados == 12` y exactamente 12 puntos en `tendenciaMensual`.
  - `test04_adminPropiedadSoloVeSuPropiedad`: Valida llamada sin parámetro `meses`, verificando exactamente 12 puntos en `tendenciaFinanciera` y 12 puntos en `tendenciaOperativa`.
  - `test20_facturadoCeroEfectividad100`: Valida que `facturado = 0` y `recaudo = 0` produce `efectividad = 100.0%`.
  - `test20b_facturadoCeroRecaudoPositivoConservaMontoYEfectividad100`: Valida que `facturado = 0` y `recaudo = 500,000` preserva el monto recaudado real y produce `efectividad = 100.0%`.
  - `test21_facturadoCeroMorosidadCero`: Valida que `facturado = 0` produce `morosidad = 0.0%`.
  - `test22_recaudadoMayorFacturadoClamp100ConservaMontoReal`: Valida clamping a 100.0% preservando monto recaudado.
  - `test25b_rankingsOrdenMulticriterioYDesempateNombre`: Valida orden multicriterio estricto y desempates.
  - `test32b_slaCalculaTicketFueraDeSla`: Valida cálculo exacto de SLA con tickets cerrados fuera de tiempo.

### 11.2. Suite de Regresión Completa F11
Ejecutada con Maven 3.9.9 y JDK 17 sobre Oracle XE 21c (`localhost:1521/XEPDB1`):

| suite | total | passed | failed | errors | skipped |
|---|---|---|---|---|---|
| `F11_04_ExportacionesIntegrationTest` | 17 | 17 | 0 | 0 | 0 |
| `F11_04_IngresosPresupuestalesIntegrationTest` | 16 | 16 | 0 | 0 | 0 |
| `F11_04_ReportesBaseMotorIntegrationTest` | 20 | 20 | 0 | 0 | 0 |
| `F11_04_ReportesConfiguradosHistorialIntegrationTest` | 28 | 28 | 0 | 0 | 0 |
| `F11_05_AnalyticsIntegrationTest` | 38 | 38 | 0 | 0 | 0 |
| **TOTAL REGRESIÓN F11** | **119** | **119** | **0** | **0** | **0** |

**Resultado Global**: **BUILD SUCCESS** (Tiempo de ejecución total: 34.67s).

### 11.3. Compilación de Frontend
- **Comando**: `npm run build` en `frontend/`
- **Resultado**: **PASS** (`built in 10.92s`, 0 errores de compilación, 0 advertencias críticas, 0 imports rotos).

---

## 12. Veredicto Final

$$\mathbf{F11\text{-}05\ —\ FUNCTIONALLY\ AND\ TECHNICALLY\ COMPLETE\ FOR\ THE\ CURRENT\ SAED\ 2.0\ BASELINE}$$

El módulo F11-05 Analytics se encuentra plenamente reconciliado, verificado contra la base de datos Oracle XE en vivo, con cero discrepancias semánticas, contratos consistentes y cobertura automatizada integral.
