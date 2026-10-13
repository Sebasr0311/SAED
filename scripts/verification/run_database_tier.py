"""Run the DATABASE test tier against the disposable isolated Oracle only.

Usage: run_database_tier.py <state> [--skip-seed] [--isolate-classes] [--tests <surefire -Dtest filter>]

Safety: refuses to start unless DB_URL is exactly the isolated loopback endpoint and the Docker
container carries the saed.scope=isolated-fixture label with the 127.0.0.1:11521 binding.
Credentials are read from the state's private-state.json and reach Maven only through the child
process environment. Nothing secret is printed or logged. The user's local Oracle (localhost:1521)
is never contacted: DB_URL is always set explicitly.
"""
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

REPO = pathlib.Path(__file__).resolve().parents[2]
BACKEND = REPO / "backend"
SEED = REPO / "database" / "seeds" / "demo" / "V5.99__demo_seeds.sql"
SEEDER = BACKEND / "src" / "main" / "java" / "com" / "saed" / "backend" / "config" / "DatabaseSeeder.java"
ENTITLEMENTS = REPO / "database" / "migrations" / "V5.13__seed_modulos_plan_modulos.sql"
# Suite expectations the catalogs do not carry: org 1 (the demo organization every module test uses) must hold an
# active ENTERPRISE membership (ASAMBLEAS is Enterprise-only in the V5.13 matrix).
ORG1_ENTERPRISE = """
DECLARE n NUMBER;
BEGIN
  SELECT COUNT(*) INTO n FROM MEMBRESIAS WHERE ID_ORGANIZACION = 1 AND ESTADO = 'ACTIVA';
  IF n = 0 THEN
    INSERT INTO MEMBRESIAS (ID_ORGANIZACION, ID_PLAN, ESTADO, FECHA_INICIO, FECHA_FIN, ES_PRUEBA)
    VALUES (1, 3, 'ACTIVA', TRUNC(SYSDATE) - 1, ADD_MONTHS(TRUNC(SYSDATE), 12), 'N');
  ELSE
    UPDATE MEMBRESIAS SET ID_PLAN = 3, FECHA_FIN = ADD_MONTHS(TRUNC(SYSDATE), 12) WHERE ID_ORGANIZACION = 1 AND ESTADO = 'ACTIVA';
  END IF;
  COMMIT;
END;
/
"""
JAVA_HOME = pathlib.Path.home() / ".jdks" / "ms-17.0.19"
EXPECTED_URL = "jdbc:oracle:thin:@//127.0.0.1:11521/XEPDB1"
SCOPE_LABEL, SCOPE = "saed.scope", "isolated-fixture"
OWNER, RUNTIME = "SAED_ISO_OWNER", "SAED_ISO_RUNTIME"
PORT = "11521"


def fail(message: str) -> None:
    print("REFUSED:", message)
    sys.exit(2)


def docker_env() -> dict:
    env = dict(os.environ)
    if os.name == "nt":
        env.setdefault("DOCKER_HOST", "npipe:////./pipe/dockerDesktopLinuxEngine")
    return env


def verify_container(state: dict) -> str:
    manifest = json.loads((REPO / "local" / "isolated-oracle" / STATE / "execution-state.json").read_text(encoding="utf-8"))
    name = "saed-isolated-" + manifest["run"][:12]
    out = subprocess.run(["docker", "inspect", name, "--format", "{{json .Config.Labels}}|{{json .NetworkSettings.Ports}}|{{.State.Running}}"],
                         capture_output=True, text=True, env=docker_env(), timeout=30)
    if out.returncode:
        fail("container not found")
    labels, ports, running = out.stdout.strip().split("|")
    labels, ports = json.loads(labels), json.loads(ports)
    if labels.get(SCOPE_LABEL) != SCOPE:
        fail("container lacks the isolated-fixture scope label")
    if ports != {"1521/tcp": [{"HostIp": "127.0.0.1", "HostPort": PORT}]}:
        fail("container port binding is not exactly 127.0.0.1:" + PORT)
    if running != "true":
        fail("container is not running")
    return name


def event_catalog_sql() -> str:
    """EVENTOS_SISTEMA inserts of DatabaseSeeder, which skips them once the base roles exist (alreadySeeded)."""
    source = SEEDER.read_text(encoding="utf-8")
    statements = []
    for call in re.findall(r"runSqlSafe\((.*?)\);\s*\n", source, re.S):
        literals = re.findall(r'"((?:[^"\\]|\\.)*)"', call)
        sql = "".join(literals).replace('\\"', '"')
        if sql.startswith("INSERT INTO EVENTOS_SISTEMA"):
            statements.append(sql + ";")
    return chr(10).join(statements)


def seed_demo(container: str) -> None:
    # Demo dataset, then the canonical entitlement matrix (tests flip PLAN_MODULOS rows and may not restore them).
    body = chr(10).join([SEED.read_text(encoding="utf-8"), ENTITLEMENTS.read_text(encoding="utf-8"), event_catalog_sql(), "COMMIT;", ORG1_ENTERPRISE])
    script = ("CONNECT / AS SYSDBA\nWHENEVER SQLERROR CONTINUE\nSET DEFINE OFF HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 300\n"
              "ALTER SESSION SET CONTAINER=XEPDB1;\nALTER SESSION SET CURRENT_SCHEMA=" + OWNER + ";\n" + body + "\nEXIT\n")
    out = subprocess.run(["docker", "exec", "-i", "--user", "oracle", "--env", "NLS_LANG=AMERICAN_AMERICA.AL32UTF8", container, "sqlplus", "-L", "-s", "/nolog"],
                         input=script, capture_output=True, text=True, encoding="utf-8", env=docker_env(), timeout=300)
    errors = [line for line in out.stdout.splitlines() if line.startswith(("ORA-", "SP2-", "ERROR"))]
    print(f"demo seed applied rc={out.returncode} oracle_error_lines={len(errors)}")
    for line in sorted(set(errors))[:10]:
        print("  ", line[:160])


def tally(reports: pathlib.Path) -> dict:
    classes = {}
    for xml in sorted(reports.glob("TEST-*.xml")):
        root = ET.parse(xml).getroot()
        name = root.get("name")
        row = classes.setdefault(name, {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "problems": []})
        for key in ("tests", "failures", "errors", "skipped"):
            row[key] += int(root.get(key, 0))
        for case in root.iter("testcase"):
            for kind in ("failure", "error"):
                node = case.find(kind)
                if node is not None:
                    first = (node.get("message") or (node.text or "")).strip().splitlines()
                    row["problems"].append({"test": case.get("name"), "kind": kind, "type": node.get("type"),
                                            "message": (first[0] if first else "")[:300]})
    return classes


def main() -> None:
    global STATE
    STATE = sys.argv[1]
    skip_seed = "--skip-seed" in sys.argv
    test_filter = sys.argv[sys.argv.index("--tests") + 1] if "--tests" in sys.argv else None
    private = json.loads((REPO / "local" / "isolated-oracle" / STATE / "private-state.json").read_text(encoding="utf-8"))
    db_url = EXPECTED_URL
    if db_url != EXPECTED_URL:
        fail("DB_URL must be exactly " + EXPECTED_URL)
    container = verify_container(private)
    if not skip_seed:
        seed_demo(container)

    env = dict(os.environ)
    for key in list(env):
        if key.startswith(("DB_", "SPRING_DATASOURCE", "JWT_")):
            del env[key]
    env.update({
        "JAVA_HOME": str(JAVA_HOME),
        "PATH": str(JAVA_HOME / "bin") + os.pathsep + env.get("PATH", ""),
        "SPRING_PROFILES_ACTIVE": "dev",  # classes without @ActiveProfiles would otherwise resolve the prod guard
        "DB_URL": db_url,
        "DB_USERNAME": RUNTIME,
        "DB_PASSWORD": private["runtime_password"],
        # An env var named ..._HIKARI_CONNECTION_INIT_SQL would relax-bind to hikari.connection.init.sql, making
        # the binder call HikariDataSource.getConnection() and seal the pool; JSON keeps the hyphenated key intact.
        "SPRING_APPLICATION_JSON": json.dumps({"spring": {"datasource": {"hikari": {"connection-init-sql": "ALTER SESSION SET CURRENT_SCHEMA=" + OWNER}}}}),
    })
    reports = BACKEND / "target" / "surefire-reports"
    shutil.rmtree(reports, ignore_errors=True)
    mvnw = str(BACKEND / ("mvnw.cmd" if os.name == "nt" else "mvnw"))
    args = [mvnw, "-o", "test", "-Dsaed.test.tier=database", "-DforkCount=1", "-DfailIfNoTests=false", "-Dsurefire.failIfNoSpecifiedTests=false"]
    if "--isolate-classes" in sys.argv:  # one JVM per class: stops ThreadLocal/pool/context leaks between classes
        args.append("-DreuseForks=false")
    if test_filter:
        args.append("-Dtest=" + test_filter)
    state_dir = REPO / "local" / "isolated-oracle" / STATE
    log_path = state_dir / "database-tier-maven.log"
    with log_path.open("w", encoding="utf-8") as log:
        code = subprocess.run(args, cwd=str(BACKEND), env=env, stdout=log, stderr=subprocess.STDOUT).returncode

    classes = tally(reports)
    totals = {k: sum(c[k] for c in classes.values()) for k in ("tests", "failures", "errors", "skipped")}
    totals["classes"] = len(classes)
    totals["classes_failing"] = sum(1 for c in classes.values() if c["failures"] or c["errors"])
    result = {"state": STATE, "tier": "database", "maven_exit": code, "totals": totals, "classes": classes}
    (state_dir / "database-tier.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
    lines = [f"database tier [{STATE}] maven_exit={code} " + " ".join(f"{k}={v}" for k, v in totals.items())]
    for name, c in classes.items():
        if c["failures"] or c["errors"]:
            lines.append(f"FAIL {name} tests={c['tests']} failures={c['failures']} errors={c['errors']}")
    summary = "\n".join(lines)
    (state_dir / "database-tier-summary.txt").write_text(summary + "\n", encoding="utf-8")
    print(summary)


STATE = ""
if __name__ == "__main__":
    main()
