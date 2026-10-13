"""Execute every RLS-protected table under each runtime profile context and report broken predicates.

Connects as the isolated runtime account (password only on stdin), sets the application's session context
(SET_BOOTSTRAP_CONTEXT then SET_CONTEXT) for each profile and runs SELECT COUNT(*) on every table that has an
enabled policy. Any ORA-28113/ORA-00904 means the policy function returns a predicate the table cannot satisfy,
so every application query on that table fails for that profile.

It also runs a coverage check: tables in the owner schema that look tenant-scoped (ID_ORGANIZACION, ID_PROPIEDAD or
ID_UNIDAD column, or a foreign key to a policy-protected table) but have NO enabled policy are printed as
"UNPROTECTED TABLES: n" and make the script exit 1.

Usage: rls_predicate_sweep.py <state> <container-name>
"""
import json
import pathlib
import subprocess
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]

PROFILES = [
    ("SUPERADMIN", 1, None, "NULL"),
    ("ADMIN_ORGANIZACION", 91002, 91001, "NULL"),
    ("ADMIN_PROPIEDAD", 91003, 91001, 91001),
    ("PORTERO", 91004, 91001, 91001),
    ("RESIDENTE", 91005, 91001, 91001),
    ("RESIDENTE_CONVIVENCIA", 91006, 91001, 91001),
]

# Global catalogs that are intentionally shared by every tenant and therefore have no tenant policy.
# Add a table here only with a reason; anything else tenant-looking and unprotected fails the sweep.
GLOBAL_CATALOGS = {
    "ROLES": "platform role catalog",
    "PLANES": "SaaS plan catalog",
    "MODULOS": "SaaS module catalog",
    "PLAN_MODULOS": "plan-to-module catalog",
    "EVENTOS_SISTEMA": "system event catalog",
}
GLOBAL_CATALOG_PREFIXES = ("TIPOS_",)  # lookup tables such as TIPOS_DOCUMENTO
TENANT_COLUMNS = ("ID_ORGANIZACION", "ID_PROPIEDAD", "ID_UNIDAD")
OWNER = "SAED_ISO_OWNER"


def unprotected_tables(owner_tables, tenant_columns, foreign_keys, protected):
    """Tenant-looking tables without an enabled policy.

    owner_tables: all table names; tenant_columns: {table: set(columns)}; foreign_keys: [(child, parent)];
    protected: tables with an enabled policy. Pure function, no database access."""
    protected = set(protected)
    scoped = {t for t, cols in tenant_columns.items() if set(cols) & set(TENANT_COLUMNS)}
    scoped |= {child for child, parent in foreign_keys if parent in protected}
    return [table for table in sorted(set(owner_tables) & scoped)
            if table not in protected and table not in GLOBAL_CATALOGS
            and not table.startswith(GLOBAL_CATALOG_PREFIXES)]


def run(container, script):
    return subprocess.run(["docker", "exec", "-i", container, "sqlplus", "-s", "/nolog"], input=script,
                          capture_output=True, text=True, encoding="utf-8").stdout


def dictionary_query(container, sql):
    return run(container, "CONNECT / AS SYSDBA\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 400\n"
                          f"ALTER SESSION SET CONTAINER=XEPDB1;\n{sql}\nEXIT\n").splitlines()


def coverage_check(container, protected):
    tables = [l.split()[1] for l in dictionary_query(
        container, f"SELECT 'A ' || TABLE_NAME FROM DBA_TABLES WHERE OWNER='{OWNER}' AND TEMPORARY='N';")
        if l.startswith("A ")]
    columns = {}
    for l in dictionary_query(
            container, f"SELECT 'C ' || TABLE_NAME || ' ' || COLUMN_NAME FROM DBA_TAB_COLS WHERE OWNER='{OWNER}' "
                       "AND COLUMN_NAME IN ('ID_ORGANIZACION','ID_PROPIEDAD','ID_UNIDAD');"):
        if l.startswith("C "):
            _, table, column = l.split()
            columns.setdefault(table, set()).add(column)
    foreign_keys = []
    for l in dictionary_query(
            container, "SELECT 'F ' || c.TABLE_NAME || ' ' || p.TABLE_NAME FROM DBA_CONSTRAINTS c "
                       "JOIN DBA_CONSTRAINTS p ON p.OWNER=c.R_OWNER AND p.CONSTRAINT_NAME=c.R_CONSTRAINT_NAME "
                       f"WHERE c.OWNER='{OWNER}' AND c.CONSTRAINT_TYPE='R';"):
        if l.startswith("F "):
            _, child, parent = l.split()
            foreign_keys.append((child, parent))
    if not tables:
        print("coverage check: could not read the data dictionary")
        return None
    return unprotected_tables(tables, columns, foreign_keys, protected)


def main():
    state_name, container = sys.argv[1], sys.argv[2]
    state = json.loads((REPO / "local" / "isolated-oracle" / state_name / "private-state.json").read_text(encoding="utf-8"))
    tables = [l.split()[1] for l in dictionary_query(
        container, f"SELECT DISTINCT 'T ' || OBJECT_NAME FROM DBA_POLICIES WHERE OBJECT_OWNER='{OWNER}' "
                   "AND ENABLE='YES' ORDER BY 1;") if l.startswith("T ")]
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
        out = run(container, script)
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

    unprotected = coverage_check(container, tables)
    if unprotected is None:
        return 1
    for table in unprotected:
        print(f"  {table}")
    print(f"UNPROTECTED TABLES: {len(unprotected)}")
    return 1 if unprotected else 0


if __name__ == "__main__":
    sys.exit(main())
