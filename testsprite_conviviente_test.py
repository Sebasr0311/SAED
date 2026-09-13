import os
import requests

TARGET_URL = os.environ.get("TARGET_URL", "https://saed-backend.onrender.com")

def test_conviviente_full_audit():
    base_url = TARGET_URL.rstrip("/")
    
    # 1. Unauthenticated requests must be blocked with 401 or 403
    r_unauth = requests.get(f"{base_url}/api/v1/units/1/residents", timeout=15)
    assert r_unauth.status_code in [401, 403], f"Expected 401/403 unauth, got {r_unauth.status_code}"

    # 2. Invalid credentials must return 401
    r_bad = requests.post(f"{base_url}/api/v1/auth/login", json={
        "username": "non_existent_conviviente",
        "password": "WrongPassword123!"
    }, timeout=15)
    assert r_bad.status_code == 401, f"Expected 401 bad credentials, got {r_bad.status_code}"

    # 3. Login as Resident Titular (camartinez)
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

    # 4. Check Conviviente Quota for Unit 1 (matching ConvivienteQuotaDTO)
    r_quota = requests.get(f"{base_url}/api/v1/units/1/residents/quota", headers=headers, timeout=15)
    assert r_quota.status_code == 200, f"GET quota failed: {r_quota.status_code} - {r_quota.text}"
    quota_data = r_quota.json()
    assert "limiteConfigurado" in quota_data, f"limiteConfigurado missing: {quota_data}"
    assert "convivientesActivos" in quota_data, f"convivientesActivos missing: {quota_data}"
    assert "cuposDisponibles" in quota_data, f"cuposDisponibles missing: {quota_data}"
    assert "limiteAlcanzado" in quota_data, f"limiteAlcanzado missing: {quota_data}"

    # 5. Check Inhabitants list for Unit 1
    r_residents = requests.get(f"{base_url}/api/v1/units/1/residents", headers=headers, timeout=15)
    assert r_residents.status_code == 200, f"GET residents failed: {r_residents.status_code} - {r_residents.text}"
    residents_list = r_residents.json()
    assert isinstance(residents_list, list), "Residents response must be a list"

    # 6. Verify Security Perimeter: Resident cannot access SuperAdmin endpoints (403 Forbidden)
    r_forbidden = requests.get(f"{base_url}/api/v1/organizations", headers=headers, timeout=15)
    assert r_forbidden.status_code == 403, f"Expected 403 Forbidden for non-superadmin, got {r_forbidden.status_code}"

    # 7. Verify Resident Profile endpoint
    r_me = requests.get(f"{base_url}/api/v1/me", headers=headers, timeout=15)
    assert r_me.status_code == 200, f"GET /me failed: {r_me.status_code}"

if __name__ == "__main__":
    test_conviviente_full_audit()
    print("SUCCESS: 100% of Conviviente Security, Quota & Authentication assertions passed.")
