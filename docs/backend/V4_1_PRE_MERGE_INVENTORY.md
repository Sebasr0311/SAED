# SAED 2.0 — V4.1 PRE-MERGE INVENTORY

## Estado del Repositorio
- Rama actual: `feature/db-v4.1-session-bootstrap` (pre-merge a `main`)
- Baseline de base de datos intacto: `V3.9`

## Estructura Relevante Revisada
- `database/migrations/V4.0__auth_bootstrap.sql`: Mantiene estado aislado y define `PKG_AUTH_BOOTSTRAP` para Authentication Bootstrap.
- `database/migrations/V4.1__core_session_patch.sql`: Define parche de Session Context (Machine State de RLS).
- `database/migrations/V4.1__core_session_patch_ROLLBACK.sql`: Plan de retorno seguro en DB.
- `database/migrations/test_v4_1_session.sql` y `test_v4_1_dml.sql`: Casos de prueba directos sobre Oracle RLS.
- `backend/src/main/java/com/saed/backend/config/`: Contiene Proxy DataSource y ConnectionProxy que administran la inyección/remoción de Contexto.
- `backend/src/test/java/...`: Pruebas de integración asegurando inmutabilidad e intercepción de concurrencia.
- `docs/backend/`: Contiene los diseños aprobados, incluyendo `V4_1_IMPLEMENTATION.md`, `V4_1_SECURITY_TEST_REPORT.md` y `V4_1_ROLLBACK.md`.

## Verificación Visual y Archivos de Build
- `pom.xml`: No presenta dependencias de JPA, Hibernate u ORMs acoplados. Presenta `spring-boot-starter-jdbc` y `ojdbc11`.
- `application.yml` y `.gitignore`: Secretos excluidos de control de versiones. Placeholder local `dGhpcy1...` (usado para pruebas H2).
- V4.1 **cumple exactamente** con lo documentado en `V4_1_CORE_SESSION_PATCH_DESIGN.md`.
