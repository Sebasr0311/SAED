# SAED 2.0 Authorization

La autorización en SAED 2.0 ocurre en dos capas estrictas, complementarias y no solapadas:

## 1. Capa Lógica (Backend - Spring Security)
- Verifica a nivel HTTP/Endpoint si el usuario tiene el rol general requerido (ej: `@PreAuthorize("hasRole('ADMIN_PROPIEDAD')")`).
- Evita que peticiones absurdas (ej: un Residente intentando llamar al endpoint de creación de organizaciones) saturen la base de datos.
- Confía en los claims del *Context JWT*.

## 2. Capa Física (Oracle VPD/RLS)
- Es la autoridad final absoluta.
- Independientemente de lo que opine Spring Security, todas las sentencias DML pasan por el filtro físico de las 88 políticas RLS.
- Si un atacante logra engañar al backend (o si hay un bug en el código Java), la base de datos abortará la acción devolviendo 0 filas o bloqueando el INSERT con `ORA-28115`.

## Scopes
- `GLOBAL` (Superadmin)
- `TENANT` (Organización entera)
- `PROPERTY` (Solo una propiedad específica)
- `UNIT` (Solo los datos de un residente en su apartamento)

El JWT y el `SaedContext` portarán esta información para inyectarla en Oracle.
