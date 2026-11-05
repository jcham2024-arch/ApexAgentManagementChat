# Apex Chat Common

**Tier:** 0

**Purpose:** Pure POJO module — no I/O, no threads, no network, no OS calls, no enterprise dependencies.

**Allowed dependencies:** `java.base`, `org.jspecify`

**Ports implemented:** `TenantId`, `TypedId`

**Tests:** `CorePurityTest` (no enterprise imports, no platform IO, no Executors), `TenantIdTest`, `TypedIdTest`

**Example:** See `com/apex/chat/common/identity/TenantId.java` and `com/apex/chat/common/identity/TypedId.java` for record-based identity types.
