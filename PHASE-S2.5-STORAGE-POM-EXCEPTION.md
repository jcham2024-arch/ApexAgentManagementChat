# PHASE-S2.5-STORAGE-POM-EXCEPTION.md — Session Package, Part 2 of 5

**Version:** 1.0 · **Sub-phase of S2** · **Part 2 of 5:** §4.1–§4.2, pom change and StorageException.
Parts: SESSION · POM-EXCEPTION · SQLITE-PORT · METADATA-TESTS ·
CRITERIA, each `PHASE-S2.5-STORAGE-<part>.md` (`AGENT_RULES.md` §2.13).

---
## 4. Files

### 4.1 `engine-store-sqlite/pom.xml` — add dependency

Insert inside `<dependencies>`, **after** the existing
`engine-core` dependency and **before** `jspecify`:

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.46.1.3</version>
</dependency>
```

If `mvn -B dependency:resolve` fails on that version, use the latest
stable release in the 3.46.x or 3.47.x line and note the version in the
commit body. Do not add any other dependency. Do not add
`slf4j-simple` or any logging library.

### 4.2 `StorageException.java`

```java
package com.apex.engine.store.sqlite;

/**
 * Failure from the SQLite storage adapter. {@link #category()}
 * classifies the failure: {@code init}, {@code append}, {@code query},
 * {@code duplicate}.
 */
public final class StorageException extends RuntimeException {
    private final String category;

    public StorageException(String category, String message) {
        super(message);
        this.category = category;
    }

    public StorageException(String category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public String category() {
        return category;
    }
}
```
