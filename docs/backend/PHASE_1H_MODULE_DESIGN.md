# PHASE 1H - MODULE DESIGN: PARQUEADEROS Y ASIGNACIONES

## 1. OBJETIVO
Implementar el ciclo de vida de los parqueaderos y sus respectivas asignaciones a unidades residenciales, aislando la privacidad de los residentes y unificando el modelo de datos.

## 2. BASE DE DATOS (MIGRACION V4.6)
**Script**: V4.6__parqueaderos_schema_patch.sql
**Autor**: srusso1
**Acciones**:
- Eliminar la restriccion de clave foranea FK_PARQUEADERO_UNIDAD en PARQUEADEROS.
- Eliminar la columna ASIGNADO_A_UNIDAD de PARQUEADEROS.
- Garantizar que las politicas RLS queden inalteradas, puesto que POL_RLS_PROP_PARQUEADEROS y POL_RLS_UNI_ASIGNACIONES_PA operan exitosamente.

## 3. ARQUITECTURA BACKEND (Sebasr0311)
**Paquete**: com.saed.backend.parqueaderos

### 3.1 DTOs
- ParqueaderoDTO
- ParqueaderoRequestDTO
- AsignacionParqueaderoDTO
- AsignacionParqueaderoRequestDTO

### 3.2 Repositories
- ParqueaderosRepository y AsignacionesParqueaderoRepository utilizando NamedParameterJdbcTemplate.

### 3.3 Services
- ParqueaderosService: Logica CRUD para el catalogo fisico de parqueaderos.
- AsignacionesParqueaderoService: Validacion de fechas, canones y solapamiento de parqueaderos.

### 3.4 Controllers
- GET /api/v1/parqueaderos: (ADMIN, PORTERO)
- POST /api/v1/parqueaderos: (ADMIN)
- PUT /api/v1/parqueaderos/{id}: (ADMIN)
- DELETE /api/v1/parqueaderos/{id}: (ADMIN)

- GET /api/v1/asignaciones-parqueadero: (ADMIN, PORTERO, RESIDENTE - Limitado por RLS automaticamente).
- POST /api/v1/asignaciones-parqueadero: (ADMIN)
- PUT /api/v1/asignaciones-parqueadero/{id}/finalizar: (ADMIN)

## 4. INTEGRACION FRONTEND (AnghelaD)
- Refactorizar ParqueaderosPage.jsx para invocar /api/v1/parqueaderos.
- El payload en ParqueaderosPage.jsx contiene codigo en vez de 
umeroParqueadero y esVisitante. El Backend mapeara esVisitante == true a TIPO = 'VISITANTES'.

## 5. ESTRATEGIA DE PRUEBAS
- Phase1HParqueaderosIntegrationTest.java: 
  - Validara que ADMINISTRADOR pueda crear parqueaderos.
  - Validara que RESIDENTE falle al crear un parqueadero (HTTP 403).
