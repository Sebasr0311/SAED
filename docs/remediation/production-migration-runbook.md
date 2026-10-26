# Production migration runbook — F0–F11 remediation (V5.31 → V5.90)

Audience: the database owner who applies the remediation to the production Oracle database. Nobody ran anything
against production while preparing this; every step below was rehearsed only on disposable isolated installs
(final evidence: `oracle-isolation-progress.md`, recovery-29).

## 0. Before you start

1. **Take a backup** you can restore (logical export per `g03-backup-restore.md`, plus the managed-database backup of
   the service). Write down the time; it is your rollback point.
2. **Rehearse on a copy first.** Restore that backup into a staging database, apply this runbook there, and run the
   verification in section 4 against staging. Only then repeat on production. Production has history the isolated
   fixture does not (real tenants, legacy rows), and the back-fills in V5.59, V5.63 and V5.69 touch existing data.
3. **Know your current level.** There is no migration-history table, so check which remediation migrations are
   already present (section 3 lists a marker per migration). All files from V5.31 on are written to be re-runnable
   (data-dictionary guards, `CREATE OR REPLACE`, guarded `UPDATE`s, no `MERGE` on policy tables), but apply only the
   missing ones when you can tell.
4. **Maintenance window.** V5.54, V5.58 and V5.64 replace the whole `PKG_SAED_SECURITY_RLS` body. Stop the backend
   during the window so no request runs with a half-replaced security package.
5. Have the passwords of the **schema owner** and of **`SAED_SEC_MASTER`** at hand. Do not run anything as the
   application's runtime account.

## 1. Who runs what

| Files | Connect as | Session setup |
|---|---|---|
| Every file **without** `__sec_` in its name | the application **schema owner** | `ALTER SESSION SET CURRENT_SCHEMA=<owner>;` |
| `V5.34__sec_…`, `V5.37__sec_…`, `V5.55__sec_…`, `V5.66__sec_…`, `V5.67__sec_…`, `V5.68__sec_…` | **`SAED_SEC_MASTER`** | none |

Each file starts with its own bootstrap context block; run it whole (SQL*Plus `@file`, with `WHENEVER SQLERROR EXIT
FAILURE ROLLBACK` so a failure stops the run). Grants to the application principal are resolved inside the files
from the grantees of `PKG_AUTH_BOOTSTRAP`, so they do not depend on the runtime account's name.

## 2. Order

Strict ascending order. Gaps in the numbering are intentional.

```
V5.31 V5.32 V5.33 V5.34(sec) V5.35 V5.36 V5.37(sec)
V5.40 V5.41 V5.42 V5.43
V5.50 V5.51 V5.52 V5.53 V5.54 V5.55(sec) V5.56 V5.57 V5.58 V5.59
V5.60 V5.61 V5.62 V5.63 V5.64 V5.65 V5.66(sec) V5.67(sec) V5.68(sec) V5.69
V5.70 V5.71 V5.80 V5.90
```

Dependencies that must hold: V5.36 before V5.37; V5.65 before V5.66–V5.68; V5.63 before V5.64 and V5.67; V5.54 before
V5.58 before V5.64 (each replaces the previous package body); V5.59 after V5.58.

## 3. Markers (is a migration already applied?)

| Migration | Marker |
|---|---|
| V5.31 | `REPORTES_CONFIGURADOS` has `ID_ORGANIZACION` |
| V5.32 | table `ONBOARDING_INTENCIONES` exists |
| V5.41 / V5.42 | tables `PLANES_MANTENIMIENTO` / `AVISOS_RENOVACION_CONTRATO` exist |
| V5.51 | table `CONFIGURACION_COBRO_PROPIEDAD` exists |
| V5.56 | table `DESAFIOS_ELIM_PROPIEDAD` exists |
| V5.57 | `VOTACIONES` has `RESULTADO` and `PESO_REPRESENTADO` |
| V5.59 | constraint `CK_ASIG_UNIDAD_PROPIEDAD` exists |
| V5.60 / V5.62 | tables `INTERCOM_SOLICITUDES` / `COMUNICADOS_LECTURAS` exist |
| V5.63 | `VISITANTES` has `ID_PROPIEDAD`; `UIX_CONTRATOS_ACTIVO_UNIDAD` has one indexed expression |
| V5.66 / V5.67 / V5.68 | `SAED_SEC_MASTER` owns `SP_REGISTRAR_CONVIVIENTE` / `SP_RESOLVER_VISITANTE` / `SP_REGISTRAR_CUENTA` |
| V5.90 | `MODULOS` has `CODIGO = 'INCIDENTES'` |

V5.57 and V5.63–V5.66 were revised in place before ever reaching a shared database. If production somehow shows an
older shape of one of them, stop and compare with the file instead of re-running it blindly.

## 4. After applying — verification

1. **No invalid objects** in the owner schema or in `SAED_SEC_MASTER` (`DBA_OBJECTS.STATUS <> 'VALID'` returns 0).
2. **Runtime grants on the new tables.** The application account needs the same privileges it has on comparable
   tables for: `ONBOARDING_INTENCIONES`, `PLANES_MANTENIMIENTO`, `AVISOS_RENOVACION_CONTRATO`,
   `CONFIGURACION_COBRO_PROPIEDAD`, `DESAFIOS_ELIM_PROPIEDAD`, `INTERCOM_SOLICITUDES`, `COMUNICADOS_LECTURAS`.
   Check with `DBA_TAB_PRIVS` and grant what is missing (least privilege: read-only where the application only reads).
3. **Policies enabled**: `DBA_POLICIES` for the owner, `ENABLE = 'YES'`, on every table of
   `scripts/verification/rls_predicate_sweep.py`'s list, including `DESAFIOS_ELIM_PROPIEDAD`.
4. **Back-fill results**: count `PERSONAS` with `ID_ORGANIZACION IS NULL` (expected: only persons with no link, or
   linked to several organizations — review those by hand); count `USUARIO_ASIGNACIONES` with a unit and no property
   (expected 0, enforced by the V5.59 constraint).
5. **Smoke through the application** (staging first): log in once per role, open the main screens, register a visitor,
   approve a payment, close a test voting. On staging you can run the repository's verification scripts against the
   staging backend (`scripts/verification/*`), never against production data with write scenarios.

## 5. Rollback

There are no down-migrations. Rollback is restoring the backup taken in step 0.1. Package bodies can be rolled back
alone by re-running the previous file that defined them (V5.28 → V5.54 → V5.58 → V5.64), but data back-fills cannot
be undone that way — prefer a full restore if anything goes wrong.

## 6. Application configuration that must exist before the backend starts

The `prod` profile has no defaults for secrets. Set at least: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`
(new value — the old development secret is public), the Wompi keys of the production merchant
(`WOMPI_PUBLIC_KEY`, `WOMPI_INTEGRITY_SECRET`, `WOMPI_EVENTS_SECRET`; rotated, see the security notes), the Brevo key
(`BREVO_API_KEY`) and the mail sender, and `CORS_ALLOWED_ORIGINS` with the real frontend origin. Check
`backend/.env.example` for the complete, current list.
