# PHASE 1D AUTHORIZATION MATRIX

## 1. RLS Scope Requirements

Oracle RLS dynamically filters tables based on the user's active context (`GLOBAL`, `ORGANIZACION`, `PROPIEDADES_SELECCIONADAS`, `PROPIEDAD`, `UNIDAD`).

| Entity | Protected By | Required Context | Behavior / Access |
|---|---|---|---|
| `PERSONAS` | `FN_FILTRO_ORGANIZACION` (Pending V4.3) | `ORGANIZACION` or `GLOBAL` | Viewable if associated with an entity within the user's Organization. |
| `PROPIETARIOS_UNIDAD` | `FN_FILTRO_UNIDAD` | `UNIDAD`, `PROPIEDAD`, `ORG` | Viewable/Mutable if the Unit falls within the active scope. |
| `RESIDENTES_UNIDAD` | `FN_FILTRO_UNIDAD` | `UNIDAD`, `PROPIEDAD`, `ORG` | Viewable/Mutable if the Unit falls within the active scope. |

## 2. Spring Boot Application Level Matrix

Spring Boot validates roles before attempting the database transaction to prevent unnecessary DB hits, but relies entirely on Oracle for row-level isolation.

| Endpoint | Allowed Scopes (Spring) | Oracle RLS Enforcement |
|---|---|---|
| `POST /api/v1/personas` | `GLOBAL`, `ORGANIZACION`, `PROPIEDAD` | Inserts person. V4.3 patch must allow insert. |
| `GET /api/v1/personas` | `GLOBAL`, `ORGANIZACION`, `PROPIEDAD`, `UNIDAD` | Selects only personas linked to the active scope. |
| `POST /api/v1/units/{id}/owners` | `GLOBAL`, `ORGANIZACION`, `PROPIEDAD` | Prevents insert if `{id}` is outside context (ORA-28115). |
| `GET /api/v1/units/{id}/owners` | `GLOBAL`, `ORGANIZACION`, `PROPIEDAD`, `UNIDAD` | Returns empty if `{id}` is outside context. |
| `POST /api/v1/units/{id}/residents`| `GLOBAL`, `ORGANIZACION`, `PROPIEDAD` | Prevents insert if `{id}` is outside context (ORA-28115). |
| `GET /api/v1/units/{id}/residents` | `GLOBAL`, `ORGANIZACION`, `PROPIEDAD`, `UNIDAD` | Returns empty if `{id}` is outside context. |

## 3. Privilege Escalation Prevention
- A `RESIDENTE` or `ADMIN_UNIDAD` cannot create new `PERSONAS` or assign `PROPIETARIOS_UNIDAD`.
- Only `ADMIN_PROPIEDAD`, `ADMIN_ORGANIZACION`, and `SUPERADMIN` have the authority to assign ownership/residency to units.
