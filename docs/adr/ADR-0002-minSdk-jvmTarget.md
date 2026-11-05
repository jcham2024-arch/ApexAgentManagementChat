# ADR-0002: minSdk 26 / Java 11 for shared core

**Status:** Proposed  
**Deciders:** Engineering team  
**Date:** 2026-11-05

## Context

The shared library (`core`) must compile on both:
- Android (ART via Kotlin/Java, AGP/Lint/R8)
- JVM desktop/server (JVM 21+)

The Apex codebase currently targets Java 21 (`maven.compiler.release=21`, `maven.compiler.source=21`, `maven.compiler.target=21`). The Harness Android app targets an unknown minSdk.

For a shared core that compiles to both platforms, we need the **lowest common denominator** that satisfies both:
- Android minSdk 26 corresponds to Java 8 language features but AGP supports Java 11 via desugaring
- JVM desktop can run Java 11+ without issue

Java 11 is the recommended target because:
- It is the LTS floor for modern Android desugaring (AGP 7+ supports Java 11 language features on minSdk 26+)
- It avoids newer language features (records, sealed classes, pattern matching) that require desugaring or higher minSdk
- Apex currently uses Java 21 records in Tier 0 modules; these would need to be rewritten as classes/builders for Java 11 compatibility, OR desugaring must be enabled
- `jspecify` (nullability annotations) works on Java 11+

## Decision

**minSdk 26 / jvmTarget 11 (Java 11 language level)** for all shared library modules (`core`, `spi`, `provider-*`, `testkit`, `bom`).

Platform-specific modules may target higher levels:
- `platform-android`: minSdk 26, Java 11 language level (with desugaring for newer APIs if needed)
- `platform-desktop`: Java 21 (desktop JVM can use newer features)

## Shared core constraints

The shared core stays **pure Java 11, zero third-party dependencies** (only `jspecify`). This means:

| Feature | Status in shared core |
|---|---|
| `java.base` only | ✅ Allowed |
| `org.jspecify` | ✅ Allowed (nullability annotations) |
| Java 21 records | ❌ Not in shared core (use classes + builders) |
| Sealed interfaces | ❌ Not in shared core |
| Pattern matching for `instanceof` | ❌ Not in shared core |
| `java.io`, `java.nio.file`, `java.net` | ❌ Not in shared core (Tier 0 prohibition) |
| `Executors.*` | ❌ Not in shared core |

Platform-specific I/O, HTTP, and JSON live behind ports in `platform-*` modules.

## Consequences

### Positive
- Single source compiles on Android (minSdk 26+) and JVM desktop
- `forbiddenapis` + `-Xjdk-release=11` in CI prevents accidental API leakage
- Clear boundary: shared core is pure, platforms handle I/O

### Negative
- Apex's existing Java 21 records in Tier 0 modules must be rewritten for shared core
- Desugaring configuration needed for Android module
- Team must be disciplined about not leaking newer APIs into shared core

## Alternatives considered

| Option | Pros | Cons |
|---|---|---|
| Java 8 language level | Maximum compatibility | No `jspecify` null safety, verbose |
| **Java 11 (chosen)** | `jspecify` support, LTS, Android-desugarable | Requires record→class migration for shared core |
| Java 17 | Newer LTS | minSdk 26 desugaring support is less certain |
| Java 21 (current Apex) | No changes needed for Apex | Not supported on Android minSdk 26 without extensive desugaring |

## Related

- ADR-0001: Build system (Gradle)
- docs/REPO-MAP.md: Verified path map
- AGENT_RULES.md §2.6: "Android desugaring compliance at Java 17 language level. No Java 21 syntax in abstract modules." — **This rule conflicts with ADR-0002 and must be reconciled.** ADR-0002 (Java 11) takes precedence for shared core; AGENT_RULES §2.6 should be updated to Java 11.

## Related

- ADR-0001: Build system (Gradle)
- docs/REPO-MAP.md: Verified path map
