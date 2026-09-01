# SAED 2.0 — Auditoría Completa de Row Level Security (RLS) y Multi-Tenancy

**Fecha:** 01 de Septiembre de 2026  
**Plan Maestro:** `Versión 4.0 — Definitiva`  
**Fase:** `Fase 1 — Auditoría Definitiva`  
**Auditor:** Principal Database & RLS Specialist  

---

## 1. Resumen de Políticas y Protección de Tablas

En la base de datos Oracle XE (`SAED_V39_FINAL_TEST`), se han verificado:
* **Total de Políticas RLS Activas:** **90 políticas** registradas en `ALL_POLICIES`.
* **Paquetes de Seguridad:** `PKG_SAED_SESSION` (Gestor de contexto en espacio de nombres `SAED_CTX`) y `PKG_SAED_SECURITY_RLS` (Generador de predicados SQL dinámicos).

---

## 2. Inventario de Funciones de Predicado RLS

| Función de Predicado | Ámbito de Protección | Tablas Asignadas | Lógica del Predicado Generado |
| :--- | :--- | :--- | :--- |
| `FN_FILTRO_ORGANIZACION` | Multi-Tenant Organización | `ORGANIZACIONES`, `ORGANIZACION_PROPIEDAD`, `MEMBRESIAS`, `MEMBRESIAS_HISTORIAL`, `CONCEPTOS_COBRO`, `PROVEEDORES`, `PLANTILLAS_NOTIFICACION`, `REGLAS_AUTOMATIZACION`, `DOCUMENTOS` | `id_organizacion = SYS_CONTEXT('SAED_CTX', 'ID_ORGANIZACION')` |
| `FN_FILTRO_PROPIEDAD` | Multi-Tenant Propiedad | `PROPIEDADES`, `PROPIEDAD_CONFIGURACION`, `BLOQUES`, `PRESUPUESTOS`, `GASTOS`, `CONCILIACIONES`, `PORTERIAS`, `ACCESOS_CONFIGURADOS`, `VISITANTES`, `REGISTROS_ACCESO`, `PARQUEADEROS`, `CATALOGO_ZONAS`, `ZONAS_COMUNES`, `BLOQUEOS_ZONA`, `ACTIVOS`, `MANTENIMIENTOS`, `CONTRATOS_PROVEEDOR`, `TRABAJADORES`, `PQRS_SLA_CONFIGURACION`, `PQRS_TICKETS`, `PQRS_TRAZABILIDAD`, `PAQUETES`, `INCIDENTES`, `INCIDENTE_INVOLUCRADOS`, `COMUNICADOS`, `NOTIFICACIONES`, `ENCUESTAS`, `ENCUESTA_OPCIONES`, `ENCUESTA_RESPUESTAS`, `ACCIONES_AUTOMATIZACION`, `EJECUCIONES_AUTOMATIZACION`, `VERSIONES_DOCUMENTO`, `ASAMBLEAS`, `ASISTENCIAS_ASAMBLEA`, `PODERES_REPRESENTACION`, `VOTACIONES`, `VOTOS`, `ACTAS_ASAMBLEA`, `SANCIONES`, `SANCION_DESCARGOS`, `POLIZAS_SEGURO`, `PLANES_EMERGENCIA`, `CONTACTOS_EMERGENCIA`, `MEDICIONES_CONSUMO`, `REPORTES_CONFIGURADOS`, `HISTORIAL_REPORTES`, `ALERTAS_ADMIN` | `id_propiedad = SYS_CONTEXT('SAED_CTX', 'ID_PROPIEDAD')` |
| `FN_FILTRO_UNIDAD` | Ámbito Unidad Residencial | `UNIDADES`, `PROPIETARIOS_UNIDAD`, `RESIDENTES_UNIDAD`, `TUTORES`, `CONTRATOS`, `CONTRATO_RESIDENTE`, `CUOTAS`, `PAGOS`, `PAGO_DETALLE`, `TRANSACCIONES_PAGO`, `MULTAS`, `CARTERA`, `PAZ_Y_SALVOS`, `VISITAS`, `QR_ACCESOS`, `VEHICULOS`, `ASIGNACIONES_PARQUEADERO`, `VEHICULOS_VISITA`, `MASCOTAS`, `RESERVAS`, `OBRAS`, `OBRA_TRABAJADORES` | `id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = :prop)` |
| `FN_FILTRO_ASIGNACION` | Asignaciones de Usuario | `USUARIO_ASIGNACIONES` | `id_usuario = :usr` (para RESIDENTE/BOOTSTRAP) o `id_organizacion = :org` (para ADMINs) |
| `FN_FILTRO_USUARIOS` | Usuarios de la Organización | `USUARIOS` | `id_usuario IN (SELECT id_usuario FROM USUARIO_ASIGNACIONES WHERE id_organizacion = :org)` o `id_usuario = :usr` (para RESIDENTE) |
| `FN_FILTRO_GLOBAL_MUTATE` | Mutaciones Globales | `ADMINISTRADORES_SAED` | `SUPERADMIN` (`1=1`) o `1=0` para el resto |
| `FN_FILTRO_GLOBAL_READONLY` | Catálogos Globales | `ROLES`, `PERMISOS`, `TIPOS_DOCUMENTO`, `TIPOS_PROPIEDAD`, `TIPOS_UNIDAD`, `PLANES`, `MODULOS`, `PLAN_MODULOS` | `1=1` (Lectura libre para usuarios autenticados) |

---

## 3. Hallazgos Críticos de RLS

### 1. `RLS-001` (P0) — Falta de Restricción de Unidad para Rol Residente en `FN_FILTRO_UNIDAD`
* **Problema:** En `PKG_SAED_SECURITY_RLS.FN_FILTRO_UNIDAD`, cuando el usuario activo tiene rol `RESIDENTE`, la función retorna:
  ```sql
  RETURN 'id_unidad IN (SELECT id_unidad FROM UNIDADES WHERE id_propiedad = ' || v_prop || ')';
  ```
* **Impacto:** A nivel de base de datos Oracle, un residente tiene permiso de lectura sobre todas las cuotas, pagos y multas de **toda la propiedad**, no solo de su apartamento. El aislamiento por unidad recae hoy en la cláusula Java `WHERE ID_UNIDAD = :u`.
* **Solución requerida para Fase 3:** En `FN_FILTRO_UNIDAD`, agregar:
  ```sql
  IF v_rol = 'RESIDENTE' THEN
      RETURN 'id_unidad IN (SELECT id_unidad FROM RESIDENTES_UNIDAD ru JOIN USUARIOS u ON ru.id_persona = u.id_persona WHERE u.id_usuario = ' || v_usr || ')';
  END IF;
  ```

### 2. `RLS-002` (P1) — Tablas Globales y Bypasses
* **Problema:** Las tablas `TIPOS_DOCUMENTO`, `ROLES`, `PERMISOS`, `PLANES` y `MODULOS` no tienen RLS de mutación (solo lectura `1=1`), por lo que un usuario con privilegios de conexión directos podría alterar los planes si no se restringe `DML`.
* **Solución:** Aplicar `FN_FILTRO_GLOBAL_MUTATE` a todas las tablas de catálogo global para operaciones `INSERT`, `UPDATE` y `DELETE`.

### 3. `RLS-003` (P2) — Verificación de Operaciones `MERGE` y `INSERT ... VALUES`
* **Problema:** Las políticas RLS actuales protegen `SELECT`, `INSERT`, `UPDATE` y `DELETE` (`statement_types => 'SELECT,INSERT,UPDATE,DELETE'`). Sin embargo, en Oracle XE, las inserciones que omiten la columna particionante pueden fallar silenciosamente o causar `ORA-28115: violación de política con opción CHECK` si no se inicializa la columna en el payload del repository.
