# PHASE 1H - CURRENT STATE AUDIT

## 1. CONTEXTO ACTUAL DEL PROYECTO
- **main**: Sincronizado, limpio, incluye hasta Fase 1G (Paquetes y Correspondencia).
- **develop**: Sincronizado, actua como rama de integracion principal.
- **Oracle RLS**: Validado. La tabla PAQUETES fue asegurada en la Fase 1G. 
- **Plan de Trabajo**: Basado en docs/contexto/PLAN_TRABAJO_GITHUB.txt y los modulos definidos para el frontend (ParqueaderosPage.jsx), el modulo logico consecutivo tras completar Porteria y Paquetes es la **Gestion de Parqueaderos y Asignaciones**.

## 2. DETERMINACION DE LA FASE 1H
- La Fase 1H abarcara la gestion del parque automotor inmovil (Parqueaderos de la copropiedad) y sus asignaciones a las unidades, cubriendo las tablas PARQUEADEROS y ASIGNACIONES_PARQUEADERO.
- Este modulo es operado activamente por la Administracion y consultado por la Porteria. 

## 3. AUDITORIA DE SEGURIDAD (RLS) - TABLAS OBJETIVO
- **PARQUEADEROS**:
  - Posee la politica POL_RLS_PROP_PARQUEADEROS vinculada a FN_FILTRO_PROPIEDAD.
  - Un RESIDENTE actualmente tiene vision de propiedad (FN_FILTRO_PROPIEDAD), lo cual le permite ver todo el catalogo de parqueaderos. Esto es un comportamiento normal ya que el mapa de parqueaderos suele ser de dominio comun.
- **ASIGNACIONES_PARQUEADERO**:
  - Posee la politica POL_RLS_UNI_ASIGNACIONES_PA vinculada a FN_FILTRO_UNIDAD.
  - Esta politica aísla correctamente a los residentes: un RESIDENTE solo puede ver las asignaciones de parqueaderos vinculadas a las unidades a las que pertenece (USUARIO_ASIGNACIONES).
  - No hay fuga de datos de privacidad.

## 4. INCONSISTENCIAS DE MODELADO DETECTADAS
- La tabla PARQUEADEROS cuenta con la columna ASIGNADO_A_UNIDAD. Mantener esta relacion directa ademas de tener la tabla intermedia y mas rica en atributos ASIGNACIONES_PARQUEADERO es redundante y dificulta la atomicidad (anomalias de actualizacion). Ademas, si el Residente consulta PARQUEADEROS, puede inferir asignaciones ajenas, evadiendo la privacidad lograda en ASIGNACIONES_PARQUEADERO.
- **Accion a tomar en Base de Datos**: Crear migracion V4.6__parqueaderos_schema_patch.sql para remover la columna ASIGNADO_A_UNIDAD de la tabla PARQUEADEROS (desacoplando el tenant del catalogo base) delegando las asignaciones integramente a ASIGNACIONES_PARQUEADERO.

## 5. BLOQUEOS
- Ningun bloqueo arquitectonico. Se utilizara la misma pila probada: DTO -> Repository (NamedParameterJdbcTemplate) -> Service -> Controller con Oracle RLS subyacente.
