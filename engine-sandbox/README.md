# engine-sandbox

**Tier:** 1 · **Purpose:** Sandboxed tool execution across isolation tiers.

## Allowed dependencies
java.base (including java.nio.file), jspecify

## Ports consumed
none in S4.0 — S4.6 will consume SandboxedProcessExecutor

## Ports implemented
SandboxPort (per tier) — implementations land in S4.6

## Types
- `SandboxTier` — PROCESS / CONTAINER / FIRE_CRACKER
- `SandboxCommand` — binary, args, cwd, timeout
- `SandboxResult` — exit code, output, timedOut
- `SandboxPort` — per-tier execution interface

## Tests
`SandboxTypesTest`
