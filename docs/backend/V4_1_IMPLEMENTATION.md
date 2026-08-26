# SAED 2.0 — V4.1 CORE SESSION PATCH IMPLEMENTATION

## 1. Problema Original
La base de datos original (V3.9) tenía un deadlock arquitectónico en `PKG_SAED_SESSION.SET_CONTEXT`. 
Al intentar validarse a sí mismo haciendo `SELECT estado FROM USUARIOS`, el paquete disparaba el RLS `FN_FILTRO_USUARIOS` que le denegaba el acceso por tener el Contexto vacío (`ID_ORGANIZACION` nulo). El resultado era invariablemente `ORA-20082` (Usuario no existe), impidiendo arrancar la sesión de negocio.

## 2. Causa Raíz
Oracle procesa el RLS en el esquema propietario (SAED_V39_FINAL_TEST) por el `AUTHID DEFINER`. La política `FN_FILTRO_USUARIOS` retornaba `1=0` al carecer del contexto de Tenant (Organización).

## 3. Solución Implementada
Se implementó una **Máquina de Estados de Sesión (Session State Machine)**.
Se introdujo una variable privada en el contexto `SAED_CTX` llamada `STATE`.
Nuevos estados definidos:
- **ANONYMOUS** (o Nulo): Sin acceso. Todo retorna `1=0`.
- **BOOTSTRAP**: Solo permite aislar la fila de la identidad actual y de sus propias asignaciones para poder resolver el ciclo Auth → Tenant.
- **BUSINESS**: Sesión RLS completa y estándar para el Tenant (Org) establecido.
- **CLEARING**: Transitorio durante el cierre de la conexión, restringe todo hasta llegar a ANONYMOUS.

## 4. Flujo de Autenticación Definitivo
1. `POST /api/v1/auth/login` → Llama a V4.0 (Auth Bootstrap) y retorna un JWT. *(STATE 0)*
2. `GET /api/v1/me/contexts` → Se intercepta el JWT. `SaedDataSourceProxy` lee que no hay Organización e invoca `SET_BOOTSTRAP_CONTEXT`. *(STATE 1)*. El servicio consulta `USUARIO_ASIGNACIONES` (Oracle RLS permite ver solo los propios).
3. `GET /api/v1/...` → El cliente envía el JWT y el `X-Assignment-Id`. `SaedDataSourceProxy` lee la organización completa e invoca `SET_CONTEXT`. *(STATE 2)*.

## 5. Amenazas Mitigadas
- **Spoofing**: Rechazo en la base de datos de Contextos falsificados (Validación de asignaciones físicas antes del salto a STATE 2).
- **Context Bleed**: Controlado a nivel de Pool (HikariCP) mediante Proxy Interceptor. Llama siempre a `CLEAR_CONTEXT` en `.close()`.
- **Data Leakage**: RLS previene acceso a tablas de negocio mientras se encuentre en STATE 0 o 1.
