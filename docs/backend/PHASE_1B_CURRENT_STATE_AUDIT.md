# PHASE 1B - CURRENT STATE AUDIT (ORACLE V3.9)

## 1. Introducción
Este documento audita el estado actual (V3.9) del modelo de datos base para la resolución de organizaciones, propiedades y autorización (Phase 1B). 
**Regla de oro:** Todo el diseño debe basarse estrictamente en `V3.9__baseline_multitenant.sql` y sus modificaciones en `V4.0` y `V4.1`.

## 2. Inventario de Tablas Core (Fase 1B)

### 2.1. `ORGANIZACIONES`
- **PK:** `ID_ORGANIZACION` (NUMBER, IDENTITY)
- **Campos principales:** `NOMBRE`, `IDENTIFICACION_FISCAL`, `ESTADO`
- **Comentarios:** Es el "Tenant" de más alto nivel en el modelo Multi-Tenant. 

### 2.2. `PROPIEDADES`
- **PK:** `ID_PROPIEDAD` (NUMBER, IDENTITY)
- **FK:** `ID_ORGANIZACION` -> `ORGANIZACIONES(ID_ORGANIZACION)`
- **Campos principales:** `NOMBRE`, `ID_TIPO_PROPIEDAD`, `ESTADO`
- **Comentarios:** Agrupa los conjuntos/edificios debajo de una Organización.

### 2.3. `UNIDADES` (Si aplica)
- **PK:** `ID_UNIDAD` (NUMBER, IDENTITY)
- **FK:** `ID_PROPIEDAD` -> `PROPIEDADES(ID_PROPIEDAD)`
- **Campos principales:** `IDENTIFICADOR_UNIDAD`, `ID_TIPO_UNIDAD`, `ID_PERSONA_PROPIETARIO`

### 2.4. `ROLES`
- **PK:** `ID_ROL` (NUMBER, IDENTITY)
- **Campos principales:** `CODIGO` (VARCHAR2), `NOMBRE`, `ALCANCE` (GLOBAL, ORGANIZACION, PROPIEDAD, UNIDAD), `ESTADO`

### 2.5. `PERMISOS` y `ROL_PERMISO`
- `PERMISOS`: `ID_PERMISO`, `CODIGO`, `MODULO`
- `ROL_PERMISO`: `ID_ROL`, `ID_PERMISO` (relación N:M)

### 2.6. `USUARIO_ASIGNACIONES` (El pivote de autorización)
- **PK:** `ID_ASIGNACION` (NUMBER, IDENTITY)
- **FKs:** 
  - `ID_USUARIO` -> `USUARIOS`
  - `ID_ROL` -> `ROLES`
  - `ID_ORGANIZACION` -> `ORGANIZACIONES` (Opcional, depende del alcance del rol)
  - `ID_PROPIEDAD` -> `PROPIEDADES` (Opcional)
  - `ID_UNIDAD` -> `UNIDADES` (Opcional)
- **Campos principales:** `ESTADO`, `FECHA_INICIO`, `FECHA_FIN`

## 3. Estado de la Lógica de Contexto y RLS
- **V4.0:** Otorgó permisos a `SAED_SEC_MASTER` para consultar `USUARIO_ASIGNACIONES` y `ROLES` saltándose RLS.
- **V4.1:** Definió en `PKG_SAED_SESSION` los estados y transiciones seguros:
  - `STATE 1 (Identity Only)` -> Creado por V4.0 tras autenticación.
  - `STATE 2 (Context Bound)` -> Seleccionado por el usuario explícitamente vía `X-Assignment-Id`.
- **Context Bleed Prevention:** `PKG_SAED_SECURITY_RLS` posee mitigaciones para que si un usuario tiene STATE 1 intente acceder a datos tenant, RLS retorne 0 filas.

## 4. Conclusión
El esquema V3.9 proporciona un soporte Multi-Tenant maduro. La tabla `USUARIO_ASIGNACIONES` es la fuente de verdad para determinar a qué Organización/Propiedad/Unidad tiene acceso un usuario y con qué Rol. El diseño de Fase 1B debe orquestar la consulta a esta tabla y la propagación a `PKG_SAED_SESSION.SET_CONTEXT`.
