# PHASE-S4.0-MEMORY-SANDBOX-BOOTSTRAP.md

**Version:** 1.0 · **Sub-phase of S4** · **Ends with module skeletons, enums, and interfaces compiling; `mvn test` green.**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md` (repo)
2. `CONTEXT.md` (repo)
3. `PHASE-S4-MEMORY-SANDBOX.md` (repo — router)
4. **This file**
# PHASE-S4.0-MEMORY-SANDBOX-BOOTSTRAP.md

**Version:** 1.0 · **Sub-phase of S4** · **Ends with module skeletons,
enums, and interfaces compiling; `mvn test` green**

## 0. Reading protocol

Load in this order:

1. `AGENT_RULES.md`
2. `CONTEXT.md`
3. `PHASE-S4-MEMORY-SANDBOX.md` (router)
4. **This file**

## 1. Context notes — deltas for S4.0

Authoritative where they conflict with any prior file. Do not stop over
them.

### 1.1 Two new modules, no other code changes

S4.0 creates `engine-memory` (Tier 0) and `engine-sandbox` (Tier 1).
No existing module is touched. No behavior beyond records and
interfaces.

### 1.2 Memory does not replace `StoragePort` or `VectorStorePort`

`MemoryRouter` composes over them. Episodic memory (S4.2) wraps
`StoragePort`. Semantic memory (S4.3) wraps `VectorStorePort`. Working
memory (S4.1) is a new in-memory buffer. The router is the composition
point; the ports stay untouched.

### 1.3 `ContextCompactor` is interface only

Signature defined here. Implementation and C4 proof land in S4.5. Do
not implement.

### 1.4 `SandboxPort` is interface only

Signature and `SandboxTier` defined here. Process tier lands in S4.6.
Do not implement.

### 1.5 Tier discipline

`engine-memory` is Tier 0: `java.base` + `org.jspecify` only. It
**must** carry the Enforcer block from `apex-chat/pom.xml`, verbatim.

`engine-sandbox` is Tier 1: may use `java.nio.file` (Path is in the
public API). It **must** carry the Enforcer block from
`engine-tools-impl/pom.xml`, verbatim — same exclusions as Tier 0,
declared inline.

### 1.6 `INVARIANTS.md` unchanged

C4 proof is S4.5. Do not touch `docs/INVARIANTS.md`.

### 1.7 No spec needed

Router §4 confirms.

## 2. Prerequisite

S3.5 merged to `main`. `mvn -B test` green on `main`. If not, stop
and report.

## 3. Scope

Deliver:

1. `apex/pom.xml` updated: two new modules
2. Two new module POMs
3. Six types in `engine-memory`
4. Four types in `engine-sandbox`
5. `package-info.java` and `README.md` in both modules
6. `CONTEXT.md` §4: two new rows
7. Two test classes

## 4. Files

### 4.1 `apex/pom.xml`

In `<modules>`, after `engine-store-sqlite`:

```xml
<module>engine-memory</module>
<module>engine-sandbox</module>
```

### 4.2 `engine-memory/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.apex</groupId>
        <artifactId>apex</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>engine-memory</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.jspecify</groupId>
            <artifactId>jspecify</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <!-- maven-enforcer-plugin block, verbatim from apex-chat/pom.xml -->
        </plugins>
    </build>
</project>
```

### 4.3 `engine-sandbox/pom.xml`

Same structure, but `<artifactId>engine-sandbox</artifactId>` and the
Enforcer block copied verbatim from `engine-tools-impl/pom.xml`
(Tier 1 style — same exclusions, declared inline).

### 4.4 `engine-memory` types (package `com.apex.engine.memory`)

**MemoryKind.java**
```java
package com.apex.engine.memory;

/**
 * Kinds of memory the router can query. WORKING is the current
 * conversation buffer; EPISODIC is past steps and sessions; SEMANTIC
 * is facts, decisions, and embeddings.
 */
public enum MemoryKind {
    WORKING,
    EPISODIC,
    SEMANTIC
}
```

**MemoryQuery.java**
```java
package com.apex.engine.memory;

import java.util.Objects;
import java.util.Set;

/**
 * A memory retrieval request. {@code kinds} names which backends to
 * consult; empty means none (rejected). {@code maxHits} caps the total
 * results returned across all consulted backends.
 */
public record MemoryQuery(String text, Set<MemoryKind> kinds, int maxHits) {
    public MemoryQuery {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kinds, "kinds");
        kinds = Set.copyOf(kinds);
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("kinds must not be empty");
        }
        if (maxHits <= 0) {
            throw new IllegalArgumentException("maxHits must be positive");
        }
    }
}
```

**MemoryHit.java**
```java
package com.apex.engine.memory;

import java.util.Objects;

/**