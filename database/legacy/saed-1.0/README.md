# SAED 1.0 Legacy Scripts

Este directorio contiene los scripts originales de la arquitectura de SAED 1.0.

**ATENCIÓN:**
- Estos scripts **NO representan el baseline oficial de SAED 2.0**.
- El diseño de base de datos contenido aquí corresponde a una arquitectura monolítica antigua que carece de soporte multi-tenant real (ausencia de tablas como `ORGANIZACIONES`, `PROPIEDADES`, y de un modelo robusto en `USUARIO_ASIGNACIONES`).
- Se conservan en este directorio **únicamente por motivos de trazabilidad histórica**.
- **NO MODIFICAR NI EJECUTAR** estos scripts en entornos de SAED 2.0.

El baseline oficial y real de SAED 2.0 se encuentra en:
`database/migrations/V3.9__baseline_multitenant.sql`
