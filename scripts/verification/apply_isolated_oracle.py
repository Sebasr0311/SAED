"""Explicit, single-attempt installation in the reviewed disposable container.

No arbitrary SQL/endpoint option is exposed. Construction and mocked tests do not
connect Oracle. Partial/unknown execution is blocked until independent inspection;
this script never drops schemas, resets state or resumes a failed installation.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

import isolated_oracle as prepared

CONTAINER = "9d757df82b100e85346522e6baae17b985da763bc14362cbd9c791293a8d5ab5"
RUN_ID = "00ecef904fae11ad5c2af7eface4510e"
CONTROL = "SAED_ISO_CONTROL"
OWNER = prepared.OWNER
RUNTIME = prepared.RUNTIME
SECURITY = prepared.SECURITY
PDB = prepared.PDB
DDL_PRIVILEGES = "CREATE TABLE, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER, CREATE VIEW"
STATE_NAME = "execution-state.json"


def child_environment() -> dict[str, str]:
    allowed = {"SYSTEMROOT", "WINDIR", "PATH", "PATHEXT", "TEMP", "TMP", "COMSPEC", "USERPROFILE", "LOCALAPPDATA", "APPDATA"}
    environment = {key: value for key, value in os.environ.items() if key.upper() in allowed}
    environment["DOCKER_HOST"] = "npipe:////./pipe/dockerDesktopLinuxEngine"
    return environment


def object_json(path: Path) -> dict:
    if path.is_symlink():
        raise ValueError("State file must not be a link")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("State must be an object")
    return data


def credentials() -> dict:
    data = object_json(prepared.WORK / "private-state.json")
    if data.get("state") != prepared.STATE:
        raise ValueError("Generated private state selector does not match")
    for key in ("owner_password", "runtime_password", "security_password"):
        if not isinstance(data.get(key), str) or not re.fullmatch(r"[0-9a-f]{30}", data[key]):
            raise ValueError("Generated private state is invalid")
    if not isinstance(data.get("marker"), str) or not re.fullmatch(r"[0-9a-f]{48}", data["marker"]):
        raise ValueError("Generated private marker is invalid")
    return data


def candidate_identity(manifest: dict) -> str:
    # Bind code/config bytes as well as migration and prepared-artifact bytes.
    bindings = {name: prepared.digest((prepared.ROOT / name).read_text(encoding="utf-8-sig")) for name in ("scripts/verification/isolated_oracle.py", "scripts/verification/apply_isolated_oracle.py", "scripts/verification/ProtectIsolatedState.ps1", "backend/src/test/resources/application-isolated.yml")}
    return prepared.digest(json.dumps({"state": prepared.STATE, "manifest": manifest, "bindings": bindings}, sort_keys=True))


def persist_state(state: dict) -> None:
    prepared.check_state_path()
    destination = prepared.WORK / STATE_NAME
    temporary = prepared.WORK / "execution-state.tmp"
    if destination.is_symlink() or temporary.exists() or temporary.is_symlink():
        raise ValueError("Unexpected execution-state file; preserve it")
    prepared.write_private(temporary, json.dumps(state, indent=2))
    temporary.replace(destination)
    if os.name == "nt" and prepared.protect_windows_state().returncode:
        raise ValueError("Execution-state ACL protection failed")


def output_protocol(result: subprocess.CompletedProcess, stage: str) -> dict:
    raw = result.stdout + result.stderr
    codes = sorted(set(re.findall(r"(?:ORA|PLS|SP2|TNS)-[0-9]+", raw)))
    if result.returncode or codes or "compilation errors" in raw.lower():
        raise ValueError("SQL stage failed: " + (", ".join(codes) or "process/compilation failure"))
    lines = [line.strip() for line in raw.splitlines() if line.strip()]
    expected = "SAED_STAGE_OK:" + stage
    if lines.count(expected) != 1:
        raise ValueError("SQL stage completion was not confirmed")
    counts = {}
    for line in lines:
        if line == expected or re.fullmatch(r"SEED_PLANES: [0-9]+ planes cargados", line):
            continue
        match = re.fullmatch(r"SAED_CATALOG:TABLES=([0-9]+),INVALID=0,POLICIES=([0-9]+)", line)
        if match:
            if counts:
                raise ValueError("Duplicated catalog output")
            counts = {"tables": int(match.group(1)), "invalid": 0, "policies": int(match.group(2))}
            continue
        raise ValueError("Unexpected SQL output suppressed")
    if stage in ("catalog", "sealed-catalog") and not counts:
        raise ValueError("Catalog counts were not confirmed")
    return counts


class Installer:
    def __init__(self, container: str, run_id: str):
        if container != CONTAINER or run_id != RUN_ID:
            raise ValueError("Only the reviewed isolated container/run is permitted")
        self.container = container
        self.run_id = run_id
        prepared.check_state_path()
        self.state_id = prepared.STATE
        self.manifest = prepared.verify_prepared()
        self.private = credentials()
        self.candidate = candidate_identity(self.manifest)
        self.stages = self.build_stages()
        self.state = {"format": 1, "state": self.state_id, "candidate": self.candidate, "container": container, "run": run_id, "status": "in-progress", "completed": [], "stage": "bootstrap"}
        path = prepared.WORK / STATE_NAME
        if path.exists():
            self.state = object_json(path)
            if type(self.state.get("format")) is not int or self.state.get("format") != 1 or self.state.get("state") != self.state_id or self.state.get("candidate") != self.candidate or self.state.get("container") != container or self.state.get("run") != run_id:
                raise ValueError("Execution state does not match the reviewed candidate")
            if self.state.get("status") != "complete":
                raise ValueError("Prior installation is partial/blocked; no automatic retry or reset")
            if self.state.get("completed") != list(self.stages):
                raise ValueError("Completed state has an incomplete or unexpected stage inventory")

    def marker_guard(self, principal: str) -> str:
        if principal not in ("SYS", OWNER, SECURITY, RUNTIME):
            raise ValueError("Unexpected database principal")
        return f"""DECLARE n NUMBER;
BEGIN
 IF SYS_CONTEXT('USERENV','CON_NAME') != '{PDB}' OR SYS_CONTEXT('USERENV','SESSION_USER') != '{principal}' THEN
  RAISE_APPLICATION_ERROR(-20990,'ISOLATED_PDB_OR_PRINCIPAL_MISMATCH');
 END IF;
 SELECT COUNT(*) INTO n FROM {CONTROL}.RUN_GUARD
 WHERE MARKER='{self.private['marker']}' AND CONTAINER_ID='{self.container}'
 AND RUN_ID='{self.run_id}' AND CANDIDATE='{self.candidate}' AND PDB_NAME='{PDB}';
 IF n != 1 THEN RAISE_APPLICATION_ERROR(-20991,'ISOLATED_MARKER_MISMATCH'); END IF;
END;
/
"""

    def bootstrap(self) -> str:
        return f"""DECLARE n NUMBER;
BEGIN
 IF SYS_CONTEXT('USERENV','CON_NAME') != '{PDB}' OR SYS_CONTEXT('USERENV','SESSION_USER') != 'SYS' THEN
  RAISE_APPLICATION_ERROR(-20990,'ISOLATED_PDB_OR_PRINCIPAL_MISMATCH'); END IF;
 SELECT COUNT(*) INTO n FROM DBA_USERS WHERE USERNAME LIKE 'SAED%';
 IF n != 0 THEN RAISE_APPLICATION_ERROR(-20992,'ISOLATED_SCHEMAS_ALREADY_EXIST'); END IF;
 SELECT COUNT(*) INTO n FROM DBA_OBJECTS WHERE OWNER LIKE 'SAED%';
 IF n != 0 THEN RAISE_APPLICATION_ERROR(-20992,'ISOLATED_OBJECTS_ALREADY_EXIST'); END IF;
 SELECT COUNT(*) INTO n FROM DBA_CONTEXT WHERE NAMESPACE='SAED_CTX';
 IF n != 0 THEN RAISE_APPLICATION_ERROR(-20992,'ISOLATED_CONTEXT_ALREADY_EXISTS'); END IF;
END;
/
CREATE USER {CONTROL} NO AUTHENTICATION ACCOUNT LOCK DEFAULT TABLESPACE USERS QUOTA 1M ON USERS;
CREATE TABLE {CONTROL}.RUN_GUARD (MARKER VARCHAR2(48) PRIMARY KEY, CONTAINER_ID VARCHAR2(64) NOT NULL, RUN_ID VARCHAR2(32) NOT NULL, CANDIDATE VARCHAR2(64) NOT NULL, PDB_NAME VARCHAR2(20) NOT NULL);
INSERT INTO {CONTROL}.RUN_GUARD VALUES ('{self.private['marker']}','{self.container}','{self.run_id}','{self.candidate}','{PDB}');
COMMIT;
CREATE USER {OWNER} IDENTIFIED BY "{self.private['owner_password']}" DEFAULT TABLESPACE USERS QUOTA 100M ON USERS;
CREATE USER {RUNTIME} IDENTIFIED BY "{self.private['runtime_password']}";
CREATE USER {SECURITY} IDENTIFIED BY "{self.private['security_password']}";
GRANT CREATE SESSION TO {OWNER}, {RUNTIME}, {SECURITY};
GRANT {DDL_PRIVILEGES} TO {OWNER};
GRANT EXECUTE ON SYS.DBMS_RLS TO {OWNER};
GRANT EXECUTE ON SYS.DBMS_SESSION TO {OWNER};
GRANT CREATE PROCEDURE, CREATE SYNONYM TO {SECURITY};
GRANT EXEMPT ACCESS POLICY TO {SECURITY};
GRANT SELECT ON {CONTROL}.RUN_GUARD TO {OWNER}, {RUNTIME}, {SECURITY};
CREATE CONTEXT SAED_CTX USING {OWNER}.PKG_SAED_SESSION;
"""

    def build_stages(self) -> dict[str, tuple[str, str]]:
        stages = {"bootstrap": ("SYS", self.bootstrap())}
        for name in ("010-structure.sql", "020-catalogs.sql", "030-synthetic-fixture.sql", "031-mixed-assignment.sql", "040-auth-grants.sql", "050-auth-synonyms.sql", "060-auth-package.sql", "900-baseline-policies.sql"):
            principal = "SYS" if name == "030-synthetic-fixture.sql" else SECURITY if name.startswith(("050-", "060-")) else OWNER
            stages[name] = (principal, (prepared.WORK / name).read_text(encoding="utf-8"))
        for name in sorted(self.manifest["files"]):
            if re.fullmatch(r"1[0-9][0-9]-V5[.][0-9]+__[A-Za-z0-9_]+[.]sql", name):
                stages[name] = (OWNER, (prepared.WORK / name).read_text(encoding="utf-8"))
        stages["lifecycle-fixture"] = (OWNER, """BEGIN
 UPDATE USUARIOS SET ESTADO='INACTIVO' WHERE ID_USUARIO=92005;
 IF SQL%ROWCOUNT != 1 THEN RAISE_APPLICATION_ERROR(-20994,'INACTIVE_FIXTURE_MISSING'); END IF;
 UPDATE USUARIOS SET ESTADO='BLOQUEADO' WHERE ID_USUARIO=92006;
 IF SQL%ROWCOUNT != 1 THEN RAISE_APPLICATION_ERROR(-20994,'BLOCKED_FIXTURE_MISSING'); END IF;
END;
/
COMMIT;
""")
        stages["runtime-grants"] = (OWNER, f"""BEGIN
 FOR t IN (SELECT TABLE_NAME FROM USER_TABLES) LOOP
  IF t.TABLE_NAME IN ('AUDITORIA_LOG','HISTORIAL_REPORTES','EJECUCIONES_AUTOMATIZACION') THEN
   EXECUTE IMMEDIATE 'GRANT SELECT, INSERT ON ' || DBMS_ASSERT.SIMPLE_SQL_NAME(t.TABLE_NAME) || ' TO {RUNTIME}';
  ELSE
   EXECUTE IMMEDIATE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ' || DBMS_ASSERT.SIMPLE_SQL_NAME(t.TABLE_NAME) || ' TO {RUNTIME}';
  END IF;
 END LOOP;
 FOR v IN (SELECT VIEW_NAME FROM USER_VIEWS) LOOP
  EXECUTE IMMEDIATE 'GRANT SELECT ON ' || DBMS_ASSERT.SIMPLE_SQL_NAME(v.VIEW_NAME) || ' TO {RUNTIME}';
 END LOOP;
 FOR s IN (SELECT SEQUENCE_NAME FROM USER_SEQUENCES) LOOP
  EXECUTE IMMEDIATE 'GRANT SELECT ON ' || DBMS_ASSERT.SIMPLE_SQL_NAME(s.SEQUENCE_NAME) || ' TO {RUNTIME}';
 END LOOP;
 FOR p IN (SELECT OBJECT_NAME FROM USER_OBJECTS WHERE OBJECT_TYPE IN ('PACKAGE','PROCEDURE') AND OBJECT_NAME IN ('PKG_SAED_SESSION','PKG_SAED_SECURITY_RLS','SP_VALIDAR_CONSUMIR_QR')) LOOP
  EXECUTE IMMEDIATE 'GRANT EXECUTE ON ' || DBMS_ASSERT.SIMPLE_SQL_NAME(p.OBJECT_NAME) || ' TO {RUNTIME}';
 END LOOP;
END;
/
""")
        stages["catalog"] = ("SYS", self.catalog())
        stages["seal"] = ("SYS", f"REVOKE {DDL_PRIVILEGES} FROM {OWNER};\nREVOKE EXECUTE ON SYS.DBMS_RLS FROM {OWNER};\nREVOKE CREATE SESSION FROM {OWNER};\nALTER USER {OWNER} ACCOUNT LOCK;\nREVOKE CREATE SESSION, CREATE PROCEDURE, CREATE SYNONYM FROM {SECURITY};\nALTER USER {SECURITY} ACCOUNT LOCK;\n")
        stages["sealed-catalog"] = ("SYS", self.catalog() + f"""DECLARE n NUMBER;
BEGIN
 SELECT COUNT(*) INTO n FROM DBA_USERS WHERE USERNAME IN ('{OWNER}','{SECURITY}','{CONTROL}') AND ACCOUNT_STATUS LIKE 'LOCKED%';
 IF n != 3 THEN RAISE_APPLICATION_ERROR(-20999,'ISOLATED_INSTALLER_ACCOUNTS_NOT_LOCKED'); END IF;
 SELECT COUNT(*) INTO n FROM DBA_SYS_PRIVS WHERE GRANTEE='{OWNER}' OR (GRANTEE='{SECURITY}' AND PRIVILEGE != 'EXEMPT ACCESS POLICY');
 IF n != 0 THEN RAISE_APPLICATION_ERROR(-20999,'ISOLATED_TRANSIENT_PRIVILEGES_REMAIN'); END IF;
 SELECT COUNT(*) INTO n FROM DBA_TAB_PRIVS WHERE GRANTEE IN ('{OWNER}','{RUNTIME}') AND OWNER='SYS' AND TABLE_NAME='DBMS_RLS';
 IF n != 0 THEN RAISE_APPLICATION_ERROR(-20999,'ISOLATED_POLICY_DDL_PRIVILEGE_REMAINS'); END IF;
END;
/
""")
        return stages

    def catalog(self) -> str:
        names = sorted(set(re.findall(r'CREATE TABLE "([A-Z0-9_]+)"', (prepared.WORK / "010-structure.sql").read_text(encoding="utf-8"))))
        if not names:
            raise ValueError("Baseline table inventory missing")
        literals = ",".join("'" + name + "'" for name in names)
        return f"""DECLARE n NUMBER;
BEGIN
 SELECT COUNT(*) INTO n FROM DBA_OBJECTS WHERE OWNER IN ('{OWNER}','{SECURITY}') AND STATUS != 'VALID';
 IF n != 0 THEN RAISE_APPLICATION_ERROR(-20995,'ISOLATED_INVALID_OBJECTS'); END IF;
 SELECT COUNT(*) INTO n FROM DBA_TABLES WHERE OWNER='{OWNER}' AND TABLE_NAME IN ({literals});
 IF n != {len(names)} THEN RAISE_APPLICATION_ERROR(-20996,'ISOLATED_REQUIRED_TABLES_MISSING'); END IF;
 SELECT COUNT(*) INTO n FROM DBA_TAB_COLUMNS WHERE OWNER='{OWNER}' AND TABLE_NAME='REPORTES_CONFIGURADOS' AND COLUMN_NAME IN ('ID_ORGANIZACION','ID_PROPIEDAD','PARAMETROS_FILTRO_JSON','ID_USUARIO_CREO','FECHA_CREACION','FECHA_ACTUALIZACION');
 IF n != 6 THEN RAISE_APPLICATION_ERROR(-20998,'ISOLATED_REPORT_COLUMNS_MISSING'); END IF;
 SELECT COUNT(*) INTO n FROM DBA_POLICIES WHERE OBJECT_OWNER='{OWNER}' AND ENABLE='YES';
 IF n < 90 THEN RAISE_APPLICATION_ERROR(-20997,'ISOLATED_POLICY_COVERAGE_MISSING'); END IF;
 SELECT COUNT(*) INTO n FROM DBA_SYS_PRIVS WHERE GRANTEE='{RUNTIME}' AND PRIVILEGE != 'CREATE SESSION';
 IF n != 0 THEN RAISE_APPLICATION_ERROR(-20999,'ISOLATED_RUNTIME_PRIVILEGES_EXCESSIVE'); END IF;
 SELECT COUNT(*) INTO n FROM DBA_ROLE_PRIVS WHERE GRANTEE='{RUNTIME}';
 IF n != 0 THEN RAISE_APPLICATION_ERROR(-20999,'ISOLATED_RUNTIME_ROLES_EXCESSIVE'); END IF;
END;
/
SELECT 'SAED_CATALOG:TABLES=' || (SELECT COUNT(*) FROM DBA_TABLES WHERE OWNER='{OWNER}') || ',INVALID=0,POLICIES=' || (SELECT COUNT(*) FROM DBA_POLICIES WHERE OBJECT_OWNER='{OWNER}' AND ENABLE='YES') FROM DUAL;
"""

    def invoke(self, stage: str) -> dict:
        if stage not in self.stages:
            raise ValueError("Unrecognized isolated installation stage")
        if prepared.STATE != self.state_id:
            raise ValueError("Recovery state selector changed; no SQL invoked")
        current = prepared.verify_prepared()
        if candidate_identity(current) != self.candidate:
            raise ValueError("Installation candidate changed; no SQL invoked")
        # A fixed clean environment removes Docker-context and datasource overrides.
        prepared.inspect_container(self.container, self.run_id, environment=child_environment())
        principal, payload = self.stages[stage]
        if principal not in ("SYS", OWNER, SECURITY, RUNTIME) or (stage == "bootstrap" and principal != "SYS"):
            raise ValueError("Unexpected database principal")
        if principal == "SYS":
            connection = "/ AS SYSDBA"
            pdb_selection = f"ALTER SESSION SET CONTAINER={PDB};\n"
        else:
            key = "owner_password" if principal == OWNER else "security_password" if principal == SECURITY else "runtime_password"
            connection = principal + '/"' + self.private[key] + '"@//127.0.0.1:1521/' + PDB
            pdb_selection = ""
        guard = "" if stage == "bootstrap" else self.marker_guard(principal)
        owner_schema = ""
        if stage == "030-synthetic-fixture.sql":
            owner_schema = f"ALTER SESSION SET CURRENT_SCHEMA={OWNER};\nDECLARE\nBEGIN\n IF SYS_CONTEXT('USERENV','CURRENT_SCHEMA') != '{OWNER}' THEN\n  RAISE_APPLICATION_ERROR(-20990,'ISOLATED_OWNER_SCHEMA_MISMATCH');\n END IF;\nEND;\n/\n"
        business_context = ""
        if principal == OWNER and stage not in ("010-structure.sql", "020-catalogs.sql", "030-synthetic-fixture.sql", "031-mixed-assignment.sql"):
            business_context = "BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1,0,NULL,'SUPERADMIN'); END;\n/\n"
        compile_guard = ""
        if principal in (OWNER, SECURITY):
            # DDL invalidates dependents without compile errors; recompile only invalid objects first.
            # Objects with real compile errors stay INVALID and the guard still fails closed.
            compile_guard = "BEGIN DBMS_UTILITY.COMPILE_SCHEMA(schema => USER, compile_all => FALSE); END;\n/\nDECLARE n NUMBER; BEGIN SELECT COUNT(*) INTO n FROM USER_OBJECTS WHERE STATUS != 'VALID'; IF n != 0 THEN RAISE_APPLICATION_ERROR(-20995,'ISOLATED_INVALID_OBJECTS'); END IF; END;\n/\n"
        elif stage == "030-synthetic-fixture.sql":
            # The fixture's identity DDL invalidates dependent owner objects without compile errors;
            # recompile only invalid ones and still fail closed if any remain invalid.
            compile_guard = (f"BEGIN DBMS_UTILITY.COMPILE_SCHEMA(schema => '{OWNER}', compile_all => FALSE); END;\n/\n"
                             f"DECLARE n NUMBER; BEGIN SELECT COUNT(*) INTO n FROM DBA_OBJECTS WHERE OWNER = '{OWNER}' AND STATUS != 'VALID'; IF n != 0 THEN RAISE_APPLICATION_ERROR(-20995,'ISOLATED_INVALID_OBJECTS'); END IF; END;\n/\n")
        settings = "\n".join("SET " + option for option in ("ECHO OFF", "VERIFY OFF", "FEEDBACK OFF", "HEADING OFF", "PAGESIZE 0", "DEFINE OFF", "SERVEROUTPUT OFF", "SQLBLANKLINES ON"))
        wrapper = settings + "\nWHENEVER SQLERROR EXIT FAILURE ROLLBACK\nWHENEVER OSERROR EXIT FAILURE ROLLBACK\nCONNECT " + connection + "\n" + pdb_selection + guard + owner_schema + business_context + payload + "\n" + compile_guard + "PROMPT SAED_STAGE_OK:" + stage + "\nEXIT SUCCESS\n"
        # The Linux SQLPlus client must agree with all three UTF-8 pipe streams;
        # neither the Windows ANSI page nor inherited Oracle settings are input.
        try:
            result = subprocess.run(
                ["docker", "exec", "-i", "--user", "oracle", "--env", "NLS_LANG=AMERICAN_AMERICA.AL32UTF8", self.container, "sqlplus", "-L", "-s", "/nolog"],
                input=wrapper, capture_output=True, text=True, encoding="utf-8",
                errors="strict", timeout=180, env=child_environment(),
            )
        except UnicodeError:
            # Decoder/encoder diagnostics can contain private SQL or client text.
            # Preserve the failed attempt; never retry with replacement/fallback.
            raise ValueError("SQL stage failed: UTF-8 transport failure") from None
        return output_protocol(result, stage)

    def apply(self) -> None:
        prepared.check_state_path()
        if self.state["status"] == "complete":
            self.invoke("sealed-catalog")
            print("EXISTING INSTALLATION MARKER/CATALOG VERIFIED: no schema recreated")
            return
        lock = prepared.WORK / "installation.lock"
        if lock.exists() or lock.is_symlink():
            raise ValueError("Installation lock exists; no concurrent apply or automatic retry")
        prepared.write_private(lock, self.candidate)
        persist_state(self.state)
        try:
            for stage in self.stages:
                self.state["stage"] = stage
                persist_state(self.state)
                counts = self.invoke(stage)
                self.state["completed"].append(stage)
                if counts:
                    self.state["catalog_counts"] = counts
                persist_state(self.state)
            self.state["status"] = "complete"
            persist_state(self.state)
        except (ValueError, OSError, subprocess.TimeoutExpired) as failure:
            self.state["status"] = "blocked"
            self.state["reason"] = str(failure) if isinstance(failure, ValueError) else type(failure).__name__
            persist_state(self.state)
            raise
        print("ISOLATED INSTALLATION COMPLETE: catalog counts recorded; application/RLS tests still pending")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("apply",))
    parser.add_argument("--container-id", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--state", required=True, help="Explicit reviewed recovery state, for example recovery-01")
    args = parser.parse_args()
    prepared.select_state(args.state)
    Installer(args.container_id, args.run_id).apply()


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, json.JSONDecodeError, subprocess.TimeoutExpired) as failure:
        print("BLOCKED: " + (str(failure) if isinstance(failure, ValueError) else type(failure).__name__), file=sys.stderr)
        sys.exit(1)
