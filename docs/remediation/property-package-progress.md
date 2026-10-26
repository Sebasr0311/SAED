# Property deletion and package PIN remediation (C-07, C-11)

Date: 2026-10-09
Status: fixed in working tree, not committed, not deployed.

## C-07 (F4-H01): destructive property deletion

Fixed:
- `PropertyRepository.findProtectedHistory` checks 15 history tables: CUOTAS, PAGOS,
  TRANSACCIONES_PAGO, CARTERA, MULTAS, SANCIONES, CONTRATOS, PAZ_Y_SALVOS, ASAMBLEAS,
  VOTOS, ASISTENCIAS_ASAMBLEA, PODERES_REPRESENTACION, GASTOS, CONCILIACIONES and
  REGISTROS_ACCESO.
- `PropertyDeletionService` refuses with `PropertyHasHistoryException`, a subclass of
  `IllegalStateException` that the global handler returns as 409 CONFLICT. It refuses at
  two points: in `requestDeletion`, before any OTP is issued, and again in
  `confirmAndExecuteDeletion`, before the challenge is consumed. The message points to
  deactivation through the existing `PATCH /api/v1/properties/{id}/status` with `INACTIVA`.
  No new endpoint was added.
- `deletePropertyCascade` no longer swallows exceptions. It only deletes operational rows
  (PAQUETES, RESERVAS, UNIDADES) and then PROPIEDADES. It never touches the history tables.
- `confirmAndExecuteDeletion` is `@Transactional(rollbackFor = Exception.class)`, so the
  history check and the cascade run in one transaction.
- F4-H02: the log line that printed the raw OTP when the email failed was removed. The email
  address is now masked in that line.

## C-11 (F8-01): package PIN exposed by API

Fixed:
- `PaqueteDTO.codigoRetiroPin` is `@JsonInclude(NON_NULL)`. `withoutPin()` returns a copy
  without the PIN.
- `PaquetesServiceImpl.forCaller` returns the PIN only to a unit-scoped resident whose
  `unitId` matches the package unit. Every other caller (portero, admin, missing context) gets
  the redacted copy. This applies to list, by-unit, detail, register, update and deliver. It
  therefore covers `PaquetesController` and the `BuzonController` package endpoints.
- Delivery verification was already server-side (`registrarEntrega` compares the PIN), so the
  frontend delivery call is unchanged.
- The PIN is not logged anywhere in the package flow. Resident notifications still carry it
  on purpose, scoped per recipient user.
- Rate limiting already exists: 3 failed attempts lock the package
  (`INTENTOS_FALLIDOS_PIN`, incremented with REQUIRES_NEW). It was not changed.

Frontend: removed the dead "PIN de Seguridad" block from `PaquetesAdminPage.jsx`.
`npm run lint` gave 0 errors (342 warnings, none in that file). `npm run build` passed.

## Tests

- RED: 21 new tests, 15 failing on assertions with 0 errors. The other 6 confirmed behavior
  that already existed: server-side PIN check, unit isolation, and propagation of a mocked
  failure.
- GREEN: the 21 new tests pass. The CI `-Dtest` list plus the new tests: 20 suites,
  110 tests, 0 failures, 0 errors. `compile checkstyle:check` passed.
- `PackageDeliverySecurityIntegrationTest` and `Mvp05PaqueteriaParqueaderosTest` (both DB-backed)
  now read the PIN from PAQUETES instead of from the portero response. They were compiled but
  not run.

## Open items

- Mocked tests do not prove Oracle behavior. Still unverified against Oracle: the
  history-check SQL, the `CARTERA` columns `ID_PROPIEDAD`/`ID_UNIDAD`, FK ordering in the
  cascade, VPD visibility for the history checks, and real rollback.
- If a history table or column is missing, the check fails closed (500) and deletion is blocked.
- REGISTROS_ACCESO is treated as audit history. Any property that has ever been used will
  therefore be refused for hard deletion. This needs a product decision.
- Other children of UNIDADES (assignments, residents) are not deleted. Their FKs now surface
  as an error instead of a silent partial delete.
- `getPaquetesByUnidad` for staff has no property-ownership check (cross-property IDOR risk).
  It is out of scope here.
- The PIN is visible to every resident of the recipient unit, not only to the named
  recipient person. This matches the existing notification design.
