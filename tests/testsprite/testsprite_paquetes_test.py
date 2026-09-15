import os
import requests

TARGET_URL = os.environ.get("TARGET_URL", "https://saed-backend.onrender.com")

def test_paquetes_delivery_and_pin_audit():
    base_url = TARGET_URL.rstrip("/")

    # 1. Unauthenticated access to packages endpoint must be rejected
    r_unauth = requests.get(f"{base_url}/api/v1/paquetes", timeout=15)
    assert r_unauth.status_code in [401, 403], f"Expected 401/403 for unauthenticated, got {r_unauth.status_code}"

    # 2. Login as Portero (portero01)
    r_login_portero = requests.post(f"{base_url}/api/v1/auth/login", json={
        "username": "portero01",
        "password": "admin123"
    }, timeout=15)
    assert r_login_portero.status_code == 200, f"Login failed for portero01: {r_login_portero.status_code} - {r_login_portero.text}"
    token_portero = r_login_portero.json()["token"]
    headers_portero = {
        "Authorization": f"Bearer {token_portero}",
        "Content-Type": "application/json"
    }

    # 3. Login as Residente (camartinez)
    r_login_residente = requests.post(f"{base_url}/api/v1/auth/login", json={
        "username": "camartinez",
        "password": "admin123"
    }, timeout=15)
    assert r_login_residente.status_code == 200, f"Login failed for camartinez: {r_login_residente.status_code} - {r_login_residente.text}"
    token_residente = r_login_residente.json()["token"]
    headers_residente = {
        "Authorization": f"Bearer {token_residente}",
        "Content-Type": "application/json"
    }

    # 4. Portero registers a new package for Unit 1
    pkg_payload = {
        "idUnidad": 1,
        "idPorteria": 1,
        "empresaMensajeria": "TestSprite Verified Logistics",
        "descripcion": "Paquete de verificacion TestSprite Fase 8",
        "tamano": "PEQUENO"
    }
    r_create = requests.post(f"{base_url}/api/v1/paquetes", json=pkg_payload, headers=headers_portero, timeout=15)
    assert r_create.status_code in [200, 201], f"Package creation failed: {r_create.status_code} - {r_create.text}"
    pkg_data = r_create.json()
    assert "idPaquete" in pkg_data, "idPaquete missing in creation response"
    paquete_id = pkg_data["idPaquete"]
    assert "codigoRetiroPin" in pkg_data, "codigoRetiroPin missing in creation response"
    pin_generado = pkg_data["codigoRetiroPin"]
    assert pkg_data.get("estado") == "RECIBIDO", f"Expected estado RECIBIDO, got {pkg_data.get('estado')}"

    # 5. Residente can view the newly registered package
    r_res_pkgs = requests.get(f"{base_url}/api/v1/paquetes", headers=headers_residente, timeout=15)
    assert r_res_pkgs.status_code == 200, f"GET /paquetes failed for resident: {r_res_pkgs.status_code}"
    res_pkg_ids = [p.get("idPaquete") for p in r_res_pkgs.json()]
    assert paquete_id in res_pkg_ids, f"Package {paquete_id} not visible to resident in Unit 1"

    # 6. Anti-fraud: Delivery attempt with WRONG PIN must fail with 400 Bad Request
    wrong_delivery_payload = {
        "codigoRetiroPin": "999999",
        "idPersonaRecibe": 1,
        "idPorteria": 1
    }
    r_wrong_pin = requests.post(f"{base_url}/api/v1/paquetes/{paquete_id}/entrega", json=wrong_delivery_payload, headers=headers_portero, timeout=15)
    assert r_wrong_pin.status_code == 400, f"Expected 400 for wrong PIN, got {r_wrong_pin.status_code} - {r_wrong_pin.text}"

    # 7. Delivery with CORRECT PIN must succeed and update status to ENTREGADO
    valid_delivery_payload = {
        "codigoRetiroPin": pin_generado,
        "idPersonaRecibe": 1,
        "idPorteria": 1,
        "firmaUrl": "https://saed.app/signatures/test-delivery.png"
    }
    r_valid_delivery = requests.post(f"{base_url}/api/v1/paquetes/{paquete_id}/entrega", json=valid_delivery_payload, headers=headers_portero, timeout=15)
    assert r_valid_delivery.status_code == 200, f"Delivery failed with valid PIN: {r_valid_delivery.status_code} - {r_valid_delivery.text}"
    delivery_data = r_valid_delivery.json()
    assert delivery_data.get("estado") == "ENTREGADO", f"Expected ENTREGADO, got {delivery_data.get('estado')}"

    # 8. Idempotency & Replay Protection: Second delivery attempt of already-delivered package must be rejected
    r_replay = requests.post(f"{base_url}/api/v1/paquetes/{paquete_id}/entrega", json=valid_delivery_payload, headers=headers_portero, timeout=15)
    assert r_replay.status_code in [400, 409], f"Replay delivery must be rejected with 400/409, got {r_replay.status_code}"

if __name__ == "__main__":
    test_paquetes_delivery_and_pin_audit()
    print("SUCCESS: 100% of Paquetes Delivery, PIN Verification & Perimeter assertions passed.")
