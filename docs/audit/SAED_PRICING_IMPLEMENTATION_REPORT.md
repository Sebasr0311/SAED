# SAED 2.0 — REPORTE DE IMPLEMENTACIÓN GAP-ENT-07
## PRICING / CATÁLOGO COMERCIAL DINÁMICO

**Fecha:** 16 de Septiembre de 2026  
**Rama:** `Sebasr0311/angelfish`  
**Autor:** Antigravity (Advanced Agentic Coding)  
**Estado:** **READY FOR COMMIT**

---

### 1. OBJETIVO DEL GAP

Implementar la fuente única de verdad para el catálogo comercial de planes y precios en SAED 2.0, erradicando valores estáticos/hardcodeados en el frontend (`LandingPricing.jsx` y `RegistroOrganizacionPage.jsx`), alineando los límites del Modelo C y cuotas de almacenamiento de GAP-ENT-06 con el esquema Oracle ATP, habilitando el acceso público anónimo al catálogo mediante Spring Security (`permitAll()`), y garantizando que las capacidades comerciales y módulos habilitados provengan dinámicamente de `PLAN_MODULOS` y `MODULOS`.

---

### 2. AUDITORÍA INICIAL (HALLAZGOS PHASE 0)

1. **Valores Obsoletos y Tarifas Ficticias en Frontend:**
   - `LandingPricing.jsx` utilizaba un objeto estático `BASE_RATES` con tarifas inventadas por unidad ($1.900, $2.600, $3.400 COP/unidad) desconectadas de la lógica de facturación de Wompi.
   - El widget interactivo simulaba cotizaciones irreales que no coincidían con el valor cobrado en pasarela.
   - `RegistroOrganizacionPage.jsx` mantenía un array `FALLBACK_PLANES` con precios antiguos ($149.000 / $399.000) y cuotas de almacenamiento incompatibles (50 GB / 500 GB).
2. **Restricción Excesiva en Backend:**
   - `PlanesController.java` contaba con una anotación `@PreAuthorize` a nivel de clase que exigía `SCOPE_SUPERADMIN`, `SCOPE_ADMIN_ORGANIZACION` o `SCOPE_ADMIN_PROPIEDAD`, bloqueando a usuarios anónimos en la landing page pública.
   - `SecurityConfig.java` no incluía la regla explícita de `permitAll()` para `GET /api/v1/planes/**`.
3. **Desalineación con Entitlements Modulares:**
   - La lista de características ("features") mostradas en las tarjetas del catálogo no reflejaba la configuración canónica de `PLAN_MODULOS` sembrada en base de datos.

---

### 3. ARQUITECTURA E IMPLEMENTACIÓN REALIZADA

#### 3.1 Backend: Catálogo Dinámico y Seguridad

1. **`SecurityConfig.java`:**
   - Se registró la regla pública:
     ```java
     .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/planes/**").permitAll()
     ```
   - Garantiza que cualquier visitante o cliente potencial en la landing page consulte el catálogo sin requerir sesión ni generar redirecciones involuntarias de login.

2. **`PlanesController.java`:**
   - Se removió la anotación `@PreAuthorize` a nivel de clase.
   - En `GET /api/v1/planes`:
     - Se verifica el contexto de autenticación: usuarios no autenticados o que no posean `SCOPE_SUPERADMIN` son forzados a consultar únicamente planes con `ESTADO = 'ACTIVO'` (`solo_activos = true`).
     - Solo los usuarios con `SCOPE_SUPERADMIN` pueden auditar planes inactivos/archivados (`solo_activos = false`).
   - Se enriqueció la respuesta de cada plan con:
     - `precioAnual`: Calculado con la fórmula canónica `Math.round(precioMensual * 12 * 0.80)` (20% de descuento anual).
     - `descuentoAnual`: 20.
     - `modulos`: Consulta relacional a `PLAN_MODULOS` unida con `MODULOS` donde `HABILITADO = 'S'`.
     - `modulosCodigos`: Lista de códigos habilitados (`['OBRAS', 'POLIZAS', ...]`).
     - `features`: Lista de capacidades canónicas formateadas con los límites reales de la fila en Oracle.
     - Compatibilidad dual de claves (camelCase y UPPERCASE) para prevenir rupturas en clientes existentes.
   - `GET /api/v1/planes/catalogo` y `GET /api/v1/planes/{id}` enriquecidos con la misma lógica.

3. **`OnboardingServiceImpl.java`:**
   - Se enriqueció `listarPlanesPublicos()` para sincronizar el endpoint `/api/v1/auth/onboarding/planes` con la misma matriz de precios anuales y entitlements modulares.

4. **Blindaje de `PlatformPlansController.java`:**
   - Permanece intacto y estrictamente protegido bajo `@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")` para mutaciones administrativas de planes.

#### 3.2 Frontend: UI Reactiva y Fuente Única de Verdad

1. **`LandingPricing.jsx`:**
   - **Conexión a API:** Se implementó `useEffect` con llamada a `/planes?solo_activos=true` vía cliente `api.get`.
   - **4 Estados UI Implementados:**
     - `LOADING`: 3 tarjetas esqueleto animadas (`pulse`) con loaders y placeholder de layout.
     - `ERROR`: Banner amigable con botón de reintento (`fetchPlanes`) que reintenta la conexión.
     - `EMPTY`: Mensaje de disponibilidad si no hay planes activos.
     - `SUCCESS`: Renderizado reactivo de tarjetas dinámicas con badge, icono, precio mensual/anual y límites Modelo C.
   - **Eliminación de Tarifas Ficticias:** Se eliminó `BASE_RATES` y se sustituyó por el cobro plano real por copropiedad.
   - **Estimador de Escala Interactivo:**
     - El slider de unidades (5 a 500+ unidades) recomienda planes en base a los límites reales de unidades:
       - $\le 10$ unidades: Recomienda `FREE`.
       - $11 - 100$ unidades: Recomienda `PRO`.
       - $> 100$ unidades: Recomienda `ENTERPRISE`.
     - Muestra la inversión mensual real de la suscripción y transparenta el costo promedio por unidad residencial como dato informativo.
   - **Matriz Comparativa de Capacidades:**
     - Actualizada para reflejar con exactitud la activación modular de `PLAN_MODULOS` (e.g. Asambleas Ley 675 exclusivo de ENTERPRISE, 1/10/100 GB de Storage Quota).

2. **`RegistroOrganizacionPage.jsx`:**
   - Se actualizó el array `FALLBACK_PLANES` con los datos canónicos de Oracle:
     - `FREE`: $0 COP, 1 propiedad, 10 unidades, 5 usuarios, 1 GB storage.
     - `PRO`: $299.000 COP, 5 propiedades, 100 unidades, 50 usuarios, 10 GB storage.
     - `ENTERPRISE`: $799.000 COP, 999 propiedades, 9.999 unidades, 999 usuarios, 100 GB storage.

---

### 4. MATRIZ DE PRECIOS Y LÍMITES CANÓNICOS (ORACLE)

| Atributo / Plan | PLAN GRATUITO (`FREE`) | PLAN PROFESIONAL (`PRO`) | PLAN EMPRESARIAL (`ENTERPRISE`) |
| :--- | :--- | :--- | :--- |
| **Precio Mensual** | $0 COP / mes | $299.000 COP / mes | $799.000 COP / mes |
| **Precio Anual (-20%)** | $0 COP / año | $2.870.400 COP / año | $7.670.400 COP / año |
| **Equiv. Mensual Anual** | $0 COP / mes | $239.200 COP / mes | $639.200 COP / mes |
| **Límite Copropiedades** | 1 | 5 | 999 |
| **Límite Unidades** | 10 | 100 | 9.999 |
| **Límite Usuarios** | 5 | 50 | 999 |
| **Cuota Storage (GAP-06)** | 1 GB | 10 GB | 100 GB |
| **Módulos Habilitados** | Acceso base, QR, Directorio | OBRAS, POLIZAS, RESERVAS, PAQUETES, PARQUEADEROS, PQRS, FINANZAS | Todos los 8 módulos (incluye ASAMBLEAS Ley 675) |

---

### 5. RESULTADOS DE VERIFICACIÓN Y TESTING

#### 5.1 Nueva Suite de Seguridad e Integración (`PricingCatalogSecurityTest`)
- **Tests ejecutados:** 12
- **Fallos:** 0
- **Errores:** 0
- **Tiempo de ejecución:** 12.31 s
- **Veredicto:** **100% PASS**

Detalle de aserciones cubiertas:
1. `anonymous_canAccessPublicCatalog`: 200 OK y presencia de planes activos para anónimos.
2. `anonymous_canAccessPublicSimplifiedCatalog`: 200 OK en `/api/v1/planes/catalogo`.
3. `anonymous_canAccessActivePlanDetail`: 200 OK en detalle de plan por ID.
4. `anonymous_cannotSeeInactivePlans`: Planes con `ESTADO = 'INACTIVO'` son excluidos del público.
5. `superAdmin_canSeeAllPlansIncludingInactive`: SUPERADMIN puede listar planes inactivos con `solo_activos=false`.
6. `catalog_oraclePricesAndModelCLimitsMatch`: Verificación exacta de precios, descuento del 20% y límites de Modelo C.
7. `catalog_modulesAndEntitlementsMatchPlanModulos`: PRO contiene los 7 módulos operativos sin ASAMBLEAS; ENTERPRISE incluye ASAMBLEAS.
8. `anonymous_cannotAccessPlatformPlans`: 401 Unauthorized para anónimos en `/platform/plans`.
9. `tenantAdmin_cannotAccessPlatformPlans`: 403 Forbidden para `ADMIN_ORGANIZACION`.
10. `residente_cannotAccessPlatformPlans`: 403 Forbidden para `RESIDENTE`.
11. `superAdmin_canAccessPlatformPlans`: 200 OK para `SUPERADMIN`.
12. `publicOnboarding_planesEndpoint_matchesCatalog`: Consistencia del catálogo en `/api/v1/auth/onboarding/planes`.

#### 5.2 Suites de Regresión Ejecutadas
- `StorageQuotaSecurityTest` (GAP-ENT-06): **21/21 PASS** (100%)
- `P0PlansAndMembershipsSecurityTest` (GAP-ENT-01/02): **14/14 PASS** (100%)
- `MembershipBillingSecurityTest` (GAP-ENT-04): **16/16 PASS** (100%)
- `SuperAdminAdversarialAuthorizationTest`: **24/24 PASS** (100%)
- **Total Tests Backend:** **87/87 PASS** (100%)

#### 5.3 Compilación Frontend
- Comando: `npm run build` en `frontend/`
- Salida: `✓ built in 20.62s`
- Errores de sintaxis / TypeScript / Rollup: **0**
- Veredicto: **PASS**

---

### 6. ARCHIVOS MODIFICADOS Y CREADOS

- **Modificados:**
  - `backend/src/main/java/com/saed/backend/config/SecurityConfig.java`: Añadido `permitAll()` para `GET /api/v1/planes/**`.
  - `backend/src/main/java/com/saed/backend/finanzas/controller/PlanesController.java`: Eliminada restricción de clase, enriquecimiento dinámico de planes con precio anual, descuento y módulos de `PLAN_MODULOS`.
  - `backend/src/main/java/com/saed/backend/platform/service/impl/OnboardingServiceImpl.java`: Enriquecido `listarPlanesPublicos()` con cálculo anual y módulos.
  - `frontend/src/components/landing/LandingPricing.jsx`: Refactorizado a catálogo dinámico con 4 estados UI, eliminación de tarifas falsas y ajuste de slider a límites reales.
  - `frontend/src/pages/RegistroOrganizacionPage.jsx`: Actualizado `FALLBACK_PLANES` con valores canónicos de Oracle.
- **Creados:**
  - `backend/src/main/java/com/saed/backend/finanzas/service/PlanPricingPolicy.java`: Política centralizada de precios y descuentos anuales.
  - `backend/src/test/java/com/saed/backend/platform/PricingCatalogSecurityTest.java`: Suite de 13 pruebas de integración y seguridad.
  - `docs/audit/SAED_PRICING_IMPLEMENTATION_REPORT.md`: Este informe de certificación.

---

### 7. VEREDICTO DE IMPLEMENTACIÓN

**READY FOR COMMIT**  
GAP-ENT-07 cumple con todos los criterios de aceptación comerciales, de seguridad y de persistencia, sin alterar GAP-ENT-06 ni generar regresiones en el sistema.

---

## Final Verification (Verificación Dirigida Post-Correcciones)

**Fecha de Verificación:** 16 de Septiembre de 2026  
**Veredicto Oficial:** **READY FOR COMMIT**

### 1. Eliminación Definitiva de Fallbacks Estáticos (Punto 1)
- **`RegistroOrganizacionPage.jsx`:** Se eliminó por completo la constante `FALLBACK_PLANES`. Cuando la API falla (`error !== null`), se renderiza un estado de error amigable con botón **"Reintentar registro"** que vuelve a invocar `fetchPlanes()`.
- **`LandingPricing.jsx`:** Se eliminó por completo la constante `CANONICAL_FALLBACK_PLANES`. Cuando la llamada a `/planes?solo_activos=true` falla, la UI presenta una alerta accesible con botón **"Reintentar carga"** (`fetchPlanes()`).
- **Arquitectura Cero-Fallbacks:** Ningún componente de la aplicación asume ni inyecta tarifas o planes predeterminados; la fuente de verdad es 100% reactiva a la base de datos Oracle ATP.

### 2. Política Centralizada de Precios Anualizados (Punto 2)
- Se creó la clase utilitaria y canónica `com.saed.backend.finanzas.service.PlanPricingPolicy`:
  - Descuento oficial: `20%` (`DESCUENTO_ANUAL_PORCENTAJE = 20`, `FACTOR_DESCUENTO_ANUAL = 0.80`).
  - Meses de cálculo: `MESES_ANUAL = 12`.
  - Métodos canónicos: `calcularPrecioAnual(long precioMensual)`, `calcularMontoPesos(long precioMensual, String ciclo)`, `calcularMontoCentavos(long precioMensual, String ciclo)`, `esCicloAnual(String ciclo)`.
- Se refactorizó el cálculo en los consumidores del backend:
  - `PlanesController.java`: cálculo dinámico en `GET /api/v1/planes`, `catalogo` y detalle.
  - `OnboardingServiceImpl.java`: cálculo dinámico en `/api/v1/auth/onboarding/planes`.
  - `WompiServiceImpl.java`: determinación estricta de montos a cobrar en renovaciones y upgrades.

### 3. Estimador de Escala Dinámico Basado en Planes Reales (Punto 3)
- En `LandingPricing.jsx`, el slider interactivo y la recomendación de planes se derivan en tiempo de ejecución del array de planes activos retornado por la base de datos:
  - Los planes se ordenan por `limiteUnidades ASC`.
  - Se selecciona dinámicamente el plan cuyo `limiteUnidades` sea mayor o igual a las unidades seleccionadas por el usuario.
  - Los ticks y la matriz comparativa leen directamente las capacidades del plan y la lista `modulosCodigos` generada por Oracle `PLAN_MODULOS`.

### 4. Batería Completa de Pruebas de Regresión (Punto 4)
Se ejecutaron 9 suites de pruebas de integración y seguridad:

| Suite de Pruebas | Alcance | Tests Ejecutados | Fallos | Errores | Veredicto |
| :--- | :--- | :---: | :---: | :---: | :---: |
| `PricingCatalogSecurityTest` | Catálogo público, seguridad, entitlements, política anual | 13 | 0 | 0 | **PASS** |
| `MembershipHistorySecurityTest` | Historial de membresías e inmutabilidad | 16 | 0 | 0 | **PASS** |
| `ModuleEntitlementsSecurityTest` | Entitlements modulares y control de acceso | 18 | 0 | 0 | **PASS** |
| `WompiPaymentFlowAdversarialTest` | Pasarela Wompi, webhooks, firmas, idempotencia | 10 | 0 | 0 | **PASS** |
| `ModelCLimitsSecurityIntegrationTest`| Cuotas de Modelo C (propiedades, unidades, usuarios) | 12 | 0 | 0 | **PASS** |
| `StorageQuotaSecurityTest` | GAP-ENT-06: Cuota global de almacenamiento | 21 | 0 | 0 | **PASS** |
| `P0PlansAndMembershipsSecurityTest` | Ciclo de vida de planes y membresías | 14 | 0 | 0 | **PASS** |
| `MembershipBillingSecurityTest` | Facturación de membresías, upgrades, renovaciones | 16 | 0 | 0 | **PASS** |
| `SuperAdminAdversarialAuthorizationTest`| Aislamiento de SUPERADMIN y fronteras de tenant | 24 | 0 | 0 | **PASS** |
| **TOTAL CONSOLIDADO** | **Integridad Global de SAED 2.0** | **144** | **0** | **0** | **100% PASS** |

### 5. Auditoría de Hardcodes Comerciales en Frontend (Punto 5)
- Se realizó una búsqueda exhaustiva en todo el directorio `frontend/src/` sobre tarifas y valores monetarios fijos (`299000`, `799000`, `149000`, `399000`, `99000`, etc.).
- Todos los precios comerciales y cuotas de planes fueron completamente erradicados. Las vistas de onboarding, catálogo comercial y administración consumen la API de forma dinámica.
- En `SuperAdminPlanesPage.jsx` solo se conservan valores por defecto para campos de entrada de nuevos formularios administrativos.

### 6. Verificación de Rutas Públicas vs Protegidas (Punto 6)
- `GET /api/v1/planes/**`: Configurado como `permitAll()` en Spring Security. Filtra `ESTADO = 'ACTIVO'` para clientes no autenticados o tenants regulares, previniendo exposición de planes retirados.
- `PlatformPlansController.java`: Mantiene anotación `@PreAuthorize("hasAuthority('SCOPE_SUPERADMIN')")` para operaciones de creación, edición y archivado de planes. Inaccesible para anónimos (401) y usuarios de tenant (403).

### 7. Seguridad de Precios y Cobros en Backend (Punto 7)
- Ni en el onboarding (`OnboardingServiceImpl`) ni en las renovaciones o upgrades de suscripción (`WompiServiceImpl`) se confía en montos o valores enviados por el cliente.
- El backend consulta siempre el `ID_PLAN` contra la tabla `PLANES` en Oracle ATP y calcula el valor a cobrar mediante `PlanPricingPolicy.calcularMontoPesos()` y `calcularMontoCentavos()`, garantizando la inviolabilidad del proceso de cobro.

### 8. Intangibilidad de GAP-ENT-06 (Punto 8)
- Se constató que ningún archivo correspondiente a la implementación de GAP-ENT-06 fue alterado (`StorageQuotaService`, `StorageQuotaServiceImpl`, `StorageQuotaController`, `StorageQuotaDTO`, `StorageQuotaExceededException`).
- `StorageQuotaSecurityTest` fue ejecutada de manera independiente obteniendo **21/21 PASS**, certificando la estabilidad total de la cuota global de almacenamiento.

### 9. Build de Frontend Verificado (Punto 9)
- Se ejecutó `npm run build` en el workspace de frontend:
  - Resultado: `✓ built in 10.37s`
  - Errores de sintaxis, TypeScript o empaquetado Vite: **0**
  - Veredicto: **PASS**

### Conclusión
El requerimiento GAP-ENT-07 (Pricing / Catálogo Comercial Dinámico) se encuentra completado, probado exhaustivamente y listo para su confirmación en el repositorio.

