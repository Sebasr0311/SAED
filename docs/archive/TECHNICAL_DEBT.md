# SAED 2.0 — Matriz Oficial de Deuda Técnica y Oportunidades de Refactorización

**Fecha:** 01 de Septiembre de 2026  
**Plan Maestro:** `Versión 4.0 — Definitiva`  
**Fase:** `Fase 1 — Auditoría Definitiva`  

---

## 1. Deuda Técnica Clasificada por Categorías

| ID | Categoría | Severidad | Módulo / Archivo | Descripción de la Deuda | Impacto | Estrategia de Resolución |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TECH-001** | `Base de Datos / Migraciones` | **P1** | `database/migrations/` | 17 parches SQL con versiones superpuestas y scripts de rollback. | Impide despliegue automático limpio con Flyway en entornos nuevos. | Consolidar en `database/schema/V5.0__master_baseline.sql` en Fase 4. |
| **TECH-002** | `Arquitectura Backend` | **P2** | 15 Controladores REST | Inyección directa de `NamedParameterJdbcTemplate` dentro de controladores. | Violación de separación de capas; mezcla HTTP con SQL. | Extraer la lógica a Services y Repositorios en Fase 2 y Fase 34. |
| **TECH-003** | `Código Legacy SAED 1.0` | **P3** | `backend_legacy/` y `database/legacy/` | 164 archivos de código y DDLs antiguos conservados en el repo. | Ruido en búsquedas de texto y confusión en onboarding. | Mover a un repositorio de archivo o rama de historial en Fase 42. |
| **TECH-004** | `Logging y Observabilidad` | **P2** | `SaedDataSourceProxy.java` | Uso de `System.out.println` para imprimir contexto en cada request. | Ruido en logs y potencial fuga de metadatos en consola. | Reemplazar con logger estructurado en nivel `DEBUG` en Fase 37. |
| **TECH-005** | `Frontend Bundle Size` | **P2** | `frontend/src/pages/` | Importación estática de librerías pesadas (`xlsx` 627 kB). | Aumento del tiempo de carga inicial de la aplicación. | Implementar dynamic imports (`React.lazy` y `import('xlsx')`) en Fase 33. |
| **TECH-006** | `Auditoría de Dominio` | **P2** | Operaciones de Negocio | Mutaciones estándar no disparan `SP_REGISTRAR_AUDITORIA`. | Trazabilidad incompleta para auditoría forense de cambios. | Implementar interceptor o AOP de auditoría en Fase 5. |
| **TECH-007** | `Ad-hoc Test Scripts` | **P3** | `backend/src/test/` | Scripts sueltos (`CheckAssignmentsTest`, `CheckConstraintsTest`). | Pruebas que solo imprimen a consola sin aserciones formales. | Convertir en asserts rigurosos o eliminar en Fase 38. |
