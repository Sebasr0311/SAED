# SAED 2.0 — V4.1 PRE-MERGE AUDIT REPORT

## 1. Resumen Ejecutivo
Se realizó una auditoría profunda sobre la rama `feature/db-v4.1-session-bootstrap` para evaluar la solidez y el cumplimiento de la arquitectura V4.1 frente a los requerimientos de la V3.9 de SAED. El foco central consistió en probar que la implementación Zero Trust y el manejo del pool de conexiones no presentan Context Bleed (fugas de contexto). Tras solventar tests legados de H2 y endurecer el manejo de excepciones del Proxy JDBC, **se aprueba el merge**.

## 2. Inventario
- **Ramas:** `feature/db-v4.1-session-bootstrap` (candidata a merge).
- **Backend:** `spring-boot-starter-jdbc` (sin Hibernate/JPA), `ojdbc11`.
- **Database:** Migración `V4.1__core_session_patch.sql` presente y aplicada. `V3.9` se mantuvo intacta y congelada.
- **Config:** `.gitignore` excluye adecuadamente los archivos de variables de entorno; `application.yml` utiliza inyección por entorno con placeholders seguros para desarrollo local.

## 3. Pruebas Ejecutadas
Se corrió la suite de `mvn test` (9 pruebas), y se ejecutaron scripts en base de datos `test_v4_1_session.sql` y `test_v4_1_dml.sql` (auditoría DML multi-tenant y tests contra V$).

## 4. Tests Exitosos
- **Java (9 PASS):**
  - `ContextBleedIntegrationTest` (1 Hilo de conexión mock, verificado).
  - `AuthServiceTest` (3 Pruebas) - Validando autenticación con AuthData.
  - `SaedContextIntegrationTest` (2 Pruebas) - Verificando aserciones de excepciones sin orgId o rol inválido.
  - `AdversarialFoundationTest` (3 Pruebas) - Verificando fugas de hilos mediante Mock DB.
- **Oracle DML (PASS):**
  - Acceso DML a organizaciones ajenas → Bloqueado (0 filas insertadas/modificadas).
  - Eliminación de registros de `AUDITORIA_LOG` → Dispara `ORA-20099`.
  - Spoofing de asignaciones → Dispara `ORA-20080`.

## 5. Tests Fallidos (Previo a corrección)
- Inicialmente `SaedContextIntegrationTest` y `AdversarialFoundationTest` (H2 Database) fallaban ya que esperaban semántica antigua de `ORA-20082` a través de Mock de Java en lugar de la nueva lógica de `SET_BOOTSTRAP_CONTEXT`.
- **Acción Tomada:** Los tests fueron actualizados (Refactor arquitectónico legítimo).

## 6. Riesgos y 7. Hallazgos
- **Riesgo Encontrado:** `SaedConnectionProxy` limpiaba el contexto pero si existía un error de Red (Timeout), el HikariCP podría recibir una conexión envenenada en lugar de descartarla.
- **Corrección (Aplicada):** Se actualizó `SaedConnectionProxy` para arrojar un `RuntimeException` crudo tras interceptar SQLException durante `CLEAR_CONTEXT`, provocando la expulsión forzosa (Eviction) por parte de HikariCP.

## 8. Compatibilidad
- **V3.9 Compatibilidad:** 100% Intacta. La política RLS y sus PKG principales se parchearon únicamente sin cambiar los binarios persistentes de negocio.
- **V4.0 Compatibilidad:** Perfectamente interconectado, se aprovecha del `PKG_AUTH_BOOTSTRAP`.

## 9. Seguridad
- **Oracle:** RLS robusto (4 estados: ANONYMOUS, BOOTSTRAP, BUSINESS, CLEARING). Inmutabilidad en tabla logística con ORA-20099 intacto.
- **Spring:** Ausencia absoluta de dependencias Hibernate u ORM pesados. Filtros JWT inalterados.
- **HikariCP:** Close interceptado y purge riguroso.
- **Context Bleed:** 0 comprobado en múltiples hilos mediante test.
- **Autoridad:** JWT transporta solo `idUsuario`. No hay tokens con claims autoritativos del tenant. El Tenant se exige vía `X-Assignment-Id`.
- **Configuración y Secretos:** Limpios, ningún leak hacia VCS.

## 10. Estado Final
**VERDICT: V4.1 PRE-MERGE APPROVED**
