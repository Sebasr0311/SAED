# G03 — Database backup and restore (isolated proof and runbook)

Date: 2026-10-09. Source: isolated recovery-11 install (`saed-isolated-5f9f732c8d61`, Oracle 18 XE, PDB `XEPDB1`). Destination: a second, fresh disposable container `saed-restore-ab97fd94` with **no published ports** (same immutable image digest). No production system was touched.

## Runbook

1. **Consistent export** (Data Pump, SYSDBA inside the source container, PDB selected with `ORACLE_PDB_SID=XEPDB1`), parfile:
   ```
   userid="/ as sysdba"
   schemas=SAED_ISO_OWNER,SAED_SEC_MASTER,SAED_ISO_RUNTIME,SAED_ISO_CONTROL
   directory=DATA_PUMP_DIR
   dumpfile=saed_g03.dmp
   logfile=saed_g03_exp.log
   flashback_time=systimestamp
   reuse_dumpfiles=y
   ```
   In production substitute the real owner/runtime/security schema names. `flashback_time` makes the dump transactionally consistent while the application keeps running.
2. **Transfer** the dump file (`docker cp` here; object storage in production). The dump contains password hashes and personal data: store it encrypted and access-controlled. Here it lives under the Git-ignored `local/isolated-oracle/g03/`.
3. **Import** into the destination PDB with the same schema list (`impdp parfile=…` without `flashback_time`).
4. **Recreate global objects that Data Pump does not carry:** `CREATE CONTEXT SAED_CTX USING <owner>.PKG_SAED_SESSION;` (application contexts are not schema objects), then `DBMS_UTILITY.COMPILE_SCHEMA` for the owner and security schemas.
5. **Verify** with the read-only snapshot (`local/isolated-oracle/recovery-11/snapshot.sql`: row counts of 9 key tables, SHA-256 digests of credentials/status, assignments and organizations, enabled policies, object/invalid counts, disabled triggers) on source and destination, and run the RLS functional check below.

## Evidence

| Check | Result |
|---|---|
| Export | 104 tables, 59 s, dump 8.6 MB. 8 errors, all `ORA-39127` from the OLAP callout `SYS.DBMS_CUBE_EXP` that does not exist in the XE image (no SAED object affected). |
| Import | `successfully completed`, 0 ORA errors, 35 s (39 s wall clock). |
| Missing after import | `SAED_CTX` application context (expected; recreated in step 4). |
| Snapshot comparison | Identical: all row counts, credential/assignment/organization digests, 95 enabled policies, 637 objects, 0 invalid, 0 disabled triggers. Only `LAST_DDL_TIME` differs, as import recreates objects. |
| RLS functional check as the runtime account (password sent only on stdin), using the application's sequence `SET_BOOTSTRAP_CONTEXT` → `SET_CONTEXT` | Source and restored DB give the same results: no context → 0 properties; organization 91001 property admin → 1 property and 2 units, 0 foreign; organization 92001 → 1 property, 0 foreign; after `CLEAR_CONTEXT` → 0. `SET_CONTEXT` without identity is rejected with ORA-20085 on both. |

**Recovery point:** the export's `flashback_time` (transactionally consistent snapshot). **Recovery time measured here:** about 2 minutes of database work (59 s export + 35 s import + context/compile), excluding container provisioning (~40 s) and file transfer.

## Not proven here (operational, outside the isolated environment)
- Uploaded attachments: the backend stores files on the filesystem (`FileStorageService`); their backup must be part of the same procedure (copy the upload directory at the same recovery point and compare SHA-256 per file). F00-04 tracks persistent upload storage.
- The academic target of 60-day retention and 99.5 % availability depends on the production hosting (scheduled exports, retention policy, off-site copies) and must be configured there.
- Point-in-time recovery beyond logical exports (archive logs / RMAN or managed-database backups) depends on the production database service.
