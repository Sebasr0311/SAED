"""Prepare a guarded disposable Oracle schema. Never discovers an application DB.

Preparation performs no Docker or database operation. Applying requires a reviewed
manifest, explicit container identity, immutable image, loopback port and marker.
SQLPlus output is captured and only error codes are reported, never SQL/secrets.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "local" / "isolated-oracle"
STATE = None
IMAGE = "sha256:ee0d7b8ac3665fb80219cbcb5a080cc202cc970120e1fbef592d7592274cef22"
OWNER = "SAED_ISO_OWNER"
RUNTIME = "SAED_ISO_RUNTIME"
SECURITY = "SAED_SEC_MASTER"
PORT = "11521"
LABEL = "saed.run"
SCOPE_LABEL = "saed.scope"
SCOPE = "isolated-fixture"
PDB = "XEPDB1"
IMAGE_REF = "docker.io/gvenzl/oracle-xe@" + IMAGE
JAVA = Path(r"C:\Users\JUAN\.jdks\ms-17.0.19\bin\java.exe")
MAVEN_CACHE = Path.home() / ".m2/repository"
CRYPTO = MAVEN_CACHE / "org/springframework/security/spring-security-crypto/6.5.11/spring-security-crypto-6.5.11.jar"
LOGGING = MAVEN_CACHE / "org/springframework/spring-jcl/6.2.19/spring-jcl-6.2.19.jar"
ROLES = ("SUPERADMIN", "ADMIN_ORGANIZACION", "ADMIN_PROPIEDAD", "PORTERO", "RESIDENTE", "RESIDENTE_CONVIVENCIA")


def state_directory(name: str) -> Path:
    if not isinstance(name, str) or not re.fullmatch(r"recovery-[0-9]{2,4}", name):
        raise ValueError("Explicit recovery state selector required")
    base = ROOT / "local" / "isolated-oracle"
    directory = base / name
    for ancestor in (directory, *directory.parents):
        if ancestor.is_symlink() or ancestor.is_junction() or (ancestor.exists() and getattr(ancestor.lstat(), "st_file_attributes", 0) & 0x400):
            raise ValueError("Recovery state must not traverse a reparse point")
    if directory.resolve().parent != base.resolve():
        raise ValueError("Recovery state escapes the fixed workspace directory")
    return directory


def select_state(name: str) -> None:
    global WORK, STATE
    directory = state_directory(name)
    WORK, STATE = directory, name


def check_state_path() -> None:
    if WORK != state_directory(STATE):
        raise ValueError("Recovery state directory does not match its selector")


def digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def section(text: str, start: str, end: str) -> str:
    beginnings = list(re.finditer(r"(?m)^" + re.escape(start) + r"[^\n]*\n", text))
    endings = list(re.finditer(r"(?m)^" + re.escape(end) + r"[^\n]*\n", text))
    if len(beginnings) != 1 or len(endings) != 1 or beginnings[0].end() >= endings[0].start():
        raise ValueError("Invalid baseline section order")
    return text[beginnings[0].end():endings[0].start()]


def replace_once(text: str, old: str, new: str, reason: str) -> str:
    if text.count(old) != 1:
        raise ValueError("Expected normalization missing: " + reason)
    return text.replace(old, new)


def sanitize_source(text: str) -> str:
    # Imported files cannot override the future guarded SQLPlus executor.
    text = re.sub(r"(?im)^[ \t]*SET[ \t]+(?:ECHO|VERIFY|FEEDBACK|HEADING|PAGESIZE|LINESIZE|DEFINE|TERMOUT|TRIMSPOOL|SERVEROUTPUT|TIMING)\b[^\n]*$", "", text)
    return re.sub(r"(?im)^[ \t]*EXIT;?[ \t]*\s*\Z", "", text)


def extract_sources() -> tuple[dict[str, str], list[dict], list[str]]:
    baseline_path = ROOT / "database/migrations/V5.0__master_baseline.sql"
    baseline = baseline_path.read_text(encoding="utf-8-sig")
    structure = section(baseline, "-- 2. TABLAS DEL SISTEMA", "-- 4. SEMILLAS MAESTRAS DE CONFIGURACION, CATALOGOS E IDENTIDAD")
    structure = replace_once(structure, 'ALTER TRIGGER "TRG_HISTREP_INMUTABLE" \n\n-- Constraints', 'ALTER TRIGGER "TRG_HISTREP_INMUTABLE" COMPILE;\n\n-- Constraints', "unfinished trigger compile")
    structure = replace_once(structure, "'PORTERO','VIGILANTE','RESIDENTE','PROPIETARIO_UNIDAD'", "'PORTERO','VIGILANTE','RESIDENTE','RESIDENTE_CONVIVENCIA','PROPIETARIO_UNIDAD'", "conviviente role constraint")
    structure = replace_once(structure, "('PROPIETARIO','ARRENDATARIO','FAMILIAR','OTRO')", "('PROPIETARIO','ARRENDATARIO','FAMILIAR','CONVIVIENTE','OTRO')", "conviviente resident type constraint")
    catalogs = section(baseline, "-- 4.1 TIPOS_DOCUMENTO", "-- 4.5 BOOTSTRAP DE IDENTIDAD INICIAL")
    policies = section(baseline, "-- 7. REGISTRO DECLARATIVO DE LAS 90 POLITICAS RLS", "-- 8. PAQUETE DE AUTENTICACION MAESTRO")
    auth_heading = re.search(r"(?m)^-- 8\. PAQUETE DE AUTENTICACION MAESTRO[^\n]*\n", baseline)
    if not auth_heading:
        raise ValueError("Authentication section missing")
    auth = baseline[auth_heading.end():]
    grant_start = auth.index("GRANT SELECT, UPDATE(")
    synonym_start = auth.index("BEGIN\n    EXECUTE IMMEDIATE 'CREATE OR REPLACE SYNONYM")
    package_start = auth.index("CREATE OR REPLACE PACKAGE SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP")
    auth_grants = auth[grant_start:synonym_start]
    auth_synonyms = auth[synonym_start:package_start].replace("SYNONYM SAED_SEC_MASTER.", "SYNONYM ")
    auth_synonyms = auth_synonyms.replace("' || USER || '", OWNER)
    auth_package = auth[package_start:].replace("SAED_SEC_MASTER.PKG_AUTH_BOOTSTRAP", "PKG_AUTH_BOOTSTRAP")
    auth_package = auth_package.replace("TO SAED_APP;", f"TO {RUNTIME};")
    auth_package = replace_once(auth_package, "REVOKE EXECUTE ON PKG_AUTH_BOOTSTRAP FROM PUBLIC;", "BEGIN\n EXECUTE IMMEDIATE 'REVOKE EXECUTE ON PKG_AUTH_BOOTSTRAP FROM PUBLIC';\n EXCEPTION WHEN OTHERS THEN IF SQLCODE != -1927 THEN RAISE; END IF;\nEND;\n/", "absent PUBLIC grant")
    parts = {"010-structure.sql": structure, "020-catalogs.sql": catalogs, "040-auth-grants.sql": auth_grants, "050-auth-synonyms.sql": auth_synonyms, "060-auth-package.sql": auth_package, "900-baseline-policies.sql": policies}
    sources = [{"file": str(baseline_path.relative_to(ROOT)).replace("\\", "/"), "sha256": digest(baseline)}]
    versions = set()
    for path in sorted((ROOT / "database/migrations").glob("V5.*__*.sql"), key=lambda p: int(p.name.split("__")[0].split(".")[1])):
        version = int(path.name.split("__")[0].split(".")[1])
        # V5.30 (demo-seed purge) is deliberately excluded; V5.31/V5.32 version formerly startup-only schema.
        # V5.30 (demo-seed purge) is deliberately excluded; every other V5.x migration is applied in order.
        if version < 1 or version == 30:
            continue
        if version in versions:
            raise ValueError("Duplicate migration version")
        versions.add(version)
        source = path.read_text(encoding="utf-8-sig")
        sources.append({"file": str(path.relative_to(ROOT)).replace("\\", "/"), "sha256": digest(source)})
        if version == 3:
            source = replace_once(source, "ALTER TABLE SAED_APP.AUDITORIA_LOG", "ALTER TABLE AUDITORIA_LOG", "hardcoded application schema")
        if version == 20:
            # A non-constant DEFAULT forces a full-table rewrite that active VPD rejects (ORA-28133).
            # Add the column metadata-only, then set the default for future inserts.
            source = replace_once(source,
                "EXECUTE IMMEDIATE 'ALTER TABLE OBRA_TRABAJADORES ADD (FECHA_AUTORIZACION TIMESTAMP(6) WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)';",
                "EXECUTE IMMEDIATE 'ALTER TABLE OBRA_TRABAJADORES ADD (FECHA_AUTORIZACION TIMESTAMP(6) WITH TIME ZONE)';\n"
                "        EXECUTE IMMEDIATE 'ALTER TABLE OBRA_TRABAJADORES MODIFY (FECHA_AUTORIZACION DEFAULT CURRENT_TIMESTAMP)';",
                "non-constant default under active VPD")
        if version == 23:
            # TIMESTAMP WITH TIME ZONE keys are indexed as SYS_EXTRACT_UTC(...); the baseline IX_ASAMBLEAS_PROP is
            # already equivalent, so only ORA-01408 (column list already indexed) is tolerated here.
            create = "EXECUTE IMMEDIATE 'CREATE INDEX IX_ASAMBLEAS_FECHA ON ASAMBLEAS (ID_PROPIEDAD, FECHA_HORA_PRIMERA_CONV)';"
            source = replace_once(source, create,
                "BEGIN " + create + " EXCEPTION WHEN OTHERS THEN IF SQLCODE != -1408 THEN RAISE; END IF; END;",
                "equivalent assembly date index")
        parts[f"{100 + version:03}-{path.name}"] = source
    if not set(range(1, 30)) <= versions or 30 in versions:
        raise ValueError("Migration chain V5.1 through V5.29 is incomplete or includes the excluded V5.30 purge")
    for name in parts:
        content = sanitize_source(parts[name])
        if re.search(r"(?im)IDENTIFIED\s+BY|\$2[aby]\$|DROP\s+USER|^\s*(?:CONNECT\b|HOST\b|SPOOL\b|@)", content):
            raise ValueError("Forbidden source directive in " + name)
        if re.search(r"(?i)MERGE\s+INTO\s+(USUARIOS|PERSONAS|ORGANIZACIONES|PROPIEDADES|ADMINISTRADORES_SAED|USUARIO_ASIGNACIONES)\b", content):
            raise ValueError("Production identity seed in " + name)
        parts[name] = content
    transformations = ["Exclude baseline security-user deletion, fixed credentials and original identity bootstrap.", "Complete the single dangling TRG_HISTREP_INMUTABLE ALTER with COMPILE.", "Allow RESIDENTE_CONVIVENCIA before V5.10 inserts it.", "Allow CONVIVIENTE resident type before V5.9 extends CK_RESIDUNIDAD_TIPO.","Run authentication grants as owner; synonyms/package as security owner without ANY grants.", "Authentication synonyms reference the isolated owner explicitly.", "V5.3 uses current isolated owner instead of SAED_APP.", "V5.20 adds FECHA_AUTORIZACION metadata-only, then sets its CURRENT_TIMESTAMP default (avoids VPD-blocked full-table rewrite).", "V5.23 tolerates only ORA-01408 for IX_ASAMBLEAS_FECHA (baseline IX_ASAMBLEAS_PROP is equivalent).","Remove imported SQLPlus SET/EXIT; exclude V5.30, V3/V4 and rollback files.", "Include V5.31 report tenant-scope migration after V5.29.", "Include V5.32 startup-only schema drift (ONBOARDING_INTENCIONES, PERSONAS/BLOQUEOS_ZONA columns, BLOQUEOS_ZONA indexes, HISTORIAL_REPORTES/PORTERIA_TURNOS policies) after V5.31."]
    return parts, sources, transformations


def generate_hashes(passwords: list[str]) -> list[str]:
    if not all(path.is_file() for path in (JAVA, CRYPTO, LOGGING)):
        raise ValueError("Reviewed local JDK/crypto dependencies unavailable; no downloads allowed")
    helper = ROOT / "scripts/verification/SyntheticPasswordHashes.java"
    result = subprocess.run([str(JAVA), "--class-path", os.pathsep.join((str(CRYPTO), str(LOGGING))), str(helper)], input="\n".join(passwords) + "\n", capture_output=True, text=True, timeout=90)
    hashes = result.stdout.splitlines()
    if result.returncode or len(hashes) != len(passwords) or any(not re.fullmatch(r"\$2[aby]\$12\$[./A-Za-z0-9]{53}", value) for value in hashes):
        raise ValueError("Synthetic password generation failed; private output suppressed")
    return hashes


def fixture_accounts() -> list[dict]:
    accounts = []
    for tenant_index in range(2):
        tenant = 91001 + tenant_index * 1000
        for role_index, role in enumerate(ROLES):
            if tenant_index and role == "SUPERADMIN":
                continue
            identifier = 1 if role == "SUPERADMIN" else tenant + role_index
            accounts.append({"id": identifier, "role": role, "organization": None if role == "SUPERADMIN" else tenant, "property": tenant if role_index >= 2 else None, "unit": tenant if role_index >= 4 else None, "username": f"iso_{tenant_index}_{role.lower()}", "email": f"iso-{tenant_index}-{role.lower()}@example.invalid", "password": secrets.token_hex(24)})
    return accounts


def fixture_sql(accounts: list[dict], hashes: list[str]) -> str:
    if len(accounts) != len(hashes):
        raise ValueError("Fixture account hashes do not match")
    statements = ["-- Synthetic fixture; keep generated credential material private.", "INSERT INTO ROLES (ID_ROL,CODIGO,NOMBRE,ALCANCE,ESTADO) VALUES (6,'RESIDENTE_CONVIVENCIA','Synthetic conviviente','UNIDAD','ACTIVO');"]
    for tenant in (91001, 92001):
        statements.append(f"INSERT INTO ORGANIZACIONES (ID_ORGANIZACION,NOMBRE,IDENTIFICACION_FISCAL,EMAIL_CONTACTO,ESTADO) VALUES ({tenant},'Synthetic organization {tenant}','ISO-{tenant}','org-{tenant}@example.invalid','ACTIVA');")
        # Active ENTERPRISE (plan 3) membership so plan-module guards allow every module (F3-07).
        statements.append(f"INSERT INTO MEMBRESIAS (ID_MEMBRESIA,ID_ORGANIZACION,ID_PLAN,FECHA_INICIO,FECHA_FIN,ESTADO,ES_PRUEBA) VALUES ({tenant},{tenant},3,TRUNC(SYSDATE)-1,ADD_MONTHS(TRUNC(SYSDATE),12),'ACTIVA','N');")
        for property_id in (tenant, tenant + 20):
            statements.append(f"INSERT INTO PROPIEDADES (ID_PROPIEDAD,ID_ORGANIZACION,ID_TIPO_PROPIEDAD,NOMBRE,DIRECCION,CIUDAD,PAIS,ESTADO) VALUES ({property_id},{tenant},1,'Synthetic property {property_id}','Synthetic address','Synthetic city','Colombia','ACTIVA');")
            for unit_id in (property_id, property_id + 10):
                statements.append(f"INSERT INTO UNIDADES (ID_UNIDAD,ID_PROPIEDAD,ID_TIPO_UNIDAD,IDENTIFICADOR,COEFICIENTE_COPROPIEDAD,ESTADO) VALUES ({unit_id},{property_id},1,'ISO-{unit_id}',0.5,'ACTIVA');")
    role_ids = {"SUPERADMIN": 1, "ADMIN_PROPIEDAD": 2, "ADMIN_ORGANIZACION": 3, "PORTERO": 4, "RESIDENTE": 5, "RESIDENTE_CONVIVENCIA": 6}
    for account, hashed in zip(accounts, hashes):
        if not re.fullmatch(r"\$2[aby]\$12\$[./A-Za-z0-9]{53}", hashed):
            raise ValueError("Invalid generated fixture credential")
        identifier = account["id"]
        statements.append(f"INSERT INTO PERSONAS (ID_PERSONA,ID_TIPO_DOCUMENTO,NUMERO_DOCUMENTO,TIPO_PERSONA,PRIMER_NOMBRE,PRIMER_APELLIDO,EMAIL,ESTADO) VALUES ({identifier},1,'ISO-{identifier}','NATURAL','Synthetic','{account['role']}','{account['email']}','ACTIVO');")
        statements.append(f"INSERT INTO USUARIOS (ID_USUARIO,ID_PERSONA,NOMBRE_USUARIO,EMAIL,HASH_PASSWORD,ESTADO) VALUES ({identifier},{identifier},'{account['username']}','{account['email']}','{hashed}','ACTIVO');")
        values = [str(identifier), str(identifier), str(role_ids[account["role"]])] + [str(account[key]) if account[key] is not None else "NULL" for key in ("organization", "property", "unit")]
        statements.append("INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION,ID_USUARIO,ID_ROL,ID_ORGANIZACION,ID_PROPIEDAD,ID_UNIDAD,ESTADO) VALUES (" + ",".join(values) + ",'ACTIVA');")
        if account["role"] == "SUPERADMIN":
            statements.append(f"INSERT INTO ADMINISTRADORES_SAED (ID_ADMINISTRADOR_SAED,ID_USUARIO,NIVEL,ESTADO) VALUES ({identifier},{identifier},'SUPERADMIN','ACTIVO');")
        if account["role"] in ("RESIDENTE", "RESIDENTE_CONVIVENCIA"):
            resident_type = "PROPIETARIO" if account["role"] == "RESIDENTE" else "CONVIVIENTE"
            statements.append(f"INSERT INTO RESIDENTES_UNIDAD (ID_RESIDENTE_UNIDAD,ID_UNIDAD,ID_PERSONA,TIPO_RESIDENTE,ESTADO) VALUES ({identifier},{account['unit']},{identifier},'{resident_type}','ACTIVO');")
            if resident_type == "PROPIETARIO":
                statements.append(f"INSERT INTO PROPIETARIOS_UNIDAD (ID_PROPIETARIO_UNIDAD,ID_UNIDAD,ID_PERSONA,ESTADO) VALUES ({identifier},{account['unit']},{identifier},'ACTIVO');")
    for table, column in (("ROLES", "ID_ROL"), ("ORGANIZACIONES", "ID_ORGANIZACION"), ("PROPIEDADES", "ID_PROPIEDAD"), ("UNIDADES", "ID_UNIDAD"), ("PERSONAS", "ID_PERSONA"), ("USUARIOS", "ID_USUARIO"), ("USUARIO_ASIGNACIONES", "ID_ASIGNACION"), ("ADMINISTRADORES_SAED", "ID_ADMINISTRADOR_SAED"), ("RESIDENTES_UNIDAD", "ID_RESIDENTE_UNIDAD"), ("PROPIETARIOS_UNIDAD", "ID_PROPIETARIO_UNIDAD"), ("MEMBRESIAS", "ID_MEMBRESIA")):
        statements.append(f'ALTER TABLE {table} MODIFY ("{column}" GENERATED BY DEFAULT ON NULL AS IDENTITY START WITH LIMIT VALUE);')
    statements.append("COMMIT;")
    return "\n".join(statements) + "\n"


def mixed_assignment_sql() -> str:
    # One existing resident also administers another property in the same tenant.
    # This intentionally exercises active-role changes without new credentials.
    return "INSERT INTO USUARIO_ASIGNACIONES (ID_ASIGNACION,ID_USUARIO,ID_ROL,ID_ORGANIZACION,ID_PROPIEDAD,ID_UNIDAD,ESTADO) VALUES (93001,91005,2,91001,91021,NULL,'ACTIVA');\nALTER TABLE USUARIO_ASIGNACIONES MODIFY (ID_ASIGNACION GENERATED BY DEFAULT ON NULL AS IDENTITY START WITH LIMIT VALUE);\nCOMMIT;\n"


def prepare() -> None:
    check_state_path()
    parts, sources, transformations = extract_sources()
    if WORK.exists():
        raise ValueError("Preparation directory already exists; preserve it and review before another run")
    if WORK.is_symlink() or WORK.parent.is_symlink():
        raise ValueError("Preparation directory must not traverse a link")
    ignored = subprocess.run(["git", "check-ignore", "--quiet", str(WORK / "private-state.json")], cwd=ROOT, capture_output=True, timeout=15)
    if ignored.returncode:
        raise ValueError("Generated credential directory is not Git-ignored")
    accounts = fixture_accounts()
    hashes = generate_hashes([account["password"] for account in accounts])
    parts["030-synthetic-fixture.sql"] = fixture_sql(accounts, hashes)
    parts["031-mixed-assignment.sql"] = mixed_assignment_sql()
    WORK.mkdir(parents=True, mode=0o700)
    if os.name == "nt":
        # Protect generated secrets before any database operation; failure aborts.
        proc = protect_windows_state()
        if proc.returncode:
            raise ValueError("Could not restrict generated state ACL; no secrets written")
    generated = {"state": STATE, "owner_password": secrets.token_hex(15), "runtime_password": secrets.token_hex(15), "security_password": secrets.token_hex(15), "marker": secrets.token_hex(24), "jwt_secret": base64.b64encode(secrets.token_bytes(64)).decode("ascii"), "accounts": accounts}
    write_private(WORK / "private-state.json", json.dumps(generated))
    manifest = {"format": 1, "state": STATE, "image": IMAGE_REF, "port": PORT, "pdb": PDB, "label_key": LABEL, "scope_label": SCOPE_LABEL, "scope": SCOPE, "files": {}, "sources": sources, "transformations": transformations, "status": "prepared-not-applied", "fixture": {"organizations": 2, "properties": 4, "units": 8, "users": len(accounts), "roles": list(ROLES), "mixed_role_user": 91005}, "limitations": ["Oracle production version undecided", "No application .env or inherited datasource allowed", "Installation, marker, compile/catalog and runtime RLS proof still pending"]}
    for name, content in parts.items():
        write_private(WORK / name, content)
        manifest["files"][name] = digest(content)
    write_private(WORK / "manifest.json", json.dumps(manifest, indent=2))
    if os.name == "nt" and protect_windows_state().returncode:
        raise ValueError("Could not verify generated credential ACL")
    print(f"PREPARED: {len(parts)} SQL artifacts, 2 synthetic organizations, {len(accounts)} users; no database connected")


def write_private(path: Path, content: str) -> None:
    if path.parent != WORK:
        raise ValueError("Private file must stay in the selected state directory")
    with path.open("x", encoding="utf-8", newline="\n") as target:
        target.write(content)
    if os.name != "nt":
        path.chmod(0o600)


def protect_windows_state(verify_only: bool = False) -> subprocess.CompletedProcess:
    check_state_path()
    arguments = ["powershell.exe", "-NoProfile", "-NonInteractive", "-File", str(ROOT / "scripts/verification/ProtectIsolatedState.ps1"), "-State", STATE]
    if verify_only:
        arguments.append("-VerifyOnly")
    return subprocess.run(arguments, capture_output=True, text=True, timeout=30)


def inspect_container(container: str, run: str, environment: dict | None = None) -> dict:
    if not re.fullmatch(r"[0-9a-f]{64}", container) or not re.fullmatch(r"[0-9a-f]{32}", run):
        raise ValueError("Explicit full container ID and execution identity required")
    template = '{"Id":{{json .Id}},"Name":{{json .Name}},"Image":{{json .Config.Image}},"Labels":{"saed.run":{{json (index .Config.Labels "saed.run")}},"saed.scope":{{json (index .Config.Labels "saed.scope")}}},"Running":{{json .State.Running}},"Ports":{{json .NetworkSettings.Ports}},"Mounts":[{{range $index, $mount := .Mounts}}{{if $index}},{{end}}{{json $mount.Type}}{{end}}]}'
    result = subprocess.run(["docker", "inspect", "--format", template, container], capture_output=True, text=True, timeout=15, env=environment)
    if result.returncode:
        raise ValueError("Container metadata unavailable")
    data = json.loads(result.stdout)
    if data["Id"] != container or data["Name"] != "/saed-isolated-" + run[:12]:
        raise ValueError("Unexpected container identity")
    if data.get("Labels", {}).get(LABEL) != run or data.get("Labels", {}).get(SCOPE_LABEL) != SCOPE:
        raise ValueError("Execution/scope label does not match")
    if data["Image"] != IMAGE_REF:
        raise ValueError("Container must use reviewed immutable digest")
    if data["Running"] is not True:
        raise ValueError("Container is not running")
    ports = data.get("Ports", {})
    if ports.get("1521/tcp") != [{"HostIp": "127.0.0.1", "HostPort": PORT}] or any(value for key, value in ports.items() if key != "1521/tcp"):
        raise ValueError("Only the isolated loopback endpoint is allowed")
    if data.get("Mounts"):
        raise ValueError("Disposable container must have no external mounts")
    return data


def verify_prepared() -> dict:
    check_state_path()
    if WORK.is_symlink() or WORK.parent.is_symlink():
        raise ValueError("Prepared directory must not be a link")
    if os.name == "nt" and protect_windows_state(verify_only=True).returncode:
        raise ValueError("Prepared credential ACL failed verification")
    manifest = json.loads((WORK / "manifest.json").read_text(encoding="utf-8"))
    if not isinstance(manifest, dict):
        raise ValueError("Prepared manifest must be an object")
    if type(manifest.get("format")) is not int or manifest.get("format") != 1 or manifest.get("state") != STATE or manifest.get("status") != "prepared-not-applied" or manifest.get("image") != IMAGE_REF or manifest.get("port") != PORT or manifest.get("pdb") != PDB or manifest.get("label_key") != LABEL or manifest.get("scope_label") != SCOPE_LABEL or manifest.get("scope") != SCOPE:
        raise ValueError("Unexpected manifest identity")
    expected_parts, expected_sources, _ = extract_sources()
    expected_names = set(expected_parts) | {"030-synthetic-fixture.sql", "031-mixed-assignment.sql"}
    files = manifest.get("files")
    if not isinstance(files, dict) or set(files) != expected_names:
        raise ValueError("Prepared artifact inventory is incomplete or unexpected")
    if manifest.get("sources") != expected_sources:
        raise ValueError("Reviewed source inventory/order/digest changed")
    for name, expected in files.items():
        if not re.fullmatch(r"[0-9]{3}-[A-Za-z0-9_.-]+\.sql", name):
            raise ValueError("Unsafe artifact name")
        if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
            raise ValueError("Invalid prepared artifact digest")
        path = WORK / name
        if path.is_symlink():
            raise ValueError("Prepared SQL must not be a link")
        content = path.read_text(encoding="utf-8")
        if digest(content) != expected:
            raise ValueError("Prepared SQL integrity changed")
        if name in expected_parts and content != expected_parts[name]:
            raise ValueError("Prepared SQL differs from reviewed extraction")
        if name == "031-mixed-assignment.sql" and content != mixed_assignment_sql():
            raise ValueError("Mixed-role fixture changed")
        if name == "030-synthetic-fixture.sql":
            hashes = re.findall(r"\$2[aby]\$12\$[./A-Za-z0-9]{53}", content)
            accounts = fixture_accounts()
            if len(hashes) != len(accounts) or content != fixture_sql(accounts, hashes):
                raise ValueError("Synthetic fixture scope or credential format changed")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["prepare", "check-prepared", "check-container"])
    parser.add_argument("--container-id")
    parser.add_argument("--run-id")
    parser.add_argument("--state", help="New explicit recovery state, for example recovery-01")
    args = parser.parse_args()
    if args.mode != "check-container":
        if not args.state:
            parser.error("--state is required for preparation and integrity checks")
        select_state(args.state)
    if args.mode == "prepare":
        prepare()
    elif args.mode == "check-prepared":
        manifest = verify_prepared()
        print(f"PREPARATION GUARDS PASS: {len(manifest['files'])} unchanged artifacts; no database connected")
    else:
        inspect_container(args.container_id or "", args.run_id or "")
        print("CONTAINER GUARDS PASS: no SQL executed; database readiness not asserted")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, subprocess.TimeoutExpired, OSError, KeyError, IndexError, json.JSONDecodeError) as exc:
        # Do not emit subprocess payloads, SQL, connection strings or credentials.
        print("BLOCKED: " + (str(exc) if isinstance(exc, ValueError) else type(exc).__name__), file=sys.stderr)
        sys.exit(1)
