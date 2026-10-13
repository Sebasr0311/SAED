"""Prove that a resident context does not read or write another property the same user administers.

Fixture: user 91005 is RESIDENTE of unit 91001 in property 91001 and ADMIN_PROPIEDAD of property 91021 (assignment
93001). Under the RESIDENTE context of property 91001 the runtime account must see and change NOTHING of property 91021
(PKG_SAED_SECURITY_RLS used to scope residents through the union of all the user's assignments). Positive controls
make sure the own property is still visible and that the ADMIN_PROPIEDAD context of 91021 still sees 91021.

Connects as the isolated runtime account (password only on stdin, never printed). Exit code 1 when a check fails.

Usage: rls_context_scope_probe.py <state> <container-name>
"""
import json
import pathlib
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
STATE, CONTAINER = sys.argv[1], sys.argv[2]
state = json.loads((REPO / "local" / "isolated-oracle" / STATE / "private-state.json").read_text(encoding="utf-8"))

USER, ORG, OWN_PROPERTY, OTHER_PROPERTY = 91005, 91001, 91001, 91021

# (label, role, context property, probe SQL, expected value)
CHECKS = [
    ("RESIDENTE sees own property", "RESIDENTE", OWN_PROPERTY,
     f"SELECT COUNT(*) FROM PROPIEDADES WHERE id_propiedad = {OWN_PROPERTY}", 1),
    ("RESIDENTE sees no row of the other property (PROPIEDADES)", "RESIDENTE", OWN_PROPERTY,
     f"SELECT COUNT(*) FROM PROPIEDADES WHERE id_propiedad = {OTHER_PROPERTY}", 0),
    ("RESIDENTE sees no unit of the other property (UNIDADES)", "RESIDENTE", OWN_PROPERTY,
     f"SELECT COUNT(*) FROM UNIDADES WHERE id_propiedad = {OTHER_PROPERTY}", 0),
    ("RESIDENTE cannot update the other property", "RESIDENTE", OWN_PROPERTY,
     f"UPDATE PROPIEDADES SET nombre = nombre WHERE id_propiedad = {OTHER_PROPERTY}", 0),
    ("ADMIN_PROPIEDAD still sees the property it administers", "ADMIN_PROPIEDAD", OTHER_PROPERTY,
     f"SELECT COUNT(*) FROM PROPIEDADES WHERE id_propiedad = {OTHER_PROPERTY}", 1),
    ("ADMIN_PROPIEDAD does not see the resident property", "ADMIN_PROPIEDAD", OTHER_PROPERTY,
     f"SELECT COUNT(*) FROM PROPIEDADES WHERE id_propiedad = {OWN_PROPERTY}", 0),
]


def run(script):
    return subprocess.run(["docker", "exec", "-i", CONTAINER, "sqlplus", "-s", "/nolog"], input=script,
                          capture_output=True, text=True, encoding="utf-8").stdout


failed = 0
for label, role, prop, sql, expected in CHECKS:
    if sql.startswith("UPDATE"):
        body = f"  EXECUTE IMMEDIATE q'[{sql}]'; n := SQL%ROWCOUNT; ROLLBACK;\n"
    else:
        body = f"  EXECUTE IMMEDIATE q'[{sql}]' INTO n;\n"
    script = (f'CONNECT SAED_ISO_RUNTIME/"{state["runtime_password"]}"@//localhost:1521/XEPDB1\n'
              "SET SERVEROUTPUT ON SIZE UNLIMITED FEEDBACK OFF\nALTER SESSION SET CURRENT_SCHEMA=SAED_ISO_OWNER;\n"
              f"BEGIN PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT({USER}); "
              f"PKG_SAED_SESSION.SET_CONTEXT({USER}, {ORG}, {prop}, '{role}'); END;\n/\n"
              f"DECLARE n NUMBER; BEGIN\n{body}  DBMS_OUTPUT.PUT_LINE('RESULT ' || n);\n"
              "EXCEPTION WHEN OTHERS THEN DBMS_OUTPUT.PUT_LINE('ERR ' || SQLCODE); END;\n/\nEXIT\n")
    lines = run(script).splitlines()
    result = next((l.split()[1] for l in lines if l.startswith(("RESULT ", "ERR "))), "NO_OUTPUT")
    ok = result == str(expected)
    failed += 0 if ok else 1
    print(f"{'PASS' if ok else 'FAIL'}  {label}: got {result}, expected {expected}")

print(f"\nCONTEXT SCOPE FAILURES: {failed}")
sys.exit(1 if failed else 0)
