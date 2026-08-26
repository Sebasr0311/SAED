# SAED 2.0 - Phase 1A Implementation

## Overview
Phase 1A successfully implements the `V4.0 Authentication Bootstrap` in the Spring Boot backend, following the zero-trust paradigm. The API now exposes the initial authentication mechanism which retrieves basic user identity directly from Oracle, bypassing conventional JPA querying or insecure raw SQL selects.

## Key Changes
1. **AuthService and Repository**
   - Implemented `AuthService` to validate user credentials against Oracle using `PKG_AUTH_BOOTSTRAP.GET_AUTH_DATA`.
   - Replaced dummy implementation in `AuthRepositoryImpl` with a configured `SimpleJdbcCall`.
   - Prevented user enumeration and timing attacks through generic error messages ("Credenciales invalidas") and unconditional BCrypt evaluation.
   - Enforced database lockout mechanisms natively by executing `PKG_AUTH_BOOTSTRAP.REGISTER_LOGIN_FAILURE` (Oracle autonomous transaction).
   - Successful logins call `PKG_AUTH_BOOTSTRAP.REGISTER_LOGIN_SUCCESS` to reset attempt counters.

2. **JWT Stateless Identity**
   - Refactored `JwtProvider.generateIdentityToken(Long idUsuario)` to strictly issue Phase 1A identity tokens.
   - Tokens contain **only** `id_usuario` as the `sub` claim. Tenancy context (roles, organization, property) is strictly excluded, ensuring the UI cannot dictate access limits.

3. **Oracle Integration Testing**
   - Wrote `Phase1AAuthIntegrationTest` executing under the `dev` profile.
   - Targets the live `XEPDB1` Oracle database with a real seeded BCrypt hash user (`integration@saed.com`).
   - Covered login success, invalid passwords, nonexistent users, inactive users, blocked users, and JWT payload inspection.
   - Proven that direct standard queries against `USUARIOS` fail due to `SAED_V39_FINAL_TEST` RLS policies (0 rows returned).

## Restrictions Respected
- **No V3.9 Modification**: `99_seguridad_v2.sql` and `PKG_SAED_SESSION` remain entirely untouched.
- **No Alternative Auth Methods**: Everything routes strictly through `PKG_AUTH_BOOTSTRAP`.
- **Stateless Guarantee**: The Spring Context does not retain the user state or Oracle session across requests; it is fully stateless.

## Next Steps
This concludes Phase 1A. After the PR is merged, we will commence Phase 1B (Spring Security + JWT + Assignment Context), which utilizes `V4.1__core_session_patch.sql` to retrieve context assignments.
