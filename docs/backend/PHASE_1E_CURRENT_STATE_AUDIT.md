# PHASE 1E CURRENT STATE AUDIT

## 1. FUENTE DE VERDAD APLICABLE
La fase 1E implementa el registro de dependientes y asociados externos: Mascotas, Vehículos, Visitantes y Tutores, extendiendo la arquitectura de Personas implementada en la Fase 1D.
La base de datos se rige por V3.9 con parches hasta V4.3.

## 2. ESQUEMA DE MASCOTAS
- **Tabla:** MASCOTAS
- **PK:** ID_MASCOTA
- **Campos críticos:** ID_UNIDAD, ID_PERSONA_RESPONSABLE, NOMBRE, ESPECIE
- **Restricciones Not Null:** ID_UNIDAD, ID_PERSONA_RESPONSABLE, NOMBRE, ESPECIE, GENERO, ES_RAZA_MANEJO_ESPECIAL, ESTADO
- **RLS:** FN_FILTRO_UNIDAD. Requiere ID_UNIDAD vlido en el contexto de la organizacin.

## 3. ESQUEMA DE VEHICULOS
- **Tabla:** VEHICULOS
- **PK:** ID_VEHICULO
- **Campos críticos:** ID_PERSONA, ID_UNIDAD (Nullable), PLACA, TIPO_VEHICULO
- **Restricciones Not Null:** ID_PERSONA, PLACA, TIPO_VEHICULO, ESTADO
- **RLS:** FN_FILTRO_UNIDAD. NOTA: Si ID_UNIDAD es NULL, el vehículo será invisible a menos que FN_FILTRO_UNIDAD se parchee, pero en V4.3 VEHICULOS usa id_unidad IN ..., por lo que **obligatoriamente** debe proveerse ID_UNIDAD para que la fila sea leíble.

## 4. ESQUEMA DE VISITANTES
- **Tabla:** VISITANTES
- **PK:** ID_VISITANTE
- **Campos críticos:** ID_PERSONA
- **Restricciones Not Null:** ID_PERSONA, ES_FRECUENTE, ESTADO
- **RLS:** FN_FILTRO_PROPIEDAD. En V4.3 se adaptó a: id_visitante IN (SELECT id_visitante FROM VISITAS ... ). Esto significa que un VISITANTE recién creado, que aún no tiene VISITAS, **NO será visible** para SELECT. Esto es un problema clásico de Catch-22 para la creación y gestión.

## 5. ESQUEMA DE TUTORES
- **Tabla:** TUTORES
- **PK:** ID_TUTOR
- **Campos críticos:** ID_PERSONA_MENOR, ID_PERSONA_TUTOR, PARENTESCO
- **Restricciones Not Null:** ID_PERSONA_MENOR, ID_PERSONA_TUTOR, PARENTESCO, ESTADO, FECHA_REGISTRO
- **RLS:** FN_FILTRO_UNIDAD. En V4.3 se adaptó a id_persona_menor IN (SELECT id_persona FROM RESIDENTES_UNIDAD ...). Para que un Tutor sea visible, el menor debe ser ya residente de la unidad.

## 6. VULNERABILIDAD RLS Y CATCH-22
Existen dos problemas lógicos detectados en el esquema RLS actual para Fase 1E:
1. **VISITANTES:** Si creo un visitante y no le registro una visita inmediatamente, no podré leer el registro creado para asociarlo a una visita posterior. La política de UPDATE fallará porque update_check evalúa si el registro resultante es visible. 
2. **VEHICULOS:** Aunque ID_UNIDAD permite NULL, el RLS de V3.9 le aplica FN_FILTRO_UNIDAD (que filtra id_unidad IN ...). Por ende, los vehículos con ID_UNIDAD = NULL desaparecen del radar para los usuarios regulares, quedando huérfanos.

## 7. RESOLUCIÓN PROPUESTA (V4.4)
Se requiere una migración V4.4 para corregir el comportamiento de RLS en estas tablas.
- **VISITANTES:** Debería ser visible si ID_PERSONA pertenece a PROPIETARIOS_UNIDAD, RESIDENTES_UNIDAD, o bien si el visitante tiene una VISITA en la propiedad. Mejor aún, las personas ya son compartidas a nivel de unidad/propiedad, el perfil de Visitante podría depender de la autorización de creación.
- Alternativamente, si no se altera V4.3, la UI debe forzar la creación atómica (Visitante + Visita), y en Vehículos forzar ID_UNIDAD Not Null en el DTO.
Dado que la orden prohíbe alterar migraciones anteriores y requiere crear una nueva si hay problemas irreversibles, evaluaremos en el diseño si podemos vivir con esto (forzando ID_UNIDAD en DTO, y permitiendo que VISITANTES sólo se lean vía la tabla PERSONAS hasta que tengan visita).

