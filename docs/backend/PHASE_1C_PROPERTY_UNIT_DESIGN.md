# PHASE 1C PROPERTY & UNIT DESIGN

## PROPIEDADES (PROPERTIES)
### Entidad
Mapea a la tabla PROPIEDADES.

### Reglas
- FK estricta a ID_ORGANIZACION. Al crear una propiedad, el idOrganizacion suministrado en el payload se validará contra el ID_ORGANIZACION del contexto del token actual (X-Assignment-Id).
- Si el contexto es ORGANIZACION = 1, la petición POST de propiedad forzosamente asumirá ID_ORGANIZACION = 1. Se **ignora** cualquier ID de organización inyectado en el JSON para prevenir spoofing (Anti-Spoofing).
- Restricción Check: TIPO_OCUPACION_PREDOMINANTE y ESTADO.

## UNIDADES (UNITS)
### Entidad
Mapea a la tabla UNIDADES.

### Reglas
- FK a ID_PROPIEDAD.
- La creación de unidades se hace siempre bajo una propiedad validada.
- Oracle RLS impedirá consultar o afectar unidades que no pertenezcan a la propiedad u organización activa del contexto de sesión.
