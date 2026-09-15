import os
import requests

TARGET_URL = os.environ.get("TARGET_URL", "https://saed-backend.onrender.com")

def test_admin_propiedad_full_audit():
    base_url = TARGET_URL.rstrip("/")

    # 1. Unauthenticated requests to administrative endpoints must be rejected
    r_unauth1 = requests.get(f"{base_url}/api/v1/units", timeout=15)
    assert r_unauth1.status_code in [401, 403], f"Expected 401/403 for unauthenticated /units, got {r_unauth1.status_code}"

    r_unauth2 = requests.get(f"{base_url}/api/v1/properties/1", timeout=15)
    assert r_unauth2.status_code in [401, 403], f"Expected 401/403 for unauthenticated /properties/1, got {r_unauth2.status_code}"

    # 2. Login as Admin Propiedad (admin / admin123)
    r_login = requests.post(f"{base_url}/api/v1/auth/login", json={
        "username": "admin",
        "password": "admin123"
    }, timeout=15)
    assert r_login.status_code == 200, f"Login failed for admin: {r_login.status_code} - {r_login.text}"
    auth_data = r_login.json()
    assert "token" in auth_data, "Token missing in login response"
    token = auth_data["token"]
    user_info = auth_data.get("usuario", {})
    assert user_info.get("rol") == "ADMIN_PROPIEDAD", f"Expected role ADMIN_PROPIEDAD, got {user_info.get('rol')}"
    assert user_info.get("alcance") == "PROPIEDAD", f"Expected scope PROPIEDAD, got {user_info.get('alcance')}"
    assert user_info.get("idPropiedad") == 1, f"Expected idPropiedad 1, got {user_info.get('idPropiedad')}"

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
    assert isinstance(contexts, list) and len(contexts) > 0, "Expected at least one context for admin propiedad"

    # 4. Property Detail (Property 1)
    r_prop = requests.get(f"{base_url}/api/v1/properties/1", headers=headers, timeout=15)
    assert r_prop.status_code == 200, f"GET /properties/1 failed: {r_prop.status_code}"
    prop_data = r_prop.json()
    assert prop_data.get("id") == 1 or prop_data.get("idPropiedad") == 1, f"Unexpected prop data: {prop_data}"

    # 5. Units Management
    r_units = requests.get(f"{base_url}/api/v1/units", headers=headers, timeout=15)
    assert r_units.status_code == 200, f"GET /units failed: {r_units.status_code}"
    units_data = r_units.json()
    units_list = units_data.get("items") if isinstance(units_data, dict) else units_data
    assert isinstance(units_list, list), f"Expected units list, got {type(units_list)}"

    # 6. Residents and Owners Oversight
    r_residents = requests.get(f"{base_url}/api/v1/units/1/residents", headers=headers, timeout=15)
    assert r_residents.status_code == 200, f"GET /units/1/residents failed: {r_residents.status_code}"

    r_owners = requests.get(f"{base_url}/api/v1/units/1/owners", headers=headers, timeout=15)
    assert r_owners.status_code == 200, f"GET /units/1/owners failed: {r_owners.status_code}"

    # 7. Guardhouse & Operational Supervision
    r_registros = requests.get(f"{base_url}/api/v1/porteria/propiedades/1/registros", headers=headers, timeout=15)
    assert r_registros.status_code == 200, f"GET porteria registros failed: {r_registros.status_code}"

    r_visitas_resumen = requests.get(f"{base_url}/api/v1/porteria/visitas-resumen", headers=headers, timeout=15)
    assert r_visitas_resumen.status_code == 200, f"GET visitas-resumen failed: {r_visitas_resumen.status_code}"

    r_paquetes = requests.get(f"{base_url}/api/v1/paquetes", headers=headers, timeout=15)
    assert r_paquetes.status_code == 200, f"GET paquetes failed: {r_paquetes.status_code}"

    r_parqueaderos = requests.get(f"{base_url}/api/v1/parqueaderos", headers=headers, timeout=15)
    assert r_parqueaderos.status_code == 200, f"GET parqueaderos failed: {r_parqueaderos.status_code}"

    # 8. Financial Portfolio (Cartera)
    r_cartera = requests.get(f"{base_url}/api/v1/cartera", headers=headers, timeout=15)
    assert r_cartera.status_code in [200, 204], f"GET cartera failed: {r_cartera.status_code}"

    # 9. Administrative Unit Visits Oversight (Authorized for Admin, unlike Portero)
    r_unit_visitas = requests.get(f"{base_url}/api/v1/porteria/unidades/1/visitas", headers=headers, timeout=15)
    assert r_unit_visitas.status_code == 200, f"Expected 200 for admin unit visits, got {r_unit_visitas.status_code}"

    # 10. Zero-Trust Platform Perimeter & Cross-Tenant Isolation:
    # Admin Propiedad cannot manage platform-level organizations
    r_orgs = requests.get(f"{base_url}/api/v1/organizations", headers=headers, timeout=15)
    assert r_orgs.status_code == 403, f"Expected 403 for organizations, got {r_orgs.status_code}"

    # Admin Propiedad cannot access platform subscription plans
    r_plans = requests.get(f"{base_url}/api/v1/platform/plans", headers=headers, timeout=15)
    assert r_plans.status_code == 403, f"Expected 403 for platform plans, got {r_plans.status_code}"

    # Admin Propiedad cannot access platform dashboard
    r_dashboard = requests.get(f"{base_url}/api/v1/platform/dashboard", headers=headers, timeout=15)
    assert r_dashboard.status_code == 403, f"Expected 403 for platform dashboard, got {r_dashboard.status_code}"

    # Admin Propiedad cannot access a foreign property outside its assigned scope
    r_foreign = requests.get(f"{base_url}/api/v1/properties/888888", headers=headers, timeout=15)
    assert r_foreign.status_code in [403, 404], f"Expected 403 or 404 for foreign property 888888, got {r_foreign.status_code}"

if __name__ == "__main__":
    test_admin_propiedad_full_audit()
    print("SUCCESS: 100% of Admin Propiedad Operations, Security Boundaries & Zero-Trust assertions passed.")
