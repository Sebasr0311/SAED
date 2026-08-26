# SAED 2.0 — V4 AUTH BOOTSTRAP SECURITY REVIEW

## 1. Matriz de Privilegios y Aislamiento

El uso de `EXEMPT ACCESS POLICY` es la directiva más crítica de seguridad en Oracle VPD. Su asignación debe estar estrictamente aislada.

| PRINCIPAL | OBJECT | PRIVILEGE | REASON | RISK |
| :--- | :--- | :--- | :--- | :--- |
| `SAED_SEC_MASTER` | `SYSTEM` | `EXEMPT ACCESS POLICY` | Requerido para que los procedimientos `DEFINER` salten el RLS y permitan el bootstrap del login. | **CRÍTICO**. Riesgo de salto RLS masivo. Mitigaciones: Cuenta bloqueada en BD (`ACCOUNT LOCK`), sin autenticación por contraseña (`NO AUTHENTICATION`), uso exclusivo para albergar packages. Nunca conectar aplicaciones a este usuario. |
| `SAED_SEC_MASTER` | Tablas de Negocio (`SAED_V39...`) | `SELECT`, `UPDATE`, `INSERT` | Necesario para buscar el hash, registrar intentos y auditar. | Medio. Controlado por el código inmutable de sus packages. |
| `SAED_APP` (Spring) | `SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP` | `EXECUTE` | Permitir al pool de conexiones invocar la validación de credenciales. | Bajo. Superficie limitada a las firmas de los procedimientos. |
| `SAED_APP` (Spring) | `USUARIOS` | NINGUNO adicional | `SAED_APP` jamás recibe `EXEMPT ACCESS POLICY`. Mantiene su RLS ordinario (bloqueo total sin contexto). | Cero. |
| `SAED_APP` (Spring) | `SYSTEM` | NINGUNO | El usuario que ejecuta el pool no administra la DB. | Cero. |

## 2. Definición Restrictiva de `PKG_AUTH_BOOTSTRAP`

El paquete operará con `AUTHID DEFINER` bajo el esquema `SAED_SEC_MASTER`. 
No existirán operaciones genéricas (`GET_USER`, `LIST_USERS`, `GET_ORGANIZATION`).

### Procedimiento A: `GET_AUTH_DATA`
- **Parámetros**: `p_email (IN)`, `p_id_usuario (OUT)`, `p_hash (OUT)`, `p_estado (OUT)`, `p_intentos (OUT)`.
- **Acceso**: `SELECT` exacto por email sobre `USUARIOS`.
- **Riesgo y Prevención**: 
  - *User Enumeration*: Oracle devolverá un código de estado si no existe (o null/vacío). Spring Boot será el encargado de ejecutar un cálculo BCrypt falso en memoria para nivelar los tiempos de respuesta e impedir ataques de *timing*.
  - *Hash Harvesting*: Imposible, ya que la consulta requiere proveer el email exacto uno por uno. No hay comodines (`LIKE`), ni búsquedas masivas.

### Procedimiento B: `GET_ASSIGNMENT_CONTEXT`
- **Parámetros**: `p_id_usuario (IN)`, `p_id_asignacion (IN)`, `p_org_id (OUT)`, `p_prop_id (OUT)`, `p_unidad_id (OUT)`, `p_rol_codigo (OUT)`.
- **Acceso**: `SELECT` exacto cruzando `USUARIO_ASIGNACIONES` y `ROLES`.
- **Validaciones**:
  1. `id_asignacion` existe en BD.
  2. Pertenece estricta y únicamente a `p_id_usuario` (identidad ya validada por JWT).
  3. `estado = 'ACTIVA'`.
  4. La organización, propiedad y/o unidad asociadas siguen existiendo y están activas.
- **Riesgo y Prevención**: Previene que un usuario con JWT válido reclame un contexto que no le pertenece (escalamiento vertical u horizontal).

## 3. Separación de Responsabilidades (Rate Limiting y Auditoría)

- **Backend (Spring Boot)**:
  - Maneja el *Rate Limiting* per-IP y per-User en memoria (Redis/Filtro HTTP).
  - Mitiga *Timing Attacks* en endpoints de Login.
  - Ejecuta `BCryptPasswordEncoder.matches()` asegurando que la DB no toque texto plano.
- **Oracle (PL/SQL)**:
  - Lookup atómico de Identidad.
  - Actualización de `intentos_fallidos` en la tabla `USUARIOS`.
  - Bloqueo duro del estado de usuario (cambia a `BLOQUEADO`) al llegar al umbral de fallos en DB.

### Uso de PRAGMA AUTONOMOUS_TRANSACTION
Se utilizarán procedimientos internos en el paquete para actualizar `intentos_fallidos` o grabar en `AUDITORIA_LOG`.
- **Comportamiento**: Un fallo en la inserción de auditoría (ej. espacio en disco lleno) se controlará con un bloque `EXCEPTION` interno de PL/SQL que hará logging al sistema operativo, pero **no abortará el inicio de sesión del usuario** si la autenticación ya fue confirmada. El login tiene prioridad sobre la auditoría en la capa transaccional final (para evitar ataques de Denegación de Servicio vía agotamiento de logs).

## 4. Threat Model: Backend Completamente Comprometido

Escenario: El atacante roba las credenciales de base de datos de `SAED_APP` y ejecuta SQL libre.

- **¿Puede hacer `SELECT * FROM USUARIOS`?** NO. `SAED_APP` no tiene `EXEMPT ACCESS POLICY`. El contexto nulo obligará a RLS a retornar 0 filas.
- **¿Puede usar el package para robar hashes?** SÍ, pero uno por uno, adivinando correos electrónicos válidos a ciegas. Es un proceso extremadamente lento y detectable, inviable para volcado masivo.
- **¿Puede saltarse el RLS para ver organizaciones o propiedades?** NO. Sin ejecutar `PKG_SAED_SESSION.SET_CONTEXT` validado, Oracle le negará lectura a todas las tablas protegidas de negocio.
- **¿Puede convertirse en SUPERADMIN?** NO. `SET_CONTEXT` valida independientemente que el ID exista en la tabla física de `ADMINISTRADORES_SAED`. El package `PKG_AUTH_BOOTSTRAP` no expone capacidad de inyectar roles.
- **¿Puede modificar datos?** NO. El paquete PL/SQL es estricto en modo solo-lectura / actualización de estado predefinido. 

El modelo demuestra que el Principio de Mínimo Privilegio resiste la caída total del Backend.

## 5. Diseño de Pruebas Adversariales (A-M)

Las siguientes pruebas se implementarán en la fase de código para garantizar la arquitectura:

- **A/B**: Desde `SAED_APP`, intentar un query directo (`SELECT count(*) FROM USUARIOS / PERSONAS`) sin invocar el proxy -> debe retornar 0 filas (Zero-Trust intacto).
- **C/J**: Intentar `UPDATE USUARIOS SET estado = 'ACTIVO'` directamente -> Bloqueado por RLS o falta de permisos `UPDATE`.
- **D**: Realizar llamadas cíclicas en bucle a `GET_AUTH_DATA` para verificar que es contenido (simulación de harvesting).
- **E**: Llamar a `GET_ASSIGNMENT_CONTEXT` pasando `id_usuario = 1` y un `id_asignacion = 2` (perteneciente al usuario 5). Oracle debe retornar "inválido" u ocultarlo.
- **F/G**: Llamar con `id_asignacion` borrado o `estado='INACTIVA'`. Debe rechazar.
- **I**: Intentar resolver contexto `SUPERADMIN` engañando la resolución del assignment.
- **K**: Inyectar contextos con organizaciones que no corresponden al assignment original cruzando datos.
- **L/M**: Fallar deliberadamente un constraint dentro de un `AUTONOMOUS_TRANSACTION` y confirmar que el login general no colapsa y hace rollback únicamente del audit interno.

---
**STATUS: APPROVED FOR IMPLEMENTATION**
