# PHASE 1B - ORGANIZATION & ROLE DESIGN

## 1. Introducción
El modelo de autorización de SAED 2.0 es dinámico y jerárquico. Un mismo `USUARIO` puede pertenecer a múltiples `ORGANIZACIONES` o tener acceso a diferentes `PROPIEDADES` con distintos `ROLES`.

Este diseño explica cómo resolver y estructurar esos roles para el cliente.

## 2. Jerarquía Espacial (Alcances / Scopes)
Según `ROLES.ALCANCE`, un rol opera en un nivel específico:
1. **GLOBAL:** (Ej. `SUPER_ADMIN`). No requiere `ID_ORGANIZACION`.
2. **ORGANIZACION:** (Ej. `ADMIN_ORG`). Requiere `ID_ORGANIZACION`. Acceso a todas las propiedades debajo de ella.
3. **PROPIEDAD:** (Ej. `ADMIN_EDIFICIO`). Requiere `ID_ORGANIZACION` y `ID_PROPIEDAD`.
4. **UNIDAD:** (Ej. `PROPIETARIO`, `INQUILINO`). Requiere `ID_ORGANIZACION`, `ID_PROPIEDAD` e `ID_UNIDAD`.

## 3. Resolución de Asignaciones (Assignments)

Cuando un usuario se autentica (STATE 1), el cliente no sabe qué organizaciones tiene disponibles. El backend proveerá un endpoint `/api/v1/auth/assignments` que devolverá la lista de asignaciones vigentes:

### Query Estructural (Conceptual)
```sql
SELECT 
    ua.id_asignacion,
    r.codigo as rol_codigo,
    r.alcance as rol_alcance,
    o.id_organizacion, o.nombre as organizacion_nombre,
    p.id_propiedad, p.nombre as propiedad_nombre,
    u.id_unidad, u.identificador_unidad
FROM USUARIO_ASIGNACIONES ua
JOIN ROLES r ON ua.id_rol = r.id_rol
LEFT JOIN ORGANIZACIONES o ON ua.id_organizacion = o.id_organizacion
LEFT JOIN PROPIEDADES p ON ua.id_propiedad = p.id_propiedad
LEFT JOIN UNIDADES u ON ua.id_unidad = u.id_unidad
WHERE ua.id_usuario = :idUsuario 
  AND ua.estado = 'ACTIVA' 
  AND TRUNC(SYSDATE) BETWEEN ua.fecha_inicio AND NVL(ua.fecha_fin, TRUNC(SYSDATE));
```

## 4. Agrupamiento para el Cliente
Para facilitar la UI, el backend agrupará estas asignaciones por `Organización` -> `Propiedad` -> `Asignaciones (Rol/Unidad)`.

El cliente presentará un "Selector de Organización/Propiedad" al usuario al iniciar sesión. Cuando el usuario elija, el cliente usará el `id_asignacion` elegido para las peticiones subsecuentes.
