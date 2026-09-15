import os
import requests

TARGET_URL = os.environ.get("TARGET_URL", "https://saed-backend.onrender.com")

def test_portero_full_audit():
    base_url = TARGET_URL.rstrip("/")

    # 1. Unauthenticated request to sensitive guardhouse endpoint must be rejected
    r_unauth = requests.get(f"{base_url}/api/v1/porteria/propiedades/1/registros", timeout=15)
    assert r_unauth.status_code in [401, 403], f"Expected 401/403 for unauthenticated, got {r_unauth.status_code}"

    # 2. Login as Portero (portero01)
    r_login = requests.post(f"{base_url}/api/v1/auth/login", json={
        "username": "portero01",
        "password": "admin123"
    }, timeout=15)
    assert r_login.status_code == 200, f"Login failed for portero01: {r_login.status_code} - {r_login.text}"
    auth_data = r_login.json()
    assert "token" in auth_data, "Token missing in login response"
    token = auth_data["token"]
    user_info = auth_data.get("usuario", {})
    assert user_info.get("rol") == "PORTERO", f"Expected role PORTERO, got {user_info.get('rol')}"
    assert user_info.get("alcance") == "PROPIEDAD", f"Expected scope PROPIEDAD, got {user_info.get('alcance')}"

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
    assert isinstance(contexts, list) and len(contexts) > 0, "Expected at least one context for portero"

    # 4. Guardhouse Access Registries for Property 1
    r_registros = requests.get(f"{base_url}/api/v1/porteria/propiedades/1/registros", headers=headers, timeout=15)
    assert r_registros.status_code == 200, f"GET registros failed: {r_registros.status_code}"
    assert isinstance(r_registros.json(), list), "Registros must be a list"

    # 5. Packages in Guardhouse
    r_packages = requests.get(f"{base_url}/api/v1/paquetes", headers=headers, timeout=15)
    assert r_packages.status_code == 200, f"GET paquetes failed: {r_packages.status_code}"

    # 6. Unit Inhabitants & Owners (Guardhouse needs to verify identities)
    r_inhabitants = requests.get(f"{base_url}/api/v1/units/1/residents", headers=headers, timeout=15)
    assert r_inhabitants.status_code == 200, f"GET residents failed: {r_inhabitants.status_code}"

    r_owners = requests.get(f"{base_url}/api/v1/units/1/owners", headers=headers, timeout=15)
    assert r_owners.status_code == 200, f"GET owners failed: {r_owners.status_code}"

    # 7. Visits Summary for Guardhouse (Property 1)
    r_visits = requests.get(f"{base_url}/api/v1/porteria/visitas-resumen", headers=headers, timeout=15)
    assert r_visits.status_code == 200, f"GET visitas-resumen failed: {r_visits.status_code}"

    # 7b. Privacy boundary: Portero cannot peek into unit's private visit agenda
    r_private_unit_visits = requests.get(f"{base_url}/api/v1/porteria/unidades/1/visitas", headers=headers, timeout=15)
    assert r_private_unit_visits.status_code == 403, f"Expected 403 for private unit visits, got {r_private_unit_visits.status_code}"

    # 8. Parqueaderos
    r_parqueaderos = requests.get(f"{base_url}/api/v1/parqueaderos", headers=headers, timeout=15)
    assert r_parqueaderos.status_code == 200, f"GET parqueaderos failed: {r_parqueaderos.status_code}"

    # 9. P2-02 CRITICAL SECURITY BOUNDARY: PORTERO is strictly forbidden from changing password
    r_pwd = requests.post(f"{base_url}/api/v1/me/change-password", json={
        "currentPassword": "admin123",
        "newPassword": "NewPassword123!"
    }, headers=headers, timeout=15)
    assert r_pwd.status_code == 403, f"Expected 403 Forbidden for portero change-password, got {r_pwd.status_code}"

    # 10. Zero-Trust Administrative Isolation:
    # Portero cannot list all admin reservations
    r_admin_reservas = requests.get(f"{base_url}/api/v1/reservas/todas", headers=headers, timeout=15)
    assert r_admin_reservas.status_code == 403, f"Expected 403 for admin reservas, got {r_admin_reservas.status_code}"

    # Portero cannot access platform-level organizations
    r_orgs = requests.get(f"{base_url}/api/v1/organizations", headers=headers, timeout=15)
    assert r_orgs.status_code == 403, f"Expected 403 for organizations, got {r_orgs.status_code}"

    # Portero cannot access resident finances
    r_finanzas = requests.get(f"{base_url}/api/v1/residentes/4/dashboard", headers=headers, timeout=15)
    assert r_finanzas.status_code == 403, f"Expected 403 for finanzas, got {r_finanzas.status_code}"

if __name__ == "__main__":
    test_portero_full_audit()
    print("SUCCESS: 100% of Portero Operations, Security Boundaries & Perimeter assertions passed.")
