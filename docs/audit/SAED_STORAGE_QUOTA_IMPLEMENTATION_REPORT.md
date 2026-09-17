# SAED 2.0 — REPORTE DE IMPLEMENTACIÓN Y CERTIFICACIÓN
## GAP-ENT-06: CUOTA GLOBAL DE ALMACENAMIENTO POR ORGANIZACIÓN

**Fecha de Certificación:** 2026-09-16  
**Entorno:** SAED 2.0 (`Sebasr0311/SAED`, rama `Sebasr0311/angelfish`)  
**Stack:** Java 17, Spring Boot 3.2.3, Oracle Database XE / ATP (RLS / VPD), React 18 / Vite  
**Veredicto Final:** **LISTO PARA COMMIT (READY FOR COMMIT)**  

---

## 1. RESUMEN EJECUTIVO Y CONTEXTO ARQUITECTÓNICO

El gap **GAP-ENT-06** implementa el control y gobernanza de la cuota global acumulativa de almacenamiento asignada a cada organización en SAED 2.0. Con esta implementación, la plataforma garantiza que:
1. **Límite Multi-Tenant por Plan:** Cada organización tiene un techo de almacenamiento en disco determinado por la columna `LIMITE_ALMACENAMIENTO_GB` de su membresía activa (`PLANES` + `MEMBRESIAS`).
2. **Coexistencia de Límites:** El límite por archivo individual (10 MB = 10,485,760 bytes) se mantiene intacto y opera como primera línea defensiva. La cuota acumulativa global por organización (en GiB binarios) actúa como segunda línea server-side no saltable.
3. **Fail-Closed Estricto:** Organizaciones sin membresía activa o con suscripción expirada/cancelada/suspendida tienen denegada cualquier operación de subida (HTTP 403 `InactiveMembershipException`). Planes con cuota 0 o nula asignan 0 bytes disponibles.
4. **Resistencia a Concurrencia (Race Conditions):** Mediante bloqueo pesimista en base de datos (`SELECT ... FOR UPDATE OF m.ID_MEMBRESIA`), se serializan intentos simultáneos de subida entre múltiples instancias o hilos, impidiendo que peticiones concurrentes sobrepasen la cuota asignada.
5. **Reemplazo Atómico:** El versionamiento y reemplazo de archivos calcula la diferencia neta (`max(0, usado - anterior) + nuevo <= limite`), permitiendo sustituir archivos grandes si el uso proyectado no excede el límite.
6. **Anti-IDOR y Auditoría Canónica:** El endpoint de consulta de cuota `/api/v1/storage/quota` restringe a usuarios no `SUPERADMIN` a consultar únicamente su propia organización. Los rechazos por exceso de cuota se registran en `AUDITORIA_LOG` cumpliendo el constraint `CK_AUDITORIA_ACCION` (`ACCESO_DENEGADO`), `ENTIDAD='STORAGE'`, `RESULTADO='FALLIDO'` y formato JSON válido.

---

## 2. FUENTE DE VERDAD Y ESTRUCTURA DE BASE DE DATOS

La implementación no altera el DDL existente ni agrega columnas innecesarias; utiliza el esquema certificado de SAED 2.0:

| Objeto de Base de Datos | Columna / Restricción | Rol en GAP-ENT-06 |
|---|---|---|
| `PLANES` | `LIMITE_ALMACENAMIENTO_GB` (`NUMBER`) | Define la capacidad máxima contratada (1 GB FREE, 10 GB PRO, 50 GB ENTERPRISE). |
| `MEMBRESIAS` | `ESTADO`, `FECHA_FIN`, `ID_ORGANIZACION` | Determina la vigencia (`ACTIVA`, `PRUEBA`) y el objeto de bloqueo pesimista (`FOR UPDATE`). |
| `VERSIONES_DOCUMENTO` | `ARCHIVO_TAMANO_BYTES` (`NUMBER`) | Sumatoria del tamaño de versiones activas de documentos. |
| `DOCUMENTOS` | `ID_ORGANIZACION`, `ESTADO` | Agrupador de versiones; se excluyen documentos con `ESTADO = 'ELIMINADO'`. |
| `GASTOS` | `ARCHIVO_TAMANO_BYTES` (`NUMBER`) | Sumatoria de comprobantes y soportes de gastos asociados a propiedades de la organización (excluye `ANULADO`). |
| `AUDITORIA_LOG` | `ACCION`, `ENTIDAD`, `RESULTADO`, `DETALLES` | Trazabilidad de seguridad; respeta `CK_AUDITORIA_ACCION` (`ACCESO_DENEGADO`) y payload JSON válido (`IS JSON`). |

---

## 3. ESPECIFICACIÓN TÉCNICA DE LA IMPLEMENTACIÓN

### 3.1. Conversión Binaria Estricta
Se adoptó la convención binaria estándar para almacenamiento (GiB):
$$\text{BYTES\_PER\_GB} = 1024 \times 1024 \times 1024 = 1{,}073{,}741{,}824 \text{ bytes}$$
$$\text{MAX\_FILE\_SIZE} = 10 \times 1024 \times 1024 = 10{,}485{,}760 \text{ bytes (10 MB)}$$

### 3.2. Fórmula de Consumo Acumulado
$$\text{Uso Total} = \sum_{\substack{d \in \text{DOCS}(\text{org}) \\ d.\text{estado} \neq \text{'ELIMINADO'}}} v.\text{tamano} + \sum_{\substack{g \in \text{GASTOS}(\text{org}) \\ g.\text{estado} \neq \text{'ANULADO'}}} g.\text{tamano}$$

### 3.3. Validación de Subida y Reemplazo
- **Subida nueva:**  
  $$\text{usado} + \text{nuevo} \le \text{limite} \implies \text{Permitido}$$  
  $$\text{usado} + \text{nuevo} > \text{limite} \implies \text{HTTP 409 Conflict (STORAGE\_QUOTA\_EXCEEDED)}$$  
  *(El caso exacto $\text{usado} + \text{nuevo} = \text{limite}$ se permite).*
- **Reemplazo:**  
  $$\max(0, \text{usado} - \text{liberado}) + \text{nuevo} \le \text{limite} \implies \text{Permitido}$$

### 3.4. Concurrencia y Bloqueo Pesimista
En `StorageQuotaServiceImpl.validateUpload` y `validateReplacement`:
```sql
SELECT m.ID_MEMBRESIA, m.ID_PLAN, m.ESTADO, p.CODIGO AS PLAN_CODIGO, p.LIMITE_ALMACENAMIENTO_GB
FROM MEMBRESIAS m
JOIN PLANES p ON m.ID_PLAN = p.ID_PLAN
WHERE m.ID_ORGANIZACION = :orgId
  AND m.ESTADO IN ('ACTIVA', 'PRUEBA')
  AND (m.FECHA_FIN IS NULL OR m.FECHA_FIN >= TRUNC(SYSDATE))
FOR UPDATE OF m.ID_MEMBRESIA
```
Esto garantiza que dos subidas paralelas dentro de la misma organización se evalúen secuencialmente, previniendo condiciones de carrera donde ambas subidas excederían conjuntamente la cuota.

---

## 4. CONTRATOS DE API Y MANEJO DE ERRORES

### 4.1. Endpoint de Consulta: `GET /api/v1/storage/quota`
- **Autorización:** `@PreAuthorize("hasAnyAuthority('SCOPE_SUPERADMIN', 'SCOPE_ADMIN_ORGANIZACION', 'SCOPE_ADMIN_PROPIEDAD')")`
- **Control Anti-IDOR:**
  - `SUPERADMIN`: Puede consultar cualquier `organizationId` o la suya propia.
  - `ADMIN_ORGANIZACION` / `ADMIN_PROPIEDAD`: Si solicitan un `organizationId` distinto al de su sesión activa, se rechaza inmediatamente con **HTTP 403 Forbidden** (`ACCESS_DENIED_ORGANIZATION_MISMATCH`).
- **Respuesta Exitosa (HTTP 200):**
```json
{
  "status": "success",
  "data": {
    "organizationId": 1,
    "limitBytes": 1073741824,
    "usedBytes": 524288000,
    "availableBytes": 549453824,
    "limitGb": 1.0,
    "usedGb": 0.4883,
    "availableGb": 0.5117,
    "percentageUsed": 48.83,
    "planCodigo": "FREE",
    "membershipEstado": "ACTIVA"
  }
}
```

### 4.2. Error Contract: HTTP 409 Conflict (`STORAGE_QUOTA_EXCEEDED`)
Lanzado cuando se sobrepasa la cuota acumulada en `DocumentoController` o `FileStorageService`:
```json
{
  "status": 409,
  "code": "STORAGE_QUOTA_EXCEEDED",
  "message": "Cuota global de almacenamiento excedida para la organización. Límite: 1073741824 bytes, Usado: 1070000000 bytes, Solicitado: 8388608 bytes, Disponible: 3741824 bytes.",
  "limitBytes": 1073741824,
  "usedBytes": 1070000000,
  "requestedBytes": 8388608,
  "availableBytes": 3741824,
  "timestamp": "2026-09-16T17:03:01.593-05:00"
}
```

### 4.3. Integración en `OrgSubscriptionDTO` (`GET /api/v1/org/suscripcion`)
Se enriqueció la suscripción activa con las métricas en tiempo real:
- `almacenamientoUsadoBytes` (`Long`)
- `almacenamientoUsadoGb` (`Double`)
- `porcentajeAlmacenamiento` (`Double`)

---

## 5. EVIDENCIA DE PRUEBAS AUTOMATIZADAS

### 5.1. Suite Principal: `StorageQuotaSecurityTest` (21/21 Verde)
Ruta: `backend/src/test/java/com/saed/backend/platform/StorageQuotaSecurityTest.java`  
Resultado: **21 tests run, 0 failures, 0 errors, 0 skipped** (Tiempo: 14.48 s).

| # | Caso de Prueba | Resultado |
|---|---|---|
| 01 | Subida por debajo de la cuota permitida (archivo 2 MB en plan 1 GB) | **PASS** |
| 02 | Rechazo cuando subida excede cuota global (HTTP 409 `STORAGE_QUOTA_EXCEEDED`) | **PASS** |
| 03 | Validación exacta al límite (`usado + nuevo == limite`) es aprobada | **PASS** |
| 04 | Rechazo estricto por 1 solo byte por encima del límite | **PASS** |
| 05 | Preservación del límite de archivo individual (10 MB) aún con cuota disponible | **PASS** |
| 06 | Rechazo de tamaño de archivo negativo | **PASS** |
| 07 | Manejo fail-closed: organización sin membresía activa es rechazada (HTTP 403) | **PASS** |
| 08 | Manejo fail-closed: membresía expirada es rechazada (HTTP 403) | **PASS** |
| 09 | Manejo fail-closed: membresía cancelada es rechazada (HTTP 403) | **PASS** |
| 10 | Manejo fail-closed: membresía suspendida es rechazada (HTTP 403) | **PASS** |
| 11 | Membresía en período de prueba (`PRUEBA`) permite almacenamiento según su plan | **PASS** |
| 12 | Plan con almacenamiento null o 0 asigna 0 bytes de cuota | **PASS** |
| 13 | Rechazo inmediato de subida en plan con cuota 0 (0 bytes disponibles) | **PASS** |
| 14 | Cálculo de uso refleja sumatoria de versiones de documentos no eliminados | **PASS** |
| 15 | Endpoint `GET /api/v1/storage/quota` retorna métricas y porcentajes exactos | **PASS** |
| 16 | Control Anti-IDOR en `/api/v1/storage/quota` bloquea consulta de otra organización | **PASS** |
| 17 | SUPERADMIN puede consultar cuota de cualquier organización | **PASS** |
| 18 | Concurrencia multi-hilo serializada con pesimistic lock evita sobrepasar cuota | **PASS** |
| 19 | Reemplazo de archivo descuenta archivo anterior (`usado - viejo + nuevo`) | **PASS** |
| 20 | Reemplazo que excede la cuota es rechazado | **PASS** |
| 21 | Contrato de error HTTP 409 con payload estructurado en `DocumentoController` | **PASS** |

### 5.2. Suites de Regresión Certificadas
Se ejecutaron las suites de control de acceso, auditoría y facturación:
- `MembershipBillingSecurityTest`: 16 pruebas — **16 PASS**
- `MembershipHistorySecurityTest`: 16 pruebas — **16 PASS**
- `ModuleEntitlementsSecurityTest`: 18 pruebas — **18 PASS**
- `P0PlansAndMembershipsSecurityTest`: 14 pruebas — **14 PASS**
- `SuperAdminAdversarialAuthorizationTest`: 24 pruebas — **24 PASS**

**Total pruebas ejecutadas en backend:** **109 / 109 PASS (100% verde, 0 fallos).**

### 5.3. Verificación de Compilación Frontend
Ejecución: `npm run build` en `frontend/`  
Resultado: **Código 0 (`✓ built in 17.86s`)**, sin errores de tipado, sintaxis o empaquetado Vite.

---

## 6. CONCLUSIÓN Y ESTADO FINAL

El módulo **GAP-ENT-06: Cuota Global de Almacenamiento por Organización** queda completamente implementado, auditado y certificado con pruebas adversariales, de concurrencia y de regresión en SAED 2.0. No se realizaron commits ni pushes conforme a las restricciones del proyecto. El espacio de trabajo está en estado limpio y verificado.
