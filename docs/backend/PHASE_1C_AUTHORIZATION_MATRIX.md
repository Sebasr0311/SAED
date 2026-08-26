# PHASE 1C AUTHORIZATION MATRIX

## Matriz de Creación de Entidades

| Acción | GLOBAL / SUPERADMIN | ORGANIZACION (Admin Org) | PROPIEDAD (Admin Prop) | UNIDAD |
| --- | --- | --- | --- | --- |
| Crear Organización | PERMITIDO | DENEGADO | DENEGADO | DENEGADO |
| Actualizar Org | PERMITIDO | PERMITIDO (Si es suya) | DENEGADO | DENEGADO |
| Crear Propiedad | PERMITIDO | PERMITIDO (En su org) | DENEGADO | DENEGADO |
| Actualizar Propiedad | PERMITIDO | PERMITIDO (En su org) | PERMITIDO (Si es suya) | DENEGADO |
| Crear Unidad | PERMITIDO | PERMITIDO (En su org) | PERMITIDO (En su prop) | DENEGADO |
| Crear Asignación | Todo alcance | Hasta alcance Org | Hasta alcance Prop | DENEGADO |

## Prevención de Spoofing (Java)
- Las operaciones POST/PUT no pueden inyectar un ID de Organización o Propiedad distinto al que tienen en el SaedContext (establecido en State 2 por X-Assignment-Id).
- Si el contexto es de Organización 5, cualquier idOrganizacion del payload se sobreescribe forzosamente con 5 o se rechaza con 403.
