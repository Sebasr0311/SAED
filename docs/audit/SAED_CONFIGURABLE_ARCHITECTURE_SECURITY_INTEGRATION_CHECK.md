# SAED 2.0 — VERIFICACIÓN DIRIGIDA DE SEGURIDAD E INTEGRACIÓN
## ARQUITECTURA CONFIGURABLE DE PROPIEDADES (GAP-CFG-01 A GAP-CFG-07)

**Fecha:** 2026-09-16  
**Repositorio:** `https://github.com/Sebasr0311/SAED`  
**Rama:** `Sebasr0311/angelfish`  
**Base Commit:** `d8c8eb895dbd315ed0367243711ea754c09580c0`  
**Estado:** `READY FOR COMMIT`  

---

## 1. RESUMEN EJECUTIVO

Se ejecutó la verificación técnica, de seguridad y de integración del bloque **Arquitectura Configurable de Propiedades** implementado en SAED 2.0. El análisis cubrió exhaustivamente la capa de bloques y jerarquías (`BLOQUES`), la configuración operativa por propiedad (`PROPIEDAD_CONFIGURACION`), la persistencia y validación de tipos y estructuras en unidades (`UNIDADES`), el aislamiento estricto multi-tenant y la matriz de autorización para los cinco roles del sistema.

### Veredicto Global
| Componente | Estado | Detalle |
|---|:---:|---|
| **Estructura y Jerarquía de Bloques** | **PASS** | Validaciones cíclicas, profundidad máxima (5), autoreferencias y guardas de integridad referencial verificadas. |
| **Configuración por Propiedad** | **PASS** | Whitelist estricta, validaciones de tipo/rango, fallback UPDATE+INSERT inmune a `ORA-28132`. |
| **Persistencia Unidades** | **PASS** | `ID_BLOQUE` e `ID_TIPO_UNIDAD` persistidos; rechazo de bloques ajenos y tipos inválidos. |
| **Aislamiento Multi-Tenant** | **PASS** | Blindaje contra bypass de `propertyId` nulo y validación estricta de propiedad/organización. |
| **Matriz de Autorización** | **PASS** | Control granular `@PreAuthorize` alineado a `SCOPE_*` y roles en backend. |
| **Frontend ↔ Backend Contract** | **PASS** | Cero endpoints huérfanos o legacy; payload 'NONE' sanitizado a `null`. |
| **Suite de Pruebas Backend** | **PASS** | 32/32 tests unitarios e integrados aprobados (`BUILD SUCCESS`). |
| **Build Frontend** | **PASS** | `vite build` completado sin errores de compilación (`built in 29.19s`). |

**Recomendación:** `READY FOR COMMIT`

---

## 2. REVISIÓN DE BLOQUES Y JERARQUÍAS (GAP-CFG-01 / GAP-CFG-02)

### 2.1 Componentes Inspeccionados
- `BlockController.java`: Mapeo base en `/api/v1/properties/{propertyId}/blocks`.
- `BlockService.java` / `BlockServiceImpl.java`: Lógica de negocio y reglas de consistencia de árbol.
- `BlockRepository.java` / `BlockRepositoryImpl.java`: Acceso a datos y consultas recursivas.
- `BlockDTO.java`, `BlockRequestDTO.java`, `BlockTreeDTO.java`: Contratos de transferencia.

### 2.2 Verificación de Reglas y Restricciones
1. **Validación de Propiedad (`validatePropertyAccess`):**
   - Garantiza que el `propertyId` de la ruta coincida con el contexto del token (`SaedContextHolder`).
   - Bloquea cross-property spoofing con `AccessDeniedException` (HTTP 403).
2. **Restricciones Jerárquicas:**
   - **Autoreferencia:** Bloquea explícitamente `id.equals(padreId)` lanzando `IllegalArgumentException` (HTTP 400).
   - **Padre Cross-Property:** Valida que el `idBloquePadre` pertenezca a la misma propiedad (`propertyId.equals(parentBlock.getIdPropiedad())`).
   - **Detección de Ciclos:** Algoritmo de recorrido ascendente que detecta referencias circulares en el árbol (`IllegalArgumentException`).
   - **Profundidad Máxima:** Límite máximo de 5 niveles de profundidad impuesto en inserción/edición.
3. **Guardas de Integridad en Eliminación (`delete`):**
   - Valida si existen sub-bloques hijos (`countChildren(id) > 0`): lanza `IllegalStateException` (HTTP 409 Conflict).
   - Valida si existen unidades asociadas (`countUnits(id) > 0`): lanza `IllegalStateException` (HTTP 409 Conflict).
   - Impide orfandad de registros y mantiene integridad del modelo.

---

## 3. CONFIGURACIÓN OPERATIVA POR PROPIEDAD (GAP-CFG-04)

### 3.1 Componentes Inspeccionados
- `PropertyConfigController.java`: Mapeo base en `/api/v1/properties/{propertyId}/config`.
- `PropertyConfigService.java` / `PropertyConfigServiceImpl.java`: Gestión de whitelist y valores por defecto.
- `PropertyConfigRepositoryImpl.java`: Persistencia con patrón seguro UPDATE + INSERT.
- `ConvivienteQuotaServiceImpl.java`: Integración dinámica de límite de convivientes.

### 3.2 Seguridad y Resiliencia en Persistencia
1. **Whitelist y Valores por Defecto:**
   - `LIMITE_CONVIVIENTES_POR_UNIDAD`: Default `4` (Rango: 1 - 50).
   - `TOLERANCIA_MORA_DIAS`: Default `30` (Rango: 0 - 365).
   - `HABILITA_QR`: Default `true` (Booleano: `true`/`false`).
   - `HABILITA_LPR`: Default `false` (Booleano: `true`/`false`).
   - `PERMITE_MASCOTAS`: Default `true` (Booleano: `true`/`false`).
   - `VALOR_EXPENSA_DEFECTO`: Default `0` (Numérico >= 0).
   - `FORMATO_NOTIFICACION`: Default `EMAIL` (Enum: `EMAIL`, `SMS`, `INTERNO`).
   - Claves desconocidas son rechazadas con `IllegalArgumentException` (HTTP 400).
2. **Inmunidad a Oracle VPD (`ORA-28132`):**
   - El uso de `MERGE INTO` en tablas con políticas RLS activas genera `ORA-28132: Merge into syntax does not support security policies`.
   - `PropertyConfigRepositoryImpl.saveOrUpdate()` ejecuta primero un `UPDATE`. Si el número de filas afectadas es `0`, procede con un `INSERT`. Esto garantiza compatibilidad 100% con Oracle VPD.
3. **Consumo Dinámico:**
   - `ConvivienteQuotaServiceImpl.getLimitForProperty()` consulta directamente la configuración de la propiedad en lugar de un valor hardcodeado, utilizando `4` como fallback seguro.

---

## 4. PERSISTENCIA EN UNIDADES Y CLAVES FORÁNEAS (GAP-CFG-03)

### 4.1 Componentes Inspeccionados
- `UnitRepositoryImpl.java`: Método `update` y `create`.
- `UnitService.java`: Método `validateBlockAndType`.

### 4.2 Verificación de Consistencia
1. **Persistencia de `ID_BLOQUE` e `ID_TIPO_UNIDAD`:**
   - La sentencia `UPDATE UNIDADES` incluye explícitamente `id_bloque = :idBloque, id_tipo_unidad = :idTipoUnidad`.
2. **Validación de Bloque Ajeno:**
   - `validateBlockAndType(propertyId, idBloque, idTipoUnidad)` verifica que el bloque pertenezca a `propertyId`. Si pertenece a otra propiedad, arroja `IllegalArgumentException`.
3. **Validación de Tipo de Unidad:**
   - Verifica existencia activa en `TIPOS_UNIDAD`.
4. **Compatibilidad Hacia Atrás:**
   - `idBloque` es opcional (`nullable`). Unidades creadas previamente sin bloque asignado se leen y actualizan de forma transparente.

---

## 5. AISLAMIENTO MULTI-TENANT Y ANTI-SPOOFING

### 5.1 Endurecimiento Aplicado Durante la Verificación
Durante la auditoría de `validatePropertyAccess` en `BlockServiceImpl` y `PropertyConfigServiceImpl`, se detectó que si un token no-global carecía de `propertyId` o `organizationId`, la condición `!= null` omitía la verificación. Se aplicó una corrección quirúrgica:
- Si el alcance es `ORGANIZACION`, se requiere obligatoriamente `userOrgId != null` y coincidencia estricta con el `id_organizacion` de la propiedad.
- Para alcances operativos de propiedad (`ADMIN_PROPIEDAD`, `PORTERO`, `RESIDENTE`), se exige obligatoriamente `ctx.getPropertyId() != null` y coincidencia exacta con `propertyId`. De lo contrario, se arroja `AccessDeniedException`.

---

## 6. MATRIZ DE AUTORIZACIÓN POR ROL

| Endpoint / Operación | SUPERADMIN | ADMIN_ORGANIZACION | ADMIN_PROPIEDAD | PORTERO | RESIDENTE |
|---|:---:|:---:|:---:|:---:|:---:|
| `GET /properties/{id}/blocks` | ✅ Permitido | ✅ Permitido (su org) | ✅ Permitido (su prop) | ✅ Permitido (su prop) | ✅ Permitido (su prop) |
| `GET /properties/{id}/blocks/tree` | ✅ Permitido | ✅ Permitido (su org) | ✅ Permitido (su prop) | ✅ Permitido (su prop) | ✅ Permitido (su prop) |
| `POST /properties/{id}/blocks` | ✅ Permitido | ✅ Permitido (su org) | ✅ Permitido (su prop) | ❌ 403 Denegado | ❌ 403 Denegado |
| `PUT /properties/{id}/blocks/{bId}` | ✅ Permitido | ✅ Permitido (su org) | ✅ Permitido (su prop) | ❌ 403 Denegado | ❌ 403 Denegado |
| `DELETE /properties/{id}/blocks/{bId}` | ✅ Permitido | ✅ Permitido (su org) | ✅ Permitido (su prop) | ❌ 403 Denegado | ❌ 403 Denegado |
| `GET /properties/{id}/config` | ✅ Permitido | ✅ Permitido (su org) | ✅ Permitido (su prop) | ✅ Permitido (su prop) | ✅ Permitido (su prop) |
| `PUT /properties/{id}/config` | ✅ Permitido | ✅ Permitido (su org) | ✅ Permitido (su prop) | ❌ 403 Denegado | ❌ 403 Denegado |
| `POST /units` (con bloque y tipo) | ✅ Permitido | ✅ Permitido (su org) | ✅ Permitido (su prop) | ❌ 403 Denegado | ❌ 403 Denegado |
| `PUT /units/{id}` (actualizar bloque) | ✅ Permitido | ✅ Permitido (su org) | ✅ Permitido (su prop) | ❌ 403 Denegado | ❌ 403 Denegado |

---

## 7. CONTRATOS FRONTEND ↔ BACKEND (GAP-CFG-05 / GAP-CFG-06 / GAP-CFG-07)

### 7.1 Páginas y Modales Auditados
1. `frontend/src/pages/UnidadesPage.jsx`:
   - Pestañas funcionales: "Unidades Habitacionales" y "Estructura de Copropiedad".
   - Botón de "Configuración" abre `PropertyConfigModal`.
   - Selector de Bloque/Torre en modal de unidad.
   - Selector de Estructura Padre en modal de bloque.
   - **Mejora aplicada en verificación:** Sanitización del valor `'NONE'`. Al seleccionar "Sin bloque" o "Ninguna (Nivel superior)", el payload envía `null` en vez de evaluar `Number('NONE')` -> `NaN`.
2. `frontend/src/components/PropertyConfigModal.jsx`:
   - Modal interactivo con validaciones por tipo (números positivos, switches booleanos, selector de canal).
   - Consumo de `GET` y `PUT` sobre `/properties/{propertyId}/config`.
3. `frontend/src/pages/OrgPropiedadesPage.jsx` y `PropiedadesPage.jsx`:
   - Consumo correcto de tipos de propiedad del catálogo (`/catalogo/tipos-propiedad`).
   - Acceso contextual a configuración y estructura.

### 7.2 Verificación de Endpoints Huérfanos / Legacy Prohibidos
Búsqueda global mediante ripgrep en todo el repositorio:
- `/api/v1/api/`: **0 ocurrencias** en código fuente activo.
- `/api/v1/unidades`: **0 ocurrencias** en código fuente activo (solo tests y docs de auditoría).
- `/sugerir-tipo`: **0 ocurrencias** en código fuente activo.
- `/renovar`: **0 ocurrencias** en código fuente activo.
- `/reenviar-correo`: **0 ocurrencias** en código fuente activo.

---

## 8. EVIDENCIA DE COMPILACIÓN Y PRUEBAS

### 8.1 Backend Test Execution (`mvn test`)
```text
[INFO] Running com.saed.backend.authorization.service.BlockServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.957 s
[INFO] Running com.saed.backend.authorization.service.PropertyConfigServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.305 s
[INFO] Running com.saed.backend.authorization.service.PropertyServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.278 s
[INFO] Running com.saed.backend.authorization.service.PropertyStatusServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.054 s
[INFO] Running com.saed.backend.authorization.service.UnitServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.120 s
[INFO] 
[INFO] Results:
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time:  43.730 s
```

### 8.2 Frontend Build Execution (`npm run build`)
```text
✓ built in 29.19s
dist/assets/UnidadesPage-Dmgwi98n.js                   19.38 kB │ gzip:   5.37 kB
dist/assets/PropertyConfigModal-DSshkWrP.js             7.99 kB │ gzip:   2.41 kB
dist/assets/OrgPropiedadesPage-_gag_RHX.js             26.71 kB │ gzip:   6.94 kB
dist/assets/PropiedadesPage-ZU8NgADZ.js                15.40 kB │ gzip:   4.27 kB
```
Compilación limpia sin errores de sintaxis ni fallos de bundling.

---

## 9. CLASIFICACIÓN DE HALLAZGOS

| ID | Severidad | Categoría | Descripción | Resolución |
|---|:---:|:---:|---|---|
| **FINDING-01** | **P2** | Seguridad / Multi-Tenant | `validatePropertyAccess` en `BlockServiceImpl` y `PropertyConfigServiceImpl` permitía bypass en caso de `ctx.getPropertyId() == null` para roles de propiedad. | **RESUELTO:** Validación estricta agregada para exigir coincidencia no nula tanto en organización como en propiedad. |
| **FINDING-02** | **P2** | Frontend / UX | En `UnidadesPage.jsx`, seleccionar `<SelectItem value="NONE">` causaba `Number('NONE')` -> `NaN` en el payload JSON. | **RESUELTO:** Sanitización aplicada en `guardarUnidad` y `guardarBloque` (`idBloque && idBloque !== 'NONE' ? Number(...) : null`). |

**Totales:**
- **P0 (Crítico / Bloqueante):** 0
- **P1 (Alto):** 0
- **P2 (Medio / Resuelto durante verificación):** 2
- **P3 (Bajo / Cosmético):** 0

---

## 10. ARCHIVOS MODIFICADOS Y CREADOS

### Archivos Nuevos
1. `backend/src/main/java/com/saed/backend/authorization/controller/BlockController.java`
2. `backend/src/main/java/com/saed/backend/authorization/controller/PropertyConfigController.java`
3. `backend/src/main/java/com/saed/backend/authorization/dto/BlockDTO.java`
4. `backend/src/main/java/com/saed/backend/authorization/dto/BlockRequestDTO.java`
5. `backend/src/main/java/com/saed/backend/authorization/dto/BlockTreeDTO.java`
6. `backend/src/main/java/com/saed/backend/authorization/dto/PropertyConfigDTO.java`
7. `backend/src/main/java/com/saed/backend/authorization/dto/PropertyConfigUpdateDTO.java`
8. `backend/src/main/java/com/saed/backend/authorization/repository/BlockRepository.java`
9. `backend/src/main/java/com/saed/backend/authorization/repository/BlockRepositoryImpl.java`
10. `backend/src/main/java/com/saed/backend/authorization/repository/PropertyConfigRepository.java`
11. `backend/src/main/java/com/saed/backend/authorization/repository/PropertyConfigRepositoryImpl.java`
12. `backend/src/main/java/com/saed/backend/authorization/service/BlockService.java`
13. `backend/src/main/java/com/saed/backend/authorization/service/BlockServiceImpl.java`
14. `backend/src/main/java/com/saed/backend/authorization/service/PropertyConfigService.java`
15. `backend/src/main/java/com/saed/backend/authorization/service/PropertyConfigServiceImpl.java`
16. `backend/src/test/java/com/saed/backend/authorization/service/BlockServiceTest.java`
17. `backend/src/test/java/com/saed/backend/authorization/service/PropertyConfigServiceTest.java`
18. `frontend/src/components/PropertyConfigModal.jsx`
19. `docs/audit/SAED_CONFIGURABLE_ARCHITECTURE_SECURITY_INTEGRATION_CHECK.md`

### Archivos Modificados
1. `backend/src/main/java/com/saed/backend/authorization/repository/UnitRepositoryImpl.java` (Persistencia de `id_bloque` e `id_tipo_unidad`)
2. `backend/src/main/java/com/saed/backend/authorization/service/UnitService.java` (Validación de bloque y tipo en create/update)
3. `backend/src/main/java/com/saed/backend/catalog/controller/CatalogoController.java` (Endpoint `/tipos-propiedad`)
4. `backend/src/main/java/com/saed/backend/config/DatabaseSeeder.java` (Semillas de catálogo)
5. `backend/src/main/java/com/saed/backend/person/service/impl/ConvivienteQuotaServiceImpl.java` (Lectura dinámica de límite)
6. `backend/src/test/java/com/saed/backend/authorization/service/PropertyServiceTest.java`
7. `backend/src/test/java/com/saed/backend/authorization/service/UnitServiceTest.java`
8. `database/seeds/demo/V5.99__demo_seeds.sql`
9. `frontend/src/pages/OrgPropiedadesPage.jsx`
10. `frontend/src/pages/PropiedadesPage.jsx`
11. `frontend/src/pages/UnidadesPage.jsx`

---

## 11. CONCLUSIÓN Y RECOMENDACIÓN FINAL

La verificación dirigida confirma que la implementación de la Arquitectura Configurable de Propiedades cumple con todos los estándares arquitectónicos, de seguridad multi-tenant y de consistencia de contratos API en SAED 2.0.

No existen bloqueantes ni defectos abiertos.

**Veredicto Final:** `READY FOR COMMIT`
