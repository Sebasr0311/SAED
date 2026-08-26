# Phase 1C Security Audit

## Objective
Audit the implemented Phase 1C capabilities against the established security and authorization requirements.

## Findings

1. **Context Initialization (STATE 1 to STATE 2)**
   - `JwtAuthenticationFilter` now successfully injects `roleScope` (`alcance`) into `SaedContext` during assignment resolution.
   - Security context accurately tracks `organizationId`, `propertyId`, and `unitId`.

2. **Privilege Escalation Protection**
   - Services validate `currentScope` against `targetRole.getAlcance()`.
   - A Property Admin cannot grant an Organization Admin role.
   - Residents (`UNIDAD` scope) are blocked explicitly from creating any assignments or units.

3. **Context Spoofing Prevention**
   - Any user missing `GLOBAL` scope who tries to create a resource (e.g., Property) with a different `idOrganizacion` will have the payload explicitly overridden by their `SaedContext.getOrganizationId()`.
   - Oracle RLS ultimately enforces tenant isolation, acting as a second layer of defense.

4. **Global Scope State Isolation Fix (CRITICAL BUG FIX)**
   - Discovered that `SaedDataSourceProxy` incorrectly evaluated `SUPERADMIN` (who has `organizationId = null`) as being in `STATE 1: BOOTSTRAP`.
   - This caused Oracle RLS to evaluate `v_state = 'BOOTSTRAP'` and immediately return `1=0` before checking `v_rol = 'SUPERADMIN'`, incorrectly blocking global mutations.
   - Fixed by checking `roleScope == null` instead of `organizationId == null` to distinguish between BOOTSTRAP and BUSINESS states.

5. **V3.9 Integrity**
   - No `@Transactional` spring filters replace the Oracle RLS filter.
   - `PKG_SAED_SECURITY_RLS` handles read/write isolation based on the established session parameters.

## Status
All security gates pass. No context bleed or privilege escalation vulnerabilities identified.
