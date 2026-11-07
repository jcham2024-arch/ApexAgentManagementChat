package com.apex.engine.cli;

import java.util.Objects;

/**
 * A parsed decision from the LLM's response. Either an action to
 * execute (with arguments) or a terminal done. {@link #argumentsJson}
 * is empty when done.
 */
public sealed interface Decision {

    record Action(String toolName, String argumentsJson)
            implements Decision {
        public Action {
            Objects.requireNonNull(toolName, "toolName");
            Objects.requireNonNull(argumentsJson, "argumentsJson");
            if (toolName.isBlank()) {
                throw new IllegalArgumentException("toolName blank");
            }
        }
    }

    record Done(String summary) implements Decision {
        public Done {
            Objects.requireNonNull(summary, "summary");
        }
    }
}
