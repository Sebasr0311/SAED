#!/usr/bin/env python3
"""Static API-contract checker: frontend API calls vs. Spring controller mappings.

Extracts every backend endpoint (HTTP method + full path) from backend/src/main/java and every frontend call made
through the API client (api / tenantApi / apiClient wrappers and fetch(`${BASE_URL}...`)) from frontend/src, then
reports frontend calls that no backend endpoint serves. Path variables are normalised to `{}` and query strings
are dropped. No network and no database are used.

Exit code 1 when a frontend call has no matching backend endpoint and is not in ALLOWLIST.

Usage: python scripts/verification/api_contract_check.py [--unused] [--dynamic]
"""
import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BACKEND_SRC = ROOT / "backend" / "src" / "main" / "java"
FRONTEND_SRC = ROOT / "frontend" / "src"
API_PREFIX = "/api/v1"

# Frontend calls that are intentionally not served by the Spring backend. Key: (METHOD, normalised path).
# Every entry needs a reason. Keep it empty unless a call targets an external service by relative path.
ALLOWLIST = {}

HTTP_BY_ANNOTATION = {"Get": "GET", "Post": "POST", "Put": "PUT", "Patch": "PATCH", "Delete": "DELETE"}
FRONT_METHOD = {"get": "GET", "post": "POST", "put": "PUT", "patch": "PATCH", "delete": "DELETE", "del": "DELETE",
                "postFormData": "POST", "putFormData": "PUT"}


# ---------------------------------------------------------------------------
# Lexical helpers
# ---------------------------------------------------------------------------
def normalize_path(path):
    """Drop the query string, normalise path variables to {} and strip trailing slashes."""
    path = path.split("?", 1)[0]
    path = re.sub(r"\{[^}]*\}", "{}", path)
    if not path.startswith("/"):
        path = "/" + path
    if len(path) > 1:
        path = path.rstrip("/")
    return path


def _skip_string(s, i):
    """s[i] is a quote or backtick; return the index just after the closing delimiter."""
    q = s[i]
    i += 1
    n = len(s)
    while i < n:
        c = s[i]
        if c == "\\":
            i += 2
            continue
        if c == q:
            return i + 1
        if q == "`" and c == "$" and i + 1 < n and s[i + 1] == "{":
            i = _skip_braces(s, i + 1)
            continue
        i += 1
    return n


def _skip_braces(s, i):
    """s[i] == '{'; return the index after the matching '}' (strings are skipped)."""
    depth = 0
    n = len(s)
    while i < n:
        c = s[i]
        if c in "'\"`":
            i = _skip_string(s, i)
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


def strip_comments(src, template_strings=True):
    """Remove // and /* */ comments while keeping strings and newlines (so line numbers stay valid)."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if c in "'\"" or (c == "`" and template_strings):
            j = _skip_string(src, i)
            out.append(src[i:j])
            i = j
        elif c == "/" and nxt == "/":
            j = src.find("\n", i)
            i = n if j == -1 else j
        elif c == "/" and nxt == "*":
            j = src.find("*/", i + 2)
            j = n if j == -1 else j + 2
            out.append("\n" * src.count("\n", i, j))
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def _mask_strings(src):
    """Same length as src, string contents replaced by 'x' so structure scans cannot be fooled."""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        if src[i] in "'\"`":
            j = _skip_string(src, i)
            for k in range(i + 1, max(i + 1, j - 1)):
                if out[k] != "\n":
                    out[k] = "x"
            i = j
        else:
            i += 1
    return "".join(out)


def _balanced_parens(mask, i):
    """mask[i] == '('; return the index of the matching ')'."""
    depth = 0
    for j in range(i, len(mask)):
        if mask[j] in "([{":
            depth += 1
        elif mask[j] in ")]}":
            depth -= 1
            if depth == 0:
                return j
    return len(mask) - 1


def _split_top_level(mask, text, sep=","):
    """Split text on top-level separators using mask (same length as text) to find nesting."""
    parts, depth, start = [], 0, 0
    for i, c in enumerate(mask):
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
        elif c == sep and depth == 0:
            parts.append(text[start:i])
            start = i + 1
    parts.append(text[start:])
    return parts


JAVA_STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')


# ---------------------------------------------------------------------------
# Backend
# ---------------------------------------------------------------------------
def _mapping_paths(args):
    """Paths declared by a mapping annotation argument list (value=/path=/positional)."""
    if args is None:
        return [""]
    mask = _mask_strings(args)
    paths = []
    for part, pmask in zip(_split_top_level(mask, args), _split_top_level(mask, mask)):
        m = re.match(r"\s*(\w+)\s*=(?!=)", pmask)
        if m and m.group(1) not in ("value", "path"):
            continue
        paths.extend(JAVA_STRING.findall(part))
    return paths or [""]


def _mapping_methods(args):
    return re.findall(r"RequestMethod\.(\w+)", args or "")


def parse_java_endpoints(source):
    """Return [(METHOD, normalised path)] for one controller source (without the /api/v1 prefix)."""
    code = strip_comments(source, template_strings=False)
    mask = _mask_strings(code)
    cls = re.search(r"\b(?:class|interface)\s+\w+", mask)
    cls_pos = cls.start() if cls else len(mask)
    prefixes, mappings = [""], []
    for m in re.finditer(r"@(Get|Post|Put|Patch|Delete|Request)Mapping\b", mask):
        args = None
        k = m.end()
        while k < len(mask) and mask[k] in " \t\r\n":
            k += 1
        if k < len(mask) and mask[k] == "(":
            close = _balanced_parens(mask, k)
            args = code[k + 1:close]
        kind = m.group(1)
        if kind == "Request" and m.start() < cls_pos:
            prefixes = _mapping_paths(args)
        else:
            mappings.append((kind, args))
    endpoints = []
    for kind, args in mappings:
        methods = (_mapping_methods(args) or ["ANY"]) if kind == "Request" else [HTTP_BY_ANNOTATION[kind]]
        for prefix in prefixes:
            for sub in _mapping_paths(args):
                full = normalize_path(prefix.rstrip("/") + "/" + sub.lstrip("/"))
                if full == API_PREFIX or full.startswith(API_PREFIX + "/"):
                    full = full[len(API_PREFIX):] or "/"
                for method in methods:
                    endpoints.append((method, full))
    return endpoints


def collect_backend(root=BACKEND_SRC):
    endpoints = set()
    for path in sorted(root.rglob("*.java")):
        endpoints.update(parse_java_endpoints(path.read_text(encoding="utf-8", errors="replace")))
    return sorted(endpoints)


# ---------------------------------------------------------------------------
# Frontend
# ---------------------------------------------------------------------------
CALL_RE = re.compile(r"\b(?:api|tenantApi|apiClient)\.(get|post|put|patch|delete|del|postFormData|putFormData)\s*\(")
FETCH_RE = re.compile(r"\bfetch\s*\(\s*`\$\{BASE_URL\}")


def _template_to_path(body):
    """Template literal body (without backticks) -> path with {} for expressions."""
    out, i, n = [], 0, len(body)
    while i < n:
        if body[i] == "$" and i + 1 < n and body[i + 1] == "{":
            j = _skip_braces(body, i + 1)
            prev = out[-1][-1:] if out else ""
            rest = body[j:j + 1]
            if not out:
                pass  # leading expression: base URL / prefix, drop it
            elif prev == "/":
                out.append("{}")
            elif rest in ("", "?"):
                pass  # suffix glued to a segment (query string, optional parameters): drop it
            else:
                out.append("{}")
            i = j
        elif body[i] == "\\":
            out.append(body[i + 1:i + 2])
            i += 2
        else:
            out.append(body[i])
            i += 1
    return "".join(out)


def _string_paths(arg):
    """Every string token of an argument expression that looks like a path, converted."""
    paths = []
    i, n = 0, len(arg)
    while i < n:
        if arg[i] in "'\"`":
            j = _skip_string(arg, i)
            body = arg[i + 1:j - 1]
            text = _template_to_path(body) if arg[i] == "`" else body
            if text.startswith("/"):
                paths.append(normalize_path(text))
            i = j
        else:
            i += 1
    return paths


def _args(code, mask, open_idx):
    close = _balanced_parens(mask, open_idx)
    inner, inner_mask = code[open_idx + 1:close], mask[open_idx + 1:close]
    parts = _split_top_level(inner_mask, inner)
    return parts[0], inner[len(parts[0]):]


def parse_frontend_calls(source):
    """Return ([(METHOD, path, line)], [line of calls whose path is not statically known])."""
    code = strip_comments(source)
    mask = _mask_strings(code)
    calls, dynamic = [], []
    for m in CALL_RE.finditer(mask):
        first, _ = _args(code, mask, m.end() - 1)
        line = code.count("\n", 0, m.start()) + 1
        paths = _string_paths(first)
        if not paths:
            dynamic.append(line)
        for p in paths:
            calls.append((FRONT_METHOD[m.group(1)], p, line))
    for m in FETCH_RE.finditer(code):
        first, rest = _args(code, mask, code.rfind("(", 0, m.end()))
        line = code.count("\n", 0, m.start()) + 1
        method = re.search(r"method\s*:\s*['\"](\w+)['\"]", rest)
        paths = _string_paths(first)
        if not paths:
            dynamic.append(line)
        for p in paths:
            calls.append((method.group(1).upper() if method else "GET", p, line))
    return calls, dynamic


def collect_frontend(root=FRONTEND_SRC):
    calls, dynamic = [], []
    for path in sorted(list(root.rglob("*.js")) + list(root.rglob("*.jsx"))):
        rel = path.relative_to(ROOT).as_posix()
        found, dyn = parse_frontend_calls(path.read_text(encoding="utf-8", errors="replace"))
        calls.extend((m, p, f"{rel}:{line}") for m, p, line in found)
        dynamic.extend(f"{rel}:{line}" for line in dyn)
    return calls, dynamic


# ---------------------------------------------------------------------------
# Matching
# ---------------------------------------------------------------------------
def _seg_ok(x, y):
    """Frontend segment x vs backend segment y. A literal word never satisfies a backend path variable (that is how
    `/reservas/zonas` hid behind `/reservas/{id}`); only numeric literals do. A frontend `{}` fits anything."""
    return x == y or x == "{}" or (y in ("{}", "*") and x.isdigit())


def _segments_match(call_path, endpoint_path):
    a, b = call_path.strip("/").split("/"), endpoint_path.strip("/").split("/")
    if "**" in b:
        k = b.index("**")
        return len(a) >= k and all(_seg_ok(x, y) for x, y in zip(a[:k], b[:k]))
    return len(a) == len(b) and all(_seg_ok(x, y) for x, y in zip(a, b))


def _serves(endpoint, method, path):
    return endpoint[0] in (method, "ANY") and _segments_match(path, endpoint[1])


def find_unmatched(calls, endpoints, allowlist=None):
    allowlist = ALLOWLIST if allowlist is None else allowlist
    return [c for c in calls
            if (c[0], c[1]) not in allowlist and not any(_serves(e, c[0], c[1]) for e in endpoints)]


def find_unused(calls, endpoints):
    return sorted(e for e in endpoints if not any(_serves(e, m, p) for m, p, _ in calls))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--unused", action="store_true", help="also list backend endpoints no frontend call uses")
    parser.add_argument("--dynamic", action="store_true", help="list calls whose path is not statically known")
    opts = parser.parse_args(argv)
    endpoints = collect_backend()
    calls, dynamic = collect_frontend()
    unmatched = find_unmatched(calls, endpoints)
    by_path = {}
    for e in endpoints:
        by_path.setdefault(e[1], set()).add(e[0])
    print(f"backend endpoints: {len(endpoints)}  frontend calls: {len(calls)}  dynamic (skipped): {len(dynamic)}")
    for method, path, where in sorted(unmatched, key=lambda c: (c[2], c[0])):
        hint = ""
        for ep, methods in by_path.items():
            if _segments_match(path, ep):
                hint = f"  (path exists for {','.join(sorted(methods))})"
                break
        print(f"UNMATCHED {method} {path}  <- {where}{hint}")
    if opts.dynamic:
        for where in dynamic:
            print(f"DYNAMIC {where}")
    if opts.unused:
        unused = find_unused(calls, endpoints)
        print(f"-- backend endpoints without a frontend call (informational): {len(unused)}")
        for method, path in unused:
            print(f"UNUSED {method} {path}")
    print(f"UNMATCHED FRONTEND CALLS: {len(unmatched)}")
    return 1 if unmatched else 0


if __name__ == "__main__":
    sys.exit(main())
