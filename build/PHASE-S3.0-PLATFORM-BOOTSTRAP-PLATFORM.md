# PHASE-S3.0-PLATFORM-BOOTSTRAP-PLATFORM.md — Session Package, Part 3 of 4

**Version:** 1.0 · **Part 3 of 4:** §4.3–§4.8, platform modules and doc updates.
Parts: SESSION · RISK-APPROVAL · PLATFORM · TESTS-CRITERIA, each
`PHASE-S3.0-PLATFORM-BOOTSTRAP-<part>.md` (`AGENT_RULES.md` §2.13).

---
### 4.3 Create `apex-platform` parent POM

**`apex-platform/pom.xml`:**

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
    <artifactId>apex-platform</artifactId>
    <packaging>pom</packaging>
    <modules>
        <module>apex-auth</module>
        <module>apex-tenant</module>
        <module>apex-audit</module>
    </modules>
</project>
```

**No Enforcer block.** `apex-platform` is Tier 2. The Enforcer ban is
inherited from `apex` root? Check `apex/pom.xml`. S2.0 §3.1 moved the
Enforcer from the root to `apex-chat` and `apex-core`. The root
`apex/pom.xml` has no Enforcer. So `apex-platform` and its children
have no Enforcer. That is correct — Tier 2 modules may use Spring.

**Update `apex/pom.xml`** — add to `<modules>`:

```xml
<module>apex-platform</module>
```

### 4.4 Create three Tier 2 module POMs

For each of `apex-platform/apex-auth/pom.xml`,
`apex-platform/apex-tenant/pom.xml`,
`apex-platform/apex-audit/pom.xml`:

- Parent: `apex-platform`
- `<artifactId>` matches the module directory name
- Dependencies: `jspecify`, `junit-jupiter` (test), `assertj-core`
  (test), `engine-core` (version `${project.version}`)
- No `<build>` section (no Enforcer)
- No Spring, Jackson, or other dependency in S3.0

Example for `apex-platform/apex-auth/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.apex</groupId>
        <artifactId>apex-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>apex-auth</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.apex</groupId>
            <artifactId>engine-core</artifactId>
            <version>${project.version}</version>
        </dependency>
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
</project>
```

Repeat for `apex-tenant`, `apex-audit`.

### 4.5 `package-info.java` in each new module

**`apex-platform/apex-auth/src/main/java/com/apex/platform/auth/package-info.java`:**
```java
/**
 * JWT parsing, RBAC policy, and authentication filter for the Apex
 * kernel. Tier 2. Ports consumed: none (S3.0 skeleton; auth filter
 * added in S3.1).
 */
@NullMarked
package com.apex.platform.auth;

import org.jspecify.annotations.NullMarked;
```

**`apex-platform/apex-tenant/src/main/java/com/apex/platform/tenant/package-info.java`:**
```java
/**
 * Tenant isolation: validators, RLS advisors, and StoragePort
 * tenant-scoping. Tier 2. Ports consumed: StoragePort (from S3.2).
 */
@NullMarked
package com.apex.platform.tenant;

import org.jspecify.annotations.NullMarked;
```

**`apex-platform/apex-audit/src/main/java/com/apex/platform/audit/package-info.java`:**
```java
/**
 * Chained audit log with PII redaction. Tier 2. Implements AuditPort
 * (from S3.3).
 */
@NullMarked
package com.apex.platform.audit;

import org.jspecify.annotations.NullMarked;
```

### 4.6 `README.md` in each new module

Template (fill in per module):

```markdown
# <module-name>

**Tier:** 2 · **Purpose:** <one sentence matching package-info>

## Allowed dependencies
any (Tier 2)

## Ports consumed
<list or "none">

## Ports implemented
<list or "none">

## Tests
none yet — populated in S3.<N>
```

Fill in:
- `apex-auth` — Purpose: JWT, RBAC, auth filter. Consumes: none (S3.0). Implements: none. Tests: S3.1.
- `apex-tenant` — Purpose: tenant isolation and RLS. Consumes: StoragePort (S3.2). Implements: none. Tests: S3.2.
- `apex-audit` — Purpose: chained audit log, PII redaction. Consumes: none. Implements: AuditPort (S3.3). Tests: S3.3.

### 4.7 Update `CONTEXT.md` §4

Add four rows to the module table (or verify they exist):

| Module | Tier | Role |
|---|---|---|
| `apex-platform` | parent | Aggregator for platform security modules |
| `apex-platform/apex-auth` | 2 | JWT, RBAC, auth filter |
| `apex-platform/apex-tenant` | 2 | Tenant isolation, RLS |
| `apex-platform/apex-audit` | 2 | Chained audit log, PII redaction |

If already present, verify and leave. If missing, add.

### 4.8 Update `docs/INVARIANTS.md`

Change the Test column for these rows:

| ID | Before | After |
|---|---|---|
| K1 | S3 | S3.5 pending |
| K3 | S3 | S3.5 pending |
| K4 | S3 | S3.5 pending |
| K7 | S3 | S3.5 pending |
| E2 | S3 | S3.5 pending |

Do not change other rows.
