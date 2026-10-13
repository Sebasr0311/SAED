"""Household and gatehouse verification against the isolated Oracle install and backend.

Drives the real HTTP API with the synthetic fixture accounts (property 91001) and reads Oracle (SYSDBA inside the
container) only to prove what was stored:

  a. the property administrator registers a resident -> 201 and a valid RESIDENTES_UNIDAD.TIPO_RESIDENTE is stored;
  b. the resident registers a cohabitant -> 201 within quota, the cohabitant logs in and uses a resident endpoint;
  c. the resident pre-registers a visitor -> 201; registering the same person again reuses the same visitor;
  d. the porter sees that visit and registers a walk-in visitor -> 201;
  e. another resident of the same property cannot see the first resident's visit.

Every run uses unique values. Never prints passwords, tokens or response bodies.

Usage: household_gatehouse_scenario.py <state> <container-name> <port>
"""
import json
import pathlib
import secrets
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
OWNER = "SAED_ISO_OWNER"
PROPERTY_UNIT_RESIDENT = 91001  # unit of iso_0_residente
PROPERTY_UNIT_OTHER = 91011  # second unit of the same property, for the registered resident
VALID_RESIDENCE_TYPES = ("PROPIETARIO", "ARRENDATARIO")
failures = []


def sql(statements):
    script = ("CONNECT / AS SYSDBA\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 200\n"
              "ALTER SESSION SET CONTAINER=XEPDB1;\n" + statements + "\nEXIT\n")
    out = subprocess.run(["docker", "exec", "-i", CONTAINER, "sqlplus", "-s", "/nolog"], input=script,
                         capture_output=True, text=True, encoding="utf-8")
    return out.stdout


def scalar(tag, query):
    """Run a query returning one value and return it (or None) from the line prefixed with the tag."""
    out = sql(f"SELECT '{tag} ' || ({query}) FROM DUAL;")
    for line in out.splitlines():
        if line.startswith(tag + " "):
            return line[len(tag) + 1:].strip()
    return None


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            raw = res.read()
            return res.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as err:
        try:
            return err.code, json.loads(err.read() or b"null")
        except ValueError:
            return err.code, None


def login_with(email, password):
    status, body = call("POST", "/api/v1/auth/login", {"username": email, "password": password})
    if status != 200 or not isinstance(body, dict):
        return None
    return next((v for k, v in body.items() if "token" in k.lower() and isinstance(v, str)), None)


def login_fixture(username):
    acc = next(a for a in state["accounts"] if a["username"] == username)
    token = login_with(acc["email"], acc["password"])
    assert token, f"fixture login failed for {username}"
    return token


def find_key(node, keys):
    if isinstance(node, dict):
        for key in keys:
            if isinstance(node.get(key), int) and not isinstance(node.get(key), bool):
                return node[key]
        for value in node.values():
            hit = find_key(value, keys)
            if hit is not None:
                return hit
    elif isinstance(node, list):
        for value in node:
            hit = find_key(value, keys)
            if hit is not None:
                return hit
    return None


def contains_visit(node, visit_id):
    if isinstance(node, dict):
        if node.get("idVisita") == visit_id:
            return True
        return any(contains_visit(v, visit_id) for v in node.values())
    if isinstance(node, list):
        return any(contains_visit(v, visit_id) for v in node)
    return False


def expect(label, condition):
    print(f"  {'PASS' if condition else 'FAIL'} {label}")
    if not condition:
        failures.append(label)


def new_account(tag):
    return {"username": f"hg-{RUN}-{tag}", "email": f"hg-{RUN}-{tag}@example.invalid",
            "password": "Aa1!" + secrets.token_hex(12), "document": f"HG{RUN}{tag.upper()}"}


def user_payload(account, role, unit=None):
    payload = {"username": account["username"], "email": account["email"], "password": account["password"], "rol": role,
               "numeroDocumento": account["document"], "primerNombre": "Synthetic", "primerApellido": f"Household{RUN}"}
    if unit is not None:
        payload["idUnidad"] = unit
    return payload


def residence_type(document):
    return scalar("RT", f"SELECT MAX(ru.TIPO_RESIDENTE) FROM {OWNER}.RESIDENTES_UNIDAD ru JOIN {OWNER}.PERSONAS p "
                        f"ON p.ID_PERSONA = ru.ID_PERSONA WHERE p.NUMERO_DOCUMENTO = '{document}'")


admin = login_fixture("iso_0_admin_propiedad")
resident = login_fixture("iso_0_residente")
porter = login_fixture("iso_0_portero")

print("a. property administrator registers a resident")
registered = new_account("res")
status, _ = call("POST", "/api/v1/usuarios", user_payload(registered, "RESIDENTE", PROPERTY_UNIT_OTHER), admin)
expect(f"resident registration answers 201 (status {status})", status == 201)
stored = residence_type(registered["document"])
expect(f"stored residence type is a domain relation ({stored})", stored in VALID_RESIDENCE_TYPES)
status, _ = call("POST", "/api/v1/usuarios", {**user_payload(new_account("bad"), "RESIDENTE", PROPERTY_UNIT_OTHER),
                                               "tipoResidente": "TITULAR"}, admin)
expect(f"relation outside the domain is rejected (status {status})", status == 400)

print("b. resident registers a cohabitant")
cohabitant = new_account("cov")
status, _ = call("POST", "/api/v1/usuarios", user_payload(cohabitant, "RESIDENTE_CONVIVENCIA"), resident)
expect(f"cohabitant registration answers 201 (status {status})", status == 201)
expect(f"stored cohabitant relation is CONVIVIENTE ({residence_type(cohabitant['document'])})",
       residence_type(cohabitant["document"]) == "CONVIVIENTE")
cohabitant_token = login_with(cohabitant["email"], cohabitant["password"])
expect("cohabitant can log in", bool(cohabitant_token))
if cohabitant_token:
    status, _ = call("GET", f"/api/v1/porteria/unidades/{PROPERTY_UNIT_RESIDENT}/visitas", None, cohabitant_token)
    expect(f"cohabitant resident endpoint answers 200 (status {status})", status == 200)

print("c. resident pre-registers a visitor")
visitor_document = f"VIS{RUN}"
visit_body = {"numeroDocumento": visitor_document, "nombreVisitante": "Visitor", "apellidos": f"Synthetic{RUN}",
              "motivo": "Scenario visit"}
status, first = call("POST", "/api/v1/porteria/visitas", visit_body, resident)
expect(f"visitor pre-registration answers 201 (status {status})", status == 201)
visit_id = find_key(first, ("idVisita",))
expect("pre-registration returns the visit id", visit_id is not None)
status, second = call("POST", "/api/v1/porteria/visitas", visit_body, resident)
expect(f"second registration of the same person answers 201 (status {status})", status == 201)
visitor_rows = scalar("VR", f"SELECT COUNT(*) FROM {OWNER}.VISITANTES v JOIN {OWNER}.PERSONAS p ON p.ID_PERSONA = v.ID_PERSONA "
                            f"WHERE p.NUMERO_DOCUMENTO = '{visitor_document}'")
distinct_visitors = scalar("VD", f"SELECT COUNT(DISTINCT vi.ID_VISITANTE) FROM {OWNER}.VISITAS vi JOIN {OWNER}.VISITANTES v "
                                 f"ON v.ID_VISITANTE = vi.ID_VISITANTE JOIN {OWNER}.PERSONAS p ON p.ID_PERSONA = v.ID_PERSONA "
                                 f"WHERE p.NUMERO_DOCUMENTO = '{visitor_document}'")
expect(f"the same person is one visitor with two visits (visitors {visitor_rows}, distinct on visits {distinct_visitors})",
       visitor_rows == "1" and distinct_visitors == "1")

print("d. porter sees the visit and registers a walk-in")
status, summary = call("GET", "/api/v1/porteria/visitas-resumen", None, porter)
expect(f"porter lists visits (status {status})", status == 200)
expect("porter sees the pre-registered visit", visit_id is not None and contains_visit(summary, visit_id))
walk_in = {"idUnidad": PROPERTY_UNIT_RESIDENT, "numeroDocumento": f"WLK{RUN}", "nombreVisitante": "Walkin",
           "apellidos": f"Synthetic{RUN}", "motivo": "Scenario walk-in", "metodoIngreso": "LLAMADA_PORTERIA"}
status, _ = call("POST", "/api/v1/porteria/visitas", walk_in, porter)
expect(f"porter walk-in registration answers 201 (status {status})", status == 201)

print("e. another resident of the same property cannot see the visit")
other_token = login_with(registered["email"], registered["password"])
expect("registered resident can log in", bool(other_token))
if other_token and visit_id is not None:
    status, _ = call("GET", f"/api/v1/porteria/visitas/{visit_id}", None, other_token)
    expect(f"direct visit read is denied (status {status})", status in (403, 404))
    status, _ = call("GET", f"/api/v1/porteria/unidades/{PROPERTY_UNIT_RESIDENT}/visitas", None, other_token)
    expect(f"other unit's visit list is denied (status {status})", status in (403, 404))

print("f. resident revokes the cohabitant (keeps the unit within its quota, so the scenario is repeatable)")
status, residents = call("GET", f"/api/v1/units/{PROPERTY_UNIT_RESIDENT}/residents", None, resident)
expect(f"resident lists its unit's residents (status {status})", status == 200)
entries = residents if isinstance(residents, list) else (residents or {}).get("data") or []
cohabitant_entry = next((e for e in entries if isinstance(e, dict)
                         and cohabitant["document"] in json.dumps(e.get("persona") or {})), None)
expect("the new cohabitant appears in the unit's residents", cohabitant_entry is not None)
if cohabitant_entry is not None:
    status, _ = call("DELETE", f"/api/v1/units/{PROPERTY_UNIT_RESIDENT}/residents/{cohabitant_entry['id']}/permanent",
                     None, resident)
    expect(f"cohabitant revocation answers 204 (status {status})", status == 204)
    status, residents = call("GET", f"/api/v1/units/{PROPERTY_UNIT_RESIDENT}/residents", None, resident)
    entries = residents if isinstance(residents, list) else (residents or {}).get("data") or []
    expect("the cohabitant no longer appears among active residents",
           not any(cohabitant["document"] in json.dumps(e.get("persona") or {}) and e.get("estado") == "ACTIVO"
                   for e in entries if isinstance(e, dict)))
    revoked_token = login_with(cohabitant["email"], cohabitant["password"])
    if revoked_token:
        status, _ = call("GET", f"/api/v1/porteria/unidades/{PROPERTY_UNIT_RESIDENT}/visitas", None, revoked_token)
        expect(f"revoked cohabitant has no unit access (status {status})", status in (401, 403))
    else:
        expect("revoked cohabitant cannot log in", True)

print(f"\nFAILURES: {len(failures)}")
for item in failures:
    print("  -", item)
sys.exit(1 if failures else 0)
