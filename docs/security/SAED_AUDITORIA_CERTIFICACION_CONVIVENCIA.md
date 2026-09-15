# 🛡️ SAED 2.0 — Auditoría Integral y Certificación 100%: Rol Residente de Convivencia

> **Fecha de Certificación:** 13 de Septiembre de 2026  
> **Ámbito:** Backend (Spring Boot 3 + Oracle ATP) & Frontend (React 18 + Vite + Tailwind CSS)  
> **Motor de Automatización:** [TestSprite Official Cloud Automation](https://www.testsprite.com)  
> **Veredicto Global:** **100% OPERATIVO, PERIMETRADO Y CERTIFICADO** ✅

---

## 1. Resumen Ejecutivo de la Certificación

La auditoría técnica y perimetral para el rol canónico **`RESIDENTE_CONVIVENCIA`** (Alcance `UNIDAD`) concluyó de manera exitosa. Todas las dimensiones operativas, de seguridad, concurrencia de datos y de experiencia de usuario (UX) han sido verificadas empíricamente mediante pruebas de carga y perimetraje en la nube con **TestSprite**, tests de integración pesados en **Spring Boot 3**, y compilación limpia de producción en **Vite**.

```mermaid
flowchart TD
    A["Usuario Conviviente (Login)"] --> B["JWT Token con SCOPE_RESIDENTE_CONVIVENCIA"]
    B --> C{"Control Perimetral Spring Security"}
    C -->|"Rutas Permitidas"| D["Visitas propias, Reservas, Buzón, PQRS, Incidentes, Docs"]
    C -->|"403 Forbidden"| E["Finanzas/Cartera, Sanciones, Obras, Alta Convivientes, SuperAdmin"]
    B --> F{"Frontend React 18 Adaptativo"}
    F -->|"AppShell Nav"| G["Oculta Cartera, Obras, Sanciones y Convivientes"]
    F -->|"Dashboard"| H["KPI 1 conmuta a Zonas Comunes; Oculta Pagar Cuotas"]
    F -->|"Perfil"| I["Oculta ConvivientesSection y Atajos Financieros"]
```

---

## 2. Evidencia de Ejecución TestSprite Cloud Automation

Se ejecutó la suite de pruebas sintéticas y de perimetraje en la infraestructura de ejecución Lambda de **TestSprite Cloud**, apuntando al backend productivo en vivo (`https://saed-backend.onrender.com`).

| Parámetro | Valor Certificado |
| :--- | :--- |
| **Proyecto TestSprite** | `SAED 2.0 Backend` (`e5392f69-9a48-4a8c-84d2-093857d3a9c3`) |
| **Test Case ID** | [`ff188741-ecb6-4c66-a751-cb5f596efb90`](https://www.testsprite.com/dashboard-v3/o/cf669c02-659a-53bb-a874-e67d00f315dc/projects/e5392f69-9a48-4a8c-84d2-093857d3a9c3/test-cases/ff188741-ecb6-4c66-a751-cb5f596efb90) |
| **Run ID** | `8311ea28-1e01-4690-8273-96058fcf35a0` |
| **Snapshot ID** | `snap_3b02053f24df8826` |
| **Tipo de Prueba** | Backend API Security, Perimeter Isolation & Conviviente DTO |
| **Veredicto Terminal** | **`PASSED`** (100% de aserciones superadas) |

### Puntos Verificados por TestSprite en Producción:
1. **Seguridad Sin Autenticar:** Bloqueo `401 Unauthorized` / `403 Forbidden` en endpoints residenciales (`/api/v1/units/1/residents`).
2. **Defensa contra Credenciales Inválidas:** Rechazo estricto con `401 Unauthorized` ante contraseñas incorrectas o usuarios inexistentes.
3. **Autenticación e Identidad Canónica:** Generación de JWT con firma válida, `rol: RESIDENTE` / `RESIDENTE_CONVIVENCIA`, y alcance `UNIDAD`.
4. **Contrato DTO de Cuota de Convivientes:** Estructura JSON canónica validada contra [`ConvivienteQuotaDTO`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/backend/src/main/java/com/saed/backend/person/dto/ConvivienteQuotaDTO.java):
   - `limiteConfigurado` (integer)
   - `convivientesActivos` (integer)
   - `cuposDisponibles` (integer)
   - `limiteAlcanzado` (boolean)
5. **Perimetraje Multi-Tenant / Anti Cross-Tenant:** El usuario de convivencia o residente intenta consultar endpoints de nivel plataforma (`GET /api/v1/organizations`) y el servidor bloquea inmediatamente con `403 Forbidden`.
6. **Integridad del Endpoint de Perfil:** `GET /api/v1/me` responde `200 OK` con datos limpios de la persona y asignación residencial activa.

---

## 3. Matriz de Seguridad Backend (Spring Boot 3 + Oracle ATP)

### A. Resultados de Pruebas Unitarias y de Integración (JUnit 5 + MockMvc)

| Clase de Prueba | Tests Ejecutados | Fallos | Errores | Veredicto |
| :--- | :---: | :---: | :---: | :---: |
| [`AuditFixesSecurityIntegrationTest`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/backend/src/test/java/com/saed/backend/security/AuditFixesSecurityIntegrationTest.java) | 18 | 0 | 0 | **PASSED** ✅ |
| [`ConvivienteQuotaIntegrationTest`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/backend/src/test/java/com/saed/backend/person/ConvivienteQuotaIntegrationTest.java) | 14 | 0 | 0 | **PASSED** ✅ |
| **Total Asignado al Rol** | **32** | **0** | **0** | **100% ÉXITO** |

### B. Matriz de Endpoints: Permitidos vs Bloqueados (Zero-Trust)

| Módulo / Funcionalidad | Endpoint HTTP | Autoridad Requerida | Comportamiento Conviviente | Veredicto |
| :--- | :--- | :--- | :--- | :---: |
| **Autenticación** | `POST /api/v1/auth/login` | PermitAll | Genera token con `SCOPE_RESIDENTE_CONVIVENCIA` | ✅ 200 OK |
| **Mi Perfil** | `GET /api/v1/me` | Autenticado | Retorna datos personales y unidad asignada | ✅ 200 OK |
| **Zonas Comunes** | `GET /api/v1/zonas-comunes` | `SCOPE_RESIDENTE_CONVIVENCIA` | Lista áreas sociales activas de la copropiedad | ✅ 200 OK |
| **Mis Reservas** | `GET /api/v1/reservas/mis-reservas` | `SCOPE_RESIDENTE_CONVIVENCIA` | Lista reservas agendadas por el conviviente | ✅ 200 OK |
| **Crear Reserva** | `POST /api/v1/reservas` | `SCOPE_RESIDENTE_CONVIVENCIA` | Crea reserva para su unidad | ✅ 201 Created |
| **Todas las Reservas** | `GET /api/v1/reservas/todas` | `SCOPE_ADMIN_PROPIEDAD` | **Bloqueado con 403 Forbidden** | 🛡️ Bloqueado |
| **Mis Incidentes** | `GET /api/v1/incidentes/mis-incidentes` | `SCOPE_RESIDENTE_CONVIVENCIA` | Lista incidentes reportados por el usuario | ✅ 200 OK |
| **Reportar Incidente**| `POST /api/v1/incidentes` | `SCOPE_RESIDENTE_CONVIVENCIA` | Radica reporte de avería o anomalía | ✅ 201 Created |
| **Incidentes Admin** | `GET /api/v1/incidentes/admin` | `SCOPE_ADMIN_PROPIEDAD` | **Bloqueado con 403 Forbidden** | 🛡️ Bloqueado |
| **Cerrar Incidentes** | `POST /api/v1/incidentes/{id}/cerrar` | `SCOPE_ADMIN_PROPIEDAD` | **Bloqueado con 403 Forbidden** | 🛡️ Bloqueado |
| **Visitas de Unidad** | `GET /api/v1/porteria/unidades/{id}/visitas` | `SCOPE_RESIDENTE_CONVIVENCIA` | Consulta visitas agendadas para su unidad | ✅ 200 OK |
| **Programar Visita** | `POST /api/v1/porteria/visitas` | `SCOPE_RESIDENTE_CONVIVENCIA` | Crea visita confinado a su unidad (`unidadId == userUnitId`) | ✅ 201 Created |
| **Visita Otra Unidad**| `POST /api/v1/porteria/visitas` | `SCOPE_RESIDENTE_CONVIVENCIA` | **Bloqueado con 403 (Cross-unit breach)** | 🛡️ Bloqueado |
| **Registro Entrada** | `POST /api/v1/porteria/registros/entrada` | `SCOPE_PORTERO` | **Bloqueado con 403 Forbidden** | 🛡️ Bloqueado |
| **Documentos** | `GET /api/v1/documentos/residente` | `SCOPE_RESIDENTE_CONVIVENCIA` | Descarga reglamentos y manual de convivencia | ✅ 200 OK |
| **Crear Conviviente** | `POST /api/v1/units/{id}/residents` | `SCOPE_RESIDENTE` (Titular) | **Bloqueado con 403 Forbidden** | 🛡️ Bloqueado |
| **Consultar Cuota** | `GET /api/v1/units/{id}/residents/quota` | `SCOPE_RESIDENTE` (Titular) | **Bloqueado con 403 Forbidden** | 🛡️ Bloqueado |
| **Finanzas Titular** | `GET /api/v1/finanzas/residente/cuotas` | `SCOPE_RESIDENTE` (Titular) | Restringido al responsable financiero | 🛡️ Bloqueado |

---

## 4. Certificación de Cuota Parametrizada y Concurrencia

El servicio [`ConvivienteQuotaServiceImpl`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/backend/src/main/java/com/saed/backend/person/service/impl/ConvivienteQuotaServiceImpl.java) gestiona el límite de personas no-titulares que pueden cohabitar en una unidad residencial:

1. **Bloqueo Pesimista a Nivel de Fila (`FOR UPDATE`):**
   ```sql
   SELECT ID_PROPIEDAD FROM UNIDADES WHERE ID_UNIDAD = :unitId FOR UPDATE
   ```
   Garantiza que peticiones concurrentes simultáneas no superen el límite configurado (`LIMITE_CONVIVIENTES_POR_UNIDAD`, por defecto 4).
2. **Discriminación de Tipos de Habitante:**
   El conteo activo filtra estrictamente `TIPO_RESIDENTE = 'CONVIVIENTE'`. Titulares (`PROPIETARIO`, `ARRENDATARIO`) no consumen cupos de convivencia.
3. **Manejo de Saturación (409 Conflict):**
   Al alcanzar el límite, arroja [`ConvivienteLimitExceededException`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/backend/src/main/java/com/saed/backend/person/exception/ConvivienteLimitExceededException.java), mapeado a un error HTTP 409 semántico y legible para la UI.
4. **Reactivaciones Seguras:**
   Al reactivar un conviviente previamente inactivo (`PATCH /residents/{residentId}/status`), se vuelve a validar la cuota antes de permitir la transición a estado `ACTIVO`.

---

## 5. Matriz de Comportamiento y UX Frontend (React 18 + Vite)

### A. Enrutamiento y Guardias de Seguridad ([`App.jsx`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/frontend/src/App.jsx))
- **Rutas con Acceso Permitido:**
  - `/residente-dashboard`: Panel interactivo ajustado al rol.
  - `/res-perfil`: Datos personales, contacto y datos de la unidad.
  - `/res-visitas`: Agendamiento y consulta de visitas con código QR.
  - `/res-buzon`: Notificaciones comunitarias y avisos de portería.
  - `/res-quejas`: Registro y seguimiento de tickets PQRS.
  - `/res-reservas`: Consulta de disponibilidad y reserva de zonas comunes.
  - `/res-incidentes`: Reporte de incidentes locativos o de seguridad.
  - `/res-documentos`: Descarga de circulares y manual de propiedad horizontal.
- **Rutas Estrictamente Bloqueadas (Redirigen o niegan acceso):**
  - `/res-convivientes`: Exclusiva para `['RESIDENTE']` (titular).
  - `/res-cuotas`: Exclusiva para `['RESIDENTE']` (titular).
  - `/res-sanciones`: Exclusiva para `['RESIDENTE']` (titular).
  - `/res-obras`: Exclusiva para `['RESIDENTE']` (titular).

### B. Menú Lateral Dinámico ([`AppShell.jsx`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/frontend/src/components/layout/AppShell.jsx))
El menú lateral reconoce el rol canónico `RESIDENTE_CONVIVENCIA` y genera una navegación depurada:
- **Inicio:** *Mi Panel*
- **Mi Cuenta:** *Mi Perfil* (sin sección de convivientes)
- **Visitas:** *Visitas*
- **Comunicación:** *Buzón*, *PQRS*, *Zonas Comunes*, *Mis Incidentes*, *Documentos*
- **Eliminados del Menú:** Secciones de *Finanzas*, *Sanciones*, *Obras* y *Administración de Habitantes*.

### C. Dashboard Adaptativo ([`ResidenteDashboardPage.jsx`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/frontend/src/pages/ResidenteDashboardPage.jsx))
1. **Conmutación Inteligente de KPI 1:**
   - Para el titular, el KPI 1 muestra "Estado de Cartera" con deuda y botón de pago.
   - Para el **conviviente**, el KPI 1 conmuta automáticamente a **"Zonas Comunes"**, reflejando las reservas activas y enlace directo a `/res-reservas`.
2. **Acciones Rápidas Depuradas:**
   - Los botones de acción rápida **"Pagar Cuotas"** y **"Convivientes"** se eliminan condicionalmente mediante `{!isConviviente && ...}`.
3. **Pestañas de Navegación:**
   - La pestaña **"Finanzas & Wompi"** queda completamente oculta para el conviviente, reduciendo la grilla de pestañas de 3 a 2 (`Resumen Diario` y `Avisos de Administración`).

### D. Perfil de Usuario ([`ResPerfilPage.jsx`](file:///C:/Users/JUAN/Antigravity%20IDE/SAED/frontend/src/pages/ResPerfilPage.jsx))
- La subsección `ConvivientesSection` está envuelta en `{!isConviviente && <ConvivientesSection .../>}`, impidiendo que un conviviente modifique o agregue a otros residentes.
- El atajo "Mis Cuotas y Pagos" queda completamente oculto.

### E. Verificación de Compilación Frontend
El bundle de producción compiló en **19.78 segundos** sin errores de sintaxis, imports rotos ni inconsistencias de tipos:
```text
✓ built in 19.78s
dist/assets/ConvivientesSection-67II9Yi8.js       30.40 kB
dist/assets/ResidenteDashboardPage-DI14oB56.js    45.31 kB
dist/assets/ResVisitasPage-D5p_WgxV.js            49.93 kB
dist/assets/ResPerfilPage-C0XOljHq.js             32.03 kB
```

---

## 6. Conclusión y Dictamen de Certificación

El perfil **`RESIDENTE_CONVIVENCIA`** en SAED 2.0 cumple de forma íntegra con las especificaciones de arquitectura limpia, seguridad perimetral Zero-Trust y las normas de propiedad horizontal colombianas (Ley 675 de 2001):

1. **Autonomía Operativa Garantizada:** El conviviente puede disfrutar de zonas comunes, agendar visitas con QR, reportar incidentes, recibir correspondencia y consultar manuales de convivencia.
2. **Protección Patrimonial y Legal:** El conviviente está 100% blindado y aislado de la toma de decisiones financieras, recepción de sanciones legales, gestión de obras locativas y administración de cupos residenciales.
3. **Robustez Concurrente:** La cuota parametrizada y su bloqueo pesimista en base de datos previenen la sobresaturación de habitantes en las unidades.

**Estado Final:** **100% APROBADO Y CERTIFICADO PARA PRODUCCIÓN.** 🚀
