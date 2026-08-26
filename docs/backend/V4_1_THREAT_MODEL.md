# SAED 2.0 — V4.1 THREAT MODEL

## Matriz de Amenazas

| Amenaza | Vector de Ataque | Mecanismo de Defensa en V4.1 | Riesgo Residual |
|:---|:---|:---|:---|
| **Exfiltración Masiva de Usuarios** | Un atacante autenticado en `BOOTSTRAP` ejecuta `SELECT * FROM USUARIOS`. | `FN_FILTRO_USUARIOS` inyecta la cláusula `id_usuario = (mi_id)`. Solo retorna 1 fila. | Bajo |
| **Exfiltración de Asignaciones Ajenas** | Un atacante autenticado en `BOOTSTRAP` hace scraping a `/api/v1/me/contexts`. | `FN_FILTRO_ASIGNACION` inyecta la cláusula `id_usuario = (mi_id)`. Solo retorna las suyas. | Bajo |
| **Cross-Tenant Business Access** | Un atacante en `BOOTSTRAP` intenta leer `PROPIEDADES`. | Las tablas de negocio no son parcheadas para `BOOTSTRAP`. Al estar la Organización Nula, retorna `1=0`. | Nulo |
| **Escalamiento a Superadmin** | Inyección de `rol_codigo = 'SUPERADMIN'` en el Request Header al llamar a `SET_CONTEXT`. | `SET_CONTEXT` validará físicamente el registro en `ADMINISTRADORES_SAED`. Si falla, arroja `ORA-20083`. | Nulo |
| **Falsificación de Contexto Válido** | Enviar `id_asignacion` válido pero ajeno (Insecure Direct Object Reference). | Spring obtendrá la asignación usando JDBC filtrado por su sesión RLS activa en `BOOTSTRAP`. No podrá ver la asignación ajena. | Nulo |
| **Context Bleed (Filtración entre Threads)** | Un hilo (Thread A) reutiliza la conexión que Thread B dejó en estado `BUSINESS`. | Spring llama a `CLEAR_CONTEXT()` en el bloque `finally` de `SaedConnectionProxy`, llevando el estado a `NONE`. | Muy Bajo (depende de correcta implementación Java). |
