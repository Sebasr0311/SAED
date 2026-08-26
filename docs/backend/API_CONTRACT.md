# SAED 2.0 API Contract (Foundation)

## Base URL
`/api/v1`

## Autenticación
### `POST /auth/login`
**Descripción**: Valida credenciales contra `USUARIOS`. Devuelve un Identity JWT y lista de propiedades asignadas.
**Body**:
```json
{
  "email": "user@example.com",
  "password": "secretpassword"
}
```
**Response (200 OK)**:
```json
{
  "token": "eyJhbG... (Identity JWT sin contexto de base de datos)",
  "assignments": [
    { "propertyId": 1, "organizationId": 10, "role": "ADMIN_PROPIEDAD" }
  ]
}
```

## Contexto de Sesión
### `POST /session/context`
**Descripción**: Establece el ámbito (scope) organizacional. Devuelve el Context JWT que permite acceso a tablas transaccionales.
**Headers**: `Authorization: Bearer <Identity JWT>`
**Body**:
```json
{
  "propertyId": 1
}
```
**Response (200 OK)**:
```json
{
  "contextToken": "eyJhbG... (Context JWT que inyecta PKG_SAED_SESSION)",
  "role": "ADMIN_PROPIEDAD"
}
```

### `GET /me`
**Descripción**: Retorna los datos del usuario actual (nombre, email).
**Headers**: `Authorization: Bearer <Context JWT>`

## Errores Estándar (4xx / 5xx)
Cualquier endpoint que falle retornará este formato unificado:
```json
{
  "success": false,
  "code": "ACCESS_DENIED_RLS",
  "message": "Operación bloqueada por la política de seguridad RLS."
}
```
