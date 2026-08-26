# PHASE 1B - AUTHORIZATION MATRIX

Esta matriz define los permisos estructurales base esperados para cada alcance (SCOPE) en SAED 2.0.
Esta matriz está respaldada y forzada por el motor de Oracle RLS (`PKG_SAED_SECURITY_RLS`).

## Matriz de Alcance Jerárquico (Scopes)

| Alcance (Scope) | Nivel Jerárquico | Acceso `ORGANIZACION` | Acceso `PROPIEDAD` | Acceso `UNIDAD` | Ejemplo de Rol Real en BBDD |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **GLOBAL** | Sistema completo | ALL | ALL | ALL | `ADMIN_SAED`, `SOPORTE_TI` |
| **ORGANIZACION** | Tenant y sub-entidades | Solo Tenant Asignado | Todas del Tenant Asignado | Todas de las Propiedades del Tenant | `ADMIN_ORG` |
| **PROPIEDAD** | Conjunto/Edificio específico | Solo Lectura del Tenant Padre | Solo Propiedad Asignada | Todas de la Propiedad Asignada | `ADMIN_PROPIEDAD`, `SUPERVISOR` |
| **UNIDAD** | Apartamento / Local | No | No (Solo Lectura metadatos básicos) | Solo Unidad Asignada | `PROPIETARIO`, `RESIDENTE` |

## Impacto en RLS (`PKG_SAED_SECURITY_RLS`)

Las funciones de filtro definidas en V3.9 y parcheadas en V4.1 utilizan este alcance (`v_rol_codigo`) y los IDs inyectados por la sesión:

- **`FN_FILTRO_ORGANIZACION`**: Retorna `id_organizacion = SYS_CONTEXT('SAED_CTX','ORGANIZACION_ID')` o `1=1` si es GLOBAL.
- **`FN_FILTRO_PROPIEDAD`**: 
  - Si Rol es GLOBAL: `1=1`
  - Si Rol es ORGANIZACION: Retorna `id_organizacion = SYS_CONTEXT(...)`
  - Si Rol es PROPIEDAD o UNIDAD: Retorna `id_propiedad = SYS_CONTEXT('SAED_CTX', 'PROPIEDAD_ID')`.
- **`FN_FILTRO_UNIDAD`**:
  - Si Rol es UNIDAD: Retorna `id_unidad = SYS_CONTEXT('SAED_CTX', 'UNIDAD_ID')`.

## Implicaciones para Spring Boot (Controller Level)
A nivel de Spring, se utilizará `@PreAuthorize("hasAuthority('PERMISO_X')")` para restringir acciones lógicas (ej. crear un usuario). Sin embargo, el filtro **de qué datos** ve el usuario o **sobre qué entidad en particular** puede actuar, se delega 100% a la base de datos (Oracle RLS). Spring NO debe hacer validaciones de tenencia en memoria.
