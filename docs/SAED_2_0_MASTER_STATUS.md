# SAED 2.0 — Master Status Tracker (Fuente Única de Estado)

**Plan Maestro Activo:** `Versión 4.0 — Definitiva (31 de Agosto de 2026)`  
**Última Actualización:** 31 de Agosto de 2026  
**Responsable:** Lead Architect & Security Auditor  

---

## 1. Estado Global del Proyecto

| Dimensión | Estado | Evidencia / Métrica |
| :--- | :--- | :--- |
| **Repositorio Git** | `Clean / Sincronizado` | Commit `3034b6d` en `origin/main` |
| **Secretos / Credenciales** | `Controlados / 0 Expuestos` | Secret scan limpio en `src/main` y `frontend/src` |
| **Backend Build & Tests** | `100% BUILD SUCCESS` | 115/115 tests pasando en `mvn clean test` (52.3s) |
| **Frontend Build** | `100% BUILD SUCCESS` | `npm run build` exitoso (0 errores, 2000 módulos) |
| **Base de Datos Oracle XE** | `100% VALID` | 96 tablas, 336 índices, 90 políticas RLS, 0 inválidos |
| **Nivel de Evidencia Global** | `E2 (Tests) / E3 (API Real)` | Tests ejecutándose contra instancia Oracle XE real |

---

## 2. Mapa de Fases del Plan Maestro v4.0

```text
[X] FASE 0: Congelación y Auditoría Real (Baseline) -> APROBADO
[ ] FASE 1: Matriz Definitiva de Bugs y Deuda Técnica (BUG_LEDGER.md) -> EN PROGRESO
[ ] FASE 2: Auditoría Completa de Arquitectura (Backend / Frontend)
[ ] FASE 3: Seguridad y Multi-Tenancy (Ataques Adversariales A a L)
[ ] FASE 4: Auditoría de Oracle (Normalización de Baseline V5.0 y Scripts)
[ ] FASE 5: Auditoría y Trazabilidad (Log Central Append-Only)
[ ] FASE 6: Estabilización de Módulos Existentes (Identity, Auth, Personas, etc.)
[ ] FASE 7: Wompi y Pagos (Integración Real + Webhooks + HMAC)
[ ] FASE 8: Finanzas Completas (Cartera, Presupuesto, Flujo de Caja, Conciliación)
[ ] FASE 9: Propiedades y Estructura Configurable (Edificios y Conjuntos)
[ ] FASE 10: Porterías y Accesos (Visitas, QR Dinámico, Múltiples Accesos)
[ ] FASE 11: Vehículos y Parqueaderos
[ ] FASE 12: Zonas Comunes
[ ] FASE 13: Reservas (Manejo de Concurrencia)
[ ] FASE 14: Mantenimiento (Preventivo y Correctivo)
[ ] FASE 15: Activos e Inventario
[ ] FASE 16: Proveedores y Contratistas
[ ] FASE 17: Obras y Remodelaciones
[ ] FASE 18: PQRS y SLA Configurable
[ ] FASE 19: Paquetería
[ ] FASE 20: Incidentes de Seguridad
[ ] FASE 21: Documentos y Reglamentos Versionados
[ ] FASE 22: Asambleas, Quórum, Votaciones y Actas
[ ] FASE 23: Sanciones y Debido Proceso (Descargos)
[ ] FASE 24: Seguros y Pólizas
[ ] FASE 25: Emergencias y Directorio
[ ] FASE 26: Mascotas
[ ] FASE 27: Comunicaciones y Notificaciones Segmentadas
[ ] FASE 28: Dashboards Especializados por Rol
[ ] FASE 29: Exportaciones Masivas (PDF / Excel / CSV)
[ ] FASE 30: Motor de Automatizaciones (Evento -> Condición -> Acción)
[ ] FASE 31: Portal Residente
[ ] FASE 32: Portal Portería
[ ] FASE 33: Frontend Hardening (Loading, Error, Empty states en 39 páginas)
[ ] FASE 34: Backend Hardening (Validación, Transacciones, Timeouts)
[ ] FASE 35: Manejo Global de Errores Estandarizado
[ ] FASE 36: Performance y Optimización de Consultas
[ ] FASE 37: Observabilidad y Logs Estructurados
[ ] FASE 38: Testing Completo (Unitarios, Integración, RLS, E2E)
[ ] FASE 39: Datos de Prueba Reproducibles (Dataset Multimodelo)
[ ] FASE 40: Planes y Límites de Suscripción
[ ] FASE 41: Configuración Operativa desde la Web
[ ] FASE 42: Depuración de Código Legacy
[ ] FASE 43: Documentación Técnica y Operativa Completa
[ ] FASE 44: CI/CD Pipeline (GitHub Actions)
[ ] FASE 45: Auditoría de Seguridad de Dependencias (CVEs)
[ ] FASE 46: Preparación para Producción
[ ] FASE 47: Migración a Oracle ATP Producción
[ ] FASE 48: Despliegue Backend en Render
[ ] FASE 49: Despliegue Frontend en Vercel
[ ] FASE 50: Smoke Tests en Producción Real
[ ] FASE 51: Pruebas de Seguridad en Producción
[ ] FASE 52: Recuperación y Continuidad Operativa
[ ] FASE 53: Auditoría Final y Cierre SAED 2.0
```

---

## 3. Estado de los Gates

* **Gate A (Código):** ✅ `Aprobado` (Compilación 100% limpia, 115 tests verdes).
* **Gate B (Seguridad):** 🟡 `En Verificación` (Alineación de matriz de ataques A a L en progreso).
* **Gate C (Base de Datos):** ✅ `Aprobado` (96 tablas, 90 políticas RLS activas, 0 inválidos).
* **Gate D (Funcionalidad):** 🟡 `En Progreso` (Fases 0 a 10 implementadas; 11 a 30 en hardening).
* **Gate E (Frontend):** 🟡 `En Progreso` (Páginas funcionales compilando; hardening de micro-estados pendiente).
* **Gate F (Integraciones):** 🟡 `Pendiente` (Validación con sandbox de Wompi / Brevo).
* **Gate G (Producción):** ⏳ `Pendiente` (Programado para Fases 46-50).
* **Gate H (Operación):** ⏳ `Pendiente` (Programado para Fases 51-53).
