# SAED 2.0 Authentication & Authorization

## Diferencia Clave
- **Autenticación (Quien Eres)**: Validar las credenciales (Email y Password) contra la tabla `USUARIOS`. Genera el JWT de identidad inicial (sin contexto organizacional).
- **Autorización (Qué Puedes Hacer y Dónde)**: El acceso a los recursos organizacionales exige que el usuario solicite un **Contexto Específico** en base a sus asignaciones en `USUARIO_ASIGNACIONES`. 

## 1. Login (`POST /api/v1/auth/login`)
El usuario provee email y contraseña. 
El backend:
1. Comprueba BCrypt contra `USUARIOS.password_hash`.
2. Verifica que `estado = 'ACTIVO'`.
3. Consulta `USUARIO_ASIGNACIONES` para retornar la lista de propiedades, organizaciones y roles que el usuario tiene disponibles.
4. Emite un **Identity JWT**. Este token *NO* tiene acceso a tablas transaccionales todavía.

## 2. Definición de Contexto (`POST /api/v1/session/context`)
El cliente selecciona una propiedad de su lista y la envía.
El backend:
1. Recibe el Identity JWT y la solicitud `{ propertyId: 10 }`.
2. **VALIDA** en la base de datos (con un nuevo query) que el usuario tiene realmente una asignación activa en `propertyId = 10`. 
3. Si es válido, emite un **Context JWT**, que contiene el `organizationId`, `propertyId`, y `roleCode`.
4. El cliente utiliza este *Context JWT* para todas las operaciones posteriores.

## 3. Flujo API
Cuando llega un Request con el **Context JWT**:
1. `JwtAuthenticationFilter` extrae el token y lo valida criptográficamente.
2. Si es válido, crea un `SaedContext(userId, orgId, propId, role)` y lo deposita en `SaedContextHolder`.
3. Spring deriva la petición al Controller.
4. El Service invoca al Repository (`@Transactional`).
5. `SaedDataSourceProxy` lee el `SaedContextHolder` e invoca `PKG_SAED_SESSION.SET_CONTEXT` en Oracle.
6. La consulta de Oracle responde aplicando las políticas RLS basadas en esa propiedad y usuario.
7. Al finalizar, Oracle limpia el contexto.
