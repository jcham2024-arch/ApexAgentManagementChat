package com.apex.engine.planner;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A typed action with preconditions, effects, base cost, and risk
 * level. The planner calls {@link #applyEffects(Blackboard)} to
 * simulate forward during planning; the loop calls
 * {@link #execute(Blackboard, BooleanSupplier)} to actually perform
 * the action.
 *
 * <p>Contract: for a successful execution, the blackboard returned by
 * {@code execute} must equal the blackboard returned by
 * {@code applyEffects} when called on the same starting state. This
 * keeps the planner's simulation sound.
 *
 * <p><b>Known flaw in the S2.2 phase file, temporary signature.</b> The
 * phase file specifies {@code execute(Blackboard, CancellationToken)}
 * with {@code CancellationToken} in
 * {@code com.apex.engine.core.concurrent}. This module cannot name that
 * type: engine-core depends on engine-planner (S2.2 §1.2, §4.3), so the
 * reference is a Maven cycle and breaks §1.5 (planner uses the JDK and
 * JSpecify only). The parameter is therefore the JDK
 * {@link BooleanSupplier} that the core's token satisfies. Fix in the
 * next step: move {@code CancellationToken} into engine-planner, restore
 * the token parameter, and delete this paragraph.
 */
public interface Action {
    String id();
    List<Condition> preconditions();
    Blackboard applyEffects(Blackboard state);
    double cost();
    RiskLevel riskLevel();
    ActionOutput execute(Blackboard state, BooleanSupplier isCancelled);
}
