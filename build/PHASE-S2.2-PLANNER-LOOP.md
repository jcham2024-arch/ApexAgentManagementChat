# PHASE-S2.2-PLANNER-LOOP.md — Session Package, Part 4 of 5

**Version:** 1.0 · **Sub-phase of S2** · **Part 4 of 5:** §4.2, engine-core loop and cancellation types.
Parts: SESSION (§0–§3) · PRIMITIVES (§4.1a) · PLANNER (§4.1b) · LOOP
(§4.2) · TESTS-DOCS (§4.3–§7), each `PHASE-S2.2-PLANNER-<part>.md`. Cut
for the 200-line rule in `AGENT_RULES.md` §2.13.

---
### 4.2 `engine-core` sources (Tier 0)

**`engine-core/src/main/java/com/apex/engine/core/concurrent/CancellationToken.java`**
```java
package com.apex.engine.core.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation. {@link #cancel()} is idempotent and fires
 * exactly once. Long-running operations must check
 * {@link #isCancelled()} at loop boundaries and honour the token within
 * 100ms of cancellation (K6).
 */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final CompletableFuture<Void> cancelFuture = new CompletableFuture<>();

    public boolean cancel() {
        if (cancelled.compareAndSet(false, true)) {
            cancelFuture.complete(null);
            return true;
        }
        return false;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public CompletableFuture<Void> onCancel() {
        return cancelFuture;
    }
}
```

**`engine-core/src/main/java/com/apex/engine/core/loop/PlanStatus.java`**
```java
package com.apex.engine.core.loop;

/**
 * Terminal status of a {@link PlanLoop} run.
 */
public enum PlanStatus {
    COMPLETED,
    STUCK,
    FAILED,
    CANCELLED,
    MAX_STEPS_EXCEEDED
}
```

**`engine-core/src/main/java/com/apex/engine/core/loop/StepRecord.java`**
```java
package com.apex.engine.core.loop;

import com.apex.engine.planner.ActionOutput;

import java.util.Objects;

/**
 * A single step executed by {@link PlanLoop}. Used for logging and
 * later translation to the persistent step_log.
 */
public record StepRecord(int index, String actionId, ActionOutput output) {
    public StepRecord {
        if (index < 0) throw new IllegalArgumentException("index < 0");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(output, "output");
    }
}
```

**`engine-core/src/main/java/com/apex/engine/core/loop/PlanLoop.java`**
```java
package com.apex.engine.core.loop;

import com.apex.engine.core.concurrent.CancellationToken;
import com.apex.engine.planner.Action;
import com.apex.engine.planner.ActionOutput;
import com.apex.engine.planner.AStarPlanner;
import com.apex.engine.planner.Blackboard;
import com.apex.engine.planner.Goal;
import com.apex.engine.planner.Plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * OODA replan loop. Plans from the current blackboard to the goal,
 * executes the first action, applies its effects, and repeats. Checks
 * the cancellation token at every step boundary so cancellation is
 * observable within 100ms (K6). Stops at {@code maxSteps}.
 */
public final class PlanLoop {

    private final AStarPlanner planner = new AStarPlanner();

    public PlanLoopResult run(Blackboard initial, Goal goal,
                              List<Action> actions, int maxSteps,
                              CancellationToken token) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(token, "token");
        if (maxSteps <= 0) throw new IllegalArgumentException("maxSteps <= 0");

        Blackboard state = initial;
        List<StepRecord> steps = new ArrayList<>(maxSteps);

        while (steps.size() < maxSteps) {
            if (token.isCancelled()) {
                return new PlanLoopResult(PlanStatus.CANCELLED, state, steps);
            }
            if (goal.satisfiedBy(state)) {
                return new PlanLoopResult(PlanStatus.COMPLETED, state, steps);
            }

            Optional<Plan> next = planner.plan(state, goal, actions);
            if (next.isEmpty()) {
                return new PlanLoopResult(PlanStatus.STUCK, state, steps);
            }
            Action action = next.get().steps().get(0);

            ActionOutput output = action.execute(state, token);
            steps.add(new StepRecord(steps.size(), action.id(), output));

            if (output instanceof ActionOutput.Failure) {
                return new PlanLoopResult(PlanStatus.FAILED, state, steps);
            }
            state = ((ActionOutput.Success) output).newState();
        }
        return new PlanLoopResult(PlanStatus.MAX_STEPS_EXCEEDED, state, steps);
    }
}
```

**`engine-core/src/main/java/com/apex/engine/core/loop/PlanLoopResult.java`**
```java
package com.apex.engine.core.loop;

import com.apex.engine.planner.Blackboard;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of a {@link PlanLoop} run.
 */
public record PlanLoopResult(PlanStatus status, Blackboard finalState,
                             List<StepRecord> steps) {
    public PlanLoopResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(finalState, "finalState");
        steps = List.copyOf(steps);
    }
}
```

**`engine-core/src/main/java/com/apex/engine/core/loop/package-info.java`**
```java
/**
 * OODA replan loop and its result types. Tier 0. Consumes
 * engine-planner primitives. Not a port implementation — the port
 * adapter lives in the port package.
 */
@NullMarked
package com.apex.engine.core.loop;

import org.jspecify.annotations.NullMarked;
```

**`engine-core/src/main/java/com/apex/engine/core/concurrent/package-info.java`**
```java
/**
 * Cooperative cancellation primitive. Tier 0.
 */
@NullMarked
package com.apex.engine.core.concurrent;

import org.jspecify.annotations.NullMarked;
```
