# F10-05 — Actas de Asamblea

## Overview

Module for creating, managing, and publishing assembly minutes (actas) within SAED 2.0. Actas follow a strict state machine and are governed by Oracle VPD (Virtual Private Database) row-level security.

## State Machine

```
BORRADOR → EN_REVISION_COMISION → APROBADA → PUBLICADA_OFICIAL
                              ↘
                          BORRADOR (rejection)
```

| Transition | Allowed Roles |
|---|---|
| Create (→ BORRADOR) | ADMIN_PROPIEDAD |
| BORRADOR → EN_REVISION_COMISION | ADMIN_PROPIEDAD |
| EN_REVISION_COMISION → APROBADA | ADMIN_PROPIEDAD |
| EN_REVISION_COMISION → BORRADOR | ADMIN_PROPIEDAD |
| APROBADA → PUBLICADA_OFICIAL | ADMIN_PROPIEDAD |
| Read PUBLICADA_OFICIAL | RESIDENTE |

## API Endpoints

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/api/v1/actas` | ADMIN_PROPIEDAD | Create draft acta |
| GET | `/api/v1/actas` | ADMIN_PROPIEDAD, RESIDENTE | List actas (filtered by role) |
| GET | `/api/v1/actas/{id}` | ADMIN_PROPIEDAD, RESIDENTE | Get acta by ID |
| PUT | `/api/v1/actas/{id}` | ADMIN_PROPIEDAD | Update BORRADOR content |
| PATCH | `/api/v1/actas/{id}/estado` | ADMIN_PROPIEDAD | Advance/revert state |
| POST | `/api/v1/actas/{id}/documento` | ADMIN_PROPIEDAD | Associate signed document |

## Architecture

```
ActaController
    └── ActaService (ActaServiceImpl)
            ├── ActaRepository (ActaRepositoryImpl)
            │       └── NamedParameterJdbcTemplate → Oracle ACTAS_ASAMBLEA
            ├── DocumentoRepository (for document association)
            └── AuditService
```

## Key Technical Decisions

### Oracle CLOB for `CONTENIDO_TEXTO`

The `CONTENIDO_TEXTO` column is defined as `CLOB` in Oracle (not `VARCHAR2`) to support large assembly text content.

**Implications for Spring JDBC:**

1. **`SqlLobValue` required** — Use `org.springframework.jdbc.core.support.SqlLobValue` to bind CLOB values via `NamedParameterJdbcTemplate`:
   ```java
   params.addValue("contenidoTexto",
       new SqlLobValue(text),
       java.sql.Types.CLOB);
   ```

2. **No `COALESCE` on CLOB** — Oracle does not support `COALESCE` with CLOB columns (`ORA-00932`). Use dynamic SQL instead:
   ```java
   if (request.getContenidoTexto() != null) {
       sql.append(", CONTENIDO_TEXTO = :contenidoTexto");
       params.addValue("contenidoTexto", new SqlLobValue(text), Types.CLOB);
   }
   ```

3. **Null CLOB binding** — Passing `null` without explicit `Types.CLOB` causes `ORA-17004` (Invalid column type: 268435455). Always declare the SQL type explicitly, even for null values.

### VPD Policy — `POL_RLS_PROP_ACTAS_ASAMBLEA`

The `ACTAS_ASAMBLEA` table is protected by a VPD grouped policy.

**Critical rules:**
- `update_check` **must be `FALSE`**. Setting it to `TRUE` causes `ORA-28115` on UPDATE because the VPD predicate filters by property and the post-update row may not satisfy it.
- `DBMS_RLS.ADD_GROUPED_POLICY` does **not** accept `statement_types`, `sec_relevant_cols`, or `sec_relevant_cols_opt` parameters (unlike `ADD_POLICY`). Passing them causes `ORA-28104`.
- Always `DROP_GROUPED_POLICY` before `ADD_GROUPED_POLICY` in initializers to prevent policy accumulation across test context restarts.

**Idempotent pattern:**
```sql
BEGIN
  BEGIN
    DBMS_RLS.DROP_GROUPED_POLICY(
      object_schema => 'SAED_BASELINE_TEST_01',
      object_name   => 'ACTAS_ASAMBLEA',
      policy_group  => 'SAED_POLICY_GROUP',
      policy_name   => 'POL_RLS_PROP_ACTAS_ASAMBLEA'
    );
  EXCEPTION WHEN OTHERS THEN NULL;
  END;
  DBMS_RLS.ADD_GROUPED_POLICY(
    object_schema  => 'SAED_BASELINE_TEST_01',
    object_name    => 'ACTAS_ASAMBLEA',
    policy_group   => 'SAED_POLICY_GROUP',
    policy_name    => 'POL_RLS_PROP_ACTAS_ASAMBLEA',
    policy_function => 'FN_FILTRO_PROPIEDAD_ACTAS',
    update_check   => FALSE,
    enable         => TRUE
  );
END;
```

**Verify policy state:**
```sql
SELECT POLICY_GROUP, CHK_OPTION, ENABLE, POLICY_TYPE
FROM DBA_POLICIES
WHERE OBJECT_NAME = 'ACTAS_ASAMBLEA'
  AND POLICY_NAME = 'POL_RLS_PROP_ACTAS_ASAMBLEA';
```

### Pessimistic Lock — `lockActaForUpdate`

Uses `SELECT ... FOR UPDATE NOWAIT` to prevent concurrent modifications:
- Returns `404` if the acta is not found or not accessible under VPD
- Returns `409` if another transaction holds the lock (`ORA-00054`)

### Multi-Tenant Isolation

All queries go through `SaedContextHolder` → `SaedDataSourceProxy` → `PKG_SAED_SESSION.SET_CONTEXT`, which sets Oracle's application context. VPD predicates filter rows by `ID_PROPIEDAD` and `ID_ORGANIZACION` from this context.

Residents (RESIDENTE) see only `PUBLICADA_OFICIAL` actas. Admins see all states within their property.

## Test Suite — `F10ActasIntegrationTest`

**38 tests / 38 pass.** Covers:

| Range | Coverage area |
|---|---|
| T01–T10 | Creation, state machine, role matrix |
| T11–T20 | IDOR protection, resident confinement, document association |
| T21–T24 | Governance: audit, VPD boundary, concurrent update lock |
| T25 | Update BORRADOR content (CLOB write) |
| T26–T32 | List/filter by role and state, cross-property isolation |
| T33–T38 | Concurrency (ExecutorService + CountDownLatch), edge cases |

**Test infrastructure:**
```java
@BeforeEach
void setUp() {
    jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); " +
                         "PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN'); END;");
    // ... token generation, mock assignments
}

@AfterEach
void tearDown() {
    jdbcTemplate.execute("BEGIN PKG_SAED_SESSION.CLEAR_CONTEXT; END;");
    SaedContextHolder.clearContext();
}
```

## Error Reference

| Oracle Error | Cause | Fix |
|---|---|---|
| `ORA-17004` (268435455) | `null` param without explicit SQL type | Add `java.sql.Types.*` as 3rd arg to `addValue()` |
| `ORA-00932` CLOB | `COALESCE` on CLOB column or `Types.VARCHAR` on CLOB | Use `SqlLobValue` + dynamic SQL |
| `ORA-28115` | VPD `update_check=TRUE` blocks UPDATE | Set `update_check => FALSE` in policy |
| `ORA-28104` | Invalid param in `ADD_GROUPED_POLICY` | Remove `statement_types`, `sec_relevant_cols` |
| `ORA-00054` | Row locked by another session | Caught → 409 Conflict |
