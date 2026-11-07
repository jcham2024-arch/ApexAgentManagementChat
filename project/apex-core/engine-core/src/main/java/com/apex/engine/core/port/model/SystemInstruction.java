package com.apex.engine.core.port.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * System instruction split into role, rules, and tool usage. Construction
 * rejects mixed content (e.g. tool-specific directives in the role field).
 * Immutable. Tier 0.
 */
public record SystemInstruction(
        Role role,
        List<String> rules,
        ToolUsage toolUsage
) {
    public SystemInstruction {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(toolUsage, "toolUsage");
        rules = List.copyOf(rules);
        for (String rule : rules) {
            Objects.requireNonNull(rule, "rule");
            if (rule.isBlank()) {
                throw new IllegalArgumentException("rule must not be blank");
            }
        }
    }

    /** The role the agent should adopt (system, assistant, developer, etc.). */
    public record Role(String value) {
        public Role {
            Objects.requireNonNull(value, "value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("role value must not be blank");
            }
        }
    }

    /** Tool usage guidance for the model. */
    public record ToolUsage(
            Set<String> allowedTools,
            Set<String> forbiddenTools,
            boolean requireApprovalForHighRisk
    ) {
        public ToolUsage {
            Objects.requireNonNull(allowedTools, "allowedTools");
            Objects.requireNonNull(forbiddenTools, "forbiddenTools");
            allowedTools = Set.copyOf(allowedTools);
            forbiddenTools = Set.copyOf(forbiddenTools);
            // A tool cannot be both allowed and forbidden
            java.util.Set<String> overlap = new java.util.HashSet<>(allowedTools);
            overlap.retainAll(forbiddenTools);
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException(
                    "tools cannot be both allowed and forbidden: " + overlap);
            }
        }
    }
}
