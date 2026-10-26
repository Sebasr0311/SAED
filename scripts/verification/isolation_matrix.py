"""Cross-tenant isolation matrix against the isolated backend.

For each synthetic caller, request tenant list endpoints and direct foreign-tenant resource ids,
then flag any response that contains an identifier from the other organization's id range.
Foreign rows are recognised exactly, from the fixture definition (fixture_scope), not by numeric id range: Oracle
sequences hand out new ids inside the fixture ranges, so a range test reports false leaks after any scenario run.
Never prints passwords, tokens or response bodies; only status codes and leaked id counts.

Usage: isolation_matrix.py <recovery-state> <port>
"""
import json
import pathlib
import sys
import urllib.error
import urllib.request

import fixture_scope

REPO = pathlib.Path(__file__).resolve().parents[2]
state = json.loads((REPO / "local" / "isolated-oracle" / sys.argv[1] / "private-state.json").read_text(encoding="utf-8"))
BASE = f"http://127.0.0.1:{sys.argv[2]}"

# Exact fixture ids per organization (organization, properties, units, persons/users/assignments).
FIXTURE_IDS = {tenant: fixture_scope.fixture_ids(state["accounts"], tenant) for tenant in (91001, 92001)}

LIST_ENDPOINTS = [
    "/api/v1/organizations",
    "/api/v1/properties",
    "/api/v1/units",
    "/api/v1/personas",
    "/api/v1/usuarios",
    "/api/v1/assignments",
    "/api/v1/auth/assignments",
    "/api/v1/residentes",
]


def foreign_endpoints(org):
    """Direct requests for resources that belong to the other organization."""
    o = 92001 if org == 91001 else 91001
    return [
        f"/api/v1/organizations/{o}",
        f"/api/v1/properties/{o}",
        f"/api/v1/properties/{o + 20}",
        f"/api/v1/units/{o}",
        f"/api/v1/units/{o + 10}",
        f"/api/v1/units/{o}/residents",
        f"/api/v1/units/{o}/owners",
        f"/api/v1/personas/{o + 2}",
        f"/api/v1/personas/{o + 4}",
        f"/api/v1/properties/{o}/blocks",
        f"/api/v1/residentes/{o + 4}/dashboard",
        f"/api/v1/residentes/{o + 4}/visitas-historial",
    ]


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as res:
            raw = res.read()
            return res.status, raw
    except urllib.error.HTTPError as err:
        return err.code, err.read()
    except urllib.error.URLError as err:
        return 0, str(err).encode()


def leaked_ids(raw, foreign_org):
    return fixture_scope.found_ids(raw, FIXTURE_IDS[foreign_org])


def login(account):
    status, raw = call("POST", "/api/v1/auth/login", {"username": account["email"], "password": account["password"]})
    if status != 200:
        return None
    body = json.loads(raw)

    def find(node):
        if isinstance(node, dict):
            for key in ("token", "accessToken", "access_token"):
                if isinstance(node.get(key), str):
                    return node[key]
            for value in node.values():
                hit = find(value)
                if hit:
                    return hit
        return None

    return find(body)


callers = [a for a in state["accounts"] if a["organization"] == 91001]
callers += [a for a in state["accounts"] if a["organization"] == 92001 and a["role"] == "ADMIN_ORGANIZACION"]
superadmin = [a for a in state["accounts"] if a["role"] == "SUPERADMIN"]

leaks = 0
for account in callers + superadmin:
    token = login(account)
    org = account["organization"] or 91001
    foreign = 92001 if org == 91001 else 91001
    tag = "SUPERADMIN (design: no operational data)" if account["role"] == "SUPERADMIN" else f"{account['role']} org={org}"
    print(f"\n### {account['username']} — {tag}")
    if not token:
        print("  LOGIN FAILED")
        continue
    for path in LIST_ENDPOINTS + foreign_endpoints(org):
        status, raw = call("GET", path, token=token)
        ids = leaked_ids(raw, foreign) if 200 <= status < 300 else []
        if ids and account["role"] != "SUPERADMIN":
            leaks += 1
        mark = "LEAK" if ids else "ok"
        print(f"  {status:>3} {mark:<4} {path}" + (f"  foreign_ids={ids[:8]}{'…' if len(ids) > 8 else ''}" if ids else ""))

print(f"\nNON-SUPERADMIN LEAKING RESPONSES: {leaks}")
