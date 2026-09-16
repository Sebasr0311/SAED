# SAED 2.0 — CONFIGURABLE ARCHITECTURE ANALYSIS

**Objetivo:** Análisis técnico de solo lectura del estado actual de la arquitectura configurable en SAED 2.0:
$$\text{ORGANIZACIÓN} \longrightarrow \text{PROPIEDAD} \longrightarrow \text{ESTRUCTURA / TIPO DE PROPIEDAD} \longrightarrow \text{UNIDADES} \longrightarrow \text{OPERACIÓN}$$

---

## 1. Repository State

* **Repositorio Oficial:** `https://github.com/Sebasr0311/SAED`
* **Rama Activa:** `Sebasr0311/angelfish`
* **Commit Base:** `d8c8eb895dbd315ed0367243711ea754c09580c0` (`fix(api): align frontend endpoints and render health check to saed 2.0 backend contracts`)
* **Integridad del Working Tree:** 100% limpio (`working tree clean`).
* **Modificaciones Realizadas:** Ninguna sobre código de producción, SQL ni frontend. Únicamente la generación de este reporte técnico.

---

## 2. Executive Summary

El análisis del repositorio revela una situación arquitectónica sumamente favorable y particular:
1. **La base de datos relacional (Oracle ATP) ya contiene el 100% de las tablas, relaciones, llaves foráneas y políticas RLS necesarias para la arquitectura configurable.** Existen las tablas `ORGANIZACIONES`, `PROPIEDADES`, `TIPOS_PROPIEDAD`, `BLOQUES` (árbol jerárquico recursivo con `ID_BLOQUE_PADRE`), `UNIDADES`, `TIPOS_UNIDAD` y `PROPIEDAD_CONFIGURACION`.
2. **Multi-tenancy Organización → Múltiples Propiedades:** Está **completamente implementado y funcional** en base de datos, backend y frontend.
3. **Tipo de Propiedad (`TIPOS_PROPIEDAD`):** Es actualmente **`SOLO DATO`**. El backend y frontend no aplican reglas diferentes según el tipo de propiedad. En el frontend ni siquiera existe un selector para elegirlo al crear una propiedad (se asigna por defecto el ID 1).
4. **Estructura Interna (`BLOQUES`):** Es una **`CAPACIDAD A NIVEL DE SCHEMA` sin capa de aplicación**. La tabla `BLOQUES` admite jerarquías completas (`TORRE`, `BLOQUE`, `ETAPA`, `MANZANA`, `PISO`, `SECTOR`), pero en el backend no existe ningún servicio ni controlador CRUD para gestionarlos (solo una consulta de catálogo en `CatalogoController.java`), y en el frontend solo se presenta un selector plano en `UnidadesPage.jsx`.
5. **Configuración de Propiedad (`PROPIEDAD_CONFIGURACION`):** Está **`PARCIALMENTE IMPLEMENTADA DE FORMA AD-HOC`**. La tabla existe con RLS, pero solo es consumida puntualmente por `ConvivienteQuotaServiceImpl.java` mediante SQL inline. No existe un servicio genérico de configuración ni endpoints para que el administrador la modifique.
6. **Independencia Operativa:** **Total.** Todos los módulos operativos (Residentes, Convivientes, Vehículos, Visitas, Paquetes, Pagos, Contratos) se vinculan directamente a `ID_UNIDAD`. Ninguno depende de una estructura fija de torres o manzanas. Completar la arquitectura configurable no causará regresiones en la operación existente.

---

## 3. Organization Model

* **Estado:** **`IMPLEMENTADO`**
* **Tabla Principal:** `ORGANIZACIONES` (`ID_ORGANIZACION`, `NOMBRE`, `IDENTIFICACION_FISCAL`, `EMAIL_CONTACTO`, `TELEFONO_CONTACTO`, `DIRECCION`, `CIUDAD`, `PAIS`, `ESTADO`).
* **Tabla Intermedia:** `ORGANIZACION_PROPIEDAD` (`ID_ORGANIZACION`, `ID_PROPIEDAD`, `TIPO_RELACION`, `FECHA_VINCULACION`, `ESTADO`). Adicionalmente, `PROPIEDADES` posee `ID_ORGANIZACION` como llave foránea directa para garantizar pertenencia de tenant.
* **Backend:**
  * Controlador: `OrganizationController.java` (`@RequestMapping("/api/v1/organizations")`).
  * Servicio: `OrganizationService.java`.
  * Repositorio: `OrganizationRepositoryImpl.java`.
  * DTOs: `OrganizationDTO.java`, `OrganizationRequestDTO.java`.
* **Frontend:**
  * Superadmin: `SuperAdminOrganizacionesPage.jsx` (`GET /organizations`, creación, edición, estado y borrado).
  * Perfil Org: `OrgOrganizacionPage.jsx` (`/org/profile`).
  * Onboarding público: `RegistroOrganizacionPage.jsx` (`/public/onboarding/registro`).
* **Relación con Propiedades:** 1 Organización posee $N$ Propiedades.

---

## 4. Property Model

* **Estado:** **`IMPLEMENTADO` (con soporte multi-propiedad real)**
* **Tabla Principal:** `PROPIEDADES` (`ID_PROPIEDAD`, `ID_ORGANIZACION`, `ID_TIPO_PROPIEDAD`, `NOMBRE`, `DIRECCION`, `CIUDAD`, `PAIS`, `TIPO_OCUPACION_PREDOMINANTE`, `ESTADO`).
* **Soporte Multi-Propiedad:**
  * **Relación:** 1 Organización $\longrightarrow$ $N$ Propiedades.
  * **Control de Cupos:** `PlanLimitService.validateAndLockPropertyLimit(orgId)` valida que la organización no exceda el límite contratado en su suscripción SaaS.
  * **Creación y Edición:** Endpoints `POST /api/v1/properties` y `PUT /api/v1/properties/{id}` con anti-spoofing para asegurar que administradores no creen propiedades fuera de su organización.
  * **Activación / Estado:** `PATCH /api/v1/properties/{id}/status`.
  * **Eliminación Segura:** Flujo de 4 fases con OTP por correo y doble confirmación textual en `PropertyDeletionService.java`.
  * **Selección de Propiedad (Tenant Switcher):** Endpoint `GET /api/v1/me/contexts` permite a usuarios con asignaciones en múltiples copropiedades alternar entre ellas mediante el header `X-Assignment-Id`, gestionado por `TenantContext.jsx`.
  * **Aislamiento Tenant:** Política Oracle RLS `FN_FILTRO_ORGANIZACION` sobre `PROPIEDADES`.

---

## 5. Property Types

* **Estado:** **`PARCIAL (SOLO DATO)`**
* **Tabla:** `TIPOS_PROPIEDAD` (`ID_TIPO_PROPIEDAD`, `CODIGO`, `NOMBRE`).
  * Constraint en DB: `CK_TIPOSPROP_CODIGO CHECK (codigo IN ('EDIFICIO','CONJUNTO_CERRADO'))`.
* **Catálogo API:** `CatalogoController.java:53` (`GET /api/v1/catalogos/tipos-propiedad`).
* **Semillas (Seeds):** En `database/seeds/demo/V5.99__demo_seeds.sql` solo se encuentra insertado `1, 'EDIFICIO', 'Edificio Residencial'`. El código `CONJUNTO_CERRADO` no está sembrado en el dataset demo.
* **Comportamiento en Backend:** Cero ramificación de lógica. No existen condicionales (`if/switch`) en ningún servicio o controlador que alteren reglas de negocio según `tipoPropiedad`.
* **Comportamiento en Frontend:** Es puramente una etiqueta descriptiva (`prop.tipoPropiedadNombre || 'Edificio Residencial'`). En `OrgPropiedadesPage.jsx`, el modal de creación de propiedad hardcodea `idTipoPropiedad: 1` y no expone selector para el usuario.
* **Veredicto:** **SOLO DATO**. No constituye una configuración funcional en la actualidad.

---

## 6. Unit Model

* **Estado:** **`IMPLEMENTADO`**
* **Tabla:** `UNIDADES` (`ID_UNIDAD`, `ID_PROPIEDAD`, `ID_BLOQUE`, `ID_TIPO_UNIDAD`, `IDENTIFICADOR`, `AREA_M2`, `COEFICIENTE_COPROPIEDAD`, `CARACTERISTICAS`, `ESTADO`).
  * Constraints: `CK_UNIDADES_AREA` ($>0$), `CK_UNIDADES_COEFICIENTE` ($\ge 0$), `CK_UNIDADES_CARACT_JSON` (`caracteristicas IS JSON`), `CK_UNIDADES_ESTADO` (`ACTIVA`, `INACTIVA`, `EN_CONSTRUCCION`).
* **Catálogo de Tipos de Unidad:** `TIPOS_UNIDAD` (`ID_TIPO_UNIDAD`, `CODIGO`, `NOMBRE`).
  * Constraint: `CK_TIPOSUNIDAD_CODIGO CHECK (codigo IN ('APARTAMENTO','CASA','LOCAL','PARQUEADERO','DEPOSITO','OFICINA','PORTERIA'))`.
* **Backend:**
  * Controlador: `UnitController.java` (`@RequestMapping("/api/v1/units")`).
  * Servicio: `UnitService.java` (valida límites de plan SaaS con `planLimitService.validateAndLockUnitLimit`).
  * Repositorio: `UnitRepositoryImpl.java`.
  * DTOs: `UnitDTO.java`, `UnitRequestDTO.java`.
* **Detalle Técnico Identificado:** En `UnitRepositoryImpl.update(id, request)`, la sentencia SQL solo actualiza `identificador`, `area_m2` y `coeficiente_copropiedad`; omite la actualización de `id_bloque` y `id_tipo_unidad`.

---

## 7. Property Hierarchy

* **Estado:** **`PARCIAL (DATABASE LISTA / APPLICATION INCOMPLETA)`**
* **¿Existe modelo genérico?** **SÍ, en Base de Datos (Caso B / C).**
  * Tabla `BLOQUES`:
    * `ID_BLOQUE` (PK)
    * `ID_PROPIEDAD` (FK $\rightarrow$ `PROPIEDADES`)
    * `ID_BLOQUE_PADRE` (FK $\rightarrow$ `BLOQUES`, autorreferencia para jerarquías $N$-niveles)
    * `TIPO`: `CHECK (tipo IN ('TORRE','BLOQUE','ETAPA','MANZANA','PISO','SECTOR'))`
    * `CODIGO` VARCHAR2(30)
    * `NOMBRE` VARCHAR2(100)
    * `ORDEN` NUMBER(5,0)
    * `ESTADO` VARCHAR2(10)
    * Constraint: `CK_BLOQUES_NO_AUTOPADRE CHECK (id_bloque_padre IS NULL OR id_bloque_padre != id_bloque)`
    * RLS Policy: `POL_RLS_PROP_BLOQUES` mediante `FN_FILTRO_PROPIEDAD` activa y blindada.
* **Capacidades que el modelo DB ya permite:**
  * **Edificio:** Bloque Padre (`TORRE`) $\longrightarrow$ Bloque Hijo (`PISO`) $\longrightarrow$ Unidad (`APARTAMENTO`).
  * **Conjunto:** Bloque Padre (`ETAPA`) $\longrightarrow$ Bloque Hijo (`MANZANA`) $\longrightarrow$ Unidad (`CASA`).
  * **Simple:** Bloque (`TORRE` o `BLOQUE`) $\longrightarrow$ Unidad.
* **Gaps en la Capa de Aplicación:**
  * **Backend:** No existe `BloqueController`, `BloqueService`, ni `BloqueRepository`. Solo existe una consulta de lectura en `CatalogoController.java:37` (`GET /api/v1/catalogos/bloques`).
  * **Frontend:** No existe ninguna pantalla ni modal para crear, editar o estructurar bloques. `UnidadesPage.jsx` solo renderiza un `<Select>` plano de bloques preexistentes.

---

## 8. Property Configuration

* **Estado:** **`PARCIAL / AD-HOC`**
* **Tabla:** `PROPIEDAD_CONFIGURACION`:
  * Columnas: `ID_PROP_CONFIG`, `ID_PROPIEDAD`, `CLAVE`, `VALOR`, `DESCRIPCION`, `FECHA_ACTUALIZACION`.
  * Constraints: `PK_PROP_CONFIGURACION`, `UQ_PROPCONFIG_CLAVE UNIQUE (ID_PROPIEDAD, CLAVE)`, `FK_PROPCONFIG_PROP`.
  * RLS: `POL_RLS_PROP_PROPIEDAD_CONFI` bajo `FN_FILTRO_PROPIEDAD`.
* **Uso Actual:**
  * Únicamente `ConvivienteQuotaServiceImpl.java` consulta la clave `LIMITE_CONVIVIENTES_POR_UNIDAD` mediante un `SELECT` inline directo con fallback a 4.
* **Respuesta a la Pregunta Clave:**
  > ¿SAED ya tiene un mecanismo general de configuración por propiedad o cada configuración está implementada de forma aislada?
  * **Respuesta:** Está implementada de forma **aislada y ad-hoc**. No existe un `PropertyConfigService` centralizado, ni un repositorio de parámetros, ni un controlador REST (`/api/v1/properties/{id}/settings`), ni una interfaz web para que el administrador configure su copropiedad.

---

## 9. Operational Dependencies

Se auditó si los submódulos de la plataforma dependen de una jerarquía estructural fija o si ya operan de forma abstracta sobre `ID_UNIDAD`:

| Módulo Operativo | Tabla / Entidad | Dependencia Estructural | Veredicto |
|---|---|---|:---:|
| **Residentes** | `RESIDENTES_UNIDAD` | `ID_UNIDAD`, `ID_PERSONA` | Abstracta sobre Unidad |
| **Convivientes** | `RESIDENTES_UNIDAD` | `ID_UNIDAD`, `ID_PERSONA`, `TIPO_RELACION` | Abstracta sobre Unidad |
| **Vehículos** | `VEHICULOS` | `ID_UNIDAD`, `PLACA` | Abstracta sobre Unidad |
| **Parqueaderos** | `PARQUEADEROS` | `ID_PROPIEDAD`, `ID_UNIDAD` | Abstracta sobre Unidad |
| **Visitas / QR** | `VISITAS`, `QR_ACCESOS` | `ID_UNIDAD`, `ID_VISITANTE` | Abstracta sobre Unidad |
| **Paquetería** | `PAQUETES` | `ID_PROPIEDAD`, `ID_UNIDAD` | Abstracta sobre Unidad |
| **Cuotas / Pagos**| `CUOTAS`, `PAGOS` | `ID_UNIDAD` | Abstracta sobre Unidad |
| **Contratos** | `CONTRATOS` | `ID_UNIDAD`, `ID_PERSONA` | Abstracta sobre Unidad |

**Hallazgo Crucial:** El 100% de la operación transaccional se apoya en `ID_UNIDAD`. La jerarquía de bloques (`BLOQUES`) es una capa de catalogación, direccionamiento y agrupación visual. **Modificar y flexibilizar la estructura de bloques no altera ni pone en riesgo ninguna regla de negocio operativa.**

---

## 10. Frontend State

1. **`OrgPropiedadesPage.jsx`:**
   * Lista copropiedades de la organización (`GET /properties`).
   * Modal de creación presente, pero omite el campo `idTipoPropiedad` (envía `1` implícito).
   * No permite configurar si la propiedad es Edificio o Conjunto Cerrado.
2. **`PropiedadesPage.jsx`:**
   * Panel de administración de propiedad. Permite editar datos básicos (nombre, dirección, ocupación). No gestiona estructura interna ni configuraciones.
3. **`UnidadesPage.jsx`:**
   * Muestra listado de unidades con identificador, tipo, bloque, área y estado.
   * Modal para crear/editar unidad con dropdown plano de tipos (`/tipos-unidad`) y bloques (`/bloques`).
   * No ofrece navegación en árbol ni creación de bloques o pisos.
4. **Módulos Faltantes en UI:**
   * Pantalla/modal de gestión de Estructura de Copropiedad (creación de Torres, Etapas, Pisos, Manzanas).
   * Pantalla/modal de Configuración de Propiedad (parámetros operativos, cupos, feature flags).

---

## 11. Database State

* **Evaluación de Estructuras Existentes:**
  * `ORGANIZACIONES`: Completa.
  * `ORGANIZACION_PROPIEDAD`: Completa.
  * `PROPIEDADES`: Completa.
  * `TIPOS_PROPIEDAD`: Completa (requiere semilla de `CONJUNTO_CERRADO`).
  * `BLOQUES`: Completa (soporta jerarquía recursiva $N$-niveles).
  * `UNIDADES`: Completa (vinculada a `ID_BLOQUE` y `ID_TIPO_UNIDAD`).
  * `TIPOS_UNIDAD`: Completa (admite `APARTAMENTO`, `CASA`, `LOCAL`, etc.).
  * `PROPIEDAD_CONFIGURACION`: Completa (clave-valor con constraint de unicidad).
* **¿Se requieren tablas nuevas?** **NO.** La base de datos ya está completamente normalizada y diseñada para este requerimiento.

---

## 12. Multi-Tenancy

* **Esquema de Seguridad:** Oracle Virtual Private Database (VPD / RLS) acceda a través del paquete `PKG_SAED_SESSION` y contexto `SAED_CTX`.
* **Variables de Contexto:** `ID_ORGANIZACION`, `ID_PROPIEDAD`, `ID_USUARIO`, `ROL_CODIGO`.
* **Políticas Activas en Tablas del Bloque:**
  * `PROPIEDADES` $\longrightarrow$ `POL_RLS_ORG_PROPIEDADES` (`FN_FILTRO_ORGANIZACION`).
  * `BLOQUES` $\longrightarrow$ `POL_RLS_PROP_BLOQUES` (`FN_FILTRO_PROPIEDAD`).
  * `PROPIEDAD_CONFIGURACION` $\longrightarrow$ `POL_RLS_PROP_PROPIEDAD_CONFI` (`FN_FILTRO_PROPIEDAD`).
  * `UNIDADES` $\longrightarrow$ `POL_RLS_UNI_UNIDADES` (`FN_FILTRO_UNIDAD`).
* **Veredicto:** El aislamiento multi-inquilino está completamente activo y protegido a nivel del motor relacional.

---

## 13. SAED 2.0 Requirements Matrix

| Requisito SAED 2.0 | Estado Actual | Evidencia en Código / DB | GAP Detectado |
|---|:---:|---|---|
| **Organización → múltiples propiedades** | **IMPLEMENTADO** | `PROPIEDADES.ID_ORGANIZACION`, `PlanLimitService`, `PropertyController` | Ninguno |
| **Tipo: Edificio de apartamentos** | **PARCIAL** | `TIPOS_PROPIEDAD` (`CODIGO = 'EDIFICIO'`), semilla en `V5.99` | Solo dato; no genera estructura por defecto |
| **Tipo: Conjunto cerrado** | **PARCIAL** | Constraint `CK_TIPOSPROP_CODIGO` lo admite; no sembrado en demo | Falta registro en catálogo y selección en UI |
| **Modelo de Unidades** | **IMPLEMENTADO** | `UNIDADES`, `UnitController`, `UnitRepositoryImpl`, `UnidadesPage` | `update` omite `id_bloque`/`id_tipo_unidad` |
| **Jerarquía configurable** | **PARCIAL** | Tabla `BLOQUES` admite árbol recursivo (`ID_BLOQUE_PADRE`) | Falta CRUD en backend y componente visual en UI |
| **Configuración por propiedad** | **PARCIAL** | Tabla `PROPIEDAD_CONFIGURACION` activa con RLS | Acceso ad-hoc; falta servicio genérico y UI |
| **Aislamiento por propiedad** | **IMPLEMENTADO** | Políticas RLS `FN_FILTRO_PROPIEDAD`, `X-Assignment-Id`, `SAED_CTX` | Ninguno |

---

## 14. Confirmed Gaps

1. **GAP-CFG-01 (Catálogos & Seeds):** La tabla `TIPOS_PROPIEDAD` no tiene sembrado `CONJUNTO_CERRADO` en el seeder de arranque demo, y `TIPOS_UNIDAD` carece de semillas para tipos no residenciales (`CASA`, `LOCAL`).
2. **GAP-CFG-02 (Tipo de Propiedad Funcional):** `tipoPropiedad` no está expuesto en el formulario de creación de `OrgPropiedadesPage.jsx` ni condiciona la plantilla inicial de bloques.
3. **GAP-CFG-03 (Backend Jerarquía Bloques):** Ausencia de `BlockController`, `BlockService` y `BlockRepository` para crear, actualizar, ordenar y eliminar nodos de la jerarquía (`Torre`, `Etapa`, `Piso`, `Manzana`).
4. **GAP-CFG-04 (Actualización de Unidad):** `UnitRepositoryImpl.update` no actualiza `id_bloque` ni `id_tipo_unidad` al editar una unidad existente.
5. **GAP-CFG-05 (Backend Configuración Propiedad):** Ausencia de servicio genérico `PropertyConfigService` y controlador `PropertyConfigController` (`GET/PUT /api/v1/properties/{id}/config`) para consultar y modificar parámetros dinámicos clave-valor.
6. **GAP-CFG-06 (Frontend Estructura & Bloques):** No existe un componente o pestaña en la UI para visualizar el árbol estructural de la propiedad ni para registrar bloques/torres/manzanas.
7. **GAP-CFG-07 (Frontend Configuración Propiedad):** No existe interfaz gráfica para que `ADMIN_PROPIEDAD` configure parámetros como `LIMITE_CONVIVIENTES_POR_UNIDAD`, días de tolerancia de mora o flags operativas.

---

## 15. Recommended Architecture

### ¿Qué debemos reutilizar?
* Las tablas Oracle existentes: `PROPIEDADES`, `TIPOS_PROPIEDAD`, `BLOQUES`, `UNIDADES`, `TIPOS_UNIDAD` y `PROPIEDAD_CONFIGURACION`.
* El esquema de seguridad RLS existente (`FN_FILTRO_PROPIEDAD`).
* Los controladores `PropertyController` y `UnitController` existentes.

### ¿Qué debemos extender?
* `PropertyRequestDTO`: permitir recibir `idTipoPropiedad` desde la UI.
* `UnitRepositoryImpl.update`: agregar `id_bloque = :idBloque, id_tipo_unidad = :idTipoUnidad` a la sentencia `UPDATE`.
* `CatalogoController.java`: mantener endpoints de catálogo para lectura rápida.

### ¿Qué debemos crear?
1. **Backend - Módulo de Estructura de Bloques:**
   * DTOs: `BlockDTO`, `BlockRequestDTO`, `BlockTreeDTO`.
   * Repositorio: `BlockRepository` / `BlockRepositoryImpl`.
   * Servicio: `BlockService`.
   * Controlador: `BlockController` (`/api/v1/properties/{propertyId}/blocks`).
2. **Backend - Módulo de Configuración de Propiedad:**
   * DTOs: `PropertyConfigDTO`, `PropertyConfigUpdateDTO`.
   * Repositorio: `PropertyConfigRepository` / `PropertyConfigRepositoryImpl`.
   * Servicio: `PropertyConfigService` (reutilizable por `ConvivienteQuotaService` y futuros módulos).
   * Controlador: `PropertyConfigController` (`/api/v1/properties/{propertyId}/config`).
3. **Frontend:**
   * Selector de Tipo de Propiedad en `OrgPropiedadesPage.jsx`.
   * Vista/Pestaña de "Estructura y Bloques" en `UnidadesPage.jsx` o `PropiedadesPage.jsx` (gestor de Torres/Manzanas/Pisos).
   * Pestaña o modal de "Configuración de Propiedad" para `ADMIN_PROPIEDAD`.

### ¿Qué debemos evitar?
* **Cero cambios al motor RLS o a `PKG_SAED_SESSION`:** El aislamiento ya está garantizado.
* **Cero modificaciones a tablas relacionales:** No alterar DDL ni agregar columnas innecesarias.
* **No crear jerarquías rígidas:** Aprovechar la autorreferencia `id_bloque_padre` para soportar tanto edificios (Torre $\rightarrow$ Piso) como conjuntos (Etapa $\rightarrow$ Manzana).

---

## 16. Migration Strategy

* **Impacto en Datos Existentes:** **NULO (100% compatible hacia atrás).**
* **Preservación de Unidades Actuales:**
  * En la tabla `UNIDADES`, la columna `ID_BLOQUE` es `NULLABLE`. Las unidades existentes sin bloque asignado continúan funcionando de forma idéntica.
  * Para copropiedades tipo `EDIFICIO`, las unidades existentes pueden asociarse de forma transparente a una `Torre 1` o `Bloque Principal` mediante un update simple o desde la UI.
* **Transición Gradual:**
  $$\text{Unidades Existentes (Bloque Opcional)} \longrightarrow \text{Adición de Bloques Estructurados} \longrightarrow \text{Asociación Dinámica}$$

---

## 17. Implementation Scope

* **DENTRO DEL ALCANCE:**
  * Selección de `Tipo de Propiedad` al registrar/editar copropiedad.
  * Gestión de Estructura de Bloques (Torres, Etapas, Manzanas, Pisos).
  * Asignación jerárquica de unidades a bloques.
  * Gestión centralizada de parámetros clave-valor en `PROPIEDAD_CONFIGURACION`.
* **FUERA DEL ALCANCE (ESTRICTAMENTE EXCLUIDO):**
  * LPR (Reconocimiento de placas).
  * SMS / Pasarelas externas de mensajería.
  * Motores de reportes avanzados.
  * Mantenimiento preventivo / órdenes de trabajo.
  * Asambleas y quórum.
  * Integración con Wompi / pasarela de pagos.
  * Módulos de PQRS / tickets.

---

## 18. Recommended Implementation Order

1. **Paso 1 — Semillas y Correcciones Menores de Catálogo:**
   * Asegurar inserción de `CONJUNTO_CERRADO` en `TIPOS_PROPIEDAD` y catálogo de `TIPOS_UNIDAD`.
   * Completar campos `id_bloque` e `id_tipo_unidad` en `UnitRepositoryImpl.update`.
2. **Paso 2 — Backend de Estructura (Bloques):**
   * Implementar `BlockDTO`, `BlockRepository`, `BlockService` y `BlockController` (`CRUD` con validación de propiedad y jerarquía padre-hijo).
3. **Paso 3 — Backend de Configuración de Propiedad:**
   * Implementar `PropertyConfigRepository`, `PropertyConfigService` y `PropertyConfigController` (`GET`, `PUT` masivo o individual de configuraciones por propiedad).
4. **Paso 4 — Frontend: Selección de Tipo y Estructura:**
   * Agregar selector de tipo de propiedad en `OrgPropiedadesPage.jsx`.
   * Incorporar gestor visual de bloques (Torres/Etapas/Manzanas) y selector jerárquico en `UnidadesPage.jsx`.
5. **Paso 5 — Frontend: Panel de Configuración y Pruebas E2E:**
   * Agregar vista de configuración de propiedad para `ADMIN_PROPIEDAD`.
   * Ejecutar suites de integración (`BlockSecurityIntegrationTest`, `PropertyConfigSecurityIntegrationTest`) y verificar no-regresión.

---

## 19. Risks

1. **Recursión o Ciclos en Jerarquía de Bloques:** Mitigado por constraint en base de datos (`CK_BLOQUES_NO_AUTOPADRE`) y validación en servicio que restringe la profundidad a máximo 3 niveles (ej. Etapa $\rightarrow$ Manzana $\rightarrow$ Bloque).
2. **Permisos y Scopes de Edición:** Asegurar que `ADMIN_PROPIEDAD` pueda administrar los bloques de su propiedad, y `ADMIN_ORGANIZACION` pueda hacerlo sobre cualquiera de sus propiedades, respetando la autoridad `SCOPE_ADMIN_PROPIEDAD` / `SCOPE_ADMIN_ORGANIZACION`.

---

## 20. Conclusion

* **Clasificación de Complejidad:** **`MEDIO`**.
* **Justificación:** La complejidad es moderada debido a que el reto mayor en sistemas SaaS (el modelado de datos relacional y el aislamiento de seguridad RLS en base de datos) ya está **completamente construido y probado** en Oracle ATP. El trabajo consiste exclusivamente en implementar la capa REST de servicios/controladores estándar en Spring Boot y las vistas correspondientes en React, sin necesidad de alteraciones estructurales en la base de datos ni en el núcleo de seguridad.
