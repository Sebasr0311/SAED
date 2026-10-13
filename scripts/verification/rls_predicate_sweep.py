"""Execute every RLS-protected table under each runtime profile context and report broken predicates.

Connects as the isolated runtime account (password only on stdin), sets the application's session context
(SET_BOOTSTRAP_CONTEXT then SET_CONTEXT) for each profile and runs SELECT COUNT(*) on every table that has an
enabled policy. Any ORA-28113/ORA-00904 means the policy function returns a predicate the table cannot satisfy,
so every application query on that table fails for that profile.

Usage: rls_predicate_sweep.py <state> <container-name>
"""
import json
import pathlib
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
STATE, CONTAINER = sys.argv[1], sys.argv[2]
state = json.loads((REPO / "local" / "isolated-oracle" / STATE / "private-state.json").read_text(encoding="utf-8"))

PROFILES = [
    ("SUPERADMIN", 1, None, "NULL"),
    ("ADMIN_ORGANIZACION", 91002, 91001, "NULL"),
    ("ADMIN_PROPIEDAD", 91003, 91001, 91001),
    ("PORTERO", 91004, 91001, 91001),
    ("RESIDENTE", 91005, 91001, 91001),
    ("RESIDENTE_CONVIVENCIA", 91006, 91001, 91001),
]


def run(script):
    return subprocess.run(["docker", "exec", "-i", CONTAINER, "sqlplus", "-s", "/nolog"], input=script,
                          capture_output=True, text=True, encoding="utf-8").stdout


tables = [l.split()[1] for l in run(
    "CONNECT / AS SYSDBA\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0\nALTER SESSION SET CONTAINER=XEPDB1;\n"
    "SELECT DISTINCT 'T ' || OBJECT_NAME FROM DBA_POLICIES WHERE OBJECT_OWNER='SAED_ISO_OWNER' AND ENABLE='YES' ORDER BY 1;\nEXIT\n"
).splitlines() if l.startswith("T ")]
print(f"{len(tables)} policy-protected tables")

broken = {}
superadmin_visible = []
for role, user, prop, unit in PROFILES:
    # SUPERADMIN runs without organization or property, exactly as the application sets its context.
    org = "NULL" if role == "SUPERADMIN" else 91001
    prop_sql = "NULL" if role in ("SUPERADMIN", "ADMIN_ORGANIZACION") else prop
    probes = "\n".join(
        f"  BEGIN EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM {t}' INTO n; "
        f"IF n > 0 THEN DBMS_OUTPUT.PUT_LINE('ROWS {t} ' || n); END IF; EXCEPTION WHEN OTHERS THEN "
        f"DBMS_OUTPUT.PUT_LINE('ERR {t} ' || SQLCODE); END;" for t in tables)
    script = (f'CONNECT SAED_ISO_RUNTIME/"{state["runtime_password"]}"@//localhost:1521/XEPDB1\n'
              "SET SERVEROUTPUT ON SIZE UNLIMITED FEEDBACK OFF\nALTER SESSION SET CURRENT_SCHEMA=SAED_ISO_OWNER;\n"
              f"BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT({user}); "
              f"PKG_SAED_SESSION.SET_CONTEXT({user}, {org}, {prop_sql}, '{role}'); END;\n/\n"
              f"DECLARE n NUMBER; BEGIN\n{probes}\nEND;\n/\nEXIT\n")
    out = run(script)
    errors = [l.split()[1:] for l in out.splitlines() if l.startswith("ERR ")]
    if "ORA-" in out and not errors:
        print(f"{role}: context setup failed: {[l for l in out.splitlines() if 'ORA-' in l][:2]}")
        continue
    for table, code in errors:
        broken.setdefault(table, {})[role] = code
    if role == "SUPERADMIN":
        superadmin_visible = [l.split()[1:] for l in out.splitlines() if l.startswith("ROWS ")]
    print(f"{role:<22} broken tables: {len(errors)}")

# DEC-02: SUPERADMIN sees platform data only; review this list for operational tables.
print("\nSUPERADMIN tables with visible rows:")
for table, count in superadmin_visible:
    print(f"  {table:<32} {count}")
print()
for table in sorted(broken):
    print(f"  {table:<32} " + ", ".join(f"{r}={c}" for r, c in sorted(broken[table].items())))
print(f"\nBROKEN TABLES: {len(broken)}")
