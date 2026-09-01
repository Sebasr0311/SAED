# SAED 2.0 — Auditoría de Cobertura y Clasificación de Pruebas

**Fecha:** 01 de Septiembre de 2026  
**Plan Maestro:** `Versión 4.0 — Definitiva`  
**Fase:** `Fase 1 — Auditoría Definitiva`  
**Auditor:** Principal QA & Test Automation Specialist  

---

## 1. Clasificación Exhaustiva de los 115 Tests Activos

| Categoría de Prueba | Clases de Prueba | Cantidad de Tests | Motor de Ejecución | Nivel de Evidencia |
| :--- | :--- | :--- | :--- | :--- |
| **Integración con Oracle XE Real** | `Phase1AAuthIntegrationTest` (4), `Phase1BAuthorizationIntegrationTest` (5), `Phase1CTenantIntegrationTest` (7), `Phase1DPersonIntegrationTest` (8), `Phase1EDependentIntegrationTest` (6), `Phase1FPorteriaIntegrationTest` (8), `Phase1GPaquetesIntegrationTest` (5), `Phase1HParqueaderosIntegrationTest` (6), `Phase1IFinanzasIntegrationTest` (3), `Phase1JPortalIntegrationTest` (4), `Phase13WompiIntegrationTest` (4), `AuditoriaAuditTest` (1) | **66 tests** | Base de datos Oracle XE real (`localhost:1521/XEPDB1`) | **E3 (API Real)** |
| **Pruebas Adversariales de RLS / Multi-Tenancy** | `Phase1BAdversarialTest` (2), `Phase1CAdversarialTest` (2), `PhaseBFinanzasAdversarialTest` (4), `PhaseDDocumentosAdversarialTest` (1), `PhaseDIncidentesAdversarialTest` (1), `PhaseDObrasAdversarialTest` (1), `PhaseDPqrsAdversarialTest` (1), `PhaseDReservasAdversarialTest` (1), `PhaseDSancionesAdversarialTest` (1), `AdversarialFoundationTest` (3), `ContextBleedIntegrationTest` (1) | **18 tests** | Inyección de roles y contextos cruzados contra Oracle XE | **E5 (Seguridad Adversarial)** |
| **Pruebas Unitarias con Mocks (Mockito / MockMvc)** | `AuthServiceTest` (5), `AssignmentServiceTest` (4), `AssignmentManagementServiceTest` (4), `OrganizationServiceTest` (5), `PropertyServiceTest` (4), `UnitServiceTest` (5), `JwtAuthenticationFilterTest` (1), `AlertasControllerTest` (1) | **29 tests** | Memoria / Mocks Java | **E2 (Tests Unitarios)** |
| **Scripts de Verificación / Seeds de Prueba** | `CheckAssignmentsTest` (1), `CheckConstraintsTest` (1), `ScriptRunnerTest` (1) | **2 tests** | Ejecución de utilitarios | **E1 (Verificación Script)** |

* **Total de Tests Ejecutados:** **115 tests** (`115 PASSED / 0 FAILED / 0 SKIPPED`).

---

## 2. Brechas de Testing Identificadas

1. **`TEST-001` (P1) — Falta de Tests Adversariales para Ataques E a L:**
   * Aunque existen tests adversariales para Fases B, C y D, falta formalizar la suite automatizada para:
     * *Ataque E:* Cross-tenant POST / Creación de recurso en tenant ajeno.
     * *Ataque F:* Cross-tenant PUT / Mutación de recurso en tenant ajeno.
     * *Ataque G:* Cross-tenant DELETE / Eliminación de recurso ajeno.
     * *Ataque H:* Escalada de privilegios vertical (Residente ➔ Administrador).
     * *Ataque I:* Concurrencia de 50 requests paralelos con tenants cruzados.
2. **`TEST-002` (P2) — Falta de Pruebas End-to-End (E2E) con Playwright:**
   * No existe suite automatizada que levante el frontend en Vite y valide los 4 flujos principales de usuario (Superadmin, Administrador, Portero, Residente) contra la API real.
3. **`TEST-003` (P2) — Tests Script Ad-hoc en el Árbol de Pruebas:**
   * Clases como `CheckAssignmentsTest.java`, `CheckConstraintsTest.java` y `ScriptRunnerTest.java` actúan como scripts de verificación manual con salida a consola en lugar de aserciones automatizadas de regresión.
