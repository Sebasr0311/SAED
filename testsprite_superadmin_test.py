import os
import requests

TARGET_URL = os.environ.get("TARGET_URL", "https://saed-backend.onrender.com")

def test_superadmin_platform_security_audit():
    base_url = TARGET_URL.rstrip("/")

    # 1. Unauthenticated requests to platform endpoints must be rejected
    r_unauth1 = requests.get(f"{base_url}/api/v1/platform/dashboard", timeout=15)
    assert r_unauth1.status_code in [401, 403], f"Expected 401/403 for unauthenticated /platform/dashboard, got {r_unauth1.status_code}"

    r_unauth2 = requests.get(f"{base_url}/api/v1/organizations", timeout=15)
    assert r_unauth2.status_code in [401, 403], f"Expected 401/403 for unauthenticated /organizations, got {r_unauth2.status_code}"

    # 2. Login as SuperAdmin (admin_global / admin123)
    r_login = requests.post(f"{base_url}/api/v1/auth/login", json={
        "username": "admin_global",
        "password": "admin123"
    }, timeout=15)
    assert r_login.status_code == 200, f"Login failed for admin_global: {r_login.status_code} - {r_login.text}"
    auth_data = r_login.json()
    assert "token" in auth_data, "Token missing in login response"
    token = auth_data["token"]
    user_info = auth_data.get("usuario", {})
    assert user_info.get("rol") == "SUPERADMIN", f"Expected role SUPERADMIN, got {user_info.get('rol')}"
    assert user_info.get("alcance") == "GLOBAL", f"Expected scope GLOBAL, got {user_info.get('alcance')}"

    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }

    # 3. Profile / Me & Contexts
    r_me = requests.get(f"{base_url}/api/v1/me", headers=headers, timeout=15)
    assert r_me.status_code == 200, f"GET /me failed: {r_me.status_code}"
    me_data = r_me.json()
    assert "id" in me_data and me_data["id"] > 0, f"Unexpected /me response: {me_data}"

    r_ctx = requests.get(f"{base_url}/api/v1/me/contexts", headers=headers, timeout=15)
    assert r_ctx.status_code == 200, f"GET /me/contexts failed: {r_ctx.status_code}"
    contexts = r_ctx.json()
    assert isinstance(contexts, list) and len(contexts) > 0, "Expected at least one context for admin_global"

    # 4. SaaS Platform Executive Dashboard
    r_dash = requests.get(f"{base_url}/api/v1/platform/dashboard", headers=headers, timeout=15)
    assert r_dash.status_code == 200, f"GET /platform/dashboard failed: {r_dash.status_code}"

    # 5. Global Organizations Management
    r_orgs = requests.get(f"{base_url}/api/v1/organizations", headers=headers, timeout=15)
    assert r_orgs.status_code == 200, f"GET /organizations failed: {r_orgs.status_code}"
    orgs_data = r_orgs.json()
    orgs_list = orgs_data.get("items") if isinstance(orgs_data, dict) else orgs_data
    assert isinstance(orgs_list, list), f"Expected list from /organizations, got {type(orgs_list)}"

    # 6. Global Subscription Plans
    r_plans = requests.get(f"{base_url}/api/v1/platform/plans", headers=headers, timeout=15)
    assert r_plans.status_code == 200, f"GET /platform/plans failed: {r_plans.status_code}"

    # 7. Global Memberships Management
    r_mem = requests.get(f"{base_url}/api/v1/platform/memberships", headers=headers, timeout=15)
    assert r_mem.status_code == 200, f"GET /platform/memberships failed: {r_mem.status_code}"

    # 8. Platform Global Administrators
    r_admins = requests.get(f"{base_url}/api/v1/platform/admins", headers=headers, timeout=15)
    assert r_admins.status_code == 200, f"GET /platform/admins failed: {r_admins.status_code}"

    # 9. Global Properties Listing
    r_props = requests.get(f"{base_url}/api/v1/properties", headers=headers, timeout=15)
    assert r_props.status_code == 200, f"GET /properties failed: {r_props.status_code}"

    # 10. Global Audit Trail
    r_audit = requests.get(f"{base_url}/api/v1/audit", headers=headers, timeout=15)
    assert r_audit.status_code == 200, f"GET /audit failed: {r_audit.status_code}"

    # 11. Strict Confinement (Zero-Trust Principle) — Direct Coproperty Operational Endpoints must be 403 Forbidden:
    # A SuperAdmin must never directly manipulate or inspect private condo unit operations
    r_multas = requests.get(f"{base_url}/api/v1/multas/todas", headers=headers, timeout=15)
    assert r_multas.status_code == 403, f"Expected 403 for /multas/todas, got {r_multas.status_code}"

    r_quejas = requests.get(f"{base_url}/api/v1/quejas/todas", headers=headers, timeout=15)
    assert r_quejas.status_code == 403, f"Expected 403 for /quejas/todas, got {r_quejas.status_code}"

    r_pqrs = requests.get(f"{base_url}/api/v1/pqrs/todos", headers=headers, timeout=15)
    assert r_pqrs.status_code == 403, f"Expected 403 for /pqrs/todos, got {r_pqrs.status_code}"

    r_visitas = requests.get(f"{base_url}/api/v1/porteria/unidades/1/visitas", headers=headers, timeout=15)
    assert r_visitas.status_code == 403, f"Expected 403 for /porteria/unidades/1/visitas, got {r_visitas.status_code}"

    r_cuotas = requests.get(f"{base_url}/api/v1/cuotas", headers=headers, timeout=15)
    assert r_cuotas.status_code == 403, f"Expected 403 for /cuotas, got {r_cuotas.status_code}"

    r_personas = requests.get(f"{base_url}/api/v1/personas", headers=headers, timeout=15)
    assert r_personas.status_code == 403, f"Expected 403 for /personas, got {r_personas.status_code}"

if __name__ == "__main__":
    test_superadmin_platform_security_audit()
    print("SUCCESS: 100% of SuperAdmin Platform Operations, Security Boundaries & Zero-Trust assertions passed.")
