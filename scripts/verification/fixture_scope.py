"""Exact detection of the other tenant's synthetic fixture rows in an API response.

The isolated fixture gives organization 91001 ids in 91xxx and organization 92001 ids in 92xxx, but Oracle identity
sequences also hand out NEW ids inside those ranges (a scenario-created row of organization 91001 can get id 92007).
A numeric range test therefore reports false leaks after any scenario run. The set of foreign ids is built from the
fixture definition instead (isolated_oracle.tenant_layout and the prepared state's account ids), so only a row that
really belongs to the other organization's fixture is flagged.

Pure functions, no I/O: never reads credentials; only account ids/organizations are used.
"""
import importlib.util
import json
import re
from pathlib import Path

_spec = importlib.util.spec_from_file_location("isolated_oracle", Path(__file__).with_name("isolated_oracle.py"))
_oracle = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_oracle)

# The mixed-role assignment (isolated_oracle.mixed_assignment_sql) belongs to organization 91001.
MIXED_ASSIGNMENT_ORGANIZATION = 91001
MIXED_ASSIGNMENT_ID = 93001


def fixture_ids(accounts, tenant):
    """Every fixture id of one organization: the organization, its properties and units, and its persons, users and
    assignments (the fixture reuses one id for the three)."""
    ids = {tenant}
    for property_id, unit_ids in _oracle.tenant_layout(tenant):
        ids.add(property_id)
        ids.update(unit_ids)
    ids.update(account["id"] for account in accounts if account.get("organization") == tenant)
    if tenant == MIXED_ASSIGNMENT_ORGANIZATION:
        ids.add(MIXED_ASSIGNMENT_ID)
    return frozenset(ids)


def found_ids(raw, ids):
    """Sorted members of ``ids`` present in a response body, as JSON numbers or as 5-digit tokens inside strings."""
    found = set()
    try:
        payload = json.loads(raw or b"null")
    except ValueError:
        payload = raw.decode("utf-8", "replace") if isinstance(raw, (bytes, bytearray)) else str(raw)

    def walk(node):
        if isinstance(node, bool):
            return
        if isinstance(node, (int, float)):
            if int(node) == node and int(node) in ids:
                found.add(int(node))
        elif isinstance(node, str):
            for match in re.findall(r"(?<!\d)(\d{5})(?!\d)", node):
                if int(match) in ids:
                    found.add(int(match))
        elif isinstance(node, dict):
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(payload)
    return sorted(found)
