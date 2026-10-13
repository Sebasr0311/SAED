# Rules for remediation writers (Claude agents, Codex)

These rules apply to every agent that changes SAED code during the F0–F12 remediation.

## Scope and safety
- Work only in the worktree/branch you were given. Never commit to another branch, never push, never deploy.
- Never connect to any database (local Oracle on 1521, production, or the isolated Docker container). The coordinator runs Oracle verification.
- Never print or commit secrets. Do not read `.env` files or `local/isolated-oracle/**` private files.
- Code, comments, test names, migrations and docs in English.
- Product decisions are fixed in `docs/remediation/decisions.md` (DEC-xx). Follow them; do not invent policy. If a requirement is genuinely ambiguous and not covered, implement the most conservative safe behavior and report it.

## Files you must NOT edit (shared, owned by the coordinator)
- `backend/src/main/java/com/saed/backend/common/exception/GlobalExceptionHandler.java` — use existing mappings: `NoSuchElementException` → 404, `IllegalArgumentException` → 400, `org.springframework.security.access.AccessDeniedException` → 403, `org.springframework.web.server.ResponseStatusException` with an explicit status (e.g. 409).
- `backend/src/main/java/com/saed/backend/security/**`, `SecurityConfig`, `application*.yml` (unless your slice is explicitly F0 config).
- `scripts/verification/**`, `.github/workflows/**` (unless your slice is explicitly F0/CI).
- Existing migrations `database/migrations/V5.0`…`V5.32`. Never edit an applied migration; add a new one.

## Migrations
- Use only the version range assigned to you in your prompt.
- Must apply as a NON-exempt schema owner under active Oracle VPD with context `PKG_SAED_SESSION.SET_BOOTSTRAP_CONTEXT(1); PKG_SAED_SESSION.SET_CONTEXT(1, 1, 1, 'SUPERADMIN');` (start the file with that block, no exception swallowing). Verified pitfalls:
  - Never `ALTER TABLE … ADD (col … DEFAULT <non-constant>)` (SYSDATE/SYSTIMESTAMP/CURRENT_TIMESTAMP) on an existing table → ORA-28133. Add the column, then `MODIFY (col DEFAULT …)`. (New tables may use such defaults in `CREATE TABLE`.)
  - Never `MERGE` into a policy-protected table (ORA-28132). Use `INSERT … SELECT … WHERE NOT EXISTS` and `UPDATE`.
  - Indexes: check `USER_INDEXES` by name AND tolerate only `SQLCODE = -1408` (TIMESTAMP WITH TIME ZONE columns are indexed as `SYS_EXTRACT_UTC(col)`).
  - `DBMS_RLS.ADD_POLICY` / `ADD_GROUPED_POLICY`: policy names ≤ 30 characters; use `update_check => TRUE` (Oracle 18 rejects `FALSE` with ORA-28104 for these statement types); check `USER_POLICIES` first; object/function schema `NULL` (current owner); new tenant tables MUST get a policy using `PKG_SAED_SECURITY_RLS.FN_FILTRO_PROPIEDAD` or `FN_FILTRO_ORGANIZACION`, and the table needs the column that predicate uses (`ID_PROPIEDAD` / `ID_ORGANIZACION`).
  - Idempotent: check `USER_TABLES`, `USER_TAB_COLS`, `USER_CONSTRAINTS`, `USER_INDEXES`, `USER_POLICIES`, `USER_TRIGGERS` before each change.
  - No `WHEN OTHERS THEN NULL`. Tolerate only specific, commented SQLCODEs.
  - PL/SQL blocks end with `/` on its own line; no `&`; no blank lines inside a single SQL statement.
- Never add startup DDL (`@PostConstruct`, runners). `StartupDdlGuardTest` enforces this.

## Code rules
- Strict TDD: write the failing test first, run it, record the RED output, then implement, then GREEN.
- No swallowed exceptions (`catch (Exception ignored) {}`, `catch … return emptyList()`), no raw `ex.getMessage()` from database exceptions in logs or responses.
- Tenant scope always comes from `SaedContextHolder` (server side), never from client-supplied organization/property/unit/person ids without verification. No SUPERADMIN/bootstrap context elevation for tenant operations.
- Not-found and out-of-scope must be indistinguishable (404, or 403 where the codebase already uses it).
- Unit tests use Mockito / WebMvc slices, no database.

## Build and verification commands
From `backend/`:
```
export JAVA_HOME="$HOME/.jdks/ms-17.0.19"; export PATH="$JAVA_HOME/bin:$PATH"
rm -rf target/surefire-reports
DB_URL="jdbc:oracle:thin:@localhost:1/x" ./mvnw -o -q compile checkstyle:check test -Dtest="<classes>"
```
Always delete `target/surefire-reports` before a run and read counts from the fresh `TEST-*.xml` files (stale reports have produced false GREEN). Also run the CI selection listed in `.github/workflows/ci.yml` plus every remediation test class you touched.
Frontend (from `frontend/`): `npm run lint` and `npm run build` if you change frontend files (do not install packages).

## Deliverable
- Commits on your branch, one per work unit, conventional commit messages, no AI attribution lines.
- A section in `docs/remediation/<phase>-progress.md`: fixed items with audit IDs, RED/GREEN counts, changed files, migrations added, open items.
- Final report (under 300 words) with the same information.
