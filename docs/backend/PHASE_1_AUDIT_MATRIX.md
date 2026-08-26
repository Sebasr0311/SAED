# SAED 2.0 — PHASE 1 AUDIT MATRIX

La premisa de SAED 2.0 es **Evitar el doble Logging**. La base de datos (V3.9) ya implementa disparadores (triggers) rigurosos para auditar DML (Insert, Update, Delete) en las tablas de negocio, almacenándolos en la tabla `AUDITORIA_LOG`. El Backend (Spring Boot) se dedicará exclusivamente a auditar *eventos de ciclo de vida e identidad* que la Base de Datos no puede ver porque ocurren fuera de las transacciones de negocio.

## Matriz de Responsabilidad de Auditoría

| EVENTO | DESCRIPCIÓN | SISTEMA RESPONSABLE | MECANISMO |
|---|---|---|---|
| **DML Business** | Creación/Edición/Eliminación de entidades de negocio (Propiedades, Org, Personas). | Base de Datos (Oracle) | Triggers `AFTER INSERT/UPDATE/DELETE` hacia `AUDITORIA_LOG`. |
| **Login Exitoso** | El usuario supera el hash BCrypt y recibe JWT. | Backend (Spring) | Actualiza `ultimo_login` en `USUARIOS` y envía evento `AUTH_SUCCESS` a `AUDITORIA_LOG`. |
| **Login Fallido** | Credenciales incorrectas. | Backend (Spring) | Aumenta `intentos_fallidos` en `USUARIOS`. Evento `AUTH_FAILED`. |
| **Bloqueo Cuenta** | Límite de intentos superado. | Backend (Spring) | Actualiza `fecha_bloqueo` / `estado='BLOQUEADO'` y Evento `AUTH_LOCKED`. |
| **Rechazo Contexto** | El usuario pide un Assignment ID que no posee o es inválido. | Backend (Spring) | Evento `CONTEXT_REJECTED` alertando un posible ataque de Spoofing. |
| **Denegación de Acceso** | El cliente falla la aserción de `Privilege Escalation` o interceptor de Permisos. | Backend (Spring) | Evento `AUTHZ_DENIED`. |
| **Membresía (DML)** | Transición PRUEBA -> ACTIVA -> SUSPENDIDA. | Base de Datos (Oracle) | Trigger nativo que escribe en `MEMBRESIAS_HISTORIAL`. (Append-only). |

## Inserción desde el Backend
Dado que en `STATE 1` (Bootstrap) el usuario aún no tiene `SAED_CTX` completamente inflado, Spring Boot utilizará una sobrecarga del método insertador del repositorio (o un procedimiento `PKG_AUDIT` si existiese) que acepta un DTO de Auditoría Crítica y lo inserta forzosamente. 
*Importante:* En V4.1 se demostró que `AUDITORIA_LOG` tiene un trigger ORA-20099 anti-delete, garantizando su inmutabilidad frente a DML destructivo tanto de Oracle como de Spring.

## Estrategia de Consulta (Performance)
`AUDITORIA_LOG` crecerá exponencialmente. La consulta de eventos por parte de un Administrador (`GET /api/v1/audit`) requerirá forzosamente los siguientes filtros obligatorios:
1. Paginación y límite de página (`LIMIT`, `OFFSET`).
2. Rango de Fechas (Máximo 30 días de ventana).
3. (Opcional) Filtrado por entidad/tabla.

*Si `AUDITORIA_LOG` no posee índices sobre la fecha, tabla o entidad en V3.9, se documentará la necesidad de una futura migración V4.X orientada exclusivamente al afinamiento de índices logísticos (Partitioning / Indexing).*
