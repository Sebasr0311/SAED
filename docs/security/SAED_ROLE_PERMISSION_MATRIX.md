# MATRIZ TÉCNICA MAESTRA DE ROLES Y PERMISOS — SAED 2.0
**Documento Normativo de Autorización y Aislamiento Multi-Tenant**  
**Versión:** 1.0  
**Fecha:** 2026-09-10  
**Fuente de Verdad:** `docs/security/SAED_ROLE_PERMISSION_MATRIX.md`

---

## 1. Principios Arquitectónicos de Aislamiento

1. **Aislamiento por Scope:**
   - **`GLOBAL` (`SUPERADMIN`):** Exclusivo para gestión de la plataforma SaaS. `id_organizacion` e `id_propiedad` son `NULL`. Prohibida la operación directa de edificios o unidades.
   - **`ORGANIZACION` (`ADMIN_ORGANIZACION`):** Gestión gerencial de cartera de propiedades. Requiere `id_organizacion NOT NULL`, con `id_propiedad NULL`. Prohibida la operación residencial diaria (visitas, garita, pagos unitarios).
   - **`PROPIEDAD` (`ADMIN_PROPIEDAD`):** Operación integral de una copropiedad. Requiere `id_organizacion NOT NULL` e `id_propiedad NOT NULL`. Restringido a las propiedades explícitamente asignadas en `USUARIO_ASIGNACIONES`.
2. **Deny-by-Default:** Todo endpoint y ruta no explícitamente autorizada para un rol es denegada automáticamente por Spring Security (`@PreAuthorize`), RLS en Oracle ATP y `ProtectedRoute` en React.
3. **Invariante Anti-Spoofing:** Las identidades de tenant (`id_organizacion`, `id_propiedad`) se derivan invariablemente del token JWT + `X-Assignment-Id` validado en el servidor vía `SaedContextHolder`. Nunca se confía en parámetros enviados en el cuerpo de la petición.

---

## 2. Matriz Maestra de Autorización

| ROL | SCOPE | MÓDULO | RUTA CANÓNICA | MÉTODO | PERMISO | ENDPOINT BACKEND | DATA SCOPE | UI SCOPE | RESTRICCIÓN OPERATIVA | ESTADO |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SUPERADMIN** | GLOBAL | Dashboard Global | `/superadmin/dashboard` | GET | READ | `/api/v1/platform/dashboard` | Global | platform | Métricas globales SaaS y salud de plataforma | Activo |
| **SUPERADMIN** | GLOBAL | Organizaciones | `/superadmin/organizaciones` | GET, POST, PUT | CRUD | `/api/v1/organizations` | Global | platform | Alta, baja y consulta de organizaciones cliente | Activo |
| **SUPERADMIN** | GLOBAL | Propiedades Globales | `/superadmin/propiedades` | GET | READ | `/api/v1/properties` | Global | platform | Catálogo/monitoreo global. Sin CRUD operativo | Activo |
| **SUPERADMIN** | GLOBAL | Planes SaaS | `/superadmin/planes` | GET, POST, PUT | CRUD | `/api/v1/platform/plans` | Global | platform | Precios, capacidades y límites de suscripción | Activo |
| **SUPERADMIN** | GLOBAL | Membresías | `/superadmin/membresias` | GET, POST, PUT | CRUD | `/api/v1/platform/memberships` | Global | platform | Asignación y estados de licencias SaaS | Activo |
| **SUPERADMIN** | GLOBAL | Administradores SAED | `/superadmin/administradores` | GET, POST, PUT | CRUD | `/api/v1/platform/admins` | Global | platform | Exclusivo operadores de plataforma SAED | Activo |
| **SUPERADMIN** | GLOBAL | Auditoría Global | `/superadmin/auditoria` | GET | READ | `/api/v1/audit` | Global | platform | Pista de auditoría append-only sin filtro de tenant | Activo |
| **SUPERADMIN** | GLOBAL | Métricas de Negocio | `/superadmin/metricas` | GET | READ | `/api/v1/platform/dashboard` | Global | platform | Crecimiento, adopción y consumo global | Activo |
| **ADMIN_ORGANIZACION** | ORGANIZACION | Dashboard Organizacional | `/org/dashboard` | GET | READ | `/api/v1/org/dashboard` | Organization | organization | Consolidado de propiedades, unidades y recaudo propio | Activo |
| **ADMIN_ORGANIZACION** | ORGANIZACION | Mi Organización | `/org/organizacion` | GET, PUT | READ_UPDATE | `/api/v1/org/profile` | Organization | organization | Datos corporativos e información de contacto | Activo |
| **ADMIN_ORGANIZACION** | ORGANIZACION | Propiedades | `/org/propiedades` | GET, POST, PUT | CRUD | `/api/v1/properties` | Organization | organization | Solo propiedades pertenecientes a su organización (RLS) | Activo |
| **ADMIN_ORGANIZACION** | ORGANIZACION | Administradores | `/org/admins` | GET, POST, PUT | CRUD | `/api/v1/org/admins` | Organization | organization | Gestión y asignación de administradores a propiedades | Activo |
| **ADMIN_ORGANIZACION** | ORGANIZACION | Plan y Suscripción | `/org/plan` | GET | READ | `/api/v1/org/subscription` | Organization | organization | Consumo de cupos y vigencia de la suscripción | Activo |
| **ADMIN_ORGANIZACION** | ORGANIZACION | Cartera Consolidada | `/org/cartera` | GET | READ | `/api/v1/org/dashboard` | Organization | organization | Supervisión de morosidad y recaudo agregado de cartera | Por integrar |
| **ADMIN_ORGANIZACION** | ORGANIZACION | Reportes Gerenciales | `/org/reportes` | GET | READ | `/api/v1/org/dashboard` | Organization | organization | Informes consolidados de gestión y ocupación | Por integrar |
| **ADMIN_ORGANIZACION** | ORGANIZACION | Analítica Comparativa | `/org/analitica` | GET | READ | `/api/v1/org/dashboard` | Organization | organization | Comparativas de rendimiento entre propiedades | Por integrar |
| **ADMIN_ORGANIZACION** | ORGANIZACION | Auditoría Organizacional | `/org/auditoria` | GET | READ | `/api/v1/audit` | Organization | organization | Trazabilidad de eventos de su organización únicamente | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Dashboard Operativo | `/dashboard` | GET | READ | `/api/v1/dashboard/*` | Property | property | Mando operativo de la copropiedad asignada | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Personas | `/personas` | GET, POST, PUT | CRUD | `/api/v1/personas` | Property | property | Padrón y censo de personas relacionadas | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Residentes | `/residentes` | GET, POST, PUT | CRUD | `/api/v1/residentes` | Property | property | Gestión de habitantes y propietarios de unidades | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Unidades | `/unidades` | GET, POST, PUT | CRUD | `/api/v1/unidades` | Property | property | Catastro de inmuebles y coeficientes de copropiedad | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Contratos Arriendo | `/contratos` | GET, POST, PUT | CRUD | `/api/v1/contratos` | Property | property | Contratos de arrendamiento vigentes | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Contratos Proveedores | `/contratos-proveedor` | GET, POST, PUT | CRUD | `/api/v1/contratos-proveedor` | Property | property | Contratos de servicios y proveedores externos | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Usuarios y Accesos | `/usuarios` | GET, POST, PUT | CRUD | `/api/v1/usuarios` | Property | property | Cuentas de acceso de residentes y personal | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Roles y Asignaciones | `/roles-asignaciones` | GET, POST, PUT | CRUD | `/api/v1/assignments` | Property | property | Asignaciones de porteros y residentes a unidades | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Reportes Operativos | `/reportes` | GET | READ | `/api/v1/reportes` | Property | property | Informes operacionales y demográficos | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Visitas | `/visitas` | GET, POST, PUT | CRUD | `/api/v1/porteria/visitas` | Property | property | Consulta y programación de visitas a la propiedad | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Historial Visitas | `/historial-visitas` | GET | READ | `/api/v1/porteria/visitas/historial` | Property | property | Bitácora de accesos y salidas históricas | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Puntos de Portería | `/porterias` | GET, POST, PUT | CRUD | `/api/v1/porterias` | Property | property | Configuración de garitas y estaciones de vigilancia | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Parqueaderos | `/parqueaderos` | GET, POST, PUT | CRUD | `/api/v1/parqueaderos` | Property | property | Asignación y control de cupos vehiculares | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Paquetería en Custodia | `/paquetes-admin` | GET, POST, PUT | CRUD | `/api/v1/paquetes` | Property | property | Supervisión de paquetería y casilleros | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Pagos | `/pagos` | GET, POST, PUT | CRUD | `/api/v1/pagos` | Property | property | Registro y validación de pagos de administración | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Cartera | `/cartera` | GET, POST, PUT | CRUD | `/api/v1/cartera` | Property | property | Facturación, saldos morosos y acuerdos de pago | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Presupuesto Anual | `/presupuestos` | GET, POST, PUT | CRUD | `/api/v1/presupuestos` | Property | property | Presupuesto aprobado por asamblea | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Gastos y Egresos | `/gastos` | GET, POST, PUT | CRUD | `/api/v1/gastos` | Property | property | Registro de gastos operacionales y mantenimiento | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Flujo de Caja | `/flujo-caja` | GET | READ | `/api/v1/flujo-caja` | Property | property | Conciliación real de ingresos vs egresos | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Conciliaciones | `/conciliaciones` | GET, POST, PUT | CRUD | `/api/v1/conciliaciones` | Property | property | Conciliación de extractos bancarios | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Paz y Salvos | `/paz-y-salvos` | GET, POST | CREATE_READ | `/api/v1/paz-y-salvos` | Property | property | Emisión de certificados de paz y salvo | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | PQRS (Atención) | `/quejas-admin` | GET, PUT | OPERATE | `/api/v1/pqrs` | Property | property | Gestión de peticiones, quejas y reclamos | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Multas | `/multas` | GET, POST, PUT | CRUD | `/api/v1/multas` | Property | property | Sanciones pecuniarias de convivencia | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Procesos Sancionatorios | `/sanciones-admin` | GET, POST, PUT | CRUD | `/api/v1/sanciones` | Property | property | Debido proceso y sanciones disciplinarias | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Reservas Zonas Comunes | `/reservas-admin` | GET, POST, PUT | CRUD | `/api/v1/reservas` | Property | property | Aprobación de reservas de áreas sociales | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Mantenimientos | `/mantenimientos` | GET, POST, PUT | CRUD | `/api/v1/mantenimientos` | Property | property | Mantenimientos de infraestructura y equipos | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Obras y Remodelaciones | `/obras-admin` | GET, POST, PUT | CRUD | `/api/v1/obras` | Property | property | Autorización y seguimiento de obras en unidades | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Pólizas y Seguros | `/polizas` | GET, POST, PUT | CRUD | `/api/v1/polizas` | Property | property | Coberturas y seguros de áreas comunes | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Planes de Emergencia | `/emergencias` | GET, POST, PUT | CRUD | `/api/v1/emergencias` | Property | property | Directorio de emergencia y planes de evacuación | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Incidentes de Convivencia | `/incidentes-admin` | GET, POST, PUT | CRUD | `/api/v1/incidentes` | Property | property | Bitácora de incidentes y novedades | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Asambleas | `/asambleas` | GET, POST, PUT | CRUD | `/api/v1/asambleas` | Property | property | Convocatorias, quórum y actas de copropietarios | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Avisos | `/avisos` | GET, POST, PUT | CRUD | `/api/v1/comunicados` | Property | property | Comunicados formales a la comunidad | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Alertas Inmediatas | `/alertas` | GET, POST, PUT | CRUD | `/api/v1/alertas` | Property | property | Avisos de emergencia o suspensión de servicios | Activo |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Documentación Oficial | `/documentos` | GET, POST, DELETE | CRUD | `/api/v1/documentos/admin` | Property | property | Repositorio de reglamentos, manuales y actas | Por habilitar ruta |
| **ADMIN_PROPIEDAD** | PROPIEDAD | Coarrendatarios | `/coarrendatarios` | GET, POST, PUT | CRUD | `/api/v1/coarrendatarios` | Property | property | Registro de avales y coarrendatarios | Activo |
| **PORTERO** | PORTERIA | Dashboard de Garita | `/portero-dashboard` | GET | READ | `/api/v1/porteria/*` | Property | property | Panel operativo de vigilancia y turnos | Activo |
| **PORTERO** | PORTERIA | Visitas y Salidas | `/visitas` | GET, POST, PUT | OPERATE | `/api/v1/porteria/visitas` | Property | property | Validación y registro de entradas y salidas | Activo |
| **PORTERO** | PORTERIA | Paquetes en Garita | `/paquetes` | GET, POST, PUT | OPERATE | `/api/v1/buzon/paquetes` | Property | property | Recepción física y entrega mediante PIN | Activo |
| **PORTERO** | PORTERIA | Control de Parqueaderos | `/parqueaderos` | GET, POST, PUT | OPERATE | `/api/v1/parqueaderos` | Property | property | Ocupación y asignación en tiempo real | Activo |
| **PORTERO** | PORTERIA | Escáner QR de Acceso | `/escanner-qr` | GET, POST | OPERATE | `/api/v1/porteria/acceso/validar` | Property | property | Lector de credenciales QR y token | Activo |
| **PORTERO** | PORTERIA | Registro de Incidentes | `/incidentes-admin` | POST, GET | OPERATE | `/api/v1/incidentes` | Property | property | Registro de infracciones y avisos de ruido | Activo |
| **RESIDENTE** | UNIDAD | Portal del Residente | `/residente-dashboard` | GET | READ | `/api/v1/residente/*` | Unit | unit | Estado general de la unidad | Activo |
| **RESIDENTE** | UNIDAD | Perfil del Residente | `/res-perfil` | GET, PUT | READ_UPDATE | `/api/v1/residente/perfil` | Unit | unit | Datos personales y grupo familiar | Activo |
| **RESIDENTE** | UNIDAD | Cuotas y Pagos | `/res-cuotas` | GET, POST | READ_CREATE | `/api/v1/cuotas`, `/api/v1/pagos` | Unit | unit | Pago de administración e historial de recibos | Activo |
| **RESIDENTE** | UNIDAD | Visitas e Invitaciones | `/res-visitas` | GET, POST | CRUD | `/api/v1/porteria/visitas` | Unit | unit | Generación de credenciales QR para invitados | Activo |
| **RESIDENTE** | UNIDAD | Casillero y Paquetes | `/res-buzon` | GET | READ | `/api/v1/buzon/mis-paquetes` | Unit | unit | Notificación de paquetes pendientes y PIN | Activo |
| **RESIDENTE** | UNIDAD | PQRS Residentes | `/res-quejas` | GET, POST | READ_CREATE | `/api/v1/pqrs` | Unit | unit | Radicación y seguimiento de solicitudes | Activo |
| **RESIDENTE** | UNIDAD | Reservas de Zonas Comunes| `/res-reservas` | GET, POST | READ_CREATE | `/api/v1/reservas` | Unit | unit | Solicitud de uso de áreas comunes | Activo |
| **RESIDENTE** | UNIDAD | Sanciones y Citaciones | `/res-sanciones` | GET | READ | `/api/v1/sanciones/mis-sanciones` | Unit | unit | Notificación de llamados de atención y multas | Activo |
| **RESIDENTE** | UNIDAD | Obras de la Unidad | `/res-obras` | GET, POST | READ_CREATE | `/api/v1/obras` | Unit | unit | Solicitud y registro de remodelaciones | Activo |
| **RESIDENTE** | UNIDAD | Incidentes Reportados | `/res-incidentes` | GET, POST | READ_CREATE | `/api/v1/incidentes` | Unit | unit | Reporte vecinal de novedades | Activo |
| **RESIDENTE** | UNIDAD | Documentos Copropiedad | `/res-documentos` | GET | READ | `/api/v1/documentos/residente` | Unit | unit | Consulta de reglamentos y circulares públicas | Activo |
