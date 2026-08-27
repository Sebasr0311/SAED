# PHASE 1D API CONTRACT

## 1. Personas (`/api/v1/personas`)

### `GET /api/v1/personas`
Retrieves paginated people within the active RLS context.
**Response**:
```json
{
  "data": [
    {
      "id": 1,
      "tipoDocumentoId": 1,
      "numeroDocumento": "1234567890",
      "tipoPersona": "NATURAL",
      "primerNombre": "Juan",
      "primerApellido": "Perez",
      "email": "juan@example.com",
      "estado": "ACTIVO"
    }
  ],
  "meta": { "total": 1, "page": 0, "size": 10 }
}
```

### `POST /api/v1/personas`
Creates a new `PERSONA`.
**Request**:
```json
{
  "tipoDocumentoId": 1,
  "numeroDocumento": "1234567890",
  "tipoPersona": "NATURAL",
  "primerNombre": "Juan",
  "segundoNombre": "D",
  "primerApellido": "Perez",
  "segundoApellido": "G",
  "email": "juan@example.com",
  "telefono": "+573001234567"
}
```

## 2. Unit Inhabitants (`/api/v1/units/{unitId}`)

### `GET /api/v1/units/{unitId}/owners`
### `GET /api/v1/units/{unitId}/residents`
Retrieves the list of owners/residents for a unit.
**Response**:
```json
{
  "data": [
    {
      "id": 1,
      "persona": { "id": 1, "primerNombre": "Juan", "primerApellido": "Perez" },
      "porcentajePropiedad": 100.0,
      "esPrincipal": "S",
      "fechaInicio": "2026-08-01",
      "estado": "ACTIVO"
    }
  ]
}
```

### `POST /api/v1/units/{unitId}/owners`
Assigns a person as an owner of the unit.
**Request**:
```json
{
  "personaId": 1,
  "porcentajePropiedad": 100.0,
  "esPrincipal": "S"
}
```

### `POST /api/v1/units/{unitId}/residents`
Assigns a person as a resident of the unit.
**Request**:
```json
{
  "personaId": 1,
  "tipoResidente": "ARRENDATARIO"
}
```
