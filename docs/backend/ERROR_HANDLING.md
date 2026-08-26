# SAED 2.0 Error Handling

## Global Exception Handler (`@ControllerAdvice`)
Los errores no se devolverán directamente al cliente para evitar la exposición del diseño interno (stack traces, tablas, columnas de Oracle).
Se centralizan en la clase `GlobalExceptionHandler`.

## Mapeo Específico de Oracle (RLS/VPD)
La base de datos SAED V3.9 está altamente protegida por RLS.
Cuando un usuario intenta modificar o insertar un registro que su contexto RLS no autoriza, Oracle lanza:

`ORA-28115: policy with check option violation`

El backend capturará `DataAccessException` y verificará el código SQL. Si detecta el error 28115, la API responderá un HTTP `403 FORBIDDEN` limpio:
```json
{
  "success": false,
  "code": "ACCESS_DENIED",
  "message": "Operación bloqueada por la capa de seguridad. Carece de permisos sobre el tenant o recurso solicitado."
}
```

## Estructura Uniforme
Todo error HTTP 4xx o 5xx respetará el mismo esquema DTO:
`ErrorResponse(success: boolean, code: String, message: String)`
