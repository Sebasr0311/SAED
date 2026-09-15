import os
import requests

TARGET_URL = os.environ.get("TARGET_URL", "https://saed-backend.onrender.com")

def test_admin_organizacion_full_audit():
    base_url = TARGET_URL.rstrip("/")

    # 1. Unauthenticated requests to organizational console must be rejected
    r_unauth1 = requests.get(f"{base_url}/api/v1/org/profile", timeout=15)
    assert r_unauth1.status_code in [401, 403], f"Expected 401/403 for unauthenticated /org/profile, got {r_unauth1.status_code}"

    r_unauth2 = requests.get(f"{base_url}/api/v1/org/dashboard", timeout=15)
    assert r_unauth2.status_code in [401, 403], f"Expected 401/403 for unauthenticated /org/dashboard, got {r_unauth2.status_code}"

    # 2. Login as Admin Organizacion (admin_org / admin123)
    r_login = requests.post(f"{base_url}/api/v1/auth/login", json={
        "username": "admin_org",
        "password": "admin123"
    }, timeout=15)
    assert r_login.status_code == 200, f"Login failed for admin_org: {r_login.status_code} - {r_login.text}"
    auth_data = r_login.json()
    assert "token" in auth_data, "Token missing in login response"
    token = auth_data["token"]
    user_info = auth_data.get("usuario", {})
    assert user_info.get("rol") == "ADMIN_ORGANIZACION", f"Expected role ADMIN_ORGANIZACION, got {user_info.get('rol')}"
    assert user_info.get("alcance") == "ORGANIZACION", f"Expected scope ORGANIZACION, got {user_info.get('alcance')}"
    assert user_info.get("idOrganizacion") == 1, f"Expected idOrganizacion 1, got {user_info.get('idOrganizacion')}"

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
    assert isinstance(contexts, list) and len(contexts) > 0, "Expected at least one context for admin_org"

    # 4. Institutional Profile
    r_profile = requests.get(f"{base_url}/api/v1/org/profile", headers=headers, timeout=15)
    assert r_profile.status_code == 200, f"GET /org/profile failed: {r_profile.status_code}"
    profile_data = r_profile.json().get("data", r_profile.json())
    assert profile_data.get("idOrganizacion") == 1 or profile_data.get("id") == 1, f"Unexpected profile data: {profile_data}"

    # 5. Organizational Executive Dashboard
    r_dash = requests.get(f"{base_url}/api/v1/org/dashboard", headers=headers, timeout=15)
    assert r_dash.status_code == 200, f"GET /org/dashboard failed: {r_dash.status_code}"

    # 6. Subscription & Plan Quotas
    r_sub = requests.get(f"{base_url}/api/v1/org/subscription", headers=headers, timeout=15)
    assert r_sub.status_code == 200, f"GET /org/subscription failed: {r_sub.status_code}"

    # 7. Organizational Administrators
    r_admins = requests.get(f"{base_url}/api/v1/org/admins", headers=headers, timeout=15)
    assert r_admins.status_code == 200, f"GET /org/admins failed: {r_admins.status_code}"

    # 8. Properties Portfolio of the Organization
    r_props = requests.get(f"{base_url}/api/v1/properties", headers=headers, timeout=15)
    assert r_props.status_code == 200, f"GET /properties failed: {r_props.status_code}"
    props_data = r_props.json()
    props_list = props_data.get("items") if isinstance(props_data, dict) else props_data
    assert isinstance(props_list, list), f"Expected properties list, got {type(props_list)}"

    # 9. Organizational Audit Trail
    r_audit = requests.get(f"{base_url}/api/v1/audit", headers=headers, timeout=15)
    assert r_audit.status_code == 200, f"GET /audit failed: {r_audit.status_code}"

    # 10. Zero-Trust Confinement — Platform Level Strictly Forbidden (403):
    r_plat_dash = requests.get(f"{base_url}/api/v1/platform/dashboard", headers=headers, timeout=15)
    assert r_plat_dash.status_code == 403, f"Expected 403 for platform dashboard, got {r_plat_dash.status_code}"

    r_plat_plans = requests.get(f"{base_url}/api/v1/platform/plans", headers=headers, timeout=15)
    assert r_plat_plans.status_code == 403, f"Expected 403 for platform plans, got {r_plat_plans.status_code}"

    r_plat_admins = requests.get(f"{base_url}/api/v1/platform/admins", headers=headers, timeout=15)
    assert r_plat_admins.status_code == 403, f"Expected 403 for platform admins, got {r_plat_admins.status_code}"

    r_plat_mem = requests.get(f"{base_url}/api/v1/platform/memberships", headers=headers, timeout=15)
    assert r_plat_mem.status_code == 403, f"Expected 403 for platform memberships, got {r_plat_mem.status_code}"

    r_orgs = requests.get(f"{base_url}/api/v1/organizations", headers=headers, timeout=15)
    assert r_orgs.status_code == 403, f"Expected 403 for listing platform organizations, got {r_orgs.status_code}"

    # 11. Zero-Trust Confinement — Direct Operational Property Level Forbidden (403):
    r_multas = requests.get(f"{base_url}/api/v1/multas/todas", headers=headers, timeout=15)
    assert r_multas.status_code == 403, f"Expected 403 for property multas, got {r_multas.status_code}"

    r_quejas = requests.get(f"{base_url}/api/v1/quejas/todas", headers=headers, timeout=15)
    assert r_quejas.status_code == 403, f"Expected 403 for property quejas, got {r_quejas.status_code}"

    r_pqrs = requests.get(f"{base_url}/api/v1/pqrs/todos", headers=headers, timeout=15)
    assert r_pqrs.status_code == 403, f"Expected 403 for property pqrs, got {r_pqrs.status_code}"

if __name__ == "__main__":
    test_admin_organizacion_full_audit()
    print("SUCCESS: 100% of Admin Organizacion Operations, Security Boundaries & Zero-Trust assertions passed.")
