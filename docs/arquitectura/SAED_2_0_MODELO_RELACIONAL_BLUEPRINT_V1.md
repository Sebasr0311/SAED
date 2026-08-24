# SAED 2.0 — Especificación Conceptual y Lógica del Modelo Relacional Definitivo (v1)

> **Motor Objetivo:** Oracle Autonomous Transaction Processing (ATP 19c Enterprise)  
> **Paradigma:** Relacional Puro (Integridad Referencial Estricta, Multi-Tenant Lógico, Zero Trust)  
> **Fecha:** Agosto 2026  
> **Documento:** Blueprint Arquitectónico de Base de Datos v1.0

---

## 1. INVENTARIO DEFINITIVO DE ENTIDADES

El modelo Core de SAED 2.0 se divide en 9 dominios relacionales cohesivos:

### A. Catálogos y Plataforma Global
1. `TIPOS_DOCUMENTO`: Catálogo de tipos de identificación personal (CC, CE, NIT, Pasaporte, etc.).
2. `PLANES`: Catálogo de planes de suscripción comercial (Básico, Profesional, Empresarial).
3. `MODULOS`: Módulos funcionales activables (Visitas, Pagos, Contratos, Buzón, Multas, Parqueaderos).
4. `PLAN_MODULOS`: Tabla de cruce `n:m` entre planes y módulos incluidos.
5. `TIPOS_PROPIEDAD`: Tipologías de propiedades (Edificio Residencial, Conjunto Cerrado, Centro Comercial).
6. `TIPOS_UNIDAD`: Tipologías de unidades físicas (Apartamento, Casa, Local, Oficina, Bodega, Parqueadero Independiente).
7. `TIPOS_VEHICULO`: Catálogo de tipos de vehículos (Automóvil, Motocicleta, Bicicleta, Camioneta).

### B. Identidad, Seguridad y Autorización (RBAC + Scope)
8. `PERSONAS`: Entidad física/jurídica real (datos biográficos independientes de roles en el sistema).
9. `USUARIOS`: Cuenta de autenticación y credenciales (login, password hash, estado).
10. `ROLES`: Definición de roles de seguridad (SUPERADMIN, ADMIN_ORGANIZACION, ADMIN_GENERAL, ADMIN_PROPIEDAD, PORTERO, RESIDENTE, etc.).
11. `PERMISOS`: Acciones atómicas del sistema (`unidades:read`, `pagos:create`, `visitas:scan`, etc.).
12. `ROL_PERMISOS`: Tabla de cruce `n:m` entre roles y permisos.
13. `USUARIO_ASIGNACIONES`: Vinculación dinámica entre Usuario, Rol y Alcance territorial (`GLOBAL`, `ORGANIZACION`, `PROPIEDAD`, `UNIDAD`).

### C. Organizaciones y Membresías
14. `ORGANIZACIONES`: Clientes corporativos de SAED (empresas de administración, constructoras, juntas).
15. `ORGANIZACION_CONFIGURACION`: Parámetros operativos y de facturación específicos de la organización.
16. `MEMBRESIAS`: Historial de suscripciones y vigencias contractuales de cada organización con SAED.

### D. Propiedades, Bloques y Unidades
17. `PROPIEDADES`: Inmuebles o complejos administrados (Torre del Parque, Condominio El Roble).
18. `PROPIEDAD_CONFIGURACION`: Parámetros relacionales de operación de la propiedad (tolerancias, horarios de portería, límites).
19. `BLOQUES`: Agrupadores físicos opcionales (Torre 1, Torre 2, Manzana A, Etapa 3).
20. `UNIDADES`: Espacios físicos individuales privativos (Apto 101, Casa 24, Local 3B).

### E. Dominio Legal y Tenencia (Propiedad, Residencia y Contratos)
21. `PROPIETARIOS_UNIDAD`: Registro legal de títulos de propiedad `n:m` (quién es dueño de qué unidad y en qué porcentaje).
22. `CONTRATOS`: Contratos de arrendamiento o tenencia formal sobre una unidad.
23. `RESIDENTES_UNIDAD`: Ocupantes reales de la unidad (arrendatarios, familiares, autorizados).
24. `TUTORES_RESIDENTE`: Representantes legales de menores de edad o personas tuteladas.

### F. Operación Financiera
25. `CONCEPTOS_COBRO`: Catálogo de conceptos liquidables por propiedad (Expensa Ordinaria, Extraordinaria, Alquiler Parqueadero, Multa).
26. `CUOTAS`: Cuentas por cobrar generadas periódicamente a cada unidad.
27. `PAGOS`: Recibos de caja y abonos aplicados a cuotas.
28. `MULTAS`: Sanciones económicas impuestas a unidades por infracción al reglamento.
29. `TRANSACCIONES_PASARELA`: Trazabilidad de pagos online (Wompi, pasarelas bancarias, webhooks).

### G. Visitas, QR y Accesos
30. `VISITANTES`: Registro de personas externas que ingresan a una propiedad.
31. `VISITANTES_FRECUENTES`: Listas blancas de visitantes autorizados por una unidad/residente.
32. `VISITAS`: Intención o autorización de visita programada o no programada.
33. `QR_ACCESOS`: Token efímero de acceso vehicular/peatonal de un solo uso.
34. `REGISTROS_ACCESO`: Bitácora física de entradas y salidas registradas en portería.

### H. Parqueaderos y Vehículos
35. `PARQUEADEROS`: Espacios de estacionamiento físicos de la propiedad.
36. `VEHICULOS`: Registro de automotores vinculados a personas.
37. `VEHICULOS_UNIDAD`: Asignación de vehículos fijos a unidades.
38. `VEHICULOS_VISITA`: Registro de vehículos temporales asociados a una visita activa.

### I. Comunicación, Buzón y Auditoría
39. `COMUNICADOS`: Circulares y avisos oficiales de la administración a residentes.
40. `BUZON_PAQUETERIA`: Registro y entrega de encomiendas/correspondencia en portería.
41. `QUEJAS_SUGERENCIAS`: Sistema de tickets PQRS entre residentes y administración.
42. `AUDITORIA_LOG`: Registro inmutable (Append-Only) de eventos y cambios en el sistema.

---

## 2. PROPÓSITO Y 3. NIVEL DE ALCANCE (SCOPE) DE CADA ENTIDAD

| # | Entidad | Propósito de Dominio | Nivel de Alcance (Scope) |
|---|---|---|---|
| 1 | `TIPOS_DOCUMENTO` | Catálogo universal de identificación legal | `GLOBAL` |
| 2 | `PLANES` | Definición comercial de tiers de servicio de SAED | `GLOBAL` |
| 3 | `MODULOS` | Inventario de capacidades de la plataforma | `GLOBAL` |
| 4 | `PLAN_MODULOS` | Matriz de funcionalidades por plan | `GLOBAL` |
| 5 | `TIPOS_PROPIEDAD` | Catálogo de tipos de inmuebles soportados | `GLOBAL` |
| 6 | `TIPOS_UNIDAD` | Catálogo de tipologías de espacios privativos | `GLOBAL` |
| 7 | `TIPOS_VEHICULO` | Catálogo vehicular para control de acceso | `GLOBAL` |
| 8 | `PERSONAS` | Entidad ontológica única para ciudadanos/empresas | `GLOBAL` |
| 9 | `USUARIOS` | Cuenta de autenticación y credenciales de acceso | `GLOBAL` (1 por persona) |
| 10 | `ROLES` | Plantilla de privilegios de seguridad | `GLOBAL` / `ORGANIZACION` |
| 11 | `PERMISOS` | Acciones atómicas de autorización técnica | `GLOBAL` |
| 12 | `ROL_PERMISOS` | Asociación de permisos a roles | `GLOBAL` / `ORGANIZACION` |
| 13 | `USUARIO_ASIGNACIONES` | Asignación de rol + alcance a un usuario | `USUARIO` / Contextual |
| 14 | `ORGANIZACIONES` | Tenant cliente corporativo de SAED | `ORGANIZACION` |
| 15 | `ORGANIZACION_CONFIGURACION` | Parámetros globales de la organización | `ORGANIZACION` |
| 16 | `MEMBRESIAS` | Histórico contractual de suscripciones | `ORGANIZACION` |
| 17 | `PROPIEDADES` | Edificio, conjunto o complejo administrado | `PROPIEDAD` |
| 18 | `PROPIEDAD_CONFIGURACION` | Parámetros operacionales locales | `PROPIEDAD` |
| 19 | `BLOQUES` | Sub-división física de la propiedad | `PROPIEDAD` |
| 20 | `UNIDADES` | Inmueble privativo concreto | `UNIDAD` |
| 21 | `PROPIETARIOS_UNIDAD` | Registro de titularidad legal | `UNIDAD` |
| 22 | `CONTRATOS` | Relación contractual de arrendamiento | `UNIDAD` |
| 23 | `RESIDENTES_UNIDAD` | Ocupación física habitual | `UNIDAD` |
| 24 | `TUTORES_RESIDENTE` | Tutela de residentes menores | `UNIDAD` / `PERSONA` |
| 25 | `CONCEPTOS_COBRO` | Tipos de cargos financieros | `PROPIEDAD` |
| 26 | `CUOTAS` | Cartera / Cuentas por cobrar | `UNIDAD` |
| 27 | `PAGOS` | Recaudos y aplicaciones financieras | `UNIDAD` |
| 28 | `MULTAS` | Sanciones disciplinarias | `UNIDAD` |
| 29 | `TRANSACCIONES_PASARELA` | Logs de pasarela de pago | `ORGANIZACION` / `PROPIEDAD` |
| 30 | `VISITANTES` | Identidad de personas externas | `PROPIEDAD` |
| 31 | `VISITANTES_FRECUENTES` | Reglas de acceso recurrente | `UNIDAD` |
| 32 | `VISITAS` | Autorizaciones de ingreso puntual | `PROPIEDAD` / `UNIDAD` |
| 33 | `QR_ACCESOS` | Tokens criptográficos efímeros | `PROPIEDAD` / `VISITA` |
| 34 | `REGISTROS_ACCESO` | Auditoría de molinetes y talanqueras | `PROPIEDAD` |
| 35 | `PARQUEADEROS` | Inventario de celdas de parqueo | `PROPIEDAD` |
| 36 | `VEHICULOS` | Padrón vehicular registrado | `GLOBAL` / `PERSONA` |
| 37 | `VEHICULOS_UNIDAD` | Asignación de vehículos fijos | `UNIDAD` |
| 38 | `VEHICULOS_VISITA` | Parqueo temporal de visitantes | `PROPIEDAD` / `VISITA` |
| 39 | `COMUNICADOS` | Tablón de avisos | `PROPIEDAD` |
| 40 | `BUZON_PAQUETERIA` | Custodia de paquetes en portería | `UNIDAD` |
| 41 | `QUEJAS_SUGERENCIAS` | PQRS y tickets de atención | `UNIDAD` / `PROPIEDAD` |
| 42 | `AUDITORIA_LOG` | Bitácora forense de seguridad | `GLOBAL` / `ORGANIZACION` / `PROPIEDAD` |

---

## 4. RELACIONES, 5. PK, 6. FK, 7. OBLIGATORIEDAD Y 8. REGLAS ON DELETE

| Tabla | PK | FKs (Tabla Destino, Campo) | Nullability FK | Regla ON DELETE | Justificación de Negocio |
|---|---|---|---|---|---|
| `PLAN_MODULOS` | `(id_plan, id_modulo)` | `PLANES(id_plan)`<br>`MODULOS(id_modulo)` | NOT NULL<br>NOT NULL | `CASCADE`<br>`CASCADE` | Tabla de cruce pura; si se elimina un plan o módulo, se borra su vínculo. |
| `PERSONAS` | `id_persona` | `TIPOS_DOCUMENTO(id_tipo_doc)` | NOT NULL | `RESTRICT` | Una persona no puede existir sin tipo de documento válido. |
| `USUARIOS` | `id_usuario` | `PERSONAS(id_persona)` | NOT NULL | `RESTRICT` | No se pueden borrar personas que tengan cuentas de usuario activas. |
| `ROL_PERMISOS` | `(id_rol, id_permiso)` | `ROLES(id_rol)`<br>`PERMISOS(id_permiso)` | NOT NULL<br>NOT NULL | `CASCADE`<br>`CASCADE` | Cruce puro de privilegios. |
| `USUARIO_ASIGNACIONES` | `id_asignacion` | `USUARIOS(id_usuario)`<br>`ROLES(id_rol)`<br>`ORGANIZACIONES(id_org)`<br>`PROPIEDADES(id_propiedad)`<br>`UNIDADES(id_unidad)` | NOT NULL<br>NOT NULL<br>NULL (Superadmin)<br>NULL (Admin Org)<br>NULL (Admin Prop) | `CASCADE`<br>`RESTRICT`<br>`CASCADE`<br>`CASCADE`<br>`CASCADE` | Si se elimina el usuario o la propiedad, se eliminan sus asignaciones. El rol no se puede borrar si está asignado (`RESTRICT`). |
| `ORGANIZACIONES` | `id_organizacion` | (Sin FKs obligatorias directas) | - | - | Entidad raíz de cliente. |
| `ORGANIZACION_CONFIG` | `id_organizacion` | `ORGANIZACIONES(id_organizacion)` | NOT NULL | `CASCADE` | Configuración 1:1 dependiente de la organización. |
| `MEMBRESIAS` | `id_membresia` | `ORGANIZACIONES(id_organizacion)`<br>`PLANES(id_plan)` | NOT NULL<br>NOT NULL | `CASCADE`<br>`RESTRICT` | Histórico de suscripción. El plan no se puede borrar si hay membresías vinculadas. |
| `PROPIEDADES` | `id_propiedad` | `ORGANIZACIONES(id_org)`<br>`TIPOS_PROPIEDAD(id_tipo_prop)` | NOT NULL<br>NOT NULL | `RESTRICT`<br>`RESTRICT` | Una organización con propiedades no se borra accidentalmente. |
| `PROPIEDAD_CONFIG` | `id_propiedad` | `PROPIEDADES(id_propiedad)` | NOT NULL | `CASCADE` | Configuración 1:1 de la propiedad. |
| `BLOQUES` | `id_bloque` | `PROPIEDADES(id_propiedad)` | NOT NULL | `CASCADE` | Si se borra la propiedad, se borran sus torres/bloques. |
| `UNIDADES` | `id_unidad` | `PROPIEDADES(id_propiedad)`<br>`BLOQUES(id_bloque)`<br>`TIPOS_UNIDAD(id_tipo_unidad)` | NOT NULL<br>NULL (Opcional)<br>NOT NULL | `RESTRICT`<br>`SET NULL`<br>`RESTRICT` | La unidad pertenece a la propiedad. Si se borra un bloque, la unidad queda sin bloque asignado. |
| `PROPIETARIOS_UNIDAD` | `(id_unidad, id_persona)` | `UNIDADES(id_unidad)`<br>`PERSONAS(id_persona)` | NOT NULL<br>NOT NULL | `CASCADE`<br>`RESTRICT` | Matriz de titularidad legal. |
| `CONTRATOS` | `id_contrato` | `UNIDADES(id_unidad)`<br>`PERSONAS(id_arrendatario_ppal)`<br>`USUARIOS(id_creado_por)` | NOT NULL<br>NOT NULL<br>NOT NULL | `RESTRICT`<br>`RESTRICT`<br>`RESTRICT` | Trazabilidad contractual inmutable. |
| `RESIDENTES_UNIDAD` | `id_residente_unidad`| `UNIDADES(id_unidad)`<br>`PERSONAS(id_persona)`<br>`CONTRATOS(id_contrato)` | NOT NULL<br>NOT NULL<br>NULL (Propietario) | `CASCADE`<br>`RESTRICT`<br>`SET NULL` | Vínculo de habitabilidad física. |
| `TUTORES_RESIDENTE` | `(id_menor, id_tutor)` | `PERSONAS(id_menor)`<br>`PERSONAS(id_tutor)` | NOT NULL<br>NOT NULL | `CASCADE`<br>`RESTRICT` | Tutela legal de menores. |
| `CONCEPTOS_COBRO` | `id_concepto` | `PROPIEDADES(id_propiedad)` | NOT NULL | `RESTRICT` | Catálogo de cargos por propiedad. |
| `CUOTAS` | `id_cuota` | `UNIDADES(id_unidad)`<br>`CONCEPTOS_COBRO(id_concepto)`<br>`CONTRATOS(id_contrato)` | NOT NULL<br>NOT NULL<br>NULL | `RESTRICT`<br>`RESTRICT`<br>`SET NULL` | Cartera financiera; inmutable ante borrados. |
| `PAGOS` | `id_pago` | `CUOTAS(id_cuota)`<br>`USUARIOS(id_registrado_por)` | NOT NULL<br>NOT NULL | `RESTRICT`<br>`RESTRICT` | Recaudo financiero inmutable. |
| `MULTAS` | `id_multa` | `UNIDADES(id_unidad)`<br>`USUARIOS(id_impuesta_por)`<br>`CUOTAS(id_cuota_generada)`| NOT NULL<br>NOT NULL<br>NULL | `RESTRICT`<br>`RESTRICT`<br>`SET NULL` | Sanciones disciplinarias. |
| `TRANSACCIONES_PASARELA`| `id_transaccion` | `ORGANIZACIONES(id_org)`<br>`PROPIEDADES(id_propiedad)`<br>`CUOTAS(id_cuota)` | NOT NULL<br>NOT NULL<br>NULL | `RESTRICT`<br>`RESTRICT`<br>`SET NULL` | Auditoría de pasarela de pago. |
| `VISITANTES` | `id_visitante` | `PROPIEDADES(id_propiedad)`<br>`PERSONAS(id_persona)` | NOT NULL<br>NULL | `CASCADE`<br>`SET NULL` | Visitante contextual a la propiedad. |
| `VISITANTES_FRECUENTES`| `id_frecuente` | `UNIDADES(id_unidad)`<br>`VISITANTES(id_visitante)` | NOT NULL<br>NOT NULL | `CASCADE`<br>`RESTRICT` | Reglas de acceso frecuente. |
| `VISITAS` | `id_visita` | `PROPIEDADES(id_propiedad)`<br>`UNIDADES(id_unidad)`<br>`PERSONAS(id_autoriza)`<br>`VISITANTES(id_visitante_ppal)`| NOT NULL<br>NOT NULL<br>NOT NULL<br>NOT NULL | `RESTRICT`<br>`RESTRICT`<br>`RESTRICT`<br>`RESTRICT` | Evento de visita inmutable. |
| `QR_ACCESOS` | `id_qr` | `VISITAS(id_visita)`<br>`PROPIEDADES(id_propiedad)` | NOT NULL<br>NOT NULL | `CASCADE`<br>`RESTRICT` | Token efímero de acceso. |
| `REGISTROS_ACCESO` | `id_acceso` | `PROPIEDADES(id_propiedad)`<br>`VISITAS(id_visita)`<br>`USUARIOS(id_portero)` | NOT NULL<br>NULL (Residente)<br>NOT NULL | `RESTRICT`<br>`SET NULL`<br>`RESTRICT` | Trazabilidad forense de accesos. |
| `PARQUEADEROS` | `id_parqueadero` | `PROPIEDADES(id_propiedad)`<br>`UNIDADES(id_unidad_asignada)`| NOT NULL<br>NULL (Visitante/Libre)| `CASCADE`<br>`SET NULL` | Inventario físico de bahías. |
| `VEHICULOS` | `id_vehiculo` | `TIPOS_VEHICULO(id_tipo_veh)`<br>`PERSONAS(id_propietario)` | NOT NULL<br>NULL | `RESTRICT`<br>`SET NULL` | Padrón vehicular. |
| `VEHICULOS_UNIDAD` | `(id_unidad, id_vehiculo)`| `UNIDADES(id_unidad)`<br>`VEHICULOS(id_vehiculo)` | NOT NULL<br>NOT NULL | `CASCADE`<br>`RESTRICT` | Asignación de vehículos fijos. |
| `VEHICULOS_VISITA` | `id_vehiculo_visita` | `VISITAS(id_visita)`<br>`VEHICULOS(id_vehiculo)`<br>`PARQUEADEROS(id_parqueadero)`| NOT NULL<br>NOT NULL<br>NULL | `CASCADE`<br>`RESTRICT`<br>`SET NULL` | Asignación temporal de celda. |
| `COMUNICADOS` | `id_comunicado` | `PROPIEDADES(id_propiedad)`<br>`USUARIOS(id_publicado_por)` | NOT NULL<br>NOT NULL | `CASCADE`<br>`RESTRICT` | Circulares informativas. |
| `BUZON_PAQUETERIA` | `id_paquete` | `UNIDADES(id_unidad)`<br>`USUARIOS(id_recibido_por)`<br>`USUARIOS(id_entregado_por)`| NOT NULL<br>NOT NULL<br>NULL | `RESTRICT`<br>`RESTRICT`<br>`SET NULL` | Custodia de paquetes en portería. |
| `QUEJAS_SUGERENCIAS` | `id_ticket` | `PROPIEDADES(id_propiedad)`<br>`UNIDADES(id_unidad)`<br>`PERSONAS(id_creado_por)`<br>`USUARIOS(id_asignado_a)` | NOT NULL<br>NOT NULL<br>NOT NULL<br>NULL | `RESTRICT`<br>`RESTRICT`<br>`RESTRICT`<br>`SET NULL` | Sistema de PQRS. |
| `AUDITORIA_LOG` | `id_log` | `ORGANIZACIONES(id_org)`<br>`PROPIEDADES(id_propiedad)`<br>`USUARIOS(id_usuario)` | NULL (System)<br>NULL (Org event)<br>NULL (Anon) | `SET NULL`<br>`SET NULL`<br>`SET NULL` | Registro inmutable de eventos. |

---

## 9. RESTRICCIONES UNIQUE E ÍNDICES BASADOS EN FUNCIONES

### Solución Definitiva al Comportamiento de `NULL` en Oracle:
En Oracle Database, una restricción `UNIQUE (col1, col2, col3)` **no restringe duplicados** si alguna columna es `NULL`. Para garantizar la integridad en entidades jerárquicas opcionales se implementarán índices únicos basados en funciones:

1. **Unicidad de Unidades:**
   ```sql
   CREATE UNIQUE INDEX uk_unidad_prop_bloque_num 
   ON UNIDADES (id_propiedad, NVL(id_bloque, -1), UPPER(TRIM(identificador)));
   ```
2. **Unicidad de Parqueaderos:**
   ```sql
   CREATE UNIQUE INDEX uk_parqueadero_prop_codigo 
   ON PARQUEADEROS (id_propiedad, UPPER(TRIM(codigo_celda)));
   ```
3. **Unicidad de Bloques:**
   ```sql
   CREATE UNIQUE INDEX uk_bloque_prop_nombre 
   ON BLOQUES (id_propiedad, UPPER(TRIM(nombre)));
   ```
4. **Unicidad de Personas por Documento:**
   ```sql
   CREATE UNIQUE INDEX uk_persona_documento 
   ON PERSONAS (id_tipo_doc, UPPER(TRIM(numero_documento)));
   ```
5. **Unicidad de Cuentas de Usuario:**
   ```sql
   CREATE UNIQUE INDEX uk_usuario_username 
   ON USUARIOS (UPPER(TRIM(username)));
   ```
6. **Unicidad de Vehículos por Placa:**
   ```sql
   CREATE UNIQUE INDEX uk_vehiculo_placa 
   ON VEHICULOS (UPPER(TRIM(placa)));
   ```
7. **Unicidad de Asignaciones de Seguridad (Anti-Duplicación de Rol/Scope):**
   ```sql
   CREATE UNIQUE INDEX uk_usuario_asig_scope 
   ON USUARIO_ASIGNACIONES (id_usuario, id_rol, NVL(id_organizacion, -1), NVL(id_propiedad, -1), NVL(id_unidad, -1));
   ```
8. **Unicidad de Visitante por Propiedad:**
   ```sql
   CREATE UNIQUE INDEX uk_visitante_prop_doc 
   ON VISITANTES (id_propiedad, id_tipo_doc, UPPER(TRIM(numero_documento)));
   ```

---

## 10. RESTRICCIONES CHECK (INTEGRIDAD DE DOMINIO)

- `MEMBRESIAS.estado`: `CHECK (estado IN ('ACTIVA', 'SUSPENDIDA', 'VENCIDA', 'PRUEBA', 'CANCELADA'))`
- `MEMBRESIAS.fechas`: `CHECK (fecha_fin >= fecha_inicio)`
- `UNIDADES.estado`: `CHECK (estado IN ('DISPONIBLE', 'OCUPADA', 'MANTENIMIENTO', 'BLOQUEADA'))`
- `CONTRATOS.estado`: `CHECK (estado IN ('BORRADOR', 'ACTIVO', 'FINALIZADO', 'RESCINDIDO'))`
- `CONTRATOS.fechas`: `CHECK (fecha_fin >= fecha_inicio)`
- `CUOTAS.estado`: `CHECK (estado IN ('PENDIENTE', 'PAGADA_PARCIAL', 'PAGADA', 'EN_MORA', 'ANULADA'))`
- `CUOTAS.saldo`: `CHECK (saldo_pendiente >= 0 AND saldo_pendiente <= monto_total)`
- `PAGOS.monto`: `CHECK (monto_pagado > 0)`
- `PAGOS.metodo`: `CHECK (metodo_pago IN ('EFECTIVO', 'TRANSFERENCIA', 'WOMPI', 'CONSIGNACION', 'TARJETA'))`
- `VISITAS.estado`: `CHECK (estado IN ('PROGRAMADA', 'EN_CURSO', 'FINALIZADA', 'EXPIRADA', 'CANCELADA'))`
- `QR_ACCESOS.estado`: `CHECK (usado IN (0, 1))`
- `PARQUEADEROS.tipo`: `CHECK (tipo_uso IN ('PRIVADO_UNIDAD', 'VISITANTE', 'COMUNAL', 'DISCAPACITADOS', 'CARGA'))`
- `ROLES.nivel_alcance`: `CHECK (nivel_alcance IN ('GLOBAL', 'ORGANIZACION', 'PROPIEDAD', 'UNIDAD'))`
- `AUDITORIA_LOG.resultado`: `CHECK (resultado IN ('EXITOSO', 'DENEGADO', 'FALLO'))`

---

## 11. ÍNDICES DE RENDIMIENTO REQUERIDOS

En Oracle Database, **toda Foreign Key sin índice genera un bloqueo de tabla (Table-Level Share Lock)** cuando se actualiza o elimina la tabla padre. Además de los índices PK/UK, se crearán índices `B-Tree` en:

1. **FKs de Seguridad y Jerarquía:**
   - `IDX_FK_ASIG_USUARIO` en `USUARIO_ASIGNACIONES(id_usuario)`
   - `IDX_FK_ASIG_PROP` en `USUARIO_ASIGNACIONES(id_propiedad)`
   - `IDX_FK_PROP_ORG` en `PROPIEDADES(id_organizacion)`
   - `IDX_FK_UNID_PROP` en `UNIDADES(id_propiedad)`
   - `IDX_FK_UNID_BLOQUE` en `UNIDADES(id_bloque)`
2. **FKs Operativas de Alta Frecuencia:**
   - `IDX_FK_CUOTA_UNIDAD` en `CUOTAS(id_unidad, estado)`
   - `IDX_FK_PAGO_CUOTA` en `PAGOS(id_cuota)`
   - `IDX_FK_VISITA_PROP_ESTADO` en `VISITAS(id_propiedad, estado, fecha_programada)`
   - `IDX_FK_QR_TOKEN` en `QR_ACCESOS(codigo_hash, usado, fecha_expiracion)`
   - `IDX_FK_ACCESO_PROP_FECHA` en `REGISTROS_ACCESO(id_propiedad, fecha_ingreso)`
   - `IDX_FK_AUDIT_PROP_FECHA` en `AUDITORIA_LOG(id_propiedad, fecha_evento)`
   - `IDX_FK_AUDIT_ORG_FECHA` en `AUDITORIA_LOG(id_organizacion, fecha_evento)`

---

## 12. REGLAS DE INTEGRIDAD Y COHERENCIA DE NEGOCIO

1. **Coherencia de Scope en Asignaciones:** Un trigger `TRG_CHK_ASIGNACION_SCOPE` verificará que:
   - Si el rol es `GLOBAL`, `id_organizacion`, `id_propiedad` e `id_unidad` deben ser `NULL`.
   - Si el rol es `ORGANIZACION`, `id_organizacion` debe ser obligatorio y `id_propiedad`/`id_unidad` deben ser `NULL`.
   - Si el rol es `PROPIEDAD`, `id_propiedad` debe ser obligatorio.
   - Si el rol es `UNIDAD`, `id_unidad` e `id_propiedad` deben ser obligatorios y coherentes.
2. **Coherencia Territorial:** Si una visita o cuota referencia a una `id_unidad`, la unidad debe pertenecer a la misma `id_propiedad` que la visita.
3. **Consumo Único de QR:** Un trigger `TRG_QR_CONSUMIR` garantizará atómicamente que al marcar `usado = 1`, la fecha actual sea `<= fecha_expiracion` y `usado` previo sea `0`.
4. **Actualización de Saldos de Cuota:** Cada insert en `PAGOS` recalculará el `saldo_pendiente` de la cuota y actualizará su estado a `PAGADA` o `PAGADA_PARCIAL` dentro de la misma transacción.

---

## 13. ALMACENAMIENTO DE `id_organizacion` (DIRECTO VS. HEREDADO)

Para maximizar el rendimiento en consultas multi-tenant sin sobre-desnormalizar:

### A. Tablas con `id_organizacion` DIRECTO:
1. `ORGANIZACIONES` (PK)
2. `ORGANIZACION_CONFIGURACION` (PK/FK)
3. `MEMBRESIAS` (FK)
4. `PROPIEDADES` (FK)
5. `USUARIO_ASIGNACIONES` (FK opcional según scope)
6. `TRANSACCIONES_PASARELA` (FK directa para reportes de facturación general)
7. `AUDITORIA_LOG` (FK para trazabilidad corporativa)

### B. Tablas con Contexto HEREDADO (vía `id_propiedad` o `id_unidad`):
- `BLOQUES` $\rightarrow$ Hereda de `PROPIEDADES`.
- `UNIDADES` $\rightarrow$ Hereda de `PROPIEDADES`.
- `CONTRATOS`, `CUOTAS`, `PAGOS`, `MULTAS` $\rightarrow$ Heredan de `UNIDADES` y `PROPIEDADES`.
- `VISITAS`, `QR_ACCESOS`, `REGISTROS_ACCESO` $\rightarrow$ Heredan de `PROPIEDADES`.
- `PARQUEADEROS`, `COMUNICADOS`, `BUZON_PAQUETERIA` $\rightarrow$ Heredan de `PROPIEDADES`.

*Justificación:* Cada propiedad pertenece estrictamente a una sola organización. Inyectar `id_organizacion` en cada detalle de acceso o paquete violaría la 3FN sin aportar beneficios de indexación, ya que el filtrado de portería y administración se realiza por `id_propiedad`.

---

## 14. DISEÑO: IDENTIDAD Y AUTORIZACIÓN (RBAC + SCOPE)

```
+-----------------------------------------------------------------------------------+
| PERSONAS (id_persona, id_tipo_doc, num_doc, nombres, apellidos, email, telefono)   |
+-----------------------------------------------------------------------------------+
                                         │ 1:1
                                         ▼
+-----------------------------------------------------------------------------------+
| USUARIOS (id_usuario, id_persona, username, password_hash, activo, ultimo_login)  |
+-----------------------------------------------------------------------------------+
                                         │ 1:N
                                         ▼
+-----------------------------------------------------------------------------------+
| USUARIO_ASIGNACIONES                                                              |
|   - id_asignacion (PK)                                                            |
|   - id_usuario (FK -> USUARIOS)                                                   |
|   - id_rol (FK -> ROLES)                                                          |
|   - id_organizacion (FK -> ORGANIZACIONES, NULL si Global)                        |
|   - id_propiedad (FK -> PROPIEDADES, NULL si Org o Global)                        |
|   - id_unidad (FK -> UNIDADES, NULL si Propiedad/Org/Global)                      |
|   - activo (1/0)                                                                  |
+-----------------------------------------------------------------------------------+
```

---

## 15. DISEÑO: ORGANIZACIONES, MEMBRESÍAS Y PLANES

```
+----------------+        1:N        +-------------------+        N:1        +------------+
| ORGANIZACIONES | ────────────────> |    MEMBRESIAS     | <──────────────── |   PLANES   |
+----------------+                   +-------------------+                   +------------+
        │ 1:1                          - id_membresia (PK)                         │ 1:N
        ▼                              - id_organizacion (FK)                      ▼
+-------------------------+            - id_plan (FK)                        +--------------+
| ORGANIZACION_CONFIG     |            - fecha_inicio / fin                  | PLAN_MODULOS |
| - razon_social          |            - estado (ACTIVA/SUSP)                +--------------+
| - dias_gracia_pago      |            - max_propiedades_override                  │ N:1
| - logo_url              |            - max_unidades_override                     ▼
+-------------------------+                                                  +--------------+
                                                                             |   MODULOS    |
                                                                             +--------------+
```

---

## 16. DISEÑO: PROPIEDADES, BLOQUES Y UNIDADES

```
+-----------------------------------------------------------------------------------+
| PROPIEDADES (id_propiedad, id_organizacion, id_tipo_propiedad, nombre, direccion)  |
+-----------------------------------------------------------------------------------+
         │ 1:1                                  │ 1:N (Opcional)
         ▼                                      ▼
+--------------------------------+     +--------------------------------------------+
| PROPIEDAD_CONFIGURACION        |     | BLOQUES                                    |
| - hora_cierre_porteria         |     | - id_bloque (PK)                           |
| - minutos_validez_qr_default   |     | - id_propiedad (FK)                        |
| - cupo_parqueadero_visitantes  |     | - nombre ('Torre 1', 'Manzana B')          |
| - permite_registro_placa_qr    |     +--------------------------------------------+
+--------------------------------+                          │ 1:N (Opcional)
         │ 1:N                                              │
         └───────────────────────┬──────────────────────────┘
                                 ▼
+-----------------------------------------------------------------------------------+
| UNIDADES                                                                          |
|   - id_unidad (PK)                                                                |
|   - id_propiedad (FK -> PROPIEDADES)                                              |
|   - id_bloque (FK -> BLOQUES, NULLABLE)                                           |
|   - id_tipo_unidad (FK -> TIPOS_UNIDAD)                                           |
|   - identificador ('101', 'Casa 5', 'Local 2')                                    |
|   - piso, area_m2, coeficiente_copropiedad, estado, activo                        |
+-----------------------------------------------------------------------------------+
```

---

## 17. DISEÑO: TITULARIDAD, HABITABILIDAD Y CONTRATOS

```
                 +----------------------------------------+
                 |                PERSONAS                |
                 +----------------------------------------+
                     │ 1:N                            │ 1:N
                     │                                │
    +────────────────┼─────────────────+              │
    │                │                 │              │
    ▼                ▼                 ▼              ▼
+---------------+ +----------------+ +-------------+ +---------------------+
| PROPIETARIOS_ | | RESIDENTES_    | | CONTRATOS   | | TUTORES_RESIDENTE   |
| UNIDAD        | | UNIDAD         | | - id_unidad | | - id_menor (FK)     |
| - id_unidad   | | - id_unidad    | | - id_arrend | | - id_tutor (FK)     |
| - id_persona  | | - id_persona   | | - canon     | | - parentesco        |
| - pct_propied | | - id_contrato  | | - estado    | +---------------------+
+---------------+ | - es_titular   | +-------------+
                  +----------------+
```

---

## 18. DISEÑO: CUOTAS, PAGOS Y PASARELA (WOMPI)

```
+------------------+ 1:N  +---------------------------------------------------------+
| CONCEPTOS_COBRO  | ───> | CUOTAS                                                  |
+------------------+      | - id_cuota (PK), id_unidad (FK), id_concepto (FK)        |
                          | - periodo_facturado ('2026-08'), fecha_emision/vencim   |
                          | - monto_base, interes_mora, monto_total, saldo_pendiente |
                          | - estado ('PENDIENTE', 'PAGADA', 'EN_MORA')             |
                          +---------------------------------------------------------+
                                       │ 1:N                           ▲
                                       ▼                               │ 1:1 (Opcional)
                          +-------------------------+      +------------------------+
                          | PAGOS                   |      | MULTAS                 |
                          | - id_pago (PK)          |      | - id_multa (PK)        |
                          | - id_cuota (FK)         |      | - id_unidad (FK)       |
                          | - monto_pagado          |      | - motivo, evidencia    |
                          | - metodo_pago           |      | - id_cuota_generada    |
                          | - num_comprobante       |      +------------------------+
                          | - fecha_pago, registrado|
                          +-------------------------+
```

---

## 19. DISEÑO: VISITANTES, VISITAS, QR Y ACCESOS

```
+-----------------------------------------------------------------------------------+
| VISITANTES (id_visitante, id_propiedad, id_tipo_doc, num_doc, nombres, apellidos) |
+-----------------------------------------------------------------------------------+
         │ 1:N
         ▼
+-----------------------------------------------------------------------------------+
| VISITAS                                                                           |
|   - id_visita (PK)                                                                |
|   - id_propiedad (FK), id_unidad (FK)                                             |
|   - id_autoriza (FK -> PERSONAS), id_visitante_ppal (FK -> VISITANTES)            |
|   - fecha_programada, estado ('PROGRAMADA', 'EN_CURSO', 'FINALIZADA', 'EXPIRADA')|
+-----------------------------------------------------------------------------------+
         │ 1:N                                               │ 1:N
         ▼                                                   ▼
+------------------------------------+     +----------------------------------------+
| QR_ACCESOS                         |     | REGISTROS_ACCESO                       |
| - id_qr (PK)                       |     | - id_acceso (PK)                       |
| - id_visita (FK)                   |     | - id_propiedad (FK)                    |
| - codigo_hash (HMAC SHA-256)       |     | - id_visita (FK, Nullable)             |
| - fecha_expiracion, usado, fecha_u |     | - id_portero (FK -> USUARIOS)          |
+------------------------------------+     | - tipo ('ENTRADA'/'SALIDA'), fecha_reg |
                                           +----------------------------------------+
```

---

## 20. DISEÑO: PARQUEADEROS Y VEHÍCULOS

```
+-----------------------------------+        +--------------------------------------+
| PARQUEADEROS                      |        | VEHICULOS                            |
| - id_parqueadero (PK)             |        | - id_vehiculo (PK)                   |
| - id_propiedad (FK)               |        | - id_tipo_vehiculo (FK)              |
| - codigo_celda ('P-101', 'V-04')  |        | - placa, marca, modelo, color        |
| - tipo_uso ('PRIVADO', 'VISITA')  |        | - id_propietario (FK -> PERSONAS)    |
| - id_unidad_asignada (FK, Null)   |        +--------------------------------------+
+-----------------------------------+                  │ 1:N            │ 1:N
         ▲                                             ▼                ▼
         │ 1:1 (Asignación temporal)         +------------------+ +-----------------+
         └────────────────────────────────── | VEHICULOS_VISITA | | VEHICULOS_      |
                                             | - id_visita (FK) | | UNIDAD          |
                                             | - id_vehiculo(FK)| | - id_unidad(FK) |
                                             +------------------+ +-----------------+
```

---

## 21. DISEÑO: AUDITORIA_LOG (INMUTABLE / APPEND-ONLY)

```sql
-- Estructura Lógica de Auditoría Forense
CREATE TABLE AUDITORIA_LOG (
    id_log                  NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    id_organizacion         NUMBER REFERENCES ORGANIZACIONES(id_organizacion) ON DELETE SET NULL,
    id_propiedad            NUMBER REFERENCES PROPIEDADES(id_propiedad) ON DELETE SET NULL,
    id_usuario              NUMBER REFERENCES USUARIOS(id_usuario) ON DELETE SET NULL,
    ip_origen               VARCHAR2(45) NOT NULL,
    user_agent              VARCHAR2(250),
    modulo                  VARCHAR2(50) NOT NULL,
    accion                  VARCHAR2(50) NOT NULL, -- 'LOGIN', 'CREATE', 'UPDATE', 'DELETE', 'QR_SCAN', 'PAGO'
    entidad                 VARCHAR2(50) NOT NULL, -- 'CONTRATOS', 'UNIDADES', 'PAGOS', 'USUARIOS'
    id_registro_afectado    VARCHAR2(100) NOT NULL,
    resultado               VARCHAR2(20) NOT NULL CHECK (resultado IN ('EXITOSO', 'DENEGADO', 'FALLO')),
    motivo_fallo            VARCHAR2(250),
    datos_anteriores_json   CLOB,
    datos_nuevos_json       CLOB,
    fecha_evento            TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
```

---

## 22. REGLAS PARA EVITAR INCONSISTENCIAS MULTI-TENANT

1. **Validación Cruzada de Jerarquía:** No se permite asociar un `id_bloque` o `id_unidad` a una visita si el bloque/unidad no pertenece a la misma `id_propiedad`.
2. **Aislamiento en Consultas:** Ninguna consulta SQL en la capa DAO debe omitir la cláusula `WHERE id_propiedad = :ctx_propiedad` o `WHERE id_organizacion = :ctx_org`.
3. **Foreign Keys Compuestas en Casos Críticos:** En tablas operativas como `CUOTAS`, las FKs validarán la pertenencia directa a la propiedad correspondiente.

---

## 23. REGLAS PARA EVITAR ACCESO INTER-TENANT (*ZERO TRUST*)

1. **Token JWT sin Autoridad Absoluta:** El token solo provee la identidad autenticada (`id_usuario`).
2. **Resolución en Base de Datos en Cada Petición:** El backend consulta `USUARIO_ASIGNACIONES` para certificar que el usuario posee el permiso sobre la `id_propiedad` requerida en la URL/Payload.
3. **Prohibición de Parámetros Globales en Clientes:** El cliente web jamás enviará `id_organizacion` o `id_propiedad` como parámetro de confianza ciega; el backend verifica la relación de tenencia antes de ejecutar la acción.

---

## 24. ENTIDADES HISTÓRICAS VS. ELIMINABLES

### A. Entidades Inmutables / Históricas (PROHIBIDO DELETE FÍSICO):
- `AUDITORIA_LOG` (Append-Only)
- `REGISTROS_ACCESO` (Historial forense de portería)
- `PAGOS` y `CUOTAS` (Trazabilidad contable y tributaria)
- `CONTRATOS` (Historial legal de arrendamientos)
- `MEMBRESIAS` (Histórico de cobros de plataforma)
- `TRANSACCIONES_PASARELA` (Auditoría bancaria)

### B. Entidades Depurables tras Expiración:
- `QR_ACCESOS` (Tokens expirados con más de 90 días pueden archivarse/purgarse en batch).

---

## 25. ESTRATEGIA DE BORRADO LÓGICO (*SOFT DELETE*)

Se implementará la columna `activo NUMBER(1) DEFAULT 1 NOT NULL` en las siguientes entidades maestras:
- `ORGANIZACIONES` (`activo = 0` desactiva el acceso a todos sus miembros).
- `PROPIEDADES` (`activo = 0` inhabilita la operativa del inmueble).
- `BLOQUES` y `UNIDADES` (Preserva la integridad histórica de pagos y visitas pasadas).
- `PERSONAS` y `USUARIOS` (`activo = 0` bloquea el inicio de sesión).
- `CONCEPTOS_COBRO` (`activo = 0` no permite emitir nuevas cuotas con ese concepto).
- `PARQUEADEROS` (`activo = 0` inhabilita la celda por mantenimiento/obras).
