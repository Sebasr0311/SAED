# SAED 2.0 — V4 AUTH BOOTSTRAP DESIGN

## 1. Problema
El sistema requiere autenticar a los usuarios validando su `hash_password` en la tabla `USUARIOS`. Sin embargo, debido al robusto diseño Zero-Trust de Oracle V3.9, cualquier consulta a la base de datos sin un contexto multi-tenant válido (`SYS_CONTEXT` vacío) será interceptada por las políticas RLS, ocultando absolutamente todos los registros. Como resultado, el proceso de autenticación es imposible porque el sistema no puede localizar al usuario para comparar su contraseña.

## 2. Causa
La política RLS `POL_RLS_SEC_USR` utiliza la función `PKG_SAED_SECURITY_RLS.FN_FILTRO_USUARIOS`. Esta función está diseñada con un paradigma estricto: `IF v_org IS NULL THEN RETURN '1=0'; END IF;`. Dado que un usuario no autenticado carece inherentemente de organización, la función bloquea el acceso en la capa física de Oracle.

## 3. Requisitos
- Extraer `id_usuario`, `hash_password`, `estado` e `intentos_fallidos` usando únicamente un `email`.
- Actualizar `intentos_fallidos`, `fecha_bloqueo` y `ultimo_login`.
- Mantener la contraseña en texto plano fuera de Oracle (procesar el match de BCrypt en Spring).
- No debilitar ni modificar las políticas RLS existentes de la V3.9.
- Respetar el Principio de Mínimo Privilegio (evitar exponer listados completos de usuarios).

## 4. Threat Model
- **Vulnerabilidad**: Exposición de un endpoint o vista de base de datos que permite a un atacante no autenticado enumerar usuarios o extraer hashes de contraseñas de forma masiva (Data Exfiltration).
- **Abuso de bypass**: Un atacante compromete el backend y utiliza el canal de "Bootstrap" para eludir el RLS en otras tablas (Escalamiento de privilegios / Cross-tenant).
- **Mitigación obligatoria**: El mecanismo de bypass debe ser atómico, inyectable solo para operaciones puntuales predefinidas, impidiendo el uso de sentencias `SELECT` arbitrarias.

## 5. Alternativas
### A. Vista de autenticación con acceso controlado (BEQUEATH DEFINER)
- **Concepto**: Crear una `VW_AUTH_USUARIOS`.
- **Análisis**: En Oracle, las vistas heredan las políticas RLS de las tablas subyacentes. Para que la vista salte el RLS, el definidor debe tener el privilegio del sistema `EXEMPT ACCESS POLICY`. Si el esquema principal lo recibe, debilita toda la base de datos. Si lo recibe un esquema alterno, se podría exponer una vista, pero esto permite ejecutar `SELECT * FROM VW_AUTH_USUARIOS`, violando el principio de mínimo privilegio.

### B. Usuario/connection pool separado exclusivamente para authentication bootstrap
- **Concepto**: Configurar un segundo HikariPool en Spring Boot que conecte con un usuario exento de RLS.
- **Análisis**: Si Spring Boot es comprometido, el atacante obtiene control de esa conexión y puede leer toda la tabla `USUARIOS` sin restricciones. Muy alto riesgo.

### C. Policy RLS específica para la ruta de bootstrap
- **Concepto**: Modificar `FN_FILTRO_USUARIOS` para admitir un contexto temporal de "login_mode".
- **Análisis**: Totalmente descartado. Rompe la regla estricta de NO modificar los scripts V3.9 ni las políticas existentes.

### D. Package PL/SQL DEFINER'S RIGHTS que exponga exclusivamente la operación (RECOMENDADO)
- **Concepto**: Crear un esquema de seguridad de alto nivel (`SAED_SEC_MASTER`) que posea `EXEMPT ACCESS POLICY`. Este esquema despliega un paquete PL/SQL `PKG_AUTH_BOOTSTRAP` con procedimientos atómicos que reciben parámetros escalares (ej. `email`) y retornan valores `OUT` específicos.
- **Análisis**: Seguridad impenetrable. Aunque Spring sea vulnerado, la única operación permitida es preguntar por el hash de un correo específico uno a uno. No es posible ejecutar SELECT masivos, JOINs ni acceder a otras tablas. BCrypt sigue evaluándose en el backend.

## 6. Comparación
| Criterio | A (View) | B (Double Pool) | C (Mod. RLS) | D (PL/SQL Autónomo) |
| :--- | :--- | :--- | :--- | :--- |
| **Seguridad de Datos** | Baja (Lectura masiva) | Baja (Conexión abierta) | Media | **Muy Alta** (Blindado) |
| **Mínimo Privilegio** | Falla | Falla | Falla | **Cumple** |
| **Impacto V3.9** | Ninguno | Ninguno | Alto | **Ninguno** |
| **Rendimiento** | Alto | Medio | Alto | Alto |

## 7. Solución recomendada
Implementar la **Alternativa D**. Se creará un nuevo usuario/esquema en Oracle (`SAED_SEC_MASTER`) administrado únicamente por el DBA. Este esquema tendrá el privilegio `EXEMPT ACCESS POLICY` de forma focalizada, y contendrá el paquete `PKG_AUTH_BOOTSTRAP`. Al usuario regular de la aplicación (`SAED_APP` o el propietario) solo se le otorgará el permiso `EXECUTE` sobre este paquete.

## 8. Flujo de autenticación
1. Cliente envía `POST /api/v1/auth/login` (`email`, `password`).
2. Spring Boot invoca un `SimpleJdbcCall` al procedimiento `SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP.GET_AUTH_DATA(p_email, out_id, out_hash, out_estado, out_intentos)`.
3. El SP, al estar exento, puede localizar la fila y retornar los valores `OUT` al backend.
4. El backend utiliza `BCryptPasswordEncoder.matches(password_plano, out_hash)`.
5. Si es exitoso, Spring invoca `SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP.REGISTER_SUCCESS(id)`.
6. Si falla, invoca `REGISTER_FAILURE(id)`.
7. Spring emite el token JWT Identity-Only.

## 9. Flujo después del login
1. El JWT se envía junto a `X-Assignment-Id`.
2. El request cae en `JwtAuthenticationFilter`. Ya existe un `id_usuario`.
3. Aquí se requiere otro bypass similar para validar la asignación o el usuario puede leer su asignación si la RLS lo permite. (Nota: `FN_FILTRO_ASIGNACION` requiere organización, por ende, el paquete de bootstrap deberá exponer una función adicional `GET_ASSIGNMENT_CONTEXT(id_usuario, id_asignacion)` para arrancar la sesión).
4. El backend inyecta los parámetros reales en `SaedDataSourceProxy` -> `PKG_SAED_SESSION.SET_CONTEXT`.
5. A partir de aquí, el flujo Zero-Trust ordinario de la V3.9 asume el control.

## 10. Privilegios requeridos
Ejecutados como `SYSDBA`:
```sql
CREATE USER SAED_SEC_MASTER IDENTIFIED BY "***" ACCOUNT LOCK;
GRANT CREATE SESSION, CREATE PROCEDURE TO SAED_SEC_MASTER;
GRANT EXEMPT ACCESS POLICY TO SAED_SEC_MASTER; -- Privilegio crítico aislado
GRANT EXECUTE ON SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP TO SAED_V39_FINAL_TEST;
```

## 11. Oracle objects necesarios
- Esquema `SAED_SEC_MASTER`.
- `PKG_AUTH_BOOTSTRAP` (Especificación y Cuerpo):
  - `PROCEDURE GET_AUTH_DATA(...)`
  - `PROCEDURE REGISTER_LOGIN_FAILURE(...)`
  - `PROCEDURE REGISTER_LOGIN_SUCCESS(...)`
  - `PROCEDURE GET_ASSIGNMENT_CONTEXT(...)`

## 12. Cambios Spring necesarios
Refactorizar `UserRepository` y `ContextService` para reemplazar sus sentencias `SELECT / UPDATE` directas por invocaciones `CallableStatement` (ej. mediante `JdbcTemplate.call()`) dirigidas al paquete `PKG_AUTH_BOOTSTRAP`.

## 13. Auditoría
El paquete `PKG_AUTH_BOOTSTRAP` incluirá transacciones autónomas (`PRAGMA AUTONOMOUS_TRANSACTION`) para registrar en `AUDITORIA_LOG` cualquier intento fallido de autenticación (fuerza bruta) o inicio de sesión exitoso. Al estar dentro de la DB, garantiza la escritura incluso si el backend colapsa en medio del request.

## 14. Riesgos
- Otorgar `EXEMPT ACCESS POLICY` es un privilegio del sistema riesgoso. El esquema `SAED_SEC_MASTER` debe tener la cuenta bloqueada para login directo externo o tener sus contraseñas resguardadas en una bóveda, garantizando que nadie pueda hacer login interactivo con él.

## 15. Tests de seguridad
- Ejecutar un ataque en Spring Boot que intente inyectar SQL en la llamada a `GET_AUTH_DATA`.
- Verificar que `SAED_V39_FINAL_TEST` (usuario normal) intente hacer `SELECT * FROM USUARIOS` con contexto nulo y reciba 0 filas.
- Comprobar que el paquete no puede retornar más de 1 registro por vez.

## 16. Plan de migración
El script `V4.0__auth_bootstrap.sql` se ejecutará en 2 fases:
- **Pre-migración (DBA)**: Creación de esquema y otorgamiento de `EXEMPT ACCESS POLICY`.
- **Migración (Flyway/Liquibase)**: Despliegue del paquete `PKG_AUTH_BOOTSTRAP` y los grants a los usuarios de la aplicación.

## 17. Rollback
```sql
REVOKE EXECUTE ON SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP FROM SAED_V39_FINAL_TEST;
DROP USER SAED_SEC_MASTER CASCADE;
```
Restaurar Spring Boot a la versión anterior en caso de rollback de código.

## 18. Compatibilidad con V3.9
100% Compatible. No se altera, se omite o se reescribe ni un solo objeto del script `01_identidad.sql` o `99_seguridad_v2.sql`. La base de datos sigue operando exactamente como fue diseñada en la Fase V3.9.

## 19. Criterios de aceptación
- [ ] Login funciona sin contexto multi-tenant.
- [ ] No es posible realizar un `SELECT *` arbitrario (Mínimo Privilegio).
- [ ] Cross-tenant data exfiltration es imposible mediante este mecanismo.
- [ ] BCrypt se valida 100% del lado del backend.
- [ ] El script V3.9 se mantiene inalterado.
- [ ] Trazabilidad de seguridad nativa asegurada vía auditoría PL/SQL.

---
**STATUS**: READY FOR IMPLEMENTATION
