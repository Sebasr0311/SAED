# Open items F3-F11 (second pass)

Branch `fix/f0-f11-p0-remediation`. Single writer. No database, Docker, push or deploy. Migrations used: V5.54, V5.55, V5.56 (V5.57-V5.59 unused).
Final unit tier (`-Dsaed.test.tier=unit`, clean reports, compile + checkstyle): 131 classes, 1121 tests, 0 failures, 0 errors. Python harness: all tests OK. Frontend lint 0 errors, build OK.

## Commits

| Commit | Item |
|---|---|
| 9ea822b | F11: `/generar` without `formato` uses `formatoSalidaDefecto` (metadata only with `metadata=true` or explicit JSON). RED 5/1 -> GREEN 5/0 |
| 146bf89 | Security review 1-3: persons linked to a unit must belong to the caller's organization (404 otherwise); import skips unit assignment for foreign identities; legacy persons without organization count only when visible in scope |
| 6850c86 | F6 + review 4: `createContrato` and contract participants run in the caller's context (no SUPERADMIN elevation, errors propagate, foreign = missing = 404, co-tenants must belong to the organization); payments above the balance (or with unknown balance) rejected with 400. RED 12/5 |
| f8fb48e | P0 RLS: VOTOS, ACCIONES_/EJECUCIONES_AUTOMATIZACION, MEMBRESIAS_HISTORIAL filtered through their parent (V5.54); residents read only their unit's or own votes. Harness test `test_rls_policy_columns.py` RED 3F+1E -> GREEN 4/4 |
| 3cf15e4 | Review 7: `SP_REVOCAR_ACCESO_UNIDAD` requires an in-force titular assignment and an active titular residence (V5.55, `__sec_`) |
| b2aaa0f | Review 5-6: Wompi signature must cover id, status and amount; optional provider confirmation behind `WompiTransactionVerifier` (disabled by default); public paz y salvo verification returns authenticity only, 192-bit codes, context errors not swallowed. RED 7/7 |
| 9aef712 | F3: rate limit on public onboarding (per IP, per tax id, per e-mail; bounded, TTL). |
| 2b24e1d | F4-02: deletion OTP challenges persisted in `DESAFIOS_ELIM_PROPIEDAD` (V5.56: hash only, expiry by DB clock, atomic conditional updates, ADD_POLICY FN_FILTRO_PROPIEDAD, FK cascade). |
| 2ca5ea8 | F05-01: `POST /api/v1/residentes/registro-integral` (person, guardian, lease, unit link, account in one transaction); residents form switched to it for new residents |
| 6846f5a | F9-09: evidence must be http(s) links or an inline image; UI renders only safe links |
| a817ef0 | F10-10 (power decided once, through its own assembly), F10-12 (closed voting cannot be annulled), F10-19 (real client IP / user agent in audit) |
| cc57cd4 | F10-16: case numbers from MAX under a property row lock. RED 1 -> GREEN 2/2 |

## Needs Oracle verification (coordinator)

- V5.54: new predicate branches compile (package body), ORA-28113 gone for the four tables under every profile; resident vote visibility (own unit / own person only, so resident-facing TOTAL_VOTOS counts only their votes).
- V5.55 (`__sec_`) and V5.56 (tenant table, ADD_POLICY, FK cascade on property deletion, REQUIRES_NEW access under ADMIN_ORGANIZACION context).
- `ContratoParticipanteServiceImpl` / `createContrato` queries under RLS without elevation; ARRENDATARIO flow in the composite endpoint; `UsuarioController.crearUsuario` called inside the registration transaction.
- `generarSiguienteExpediente` `FOR UPDATE` on PROPIEDADES for ADMIN_PROPIEDAD.

## Left open

- F9-05 (INCIDENTES module seed) and F9-06 (RequireModule for activos/mantenimiento/proveedores): the coordinator has uncommitted work on this (V5.90, RequireModuleSeedTest), not duplicated.
- F9-13 PQRS admin/resident screens (large UI, no backend change).
- F10-12 closing result/majority persistence (needs schema), F10-13 resident proxy-voting UI, F10-17 resident policy field minimization.
- Gateway overpayment stays "store and flag SOBREPAGO" (money already collected); manual and ordinary payments are rejected.
- Provider-side Wompi verifier implementation (needs private key and HTTP client) and a shared rate-limit store for multi-instance.
