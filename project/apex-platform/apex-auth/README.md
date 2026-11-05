# apex-auth

**Tier:** 2 · **Purpose:** JWT verification, RBAC policy, auth filter.

## Allowed dependencies
any (Tier 2); currently jjwt 0.12.6 (HS256), gson (via jjwt-gson)

## Ports consumed
none

## Ports implemented
none (AuthFilter is a plain interface; Spring wiring lands in S6)

## Design notes
- HS256 only in S3.1. RS256 with public keys lands in S6.
- Tokens must carry `sub`, `tenant_id`, `roles`, and `exp` claims.
- Secret must be at least 32 bytes; shorter secrets are rejected.
- RBAC: 4 roles, 8 permissions, no role inheritance, explicit grants.
- Separation of duties: TENANT_ADMIN does not hold PROPOSAL_APPROVE.

## Tests
PrincipalTest · RbacPolicyTest · JwtAuthFilterTest
