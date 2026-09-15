# SAED 2.0 — MATRIZ COMPARATIVA DE OBJETOS: ORACLE XE vs ORACLE ATP CLOUD
**Fecha:** 5 de Septiembre de 2026  
**Fase:** CLOUD-01 — ATP Synchronization & Re-Certification  
**Entornos:**
- **Oracle XE 21c (Preproducción Local):** `localhost:1521/XEPDB1` — Schema `SAED_BASELINE_TEST_01`
- **Oracle ATP 19c (Producción Cloud):** `adb.sa-bogota-1.oraclecloud.com` — Schema `SAED_APP`

---

## 1. Inventario de Tablas Operativas y Conteos de Filas

| Tabla | Oracle XE (Local) | Oracle ATP (Cloud) | Estado Sincronización |
| :--- | :--- | :--- | :--- |
| `ORGANIZACIONES` | 2 registros | 2 registros | 🟢 IDÉNTICO |
| `PROPIEDADES` | 2 registros | 2 registros | 🟢 IDÉNTICO |
| `UNIDADES` | 2 registros | 2 registros | 🟢 IDÉNTICO |
| `ROLES` | 5 roles | 5 roles (añadido `PORTERO` ID 5) | 🟢 SINCRONIZADO |
| `USUARIOS` | 6 usuarios | 6 usuarios | 🟢 IDÉNTICO |
| `USUARIO_ASIGNACIONES` | 7 asignaciones | 7 asignaciones (IDs 1, 2, 3, 102, 204, 205, 206) | 🟢 SINCRONIZADO |
| `PERSONAS` | 5 registros | 5 registros | 🟢 IDÉNTICO |
| `RESIDENTES_UNIDAD` | 2 registros | 2 registros (Persona 4 vinculada a Unidad 1) | 🟢 SINCRONIZADO |
| `PORTERIAS` | 1 registro | 1 registro ('Portería Principal') | 🟢 SINCRONIZADO |
| `VISITANTES` | 1 registro | 1 registro (ID 10, 'Visitante Demo') | 🟢 SINCRONIZADO |
| `VISITAS` | 1 registro | 1 registro (ID 100, Unidad 1, QR) | 🟢 SINCRONIZADO |
| `QR_ACCESOS` | 1 registro | 1 registro (ID 100, Token ACTIVO) | 🟢 SINCRONIZADO |
| `PARQUEADEROS` | 3 registros | 3 registros (V-01, V-02, P-101) | 🟢 SINCRONIZADO |
| `VEHICULOS_VISITA` | 1 registro | 1 registro (ID 1, Placa 'DEM-123') | 🟢 SINCRONIZADO |
| `REGISTROS_ACCESO` | 1 registro | 1 registro (ID 1, Entrada) | 🟢 SINCRONIZADO |
| `PAQUETES` | 2 registros | 2 registros (PK-DEMO-001 RECIBIDO, PK-DEMO-002 ENTREGADO) | 🟢 SINCRONIZADO |
| `COMUNICADOS` | 1 registro | 1 registro (ID 1, 'Mantenimiento Preventivo') | 🟢 SINCRONIZADO |
| `NOTIFICACIONES` | 1 registro | 1 registro (ID 1, Usuario 4) | 🟢 SINCRONIZADO |
| `CONTRATOS` | 1 registro | 1 registro (ID 1, CNT-2026-001, ACTIVO) | 🟢 SINCRONIZADO |
| `CUOTAS` | 2 registros | 2 registros (Cuota 1 PAGADA, Cuota 2 PENDIENTE $250.000) | 🟢 SINCRONIZADO |
| `CARTERA` | 1 registro | 1 registro (Unidad 1, Saldo $250.000, AL_DIA) | 🟢 SINCRONIZADO |
| `PQRS` | 2 registros | 2 registros | 🟢 IDÉNTICO |
| `TIPOS_DOCUMENTO` | 2 registros | 2 registros (CC, NIT) | 🟢 IDÉNTICO |

---

## 2. Paquetes PL/SQL y Seguridad RLS / VPD

| Objeto PL/SQL | Tipo | Estado en ATP | Ajuste Quirúrgico Aplicado |
| :--- | :--- | :--- | :--- |
| `PKG_SAED_SESSION` | PACKAGE SPEC / BODY | `VALID` | Contexto de sesión Zero-Trust `SAED_CTX` activo. |
| `PKG_SAED_SECURITY_RLS` | PACKAGE SPEC / BODY | `VALID` | **Corregido:** `FN_FILTRO_UNIDAD` ahora filtra `QR_ACCESOS` y `VEHICULOS_VISITA` mediante subconsulta sobre `VISITAS` y `UNIDADES` (evitando error por ausencia de columna `id_unidad`). Implementada `FN_FILTRO_PERSONAS`. |
| `PKG_AUTH_BOOTSTRAP` | PACKAGE SPEC / BODY | `VALID` | Procedimientos de autenticación y carga de contextos operativos. |
| `PKG_SAED_AUDIT` | PACKAGE SPEC / BODY | `VALID` | Trazabilidad y auditoría de accesos. |

---

## 3. Matriz de Usuarios y Asignaciones Activas en ATP

| Usuario | ID Usuario | Rol Código | ID Asignación | Alcance | Org / Prop / Unidad | Estado |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `admin_global` | 1 | `SUPERADMIN` | 1 | GLOBAL | Org 0 / Prop 0 / Unidad NULL | `ACTIVA` |
| `admin` | 5 | `ADMIN_PROPIEDAD` | 205 | PROPIEDAD | Org 1 / Prop 1 / Unidad NULL | `ACTIVA` |
| `portero01` | 6 | `PORTERO` | 206 | PROPIEDAD | Org 1 / Prop 1 / Unidad NULL | `ACTIVA` |
| `camartinez` | 4 | `RESIDENTE` | 204 | UNIDAD | Org 1 / Prop 1 / Unidad 1 | `ACTIVA` |
| `residente_hor` | 2 | `RESIDENTE` | 2 | UNIDAD | Org 1 / Prop 1 / Unidad 1 | `ACTIVA` |
| `residente_sol` | 3 | `RESIDENTE` | 3 | UNIDAD | Org 2 / Prop 2 / Unidad 2 | `ACTIVA` |
