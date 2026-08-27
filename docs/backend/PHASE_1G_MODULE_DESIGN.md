# PHASE 1G - DISEÑO DEL MÓDULO PAQUETERÍA

## 1. OBJETIVO
Gestionar la recepción, notificación y entrega de paquetes y correspondencia en la portería, garantizando que los residentes únicamente tengan visibilidad de los paquetes dirigidos a su unidad.

## 2. ARQUITECTURA DB
**Tabla**: PAQUETES
**Llaves Foráneas**: ID_PROPIEDAD, ID_PORTERIA, ID_UNIDAD, ID_PERSONA_DESTINATARIO, RECIBIDO_POR_PORTERO, ENTREGADO_A_PERSONA, ENTREGADO_POR_PORTERO.
**Restricción RLS**: Se modificará de FN_FILTRO_PROPIEDAD a FN_FILTRO_UNIDAD. Además, se reforzará FN_FILTRO_UNIDAD para restringir a los roles RESIDENTE únicamente a sus unidades asignadas, protegiendo así toda la información dependiente de la unidad.

## 3. ARQUITECTURA JAVA
**Paquete**: com.saed.backend.paquetes
- **DTOs**: PaqueteDTO, PaqueteRequestDTO, PaqueteEntregaDTO.
- **Repository**: PaquetesRepository (NamedParameterJdbcTemplate).
- **Service**: PaquetesService (Lógica de recepción y entrega).
- **Controller**: PaquetesController (Endpoints CRUD y operaciones de entrega).

## 4. API CONTRACT
- POST /api/v1/paquetes: Registrar nuevo paquete en portería.
- GET /api/v1/paquetes: Listar paquetes (automáticamente filtrado por RLS según el rol).
- GET /api/v1/paquetes/{id}: Detalle del paquete.
- PUT /api/v1/paquetes/{id}: Actualizar info básica.
- POST /api/v1/paquetes/{id}/entrega: Registrar la entrega física del paquete al residente.

## 5. FRONTEND
- **PaquetesAdminPage.jsx**: Consume POST /api/v1/paquetes y POST /api/v1/paquetes/{id}/entrega.
- **ResBuzonPage.jsx**: Consume GET /api/v1/paquetes (Solo lectura, restringido por RLS a su unidad).
