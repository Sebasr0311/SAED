# Voting Integrity Remediation (C-10 / F10-01, F10-02) - Progress

Date: 2026-10-09
Status: service-level fix done; not committed; not verified against Oracle.

## Fixed

- **Forged vote weight (F10-01).** `AsambleaServiceImpl.emitirVoto` no longer reads `coeficienteVoto` from the request. The stored weight is always `repository.findCoeficienteUnidad(idUnidad)` (`UNIDADES.COEFICIENTE_COPROPIEDAD`). The client value is silently ignored (not rejected) so existing callers and integration tests that still send it keep working. The frontend (`AsambleasAdminPage.jsx`, `ResAsambleasPage.jsx`) never sends it.
- **Duplicate vote -> 409.** The pre-insert duplicate check now returns `409 CONFLICT` (was `400` via `IllegalArgumentException`). A `DuplicateKeyException` from the existing DB constraint `UQ_VOTO_UNIDAD_VOTACION (ID_VOTACION, ID_UNIDAD)` (`database/migrations/V5.0__master_baseline.sql`) is mapped to the same 409 instead of a generic 500.

Already enforced before this slice (now covered by unit tests): unit must belong to the assembly's property; non-admin caller must hold an active assignment/ownership of the unit or an APROBADO proxy (403, no insert); assembly must be `EN_CURSO` or `CONVOCADA`; voting must be `ABIERTA`.

## Evidence

Test: `backend/src/test/java/com/saed/backend/asambleas/service/impl/VoteIntegrityServiceTest.java` (Mockito, no Spring context, no DB).

- RED: 12 tests, 4 failures (forged weight x2 stored 99; duplicate returned `IllegalArgumentException`; `DuplicateKeyException` propagated).
- GREEN: 12 tests, 0 failures, 0 errors.
- CI unit selection (`ci.yml` `-Dtest=` list): 17 classes, 89 tests, 0 errors, 0 failures, 0 skipped.
- `checkstyle:check` and `compile`: pass.

Mocked unit tests do not prove Oracle behavior (RLS, constraint name, ORA-00001 -> `DuplicateKeyException` translation, `FOR UPDATE` locking).

## Changed files

- `backend/src/main/java/com/saed/backend/asambleas/service/impl/AsambleaServiceImpl.java`
- `backend/src/test/java/com/saed/backend/asambleas/service/impl/VoteIntegrityServiceTest.java` (new)

## Open items

- **D03 - CONVOCADA policy.** Votes are still accepted while the assembly is `CONVOCADA`. Left unchanged pending a product decision.
- **Quorum / attendance.** No check that the voting unit registered attendance or that quorum was reached. Not implemented in this slice.
- **Voter eligibility scope.** `usuarioPerteneceAUnidad` accepts any active `USUARIO_ASIGNACIONES` row (tenants/residents included), not only legal owners. Admin roles may register a vote for any unit of the property with an unvalidated `idPersonaVotante`. Both are policy questions.
- **Coefficient fallback.** `findCoeficienteUnidad` returns a hard-coded `0.010000` when the unit coefficient is missing or zero; it should fail instead of fabricating a weight.
- **Oracle proof.** Run `F10VotacionesIntegrationTest` / `F10AsambleasIntegrationTest` against a disposable Oracle to confirm the 409 mapping and stored weights.
