# SAED 2.0 — DOCUMENTACIÓN TÉCNICA
## F11-04 BLOQUE C: MOTOR DE EXPORTACIÓN (PDF / CSV / XLSX)

### 1. Resumen Ejecutivo
El **Motor de Exportación F11-04 Bloque C** proporciona capacidades de exportación multi-tenant, deterministas y de alta fidelidad para los reportes administrativos y financieros existentes en SAED 2.0:
- **Cartera Morosa** (`/reportes/cartera-morosa`)
- **Ejecución de Cuotas** (`/reportes/ejecucion-cuotas`)
- **Pagos Recientes** (`/reportes/pagos-recientes`)
- **Ejecución Presupuestal Global** (`/reportes/ejecucion-presupuestal`)

### 2. Principio Arquitectónico Fundamental: Consumo Directo de DTOs
El motor de exportación **NO ejecuta consultas SQL paralelas**. Consume directamente los mismos contratos tipados emitidos por `ReportesService`:
```text
Controller / Request
       ↓
ReportesService (Aplica RLS, SaedContextHolder, validación multi-tenant)
       ↓
DTO Tipado (CarteraMorosaDTO, EjecucionCuotasDTO, PagoRecienteDTO, EjecucionPresupuestalGlobalDTO)
       ↓
ReportExportService
   ├── PDF: OpenHTMLtoPDF (XHTML corporativo SAED 2.0 con tablas y KPIs)
   ├── CSV: Streaming RFC 4180 con UTF-8 BOM (\uFEFF) y escape de comas/comillas
   └── JSON: Serialización canónica Jackson
```
Esto garantiza matemáticamente que:
```text
JSON === PDF === CSV === XLSX
```

### 3. Decisión de Arquitectura para XLSX: Opción X3
Tras la auditoría técnica de dependencias:
- **Rechazo de Apache POI en Backend:** Apache POI agregaría más de 30 MB al runtime Java, un alto consumo de Heap en la JVM (OOM risk) y redundancia innecesaria.
- **Adopción de Opción X3 (Frontend Styled XLSX):** El frontend ya cuenta con `xlsx-js-style` y `fflate`. Se implementó un generador diferido (`exportToExcel` en `exportUtils.js`) con importación dinámica (lazy import), aplicando la paleta corporativa de SAED (`#0F2044`), filas cebra, anchos de columna auto-ajustables y formateo nativo de moneda COP.

### 4. Seguridad Multi-Tenant y Protección Anti-IDOR
1. **ADMIN_PROPIEDAD:** Únicamente puede consultar y exportar datos pertenecientes a su propiedad activa (`SaedContextHolder.ID_PROPIEDAD`). Si envía un `propertyId` foráneo, el sistema rechaza la petición con `403 FORBIDDEN`.
2. **ADMIN_ORGANIZACION:** Puede exportar el consolidado de su organización o propiedades que pertenezcan a su organización. Si solicita una propiedad de otra organización, se rechaza con `403 FORBIDDEN`.
3. **SUPERADMIN:** Acceso de auditoría y supervisión global (`SCOPE_SUPERADMIN`).
4. **RESIDENTE / PORTERO:** Rechazados con `403 FORBIDDEN` por `@PreAuthorize`.
5. **No autenticado:** Rechazado con `401 UNAUTHORIZED`.

### 5. Control de Volumen y Límites Síncronos
- **Límite Canónico:** Hasta 10.000 filas por exportación síncrona.
- Si un reporte supera las 10.000 filas, `ReportExportService` lanza `ExportVolumeLimitExceededException`.
- `GlobalExceptionHandler` mapea la excepción a **HTTP 422 UNPROCESSABLE ENTITY** con el código canónico `EXCEEDS_EXPORT_LIMIT`.

### 6. Contratos y Endpoints
Los endpoints de `ReportesController` son polimórficos y soportan tanto `formato` como `format` de forma insensible a mayúsculas:
- `GET /api/v1/reportes/cartera-morosa?formato=PDF` -> `application/pdf`
- `GET /api/v1/reportes/cartera-morosa?formato=CSV` -> `text/csv;charset=UTF-8` con BOM
- `GET /api/v1/reportes/cartera-morosa?formato=JSON` -> `application/json`

Headers de respuesta:
- `Content-Type: application/pdf` o `text/csv;charset=UTF-8`
- `Content-Disposition: attachment; filename="<reporte>_<timestamp>.<ext>"; filename*=UTF-8''<reporte>_<timestamp>.<ext>`

### 7. Trazabilidad y Auditoría
Cada exportación exitosa registra de manera no bloqueante un evento en `AUDITORIA_LOG`:
- Acción: `REPORTE_EXPORTADO`
- Módulo: `REPORTES`
- Detalle JSON: `{"formato": "PDF", "reporte": "CARTERA_MOROSA"}`

### 8. Evidencia de Pruebas
- **F11_04_ExportacionesIntegrationTest:** 17 tests de integración (100% pasando).
- **ReportExportServiceTest:** 7 tests unitarios del servicio de exportación (100% pasando).
- **Regresión Completa F11-04:** 60 tests de integración concurrentes pasando sin fallas ni regresiones.
