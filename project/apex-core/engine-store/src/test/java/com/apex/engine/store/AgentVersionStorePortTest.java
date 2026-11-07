package com.apex.engine.store;

import com.apex.engine.core.port.model.AgentVersionManifest;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AgentVersionStorePortTest {

    @Test
    void agentVersionStoreIsImplementable() {
        AgentVersionStore port = new AgentVersionStore() {
            @Override
            public Optional<AgentVersionManifest> resolve(String id) {
                return Optional.empty();
            }

            @Override
            public void store(AgentVersionManifest manifest) {}

            @Override
            public Optional<AgentVersionManifest> rollback(String id,
                    long sequence) {
                return Optional.empty();
            }
        };

        AgentVersionManifest manifest = new AgentVersionManifest(
                "manifest-1", "model-ref", "prompt-hash", "tool-hash",
                "baseline-ref", "prior-hash", 0L);

        assertThat(port.resolve("m")).isEmpty();
        port.store(manifest);
        assertThat(port.resolve("m")).isEmpty();
        assertThat(port.rollback("m", 0)).isEmpty();
    }
}
