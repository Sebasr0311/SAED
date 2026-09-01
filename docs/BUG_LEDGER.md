# SAED 2.0 — Matriz Oficial de Bugs y Deuda Técnica (Bug Ledger)

**Fecha:** 31 de Agosto de 2026  
**Plan Maestro:** `Versión 4.0 — Definitiva`  
**Criterio:** Ningún P0 o P1 puede permanecer abierto para declarar SAED 2.0 en producción.

---

## 1. Registro de Hallazgos y Deuda Técnica

| ID | Módulo | Capa | Severidad | Descripción del Problema | Causa Raíz | Solución Aplicada / Planificada | Test de Regresión | Estado | Commit |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **BUG-0001** | `Frontend API` | Frontend | **P0** | Descarte de cabeceras en peticiones de mutación (`post`, `put`, `patch`, `del`). | `api.js` no propagaba el objeto `options`. | Se propagaron cabeceras (`X-Assignment-Id`, `Authorization`) en todos los métodos y se agregó alias `delete`. | Build y pruebas de cliente | `VERIFICADO` | `49163cb` |
| **BUG-0002** | `UI DataTable` | Frontend | **P1** | Incompatibilidad de propiedades en `<DataTable>` (`rows` vs `data`, `key` vs `accessorKey`). | Falta de soporte de contratos alternativos en tabla universal. | Se normalizó la lectura de `rows/data` y campos de columnas en `DataTable.jsx`. | Build de 39 páginas | `VERIFICADO` | `49163cb` |
| **BUG-0003** | `Oracle Identity`| BD / Tests | **P1** | Colisión de secuencias Identity (`ORA-00001: PK_PERSONAS`) al correr suite completa. | `START WITH 1000` rebasado por semillas de pruebas previas. | Se configuró `START WITH LIMIT VALUE` en setup de tests y `ScriptRunnerTest`. | `Phase1DPersonIntegrationTest` | `VERIFICADO` | `8ead883` |
| **BUG-0004** | `Seguridad` | Repositorio | **P0** | Archivos sueltos con credenciales locales (`Parche.java`). | Scripts temporales generados fuera del flujo de configuración. | Eliminado del repositorio y excluido en `.gitignore`. Secret scan limpio en `src/main`. | Secret scan git | `VERIFICADO` | `e1b6933` |
| **BUG-0005** | `DataSource` | Backend | **P2** | Salidas en consola en `SaedDataSourceProxy` mostrando IDs de contexto. | Salidas `System.out` sin control de niveles de log. | Migrar a `log.debug(...)` con datos minimizados en Fase 34. | `ContextBleedIntegrationTest` | `EN PROGRESO` | — |
| **BUG-0006** | `Wompi Webhook`| Backend | **P1** | Manejo de idempotencia y trazabilidad de pagos Wompi bajo sandbox real. | Flujo probado con mock pero requiere validación contra sandbox oficial. | Implementar suite E2E de sandbox Wompi en Fase 7 con validación de HMAC y montos. | `Phase13WompiIntegrationTest` | `EN PROGRESO` | — |
| **BUG-0007** | `Automatizaciones`| Backend / BD | **P2** | Motor de automatizaciones pendiente de implementación completa (Evento -> Condición -> Acción). | Dominio de Fase 30 pendiente según roadmap maestro. | Desarrollar motor en Fase 30 (`REGLAS_AUTOMATIZACION`, scheduler). | Test de motor de reglas | `PENDIENTE` | — |
| **BUG-0008** | `Consumos` | Backend / BD | **P2** | Módulo de servicios públicos (`MEDICIONES_CONSUMO`) pendiente de API y Frontend. | Dominio de Fase 8/28 pendiente de conexión completa. | Desarrollar API y dashboard de consumos en Fase 28. | Tests de lecturas de consumo | `PENDIENTE` | — |

---

## 2. Resumen de Severidades

| Severidad | Abiertos | En Progreso | Resueltos / Verificados |
| :--- | :--- | :--- | :--- |
| **P0 (Bloqueante)** | 0 | 0 | 2 (`BUG-0001`, `BUG-0004`) |
| **P1 (Crítico)** | 0 | 1 (`BUG-0006`) | 2 (`BUG-0002`, `BUG-0003`) |
| **P2 (Importante)** | 2 (`BUG-0007`, `BUG-0008`) | 1 (`BUG-0005`) | 0 |
| **P3 (Mejora)** | 0 | 0 | 0 |
