"""F06-03 single-debit verification against the isolated Oracle install and backend.

Seeds one fee of 100 for unit 91001 (SYSDBA inside the container), then drives the real HTTP API:
register partial payments as the resident, approve/reject as the property administrator, and read the
fee balance from Oracle after every step. Never prints passwords or tokens.

Usage: payment_debit_scenario.py <state> <container-name> <port>
"""
import concurrent.futures
import json
import pathlib
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

REPO = pathlib.Path(__file__).resolve().parents[2]
STATE, CONTAINER, PORT = sys.argv[1], sys.argv[2], sys.argv[3]
state = json.loads((REPO / "local" / "isolated-oracle" / STATE / "private-state.json").read_text(encoding="utf-8"))
BASE = f"http://127.0.0.1:{PORT}"
RUN = uuid.uuid4().hex[:8]
FEE_ID = 95100 + int(RUN, 16) % 800  # a fresh fee per run
failures = []


def sql(statements):
    script = "CONNECT / AS SYSDBA\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 200\nALTER SESSION SET CONTAINER=XEPDB1;\n" + statements + "\nEXIT\n"
    out = subprocess.run(["docker", "exec", "-i", CONTAINER, "sqlplus", "-s", "/nolog"], input=script,
                         capture_output=True, text=True, encoding="utf-8")
    return out.stdout


def fee_state():
    out = sql(f"SELECT 'FEE ' || SALDO_PENDIENTE || ' ' || ESTADO FROM SAED_ISO_OWNER.CUOTAS WHERE ID_CUOTA = {FEE_ID};")
    line = next(l for l in out.splitlines() if l.startswith("FEE "))
    _, saldo, estado = line.split()
    return float(saldo), estado


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            return res.status, json.loads(res.read() or b"null")
    except urllib.error.HTTPError as err:
        try:
            return err.code, json.loads(err.read() or b"null")
        except ValueError:
            return err.code, None


def login(username):
    acc = next(a for a in state["accounts"] if a["username"] == username)
    status, body = call("POST", "/api/v1/auth/login", {"username": acc["email"], "password": acc["password"]})
    assert status == 200, (username, status)
    return next(v for k, v in body.items() if "token" in k.lower() and isinstance(v, str))


def payment_id(body):
    def find(node):
        if isinstance(node, dict):
            for key in ("idPago", "id_pago", "id"):
                if isinstance(node.get(key), int):
                    return node[key]
            for value in node.values():
                hit = find(value)
                if hit:
                    return hit
        return None
    return find(body)


def expect(label, condition):
    print(f"  {'PASS' if condition else 'FAIL'} {label}")
    if not condition:
        failures.append(label)


def register(token, amount, reference):
    status, body = call("POST", "/api/v1/pagos", {"idCuota": FEE_ID, "valorPagado": amount, "metodoPago": "TRANSFERENCIA",
                                                   "referencia": reference, "idempotencyKey": str(uuid.uuid4())}, token)
    pid = payment_id(body)
    if pid is None and status in (200, 201):
        # POST /pagos returns only {success, message}; resolve the id by the unique reference.
        out = sql(f"SELECT 'PID ' || MAX(ID_PAGO) FROM SAED_ISO_OWNER.PAGOS WHERE REFERENCIA_COMPROBANTE = '{reference}';")
        line = next((l for l in out.splitlines() if l.startswith("PID ")), "PID ")
        pid = int(line.split()[1]) if len(line.split()) > 1 else None
    return status, pid


print("Seeding fee 100 for unit 91001")
seed_output = sql(f"""
ALTER SESSION SET CURRENT_SCHEMA=SAED_ISO_OWNER;
INSERT INTO CONCEPTOS_COBRO (ID_CONCEPTO, ID_ORGANIZACION, ID_PROPIEDAD, CODIGO, NOMBRE, TIPO, ESTADO)
VALUES ({FEE_ID}, 91001, 91001, 'ADM-ISO-{RUN}', 'Administracion sintetica {RUN}', 'ADMINISTRACION', 'ACTIVO');
INSERT INTO CUOTAS (ID_CUOTA, ID_UNIDAD, ID_CONCEPTO, PERIODO, VALOR_BASE, SALDO_PENDIENTE, FECHA_VENCIMIENTO, ESTADO)
VALUES ({FEE_ID}, 91001, {FEE_ID}, TO_CHAR(SYSDATE, 'YYYY-MM'), 100, 100, TRUNC(SYSDATE) + 15, 'PENDIENTE');
COMMIT;
""")
if "ORA-" in seed_output:
    print("  seeding failed:", [l for l in seed_output.splitlines() if "ORA-" in l])
    sys.exit(1)
print("  seeded fee", FEE_ID)
expect(f"fee starts at 100 PENDIENTE {fee_state()}", fee_state() == (100.0, "PENDIENTE"))

resident = login("iso_0_residente")
admin = login("iso_0_admin_propiedad")

print("1. resident registers 30")
status, p1 = register(resident, 30, "ISO-P1-" + RUN)
expect(f"registration accepted (status {status}, id {p1})", status in (200, 201) and p1)
expect(f"pending payment does not change the balance {fee_state()}", fee_state()[0] == 100.0)

print("2. admin approves 30")
status, _ = call("POST", f"/api/v1/pagos/{p1}/aprobar", {}, admin)
expect(f"approval accepted (status {status})", status in (200, 201, 204))
expect(f"balance debited exactly once to 70 {fee_state()}", fee_state() == (70.0, "PAGADA_PARCIAL"))

print("3. repeated approval of the same payment")
status, _ = call("POST", f"/api/v1/pagos/{p1}/aprobar", {}, admin)
expect(f"repeated approval rejected (status {status})", status >= 400)
expect(f"balance still 70 {fee_state()}", fee_state()[0] == 70.0)

print("4. five concurrent approvals of a 20 payment")
status, p2 = register(resident, 20, "ISO-P2-" + RUN)
expect(f"second registration accepted (status {status})", status in (200, 201) and p2)
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
    results = list(pool.map(lambda _: call("POST", f"/api/v1/pagos/{p2}/aprobar", {}, admin)[0], range(5)))
expect(f"exactly one concurrent approval succeeds {sorted(results)}", sum(1 for s in results if s < 300) == 1)
expect(f"losers get 409 (conflict) or 503 (pool busy, retryable), never 500 {sorted(results)}", all(s < 300 or s in (409, 503) for s in results))
expect(f"balance 50 after concurrent approvals {fee_state()}", fee_state()[0] == 50.0)

print("5. rejected payment")
status, p3 = register(resident, 10, "ISO-P3-" + RUN)
status_r, _ = call("POST", f"/api/v1/pagos/{p3}/rechazar", {"motivoRechazo": "Comprobante ilegible"}, admin)
expect(f"rejection accepted (status {status_r})", status_r in (200, 201, 204))
expect(f"rejected payment leaves balance at 50 {fee_state()}", fee_state()[0] == 50.0)

print("6. pay the remaining 50")
status, p4 = register(resident, 50, "ISO-P4-" + RUN)
status_a, _ = call("POST", f"/api/v1/pagos/{p4}/aprobar", {}, admin)
expect(f"final approval accepted (status {status_a})", status_a in (200, 201, 204))
expect(f"fee fully paid {fee_state()}", fee_state() == (0.0, "PAGADA"))

print(f"\nFAILURES: {len(failures)}")
for item in failures:
    print("  -", item)
