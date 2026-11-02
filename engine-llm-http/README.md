# engine-llm-http

**Tier:** 1 · **Purpose:** OpenAI-compatible ChatModelPort implementation over java.net.http.

## Allowed dependencies
Tier 0 modules + JDK I/O (java.io, java.nio.file, java.net.http, java.sql).
HTTP uses the JDK client — no Maven artifact is added for it. No Spring,
Google, Embabel, Jackson, Micrometer, or HikariCP — enforced in this
module's own POM.

## Ports consumed
none (S2.1)

## Ports implemented
none (S2.1)

## Tests
none yet — populated in S2.1+
