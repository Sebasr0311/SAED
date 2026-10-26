"""DEC-02 probe: SUPERADMIN calls every parameterless GET endpoint and reports responses carrying tenant ids.

SUPERADMIN manages platform data (organizations, properties as tenant containers, plans, memberships, organization
administrators) and must not read operational data. The isolation matrix only flags the *other* organization's ids,
so it cannot see SUPERADMIN reading either tenant. This probe extracts GET routes from the controllers, calls each one
as SUPERADMIN and lists every 2xx response that contains fixture ids of any organization (91xxx or 92xxx), so each hit
can be classified as platform data or a DEC-02 violation. Never prints passwords, tokens or response bodies.

Usage: superadmin_operational_probe.py <state> <port>
"""
import json
import pathlib
import re
import sys
import urllib.error
import urllib.request

REPO = pathlib.Path(__file__).resolve().parents[2]
STATE, PORT = sys.argv[1], sys.argv[2]
BASE = f"http://127.0.0.1:{PORT}"
state = json.loads((REPO / "local" / "isolated-oracle" / STATE / "private-state.json").read_text(encoding="utf-8"))
CONTROLLERS = REPO / "backend" / "src" / "main" / "java"


def routes():
    found = set()
    for file in CONTROLLERS.rglob("*Controller.java"):
        src = file.read_text(encoding="utf-8", errors="replace")
        head = src.split("class ", 1)[0]
        base = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?"([^"]*)"', head)
        prefix = base.group(1) if base else ""
        for m in re.finditer(r'@GetMapping(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?(?:\{\s*)?"([^"]*)")?', src):
            path = prefix + (m.group(1) or "")
            if "{" not in path and path.startswith("/api/"):
                found.add(path)
    return sorted(found)


def call(path, token):
    req = urllib.request.Request(BASE + path, method="GET")
    req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as res:
            return res.status, res.read()
    except urllib.error.HTTPError as err:
        return err.code, b""
    except urllib.error.URLError:
        return 0, b""


def tenant_ids(raw):
    found = set()
    try:
        payload = json.loads(raw or b"null")
    except ValueError:
        return found

    def walk(node):
        if isinstance(node, bool):
            return
        if isinstance(node, int) and (91000 <= node <= 92999):
            found.add(node)
        elif isinstance(node, dict):
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(payload)
    return found


account = next(a for a in state["accounts"] if a["role"] == "SUPERADMIN")
req = urllib.request.Request(BASE + "/api/v1/auth/login", method="POST",
                             data=json.dumps({"username": account["email"], "password": account["password"]}).encode())
req.add_header("Content-Type", "application/json")
with urllib.request.urlopen(req, timeout=20) as res:
    body = json.loads(res.read())
token = body.get("token") or body.get("accessToken") or (body.get("data") or {}).get("token")

statuses = {}
hits = []
for path in routes():
    status, raw = call(path, token)
    statuses[status] = statuses.get(status, 0) + 1
    if status >= 500 or status == 0:
        hits.append((path, status, []))
    elif 200 <= status < 300:
        ids = tenant_ids(raw)
        if ids:
            hits.append((path, status, sorted(ids)))

print(f"GET routes probed: {sum(statuses.values())}  statuses: {dict(sorted(statuses.items()))}")
print("\n2xx responses carrying tenant ids (classify each: platform data or DEC-02 violation):")
for path, status, ids in hits:
    if ids:
        print(f"  {status} {path:<55} ids={ids[:10]}{'…' if len(ids) > 10 else ''}")
errors = [(p, s) for p, s, ids in hits if not ids]
print("\nServer errors (a role without access must get 4xx, never 5xx):")
for path, status in errors:
    print(f"  {status} {path}")
print(f"\nRESPONSES WITH TENANT IDS: {len(hits) - len(errors)}  SERVER ERRORS: {len(errors)}")
sys.exit(1 if errors else 0)
