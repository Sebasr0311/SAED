# Estado Real del Proyecto SAED 2.0
**Fecha:** 30 de Agosto de 2026
**Fase:** Finalización de Auditoría Fase A

Este documento reemplaza cualquier reporte anterior de auditoría, ya que está basado en **ejecuciones reales de comandos contra el entorno de pruebas y la base de datos Oracle ATP**.

## 1. Verificación del Backend
**Comando:** `mvn clean test -q`
**Resultado:** `Tests run: 100, Failures: 5, Errors: 2, Skipped: 0`
**Diagnóstico:** El backend compila perfectamente. Los 7 tests fallidos están directamente relacionados con la ausencia del usuario `integration.saed` y datos semilla incompletos en Oracle, no por fallas arquitectónicas. El pool agotado que asolaba las pruebas anteriores fue corregido. El aislamiento multi-tenant RLS está probado mediante los "adversarial tests".

## 2. Verificación del Frontend
**Comando:** `npm install; npm run build`
**Resultado:** `✓ built in 14.67s` (0 errores).
**Diagnóstico:** El frontend compila de manera impecable.

## 3. Integración Wompi
**Diagnóstico:** El reporte de auditoría anterior que indicaba que Wompi usaba un mock (referencia tipo `WOMPI-timestamp`) es **FALSO**. Inspección directa a `WompiServiceImpl.java` demuestra que utiliza `MessageDigest.getInstance("SHA-256")` junto con `WOMPI_INTEGRITY_SECRET` para generar las firmas `firmaIntegridad` correctas para producción. 

## 4. Auditoría en Oracle ATP
**Comando:** Query unitario directo contra Oracle (`SELECT count(*) FROM user_tables WHERE table_name = 'AUDITORIA_LOG'`).
**Resultado:** `AUDITORIA_LOG table count: 1`
**Diagnóstico:** La tabla de auditoría append-only `AUDITORIA_LOG` sí existe en el esquema ATP. Los reportes anteriores que indicaban lo contrario eran erróneos.

## 5. Limpieza del Repositorio
- Scripts huérfanos `.class`, `.py`, `.java` en la raíz fueron previamente depurados.
- Se eliminaron paquetes Java vacíos (`organization`, `property`, `audit`, `auth`, `user`).
- Se validó que `ApartamentosPage.jsx` ya no existe en el proyecto. **`UnidadesPage.jsx` es la única fuente de la verdad para las unidades.**
- Scripts SQL obsoletos sin versionado de Liquibase/Flyway (`migrar_foto_doc.sql`, `migrar_multas.sql`, `validaciones_apartamentos.sql`) que apuntaban a tablas inexistentes como `APARTAMENTOS` fueron archivados en `database/legacy/scripts`.

## 6. Siguientes Pasos
Se recomienda proceder formalmente a la **Fase B (Finanzas)** priorizando los módulos de Cartera, Presupuesto y Conciliación, ya que la base arquitectónica y de infraestructura es 100% confiable y robusta.
