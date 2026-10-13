"""Login every synthetic account against the isolated backend and compare the resolved context.

Never prints passwords or tokens. Usage: smoke_isolated_profiles.py <recovery-state> <port>
"""
import json
import pathlib
import sys
import urllib.error
import urllib.request

REPO = pathlib.Path(r"C:\Users\JUAN\Antigravity IDE\SAED")
state = json.loads((REPO / "local" / "isolated-oracle" / sys.argv[1] / "private-state.json").read_text(encoding="utf-8"))
BASE = f"http://127.0.0.1:{sys.argv[2]}"


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as res:
            return res.status, json.loads(res.read() or b"null")
    except urllib.error.HTTPError as err:
        raw = err.read()
        try:
            return err.code, json.loads(raw or b"null")
        except ValueError:
            return err.code, None


for acc in state["accounts"]:
    label = f"{acc['username']:<30} {acc['role']:<22}"
    status, body = call("POST", "/api/v1/auth/login", {"username": acc["email"], "password": acc["password"]})
    if status != 200 or not isinstance(body, dict):
        message = body.get("message") if isinstance(body, dict) else None
        print(f"{label} LOGIN {status} {message!r}")
        continue
    user = body.get("usuario") or {}
    got = (user.get("rol"), user.get("idOrganizacion"), user.get("idPropiedad"), user.get("idUnidad"))
    want = (acc["role"], acc["organization"], acc["property"], acc["unit"])
    print(f"{label} LOGIN 200 context={got} {'MATCH' if got == want else 'MISMATCH expected=' + str(want)}")
