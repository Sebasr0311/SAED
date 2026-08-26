# PHASE 1B - AUTHORIZATION MODEL

## 1. Principio de Confianza Cero (Zero Trust)
En SAED 2.0, el cliente (Frontend) **NUNCA** envía identificadores directos de su contexto (`id_organizacion`, `id_rol`, etc.) como parámetros confiables. 

El JWT (creado en Fase 1A) contiene **únicamente** el `id_usuario`.
El cliente envía un header `X-Assignment-Id` con el ID de la asignación seleccionada.

## 2. Flujo de Autorización

1. **Request Inbound:** 
   El cliente envía `Authorization: Bearer <JWT>` y `X-Assignment-Id: 123`.
2. **Spring Security (Fase 1A):**
   Valida el JWT y establece el `SecurityContext` con el `id_usuario`.
3. **Interceptor de Contexto (Fase 1B):**
   Intercepta la petición web (ej. `HandlerInterceptor` o `Filter`).
4. **Validación de Assignment:**
   El backend consulta `USUARIO_ASIGNACIONES` (vía un DAO en `SAED_SEC_MASTER` o usando el contexto de bootstrap). 
   - ¿El `id_asignacion = 123` pertenece al `id_usuario` del JWT?
   - ¿Está activa?
   - Si NO: Retorna `HTTP 403 Forbidden` (Anti-Privilege Escalation).
5. **Transición Oracle (STATE 1 -> STATE 2):**
   Si la asignación es válida, se extraen `id_organizacion`, `id_propiedad`, `rol_codigo` asociados a ese `id_asignacion`.
   El interceptor inyecta estos valores en el `Connection` Oracle invocando `PKG_SAED_SESSION.SET_CONTEXT(...)`.
6. **Ejecución Oracle RLS:**
   El Controlador/Servicio ejecuta SQL normal (ej. `SELECT * FROM PROPIEDADES`).
   Oracle RLS evalúa la política basándose en el contexto de la sesión, filtrando automáticamente los datos.

## 3. Manejo del Contexto Inválido (Context Bleed Prevention)
Si el cliente no envía `X-Assignment-Id`, el Oracle session se mantiene en **STATE 1 (Identity Only)**.
Las políticas RLS (V4.1) están programadas para que si `SYS_CONTEXT('SAED_CTX', 'ORGANIZACION_ID')` es NULL, retornen un predicado estricto `1=2`, impidiendo fugas de datos masivas.

## 4. Auditoría
Cada transición de contexto y cada fallo de autorización (intento de usurpar un `X-Assignment-Id` ajeno) debe quedar registrado en Spring Boot y opcionalmente en la base de datos Oracle (vía triggers o SPs de seguridad).
