# PHASE 1C ORGANIZATION DESIGN

## OPERACIONES (CRUD)
- **Crear (POST):**
  - Obligatorio: 
ombre, identificacionFiscal, emailContacto.
  - Opcional: 	elefonoContacto, direccion, ciudad.
  - Backend inyecta pais = 'Colombia' (default en schema) si no se provee.
  - El backend asignará creadoPor = JWT.userId.
- **Consultar (GET):**
  - Devuelve organizaciones. Gracias al RLS de V3.9, SELECT * FROM ORGANIZACIONES ya vendrá filtrado según el alcance del usuario (si es SAED admin, ve todas, si es ADMIN_ORG, ve solo la suya).
- **Actualizar (PUT/PATCH):**
  - Actualización de campos de contacto. identificacionFiscal puede ser inmutable a nivel de reglas de negocio o depender de reglas V3.9.
- **Cambiar Estado:**
  - ACTIVA, SUSPENDIDA, INACTIVA.
  - Validado en DB por CK_ORGANIZACIONES_ESTADO.

## RESTRICCIONES DE SEGURIDAD
- Un endpoint de Creación solo puede ser consumido por un SUPERADMIN o un rol con alcance GLOBAL. Un usuario regular con alcance PROPIEDAD no puede crear organizaciones.
- Las consultas devolverán inherentemente lo que RLS permita.
