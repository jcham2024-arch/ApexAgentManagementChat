# ADR-0001: Build system — Gradle for unified build

**Status:** Proposed  
**Deciders:** Engineering team  
**Date:** 2026-11-05

## Context

The consolidation pre-plan (NEXT-STEPS.md) requires merging two repositories:
- `Google-Ai-repo@Harness` — Gradle-based Android app (`build.gradle` / `settings.gradle`)
- `ApexAgentManagementChat` — Maven-based engine (`pom.xml` × 2 aggregators)

A unified build is required because:
- Android tooling (AGP, Lint, R8) is Gradle-only
- Two build systems will diverge and rot within weeks
- Both consumers (Android harness, Java desktop) must compile against the same interfaces

Maven is the incumbent for Apex. Gradle is the incumbent for Harness. Gradle wins because:
- Android consumer cannot use Maven for AGP/Lint/R8
- Gradle composite builds can incrementally absorb Maven modules
- Convention plugins can enforce compiler flags, lint, and publishing uniformly

## Decision

Adopt Gradle as the single build system for the unified library and both consumers.

### Migration approach

1. **Gradle skeleton first:** Empty modules (`:core-engine`, `:testkit`, `:platform-android`, `:platform-desktop`, `:bom`) with CI green on an empty build.
2. **Incremental port:** Port Apex Maven modules to Gradle one at a time, or use a Gradle composite build as a temporary bridge.
3. **Hard cutover:** Timebox the Maven→Gradle port to 2 weeks. If not complete, fall back to composite build as a bridge only, with a hard cutover date.

### Convention plugin

A Gradle convention plugin enforces:
- Compiler flags (`-release 21` → `-release 11` for shared modules)
- Lint rules
- Publishing configuration
- `forbiddenapis` to ban `java.net.http.**`, `java.awt.**`, `androidx.**` (except annotation) in `core-engine`

## Consequences

### Positive
- Single build system for Android + desktop consumers
- AGP/Lint/R8 support for Android
- Convention plugins enforce architectural rules uniformly
- Composite build enables incremental migration

### Negative
- Apex Maven modules must be rewritten as Gradle modules (one-time port cost)
- Team must learn Gradle conventions if Maven-native
- CI configuration must be duplicated or migrated

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| Keep Maven + Gradle side by side | No port cost | Two build systems rot; Android can't use Maven |
| Maven for everything | No port cost for Apex | Android AGP doesn't work with Maven |
| **Gradle (chosen)** | Single system, Android-native | Port cost for Apex modules |

## Related

- ADR-0002: minSdk/jvmTarget
- docs/REPO-MAP.md: Verified path map
- docs/PORT-RECONCILIATION.md: Port side-by-side table
