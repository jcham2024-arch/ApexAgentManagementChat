package com.apex.engine.core.port;

import com.apex.engine.core.port.model.EvaluationProbeResult;
import com.apex.engine.core.port.model.PlanTrace;
import com.apex.engine.planner.Plan;

/**
 * Evaluation probe: evaluates a plan trace and produces a probe result.
 * Implementations include LLM-as-a-judge evaluators, regression detectors,
 * and drift detectors. Tier 0.
 */
public interface EvaluationProbe {

    /**
     * Evaluate a plan trace. Returns a probe result with score, pass/fail,
     * and optional feedback. Implementations must be deterministic: same
     * input trace must produce same result.
     */
    EvaluationProbeResult evaluate(PlanTrace trace);

    /** The name of this probe, used for identification in reports. */
    String name();
}
