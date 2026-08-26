# PHASE 1C PRE-MERGE AUDIT: V4.2 RLS PATCH VERIFICATION

## Objetivo
Evaluar el impacto arquitectónico y de seguridad de la migración `V4.2__core_rls_patch.sql` introducida en la Fase 1C para resolver incompatibilidades físicas del esquema RLS legado en operaciones de `SUPERADMIN`. 

## 1. Integridad de los Archivos Base
**Veredicto:** APROBADO
- **V3.9, V4.0, V4.1:** Los archivos SQL originales de estas migraciones permanecen **intactos** (no fueron modificados en Git ni en su estructura original).
- **Objetos Modificados por V4.2:** El parche `V4.2__core_rls_patch.sql` afecta única y exclusivamente a un (1) objeto en la base de datos: `PACKAGE BODY SAED_V39_FINAL_TEST.PKG_SAED_SECURITY_RLS`.
- **Estructura Conservada:** No se añadieron, eliminaron ni modificaron tablas, triggers o políticas de grupo (`DBMS_RLS.ADD_GROUPED_POLICY`). El modelo relacional es idéntico a V3.9.

## 2. Análisis del Bypass 1=1 para SUPERADMIN
**Veredicto:** APROBADO

La instrucción `IF v_rol = 'SUPERADMIN' THEN RETURN '1=1'; END IF;` fue introducida para reemplazar a `RETURN NULL;`, solucionando así la restricción `ORA-28115` en DML dinámicos.

- **¿Permite saltarse el alcance para otros roles?**
  No. La evaluación de `v_rol` (`SYS_CONTEXT('SAED_CTX', 'ROL_CODIGO')`) es excluyente y precede a la lógica de negocio regular. Si `v_rol` es cualquier otro valor (`ADMIN_ORGANIZACION`, `RESIDENTE`, etc.), la instrucción es ignorada y se aplican las restricciones estrictas del baseline V3.9 (e.g. `RETURN 'id_organizacion = ' || v_org;`).
  
- **¿Puede un usuario normal manipular el contexto para obtener este comportamiento?**
  No. El contexto `SAED_CTX` es un _Application Context_ confiado en Oracle que está asociado de forma exclusiva al paquete `PKG_SAED_SESSION`. Solamente el código interno de `PKG_SAED_SESSION` puede modificar las variables de `SAED_CTX` utilizando `DBMS_SESSION.SET_CONTEXT`. Es imposible que un usuario inyecte comandos SQL maliciosos o llame a `DBMS_SESSION` directamente sin provocar una denegación (ORA-01031).

- **¿Verifica Oracle la identidad del SUPERADMIN?**
  Sí. `PKG_SAED_SESSION.SET_CONTEXT` requiere validar la base de datos antes de inyectar el código de rol `SUPERADMIN`:
  ```sql
  SELECT COUNT(*) INTO v_valido FROM ADMINISTRADORES_SAED WHERE id_usuario = p_id_usuario AND estado = 'ACTIVO';
  ```
  La modificación que hicimos en V4.2 para el estado `BOOTSTRAP` protege esta consulta para que un usuario en autenticación *solo* pueda leer si su propio ID existe en la tabla de administradores:
  ```sql
  IF v_state = 'BOOTSTRAP' THEN RETURN 'id_usuario = ' || v_usr; END IF;
  ```
  Esto bloquea cualquier fuga de datos en la etapa de bootstrap y asegura validación estricta de identidad.

## 3. Vectores de Ataque en Capa de Aplicación
**Veredicto:** APROBADO

- **X-Assignment-Id Falso:** 
  Para usuarios normales, el backend verifica rigurosamente la propiedad de la asignación usando el ID extraído del Token JWT in-falsificable (`AssignmentRepository.findByIdAndUsuarioId`). 
  Para `SUPERADMIN`, la aplicación ni siquiera consume el `X-Assignment-Id` para derivar autoridad. La autoridad del superadministrador se deduce de la tabla `ADMINISTRADORES_SAED` al momento de la validación inicial y se inyecta por el `SaedDataSourceProxy`.

- **Aislamiento de Múltiples Inquilinos:**
  Para los roles con alcances inferiores (`ORGANIZACION`, `PROPIEDAD`, `UNIDAD`), la migración V4.2 preserva intactas las reglas físicas originales de aislamiento RLS de V3.9. No hubo relajación de reglas de acceso sobre las tablas `ORGANIZACIONES`, `PROPIEDADES` o `UNIDADES`.

## 4. Auditoría de Context Bleed y Privilegios
**Veredicto:** APROBADO

- **Context Bleed:** El estado de las variables de Oracle fue puesto a prueba bajo condiciones multi-hilo en `ContextBleedIntegrationTest` (que falló controladamente cuando no había aislamiento, y pasó tras implementar la limpieza forzada). Ninguna conexión retorna al pool de Spring/HikariCP preservando la condición de `SUPERADMIN`. Todo ciclo inicia en `CLEARING`.
- **Privilegio EXEMPT ACCESS POLICY:**
  Auditoría realizada (26-Ago-2026):
  ```sql
  GRANTEE  PRIVILEGE                                ADM COM INH
  -------- ---------------------------------------- --- --- ---
  SAED_APP CREATE SESSION                           NO  NO  NO
  ```
  `SAED_APP` sigue ejecutándose como usuario restringido; todo RLS está en plena vigencia.

## VEREDICTO FINAL

**PHASE 1C PRE-MERGE — APPROVED**

La corrección implementada en `V4.2__core_rls_patch.sql` resuelve problemas estructurales legítimos (bugs inherentes del framework V3.9) operando bajo las garantías y defensas más altas disponibles de Oracle. El parche de infraestructura es local, auditado, determinista y seguro. Autorizado para Merge a `main`.
