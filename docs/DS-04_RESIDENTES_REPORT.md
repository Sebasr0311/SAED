# SAED 2.0 — REPORTE TÉCNICO DS-04
## REDISEÑO GESTIÓN DE RESIDENTES (CENSO POBLACIONAL)

**Fecha:** 4 de Septiembre de 2026  
**Fase:** Design System Interno — Hito DS-04  
**Alcance:** Exclusivo a `frontend/src/pages/ResidentesPage.jsx`  
**Referencia Visual:** Congelada desde DS-01, DS-02 y DS-03  
**Estado:** ✅ **CERTIFICADO / LISTO PARA PRODUCCIÓN**

---

## 1. RESUMEN EJECUTIVO

El hito **DS-04** modernizó integralmente la interfaz de **Gestión de Residentes** (`ResidentesPage.jsx`), alineándola con el lenguaje de diseño **Modern Enterprise SaaS / PropTech Premium** establecido en la Landing, Login, AppShell (DS-02) y Dashboard de ADMIN_PROPIEDAD (DS-03).

La página conserva el 100% de los contratos de API, payloads JSON, lógica de negocio y flujos de validación preexistentes (incluyendo el registro condicional de tutores legales para menores de edad y el doble factor de verificación de contraseña para borrados), reemplazando los componentes visuales heredados y eliminando por completo Material Symbols en favor de `lucide-react`.

---

## 2. INVARIANTES ABSOLUTAS CERTIFICADAS (10/10)

| # | Invariante Requerida | Estado | Evidencia de Control |
|---|----------------------|:------:|----------------------|
| 1 | **Backend Spring Boot** | 🔒 INTOCADO | 0 archivos modificados en `backend/`. Git status limpio. |
| 2 | **Oracle ATP / Base de Datos** | 🔒 READ-ONLY | Sin DDL, DML ni migraciones ejecutadas. |
| 3 | **RLS / VPD** | 🔒 PRESERVADO | Headers `X-Assignment-Id` inyectados en cada llamada vía `useTenantApi()`. |
| 4 | **JWT / AuthContext / AuthProvider** | 🔒 INTOCADO | Tokens, sesiones y ciclo de refresco 100% inalterados. |
| 5 | **ProtectedRoute** | 🔒 INTOCADO | Control de acceso y guards intactos. |
| 6 | **TenantProvider** | 🔒 INTOCADO | Aislamiento y selector de asignación activa respetados. |
| 7 | **Roles y Permisos (RBAC)** | 🔒 INTACTO | Matriz de control de acceso idéntica. |
| 8 | **ROLE_HOME / ACCESS_BY_ROLE** | 🔒 INTACTO | Enrutamiento raíz por rol intacto en `access.js`. |
| 9 | **APIs y Contratos REST** | 🔒 INTOCADO | Ningún endpoint nuevo creado; payloads de creación, edición y asignación idénticos. |
| 10 | **Landing, Login, AppShell y DS-03** | 🔒 CONGELADOS | Componentes previos congelados y sin regresiones. |

---

## 3. ARCHIVOS MODIFICADOS Y ARTEFACTOS GENERADOS

- **Archivo Modificado Exclusivamente:**
  - `frontend/src/pages/ResidentesPage.jsx`: Reescritura arquitectónica de presentación y UX utilizando las primitivas de diseño de SAED 2.0.
- **Artefactos y Reportes Generados:**
  - `docs/DS-04_RESIDENTES_REPORT.md`
  - `docs/screenshots/screenshot-ds04-1440x900.png`
  - `docs/screenshots/screenshot-ds04-modal-1440x900.png`
  - `docs/screenshots/screenshot-ds04-1280x800.png`
  - `docs/screenshots/screenshot-ds04-1024x800.png`
  - `docs/screenshots/screenshot-ds04-768x1024.png`
  - `docs/screenshots/screenshot-ds04-390x844.png`
  - `docs/screenshots/screenshot-ds04-390x844-cards.png`
  - `docs/screenshots/screenshot-ds04-360x740.png`
  - `docs/screenshots/screenshot-ds04-360x740-cards.png`

---

## 4. ENDPOINTS EXISTENTES UTILIZADOS

Todas las operaciones se ejecutan mediante `useTenantApi()`, garantizando el aislamiento multi-tenant por asignación activa:

1. **`GET /api/v1/personas`**:
   - Consulta el censo completo de habitantes de la copropiedad.
2. **`GET /api/v1/units`**:
   - Consulta las unidades habitacionales de la propiedad para resolver el selector de apartamentos y la etiqueta del inmueble asignado.
3. **`GET /api/v1/tipos-documento`** (vía hook `useTiposDocumento`):
   - Obtiene el catálogo oficial de tipos de documento (CC, CE, TI, PAS, NIT).
4. **`GET /api/v1/residentes/{id}`**:
   - Consulta el detalle del residente si `esMenorEdad === true` para precargar la información del tutor legal.
5. **`POST /api/v1/personas`**:
   - Registro de nueva persona natural en el censo con payload estricto (`tipoDocumentoId`, `numeroDocumento`, `tipoPersona: "NATURAL"`, `primerNombre`, `segundoNombre`, `primerApellido`, `segundoApellido`, `email`, `telefono`).
6. **`PUT /api/v1/personas/{id}`**:
   - Actualización de los datos biográficos de la persona existente.
7. **`POST /api/v1/residentes/{idResidente}/asignar-apartamento`**:
   - Asocia el residente al apartamento seleccionado (`{ idApartamento, rolEnContrato: 'OTRO' }`).
8. **`GET /api/v1/personas/{idResidente}`**:
   - Verificación de confirmación de asignación en el servidor tras la mutación.
9. **`DELETE /api/v1/personas/{id}`**:
   - Baja del habitante en el censo tras la confirmación obligatoria con clave de administrador.
10. **`POST /api/v1/auth/verify-password`**:
    - Verificación del factor de seguridad en `ConfirmPasswordDialog` previo al borrado.

---

## 5. ARQUITECTURA DE DISEÑO Y MEJORAS UX

### Primitivas del Design System Utilizadas
- **`PageContainer`**: Envoltura consistente con márgenes responsivos y espaciado vertical estandarizado (`space-y-6`).
- **`MetricCard`**:
  1. *Total Residentes*: Censo global de la copropiedad (Icono `Users`, variante `primary`).
  2. *Con Unidad Asignada*: Habitantes vinculados a un apartamento específico (Icono `Building`, variante `info`).
  3. *Canal Digital Activo*: Habitantes con correo electrónico para notificaciones y cartera (Icono `Mail`, variante `success`).
  4. *Menores en Censo*: Residentes con tutor legal obligatorio (Icono `ShieldCheck`, variante `secondary`).
- **`Card`, `CardHeader`, `CardContent`**: Contenedor principal con elevación suave y bordes tenues (`border-border/80 shadow-xs`).
- **`Badge`**: Etiquetas semánticas para tipo de persona, estado de menor de edad y chip de unidad habitacional.
- **`Button`**: Variantes primarias, outline y danger con micro-iconos Lucide y áreas táctiles accesibles (touch target ≥ 44px en móviles).
- **`LoadingState` / `ErrorState`**: Manejo de ciclo de vida asíncrono con reintento unificado.
- **Iconografía**: Exclusivamente `lucide-react` (`Users`, `UserPlus`, `Building`, `Mail`, `Phone`, `Search`, `X`, `Pencil`, `Trash2`, `ShieldCheck`, `ShieldAlert`, `RefreshCw`, `ChevronLeft`, `ChevronRight`). Cero `material-symbols-outlined`.

### Innovaciones de UX y Adaptabilidad Móvil
1. **Doble Presentación Responsiva**:
   - **Desktop y Tablet (`hidden md:block`)**: Tabla corporativa con avatares de iniciales, microdatos claros de contacto (iconos inline de teléfono y correo), tipografía monoespaciada para documentos y botones de acción con tooltips de accesibilidad (`aria-label`).
   - **Móvil (`md:hidden`)**: Lista adaptativa de tarjetas compactas individuales que elimina cualquier desbordamiento horizontal accidental. Cada tarjeta incluye avatar, identificación, badge de apartamento, datos de contacto y botones de Editar/Eliminar de fácil pulsación con el pulgar.
2. **Búsqueda Dinámica**:
   - Input con debounce y botón instantáneo de borrado (`X`).
   - Contador en tiempo real: `Mostrando X de Y residentes`.
   - Empty state inteligente: si la búsqueda no arroja coincidencias, ofrece el botón "Limpiar búsqueda".
3. **Formulario y Sección de Tutor Legal**:
   - Formulario en grid de 2 columnas con validaciones reactivas en blur (`useLiveValidation`).
   - Bloque condicional para tutor legal (si edad entre 16 y 17 años) presentado en una tarjeta sutil con alerta visual y selección completa de parentesco.
4. **Seguridad en Eliminación**:
   - Confirmación en dos fases: diálogo informativo + validación de contraseña de administrador (`ConfirmPasswordDialog`).

---

## 6. CONTROL DE CALIDAD Y VALIDACIONES

### A. Linter (ESLint)
```powershell
npx eslint src/pages/ResidentesPage.jsx
# Resultado: 0 errors, 0 warnings (100% CLEAN)
```

### B. Compilación de Producción (Vite)
```powershell
npm run build
# Resultado:
# ✓ built in 6.94s
# dist/assets/ResidentesPage-BdIGCXL9.js: 26.03 kB │ gzip: 7.35 kB
# Cero errores de TypeScript, JSX o empaquetado.
```

### C. Matriz de Pruebas Visuales y Responsividad

| Viewport | Dispositivo Objetivo | Layout Verificado | Estado |
|---|---|---|:---:|
| **1440 × 900** | Desktop Large | Topbar, Breadcrumb, 4 MetricCards horizontales, Tabla amplia con hover, modal centrado. | ✅ APROBADO |
| **1280 × 800** | Desktop Standard / Laptop | Proporciones balanceadas, lectura nítida de censo y acciones. | ✅ APROBADO |
| **1024 × 800** | iPad Pro / Laptop compacta | Cuadrícula de KPIs fluida, tabla con scroll horizontal interno protegido sin romper el layout. | ✅ APROBADO |
| **768 × 1024** | iPad Portrait / Tablet | Menú hamburguesa activo, KPIs en matriz 2×2, tabla con scroll suave. | ✅ APROBADO |
| **390 × 844** | iPhone 12/13/14/15 | Tarjetas móviles apiladas, touch targets ≥ 44px, cero desbordamiento horizontal. | ✅ APROBADO |
| **360 × 740** | Android Estándar / Galaxy | Excelente legibilidad de microdatos, botones de acción fluidos y accesibles. | ✅ APROBADO |

---

## 7. CONCLUSIÓN

La página de **Gestión de Residentes** ha alcanzado el nivel de excelencia estética y funcional de **SAED 2.0**, operando en armonía perfecta con el AppShell y el Dashboard de ADMIN_PROPIEDAD. Se garantiza la estabilidad del sistema al haber preservado rigurosamente el 100% de la lógica de backend, base de datos y contratos de red.
