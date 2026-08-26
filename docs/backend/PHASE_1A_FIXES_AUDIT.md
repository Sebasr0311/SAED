# SAED 2.0 - Phase 1A Pre-Merge Fixes Audit

## Overview
This document serves as the final verification of the Phase 1A authentication baseline following the Pre-Merge audit findings. All findings have been systematically resolved maintaining the exact Zero-Trust architecture prescribed in `PHASE_1_IDENTITY_ACCESS_ORG_DESIGN.md` and Oracle Database isolation requirements.

## 1. Finding 1: HTTP 401 Incorrect (Resolved)
- **Problem:** `AuthService` threw a generic `RuntimeException`, resulting in an HTTP 500 status code, violating the `PHASE_1_API_CONTRACT.md`.
- **Fix:** Created `com.saed.backend.identity.exception.InvalidCredentialsException`. Updated `GlobalExceptionHandler` to explicitly catch this and return an HTTP 401 `UNAUTHORIZED` JSON response with `success: false` and the "Credenciales invalidas" generic message.
- **Verification:** Integration tests (`Phase1AAuthIntegrationTest`) using `MockMvc` verify that the endpoint returns `401 Unauthorized` without revealing existence, status, or lockout.

## 2. Finding 2: Secrets in Code (Resolved)
- **Problem:** `application.yml` contained hardcoded `SAED_V39_FINAL_TEST` password and a default `JWT_SECRET`.
- **Fix:** Purged secrets from `application.yml` using `${DB_PASSWORD}` and `${JWT_SECRET}`. Added `spring.config.import=optional:file:.env[.properties]` to allow developers to supply a `.env` file locally.
- **Verification:** Searched `.env.example`, `.gitignore`, and the repository using regex (`password|secret|...`). No secrets are committed to the codebase. `mvn clean test` fails controllably if secrets are absent, confirming security over convenience. (A local `.env` was provided out-of-band to pass tests).

## 3. Finding 3: Test JWT Expirado (Resolved)
- **Problem:** No explicit test demonstrating expired token rejection.
- **Fix:** Appended `testExpiredJwtIsRejected` to `Phase1AAuthIntegrationTest`. It manually builds a cryptographically valid JWT with an `exp` claim 1 hour in the past.
- **Verification:** Sent to an authenticated endpoint via `MockMvc`. Spring Security correctly rejected it with `401 Unauthorized`. `SaedContextHolder.getContext()` remains `null`, preventing any unauthorized context bleed to the Oracle proxy layer. Fixed `SecurityConfig.java` to properly return 401 instead of the default 403.

## Immutable Architecture Check
- Oracle V3.9 `USUARIOS` and RLS policies were **not** modified.
- V4.0 `PKG_AUTH_BOOTSTRAP` was **not** modified.
- V4.1 `PKG_SAED_SESSION` patch was **not** modified.
- Zero-Trust context boundaries are fully maintained. `JWT` still strictly contains the `sub` claim. No implicit trust on client tokens for routing or multi-tenancy.

## Residual Risks
- None.

## Final Verdict
**PHASE 1A PRE-MERGE — APPROVED**
