# PHASE-S2.1-PORTS-INTERFACES.md — Session Package, Part 2 of 4

**Version:** 1.0 · **Sub-phase of S2** · **Part 2 of 4:** §4.2, the nine port interfaces.

Parts: `PHASE-S2.1-PORTS-SESSION.md` (§0–§4.1: protocol, context deltas,
prerequisite, scope, `TypedId`) · this file (§4.2) ·
`PHASE-S2.1-PORTS-RECORDS.md` (§4.3–§4.4) ·
`PHASE-S2.1-PORTS-TESTS-DOCS.md` (§4.5–§7). Read all four in order;
together they are the original S2.1 session package, split to honour
the 200-line rule in `AGENT_RULES.md` §2.13.

---

### 4.2 Nine port interfaces

Package `com.apex.engine.core.port`. Each interface has Javadoc naming
its Tier 1 implementation, its Tier 2 implementation (per
`CONTEXT.md` §3), and the sub-phase where its first implementation
lands. One method each except `StoragePort`, `VectorStorePort`, and
`AuditPort` which have two.

**ChatModelPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;

/**
 * Provider-agnostic chat inference. Tier 1 impl: engine-llm-http
 * (S2.4). Tier 2 impl: adapter-spring-ai (S3+). First impl lands in
 * S2.4. Streaming is added in S2.4 as an additive interface method.
 */
public interface ChatModelPort {
    ChatResponse chat(ChatRequest request);
}
```

**PlannerPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.PlanRequest;
import com.apex.engine.core.port.model.PlanResult;

/**
 * Planning strategy over typed actions. Tier 1 impl: engine-planner
 * (S2.2). First impl lands in S2.2.
 */
public interface PlannerPort {
    PlanResult plan(PlanRequest request);
}
```

**ToolExecutorPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.ToolCall;
import com.apex.engine.core.port.model.ToolResult;

/**
 * Executes a tool call and returns its result. Tier 1 impl:
 * engine-tools-impl (S2.3). First impl lands in S2.3.
 */
public interface ToolExecutorPort {
    ToolResult execute(ToolCall call);
}
```

**ApprovalPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;

/**
 * Synchronous human-in-the-loop approval. Tier 1 impl: engine-cli
 * (S2.6, terminal prompt). Tier 2 impl: adapter-spring-ai (S3, SSE).
 * Event-driven approval lands in S3 as an additive interface.
 */
public interface ApprovalPort {
    ApprovalDecision request(ApprovalRequest request);
}
```

**StoragePort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.StepLogEntry;

import java.util.List;

/**
 * Persistence for step and session logs. Tier 1 impl:
 * engine-store-sqlite (S2.5). Tier 2 impl: adapter-spring-jdbc (S6+).
 */
public interface StoragePort {
    void appendStep(StepLogEntry entry);
    List<StepLogEntry> stepsFor(String sessionId);
}
```

**VectorStorePort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.VectorHit;

import java.util.List;

/**
 * Semantic retrieval over embeddings. Tier 1 impl:
 * engine-store-sqlite (S4). Tier 2 impl: adapter-spring-ai (pgvector,
 * S4). First impl lands in S4.
 */
public interface VectorStorePort {
    void store(String id, float[] vector);
    List<VectorHit> search(float[] query, int k);
}
```

**SecretsPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.SecretRef;

/**
 * Credentials and API keys. Tier 1 impl: environment variables (S3).
 * Tier 2 impl: adapter-spring-vault (S3+). First impl lands in S3.
 */
public interface SecretsPort {
    String get(SecretRef ref);
}
```

**AuditPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.AuditEntry;

import java.util.List;

/**
 * Tamper-evident audit log. Tier 1 impl: append-only file (S3). Tier 2
 * impl: adapter-spring-audit (S3+). First impl lands in S3.
 */
public interface AuditPort {
    void append(AuditEntry entry);
    List<AuditEntry> all();
}
```

**TelemetryPort.java**
```java
package com.apex.engine.core.port;

import com.apex.engine.core.port.model.TelemetrySpan;

/**
 * Observability spans. Tier 1 impl: no-op (S2). Tier 2 impl:
 * adapter-micrometer (S6+). First impl lands in S2 with a no-op.
 */
public interface TelemetryPort {
    void record(TelemetrySpan span);
}
```

**End of part 2.** Continue in `PHASE-S2.1-PORTS-RECORDS.md`.
