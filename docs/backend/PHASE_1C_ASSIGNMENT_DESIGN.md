# PHASE 1C ASSIGNMENT DESIGN

## TABLA CORE: USUARIO_ASIGNACIONES
Es el corazón del multi-tenancy.

### Reglas de Negocio en Creación (POST)
- Solo usuarios con roles administrativos superiores pueden crear asignaciones hacia roles inferiores o iguales (Anti-Privilege Escalation).
- Validaciones FK obligatorias según el lcance del rol seleccionado (ROLES.ALCANCE):
  - **ORGANIZACION:** Exige id_organizacion, prohíbe id_propiedad, id_unidad.
  - **PROPIEDAD:** Exige id_organizacion e id_propiedad.
  - **UNIDAD:** Exige id_organizacion, id_propiedad, id_unidad.
- Unique Index (UIX_ASIGNACION_UNICA): Protegido por Oracle. Si Spring intenta crear una asignación duplicada para el mismo usuario, rol y recursos, Oracle lanzará ORA-00001 (Unique Constraint Violated).
- Manejo de fechas: echaFin debe ser null o >= echaInicio (DB: CK_ASIGNACION_FECHAS).

### Cambio de Estado
- Estados: ACTIVA, INACTIVA, VENCIDA, REVOCADA.
- No se borran físicamente (soft delete implícito vía estado INACTIVA o REVOCADA).

## TABLA: USUARIO_PROPIEDADES_ASIGNADAS
- Se usa cuando ROLES.ALCANCE = 'PROPIEDADES_SELECCIONADAS'.
- Spring debe insertar en USUARIO_ASIGNACIONES con id_organizacion y dejar id_propiedad nulo.
- Y subsecuentemente insertar N registros en USUARIO_PROPIEDADES_ASIGNADAS en la misma transacción.
