package com.apex.platform.app.real;

import com.apex.engine.core.port.ChatModelPort;
import com.apex.engine.core.port.model.ChatRequest;
import com.apex.engine.core.port.model.ChatResponse;

import java.util.List;

/**
 * Scripted {@link ChatModelPort} for the runner tests. Returns the
 * responses in order and repeats the last one, so a test can model a
 * model that will not stop asking for the same thing.
 *
 * <p>The cursor is per instance, not static: a shared counter made the
 * tests order-dependent.
 */
class ScriptedModel implements ChatModelPort {

    private final List<String> responses;
    private final boolean recordPrompts;
    private int next;
    private String lastPrompt;

    ScriptedModel(List<String> responses) {
        this(responses, false);
    }

    private ScriptedModel(List<String> responses, boolean recordPrompts) {
        this.responses = responses;
        this.recordPrompts = recordPrompts;
    }

    /** A variant that remembers the last prompt, for feedback assertions. */
    static ScriptedModel recording(List<String> responses) {
        return new ScriptedModel(responses, true);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        if (recordPrompts) {
            lastPrompt = String.join("\n", request.promptParts());
        }
        int index = Math.min(next++, responses.size() - 1);
        return new ChatResponse(responses.get(index));
    }

    String lastPrompt() {
        return lastPrompt;
    }
}
