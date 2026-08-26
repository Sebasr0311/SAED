# PHASE 1B TEST DATA DESIGN

## 1. Objetivo
Establecer un subconjunto mínimo, determinista y aislado de datos de prueba en la base de datos Oracle `SAED_V39_FINAL_TEST` para posibilitar la ejecución de las suites de pruebas de Integración y Adversariales de la Fase 1A y Fase 1B.

## 2. Aislamiento y Mantenimiento
- **Entorno Objetivo:** Base de datos Oracle local de desarrollo/pruebas (SAED_V39_FINAL_TEST).
- **Scripts:** 
  - `database/test-data/phase-1b/seed_phase_1b_oracle.sql`
  - `database/test-data/phase-1b/cleanup_phase_1b_oracle.sql`
- **Condición de Ejecución:** Los scripts no forman parte de las migraciones Flyway (V3.9, V4.0, V4.1). Deben ejecutarse manualmente o vía perfil de prueba antes de correr la suite.

## 3. Entidades a Crear

### 3.1 Diccionarios (Si están vacíos)
Deben insertarse condicionalmente si no existen, pues son dependencias (FKs) rígidas:
- `TIPOS_DOCUMENTO`: CC (Cédula)
- `TIPOS_PROPIEDAD`: RESIDENCIAL
- `TIPOS_UNIDAD`: APARTAMENTO
- `ROLES`: SUPERADMIN, ADMIN_PROPIEDAD, PROPIETARIO

### 3.2 Jerarquía Multi-tenant
- **Organización A:** ID = 1000, Nombre = "Org Test A"
- **Organización B:** ID = 2000, Nombre = "Org Test B"
- **Propiedad A:** ID = 1000, Org = 1000
- **Propiedad B:** ID = 2000, Org = 2000
- **Unidad A:** ID = 1000, Prop = 1000
- **Unidad B:** ID = 2000, Prop = 2000

### 3.3 Personas y Usuarios
Todos usarán el password `password123` (Hash: `$2a$10$hV0SOJvWQtYqvqEg7tKXqeolVuNjM4Y7BCUXxg1yVBvB4/gRi2xFe`).
- **User 1 (integration@saed.com):** ID = 1000. Requerido por Fase 1A. Asignado a Propiedad A como ADMIN_PROPIEDAD.
- **User 2 (user_b@saed.com):** ID = 2000. Requerido por Fase 1B. Asignado a Propiedad B como ADMIN_PROPIEDAD.
- **User 3 (unit_a@saed.com):** ID = 3000. Asignado a Unidad A como PROPIETARIO.

### 3.4 Asignaciones (USUARIO_ASIGNACIONES)
- **Asignación 1000:** User 1000 -> Org 1000, Prop 1000, ADMIN_PROPIEDAD
- **Asignación 2000:** User 2000 -> Org 2000, Prop 2000, ADMIN_PROPIEDAD
- **Asignación 3000:** User 3000 -> Org 1000, Prop 1000, Unidad 1000, PROPIETARIO

## 4. Estrategia de Cleanup
El archivo de cleanup eliminará las filas creadas basándose en las FKs y IDs explícitos (`ID >= 1000 y ID <= 3000`). No usará `TRUNCATE` ni eliminará datos que no le pertenecen.

## 5. Integración con RLS
Los scripts serán ejecutados por el usuario `SAED_V39_FINAL_TEST` el cual, según el modelo, es el dueño del esquema. Si es necesario inyectar contexto para evadir validaciones de auditoría o triggers, se ejecutará un `PKG_SAED_SESSION.SET_CONTEXT` temporal en el mismo script SQL, emulando al administrador. No se alterarán las políticas DBMS_RLS.
