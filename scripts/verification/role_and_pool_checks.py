"""Mixed-role switching and pooled-connection context checks against the isolated backend.

A. The mixed-role fixture user (resident in property 91001, ADMIN_PROPIEDAD in property 91021 via
   assignment 93001) switches context per request with X-Assignment-Id; forged headers must not grant access.
B. Hundreds of concurrent requests alternate between organizations and roles over the small Hikari pool;
   any response containing an id from the caller's foreign organization means context leaked across
   pooled connections.

Never prints passwords, tokens or response bodies. Usage: role_and_pool_checks.py <state> <port> [rounds]
"""
import concurrent.futures
import json
import pathlib
import re
import sys
import urllib.error
import urllib.request

import fixture_scope

REPO = pathlib.Path(__file__).resolve().parents[2]
state = json.loads((REPO / "local" / "isolated-oracle" / sys.argv[1] / "private-state.json").read_text(encoding="utf-8"))
BASE = f"http://127.0.0.1:{sys.argv[2]}"
ROUNDS = int(sys.argv[3]) if len(sys.argv) > 3 else 60
# Exact fixture ids per organization: a numeric range would flag ids that sequences hand out to scenario-created rows.
FIXTURE_IDS = {tenant: fixture_scope.fixture_ids(state["accounts"], tenant) for tenant in (91001, 92001)}


def call(path, token, headers=None):
    req = urllib.request.Request(BASE + path, method="GET")
    req.add_header("Authorization", "Bearer " + token)
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            return res.status, res.read()
    except urllib.error.HTTPError as err:
        return err.code, err.read()


def login(username):
    acc = next(a for a in state["accounts"] if a["username"] == username)
    req = urllib.request.Request(BASE + "/api/v1/auth/login", data=json.dumps({"username": acc["email"], "password": acc["password"]}).encode(), method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=30) as res:
        body = json.loads(res.read())

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


def ids_in(raw, org):
    return fixture_scope.found_ids(raw, FIXTURE_IDS[org])


failures = []


def expect(label, condition):
    print(f"  {'PASS' if condition else 'FAIL'} {label}")
    if not condition:
        failures.append(label)


# ---------------------------------------------------------------- A. role switching
print("A. Mixed-role switching (user 91005)")
mixed = login("iso_0_residente")
status, raw = call("/api/v1/auth/assignments", mixed)
assignment_ids = sorted({int(m) for m in re.findall(r'"(?:idAsignacion|id|assignmentId)"\s*:\s*(\d+)', raw.decode())})
expect(f"lists own assignments (status {status}, ids {assignment_ids})", status == 200 and 93001 in assignment_ids)

s_res, _ = call("/api/v1/usuarios", mixed)
expect(f"default resident context cannot list users (status {s_res})", s_res == 403)
s_adm, raw_adm = call("/api/v1/usuarios", mixed, {"X-Assignment-Id": "93001"})
expect(f"admin assignment 93001 can list users (status {s_adm})", s_adm == 200)
expect("admin assignment sees no organization-92001 ids", not ids_in(raw_adm, 92001))
s_prop, _ = call("/api/v1/properties/91021", mixed, {"X-Assignment-Id": "93001"})
expect(f"admin assignment reads administered property 91021 (status {s_prop})", s_prop == 200)
s_foreign, raw_foreign = call("/api/v1/properties/92001", mixed, {"X-Assignment-Id": "93001"})
expect(f"admin assignment cannot read foreign property 92001 (status {s_foreign})", s_foreign in (403, 404) or not ids_in(raw_foreign, 92001))

other_admin = next(a for a in state["accounts"] if a["username"] == "iso_0_admin_propiedad")["id"]
foreign_admin = next(a for a in state["accounts"] if a["username"] == "iso_1_admin_propiedad")["id"]
for label, header in (("another user's assignment", str(other_admin)), ("organization-92001 assignment", str(foreign_admin)),
                      ("nonexistent assignment", "999999"), ("negative id", "-5"), ("garbage", "93001 OR 1=1")):
    s_forged, raw_forged = call("/api/v1/usuarios", mixed, {"X-Assignment-Id": header})
    expect(f"forged X-Assignment-Id ({label}) is rejected (status {s_forged})", s_forged in (400, 401, 403) and not ids_in(raw_forged, 92001))

s_after, _ = call("/api/v1/usuarios", mixed)
expect(f"context does not stick after a switched request (status {s_after})", s_after == 403)

# ---------------------------------------------------------------- B. pooled-connection context
print(f"\nB. Pooled-connection context ({ROUNDS} rounds x 6 interleaved callers, 12 threads)")
callers = [
    ("org1 admin_org", login("iso_0_admin_organizacion"), {}, 91001, "/api/v1/usuarios"),
    ("org2 admin_org", login("iso_1_admin_organizacion"), {}, 92001, "/api/v1/usuarios"),
    ("org1 admin_prop", login("iso_0_admin_propiedad"), {}, 91001, "/api/v1/units"),
    ("org2 admin_prop", login("iso_1_admin_propiedad"), {}, 92001, "/api/v1/units"),
    ("mixed as admin", mixed, {"X-Assignment-Id": "93001"}, 91001, "/api/v1/assignments"),
    ("org1 resident", login("iso_0_residente_convivencia"), {}, 91001, "/api/v1/units"),
]
jobs = [c for _ in range(ROUNDS) for c in callers]
statuses = {}
leaks = []
empty = []


def run(job):
    name, token, headers, org, path = job
    foreign = 92001 if org == 91001 else 91001
    status, raw = call(path, token, headers)
    ok = 200 <= status < 300
    return name, status, ids_in(raw, foreign) if ok else [], bool(ids_in(raw, org)) if ok else False


with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
    for name, status, leaked, has_own in pool.map(run, jobs):
        if 200 <= status < 300 and not has_own:
            empty.append(name)
        statuses.setdefault(name, {}).setdefault(status, 0)
        statuses[name][status] += 1
        if leaked:
            leaks.append((name, leaked[:5]))
for name, counts in statuses.items():
    print(f"  {name:<16} {counts}")
expect(f"{len(jobs)} concurrent requests with zero cross-organization ids (leaks: {len(leaks)})", not leaks)
expect(f"every 2xx response contains its own organization ids (empty: {len(empty)})", not empty)
expect("no 5xx under concurrency", all(code < 500 for counts in statuses.values() for code in counts))

print(f"\nFAILURES: {len(failures)}")
for item in failures:
    print("  -", item)
