# PHASE-S2.2-PLANNER-PRIMITIVES.md — Session Package, Part 2 of 5

**Version:** 1.0 · **Sub-phase of S2** · **Part 2 of 5:** §4.1, GOAP primitives (first half).
Parts: SESSION (§0–§3) · PRIMITIVES (§4.1a) · PLANNER (§4.1b) · LOOP
(§4.2) · TESTS-DOCS (§4.3–§7), each `PHASE-S2.2-PLANNER-<part>.md`. Cut
for the 200-line rule in `AGENT_RULES.md` §2.13.

---
### 4.1 `engine-planner` sources (Tier 0)

Package `com.apex.engine.planner` for all files.

**`RiskLevel.java`**
```java
package com.apex.engine.planner;

/**
 * Risk tier of an action. L0 is read-only; L5 is privilege escalation.
 * The planner weights higher-risk actions with a higher cost so a
 * lower-risk path is preferred when total base cost is equal.
 */
public enum RiskLevel {
    L0, L1, L2, L3, L4, L5;

    /** Risk penalty added to the base cost during planning. */
    public double penalty() {
        return switch (this) {
            case L0 -> 0.0;
            case L1 -> 0.1;
            case L2 -> 0.3;
            case L3 -> 1.0;
            case L4 -> 3.0;
            case L5 -> 10.0;
        };
    }
}
```

**`Condition.java`**
```java
package com.apex.engine.planner;

/**
 * A predicate over {@link Blackboard} state. Implementations must have
 * value-based {@code equals} and {@code hashCode} so that two
 * conditions describing the same fact are treated as equal.
 */
public interface Condition {
    boolean evaluate(Blackboard state);
    String describe();
}
```

**`FactCondition.java`**
```java
package com.apex.engine.planner;

import java.util.Objects;

/**
 * A condition satisfied when the blackboard's value at {@code key}
 * equals {@code value}.
 */
public record FactCondition(String key, String value) implements Condition {
    public FactCondition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
    }

    @Override
    public boolean evaluate(Blackboard state) {
        return state.getString(key).map(value::equals).orElse(false);
    }

    @Override
    public String describe() {
        return key + "=" + value;
    }
}
```

**`ActionOutput.java`**
```java
package com.apex.engine.planner;

/**
 * Result of executing an {@link Action}. Success carries the new
 * blackboard state; Failure carries the reason. Executing the same
 * action against the same state must produce the same output
 * (determinism).
 */
public sealed interface ActionOutput {
    record Success(Blackboard newState) implements ActionOutput {}
    record Failure(String reason) implements ActionOutput {}
}
```

**`Action.java`**
```java
package com.apex.engine.planner;

import java.util.List;

/**
 * A typed action with preconditions, effects, base cost, and risk
 * level. The planner calls {@link #applyEffects(Blackboard)} to
 * simulate forward during planning; the loop calls
 * {@link #execute(Blackboard, CancellationToken)} to actually perform
 * the action.
 *
 * <p>Contract: for a successful execution, the blackboard returned by
 * {@code execute} must equal the blackboard returned by
 * {@code applyEffects} when called on the same starting state. This
 * keeps the planner's simulation sound.
 */
public interface Action {
    String id();
    List<Condition> preconditions();
    Blackboard applyEffects(Blackboard state);
    double cost();
    RiskLevel riskLevel();
    ActionOutput execute(Blackboard state, CancellationToken token);
}
```

**`Goal.java`**
```java
package com.apex.engine.planner;

import java.util.List;
import java.util.Objects;

/**
 * A target state expressed as required conditions. The planner stops
 * when all required conditions are satisfied.
 */
public record Goal(String id, List<Condition> required) {
    public Goal {
        Objects.requireNonNull(id, "id");
        required = List.copyOf(required);
        if (required.isEmpty()) {
            throw new IllegalArgumentException("goal needs at least one condition");
        }
    }

    public boolean satisfiedBy(Blackboard state) {
        return required.stream().allMatch(c -> c.evaluate(state));
    }

    public int unsatisfiedCount(Blackboard state) {
        return (int) required.stream().filter(c -> !c.evaluate(state)).count();
    }
}
```

**`Plan.java`**
```java
package com.apex.engine.planner;

import java.util.List;
import java.util.Objects;

/**
 * An ordered sequence of actions whose combined effects satisfy a
 * goal. Cost is the sum of base costs plus risk penalties. Depth is
 * the number of steps in the plan.
 */
public record Plan(List<Action> steps, double cost, int depth) {
    public Plan {
        steps = List.copyOf(steps);
        if (cost < 0) throw new IllegalArgumentException("cost < 0");
        if (depth < 0) throw new IllegalArgumentException("depth < 0");
        Objects.requireNonNull(steps, "steps");
    }
}
```
