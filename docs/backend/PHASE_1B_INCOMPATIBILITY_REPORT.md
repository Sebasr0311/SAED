# INCOMPATIBILITY FOUND: SAED 2.0 PHASE 1B

## Causa
Existe una grave contradicción entre la documentación arquitectónica/migraciones y el código fuente real del Baseline V3.9:
- Los documentos de diseño (`PHASE_1_IDENTITY_ACCESS_ORG_DESIGN.md`, `PHASE_1_CURRENT_STATE_AUDIT.md`) y las migraciones recientes (`V4.0__auth_bootstrap.sql`, `V4.1__core_session_patch.sql`) asumen y consultan la existencia de tablas multi-tenant como `ORGANIZACIONES`, `PROPIEDADES`, `ROLES`, `MEMBRESIAS` y especialmente `USUARIO_ASIGNACIONES`.
- Sin embargo, los scripts oficiales del baseline en el repositorio (`database/schema/script_oracle.sql`, `modelo_relacional_v4.sql`) corresponden al esquema monolítico de SAED 1.0. **Dichas tablas no existen en el código fuente de V3.9**. En V3.9, la tabla `USUARIOS` tiene una columna plana `rol VARCHAR2(20)`, y el sistema es single-tenant.

## Impacto
**Crítico / Bloqueante**. Es imposible diseñar o implementar la Resolución de Organizaciones, Propiedades y el Context State Machine de la Fase 1B, ya que el modelo de datos subyacente (`USUARIO_ASIGNACIONES`) es un "fantasma" que no existe en el repositorio oficial. Si el sistema se despliega en un entorno limpio usando los scripts del repositorio, fallará catastróficamente con `ORA-00942: table or view does not exist`.

## Componente Afectado
- **Database Baseline (V3.9)**: Desincronizado con la realidad de SAED 2.0.
- **Migraciones V4.0 y V4.1**: Dependen de objetos inexistentes en el esquema base.
- **Fase 1B (Autorización y Contexto)**: Totalmente bloqueada.

## Posibles Soluciones
1. **Actualizar el Baseline:** Actualizar los archivos en `database/schema/` para que reflejen el verdadero esquema V3.9 que contiene las tablas multi-tenant (si es que ya fueron creadas en la base de datos física fuera del control de versiones).
2. **Crear Migración Estructural:** Si V3.9 es verdaderamente el esquema de SAED 1.0, se requiere crear una nueva migración (ej. `V4.2__saed2_multitenant_schema.sql`) que introduzca `ORGANIZACIONES`, `PROPIEDADES`, `ROLES`, `PERMISOS` y `USUARIO_ASIGNACIONES`, y migre los datos de la columna `rol` antigua al nuevo modelo.

## Riesgos
- **Falso Positivo en Pruebas:** Las pruebas `mvn clean test` de la Fase 1A pasaron posiblemente porque la base de datos física de desarrollo (`SAED_V39_FINAL_TEST`) fue alterada manualmente sin commitear los scripts DDL al repositorio, o debido a configuraciones de test que ocultan la carencia estructural. El repositorio Git ha perdido la fuente de la verdad.
- Diseñar sobre tablas inexistentes generará deuda técnica masiva y código inoperable.

## ¿Requiere Cambio en Base de Datos?
**DATABASE CHANGE REQUIRED**
