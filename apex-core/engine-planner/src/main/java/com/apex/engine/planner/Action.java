package com.apex.engine.planner;

import com.apex.engine.planner.concurrent.CancellationToken;
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
 *
 * <p>Implementations must check {@link CancellationToken#isCancelled()}
 * at safe points and abort promptly when cancellation is requested.
 */
public interface Action {
    String id();
    List<Condition> preconditions();
    Blackboard applyEffects(Blackboard state);
    double cost();
    RiskLevel riskLevel();
    ActionOutput execute(Blackboard state, CancellationToken token);
}
