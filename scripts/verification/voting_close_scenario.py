"""F10-12 voting-close verification against the isolated Oracle install and backend (DEC-03, DEC-11).

Seeds (SYSDBA inside the container) nine scenario units of property 91001 whose coefficients, together with
the fixture units set INACTIVA for the duration of the run, sum to 1.0, plus their owner rows. Everything the
application itself does goes through the real HTTP API: create/start assemblies, register attendance, open
votings, vote, close, file proxies. Fixture units are restored in a finally block. Never prints passwords,
tokens or response bodies.

Usage: voting_close_scenario.py <state> <container-name> <port>
"""
import json
import pathlib
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from decimal import Decimal

REPO = pathlib.Path(__file__).resolve().parents[2]
STATE, CONTAINER, PORT = sys.argv[1], sys.argv[2], sys.argv[3]
state = json.loads((REPO / "local" / "isolated-oracle" / STATE / "private-state.json").read_text(encoding="utf-8"))
BASE = f"http://127.0.0.1:{PORT}"
RUN = uuid.uuid4().hex[:8]
PROPERTY = 91001
FIXTURE_UNITS = (91001, 91011)  # deactivated during the run so the active coefficients sum to exactly 1.0
OWNER_PERSONA = 91003           # persona of iso_0_admin_propiedad; legal owner of every scenario unit
RESIDENT_PERSONA = 91005        # owner of fixture unit 91001 (iso_0_residente)
COHABITANT_PERSONA = 91006      # persona of iso_0_residente_convivencia (used as proxy, in the organization)

# unit key -> (id, coefficient). Sum = 1.00
UNITS = {
    "A1": (91701, "0.26"), "A2": (91702, "0.05"),
    "B1": (91703, "0.15"), "B2": (91704, "0.05"),
    "C": (91705, "0.09"),
    "D1": (91706, "0.24"), "D2": (91707, "0.05"),
    "E1": (91708, "0.05"), "E2": (91709, "0.06"),
}
failures = []


def sql(statements):
    script = ("CONNECT / AS SYSDBA\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 300\n"
              "ALTER SESSION SET CONTAINER=XEPDB1;\nALTER SESSION SET CURRENT_SCHEMA=SAED_ISO_OWNER;\n"
              + statements + "\nEXIT\n")
    out = subprocess.run(["docker", "exec", "-i", CONTAINER, "sqlplus", "-s", "/nolog"], input=script,
                         capture_output=True, text=True, encoding="utf-8")
    return out.stdout


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            return res.status, json.loads(res.read() or b"null")
    except urllib.error.HTTPError as err:
        try:
            return err.code, json.loads(err.read() or b"null")
        except ValueError:
            return err.code, None


def login(username):
    acc = next(a for a in state["accounts"] if a["username"] == username)
    status, body = call("POST", "/api/v1/auth/login", {"username": acc["email"], "password": acc["password"]})
    assert status == 200, (username, status)
    return next(v for k, v in body.items() if "token" in k.lower() and isinstance(v, str))


def expect(label, condition):
    print(f"  {'PASS' if condition else 'FAIL'} {label}")
    if not condition:
        failures.append(label)


def close_to(actual, expected, tol="0.0001"):
    return actual is not None and abs(Decimal(str(actual)) - Decimal(str(expected))) <= Decimal(tol)


def seed_units():
    tipo_out = sql(f"SELECT 'TIPO ' || ID_TIPO_UNIDAD FROM UNIDADES WHERE ID_UNIDAD = {FIXTURE_UNITS[0]};")
    tipo = next(l.split()[1] for l in tipo_out.splitlines() if l.startswith("TIPO "))
    prior = sql("SELECT 'PRIOR ' || ID_UNIDAD || ' ' || ESTADO FROM UNIDADES WHERE ID_UNIDAD IN (%s);"
                % ",".join(map(str, FIXTURE_UNITS)))
    prior_states = {int(l.split()[1]): l.split()[2] for l in prior.splitlines() if l.startswith("PRIOR ")}
    stmts = []
    for key, (uid, coef) in UNITS.items():
        stmts.append(f"""MERGE INTO UNIDADES u USING (SELECT {uid} AS ID FROM DUAL) s ON (u.ID_UNIDAD = s.ID)
WHEN MATCHED THEN UPDATE SET COEFICIENTE_COPROPIEDAD = {coef}, ESTADO = 'ACTIVA', ID_PROPIEDAD = {PROPERTY}
WHEN NOT MATCHED THEN INSERT (ID_UNIDAD, ID_PROPIEDAD, ID_TIPO_UNIDAD, IDENTIFICADOR, COEFICIENTE_COPROPIEDAD, ESTADO)
VALUES ({uid}, {PROPERTY}, {tipo}, 'ISO-VOTE-{key}', {coef}, 'ACTIVA');
INSERT INTO PROPIETARIOS_UNIDAD (ID_UNIDAD, ID_PERSONA, PORCENTAJE_PROPIEDAD, ES_PRINCIPAL, ESTADO)
SELECT {uid}, {OWNER_PERSONA}, 100, 'S', 'ACTIVO' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM PROPIETARIOS_UNIDAD WHERE ID_UNIDAD = {uid} AND ID_PERSONA = {OWNER_PERSONA} AND ESTADO = 'ACTIVO');""")
    stmts.append("UPDATE UNIDADES SET ESTADO = 'INACTIVA' WHERE ID_UNIDAD IN (%s);" % ",".join(map(str, FIXTURE_UNITS)))
    stmts.append("COMMIT;")
    out = sql("\n".join(stmts))
    errors = [l for l in out.splitlines() if "ORA-" in l or "SP2-" in l]
    return prior_states, errors


def restore_units(prior_states):
    stmts = ["UPDATE UNIDADES SET ESTADO = 'INACTIVA' WHERE ID_UNIDAD IN (%s);" % ",".join(str(u[0]) for u in UNITS.values())]
    for uid, estado in prior_states.items():
        stmts.append(f"UPDATE UNIDADES SET ESTADO = '{estado}' WHERE ID_UNIDAD = {uid};")
    stmts.append("COMMIT;")
    sql("\n".join(stmts))


def votacion_row(vid):
    out = sql("SELECT 'ROW ' || RESULTADO || '|' || NVL(OPCION_GANADORA, '-') || '|' || PESO_SI || '|' || PESO_REPRESENTADO || '|' "
              f"|| BASE_COEFICIENTES || '|' || PORCENTAJE_SI || '|' || ESTADO FROM VOTACIONES WHERE ID_VOTACION = {vid};")
    line = next((l for l in out.splitlines() if l.startswith("ROW ")), None)
    if line is None:
        return None
    resultado, ganadora, peso_si, representado, base, pct, estado = line[4:].split("|")
    return {"resultado": resultado, "ganadora": ganadora, "peso_si": peso_si, "representado": representado,
            "base": base, "pct": pct, "estado": estado}


def find_id(body, *names):
    return next((body[n] for n in names if isinstance(body, dict) and isinstance(body.get(n), int)), None)


def new_assembly(admin, label, attending):
    status, body = call("POST", "/api/v1/asambleas", {
        "idPropiedad": PROPERTY, "tipo": "EXTRAORDINARIA", "modalidad": "PRESENCIAL",
        "titulo": f"Verificacion votacion {label} {RUN}", "convocatoriaNumero": 1,
        "fechaHoraPrimeraConv": "2030-01-15T10:00:00", "lugarOEnlace": "Salon comunal",
        "ordenDelDia": "1. Votaciones sinteticas"}, admin)
    aid = find_id(body, "idAsamblea", "id")
    expect(f"[{label}] assembly created (status {status}, id {aid})", status == 201 and aid)
    status, _ = call("PUT", f"/api/v1/asambleas/{aid}/estado", {"nuevoEstado": "EN_CURSO"}, admin)
    expect(f"[{label}] assembly started EN_CURSO (status {status})", status == 200)
    for key in attending:
        status, _ = call("POST", f"/api/v1/asambleas/{aid}/asistencias", {
            "idUnidad": UNITS[key][0], "idPersonaAsistente": OWNER_PERSONA, "esPropietarioDirecto": "S"}, admin)
        if status != 201:
            expect(f"[{label}] attendance of {key} registered (status {status})", False)
    return aid


def new_voting(admin, aid, label, majority, punto):
    status, body = call("POST", f"/api/v1/asambleas/{aid}/votaciones", {
        "puntoOrdenDia": punto, "titulo": f"Punto {label} {RUN}", "tipoMayoriaRequerida": majority}, admin)
    vid = find_id(body, "idVotacion", "id")
    expect(f"[{label}] voting opened {majority} (status {status}, id {vid})", status == 201 and vid)
    return vid


def cast(admin, aid, vid, label, votes):
    bad = []
    for key, option in votes.items():
        status, _ = call("POST", f"/api/v1/asambleas/{aid}/votaciones/{vid}/votar",
                         {"idUnidad": UNITS[key][0], "opcionVoto": option}, admin)
        if status != 201:
            bad.append((key, status))
    expect(f"[{label}] {len(votes)} votes accepted {bad if bad else ''}".strip(), not bad)


def close_and_check(admin, aid, vid, label, resultado, ganadora, si, representado, pct):
    status, closed = call("PUT", f"/api/v1/asambleas/{aid}/votaciones/{vid}/cerrar", {}, admin)
    expect(f"[{label}] close accepted (status {status})", status == 200)
    status, listing = call("GET", f"/api/v1/asambleas/{aid}/votaciones", None, admin)
    api = next((v for v in (listing or []) if v.get("idVotacion") == vid), {})
    expect(f"[{label}] API resultado={api.get('resultado')} expected {resultado}", api.get("resultado") == resultado)
    expect(f"[{label}] API opcionGanadora={api.get('opcionGanadora')} expected {ganadora}", api.get("opcionGanadora") == ganadora)
    expect(f"[{label}] API pesoRepresentado={api.get('pesoRepresentado')} expected {representado}", close_to(api.get("pesoRepresentado"), representado))
    expect(f"[{label}] API baseCoeficientes={api.get('baseCoeficientes')} expected 1.00", close_to(api.get("baseCoeficientes"), "1.00"))
    expect(f"[{label}] API porcentajeSi={api.get('porcentajeSi')} expected {pct}", close_to(api.get("porcentajeSi"), pct, "0.01"))
    row = votacion_row(vid)
    expect(f"[{label}] DB row found", row is not None)
    if row:
        expect(f"[{label}] DB RESULTADO={row['resultado']} ESTADO={row['estado']} OPCION_GANADORA={row['ganadora']}",
               row["resultado"] == resultado and row["estado"] == "CERRADA" and row["ganadora"] == ganadora)
        expect(f"[{label}] DB PESO_SI={row['peso_si']} PESO_REPRESENTADO={row['representado']} BASE={row['base']} PORCENTAJE_SI={row['pct']}",
               close_to(row["peso_si"], si) and close_to(row["representado"], representado)
               and close_to(row["base"], "1.00") and close_to(row["pct"], pct, "0.01"))
        expect("[%s] DB columns match the API values" % label,
               close_to(row["peso_si"], api.get("votosSi") if api.get("votosSi") is not None else -1)
               and close_to(row["representado"], api.get("pesoRepresentado") if api.get("pesoRepresentado") is not None else -1)
               and close_to(row["base"], api.get("baseCoeficientes") if api.get("baseCoeficientes") is not None else -1)
               and close_to(row["pct"], api.get("porcentajeSi") if api.get("porcentajeSi") is not None else -1, "0.01")
               and row["resultado"] == api.get("resultado"))
    return row


print(f"Run {RUN}: seeding nine scenario units of property {PROPERTY} (fixture units {FIXTURE_UNITS} inactive during the run)")
prior_states, seed_errors = seed_units()
if seed_errors:
    print("  seeding failed:", seed_errors)
    restore_units(prior_states)
    sys.exit(1)
sum_out = sql(f"SELECT 'SUM ' || SUM(COEFICIENTE_COPROPIEDAD) FROM UNIDADES WHERE ID_PROPIEDAD = {PROPERTY} AND ESTADO = 'ACTIVA';")
active_sum = next(l.split()[1] for l in sum_out.splitlines() if l.startswith("SUM "))
expect(f"active coefficients of the property sum to 1.0 (got {active_sum})", close_to(active_sum, "1"))

try:
    admin = login("iso_0_admin_propiedad")
    resident = login("iso_0_residente")

    print("1a. SIMPLE_50_MAS_1: represented 0.60, SI 0.31, NO 0.20, 0.09 present without voting")
    a1 = new_assembly(admin, "1a", ["A1", "A2", "B1", "B2", "C"])
    v1a = new_voting(admin, a1, "1a", "SIMPLE_50_MAS_1", 1)
    cast(admin, a1, v1a, "1a", {"A1": "SI", "A2": "SI", "B1": "NO", "B2": "NO"})
    close_and_check(admin, a1, v1a, "1a", "APROBADA", "SI", "0.31", "0.60", "51.6667")

    print("1b. SIMPLE_50_MAS_1: SI 0.29 beats NO 0.20 but is below half of the represented 0.60")
    a2 = new_assembly(admin, "1b", ["D1", "D2", "B1", "B2", "E1", "E2"])
    v1b = new_voting(admin, a2, "1b", "SIMPLE_50_MAS_1", 1)
    cast(admin, a2, v1b, "1b", {"D1": "SI", "D2": "SI", "B1": "NO", "B2": "NO"})
    close_and_check(admin, a2, v1b, "1b", "RECHAZADA", "SI", "0.29", "0.60", "48.3333")

    print("2a. CALIFICADA_70_PCT: SI 0.65 of represented 0.80, base 1.00")
    a3 = new_assembly(admin, "2a", ["A1", "A2", "D1", "D2", "E1", "B1"])
    v2a = new_voting(admin, a3, "2a", "CALIFICADA_70_PCT", 1)
    cast(admin, a3, v2a, "2a", {"A1": "SI", "A2": "SI", "D1": "SI", "D2": "SI", "E1": "SI", "B1": "NO"})
    close_and_check(admin, a3, v2a, "2a", "RECHAZADA", "SI", "0.65", "0.80", "65.0000")

    print("2b. CALIFICADA_70_PCT: SI 0.70 of represented 0.85, base 1.00")
    a4 = new_assembly(admin, "2b", ["A1", "A2", "D1", "D2", "E1", "B2", "B1"])
    v2b = new_voting(admin, a4, "2b", "CALIFICADA_70_PCT", 1)
    cast(admin, a4, v2b, "2b", {"A1": "SI", "A2": "SI", "D1": "SI", "D2": "SI", "E1": "SI", "B2": "SI", "B1": "NO"})
    close_and_check(admin, a4, v2b, "2b", "APROBADA", "SI", "0.70", "0.85", "70.0000")

    print("3. closing twice")
    before = votacion_row(v1a)
    status, _ = call("PUT", f"/api/v1/asambleas/{a1}/votaciones/{v1a}/cerrar", {}, admin)
    expect(f"second close rejected (status {status})", status in (400, 409))
    after = votacion_row(v1a)
    expect("stored result unchanged after the second close", before is not None and before == after)
    status, _ = call("PUT", f"/api/v1/asambleas/{a1}/votaciones/{v1a}/anular", {}, admin)
    expect(f"annulling a closed voting rejected (status {status})", status in (400, 409))
    expect("stored result unchanged after the annul attempt", before == votacion_row(v1a))

    print("5. proxy authorization")
    other = a1
    status, _ = call("POST", f"/api/v1/asambleas/{other}/poderes", {
        "idUnidad": UNITS["A1"][0], "idPersonaPropietario": RESIDENT_PERSONA, "idPersonaApoderado": COHABITANT_PERSONA}, resident)
    expect(f"resident claiming a unit they do not own (as grantor) rejected 403 (status {status})", status == 403)
    status, _ = call("POST", f"/api/v1/asambleas/{other}/poderes", {
        "idUnidad": UNITS["A1"][0], "idPersonaPropietario": OWNER_PERSONA, "idPersonaApoderado": RESIDENT_PERSONA}, resident)
    expect(f"resident filing in the name of the real owner rejected 403 (status {status})", status == 403)
    status, body = call("POST", f"/api/v1/asambleas/{other}/poderes", {
        "idUnidad": FIXTURE_UNITS[0], "idPersonaPropietario": RESIDENT_PERSONA, "idPersonaApoderado": COHABITANT_PERSONA}, resident)
    expect(f"real owner filing for their own unit accepted 201 (status {status})", status == 201)
finally:
    restore_units(prior_states)

print(f"\nFAILURES: {len(failures)}")
for item in failures:
    print("  -", item)
sys.exit(1 if failures else 0)
