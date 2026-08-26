# PHASE 1B - API CONTRACT

## 1. GET `/api/v1/auth/assignments`

**Objetivo:** Obtiene la lista de asignaciones vigentes para el usuario autenticado (STATE 1).
**Autenticación:** Requiere Bearer JWT (Fase 1A). No requiere `X-Assignment-Id`.

### Request
```http
GET /api/v1/auth/assignments HTTP/1.1
Authorization: Bearer eyJhbG...
```

### Response (200 OK)
```json
{
  "status": "success",
  "data": [
    {
      "idAsignacion": 101,
      "rol": {
        "codigo": "ADMIN_SAED",
        "alcance": "GLOBAL"
      },
      "organizacion": null,
      "propiedad": null,
      "unidad": null
    },
    {
      "idAsignacion": 102,
      "rol": {
        "codigo": "ADMIN_ORG",
        "alcance": "ORGANIZACION"
      },
      "organizacion": {
        "id": 1,
        "nombre": "Edificios SA"
      },
      "propiedad": null,
      "unidad": null
    },
    {
      "idAsignacion": 103,
      "rol": {
        "codigo": "RESIDENTE",
        "alcance": "UNIDAD"
      },
      "organizacion": {
        "id": 1,
        "nombre": "Edificios SA"
      },
      "propiedad": {
        "id": 5,
        "nombre": "Torres del Norte"
      },
      "unidad": {
        "id": 501,
        "identificador": "Apto 101"
      }
    }
  ]
}
```

## 2. Peticiones de Negocio (Ejemplo)

**Objetivo:** Consultar datos dependientes del contexto. Requiere STATE 2.

### Request
```http
GET /api/v1/propiedades HTTP/1.1
Authorization: Bearer eyJhbG...
X-Assignment-Id: 102
```

### Response (200 OK)
Retorna las propiedades de "Edificios SA" (pues `idAsignacion=102` otorga acceso a toda esa organización). RLS se encarga del filtro en BBDD.

### Response (403 Forbidden)
Si el `id_asignacion` no le pertenece al usuario o está inactivo.
```json
{
  "status": "error",
  "message": "Access Denied: Asignación no válida."
}
```
