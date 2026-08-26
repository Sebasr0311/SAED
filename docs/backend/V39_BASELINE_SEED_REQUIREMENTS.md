# REQUISITOS DE DATA SEMILLA PARA SAED V3.9

Para que una instalación limpia del baseline `V3.9__baseline_multitenant.sql` y sus migraciones subsecuentes sea funcional, se requiere la inserción de **data semilla (Seed Data)**.

La extracción de V3.9 es estructural (DDL) y no contiene datos comerciales ni paramétricos por diseño.

## Tablas que Requieren Data Semilla

1. **`ROLES`**
   - **Propósito:** El sistema opera bajo un modelo RBAC. Los roles (`ADMIN_SAED`, `ADMIN_ORG`, `ADMIN_PROPIEDAD`, `SUPERVISOR`, `RESIDENTE`, etc.) deben existir para que el módulo de Autorización (Fase 1B) y `USUARIO_ASIGNACIONES` puedan operar.
   - **Dependencias:** Ninguna (Tabla maestra).
   - **Nota:** La instancia `SAED_V39_FINAL_TEST` cuenta con 9 roles registrados.

2. **`ROL_PERMISO`** (Opcional pero Recomendado)
   - **Propósito:** Mapeo de permisos (scopes) granulares por rol.
   - **Dependencias:** Requiere que los `ROLES` estén creados.

3. **Parámetros del Sistema (Si Existen)**
   - Cualquier tabla de catálogo global como `TIPOS_DOCUMENTO`, `ESTADOS_...` que no utilice restricciones de tipo estáticas sino tablas paramétricas.
   
4. **Organización Administrativa Base (Para Root Tenant)**
   - Si se requiere un "Tenant 0" para que los superadministradores gestionen la plataforma, se debe registrar en `ORGANIZACIONES` (y opcionalmente crear un `USUARIO` inicial con rol de sistema).

## Instrucciones de Implementación Futura

- **NO** modificar `V3.9__baseline_multitenant.sql` para agregar inserts.
- Se debe crear un script específico de seed data (ej. `V3.9.1__seed_data.sql` o vía herramienta de Flyway/Liquibase) que inyecte esta configuración.
- El script de seed data deberá ejecutarse justo después del baseline, o después de V4.1, asegurando que se inyecte con un contexto administrativo válido usando `PKG_SAED_SESSION`.
