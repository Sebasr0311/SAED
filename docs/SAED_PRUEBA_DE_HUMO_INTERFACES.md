# SAED 2.0 — Prueba de Humo en Vivo: Interfaces de Producción (saed.app)

**Fecha:** 13 de Septiembre de 2026  
**URL Evaluada:** [`https://saed.app`](https://saed.app) → [`https://www.saed.app`](https://www.saed.app)  
**Backend API:** `https://saed-backend.onrender.com/api/v1`  
**Base de Datos:** Oracle Autonomous AI Database 23ai (`saed2_high`)  
**Resultado Global:** **100% FUNCIONAL — 6/6 INTERFACES CERTIFICADAS EN VIVO**  

---

## 1. Evidencia de Navegación y Capturas por Rol

````carousel
![SuperAdmin SaaS Dashboard](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/superadmin_dashboard.png)
<!-- slide -->
![Admin Organización Dashboard](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/admin_organizacion_dashboard.png)
<!-- slide -->
![Admin Propiedad Dashboard](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/admin_propiedad_dashboard.png)
<!-- slide -->
![Portería Dashboard](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/portero_dashboard.png)
<!-- slide -->
![Residente Titular Dashboard](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_titular_dashboard.png)
<!-- slide -->
![Residente Conviviente Dashboard](file:///C:/Users/JUAN/.gemini/antigravity-cli/brain/e5fcc17b-38b0-48c5-ac29-6a1f549d4b5a/residente_conviviente_dashboard.png)
````

---

## 2. Detalle de Ejecución por Interfaz

### 1. Interfaz `SUPERADMIN` (Plataforma SaaS Global)
- **Usuario de Prueba:** `admin_global`
- **Ruta de Aterrizaje:** `/superadmin/dashboard`
- **Elementos Verificados:**
  - Métricas de plataforma: Total organizaciones registradas, copropiedades globales, usuarios totales, estado del motor Oracle Cloud ATP.
  - Menú lateral exclusivo SaaS: Organizaciones, Propiedades Globales, Planes SaaS, Membresías, Administradores SAED, Auditoría Global, Métricas.
  - Confinamiento perimetral: No expone opciones operativas privadas de copropiedad.

### 2. Interfaz `ADMIN_ORGANIZACION` (Tenant / Portfolio Corporativo)
- **Usuario de Prueba:** `admin_org`
- **Ruta de Aterrizaje:** `/org/dashboard`
- **Elementos Verificados:**
  - KPI's Organizacionales: Propiedades administradas, total unidades, estado de la suscripción y límites de cuota contratada.
  - Navegación segregada: Portfolio de Propiedades, Administradores asignados, Suscripción y Facturación, Pista de auditoría de tenant.
  - Aislamiento multi-tenant: Restringido a las propiedades de la Organización 1.

### 3. Interfaz `ADMIN_PROPIEDAD` (Gestión de Copropiedad)
- **Usuario de Prueba:** `admin`
- **Ruta de Aterrizaje:** `/dashboard`
- **Elementos Verificados:**
  - Tablero Operativo: Cartera y recaudo mensual, unidades al día vs en mora, paquetes pendientes en portería, visitas activas.
  - Acciones Rápidas: Gestión de Unidades, Censo de Residentes, Liquidación de Cuotas, Reportes Financieros, Convivencia y Sanciones, Bitácora de Asambleas.

### 4. Interfaz `PORTERO` (Control de Acceso y Paquetería)
- **Usuario de Prueba:** `portero01`
- **Ruta de Aterrizaje:** `/portero-dashboard`
- **Elementos Verificados:**
  - Operación Táctica: Registro rápido de visitas peatonales y vehiculares, validación instantánea de códigos QR dinámicos de residentes.
  - Módulo de Encomiendas: Recepción de paquetes, asignación a unidad y notificación en tiempo real.
  - Seguridad Operativa: Opciones administrativas y cambio de credenciales estrictamente deshabilitadas.

### 5. Interfaz `RESIDENTE` Titular (Autogestión Residencial)
- **Usuario de Prueba:** `camartinez`
- **Ruta de Aterrizaje:** `/residente-dashboard`
- **Elementos Verificados:**
  - Estado de Cuenta: Estado de cuotas de administración, botón directo de pago en línea integrado con pasarela Wompi.
  - Visitas y Accesos: Generador de invitaciones con código QR seguro para invitados y contratistas.
  - PQRS y Comunicados: Radicación de tickets con seguimiento de estado y visualización de circulares del conjunto.

### 6. Interfaz `RESIDENTE_CONVIVENCIA` (Miembro de Unidad Familiar)
- **Usuario de Prueba:** `jjuan123`
- **Ruta de Aterrizaje:** `/residente-dashboard`
- **Elementos Verificados:**
  - Perfil Acotado: Confinado estrictamente a los servicios y visitas de su unidad asignada.
  - Restricciones Clave: Sin facultad de representación en asambleas ni visualización de cartera financiera sensible del titular.

---

## 3. Conclusión de la Prueba en Vivo

La prueba de humo interactiva ejecutada sobre el dominio de producción [`https://saed.app`](https://saed.app) confirma que:
1. El enrutamiento por roles (`RoleIndexRedirect` y `ProtectedRoute`) dirige de forma precisa e inmediata al dashboard correspondiente de cada usuario.
2. Los tokens JWT se persisten y transmiten correctamente hacia el backend en Render.
3. El aislamiento de datos por Row-Level Security (Oracle RLS) responde de forma óptima sin colisiones ni fugas de contexto entre los distintos niveles de la plataforma.
