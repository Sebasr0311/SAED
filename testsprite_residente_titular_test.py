import os
import requests

TARGET_URL = os.environ.get("TARGET_URL", "https://saed-backend.onrender.com")

def test_residente_titular_full_audit():
    base_url = TARGET_URL.rstrip("/")
    
    # 1. Unauthenticated request to sensitive residential endpoint must fail
    r_unauth = requests.get(f"{base_url}/api/v1/units/1/residents", timeout=15)
    assert r_unauth.status_code in [401, 403], f"Expected 401/403 for unauthenticated, got {r_unauth.status_code}"

    # 2. Login as Residente Titular (camartinez)
    r_login = requests.post(f"{base_url}/api/v1/auth/login", json={
        "username": "camartinez",
        "password": "admin123"
    }, timeout=15)
    assert r_login.status_code == 200, f"Login failed for camartinez: {r_login.status_code} - {r_login.text}"
    auth_data = r_login.json()
    assert "token" in auth_data, "Token missing in login response"
    token = auth_data["token"]
    user_info = auth_data.get("usuario", {})
    assert user_info.get("rol") == "RESIDENTE", f"Expected role RESIDENTE, got {user_info.get('rol')}"
    assert user_info.get("alcance") == "UNIDAD", f"Expected scope UNIDAD, got {user_info.get('alcance')}"

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
    assert isinstance(contexts, list) and len(contexts) > 0, "Expected at least one context"

    # 4. Inhabitants of Own Unit (Unit 1)
    r_inhabitants = requests.get(f"{base_url}/api/v1/units/1/residents", headers=headers, timeout=15)
    assert r_inhabitants.status_code == 200, f"GET inhabitants failed: {r_inhabitants.status_code}"
    assert isinstance(r_inhabitants.json(), list), "Inhabitants must be a list"

    # 5. Conviviente Quota for Own Unit (Unit 1)
    r_quota = requests.get(f"{base_url}/api/v1/units/1/residents/quota", headers=headers, timeout=15)
    assert r_quota.status_code == 200, f"GET quota failed: {r_quota.status_code}"
    quota_data = r_quota.json()
    assert "limiteConfigurado" in quota_data, "limiteConfigurado missing"
    assert "convivientesActivos" in quota_data, "convivientesActivos missing"
    assert "cuposDisponibles" in quota_data, "cuposDisponibles missing"
    assert "limiteAlcanzado" in quota_data, "limiteAlcanzado missing"

    # 6. Visits of Own Unit (Unit 1)
    r_visits = requests.get(f"{base_url}/api/v1/porteria/unidades/1/visitas", headers=headers, timeout=15)
    assert r_visits.status_code == 200, f"GET visits failed: {r_visits.status_code}"

    # 7. Zonas Comunes
    r_zonas = requests.get(f"{base_url}/api/v1/zonas-comunes", headers=headers, timeout=15)
    assert r_zonas.status_code == 200, f"GET zonas comunes failed: {r_zonas.status_code}"

    # 8. Mis Reservas
    r_reservas = requests.get(f"{base_url}/api/v1/reservas/mis-reservas", headers=headers, timeout=15)
    assert r_reservas.status_code == 200, f"GET mis-reservas failed: {r_reservas.status_code}"

    # 9. Mis Incidentes
    r_incidentes = requests.get(f"{base_url}/api/v1/incidentes/mis-incidentes", headers=headers, timeout=15)
    assert r_incidentes.status_code == 200, f"GET mis-incidentes failed: {r_incidentes.status_code}"

    # 10. Documentos de Residente
    r_docs = requests.get(f"{base_url}/api/v1/documentos/residente", headers=headers, timeout=15)
    assert r_docs.status_code == 200, f"GET documentos failed: {r_docs.status_code}"

    # 11. Security Perimeter - Cross-Unit Attack (IDOR on other unit)
    # Titular of unit 1 cannot see inhabitants or quota of another unit (e.g., unit 999 or 2)
    r_cross_inhabitants = requests.get(f"{base_url}/api/v1/units/999999/residents", headers=headers, timeout=15)
    assert r_cross_inhabitants.status_code in [403, 404], f"Expected 403/404 cross-unit, got {r_cross_inhabitants.status_code}"

    # 12. Security Perimeter - Administrative Breach (Zero Trust)
    # Residente cannot list all reservations of the complex
    r_admin_reservas = requests.get(f"{base_url}/api/v1/reservas/todas", headers=headers, timeout=15)
    assert r_admin_reservas.status_code == 403, f"Expected 403 for admin reservas, got {r_admin_reservas.status_code}"

    # Residente cannot access guardhouse entrance registry
    r_porteria = requests.get(f"{base_url}/api/v1/porteria/propiedades/1/registros", headers=headers, timeout=15)
    assert r_porteria.status_code == 403, f"Expected 403 for guardhouse entry, got {r_porteria.status_code}"

    # Residente cannot access platform-level organizations
    r_orgs = requests.get(f"{base_url}/api/v1/organizations", headers=headers, timeout=15)
    assert r_orgs.status_code == 403, f"Expected 403 for organizations, got {r_orgs.status_code}"

if __name__ == "__main__":
    test_residente_titular_full_audit()
    print("SUCCESS: 100% of Residente Titular Security, Operations & Perimeter assertions passed.")
