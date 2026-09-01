# SAED 2.0 — Inventario de Funcionalidades Faltantes y Brechas de Negocio

**Fecha:** 01 de Septiembre de 2026  
**Plan Maestro:** `Versión 4.0 — Definitiva`  
**Fase:** `Fase 1 — Auditoría Definitiva`  
**Documento de Referencia:** `SAED_2.0_DOCUMENTO_MAESTRO_COMPLETO_FINAL.txt`  

---

## 1. Brechas Funcionales Identificadas frente al Documento Maestro

| Requerimiento del Documento Maestro | Capa BD | Capa Backend | Capa Frontend | Estado Actual | Fase de Resolución en Plan v4.0 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Motor de Automatizaciones (Evento ➔ Condición ➔ Acción)** | Tablas `REGLAS_AUTOMATIZACION`, `ACCIONES_AUTOMATIZACION`, `EJECUCIONES_AUTOMATIZACION` creadas en Oracle | No existe scheduler ni motor de reglas en Spring Boot | No existe pantalla de configuración de automatizaciones | `FALTANTE (MISSING)` | **Fase 30** |
| **Medición de Consumos y Servicios Públicos** | Tabla `MEDICIONES_CONSUMO` creada en Oracle | No existe Controller ni Service de Consumo | No existe vista de lecturas de agua/gas/luz | `FALTANTE (MISSING)` | **Fase 28 / 31** |
| **Mascotas (Registro, Vacunas y Récord)** | Tablas `MASCOTAS`, `VACUNAS_MASCOTA` creadas en Oracle | API unificada en PersonaService pero sin endpoints dedicados `/api/v1/mascotas` | Gestionado dentro del perfil del residente; falta vista de administración | `PARCIAL (PARTIAL)` | **Fase 26** |
| **Poderes de Representación en Asambleas** | Tablas `PODERES_REPRESENTACION` creadas en Oracle | Backend de Asambleas incluye Quórum y Votaciones; falta delegación formal | Formulario de poder en `AsambleasAdminPage.jsx` | `PARCIAL (PARTIAL)` | **Fase 22** |
| **Integración Sandbox Oficial Wompi** | Tablas `TRANSACCIONES_PAGO` creadas en Oracle | Servicio `WompiServiceImpl` funcional con mock y variables de entorno | Widget en `AppShell.jsx` | `PARCIAL (PARTIAL)` | **Fase 7** |
| **Generación de PDFs con Estilo Institucional (Paz y Salvo / Recibos)** | Esquema listo | `PdfService` básico; requiere membrete formal con logo de la copropiedad | Descarga de PDF en cliente | `PARCIAL (PARTIAL)` | **Fase 29** |

---

## 2. Resumen de Brechas

* **Funcionalidades Faltantes Totales:** 2 dominios completos (*Automatizaciones* y *Consumos*).
* **Funcionalidades Parciales:** 4 capacidades complementarias (*Mascotas extendido*, *Poderes formales*, *Sandbox Wompi validado*, *PDFs con membrete*).
* **Resto de Funcionalidades (30 dominios):** Estructuras de base de datos, backend y frontend ya presentes en el código.
