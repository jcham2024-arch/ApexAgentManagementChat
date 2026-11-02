package com.apex.engine.core.port;

import com.apex.engine.core.port.model.PlanRequest;
import com.apex.engine.core.port.model.PlanResult;

/**
 * Planning strategy over typed actions. Tier 1 impl: engine-planner
 * (S2.2). Tier 2 impl: adapter-spring-ai. First impl lands in S2.2.
 */
public interface PlannerPort {
    PlanResult plan(PlanRequest request);
}
