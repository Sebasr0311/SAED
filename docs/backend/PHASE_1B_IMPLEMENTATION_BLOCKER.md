# PHASE 1B IMPLEMENTATION BLOCKER

## 1. Hallazgo
Las pruebas de integración requeridas (incluyendo las creadas en Fase 1A) fallan al ejecutarse contra la base de datos real Oracle (`SAED_V39_FINAL_TEST`). El error arrojado es `InvalidCredentialsException` en los tests de autenticación y falla en la aserción de `X-Assignment-Id` en los tests de seguridad de Fase 1B.

## 2. Evidencia
Al ejecutar `mvn test`, las pruebas `Phase1AAuthIntegrationTest` y `Phase1BAdversarialTest` (al intentar generar tokens para usuarios) devuelven excepciones. Oracle devuelve 0 filas porque los usuarios simulados (`integration@saed.com`, `id_usuario = 1`) **NO existen** en la base de datos V3.9 recientemente recuperada.

## 3. Impacto
Las reglas de la iteración estipulan explícitamente:
- *"Las pruebas de seguridad que dependan de Oracle RLS deben ejecutarse contra Oracle real, no simularse únicamente con H2."*
- *"No considerar terminada la Fase 1B hasta tener pruebas reales."*

Debido a la ausencia de datos semilla (Usuarios, Organizaciones, Asignaciones), es arquitectónicamente imposible validar el motor RLS y las políticas de la Fase 1B en el entorno de pruebas de integración automatizado sin inyectar datos de prueba en la base de datos.

## 4. Fuente de Verdad Afectada
`SAED_V39_FINAL_TEST` (Base de datos local) / Migraciones V3.9 a V4.1.

## 5. Opciones Posibles
- **Opción A:** Crear una migración `V4.2__test_seed_data.sql` que inyecte registros falsos (`USUARIOS`, `ORGANIZACIONES`, `USUARIO_ASIGNACIONES`) solo para los perfiles de test.
- **Opción B:** Ignorar las pruebas de integración temporalmente y depender únicamente de pruebas unitarias (`Mockito`).
- **Opción C:** Modificar el `application-test.yml` para usar H2, desactivando la regla de usar Oracle real (contradice los requisitos).

## 6. Recomendación
Recomiendo la **Opción A**, adaptada para Spring Boot. Se debe crear un script `src/test/resources/data-test.sql` que Spring ejecute automáticamente al levantar el contexto de prueba, inyectando el seed data necesario en la instancia Oracle.

## 7. Autorización Adicional Necesaria
Solicito autorización para:
1. Crear scripts de Seed Data para pruebas de integración.
2. Definir si estos scripts irán como migraciones oficiales (Flyway) o como inicialización exclusiva de los perfiles de test en Spring Boot (`data-test.sql`).
